package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.util.KLog

import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.R
import com.ivor.ivormusic.service.VideoPlaybackService

/**
 * Everything that keeps the system Picture-in-Picture window in sync with the
 * video player: its shape and its transport controls.
 *
 * This has to be composed **above** MainActivity's `if (isInPipMode) return`,
 * and that is the whole reason it is its own composable rather than a block
 * inside [VideoPlayerOverlay]. The overlay is part of the app UI that PiP
 * replaces, so entering PiP tears it out of the composition. The actions target
 * [VideoPlaybackService] directly rather than relying on any UI-owned receiver;
 * this controller remains here so their icons track play/pause state in PiP.
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
    val miniVideoBounds by viewModel.miniVideoSurfaceBounds.collectAsState()

    // PictureInPictureParams live on the Activity and are sticky. Update them
    // synchronously after every successful composition instead of from a
    // coroutine effect: when the player collapses and the user immediately
    // swipes Home, even one queued frame with auto-enter still armed lets the
    // system capture the mini player and the entire app hierarchy into PiP.
    SideEffect {
        val builder = PictureInPictureParams.Builder()
        val validBounds = (if (isExpanded) videoBounds else miniVideoBounds)
            ?.takeIf { !it.isEmpty }
        val autoEnterEligible = currentVideo != null &&
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
            builder.setActions(pipActions(activity, isPlaying))

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
                // Both expanded and collapsed playback are eligible, but only
                // after that layout's own video surface has reported bounds.
                // The source rect makes the transition snapshot video-only;
                // MainActivity then replaces the app with PipVideoSurface.
                // A paused player remains ineligible because its newly attached
                // PiP SurfaceView may not produce a fresh replacement frame.
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
 * Devices advertise how many actions they will render. Play/pause is the one
 * control that must survive; a two-slot OEM still gets forward seek rather
 * than unnecessarily collapsing the row to a single button.
 */
private fun pipActions(
    activity: androidx.activity.ComponentActivity,
    isPlaying: Boolean
): List<RemoteAction> {
    val playPause = if (isPlaying) {
        remoteAction(
            activity, VideoPlaybackService.ACTION_PAUSE,
            R.drawable.ic_media_pause, "Pause"
        )
    } else {
        remoteAction(
            activity, VideoPlaybackService.ACTION_PLAY,
            R.drawable.ic_media_play, "Play"
        )
    }

    val maxActions = try {
        activity.maxNumPictureInPictureActions
    } catch (e: Exception) {
        3
    }
    val actions = listOf(
        remoteAction(
            activity, VideoPlaybackService.ACTION_REWIND,
            R.drawable.ic_media_replay_10, "Back 10 seconds"
        ),
        playPause,
        remoteAction(
            activity, VideoPlaybackService.ACTION_FORWARD,
            R.drawable.ic_media_forward_10, "Forward 10 seconds"
        )
    )
    return when {
        maxActions >= 3 -> actions
        maxActions == 2 -> listOf(playPause, actions.last())
        else -> listOf(playPause)
    }
}

private fun remoteAction(
    activity: androidx.activity.ComponentActivity,
    action: String,
    iconRes: Int,
    label: String
): RemoteAction {
    // The explicit service is already publishing this ExoPlayer through its
    // MediaSession. Unlike a dynamic receiver, it does not belong to a Compose
    // subtree or Activity UI that an OEM may suspend while PiP is active.
    val intent = PendingIntent.getService(
        activity,
        action.hashCode(),
        Intent(activity, VideoPlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return RemoteAction(Icon.createWithResource(activity, iconRes), label, label, intent)
}

private const val TAG = "VideoPipController"
