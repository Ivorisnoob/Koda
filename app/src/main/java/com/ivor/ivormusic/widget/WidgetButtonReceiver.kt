package com.ivor.ivormusic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles widget button presses in the background. No UI, no flash —
 * behaves like a normal interactive widget.
 */
class WidgetButtonReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(WidgetActionActivity.EXTRA_ACTION) ?: return
        val index = intent.getIntExtra(WidgetActionActivity.EXTRA_INDEX, -1)
        
        // Use goAsync to keep the receiver alive for the coroutine
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    WidgetActionActivity.ACTION_TOGGLE -> {
                        withController(context) { controller ->
                            if (controller.playWhenReady) controller.pause()
                            else controller.play()
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_NEXT -> {
                        withController(context) { controller ->
                            controller.seekToNext()
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_PREVIOUS -> {
                        withController(context) { controller ->
                            controller.seekToPrevious()
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_SHUFFLE -> {
                        withController(context) { controller ->
                            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_CYCLE_REPEAT -> {
                        withController(context) { controller ->
                            controller.repeatMode = when (controller.repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_OFF ->
                                    androidx.media3.common.Player.REPEAT_MODE_ALL
                                androidx.media3.common.Player.REPEAT_MODE_ALL ->
                                    androidx.media3.common.Player.REPEAT_MODE_ONE
                                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                            }
                            PlayerWidgets.publish(context, controller.toSnapshot())
                        }
                    }
                    WidgetActionActivity.ACTION_PLAY_INDEX -> {
                        if (index >= 0) {
                            withController(context) { controller ->
                                controller.seekTo(index, 0)
                                controller.play()
                                PlayerWidgets.publish(context, controller.toSnapshot())
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
