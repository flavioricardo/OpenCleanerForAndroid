package io.github.flavioricardo.opencleaner.scan

import java.io.File
import java.io.RandomAccessFile

fun File.put(path: String, content: ByteArray, modified: Long = 1_000_000L): File =
    File(this, path).apply {
        parentFile!!.mkdirs()
        writeBytes(content)
        setLastModified(modified)
    }

fun File.put(path: String, text: String, modified: Long = 1_000_000L): File =
    put(path, text.toByteArray(), modified)

/** Sparse file of the given length (no real disk use on Linux). */
fun File.sized(path: String, length: Long): File =
    File(this, path).apply {
        parentFile!!.mkdirs()
        RandomAccessFile(this, "rw").use { it.setLength(length) }
    }

fun bytes(size: Int, seed: Int = 0) = ByteArray(size) { (it * 31 + seed).toByte() }
