package com.ivor.ivormusic.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.OutputStream

/**
 * Where a download lands in the user's shared storage.
 *
 * Both live under the public Downloads directory so they show up in the Files
 * app as Downloads > Koda > Music / Video. [Environment.DIRECTORY_DOWNLOADS] is
 * the string "Download" (singular) even though Files displays it as "Downloads"
 * - do not spell it out by hand.
 */
enum class DownloadMediaType(
    val folderName: String,
    val mimeType: String,
    val extension: String
) {
    MUSIC("Music", "audio/mp4", "m4a"),
    VIDEO("Video", "video/mp4", "mp4");

    val relativePath: String
        get() = "${Environment.DIRECTORY_DOWNLOADS}/Koda/$folderName"
}

/**
 * Writes downloads into shared storage through MediaStore so they are visible
 * and manageable in the Files app, rather than hidden in the app's private
 * filesDir.
 *
 * Consequences worth knowing about, because they shape the repository:
 *  - the user can delete these files behind our back, so stored metadata must
 *    be reconciled against MediaStore rather than trusted,
 *  - files survive uninstall,
 *  - no storage permission is needed for entries this app created (API 29+),
 *    which is why this never touches MANAGE_EXTERNAL_STORAGE.
 *
 * Writes go out as IS_PENDING and are only published on success, so a partial
 * transfer never appears in the Files app as a playable file.
 */
class DownloadStorage(private val context: Context) {

    companion object {
        private const val TAG = "DownloadStorage"

        /** Characters that are illegal in FAT/exFAT names, plus control chars. */
        private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|\x00-\x1F]""")

        /**
         * Cap on the name portion. Most Android volumes are exFAT with a 255
         * byte limit; multi-byte titles can blow past that well before 255
         * characters, so this stays conservative.
         */
        private const val MAX_NAME_LENGTH = 100
    }

    private val resolver get() = context.contentResolver

    private val collection: Uri
        get() = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Turn a title/artist into something safe and readable on disk. These names
     * are user-facing now, so "Blinding Lights - The Weeknd.m4a" beats the old
     * "<videoId>.m4a".
     */
    fun buildFileName(title: String, artist: String, type: DownloadMediaType): String {
        val base = if (artist.isBlank()) title else "$title - $artist"
        val safe = ILLEGAL_FILENAME_CHARS.replace(base, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
            .take(MAX_NAME_LENGTH)
            .trim()
            .ifBlank { "Koda download" }
        return "$safe.${type.extension}"
    }

    /**
     * Create a pending entry and return its URI, or null if MediaStore refused.
     * The file stays invisible to other apps until [publish].
     */
    fun createPending(displayName: String, type: DownloadMediaType): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, type.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, type.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaStore entry for $displayName", e)
            null
        }
    }

    fun openOutput(uri: Uri): OutputStream? {
        return try {
            resolver.openOutputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open output for $uri", e)
            null
        }
    }

    /** Clear IS_PENDING, making the file visible in the Files app. */
    fun publish(uri: Uri) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish $uri", e)
        }
    }

    /**
     * Delete a download. Returns false when the row was already gone, which is
     * a normal outcome if the user deleted it from the Files app.
     */
    fun delete(uri: Uri): Boolean {
        return try {
            resolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete $uri", e)
            false
        }
    }

    /**
     * Whether the row still exists. Used to drop metadata for files the user
     * removed outside the app.
     */
    fun exists(uri: Uri): Boolean {
        return try {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Every published file this app owns under the given Koda folder, keyed by
     * URI. Lets the repository reconcile in one query rather than probing each
     * stored entry individually.
     */
    fun listExisting(type: DownloadMediaType): Map<Uri, String> {
        val result = mutableMapOf<Uri, String>()
        try {
            resolver.query(
                collection,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME
                ),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("${type.relativePath}%"),
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                    result[uri] = cursor.getString(nameColumn) ?: ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list ${type.relativePath}", e)
        }
        return result
    }
}
