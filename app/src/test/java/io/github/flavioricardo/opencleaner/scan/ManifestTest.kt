package io.github.flavioricardo.opencleaner.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun noInternetPermission() {
        assertFalse(manifest.contains("android.permission.INTERNET"))
    }

    @Test fun declaresSpecPermissions() {
        listOf("MANAGE_EXTERNAL_STORAGE", "PACKAGE_USAGE_STATS", "QUERY_ALL_PACKAGES", "REQUEST_DELETE_PACKAGES")
            .forEach { assertTrue(it, manifest.contains("android.permission.$it")) }
    }
}
