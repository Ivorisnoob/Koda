package com.ivor.ivormusic.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.data.AudioProfileStore
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Starts the overlap into the next song when the playing one reaches its fade
 * window, with the plan AutoMix makes from both songs' profiles or a plain
 * crossfade of the user's length.
 *
 * Runs while music plays, at [POLL_MS] rather than off the one-second progress
 * loop, which is too coarse to place the start of a fade and was what made the
 * old one step audibly. Manual skips do not come through here.
 *
 * Main-thread owned.
 *
 * @param prefetch asked for when the next song has no stream yet.
 */
@UnstableApi
internal class AutomaticCrossfades(
    private val scope: CoroutineScope,
    private val engine: () -> CrossfadeEngine,
    private val profiles: () -> AudioProfileStore,
    private val settings: () -> Settings,
    private val prefetch: () -> Unit,
) {
    /** What the decision reads, taken fresh on every poll. */
    data class Settings(
        val crossfade: Boolean,
        val autoMix: Boolean,
        val crossfadeDurationMs: Long,
        val playbackSpeed: Float,
        /** The sleep timer wants this song to be the last. */
        val stopAtEndOfSong: Boolean,
    )

    private var job: Job? = null

    /**
     * The outgoing and incoming occurrences of the last overlap started or
     * tried. A failed standby should not reopen its source every poll: that
     * pair advances normally, and a seek, mode change, new source or new queue
     * occurrence allows another attempt.
     */
    private var attempt: Pair<MediaItem, MediaItem>? = null

    /** Allow the current pair another attempt. */
    fun forgetAttempt() {
        attempt = null
    }

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(POLL_MS)
                poll()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun poll() {
        val settings = settings()
        val engine = engine()
        if (!settings.crossfade || engine.isFading) return
        val current = engine.active
        if (!current.isPlaying) return
        // Repeat-one means the "next" track is this one, and an overlap
        // of a track with itself is comb filtering, not a crossfade.
        if (current.repeatMode == Player.REPEAT_MODE_ONE) return
        // Starting the next song would be the app arguing with the sleep timer.
        if (settings.stopAtEndOfSong) return
        if (!current.hasNextMediaItem()) return

        val duration = current.duration
        if (duration <= 0) return
        val remaining = duration - current.currentPosition
        if (remaining <= 0) return

        val nextIndex = current.nextMediaItemIndex
        if (nextIndex !in 0 until current.mediaItemCount) return
        val nextItem = current.getMediaItemAt(nextIndex)
        val outgoingItem = current.currentMediaItem ?: return
        val alreadyAttempted = attempt?.let { (oldOutgoing, oldIncoming) ->
            oldOutgoing.isSameQueueItemAs(outgoingItem) &&
                oldIncoming.isSameQueueItemAs(nextItem) &&
                oldIncoming.localConfiguration?.uri == nextItem.localConfiguration?.uri
        } == true
        if (alreadyAttempted) return
        // An unresolved item has no stream to fade in. Let the normal
        // advance happen only as the degraded path; normally this
        // proactively resolves the real playback-order successor long
        // before the transition window.
        val nextUri = nextItem.localConfiguration?.uri
        if (nextUri == null || nextUri.toString().startsWith(MusicStreamResolver.PLACEHOLDER_PREFIX)) {
            prefetch()
            return
        }

        val plan = plan(current, outgoingItem, nextItem, duration, settings)
        // The prepare lead is how far out the transition may be held
        // ready; normally it equals the overlap, and only the silence
        // skip stretches it - the engine waits out the dead air and
        // fades where the music actually stopped.
        val prepareLeadMs = plan.effectivePrepareLeadMs
        if (!plan.shouldOverlap || remaining > prepareLeadMs + PREPARE_LEAD_MS) return

        val started = engine.startTransition(
            nextItem = nextItem,
            durationMs = plan.overlapMs,
            targetIndex = nextIndex,
            incomingStartMs = plan.incomingStartMs,
            incomingSpeed = plan.incomingSpeed,
            filterSweepStrength = plan.filterSweepStrength,
            startAtRemainingMs = prepareLeadMs,
            style = plan.style,
            echoDelayMs = plan.echoDelayMs,
        )
        if (!started) return
        attempt = outgoingItem to nextItem
        KLog.d(
            TAG,
            "Crossfade: ${plan.reason} ${plan.overlapMs}ms " +
                "lead=${plan.incomingStartMs} beatIn=${plan.incomingDownbeatDelayMs} " +
                "speed=${plan.incomingSpeed} style=${plan.style} " +
                "key=${plan.harmonicMatch} into ${nextItem.mediaId}"
        )
        // FALLBACK is three different outcomes in the planner: no
        // outgoing profile, no trusted common tempo, or no ending
        // found. What it was handed tells them apart.
        if (settings.autoMix && plan.reason == TransitionPlan.Reason.FALLBACK) {
            val out = profiles().peek(outgoingItem.mediaId)
            val inc = profiles().peek(nextItem.mediaId)
            KLog.d(
                TAG,
                "Crossfade: fallback inputs out=" +
                    (out?.let {
                        "bpm=${it.outroBpm}@${it.outroTempoConfidence} " +
                            "outro=${it.outroLeadMs} " +
                            "phrase=${it.phraseOutroLeadMs}@${it.phraseConfidence}"
                    } ?: "none") +
                    " in=" +
                    (inc?.let { "bpm=${it.bpm}@${it.tempoConfidence}" } ?: "none")
            )
        }
    }

    private fun plan(
        current: Player,
        outgoingItem: MediaItem,
        nextItem: MediaItem,
        duration: Long,
        settings: Settings,
    ): TransitionPlan {
        if (!settings.autoMix) {
            return TransitionPlan(
                overlapMs = settings.crossfadeDurationMs,
                incomingStartMs = 0L,
                reason = TransitionPlan.Reason.FALLBACK,
            )
        }
        val sameAlbum = outgoingItem.mediaMetadata.albumTitle
            ?.toString()?.takeIf { it.isNotBlank() }
            ?.let { album -> album == nextItem.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() } } == true
        return TransitionPlanner.plan(
            fallbackOverlapMs = AUTO_MIX_FALLBACK_OVERLAP_MS,
            maximumOverlapMs = AUTO_MIX_MAX_OVERLAP_MS,
            outgoing = profiles().peek(outgoingItem.mediaId),
            incoming = profiles().peek(nextItem.mediaId),
            outgoingDurationMs = duration,
            incomingDurationMs = nextItem.mediaMetadata.durationMs ?: 0L,
            preserveAlbumSequence = !current.shuffleModeEnabled && nextItem.isAlbumSuccessorOf(outgoingItem),
            preserveAbruptEnding = !current.shuffleModeEnabled && sameAlbum,
            baseSpeed = settings.playbackSpeed,
        )
    }

    private companion object {
        const val TAG = "MusicService"
        const val AUTO_MIX_FALLBACK_OVERLAP_MS = 3_000L
        const val AUTO_MIX_MAX_OVERLAP_MS = 15_000L

        /**
         * How often the watcher checks whether the outgoing track has entered
         * its fade window. Fine enough to place the start of a fade, which the
         * one-second progress tick never was.
         */
        const val POLL_MS = 200L

        /** How far ahead of its fade an overlap is prepared. */
        const val PREPARE_LEAD_MS = 1_500L
    }
}
