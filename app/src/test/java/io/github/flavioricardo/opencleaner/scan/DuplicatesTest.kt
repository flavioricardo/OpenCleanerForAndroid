package io.github.flavioricardo.opencleaner.scan

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DuplicatesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun sameContentDifferentNames_grouped() {
        val a = tmp.root.put("a.jpg", "same content")
        val b = tmp.root.put("b.jpg", "same content")
        val groups = findDuplicates(listOf(a, b)) { 0 }
        assertEquals(1, groups.size)
        assertEquals(1, groups[0].remove.size)
    }

    @Test fun sameSizeDifferentContent_notGrouped() {
        val a = tmp.root.put("a.bin", "0123456789")
        val b = tmp.root.put("b.bin", "9876543210")
        assertEquals(0, findDuplicates(listOf(a, b)) { 0 }.size)
    }

    @Test fun differentAfterFirst64Kb_notGrouped() {
        val head = bytes(64 * 1024)
        val a = tmp.root.put("a.bin", head + byteArrayOf(1))
        val b = tmp.root.put("b.bin", head + byteArrayOf(2))
        assertEquals(0, findDuplicates(listOf(a, b)) { 0 }.size)
    }

    @Test fun keepsLowestScore() {
        val gallery = tmp.root.put("DCIM/x.jpg", "photo", modified = 9_000_000L)
        val wa = tmp.root.put("WhatsApp/x.jpg", "photo", modified = 1_000_000L)
        val group = findDuplicates(listOf(wa, gallery)) { if ("WhatsApp" in it.path) 1 else 0 }.single()
        assertEquals(gallery, group.keep)
        assertEquals(listOf(wa), group.remove)
    }

    @Test fun tieKeepsOldest() {
        val newer = tmp.root.put("a/x.jpg", "photo", modified = 9_000_000L)
        val older = tmp.root.put("b/x.jpg", "photo", modified = 1_000_000L)
        assertEquals(older, findDuplicates(listOf(newer, older)) { 0 }.single().keep)
    }

    @Test fun uniqueSizes_neverRead() {
        val a = tmp.root.put("a.txt", "1")
        val b = tmp.root.put("b.txt", "22").apply { setReadable(false) }
        assertEquals(0, findDuplicates(listOf(a, b)) { 0 }.size)
    }

    @Test fun emptyFiles_ignored() {
        val a = tmp.root.put("a", "")
        val b = tmp.root.put("b", "")
        assertEquals(0, findDuplicates(listOf(a, b)) { 0 }.size)
    }
}
