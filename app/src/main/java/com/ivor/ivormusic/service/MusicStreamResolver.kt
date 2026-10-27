package com.ivor.ivormusic.service

import android.net.Uri
import androidx.media3.common.MediaItem
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedHashMap

/**
 * Turns a queue row into something ExoPlayer can play: a download, a stream
 * URL still known to be valid, the full disk cache, or a fresh resolution.
 *
 * Resolution is single-flight by track id: the validation of the current song,
 * the prefetch of the next ones and an error retry all join one request. It
 * never touches a player. Whoever applies a result checks that the queue
 * occurrence it was for is still there, because the queue can be replaced
 * while YouTube answers.
 *
 * A failure is not thrown: the row comes back with an `error://` URI, so the
 * player's error path takes over and the placeholder never loops back into
 * another resolution.
 */
internal class MusicStreamResolver(
    resolveScope: CoroutineScope,
    private val youtube: () -> YouTubeRepository,
    private val downloadedSongs: () -> List<Song>,
    private val cacheEnabled: () -> Boolean,
    private val cachedSong: (videoId: String) -> Song?,
    singleFlight: DeferredSingleFlight<String, MediaItem>? = null,
) {
    // Work is published before it starts so cache hits may complete
    // immediately without recursively mutating a ConcurrentHashMap computation.
    // Tests hand in their own, filled with the results they want.
    private val active = singleFlight ?: DeferredSingleFlight(resolveScope)

    // googlevideo URLs die after ~6h (their `expire` param) and on network/IP
    // changes, so each entry carries an expiry and is dropped instead of being
    // replayed as a guaranteed 403.
    private class CachedUri(val uri: String, val expiresAtMs: Long)
    private val uriCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedUri>(MAX_RESOLVED_URI_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedUri>): Boolean =
                size > MAX_RESOLVED_URI_ENTRIES
        }
    )

    /** The shared resolution for [mediaItem]'s track, started if none is running. */
    fun resolve(mediaItem: MediaItem): Deferred<MediaItem> =
        active.getOrStart(mediaItem.mediaId) { performResolution(mediaItem) }

    fun isResolving(videoId: String): Boolean = active.contains(videoId)

    /** Drop a running resolution, so the next [resolve] starts a fresh one. */
    fun forgetInFlight(videoId: String) {
        active.forget(videoId)
    }

    /** Stop trusting the stream URL known for [videoId]. */
    fun forgetUrl(videoId: String) {
        uriCache.remove(videoId)
    }

    /** Stop trusting every known stream URL: they were all signed with a refused token. */
    fun forgetAllUrls() {
        uriCache.clear()
    }

    fun clear() {
        active.clear()
        uriCache.clear()
    }

    /**
     * Resolution is single-flight by track id, while a queue can contain the
     * same track more than once. The shared result therefore carries the
     * occurrence metadata of whichever caller won the flight. Rebind only its
     * resolved source to the caller's item before inserting it into a queue so
     * duplicate rows never acquire the same occurrence id.
     */
    fun bind(original: MediaItem, resolved: MediaItem): MediaItem {
        val uri = resolved.localConfiguration?.uri ?: return original
        return withUri(original, uri, resolved.mediaMetadata.durationMs)
    }

    private suspend fun performResolution(originalItem: MediaItem): MediaItem {
        val videoId = originalItem.mediaId
        KLog.d(TAG, "Resolution: Starting for $videoId")

        // 1. Downloads
        val downloaded = downloadedSongs().find { it.id == videoId }
        if (downloaded != null && downloaded.uri != null) {
            KLog.d(TAG, "Resolution: Found download for $videoId")
            return withUri(originalItem, downloaded.uri, downloaded.duration)
        }

        // 2. Cache (Memory) — only while the underlying googlevideo URL is
        // still valid; expired entries are re-resolved instead of replayed.
        uriCache[videoId]?.let { cached ->
            if (cached.expiresAtMs > System.currentTimeMillis()) {
                KLog.d(TAG, "Resolution: Found cached URI for $videoId")
                return withUri(originalItem, Uri.parse(cached.uri))
            }
            KLog.d(TAG, "Resolution: Cached URI expired for $videoId, re-resolving")
            uriCache.remove(videoId)
        }

        // 3. Disk Cache (Fully Cached - Instant Playback). Skipped when the
        // cache setting is off: the data source then bypasses the cache, so a
        // CACHED_PREFIX URI would hit the network with a fake host and fail.
        if (cacheEnabled() && CacheManager.isFullyCached(videoId)) {
            KLog.d(TAG, "Resolution: Found full disk cache for $videoId. Enabling instant playback.")
            return withUri(originalItem, Uri.parse("$CACHED_PREFIX$videoId"))
        }

        // 4. Network with Retry
        // YouTubeRepository owns the visionOS-first client fallback. This layer
        // bounds the whole resolution and handles playback-time re-resolution.
        return try {
            val result = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                youtube().getStreamUrl(videoId)
            }

            val streamUrl = result?.getOrNull()
            if (!streamUrl.isNullOrEmpty()) {
                uriCache[videoId] = CachedUri(streamUrl, streamUrlExpiryMs(streamUrl))
                KLog.d(TAG, "Resolution: Network success for $videoId")
                withUri(originalItem, Uri.parse(streamUrl))
            } else if (result == null) {
                // Every client had its turn inside the whole budget. Told apart
                // from a plain failure so the error path skips instead of
                // spending the same budget again.
                KLog.e(TAG, "Resolution: Timed out for $videoId")
                withUri(originalItem, Uri.parse("error://$RESOLUTION_TIMEOUT_HOST/$videoId"))
            } else {
                KLog.e(TAG, "Resolution: Failed for $videoId")
                // Return an item with a special error URI instead of the placeholder
                // This breaks the loop because isPlaceholder() will be false.
                withUri(originalItem, Uri.parse("error://resolution_failed/$videoId"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Resolution: Exception for $videoId", e)
            withUri(originalItem, Uri.parse("error://exception/$videoId"))
        }
    }

    private fun withUri(original: MediaItem, uri: Uri, duration: Long? = null): MediaItem {
        val metaBuilder = original.mediaMetadata.buildUpon()
        duration?.takeIf { it > 0L }?.let(metaBuilder::setDurationMs)
        if (original.mediaMetadata.title == null) {
            val cachedInfo = cachedSong(original.mediaId)
            if (cachedInfo != null) {
                metaBuilder.setTitle(cachedInfo.title)
                    .setArtist(cachedInfo.artist)
                    .setArtworkUri(if (cachedInfo.thumbnailUrl != null) Uri.parse(cachedInfo.thumbnailUrl) else null)
            }
        }

        // Playback reads the cache under the song id, and so must the warm
        // that fills it ahead of time.
        return original.buildUpon()
            .setUri(uri)
            .setCustomCacheKey(original.mediaId)
            .setMediaMetadata(metaBuilder.build())
            .setTag(original.mediaId)
            .build()
    }

    /**
     * When the cached URI for a googlevideo URL stops being usable. Prefers the
     * URL's own `expire` query param (epoch seconds, ~6h out) minus a safety
     * margin; falls back to a conservative fixed TTL.
     */
    private fun streamUrlExpiryMs(url: String): Long {
        val expireSec = try {
            Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return if (expireSec != null && expireSec > 0) {
            expireSec * 1000L - URI_EXPIRY_SAFETY_MS
        } else {
            System.currentTimeMillis() + URI_DEFAULT_TTL_MS
        }
    }

    companion object {
        private const val TAG = "MusicService"

        /** A YouTube row not yet resolved; what the queue holds until a stream is known. */
        const val PLACEHOLDER_PREFIX = "https://placeholder.ivormusic/"

        /** A song the disk cache holds whole, played without the network. */
        const val CACHED_PREFIX = "https://cached.ivormusic/"

        /** The error URI host used when the whole budget ran out. */
        const val RESOLUTION_TIMEOUT_HOST = "resolution_timeout"

        private const val MAX_RESOLVED_URI_ENTRIES = 128

        // Covers the direct visionOS call and the NewPipe fallback behind it;
        // their individual requests are also bounded by OkHttp.
        //
        // This timeout does not itself interrupt anything - both paths block
        // inside OkHttp, so it can only discard a late result, and a discarded
        // result is a skipped song. The budgets underneath are what keep the
        // arithmetic inside it: each direct /player call is capped at 8s by
        // streamResolveClient, and YouTubeRepository gives NewPipe
        // NEWPIPE_STREAM_BUDGET_MS (8s), so the ordinary failing case is one
        // client plus one budget and lands inside 20s.
        // Lowering either one without the other reintroduces the case where a
        // working fallback exists and is never reached.
        const val RESOLVE_TIMEOUT_MS = 20_000L

        // Safety margin before a googlevideo URL's `expire` timestamp, and the
        // fallback lifetime when the URL carries no readable expire param.
        private const val URI_EXPIRY_SAFETY_MS = 5 * 60 * 1000L
        private const val URI_DEFAULT_TTL_MS = 4 * 60 * 60 * 1000L
    }
}
