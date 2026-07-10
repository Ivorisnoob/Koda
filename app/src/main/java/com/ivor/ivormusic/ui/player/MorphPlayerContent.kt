package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Morph Player - one living shape.
 *
 * The album art lives inside a large MaterialShapes polygon that breathes,
 * tilts and continuously morphs through organic cuts while music plays, and
 * settles into a circle at rest. The shape IS the play/pause indicator; a
 * wavy progress ring wraps it and flattens in silence. Conventional
 * controls only exist as a summonable floating toolbar.
 *
 * Unlike the pure-flat styles, Morph keeps the ambient ChromaticMist layer:
 * color is borrowed from the music.
 *
 * See docs/PLAYER_STYLE_MORPH_RESEARCH.md.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MorphPlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    BackHandler(enabled = true) { onCollapse() }

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

    val surfaceColor = MaterialTheme.colorScheme.background
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    // Controls are guests: summoned by a tap, they slip away while music
    // plays.
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4500)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = true }
    ) {
        val albumArtUrl = currentSong?.highResThumbnailUrl
            ?: currentSong?.thumbnailUrl
            ?: currentSong?.albumArtUri?.toString()
        ChromaticMistBackground(
            albumArtUrl = albumArtUrl,
            enabled = ambientBackground && !showQueue,
            modifier = Modifier.fillMaxSize()
        )

        Crossfade(targetState = showQueue, label = "MorphQueueTransition") { queueVisible ->
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
                    field = surfaceColor,
                    accent = onSurfaceColor
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ========== TOP BAR ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MorphUtilityButton(onClick = onCollapse) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MorphUtilityButton(onClick = { showLyrics = !showLyrics }) {
                                Icon(
                                    Icons.Rounded.Lyrics, "Lyrics",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            MorphUtilityButton(onClick = { showAddToPlaylist = true }) {
                                Icon(
                                    Icons.Rounded.PlaylistAdd, "Add to Playlist",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            MorphUtilityButton(onClick = { showQueue = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // ========== HERO SHAPE / LYRICS ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Crossfade(targetState = showLyrics, label = "MorphLyrics") { lyricsVisible ->
                            if (lyricsVisible) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp)
                                ) {
                                    SyncedLyricsView(
                                        lyricsResult = lyricsResult,
                                        currentPositionMs = progress,
                                        onSeekTo = { viewModel.seekTo(it) },
                                        ambientBackground = ambientBackground,
                                        primaryColor = primaryColor,
                                        onSurfaceColor = onSurfaceColor,
                                        onSurfaceVariantColor = onSurfaceVariantColor
                                    )
                                }
                            } else {
                                MorphHero(
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering && playWhenReady && !isPlaying,
                                    progressFraction = if (duration > 0) {
                                        (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    } else 0f,
                                    onPlayPause = { viewModel.togglePlayPause() },
                                    onNext = { viewModel.skipToNext() },
                                    onPrevious = { viewModel.skipToPrevious() },
                                    ringColor = primaryColor,
                                    ringTrackColor = onSurfaceVariantColor.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // ========== TITLE / ARTIST ==========
                    Text(
                        text = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = onSurfaceColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                    val artistName = currentSong?.artist?.takeIf { !it.startsWith("Unknown") }
                        ?: "Unknown Artist"
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryColor.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = artistName != "Unknown Artist") {
                                onArtistClick(artistName)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // ========== SCRUB LINE ==========
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        var scrubPosition by remember { mutableStateOf<Float?>(null) }
                        val displayedProgress = scrubPosition?.toLong() ?: progress
                        val fraction = if (duration > 0) {
                            displayedProgress.toFloat() / duration.toFloat()
                        } else 0f
                        val animatedFraction by animateFloatAsState(
                            targetValue = fraction.coerceIn(0f, 1f),
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "MorphScrub"
                        )
                        val lineStroke = Stroke(
                            width = with(LocalDensity.current) { 3.dp.toPx() },
                            cap = StrokeCap.Round
                        )
                        Box(contentAlignment = Alignment.Center) {
                            LinearWavyProgressIndicator(
                                progress = { animatedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp),
                                color = primaryColor,
                                trackColor = onSurfaceVariantColor.copy(alpha = 0.15f),
                                stroke = lineStroke,
                                trackStroke = lineStroke,
                                amplitude = { if (isPlaying) 1f else 0f }
                            )
                            Slider(
                                value = scrubPosition ?: progress.toFloat(),
                                onValueChange = { scrubPosition = it },
                                onValueChangeFinished = {
                                    scrubPosition?.let { viewModel.seekTo(it.toLong()) }
                                    scrubPosition = null
                                },
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
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatEditorialTime(displayedProgress),
                                style = MaterialTheme.typography.labelMedium,
                                color = onSurfaceVariantColor
                            )
                            Text(
                                text = formatEditorialTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = onSurfaceVariantColor
                            )
                        }
                    }

                    // ========== SUMMONED FLOATING TOOLBAR ==========
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            HorizontalFloatingToolbar(
                                expanded = true,
                                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                                floatingActionButton = {
                                    FloatingToolbarDefaults.StandardFloatingActionButton(
                                        onClick = { viewModel.toggleCurrentSongLike() }
                                    ) {
                                        LikeBurstIcon(isFavorite = isFavorite)
                                    }
                                },
                                content = {
                                    IconToggleButton(
                                        checked = shuffleModeEnabled,
                                        onCheckedChange = { viewModel.toggleShuffle() }
                                    ) {
                                        Icon(Icons.Default.Shuffle, "Shuffle")
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = { viewModel.skipToPrevious() }
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, "Previous")
                                    }
                                    androidx.compose.material3.IconButton(
                                        onClick = { viewModel.skipToNext() }
                                    ) {
                                        Icon(Icons.Default.SkipNext, "Next")
                                    }
                                    IconToggleButton(
                                        checked = repeatMode != Player.REPEAT_MODE_OFF,
                                        onCheckedChange = { viewModel.toggleRepeat() }
                                    ) {
                                        Icon(
                                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                            else Icons.Default.Repeat,
                                            "Repeat"
                                        )
                                    }
                                }
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(6.dp)
                    )
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

@Composable
private fun MorphUtilityButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.size(44.dp)
    ) {
        content()
    }
}

/**
 * The living hero: album art clipped by a shape that cycles through
 * organic MaterialShapes cuts while playing and settles to a circle at
 * rest, wrapped by a wavy progress ring that flattens in silence.
 *
 * Each morph segment runs to completion (never cancelled mid-flight), so
 * a pause settles at the next segment boundary while tilt, breath and the
 * ring respond instantly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphHero(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressFraction: Float,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    ringColor: Color,
    ringTrackColor: Color
) {
    val cycleShapes = remember {
        listOf(
            MaterialShapes.Cookie12Sided,
            MaterialShapes.SoftBurst,
            MaterialShapes.Sunny,
            MaterialShapes.Puffy,
            MaterialShapes.Cookie9Sided
        )
    }
    val circle = remember { MaterialShapes.Circle }
    var morphPair by remember { mutableStateOf(circle to circle) }
    val morphProgress = remember { Animatable(1f) }
    val playingState = rememberUpdatedState(isPlaying)

    LaunchedEffect(Unit) {
        var index = 0
        while (isActive) {
            val target = if (playingState.value) {
                cycleShapes[index++ % cycleShapes.size]
            } else {
                circle
            }
            if (target !== morphPair.second) {
                morphPair = morphPair.second to target
                morphProgress.snapTo(0f)
                morphProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing)
                )
            } else {
                delay(150)
            }
        }
    }
    val morph = remember(morphPair) { Morph(morphPair.first, morphPair.second) }
    val heroShape = remember(morph, morphProgress.value) {
        EditorialMorphShape(morph, morphProgress.value)
    }

    // Breath and tilt: springs chase a moving target while playing and a
    // fixed one when paused, so silence settles smoothly and immediately.
    val infinite = rememberInfiniteTransition(label = "MorphLife")
    val breathValue by infinite.animateFloat(
        initialValue = 0.975f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MorphBreathValue"
    )
    val tiltValue by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "MorphTiltValue"
    )
    val breath by animateFloatAsState(
        targetValue = if (isPlaying) breathValue else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "MorphBreath"
    )
    val tilt by animateFloatAsState(
        targetValue = if (isPlaying) tiltValue else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "MorphTilt"
    )

    // Horizontal drag: stretch toward the skip, spring back if abandoned.
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (maxWidth < 50.dp || maxHeight < 50.dp) return@BoxWithConstraints
        val heroSize = minOf(maxWidth, maxHeight) * 0.72f
        val ringSize = heroSize + 40.dp
        val skipThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }

        CircularWavyProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier.size(ringSize),
            color = ringColor,
            trackColor = ringTrackColor,
            amplitude = { if (isPlaying) 1f else 0f }
        )

        Box(
            modifier = Modifier
                .size(heroSize)
                .graphicsLayer {
                    translationX = dragX.value * 0.5f
                    rotationZ = tilt + dragX.value / 60f
                    scaleX = breath * (1f + (abs(dragX.value) / 2500f).coerceAtMost(0.08f))
                    scaleY = breath
                }
                .clip(heroShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnPlayPause() })
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { dragX.snapTo(dragX.value + dragAmount) }
                        },
                        onDragEnd = {
                            val dx = dragX.value
                            scope.launch {
                                if (abs(dx) > skipThresholdPx) {
                                    if (dx < 0) currentOnNext() else currentOnPrevious()
                                }
                                dragX.animateTo(
                                    0f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            scope.launch { dragX.animateTo(0f, spring()) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val artModel = currentSong?.highResThumbnailUrl
                ?: currentSong?.thumbnailUrl
                ?: currentSong?.albumArtUri
            if (artModel != null) {
                AsyncImage(
                    model = artModel,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(heroSize * 0.3f)
                )
            }
            if (isBuffering) {
                LoadingIndicator(
                    modifier = Modifier.size(44.dp),
                    color = ringColor,
                    polygons = listOf(
                        MaterialShapes.SoftBurst,
                        MaterialShapes.Cookie9Sided,
                        MaterialShapes.Pill,
                        MaterialShapes.Sunny
                    )
                )
            }
        }
    }
}
