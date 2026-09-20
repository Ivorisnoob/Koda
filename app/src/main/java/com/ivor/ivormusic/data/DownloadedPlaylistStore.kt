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

    /**
     * Whether this snapshot describes nothing that is here or on its way.
     *
     * The [pending] half is the one that matters and the one with no compile
     * error behind it: a playlist download is remembered when it is accepted,
     * so between that moment and the first file landing the snapshot has
     * legitimately nothing to show. A prune that only counted files would
     * delete the record of the download currently running.
     */
    fun isOrphaned(downloads: List<Song>, pending: Set<String>): Boolean =
        offlineSongs(downloads).isEmpty() && songs.none { it.id in pending }
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
            write(listOf(playlist) + state.value.filterNot { it.id == playlist.id })
        }
    }

    /**
     * Drop snapshots by id. The store only ever grew, so a playlist whose last
     * track had been deleted stayed on the Downloads tab forever as a card
     * reading "0 of 12 available offline" - an entry offering nothing, which
     * cannot be got rid of because the thing it describes is already gone.
     *
     * Deliberately not a deletion of files: the songs are what hold the bytes,
     * and this is only the record that they were once downloaded together.
     */
    suspend fun forget(ids: Set<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        mutex.withLock {
            val remaining = state.value.filterNot { it.id in ids }
            if (remaining.size != state.value.size) write(remaining)
        }
    }

    /** Callers hold [mutex]; the flow is only updated once the bytes are down. */
    private fun write(playlists: List<DownloadedPlaylist>) {
        val bytes = json.encodeToString(playlists).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
            state.value = playlists
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
