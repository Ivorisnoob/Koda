package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.WaveformEnvelope
import com.ivor.ivormusic.data.WaveformStore
import com.ivor.ivormusic.util.WaveformReveal
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
 * One song's finished waveform: a frozen envelope plus the bars it resamples to.
 *
 * Frozen is the whole contract. The bar is a picture of the *song*, not of the listening, so
 * once a song's shape is known it does not change again until another song starts - which is
 * also what lets the resampled bars be memoized. [WaveformEnvelope.bars] runs a reduction and
 * a percentile sort over every bar, and recomputing that on each frame of playback spent real
 * work arriving at the identical array; the width only changes when the row is resized.
 *
 * The memo is mutable behind an [Immutable] class, which is sound because nothing observable
 * changes: the same count always yields the same bars. It is touched only from the draw phase,
 * which is single-threaded.
 */
@Immutable
internal class PlayerWaveform(private val envelope: WaveformEnvelope) {

    private var barCount = 0
    private var bars: FloatArray = FloatArray(0)

    fun bars(count: Int): FloatArray {
        if (count == barCount) return bars
        bars = envelope.bars(count)
        barCount = count
        return bars
    }
}

/**
 * The playing song's waveform, once the whole of it is known, and never before.
 *
 * Two rules here, and the second is the change the first made possible.
 *
 * **It waits for the whole song.** A part-measured envelope is not a waveform of the track; it
 * is a picture of how far somebody has listened, and drawing one means the bars ahead of the
 * playhead keep changing as they are reached. So this stays null - the styles then draw the
 * ordinary wavy bar - until [WaveformStore] has enough of the song to show all of it, which
 * [com.ivor.ivormusic.data.WaveformAnalyzer] normally arranges before playback is even audible.
 *
 * **Nothing here recomposes at the measuring rate.** The store's revision used to be collected
 * in composition, so every recorded sample - ten a second, for the whole of every song -
 * recomposed the player host and each style's progress row. It is collected in an effect now
 * and read exactly once: the first revision at which the song is ready wins, and the pin holds
 * for that song. The in-memory answer is taken during composition so a song whose shape is
 * already stored has it on the first frame rather than one frame later, which is what stops
 * the row collapsing and re-expanding around the waveform layout on every track change.
 */
@Composable
internal fun rememberPlayerWaveform(songId: String?): PlayerWaveform? {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) { ThemePreferences(context) }
    val enabled by preferences.waveformSeekBar.collectAsState()

    var waveform by remember(songId, enabled) {
        mutableStateOf(
            if (enabled && !songId.isNullOrBlank()) {
                WaveformStore.cachedReadySnapshot(songId)?.let(::PlayerWaveform)
            } else {
                null
            }
        )
    }
    LaunchedEffect(songId, enabled) {
        if (!enabled || songId.isNullOrBlank() || waveform != null) return@LaunchedEffect
        val ready = WaveformStore.version
            // The first ask may reach disk, which is not something to do on the frame that
            // opens the player; every later one is a map lookup.
            .map { withContext(Dispatchers.IO) { WaveformStore.readySnapshot(context, songId) } }
            .filterNotNull()
            .first()
        waveform = PlayerWaveform(ready)
    }
    return waveform
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
 *
 * **The waveform arrives and then holds still.** When a song's shape appears - at load for a
 * device file, once its stream has been read for a YouTube song, instantly for anything played
 * before - the bars sweep up out of a flat line, each springing past its level and settling,
 * and that is the last time they move. Everything after it is the same picture with the played
 * side filled in. A bar that grew behind the playhead could only ever describe the part already
 * heard, which is the opposite of what the row is for.
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

    // One entrance per waveform, and it ends: `reveal` reaches 1 and stays there, so every
    // frame after it draws the identical bars. Constant rate rather than a spring because the
    // bounce belongs to each bar - see WaveformReveal.
    val reveal = remember(waveform) { Animatable(0f) }
    LaunchedEffect(waveform) {
        if (waveform != null) {
            reveal.animateTo(1f, tween(REVEAL_DURATION_MS, easing = LinearEasing))
        }
    }

    // Through the app's own vocabulary rather than LocalHapticFeedback, so the Haptics setting
    // reaches the seek bar: calling the platform directly is what made this one control go on
    // buzzing for somebody who had turned haptics off.
    val haptics = rememberKodaHaptics()
    var hasBeenHeld by remember { mutableStateOf(false) }
    LaunchedEffect(interacting) {
        // The first run of this effect is composition rather than a release. Firing there gave
        // a confirm buzz every time the player opened or a style crossfaded in, for a bar
        // nobody had touched.
        if (!interacting && !hasBeenHeld) return@LaunchedEffect
        hasBeenHeld = true
        // Taking hold of the bar and letting go of it are different events and should not feel
        // the same; the release is the one that committed a seek.
        if (interacting) haptics.tick() else haptics.confirm()
    }

    // How wide the style made this row, which is what turns a fraction into a bar index.
    var trackWidthPx by remember { mutableStateOf(0) }
    val detentPx = with(LocalDensity.current) { (BAR_WIDTH + BAR_GAP).toPx() } * TICK_EVERY_BARS
    ScrubDetentTicks(
        active = interacting,
        trackWidthPx = trackWidthPx,
        detentPx = detentPx,
        progress = progress,
        onTick = haptics::tick,
    )

    Box(
        modifier = modifier.onSizeChanged { trackWidthPx = it.width },
        contentAlignment = Alignment.Center,
    ) {
        if (waveform != null) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawWaveform(waveform, progress(), bloom, reveal.value, color, trackColor)
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
 * One small tick each time the playhead crosses a detent while the bar is held, so a scrub has
 * texture under the thumb the way a physical wheel does.
 *
 * The detents are the drawn bars, taken every [TICK_EVERY_BARS] so a slow drag ticks steadily
 * rather than buzzing: at one per bar a sweep across a phone-width track is a hundred pulses,
 * which the vibrator smears into a single tone and which tells the hand nothing. The grid is
 * the waveform's own, so the tick and the bar passing under the playhead are one event rather
 * than two that happen near each other - the rule the video player's level slats already
 * follow.
 *
 * It reads the *drawn* position rather than the raw gesture. Each style feeds this component a
 * spring-smoothed fraction, so the playhead trails the finger slightly; ticking off the finger
 * instead would put the buzz somewhere the eye is not. The offset is constant while a drag is
 * moving, so what the hand feels is the cadence, which is right either way.
 *
 * [MIN_TICK_GAP_NANOS] is a floor rather than a rate: a flick can cross detents faster than the
 * vibrator can separate them, and queued pulses arrive after the gesture has finished.
 *
 * Separated from [ExpressiveScrubber] so the snapshot subscription is scoped to the gesture -
 * `progress` changes every frame of playback, and a collector left running would be woken by
 * every one of them for the whole of every song.
 */
@Composable
private fun ScrubDetentTicks(
    active: Boolean,
    trackWidthPx: Int,
    detentPx: Float,
    progress: () -> Float,
    onTick: () -> Unit,
) {
    // Keyed on the resolved pixel spacing rather than on the Density that produced it: a
    // restart resets the detent the gesture is measured from, and an identity comparison on a
    // composition local would restart this mid-drag and drop ticks.
    LaunchedEffect(active, trackWidthPx, detentPx) {
        if (!active || trackWidthPx <= 0 || detentPx <= 0f) return@LaunchedEffect
        var lastDetent = Int.MIN_VALUE
        var lastTickNanos = 0L
        snapshotFlow { progress() }.collect { value ->
            val detent = (value.coerceIn(0f, 1f) * trackWidthPx / detentPx).toInt()
            if (detent == lastDetent) return@collect
            // The detent the gesture started on is where the finger already is, not a crossing;
            // ticking for it would double up with the tick that acknowledged the grab.
            val opening = lastDetent == Int.MIN_VALUE
            lastDetent = detent
            if (opening) return@collect
            val now = System.nanoTime()
            if (now - lastTickNanos < MIN_TICK_GAP_NANOS) return@collect
            lastTickNanos = now
            onTick()
        }
    }
}

/**
 * Bars mirrored about the centre line, played portion in [color] and the rest in [trackColor].
 *
 * Progress, bloom and reveal are all read inside the draw lambda rather than in composition:
 * progress changes every frame while a song plays and the other two every frame of an
 * animation, and reading any of them during composition would recompose the whole progress row
 * at that rate. The bar under the playhead is split at the exact fraction rather than rounded
 * to a whole bar, so the boundary advances smoothly instead of stepping.
 *
 * [reveal] is the entrance, and only the entrance. It runs 0..1 once when the song's shape
 * arrives, lifting each bar from a dot on the centre line to its measured height as the sweep
 * reaches it, and every bar's factor is exactly 1 from then on - so the heights this draws are
 * the song's own levels, not levels an animation is still scaling.
 */
private fun DrawScope.drawWaveform(
    waveform: PlayerWaveform,
    progress: Float,
    bloom: Float,
    reveal: Float,
    color: Color,
    trackColor: Color,
) {
    val barWidth = BAR_WIDTH.toPx()
    val gap = BAR_GAP.toPx()
    val slot = barWidth + gap
    if (size.width < slot || size.height <= 0f) return
    val count = ((size.width + gap) / slot).toInt().coerceIn(1, WaveformEnvelope.BUCKETS)
    val bars = waveform.bars(count)
    val fraction = progress.coerceIn(0f, 1f)
    val playedBars = fraction * count
    val centre = size.height / 2f
    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
    val lensRadius = LENS_RADIUS.toPx().coerceAtLeast(1f)
    val playheadX = fraction * size.width
    val settled = reveal >= 1f
    for (index in 0 until count) {
        val left = index * slot
        // A soft bell around the playhead rather than a hard window: a boundary you can see is
        // a boundary that reads as a rendering seam.
        val distance = abs(left + barWidth / 2f - playheadX) / lensRadius
        val lens = 1f + LENS_GAIN * bloom * exp(-distance * distance)
        val level = bars[index].coerceIn(WaveformEnvelope.RESTING, 1f)
        val target = (level * size.height * lens).coerceAtLeast(barWidth)
        // Rising from a dot rather than from nothing, so the row never goes empty: the bars
        // start as the flat line the plain track was and unfold into the song.
        val height = if (settled) {
            target
        } else {
            val risen = WaveformReveal.factorAt(index, count, reveal)
            (barWidth + (target - barWidth) * risen).coerceIn(barWidth, size.height)
        }
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

/**
 * The row keeps the height it was designed for whenever the plain wavy bar is what draws, and
 * grows into the taller one as a waveform arrives.
 *
 * Animated because the arrival can happen mid-song - a streamed track finishes being measured
 * while it is already playing - and a progress row that jumps 20dp taller in one frame drags
 * the time labels and everything under it with it. Growing over the same moment the bars sweep
 * up reads as the bar unfolding instead. A song whose shape is already stored is non-null on
 * its first composition, so an ordinary track change starts and stays at the full height rather
 * than bouncing through the short one.
 */
@Composable
internal fun scrubberTrackHeight(default: Dp): Dp {
    val target = if (LocalPlayerWaveform.current != null) WAVEFORM_TRACK_HEIGHT else default
    val height by animateDpAsState(
        targetValue = target,
        animationSpec = spring(
            // No bounce: this is the row every other element measures against, and overshooting
            // its height moves the labels below it twice.
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "ScrubberTrackHeight",
    )
    return height
}

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

/**
 * One tick every third bar.
 *
 * A bar slot is 3.5dp, so this is a detent every 10.5dp - about thirty across a phone-width
 * track, which is a steady tick under a deliberate drag and a fine burr under a flick.
 */
private const val TICK_EVERY_BARS = 3

/** Roughly fifty pulses a second, which is as many as a phone can keep distinct. */
private const val MIN_TICK_GAP_NANOS = 20_000_000L

/**
 * How long the bars take to sweep in.
 *
 * Long enough to read as a sweep across the track rather than a flash, short enough that it is
 * finished before anybody could want to scrub. It is one animation per song, never a loop.
 */
private const val REVEAL_DURATION_MS = 620
