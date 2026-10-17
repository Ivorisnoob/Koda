package com.ivor.ivormusic.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Transparent trampoline that forwards widget button presses to
 * WidgetButtonReceiver and finishes immediately (synchronously).
 * Uses Theme.NoDisplay to avoid any visual flash.
 */
class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Forward to the receiver (does the work in background)
        val broadcast = Intent(this, WidgetButtonReceiver::class.java).apply {
            putExtra(EXTRA_ACTION, intent.getStringExtra(EXTRA_ACTION))
            putExtra(EXTRA_INDEX, intent.getIntExtra(EXTRA_INDEX, -1))
            // Copy Glance ActionParameters if present
            intent.extras?.let { putExtras(it) }
        }
        sendBroadcast(broadcast)
        finish()  // Synchronous — safe with Theme.NoDisplay
    }

    companion object {
        const val EXTRA_ACTION = "widget_action"
        const val EXTRA_INDEX = "widget_index"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_SHUFFLE = "shuffle"
        const val ACTION_PLAY_INDEX = "play_index"
        const val ACTION_CYCLE_REPEAT = "cycle_repeat"
    }
}
