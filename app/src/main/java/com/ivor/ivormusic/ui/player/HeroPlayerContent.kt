package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlin.math.roundToInt

/** Share of the deck's width the play disc takes; the pills get the rest. */
private const val HERO_DISC_FRACTION = 0.48f

/** The deck stops growing here, so a wide phone gives the room to the art. */
private val HERO_DECK_MAX = 184.dp

/**
 * Hero Player - full-bleed artwork over a tonal control deck.
 *
 * The cover takes the top of the screen edge to edge, under the status bar,
 * with the title set large on its foot; below it sits a deck in the
 * cover's own colour with an oversized play disc beside two tall skip pills.
 *
 * Signature moves:
 * - The deck is the artwork's colour. The player's scheme is already derived
 *   from the cover, so `primaryContainer` is the field and `primary` the disc:
 *   no colour is picked here, and none is hardcoded.
 * - The art fades into the deck at its foot, and the title sits on that fade
 *   in the deck's ink, so it reads on any cover in both themes.
 * - The play disc is the state: a settled circle while playing, squared
 *   shoulders while paused, morphing on the house bouncy spring.
 * - Skip pills and toggles squish under the thumb; checked toggles square up
 *   and take the disc's colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroPlayerSheetContent(
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onOpenAlbum: (PlaylistDisplayItem) -> Unit = {}
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

    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    // Swipe-to-skip, shared by the art and the title on it so both commit at
    // the same threshold with the same spring home.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )

    val field = MaterialTheme.colorScheme.primaryContainer
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val sleepTimer = rememberSleepTimerControl(viewModel = viewModel)

    val togglePlay: () -> Unit = {
        playerHaptics.playPause(!viewModel.isPlaying.value)
        viewModel.togglePlayPause()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(field)
    ) {
        Crossfade(targetState = showQueue, label = "HeroQueueTransition") { queueVisible ->
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
                    field = field,
                    accent = ink
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ========== HERO ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { togglePlay() })
                                }
                                // Off while lyrics are up: that view scrolls and seeks.
                                .swipeToSkip(swipeToSkip, enabled = !showLyrics)
                                .styleWheelHold(styleWheel)
                        ) {
                            Crossfade(targetState = showLyrics, label = "HeroArtLyrics") { lyricsVisible ->
                                if (lyricsVisible) {
                                    SyncedLyricsView(
                                        lyricsResult = lyricsResult,
                                        currentPositionMs = progress,
                                        isPlaying = isPlaying,
                                        onSeekTo = { viewModel.seekTo(it) },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                            // Clear of the top bar above and
                                            // the title below.
                                            .padding(top = 60.dp, bottom = 132.dp),
                                        primaryColor = accent,
                                        onSurfaceColor = ink,
                                        onSurfaceVariantColor = ink.copy(alpha = 0.6f)
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        val artSong = currentSong
                                            ?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
                                        if (artSong != null) {
                                            PlayerArtwork(
                                                song = artSong,
                                                contentDescription = "Album Art",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = ink.copy(alpha = 0.4f),
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(120.dp)
                                            )
                                        }
                                        // Top fade: the status bar and the top
                                        // bar's buttons need something to sit
                                        // on whatever the cover is.
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(132.dp)
                                                .align(Alignment.TopCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(field.copy(alpha = 0.5f), field.copy(alpha = 0f))
                                                    )
                                                )
                                        )
                                        // Foot fade: the art hands over to the
                                        // deck and carries the title. Faded to
                                        // the field at zero alpha, never to
                                        // Transparent, which greys the band.
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(0.55f)
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        0f to field.copy(alpha = 0f),
                                                        0.55f to field.copy(alpha = 0.6f),
                                                        1f to field
                                                    )
                                                )
                                        )
                                    }
                                }
                            }

                            // ========== TITLE ON THE ART ==========
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val title = currentSong?.title?.takeIf { !isUnknownTitle(it) }
                                        ?: stringResource(R.string.untitled_song)
                                    Crossfade(targetState = title, label = "HeroTitle") { shown ->
                                        Text(
                                            text = shown,
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontFamily = MontserratFamily,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = (-0.02).em,
                                                lineHeight = 1.0.em
                                            ),
                                            color = ink,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            autoSize = TextAutoSize.StepBased(
                                                minFontSize = 22.sp,
                                                maxFontSize = 40.sp,
                                                stepSize = 2.sp
                                            )
                                        )
                                    }
                                    val artistName = currentSong?.artist?.takeIf { !isUnknownArtist(it) }
                                    Text(
                                        text = artistName ?: stringResource(R.string.unknown_artist),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ink.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(enabled = artistName != null) {
                                                artistName?.let(onArtistClick)
                                            }
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                // Like, in the reference's round slot on the art.
                                Surface(
                                    onClick = { viewModel.toggleCurrentSongLike() },
                                    shape = CircleShape,
                                    color = ink.copy(alpha = 0.06f),
                                    contentColor = ink,
                                    border = BorderStroke(1.5.dp, ink.copy(alpha = 0.55f)),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        LikeBurstIcon(isFavorite = isFavorite, iconSize = 22.dp)
                                    }
                                }
                            }
                        }

                        // ========== TOP BAR ==========
                        // Outside the swipe, so it holds still while the art
                        // follows the finger. The overflow is last in the row.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HeroChromeButton(onClick = onCollapse, field = field, ink = ink) {
                                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            HeroChromeButton(
                                onClick = { showLyrics = !showLyrics },
                                field = field,
                                ink = ink,
                                checked = showLyrics,
                                accent = accent,
                                onAccent = onAccent
                            ) {
                                Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(22.dp))
                            }
                            HeroChromeButton(
                                onClick = sleepTimer.open,
                                field = field,
                                ink = ink,
                                checked = sleepTimer.active,
                                accent = accent,
                                onAccent = onAccent
                            ) {
                                Icon(Icons.Rounded.Bedtime, "Sleep timer", modifier = Modifier.size(22.dp))
                            }
                            HeroChromeButton(onClick = { showOptions = true }, field = field, ink = ink) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    stringResource(R.string.cd_more_options),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // ========== SCRUBBER ==========
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
                            label = "HeroProgress"
                        )
                        val lineStroke = Stroke(
                            width = with(LocalDensity.current) { 4.dp.toPx() },
                            cap = StrokeCap.Round
                        )
                        Box(contentAlignment = Alignment.Center) {
                            ExpressiveScrubber(
                                progress = { animatedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(scrubberTrackHeight(14.dp)),
                                color = ink,
                                trackColor = ink.copy(alpha = 0.22f),
                                stroke = lineStroke,
                                trackStroke = lineStroke,
                                amplitude = { if (isPlaying) 1f else 0f }
                            )
                            Slider(
                                interactionSource = LocalPlayerScrubInteraction.current,
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
                                color = ink.copy(alpha = 0.75f)
                            )
                            Text(
                                text = formatEditorialTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = ink.copy(alpha = 0.75f)
                            )
                        }
                    }

                    PlayerVisualizerSlot(modifier = Modifier.fillMaxWidth())

                    // ========== DECK ==========
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 10.dp, bottom = 16.dp)
                    ) {
                        val deck = min(HERO_DECK_MAX, (maxWidth - 12.dp) * HERO_DISC_FRACTION)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(deck),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HeroPlayDisc(
                                isPlaying = isPlaying,
                                buffering = isBuffering && playWhenReady && !isPlaying,
                                onClick = togglePlay,
                                color = accent,
                                contentColor = onAccent,
                                modifier = Modifier.size(deck)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HeroSkipPill(
                                        onClick = { playerHaptics.skip(); viewModel.skipToPrevious() },
                                        ink = ink
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(30.dp))
                                    }
                                    HeroSkipPill(
                                        onClick = { playerHaptics.skip(); viewModel.skipToNext() },
                                        ink = ink
                                    ) {
                                        Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(30.dp))
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    HeroToggle(
                                        checked = shuffleModeEnabled,
                                        onClick = { viewModel.toggleShuffle() },
                                        ink = ink,
                                        accent = accent,
                                        onAccent = onAccent
                                    ) {
                                        Icon(
                                            Icons.Default.Shuffle,
                                            stringResource(R.string.cd_shuffle),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    HeroToggle(
                                        checked = repeatMode != Player.REPEAT_MODE_OFF,
                                        onClick = { viewModel.toggleRepeat() },
                                        ink = ink,
                                        accent = accent,
                                        onAccent = onAccent
                                    ) {
                                        Icon(
                                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne
                                            else Icons.Default.Repeat,
                                            "Repeat",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    HeroToggle(
                                        checked = false,
                                        onClick = { showQueue = true },
                                        ink = ink,
                                        accent = accent,
                                        onAccent = onAccent
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.QueueMusic,
                                            "Queue",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
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
                onAlbumClick = onAlbumClick,
                onOpenAlbum = onOpenAlbum
            )
        }
    }
}

/**
 * The oversized play control. Its shape is the state: a settled circle while
 * playing, squared shoulders while paused, so it reads from across a room;
 * a press dips it on the bouncy spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroPlayDisc(
    isPlaying: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateFloatAsState(
        targetValue = if (isPlaying) 50f else 30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "HeroDiscCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "HeroDiscPress"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        // The bouncy spring overshoots past a circle; a corner past half
        // the side is no shape at all, so it is held there.
        shape = RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50)),
        color = color,
        contentColor = contentColor,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (buffering) {
                LoadingIndicator(
                    modifier = Modifier.size(64.dp),
                    color = contentColor,
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
                    contentDescription = if (isPlaying) "Pause" else stringResource(R.string.cd_play),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

/** A tall skip pill in the deck's ink, squishing under the thumb. */
@Composable
private fun RowScope.HeroSkipPill(
    onClick: () -> Unit,
    ink: Color,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "HeroSkipSquish"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(percent = 50),
        color = ink.copy(alpha = 0.08f),
        contentColor = ink,
        border = BorderStroke(1.5.dp, ink.copy(alpha = 0.4f)),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = squish
                scaleY = squish
            }
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * A small deck toggle. Checked squares up and takes the disc's colour, so
 * its state is carried by shape and tone together rather than tone alone.
 */
@Composable
private fun RowScope.HeroToggle(
    checked: Boolean,
    onClick: () -> Unit,
    ink: Color,
    accent: Color,
    onAccent: Color,
    content: @Composable () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (checked) 16.dp else 26.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "HeroToggleCorner"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = if (checked) accent else ink.copy(alpha = 0.08f),
        contentColor = if (checked) onAccent else ink,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * A round button over the art, on its own plate of the deck's field so it
 * reads over any cover. Lit in the disc's colour while its thing is on.
 */
@Composable
private fun HeroChromeButton(
    onClick: () -> Unit,
    field: Color,
    ink: Color,
    checked: Boolean = false,
    accent: Color = ink,
    onAccent: Color = field,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (checked) accent else field.copy(alpha = 0.78f),
        contentColor = if (checked) onAccent else ink,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
