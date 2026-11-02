package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoQuality

/**
 * Pick the starting quality from [capped], a ladder already capped to what
 * this panel can show, for the [preferred] setting of the current network.
 *
 * The ladder is sorted highest-first with 60fps variants before 30fps, so the
 * first label at or below the target height is the best match; if the video
 * has nothing at or below it, take the lowest available. A video opening in
 * the mini player ([isExpanded] false) stays out of HDR when it can.
 */
fun pickStartingVideoQuality(
    capped: List<VideoQuality>,
    preferred: String,
    isExpanded: Boolean,
): VideoQuality {
    fun height(label: String): Int = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

    val options = if (!isExpanded) {
        capped.filterNot(VideoQuality::isHdr).ifEmpty { capped }
    } else {
        capped
    }

    // The live ladder leads with an "Auto" entry (height 0) that the VOD
    // branches below would skip over, so it picks its own entry: Auto when
    // the setting says auto, otherwise the best rendition at or below the
    // target, falling back to Auto rather than to the lowest available.
    if (options.firstOrNull()?.isLive == true) {
        if (preferred == ThemePreferences.VIDEO_QUALITY_AUTO) return options.first()
        val targetHeight = height(preferred)
        return options.firstOrNull { height(it.resolution) in 1..targetHeight }
            ?: options.first()
    }

    if (preferred == ThemePreferences.VIDEO_QUALITY_AUTO) {
        return options.firstOrNull { height(it.resolution) > 0 } ?: options.first()
    }
    val targetHeight = height(preferred)
    return options.firstOrNull { height(it.resolution) in 1..targetHeight }
        ?: options.lastOrNull { height(it.resolution) > 0 }
        ?: options.first()
}
