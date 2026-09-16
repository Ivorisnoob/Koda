/*
 * UMP part ids and control field numbers adapted from PipePipeExtractor's
 * SabrResponseDecoder/YoutubeSabrSession at c0cd0d61863f430af86475aaac968fbef245f507
 * (GPL-3.0). Copyright the upstream contributors. See THIRD_PARTY_NOTICES.md.
 */
package com.ivor.ivormusic.data.youtube.sabr.protocol

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException

/**
 * Control parts of one response, collected while media streams to disk. Byte values
 * are opaque server state: never log, persist or put them in exception messages.
 */
internal class SabrResponseControls {
    class ContextUpdate(val type: Int, value: ByteArray, val sendByDefault: Boolean, val keepExisting: Boolean) {
        private val bytes = value.copyOf()
        val value: ByteArray get() = bytes.copyOf()
        override fun toString() = "ContextUpdate(type=$type)"
    }

    var backoffMs: Int? = null; private set
    private var cookie: ByteArray? = null
    val playbackCookie: ByteArray? get() = cookie?.copyOf()
    var redirectUrl: String? = null; private set
    var errorType: String? = null; private set
    var errorCode: Int? = null; private set
    val hasError: Boolean get() = errorCode != null || errorType != null
    var reloadRequested = false; private set
    var protectionStatus: Int? = null; private set
    var live = false; private set
    val contextUpdates = mutableListOf<ContextUpdate>()
    val startSending = mutableListOf<Int>()
    val stopSending = mutableListOf<Int>()
    val discard = mutableListOf<Int>()
    /** Optional parts that failed to decode and were skipped. */
    var malformedParts = 0; private set

    val attestationPending: Boolean get() = protectionStatus == ATTESTATION_PENDING
    val attestationRequired: Boolean get() = protectionStatus == ATTESTATION_REQUIRED

    fun accept(type: Int, payload: ByteArray) {
        when (type) {
            NEXT_REQUEST_POLICY -> optional { nextRequestPolicy(payload) }
            SABR_REDIRECT -> redirect(payload)
            SABR_ERROR -> error(payload)
            RELOAD_PLAYER_RESPONSE -> reloadRequested = true
            STREAM_PROTECTION_STATUS -> optional { protection(payload) }
            SABR_CONTEXT_UPDATE -> optional { contextUpdate(payload) }
            SABR_CONTEXT_SENDING_POLICY -> optional { sendingPolicy(payload) }
            LIVE_METADATA -> live = true
            // Format metadata, selectable formats, snackbars etc. carry nothing the
            // transport needs; timelines come from the initialization ranges.
            else -> Unit
        }
    }

    private inline fun optional(block: () -> Unit) {
        // [scar upstream] invalid wire types have been observed in a transient
        // NEXT_REQUEST_POLICY. Skip the part rather than discarding the media around it.
        try { block() } catch (_: SabrProtocolException) {
            if (++malformedParts > MAX_MALFORMED_PARTS) throw SabrProtocolException("Too many malformed SABR controls")
        }
    }

    private fun nextRequestPolicy(data: ByteArray) {
        var backoff: Int? = null
        var nextCookie: ByteArray? = null
        SabrProto.readFields(data).forEach {
            when (it.number) {
                4 -> backoff = varint(it, Int.MAX_VALUE.toLong()).toInt()
                7 -> nextCookie = bytes(it)
            }
        }
        backoff?.let { backoffMs = it }
        nextCookie?.let { cookie = it }
    }

    private fun redirect(data: ByteArray) {
        val url = SabrProto.readFields(data).lastOrNull { it.number == 1 }?.let { String(bytes(it), Charsets.UTF_8) }
        if (url.isNullOrBlank()) throw SabrProtocolException("Malformed SABR redirect")
        redirectUrl = url
    }

    private fun error(data: ByteArray) {
        // An error always stands, even when its details cannot be decoded.
        errorCode = 0
        try {
            SabrProto.readFields(data).forEach {
                when (it.number) {
                    1 -> errorType = String(bytes(it), Charsets.UTF_8).take(MAX_ERROR_TYPE_CHARS)
                    2 -> errorCode = varint(it, Int.MAX_VALUE.toLong()).toInt()
                }
            }
        } catch (_: SabrProtocolException) {
            malformedParts++
        }
    }

    private fun protection(data: ByteArray) {
        SabrProto.readFields(data).forEach { if (it.number == 1) protectionStatus = varint(it, Int.MAX_VALUE.toLong()).toInt() }
    }

    private fun contextUpdate(data: ByteArray) {
        var type: Int? = null
        var value: ByteArray? = null
        var sendByDefault = false
        var writePolicy = 0L
        SabrProto.readFields(data).forEach {
            when (it.number) {
                1 -> type = varint(it, Int.MAX_VALUE.toLong()).toInt()
                3 -> value = bytes(it)
                4 -> sendByDefault = varint(it, 1) == 1L
                5 -> writePolicy = varint(it, Int.MAX_VALUE.toLong())
            }
        }
        val validType = type ?: throw SabrProtocolException("SABR context update has no type")
        val validValue = value?.takeIf { it.isNotEmpty() } ?: throw SabrProtocolException("SABR context update has no value")
        if (contextUpdates.size >= MAX_CONTEXT_UPDATES) throw SabrProtocolException("Too many SABR context updates")
        contextUpdates += ContextUpdate(validType, validValue, sendByDefault, keepExisting = writePolicy == 2L)
    }

    private fun sendingPolicy(data: ByteArray) {
        // Decode fully before applying so a malformed part changes nothing.
        val decoded = List(3) { mutableListOf<Int>() }
        var entries = startSending.size + stopSending.size + discard.size
        SabrProto.readFields(data).forEach { field ->
            if (field.number !in 1..3) return@forEach
            val values = when (field.wireType) {
                SabrProto.WIRE_VARINT -> listOf(field.varint)
                SabrProto.WIRE_LENGTH_DELIMITED -> SabrProto.readPackedVarints(field.bytes)
                else -> throw SabrProtocolException("Invalid SABR context policy")
            }
            values.forEach {
                if (it < 0 || it > Int.MAX_VALUE) throw SabrProtocolException("Invalid SABR context type")
                if (++entries > MAX_CONTEXT_UPDATES) throw SabrProtocolException("Too many SABR context policy entries")
                decoded[field.number - 1] += it.toInt()
            }
        }
        startSending += decoded[0]
        stopSending += decoded[1]
        discard += decoded[2]
    }

    private fun varint(field: SabrProto.Field, maximum: Long): Long {
        if (field.wireType != SabrProto.WIRE_VARINT || field.varint < 0 || field.varint > maximum) {
            throw SabrProtocolException("Invalid SABR control field ${field.number}")
        }
        return field.varint
    }

    private fun bytes(field: SabrProto.Field): ByteArray {
        if (field.wireType != SabrProto.WIRE_LENGTH_DELIMITED) {
            throw SabrProtocolException("Invalid SABR control field ${field.number}")
        }
        return field.bytes
    }

    override fun toString() = "SabrResponseControls(backoffMs=$backoffMs, cookie=${cookie != null}, " +
        "redirect=${redirectUrl != null}, error=$errorCode, reload=$reloadRequested, " +
        "protection=$protectionStatus, contexts=${contextUpdates.size}, malformed=$malformedParts)"

    companion object {
        const val LIVE_METADATA = 31
        const val NEXT_REQUEST_POLICY = 35
        const val SABR_REDIRECT = 43
        const val SABR_ERROR = 44
        const val RELOAD_PLAYER_RESPONSE = 46
        const val SABR_CONTEXT_UPDATE = 57
        const val STREAM_PROTECTION_STATUS = 58
        const val SABR_CONTEXT_SENDING_POLICY = 59

        const val ATTESTATION_PENDING = 2
        const val ATTESTATION_REQUIRED = 3

        private const val MAX_MALFORMED_PARTS = 16
        private const val MAX_CONTEXT_UPDATES = 64
        private const val MAX_ERROR_TYPE_CHARS = 128
    }
}
