package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.PlaybackRecoveryBudget

/** What a playback failure looked like, and what was true when it happened. */
data class VideoFailure(
    /** The codec lost its surface or was reclaimed; see `isTransientRendererError`. */
    val isRendererError: Boolean,
    /** The URL or container failed; see `isRecoverableSourceError`. */
    val isSourceError: Boolean,
    val httpStatus: Int?,
    /** An HDR rendition is playing and an SDR one is on offer. */
    val hasSdrFallback: Boolean,
    val networkAllowed: Boolean,
    val isLocalPlayback: Boolean,
    val isBotCheckActive: Boolean,
    val queueHasNext: Boolean,
)

/** What to do about a [VideoFailure]. */
enum class VideoRecoveryStep {
    /** Reload the SDR rendition at the same position. */
    FALL_BACK_TO_SDR,

    /** Re-prepare in place, keeping the position. */
    RE_PREPARE,

    /** Keep the item and say the cached part ended; Retry continues from here. */
    HOLD_OFFLINE,

    /** Fetch new URLs, replacing the visitor token first after a 403. */
    RE_RESOLVE,

    /** Step over this entry of the playlist. */
    SKIP_IN_QUEUE,

    /** Nothing left to try silently: put up the error overlay. */
    SHOW_ERROR,
}

/**
 * Decides what a playback failure gets, and counts what has been spent. The
 * order is the behaviour; each step's reason is on [next].
 *
 * Main-thread owned, like the player it guards. Times come from a monotonic
 * clock (`SystemClock.elapsedRealtime()`).
 */
class VideoRecoveryPolicy {
    /** Renderer retries spent since playback last reached READY. */
    var rendererRetries = 0
        private set

    /** Videos skipped in a row; reset the moment anything actually plays. */
    var queueSkips = 0
        private set

    private var hdrFallbackUsed = false

    private val sourceBudget = PlaybackRecoveryBudget(
        maxAttempts = MAX_SOURCE_RETRIES,
        refundAfterMs = SOURCE_RETRY_REFUND_MS,
    )

    /** Source re-resolves spent and not yet given back. */
    val sourceRetries: Int get() = sourceBudget.attemptsUsed

    /** Choose the step for [failure] at [nowMs], spending what it costs. */
    fun next(failure: VideoFailure, nowMs: Long): VideoRecoveryStep {
        // Once per video, and not for a 403. HDR and SDR come from one visionOS
        // response, signed under one token, so a refused HDR URL means the SDR
        // one beside it is refused too: reloading it only failed again and
        // spent a second token replacement on the way. RE_RESOLVE fetches the
        // whole ladder instead, and this stays armed for a failure HDR itself
        // causes.
        if (failure.hasSdrFallback && !hdrFallbackUsed && failure.httpStatus != 403) {
            hdrFallbackUsed = true
            return VideoRecoveryStep.FALL_BACK_TO_SDR
        }

        // A renderer failure is not a broken stream, so do not dead-end the
        // player on an overlay the user cannot dismiss.
        if (failure.isRendererError && rendererRetries < MAX_RENDERER_RETRIES) {
            rendererRetries++
            return VideoRecoveryStep.RE_PREPARE
        }

        // CacheDataSource serves every contiguous byte it has before asking
        // upstream. The cached part was valid playback, and throwing it away
        // turns a temporary connection loss into a blank player.
        if (failure.isSourceError && !failure.networkAllowed) {
            return VideoRecoveryStep.HOLD_OFFLINE
        }

        // A source failure means the URL is dead, not the video: re-resolving
        // is the only thing that can help. Not while the bot check's verdict
        // stands: the re-resolve would be refused on the same address, at the
        // cost of every fallback client. The overlay's Retry still asks,
        // because the user did.
        if (!failure.isLocalPlayback &&
            failure.isSourceError &&
            !failure.isBotCheckActive &&
            sourceBudget.tryConsume(nowMs)
        ) {
            return VideoRecoveryStep.RE_RESOLVE
        }

        // Both budgets are spent, so this video is genuinely unplayable. In a
        // playlist that is almost always one deleted or private entry among
        // many, and it must not end the run - but a network that has gone away
        // fails every video too, and racing to the end of the playlist on that
        // is worse than stopping.
        if (failure.queueHasNext && queueSkips < MAX_QUEUE_SKIPS) {
            queueSkips++
            return VideoRecoveryStep.SKIP_IN_QUEUE
        }

        return VideoRecoveryStep.SHOW_ERROR
    }

    /**
     * Playback reached READY. The source budget is not refilled here: a stream
     * refused after its opening megabyte reaches READY first, and refilling on
     * READY is what made that loop. [onHealthy] gives it back instead.
     */
    fun onReady() {
        rendererRetries = 0
        queueSkips = 0
    }

    /** Playback is going well at [nowMs]; see [PlaybackRecoveryBudget]. */
    fun onHealthy(nowMs: Long) = sourceBudget.onHealthy(nowMs)

    /** The user pressed Retry, or the network changed under a refusal. */
    fun onRetry() {
        rendererRetries = 0
        sourceBudget.reset()
    }

    /** A different video opened. */
    fun onNewVideo() {
        rendererRetries = 0
        sourceBudget.reset()
        hdrFallbackUsed = false
    }

    /** The user chose where to go in or out of a queue, so the run starts over. */
    fun onQueueMoved() {
        queueSkips = 0
    }

    companion object {
        /** Silent re-prepare attempts before a renderer error reaches the UI. */
        const val MAX_RENDERER_RETRIES = 2

        /**
         * Silent re-resolve attempts before a source error reaches the UI. One
         * is enough: the first pass already remints a rejected visitorData and
         * fetches brand-new URLs, so a second failure means the video really is
         * unplayable and the user should get the overlay instead of a spinner.
         */
        const val MAX_SOURCE_RETRIES = 1

        /**
         * Healthy playback after a re-resolve before its attempt comes back.
         * Far longer than a stream refused after its opening plays for, and
         * far shorter than a URL takes to expire.
         */
        const val SOURCE_RETRY_REFUND_MS = 60_000L

        /**
         * Unplayable videos stepped over in a row before the error overlay wins.
         *
         * Three, because that is comfortably more than the run of dead entries a
         * real playlist has in one place and far fewer than it takes to burn
         * through a playlist when the network is the thing that is broken.
         */
        const val MAX_QUEUE_SKIPS = 3
    }
}
