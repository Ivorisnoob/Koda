package com.ivor.ivormusic.data

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Measures the shape of a track's audio: where it starts, how it ends, and
 * whether it ends at all or runs straight into the next thing.
 *
 * **Only the ends are decoded.** Answering every question in [AudioProfile]
 * needs the first few seconds and the last few, so the extractor seeks rather
 * than walking the file: roughly twenty seconds of audio per track instead of
 * four minutes, which is the difference between this being a background detail
 * and being something the user notices.
 *
 * **The input is the playback cache, not a file.** Media3's `SimpleCache` has
 * no path to hand `MediaExtractor`, so [CacheBackedDataSource] adapts a
 * `CacheDataSource` to the `MediaDataSource` interface, which takes ranged
 * reads and is exactly what a seeking extractor wants. Nothing is downloaded
 * here: if the bytes are not already cached the read fails and the track simply
 * has no profile, which every consumer has to tolerate anyway.
 */
@UnstableApi
object AudioProfiler {

    private const val TAG = "AudioProfiler"

    /** How much of each end to decode. */
    private const val HEAD_WINDOW_US = 8_000_000L
    private const val TAIL_WINDOW_US = 20_000_000L

    /** Envelope resolution. Fine enough to see a fade, coarse enough to be cheap. */
    private const val WINDOW_MS = 20

    /**
     * Below this RMS (of full scale) counts as silence. Chosen well above the
     * noise floor of a lossy encode, which is never digitally silent.
     */
    private const val SILENCE_RMS = 0.002f

    /**
     * A track still above this fraction of its own loud level at the very end
     * did not finish - it was cut into whatever followed.
     */
    private const val ABRUPT_END_RATIO = 0.25f

    /** Give up rather than block a transition that is seconds away. */
    private const val DECODE_TIMEOUT_US = 10_000L

    /**
     * @param uri the resolved stream URI, needed so an uncached read could fall
     *   through to it; [cacheKey] is what the cache is actually keyed by.
     * @return null when the audio could not be read or decoded, which is a
     *   normal outcome and not an error worth surfacing.
     */
    suspend fun profile(
        songId: String,
        context: Context,
        uri: android.net.Uri,
        cacheKey: String,
        factory: CacheDataSource.Factory?,
        durationMs: Long,
    ): AudioProfile? = withContext(Dispatchers.Default) {
        if (durationMs <= 0) return@withContext null
        var extractor: MediaExtractor? = null
        var source: CacheBackedDataSource? = null
        try {
            extractor = MediaExtractor()
            if (uri.scheme == "http" || uri.scheme == "https") {
                val cacheFactory = factory ?: return@withContext null
                source = CacheBackedDataSource(cacheFactory, uri, cacheKey)
                if (source.getSize() <= 0) return@withContext null
                extractor.setDataSource(source)
            } else {
                extractor.setDataSource(context, uri, emptyMap())
            }

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val head = decodeAudio(extractor, format, 0L, HEAD_WINDOW_US)
            val tailStartUs = ((durationMs * 1000L) - TAIL_WINDOW_US).coerceAtLeast(0L)
            val tail = decodeAudio(extractor, format, tailStartUs, TAIL_WINDOW_US)

            if (head.envelope.isEmpty() && tail.envelope.isEmpty()) return@withContext null

            val rhythm = analyseRhythm(head.envelope)
            val outroRhythm = analyseRhythm(tail.envelope)
            val tailStartMs = tailStartUs / 1000L
            val outroDownbeatLeadMs = lastDownbeatLead(
                rhythm = outroRhythm,
                windowStartMs = tailStartMs,
                durationMs = durationMs,
            )
            val rawOutroLead = outroLead(tail.envelope)
            val phrase = analysePhraseBoundary(
                rawOutroLeadMs = rawOutroLead,
                durationMs = durationMs,
                rhythm = outroRhythm,
                gridOriginMs = tailStartMs + outroRhythm.downbeatOffsetMs,
            )
            val key = analyseKey(head.samples, head.sampleRate)

            AudioProfile(
                songId = songId,
                leadInSilenceMs = leadInSilence(head.envelope),
                tailFadeMs = tailFadeLength(tail.envelope),
                endsAbruptly = endsAbruptly(tail.envelope),
                outroLeadMs = rawOutroLead,
                bpm = rhythm.bpm,
                tempoConfidence = rhythm.confidence,
                beatOffsetMs = rhythm.beatOffsetMs,
                downbeatOffsetMs = rhythm.downbeatOffsetMs,
                outroBpm = outroRhythm.bpm,
                outroTempoConfidence = outroRhythm.confidence,
                outroDownbeatLeadMs = outroDownbeatLeadMs,
                phraseOutroLeadMs = phrase.first,
                phraseConfidence = phrase.second,
                keyPitchClass = key.pitchClass,
                keyMode = key.mode,
                keyConfidence = key.confidence,
            )
        } catch (e: Exception) {
            Log.d(TAG, "No profile for $songId: ${e.message}")
            null
        } finally {
            runCatching { extractor?.release() }
            runCatching { source?.close() }
        }
    }

    /**
     * Decode [windowUs] from [fromUs] into a per-window RMS envelope.
     *
     * Synchronous `MediaCodec`, because this is a short one-shot job on a
     * background dispatcher and the async callback API buys nothing here.
     */
    private fun decodeAudio(
        extractor: MediaExtractor,
        format: MediaFormat,
        fromUs: Long,
        windowUs: Long,
    ): DecodedAudio {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return DecodedAudio.EMPTY
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull()
            ?: return DecodedAudio.EMPTY

        val envelope = ArrayList<Float>(512)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44_100)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2).coerceAtLeast(1)
        val mono = FloatCollector((sampleRate * (windowUs / 1_000_000L)).toInt())
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            val samplesPerWindow = (sampleRate * channels * WINDOW_MS / 1000).coerceAtLeast(1)

            var acc = 0.0
            var accCount = 0
            var channelIndex = 0
            var monoAcc = 0f

            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inIndex = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        val size = if (inBuffer == null) -1 else extractor.readSampleData(inBuffer, 0)
                        val presentationUs = extractor.sampleTime
                        if (size < 0 || presentationUs > fromUs + windowUs) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, presentationUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, DECODE_TIMEOUT_US)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0 &&
                        bufferInfo.presentationTimeUs >= fromUs
                    ) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val shorts = outBuffer.slice()
                            .order(ByteOrder.nativeOrder())
                            .asShortBuffer()
                        while (shorts.hasRemaining()) {
                            val sample = shorts.get() / 32768f
                            acc += (sample * sample).toDouble()
                            accCount++
                            monoAcc += sample
                            channelIndex++
                            if (channelIndex == channels) {
                                mono.add(monoAcc / channels)
                                channelIndex = 0
                                monoAcc = 0f
                            }
                            if (accCount >= samplesPerWindow) {
                                envelope.add(sqrt(acc / accCount).toFloat())
                                acc = 0.0
                                accCount = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEnd = true
                    }
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && sawInputEnd) {
                    // Nothing left to drain and nothing more going in.
                    sawOutputEnd = true
                }
            }
            if (accCount > 0) envelope.add(sqrt(acc / accCount).toFloat())
        } catch (e: Exception) {
            Log.d(TAG, "Decode window failed: ${e.message}")
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
        return DecodedAudio(envelope.toFloatArray(), mono.toArray(), sampleRate)
    }

    /** Tempo and phase from positive changes in the 20 ms energy envelope. */
    private fun analyseRhythm(envelope: FloatArray): RhythmAnalysis {
        if (envelope.size < 180) return RhythmAnalysis.NONE
        val onset = FloatArray(envelope.size)
        for (i in 2 until envelope.size) {
            val local = (envelope[i - 1] + envelope[i - 2]) * 0.5f
            onset[i] = (envelope[i] - local).coerceAtLeast(0f)
        }
        val mean = onset.average().toFloat()
        if (mean <= 0.0001f) return RhythmAnalysis.NONE
        for (i in onset.indices) onset[i] = (onset[i] - mean * 0.35f).coerceAtLeast(0f)

        val minLag = (60_000f / (MAX_BPM * WINDOW_MS)).roundToInt()
        val maxLag = (60_000f / (MIN_BPM * WINDOW_MS)).roundToInt()
        val scores = DoubleArray(maxLag + 1)
        for (lag in minLag..maxLag) {
            var dot = 0.0
            var left = 0.0
            var right = 0.0
            for (i in lag until onset.size) {
                val a = onset[i].toDouble()
                val b = onset[i - lag].toDouble()
                dot += a * b
                left += a * a
                right += b * b
            }
            val correlation = if (left > 0 && right > 0) dot / sqrt(left * right) else 0.0
            // Prefer the faster member of common half/double-time ambiguity,
            // but only very slightly; evidence remains the deciding factor.
            scores[lag] = correlation * (1.0 + (maxLag - lag) * 0.001)
        }
        val bestLag = (minLag..maxLag).maxByOrNull { scores[it] } ?: return RhythmAnalysis.NONE
        val best = scores[bestLag]
        val second = (minLag..maxLag)
            .filter { kotlin.math.abs(it - bestLag) > 2 }
            .maxOfOrNull { scores[it] } ?: 0.0
        if (bestLag == 0) return RhythmAnalysis.NONE
        val confidence = ((best - second) * 2.5 + best * 0.55).toFloat().coerceIn(0f, 1f)
        if (confidence < MIN_TEMPO_CONFIDENCE_TO_STORE) return RhythmAnalysis.NONE

        var beatPhase = 0
        var beatScore = -1.0
        for (phase in 0 until bestLag) {
            var score = 0.0
            var i = phase
            while (i < onset.size) {
                score += onset[i]
                i += bestLag
            }
            if (score > beatScore) { beatScore = score; beatPhase = phase }
        }

        // Of four consecutive beat phases, call the strongest accent the bar
        // downbeat. This is deliberately ignored downstream at low confidence.
        var downbeatBeat = 0
        var downbeatScore = -1.0
        for (candidate in 0..3) {
            var score = 0.0
            var beat = candidate
            while (beatPhase + beat * bestLag < onset.size) {
                score += onset[beatPhase + beat * bestLag]
                beat += 4
            }
            if (score > downbeatScore) { downbeatScore = score; downbeatBeat = candidate }
        }
        val left = scores.getOrElse(bestLag - 1) { best }
        val right = scores.getOrElse(bestLag + 1) { best }
        val denominator = left - 2.0 * best + right
        val fractional = if (kotlin.math.abs(denominator) > 1e-9) {
            (0.5 * (left - right) / denominator).coerceIn(-0.5, 0.5)
        } else 0.0
        return RhythmAnalysis(
            bpm = (60_000.0 / ((bestLag + fractional) * WINDOW_MS)).toFloat(),
            confidence = confidence,
            beatOffsetMs = beatPhase.toLong() * WINDOW_MS,
            downbeatOffsetMs = (beatPhase + downbeatBeat * bestLag).toLong() * WINDOW_MS,
        )
    }

    /** Snap a measured energy boundary to the nearest bar line. */
    private fun analysePhraseBoundary(
        rawOutroLeadMs: Long,
        durationMs: Long,
        rhythm: RhythmAnalysis,
        gridOriginMs: Long,
    ): Pair<Long, Float> {
        val bpm = rhythm.bpm ?: return 0L to 0f
        if (rawOutroLeadMs <= 0 || rhythm.confidence < MIN_GRID_CONFIDENCE) return 0L to 0f
        val barMs = 4.0 * 60_000.0 / bpm
        val rawPosition = durationMs - rawOutroLeadMs
        val bars = ((rawPosition - gridOriginMs) / barMs).roundToInt()
        val snappedPosition = gridOriginMs + (bars * barMs).toLong()
        val distance = kotlin.math.abs(snappedPosition - rawPosition)
        if (distance > barMs * 0.32) return 0L to 0f
        val lead = (durationMs - snappedPosition).coerceAtLeast(0L)
        val proximity = (1.0 - distance / (barMs * 0.32)).toFloat().coerceIn(0f, 1f)
        return lead to (rhythm.confidence * (0.65f + 0.35f * proximity)).coerceIn(0f, 1f)
    }

    private fun lastDownbeatLead(
        rhythm: RhythmAnalysis,
        windowStartMs: Long,
        durationMs: Long,
    ): Long {
        val bpm = rhythm.bpm ?: return 0L
        if (rhythm.confidence < MIN_GRID_CONFIDENCE) return 0L
        val barMs = (4f * 60_000f / bpm).toLong().coerceAtLeast(1L)
        var downbeat = windowStartMs + rhythm.downbeatOffsetMs
        if (downbeat > durationMs) return 0L
        downbeat += ((durationMs - downbeat) / barMs) * barMs
        return (durationMs - downbeat).coerceAtLeast(0L)
    }

    /** FFT chroma followed by the standard major/minor key templates. */
    private fun analyseKey(samples: FloatArray, sampleRate: Int): KeyAnalysis {
        if (samples.size < FFT_SIZE || sampleRate <= 0) return KeyAnalysis.NONE
        val chroma = DoubleArray(12)
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        var frameCount = 0
        var offset = 0
        while (offset + FFT_SIZE <= samples.size) {
            var energy = 0.0
            for (i in 0 until FFT_SIZE) {
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))
                val value = samples[offset + i] * window
                real[i] = value
                imag[i] = 0.0
                energy += value * value
            }
            if (energy > 0.0001) {
                fft(real, imag)
                for (bin in 1 until FFT_SIZE / 2) {
                    val hz = bin.toDouble() * sampleRate / FFT_SIZE
                    if (hz !in 55.0..5000.0) continue
                    val midi = 69.0 + 12.0 * (ln(hz / 440.0) / ln(2.0))
                    val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                    val magnitude = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                    chroma[pitchClass] += magnitude
                }
                frameCount++
            }
            offset += FFT_HOP
        }
        val total = chroma.sum()
        if (frameCount < 8 || total <= 0.0) return KeyAnalysis.NONE
        for (i in chroma.indices) chroma[i] /= total

        var best = Double.NEGATIVE_INFINITY
        var second = Double.NEGATIVE_INFINITY
        var bestRoot = 0
        var bestMode = "major"
        for (root in 0..11) {
            for ((mode, template) in listOf("major" to MAJOR_KEY, "minor" to MINOR_KEY)) {
                var score = 0.0
                for (pc in 0..11) score += chroma[pc] * template[(pc - root + 12) % 12]
                if (score > best) {
                    second = best; best = score; bestRoot = root; bestMode = mode
                } else if (score > second) second = score
            }
        }
        val confidence = ((best - second) / kotlin.math.abs(best).coerceAtLeast(0.001) * 4.0)
            .toFloat().coerceIn(0f, 1f)
        return if (confidence < MIN_KEY_CONFIDENCE_TO_STORE) KeyAnalysis.NONE
        else KeyAnalysis(bestRoot, bestMode, confidence)
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < n) {
                var wr = 1.0; var wi = 0.0
                for (k in 0 until len / 2) {
                    val even = i + k; val odd = even + len / 2
                    val vr = real[odd] * wr - imag[odd] * wi
                    val vi = real[odd] * wi + imag[odd] * wr
                    val ur = real[even]; val ui = imag[even]
                    real[even] = ur + vr; imag[even] = ui + vi
                    real[odd] = ur - vr; imag[odd] = ui - vi
                    val nextWr = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR; wr = nextWr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private data class DecodedAudio(val envelope: FloatArray, val samples: FloatArray, val sampleRate: Int) {
        companion object { val EMPTY = DecodedAudio(FloatArray(0), FloatArray(0), 0) }
    }
    private data class RhythmAnalysis(
        val bpm: Float?, val confidence: Float, val beatOffsetMs: Long, val downbeatOffsetMs: Long,
    ) { companion object { val NONE = RhythmAnalysis(null, 0f, 0L, 0L) } }
    private data class KeyAnalysis(val pitchClass: Int?, val mode: String?, val confidence: Float) {
        companion object { val NONE = KeyAnalysis(null, null, 0f) }
    }
    private class FloatCollector(initialCapacity: Int) {
        private var values = FloatArray(initialCapacity.coerceAtLeast(1024))
        private var size = 0
        fun add(value: Float) {
            if (size == values.size) values = values.copyOf(values.size * 2)
            values[size++] = value
        }
        fun toArray(): FloatArray = values.copyOf(size)
    }

    private val MAJOR_KEY = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_KEY = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)
    private const val FFT_SIZE = 4096
    private const val FFT_HOP = 2048
    private const val MIN_BPM = 70f
    private const val MAX_BPM = 180f
    private const val MIN_TEMPO_CONFIDENCE_TO_STORE = 0.12f
    private const val MIN_GRID_CONFIDENCE = 0.28f
    private const val MIN_KEY_CONFIDENCE_TO_STORE = 0.025f

    private fun leadInSilence(head: FloatArray): Long {
        if (head.isEmpty()) return 0
        val firstSound = head.indexOfFirst { it > SILENCE_RMS }
        if (firstSound <= 0) return 0
        return firstSound.toLong() * WINDOW_MS
    }

    /**
     * The length of the track's own fade-out.
     *
     * Walks back from the end while the envelope is climbing: a real fade is
     * monotonic-ish decay into silence, so the point where it stops rising as
     * you walk backwards is where the fade began. Zero when the track ends
     * loud, which is [endsAbruptly]'s case rather than this one.
     */
    private fun tailFadeLength(tail: FloatArray): Long {
        if (tail.size < 4) return 0
        val peak = tail.max()
        if (peak <= SILENCE_RMS) return 0
        val last = tail.last()
        if (last > peak * ABRUPT_END_RATIO) return 0

        var i = tail.size - 1
        var previous = last
        while (i > 0) {
            val value = tail[i]
            // Allow small rises so vibrato or a reverb tail does not end the walk.
            if (value < previous * 0.9f) break
            previous = maxOf(previous, value)
            if (value > peak * 0.7f) break
            i--
        }
        return (tail.size - 1 - i).toLong() * WINDOW_MS
    }

    /**
     * True when the final moments are still at real level relative to the
     * track's own loud passages - a cut rather than an ending.
     */
    private fun endsAbruptly(tail: FloatArray): Boolean {
        if (tail.size < 4) return false
        val peak = tail.max()
        if (peak <= SILENCE_RMS) return false
        // The very last few windows, so a single clipped sample cannot decide.
        val finalWindows = tail.takeLast(5)
        val finalLevel = finalWindows.average().toFloat()
        return finalLevel > peak * ABRUPT_END_RATIO
    }

    /**
     * How long before the end the energy last dropped and stayed down.
     *
     * Used to anchor an overlap at a musical boundary rather than at a fixed
     * offset that might land mid-phrase.
     */
    private fun outroLead(tail: FloatArray): Long {
        if (tail.size < 8) return 0
        val peak = tail.max()
        if (peak <= SILENCE_RMS) return 0
        val threshold = peak * 0.5f
        var i = tail.size - 1
        while (i > 0 && tail[i] < threshold) i--
        val windowsAfter = tail.size - 1 - i
        if (windowsAfter < 4) return 0
        return windowsAfter.toLong() * WINDOW_MS
    }

    /**
     * `MediaDataSource` over the playback cache.
     *
     * `MediaExtractor` wants random access and `SimpleCache` has no file path,
     * so each read opens a bounded `DataSpec` at the requested offset. Opening
     * per read is not as wasteful as it looks: the extractor reads in large
     * chunks and seeks rarely, and a cache hit costs a file handle rather than
     * a request.
     */
    private class CacheBackedDataSource(
        private val factory: CacheDataSource.Factory,
        private val uri: android.net.Uri,
        private val cacheKey: String,
    ) : MediaDataSource() {

        private val length: Long = CacheManager.getCachedLength(cacheKey)

        override fun getSize(): Long = length

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= length) return -1
            val source = factory.createDataSource()
            return try {
                val spec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(position)
                    .setLength(size.toLong().coerceAtMost(length - position))
                    .setKey(cacheKey)
                    .build()
                source.open(spec)
                var read = 0
                while (read < size) {
                    val n = source.read(buffer, offset + read, size - read)
                    if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                    read += n
                }
                if (read == 0) -1 else read
            } catch (e: Exception) {
                -1
            } finally {
                runCatching { source.close() }
            }
        }

        override fun close() = Unit
    }
}
