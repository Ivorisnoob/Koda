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
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import androidx.core.util.Consumer
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.R

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
    
    // Context and Activity
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    // PiP State
    var isInPipMode by remember { mutableStateOf(false) }

    // Listen for PiP Mode changes
    DisposableEffect(activity) {
        val listener = Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
            // Ensure expanded state is consistent/handled? 
            // Usually if we go to PiP, we might want to ensure UI is ready for return?
        }
        activity?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity?.removeOnPictureInPictureModeChangedListener(listener) }
    }

    if (currentVideo == null) return

    // Keep the screen awake while a video is actually playing (mini, full or
    // fullscreen). Cleared when paused, when the player closes, or in system
    // PiP (the PiP window should not block the lock screen timeout).
    DisposableEffect(isPlaying, isInPipMode) {
        val window = activity?.window
        if (isPlaying && !isInPipMode) {
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
    
    LaunchedEffect(currentVideo, isPlaying, isExpanded) {
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
                .setAspectRatio(Rational(16, 9))
                .setActions(actions)
                
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                 // Only auto-enter PiP while the full player is on screen.
                 // Auto-entering from the mini player captures the whole app UI
                 // into the PiP window instead of just the video surface.
                 paramsBuilder.setAutoEnterEnabled(isExpanded)
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

    // If in System PiP Mode, show purely the player
    if (isInPipMode) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.exoPlayer
                    useController = false
                }
            },
            update = { pv ->
                pv.player = viewModel.exoPlayer
                pv.useController = false
            },
            modifier = Modifier.fillMaxSize()
        )
        return // Return early, don't show overlay UI
    }

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

        // Dragging ~80% of the screen collapses fully; clamped so the full
        // player layout never squashes into an unreadable sliver mid-drag.
        val dragRangePx = fullHeightPx * 0.8f

        // Minimized resting position sits above the nav bar, and above the
        // music mini player too when both players are alive at the same time
        val p = expandProgress.value
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
                         scope.launch {
                             expandProgress.snapTo(
                                 (expandProgress.value - dy / dragRangePx).coerceIn(0.25f, 1f)
                             )
                         }
                     },
                     onMinimizeDragRelease = { velocityY ->
                         // Momentum first: any real downward flick commits, an
                         // upward flick always restores; position only decides
                         // for slow, deliberate drags.
                         val minimize = when {
                             velocityY > 600f -> true
                             velocityY < -600f -> false
                             else -> expandProgress.value < 0.85f
                         }
                         if (minimize) {
                             viewModel.setExpanded(false)
                         } else {
                             scope.launch {
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
