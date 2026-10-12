package com.lgcns.codeengine.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import timber.log.Timber

object BiometricUtility {

    const val HARDWARE_IS_OK: Int = 0

    fun isBiometricHardWareAvailable(con: Context): Int {
        var result = HARDWARE_IS_OK
        val biometricManager = BiometricManager.from(con)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //Timber.d("canAuthenticate: " + biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_STRONG))
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> result = HARDWARE_IS_OK
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> result = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> result = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> result = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                    result = HARDWARE_IS_OK
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                    result = HARDWARE_IS_OK
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                    result = BiometricManager.BIOMETRIC_STATUS_UNKNOWN
            }
        } else {
            //Timber.d("old canAuthenticate: " + biometricManager.canAuthenticate())
            @Suppress("DEPRECATION")
            when (biometricManager.canAuthenticate()) {
                BiometricManager.BIOMETRIC_SUCCESS -> result = HARDWARE_IS_OK
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> result = BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> result = BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> result = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
            }
        }

        Timber.d("isBiometricHardWareAvailable(): $result")
        return result
    }


    fun deviceHasPasswordPinLock(con: Context): Boolean {
        val keyMgr = con.getSystemService(AppCompatActivity.KEYGUARD_SERVICE) as KeyguardManager
        if (keyMgr.isKeyguardSecure) {
            Timber.d("deviceHasPasswordPinLock(): true")
            return true
        }
        Timber.d("deviceHasPasswordPinLock(): false")
        return false
    }

}
