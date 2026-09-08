package com.ivor.ivormusic.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.text.Normalizer
import java.util.Base64
import java.util.Locale
import kotlin.math.abs

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

    fun isMediaUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            (url.host.endsWith(".itunes.apple.com") || url.host.endsWith(".mzstatic.com"))
    }

    /** Pin inexpensive H.264 SDR video, not an adaptive master that can select HEVC/4K. */
    fun rendition(master: String, playlist: String): String? {
        if (!isMediaUrl(master) || !playlist.trimStart().startsWith("#EXTM3U")) return null
        data class Variant(val url: String, val width: Int, val bandwidth: Long)
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
                if (a["CODECS"]?.split(',')?.any { it.trim().startsWith("avc1.") } != true ||
                    a["VIDEO-RANGE"]?.let { it != "SDR" } == true ||
                    (a["FRAME-RATE"]?.toDoubleOrNull() ?: 30.0) > 30.1 ||
                    width !in 1..960 || height !in 1..960 || bandwidth !in 1..3_000_000) continue
                val url = master.toHttpUrlOrNull()?.resolve(line)?.toString() ?: continue
                if (isMediaUrl(url)) variants += Variant(url, width, bandwidth)
            }
        }
        // About 480px is enough for a phone cover; prefer the cheapest encode at that size.
        return (variants.filter { it.width >= 480 }.minWithOrNull(compareBy({ it.width }, { it.bandwidth }))
            ?: variants.maxWithOrNull(compareBy<Variant> { it.width }.thenBy { -it.bandwidth }))?.url
    }

    fun usableWebToken(value: String, nowSeconds: Long): Boolean = try {
        val payload = JSONObject(String(Base64.getUrlDecoder().decode(value.split('.')[1]), Charsets.UTF_8))
        payload.optString("iss") == "AMPWebPlay" && payload.optLong("exp") > nowSeconds + 3_600
    } catch (_: IllegalArgumentException) { false
    } catch (_: IndexOutOfBoundsException) { false
    } catch (_: org.json.JSONException) { false }
}
