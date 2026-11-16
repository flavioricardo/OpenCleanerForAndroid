package io.github.flavioricardo.opencleaner.scan

import java.io.File

const val OWN_PACKAGE = "io.github.flavioricardo.opencleaner"

/** One installed app as the analysis sees it. Times in epoch millis; lastUsed 0 = never. */
data class AppInfo(
    val pkg: String,
    val label: String,
    val appBytes: Long,
    /** Includes the cache, as StorageStats.getDataBytes() does. */
    val dataBytes: Long,
    /** Internal plus external cache. */
    val cacheBytes: Long,
    /** The part of cacheBytes the system "clear cache" dialog can remove (Android/data/<pkg>/cache). */
    val externalCacheBytes: Long,
    val lastUsed: Long,
    val installed: Long,
    val system: Boolean,
    /** Works without being opened: no launcher icon, or it is the current keyboard or home app. */
    val alwaysOn: Boolean = false,
)

sealed interface Action {
    data class DeleteFiles(val files: List<File>) : Action
    data object ClearAllCache : Action
    data class Uninstall(val pkg: String) : Action
    /** Something only the user can do in the app's own settings ("only you can do"). */
    data class OpenAppSettings(val pkg: String) : Action
}

/** Steps of an analysis, in the order they run. APPS is loaded by the UI before analyze(). */
enum class Phase { APPS, FILES, WHATSAPP, DUPLICATES }

enum class Kind {
    WA_DUPLICATES, WA_BACKUPS, WA_STATUSES, CACHE, APKS, THUMBNAILS, EMPTY_DIRS,
    DUPLICATES, LARGE_VIDEOS, UNUSED_APP, MANUAL_APP, APP_CACHE, NEEDS_FILES_ACCESS, NEEDS_USAGE_ACCESS,
}

/** label is the app name for UNUSED_APP, MANUAL_APP and APP_CACHE. */
data class Finding(
    val kind: Kind,
    val label: String?,
    val bytes: Long,
    val action: Action?,
    val checked: Boolean,
)
