package com.ivor.ivormusic.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.data.AudioProfile
import com.ivor.ivormusic.data.AudioProfileStore
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.TILE_LANES
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.Tile
import com.ivor.ivormusic.data.TileChart
import com.ivor.ivormusic.data.TileChartFactory
import com.ivor.ivormusic.data.TileDifficulty
import com.ivor.ivormusic.data.TileKind
import com.ivor.ivormusic.data.TileRunResult
import com.ivor.ivormusic.data.TileScoreStore
import com.ivor.ivormusic.ui.components.SongArtwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Tiles - the rhythm board.
 *
 * Four lanes, tiles falling on the track's own beat, tapped as they cross the
 * line. It exists because the app already measures every track it plays for
 * AutoMix - tempo, the first downbeat, the key, where the audio starts and how
 * it ends - and that measurement is exactly a beat map nobody was reading. See
 * [TileChartFactory]: no new analysis, no request, no decode.
 *
 * **Two rules hold the whole design together.**
 *
 * The first: *playback is never a consequence.* Missing a tile costs score and
 * breaks the streak and does nothing else. Magic Tiles stops the music on a
 * miss, which is right for a game and wrong for the player you keep your music
 * in - nobody wants their album to stop because they fumbled while walking. So
 * the transport, the queue, seeking and the artist link all still work, and the
 * board is a layer over playback rather than a thing that owns it.
 *
 * The second: *never fake the beat.* A chart that does not match what is
 * playing is worse than no chart, because it reads as the app being broken
 * rather than as a track it has not measured yet. A track with no confident
 * tempo says so and offers Freestyle - an assumed 4/4 grid the user turns on
 * knowingly - rather than quietly guessing.
 *
 * The dress is the Editorial one, two flat tones and a serif: the board is set
 * like a page, the accent tiles carry their bar numbers the way a score carries
 * measure numbers, and the analysis prints in the credit line under the title.
 * The one color outside the two-tone contract is `error`, used only for a miss.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TilesPlayerSheetContent(
    viewModel: PlayerViewModel,
    @Suppress("UNUSED_PARAMETER") ambientBackground: Boolean = true,
    onCollapse: () -> Unit,
    onLoadMore: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    // ambientBackground is ignored on purpose, as it is in Editorial: this is a
    // flat two-tone surface and a drifting shader behind a board you are aiming
    // at is noise the player has to look past.

    val context = LocalContext.current
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentQueue by viewModel.currentQueue.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val field = MaterialTheme.colorScheme.primaryContainer
    val accent = MaterialTheme.colorScheme.onPrimaryContainer

    var showQueue by remember { mutableStateOf(false) }
    var showTuning by remember { mutableStateOf(false) }

    // Both are this player's own, set here and read here, so they follow the
    // download sheet's pattern rather than the Settings screen's five files.
    var difficulty by remember {
        mutableStateOf(TileDifficulty.from(ThemePreferences.currentTilesDifficulty(context)))
    }
    var syncOffsetMs by remember {
        mutableIntStateOf(ThemePreferences.currentTilesSyncOffsetMs(context))
    }

    val profileStore = remember(context) { AudioProfileStore(context) }
    val scoreStore = remember(context) { TileScoreStore(context) }

    val songId = currentSong?.id
    // Which track the user has accepted a guessed grid for. Cleared by the song
    // changing, because consenting to freestyle on one track is not consenting
    // to it forever.
    var freestyleFor by remember { mutableStateOf<String?>(null) }
    var chartState by remember { mutableStateOf<TilesChartState>(TilesChartState.Loading) }

    LaunchedEffect(songId, duration, difficulty, freestyleFor) {
        val id = songId
        if (id == null || duration <= 0L) {
            chartState = TilesChartState.Loading
            return@LaunchedEffect
        }
        val wantsFreestyle = freestyleFor == id
        var profile = profileStore.get(id)

        // The service profiles a track shortly after it starts playing, and
        // this store is a different instance that loaded before that write
        // landed - hence the reload rather than a second look at the same
        // in-memory map. Bounded: a track that is never going to be profiled
        // (metered network, uncached, unreadable audio) must reach an answer
        // rather than spin.
        var attempt = 0
        while (!wantsFreestyle && !profile.hasTempo() && attempt < ANALYSIS_ATTEMPTS) {
            chartState = TilesChartState.Analysing
            delay(ANALYSIS_RETRY_MS)
            profileStore.reload()
            profile = profileStore.get(id)
            attempt++
        }

        val chart = TileChartFactory.build(
            songId = id,
            durationMs = duration,
            profile = profile,
            difficulty = difficulty,
            freestyle = wantsFreestyle
        )
        chartState = if (chart == null) TilesChartState.NoTempo else TilesChartState.Ready(chart)
    }

    LaunchedEffect(songId) { freestyleFor = null }

    val chart = (chartState as? TilesChartState.Ready)?.chart
    val game = remember(chart) { chart?.let(TilesRunCache::forChart) }

    var best by remember { mutableStateOf<TileRunResult?>(null) }
    var resultCard by remember { mutableStateOf<TilesResultCard?>(null) }
    var previousGame by remember { mutableStateOf<TilesGameState?>(null) }

    suspend fun submitRun(run: TilesGameState) {
        if (run.submitted) return
        val result = run.result()
        if (result.judged <= 0) return
        run.submitted = true
        resultCard = TilesResultCard(result, scoreStore.submit(result) != null)
    }

    // A run is banked when the track changes and again when the chart simply
    // runs out, because those are two different endings: the second happens
    // while the player may be collapsed, and a score you earned by playing a
    // song to its end should not depend on the screen still being on it.
    LaunchedEffect(game) {
        previousGame?.takeIf { it !== game }?.let { submitRun(it) }
        previousGame = game
        best = game?.let { scoreStore.best(it.chart.songId, it.chart.difficulty) }
    }

    LaunchedEffect(resultCard) {
        if (resultCard != null) {
            delay(RESULT_CARD_MS)
            resultCard = null
        }
    }

    // Held as state and read only inside the board's draw lambda. Passing the
    // clock down as a plain value would recompose the whole board - constraints,
    // Canvas, overlays - sixty times a second; a snapshot read taken in the draw
    // phase invalidates the drawing alone, which is all that actually changes.
    val songTime = remember { mutableLongStateOf(0L) }

    // The board's clock. `progress` ticks once a second, which is a seek bar's
    // resolution, not a beat's; MediaController extrapolates locally, so a
    // per-frame read is both cheap and smooth.
    LaunchedEffect(game, isPlaying, syncOffsetMs) {
        val run = game ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        val lastTileEnd = run.chart.tiles.lastOrNull()?.endMs ?: 0L
        while (isActive) {
            withFrameNanos { }
            val now = viewModel.playbackPositionMs() - syncOffsetMs
            songTime.longValue = now
            run.advance(now, scoringEnabled = true)
            if (!run.submitted && now > lastTileEnd + RUN_END_GRACE_MS) submitRun(run)
        }
    }

    // Paused, the frame loop is not running, so the board follows `progress`
    // instead - which is what moves when someone scrubs a paused track.
    LaunchedEffect(game, isPlaying, progress, syncOffsetMs) {
        if (isPlaying) return@LaunchedEffect
        val run = game ?: return@LaunchedEffect
        run.abandonHolds()
        val now = progress - syncOffsetMs
        songTime.longValue = now
        run.advance(now, scoringEnabled = false)
    }

    // Putting the player away mid-tile is not a dropped hold.
    DisposableEffect(game) {
        onDispose { game?.abandonHolds() }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(field)) {
        Crossfade(targetState = showQueue, label = "TilesQueueTransition") { queueVisible ->
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
                TilesNowPlaying(
                    viewModel = viewModel,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    playWhenReady = playWhenReady,
                    position = progress,
                    duration = duration,
                    songTime = songTime,
                    chartState = chartState,
                    game = game,
                    best = best,
                    resultCard = resultCard,
                    difficulty = difficulty,
                    syncOffsetMs = syncOffsetMs,
                    onDismissResult = { resultCard = null },
                    onStartFreestyle = { freestyleFor = songId },
                    onCollapse = onCollapse,
                    onShowQueue = { showQueue = true },
                    onShowTuning = { showTuning = true },
                    onArtistClick = onArtistClick,
                    field = field,
                    accent = accent
                )
            }
        }
    }

    if (showTuning) {
        TilesTuningSheet(
            difficulty = difficulty,
            syncOffsetMs = syncOffsetMs,
            onDifficultyChange = {
                difficulty = it
                ThemePreferences.setTilesDifficulty(context, it.key)
            },
            onSyncOffsetChange = {
                syncOffsetMs = it
                ThemePreferences.setTilesSyncOffsetMs(context, it)
            },
            onDismissRequest = { showTuning = false },
            field = field,
            accent = accent
        )
    }
}

private fun AudioProfile?.hasTempo(): Boolean = this?.bpm != null

/**
 * The run in progress, kept across the player being collapsed.
 *
 * The expanded player is removed from composition when it collapses, so
 * everything held in `remember` goes with it. A score that resets because
 * somebody dropped to the mini player to check the queue is a score nobody can
 * be bothered to chase, so one run survives - keyed by the chart it belongs to,
 * which is what makes changing track or difficulty start a genuinely new one.
 */
internal object TilesRunCache {
    private var key: String? = null
    private var run: TilesGameState? = null

    fun forChart(chart: TileChart): TilesGameState {
        val signature = "${chart.songId}|${chart.difficulty.key}|" +
            "${chart.isFreestyle}|${chart.tiles.size}"
        val existing = run
        if (signature == key && existing != null) return existing
        val fresh = TilesGameState(chart)
        key = signature
        run = fresh
        return fresh
    }
}

private sealed interface TilesChartState {
    /** No track, or its duration is not known yet. */
    data object Loading : TilesChartState

    /** Waiting on the profile the service writes shortly after playback starts. */
    data object Analysing : TilesChartState

    /** Measured, and there is no tempo in it worth charting. */
    data object NoTempo : TilesChartState

    data class Ready(val chart: TileChart) : TilesChartState
}

private data class TilesResultCard(val result: TileRunResult, val isBest: Boolean)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TilesNowPlaying(
    viewModel: PlayerViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    position: Long,
    duration: Long,
    songTime: LongState,
    chartState: TilesChartState,
    game: TilesGameState?,
    best: TileRunResult?,
    resultCard: TilesResultCard?,
    difficulty: TileDifficulty,
    syncOffsetMs: Int,
    onDismissResult: () -> Unit,
    onStartFreestyle: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowTuning: () -> Unit,
    onArtistClick: (String) -> Unit,
    field: Color,
    accent: Color
) {
    val haptics = LocalHapticFeedback.current
    val playerHaptics = rememberPlayerHaptics()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Landscape, and small phones, have no room for the page furniture. The
    // board is the point of the style, so the credit line goes first and the
    // masthead loses its artwork rather than the board losing its height.
    val compact = maxHeight < COMPACT_HEIGHT

    Column(modifier = Modifier.fillMaxSize()) {

        // ========== TOP BAR ==========
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorialCircleButton(onClick = onCollapse, accent = accent, field = field, size = 42.dp) {
                Icon(Icons.Default.KeyboardArrowDown, "Collapse", modifier = Modifier.size(24.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorialCircleButton(
                    onClick = onShowTuning,
                    accent = accent,
                    field = field,
                    size = 42.dp
                ) {
                    Icon(Icons.Rounded.Tune, "Tiles tuning", modifier = Modifier.size(20.dp))
                }
                EditorialCircleButton(
                    onClick = onShowQueue,
                    accent = accent,
                    field = field,
                    size = 42.dp
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        "Queue",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ========== MASTHEAD ==========
        TilesMasthead(
            currentSong = currentSong,
            game = game,
            best = best,
            showArtwork = !compact,
            onNext = { playerHaptics.skip(); viewModel.skipToNext() },
            onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() },
            onArtistClick = onArtistClick,
            field = field,
            accent = accent
        )

        // ========== CREDIT LINE ==========
        // The measurement is why this player can exist, so it is printed rather
        // than hidden: tempo, key, and the chart being played against.
        if (!compact) {
            TilesCreditLine(
                chartState = chartState,
                difficulty = difficulty,
                syncOffsetMs = syncOffsetMs,
                onShowTuning = onShowTuning,
                accent = accent
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ========== BOARD ==========
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Crossfade(
                targetState = chartState is TilesChartState.Ready,
                label = "TilesBoardState"
            ) { ready ->
                if (ready && game != null) {
                    TilesBoard(
                        game = game,
                        songTime = songTime,
                        isPlaying = isPlaying,
                        nowMs = { viewModel.playbackPositionMs() - syncOffsetMs },
                        onHit = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        },
                        onResume = {
                            playerHaptics.playPause(true)
                            viewModel.togglePlayPause()
                        },
                        field = field,
                        accent = accent
                    )
                } else {
                    TilesBoardPlaceholder(
                        chartState = chartState,
                        onStartFreestyle = onStartFreestyle,
                        accent = accent,
                        field = field
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = resultCard != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                resultCard?.let {
                    TilesResultStrip(
                        card = it,
                        onDismiss = onDismissResult,
                        field = field,
                        accent = accent
                    )
                }
            }
        }

        // ========== TRANSPORT ==========
        TilesTransport(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            playWhenReady = playWhenReady,
            position = position,
            duration = duration,
            onPlayPause = {
                playerHaptics.playPause(!isPlaying)
                viewModel.togglePlayPause()
            },
            onNext = { playerHaptics.skip(); viewModel.skipToNext() },
            onPrevious = { playerHaptics.skip(); viewModel.skipToPrevious() },
            onSeekTo = { viewModel.seekTo(it) },
            field = field,
            accent = accent
        )
    }
    }
}

/**
 * Title, artist and the running score on one line.
 *
 * The score sits beside the headline rather than over the board, because
 * anything drawn on the board is something to look past while aiming.
 */
@Composable
private fun TilesMasthead(
    currentSong: Song?,
    game: TilesGameState?,
    best: TileRunResult?,
    showArtwork: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onArtistClick: (String) -> Unit,
    field: Color,
    accent: Color
) {
    val swipeToSkip = rememberSwipeToSkip(onNext = onNext, onPrevious = onPrevious)
    val styleWheel = LocalPlayerStyleWheelController.current
    val title = currentSong?.title?.takeIf { !it.startsWith("Unknown") } ?: "Nothing playing"
    val artist = currentSong?.artist?.takeIf { !it.startsWith("Unknown") } ?: "Unknown Artist"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            // The swipe lives here and never on the board: a horizontal drag
            // during a hold must not skip the track out from under the run.
            .swipeToSkip(swipeToSkip),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // The board owns tap and long press, so the artwork is what hosts the
        // style wheel's hold gesture in this style.
        currentSong?.takeIf { showArtwork }?.let { song ->
            SongArtwork(
                song = song,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .styleWheelHold(styleWheel)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .swipeToSkipFollow(swipeToSkip)
                // With the artwork gone there would be nothing left in this
                // style hosting the style wheel, and the board cannot host it -
                // a long press there is a held tile.
                .then(if (showArtwork) Modifier else Modifier.styleWheelHold(styleWheel))
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
                color = accent.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = artist != "Unknown Artist") { onArtistClick(artist) }
                    .padding(vertical = 2.dp)
            )
        }

        TilesScoreBlock(game = game, best = best, field = field, accent = accent)
    }
}

@Composable
private fun TilesScoreBlock(
    game: TilesGameState?,
    best: TileRunResult?,
    field: Color,
    accent: Color
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "SCORE",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontSize = 9.sp
            ),
            color = accent.copy(alpha = 0.6f)
        )
        AnimatedContent(
            targetState = game?.score ?: 0,
            label = "TilesScore"
        ) { score ->
            Text(
                text = formatScore(score),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
        }
        val combo = game?.combo ?: 0
        val multiplier = game?.multiplier ?: 1
        // A streak worth naming only once it is one; a permanent "x1" is noise.
        AnimatedVisibility(visible = combo >= COMBO_VISIBLE_AT) {
            Surface(
                shape = RoundedCornerShape(50),
                color = accent,
                contentColor = field
            ) {
                Text(
                    text = "$combo  ×$multiplier",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (combo < COMBO_VISIBLE_AT && best != null && best.score > 0) {
            Text(
                text = "BEST ${formatScore(best.score)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontSize = 9.sp
                ),
                color = accent.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
    }
}

/** "128 BPM · F♯ MINOR · STANDARD", set as a magazine credit line. */
@Composable
private fun TilesCreditLine(
    chartState: TilesChartState,
    difficulty: TileDifficulty,
    syncOffsetMs: Int,
    onShowTuning: () -> Unit,
    accent: Color
) {
    val chart = (chartState as? TilesChartState.Ready)?.chart
    val parts = buildList {
        when {
            chart == null -> add("NO CHART")
            chart.isFreestyle -> add("FREESTYLE 120 BPM")
            else -> {
                chart.bpm?.let { add("${it.toInt()} BPM") }
                chart.keyLabel?.let { add(it) }
            }
        }
        add(difficulty.label.uppercase())
        if (syncOffsetMs != 0) add("${if (syncOffsetMs > 0) "+" else ""}$syncOffsetMs MS")
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(accent.copy(alpha = 0.22f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.6.sp,
                fontSize = 10.sp
            ),
            color = accent.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onShowTuning)
                .padding(vertical = 2.dp)
        )
    }
}

/**
 * The board itself: one Canvas for every tile, and one pointer loop for every
 * finger.
 *
 * Drawn rather than composed. A tile per composable would mean a few dozen
 * nodes entering and leaving every second at exactly the moment the screen has
 * the least frame budget to spare, and the whole board is flat rectangles on
 * one clock - which is what a Canvas is for. The clock is the only state the
 * draw reads, so it repaints once per frame while playing and not at all while
 * paused.
 */
@Composable
private fun TilesBoard(
    game: TilesGameState,
    songTime: LongState,
    isPlaying: Boolean,
    nowMs: () -> Long,
    onHit: () -> Unit,
    onResume: () -> Unit,
    field: Color,
    accent: Color
) {
    val density = LocalDensity.current
    val chart = game.chart
    // The pointer loop outlives the composition that started it - it only
    // restarts when one of its keys changes - so a lambda captured directly
    // would go on reading the sync offset it was born with. Same trap
    // `rememberSwipeToSkip` documents, same fix.
    val currentNow by rememberUpdatedState(nowMs)
    val currentOnHit by rememberUpdatedState(onHit)
    val approachMs = remember(chart) {
        if (chart.difficulty == TileDifficulty.RUSH) RUSH_APPROACH_MS else APPROACH_MS
    }
    val missColor = MaterialTheme.colorScheme.error
    val textMeasurer = rememberTextMeasurer()
    val barNumberStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
    // Measured once per bar number and kept: only a couple of accent tiles are
    // ever on screen, but measuring inside the draw of every frame is exactly
    // the kind of work that turns a smooth board into a stuttering one.
    val barLabels = remember(textMeasurer, barNumberStyle) {
        mutableMapOf<Int, TextLayoutResult>()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Read out here rather than inside the Box below: BoxWithConstraintsScope
        // extends BoxScope, so the inner Box's receiver shadows this one and
        // `maxHeight` stops resolving against an implicit receiver.
        val boardHeight = maxHeight
        val boardHeightPx = with(density) { boardHeight.toPx() }
        val boardWidthPx = with(density) { maxWidth.toPx() }
        val laneWidthPx = boardWidthPx / TILE_LANES
        val hitLineY = boardHeightPx * HIT_LINE_FRACTION
        val cornerPx = with(density) { 9.dp.toPx() }
        val insetPx = with(density) { 4.dp.toPx() }
        val hitLineStroke = with(density) { 3.dp.toPx() }
        val rulePx = with(density) { 1.dp.toPx() }
        val outlinePx = with(density) { 2.dp.toPx() }
        val holdHeadPx = with(density) { 26.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.05f))
                .semantics {
                    contentDescription =
                        "Rhythm board, four lanes. Tap a lane as its tile crosses the line."
                }
                // Every change is consumed, which is also what stops a stray
                // vertical drag on the board from collapsing the whole player
                // mid-run: the sheet's own drag detector never sees an
                // unconsumed pointer.
                .pointerInput(game, isPlaying, laneWidthPx) {
                    if (!isPlaying) return@pointerInput
                    awaitPointerEventScope {
                        val lanes = mutableMapOf<PointerId, Int>()
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                when {
                                    change.changedToDownIgnoreConsumed() -> {
                                        val lane = (change.position.x / laneWidthPx)
                                            .toInt()
                                            .coerceIn(0, TILE_LANES - 1)
                                        lanes[change.id] = lane
                                        if (game.press(lane, currentNow()) != null) currentOnHit()
                                        change.consume()
                                    }

                                    change.changedToUpIgnoreConsumed() -> {
                                        lanes.remove(change.id)?.let { lane ->
                                            game.release(lane, currentNow())
                                        }
                                        change.consume()
                                    }

                                    else -> if (change.positionChanged()) change.consume()
                                }
                            }
                            // A pointer that vanishes without an up event -
                            // cancelled by a parent claiming the gesture, or by
                            // the window losing focus - would otherwise leave a
                            // lane held down forever.
                            val live = event.changes.map { it.id }.toSet()
                            lanes.keys.filterNot { it in live }.forEach { id ->
                                lanes.remove(id)?.let { game.release(it, currentNow()) }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // The one snapshot read that drives the repaint.
                val songTimeMs = songTime.longValue
                val pxPerMs = hitLineY / approachMs
                val trailMs = ((size.height - hitLineY) / pxPerMs).toLong()

                for (lane in 1 until TILE_LANES) {
                    val x = lane * laneWidthPx
                    drawLine(
                        color = accent.copy(alpha = 0.14f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = rulePx
                    )
                }

                drawRect(
                    color = accent.copy(alpha = 0.05f),
                    topLeft = Offset(0f, hitLineY),
                    size = Size(size.width, size.height - hitLineY)
                )

                for (lane in 0 until TILE_LANES) {
                    val since = songTimeMs - game.laneFlashAt(lane)
                    if (since < 0 || since > LANE_FLASH_MS) continue
                    val alpha = (1f - since.toFloat() / LANE_FLASH_MS) * 0.3f
                    drawRect(
                        color = accent.copy(alpha = alpha),
                        topLeft = Offset(lane * laneWidthPx, hitLineY - laneWidthPx * 0.2f),
                        size = Size(laneWidthPx, size.height - hitLineY + laneWidthPx * 0.2f)
                    )
                }

                // Back up far enough to catch a long hold that started before
                // the visible window but is still crossing it.
                var index = chart.indexAtOrAfter(songTimeMs - trailMs - MAX_TILE_LOOKBACK_MS)
                while (index < chart.tiles.size) {
                    val tile = chart.tiles[index]
                    if (tile.startMs - songTimeMs > approachMs) break
                    val status = game.statusOf(index)
                    if (status != TileStatus.CLEARED && status != TileStatus.SKIPPED) {
                        val bottom = hitLineY - (tile.startMs - songTimeMs) * pxPerMs
                        val top = bottom - tile.drawLenMs * pxPerMs
                        if (bottom > -cornerPx && top < size.height) {
                            drawTile(
                                tile = tile,
                                status = status,
                                left = tile.lane * laneWidthPx + insetPx,
                                width = laneWidthPx - insetPx * 2,
                                top = top,
                                bottom = bottom,
                                hitLineY = hitLineY,
                                corner = cornerPx,
                                holdHead = holdHeadPx,
                                outline = outlinePx,
                                accent = accent,
                                field = field,
                                missColor = missColor,
                                barLabel = { bar ->
                                    barLabels.getOrPut(bar) {
                                        textMeasurer.measure(
                                            AnnotatedString(bar.toString()),
                                            barNumberStyle
                                        )
                                    }
                                }
                            )
                        }
                    }
                    index++
                }

                drawLine(
                    color = accent,
                    start = Offset(0f, hitLineY),
                    end = Offset(size.width, hitLineY),
                    strokeWidth = hitLineStroke
                )
            }

            TilesComboGhost(
                game = game,
                accent = accent,
                modifier = Modifier.align(Alignment.Center)
            )

            TilesJudgmentWord(
                game = game,
                missColor = missColor,
                accent = accent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Coerced because a short board (landscape, a small phone)
                    // puts the line within 64dp of the top, and a negative
                    // padding is a crash rather than a layout.
                    .padding(top = (boardHeight * HIT_LINE_FRACTION - 64.dp).coerceAtLeast(0.dp))
            )

            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(field.copy(alpha = 0.9f))
                        .clickable(onClick = onResume),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold
                            ),
                            color = accent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "TAP TO RESUME",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                            color = accent.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One tile.
 *
 * A tap is a solid block; a downbeat carries its bar number the way a score
 * carries measure numbers; a hold is a light column with a solid head, and
 * fills in as it is held. A missed tile is not removed - it falls on as an
 * outline, so the player can see what got away rather than only that the streak
 * broke.
 */
private fun DrawScope.drawTile(
    tile: Tile,
    status: Byte,
    left: Float,
    width: Float,
    top: Float,
    bottom: Float,
    hitLineY: Float,
    corner: Float,
    holdHead: Float,
    outline: Float,
    accent: Color,
    field: Color,
    missColor: Color,
    barLabel: (Int) -> TextLayoutResult
) {
    val radius = CornerRadius(corner, corner)
    val missed = status == TileStatus.MISSED
    val held = status == TileStatus.HELD
    // Once a hold is being held, the part already past the line is spent: it is
    // drawn as being swallowed by the line rather than sliding on underneath,
    // which is the only feedback that says "keep holding".
    val drawBottom = if (held) minOf(bottom, hitLineY) else bottom
    val height = (drawBottom - top).coerceAtLeast(0f)
    if (height <= 0f) return

    if (missed) {
        drawRoundRect(
            color = missColor.copy(alpha = 0.35f),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = radius,
            style = Stroke(width = outline)
        )
        return
    }

    when (tile.kind) {
        TileKind.HOLD -> {
            drawRoundRect(
                color = accent.copy(alpha = if (held) 0.8f else 0.4f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = radius
            )
            // The head is what is actually aimed at; without it a long column
            // gives the eye nothing to time against.
            if (!held) {
                val headHeight = minOf(holdHead, height)
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(left, drawBottom - headHeight),
                    size = Size(width, headHeight),
                    cornerRadius = radius
                )
            }
        }

        TileKind.ACCENT -> {
            drawRoundRect(
                color = accent,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = radius
            )
            val label = barLabel(tile.bar)
            if (height > label.size.height * 1.6f) {
                drawText(
                    textLayoutResult = label,
                    color = field,
                    topLeft = Offset(
                        left + (width - label.size.width) / 2f,
                        top + (height - label.size.height) / 2f
                    )
                )
            }
        }

        TileKind.TAP -> {
            drawRoundRect(
                color = accent,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = radius
            )
        }
    }
}

/**
 * The streak, set large and faint behind the falling tiles.
 *
 * Its own composable because it reads `combo`, which changes on every single
 * tile: read from the board directly, that one number would recompose the
 * board's constraints, Canvas and overlays a few times a second.
 */
@Composable
private fun TilesComboGhost(
    game: TilesGameState,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val combo = game.combo
    AnimatedVisibility(
        visible = combo >= COMBO_GHOST_AT,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = combo.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold
            ),
            color = accent.copy(alpha = 0.12f)
        )
    }
}

/** The judgment, set as a pull-quote above the line and gone in half a second. */
@Composable
private fun TilesJudgmentWord(
    game: TilesGameState,
    missColor: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val serial = game.judgmentSerial
    LaunchedEffect(serial) {
        if (serial == 0) return@LaunchedEffect
        visible = true
        delay(JUDGMENT_HOLD_MS)
        visible = false
    }
    val judgment = game.lastJudgment
    AnimatedVisibility(
        visible = visible && judgment != null,
        enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) +
            slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) { it / 2 },
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = judgment?.label.orEmpty(),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold
            ),
            // The single break from the two-tone contract, and only for a miss:
            // "Miss" in the same ink as "Perfect" is a word you have to read
            // instead of a state you can feel.
            color = if (judgment == TileJudgment.MISS) missColor else accent
        )
    }
}

/**
 * What the board shows when there is no chart.
 *
 * Never a silently empty rectangle: either the analysis has not landed yet, or
 * this track has no steady tempo in it, and those are different sentences with
 * different things to offer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TilesBoardPlaceholder(
    chartState: TilesChartState,
    onStartFreestyle: () -> Unit,
    accent: Color,
    field: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(28.dp)
        ) {
            when (chartState) {
                TilesChartState.Loading -> {
                    LoadingIndicator(
                        modifier = Modifier.size(44.dp),
                        color = accent,
                        polygons = listOf(
                            MaterialShapes.Cookie4Sided,
                            MaterialShapes.Pill,
                            MaterialShapes.SoftBurst
                        )
                    )
                }

                TilesChartState.Analysing -> {
                    LoadingIndicator(
                        modifier = Modifier.size(44.dp),
                        color = accent,
                        polygons = listOf(
                            MaterialShapes.Cookie4Sided,
                            MaterialShapes.Pill,
                            MaterialShapes.SoftBurst
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Reading the beat",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold
                        ),
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Koda measures a track's tempo the first time you play it. " +
                            "The board fills in as soon as it lands.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    TilesTextAction("PLAY FREESTYLE INSTEAD", onStartFreestyle, accent, field)
                }

                TilesChartState.NoTempo -> {
                    Text(
                        text = "No steady beat here",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold
                        ),
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Nothing in this track kept a tempo confidently enough to " +
                            "chart. Freestyle lays down a steady 4/4 instead, which will " +
                            "not follow the music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    TilesTextAction("PLAY FREESTYLE", onStartFreestyle, accent, field)
                }

                is TilesChartState.Ready -> Unit
            }
        }
    }
}

@Composable
private fun TilesTextAction(
    label: String,
    onClick: () -> Unit,
    accent: Color,
    field: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = accent,
        contentColor = field
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/** The verdict on a finished run, set like a review: a grade and its numbers. */
@Composable
private fun TilesResultStrip(
    card: TilesResultCard,
    onDismiss: () -> Unit,
    field: Color,
    accent: Color
) {
    Surface(
        onClick = onDismiss,
        shape = RoundedCornerShape(18.dp),
        color = accent,
        contentColor = field,
        modifier = Modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = card.result.grade,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                )
            )
            Column {
                Text(
                    text = if (card.isBest) "NEW BEST" else "RUN COMPLETE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp)
                )
                Text(
                    text = "${formatScore(card.result.score)}  ·  " +
                        "${(card.result.accuracy * 100).toInt()}%  ·  " +
                        "×${card.result.maxCombo}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (card.result.isFullCombo) {
                    Text(
                        text = "FULL COMBO",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp)
                    )
                }
            }
        }
    }
}

/** Slim transport, so the board keeps the height. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TilesTransport(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    field: Color,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
    ) {
        TilesSeekLine(
            position = position,
            duration = duration,
            onSeekTo = onSeekTo,
            accent = accent
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EditorialCircleButton(
                onClick = onPrevious,
                accent = accent,
                field = field,
                size = 52.dp
            ) {
                Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(26.dp))
            }
            Surface(
                onClick = onPlayPause,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                color = accent,
                contentColor = field
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isBuffering && playWhenReady && !isPlaying) {
                        LoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = field,
                            polygons = listOf(
                                MaterialShapes.SoftBurst,
                                MaterialShapes.Pill,
                                MaterialShapes.Sunny
                            )
                        )
                    } else {
                        AnimatedContent(targetState = isPlaying, label = "TilesPlayWord") { playing ->
                            Text(
                                text = if (playing) "PAUSE" else "PLAY",
                                style = MaterialTheme.typography.titleMedium.copy(
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
                size = 52.dp
            ) {
                Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(26.dp))
            }
        }
    }
}

/**
 * A printed rule that happens to be the seek bar.
 *
 * Scrubbed locally and seeked once on release, like every other style here:
 * seeking per drag frame rebuffers a streamed track.
 */
@Composable
private fun TilesSeekLine(
    position: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    accent: Color
) {
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = scrubFraction
        ?: if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TilesSeek"
    )
    val shown = scrubFraction ?: animated

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        if (duration > 0L) {
                            onSeekTo((offset.x / size.width * duration).toLong().coerceIn(0L, duration))
                        }
                    }
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scrubFraction = ((scrubFraction ?: 0f) + amount / size.width)
                                .coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            scrubFraction?.let { value ->
                                if (duration > 0L) onSeekTo((value * duration).toLong())
                            }
                            scrubFraction = null
                        },
                        onDragCancel = { scrubFraction = null }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                val y = size.height / 2f
                val stroke = 2.dp.toPx()
                drawLine(
                    color = accent.copy(alpha = 0.22f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke
                )
                drawLine(
                    color = accent,
                    start = Offset(0f, y),
                    end = Offset(size.width * shown, y),
                    strokeWidth = stroke
                )
                drawCircle(
                    color = accent,
                    radius = 4.dp.toPx(),
                    center = Offset(size.width * shown, y)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shownPosition = scrubFraction?.let { (it * duration).toLong() } ?: position
            Text(
                text = formatTime(shownPosition),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = accent.copy(alpha = 0.65f)
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = accent.copy(alpha = 0.65f)
            )
        }
    }
}

/**
 * Difficulty and sync, the two things a rhythm player has to be able to change
 * without leaving the board.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TilesTuningSheet(
    difficulty: TileDifficulty,
    syncOffsetMs: Int,
    onDifficultyChange: (TileDifficulty) -> Unit,
    onSyncOffsetChange: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    field: Color,
    accent: Color
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = field,
        contentColor = accent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Tuning",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )

            Text(
                text = "DENSITY",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = accent.copy(alpha = 0.6f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TileDifficulty.entries.forEach { entry ->
                    val selected = entry == difficulty
                    Surface(
                        onClick = { onDifficultyChange(entry) },
                        shape = RoundedCornerShape(50),
                        color = if (selected) accent else Color.Transparent,
                        contentColor = if (selected) field else accent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = entry.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
            Text(
                text = "Changing this starts a new run: a score only means something " +
                    "against the chart it was set on.",
                style = MaterialTheme.typography.bodySmall,
                color = accent.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SYNC",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = accent.copy(alpha = 0.6f)
                )
                Text(
                    text = "${if (syncOffsetMs > 0) "+" else ""}$syncOffsetMs ms",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
            }
            Slider(
                value = syncOffsetMs.toFloat(),
                onValueChange = { onSyncOffsetChange(it.toInt()) },
                valueRange = ThemePreferences.TILES_SYNC_OFFSET_MIN_MS.toFloat()..
                    ThemePreferences.TILES_SYNC_OFFSET_MAX_MS.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = accent.copy(alpha = 0.25f)
                )
            )
            Text(
                text = "Every phone puts out sound a little after the player says it did. " +
                    "Raise this if tiles reach the line before you hear the beat, lower " +
                    "it if you hear the beat first.",
                style = MaterialTheme.typography.bodySmall,
                color = accent.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatScore(score: Int): String {
    if (score < 1000) return score.toString()
    val text = score.toString()
    return buildString {
        text.forEachIndexed { index, char ->
            if (index > 0 && (text.length - index) % 3 == 0) append(',')
            append(char)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** How long a tile is on screen before it reaches the line. */
private const val APPROACH_MS = 1500L
private const val RUSH_APPROACH_MS = 1250L

/** Where the line sits: low enough to be a thumb's home, high enough to read. */
private const val HIT_LINE_FRACTION = 0.78f

/** Below this the page furniture is dropped so the board keeps its height. */
private val COMPACT_HEIGHT = 560.dp

private const val LANE_FLASH_MS = 220L
private const val JUDGMENT_HOLD_MS = 520L
private const val RESULT_CARD_MS = 5000L
private const val COMBO_VISIBLE_AT = 5
private const val COMBO_GHOST_AT = 10

/** Longest a hold can be, so the draw scan knows how far back to look. */
private const val MAX_TILE_LOOKBACK_MS = 6000L

/** The chart is spent this long after its last tile, and the run is banked. */
private const val RUN_END_GRACE_MS = 1200L

private const val ANALYSIS_ATTEMPTS = 6
private const val ANALYSIS_RETRY_MS = 2500L
