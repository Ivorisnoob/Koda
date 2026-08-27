package com.ivor.ivormusic.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.Player

/**
 * Every transport action binds a throwaway [MediaController] rather than holding
 * one: widgets live for days while controllers are expensive session clients,
 * and a held controller would keep the service bound forever. After each tap the
 * widget is redrawn so the picture stays current.
 */

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { controller ->
            if (controller.isPlaying || controller.playbackState == Player.STATE_BUFFERING) {
                controller.pause()
            } else {
                controller.play()
            }
        }
        PlayerWidgets.pushAll(context)
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { it.seekToNext() }
        PlayerWidgets.pushAll(context)
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { it.seekToPrevious() }
        PlayerWidgets.pushAll(context)
    }
}

class SeekBackAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { it.seekBack() }
        PlayerWidgets.pushAll(context)
    }
}

class SeekForwardAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { it.seekForward() }
        PlayerWidgets.pushAll(context)
    }
}

class ToggleShuffleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { it.shuffleModeEnabled = !it.shuffleModeEnabled }
        PlayerWidgets.pushAll(context)
    }
}

class CycleRepeatAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        withTransientController(context) { controller ->
            controller.repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
        PlayerWidgets.pushAll(context)
    }
}
