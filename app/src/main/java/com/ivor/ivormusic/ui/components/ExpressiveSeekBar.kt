package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.VideoChapter
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A bespoke Material 3 Expressive wavy seekbar for video playback.
 *
 * Designed with:
 * 1. Global continuous wave phase: Wavy active track flows seamlessly across chapter segments.
 * 2. Segmented chapter gaps: Clean gaps with smooth rounded ends at each timestamp.
 * 3. No background bleed: Inactive track is drawn only from the playhead forward (never behind the wave).
 * 4. Expanding handle (thumb): Scales on touch/scrub with spring physics.
 * 5. Smooth flattening: Amplitude smoothly animates to 0 when paused or scrubbing.
 * 6. Haptic chapter boundary feedback.
 */
@Composable
fun ExpressiveSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    chapters: List<VideoChapter> = emptyList(),
    durationMs: Long = 0L,
    isPlaying: Boolean = false,
    isWavy: Boolean = true,
    onTonalSurface: Boolean = false,
    trackHeight: Dp = 4.5.dp,
    gapWidth: Dp = 3.5.dp,
    onScrub: ((Float?) -> Unit)? = null
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)

    // Clear pending seek lock once player's reported progress reaches the seek destination
    LaunchedEffect(progress) {
        val pending = pendingSeekFraction
        if (pending != null && abs(progress - pending) < 0.03f) {
            pendingSeekFraction = null
        }
    }

    // Safety timeout: unlock after 700ms in case progress updates slowly or video is paused
    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction != null) {
            delay(700)
            pendingSeekFraction = null
        }
    }

    val displayedFraction = (if (isScrubbing) scrubFraction else (pendingSeekFraction ?: progress)).coerceIn(0f, 1f)

    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Colors
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveTrackColor = if (onTonalSurface) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color.White.copy(alpha = 0.28f)
    }
    val bufferedColor = if (onTonalSurface) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    } else {
        Color.White.copy(alpha = 0.50f)
    }
    val thumbColor = MaterialTheme.colorScheme.primary

    // Wave amplitude: active and wavy when playing (and wavy enabled), flattens cleanly when paused or scrubbing
    val targetAmplitude = if (isPlaying && !isScrubbing && isWavy) 3.5f else 0f
    val animatedAmplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "waveAmplitude"
    )

    // Animated wave phase for continuous playback motion
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val animatedThumbWidth by animateFloatAsState(
        targetValue = if (isScrubbing) with(density) { 8.5.dp.toPx() } else with(density) { 4.5.dp.toPx() },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbWidth"
    )
    val animatedThumbHeight by animateFloatAsState(
        targetValue = if (isScrubbing) with(density) { 26.dp.toPx() } else with(density) { 18.dp.toPx() },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbHeight"
    )

    val segments = remember(chapters, durationMs) {
        calculateChapterSegments(chapters, durationMs)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(segments) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val width = size.width.toFloat()
                    if (width <= 0f) return@awaitEachGesture

                    var currentFraction = (down.position.x / width).coerceIn(0f, 1f)
                    isScrubbing = true
                    scrubFraction = currentFraction
                    currentOnScrub?.invoke(currentFraction)

                    var lastChapter = getChapterIndex(segments, currentFraction)
                    var isCancelled = false
                    val pointerId = down.id

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()

                            if (change == null) {
                                isCancelled = true
                                break
                            }

                            if (!change.pressed) {
                                change.consume()
                                break
                            }

                            val newFraction = (change.position.x / width).coerceIn(0f, 1f)
                            change.consume()

                            if (newFraction != currentFraction) {
                                currentFraction = newFraction
                                scrubFraction = newFraction
                                currentOnScrub?.invoke(newFraction)

                                val chapterIndex = getChapterIndex(segments, newFraction)
                                if (chapterIndex != lastChapter && chapterIndex >= 0) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastChapter = chapterIndex
                                }
                            }
                        }

                        if (!isCancelled) {
                            val target = currentFraction
                            pendingSeekFraction = target
                            currentOnSeek(target)
                        } else {
                            pendingSeekFraction = null
                        }
                    } finally {
                        isScrubbing = false
                        currentOnScrub?.invoke(null)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val strokePx = trackHeight.toPx()
            val gapPx = gapWidth.toPx()
            val wavelengthPx = 28.dp.toPx()
            val ampPx = animatedAmplitude.dp.toPx()

            val thumbX = (displayedFraction * width).coerceIn(0f, width)
            val bufferX = (bufferedProgress.coerceIn(0f, 1f) * width).coerceIn(0f, width)

            val trackStroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

            segments.forEachIndexed { index, segment ->
                val isFirst = index == 0
                val isLast = index == segments.lastIndex

                val segStartX = segment.startFraction * width + if (isFirst) 0f else gapPx / 2f
                val segEndX = segment.endFraction * width - if (isLast) 0f else gapPx / 2f

                if (segEndX <= segStartX) return@forEachIndexed

                // 1. INACTIVE TRACK: only drawn in the unplayed region (thumbX to segEndX)
                if (thumbX < segEndX) {
                    val inactStart = thumbX.coerceAtLeast(segStartX)
                    if (segEndX > inactStart) {
                        drawLine(
                            color = inactiveTrackColor,
                            start = Offset(inactStart, centerY),
                            end = Offset(segEndX, centerY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 2. BUFFERED TRACK: drawn in the region (thumbX to bufferX) within this segment
                if (bufferX > thumbX && bufferX > segStartX && thumbX < segEndX) {
                    val bufStart = thumbX.coerceAtLeast(segStartX)
                    val bufEnd = bufferX.coerceAtMost(segEndX)
                    if (bufEnd > bufStart) {
                        drawLine(
                            color = bufferedColor,
                            start = Offset(bufStart, centerY),
                            end = Offset(bufEnd, centerY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 3. ACTIVE TRACK: drawn in the played region (segStartX to min(thumbX, segEndX))
                if (thumbX > segStartX) {
                    val activeEnd = thumbX.coerceAtMost(segEndX)
                    if (activeEnd > segStartX) {
                        // Wave amplitude ramps up smoothly after passing initial distance threshold (1.4 wavelengths)
                        val minWaveDistance = wavelengthPx * 1.4f
                        val waveStartRamp = (thumbX / minWaveDistance).coerceIn(0f, 1f)
                        val effectiveAmpPx = ampPx * waveStartRamp

                        if (effectiveAmpPx > 0.4f) {
                            // Continuous Wavy Path with global phase alignment
                            val wavePath = Path()
                            val stepPx = 2f
                            var x = segStartX
                            val startWaveOffset = (x / wavelengthPx) * 2f * PI.toFloat()
                            wavePath.moveTo(x, centerY + sin(startWaveOffset + phase) * effectiveAmpPx)

                            x += stepPx
                            while (x <= activeEnd) {
                                val waveOffset = (x / wavelengthPx) * 2f * PI.toFloat()
                                val y = centerY + sin(waveOffset + phase) * effectiveAmpPx
                                wavePath.lineTo(x, y)
                                x += stepPx
                            }
                            // Connect to activeEnd
                            val endOffset = (activeEnd / wavelengthPx) * 2f * PI.toFloat()
                            wavePath.lineTo(activeEnd, centerY + sin(endOffset + phase) * effectiveAmpPx)

                            drawPath(
                                path = wavePath,
                                color = activeColor,
                                style = trackStroke
                            )
                        } else {
                            // Flat active track (when paused, scrubbing, or before distance threshold)
                            drawLine(
                                color = activeColor,
                                start = Offset(segStartX, centerY),
                                end = Offset(activeEnd, centerY),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // 4. THUMB / HANDLE (Rounded Rectangular Pill with Size Morphing)
            val halfThumbW = animatedThumbWidth / 2f
            val halfThumbH = animatedThumbHeight / 2f
            val clampedThumbX = thumbX.coerceIn(halfThumbW, width - halfThumbW)
            val cornerRadius = CornerRadius(halfThumbW, halfThumbW)

            // Outer pill halo when scrubbing
            if (isScrubbing) {
                val haloExtra = 5.dp.toPx()
                val haloW = animatedThumbWidth + haloExtra * 2f
                val haloH = animatedThumbHeight + haloExtra * 2f
                drawRoundRect(
                    color = thumbColor.copy(alpha = 0.25f),
                    topLeft = Offset(clampedThumbX - haloW / 2f, centerY - haloH / 2f),
                    size = Size(haloW, haloH),
                    cornerRadius = CornerRadius(haloW / 2f, haloW / 2f)
                )
            }

            // Solid pill handle
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(clampedThumbX - halfThumbW, centerY - halfThumbH),
                size = Size(animatedThumbWidth, animatedThumbHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}

/** Represents a single visual chapter segment normalized between 0f and 1f. */
data class ChapterSegment(
    val startFraction: Float,
    val endFraction: Float
)

private fun calculateChapterSegments(chapters: List<VideoChapter>, durationMs: Long): List<ChapterSegment> {
    if (chapters.size <= 1 || durationMs <= 0L) {
        return listOf(ChapterSegment(0f, 1f))
    }

    val sorted = chapters
        .filter { it.startMs in 0 until durationMs }
        .sortedBy { it.startMs }

    if (sorted.isEmpty()) {
        return listOf(ChapterSegment(0f, 1f))
    }

    val segments = mutableListOf<ChapterSegment>()
    for (i in sorted.indices) {
        val startFrac = (sorted[i].startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val endFrac = if (i < sorted.lastIndex) {
            (sorted[i + 1].startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }
        if (endFrac > startFrac) {
            segments.add(ChapterSegment(startFrac, endFrac))
        }
    }

    return if (segments.isEmpty()) listOf(ChapterSegment(0f, 1f)) else segments
}

private fun getChapterIndex(segments: List<ChapterSegment>, fraction: Float): Int {
    for (i in segments.indices) {
        if (fraction >= segments[i].startFraction && fraction <= segments[i].endFraction) {
            return i
        }
    }
    return -1
}
