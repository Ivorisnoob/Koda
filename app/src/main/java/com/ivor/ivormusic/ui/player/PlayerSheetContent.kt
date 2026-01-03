package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song

/**
 * 🌟 Material 3 Expressive Music Player
 * 
 * Design Philosophy:
 * - Bold, organic shapes with fluid animations
 * - Shape morphing on all interactive elements
 * - Spring physics for natural, bouncy feel
 * - Dynamic colors from album artwork
 * - MaterialShapes for loading indicators
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSheetContent(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {}
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    
    var isFavorite by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    // Theme colors
    val surfaceColor = MaterialTheme.colorScheme.background
    val onSurfaceColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        Crossfade(targetState = showQueue, label = "PlayerQueueTransition") { isQueueVisible ->
            if (isQueueVisible) {
                ExpressiveQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.playQueue(currentQueue, song) },
                    onLoadMore = onLoadMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            } else {
                ExpressiveNowPlayingView(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playWhenReady = playWhenReady,
                    progress = progress,
                    duration = duration,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    onFavoriteToggle = { isFavorite = it },
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    viewModel = viewModel,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    secondaryContainerColor = secondaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            }
        }
    }
}

/**
 * 🎵 Now Playing Screen with Expressive Design
 * 
 * Features:
 * - Full-bleed album art (75% height)
 * - Gradient blend at bottom
 * - Wavy progress indicator
 * - Huge morphing play button
 * - Expressive skip buttons with shape morphing
 * - IconToggleButton for favorite with shape morphing
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveNowPlayingView(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    onFavoriteToggle: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    viewModel: PlayerViewModel,
    primaryColor: Color,
    primaryContainerColor: Color,
    secondaryContainerColor: Color,
    tertiaryContainerColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ========== 1. FULL-BLEED ALBUM ART (75% Height) ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.70f)
                .align(Alignment.TopCenter)
        ) {
            // Album Art
            if (currentSong?.albumArtUri != null || currentSong?.thumbnailUrl != null) {
                AsyncImage(
                    model = currentSong?.highResThumbnailUrl ?: currentSong?.thumbnailUrl ?: currentSong?.albumArtUri,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder with expressive shape
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(180.dp),
                        tint = onSurfaceVariantColor.copy(alpha = 0.2f)
                    )
                }
            }
            
            // Top Controls Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 48.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collapse button with shape morphing
                FilledIconButton(
                    onClick = onCollapse,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(28.dp))
                }
                
                // Playing source pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic, 
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
                
                // Queue button with shape morphing
                FilledIconButton(
                    onClick = onShowQueue,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", modifier = Modifier.size(24.dp))
                }
            }
            
            // Gradient Blend at Bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }

        // ========== 2. CONTROLS & INFO (Bottom Section) ==========
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🌟 Wavy Progress Indicator (Expressive!)
            Box(contentAlignment = Alignment.Center) {
                val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "Progress"
                )
                
                val thickStroke = Stroke(width = with(LocalDensity.current) { 6.dp.toPx() }, cap = StrokeCap.Round)

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    stroke = thickStroke,
                    trackStroke = thickStroke,
                    color = primaryColor,
                    trackColor = onSurfaceVariantColor.copy(alpha = 0.15f)
                )
                
                // Invisible slider for touch interaction
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Time Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(progress), style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
                Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = onSurfaceVariantColor)
            }
            
            Spacer(modifier = Modifier.height(20.dp))

            // Song Title & Artist
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurfaceColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurfaceVariantColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ========== EXPRESSIVE STANDARD BUTTON GROUP ==========
            // Using Standard ButtonGroup for squishy physics - buttons expand on press
            // and neighbors compress to accommodate
            ButtonGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp) // Minor spacing between buttons
            ) {
                // Shuffle Toggle - with animateWidth for squishy physics
                val shuffleInteraction = remember { MutableInteractionSource() }
                FilledTonalIconToggleButton(
                    checked = shuffleModeEnabled,
                    onCheckedChange = { viewModel.toggleShuffle() },
                    interactionSource = shuffleInteraction,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                        .animateWidth(shuffleInteraction)
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Previous Button - with animateWidth for squishy physics
                val prevInteraction = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = { viewModel.skipToPrevious() },
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = prevInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .animateWidth(prevInteraction)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                // 🌟 Play/Pause Button (center, larger weight for emphasis)
                val playInteraction = remember { MutableInteractionSource() }
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = playInteraction,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primaryContainerColor,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .animateWidth(playInteraction)
                ) {
                    if (isBuffering && playWhenReady) {
                        // 🌟 Organic morphing loading with MaterialShapes
                        LoadingIndicator(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            polygons = listOf(
                                MaterialShapes.SoftBurst,
                                MaterialShapes.Cookie9Sided,
                                MaterialShapes.Pill,
                                MaterialShapes.Sunny
                            )
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // Next Button - with animateWidth for squishy physics
                val nextInteraction = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = { viewModel.skipToNext() },
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = nextInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .animateWidth(nextInteraction)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(36.dp)
                    )
                }
                
                // Repeat Toggle - with animateWidth for squishy physics
                val repeatInteraction = remember { MutableInteractionSource() }
                FilledTonalIconToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { viewModel.toggleRepeat() },
                    interactionSource = repeatInteraction,
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxHeight()
                        .animateWidth(repeatInteraction)
                ) {
                    Icon(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 🌟 Favorite Toggle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedIconToggleButton(
                    checked = isFavorite,
                    onCheckedChange = onFavoriteToggle,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * � Expressive Queue View
 * 
 * Features:
 * - Animated queue items with spring physics
 * - Currently playing indicator with expressive icon
 * - Load more button with shape morphing
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveQueueView(
    queue: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onLoadMore: () -> Unit,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Top bar for Queue
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onCollapse,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = onSurfaceColor
            )
            
            FilledTonalIconButton(
                onClick = onBackToPlayer,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = "Now Playing",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurfaceVariantColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = song.id == currentSong?.id
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isCurrent) primaryColor.copy(alpha = 0.12f) else Color.Transparent,
                        onClick = { onSongClick(song) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl ?: song.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) primaryColor else onSurfaceColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) primaryColor.copy(alpha = 0.7f) else onSurfaceVariantColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = "Playing",
                                    tint = primaryColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    if (index < queue.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = onSurfaceColor.copy(alpha = 0.05f)
                        )
                    }
                }

                // Load More Button with expressive shape morphing
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onLoadMore,
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Load More Recommendations", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
