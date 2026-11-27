package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.ui.components.LikeBurstIcon

/** Where the cover stops being sharp and starts dissolving into the frosted foot. */
internal const val CANVAS_ART_FRACTION = 0.70f

/**
 * Canvas's own two tones: white glyphs over a black scrim, so the chrome reads
 * over any cover without fighting the artwork's palette. Shared by Canvas and
 * by blocks in Canvas's skin.
 */
internal val CanvasGlyph = Color.White
internal val CanvasScrim = Color.Black

/**
 * The scrim the chrome sits on: a little dark under the status bar, clear
 * through the middle, darkest at the foot. Measured from [startY] to [endY],
 * which a block in Canvas's skin sets to the screen's own top and bottom.
 */
internal fun canvasScrimBrush(startY: Float = 0f, endY: Float = Float.POSITIVE_INFINITY) = Brush.verticalGradient(
    0f to CanvasScrim.copy(alpha = 0.35f),
    0.25f to Color.Transparent,
    0.55f to Color.Transparent,
    1f to CanvasScrim.copy(alpha = 0.75f),
    startY = startY,
    endY = endY
)

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
 *
 * Its pieces are shared with blocks in Canvas's skin ([CanvasSkinBlock]) and
 * tagged with [captureAs], so a custom layout started from Canvas is this
 * screen as laid out on the phone, drawn by this code.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PosterPlayerSheetContent(
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

    val playerHaptics = rememberPlayerHaptics()
    val styleWheel = LocalPlayerStyleWheelController.current

    // Swipe-to-skip: the artwork follows the finger, springs back if the
    // drag is abandoned, and the incoming cover slides in from the swipe
    // direction. Canvas is full-bleed, so the gesture sits on the whole
    // screen and the song information is already inside it — the title block
    // only has to be told to move with it.
    var skipDirection by remember { mutableIntStateOf(1) }
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { skipDirection = 1; playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { skipDirection = -1; playerHaptics.skip(); viewModel.skipToPrevious() }
    )

    // Monochrome UI over the art: white glyphs, black scrims. Reads over
    // any cover without fighting the artwork's own palette.
    val glyph = CanvasGlyph
    val scrim = CanvasScrim

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
                    field = MaterialTheme.colorScheme.surfaceContainerLowest,
                    accent = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .coverSeekOnly(LocalCoverSeek.current.takeIf { !showLyrics })
                        // Off while lyrics are up: that view scrolls and seeks.
                        .swipeToSkip(swipeToSkip, enabled = !showLyrics)
                        // Last in the chain: consumes the post-long-press
                        // hold stream before the tap and swipe detectors.
                        .styleWheelHold(styleWheel)
                ) {
                    // ========== THE FIELD ==========
                    // Built from the cover's own bottom edge so the join is continuous
                    // by construction - see CanvasField. ChromaticMistBackground was the
                    // obvious reuse and is wrong here twice over: it answers a null model
                    // with a flat rectangle, and it is fed highResThumbnailUrl, which is
                    // null for every device-library song, so it drew plain white. Its
                    // colours are also palette-derived rather than edge-derived, which
                    // cannot close a seam even when it does find artwork.
                    CanvasField(
                        song = currentSong,
                        joinAt = CANVAS_ART_FRACTION,
                        scrim = scrim,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ========== THE CANVAS: the cover itself ==========
                    CanvasArt(
                        song = currentSong,
                        skipDirection = skipDirection,
                        swipeOffset = { swipeToSkip.offset },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(CANVAS_ART_FRACTION)
                            .captureAs("canvas.art", 0) {
                                captureBlock(LayoutBlockKind.COVER, LayoutSkin.CANVAS) {
                                    copy(shape = BlockShape.SQUARE, action = LayoutAction.NONE)
                                }
                            }
                    )

                    // The chrome never leaves, so the scrim that keeps white glyphs
                    // legible over an arbitrary cover never leaves either. It also
                    // carries the whole bottom on API 30, where blur is a no-op.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(canvasScrimBrush())
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
                        ) {
                            SyncedLyricsView(
                                lyricsResult = lyricsResult,
                                currentPositionMs = progress,
                                isPlaying = isPlaying,
                                onSeekTo = { viewModel.seekTo(it) },
                                primaryColor = glyph,
                                onSurfaceColor = glyph.copy(alpha = 0.9f),
                                onSurfaceVariantColor = glyph.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // ========== TOP BAR ==========
                    Box(modifier = Modifier.align(Alignment.TopCenter)) {
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
                                size = 44.dp,
                                modifier = Modifier.captureAs("canvas.collapse", 20) {
                                    canvasButton(LayoutAction.COLLAPSE, ButtonLook.TONAL)
                                }
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
                                    size = 44.dp,
                                    modifier = Modifier.captureAs("canvas.sleep", 21) {
                                        canvasButton(LayoutAction.SLEEP_TIMER, ButtonLook.TONAL)
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.Bedtime, "Sleep timer",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                EditorialCircleButton(
                                    onClick = { showQueue = true },
                                    accent = scrim.copy(alpha = 0.35f),
                                    field = glyph,
                                    size = 44.dp,
                                    modifier = Modifier.captureAs("canvas.queue", 22) {
                                        canvasButton(LayoutAction.QUEUE, ButtonLook.TONAL)
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                EditorialCircleButton(
                                    onClick = { showOptions = true },
                                    accent = scrim.copy(alpha = 0.35f),
                                    field = glyph,
                                    size = 44.dp,
                                    modifier = Modifier.captureAs("canvas.options", 23) {
                                        canvasButton(LayoutAction.OPTIONS, ButtonLook.TONAL)
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.MoreVert, "More options",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // ========== BOTTOM CLUSTER ==========
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .navigationBarsPadding()
                                .padding(bottom = 16.dp)
                        ) {
                            // Title + like/lyrics chips
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CanvasTitle(
                                    song = currentSong,
                                    onArtistClick = onArtistClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .captureAs("canvas.title", 10) {
                                            captureBlock(LayoutBlockKind.TITLE, LayoutSkin.CANVAS)
                                        }
                                        .swipeToSkipFollow(swipeToSkip)
                                )
                                EditorialChip(
                                    checked = isFavorite,
                                    onClick = { viewModel.toggleCurrentSongLike() },
                                    accent = glyph,
                                    field = scrim,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .captureAs("canvas.like", 11) {
                                            canvasButton(LayoutAction.LIKE, ButtonLook.OUTLINED)
                                        }
                                ) {
                                    LikeBurstIcon(isFavorite = isFavorite, iconSize = 20.dp)
                                }
                                EditorialChip(
                                    checked = showLyrics,
                                    onClick = { showLyrics = !showLyrics },
                                    accent = glyph,
                                    field = scrim,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .captureAs("canvas.lyrics", 12) {
                                            canvasButton(LayoutAction.LYRICS, ButtonLook.OUTLINED)
                                        }
                                ) {
                                    Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Seek bar + times
                            CanvasSeekBar(
                                progress = progress,
                                duration = duration,
                                isPlaying = isPlaying,
                                onSeekTo = { viewModel.seekTo(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .captureAs("canvas.scrubber", 30) {
                                        captureBlock(LayoutBlockKind.SCRUBBER, LayoutSkin.CANVAS) {
                                            copy(showDetail = true)
                                        }
                                    }
                            )

                            PlayerVisualizerSlot(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .captureAs("canvas.visualizer", 31) {
                                        captureBlock(LayoutBlockKind.VISUALIZER, LayoutSkin.KODA)
                                    }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Floating pill toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = scrim.copy(alpha = 0.45f),
                                    modifier = Modifier.captureAs("canvas.toolbar", 40) {
                                        captureBlock(LayoutBlockKind.PANEL, LayoutSkin.CANVAS) {
                                            copy(shape = BlockShape.ROUND)
                                        }
                                    }
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
                                            field = scrim,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .captureAs("canvas.shuffle", 41) {
                                                    canvasButton(LayoutAction.SHUFFLE, ButtonLook.OUTLINED)
                                                }
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
                                            size = 52.dp,
                                            modifier = Modifier.captureAs("canvas.previous", 42) {
                                                canvasButton(LayoutAction.PREVIOUS, ButtonLook.PLAIN)
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious, "Previous",
                                                modifier = Modifier.size(30.dp)
                                            )
                                        }
                                        CanvasPlayButton(
                                            isPlaying = isPlaying,
                                            buffering = isBuffering && playWhenReady && !isPlaying,
                                            onClick = {
                                                playerHaptics.playPause(!viewModel.isPlaying.value)
                                                viewModel.togglePlayPause()
                                            },
                                            modifier = Modifier
                                                .size(64.dp)
                                                .captureAs("canvas.play", 43) {
                                                    canvasButton(LayoutAction.PLAY_PAUSE, ButtonLook.FILLED)
                                                }
                                        )
                                        EditorialCircleButton(
                                            onClick = {
                                                skipDirection = 1
                                                playerHaptics.skip()
                                                viewModel.skipToNext()
                                            },
                                            accent = Color.Transparent,
                                            field = glyph,
                                            size = 52.dp,
                                            modifier = Modifier.captureAs("canvas.next", 44) {
                                                canvasButton(LayoutAction.NEXT, ButtonLook.PLAIN)
                                            }
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
                                            field = scrim,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .captureAs("canvas.repeat", 45) {
                                                    canvasButton(LayoutAction.REPEAT, ButtonLook.OUTLINED)
                                                }
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

private fun canvasButton(action: LayoutAction, look: ButtonLook) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.CANVAS) { copy(action = action, look = look) }

/* ------------------------------------------------------------------ */
/* Pieces, shared with blocks in Canvas's skin                         */
/* ------------------------------------------------------------------ */

/**
 * The cover, dissolving at its foot into the field behind it. The next cover
 * slides in from the side the skip went; the art follows a swipe at a third
 * of the finger's speed, so the crop shows. [scrimOver] is drawn on the art
 * inside the same mask, for a cover drawn above the screen's own scrim.
 */
@Composable
internal fun CanvasArt(
    song: Song?,
    skipDirection: Int,
    swipeOffset: () -> Float,
    modifier: Modifier = Modifier,
    scrimOver: Brush? = null,
    motionEligible: Boolean = true,
) {
    // Measured down the art box, not the screen: the cover is solid for most of its
    // height and gives out over the last fifth, so there is no seam to see.
    val artFootMask = remember {
        Brush.verticalGradient(
            0.72f to Color.Black,
            0.88f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Transparent,
        )
    }
    val direction by rememberUpdatedState(skipDirection)
    AnimatedContent(
        targetState = song,
        transitionSpec = {
            val dir = direction
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
        modifier = modifier
            .graphicsLayer {
                // Art moves less than the finger: at 0.5x the crop shows.
                translationX = swipeOffset() * 0.35f
                // So the foot can be masked away without taking the
                // field behind it out too.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                scrimOver?.let { drawRect(brush = it) }
                drawRect(brush = artFootMask, blendMode = BlendMode.DstIn)
            },
        label = "CanvasSongSwitch"
    ) { shown ->
        if (shown != null && (shown.thumbnailUrl != null || shown.albumArtUri != null)) {
            PlayerArtwork(
                song = shown,
                contentDescription = "Album Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                motionEligible = motionEligible
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
}

/** The title in white over the scrim, with the artist under it. */
@Composable
internal fun CanvasTitle(
    song: Song?,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    artistEnabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = song?.title?.takeIf { !isUnknownTitle(it) } ?: "Untitled",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black
            ),
            color = CanvasGlyph,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val artistName = song?.artist?.takeIf { !isUnknownArtist(it) } ?: "Unknown Artist"
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleSmall,
            color = CanvasGlyph.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = artistEnabled && artistName != "Unknown Artist") {
                    onArtistClick(artistName)
                }
                .padding(vertical = 2.dp)
        )
    }
}

/**
 * The one seek bar whose thumb is a real Material thumb rather than a drawn
 * playhead, with the times under it.
 */
@Composable
internal fun CanvasSeekBar(
    progress: Long,
    duration: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTimes: Boolean = true,
) {
    Column(modifier = modifier) {
        var scrubFraction by remember { mutableStateOf<Float?>(null) }
        // Canvas is the one style whose thumb is a real Material thumb rather
        // than a drawn playhead, so it is where a once-a-second value was most
        // visible. It follows the finger while scrubbing and the clock
        // otherwise - see rememberSmoothProgress.
        val smoothProgress = rememberSmoothProgress(
            positionMs = progress,
            durationMs = duration,
            isPlaying = isPlaying,
            scrubPositionMs = scrubFraction?.let { it * duration }
        )
        Slider(
            value = smoothProgress.value,
            onValueChange = { scrubFraction = it },
            onValueChangeFinished = {
                scrubFraction?.let {
                    if (duration > 0) onSeekTo((it * duration).toLong())
                }
                scrubFraction = null
            },
            colors = SliderDefaults.colors(
                thumbColor = CanvasGlyph,
                activeTrackColor = CanvasGlyph,
                inactiveTrackColor = CanvasGlyph.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (showTimes) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatEditorialTime(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = CanvasGlyph.copy(alpha = 0.7f)
                )
                Text(
                    text = formatEditorialTime(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = CanvasGlyph.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Play in a solid white disc, the one filled thing on the toolbar. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CanvasPlayButton(
    isPlaying: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier.size(34.dp),
) {
    EditorialCircleButton(
        onClick = onClick,
        accent = CanvasGlyph,
        field = CanvasScrim,
        modifier = modifier
    ) {
        if (buffering) {
            LoadingIndicator(
                modifier = Modifier.size(28.dp),
                color = CanvasScrim,
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
                if (isPlaying) "Pause" else "Play",
                modifier = iconModifier
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Canvas's skin for custom layouts                                    */
/* ------------------------------------------------------------------ */

/**
 * A block drawn by Canvas's own pieces, white over the scrim. Filled is the
 * white disc, plain the bare circle the skips sit in, tonal the dark plate the
 * top bar's buttons sit on (lit white while on), outlined the chip; a panel is
 * the dark pill the toolbar floats on.
 */
@Composable
internal fun CanvasSkinBlock(
    block: LayoutBlock,
    host: LayoutHost,
    motionArtwork: Boolean,
    fallback: @Composable (LayoutInk) -> Unit,
) {
    val song by host.viewModel.currentSong.collectAsState()
    when (block.kind) {
        LayoutBlockKind.COVER -> CanvasSkinCover(block, host, song, motionArtwork)
        LayoutBlockKind.TITLE -> SkinTitleFrame(block, host) { frame ->
            CanvasTitle(
                song = song,
                onArtistClick = host.onArtistClick,
                artistEnabled = host.interactive,
                modifier = frame
            )
        }
        LayoutBlockKind.SCRUBBER -> {
            val progress by host.viewModel.progress.collectAsState()
            val duration by host.viewModel.duration.collectAsState()
            val isPlaying by host.viewModel.isPlaying.collectAsState()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CanvasSeekBar(
                    progress = progress,
                    duration = duration,
                    isPlaying = isPlaying,
                    onSeekTo = { if (host.interactive) host.viewModel.seekTo(it) },
                    showTimes = block.showDetail,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        LayoutBlockKind.BUTTON -> CanvasSkinButton(block, host)
        LayoutBlockKind.PANEL -> Box(
            Modifier
                .fillMaxSize()
                .clip(block.shape.composeShape())
                .background(CanvasScrim.copy(alpha = 0.45f))
        )
        else -> fallback(LayoutInk(CanvasScrim, CanvasGlyph))
    }
}

/**
 * Canvas's cover: the art with its dissolving foot, the screen's scrim drawn
 * over it the way Canvas lays the scrim over its art, and the lyrics opened
 * over the whole screen beneath the chrome.
 */
@Composable
private fun CanvasSkinCover(block: LayoutBlock, host: LayoutHost, song: Song?, primary: Boolean) {
    val frame = LocalBlockFrame.current
    val scrim = remember(frame?.rect, frame?.screenHeight) {
        frame?.let { canvasScrimBrush(startY = -it.rect.top, endY = it.screenHeight - it.rect.top) }
    }
    val showLyrics = host.lyricsOpen && primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layoutCoverGestures(block, host, follow = null, enabled = !showLyrics)
    ) {
        CanvasArt(
            song = song,
            skipDirection = host.skipDirection,
            swipeOffset = { host.swipeToSkip?.offset ?: 0f },
            scrimOver = scrim,
            motionEligible = primary,
            modifier = Modifier.fillMaxSize()
        )
        if (frame != null) {
            AnimatedVisibility(
                visible = showLyrics,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.screenSized(frame)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CanvasScrim.copy(alpha = 0.6f))
                ) {
                    CoverLyrics(
                        host = host,
                        primary = CanvasGlyph,
                        ink = CanvasGlyph.copy(alpha = 0.9f),
                        inkVariant = CanvasGlyph.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasSkinButton(block: LayoutBlock, host: LayoutHost) {
    val action = block.action
    val state = rememberLayoutActionState(action, host)
    val onClick = { host.onAction(action) }
    val label = action.label()
    val fill = Modifier.fillMaxSize()
    when (block.look) {
        ButtonLook.FILLED -> if (action == LayoutAction.PLAY_PAUSE) {
            CanvasPlayButton(
                isPlaying = state.isPlaying,
                buffering = state.buffering,
                onClick = onClick,
                modifier = fill,
                iconModifier = Modifier.layoutIcon(34.dp)
            )
        } else {
            EditorialCircleButton(onClick = onClick, accent = CanvasGlyph, field = CanvasScrim, modifier = fill) {
                LayoutActionIcon(action, state, label, Modifier.layoutIcon(30.dp), ActionGlyphs.FILLED)
            }
        }
        ButtonLook.PLAIN -> EditorialCircleButton(
            onClick = onClick,
            accent = Color.Transparent,
            field = CanvasGlyph,
            modifier = fill
        ) {
            LayoutActionIcon(action, state, label, Modifier.layoutIcon(30.dp), ActionGlyphs.FILLED)
        }
        ButtonLook.TONAL -> {
            // Solid while on: over artwork the scrim's alpha is the only other
            // contrast there is.
            val lit = state.checked == true
            EditorialCircleButton(
                onClick = onClick,
                accent = if (lit) CanvasGlyph else CanvasScrim.copy(alpha = 0.35f),
                field = if (lit) CanvasScrim else CanvasGlyph,
                modifier = fill
            ) {
                LayoutActionIcon(
                    action, state, label,
                    Modifier.layoutIcon(if (action == LayoutAction.COLLAPSE) 26.dp else 22.dp),
                    ActionGlyphs.FILLED
                )
            }
        }
        ButtonLook.OUTLINED -> EditorialChip(
            checked = state.checked == true,
            onClick = onClick,
            accent = CanvasGlyph,
            field = CanvasScrim,
            modifier = fill
        ) {
            LayoutActionIcon(action, state, label, Modifier.layoutIcon(20.dp), ActionGlyphs.FILLED)
        }
    }
}
