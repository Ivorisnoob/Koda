package com.ivor.ivormusic.video

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException

/**
 * Source-level failures - dead URL, 403, malformed container - are worth
 * re-resolving for. Renderer failures are not: [isTransientRendererError]
 * already re-prepares those in place, and this runs after it.
 */
@OptIn(UnstableApi::class)
fun isRecoverableSourceError(error: PlaybackException): Boolean {
    if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_SOURCE) {
        return true
    }
    return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
}

/**
 * Renderer-level failures (surface torn down, codec reclaimed by another app,
 * transient decode error) are worth re-preparing for. Source-level failures -
 * dead URL, unsupported format - are not, and must reach the UI.
 */
@OptIn(UnstableApi::class)
fun isTransientRendererError(error: PlaybackException): Boolean {
    if (error is ExoPlaybackException && error.type == ExoPlaybackException.TYPE_RENDERER) {
        return true
    }
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED -> true
        else -> false
    }
}

/** HTTP status behind a source error, or null when it was not an HTTP failure. */
@OptIn(UnstableApi::class)
fun httpResponseCode(error: PlaybackException): Int? {
    var cause: Throwable? = error.cause
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause
    }
    return null
}
