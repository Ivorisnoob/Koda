package com.ivor.ivormusic.data

import com.ivor.ivormusic.data.youtube.sabr.model.SabrDescriptor
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import com.ivor.ivormusic.data.youtube.sabr.model.SabrIdentity
import org.junit.Assert.*
import org.junit.Test

class PlaybackSourceTest {
    private val identity = SabrIdentity("profile", 1, 2)
    private val audio = SabrFormat(SabrFormatId(251, 123, "original", "en"), "audio/webm", 128000, 90000)

    private fun descriptor(formats: List<SabrFormat> = listOf(audio), token: ByteArray = byteArrayOf(1, 2)) =
        SabrDescriptor("video", "cpn-secret", "version", "visitor-secret",
            "https://example.test/secret", "config-secret", identity, 100, 200, formats, token)

    @Test fun `legacy manifest classification wins over companion audio`() {
        val source = VideoQuality("Live", "manifest", isDASH = true, audioUrl = "unused", format = "hls")
            .playbackSource as PlaybackSource.Manifest
        assertEquals(PlaybackSource.ManifestType.HLS, source.type)
        assertEquals(listOf("manifest"), source.urls)
        assertTrue(VideoQuality("720p", "video", audioUrl = "audio").playbackSource is PlaybackSource.Split)
        assertTrue(VideoQuality("360p", "muxed").playbackSource is PlaybackSource.Progressive)
    }

    @Test fun `descriptor owns immutable snapshots and redacts diagnostics`() {
        val formats = mutableListOf(audio)
        val token = byteArrayOf(1, 2)
        val result = descriptor(formats, token)
        formats.clear()
        token[0] = 9
        result.poToken[0] = 8
        assertEquals(1, result.formats.size)
        assertArrayEquals(byteArrayOf(1, 2), result.poToken)
        assertFalse(result.toString().contains("secret"))
        assertFalse(PlaybackSource.Progressive("secret").toString().contains("secret"))
    }

    @Test fun `expiry and every identity generation invalidate a descriptor`() {
        val result = descriptor()
        assertTrue(result.isUsable(100, identity))
        assertFalse(result.isUsable(99, identity))
        assertFalse(result.isUsable(200, identity))
        assertFalse(result.isUsable(150, identity.copy(profileId = "other")))
        assertFalse(result.isUsable(150, identity.copy(loginGeneration = 2)))
        assertFalse(result.isUsable(150, identity.copy(attestationGeneration = 3)))
    }

    @Test fun `audio only selection requires no video format`() {
        val source = PlaybackSource.Sabr(descriptor(), audio.id, null)
        assertNull(source.video)
        assertEquals(audio.id, source.audio)
        assertThrows(IllegalArgumentException::class.java) { PlaybackSource.Sabr(descriptor(), null, null) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackSource.Sabr(descriptor(), null, audio.id) }
    }

    @Test fun `sabr sources never enter url backed consumers`() {
        // Stage 2b boundary: no synthetic SABR URL may reach a progressive
        // source or the ranged downloader; SABR is not UrlBacked by construction.
        val source: PlaybackSource = PlaybackSource.Sabr(descriptor(), audio.id, null)
        assertFalse(PlaybackSource.UrlBacked::class.java.isInstance(source))
        val projected = VideoQuality("360p", "https://r.googlevideo.com/videoplayback?itag=18")
            .playbackSource
        assertFalse(projected.urls.any { isSabrUri(it) })
    }

    @Test fun `cache identity distinguishes rendition track tags and initialization`() {
        val id = audio.id
        val keys = listOf(id.cacheKey("v"), id.cacheKey("v", 1), id.cacheKey("other", 1),
            id.copy(lastModified = 124).cacheKey("v", 1), id.copy(audioTrackId = "fr").cacheKey("v", 1),
            id.copy(xtags = null).cacheKey("v", 1), id.copy(xtags = "").cacheKey("v", 1),
            id.copy(xtags = "x:y", audioTrackId = "z").cacheKey("v", 1),
            id.copy(xtags = "x", audioTrackId = "y:z").cacheKey("v", 1))
        assertEquals(keys.size, keys.distinct().size)
        assertThrows(IllegalArgumentException::class.java) { id.cacheKey("v", 0) }
    }
}
