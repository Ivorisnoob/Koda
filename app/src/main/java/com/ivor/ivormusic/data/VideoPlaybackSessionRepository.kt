package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Minimal, self-contained metadata for one queue entry in a persisted video
 * session.
 *
 * Deliberately not [VideoItem]: that carries `descriptionLinks`, a sealed
 * interface that would need its own polymorphic serialization for a field the
 * restored mini player never shows. Everything here is re-supplied within
 * moments of restore by the watch-next fetch [VideoItem] is normally enriched
 * from - the same "nameless start, filled in by watch-next" path a shared link
 * already uses.
 */
@Serializable
data class PersistedVideoSnapshot(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String? = null,
    val channelIconUrl: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long = 0L,
    val viewCount: String = ""
)

/**
 * Snapshot of the video playback session: the queue (or just the one video
 * being watched on its own), which entry was playing, and how far into it the
 * user was.
 */
@Serializable
data class VideoPlaybackSession(
    val videos: List<PersistedVideoSnapshot>,
    val currentIndex: Int,
    /** Null for a video watched on its own, outside any playlist. */
    val queueTitle: String? = null,
    val queuePlaylistId: String? = null,
    val positionMs: Long,
    val savedAt: Long
)

/**
 * Persists the last video playback session to a JSON file so reopening the
 * app can restore the video (or playlist) and position instead of losing the
 * user's place - the same pattern [PlaybackSessionRepository] already uses for
 * music. File-based like that repository; writes happen on the caller's
 * dispatcher (call from Dispatchers.IO).
 */
class VideoPlaybackSessionRepository(context: Context) {

    companion object {
        private const val TAG = "VideoPlaybackSession"
        private const val FILE_NAME = "video_playback_session.json"

        // A playlist queue can run to hundreds of entries; cap what is
        // persisted so the file stays small and restore stays instant, the
        // same window PlaybackSessionRepository keeps around the current song.
        private const val MAX_SAVED_VIDEOS = 100
    }

    private val sessionFile = File(context.filesDir, FILE_NAME)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun save(
        videos: List<PersistedVideoSnapshot>,
        currentIndex: Int,
        queueTitle: String?,
        queuePlaylistId: String?,
        positionMs: Long
    ) {
        if (videos.isEmpty() || currentIndex !in videos.indices) return
        try {
            // Keep a window around the current video when trimming, so both
            // history and upcoming entries survive.
            val start = (currentIndex - MAX_SAVED_VIDEOS / 2).coerceAtLeast(0)
            val end = (start + MAX_SAVED_VIDEOS).coerceAtMost(videos.size)
            val trimmed = videos.subList(start, end)
            val session = VideoPlaybackSession(
                videos = trimmed,
                currentIndex = currentIndex - start,
                queueTitle = queueTitle,
                queuePlaylistId = queuePlaylistId,
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
            KLog.e(TAG, "Failed to save video playback session", e)
        }
    }

    fun load(): VideoPlaybackSession? {
        return try {
            if (!sessionFile.exists()) return null
            val session = json.decodeFromString<VideoPlaybackSession>(sessionFile.readText())
            if (session.videos.isEmpty() || session.currentIndex !in session.videos.indices) null
            else session
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to load video playback session", e)
            null
        }
    }

    fun clear() {
        try {
            sessionFile.delete()
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to clear video playback session", e)
        }
    }
}
