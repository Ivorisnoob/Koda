package com.ivor.ivormusic.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SongRepository(private val context: Context) {

    /**
     * Get all songs from the device, optionally filtering out excluded folders.
     * @param excludedFolders Set of folder paths to exclude from results
     * @param manualScan If true, bypass MediaStore and scan filesystem directly (OEM fix)
     */
    suspend fun getSongs(
        excludedFolders: Set<String> = emptySet(),
        manualScan: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        if (manualScan) {
            return@withContext getSongsViaManualScan(excludedFolders)
        }
        val songs = mutableListOf<Song>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA, // File path for folder filtering
            MediaStore.Audio.Media.DATE_ADDED, // Seconds since epoch, for "Recently added"
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.CD_TRACK_NUMBER,
            MediaStore.Audio.Media.DISC_NUMBER
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val cdTrackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER)
            val discColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val filePath = cursor.getString(dataColumn) ?: ""
                // DATE_ADDED is seconds, not millis. 0 means the provider had
                // nothing, which is "unknown" rather than 1970.
                val dateAdded = cursor.getLong(dateAddedColumn).takeIf { it > 0 }?.times(1000L)
                val position = albumPosition(
                    encodedTrack = cursor.getInt(trackColumn).takeIf { !cursor.isNull(trackColumn) },
                    cdTrackNumber = cursor.getString(cdTrackColumn),
                    discNumber = cursor.getString(discColumn)
                )

                // Check if this song's folder is excluded
                val parentFolder = File(filePath).parent ?: ""
                if (excludedFolders.any { excluded -> 
                    parentFolder == excluded || parentFolder.startsWith("$excluded/") 
                }) {
                    continue
                }

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                songs.add(
                    Song.fromLocal(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        albumArtUri = albumArtUri,
                        filePath = filePath,
                        dateAdded = dateAdded,
                        trackNumber = position.track,
                        discNumber = position.disc
                    )
                )
            }
        }
        songs
    }
    
    /**
     * Get all unique folders that contain music files.
     * Used for the folder exclusion UI.
     */
    suspend fun getAvailableFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, Int>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataColumn) ?: continue
                val parentFolder = File(filePath).parent ?: continue
                folders[parentFolder] = (folders[parentFolder] ?: 0) + 1
            }
        }
        
        folders.map { (path, count) -> 
            FolderInfo(
                path = path,
                displayName = File(path).name,
                songCount = count
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Manual filesystem scan to bypass MediaStore restrictions on HyperOS/OEMs.
     */
    private fun getSongsViaManualScan(excludedFolders: Set<String>): List<Song> {
        val songs = mutableListOf<Song>()
        val rootPaths = listOfNotNull(
            System.getenv("EXTERNAL_STORAGE"),
            "/storage/emulated/0",
            "/sdcard"
        ).distinct()

        val audioExtensions = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")

        rootPaths.forEach { root ->
            val rootFile = File(root)
            if (rootFile.exists() && rootFile.isDirectory) {
                scanDir(rootFile, songs, excludedFolders, audioExtensions)
            }
        }
        return songs.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    private fun scanDir(
        dir: File,
        results: MutableList<Song>,
        excludedFolders: Set<String>,
        extensions: Set<String>
    ) {
        // Skip hidden and excluded folders
        if (dir.name.startsWith(".") || excludedFolders.contains(dir.absolutePath)) return
        
        // Skip common Android system/data dirs
        val name = dir.name.lowercase()
        if (name == "android" || name == "data" || name == "obb") return

        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                scanDir(file, results, excludedFolders, extensions)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in extensions) {
                    val metadata = readManualMetadata(file)
                    val song = Song(
                        id = file.absolutePath.hashCode().toString(),
                        title = metadata?.title ?: file.nameWithoutExtension,
                        artist = metadata?.artist ?: "Unknown Artist",
                        album = metadata?.album ?: dir.name,
                        duration = metadata?.durationMs ?: 0L,
                        uri = Uri.fromFile(file),
                        source = SongSource.LOCAL,
                        filePath = file.absolutePath,
                        // No MediaStore here, so mtime is the best proxy available
                        dateAdded = file.lastModified().takeIf { it > 0 },
                        trackNumber = metadata?.position?.track,
                        discNumber = metadata?.position?.disc
                    )
                    results.add(song)
                }
            }
        }
    }

    private data class ManualAudioMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val position: AlbumPosition
    )

    private fun readManualMetadata(file: File): ManualAudioMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            ManualAudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() },
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 },
                position = albumPosition(
                    encodedTrack = null,
                    cdTrackNumber = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER
                    ),
                    discNumber = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER
                    )
                )
            )
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }
}

/**
 * Information about a folder containing music files.
 */
data class FolderInfo(
    val path: String,
    val displayName: String,
    val songCount: Int
)
