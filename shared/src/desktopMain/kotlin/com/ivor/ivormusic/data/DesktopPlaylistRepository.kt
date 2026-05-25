package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DesktopPlaylistRepository : PlaylistRepository {

    private val configDir = File(System.getProperty("user.home"), ".config/koda").also { it.mkdirs() }
    private val playlistsFile = File(configDir, "playlists.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    override val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        if (!playlistsFile.exists()) return
        try {
            _userPlaylists.value = json.decodeFromString(playlistsFile.readText())
        } catch (_: Exception) {}
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        playlistsFile.writeText(json.encodeToString(_userPlaylists.value))
    }

    override suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        loadFromDisk()
    }

    override suspend fun createPlaylist(name: String, description: String?): String {
        val id = UUID.randomUUID().toString()
        mutex.withLock {
            val playlist = UserPlaylist(
                id = id,
                name = name,
                description = description,
                songs = emptyList(),
                createdAt = System.currentTimeMillis()
            )
            _userPlaylists.value = _userPlaylists.value + playlist
            saveToDisk()
        }
        return id
    }

    override suspend fun addSongToPlaylist(playlistId: String, song: Song) {
        mutex.withLock {
            _userPlaylists.value = _userPlaylists.value.map { pl ->
                if (pl.id == playlistId && pl.songs.none { it.id == song.id })
                    pl.copy(songs = pl.songs + song)
                else pl
            }
            saveToDisk()
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: String, songId: String) {
        mutex.withLock {
            _userPlaylists.value = _userPlaylists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(songs = pl.songs.filter { it.id != songId })
                else pl
            }
            saveToDisk()
        }
    }

    override suspend fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        mutex.withLock {
            _userPlaylists.value = _userPlaylists.value.map { pl ->
                if (pl.id == playlistId) {
                    val songs = pl.songs.toMutableList()
                    if (fromIndex in songs.indices && toIndex in songs.indices) {
                        val song = songs.removeAt(fromIndex)
                        songs.add(toIndex, song)
                    }
                    pl.copy(songs = songs)
                } else pl
            }
            saveToDisk()
        }
    }

    override suspend fun replacePlaylistSongs(playlistId: String, songs: List<Song>) {
        mutex.withLock {
            _userPlaylists.value = _userPlaylists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(songs = songs) else pl
            }
            saveToDisk()
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        mutex.withLock {
            _userPlaylists.value = _userPlaylists.value.filter { it.id != playlistId }
            saveToDisk()
        }
    }

    override suspend fun updatePlaylist(playlistId: String, name: String, description: String?) {
        mutex.withLock {
            _userPlaylists.value = _userPlaylists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(name = name, description = description) else pl
            }
            saveToDisk()
        }
    }
}
