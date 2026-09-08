package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.TreeSet

/**
 * Process-owned playback caches.
 *
 * Music uses the user's size-bounded LRU because complete songs become the
 * Ready offline library. Video uses a separate transient cache with no
 * configured byte ceiling: a high-bitrate 4K stream must not evict music or
 * stop caching halfway through merely because the music limit is 2 GB. The
 * video cache is cleared five minutes after every video surface closes.
 */
@UnstableApi
object CacheManager {

    private const val TAG = "CacheManager"
    private const val CACHE_DIR_NAME = "ivor_music_cache"
    private const val VIDEO_CACHE_DIR_NAME = "ivor_video_playback_cache"
    private const val VIDEO_CACHE_IDLE_CLEAR_MS = 5 * 60 * 1000L

    // Default 512MB cache
    const val DEFAULT_CACHE_SIZE_MB = 512L
    const val MIN_CACHE_SIZE_MB = 256L

    /**
     * The ceiling the settings slider offers. 10 GB is far more than a music
     * cache normally needs, which is exactly why the slider warns rather than
     * refuses past a few gigabytes: someone with a large card and a long
     * commute is entitled to keep their whole rotation offline-ready, and
     * someone on a 32 GB phone should be told what they are about to spend.
     */
    const val MAX_CACHE_SIZE_MB = 10240L // 10GB

    /** Past this the settings page states the cost out loud. */
    const val LARGE_CACHE_WARNING_MB = 4096L // 4GB

    private var simpleCache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var evictor: SizeAdjustableLruEvictor? = null
    private var maxCacheSizeBytes: Long = DEFAULT_CACHE_SIZE_MB * 1024 * 1024

    private val _currentCacheSizeBytes = MutableStateFlow(0L)
    val currentCacheSizeBytes: StateFlow<Long> = _currentCacheSizeBytes.asStateFlow()

    private var cacheDir: File? = null

    private var videoCache: SimpleCache? = null
    private var videoDatabaseProvider: StandaloneDatabaseProvider? = null
    private var videoCacheDir: File? = null
    private val activeVideoOwners = mutableSetOf<String>()
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var videoCleanupJob: Job? = null

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
     * Mark one player surface as using the transient video cache.
     *
     * Owners are names rather than a count so repeated play/open calls from a
     * single ViewModel cannot leak an acquisition. A new video cancels a
     * pending idle clear; closing the last surface starts the five-minute grace
     * period requested for quick reopen and swipe-back.
     */
    @Synchronized
    fun setVideoPlaybackActive(owner: String, active: Boolean) {
        if (active) {
            activeVideoOwners.add(owner)
            videoCleanupJob?.cancel()
            videoCleanupJob = null
        } else {
            activeVideoOwners.remove(owner)
            if (activeVideoOwners.isEmpty()) scheduleVideoCacheClear()
        }
    }

    @Synchronized
    private fun scheduleVideoCacheClear() {
        videoCleanupJob?.cancel()
        videoCleanupJob = cacheScope.launch {
            delay(VIDEO_CACHE_IDLE_CLEAR_MS)
            val shouldClear = synchronized(this@CacheManager) {
                activeVideoOwners.isEmpty()
            }
            if (shouldClear) clearVideoCache()
        }
    }

    /** Clear transient video bytes only. Music and permanent downloads stay. */
    @Synchronized
    fun clearVideoCache(shorts: Boolean? = null) {
        try {
            val cache = videoCache ?: return
            val keys = cache.keys.filter { shorts == null || isShortsCacheKey(it) == shorts }
            keys.forEach(cache::removeResource)
            KLog.d(TAG, "Transient video cache cleared: removed ${keys.size} items")
        } catch (e: Exception) {
            KLog.e(TAG, "Error clearing transient video cache", e)
        }
    }

    /**
     * One-time-compatible cleanup for builds that stored video in the music
     * LRU. Safe to call on every start; after migration the key scan is empty.
     */
    fun removeLegacyVideoEntries() {
        cacheScope.launch {
            try {
                val cache = simpleCache ?: return@launch
                val keys = cache.keys.filter(::isNonMusicPlaybackCacheKey)
                keys.forEach(cache::removeResource)
                if (keys.isNotEmpty()) {
                    updateCacheSize()
                    KLog.i(TAG, "Removed ${keys.size} legacy video entries from the music cache")
                }
            } catch (e: Exception) {
                KLog.w(TAG, "Could not remove legacy video cache entries", e)
            }
        }
    }

    /**
     * Create a CacheDataSource.Factory for use with ExoPlayer.
     * If cache is unavailable or corrupted, returns null to fallback to non-cached playback.
     *
     * @param upstreamFactory Optional upstream data source factory. If provided, this is used
     *   for cache misses. Use DefaultDataSource.Factory to support all URI schemes (file://,
     *   content://, http(s)://). If null, defaults to HTTP-only factory.
     */
    fun createCacheDataSourceFactory(
        context: Context,
        upstreamFactory: DataSource.Factory? = null,
        writeEnabled: Boolean = true,
    ): CacheDataSource.Factory? {
        val cache = simpleCache ?: return null

        try {
            // Per-URL UA: googlevideo URLs are tagged with their issuing client
            // (?c=IOS, ?c=TVHTML5_SIMPLY_EMBEDDED, etc.) and YouTube 403s the
            // playback if our UA doesn't match. createPerClientHttpFactory()
            // inspects each request's URI and sets the right UA dynamically.
            val upstream = upstreamFactory ?: createPerClientHttpFactory(context)

            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                // Progressive MediaItems supply a stable song/video key.
                // Adaptive manifests and their segments do not; namespace
                // their URI keys so Ready offline never mistakes one for a
                // song id while they still remain reusable in this session.
                .setCacheKeyFactory { dataSpec ->
                    dataSpec.key ?: opaquePlaybackCacheKey(dataSpec.uri.toString())
                }
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                )
                .apply { if (!writeEnabled) setCacheWriteDataSinkFactory(null) }
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to create cache data source factory", e)
            return null
        }
    }

    /**
     * Data source used by a player that can open both device and network URIs.
     *
     * Network streams pass through [CacheDataSource] while the user's cache
     * setting is enabled. Local `content://` and `file://` media always use
     * [DefaultDataSource], because handing either to the HTTP-only cache
     * upstream fails on devices whose downloads are exposed through MediaStore.
     * The setting is read for every open so changing it applies to the next
     * load without rebuilding ExoPlayer.
     */
    fun createPlaybackDataSourceFactory(
        context: Context,
        isCacheEnabled: () -> Boolean,
    ): DataSource.Factory {
        val directFactory = DefaultDataSource.Factory(context, createPerClientHttpFactory(context))
        val cacheFactory = createCacheDataSourceFactory(context)
        val readOnlyFactory = createCacheDataSourceFactory(context, writeEnabled = false)
        return DataSource.Factory {
            SwitchingPlaybackDataSource(
                direct = directFactory.createDataSource(),
                cached = cacheFactory?.createDataSource(),
                isCacheEnabled = isCacheEnabled,
                readOnlyCache = readOnlyFactory?.createDataSource(),
            )
        }
    }

    /**
     * Player factory for online video and Shorts.
     *
     * Video and Shorts have separate write policies and key namespaces while
     * sharing the temporary store. The music size limit does not apply here.
     * Cache failures (including a full disk) fall through to upstream so the
     * act of caching can never stop an otherwise playable video.
     */
    @Synchronized
    fun createVideoPlaybackDataSourceFactory(context: Context, shorts: Boolean = false): DataSource.Factory {
        initializeVideoCache(context.applicationContext)
        val directFactory = DefaultDataSource.Factory(
            context,
            createPerClientHttpFactory(context),
        )
        val cacheFactory = createVideoCacheDataSourceFactory(context, shorts)
        val readOnlyFactory = createVideoCacheDataSourceFactory(context, shorts, writeEnabled = false)
        return DataSource.Factory {
            SwitchingPlaybackDataSource(
                direct = directFactory.createDataSource(),
                cached = cacheFactory?.createDataSource(),
                isCacheEnabled = {
                    if (shorts) ThemePreferences.isShortsCacheEnabled(context)
                    else ThemePreferences.isVideoCacheEnabled(context)
                },
                readOnlyCache = readOnlyFactory?.createDataSource(),
            )
        }
    }

    /** Blocking range warm for the immediate-next Shorts item. Call on IO. */
    fun cacheVideoRange(context: Context, dataSpec: DataSpec) {
        if (!ThemePreferences.isShortsCacheEnabled(context) ||
            !ThemePreferences.isPlaybackPreloadEnabled(context)) return
        val factory = synchronized(this) {
            initializeVideoCache(context.applicationContext)
            createVideoCacheDataSourceFactory(context, shorts = true)
        } ?: return
        CacheWriter(factory.createDataSource(), dataSpec, null, null).cache()
    }

    private fun createVideoCacheDataSourceFactory(
        context: Context,
        shorts: Boolean,
        writeEnabled: Boolean = true,
    ): CacheDataSource.Factory? =
        videoCache?.let { cache ->
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(createPerClientHttpFactory(context))
                .setCacheKeyFactory { dataSpec ->
                    val key = dataSpec.key ?: opaquePlaybackCacheKey(dataSpec.uri.toString())
                    playbackCacheCategoryKey(key, shorts)
                }
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .apply { if (!writeEnabled) setCacheWriteDataSinkFactory(null) }
        }

    @Synchronized
    private fun initializeVideoCache(context: Context) {
        if (videoCache != null) return
        videoCacheDir = File(context.cacheDir, VIDEO_CACHE_DIR_NAME)
        videoDatabaseProvider = StandaloneDatabaseProvider(context)
        try {
            videoCache = SimpleCache(
                checkNotNull(videoCacheDir),
                NoOpCacheEvictor(),
                checkNotNull(videoDatabaseProvider),
            )
        } catch (e: Exception) {
            KLog.e(TAG, "Video cache initialization failed, attempting recovery", e)
            runCatching { videoCacheDir?.deleteRecursively() }
            videoDatabaseProvider = StandaloneDatabaseProvider(context)
            videoCache = runCatching {
                SimpleCache(
                    checkNotNull(videoCacheDir),
                    NoOpCacheEvictor(),
                    checkNotNull(videoDatabaseProvider),
                )
            }.onFailure {
                KLog.e(TAG, "Video cache recovery failed; playback will stay direct", it)
            }.getOrNull()
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
    fun createPerClientHttpFactory(context: Context): DataSource.Factory =
        ChunkedStreamDataSource.Factory {
            !ThemePreferences.isLocalOnly(context)
        }

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

    /** Release process-owned caches. Tests/process teardown only. */
    @Synchronized
    fun release() {
        try {
            simpleCache?.release()
            simpleCache = null
            videoCleanupJob?.cancel()
            videoCleanupJob = null
            videoCache?.release()
            videoCache = null
            databaseProvider = null
            videoDatabaseProvider = null
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
     * Free space on the volume the music cache lives on, or -1 when the
     * platform will not answer.
     *
     * The settings slider reads this because a ceiling is a promise about disk
     * the device may not have: offering 10 GB on a phone with 3 GB left is an
     * invitation to fill it. It is a snapshot rather than a reservation - the
     * evictor still only trims what the cache itself holds - so the page states
     * it as context beside the choice rather than clamping the choice to it.
     */
    fun availableSpaceBytes(context: Context): Long = try {
        val dir = cacheDir ?: File(context.cacheDir, CACHE_DIR_NAME)
        val target = if (dir.exists()) dir else context.cacheDir
        android.os.StatFs(target.absolutePath).availableBytes
    } catch (e: Exception) {
        KLog.e(TAG, "Could not read free space", e)
        -1L
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
     * The returned keys are song ids: music playback reads and writes under
     * `MediaItem`'s custom cache key (see `MusicService.buildMediaItemWithUri`),
     * which is what makes this list mean anything to the rest of the app rather
     * than being a set of opaque URLs. Video entries share the physical LRU
     * cache but carry a namespaced key and are intentionally excluded here.
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
                if (isNonMusicPlaybackCacheKey(key)) return@mapNotNull null
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

    private class SwitchingPlaybackDataSource(
        private val direct: DataSource,
        private val cached: DataSource?,
        private val isCacheEnabled: () -> Boolean,
        private val readOnlyCache: DataSource? = null,
    ) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            direct.addTransferListener(transferListener)
            cached?.addTransferListener(transferListener)
            readOnlyCache?.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme
            val isNetwork = scheme == "http" || scheme == "https"
            active = if (isNetwork) {
                if (isCacheEnabled()) cached ?: direct else readOnlyCache ?: direct
            } else direct
            return checkNotNull(active).open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

        override fun getUri(): android.net.Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.close()
            active = null
        }
    }
}
