package com.ivor.ivormusic.ui.video

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    viewModel: VideoPlayerViewModel
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

    // Update PiP Params (Active when video is present)
    val packageName = context.packageName
    val pipPlayAction = "$packageName.PIP_PLAY"
    val pipPauseAction = "$packageName.PIP_PAUSE"
    
    LaunchedEffect(currentVideo, isPlaying) {
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
                 paramsBuilder.setAutoEnterEnabled(true)
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

    val transition = updateTransition(isExpanded, label = "VideoExpand")
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    val density = LocalDensity.current
    val bottomWindowInsets = WindowInsets.navigationBars
    val bottomInset = with(density) { bottomWindowInsets.getBottom(this).toDp() }
    
    // Animate dimensions
    val height by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "height"
    ) { expanded ->
        if (expanded) screenHeight else 88.dp 
    }
    
    val widthPadding by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "widthPadding"
    ) { expanded ->
        if (expanded) 0.dp else 16.dp
    }

    // Position above nav bar when minimized
    val bottomPadding by transition.animateDp(
        transitionSpec = { spring(stiffness = 300f, dampingRatio = 0.8f) },
        label = "bottomPadding"
    ) { expanded ->
        if (expanded) 0.dp else (100.dp + bottomInset)
    }

    // Container
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(bottom = bottomPadding.coerceAtLeast(0.dp))
                .padding(horizontal = widthPadding.coerceAtLeast(0.dp))
                .fillMaxWidth()
                .height(height.coerceAtLeast(0.dp))
                .clickable(enabled = !isExpanded) { viewModel.setExpanded(true) },
            shape = RoundedCornerShape(if (isExpanded) 0.dp else 28.dp), // Expressive Large Shape
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
