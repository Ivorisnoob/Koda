package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages liked songs persistence.
 *
 * - Song IDs live in SharedPreferences (fast lookup, backwards compatible).
 * - Full [Song] metadata lives in a JSON file, so liked YouTube songs can be
 *   shown in the Library even without a YouTube login.
 * - State is held in companion-level flows shared across instances: several
 *   ViewModels construct their own repository, and a like toggled in the
 *   player must be visible to the Library immediately.
 */
class LikedSongsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    private val songsFile = File(appContext.filesDir, "liked_songs_meta.json")

    companion object {
        private const val TAG = "LikedSongsRepository"
        private const val PREFS_NAME = "ivor_music_liked_songs"
        private const val KEY_LIKED_SONGS = "liked_song_ids"

        private val json = Json { ignoreUnknownKeys = true }

        // Process-wide state (see class doc).
        private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
        private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
        @Volatile
        private var loaded = false
    }

    /** IDs of all liked songs (local and YouTube). */
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    /** Full metadata for liked songs, newest like first. */
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    init {
        if (!loaded) {
            synchronized(LikedSongsRepository::class.java) {
                if (!loaded) {
                    _likedSongIds.value = prefs.getStringSet(KEY_LIKED_SONGS, emptySet()) ?: emptySet()
                    _likedSongs.value = loadSongMetadata()
                    loaded = true
                }
            }
        }
    }

    private fun saveLikedIds(songIds: Set<String>) {
        prefs.edit().putStringSet(KEY_LIKED_SONGS, songIds).apply()
        _likedSongIds.value = songIds
    }

    private fun loadSongMetadata(): List<Song> {
        if (!songsFile.exists()) return emptyList()
        return try {
            val stored = json.decodeFromString<List<Song>>(songsFile.readText())
            // Likes saved before songs carried a timestamp have none. The list
            // is newest-first, so counting backwards from the file's mtime
            // preserves the order the user actually liked them in. Written back
            // immediately so this only ever runs once.
            if (stored.any { it.dateAdded == null }) {
                val anchor = songsFile.lastModified().takeIf { it > 0 }
                    ?: System.currentTimeMillis()
                val backfilled = stored.mapIndexed { index, song ->
                    song.dateAdded?.let { song } ?: song.copy(dateAdded = anchor - index * 1000L)
                }
                try {
                    songsFile.writeText(json.encodeToString(backfilled))
                } catch (e: Exception) {
                    KLog.e(TAG, "Error persisting backfilled like timestamps", e)
                }
                backfilled
            } else stored
        } catch (e: Exception) {
            KLog.e(TAG, "Error loading liked song metadata", e)
            emptyList()
        }
    }

    private fun saveSongMetadata(songs: List<Song>) {
        _likedSongs.value = songs
        try {
            songsFile.writeText(json.encodeToString(songs))
        } catch (e: Exception) {
            KLog.e(TAG, "Error saving liked song metadata", e)
        }
    }

    /**
     * Check if a song is liked.
     */
    fun isLiked(songId: String): Boolean {
        return _likedSongIds.value.contains(songId)
    }

    /**
     * Toggle the liked status of a song, storing its full metadata on like.
     * Prefer this over the ID-only overload whenever the [Song] is available.
     * @return true if the song is now liked, false if unliked
     */
    fun toggleLike(song: Song): Boolean {
        val isNowLiked = toggleLike(song.id)
        if (isNowLiked) {
            saveSongMetadata(listOf(song.likedNow()) + _likedSongs.value.filter { it.id != song.id })
        }
        return isNowLiked
    }

    /**
     * In the library, a liked song's "date added" is when it was liked — not
     * when its file landed on the device, which is what a local [Song] carries.
     */
    private fun Song.likedNow(): Song = copy(dateAdded = System.currentTimeMillis())

    /**
     * Toggle the liked status of a song by ID only (no metadata stored).
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
        saveLikedIds(currentLiked)
        if (!isNowLiked && _likedSongs.value.any { it.id == songId }) {
            saveSongMetadata(_likedSongs.value.filter { it.id != songId })
        }
        return isNowLiked
    }

    /**
     * Add a song to liked.
     */
    fun likeSong(song: Song) {
        saveLikedIds(_likedSongIds.value + song.id)
        saveSongMetadata(listOf(song.likedNow()) + _likedSongs.value.filter { it.id != song.id })
    }

    /**
     * Remove a song from liked.
     */
    fun unlikeSong(songId: String) {
        saveLikedIds(_likedSongIds.value - songId)
        if (_likedSongs.value.any { it.id == songId }) {
            saveSongMetadata(_likedSongs.value.filter { it.id != songId })
        }
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
