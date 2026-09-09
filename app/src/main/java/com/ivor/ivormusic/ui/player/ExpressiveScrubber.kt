package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.WaveformEnvelope
import com.ivor.ivormusic.data.WaveformStore
import kotlin.math.abs
import kotlin.math.exp

/**
 * The playing song's measured envelope, or null when the waveform seek bar is off.
 *
 * Provided once by the player host so every style's progress bar reaches it through one
 * wiring rather than eight that can each be forgotten - the per-call-site parameter trap
 * section 9 describes, which is how the Home feed shipped with `onEnqueueVideo` unset.
 */
internal val LocalPlayerWaveform = staticCompositionLocalOf<PlayerWaveform?> { null }

/**
 * The scrubbing gesture's interaction source, shared between each style's transparent
 * [androidx.compose.material3.Slider] and the visual drawn under it.
 *
 * This is what lets the scrubber gain touch physics without any style handing over its
 * gesture. The Slider goes on owning the drag, the seek, the value range and the
 * accessibility semantics exactly as before - all of it on the primary playback control,
 * where a regression is least affordable - and the visual merely listens to it.
 *
 * Non-null with an inert default, so a style composed outside the player host still draws:
 * its thumb simply never blooms, rather than the whole bar failing to render.
 */
internal val LocalPlayerScrubInteraction = staticCompositionLocalOf { MutableInteractionSource() }

/**
 * An envelope paired with the store revision it was read at.
 *
 * The envelope itself is mutable and grows on the sampler's thread, which Compose cannot
 * observe; [revision] is what changes identity and triggers a redraw. Recomposition therefore
 * happens at the sampler's cadence rather than per frame, and only where this local is read.
 */
@Immutable
internal class PlayerWaveform(val envelope: WaveformEnvelope, val revision: Int)

@Composable
internal fun rememberPlayerWaveform(songId: String?): PlayerWaveform? {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val enabled by preferences.waveformSeekBar.collectAsState()
    val revision by WaveformStore.version.collectAsState()
    if (!enabled || songId.isNullOrBlank()) return null
    return remember(songId, revision) {
        PlayerWaveform(WaveformStore.envelope(context, songId), revision)
    }
}

/**
 * A drop-in replacement for [LinearWavyProgressIndicator] that gives the seek bar touch
 * physics, and draws the song's measured waveform in place of a plain track when there is one.
 *
 * Shaped as a replacement on purpose. Every player style already pairs its progress visual
 * with a transparent Slider laid over it, so swapping only the visual leaves all eight styles'
 * seek behaviour untouched. [waveform] and [interactionSource] both default to their
 * composition locals, so a call site needs no argument it did not already pass.
 *
 * Three things happen on touch, each the same gesture read a different way: the thumb blooms
 * from its resting pill into a taller playhead on a bouncy spring, the wave settles flat
 * because a wave travelling under a moving finger reads as the bar lagging behind it, and -
 * where there is a waveform - the bars around the playhead swell under a soft lens, so the
 * part being aimed at is the part that grows.
 */
@Composable
internal fun ExpressiveScrubber(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    amplitude: (Float) -> Float = { 1f },
    waveform: PlayerWaveform? = LocalPlayerWaveform.current,
    interactionSource: MutableInteractionSource = LocalPlayerScrubInteraction.current,
) {
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val interacting = dragged || pressed

    val bloom by animateFloatAsState(
        targetValue = if (interacting) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ScrubberBloom",
    )

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(interacting) {
        // Taking hold of the bar and letting go of it are different events and should not feel
        // the same; the release is the one that committed a seek.
        haptics.performHapticFeedback(
            if (interacting) HapticFeedbackType.TextHandleMove else HapticFeedbackType.Confirm
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (waveform != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawWaveform(waveform.envelope, progress(), bloom, color, trackColor)
            }
        } else {
            LinearWavyProgressIndicator(
                progress = progress,
                modifier = Modifier.matchParentSize(),
                color = color,
                trackColor = trackColor,
                stroke = stroke,
                trackStroke = trackStroke,
                amplitude = { at -> amplitude(at) * (1f - bloom) },
            )
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            drawThumb(progress(), bloom, color)
        }
    }
}

/**
 * Bars mirrored about the centre line, played portion in [color] and the rest in [trackColor].
 *
 * Progress is read inside the draw lambda rather than in composition: it changes every frame
 * while a song plays, and reading it during composition would recompose the whole progress row
 * at that rate. The bar under the playhead is split at the exact fraction rather than rounded
 * to a whole bar, so the boundary advances smoothly instead of stepping.
 */
private fun DrawScope.drawWaveform(
    envelope: WaveformEnvelope,
    progress: Float,
    bloom: Float,
    color: Color,
    trackColor: Color,
) {
    val barWidth = BAR_WIDTH.toPx()
    val gap = BAR_GAP.toPx()
    val slot = barWidth + gap
    if (size.width < slot || size.height <= 0f) return
    val count = ((size.width + gap) / slot).toInt().coerceIn(1, WaveformEnvelope.BUCKETS)
    val bars = envelope.bars(count)
    val fraction = progress.coerceIn(0f, 1f)
    val playedBars = fraction * count
    val centre = size.height / 2f
    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
    val lensRadius = LENS_RADIUS.toPx().coerceAtLeast(1f)
    val playheadX = fraction * size.width
    for (index in 0 until count) {
        val left = index * slot
        // A soft bell around the playhead rather than a hard window: a boundary you can see is
        // a boundary that reads as a rendering seam.
        val distance = abs(left + barWidth / 2f - playheadX) / lensRadius
        val lens = 1f + LENS_GAIN * bloom * exp(-distance * distance)
        val level = bars[index].coerceIn(WaveformEnvelope.RESTING, 1f)
        val height = (level * size.height * lens).coerceAtLeast(barWidth)
        val played = (playedBars - index).coerceIn(0f, 1f)
        drawBar(left, centre, barWidth, height, radius, trackColor, 0f, 1f)
        if (played > 0f) drawBar(left, centre, barWidth, height, radius, color, 0f, played)
    }
}

private fun DrawScope.drawBar(
    left: Float,
    centre: Float,
    width: Float,
    height: Float,
    radius: CornerRadius,
    color: Color,
    from: Float,
    to: Float,
) {
    if (to <= from) return
    // Narrow the drawn slice rather than shortening the bar: the rounded cap belongs to the
    // bar, not to the playhead crossing it.
    clipRect(left + from * width, 0f, left + to * width, size.height) {
        drawRoundRect(
            color = color,
            topLeft = Offset(left, centre - height / 2f),
            size = Size(width, height),
            cornerRadius = radius,
        )
    }
}

/** The resting pill, growing into a taller and slightly wider playhead as [bloom] rises. */
private fun DrawScope.drawThumb(progress: Float, bloom: Float, color: Color) {
    if (size.width <= 0f || size.height <= 0f) return
    val width = THUMB_WIDTH.toPx() + (THUMB_WIDTH_BLOOMED - THUMB_WIDTH).toPx() * bloom
    val height = size.height * (THUMB_HEIGHT + (THUMB_HEIGHT_BLOOMED - THUMB_HEIGHT) * bloom)
    val centreX = (progress.coerceIn(0f, 1f) * size.width)
        .coerceIn(width / 2f, (size.width - width / 2f).coerceAtLeast(width / 2f))
    drawRoundRect(
        color = color,
        topLeft = Offset(centreX - width / 2f, size.height / 2f - height / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f, width / 2f),
    )
}

/** Wide enough to read as a waveform, narrow enough that a phone-width bar shows real detail. */
private val BAR_WIDTH = 2.dp
private val BAR_GAP = 1.5.dp

/**
 * The height a style's progress row is given once it is drawing a waveform.
 *
 * The wavy indicator only ever needed the 12-14dp those rows were built for, but a waveform is
 * read by comparing bar heights and there is nothing to compare inside 14dp. This is applied by
 * the call sites through [scrubberTrackHeight] rather than inside the component, because a fixed
 * height arriving in the modifier cannot be widened from within - and growing the row moves the
 * time labels under it, which is a layout change each style has to actually make.
 */
private val WAVEFORM_TRACK_HEIGHT = 34.dp

/** The row keeps the height it was designed for whenever the plain wavy bar is what draws. */
@Composable
internal fun scrubberTrackHeight(default: Dp): Dp =
    if (LocalPlayerWaveform.current != null) WAVEFORM_TRACK_HEIGHT else default

private val THUMB_WIDTH = 3.5.dp
private val THUMB_WIDTH_BLOOMED = 6.dp

/**
 * How far past the track the playhead stands, at rest and while held.
 *
 * It overhangs at rest too: a playhead confined to the track is lost among the bars it is
 * supposed to mark. Drawing outside the Canvas bounds is deliberate and safe here - Compose does
 * not clip unless asked - and the overhang is what the styles have to leave room for.
 */
private const val THUMB_HEIGHT = 1.6f
private const val THUMB_HEIGHT_BLOOMED = 2.2f

private val LENS_RADIUS = 28.dp
private const val LENS_GAIN = 0.45f
