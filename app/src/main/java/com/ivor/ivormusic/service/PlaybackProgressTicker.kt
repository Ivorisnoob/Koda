package com.ivor.ivormusic.service

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The once-a-second loop that runs while music plays: the Android 16 Live
 * Update, the widgets' progress strip and the periodic session save.
 *
 * Single-flight: only one loop ever runs. The service used to launch a fresh
 * one on every STATE_READY, which fires several times per song as resolution
 * replaces the source, and the loops fought over the Live Update.
 *
 * Main-thread owned; every callback is read on each tick, so a crossfade that
 * swaps the audible player is followed on the next one.
 */
internal class PlaybackProgressTicker(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> Player,
    private val liveUpdate: () -> MusicProgressLiveUpdate?,
    private val publishWidgets: () -> Unit,
    private val checkpoint: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            var widgetTick = 0
            var checkpointTick = 0
            try {
                while (isActive && player().isPlaying) {
                    val current = player()
                    val duration = current.duration
                    val position = current.currentPosition

                    // Android 16 Live Update
                    if (duration > 0) {
                        val mediaItem = current.currentMediaItem
                        liveUpdate()?.updateProgress(
                            songTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                            artistName = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown",
                            currentPositionMs = position,
                            durationMs = duration,
                            isPlaying = true,
                            speed = current.playbackParameters.speed,
                        )
                    }

                    // Push progress to the widget family every 5 seconds so the
                    // progress strip advances visibly without re-rendering every
                    // tick. Cheap — one snapshot bind shared across all widgets.
                    widgetTick++
                    if (widgetTick >= WIDGET_TICKS) {
                        widgetTick = 0
                        publishWidgets()
                    }

                    // A process death loses at most this much position.
                    checkpointTick++
                    if (checkpointTick >= CHECKPOINT_TICKS) {
                        checkpointTick = 0
                        checkpoint()
                    }

                    // The fade-out used to live here, on a one-second tick,
                    // which gave a three second fade about three volume steps.
                    // monitorTransitions drives it now, off the real playback
                    // position, and the outgoing ramp belongs to CrossfadeEngine.

                    delay(TICK_MS)
                }
            } finally {
                // Loop exited (paused / stopped / cancelled) — drop the live update so
                // it never freezes at the last reported progress.
                liveUpdate()?.hide()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val TICK_MS = 1000L
        const val WIDGET_TICKS = 5

        /** Ticks between checkpoints while playing. */
        const val CHECKPOINT_TICKS = 15
    }
}
