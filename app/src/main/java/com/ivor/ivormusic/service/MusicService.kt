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
import com.google.common.collect.ImmutableList
import androidx.media3.session.LibraryResult
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
    
    // Live Update for music progress (Android 16+)
    private var musicProgressLiveUpdate: MusicProgressLiveUpdate? = null
    
    companion object {
        private const val TAG = "MusicService"
        private const val PREFETCH_AHEAD = 3
        private const val STREAM_TIMEOUT_MS = 10000L
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService onCreate")
        
        // Initialize Cache Manager
        com.ivor.ivormusic.data.CacheManager.initialize(this)
        
        // Set custom media notification provider for Android 16 Live Activities support
        setMediaNotificationProvider(LiveUpdateMediaNotificationProvider(this))
        
        // Initialize Live Update for music progress (Android 16+)
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            musicProgressLiveUpdate = MusicProgressLiveUpdate(this)
        }
        
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "onGetSession called from: ${controllerInfo.packageName}")
        return mediaLibrarySession
    }

    override fun onDestroy() {
        fadeVolumeJob?.cancel()
        musicProgressLiveUpdate?.hide()
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
                         val finalItem = resolveStreamUrl(item, videoId)
                            
                         mutableListOf(finalItem)
                    } else {
                        // Batch add, prefetch later
                        mediaItems.map { 
                            it.buildUpon().setCustomCacheKey(it.mediaId).build()
                        }.toMutableList()
                    }
                }
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return serviceScope.future {
                    Log.d(TAG, "onGetLibraryRoot called from package: ${browser.packageName}")
                    val rootExtras = android.os.Bundle().apply {
                         putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                         putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // Grid
                         putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) // List
                    }
                    val rootItem = MediaItem.Builder()
                        .setMediaId("root")
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle("Root")
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build()
                    LibraryResult.ofItem(rootItem, MediaLibraryService.LibraryParams.Builder().setExtras(rootExtras).build())
                }
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    Log.d(TAG, "onGetChildren called for parentId: $parentId")
                    val items = mutableListOf<MediaItem>()
                    
                    when (parentId) {
                        "root" -> {
                            // 1. Recommended (Grid Layout for Songs)
                            val recommendedExtras = android.os.Bundle().apply {
                                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 2) // Grid
                            }
                            items.add(
                                MediaItem.Builder()
                                    .setMediaId("RECOMMENDED")
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle("Recommended For You")
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setExtras(recommendedExtras)
                                            .build()
                                    )
                                    .build()
                            )
                            // 2. Playlists (Grid Layout for Playlists)
                            val playlistsExtras = android.os.Bundle().apply {
                                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // Grid
                            }
                            items.add(
                                MediaItem.Builder()
                                    .setMediaId("PLAYLISTS")
                                    .setMediaMetadata(
                                        androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle("Your Playlists")
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setExtras(playlistsExtras)
                                            .build()
                                    )
                                    .build()
                            )
                        }
                        "RECOMMENDED" -> {
                            try {
                                val songs = youtubeRepository.getRecommendations()
                                songs.forEach { song ->
                                    items.add(
                                        MediaItem.Builder()
                                            .setMediaId(song.id)
                                            .setMediaMetadata(
                                                androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(song.title)
                                                    .setArtist(song.artist)
                                                    .setAlbumTitle(song.album)
                                                    .setArtworkUri(android.net.Uri.parse(song.thumbnailUrl ?: ""))
                                                    .setIsBrowsable(false)
                                                    .setIsPlayable(true)
                                                    .build()
                                            )
                                            .build()
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching recommended", e)
                            }
                        }
                        "PLAYLISTS" -> {
                             try {
                                val playlists = youtubeRepository.getUserPlaylists()
                                playlists.forEach { playlist ->
                                    val extras = android.os.Bundle().apply {
                                        // Hint Grid Item for the playlist itself
                                        putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2) // Grid
                                    }
                                    // Extract ID from URL (simple assumption for now)
                                    val playlistId = playlist.url.substringAfter("list=")
                                    
                                    items.add(
                                        MediaItem.Builder()
                                            // Navigation ID prefix to distinguish from videos
                                            .setMediaId("PLAYLIST_$playlistId") 
                                            .setMediaMetadata(
                                                androidx.media3.common.MediaMetadata.Builder()
                                                    .setTitle(playlist.name)
                                                    .setSubtitle(playlist.uploaderName) // Use subtitle for artist/uploader
                                                    .setArtworkUri(android.net.Uri.parse(playlist.thumbnailUrl ?: ""))
                                                    .setIsBrowsable(true)
                                                    .setIsPlayable(false)
                                                    .setExtras(extras)
                                                    .build()
                                            )
                                            .build()
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching playlists", e)
                            }
                        }
                        else -> {
                            // Handle Playlist Drill-down
                            if (parentId.startsWith("PLAYLIST_")) {
                                val playlistId = parentId.removePrefix("PLAYLIST_")
                                try {
                                    val songs = youtubeRepository.getPlaylist(playlistId)
                                    songs.forEach { song ->
                                        items.add(
                                            MediaItem.Builder()
                                                .setMediaId(song.id)
                                                .setMediaMetadata(
                                                    androidx.media3.common.MediaMetadata.Builder()
                                                        .setTitle(song.title)
                                                        .setArtist(song.artist)
                                                        .setAlbumTitle(song.album)
                                                        .setArtworkUri(android.net.Uri.parse(song.thumbnailUrl ?: ""))
                                                        .setIsBrowsable(false)
                                                        .setIsPlayable(true)
                                                        .build()
                                                )
                                                .build()
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching playlist details", e)
                                }
                            }
                        }
                    }
                    
                    LibraryResult.ofItemList(com.google.common.collect.ImmutableList.copyOf(items), null)
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
                val duration = player.duration
                val position = player.currentPosition
                val isPlaying = player.isPlaying
                
                // Update Live Update notification (Android 16+)
                if (isPlaying && duration > 0 && position >= 0) {
                    val mediaItem = player.currentMediaItem
                    val title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown"
                    val artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist"
                    
                    musicProgressLiveUpdate?.updateProgress(
                        songTitle = title,
                        artistName = artist,
                        currentPositionMs = position,
                        durationMs = duration,
                        isPlaying = true
                    )
                } else if (!isPlaying) {
                    // Hide Live Update when paused
                    musicProgressLiveUpdate?.hide()
                }
                
                // Crossfade logic
                if (isCrossfadeEnabled && isPlaying) {
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
                kotlinx.coroutines.delay(1000) // Update every second for Live Update
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
        // Note: Even for cached content, we still need a valid URL because ExoPlayer's
        // CacheDataSource uses the URL for content metadata. The cache will be used
        // automatically during playback if content exists under the customCacheKey.

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
