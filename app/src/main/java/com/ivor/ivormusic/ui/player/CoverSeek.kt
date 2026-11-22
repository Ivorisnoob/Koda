package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Double-tap the music player's cover to seek: the left half jumps back, the
 * right half forward, [CoverSeek.STEP_MS] a tap (#381). Taps in a row on the
 * same side add up, the way YouTube's do, and the label says the running total.
 *
 * One instance per expanded player, handed to every style through
 * [LocalCoverSeek]; null there when the setting is off, which a style passes
 * straight to `detectTapGestures(onDoubleTap = ...)` so a single tap on the
 * cover keeps answering at once instead of waiting out the double-tap window.
 */
@Stable
class CoverSeek internal constructor(
    private val scope: CoroutineScope,
    private val seekBy: (Long) -> Unit,
) {
    /** -1 back, 1 forward, 0 before the first tap. */
    internal var side by mutableIntStateOf(0)
        private set

    /** Taps in the current run, for the label. */
    internal var taps by mutableIntStateOf(0)
        private set

    internal val flash = Animatable(0f)
    private var lastTapAt = 0L

    /** A double tap at [x] across a cover [width] wide. */
    fun onDoubleTap(x: Float, width: Int) {
        val direction = coverSeekDirection(x, width)
        val now = System.currentTimeMillis()
        taps = if (direction == side && now - lastTapAt < RUN_WINDOW_MS) taps + 1 else 1
        side = direction
        lastTapAt = now
        seekBy(direction * STEP_MS)
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(durationMillis = FADE_MS, delayMillis = HOLD_MS))
        }
    }

    companion object {
        const val STEP_MS = 10_000L
        private const val RUN_WINDOW_MS = 1_200L
        private const val HOLD_MS = 450
        private const val FADE_MS = 300
    }
}

/** Back for the left half, forward for the right; a cover with no width seeks forward. */
internal fun coverSeekDirection(x: Float, width: Int): Int = if (width > 0 && x < width / 2f) -1 else 1

/** The player's [CoverSeek], or null while double-tap seeking is switched off. */
internal val LocalCoverSeek = staticCompositionLocalOf<CoverSeek?> { null }

@Composable
internal fun rememberCoverSeek(seekBy: (Long) -> Unit): CoverSeek {
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(seekBy)
    return remember(scope) { CoverSeek(scope) { current(it) } }
}

/**
 * Draws the seek feedback over the half of the cover that was tapped: a scrim
 * on that side and the running total ("-20 s"). Nothing is drawn at rest.
 */
fun Modifier.coverSeekFeedback(seek: CoverSeek?): Modifier = if (seek == null) this else composed {
    val measurer = rememberTextMeasurer()
    val scrim = MaterialTheme.colorScheme.scrim
    // A pill in the player's own container role, so the label reads on any
    // cover without a hardcoded colour.
    val pill = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    drawWithContent {
        drawContent()
        val alpha = seek.flash.value
        if (alpha <= 0f || seek.side == 0) return@drawWithContent
        val half = size.width / 2f
        val left = if (seek.side < 0) 0f else half
        clipRect(left = left, top = 0f, right = left + half, bottom = size.height) {
            drawRect(scrim.copy(alpha = 0.35f * alpha), topLeft = Offset(left, 0f), size = Size(half, size.height))
        }
        val seconds = seek.taps * (CoverSeek.STEP_MS / 1000)
        val label = if (seek.side < 0) "-$seconds s" else "+$seconds s"
        val layout = measurer.measure(
            label,
            TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = alpha)),
        )
        val padX = 14.dp.toPx()
        val padY = 6.dp.toPx()
        val pillSize = Size(layout.size.width + padX * 2, layout.size.height + padY * 2)
        val pillTopLeft = Offset(left + (half - pillSize.width) / 2f, (size.height - pillSize.height) / 2f)
        drawRoundRect(
            color = pill.copy(alpha = alpha),
            topLeft = pillTopLeft,
            size = pillSize,
            cornerRadius = CornerRadius(pillSize.height / 2f),
        )
        drawText(layout, topLeft = Offset(pillTopLeft.x + padX, pillTopLeft.y + padY))
    }
}

/**
 * A cover's tap handling with the seek double tap folded in: [onTap] is what a
 * single tap already did (usually play and pause). With seeking off this is
 * the plain single-tap detector the styles had before.
 */
fun Modifier.coverTaps(seek: CoverSeek?, onTap: () -> Unit): Modifier = this
    .coverSeekFeedback(seek)
    .pointerInput(seek) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = seek?.let { s -> { offset: Offset -> s.onDoubleTap(offset.x, size.width) } },
        )
    }

/** For a cover that had no tap of its own: only the seek double tap, when it is on. */
fun Modifier.coverSeekOnly(seek: CoverSeek?): Modifier = if (seek == null) this else this
    .coverSeekFeedback(seek)
    .pointerInput(seek) {
        detectTapGestures(onDoubleTap = { offset -> seek.onDoubleTap(offset.x, size.width) })
    }
