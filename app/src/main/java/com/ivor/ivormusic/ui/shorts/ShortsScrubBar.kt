package com.ivor.ivormusic.ui.shorts

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** Touch height of the strip; the metadata row above leaves this much room. */
internal val SHORTS_SCRUB_ZONE = 28.dp

/**
 * The Short's wavy progress line, draggable.
 *
 * Seeks once, on release: a seek per drag frame would be a ranged googlevideo
 * request per frame. The line blooms and the wave settles while dragging, and
 * the target time shows above it. Sits above the navigation bar, because a
 * horizontal drag inside the gesture-nav area switches apps.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShortsScrubBar(
    mediaId: String?,
    progress: () -> Float,
    durationMs: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberKodaHaptics()
    val currentOnSeek by rememberUpdatedState(onSeek)
    var scrubbing by remember(mediaId) { mutableStateOf(false) }
    var scrubValue by remember(mediaId) { mutableFloatStateOf(0f) }
    // Held after release until the 250ms poll catches up, so the line does
    // not flash back to the pre-seek position.
    var committed by remember(mediaId) { mutableStateOf<Float?>(null) }
    LaunchedEffect(committed) {
        val target = committed ?: return@LaunchedEffect
        withTimeoutOrNull(1_500L) { snapshotFlow { progress() }.first { abs(it - target) < 0.02f } }
        committed = null
    }

    val bloom by animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "ShortsScrubBloom",
    )

    // Read here, not inside the amplitude lambda: see the scar in ShortsPlayerOverlay.
    val waving = isPlaying && !scrubbing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SHORTS_SCRUB_ZONE)
            .pointerInput(mediaId) {
                fun fraction(x: Float) = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs() <= 0L) return@detectHorizontalDragGestures
                        scrubbing = true
                        scrubValue = fraction(offset.x)
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    },
                    onHorizontalDrag = { change, _ ->
                        if (!scrubbing) return@detectHorizontalDragGestures
                        change.consume()
                        scrubValue = fraction(change.position.x)
                    },
                    onDragEnd = {
                        if (scrubbing) {
                            scrubbing = false
                            committed = scrubValue
                            currentOnSeek((scrubValue * durationMs()).toLong())
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    },
                    onDragCancel = { scrubbing = false },
                )
            },
    ) {
        if (bloom > 0.01f) {
            val duration = durationMs()
            Text(
                text = "${formatShortsTime((scrubValue * duration).toLong())} / ${formatShortsTime(duration)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // Over footage: a fixed light label, like the player chrome.
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = bloom.coerceIn(0f, 1f)
                        translationY = -(40.dp.toPx() * bloom)
                    },
            )
        }
        LinearWavyProgressIndicator(
            progress = { (if (scrubbing) scrubValue else committed ?: progress()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    scaleY = 1f + 0.6f * bloom
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.25f + 0.2f * bloom.coerceIn(0f, 1f)),
            amplitude = { if (waving) 1f else 0f },
        )
    }
}

private fun formatShortsTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
