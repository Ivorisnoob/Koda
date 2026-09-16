package com.ivor.ivormusic.data.youtube.sabr.session

import com.ivor.ivormusic.data.youtube.sabr.SabrFormatTimeline
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat

/**
 * One track of a transaction. [buffered] is the contiguous run of media sequences the
 * consumer really holds around the playhead, never an optimistic "everything before".
 * It needs the timeline to translate sequences into time.
 */
internal class SabrTrack(
    val format: SabrFormat,
    val timeline: SabrFormatTimeline? = null,
    val buffered: IntRange? = null,
) {
    init {
        if (buffered != null) {
            requireNotNull(timeline) { "Buffered SABR range needs a timeline" }
            require(!buffered.isEmpty() && buffered.first >= 1 && buffered.last <= timeline.endSequence) {
                "Buffered SABR range outside the timeline"
            }
        }
    }

    override fun toString() = "SabrTrack(itag=${format.id.itag}, buffered=$buffered)"
}

/** Immutable description of one transaction. Audio-only and video-only are both valid. */
internal class SabrRequest private constructor(
    tracks: List<SabrTrack>,
    val playerTimeMs: Long,
    val playbackRate: Float,
    /** False for preparation: formats are preferred, not declared as the active selection. */
    val selectsTracks: Boolean,
) {
    val tracks: List<SabrTrack> = tracks.toList()
    val audio: SabrTrack? = this.tracks.singleOrNull { it.format.isAudio }
    val video: SabrTrack? = this.tracks.singleOrNull { it.format.isVideo }

    init {
        require(this.tracks.isNotEmpty()) { "SABR request needs a track" }
        require(this.tracks.count { it.format.isAudio } <= 1 && this.tracks.count { it.format.isVideo } <= 1) {
            "SABR request allows one audio and one video track"
        }
        require(playerTimeMs >= 0) { "Negative SABR player time" }
        require(playbackRate.isFinite() && playbackRate > 0f) { "Invalid SABR playback rate" }
    }

    companion object {
        fun preparation(playerTimeMs: Long, formats: List<SabrFormat>) =
            SabrRequest(formats.map { SabrTrack(it) }, playerTimeMs, 1f, selectsTracks = false)

        /** [playbackRate] is the player's actual speed, which the server uses to pace media. */
        fun playback(playerTimeMs: Long, playbackRate: Float, tracks: List<SabrTrack>) =
            SabrRequest(tracks, playerTimeMs, playbackRate, selectsTracks = true)
    }
}
