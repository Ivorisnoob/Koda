package com.ivor.ivormusic.data.youtube

import android.content.Context
import com.ivor.ivormusic.data.TrackLoudnessStore

/**
 * What a `/player` response carries besides its streams, kept as a side
 * effect of resolving: the caption tracklist and the track's loudness.
 */
internal class PlayerResponseHarvest(
    private val context: Context,
    private val captionTracks: CaptionTracks,
) {
    /**
     * Keep what a `/player` response carries besides its streams: the caption
     * tracklist, so a later CC tap is free, and the track's loudness.
     */
    fun harvestPlayerResponse(videoId: String, root: org.json.JSONObject) {
        captionTracks.cacheCaptionTracks(videoId, parseCaptionTracks(root))
        cacheTrackLoudness(videoId, playerLoudnessDb(root))
    }

    /**
     * `playerConfig.audioConfig.loudnessDb`: how far the track's master sits
     * above YouTube's -14 LKFS target, so the playback correction is a gain of
     * the negation. See [TrackLoudnessStore]. Present on every OK response
     * probed (August 2026), but a missing key must read as unknown rather than
     * 0.0, which is a real measurement meaning "already at target".
     */
    private fun playerLoudnessDb(root: org.json.JSONObject): Float? =
        root.optJSONObject("playerConfig")
            ?.optJSONObject("audioConfig")
            ?.let { audio ->
                if (audio.has("loudnessDb")) audio.optDouble("loudnessDb").toFloat() else null
            }
            ?.takeIf { it.isFinite() }

    /**
     * Persist the track's loudness alongside the captions harvested from the
     * same response.
     *
     * Written here rather than at the caller because this is the one place
     * every `/player` response passes through, and because a song that is
     * already fully cached never comes back this way - see
     * [TrackLoudnessStore] for why that makes persistence the point.
     */
    fun cacheTrackLoudness(videoId: String, loudnessDb: Float?) {
        TrackLoudnessStore.put(context, videoId, loudnessDb ?: return)
    }
}
