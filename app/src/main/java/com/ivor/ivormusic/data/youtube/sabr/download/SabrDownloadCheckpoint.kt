package com.ivor.ivormusic.data.youtube.sabr.download

import org.json.JSONObject

/**
 * Durable per-track download progress for one SABR source (stage 9a). Segment
 * writes are ordered, so completion per track is always the prefix
 * 1..[completedThrough]; resume continues at `completedThrough + 1` with a
 * fresh session and the stored rendition identity (itag, lastModified, xtags,
 * audio track), which is why the identity rides along rather than just a
 * count. Serialized with org.json; unknown fields are ignored on read so a
 * newer writer never breaks an older reader.
 *
 * Persistence wiring (where these bytes live and when they are fsynced)
 * arrives with the download pipeline; this slice is the format plus its
 * rules. Designed from the stage 9 requirements, not from any upstream
 * downloader implementation.
 */
internal data class SabrTrackProgress(
    val itag: Int,
    val lastModified: Long,
    val xtags: String?,
    val audioTrackId: String?,
    val completedThrough: Int,
    val endSequence: Int,
) {
    init {
        require(itag > 0) { "Invalid SABR download itag" }
        require(lastModified >= 0) { "Invalid SABR rendition version" }
        require(completedThrough >= 0 && endSequence > 0) { "Invalid SABR download progress" }
        require(completedThrough <= endSequence) { "SABR download progress past its timeline" }
    }

    val isComplete: Boolean get() = completedThrough >= endSequence

    /**
     * Records one written segment. Only the next expected sequence advances
     * the frontier; gaps and duplicates leave it untouched, because ordered
     * writes never complete around a hole.
     */
    fun advance(sequence: Int): SabrTrackProgress =
        if (sequence == completedThrough + 1 && sequence <= endSequence) {
            copy(completedThrough = sequence)
        } else {
            this
        }

    fun toJson(): JSONObject = JSONObject()
        .put("itag", itag)
        .put("lastModified", lastModified)
        .put("xtags", xtags ?: JSONObject.NULL)
        .put("audioTrackId", audioTrackId ?: JSONObject.NULL)
        .put("completedThrough", completedThrough)
        .put("endSequence", endSequence)

    companion object {
        fun parse(json: JSONObject): SabrTrackProgress = SabrTrackProgress(
            itag = json.getInt("itag"),
            lastModified = json.getLong("lastModified"),
            xtags = json.opt("xtags")?.takeIf { it != JSONObject.NULL } as String?,
            audioTrackId = json.opt("audioTrackId")?.takeIf { it != JSONObject.NULL } as String?,
            completedThrough = json.getInt("completedThrough"),
            endSequence = json.getInt("endSequence"),
        )
    }
}

internal data class SabrDownloadCheckpoint(
    val videoId: String,
    val audio: SabrTrackProgress,
    val video: SabrTrackProgress?,
    val updatedAtMs: Long,
) {
    init {
        require(videoId.isNotBlank()) { "Missing SABR download video id" }
        require(updatedAtMs >= 0) { "Invalid SABR download checkpoint time" }
    }

    val isComplete: Boolean get() = audio.isComplete && (video == null || video.isComplete)

    fun toJson(): JSONObject = JSONObject()
        .put("videoId", videoId)
        .put("audio", audio.toJson())
        .put("video", video?.toJson() ?: JSONObject.NULL)
        .put("updatedAtMs", updatedAtMs)

    companion object {
        fun parse(raw: String): SabrDownloadCheckpoint {
            val json = JSONObject(raw)
            return SabrDownloadCheckpoint(
                videoId = json.getString("videoId"),
                audio = SabrTrackProgress.parse(json.getJSONObject("audio")),
                video = json.opt("video")?.takeIf { it != JSONObject.NULL }
                    ?.let { SabrTrackProgress.parse(it as JSONObject) },
                updatedAtMs = json.getLong("updatedAtMs"),
            )
        }
    }
}
