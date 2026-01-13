package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.LyricsResult
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Material 3 Expressive Gesture-Based Music Player
 * 
 * Design based on reference UI:
 * - "Playing From" header with album name at top
 * - Large carousel album art (tap to play/pause, swipe to change tracks)
 * - Song title and artist centered below
 * - Slider progress bar
 * - HorizontalFloatingToolbar at the bottom for actions (shuffle, repeat, favorite, download)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GesturePlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    BackHandler(enabled = true) {
        onCollapse()
    }
    
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    
    // Lyrics state
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    
    // Load more state
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    // Root guard against invalid dimensions during collapse transitions
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Don't render anything if dimensions are too small
        if (maxWidth < 200.dp || maxHeight < 200.dp) {
            return@BoxWithConstraints
        }
        
        Crossfade(targetState = showQueue, label = "GesturePlayerQueueTransition") { isQueueVisible ->
            if (isQueueVisible) {
                GestureQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.playQueue(currentQueue, song) },
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    isDownloaded = { id -> viewModel.isDownloaded(id) },
                    isDownloading = { id -> viewModel.isDownloading(id) },
                    isLocalOriginal = { song -> viewModel.isLocalOriginal(song) },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            } else {
                GestureNowPlayingView(
                    currentSong = currentSong,
                    queue = currentQueue,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering && playWhenReady,
                    progress = progress,
                    duration = duration,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    lyricsResult = lyricsResult,
                    showLyrics = showLyrics,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onSeekTo = { viewModel.seekTo(it) },
                    ambientBackground = ambientBackground,
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    onArtistClick = onArtistClick,
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onToggleFavorite = { viewModel.toggleCurrentSongLike() },
                    onToggleDownload = { currentSong?.let { viewModel.toggleDownload(it) } },
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    onSongChange = { song -> viewModel.playQueue(currentQueue, song) },
                    isDownloaded = currentSong?.let { viewModel.isDownloaded(it.id) } ?: false,
                    isDownloading = currentSong?.let { viewModel.isDownloading(it.id) } ?: false,
                    isLocalOriginal = currentSong?.let { viewModel.isLocalOriginal(it) } ?: false,
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
            }
        }
    }
}

/**
 * Gesture-based Now Playing View matching the reference design
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GestureNowPlayingView(
    currentSong: Song?,
    queue: List<Song>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    lyricsResult: LyricsResult,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onSeekTo: (Long) -> Unit,
    ambientBackground: Boolean,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onArtistClick: (String) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onSongChange: (Song) -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocalOriginal: Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    // Simple current song index for the custom carousel
    val currentIndex = remember(currentSong?.id, queue.size) {
        if (queue.isEmpty()) 0
        else queue.indexOfFirst { it.id == currentSong?.id }.coerceIn(0, queue.lastIndex.coerceAtLeast(0))
    }
    
    // Get album info
    val albumName = currentSong?.album?.takeIf { it.isNotEmpty() && !it.startsWith("Unknown") } ?: "Unknown Album"
    val albumArtUrl = currentSong?.highResThumbnailUrl 
        ?: currentSong?.thumbnailUrl 
        ?: currentSong?.albumArtUri?.toString()
    
    // Use BoxWithConstraints to guard against invalid dimensions during transitions
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Don't render content if dimensions are too small (during collapse animation)
        if (maxWidth < 100.dp || maxHeight < 100.dp) {
            return@BoxWithConstraints
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            // Chromatic Mist ambient background
            ChromaticMistBackground(
                albumArtUrl = albumArtUrl,
                enabled = ambientBackground,
                modifier = Modifier.fillMaxSize()
            )
            
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ========== 1. TOP BAR ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Collapse button
                FilledIconButton(
                    onClick = onCollapse,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(28.dp))
                }
                
                // Lyrics Toggle Button (center)
                Surface(
                    onClick = onToggleLyrics,
                    shape = RoundedCornerShape(24.dp),
                    color = if (showLyrics) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lyrics,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (showLyrics) MaterialTheme.colorScheme.onPrimary 
                                   else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showLyrics) "Now Playing" else "Lyrics",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (showLyrics) MaterialTheme.colorScheme.onPrimary 
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Queue button
                FilledIconButton(
                    onClick = onShowQueue,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", modifier = Modifier.size(24.dp))
                }
            }
            
            // ========== 2. "PLAYING FROM" HEADER (right below top bar) ==========
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Playing From",
                style = MaterialTheme.typography.labelMedium,
                color = onSurfaceVariantColor,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = albumName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = onSurfaceColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            // ========== CENTER CONTENT (vertically centered) ==========
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // ========== 3. ALBUM ART / LYRICS (Crossfade) ==========
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Use most of available width for HUGE album art - ensure positive size
                    val albumSize = (maxWidth - 32.dp).coerceAtLeast(1.dp)
                    
                    Box(
                        modifier = Modifier.size(albumSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(targetState = showLyrics, label = "AlbumLyricsCrossfade") { isLyricsVisible ->
                            if (isLyricsVisible) {
                                // Key on song ID to force complete reset when song changes
                                key(currentSong?.id) {
                                    // Synced Lyrics View
                                    SyncedLyricsView(
                                        lyricsResult = lyricsResult,
                                        currentPositionMs = progress,
                                        onSeekTo = onSeekTo,
                                        ambientBackground = ambientBackground,
                                        primaryColor = primaryColor,
                                        onSurfaceColor = onSurfaceColor,
                                        onSurfaceVariantColor = onSurfaceVariantColor
                                    )
                                }
                            } else {
                                // Album Art Carousel
                                if (queue.isNotEmpty()) {
                                    SwipeableAlbumCarousel(
                                        queue = queue,
                                        currentIndex = currentIndex,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        onPlayPauseToggle = onPlayPauseToggle,
                                        onSongChange = onSongChange
                                    )
                                } else if (currentSong != null) {
                                    // Single album art
                                    SingleAlbumArt(
                                        song = currentSong,
                                        isPlaying = isPlaying,
                                        isBuffering = isBuffering,
                                        onPlayPauseToggle = onPlayPauseToggle
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Centered song info and progress area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ========== 4. SONG INFO ==========
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = onSurfaceColor,
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val artistName = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.titleMedium,
                                color = onSurfaceVariantColor,
                                modifier = Modifier
                                    .clickable(enabled = artistName != "Unknown Artist") { onArtistClick(artistName) },
                                textAlign = TextAlign.Start
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(30.dp))
                        
                        // ========== 5. WAVY PROGRESS BAR (Expressive Style) ==========
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
                            val animatedProgress by animateFloatAsState(
                                targetValue = progressFraction,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "WavyProgress"
                            )
                            
                            val thickStroke = Stroke(width = with(LocalDensity.current) { 6.dp.toPx() }, cap = StrokeCap.Round)
                            
                            // Wavy progress with invisible slider overlay for touch
                            Box(contentAlignment = Alignment.Center) {
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
                                    onValueChange = { onSeekTo(it.toLong()) },
                                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Transparent,
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(progress), 
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = onSurfaceVariantColor
                                )
                                Text(
                                    text = formatDuration(duration), 
                                    style = MaterialTheme.typography.labelMedium, 
                                    color = onSurfaceVariantColor
                                )
                            }
                        }
                    }
                }
            }
            
            // ========== 6. FLOATING TOOLBAR (Action Buttons) ==========
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GesturePlayerToolbar(
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    isLocalOriginal = isLocalOriginal,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    onToggleFavorite = onToggleFavorite,
                    onToggleDownload = onToggleDownload,
                    primaryColor = primaryColor,
                    onSurfaceVariantColor = onSurfaceVariantColor
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        }
    }
}

/**
 * Floating toolbar using official Material 3 Expressive HorizontalFloatingToolbar
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun GesturePlayerToolbar(
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocalOriginal: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
    primaryColor: Color,
    onSurfaceVariantColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            floatingActionButton = {
                // Use FAB for the favorite button (highlighted action)
                FloatingToolbarDefaults.StandardFloatingActionButton(
                    onClick = onToggleFavorite,
                    containerColor = if (isFavorite) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        FloatingActionButtonDefaults.containerColor,
                    contentColor = if (isFavorite) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else onSurfaceVariantColor
                    )
                }
            },
            content = {
                // Shuffle toggle
                IconToggleButton(
                    checked = shuffleModeEnabled,
                    onCheckedChange = { onToggleShuffle() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle"
                    )
                }
                
                // Repeat toggle
                IconToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { onToggleRepeat() }
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat"
                    )
                }
                
                // Download button (or local indicator)
                if (isLocalOriginal) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "Local File"
                        )
                    }
                } else {
                    IconToggleButton(
                        checked = isDownloaded,
                        onCheckedChange = { onToggleDownload() }
                    ) {
                        if (isDownloading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = primaryColor
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                contentDescription = if (isDownloaded) "Downloaded" else "Download"
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * Custom Swipeable Album Carousel with smooth sliding animations
 * 
 * Architecture:
 * - Uses a continuous float position for the carousel
 * - Pre-renders 5 albums (-2 to +2) so transitions are instant
 * - External changes animate smoothly via spring physics
 * - No flickering, no delayed loading
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SwipeableAlbumCarousel(
    queue: List<Song>,
    currentIndex: Int,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSongChange: (Song) -> Unit
) {
    // The carousel position as a continuous float
    // Position 0 = first song centered, Position 1 = second song centered, etc.
    var targetPosition by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    val animatedPosition by animateFloatAsState(
        targetValue = targetPosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "CarouselPosition"
    )
    
    // Track if user is dragging (to pause external sync during drag)
    var isDragging by remember { mutableStateOf(false) }
    
    // Sync with external index changes (e.g., from queue click, skip buttons)
    LaunchedEffect(currentIndex) {
        if (!isDragging && targetPosition.roundToInt() != currentIndex) {
            // Smoothly animate to the new position
            targetPosition = currentIndex.toFloat()
        }
    }
    
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Sizing calculations - BIGGER for better visibility
        val albumSize = (minOf(maxWidth * 0.90f, maxHeight * 0.95f)).coerceAtLeast(1.dp)
        val cornerRadius = albumSize * 0.08f
        
        // Spacing between album centers
        val spacing = albumSize * 0.75f
        val spacingPx = with(LocalDensity.current) { spacing.toPx() }
        
        // Track starting position when drag begins
        var dragStartPosition by remember { mutableFloatStateOf(0f) }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(albumSize)
                .pointerInput(queue.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            isDragging = true
                            dragStartPosition = targetPosition
                        },
                        onDragEnd = {
                            isDragging = false
                            // Snap to nearest integer position
                            val nearestIndex = targetPosition.roundToInt().coerceIn(0, queue.lastIndex)
                            val startIndex = dragStartPosition.roundToInt()
                            
                            // Always trigger song change if we moved to different index
                            if (nearestIndex != startIndex && nearestIndex in 0..queue.lastIndex) {
                                onSongChange(queue[nearestIndex])
                            }
                            targetPosition = nearestIndex.toFloat()
                        },
                        onDragCancel = {
                            isDragging = false
                            // Return to start position on cancel
                            targetPosition = dragStartPosition.roundToInt().toFloat().coerceIn(0f, queue.lastIndex.toFloat())
                        },
                        onHorizontalDrag = { _, amount ->
                            val delta = -amount / spacingPx // Negative: drag right = go to lower index
                            val newPosition = (targetPosition + delta).coerceIn(0f, queue.lastIndex.toFloat())
                            targetPosition = newPosition
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Render albums in range: visible ones around the current position
            // Pre-render 2 extra on each side for smooth transitions
            val centerIndex = animatedPosition.roundToInt()
            val visibleRange = (centerIndex - 2).coerceAtLeast(0)..(centerIndex + 2).coerceAtMost(queue.lastIndex)
            
            visibleRange.forEach { songIndex ->
                val song = queue[songIndex]
                
                // Position relative to center (0 = centered)
                val relativePosition = songIndex.toFloat() - animatedPosition
                val distanceFromCenter = relativePosition.absoluteValue
                
                // Skip rendering if too far off (optimization)
                if (distanceFromCenter > 2.5f) return@forEach
                
                // Size: full at center, smaller at sides
                val sizeMultiplier = (1f - distanceFromCenter * 0.30f).coerceIn(0.55f, 1f)
                val currentSize = albumSize * sizeMultiplier
                val currentCornerRadius = cornerRadius * sizeMultiplier
                
                // Alpha: full at center, faded at sides
                val alpha = (1f - distanceFromCenter * 0.45f).coerceIn(0.35f, 1f)
                
                // Rotation: tilted based on position
                val rotation = relativePosition * 8f
                
                // Z-index: center album on top
                val zIndex = (10f - distanceFromCenter * 3f).coerceAtLeast(0f)
                
                // X position from center
                val xOffset = spacing * relativePosition
                
                // Y offset for depth (side albums drop down)
                val yOffset = 10.dp * distanceFromCenter
                
                // Is this the "current" centered album?
                val isCentered = distanceFromCenter < 0.5f
                
                val imgUrl = song.highResThumbnailUrl 
                    ?: song.thumbnailUrl 
                    ?: song.albumArtUri?.toString()
                
                // Use key for stable identity
                key(song.id) {
                    Surface(
                        modifier = Modifier
                            .size(currentSize)
                            .offset(x = xOffset, y = yOffset)
                            .zIndex(zIndex)
                            .alpha(alpha)
                            .graphicsLayer { rotationZ = rotation }
                            .then(
                                if (isCentered) {
                                    Modifier.pointerInput(Unit) {
                                        detectTapGestures(onTap = { onPlayPauseToggle() })
                                    }
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(currentCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = if (isCentered) 16.dp else 4.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Album art with stable loading
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = song.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(currentCornerRadius)),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Fallback gradient if no image
                            if (imgUrl == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(currentSize * 0.35f),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                            
                            // Play/Pause overlay only for centered album when paused/buffering
                            if (isCentered && songIndex == currentIndex && (isBuffering || !isPlaying)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(currentCornerRadius))
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isBuffering) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(60.dp),
                                            color = Color.White,
                                            polygons = listOf(
                                                MaterialShapes.SoftBurst,
                                                MaterialShapes.Cookie9Sided,
                                                MaterialShapes.Pill,
                                                MaterialShapes.Sunny
                                            )
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            modifier = Modifier.size(68.dp),
                                            tint = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



/**
 * Single Album Art (fallback when no queue)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SingleAlbumArt(
    song: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Use 98% of available space for HUGE album art - ensure positive size
        val albumSize = (minOf(maxWidth, maxHeight) * 0.98f).coerceAtLeast(1.dp)
        val cornerRadius = (albumSize * 0.10f).coerceAtLeast(0.dp)
        
        Surface(
            modifier = Modifier
                .size(albumSize)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onPlayPauseToggle() })
                },
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 16.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imgUrl = song?.highResThumbnailUrl ?: song?.thumbnailUrl ?: song?.albumArtUri?.toString()
                
                if (imgUrl != null) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = song?.title ?: "Album Art",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(albumSize * 0.35f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
                
                // Play/Pause overlay
                if (isBuffering || !isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBuffering) {
                            LoadingIndicator(
                                modifier = Modifier.size(64.dp),
                                color = Color.White,
                                polygons = listOf(
                                    MaterialShapes.SoftBurst,
                                    MaterialShapes.Cookie9Sided,
                                    MaterialShapes.Pill,
                                    MaterialShapes.Sunny
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(72.dp),
                                tint = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gesture Queue View
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GestureQueueView(
    queue: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    isLocalOriginal: (Song) -> Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    // Guard against invalid dimensions during transitions
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (maxWidth < 100.dp || maxHeight < 100.dp) {
            return@BoxWithConstraints
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 48.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onCollapse,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(28.dp))
                }
                
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Up Next",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                FilledIconButton(
                    onClick = onBackToPlayer,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, "Now Playing", modifier = Modifier.size(24.dp))
                }
            }
            
            // Queue Content
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Queue is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = onSurfaceVariantColor
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Current song header
                    currentSong?.let { song ->
                        item(key = "now_playing_gesture_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Featured album art
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val imgUrl = song.highResThumbnailUrl ?: song.thumbnailUrl ?: song.albumArtUri?.toString()
                                    
                                    Surface(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clickable { onBackToPlayer() },
                                        shape = RoundedCornerShape(28.dp),
                                        shadowElevation = 8.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        if (imgUrl != null) {
                                            AsyncImage(
                                                model = imgUrl,
                                                contentDescription = "Now Playing",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(28.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Text(
                                    text = song.title.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = onSurfaceColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.artist.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = onSurfaceVariantColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = onSurfaceVariantColor.copy(alpha = 0.1f)
                                    )
                                    Text(
                                        text = "QUEUE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceVariantColor,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = onSurfaceVariantColor.copy(alpha = 0.1f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                    
                    // Queue items
                    itemsIndexed(queue, key = { index, song -> "gesture_queue_${song.id}_$index" }) { index, song ->
                        val isCurrent = song.id == currentSong?.id
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = if (isCurrent) primaryColor.copy(alpha = 0.12f) 
                                   else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = if (isCurrent) androidx.compose.foundation.BorderStroke(
                                1.5.dp, 
                                primaryColor.copy(alpha = 0.3f)
                            ) else null,
                            onClick = { onSongClick(song) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = "Playing",
                                            tint = primaryColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = onSurfaceVariantColor
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                val songImgUrl = song.thumbnailUrl ?: song.albumArtUri?.toString()
                                
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = if (isCurrent) 4.dp else 2.dp
                                ) {
                                    if (songImgUrl != null) {
                                        AsyncImage(
                                            model = songImgUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(16.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
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
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCurrent) primaryColor.copy(alpha = 0.7f) else onSurfaceVariantColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                // Download Status Icon
                                if (isDownloading(song.id)) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = if (isCurrent) primaryColor else onSurfaceVariantColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isDownloaded(song.id)) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Downloaded",
                                        tint = if (isCurrent) primaryColor else onSurfaceVariantColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else if (isLocalOriginal(song)) {
                                    Icon(
                                        imageVector = Icons.Rounded.Smartphone,
                                        contentDescription = "Local",
                                        tint = if (isCurrent) primaryColor.copy(alpha = 0.7f) else onSurfaceVariantColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                    
                    // Load More Button with expressive shape morphing
                    item(key = "gesture_queue_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = onLoadMore,
                                enabled = !isLoadingMore,
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
                            ) {
                                if (isLoadingMore) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        polygons = listOf(
                                            MaterialShapes.Cookie9Sided,
                                            MaterialShapes.Pill,
                                            MaterialShapes.Sunny
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Loading...", style = MaterialTheme.typography.titleSmall)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Load More", style = MaterialTheme.typography.titleSmall)
                                }
                            }
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
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
