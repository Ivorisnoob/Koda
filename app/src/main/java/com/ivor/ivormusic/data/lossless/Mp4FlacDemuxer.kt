package com.ivor.ivormusic.data.lossless

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Lifts FLAC out of an MP4 - fragmented (a joined DASH download) or not -
 * without the platform's help.
 *
 * [scar November 2026] The first version asked `android.media.MediaExtractor`
 * for the track, and on the emulator it threw `IllegalArgumentException` from
 * `getTrackFormatNative` for the lossless server's `fLaC` sample entry: a
 * correct 71 MB, 24-bit / 96 kHz download was thrown away and retried three
 * times. Nothing here depends on what a given phone's extractor understands.
 *
 * What it relies on is ISO/IEC 14496-12 and the FLAC-in-ISOBMFF mapping
 * (xiph `isoflac.txt`): the `fLaC` sample entry carries a `dfLa` box holding
 * the native FLAC metadata blocks, and each sample is one whole FLAC frame,
 * which carries its own sync code. So a native FLAC stream is `fLaC`, those
 * blocks, then every `mdat` payload in file order - no sample table needed, no
 * re-encoding, bit for bit what the server sent. A DASH audio file has one
 * track, which is what makes taking every `mdat` whole correct; a file with
 * more than one track is refused rather than guessed at.
 *
 * Pure `java.io`, so it is pinned by a JVM test.
 */
object Mp4FlacDemuxer {

    /** What the MP4's audio track turned out to be. */
    sealed interface Probe {
        /** FLAC, with its metadata blocks (STREAMINFO first) as `dfLa` carries them. */
        data class Flac(val metadataBlocks: ByteArray) : Probe

        /** ALAC: lossless, kept in its MP4. */
        data object Alac : Probe

        /** Anything else, by its sample entry type ("mp4a", "Opus"...). */
        data class Other(val sampleEntry: String) : Probe
    }

    private val CONTAINERS = setOf("moov", "trak", "mdia", "minf", "stbl")

    /** Reads the sample entry of the file's audio track. */
    fun probe(file: File): Probe = RandomAccessFile(file, "r").use { raf ->
        val moov = topLevel(raf).firstOrNull { it.type == "moov" }
            ?: throw IOException("No moov box")
        val traks = children(raf, moov).filter { it.type == "trak" }
        if (traks.size != 1) throw IOException("Expected one track, found ${traks.size}")
        val stsd = findPath(raf, traks.single(), listOf("mdia", "minf", "stbl", "stsd"))
            ?: throw IOException("No stsd box")
        // stsd: version/flags (4) + entry count (4), then the sample entries.
        val entry = boxAt(raf, stsd.payloadStart + 8, stsd.end) ?: throw IOException("Empty stsd")
        when (entry.type) {
            "fLaC" -> {
                // AudioSampleEntry: 6 reserved + 2 data reference + 8 reserved
                // + channels, sample size, pre-defined, reserved, rate = 28.
                val dfla = childrenFrom(raf, entry.payloadStart + 28, entry.end)
                    .firstOrNull { it.type == "dfLa" } ?: throw IOException("fLaC without dfLa")
                // dfLa is a full box: version and flags first.
                val length = (dfla.end - dfla.payloadStart - 4).toInt()
                if (length < 4 + STREAMINFO_LENGTH) throw IOException("dfLa too short")
                val blocks = ByteArray(length)
                raf.seek(dfla.payloadStart + 4)
                raf.readFully(blocks)
                if ((blocks[0].toInt() and 0x7F) != 0) throw IOException("First block is not STREAMINFO")
                Probe.Flac(blocks)
            }
            "alac" -> Probe.Alac
            else -> Probe.Other(entry.type)
        }
    }

    /**
     * Writes [source]'s FLAC as a native FLAC stream to [target]. [probe] is
     * what [probe] answered for it. Throws when the media is not FLAC frames.
     */
    fun writeFlac(source: File, probe: Probe.Flac, target: File) {
        RandomAccessFile(source, "r").use { raf ->
            val mdats = topLevel(raf).filter { it.type == "mdat" && it.end > it.payloadStart }
            if (mdats.isEmpty()) throw IOException("No media")
            raf.seek(mdats.first().payloadStart)
            val sync = ByteArray(2).also { raf.readFully(it) }
            if (sync[0] != 0xFF.toByte() || (sync[1].toInt() and 0xFE) != 0xF8) {
                throw IOException("Media is not FLAC frames")
            }
            target.outputStream().buffered(1 shl 16).use { out ->
                out.write(byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()))
                out.write(markLastBlock(probe.metadataBlocks))
                val buffer = ByteArray(1 shl 16)
                for (mdat in mdats) {
                    raf.seek(mdat.payloadStart)
                    var remaining = mdat.end - mdat.payloadStart
                    while (remaining > 0) {
                        val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw IOException("Media ends early")
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
        }
    }

    /** STREAMINFO's 34 bytes, from metadata blocks as [Probe.Flac] holds them. */
    fun streamInfo(blocks: ByteArray): ByteArray = blocks.copyOfRange(4, 4 + STREAMINFO_LENGTH)

    /**
     * The blocks with only the final one flagged last. `dfLa` normally gets
     * this right already; a muxer that did not would otherwise make a reader
     * stop early or read audio as metadata.
     */
    internal fun markLastBlock(blocks: ByteArray): ByteArray {
        val copy = blocks.copyOf()
        var offset = 0
        while (offset + 4 <= copy.size) {
            val length = ((copy[offset + 1].toInt() and 0xFF) shl 16) or
                ((copy[offset + 2].toInt() and 0xFF) shl 8) or
                (copy[offset + 3].toInt() and 0xFF)
            val next = offset + 4 + length
            val last = next >= copy.size
            copy[offset] = ((copy[offset].toInt() and 0x7F) or (if (last) 0x80 else 0)).toByte()
            if (last) return copy.copyOf(minOf(next, copy.size))
            offset = next
        }
        return copy
    }

    private const val STREAMINFO_LENGTH = 34

    private class Box(val type: String, val payloadStart: Long, val end: Long)

    private fun topLevel(raf: RandomAccessFile): List<Box> = childrenFrom(raf, 0L, raf.length())

    private fun children(raf: RandomAccessFile, parent: Box): List<Box> =
        childrenFrom(raf, parent.payloadStart, parent.end)

    private fun childrenFrom(raf: RandomAccessFile, start: Long, end: Long): List<Box> {
        val boxes = mutableListOf<Box>()
        var position = start
        while (position + 8 <= end) {
            val box = boxAt(raf, position, end) ?: break
            boxes += box
            if (box.end <= position) break
            position = box.end
        }
        return boxes
    }

    /** The box starting at [position], its size clamped to [limit]. */
    private fun boxAt(raf: RandomAccessFile, position: Long, limit: Long): Box? {
        if (position + 8 > limit) return null
        raf.seek(position)
        val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
        val typeBytes = ByteArray(4).also { raf.readFully(it) }
        val type = String(typeBytes, Charsets.ISO_8859_1)
        var header = 8L
        val size = when (size32) {
            0L -> limit - position                    // to the end of the parent
            1L -> { header = 16L; raf.readLong() }   // 64-bit size follows
            else -> size32
        }
        if (size < header) return null
        return Box(type, position + header, minOf(position + size, limit))
    }

    private fun findPath(raf: RandomAccessFile, from: Box, path: List<String>): Box? {
        var current = from
        for (name in path) {
            if (current.type !in CONTAINERS && current !== from) return null
            current = children(raf, current).firstOrNull { it.type == name } ?: return null
        }
        return current
    }
}
