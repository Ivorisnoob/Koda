package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.FolderInfo
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DesktopLocalSongRepository : LocalSongRepository {

    private val musicExtensions = setOf("mp3", "flac", "ogg", "aac", "m4a", "wav", "opus", "wma")

    private val defaultMusicDirs: List<File> get() {
        val home = File(System.getProperty("user.home"))
        return listOfNotNull(
            File(home, "Music").takeIf { it.exists() },
            File(home, "music").takeIf { it.exists() },
            File("/music").takeIf { it.exists() }
        )
    }

    override suspend fun getSongs(excludedFolders: Set<String>, manualScan: Boolean): List<Song> =
        withContext(Dispatchers.IO) {
            val songs = mutableListOf<Song>()
            defaultMusicDirs.forEach { dir ->
                scanDirectory(dir, excludedFolders, songs)
            }
            songs
        }

    private fun scanDirectory(dir: File, excludedFolders: Set<String>, songs: MutableList<Song>) {
        if (!dir.canRead()) return
        if (dir.absolutePath in excludedFolders) return
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> scanDirectory(file, excludedFolders, songs)
                file.isFile && file.extension.lowercase() in musicExtensions -> {
                    songs.add(fileToSong(file))
                }
            }
        }
    }

    private fun fileToSong(file: File): Song {
        val name = file.nameWithoutExtension
        val parts = name.split(" - ", limit = 2)
        val artist = if (parts.size == 2) parts[0].trim() else "Unknown Artist"
        val title = if (parts.size == 2) parts[1].trim() else name
        return Song(
            id = file.absolutePath,
            title = title,
            artist = artist,
            album = file.parentFile?.name ?: "",
            duration = 0L,
            source = SongSource.LOCAL,
            uri = file.toURI().toString()
        )
    }

    override suspend fun getAvailableFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        defaultMusicDirs.flatMap { dir ->
            dir.listFiles()?.filter { it.isDirectory }?.map { sub ->
                FolderInfo(
                    path = sub.absolutePath,
                    displayName = sub.name,
                    songCount = sub.listFiles()
                        ?.count { it.extension.lowercase() in musicExtensions } ?: 0
                )
            } ?: emptyList()
        }
    }
}
