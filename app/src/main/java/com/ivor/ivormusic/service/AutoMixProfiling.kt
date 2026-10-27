package com.ivor.ivormusic.service

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import com.ivor.ivormusic.data.AudioProfileStore
import com.ivor.ivormusic.data.AudioProfiler
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Measures the songs AutoMix is about to mix: lead-in silence, the outro and
 * its tempo, read from small windows at each end rather than the whole file.
 *
 * Metered networks never fetch bytes for this; a song can still be profiled
 * there once normal playback has cached it whole. One song at a time.
 *
 * @param enabled crossfade and AutoMix both on.
 */
@UnstableApi
internal class AutoMixProfiling(
    private val context: Context,
    private val ioScope: CoroutineScope,
    private val store: () -> AudioProfileStore,
    private val cacheFactory: () -> CacheDataSource.Factory?,
    private val enabled: () -> Boolean,
) {
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val semaphore = Semaphore(1)

    fun profile(mediaItem: MediaItem, knownDurationMs: Long? = null) {
        if (!enabled()) return
        val id = mediaItem.mediaId
        val uri = mediaItem.localConfiguration?.uri ?: return
        if (uri.toString().startsWith(MusicStreamResolver.PLACEHOLDER_PREFIX)) return
        val store = store()
        if (store.peek(id) != null) return
        val factory = cacheFactory()
        // Every way out below is logged: a song with no profile mixes as a
        // plain fade, and a silent return made that impossible to diagnose
        // from a log.
        val durationMs = knownDurationMs?.takeIf { it > 0L }
            ?: mediaItem.mediaMetadata.durationMs?.takeIf { it > 0L }
            ?: run {
                KLog.d(TAG, "Profile: skipped $id, duration unknown")
                return
            }
        val isNetwork = uri.scheme == "http" || uri.scheme == "https"
        if (isNetwork && factory == null) {
            KLog.d(TAG, "Profile: skipped $id, no cache to read through")
            return
        }
        if (!inFlight.add(id)) return

        ioScope.launch {
            var acquired = false
            try {
                semaphore.acquire()
                acquired = true
                if (store.get(id) != null) return@launch
                if (isNetwork && ThemePreferences.isNetworkMetered(context) &&
                    !CacheManager.isFullyCached(id)
                ) {
                    KLog.d(TAG, "Profile: skipped $id, metered network and not fully cached")
                    return@launch
                }

                val startedMs = SystemClock.elapsedRealtime()
                val profile = withTimeoutOrNull(TIMEOUT_MS) {
                    AudioProfiler.profile(
                        songId = id,
                        context = context,
                        uri = uri,
                        cacheKey = id,
                        factory = factory,
                        durationMs = durationMs,
                        budgetMs = TIMEOUT_MS,
                    )
                }
                val tookMs = SystemClock.elapsedRealtime() - startedMs
                if (profile == null) {
                    KLog.d(TAG, "Profile: none for $id after ${tookMs}ms (scheme=${uri.scheme})")
                } else {
                    store.put(profile)
                    KLog.d(
                        TAG,
                        "Profile: $id in ${tookMs}ms lead=${profile.leadInSilenceMs} " +
                            "tail=${profile.tailFadeMs} abrupt=${profile.endsAbruptly} " +
                            "outro=${profile.outroLeadMs} " +
                            "bpm=${profile.bpm}@${profile.tempoConfidence} " +
                            "outroBpm=${profile.outroBpm}@${profile.outroTempoConfidence}"
                    )
                }
            } finally {
                if (acquired) semaphore.release()
                inFlight.remove(id)
            }
        }
    }

    private companion object {
        const val TAG = "MusicService"
        const val TIMEOUT_MS = 30_000L
    }
}
