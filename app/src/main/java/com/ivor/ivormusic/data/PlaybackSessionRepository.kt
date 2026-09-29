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
    val savedAt: Long,
    /** The shuffle permutation (queue indices in playing order), or empty when unshuffled. */
    val playOrder: List<Int> = emptyList(),
)

/**
 * Where playback was inside the saved queue, written far more often than the
 * queue itself. [queueItemId] finds the row again even if the index moved.
 */
@Serializable
internal data class PlaybackPosition(
    val queueItemId: String? = null,
    val currentIndex: Int,
    val positionMs: Long,
    val savedAt: Long,
)

internal val PlaybackSession.items: List<MusicQueueItem>
    get() = queue.ifEmpty { legacySongs.map { MusicQueueItem(song = it) } }

/**
 * Persists the last playback session so reopening the app, or a platform
 * resume, restores the whole queue and position instead of just the last
 * song.
 *
 * **Two files, because the two halves change at very different rates.** The
 * queue changes when someone plays, adds, moves or removes something; the
 * position changes every second. [saveQueue] writes the queue (with the
 * position at that moment) and [savePosition] writes only the small position
 * file, which [load] lays over the queue when it is newer. That is what lets
 * the queue keep up to [MAX_SAVED_SONGS] songs: when every checkpoint rewrote
 * the whole queue the cap had to be 200, and a restored Liked Songs or a large
 * playlist came back as the 200 songs around the one playing.
 *
 * Written by `MusicService`, which owns playback; the app's screen only reads
 * and clears. Methods block, so call them off the main thread, and they are
 * synchronized so a final write from the service's teardown cannot interleave
 * with its background writer.
 */
class PlaybackSessionRepository internal constructor(private val directory: File) {

    constructor(context: Context) : this(context.filesDir)

    companion object {
        private const val TAG = "PlaybackSession"
        private const val FILE_NAME = "playback_session.json"
        private const val POSITION_FILE_NAME = "playback_position.json"

        /**
         * Auto-queue can grow a queue for hours, so this stays bounded. Large
         * enough for any real playlist or library, and written only when the
         * queue changes, so its size is paid once per edit rather than per tick.
         */
        const val MAX_SAVED_SONGS = 5_000

        /**
         * Where the saved window starts for a queue of [size] playing
         * [currentIndex]. A queue within the cap is kept whole; a longer one
         * keeps the cap's worth centred on the current song, clamped so it is
         * always full.
         */
        fun savedWindowStart(size: Int, currentIndex: Int): Int =
            if (size <= MAX_SAVED_SONGS) 0
            else (currentIndex - MAX_SAVED_SONGS / 2).coerceIn(0, size - MAX_SAVED_SONGS)
    }

    private val sessionFile get() = File(directory, FILE_NAME)
    private val positionFile get() = File(directory, POSITION_FILE_NAME)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Save the whole queue. Trims to a [MAX_SAVED_SONGS] window around the
     * current song, so both history (previous) and what is coming survive.
     */
    @Synchronized
    fun saveQueue(queue: List<MusicQueueItem>, currentIndex: Int, positionMs: Long, playOrder: IntArray? = null) {
        if (queue.isEmpty() || currentIndex !in queue.indices) return
        try {
            val start = savedWindowStart(queue.size, currentIndex)
            val end = (start + MAX_SAVED_SONGS).coerceAtMost(queue.size)
            val now = System.currentTimeMillis()
            val session = PlaybackSession(
                queue = queue.subList(start, end),
                currentIndex = currentIndex - start,
                positionMs = positionMs.coerceAtLeast(0L),
                savedAt = now,
                playOrder = playOrder
                    ?.filter { it in start until end }
                    ?.map { it - start }
                    .orEmpty(),
            )
            writeAtomically(sessionFile, json.encodeToString(session))
            // The queue file now carries the latest position itself; a
            // position file left from the previous queue must not override it.
            positionFile.delete()
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to save playback session", e)
        }
    }

    /** Record where playback is inside the queue [saveQueue] last wrote. */
    @Synchronized
    fun savePosition(queueItemId: String?, currentIndex: Int, positionMs: Long) {
        if (!sessionFile.exists()) return
        try {
            val position = PlaybackPosition(
                queueItemId = queueItemId,
                currentIndex = currentIndex,
                positionMs = positionMs.coerceAtLeast(0L),
                savedAt = System.currentTimeMillis(),
            )
            writeAtomically(positionFile, json.encodeToString(position))
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to save playback position", e)
        }
    }

    @Synchronized
    fun load(): PlaybackSession? {
        return try {
            if (!sessionFile.exists()) return null
            val decoded = json.decodeFromString<PlaybackSession>(sessionFile.readText())
            val items = decoded.items
            if (items.isEmpty() || decoded.currentIndex !in items.indices) return null
            val session = decoded.copy(
                queue = items,
                legacySongs = emptyList(),
                playOrder = decoded.playOrder.takeIf { it.sorted() == items.indices.toList() }.orEmpty(),
            )
            withLatestPosition(session)
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to load playback session", e)
            null
        }
    }

    @Synchronized
    fun clear() {
        try {
            sessionFile.delete()
            positionFile.delete()
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to clear playback session", e)
        }
    }

    /**
     * Lay the position file over [session] when it is newer. The queue-item id
     * wins over the saved index: the index is trimmed-window relative and a
     * queue edit can shift it, while the id names the row. A position whose
     * row is gone is ignored rather than guessed at.
     */
    private fun withLatestPosition(session: PlaybackSession): PlaybackSession {
        val position = try {
            if (!positionFile.exists()) return session
            json.decodeFromString<PlaybackPosition>(positionFile.readText())
        } catch (e: Exception) {
            KLog.w(TAG, "Ignoring an unreadable playback position: ${e.message}")
            return session
        }
        if (position.savedAt < session.savedAt) return session
        val index = position.queueItemId
            ?.let { id -> session.queue.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: position.currentIndex.takeIf { position.queueItemId == null && it in session.queue.indices }
            ?: return session
        return session.copy(currentIndex = index, positionMs = position.positionMs)
    }

    private fun writeAtomically(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}
