package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What the music player holds, for lists that mark the song that is playing. */
@Immutable
data class NowPlayingState(
    val songId: String? = null,
    val isPlaying: Boolean = false,
)

/**
 * The music player's current song, provided once by `HomeScreen` (which owns
 * the player) so every track list under it can mark the playing row without
 * a parameter threaded through each call site - a per-call-site wiring
 * parameter that defaults to "nothing playing" is how a feature ships half
 * wired.
 */
val LocalNowPlaying = compositionLocalOf { NowPlayingState() }

/**
 * Three bars that move while [playing] and rest low while paused: the "this
 * one" mark on the playing row. Drawn in [color] so it sits on whatever
 * container the row gives it.
 */
@Composable
fun PlayingBars(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    val transition = rememberInfiniteTransition(label = "playingBars")
    // Staggered periods so the bars never line up into one block.
    val heights = listOf(560, 430, 700).map { period ->
        val h by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
            label = "playingBar$period"
        )
        h
    }
    Canvas(modifier.size(size)) {
        val gap = this.size.width * 0.14f
        val barWidth = (this.size.width - gap * 2) / 3f
        heights.forEachIndexed { index, animated ->
            val fraction = if (playing) animated else 0.3f
            val barHeight = this.size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), this.size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
