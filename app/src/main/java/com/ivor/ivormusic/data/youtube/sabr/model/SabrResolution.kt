/*
 * MWEB resolving response parsed into SABR playback inputs. Envelope shape adapted
 * from PipePipeExtractor (GPL-3.0), commit c0cd0d61863f430af86475aaac968fbef245f507
 * (buildSabrInfoFromPlayerResponse, parseSabrFormats): serverAbrStreamingUrl,
 * playerConfig ustreamer path, responseContext visitor fallback, adaptive formats
 * with microsecond lastModified and string init/index ranges, skip-malformed
 * formats. Envelope verified live 2026-09-18 (anonymous MWEB /player, playability
 * OK, `c=MWEB`, 25 adaptive formats, no xtags, ciphered URLs, no ustreamer leaf).
 * Copyright the PipePipeExtractor contributors. See THIRD_PARTY_NOTICES.md.
 * Koda differences: org.json, fail-closed empty envelope, opaque ranges as
 * LongRange, initialization URLs stay null until the signature decoder lands,
 * expiry becomes an absolute timestamp, diagnostics redacted.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.model

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import java.net.URI
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Verified SABR resolution, memory-only. The ustreamer blob is opaque until the
 * Media3 bridge (stage 6) shows what it needs; a missing one does not fail
 * resolution - the descriptor contract decides at construction (stage 5b).
 */
internal data class SabrResolution(
    val serverAbrStreamingUrl: String,
    val ustreamerConfig: String?,
    val visitorData: String,
    val expiresAtMs: Long,
    val formats: List<SabrFormat>,
) {
    init {
        require(serverAbrStreamingUrl.isNotBlank() && visitorData.isNotBlank()) {
            "Incomplete SABR resolution"
        }
        require(expiresAtMs > 0 && formats.isNotEmpty()) { "Unusable SABR resolution" }
    }

    override fun toString() = "SabrResolution(formats=${formats.size}, sensitiveFields=redacted)"
}

/**
 * Parses a token-bound MWEB /player response. The token carries the visitor
 * identity (the server only serves media to attested requests); the response
 * contributes the streaming URL, formats and lifetime. Throws
 * [SabrProtocolException] on any envelope gap - never guess.
 */
@Throws(SabrProtocolException::class)
internal fun parseSabrResolution(
    response: JSONObject,
    videoId: String,
    cpn: String,
    token: SabrMintedToken,
    resolvedAtMs: Long,
): SabrResolution {
    require(videoId.isNotBlank() && cpn.isNotBlank() && resolvedAtMs >= 0) {
        "Incomplete SABR resolution request"
    }
    val streamingData = response.optJSONObject("streamingData")
        ?: throw SabrProtocolException("MWEB player response has no streaming data")
    val serverUrl = streamingData.optString("serverAbrStreamingUrl", null)
        ?.takeIf { it.isNotBlank() }
        ?: throw SabrProtocolException("MWEB player response has no SABR streaming URL")
    validateSabrStreamingUrl(serverUrl)
    val expiresInSec = streamingData.optLong("expiresInSeconds", -1)
    if (expiresInSec <= 0) throw SabrProtocolException("MWEB player response has no expiry")
    val formats = parseResolutionFormats(streamingData.optJSONArray("adaptiveFormats"))
    if (formats.isEmpty()) throw SabrProtocolException("MWEB player response has no SABR formats")
    return SabrResolution(
        serverAbrStreamingUrl = serverUrl,
        ustreamerConfig = response.optJSONObject("playerConfig")
            ?.optJSONObject("mediaCommonConfig")
            ?.optJSONObject("mediaUstreamerRequestConfig")
            ?.optString("videoPlaybackUstreamerConfig", null)?.takeIf { it.isNotEmpty() },
        visitorData = token.visitorData,
        expiresAtMs = resolvedAtMs + expiresInSec * 1000,
        formats = formats,
    )
}

/**
 * Builds the immutable playback snapshot. The ustreamer leaf is required: without
 * token-bound evidence that resolutions succeed lacking it, failing closed is the
 * only honest option. Padding is restored explicitly rather than relying on the
 * decoder tolerating its absence.
 */
@Throws(SabrProtocolException::class)
internal fun SabrResolution.toDescriptor(
    videoId: String,
    cpn: String,
    token: SabrMintedToken,
    identity: SabrIdentity,
    resolvedAtMs: Long,
): SabrDescriptor {
    require(videoId.isNotBlank() && cpn.isNotBlank()) { "Incomplete SABR resolution request" }
    val ustreamer = ustreamerConfig?.takeIf { it.isNotBlank() }
        ?: throw SabrProtocolException("MWEB resolution has no ustreamer config")
    val padded = token.poTokenBase64Url +
        "=".repeat((4 - token.poTokenBase64Url.length % 4) % 4)
    val tokenBytes = try {
        Base64.getUrlDecoder().decode(padded)
    } catch (error: IllegalArgumentException) {
        throw SabrProtocolException("PO token is not base64", error)
    }
    if (tokenBytes.isEmpty()) throw SabrProtocolException("PO token decoded empty")
    return SabrDescriptor(
        videoId = videoId,
        cpn = cpn,
        clientVersion = token.clientVersion,
        visitorData = visitorData,
        serverAbrStreamingUrl = serverAbrStreamingUrl,
        ustreamerConfig = ustreamer,
        identity = identity,
        resolvedAtMs = resolvedAtMs,
        expiresAtMs = expiresAtMs,
        formats = formats,
        poToken = tokenBytes,
    )
}

/**
 * Rejects anything but an https googlevideo URL minted for MWEB, so a mismatched
 * User-Agent never reaches the server (invariant 2). Shared by the parser and
 * the session transport.
 */
@Throws(SabrProtocolException::class)
internal fun validateSabrStreamingUrl(url: String) {
    val uri = try {
        URI(url)
    } catch (error: Exception) {
        throw SabrProtocolException("Malformed SABR streaming URL", error)
    }
    val host = uri.host?.lowercase()
    if (!"https".equals(uri.scheme, ignoreCase = true) || host == null ||
        !(host == "googlevideo.com" || host.endsWith(".googlevideo.com"))) {
        throw SabrProtocolException("SABR streaming URL is not a googlevideo host")
    }
    val clients = uri.rawQuery.orEmpty().split('&')
        .map { it.substringBefore('=') to it.substringAfter('=', "") }
        .filter { (name, _) -> name == "c" }.map { (_, value) -> value }
    if (clients.any { !it.equals("MWEB", ignoreCase = true) }) {
        throw SabrProtocolException("SABR streaming URL was minted for another client")
    }
}

private fun parseResolutionFormats(adaptive: JSONArray?): List<SabrFormat> {
    if (adaptive == null) return emptyList()
    val formats = ArrayList<SabrFormat>(adaptive.length())
    for (i in 0 until adaptive.length()) {
        val format = adaptive.optJSONObject(i) ?: continue
        if (format.optInt("itag", -1) <= 0) continue
        val lastModified = longOrNull(format.opt("lastModified"))?.takeIf { it >= 0 } ?: continue
        val mime = format.optString("mimeType", null)
            ?.takeIf { it.startsWith("audio/") || it.startsWith("video/") } ?: continue
        if (format.optInt("bitrate", -1) < 0) continue
        try {
            formats.add(SabrFormat(
                id = SabrFormatId(
                    itag = format.getInt("itag"),
                    lastModified = lastModified,
                    xtags = format.optString("xtags", null),
                    audioTrackId = format.optJSONObject("audioTrack")?.optString("id", null),
                ),
                mimeType = mime,
                bitrate = format.getInt("bitrate"),
                durationMs = longOrNull(format.opt("approxDurationMs"))?.takeIf { it >= 0 } ?: 0,
                width = format.optInt("width", 0).takeIf { it >= 0 } ?: 0,
                height = format.optInt("height", 0).takeIf { it >= 0 } ?: 0,
                audioTrackDisplayName = format.optJSONObject("audioTrack")
                    ?.optString("displayName", null),
                isDrc = format.optBoolean("isDrc", false),
                initializationRange = format.optJSONObject("initRange")?.longRangeOrNull(),
                indexRange = format.optJSONObject("indexRange")?.longRangeOrNull(),
            ))
        } catch (_: IllegalArgumentException) {
            continue
        }
    }
    return formats
}

private fun longOrNull(value: Any?): Long? = when (value) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
}

private fun JSONObject.longRangeOrNull(): LongRange? {
    val start = longOrNull(opt("start")) ?: return null
    val end = longOrNull(opt("end")) ?: return null
    if (start < 0 || end < start) return null
    return start..end
}
