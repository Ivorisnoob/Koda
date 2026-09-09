package com.ivor.ivormusic.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

/**
 * How much of Apple's motion-artwork ladder one cover may spend.
 *
 * [verified September 2026, 346 rungs across 12 square ladders] every rung Apple publishes is
 * VIDEO-RANGE=SDR, H.264 stops at 1080x1080 and HEVC continues to 2160x2160 - so there is no
 * HDR tier to offer, and the top tier is a codec step rather than a dynamic-range one.
 *
 * Persisted by [name]; these constants are frozen.
 */
enum class MotionArtworkQuality {
    /** The smallest square worth drawing, for keeping an animated cover on a tight connection. */
    SAVER,

    /** About 480px at its cheapest encode - the long-standing behaviour, and still the default. */
    BALANCED,

    /** The largest H.264 square rung at its richest encode; matches what a 3x phone draws. */
    HIGH,

    /** Whatever the ladder carries, HEVC and 2160x2160 included. */
    MAXIMUM;

    companion object {
        val DEFAULT = BALANCED

        fun fromName(value: String?): MotionArtworkQuality =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/** Pure catalog matching and rendition selection; never chooses an unrelated album from the map. */
internal object MotionArtworkResolver {
    data class Track(val title: String, val artist: String, val album: String, val durationMs: Long)

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun artist(value: String): String = normalized(value)
        .removeSuffix(" topic").removeSuffix("vevo").trim()

    private fun title(value: String, artistName: String): String {
        val withoutLabel = value.replace(
            Regex("[\\[(](?:official\\s+)?(?:music\\s+video|video|audio|lyric\\s+video|lyrics)[\\])]", RegexOption.IGNORE_CASE), ""
        )
        val parts = withoutLabel.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        return normalized(if (parts.size == 2 && artist(parts[0]) == artist(artistName)) parts[1] else withoutLabel)
    }

    private fun album(value: String): String = normalized(value.replace(
        Regex("[\\[(](?:deluxe(?: edition)?|expanded(?: edition)?|remastered(?: \\d{4})?)[\\])]", RegexOption.IGNORE_CASE), ""
    ))

    fun matches(track: Track, candidate: JSONObject): Boolean {
        val targetArtist = artist(track.artist)
        if (targetArtist.isBlank() || targetArtist in setOf("unknown", "unknown artist")) return false
        if (targetArtist != artist(candidate.optString("artistName"))) return false
        val targetTitle = title(track.title, track.artist)
        if (targetTitle.isBlank() || targetTitle != title(candidate.optString("name"), track.artist)) return false
        val hint = album(track.album)
        if (hint.isNotBlank() && hint !in setOf("unknown", "unknown album", "youtube", "youtube music") &&
            hint != album(candidate.optString("albumName"))) return false
        val duration = candidate.optLong("durationInMillis")
        return track.durationMs <= 0 || duration <= 0 || abs(track.durationMs - duration) <= 8_000
    }

    fun masterUrl(json: JSONObject, track: Track): String? {
        val resources = json.optJSONObject("resources") ?: return null
        val songs = resources.optJSONObject("songs") ?: return null
        val albums = resources.optJSONObject("albums") ?: return null
        val results = json.optJSONObject("results")?.optJSONObject("songs")?.optJSONArray("data") ?: return null
        for (i in 0 until results.length()) {
            val song = songs.optJSONObject(results.optJSONObject(i)?.optString("id") ?: continue) ?: continue
            if (!matches(track, song.optJSONObject("attributes") ?: continue)) continue
            val albumId = song.optJSONObject("relationships")?.optJSONObject("albums")
                ?.optJSONArray("data")?.optJSONObject(0)?.optString("id") ?: continue
            val video = albums.optJSONObject(albumId)?.optJSONObject("attributes")
                ?.optJSONObject("editorialVideo") ?: continue
            for (key in listOf("motionDetailSquare", "motionSquareVideo1x1")) {
                val url = video.optJSONObject(key)?.optString("video") ?: continue
                if (isMediaUrl(url)) return url
            }
        }
        return null
    }

    /**
     * Apple's web player answers its own root with a 301 to a locale path, so the
     * token scrape has to follow redirects - but only inside the host it started on.
     * The client itself still refuses to follow any, so an off-Apple Location is a
     * dead end rather than a request.
     */
    fun isWebUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            url.host == "music.apple.com"
    }

    fun isMediaUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            (url.host.endsWith(".itunes.apple.com") || url.host.endsWith(".mzstatic.com"))
    }

    /**
     * Pin one concrete rendition, never the adaptive master: a master lets the ABR selector
     * reach for a rung the chosen tier never agreed to pay for.
     *
     * Returns an ordered fallback chain rather than a single URL. [MotionArtworkQuality.MAXIMUM]
     * hands the device a 2160x2160 HEVC clip and not every decoder will take one; before this,
     * that single playback error dropped the song to its static cover for good. The chain only
     * ever steps downward, so a failure costs sharpness and never extra data.
     */
    fun renditions(master: String, playlist: String, quality: MotionArtworkQuality): List<String> {
        val variants = variants(master, playlist)
        if (variants.isEmpty()) return emptyList()
        return FALLBACK_ORDER.dropWhile { it != quality }.mapNotNull { pick(variants, it) }.distinct()
    }

    /** The tier's own choice, with no fallback behind it. */
    fun rendition(
        master: String,
        playlist: String,
        quality: MotionArtworkQuality = MotionArtworkQuality.BALANCED,
    ): String? = pick(variants(master, playlist), quality)

    private data class Variant(
        val url: String,
        val width: Int,
        val height: Int,
        val bandwidth: Long,
        val frameRate: Double,
        val hevc: Boolean,
    )

    private fun variants(master: String, playlist: String): List<Variant> {
        if (!isMediaUrl(master) || !playlist.trimStart().startsWith("#EXTM3U")) return emptyList()
        val variants = mutableListOf<Variant>()
        var attributes: Map<String, String>? = null
        for (line in playlist.lineSequence().map(String::trim)) {
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                attributes = Regex("([A-Z-]+)=(\"[^\"]*\"|[^,]*)").findAll(line.substringAfter(':'))
                    .associate { it.groupValues[1] to it.groupValues[2].trim('"') }
            } else if (line.isNotEmpty() && !line.startsWith('#')) {
                val a = attributes ?: continue
                attributes = null
                val size = a["RESOLUTION"]?.split('x') ?: continue
                val width = size.getOrNull(0)?.toIntOrNull() ?: continue
                val height = size.getOrNull(1)?.toIntOrNull() ?: continue
                val bandwidth = a["BANDWIDTH"]?.toLongOrNull() ?: continue
                val codecs = a["CODECS"]?.split(',').orEmpty().map(String::trim)
                val hevc = codecs.any { it.startsWith("hvc1.") || it.startsWith("hev1.") }
                if (!hevc && codecs.none { it.startsWith("avc1.") }) continue
                // Apple publishes motion artwork as SDR only [verified September 2026, 346 rungs
                // across 12 square ladders], and the artwork surface is a TextureView, which
                // could not present anything else. A non-SDR rung is refused rather than tried.
                if (a["VIDEO-RANGE"]?.let { it != "SDR" } == true) continue
                if (width < 1 || height < 1 || bandwidth < 1) continue
                val url = master.toHttpUrlOrNull()?.resolve(line)?.toString() ?: continue
                if (!isMediaUrl(url)) continue
                variants += Variant(url, width, height, bandwidth, a["FRAME-RATE"]?.toDoubleOrNull() ?: 30.0, hevc)
            }
        }
        return variants
    }

    /**
     * What one tier may spend. [targetWidth] applies to the saving tiers, which take the
     * cheapest encode at about that size; [richest] tiers take the largest rung they admit,
     * at its best encode. Every cap is read against BANDWIDTH rather than AVERAGE-BANDWIDTH,
     * which is what keeps [MotionArtworkQuality.BALANCED] selecting exactly what it always has.
     */
    private data class Budget(
        val hevc: Boolean,
        val maxSide: Int,
        val maxBandwidth: Long,
        val maxFrameRate: Double,
        val targetWidth: Int,
        val richest: Boolean,
    )

    private fun budget(quality: MotionArtworkQuality): Budget = when (quality) {
        // About a 360px square: still reads as motion behind the hero, at a tenth of the bytes.
        MotionArtworkQuality.SAVER -> Budget(false, 640, 1_500_000L, 30.1, 240, false)
        MotionArtworkQuality.BALANCED -> Budget(false, 960, 3_000_000L, 30.1, 480, false)
        // H.264 stops at 1080x1080 on every ladder probed, which is what a 3x phone draws.
        MotionArtworkQuality.HIGH -> Budget(false, 1440, 24_000_000L, 30.1, 0, true)
        MotionArtworkQuality.MAXIMUM -> Budget(true, 2160, 48_000_000L, 60.1, 0, true)
    }

    private fun pick(variants: List<Variant>, quality: MotionArtworkQuality): String? {
        val budget = budget(quality)
        val eligible = variants.filter {
            (budget.hevc || !it.hevc) && it.width <= budget.maxSide && it.height <= budget.maxSide &&
                it.bandwidth <= budget.maxBandwidth && it.frameRate <= budget.maxFrameRate
        }
        if (eligible.isEmpty()) return null
        if (budget.richest) {
            return eligible.maxWithOrNull(compareBy<Variant> { it.width }.thenBy { it.bandwidth })?.url
        }
        return (eligible.filter { it.width >= budget.targetWidth }
            .minWithOrNull(compareBy({ it.width }, { it.bandwidth }))
            ?: eligible.maxWithOrNull(compareBy<Variant> { it.width }.thenBy { -it.bandwidth }))?.url
    }

    /** Richest first, so a chain built from it can only ever step down. */
    private val FALLBACK_ORDER = listOf(
        MotionArtworkQuality.MAXIMUM,
        MotionArtworkQuality.HIGH,
        MotionArtworkQuality.BALANCED,
        MotionArtworkQuality.SAVER,
    )

    fun usableWebToken(value: String, nowSeconds: Long): Boolean = try {
        val payload = JSONObject(String(Base64.getUrlDecoder().decode(value.split('.')[1]), Charsets.UTF_8))
        payload.optString("iss") == "AMPWebPlay" && payload.optLong("exp") > nowSeconds + 3_600
    } catch (_: IllegalArgumentException) { false
    } catch (_: IndexOutOfBoundsException) { false
    } catch (_: org.json.JSONException) { false }
}
