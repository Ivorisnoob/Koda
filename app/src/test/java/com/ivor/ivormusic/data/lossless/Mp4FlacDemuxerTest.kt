package com.ivor.ivormusic.data.lossless

import com.ivor.ivormusic.service.core.QualityVerdict
import com.ivor.ivormusic.service.core.SpectrumAnalysis
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

class Mp4FlacDemuxerTest {

    private fun box(type: String, vararg payload: ByteArray): ByteArray {
        val body = payload.fold(ByteArray(0)) { acc, part -> acc + part }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeInt(8 + body.size)
            writeBytes(type)
            write(body)
        }
        return out.toByteArray()
    }

    /** STREAMINFO for 96 kHz, stereo, 24-bit. */
    private val streamInfo = ByteArray(34).also {
        val rate = 96_000
        it[10] = (rate shr 12).toByte()
        it[11] = (rate shr 4).toByte()
        // rate low nibble, channels-1 (1) in 3 bits, bits-1 high bit (23 -> 0b10111 -> high bit 1)
        it[12] = (((rate and 0xF) shl 4) or (1 shl 1) or 1).toByte()
        it[13] = ((23 and 0xF) shl 4).toByte()
    }

    private fun fragmentedFlacMp4(frames: List<ByteArray>, sampleEntry: String = "fLaC"): ByteArray {
        val blocks = byteArrayOf(0x80.toByte(), 0, 0, 34) + streamInfo  // last block: STREAMINFO
        val dfla = box("dfLa", ByteArray(4), blocks)
        val entry = box(sampleEntry, ByteArray(28), dfla)
        val stsd = box("stsd", ByteArray(4), byteArrayOf(0, 0, 0, 1), entry)
        val moov = box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd)))))
        val ftyp = box("ftyp", "mp41dash".toByteArray())
        var file = ftyp + moov
        frames.forEach { frame -> file += box("moof", ByteArray(8)) + box("mdat", frame) }
        return file
    }

    private val frameA = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 1, 2, 3)
    private val frameB = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 4, 5)

    @Test
    fun `fragmented flac becomes native flac with every frame in order`() {
        val dir = createTempDir()
        try {
            val source = File(dir, "in.mp4").apply { writeBytes(fragmentedFlacMp4(listOf(frameA, frameB))) }
            val probe = Mp4FlacDemuxer.probe(source) as Mp4FlacDemuxer.Probe.Flac
            val target = File(dir, "out.flac")
            Mp4FlacDemuxer.writeFlac(source, probe, target)
            val bytes = target.readBytes()
            assertEquals("fLaC", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals(0x80, bytes[4].toInt() and 0xFF) // STREAMINFO, last block
            assertArrayEquals(frameA + frameB, bytes.copyOfRange(4 + 4 + 34, bytes.size))
            val info = LosslessAudioFiles.parseStreamInfo(Mp4FlacDemuxer.streamInfo(probe.metadataBlocks))
            assertEquals(96_000, info?.sampleRate)
            assertEquals(24, info?.bitsPerSample)
            assertEquals(2, info?.channels)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `aac in mp4 is reported as what it is`() {
        val dir = createTempDir()
        try {
            val source = File(dir, "in.mp4").apply { writeBytes(fragmentedFlacMp4(listOf(frameA), "mp4a")) }
            val probe = Mp4FlacDemuxer.probe(source)
            assertTrue(probe is Mp4FlacDemuxer.Probe.Other)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `only the final metadata block is flagged last`() {
        val blocks = byteArrayOf(0x80.toByte(), 0, 0, 2, 9, 9) + byteArrayOf(0x04, 0, 0, 1, 7)
        val fixed = Mp4FlacDemuxer.markLastBlock(blocks)
        assertEquals(0x00, fixed[0].toInt() and 0x80)
        assertEquals(0x80, fixed[6].toInt() and 0x80)
    }
}

class SpectrumAnalysisTest {

    /** A flat spectrum at -40 dB to [toHz], floor at -110 dB above it. */
    private fun spectrum(sampleRate: Int, toHz: Int, bins: Int = 2048): FloatArray {
        val binHz = sampleRate / 2.0 / bins
        return FloatArray(bins) { i -> if (i * binHz < toHz) -40f else -110f }
    }

    @Test
    fun `a lossy ceiling at 16 kHz is found`() {
        val cutoff = SpectrumAnalysis.cutoffHz(spectrum(44_100, 16_000), 44_100)!!
        assertTrue("cutoff $cutoff", cutoff in 15_500..16_500)
        assertEquals(QualityVerdict.LOSSY_SOURCE, SpectrumAnalysis.verdict(true, 44_100, cutoff, 10f))
    }

    @Test
    fun `a 96 kHz file stopping at 22 kHz is upsampled`() {
        val cutoff = SpectrumAnalysis.cutoffHz(spectrum(96_000, 22_000), 96_000)!!
        assertEquals(QualityVerdict.UPSAMPLED_CD, SpectrumAnalysis.verdict(true, 96_000, cutoff, 10f))
    }

    @Test
    fun `sound to 40 kHz is genuine hi-res`() {
        val cutoff = SpectrumAnalysis.cutoffHz(spectrum(96_000, 40_000), 96_000)!!
        assertEquals(QualityVerdict.GENUINE_HI_RES, SpectrumAnalysis.verdict(true, 96_000, cutoff, 10f))
    }

    @Test
    fun `full cd band is lossless cd`() {
        val cutoff = SpectrumAnalysis.cutoffHz(spectrum(44_100, 21_500), 44_100)!!
        assertEquals(QualityVerdict.LOSSLESS_CD, SpectrumAnalysis.verdict(true, 44_100, cutoff, 10f))
    }

    @Test
    fun `too little heard is still listening and lossy streams say so`() {
        assertEquals(QualityVerdict.LISTENING, SpectrumAnalysis.verdict(true, 44_100, 21_000, 2f))
        assertEquals(QualityVerdict.LOSSY_STREAM, SpectrumAnalysis.verdict(false, 44_100, 16_000, 20f))
    }

    @Test
    fun `an all floor spectrum has no ceiling`() {
        assertNull(SpectrumAnalysis.cutoffHz(FloatArray(2048) { -110f }, 44_100))
    }
}
