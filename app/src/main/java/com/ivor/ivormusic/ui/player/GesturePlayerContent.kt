package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.LyricsResult

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
    
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    val surfaceColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        Crossfade(targetState = showQueue, label = "GesturePlayerQueueTransition") { isQueueVisible ->
            if (isQueueVisible) {
                GestureQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.playQueue(currentQueue, song) },
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
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
    // Find current song index in queue
    val currentIndex = remember(currentSong, queue) {
        queue.indexOfFirst { it.id == currentSong?.id }.coerceAtLeast(0)
    }
    
    // Carousel state synced to current song
    val carouselState = rememberCarouselState(initialItem = currentIndex) { queue.size.coerceAtLeast(1) }
    
    // Sync carousel with song changes from external sources
    LaunchedEffect(currentIndex) {
        if (queue.isNotEmpty() && carouselState.currentItem != currentIndex) {
            carouselState.scrollToItem(currentIndex)
        }
    }
    
    // Handle carousel scroll to change songs
    LaunchedEffect(carouselState.currentItem) {
        val currentCarouselIndex = carouselState.currentItem
        if (queue.isNotEmpty() && currentCarouselIndex != currentIndex && currentCarouselIndex in queue.indices) {
            onSongChange(queue[currentCarouselIndex])
        }
    }
    
    // Get album info
    val albumName = currentSong?.album?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Album"
    val albumArtUrl = currentSong?.highResThumbnailUrl 
        ?: currentSong?.thumbnailUrl 
        ?: currentSong?.albumArtUri?.toString()
    
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
            horizontalAlignment = Alignment.CenterHorizontally
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
                
                Spacer(modifier = Modifier.weight(1f))
                
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ========== 2. "PLAYING FROM" HEADER ==========
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ========== 3. ALBUM ART CAROUSEL ==========
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (queue.isNotEmpty()) {
                    GestureAlbumCarousel(
                        queue = queue,
                        carouselState = carouselState,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onPlayPauseToggle = onPlayPauseToggle
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ========== 4. SONG INFO ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurfaceColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                val artistName = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurfaceVariantColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = artistName != "Unknown Artist") { onArtistClick(artistName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ========== 5. SLIDER PROGRESS (Video Player Style) ==========
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val progressFraction = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f
                
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction -> 
                        onSeekTo((fraction * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = primaryColor,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = onSurfaceVariantColor.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ========== 6. FLOATING TOOLBAR (Action Buttons) ==========
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
            
            Spacer(modifier = Modifier.height(40.dp))
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
                        FloatingActionButtonDefaults.containerColor
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
 * Gesture Album Carousel - Swipe to change songs, tap to play/pause
 * Uses Material 3 Expressive HorizontalUncontainedCarousel with maskClip for proper transitions
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GestureAlbumCarousel(
    queue: List<Song>,
    carouselState: androidx.compose.material3.carousel.CarouselState,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseToggle: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val albumSize = minOf(maxWidth, maxHeight) * 0.85f
        
        HorizontalUncontainedCarousel(
            state = carouselState,
            itemWidth = albumSize,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = (maxWidth - albumSize) / 2),
            modifier = Modifier
                .fillMaxWidth()
                .height(albumSize)
        ) { index ->
            val song = queue.getOrNull(index)
            val isCurrentItem = carouselState.currentItem == index
            
            // Use maskClip for proper carousel item clipping during scroll
            Box(
                modifier = Modifier
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onPlayPauseToggle() })
                    }
            ) {
                val imgUrl = song?.highResThumbnailUrl ?: song?.thumbnailUrl ?: song?.albumArtUri?.toString()
                
                if (imgUrl != null) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = song?.title ?: "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder
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
                
                // Play/Pause overlay for current item
                if (isCurrentItem && (isBuffering || !isPlaying)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
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
                        } else if (!isPlaying) {
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
        val albumSize = minOf(maxWidth, maxHeight) * 0.85f
        val cornerRadius = albumSize * 0.10f
        
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
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    isLocalOriginal: (Song) -> Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
