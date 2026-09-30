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

    // A VOD rendition describes a whole file: it declares contentLength and its
    // URL is an ordinary ranged endpoint. A live rendition is a segment endpoint
    // (`live=1`, `noclen=1`) carrying neither, which is why live playback must
    // stay on the HLS manifest and the manifest owns its own HDR/SDR adaptation.
    //
    // [scar] The presence of hlsManifestUrl alone is NOT a live signal. That was
    // verified against ANDROID_VR, which omits the key on VODs - but the Apple
    // clients do not. [verified September 2026] visionOS and IOS return
    // hlsManifestUrl on every ordinary VOD, so gating on the key alone made the
    // visionOS HDR augmentation classify every video as live, return an all-SDR
    // HLS ladder, and silently reduce the whole HDR feature to a no-op. The
    // discriminator is the formats, not the manifest.
    fun isVodFormat(format: JSONObject): Boolean {
        val url = format.optString("url")
        return url.isNotEmpty() &&
            format.optString("contentLength").isNotBlank() &&
            !url.contains("live=1")
    }

    val hlsManifestUrl = streamingData.optString("hlsManifestUrl").takeIf(String::isNotBlank)
    if (hlsManifestUrl != null && (adaptive + muxed).none(::isVodFormat)) {
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

/**
 * The selectable soundtracks in a native player response: one entry per
 * `audioTrack.id`, each resolved to its best URL-bearing format by the same
 * rule the ladder uses for the original (AAC first, then bitrate), so a dub
 * sounds like the original did. Empty unless there are at least two tracks.
 *
 * Dynamic-range-compressed copies (`isDrc`, which IOS serves beside the normal
 * ones) are skipped: they are the same language at a flatter mix, not a choice
 * a viewer is asking for.
 */
internal fun parseDirectAudioTracks(streamingData: JSONObject): List<YouTubeAudioTrack> {
    val adaptive = streamingData.optJSONArray("adaptiveFormats") ?: return emptyList()
    val byTrack = linkedMapOf<String, MutableList<JSONObject>>()
    for (index in 0 until adaptive.length()) {
        val format = adaptive.optJSONObject(index) ?: continue
        if (!format.optString("mimeType").startsWith("audio/")) continue
        if (format.optString("url").isEmpty() || format.optBoolean("isDrc")) continue
        val id = format.optJSONObject("audioTrack")?.optString("id")
            ?.takeIf(String::isNotBlank) ?: continue
        byTrack.getOrPut(id) { mutableListOf() } += format
    }
    if (byTrack.size < 2) return emptyList()

    return byTrack.mapNotNull { (id, formats) ->
        val best = formats.maxWithOrNull(
            compareBy(
                { if (it.optString("mimeType").contains("mp4a")) 1 else 0 },
                { it.optInt("bitrate") },
            )
        ) ?: return@mapNotNull null
        val track = best.optJSONObject("audioTrack")
        val tags = decodeYouTubeXtags(best.optString("xtags"))
        YouTubeAudioTrack(
            id = id,
            displayName = track?.optString("displayName")?.takeIf(String::isNotBlank) ?: id,
            languageTag = tags["lang"] ?: id.substringBefore('.').takeIf(String::isNotBlank),
            kind = youTubeAudioTrackKind(
                acont = tags["acont"],
                isDefault = track?.optBoolean("audioIsDefault") == true,
            ),
            url = best.optString("url"),
        )
    }.sortedForMenu()
}

/** The original first, then by name, so the menu opens on the likely answer. */
internal fun List<YouTubeAudioTrack>.sortedForMenu(): List<YouTubeAudioTrack> =
    sortedWith(compareBy({ !it.isOriginal }, { it.displayName.lowercase() }))

internal fun youTubeAudioTrackKind(acont: String?, isDefault: Boolean): YouTubeAudioTrackKind =
    when (acont) {
        "original" -> YouTubeAudioTrackKind.ORIGINAL
        "dubbed" -> YouTubeAudioTrackKind.DUBBED
        "dubbed-auto" -> YouTubeAudioTrackKind.AUTO_DUBBED
        "descriptive" -> YouTubeAudioTrackKind.DESCRIPTIVE
        "secondary" -> YouTubeAudioTrackKind.SECONDARY
        // No xtags at all: the default track is the only one YouTube would
        // have played, which is what "original" means to the player.
        else -> if (isDefault) YouTubeAudioTrackKind.ORIGINAL else YouTubeAudioTrackKind.UNKNOWN
    }

/**
 * Decode a format's `xtags`: base64url protobuf of repeated `{1: key, 2: value}`
 * pairs, e.g. {acont: dubbed, lang: de}. Decoded here rather than through
 * NewPipe's extractAudioTrackType because that folds "dubbed-auto" into DUBBED,
 * and a machine dub should say so. Anything malformed decodes to nothing.
 */
internal fun decodeYouTubeXtags(xtags: String?): Map<String, String> {
    if (xtags.isNullOrBlank()) return emptyMap()
    val bytes = runCatching {
        java.util.Base64.getUrlDecoder().decode(xtags.trimEnd('='))
    }.getOrNull() ?: return emptyMap()

    // Returns (fieldNumber, bytes) for length-delimited fields; other wire
    // types are skipped, and a truncated buffer ends the walk.
    fun fields(buffer: ByteArray): List<Pair<Int, ByteArray>> {
        val out = mutableListOf<Pair<Int, ByteArray>>()
        var position = 0
        fun varint(): Long? {
            var shift = 0
            var value = 0L
            while (position < buffer.size && shift < 64) {
                val byte = buffer[position++].toInt() and 0xff
                value = value or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) return value
                shift += 7
            }
            return null
        }
        while (position < buffer.size) {
            val key = varint() ?: break
            when ((key and 7).toInt()) {
                0 -> varint() ?: break
                2 -> {
                    val length = varint()?.toInt() ?: break
                    if (length < 0 || length > buffer.size - position) break
                    out += (key ushr 3).toInt() to buffer.copyOfRange(position, position + length)
                    position += length
                }
                else -> break
            }
        }
        return out
    }

    return runCatching {
        fields(bytes).filter { it.first == 1 }.mapNotNull { (_, pair) ->
            val entry = fields(pair).toMap()
            val key = entry[1]?.toString(Charsets.UTF_8)?.takeIf(String::isNotBlank)
            val value = entry[2]?.toString(Charsets.UTF_8)
            if (key != null && value != null) key to value else null
        }.toMap()
    }.getOrDefault(emptyMap())
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
