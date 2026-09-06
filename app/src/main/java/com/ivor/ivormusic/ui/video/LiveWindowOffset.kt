package com.ivor.ivormusic.ui.video

/** A fixed tolerance, independent of whether the DVR window is minutes or hours long. */
internal const val LIVE_EDGE_TOLERANCE_MS = 10_000L

/** Distance from the newest seekable media; unknown timelines must not invent a time. */
internal fun liveWindowOffsetMs(durationMs: Long, positionMs: Long): Long? =
    durationMs.takeIf { it > 0L }?.let { it - positionMs.coerceIn(0L, it) }
