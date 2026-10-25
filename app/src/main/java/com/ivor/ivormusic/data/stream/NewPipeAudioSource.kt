package com.ivor.ivormusic.data.stream

import com.ivor.ivormusic.util.KLog
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamExtractor

/**
 * A NewPipe audio resolution, and whether it failed on the bot check. The
 * second half matters to whoever runs next: NewPipe mints a fresh visitorData
 * for each of its clients, so its refusal already says a new token will not
 * help.
 */
internal class NewPipeAudio(val url: String?, val botChecked: Boolean = false)

/** Whether a NewPipe extraction failed because YouTube asked it to prove it is not a bot. */
internal fun Throwable.isNewPipeBotCheck(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { it is SignInConfirmNotBotException }

/**
 * Audio through NewPipe Extractor's own client chain, as the fallback behind
 * the direct visionOS call. Its identities are its own, which is the point of
 * a fallback and also its cost: one extraction is eight requests and three
 * fresh visitor ids.
 *
 * @param service the YouTube service, on a NewPipe that is already
 * initialised with a downloader.
 * @param detached where the blocking extractions run. It must not be a child
 * of any caller; see [withinBudget].
 */
internal class NewPipeAudioSource(
    private val service: YoutubeService,
    private val detached: CoroutineScope,
) {
    /**
     * An audio URL for playback, or none once [budgetMs] has passed.
     *
     * Bound the wait, not the work. NewPipe's `fetchPage()` blocks and cannot
     * be interrupted, so a timeout around it where it runs achieves nothing:
     * the enclosing scope does not return until the blocking child does. The
     * extraction therefore starts on [detached], and only the await is
     * bounded. When the budget expires the caller moves on to its next client
     * while the extraction finishes in the background, capped by the
     * downloader's own per-request timeout.
     *
     * Abandoning rather than cancelling is deliberate: nothing is written
     * outside the returned value, and a late result is simply discarded.
     *
     * [scar] Being slow here used to be indistinguishable from failing. A
     * stalled extraction ran out the player's whole resolution budget and the
     * song was skipped, with a working fallback sitting unused behind it.
     */
    suspend fun withinBudget(
        videoId: String,
        preference: AudioPreference,
        budgetMs: Long,
    ): NewPipeAudio {
        val extraction = detached.async { playbackUrl(videoId, preference) }
        return try {
            withTimeoutOrNull(budgetMs) { extraction.await() } ?: run {
                // Cancelling cannot interrupt a thread already inside
                // fetchPage(), but it marks the work abandoned so nothing
                // runs after it and a queued extraction never starts at all.
                extraction.cancel()
                KLog.w(STREAM_TAG, "NewPipe over its ${budgetMs}ms budget videoId=$videoId")
                NewPipeAudio(null)
            }
        } catch (e: CancellationException) {
            // The caller went away rather than the budget expiring.
            extraction.cancel()
            throw e
        }
    }

    /** An AAC/M4A URL for a file download: the smallest stream when [smallest], else the best. */
    suspend fun m4aUrl(videoId: String, smallest: Boolean): NewPipeAudio =
        extract(videoId, "M4A") { extractor ->
            val m4a = originalAudioStreams(extractor.audioStreams.filter { it.isUrl }).filter { stream ->
                stream.format?.suffix.equals("m4a", ignoreCase = true) ||
                    stream.codec?.contains("mp4a", ignoreCase = true) == true
            }
            val chosen = if (smallest) {
                m4a.minByOrNull { it.averageBitrate }
            } else {
                m4a.maxByOrNull { it.averageBitrate }
            }
            chosen?.content?.takeIf(String::isNotBlank)
        }

    /**
     * The URL to play. Generated manifest content shares the stream model
     * with direct URLs, and only a direct URL can be handed to the player.
     * With no audio-only stream, a muxed one still carries an audio track.
     */
    private suspend fun playbackUrl(videoId: String, preference: AudioPreference): NewPipeAudio =
        extract(videoId, "audio") { extractor ->
            val audioOnly = originalAudioStreams(extractor.audioStreams.filter { it.isUrl })
            // NewPipe's averageBitrate is in kbps.
            val chosen = when (preference) {
                AudioPreference.LOWEST -> audioOnly.minByOrNull { it.averageBitrate }
                AudioPreference.BALANCED -> audioOnly.minByOrNull { abs(it.averageBitrate - 128) }
                AudioPreference.HIGHEST -> audioOnly.maxByOrNull { it.averageBitrate }
            }
            chosen?.content?.takeIf { it.isNotBlank() }
                ?: extractor.videoStreams.asSequence()
                    .filter { it.isUrl }
                    .mapNotNull { it.content?.takeIf(String::isNotBlank) }
                    .firstOrNull()
        }

    private suspend fun extract(
        videoId: String,
        what: String,
        pick: (StreamExtractor) -> String?,
    ): NewPipeAudio = withContext(Dispatchers.IO) {
        try {
            val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            NewPipeAudio(pick(extractor))
        } catch (e: CancellationException) {
            // The budget expired or the caller went away: not an extraction
            // failure, and it must not be reported as one.
            throw e
        } catch (e: Exception) {
            KLog.w(STREAM_TAG, "NewPipe $what failed videoId=$videoId: ${e.message}")
            NewPipeAudio(url = null, botChecked = e.isNewPipeBotCheck())
        }
    }
}

/** NewPipe's side of [originalTrackOnly]: the same rule over its stream model. */
internal fun originalAudioStreams(streams: List<AudioStream>): List<AudioStream> {
    val originals = streams.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
    if (originals.isNotEmpty()) return originals
    return streams.filter { it.audioTrackType == null }
}
