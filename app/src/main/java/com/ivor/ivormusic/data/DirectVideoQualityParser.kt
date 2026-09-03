package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioTrackType

/**
 * Parse the unciphered formats returned by a native InnerTube player client.
 *
 * This deliberately does not use NewPipe's itag table. That table in v0.26.5
 * ends before YouTube's HDR itags 330-337, which is why NewPipe can make the
 * successful visionOS request yet omit every HDR rendition from its public
 * Stream models.
 */
internal fun parseDirectVideoQualities(
    streamingData: JSONObject,
    includeHdr: Boolean,
): List<VideoQuality> {
    fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }

    val adaptive = streamingData.optJSONArray("adaptiveFormats")?.objects() ?: emptyList()
    val muxed = streamingData.optJSONArray("formats")?.objects() ?: emptyList()

    // Source shape comes from the largest format that declares dimensions so
    // vertical videos lay out correctly before the first decoded frame.
    val sourceAspect = (adaptive + muxed)
        .filter { it.optInt("width") > 0 && it.optInt("height") > 0 }
        .maxByOrNull { it.optInt("height") }
        ?.let { it.optInt("width").toFloat() / it.optInt("height").toFloat() }

    fun dynamicRange(format: JSONObject): VideoDynamicRange =
        youtubeVideoDynamicRange(
            qualityLabel = format.optString("qualityLabel"),
            transferCharacteristics = format.optJSONObject("colorInfo")
                ?.optString("transferCharacteristics"),
        )

    fun isHdrFormat(format: JSONObject): Boolean =
        dynamicRange(format) != VideoDynamicRange.SDR

    fun labelHeight(label: String): Int =
        label.takeWhile(Char::isDigit).toIntOrNull() ?: 0

    fun labelFps(label: String): Int =
        label.substringAfter("p", "").takeWhile(Char::isDigit).toIntOrNull() ?: 30

    // Progressive live endpoints are single segments. Live playback must stay
    // on the HLS manifest, and the manifest owns its own HDR/SDR adaptation.
    val hlsManifestUrl = streamingData.optString("hlsManifestUrl").takeIf(String::isNotBlank)
    if (hlsManifestUrl != null) {
        fun liveEntry(label: String) = VideoQuality(
            resolution = label,
            url = hlsManifestUrl,
            format = "HLS",
            isDASH = true,
            isLive = true,
            sourceAspectRatio = sourceAspect,
        )

        val ladder = adaptive
            .filter {
                it.optString("mimeType").startsWith("video/") && !isHdrFormat(it)
            }
            .mapNotNull { it.optString("qualityLabel").takeIf(String::isNotEmpty) }
            .distinct()
            .sortedWith(
                compareByDescending<String>(::labelHeight)
                    .thenByDescending(::labelFps)
            )

        return listOf(liveEntry("Auto")) + ladder.map(::liveEntry)
    }

    val directAudioFormats = adaptive.filter {
        it.optString("mimeType").startsWith("audio/") &&
            it.optString("url").isNotEmpty()
    }
    val typedAudio = directAudioFormats.map { it to directAudioTrackType(it) }
    val hasAlternateAudioTracks = typedAudio.any {
        it.second != null && it.second != AudioTrackType.ORIGINAL
    }
    val originalAudio = typedAudio.filter { it.second == AudioTrackType.ORIGINAL }
        .map { it.first }
        .ifEmpty { typedAudio.filter { it.second == null }.map { it.first } }
    val bestAudioUrl = originalAudio
        .maxWithOrNull(
            compareBy(
                { if (it.optString("mimeType").contains("mp4a")) 1 else 0 },
                { it.optInt("bitrate") },
            )
        )
        ?.optString("url")
        ?.takeIf(String::isNotEmpty)

    fun codecRank(mimeType: String): Int = when {
        mimeType.contains("avc1") -> 3
        mimeType.contains("vp9") || mimeType.contains("vp09") -> 2
        else -> 1
    }

    fun container(mimeType: String): String =
        mimeType.substringAfter("video/").substringBefore(';').ifEmpty { "mp4" }

    fun codec(mimeType: String): String? =
        mimeType.substringAfter("codecs=\"", "")
            .substringBefore('"')
            .takeIf(String::isNotBlank)

    val qualities = mutableListOf<VideoQuality>()

    if (bestAudioUrl != null) {
        adaptive
            .filter {
                it.optString("mimeType").startsWith("video/") &&
                    it.optString("url").isNotEmpty() &&
                    it.optString("qualityLabel").isNotEmpty() &&
                    (includeHdr || !isHdrFormat(it))
            }
            .groupBy {
                normalizedVideoQualityLabel(it.optString("qualityLabel")) to dynamicRange(it)
            }
            .forEach { (identity, formats) ->
                val (label, range) = identity
                val best = formats.maxWithOrNull(
                    compareBy({ codecRank(it.optString("mimeType")) }, { it.optInt("bitrate") })
                ) ?: return@forEach
                qualities += VideoQuality(
                    resolution = label,
                    url = best.optString("url"),
                    format = container(best.optString("mimeType")),
                    isDASH = false,
                    audioUrl = bestAudioUrl,
                    sourceAspectRatio = sourceAspect,
                    codec = codec(best.optString("mimeType")),
                    dynamicRange = range,
                    width = best.optInt("width"),
                    height = best.optInt("height"),
                    frameRate = best.optInt("fps"),
                )
            }
    }

    // Muxed formats are retained separately for downloads. When an explicitly
    // alternate soundtrack exists, a known-original adaptive pair wins rather
    // than allowing YouTube to choose a dub again.
    if (!hasAlternateAudioTracks || bestAudioUrl == null) {
        muxed.forEach { format ->
            val range = dynamicRange(format)
            if (!includeHdr && range != VideoDynamicRange.SDR) return@forEach
            val label = normalizedVideoQualityLabel(format.optString("qualityLabel"))
            val url = format.optString("url")
            if (label.isNotEmpty() && url.isNotEmpty()) {
                qualities += VideoQuality(
                    resolution = label,
                    url = url,
                    format = container(format.optString("mimeType")),
                    isDASH = false,
                    sourceAspectRatio = sourceAspect,
                    codec = codec(format.optString("mimeType")),
                    dynamicRange = range,
                    width = format.optInt("width"),
                    height = format.optInt("height"),
                    frameRate = format.optInt("fps"),
                )
            }
        }
    }

    return deduplicateVideoQualityVariants(qualities)
}

private fun directAudioTrackType(format: JSONObject): AudioTrackType? {
    val xtags = format.optString("xtags").takeIf(String::isNotBlank) ?: return null
    return runCatching { YoutubeParsingHelper.extractAudioTrackType(xtags) }.getOrNull()
}

internal fun youtubeVideoDynamicRange(
    qualityLabel: String,
    transferCharacteristics: String?,
): VideoDynamicRange {
    val transfer = transferCharacteristics.orEmpty().uppercase()
    return when {
        transfer.contains("ARIB_STD_B67") || transfer.contains("HLG") ->
            VideoDynamicRange.HLG
        transfer.contains("2084") || qualityLabel.contains("HDR", ignoreCase = true) ->
            VideoDynamicRange.HDR10
        else -> VideoDynamicRange.SDR
    }
}

internal fun normalizedVideoQualityLabel(label: String): String = label
    .replace(Regex("""\s+HDR(?:10\+?)?\b.*$""", RegexOption.IGNORE_CASE), "")
    .trim()

internal val VideoQuality.resolutionHeight: Int
    get() = resolution.takeWhile(Char::isDigit).toIntOrNull() ?: 0

internal val VideoQuality.resolutionFrameRate: Int
    get() = resolution.substringAfter('p', "").takeWhile(Char::isDigit).toIntOrNull() ?: 30

/** Closest safe rendition after an HDR source or decoder failure. */
internal fun bestSdrFallback(
    qualities: List<VideoQuality>,
    failed: VideoQuality,
): VideoQuality? {
    val sdr = qualities.filter { !it.isHdr && !it.isLive }
    return sdr.firstOrNull { it.resolution == failed.resolution }
        ?: sdr.firstOrNull { it.resolutionHeight == failed.resolutionHeight }
        ?: sdr.filter { it.resolutionHeight in 1..failed.resolutionHeight }
            .maxByOrNull(VideoQuality::resolutionHeight)
        ?: sdr.filter { it.resolutionHeight > 0 }.minByOrNull(VideoQuality::resolutionHeight)
        ?: sdr.firstOrNull()
}
