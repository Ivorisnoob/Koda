package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.media3.common.Player
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.QueueDragHandle
import com.ivor.ivormusic.ui.components.QueueRowContainer
import com.ivor.ivormusic.ui.components.queueDragLongPress
import com.ivor.ivormusic.ui.components.queueRowKeys
import com.ivor.ivormusic.ui.components.rememberQueueRemoval
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import com.ivor.ivormusic.ui.components.SongArtwork
import kotlin.math.abs

/**
 * Editorial Player - the two-tone magazine style.
 *
 * Modeled on Google's Material 3 Expressive announcement mock (the "Serafina"
 * player screen). Pure-flat contract: no gradients, no shadows, no scrims.
 * Exactly two colors plus the artwork - a flat field (primaryContainer) and
 * one accent (onPrimaryContainer) carry every element. Hierarchy comes from
 * size and shape only.
 *
 * Signature moves:
 * - Album art die-cut into a per-track MaterialShapes polygon that morphs to
 *   a new cut on every track change.
 * - Display serif italic headline, re-typeset per track like a new spread.
 * - PLAY/PAUSE word pill: the label is the icon.
 * - Wavy-played / flat-remaining progress line whose wave settles on pause.
 *
 * See docs/PLAYER_STYLES_PURE_EXPRESSIVE_CONCEPTS.md section 5.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorialPlayerSheetContent(
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
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    // The two-tone contract: field + accent, nothing else.
    val field = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.onPrimaryContainer

    // The picker follows the same two tones, so it reads as another page of
    // this player rather than a sheet borrowed from somewhere else.
    val sleepTimer = rememberSleepTimerControl(
        viewModel = viewModel,
        accent = accent,
        onAccent = field,
        container = field,
        onContainer = accent
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(field)
    ) {
        Crossfade(targetState = showQueue, label = "EditorialQueueTransition") { queueVisible ->
            if (queueVisible) {
                EditorialQueueView(
                    queue = currentQueue,
                    currentSong = currentSong,
                    onSongClick = { song -> viewModel.skipToSong(song) },
                    onRemoveSong = { index -> viewModel.removeQueueItem(index) },
                    onMoveSong = { from, to -> viewModel.moveQueueItem(from, to, persist = false) },
                    onCommitOrder = { viewModel.commitQueueOrder() },
                    onUndoRemove = { viewModel.undoQueueRemoval() },
                    onLoadMore = onLoadMore,
                    isLoadingMore = isLoadingMore,
                    onCollapse = onCollapse,
                    onBackToPlayer = { showQueue = false },
                    field = field,
                    accent = accent
                )
            } else {
                EditorialNowPlayingView(
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playWhenReady = playWhenReady,
                    progress = progress,
                    duration = duration,
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    showLyrics = showLyrics,
                    lyricsResult = lyricsResult,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onPlayPause = {
                        playerHaptics.playPause(!viewModel.isPlaying.value)
                        viewModel.togglePlayPause()
                    },
                    onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() },
                    onNext = { playerHaptics.skip(); viewModel.skipToNext() },
                    onSeekTo = { viewModel.seekTo(it) },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onToggleRepeat = { viewModel.toggleRepeat() },
                    onFavoriteToggle = { viewModel.toggleCurrentSongLike() },
                    onDownloadToggle = { currentSong?.let { viewModel.toggleDownload(it) } },
                    isDownloaded = currentSong?.let { viewModel.isDownloaded(it.id) } ?: false,
                    isDownloading = currentSong?.let { viewModel.isDownloading(it.id) } ?: false,
                    isLocalOriginal = currentSong?.let { viewModel.isLocalOriginal(it) } ?: false,
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    onShowAddToPlaylist = { showAddToPlaylist = true },
                    onShowSleepTimer = sleepTimer.open,
                    sleepTimerActive = sleepTimer.active,
                    onArtistClick = onArtistClick,
                    field = field,
                    accent = accent
                )
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorialNowPlayingView(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Long,
    duration: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    showLyrics: Boolean,
    lyricsResult: LyricsResult,
    onToggleLyrics: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onFavoriteToggle: (Boolean) -> Unit,
    onDownloadToggle: () -> Unit,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isLocalOriginal: Boolean,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowAddToPlaylist: () -> Unit,
    onShowSleepTimer: () -> Unit,
    sleepTimerActive: Boolean,
    onArtistClick: (String) -> Unit,
    field: Color,
    accent: Color
) {
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
                accent = accent,
                field = field,
                size = 44.dp
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(26.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Inverted while a timer runs - in a two-tone player, swapping
                // the fill is the only emphasis there is.
                EditorialCircleButton(
                    onClick = onShowSleepTimer,
                    accent = if (sleepTimerActive) accent else field,
                    field = if (sleepTimerActive) field else accent,
                    size = 44.dp
                ) {
                    Icon(Icons.Rounded.Bedtime, "Sleep timer", modifier = Modifier.size(22.dp))
                }
                EditorialCircleButton(
                    onClick = onShowAddToPlaylist,
                    accent = accent,
                    field = field,
                    size = 44.dp
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, "Add to Playlist", modifier = Modifier.size(22.dp))
                }
                EditorialCircleButton(
                    onClick = onShowQueue,
                    accent = accent,
                    field = field,
                    size = 44.dp
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", modifier = Modifier.size(22.dp))
                }
            }
        }

        // ========== DIE-CUT ART / LYRICS ==========
        // Swipe-to-skip: the die-cut art follows the finger and springs
        // back if the drag is abandoned; past the threshold it commits a
        // previous/next skip. Inactive while lyrics are shown. The headline
        // below shares this state, so both read as one gesture.
        val swipeToSkip = rememberSwipeToSkip(onNext = onNext, onPrevious = onPrevious)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .swipeToSkip(swipeToSkip, enabled = !showLyrics),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = showLyrics, label = "EditorialArtLyrics") { lyricsVisible ->
                if (lyricsVisible) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        SyncedLyricsView(
                            lyricsResult = lyricsResult,
                            currentPositionMs = progress,
                            onSeekTo = onSeekTo,
                            primaryColor = accent,
                            onSurfaceColor = accent,
                            onSurfaceVariantColor = accent.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            translationX = swipeToSkip.offset * SwipeToSkipDefaults.ArtFollow
                            rotationZ = swipeToSkip.offset / 80f
                        }
                    ) {
                        EditorialDieCutArt(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onTap = onPlayPause,
                            accent = accent,
                            field = field
                        )
                    }
                }
            }
        }

        // ========== HEADLINE ==========
        // Wrapped so the song information is one swipe target the full width
        // of the player, not two text-shaped ones.
        val title = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Untitled"
        val headlineBase = when {
            title.length <= 12 -> MaterialTheme.typography.displayLarge
            title.length <= 24 -> MaterialTheme.typography.displayMedium
            else -> MaterialTheme.typography.displaySmall
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .swipeToSkip(swipeToSkip)
                .swipeToSkipFollow(swipeToSkip)
        ) {
            Text(
                text = title,
                style = headlineBase.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            val artistName = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"
            Text(
                text = artistName.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = accent.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = artistName != "Unknown Artist") { onArtistClick(artistName) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== CONTROL CLUSTER (asymmetric bento) ==========
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Row 1: word pill + next circle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // The word IS the icon.
                Surface(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(50),
                    color = accent,
                    contentColor = field
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering && playWhenReady && !isPlaying) {
                            LoadingIndicator(
                                modifier = Modifier.size(36.dp),
                                color = field,
                                polygons = listOf(
                                    MaterialShapes.SoftBurst,
                                    MaterialShapes.Cookie9Sided,
                                    MaterialShapes.Pill,
                                    MaterialShapes.Sunny
                                )
                            )
                        } else {
                            AnimatedContent(
                                targetState = isPlaying,
                                label = "EditorialPlayWord"
                            ) { playing ->
                                Text(
                                    text = if (playing) "PAUSE" else "PLAY",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 3.sp
                                    )
                                )
                            }
                        }
                    }
                }
                EditorialCircleButton(
                    onClick = onNext,
                    accent = accent,
                    field = field,
                    size = 80.dp
                ) {
                    Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(34.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: previous circle + progress line with times
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EditorialCircleButton(
                    onClick = onPrevious,
                    accent = accent,
                    field = field,
                    size = 80.dp
                ) {
                    Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(34.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    // Scrub locally, seek once on release (streamed tracks
                    // rebuffer if seeked per drag frame).
                    var scrubPosition by remember { mutableStateOf<Float?>(null) }
                    val displayedProgress = scrubPosition?.toLong() ?: progress
                    val progressFraction =
                        if (duration > 0) displayedProgress.toFloat() / duration.toFloat() else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progressFraction,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "EditorialProgress"
                    )
                    val lineStroke = Stroke(
                        width = with(LocalDensity.current) { 4.dp.toPx() },
                        cap = StrokeCap.Round
                    )

                    Box(contentAlignment = Alignment.Center) {
                        // Wavy played portion, flat remainder - the wave
                        // settles flat when paused.
                        LinearWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp),
                            color = accent,
                            trackColor = accent.copy(alpha = 0.35f),
                            stroke = lineStroke,
                            trackStroke = lineStroke,
                            amplitude = { if (isPlaying) 1f else 0f }
                        )
                        Slider(
                            value = scrubPosition ?: progress.toFloat(),
                            onValueChange = { scrubPosition = it },
                            onValueChangeFinished = {
                                scrubPosition?.let { onSeekTo(it.toLong()) }
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
                            color = accent.copy(alpha = 0.8f)
                        )
                        Text(
                            text = formatEditorialTime(duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = accent.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== CHIPS ROW ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                EditorialChip(
                    checked = isFavorite,
                    onClick = { onFavoriteToggle(!isFavorite) },
                    accent = accent,
                    field = field
                ) {
                    LikeBurstIcon(isFavorite = isFavorite, iconSize = 20.dp)
                }
                EditorialChip(
                    checked = isDownloaded || isDownloading,
                    onClick = { if (!isDownloading && !isLocalOriginal) onDownloadToggle() },
                    accent = accent,
                    field = field
                ) {
                    when {
                        isLocalOriginal -> Icon(
                            Icons.Rounded.Smartphone, "Local File",
                            modifier = Modifier.size(20.dp)
                        )
                        isDownloading -> CircularWavyProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = field
                        )
                        else -> Icon(
                            if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                            if (isDownloaded) "Downloaded" else "Download",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                EditorialChip(
                    checked = shuffleModeEnabled,
                    onClick = onToggleShuffle,
                    accent = accent,
                    field = field
                ) {
                    Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(20.dp))
                }
                EditorialChip(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = onToggleRepeat,
                    accent = accent,
                    field = field
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
                    onClick = onToggleLyrics,
                    accent = accent,
                    field = field
                ) {
                    Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(8.dp)
        )
    }
}

/**
 * The die-cut artwork: album art clipped by a per-track MaterialShapes
 * polygon. On track change the clip morphs from the previous cut to the new
 * one; the art settles slightly smaller while paused.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorialDieCutArt(
    currentSong: Song?,
    isPlaying: Boolean,
    onTap: () -> Unit,
    accent: Color,
    field: Color
) {
    val styleWheel = LocalPlayerStyleWheelController.current
    val dieCuts = remember {
        listOf(
            MaterialShapes.Flower,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Puffy,
            MaterialShapes.Cookie12Sided,
            MaterialShapes.SoftBurst
        )
    }
    val targetPolygon = remember(currentSong?.id) {
        dieCuts[abs(currentSong?.id?.hashCode() ?: 0) % dieCuts.size]
    }

    var morphFrom by remember { mutableStateOf(targetPolygon) }
    var morphTo by remember { mutableStateOf(targetPolygon) }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(targetPolygon) {
        if (targetPolygon !== morphTo) {
            morphFrom = morphTo
            morphTo = targetPolygon
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }
    val morph = remember(morphFrom, morphTo) { Morph(morphFrom, morphTo) }
    val dieCutShape = remember(morph, morphProgress.value) {
        EditorialMorphShape(morph, morphProgress.value)
    }

    // Paused art settles slightly smaller; playing art sits at full size.
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "EditorialArtScale"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (maxWidth < 50.dp || maxHeight < 50.dp) return@BoxWithConstraints
        val artSize = minOf(maxWidth, maxHeight) * 0.95f

        Box(
            modifier = Modifier
                .size(artSize)
                .graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }
                .clip(dieCutShape)
                .background(accent)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() })
                }
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
                    tint = field,
                    modifier = Modifier.size(artSize * 0.3f)
                )
            }
        }
    }
}

/** Flat accent circle button with field-colored content. */
@Composable
internal fun EditorialCircleButton(
    onClick: () -> Unit,
    accent: Color,
    field: Color,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = accent,
            contentColor = field
        ),
        modifier = Modifier.size(size)
    ) {
        content()
    }
}

/**
 * Two-tone toggle chip: checked = accent fill with field glyph, unchecked =
 * bare accent glyph on the field. No third color, no outline.
 */
@Composable
internal fun EditorialChip(
    checked: Boolean,
    onClick: () -> Unit,
    accent: Color,
    field: Color,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (checked) accent else Color.Transparent,
        contentColor = if (checked) field else accent,
        modifier = Modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * Editorial queue: the same two-tone spread. The current track is the only
 * inverted row (accent fill, field text) - print-style emphasis. Shared with
 * the Poster style, which passes its own field/accent pair.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EditorialQueueView(
    queue: List<Song>,
    currentSong: Song?,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (index: Int) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    onCollapse: () -> Unit,
    onBackToPlayer: () -> Unit,
    field: Color,
    accent: Color,
    onMoveSong: (from: Int, to: Int) -> Unit = { _, _ -> },
    onCommitOrder: () -> Unit = {},
    onUndoRemove: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val rowKeys = remember(queue) { queueRowKeys(queue.map { it.id }, "editorial_queue") }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = onMoveSong,
        onSettle = onCommitOrder
    )
    val removal = rememberQueueRemoval(onUndo = onUndoRemove)

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(field)
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
                accent = accent,
                field = field,
                size = 44.dp
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(26.dp))
            }
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
            EditorialCircleButton(
                onClick = onBackToPlayer,
                accent = accent,
                field = field,
                size = 44.dp
            ) {
                Icon(Icons.Rounded.MusicNote, "Now Playing", modifier = Modifier.size(22.dp))
            }
        }

        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Occurrence-qualified, not index-qualified: the same song can
                // appear twice and duplicate keys crash, but a key that moves
                // with the index breaks reordering. See queueRowKeys.
                itemsIndexed(
                    queue,
                    key = { index, _ -> rowKeys.getOrElse(index) { "editorial_queue_$index" } }
                ) { index, song ->
                    val isCurrent = song.id == currentSong?.id
                    val key = rowKeys.getOrElse(index) { "editorial_queue_$index" }
                    val isDragging = reorder.draggingKey == key

                    QueueRowContainer(
                        isDragging = isDragging,
                        dragOffset = reorder.offsetFor(key),
                        removeEnabled = queue.size > 1,
                        onRemove = {
                            onRemoveSong(index)
                            removal.onRemoved(song.title)
                        },
                        // animateItem sits on the container rather than the row,
                        // so the slide-out-of-the-way applies to the whole
                        // swipeable cell. It is disabled for the row under the
                        // finger, which is positioned by the drag instead.
                        modifier = if (isDragging) Modifier else Modifier.animateItem()
                    ) {
                        Surface(
                            onClick = { onSongClick(song) },
                            shape = RoundedCornerShape(24.dp),
                            color = if (isCurrent) accent else field,
                            contentColor = if (isCurrent) field else accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .queueDragLongPress(reorder, key)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Rounded.GraphicEq, "Playing",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = FontFamily.Serif,
                                                fontStyle = FontStyle.Italic
                                            )
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.graphicsLayer { alpha = 0.75f }
                                    )
                                }
                                QueueDragHandle(
                                    state = reorder,
                                    rowKey = key,
                                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                                )
                                IconButton(
                                    onClick = {
                                        onRemoveSong(index)
                                        removal.onRemoved(song.title)
                                    },
                                    enabled = queue.size > 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close, "Remove from queue",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "editorial_load_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = onLoadMore,
                            enabled = !isLoadingMore,
                            shape = RoundedCornerShape(50),
                            color = accent,
                            contentColor = field
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    LoadingIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = field,
                                        polygons = listOf(
                                            MaterialShapes.Cookie9Sided,
                                            MaterialShapes.Pill,
                                            MaterialShapes.Sunny
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "MORE",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            letterSpacing = 3.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        SnackbarHost(
            hostState = removal.hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }
}

/**
 * Shape backed by a graphics-shapes Morph at a given progress, scaled to
 * fill the composable bounds (same idiom as OnboardingScreen's MorphShape).
 */
internal class EditorialMorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        val bounds = morph.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Shape backed by a single RoundedPolygon scaled to fill the composable
 * bounds. Static sibling of EditorialMorphShape.
 */
internal class EditorialPolygonShape(
    private val polygon: RoundedPolygon
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val matrix = Matrix()
        val bounds = polygon.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

internal fun formatEditorialTime(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
