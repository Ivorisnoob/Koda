package com.ivor.ivormusic.ui.video

/** A fixed tolerance, independent of whether the DVR window is minutes or hours long. */
internal const val LIVE_EDGE_TOLERANCE_MS = 10_000L

/**
 * How far behind the live edge playback is; unknown timelines must not invent
 * a time.
 *
 * [targetOffsetMs] is the distance behind the newest media in the window that
 * the player is deliberately holding, read off the timeline the source
 * published (`VideoPlayerViewModel.liveTargetOffsetMs`). It is not slack to be
 * forgiven - it *is* the live edge. [scar] A live HLS player is never at the
 * end of its playlist: with no `EXT-X-START` and no `HOLD-BACK`, which is what
 * YouTube sends, Media3 targets three target-durations back from the end, so
 * with ~5s segments a stream sitting exactly where the manifest asks reads as
 * 15-16s behind the window end. Measuring from the end therefore reported a
 * permanent -0:15 and the chip never said LIVE. Measured from the edge, the
 * number means what a viewer reads it as: how far they have scrubbed back.
 *
 * Zero target reduces this to the old window-end measurement, which is the
 * right answer for a source that declares no target at all.
 */
internal fun liveWindowOffsetMs(
    durationMs: Long,
    positionMs: Long,
    targetOffsetMs: Long = 0L,
): Long? = durationMs.takeIf { it > 0L }?.let { duration ->
    val edge = duration - targetOffsetMs.coerceIn(0L, duration)
    (edge - positionMs.coerceIn(0L, duration)).coerceAtLeast(0L)
}
