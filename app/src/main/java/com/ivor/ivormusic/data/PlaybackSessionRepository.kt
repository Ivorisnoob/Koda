package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Snapshot of the music playback session: the queue, which song was playing,
 * and how far into it the user was.
 */
@Serializable
data class PlaybackSession(
    val songs: List<Song>,
    val currentIndex: Int,
    val positionMs: Long,
    val savedAt: Long
)

/**
 * Persists the last playback session to a JSON file so reopening the app can
 * restore the full queue and position instead of just the last song's
 * metadata. File-based like PlaylistRepository; writes happen on the caller's
 * dispatcher (call from Dispatchers.IO).
 */
class PlaybackSessionRepository(context: Context) {

    companion object {
        private const val TAG = "PlaybackSession"
        private const val FILE_NAME = "playback_session.json"

        // Auto-queue can grow the queue without bound over a long listening
        // session; cap what we persist so the file stays small and restore
        // stays instant.
        private const val MAX_SAVED_SONGS = 200
    }

    private val sessionFile = File(context.filesDir, FILE_NAME)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun save(songs: List<Song>, currentIndex: Int, positionMs: Long) {
        if (songs.isEmpty() || currentIndex !in songs.indices) return
        try {
            // Keep a window around the current song when trimming, so both
            // history (previous button) and upcoming songs survive.
            val start = (currentIndex - MAX_SAVED_SONGS / 2).coerceAtLeast(0)
            val end = (start + MAX_SAVED_SONGS).coerceAtMost(songs.size)
            val trimmed = songs.subList(start, end)
            val session = PlaybackSession(
                songs = trimmed,
                currentIndex = currentIndex - start,
                positionMs = positionMs.coerceAtLeast(0L),
                savedAt = System.currentTimeMillis()
            )
            val tmp = File(sessionFile.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(session))
            if (!tmp.renameTo(sessionFile)) {
                sessionFile.writeText(json.encodeToString(session))
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playback session", e)
        }
    }

    fun load(): PlaybackSession? {
        return try {
            if (!sessionFile.exists()) return null
            val session = json.decodeFromString<PlaybackSession>(sessionFile.readText())
            if (session.songs.isEmpty() || session.currentIndex !in session.songs.indices) null
            else session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load playback session", e)
            null
        }
    }

    fun clear() {
        try {
            sessionFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear playback session", e)
        }
    }
}
