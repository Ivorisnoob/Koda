package com.ivor.ivormusic.ui.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.ui.components.MiniPlayerContent
import com.ivor.ivormusic.ui.components.miniSkipGesture
import com.ivor.ivormusic.ui.components.PLAYER_CONTAINER_SPRING
import com.ivor.ivormusic.ui.components.containerBackdropAlpha
import com.ivor.ivormusic.ui.components.containerFullAlpha
import com.ivor.ivormusic.ui.components.containerMiniAlpha
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How far a back gesture shrinks the expanded player before it is released.
 *
 * Not all the way to the mini pill: a preview that arrives at the destination
 * has stopped being a preview, and there would be nothing left for releasing
 * to do.
 */
private const val PLAYER_BACK_PEEK = 0.72f

/**
 * A container that expands from a MiniPlayer (floating pill) to a Full Screen Player.
 * Uses a single animated progress value to drive all property interpolations for optimal performance.
 * Leverages Material Physics motion scheme for smooth, interruptible animations.
 * 
 * Swipe gestures:
 * - Swipe UP on mini player: Expand to full player
 * - Swipe DOWN on mini player: Dismiss/clear player
 * - Swipe LEFT/RIGHT on mini player: the pill stays put and the song inside it
 *   moves to the next or previous one in the play order (MiniSkipCarousel)
 * - Swipe DOWN on full player: Collapse to mini player
 */
/** The collapsed pill as a bubble: its 52dp artwork slot and the 8dp round it. */
private val MINI_BUBBLE_SIZE = 68.dp

/** The share of the expansion over which a bubble widens back into the pill. */
private const val MINI_BUBBLE_RELEASE = 0.2f

/** A rounded rectangle whose radius is never more than half its own smaller side. */
private class CappedRoundedShape(private val radius: androidx.compose.ui.unit.Dp) :
    androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val capped = minOf(with(density) { radius.toPx() }, size.minDimension / 2f)
        return androidx.compose.ui.graphics.Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height),
                androidx.compose.ui.geometry.CornerRadius(capped)
            )
        )
    }

    override fun equals(other: Any?): Boolean = other is CappedRoundedShape && other.radius == radius
    override fun hashCode(): Int = radius.hashCode()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpandablePlayer(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    playWhenReady: Boolean,
    progress: Float,
    duration: Long,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    viewModel: PlayerViewModel,
    ambientBackground: Boolean = true,
    artworkColors: Boolean = false,
    playerStyle: PlayerStyle = PlayerStyle.EDITORIAL,
    onPlayerStyleChange: (PlayerStyle) -> Unit = {},
    collapsedBottomSpacing: androidx.compose.ui.unit.Dp = 100.dp,
    /**
     * How far to slide the collapsed pill down, in pixels, so it follows the
     * navigation toolbar as that scrolls away. A lambda because it changes on
     * every scroll frame and is read during layout - taking a value here would
     * recompose the whole player instead.
     */
    collapsedFollowOffsetPx: () -> Float = { 0f },
    /**
     * How far the collapsed pill has shrunk into a bubble at its bottom end
     * corner: 0 is the full pill, 1 is a circle holding only the cover. It
     * follows the page's scroll, so like the offset above it is a lambda
     * read in the layout and draw phases, never in composition.
     */
    collapsedBubbleFraction: () -> Float = { 0f },
    /**
     * Where the page the pill floats over begins and ends: a navigation rail
     * on the start edge, and a phone on its side's cutout or system bar. The
     * pill centres between them, and the expanded player still fills the
     * whole window.
     */
    collapsedStartInset: androidx.compose.ui.unit.Dp = 0.dp,
    collapsedEndInset: androidx.compose.ui.unit.Dp = 0.dp,
    /** The widest the collapsed pill may grow; unspecified is the full page. */
    collapsedMaxWidth: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Unspecified,
    onArtistClick: (String) -> Unit = {},
    /** Hand the playing song to the video player; null where there is none. */
    onWatchAsVideo: (() -> Unit)? = null,
    onAlbumClick: (String) -> Unit = {},
    onOpenAlbum: (com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (currentSong == null) return

    // Long-pressing the artwork in any style summons the style wheel; it
    // lives here, above whichever style is active, so the player can morph
    // live underneath it. The controller streams the hold-drag-release
    // gesture from the artwork into the wheel.
    val styleWheel = rememberPlayerStyleWheelController()
    // The session outlives a pause so the artwork freezes on its last frame
    // instead of dropping back to the still cover; the loop's own play state
    // follows isPlaying through LocalMotionArtworkPlaying.
    val motionArtworkSession = rememberMotionArtworkSession(
        song = currentSong,
        active = isExpanded && !styleWheel.isOpen
    )
    // Provided here rather than per style, so every style's progress bar reaches the measured
    // waveform through one wiring instead of eight that can each be forgotten. The scrub
    // interaction travels the same way: each style's transparent Slider still owns the gesture
    // and publishes it here, which is what the visual under it reads to bloom its thumb.
    val playerWaveform = rememberPlayerWaveform(currentSong?.id)
    val scrubInteraction = remember { MutableInteractionSource() }
    // Where a scrub started, shared by the same route: each style's Slider snaps its drag
    // through it and the bar under it draws the marker.
    val scrubReturnPoint = remember { ScrubReturnPoint() }
    // The user's playback rate, for the same reason and by the same route: a bar
    // that interpolates between samples has to know how fast the clock is
    // running. It changes only when somebody moves the speed slider.
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    LaunchedEffect(isExpanded) {
        if (!isExpanded) styleWheel.dismiss()
    }

    // Keep the screen awake while the expanded player is open and playing,
    // mirroring the video overlay's hold. Collapsed playback deliberately
    // holds nothing: audio in the mini pill is meant to survive screen-off.
    //
    // Only the hold this effect took is released: opening the paused player
    // over a playing video mini bar (or vice versa) must not clear the other
    // player's hold, and a cleared queue disposes this without touching it.
    val context = LocalContext.current
    DisposableEffect(isExpanded, isPlaying) {
        val holding = isExpanded && isPlaying
        val window = (context as? android.app.Activity)?.window
        if (holding) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (holding) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Configuration.screenHeightDp/screenWidthDp exclude the status and
    // navigation bars on older Android releases even though enableEdgeToEdge
    // makes our Compose container cover them. A bottom-aligned surface with
    // that shorter height therefore leaves both inset heights exposed at the
    // top on Android 11/12. The window container is the space this overlay
    // actually has to fill.
    //
    // Both axes come from it, not just the height: the width is how far the
    // dismiss gesture throws the collapsed pill, and in landscape (or on a
    // device that keeps its bar on the side) it is short by the same inset.
    // VideoPlayerOverlay solves the same problem with BoxWithConstraints; here
    // the measurement is needed above the layout that would provide it.
    val windowSize = com.ivor.ivormusic.ui.theme.windowDpSize()
    val screenHeight = windowSize.height
    val screenWidth = windowSize.width
    // What the expanded player does with a window that is not a phone held
    // upright: the style as ever, the style beside a companion panel, or one
    // shared landscape layout. See AdaptivePlayer.kt.
    val playerFrame = playerFrameFor(com.ivor.ivormusic.ui.theme.currentWindowLayout())
    val density = LocalDensity.current
    val bottomWindowInsets = WindowInsets.navigationBars
    val bottomInset = with(density) { bottomWindowInsets.getBottom(this).toDp() }
    
    // Single animated progress (0f = collapsed, 1f = expanded), settling on
    // the spring the video player's container transform shares with this one
    // (PlayerContainerTransform.kt). Its overshoot is far below a pixel, so
    // the coerce below is a guard rather than something the eye ever sees
    // working.
    //
    // An Animatable rather than animateFloatAsState so a back gesture can
    // scrub it and an interrupted transition continues from its current value.
    val expandSpec = PLAYER_CONTAINER_SPRING
    val expand = remember { Animatable(if (isExpanded) 1f else 0f) }
    LaunchedEffect(isExpanded) {
        expand.animateTo(if (isExpanded) 1f else 0f, expandSpec)
    }
    val expandProgress = expand.value.coerceIn(0f, 1f)

    /**
     * Back on the expanded player previews the collapse it is going to do.
     *
     * This surface is the easy case, and the reason is [expandProgress]: the
     * player is already one value from mini pill to full screen, with every
     * height, padding, corner and alpha derived from it, so a preview is that
     * value scrubbed rather than a new animation invented alongside it. What
     * the finger reveals is the real destination, because there is no
     * separate "leaving" state to draw.
     *
     * It also replaces eight identical `BackHandler(enabled = true) {
     * onCollapse() }` blocks, one per player style. Back handlers resolve most
     * recent first, so eight children each claiming the gesture made which one
     * answered a question about composition order. One handler on the
     * container is both fewer lines and better defined.
     */
    val playerScope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = isExpanded) { events ->
        try {
            events.collect { event ->
                expand.snapTo(
                    androidx.compose.ui.util.lerp(1f, PLAYER_BACK_PEEK, event.progress.coerceIn(0f, 1f))
                )
            }
            // Committed. LaunchedEffect(isExpanded) carries it the rest of the
            // way down from wherever the finger left it.
            onExpandChange(false)
        } catch (cancelled: CancellationException) {
            // Launched from the player's own scope: the coroutine this runs in
            // is the one being cancelled, so a spring started here would never
            // move and the player would sit shrunken.
            playerScope.launch { expand.animateTo(1f, expandSpec) }
        }
    }


    // Derive all properties from the single progress value
    val collapsedHeight = 80.dp
    val collapsedWidthPadding = 16.dp
    // The pill's resting width and where it starts, inside the page area. A
    // cap only ever narrows it, and the leftover is split either side so the
    // pill centres over the page rather than hugging the rail.
    val collapsedPageWidth = (screenWidth - collapsedStartInset - collapsedEndInset)
        .coerceAtLeast(0.dp)
    val collapsedPillWidth = (collapsedPageWidth - collapsedWidthPadding * 2)
        .let { if (collapsedMaxWidth.isSpecified) it.coerceAtMost(collapsedMaxWidth) else it }
        .coerceAtLeast(0.dp)
    val collapsedStartPadding = collapsedStartInset + (collapsedPageWidth - collapsedPillWidth) / 2
    val collapsedEndPadding = (screenWidth - collapsedStartPadding - collapsedPillWidth)
        .coerceAtLeast(0.dp)
    val collapsedBottomPadding = collapsedBottomSpacing + bottomInset
    // Exactly half the collapsed height: a radius larger than that (the old
    // 50.dp) is illegal for the shape and rendered visibly distorted corners.
    // It also now matches MiniPlayerContent's inner 50% pill, so the ripple
    // and the container clip along the same outline.
    val collapsedCornerRadius = collapsedHeight / 2
    // The bubble is the pill's own artwork slot and the 8dp round it, so the
    // cover the pill was showing is the cover the bubble shows: nothing is
    // swapped, the pill just closes round it.
    val bubbleSizePx = with(density) { MINI_BUBBLE_SIZE.toPx() }
    // Opening the player from the bubble widens it back to the pill over the
    // first part of the expansion, so the container transform starts from the
    // shape it has always started from.
    val bubbleFraction: () -> Float = {
        collapsedBubbleFraction().coerceIn(0f, 1f) *
            (1f - (expandProgress / MINI_BUBBLE_RELEASE).coerceIn(0f, 1f))
    }

    val expandedHeight = screenHeight
    val expandedWidthPadding = 0.dp
    val expandedBottomPadding = 0.dp
    val expandedCornerRadius = 0.dp

    // Interpolated values based on progress
    val height = lerp(collapsedHeight, expandedHeight, expandProgress)
    val startPadding = lerp(collapsedStartPadding, expandedWidthPadding, expandProgress)
    val endPadding = lerp(collapsedEndPadding, expandedWidthPadding, expandProgress)
    val bottomPadding = lerp(collapsedBottomPadding, expandedBottomPadding, expandProgress)
    val cornerRadius = lerp(collapsedCornerRadius, expandedCornerRadius, expandProgress)
        .coerceAtMost(height / 2)

    // Collapsed shows surface, expanded shows transparent - but opaque until
    // the content inside is solid; see containerBackdropAlpha for why.
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
        alpha = containerBackdropAlpha(expandProgress)
    )

    // Swipe Logic for expand/collapse (vertical)
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }
    val verticalSwipeThreshold = -50f
    // Swipe up on the expanded player opens the options sheet. Longer than
    // the collapse swipe, so a slightly upward flick does not open it.
    val optionsSwipeThreshold = with(density) { 80.dp.toPx() }
    val nowPlayingOptionsOpen = remember { mutableStateOf(false) }
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    LaunchedEffect(isExpanded) { if (!isExpanded) nowPlayingOptionsOpen.value = false }
    
    // The collapsed pill's gestures, the same set the video bar answers to:
    // up expands, down dismisses, and sideways moves the song inside the pill
    // while the pill itself stays put. Sideways used to be the dismiss, and the
    // two bars disagreed about it; a sideways swipe on something showing a
    // song reads as "another song", the way it does on the artwork of every
    // expanded style.
    //
    // The downward drag is a plain state while the finger is on the pill and
    // an Animatable only for the release, as the video bar does: retargeting an
    // Animatable on every drag delta lags behind the finger on slower devices.
    var miniDragY by remember { mutableFloatStateOf(0f) }
    var isMiniDraggingY by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    val miniSettleY = remember { Animatable(0f) }
    val miniDismissThresholdPx = with(density) { 56.dp.toPx() }
    val miniFlingVelocityPx = with(density) { 700.dp.toPx() }
    val miniOffsetY: () -> Float = { if (isMiniDraggingY) miniDragY else miniSettleY.value }
    val miniOffsetSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    // The songs either side of this one in the order they will play - the
    // play order, not the queue as added, so a shuffled queue peeks at what
    // actually comes next. A swipe jumps to that exact occurrence rather than
    // sending "previous", which past three seconds restarts the song instead.
    val playOrder by viewModel.playOrderQueue.collectAsState()
    val currentQueueItemId by viewModel.currentQueueItemId.collectAsState()
    val queuePosition = playOrder.indexOfFirst { it.id == currentQueueItemId }
    val previousItem = if (queuePosition > 0) playOrder[queuePosition - 1] else null
    val nextItem = if (queuePosition >= 0) playOrder.getOrNull(queuePosition + 1) else null
    // Keyed on the song the pill draws, not the queue occurrence: the
    // occurrence id can move a moment before the song does, and snapping back
    // on it drew the outgoing song again for a frame after the swipe.
    val miniSkip = com.ivor.ivormusic.ui.components.rememberMiniSkipState(
        contentKey = currentSong.id,
        nextKey = nextItem?.song?.id,
        previousKey = previousItem?.song?.id,
        onNext = { nextItem?.let { viewModel.skipToQueueItem(it.id) } },
        onPrevious = { previousItem?.let { viewModel.skipToQueueItem(it.id) } },
    )

    // Container
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(
                    start = startPadding.coerceAtLeast(0.dp),
                    end = endPadding.coerceAtLeast(0.dp)
                )
                // The follow fades out as the player expands: a fullscreen
                // player has no navigation bar to sit above.
                .offset {
                    IntOffset(
                        0,
                        (collapsedFollowOffsetPx() * (1f - expandProgress) + miniOffsetY()).roundToInt()
                    )
                }
                .graphicsLayer {
                    // Fades with the downward pull, at most halfway until the
                    // release commits, then all the way as it leaves.
                    val fadeLimit = if (isDismissing) 1f else 0.5f
                    alpha = 1f - (miniOffsetY().coerceAtLeast(0f) / (miniDismissThresholdPx * 2f))
                        .coerceIn(0f, fadeLimit)
                }
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp))
                // The pill closes toward its bottom end corner. Sized here,
                // in layout, because the fraction moves on every scroll
                // frame; everything below this line (the gestures, the
                // click, the surface itself) takes the smaller bounds.
                .layout { measurable, constraints ->
                    val fraction = bubbleFraction()
                    val width = androidx.compose.ui.util.lerp(
                        constraints.maxWidth.toFloat(), bubbleSizePx, fraction
                    ).roundToInt().coerceAtMost(constraints.maxWidth)
                    val height = androidx.compose.ui.util.lerp(
                        constraints.maxHeight.toFloat(), bubbleSizePx, fraction
                    ).roundToInt().coerceAtMost(constraints.maxHeight)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.placeRelative(
                            constraints.maxWidth - width,
                            constraints.maxHeight - height
                        )
                    }
                }
                .pointerInput(isExpanded) {
                    if (isExpanded) {
                        // Expanded: Only handle vertical drag for collapse
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragOffset = 0f },
                            onDragEnd = {
                                if (verticalDragOffset > -verticalSwipeThreshold) {
                                    onExpandChange(false)
                                } else if (verticalDragOffset < -optionsSwipeThreshold &&
                                    !nowPlayingOptionsOpen.value
                                ) {
                                    haptics.performHapticFeedback(
                                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureEnd
                                    )
                                    nowPlayingOptionsOpen.value = true
                                }
                                verticalDragOffset = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                verticalDragOffset += dragAmount
                            }
                        )
                    }
                }
                // Collapsed: sideways moves the song inside the pill.
                .miniSkipGesture(miniSkip, enabled = !isExpanded)
                .pointerInput(isExpanded, miniDismissThresholdPx, miniFlingVelocityPx) {
                    if (!isExpanded) {
                        // Collapsed: up expands, down dismisses. Only the
                        // downward pull moves the pill: an expansion is its own
                        // animation, so following the finger up would promise
                        // a movement that never continues.
                        val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        var thresholdFeedbackSent = false
                        val settleHome: () -> Unit = {
                            val released = miniDragY
                            playerScope.launch {
                                miniSettleY.snapTo(released)
                                isMiniDraggingY = false
                                miniSettleY.animateTo(0f, miniOffsetSpec)
                            }
                        }
                        detectVerticalDragGestures(
                            onDragStart = {
                                verticalDragOffset = 0f
                                thresholdFeedbackSent = false
                                velocityTracker.resetTracking()
                                miniDragY = 0f
                                isMiniDraggingY = true
                                playerScope.launch { miniSettleY.stop() }
                            },
                            onDragEnd = {
                                val velocityY = velocityTracker.calculateVelocity().y
                                val travel = verticalDragOffset
                                val dismiss = travel > miniDismissThresholdPx ||
                                    (travel > 0f && velocityY > miniFlingVelocityPx)
                                when {
                                    travel < verticalSwipeThreshold -> {
                                        settleHome()
                                        onExpandChange(true)
                                    }
                                    dismiss -> {
                                        if (!thresholdFeedbackSent) haptics.confirm()
                                        isDismissing = true
                                        val released = miniDragY
                                        playerScope.launch {
                                            miniSettleY.snapTo(released)
                                            isMiniDraggingY = false
                                            // Below the screen's edge from wherever it is.
                                            miniSettleY.animateTo(
                                                with(density) { (collapsedHeight + collapsedBottomPadding).toPx() } +
                                                    miniDismissThresholdPx,
                                                miniOffsetSpec
                                            )
                                            viewModel.clearPlayer()
                                            isDismissing = false
                                            miniDragY = 0f
                                            miniSettleY.snapTo(0f)
                                        }
                                    }
                                    else -> settleHome()
                                }
                                verticalDragOffset = 0f
                            },
                            onDragCancel = {
                                verticalDragOffset = 0f
                                settleHome()
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                verticalDragOffset += dragAmount
                                // Deltas, not pointer positions: the pill moves
                                // under the finger, so positions relative to it
                                // under-report the speed.
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    androidx.compose.ui.geometry.Offset(0f, verticalDragOffset)
                                )
                                miniDragY = verticalDragOffset.coerceAtLeast(0f)
                                // Only the dismiss edge ticks, and it re-arms if
                                // the finger comes back inside it.
                                val crossed = verticalDragOffset >= miniDismissThresholdPx
                                if (crossed && !thresholdFeedbackSent) {
                                    thresholdFeedbackSent = true
                                    haptics.threshold()
                                } else if (!crossed) {
                                    thresholdFeedbackSent = false
                                }
                            }
                        )
                    }
                }
                .clickable(enabled = !isExpanded) { onExpandChange(true) },
            // Capped to half the surface's own size, whatever that is this
            // frame: the same radius is a pill at full width and a circle
            // as a bubble, and is never larger than the shape allows.
            shape = CappedRoundedShape(cornerRadius.coerceAtLeast(0.dp)),
            // No drop shadow: the pill stands off the page by its container
            // tone alone. A shadow under it read as cheap, and under the
            // bubble as a smudge. [judgement October 2026]
            color = containerColor
        ) {
            // Both layers are positioned in a Box sized to the current (animating)
            // Surface height, which the Surface shape clips. The expanded content
            // is given a FIXED full-screen height so it is measured exactly once —
            // the growing Surface merely reveals/clips it instead of forcing the
            // whole now-playing screen (and its ambient shader) to re-lay-out every
            // frame. A single expandProgress value drives the mini/full crossfade.
            Box(modifier = Modifier.fillMaxSize()) {

                // --- Mini layer: fades out over the first part of the expansion ---
                if (expandProgress < 0.999f) {
                    val miniAlpha = containerMiniAlpha(expandProgress)
                    val miniWidthPx = with(density) { collapsedPillWidth.roundToPx() }
                    val miniHeightPx = with(density) { collapsedHeight.roundToPx() }
                    Box(
                        modifier = Modifier
                            // Measured once at its collapsed size, like the
                            // full layer below: re-measuring the artwork and
                            // title against the surface's changing width on
                            // every frame is work for a layer that is fading
                            // or being closed over.
                            //
                            // Placed bottom-centre as a pill, and with its
                            // start on the surface's start as a bubble: the
                            // surface closes from the start side, so the cover
                            // at the row's start rides with that edge and ends
                            // up centred in the circle.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    Constraints.fixed(miniWidthPx, miniHeightPx)
                                )
                                val fraction = bubbleFraction()
                                val x = androidx.compose.ui.util.lerp(
                                    (constraints.maxWidth - miniWidthPx) / 2f, 0f, fraction
                                )
                                val y = androidx.compose.ui.util.lerp(
                                    (constraints.maxHeight - miniHeightPx).toFloat(),
                                    (constraints.maxHeight - miniHeightPx) / 2f,
                                    fraction
                                )
                                layout(constraints.maxWidth, constraints.maxHeight) {
                                    placeable.placeRelative(x.roundToInt(), y.roundToInt())
                                }
                            }
                            .graphicsLayer { alpha = miniAlpha }
                    ) {
                        MiniPlayerContent(
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            playWhenReady = playWhenReady,
                            progress = progress,
                            onPlayPauseClick = onPlayPauseClick,
                            onNextClick = onNextClick,
                            onClick = { onExpandChange(true) },
                            skipState = miniSkip,
                            previousSong = previousItem?.song,
                            nextSong = nextItem?.song,
                            detailAlpha = { 1f - bubbleFraction() }
                        )
                    }
                }

                // --- Full layer: fixed height, fades in over the latter part ---
                if (isExpanded || expandProgress > 0.001f) {
                    // Solid by the halfway point rather than only at the very
                    // end, so the second half of the motion is one opaque
                    // screen growing instead of a washed-out one.
                    val fullAlpha = containerFullAlpha(expandProgress)
                    // Optionally re-theme the expanded player's accent roles
                    // from the album cover; every style's buttons read
                    // MaterialTheme.colorScheme, so one wrapper covers them
                    // all. The mini player stays on the app theme.
                    // Local songs carry albumArtUri, YouTube songs only a
                    // thumbnailUrl (Palette samples at 128px, so the normal
                    // -res thumbnail is plenty and never 404s like maxres)
                    val playerColorScheme = rememberArtworkColorScheme(
                        enabled = artworkColors,
                        albumArtUri = currentSong.albumArtUri?.toString()
                            ?: currentSong.thumbnailUrl,
                        base = MaterialTheme.colorScheme
                    )
                    MaterialTheme(colorScheme = playerColorScheme) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            // Escape the animated parent's constraints in both axes.
                            // height() alone is still clamped to the pill every frame.
                            .wrapContentSize(Alignment.TopCenter, unbounded = true)
                            .requiredSize(screenWidth, expandedHeight)
                            // Alpha only. The content is already rising with
                            // the container's top edge, so a second 24dp lift
                            // of its own put two different speeds on one
                            // object and read as a slip.
                            .graphicsLayer { alpha = fullAlpha }
                    ) {
                        // The live player blurs beneath the style wheel.
                        val wheelBlur by animateDpAsState(
                            targetValue = if (styleWheel.isOpen) 24.dp else 0.dp,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "StyleWheelBlur"
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (wheelBlur > 0.5.dp) Modifier.blur(wheelBlur) else Modifier)
                        ) {
                        // Any artwork can host the wheel's hold gesture
                        // through this local, without per-style plumbing.
                        CompositionLocalProvider(
                            LocalPlayerStyleWheelController provides styleWheel
                        ) {
                        if (playerFrame.kind == PlayerFrameKind.LANDSCAPE) {
                            // Every style shares this one layout on its side,
                            // so there is no style swap to crossfade. The
                            // locals are the ones each style receives below.
                            CompositionLocalProvider(
                                LocalMotionArtwork provides motionArtworkSession,
                                LocalMotionArtworkPlaying provides isPlaying,
                                LocalPlayerWaveform provides playerWaveform,
                                LocalPlayerScrubInteraction provides scrubInteraction,
                                LocalScrubReturnPoint provides scrubReturnPoint,
                                LocalPlaybackSpeed provides playbackSpeed,
                                LocalNowPlayingOptionsOpen provides nowPlayingOptionsOpen,
                            ) {
                                LandscapeNowPlaying(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = { viewModel.loadMoreRecommendations() },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                        } else
                        Row(modifier = Modifier.fillMaxSize()) {
                        // FULL: the style is the window. STAGE: the style keeps
                        // a phone-shaped column and the companion panel takes
                        // the rest of the width.
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .then(
                                    if (playerFrame.kind == PlayerFrameKind.STAGE) {
                                        Modifier.width(playerFrame.stageWidth)
                                    } else {
                                        Modifier.weight(1f)
                                    }
                                )
                        ) {
                        // Crossfade makes a live style swap (from the style
                        // wheel or Settings) a soft morph instead of a cut.
                        Crossfade(
                            targetState = playerStyle,
                            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                            label = "PlayerStyleSwap"
                        ) { activeStyle ->
                        CompositionLocalProvider(
                            LocalMotionArtwork provides motionArtworkSession.takeIf { activeStyle == playerStyle },
                            LocalMotionArtworkPlaying provides isPlaying,
                            LocalPlayerWaveform provides playerWaveform,
                            LocalPlayerScrubInteraction provides scrubInteraction,
                            LocalScrubReturnPoint provides scrubReturnPoint,
                            // The rate every style's bar extrapolates at between
                            // the service's once-a-second samples.
                            LocalPlaybackSpeed provides playbackSpeed,
                            LocalNowPlayingOptionsOpen provides nowPlayingOptionsOpen,
                        ) {
                        when (activeStyle) {
                            PlayerStyle.CLASSIC -> {
                                PlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.GESTURE -> {
                                GesturePlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.EDITORIAL -> {
                                EditorialPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.POSTER -> {
                                PosterPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.BENTO -> {
                                BentoPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.STICKER -> {
                                StickerPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.MORPH -> {
                                MorphPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.DIAL -> {
                                DialPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                            PlayerStyle.HERO -> {
                                HeroPlayerSheetContent(
                                    viewModel = viewModel,
                                    ambientBackground = ambientBackground,
                                    onCollapse = { onExpandChange(false) },
                                    onLoadMore = {
                                        viewModel.loadMoreRecommendations()
                                    },
                                    onArtistClick = onArtistClick,
                                    onWatchAsVideo = onWatchAsVideo,
                                    onAlbumClick = onAlbumClick,
                                    onOpenAlbum = onOpenAlbum
                                )
                            }
                        }
                        }
                        }
                        }
                        if (playerFrame.kind == PlayerFrameKind.STAGE) {
                            PlayerCompanionPanel(
                                viewModel = viewModel,
                                onLoadMore = { viewModel.loadMoreRecommendations() },
                                ambientBackground = ambientBackground,
                                // The stage already keeps the start edge clear.
                                insets = WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Vertical + WindowInsetsSides.End
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        }
                        }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = styleWheel.isOpen,
                            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                        ) {
                            PlayerStyleWheel(
                                currentStyle = playerStyle,
                                controller = styleWheel,
                                onStyleSelected = onPlayerStyleChange,
                                onDismiss = { styleWheel.dismiss() }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

}
