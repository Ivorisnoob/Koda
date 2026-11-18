package com.ivor.ivormusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R

/**
 * What the players hold right now, for every list that marks the current row.
 *
 * One instance is provided at the theme root and updated in place. A row
 * never reads [mediaId] or [playing] directly: it derives a boolean for its
 * own id, so a song change recomposes the row that lost the mark and the row
 * that gained it. Reading the id itself from a list would recompose the list.
 */
@Stable
class NowPlayingState {
    var mediaId by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var videoId by mutableStateOf<String?>(null)
        private set
    var videoPlaying by mutableStateOf(false)
        private set

    fun update(
        mediaId: String?,
        playing: Boolean,
        videoId: String?,
        videoPlaying: Boolean,
    ) {
        if (this.mediaId != mediaId) this.mediaId = mediaId
        if (this.playing != playing) this.playing = playing
        if (this.videoId != videoId) this.videoId = videoId
        if (this.videoPlaying != videoPlaying) this.videoPlaying = videoPlaying
    }
}

/**
 * Provided once above the navigation host. The default never updates, so a
 * row composed outside that provider simply shows nothing playing.
 */
val LocalNowPlaying = compositionLocalOf { NowPlayingState() }

@Immutable
private data class NowPlayingMatch(val current: Boolean, val playing: Boolean)

@Composable
private fun rememberNowPlayingMatch(mediaId: String?, video: Boolean): State<NowPlayingMatch> {
    val state = LocalNowPlaying.current
    return remember(state, mediaId, video) {
        derivedStateOf(structuralEqualityPolicy()) {
            val id = mediaId?.takeIf { it.isNotBlank() }
            val current = id != null && id == if (video) state.videoId else state.mediaId
            val running = if (video) state.videoPlaying else state.playing
            NowPlayingMatch(current = current, playing = current && running)
        }
    }
}

/** True when [mediaId] is the song the music player is on. */
@Composable
fun isNowPlayingSong(mediaId: String?): Boolean {
    val match by rememberNowPlayingMatch(mediaId, video = false)
    return match.current
}

/**
 * Title colour for a row: the scheme primary while this song is the one
 * playing, [resting] otherwise.
 */
@Composable
fun nowPlayingTitleColor(mediaId: String?, resting: Color, video: Boolean = false): Color {
    val match by rememberNowPlayingMatch(mediaId, video)
    return if (match.current) MaterialTheme.colorScheme.primary else resting
}

/**
 * The equalizer mark for the current song or video.
 *
 * Laid over artwork, it sits on [androidx.compose.material3.ColorScheme.scrim]
 * so the bars stay readable on any cover. The bars themselves animate in the
 * draw phase: a frame does not recompose the row.
 */
@Composable
fun NowPlayingMark(
    mediaId: String?,
    modifier: Modifier = Modifier,
    onArtwork: Boolean = true,
    barSize: Dp = 20.dp,
    video: Boolean = false,
    currentOccurrence: Boolean? = null
) {
    val match = if (currentOccurrence == null) {
        rememberNowPlayingMatch(mediaId, video).value
    } else {
        val state = LocalNowPlaying.current
        NowPlayingMatch(currentOccurrence, currentOccurrence && if (video) state.videoPlaying else state.playing)
    }
    AnimatedVisibility(
        visible = match.current,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = modifier,
    ) {
        val description = stringResource(
            if (match.playing) R.string.cd_song_now_playing else R.string.cd_song_paused
        )
        val barColor = if (onArtwork) barsOnScrimColor() else MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (onArtwork) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                )
            }
            PlayingBars(
                playing = match.playing,
                color = barColor,
                modifier = Modifier.size(barSize),
            )
        }
    }
}

/**
 * Three bars that move while [playing] and rest low while paused.
 * Heights are read inside [Canvas], so the animation only redraws.
 */
@Composable
fun PlayingBars(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (playing) {
        MovingBars(color = color, modifier = modifier)
    } else {
        RestingBars(color = color, modifier = modifier)
    }
}

/**
 * Bars for a queue slot that already knows it is the current occurrence.
 * [video] reads the video player, so a music queue and a video queue do not
 * share a play/pause flag.
 */
@Composable
fun NowPlayingInlineBars(
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.LocalContentColor.current,
    video: Boolean = false,
) {
    val state = LocalNowPlaying.current
    val playing by remember(state, video) {
        derivedStateOf { if (video) state.videoPlaying else state.playing }
    }
    val description = stringResource(
        if (playing) R.string.cd_song_now_playing else R.string.cd_song_paused
    )
    PlayingBars(
        playing = playing,
        color = color,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * A colour that stays light on the scrim in every scheme. Scrim is black in
 * light, dark, AMOLED and album colours; `onSurface` is the light role in a
 * dark scheme and `inverseOnSurface` is the light role in a light one.
 */
@Composable
private fun barsOnScrimColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.onSurface.luminance() >= scheme.inverseOnSurface.luminance()) {
        scheme.onSurface
    } else {
        scheme.inverseOnSurface
    }
}

@Composable
private fun MovingBars(color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "nowPlayingBars")
    // Different periods, and one bar starting at the top, so they never line up.
    val first = transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
        label = "nowPlayingBar0",
    )
    val second = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(430), RepeatMode.Reverse),
        label = "nowPlayingBar1",
    )
    val third = transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "nowPlayingBar2",
    )
    Canvas(modifier) {
        drawBars(color, first.value, second.value, third.value)
    }
}

@Composable
private fun RestingBars(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawBars(color, 0.32f, 0.32f, 0.32f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(
    color: Color,
    first: Float,
    second: Float,
    third: Float,
) {
    val gap = size.width * 0.16f
    val barWidth = (size.width - gap * 2f) / 3f
    val fractions = floatArrayOf(first, second, third)
    fractions.forEachIndexed { index, fraction ->
        val barHeight = size.height * fraction.coerceIn(0.2f, 1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f),
        )
    }
}
