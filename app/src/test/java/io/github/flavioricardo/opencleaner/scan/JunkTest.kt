package io.github.flavioricardo.opencleaner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JunkTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun emptyDirs_nested() {
        File(tmp.root, "a/b/c").mkdirs()
        tmp.root.put("d/file.txt", "x")
        File(tmp.root, "d/empty").mkdirs()
        val result = emptyDirs(tmp.root).map { it.relativeTo(tmp.root).invariantSeparatorsPath }.toSet()
        assertEquals(setOf("a", "d/empty"), result)
    }

    @Test fun emptyDirs_keepsStandardAndAndroid() {
        File(tmp.root, "Music").mkdirs()
        File(tmp.root, "Pictures/Old").mkdirs()
        File(tmp.root, "Android/media/x").mkdirs()
        val result = emptyDirs(tmp.root).map { it.relativeTo(tmp.root).invariantSeparatorsPath }.toSet()
        assertEquals(setOf("Pictures/Old"), result)
    }

    @Test fun listFilesUnder_skipsAndroidData() {
        tmp.root.put("Android/data/x/f", "x")
        tmp.root.put("Android/obb/x/f", "x")
        val media = tmp.root.put("Android/media/x/f", "x")
        assertEquals(listOf(media), listFilesUnder(tmp.root))
    }

    @Test fun downloadApks_recursive() {
        val apk = tmp.root.put("Download/sub/app.APK", "x")
        tmp.root.put("Download/doc.pdf", "x")
        assertEquals(listOf(apk), downloadApks(tmp.root))
    }

    @Test fun largeVideos_threshold() {
        val big = tmp.root.sized("Movies/big.mp4", 100L * 1024 * 1024 + 1)
        val photo = tmp.root.sized("Pictures/big.jpg", 100L * 1024 * 1024 + 1)
        val small = tmp.root.sized("Movies/small.mp4", 100L * 1024 * 1024)
        val result = largeVideos(listOf(big, photo, small))
        assertEquals(listOf(big), result)
    }

    @Test fun deleteFiles_countsOnlyRemoved() {
        val gone = tmp.root.put("a", bytes(10))
        val kept = tmp.root.put("b", bytes(20))
        gone.delete()
        assertEquals(20L, deleteFiles(listOf(gone, kept)))
        assertFalse(kept.exists())
    }

    @Test fun deleteFiles_neverRemovesFolderThatGotAFile() {
        val dir = File(tmp.root, "a/b").apply { mkdirs() }
        val late = tmp.root.put("a/b/late.txt", "x")
        deleteFiles(listOf(File(tmp.root, "a")))
        assertTrue(late.exists())
        assertTrue(dir.exists())
    }
}
