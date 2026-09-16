package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormat
import com.ivor.ivormusic.data.youtube.sabr.model.SabrFormatId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class SabrTimelineTest {
    private fun format(mime: String = "audio/mp4", duration: Long = 3000) =
        SabrFormat(SabrFormatId(140, 1), mime, 128000, duration)

    @Test fun `MP4 versions and extended boxes preserve one based seek boundaries`() {
        for (version in 0..1) for (extended in listOf(false, true)) {
            val timeline = SabrFormatTimeline.parse(format(), sidx(version, extended))
            assertEquals(3, timeline.endSequence)
            assertEquals(1, timeline.getSequenceAt(-1))
            assertEquals(1, timeline.getSequenceAt(999))
            assertEquals(2, timeline.getSequenceAt(1000))
            assertEquals(3, timeline.getSequenceAt(2999))
            assertEquals(4, timeline.getSequenceAt(3000))
            assertEquals(1000, timeline.getStartMs(2))
            assertEquals(-1, timeline.getStartMs(0))
            assertEquals(-1, timeline.getEndMs(4))
        }
    }

    @Test fun `MP4 rounds absolute boundaries without accumulating fractional gaps`() {
        val timeline = SabrFormatTimeline.parse(format(), sidx(timescale = 3, durations = intArrayOf(1, 1, 1)))
        assertEquals(333, timeline.getEndMs(1))
        assertEquals(333, timeline.getStartMs(2))
        assertEquals(667, timeline.getEndMs(2))
        assertEquals(1000, timeline.getEndMs(3))
    }

    @Test fun `MP4 rejects truncation zero scales nested references and overflow`() {
        val valid = sidx()
        for (length in valid.indices) {
            assertThrows(SabrProtocolException::class.java) { SabrFormatTimeline.parse(format(), valid.copyOf(length)) }
        }
        listOf(sidx(timescale = 0), sidx(nested = true), sidx(durations = intArrayOf(0)),
            sidx(version = 2), sidx(version = 1, start = Long.MAX_VALUE),
            sidx(durations = intArrayOf())).forEach {
            assertThrows(SabrProtocolException::class.java) { SabrFormatTimeline.parse(format(), it) }
        }
    }

    @Test fun `MP4 does not interpret bytes inside another box as SIDX`() {
        val fake = box("free", sidx())
        assertThrows(SabrProtocolException::class.java) { SabrFormatTimeline.parse(format(), fake) }
        assertEquals(3, SabrFormatTimeline.parse(format(), fake + sidx()).endSequence)
    }

    @Test fun `WebM cues support full and unknown or partial Segment masters`() {
        val webm = format("audio/webm")
        for (master in listOf(0, 1, 2)) {
            val timeline = SabrFormatTimeline.parse(webm, webm(master = master))
            assertEquals(3, timeline.endSequence)
            assertEquals(2, timeline.getSequenceAt(1000))
            assertEquals(3000, timeline.getEndMs(3))
            assertEquals(4, timeline.getSequenceAt(3000))
        }
    }

    @Test fun `WebM uses default and explicit timecode scales`() {
        assertEquals(1000, SabrFormatTimeline.parse(format("audio/webm"), webm()).getEndMs(1))
        assertEquals(2000, SabrFormatTimeline.parse(format("audio/webm", 6000),
            webm(scale = 2000000)).getEndMs(1))
    }

    @Test fun `WebM rejects incomplete leaves invalid cues and invented final duration`() {
        val valid = webm()
        for (length in valid.indices) {
            assertThrows(SabrProtocolException::class.java) {
                SabrFormatTimeline.parse(format("audio/webm"), valid.copyOf(length))
            }
        }
        listOf(webm(times = listOf(0, 0)), webm(times = listOf(1000, 0)),
            webm(scale = 0), webm(times = emptyList()), webm(scale = Long.MAX_VALUE)).forEach {
            assertThrows(SabrProtocolException::class.java) { SabrFormatTimeline.parse(format("audio/webm"), it) }
        }
        assertThrows(SabrProtocolException::class.java) {
            SabrFormatTimeline.parse(format("audio/webm", 0), valid)
        }
    }

    private fun sidx(version: Int = 0, extended: Boolean = false, timescale: Int = 1000,
                     durations: IntArray = intArrayOf(1000, 1000, 1000), start: Long = 0,
                     nested: Boolean = false): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).apply {
            writeInt(version shl 24); writeInt(1); writeInt(timescale)
            if (version == 1) { writeLong(start); writeLong(0) }
            else { writeInt(start.toInt()); writeInt(0) }
            writeShort(0); writeShort(durations.size)
            durations.forEach { writeInt(if (nested) Int.MIN_VALUE or 100 else 100); writeInt(it); writeInt(0) }
        }
        return box("sidx", payload.toByteArray(), extended)
    }

    private fun box(type: String, payload: ByteArray, extended: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(if (extended) 1 else payload.size + 8)
            writeBytes(type)
            if (extended) writeLong(payload.size + 16L)
            write(payload)
        }
        return out.toByteArray()
    }

    private fun webm(times: List<Long> = listOf(0, 1000, 2000), scale: Long? = null, master: Int = 0): ByteArray {
        val info = element(bytes(0x15, 0x49, 0xa9, 0x66),
            scale?.let { element(bytes(0x2a, 0xd7, 0xb1), unsigned(it)) } ?: byteArrayOf())
        val cues = element(bytes(0x1c, 0x53, 0xbb, 0x6b),
            times.flatMap { element(bytes(0xbb), element(bytes(0xb3), unsigned(it))).toList() }.toByteArray())
        val body = info + cues
        val segmentId = bytes(0x18, 0x53, 0x80, 0x67)
        return when (master) {
            1 -> segmentId + bytes(0xff) + body
            2 -> segmentId + bytes(0x40, 0xff) + body
            else -> element(segmentId, body)
        }
    }

    private fun element(id: ByteArray, body: ByteArray): ByteArray {
        require(body.size < 127)
        return id + bytes(0x80 or body.size) + body
    }
    private fun unsigned(value: Long): ByteArray = ByteArray(8) { (value ushr ((7 - it) * 8)).toByte() }
    private fun bytes(vararg ints: Int) = ints.map(Int::toByte).toByteArray()
}
