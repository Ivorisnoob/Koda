package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Applies a smooth back-and-forth marquee animation to single-line text when [isPlaying] is true.
 * Pauses for 2 seconds at the start before scrolling, and 2 seconds at the end.
 * If the text fits comfortably within its bounds, Compose basicMarquee does nothing.
 */
fun Modifier.titleMarquee(isPlaying: Boolean = true): Modifier = if (isPlaying) {
    this.basicMarquee(
        iterations = Int.MAX_VALUE,
        repeatDelayMillis = 2000,
        initialDelayMillis = 2000,
        velocity = 30.dp
    )
} else {
    this
}
