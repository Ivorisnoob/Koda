package com.ivor.ivormusic.data

import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.FlowSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LikedSongsRepository(private val settings: Settings) {

    private val _likedSongIds = MutableStateFlow<Set<String>>(loadLikedSongs())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    private fun loadLikedSongs(): Set<String> {
        val raw = settings.getStringOrNull(KEY_LIKED_SONGS) ?: return emptySet()
        return if (raw.isBlank()) emptySet() else raw.split("|").toSet()
    }

    private fun saveLikedSongs(ids: Set<String>) {
        settings.putString(KEY_LIKED_SONGS, ids.joinToString("|"))
        _likedSongIds.value = ids
    }

    fun isLiked(songId: String): Boolean = _likedSongIds.value.contains(songId)

    fun toggleLike(songId: String): Boolean {
        val current = _likedSongIds.value.toMutableSet()
        return if (current.contains(songId)) {
            current.remove(songId)
            saveLikedSongs(current)
            false
        } else {
            current.add(songId)
            saveLikedSongs(current)
            true
        }
    }

    fun likeSong(songId: String) {
        val current = _likedSongIds.value.toMutableSet()
        current.add(songId)
        saveLikedSongs(current)
    }

    fun unlikeSong(songId: String) {
        val current = _likedSongIds.value.toMutableSet()
        current.remove(songId)
        saveLikedSongs(current)
    }

    fun getLikedCount(): Int = _likedSongIds.value.size

    companion object {
        private const val KEY_LIKED_SONGS = "liked_song_ids"
    }
}
