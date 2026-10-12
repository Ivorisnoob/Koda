package com.ivor.ivormusic.data.js

import com.ivor.ivormusic.data.stream.STREAM_TAG
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager

/**
 * YouTube's player script, executed on this device instead of over the network.
 *
 * The direct visionOS `/player` serves plain URLs, so this path is idle on a
 * good day. When an answer carries ciphered formats and no plain URL, the old
 * code logged `ciphered=N/total` and resolved to nothing, and every retry and
 * re-resolution cost more requests from the same address - the pattern the
 * bot check and the 429 hold are built to catch. This runs the same decipher
 * NewPipe's fallback uses - NewPipe's public `YoutubeJavaScriptPlayerManager`
 * over the Rhino engine this app now owns directly (see `libs.rhino`, pinned
 * to the version NewPipe resolves transitively) - but against the direct
 * response, with no extraction and no fresh visitor minting.
 *
 * Two properties make it safe to call from resolution:
 *
 * - It never throws and never costs a request on failure: anything the
 *   player script cannot answer returns null (or the input URL back), and the
 *   caller continues down its existing chain unchanged.
 * - It holds no new process-wide state: the player script cache is NewPipe's
 *   own static manager, already warm whenever the NewPipe fallback ran, so a
 *   direct decipher and a later extraction share one download per process.
 *
 * Blocking: a cold manager downloads the player script once per process and
 * Rhino runs interpreted. Call only from `Dispatchers.IO`; every caller
 * already is.
 */
internal object LocalJsDecipher {
    /** A parsed `signatureCipher`/`cipher` value: the base URL, the protected signature, and the query param that carries it. */
    internal class ParsedCipher(val url: String, val signature: String, val sigParam: String)

    /**
     * Split a `signatureCipher`/`cipher` value into its parts. Null when the
     * base URL or the signature is missing; `sp` defaults to `sig`, the name
     * YouTube uses unless the cipher says otherwise.
     */
    internal fun parseSignatureCipher(cipher: String): ParsedCipher? {
        if (cipher.isBlank()) return null
        val params = cipher.split('&').mapNotNull { part ->
            val cut = part.indexOf('=')
            if (cut <= 0) null
            else decodeParam(part.substring(0, cut)) to decodeParam(part.substring(cut + 1))
        }.toMap()
        val url = params["url"].takeUnless { it.isNullOrBlank() } ?: return null
        val signature = params["s"].takeUnless { it.isNullOrBlank() } ?: return null
        val sigParam = params["sp"].takeUnless { it.isNullOrBlank() } ?: "sig"
        return ParsedCipher(url, signature, sigParam)
    }

    /**
     * Run YouTube's signature decipher over [signature]. Null on any failure;
     * the caller keeps its existing fallbacks. [run] is injectable so JVM
     * tests never touch the network.
     */
    fun deobfuscateSignature(
        playerUrl: String,
        signature: String,
        run: (String, String) -> String = YoutubeJavaScriptPlayerManager::deobfuscateSignature,
    ): String? {
        if (playerUrl.isBlank() || signature.isBlank()) return null
        return try {
            run(playerUrl, signature)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(STREAM_TAG, "Local signature decipher failed (${e.javaClass.simpleName}), keeping fallbacks")
            null
        }
    }

    /**
     * Replace a streaming URL's throttling (`n`) parameter with its
     * deobfuscated value, so googlevideo serves at full speed instead of
     * pacing the stream. Returns [streamingUrl] unchanged on any failure.
     */
    fun deobfuscateThrottling(
        playerUrl: String,
        streamingUrl: String,
        run: (String, String) -> String = YoutubeJavaScriptPlayerManager::getUrlWithThrottlingParameterDeobfuscated,
    ): String {
        if (playerUrl.isBlank() || streamingUrl.isBlank()) return streamingUrl
        return try {
            run(playerUrl, streamingUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(STREAM_TAG, "Local throttling decipher failed (${e.javaClass.simpleName}), using the URL as served")
            streamingUrl
        }
    }

    /**
     * A copy of [format] with its `signatureCipher`/`cipher` replaced by a
     * plain `url` that keeps its itag, mime type, bitrate and loudness, or
     * null when it has no decipherable cipher (plain formats pass through as
     * their own copy, so callers can always write the result back).
     */
    internal fun decipheredFormat(
        format: JSONObject,
        playerUrl: String,
        deobfuscate: (String) -> String? = { deobfuscateSignature(playerUrl, it) },
        rethrottle: (String) -> String = { deobfuscateThrottling(playerUrl, it) },
    ): JSONObject? {
        if (format.optString("url").isNotBlank()) return JSONObject(format.toString())
        val cipher = format.optString("signatureCipher")
            .takeIf { it.isNotBlank() }
            ?: format.optString("cipher").takeIf { it.isNotBlank() }
            ?: return null
        val parsed = parseSignatureCipher(cipher) ?: return null
        val signature = deobfuscate(parsed.signature) ?: return null
        return JSONObject(format.toString()).apply {
            remove("signatureCipher")
            remove("cipher")
            put("url", rethrottle("${parsed.url}&${parsed.sigParam}=$signature"))
        }
    }

    /**
     * [streamingData] with its ciphered formats deciphered locally, or null
     * when there was nothing to recover. Null means "carry on exactly as
     * before": the caller falls through to its next path, having spent no
     * request. Entries are deciphered in place inside their own array, so the
     * response's adaptive/muxed split is preserved exactly.
     */
    fun decipheredStreamingDataOrNull(
        streamingData: JSONObject,
        playerUrl: String?,
        deobfuscate: (String) -> String? = { deobfuscateSignature(playerUrl.orEmpty(), it) },
        rethrottle: (String) -> String = { deobfuscateThrottling(playerUrl.orEmpty(), it) },
    ): JSONObject? {
        if (playerUrl.isNullOrBlank()) return null
        if (!hasCipheredFormats(streamingData)) return null
        var recovered = 0
        val out = JSONObject()
        for (key in listOf("adaptiveFormats", "formats")) {
            val array = streamingData.optJSONArray(key) ?: continue
            val rewritten = org.json.JSONArray()
            for (i in 0 until array.length()) {
                val format = array.optJSONObject(i) ?: continue
                if (format.optString("url").isNotBlank()) {
                    rewritten.put(format)
                    continue
                }
                rewritten.put(
                    decipheredFormat(format, playerUrl, deobfuscate, rethrottle)?.also { recovered++ }
                        ?: format,
                )
            }
            out.put(key, rewritten)
        }
        return out.takeIf { recovered > 0 }
    }

    /** Whether [streamingData] holds any format the decipher could recover. */
    internal fun hasCipheredFormats(streamingData: JSONObject): Boolean =
        listOf("adaptiveFormats", "formats").any { key ->
            val array = streamingData.optJSONArray(key) ?: return@any false
            (0 until array.length()).any { i ->
                val format = array.optJSONObject(i) ?: return@any false
                format.optString("url").isBlank() &&
                    (format.optString("signatureCipher").isNotBlank() || format.optString("cipher").isNotBlank())
            }
        }

    private fun decodeParam(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
