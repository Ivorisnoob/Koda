package com.ivor.ivormusic.data.youtube.sabr.exception

import java.io.IOException
import java.io.InterruptedIOException

/*
 * Typed SABR failures. Messages are diagnostic only and must never contain URLs,
 * tokens, playback cookies or context bytes.
 */

/** Truncated or inconsistent media framing; a fresh request for the same state may succeed. */
internal class SabrIncompleteMediaException(message: String, cause: Throwable? = null) :
    SabrProtocolException(message, cause)

/** The server wants (new) attestation before it serves media. Stage 5 owns the retry. */
internal class SabrAttestationException(message: String) : IOException(message)

/** The player response behind this session is no longer valid; re-resolve and restore position. */
internal class SabrReloadException(message: String) : IOException(message)

/** A SABR_ERROR part. [type] is the server's error type, not user data. */
internal class SabrServerErrorException(val type: String?, val code: Int) :
    IOException("SABR server error: type=${type ?: "unknown"}, code=$code")

/** A non-UMP HTTP answer. */
internal class SabrHttpException(val status: Int) : IOException("SABR HTTP status $status")

/** The descriptor expired or belongs to another profile/login/attestation generation. */
internal class SabrStaleDescriptorException : IOException("SABR descriptor is no longer usable")

/** The session was closed or its thread interrupted. Never retried. */
internal class SabrCancelledException(message: String = "SABR session cancelled") :
    InterruptedIOException(message)

/**
 * Refused before any network ran: Local Only forbids token, bootstrap and
 * descriptor preparation alike. Never retried, never leaves the device.
 */
internal class SabrLocalOnlyException(message: String = "SABR attestation is disabled in Local Only") :
    IOException(message)
