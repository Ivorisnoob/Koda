package com.ivor.ivormusic.data.lossless

import android.media.MediaExtractor
import android.media.MediaFormat
import com.ivor.ivormusic.util.KLog
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

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
 * tagger could tag. Here the FLAC frames are lifted out of the MP4 into a real
 * FLAC stream: `fLaC`, the STREAMINFO block the MP4's `dfLa` box carries, then
 * every frame as it was. Nothing is decoded or re-encoded, so it is bit for
 * bit the audio the server sent.
 *
 * Anything that cannot be remuxed that way but is still lossless (ALAC, or a
 * FLAC track whose header the platform extractor does not expose) is kept as
 * the MP4 it came in, under `.m4a`. A lossy track is refused: the server
 * substituting AAC is not what the listener asked for.
 */
object LosslessAudioFiles {
    private const val TAG = "LosslessAudioFile"
    private const val STREAMINFO_LENGTH = 34

    /** No FLAC frame comes near this; a buffer this size failing is a broken file. */
    private const val MAX_FRAME_BUFFER = 32 shl 20

    fun finish(source: File, tempDirectory: File): LosslessAudioFile {
        val magic = ByteArray(12)
        RandomAccessFile(source, "r").use { it.read(magic) }
        if (magic.ascii(0, 4) == "fLaC") {
            // Under a .flac name: the tagger picks its reader by extension.
            val named = File.createTempFile("koda_lossless_", ".flac", tempDirectory)
            if (!source.renameTo(named)) {
                source.copyTo(named, overwrite = true)
                source.delete()
            }
            return LosslessAudioFile(named, "flac", "audio/flac", readFlacInfo(named))
        }
        return remuxMp4(source, tempDirectory)
    }

    private fun remuxMp4(source: File, tempDirectory: File): LosslessAudioFile {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw LosslessUnavailableException("The server's file holds no audio")
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()

            when (mime) {
                MediaFormat.MIMETYPE_AUDIO_FLAC -> Unit
                "audio/alac" -> return LosslessAudioFile(source, "m4a", "audio/mp4", formatInfo(format))
                else -> throw LosslessUnavailableException("The server sent lossy audio ($mime)")
            }

            val streamInfo = streamInfoFromCsd(format)
                ?: return keepMp4(source, format, "no STREAMINFO in the track header")

            val target = File.createTempFile("koda_lossless_", ".flac", tempDirectory)
            try {
                writeFlac(extractor, track, streamInfo, target, format)
            } catch (e: Exception) {
                target.delete()
                KLog.w(TAG, "FLAC remux failed; keeping the MP4", e)
                return keepMp4(source, format, e.message ?: "remux failed")
            }
            source.delete()
            return LosslessAudioFile(target, "flac", "audio/flac", parseStreamInfo(streamInfo))
        } finally {
            extractor.release()
        }
    }

    private fun keepMp4(source: File, format: MediaFormat, why: String): LosslessAudioFile {
        KLog.w(TAG, "Keeping FLAC in MP4: $why")
        return LosslessAudioFile(
            source, "m4a", "audio/mp4",
            streamInfoFromCsd(format)?.let(::parseStreamInfo) ?: formatInfo(format)
        )
    }

    private fun writeFlac(
        extractor: MediaExtractor,
        track: Int,
        streamInfo: ByteArray,
        target: File,
        format: MediaFormat,
    ) {
        extractor.selectTrack(track)
        val capacity = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
            .getOrNull()?.takeIf { it > 0 } ?: (1 shl 20)
        var buffer = ByteBuffer.allocate(capacity)
        var frames = 0
        target.outputStream().buffered(1 shl 16).use { out ->
            out.write("fLaC".toByteArray(Charsets.US_ASCII))
            // STREAMINFO alone, marked last; the tagger adds its blocks after it.
            out.write(byteArrayOf(0x80.toByte(), 0, 0, STREAMINFO_LENGTH.toByte()))
            out.write(streamInfo)
            val bytes = ByteArray(capacity)
            var chunk = bytes
            while (true) {
                buffer.clear()
                val size = try {
                    extractor.readSampleData(buffer, 0)
                } catch (e: IllegalArgumentException) {
                    // The buffer was too small for this frame: grow and retry.
                    if (buffer.capacity() >= MAX_FRAME_BUFFER) throw e
                    buffer = ByteBuffer.allocate(buffer.capacity() * 2)
                    continue
                }
                if (size < 0) break
                if (chunk.size < size) chunk = ByteArray(size)
                buffer.get(chunk, 0, size)
                if (frames == 0 && !isFlacFrame(chunk, size)) {
                    throw IllegalStateException("Samples are not FLAC frames")
                }
                out.write(chunk, 0, size)
                frames++
                extractor.advance()
            }
        }
        if (frames == 0) throw IllegalStateException("No frames")
    }

    /** A FLAC frame opens with the 14-bit sync code 0b11111111111110. */
    private fun isFlacFrame(bytes: ByteArray, size: Int): Boolean =
        size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xFE) == 0xF8

    /**
     * The 34 STREAMINFO bytes from the track's codec data. Android's MP4
     * extractor writes `fLaC`, a block header and STREAMINFO there; a bare
     * 34-byte block is accepted too.
     */
    private fun streamInfoFromCsd(format: MediaFormat): ByteArray? {
        val csd = runCatching { format.getByteBuffer("csd-0") }.getOrNull() ?: return null
        val bytes = ByteArray(csd.remaining()).also { csd.duplicate().get(it) }
        return when {
            bytes.size >= 8 + STREAMINFO_LENGTH && bytes.ascii(0, 4) == "fLaC" &&
                (bytes[4].toInt() and 0x7F) == 0 ->
                bytes.copyOfRange(8, 8 + STREAMINFO_LENGTH)
            bytes.size == STREAMINFO_LENGTH -> bytes
            else -> null
        }
    }

    private fun readFlacInfo(file: File): LosslessAudioInfo? = runCatching {
        val header = ByteArray(8 + STREAMINFO_LENGTH)
        RandomAccessFile(file, "r").use { it.readFully(header) }
        if ((header[4].toInt() and 0x7F) != 0) null
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

    private fun formatInfo(format: MediaFormat): LosslessAudioInfo? {
        val rate = runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull() ?: return null
        val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull() ?: 2
        val bits = runCatching { format.getInteger("bits-per-sample") }.getOrNull() ?: 16
        return LosslessAudioInfo(rate, bits, channels)
    }

    private fun ByteArray.ascii(from: Int, length: Int): String =
        if (size < from + length) "" else String(this, from, length, Charsets.US_ASCII)
}
