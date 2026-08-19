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
import androidx.compose.material.icons.rounded.MusicNote
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
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.SongArtwork

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
    onArtistClick: (String) -> Unit = {}
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
    val isFavorite by viewModel.isCurrentSongLiked.collectAsState()
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

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
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp),
                            color = tileColor,
                            contentColor = onTileVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "NOW PLAYING",
                                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
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
                        ) {
                            Icon(
                                Icons.Rounded.Bedtime, "Sleep timer",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        BentoTile(
                            onClick = { showAddToPlaylist = true },
                            color = tileColor,
                            contentColor = onTile,
                            modifier = Modifier
                                .width(56.dp)
                                .fillMaxHeight()
                        ) {
                            Icon(
                                Icons.Rounded.PlaylistAdd, "Add to Playlist",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // ========== ART TILE (corner-breathing) ==========
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
                    // Spring chases a moving target while playing and a
                    // fixed one while paused, so pause settles smoothly.
                    val artCorner by animateFloatAsState(
                        targetValue = if (isPlaying) breathing else 36f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "BentoArtCorner"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // Outside the clip, so the whole tile slides with
                            // the finger instead of the art sliding inside it.
                            .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                            .clip(RoundedCornerShape(artCorner.dp))
                            .background(tileColor)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = {
                                    playerHaptics.playPause(!viewModel.isPlaying.value)
                                    viewModel.togglePlayPause()
                                })
                            }
                            // Off while lyrics are up: that view scrolls and seeks.
                            .swipeToSkip(swipeToSkip, enabled = !showLyrics)
                            .styleWheelHold(styleWheel)
                    ) {
                        Crossfade(targetState = showLyrics, label = "BentoArtLyrics") { lyricsVisible ->
                            if (lyricsVisible) {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    SyncedLyricsView(
                                        lyricsResult = lyricsResult,
                                        currentPositionMs = progress,
                                        onSeekTo = { viewModel.seekTo(it) },
                                        primaryColor = MaterialTheme.colorScheme.primary,
                                        onSurfaceColor = onTile,
                                        onSurfaceVariantColor = onTileVariant
                                    )
                                }
                            } else {
                                val artSong = currentSong?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
                                if (artSong != null) {
                                    SongArtwork(
                                        song = artSong,
                                        contentDescription = "Album Art",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = onTileVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(96.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ========== TITLE TILE ==========
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .swipeToSkipFollow(swipeToSkip)
                            .swipeToSkip(swipeToSkip),
                        shape = RoundedCornerShape(20.dp),
                        color = tileColor
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(
                                text = currentSong?.title?.takeIf { !it.startsWith("Unknown") }
                                    ?: "Untitled",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = onTile,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val artistName = currentSong?.artist?.takeIf { !it.startsWith("Unknown") }
                                ?: "Unknown Artist"
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = onTileVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(enabled = artistName != "Unknown Artist") {
                                        onArtistClick(artistName)
                                    }
                            )
                        }
                    }

                    // ========== PROGRESS TILE (hard two-tone fill) ==========
                    BentoProgressTile(
                        progress = progress,
                        duration = duration,
                        onSeekTo = { viewModel.seekTo(it) },
                        trackColor = tileColor,
                        fillColor = MaterialTheme.colorScheme.primaryContainer,
                        onTile = onTile,
                        onTileVariant = onTileVariant
                    )

                    // ========== TRANSPORT ROW (squish physics) ==========
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BentoSquishTile(
                            baseWeight = 1f,
                            onClick = { playerHaptics.skip(); viewModel.skipToPrevious() },
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
                            onClick = {
                                playerHaptics.playPause(!viewModel.isPlaying.value)
                                viewModel.togglePlayPause()
                            },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            if (isBuffering && playWhenReady && !isPlaying) {
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
                            onClick = { playerHaptics.skip(); viewModel.skipToNext() },
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(
                                Icons.Default.SkipNext, "Next",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

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
                            uncheckedContent = onTileVariant
                        ) {
                            LikeBurstIcon(isFavorite = isFavorite, iconSize = 22.dp)
                        }
                        BentoToggleTile(
                            checked = shuffleModeEnabled,
                            onClick = { viewModel.toggleShuffle() },
                            uncheckedColor = tileColor,
                            uncheckedContent = onTileVariant
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(22.dp))
                        }
                        BentoToggleTile(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onClick = { viewModel.toggleRepeat() },
                            uncheckedColor = tileColor,
                            uncheckedContent = onTileVariant
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
                            uncheckedContent = onTileVariant
                        ) {
                            Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(22.dp))
                        }
                        // Up-next peek tile: expands into the queue.
                        Surface(
                            onClick = { showQueue = true },
                            modifier = Modifier
                                .weight(2f)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp),
                            color = tileColor,
                            contentColor = onTileVariant
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
                                val nextTitle = remember(currentQueue, currentSong?.id) {
                                    val index = currentQueue.indexOfFirst { it.id == currentSong?.id }
                                    currentQueue.getOrNull(index + 1)?.title ?: "End of queue"
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

                    Spacer(modifier = Modifier.navigationBarsPadding())
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

/** Plain flat tile with a click action. */
@Composable
private fun BentoTile(
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
private fun androidx.compose.foundation.layout.RowScope.BentoSquishTile(
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
private fun androidx.compose.foundation.layout.RowScope.BentoToggleTile(
    checked: Boolean,
    onClick: () -> Unit,
    uncheckedColor: Color,
    uncheckedContent: Color,
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
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
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
private fun BentoProgressTile(
    progress: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    trackColor: Color,
    fillColor: Color,
    onTile: Color,
    onTileVariant: Color
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = scrubPosition?.toLong() ?: progress
    val fraction = if (duration > 0) {
        (displayedProgress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "BentoProgressFill"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(trackColor)
    ) {
        if (animatedFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .fillMaxHeight()
                    .background(fillColor)
            )
        }
        Row(
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
