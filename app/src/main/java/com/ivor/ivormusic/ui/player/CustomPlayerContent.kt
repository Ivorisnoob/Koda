package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutGeometry
import com.ivor.ivormusic.data.playerlayout.LayoutPreset
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.data.playerlayout.PlayerLayoutPresets
import com.ivor.ivormusic.data.playerlayout.PlayerLayoutStore

/**
 * Custom Player - the layout the listener built, drawn from `PlayerLayoutStore`.
 *
 * Everything shared by the built-in styles still applies, because the blocks
 * are made of the same parts: swipe up for the menu and down to collapse
 * (`ExpandablePlayer`), back from the queue to the player, swipe-to-skip on the
 * cover and title, double tap on the cover to seek, the smooth seek bar with
 * its return point. With no layout saved yet it draws the Classic preset, so
 * choosing Custom never opens onto an empty screen.
 */
@Composable
fun CustomPlayerSheetContent(
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
    val context = LocalContext.current
    val store = remember(context) { PlayerLayoutStore(context) }
    val layouts by store.layouts.collectAsState()
    val activeId by store.activeId.collectAsState()
    val fallbackName = stringResource(R.string.cpl_preset_classic)
    val fallback = remember(fallbackName) { PlayerLayoutPresets.build(LayoutPreset.CLASSIC, fallbackName) }
    val layout = PlayerLayoutStore.active(layouts, activeId) ?: fallback

    val currentSong by viewModel.currentSong.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    var showQueue by rememberPlayerQueueOpen()
    var showOptions by rememberNowPlayingOptionsOpen()
    var showLyrics by remember { mutableStateOf(false) }
    val playerHaptics = rememberPlayerHaptics()
    val sleepTimer = rememberSleepTimerControl(viewModel = viewModel)
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )
    // Declared after ExpandablePlayer's handlers, so it answers first while
    // the lyrics are open; the queue's own handler wins over both.
    BackHandler(enabled = showLyrics && !showQueue) { showLyrics = false }

    val perform: (LayoutAction) -> Unit = { action ->
        when (action) {
            LayoutAction.NONE -> Unit
            LayoutAction.PLAY_PAUSE -> {
                playerHaptics.playPause(!viewModel.isPlaying.value)
                viewModel.togglePlayPause()
            }
            LayoutAction.PREVIOUS -> { playerHaptics.skip(); viewModel.skipToPrevious() }
            LayoutAction.NEXT -> { playerHaptics.skip(); viewModel.skipToNext() }
            LayoutAction.SEEK_BACK -> viewModel.seekBy(-10_000L)
            LayoutAction.SEEK_FORWARD -> viewModel.seekBy(10_000L)
            LayoutAction.LIKE -> viewModel.toggleCurrentSongLike()
            LayoutAction.SHUFFLE -> viewModel.toggleShuffle()
            LayoutAction.REPEAT -> viewModel.toggleRepeat()
            LayoutAction.QUEUE -> showQueue = true
            LayoutAction.LYRICS -> showLyrics = !showLyrics
            LayoutAction.SLEEP_TIMER -> sleepTimer.open()
            LayoutAction.RADIO -> viewModel.currentSong.value?.let { viewModel.playSongRadio(it) }
            LayoutAction.STANDBY -> viewModel.openStandby()
            LayoutAction.OPTIONS -> showOptions = true
            LayoutAction.COLLAPSE -> onCollapse()
        }
    }
    val host = LayoutHost(
        viewModel = viewModel,
        interactive = true,
        onAction = perform,
        onArtistClick = onArtistClick,
        lyricsOpen = showLyrics,
        sleepTimerActive = sleepTimer.active,
        swipeToSkip = swipeToSkip,
    )
    val safeArea = WindowInsets.safeDrawing.asPaddingValues()
    val ink = rememberLayoutInk(layout.background)

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(targetState = showQueue, label = "CustomQueueTransition") { queueVisible ->
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
                    field = ink.field,
                    accent = ink.ink
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    CustomPlayerCanvas(
                        layout = layout,
                        host = host,
                        safeArea = safeArea,
                        ambientBackground = ambientBackground,
                    )
                    AnimatedVisibility(
                        visible = showLyrics,
                        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                    ) {
                        LayoutLyricsOverlay(
                            layout = layout,
                            host = host,
                            ink = ink,
                            safeArea = safeArea,
                            onClose = { showLyrics = false }
                        )
                    }
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

/**
 * Lyrics opened from a button: they fill the screen above the playback
 * controls, so pause and skip stay where the listener put them.
 */
@Composable
private fun LayoutLyricsOverlay(
    layout: PlayerLayout,
    host: LayoutHost,
    ink: LayoutInk,
    safeArea: PaddingValues,
    onClose: () -> Unit,
) {
    val viewModel = host.viewModel
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val top = safeArea.calculateTopPadding()
        val areaHeight = (maxHeight - top - safeArea.calculateBottomPadding()).coerceAtLeast(0.dp)
        val height = top + areaHeight * LayoutGeometry.controlsTop(layout)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clipToBounds()
                .background(
                    Brush.verticalGradient(
                        0f to ink.field,
                        0.92f to ink.field,
                        1f to ink.field.copy(alpha = 0f)
                    )
                )
        ) {
            SyncedLyricsView(
                lyricsResult = lyricsResult,
                currentPositionMs = progress,
                isPlaying = isPlaying,
                onSeekTo = { viewModel.seekTo(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = top + 56.dp, bottom = 12.dp),
                primaryColor = MaterialTheme.colorScheme.primary,
                onSurfaceColor = ink.ink,
                onSurfaceVariantColor = ink.ink.copy(alpha = 0.6f),
                backgroundColor = ink.field
            )
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = top + 6.dp, end = 16.dp)
                    .size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cpl_close_lyrics),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
