package com.ivor.ivormusic.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Download intent, separate from the live references kept by Save. */
@Serializable
data class DownloadedPlaylist(
    val id: String,
    val title: String,
    val artworkUrl: String? = null,
    val songs: List<Song>
) {
    /** Preserve occurrences and order, but only return files we actually have. */
    fun offlineSongs(downloads: List<Song>): List<Song> {
        val files = downloads.associateBy { it.id }
        return songs.mapNotNull { song ->
            files[song.id] ?: song.takeIf { it.source == SongSource.LOCAL && it.uri != null }
        }
    }
}

/** Owned by the singleton download repository; no account or network is needed. */
class DownloadedPlaylistStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "downloaded_playlists.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(read())
    val playlists = state.asStateFlow()

    private fun read(): List<DownloadedPlaylist> = try {
        file.openRead().bufferedReader().use { json.decodeFromString(it.readText()) }
    } catch (_: java.io.FileNotFoundException) {
        emptyList()
    } catch (error: Exception) {
        com.ivor.ivormusic.util.KLog.e("DownloadedPlaylists", "Cannot read playlist snapshots", error)
        emptyList()
    }

    suspend fun remember(playlist: DownloadedPlaylist) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = listOf(playlist) + state.value.filterNot { it.id == playlist.id }
            val bytes = json.encodeToString(updated).toByteArray(Charsets.UTF_8)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
                state.value = updated
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        }
    }
}
