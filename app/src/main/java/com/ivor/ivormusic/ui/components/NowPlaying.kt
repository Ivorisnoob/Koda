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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.theme.onMediaScrim

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
 *
 * **A video thumbnail gets a corner badge instead.** A whole-frame scrim with
 * bars in the middle works on a square cover, where the art is decoration.
 * Over a 16:9 frame the bars landed on the thumbnail's own text and read as a
 * loading glitch rather than a state, and the dimming hid the picture the
 * card is for. So with [video] the mark is a pill in the top corner, the
 * duration badge's opposite, saying "Now playing" or "Paused" where the frame
 * is wide enough. [barSize] is not used there; the pill has one size.
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
        if (video) {
            NowPlayingVideoBadge(playing = match.playing)
            return@AnimatedVisibility
        }
        val description = stringResource(
            if (match.playing) R.string.cd_song_now_playing else R.string.cd_song_paused
        )
        val barColor = if (onArtwork) MaterialTheme.colorScheme.onMediaScrim else MaterialTheme.colorScheme.primary
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
 * The video form of [NowPlayingMark]: a scrim pill in the top-start corner,
 * matching [ThumbnailBadge] (same scrim, same white label, since it sits on a
 * photograph rather than the theme). The label drops on a frame narrower than
 * [VIDEO_BADGE_LABEL_MIN_WIDTH] - a compact row's thumbnail - leaving the bars.
 */
@Composable
private fun NowPlayingVideoBadge(playing: Boolean) {
    val label = stringResource(
        if (playing) R.string.cd_song_now_playing else R.string.cd_song_paused
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showLabel = maxWidth >= VIDEO_BADGE_LABEL_MIN_WIDTH
        val inset = if (showLabel) 8.dp else 4.dp
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(inset)
                .semantics { contentDescription = label },
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                PlayingBars(
                    playing = playing,
                    color = MaterialTheme.colorScheme.onMediaScrim,
                    modifier = Modifier.size(12.dp),
                )
                if (showLabel) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onMediaScrim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Below this a video frame shows the badge's bars without its label. */
private val VIDEO_BADGE_LABEL_MIN_WIDTH = 180.dp

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
