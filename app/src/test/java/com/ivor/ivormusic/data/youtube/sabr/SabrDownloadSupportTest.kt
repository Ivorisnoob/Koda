package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.download.SabrDownloadFate
import com.ivor.ivormusic.data.youtube.sabr.download.SabrDownloadRejection
import com.ivor.ivormusic.data.youtube.sabr.download.checkDownloadable
import com.ivor.ivormusic.data.youtube.sabr.download.classifyDownloadError
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrAttestationException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrCancelledException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrHttpException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrIncompleteMediaException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrLocalOnlyException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrReloadException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrServerErrorException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrStaleDescriptorException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SabrDownloadSupportTest {
    private fun audio(mime: String = "audio/webm; codecs=\"opus\"", duration: Long = 90000) =
        SabrFormat(SabrFormatId(251, 3), mime, 128000, duration)

    private fun video(mime: String = "video/mp4; codecs=\"avc1\"", duration: Long = 90000) =
        SabrFormat(SabrFormatId(137, 5), mime, 8000000, duration, width = 1920, height = 1080)

    @Test fun `supported ladders pass and video is ignored for audio only`() {
        assertNull(checkDownloadable(listOf(audio(), video()), audioOnly = true))
        assertNull(checkDownloadable(listOf(audio()), audioOnly = true))
        assertNull(checkDownloadable(listOf(audio(), video()), audioOnly = false))
    }

    @Test fun `missing tracks are rejected upfront`() {
        assertTrue(checkDownloadable(listOf(video()), audioOnly = true) is SabrDownloadRejection.NoAudio)
        assertTrue(checkDownloadable(listOf(audio()), audioOnly = false) is SabrDownloadRejection.NoVideo)
        assertTrue(checkDownloadable(emptyList(), audioOnly = true) is SabrDownloadRejection.NoAudio)
    }

    @Test fun `unmuxable picks and missing durations are rejected upfront`() {
        val bad = checkDownloadable(
            listOf(audio(), video("video/3gpp")), audioOnly = false)
        assertTrue(bad is SabrDownloadRejection.UnsupportedMime)
        val timeless = checkDownloadable(listOf(audio(duration = 0)), audioOnly = true)
        assertTrue(timeless is SabrDownloadRejection.UnknownDuration)
    }

    @Test fun `failures classify into retry refresh fatal and terminal`() {
        assertEquals(SabrDownloadFate.RETRYABLE,
            classifyDownloadError(SabrIncompleteMediaException("truncated")))
        assertEquals(SabrDownloadFate.RETRYABLE, classifyDownloadError(SabrHttpException(503)))
        assertEquals(SabrDownloadFate.RETRYABLE, classifyDownloadError(java.io.IOException("reset")))
        assertEquals(SabrDownloadFate.REFRESH_THEN_RETRY,
            classifyDownloadError(SabrAttestationException("attest")))
        assertEquals(SabrDownloadFate.REFRESH_THEN_RETRY,
            classifyDownloadError(SabrStaleDescriptorException()))
        assertEquals(SabrDownloadFate.REFRESH_THEN_RETRY,
            classifyDownloadError(SabrReloadException("reload")))
        assertEquals(SabrDownloadFate.FATAL, classifyDownloadError(SabrHttpException(404)))
        assertEquals(SabrDownloadFate.FATAL,
            classifyDownloadError(SabrServerErrorException("q", 1)))
        assertEquals(SabrDownloadFate.FATAL,
            classifyDownloadError(SabrProtocolException("malformed")))
        assertEquals(SabrDownloadFate.FATAL,
            classifyDownloadError(IllegalStateException("bug")))
        assertEquals(SabrDownloadFate.TERMINAL,
            classifyDownloadError(SabrCancelledException()))
        assertEquals(SabrDownloadFate.TERMINAL,
            classifyDownloadError(SabrLocalOnlyException()))
        assertEquals(SabrDownloadFate.TERMINAL,
            classifyDownloadError(CancellationException()))
    }
}
