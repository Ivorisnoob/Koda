package com.ivor.ivormusic.ui.standby

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.player.ChromaticMistBackground
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.SyncedLyricsView
import com.ivor.ivormusic.ui.player.rememberArtworkSeed
import com.ivor.ivormusic.ui.player.rememberSmoothProgress
import com.ivor.ivormusic.ui.theme.AmoledScheme
import com.ivor.ivormusic.ui.theme.AppOrientation
import com.ivor.ivormusic.ui.theme.buildSeedColorScheme
import com.ivor.ivormusic.ui.theme.kodaColorScheme
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standby: the playing song as a full-screen, glanceable surface for a phone
 * set down on a desk or a nightstand - cover, title, the line being sung, a
 * clock - in the spirit of iOS StandBy and Apple Music's full-screen lyrics.
 *
 * **Above everything.** It is hosted after both players and Shorts in
 * [com.ivor.ivormusic.MainActivity], owns the whole window while it is open
 * (system bars hidden, screen kept on, free to rotate) and gives all of that
 * back when it closes. It reads playback from [PlayerViewModel] and writes
 * nothing of its own, so there is no history or incognito question here.
 *
 * **Motion is physical** ([appleSpring]): it rises from the bottom edge and
 * leaves along the same path, follows a downward swipe 1:1 with resistance
 * past the top, and decides on release from where the flick was *going*
 * ([projectedDistance]) rather than where the finger stopped. Any of it can be
 * caught mid-flight. Pausing is a calm morph rather than a cut: the cover
 * settles back and the clock grows until it is a bedside clock, and play
 * reverses it.
 *
 * **Bedside manners.** The controls fade after a few seconds untouched, the
 * whole screen dims after five minutes (the first touch only wakes it), and
 * the content drifts a few pixels every minute so an OLED panel never holds
 * one image. It is always dark: a bright page is the last thing a dark room
 * wants, whatever the app's own theme is.
 */
@Composable
fun StandbyOverlay(viewModel: PlayerViewModel) {
    val requested by viewModel.isStandbyOpen.collectAsState()
    val song by viewModel.currentSong.collectAsState()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(requested) { if (requested) shown = true }
    // The player was cleared underneath it: there is nothing left to show.
    LaunchedEffect(song == null) { if (song == null) viewModel.closeStandby() }
    if (!shown) return
    StandbyHost(
        viewModel = viewModel,
        closeRequested = !requested,
        onGone = {
            shown = false
            viewModel.closeStandby()
        },
    )
}

@Composable
private fun StandbyHost(
    viewModel: PlayerViewModel,
    closeRequested: Boolean,
    onGone: () -> Unit,
) {
    val song = viewModel.currentSong.collectAsState().value ?: return
    StandbyWindowEffect()
    val (scheme, amoled) = rememberStandbyColorScheme(song)

    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val scope = rememberCoroutineScope()
                val haptics = rememberKodaHaptics()
                // 0 is open, heightPx is off the bottom edge. One value drives
                // the entrance, the swipe, the fling and the exit, so each can
                // take over from the others at any frame.
                val offset = remember { Animatable(heightPx) }
                val backProgress = remember { Animatable(0f) }
                var exiting by remember { mutableStateOf(false) }
                val currentOnGone by rememberUpdatedState(onGone)

                fun dismiss(velocity: Float) {
                    if (exiting) return
                    exiting = true
                    scope.launch {
                        offset.animateTo(heightPx, appleSpring(0.42f), initialVelocity = velocity.coerceAtLeast(0f))
                        currentOnGone()
                    }
                }

                LaunchedEffect(Unit) { offset.animateTo(0f, appleSpring(0.55f)) }
                LaunchedEffect(closeRequested) { if (closeRequested) dismiss(0f) }

                PredictiveBackHandler(enabled = !exiting) { events ->
                    try {
                        events.collect { backProgress.snapTo(it.progress) }
                        dismiss(0f)
                    } catch (e: CancellationException) {
                        // Springs back from the screen's scope: this coroutine
                        // is the one being cancelled.
                        scope.launch { backProgress.animateTo(0f, appleSpring(0.35f)) }
                        throw e
                    }
                }

                // Idle state: controls fade after a few seconds, the screen dims
                // after a few minutes, and any touch resets both.
                var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var chromeVisible by remember { mutableStateOf(true) }
                var dimmed by remember { mutableStateOf(false) }
                LaunchedEffect(lastTouch, chromeVisible) {
                    if (chromeVisible) {
                        delay(CHROME_IDLE_MS)
                        chromeVisible = false
                    }
                }
                val context = LocalContext.current
                val dimWhenIdle by remember(context) { ThemePreferences(context) }.standbyDim.collectAsState()
                LaunchedEffect(lastTouch, dimWhenIdle) {
                    dimmed = false
                    if (!dimWhenIdle) return@LaunchedEffect
                    delay(DIM_IDLE_MS)
                    dimmed = true
                }
                val dim by animateFloatAsState(
                    targetValue = if (dimmed) DIM_SCRIM_ALPHA else 0f,
                    animationSpec = tween(if (dimmed) 1600 else 350, easing = FastOutSlowInEasing),
                    label = "StandbyDim",
                )
                val dimmedNow by rememberUpdatedState(dimmed)

                // The backdrop the sheet slides over: the app, dimmed in
                // proportion to how much of Standby is on screen.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1f - offset.value / heightPx).coerceIn(0f, 1f) }
                        .background(scheme.scrim.copy(alpha = 0.72f))
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        // Every touch is noticed here first, before any control
                        // sees it. While dimmed, the first touch only wakes the
                        // screen: a hand reaching for a dark phone in the night
                        // should not also skip the song.
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.type == PointerEventType.Press) {
                                        if (dimmedNow) event.changes.forEach { it.consume() }
                                        lastTouch = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                        // Swipe down to leave. Read outside the moving layer, so
                        // the finger is measured against the screen and not
                        // against a surface that is travelling with it.
                        .pointerInput(heightPx) {
                            val tracker = VelocityTracker()
                            var raw = 0f
                            var pastThreshold = false
                            detectVerticalDragGestures(
                                onDragStart = {
                                    tracker.resetTracking()
                                    // Caught mid-flight: start from where it is.
                                    raw = offset.value
                                    pastThreshold = raw > heightPx * DISMISS_FRACTION
                                    scope.launch { offset.stop() }
                                },
                                onVerticalDrag = { change, dy ->
                                    if (exiting) return@detectVerticalDragGestures
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    raw += dy
                                    val shown = if (raw < 0f) -rubberBand(-raw, heightPx) else raw
                                    val past = shown > heightPx * DISMISS_FRACTION
                                    if (past != pastThreshold) {
                                        pastThreshold = past
                                        haptics.threshold()
                                    }
                                    scope.launch { offset.snapTo(shown) }
                                },
                                onDragEnd = {
                                    val velocity = tracker.calculateVelocity().y
                                    val landing = offset.value + projectedDistance(velocity)
                                    if (landing > heightPx * DISMISS_FRACTION && velocity > -300f) {
                                        dismiss(velocity)
                                    } else {
                                        // A thrown surface overshoots a little on
                                        // the way home; a placed one does not.
                                        val bouncy = kotlin.math.abs(velocity) > 900f
                                        scope.launch {
                                            offset.animateTo(
                                                0f,
                                                appleSpring(0.42f, if (bouncy) 0.82f else 1f),
                                                initialVelocity = velocity,
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { offset.animateTo(0f, appleSpring(0.42f)) }
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { chromeVisible = !chromeVisible })
                        }
                        .graphicsLayer {
                            val y = offset.value
                            translationY = y
                            val back = backProgress.value
                            val scale = 1f - BACK_SCALE_DEPTH * back
                            scaleX = scale
                            scaleY = scale
                            // The screen becomes a card as it is pulled away:
                            // rounded by the time it has moved a fifth of the way.
                            val card = max((y / (heightPx * 0.2f)).coerceIn(0f, 1f), back)
                            shape = RoundedCornerShape((CARD_CORNER_DP * card).dp)
                            clip = card > 0f
                        }
                ) {
                    StandbyBackdrop(song = song, amoled = amoled)
                    StandbySurface(
                        viewModel = viewModel,
                        song = song,
                        chromeVisible = chromeVisible,
                        onInteraction = { lastTouch = System.currentTimeMillis() },
                    )
                    // The grabber: says "this pulls down", and is the way out for
                    // anyone who cannot swipe.
                    Grabber(
                        visible = chromeVisible,
                        onClick = { dismiss(0f) },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = dim }
                            .background(scheme.scrim)
                    )
                }
            }
        }
    }
}

/**
 * What Standby does to the window while it is open, all of it undone on the
 * way out: the screen stays on, the system bars step aside (a swipe from the
 * edge still brings them back for a moment), and the phone may turn sideways
 * even where Koda is otherwise held upright - Standby is made for landscape.
 * The system rotation lock is still respected.
 */
@Composable
private fun StandbyWindowEffect() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(view, context) {
        val activity = context.findActivity()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        view.keepScreenOn = true
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.let(AppOrientation::apply)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Standby's scheme: always the dark one. From the playing cover when album
 * colours are on, as the player's is; otherwise the app's own palette, dark.
 * The roles drawn here ease from one song's colours to the next rather than
 * cutting, in step with the cover crossfading.
 *
 * Returns whether AMOLED is on, which takes the ambient backdrop away and
 * leaves true black.
 */
@Composable
private fun rememberStandbyColorScheme(song: Song): Pair<ColorScheme, Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val artworkColors by prefs.playerArtworkColors.collectAsState()
    val palette by prefs.colorPalette.collectAsState()
    val style by prefs.paletteStyle.collectAsState()
    val amoled by prefs.amoledTheme.collectAsState()
    val configuration = LocalConfiguration.current
    val seed = rememberArtworkSeed(artworkColors, song.albumArtUri?.toString() ?: song.thumbnailUrl)
    val base = remember(context, configuration, palette, amoled, style) {
        kodaColorScheme(context, darkTheme = true, colorPalette = palette, amoledDark = amoled, paletteStyle = style)
    }
    val target = remember(base, seed, style, amoled) {
        seed?.let { buildSeedColorScheme(it, dark = true, style = style) }
            ?.let { if (amoled) with(AmoledScheme) { it.amoled() } else it }
            ?: base
    }
    val spec = tween<androidx.compose.ui.graphics.Color>(SCHEME_FADE_MS, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, spec, label = "StandbyPrimary")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "StandbyOnSurface")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "StandbyOnSurfaceVariant")
    val background by animateColorAsState(target.background, spec, label = "StandbyBackground")
    val container by animateColorAsState(target.surfaceContainerHigh, spec, label = "StandbyContainer")
    val scheme = target.copy(
        primary = primary,
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        background = background,
        surfaceContainerHigh = container,
    )
    return scheme to amoled
}

/**
 * The room the song sits in: the cover's colours drifting as a mist, the cover
 * itself as a soft blur where the platform can draw one (Android 12+), and a
 * scrim deepening toward the edges where the text sits. AMOLED is plain black,
 * which is also the kindest thing for a screen left on all night.
 */
@Composable
private fun StandbyBackdrop(song: Song, amoled: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        if (amoled) return@Box
        ChromaticMistBackground(
            albumArtUrl = song.albumArtUri?.toString() ?: song.highResThumbnailUrl ?: song.thumbnailUrl,
            fallbackColor = scheme.background,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AnimatedContent(
                targetState = song,
                contentKey = { it.id },
                transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) },
                label = "StandbyBackdropArt",
            ) { shown ->
                SongArtwork(
                    song = shown,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                            alpha = 0.42f
                        }
                        .blur(90.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.scrim.copy(alpha = 0.30f),
                        0.45f to scheme.scrim.copy(alpha = 0.18f),
                        1f to scheme.scrim.copy(alpha = 0.55f),
                    )
                )
        )
    }
}

/** Which way the last skip went, so the next song arrives from that side. */
private class SkipDirection {
    private var lastIndex = -1
    var direction = 1
        private set

    fun update(index: Int): Int {
        if (index >= 0) {
            if (lastIndex >= 0 && index != lastIndex) direction = if (index > lastIndex) 1 else -1
            lastIndex = index
        }
        return direction
    }
}

/** Everything Standby draws, given the state it is in. */
private class StandbyState(
    val song: Song,
    val paused: State<Float>,
    val isPaused: Boolean,
    val showLyrics: Boolean,
    val chromeVisible: Boolean,
    val direction: Int,
)

@Composable
private fun StandbySurface(
    viewModel: PlayerViewModel,
    song: Song,
    chromeVisible: Boolean,
    onInteraction: () -> Unit,
) {
    val playWhenReady by viewModel.playWhenReady.collectAsState()
    val lyrics by viewModel.lyricsResult.collectAsState()
    val queue by viewModel.playOrderQueue.collectAsState()
    val currentItemId by viewModel.currentQueueItemId.collectAsState()
    val skip = remember { SkipDirection() }
    val direction = skip.update(queue.indexOfFirst { it.id == currentItemId })

    // Paused is a place, not a cut: one calm spring carries the cover back
    // and the clock forward, and play brings them home along the same path.
    val paused = animateFloatAsState(
        targetValue = if (playWhenReady) 0f else 1f,
        animationSpec = appleSpring(0.75f),
        label = "StandbyPaused",
    )
    val hasLyrics = (lyrics as? LyricsResult.Success)?.lines?.isNotEmpty() == true
    val state = StandbyState(
        song = song,
        paused = paused,
        isPaused = !playWhenReady,
        showLyrics = hasLyrics && playWhenReady,
        chromeVisible = chromeVisible,
        direction = direction,
    )

    // A few pixels of drift every minute: no pixel holds one colour all night.
    val driftX = remember { Animatable(0f) }
    val driftY = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        val reach = with(density) { DRIFT_DP.dp.toPx() }
        while (true) {
            delay(DRIFT_INTERVAL_MS)
            val x = Random.nextFloat() * 2f - 1f
            val y = Random.nextFloat() * 2f - 1f
            launch { driftX.animateTo(x * reach, tween(DRIFT_MOVE_MS, easing = FastOutSlowInEasing)) }
            driftY.animateTo(y * reach, tween(DRIFT_MOVE_MS, easing = FastOutSlowInEasing))
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = driftX.value
                translationY = driftY.value
            }
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        if (maxWidth > maxHeight) {
            StandbyLandscape(viewModel, state, maxWidth, maxHeight, onInteraction)
        } else {
            StandbyPortrait(viewModel, state, maxWidth, maxHeight, onInteraction)
        }
    }
}

@Composable
private fun StandbyLandscape(
    viewModel: PlayerViewModel,
    state: StandbyState,
    width: Dp,
    height: Dp,
    onInteraction: () -> Unit,
) {
    val horizontal = (width * 0.05f).coerceIn(28.dp, 64.dp)
    val vertical = (height * 0.08f).coerceIn(20.dp, 40.dp)
    val coverSize = min((height - vertical * 2).value, (width * 0.40f).value).dp
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StandbyCover(
            song = state.song,
            direction = state.direction,
            modifier = Modifier
                .size(coverSize)
                // iOS Music's own gesture for "paused": the cover steps back.
                .graphicsLayer {
                    val s = lerpFloat(1f, PAUSED_COVER_SCALE, state.paused.value)
                    scaleX = s
                    scaleY = s
                },
        )
        Spacer(Modifier.width(horizontal))
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val clockStyle = rememberClockStyle(availableWidth = maxWidth * 0.96f, maxHeight = maxHeight * 0.62f)
            val smallScale = (LANDSCAPE_CLOCK_SP / clockStyle.fontSize.value).coerceAtMost(1f)
            StandbyStack(
                kinds = listOf(SlotKind.CLOCK, SlotKind.FIXED, SlotKind.FLEXIBLE, SlotKind.FIXED, SlotKind.FIXED),
                contents = listOf(
                    { ClockSlot(state, clockStyle) },
                    { DateSlot(state) },
                    { LyricsRegion(viewModel, state) },
                    { StandbyHeadline(state, singleLine = true) },
                    { ChromeBlock(viewModel, state, onInteraction) },
                ),
                clockScale = { lerpFloat(smallScale, 1f, state.paused.value) },
                centered = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StandbyPortrait(
    viewModel: PlayerViewModel,
    state: StandbyState,
    width: Dp,
    height: Dp,
    onInteraction: () -> Unit,
) {
    val horizontal = (width * 0.07f).coerceIn(20.dp, 40.dp)
    val contentWidth = width - horizontal * 2
    val coverSize = min(contentWidth.value, (height * 0.40f).value).dp
    val clockStyle = rememberClockStyle(availableWidth = contentWidth * 0.94f, maxHeight = height * 0.22f)
    val smallScale = (PORTRAIT_CLOCK_SP / clockStyle.fontSize.value).coerceAtMost(1f)
    StandbyStack(
        kinds = listOf(
            SlotKind.CLOCK, SlotKind.FIXED, SlotKind.FIXED, SlotKind.FIXED, SlotKind.FLEXIBLE, SlotKind.FIXED,
        ),
        contents = listOf(
            { ClockSlot(state, clockStyle) },
            { DateSlot(state) },
            {
                StandbyCover(
                    song = state.song,
                    direction = state.direction,
                    modifier = Modifier
                        .padding(top = 20.dp)
                        // Laid out at its scaled size, so everything below
                        // closes up as it settles back and the clock above has
                        // room to grow.
                        .layoutScale { lerpFloat(1f, PAUSED_PORTRAIT_COVER_SCALE, state.paused.value) }
                        .size(coverSize),
                )
            },
            {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    StandbyHeadline(state, singleLine = false)
                }
            },
            { LyricsRegion(viewModel, state) },
            { ChromeBlock(viewModel, state, onInteraction) },
        ),
        clockScale = { lerpFloat(smallScale, 1f, state.paused.value) },
        centered = true,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontal)
            .padding(top = 36.dp, bottom = 28.dp),
    )
}

/** How a [StandbyStack] row takes its height. */
private enum class SlotKind {
    /** The clock: as large as its spring asks, never larger than the room left. */
    CLOCK,

    /** Its own height. */
    FIXED,

    /** Whatever is left once the others are laid out. */
    FLEXIBLE,
}

/**
 * Standby's column: rows stacked top to bottom, with the clock growing into
 * whatever room the rest leave free.
 *
 * A plain Column cannot say that. Paused, the clock wants to be a bedside
 * clock; on a phone held sideways there are about 330dp for it, the date, the
 * title and the controls together, and a clock sized up front either crowded
 * the controls off the bottom or stayed small for good. Here the clock is
 * measured at its largest and scaled to the lesser of what its spring asks for
 * and what fits, every frame. So when the controls fold away a few seconds
 * after a pause, the clock goes on growing into the space they leave, and when
 * a tap brings them back it makes room again - one continuous motion either
 * way, read in the layout phase without recomposing anything.
 */
@Composable
private fun StandbyStack(
    kinds: List<SlotKind>,
    contents: List<@Composable () -> Unit>,
    clockScale: () -> Float,
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    Layout(contents = contents, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val open = Constraints(maxWidth = width)
        val placeables = arrayOfNulls<Placeable>(kinds.size)
        var fixedHeight = 0
        kinds.forEachIndexed { i, kind ->
            if (kind == SlotKind.FIXED) {
                val placeable = measurables[i].firstOrNull()?.measure(open)
                placeables[i] = placeable
                fixedHeight += placeable?.height ?: 0
            }
        }
        val clock = measurables[kinds.indexOf(SlotKind.CLOCK)].firstOrNull()?.measure(open)
        val room = (height - fixedHeight).coerceAtLeast(0)
        val scale = if (clock == null || clock.height == 0) {
            0f
        } else {
            min(clockScale(), room.toFloat() / clock.height).coerceAtLeast(0f)
        }
        val clockWidth = ((clock?.width ?: 0) * scale).roundToInt()
        val clockHeight = ((clock?.height ?: 0) * scale).roundToInt()
        val flexHeight = (room - clockHeight).coerceAtLeast(0)
        kinds.forEachIndexed { i, kind ->
            if (kind == SlotKind.FLEXIBLE) {
                placeables[i] = measurables[i].firstOrNull()?.measure(Constraints.fixed(width, flexHeight))
            }
        }
        layout(width, height) {
            var y = 0
            kinds.forEachIndexed { i, kind ->
                when (kind) {
                    SlotKind.CLOCK -> {
                        clock?.placeWithLayer(if (centered) (width - clockWidth) / 2 else 0, y) {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        y += clockHeight
                    }
                    SlotKind.FIXED, SlotKind.FLEXIBLE -> {
                        val placeable = placeables[i]
                        if (placeable != null) {
                            placeable.place(if (centered) (width - placeable.width) / 2 else 0, y)
                        }
                        y += if (kind == SlotKind.FLEXIBLE) flexHeight else placeable?.height ?: 0
                    }
                }
            }
        }
    }
}

/**
 * The clock's full-size style: Light at whatever size fills [availableWidth]
 * with "00:00", capped by [maxHeight]. It is drawn at that size and scaled
 * down while music plays, never up.
 */
@Composable
private fun rememberClockStyle(availableWidth: Dp, maxHeight: Dp): TextStyle {
    val base = MaterialTheme.typography.displayLarge.copy(
        fontWeight = FontWeight.Light,
        fontSize = 100.sp,
        lineHeight = 1.em,
        letterSpacing = (-0.03).em,
        fontFeatureSettings = "tnum, lnum",
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(base, availableWidth, maxHeight, density) {
        with(density) {
            val atHundred = measurer.measure("00:00", base)
            val byWidth = 100f * availableWidth.toPx() / atHundred.size.width.coerceAtLeast(1)
            val byHeight = 100f * maxHeight.toPx() / atHundred.size.height.coerceAtLeast(1)
            base.copy(fontSize = min(byWidth, byHeight).coerceAtLeast(24f).sp)
        }
    }
}

@Composable
private fun ClockSlot(state: StandbyState, style: TextStyle) {
    val context = LocalContext.current
    val now by rememberMinuteClock()
    val time = remember(now) { clockText(context, now) }
    val scheme = MaterialTheme.colorScheme
    // A bedside clock wears the album's accent; a playing one stays quiet.
    val color by animateColorAsState(
        targetValue = if (state.isPaused) scheme.primary else scheme.onSurface,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "StandbyClockColor",
    )
    StandbyClock(timeText = time, style = style.copy(color = color))
}

/** The date, and the battery while charging, under a paused clock only. */
@Composable
private fun DateSlot(state: StandbyState) {
    val now by rememberMinuteClock()
    androidx.compose.animation.AnimatedVisibility(
        visible = state.isPaused,
        enter = fadeIn(tween(500, delayMillis = 150)) + expandVertically(appleSpring(0.6f)),
        exit = fadeOut(tween(200)) + shrinkVertically(appleSpring(0.5f)),
    ) {
        DateLine(now)
    }
}

@Composable
private fun DateLine(now: Long) {
    val battery by rememberBatteryInfo()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = remember(now) { dateText(now) },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
            color = scheme.onSurfaceVariant,
        )
        battery?.takeIf { it.charging }?.let { info ->
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.standby_battery_percent, info.percent),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The lines being sung, in the room between the clock and the title. It takes
 * whatever height is free, so it grows as the controls fade away. Absent
 * lyrics leave the room empty rather than saying so: on a bedside screen "No
 * lyrics" is noise, not information.
 */
@Composable
private fun LyricsRegion(viewModel: PlayerViewModel, state: StandbyState) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = state.showLyrics,
            enter = fadeIn(tween(450, delayMillis = 120)),
            exit = fadeOut(tween(250)),
        ) {
            val lyrics by viewModel.lyricsResult.collectAsState()
            val position by viewModel.progress.collectAsState()
            val isPlaying by viewModel.isPlaying.collectAsState()
            val scheme = MaterialTheme.colorScheme
            SyncedLyricsView(
                lyricsResult = lyrics,
                currentPositionMs = position,
                isPlaying = isPlaying,
                onSeekTo = viewModel::seekTo,
                modifier = Modifier.fillMaxSize(),
                primaryColor = scheme.primary,
                onSurfaceColor = scheme.onSurface,
                onSurfaceVariantColor = scheme.onSurfaceVariant,
                // Flush with the title below, so left-aligned lines and the
                // title share one edge.
                horizontalGutter = 0.dp,
                // Standby's panel is small and was tuned around a centred
                // line and the plain scroll; it keeps both.
                anchorFraction = 0.5f,
                cascadeScroll = false,
            )
        }
    }
}

@Composable
private fun StandbyCover(song: Song, direction: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(COVER_CORNER_DP.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            // The cover lifts off the page in its own colour.
            .shadow(
                elevation = 36.dp,
                shape = shape,
                ambientColor = scheme.primary.copy(alpha = 0.45f),
                spotColor = scheme.primary.copy(alpha = 0.55f),
            )
            .clip(shape)
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.28f),
        )
        AnimatedContent(
            targetState = song,
            contentKey = { it.id },
            transitionSpec = {
                val d = direction
                (slideInHorizontally(appleSpring(0.5f)) { (it * 0.22f * d).toInt() } +
                    fadeIn(tween(260)) + scaleIn(appleSpring(0.5f), initialScale = 0.94f)) togetherWith
                    (slideOutHorizontally(appleSpring(0.5f)) { (-it * 0.22f * d).toInt() } +
                        fadeOut(tween(200)) + scaleOut(appleSpring(0.5f), targetScale = 0.94f))
            },
            label = "StandbyCoverArt",
        ) { shown ->
            SongArtwork(
                song = shown,
                contentDescription = stringResource(R.string.standby_cover_description, shown.title),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StandbyHeadline(state: StandbyState, singleLine: Boolean) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = state.song,
        contentKey = { it.id },
        transitionSpec = {
            val d = state.direction
            (slideInHorizontally(appleSpring(0.5f)) { (it * 0.06f * d).toInt() } + fadeIn(tween(320, delayMillis = 60))) togetherWith
                (slideOutHorizontally(appleSpring(0.5f)) { (-it * 0.06f * d).toInt() } + fadeOut(tween(160))) using
                SizeTransform(clip = false)
        },
        modifier = Modifier.fillMaxWidth(),
        label = "StandbyHeadline",
    ) { shown ->
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = shown.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                ),
                color = scheme.onSurface,
                maxLines = if (singleLine) 1 else 2,
                overflow = if (singleLine) TextOverflow.Clip else TextOverflow.Ellipsis,
                // A long title glides across rather than being cut.
                modifier = if (singleLine) {
                    Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 2500, repeatDelayMillis = 4000)
                } else {
                    Modifier
                },
            )
            if (shown.artist.isNotBlank()) {
                Text(
                    text = shown.artist,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Progress and transport, which fade and fold away when left alone. */
@Composable
private fun ChromeBlock(
    viewModel: PlayerViewModel,
    state: StandbyState,
    onInteraction: () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = state.chromeVisible,
        enter = fadeIn(tween(260)) + expandVertically(appleSpring(0.45f), expandFrom = Alignment.Top),
        exit = fadeOut(tween(320)) + shrinkVertically(appleSpring(0.6f), shrinkTowards = Alignment.Top),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            StandbyProgress(viewModel, onInteraction)
            Spacer(Modifier.height(4.dp))
            StandbyTransport(viewModel, state.isPaused)
        }
    }
}

/**
 * A thin capsule that thickens under the finger, as Apple Music's does, and
 * moves relative to where it was grabbed rather than jumping to the touch.
 * Elapsed on the left, remaining on the right.
 */
@Composable
private fun StandbyProgress(viewModel: PlayerViewModel, onInteraction: () -> Unit) {
    val position by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    var scrub by remember { mutableStateOf<Float?>(null) }
    var awaitingSeek by remember { mutableStateOf(false) }
    // Hold the released position until the player reports the seek, so the
    // bar does not flick back to the old position for a moment.
    LaunchedEffect(position) {
        if (awaitingSeek) {
            awaitingSeek = false
            scrub = null
        }
    }
    LaunchedEffect(awaitingSeek) {
        if (awaitingSeek) {
            delay(1500)
            awaitingSeek = false
            scrub = null
        }
    }
    val smooth = rememberSmoothProgress(
        positionMs = position,
        durationMs = duration,
        isPlaying = isPlaying,
        scrubPositionMs = scrub?.let { it * duration },
        speed = speed,
    )
    val active = scrub != null && !awaitingSeek
    val thickness by animateDpAsState(
        targetValue = if (active) 12.dp else 6.dp,
        animationSpec = appleSpring(0.35f, 0.75f),
        label = "StandbyBarThickness",
    )
    val scheme = MaterialTheme.colorScheme
    val track = scheme.onSurface.copy(alpha = 0.22f)
    val fill = scheme.onSurface.copy(alpha = 0.92f)

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    if (duration <= 0L) return@pointerInput
                    var start = 0f
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            start = smooth.value
                            travelled = 0f
                            scrub = start
                            onInteraction()
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            travelled += dx
                            scrub = (start + travelled / size.width).coerceIn(0f, 1f)
                            onInteraction()
                        },
                        onDragEnd = {
                            scrub?.let { viewModel.seekTo((it * duration).toLong()) }
                            awaitingSeek = true
                        },
                        onDragCancel = { scrub = null },
                    )
                }
                .drawBehind {
                    val h = thickness.toPx()
                    val top = (size.height - h) / 2f
                    val radius = CornerRadius(h / 2f, h / 2f)
                    drawRoundRect(track, Offset(0f, top), Size(size.width, h), radius)
                    val played = size.width * smooth.value.coerceIn(0f, 1f)
                    if (played > 0f) {
                        drawRoundRect(fill, Offset(0f, top), Size(max(played, h), h), radius)
                    }
                }
        )
        val shownMs = scrub?.let { (it * duration).toLong() } ?: position
        Row(Modifier.fillMaxWidth()) {
            val timeStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")
            Text(formatTime(shownMs), style = timeStyle, color = scheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatTime((duration - shownMs).coerceAtLeast(0L)),
                style = timeStyle,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun StandbyTransport(viewModel: PlayerViewModel, paused: Boolean) {
    val isBuffering by viewModel.isBuffering.collectAsState()
    val haptics = rememberKodaHaptics()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            contentDescription = stringResource(R.string.cd_previous),
            size = 64.dp,
            onClick = {
                haptics.confirm()
                viewModel.skipToPrevious()
            },
        ) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(40.dp))
        }
        GlyphButton(
            contentDescription = stringResource(if (paused) R.string.cd_play else R.string.cd_pause),
            size = 80.dp,
            onClick = {
                haptics.toggle(paused)
                viewModel.togglePlayPause()
            },
        ) {
            if (isBuffering && !paused) {
                CircularProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    color = scheme.onSurface.copy(alpha = 0.45f),
                    strokeWidth = 2.dp,
                )
            }
            AnimatedContent(
                targetState = paused,
                transitionSpec = {
                    (scaleIn(appleSpring(0.35f, 0.7f), initialScale = 0.5f) + fadeIn(tween(120))) togetherWith
                        (scaleOut(appleSpring(0.3f), targetScale = 0.5f) + fadeOut(tween(100)))
                },
                label = "StandbyPlayPause",
            ) { showPlay ->
                Icon(
                    imageVector = if (showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(58.dp),
                )
            }
        }
        GlyphButton(
            contentDescription = stringResource(R.string.cd_next),
            size = 64.dp,
            onClick = {
                haptics.confirm()
                viewModel.skipToNext()
            },
        ) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
}

/**
 * A bare glyph, as Apple draws transport controls: no container until it is
 * touched. On touch-down - not on release - it presses in and a soft disc
 * appears behind it; on release it springs back with a little life.
 */
@Composable
private fun GlyphButton(
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.84f else 1f,
        animationSpec = if (pressed) appleSpring(0.16f) else appleSpring(0.4f, 0.6f),
        label = "GlyphScale",
    )
    val highlight by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) tween(60) else tween(280, easing = FastOutSlowInEasing),
        label = "GlyphHighlight",
    )
    val disc = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(size)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .drawBehind { drawCircle(disc.copy(alpha = 0.12f * highlight)) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun Grabber(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 260 else 320),
        label = "StandbyGrabber",
    )
    val description = stringResource(R.string.standby_close)
    Box(
        modifier = modifier
            .padding(top = 6.dp)
            .size(width = 72.dp, height = 28.dp)
            .graphicsLayer { this.alpha = alpha }
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = visible,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
        )
    }
}

private const val DISMISS_FRACTION = 0.25f
private const val BACK_SCALE_DEPTH = 0.08f
private const val CARD_CORNER_DP = 36f
private const val COVER_CORNER_DP = 28f
private const val PAUSED_COVER_SCALE = 0.82f
private const val PAUSED_PORTRAIT_COVER_SCALE = 0.62f
private const val LANDSCAPE_CLOCK_SP = 54f
private const val PORTRAIT_CLOCK_SP = 60f
private const val CHROME_IDLE_MS = 5_000L
private const val DIM_IDLE_MS = 5 * 60_000L
private const val DIM_SCRIM_ALPHA = 0.62f
private const val DRIFT_DP = 6f
private const val DRIFT_INTERVAL_MS = 60_000L
private const val DRIFT_MOVE_MS = 4_000
private const val SCHEME_FADE_MS = 700
