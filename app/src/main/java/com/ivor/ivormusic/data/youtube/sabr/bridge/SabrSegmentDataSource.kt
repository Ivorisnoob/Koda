/*
 * Serves Media3's exact format/sequence demand from SABR spool files. Demand
 * path adapted from PipePipe (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d
 * (SabrSegmentDataSource): init bytes from the spec, media through the bridge,
 * one re-await when the spool file vanished underneath, position/length
 * bookkeeping, discard-on-close for media. Copyright the PipePipe contributors.
 * See THIRD_PARTY_NOTICES.md.
 * Koda differences: Media3 (not ExoPlayer2) imports, KLog, beyond-timeline and
 * missing-init stay fatal (static VOD timelines - a demand outside them is a
 * manifest bug, not a retryable stall); network transfer accounting stays in
 * the session, so the transfer listener is a documented no-op.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.bridge

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.ivor.ivormusic.data.youtube.sabr.media.SabrSegment
import com.ivor.ivormusic.util.KLog
import java.io.IOException
import java.io.InputStream

internal class SabrSegmentDataSource(
    private val videoId: String,
    private val spec: SabrPlaybackSpec,
    private val bridge: SabrBridge,
) : DataSource {
    private var uri: Uri? = null
    private var data: ByteArray? = null
    private var stream: InputStream? = null
    private var opened: SabrSegmentRef? = null
    private var remaining = 0L
    private var position = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        closeStream()
        data = null
        position = dataSpec.position.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        val ref = spec.refFor(dataSpec.uri.toString())
        opened = ref
        val total: Long
        val available: Long
        if (ref.sequence == null) {
            val init = spec.initData(ref.format)
                ?: throw IllegalStateException(
                    "SABR initialization missing after preparation: itag=${ref.format.id.itag}")
            data = init
            total = init.size.toLong()
            available = maxOf(0L, total - position)
        } else {
            var segment = awaitMedia(ref)
            try {
                stream = segment.open()
            } catch (error: IOException) {
                // Spool file vanished between demand and open; the bridge
                // re-requests once, then any further failure propagates.
                bridge.discard(ref)
                segment = awaitMedia(ref)
                stream = segment.open()
            }
            val skipped = skipFully(requireNotNull(stream), dataSpec.position)
            position = skipped.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
            total = segment.length
            available = maxOf(0L, total - skipped)
        }
        remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available
        else minOf(dataSpec.length, available)
        KLog.d(TAG, "open video=$videoId itag=${ref.format.id.itag} " +
            "seq=${ref.sequence ?: "init"} bytes=$total")
        return remaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining <= 0) return C.RESULT_END_OF_INPUT
        val held = data
        if (held != null) {
            if (position >= held.size) return C.RESULT_END_OF_INPUT
            val count = minOf(minOf(length, held.size - position).toLong(), remaining).toInt()
            held.copyInto(target, offset, position, position + count)
            position += count
            remaining -= count
            return count
        }
        val input = stream ?: return C.RESULT_END_OF_INPUT
        val count = input.read(target, offset, minOf(length.toLong(), remaining).toInt())
        if (count < 0) {
            remaining = 0
            return C.RESULT_END_OF_INPUT
        }
        position += count
        remaining -= count
        return count
    }

    override fun close() {
        data = null
        try {
            closeStream()
        } catch (error: IOException) {
            KLog.w(TAG, "Could not close SABR segment stream", error)
        }
        val ref = opened
        opened = null
        if (ref?.sequence != null) bridge.discard(ref)
    }

    override fun getUri(): Uri? = uri

    override fun addTransferListener(transferListener: TransferListener) {
        // Network transfer happens inside SabrSession, not through this DataSource.
    }

    private fun awaitMedia(ref: SabrSegmentRef): SabrSegment {
        val sequence = requireNotNull(ref.sequence) { "SABR initialization is served from the spec" }
        val end = bridge.timeline(ref.format).endSequence
        if (sequence > end) {
            throw IllegalStateException(
                "SABR segment beyond timeline: itag=${ref.format.id.itag}, seq=$sequence, end=$end")
        }
        return bridge.awaitSegment(ref)
    }

    private fun closeStream() {
        try {
            stream?.close()
        } finally {
            stream = null
        }
    }

    private companion object {
        const val TAG = "SabrSegmentDataSource"

        fun skipFully(input: InputStream, requested: Long): Long {
            var left = maxOf(0, requested)
            val buffer = ByteArray(8192)
            while (left > 0) {
                val skipped = input.skip(left)
                if (skipped > 0) {
                    left -= skipped
                } else {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) break
                    left -= read
                }
            }
            return requested - left
        }
    }
}
