package com.ivor.ivormusic.service

import android.content.Intent
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one-button headset gesture: one press plays or pauses, two skip ahead,
 * three go back.
 *
 * Media3's stock gesture resolver treats a HEADSETHOOK triple as Next followed
 * by a delayed Play/Pause. Only that raw one-button gesture is taken over here;
 * devices that emit explicit media keys continue through Media3 unchanged.
 *
 * @param skip a skip in the given direction, through the service's own
 *   transition; `restartCurrent` false makes Previous always change song.
 */
internal class HeadsetButtonHandler(
    private val scope: CoroutineScope,
    private val skip: (forward: Boolean, restartCurrent: Boolean) -> Unit,
) {
    private val timeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val sequence = HeadsetButtonSequence(timeoutMs)
    private var pending: Job? = null

    /** True when the press was handled here; false hands it back to Media3. */
    @Suppress("DEPRECATION")
    fun handle(player: Player, intent: Intent): Boolean {
        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return false
        if (keyEvent.keyCode != KeyEvent.KEYCODE_HEADSETHOOK ||
            keyEvent.action != KeyEvent.ACTION_DOWN ||
            keyEvent.repeatCount != 0
        ) return false

        pending?.cancel()
        val result = sequence.onTap(keyEvent.eventTime)
        result.completedAction?.let { perform(player, it) }
        if (result.awaitingMore) {
            pending = scope.launch {
                delay(timeoutMs)
                sequence.consumePending()?.let { perform(player, it) }
            }
        }
        return true
    }

    fun release() {
        pending?.cancel()
        pending = null
        sequence.clear()
    }

    private fun perform(player: Player, action: HeadsetButtonAction) {
        when (action) {
            HeadsetButtonAction.TOGGLE_PLAY_PAUSE -> {
                if (player.playWhenReady) {
                    player.pause()
                } else {
                    when (player.playbackState) {
                        Player.STATE_IDLE -> player.prepare()
                        Player.STATE_ENDED -> player.seekToDefaultPosition()
                    }
                    player.play()
                }
            }
            HeadsetButtonAction.NEXT -> skip(true, true)
            HeadsetButtonAction.PREVIOUS -> skip(false, false)
        }
    }
}
