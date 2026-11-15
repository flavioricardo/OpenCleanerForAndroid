package io.github.flavioricardo.opencleaner.scan

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WhatsAppTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun noWhatsApp_emptyRoots() {
        assertEquals(emptyList<File>(), whatsAppRoots(tmp.root))
    }

    @Test fun bothApps_found() {
        File(tmp.root, "Android/media/com.whatsapp/WhatsApp").mkdirs()
        File(tmp.root, "Android/media/com.whatsapp.w4b/WhatsApp Business").mkdirs()
        assertEquals(2, whatsAppRoots(tmp.root).size)
    }

    @Test fun oldBackups_keepsCurrent() {
        val root = File(tmp.root, "WhatsApp")
        root.put("Databases/msgstore.db.crypt14", "current")
        val d1 = root.put("Databases/msgstore-2026-11-10.1.db.crypt14", "old")
        val d2 = root.put("Databases/msgstore-2026-11-11.1.db.crypt15", "old")
        root.put("Databases/wa.db", "contacts")
        assertEquals(setOf(d1, d2), oldBackups(root).toSet())
    }

    @Test fun statuses_skipsNomedia() {
        val root = File(tmp.root, "WhatsApp")
        root.put("Media/.Statuses/.nomedia", "")
        val s = root.put("Media/.Statuses/a.jpg", "status")
        assertEquals(listOf(s), statusFiles(root))
    }
}
