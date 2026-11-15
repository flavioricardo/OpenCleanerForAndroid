package io.github.flavioricardo.opencleaner.scan

import java.io.File

/**
 * Deletes files and empty folders. Returns the bytes actually removed: a file that is already
 * gone, or that cannot be deleted, counts zero. Folders are removed bottom-up and only when
 * empty, so a file that appeared after the analysis is never touched.
 */
fun deleteFiles(files: List<File>): Long {
    var freed = 0L
    for (f in files) {
        if (f.isDirectory) {
            f.walkBottomUp().forEach { if (it.isDirectory) it.delete() }
            continue
        }
        val size = f.length()
        if (f.isFile && f.delete()) freed += size
    }
    return freed
}
