package com.ivor.ivormusic.service

import android.os.SystemClock
import androidx.media3.common.Player
import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.service.core.ListeningLedger
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Writes the listening history from the service.
 *
 * It used to be written by `PlayerViewModel` fifteen seconds after a song
 * started. The ViewModel lives with the activity, so music playing with the
 * app's screen gone - swiped from recents, started from a headset, Android
 * Auto - was never recorded, and the entry carried the song's length rather
 * than the time anyone heard of it. Recording here covers every route into
 * playback, and the ledger measures the listening.
 *
 * [sample] runs on the main thread with the audible player (it follows a
 * crossfade's engine swap, as the Last.fm tracker does). Writes go through one
 * queue on the IO dispatcher, in order, so an amendment can never overtake the
 * play it amends.
 */
internal class ListeningRecorder(
    private val stats: StatsRepository,
    private val preferences: ThemePreferences,
    scope: CoroutineScope,
    private val monotonicMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val ledger = ListeningLedger()

    private sealed interface Write {
        data class Record(val occurrence: String, val song: Song, val listenedMs: Long) : Write
        data class Amend(val occurrence: String, val listenedMs: Long) : Write
    }

    private val writes = Channel<Write>(Channel.UNLIMITED)

    /** The song each occurrence was when it started counting; read at record time. */
    private var countingSong: Pair<String, Song>? = null

    /**
     * The entry each recorded occurrence became, for its amendments. Only the
     * last few are ever amended. Guarded by itself: the writer fills it on IO,
     * [flushFinal] reads it on the main thread.
     */
    private val written = LinkedHashMap<String, PlayHistoryEntry>()

    init {
        scope.launch(Dispatchers.IO) {
            for (write in writes) {
                try {
                    when (write) {
                        is Write.Record -> {
                            // Fresh read: the switch is flipped from Settings and
                            // from the History page, each on its own instance.
                            if (!preferences.isSaveMusicHistoryEnabled()) continue
                            val entry = stats.addPlayEvent(write.song, write.listenedMs) ?: continue
                            synchronized(written) {
                                written[write.occurrence] = entry
                                while (written.size > 16) written.remove(written.keys.first())
                            }
                        }
                        is Write.Amend -> {
                            val entry = synchronized(written) { written[write.occurrence] } ?: continue
                            stats.updateListened(entry.songId, entry.timestamp, write.listenedMs)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KLog.e(TAG, "Could not write the listening history", e)
                }
            }
        }
    }

    fun sample(player: Player) {
        val item = player.currentMediaItem
        // A live stream has no end and no length to measure against; it is
        // not a play of a song.
        val occurrence = item
            ?.takeIf { it.mediaId.isNotBlank() && !player.isCurrentMediaItemLive }
            ?.let { it.queueItemId ?: it.mediaId }
        if (occurrence != null && countingSong?.first != occurrence) {
            item.toQueueSong()?.let { countingSong = occurrence to it }
        }
        for (action in ledger.sample(occurrence, player.isPlaying, monotonicMillis())) {
            dispatch(action)
        }
    }

    /** Repeat-one came round: the same occurrence is a new play from here. */
    fun restart() {
        ledger.restart()?.let(::dispatch)
    }

    /**
     * The service is going: write down what the current play has reached,
     * synchronously and briefly - the writer's scope is cancelled right after,
     * and the process may follow. At worst the last stretch of one song's time
     * is lost; the play itself was written when it qualified.
     */
    fun flushFinal() {
        writes.close()
        val amend = ledger.close() as? ListeningLedger.Action.Amend ?: return
        val entry = synchronized(written) { written[amend.occurrence] } ?: return
        try {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(FINAL_WRITE_BUDGET_MS) {
                    stats.updateListened(entry.songId, entry.timestamp, amend.listenedMs)
                }
            }
        } catch (e: Exception) {
            KLog.w(TAG, "Final listening write failed: ${e.message}")
        }
    }

    private fun dispatch(action: ListeningLedger.Action) {
        when (action) {
            is ListeningLedger.Action.Record -> {
                val song = countingSong?.takeIf { it.first == action.occurrence }?.second ?: return
                writes.trySend(Write.Record(action.occurrence, song, action.listenedMs))
            }
            is ListeningLedger.Action.Amend ->
                writes.trySend(Write.Amend(action.occurrence, action.listenedMs))
        }
    }

    private companion object {
        const val TAG = "ListeningRecorder"

        /** How long the service's teardown may wait for the last write. */
        const val FINAL_WRITE_BUDGET_MS = 400L
    }
}
