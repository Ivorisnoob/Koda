package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.util.KodaHaptics
import kotlin.math.abs

/**
 * The pure rule behind the scrub return point, kept free of Compose so the JVM
 * suite can hold it to its edges.
 *
 * A drag leaves a marker where it started. Coming back to it, the playhead
 * sticks: inside [enter] of the origin the value *is* the origin, and it lets
 * go only past [exit], which is wider - hysteresis, so a finger resting on the
 * edge cannot flicker in and out and buzz on every frame.
 *
 * **It snaps only on the way back.** The drag starts on the origin, and a
 * magnet active from the first frame would hold the thumb there and make a
 * small seek impossible. So the point is armed only once the finger has been
 * further than [exit] away; until then the value passes straight through.
 */
internal object ScrubReturn {

    data class Step(val fraction: Float, val armed: Boolean, val snapped: Boolean)

    fun step(
        fraction: Float,
        origin: Float,
        armed: Boolean,
        snapped: Boolean,
        enter: Float,
        exit: Float,
    ): Step {
        if (enter <= 0f || exit < enter) return Step(fraction, armed, false)
        val distance = abs(fraction - origin)
        val nowArmed = armed || distance > exit
        if (!nowArmed) return Step(fraction, false, false)
        val nowSnapped = if (snapped) distance <= exit else distance <= enter
        return Step(if (nowSnapped) origin else fraction, true, nowSnapped)
    }
}

/**
 * Where the current scrub started, shared between a style's Slider (which asks
 * it to resolve each drag value) and [ExpressiveScrubber] (which draws the
 * marker and supplies the geometry the snap is measured in).
 *
 * Provided once by the player host through [LocalScrubReturnPoint], like the
 * scrub interaction and the waveform, so the styles need no new parameter.
 *
 * **Letting go on the marker cancels the seek** rather than seeking to it. The
 * song kept playing while the finger was away, so the origin is a few seconds
 * behind the true position by the time of release; seeking there would rewind
 * the song and rebuffer a stream, when what the gesture means is "never mind".
 */
@Stable
internal class ScrubReturnPoint {

    /** Fraction of the track the drag started at, or [NO_ORIGIN] between drags. */
    var origin by mutableFloatStateOf(NO_ORIGIN)
        private set

    /**
     * Where to draw the marker: the last drag's origin, kept after release so
     * the marker fades out where it was instead of vanishing in one frame.
     */
    var markerOrigin by mutableFloatStateOf(NO_ORIGIN)
        private set

    /** Armed: the finger has left the origin, so the marker is worth showing. */
    var isArmed by mutableStateOf(false)
        private set

    var isSnapped by mutableStateOf(false)
        private set

    private var enterFraction = 0f
    private var exitFraction = 0f

    /** Written by the drawn bar, which is the one thing that knows its width. */
    fun updateGeometry(trackWidthPx: Int, enterPx: Float, exitPx: Float) {
        if (trackWidthPx <= 0) {
            enterFraction = 0f
            exitFraction = 0f
            return
        }
        enterFraction = enterPx / trackWidthPx
        exitFraction = exitPx / trackWidthPx
    }

    /**
     * One drag value in, the value to show and seek to out, in the Slider's own
     * units. [originFraction] is read only on the first value of a drag, which is
     * when the playing position is still the position the drag left from.
     */
    fun follow(value: Float, rangeEnd: Float, originFraction: () -> Float): Float {
        if (rangeEnd <= 0f) return value
        if (origin == NO_ORIGIN) {
            origin = originFraction().coerceIn(0f, 1f)
            markerOrigin = origin
            isArmed = false
            isSnapped = false
        }
        val step = ScrubReturn.step(
            fraction = value / rangeEnd,
            origin = origin,
            armed = isArmed,
            snapped = isSnapped,
            enter = enterFraction,
            exit = exitFraction,
        )
        isArmed = step.armed
        isSnapped = step.snapped
        return step.fraction * rangeEnd
    }

    /** Ends the drag. True when it ended on the marker: do not seek. */
    fun release(): Boolean {
        val cancelled = isSnapped
        origin = NO_ORIGIN
        isArmed = false
        isSnapped = false
        return cancelled
    }

    companion object {
        const val NO_ORIGIN = -1f
    }
}

/**
 * The host's return point. The default is a fresh, unmeasured instance, so a
 * style composed outside the host still scrubs normally: with no geometry
 * written, nothing ever snaps.
 */
internal val LocalScrubReturnPoint = staticCompositionLocalOf { ScrubReturnPoint() }

/**
 * The magnet: it catches within [RETURN_SNAP_ENTER] of the marker and lets go
 * past [RETURN_SNAP_EXIT]. A fingertip is about 8dp of uncertainty, so a catch a
 * little wider than that finds the marker without aiming, and the gap between
 * the two stops an edge from chattering.
 */
internal val RETURN_SNAP_ENTER = 10.dp
internal val RETURN_SNAP_EXIT = 18.dp
private val RETURN_MARKER_RADIUS = 5.dp
private val RETURN_MARKER_INSET = 2.dp
private val RETURN_HALO_SPREAD = 7.dp
private const val RETURN_HALO_ALPHA = 0.28f

/** How far the marker has appeared and how far it has swelled for a snap, both 0..1. */
@Stable
internal class ScrubReturnMarker(shown: State<Float>, snap: State<Float>) {
    val shown by shown
    val snap by snap
}

/**
 * The marker's motion and feel for one bar, shared by the music scrubber and
 * the video seek bar so the two cannot drift apart.
 *
 * It fades in once the finger has left the origin, and swells on a bouncy
 * spring when the playhead snaps onto it. Landing on it is an edge that
 * matters (a threshold haptic) and leaving it a light release; both go through
 * [KodaHaptics] so the Haptics setting reaches them. The snap is watched
 * through a snapshot collector rather than a keyed effect, so the first
 * composition is not mistaken for a change and nothing buzzes on open.
 *
 * Read [ScrubReturnMarker.shown] and [ScrubReturnMarker.snap] only while
 * drawing: they change every frame of their springs.
 */
@Composable
internal fun rememberScrubReturnMarker(
    returnPoint: ScrubReturnPoint,
    active: Boolean,
    haptics: KodaHaptics,
): ScrubReturnMarker {
    val shown = animateFloatAsState(
        targetValue = if (active && returnPoint.isArmed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ScrubReturnMarker",
    )
    val snap = animateFloatAsState(
        targetValue = if (returnPoint.isSnapped) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "ScrubReturnSnap",
    )
    LaunchedEffect(returnPoint) {
        var previous = returnPoint.isSnapped
        snapshotFlow { returnPoint.isSnapped }.collect { snapped ->
            if (snapped == previous) return@collect
            previous = snapped
            if (snapped) haptics.threshold() else haptics.subtle()
        }
    }
    return remember(shown, snap) { ScrubReturnMarker(shown, snap) }
}

/**
 * The dot a drag left behind, where the playhead was when the finger went down.
 *
 * Two-tone so it reads on either side of the playhead: [ringColor] shows on
 * the unplayed track and [centerColor] on the played part. When the playhead
 * snaps onto it a soft halo swells out behind it, the visual half of the
 * haptic; the thumb, drawn after this, sits exactly on top.
 */
internal fun DrawScope.drawScrubReturnMarker(
    origin: Float,
    marker: ScrubReturnMarker,
    ringColor: Color,
    centerColor: Color,
) {
    val shown = marker.shown
    if (origin < 0f || shown <= 0f || size.width <= 0f) return
    val snap = marker.snap
    val center = Offset(origin.coerceIn(0f, 1f) * size.width, size.height / 2f)
    val core = RETURN_MARKER_RADIUS.toPx() * shown
    if (snap > 0f) {
        drawCircle(
            color = ringColor.copy(alpha = RETURN_HALO_ALPHA * shown * snap.coerceIn(0f, 1f)),
            radius = core + RETURN_HALO_SPREAD.toPx() * snap,
            center = center,
        )
    }
    drawCircle(color = ringColor.copy(alpha = ringColor.alpha * shown), radius = core, center = center)
    drawCircle(
        color = centerColor.copy(alpha = centerColor.alpha * shown),
        radius = (core - RETURN_MARKER_INSET.toPx()).coerceAtLeast(0f),
        center = center,
    )
}
