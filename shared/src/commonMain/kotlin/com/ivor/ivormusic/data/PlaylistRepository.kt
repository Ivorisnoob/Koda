package com.ivor.ivormusic.data

import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.UserPlaylist
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent playlist repository interface.
 * Android implementation uses file-based JSON + Bitmap cover art generation.
 */
interface PlaylistRepository {
    val userPlaylists: StateFlow<List<UserPlaylist>>

    suspend fun refreshPlaylists()
    suspend fun createPlaylist(name: String, description: String?): String
    suspend fun addSongToPlaylist(playlistId: String, song: Song)
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
    suspend fun moveSongInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int)
    suspend fun replacePlaylistSongs(playlistId: String, songs: List<Song>)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun updatePlaylist(playlistId: String, name: String, description: String?)
}
