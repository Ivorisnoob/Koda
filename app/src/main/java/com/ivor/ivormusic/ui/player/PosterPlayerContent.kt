package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.SongArtwork
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Canvas Player - the full-bleed artwork style (replaces the old kinetic
 * type poster; the style key stays "poster").
 *
 * The album art IS the screen: edge-to-edge artwork with a quiet monochrome
 * UI floating over a bottom scrim. Nothing depends on how long the song
 * title is.
 *
 * Signature moves:
 * - The resting screen is pure artwork: while music plays the chrome slips
 *   away after a few seconds and a tap anywhere summons it back.
 * - Swiping the artwork horizontally skips; the art follows the finger,
 *   springs back if abandoned, and the next cover slides in from the swipe
 *   direction.
 * - All controls are white-on-scrim, so they read over any cover.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PosterPlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    // Back is handled once by ExpandablePlayer, which previews the collapse
    // as a gesture instead of firing at the end of one. A BackHandler here
    // would be registered later and silently win.

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    val playerHaptics = rememberPlayerHaptics()
    val styleWheel = LocalPlayerStyleWheelController.current

    // Swipe-to-skip: the artwork follows the finger, springs back if the
    // drag is abandoned, and the incoming cover slides in from the swipe
    // direction.
    val scope = rememberCoroutineScope()
    val swipeDragX = remember { Animatable(0f) }
    var skipDirection by remember { mutableIntStateOf(1) }
    val skipThresholdPx = with(LocalDensity.current) { 90.dp.toPx() }

    // The chrome slips away on its own while music plays; any tap on the
    // canvas summons it back.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(5000)
            controlsVisible = false
        }
    }

    // Monochrome UI over the art: white glyphs, black scrims. Reads over
    // any cover without fighting the artwork's own palette.
    val glyph = Color.White
    val scrim = Color.Black

    // Canvas is white type on black over the artwork, and the picker keeps
    // that: a light themed sheet under a full-bleed cover reads as a different
    // app opening on top of this one.
    val sleepTimer = rememberSleepTimerControl(
        viewModel = viewModel,
        accent = glyph,
        onAccent = scrim,
        container = scrim,
        onContainer = glyph
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Crossfade(targetState = showQueue, label = "CanvasQueueTransition") { queueVisible ->
            if (queueVisible) {
                EditorialQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.skipToSong(song) },
                    onRemoveSong = { index -> viewModel.removeQueueItem(index) },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    field = MaterialTheme.colorScheme.surfaceContainerLowest,
                    accent = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { controlsVisible = true }
                        .pointerInput(showLyrics) {
                            if (showLyrics) return@pointerInput
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch { swipeDragX.snapTo(swipeDragX.value + dragAmount) }
                                },
                                onDragEnd = {
                                    val dx = swipeDragX.value
                                    scope.launch {
                                        if (abs(dx) > skipThresholdPx) {
                                            skipDirection = if (dx < 0) 1 else -1
                                            playerHaptics.skip()
                                            if (dx < 0) viewModel.skipToNext()
                                            else viewModel.skipToPrevious()
                                        }
                                        swipeDragX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { swipeDragX.animateTo(0f, spring()) }
                                }
                            )
                        }
                        // Last in the chain: consumes the post-long-press
                        // hold stream before the tap and swipe detectors.
                        .styleWheelHold(styleWheel)
                ) {
                    // ========== THE CANVAS: full-bleed artwork ==========
                    AnimatedContent(
                        targetState = currentSong,
                        transitionSpec = {
                            val dir = skipDirection
                            (slideInHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) { (it / 3) * dir } + fadeIn()) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                ) { (-it / 3) * dir } + fadeOut())
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = swipeDragX.value * 0.35f },
                        label = "CanvasSongSwitch"
                    ) { song ->
                        if (song != null && (song.thumbnailUrl != null || song.albumArtUri != null)) {
                            SongArtwork(
                                song = song,
                                contentDescription = "Album Art",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(120.dp)
                                )
                            }
                        }
                    }

                    // Scrims fade with the chrome so the resting screen is
                    // pure artwork.
                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (controlsVisible || showLyrics) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "CanvasScrimAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(scrimAlpha)
                            .background(
                                Brush.verticalGradient(
                                    0f to scrim.copy(alpha = 0.35f),
                                    0.25f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1f to scrim.copy(alpha = 0.75f)
                                )
                            )
                    )

                    // ========== LYRICS OVERLAY ==========
                    AnimatedVisibility(
                        visible = showLyrics,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(scrim.copy(alpha = 0.6f))
                                .padding(horizontal = 16.dp)
                        ) {
                            SyncedLyricsView(
                                lyricsResult = lyricsResult,
                                currentPositionMs = progress,
                                onSeekTo = { viewModel.seekTo(it) },
                                primaryColor = glyph,
                                onSurfaceColor = glyph.copy(alpha = 0.9f),
                                onSurfaceVariantColor = glyph.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // ========== TOP BAR ==========
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorialCircleButton(
                                onClick = onCollapse,
                                accent = scrim.copy(alpha = 0.35f),
                                field = glyph,
                                size = 44.dp
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown, "Collapse",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Solid fill while armed - the only contrast
                                // available over artwork is the scrim's alpha.
                                EditorialCircleButton(
                                    onClick = sleepTimer.open,
                                    accent = if (sleepTimer.active) glyph else scrim.copy(alpha = 0.35f),
                                    field = if (sleepTimer.active) scrim else glyph,
                                    size = 44.dp
                                ) {
                                    Icon(
                                        Icons.Rounded.Bedtime, "Sleep timer",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                EditorialCircleButton(
                                    onClick = { showAddToPlaylist = true },
                                    accent = scrim.copy(alpha = 0.35f),
                                    field = glyph,
                                    size = 44.dp
                                ) {
                                    Icon(
                                        Icons.Rounded.PlaylistAdd, "Add to Playlist",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                EditorialCircleButton(
                                    onClick = { showQueue = true },
                                    accent = scrim.copy(alpha = 0.35f),
                                    field = glyph,
                                    size = 44.dp
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ========== BOTTOM CLUSTER ==========
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp)
                        ) {
                            // Title + like/lyrics chips
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentSong?.title
                                            ?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = glyph,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val artistName = currentSong?.artist
                                        ?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"
                                    Text(
                                        text = artistName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = glyph.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = artistName != "Unknown Artist") {
                                                onArtistClick(artistName)
                                            }
                                            .padding(vertical = 2.dp)
                                    )
                                }
                                EditorialChip(
                                    checked = isFavorite,
                                    onClick = { viewModel.toggleCurrentSongLike() },
                                    accent = glyph,
                                    field = scrim
                                ) {
                                    LikeBurstIcon(isFavorite = isFavorite, iconSize = 20.dp)
                                }
                                EditorialChip(
                                    checked = showLyrics,
                                    onClick = { showLyrics = !showLyrics },
                                    accent = glyph,
                                    field = scrim
                                ) {
                                    Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Seek bar + times
                            var scrubFraction by remember { mutableStateOf<Float?>(null) }
                            Slider(
                                value = scrubFraction
                                    ?: if (duration > 0) {
                                        (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    } else 0f,
                                onValueChange = { scrubFraction = it },
                                onValueChangeFinished = {
                                    scrubFraction?.let {
                                        if (duration > 0) viewModel.seekTo((it * duration).toLong())
                                    }
                                    scrubFraction = null
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = glyph,
                                    activeTrackColor = glyph,
                                    inactiveTrackColor = glyph.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatEditorialTime(progress),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glyph.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatEditorialTime(duration),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = glyph.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Floating pill toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = scrim.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        EditorialChip(
                                            checked = shuffleModeEnabled,
                                            onClick = { viewModel.toggleShuffle() },
                                            accent = glyph,
                                            field = scrim
                                        ) {
                                            Icon(
                                                Icons.Default.Shuffle, "Shuffle",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        EditorialCircleButton(
                                            onClick = {
                                                skipDirection = -1
                                                playerHaptics.skip()
                                                viewModel.skipToPrevious()
                                            },
                                            accent = Color.Transparent,
                                            field = glyph,
                                            size = 52.dp
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious, "Previous",
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                        EditorialCircleButton(
                                            onClick = {
                                                playerHaptics.playPause(!viewModel.isPlaying.value)
                                                viewModel.togglePlayPause()
                                            },
                                            accent = glyph,
                                            field = scrim,
                                            size = 64.dp
                                        ) {
                                            if (isBuffering && playWhenReady && !isPlaying) {
                                                LoadingIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    color = scrim,
                                                    polygons = listOf(
                                                        MaterialShapes.SoftBurst,
                                                        MaterialShapes.Cookie9Sided,
                                                        MaterialShapes.Pill,
                                                        MaterialShapes.Sunny
                                                    )
                                                )
                                            } else {
                                                Icon(
                                                    if (isPlaying) Icons.Rounded.Pause
                                                    else Icons.Rounded.PlayArrow,
                                                    if (isPlaying) "Pause" else "Play",
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                        }
                                        EditorialCircleButton(
                                            onClick = {
                                                skipDirection = 1
                                                playerHaptics.skip()
                                                viewModel.skipToNext()
                                            },
                                            accent = Color.Transparent,
                                            field = glyph,
                                            size = 52.dp
                                        ) {
                                            Icon(
                                                Icons.Default.SkipNext, "Next",
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                        EditorialChip(
                                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                                            onClick = { viewModel.toggleRepeat() },
                                            accent = glyph,
                                            field = scrim
                                        ) {
                                            Icon(
                                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                                else Icons.Default.Repeat,
                                                "Repeat",
                                                modifier = Modifier.size(20.dp)
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
