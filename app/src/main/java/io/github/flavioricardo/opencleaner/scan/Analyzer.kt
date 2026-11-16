package io.github.flavioricardo.opencleaner.scan

import java.io.File

private const val DAY = 24L * 60 * 60 * 1000
private const val GB = 1024L * 1024 * 1024

// ponytail: 100 MB keeps the per-app cache list short; lower it if people want more apps there.
private const val MIN_APP_CACHE_BYTES = 100L * 1024 * 1024

// ponytail: 100 KB floor keeps thousands of tiny icons out of the duplicate scan; lower it if it misses gains.
private const val MIN_DUPLICATE_BYTES = 100L * 1024

private val ALWAYS_SHOWN = setOf(Kind.EMPTY_DIRS, Kind.NEEDS_FILES_ACCESS, Kind.NEEDS_USAGE_ACCESS)

/**
 * Builds the list shown after "Analyze". Reads only, never deletes.
 * storage == null: no all-files access. apps == null: no usage access.
 * progress(phase, done, total) reports each step; total 0 means unknown.
 */
fun analyze(
    storage: File?,
    apps: List<AppInfo>?,
    now: Long,
    progress: (Phase, Int, Int) -> Unit = { _, _, _ -> },
): List<Finding> {
    val out = mutableListOf<Finding>()

    if (storage == null) {
        out += Finding(Kind.NEEDS_FILES_ACCESS, null, 0, null, false)
    } else {
        val all = listFilesUnder(storage) { progress(Phase.FILES, it, 0) }

        progress(Phase.WHATSAPP, 0, 0)
        val wa = whatsAppRoots(storage)
        fun underWhatsApp(f: File) = wa.any { f.path.startsWith(it.path + File.separator) }

        val backups = wa.flatMap(::oldBackups)
        val statuses = wa.flatMap(::statusFiles)
        val apks = downloadApks(storage)
        val thumbnails = thumbnailFiles(storage)
        // Files another item already deletes stay out of the duplicate scan, so the copy a
        // group keeps is never deleted by some other item.
        val claimed = (backups + statuses + apks + thumbnails).toSet()

        // One stat per file: every call goes through FUSE on a real phone.
        val sizes = all.associateWith { it.length() }
        val sizeOf = { f: File -> sizes[f] ?: f.length() }
        fun files(kind: Kind, list: List<File>, checked: Boolean) =
            Finding(kind, null, list.sumOf(sizeOf), Action.DeleteFiles(list), checked)

        // Duplicates only in user folders and WhatsApp media: never in WhatsApp's Databases/Backups
        // (the current backup must stay), other apps' Android/ folders or hidden folders.
        val waMedia = wa.map { File(it, "Media") }
        fun duplicateCandidate(f: File): Boolean {
            if (waMedia.any { f.path.startsWith(it.path + File.separator) }) return true
            val rel = f.relativeTo(storage).invariantSeparatorsPath
            return !rel.startsWith("Android/") && rel.split('/').none { it.startsWith(".") }
        }
        val empty = emptyDirs(storage)
        val removable = findDuplicates(
            all.filter { it !in claimed && sizeOf(it) >= MIN_DUPLICATE_BYTES && duplicateCandidate(it) },
            sizeOf,
            onRead = { done, total -> progress(Phase.DUPLICATES, done, total) },
        ) { if (underWhatsApp(it)) 1 else 0 }.flatMap { it.remove }
        val removableSet = removable.toSet()

        out += files(Kind.WA_DUPLICATES, removable.filter(::underWhatsApp), true)
        out += files(Kind.WA_BACKUPS, backups, true)
        out += files(Kind.WA_STATUSES, statuses, true)
        out += files(Kind.APKS, apks, true)
        out += files(Kind.THUMBNAILS, thumbnails, true)
        out += files(Kind.DUPLICATES, removable.filterNot(::underWhatsApp), true)
        out += files(Kind.LARGE_VIDEOS, largeVideos(all.filter { it !in claimed && it !in removableSet }, sizeOf = sizeOf), false)
        if (empty.isNotEmpty()) out += Finding(Kind.EMPTY_DIRS, null, 0, Action.DeleteFiles(empty), true)
    }

    if (apps == null) {
        out += Finding(Kind.NEEDS_USAGE_ACCESS, null, 0, null, false)
    } else {
        // The system cache dialog only opens for apps with all-files access, and only clears
        // external caches. Internal cache is cleared app by app in the app's settings page.
        if (storage != null) out += Finding(Kind.CACHE, null, apps.sumOf { it.externalCacheBytes }, Action.ClearAllCache, true)
        apps.filter { it.pkg != OWN_PACKAGE && it.cacheBytes - it.externalCacheBytes >= MIN_APP_CACHE_BYTES }
            .sortedByDescending { it.cacheBytes - it.externalCacheBytes }
            .forEach {
                out += Finding(Kind.APP_CACHE, it.label, it.cacheBytes - it.externalCacheBytes, Action.OpenAppSettings(it.pkg), false)
            }

        val unused = apps
            .filter {
                !it.system && !it.alwaysOn && it.pkg != OWN_PACKAGE &&
                    now - maxOf(it.lastUsed, it.installed) > 30 * DAY
            }
            .sortedByDescending { it.appBytes + it.dataBytes }
        unused.forEach {
            out += Finding(Kind.UNUSED_APP, it.label, it.appBytes + it.dataBytes, Action.Uninstall(it.pkg), false)
        }

        val unusedPkgs = unused.map { it.pkg }.toSet()
        apps.filter {
            !it.system && it.pkg !in unusedPkgs && !it.pkg.startsWith("com.whatsapp") &&
                it.dataBytes - it.cacheBytes >= GB
        }
            .sortedByDescending { it.dataBytes - it.cacheBytes }
            .forEach {
                out += Finding(Kind.MANUAL_APP, it.label, it.dataBytes - it.cacheBytes, Action.OpenAppSettings(it.pkg), false)
            }
    }

    return out.filter { it.bytes > 0 || it.kind in ALWAYS_SHOWN }
}
