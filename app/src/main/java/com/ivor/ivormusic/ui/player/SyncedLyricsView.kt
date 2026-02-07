package com.ivor.ivormusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.LrcLine
import com.ivor.ivormusic.data.LyricsResult
import kotlinx.coroutines.launch

/**
 * Synced Lyrics View - Material 3 Expressive
 * 
 * Features:
 * - Auto-scrolling to current lyric line
 * - Highlighted current line with distinct styling
 * - Spring animations for smooth transitions
 * - Tap to seek functionality
 * - Beautiful gradient fade at top/bottom
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncedLyricsView(
    lyricsResult: LyricsResult,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    ambientBackground: Boolean = false,
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onSurfaceColor: Color = MaterialTheme.colorScheme.onSurface,
    onSurfaceVariantColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
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
                    currentPositionMs = currentPositionMs,
                    onSeekTo = onSeekTo,
                    ambientBackground = ambientBackground,
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            }
        }
    }
}

@Composable
private fun LyricsContent(
    lines: List<LrcLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    ambientBackground: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    val listState = rememberLazyListState()
    
    // Calculate current line index based on playback position
    val currentLineIndex by remember(currentPositionMs, lines) {
        derivedStateOf {
            val index = lines.indexOfLast { it.timeMs <= currentPositionMs }
            index.coerceAtLeast(0)
        }
    }
    
    // Auto-scroll to current line
    LaunchedEffect(currentLineIndex) {
        if (lines.isNotEmpty()) {
            // Animate scroll to the current line to keep it centered
            // The BoxWithConstraints padding strategy ensures this aligns to center
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = 0
            )
        }
    }
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Calculate padding to center the content
        // We use half height for top/bottom padding so the first/last items can reach the center
        val centerPadding = maxHeight / 2
        
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Large vertical padding allows every item to be scrolled to the center
            contentPadding = PaddingValues(
                top = centerPadding,
                bottom = centerPadding,
                start = 24.dp,
                end = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp), // Increased spacing for better readability
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(lines, key = { index, line -> "${index}_${line.timeMs}" }) { index, line ->
                LyricLine(
                    line = line,
                    isCurrent = index == currentLineIndex,
                    isPast = index < currentLineIndex,
                    onTap = { onSeekTo(line.timeMs) },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            }
        }
        
        // Top fade gradient
        if (!ambientBackground) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(centerPadding) // Gradient covers the top padding area
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0f)
                            )
                        )
                    )
            )
            
            // Bottom fade gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(centerPadding) // Gradient covers the bottom padding area
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun LyricLine(
    line: LrcLine,
    isCurrent: Boolean,
    isPast: Boolean,
    onTap: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    // Animate scale for current line emphasis
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "LyricScale"
    )
    
    // Animate alpha for past/future lines
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            isPast -> 0.4f
            else -> 0.6f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LyricAlpha"
    )
    
    // Animate color
    val textColor by animateColorAsState(
        targetValue = if (isCurrent) primaryColor else onSurfaceColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "LyricColor"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) primaryColor.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Text(
            text = line.text,
            style = if (isCurrent) {
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.titleLarge
            },
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
        )
    }
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
