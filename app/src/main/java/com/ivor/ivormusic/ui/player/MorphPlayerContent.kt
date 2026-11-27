package com.ivor.ivormusic.ui.player

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
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.media3.common.Player
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    onArtistClick: (String) -> Unit = {},
    /** Hand this song to the video player; null where there is no video pipeline. */
    onWatchAsVideo: (() -> Unit)? = null,
    onAlbumClick: (String) -> Unit = {},
    onOpenAlbum: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {}
) {
    // Back is handled once by ExpandablePlayer, which previews the collapse
    // as a gesture instead of firing at the end of one. A BackHandler here
    // would be registered later and silently win.

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playerHaptics = rememberPlayerHaptics()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    var showQueue by rememberPlayerQueueOpen()
    var showLyrics by remember { mutableStateOf(false) }

    // Swipe-to-skip, shared by the morphing hero and the title/artist block
    // so both commit at the same threshold with the same spring home.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )
    var showOptions by rememberNowPlayingOptionsOpen()

    // Morph runs on the theme's own roles, so the picker needs no overrides.
    val sleepTimer = rememberSleepTimerControl(viewModel = viewModel)
    var controlsVisible by remember { mutableStateOf(true) }

    val surfaceColor = MaterialTheme.colorScheme.background
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    // Controls are guests: summoned by a tap, they slip away while music
    // plays.
    // Any touch on the player counts as using the controls: each press
    // restarts the wait, and nothing hides while a finger is down (a scrub,
    // a held button). Before, the 4.5s ran from when the controls appeared,
    // so they left in the middle of being used.
    var touchCount by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var fingerDown by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, isPlaying, touchCount, fingerDown) {
        if (controlsVisible && isPlaying && !fingerDown) {
            delay(4500)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
            // Watches every press on its way down to the controls, consuming
            // nothing, so buttons and the scrubber behave exactly as before.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    fingerDown = true
                    touchCount++
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        fingerDown = false
                        touchCount++
                    }
                }
            }
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
                    currentQueueItemId = currentQueueItemId,
                    onQueueItemClick = { item -> viewModel.skipToQueueItem(item.id) },
                    onRemoveItem = { item -> viewModel.removeQueueItem(item.id) },
                    onMoveSong = { from, to -> viewModel.moveQueueItem(from, to) },
                    onUndoRemove = { viewModel.undoQueueRemoval() },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    onSaveQueue = { name, description, onSaved ->
                        viewModel.saveQueueAsPlaylist(name, description, onSaved)
                    },
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
                        MorphUtilityButton(
                            onClick = onCollapse,
                            modifier = Modifier.size(44.dp).captureAs("morph.collapse", 20) { morphButton(LayoutAction.COLLAPSE) }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MorphUtilityButton(
                                onClick = sleepTimer.open,
                                active = sleepTimer.active,
                                modifier = Modifier.size(44.dp).captureAs("morph.sleep", 21) { morphButton(LayoutAction.SLEEP_TIMER) }
                            ) {
                                Icon(
                                    Icons.Rounded.Bedtime, "Sleep timer",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            MorphUtilityButton(
                                onClick = { showLyrics = !showLyrics },
                                modifier = Modifier.size(44.dp).captureAs("morph.lyrics", 22) { morphButton(LayoutAction.LYRICS) }
                            ) {
                                Icon(
                                    Icons.Rounded.Lyrics, "Lyrics",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            MorphUtilityButton(
                                onClick = { showQueue = true },
                                modifier = Modifier.size(44.dp).captureAs("morph.queue", 23) { morphButton(LayoutAction.QUEUE) }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            MorphUtilityButton(
                                onClick = { showOptions = true },
                                modifier = Modifier.size(44.dp).captureAs("morph.options", 24) { morphButton(LayoutAction.OPTIONS) }
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert, "More options",
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
                            .captureAs("morph.art", 0) {
                                captureBlock(LayoutBlockKind.COVER, LayoutSkin.MORPH) {
                                    copy(action = LayoutAction.PLAY_PAUSE)
                                }
                            }
                    ) {
                        Crossfade(targetState = showLyrics, label = "MorphLyrics") { lyricsVisible ->
                            if (lyricsVisible) {
                                SyncedLyricsView(
                                    lyricsResult = lyricsResult,
                                    currentPositionMs = progress,
                                    isPlaying = isPlaying,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    primaryColor = primaryColor,
                                    onSurfaceColor = onSurfaceColor,
                                    onSurfaceVariantColor = onSurfaceVariantColor
                                )
                            } else {
                                MorphHero(
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering && playWhenReady && !isPlaying,
                                    progressFraction = if (duration > 0) {
                                        (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    } else 0f,
                                    onPlayPause = {
                                        playerHaptics.playPause(!viewModel.isPlaying.value)
                                        viewModel.togglePlayPause()
                                    },
                                    swipeToSkip = swipeToSkip,
                                    ringColor = primaryColor,
                                    ringTrackColor = onSurfaceVariantColor.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // ========== TITLE / ARTIST ==========
                    // Wrapped so the song information is one swipe target the
                    // full width of the player, not two text-shaped ones.
                    MorphTitle(
                        song = currentSong,
                        onArtistClick = onArtistClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("morph.title", 10) {
                                captureBlock(LayoutBlockKind.TITLE, LayoutSkin.MORPH)
                            }
                            .swipeToSkip(swipeToSkip)
                            .swipeToSkipFollow(swipeToSkip)
                    )

                    // ========== SCRUB LINE ==========
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .captureAs("morph.scrubber", 30) {
                                captureBlock(LayoutBlockKind.SCRUBBER, LayoutSkin.MORPH) {
                                    copy(showDetail = true, motion = true)
                                }
                            }
                    ) {
                        var scrubPosition by remember { mutableStateOf<Float?>(null) }
                        val displayedProgress = scrubPosition?.toLong() ?: progress
                        val scrubReturnPoint = LocalScrubReturnPoint.current
                        // Advanced from the clock each frame rather than sprung
                        // toward a once-a-second sample - see
                        // rememberSmoothProgress.
                        val smoothProgress = rememberSmoothProgress(
                            positionMs = progress,
                            durationMs = duration,
                            isPlaying = isPlaying,
                            scrubPositionMs = scrubPosition
                        )
                        val lineStroke = Stroke(
                            width = with(LocalDensity.current) { 3.dp.toPx() },
                            cap = StrokeCap.Round
                        )
                        Box(contentAlignment = Alignment.Center) {
                            ExpressiveScrubber(
                                progress = { smoothProgress.value },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(scrubberTrackHeight(12.dp)),
                                color = primaryColor,
                                trackColor = onSurfaceVariantColor.copy(alpha = 0.15f),
                                stroke = lineStroke,
                                trackStroke = lineStroke,
                                amplitude = { if (isPlaying) 1f else 0f }
                            )
                            Slider(
                                interactionSource = LocalPlayerScrubInteraction.current,
                                value = scrubPosition ?: progress.toFloat(),
                                // Through the return point: the drag leaves a marker where it
                                // started and snaps back onto it, and letting go on the marker
                                // is "never mind" rather than a seek. See ScrubReturnPoint.
                                onValueChange = {
                                    scrubPosition = scrubReturnPoint.follow(
                                        value = it,
                                        rangeEnd = duration.toFloat().coerceAtLeast(1f)
                                    ) { smoothProgress.value }
                                },
                                onValueChangeFinished = {
                                    val cancelled = scrubReturnPoint.release()
                                    if (!cancelled) scrubPosition?.let { viewModel.seekTo(it.toLong()) }
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

                    PlayerVisualizerSlot(
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("morph.visualizer", 31) {
                                captureBlock(LayoutBlockKind.VISUALIZER, LayoutSkin.KODA)
                            }
                    )

                    // ========== SUMMONED FLOATING TOOLBAR ==========
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MorphToolbar(
                            isFavorite = isFavorite,
                            shuffleModeEnabled = shuffleModeEnabled,
                            repeatMode = repeatMode,
                            onLike = { viewModel.toggleCurrentSongLike() },
                            onShuffle = { viewModel.toggleShuffle() },
                            onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() },
                            onNext = { playerHaptics.skip(); viewModel.skipToNext() },
                            onRepeat = { viewModel.toggleRepeat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                                .captureAs("morph.toolbar", 40) {
                                    captureBlock(LayoutBlockKind.TOOLBAR, LayoutSkin.MORPH)
                                }
                        )
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

    // The overflow menu. Hosted here rather than above the style dispatch so
    // it sits inside this player's own composition, and gated on a song so the
    // sheet can never open against nothing.
    if (showOptions) {
        currentSong?.let { song ->
            NowPlayingOptionsSheet(
                song = song,
                viewModel = viewModel,
                onDismiss = { showOptions = false },
                onArtistClick = onArtistClick,
                onWatchAsVideo = onWatchAsVideo,
                onAlbumClick = onAlbumClick,
                onOpenAlbum = onOpenAlbum
            )
        }
    }
}

@Composable
internal fun MorphUtilityButton(
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier.size(44.dp),
    content: @Composable () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
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
internal fun MorphHero(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressFraction: Float,
    onPlayPause: () -> Unit,
    /** Null where the hero takes no swipe: the layout editor, or a layout that turned it off. */
    swipeToSkip: SwipeToSkipState?,
    ringColor: Color,
    ringTrackColor: Color,
    motionEligible: Boolean = true
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
    // The gesture itself is the shared contract; only the hero's reaction to
    // it (tilt, stretch) is Morph's own.
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    val styleWheel = LocalPlayerStyleWheelController.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (maxWidth < 50.dp || maxHeight < 50.dp) return@BoxWithConstraints
        val heroSize = minOf(maxWidth, maxHeight) * 0.72f
        val ringSize = heroSize + 40.dp

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
                    val dx = swipeToSkip?.offset ?: 0f
                    translationX = dx * SwipeToSkipDefaults.ArtFollow
                    rotationZ = tilt + dx / 60f
                    scaleX = breath * (1f + (abs(dx) / 2500f).coerceAtMost(0.08f))
                    scaleY = breath
                }
                .clip(heroShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .coverTaps(LocalCoverSeek.current) { currentOnPlayPause() }
                .then(if (swipeToSkip != null) Modifier.swipeToSkip(swipeToSkip) else Modifier)
                // Last in the chain: consumes the post-long-press hold
                // stream before the tap and skip-drag detectors see it.
                .styleWheelHold(styleWheel),
            contentAlignment = Alignment.Center
        ) {
            val artSong = currentSong?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
            if (artSong != null) {
                PlayerArtwork(
                    song = artSong,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    motionEligible = motionEligible
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

private fun morphButton(action: LayoutAction) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.MORPH) { copy(action = action, look = ButtonLook.TONAL) }

/* ------------------------------------------------------------------ */
/* Pieces, shared with blocks in Morph's skin                          */
/* ------------------------------------------------------------------ */

/** The title centred under the living shape, the artist in the accent. */
@Composable
internal fun MorphTitle(
    song: Song?,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    artistEnabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = song?.title?.takeIf { !isUnknownTitle(it) } ?: "Untitled",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
        val artistName = song?.artist?.takeIf { !isUnknownArtist(it) } ?: "Unknown Artist"
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = artistEnabled && artistName != "Unknown Artist") {
                    onArtistClick(artistName)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** The floating toolbar Morph summons: Like as its button, then shuffle, the skips and repeat. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun MorphToolbar(
    isFavorite: Boolean,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onLike: () -> Unit,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(onClick = onLike) {
                    LikeBurstIcon(isFavorite = isFavorite)
                }
            },
            content = {
                IconToggleButton(
                    checked = shuffleModeEnabled,
                    onCheckedChange = { onShuffle() }
                ) {
                    Icon(Icons.Default.Shuffle, "Shuffle")
                }
                androidx.compose.material3.IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, "Previous")
                }
                androidx.compose.material3.IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, "Next")
                }
                IconToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = { onRepeat() }
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

/* ------------------------------------------------------------------ */
/* Morph's skin for custom layouts                                     */
/* ------------------------------------------------------------------ */

/**
 * A block drawn by Morph's own pieces. The cover is the living shape inside
 * its wavy progress ring; every button is the round utility button, lit while
 * on; the toolbar is the one Morph summons, here always in view.
 */
@Composable
internal fun MorphSkinBlock(
    block: LayoutBlock,
    host: LayoutHost,
    motionArtwork: Boolean,
    fallback: @Composable (LayoutInk) -> Unit,
) {
    val viewModel = host.viewModel
    val song by viewModel.currentSong.collectAsState()
    val scheme = MaterialTheme.colorScheme
    when (block.kind) {
        LayoutBlockKind.COVER -> {
            val isPlaying by viewModel.isPlaying.collectAsState()
            val isBuffering by viewModel.isBuffering.collectAsState()
            val playWhenReady by viewModel.playWhenReady.collectAsState()
            val progress by viewModel.progress.collectAsState()
            val duration by viewModel.duration.collectAsState()
            val showLyrics = host.lyricsOpen && motionArtwork
            val swipe = host.swipeToSkip?.takeIf { host.interactive && block.swipeToSkip }
            Box(Modifier.fillMaxSize()) {
                Crossfade(targetState = showLyrics, label = "MorphSkinArtLyrics") { lyrics ->
                    if (lyrics) {
                        CoverLyrics(
                            host = host,
                            primary = scheme.primary,
                            ink = scheme.onSurface,
                            inkVariant = scheme.onSurfaceVariant
                        )
                    } else {
                        MorphHero(
                            currentSong = song,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering && playWhenReady && !isPlaying,
                            progressFraction = if (duration > 0) {
                                (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            } else 0f,
                            onPlayPause = rememberBlockAction(block, host),
                            swipeToSkip = swipe,
                            ringColor = scheme.primary,
                            ringTrackColor = scheme.onSurfaceVariant.copy(alpha = 0.2f),
                            motionEligible = motionArtwork
                        )
                    }
                }
            }
        }
        LayoutBlockKind.TITLE -> SkinTitleFrame(block, host) { frame ->
            MorphTitle(
                song = song,
                onArtistClick = host.onArtistClick,
                artistEnabled = host.interactive,
                modifier = frame
            )
        }
        LayoutBlockKind.SCRUBBER -> LayoutScrubber(
            host = host,
            color = scheme.primary,
            trackColor = scheme.onSurfaceVariant.copy(alpha = 0.15f),
            timeColor = scheme.onSurfaceVariant,
            stroke = 3.dp,
            trackHeight = 12.dp,
            flat = !block.motion,
            showTimes = block.showDetail,
            verticalArrangement = Arrangement.Top
        )
        LayoutBlockKind.TOOLBAR -> {
            val liked by viewModel.isCurrentSongLiked.collectAsState()
            val shuffle by viewModel.shuffleModeEnabled.collectAsState()
            val repeatMode by viewModel.repeatMode.collectAsState()
            MorphToolbar(
                isFavorite = liked,
                shuffleModeEnabled = shuffle,
                repeatMode = repeatMode,
                onLike = { host.onAction(LayoutAction.LIKE) },
                onShuffle = { host.onAction(LayoutAction.SHUFFLE) },
                onPrevious = { host.onAction(LayoutAction.PREVIOUS) },
                onNext = { host.onAction(LayoutAction.NEXT) },
                onRepeat = { host.onAction(LayoutAction.REPEAT) },
                modifier = Modifier.fillMaxSize()
            )
        }
        LayoutBlockKind.BUTTON -> {
            val action = block.action
            val state = rememberLayoutActionState(action, host)
            MorphUtilityButton(
                onClick = rememberBlockAction(block, host),
                active = state.checked == true || block.look == ButtonLook.FILLED,
                modifier = Modifier.fillMaxSize()
            ) {
                LayoutActionIcon(
                    action, state, action.label(),
                    Modifier.layoutIcon(if (action == LayoutAction.COLLAPSE) 26.dp else 22.dp),
                    ActionGlyphs.FILLED
                )
            }
        }
        else -> fallback(LayoutInk(scheme.background, scheme.onSurface))
    }
}
