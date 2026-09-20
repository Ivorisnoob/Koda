package com.ivor.ivormusic.data.youtube.sabr.download

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
import kotlinx.coroutines.CancellationException

/**
 * Upfront download eligibility (stage 9a): a descriptor that cannot become a
 * finished file is rejected here with a reason, never deferred until final
 * muxing. Containers Koda can remux without new dependencies are mp4 and
 * webm, audio and video alike; anything else fails before the first segment.
 */
internal sealed interface SabrDownloadRejection {
    val reason: String

    data class NoAudio(override val reason: String = "SABR download has no audio rendition") :
        SabrDownloadRejection

    data class NoVideo(override val reason: String = "SABR download has no video rendition") :
        SabrDownloadRejection

    data class UnsupportedMime(val mimeType: String) : SabrDownloadRejection {
        override val reason: String get() = "SABR download cannot remux $mimeType"
    }

    data class UnknownDuration(override val reason: String = "SABR download has no duration") :
        SabrDownloadRejection
}

private val DOWNLOADABLE_MIMES = setOf("audio/mp4", "audio/webm", "video/mp4", "video/webm")

internal fun checkDownloadable(formats: List<SabrFormat>, audioOnly: Boolean): SabrDownloadRejection? {
    // Only the picked tracks matter; unpicked renditions never enter a file.
    val audio = formats.filter { it.isAudio }.maxByOrNull { it.bitrate }
        ?: return SabrDownloadRejection.NoAudio()
    val video = if (audioOnly) null else formats.filter { it.isVideo }.maxByOrNull { it.bitrate }
    if (!audioOnly && video == null) return SabrDownloadRejection.NoVideo()
    listOfNotNull(audio, video).forEach { format ->
        val mime = format.mimeType.substringBefore(';').trim().lowercase()
        if (mime !in DOWNLOADABLE_MIMES) return SabrDownloadRejection.UnsupportedMime(mime)
        if (format.durationMs <= 0) return SabrDownloadRejection.UnknownDuration()
    }
    return null
}

/** What a download worker does with a failure. Never retries cancellation. */
internal enum class SabrDownloadFate {
    /** Same session, same descriptor, bounded by the worker's attempt budget. */
    RETRYABLE,

    /** Drop the descriptor, invalidate attestation, resolve fresh, then retry. */
    REFRESH_THEN_RETRY,

    /** Classified dead end: record the reason and stop. */
    FATAL,

    /** Cancellation or Local Only: stop now, nothing to record as a failure. */
    TERMINAL,
}

internal fun classifyDownloadError(error: Throwable): SabrDownloadFate {
    if (error is CancellationException) return SabrDownloadFate.TERMINAL
    if (error is SabrCancelledException) return SabrDownloadFate.TERMINAL
    if (error is SabrLocalOnlyException) return SabrDownloadFate.TERMINAL
    if (error is SabrIncompleteMediaException) return SabrDownloadFate.RETRYABLE
    if (error is SabrAttestationException) return SabrDownloadFate.REFRESH_THEN_RETRY
    if (error is SabrStaleDescriptorException) return SabrDownloadFate.REFRESH_THEN_RETRY
    if (error is SabrReloadException) return SabrDownloadFate.REFRESH_THEN_RETRY
    if (error is SabrHttpException) {
        return if (error.status >= 500) SabrDownloadFate.RETRYABLE else SabrDownloadFate.FATAL
    }
    if (error is SabrServerErrorException) return SabrDownloadFate.FATAL
    if (error is SabrProtocolException) return SabrDownloadFate.FATAL
    if (error is java.io.IOException) return SabrDownloadFate.RETRYABLE
    return SabrDownloadFate.FATAL
}
