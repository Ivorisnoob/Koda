package com.ivor.ivormusic.data

/**
 * How many silent re-resolves one video gets, and when they are given back.
 *
 * [scar] The video player refilled its budget whenever playback reached
 * READY. A googlevideo refusal that lands after the opening window - the
 * first megabyte or so served, then 403 for every byte past it
 * (docs/youtube-data.md) - reaches READY on that first megabyte, so each
 * recovery refilled the budget it had just spent and the video looped:
 * replace the visitorData, resolve, play a second, fail, for as long as the
 * player stayed open. Attempts now come back only once playback has stayed
 * healthy for [refundAfterMs] since the last one, which a refusal after the
 * opening never does, while a video that recovers and plays on still gets its
 * attempt back for the next expired URL or network drop hours later.
 *
 * Main-thread owned, like the player it guards. Times come from a monotonic
 * clock (`SystemClock.elapsedRealtime()`).
 */
internal class PlaybackRecoveryBudget(
    private val maxAttempts: Int,
    private val refundAfterMs: Long,
) {
    /** Attempts spent and not yet given back. */
    var attemptsUsed = 0
        private set
    private var lastAttemptAtMs = 0L

    /** Spend one attempt at [nowMs]; false when none is left. */
    fun tryConsume(nowMs: Long): Boolean {
        if (attemptsUsed >= maxAttempts) return false
        attemptsUsed++
        lastAttemptAtMs = nowMs
        return true
    }

    /** Playback is going well at [nowMs]; the attempts come back once it has stayed so. */
    fun onHealthy(nowMs: Long) {
        if (attemptsUsed > 0 && nowMs - lastAttemptAtMs >= refundAfterMs) attemptsUsed = 0
    }

    /** A new video, or the user pressed Retry. */
    fun reset() {
        attemptsUsed = 0
        lastAttemptAtMs = 0L
    }
}
