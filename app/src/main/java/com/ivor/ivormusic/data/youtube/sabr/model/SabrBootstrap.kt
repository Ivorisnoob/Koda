/*
 * Adapted from PipePipe (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d.
 * Copyright the PipePipe contributors. See THIRD_PARTY_NOTICES.md.
 * Koda modifications: package isolation, org.json in place of nanojson, fail-closed
 * validation, redacted diagnostics. Home WEB identity verified live 2026-09-18
 * (.probe/stage5-bootstrap-anon-2026-09-18.log).
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.model

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import org.json.JSONObject

/** How the home-page bootstrap says PO tokens must be minted, or not at all. */
internal enum class SabrPoTokenBinding { CONTENT, SESSION, NONE }

/** BotGuard challenge as the page carries it; the interpreter runs in WebView (stage 5b). */
internal data class SabrAttestationChallenge(
    val program: String,
    val globalName: String,
    val interpreterJavascript: String?,
    val interpreterUrl: String?,
)

/**
 * Immutable, memory-only attestation bootstrap from a YouTube home page.
 * No cookies or minted tokens belong here; the challenge is public page content.
 */
internal data class SabrBootstrap(
    val visitorData: String,
    val dataSyncId: String?,
    val clientName: String,
    val clientVersion: String,
    val binding: SabrPoTokenBinding,
    val eventId: String,
    val challenge: SabrAttestationChallenge,
) {
    init {
        require(visitorData.isNotBlank() && clientName.isNotBlank() &&
            clientVersion.isNotBlank() && eventId.isNotBlank()) { "Incomplete SABR bootstrap" }
        require(challenge.program.isNotBlank() && challenge.globalName.isNotBlank()) {
            "Incomplete SABR challenge"
        }
    }

    override fun toString() = "SabrBootstrap(binding=$binding, client=$clientName)"
}

/**
 * Reads the attestation bootstrap out of a YouTube home page: the `ytcfg.set`
 * configs plus the `window.ytAtN` challenge. Only configs before the challenge
 * count and later duplicates win. Throws [SabrProtocolException] when the page
 * does not carry the expected shape - never guess.
 */
@Throws(SabrProtocolException::class)
internal fun parseSabrBootstrap(pageHtml: String): SabrBootstrap {
    val challengeCall = callArguments(pageHtml, ATTESTATION_CALLEE).firstNotNullOfOrNull { call ->
        parseChallenge(call.argument)?.let { challenge -> call to challenge }
    } ?: throw SabrProtocolException("YouTube home has no initial attestation challenge")
    val configs = callArguments(pageHtml, CONFIG_CALLEE)
        .filter { it.start < challengeCall.first.start }
        .mapNotNull { runCatching { JSONObject(it.argument) }.getOrNull() }
    val clientConfig = configs.lastOrNull { it.has(INNERTUBE_CONTEXT) }
        ?: throw SabrProtocolException("YouTube home has no client context")
    val eventId = configs.lastNonEmptyString(EVENT_ID)
        ?: throw SabrProtocolException("YouTube home has no event id")
    val visitorData = (clientConfig.optString(EOM_VISITOR_DATA, null)?.takeIf { it.isNotEmpty() }
        ?: clientConfig.optString(VISITOR_DATA, null)?.takeIf { it.isNotEmpty() })
        ?.replace("%3D", "=", ignoreCase = true)
        ?: throw SabrProtocolException("YouTube home has no anonymous visitor data")
    val client = clientConfig.optJSONObject(INNERTUBE_CONTEXT)?.optJSONObject("client")
        ?: throw SabrProtocolException("YouTube home has no Innertube client context")
    val clientName = client.optString("clientName", null)?.takeIf { it.isNotEmpty() }
        ?: throw SabrProtocolException("YouTube home has no client name")
    if (clientName != HOME_CLIENT) throw SabrProtocolException("Unsupported YouTube home client")
    val clientVersion = client.optString("clientVersion", null)?.takeIf { it.isNotEmpty() }
        ?: throw SabrProtocolException("YouTube home has no client version")
    val watchConfig = clientConfig.optJSONObject(WEB_PLAYER_CONFIGS)?.optJSONObject(KEVLAR_WATCH)
    val flags = watchConfig?.optString("serializedExperimentFlags", null)
        ?.let(::parseExperimentFlags).orEmpty()
    val binding = when {
        flags[CONTENT_PO_TOKEN_FLAG] == "true" -> SabrPoTokenBinding.CONTENT
        flags[SESSION_PO_TOKEN_FLAG] == "true" -> SabrPoTokenBinding.SESSION
        watchConfig == null -> SabrPoTokenBinding.CONTENT
        else -> SabrPoTokenBinding.NONE
    }
    return SabrBootstrap(
        visitorData = visitorData,
        dataSyncId = configs.lastNonEmptyString(DATA_SYNC_ID),
        clientName = clientName,
        clientVersion = clientVersion,
        binding = binding,
        eventId = eventId,
        challenge = challengeCall.second,
    )
}

private fun List<JSONObject>.lastNonEmptyString(key: String): String? =
    asReversed().firstNotNullOfOrNull { it.optString(key, null)?.takeIf(String::isNotEmpty) }

private fun parseChallenge(argument: String): SabrAttestationChallenge? {
    val match = ATTESTATION_RESPONSE.find(argument) ?: return null
    val quote = match.groupValues[1].single()
    val raw = try {
        decodeJsString(argument, match.range.last + 1, quote)
    } catch (_: IllegalArgumentException) {
        return null
    }
    return try {
        val challenge = JSONObject(raw).optJSONObject("bgChallenge")
            ?: throw SabrProtocolException("Attestation response has no BotGuard challenge")
        val inline = challenge.optJSONObject("interpreterJavascript")
            ?.optString("privateDoNotAccessOrElseSafeScriptWrappedValue", null)
            ?.takeIf { it.isNotEmpty() }
        val url = challenge.optJSONObject("interpreterUrl")
            ?.optString("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", null)
            ?.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("//")) "https:$it" else it }
        if (inline == null && url == null) {
            throw SabrProtocolException("Attestation challenge has no interpreter script or URL")
        }
        SabrAttestationChallenge(
            program = challenge.optString("program", null)?.takeIf { it.isNotEmpty() }
                ?: throw SabrProtocolException("Attestation challenge has no program"),
            globalName = challenge.optString("globalName", null)?.takeIf { it.isNotEmpty() }
                ?: throw SabrProtocolException("Attestation challenge has no global name"),
            interpreterJavascript = inline,
            interpreterUrl = url,
        )
    } catch (_: Exception) {
        null
    }
}

private data class JsCall(val start: Int, val argument: String)

private fun callArguments(source: String, callee: String): List<JsCall> {
    val calls = ArrayList<JsCall>()
    var searchFrom = 0
    while (true) {
        val callStart = source.indexOf(callee, searchFrom)
        if (callStart < 0) return calls
        var paren = callStart + callee.length
        while (paren < source.length && source[paren].isWhitespace()) paren++
        if (paren >= source.length || source[paren] != '(') {
            searchFrom = callStart + callee.length
            continue
        }
        var objectStart = paren + 1
        while (objectStart < source.length && source[objectStart].isWhitespace()) objectStart++
        if (objectStart >= source.length || source[objectStart] != '{') {
            searchFrom = paren + 1
            continue
        }
        val objectEnd = jsObjectEnd(source, objectStart)
        if (objectEnd < 0) {
            searchFrom = objectStart + 1
            continue
        }
        calls.add(JsCall(callStart, source.substring(objectStart, objectEnd + 1)))
        searchFrom = objectEnd + 1
    }
}

private fun jsObjectEnd(source: String, start: Int): Int {
    var depth = 0
    var quote = '\u0000'
    var escaped = false
    for (index in start until source.length) {
        val character = source[index]
        if (quote != '\u0000') {
            if (escaped) escaped = false
            else if (character == '\\') escaped = true
            else if (character == quote) quote = '\u0000'
            continue
        }
        when (character) {
            '\'', '"' -> quote = character
            '{' -> depth++
            '}' -> if (--depth == 0) return index
        }
    }
    return -1
}

private fun parseExperimentFlags(serialized: String): Map<String, String> =
    serialized.split('&').associate {
        val separator = it.indexOf('=')
        if (separator < 0) it to "true" else it.substring(0, separator) to it.substring(separator + 1)
    }

private fun decodeJsString(source: String, start: Int, quote: Char): String {
    val result = StringBuilder()
    var index = start
    while (index < source.length) {
        val character = source[index++]
        if (character == quote) return result.toString()
        if (character != '\\') {
            result.append(character)
            continue
        }
        require(index < source.length) { "Incomplete JavaScript string escape" }
        when (val escaped = source[index++]) {
            'b' -> result.append('\b')
            'f' -> result.append('\u000C')
            'n' -> result.append('\n')
            'r' -> result.append('\r')
            't' -> result.append('\t')
            'v' -> result.append('\u000B')
            'x' -> {
                result.append(readJsHex(source, index, 2).toChar())
                index += 2
            }
            'u' -> {
                result.append(readJsHex(source, index, 4).toChar())
                index += 4
            }
            '\n' -> Unit
            '\r' -> if (index < source.length && source[index] == '\n') index++
            else -> result.append(escaped)
        }
    }
    throw IllegalArgumentException("Unterminated JavaScript string")
}

private fun readJsHex(source: String, start: Int, length: Int): Int {
    require(start + length <= source.length) { "Incomplete hexadecimal escape" }
    var value = 0
    repeat(length) {
        value = value * 16 + (source[start + it].digitToIntOrNull(16)
            ?: throw IllegalArgumentException("Invalid hexadecimal escape"))
    }
    return value
}

private const val CONFIG_CALLEE = "ytcfg.set"
private const val ATTESTATION_CALLEE = "window.ytAtN"
private val ATTESTATION_RESPONSE = Regex("['\"]R['\"]\\s*:\\s*(['\"])")
private const val INNERTUBE_CONTEXT = "INNERTUBE_CONTEXT"
private const val EOM_VISITOR_DATA = "EOM_VISITOR_DATA"
private const val VISITOR_DATA = "VISITOR_DATA"
private const val EVENT_ID = "EVENT_ID"
private const val DATA_SYNC_ID = "DATASYNC_ID"
private const val HOME_CLIENT = "WEB"
private const val WEB_PLAYER_CONFIGS = "WEB_PLAYER_CONTEXT_CONFIGS"
private const val KEVLAR_WATCH = "WEB_PLAYER_CONTEXT_CONFIG_ID_KEVLAR_WATCH"
private const val CONTENT_PO_TOKEN_FLAG = "html5_generate_content_po_token"
private const val SESSION_PO_TOKEN_FLAG = "html5_generate_session_po_token"
