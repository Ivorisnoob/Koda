package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Snapshot of the music playback session: the queue, which song was playing,
 * and how far into it the user was.
 */
@Serializable
data class PlaybackSession(
    val queue: List<MusicQueueItem> = emptyList(),
    // Kept only so sessions written by older app versions still restore. New
    // snapshots omit this default-valued field and persist queue-entry IDs.
    @SerialName("songs")
    val legacySongs: List<Song> = emptyList(),
    val currentIndex: Int,
    val positionMs: Long,
    val savedAt: Long
)

internal val PlaybackSession.items: List<MusicQueueItem>
    get() = queue.ifEmpty { legacySongs.map { MusicQueueItem(song = it) } }

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

    fun save(queue: List<MusicQueueItem>, currentIndex: Int, positionMs: Long) {
        if (queue.isEmpty() || currentIndex !in queue.indices) return
        try {
            // Keep a window around the current song when trimming, so both
            // history (previous button) and upcoming songs survive.
            val start = (currentIndex - MAX_SAVED_SONGS / 2).coerceAtLeast(0)
            val end = (start + MAX_SAVED_SONGS).coerceAtMost(queue.size)
            val trimmed = queue.subList(start, end)
            val session = PlaybackSession(
                queue = trimmed,
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
            KLog.e(TAG, "Failed to save playback session", e)
        }
    }

    fun load(): PlaybackSession? {
        return try {
            if (!sessionFile.exists()) return null
            val decoded = json.decodeFromString<PlaybackSession>(sessionFile.readText())
            val items = decoded.items
            if (items.isEmpty() || decoded.currentIndex !in items.indices) null
            else decoded.copy(queue = items, legacySongs = emptyList())
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to load playback session", e)
            null
        }
    }

    fun clear() {
        try {
            sessionFile.delete()
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to clear playback session", e)
        }
    }
}
