/*
 * Fixed rendition selection from a resolved descriptor. Stage 6 is fixed
 * quality by design: one audio plus one video rendition chosen once, no
 * adaptive switching (that is a later stage after fixed transport is stable).
 */
package com.ivor.ivormusic.data.youtube.sabr.bridge

import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat

/** Highest-bitrate audio; music plays exactly this and nothing else. */
internal fun SabrDescriptor.selectAudio(): SabrFormat? =
    formats.filter { it.isAudio }.maxByOrNull { it.bitrate }

/**
 * Highest rendition at or below [maxHeight] px, else the lowest available so
 * playback still starts on a small screen. Unknown heights (0) never win a
 * capped pick but lose the fallback by bitrate order.
 */
internal fun SabrDescriptor.selectVideo(maxHeight: Int): SabrFormat? {
    val videos = formats.filter { it.isVideo }
    videos.filter { it.height in 1..maxHeight }
        .maxWithOrNull(compareBy({ it.height }, { it.bitrate }))
        ?.let { return it }
    return videos.minWithOrNull(compareBy(
        { if (it.height <= 0) Int.MAX_VALUE else it.height }, { it.bitrate }))
}
