package com.ivor.ivormusic.data.js

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LocalJsDecipher] over synthetic ciphers. The decipher and rethrottle
 * lambdas are fakes, so these tests never touch the network or Rhino: they
 * pin the parsing, the URL assembly and the never-throw contract, while the
 * real player-script path stays covered by NewPipe upstream.
 */
class LocalJsDecipherTest {
    private val cipher =
        "url=https%3A%2F%2Frr1---sn-xyz.googlevideo.com%2Fvideoplayback%3Fexpire%3D123%26itag%3D140" +
            "&s=ABCDEF123456&sp=sig"

    private fun cipheredFormat(cipherKey: String = "signatureCipher"): JSONObject =
        JSONObject().apply {
            put("itag", 140)
            put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
            put("bitrate", 131007)
            put("loudnessDb", 5.39)
            put(cipherKey, cipher)
        }

    // --- parseSignatureCipher -------------------------------------------------

    @Test fun `a full cipher splits into url, signature and param`() {
        val parsed = LocalJsDecipher.parseSignatureCipher(cipher)
        assertNotNull(parsed)
        assertEquals(
            "https://rr1---sn-xyz.googlevideo.com/videoplayback?expire=123&itag=140",
            parsed!!.url,
        )
        assertEquals("ABCDEF123456", parsed.signature)
        assertEquals("sig", parsed.sigParam)
    }

    @Test fun `a missing sp defaults to sig`() {
        val parsed = LocalJsDecipher.parseSignatureCipher("url=https%3A%2F%2Fx%2Fy&s=ABC")
        assertNotNull(parsed)
        assertEquals("sig", parsed!!.sigParam)
    }

    @Test fun `a cipher without url or signature is nothing to decipher`() {
        assertNull(LocalJsDecipher.parseSignatureCipher(""))
        assertNull(LocalJsDecipher.parseSignatureCipher("sp=sig&s=ABC"))
        assertNull(LocalJsDecipher.parseSignatureCipher("url=https%3A%2F%2Fx%2Fy&sp=sig"))
        assertNull(LocalJsDecipher.parseSignatureCipher("not-a-query-string"))
    }

    // --- decipheredFormat -----------------------------------------------------

    @Test fun `a plain format passes through untouched`() {
        val plain = JSONObject().apply {
            put("itag", 251)
            put("mimeType", "audio/webm; codecs=\"opus\"")
            put("url", "https://example.com/audio")
        }
        val out = LocalJsDecipher.decipheredFormat(plain, "https://player")!!
        assertEquals("https://example.com/audio", out.optString("url"))
        assertEquals(251, out.optInt("itag"))
    }

    @Test fun `a ciphered format recovers its url and keeps its identity`() {
        val out = LocalJsDecipher.decipheredFormat(
            cipheredFormat(),
            "https://player",
            deobfuscate = { "FEDCBA654321" },
            rethrottle = { it },
        )
        assertNotNull(out)
        assertEquals(
            "https://rr1---sn-xyz.googlevideo.com/videoplayback?expire=123&itag=140&sig=FEDCBA654321",
            out!!.optString("url"),
        )
        assertEquals("", out.optString("signatureCipher"))
        assertEquals(140, out.optInt("itag"))
        assertEquals(131007, out.optInt("bitrate"))
        assertEquals(5.39, out.optDouble("loudnessDb"), 0.001)
    }

    @Test fun `both cipher key spellings decipher`() {
        for (key in listOf("signatureCipher", "cipher")) {
            val out = LocalJsDecipher.decipheredFormat(
                cipheredFormat(key),
                "https://player",
                deobfuscate = { "SIG" },
                rethrottle = { it },
            )
            assertTrue("key $key", out != null && out.optString("url").endsWith("&sig=SIG"))
        }
    }

    @Test fun `an undecipherable format stays undecipherable`() {
        assertNull(
            LocalJsDecipher.decipheredFormat(
                cipheredFormat(), "https://player",
                deobfuscate = { null }, rethrottle = { it },
            ),
        )
        assertNull(
            LocalJsDecipher.decipheredFormat(
                JSONObject().apply { put("itag", 140) }, "https://player",
                deobfuscate = { "SIG" }, rethrottle = { it },
            ),
        )
    }

    // --- decipheredStreamingDataOrNull ----------------------------------------

    @Test fun `recovery preserves each array it came from`() {
        val data = JSONObject().apply {
            put("adaptiveFormats", org.json.JSONArray().apply { put(cipheredFormat()) })
            put("formats", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("itag", 18)
                    put("mimeType", "video/mp4")
                    put("url", "https://example.com/muxed")
                })
            })
        }
        val out = LocalJsDecipher.decipheredStreamingDataOrNull(
            data, "https://player",
            deobfuscate = { "SIG" }, rethrottle = { it },
        )
        assertNotNull(out)
        val adaptive = out!!.getJSONArray("adaptiveFormats")
        assertEquals(1, adaptive.length())
        assertTrue(adaptive.getJSONObject(0).optString("url").endsWith("&sig=SIG"))
        assertEquals(140, adaptive.getJSONObject(0).optInt("itag"))
        val formats = out.getJSONArray("formats")
        assertEquals(1, formats.length())
        assertEquals("https://example.com/muxed", formats.getJSONObject(0).optString("url"))
    }

    @Test fun `nothing ciphered, no player url or no recovery is null`() {
        val plain = JSONObject().apply {
            put("adaptiveFormats", org.json.JSONArray().apply {
                put(JSONObject().apply { put("url", "https://example.com/a") })
            })
        }
        assertNull(
            LocalJsDecipher.decipheredStreamingDataOrNull(
                plain, "https://player", deobfuscate = { "SIG" }, rethrottle = { it },
            ),
        )
        val ciphered = JSONObject().apply {
            put("adaptiveFormats", org.json.JSONArray().apply { put(cipheredFormat()) })
        }
        assertNull(
            LocalJsDecipher.decipheredStreamingDataOrNull(
                ciphered, null, deobfuscate = { "SIG" }, rethrottle = { it },
            ),
        )
        assertNull(
            LocalJsDecipher.decipheredStreamingDataOrNull(
                ciphered, "https://player", deobfuscate = { null }, rethrottle = { it },
            ),
        )
    }

    // --- never-throw wrappers ---------------------------------------------------

    @Test fun `a failing decipher returns null and a failing rethrottle keeps the url`() {
        assertNull(
            LocalJsDecipher.deobfuscateSignature("https://player", "ABC") { _, _ ->
                throw IllegalStateException("player reshaped")
            },
        )
        val url = "https://example.com/videoplayback?n=XYZ"
        assertEquals(
            url,
            LocalJsDecipher.deobfuscateThrottling("https://player", url) { _, _ ->
                throw IllegalStateException("player reshaped")
            },
        )
    }

    @Test fun `blank inputs never reach the js`() {
        var called = false
        assertNull(LocalJsDecipher.deobfuscateSignature("", "ABC") { _, _ -> called = true; "x" })
        assertNull(LocalJsDecipher.deobfuscateSignature("https://player", "") { _, _ -> called = true; "x" })
        assertEquals(
            "https://example.com/a",
            LocalJsDecipher.deobfuscateThrottling("", "https://example.com/a") { _, _ -> called = true; "x" },
        )
        assertEquals(false, called)
    }
}
