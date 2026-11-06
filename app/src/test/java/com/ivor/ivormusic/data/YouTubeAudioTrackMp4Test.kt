package com.ivor.ivormusic.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [YouTubeAudioTrack.isMp4Audio] decides whether a dub can be downloaded: the
 * MP4 muxer and the `.m4a` a song download is saved as both take AAC only.
 */
class YouTubeAudioTrackMp4Test {

    private fun track(url: String) = YouTubeAudioTrack(
        id = "de.3",
        displayName = "German",
        languageTag = "de",
        kind = YouTubeAudioTrackKind.DUBBED,
        url = url,
    )

    @Test
    fun encodedMp4MimeIsAccepted() {
        val url = "https://rr1.googlevideo.com/videoplayback?itag=140&mime=audio%2Fmp4&c=VISIONOS"
        assertTrue(track(url).isMp4Audio)
    }

    @Test
    fun plainMp4MimeIsAccepted() {
        assertTrue(track("https://example.com/videoplayback?mime=audio/mp4&itag=140").isMp4Audio)
    }

    @Test
    fun webmMimeIsRefused() {
        val url = "https://rr1.googlevideo.com/videoplayback?itag=251&mime=audio%2Fwebm&c=VISIONOS"
        assertFalse(track(url).isMp4Audio)
    }

    @Test
    fun urlWithoutMimeIsLetThrough() {
        assertTrue(track("https://rr1.googlevideo.com/videoplayback?itag=140").isMp4Audio)
    }

    @Test
    fun mimeLikeTextElsewhereInTheUrlIsIgnored() {
        // "xmime=" is not the mime parameter; with none present it is let through.
        assertTrue(track("https://example.com/videoplayback?xmime=audio%2Fwebm").isMp4Audio)
    }
}
