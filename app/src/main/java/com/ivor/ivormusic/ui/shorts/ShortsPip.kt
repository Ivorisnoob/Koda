package com.ivor.ivormusic.ui.shorts

import com.ivor.ivormusic.util.KLog

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ivor.ivormusic.R

/**
 * Picture-in-picture for the Shorts player.
 *
 * The video player's [com.ivor.ivormusic.ui.video.VideoPipController] owns the
 * Activity's PiP params whenever Shorts is closed. While Shorts is open it
 * stands down and this controller publishes instead: a portrait window, and
 * previous / play-pause / next buttons, since a Short is too short for the
 * 10-second skips to be useful.
 *
 * Like the video controller, this is composed above MainActivity's
 * `if (isInPipMode) return`, so its action receiver stays registered while the
 * Shorts overlay is out of the composition behind the PiP window.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShortsPipController(viewModel: ShortsPlayerViewModel) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity ?: return
    if (!supportsShortsPip(activity)) return

    val isActive by viewModel.isActive.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val packageName = context.packageName

    DisposableEffect(context, viewModel, packageName) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    "$packageName.$ACTION_PLAY" -> viewModel.play()
                    "$packageName.$ACTION_PAUSE" -> viewModel.pause()
                    "$packageName.$ACTION_NEXT" -> viewModel.playNext()
                    "$packageName.$ACTION_PREVIOUS" -> viewModel.playPrevious()
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("$packageName.$ACTION_PLAY")
            addAction("$packageName.$ACTION_PAUSE")
            addAction("$packageName.$ACTION_NEXT")
            addAction("$packageName.$ACTION_PREVIOUS")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // The Shorts overlay pauses on ON_STOP, but it is not composed while in
    // PiP. Closing the PiP window stops the Activity, and without this the
    // Short would carry on playing with no window to show it.
    DisposableEffect(activity, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (!viewModel.isActive.value) return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.onEnterBackground()
                androidx.lifecycle.Lifecycle.Event.ON_START -> viewModel.onEnterForeground()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    val hasPrevious = currentIndex > 0
    LaunchedEffect(isActive, currentVideo?.videoId, isPlaying, hasPrevious) {
        // Closed: leave the params to the video player's controller, which
        // republishes (or disarms) as soon as Shorts reports inactive.
        if (!isActive) return@LaunchedEffect
        try {
            activity.setPictureInPictureParams(
                shortsPipParams(
                    activity = activity,
                    isPlaying = isPlaying,
                    hasPrevious = hasPrevious,
                    autoEnter = currentVideo != null
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "setPictureInPictureParams refused", e)
        }
    }
}

/** What the app draws in PiP while Shorts is open: the Short, and nothing else. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ShortsPipSurface(viewModel: ShortsPlayerViewModel, modifier: Modifier = Modifier) {
    val exoPlayer = viewModel.exoPlayer ?: return
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
            // The overlay's PlayerView renders the same ExoPlayer; hand the
            // surface back before this view goes away.
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun supportsShortsPip(activity: androidx.activity.ComponentActivity): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

/**
 * Enter PiP from the Shorts player's button. Passes the full params snapshot,
 * for the same reason the video player does: some OxygenOS 12 builds drop the
 * actions installed earlier when entering through the no-argument call.
 */
fun enterShortsPip(
    activity: androidx.activity.ComponentActivity,
    viewModel: ShortsPlayerViewModel
): Boolean {
    if (!supportsShortsPip(activity)) return false
    return try {
        val index = viewModel.currentIndex.value
        activity.enterPictureInPictureMode(
            shortsPipParams(
                activity = activity,
                isPlaying = viewModel.isPlaying.value,
                hasPrevious = index > 0,
                autoEnter = viewModel.currentVideo.value != null
            )
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        KLog.w(TAG, "Shorts PiP entry refused", e)
        false
    }
}

private fun shortsPipParams(
    activity: androidx.activity.ComponentActivity,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    autoEnter: Boolean
): PictureInPictureParams {
    val playPause = if (isPlaying) {
        remoteAction(activity, ACTION_PAUSE, R.drawable.ic_media_pause, activity.getString(R.string.cd_pause))
    } else {
        remoteAction(activity, ACTION_PLAY, R.drawable.ic_media_play, activity.getString(R.string.cd_play))
    }
    val actions = listOf(
        remoteAction(activity, ACTION_PREVIOUS, R.drawable.ic_media_previous, activity.getString(R.string.pip_previous))
            .apply { isEnabled = hasPrevious },
        playPause,
        // Enabled at the end of the loaded feed too: playNext() asks for the
        // next page there, and the next tap lands once it has arrived.
        remoteAction(activity, ACTION_NEXT, R.drawable.ic_media_next, activity.getString(R.string.pip_next))
            .apply { isEnabled = true }
    )
    return PictureInPictureParams.Builder()
        .setAspectRatio(SHORTS_PIP_ASPECT)
        .setActions(actions)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setAutoEnterEnabled(autoEnter)
                setSeamlessResizeEnabled(true)
            }
        }
        .build()
}

private fun remoteAction(
    activity: androidx.activity.ComponentActivity,
    action: String,
    iconRes: Int,
    label: String
): RemoteAction {
    val packageName = activity.packageName
    val intent = PendingIntent.getBroadcast(
        activity,
        action.hashCode(),
        Intent("$packageName.$action").setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return RemoteAction(Icon.createWithResource(activity, iconRes), label, label, intent)
}

/** Shorts are 9:16; PiP accepts anything from 1:2.39 to 2.39:1. */
private val SHORTS_PIP_ASPECT = Rational(9, 16)

private const val TAG = "ShortsPip"
private const val ACTION_PLAY = "SHORTS_PIP_PLAY"
private const val ACTION_PAUSE = "SHORTS_PIP_PAUSE"
private const val ACTION_NEXT = "SHORTS_PIP_NEXT"
private const val ACTION_PREVIOUS = "SHORTS_PIP_PREVIOUS"
