package com.ivor.ivormusic.service

import com.ivor.ivormusic.data.ThemePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sleep timer: a duration, or the end of the current track.
 *
 * This lives with the service, not in PlayerViewModel where it used to. The
 * ViewModel is scoped to MainActivity, so its viewModelScope - and with it the
 * timer's delay() - was cancelled the moment the activity went away. Backing
 * out of the app while music kept playing (the whole point of a foreground
 * media service, and exactly what someone setting a sleep timer does next)
 * silently killed the timer, and playback ran all night. There was no
 * persistence either, so reopening the app showed no timer running and gave
 * the user no way to tell it had died.
 *
 * The player outlives the UI, so the thing that stops the player has to as
 * well. State is published back through the session's extras, which is how
 * every connected controller - the UI, and anything else - learns about it;
 * [onChanged] is where the service does that.
 *
 * @param setPauseAtEndOfItems applied to both crossfade engines. The standby
 *   becomes audible at the next crossfade and must not carry an old
 *   end-of-track instruction, nor miss a new one.
 * @param fadeOutAndPause eases the volume down over the given time and pauses.
 *   It belongs to whatever owns the volume, so it cannot fight a crossfade
 *   fade-in; cancelling the job it returns restores full volume.
 */
internal class SleepTimerController(
    private val scope: CoroutineScope,
    private val preferences: ThemePreferences,
    private val setPauseAtEndOfItems: (Boolean) -> Unit,
    private val fadeOutAndPause: (durationMs: Long) -> Job,
    private val pauseIfPlaying: () -> Unit,
    private val onChanged: () -> Unit,
) {
    private var job: Job? = null
    private var fadeJob: Job? = null

    /** Wall-clock ms when the timer fires, or 0 when no duration timer is set. */
    var endsAt: Long = 0L
        private set

    /** True while the player is set to stop when the current track finishes. */
    var endOfTrack: Boolean = false
        private set

    /**
     * Arm the sleep timer. [minutes] of 0 or less means "at the end of the
     * current track" instead of a duration.
     */
    fun start(minutes: Int) {
        clear(publish = false)

        if (minutes <= 0) {
            // Media3 has exactly this behaviour built in, and it is more precise
            // than watching for the track to end ourselves: the player stops on
            // the item boundary rather than a callback or two later, and a
            // later play() still moves on to the next track normally.
            endOfTrack = true
            setPauseAtEndOfItems(true)
            preferences.saveSleepTimer(endsAt = 0L, endOfTrack = true)
        } else {
            armDuration(System.currentTimeMillis() + minutes * 60_000L)
        }
        onChanged()
    }

    /** Re-arm a timer after the media service itself was recreated. */
    fun restore() {
        val savedEndOfTrack = preferences.isSleepTimerEndOfTrack()
        val savedEndsAt = preferences.getSleepTimerEndsAt()
        when {
            savedEndOfTrack -> {
                endOfTrack = true
                endsAt = 0L
                setPauseAtEndOfItems(true)
            }
            savedEndsAt > System.currentTimeMillis() -> armDuration(savedEndsAt)
            else -> {
                setPauseAtEndOfItems(false)
                preferences.clearSleepTimer()
            }
        }
    }

    /** Disarm, whether it fired or the user cancelled it. */
    fun clear(publish: Boolean = true) {
        job?.cancel()
        job = null
        // Cancelling mid-fade restores full volume (the fade's own completion).
        fadeJob?.takeIf { it.isActive }?.cancel()
        fadeJob = null
        endsAt = 0L
        endOfTrack = false
        setPauseAtEndOfItems(false)
        preferences.clearSleepTimer()
        if (publish) onChanged()
    }

    /** Stop counting without touching what is saved, for the service going away. */
    fun release() {
        job?.cancel()
        job = null
    }

    private fun armDuration(deadline: Long) {
        endOfTrack = false
        endsAt = deadline
        setPauseAtEndOfItems(false)
        preferences.saveSleepTimer(endsAt = deadline, endOfTrack = false)
        job = scope.launch {
            while (true) {
                val remaining = endsAt - System.currentTimeMillis()
                if (remaining <= FADE_MS) break
                delay((remaining - FADE_MS).coerceAtMost(TICK_MS))
            }
            // Fade ends on the deadline. A skip's fade-in can pre-empt it, so the pause is also enforced here.
            fadeOutAndPause((endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
                .also { fadeJob = it }
                .join()
            val left = endsAt - System.currentTimeMillis()
            if (left > 0L) delay(left)
            pauseIfPlaying()
            clear()
        }
    }

    private companion object {
        /**
         * How long the fade before the timer's pause takes. Long enough to read
         * as drifting off rather than as a glitch, short enough that the last
         * thing heard is not a minute of near-silence.
         */
        const val FADE_MS = 30_000L

        /**
         * Longest a single slice of the countdown sleeps for. Bounded so the
         * job re-checks the real deadline regularly instead of trusting one
         * long delay that deep sleep can stretch.
         */
        const val TICK_MS = 30_000L
    }
}
