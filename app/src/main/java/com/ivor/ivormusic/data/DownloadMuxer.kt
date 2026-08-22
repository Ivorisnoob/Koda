package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Joins a separate video track and audio track into one MP4.
 *
 * YouTube only serves a single ready-made file at 360p; every higher quality
 * arrives as video-only plus a separate audio stream (see
 * `YouTubeRepository.parseQualitiesFromStreamingData`). Downloading anything
 * watchable therefore means remuxing.
 *
 * This is a **container copy, not a transcode** - samples are read from each
 * input and written straight into the output, so it costs IO and almost no CPU,
 * and quality is untouched.
 *
 * The MP4 muxer is picky about codecs: H.264/H.265 video with AAC audio is
 * reliable, VP9/Opus in MP4 is not. Rather than guess, [mux] reports failure
 * when the muxer rejects a track so the caller can fall back to the progressive
 * stream.
 */
object DownloadMuxer {

    private const val TAG = "DownloadMuxer"

    /** Fallback sample buffer when the format does not declare a max size. */
    private const val DEFAULT_SAMPLE_SIZE = 1 shl 20 // 1 MB

    /**
     * Remux [videoFile] and, when present, [audioFile] into [output].
     *
     * @return true when the output holds the requested video and audio tracks.
     */
    fun mux(videoFile: File, audioFile: File?, output: FileDescriptor): Boolean {
        var muxer: MediaMuxer? = null
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null

        return try {
            muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            val videoTrack = selectTrack(videoExtractor, "video/")
                ?: run {
                    KLog.e(TAG, "No video track in ${videoFile.name}")
                    return false
                }
            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val outputVideoTrack = muxer.addTrack(videoFormat)

            var outputAudioTrack = -1
            var audioFormat: MediaFormat? = null
            if (audioFile != null && audioFile.length() > 0) {
                audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
                val audioTrack = selectTrack(audioExtractor, "audio/")
                if (audioTrack != null) {
                    audioFormat = audioExtractor.getTrackFormat(audioTrack)
                    outputAudioTrack = muxer.addTrack(audioFormat)
                } else {
                    // Publishing this as a successful silent download hides a
                    // broken audio response. Let the worker discard the partial
                    // mux and use its progressive fallback instead.
                    KLog.e(TAG, "No audio track in ${audioFile.name}")
                    return false
                }
            }

            muxer.start()

            copyTrack(videoExtractor, muxer, outputVideoTrack, videoFormat)
            if (audioExtractor != null && audioFormat != null && outputAudioTrack >= 0) {
                copyTrack(audioExtractor, muxer, outputAudioTrack, audioFormat)
            }

            muxer.stop()
            true
        } catch (e: Exception) {
            // Most often IllegalArgumentException from addTrack for a codec the
            // MP4 muxer will not accept (VP9, Opus, sometimes AV1).
            KLog.e(TAG, "Muxing failed: ${e.message}", e)
            false
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor?.release() }
            runCatching { audioExtractor?.release() }
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) {
                extractor.selectTrack(i)
                return i
            }
        }
        return null
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        outputTrack: Int,
        format: MediaFormat
    ) {
        val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(DEFAULT_SAMPLE_SIZE)
        } else {
            DEFAULT_SAMPLE_SIZE
        }

        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()

        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break

            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            // MediaExtractor.SAMPLE_FLAG_SYNC and BUFFER_FLAG_KEY_FRAME share a
            // value, so the extractor's flags carry over directly.
            info.flags = extractor.sampleFlags

            muxer.writeSampleData(outputTrack, buffer, info)
            extractor.advance()
        }
    }
}
