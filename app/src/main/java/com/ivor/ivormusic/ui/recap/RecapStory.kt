package com.ivor.ivormusic.ui.recap

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.recap.Recap
import com.ivor.ivormusic.data.recap.RecapSong
import com.ivor.ivormusic.ui.standby.appleSpring
import com.ivor.ivormusic.ui.standby.projectedDistance
import com.ivor.ivormusic.ui.theme.PaletteStyle
import com.ivor.ivormusic.ui.theme.buildSeedColorScheme
import com.ivor.ivormusic.util.KodaHaptics
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/** The cards a story can hold, in the order they are told. */
internal enum class RecapCardKind {
    DOOR, SONG, RACE, ARTIST, REGULARS, RIDGE, HOURS, CAME_BACK, CALENDAR, SITTING, NEW_ARTIST, ODD_HOUR, PRESSING
}

/** Only the cards the plays can carry: a quiet week is a short story, never a padded one. */
internal fun cardsFor(recap: Recap): List<RecapCardKind> = buildList {
    add(RecapCardKind.DOOR)
    if (recap.topSongs.isNotEmpty()) add(RecapCardKind.SONG)
    if (recap.race != null) add(RecapCardKind.RACE)
    if (recap.topArtists.isNotEmpty() && recap.topArtistShare > 0f) add(RecapCardKind.ARTIST)
    if (recap.topArtists.size >= 3) add(RecapCardKind.REGULARS)
    if (recap.topArtists.count { it.plays >= 5 } >= 2) add(RecapCardKind.RIDGE)
    if (recap.plays >= 10) add(RecapCardKind.HOURS)
    if (recap.cameBack != null) add(RecapCardKind.CAME_BACK)
    if (recap.elapsedDays >= 7 && recap.activeDays >= 3) add(RecapCardKind.CALENDAR)
    if (recap.longSitting != null) add(RecapCardKind.SITTING)
    if (recap.newArtist != null) add(RecapCardKind.NEW_ARTIST)
    if (recap.oddHour != null) add(RecapCardKind.ODD_HOUR)
    add(RecapCardKind.PRESSING)
}

/**
 * Where the travelling window should be: a slot a card laid out, in the
 * story's own coordinates. [follow] snaps the window to it every frame (a
 * race's head, a scrub) instead of springing there.
 */
internal data class WindowSlot(
    val bounds: Rect,
    val shape: RecapShape,
    val songId: String?,
    val follow: Boolean = false,
    val visible: Boolean = true,
    val rotation: Float = 0f,
    /** The travel's spring response, seconds. */
    val response: Float = 0.55f,
)

/**
 * What a card is given: the recap, the card's own clock (seconds since it
 * appeared), the covers' colours, the window, and what the card can do.
 */
@Stable
internal class RecapStoryScope(
    val recap: Recap,
    val clock: () -> Float,
    private val isCurrent: () -> Boolean,
    private val colours: Map<String, Color>,
    private val container: () -> LayoutCoordinates?,
    private val onSlot: (WindowSlot) -> Unit,
    val play: (List<String>, String?) -> Unit,
    val openProof: () -> Unit,
    val openShare: () -> Unit,
    val haptics: KodaHaptics,
    val fallback: Color,
) {
    /** A song's colour as drawn on the room; the scheme's quiet tone until it is known. */
    fun colourOf(songId: String?): Color = songId?.let { colours[it] }?.onRoom() ?: fallback

    /** As extracted, for the room's tone and the note's words; null until known. */
    fun rawColourOf(songId: String?): Color? = songId?.let { colours[it] }

    fun song(id: String?): RecapSong? = id?.let { recap.songs[it] }

    fun coverOf(id: String?): String? = song(id)?.thumbnailUrl

    private data class Spec(
        val shape: RecapShape,
        val songId: String?,
        val follow: Boolean,
        val visible: Boolean,
        val rotation: Float,
        val response: Float,
    )

    private fun report(coords: LayoutCoordinates, spec: Spec) {
        if (!isCurrent()) return
        val parent = container() ?: return
        if (!coords.isAttached || !parent.isAttached) return
        val bounds = parent.localBoundingBoxOf(coords, clipBounds = false)
        onSlot(WindowSlot(bounds, spec.shape, spec.songId, spec.follow, spec.visible, spec.rotation, spec.response))
    }

    /**
     * Put the travelling window on whatever this modifies. Reported from
     * layout, so a slot that moves (an offset read from the clock) reports
     * every frame it moves; and again whenever what it asks for changes - a
     * new cover in the same place moves nothing in layout.
     */
    @Composable
    fun slot(
        shape: RecapShape,
        songId: String?,
        follow: Boolean = false,
        visible: Boolean = true,
        rotation: Float = 0f,
        response: Float = 0.55f,
    ): Modifier {
        val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
        val spec = Spec(shape, songId, follow, visible, rotation, response)
        val latest by rememberUpdatedState(spec)
        LaunchedEffect(spec) { holder[0]?.let { report(it, spec) } }
        return Modifier.onGloballyPositioned { coords ->
            holder[0] = coords
            report(coords, latest)
        }
    }
}

/** Opens the recap shelf from anywhere in the app. Provided once, by MainActivity. */
val LocalRecapLauncher = staticCompositionLocalOf<() -> Unit> {
    error("No recap host: MainActivity provides LocalRecapLauncher")
}

/**
 * The story, hosted above everything in MainActivity as Standby is: it owns
 * the window while open. Always dark, in a scheme from the period's top cover.
 */
@Composable
fun RecapStoryOverlay(
    viewModel: RecapViewModel,
    localSongs: List<Song>,
    onPlay: (List<Song>, Song?) -> Unit,
) {
    val recap by viewModel.story.collectAsState()
    var shown by remember { mutableStateOf<Recap?>(null) }
    LaunchedEffect(recap) { if (recap != null) shown = recap }
    val current = shown ?: return
    val colours by viewModel.colours.collectAsState()
    RecapStoryHost(
        recap = current,
        colours = colours,
        closeRequested = recap == null,
        onClose = viewModel::close,
        onGone = { shown = null },
        onPlay = { ids, start ->
            val songs = ids.mapNotNull { id -> current.songs[id]?.toPlayable(localSongs) }
            if (songs.isNotEmpty()) {
                onPlay(songs, start?.let { id -> songs.firstOrNull { it.id == id } } ?: songs.first())
            }
        },
    )
}

private fun RecapSong.toPlayable(localSongs: List<Song>): Song? =
    if (source == SongSource.LOCAL) {
        localSongs.firstOrNull { it.id == id }
    } else {
        Song.fromYouTube(id, title, artist, album, durationMs, thumbnailUrl)
    }

@Composable
private fun RecapStoryHost(
    recap: Recap,
    colours: Map<String, Color>,
    closeRequested: Boolean,
    onClose: () -> Unit,
    onGone: () -> Unit,
    onPlay: (List<String>, String?) -> Unit,
) {
    // Always dark, whatever the app's theme: from the period's top cover once
    // its colour is known, from the app's own accent until then.
    val accent = MaterialTheme.colorScheme.primary
    val seed = recap.topSongs.firstOrNull()?.id?.let { colours[it] } ?: accent
    val scheme: ColorScheme = remember(seed) {
        buildSeedColorScheme(seed, dark = true, style = PaletteStyle.TONAL_SPOT)
    }
    RecapWindowEffect()
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface) {
            StoryBody(recap, colours, closeRequested, onClose, onGone, onPlay)
        }
    }
}

@Composable
private fun StoryBody(
    recap: Recap,
    colours: Map<String, Color>,
    closeRequested: Boolean,
    onClose: () -> Unit,
    onGone: () -> Unit,
    onPlay: (List<String>, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val cards = remember(recap) { cardsFor(recap) }
    var index by remember(recap) { mutableIntStateOf(0) }
    var proofOpen by remember(recap) { mutableStateOf(false) }
    var shareOpen by remember(recap) { mutableStateOf(false) }
    // Read only by the window and the room: a slot that follows a race head
    // changes every frame, and nothing else should recompose for it.
    val slot = remember(recap) { mutableStateOf<WindowSlot?>(null) }
    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val indexState = rememberUpdatedState(index)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        // 0 is open, heightPx is off the bottom edge: one value for the
        // entrance, the swipe down, the fling and the exit.
        val offset = remember { Animatable(heightPx) }
        val backProgress = remember { Animatable(0f) }
        var exiting by remember { mutableStateOf(false) }
        val currentOnGone by rememberUpdatedState(onGone)

        fun dismiss(velocity: Float) {
            if (exiting) return
            exiting = true
            onClose()
            scope.launch {
                try {
                    offset.animateTo(heightPx, appleSpring(0.42f), initialVelocity = velocity.coerceAtLeast(0f))
                } finally {
                    if (isActive) currentOnGone()
                }
            }
        }

        LaunchedEffect(Unit) { offset.animateTo(0f, appleSpring(0.55f)) }
        LaunchedEffect(closeRequested) { if (closeRequested) dismiss(0f) }

        PredictiveBackHandler(enabled = !exiting && !proofOpen && !shareOpen) { events ->
            try {
                events.collect { backProgress.snapTo(it.progress) }
                dismiss(0f)
            } catch (e: CancellationException) {
                scope.launch { backProgress.animateTo(0f, appleSpring(0.35f)) }
                throw e
            }
        }
        androidx.activity.compose.BackHandler(enabled = proofOpen || shareOpen) {
            if (shareOpen) shareOpen = false else proofOpen = false
        }

        fun go(to: Int) {
            val target = to.coerceIn(0, cards.lastIndex)
            if (target != index) {
                haptics.tick()
                proofOpen = false
                index = target
            }
        }

        // The app behind, dimmed as the story covers it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - offset.value / heightPx).coerceIn(0f, 1f) }
                .background(scheme.scrim.copy(alpha = 0.72f))
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offset.value
                    val back = backProgress.value
                    val s = 1f - 0.08f * back
                    scaleX = s
                    scaleY = s
                    val card = max((offset.value / (heightPx * 0.2f)).coerceIn(0f, 1f), back)
                    shape = RoundedCornerShape((28 * card).dp)
                    clip = card > 0f
                }
                .onGloballyPositioned { container = it }
                // One handler for every touch on the story, read after the
                // cards have had theirs (the Final pass): anything a card took
                // - a button, the calendar's scrub, the plays list - is left
                // alone. A tap on the left third goes back, anywhere else on;
                // sideways swipes do the same; down leaves; up on the song card
                // shows the plays behind it.
                .pointerInput(cards) {
                    val tracker = VelocityTracker()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                        if (exiting || shareOpen) return@awaitEachGesture
                        tracker.resetTracking()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var total = Offset.Zero
                        var axis = 0 // 0 undecided, 1 sideways, 2 up or down, -1 a card's
                        var released = false
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (axis == 0 && change.isConsumed) { axis = -1; break }
                            if (!change.pressed) { released = true; break }
                            tracker.addPosition(change.uptimeMillis, change.position)
                            total += change.positionChange()
                            if (axis == 0 && total.getDistance() > slop) {
                                axis = if (abs(total.x) > abs(total.y)) 1 else 2
                            }
                            if (axis == 2 && total.y > 0 && !proofOpen) {
                                // The story follows the finger down; past the top
                                // it resists rather than stopping dead.
                                change.consume()
                                val raw = total.y
                                scope.launch { offset.snapTo(raw) }
                            } else if (axis != 0) {
                                change.consume()
                            }
                        }
                        val velocity = tracker.calculateVelocity()
                        when (axis) {
                            0 -> if (released && !proofOpen) {
                                go(if (down.position.x < size.width * 0.3f) index - 1 else index + 1)
                            }
                            1 -> if (!proofOpen && (abs(total.x) > widthPx * 0.12f || abs(velocity.x) > 600f)) {
                                go(if (total.x < 0) index + 1 else index - 1)
                            }
                            2 -> when {
                                proofOpen -> if (total.y > 0) proofOpen = false
                                total.y < 0 -> {
                                    scope.launch { offset.animateTo(0f, appleSpring(0.42f)) }
                                    if (cards[index] == RecapCardKind.SONG && (-total.y > heightPx * 0.08f || velocity.y < -600f)) {
                                        proofOpen = true
                                    }
                                }
                                else -> {
                                    val landing = offset.value + projectedDistance(velocity.y)
                                    if (landing > heightPx * 0.3f && velocity.y > -300f) {
                                        dismiss(velocity.y)
                                    } else {
                                        scope.launch {
                                            offset.animateTo(
                                                0f,
                                                appleSpring(0.42f, if (abs(velocity.y) > 900f) 0.82f else 1f),
                                                initialVelocity = velocity.y,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            RoomBackdrop(slot, colours, scheme.surfaceContainerLowest)

            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    fadeIn(tween(260, delayMillis = 90)) togetherWith fadeOut(tween(170))
                },
                label = "recapCard",
            ) { shownIndex ->
                val clock = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    clock.animateTo(CARD_CLOCK_SECONDS, tween((CARD_CLOCK_SECONDS * 1000).toInt(), easing = LinearEasing))
                }
                val fallback = scheme.onSurfaceVariant.copy(alpha = 0.55f)
                val storyScope = remember(recap, colours, shownIndex, fallback) {
                    RecapStoryScope(
                        recap = recap,
                        clock = { clock.value },
                        isCurrent = { shownIndex == indexState.value },
                        colours = colours,
                        container = { container },
                        onSlot = { if (shownIndex == indexState.value) slot.value = it },
                        play = onPlay,
                        openProof = { proofOpen = true },
                        openShare = { shareOpen = true },
                        haptics = haptics,
                        fallback = fallback,
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(top = 56.dp)
                ) {
                    RecapCard(cards[shownIndex], storyScope)
                }
            }

            TravellingWindow(slotState = slot, coverOf = { id -> id?.let { recap.songs[it]?.thumbnailUrl } })

            // Where you are, not a timer: nothing moves on by itself.
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f)) {
                    cards.indices.forEach { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        i == index -> scheme.onSurface
                                        i < index -> scheme.onSurface.copy(alpha = 0.6f)
                                        else -> scheme.onSurface.copy(alpha = 0.22f)
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.size(4.dp))
                IconButton(onClick = { dismiss(0f) }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.recap_close))
                }
            }

            val songCard = cards[index] == RecapCardKind.SONG
            AnimatedVisibility(
                visible = proofOpen && songCard,
                enter = androidx.compose.animation.slideInVertically(appleSpring(0.55f)) { it },
                exit = androidx.compose.animation.slideOutVertically(appleSpring(0.42f)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                recap.topSongs.firstOrNull()?.let { top ->
                    ProofSheet(
                        recap = recap,
                        song = top,
                        accent = colours[top.id]?.onRoom() ?: scheme.primary,
                        onPlay = { onPlay(listOf(top.id), top.id) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = shareOpen,
            enter = androidx.compose.animation.slideInVertically(appleSpring(0.55f)) { it } + fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(appleSpring(0.42f)) { it } + fadeOut(),
        ) {
            RecapSharePage(recap = recap, colours = colours, onBack = { shareOpen = false })
        }
    }
}

/** How long a card's clock runs; everything has landed well before. */
private const val CARD_CLOCK_SECONDS = 12f

/**
 * Light system-bar icons while the story is open - it is dark whatever the
 * app's theme - and whatever they were before, back on the way out.
 */
@Composable
private fun RecapWindowEffect() {
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(view, context) {
        val window = (context.findActivity())?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            lightStatus?.let { controller.isAppearanceLightStatusBars = it }
            lightNavigation?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The room: the darkest surface, leaning toward whatever cover the window holds. */
@Composable
private fun RoomBackdrop(slot: androidx.compose.runtime.State<WindowSlot?>, colours: Map<String, Color>, base: Color) {
    val songId by remember(slot) { androidx.compose.runtime.derivedStateOf { slot.value?.songId } }
    val room by animateColorAsState(
        targetValue = roomTone(base, songId?.let { colours[it] }),
        animationSpec = tween(520),
        label = "recapRoom",
    )
    Box(Modifier.fillMaxSize().background(room))
}

/**
 * The one window that travels the story: it springs from slot to slot,
 * changing shape on the way, and dissolves from one cover to the next. Laid
 * out once at a fixed size and moved and scaled in the draw phase, so a frame
 * of travel never re-lays anything.
 */
@Composable
private fun TravellingWindow(slotState: androidx.compose.runtime.State<WindowSlot?>, coverOf: (String?) -> String?) {
    val slot = slotState.value
    val density = LocalDensity.current
    val baseSize = 320.dp
    val basePx = with(density) { baseSize.toPx() }
    val cx = remember { Animatable(0f) }
    val cy = remember { Animatable(0f) }
    val size = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val morph = remember { Animatable(1f) }
    var fromShape by remember { mutableStateOf(RecapShape.COOKIE9) }
    var toShape by remember { mutableStateOf(RecapShape.COOKIE9) }
    var placed by remember { mutableStateOf(false) }

    LaunchedEffect(slot) {
        val s = slot ?: return@LaunchedEffect
        val targetX = s.bounds.center.x
        val targetY = s.bounds.center.y
        val targetSize = s.bounds.width
        if (s.shape != toShape) {
            fromShape = if (morph.value < 0.5f) fromShape else toShape
            toShape = s.shape
            morph.snapTo(0f)
            launch { morph.animateTo(1f, appleSpring(0.5f)) }
        }
        if (!placed || s.follow || !s.visible) {
            cx.snapTo(targetX); cy.snapTo(targetY); size.snapTo(targetSize); turn.snapTo(s.rotation)
            placed = true
        } else {
            val spec = appleSpring<Float>(s.response)
            launch { cx.animateTo(targetX, spec) }
            launch { cy.animateTo(targetY, spec) }
            launch { size.animateTo(targetSize, spec) }
            launch { turn.animateTo(s.rotation, spec) }
        }
        alpha.animateTo(if (s.visible) 1f else 0f, tween(if (s.visible) 220 else 120))
    }

    val cover = coverOf(slot?.songId)
    Box(
        Modifier
            .size(baseSize)
            .graphicsLayer {
                val scale = size.value / basePx
                translationX = cx.value - basePx / 2f
                translationY = cy.value - basePx / 2f
                scaleX = scale
                scaleY = scale
                rotationZ = turn.value
                this.alpha = alpha.value
                shape = MorphShape(morphOf(fromShape, toShape), morph.value.coerceIn(0f, 1f))
                clip = true
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Crossfade(targetState = cover, animationSpec = tween(420), label = "recapCover") { url ->
            if (url != null) {
                AsyncImage(
                    model = googleImageAtSize(url, 544) ?: url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
