package io.github.flavioricardo.opencleaner.system

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.storage.StorageManager
import io.github.flavioricardo.opencleaner.scan.AppInfo

private const val YEAR = 365L * 24 * 60 * 60 * 1000

/** Installed apps with their sizes and last use. Null without usage access. Slow: call off the main thread. */
fun loadApps(ctx: Context): List<AppInfo>? {
    if (!hasUsage(ctx)) return null
    val pm = ctx.packageManager
    val stats = ctx.getSystemService(StorageStatsManager::class.java)
    val usage = ctx.getSystemService(UsageStatsManager::class.java)
    val now = System.currentTimeMillis()
    val lastUsed = usage.queryAndAggregateUsageStats(now - YEAR, now)
    val user = Process.myUserHandle()

    return pm.getInstalledApplications(0).mapNotNull { app ->
        try {
            val s = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, app.packageName, user)
            AppInfo(
                pkg = app.packageName,
                label = pm.getApplicationLabel(app).toString(),
                appBytes = s.appBytes,
                dataBytes = s.dataBytes,
                cacheBytes = s.cacheBytes,
                lastUsed = lastUsed[app.packageName]?.lastTimeUsed ?: 0L,
                installed = pm.getPackageInfo(app.packageName, 0).firstInstallTime,
                system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            )
        } catch (e: Exception) {
            null // Package removed meanwhile, or stats unavailable: skip it.
        }
    }
}
