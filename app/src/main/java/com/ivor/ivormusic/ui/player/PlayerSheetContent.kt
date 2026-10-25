package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.media3.common.Player
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.queuePlaylistDateText
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.QueueDragHandle
import com.ivor.ivormusic.ui.components.QueueRowContainer
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.components.queueDragLongPress
import com.ivor.ivormusic.ui.components.rememberFocusedQueueListState
import com.ivor.ivormusic.ui.components.rememberQueueRemoval
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The Classic player, in Material 3 Expressive terms: two hero moments - the
 * cover, whose shape is the play state, and the play button, the one wide
 * filled control in a standard button group - and everything else in tonal
 * containers that change shape when pressed or turned on.
 *
 * Below the controls, Up next, Lyrics and Related open a panel that rises over
 * the player. Its open flag is [rememberPlayerQueueOpen], shared with
 * [ExpandablePlayer], so back closes the panel and returns to the player, the
 * same as every other style's queue.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    /** Hand this song to the video player; null where there is no video pipeline. */
    onWatchAsVideo: (() -> Unit)? = null,
    onAlbumClick: (String) -> Unit = {},
    onOpenAlbum: (PlaylistDisplayItem) -> Unit = {}
) {
    // Back is handled by ExpandablePlayer: it collapses the player, or closes
    // this style's panel first while the panel is open.
    val currentSong by viewModel.currentSong.collectAsState()
    var panelOpen by rememberPlayerQueueOpen()
    var panelTab by rememberSaveable { mutableStateOf(ClassicTab.UpNext) }
    var showOptions by rememberNowPlayingOptionsOpen()

    val albumArtUrl = currentSong?.highResThumbnailUrl
        ?: currentSong?.thumbnailUrl
        ?: currentSong?.albumArtUri?.toString()

    // The panel's position, 0 closed to 1 open. One Animatable for the drag,
    // the fling and the tap, so any of them can be caught mid-flight.
    val scope = rememberCoroutineScope()
    val panel = remember { Animatable(if (panelOpen) 1f else 0f) }
    val panelSpring = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    LaunchedEffect(panelOpen) { panel.animateTo(if (panelOpen) 1f else 0f, panelSpring) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    val dragPanel: (Float) -> Unit = { deltaPx ->
        scope.launch { panel.snapTo((panel.value - deltaPx / heightPx).coerceIn(0f, 1f)) }
    }
    val settlePanel: (Float) -> Unit = { velocity ->
        val open = if (abs(velocity) > 900f) velocity < 0f else panel.value > 0.5f
        if (open == panelOpen) scope.launch { panel.animateTo(if (open) 1f else 0f, panelSpring) }
        else panelOpen = open
    }
    val openTab: (ClassicTab) -> Unit = { tab ->
        panelTab = tab
        panelOpen = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
    ) {
        ChromaticMistBackground(
            albumArtUrl = albumArtUrl,
            enabled = ambientBackground,
            modifier = Modifier.fillMaxSize()
        )

        ClassicNowPlaying(
            viewModel = viewModel,
            currentSong = currentSong,
            onCollapse = onCollapse,
            onShowOptions = { showOptions = true },
            onArtistClick = onArtistClick,
            onOpenTab = openTab,
            onDragPanel = dragPanel,
            onSettlePanel = settlePanel,
            modifier = Modifier.graphicsLayer {
                // Recedes a little under the rising panel, so the panel reads
                // as coming over the player rather than replacing it.
                val p = panel.value
                alpha = 1f - p * 0.35f
                val scale = 1f - p * 0.04f
                scaleX = scale
                scaleY = scale
            }
        )

        val panelVisible by remember { derivedStateOf { panel.value > 0f } }
        if (panelVisible) {
            ClassicPanel(
                viewModel = viewModel,
                currentSong = currentSong,
                tab = panelTab,
                onTabChange = { panelTab = it },
                onClose = { panelOpen = false },
                onDragPanel = dragPanel,
                onSettlePanel = settlePanel,
                onLoadMore = onLoadMore,
                onArtistClick = onArtistClick,
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - panel.value) * size.height
                }
            )
        }
    }

    // The overflow menu, gated on a song so it can never open against nothing.
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

/** The panel's three tabs, in the order YouTube Music has them. Not persisted. */
private enum class ClassicTab(val label: Int, val icon: ImageVector) {
    UpNext(R.string.cl_tab_up_next, Icons.AutoMirrored.Rounded.QueueMusic),
    Lyrics(R.string.cl_tab_lyrics, Icons.Rounded.Lyrics),
    Related(R.string.cl_tab_related, Icons.Rounded.Explore)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ClassicNowPlaying(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    onCollapse: () -> Unit,
    onShowOptions: () -> Unit,
    onArtistClick: (String) -> Unit,
    onOpenTab: (ClassicTab) -> Unit,
    onDragPanel: (Float) -> Unit,
    onSettlePanel: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleOn by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    // Collected so the download button follows the download as it runs.
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val haptics = rememberPlayerHaptics()
    val sleepTimer = rememberSleepTimerControl(viewModel = viewModel)
    // Playing or about to: the cover and the play button both read this.
    val active = isPlaying || (isBuffering && playWhenReady)

    val swipeToSkip = rememberSwipeToSkip(
        onNext = { haptics.skip(); viewModel.skipToNext() },
        onPrevious = { haptics.skip(); viewModel.skipToPrevious() }
    )
    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---- Top bar. The title is centred on the bar itself, not between
        // the buttons, so one button on the left and two on the right cannot
        // push it off centre.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp)
        ) {
            FilledTonalIconButton(
                onClick = onCollapse,
                shapes = IconButtonDefaults.shapes(),
                colors = tonal,
                modifier = Modifier.align(Alignment.CenterStart).size(48.dp)
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.cl_collapse),
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 112.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.ps_now_playing).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.18.em,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                val album = currentSong?.album?.takeIf { it.isNotBlank() }
                if (album != null) {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // A toggle, so a running timer is visible as a filled, squared
                // button rather than only a tint.
                FilledTonalIconToggleButton(
                    checked = sleepTimer.active,
                    onCheckedChange = { sleepTimer.open() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.sleep_timer_title))
                }
                FilledTonalIconButton(
                    onClick = onShowOptions,
                    shapes = IconButtonDefaults.shapes(),
                    colors = tonal,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
            }
        }

        // ---- Hero one: the cover. Its shape is the play state - a soft
        // square while the music plays, a twelve-sided cookie that sinks back
        // when it stops - so pause reads at a glance from across a room.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .swipeToSkip(swipeToSkip),
            contentAlignment = Alignment.Center
        ) {
            if (maxWidth < 64.dp || maxHeight < 64.dp) return@BoxWithConstraints
            val coverSize = minOf(maxWidth - 40.dp, maxHeight - 12.dp)
            val pausedness by animateFloatAsState(
                targetValue = if (active) 0f else 1f,
                animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
                label = "classicCoverMorph"
            )
            val coverMorph = remember { Morph(MaterialShapes.Square, MaterialShapes.Cookie12Sided) }
            val coverShape = remember(pausedness) { UnitMorphShape(coverMorph, pausedness.coerceIn(0f, 1f)) }
            val styleWheel = LocalPlayerStyleWheelController.current
            Surface(
                modifier = Modifier
                    .size(coverSize)
                    .graphicsLayer {
                        val scale = 1f - 0.1f * pausedness
                        scaleX = scale
                        scaleY = scale
                    }
                    .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                    .styleWheelHold(styleWheel),
                shape = coverShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 12.dp
            ) {
                val artSong = currentSong?.takeIf { it.albumArtUri != null || it.thumbnailUrl != null }
                if (artSong != null) {
                    PlayerArtwork(
                        song = artSong,
                        contentDescription = stringResource(R.string.cd_album_art),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(coverSize * 0.32f),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            // ---- The song: the poster face at size, the artist in the accent,
            // and the two things done to a song as tonal toggles.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .swipeToSkip(swipeToSkip)
                        .swipeToSkipFollow(swipeToSkip)
                ) {
                    Text(
                        text = currentSong?.title?.takeIf { !isUnknownTitle(it) }
                            ?: stringResource(R.string.untitled_song),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.02).em
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        // A long title travels rather than being cut.
                        modifier = Modifier.basicMarquee(initialDelayMillis = 2000)
                    )
                    val artist = currentSong?.artist?.takeIf { !isUnknownArtist(it) }
                    Text(
                        text = artist ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = artist != null) { artist?.let(onArtistClick) }
                    )
                }
                val song = currentSong
                if (song != null) {
                    val isLocal = viewModel.isLocalOriginal(song)
                    val isDownloading = song.id in downloadingIds
                    val isDownloaded = downloadedSongs.any { it.id == song.id }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconToggleButton(
                        checked = isDownloaded || isDownloading || isLocal,
                        onCheckedChange = { if (!isDownloading && !isLocal) viewModel.toggleDownload(song) },
                        modifier = Modifier.size(52.dp)
                    ) {
                        when {
                            isLocal -> Icon(Icons.Rounded.Smartphone, contentDescription = stringResource(R.string.cd_local_file))
                            isDownloading -> CircularWavyProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = LocalContentColor.current
                            )
                            isDownloaded -> Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.song_options_downloaded)
                            )
                            else -> Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.song_options_download))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconToggleButton(
                        checked = isFavorite,
                        onCheckedChange = { viewModel.toggleCurrentSongLike() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        LikeBurstIcon(isFavorite = isFavorite, iconSize = 24.dp)
                    }
                }
            }

            // ---- The seek bar. The Slider owns the drag; the scrubber draws.
            var scrubPosition by remember { mutableStateOf<Float?>(null) }
            val displayedProgress = scrubPosition?.toLong() ?: progress
            val scrubReturnPoint = LocalScrubReturnPoint.current
            val smoothProgress = rememberSmoothProgress(
                positionMs = progress,
                durationMs = duration,
                isPlaying = isPlaying,
                scrubPositionMs = scrubPosition
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = 14.dp)) {
                val stroke = Stroke(width = with(LocalDensity.current) { 7.dp.toPx() }, cap = StrokeCap.Round)
                ExpressiveScrubber(
                    progress = { smoothProgress.value },
                    modifier = Modifier.fillMaxWidth().height(scrubberTrackHeight(14.dp)),
                    stroke = stroke,
                    trackStroke = stroke,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
                Slider(
                    interactionSource = LocalPlayerScrubInteraction.current,
                    value = scrubPosition ?: progress.toFloat(),
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(displayedProgress),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration(duration),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PlayerVisualizerSlot(modifier = Modifier.fillMaxWidth())

            // ---- Hero two: the transport, a standard button group. A pressed
            // button widens and its neighbours give way; shuffle and repeat
            // square off when on; play is the one wide filled control, a
            // squircle while playing and a full pill while paused.
            val shuffleInteraction = remember { MutableInteractionSource() }
            val prevInteraction = remember { MutableInteractionSource() }
            val playInteraction = remember { MutableInteractionSource() }
            val nextInteraction = remember { MutableInteractionSource() }
            val repeatInteraction = remember { MutableInteractionSource() }
            val playCorner by animateDpAsState(
                targetValue = if (active) 28.dp else 48.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                label = "classicPlayCorner"
            )
            val playShapes = IconButtonShapes(
                shape = RoundedCornerShape(playCorner),
                pressedShape = RoundedCornerShape(16.dp)
            )
            val transportTonal = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconToggleButton(
                            checked = shuffleOn,
                            onCheckedChange = { viewModel.toggleShuffle() },
                            interactionSource = shuffleInteraction,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                                .animateWidth(shuffleInteraction)
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = stringResource(R.string.cd_shuffle),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconButton(
                            onClick = { haptics.skip(); viewModel.skipToPrevious() },
                            shapes = IconButtonDefaults.shapes(),
                            colors = transportTonal,
                            interactionSource = prevInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateWidth(prevInteraction)
                        ) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.cd_previous),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledIconButton(
                            onClick = {
                                haptics.playPause(!viewModel.isPlaying.value)
                                viewModel.togglePlayPause()
                            },
                            shapes = playShapes,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            interactionSource = playInteraction,
                            modifier = Modifier
                                .weight(1.9f)
                                .fillMaxHeight()
                                .animateWidth(playInteraction)
                        ) {
                            if (isBuffering && playWhenReady && !isPlaying) {
                                LoadingIndicator(
                                    modifier = Modifier.size(44.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    polygons = listOf(
                                        MaterialShapes.SoftBurst,
                                        MaterialShapes.Cookie9Sided,
                                        MaterialShapes.Pill,
                                        MaterialShapes.Sunny
                                    )
                                )
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconButton(
                            onClick = { haptics.skip(); viewModel.skipToNext() },
                            shapes = IconButtonDefaults.shapes(),
                            colors = transportTonal,
                            interactionSource = nextInteraction,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .animateWidth(nextInteraction)
                        ) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.cd_next),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
                customItem(
                    buttonGroupContent = {
                        FilledTonalIconToggleButton(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onCheckedChange = { viewModel.toggleRepeat() },
                            interactionSource = repeatInteraction,
                            modifier = Modifier
                                .weight(0.85f)
                                .fillMaxHeight()
                                .animateWidth(repeatInteraction)
                        ) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = stringResource(R.string.cd_repeat),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    },
                    menuContent = {}
                )
            }
        }

        // ---- The way into the panel: a tonal pill of three labelled tabs,
        // tapped or pulled up.
        ClassicPanelEntry(
            onSelect = onOpenTab,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { onDragPanel(it) },
                    onDragStopped = { velocity -> onSettlePanel(velocity) }
                )
        )
    }
}

/**
 * Up next, Lyrics and Related, under the controls: one tonal container with a
 * grip, so it reads as something that comes up from the edge, and labels kept
 * beside the icons - an icon alone does not say "Related".
 */
@Composable
private fun ClassicPanelEntry(
    onSelect: (ClassicTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Row(modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp)) {
                ClassicTab.entries.forEach { tab ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSelect(tab) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(tab.label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicPanel(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    tab: ClassicTab,
    onTabChange: (ClassicTab) -> Unit,
    onClose: () -> Unit,
    onDragPanel: (Float) -> Unit,
    onSettlePanel: (Float) -> Unit,
    onLoadMore: () -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val removal = rememberQueueRemoval(onUndo = { viewModel.undoQueueRemoval() })
    val haptics = rememberPlayerHaptics()

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // The song, small, at the top of the panel - tap it or pull it
                // down to go back to the player.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { onDragPanel(it) },
                            onDragStopped = { velocity -> onSettlePanel(velocity) }
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                    currentSong?.let { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClose)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongArtwork(
                                song = song,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledIconButton(
                                onClick = {
                                    haptics.playPause(!viewModel.isPlaying.value)
                                    viewModel.togglePlayPause()
                                },
                                shapes = IconButtonDefaults.shapes(),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // Switching the panel's view is what a connected button group
                // is for: one selected, each shaped by its place in the row.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                ) {
                    val count = ClassicTab.entries.size
                    ClassicTab.entries.forEachIndexed { index, entry ->
                        ToggleButton(
                            checked = entry == tab,
                            onCheckedChange = { onTabChange(entry) },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            colors = ToggleButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                checkedContainerColor = MaterialTheme.colorScheme.primary,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Icon(entry.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(entry.label), maxLines = 1)
                        }
                    }
                }

                AnimatedContent(
                    targetState = tab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.weight(1f),
                    label = "classicPanelTab"
                ) { shown ->
                    when (shown) {
                        ClassicTab.UpNext -> ClassicUpNext(
                            viewModel = viewModel,
                            removal = removal,
                            onLoadMore = onLoadMore
                        )
                        ClassicTab.Lyrics -> {
                            val lyrics by viewModel.lyricsResult.collectAsState()
                            val position by viewModel.progress.collectAsState()
                            SyncedLyricsView(
                                lyricsResult = lyrics,
                                currentPositionMs = position,
                                isPlaying = isPlaying,
                                onSeekTo = { viewModel.seekTo(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        ClassicTab.Related -> ClassicRelated(
                            viewModel = viewModel,
                            song = currentSong,
                            onArtistClick = onArtistClick,
                            onAnnounce = { removal.announce(it) }
                        )
                    }
                }
            }
            DismissibleSnackbarHost(
                hostState = removal.hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Up next: the queue in the order it will be heard, reorderable and
 * swipe-removable through the shared queue machinery, drawn as one grouped
 * list with the playing song marked where it sits.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicUpNext(
    viewModel: PlayerViewModel,
    removal: com.ivor.ivormusic.ui.components.QueueRemovalController,
    onLoadMore: () -> Unit
) {
    val context = LocalContext.current
    val queue by viewModel.playOrderQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadedIds = remember(downloadedSongs) { downloadedSongs.mapTo(HashSet()) { it.id } }
    var showSaveDialog by remember { mutableStateOf(false) }

    if (queue.isEmpty()) {
        ClassicPanelMessage(stringResource(R.string.ps_queue_empty))
        return
    }

    val currentIndex = remember(queue, currentQueueItemId) { queue.indexOfFirst { it.id == currentQueueItemId } }
    val listState = rememberFocusedQueueListState(currentQueueIndex = currentIndex, leadingItemCount = 1)
    val rowKeys = remember(queue) { queue.map { it.id } }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = { from, to -> viewModel.movePlayOrderItem(from, to) },
        onSettle = {}
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "up_next_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.cl_playing_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalIconButton(
                    onClick = { showSaveDialog = true },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.queue_save_as_playlist)
                    )
                }
            }
        }
        itemsIndexed(queue, key = { index, _ -> rowKeys.getOrElse(index) { "queue_$index" } }) { index, item ->
            val key = rowKeys.getOrElse(index) { "queue_$index" }
            val isCurrent = item.id == currentQueueItemId
            val isDragging = reorder.draggingKey == key
            QueueRowContainer(
                isDragging = isDragging,
                dragOffset = reorder.offsetFor(key),
                removeEnabled = queue.size > 1,
                onRemove = {
                    viewModel.removeQueueItem(item.id)
                    removal.onRemoved(item.song.title)
                },
                modifier = if (isDragging) Modifier else Modifier.animateItem()
            ) {
                ClassicSongRow(
                    song = item.song,
                    highlighted = isCurrent,
                    first = index == 0,
                    last = index == queue.lastIndex,
                    onClick = { viewModel.skipToQueueItem(item.id) },
                    modifier = Modifier.queueDragLongPress(reorder, key),
                    status = {
                        when {
                            item.song.id in downloadingIds -> CircularWavyProgressIndicator(modifier = Modifier.size(16.dp))
                            item.song.id in downloadedIds -> Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.song_options_downloaded),
                                tint = LocalContentColor.current.copy(alpha = 0.72f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    trailing = {
                        QueueDragHandle(
                            state = reorder,
                            rowKey = key,
                            tint = LocalContentColor.current.copy(alpha = 0.72f)
                        )
                    }
                )
            }
        }
        item(key = "up_next_more") {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                FilledTonalButton(onClick = onLoadMore, enabled = !isLoadingMore, shapes = ButtonDefaults.shapes()) {
                    if (isLoadingMore) {
                        LoadingIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.loading))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.load_more))
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        CreatePlaylistDialog(
            initialName = stringResource(R.string.queue_save_default_name, remember { queuePlaylistDateText() }),
            onDismiss = { showSaveDialog = false },
            onCreate = { name, description ->
                showSaveDialog = false
                viewModel.saveQueueAsPlaylist(name, description) { savedName, trackCount ->
                    removal.announce(
                        context.resources.getQuantityString(
                            R.plurals.queue_saved_to_playlist, trackCount, trackCount, savedName
                        )
                    )
                }
            }
        )
    }
}

/**
 * Related: songs like the one playing, from YouTube Music's own radio for it,
 * asked for when this tab is opened (see [PlayerViewModel.relatedSongsFor]).
 * A tap starts that song's own radio, as it does in YouTube Music; the plus
 * queues it without leaving what is playing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicRelated(
    viewModel: PlayerViewModel,
    song: Song?,
    onArtistClick: (String) -> Unit,
    onAnnounce: (String) -> Unit
) {
    val context = LocalContext.current
    if (song == null) return
    if (song.source != SongSource.YOUTUBE) {
        ClassicPanelMessage(stringResource(R.string.cl_related_device))
        return
    }
    var attempt by remember(song.id) { mutableStateOf(0) }
    val related by produceState<List<Song>?>(initialValue = null, song.id, attempt) {
        value = null
        value = viewModel.relatedSongsFor(song)
    }
    val list = related
    when {
        list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        list.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.cl_related_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { attempt++ }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_retry))
            }
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val artist = song.artist.takeIf { !isUnknownArtist(it) }
            if (artist != null) {
                item(key = "related_artist") {
                    Surface(
                        onClick = { onArtistClick(artist) },
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialShapes.Cookie9Sided.toShape(),
                                color = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Person, contentDescription = null)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                stringResource(R.string.cl_related_more_from, artist),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item(key = "related_header") {
                Text(
                    stringResource(R.string.cl_related_like_this),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(list, key = { _, item -> "related_${item.id}" }) { index, item ->
                ClassicSongRow(
                    song = item,
                    highlighted = false,
                    first = index == 0,
                    last = index == list.lastIndex,
                    onClick = { viewModel.playSongRadio(item) },
                    trailing = {
                        IconButton(
                            onClick = {
                                viewModel.addToQueue(listOf(item))
                                onAnnounce(context.getString(R.string.cl_added_to_queue, item.title))
                            },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = stringResource(R.string.song_options_add_to_queue)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ClassicPanelMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One row of a panel list, in the app's segmented-list geometry: 20dp at the
 * group's outer corners, 4dp between rows, so a list reads as one container.
 * The playing row is the accent container with its cover marked.
 */
@Composable
private fun ClassicSongRow(
    song: Song,
    highlighted: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    status: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit
) {
    val outer = 20.dp
    val inner = 4.dp
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(
            topStart = if (first) outer else inner,
            topEnd = if (first) outer else inner,
            bottomStart = if (last) outer else inner,
            bottomEnd = if (last) outer else inner
        ),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))) {
                SongArtwork(
                    song = song,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (highlighted) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.cd_playing),
                            tint = MaterialTheme.colorScheme.inverseOnSurface
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    status()
                }
            }
            trailing()
        }
    }
}

/** A [Morph] between two unit-square Material shapes, stretched to the composable. */
private class UnitMorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    return String.format(java.util.Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
