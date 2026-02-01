package com.ivor.ivormusic.data

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.DefaultHttpDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

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
    private var maxCacheSizeBytes: Long = DEFAULT_CACHE_SIZE_MB * 1024 * 1024
    
    private val _currentCacheSizeBytes = MutableStateFlow(0L)
    val currentCacheSizeBytes: StateFlow<Long> = _currentCacheSizeBytes.asStateFlow()
    
    private var cacheDir: File? = null
    
    /**
     * Initialize the cache. Call this once from Application or Service.
     */
    @Synchronized
    fun initialize(context: Context, maxSizeMb: Long = DEFAULT_CACHE_SIZE_MB) {
        if (simpleCache != null) {
            Log.d(TAG, "Cache already initialized")
            updateCacheSize()
            return
        }
        
        maxCacheSizeBytes = maxSizeMb * 1024 * 1024
        cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        
        databaseProvider = StandaloneDatabaseProvider(context)
        
        val internalEvictor = LeastRecentlyUsedCacheEvictor(maxCacheSizeBytes)
        val evictor = object : androidx.media3.datasource.cache.CacheEvictor {
            override fun requiresCacheSpanTouches() = internalEvictor.requiresCacheSpanTouches()
            override fun onCacheInitialized() = internalEvictor.onCacheInitialized()
            override fun onStartFile(cache: androidx.media3.datasource.cache.Cache, key: String, position: Long, length: Long) = internalEvictor.onStartFile(cache, key, position, length)
            override fun onSpanAdded(cache: androidx.media3.datasource.cache.Cache, span: androidx.media3.datasource.cache.CacheSpan) {
                internalEvictor.onSpanAdded(cache, span)
                updateCacheSize()
            }
            override fun onSpanRemoved(cache: androidx.media3.datasource.cache.Cache, span: androidx.media3.datasource.cache.CacheSpan) {
                internalEvictor.onSpanRemoved(cache, span)
                updateCacheSize()
            }
            override fun onSpanTouched(cache: androidx.media3.datasource.cache.Cache, oldSpan: androidx.media3.datasource.cache.CacheSpan, newSpan: androidx.media3.datasource.cache.CacheSpan) {
                internalEvictor.onSpanTouched(cache, oldSpan, newSpan)
            }
        }
        
        try {
            simpleCache = SimpleCache(
                cacheDir!!,
                evictor,
                databaseProvider!!
            )
            Log.d(TAG, "Cache initialized with max size: ${maxSizeMb}MB")
        } catch (e: Exception) {
            Log.e(TAG, "Cache initialization failed, attempting recovery by clearing cache", e)
            // Cache is corrupted - delete and retry
            try {
                cacheDir?.deleteRecursively()
                cacheDir?.mkdirs()
                databaseProvider = StandaloneDatabaseProvider(context)
                simpleCache = SimpleCache(
                    cacheDir!!,
                    LeastRecentlyUsedCacheEvictor(maxCacheSizeBytes),
                    databaseProvider!!
                )
                Log.d(TAG, "Cache recovery successful")
            } catch (e2: Exception) {
                Log.e(TAG, "Cache recovery failed - caching disabled", e2)
                simpleCache = null
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
     */
    fun createCacheDataSourceFactory(): CacheDataSource.Factory? {
        val cache = simpleCache ?: return null
        
        try {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
            
            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)
                .setFlags(
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
                )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create cache data source factory", e)
            return null
        }
    }
    
    /**
     * Update the current cache size state.
     */
    fun updateCacheSize() {
        val cache = simpleCache
        if (cache != null) {
            _currentCacheSizeBytes.value = cache.cacheSpace
            Log.d(TAG, "Current cache size: ${formatSize(cache.cacheSpace)}")
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
                Log.d(TAG, "Cache cleared: removed ${keys.size} items")
            }
            updateCacheSize()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
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
            Log.d(TAG, "Cache released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing cache", e)
        }
    }
    
    /**
     * Update the maximum cache size. Requires reinitializing the cache.
     */
    @Synchronized
    fun setMaxCacheSize(context: Context, maxSizeMb: Long) {
        if (maxCacheSizeBytes == maxSizeMb * 1024 * 1024) return
        
        Log.d(TAG, "Updating cache size to ${maxSizeMb}MB")
        
        // Release existing cache
        simpleCache?.release()
        simpleCache = null
        
        // Reinitialize with new size
        initialize(context, maxSizeMb)
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
}
