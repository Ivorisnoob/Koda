package com.ivor.ivormusic.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import kotlinx.coroutines.withTimeoutOrNull
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ConcurrentHashMap

class MusicService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: Player
    private lateinit var youtubeRepository: YouTubeRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Cache for resolved stream URLs (videoId -> streamUrl)
    private val urlCache = ConcurrentHashMap<String, String>()
    
    // Track which items are being resolved to avoid duplicate requests
    private val resolvingItems = ConcurrentHashMap<String, Boolean>()
    
    companion object {
        private const val TAG = "MusicService"
        private const val PREFETCH_AHEAD = 2 
        private const val STREAM_TIMEOUT_MS = 6000L
    }

    override fun onCreate() {
        super.onCreate()
        initializeSessionAndPlayer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        urlCache.clear()
        resolvingItems.clear()
        super.onDestroy()
    }

    private fun initializeSessionAndPlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        youtubeRepository = YouTubeRepository(this)

        // Add listener for lazy pre-fetching of upcoming songs
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // Pre-fetch next songs when track changes
                prefetchUpcomingSongs()
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_READY) {
                    // Also trigger prefetch when playback is ready
                    prefetchUpcomingSongs()
                }
            }
        })

        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                return serviceScope.future {
                    // LAZY RESOLUTION: Only resolve first 2 songs immediately
                    // Rest will be resolved in background as playback progresses
                    
                    val immediateCount = minOf(2, mediaItems.size)
                    Log.d(TAG, "Adding ${mediaItems.size} items, resolving first $immediateCount immediately")
                    
                    val resolvedItems = mediaItems.mapIndexed { index, item ->
                        async(Dispatchers.IO) {
                            val videoId = item.mediaId
                            
                            // Check if already has URI (local songs)
                            if (item.localConfiguration?.uri != null) {
                                return@async item
                            }
                            
                            // Check cache first
                            urlCache[videoId]?.let { cachedUrl ->
                                Log.d(TAG, "Cache hit for $videoId")
                                return@async item.buildUpon()
                                    .setUri(android.net.Uri.parse(cachedUrl))
                                    .build()
                            }
                            
                            // Only resolve immediately for first N songs
                            if (index < immediateCount) {
                                resolveStreamUrl(item, videoId)
                            } else {
                                // Return without URI - will be resolved later
                                item
                            }
                        }
                    }
                    resolvedItems.awaitAll().toMutableList()
                }
            }
        })
            .setSessionActivity(sessionIntent)
            .build()
    }
    
    /**
     * Resolve stream URL for a media item
     */
    private suspend fun resolveStreamUrl(item: MediaItem, videoId: String): MediaItem {
        // Mark as resolving
        if (resolvingItems.putIfAbsent(videoId, true) == true) {
            // Already being resolved by another coroutine, wait and check cache
            kotlinx.coroutines.delay(100)
            urlCache[videoId]?.let { cachedUrl ->
                return item.buildUpon()
                    .setUri(android.net.Uri.parse(cachedUrl))
                    .build()
            }
        }
        
        return try {
            val streamUrl = withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                youtubeRepository.getStreamUrl(videoId)
            }
            if (streamUrl != null) {
                // Cache the URL
                urlCache[videoId] = streamUrl
                Log.d(TAG, "Resolved URL for $videoId")
                item.buildUpon()
                    .setUri(android.net.Uri.parse(streamUrl))
                    .build()
            } else {
                Log.w(TAG, "Failed to resolve URL for $videoId (timeout)")
                item
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving URL for $videoId", e)
            item
        } finally {
            resolvingItems.remove(videoId)
        }
    }
    
    /**
     * Pre-fetch stream URLs for upcoming songs in the queue
     */
    private fun prefetchUpcomingSongs() {
        // Collect all player data on the main thread first (required by ExoPlayer)
        val currentIndex = player.currentMediaItemIndex
        val mediaCount = player.mediaItemCount
        
        if (mediaCount == 0) return
        
        // Collect media items to prefetch while still on main thread
        val itemsToPrefetch = mutableListOf<Pair<Int, MediaItem>>()
        for (i in 1..PREFETCH_AHEAD) {
            val nextIndex = currentIndex + i
            if (nextIndex >= mediaCount) break
            val mediaItem = player.getMediaItemAt(nextIndex)
            itemsToPrefetch.add(nextIndex to mediaItem)
        }
        
        if (itemsToPrefetch.isEmpty()) return
        
        // Now launch IO coroutine with the collected data
        serviceScope.launch(Dispatchers.IO) {
            for ((nextIndex, mediaItem) in itemsToPrefetch) {
                val videoId = mediaItem.mediaId
                
                // Skip if already has URI or is cached
                if (mediaItem.localConfiguration?.uri != null) continue
                if (urlCache.containsKey(videoId)) continue
                if (resolvingItems.containsKey(videoId)) continue
                
                Log.d(TAG, "Prefetching URL for song at index $nextIndex (videoId: $videoId)")
                
                val resolvedItem = resolveStreamUrl(mediaItem, videoId)
                
                // Update the media item in the player if we got a URL
                if (resolvedItem.localConfiguration?.uri != null) {
                    serviceScope.launch(Dispatchers.Main) {
                        try {
                            // Replace the item in the playlist with the resolved one
                            player.replaceMediaItem(nextIndex, resolvedItem)
                            Log.d(TAG, "Updated media item at index $nextIndex with resolved URL")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update media item at $nextIndex", e)
                        }
                    }
                }
            }
        }
    }
}
