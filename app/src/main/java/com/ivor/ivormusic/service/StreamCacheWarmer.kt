package com.ivor.ivormusic.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.LinkedHashMap

/**
 * Writes the start - and, unmetered, the end - of an upcoming song's stream
 * into the disk cache, so the eventual transition or skip starts playing from
 * disk instead of waiting on the network, and AutoMix can read the track's
 * outro without a network crawl.
 *
 * Only real network streams are warmed: local files, songs already whole on
 * disk, and the resolver's sentinel URIs are skipped.
 */
@UnstableApi
internal class StreamCacheWarmer(
    private val context: Context,
    private val ioScope: CoroutineScope,
    private val cacheFactory: () -> CacheDataSource.Factory?,
    private val cacheEnabled: () -> Boolean,
) {
    // Songs whose stream head has been (or is being) written into the disk
    // cache this session, so each prefetch round doesn't re-warm them.
    private val warmedIds = java.util.Collections.newSetFromMap(
        java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Boolean>(MAX_WARMED_IDS, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
                    size > MAX_WARMED_IDS
            }
        )
    )

    // One warm at a time: warming must never contend with the current song's
    // own buffering for the whole prefetch window.
    private val semaphore = Semaphore(1)

    /** Forget what was warmed, so every song is warmed again under a fresh stream URL. */
    fun clear() {
        warmedIds.clear()
    }

    fun warm(videoId: String, uri: Uri?) {
        if (!ThemePreferences.isPlaybackPreloadEnabled(context)) return
        val factory = cacheFactory() ?: return
        if (!cacheEnabled() || uri == null) return
        if (uri.scheme != "http" && uri.scheme != "https") return
        val url = uri.toString()
        if (url.startsWith(MusicStreamResolver.PLACEHOLDER_PREFIX) ||
            url.startsWith(MusicStreamResolver.CACHED_PREFIX)
        ) return
        if (!warmedIds.add(videoId)) return
        if (CacheManager.isFullyCached(videoId)) return

        ioScope.launch {
            semaphore.acquire()
            try {
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(0)
                    .setLength(HEAD_BYTES)
                    // Playback reads the cache under the song id (see
                    // MusicStreamResolver's setCustomCacheKey), so the warm
                    // must write under the same key.
                    .setKey(videoId)
                    .build()
                CacheWriter(factory.createDataSource(), dataSpec, null, null).cache()
                KLog.d(TAG, "Warm: cached stream head for $videoId")

                // The tail matters as much as the head, for a different
                // reason: AutoMix reads the last twenty seconds to decide how
                // to transition out of this track, and without these bytes
                // every read crawls over the network and profiling loses its
                // race with the song itself - which is why every transition
                // used to degrade to FALLBACK. googlevideo carries the full
                // size in `clen`; without it there is no honest end to seek
                // from. Metered networks skip it, matching the profiler's own
                // guard, so warming never spends data AutoMix would refuse to
                // use anyway.
                val totalBytes = uri.getQueryParameter("clen")?.toLongOrNull() ?: 0L
                val canWarmTail = totalBytes > HEAD_BYTES + TAIL_BYTES &&
                    !CacheManager.isFullyCached(videoId) &&
                    !ThemePreferences.isNetworkMetered(context)
                if (canWarmTail) {
                    val tailSpec = DataSpec.Builder()
                        .setUri(uri)
                        .setPosition(totalBytes - TAIL_BYTES)
                        .setLength(TAIL_BYTES)
                        .setKey(videoId)
                        .build()
                    CacheWriter(factory.createDataSource(), tailSpec, null, null).cache()
                    KLog.d(TAG, "Warm: cached stream tail for $videoId")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Retryable on the next prefetch round (e.g. an expired URL
                // that resolution will refresh).
                warmedIds.remove(videoId)
                KLog.w(TAG, "Warm: failed for $videoId: ${e.message}")
            } finally {
                semaphore.release()
            }
        }
    }

    private companion object {
        const val TAG = "MusicService"
        const val MAX_WARMED_IDS = 256

        // Stream head pre-cached for upcoming songs: ~30s of opus audio, enough
        // to cover the 0.5s start buffer plus the first ranged chunk's RTT.
        const val HEAD_BYTES = 512L * 1024

        // Tail warm for AutoMix: comfortably more than the profiler's
        // twenty-second outro window at any audio bitrate YouTube serves.
        const val TAIL_BYTES = 1024L * 1024
    }
}
