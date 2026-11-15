package io.github.flavioricardo.opencleaner.system

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
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

    // Apps that work without ever being opened must not look "unused".
    val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.packageName }.toSet()
    val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
    val keyboard = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        ?.let { ComponentName.unflattenFromString(it)?.packageName }

    return pm.getInstalledPackages(0).mapNotNull { info ->
        val app = info.applicationInfo ?: return@mapNotNull null
        try {
            val s = stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
            AppInfo(
                pkg = info.packageName,
                label = pm.getApplicationLabel(app).toString(),
                appBytes = s.appBytes,
                dataBytes = s.dataBytes,
                cacheBytes = s.cacheBytes,
                lastUsed = lastUsed[info.packageName]?.lastTimeUsed ?: 0L,
                installed = info.firstInstallTime,
                system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                alwaysOn = info.packageName !in launchable || info.packageName == home || info.packageName == keyboard,
            )
        } catch (e: Exception) {
            null // Package removed meanwhile, or stats unavailable: skip it.
        }
    }
}
