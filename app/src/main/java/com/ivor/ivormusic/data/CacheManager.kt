package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.TreeSet

/**
 * Singleton manager for ExoPlayer's SimpleCache.
 * Handles persistent caching of audio streams for offline/instant playback.
 */
@UnstableApi
object CacheManager {

    private const val TAG = "CacheManager"
    private const val CACHE_DIR_NAME = "ivor_music_cache"

    // Default 512MB cache
    const val DEFAULT_CACHE_SIZE_MB = 512L
    const val MIN_CACHE_SIZE_MB = 128L
    const val MAX_CACHE_SIZE_MB = 4096L // 4GB

    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var evictor: SizeAdjustableLruEvictor? = null
    private var maxCacheSizeBytes: Long = DEFAULT_CACHE_SIZE_MB * 1024 * 1024

    private val _currentCacheSizeBytes = MutableStateFlow(0L)
    val currentCacheSizeBytes: StateFlow<Long> = _currentCacheSizeBytes.asStateFlow()

    private var cacheDir: File? = null

    /**
     * LRU evictor whose size limit can be changed while the cache is live.
     *
     * Media3's LeastRecentlyUsedCacheEvictor fixes its limit at construction,
     * which forced a full SimpleCache release+recreate to apply a new size —
     * invalidating every CacheDataSource.Factory already handed to a player.
     * This port keeps the same LRU behavior but lets [updateMaxBytes] shrink
     * or grow the limit in place and trim immediately.
     *
     * Locking: Media3 invokes the callbacks while holding the SimpleCache lock,
     * so the ordering is always cache lock -> [lock]. [evictWhileOverLimit]
     * never holds [lock] across a removeSpan call, so the external trim path
     * ([updateMaxBytes]) cannot deadlock against a concurrent cache write.
     */
    private class SizeAdjustableLruEvictor(initialMaxBytes: Long) : CacheEvictor {

        @Volatile private var maxBytes: Long = initialMaxBytes
        private val lock = Any()
        private val leastRecentlyUsed = TreeSet<CacheSpan>(::compareSpans)
        private var currentSize = 0L

        override fun requiresCacheSpanTouches(): Boolean = true

        override fun onCacheInitialized() {
            // Existing spans are reported through onSpanAdded during load.
        }

        override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
            if (length != C.LENGTH_UNSET.toLong()) {
                evictWhileOverLimit(cache, length)
            }
        }

        override fun onSpanAdded(cache: Cache, span: CacheSpan) {
            synchronized(lock) {
                leastRecentlyUsed.add(span)
                currentSize += span.length
                _currentCacheSizeBytes.value = currentSize
            }
            evictWhileOverLimit(cache, 0)
        }

        override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
            synchronized(lock) {
                if (leastRecentlyUsed.remove(span)) {
                    currentSize -= span.length
                    _currentCacheSizeBytes.value = currentSize
                }
            }
        }

        override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
            onSpanRemoved(cache, oldSpan)
            onSpanAdded(cache, newSpan)
        }

        /** Apply a new size limit and trim down to it immediately. */
        fun updateMaxBytes(cache: Cache, newMaxBytes: Long) {
            maxBytes = newMaxBytes
            evictWhileOverLimit(cache, 0)
        }

        private fun evictWhileOverLimit(cache: Cache, requiredSpace: Long) {
            while (true) {
                val toEvict = synchronized(lock) {
                    if (currentSize + requiredSpace <= maxBytes) null
                    else leastRecentlyUsed.firstOrNull()
                } ?: return
                cache.removeSpan(toEvict)
                // onSpanRemoved normally drops the span from our bookkeeping;
                // this fallback guarantees loop progress even if it didn't fire.
                synchronized(lock) {
                    if (leastRecentlyUsed.remove(toEvict)) {
                        currentSize -= toEvict.length
                        _currentCacheSizeBytes.value = currentSize
                    }
                }
            }
        }

        private companion object {
            fun compareSpans(lhs: CacheSpan, rhs: CacheSpan): Int {
                val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
                return when {
                    delta == 0L -> lhs.compareTo(rhs)
                    delta < 0L -> -1
                    else -> 1
                }
            }
        }
    }

    /**
     * Initialize the cache. Call this once from Application or Service.
     */
    @Synchronized
    fun initialize(context: Context, maxSizeMb: Long = DEFAULT_CACHE_SIZE_MB) {
        if (simpleCache != null) {
            KLog.d(TAG, "Cache already initialized")
            setMaxCacheSize(context, maxSizeMb)
            return
        }

        maxCacheSizeBytes = maxSizeMb * 1024 * 1024
        cacheDir = File(context.cacheDir, CACHE_DIR_NAME)

        databaseProvider = StandaloneDatabaseProvider(context)

        try {
            evictor = SizeAdjustableLruEvictor(maxCacheSizeBytes)
            simpleCache = SimpleCache(
                cacheDir!!,
                evictor!!,
                databaseProvider!!
            )
            KLog.d(TAG, "Cache initialized with max size: ${maxSizeMb}MB")
        } catch (e: Exception) {
            KLog.e(TAG, "Cache initialization failed, attempting recovery by clearing cache", e)
            // Cache is corrupted - delete and retry
            try {
                cacheDir?.deleteRecursively()
                cacheDir?.mkdirs()
                databaseProvider = StandaloneDatabaseProvider(context)
                evictor = SizeAdjustableLruEvictor(maxCacheSizeBytes)
                simpleCache = SimpleCache(
                    cacheDir!!,
                    evictor!!,
                    databaseProvider!!
                )
                KLog.d(TAG, "Cache recovery successful")
            } catch (e2: Exception) {
                KLog.e(TAG, "Cache recovery failed - caching disabled", e2)
                simpleCache = null
                evictor = null
            }
        }

        updateCacheSize()
    }

    /**
     * Get the SimpleCache instance. Returns null if not initialized.
     */
    fun getCache(): SimpleCache? = simpleCache

    /**
     * Create a CacheDataSource.Factory for use with ExoPlayer.
     * If cache is unavailable or corrupted, returns null to fallback to non-cached playback.
     *
     * @param upstreamFactory Optional upstream data source factory. If provided, this is used
     *   for cache misses. Use DefaultDataSource.Factory to support all URI schemes (file://,
     *   content://, http(s)://). If null, defaults to HTTP-only factory.
     */
    fun createCacheDataSourceFactory(upstreamFactory: DataSource.Factory? = null): CacheDataSource.Factory? {
        val cache = simpleCache ?: return null

        try {
            // Per-URL UA: googlevideo URLs are tagged with their issuing client
            // (?c=IOS, ?c=TVHTML5_SIMPLY_EMBEDDED, etc.) and YouTube 403s the
            // playback if our UA doesn't match. createPerClientHttpFactory()
            // inspects each request's URI and sets the right UA dynamically.
            val upstream = upstreamFactory ?: createPerClientHttpFactory()

            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                )
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to create cache data source factory", e)
            return null
        }
    }

    /**
     * The HTTP factory for all stream fetches: ChunkedStreamDataSource picks
     * the per-request User-Agent from the URI's `?c=` client tag (a UA
     * mismatch means a googlevideo 403) and downloads googlevideo media in
     * bounded ranged chunks, sidestepping the server-side pacing that
     * throttles open-ended progressive requests to roughly the media bitrate.
     *
     * Used as the upstream for the playback cache, and from MusicService for
     * non-cache HTTP fallback.
     */
    fun createPerClientHttpFactory(): DataSource.Factory = ChunkedStreamDataSource.Factory()

    /**
     * Update the current cache size state.
     */
    fun updateCacheSize() {
        val cache = simpleCache
        if (cache != null) {
            _currentCacheSizeBytes.value = cache.cacheSpace
        }
    }

    /**
     * Get current cache size in bytes.
     */
    fun getCacheSizeBytes(): Long {
        return simpleCache?.cacheSpace ?: 0L
    }

    /**
     * Clear all cached content.
     */
    @Synchronized
    fun clearCache() {
        try {
            simpleCache?.let { cache ->
                // Get all cached keys and remove them
                val keys = cache.keys.toList()
                keys.forEach { key ->
                    cache.removeResource(key)
                }
                KLog.d(TAG, "Cache cleared: removed ${keys.size} items")
            }
            updateCacheSize()
        } catch (e: Exception) {
            KLog.e(TAG, "Error clearing cache", e)
        }
    }

    /**
     * Release the cache. Call when the app is being destroyed.
     */
    @Synchronized
    fun release() {
        try {
            simpleCache?.release()
            simpleCache = null
            databaseProvider = null
            evictor = null
            KLog.d(TAG, "Cache released")
        } catch (e: Exception) {
            KLog.e(TAG, "Error releasing cache", e)
        }
    }

    /**
     * Update the maximum cache size, applied live. The SimpleCache instance is
     * kept — only the evictor's limit changes and excess spans are trimmed —
     * so CacheDataSource factories already handed to a player stay valid and
     * playback is never interrupted by a size change.
     */
    @Synchronized
    fun setMaxCacheSize(context: Context, maxSizeMb: Long) {
        val newSizeBytes = maxSizeMb * 1024 * 1024
        if (maxCacheSizeBytes == newSizeBytes) return
        maxCacheSizeBytes = newSizeBytes

        val cache = simpleCache
        val currentEvictor = evictor
        if (cache == null || currentEvictor == null) {
            // Not initialized yet — the size takes effect at initialize().
            return
        }

        KLog.d(TAG, "Updating cache size to ${maxSizeMb}MB (live)")
        currentEvictor.updateMaxBytes(cache, newSizeBytes)
        updateCacheSize()
    }

    /**
     * Format bytes to human-readable string.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Check if a specific content key is cached (partially or fully).
     */
    fun isCached(contentKey: String): Boolean {
        val cache = simpleCache ?: return false
        return cache.getCachedBytes(contentKey, 0, Long.MAX_VALUE) > 0
    }

    /**
     * Check if a specific content key is fully cached.
     */
    fun isFullyCached(contentKey: String): Boolean {
        val cache = simpleCache ?: return false
        val length = cache.getContentMetadata(contentKey).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
        if (length <= 0) return false
        return cache.getCachedBytes(contentKey, 0, length) >= length
    }

    /**
     * Get the total length of the cached content for a key.
     */
    fun getCachedLength(contentKey: String): Long {
        val cache = simpleCache ?: return -1L
        return cache.getContentMetadata(contentKey).get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
    }

    /**
     * Every content key held in full, and how many bytes each occupies.
     *
     * The keys are song ids: playback reads and writes under
     * `MediaItem`'s custom cache key (see `MusicService.buildMediaItemWithUri`),
     * which is what makes this list mean anything to the rest of the app rather
     * than being a set of opaque URLs.
     *
     * **Fully cached only, and that is the whole point.**
     * `MusicService.warmStreamCache` writes the first 512 KB of the next three
     * queue songs, so a partial entry means "this will start quickly", not
     * "this plays offline". Listing those as available would promise playback
     * that stops a few seconds in the moment the network goes.
     *
     * Walks the whole key set, so callers should treat it as a snapshot taken
     * off the main thread rather than something to poll.
     */
    fun fullyCachedEntries(): Map<String, Long> {
        val cache = simpleCache ?: return emptyMap()
        return runCatching {
            cache.keys.mapNotNull { key ->
                val length = cache.getContentMetadata(key)
                    .get(androidx.media3.datasource.cache.ContentMetadata.KEY_CONTENT_LENGTH, -1L)
                if (length > 0 && cache.getCachedBytes(key, 0, length) >= length) {
                    key to length
                } else {
                    null
                }
            }.toMap()
        }.getOrElse {
            KLog.w(TAG, "Failed to enumerate cached keys", it)
            emptyMap()
        }
    }
}
