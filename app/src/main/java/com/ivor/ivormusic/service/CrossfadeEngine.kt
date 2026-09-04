package com.ivor.ivormusic.service

import com.ivor.ivormusic.util.KLog

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import android.os.SystemClock
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
    /** Per-player transition filter. Zero must be a true bypass. */
    private val setFilterSweep: (player: ExoPlayer, amount: Float) -> Unit = { _, _ -> },
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
    private var tempoReleaseJob: Job? = null

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
    private var shuffleEnabled = false
    private var shuffleSeed = 0L
    private var repeatMode = Player.REPEAT_MODE_OFF

    /**
     * One shuffle permutation shared by both engines. Copying only the Boolean
     * makes each incoming ExoPlayer generate a fresh order at every crossfade,
     * which puts already-played songs back into the future.
     */
    fun setShuffleState(enabled: Boolean, seed: Long) {
        shuffleEnabled = enabled
        shuffleSeed = seed
        applyPlaybackOrder(playerA)
        applyPlaybackOrder(playerB)
    }

    fun setRepeatMode(mode: Int) {
        repeatMode = mode
        playerA.repeatMode = mode
        playerB.repeatMode = mode
    }

    /** Rebuild the audible player's permutation after its queue was edited. */
    fun refreshActiveShuffleOrder() {
        applyPlaybackOrder(active)
    }

    fun setPauseAtEndOfMediaItems(enabled: Boolean) {
        playerA.pauseAtEndOfMediaItems = enabled
        playerB.pauseAtEndOfMediaItems = enabled
    }

    private fun applyPlaybackOrder(target: ExoPlayer) {
        target.setShuffleOrder(
            ShuffleOrder.DefaultShuffleOrder(target.mediaItemCount, shuffleSeed)
        )
        target.shuffleModeEnabled = shuffleEnabled
        target.repeatMode = repeatMode
    }

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
        incomingStartMs: Long = 0L,
        incomingSpeed: Float = 1f,
        filterSweepStrength: Float = 0f,
        /** Natural transitions prepare early, then begin at this remainder. */
        startAtRemainingMs: Long? = null,
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

        // What the overlap is fading *out* of. Every index below addresses the
        // queue as it stands right now, so if the outgoing player leaves this
        // track the whole plan is stale - see [runFade] and [completeSwap].
        // No outgoing track means there is nothing to overlap and nothing to
        // anchor on; the caller's ordinary advance is the right answer.
        val outgoingId = outgoing.currentMediaItem?.mediaId ?: return false

        return try {
            fadingIntoId = nextItem.mediaId
            pendingTargetIndex = targetIndex
            // One item only. The rest of the queue is spliced around it at swap
            // time, from the live timeline rather than a stale snapshot.
            incoming.setMediaItem(nextItem, incomingStartMs.coerceAtLeast(0L))
            incoming.playbackParameters = PlaybackParameters(
                incomingSpeed.coerceIn(MIN_TRANSITION_SPEED, MAX_TRANSITION_SPEED), 1f
            )
            incoming.volume = 0f
            incoming.prepare()
            incoming.playWhenReady = false

            fadeJob = scope.launch {
                runFade(
                    outgoing, incoming, fadeMs, targetIndex, outgoingId,
                    filterSweepStrength.coerceIn(0f, 1f),
                    startAtRemainingMs,
                )
            }.also { job ->
                job.invokeOnCompletion {
                    if (fadeJob === job) fadeJob = null
                }
            }
            true
        } catch (e: Exception) {
            KLog.e(TAG, "Could not start transition", e)
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
        outgoingId: String,
        filterSweepStrength: Float,
        startAtRemainingMs: Long?,
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
                KLog.w(TAG, "Standby was not ready in time; abandoning the overlap")
                abortInto(outgoing, incoming)
                return
            }

            if (startAtRemainingMs != null) {
                while (outgoing.duration - outgoing.currentPosition > startAtRemainingMs) {
                    currentCoroutineContext().ensureActive()
                    if (!outgoing.isPlaying || incoming.playbackState != Player.STATE_READY) {
                        abortInto(outgoing, incoming)
                        return
                    }
                    delay(RAMP_INTERVAL_MS)
                }
            }

            val outGain = gainFor(outgoing)
            val inGain = gainFor(incoming)
            val incomingBeforeStart = incoming.currentPosition
            incoming.playWhenReady = true

            // STATE_READY means decoded data exists, not that the audio sink's
            // clock has begun. Starting the fade on READY alone makes the
            // incoming beat device-latency late and can create a dip. Keep the
            // outgoing track untouched until the incoming position advances.
            val clockStarted = withTimeoutOrNull(INCOMING_CLOCK_TIMEOUT_MS) {
                while (!incoming.isPlaying ||
                    incoming.currentPosition <= incomingBeforeStart + MIN_CLOCK_ADVANCE_MS
                ) {
                    currentCoroutineContext().ensureActive()
                    if (incoming.playerError != null ||
                        incoming.playbackState == Player.STATE_IDLE ||
                        incoming.playbackState == Player.STATE_ENDED
                    ) return@withTimeoutOrNull false
                    delay(RAMP_INTERVAL_MS)
                }
                true
            } == true
            if (!clockStarted) {
                KLog.w(TAG, "Incoming playback clock did not start; abandoning the overlap")
                abortInto(outgoing, incoming)
                return
            }

            val remaining = outgoing.duration - outgoing.currentPosition
            val fadeMs = requestedFadeMs.coerceAtMost(remaining - END_GUARD_MS)
            if (fadeMs < MIN_FADE_MS) {
                abortInto(outgoing, incoming)
                return
            }

            val outgoingStartPosition = outgoing.currentPosition
            val incomingStartPosition = incoming.currentPosition
            val outgoingSpeed = outgoing.playbackParameters.speed.coerceAtLeast(0.01f)
            val incomingSpeed = incoming.playbackParameters.speed.coerceAtLeast(0.01f)
            var incomingStallStartedMs = 0L
            var maxClockDriftMs = 0L

            while (scope.isActive) {
                currentCoroutineContext().ensureActive()
                // If the audible player buffers, pause the muted/quiet player
                // so it cannot run ahead. If only the incoming player buffers,
                // hold the curve briefly with the outgoing track still audible;
                // a longer stall abandons the overlap instead of fading toward
                // a player that is not producing audio.
                if (outgoing.playWhenReady && !outgoing.isPlaying && incoming.isPlaying) {
                    incoming.playWhenReady = false
                } else if (outgoing.isPlaying && !incoming.playWhenReady) {
                    incoming.playWhenReady = true
                }

                if (outgoing.isPlaying && incoming.playWhenReady && !incoming.isPlaying) {
                    if (incomingStallStartedMs == 0L) {
                        incomingStallStartedMs = SystemClock.elapsedRealtime()
                    } else if (SystemClock.elapsedRealtime() - incomingStallStartedMs >=
                        MAX_INCOMING_STALL_MS
                    ) {
                        KLog.w(TAG, "Incoming engine stalled mid-fade; abandoning the overlap")
                        abortInto(outgoing, incoming)
                        return
                    }
                } else {
                    incomingStallStartedMs = 0L
                }

                val outgoingElapsedMs = (
                    (outgoing.currentPosition - outgoingStartPosition).coerceAtLeast(0L) /
                        outgoingSpeed
                    ).toLong()
                val incomingElapsedMs = (
                    (incoming.currentPosition - incomingStartPosition).coerceAtLeast(0L) /
                        incomingSpeed
                    ).toLong()
                val clockDriftMs = kotlin.math.abs(outgoingElapsedMs - incomingElapsedMs)
                maxClockDriftMs = maxOf(maxClockDriftMs, clockDriftMs)
                // The quieter player determines safe progress. In particular,
                // the outgoing side never disappears ahead of incoming audio.
                val elapsedMs = minOf(outgoingElapsedMs, incomingElapsedMs)
                val t = (elapsedMs.toFloat() / fadeMs).coerceIn(0f, 1f)

                // Equal power: cos^2 + sin^2 == 1, so the summed energy is flat
                // across the transition instead of dipping in the middle.
                val angle = t * (Math.PI.toFloat() / 2f)
                outgoing.volume = outGain * cos(angle) * duckGain
                incoming.volume = inGain * sin(angle) * duckGain
                setFilterSweep(outgoing, filterSweepStrength * t)

                if (t >= 1f) break
                // The queue was replaced or jumped under the overlap - a tap on
                // another song in the list is the ordinary way this happens.
                // Every index in the plan now addresses a different track, and
                // the position arithmetic above is measuring a song that is no
                // longer playing, so `t` would sit at zero and this would spin
                // for the rest of the new track. Worse than the wasted work:
                // `isFading` stays true, which blocks the *correct* transition
                // and leaves `pendingTargetIndex` pointing into a queue that no
                // longer exists, so the next Previous/Next jumps somewhere
                // arbitrary.
                if (outgoing.currentMediaItem?.mediaId != outgoingId) {
                    KLog.w(TAG, "Queue moved under the overlap; abandoning it")
                    abortInto(outgoing, incoming)
                    return
                }
                // The outgoing player ending early (a short file, an error)
                // must not leave this spinning against a frozen position.
                if (outgoing.playbackState == Player.STATE_ENDED) break
                // A fatal error drives a player to STATE_IDLE, and the service's
                // listener is not on the standby, so nothing else would notice.
                // Swapping onto a dead engine would be silence with a running
                // progress bar, so abandon and let the outgoing track finish.
                if (incoming.playbackState == Player.STATE_IDLE) {
                    KLog.w(TAG, "Standby engine died mid-fade; abandoning the overlap")
                    abortInto(outgoing, incoming)
                    return
                }
                delay(RAMP_INTERVAL_MS)
            }
            currentCoroutineContext().ensureActive()
            KLog.d(TAG, "Transition clocks completed with max drift=${maxClockDriftMs}ms")
            completeSwap(outgoing, incoming, gainFor(incoming), targetIndex, outgoingId)
        } catch (e: CancellationException) {
            abortInto(outgoing, incoming)
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Transition failed; keeping the outgoing player", e)
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
        outgoingId: String,
    ) {
        try {
            // The outgoing track is the anchor for everything below. A queue
            // replaced during the fade - the user tapping another song in the
            // list they are listening to - moves it, and the id check on
            // [targetIndex] alone does not catch that: reopening the same
            // playlist puts the same song back at the same index, so the guard
            // passes and the session is handed to the standby playing a track
            // nobody asked for. Anchoring on what the fade started from is what
            // makes that case impossible rather than merely unlikely.
            if (outgoing.currentMediaItem?.mediaId != outgoingId ||
                targetIndex !in 0 until outgoing.mediaItemCount ||
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

            applyPlaybackOrder(incoming)
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
            outgoing.playbackParameters = PlaybackParameters.DEFAULT
            setFilterSweep(outgoing, 0f)
            releaseTempo(incoming)
        } catch (e: Exception) {
            KLog.e(TAG, "Swap failed; falling back to the outgoing player", e)
            runCatching {
                incoming.stop()
                incoming.clearMediaItems()
                incoming.playbackParameters = PlaybackParameters.DEFAULT
                setFilterSweep(incoming, 0f)
                setFilterSweep(outgoing, 0f)
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
            incoming.playbackParameters = PlaybackParameters.DEFAULT
            setFilterSweep(incoming, 0f)
            setFilterSweep(outgoing, 0f)
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
        tempoReleaseJob?.cancel()
        tempoReleaseJob = null
        active.playbackParameters = PlaybackParameters.DEFAULT
        val job = fadeJob
        if (job?.isActive == true) job.cancel()
        fadeJob = null
        fadingIntoId = null
        pendingTargetIndex = null
        runCatching {
            standby.stop()
            standby.clearMediaItems()
            standby.volume = 0f
            standby.playbackParameters = PlaybackParameters.DEFAULT
            setFilterSweep(standby, 0f)
            setFilterSweep(active, 0f)
            active.volume = gainFor(active) * duckGain
        }
    }

    /** Ease the small tempo correction back to the source tempo after mixing. */
    private fun releaseTempo(player: ExoPlayer) {
        tempoReleaseJob?.cancel()
        val start = player.playbackParameters.speed
        if (kotlin.math.abs(start - 1f) < 0.001f) {
            player.playbackParameters = PlaybackParameters.DEFAULT
            return
        }
        tempoReleaseJob = scope.launch {
            val steps = (TEMPO_RELEASE_MS / TEMPO_RELEASE_STEP_MS).toInt()
            repeat(steps) { index ->
                val t = (index + 1f) / steps
                player.playbackParameters = PlaybackParameters(start + (1f - start) * t, 1f)
                delay(TEMPO_RELEASE_STEP_MS)
            }
            player.playbackParameters = PlaybackParameters.DEFAULT
        }
    }

    /** Both engines share one id, or an external equalizer attaches to half. */
    fun setAudioSessionId(sessionId: Int) {
        playerA.audioSessionId = sessionId
        playerB.audioSessionId = sessionId
    }

    fun release() {
        fadeJob?.cancel()
        tempoReleaseJob?.cancel()
        setFilterSweep(playerA, 0f)
        setFilterSweep(playerB, 0f)
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
        private const val INCOMING_CLOCK_TIMEOUT_MS = 750L
        private const val MIN_CLOCK_ADVANCE_MS = 8L
        private const val MAX_INCOMING_STALL_MS = 350L
        private const val MIN_TRANSITION_SPEED = 0.96f
        private const val MAX_TRANSITION_SPEED = 1.04f
        private const val TEMPO_RELEASE_MS = 2_500L
        private const val TEMPO_RELEASE_STEP_MS = 100L
    }
}
