package com.ivor.ivormusic.ui.player

import android.database.ContentObserver
import android.icu.text.BreakIterator
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.LrcContentSpan
import com.ivor.ivormusic.data.LrcLine
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.LyricsSyncType
import kotlinx.coroutines.isActive

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
    onSurfaceVariantColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val hasWordTiming = lyricsResult is LyricsResult.Success &&
        lyricsResult.syncType == LyricsSyncType.WORD
    val lyricsMotionEnabled = rememberLyricsMotionEnabled()
    var renderedPositionMs by remember { mutableLongStateOf(currentPositionMs) }

    // The player publishes coarse progress updates. Interpolate only while a
    // word-timed lyric is visible so the sung color advances one grapheme at a
    // time without increasing app-wide playback update frequency.
    LaunchedEffect(currentPositionMs, isPlaying, hasWordTiming) {
        renderedPositionMs = currentPositionMs
        if (!isPlaying || !hasWordTiming) return@LaunchedEffect

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
                LyricsContent(
                    lines = lyricsResult.lines,
                    syncType = lyricsResult.syncType,
                    currentPositionMs = renderedPositionMs,
                    onSeekTo = onSeekTo,
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    motionEnabled = lyricsMotionEnabled
                )
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
    motionEnabled: Boolean
) {
    val listState = rememberLazyListState()
    val isSynced = syncType != LyricsSyncType.PLAIN
    
    // Calculate current line index based on playback position. -1 while the
    // intro plays, so the first line isn't falsely highlighted before its
    // timestamp is reached.
    val currentLineIndex by remember(currentPositionMs, lines, isSynced) {
        derivedStateOf {
            if (isSynced) lines.indexOfLast { it.timeMs <= currentPositionMs } else -1
        }
    }

    // Auto-scroll to current line
    LaunchedEffect(currentLineIndex, lines) {
        if (isSynced && currentLineIndex >= 0 && lines.isNotEmpty()) {
            // Animate scroll to the current line to keep it centered
            try {
                listState.animateScrollToItem(
                    index = currentLineIndex.coerceAtLeast(0),
                    scrollOffset = 0
                )
            } catch (e: Exception) {
                // Ignore scroll errors during rapid updates
            }
        }
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Calculate padding to center the content
        val centerPadding = maxHeight / 2
        
        LazyColumn(
            state = listState,
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
                top = centerPadding,
                bottom = centerPadding,
                start = 32.dp,
                end = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { index, line -> "${index}_${line.timeMs}" }) { index, line ->
                LyricLine(
                    line = line,
                    isCurrent = index == currentLineIndex,
                    isSynced = isSynced,
                    onTap = { onSeekTo(line.timeMs) },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    motionEnabled = motionEnabled,
                    currentPositionMs = currentPositionMs
                )
            }
        }
    }
}

@Composable
private fun LyricLine(
    line: LrcLine,
    isCurrent: Boolean,
    isSynced: Boolean,
    onTap: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    motionEnabled: Boolean,
    currentPositionMs: Long = 0L
) {
    // Animate scale for current line emphasis
    val scale by animateFloatAsState(
        targetValue = if (isSynced && isCurrent) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LyricScale"
    )
    
    // Animate alpha for past/future lines
    val alpha by animateFloatAsState(
        targetValue = if (!isSynced || isCurrent) 1f else 0.35f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LyricAlpha"
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val tapModifier = if (isSynced) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onTap
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .then(tapModifier)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isCurrent && line.contentSpans.isNotEmpty()) {
            KaraokeWordFlow(
                spans = line.contentSpans,
                lineText = line.text,
                currentPositionMs = currentPositionMs,
                primaryColor = primaryColor,
                unsungColor = onSurfaceColor.copy(alpha = 0.5f),
                motionEnabled = motionEnabled,
                modifier = Modifier.fillMaxWidth()
            )
            
        } else if (isSynced && isCurrent) {
            // Standard LRC: Line-Synced Only
            // Just highlight the whole line clearly. No "fake" gradient filling.
            // This is safer and more honest to the user.
            
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = primaryColor.copy(alpha = 0.5f),
                        blurRadius = 24f
                    )
                ),
                color = primaryColor,
                textAlign = TextAlign.Center
            )
        } else {
            // Inactive lines
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = onSurfaceColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val LETTER_LIFT_DURATION_MS = 220L
private val LetterLiftEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

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
    val position: Offset,
    val pivot: Offset
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
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = primaryColor,
            textAlign = TextAlign.Center
        )
        return
    }

    val textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
    val density = LocalDensity.current
    val wordSpacing = with(density) {
        runCatching { (textStyle.fontSize.toPx() * 0.26f).toDp() }.getOrDefault(6.dp)
    }

    CenteredLyricFlow(
        tokens = tokens,
        horizontalSpacing = wordSpacing,
        verticalSpacing = 8.dp,
        modifier = modifier.clearAndSetSemantics {
            text = androidx.compose.ui.text.AnnotatedString(lineText)
        }
    ) { token ->
        KaraokeWord(
            token = token,
            currentPositionProvider = currentPositionProvider,
            primaryColor = primaryColor,
            unsungColor = unsungColor,
            motionEnabled = motionEnabled,
            textStyle = textStyle
        )
    }
}

@Composable
private fun KaraokeWord(
    token: TimedLyricToken,
    currentPositionProvider: () -> Long,
    primaryColor: Color,
    unsungColor: Color,
    motionEnabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize = token.graphemes.size + 1)
    val liftPx = with(density) { 6.dp.toPx() }
    val topInsetPx = with(density) { 8.dp.toPx() }
    val measuredWord = remember(token, textStyle, textMeasurer, topInsetPx) {
        measureLyricWord(
            token = token,
            textStyle = textStyle,
            textMeasurer = textMeasurer,
            topInsetPx = topInsetPx
        )
    }
    val canvasWidth = with(density) { measuredWord.widthPx.toDp() }
    val canvasHeight = with(density) { measuredWord.heightPx.toDp() }

    // The reference renderer measures a complete word once, then draws each
    // glyph at its original bound inside one fixed Canvas. Playback therefore
    // invalidates drawing only: kerning, wrapping, and line placement never
    // change while the letter wave moves.
    Canvas(modifier = Modifier.size(width = canvasWidth, height = canvasHeight)) {
        val currentPositionMs = currentPositionProvider()
        measuredWord.graphemes.forEach { glyph ->
            val grapheme = glyph.timing
            val highlightProgress = (
                (currentPositionMs - grapheme.startMs).toFloat() / grapheme.durationMs
            ).coerceIn(0f, 1f)
            val liftProgress = (
                (currentPositionMs - grapheme.startMs).toFloat() / LETTER_LIFT_DURATION_MS
            ).coerceIn(0f, 1f)
            val liftAmount = if (motionEnabled) {
                LetterLiftEasing.transform(liftProgress)
            } else {
                0f
            }

            // A short swell and glow travel with the leading letter, but its
            // vertical lift remains completed after the pulse has passed.
            val pulse = 4f * liftProgress * (1f - liftProgress)
            val letterScale = 1f + 0.06f * pulse
            val glow = if (liftProgress in 0.0001f..0.9999f) {
                (liftProgress * (1f - liftProgress) / 0.21f).coerceIn(0f, 1f)
            } else {
                0f
            }
            val drawPosition = glyph.position.copy(
                y = glyph.position.y - liftPx * liftAmount
            )
            val drawPivot = glyph.pivot.copy(
                y = glyph.pivot.y - liftPx * liftAmount
            )

            withTransform({
                scale(
                    scaleX = letterScale,
                    scaleY = letterScale,
                    pivot = drawPivot
                )
            }) {
                drawText(
                    textLayoutResult = glyph.textLayout,
                    color = lerp(unsungColor, primaryColor, highlightProgress),
                    topLeft = drawPosition,
                    shadow = if (glow > 0f) {
                        Shadow(
                            color = primaryColor.copy(alpha = 0.4f * glow),
                            blurRadius = 10f * glow
                        )
                    } else {
                        null
                    }
                )
            }
        }
    }
}

private fun measureLyricWord(
    token: TimedLyricToken,
    textStyle: androidx.compose.ui.text.TextStyle,
    textMeasurer: TextMeasurer,
    topInsetPx: Float
): MeasuredLyricWord {
    val wordText = token.graphemes.joinToString(separator = "") { it.text }
    val wordLayout = textMeasurer.measure(
        text = wordText,
        style = textStyle,
        maxLines = 1,
        softWrap = false
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
        val originalWidth = (right - left).coerceAtLeast(0f)
        val x = left + (originalWidth - graphemeLayout.size.width) / 2f
        val y = topInsetPx + wordLayout.firstBaseline - graphemeLayout.firstBaseline
        val position = Offset(x = x, y = y)
        val pivot = Offset(
            x = left + originalWidth / 2f,
            y = y + graphemeLayout.size.height
        )
        textOffset = endOffset
        MeasuredLyricGrapheme(
            timing = grapheme,
            textLayout = graphemeLayout,
            position = position,
            pivot = pivot
        )
    }

    return MeasuredLyricWord(
        widthPx = wordLayout.size.width.coerceAtLeast(1),
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
                var x = ((layoutWidth - line.width) / 2).coerceAtLeast(0)
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

    val lineGraphemes = splitIntoGraphemes(lineText)
    val layoutGraphemes = if (lineGraphemes.count { it.isNotBlank() } == timedGraphemes.size) {
        lineGraphemes
    } else {
        spans.flatMap { splitIntoGraphemes(it.text) }
    }

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

        val timing = timedGraphemes.getOrNull(timedIndex++) ?: return@forEach
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
