package io.github.flavioricardo.opencleaner.system

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.Settings

fun hasAllFiles(): Boolean = Environment.isExternalStorageManager()

fun hasUsage(ctx: Context): Boolean {
    val ops = ctx.getSystemService(AppOpsManager::class.java)
    val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
    return mode == AppOpsManager.MODE_ALLOWED ||
        (mode == AppOpsManager.MODE_DEFAULT &&
            ctx.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED)
}

fun allFilesIntent(ctx: Context): Intent =
    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))

fun usageIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
