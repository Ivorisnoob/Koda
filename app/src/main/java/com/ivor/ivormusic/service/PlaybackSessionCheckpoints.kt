package com.ivor.ivormusic.service

import androidx.media3.common.Player
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.PlaybackSessionRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Remembering what was playing, so a process death resumes the right song at
 * the right place.
 *
 * The service owns playback, so it owns remembering it. This used to be
 * PlayerViewModel's job, and that ViewModel only exists while the app's screen
 * does: with the screen gone - Android Auto, a headset or widget resume, the
 * app swiped away mid-song, Back out of it on Android 11 - nothing was saved,
 * and a later process death restored an old song at an old position. The
 * queue is written back from the player's own rows ([toMusicQueueItem]), so it
 * needs nothing from the UI.
 *
 * Every entry point but the writer runs on the main thread, where the player
 * is read.
 *
 * @param repository read only by the writer, so nothing touches the disk on
 *   the main thread or before the first write.
 * @param player the audible player, or null before the engine exists.
 * @param unshuffledOrder the order shuffling replaced, by [occurrenceKeys], or
 *   null while unshuffled. The file keeps its old shape: the queue in that
 *   order, and the timeline - the order it plays - as a permutation of it.
 */
internal class PlaybackSessionCheckpoints(
    private val repository: () -> PlaybackSessionRepository,
    private val mainScope: CoroutineScope,
    private val ioScope: CoroutineScope,
    private val player: () -> Player?,
    private val unshuffledOrder: () -> List<String>?,
) {
    private sealed interface SessionWrite {
        /** Main-thread order; the writer never applies an older snapshot over a newer one. */
        val seq: Long

        class Queue(
            override val seq: Long,
            val fingerprint: Long,
            val queue: List<MusicQueueItem>,
            val index: Int,
            val positionMs: Long,
            val order: IntArray?,
        ) : SessionWrite

        class Position(
            override val seq: Long,
            val queueItemId: String?,
            val index: Int,
            val positionMs: Long,
        ) : SessionWrite

        class Clear(override val seq: Long) : SessionWrite
    }

    /**
     * Only the newest snapshot is worth writing, so a burst of checkpoints
     * collapses into one. Safe to conflate across kinds: until a queue write
     * lands, [writtenFingerprint] still differs, so every snapshot taken
     * meanwhile is itself a full queue write.
     */
    private val writes = Channel<SessionWrite>(Channel.CONFLATED)
    private val writeLock = Any()
    private var seq = 0L
    private var lastWrittenSeq = 0L
    private var debounceJob: Job? = null
    private var closed = false

    /** What the queue file on disk describes; null until this service has written one. */
    @Volatile private var writtenFingerprint: Long? = null

    fun start() {
        ioScope.launch {
            for (write in writes) write(write)
        }
    }

    /**
     * Save now: a song change, a pause, the periodic tick. Runs inside player
     * callbacks, where an exception would reach ExoPlayer's listener dispatch,
     * so a failed save is logged and never allowed to disturb playback.
     */
    fun checkpoint() {
        debounceJob?.cancel()
        debounceJob = null
        try {
            snapshot()?.let { writes.trySend(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Session checkpoint failed: ${e.message}")
        }
    }

    /** Save shortly, once a burst of queue edits has settled. */
    fun schedule() {
        if (closed) return
        debounceJob?.cancel()
        debounceJob = mainScope.launch {
            delay(DEBOUNCE_MS)
            debounceJob = null
            checkpoint()
        }
    }

    /** The queue was emptied: nothing should restore it next launch. */
    fun clear() {
        if (closed) return
        debounceJob?.cancel()
        debounceJob = null
        writes.trySend(SessionWrite.Clear(++seq))
    }

    /**
     * The last write, synchronously: the process may be gone before a
     * background writer runs. Later events (the player's own release) must not
     * clear what this saved, so writing closes here.
     */
    fun writeFinal() {
        try {
            snapshot()?.let(::write)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Final session write failed: ${e.message}")
        }
        closed = true
        debounceJob?.cancel()
        writes.close()
    }

    private fun write(write: SessionWrite) {
        synchronized(writeLock) {
            if (write.seq <= lastWrittenSeq) return
            lastWrittenSeq = write.seq
            val repository = repository()
            when (write) {
                is SessionWrite.Queue -> {
                    repository.saveQueue(write.queue, write.index, write.positionMs, write.order)
                    writtenFingerprint = write.fingerprint
                }
                is SessionWrite.Position ->
                    repository.savePosition(write.queueItemId, write.index, write.positionMs)
                is SessionWrite.Clear -> {
                    repository.clear()
                    writtenFingerprint = null
                }
            }
        }
    }

    /**
     * Which queue this is, cheaply: every saved row's key, the play order, and
     * where the saved window starts, so a queue longer than the cap is written
     * again once the current song walks out of the saved part.
     */
    private fun fingerprint(saved: List<String>, order: IntArray?, index: Int): Long {
        var hash = 17L
        for (key in saved) hash = hash * 31 + key.hashCode()
        if (order != null) {
            hash = hash * 31 + 1
            for (position in order) hash = hash * 31 + position
        }
        return hash * 31 + PlaybackSessionRepository.savedWindowStart(saved.size, index)
    }

    /**
     * A write describing the player now: the whole queue when it differs from
     * what is on disk, otherwise just the position. Main thread only.
     */
    private fun snapshot(): SessionWrite? {
        if (closed) return null
        val current = player() ?: return null
        val count = current.mediaItemCount
        if (count == 0) return null
        val playing = current.currentMediaItemIndex.takeIf { it in 0 until count } ?: return null
        val positionMs = current.currentPosition.coerceAtLeast(0L)

        // Saved in the order shuffling replaced, when it did, and the
        // timeline as the order those rows play in.
        val timeline = current.occurrenceKeys()
        val saved = unshuffledOrder()?.let { order ->
            val present = timeline.toHashSet()
            val remembered = order.filter { it in present }
            val placed = remembered.toHashSet()
            remembered + timeline.filterNot { it in placed }
        } ?: timeline
        val savedIndexOf = HashMap<String, Int>(saved.size).also { map -> saved.forEachIndexed { at, key -> map[key] = at } }
        val order = if (saved == timeline) null else IntArray(count) { savedIndexOf.getValue(timeline[it]) }
        val index = savedIndexOf.getValue(timeline[playing])

        val fingerprint = fingerprint(saved, order, index)
        if (fingerprint == writtenFingerprint) {
            return SessionWrite.Position(++seq, current.currentMediaItem?.queueItemId, index, positionMs)
        }
        val rowOf = HashMap<String, Int>(count).also { map -> timeline.forEachIndexed { at, key -> map[key] = at } }
        val queue = ArrayList<MusicQueueItem>(count)
        for (key in saved) {
            queue += current.getMediaItemAt(rowOf.getValue(key)).toMusicQueueItem() ?: return null
        }
        return SessionWrite.Queue(++seq, fingerprint, queue, index, positionMs, order)
    }

    private companion object {
        const val TAG = "MusicService"

        /**
         * How long a queue edit waits before the session is saved. Coalesces a
         * drag across several rows, and the burst of source replacements
         * prefetching makes, into one write.
         */
        const val DEBOUNCE_MS = 1_500L
    }
}
