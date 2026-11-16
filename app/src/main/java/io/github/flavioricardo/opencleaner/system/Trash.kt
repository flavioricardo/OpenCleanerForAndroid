package io.github.flavioricardo.opencleaner.system

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import io.github.flavioricardo.opencleaner.scan.Trashed
import java.io.File

// The Android trash (MediaStore IS_TRASHED): 30 days to undo, then the system deletes for good.

// SQLite allows 999 arguments per query, and a PendingIntent carries its URIs through Binder.
private const val BATCH = 500

private val FILES: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

/** The URI of a media row in its own collection: trash and delete requests want these. */
private fun mediaUri(id: Long, type: Int): Uri? = when (type) {
    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else -> null
}?.let { ContentUris.withAppendedId(it, id) }

/** Photos, videos and audio already in the trash. Empty when MediaStore cannot be read. */
fun loadTrash(ctx: Context): List<Trashed> = runCatching {
    val args = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY) }
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.MEDIA_TYPE)
    ctx.contentResolver.query(FILES, projection, args, null)?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val uri = mediaUri(c.getLong(0), c.getInt(2)) ?: continue
                add(Trashed(uri.toString(), c.getLong(1)))
            }
        }
    }.orEmpty()
}.getOrDefault(emptyList())

/** Photos, videos and audio among these files, with their MediaStore URIs. Other files are left out. */
fun mediaUris(ctx: Context, files: List<File>): Map<File, Uri> {
    val out = HashMap<File, Uri>()
    val byPath = files.associateBy { it.path }
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.MEDIA_TYPE)
    for (chunk in files.chunked(BATCH)) {
        val where = "${MediaStore.Files.FileColumns.DATA} IN (${chunk.joinToString(",") { "?" }})"
        runCatching {
            ctx.contentResolver.query(FILES, projection, where, chunk.map { it.path }.toTypedArray(), null)?.use { c ->
                while (c.moveToNext()) {
                    val file = byPath[c.getString(1)] ?: continue
                    out[file] = mediaUri(c.getLong(0), c.getInt(2)) ?: continue
                }
            }
        }
    }
    return out
}

/** System dialogs that move these items to the trash, one per batch. */
fun trashRequests(ctx: Context, uris: List<Uri>): List<PendingIntent> =
    uris.chunked(BATCH).map { MediaStore.createTrashRequest(ctx.contentResolver, it, true) }

/** System dialogs that delete these items for good, one per batch. */
fun deleteRequests(ctx: Context, uris: List<Uri>): List<PendingIntent> =
    uris.chunked(BATCH).map { MediaStore.createDeleteRequest(ctx.contentResolver, it) }
