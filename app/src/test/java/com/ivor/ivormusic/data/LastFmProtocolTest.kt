package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LastFmProtocolTest {
    @Test fun profileReadsIdentityAndChoosesLargestHttpsAvatar() {
        val profile = LastFmProtocol.profile(JSONObject("""{"realname":"River","country":"India","subscriber":"1","registered":{"unixtime":"1037793040"},"image":[{"size":"small","#text":"https://example.com/small.jpg"},{"size":"large","#text":"https://example.com/large.jpg"}]}"""))
        assertEquals("River", profile.realName)
        assertEquals("India", profile.country)
        assertEquals(1037793040L, profile.registeredAt)
        assertEquals("https://example.com/large.jpg", profile.avatar)
        assertTrue(profile.subscriber)
    }

    @Test fun absentProfileDetailsRemainAbsent() {
        assertEquals(LastFmProfile(), LastFmProtocol.profile(JSONObject()))
    }

    @Test fun recentPreservesAlbumArtworkAndListeningTimestamp() {
        val track = LastFmProtocol.recent(JSONObject("""{"recenttracks":{"track":[{"name":"Song","artist":{"#text":"Artist"},"album":{"#text":"Album"},"date":{"uts":"1213031819"},"image":[{"#text":"https://example.com/cover.jpg"}]}]}}""")).single()
        assertEquals("Album", track.album)
        assertEquals(1213031819L, track.playedAt)
        assertEquals("https://example.com/cover.jpg", track.artwork)
    }

    @Test fun signatureSortsAndExcludesTransportFields() {
        val expected = LastFmProtocol.signature(mapOf("api_key" to "key", "method" to "auth.getSession", "token" to "token"), "secret")
        assertEquals(expected, LastFmProtocol.signature(linkedMapOf("token" to "token", "format" to "json",
            "method" to "auth.getSession", "callback" to "ignored", "api_sig" to "ignored", "api_key" to "key"), "secret"))
        assertNotEquals(expected, LastFmProtocol.signature(mapOf("api_key" to "key", "method" to "auth.getSession", "token" to "token"), "other"))
    }

    @Test fun signatureUsesUtf8AndLowercaseMd5() {
        // Independently generated with Python hashlib.md5 over the documented canonical input.
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", LastFmProtocol.signature(emptyMap(), ""))
        assertEquals("9b493c709d47f084d4570cc7b14f7037", LastFmProtocol.signature(mapOf("artist" to "Björk"), "secret"))
    }

    @Test fun recentHandlesSingletonNowPlayingAndEmptyHistory() {
        val singleton = JSONObject("""{"recenttracks":{"track":{"name":"Song","artist":{"#text":"Artist"},"@attr":{"nowplaying":"true"}}}}""")
        assertEquals(listOf(LastFmTrack("Song", "Artist", true)), LastFmProtocol.recent(singleton))
        assertTrue(LastFmProtocol.recent(JSONObject("""{"recenttracks":{"track":[]}}""")).isEmpty())
    }

    @Test fun recentHandlesArrayAndMissingNowPlaying() {
        assertFalse(LastFmProtocol.recent(JSONObject("""{"recenttracks":{"track":[{"name":"Song","artist":{"#text":"Artist"}}]}}""")).single().nowPlaying)
    }

    @Test fun acknowledgementDistinguishesAcceptedAndIgnored() {
        assertEquals(0, LastFmProtocol.scrobbleCode(ack(0)))
        assertEquals(5, LastFmProtocol.scrobbleCode(ack(5)))
        assertEquals(3, LastFmProtocol.scrobbleCode(ack(3)))
    }

    @Test fun acknowledgementAcceptsNumericJsonAttributes() {
        val json = ack(0)
        json.getJSONObject("scrobbles").getJSONObject("@attr").put("accepted", 1).put("ignored", 0)
        json.getJSONObject("scrobbles").getJSONObject("scrobble").getJSONObject("ignoredMessage").put("code", 0)
        assertEquals(0, LastFmProtocol.scrobbleCode(json))
    }

    @Test(expected = IllegalStateException::class) fun inconsistentAcknowledgementCannotDropQueueEntry() {
        val json = ack(0)
        json.getJSONObject("scrobbles").getJSONObject("@attr").put("accepted", "0")
        LastFmProtocol.scrobbleCode(json)
    }

    private fun ack(code: Int) = JSONObject("""{"scrobbles":{"@attr":{"accepted":"${if (code == 0) 1 else 0}","ignored":"${if (code == 0) 0 else 1}"},"scrobble":{"ignoredMessage":{"code":"$code","#text":""}}}}""")
}
