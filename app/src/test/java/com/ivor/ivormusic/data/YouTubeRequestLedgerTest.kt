package com.ivor.ivormusic.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The ledger is only worth anything if its names are right: a `player` call
 * filed under the wrong name, or a video id leaking into an endpoint name,
 * would make a tester's log say something other than what happened.
 */
class YouTubeRequestLedgerTest {

    private fun endpoint(url: String) = YouTubeRequestLedger.endpointOf(url.toHttpUrl())

    @Test
    fun innerTubeCallsAreNamedByMethod() {
        assertEquals("player", endpoint("https://www.youtube.com/youtubei/v1/player?prettyPrint=false"))
        assertEquals("next", endpoint("https://youtubei.googleapis.com/youtubei/v1/next"))
        assertEquals("visitor_id", endpoint("https://www.youtube.com/youtubei/v1/visitor_id"))
        assertEquals(
            "reel/reel_item_watch",
            endpoint("https://youtubei.googleapis.com/youtubei/v1/reel/reel_item_watch?t=abc"),
        )
        assertEquals("browse", endpoint("https://music.youtube.com/youtubei/v1/browse"))
    }

    @Test
    fun pageFetchesKeepAtMostTwoSegmentsAndNoIds() {
        assertEquals("web/watch", endpoint("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("web/sw.js", endpoint("https://www.youtube.com/sw.js"))
        assertEquals("web/feeds/videos.xml", endpoint("https://www.youtube.com/feeds/videos.xml?channel_id=UC1"))
        assertEquals(
            "web/s/player",
            endpoint("https://www.youtube.com/s/player/abcdef12/player_ias.vflset/en_US/base.js"),
        )
        assertEquals("music/", endpoint("https://music.youtube.com/"))
    }

    @Test
    fun otherHostsAreNotYouTubeTraffic() {
        assertNull(endpoint("https://rr1---sn-abc.googlevideo.com/videoplayback?id=1"))
        assertNull(endpoint("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertNull(endpoint("https://sponsor.ajay.app/api/skipSegments"))
        assertNull(endpoint("https://notyoutube.com/watch"))
    }

    @Test
    fun clientNameIsReadFromTheContext() {
        val body = """{"context":{"client":{"clientName": "ANDROID_VR","clientVersion":"1.65"}}}"""
        assertEquals("ANDROID_VR", YouTubeRequestLedger.clientNameIn(body))
        assertNull(YouTubeRequestLedger.clientNameIn("""{"videoId":"abc"}"""))
    }
}
