package com.ivor.ivormusic.widget

import android.content.Context
import android.os.Bundle
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.ivor.ivormusic.service.MusicService
import kotlinx.coroutines.delay

/**
 * Every widget tap binds one throwaway [MediaController], issues the command,
 * and publishes the state that came of it. Two things about this app decide the
 * shape of that.
 *
 * **Play/pause masks locally, skips do not.** Media3 applies play() and pause()
 * to the controller optimistically, so reading straight back reports the new
 * intent and the glyph flips on the next redraw. Skips do not, because
 * `MusicService`'s session callback intercepts COMMAND_SEEK_TO_NEXT/PREVIOUS to
 * run them through the crossfade engine and answers RESULT_ERROR_NOT_SUPPORTED,
 * which suppresses the masking. A read taken immediately after a skip returns
 * the *previous* track, so skips wait a beat before reading back.
 *
 * **Publishing is safe from either side.** The service writes the same store
 * from its own player callbacks, and both writers write settled state, so
 * whichever lands last is still correct - there is no ordering to protect.
 */

/** How long an intercepted command needs before the session reflects it. */
private const val SETTLE_MS = 500L

/**
 * Issue [command], let the session settle if it has to, then publish what the
 * session now reports.
 */
private suspend fun runTransport(
    context: Context,
    settle: Boolean,
    command: (MediaController) -> Unit,
) {
    val sent = withController(context) { controller ->
        command(controller)
        true
    } ?: false
    if (!sent) return

    if (settle) {
        delay(SETTLE_MS)
        val settled = withController(context) { it.toSnapshot() } ?: return
        PlayerWidgets.publish(context, settled)
    }
    // Without a settle the command masked locally and runImmediate already
    // published; nothing more to do.
}

/**
 * The masking path: the command is visible on the controller that issued it, so
 * the state is read and published without a second bind or a wait.
 */
private suspend fun runImmediate(
    context: Context,
    command: (MediaController) -> Unit,
) {
    val snapshot = withController(context) { controller ->
        command(controller)
        controller.toSnapshot()
    } ?: return
    PlayerWidgets.publish(context, snapshot)
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runImmediate(context) { controller ->
            // Decide from live state, not from the last render: the widget's
            // picture of the world can be a beat behind by tap time. Buffering
            // counts as playing, so a second tap during a spin-up stops it
            // instead of queueing a redundant play.
            if (controller.playWhenReady ||
                controller.playbackState == Player.STATE_BUFFERING
            ) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runTransport(context, settle = true) { it.seekToNext() }
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runTransport(context, settle = true) { it.seekToPrevious() }
    }
}

class SeekBackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runImmediate(context) { it.seekBack() }
    }
}

class SeekForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runImmediate(context) { it.seekForward() }
    }
}

class ToggleShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runImmediate(context) { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }
}

class CycleRepeatAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        runImmediate(context) { controller ->
            controller.repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
}

/**
 * Jump to a queue entry from the Up next widget. Sent as the app's own
 * skip-to-index command rather than seekToDefaultPosition, so a widget jump
 * runs through the same crossfade path as one taken in the queue sheet; a plain
 * seek would land on the target with no overlap and, mid-fade, on the wrong
 * player.
 */
class PlayQueueIndexAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val index = parameters[QueueIndexKey] ?: return
        runTransport(context, settle = true) { controller ->
            controller.sendCustomCommand(
                SessionCommand(MusicService.CMD_SKIP_TO_INDEX, Bundle.EMPTY),
                Bundle().apply { putInt(MusicService.ARG_SKIP_INDEX, index) },
            )
        }
    }

    companion object {
        val QueueIndexKey = ActionParameters.Key<Int>("koda.widget.queueIndex")
    }
}
