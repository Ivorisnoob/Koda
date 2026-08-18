package com.ivor.ivormusic.ui.video

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi

/**
 * Aspect ratio for the PiP window, from the video's own dimensions.
 *
 * A hardcoded 16:9 letterboxed everything that was not 16:9 - vertical clips
 * worst of all. The system rejects anything outside roughly 1:2.39 to 2.39:1
 * with an IllegalArgumentException, so the video's ratio is clamped into that
 * range rather than passed through blind.
 */
internal fun pipAspectRatio(videoAspectRatio: Float?): Rational {
    val ratio = videoAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val clamped = ratio.coerceIn(MIN_PIP_ASPECT, MAX_PIP_ASPECT)
    // Scaled to integers: Rational(width, height) with 1000ths is precise
    // enough for a window a few hundred pixels wide.
    return Rational((clamped * 1000).toInt(), 1000)
}

private const val MIN_PIP_ASPECT = 1f / 2.39f
private const val MAX_PIP_ASPECT = 2.39f

/**
 * Settle for the expand/minimize transition, shared by the state-driven
 * animation and the release of a drag so both come to rest the same way.
 *
 * Softer and closer to critically damped than the house bouncy default: this
 * one carries the whole player across the screen, where a visible overshoot
 * reads as a snap rather than as character.
 */
private val MINIMIZE_SETTLE_SPRING = spring<Float>(
    dampingRatio = 0.9f,
    stiffness = 260f
)

/**
 * How far a back gesture shrinks the expanded video player before release.
 *
 * Shallower than the music player's peek, because this one is usually showing
 * moving video: the same amount of travel reads as much larger when the thing
 * being resized is playing.
 */
private const val VIDEO_BACK_PEEK = 0.82f

/**
 * Put the app into system Picture-in-Picture now.
 *
 * Needed as an explicit call because [PictureInPictureParams.Builder.setAutoEnterEnabled]
 * only exists from API 31, and minSdk here is 30 - without this, PiP was simply
 * unreachable on Android 11. It also backs the player's PiP button, which is
 * the only discoverable way in on any version.
 *
 * Returns false when the system refuses (PiP disabled for the app, or the
 * device does not support it), so callers can fall back to doing nothing
 * rather than assuming they are now in PiP.
 */
fun enterPipMode(
    activity: androidx.activity.ComponentActivity,
    videoAspectRatio: Float?,
    videoBounds: android.graphics.Rect?
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    if (!activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
    ) return false

    return try {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspectRatio(videoAspectRatio))
            .apply { videoBounds?.takeIf { !it.isEmpty }?.let { setSourceRectHint(it) } }
            .build()
        activity.enterPictureInPictureMode(params)
    } catch (e: Exception) {
        android.util.Log.w("VideoPlayerOverlay", "enterPictureInPictureMode refused", e)
        false
    }
}

/**
 * Overlay component for persistent video playback across the app.
 * Handles both In-App Mini Player and System Picture-in-Picture.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerOverlay(
    viewModel: VideoPlayerViewModel,
    timedCommentsEnabled: Boolean = false,
    /**
     * How much bottom chrome the host is drawing under this player right now -
     * the floating nav bar, and the music pill above it when both are alive.
     *
     * Passed in rather than assumed, because this overlay is drawn above the
     * NavHost and therefore renders on every route, while the nav bar and the
     * music pill are both inside `HomeScreen` and exist on one. Reserving a
     * fixed height for them left the bar hovering in empty space over Settings,
     * Downloads, a channel page and anywhere else, which is what "the mini
     * player floats in a weird place" was. The system navigation inset is not
     * included here; this player adds that itself.
     */
    hostBottomChrome: androidx.compose.ui.unit.Dp = 0.dp,
    /**
     * Open the playing video's creator. Handled by the host rather than here,
     * because the channel page is a NavHost destination and this overlay is
     * drawn above the NavHost - the host is the only layer that can both
     * navigate and drop this player to its mini bar on the way.
     */
    onOpenChannel: (String) -> Unit = {}
) {
    val isExpanded by viewModel.isExpanded.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Context and Activity
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val haptics = LocalHapticFeedback.current

    if (currentVideo == null) return

    // Suspend video decoding whenever the app stops being visible. ON_STOP is
    // the right signal: entering PiP only pauses the activity (the PiP window
    // is still visible), so PiP playback is untouched, while home / recents /
    // screen off / another app all stop it - exactly the cases where the
    // player's Surface is destroyed underneath a decoding MediaCodec.
    DisposableEffect(activity, viewModel) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onEnterBackground()
                Lifecycle.Event.ON_START -> viewModel.onEnterForeground()
                else -> Unit
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Keep the screen awake while a video is actually playing (mini, full or
    // fullscreen). Cleared when paused and when the player closes. In system
    // PiP this whole overlay leaves the composition, so onDispose clears the
    // flag there too - a PiP window must not hold off the lock screen.
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Nothing here renders in system PiP: MainActivity swaps the whole app for
    // PipVideoSurface, so the PiP window contains the video and nothing else.
    // The PiP window's own shape and controls are driven by VideoPipController,
    // which MainActivity composes above that swap - see the note there.

    // ------------------------------------------------
    // Normal In-App Overlay UI
    // ------------------------------------------------

    val density = LocalDensity.current
    val bottomWindowInsets = WindowInsets.navigationBars
    val bottomInset = with(density) { bottomWindowInsets.getBottom(this).toDp() }

    // Container. BoxWithConstraints so the expanded height matches the real
    // window height — Configuration.screenHeightDp excludes system bar areas,
    // which left a background strip visible at the top in edge-to-edge
    // fullscreen landscape.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val fullHeight = this.maxHeight
        val fullHeightPx = with(density) { fullHeight.toPx() }
        val scope = rememberCoroutineScope()

        // 0 = mini player, 1 = expanded. One progress drives every dimension,
        // so the swipe-down gesture on the video surface can drag the whole
        // player and the settle animation continues from wherever the finger
        // let go instead of jumping.
        val expandProgress = remember { Animatable(if (isExpanded) 1f else 0f) }

        // Live value while a finger is down. The drag deliberately does not go
        // through the Animatable: Animatable.snapTo runs under a MutatorMutex,
        // so each call cancels the one before it, and dispatching one per
        // pointer event meant a fast drag had most of its deltas cancelled
        // before they applied. The player fell behind the finger, which is why
        // minimizing used to need a pull most of the way down the screen.
        // Accumulating in plain snapshot state applies every delta.
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(1f) }
        // Where the finger picked the player up, so the commit test can measure
        // real travel instead of an absolute position on the screen.
        var dragStartProgress by remember { mutableFloatStateOf(1f) }

        // Declared after isDragging so it can clear it. Once expanded/collapsed
        // has actually changed, any drag is over by definition, and this is the
        // backstop that guarantees the flag cannot survive into the next one -
        // a latched isDragging freezes the player at the last dragged progress
        // and leaves every later swipe measuring travel from a start point that
        // never moves, which is what made minimizing work once per app launch.
        LaunchedEffect(isExpanded) {
            isDragging = false
            expandProgress.animateTo(
                if (isExpanded) 1f else 0f,
                MINIMIZE_SETTLE_SPRING
            )
        }

        // 1:1 with the finger: height interpolates over the full screen and the
        // player is bottom-anchored, so a range of one screen height moves the
        // player's top edge exactly as far as the finger travels. The old 0.8
        // range made it run ahead of the touch.
        val dragRangePx = fullHeightPx

        // A short, unhurried pull is all it takes: ~40dp of travel commits the
        // minimize, which is a little more than the touch slop the gesture
        // spends arming itself. Expressed in dp so the feel is the same on
        // every screen density - as a fraction of screen height it silently
        // asked for a much longer pull on tall devices.
        val minimizeTravel = with(density) { 40.dp.toPx() } / dragRangePx

        /**
         * Back previews the minimize instead of performing it.
         *
         * It rides the same channel the swipe-down gesture uses rather than a
         * parallel one: plain snapshot state while the finger is down (the
         * Animatable's MutatorMutex cancels each snapTo with the next, which
         * is why the drag above avoids it too), and the same release path
         * afterwards, so the two ways of dismissing this player cannot drift
         * apart. Which also means back inherits the fix in that `finally` for
         * free - a latched isDragging leaves the player frozen at the last
         * dragged position, and it used to take a process restart to clear.
         */
        PredictiveBackHandler(enabled = isExpanded) { events ->
            try {
                events.collect { event ->
                    isDragging = true
                    dragProgress = androidx.compose.ui.util.lerp(
                        1f,
                        VIDEO_BACK_PEEK,
                        event.progress.coerceIn(0f, 1f)
                    )
                }
                val released = dragProgress
                scope.launch {
                    try {
                        expandProgress.snapTo(released)
                    } finally {
                        isDragging = false
                    }
                    viewModel.setExpanded(false)
                }
            } catch (cancelled: CancellationException) {
                // Launched from the overlay's scope, not this one: this
                // coroutine is the one being cancelled, and a spring started
                // inside it never runs - leaving the player parked at the
                // peeked size with no way back short of another gesture.
                val released = dragProgress
                scope.launch {
                    try {
                        expandProgress.snapTo(released)
                    } finally {
                        isDragging = false
                    }
                    expandProgress.animateTo(1f, MINIMIZE_SETTLE_SPRING)
                }
            }
        }

        // Collapsed-bar gestures: up expands, down dismisses.
        //
        // The axis differs from the music pill on purpose:
        // this bar sits directly above the music pill when both are alive, and
        // a sideways throw there would be ambiguous about which it meant.
        //
        // The bar carries no close button, so this gesture is the only way to
        // dismiss it. It is the same downward pull that already dismisses the
        // expanded player, one size down.
        var miniDragY by remember { mutableFloatStateOf(0f) }
        var isDismissingMini by remember { mutableStateOf(false) }
        val miniExpandThresholdPx = with(density) { 48.dp.toPx() }
        val miniDismissThresholdPx = with(density) { 56.dp.toPx() }
        val miniFlingVelocityPx = with(density) { 700.dp.toPx() }
        // Upward travel is a preview, not a drag: the expand it commits to is
        // a different animation entirely, so letting the bar follow the finger
        // up the screen would promise a movement that never continues.
        val miniLiftLimitPx = with(density) { 24.dp.toPx() }

        val miniOffsetY by animateFloatAsState(
            targetValue = when {
                isExpanded -> 0f
                isDismissingMini -> fullHeightPx
                else -> miniDragY
            },
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            finishedListener = {
                if (isDismissingMini) {
                    viewModel.closePlayer()
                    isDismissingMini = false
                    miniDragY = 0f
                }
            },
            label = "miniVideoOffset"
        )
        // Fades as it is pushed down, so the dismiss is legible before it
        // commits. Capped at half so a bar dragged and released still reads as
        // present on the way back.
        val miniAlpha = if (isDismissingMini) {
            0f
        } else {
            1f - (miniOffsetY.coerceAtLeast(0f) / (miniDismissThresholdPx * 2f)).coerceIn(0f, 0.5f)
        }

        // Minimized resting position clears the system navigation inset plus
        // whatever bottom chrome the host says it is drawing, which is nothing
        // on most routes.
        val p = if (isDragging) dragProgress else expandProgress.value
        val height = lerp(MINI_VIDEO_HEIGHT, fullHeight, p)
        val widthPadding = lerp(16.dp, 0.dp, p)
        val bottomPadding = lerp(
            MINI_VIDEO_MARGIN + bottomInset + hostBottomChrome,
            0.dp,
            p
        )
        val cornerRadius = lerp(28.dp, 0.dp, p)

        // Offset and fade live on a wrapper rather than on the Surface itself.
        // A graphicsLayer on an elevated Surface makes its shadow render
        // against the layer's rectangular bounds instead of the rounded
        // outline, which draws as a hard slab behind the bar - the thing this
        // player is not supposed to look like.
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp))
                .offset { IntOffset(0, miniOffsetY.roundToInt()) }
                .graphicsLayer { alpha = miniAlpha }
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp))
        ) {
        Surface(
            // Surface's own onClick rather than a .clickable in the chain
            // outside it: the ripple is clipped by the component's shape, so
            // it follows the 28dp rounding instead of spilling into the square
            // corners the modifier version rippled into.
            onClick = { viewModel.setExpanded(true) },
            enabled = !isExpanded,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    isExpanded,
                    miniExpandThresholdPx,
                    miniDismissThresholdPx,
                    miniFlingVelocityPx
                ) {
                    if (isExpanded) return@pointerInput
                    var gestureTravelY = 0f
                    var thresholdFeedbackSent = false
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            gestureTravelY = 0f
                            thresholdFeedbackSent = false
                            velocityTracker.resetTracking()
                            miniDragY = 0f
                        },
                        onDragEnd = {
                            val velocityY = velocityTracker.calculateVelocity().y
                            val expand = gestureTravelY < -miniExpandThresholdPx ||
                                (gestureTravelY < 0f && velocityY < -miniFlingVelocityPx)
                            val dismiss = gestureTravelY > miniDismissThresholdPx ||
                                (gestureTravelY > 0f && velocityY > miniFlingVelocityPx)
                            when {
                                expand -> {
                                    if (!thresholdFeedbackSent) {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                                    miniDragY = 0f
                                    viewModel.setExpanded(true)
                                }
                                dismiss -> {
                                    if (!thresholdFeedbackSent) {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                                    isDismissingMini = true
                                }
                                else -> miniDragY = 0f
                            }
                        },
                        onDragCancel = { miniDragY = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            gestureTravelY += dragAmount
                            // Track a synthetic position made from the deltas.
                            // The bar itself follows downward pulls, so pointer
                            // coordinates relative to it under-report velocity.
                            velocityTracker.addPosition(
                                change.uptimeMillis,
                                Offset(0f, gestureTravelY)
                            )

                            val crossedThreshold =
                                gestureTravelY <= -miniExpandThresholdPx ||
                                    gestureTravelY >= miniDismissThresholdPx
                            if (crossedThreshold && !thresholdFeedbackSent) {
                                thresholdFeedbackSent = true
                                haptics.performHapticFeedback(
                                    HapticFeedbackType.GestureThresholdActivate
                                )
                            } else if (!crossedThreshold) {
                                // Re-arm after the finger returns inside the
                                // commit zone, matching the visual snap-back.
                                thresholdFeedbackSent = false
                            }

                            change.consume()
                            // Keep the full travel for deciding the gesture,
                            // while limiting only the visual upward preview.
                            // Previously the same value was clamped to 24dp
                            // and then compared with a 48dp expand threshold,
                            // making distance-based swipe-up impossible.
                            miniDragY = gestureTravelY.coerceAtLeast(-miniLiftLimitPx)
                        }
                    )
                },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = lerp(4.dp, 0.dp, p),
            shadowElevation = lerp(12.dp, 0.dp, p)
        ) {
             if (isExpanded) {
                 // Full Screen Content
                 VideoPlayerContent(
                     viewModel = viewModel,
                     onBackClick = {
                         viewModel.setExpanded(false)
                     },
                     timedCommentsFeatureEnabled = timedCommentsEnabled,
                     onOpenChannel = onOpenChannel,
                     onMinimizeDragDelta = { dy ->
                         if (!isDragging) {
                             isDragging = true
                             dragProgress = expandProgress.value
                             dragStartProgress = expandProgress.value
                         }
                         // Clamped short of the mini player so the expanded
                         // layout never squashes into an unreadable sliver.
                         dragProgress = (dragProgress - dy / dragRangePx)
                             .coerceIn(0.25f, 1f)
                     },
                     onMinimizeDragRelease = { velocityY ->
                         // Momentum first: any downward drift commits, an
                         // upward one always restores; travel only decides for
                         // drags that end almost still. Both thresholds are
                         // intentionally gentle - the gesture should answer a
                         // calm nudge, not ask to be pulled or flicked hard.
                         val released = dragProgress
                         val minimize = when {
                             velocityY > 120f -> true
                             velocityY < -120f -> false
                             else -> dragStartProgress - released > minimizeTravel
                         }
                         scope.launch {
                             try {
                                 // Hand the dragged position to the Animatable
                                 // before dropping out of drag mode, so the
                                 // settle continues from where the finger let
                                 // go instead of snapping back to fully
                                 // expanded first.
                                 expandProgress.snapTo(released)
                             } finally {
                                 // In a finally because snapTo suspends on the
                                 // Animatable's mutex: an expand/collapse
                                 // animation that takes the mutex first
                                 // cancels it, and the plain sequential version
                                 // then never reached this line. The flag stuck
                                 // on, and the gesture was dead until the
                                 // process restarted.
                                 isDragging = false
                             }
                             if (minimize) {
                                 viewModel.setExpanded(false)
                             } else {
                                 expandProgress.animateTo(1f, MINIMIZE_SETTLE_SPRING)
                             }
                         }
                     }
                 )
             } else {
                 // Mini Player Content. Tap and both drags are handled by the
                 // Surface above, so the bar itself only draws and offers its
                 // two controls.
                 MiniVideoPlayerContent(viewModel = viewModel)
             }
        }
        }
    }
}
