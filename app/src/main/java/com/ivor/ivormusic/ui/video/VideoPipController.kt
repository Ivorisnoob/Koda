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
import androidx.compose.runtime.LaunchedEffect
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
 * replaces, so entering PiP tears it out of the composition. This controller
 * stays above that replacement so its package-scoped action receiver remains
 * registered and its icons continue to track play/pause state in PiP.
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
    val isCasting by viewModel.isCasting.collectAsState()
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    val videoBounds by viewModel.videoSurfaceBounds.collectAsState()

    val packageName = context.packageName

    // This is the known-good pre-redesign control path. Keep the receiver in
    // the controller above MainActivity's PiP early return so swapping the app
    // UI for PipVideoSurface cannot unregister the buttons behind the window.
    DisposableEffect(context, viewModel, packageName) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    // Routed through the ViewModel, not straight at exoPlayer:
                    // while casting these buttons drive the receiver.
                    "$packageName.$ACTION_PLAY" -> viewModel.playFromExternal()
                    "$packageName.$ACTION_PAUSE" -> viewModel.pause()
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
            context.registerReceiver(
                receiver,
                filter,
                android.content.Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // PictureInPictureParams live on the Activity and are sticky. Match 4.5's
    // keyed effect so every meaningful player-state change publishes a fresh
    // snapshot to Android. Eligibility follows the proven 4.5 rule: an
    // expanded video may auto-enter PiP. Surface bounds improve the transition
    // but must never decide whether PiP is armed.
    LaunchedEffect(
        currentVideo?.videoId,
        isPlaying,
        isExpanded,
        isCasting,
        videoAspectRatio,
        videoBounds
    ) {
        val builder = PictureInPictureParams.Builder()
        val validBounds = videoBounds?.takeIf { !it.isEmpty }
        // Casting disarms PiP entirely: the picture lives on the receiver and
        // a PiP window would show only the casting card.
        val autoEnterEligible = currentVideo != null && isExpanded && !isCasting
        val hasContent = currentVideo != null && !isCasting

        if (!hasContent) {
            // No local video: disarm. Auto-enter is sticky, so leaving it armed
            // through a cast would open a window onto the casting card when the
            // user swiped home.
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
            // PiP. It is only a transition hint: Android can still enter PiP
            // when the expanded surface has not reported bounds yet.
            if (autoEnterEligible) validBounds?.let { builder.setSourceRectHint(it) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Match 4.5: only the expanded player auto-enters. Entering from
                // the mini player can capture the whole activity into the PiP
                // transition instead of the dedicated video surface.
                builder.setAutoEnterEnabled(autoEnterEligible)
                // The content is a video, not a layout that needs to reflow, so
                // let the system crossfade the resize instead of re-laying out.
                builder.setSeamlessResizeEnabled(true)
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
 * Always publish the complete row. The connected OnePlus Android 12 build
 * reports fewer than three available actions while its PiP menu can render the
 * normal three slots; trusting that value leaves only play/pause visible. The
 * window manager can truncate the row itself on a genuinely smaller surface.
 */
internal fun pipActions(
    activity: androidx.activity.ComponentActivity,
    packageName: String,
    isPlaying: Boolean
): List<RemoteAction> {
    val playPause = if (isPlaying) {
        remoteAction(
            activity, packageName, ACTION_PAUSE,
            R.drawable.ic_media_pause, "Pause"
        )
    } else {
        remoteAction(
            activity, packageName, ACTION_PLAY,
            R.drawable.ic_media_play, "Play"
        )
    }

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
