package com.ivor.ivormusic.service

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

/**
 * Two ExoPlayers alternating whole tracks, so a transition is a real overlap.
 *
 * **Why two engines and not one.** One player renders one item at a time, which
 * is why the previous implementation faded the outgoing track to silence, let
 * it end, and faded the incoming one up: a dip in the middle, the one thing a
 * crossfade is defined by not being. And a track cannot be *handed* from one
 * engine to another mid-play - resuming the same audio on a second pipeline a
 * few milliseconds off produces a click, and matching the position exactly
 * produces comb filtering between two copies of the same signal. So whichever
 * engine starts a track finishes it, and the engines alternate. Everything
 * below follows from that.
 *
 * **The session follows the audible player.** `MediaSession` wraps exactly one
 * `Player`, and the notification, Bluetooth, Android Auto and the app's own
 * `MediaController` all read through it, so at the end of a transition the
 * session is re-pointed at whichever engine is now carrying the music
 * ([onActiveChanged]). Before that moment the standby holds **only the next
 * item**, and the full queue is spliced around it with `addMediaItems` at swap
 * time. That ordering is deliberate twice over: `addMediaItems` does not
 * interrupt the item already playing, and building the surrounding queue from
 * the live timeline at swap time rather than from a snapshot taken at fade
 * start means a queue edited during the fade - or an item replaced by the
 * prefetcher, which happens constantly - lands correctly instead of being
 * reverted.
 *
 * **The curve is equal power.** `ExoPlayer.volume` is a linear amplitude
 * scalar and power goes as amplitude squared, so cosine out against sine in
 * keeps the summed energy flat: no dB conversion and no lookup table. Two
 * linear ramps would instead dip about 6 dB at the midpoint, which is the hole
 * that makes an amateur crossfade recognisable.
 *
 * **The ramp is driven from playback position, not from a step counter.** The
 * old fade ticked on the one-second progress loop, so a three second fade got
 * about three volume updates and stepped audibly. This resolves the curve from
 * the outgoing player's actual `currentPosition` every frame or so, which means
 * a buffer stall or a seek cannot desync the two halves from each other.
 *
 * Every volume written is `trackGain * curve * duckGain`: the loudness
 * correction for the whole track, the transition curve, and audio focus
 * ducking. None of the three may be dropped, and in particular no ramp may end
 * at a bare 1.0 - that would undo the loudness correction at exactly the moment
 * the next track starts.
 */
@UnstableApi
class CrossfadeEngine(
    private val scope: CoroutineScope,
    /**
     * Builds one configured player. Called twice. Both must be built with
     * `handleAudioFocus = false` - see [AudioFocusController] for why two
     * players cannot each manage focus - and given the same audio session id,
     * or an external equalizer would attach to alternate tracks only.
     */
    playerFactory: () -> ExoPlayer,
    /** Re-point the `MediaSession` at the newly audible player. */
    private val onActiveChanged: (ExoPlayer) -> Unit,
    /** The loudness correction for the media item playing on [player]. */
    private val gainFor: (player: ExoPlayer) -> Float,
) {

    private val playerA: ExoPlayer = playerFactory()
    private val playerB: ExoPlayer = playerFactory()

    /** The engine the session points at and the user is listening to. */
    var active: ExoPlayer = playerA
        private set

    private val standby: ExoPlayer get() = if (active === playerA) playerB else playerA

    /** Global attenuation from audio focus ducking. */
    @Volatile
    var duckGain: Float = 1f
        set(value) {
            field = value
            applyIdleVolumes()
        }

    private var fadeJob: Job? = null

    /** True from the moment the standby starts until the swap completes. */
    val isFading: Boolean get() = fadeJob?.isActive == true

    /**
     * The media id the standby is playing, so the caller can tell a transition
     * that is already under way from one that still needs starting.
     */
    private var fadingIntoId: String? = null

    /** Queue index that will become current if the in-flight swap completes. */
    var pendingTargetIndex: Int? = null
        private set

    private var movedListener: Player.Listener? = null

    /**
     * Follow the audible player only.
     *
     * Registered on both engines it would fire twice, and the standby's events
     * are meaningless to the service: the standby transitions into a single
     * item it was handed directly, which is not a queue advance and must not
     * drive validation or prefetching. The listener moves with [active]
     * instead, and [onActiveChanged] is the signal that a swap happened, since
     * the incoming player never emits a transition of its own.
     */
    fun setActiveListener(listener: Player.Listener) {
        movedListener?.let {
            playerA.removeListener(it)
            playerB.removeListener(it)
        }
        movedListener = listener
        active.addListener(listener)
    }

    /**
     * Push the resting volume to both engines.
     *
     * Only touches a player that is not being ramped: a write landing mid-fade
     * would fight the curve for one frame and be heard as a stutter.
     */
    fun applyIdleVolumes() {
        if (isFading) return
        active.volume = gainFor(active) * duckGain
        standby.volume = 0f
    }

    /**
     * Begin overlapping [nextItem] with what [active] is playing.
     *
     * @param durationMs how long the overlap runs. Clamped against what is
     *   actually left of the outgoing track, because a fade longer than the
     *   remainder would still be ramping when the track ended.
     * @return false when the transition could not be started, in which case the
     *   caller should let the active player advance on its own.
     */
    fun startTransition(
        nextItem: MediaItem,
        durationMs: Long,
        targetIndex: Int = active.getNextMediaItemIndex(),
    ): Boolean {
        if (isFading) return false
        val outgoing = active
        val incoming = standby

        if (targetIndex !in 0 until outgoing.mediaItemCount) return false
        if (outgoing.getMediaItemAt(targetIndex).mediaId != nextItem.mediaId) return false
        val remaining = outgoing.duration - outgoing.currentPosition
        if (outgoing.duration <= 0 || remaining <= 0) return false
        // Leave a beat at the end: ending the fade exactly on the track
        // boundary races the player's own advance.
        val fadeMs = durationMs.coerceAtMost(remaining - END_GUARD_MS)
        if (fadeMs < MIN_FADE_MS) return false

        return try {
            fadingIntoId = nextItem.mediaId
            pendingTargetIndex = targetIndex
            // One item only. The rest of the queue is spliced around it at swap
            // time, from the live timeline rather than a stale snapshot.
            incoming.setMediaItem(nextItem)
            incoming.volume = 0f
            incoming.prepare()
            incoming.playWhenReady = false

            fadeJob = scope.launch {
                runFade(outgoing, incoming, fadeMs, targetIndex)
            }.also { job ->
                job.invokeOnCompletion {
                    if (fadeJob === job) fadeJob = null
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start transition", e)
            fadingIntoId = null
            pendingTargetIndex = null
            runCatching { incoming.stop() }
            false
        }
    }

    private suspend fun runFade(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        requestedFadeMs: Long,
        targetIndex: Int,
    ) {
        try {
            val ready = withTimeoutOrNull(STANDBY_READY_TIMEOUT_MS) {
                while (incoming.playbackState != Player.STATE_READY) {
                    currentCoroutineContext().ensureActive()
                    if (incoming.playerError != null || incoming.playbackState == Player.STATE_ENDED) {
                        return@withTimeoutOrNull false
                    }
                    delay(RAMP_INTERVAL_MS)
                }
                true
            } == true
            if (!ready) {
                Log.w(TAG, "Standby was not ready in time; abandoning the overlap")
                abortInto(outgoing, incoming)
                return
            }

            val remaining = outgoing.duration - outgoing.currentPosition
            val fadeMs = requestedFadeMs.coerceAtMost(remaining - END_GUARD_MS)
            if (fadeMs < MIN_FADE_MS) {
                abortInto(outgoing, incoming)
                return
            }

            val startPosition = outgoing.currentPosition
            val outGain = gainFor(outgoing)
            val inGain = gainFor(incoming)
            incoming.playWhenReady = true

            while (scope.isActive) {
                currentCoroutineContext().ensureActive()
                // Read the curve off real playback position, so a stall moves
                // both halves together instead of sliding them apart.
                val elapsed = outgoing.currentPosition - startPosition
                val t = (elapsed.toFloat() / fadeMs).coerceIn(0f, 1f)

                // Equal power: cos^2 + sin^2 == 1, so the summed energy is flat
                // across the transition instead of dipping in the middle.
                val angle = t * (Math.PI.toFloat() / 2f)
                outgoing.volume = outGain * cos(angle) * duckGain
                incoming.volume = inGain * sin(angle) * duckGain

                if (t >= 1f) break
                // The outgoing player ending early (a short file, an error)
                // must not leave this spinning against a frozen position.
                if (outgoing.playbackState == Player.STATE_ENDED) break
                // A fatal error drives a player to STATE_IDLE, and the service's
                // listener is not on the standby, so nothing else would notice.
                // Swapping onto a dead engine would be silence with a running
                // progress bar, so abandon and let the outgoing track finish.
                if (incoming.playbackState == Player.STATE_IDLE) {
                    Log.w(TAG, "Standby engine died mid-fade; abandoning the overlap")
                    abortInto(outgoing, incoming)
                    return
                }
                delay(RAMP_INTERVAL_MS)
            }
            currentCoroutineContext().ensureActive()
            completeSwap(outgoing, incoming, gainFor(incoming), targetIndex)
        } catch (e: CancellationException) {
            abortInto(outgoing, incoming)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Transition failed; keeping the outgoing player", e)
            abortInto(outgoing, incoming)
        }
    }

    /**
     * Hand the session to [incoming] and retire [outgoing].
     *
     * The queue is rebuilt here rather than at fade start: `addMediaItems` does
     * not disturb the item already playing, and reading the surrounding items
     * from the outgoing timeline *now* picks up anything that changed during
     * the fade - a reorder, a removal, or one of the prefetcher's
     * `replaceMediaItem` calls.
     */
    private fun completeSwap(
        outgoing: ExoPlayer,
        incoming: ExoPlayer,
        inGain: Float,
        targetIndex: Int,
    ) {
        try {
            if (targetIndex !in 0 until outgoing.mediaItemCount ||
                outgoing.getMediaItemAt(targetIndex).mediaId != incoming.currentMediaItem?.mediaId
            ) {
                abortInto(outgoing, incoming)
                return
            }

            val before = ArrayList<MediaItem>(targetIndex.coerceAtLeast(0))
            for (i in 0 until targetIndex.coerceAtMost(outgoing.mediaItemCount)) {
                before.add(outgoing.getMediaItemAt(i))
            }
            val after = ArrayList<MediaItem>()
            for (i in (targetIndex + 1) until outgoing.mediaItemCount) {
                after.add(outgoing.getMediaItemAt(i))
            }

            if (after.isNotEmpty()) incoming.addMediaItems(after)
            // Prepended last, so the indices used above stay valid while the
            // trailing items are appended.
            if (before.isNotEmpty()) incoming.addMediaItems(0, before)

            incoming.repeatMode = outgoing.repeatMode
            incoming.shuffleModeEnabled = outgoing.shuffleModeEnabled
            incoming.volume = inGain * duckGain

            movedListener?.let {
                outgoing.removeListener(it)
                incoming.addListener(it)
            }
            active = incoming
            onActiveChanged(incoming)

            // Only after the session has moved: stopping the outgoing player
            // first would publish a stopped state through it on the way out.
            outgoing.stop()
            outgoing.clearMediaItems()
            outgoing.volume = 0f
        } catch (e: Exception) {
            Log.e(TAG, "Swap failed; falling back to the outgoing player", e)
            runCatching {
                incoming.stop()
                incoming.clearMediaItems()
                outgoing.volume = gainFor(outgoing) * duckGain
            }
        } finally {
            fadingIntoId = null
            pendingTargetIndex = null
        }
    }

    /**
     * Give up on an overlap that is already running and restore [outgoing].
     *
     * Separate from [cancelTransition] because it runs from inside the fade
     * coroutine, where cancelling the job would cancel the caller.
     */
    private fun abortInto(outgoing: ExoPlayer, incoming: ExoPlayer) {
        fadingIntoId = null
        pendingTargetIndex = null
        runCatching {
            incoming.stop()
            incoming.clearMediaItems()
            incoming.volume = 0f
            outgoing.volume = gainFor(outgoing) * duckGain
        }
    }

    /**
     * Stop a transition and leave the active player as it was.
     *
     * Used when something makes the pending transition wrong - the user skips,
     * the queue changes under it, playback pauses.
     */
    fun cancelTransition() {
        val job = fadeJob ?: return
        if (!job.isActive) return
        job.cancel()
        fadeJob = null
        fadingIntoId = null
        pendingTargetIndex = null
        runCatching {
            standby.stop()
            standby.clearMediaItems()
            standby.volume = 0f
            active.volume = gainFor(active) * duckGain
        }
    }

    /** Both engines share one id, or an external equalizer attaches to half. */
    fun setAudioSessionId(sessionId: Int) {
        playerA.audioSessionId = sessionId
        playerB.audioSessionId = sessionId
    }

    fun release() {
        fadeJob?.cancel()
        runCatching { playerA.release() }
        runCatching { playerB.release() }
    }

    companion object {
        private const val TAG = "CrossfadeEngine"

        /**
         * Roughly one frame. Fine enough that the ramp is inaudible - the old
         * one-second progress tick gave a three second fade about three steps -
         * and coarse enough not to be a hot loop.
         */
        private const val RAMP_INTERVAL_MS = 16L

        /** A fade shorter than this is not worth the swap; cut instead. */
        private const val MIN_FADE_MS = 250L

        /** Never let the fade run into the track's own end. */
        private const val END_GUARD_MS = 250L

        /** A prepared, warmed next item should become ready almost instantly. */
        private const val STANDBY_READY_TIMEOUT_MS = 1_500L
    }
}
