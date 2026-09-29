package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.Song
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
import com.ivor.ivormusic.ui.theme.WindowLayout

/*
 * The expanded player beyond a phone held upright.
 *
 * Nine player styles are each a composition for a tall, narrow window, and
 * [playerFrameFor] decides what happens when the window is not one:
 *
 * - FULL: the style fills the window, as it always has. Phones upright, and
 *   tablets upright, where a style's weighted artwork box absorbs the extra
 *   width.
 * - STAGE: the style keeps a phone-shaped column at the start and a companion
 *   panel (Up next, Lyrics) takes the rest. A tablet sideways or an unfolded
 *   foldable. The style is untouched; the space it cannot use becomes the two
 *   things people otherwise open a second screen for.
 * - LANDSCAPE: a window too short for any portrait composition, which is a
 *   phone on its side or a split-screen half. No style survives 360dp of
 *   height, so one shared layout takes over: artwork beside controls, with
 *   the artwork square turning into lyrics or the queue on demand, and the
 *   controls always in reach.
 *
 * [judgement] The alternative for LANDSCAPE was a landscape variant of each of
 * the nine styles. Most of what makes a style itself (Editorial's display
 * headline, Canvas's full-bleed cover, Dial's dial) is the vertical stack, and
 * nine half-versions would be worse than one layout made for the shape.
 */

internal enum class PlayerFrameKind { FULL, STAGE, LANDSCAPE }

@Immutable
internal data class PlayerFrame(
    val kind: PlayerFrameKind,
    /** The style column's width in [PlayerFrameKind.STAGE]; unspecified otherwise. */
    val stageWidth: Dp = Dp.Unspecified,
)

/** The narrowest companion panel worth drawing: a queue row with art, title and a handle. */
internal val COMPANION_MIN_WIDTH = 320.dp

/**
 * Pure so the JVM suite can hold the breakpoints (`PlayerFrameTest`).
 *
 * The stage is sized from the height, not the width: a style is designed for a
 * phone's proportions, so the column is as wide as a phone would be at this
 * height (0.56, roughly 9:16), clamped to what a style has ever been drawn at.
 */
internal fun playerFrameFor(layout: WindowLayout): PlayerFrame {
    if (layout.isShort && layout.isLandscape) return PlayerFrame(PlayerFrameKind.LANDSCAPE)
    if (layout.width >= WindowLayout.MEDIUM_WIDTH) {
        val stage = (layout.height * 0.56f).coerceIn(360.dp, 600.dp)
        if (layout.width - stage >= COMPANION_MIN_WIDTH) {
            return PlayerFrame(PlayerFrameKind.STAGE, stage)
        }
    }
    return PlayerFrame(PlayerFrameKind.FULL)
}

/* ------------------------------------------------------------------ */
/* Companion panel                                                     */
/* ------------------------------------------------------------------ */

private enum class CompanionPane { UP_NEXT, LYRICS }

/**
 * Up next and Lyrics, side by side with the player rather than instead of it.
 *
 * It wears the player's theme (the artwork scheme when Album Art Colors is
 * on), because it is part of the player surface - unlike the options sheet,
 * which is app furniture. Its ground is the ambient mist when that is on, so
 * a style with its own coloured field does not end in a hard grey wall.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerCompanionPanel(
    viewModel: PlayerViewModel,
    onLoadMore: () -> Unit,
    ambientBackground: Boolean,
    modifier: Modifier = Modifier,
    /** Insets the panel must keep clear of; the full safe area by default. */
    insets: WindowInsets = WindowInsets.safeDrawing,
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var pane by rememberSaveable { mutableStateOf(CompanionPane.UP_NEXT) }
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        ChromaticMistBackground(
            albumArtUrl = currentSong?.let { it.thumbnailUrl ?: it.albumArtUri?.toString() },
            enabled = ambientBackground,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                val entries = CompanionPane.entries
                entries.forEachIndexed { index, entry ->
                    val selected = entry == pane
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = {
                            if (!selected) {
                                haptics.subtle()
                                pane = entry
                            }
                        },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        colors = ToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) {
                        Icon(
                            imageVector = when (entry) {
                                CompanionPane.UP_NEXT -> Icons.AutoMirrored.Filled.QueueMusic
                                CompanionPane.LYRICS -> Icons.Rounded.Lyrics
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                when (entry) {
                                    CompanionPane.UP_NEXT -> R.string.ps_up_next
                                    CompanionPane.LYRICS -> R.string.ps_lyrics
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Read here: motionScheme is composable and transitionSpec is not.
                val paneFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = { fadeIn(paneFade) togetherWith fadeOut(paneFade) },
                    label = "CompanionPane"
                ) { shown ->
                    when (shown) {
                        CompanionPane.UP_NEXT -> PlayerQueuePane(
                            viewModel = viewModel,
                            onLoadMore = onLoadMore,
                            modifier = Modifier.fillMaxSize()
                        )
                        CompanionPane.LYRICS -> SyncedLyricsView(
                            lyricsResult = lyricsResult,
                            currentPositionMs = progress,
                            isPlaying = isPlaying,
                            onSeekTo = { viewModel.seekTo(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * The queue as a pane inside a larger player, rather than a screen of its own:
 * no collapse or back-to-player buttons, since the player is right there.
 *
 * It is the fourth drawing of a queue and shares all of the behaviour with the
 * other three (`QueueReorder.kt`, `QueueRowContainer.kt`): occurrence keys,
 * the drag handle first and long press as the accelerator, swipe to remove
 * with undo, focus on the current row once. Only the rows are its own, with
 * artwork, because a panel on a large screen has the room a phone queue did not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerQueuePane(
    viewModel: PlayerViewModel,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val queue by viewModel.playOrderQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val currentIndex = remember(queue, currentQueueItemId) {
        queue.indexOfFirst { it.id == currentQueueItemId }
    }
    // One leading item: the header row.
    val listState = rememberFocusedQueueListState(currentIndex, leadingItemCount = 1)
    val rowKeys = remember(queue) { queue.map { it.id } }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = { from, to -> viewModel.movePlayOrderItem(from, to, persist = false) },
        onSettle = { viewModel.commitQueueOrder() }
    )
    val removal = rememberQueueRemoval(onUndo = { viewModel.undoQueueRemoval() })
    var showSaveDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier) {
        if (queue.isEmpty()) {
            Text(
                text = stringResource(R.string.ps_queue_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "queue_pane_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentIndex >= 0) "${currentIndex + 1} / ${queue.size}"
                            else "${queue.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = stringResource(R.string.queue_save_as_playlist)
                            )
                        }
                    }
                }
                itemsIndexed(
                    queue,
                    key = { index, _ -> rowKeys.getOrElse(index) { "queue_pane_$index" } }
                ) { index, item ->
                    val key = rowKeys.getOrElse(index) { "queue_pane_$index" }
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
                        QueuePaneRow(
                            item = item,
                            isCurrent = item.id == currentQueueItemId,
                            removeEnabled = queue.size > 1,
                            onClick = { viewModel.skipToQueueItem(item.id) },
                            onRemove = {
                                viewModel.removeQueueItem(item.id)
                                removal.onRemoved(item.song.title)
                            },
                            dragHandle = {
                                QueueDragHandle(
                                    state = reorder,
                                    rowKey = key,
                                    tint = LocalContentColor.current.copy(alpha = 0.7f)
                                )
                            },
                            modifier = Modifier.queueDragLongPress(reorder, key)
                        )
                    }
                }
                item(key = "queue_pane_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalButton(onClick = onLoadMore, enabled = !isLoadingMore) {
                            if (isLoadingMore) {
                                LoadingIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text(stringResource(R.string.player_more_songs))
                            }
                        }
                    }
                }
            }
        }

        DismissibleSnackbarHost(
            hostState = removal.hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        )

        if (showSaveDialog) {
            CreatePlaylistDialog(
                initialName = stringResource(
                    R.string.queue_save_default_name,
                    remember { queuePlaylistDateText() }
                ),
                onDismiss = { showSaveDialog = false },
                onCreate = { name, description ->
                    showSaveDialog = false
                    viewModel.saveQueueAsPlaylist(name, description) { savedName, trackCount ->
                        removal.announce(
                            context.resources.getQuantityString(
                                R.plurals.queue_saved_to_playlist,
                                trackCount,
                                trackCount,
                                savedName
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun QueuePaneRow(
    item: MusicQueueItem,
    isCurrent: Boolean,
    removeEnabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = item.song
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                SongArtwork(
                    song = song,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.cd_playing),
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
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
            dragHandle()
            IconButton(onClick = onRemove, enabled = removeEnabled, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_from_queue),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Landscape                                                           */
/* ------------------------------------------------------------------ */

private enum class LandscapePane { ART, LYRICS, QUEUE }

/**
 * The expanded player in a window too short for a portrait style.
 *
 * Artwork on the start side, square to the height; everything the hand needs
 * on the other. Lyrics and the queue replace the artwork square rather than
 * the controls, so seeking inside a verse or skipping from the queue never
 * costs a trip back. When the window is also wide enough (a tablet in
 * split screen), the companion panel joins as a third column instead and the
 * square stays artwork.
 *
 * Gestures match the portrait styles: swipe the artwork or the title sideways
 * to skip (the shared `SwipeToSkip` contract), swipe down to collapse and up
 * for the options sheet (both on `ExpandablePlayer`'s container), and the
 * seek bar is the shared `ExpressiveScrubber` with its return point.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LandscapeNowPlaying(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit,
    onArtistClick: (String) -> Unit,
    onWatchAsVideo: (() -> Unit)?,
    onAlbumClick: (String) -> Unit,
    onOpenAlbum: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit,
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val playerHaptics = rememberPlayerHaptics()
    val sleepTimer = rememberSleepTimerControl(viewModel)
    var showOptions by rememberNowPlayingOptionsOpen()
    var pane by rememberSaveable { mutableStateOf(LandscapePane.ART) }

    val onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    val onNext = { playerHaptics.skip(); viewModel.skipToNext() }
    val swipeToSkip = rememberSwipeToSkip(onNext = onNext, onPrevious = onPrevious)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        ChromaticMistBackground(
            albumArtUrl = currentSong?.let { it.thumbnailUrl ?: it.albumArtUri?.toString() },
            enabled = ambientBackground,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxSize()
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        ) {
            val gap = 20.dp
            val artSize = maxHeight
            // A third column only when the controls keep a comfortable width
            // beside it; a phone on its side never qualifies.
            val withCompanion = maxWidth - artSize - LANDSCAPE_CONTROLS_MIN_WIDTH - gap * 2 >=
                COMPANION_MIN_WIDTH
            // A companion column shows lyrics and the queue itself, so the
            // square goes back to being the cover.
            val shownPane = if (withCompanion) LandscapePane.ART else pane
            val roomy = maxHeight >= 420.dp

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                // ---- Artwork / lyrics / queue square ----
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .graphicsLayer {
                            if (shownPane == LandscapePane.ART) {
                                translationX = swipeToSkip.offset * SwipeToSkipDefaults.ArtFollow
                                rotationZ = swipeToSkip.offset / 80f
                            }
                        }
                ) {
                    val paneFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = shownPane,
                        transitionSpec = {
                            (fadeIn(paneFade) + scaleIn(initialScale = 0.96f)) togetherWith
                                fadeOut(paneFade)
                        },
                        label = "LandscapePane"
                    ) { shown ->
                        when (shown) {
                            LandscapePane.ART -> currentSong?.let { song ->
                                PlayerArtwork(
                                    song = song,
                                    contentDescription = song.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .swipeToSkip(swipeToSkip)
                                )
                            }
                            LandscapePane.LYRICS -> SyncedLyricsView(
                                lyricsResult = lyricsResult,
                                currentPositionMs = progress,
                                isPlaying = isPlaying,
                                onSeekTo = { viewModel.seekTo(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                            LandscapePane.QUEUE -> PlayerQueuePane(
                                viewModel = viewModel,
                                onLoadMore = onLoadMore,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // ---- Controls ----
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LandscapeChromeButton(
                            onClick = onCollapse,
                            contentDescription = stringResource(R.string.player_collapse)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(26.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        if (!withCompanion) {
                            LandscapeToggle(
                                checked = pane == LandscapePane.LYRICS,
                                onCheckedChange = {
                                    pane = if (it) LandscapePane.LYRICS else LandscapePane.ART
                                },
                                contentDescription = stringResource(R.string.ps_lyrics)
                            ) { Icon(Icons.Rounded.Lyrics, null, Modifier.size(22.dp)) }
                            LandscapeToggle(
                                checked = pane == LandscapePane.QUEUE,
                                onCheckedChange = {
                                    pane = if (it) LandscapePane.QUEUE else LandscapePane.ART
                                },
                                contentDescription = stringResource(R.string.ps_up_next)
                            ) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(22.dp)) }
                        }
                        LandscapeToggle(
                            checked = sleepTimer.active,
                            onCheckedChange = { sleepTimer.open() },
                            contentDescription = stringResource(R.string.sleep_timer_title)
                        ) { Icon(Icons.Rounded.Bedtime, null, Modifier.size(22.dp)) }
                        LandscapeToggle(
                            checked = isFavorite,
                            onCheckedChange = { viewModel.toggleCurrentSongLike() },
                            contentDescription = stringResource(R.string.action_like)
                        ) { LikeBurstIcon(isFavorite = isFavorite, iconSize = 22.dp) }
                        LandscapeChromeButton(
                            onClick = { showOptions = true },
                            contentDescription = stringResource(R.string.cd_more_options)
                        ) {
                            Icon(Icons.Rounded.MoreVert, null, Modifier.size(22.dp))
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // ---- Title block: one swipe target with the artwork ----
                    val title = currentSong?.title?.takeIf { !isUnknownTitle(it) } ?: "Untitled"
                    val artist = currentSong?.artist?.takeIf { !isUnknownArtist(it) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .swipeToSkip(swipeToSkip)
                            .swipeToSkipFollow(swipeToSkip)
                    ) {
                        Text(
                            text = title,
                            style = if (roomy) MaterialTheme.typography.headlineMedium
                            else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (artist != null) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onArtistClick(artist) }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(if (roomy) 16.dp else 8.dp))

                    PlayerSeekRow(
                        progress = progress,
                        duration = duration,
                        isPlaying = isPlaying,
                        onSeekTo = { viewModel.seekTo(it) }
                    )

                    Spacer(Modifier.height(if (roomy) 12.dp else 4.dp))

                    // ---- Transport ----
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LandscapeToggle(
                            checked = shuffleModeEnabled,
                            onCheckedChange = { viewModel.toggleShuffle() },
                            contentDescription = stringResource(R.string.cd_shuffle)
                        ) { Icon(Icons.Default.Shuffle, null, Modifier.size(22.dp)) }
                        LandscapeSkipButton(
                            onClick = onPrevious,
                            contentDescription = stringResource(R.string.cd_previous)
                        ) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(30.dp)) }
                        MorphingPlayButton(
                            isPlaying = isPlaying,
                            showBuffering = isBuffering && playWhenReady && !isPlaying,
                            onClick = {
                                playerHaptics.playPause(!viewModel.isPlaying.value)
                                viewModel.togglePlayPause()
                            },
                            size = if (roomy) 80.dp else 72.dp
                        )
                        LandscapeSkipButton(
                            onClick = onNext,
                            contentDescription = stringResource(R.string.cd_next)
                        ) { Icon(Icons.Default.SkipNext, null, Modifier.size(30.dp)) }
                        LandscapeToggle(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onCheckedChange = { viewModel.toggleRepeat() },
                            contentDescription = stringResource(R.string.cd_repeat)
                        ) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                else Icons.Default.Repeat,
                                null,
                                Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.weight(0.5f))
                }

                if (withCompanion) {
                    PlayerCompanionPanel(
                        viewModel = viewModel,
                        onLoadMore = onLoadMore,
                        ambientBackground = false,
                        // The parent already keeps clear of the safe area.
                        insets = WindowInsets(0),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(32.dp))
                    )
                }
            }
        }
    }

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

/** Below this the landscape controls column starts truncating the transport row. */
private val LANDSCAPE_CONTROLS_MIN_WIDTH = 380.dp

/**
 * The shared seek bar, in the arrangement every `ExpressiveScrubber` style
 * uses: the visual under a transparent Slider that owns the gesture, the
 * position interpolated from the clock, and a drag routed through the return
 * point so letting go on the marker is "never mind".
 */
@Composable
internal fun PlayerSeekRow(
    progress: Long,
    duration: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = scrubPosition?.toLong() ?: progress
    val scrubReturnPoint = LocalScrubReturnPoint.current
    val smoothProgress = rememberSmoothProgress(
        positionMs = progress,
        durationMs = duration,
        isPlaying = isPlaying,
        scrubPositionMs = scrubPosition
    )
    val lineStroke = Stroke(
        width = with(LocalDensity.current) { 4.dp.toPx() },
        cap = StrokeCap.Round
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            ExpressiveScrubber(
                progress = { smoothProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scrubberTrackHeight(14.dp)),
                color = color,
                trackColor = trackColor,
                stroke = lineStroke,
                trackStroke = lineStroke,
                amplitude = { if (isPlaying) 1f else 0f }
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
                    if (!cancelled) scrubPosition?.let { onSeekTo(it.toLong()) }
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
                color = labelColor
            )
            Text(
                text = formatEditorialTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor
            )
        }
    }
}

/**
 * Play/pause whose shape is the state, as Hero's disc: squared shoulders
 * while playing, a circle while paused. The corner is clamped at half the
 * size because the bouncy spring overshoots past a circle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MorphingPlayButton(
    isPlaying: Boolean,
    showBuffering: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) size * 0.3f else size / 2,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PlayButtonCorner"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner.coerceIn(0.dp, size / 2)),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (showBuffering) {
                LoadingIndicator(
                    modifier = Modifier.size(size * 0.5f),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                    modifier = Modifier.size(size * 0.45f)
                )
            }
        }
    }
}

@Composable
private fun LandscapeChromeButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(48.dp)
            .semanticsLabel(contentDescription)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun LandscapeSkipButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .size(60.dp)
            .semanticsLabel(contentDescription)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * A two-state control: filled while on, bare while off, like Editorial's
 * chips but in the theme's own roles. A toggleable Surface, so TalkBack
 * announces the state rather than just the label.
 */
@Composable
private fun LandscapeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shape = CircleShape,
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(48.dp)
            .semanticsLabel(contentDescription)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier =
    this.then(
        Modifier.semantics { this.contentDescription = label }
    )
