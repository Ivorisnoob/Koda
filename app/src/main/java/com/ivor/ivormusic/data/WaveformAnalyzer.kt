package com.ivor.ivormusic.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Measures a whole song's loudness in one decode pass, so the seek bar can draw the parts of a
 * track nobody has reached yet.
 *
 * This is the difference between a waveform and a progress bar with texture. The bar exists so
 * somebody can see the quiet intro, the drop and the outro *before* arriving at them; an envelope
 * that only knows the part already played can show none of that, and grows and re-scales under
 * the eye for the whole of a first listen. So a song is decoded up front and drawn once, frozen.
 *
 * [com.ivor.ivormusic.service.WaveformTap]'s live sampling stays as the fallback for what this
 * cannot reach - an unsupported container, a song whose bytes may not be stored - which is why
 * [WaveformEnvelope] still accepts single samples. A song measured here is complete, and
 * [WaveformStore.record] then leaves it alone.
 *
 * **What it costs, and what it must not cost.** For a device file this is a local decode: no
 * network, a second or two of one background thread. For a YouTube stream the bytes have to be
 * fetched, and the whole point of routing that through [CacheManager]'s cache-backed source
 * under the song's own cache key is that they are the *same* bytes playback is about to read -
 * so a song listened through costs nothing extra and starts from disk. A song skipped away from
 * has cost the rest of its audio, and that is the accepted trade on any connection: the waveform
 * switch is what asks for it, and only Local Only refuses a stream ([canAnalyze]). A refusal is
 * not a failure - the song simply keeps the plain wavy bar and gains its shape from listening -
 * but a pass that broke is one, and is tried again ([retryLater]).
 *
 * Every network read goes through the ordinary playback data source, so invariant 3's bounded
 * ranged requests and [YouTubeRepository.uaForPlaybackUri]'s per-client User-Agent both hold
 * here without this file knowing about either.
 *
 * Reading the same song the player is reading is safe rather than merely unlikely to collide.
 * [verified September 2026 against the Media3 1.11.0 bytecode] [CacheManager]'s factory does not
 * set `FLAG_BLOCK_ON_CACHE`, so `CacheDataSource` takes `startReadWriteNonBlocking` and, when
 * the other reader holds the span, falls through to upstream without caching - no lock is
 * waited on and no span is written twice. Nothing here may set that flag.
 */
@UnstableApi
object WaveformAnalyzer {

    private const val TAG = "Waveform"

    /**
     * The window each measurement covers.
     *
     * Matched to the live sampler's cadence on purpose: the two paths fill the same buckets for
     * songs that get some of each, and a much shorter window reports a higher RMS for identical
     * audio, which would draw the analysed half of a song louder than the listened half.
     */
    private const val WINDOW_MS = 100

    /** How long to wait on the codec before looking at cancellation again. */
    private const val CODEC_TIMEOUT_US = 10_000L

    /**
     * How many empty codec turns after the last input before the pass gives up.
     *
     * A decoder that has been told the stream ended and then never reports it leaves this loop
     * spinning on a thread for as long as the song stays queued, since nothing else would end
     * it. Roughly two seconds of waiting, which is far longer than draining ever takes.
     */
    private const val MAX_STALLED_TURNS = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One decode at a time: this is a whole track through a codec, not a background trickle. */
    private val gate = Mutex()

    private var running: Job? = null

    @Volatile
    private var lastRequest: String? = null

    /** How long to wait before each further attempt at a song whose pass failed. */
    private val RETRY_DELAYS_MS = longArrayOf(1_500L, 4_000L, 10_000L)

    private val failureLock = Any()
    private var failedKey: String? = null
    private var failedAttempts = 0

    /**
     * How much of the start of a stream is kept in memory. Choosing a container means several
     * extractors each reading the head from the top, and every one of those was a fresh ranged
     * request: seconds of round trips on mobile data before the first sample was decoded.
     */
    private const val HEAD_BYTES = 64 * 1024

    /** A forward jump shorter than this is read through rather than reopened. */
    private const val SKIP_AHEAD_BYTES = 256L * 1024

    /**
     * Measure [songId] unless it is already measured, replacing any pass still running.
     *
     * Keyed on the URI as well as the id because a song reaches a real stream URL only after
     * resolution replaces its placeholder, and that arrives as a second look at the same song.
     * Safe to call from the player's own thread: nothing here blocks the caller.
     */
    fun request(
        context: Context,
        songId: String,
        uri: Uri,
        durationMs: Long?,
        musicCacheEnabled: Boolean,
    ) {
        if (songId.isBlank()) return
        val key = "$songId@$uri"
        if (key == lastRequest) return
        lastRequest = key
        val app = context.applicationContext
        running?.cancel()
        running = scope.launch {
            val failed = gate.withLock {
                if (WaveformStore.isComplete(app, songId)) return@withLock false
                if (!canAnalyze(app, uri, songId)) return@withLock false
                val job = coroutineContext[Job]
                val levels = try {
                    measure(app, songId, uri, durationMs, musicCacheEnabled) {
                        job?.isActive != false
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KLog.w(TAG, "Could not measure $songId", e)
                    null
                }
                // Abandoned for another song, which is not a failure of this one.
                if (job?.isActive == false) return@withLock false
                val envelope = levels?.let { WaveformEnvelope.measured(it) }
                    ?: return@withLock true
                WaveformStore.publish(app, songId, envelope)
                KLog.d(TAG, "Measured whole song $songId")
                false
            }
            if (failed) retryLater(key, songId)
        }
    }

    /**
     * Let the caller's next poll ask for [key] again, after a pause.
     *
     * [scar] A pass that failed used to stay latched in [lastRequest], so one dropped
     * connection left the song on the plain bar for as long as it stayed current - which on
     * mobile data read as the waveform loading for some songs and not others. The latch is
     * released here instead, a bounded number of times: a container no decoder can open fails
     * identically on every attempt, and must not decode in a loop for the length of the song.
     */
    private suspend fun retryLater(key: String, songId: String) {
        val attempt = synchronized(failureLock) {
            if (failedKey != key) {
                failedKey = key
                failedAttempts = 0
            }
            ++failedAttempts
        }
        if (attempt > RETRY_DELAYS_MS.size) {
            KLog.w(TAG, "Giving up on measuring $songId after $attempt attempts")
            return
        }
        delay(RETRY_DELAYS_MS[attempt - 1])
        if (lastRequest == key) lastRequest = null
    }

    /** Forget the last request, so the next one is taken even if it names the same song. */
    fun reset() {
        lastRequest = null
        running?.cancel()
        synchronized(failureLock) {
            failedKey = null
            failedAttempts = 0
        }
    }

    /**
     * Whether measuring this source is something the user's settings allow.
     *
     * A file on the device always is, and so is a song already whole in the cache - there is
     * nothing left to fetch for either, which is why the cache is asked before Local Only: that
     * switch forbids the network, not reading what is already on disk. Any other stream is
     * measured unless Local Only is on.
     *
     * [judgement October 2026] The waveform switch is the consent. This used to refuse a stream
     * while the music cache was off, since the bytes could not be kept and the pass became a
     * second download; the result was a setting that was on and a bar that stayed plain, with
     * nothing on screen to say why. With the cache off the pass now reads through the cache
     * without writing to it (see [measure]) and costs that second download.
     *
     * There is deliberately no metered check either: a connection-shaped rule hid the feature
     * from anyone mostly on mobile data.
     */
    private fun canAnalyze(context: Context, uri: Uri, songId: String): Boolean {
        return when (uri.scheme?.lowercase()) {
            null, "content", "file" -> true
            "http", "https" ->
                CacheManager.isFullyCached(songId) || !ThemePreferences.isLocalOnly(context)
            else -> false
        }
    }

    /**
     * Decode the track and return one RMS per bucket, `NaN` where no audio landed.
     *
     * Blocking, and deliberately so - [MediaExtractor] and [MediaCodec] are both blocking APIs
     * and wrapping them in a coroutine timeout would not interrupt either (the NewPipe scar in
     * docs/playback-streams.md, in miniature). Cancellation is cooperative instead: [isActive] is read once per
     * codec turn and at the top of every network read, so abandoning a song stops the pass
     * within one buffer rather than at the end of the track.
     */
    private fun measure(
        context: Context,
        songId: String,
        uri: Uri,
        hintedDurationMs: Long?,
        musicCacheEnabled: Boolean,
        isActive: () -> Boolean,
    ): FloatArray? {
        val extractor = MediaExtractor()
        var networkSource: Media3MediaDataSource? = null
        var codec: MediaCodec? = null
        try {
            when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    // With the music cache off this still reads whatever is stored but writes
                    // nothing, the same rule playback follows for that switch.
                    val factory = CacheManager.createCacheDataSourceFactory(
                        context, writeEnabled = musicCacheEnabled
                    ) ?: return null
                    networkSource = Media3MediaDataSource(factory, uri, songId, isActive)
                    extractor.setDataSource(networkSource)
                }
                else -> extractor.setDataSource(context, uri, null)
            }

            var track = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = index
                    format = candidate
                    break
                }
            }
            val input = format ?: return null
            val mime = input.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)

            // A container that does not declare its length still has the player's, which by the
            // time a song is playing is the decoded duration rather than a metadata guess.
            val durationUs = when {
                input.containsKey(MediaFormat.KEY_DURATION) -> input.getLong(MediaFormat.KEY_DURATION)
                hintedDurationMs != null && hintedDurationMs > 0L -> hintedDurationMs * 1000L
                else -> 0L
            }
            if (durationUs <= 0L) return null

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(input, null, null as MediaCrypto?, 0)
            codec.start()

            val levels = decode(codec, extractor, input, durationUs, isActive)
            // [scar] A read that fails reaches the extractor as the end of the stream, so the
            // decode finishes cleanly on half a song. Publishing that stored a waveform with a
            // flat, invented tail as complete, and a complete envelope is never measured again.
            if (networkSource?.failed == true) {
                KLog.w(TAG, "Stream for $songId broke off before the end; not keeping the result")
                return null
            }
            return levels
        } finally {
            try {
                codec?.stop()
            } catch (_: IllegalStateException) {
                // A codec torn down mid-flush has nothing to stop; releasing it is what matters.
            }
            codec?.release()
            extractor.release()
            networkSource?.close()
        }
    }

    private fun decode(
        codec: MediaCodec,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        durationUs: Long,
        isActive: () -> Boolean,
    ): FloatArray? {
        val buckets = WaveformEnvelope.BUCKETS
        val peaks = FloatArray(buckets) { Float.NaN }

        var sampleRate = inputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, 44_100)
        var channels = inputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 2)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        var scratch = FloatArray(0)
        var windowEnergy = 0.0
        var windowFrames = 0
        var windowStartUs = 0L
        var windowTarget = framesPerWindow(sampleRate)

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var measured = false
        var stalledTurns = 0

        while (!outputDone) {
            if (!isActive()) return null
            if (stalledTurns > MAX_STALLED_TURNS) {
                KLog.w(TAG, "Decoder stopped producing before the end of the stream")
                break
            }

            if (!inputDone) {
                val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)
                    val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(
                            index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val index = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val output = codec.outputFormat
                    sampleRate = output.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                    channels = output.optInt(MediaFormat.KEY_CHANNEL_COUNT, channels)
                    pcmEncoding = output.optInt(MediaFormat.KEY_PCM_ENCODING, pcmEncoding)
                    windowTarget = framesPerWindow(sampleRate)
                }
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED ->
                    if (inputDone) stalledTurns++
                else -> {
                    if (index >= 0) {
                        stalledTurns = 0
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(index)
                            if (buffer != null) {
                                buffer.limit(info.offset + info.size)
                                buffer.position(info.offset)
                                val samples = readSamples(buffer, pcmEncoding, scratch)
                                    ?: return null
                                scratch = samples.first
                                val count = samples.second
                                var cursor = 0
                                while (cursor < count) {
                                    if (windowFrames == 0) {
                                        windowStartUs = info.presentationTimeUs +
                                            (cursor / channels) * 1_000_000L / sampleRate
                                    }
                                    var mono = 0f
                                    var lane = 0
                                    while (lane < channels && cursor < count) {
                                        mono += scratch[cursor]
                                        cursor++
                                        lane++
                                    }
                                    if (lane > 0) {
                                        mono /= lane
                                        windowEnergy += mono.toDouble() * mono
                                        windowFrames++
                                    }
                                    if (windowFrames >= windowTarget) {
                                        fold(peaks, windowStartUs, durationUs, windowEnergy, windowFrames)
                                        measured = true
                                        windowEnergy = 0.0
                                        windowFrames = 0
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        }

        if (windowFrames > 0) {
            fold(peaks, windowStartUs, durationUs, windowEnergy, windowFrames)
            measured = true
        }
        return peaks.takeIf { measured }
    }

    private fun framesPerWindow(sampleRate: Int): Int =
        (sampleRate.toLong() * WINDOW_MS / 1000L).toInt().coerceAtLeast(1)

    /** Keep the loudest window in each bucket, as a listening sample does. */
    private fun fold(
        peaks: FloatArray,
        atUs: Long,
        durationUs: Long,
        energy: Double,
        frames: Int,
    ) {
        if (frames <= 0) return
        val bucket = (atUs.toDouble() / durationUs * peaks.size).toInt()
            .coerceIn(0, peaks.size - 1)
        val rms = sqrt(energy / frames).toFloat().coerceIn(0f, 1f)
        val existing = peaks[bucket]
        if (existing.isNaN() || rms > existing) peaks[bucket] = rms
    }

    /**
     * Copy one output buffer into a reusable float scratch, growing it as needed.
     *
     * In bulk rather than sample by sample: this runs over every frame of the track, and
     * [ByteBuffer.getShort] per sample is several million virtual calls where one array copy and
     * a primitive loop will do.
     */
    private fun readSamples(
        buffer: ByteBuffer,
        pcmEncoding: Int,
        scratch: FloatArray,
    ): Pair<FloatArray, Int>? {
        val ordered = buffer.order(ByteOrder.nativeOrder())
        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = ordered.asFloatBuffer()
                val count = floats.remaining()
                val out = if (scratch.size >= count) scratch else FloatArray(count)
                floats.get(out, 0, count)
                out to count
            }
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = ordered.asShortBuffer()
                val count = shorts.remaining()
                val out = if (scratch.size >= count) scratch else FloatArray(count)
                for (i in 0 until count) out[i] = shorts.get(i) / 32_768f
                out to count
            }
            // 8-bit and 24/32-bit output exist in the platform but no decoder Koda plays
            // produces them. Measuring them wrongly would be worse than not measuring them.
            else -> null
        }
    }

    private fun MediaFormat.optInt(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback

    /**
     * A [MediaDataSource] over a Media3 [DataSource], so [MediaExtractor] can read a stream
     * through the app's own playback pipeline instead of opening its own connection.
     *
     * That indirection is the point. Handing the extractor an https URL would give it an
     * unbounded GET with the wrong User-Agent - the two things docs/youtube-data.md and invariant 3 say never to
     * do to googlevideo - and would fetch bytes nothing else could use. Going through
     * [CacheManager]'s cache-backed factory under the song's own cache key means every request
     * is chunked by [ChunkedStreamDataSource], carries the User-Agent its issuing client needs,
     * and lands in the cache playback reads from.
     *
     * Reads are sequential apart from the container's own seeks (a WebM's cues sit at the end of
     * the file), so the open source is kept and continued whenever the extractor asks for the
     * position it left off at, and reopened only when it genuinely jumps.
     *
     * The fields are volatile because the reads may not arrive on the thread that started the
     * pass: the platform can run extraction in its own `mediaextractor` process and call back
     * into this one over binder. The calls are still serialized - one extractor reads one
     * source in order - so this needs visibility rather than locking.
     */
    private class Media3MediaDataSource(
        private val factory: DataSource.Factory,
        private val uri: Uri,
        private val cacheKey: String,
        private val isActive: () -> Boolean,
    ) : MediaDataSource() {

        @Volatile private var source: DataSource? = null
        @Volatile private var nextPosition = -1L
        @Volatile private var totalLength = -1L

        /** The first [HEAD_BYTES] of the stream, as far as they have been read in order. */
        private val head = ByteArray(HEAD_BYTES)
        @Volatile private var headFilled = 0

        /**
         * Whether a read or an open failed for a reason other than the pass being abandoned.
         * The extractor swallows that into an ordinary end of stream, so this is the only way
         * the caller learns the song it decoded was cut short.
         */
        @Volatile var failed = false
            private set

        override fun getSize(): Long {
            if (totalLength < 0L) {
                // Opening at the start with no length is what resolves the total; an extractor
                // that cannot learn the size falls back to reading forward, which still works.
                try {
                    openAt(0L)
                } catch (e: IOException) {
                    KLog.d(TAG, "Waveform source could not resolve its length", e)
                }
            }
            return totalLength
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (!isActive()) throw IOException("Waveform pass abandoned")
            if (size == 0) return 0
            if (totalLength in 0..position) return -1
            if (position < headFilled) {
                // A short read is fine here: the extractor asks again for the rest.
                val count = minOf(size, headFilled - position.toInt())
                System.arraycopy(head, position.toInt(), buffer, offset, count)
                return count
            }
            try {
                val open = sourceAt(position) ?: return -1
                val read = open.read(buffer, offset, size)
                if (read == C.RESULT_END_OF_INPUT) return -1
                if (position == headFilled.toLong() && headFilled < HEAD_BYTES) {
                    val keep = minOf(read, HEAD_BYTES - headFilled)
                    System.arraycopy(buffer, offset, head, headFilled, keep)
                    headFilled += keep
                }
                nextPosition = position + read
                return read
            } catch (e: IOException) {
                if (isActive()) failed = true
                throw e
            }
        }

        override fun close() {
            release()
        }

        /**
         * The source positioned at [position]: the open one when the read continues it, the
         * open one read forward when the jump is short, and a new request only otherwise.
         */
        private fun sourceAt(position: Long): DataSource? {
            val open = source
            if (open != null && position == nextPosition) return open
            val gap = position - nextPosition
            if (open != null && nextPosition >= 0L && gap in 1..SKIP_AHEAD_BYTES) {
                val scratch = ByteArray(16 * 1024)
                var left = gap
                while (left > 0L) {
                    if (!isActive()) throw IOException("Waveform pass abandoned")
                    val read = open.read(scratch, 0, minOf(left, scratch.size.toLong()).toInt())
                    if (read == C.RESULT_END_OF_INPUT) return null
                    left -= read
                }
                nextPosition = position
                return open
            }
            openAt(position)
            return source
        }

        private fun openAt(position: Long) {
            release()
            val open = factory.createDataSource()
            val resolved = try {
                open.open(
                    DataSpec.Builder()
                        .setUri(uri)
                        .setPosition(position)
                        // Playback reads this song's bytes under its media id (see
                        // buildMediaItemWithUri's setCustomCacheKey); writing under any other
                        // key would fill the cache with a second copy nothing else can use.
                        .setKey(cacheKey)
                        .build()
                )
            } catch (e: IOException) {
                // A source that failed to open still holds whatever it had begun to acquire.
                try {
                    open.close()
                } catch (_: IOException) {
                }
                throw e
            }
            source = open
            nextPosition = position
            if (resolved != C.LENGTH_UNSET.toLong()) totalLength = position + resolved
        }

        private fun release() {
            try {
                source?.close()
            } catch (e: IOException) {
                KLog.d(TAG, "Waveform source close failed", e)
            }
            source = null
            nextPosition = -1L
        }
    }
}
