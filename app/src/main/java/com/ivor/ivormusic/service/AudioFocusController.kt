package com.ivor.ivormusic.service

import com.ivor.ivormusic.util.KLog

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Audio focus, owned by the service instead of by a player.
 *
 * **Two engines cannot each manage their own focus.** ExoPlayer's built-in
 * handling requests focus per player, and two requests from the same process
 * are two distinct clients, so the second one arriving makes the first receive
 * `AUDIOFOCUS_LOSS` and pause itself. During a crossfade that is precisely the
 * outgoing track being killed the instant the incoming one starts, which is the
 * one thing the crossfade exists to prevent. So both players are built with
 * `handleAudioFocus = false` and this holds the single request for both.
 *
 * Ducking is applied as a gain rather than a pause, and it multiplies into the
 * same volume expression as the loudness correction and the fade curve - see
 * [CrossfadeEngine], which owns that arithmetic. Nothing here touches a player
 * directly; it reports, and the engine decides.
 */
class AudioFocusController(
    context: Context,
    /** Pause, keeping [wasPlayingBeforeLoss] so a later gain can resume. */
    private val onPause: () -> Unit,
    /** Resume after a transient loss that this controller paused for. */
    private val onResume: () -> Unit,
    /** 1.0 for normal, [DUCK_GAIN] while another app is talking over us. */
    private val onDuck: (Float) -> Unit,
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Whether *this* controller paused playback. A user pausing during a phone
     * call must not be overridden by the resume when the call ends, so only a
     * loss-driven pause is allowed to resume.
     */
    private var pausedByFocusLoss = false

    private var holdsFocus = false

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent: another app took over for good. Drop the request
                // rather than sitting on one we no longer own, and do not arm a
                // resume - nothing is coming back.
                holdsFocus = false
                pausedByFocusLoss = false
                onDuck(1f)
                onPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByFocusLoss = true
                onDuck(1f)
                onPause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(DUCK_GAIN)

            AudioManager.AUDIOFOCUS_GAIN -> {
                onDuck(1f)
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    onResume()
                }
            }
        }
    }

    private val request: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            // Ducking is handled here rather than by the system, so the gain
            // composes with the loudness correction instead of fighting it.
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener)
            .build()

    /** @return false when the system refused, in which case do not start. */
    fun request(): Boolean {
        if (holdsFocus) return true
        val result = audioManager.requestAudioFocus(request)
        holdsFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!holdsFocus) KLog.w(TAG, "Audio focus refused ($result)")
        return holdsFocus
    }

    fun abandon() {
        if (!holdsFocus) return
        audioManager.abandonAudioFocusRequest(request)
        holdsFocus = false
        pausedByFocusLoss = false
    }

    private companion object {
        private const val TAG = "AudioFocusController"

        /** About -14 dB, the conventional duck depth. */
        const val DUCK_GAIN = 0.2f
    }
}
