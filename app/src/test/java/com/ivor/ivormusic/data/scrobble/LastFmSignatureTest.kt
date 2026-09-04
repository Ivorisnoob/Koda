package com.ivor.ivormusic.data.scrobble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class LastFmSignatureTest {

    @Test
    fun signatureExcludesFormatAndCallback() {
        val secret = "testsecret123"
        val paramsWithFormat = mapOf(
            "method" to "track.scrobble",
            "api_key" to "testkey456",
            "format" to "json",
            "callback" to "mycallback",
            "artist" to "Radiohead",
            "track" to "Karma Police"
        )
        val paramsWithoutFormat = mapOf(
            "method" to "track.scrobble",
            "api_key" to "testkey456",
            "artist" to "Radiohead",
            "track" to "Karma Police"
        )

        val sigWith = LastFmClient.generateSignature(paramsWithFormat, secret)
        val sigWithout = LastFmClient.generateSignature(paramsWithoutFormat, secret)

        assertEquals(sigWithout, sigWith)
    }

    @Test
    fun signatureSortsKeysAlphabeticallyByAscii() {
        val secret = "mysecret"
        val params = mapOf(
            "track" to "Creep",
            "artist" to "Radiohead",
            "api_key" to "mykey",
            "method" to "track.updateNowPlaying"
        )

        // Expected sorted concatenation:
        // api_key + mykey + artist + Radiohead + method + track.updateNowPlaying + track + Creep + mysecret
        val expectedString = "api_keymykeyartistRadioheadmethodtrack.updateNowPlayingtrackCreepmysecret"
        val md = MessageDigest.getInstance("MD5")
        val expectedBytes = md.digest(expectedString.toByteArray(Charsets.UTF_8))
        val expectedHex = expectedBytes.joinToString("") { "%02x".format(it) }

        val actualSig = LastFmClient.generateSignature(params, secret)
        assertEquals(expectedHex, actualSig)
    }

    @Test
    fun signatureHandlesUtf8SpecialCharacters() {
        val secret = "secret_utf8"
        val params = mapOf(
            "artist" to "Sigur Rós",
            "track" to "Hoppípolla",
            "api_key" to "key1"
        )

        val expectedString = "api_keykey1artistSigur RóstrackHoppípollasecret_utf8"
        val md = MessageDigest.getInstance("MD5")
        val expectedBytes = md.digest(expectedString.toByteArray(Charsets.UTF_8))
        val expectedHex = expectedBytes.joinToString("") { "%02x".format(it) }

        val actualSig = LastFmClient.generateSignature(params, secret)
        assertEquals(expectedHex, actualSig)
    }

    @Test
    fun authorizationUrlConstructedProperly() {
        val url = LastFmClient.getAuthorizationUrl("test_api_key", "test_req_token")
        assertEquals("https://www.last.fm/api/auth/?api_key=test_api_key&token=test_req_token", url)
    }
}
