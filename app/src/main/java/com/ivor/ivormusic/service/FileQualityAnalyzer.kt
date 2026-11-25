package com.ivor.ivormusic.service

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.ivor.ivormusic.data.lossless.LosslessCache
import com.ivor.ivormusic.data.lossless.Mp4FlacDemuxer
import com.ivor.ivormusic.service.core.BitDepthMeter
import com.ivor.ivormusic.service.core.SpectrumAccumulator
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * The quality check for a song whose file is on the device: a lossless
 * download, a FLAC/ALAC/WAV in the device library, or a server song the
 * lossless cache has kept whole.
 *
 * It decodes the file itself rather than listening to playback, so it sees the
 * whole song in a few seconds without it playing, and it asks the decoder for
 * float PCM, in which a 24-bit sample is exact - so it can also count the bits
 * the recording really uses ([BitDepthMeter]). Playback's own path is 16-bit
 * and cannot. A phone whose decoder will not give float falls back to 16-bit
 * and the sheet says the bit check was not possible.
 *
 * Results are remembered for the last few songs, in memory only, so reopening
 * the sheet does not decode again. That is a transient cache like the
 * waveform's, not repository state.
 */
@UnstableApi
object FileQualityAnalyzer {
    private const val TAG = "FileQualityAnalyzer"
    private const val REMEMBERED = 16

    /** Where the file to check is. */
    sealed interface Input {
        /** Cache key, so a reopened sheet finds its answer. */
        val key: String

        /** A file the platform can open by address: a download or a device-library song. */
        data class Content(val uri: Uri) : Input {
            override val key: String get() = "uri|$uri"
        }

        /** A server song kept whole in [LosslessCache] under [prefix]. */
        data class Kept(val prefix: String) : Input {
            override val key: String get() = "kept|$prefix"
        }
    }

    private val remembered = object : LinkedHashMap<String, SpectrumReading>(REMEMBERED, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SpectrumReading>?) = size > REMEMBERED
    }

    fun rememberedFor(input: Input): SpectrumReading? = synchronized(remembered) { remembered[input.key] }

    /**
     * Decodes [input] whole and measures it. [onProgress] gets 0..1 as it goes.
     * Null when the file could not be opened or decoded.
     */
    suspend fun analyze(context: Context, input: Input, onProgress: (Float) -> Unit): SpectrumReading? {
        rememberedFor(input)?.let { return it }
        val result = withContext(Dispatchers.Default) {
            val temps = mutableListOf<File>()
            try {
                val source = when (input) {
                    is Input.Content -> Source.OfUri(input.uri)
                    is Input.Kept -> keptAsFile(context, input.prefix, temps)?.let(Source::OfFile)
                } ?: return@withContext null
                decode(context, source, onProgress)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.w(TAG, "Could not check ${input.key}", e)
                null
            } finally {
                temps.forEach { it.delete() }
            }
        }
        if (result != null) synchronized(remembered) { remembered[input.key] = result }
        return result
    }

    private sealed interface Source {
        data class OfUri(val uri: Uri) : Source
        data class OfFile(val file: File) : Source
    }

    /**
     * The kept song as a file the platform's extractor can read. The server's
     * FLAC comes in MP4, which some phones' extractors refuse, so it is lifted
     * out to native FLAC first ([Mp4FlacDemuxer]); ALAC stays in its MP4.
     */
    private fun keptAsFile(context: Context, prefix: String, temps: MutableList<File>): File? {
        val dir = File(context.cacheDir, "quality_check").apply { mkdirs() }
        val raw = File(dir, "kept-${System.nanoTime()}.bin").also(temps::add)
        val copied = raw.outputStream().buffered(1 shl 16).use { LosslessCache.copyKept(context, prefix, it) }
        if (!copied) return null
        val magic = ByteArray(4)
        raw.inputStream().use { if (it.read(magic) != 4) return null }
        if (String(magic, Charsets.ISO_8859_1) == "fLaC") return raw
        return when (val probe = Mp4FlacDemuxer.probe(raw)) {
            is Mp4FlacDemuxer.Probe.Flac -> File(dir, "kept-${System.nanoTime()}.flac").also { flac ->
                temps += flac
                Mp4FlacDemuxer.writeFlac(raw, probe, flac)
            }
            Mp4FlacDemuxer.Probe.Alac -> raw
            is Mp4FlacDemuxer.Probe.Other -> null
        }
    }

    private suspend fun decode(context: Context, source: Source, onProgress: (Float) -> Unit): SpectrumReading? {
        val extractor = MediaExtractor()
        try {
            when (source) {
                is Source.OfUri -> extractor.setDataSource(context, source.uri, null)
                is Source.OfFile -> extractor.setDataSource(source.file.absolutePath)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val trackFormat = extractor.getTrackFormat(track)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) trackFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val codec = openDecoder(mime) { extractor.getTrackFormat(track) } ?: return null
            try {
                return pump(extractor, codec, durationUs, onProgress)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    /** A started decoder asked for float output, or for its default when it refuses. */
    private fun openDecoder(mime: String, format: () -> MediaFormat): MediaCodec? {
        // A fresh copy each time: MediaFormat's copy constructor is API 29.
        val asFloat = format().apply { setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT) }
        for (candidate in listOf(asFloat, format())) {
            val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull() ?: return null
            val started = runCatching {
                codec.configure(candidate, null, null, 0)
                codec.start()
            }.isSuccess
            if (started) return codec
            codec.release()
        }
        return null
    }

    private suspend fun pump(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationUs: Long,
        onProgress: (Float) -> Unit,
    ): SpectrumReading? {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var accumulator: SpectrumAccumulator? = null
        var channels = 0
        var floatOut = false
        val meter = BitDepthMeter()
        val window = FloatArray(SpectrumAccumulator.FFT_SIZE)
        var filled = 0
        var scratch = FloatArray(0)
        var lastProgress = -1

        fun readOutputFormat() {
            val f = codec.outputFormat
            val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            floatOut = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            if (accumulator?.sampleRate != rate) {
                accumulator = SpectrumAccumulator(rate)
                filled = 0
            }
        }

        while (true) {
            coroutineContext.ensureActive()
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index) ?: return null
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                readOutputFormat()
                continue
            }
            if (index < 0) continue
            if (accumulator == null) readOutputFormat()
            val acc = accumulator ?: return null
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0) {
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                buffer.order(ByteOrder.nativeOrder())
                val count: Int
                if (floatOut) {
                    val floats = buffer.asFloatBuffer()
                    count = floats.remaining()
                    if (scratch.size < count) scratch = FloatArray(count)
                    floats.get(scratch, 0, count)
                    meter.add(scratch, count)
                } else {
                    val shorts = buffer.asShortBuffer()
                    count = shorts.remaining()
                    if (scratch.size < count) scratch = FloatArray(count)
                    for (i in 0 until count) scratch[i] = shorts.get() / 32768f
                }
                var i = 0
                while (i + channels <= count) {
                    var sum = 0f
                    for (c in 0 until channels) sum += scratch[i + c]
                    window[filled++] = sum / channels
                    if (filled == window.size) {
                        acc.add(window)
                        filled = 0
                    }
                    i += channels
                }
            }
            codec.releaseOutputBuffer(index, false)
            if (durationUs > 0) {
                val percent = (info.presentationTimeUs * 100 / durationUs).toInt().coerceIn(0, 100)
                if (percent != lastProgress) {
                    lastProgress = percent
                    onProgress(percent / 100f)
                }
            }
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
        val acc = accumulator ?: return null
        if (acc.windows == 0) return null
        val levels = acc.levelsDb()
        return SpectrumReading(
            sampleRate = acc.sampleRate,
            levelsDb = levels,
            seconds = acc.seconds,
            evidence = acc.evidence(levels),
            wholeFile = true,
            fullDepth = floatOut,
            usedBits = if (floatOut) meter.usedBits() else null,
        )
    }
}
