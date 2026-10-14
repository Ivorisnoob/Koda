package com.ivor.ivormusic.data.stream

import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What resolving a song's stream came to. */
internal sealed interface AudioResolution {
    /** A URL to play, and which path produced it. */
    class Resolved(val url: String, val source: String) : AudioResolution

    /** Every path was tried and none gave a URL. */
    data object Unresolved : AudioResolution
}

/**
 * Turns a video id into an audio URL the player can be handed.
 *
 * Three paths, in the order of what they cost and how well they hold up:
 *
 * 1. **visionOS**, one call under Koda's own identity, a new identity vetted
 *    before its URL is returned ([PlayerSession.visionOsVetted]).
 * 2. **NewPipe**, bounded by [newPipeBudgetMs], with identities of its own.
 * 3. **ANDROID_VR then IOS**, which can cover a failure specific to the
 *    first two but cannot be relied on for a whole file.
 *
 * @param newPipeBudgetMs how long playback waits on NewPipe. Sized against
 * the player's own resolution timeout so the third path still has room
 * inside it; the two are a pair.
 * @param onResponse every `/player` response that reached status OK on the
 * third path, for the caller to harvest captions and loudness from.
 * @param onLoudness the loudness of the format chosen on the first path,
 * whose response states it per format rather than once; see
 * [formatLoudnessDb].
 */
internal class AudioStreamResolver(
    private val session: PlayerSession,
    private val newPipe: NewPipeAudioSource,
    private val newPipeBudgetMs: Long,
    private val onResponse: (videoId: String, root: JSONObject) -> Unit,
    private val onLoudness: (videoId: String, loudnessDb: Float) -> Unit,
) {
    /** The URL to play [videoId] from, chosen for [preference]. */
    suspend fun forPlayback(videoId: String, preference: AudioPreference): AudioResolution =
        withContext(Dispatchers.IO) {
            val startMs = System.currentTimeMillis()
            fun resolved(url: String, source: String): AudioResolution {
                BotCheckVerdict.clearOnSuccess()
                KLog.i(
                    STREAM_TAG,
                    "Resolve[$source] OK videoId=$videoId dt=${System.currentTimeMillis() - startMs}ms",
                )
                return AudioResolution.Resolved(url, source)
            }

            val direct = session.visionOsVetted(videoId) { pickAudio(it, preference)?.url }
            direct.answer?.let { pickAudioLogged(videoId, it.streamingData, preference) }?.let { pick ->
                pick.loudnessDb?.let { onLoudness(videoId, it) }
                return@withContext resolved(pick.url, "visionOS")
            }

            val extracted = newPipe.withinBudget(videoId, preference, newPipeBudgetMs)
            extracted.url?.takeIf { it.isNotEmpty() }?.let {
                return@withContext resolved(it, "NewPipe fallback")
            }

            val last = session.nativeFallback(
                videoId,
                botCheckedAlready = extracted.botChecked || direct.botChecked,
                onResponse = { onResponse(videoId, it) },
            )?.let { pickAudioLogged(videoId, it.streamingData, preference) }
            if (last != null) return@withContext resolved(last.url, "InnerTube fallback")

            KLog.e(
                STREAM_TAG,
                "Resolve FAIL videoId=$videoId all clients exhausted " +
                    "dt=${System.currentTimeMillis() - startMs}ms",
            )
            AudioResolution.Unresolved
        }

    /**
     * An AAC/M4A URL for a file download, through the same three paths. The
     * download's own ranged loop is its recovery, so nothing is probed here.
     */
    suspend fun forDownload(videoId: String, smallest: Boolean): AudioResolution =
        withContext(Dispatchers.IO) {
            val direct = session.visionOs(videoId)
            direct.answer?.let { pickM4aUrl(it.streamingData, smallest) }?.let {
                BotCheckVerdict.clearOnSuccess()
                return@withContext AudioResolution.Resolved(it, "visionOS")
            }

            val extracted = newPipe.m4aUrl(videoId, smallest)
            extracted.url?.takeIf { it.isNotBlank() }?.let {
                BotCheckVerdict.clearOnSuccess()
                return@withContext AudioResolution.Resolved(it, "NewPipe fallback")
            }

            session.nativeFallback(
                videoId,
                botCheckedAlready = extracted.botChecked || direct.botChecked,
                onResponse = { onResponse(videoId, it) },
            )?.let { pickM4aUrl(it.streamingData, smallest) }
                ?.takeIf { it.isNotBlank() }
                ?.let { AudioResolution.Resolved(it, "InnerTube fallback") }
                ?: AudioResolution.Unresolved
        }

    private fun pickAudioLogged(
        videoId: String,
        streamingData: JSONObject,
        preference: AudioPreference,
    ): AudioPick? {
        val pick = pickAudio(streamingData, preference)
        if (pick == null) {
            val (ciphered, total) = cipheredFormatCount(streamingData)
            KLog.w(STREAM_TAG, "No usable URL videoId=$videoId ciphered=$ciphered/$total")
        } else if (pick.muxed) {
            KLog.w(STREAM_TAG, "Using a muxed video format videoId=$videoId (no audio-only)")
        }
        return pick
    }
}
