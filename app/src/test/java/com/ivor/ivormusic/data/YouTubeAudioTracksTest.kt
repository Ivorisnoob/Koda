package com.ivor.ivormusic.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAudioTracksTest {
    @Test
    fun `decodes the xtags visionOS served for an Arabic dub`() {
        // Copied from a visionOS /player response, September 2026.
        assertEquals(
            mapOf("acont" to "dubbed", "lang" to "ar"),
            decodeYouTubeXtags("Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICYXI"),
        )
    }

    @Test
    fun `malformed xtags decode to nothing`() {
        assertEquals(emptyMap<String, String>(), decodeYouTubeXtags("not base64 !!"))
        assertEquals(emptyMap<String, String>(), decodeYouTubeXtags("CgUK"))
        assertEquals(emptyMap<String, String>(), decodeYouTubeXtags(null))
    }

    @Test
    fun `one track per id, original first, best format each`() {
        val streamingData = JSONObject().put(
            "adaptiveFormats",
            JSONArray()
                .put(audio("de.3", "German", false, "dubbed", "de", "audio/webm; codecs=\"opus\"", 160_000))
                .put(audio("de.3", "German", false, "dubbed", "de", "audio/mp4; codecs=\"mp4a.40.2\"", 128_000))
                .put(audio("en.4", "English original", true, "original", "en", "audio/mp4; codecs=\"mp4a.40.2\"", 128_000))
                .put(audio("en.4", "English original", true, "original", "en", "audio/mp4; codecs=\"mp4a.40.2\"", 130_000, drc = true))
                .put(audio("ja.10", "Japanese", false, "dubbed-auto", "ja", "audio/mp4; codecs=\"mp4a.40.2\"", 128_000))
                .put(JSONObject().put("mimeType", "video/mp4").put("url", "https://v")),
        )

        val tracks = parseDirectAudioTracks(streamingData)

        assertEquals(listOf("en.4", "de.3", "ja.10"), tracks.map { it.id })
        assertEquals(YouTubeAudioTrackKind.ORIGINAL, tracks[0].kind)
        // The dynamic-range-compressed copy is not a choice.
        assertEquals("https://a/en.4/128000", tracks[0].url)
        // AAC wins over a higher-bitrate Opus, as it does for the original.
        assertEquals("https://a/de.3/128000", tracks[1].url)
        assertEquals(YouTubeAudioTrackKind.AUTO_DUBBED, tracks[2].kind)
        assertEquals("ja", tracks[2].languageTag)
    }

    @Test
    fun `a single soundtrack offers no choice`() {
        val streamingData = JSONObject().put(
            "adaptiveFormats",
            JSONArray()
                .put(audio("en.4", "English original", true, "original", "en", "audio/mp4", 128_000))
                .put(audio("en.4", "English original", true, "original", "en", "audio/webm", 160_000)),
        )
        assertTrue(parseDirectAudioTracks(streamingData).isEmpty())
    }

    private fun audio(
        id: String,
        name: String,
        isDefault: Boolean,
        acont: String,
        lang: String,
        mime: String,
        bitrate: Int,
        drc: Boolean = false,
    ): JSONObject = JSONObject()
        .put("mimeType", mime)
        .put("bitrate", bitrate)
        .put("url", "https://a/$id/$bitrate")
        .put("isDrc", drc)
        .put("xtags", xtags("acont" to acont, "lang" to lang))
        .put(
            "audioTrack",
            JSONObject().put("id", id).put("displayName", name).put("audioIsDefault", isDefault),
        )

    /** Encode {1: key, 2: value} pairs the way YouTube does. */
    private fun xtags(vararg pairs: Pair<String, String>): String {
        fun field(number: Int, bytes: ByteArray): ByteArray =
            byteArrayOf(((number shl 3) or 2).toByte(), bytes.size.toByte()) + bytes
        val body = pairs.fold(ByteArray(0)) { acc, (key, value) ->
            acc + field(1, field(1, key.toByteArray()) + field(2, value.toByteArray()))
        }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(body)
    }
}
