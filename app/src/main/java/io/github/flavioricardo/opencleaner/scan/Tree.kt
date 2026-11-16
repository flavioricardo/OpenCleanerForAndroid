package io.github.flavioricardo.opencleaner.scan

import java.io.File

/** One folder of shared storage with the size and file count of everything below it. */
class DirNode(val file: File) {
    var bytes = 0L
        private set
    var files = 0
        private set
    private val kids = HashMap<String, DirNode>()

    /** Subfolders, biggest first. */
    val children: List<DirNode> get() = kids.values.sortedByDescending { it.bytes }

    /** The node for a folder below this one, or null when nothing was found there. */
    fun find(dir: File): DirNode? {
        if (dir.path == file.path) return this
        val rel = dir.path.removePrefix(file.path + File.separator)
        if (rel == dir.path) return null
        var node: DirNode = this
        for (part in rel.split(File.separatorChar)) node = node.kids[part] ?: return null
        return node
    }

    private fun add(size: Long) {
        bytes += size
        files++
    }

    companion object {
        /** Folder sizes from the file sizes the analysis already read: no second walk. */
        fun build(root: File, sizes: Map<File, Long>): DirNode {
            val top = DirNode(root)
            val prefix = root.path + File.separator
            for ((f, size) in sizes) {
                top.add(size)
                val parts = f.path.removePrefix(prefix).split(File.separatorChar)
                var node = top
                for (part in parts.dropLast(1)) {
                    node = node.kids.getOrPut(part) { DirNode(File(node.file, part)) }
                    node.add(size)
                }
            }
            return top
        }
    }
}
