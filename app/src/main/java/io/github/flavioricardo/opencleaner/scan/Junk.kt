package io.github.flavioricardo.opencleaner.scan

import java.io.File

private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "3gp", "webm")

/** Standard top-level folders that apps expect to exist, even when empty. */
private val STANDARD_DIRS = setOf(
    "Alarms", "Audiobooks", "DCIM", "Documents", "Download", "Movies", "Music",
    "Notifications", "Pictures", "Podcasts", "Recordings", "Ringtones",
)

/** Every file in shared storage except Android/data and Android/obb (off limits on Android 11+). */
fun listFilesUnder(storage: File, onFile: (Int) -> Unit = {}): List<File> {
    val blocked = setOf(File(storage, "Android/data").path, File(storage, "Android/obb").path)
    val out = mutableListOf<File>()
    storage.walkTopDown()
        .onEnter { it.path !in blocked }
        .filter { it.isFile }
        .forEach {
            out += it
            if (out.size % 256 == 0) onFile(out.size)
        }
    onFile(out.size)
    return out
}

fun downloadApks(storage: File): List<File> =
    File(storage, "Download").walkTopDown()
        .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
        .toList()

fun thumbnailFiles(storage: File): List<File> =
    File(storage, "DCIM/.thumbnails").walkTopDown().filter { it.isFile }.toList()

fun largeVideos(
    files: List<File>,
    minBytes: Long = 100L * 1024 * 1024,
    sizeOf: (File) -> Long = { it.length() },
): List<File> =
    files.filter { it.extension.lowercase() in VIDEO_EXTENSIONS && sizeOf(it) > minBytes }

/**
 * Folders with no file anywhere in their subtree. Returns only the topmost one of each empty
 * subtree. Skips Android/ and keeps the standard top-level folders themselves.
 */
fun emptyDirs(storage: File): List<File> {
    val out = mutableListOf<File>()

    // True when the subtree has a file (an unreadable folder counts as having one).
    fun scan(dir: File): Boolean {
        val children = dir.listFiles() ?: return true
        var hasFile = false
        val emptyChildren = mutableListOf<File>()
        for (child in children) {
            if (!child.isDirectory) hasFile = true
            else if (scan(child)) hasFile = true
            else emptyChildren += child
        }
        if (hasFile) out += emptyChildren
        return hasFile
    }

    for (top in storage.listFiles().orEmpty()) {
        if (!top.isDirectory || top.name == "Android") continue
        if (top.name in STANDARD_DIRS) {
            // The folder stays, but its empty subfolders can go.
            top.listFiles().orEmpty().filter { it.isDirectory && !scan(it) }.forEach { out += it }
        } else if (!scan(top)) {
            out += top
        }
    }
    return out
}
