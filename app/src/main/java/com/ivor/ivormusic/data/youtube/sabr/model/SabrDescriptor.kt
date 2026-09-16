package com.ivor.ivormusic.data.youtube.sabr.model

import java.util.Collections

/** An identity snapshot, not cookies. Visitor/token changes advance [attestationGeneration]. */
internal data class SabrIdentity(
    val profileId: String,
    val loginGeneration: Long,
    val attestationGeneration: Long,
)

/** Renditions sharing an itag can still carry different languages or revised media bytes. */
internal data class SabrFormatId(
    val itag: Int,
    val lastModified: Long,
    val xtags: String? = null,
    val audioTrackId: String? = null,
) {
    init {
        require(itag > 0) { "Invalid SABR itag" }
        require(lastModified >= 0) { "Invalid SABR rendition version" }
    }

    /** Length-prefix variable fields so delimiters in opaque tags cannot alias cache entries. */
    fun cacheKey(videoId: String, sequence: Int? = null): String {
        require(sequence == null || sequence > 0) { "SABR media sequences start at one" }
        fun field(value: String?) = value?.let { "${it.length}:$it" } ?: "-1:"
        return "sabr:${field(videoId)}:$itag:$lastModified:${field(xtags)}:" +
            "${field(audioTrackId)}:${sequence ?: "init"}"
    }
}

internal class SabrFormat(
    val id: SabrFormatId,
    val mimeType: String,
    val bitrate: Int,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Double = 0.0,
    val audioTrackDisplayName: String? = null,
    val isDrc: Boolean = false,
    val initializationUrl: String? = null,
    val initializationRange: LongRange? = null,
    val indexRange: LongRange? = null,
) {
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")

    init {
        require(isAudio || isVideo) { "Unsupported SABR track type" }
        require(bitrate >= 0 && durationMs >= 0 && width >= 0 && height >= 0) {
            "Invalid SABR format dimensions or duration"
        }
        require(frameRate.isFinite() && frameRate >= 0) { "Invalid SABR frame rate" }
        listOfNotNull(initializationRange, indexRange).forEach {
            require(it.first >= 0 && !it.isEmpty()) { "Invalid SABR byte range" }
        }
    }

    override fun toString() = "SabrFormat(itag=${id.itag}, audio=$isAudio, video=$isVideo)"
}

/**
 * Immutable, memory-only player-response snapshot. No mutable request counters,
 * cookies or contexts belong here, and there is deliberately no serialization.
 * Token bytes and the format list are copied at the boundary.
 */
internal class SabrDescriptor(
    val videoId: String,
    val cpn: String,
    val clientVersion: String,
    val visitorData: String,
    val serverAbrStreamingUrl: String,
    val ustreamerConfig: String,
    val identity: SabrIdentity,
    val resolvedAtMs: Long,
    val expiresAtMs: Long,
    formats: List<SabrFormat>,
    poToken: ByteArray,
) {
    val formats: List<SabrFormat> = Collections.unmodifiableList(ArrayList(formats))
    private val token = poToken.copyOf()
    val poToken: ByteArray get() = token.copyOf()

    init {
        require(videoId.isNotBlank() && cpn.isNotBlank() && clientVersion.isNotBlank()) {
            "Incomplete SABR client identity"
        }
        require(visitorData.isNotBlank() && serverAbrStreamingUrl.isNotBlank() && ustreamerConfig.isNotBlank()) {
            "Incomplete SABR resolution"
        }
        require(resolvedAtMs >= 0 && expiresAtMs > resolvedAtMs) { "Invalid SABR lifetime" }
        require(this.formats.isNotEmpty() && this.formats.map { it.id }.distinct().size == this.formats.size) {
            "Missing or duplicate SABR formats"
        }
        require(token.isNotEmpty()) { "Missing SABR attestation" }
    }

    fun isUsable(nowMs: Long, currentIdentity: SabrIdentity): Boolean =
        identity == currentIdentity && nowMs >= resolvedAtMs && nowMs < expiresAtMs

    override fun toString() = "SabrDescriptor(formats=${formats.size}, sensitiveFields=redacted)"
}
