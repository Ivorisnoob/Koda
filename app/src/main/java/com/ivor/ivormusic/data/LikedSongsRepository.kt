package com.ivor.ivormusic.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages liked songs persistence using SharedPreferences.
 * Stores song IDs as a Set of strings (supports both local and YouTube songs).
 */
class LikedSongsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // In-memory set of liked song IDs for quick lookup
    private val _likedSongIds = MutableStateFlow<Set<String>>(loadLikedSongs())
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    companion object {
        private const val PREFS_NAME = "ivor_music_liked_songs"
        private const val KEY_LIKED_SONGS = "liked_song_ids"
    }

    /**
     * Load liked songs from SharedPreferences.
     */
    private fun loadLikedSongs(): Set<String> {
        return prefs.getStringSet(KEY_LIKED_SONGS, emptySet()) ?: emptySet()
    }

    /**
     * Save liked songs to SharedPreferences.
     */
    private fun saveLikedSongs(songIds: Set<String>) {
        prefs.edit().putStringSet(KEY_LIKED_SONGS, songIds).apply()
        _likedSongIds.value = songIds
    }

    /**
     * Check if a song is liked.
     */
    fun isLiked(songId: String): Boolean {
        return _likedSongIds.value.contains(songId)
    }

    /**
     * Toggle the liked status of a song.
     * @return true if the song is now liked, false if unliked
     */
    fun toggleLike(songId: String): Boolean {
        val currentLiked = _likedSongIds.value.toMutableSet()
        val isNowLiked = if (currentLiked.contains(songId)) {
            currentLiked.remove(songId)
            false
        } else {
            currentLiked.add(songId)
            true
        }
        saveLikedSongs(currentLiked)
        return isNowLiked
    }

    /**
     * Add a song to liked.
     */
    fun likeSong(songId: String) {
        val currentLiked = _likedSongIds.value.toMutableSet()
        currentLiked.add(songId)
        saveLikedSongs(currentLiked)
    }

    /**
     * Remove a song from liked.
     */
    fun unlikeSong(songId: String) {
        val currentLiked = _likedSongIds.value.toMutableSet()
        currentLiked.remove(songId)
        saveLikedSongs(currentLiked)
    }

    /**
     * Get all liked song IDs.
     */
    fun getAllLikedSongIds(): Set<String> {
        return _likedSongIds.value
    }

    /**
     * Get the count of liked songs.
     */
    fun getLikedCount(): Int {
        return _likedSongIds.value.size
    }
}
