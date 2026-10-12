package com.lgcns.codeengine

import android.app.Application
import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.lgcns.codeengine.data.repository.appinventory.AppInventoryRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.SHOWN_PERMISSION_NOTICE
import com.lgcns.codeengine.data.repository.datastore.DataStoreRepository.Companion.TOKEN_KEY
import com.skt.Tmap.TMapTapi
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.io.IOException
import javax.inject.Inject

@HiltAndroidApp
class CodeEngineApplication : Application() {

    @Inject
    lateinit var dataStoreRepository: DataStoreRepository

    @Inject
    lateinit var appInventoryRepository: AppInventoryRepository

    companion object {
        private var INSTANCE: CodeEngineApplication? = null

        fun getGlobalApplicationContext(): CodeEngineApplication? {
            return CodeEngineApplication.Companion.INSTANCE
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) Timber.plant(DebugTree())
        Timber.d("Start CodeEngineApplication..")

        // TMap 초기화
        TMapTapi(applicationContext).setSKTMapAuthentication(BuildConfig.TMAP_APP_KEY)

        determineAdvertisingInfo(this)
        observeAndSyncAppInventory()

    }

    private fun observeAndSyncAppInventory() {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        applicationScope.launch {
            combine(
                dataStoreRepository.getDataStoreBoolean(SHOWN_PERMISSION_NOTICE),
                dataStoreRepository.getDataStoreString(TOKEN_KEY)
            ) { permissionGranted, token ->
                Pair(permissionGranted == true, !token.isNullOrBlank())
            }.collect { (permissionDone, hasToken) ->
                if (permissionDone && hasToken) {
                    appInventoryRepository.syncIfNeeded()
                }
            }
        }
    }

    private fun determineAdvertisingInfo(context: Context) {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        applicationScope.launch {
            try {
                val advertisingIdInfo: AdvertisingIdClient.Info =
                    AdvertisingIdClient.getAdvertisingIdInfo(context)
                // You should check this in case the user disabled it from settings
                if (!advertisingIdInfo.isLimitAdTrackingEnabled) {
                    Timber.d("advertisingIdInfo.id=${advertisingIdInfo.id}")

                    dataStoreRepository.setDataStoreString(
                        DataStoreRepository.AD_ID,
                        advertisingIdInfo.id ?: ""
                    )

                } else {
                    Timber.w("If you stored the id before you should remove it")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: GooglePlayServicesNotAvailableException) {
                e.printStackTrace()
            } catch (e: GooglePlayServicesRepairableException) {
                e.printStackTrace()
            }
        }
    }

}
