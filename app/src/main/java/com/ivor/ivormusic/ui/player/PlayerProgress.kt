package com.ivor.ivormusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive

/**
 * The rate the playing track is advancing at, provided once by the player host.
 *
 * One wiring rather than a parameter threaded through nine styles and the
 * private sub-composables inside them - the same reason
 * [LocalPlayerScrubInteraction] and [LocalPlayerWaveform] are locals, and the
 * per-call-site wiring trap in `docs/screens.md` is the failure it avoids. It
 * defaults to 1x so a style composed outside the host still draws a bar that
 * moves at the ordinary rate, rather than one that does not move at all.
 */
internal val LocalPlaybackSpeed = staticCompositionLocalOf { 1f }

/**
 * The playing position as a fraction that moves every frame, not once a second.
 *
 * **The service samples, the bar interpolates.** `PlayerViewModel` publishes
 * `progress` once a second while something is playing, which is the right rate
 * for a value every surface in the app collects - the mini bar, the widgets'
 * snapshot, the time labels - and the wrong rate for the one control whose whole
 * job is to show motion. Raising the sample rate was the other option and it is
 * the worse one twice over: `progress` is read in composition by the player
 * host, the mini player and all nine styles, so sixty samples a second is sixty
 * recompositions a second of each of them - the exact shape of the scar
 * `rememberPlayerWaveform` exists to undo - and it would buy nothing the clock
 * cannot already tell us.
 *
 * **What it does is extrapolate from the last sample**, at the rate the track is
 * actually playing, and re-anchor on each new one. Playback is a clock: a
 * position plus a speed is the position at the next frame, to within the drift
 * of one sample interval. So the thumb travels at a constant, truthful rate and
 * the bar is correct to the millisecond once a second.
 *
 * **Read it only inside a deferred lambda** - `progress = { smooth.value }`,
 * inside a draw scope, inside a graphics layer - never at composable scope. That
 * is what keeps sixty updates a second in the draw phase instead of invalidating
 * the style that drew the bar. Every call site in this package follows that rule,
 * and a site that reads `.value` in composition has quietly moved the whole
 * style onto the frame clock.
 *
 * **Nothing moves while a finger owns the bar.** [scrubPositionMs] is the
 * style's own local drag value: while it is non-null the fraction *is* the
 * finger, because a clock advancing under a drag is the bar fighting the hand.
 *
 * The extrapolation is capped at [MAX_EXTRAPOLATION_MS], a little over one
 * sample interval. A stall, a rebuffer or a dropped sample then leaves the thumb
 * parked slightly ahead until the next truth arrives, rather than sailing off
 * across a bar that is not moving.
 *
 * @param positionMs the last sampled position, in milliseconds.
 * @param durationMs the track length; 0 or less yields a fraction of 0.
 * @param isPlaying whether the clock is running. Paused means perfectly still.
 * @param speed the playback rate, so a 1.5x listen advances at 1.5x.
 * @param scrubPositionMs a drag in progress, in milliseconds, or null.
 */
@Composable
internal fun rememberSmoothProgress(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    scrubPositionMs: Float? = null,
    speed: Float = LocalPlaybackSpeed.current,
): State<Float> {
    val fraction = remember { mutableFloatStateOf(0f) }
    val scrubbing = scrubPositionMs != null

    // The finger's own position, written without restarting anything: a drag
    // changes this value on every frame of the gesture, and keying the effect
    // below on it would relaunch a coroutine per frame for a branch that only
    // ever returns early.
    if (scrubPositionMs != null && durationMs > 0L) {
        SideEffect {
            fraction.floatValue = (scrubPositionMs / durationMs).coerceIn(0f, 1f)
        }
    }

    // Keyed on the sample: each new one re-anchors the extrapolation, which is
    // also what makes a seek land immediately rather than being eased into.
    LaunchedEffect(positionMs, durationMs, isPlaying, speed, scrubbing) {
        val length = durationMs.takeIf { it > 0L }
        if (length == null) {
            fraction.floatValue = 0f
            return@LaunchedEffect
        }
        // Before the sample is written, not after: on the frame a drag begins,
        // this coroutine relaunches, and writing the clock's position here
        // would yank the bar off the finger for one frame.
        if (scrubbing) return@LaunchedEffect

        fraction.floatValue = (positionMs.toFloat() / length).coerceIn(0f, 1f)
        if (!isPlaying) return@LaunchedEffect

        val rate = speed.coerceAtLeast(0f)
        val anchorNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                val elapsedMs = ((frameNanos - anchorNanos) / 1_000_000f * rate)
                    .coerceIn(0f, MAX_EXTRAPOLATION_MS)
                fraction.floatValue = ((positionMs + elapsedMs) / length).coerceIn(0f, 1f)
            }
        }
    }
    return fraction
}

/**
 * How far past the last sample the bar may travel on its own: one sample
 * interval plus a little, so an arriving sample is never behind the thumb by
 * more than the jitter in its own delivery.
 */
private const val MAX_EXTRAPOLATION_MS = 1_100f
