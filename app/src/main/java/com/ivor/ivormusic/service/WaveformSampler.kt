package com.ivor.ivormusic.service

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.data.WaveformAnalyzer
import com.ivor.ivormusic.data.WaveformStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Measures songs for the waveform seek bar while the setting is on.
 *
 * It lives in the service rather than in the composable that draws the bar
 * because a song is mostly listened to with the player collapsed or the screen
 * off: sampling from the UI would only ever have learned the parts of a song
 * somebody happened to be watching.
 *
 * Two passes run side by side: a live sample of what is heard, and a decode of
 * the whole song so its loud and quiet stretches show before they arrive.
 */
@UnstableApi
internal class WaveformSampler(
    private val context: Context,
    private val enabled: StateFlow<Boolean>,
    private val player: () -> Player,
    private val cacheEnabled: () -> Boolean,
) {
    /** Runs for as long as the calling coroutine does. */
    suspend fun run() {
        enabled.collectLatest { on ->
            if (!on) {
                WaveformAnalyzer.reset()
                return@collectLatest
            }
            coroutineScope {
                launch { analyzeSongs() }
                sampleLive()
            }
        }
    }

    /**
     * The position comes from the active player and the level from
     * [WaveformTap], which holds the loudest sample since the previous tick -
     * so the cadence still records the true peak of every stretch rather than
     * a sparse instantaneous sample. Peaks are taken on every tick even when
     * the tick is discarded, or a peak measured during a skip would land in
     * the next song's first bucket.
     */
    private suspend fun sampleLive() {
        WaveformTap.acquire()
        try {
            while (currentCoroutineContext().isActive) {
                delay(SAMPLE_MS)
                val level = WaveformTap.takeRms()
                val current = player()
                val songId = current.currentMediaItem?.mediaId
                val duration = current.duration
                // A negative level means no PCM reached the tap at all, which is not
                // the same as a measured silence and must not be recorded as one.
                if (level < 0f || songId.isNullOrBlank() || !current.isPlaying) continue
                if (duration == C.TIME_UNSET || duration <= 0L) continue
                val position = current.currentPosition.coerceIn(0L, duration)
                WaveformStore.record(context, songId, position.toFloat() / duration, level)
                WaveformStore.flushIfDue(context)
            }
        } finally {
            WaveformTap.release()
            WaveformStore.flushIfDue(context, force = true)
        }
    }

    /**
     * Measure the whole of whatever is playing.
     *
     * The URI is taken from the player rather than resolved again here: by the
     * time an item is current it already carries the source playback is
     * reading, which is the one thing this needs and the one thing this must
     * not re-derive - a second resolution would be a second stream URL, cached
     * under the same key, for the same song.
     *
     * A placeholder is skipped rather than waited on - resolution has not
     * finished, and the next poll picks up the real URI - and an error URI
     * means it never will. The `cached` sentinel is deliberately *not* skipped:
     * it is only ever handed out for a song already whole on disk, which is the
     * cheapest song there is to measure, and the analyzer reads it back through
     * the same cache under the same key without touching the network.
     *
     * **Once the playing song is measured, the next one is.** A waveform that
     * arrives a few seconds into every track is one that is missing whenever
     * somebody looks, so the song the player will reach next is measured while
     * this one plays and its bar is there on the first frame. Only with the
     * music cache on, where those bytes are the ones the next song then plays
     * from; with it off that would be a whole song fetched and thrown away per
     * track. The analyzer runs one pass at a time and a request for the playing
     * song replaces any other, so looking ahead can never delay the bar on
     * screen.
     */
    private suspend fun analyzeSongs() {
        while (currentCoroutineContext().isActive) {
            delay(ANALYSIS_POLL_MS)
            val current = player()
            val item = current.currentMediaItem ?: continue
            val currentId = item.mediaId.takeIf { it.isNotBlank() } ?: continue
            if (!WaveformStore.isComplete(context, currentId)) {
                request(item, current.duration.takeIf { it != C.TIME_UNSET && it > 0L })
                continue
            }
            if (!cacheEnabled()) continue
            val nextIndex = current.nextMediaItemIndex
            if (nextIndex == C.INDEX_UNSET || nextIndex >= current.mediaItemCount) continue
            val next = current.getMediaItemAt(nextIndex)
            if (next.mediaId.isBlank() || next.mediaId == currentId) continue
            if (WaveformStore.isComplete(context, next.mediaId)) continue
            request(next, durationMs = null)
        }
    }

    private fun request(item: MediaItem, durationMs: Long?) {
        val uri = item.localConfiguration?.uri ?: return
        if (uri.toString().startsWith(MusicStreamResolver.PLACEHOLDER_PREFIX) || uri.scheme == "error") return
        WaveformAnalyzer.request(
            context = context,
            songId = item.mediaId,
            uri = uri,
            durationMs = durationMs,
            musicCacheEnabled = cacheEnabled(),
        )
    }

    private companion object {
        /**
         * How often the live sampler looks. Fine enough that a 512-bucket
         * envelope over a three-minute song still sees several measurements per
         * bucket - at 200ms it saw about one, and a bucket fed by a single
         * reading is what made the drawn bar coarse.
         */
        const val SAMPLE_MS = 100L

        /**
         * How often the whole-song measurement looks for something new to
         * measure. A poll rather than a media-item listener because the thing
         * it waits for is not a transition: a YouTube song enters the queue as
         * a placeholder and gains its real URI when resolution replaces the
         * source under it, which emits nothing. Half a second is far below how
         * long a decode takes, so nothing is gained by noticing sooner.
         */
        const val ANALYSIS_POLL_MS = 500L
    }
}
