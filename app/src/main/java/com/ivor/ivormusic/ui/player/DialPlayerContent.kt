package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dial Player - the rotary instrument style.
 *
 * Replaces the seek bar with a huge flat tick-ring dial. The ring spins
 * one full revolution per track past a fixed needle at twelve o'clock,
 * with played ticks filled solid and upcoming ticks hollow. Dragging
 * around the ring physically rotates it to scrub, clicking a haptic
 * detent every few ticks; one seek fires on release. Pure-flat: strokes,
 * dots and flat fills only.
 *
 * The center puck is the album art die-cut per track; it morphs to a
 * circle on pause, tap toggles playback and long-press opens the style
 * wheel (the queue lives behind its top-bar button).
 *
 * See docs/PLAYER_STYLES_PURE_EXPRESSIVE_CONCEPTS.md section 3.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialPlayerSheetContent(
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

    // Swipe-to-skip, shared by the dial's puck art and the title/artist
    // block so both commit at the same threshold with the same spring home.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )
    var showOptions by rememberNowPlayingOptionsOpen()

    val field = MaterialTheme.colorScheme.surfaceContainerLowest
    val ink = MaterialTheme.colorScheme.onSurface
    val inkVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val chipColor = MaterialTheme.colorScheme.surfaceContainerHigh

    // The dial's instrument accent carries into the picker.
    val sleepTimer = rememberSleepTimerControl(viewModel = viewModel, accent = accent)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(field)
    ) {
        Crossfade(targetState = showQueue, label = "DialQueueTransition") { queueVisible ->
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
                    field = field,
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
                            field = ink,
                            size = 44.dp,
                            modifier = Modifier.captureAs("dial.collapse", 20) {
                                dialButton(LayoutAction.COLLAPSE, ButtonLook.TONAL)
                            }
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown, "Collapse",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditorialCircleButton(
                                onClick = sleepTimer.open,
                                accent = if (sleepTimer.active) accent else chipColor,
                                field = if (sleepTimer.active) MaterialTheme.colorScheme.onPrimary else ink,
                                size = 44.dp,
                                modifier = Modifier.captureAs("dial.sleep", 21) {
                                    dialButton(LayoutAction.SLEEP_TIMER, ButtonLook.TONAL)
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Bedtime, "Sleep timer",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            EditorialCircleButton(
                                onClick = { showQueue = true },
                                accent = chipColor,
                                field = ink,
                                size = 44.dp,
                                modifier = Modifier.captureAs("dial.queue", 22) {
                                    dialButton(LayoutAction.QUEUE, ButtonLook.TONAL)
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic, "Queue",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            EditorialCircleButton(
                                onClick = { showOptions = true },
                                accent = chipColor,
                                field = ink,
                                size = 44.dp,
                                modifier = Modifier.captureAs("dial.options", 23) {
                                    dialButton(LayoutAction.OPTIONS, ButtonLook.TONAL)
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert, "More options",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // ========== THE DIAL / LYRICS ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .captureAs("dial.art", 0) {
                                captureBlock(LayoutBlockKind.COVER, LayoutSkin.DIAL) {
                                    copy(action = LayoutAction.PLAY_PAUSE)
                                }
                            }
                    ) {
                        Crossfade(targetState = showLyrics, label = "DialLyrics") { lyricsVisible ->
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
                                RotaryDial(
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering && playWhenReady && !isPlaying,
                                    progress = progress,
                                    duration = duration,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onPlayPause = {
                                        playerHaptics.playPause(!viewModel.isPlaying.value)
                                        viewModel.togglePlayPause()
                                    },
                                    swipeToSkip = swipeToSkip
                                )
                            }
                        }
                    }

                    // ========== READOUT ==========
                    Text(
                        text = "${formatEditorialTime(progress)}  /  ${formatEditorialTime(duration)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        ),
                        color = ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("dial.time", 30) {
                                captureBlock(LayoutBlockKind.TIME, LayoutSkin.DIAL)
                            }
                    )

                    PlayerVisualizerSlot(
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("dial.visualizer", 31) {
                                captureBlock(LayoutBlockKind.VISUALIZER, LayoutSkin.KODA)
                            }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // ========== TITLE / ARTIST ==========
                    // Wrapped so the song information is one swipe target the
                    // full width of the player, not two text-shaped ones.
                    DialTitle(
                        song = currentSong,
                        onArtistClick = onArtistClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("dial.title", 10) {
                                captureBlock(LayoutBlockKind.TITLE, LayoutSkin.DIAL)
                            }
                            .swipeToSkip(swipeToSkip)
                            .swipeToSkipFollow(swipeToSkip)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // ========== CONTROLS ==========
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
                            field = ink,
                            size = 56.dp,
                            modifier = Modifier.captureAs("dial.previous", 40) {
                                dialButton(LayoutAction.PREVIOUS, ButtonLook.FILLED)
                            }
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious, "Previous",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        EditorialChip(
                            checked = isFavorite,
                            onClick = { viewModel.toggleCurrentSongLike() },
                            accent = accent,
                            field = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .captureAs("dial.like", 41) {
                                    dialButton(LayoutAction.LIKE, ButtonLook.OUTLINED)
                                }
                        ) {
                            LikeBurstIcon(isFavorite = isFavorite, iconSize = 20.dp)
                        }
                        EditorialChip(
                            checked = shuffleModeEnabled,
                            onClick = { viewModel.toggleShuffle() },
                            accent = accent,
                            field = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .captureAs("dial.shuffle", 42) {
                                    dialButton(LayoutAction.SHUFFLE, ButtonLook.OUTLINED)
                                }
                        ) {
                            Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(20.dp))
                        }
                        EditorialChip(
                            checked = repeatMode != Player.REPEAT_MODE_OFF,
                            onClick = { viewModel.toggleRepeat() },
                            accent = accent,
                            field = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .captureAs("dial.repeat", 43) {
                                    dialButton(LayoutAction.REPEAT, ButtonLook.OUTLINED)
                                }
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
                            accent = accent,
                            field = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .captureAs("dial.lyrics", 44) {
                                    dialButton(LayoutAction.LYRICS, ButtonLook.OUTLINED)
                                }
                        ) {
                            Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(20.dp))
                        }
                        EditorialCircleButton(
                            onClick = { playerHaptics.skip(); viewModel.skipToNext() },
                            accent = chipColor,
                            field = ink,
                            size = 56.dp,
                            modifier = Modifier.captureAs("dial.next", 45) {
                                dialButton(LayoutAction.NEXT, ButtonLook.FILLED)
                            }
                        ) {
                            Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(28.dp))
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

/**
 * The rotary dial: 60 flat ticks spinning past a fixed needle at twelve
 * o'clock (one revolution per track), played ticks solid, upcoming hollow.
 * Dragging anywhere on the ring rotates it 1:1 to scrub with a haptic
 * click per detent; a single seek fires on release. The center puck is
 * per-track die-cut art that morphs to a circle on pause.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RotaryDial(
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    /** Null where the puck takes no swipe: the layout editor, or a layout that turned it off. */
    swipeToSkip: SwipeToSkipState?,
    motionEligible: Boolean = true
) {
    val styleWheel = LocalPlayerStyleWheelController.current
    val tickColor = MaterialTheme.colorScheme.primary
    val tickTrackColor = MaterialTheme.colorScheme.outlineVariant
    val needleColor = MaterialTheme.colorScheme.onSurface

    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val liveProgress by rememberUpdatedState(progress)
    val liveDuration by rememberUpdatedState(duration)
    // Advanced from the clock each frame rather than held at the last sample -
    // the whole ring rotates with it, so a once-a-second value turned a spinning
    // dial into a ratchet. Read inside the Canvas draw scope, never here: see
    // rememberSmoothProgress.
    val smoothProgress = rememberSmoothProgress(
        positionMs = progress,
        durationMs = duration,
        isPlaying = isPlaying,
        scrubPositionMs = scrubFraction?.let { it * duration }
    )

    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val density = LocalDensity.current

    // Puck: per-track die-cut while playing, circle at rest.
    val dieCuts = remember {
        listOf(
            MaterialShapes.Sunny,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Cookie12Sided,
            MaterialShapes.SoftBurst
        )
    }
    val trackPolygon = remember(currentSong?.id) {
        dieCuts[abs(currentSong?.id?.hashCode() ?: 0) % dieCuts.size]
    }
    val puckMorph = remember(trackPolygon) { Morph(MaterialShapes.Circle, trackPolygon) }
    val puckProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "DialPuckMorph"
    )
    val puckShape = remember(puckMorph, puckProgress) {
        EditorialMorphShape(puckMorph, puckProgress)
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (maxWidth < 50.dp || maxHeight < 50.dp) return@BoxWithConstraints
        val dialSize = minOf(maxWidth, maxHeight) * 0.9f
        val puckSize = dialSize * 0.44f
        val tickWidthPx = with(density) { 4.dp.toPx() }
        val filledTickWidthPx = with(density) { 6.dp.toPx() }
        val needleWidthPx = with(density) { 6.dp.toPx() }

        // Tick ring with rotary scrub input.
        Canvas(
            modifier = Modifier
                .size(dialSize)
                .pointerInput(Unit) {
                    var previousAngle = 0f
                    var lastDetent = -1
                    detectDragGestures(
                        onDragStart = { position ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            previousAngle = Math.toDegrees(
                                atan2(
                                    (position.y - center.y).toDouble(),
                                    (position.x - center.x).toDouble()
                                )
                            ).toFloat()
                            scrubFraction = if (liveDuration > 0) {
                                (liveProgress.toFloat() / liveDuration.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            lastDetent = ((scrubFraction ?: 0f) * 60).toInt()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val angle = Math.toDegrees(
                                atan2(
                                    (change.position.y - center.y).toDouble(),
                                    (change.position.x - center.x).toDouble()
                                )
                            ).toFloat()
                            var delta = angle - previousAngle
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            previousAngle = angle
                            val next = ((scrubFraction ?: 0f) + delta / 360f).coerceIn(0f, 1f)
                            scrubFraction = next
                            val detent = (next * 60).toInt()
                            if (detent != lastDetent) {
                                lastDetent = detent
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDragEnd = {
                            scrubFraction?.let { onSeekTo((it * liveDuration).toLong()) }
                            scrubFraction = null
                        },
                        onDragCancel = { scrubFraction = null }
                    )
                }
        ) {
            // The frame-clock read lives here, in the draw scope, so sixty
            // updates a second redraw the ring without recomposing the dial.
            val displayedFraction = smoothProgress.value
            val center = this.center
            val outerRadius = size.minDimension / 2f - filledTickWidthPx
            val innerRadius = outerRadius * 0.88f
            val tickCount = 60
            // The ring spins one revolution per track past the fixed
            // needle; the filled arc always trails the twelve o'clock mark.
            val rotation = -displayedFraction * 360f
            val filledCount = (displayedFraction * tickCount).toInt()

            for (i in 0 until tickCount) {
                val filled = i < filledCount
                val angleRad = Math.toRadians(
                    (i * 360.0 / tickCount) - 90.0 + rotation
                )
                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                drawLine(
                    color = if (filled) tickColor else tickTrackColor,
                    start = Offset(center.x + cosA * innerRadius, center.y + sinA * innerRadius),
                    end = Offset(center.x + cosA * outerRadius, center.y + sinA * outerRadius),
                    strokeWidth = if (filled) filledTickWidthPx else tickWidthPx,
                    cap = StrokeCap.Round
                )
            }

            // Fixed needle at twelve o'clock.
            drawLine(
                color = needleColor,
                start = Offset(center.x, center.y - outerRadius - filledTickWidthPx),
                end = Offset(center.x, center.y - innerRadius + filledTickWidthPx),
                strokeWidth = needleWidthPx,
                cap = StrokeCap.Round
            )
        }

        // Center puck. The swipe lives here rather than on the ring, which
        // owns the rotary scrub; the puck sits on top of it, so a horizontal
        // drag started here is consumed before the ring's detector clears
        // touch slop and never turns into a seek.
        Box(
            modifier = Modifier
                .size(puckSize)
                .then(
                    if (swipeToSkip != null) {
                        Modifier.swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                    } else Modifier
                )
                .clip(puckShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .coverTaps(LocalCoverSeek.current) { onPlayPause() }
                .then(if (swipeToSkip != null) Modifier.swipeToSkip(swipeToSkip) else Modifier)
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
                    modifier = Modifier.size(puckSize * 0.35f)
                )
            }
            if (isBuffering) {
                LoadingIndicator(
                    modifier = Modifier.size(36.dp),
                    color = tickColor,
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

private fun dialButton(action: LayoutAction, look: ButtonLook) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.DIAL) { copy(action = action, look = look) }

/* ------------------------------------------------------------------ */
/* Pieces, shared with blocks in Dial's skin                           */
/* ------------------------------------------------------------------ */

/** The title and artist, small and centred under the readout. */
@Composable
internal fun DialTitle(
    song: Song?,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    artistEnabled: Boolean = true,
) {
    Column(modifier = modifier) {
        Text(
            text = song?.title?.takeIf { !isUnknownTitle(it) } ?: "Untitled",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/* ------------------------------------------------------------------ */
/* Dial's skin for custom layouts                                      */
/* ------------------------------------------------------------------ */

/**
 * A block drawn by Dial's own pieces. The cover is the whole instrument: the
 * tick ring that scrubs as it turns and the die-cut puck at its centre. Filled
 * and tonal are the round buttons (lit in the accent while on), outlined and
 * plain the chip; the time is the monospaced readout.
 */
@Composable
internal fun DialSkinBlock(
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
                Crossfade(targetState = showLyrics, label = "DialSkinArtLyrics") { lyrics ->
                    if (lyrics) {
                        CoverLyrics(
                            host = host,
                            primary = scheme.primary,
                            ink = scheme.onSurface,
                            inkVariant = scheme.onSurfaceVariant
                        )
                    } else {
                        RotaryDial(
                            currentSong = song,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering && playWhenReady && !isPlaying,
                            progress = progress,
                            duration = duration,
                            onSeekTo = { if (host.interactive) viewModel.seekTo(it) },
                            onPlayPause = {
                                if (host.interactive && block.action != LayoutAction.NONE) host.onAction(block.action)
                            },
                            swipeToSkip = swipe,
                            motionEligible = motionArtwork
                        )
                    }
                }
            }
        }
        LayoutBlockKind.TITLE -> SkinTitleFrame(block, host) { frame ->
            DialTitle(
                song = song,
                onArtistClick = host.onArtistClick,
                artistEnabled = host.interactive,
                modifier = frame
            )
        }
        LayoutBlockKind.BUTTON -> DialSkinButton(block, host)
        else -> fallback(LayoutInk(scheme.surfaceContainerLowest, scheme.onSurface))
    }
}

@Composable
private fun DialSkinButton(block: LayoutBlock, host: LayoutHost) {
    val action = block.action
    val state = rememberLayoutActionState(action, host)
    val onClick = { host.onAction(action) }
    val label = action.label()
    val scheme = MaterialTheme.colorScheme
    when (block.look) {
        ButtonLook.FILLED, ButtonLook.TONAL -> {
            val lit = state.checked == true
            EditorialCircleButton(
                onClick = onClick,
                accent = if (lit) scheme.primary else scheme.surfaceContainerHigh,
                field = if (lit) scheme.onPrimary else scheme.onSurface,
                modifier = Modifier.fillMaxSize()
            ) {
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val big = minOf(maxWidth, maxHeight) >= 52.dp
                    LayoutActionIcon(
                        action, state, label,
                        Modifier.layoutIcon(
                            when {
                                big -> 28.dp
                                action == LayoutAction.COLLAPSE -> 26.dp
                                else -> 22.dp
                            }
                        ),
                        ActionGlyphs.FILLED
                    )
                }
            }
        }
        else -> EditorialChip(
            checked = state.checked == true,
            onClick = onClick,
            accent = scheme.primary,
            field = scheme.onPrimary,
            modifier = Modifier.fillMaxSize()
        ) {
            LayoutActionIcon(action, state, label, Modifier.layoutIcon(20.dp), ActionGlyphs.FILLED)
        }
    }
}
