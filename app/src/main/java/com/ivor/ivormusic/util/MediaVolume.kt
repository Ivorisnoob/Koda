package com.ivor.ivormusic.util

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt

/**
 * The device's media volume, as one small state holder a control can drive.
 *
 * **There is no app-level volume, deliberately.** `player.volume` in
 * `MusicService` already has two jobs - the loudness correction and the
 * crossfade/duck curve - and every write is `trackGain * curve * duckGain`
 * rather than a bare 1.0, so a third factor layered on top would be a fourth
 * writer of one field and the first thing to break a fade. The video player's
 * vertical volume drag settled this the same way: the level it moves is the
 * system `STREAM_MUSIC` level, which is what "volume" means to the person
 * holding the phone and is the same level their hardware keys move.
 *
 * **Nothing here is persisted.** The stream level already survives the process
 * on its own; storing a copy would mean two answers to one question.
 *
 * [steps] is how many positions the hardware keys move through (commonly 15,
 * but 25 and 100 both exist), and a control snaps to them: the slider is
 * continuous, but the thumb is drawn at the level that is actually audible, the
 * same rule the playback-speed slider follows.
 */
class MediaVolumeState internal constructor(private val audioManager: AudioManager) {

    /** Positions between silence and full; 0 when this stream reports none. */
    val steps: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /**
     * Whether this device lets anything move the level at all. True on a
     * fixed-output dock or TV, where `setStreamVolume` is a silent no-op - a
     * control is hidden rather than drawn dead.
     */
    val isFixed: Boolean = audioManager.isVolumeFixed

    /** Usable only where there is a range to move through and it can be moved. */
    val isAvailable: Boolean get() = steps > 0 && !isFixed

    var fraction by mutableFloatStateOf(read())
        private set

    val isMuted: Boolean get() = fraction <= 0f

    /**
     * The level mute was entered from, so unmuting goes back to it rather than
     * to a guess. Half scale only for the case where the sheet was opened on an
     * already-silent device and there is nothing to remember.
     */
    private var levelBeforeMute: Float = read().takeIf { it > 0f } ?: 0.5f

    /** Re-read the stream, for a level the hardware keys moved under us. */
    fun refresh() {
        val current = read()
        if (current != fraction) fraction = current
        if (current > 0f) levelBeforeMute = current
    }

    /**
     * Move the level, snapped to a real step. Returns the step it landed on, so
     * a caller can tick a haptic exactly when the audible level changes rather
     * than on every frame of a drag.
     */
    fun set(raw: Float): Int {
        if (steps <= 0) return 0
        val step = (raw.coerceIn(0f, 1f) * steps).roundToInt().coerceIn(0, steps)
        if (step != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            // Flag 0: no system volume panel. The control being dragged is
            // already showing the level, and the panel would cover it.
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
        }
        fraction = step.toFloat() / steps
        if (fraction > 0f) levelBeforeMute = fraction
        return step
    }

    fun toggleMute() {
        if (isMuted) set(levelBeforeMute) else set(0f)
    }

    private fun read(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }
}

/**
 * A [MediaVolumeState] that follows the device while it is on screen.
 *
 * **The level can move without us**, from the hardware keys, the system panel,
 * a headset or another app, so a control that read it once would sit at a stale
 * position for as long as the sheet stayed open. Two signals cover that, and
 * neither is load-bearing - a missed one costs a stale thumb, never a wrong
 * write, because every write reads the stream first:
 *
 * - a [ContentObserver] on the system settings table, where the per-output
 *   volumes live. [judgement] There is no public broadcast for a volume change
 *   (`android.media.VOLUME_CHANGED_ACTION` is unexported API), and Media3's
 *   device-volume commands would mean enabling device volume control on the
 *   service's players and handing session controllers a volume provider -
 *   a far larger change than a stale slider justifies.
 * - the lifecycle, because returning from the system panel or another app is
 *   exactly when the level most often differs from what we last drew.
 */
@Composable
fun rememberMediaVolume(): MediaVolumeState {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val state = remember(audioManager) { MediaVolumeState(audioManager) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(state, lifecycle, context) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = state.refresh()
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer
        )
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycle.addObserver(lifecycleObserver)
        state.refresh()
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            lifecycle.removeObserver(lifecycleObserver)
        }
    }
    return state
}
