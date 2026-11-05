package com.ivor.ivormusic.service

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.lossless.LosslessAuthException
import com.ivor.ivormusic.data.lossless.LosslessClient
import com.ivor.ivormusic.data.lossless.LosslessSettings
import com.ivor.ivormusic.data.lossless.LosslessStreamUri
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * The lossless step of [MusicStreamResolver]: asks the listener's lossless
 * server for the song before YouTube is asked.
 *
 * It only ever adds a better source and never takes one away. Whatever goes
 * wrong - streaming off, a metered network the listener kept it off, Local
 * Only, the server slow, refusing, or without the song - the answer is null and
 * the resolver carries on to the disk cache and YouTube exactly as before. A
 * song that found nothing is not asked about again for a while, so a queue the
 * server does not carry costs one search per song, not one per play.
 *
 * Settings are read fresh on every call ([LosslessSettings] holds no copy), so
 * a switch flipped in Settings applies to the next song.
 */
internal class LosslessStreamSource(context: Context) {
    private val appContext = context.applicationContext

    /** Song id -> elapsedRealtime until which the server is not asked again. */
    private val restUntil = ConcurrentHashMap<String, Long>()

    /** Elapsed time until which the whole server is left alone (it refused us). */
    @Volatile
    private var serverRestUntil = 0L

    /** Whether a resolution of [videoId] should try the server at all right now. */
    fun wanted(videoId: String): Boolean {
        val config = runCatching { LosslessSettings.read(appContext) }.getOrNull() ?: return false
        if (!config.streamsLossless) return false
        if (ThemePreferences.isLocalOnly(appContext)) return false
        if (config.streamingUnmeteredOnly && ThemePreferences.isNetworkMetered(appContext)) return false
        val now = SystemClock.elapsedRealtime()
        if (now < serverRestUntil) return false
        return (restUntil[videoId] ?: 0L) <= now
    }

    /** The lossless URI for [item], marked, or null to let YouTube have it. */
    suspend fun resolve(item: MediaItem): Uri? {
        val videoId = item.mediaId
        if (!wanted(videoId)) return null
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val artist = metadata.artist?.toString().orEmpty()
        val duration = metadata.durationMs ?: 0L
        val config = LosslessSettings.read(appContext)
        val client = LosslessClient(appContext, config)

        return try {
            var timedOut = true
            val found = withTimeoutOrNull(BUDGET_MS) {
                client.resolve(title, artist, duration, config.streamingQuality)
                    .also { timedOut = false }
            }
            if (found == null) {
                KLog.d(TAG, if (timedOut) "Lossless server too slow for $videoId" else "No lossless copy of $videoId")
                rest(videoId, if (timedOut) FAILURE_REST_MS else NOT_FOUND_REST_MS)
                null
            } else {
                KLog.d(TAG, "Lossless: $videoId -> ${found.first.quality.ifBlank { "lossless" }}")
                LosslessStreamUri.mark(found.second)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LosslessAuthException) {
            // A wrong address or secret fails every song the same way; stop
            // asking until it is likely to have been fixed.
            KLog.w(TAG, "Lossless server refused access; pausing it", e)
            serverRestUntil = SystemClock.elapsedRealtime() + SERVER_REST_MS
            null
        } catch (e: Exception) {
            KLog.w(TAG, "Lossless lookup failed for $videoId", e)
            rest(videoId, FAILURE_REST_MS)
            null
        }
    }

    /** The lossless stream for [videoId] failed to play: YouTube has it for a while. */
    fun playbackFailed(videoId: String) {
        rest(videoId, FAILURE_REST_MS)
    }

    fun clear() {
        restUntil.clear()
        serverRestUntil = 0L
    }

    private fun rest(videoId: String, durationMs: Long) {
        restUntil[videoId] = SystemClock.elapsedRealtime() + durationMs
        if (restUntil.size > MAX_REMEMBERED) {
            val now = SystemClock.elapsedRealtime()
            restUntil.entries.removeAll { it.value <= now }
        }
    }

    companion object {
        private const val TAG = "LosslessStream"

        /**
         * Search, stream address and a look at what it points to. Kept well
         * under the resolver's own 20s so YouTube still has its full budget
         * when the server is slow.
         */
        const val BUDGET_MS = 7_000L

        private const val NOT_FOUND_REST_MS = 30 * 60 * 1000L
        private const val FAILURE_REST_MS = 10 * 60 * 1000L
        private const val SERVER_REST_MS = 5 * 60 * 1000L
        private const val MAX_REMEMBERED = 512

        /** How long a resolved lossless address is replayed from memory. */
        const val URI_TTL_MS = 20 * 60 * 1000L
    }
}
