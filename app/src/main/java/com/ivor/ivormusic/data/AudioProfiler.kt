package com.ivor.ivormusic.data

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
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
        uri: android.net.Uri,
        cacheKey: String,
        factory: CacheDataSource.Factory,
        durationMs: Long,
    ): AudioProfile? = withContext(Dispatchers.Default) {
        if (durationMs <= 0) return@withContext null
        var extractor: MediaExtractor? = null
        var source: CacheBackedDataSource? = null
        try {
            source = CacheBackedDataSource(factory, uri, cacheKey)
            if (source.getSize() <= 0) return@withContext null

            extractor = MediaExtractor()
            extractor.setDataSource(source)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val head = decodeEnvelope(extractor, format, 0L, HEAD_WINDOW_US)
            val tailStartUs = ((durationMs * 1000L) - TAIL_WINDOW_US).coerceAtLeast(0L)
            val tail = decodeEnvelope(extractor, format, tailStartUs, TAIL_WINDOW_US)

            if (head.isEmpty() && tail.isEmpty()) return@withContext null

            AudioProfile(
                songId = songId,
                leadInSilenceMs = leadInSilence(head),
                tailFadeMs = tailFadeLength(tail),
                endsAbruptly = endsAbruptly(tail),
                outroLeadMs = outroLead(tail),
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
    private fun decodeEnvelope(
        extractor: MediaExtractor,
        format: MediaFormat,
        fromUs: Long,
        windowUs: Long,
    ): FloatArray {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(0)
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull()
            ?: return FloatArray(0)

        val envelope = ArrayList<Float>(512)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2)
            val samplesPerWindow = (sampleRate * channels * WINDOW_MS / 1000).coerceAtLeast(1)

            var acc = 0.0
            var accCount = 0

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
                    if (outBuffer != null && bufferInfo.size > 0) {
                        val shorts = outBuffer.asShortBuffer()
                        while (shorts.hasRemaining()) {
                            val sample = shorts.get() / 32768f
                            acc += (sample * sample).toDouble()
                            accCount++
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
        return envelope.toFloatArray()
    }

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
