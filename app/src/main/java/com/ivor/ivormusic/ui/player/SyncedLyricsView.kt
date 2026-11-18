package com.ivor.ivormusic.ui.player

import android.database.ContentObserver
import android.icu.text.BreakIterator
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import com.ivor.ivormusic.data.LrcContentSpan
import com.ivor.ivormusic.data.LrcLine
import com.ivor.ivormusic.data.LyricVoice
import com.ivor.ivormusic.data.LyricsCustomization
import com.ivor.ivormusic.data.LyricsFont
import com.ivor.ivormusic.data.LyricsHighlight
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.LyricsScroll
import com.ivor.ivormusic.data.LyricsSyncType
import com.ivor.ivormusic.ui.theme.InterFamily
import com.ivor.ivormusic.ui.theme.LoraFamily
import com.ivor.ivormusic.ui.theme.MontserratFamily
import com.ivor.ivormusic.ui.theme.NunitoFamily
import com.ivor.ivormusic.util.lyricsFitDisplayFont
import kotlinx.coroutines.isActive

/**
 * What a long press on a lyric line does, given the line's index in the
 * lyrics: open the share screen with that line picked. Provided once by
 * [ExpandablePlayer] above every player style, so no style has to be wired to
 * it; where nothing provides it (Standby) a long press does nothing.
 */
internal val LocalLyricLongPress = staticCompositionLocalOf<((Int) -> Unit)?> { null }

internal fun lyricLineStyle(
    look: LyricsCustomization,
    base: TextStyle,
    fitsDisplayFont: Boolean = true,
    scale: Float = 1f
): TextStyle {
    val family = if (!fitsDisplayFont) FontFamily.SansSerif else when (look.font) {
        LyricsFont.INTER -> InterFamily
        LyricsFont.MONTSERRAT -> MontserratFamily
        LyricsFont.SYSTEM -> FontFamily.SansSerif
        LyricsFont.ROUNDED -> NunitoFamily
        LyricsFont.SERIF -> LoraFamily
    }
    val size = look.size.sp * scale
    return base.copy(
        fontFamily = family,
        fontWeight = FontWeight(look.weight.value),
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        letterSpacing = if (fitsDisplayFont && look.font == LyricsFont.INTER) (-0.02f).em else 0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )
}

/**
 * Synced Lyrics View - Material 3 Expressive
 * 
 * Features:
 * - Auto-scrolling to current lyric line
 * - Highlighted current line with distinct styling
 * - Spring animations for smooth transitions
 * - Tap to seek functionality
 * - Lines fade out toward the top/bottom edges via an alpha mask, so the
 *   effect works over any background (flat field or ambient mist) without
 *   painting assumed background colors on top of it
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncedLyricsView(
    lyricsResult: LyricsResult,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onSurfaceColor: Color = MaterialTheme.colorScheme.onSurface,
    onSurfaceVariantColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    /**
     * The side margin inside the lyrics, or null for the default, which
     * narrows on small widths. A host that already lines the lyrics up with
     * its own text (Standby, under the title) passes zero so left-aligned
     * lines start on the same edge as the title.
     */
    horizontalGutter: androidx.compose.ui.unit.Dp? = null,
    /**
     * Where the sung line rests, as a fraction of the height from the top.
     * A third of the way down by default, as Apple Music holds it: the
     * centre left half the panel empty above the first line, and put more of
     * what has been sung on screen than of what is coming.
     */
    anchorFraction: Float? = null,
    /**
     * Move to the next line as a cascade, each line below the sung one
     * following a beat after the one above. False is the plain scroll, kept
     * for Standby's small panel, which was tuned around it.
     */
    cascadeScroll: Boolean = true,
    /**
     * False for a screen that only shows the words (Standby): no tap to seek,
     * no scrolling by hand, so a swipe or a hold over the lyrics reaches the
     * screen behind them. The lyrics still follow the song.
     */
    interactive: Boolean = true
) {
    val lyricsMotionEnabled = rememberLyricsMotionEnabled()
    // Read here rather than passed in, so every player style and Standby
    // follow the one setting without each having to be wired to it.
    val context = LocalContext.current
    val prefs = remember(context) { com.ivor.ivormusic.data.ThemePreferences(context) }
    val centered by prefs.lyricsCentered.collectAsState()
    val look by prefs.lyricsLook.collectAsState()
    val interpolate = lyricsResult is LyricsResult.Success &&
        (lyricsResult.syncType == LyricsSyncType.WORD ||
            (lyricsResult.syncType == LyricsSyncType.LINE && look.interludeDots && lyricsMotionEnabled))
    var renderedPositionMs by remember { mutableLongStateOf(currentPositionMs) }

    // Fill timed words and instrumental dots smoothly between progress samples.
    LaunchedEffect(currentPositionMs, isPlaying, interpolate) {
        renderedPositionMs = currentPositionMs
        if (!isPlaying || !interpolate) return@LaunchedEffect

        val anchorPositionMs = currentPositionMs
        val anchorFrameNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000L)
                    .coerceIn(0L, 1_250L)
                renderedPositionMs = anchorPositionMs + elapsedMs
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (lyricsResult) {
            is LyricsResult.Loading -> {
                LoadingState(primaryColor)
            }
            is LyricsResult.NotFound -> {
                NoLyricsState(onSurfaceVariantColor)
            }
            is LyricsResult.Error -> {
                ErrorState(lyricsResult.message, onSurfaceVariantColor)
            }
            is LyricsResult.Success -> {
                if (lyricsResult.lines.isEmpty()) {
                    NoLyricsState(onSurfaceVariantColor)
                } else {
                    LyricsContent(
                        lines = lyricsResult.lines,
                        syncType = lyricsResult.syncType,
                        currentPositionMs = renderedPositionMs,
                        onSeekTo = onSeekTo,
                        primaryColor = primaryColor,
                        onSurfaceColor = onSurfaceColor,
                        motionEnabled = lyricsMotionEnabled,
                        isPlaying = isPlaying,
                        centered = centered,
                        horizontalGutter = horizontalGutter,
                        anchorFraction = anchorFraction ?: look.rest.fraction,
                        look = look,
                        cascadeScroll = cascadeScroll,
                        interactive = interactive
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lines: List<LrcLine>,
    syncType: LyricsSyncType,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    motionEnabled: Boolean,
    isPlaying: Boolean,
    centered: Boolean,
    horizontalGutter: androidx.compose.ui.unit.Dp?,
    anchorFraction: Float,
    look: LyricsCustomization,
    cascadeScroll: Boolean,
    interactive: Boolean
) {
    val listState = rememberLazyListState()
    val scrubbing by listState.interactionSource.collectIsDraggedAsState()
    val onLineLongPress = LocalLyricLongPress.current.takeIf { interactive }
    val longPressHaptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val cascade = remember { LyricCascade() }
    val cascading = cascadeScroll && motionEnabled && look.scroll == LyricsScroll.CASCADE
    // A duet is drawn as a conversation, whatever the alignment setting:
    // centring would throw away who is singing.
    val duet = look.duetSides && remember(lines) { lines.any { it.voice == LyricVoice.DUET } }
    val isSynced = syncType != LyricsSyncType.PLAIN

    // Read here for the reason the alignment is. Off the main thread, and made
    // again only for a new song: building ICU's rules takes a moment.
    val context = LocalContext.current
    val romanize by remember(context) { com.ivor.ivormusic.data.ThemePreferences(context) }
        .lyricsRomanized.collectAsState()
    val romanizedLines by produceState<List<String?>>(emptyList(), lines, romanize) {
        // Cleared first: the last song's readings must not sit under this one's.
        value = emptyList()
        if (romanize) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                com.ivor.ivormusic.data.LyricsRomanizer.romanize(lines.map { it.text })
            }
        }
    }

    val fitsDisplayFont = remember(lines) { lyricsFitDisplayFont(lines.map { it.text }) }

    // Calculate current line index based on playback position. -1 while the
    // intro plays, so the first line isn't falsely highlighted before its
    // timestamp is reached.
    val currentLineIndex by remember(currentPositionMs, lines, isSynced) {
        derivedStateOf {
            if (isSynced) lines.indexOfLast { it.timeMs <= currentPositionMs } else -1
        }
    }

    val interlude = if (isSynced && look.interludeDots) {
        lyricInterlude(lines, currentLineIndex, currentPositionMs)
    } else {
        null
    }

    // Auto-scroll the sung line to the anchor, using the item's measured
    // height. A fixed scroll offset is wrong as soon as a line wraps or font
    // scale grows.
    LaunchedEffect(currentLineIndex, lines, anchorFraction, look.size, look.spacing, look.scroll, cascading) {
        if (isSynced && currentLineIndex >= 0 && lines.isNotEmpty()) {
            try {
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == currentLineIndex }) {
                    listState.scrollToItem(currentLineIndex)
                    withFrameNanos { }
                }
                val layout = listState.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.index == currentLineIndex }
                if (item != null) {
                    val anchor = layout.viewportStartOffset +
                        ((layout.viewportEndOffset - layout.viewportStartOffset) * anchorFraction).toInt()
                    val itemCenter = item.offset + item.size / 2
                    val delta = (itemCenter - anchor).toFloat()
                    if (cascading) {
                        // The list jumps at once and every line is drawn back
                        // where it was, then each is let go in turn: the lines
                        // move on their own clocks while the list itself is
                        // already where it is going.
                        //
                        // [scar] The hold-back is set before the jump, in the
                        // same frame. Set after it (on the next frame's clock),
                        // one frame drew every line already jumped, then the
                        // next pulled them all back: a flash of the whole
                        // lyrics on each line change. System.nanoTime is the
                        // frame clock's own timebase on Android.
                        cascade.kick(delta, currentLineIndex, System.nanoTime())
                        val moved = listState.scrollBy(delta)
                        // The end of the list can stop the jump short.
                        if (moved != delta) cascade.correctLast(moved)
                    } else if (!motionEnabled || look.scroll == LyricsScroll.INSTANT) {
                        listState.scrollBy(delta)
                    } else {
                        listState.animateScrollBy(delta)
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A rapid song/line replacement can invalidate a measured row.
            }
        }
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // UI scale and system font scale both reduce the available dp width.
        // Shrink the gutter before shrinking the lyric itself.
        val horizontalPadding = horizontalGutter ?: when {
            maxWidth < 280.dp -> 12.dp
            maxWidth < 360.dp -> 16.dp
            else -> 24.dp
        }
        // Room above the first line and below the last, so either can reach
        // the anchor. At an anchor of one half these are the centring pads the
        // view always had.
        val topPadding = (maxHeight * anchorFraction - 36.dp).coerceAtLeast(12.dp)
        val bottomPadding = (maxHeight * (1f - anchorFraction) - 36.dp).coerceAtLeast(12.dp)
        val lineSpacing = look.spacing.dp.dp * if (maxHeight < 420.dp) 0.7f else 1f
        val typeScale = ((maxWidth - horizontalPadding * 2) / 312.dp).coerceIn(0.75f, 1f)
        val textStyle = lyricLineStyle(look, MaterialTheme.typography.headlineSmall, fitsDisplayFont, typeScale)
        val sungColor = if (look.highlight == LyricsHighlight.ACCENT) primaryColor else onSurfaceColor
        val glow = look.glow && sungColor.luminance() > 0.5f
        // Each side of a duet stops short of the other, so a long line that
        // wraps still reads as belonging to its side.
        val duetInset = if (duet) maxWidth * DUET_INSET_FRACTION else 0.dp

        // Drives the cascade: one frame clock for every line, running only
        // while a line change is still settling.
        LaunchedEffect(cascade.generation) {
            while (cascade.isMoving(cascade.frameNanos)) {
                withFrameNanos { cascade.frameNanos = it }
            }
            cascade.settle()
        }
        
        LazyColumn(
            state = listState,
            // Display only: the list still scrolls itself to the sung line.
            userScrollEnabled = interactive,
            modifier = Modifier
                .fillMaxSize()
                // Fade the lyrics themselves out toward the edges instead of
                // painting background-colored scrims over whatever is behind.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.18f to Color.Black,
                            0.82f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = bottomPadding,
                start = horizontalPadding,
                end = horizontalPadding
            ),
            verticalArrangement = Arrangement.spacedBy(lineSpacing),
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
        ) {
            itemsIndexed(lines, key = { index, line -> "${index}_${line.timeMs}" }) { index, line ->
                val gap = interlude?.takeIf { it.afterIndex == index }
                LyricLine(
                    modifier = Modifier.graphicsLayer {
                        translationY = cascade.offsetFor(index, cascade.frameNanos)
                    },
                    line = line,
                    romanized = romanizedLines.getOrNull(index),
                    isCurrent = index == currentLineIndex && gap == null,
                    distance = if (scrubbing) 1 else kotlin.math.abs(index - currentLineIndex),
                    interludeProgress = gap?.progress,
                    isSynced = isSynced,
                    tappable = interactive,
                    onTap = { onSeekTo(line.timeMs) },
                    onLongPress = onLineLongPress?.let { open ->
                        {
                            longPressHaptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            open(index)
                        }
                    },
                    primaryColor = sungColor,
                    onSurfaceColor = onSurfaceColor,
                    motionEnabled = motionEnabled,
                    currentPositionMs = currentPositionMs,
                    dotsMoving = isPlaying && motionEnabled,
                    look = look,
                    glowEnabled = glow,
                    textStyle = textStyle,
                    side = lineSide(line, duet, centered),
                    duetInset = duetInset
                )
            }
        }
        interlude?.takeIf { it.afterIndex == -1 }?.let { intro ->
            InterludeDots(
                progress = intro.progress,
                color = sungColor,
                pulseTimeMs = currentPositionMs.takeIf { isPlaying && motionEnabled },
                modifier = Modifier
                    .align(if (centered && !duet) Alignment.TopCenter else Alignment.TopStart)
                    .padding(start = if (centered && !duet) 0.dp else horizontalPadding)
                    .offset(y = (topPadding - 28.dp).coerceAtLeast(0.dp))
            )
        }
    }
}

private data class LyricInterlude(val afterIndex: Int, val progress: Float)

private fun lyricInterlude(lines: List<LrcLine>, current: Int, positionMs: Long): LyricInterlude? {
    val next = lines.getOrNull(current + 1)?.timeMs ?: return null
    val end = if (current < 0) 0L else {
        val line = lines[current]
        if (line.text.trim().lowercase() in instrumentalMarkers) line.timeMs
        else {
            val lastSpan = line.contentSpans.maxByOrNull { it.timeMs } ?: return null
            if (lastSpan.durationMs <= 0L) return null
            line.contentSpans.maxOf { it.timeMs + it.durationMs.coerceAtLeast(0L) }
        }
    }
    val duration = next - end
    // Without a known end time, a held note can look like an instrumental.
    val settleMs = if (current < 0) 0L else 300L
    if (duration < 4_000L || positionMs < end + settleMs) return null
    return LyricInterlude(current, ((positionMs - end).toFloat() / duration).coerceIn(0f, 1f))
}

private val instrumentalMarkers = setOf("", "♪", "♫", "…", "...", "(instrumental)", "[instrumental]")

@Composable
private fun InterludeDots(
    progress: Float,
    color: Color,
    pulseTimeMs: Long?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(width = 34.dp, height = 16.dp)) {
        repeat(3) { index ->
            val fill = (progress * 3f - index).coerceIn(0f, 1f)
            val phase = pulseTimeMs?.let { (it % 1_400L) / 1_400f * 2f * Math.PI - index * 0.6 }
            val pulse = phase?.let { (0.5 + 0.5 * kotlin.math.sin(it)).toFloat() } ?: 0f
            drawCircle(
                color = color.copy(alpha = 0.32f + 0.68f * fill + 0.16f * pulse * (1f - fill)),
                radius = 3.dp.toPx(),
                center = Offset((5 + 12 * index).dp.toPx(), center.y)
            )
        }
    }
}

/** How long a word-timed line takes to settle from its sung colours into a resting line. */
private const val WORD_LINE_DISSOLVE_MS = 320

/** The share of the width each side of a duet leaves to the other. */
private const val DUET_INSET_FRACTION = 0.12f

/** The edge a lyric line is set from. Start and end are mirrored in RTL. */
internal enum class LyricSide(
    val boxAlignment: Alignment,
    val columnAlignment: Alignment.Horizontal,
    val textAlign: TextAlign,
) {
    START(Alignment.CenterStart, Alignment.Start, TextAlign.Start),
    CENTER(Alignment.Center, Alignment.CenterHorizontally, TextAlign.Center),
    END(Alignment.CenterEnd, Alignment.End, TextAlign.End),
}

/**
 * Where [line] sits. In a duet the second singer answers from the end
 * edge and lines sung together stay with the first; otherwise the
 * alignment setting decides.
 */
private fun lineSide(line: LrcLine, duet: Boolean, centered: Boolean): LyricSide = when {
    duet -> if (line.voice == LyricVoice.DUET) LyricSide.END else LyricSide.START
    centered -> LyricSide.CENTER
    else -> LyricSide.START
}

/**
 * Apple Music's line change, as a shared timeline rather than an animation per
 * row.
 *
 * Each change ("kick") records how far the list jumped and which line is now
 * sung. Every row is drawn displaced by that distance and released along a
 * critically damped spring - it starts from rest and settles without
 * overshoot, which is the ease-in-out a hand-picked curve only approximates -
 * the rows below the sung line each a beat later than the one above, so the
 * text ripples down into place instead of sliding as one slab. Rows that
 * scroll into view mid-change read the same timeline, so they arrive in step
 * with their neighbours. Overlapping changes add up, so a quick run of short
 * lines never jumps.
 *
 * All of it is read in the draw phase through [frameNanos]: the lines move
 * without recomposing.
 */
private class LyricCascade {
    private class Kick(var distance: Float, val pivot: Int, val startNanos: Long)

    private val kicks = mutableListOf<Kick>()

    /** Bumped on each kick, to restart the frame loop. */
    var generation by mutableIntStateOf(0)
        private set

    var frameNanos by mutableLongStateOf(0L)

    fun kick(distance: Float, pivot: Int, nowNanos: Long) {
        kicks += Kick(distance, pivot, nowNanos)
        frameNanos = nowNanos
        generation++
    }

    /** The last jump went [actual] rather than the distance it was kicked with. */
    fun correctLast(actual: Float) {
        val last = kicks.lastOrNull() ?: return
        if (actual == 0f) kicks.removeAt(kicks.lastIndex) else last.distance = actual
        // A fresh read for the layers, which drew the old distance.
        frameNanos = frameNanos + 1
    }

    fun isMoving(nowNanos: Long): Boolean =
        kicks.any { nowNanos - it.startNanos < CASCADE_TOTAL_NANOS }

    fun settle() {
        kicks.clear()
    }

    fun offsetFor(index: Int, nowNanos: Long): Float {
        var offset = 0f
        for (kick in kicks) {
            // Lines above the sung one go first, together; each line from
            // the sung one down waits one beat more than the line above it.
            val beats = (index - kick.pivot + 1).coerceIn(0, CASCADE_MAX_BEATS)
            val t = (nowNanos - kick.startNanos) / 1_000_000_000f - beats * CASCADE_BEAT_S
            offset += kick.distance * (1f - criticallyDampedProgress(t))
        }
        return offset
    }
}

/**
 * The position of a unit-mass, critically damped spring released from rest:
 * 1 - (1 + wt)e^(-wt), with w set by Apple's "response" (the spring's period).
 * Zero velocity at the start and at the end, so it eases in and out.
 */
private fun criticallyDampedProgress(seconds: Float): Float {
    if (seconds <= 0f) return 0f
    val wt = CASCADE_OMEGA * seconds
    return (1f - (1f + wt) * kotlin.math.exp(-wt)).coerceIn(0f, 1f)
}

/** How quickly a line gets there: Apple's response, in seconds. */
private const val CASCADE_RESPONSE_S = 0.55f
private const val CASCADE_OMEGA = (2f * Math.PI.toFloat()) / CASCADE_RESPONSE_S

/** The wait between one line and the next, and how many lines are staggered. */
private const val CASCADE_BEAT_S = 0.045f
private const val CASCADE_MAX_BEATS = 8

/** Long enough for the last staggered line to settle to well under a pixel. */
private const val CASCADE_TOTAL_NANOS = 1_600_000_000L

@Composable
private fun LyricLine(
    modifier: Modifier = Modifier,
    line: LrcLine,
    /** The line in Latin letters, drawn under it; null where there is none. */
    romanized: String? = null,
    isCurrent: Boolean,
    isSynced: Boolean,
    distance: Int,
    interludeProgress: Float?,
    /** False on a display-only surface: the line takes no touch at all. */
    tappable: Boolean = true,
    onTap: () -> Unit,
    /** Opens the share screen on this line; null where sharing is not offered. */
    onLongPress: (() -> Unit)? = null,
    primaryColor: Color,
    onSurfaceColor: Color,
    motionEnabled: Boolean,
    currentPositionMs: Long = 0L,
    dotsMoving: Boolean,
    look: LyricsCustomization,
    glowEnabled: Boolean,
    textStyle: TextStyle,
    /** Which edge the line is set from; see [lineSide]. */
    side: LyricSide = LyricSide.START,
    /** Room kept free on the side away from this line's singer, in a duet. */
    duetInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    val emphasized = !isSynced || isCurrent
    val lineAlpha by animateFloatAsState(
        targetValue = when {
            emphasized -> 1f
            distance <= 1 -> look.dim.near
            else -> look.dim.far
        },
        animationSpec = if (motionEnabled) spring(stiffness = Spring.StiffnessLow) else snap(),
        label = "LyricAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (look.depthScale && !emphasized) 0.96f else 1f,
        animationSpec = if (motionEnabled) spring(stiffness = Spring.StiffnessLow) else snap(),
        label = "LyricScale"
    )
    val displayText = remember(line.text, look.uppercase) {
        if (look.uppercase) line.text.uppercase() else line.text
    }
    val blur = if (look.blur && !emphasized && android.os.Build.VERSION.SDK_INT >= 31) {
        ((distance - 1) * 0.6f).coerceIn(0f, 1.8f).dp
    } else 0.dp
    val blurPx = with(LocalDensity.current) { blur.toPx() }
    val blurEffect = remember(blurPx) {
        if (blurPx > 0f) BlurEffect(blurPx, blurPx, TileMode.Decal) else null
    }
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val pivot = when (side) {
        LyricSide.START -> if (rtl) 1f else 0f
        LyricSide.CENTER -> 0.5f
        LyricSide.END -> if (rtl) 0f else 1f
    }

    // Current and inactive lines share one style so a line wraps identically
    // either way; only colour and glow change, and both fade. A size or
    // weight change re-wrapped the line the moment it became current, jumping
    // from one row to two.
    //
    // A word-timed line is never highlighted as a whole: its words light up
    // in the karaoke view, and it dissolves from that straight into the
    // resting line (below). [scar] It used to hand over to this plain text
    // still wearing the sung colour and a full-line glow, to fade from the
    // sung colour rather than snap - which lit the entire line, glow and all,
    // the moment it ended, words the singer had not reached included.
    val wordTimed = line.contentSpans.isNotEmpty()
    val highlighted = isSynced && isCurrent && !wordTimed
    val lineColor by animateColorAsState(
        targetValue = if (highlighted) primaryColor else onSurfaceColor,
        animationSpec = if (motionEnabled) spring(stiffness = Spring.StiffnessLow) else snap(),
        label = "LyricColor"
    )
    val glow by animateFloatAsState(
        targetValue = if (highlighted && glowEnabled) 1f else 0f,
        animationSpec = if (motionEnabled) spring(stiffness = Spring.StiffnessLow) else snap(),
        label = "LyricGlow"
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // A tap seeks (synced lyrics only); a long press shares, plain lyrics too.
    val tapModifier = when {
        onLongPress != null -> Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { if (isSynced) onTap() },
            onLongClick = onLongPress
        )
        isSynced && tappable -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onTap
        )
        else -> Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(tapModifier)
            .padding(vertical = 8.dp)
            .padding(
                start = if (side == LyricSide.END) duetInset else 0.dp,
                end = if (side == LyricSide.START) duetInset else 0.dp
            ),
        contentAlignment = side.boxAlignment
    ) {
        Column(horizontalAlignment = side.columnAlignment) {
            Column(
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    alpha = lineAlpha.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(pivot, 0.5f)
                    renderEffect = blurEffect
                },
                horizontalAlignment = side.columnAlignment
            ) {
                if (wordTimed) {
                    // The two views wrap a line identically (see KaraokeWordFlow), so
                    // a crossfade between them reads as the colour settling, not as
                    // one line replaced by another.
                    androidx.compose.animation.Crossfade(
                        targetState = isCurrent,
                        animationSpec = if (motionEnabled) {
                            tween(WORD_LINE_DISSOLVE_MS, easing = FastOutSlowInEasing)
                        } else snap(),
                        modifier = Modifier.fillMaxWidth(),
                        label = "WordLineDissolve"
                    ) { singing ->
                        if (singing) {
                            KaraokeWordFlow(
                                spans = line.contentSpans,
                                lineText = displayText,
                                currentPositionMs = currentPositionMs,
                                primaryColor = primaryColor,
                                unsungColor = onSurfaceColor.copy(alpha = 0.5f),
                                motionEnabled = motionEnabled && look.wordMotion,
                                glowEnabled = glowEnabled,
                                textStyle = textStyle,
                                side = side,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = displayText,
                                style = textStyle,
                                color = onSurfaceColor,
                                textAlign = side.textAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // Line-synced lyrics highlight the whole line - no invented
                    // per-word fill.
                    Text(
                        text = displayText,
                        style = textStyle.copy(
                            shadow = if (glow > 0.01f) {
                                Shadow(
                                    color = primaryColor.copy(alpha = 0.5f * glow),
                                    blurRadius = 24f * glow
                                )
                            } else {
                                null
                            }
                        ),
                        color = lineColor,
                        textAlign = side.textAlign
                    )
                }
                // Stays still under the line: the singing is followed in the line
                // itself, and word timings describe that script, not this one.
                if (romanized != null) {
                    Text(
                        text = romanized,
                        style = MaterialTheme.typography.titleMedium,
                        color = onSurfaceColor.copy(alpha = 0.7f),
                        textAlign = side.textAlign,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            interludeProgress?.let {
                InterludeDots(
                    progress = it,
                    color = primaryColor,
                    pulseTimeMs = currentPositionMs.takeIf { dotsMoving },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * The letter wave. Each letter starts rising as it is sung and takes far longer
 * to settle than the gap to the next letter, so neighbours are always mid-rise
 * together and the word swells as one slow ripple instead of letters popping
 * one at a time. The rise is small and eased at both ends on purpose: the old
 * 6dp, 220ms pop with a per-letter swell read as letters jumping around.
 */
private const val LETTER_RISE_MS = 650L
private val LETTER_LIFT = 3.dp
private val LetterRiseEasing = CubicBezierEasing(0.37f, 0f, 0.2f, 1f)

/**
 * Held notes only: a word sung over at least this long swells gently and
 * glows while it lasts, reaching full strength by [SUSTAIN_FULL_MS].
 */
private const val SUSTAIN_START_MS = 900L
private const val SUSTAIN_FULL_MS = 1_800L
private const val SUSTAIN_SCALE = 0.035f

private data class TimedLyricGrapheme(
    val text: String,
    val startMs: Long,
    val durationMs: Long
) {
    val endMs: Long
        get() = startMs + durationMs
}

private data class TimedLyricToken(
    val graphemes: List<TimedLyricGrapheme>,
    val spaceBefore: Boolean
) {
    val startMs: Long
        get() = graphemes.minOfOrNull(TimedLyricGrapheme::startMs) ?: 0L

    val endMs: Long
        get() = graphemes.maxOfOrNull(TimedLyricGrapheme::endMs) ?: startMs
}

private data class MeasuredLyricGrapheme(
    val timing: TimedLyricGrapheme,
    val textLayout: TextLayoutResult,
    val position: Offset
)

private data class MeasuredLyricWord(
    val widthPx: Int,
    val heightPx: Int,
    val graphemes: List<MeasuredLyricGrapheme>
)

@Composable
private fun KaraokeWordFlow(
    spans: List<LrcContentSpan>,
    lineText: String,
    currentPositionMs: Long,
    primaryColor: Color,
    unsungColor: Color,
    motionEnabled: Boolean,
    glowEnabled: Boolean,
    textStyle: TextStyle,
    side: LyricSide = LyricSide.START,
    modifier: Modifier = Modifier
) {
    val tokens = remember(spans, lineText) { buildTimedLyricTokens(spans, lineText) }
    val currentPositionState = rememberUpdatedState(currentPositionMs)
    val currentPositionProvider = remember {
        { currentPositionState.value }
    }
    if (tokens.isEmpty()) {
        Text(
            text = lineText,
            style = textStyle,
            color = primaryColor,
            textAlign = side.textAlign
        )
        return
    }

    val density = LocalDensity.current
    // The font's real space advance, so this line wraps where the same line
    // set as plain text does. Measured as the difference between "a a" and
    // "aa", because a lone space can be trimmed as trailing whitespace.
    val spaceMeasurer = rememberTextMeasurer(cacheSize = 2)
    val wordSpacing = remember(textStyle, spaceMeasurer, density) {
        runCatching {
            val spaced = spaceMeasurer.measure("a a", textStyle).size.width
            val joined = spaceMeasurer.measure("aa", textStyle).size.width
            with(density) { (spaced - joined).coerceAtLeast(1).toDp() }
        }.getOrDefault(6.dp)
    }

    BoxWithConstraints(modifier = modifier) {
        val maxWordWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        CenteredLyricFlow(
            tokens = tokens,
            side = side,
            horizontalSpacing = wordSpacing,
            // The style's line height already carries the leading, so rows sit
            // as close as the same line drawn as plain text would.
            verticalSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    text = androidx.compose.ui.text.AnnotatedString(lineText)
                }
        ) { token ->
            KaraokeWord(
                token = token,
                currentPositionProvider = currentPositionProvider,
                primaryColor = primaryColor,
                unsungColor = unsungColor,
                motionEnabled = motionEnabled,
                glowEnabled = glowEnabled,
                textStyle = textStyle,
                maxWidthPx = maxWordWidthPx
            )
        }
    }
}

@Composable
private fun KaraokeWord(
    token: TimedLyricToken,
    currentPositionProvider: () -> Long,
    primaryColor: Color,
    unsungColor: Color,
    motionEnabled: Boolean,
    glowEnabled: Boolean,
    textStyle: TextStyle,
    maxWidthPx: Int
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = token.graphemes.size + 1)
    val liftPx = with(density) { LETTER_LIFT.toPx() }
    val topInsetPx = with(density) { 10.dp.toPx() }
    val sideInsetPx = with(density) { 6.dp.toPx() }
    val measuredWord = remember(
        token,
        textStyle,
        textMeasurer,
        topInsetPx,
        sideInsetPx,
        maxWidthPx
    ) {
        measureLyricWord(
            token = token,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            topInsetPx = topInsetPx,
            sideInsetPx = sideInsetPx,
            maxWidthPx = maxWidthPx
        )
    }
    val canvasWidth = with(density) { measuredWord.widthPx.toDp() }
    val canvasHeight = with(density) { measuredWord.heightPx.toDp() }

    // The reference renderer measures a complete word once, then draws each
    // glyph at its original bound inside one fixed Canvas. Playback therefore
    // invalidates drawing only: kerning, wrapping, and line placement never
    // change while the letter wave moves.
    // The insets are drawing room for the rise, the held-note swell and glyph
    // bearings, not spacing: the word reports only its text's size to the flow
    // and lets the canvas overhang by the insets. Counting them made a short
    // line read as loose, widely spaced words with tall gaps between rows.
    val sideInsetRoundedPx = sideInsetPx.toInt()
    val topInsetRoundedPx = topInsetPx.toInt()
    Canvas(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
                )
                val width = (placeable.width - sideInsetRoundedPx * 2).coerceAtLeast(1)
                val height = (placeable.height - topInsetRoundedPx * 2).coerceAtLeast(1)
                layout(width, height) {
                    placeable.place(-sideInsetRoundedPx, -topInsetRoundedPx)
                }
            }
            .size(width = canvasWidth, height = canvasHeight)
    ) {
        val currentPositionMs = currentPositionProvider()

        // Held notes swell and glow as a whole word, rising and falling over
        // the note itself; short words never do, so ordinary lines stay calm.
        val wordDurationMs = (token.endMs - token.startMs).coerceAtLeast(1L)
        val sustainStrength = if (motionEnabled) {
            ((wordDurationMs - SUSTAIN_START_MS).toFloat() /
                (SUSTAIN_FULL_MS - SUSTAIN_START_MS)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val sustain = if (sustainStrength > 0f) {
            val wordProgress = (
                (currentPositionMs - token.startMs).toFloat() / wordDurationMs
            ).coerceIn(0f, 1f)
            sustainStrength * kotlin.math.sin(Math.PI.toFloat() * wordProgress)
        } else {
            0f
        }
        val wordScale = 1f + SUSTAIN_SCALE * sustain
        val wordPivot = Offset(size.width / 2f, size.height - topInsetPx)
        val glowShadow = if (glowEnabled && sustain > 0.01f) {
            Shadow(
                color = primaryColor.copy(alpha = 0.35f * sustain),
                blurRadius = 16f * sustain
            )
        } else {
            null
        }

        withTransform({ scale(wordScale, wordScale, pivot = wordPivot) }) {
            measuredWord.graphemes.forEach { glyph ->
                val grapheme = glyph.timing
                val highlightProgress = (
                    (currentPositionMs - grapheme.startMs).toFloat() / grapheme.durationMs
                ).coerceIn(0f, 1f)
                // Rises from the letter's own start but settles long after the
                // next letter has begun, so the word moves as one ripple.
                val riseProgress = (
                    (currentPositionMs - grapheme.startMs).toFloat() / LETTER_RISE_MS
                ).coerceIn(0f, 1f)
                val lift = if (motionEnabled) LetterRiseEasing.transform(riseProgress) else 0f
                val drawPosition = glyph.position.copy(y = glyph.position.y - liftPx * lift)

                // The sung colour fades in over the unsung one rather than
                // blending into it. The unsung colour is translucent, so a blend
                // (or a gradient edge between the two) passes through a paler
                // shade than either end, which read as a light band flickering
                // across each letter just before it lit up.
                if (highlightProgress < 1f) {
                    drawText(
                        textLayoutResult = glyph.textLayout,
                        color = unsungColor,
                        topLeft = drawPosition,
                        shadow = if (highlightProgress <= 0f) glowShadow else null
                    )
                }
                if (highlightProgress > 0f) {
                    drawText(
                        textLayoutResult = glyph.textLayout,
                        color = primaryColor,
                        topLeft = drawPosition,
                        alpha = highlightProgress,
                        shadow = glowShadow
                    )
                }
            }
        }
    }
}

private fun measureLyricWord(
    token: TimedLyricToken,
    textStyle: androidx.compose.ui.text.TextStyle,
    textMeasurer: TextMeasurer,
    topInsetPx: Float,
    sideInsetPx: Float,
    maxWidthPx: Int
): MeasuredLyricWord {
    val wordText = token.graphemes.joinToString(separator = "") { it.text }
    val horizontalInsets = (sideInsetPx * 2f).toInt()
    val wordLayout = textMeasurer.measure(
        text = wordText,
        style = textStyle.copy(textAlign = TextAlign.Center),
        softWrap = true,
        constraints = Constraints(
            maxWidth = (maxWidthPx - horizontalInsets).coerceAtLeast(1)
        )
    )
    var textOffset = 0
    val measuredGraphemes = token.graphemes.map { grapheme ->
        val graphemeLayout = textMeasurer.measure(
            text = grapheme.text,
            style = textStyle,
            maxLines = 1,
            softWrap = false
        )
        val endOffset = (textOffset + grapheme.text.length).coerceAtMost(wordText.length)
        val boxes = (textOffset until endOffset).mapNotNull { offset ->
            runCatching { wordLayout.getBoundingBox(offset) }.getOrNull()
        }
        val left = boxes.minOfOrNull { it.left } ?: 0f
        val right = boxes.maxOfOrNull { it.right }
            ?: (left + graphemeLayout.size.width)
        val top = boxes.minOfOrNull { it.top } ?: 0f
        val bottom = boxes.maxOfOrNull { it.bottom }
            ?: (top + graphemeLayout.size.height)
        val originalWidth = (right - left).coerceAtLeast(0f)
        val originalHeight = (bottom - top).coerceAtLeast(0f)
        val x = sideInsetPx + left + (originalWidth - graphemeLayout.size.width) / 2f
        val y = topInsetPx + top + (originalHeight - graphemeLayout.size.height) / 2f
        val position = Offset(x = x, y = y)
        textOffset = endOffset
        MeasuredLyricGrapheme(
            timing = grapheme,
            textLayout = graphemeLayout,
            position = position
        )
    }

    return MeasuredLyricWord(
        widthPx = (wordLayout.size.width + horizontalInsets).coerceIn(1, maxWidthPx),
        heightPx = ((wordLayout.size.height + topInsetPx * 2f).toInt() + 1).coerceAtLeast(1),
        graphemes = measuredGraphemes
    )
}

private data class FlowPlacement(
    val index: Int,
    val gapBeforePx: Int
)

private data class FlowLine(
    val placements: List<FlowPlacement>,
    val width: Int,
    val height: Int
)

@Composable
private fun CenteredLyricFlow(
    tokens: List<TimedLyricToken>,
    /** Which edge each row is set from; RTL is mirrored by placeRelative. */
    side: LyricSide,
    horizontalSpacing: androidx.compose.ui.unit.Dp,
    verticalSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable (TimedLyricToken) -> Unit
) {
    Layout(
        modifier = modifier,
        content = {
            tokens.forEachIndexed { index, token ->
                key(index, token.startMs, token.endMs) { content(token) }
            }
        }
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val horizontalSpacingPx = horizontalSpacing.roundToPx()
        val verticalSpacingPx = verticalSpacing.roundToPx()
        val availableWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            placeables.sumOf { it.width } + horizontalSpacingPx * (placeables.size - 1).coerceAtLeast(0)
        }

        val lines = mutableListOf<FlowLine>()
        var currentPlacements = mutableListOf<FlowPlacement>()
        var currentWidth = 0
        var currentHeight = 0

        fun commitLine() {
            if (currentPlacements.isEmpty()) return
            lines += FlowLine(currentPlacements.toList(), currentWidth, currentHeight)
            currentPlacements = mutableListOf()
            currentWidth = 0
            currentHeight = 0
        }

        placeables.forEachIndexed { index, placeable ->
            var gapBefore = if (currentPlacements.isNotEmpty() && tokens[index].spaceBefore) {
                horizontalSpacingPx
            } else {
                0
            }
            if (currentPlacements.isNotEmpty() && currentWidth + gapBefore + placeable.width > availableWidth) {
                commitLine()
                gapBefore = 0
            }
            currentPlacements += FlowPlacement(index, gapBefore)
            currentWidth += gapBefore + placeable.width
            currentHeight = maxOf(currentHeight, placeable.height)
        }
        commitLine()

        val contentHeight = lines.sumOf(FlowLine::height) +
            verticalSpacingPx * (lines.size - 1).coerceAtLeast(0)
        val layoutWidth = availableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(layoutWidth, layoutHeight) {
            var y = 0
            lines.forEach { line ->
                var x = when (side) {
                    LyricSide.START -> 0
                    LyricSide.CENTER -> ((layoutWidth - line.width) / 2).coerceAtLeast(0)
                    LyricSide.END -> (layoutWidth - line.width).coerceAtLeast(0)
                }
                line.placements.forEach { placement ->
                    val placeable = placeables[placement.index]
                    x += placement.gapBeforePx
                    placeable.placeRelative(
                        x = x,
                        y = y + (line.height - placeable.height) / 2
                    )
                    x += placeable.width
                }
                y += line.height + verticalSpacingPx
            }
        }
    }
}

private fun buildTimedLyricTokens(
    spans: List<LrcContentSpan>,
    lineText: String
): List<TimedLyricToken> {
    val timedGraphemes = mutableListOf<TimedLyricGrapheme>()
    spans.forEach { span ->
        val graphemes = splitIntoGraphemes(span.text)
        val visibleGraphemes = graphemes.filter { it.isNotBlank() }
        val visibleCount = visibleGraphemes.size.coerceAtLeast(1)
        val spanDurationMs = span.durationMs.coerceAtLeast(1L)
        visibleGraphemes.forEachIndexed { index, text ->
            val startMs = span.timeMs + spanDurationMs * index.toLong() / visibleCount.toLong()
            val endMs = span.timeMs + spanDurationMs * (index + 1L) / visibleCount.toLong()
            timedGraphemes += TimedLyricGrapheme(
                text = text,
                startMs = startMs,
                durationMs = (endMs - startMs).coerceAtLeast(1L)
            )
        }
    }
    if (timedGraphemes.isEmpty()) return emptyList()

    // The displayed line is authoritative. Providers occasionally omit final
    // punctuation from timed spans; falling back to span text dropped the last
    // visible characters entirely. When counts differ, map the available
    // timings proportionally so every grapheme remains present and highlighted.
    val layoutGraphemes = splitIntoGraphemes(lineText)
        .takeIf { graphemes -> graphemes.any { it.isNotBlank() } }
        ?: spans.flatMap { splitIntoGraphemes(it.text) }
    val visibleLayoutCount = layoutGraphemes.count { it.isNotBlank() }.coerceAtLeast(1)

    val tokens = mutableListOf<TimedLyricToken>()
    var current = mutableListOf<TimedLyricGrapheme>()
    var currentHasSpaceBefore = false
    var pendingSpace = false
    var timedIndex = 0

    fun commitToken() {
        if (current.isEmpty()) return
        tokens += TimedLyricToken(current.toList(), currentHasSpaceBefore)
        current = mutableListOf()
        currentHasSpaceBefore = false
    }

    layoutGraphemes.forEach { text ->
        if (text.isBlank()) {
            commitToken()
            pendingSpace = true
            return@forEach
        }

        val timingIndex = lyricTimingIndex(
            displayIndex = timedIndex,
            displayCount = visibleLayoutCount,
            timingCount = timedGraphemes.size
        )
        val timing = timedGraphemes[timingIndex]
        timedIndex++
        val grapheme = timing.copy(text = text)
        if (isCjkGrapheme(text)) {
            commitToken()
            tokens += TimedLyricToken(listOf(grapheme), pendingSpace)
            pendingSpace = false
        } else {
            if (current.isEmpty()) currentHasSpaceBefore = pendingSpace
            current += grapheme
            pendingSpace = false
        }
    }
    commitToken()
    return tokens
}

internal fun lyricTimingIndex(
    displayIndex: Int,
    displayCount: Int,
    timingCount: Int
): Int {
    if (displayCount <= 1 || timingCount <= 1) return 0
    return (displayIndex.toLong() * (timingCount - 1) / (displayCount - 1))
        .toInt()
        .coerceIn(0, timingCount - 1)
}

private fun isCjkGrapheme(text: String): Boolean {
    if (text.isEmpty()) return false
    return Character.UnicodeScript.of(text.codePointAt(0)) in setOf(
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL
    )
}

@Composable
private fun rememberLyricsMotionEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(true) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        fun updateMotionPreference() {
            enabled = runCatching {
                Settings.Global.getFloat(
                    resolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) > 0f
            }.getOrDefault(true)
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateMotionPreference()
            }
        }

        updateMotionPreference()
        val registered = runCatching {
            resolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
        }.isSuccess

        onDispose {
            if (registered) runCatching { resolver.unregisterContentObserver(observer) }
        }
    }

    return enabled
}

private fun splitIntoGraphemes(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val graphemes = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        graphemes += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    return graphemes
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(primaryColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(
            modifier = Modifier.size(48.dp),
            color = primaryColor,
            polygons = listOf(
                MaterialShapes.Cookie9Sided,
                MaterialShapes.Pill,
                MaterialShapes.Sunny
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Fetching lyrics...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoLyricsState(onSurfaceVariantColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = onSurfaceVariantColor.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No lyrics available",
            style = MaterialTheme.typography.titleMedium,
            color = onSurfaceVariantColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Lyrics couldn't be found for this track",
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariantColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorState(message: String, onSurfaceVariantColor: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = onSurfaceVariantColor.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't load lyrics",
            style = MaterialTheme.typography.titleMedium,
            color = onSurfaceVariantColor
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = onSurfaceVariantColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
