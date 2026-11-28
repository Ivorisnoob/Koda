package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import androidx.compose.foundation.layout.RowScope
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutSkin

/**
 * Bento Player - the squish grid style.
 *
 * The whole screen is a bento box of flat tonal tiles with physical mass.
 * Pure-flat contract: no gradients, no shadows, no scrims - hierarchy comes
 * from the tonal surface ladder and tile size alone.
 *
 * Signature moves:
 * - Transport tiles have press mass: the pressed tile expands while its
 *   neighbors compress, springing back on release (the ButtonGroup squish
 *   physics generalized to layout weights).
 * - The art tile's corner radius slowly breathes while music plays and
 *   settles when paused - the grid itself is the play state.
 * - Progress is a hard-edged two-tone fill inside its own tile; the color
 *   boundary is the playhead.
 * - Checked toggles morph rounder and jump tonal color, never elevate.
 *
 * See docs/PLAYER_STYLES_PURE_EXPRESSIVE_CONCEPTS.md section 2.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BentoPlayerSheetContent(
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
    val styleWheel = LocalPlayerStyleWheelController.current

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
    var showOptions by rememberNowPlayingOptionsOpen()

    // Swipe-to-skip, shared by the art tile and the title tile so both
    // commit at the same threshold with the same spring home.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )

    val boardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val tileColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val onTile = MaterialTheme.colorScheme.onSurface

    // Bento's own emphasis colour, the one BentoToggleTile lights up with.
    val sleepTimer = rememberSleepTimerControl(
        viewModel = viewModel,
        accent = MaterialTheme.colorScheme.tertiaryContainer,
        onAccent = MaterialTheme.colorScheme.onTertiaryContainer
    )
    val onTileVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(boardColor)
    ) {
        Crossfade(targetState = showQueue, label = "BentoQueueTransition") { queueVisible ->
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
                    field = boardColor,
                    accent = onTile
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ========== TOP ROW ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoTile(
                            onClick = onCollapse,
                            color = tileColor,
                            contentColor = onTile,
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                                .captureAs("bento.collapse", 20) { bentoButton(LayoutAction.COLLAPSE, ButtonLook.TONAL) }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        BentoHeaderTile(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .captureAs("bento.header", 21) {
                                    captureBlock(LayoutBlockKind.HEADER, LayoutSkin.BENTO)
                                }
                        )
                        BentoTile(
                            onClick = sleepTimer.open,
                            color = if (sleepTimer.active) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else tileColor,
                            contentColor = if (sleepTimer.active) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else onTile,
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                                .captureAs("bento.sleep", 22) { bentoButton(LayoutAction.SLEEP_TIMER, ButtonLook.TONAL) }
                        ) {
                            Icon(
                                Icons.Rounded.Bedtime, "Sleep timer",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        BentoTile(
                            onClick = { showOptions = true },
                            color = tileColor,
                            contentColor = onTile,
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                                .captureAs("bento.options", 23) { bentoButton(LayoutAction.OPTIONS, ButtonLook.TONAL) }
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert, "More options",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // ========== ART TILE (corner-breathing) ==========
                    val artCorner = rememberBentoArtCorner(isPlaying)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .captureAs("bento.art", 0) {
                                captureBlock(LayoutBlockKind.COVER, LayoutSkin.BENTO) {
                                    copy(action = LayoutAction.PLAY_PAUSE)
                                }
                            }
                            // Outside the clip, so the whole tile slides with
                            // the finger instead of the art sliding inside it.
                            .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                            .clip(RoundedCornerShape(artCorner.dp))
                            .background(tileColor)
                            .coverTaps(LocalCoverSeek.current) {
                                playerHaptics.playPause(!viewModel.isPlaying.value)
                                viewModel.togglePlayPause()
                            }
                            // Off while lyrics are up: that view scrolls and seeks.
                            .swipeToSkip(swipeToSkip, enabled = !showLyrics)
                            .styleWheelHold(styleWheel)
                    ) {
                        Crossfade(targetState = showLyrics, label = "BentoArtLyrics") { lyricsVisible ->
                            if (lyricsVisible) {
                                SyncedLyricsView(
                                    lyricsResult = lyricsResult,
                                    currentPositionMs = progress,
                                    isPlaying = isPlaying,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    onSurfaceColor = onTile,
                                    onSurfaceVariantColor = onTileVariant,
                                    backgroundColor = tileColor
                                )
                            } else {
                                BentoArt(song = currentSong)
                            }
                        }
                    }

                    // ========== TITLE TILE ==========
                    BentoTitleTile(
                        song = currentSong,
                        onArtistClick = onArtistClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("bento.title", 10) {
                                captureBlock(LayoutBlockKind.TITLE, LayoutSkin.BENTO)
                            }
                            .swipeToSkipFollow(swipeToSkip)
                            .swipeToSkip(swipeToSkip)
                    )

                    // ========== PROGRESS TILE (hard two-tone fill) ==========
                    BentoProgressTile(
                        progress = progress,
                        duration = duration,
                        isPlaying = isPlaying,
                        onSeekTo = { viewModel.seekTo(it) },
                        trackColor = tileColor,
                        fillColor = MaterialTheme.colorScheme.primaryContainer,
                        onTile = onTile,
                        onTileVariant = onTileVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("bento.scrubber", 30) {
                                captureBlock(LayoutBlockKind.SCRUBBER, LayoutSkin.BENTO) {
                                    copy(showDetail = true)
                                }
                            }
                            .height(64.dp)
                    )

                    PlayerVisualizerSlot(
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("bento.visualizer", 31) {
                                captureBlock(LayoutBlockKind.VISUALIZER, LayoutSkin.KODA)
                            }
                    )

                    // ========== TRANSPORT ROW (squish physics) ==========
                    BentoTransportRow(
                        isPlaying = isPlaying,
                        buffering = isBuffering && playWhenReady && !isPlaying,
                        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() },
                        onPlayPause = {
                            playerHaptics.playPause(!viewModel.isPlaying.value)
                            viewModel.togglePlayPause()
                        },
                        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("bento.transport", 40) {
                                captureBlock(LayoutBlockKind.TRANSPORT, LayoutSkin.BENTO) {
                                    copy(look = ButtonLook.FILLED)
                                }
                            }
                            .height(84.dp)
                    )

                    // ========== STATUS ROW ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoToggleTile(
                            checked = isFavorite,
                            onClick = { viewModel.toggleCurrentSongLike() },
                            uncheckedColor = tileColor,
                            uncheckedContent = onTileVariant,
                            capture = Modifier.captureAs("bento.like", 50) { bentoButton(LayoutAction.LIKE, ButtonLook.OUTLINED) }
                        ) {
                            LikeBurstIcon(isFavorite = isFavorite, iconSize = 22.dp)
                        }
                        BentoToggleTile(
                            checked = shuffleModeEnabled,
                            onClick = { viewModel.toggleShuffle() },
                            uncheckedColor = tileColor,
                            uncheckedContent = onTileVariant,
                            capture = Modifier.captureAs("bento.shuffle", 51) { bentoButton(LayoutAction.SHUFFLE, ButtonLook.OUTLINED) }
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(22.dp))
                        }
                        BentoToggleTile(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onClick = { viewModel.toggleRepeat() },
                            uncheckedColor = tileColor,
                            uncheckedContent = onTileVariant,
                            capture = Modifier.captureAs("bento.repeat", 52) { bentoButton(LayoutAction.REPEAT, ButtonLook.OUTLINED) }
                        ) {
                            Icon(
                                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                else Icons.Default.Repeat,
                                "Repeat",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        BentoToggleTile(
                            checked = showLyrics,
                            onClick = { showLyrics = !showLyrics },
                            uncheckedColor = tileColor,
                            uncheckedContent = onTileVariant,
                            capture = Modifier.captureAs("bento.lyrics", 53) { bentoButton(LayoutAction.LYRICS, ButtonLook.OUTLINED) }
                        ) {
                            Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(22.dp))
                        }
                        // Up-next peek tile: expands into the queue.
                        BentoUpNextTile(
                            queue = currentQueue,
                            currentQueueItemId = currentQueueItemId,
                            onClick = { showQueue = true },
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight()
                                .captureAs("bento.upnext", 54) {
                                    captureBlock(LayoutBlockKind.UP_NEXT, LayoutSkin.BENTO)
                                }
                        )
                    }

                    Spacer(modifier = Modifier.navigationBarsPadding())
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

/** Plain flat tile with a click action. */
@Composable
internal fun BentoTile(
    onClick: () -> Unit,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = color,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Transport tile with press mass: while pressed its layout weight grows so
 * neighbors physically compress, springing back on release.
 */
@Composable
private fun RowScope.BentoSquishTile(
    baseWeight: Float,
    onClick: () -> Unit,
    color: Color,
    contentColor: Color,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val weight by animateFloatAsState(
        targetValue = if (pressed) baseWeight * 1.35f else baseWeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BentoSquish"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .weight(weight.coerceAtLeast(0.1f))
            .fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        color = color,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Square toggle tile: checked state jumps to tertiaryContainer and morphs
 * rounder - tonal color and corner radius carry the state, never elevation.
 */
@Composable
private fun RowScope.BentoToggleTile(
    checked: Boolean,
    onClick: () -> Unit,
    uncheckedColor: Color,
    uncheckedContent: Color,
    capture: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BentoToggleSurface(
        checked = checked,
        onClick = onClick,
        uncheckedColor = uncheckedColor,
        uncheckedContent = uncheckedContent,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(capture),
        content = content
    )
}

/** [BentoToggleTile] at any size, for blocks in Bento's skin. */
@Composable
internal fun BentoToggleSurface(
    checked: Boolean,
    onClick: () -> Unit,
    uncheckedColor: Color,
    uncheckedContent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (checked) 26.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BentoToggleCorner"
    )
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = if (checked) MaterialTheme.colorScheme.tertiaryContainer else uncheckedColor,
        contentColor = if (checked) MaterialTheme.colorScheme.onTertiaryContainer else uncheckedContent
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Progress as a tile: a hard-edged two-tone fill whose color boundary is
 * the playhead. An invisible slider on top scrubs; one seek on release.
 */
@Composable
internal fun BentoProgressTile(
    progress: Long,
    duration: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    trackColor: Color,
    fillColor: Color,
    onTile: Color,
    onTileVariant: Color,
    modifier: Modifier = Modifier.fillMaxWidth().height(64.dp),
    showTimes: Boolean = true
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = scrubPosition?.toLong() ?: progress
    // Advanced from the clock each frame rather than sprung toward a
    // once-a-second sample - see rememberSmoothProgress. The boundary is this
    // tile's playhead, so a stepping fill is as visible here as a thumb.
    val smoothProgress = rememberSmoothProgress(
        positionMs = progress,
        durationMs = duration,
        isPlaying = isPlaying,
        scrubPositionMs = scrubPosition
    )
    val fillFraction = smoothProgress.value

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(trackColor)
    ) {
        if (fillFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .fillMaxHeight()
                    .background(fillColor)
            )
        }
        if (showTimes) Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatEditorialTime(displayedProgress),
                style = MaterialTheme.typography.labelLarge,
                color = onTile
            )
            Text(
                text = formatEditorialTime(duration),
                style = MaterialTheme.typography.labelLarge,
                color = onTileVariant
            )
        }
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
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun bentoButton(action: LayoutAction, look: ButtonLook) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.BENTO) { copy(action = action, look = look) }

/* ------------------------------------------------------------------ */
/* Pieces, shared with blocks in Bento's skin                          */
/* ------------------------------------------------------------------ */

/**
 * The art tile's corner, which slowly breathes while music plays and settles
 * when it stops: the grid itself is the play state.
 */
@Composable
internal fun rememberBentoArtCorner(isPlaying: Boolean): Float {
    val infinite = rememberInfiniteTransition(label = "BentoBreath")
    val breathing by infinite.animateFloat(
        initialValue = 28f,
        targetValue = 52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BentoBreathValue"
    )
    // Spring chases a moving target while playing and a fixed one while
    // paused, so pause settles smoothly.
    val artCorner by animateFloatAsState(
        targetValue = if (isPlaying) breathing else 36f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "BentoArtCorner"
    )
    return artCorner
}

/** The cover filling its tile, or a quiet note where there is none. */
@Composable
internal fun BentoArt(song: Song?, motionEligible: Boolean = true) {
    val artSong = song?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
    if (artSong != null) {
        PlayerArtwork(
            song = artSong,
            contentDescription = "Album Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            motionEligible = motionEligible
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(96.dp)
            )
        }
    }
}

/** "NOW PLAYING" in spaced capitals on a flat tile. */
@Composable
internal fun BentoHeaderTile(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The title and artist on their own flat tile. */
@Composable
internal fun BentoTitleTile(
    song: Song?,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    artistEnabled: Boolean = true,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(
                text = song?.title?.takeIf { !isUnknownTitle(it) } ?: "Untitled",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artistName = song?.artist?.takeIf { !isUnknownArtist(it) } ?: "Unknown Artist"
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = artistEnabled && artistName != "Unknown Artist") {
                        onArtistClick(artistName)
                    }
            )
        }
    }
}

/**
 * Previous, play and next as tiles with press mass: the pressed tile expands
 * while its neighbours compress, springing back on release.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BentoTransportRow(
    isPlaying: Boolean,
    buffering: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BentoSquishTile(
            baseWeight = 1f,
            onClick = onPrevious,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                Icons.Default.SkipPrevious, "Previous",
                modifier = Modifier.size(32.dp)
            )
        }
        BentoSquishTile(
            baseWeight = 1.6f,
            onClick = onPlayPause,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            if (buffering) {
                LoadingIndicator(
                    modifier = Modifier.size(36.dp),
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
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        BentoSquishTile(
            baseWeight = 1f,
            onClick = onNext,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                Icons.Default.SkipNext, "Next",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** The up-next peek: the next song's title on a tile that opens the queue. */
@Composable
internal fun BentoUpNextTile(
    queue: List<MusicQueueItem>,
    currentQueueItemId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Queue",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // index + 1 is the real next song now that this list is the play
            // order rather than the order the queue was built in. The -1 guard
            // matters for the same reason: unfound would have read getOrNull(0)
            // and named the first song in the queue as the one coming up.
            val nextTitle = remember(queue, currentQueueItemId) {
                val index = queue.indexOfFirst { it.id == currentQueueItemId }
                if (index < 0) "End of queue"
                else queue.getOrNull(index + 1)?.song?.title ?: "End of queue"
            }
            Text(
                text = nextTitle,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Bento's skin for custom layouts                                     */
/* ------------------------------------------------------------------ */

/**
 * A block drawn by Bento's own tiles. Tonal is the flat tile of the top row
 * (lit in the tertiary tone while on), outlined and plain the toggle tile that
 * rounds and lights when on, filled the primary tile; the transport is the
 * squishing row, Up next the peek tile.
 */
@Composable
internal fun BentoSkinBlock(
    block: LayoutBlock,
    host: LayoutHost,
    motionArtwork: Boolean,
    fallback: @Composable (LayoutInk) -> Unit,
) {
    val viewModel = host.viewModel
    val song by viewModel.currentSong.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val tileColor = scheme.surfaceContainerHigh
    when (block.kind) {
        LayoutBlockKind.COVER -> {
            val isPlaying by viewModel.isPlaying.collectAsState()
            val artCorner = rememberBentoArtCorner(isPlaying)
            val showLyrics = host.lyricsOpen && motionArtwork
            val swipe = host.swipeToSkip
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Outside the clip, so the whole tile slides with the
                    // finger instead of the art sliding inside it.
                    .then(
                        if (swipe != null && host.interactive && block.swipeToSkip) {
                            Modifier.swipeToSkipFollow(swipe, SwipeToSkipDefaults.ArtFollow)
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(artCorner.dp))
                    .background(tileColor)
                    .layoutCoverGestures(block, host, follow = null, enabled = !showLyrics)
            ) {
                Crossfade(targetState = showLyrics, label = "BentoSkinArtLyrics") { lyrics ->
                    if (lyrics) {
                        CoverLyrics(
                            host = host,
                            primary = scheme.primary,
                            ink = scheme.onSurface,
                            inkVariant = scheme.onSurfaceVariant,
                            background = tileColor
                        )
                    } else {
                        BentoArt(song = song, motionEligible = motionArtwork)
                    }
                }
            }
        }
        LayoutBlockKind.TITLE -> SkinTitleFrame(block, host) { frame ->
            BentoTitleTile(
                song = song,
                onArtistClick = host.onArtistClick,
                artistEnabled = host.interactive,
                modifier = frame
            )
        }
        LayoutBlockKind.HEADER -> BentoHeaderTile(Modifier.fillMaxSize())
        LayoutBlockKind.SCRUBBER -> {
            val progress by viewModel.progress.collectAsState()
            val duration by viewModel.duration.collectAsState()
            val isPlaying by viewModel.isPlaying.collectAsState()
            BentoProgressTile(
                progress = progress,
                duration = duration,
                isPlaying = isPlaying,
                onSeekTo = { if (host.interactive) viewModel.seekTo(it) },
                trackColor = tileColor,
                fillColor = scheme.primaryContainer,
                onTile = scheme.onSurface,
                onTileVariant = scheme.onSurfaceVariant,
                showTimes = block.showDetail,
                modifier = Modifier.fillMaxSize()
            )
        }
        LayoutBlockKind.TRANSPORT -> {
            val isPlaying by viewModel.isPlaying.collectAsState()
            val isBuffering by viewModel.isBuffering.collectAsState()
            val playWhenReady by viewModel.playWhenReady.collectAsState()
            BentoTransportRow(
                isPlaying = isPlaying,
                buffering = isBuffering && playWhenReady && !isPlaying,
                onPrevious = { host.onAction(LayoutAction.PREVIOUS) },
                onPlayPause = { host.onAction(LayoutAction.PLAY_PAUSE) },
                onNext = { host.onAction(LayoutAction.NEXT) },
                modifier = Modifier.fillMaxSize()
            )
        }
        LayoutBlockKind.BUTTON -> BentoSkinButton(block, host)
        LayoutBlockKind.UP_NEXT -> {
            val queue by viewModel.currentQueue.collectAsState()
            val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
            BentoUpNextTile(
                queue = queue,
                currentQueueItemId = currentQueueItemId,
                onClick = { host.onAction(LayoutAction.QUEUE) },
                modifier = Modifier.fillMaxSize()
            )
        }
        else -> fallback(LayoutInk(scheme.surfaceContainerLowest, scheme.onSurface))
    }
}

@Composable
private fun BentoSkinButton(block: LayoutBlock, host: LayoutHost) {
    val action = block.action
    val state = rememberLayoutActionState(action, host)
    val onClick = rememberBlockAction(block, host)
    val label = action.label()
    val scheme = MaterialTheme.colorScheme
    val fill = Modifier.fillMaxSize()
    val checked = state.checked == true
    when (block.look) {
        ButtonLook.OUTLINED, ButtonLook.PLAIN -> BentoToggleSurface(
            checked = checked,
            onClick = onClick,
            uncheckedColor = scheme.surfaceContainerHigh,
            uncheckedContent = scheme.onSurfaceVariant,
            modifier = fill
        ) {
            LayoutActionIcon(action, state, label, Modifier.layoutIcon(22.dp), ActionGlyphs.FILLED)
        }
        ButtonLook.FILLED -> BentoTile(
            onClick = onClick,
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            modifier = fill
        ) {
            LayoutActionIcon(
                action, state, label,
                Modifier.layoutIcon(if (action == LayoutAction.PLAY_PAUSE) 40.dp else 26.dp),
                ActionGlyphs.FILLED
            )
        }
        ButtonLook.TONAL -> BentoTile(
            onClick = onClick,
            color = if (checked) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (checked) scheme.onTertiaryContainer else scheme.onSurface,
            modifier = fill
        ) {
            LayoutActionIcon(
                action, state, label,
                Modifier.layoutIcon(if (action == LayoutAction.COLLAPSE) 26.dp else 24.dp),
                ActionGlyphs.FILLED
            )
        }
    }
}
