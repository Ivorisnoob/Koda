package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.util.KLog

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.activity.compose.PredictiveBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
@Suppress("DEPRECATION")
fun enterPipMode(activity: androidx.activity.ComponentActivity): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    if (!activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
    ) return false

    return try {
        // VideoPipController has already installed the current aspect ratio,
        // source rectangle and transport actions. Passing a freshly built
        // params object here used to erase those actions at the moment PiP was
        // entered, leaving only the system's basic control.
        activity.enterPictureInPictureMode()
        true
    } catch (e: Exception) {
        KLog.w("VideoPlayerOverlay", "enterPictureInPictureMode refused", e)
        false
    }
}

/**
 * Explicit PiP entry from the player's visible PiP button.
 *
 * Some OxygenOS 12 builds do not carry parameters previously installed with
 * setPictureInPictureParams into the no-argument entry call. Pass the complete
 * snapshot here so the pinned task receives the aspect ratio, source rectangle
 * and all three RemoteActions atomically.
 */
fun enterPipMode(
    activity: androidx.activity.ComponentActivity,
    viewModel: VideoPlayerViewModel
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    if (!activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
    ) return false

    val bounds = (
        if (viewModel.isExpanded.value) {
            viewModel.videoSurfaceBounds.value
        } else {
            viewModel.miniVideoSurfaceBounds.value
        }
    )?.takeIf { !it.isEmpty }

    return try {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(pipAspectRatio(viewModel.videoAspectRatio.value))
            .setActions(
                pipActions(
                    activity = activity,
                    packageName = activity.packageName,
                    isPlaying = viewModel.isPlaying.value
                )
            )
            .apply { bounds?.let(::setSourceRectHint) }
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
        activity.enterPictureInPictureMode(params)
    } catch (e: Exception) {
        KLog.w("VideoPlayerOverlay", "Explicit PiP entry refused", e)
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

        // 0 = mini player, 1 = expanded. Always begin at the collapsed end so
        // opening a brand-new video gets the same reveal as tapping an existing
        // mini player. Initialising this from isExpanded skipped every entrance
        // frame when a video was first opened. Progress never changes layout:
        // the Android video surface is swapped once, then cheap layers animate.
        val expandProgress = remember { Animatable(0f) }

        // A restored collapsed player also deserves an entrance instead of
        // appearing abruptly on the first composed frame. This is read only by
        // the mini bar's graphics layer, keeping its AndroidView out of measure
        // and composition during the animation.
        val miniEntrance = remember { Animatable(0f) }
        val miniEntranceSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        LaunchedEffect(Unit) {
            miniEntrance.animateTo(1f, miniEntranceSpec)
        }

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

        // 1:1 with the finger: progress maps to a compositor translation over
        // one screen height. No player content is remeasured while dragging.
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
        var isMiniDragging by remember { mutableStateOf(false) }
        var isDismissingMini by remember { mutableStateOf(false) }
        val miniSettleOffset = remember { Animatable(0f) }
        val miniExpandThresholdPx = with(density) { 48.dp.toPx() }
        val miniDismissThresholdPx = with(density) { 56.dp.toPx() }
        val miniFlingVelocityPx = with(density) { 700.dp.toPx() }
        // Upward travel is a preview, not a drag: the expand it commits to is
        // a different animation entirely, so letting the bar follow the finger
        // up the screen would promise a movement that never continues.
        val miniLiftLimitPx = with(density) { 24.dp.toPx() }
        val miniEnterOffsetPx = with(density) { 12.dp.toPx() }
        val miniOffsetAnimationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

        // Minimized resting position clears the system navigation inset plus
        // whatever bottom chrome the host says it is drawing, which is nothing
        // on most routes.
        val widthPadding = if (isExpanded) 0.dp else 16.dp
        val bottomPadding = if (isExpanded) {
            0.dp
        } else {
            MINI_VIDEO_MARGIN + bottomInset + hostBottomChrome
        }
        val height = if (isExpanded) fullHeight else MINI_VIDEO_HEIGHT
        val cornerRadius = if (isExpanded) 0.dp else 28.dp
        val miniMotionModifier = if (isExpanded) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                // Only the small mini bar gets a render layer. Promoting and
                // scaling the full-screen PlayerView can overwhelm Android's
                // emulator compositor and some lower-end device GPUs.
                val progress = expandProgress.value
                val gestureOffset = if (isMiniDragging) miniDragY else miniSettleOffset.value
                val visibility = (
                    (1f - progress).coerceIn(0f, 1f) * miniEntrance.value
                ).coerceIn(0f, 1f)
                // The collapsed bar rises a very short distance as it becomes
                // legible. Translation and opacity are compositor-only; the
                // embedded TextureView remains at its final 88dp size.
                translationY = gestureOffset + (1f - visibility) * miniEnterOffsetPx
                alpha = visibility

                val fadeLimit = if (isDismissingMini) 1f else 0.5f
                alpha *= 1f - (
                    gestureOffset.coerceAtLeast(0f) /
                        (miniDismissThresholdPx * 2f)
                    ).coerceIn(0f, fadeLimit)
            }
        }

        // Offset and fade live on a wrapper rather than on the Surface itself.
        // A graphicsLayer on an elevated Surface makes its shadow render
        // against the layer's rectangular bounds instead of the rounded
        // outline, which draws as a hard slab behind the bar - the thing this
        // player is not supposed to look like. Read the live drag state inside
        // this layer block as well: Compose can then update only the layer for
        // each pointer event instead of recomposing and remeasuring the player,
        // including its Android video view. The Animatable is reserved for the
        // release settle; retargeting it for every drag delta made the bar lag
        // behind the finger on slower devices.
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp))
                .then(miniMotionModifier)
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
                    var dragStartOffsetY = 0f
                    var thresholdFeedbackSent = false
                    val velocityTracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            gestureTravelY = 0f
                            dragStartOffsetY = miniSettleOffset.value
                            thresholdFeedbackSent = false
                            velocityTracker.resetTracking()
                            miniDragY = dragStartOffsetY
                            isMiniDragging = true
                            scope.launch { miniSettleOffset.stop() }
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
                                    val releasedOffset = miniDragY
                                    scope.launch {
                                        miniSettleOffset.snapTo(releasedOffset)
                                        isMiniDragging = false
                                        miniSettleOffset.animateTo(
                                            0f,
                                            miniOffsetAnimationSpec
                                        )
                                    }
                                    viewModel.setExpanded(true)
                                }
                                dismiss -> {
                                    if (!thresholdFeedbackSent) {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    }
                                    isDismissingMini = true
                                    val releasedOffset = miniDragY
                                    scope.launch {
                                        miniSettleOffset.snapTo(releasedOffset)
                                        isMiniDragging = false
                                        miniSettleOffset.animateTo(
                                            fullHeightPx,
                                            miniOffsetAnimationSpec
                                        )
                                        viewModel.closePlayer()
                                        isDismissingMini = false
                                        miniDragY = 0f
                                        miniSettleOffset.snapTo(0f)
                                    }
                                }
                                else -> {
                                    val releasedOffset = miniDragY
                                    scope.launch {
                                        miniSettleOffset.snapTo(releasedOffset)
                                        isMiniDragging = false
                                        miniSettleOffset.animateTo(
                                            0f,
                                            miniOffsetAnimationSpec
                                        )
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            val releasedOffset = miniDragY
                            scope.launch {
                                miniSettleOffset.snapTo(releasedOffset)
                                isMiniDragging = false
                                miniSettleOffset.animateTo(
                                    0f,
                                    miniOffsetAnimationSpec
                                )
                            }
                        },
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
                            miniDragY = (dragStartOffsetY + gestureTravelY)
                                .coerceAtLeast(-miniLiftLimitPx)
                        }
                    )
                },
            shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isExpanded) 0.dp else 4.dp,
            shadowElevation = if (isExpanded) 0.dp else 12.dp
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
        if (isExpanded) {
            // Reveal curtain: the full player itself is completely static.
            // A plain color layer lifts toward the top while fading, revealing
            // the destination from the mini player's bottom edge. This gives a
            // clear spatial hand-off without scaling, translating or repeatedly
            // laying out the live video surface.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val progress = if (isDragging) dragProgress else expandProgress.value
                        if (isDragging) {
                            translationY = 0f
                            alpha = (1f - progress) * 0.16f
                        } else {
                            val reveal = progress.coerceIn(0f, 1f)
                            translationY = -fullHeightPx * reveal
                            alpha = 1f - reveal
                        }
                    }
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
        }
    }
}
