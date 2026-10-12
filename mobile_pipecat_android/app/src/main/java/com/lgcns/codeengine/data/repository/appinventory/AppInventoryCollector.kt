package com.lgcns.codeengine.data.repository.appinventory

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.lgcns.codeengine.data.repository.appinventory.model.AppItemRequest
import timber.log.Timber
import java.util.concurrent.TimeUnit

class AppInventoryCollector(private val context: Context) {

    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun isUsageStatsGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun collectInstalledApps(): List<AppItemRequest> {
        val pm = context.packageManager
        val appInfos = getInstalledApplications(pm)

        return appInfos.map { info ->
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                null
            }
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            AppItemRequest(
                packageName = info.packageName,
                label = label,
                usageScore = null,
                isSystem = isSystem
            )
        }
    }

    fun collectWithUsageScores(): List<AppItemRequest> {
        val pm = context.packageManager
        val appInfos = getInstalledApplications(pm)
        val usageScores = collectUsageScores()

        return appInfos.map { info ->
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                null
            }
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val score = usageScores[info.packageName]

            AppItemRequest(
                packageName = info.packageName,
                label = label,
                usageScore = score,
                isSystem = isSystem
            )
        }.let { apps ->
            apps.sortedByDescending { it.usageScore ?: 0.0 }
        }
    }

    private fun getInstalledApplications(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }
    }

    private fun collectUsageScores(): Map<String, Double> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - TimeUnit.DAYS.toMillis(30)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)

            val scoreMap = mutableMapOf<String, Double>()
            for (stat in stats) {
                val pkg = stat.packageName
                val foregroundMs = stat.totalTimeInForeground.toDouble()
                scoreMap[pkg] = (scoreMap[pkg] ?: 0.0) + foregroundMs
            }
            scoreMap
        } catch (e: Exception) {
            Timber.w(e, "Failed to collect usage stats")
            emptyMap()
        }
    }
}
