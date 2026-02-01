package com.ivor.ivormusic.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@UnstableApi
class MusicService : MediaLibraryService() {

    // --- Components ---
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var themePreferences: ThemePreferences

    // --- Scopes ---
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // --- State & Cache ---
    // --- State & Cache ---
    // Deduplicated active resolutions: VideoID -> Deferred result
    private val activeResolutions = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<MediaItem>>()
    // Cache for resolved URIs (VideoID -> URI)
    private val uriCache = ConcurrentHashMap<String, String>()
    
    // --- Configuration ---
    private var isCrossfadeEnabled = true
    private var crossfadeDurationMs = 3000L
    private var fadeVolumeJob: Job? = null
    
    // Live Update (Android 16+)
    private var musicProgressLiveUpdate: MusicProgressLiveUpdate? = null

    // Android Auto Cache
    @Volatile private var cachedRecommendations: List<Song>? = null
    @Volatile private var cachedPlaylists: List<PlaylistDisplayItem>? = null
    @Volatile private var cachedPlaylistSongs: MutableMap<String, List<Song>> = mutableMapOf()
    @Volatile private var lastBrowseCacheTime: Long = 0L
    private val browseCacheValidityMs = 5 * 60 * 1000L // 5 minutes

    companion object {
        private const val TAG = "MusicService"
        private const val PREFETCH_AHEAD_COUNT = 3
        private const val RESOLVE_TIMEOUT_MS = 10_000L // Reduced to 10s
        private const val PLACEHOLDER_PREFIX = "https://placeholder.ivormusic/"
        private const val CACHED_PREFIX = "https://cached.ivormusic/"
        private const val ANDROID_AUTO_BROWSE_TIMEOUT_MS = 30_000L
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MusicService Creating...")

        // 1. Initialize Dependencies
        CacheManager.initialize(this)
        youtubeRepository = YouTubeRepository(this)
        downloadRepository = DownloadRepository(this)
        themePreferences = ThemePreferences(this)

        // 2. Setup Notifications & Live Updates
        setMediaNotificationProvider(LiveUpdateMediaNotificationProvider(this))
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            musicProgressLiveUpdate = MusicProgressLiveUpdate(this)
        }

        // 3. Initialize Preferences
        observePreferences()

        // 4. Initialize Player
        initializePlayer()

        // 5. Initialize Session
        initializeSession()

        // 6. Pre-warm caches
        preWarmAutoCache()
    }

    override fun onDestroy() {
        Log.i(TAG, "MusicService Destroying...")
        fadeVolumeJob?.cancel()
        musicProgressLiveUpdate?.hide()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        CacheManager.release()
        activeResolutions.clear()
        uriCache.clear()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    // --- Initialization ---

    private fun initializePlayer() {
        // Custom LoadControl for "Robus + Fast" User Experience
        // We use a 2s start buffer (user request) to ensure we have enough data to avoid immediate buffering
        // but rely on pre-fetching to make it feel instant.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000, // Min Buffer 30s
                60_000, // Max Buffer 60s
                2000,   // Buffer for Playback: 2s (Robust start)
                3000    // Buffer for Rebuffer: 3s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val cacheDataSourceFactory = CacheManager.createCacheDataSourceFactory()
            ?: DefaultDataSource.Factory(this)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        
        player.addListener(PlayerEventListener())
    }

    private fun initializeSession() {
        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName).let {
            val intent = it ?: Intent(this, MainActivity::class.java)
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(sessionIntent)
            .build()
    }

    private fun observePreferences() {
        serviceScope.launch { themePreferences.crossfadeEnabled.collect { isCrossfadeEnabled = it } }
        serviceScope.launch { themePreferences.crossfadeDurationMs.collect { crossfadeDurationMs = it.toLong() } }
        serviceScope.launch {
            themePreferences.maxCacheSizeMb.collect { sizeMb ->
                CacheManager.setMaxCacheSize(this@MusicService, sizeMb)
            }
        }
    }

    // --- Core Logic: The Player Event Listener ---

    private inner class PlayerEventListener : Player.Listener {
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            // 1. Crossfade Logic
            if (isCrossfadeEnabled && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                performFadeIn()
            } else {
                player.volume = 1.0f
            }

            // 2. Critical: Check validity of CURRENT item
            if (mediaItem != null) {
                validateAndPlayCurrentItem(mediaItem)
            }

            // 3. Robust Prefetching of FUTURE items
            prefetchUpcomingSongs()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            
            // Start prefetching as soon as we are ready
            if (playbackState == Player.STATE_READY) {
                prefetchUpcomingSongs()
            }

            // Android 16 Live Update monitoring
            if (playbackState == Player.STATE_READY && player.isPlaying) {
                monitorProgress()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Player Error: ${error.errorCodeName}", error)
            handlePlayerError(error)
        }
    }

    // --- Logic 1: Validation & Playback execution ---

    private fun validateAndPlayCurrentItem(mediaItem: MediaItem) {
        val uri = mediaItem.localConfiguration?.uri
        val videoId = mediaItem.mediaId

        if (isPlaceholder(uri)) {
            Log.w(TAG, "Validation: Hit placeholder for $videoId. Resolving...")
            
            // Launch resolution main-safe
            serviceScope.launch {
                // Get the deduplicated future (reuses existing if prefetch started it)
                val deferred = getOrStartResolution(mediaItem)
                
                try {
                    val resolvedItem = deferred.await()
                    
                    // Apply if still current
                    if (player.currentMediaItem?.mediaId == videoId) {
                        Log.i(TAG, "Validation: Applied resolved item for $videoId")
                        val index = player.currentMediaItemIndex
                        player.replaceMediaItem(index, resolvedItem)
                        player.prepare()
                        player.playWhenReady = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Validation: Resolution failed for $videoId", e)
                }
            }
        } else {
            Log.d(TAG, "Validation: Playing valid URI for $videoId")
        }
    }

    // --- Logic 2: Robust Prefetching ---

    private fun prefetchUpcomingSongs() {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        for (i in 1..PREFETCH_AHEAD_COUNT) {
            val targetIndex = currentIndex + i
            if (targetIndex >= player.mediaItemCount) break

            val item = player.getMediaItemAt(targetIndex)
            val uri = item.localConfiguration?.uri

            if (isPlaceholder(uri)) {
                // Start resolution in background (fire and forget)
                // This populates activeResolutions so validateAndPlayCurrentItem can pick it up instantly
                getOrStartResolution(item)
                
                serviceScope.launch {
                    try {
                        val deferred = getOrStartResolution(item)
                        val resolvedItem = deferred.await()
                        
                        // Update player if item is still there
                        if (targetIndex < player.mediaItemCount && 
                            player.getMediaItemAt(targetIndex).mediaId == item.mediaId) {
                            Log.d(TAG, "Prefetch: Updated item +$i (${item.mediaId})")
                            player.replaceMediaItem(targetIndex, resolvedItem)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Prefetch: Failed to resolve upcoming ${item.mediaId}")
                    }
                }
            }
        }
    }

    // --- Logic 3: Resolution Core (Deduplicated) ---

    private fun getOrStartResolution(mediaItem: MediaItem): kotlinx.coroutines.Deferred<MediaItem> {
        val videoId = mediaItem.mediaId
        
        return activeResolutions.computeIfAbsent(videoId) {
            // Create a new async job
            resolveScope.async {
                performResolution(mediaItem)
            }.also { 
                // Auto-cleanup when done to prevent memory leaks
                it.invokeOnCompletion { activeResolutions.remove(videoId) }
            }
        }
    }

    private suspend fun performResolution(originalItem: MediaItem): MediaItem {
        val videoId = originalItem.mediaId
        Log.d(TAG, "Resolution: Starting for $videoId")
        
        // 1. Downloads
        val downloaded = downloadRepository.downloadedSongs.value.find { it.id == videoId }
        if (downloaded != null && downloaded.uri != null) {
            Log.d(TAG, "Resolution: Found download for $videoId")
            return buildMediaItemWithUri(originalItem, downloaded.uri, downloaded.duration)
        }

        // 2. Cache (Memory)
        uriCache[videoId]?.let { cachedUri ->
            Log.d(TAG, "Resolution: Found cached URI for $videoId")
            return buildMediaItemWithUri(originalItem, Uri.parse(cachedUri))
        }

        // 3. Disk Cache (Fully Cached - Instant Playback)
        if (CacheManager.isFullyCached(videoId)) {
            Log.d(TAG, "Resolution: Found full disk cache for $videoId. Enabling instant playback.")
            return buildMediaItemWithUri(originalItem, Uri.parse("$CACHED_PREFIX$videoId"))
        }

        // 4. Network with Retry
        // YouTubeRepository retry logic handles NewPipe flakiness. 
        // We just handle timeout here.
        return try {
            val result = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                youtubeRepository.getStreamUrl(videoId)
            }
            
            val streamUrl = result?.getOrNull()
            if (!streamUrl.isNullOrEmpty()) {
                uriCache[videoId] = streamUrl
                Log.d(TAG, "Resolution: Network success for $videoId")
                buildMediaItemWithUri(originalItem, Uri.parse(streamUrl))
            } else {
                Log.e(TAG, "Resolution: Failed or Timed Out for $videoId")
                originalItem // Return placeholder (will error in player)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resolution: Exception for $videoId", e)
            originalItem
        }
    }

    private fun buildMediaItemWithUri(original: MediaItem, uri: Uri, duration: Long? = null): MediaItem {
        val metaBuilder = original.mediaMetadata.buildUpon()
        if (original.mediaMetadata.title == null) {
             val cachedInfo = cachedRecommendations?.find { it.id == original.mediaId }
                 ?: cachedPlaylistSongs.values.flatten().find { it.id == original.mediaId }
             
             if (cachedInfo != null) {
                 metaBuilder.setTitle(cachedInfo.title)
                     .setArtist(cachedInfo.artist)
                     .setArtworkUri(if (cachedInfo.thumbnailUrl != null) Uri.parse(cachedInfo.thumbnailUrl) else null)
             }
        }

        return original.buildUpon()
            .setUri(uri)
            .setCustomCacheKey(original.mediaId)
            .setMediaMetadata(metaBuilder.build())
            .setTag(original.mediaId)
            .build()
    }

    private fun isPlaceholder(uri: Uri?): Boolean {
        return uri == null || uri.toString().startsWith(PLACEHOLDER_PREFIX)
    }

    // --- Logic 4: Error Handling ---

    private fun handlePlayerError(error: PlaybackException) {
        val currentItem = player.currentMediaItem ?: return
        val videoId = currentItem.mediaId
        
        Log.w(TAG, "Handling Error for $videoId")

        // 1. If we are already resolving this item, just wait.
        // The validation logic or update logic will handle it when ready.
        if (activeResolutions.containsKey(videoId)) {
            Log.d(TAG, "Error: Already resolving $videoId. Ignoring error.")
            // Temporarily pause to stop spinning until resolution finishes
            player.playWhenReady = true // Keep it true so UI shows buffering?
            // Actually, if we error, we are in IDLE.
            // We verify:
            return
        }

        // 2. Retry Logic
        val retryCountKey = "retry_count_$videoId"
        val retryCount = uriCache[retryCountKey]?.toIntOrNull() ?: 0

        if (retryCount < 2) {
            Log.w(TAG, "Error: Retrying ($retryCount/2) for $videoId...")
            uriCache[retryCountKey] = (retryCount + 1).toString()
            uriCache.remove(videoId) // Clear bad cache
            
            serviceScope.launch {
                delay(1000)
                // FORCE new resolution
                activeResolutions.remove(videoId) 
                
                val deferred = getOrStartResolution(currentItem)
                try {
                    val resolved = deferred.await()
                    if (player.currentMediaItem?.mediaId == videoId) {
                         player.replaceMediaItem(player.currentMediaItemIndex, resolved)
                         player.prepare()
                         player.play()
                    }
                } catch (e: Exception) {
                    // Retry failed, skip.
                    if (player.hasNextMediaItem()) {
                         player.seekToNext()
                         player.play()
                    }
                }
            }
        } else {
            Log.e(TAG, "Error: Max retries exhausted for $videoId. Skipping.")
            if (player.hasNextMediaItem()) {
                player.seekToNext()
                player.play()
            } else {
                player.stop()
            }
        }
    }

    // --- Media Library Session Callback ---
    
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()
                
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                availablePlayerCommands
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // This is called when user clicks a song or "Play All"
            
            val processedItems = mediaItems.map { item ->
                // Mark all incoming items as placeholders initially
                // This delegates ALL resolution logic to our robust prefetch system
                // instead of blocking the UI thread waiting for the first song.
                // We create a valid MediaItem but with a placeholder URI.
                
                val videoId = item.mediaId
                
                // Check if we have metadata in our browse cache to enrich the item immediately
                var meta = item.mediaMetadata
                if (meta.title == null) {
                    val cached = findSongInCache(videoId)
                    if (cached != null) {
                        meta = MediaMetadata.Builder()
                            .setTitle(cached.title)
                            .setArtist(cached.artist)
                            .setAlbumTitle(cached.album)
                            .setArtworkUri(if (cached.thumbnailUrl != null) Uri.parse(cached.thumbnailUrl) else null)
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build()
                    }
                }

                MediaItem.Builder()
                    .setMediaId(videoId)
                    .setUri("$PLACEHOLDER_PREFIX$videoId")
                    .setMediaMetadata(meta)
                    .build()
            }.toMutableList()

            return Futures.immediateFuture(processedItems)
        }
        
        // --- Browsing Logic (Android Auto / Media Browser) ---

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = android.os.Bundle().apply {
                putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) // Grid
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1) // List
            }
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Root")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(rootItem, MediaLibraryService.LibraryParams.Builder().setExtras(rootExtras).build())
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == "root") {
                return Futures.immediateFuture(LibraryResult.ofItemList(getRootItems(), null))
            }
            
            // Async fetch for content
            return serviceScope.future(Dispatchers.IO) {
                val items = fetchChildrenForId(parentId)
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }
        }
    }
    
    // --- Browsing Helper Methods ---
    
    private fun getRootItems(): ImmutableList<MediaItem> {
        val items = mutableListOf<MediaItem>()
        // 1. Recommended
        items.add(MediaItem.Builder()
            .setMediaId("RECOMMENDED")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Recommended For You").setIsBrowsable(true).setIsPlayable(false).build())
            .build())
        // 2. Playlists
        items.add(MediaItem.Builder()
            .setMediaId("PLAYLISTS")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Your Playlists").setIsBrowsable(true).setIsPlayable(false).build())
            .build())
        return ImmutableList.copyOf(items)
    }

    private suspend fun fetchChildrenForId(parentId: String): List<MediaItem> {
        val now = System.currentTimeMillis()
        val isCacheValid = (now - lastBrowseCacheTime) < browseCacheValidityMs
        
        return when (parentId) {
            "RECOMMENDED" -> {
                val songs = if (isCacheValid && cachedRecommendations != null) {
                    cachedRecommendations!!
                } else {
                    val result = youtubeRepository.getRecommendations()
                    if (result.isNotEmpty()) {
                        cachedRecommendations = result
                        lastBrowseCacheTime = now
                    }
                    result
                }
                songs.map(::mapSongToMediaItem)
            }
            "PLAYLISTS" -> {
                val playlists = if (isCacheValid && cachedPlaylists != null) {
                    cachedPlaylists!!
                } else {
                    val result = youtubeRepository.getUserPlaylists()
                    if (result.isNotEmpty()) {
                        cachedPlaylists = result
                        lastBrowseCacheTime = now
                    }
                    result
                }
                playlists.map { playlist ->
                    val playlistId = playlist.url.substringAfter("list=")
                    MediaItem.Builder()
                        .setMediaId("PLAYLIST_$playlistId")
                        .setMediaMetadata(MediaMetadata.Builder()
                            .setTitle(playlist.name)
                            .setSubtitle(playlist.uploaderName)
                            .setArtworkUri(Uri.parse(playlist.thumbnailUrl ?: ""))
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .build())
                        .build()
                }
            }
            else -> {
                if (parentId.startsWith("PLAYLIST_")) {
                    val playlistId = parentId.removePrefix("PLAYLIST_")
                    val songs = cachedPlaylistSongs[playlistId]?.takeIf { isCacheValid }
                        ?: youtubeRepository.getPlaylist(playlistId).also {
                            if (it.isNotEmpty()) cachedPlaylistSongs[playlistId] = it
                        }
                    songs.map(::mapSongToMediaItem)
                } else {
                    emptyList()
                }
            }
        }
    }

    private fun mapSongToMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(Uri.parse(song.thumbnailUrl ?: ""))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build())
            .build()
    }
    
    private fun findSongInCache(videoId: String): Song? {
        return cachedRecommendations?.find { it.id == videoId }
            ?: cachedPlaylistSongs.values.flatten().find { it.id == videoId }
    }

    // --- Helpers ---

    private fun preWarmAutoCache() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (cachedRecommendations == null) {
                    val recs = youtubeRepository.getRecommendations()
                    if (recs.isNotEmpty()) {
                        cachedRecommendations = recs
                        lastBrowseCacheTime = System.currentTimeMillis()
                    }
                }
                if (cachedPlaylists == null) {
                    val playlists = youtubeRepository.getUserPlaylists()
                    if (playlists.isNotEmpty()) cachedPlaylists = playlists
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-warm cache", e)
            }
        }
    }

    private fun performFadeIn() {
        fadeVolumeJob?.cancel()
        fadeVolumeJob = serviceScope.launch {
            player.volume = 0f
            val steps = 20
            val stepTime = crossfadeDurationMs / steps
            for (i in 1..steps) {
                player.volume = i / steps.toFloat()
                delay(stepTime)
            }
            player.volume = 1f
        }
    }
    
    private fun monitorProgress() {
        serviceScope.launch {
            while (isActive && player.isPlaying) {
                val duration = player.duration
                val position = player.currentPosition
                
                // Android 16 Live Update
                if (duration > 0) {
                     val mediaItem = player.currentMediaItem
                     musicProgressLiveUpdate?.updateProgress(
                         songTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                         artistName = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown",
                         currentPositionMs = position,
                         durationMs = duration,
                         isPlaying = true
                     )
                }
                
                // Crossfade Logic (Fade Out)
                if (isCrossfadeEnabled && duration > position) {
                    val remaining = duration - position
                    if (remaining <= crossfadeDurationMs) {
                        val volume = (remaining.toFloat() / crossfadeDurationMs).coerceIn(0f, 1f)
                        player.volume = volume
                    }
                }
                
                delay(1000)
            }
        }
    }
}
