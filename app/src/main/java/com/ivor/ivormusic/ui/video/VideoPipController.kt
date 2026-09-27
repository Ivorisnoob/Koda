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
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    val videoBounds by viewModel.videoSurfaceBounds.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val relatedVideos by viewModel.relatedVideos.collectAsState()
    val isLive by viewModel.isLive.collectAsState()
    // The Settings row writes through another ThemePreferences instance; this
    // one's change listener carries the write here, so the published params
    // follow a change without waiting for the next play/pause.
    val themePreferences = androidx.compose.runtime.remember(context) {
        com.ivor.ivormusic.data.ThemePreferences(context)
    }
    val pipButtons by themePreferences.pipButtons.collectAsState()
    val transport = pipTransport(
        queue = queue,
        hasRelated = relatedVideos.isNotEmpty(),
        isLive = isLive,
        seekOnly = pipButtons == com.ivor.ivormusic.data.ThemePreferences.PIP_BUTTONS_SEEK
    )

    val packageName = context.packageName

    // This is the known-good pre-redesign control path. Keep the receiver in
    // the controller above MainActivity's PiP early return so swapping the app
    // UI for PipVideoSurface cannot unregister the buttons behind the window.
    DisposableEffect(context, viewModel, packageName) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    "$packageName.$ACTION_PLAY" -> viewModel.playFromExternal()
                    "$packageName.$ACTION_PAUSE" -> viewModel.pause()
                    "$packageName.$ACTION_REWIND" ->
                        viewModel.seekBy(-VideoPlayerViewModel.SEEK_STEP_MS)
                    "$packageName.$ACTION_FORWARD" ->
                        viewModel.seekBy(VideoPlayerViewModel.SEEK_STEP_MS)
                    "$packageName.$ACTION_NEXT" -> viewModel.playNextOrRelated()
                    "$packageName.$ACTION_PREVIOUS" -> viewModel.playPreviousInQueue()
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("$packageName.$ACTION_PLAY")
            addAction("$packageName.$ACTION_PAUSE")
            addAction("$packageName.$ACTION_REWIND")
            addAction("$packageName.$ACTION_FORWARD")
            addAction("$packageName.$ACTION_NEXT")
            addAction("$packageName.$ACTION_PREVIOUS")
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
        videoAspectRatio,
        videoBounds,
        transport
    ) {
        val builder = PictureInPictureParams.Builder()
        val validBounds = videoBounds?.takeIf { !it.isEmpty }
        val autoEnterEligible = currentVideo != null && isExpanded
        val hasContent = currentVideo != null

        if (!hasContent) {
            // No video: disarm. Auto-enter is sticky, so leaving it armed
            // would open a PiP window onto nothing when the user swiped home.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(false)
            }
            builder.setActions(emptyList<RemoteAction>())
        } else {
            builder.setAspectRatio(pipAspectRatio(videoAspectRatio))
            builder.setActions(pipActions(activity, packageName, isPlaying, transport))

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

/** Which three-button row the PiP window shows around play/pause. */
internal sealed interface PipTransport {
    /** In a playlist: previous and next video, disabled at either end. */
    data class Playlist(val hasPrevious: Boolean, val hasNext: Boolean) : PipTransport
    /** A lone video with something related lined up: back 10s, and next. */
    data object Related : PipTransport
    /** Nothing to move to: back and forward 10s. */
    data object Seek : PipTransport
}

/**
 * One rule for both the published params and an explicit PiP entry.
 *
 * [seekOnly] is the "Skip 10s" choice in Settings (feedback, September 2026):
 * some viewers use the window to rewatch and skip within one video and never
 * to move on, and for them a Next button is one mis-tap from losing the video.
 */
internal fun pipTransport(
    queue: com.ivor.ivormusic.data.VideoQueue?,
    hasRelated: Boolean,
    isLive: Boolean,
    seekOnly: Boolean,
): PipTransport = when {
    seekOnly -> PipTransport.Seek
    queue != null -> PipTransport.Playlist(hasPrevious = queue.hasPrevious, hasNext = queue.hasNext)
    hasRelated && !isLive -> PipTransport.Related
    else -> PipTransport.Seek
}

/**
 * [pipTransport] from the ViewModel's current values, for non-composable
 * callers. The setting is a fresh read: the ViewModel holds no preferences
 * instance of its own.
 */
internal fun VideoPlayerViewModel.currentPipTransport(): PipTransport =
    pipTransport(
        queue = queue.value,
        hasRelated = relatedVideos.value.isNotEmpty(),
        isLive = isLive.value,
        seekOnly = com.ivor.ivormusic.data.ThemePreferences.pipButtonsSeekOnly(getApplication())
    )

/**
 * The transport row inside the PiP window: play/pause between two buttons
 * chosen by [transport] - previous/next in a playlist, back 10s and next when
 * a related video is lined up, back/forward 10s otherwise. Next was asked for
 * (feedback, September 2026): the window only ever offered seeking, so moving
 * on meant leaving PiP.
 *
 * A PiP window never receives touch events - the system owns every gesture on
 * it, so an in-window double-tap-to-seek is not something an app can implement.
 * RemoteActions are the only input surface PiP has, which is why the 10-second
 * skips live here as buttons rather than as the gesture they are on the full
 * player.
 *
 * Always publish the complete row of three. The connected OnePlus Android 12
 * build reports fewer than three available actions while its PiP menu can
 * render the normal three slots; trusting that value leaves only play/pause
 * visible. Three is also the most phones show, which is why a fourth button
 * is never added - a five-button row (previous, back 10s, play, forward 10s,
 * next) was asked for in September 2026 and is not something the window can
 * draw, hence the "Picture-in-picture buttons" setting choosing the two side
 * slots instead. The window manager can truncate the row itself on a
 * genuinely smaller surface.
 */
internal fun pipActions(
    activity: androidx.activity.ComponentActivity,
    packageName: String,
    isPlaying: Boolean,
    transport: PipTransport = PipTransport.Seek
): List<RemoteAction> {
    val playPause = if (isPlaying) {
        remoteAction(
            activity, packageName, ACTION_PAUSE,
            R.drawable.ic_media_pause, activity.getString(R.string.cd_pause)
        )
    } else {
        remoteAction(
            activity, packageName, ACTION_PLAY,
            R.drawable.ic_media_play, activity.getString(R.string.cd_play)
        )
    }

    val rewind = remoteAction(
        activity, packageName, ACTION_REWIND,
        R.drawable.ic_media_replay_10, activity.getString(R.string.pip_back_10)
    )
    val next = { enabled: Boolean ->
        remoteAction(
            activity, packageName, ACTION_NEXT,
            R.drawable.ic_media_next, activity.getString(R.string.pip_next)
        ).apply { isEnabled = enabled }
    }
    return when (transport) {
        is PipTransport.Playlist -> listOf(
            remoteAction(
                activity, packageName, ACTION_PREVIOUS,
                R.drawable.ic_media_previous, activity.getString(R.string.pip_previous)
            ).apply { isEnabled = transport.hasPrevious },
            playPause,
            next(transport.hasNext)
        )
        PipTransport.Related -> listOf(rewind, playPause, next(true))
        PipTransport.Seek -> listOf(
            rewind,
            playPause,
            remoteAction(
                activity, packageName, ACTION_FORWARD,
                R.drawable.ic_media_forward_10, activity.getString(R.string.pip_forward_10)
            )
        )
    }
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
private const val ACTION_NEXT = "PIP_NEXT"
private const val ACTION_PREVIOUS = "PIP_PREVIOUS"
