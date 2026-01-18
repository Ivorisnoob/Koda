package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.rounded.PlaylistAdd
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
import com.ivor.ivormusic.data.LyricsResult

/**
 *  Material 3 Expressive Music Player
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
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    // Handle back press to collapse player instead of quitting app
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
    
    // Download states
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    
    // Lyrics state
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    
    var showQueue by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    // 🌟 Stable shapes - prevents "square flash" on initial render
    // IconButtonDefaults.shapes() already uses internal remember/caching
    val stableIconButtonShapes = IconButtonDefaults.shapes()

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
                // Determine loading state for the specific "load more" action
                val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                
                ExpressiveQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.playQueue(currentQueue, song) },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    // Download status pass-through
                    isDownloaded = { id -> viewModel.isDownloaded(id) },
                    isDownloading = { id -> viewModel.isDownloading(id) },
                    isLocalOriginal = { song -> viewModel.isLocalOriginal(song) },
                    primaryColor = primaryColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                    stableShapes = stableIconButtonShapes
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
                    onFavoriteToggle = { viewModel.toggleCurrentSongLike() },
                    // Download actions
                    onDownloadToggle = { currentSong?.let { viewModel.toggleDownload(it) } },
                    isDownloaded = currentSong?.let { viewModel.isDownloaded(it.id) } ?: false,
                    isDownloading = currentSong?.let { viewModel.isDownloading(it.id) } ?: false,
                    isLocalOriginal = currentSong?.let { viewModel.isLocalOriginal(it) } ?: false,
                    // Lyrics
                    lyricsResult = lyricsResult,
                    onSeekTo = { viewModel.seekTo(it) },
                    ambientBackground = ambientBackground,
                    
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    onShowAddToPlaylist = { showAddToPlaylist = true },
                    onArtistClick = onArtistClick,
                    viewModel = viewModel,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    secondaryContainerColor = secondaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    onSurfaceColor = onSurfaceColor,
                    onSurfaceVariantColor = onSurfaceVariantColor,
                    stableShapes = stableIconButtonShapes
                )
            }
        }
    } // THIS BITCH ASS BRACKET WAS MISSING AND IT TOOK ME HOURS TO KNOW

    if (showAddToPlaylist) {
        AddToPlaylistSheet(
            playlists = localPlaylists,
            onPlaylistClick = { playlist ->
                viewModel.addToPlaylist(playlist.id)
                showAddToPlaylist = false
            },
            onCreateNewClick = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showAddToPlaylist = false
            },
            onDismissRequest = { showAddToPlaylist = false }
        )
    }
}


/**
 * I do not fucking ned that many slop comments thank you
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
    onDownloadToggle: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocalOriginal: Boolean,
    lyricsResult: LyricsResult,
    onSeekTo: (Long) -> Unit,
    ambientBackground: Boolean,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowAddToPlaylist: () -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: PlayerViewModel,
    primaryColor: Color,
    primaryContainerColor: Color,
    secondaryContainerColor: Color,
    tertiaryContainerColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    stableShapes: IconButtonShapes
) {
    // State for toggling between album art and lyrics
    var showLyrics by remember { mutableStateOf(false) }
    
    // Get album art URL for background
    val albumArtUrl = currentSong?.highResThumbnailUrl 
        ?: currentSong?.thumbnailUrl 
        ?: currentSong?.albumArtUri?.toString()
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Chromatic Mist ambient background
        ChromaticMistBackground(
            albumArtUrl = albumArtUrl,
            enabled = ambientBackground,
            modifier = Modifier.fillMaxSize()
        )
        
        // Content layer
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
        // ========== 1. TOP BAR ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse button - static shape (no morphing needed for utility buttons)
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
            
            // Expressive center toggle - morphs between "Now Playing" and "Lyrics" with animation
            val pillWidth by animateFloatAsState(
                targetValue = if (showLyrics) 140f else 160f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PillWidth"
            )
            
            Surface(
                modifier = Modifier
                    .width(pillWidth.dp)
                    .clickable { showLyrics = !showLyrics },
                shape = RoundedCornerShape(24.dp),
                color = if (showLyrics) MaterialTheme.colorScheme.surfaceContainerHigh else primaryColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Crossfade(targetState = showLyrics, label = "PillIcon") { isLyrics ->
                        Icon(
                            imageVector = if (isLyrics) Icons.AutoMirrored.Filled.QueueMusic else Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = if (isLyrics) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Crossfade(targetState = showLyrics, label = "PillText") { isLyrics ->
                        Text(
                            text = if (isLyrics) "Now Playing" else "Lyrics",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isLyrics) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            // Right Side Group: Add to Playlist + Queue
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = onShowAddToPlaylist,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, "Add to Playlist", modifier = Modifier.size(24.dp))
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
        }
        
        // ========== 2. ALBUM ART / LYRICS (Fills available space) ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = showLyrics, label = "AlbumLyricsCrossfade") { isLyricsVisible ->
                if (isLyricsVisible) {
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
                } else {
                    // Album Art
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val albumSize = minOf(maxWidth, maxHeight) * 0.9f
                        val cornerRadius = albumSize * 0.15f
                        
                        Box(contentAlignment = Alignment.Center) {
                            // Outer glow/shadow layer
                            Surface(
                                modifier = Modifier
                                    .size(albumSize)
                                    .offset(y = 8.dp),
                                shape = RoundedCornerShape(cornerRadius),
                                color = primaryContainerColor.copy(alpha = 0.3f),
                                shadowElevation = 24.dp
                            ) {}
                            
                            // Main album art container with expressive squircle shape
                            Surface(
                                modifier = Modifier.size(albumSize),
                                shape = RoundedCornerShape(cornerRadius),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Album Art
                                    if (currentSong?.albumArtUri != null || currentSong?.thumbnailUrl != null) {
                                        AsyncImage(
                                            model = currentSong?.highResThumbnailUrl ?: currentSong?.thumbnailUrl ?: currentSong?.albumArtUri,
                                            contentDescription = "Album Art",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(cornerRadius)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        // Placeholder with expressive shape
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            primaryContainerColor.copy(alpha = 0.5f),
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
                                                tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                    
                                    // Subtle inner gradient for depth
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(cornerRadius))
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(alpha = 0.1f)
                                                    ),
                                                    startY = 0f,
                                                    endY = Float.POSITIVE_INFINITY
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ========== 3. CONTROLS & INFO (Bottom Section) ==========
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    modifier = Modifier.fillMaxWidth().height(14.dp),
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
                // Clickable artist name
                val artistName = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = primaryColor.copy(alpha = 0.9f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            enabled = artistName != "Unknown Artist"
                        ) { onArtistClick(artistName) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    shapes = stableShapes,
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
                    shapes = stableShapes,
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
                    // FIX: Only show loading if we are NOT playing. If audio is playing, always show Pause.
                    if (isBuffering && playWhenReady && !isPlaying) {
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
                    shapes = stableShapes,
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
            
            // 🌟 Favorite & Download Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Favorite Button
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
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // Download Button
                if (isLocalOriginal) {
                    // Local file indicator (non-interactive or just info)
                     Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Smartphone,
                                contentDescription = "Local File",
                                tint = onSurfaceVariantColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    OutlinedIconButton(
                        onClick = onDownloadToggle,
                        modifier = Modifier.size(56.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (isDownloaded || isDownloading) primaryColor else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        if (isDownloading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = primaryColor
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                contentDescription = if (isDownloaded) "Downloaded" else "Download",
                                tint = if (isDownloaded) primaryColor else onSurfaceVariantColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        }
                    }
                }
            }
        }
    } // End Box wrapper
}

/**
 * Expressive Queue View
 * 
 * Features:
 * - Matches the player's visual design language
 * - Featured "Now Playing" card with large album art
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
    isLoadingMore: Boolean,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    isDownloaded: (String) -> Boolean,
    isDownloading: (String) -> Boolean,
    isLocalOriginal: (Song) -> Boolean,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    stableShapes: IconButtonShapes
) {
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ========== 1. TOP BAR (Matching Player Style) ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Collapse button - static shape
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
            
            // Queue title pill (matching player's "Now Playing" pill)
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
            
            // Back to player button - static shape
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
        
        // ========== 2. MAIN CONTENT ==========
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 120.dp)
                .padding(horizontal = 24.dp)
        ) {
            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(), 
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ========== FEATURED NOW PLAYING CARD ==========
                    currentSong?.let { song ->
                        item(key = "now_playing_header") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Featured album art (matching player style but smaller)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Shadow layer
                                    Surface(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .offset(y = 4.dp),
                                        shape = RoundedCornerShape(32.dp),
                                        color = primaryContainerColor.copy(alpha = 0.3f),
                                        shadowElevation = 16.dp
                                    ) {}
                                    
                                    // Main album art container
                                    Surface(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clickable { onBackToPlayer() },
                                        shape = RoundedCornerShape(32.dp),
                                        shadowElevation = 8.dp,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (song.albumArtUri != null || song.thumbnailUrl != null) {
                                                AsyncImage(
                                                    model = song.highResThumbnailUrl ?: song.thumbnailUrl ?: song.albumArtUri,
                                                    contentDescription = "Now Playing",
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(RoundedCornerShape(32.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                colors = listOf(
                                                                    primaryContainerColor.copy(alpha = 0.5f),
                                                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                                                )
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MusicNote,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(64.dp),
                                                        tint = onSurfaceVariantColor.copy(alpha = 0.3f)
                                                    )
                                                }
                                            }
                                            
                                            // Gradient overlay for depth
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.1f)
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                }
                                
                                // Song info
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
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Divider with label
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
                    
                    // ========== QUEUE ITEMS ==========
                    itemsIndexed(queue, key = { _, song -> "queue_${song.id}" }) { index, song ->
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
                                // Queue position
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
                                
                                // Thumbnail with expressive rounded corners
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shadowElevation = if (isCurrent) 4.dp else 2.dp
                                ) {
                                    AsyncImage(
                                        model = song.thumbnailUrl ?: song.albumArtUri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp)),
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
                                        tint = if (isCurrent) primaryColor.copy(alpha=0.7f) else onSurfaceVariantColor.copy(alpha=0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                        }
                    }

                    // Load More Button with expressive shape morphing
                    item(key = "load_more_button") {
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
    return String.format("%d:%02d", minutes, seconds)
}
