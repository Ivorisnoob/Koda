package com.ivor.ivormusic.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class MotionArtworkResolverTest {
    private val track = MotionArtworkResolver.Track("In the End", "Linkin Park", "Hybrid Theory", 216_000)
    private val master = "https://mvod.itunes.apple.com/art/master.m3u8"
    private fun candidate(name: String = track.title, artist: String = track.artist, album: String = track.album, duration: Long = 216_294) =
        JSONObject().put("name", name).put("artistName", artist).put("albumName", album).put("durationInMillis", duration)

    @Test fun matchesVerifiedTrackMetadata() {
        assertTrue(MotionArtworkResolver.matches(track, candidate(artist = "LINKIN PARK", album = "Hybrid Theory (Deluxe Edition)")))
    }
    @Test fun acceptsDecoratedYoutubeTitleAndTopicArtist() {
        assertTrue(MotionArtworkResolver.matches(track.copy(title = "Linkin Park - In the End (Official Music Video)", artist = "Linkin Park - Topic"), candidate()))
    }
    @Test fun rejectsOtherArtistAndWrongAlbum() {
        assertFalse(MotionArtworkResolver.matches(track, candidate(artist = "Someone Else")))
        assertFalse(MotionArtworkResolver.matches(track, candidate(album = "Live in Texas")))
    }
    @Test fun doesNotEraseLiveRemixOrCoverIdentity() {
        for (name in listOf("In the End (Live)", "In the End (Remix)", "In the End (Cover)", "In the End (Sped Up)")) {
            assertFalse(name, MotionArtworkResolver.matches(track, candidate(name = name)))
        }
    }
    @Test fun durationMismatchIsNotAnArtworkMatch() {
        assertFalse(MotionArtworkResolver.matches(track, candidate(duration = 253_000)))
        assertTrue(MotionArtworkResolver.matches(track.copy(durationMs = 0), candidate()))
    }
    @Test fun unknownMetadataDoesNotInventAMatch() {
        assertFalse(MotionArtworkResolver.matches(track.copy(artist = "Unknown artist"), candidate(artist = "Unknown artist")))
        assertFalse(MotionArtworkResolver.matches(track.copy(title = ""), candidate(name = "")))
        assertTrue(MotionArtworkResolver.matches(track.copy(album = "YouTube Music"), candidate()))
    }
    @Test fun unicodeAndPunctuationAreNormalized() {
        val song = MotionArtworkResolver.Track("Déjà Vu!", "Beyoncé", "", 0)
        assertTrue(MotionArtworkResolver.matches(song, candidate(name = "DÉJÀ VU", artist = "BEYONCÉ")))
    }
    private fun catalog(relatedAlbum: String = "matched", matchingSong: Boolean = true): JSONObject = JSONObject("""
        {"results":{"songs":{"data":[{"id":"song"}]}},"resources":{
          "songs":{"song":{"attributes":${candidate(name = if (matchingSong) track.title else "Numb")},
            "relationships":{"albums":{"data":[{"id":"$relatedAlbum"}]}}}},
          "albums":{"unrelated":{"attributes":{"editorialVideo":{"motionDetailSquare":{"video":"$master"}}}},
            "matched":{"attributes":{"editorialVideo":{"motionDetailSquare":{"video":"https://mvod.itunes.apple.com/correct.m3u8"}}}}}
        }}
    """)
    @Test fun followsRelationshipNotFirstAlbumInResourceMap() {
        assertEquals("https://mvod.itunes.apple.com/correct.m3u8", MotionArtworkResolver.masterUrl(catalog(), track))
    }
    @Test fun missingRelationshipAndUnmatchedSongStayStatic() {
        assertNull(MotionArtworkResolver.masterUrl(catalog(relatedAlbum = "missing"), track))
        assertNull(MotionArtworkResolver.masterUrl(catalog(matchingSong = false), track))
        assertNull(MotionArtworkResolver.masterUrl(JSONObject("{}"), track))
    }
    private fun variant(url: String, codec: String = "avc1.64001f", size: String = "486x486", bitrate: Int = 887529, range: String = "SDR") =
        "#EXT-X-STREAM-INF:BANDWIDTH=$bitrate,CODECS=\"$codec\",RESOLUTION=$size,VIDEO-RANGE=$range,FRAME-RATE=30.000\n$url\n"

    @Test fun prefersSmallH264InsteadOfCheaperHevcOrHugeVideo() {
        val hls = "#EXTM3U\n" + variant("hevc.m3u8", codec = "hvc1.2", bitrate = 200000) +
            variant("big.m3u8", size = "1080x1080") + variant("small.m3u8") + variant("costly.m3u8", bitrate = 1777800)
        assertEquals("https://mvod.itunes.apple.com/art/small.m3u8", MotionArtworkResolver.rendition(master, hls))
    }
    @Test fun resolvesRelativePathsAndIgnoresIframeVariants() {
        val hls = "#EXTM3U\n#EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=200000,RESOLUTION=486x486,URI=\"iframes.m3u8\"\n" + variant("../video.m3u8", codec = "avc1.64001f,mp4a.40.2")
        assertEquals("https://mvod.itunes.apple.com/video.m3u8", MotionArtworkResolver.rendition(master, hls))
    }
    @Test fun rejectsHdrHighBitrateAndUnknownCodecs() {
        assertNull(MotionArtworkResolver.rendition(master, "#EXTM3U\n" + variant("hdr.m3u8", range = "PQ") +
            variant("huge.m3u8", bitrate = 4_000_000) + variant("hevc.m3u8", codec = "hvc1.2")))
    }
    @Test fun neverFallsBackToUnboundedMasterOrArbitraryHost() {
        assertNull(MotionArtworkResolver.rendition(master, "#EXTM3U\n#EXTINF:10,\nvideo.ts"))
        assertNull(MotionArtworkResolver.rendition(master, "#EXTM3U\n" + variant("https://example.com/video.m3u8")))
        assertNull(MotionArtworkResolver.rendition(master, "<html>Error</html>"))
        for (url in listOf("http://mvod.itunes.apple.com/a", "https://itunes.apple.com.evil.test/a", "https://user@mvod.itunes.apple.com/a", "file:///tmp/a", "https://mvod.itunes.apple.com:8443/a")) {
            assertFalse(url, MotionArtworkResolver.isMediaUrl(url))
        }
        assertTrue(MotionArtworkResolver.isMediaUrl(master))
    }
    @Test fun gracefullyAcceptsSmallerRenditionWhen480IsAbsent() {
        val hls = "#EXTM3U\n" + variant("tiny.m3u8", size = "270x270") + variant("small.m3u8", size = "360x360")
        assertEquals("https://mvod.itunes.apple.com/art/small.m3u8", MotionArtworkResolver.rendition(master, hls))
    }
    @Test fun webTokenMustHaveCorrectIssuerAndTimeToLive() {
        fun token(issuer: String, expires: Long) = "eyJheader." + Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"iss":"$issuer","exp":$expires}""".toByteArray()) + ".signature"
        assertTrue(MotionArtworkResolver.usableWebToken(token("AMPWebPlay", 10_000), 1_000))
        assertFalse(MotionArtworkResolver.usableWebToken(token("Other", 10_000), 1_000))
        assertFalse(MotionArtworkResolver.usableWebToken(token("AMPWebPlay", 4_000), 1_000))
        assertFalse(MotionArtworkResolver.usableWebToken("broken", 1_000))
    }

    /**
     * The token scrape follows Apple's 301 itself rather than letting OkHttp do it, so
     * this predicate is what stops a Location header walking the request off Apple.
     */
    @Test fun webPlayerHostIsPinnedAcrossRedirects() {
        assertTrue(MotionArtworkResolver.isWebUrl("https://music.apple.com"))
        // The real 301 target, which the feature must be able to follow.
        assertTrue(MotionArtworkResolver.isWebUrl("https://music.apple.com/us/new"))
        for (url in listOf(
            "http://music.apple.com",
            "https://music.apple.com.evil.test/us/new",
            "https://evil.test/music.apple.com",
            "https://user@music.apple.com/us/new",
            "https://music.apple.com:8443/us/new",
            "file:///etc/passwd",
        )) {
            assertFalse(url, MotionArtworkResolver.isWebUrl(url))
        }
    }

    /** A media host is not a web host and vice versa; neither predicate may accept the other's. */
    @Test fun webAndMediaHostsAreDisjoint() {
        assertFalse(MotionArtworkResolver.isWebUrl(master))
        assertFalse(MotionArtworkResolver.isMediaUrl("https://music.apple.com/us/new"))
    }
}
