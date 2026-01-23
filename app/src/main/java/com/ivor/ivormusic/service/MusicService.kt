package com.ivor.ivormusic.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.SongSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ConcurrentHashMap

@UnstableApi

class MusicService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var themePreferences: com.ivor.ivormusic.data.ThemePreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Cache for resolved stream URLs (videoId -> streamUrl)
    private val urlCache = ConcurrentHashMap<String, String>()
    
    // Track which items are being resolved to avoid duplicate requests
    private val resolvingItems = ConcurrentHashMap<String, Boolean>()
    
    // Crossfade variables
    private var isCrossfadeEnabled = true
    private var crossfadeDurationMs = 3000L
    private var fadeVolumeJob: kotlinx.coroutines.Job? = null
    
    companion object {
        private const val TAG = "MusicService"
        private const val PREFETCH_AHEAD = 3
        private const val STREAM_TIMEOUT_MS = 10000L
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Cache Manager
        com.ivor.ivormusic.data.CacheManager.initialize(this)
        
        // Set custom media notification provider for Android 16 Live Activities support
        setMediaNotificationProvider(LiveUpdateMediaNotificationProvider(this))
        
        themePreferences = com.ivor.ivormusic.data.ThemePreferences(this)
        observePreferences()
        
        initializeSessionAndPlayer()
    }
    
    private fun observePreferences() {
        serviceScope.launch {
            themePreferences.crossfadeEnabled.collect { 
                isCrossfadeEnabled = it
            }
        }
        serviceScope.launch {
            themePreferences.crossfadeDurationMs.collect { 
                crossfadeDurationMs = it.toLong()
            }
        }
        serviceScope.launch {
            themePreferences.maxCacheSizeMb.collect { sizeMb ->
                com.ivor.ivormusic.data.CacheManager.setMaxCacheSize(this@MusicService, sizeMb)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        fadeVolumeJob?.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        com.ivor.ivormusic.data.CacheManager.release()
        urlCache.clear()
        resolvingItems.clear()
        super.onDestroy()
    }

    private fun initializeSessionAndPlayer() {
        // Use CacheDataSourceFactory for persistent caching
        val cacheDataSourceFactory = com.ivor.ivormusic.data.CacheManager.createCacheDataSourceFactory()
            ?: androidx.media3.datasource.DefaultDataSource.Factory(this)
            
        // Configure LoadControl for better buffering
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min buffer 30s
                120_000, // Max buffer 2 mins
                2500, // Buffer for playback 2.5s
                5000 // Buffer for rebuffer 5s
            )
            .build()
            
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        youtubeRepository = YouTubeRepository(this)

        // Add listener for lazy pre-fetching of upcoming songs and Crossfade
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                
                // Trigger Crossfade Effect (Fade In)
                // Trigger Fade In if strictly auto transition (and crossfade enabled)
                // Note: The fade-out loop handles the end of the previous track.
                if (isCrossfadeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                     performFadeIn()
                } else {
                     player.volume = 1.0f // Reset volume if manual skip
                }
                
                // Pre-fetch next songs when track changes
                prefetchUpcomingSongs()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_READY) {
                    prefetchUpcomingSongs()
                }
            }
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "Player Error: ${error.errorCodeName}", error)
                
                // RECOVERY LOGIC
                val currentItem = player.currentMediaItem
                val videoId = currentItem?.mediaId
                
                if (videoId != null && !urlCache.containsKey("RETRY_$videoId")) {
                    Log.d(TAG, "Attempting strict recovery for $videoId")
                    
                    // Mark as retrying to prevent infinite loops
                    urlCache["RETRY_$videoId"] = "true"
                    
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            // Force fresh resolution (bypass functional cache)
                            urlCache.remove(videoId)
                            val newItem = resolveStreamUrl(currentItem, videoId)
                            
                            serviceScope.launch(Dispatchers.Main) {
                                val index = player.currentMediaItemIndex
                                player.replaceMediaItem(index, newItem)
                                player.prepare()
                                player.play()
                                Log.d(TAG, "Recovery successful for $videoId")
                            }
                        } catch (e: Exception) {
                             Log.e(TAG, "Recovery failed for $videoId", e)
                             // Skip to next if recovery fails
                             serviceScope.launch(Dispatchers.Main) {
                                 if (player.hasNextMediaItem()) {
                                     player.seekToNext()
                                     player.play()
                                 }
                             }
                        }
                    }
                } else {
                    // If already retried or unknown error, try to skip
                     if (player.hasNextMediaItem()) {
                         player.seekToNext()
                         player.play()
                     }
                }
            }
        })

        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName).let {
            val intent = it ?: Intent(this, MainActivity::class.java)
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                return serviceScope.future {
                    if (mediaItems.size == 1) {
                         val item = mediaItems[0]
                         val videoId = item.mediaId
                         // Determine if needs resolution (placeholder or cache key exists)
                         val isCached = com.ivor.ivormusic.data.CacheManager.isCached(videoId)
                         if (isCached) {
                              Log.d(TAG, "Item $videoId is fully cached, playing instantly")
                               // If cached, we might still need a URL if the CacheDataSource requires it,
                               // but usually it uses the key.
                               // However, ExoPlayer CacheDataSource uses the upstream URL as the key by default.
                               // We need to ensure we provide the SAME URL as was used to cache it.
                               // Since URLs expire, this is tricky. 
                               // Strategy: We used the videoId as Key? No, by default it uses the URI.
                               // BUT, we can set a custom cache key.
                               // Let's rely on resolveStreamUrl to get a fresh URL, but CacheDataSource might ignore it if we don't match keys.
                               // FIX: We need to use content keys.
                               
                               // For now, let's just resolve. The simple cache might handle it if we configured it to key by ID, 
                               // but we didn't. So we rely on fresh URLs. 
                               // Actually, since URLs expire, the cache hit ratio is low unless we use a custom key.
                               // In CacheManager we set up SimpleCache, but not keying.
                               
                               // To make "Perfect" offline cache for YouTube:
                               // typically you need to set a custom cache key (the video ID) on the MediaItem. 
                         }
                         
                         val resolved = resolveStreamUrl(item, videoId)
                         // Add custom cache key to the media item
                         val finalItem = resolved.buildUpon()
                            .setCustomCacheKey(videoId)
                            .build()
                            
                         mutableListOf(finalItem)
                    } else {
                        // Batch add, prefetch later
                        mediaItems.map { 
                            it.buildUpon().setCustomCacheKey(it.mediaId).build()
                        }.toMutableList()
                    }
                }
            }
        })
            .setSessionActivity(sessionIntent)
            .build()
            
        // Start monitoring for Fade-Out
        monitorCrossfadeProgress()
    }
    
    private fun monitorCrossfadeProgress() {
        serviceScope.launch {
            while (isActive) {
                if (isCrossfadeEnabled && player.isPlaying) {
                    val duration = player.duration
                    val position = player.currentPosition
                    
                    if (duration > 0 && position > 0) {
                        val remaining = duration - position
                        if (remaining <= crossfadeDurationMs) {
                            // Fade Out
                            val volume = (remaining.toFloat() / crossfadeDurationMs).coerceIn(0f, 1f)
                            player.volume = volume
                        } else if (player.volume < 1f && fadeVolumeJob?.isActive != true) {
                            // Restore volume if not in fade out window and not currently fading in
                            player.volume = 1f
                        }
                    }
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }
    
    private fun performFadeIn() {
        fadeVolumeJob?.cancel()
        fadeVolumeJob = serviceScope.launch {
            // Fade In
            player.volume = 0f
            val steps = 20
            val stepTime = crossfadeDurationMs / steps
            for (i in 1..steps) {
                player.volume = i / steps.toFloat()
                kotlinx.coroutines.delay(stepTime)
            }
            player.volume = 1f
        }
    }
    
    /**
     * Resolve stream URL for a media item
     */
    private suspend fun resolveStreamUrl(item: MediaItem, videoId: String): MediaItem {
        // 0. Check Persistent Disk Cache
        if (com.ivor.ivormusic.data.CacheManager.isCached(videoId)) {
            Log.d(TAG, "Item $videoId is fully cached, skipping network resolution")
            // Return a MediaItem that points to the cache key.
            // We use a dummy URI because CacheDataSource will use the key to find the file.
            // Even if the original URL expired, the cache content is valid under this key.
            return item.buildUpon()
                .setUri(android.net.Uri.parse("https://cached.ivormusic/$videoId"))
                .setCustomCacheKey(videoId)
                .build()
        }

        // 1. Check runtime memory cache first
        urlCache[videoId]?.let { cachedUrl ->
            return item.buildUpon()
                .setUri(android.net.Uri.parse(cachedUrl))
                .setCustomCacheKey(videoId) 
                .build()
        }

        // 2. Concurrency handling
        if (resolvingItems.putIfAbsent(videoId, true) == true) {
            var attempts = 0
            while (attempts < 50) { 
                kotlinx.coroutines.delay(100)
                // Check memory cache again after waiting
                urlCache[videoId]?.let { cachedUrl ->
                    return item.buildUpon()
                        .setUri(android.net.Uri.parse(cachedUrl))
                        .setCustomCacheKey(videoId)
                        .build()
                }
                // Check disk cache again (in case another thread finished downloading it - unlikely but safe)
                if (com.ivor.ivormusic.data.CacheManager.isCached(videoId)) {
                     return item.buildUpon()
                        .setUri(android.net.Uri.parse("https://cached.ivormusic/$videoId"))
                        .setCustomCacheKey(videoId)
                        .build()
                }
                attempts++
            }
            return item
        }

        // 3. Perform Resolution
        try {
            val streamUrl = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                // Determine network type/quality preference here if needed
                youtubeRepository.getStreamUrl(videoId)
            }
            if (streamUrl != null) {
                urlCache[videoId] = streamUrl
                return item.buildUpon()
                    .setUri(android.net.Uri.parse(streamUrl))
                    .setCustomCacheKey(videoId) // CRITICAL: Use Video ID as persistent cache key
                    .build()
            } else {
                return item
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL for $videoId", e)
            return item
        } finally {
            resolvingItems.remove(videoId)
        }
    }
    
    /**
     * Pre-fetch stream URLs for upcoming songs in the queue
     */
    private fun prefetchUpcomingSongs() {
        val currentIndex = player.currentMediaItemIndex
        val mediaCount = player.mediaItemCount
        
        if (mediaCount == 0) return
        
        val itemsToPrefetch = mutableListOf<Pair<Int, MediaItem>>()
        for (i in 1..PREFETCH_AHEAD) {
            val nextIndex = currentIndex + i
            if (nextIndex >= mediaCount) break
            val mediaItem = player.getMediaItemAt(nextIndex)
            itemsToPrefetch.add(nextIndex to mediaItem)
        }
        
        if (itemsToPrefetch.isEmpty()) return
        
        serviceScope.launch(Dispatchers.IO) {
            for ((nextIndex, mediaItem) in itemsToPrefetch) {
                val videoId = mediaItem.mediaId
                
                if (mediaItem.localConfiguration?.uri != null && 
                    !mediaItem.localConfiguration!!.uri.toString().startsWith("https://placeholder")) continue
                    
                if (urlCache.containsKey(videoId)) continue
                if (resolvingItems.containsKey(videoId)) continue
                
                // Check persistent cache
                if (com.ivor.ivormusic.data.CacheManager.isCached(videoId)) {
                    // Cached on disk? We still need a URL to feed DataSource, but we can reuse expried ones or just resolve fresh
                    // Ideally we resolve fresh to ensure cache key matches wrapper
                }
                
                val resolvedItem = resolveStreamUrl(mediaItem, videoId)
                
                if (resolvedItem.localConfiguration?.uri != null) {
                    serviceScope.launch(Dispatchers.Main) {
                        try {
                            if (nextIndex < player.mediaItemCount) {
                                player.replaceMediaItem(nextIndex, resolvedItem)
                            }
                        } catch (e: Exception) {
                            // Ignore index out of bounds
                        }
                    }
                }
            }
        }
    }
}
