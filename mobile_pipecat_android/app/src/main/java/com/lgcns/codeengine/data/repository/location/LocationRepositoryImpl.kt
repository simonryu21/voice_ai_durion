package com.lgcns.codeengine.data.repository.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

class LocationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository {

    override suspend fun getCurrentLocation(): CurrentLocationResult = withContext(Dispatchers.IO) {
        if (!hasAnyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        ) {
            return@withContext CurrentLocationResult.NoPermission
        }

        val gpsLocationAsync = getCurrentLocationAsync(
            provider = LocationManager.GPS_PROVIDER,
            permission = Manifest.permission.ACCESS_FINE_LOCATION
        )
        val networkLocationAsync = getCurrentLocationAsync(
            provider = LocationManager.NETWORK_PROVIDER,
            permission = Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (gpsLocationAsync == PROVIDER_UNAVAILABLE && networkLocationAsync == PROVIDER_UNAVAILABLE) {
            return@withContext CurrentLocationResult.LocationDisabled
        }

        gpsLocationAsync?.await()?.let { location ->

            networkLocationAsync?.cancel()

            return@withContext CurrentLocationResult.Success(location)
        }

        networkLocationAsync?.await()?.let { location ->

            return@withContext CurrentLocationResult.Success(location)
        }

        return@withContext CurrentLocationResult.Timeout
    }

    override fun hasAvailableProvider(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java)
        return arrayOf(
            LocationManager.GPS_PROVIDER to Manifest.permission.ACCESS_FINE_LOCATION,
            LocationManager.NETWORK_PROVIDER to Manifest.permission.ACCESS_COARSE_LOCATION
        ).any { (provider, permission) ->
            checkPermission(permission) && manager.isProviderEnabled(provider)
        }
    }

    private fun CoroutineScope.getCurrentLocationAsync(
        provider: String,
        permission: String
    ): Deferred<GeoLocation?>? {
        val manager = context.getSystemService(LocationManager::class.java)
        if (!checkPermission(permission) || !manager.isProviderEnabled(provider)) {
            return PROVIDER_UNAVAILABLE
        }

        return async {
            val location = manager.getCurrentLocation(provider, timeoutMillis = 2000)
                ?: manager.getLastLocationInAge(provider, age = 120)
            location?.toGeoLocation()
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    suspend fun LocationManager.getCurrentLocation(
        provider: String,
        timeoutMillis: Long? = null
    ): Location? =
        if (timeoutMillis != null) {
            withTimeoutOrNull(timeoutMillis) {
                getCurrentLocationCompat(provider)
            }
        } else {
            getCurrentLocationCompat(provider)
        }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun LocationManager.getCurrentLocationCompat(provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val cancellationSignal = CancellationSignal()
            LocationManagerCompat.getCurrentLocation(
                this@getCurrentLocationCompat,
                provider,
                cancellationSignal,
                Dispatchers.IO.asExecutor(),
                cont::resume
            )
            cont.invokeOnCancellation {
                cancellationSignal.cancel()
            }
        }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun LocationManager.getLastLocationInAge(provider: String, age: Int): Location? =
        getLastKnownLocation(provider)?.takeIf { it.getAgeSeconds() <= age }

    private fun Location.getAgeSeconds(): Long =
        TimeUnit.NANOSECONDS.toSeconds(SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos)

    private fun hasAnyPermissions(vararg permissions: String): Boolean =
        permissions.any(::checkPermission)

    private fun checkPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toGeoLocation(): GeoLocation =
        GeoLocation(
            latitude = latitude,
            longitude = longitude,
        )

    companion object {
        private val PROVIDER_UNAVAILABLE: Deferred<GeoLocation?>? = null
    }
}