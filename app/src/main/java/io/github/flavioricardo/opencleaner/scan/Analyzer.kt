package io.github.flavioricardo.opencleaner.scan

import java.io.File

private const val DAY = 24L * 60 * 60 * 1000
private const val GB = 1024L * 1024 * 1024

// ponytail: 100 KB floor keeps thousands of tiny icons out of the duplicate scan; lower it if it misses gains.
private const val MIN_DUPLICATE_BYTES = 100L * 1024

private val ALWAYS_SHOWN = setOf(Kind.EMPTY_DIRS, Kind.NEEDS_FILES_ACCESS, Kind.NEEDS_USAGE_ACCESS)

/**
 * Builds the list shown after "Analyze". Reads only, never deletes.
 * storage == null: no all-files access. apps == null: no usage access.
 */
fun analyze(storage: File?, apps: List<AppInfo>?, now: Long): List<Finding> {
    val out = mutableListOf<Finding>()

    if (storage == null) {
        out += Finding(Kind.NEEDS_FILES_ACCESS, null, 0, null, false)
    } else {
        val wa = whatsAppRoots(storage)
        fun underWhatsApp(f: File) = wa.any { f.path.startsWith(it.path + File.separator) }

        val backups = wa.flatMap(::oldBackups)
        val statuses = wa.flatMap(::statusFiles)
        val apks = downloadApks(storage)
        val thumbnails = thumbnailFiles(storage)
        // Files another item already deletes stay out of the duplicate scan, so the copy a
        // group keeps is never deleted by some other item.
        val claimed = (backups + statuses + apks + thumbnails).toSet()

        val all = listFilesUnder(storage)
        val removable = findDuplicates(
            all.filter { it !in claimed && it.length() >= MIN_DUPLICATE_BYTES },
        ) { if (underWhatsApp(it)) 1 else 0 }.flatMap { it.remove }
        val removableSet = removable.toSet()

        out += files(Kind.WA_DUPLICATES, removable.filter(::underWhatsApp), true)
        out += files(Kind.WA_BACKUPS, backups, true)
        out += files(Kind.WA_STATUSES, statuses, true)
        out += files(Kind.APKS, apks, true)
        out += files(Kind.THUMBNAILS, thumbnails, true)
        out += files(Kind.DUPLICATES, removable.filterNot(::underWhatsApp), true)
        out += files(Kind.LARGE_VIDEOS, largeVideos(all.filter { it !in claimed && it !in removableSet }), false)
        val empty = emptyDirs(storage)
        if (empty.isNotEmpty()) out += Finding(Kind.EMPTY_DIRS, null, 0, Action.DeleteFiles(empty), true)
    }

    if (apps == null) {
        out += Finding(Kind.NEEDS_USAGE_ACCESS, null, 0, null, false)
    } else {
        out += Finding(Kind.CACHE, null, apps.sumOf { it.cacheBytes }, Action.ClearAllCache, true)

        val unused = apps
            .filter { !it.system && it.pkg != OWN_PACKAGE && now - maxOf(it.lastUsed, it.installed) > 30 * DAY }
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

private fun files(kind: Kind, list: List<File>, checked: Boolean) =
    Finding(kind, null, list.sumOf { it.length() }, Action.DeleteFiles(list), checked)
