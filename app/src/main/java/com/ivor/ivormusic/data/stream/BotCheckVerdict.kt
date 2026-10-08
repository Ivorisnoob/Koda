package com.ivor.ivormusic.data.stream

import com.ivor.ivormusic.util.KLog

/**
 * Whether YouTube's bot check recently refused a stream in a way no fresh
 * identity could fix: a just-minted visitorData was refused as well, or
 * NewPipe (which mints a token of its own for each client) was refused
 * alongside the direct chain. That is a verdict on the address, not on a
 * token.
 *
 * [verified September 2026] A tester's signed-in session hit this at the IP
 * level: a remint, then both clients refused again within 300ms, three times
 * across two videos. Account cookies cannot help; the stream clients reject
 * them (see `docs/youtube-data.md`).
 *
 * What the verdict changes is only the *automatic* work that would repeat the
 * refusal: remint-and-retry, playback retries, speculative prefetch. A request
 * the user makes still goes out, as with `YouTubeRateLimit`.
 *
 * Process-wide, and part of the identity rather than new state: it is the
 * verdict on that identity, and MusicService, the video players and downloads
 * each hold their own repository instance.
 */
internal object BotCheckVerdict {
    private const val HOLD_MS = 3 * 60 * 1000L

    @Volatile private var refusedAtMs: Long = 0L

    /** Never a reason to refuse a request the user made. */
    fun isActive(): Boolean {
        val at = refusedAtMs
        if (at == 0L) return false
        // A negative age is a clock that moved backwards: expire the verdict
        // rather than pin it.
        return System.currentTimeMillis() - at in 0 until HOLD_MS
    }

    fun note(videoId: String) {
        if (!isActive()) {
            KLog.w(
                STREAM_TAG,
                "Bot check refused videoId=$videoId with a fresh identity; " +
                    "holding automatic retries and prefetch for ${HOLD_MS / 1000}s",
            )
        }
        refusedAtMs = System.currentTimeMillis()
    }

    /** A stream resolved, so the address is being served again. */
    fun clearOnSuccess() {
        if (refusedAtMs != 0L) {
            refusedAtMs = 0L
            KLog.i(STREAM_TAG, "Stream resolved; bot-check verdict cleared")
        }
    }

    /** The identity or the network changed, so the verdict describes neither. */
    fun reset() {
        refusedAtMs = 0L
    }
}
