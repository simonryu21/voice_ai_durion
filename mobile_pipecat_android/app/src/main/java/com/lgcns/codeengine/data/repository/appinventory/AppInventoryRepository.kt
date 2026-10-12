package com.lgcns.codeengine.data.repository.appinventory

import android.content.Context
import com.google.gson.GsonBuilder
import com.lgcns.codeengine.data.repository.appinventory.model.AppInventoryRequest
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.REACT_TEST_URL
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.TOKEN_KEY
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.net.URI
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class AppInventoryRepository(
    private val context: Context,
    private val dataStoreRepository: DataStoreRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val collector = AppInventoryCollector(context)

    suspend fun syncIfNeeded() = withContext(ioDispatcher) {
        try {
            val token = dataStoreRepository.getDataStoreStringOnce(TOKEN_KEY)
            if (token.isNullOrBlank()) {
                Timber.d("AppInventory: No token, skipping")
                return@withContext
            }

            val baseUrl = resolveApiBaseUrl()
            if (baseUrl == null) {
                Timber.d("AppInventory: No server URL configured, skipping")
                return@withContext
            }

            val hasUsageStats = collector.isUsageStatsGranted()
            val apps = if (hasUsageStats) {
                collector.collectWithUsageScores()
            } else {
                collector.collectInstalledApps()
            }

            if (apps.isEmpty()) {
                Timber.d("AppInventory: No apps collected, skipping")
                return@withContext
            }

            val clientSorted = hasUsageStats && apps.any { it.usageScore != null }
            val snapshotHash = computeSnapshotHash(apps, clientSorted)
            val previousHash = dataStoreRepository.getDataStoreStringOnce(HASH_KEY)

            if (snapshotHash == previousHash) {
                Timber.d("AppInventory: Snapshot unchanged (hash=$snapshotHash), skipping")
                return@withContext
            }

            val request = AppInventoryRequest(
                clientTs = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                deviceId = collector.getDeviceId(),
                platform = "android",
                clientUsageStatsCollected = hasUsageStats,
                clientSorted = clientSorted,
                apps = apps
            )

            val api = createApi(baseUrl)
            val response = api.uploadAppInventory("Bearer $token", request)

            if (response.isSuccessful) {
                dataStoreRepository.setDataStoreString(HASH_KEY, snapshotHash)
                Timber.i(
                    "AppInventory: Upload success (${apps.size} apps, " +
                            "clientSorted=$clientSorted, hash=$snapshotHash)"
                )
            } else {
                Timber.w("AppInventory: Upload failed HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Timber.w(e, "AppInventory: Upload error")
        }
    }

    private fun computeSnapshotHash(
        apps: List<com.lgcns.codeengine.data.repository.appinventory.model.AppItemRequest>,
        clientSorted: Boolean
    ): String {
        val payload = buildString {
            append("clientSorted=$clientSorted;")
            for (app in apps.sortedBy { it.packageName }) {
                append(app.packageName)
                append(',')
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun resolveApiBaseUrl(): String? {
        val reactUrl = dataStoreRepository.getDataStoreStringOnce(REACT_TEST_URL)
        if (reactUrl.isNullOrBlank()) return null

        return try {
            val uri = URI(reactUrl)
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$port/"
        } catch (e: Exception) {
            Timber.w(e, "AppInventory: Failed to parse server URL: $reactUrl")
            null
        }
    }

    private fun createApi(baseUrl: String): AppInventoryApi {
        val gson = GsonBuilder().setLenient().create()
        val client = OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    setLevel(HttpLoggingInterceptor.Level.HEADERS)
                }
            )
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AppInventoryApi::class.java)
    }

    companion object {
        const val HASH_KEY = "app_inventory_hash"
        private const val CONNECTION_TIMEOUT = 15_000L
    }
}
