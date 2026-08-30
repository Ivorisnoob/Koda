package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackCacheTest {

    @Test
    fun `refreshed signatures reuse the same rendition key`() {
        val first = videoPlaybackCacheKey(
            videoId = "abc123",
            stream = VideoPlaybackCacheStream.VIDEO,
            sourceUrl = "https://r1.googlevideo.com/videoplayback?expire=1&itag=137&sig=old",
            fallbackVariant = "1080p-mp4-avc1",
        )
        val refreshed = videoPlaybackCacheKey(
            videoId = "abc123",
            stream = VideoPlaybackCacheStream.VIDEO,
            sourceUrl = "https://r2.googlevideo.com/videoplayback?expire=2&itag=137&sig=new",
            fallbackVariant = "1080p-mp4-avc1",
        )

        assertEquals(first, refreshed)
    }

    @Test
    fun `split source roles and renditions never share bytes`() {
        val video = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.VIDEO,
            "https://r.googlevideo.com/videoplayback?itag=137",
            "1080p",
        )
        val audio = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.AUDIO,
            "https://r.googlevideo.com/videoplayback?itag=140",
            "original-audio",
        )
        val otherVideo = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.VIDEO,
            "https://r.googlevideo.com/videoplayback?itag=136",
            "720p",
        )

        assertNotEquals(video, audio)
        assertNotEquals(video, otherVideo)
    }

    @Test
    fun `reprocessed rendition does not reuse stale bytes`() {
        val original = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.VIDEO,
            "https://r.googlevideo.com/videoplayback?itag=137&lmt=100&clen=5000",
            "1080p",
        )
        val reprocessed = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.VIDEO,
            "https://r.googlevideo.com/videoplayback?itag=137&lmt=200&clen=5100",
            "1080p",
        )

        assertNotEquals(original, reprocessed)
    }

    @Test
    fun `provider without itag uses a normalized stable fallback`() {
        val key = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.MUXED,
            "https://media.example/video",
            "360p MPEG_4 AVC1.4D401E",
        )
        val same = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.MUXED,
            "https://media.example/video",
            "360p MPEG_4 AVC1.4D401E",
        )
        val differentUrl = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.MUXED,
            "https://media.example/another-video",
            "360p MPEG_4 AVC1.4D401E",
        )

        assertTrue(key.startsWith("video-playback:abc123:muxed:360p-mpeg_4-avc1.4d401e-url-"))
        assertEquals(key, same)
        assertNotEquals(key, differentUrl)
    }

    @Test
    fun `video namespace is distinguishable from music ids`() {
        val key = videoPlaybackCacheKey(
            "abc123",
            VideoPlaybackCacheStream.MUXED,
            "https://r.googlevideo.com/videoplayback?itag=18",
            "360p",
        )

        assertTrue(isVideoPlaybackCacheKey(key))
        assertTrue(isNonMusicPlaybackCacheKey(key))
        assertTrue(
            isNonMusicPlaybackCacheKey(
                opaquePlaybackCacheKey("https://manifest.googlevideo.com/api/manifest/dash")
            )
        )
        assertFalse(isVideoPlaybackCacheKey("abc123"))
        assertFalse(isNonMusicPlaybackCacheKey("abc123"))
    }
}
