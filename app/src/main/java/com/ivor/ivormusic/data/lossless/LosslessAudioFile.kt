package com.ivor.ivormusic.data.lossless

import com.ivor.ivormusic.util.KLog
import java.io.File
import java.io.RandomAccessFile

/** Sample rate, bit depth and channels of a lossless file, as its own header says. */
data class LosslessAudioInfo(val sampleRate: Int, val bitsPerSample: Int, val channels: Int) {
    val isHiRes: Boolean get() = bitsPerSample > 16 || sampleRate > 48_000
}

/** A finished lossless download, ready to tag and publish. */
data class LosslessAudioFile(
    val file: File,
    val extension: String,
    val mimeType: String,
    val info: LosslessAudioInfo?,
)

/**
 * Turns what the lossless server sent into a file other players can open.
 *
 * A DASH download is a fragmented MP4 with FLAC inside. The Windows client
 * saved exactly that under a `.flac` name, which only Koda could play and no
 * tagger could tag. Here [Mp4FlacDemuxer] lifts the FLAC frames out into a
 * real FLAC stream, bit for bit, with nothing decoded or re-encoded.
 *
 * A download that arrived whole is never thrown away here. Anything that
 * cannot be remuxed but is still lossless - ALAC, or an MP4 the demuxer does
 * not follow - is kept as the MP4 it came in, under `.m4a`, which Koda and
 * most players open. Only a lossy track is refused
 * ([LosslessUnavailableException]): the server substituting AAC is not what
 * the listener asked for.
 */
object LosslessAudioFiles {
    private const val TAG = "LosslessAudioFile"
    private const val STREAMINFO_LENGTH = 34

    fun finish(source: File, tempDirectory: File): LosslessAudioFile {
        val magic = ByteArray(12)
        RandomAccessFile(source, "r").use { it.read(magic) }
        if (magic.ascii(0, 4) == "fLaC") {
            val named = renamed(source, tempDirectory, ".flac")
            return LosslessAudioFile(named, "flac", "audio/flac", readFlacInfo(named))
        }

        val probe = try {
            Mp4FlacDemuxer.probe(source)
        } catch (e: Exception) {
            KLog.w(TAG, "Could not read the MP4's track; keeping it as it came", e)
            return keepMp4(source, tempDirectory, info = null)
        }
        return when (probe) {
            is Mp4FlacDemuxer.Probe.Flac -> {
                val info = parseStreamInfo(Mp4FlacDemuxer.streamInfo(probe.metadataBlocks))
                val target = File.createTempFile("koda_lossless_", ".flac", tempDirectory)
                try {
                    Mp4FlacDemuxer.writeFlac(source, probe, target)
                    source.delete()
                    LosslessAudioFile(target, "flac", "audio/flac", info)
                } catch (e: Exception) {
                    target.delete()
                    KLog.w(TAG, "FLAC remux failed; keeping the MP4", e)
                    keepMp4(source, tempDirectory, info)
                }
            }
            Mp4FlacDemuxer.Probe.Alac -> keepMp4(source, tempDirectory, info = null)
            is Mp4FlacDemuxer.Probe.Other ->
                throw LosslessUnavailableException("The server sent lossy audio (${probe.sampleEntry})")
        }
    }

    private fun keepMp4(source: File, tempDirectory: File, info: LosslessAudioInfo?): LosslessAudioFile =
        LosslessAudioFile(renamed(source, tempDirectory, ".m4a"), "m4a", "audio/mp4", info)

    /** [source] under a name with [suffix]: the tagger picks its reader by extension. */
    private fun renamed(source: File, tempDirectory: File, suffix: String): File {
        if (source.name.endsWith(suffix)) return source
        val named = File.createTempFile("koda_lossless_", suffix, tempDirectory)
        if (!source.renameTo(named)) {
            source.copyTo(named, overwrite = true)
            source.delete()
        }
        return named
    }

    fun readFlacInfo(file: File): LosslessAudioInfo? = runCatching {
        val header = ByteArray(8 + STREAMINFO_LENGTH)
        RandomAccessFile(file, "r").use { it.readFully(header) }
        if (header.ascii(0, 4) != "fLaC" || (header[4].toInt() and 0x7F) != 0) null
        else parseStreamInfo(header.copyOfRange(8, 8 + STREAMINFO_LENGTH))
    }.getOrNull()

    internal fun parseStreamInfo(info: ByteArray): LosslessAudioInfo? {
        if (info.size < 14) return null
        fun u(i: Int) = info[i].toInt() and 0xFF
        val sampleRate = (u(10) shl 12) or (u(11) shl 4) or (u(12) shr 4)
        val channels = ((u(12) shr 1) and 0x07) + 1
        val bits = (((u(12) and 0x01) shl 4) or (u(13) shr 4)) + 1
        return LosslessAudioInfo(sampleRate, bits, channels).takeIf { sampleRate > 0 }
    }

    private fun ByteArray.ascii(from: Int, length: Int): String =
        if (size < from + length) "" else String(this, from, length, Charsets.US_ASCII)
}
