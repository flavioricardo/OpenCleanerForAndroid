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
    val cacheBytes: Long,
    val lastUsed: Long,
    val installed: Long,
    val system: Boolean,
)

sealed interface Action {
    data class DeleteFiles(val files: List<File>) : Action
    data object ClearAllCache : Action
    data class Uninstall(val pkg: String) : Action
    /** Something only the user can do in the app's own settings ("only you can do"). */
    data class OpenAppSettings(val pkg: String) : Action
}

enum class Kind {
    WA_DUPLICATES, WA_BACKUPS, WA_STATUSES, CACHE, APKS, THUMBNAILS, EMPTY_DIRS,
    DUPLICATES, LARGE_VIDEOS, UNUSED_APP, MANUAL_APP, NEEDS_FILES_ACCESS, NEEDS_USAGE_ACCESS,
}

/** label is the app name for UNUSED_APP and MANUAL_APP. */
data class Finding(
    val kind: Kind,
    val label: String?,
    val bytes: Long,
    val action: Action?,
    val checked: Boolean,
)
