/*
 * UMP media framing and compression identifiers adapted from PipePipeExtractor's
 * SabrStreamingResponseReader/SabrMediaSegmentCollector at
 * c0cd0d61863f430af86475aaac968fbef245f507 (GPL-3.0).
 * Copyright the upstream contributors. See THIRD_PARTY_NOTICES.md.
 * Koda implementation: bounded file-backed assembly and decompression.
 */
package com.ivor.ivormusic.data.youtube.sabr.media

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrIncompleteMediaException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.protocol.UmpReader
import java.io.EOFException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.zip.GZIPInputStream
import org.brotli.dec.BrotliInputStream

/**
 * Demultiplexes a response without holding media in heap. The caller owns the
 * input stream and each delivered segment; close the segment after consumption.
 * Control callbacks run synchronously and must not log or persist opaque bytes.
 */
internal object SabrSegmentReader {
    private const val MAX_OPEN_SEGMENTS = 16
    private const val MAX_CONTROL_BYTES = 512 * 1024
    private const val MAX_CONTROLS = 512
    private const val MAX_INIT_BYTES = 4L * 1024 * 1024
    private const val MAX_MEDIA_BYTES = 64L * 1024 * 1024

    private class Pending(val header: SabrMediaHeader, val lease: SabrSpool.Lease) {
        val limit: Long get() = if (header.initialization) MAX_INIT_BYTES else MAX_MEDIA_BYTES
    }

    fun read(input: InputStream, spool: SabrSpool,
             onControl: (type: Int, payload: ByteArray) -> Unit,
             onSegment: (SabrSegment) -> Unit) {
        val open = mutableMapOf<Int, Pending>()
        var controlBytes = 0
        var controls = 0
        val buffer = ByteArray(8192)
        var failure: Throwable? = null
        try {
            UmpReader.readPayloadsUntil(input) { type, size, payload ->
                interrupted()
                if (type == 21) {
                    if (size < 1) throw SabrIncompleteMediaException("SABR media has no header id")
                    val id = payload.read()
                    val pending = open[id] ?: throw SabrIncompleteMediaException("SABR media has no open header")
                    val declared = pending.header.wireLength ?: pending.limit
                    copy(payload, pending.lease, minOf(declared, pending.limit), buffer)
                } else {
                    if (++controls > MAX_CONTROLS || size > MAX_CONTROL_BYTES - controlBytes) {
                        throw SabrProtocolException("SABR control limit exceeded")
                    }
                    controlBytes += size
                    val data = ByteArray(size)
                    readExactly(payload, data)
                    when (type) {
                        20 -> {
                            // [scar upstream] A corrupt header is transient server output:
                            // recover with a fresh request rather than failing playback.
                            val header = try {
                                SabrMediaHeader.decode(data)
                            } catch (error: SabrProtocolException) {
                                throw SabrIncompleteMediaException("Malformed SABR media header", error)
                            }
                            if (header.headerId in open || open.size >= MAX_OPEN_SEGMENTS) {
                                throw SabrProtocolException("Duplicate or excessive SABR media headers")
                            }
                            val limit = if (header.initialization) MAX_INIT_BYTES else MAX_MEDIA_BYTES
                            if ((header.wireLength ?: 0) > limit) throw SabrProtocolException("SABR segment exceeds limit")
                            open[header.headerId] = Pending(header, spool.create())
                        }
                        22 -> {
                            if (size != 1) throw SabrProtocolException("Invalid SABR media end")
                            val pending = open.remove(data[0].toInt() and 255)
                                ?: throw SabrIncompleteMediaException("SABR end has no open header")
                            complete(pending, spool, buffer, onSegment)
                        }
                        else -> onControl(type, data)
                    }
                }
                true
            }
            if (open.isNotEmpty()) throw SabrIncompleteMediaException("Incomplete SABR media at EOF")
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            open.values.forEach {
                try { it.lease.close() } catch (error: Throwable) {
                    val prior = failure ?: cleanupFailure
                    if (prior != null) prior.addSuppressed(error) else cleanupFailure = error
                }
            }
            if (failure == null) cleanupFailure?.let { throw it }
        }
    }

    private fun complete(pending: Pending, spool: SabrSpool, buffer: ByteArray,
                         consumer: (SabrSegment) -> Unit) {
        var delivered = false
        var result = pending.lease
        try {
            val expected = pending.header.wireLength
            if (pending.lease.length == 0L || (expected != null && expected != pending.lease.length)) {
                throw SabrIncompleteMediaException("SABR media length mismatch")
            }
            pending.lease.finish()
            if (pending.header.compression != 0) {
                result = spool.create()
                pending.lease.open().use { raw ->
                    val decoded = when (pending.header.compression) {
                        1 -> GZIPInputStream(raw)
                        2 -> BrotliInputStream(raw)
                        else -> throw SabrProtocolException("Unsupported SABR compression")
                    }
                    decoded.use { copy(it, result, pending.limit, buffer) }
                }
                result.finish()
                pending.lease.close()
            }
            consumer(SabrSegment(pending.header, result))
            delivered = true
        } finally {
            if (!delivered) {
                try { result.close() } finally { if (result !== pending.lease) pending.lease.close() }
            }
        }
    }

    private fun copy(input: InputStream, output: SabrSpool.Lease, limit: Long, buffer: ByteArray) {
        while (true) {
            interrupted()
            val count = input.read(buffer)
            if (count < 0) return
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) return
                buffer[0] = byte.toByte()
                output.write(buffer, 1, limit)
            } else output.write(buffer, count, limit)
        }
    }

    private fun readExactly(input: InputStream, output: ByteArray) {
        var offset = 0
        while (offset < output.size) {
            interrupted()
            val count = input.read(output, offset, output.size - offset)
            if (count < 0) throw EOFException("Truncated SABR control")
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) throw EOFException("Truncated SABR control")
                output[offset++] = byte.toByte()
            } else offset += count
        }
    }

    private fun interrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("SABR read cancelled")
    }
}
