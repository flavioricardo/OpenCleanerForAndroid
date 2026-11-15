package io.github.flavioricardo.opencleaner.system

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import java.io.File

/** System dialog that clears the cache of every app (needs all-files access). */
fun clearCacheIntent(): Intent = Intent(StorageManager.ACTION_CLEAR_APP_CACHE)

fun uninstallIntent(pkg: String): Intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))

fun appSettingsIntent(pkg: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

/** (used, total) bytes of shared storage. */
fun storageUsage(): Pair<Long, Long> {
    val fs = StatFs(Environment.getExternalStorageDirectory().path)
    return (fs.totalBytes - fs.availableBytes) to fs.totalBytes
}

/** Tells the media index that these paths changed, so the gallery forgets deleted files. */
fun rescan(ctx: Context, files: List<File>) {
    if (files.isNotEmpty()) MediaScannerConnection.scanFile(ctx, files.map { it.path }.toTypedArray(), null, null)
}
