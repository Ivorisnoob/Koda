package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.util.KLog

import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.R

/**
 * Everything that keeps the system Picture-in-Picture window in sync with the
 * video player: its shape, its transport controls, and the receiver those
 * controls fire into.
 *
 * This has to be composed **above** MainActivity's `if (isInPipMode) return`,
 * and that is the whole reason it is its own composable rather than a block
 * inside [VideoPlayerOverlay]. The overlay is part of the app UI that PiP
 * replaces, so entering PiP tore it out of the composition, which disposed the
 * broadcast receiver and unregistered it - the play/pause button in the PiP
 * window fired an intent nothing was listening for, and did nothing. The params
 * effect went with it, so even a working button could never have flipped its
 * own icon between play and pause.
 *
 * Being composed for the whole life of the player also means the params are
 * kept honest when there is no video: auto-enter used to stay armed after the
 * player was closed, so leaving the app could open an empty PiP window.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPipController(viewModel: VideoPlayerViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity ?: return
    if (!activity.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
    ) return

    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isExpanded by viewModel.isExpanded.collectAsState()
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    val videoBounds by viewModel.videoSurfaceBounds.collectAsState()

    val packageName = context.packageName

    // Registered for as long as a video player exists, PiP or not. Actions are
    // package-scoped so no other app can drive playback through them.
    DisposableEffect(context, viewModel) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    "$packageName.$ACTION_PLAY" -> viewModel.exoPlayer?.play()
                    "$packageName.$ACTION_PAUSE" -> viewModel.exoPlayer?.pause()
                    "$packageName.$ACTION_REWIND" ->
                        viewModel.seekBy(-VideoPlayerViewModel.SEEK_STEP_MS)
                    "$packageName.$ACTION_FORWARD" ->
                        viewModel.seekBy(VideoPlayerViewModel.SEEK_STEP_MS)
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("$packageName.$ACTION_PLAY")
            addAction("$packageName.$ACTION_PAUSE")
            addAction("$packageName.$ACTION_REWIND")
            addAction("$packageName.$ACTION_FORWARD")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // PictureInPictureParams live on the Activity and are sticky. Update them
    // synchronously after every successful composition instead of from a
    // coroutine effect: when the player collapses and the user immediately
    // swipes Home, even one queued frame with auto-enter still armed lets the
    // system capture the mini player and the entire app hierarchy into PiP.
    SideEffect {
        val builder = PictureInPictureParams.Builder()
        val validBounds = videoBounds?.takeIf { !it.isEmpty }
        val autoEnterEligible = currentVideo != null &&
            isExpanded &&
            isPlaying &&
            validBounds != null

        if (currentVideo == null) {
            // No video: disarm. Auto-enter is sticky, so a player closed while
            // it was armed would otherwise put the app into an empty PiP window
            // the next time the user swiped home.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(false)
            }
            builder.setActions(emptyList<RemoteAction>())
        } else {
            builder.setAspectRatio(pipAspectRatio(videoAspectRatio))
            builder.setActions(pipActions(activity, packageName, isPlaying))

            // Animate the PiP window out of the video rather than out of the
            // whole activity window. Without a source rect the system scales
            // the entire screen down - app chrome, nav bar and all - which is
            // what made the transition look like the UI was being sucked into
            // the window.
            // A source rect describes the content that will remain visible in
            // PiP. Never retain the old expanded-player rect while automatic
            // entry is disarmed; that stale rect is what makes Android animate
            // and sometimes freeze a crop of the normal activity UI.
            if (autoEnterEligible) validBounds?.let { builder.setSourceRectHint(it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Only auto-enter PiP while the full player is on screen.
                // Auto-entering from the mini player captures the whole app UI
                // into the PiP window instead of just the video surface. A
                // paused player is also ineligible: its newly attached PiP
                // SurfaceView may not produce a fresh frame to replace the
                // system's transition snapshot.
                builder.setAutoEnterEnabled(autoEnterEligible)
                // The content is a video, not a layout that needs to reflow, so
                // let the system crossfade the resize instead of re-laying out.
                builder.setSeamlessResizeEnabled(autoEnterEligible)
            }
        }

        try {
            activity.setPictureInPictureParams(builder.build())
        } catch (e: Exception) {
            KLog.w(TAG, "setPictureInPictureParams refused", e)
        }
    }
}

/**
 * The transport row inside the PiP window: back 10s, play/pause, forward 10s.
 *
 * A PiP window never receives touch events - the system owns every gesture on
 * it, so an in-window double-tap-to-seek is not something an app can implement.
 * RemoteActions are the only input surface PiP has, which is why the 10-second
 * skips live here as buttons rather than as the gesture they are on the full
 * player.
 *
 * Devices advertise how many actions they will render (three on every current
 * Android build). If a device offers fewer, play/pause is the one control that
 * must survive, so the seeks are dropped rather than the list truncated.
 */
private fun pipActions(
    activity: androidx.activity.ComponentActivity,
    packageName: String,
    isPlaying: Boolean
): List<RemoteAction> {
    val playPause = if (isPlaying) {
        remoteAction(activity, packageName, ACTION_PAUSE, R.drawable.ic_media_pause, "Pause")
    } else {
        remoteAction(activity, packageName, ACTION_PLAY, R.drawable.ic_media_play, "Play")
    }

    val maxActions = try {
        activity.maxNumPictureInPictureActions
    } catch (e: Exception) {
        3
    }
    if (maxActions < 3) return listOf(playPause)

    return listOf(
        remoteAction(
            activity, packageName, ACTION_REWIND,
            R.drawable.ic_media_replay_10, "Back 10 seconds"
        ),
        playPause,
        remoteAction(
            activity, packageName, ACTION_FORWARD,
            R.drawable.ic_media_forward_10, "Forward 10 seconds"
        )
    )
}

private fun remoteAction(
    activity: androidx.activity.ComponentActivity,
    packageName: String,
    action: String,
    iconRes: Int,
    label: String
): RemoteAction {
    val intent = PendingIntent.getBroadcast(
        activity,
        action.hashCode(),
        Intent("$packageName.$action").setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return RemoteAction(Icon.createWithResource(activity, iconRes), label, label, intent)
}

private const val TAG = "VideoPipController"
private const val ACTION_PLAY = "PIP_PLAY"
private const val ACTION_PAUSE = "PIP_PAUSE"
private const val ACTION_REWIND = "PIP_REWIND"
private const val ACTION_FORWARD = "PIP_FORWARD"
