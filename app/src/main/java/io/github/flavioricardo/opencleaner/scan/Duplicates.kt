package io.github.flavioricardo.opencleaner.scan

import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class DuplicateGroup(val keep: File, val remove: List<File>, val size: Long)

private const val HEAD_BYTES = 64L * 1024

/**
 * Groups files with identical content (byte for byte). In each group the file with the
 * lowest keepScore stays; ties keep the oldest (lastModified). Unreadable files are skipped.
 * Only files that share a size are ever read, and the full hash only runs when the first
 * 64 KB already match.
 */
fun findDuplicates(
    files: List<File>,
    sizeOf: (File) -> Long = { it.length() },
    keepScore: (File) -> Long,
): List<DuplicateGroup> =
    files.groupBy(sizeOf)
        .filter { (size, same) -> size > 0 && same.size > 1 }
        .flatMap { (size, same) ->
            same.groupByNotNull { sha256(it, HEAD_BYTES) }.values
                .filter { it.size > 1 }
                .flatMap { candidates ->
                    if (size <= HEAD_BYTES) listOf(candidates)
                    else candidates.groupByNotNull { sha256(it, Long.MAX_VALUE) }.values
                }
                .filter { it.size > 1 }
                .map { copies ->
                    val sorted = copies.sortedWith(
                        compareBy<File>({ keepScore(it) }, { it.lastModified() }, { it.path })
                    )
                    DuplicateGroup(sorted.first(), sorted.drop(1), size)
                }
        }

private fun <K : Any> List<File>.groupByNotNull(key: (File) -> K?): Map<K, List<File>> {
    val out = LinkedHashMap<K, MutableList<File>>()
    for (f in this) {
        val k = key(f) ?: continue
        out.getOrPut(k) { mutableListOf() }.add(f)
    }
    return out
}

private fun sha256(file: File, limit: Long): String? = try {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        var left = limit
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            md.update(buf, 0, n)
            left -= n
        }
    }
    md.digest().joinToString("") { "%02x".format(it) }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}
