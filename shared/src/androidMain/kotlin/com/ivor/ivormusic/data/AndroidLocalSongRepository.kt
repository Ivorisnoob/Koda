package com.ivor.ivormusic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ivor.ivormusic.domain.FolderInfo
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidLocalSongRepository(private val context: Context) : LocalSongRepository {

    override suspend fun getSongs(excludedFolders: Set<String>, manualScan: Boolean): List<Song> =
        withContext(Dispatchers.IO) {
            if (manualScan) return@withContext getSongsViaManualScan(excludedFolders)
            val songs = mutableListOf<Song>()
            val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA
            )

            context.contentResolver.query(
                collection, projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val filePath = cursor.getString(dataCol) ?: ""
                    val parentFolder = File(filePath).parent ?: ""
                    if (excludedFolders.any { parentFolder == it || parentFolder.startsWith("$it/") }) continue
                    val albumId = cursor.getLong(albumIdCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
                    songs.add(Song.fromLocal(id, cursor.getString(titleCol), cursor.getString(artistCol), cursor.getString(albumCol), cursor.getLong(durCol), contentUri.toString(), albumArtUri.toString(), filePath))
                }
            }
            songs
        }

    override suspend fun getAvailableFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<String, Int>()
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        context.contentResolver.query(
            collection, arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val parent = File(cursor.getString(dataCol) ?: continue).parent ?: continue
                folders[parent] = (folders[parent] ?: 0) + 1
            }
        }
        folders.map { (path, count) -> FolderInfo(path, File(path).name, count) }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun getSongsViaManualScan(excludedFolders: Set<String>): List<Song> {
        val songs = mutableListOf<Song>()
        val roots = listOfNotNull(System.getenv("EXTERNAL_STORAGE"), "/storage/emulated/0", "/sdcard").distinct()
        val exts = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")
        roots.forEach { root ->
            val f = File(root)
            if (f.exists() && f.isDirectory) scanDir(f, songs, excludedFolders, exts)
        }
        return songs.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    private fun scanDir(dir: File, results: MutableList<Song>, excluded: Set<String>, exts: Set<String>) {
        if (dir.name.startsWith(".") || excluded.contains(dir.absolutePath)) return
        val name = dir.name.lowercase()
        if (name == "android" || name == "data" || name == "obb") return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) scanDir(file, results, excluded, exts)
            else if (file.extension.lowercase() in exts) {
                results.add(Song(
                    id = file.absolutePath.hashCode().toString(),
                    title = file.nameWithoutExtension,
                    artist = "Unknown Artist",
                    album = dir.name,
                    duration = 0L,
                    uri = file.absolutePath,
                    source = SongSource.LOCAL,
                    filePath = file.absolutePath
                ))
            }
        }
    }
}
