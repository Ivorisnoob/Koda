package com.ivor.ivormusic.service

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * The player the media session sees: the engine, with a duration that holds
 * still while a song plays.
 *
 * Media3 re-sends the session's whole metadata, cover bitmap included, every
 * time the reported duration differs from the last one it sent [verified
 * October 2026 against media3-session 1.11.0's
 * MediaSessionLegacyStub.updateMetadataIfChanged]. A streamed song's duration
 * is refined as its chunks load, so the session re-sent the cover every few
 * seconds, and apps reading the session (Essentials' island, AOD wallpaper)
 * redrew it each time as new artwork. Here a duration is kept per song until
 * the real one moves by more than [SLACK_MS], which a different song, stream
 * or seek map does, and chunk-by-chunk refinement does not.
 */
internal class SteadyDurationPlayer(player: Player) : ForwardingPlayer(player) {
    private var heldKey: Any? = null
    private var heldDurationMs = C.TIME_UNSET

    override fun getDuration(): Long = steady(super.getDuration())

    override fun getContentDuration(): Long =
        if (isPlayingAd) super.getContentDuration() else steady(super.getContentDuration())

    private fun steady(durationMs: Long): Long {
        val key = currentMediaItemIndex to currentMediaItem?.mediaId
        if (key != heldKey || durationMs == C.TIME_UNSET || heldDurationMs == C.TIME_UNSET ||
            kotlin.math.abs(durationMs - heldDurationMs) > SLACK_MS
        ) {
            heldKey = key
            heldDurationMs = durationMs
        }
        return heldDurationMs
    }

    private companion object {
        const val SLACK_MS = 2_000L
    }
}
