package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.ui.theme.IvorMusicTheme

/**
 * Player content designed to be shown inside a ModalBottomSheet.
 * Uses Material 3 Expressive components with bouncy animations.
 * Design inspired by M3 Expressive concepts with big button groups.
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

    // Colors - using dynamic theme
    val surfaceColor = MaterialTheme.colorScheme.background
    val onSurfaceColor = MaterialTheme.colorScheme.onBackground
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        Crossfade(targetState = showQueue, label = "PlayerQueueTransition") { isQueueVisible ->
            if (isQueueVisible) {
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
                        IconButton(onClick = onCollapse) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Collapse",
                                tint = onSurfaceColor,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        
                        IconButton(onClick = { showQueue = false }) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Now Playing",
                                tint = primaryColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    QueueView(
                        queue = currentQueue,
                        currentSong = currentSong,
                        onSongClick = { song -> viewModel.playQueue(currentQueue, song) },
                        onLoadMore = onLoadMore,
                        onSurfaceColor = onSurfaceColor,
                        onSurfaceVariantColor = onSurfaceVariantColor,
                        primaryColor = primaryColor
                    )
                }
            } else {
                // Full-screen Player with Expressive Design
                ExpressivePlayerView(
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
 * Expressive Player View following M3 Expressive concept:
 * - 75% Full-bleed album art
 * - Gradient blend at bottom
 * - Wavy progress indicator inside the blend
 * - Big circular Play button + Vertical Pill Group for Skip
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressivePlayerView(
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
        // ========== 1. FULL SCROLLABLE/BLEED ALBUM ART (75% Height) ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.TopCenter)
        ) {
            if (currentSong?.albumArtUri != null || currentSong?.thumbnailUrl != null) {
                AsyncImage(
                    model = currentSong?.highResThumbnailUrl ?: currentSong?.thumbnailUrl ?: currentSong?.albumArtUri,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
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
                    .padding(horizontal = 12.dp, vertical = 48.dp) // Adjusted for status bar
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onCollapse,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Collapse")
                }
                
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Safe fallback icon if PlaylistPlay is missing
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic, 
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Playing Your Mix",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
                
                FilledIconButton(
                    onClick = onShowQueue,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue")
                }
            }
            
            // Gradient Blend at Bottom of Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
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
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Wavy Progress - Inside the Blend Area
            Box(contentAlignment = Alignment.Center) {
                val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "Progress"
                )
                
                val thickStroke = Stroke(width = with(LocalDensity.current) { 5.dp.toPx() }, cap = StrokeCap.Round)

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    stroke = thickStroke,
                    trackStroke = thickStroke,
                    color = primaryColor,
                    trackColor = onSurfaceVariantColor.copy(alpha = 0.15f)
                )
                
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
                Text(formatDuration(progress), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor)
                Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, color = onSurfaceVariantColor)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Song Title & Artist
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurfaceColor
                )
                Text(
                    text = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurfaceVariantColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ========== BIG BUTTON GROUP ==========
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. Big Play Button (Circle)
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = primaryContainerColor,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (isBuffering && playWhenReady) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                
                // 2. Vertical Pills Group (Skip Prev / Next)
                // "Vertical Pills" interpreted as TALL buttons in a group
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = secondaryContainerColor,
                    modifier = Modifier.height(96.dp) // Same height as Play button
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.fillMaxHeight().aspectRatio(0.7f)) {
                            Icon(Icons.Default.SkipPrevious, "Prev", modifier = Modifier.size(32.dp))
                        }
                        
                        // Vertical Divider
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(onSurfaceVariantColor.copy(alpha = 0.2f))
                        )
                        
                        IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.fillMaxHeight().aspectRatio(0.7f)) {
                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bottom Actions (Shuffle, Like, Repeat)
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconToggleButton(
                    checked = shuffleModeEnabled,
                    onCheckedChange = { viewModel.toggleShuffle() }
                ) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffleModeEnabled) primaryColor else onSurfaceVariantColor)
                }
                
                IconToggleButton(
                    checked = isFavorite,
                    onCheckedChange = onFavoriteToggle
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else onSurfaceVariantColor,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { viewModel.toggleRepeat() }
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) primaryColor else onSurfaceVariantColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QueueView(
    queue: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onLoadMore: () -> Unit,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    primaryColor: Color
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                // Queue items
                itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                    val isCurrent = song.id == currentSong?.id
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isCurrent) primaryColor.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onSongClick(song) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Thumbnail
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(8.dp),
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
                    
                    if (index < queue.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = onSurfaceColor.copy(alpha = 0.05f)
                        )
                    }
                }

                // Load More Button at the end
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalButton(
                            onClick = onLoadMore,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
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
