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
 *
 * **Scoped per profile** (September 2026), with the listening and search
 * history: likes are taste - they weight the signed-out recommendations and
 * fill Liked Songs - and one profile's are not another's. The profile active
 * when this shipped kept the existing ids and file (see
 * [ProfileManager.historyOwnerProfileId]). Because the state is process-wide,
 * a switch has to push the new profile's likes through it
 * ([reloadForActiveProfile]), and every write first checks that what is in
 * memory belongs to the active profile: writing a stale set would put one
 * profile's likes into another's store.
 */
class LikedSongsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val TAG = "LikedSongsRepository"
        internal const val PREFS_NAME = "ivor_music_liked_songs"
        internal const val KEY_LIKED_SONGS = "liked_song_ids"
        internal const val SONGS_FILE_BASE = "liked_songs_meta"

        private val json = Json { ignoreUnknownKeys = true }

        // Process-wide state (see class doc).
        private val _likedSongIds = MutableStateFlow<Set<String>>(emptySet())
        private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
        private val LOCK = Any()

        /** The profile whose likes are in the flows, or null before the first load. */
        @Volatile
        private var loadedFor: String? = null

        /** [profileId]'s liked-id key, for backups and a new profile's copy. */
        internal fun idsKeyFor(context: Context, profileId: String): String =
            ProfileManager.historyScopedKey(KEY_LIKED_SONGS, profileId, context)

        /** [profileId]'s metadata file, for backups and a new profile's copy. */
        internal fun songsFileFor(context: Context, profileId: String): File = File(
            context.applicationContext.filesDir,
            ProfileManager.historyScopedFileName(SONGS_FILE_BASE, "json", profileId, context)
        )

        /** Give [toProfileId] a copy of [fromProfileId]'s likes, if it has none. */
        internal fun copyProfileData(context: Context, fromProfileId: String, toProfileId: String) {
            ProfileManager.copyScopedPreferences(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                listOf(KEY_LIKED_SONGS), fromProfileId, toProfileId
            ) { _, profileId -> idsKeyFor(context, profileId) }
            ProfileManager.copyScopedFile(
                songsFileFor(context, fromProfileId),
                songsFileFor(context, toProfileId)
            )
        }

        /**
         * Re-seed the shared flows from the newly active profile's store.
         * Resolves the profile from the stored id, so it is safe to call before
         * [ProfileManager.activeProfileId] has emitted.
         */
        fun reloadForActiveProfile(context: Context) {
            val repo = LikedSongsRepository(context)
            synchronized(LOCK) { repo.loadLocked(ProfileManager.requireActiveProfileId(repo.appContext)) }
        }
    }

    /** IDs of all liked songs (local and YouTube). */
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()

    /** Full metadata for liked songs, newest like first. */
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    init {
        if (loadedFor == null) {
            synchronized(LOCK) {
                if (loadedFor == null) loadLocked(ProfileManager.requireActiveProfileId(appContext))
            }
        }
    }

    /** Caller holds [LOCK]. */
    private fun loadLocked(profileId: String) {
        _likedSongIds.value = prefs.getStringSet(idsKeyFor(appContext, profileId), emptySet()) ?: emptySet()
        _likedSongs.value = loadSongMetadata(songsFileFor(appContext, profileId))
        loadedFor = profileId
    }

    /**
     * The profile every write lands on, with the flows reloaded first if they
     * still hold another profile's likes. The switch reloads them already;
     * this is the backstop for a path that changes the active profile without
     * going through [AccountSwitcher], so such a path can show stale likes
     * for a moment but can never write them into the wrong profile.
     */
    private fun writeTarget(): String {
        val active = ProfileManager.requireActiveProfileId(appContext)
        if (loadedFor != active) synchronized(LOCK) { if (loadedFor != active) loadLocked(active) }
        return active
    }

    private fun saveLikedIds(profileId: String, songIds: Set<String>) {
        prefs.edit().putStringSet(idsKeyFor(appContext, profileId), songIds).apply()
        _likedSongIds.value = songIds
    }

    private fun loadSongMetadata(songsFile: File): List<Song> {
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

    private fun saveSongMetadata(profileId: String, songs: List<Song>) {
        _likedSongs.value = songs
        try {
            songsFileFor(appContext, profileId).writeText(json.encodeToString(songs))
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
            saveSongMetadata(writeTarget(), listOf(song.likedNow()) + _likedSongs.value.filter { it.id != song.id })
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
        val profileId = writeTarget()
        val currentLiked = _likedSongIds.value.toMutableSet()
        val isNowLiked = if (currentLiked.contains(songId)) {
            currentLiked.remove(songId)
            false
        } else {
            currentLiked.add(songId)
            true
        }
        saveLikedIds(profileId, currentLiked)
        if (!isNowLiked && _likedSongs.value.any { it.id == songId }) {
            saveSongMetadata(profileId, _likedSongs.value.filter { it.id != songId })
        }
        return isNowLiked
    }

    /**
     * Add a song to liked.
     */
    fun likeSong(song: Song) {
        val profileId = writeTarget()
        saveLikedIds(profileId, _likedSongIds.value + song.id)
        saveSongMetadata(profileId, listOf(song.likedNow()) + _likedSongs.value.filter { it.id != song.id })
    }

    /**
     * Remove a song from liked.
     */
    fun unlikeSong(songId: String) {
        val profileId = writeTarget()
        saveLikedIds(profileId, _likedSongIds.value - songId)
        if (_likedSongs.value.any { it.id == songId }) {
            saveSongMetadata(profileId, _likedSongs.value.filter { it.id != songId })
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
