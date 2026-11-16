package io.github.flavioricardo.opencleaner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnalyzerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = 24L * 60 * 60 * 1000
    private val now = 1000 * day
    private val wa = "Android/media/com.whatsapp/WhatsApp"

    private fun app(pkg: String, lastUsed: Long = now, installed: Long = 0, system: Boolean = false, data: Long = 10) =
        AppInfo(pkg, pkg, appBytes = 10, dataBytes = data, cacheBytes = 5, externalCacheBytes = 5, lastUsed = lastUsed, installed = installed, system = system)

    private fun List<Finding>.of(kind: Kind) = singleOrNull { it.kind == kind }
    private fun Finding.files() = (action as Action.DeleteFiles).files

    @Test fun defaults_matchSpec() {
        tmp.root.put("$wa/Databases/msgstore.db.crypt14", "current")
        tmp.root.put("$wa/Databases/msgstore-2026-11-10.1.db.crypt14", "old")
        tmp.root.sized("Movies/big.mp4", 100L * 1024 * 1024 + 1)
        tmp.root.put("DCIM/a.jpg", bytes(200 * 1024))
        tmp.root.put("$wa/Media/WhatsApp Images/a.jpg", bytes(200 * 1024))
        val result = analyze(tmp.root, listOf(app("com.game", lastUsed = 0)), now)

        assertTrue(result.of(Kind.WA_BACKUPS)!!.checked)
        assertTrue(result.of(Kind.WA_DUPLICATES)!!.checked)
        assertTrue(result.of(Kind.CACHE)!!.checked)
        assertFalse(result.of(Kind.LARGE_VIDEOS)!!.checked)
        assertFalse(result.of(Kind.UNUSED_APP)!!.checked)
    }

    @Test fun noUsageAccess_showsNeedsUsage() {
        val result = analyze(tmp.root, null, now)
        assertTrue(result.any { it.kind == Kind.NEEDS_USAGE_ACCESS })
        assertNull(result.of(Kind.CACHE))
        assertNull(result.of(Kind.UNUSED_APP))
    }

    @Test fun noFilesAccess_showsNeedsFiles() {
        val result = analyze(null, listOf(app("com.x")), now)
        assertTrue(result.any { it.kind == Kind.NEEDS_FILES_ACCESS })
        assertTrue(result.none { it.action is Action.DeleteFiles })
        assertNull(result.of(Kind.CACHE)) // the system cache dialog needs all-files access
    }

    @Test fun currentBackup_notDeletedAsDuplicate() {
        tmp.root.put("Download/msgstore.db.crypt14", bytes(200 * 1024))
        val current = tmp.root.put("$wa/Databases/msgstore.db.crypt14", bytes(200 * 1024))
        val deleted = analyze(tmp.root, null, now).flatMap { (it.action as? Action.DeleteFiles)?.files.orEmpty() }
        assertFalse(current in deleted)
    }

    @Test fun otherAppsAndHiddenFolders_noDuplicates() {
        tmp.root.put("Android/media/com.game/a.bin", bytes(200 * 1024))
        tmp.root.put("Android/media/com.game/b.bin", bytes(200 * 1024))
        tmp.root.put(".hidden/a.bin", bytes(200 * 1024, seed = 1))
        tmp.root.put(".hidden/b.bin", bytes(200 * 1024, seed = 1))
        assertNull(analyze(tmp.root, null, now).of(Kind.DUPLICATES))
    }

    @Test fun userFolders_duplicatesFound() {
        tmp.root.put("Download/a.bin", bytes(200 * 1024))
        tmp.root.put("Documents/a.bin", bytes(200 * 1024))
        assertEquals(1, analyze(tmp.root, null, now).of(Kind.DUPLICATES)!!.files().size)
    }

    @Test fun unusedApp_rules() {
        val apps = listOf(
            app("com.system", lastUsed = 0, system = true),
            app("com.recent", lastUsed = now - 29 * day),
            app("com.old", lastUsed = now - 31 * day),
            app("com.never", lastUsed = 0),
            app("com.justinstalled", lastUsed = 0, installed = now - day),
            app(OWN_PACKAGE, lastUsed = 0),
            app("com.keyboard", lastUsed = 0).copy(alwaysOn = true),
        )
        val unused = analyze(null, apps, now).filter { it.kind == Kind.UNUSED_APP }.map { it.label }.toSet()
        assertEquals(setOf("com.old", "com.never"), unused)
    }

    @Test fun manualApp_bigDataOnly() {
        val gb = 1024L * 1024 * 1024
        val apps = listOf(app("com.netflix", data = 2 * gb), app("com.whatsapp", data = 30 * gb), app("com.small"))
        val manual = analyze(null, apps, now).filter { it.kind == Kind.MANUAL_APP }.map { it.label }
        assertEquals(listOf("com.netflix"), manual)
    }

    @Test fun waDuplicate_keepsGalleryCopy() {
        val gallery = tmp.root.put("DCIM/a.jpg", bytes(200 * 1024))
        val copy = tmp.root.put("$wa/Media/WhatsApp Images/a.jpg", bytes(200 * 1024))
        val result = analyze(tmp.root, null, now)
        assertEquals(listOf(copy), result.of(Kind.WA_DUPLICATES)!!.files())
        assertNull(result.of(Kind.DUPLICATES))
        assertTrue(result.flatMap { (it.action as? Action.DeleteFiles)?.files.orEmpty() }.none { it == gallery })
    }

    @Test fun statusDuplicate_neverDeletesBothCopies() {
        // Same video as a seen status and in the media folder: statuses delete theirs,
        // so the duplicate scan must not also delete the other copy.
        tmp.root.put("$wa/Media/.Statuses/v.mp4", bytes(200 * 1024), modified = 1_000_000L)
        val kept = tmp.root.put("$wa/Media/WhatsApp Video/v.mp4", bytes(200 * 1024), modified = 9_000_000L)
        val deleted = analyze(tmp.root, null, now).flatMap { (it.action as? Action.DeleteFiles)?.files.orEmpty() }
        assertFalse(kept in deleted)
    }

    @Test fun currentBackup_neverListed() {
        tmp.root.put("$wa/Databases/msgstore.db.crypt14", "current")
        tmp.root.put("$wa/Databases/msgstore-2026-11-10.1.db.crypt14", "old")
        val deleted = analyze(tmp.root, null, now).flatMap { (it.action as? Action.DeleteFiles)?.files.orEmpty() }
        assertTrue(deleted.none { it.name == "msgstore.db.crypt14" })
    }

    @Test fun noWhatsApp_noWhatsAppItems() {
        tmp.root.put("Download/a.txt", "x")
        val kinds = analyze(tmp.root, null, now).map { it.kind }
        assertTrue(kinds.none { it.name.startsWith("WA_") })
    }

    @Test fun zeroByteItems_hidden() {
        val kinds = analyze(tmp.root, listOf(app("com.x").copy(cacheBytes = 0, externalCacheBytes = 0)), now).map { it.kind }
        assertFalse(Kind.CACHE in kinds)
        assertFalse(Kind.APKS in kinds)
    }

    @Test fun progress_reportsFilesAndDuplicates() {
        tmp.root.put("DCIM/a.jpg", bytes(200 * 1024))
        tmp.root.put("Pictures/a.jpg", bytes(200 * 1024))
        val reports = mutableListOf<Triple<Phase, Int, Int>>()
        analyze(tmp.root, null, now) { phase, done, total -> reports += Triple(phase, done, total) }

        val phases = reports.map { it.first }.toSet()
        assertTrue(phases.containsAll(listOf(Phase.FILES, Phase.WHATSAPP, Phase.DUPLICATES)))
        // 2 head reads plus 2 full reads (the files are over 64 KB).
        val last = reports.last { it.first == Phase.DUPLICATES }
        assertEquals(4, last.second)
        assertEquals(4, last.third)
    }

    // The system dialog only clears external caches; internal cache is per app, in its settings.
    @Test fun cache_splitsExternalAndInternal() {
        val mb = 1024L * 1024
        val big = app("com.big").copy(cacheBytes = 500 * mb, externalCacheBytes = 100 * mb)
        val small = app("com.small").copy(cacheBytes = 60 * mb, externalCacheBytes = 10 * mb)
        val result = analyze(tmp.root, listOf(big, small), now)

        assertEquals(110 * mb, result.of(Kind.CACHE)!!.bytes)
        val perApp = result.filter { it.kind == Kind.APP_CACHE }
        assertEquals(listOf("com.big"), perApp.map { it.label })
        assertEquals(400 * mb, perApp.single().bytes)
        assertFalse(perApp.single().checked)
        assertEquals(Action.OpenAppSettings("com.big"), perApp.single().action)
    }
}
