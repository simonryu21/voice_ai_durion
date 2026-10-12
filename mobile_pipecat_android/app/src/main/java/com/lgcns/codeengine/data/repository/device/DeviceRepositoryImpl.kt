package com.lgcns.codeengine.data.repository.device

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceRepository {
    private var appVersion: String = ""
    private var appVersionCode : String = ""

    @SuppressLint("HardwareIds")
    override suspend fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver,
            Settings.Secure.ANDROID_ID)
    }

    override fun getAppVersion(): String {
        if (appVersion.isNotEmpty()) {
            return appVersion
        }

        appVersion = try {
            context.packageManager.getPackageInfoCompat(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("PackageManger NameNotFoundException")
            ""
        }

        return appVersion
    }

    private fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int = 0): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
        }

    override suspend fun getVersionCode(): String {
        if (appVersionCode.isNotEmpty()) {
            return appVersionCode
        }

        appVersionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e("PackageManger NameNotFoundException")
            ""
        }

        return appVersionCode
    }

    override fun getAppName(): String {
        return context.applicationInfo.loadLabel(context.packageManager).toString()
    }

    override fun getAppPackageName(): String {
        return context.packageName
    }

    override fun areNotificationsEnabled(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        return manager.areNotificationsEnabled()
    }

    override fun setClipboardText(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(
                ClipData.newPlainText(label, text)
            )
    }

    override fun getClipboardText(): String {
        val manager = context.getSystemService(ClipboardManager::class.java)
        val firstItem = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        return firstItem?.text?.toString() ?: ""
    }

    @Suppress("DEPRECATION")
    @SuppressLint("HardwareIds")
    override fun getPhoneNumber(): String {

        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return try {
            if (telephonyManager.line1Number != null) {
                telephonyManager.line1Number
            } else {
                ""
            }
        } catch (ex: SecurityException) {
            Timber.e("getPhoneNumber() : SecurityException(${ex.message})")
            ""
        }
    }

    private fun isAvailableUSIM(): Boolean {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> true
            else -> false
        }
    }

    override fun getSimOperator(): String {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (!isAvailableUSIM()) {
            return ""
        }

        var simOperatorName = telephonyManager.simOperatorName ?: ""
        var netOperatorName = telephonyManager.networkOperatorName ?: ""
        Timber.d("netOperatorName($netOperatorName), simOperatorName($simOperatorName)")
        var result = when {
            netOperatorName.isNotEmpty() -> {
                netOperatorName.lowercase()
            }
            simOperatorName.isNotEmpty() -> {
                simOperatorName.lowercase()
            }
            else -> {
                ""
            }
        }

        if (result.isEmpty()) {
            return "NA"
        }

        if (result.startsWith("olleh") || result.startsWith("kr ktf")
            || result.startsWith("ktf") || result == "kt"
        ) {
            result = "KT"
        } else if (result.startsWith("sk telecom") || result.startsWith("kor sk telecom")
            || result.startsWith("skteleco") || result.startsWith("skt telecom") || result == "skt"
        ) {
            result = "SKT"
        } else if (result.startsWith("lg u+") || result.startsWith("lgu+")
            || result.startsWith("lgt") || result.startsWith("lg uplus")
        ) {
            result = "LGT"
        }
        result = result.replace("&", "_")
        result = result.replace("<", "")
        result = result.replace(">", "")

        result = result.replace("[^A-Za-z0-9가-힣 _]".toRegex(), "")

        for (i in 0..9) {
            result = result.replace("__", "_")
        }
        Timber.d("result($result)")
        return if (result != "" && result != "_") result else "ext_" + getSimOperatorNumeric()
    }

    private fun getSimOperatorNumeric(): String {
        if (!isAvailableUSIM()) {
            return ""
        }
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        var simOperator = telephonyManager.simOperator ?: ""
        var netOperator = telephonyManager.networkOperator ?: ""
        Timber.d("getSimOperatorNumeric() : netOperator($netOperator), simOperator($simOperator)")

        var result = when {
            netOperator.isNotEmpty() -> {
                netOperator
            }
            simOperator.isNotEmpty() -> {
                simOperator
            }
            else -> {
                "NA"
            }
        }
        result = result.replace("[^A-Za-z0-9]".toRegex(), "")

        return if (result == "") "unknown_shb" else result
    }

    override suspend fun getPushTokenOrNull(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            Timber.e("Failed to get firebase messaging token. ${e.message}")
            null
        }
    }

}
