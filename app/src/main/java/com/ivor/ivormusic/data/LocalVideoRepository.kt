package com.ivor.ivormusic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The device's own video files, read straight from MediaStore.
 *
 * Deliberately a plain per-caller instance rather than a process-wide store:
 * nothing here is written, so two readers cannot disagree, and the list is
 * re-read whenever the Library asks rather than cached across a scan that the
 * system may have run in between.
 */
class LocalVideoRepository(private val context: Context) {

    /**
     * Every indexed video, newest first.
     *
     * Zero-duration rows are dropped. MediaStore indexes a file the moment it
     * appears, so a video still being written - a screen recording in progress,
     * a half-finished transfer - is present with no duration and no decodable
     * frame, and would show as an unplayable row with a blank thumbnail.
     */
    suspend fun getVideos(): List<LocalVideo> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
        )

        val videos = mutableListOf<LocalVideo>()
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val durationMs = cursor.getLong(durationColumn)
                    if (durationMs <= 0L) continue

                    val displayName = cursor.getString(nameColumn)
                    val title = cursor.getString(titleColumn)
                        ?.takeIf { it.isNotBlank() }
                        ?: displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                        ?: displayName
                        ?: "Video $id"

                    // DATE_ADDED and DATE_MODIFIED are seconds, not millis, and
                    // 0 means the provider had nothing rather than 1970.
                    val addedSeconds = cursor.getLong(addedColumn).takeIf { it > 0 }
                        ?: cursor.getLong(modifiedColumn).takeIf { it > 0 }

                    val path = cursor.getString(dataColumn)
                    val folderName = cursor.getString(bucketNameColumn)
                        ?.takeIf { it.isNotBlank() }
                    // BUCKET_DISPLAY_NAME is null for a handful of provider
                    // rows (some OEM galleries, files on a volume mounted after
                    // the scan). The legacy path still carries the directory,
                    // so an unnamed bucket becomes its folder rather than an
                    // "Unknown" heap that mixes unrelated files together.
                        ?: path?.let { File(it).parentFile?.name }?.takeIf { it.isNotBlank() }
                        ?: UNKNOWN_FOLDER_NAME

                    videos += LocalVideo(
                        id = id,
                        title = title,
                        uri = ContentUris.withAppendedId(collection, id),
                        durationMs = durationMs,
                        sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L),
                        dateAddedMs = addedSeconds?.times(1000L),
                        width = cursor.getInt(widthColumn).coerceAtLeast(0),
                        height = cursor.getInt(heightColumn).coerceAtLeast(0),
                        mimeType = cursor.getString(mimeColumn),
                        bucketId = cursor.getLong(bucketIdColumn).takeIf { !cursor.isNull(bucketIdColumn) },
                        folderName = folderName,
                    )
                }
            }
        } catch (e: SecurityException) {
            // Access can be revoked between the permission check and the query
            // - a partial grant narrowed while the tab was open, or the user
            // changing it from system settings. An empty library reads the same
            // as a device with no videos, which is the honest answer here.
            KLog.w("LocalVideoRepo", "Media access refused while listing videos", e)
        } catch (e: Exception) {
            KLog.e("LocalVideoRepo", "Failed to list device videos", e)
        }
        videos
    }

    companion object {
        const val UNKNOWN_FOLDER_NAME = "Other"

        /**
         * Group a flat list into folder cards, ordered by most recent activity.
         *
         * Folders are keyed by bucket id where MediaStore gave one and by name
         * otherwise, so the fallback rows above group with each other rather
         * than each becoming its own single-item folder.
         */
        fun foldersOf(videos: List<LocalVideo>): List<LocalVideoFolder> =
            videos
                .groupBy { it.bucketId ?: it.folderName.hashCode().toLong() }
                .map { (_, inFolder) ->
                    // Already newest-first from the query, but a caller may
                    // hand this a re-sorted list and the cover must stay the
                    // newest frame either way.
                    val newest = inFolder.maxByOrNull { it.dateAddedMs ?: 0L }
                    LocalVideoFolder(
                        bucketId = inFolder.first().bucketId,
                        name = inFolder.first().folderName,
                        videoCount = inFolder.size,
                        coverUri = newest?.uri,
                        newestDateMs = newest?.dateAddedMs,
                    )
                }
                .sortedWith(
                    compareByDescending<LocalVideoFolder> { it.newestDateMs ?: 0L }
                        .thenBy { it.name.lowercase() }
                )

        /** Apply one of the sheet's sort orders. */
        fun sorted(videos: List<LocalVideo>, sort: LocalVideoSort): List<LocalVideo> = when (sort) {
            LocalVideoSort.RECENT -> videos.sortedByDescending { it.dateAddedMs ?: 0L }
            LocalVideoSort.NAME -> videos.sortedBy { it.title.lowercase() }
            LocalVideoSort.DURATION -> videos.sortedByDescending { it.durationMs }
            LocalVideoSort.SIZE -> videos.sortedByDescending { it.sizeBytes }
        }

        /**
         * The videos in one folder, matched the same way [foldersOf] grouped
         * them so a null bucket id still resolves to its own card's contents.
         */
        fun videosIn(videos: List<LocalVideo>, folder: LocalVideoFolder): List<LocalVideo> =
            videos.filter {
                if (folder.bucketId != null) it.bucketId == folder.bucketId
                else it.bucketId == null && it.folderName == folder.name
            }

        /** "1.4 GB", "812 MB" - the size shown under a device video row. */
        fun formatSize(bytes: Long): String = when {
            bytes <= 0L -> ""
            bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000L -> "${bytes / 1_000_000L} MB"
            else -> "${(bytes / 1_000L).coerceAtLeast(1L)} KB"
        }
    }
}

/** Whether the device's videos are readable, and how completely. */
enum class LocalVideoAccess {
    /** Every video on the device is visible. */
    FULL,

    /**
     * Android 14's partial grant: only the files the user picked are visible,
     * and they can widen the selection without going to system settings.
     */
    PARTIAL,

    DENIED,
}
