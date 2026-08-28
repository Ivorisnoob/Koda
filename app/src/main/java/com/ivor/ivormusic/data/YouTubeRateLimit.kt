package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide record of YouTube telling this device to slow down.
 *
 * **Why this is a seventh process-wide holder.** It meets the same bar the
 * other six do: a write taken on one surface has to be visible on another that
 * holds its own instance. A 429 is seen by whichever [YouTubeRepository]
 * happened to make the call - `MusicService`'s, the video ViewModel's, or
 * `HomeViewModel`'s - and the surface that has to *act* on it is almost never
 * the same one. Per-instance state would let the subscriptions tab launch a
 * 200-channel refresh one second after playback resolution was refused.
 *
 * **What arms it is deliberately narrow: HTTP 429, and nothing else.** A 403
 * from googlevideo is the GVS PO-Token verdict on `visitorData` and already has
 * its own recovery (`refreshVisitorDataAfterPlaybackFailure`); routing it here
 * would replace a working remint with a five-minute sulk. A 403 from an
 * InnerTube endpoint is usually "you may not read this", not "you are asking
 * too often". Only 429 means what this class exists to record, so only 429
 * arms it - see [note].
 *
 * **What the hold gates is also narrow, and this is the important half.** It
 * gates *fan-out* work only: the subscriptions feed refresh (one request per
 * followed channel), the avatar backfill, and the background upload check.
 * It must never gate playback resolution or anything else a user is actively
 * waiting on. Someone who presses play during a hold should still get a
 * request and, if it works, their music - refusing locally would turn a
 * server-side throttle into an app that looks broken. The point is to stop
 * spending the budget on work nobody asked for, so the work someone *did* ask
 * for still has a chance.
 */
/**
 * Thrown by fan-out work that was refused or abandoned because YouTube is
 * rate-limiting this device. Distinct from an ordinary failure so the UI can
 * say so instead of blaming the user's connection or reporting an empty feed.
 */
class YouTubeRateLimitedException(
    val remainingMs: Long,
) : Exception("Rate limited by YouTube; ${remainingMs / 1000}s remaining")

object YouTubeRateLimit {

    /**
     * How long to hold off after a 429 that carried no `Retry-After`. Long
     * enough that a refresh loop cannot walk straight back into the limit,
     * short enough that a user who waits out a message is not stuck with a
     * dead tab.
     */
    private const val DEFAULT_HOLD_MS = 5 * 60 * 1000L

    /**
     * Ceiling on a server-supplied `Retry-After`. YouTube has been seen to
     * send very long values; honouring one literally would disable the
     * subscriptions tab for hours with no way for the user to tell why.
     */
    private const val MAX_HOLD_MS = 30 * 60 * 1000L

    private val _heldUntil = MutableStateFlow(0L)

    /** Wall-clock millis until which fan-out work should stand down. */
    val heldUntil: StateFlow<Long> = _heldUntil.asStateFlow()

    /**
     * Record the outcome of an InnerTube or feed request.
     *
     * @return true when [code] was a rate limit, so the caller can abort rather
     * than fall back to a more expensive request.
     */
    fun note(code: Int, url: String, retryAfterHeader: String? = null): Boolean {
        if (code != 429) return false
        val holdMs = retryAfterHeader
            ?.trim()
            ?.toLongOrNull()
            ?.let { it * 1000L }
            ?.coerceIn(DEFAULT_HOLD_MS, MAX_HOLD_MS)
            ?: DEFAULT_HOLD_MS
        val until = System.currentTimeMillis() + holdMs
        // Never shorten an existing hold: two concurrent 429s must not let the
        // second one's shorter window undo the first one's longer one.
        if (until > _heldUntil.value) _heldUntil.value = until
        KLog.w(
            "YouTubeRateLimit",
            "HTTP 429 from $url - holding fan-out for ${holdMs / 1000}s",
        )
        return true
    }

    /** Whether fan-out work should stand down right now. */
    fun isHeld(): Boolean {
        val until = _heldUntil.value
        if (until == 0L) return false
        val now = System.currentTimeMillis()
        // A clock moved backwards (NTP correction, manual change) must not pin
        // the hold forever, so treat a wildly future deadline as expired.
        if (until - now > MAX_HOLD_MS) {
            _heldUntil.value = 0L
            return false
        }
        return now < until
    }

    /** Milliseconds left on the hold, or 0 when not held. */
    fun remainingMs(): Long =
        if (isHeld()) (_heldUntil.value - System.currentTimeMillis()).coerceAtLeast(0L) else 0L

    /** Clear the hold - used when a request succeeds after one has expired. */
    fun clear() {
        _heldUntil.value = 0L
    }
}
