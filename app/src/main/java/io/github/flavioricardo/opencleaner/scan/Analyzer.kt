package io.github.flavioricardo.opencleaner.scan

import java.io.File

private const val DAY = 24L * 60 * 60 * 1000
private const val GB = 1024L * 1024 * 1024

// ponytail: 100 MB keeps the per-app cache list short; lower it if people want more apps there.
private const val MIN_APP_CACHE_BYTES = 100L * 1024 * 1024

// ponytail: 100 KB floor keeps thousands of tiny icons out of the duplicate scan; lower it if it misses gains.
private const val MIN_DUPLICATE_BYTES = 100L * 1024

private const val BIGGEST = 50

private val ALWAYS_SHOWN = setOf(Kind.EMPTY_DIRS, Kind.PRIVACY_APP, Kind.NEEDS_FILES_ACCESS, Kind.NEEDS_USAGE_ACCESS)

// WhatsApp media folders by the end of their name, so "WhatsApp Business Video" counts too.
private val WA_TYPES = listOf(
    Kind.WA_OLD_VIDEOS to listOf(" Video", " Video Notes"),
    Kind.WA_OLD_IMAGES to listOf(" Images"),
    Kind.WA_OLD_AUDIO to listOf(" Audio", " Voice Notes"),
    Kind.WA_OLD_DOCS to listOf(" Documents"),
)

/** Which old-media item a WhatsApp Media subfolder feeds, or null (stickers, GIFs...). */
fun whatsAppMediaKind(folder: String): Kind? =
    WA_TYPES.firstOrNull { (_, suffixes) -> suffixes.any { folder.endsWith(it) } }?.first

private val SCREENSHOT_DIRS = listOf("DCIM/Screenshots", "Pictures/Screenshots")
private val RECORDING_DIRS = listOf("DCIM/Screen recordings", "Movies/Screen recordings")

/** The cleanup list only (see [scan]). */
fun analyze(
    storage: File?,
    apps: List<AppInfo>?,
    now: Long,
    options: ScanOptions = ScanOptions(),
    trash: List<Trashed>? = null,
    progress: (Phase, Int, Int) -> Unit = { _, _, _ -> },
): List<Finding> = scan(storage, apps, now, options, trash, progress).findings

/**
 * Builds the list shown after "Analyze", plus the folder sizes. Reads only, never deletes.
 * storage == null: no all-files access. apps == null: no usage access. trash: what is already
 * in the Android trash (null when unknown). progress(phase, done, total) reports each step;
 * total 0 means unknown.
 */
fun scan(
    storage: File?,
    apps: List<AppInfo>?,
    now: Long,
    options: ScanOptions = ScanOptions(),
    trash: List<Trashed>? = null,
    progress: (Phase, Int, Int) -> Unit = { _, _, _ -> },
): Scan {
    val out = mutableListOf<Finding>()
    var tree: DirNode? = null
    var biggest = emptyList<Pair<File, Long>>()

    if (storage == null) {
        out += Finding(Kind.NEEDS_FILES_ACCESS, null, 0, null, false)
    } else {
        val all = listFilesUnder(storage) { progress(Phase.FILES, it, 0) }

        progress(Phase.WHATSAPP, 0, 0)
        val wa = whatsAppRoots(storage)
        fun underWhatsApp(f: File) = wa.any { f.path.startsWith(it.path + File.separator) }

        // Nothing inside a protected folder is ever deleted, by any item.
        val guarded = options.protectedDirs
        fun isProtected(f: File) = guarded.any { f.path == it.path || f.path.startsWith(it.path + File.separator) }
        fun holdsProtected(dir: File) = guarded.any { it.path.startsWith(dir.path + File.separator) }

        val backups = wa.flatMap(::oldBackups).filterNot(::isProtected)
        val statuses = wa.flatMap(::statusFiles).filterNot(::isProtected)
        val apks = downloadApks(storage).filterNot(::isProtected)
        val thumbnails = thumbnailFiles(storage).filterNot(::isProtected)
        // Files another item already deletes stay out of the duplicate scan, so the copy a
        // group keeps is never deleted by some other item.
        val claimed = (backups + statuses + apks + thumbnails).toSet()

        // One stat per file: every call goes through FUSE on a real phone.
        val sizes = all.associateWith { it.length() }
        val sizeOf = { f: File -> sizes[f] ?: f.length() }
        fun files(kind: Kind, list: List<File>, checked: Boolean) =
            Finding(kind, null, list.sumOf(sizeOf), Action.DeleteFiles(list), checked)
        tree = DirNode.build(storage, sizes)
        biggest = sizes.entries.sortedByDescending { it.value }.take(BIGGEST).map { it.key to it.value }

        // Duplicates only in user folders and WhatsApp media: never in WhatsApp's Databases/Backups
        // (the current backup must stay), other apps' Android/ folders or hidden folders.
        val waMedia = wa.map { File(it, "Media") }
        fun duplicateCandidate(f: File): Boolean {
            if (waMedia.any { f.path.startsWith(it.path + File.separator) }) return true
            val rel = f.relativeTo(storage).invariantSeparatorsPath
            return !rel.startsWith("Android/") && rel.split('/').none { it.startsWith(".") }
        }
        val empty = emptyDirs(storage).filterNot { isProtected(it) || holdsProtected(it) }
        val removable = findDuplicates(
            all.filter { it !in claimed && sizeOf(it) >= MIN_DUPLICATE_BYTES && duplicateCandidate(it) },
            sizeOf,
            onRead = { done, total -> progress(Phase.DUPLICATES, done, total) },
        ) {
            // A protected copy always stays; otherwise the copy outside WhatsApp does.
            when {
                isProtected(it) -> -1
                underWhatsApp(it) -> 1
                else -> 0
            }
        }.flatMap { it.remove }.filterNot(::isProtected)

        // Old files: one item each, never a file another item already takes. Hidden files stay
        // out, which also keeps out what the Android trash renamed to ".trashed-...".
        val oldBefore = now - options.oldMonths * 30L * DAY
        val taken = HashSet<File>(claimed).apply { addAll(removable) }
        fun oldIn(dirs: List<File>, extra: (File) -> Boolean = { true }): List<File> =
            all.filter { f ->
                dirs.any { f.path.startsWith(it.path + File.separator) } &&
                    f !in taken && !f.name.startsWith(".") && !isProtected(f) &&
                    extra(f) && f.lastModified() < oldBefore
            }.also { taken.addAll(it) }

        val waOld = WA_TYPES.map { (kind, _) ->
            val dirs = waMedia.flatMap { m ->
                m.listFiles().orEmpty().filter { d -> d.isDirectory && whatsAppMediaKind(d.name) == kind }
            }
            kind to oldIn(dirs)
        }
        val oldDownloads = oldIn(listOf(File(storage, "Download")))
        val oldScreenshots = oldIn(SCREENSHOT_DIRS.map { File(storage, it) })
        val oldRecordings = oldIn(RECORDING_DIRS.map { File(storage, it) })

        out += files(Kind.WA_DUPLICATES, removable.filter(::underWhatsApp), true)
        out += files(Kind.WA_BACKUPS, backups, true)
        out += files(Kind.WA_STATUSES, statuses, true)
        waOld.forEach { (kind, list) -> out += files(kind, list, false) }
        out += files(Kind.APKS, apks, true)
        out += files(Kind.THUMBNAILS, thumbnails, true)
        out += files(Kind.DUPLICATES, removable.filterNot(::underWhatsApp), true)
        out += files(Kind.LARGE_VIDEOS, largeVideos(
            all.filter { it !in taken && !it.name.startsWith(".") && !isProtected(it) },
            minBytes = options.minVideoBytes,
            sizeOf = sizeOf,
        ), false)
        out += files(Kind.OLD_DOWNLOADS, oldDownloads, false)
        out += files(Kind.OLD_SCREENSHOTS, oldScreenshots, false)
        out += files(Kind.OLD_RECORDINGS, oldRecordings, false)
        if (empty.isNotEmpty()) out += Finding(Kind.EMPTY_DIRS, null, 0, Action.DeleteFiles(empty), true)
    }

    // The trash empties itself after 30 days; emptying it now is the user's call.
    if (!trash.isNullOrEmpty()) {
        out += Finding(Kind.TRASH, null, trash.sumOf { it.bytes }, Action.EmptyTrash(trash.map { it.uri }), false)
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
                    now - maxOf(it.lastUsed, it.installed) > options.unusedDays * DAY
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

        apps.filter { !it.system && it.pkg != OWN_PACKAGE && it.sensitive.isNotEmpty() }
            .sortedWith(compareByDescending<AppInfo> { it.sensitive.size }.thenBy { it.label.lowercase() })
            .forEach { out += Finding(Kind.PRIVACY_APP, it.label, 0, Action.OpenAppSettings(it.pkg), false) }
    }

    return Scan(out.filter { it.bytes > 0 || it.kind in ALWAYS_SHOWN }, tree, biggest)
}
