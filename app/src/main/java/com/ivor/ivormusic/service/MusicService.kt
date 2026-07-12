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
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
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
import kotlinx.coroutines.cancel
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
    // Deduplicated active resolutions: VideoID -> Deferred result
    private val activeResolutions = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<MediaItem>>()

    // Cache for resolved stream URIs. googlevideo URLs die after ~6h (their
    // `expire` param) and on network/IP changes, so each entry carries an
    // expiry and is dropped instead of being replayed as a guaranteed 403.
    private class CachedUri(val uri: String, val expiresAtMs: Long)
    private val uriCache = ConcurrentHashMap<String, CachedUri>()

    // Per-song playback error retries. Kept separate from uriCache and reset
    // on successful playback so a song can't permanently exhaust its budget
    // over the lifetime of the service.
    private val retryCounts = ConcurrentHashMap<String, Int>()
    
    // --- Configuration ---
    private var isCrossfadeEnabled = true
    private var crossfadeDurationMs = 3000L
    private var fadeVolumeJob: Job? = null
    private var progressJob: Job? = null

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
        // Resolution is two InnerTube /player calls at most, each hard-capped at
        // 8s by OkHttp's callTimeout in YouTubeRepository.
        private const val RESOLVE_TIMEOUT_MS = 20_000L
        private const val PLACEHOLDER_PREFIX = "https://placeholder.ivormusic/"
        private const val CACHED_PREFIX = "https://cached.ivormusic/"
        private const val ANDROID_AUTO_BROWSE_TIMEOUT_MS = 30_000L
        // Safety margin before a googlevideo URL's `expire` timestamp, and the
        // fallback lifetime when the URL carries no readable expire param.
        private const val URI_EXPIRY_SAFETY_MS = 5 * 60 * 1000L
        private const val URI_DEFAULT_TTL_MS = 4 * 60 * 60 * 1000L
    }

    /**
     * When the cached URI for a googlevideo URL stops being usable. Prefers the
     * URL's own `expire` query param (epoch seconds, ~6h out) minus a safety
     * margin; falls back to a conservative fixed TTL.
     */
    private fun streamUrlExpiryMs(url: String): Long {
        val expireSec = try {
            Uri.parse(url).getQueryParameter("expire")?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
        return if (expireSec != null && expireSec > 0) {
            expireSec * 1000L - URI_EXPIRY_SAFETY_MS
        } else {
            System.currentTimeMillis() + URI_DEFAULT_TTL_MS
        }
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When the user swipes the app from recents, pause playback and stop the
        // service so the foreground notification is dismissed instead of getting
        // stuck (a foreground-service notification cannot be swiped away by the user).
        // pauseAllPlayersAndStopSelf() is the official Media3 helper for this.
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        Log.i(TAG, "MusicService Destroying...")
        fadeVolumeJob?.cancel()
        progressJob?.cancel()
        // Cancel the scopes themselves — they host the preference collectors and
        // any in-flight resolutions, which would otherwise outlive the service.
        serviceScope.cancel()
        resolveScope.cancel()
        musicProgressLiveUpdate?.hide()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        CacheManager.release()
        activeResolutions.clear()
        uriCache.clear()
        retryCounts.clear()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        // May be null briefly during teardown; Media3 handles this gracefully and
        // the connecting MediaController will simply receive a connection failure
        // rather than binding to a released session.
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

        // SMART DATA SOURCE FACTORY
        // Logic: Use CacheDataSource for network (http/https), but use valid DefaultDataSource for local files (content/file).
        // This prevents the cache from trying to grasp local content which causes playback failures on some devices.
        
        // Per-URL User-Agent — googlevideo URLs are tagged with their issuing
        // client (?c=IOS, ?c=TVHTML5_SIMPLY_EMBEDDED, ...) and YouTube answers
        // 403 if the playback UA doesn't match. CacheManager.createPerClientHttpFactory()
        // picks the UA per request.
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, CacheManager.createPerClientHttpFactory())
        val cacheDataSourceFactory = CacheManager.createCacheDataSourceFactory(null)
            ?: defaultDataSourceFactory

        val smartDataSourceFactory = DataSource.Factory {
            val defaultSource = defaultDataSourceFactory.createDataSource()
            val cacheSource = if (cacheDataSourceFactory != defaultDataSourceFactory) {
                cacheDataSourceFactory.createDataSource()
            } else null

            object : DataSource {
                private var currentSource: DataSource? = null

                override fun addTransferListener(transferListener: TransferListener) {
                    defaultSource.addTransferListener(transferListener)
                    cacheSource?.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme
                    val isNetwork = scheme == "http" || scheme == "https"
                    
                    // Route to Cache only for network requests
                    currentSource = if (isNetwork && cacheSource != null) {
                        cacheSource
                    } else {
                        defaultSource
                    }
                    return currentSource!!.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    return currentSource?.read(buffer, offset, length) ?: 0
                }

                override fun getUri(): Uri? {
                    return currentSource?.uri
                }

                override fun getResponseHeaders(): Map<String, List<String>> {
                    return currentSource?.responseHeaders ?: emptyMap()
                }

                override fun close() {
                    currentSource?.close()
                    currentSource = null
                }
            }
        }

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this).setDataSourceFactory(smartDataSourceFactory)
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
                // The current song plays — give it back its full retry budget
                // so one bad stretch (expired URL, network blip) months of
                // uptime ago can't permanently blacklist it.
                player.currentMediaItem?.mediaId?.let { retryCounts.remove(it) }
                prefetchUpcomingSongs()
            }

            // Android 16 Live Update: dismiss when playback ends or returns to idle so
            // the progress chip never lingers on the lock screen / shade after the
            // queue finishes or the service is paused.
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                progressJob?.cancel()
                musicProgressLiveUpdate?.hide()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            // Single-flight: only one progress monitor coroutine ever runs. Previous
            // approach launched a fresh loop on every STATE_READY transition (which
            // fires multiple times per song due to URI resolution / replaceMediaItem),
            // resulting in N concurrent loops fighting over crossfade volume and
            // spamming the Live Update notification.
            if (isPlaying) {
                monitorProgress()
            } else {
                progressJob?.cancel()
                progressJob = null
                musicProgressLiveUpdate?.hide()
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
                        // Read playWhenReady NOW, at apply time — not before resolution.
                        // This transition fires during setMediaItem, which races ahead
                        // of the play() that a user tap issues right after. Capturing
                        // earlier would latch a stale `false` and clobber the user's
                        // play() when we wrote it back. By apply time the intent is
                        // settled: true for a tap, still false for cold-start restore
                        // (which never calls play()), so playback no longer pauses.
                        val playWhenReady = player.playWhenReady
                        Log.i(TAG, "Validation: Applied resolved item for $videoId (playWhenReady=$playWhenReady)")
                        val index = player.currentMediaItemIndex
                        player.replaceMediaItem(index, resolvedItem)
                        player.prepare()
                        player.playWhenReady = playWhenReady
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

        // 2. Cache (Memory) — only while the underlying googlevideo URL is
        // still valid; expired entries are re-resolved instead of replayed.
        uriCache[videoId]?.let { cached ->
            if (cached.expiresAtMs > System.currentTimeMillis()) {
                Log.d(TAG, "Resolution: Found cached URI for $videoId")
                return buildMediaItemWithUri(originalItem, Uri.parse(cached.uri))
            }
            Log.d(TAG, "Resolution: Cached URI expired for $videoId, re-resolving")
            uriCache.remove(videoId)
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
                uriCache[videoId] = CachedUri(streamUrl, streamUrlExpiryMs(streamUrl))
                Log.d(TAG, "Resolution: Network success for $videoId")
                buildMediaItemWithUri(originalItem, Uri.parse(streamUrl))
            } else {
                Log.e(TAG, "Resolution: Failed or Timed Out for $videoId")
                // Return an item with a special error URI instead of the placeholder
                // This breaks the loop because isPlaceholder() will be false.
                buildMediaItemWithUri(originalItem, Uri.parse("error://resolution_failed/$videoId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resolution: Exception for $videoId", e)
            buildMediaItemWithUri(originalItem, Uri.parse("error://exception/$videoId"))
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
        val uri = currentItem.localConfiguration?.uri
        
        Log.w(TAG, "Handling Error for $videoId (uri=$uri)")

        // Local songs (content:// or file://) — errors are typically unrecoverable
        // (file deleted, permission revoked, corrupt file). Don't try YouTube resolution.
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            Log.e(TAG, "Error: Local song $videoId failed. Skipping (not retryable via YouTube).")
            if (player.hasNextMediaItem()) {
                player.seekToNext()
                player.play()
            } else {
                player.stop()
            }
            return
        }

        // 1. If we are already resolving this item, just wait.
        // The validation logic or update logic will handle it when ready.
        if (activeResolutions.containsKey(videoId)) {
            Log.d(TAG, "Error: Already resolving $videoId. Ignoring error.")
            player.playWhenReady = true
            return
        }

        // 2. Retry Logic (YouTube songs only)
        val retryCount = retryCounts[videoId] ?: 0

        if (retryCount < 2) {
            Log.w(TAG, "Error: Retrying ($retryCount/2) for $videoId...")
            retryCounts[videoId] = retryCount + 1
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
                val videoId = item.mediaId
                val existingUri = item.localConfiguration?.uri
                
                // Check if this item already has a valid, non-placeholder URI.
                // Local songs come with either content:// (MediaStore) or file:// (downloaded) URIs
                // that ExoPlayer can play directly — we must NOT overwrite them with a placeholder.
                val isLocalUri = existingUri != null 
                    && !existingUri.toString().startsWith(PLACEHOLDER_PREFIX)
                    && (existingUri.scheme == "content" || existingUri.scheme == "file")
                
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

                if (isLocalUri) {
                    // Local song: preserve the original content:// URI for direct playback
                    Log.d(TAG, "onAddMediaItems: Preserving local URI for $videoId: $existingUri")
                    MediaItem.Builder()
                        .setMediaId(videoId)
                        .setUri(existingUri)
                        .setMediaMetadata(meta)
                        .build()
                } else {
                    // YouTube song: use placeholder — resolution will happen via prefetch system
                    MediaItem.Builder()
                        .setMediaId(videoId)
                        .setUri("$PLACEHOLDER_PREFIX$videoId")
                        .setMediaMetadata(meta)
                        .build()
                }
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
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            try {
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
            } finally {
                // Loop exited (paused / stopped / cancelled) — drop the live update so
                // it never freezes at the last reported progress.
                musicProgressLiveUpdate?.hide()
            }
        }
    }
}
