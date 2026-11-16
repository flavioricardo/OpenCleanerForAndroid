package io.github.flavioricardo.opencleaner.scan

import java.io.File

const val OWN_PACKAGE = "io.github.flavioricardo.opencleaner"

/** Access an app holds that deserves a look (granted, not only requested). */
enum class Sensitive { BACKGROUND_LOCATION, CAMERA, MICROPHONE }

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
    val sensitive: Set<Sensitive> = emptySet(),
)

/** A media item already in the Android trash (MediaStore IS_TRASHED). */
data class Trashed(val uri: String, val bytes: Long)

sealed interface Action {
    data class DeleteFiles(val files: List<File>) : Action
    data object ClearAllCache : Action
    data class Uninstall(val pkg: String) : Action
    /** Something only the user can do in the app's own settings ("only you can do"). */
    data class OpenAppSettings(val pkg: String) : Action
    /** Deletes for good what is already in the Android trash (system dialog). */
    data class EmptyTrash(val uris: List<String>) : Action
}

/** What the user can tune in Options. Paths in protectedDirs are absolute. */
data class ScanOptions(
    val minVideoBytes: Long = 100L * 1024 * 1024,
    val unusedDays: Int = 30,
    val protectedDirs: List<File> = emptyList(),
    /** Old WhatsApp media, downloads, screenshots and recordings: older than this. */
    val oldMonths: Int = 6,
)

/** Steps of an analysis, in the order they run. APPS is loaded by the UI before analyze(). */
enum class Phase { APPS, FILES, WHATSAPP, DUPLICATES }

enum class Kind {
    WA_DUPLICATES, WA_BACKUPS, WA_STATUSES, WA_OLD_VIDEOS, WA_OLD_IMAGES, WA_OLD_AUDIO, WA_OLD_DOCS,
    CACHE, TRASH, APKS, THUMBNAILS, EMPTY_DIRS, DUPLICATES, LARGE_VIDEOS,
    OLD_DOWNLOADS, OLD_SCREENSHOTS, OLD_RECORDINGS,
    UNUSED_APP, MANUAL_APP, APP_CACHE, PRIVACY_APP, NEEDS_FILES_ACCESS, NEEDS_USAGE_ACCESS,
}

/** label is the app name for UNUSED_APP, MANUAL_APP, APP_CACHE and PRIVACY_APP. */
data class Finding(
    val kind: Kind,
    val label: String?,
    val bytes: Long,
    val action: Action?,
    val checked: Boolean,
)

/** Everything one analysis found: the cleanup list plus what the Storage page shows. */
class Scan(
    val findings: List<Finding>,
    /** Sizes per folder of shared storage; null without all-files access. */
    val tree: DirNode?,
    /** The largest files, biggest first. */
    val biggest: List<Pair<File, Long>>,
)
