package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutColor
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlin.math.roundToInt

/** Share of the deck's width the play disc takes; the pills get the rest. */
private const val HERO_DISC_FRACTION = 0.48f

/** How far the dark-theme field is taken from primaryContainer toward surface. */
private const val HERO_DARK_FIELD_DEPTH = 0.5f

/** The deck stops growing here, so a wide phone gives the room to the art. */
private val HERO_DECK_MAX = 184.dp

/**
 * Hero's four colours, all from the player scheme: the field, the ink on it,
 * and the disc's fill and ink. Shared by Hero and by blocks in Hero's skin.
 */
@Immutable
internal class HeroColors(val field: Color, val ink: Color, val accent: Color, val onAccent: Color)

@Composable
internal fun rememberHeroColors(): HeroColors {
    // In a dark scheme primaryContainer is a saturated tone-30 fill, and as
    // the whole screen it read loud (feedback, September 2026). The field is
    // taken halfway down to the surface there: still the cover's colour, but a
    // ground the play disc stands up out of rather than one it competes with.
    // The ink roles only gain contrast from it. Light schemes keep the pastel.
    val scheme = MaterialTheme.colorScheme
    val field = if (scheme.surface.luminance() < 0.5f) {
        lerp(scheme.primaryContainer, scheme.surface, HERO_DARK_FIELD_DEPTH)
    } else {
        scheme.primaryContainer
    }
    return HeroColors(field, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
}

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
 * - The play disc is the state: squared shoulders while playing, a settled
 *   circle while paused, morphing on the house bouncy spring.
 * - Skip pills and toggles squish under the thumb; checked toggles square up
 *   and take the disc's colour.
 *
 * Its pieces are shared with blocks in Hero's skin ([HeroSkinBlock]) and
 * tagged with [captureAs], so a custom layout started from Hero is this
 * screen as laid out on the phone, drawn by this code.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HeroPlayerSheetContent(
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

    // Swipe-to-skip, shared by the art and the title on it so both commit at
    // the same threshold with the same spring home.
    val swipeToSkip = rememberSwipeToSkip(
        onNext = { playerHaptics.skip(); viewModel.skipToNext() },
        onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() }
    )

    val colors = rememberHeroColors()
    val field = colors.field
    val ink = colors.ink
    val accent = colors.accent
    val onAccent = colors.onAccent
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
                    // ========== HERO ==========
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .captureAs("hero.art", 0) {
                                    captureBlock(LayoutBlockKind.COVER, LayoutSkin.HERO) {
                                        copy(shape = BlockShape.SQUARE, action = LayoutAction.PLAY_PAUSE)
                                    }
                                }
                                .swipeToSkipFollow(swipeToSkip, SwipeToSkipDefaults.ArtFollow)
                                .coverTaps(LocalCoverSeek.current) { togglePlay() }
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
                                        onSurfaceVariantColor = ink.copy(alpha = 0.6f),
                                        backgroundColor = field
                                    )
                                } else {
                                    HeroArtwork(
                                        song = currentSong,
                                        colors = colors,
                                        modifier = Modifier.fillMaxSize()
                                    )
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
                                HeroTitle(
                                    song = currentSong,
                                    colors = colors,
                                    onArtistClick = onArtistClick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .captureAs("hero.title", 10) {
                                            captureBlock(LayoutBlockKind.TITLE, LayoutSkin.HERO)
                                        }
                                )
                                Spacer(Modifier.width(12.dp))
                                // Like, in the reference's round slot on the art.
                                HeroLikeButton(
                                    isFavorite = isFavorite,
                                    onClick = { viewModel.toggleCurrentSongLike() },
                                    colors = colors,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .captureAs("hero.like", 11) {
                                            captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.HERO) {
                                                copy(action = LayoutAction.LIKE, look = ButtonLook.OUTLINED)
                                            }
                                        }
                                )
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
                            HeroChromeButton(
                                onClick = onCollapse,
                                field = field,
                                ink = ink,
                                modifier = Modifier
                                    .size(44.dp)
                                    .captureAs("hero.collapse", 20) { heroChrome(LayoutAction.COLLAPSE) }
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            HeroChromeButton(
                                onClick = { showLyrics = !showLyrics },
                                field = field,
                                ink = ink,
                                checked = showLyrics,
                                accent = accent,
                                onAccent = onAccent,
                                modifier = Modifier
                                    .size(44.dp)
                                    .captureAs("hero.lyrics", 21) { heroChrome(LayoutAction.LYRICS) }
                            ) {
                                Icon(Icons.Rounded.Lyrics, "Lyrics", modifier = Modifier.size(22.dp))
                            }
                            HeroChromeButton(
                                onClick = sleepTimer.open,
                                field = field,
                                ink = ink,
                                checked = sleepTimer.active,
                                accent = accent,
                                onAccent = onAccent,
                                modifier = Modifier
                                    .size(44.dp)
                                    .captureAs("hero.sleep", 22) { heroChrome(LayoutAction.SLEEP_TIMER) }
                            ) {
                                Icon(Icons.Rounded.Bedtime, "Sleep timer", modifier = Modifier.size(22.dp))
                            }
                            HeroChromeButton(
                                onClick = { showOptions = true },
                                field = field,
                                ink = ink,
                                modifier = Modifier
                                    .size(44.dp)
                                    .captureAs("hero.options", 23) { heroChrome(LayoutAction.OPTIONS) }
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    stringResource(R.string.cd_more_options),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // ========== SCRUBBER ==========
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .captureAs("hero.scrubber", 30) {
                                captureBlock(LayoutBlockKind.SCRUBBER, LayoutSkin.HERO) {
                                    copy(color = LayoutColor.NEUTRAL, showDetail = true, motion = true)
                                }
                            }
                    ) {
                        var scrubPosition by remember { mutableStateOf<Float?>(null) }
                        val displayedProgress = scrubPosition?.toLong() ?: progress
                        val scrubReturnPoint = LocalScrubReturnPoint.current
                        // Advanced from the clock each frame rather than sprung
                        // toward a once-a-second sample - see
                        // rememberSmoothProgress.
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
                        Box(contentAlignment = Alignment.Center) {
                            ExpressiveScrubber(
                                progress = { smoothProgress.value },
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
                                // Through the return point: the drag leaves a marker where it
                                // started and snaps back onto it, and letting go on the marker
                                // is "never mind" rather than a seek. See ScrubReturnPoint.
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

                    PlayerVisualizerSlot(
                        modifier = Modifier
                            .fillMaxWidth()
                            .captureAs("hero.visualizer", 31) {
                                captureBlock(LayoutBlockKind.VISUALIZER, LayoutSkin.KODA)
                            }
                    )

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
                                modifier = Modifier
                                    .size(deck)
                                    .captureAs("hero.play", 40) {
                                        captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.HERO) {
                                            copy(action = LayoutAction.PLAY_PAUSE, look = ButtonLook.FILLED)
                                        }
                                    }
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
                                        ink = ink,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .captureAs("hero.previous", 41) { heroPill(LayoutAction.PREVIOUS) }
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(30.dp))
                                    }
                                    HeroSkipPill(
                                        onClick = { playerHaptics.skip(); viewModel.skipToNext() },
                                        ink = ink,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .captureAs("hero.next", 42) { heroPill(LayoutAction.NEXT) }
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
                                        colors = colors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .captureAs("hero.shuffle", 43) { heroToggle(LayoutAction.SHUFFLE) }
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
                                        colors = colors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .captureAs("hero.repeat", 44) { heroToggle(LayoutAction.REPEAT) }
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
                                        colors = colors,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .captureAs("hero.queue", 45) { heroToggle(LayoutAction.QUEUE) }
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
                onWatchAsVideo = onWatchAsVideo,
                onAlbumClick = onAlbumClick,
                onOpenAlbum = onOpenAlbum
            )
        }
    }
}

private fun heroChrome(action: LayoutAction) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.HERO) { copy(action = action, look = ButtonLook.PLAIN) }

private fun heroPill(action: LayoutAction) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.HERO) { copy(action = action, look = ButtonLook.OUTLINED) }

private fun heroToggle(action: LayoutAction) =
    captureBlock(LayoutBlockKind.BUTTON, LayoutSkin.HERO) { copy(action = action, look = ButtonLook.TONAL) }

/* ------------------------------------------------------------------ */
/* Pieces, shared with blocks in Hero's skin                           */
/* ------------------------------------------------------------------ */

/** The cover full bleed, with the fades the status bar and the deck sit on. */
@Composable
internal fun HeroArtwork(
    song: Song?,
    colors: HeroColors,
    modifier: Modifier = Modifier,
    motionEligible: Boolean = true,
) {
    Box(modifier = modifier) {
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
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.ink.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp)
            )
        }
        // Top fade: the status bar and the top bar's buttons need something
        // to sit on whatever the cover is.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(colors.field.copy(alpha = 0.5f), colors.field.copy(alpha = 0f))
                    )
                )
        )
        // Foot fade: the art hands over to the deck and carries the title.
        // Faded to the field at zero alpha, never to Transparent, which greys
        // the band.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to colors.field.copy(alpha = 0f),
                        0.55f to colors.field.copy(alpha = 0.6f),
                        1f to colors.field
                    )
                )
        )
    }
}

/** The title set large in Montserrat, with the artist under it. */
@Composable
internal fun HeroTitle(
    song: Song?,
    colors: HeroColors,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    artistEnabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        val title = song?.title?.takeIf { !isUnknownTitle(it) }
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
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 22.sp,
                    maxFontSize = 40.sp,
                    stepSize = 2.sp
                )
            )
        }
        val artistName = song?.artist?.takeIf { !isUnknownArtist(it) }
        Text(
            text = artistName ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = artistEnabled && artistName != null) {
                    artistName?.let(onArtistClick)
                }
        )
    }
}

/** Like, in a round slot ringed in the deck's ink. */
@Composable
internal fun HeroLikeButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    colors: HeroColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = colors.ink.copy(alpha = 0.06f),
        contentColor = colors.ink,
        border = BorderStroke(1.5.dp, colors.ink.copy(alpha = 0.55f)),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            LikeBurstIcon(isFavorite = isFavorite, iconSize = iconSize, modifier = iconModifier)
        }
    }
}

/**
 * The oversized play control. Its shape is the state: squared shoulders while
 * playing, a settled circle while paused, so it reads from across a room;
 * a press dips it on the bouncy spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HeroPlayDisc(
    isPlaying: Boolean,
    buffering: Boolean,
    onClick: () -> Unit,
    color: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier.size(64.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateFloatAsState(
        targetValue = if (isPlaying) 30f else 50f,
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
        enabled = enabled,
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
                    modifier = iconModifier,
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
                    modifier = iconModifier
                )
            }
        }
    }
}

/** A tall skip pill in the deck's ink, squishing under the thumb. */
@Composable
internal fun HeroSkipPill(
    onClick: () -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(percent = 50),
        color = ink.copy(alpha = 0.08f),
        contentColor = ink,
        border = BorderStroke(1.5.dp, ink.copy(alpha = 0.4f)),
        modifier = modifier.graphicsLayer {
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
internal fun HeroToggle(
    checked: Boolean,
    onClick: () -> Unit,
    colors: HeroColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = if (checked) colors.accent else colors.ink.copy(alpha = 0.08f),
        contentColor = if (checked) colors.onAccent else colors.ink,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * A round button over the art, on its own plate of the deck's field so it
 * reads over any cover. Lit in the disc's colour while its thing is on.
 */
@Composable
internal fun HeroChromeButton(
    onClick: () -> Unit,
    field: Color,
    ink: Color,
    checked: Boolean = false,
    accent: Color = ink,
    onAccent: Color = field,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (checked) accent else field.copy(alpha = 0.78f),
        contentColor = if (checked) onAccent else ink,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/* ------------------------------------------------------------------ */
/* Hero's skin for custom layouts                                      */
/* ------------------------------------------------------------------ */

/** An icon at Hero's size, shrinking with a block made smaller than Hero drew it. */
private fun Modifier.heroIcon(size: Dp): Modifier = layoutIcon(size)

/**
 * A block drawn by Hero's own pieces. The four button looks are Hero's four
 * buttons: filled is the disc (or a lit toggle), outlined the skip pill (the
 * ringed slot for Like), tonal the deck toggle and plain the round chrome
 * button over the art. Kinds Hero has no piece of its own for fall back to
 * Koda's blocks in Hero's colours.
 */
@Composable
internal fun HeroSkinBlock(
    block: LayoutBlock,
    host: LayoutHost,
    motionArtwork: Boolean,
    fallback: @Composable (LayoutInk) -> Unit,
) {
    val colors = rememberHeroColors()
    val song by host.viewModel.currentSong.collectAsState()
    when (block.kind) {
        LayoutBlockKind.COVER -> {
            // Hero swaps the art for the lyrics in place, clear of the top bar
            // above and the title on the art's foot.
            val showLyrics = host.lyricsOpen && motionArtwork
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layoutCoverGestures(block, host, enabled = !showLyrics)
                    .clip(block.shape.composeShape())
            ) {
                Crossfade(targetState = showLyrics, label = "HeroSkinArtLyrics") { lyrics ->
                    if (lyrics) {
                        CoverLyrics(
                            host = host,
                            primary = colors.accent,
                            ink = colors.ink,
                            inkVariant = colors.ink.copy(alpha = 0.6f),
                            background = colors.field,
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(top = 60.dp, bottom = 132.dp)
                        )
                    } else {
                        HeroArtwork(
                            song = song,
                            colors = colors,
                            motionEligible = motionArtwork,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        // On the art in Hero, so it moves with the art when the cover is swiped.
        LayoutBlockKind.TITLE -> SkinTitleFrame(block, host, SwipeToSkipDefaults.ArtFollow) { frame ->
            HeroTitle(
                song = song,
                colors = colors,
                onArtistClick = host.onArtistClick,
                artistEnabled = host.interactive,
                modifier = frame
            )
        }
        LayoutBlockKind.BUTTON -> HeroSkinButton(block, host, colors)
        else -> fallback(LayoutInk(colors.field, colors.ink))
    }
}

@Composable
private fun HeroSkinButton(block: LayoutBlock, host: LayoutHost, colors: HeroColors) {
    val action = block.action
    val state = rememberLayoutActionState(action, host)
    val onClick = rememberBlockAction(block, host)
    val enabled = host.interactive
    val fill = Modifier.fillMaxSize()
    val label = action.label()
    when {
        block.look == ButtonLook.FILLED && action == LayoutAction.PLAY_PAUSE -> HeroPlayDisc(
            isPlaying = state.isPlaying,
            buffering = state.buffering,
            onClick = onClick,
            color = colors.accent,
            contentColor = colors.onAccent,
            enabled = enabled,
            modifier = fill,
            iconModifier = Modifier.heroIcon(64.dp)
        )
        block.look == ButtonLook.OUTLINED && action == LayoutAction.LIKE -> HeroLikeButton(
            isFavorite = state.checked == true,
            onClick = onClick,
            colors = colors,
            enabled = enabled,
            modifier = fill
        )
        block.look == ButtonLook.OUTLINED -> HeroSkipPill(
            onClick = onClick,
            ink = colors.ink,
            enabled = enabled,
            modifier = fill
        ) {
            LayoutActionIcon(action, state, label, Modifier.heroIcon(30.dp), ActionGlyphs.FILLED)
        }
        block.look == ButtonLook.PLAIN -> HeroChromeButton(
            onClick = onClick,
            field = colors.field,
            ink = colors.ink,
            checked = state.checked == true,
            accent = colors.accent,
            onAccent = colors.onAccent,
            enabled = enabled,
            modifier = fill
        ) {
            LayoutActionIcon(
                action, state, label,
                Modifier.heroIcon(if (action == LayoutAction.COLLAPSE) 26.dp else 22.dp),
                ActionGlyphs.FILLED
            )
        }
        else -> HeroToggle(
            // Filled is a toggle that is always lit, for an action with no state.
            checked = state.checked ?: (block.look == ButtonLook.FILLED),
            onClick = onClick,
            colors = colors,
            enabled = enabled,
            modifier = fill
        ) {
            LayoutActionIcon(action, state, label, Modifier.heroIcon(22.dp), ActionGlyphs.FILLED)
        }
    }
}
