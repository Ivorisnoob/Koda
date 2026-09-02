package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
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
import androidx.media3.common.Player
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.SongArtwork
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Sticker Player - the die-cut playful style.
 *
 * The album art is die-cut into a per-track MaterialShapes sticker slapped
 * at a slight angle onto a flat color-block board. Pure-flat contract: the
 * sticker's "lift" is a thick flat outline ring plus its resting tilt, not
 * a shadow.
 *
 * Signature moves:
 * - The sticker is grabbable: small drags rubber-band back with an
 *   underdamped spring; a committed horizontal drag peels it off screen and
 *   the next track's sticker slaps on with an overshooting entrance.
 * - Tap squashes and stretches the sticker (and toggles playback); the
 *   action commits immediately, the animation follows.
 * - It sways almost imperceptibly while music plays and sits perfectly
 *   straight when paused.
 * - Each track gets its own die-cut shape - the cut is part of the song's
 *   identity.
 *
 * See docs/PLAYER_STYLES_PURE_EXPRESSIVE_CONCEPTS.md section 4.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StickerPlayerSheetContent(
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
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Swipe-to-skip on the song information. The sticker itself keeps its own
    // peel: that gesture throws the art off the canvas and slaps the next one
    // on, which is this style's whole identity and is not a spring-back. Same
    // direction and the same commit, a different animation, on purpose.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // Color-block board: two flat tonal fields meeting on a hard edge.
    val boardTop = MaterialTheme.colorScheme.surfaceContainerLow
    val boardBottom = MaterialTheme.colorScheme.surfaceContainerLowest
    val ink = MaterialTheme.colorScheme.onSurface
    val inkVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val chipColor = MaterialTheme.colorScheme.secondaryContainer
    val onChip = MaterialTheme.colorScheme.onSecondaryContainer

    // Same chip pair the sticker's own controls use, so the picker looks cut
    // from the same sheet.
    val sleepTimer = rememberSleepTimerControl(
        viewModel = viewModel,
        accent = chipColor,
        onAccent = onChip
    )
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.fillMaxSize()) {
        // Board background: hard two-block split, no gradient.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .background(boardTop)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .background(boardBottom)
            )
        }

        Crossfade(targetState = showQueue, label = "StickerQueueTransition") { queueVisible ->
            if (queueVisible) {
                EditorialQueueView(
                    queue = currentQueue,
                    currentQueueItemId = currentQueueItemId,
                    onQueueItemClick = { item -> viewModel.skipToQueueItem(item.id) },
                    onRemoveItem = { item -> viewModel.removeQueueItem(item.id) },
                    onMoveSong = { from, to -> viewModel.moveQueueItem(from, to, persist = false) },
                    onCommitOrder = { viewModel.commitQueueOrder() },
                    onUndoRemove = { viewModel.undoQueueRemoval() },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    field = boardBottom,
                    accent = ink
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ========== TOP BAR ==========
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
                            accent = chipColor,
                            field = onChip,
                            size = 44.dp
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MusicCastIconButton(
                                containerColor = chipColor,
                                contentColor = onChip
                            )
                            EditorialCircleButton(
                                onClick = sleepTimer.open,
                                accent = if (sleepTimer.active) ink else chipColor,
                                field = if (sleepTimer.active) chipColor else onChip,
                                size = 44.dp
                            ) {
                                Icon(
                                    Icons.Rounded.Bedtime, "Sleep timer",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            EditorialCircleButton(
                                onClick = { showAddToPlaylist = true },
                                accent = chipColor,
                                field = onChip,
                                size = 44.dp
                            ) {
                                Icon(
                                    Icons.Rounded.PlaylistAdd, "Add to Playlist",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            EditorialCircleButton(
                                onClick = { showQueue = true },
                                accent = chipColor,
                                field = onChip,
                                size = 44.dp
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // ========== THE STICKER ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Crossfade(targetState = showLyrics, label = "StickerLyrics") { lyricsVisible ->
                            if (lyricsVisible) {
                                SyncedLyricsView(
                                    lyricsResult = lyricsResult,
                                    currentPositionMs = progress,
                                    isPlaying = isPlaying,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    primaryColor = accent,
                                    onSurfaceColor = ink,
                                    onSurfaceVariantColor = inkVariant
                                )
                            } else {
                                DraggableSticker(
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering && playWhenReady && !isPlaying,
                                    onPlayPause = {
                                        playerHaptics.playPause(!viewModel.isPlaying.value)
                                        viewModel.togglePlayPause()
                                    },
                                    onLike = { viewModel.toggleCurrentSongLike() },
                                    onNext = { playerHaptics.skip(); viewModel.skipToNext() },
                                    onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() },
                                    ringColor = chipColor,
                                    placeholderColor = chipColor,
                                    placeholderIconColor = onChip
                                )
                            }
                        }
                    }

                    // ========== TITLE / ARTIST ==========
                    // Wrapped so the song information is one swipe target the
                    // full width of the player, not two text-shaped ones.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .swipeToSkip(swipeToSkip)
                            .swipeToSkipFollow(swipeToSkip)
                    ) {
                        Text(
                            text = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                            color = ink,
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
                            text = artistName.uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                            color = inkVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = artistName != "Unknown Artist") {
                                    onArtistClick(artistName)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ========== PROGRESS ==========
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
                            label = "StickerProgress"
                        )
                        val lineStroke = Stroke(
                            width = with(LocalDensity.current) { 4.dp.toPx() },
                            cap = StrokeCap.Round
                        )
                        Box(contentAlignment = Alignment.Center) {
                            LinearWavyProgressIndicator(
                                progress = { animatedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp),
                                color = accent,
                                trackColor = inkVariant.copy(alpha = 0.25f),
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
                                color = inkVariant
                            )
                            Text(
                                text = formatEditorialTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = inkVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // ========== CHIPS ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        EditorialCircleButton(
                            onClick = { playerHaptics.skip(); viewModel.skipToPrevious() },
                            accent = chipColor,
                            field = onChip,
                            size = 56.dp
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious, "Previous",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        EditorialCircleButton(
                            onClick = { playerHaptics.skip(); viewModel.skipToNext() },
                            accent = chipColor,
                            field = onChip,
                            size = 56.dp
                        ) {
                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(28.dp))
                        }
                        EditorialChip(
                            checked = isFavorite,
                            onClick = { viewModel.toggleCurrentSongLike() },
                            accent = chipColor,
                            field = onChip
                        ) {
                            LikeBurstIcon(isFavorite = isFavorite, iconSize = 20.dp)
                        }
                        EditorialChip(
                            checked = shuffleModeEnabled,
                            onClick = { viewModel.toggleShuffle() },
                            accent = chipColor,
                            field = onChip
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(20.dp))
                        }
                        EditorialChip(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onClick = { viewModel.toggleRepeat() },
                            accent = chipColor,
                            field = onChip
                        ) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                else Icons.Default.Repeat,
                                "Repeat",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        EditorialChip(
                            checked = showLyrics,
                            onClick = { showLyrics = !showLyrics },
                            accent = chipColor,
                            field = onChip
                        ) {
                            Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .height(8.dp)
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

/**
 * The grabbable sticker. Tap = play/pause with squash-and-stretch,
 * double-tap = like with a pop, small drags rubber-band home, committed
 * horizontal drags peel the sticker away and skip.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DraggableSticker(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: () -> Unit,
    onLike: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    ringColor: Color,
    placeholderColor: Color,
    placeholderIconColor: Color
) {
    val dieCuts = remember {
        listOf(
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Flower,
            MaterialShapes.Gem,
            MaterialShapes.Puffy
        )
    }
    val polygon = remember(currentSong?.id) {
        dieCuts[abs(currentSong?.id?.hashCode() ?: 0) % dieCuts.size]
    }
    val shape = remember(polygon) { EditorialPolygonShape(polygon) }

    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    var peeling by remember { mutableStateOf(false) }

    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnPlayPause by rememberUpdatedState(onPlayPause)
    val currentOnLike by rememberUpdatedState(onLike)
    val styleWheel = LocalPlayerStyleWheelController.current

    // New track: the fresh sticker slaps on with an overshooting entrance.
    LaunchedEffect(currentSong?.id) {
        scaleX.snapTo(0.7f)
        scaleY.snapTo(0.7f)
        launch {
            scaleX.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
        scaleY.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }

    // Sway while playing, sit straight when paused. The spring chases a
    // moving target while playing so the pause settle is smooth.
    val infinite = rememberInfiniteTransition(label = "StickerSway")
    val sway by infinite.animateFloat(
        initialValue = -2f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "StickerSwayValue"
    )
    val tilt by animateFloatAsState(
        targetValue = if (isPlaying) sway else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "StickerTilt"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (maxWidth < 50.dp || maxHeight < 50.dp) return@BoxWithConstraints
        val stickerSize = minOf(maxWidth, maxHeight) * 0.78f
        val peelThresholdPx = with(LocalDensity.current) { 110.dp.toPx() }
        val peelDistancePx = with(LocalDensity.current) { maxWidth.toPx() * 1.2f }

        Box(
            modifier = Modifier
                .size(stickerSize)
                .graphicsLayer {
                    translationX = offset.value.x
                    translationY = offset.value.y
                    rotationZ = tilt + offset.value.x / 40f
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                }
                .border(width = 7.dp, color = ringColor, shape = shape)
                .clip(shape)
                .background(placeholderColor)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            currentOnPlayPause()
                            scope.launch {
                                scaleX.snapTo(1.12f)
                                scaleY.snapTo(0.86f)
                                launch {
                                    scaleX.animateTo(
                                        1f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                scaleY.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDoubleTap = {
                            currentOnLike()
                            scope.launch {
                                scaleX.snapTo(1.18f)
                                scaleY.snapTo(1.18f)
                                launch {
                                    scaleX.animateTo(
                                        1f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                scaleY.animateTo(
                                    1f,
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (peeling) return@detectDragGestures
                            scope.launch { offset.snapTo(offset.value + dragAmount) }
                        },
                        onDragEnd = {
                            if (peeling) return@detectDragGestures
                            val dx = offset.value.x
                            if (abs(dx) > peelThresholdPx) {
                                peeling = true
                                scope.launch {
                                    // Peel away in the drag direction, skip,
                                    // then let the new sticker slap on.
                                    val direction = if (dx > 0) 1f else -1f
                                    offset.animateTo(
                                        Offset(direction * peelDistancePx, offset.value.y),
                                        tween(durationMillis = 220)
                                    )
                                    if (direction > 0) currentOnPrevious() else currentOnNext()
                                    offset.snapTo(Offset.Zero)
                                    peeling = false
                                }
                            } else {
                                scope.launch {
                                    // Rubber-band home, underdamped on purpose.
                                    offset.animateTo(
                                        Offset.Zero,
                                        spring(
                                            dampingRatio = 0.35f,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (!peeling) {
                                scope.launch { offset.animateTo(Offset.Zero, spring()) }
                            }
                        }
                    )
                }
                // Last in the chain: consumes the post-long-press hold
                // stream before the tap and drag detectors see it.
                .styleWheelHold(styleWheel),
            contentAlignment = Alignment.Center
        ) {
            val artSong = currentSong?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
            if (artSong != null) {
                SongArtwork(
                    song = artSong,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = placeholderIconColor,
                    modifier = Modifier.size(stickerSize * 0.3f)
                )
            }
            if (isBuffering) {
                LoadingIndicator(
                    modifier = Modifier.size(40.dp),
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
