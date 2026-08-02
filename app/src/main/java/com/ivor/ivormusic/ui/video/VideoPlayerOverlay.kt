package com.ivor.ivormusic.ui.video

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.R

/**
 * Aspect ratio for the PiP window, from the video's own dimensions.
 *
 * A hardcoded 16:9 letterboxed everything that was not 16:9 - vertical clips
 * worst of all. The system rejects anything outside roughly 1:2.39 to 2.39:1
 * with an IllegalArgumentException, so the video's ratio is clamped into that
 * range rather than passed through blind.
 */
private fun pipAspectRatio(videoAspectRatio: Float?): Rational {
    val ratio = videoAspectRatio?.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val clamped = ratio.coerceIn(MIN_PIP_ASPECT, MAX_PIP_ASPECT)
    // Scaled to integers: Rational(width, height) with 1000ths is precise
    // enough for a window a few hundred pixels wide.
    return Rational((clamped * 1000).toInt(), 1000)
}

private const val MIN_PIP_ASPECT = 1f / 2.39f
private const val MAX_PIP_ASPECT = 2.39f

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
    miniPlayerExtraBottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val isExpanded by viewModel.isExpanded.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    val videoBounds by viewModel.videoSurfaceBounds.collectAsState()

    // Context and Activity
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

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

    // Update PiP Params (Active when video is present)
    val packageName = context.packageName
    val pipPlayAction = "$packageName.PIP_PLAY"
    val pipPauseAction = "$packageName.PIP_PAUSE"
    
    LaunchedEffect(currentVideo, isPlaying, isExpanded, videoAspectRatio, videoBounds) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
             val videoId = currentVideo?.videoId ?: return@LaunchedEffect
             // Use collision-resistant request codes: masked hashcode OR'd with action bit
             val baseCode = videoId.hashCode() and 0x7FFFFFFF
             val reqCodePlay = baseCode or 0x1
             val reqCodePause = baseCode or 0x2
             
             // Intents for PiP controls - using package-scoped actions for security
             val playIntent = PendingIntent.getBroadcast(
                 context, 
                 reqCodePlay, 
                 Intent(pipPlayAction).setPackage(packageName), 
                 PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )
             val pauseIntent = PendingIntent.getBroadcast(
                 context, 
                 reqCodePause, 
                 Intent(pipPauseAction).setPackage(packageName), 
                 PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )
             
             val playAction = RemoteAction(Icon.createWithResource(context, android.R.drawable.ic_media_play), "Play", "Play", playIntent)
             val pauseAction = RemoteAction(Icon.createWithResource(context, android.R.drawable.ic_media_pause), "Pause", "Pause", pauseIntent)
             
             val actions = if (isPlaying) listOf(pauseAction) else listOf(playAction)

             val paramsBuilder = PictureInPictureParams.Builder()
                .setAspectRatio(pipAspectRatio(videoAspectRatio))
                .setActions(actions)

             // Animate the PiP window out of the video rather than out of the
             // whole activity window. Without a source rect the system scales
             // the entire screen down - app chrome, nav bar and all - which is
             // what made the transition look like the UI was being sucked into
             // the window.
             videoBounds?.takeIf { !it.isEmpty }?.let { paramsBuilder.setSourceRectHint(it) }

             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                 // Only auto-enter PiP while the full player is on screen.
                 // Auto-entering from the mini player captures the whole app UI
                 // into the PiP window instead of just the video surface.
                 paramsBuilder.setAutoEnterEnabled(isExpanded)
                 // The content is a video, not a layout that needs to reflow, so
                 // let the system crossfade the resize instead of re-laying out.
                 paramsBuilder.setSeamlessResizeEnabled(true)
             }

             try {
                 activity.setPictureInPictureParams(paramsBuilder.build())
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
    }
    
    // PiP Broadcast Receiver (Handle actions) - using package-scoped actions for security
    DisposableEffect(viewModel) {
        val pipReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    pipPauseAction -> viewModel.exoPlayer?.pause()
                    pipPlayAction -> viewModel.exoPlayer?.play()
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(pipPauseAction)
            addAction(pipPlayAction)
        }
        // Package-scoped actions prevent other apps from triggering on all API levels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(pipReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(pipReceiver, filter)
        }
        onDispose { 
            context.unregisterReceiver(pipReceiver)
        }
    }

    // Nothing here renders in system PiP: MainActivity swaps the whole app for
    // PipVideoSurface, so the PiP window contains the video and nothing else.

    // ------------------------------------------------
    // Normal In-App Overlay UI
    // ------------------------------------------------

    // Handle Back Press to minimize player
    androidx.activity.compose.BackHandler(enabled = isExpanded) {
        viewModel.setExpanded(false)
    }

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
        LaunchedEffect(isExpanded) {
            expandProgress.animateTo(
                if (isExpanded) 1f else 0f,
                spring(stiffness = 300f, dampingRatio = 0.8f)
            )
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

        // 1:1 with the finger: height interpolates over the full screen and the
        // player is bottom-anchored, so a range of one screen height moves the
        // player's top edge exactly as far as the finger travels. The old 0.8
        // range made it run ahead of the touch.
        val dragRangePx = fullHeightPx

        // Minimized resting position sits above the nav bar, and above the
        // music mini player too when both players are alive at the same time
        val p = if (isDragging) dragProgress else expandProgress.value
        val height = lerp(88.dp, fullHeight, p)
        val widthPadding = lerp(16.dp, 0.dp, p)
        val bottomPadding = lerp(100.dp + bottomInset + miniPlayerExtraBottomPadding, 0.dp, p)
        val cornerRadius = lerp(28.dp, 0.dp, p)

        Surface(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp))
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp))
                .clickable(enabled = !isExpanded) { viewModel.setExpanded(true) },
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
                     onMinimizeDragDelta = { dy ->
                         if (!isDragging) {
                             isDragging = true
                             dragProgress = expandProgress.value
                         }
                         // Clamped short of the mini player so the expanded
                         // layout never squashes into an unreadable sliver.
                         dragProgress = (dragProgress - dy / dragRangePx)
                             .coerceIn(0.25f, 1f)
                     },
                     onMinimizeDragRelease = { velocityY ->
                         // Momentum first: any real downward flick commits, an
                         // upward flick always restores; position only decides
                         // for slow, deliberate drags. Both thresholds are
                         // deliberately easy to reach - a flick is how most
                         // people dismiss a player, and the old ones asked for
                         // a long, committed pull before anything happened.
                         val released = dragProgress
                         val minimize = when {
                             velocityY > 350f -> true
                             velocityY < -350f -> false
                             else -> released < 0.9f
                         }
                         scope.launch {
                             // Hand the dragged position to the Animatable
                             // before dropping out of drag mode, so the settle
                             // continues from where the finger let go instead
                             // of snapping back to fully expanded first.
                             expandProgress.snapTo(released)
                             isDragging = false
                             if (minimize) {
                                 viewModel.setExpanded(false)
                             } else {
                                 expandProgress.animateTo(
                                     1f,
                                     spring(stiffness = 300f, dampingRatio = 0.8f)
                                 )
                             }
                         }
                     }
                 )
             } else {
                 // Mini Player Content
                 MiniVideoPlayerContent(
                     viewModel = viewModel,
                     onExpand = { viewModel.setExpanded(true) },
                     onClose = { viewModel.closePlayer() }
                 )
             }
        }
    }
}
