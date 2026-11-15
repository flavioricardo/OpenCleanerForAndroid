package io.github.flavioricardo.opencleaner.scan

import java.io.File

/** WhatsApp and WhatsApp Business media folders that exist under shared storage. */
fun whatsAppRoots(storage: File): List<File> = listOf(
    File(storage, "Android/media/com.whatsapp/WhatsApp"),
    File(storage, "Android/media/com.whatsapp.w4b/WhatsApp Business"),
).filter { it.isDirectory }

/** Dated local backups. The current one (msgstore.db.cryptNN, no date) never matches. */
val BACKUP_DATED = Regex("""msgstore-\d{4}-\d{2}-\d{2}\.\d+\.db\.crypt\d+""")

fun oldBackups(root: File): List<File> =
    File(root, "Databases").listFiles().orEmpty()
        .filter { it.isFile && BACKUP_DATED.matches(it.name) }

/** Statuses already seen (WhatsApp keeps a copy for a while). */
fun statusFiles(root: File): List<File> =
    File(root, "Media/.Statuses").listFiles().orEmpty()
        .filter { it.isFile && it.name != ".nomedia" }
