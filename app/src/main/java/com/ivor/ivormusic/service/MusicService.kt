package com.ivor.ivormusic.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.MainActivity
import android.media.audiofx.AudioEffect
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.NotificationArtworkLoader
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
    // Pinned at player creation and broadcast so external equalizer apps can
    // attach to Koda's playback
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
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

    // Kept for warmStreamCache; playback wires the factory into the player
    // separately in initializePlayer.
    private var cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory? = null

    // Songs whose stream head has been (or is being) written into the disk
    // cache this session, so each prefetch round doesn't re-warm them.
    private val warmedIds =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // One warm at a time: warming must never contend with the current song's
    // own buffering for the whole prefetch window.
    private val warmSemaphore = kotlinx.coroutines.sync.Semaphore(1)
    
    // --- Configuration ---
    private var isCrossfadeEnabled = true
    private var crossfadeDurationMs = 3000L
    // Read on the playback data-source hot path (every open()), so it's a
    // volatile field fed by the preference flow instead of a prefs read.
    @Volatile private var isCacheEnabled = true
    private var fadeVolumeJob: Job? = null
    private var progressJob: Job? = null

    // Live Update (Android 16+)
    private var musicProgressLiveUpdate: MusicProgressLiveUpdate? = null

    /** Artwork URLs already being fetched for the Live Update, so a per-second
     *  progress loop does not kick off the same load repeatedly. */
    private val liveUpdateArtworkRequested = mutableSetOf<String>()

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
        // Stream head pre-cached for upcoming songs: ~30s of opus audio, enough
        // to cover the 0.5s start buffer plus the first ranged chunk's RTT.
        private const val WARM_CACHE_BYTES = 512L * 1024

        // --- Sleep timer: the contract with PlayerViewModel ---

        /** Arm the timer. Carries [ARG_SLEEP_TIMER_MINUTES]; 0 = end of track. */
        const val CMD_SLEEP_TIMER_SET = "com.ivor.ivormusic.SLEEP_TIMER_SET"
        const val CMD_SLEEP_TIMER_CANCEL = "com.ivor.ivormusic.SLEEP_TIMER_CANCEL"
        const val ARG_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"

        /** Session-extras keys the timer state is published under. */
        const val EXTRA_SLEEP_TIMER_ENDS_AT = "sleep_timer_ends_at"
        const val EXTRA_SLEEP_TIMER_END_OF_TRACK = "sleep_timer_end_of_track"

        /**
         * How long the fade before the timer's pause takes. Long enough to read
         * as drifting off rather than as a glitch, short enough that the last
         * thing heard is not a minute of near-silence.
         */
        private const val SLEEP_TIMER_FADE_MS = 5_000L

        /**
         * Longest a single slice of the countdown sleeps for. Bounded so the
         * job re-checks the real deadline regularly instead of trusting one
         * long delay that deep sleep can stretch.
         */
        private const val SLEEP_TIMER_TICK_MS = 30_000L
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
        themePreferences = ThemePreferences(this)
        isCacheEnabled = themePreferences.cacheEnabled.value
        // Initialize the cache directly at the persisted size instead of the
        // default; the size and toggle stay live via observePreferences().
        CacheManager.initialize(this, themePreferences.maxCacheSizeMb.value)
        youtubeRepository = YouTubeRepository(this)
        downloadRepository = DownloadRepository.getInstance(this)

        // 2. Setup Notifications & Live Updates
        // Create the shared playback channel before the media provider is
        // installed, so it exists with our settings (silent, no badge, public
        // on the lock screen) rather than whatever Media3 would default to.
        // Channel settings are immutable once created.
        MusicProgressLiveUpdate.ensureChannel(this)
        LiveUpdateMediaNotificationProvider.deleteLegacyMediaChannel(this)
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

        // 7. Warm the visitorData token so the first stream resolution of a
        // session doesn't pay for the mint on its critical path. Music-first
        // sessions (and Android Auto) never construct VideoPlayerViewModel,
        // which was the only other place that prefetched it.
        resolveScope.launch { youtubeRepository.prefetchVisitorData() }
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
        sleepTimerJob?.cancel()
        // Cancel the scopes themselves — they host the preference collectors and
        // any in-flight resolutions, which would otherwise outlive the service.
        serviceScope.cancel()
        resolveScope.cancel()
        musicProgressLiveUpdate?.hide()
        // Tell external equalizers our audio session is going away
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            })
        }
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        CacheManager.release()
        activeResolutions.clear()
        uriCache.clear()
        retryCounts.clear()
        warmedIds.clear()
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
        // Custom LoadControl: near-instant starts + whole-song read-ahead.
        // Playback begins once only 0.5s is buffered, then ExoPlayer keeps
        // loading up to 5 minutes ahead (min == max so the buffer is topped up
        // continuously instead of sawtoothing between the two). Since streams
        // flow through CacheDataSource, this means most songs are fully on
        // disk shortly after they start playing. Audio bitrates keep 5 minutes
        // of samples at a few MB of RAM, so time thresholds can safely win
        // over size ones.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                300_000, // Min buffer 5min (== max: continuous top-up)
                300_000, // Max buffer 5min
                500,     // Buffer for Playback: 0.5s (near-instant start)
                3000     // Buffer for Rebuffer: 3s
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
        // Null when cache init failed — playback then always goes direct.
        val cacheDataSourceFactory = CacheManager.createCacheDataSourceFactory(null)
        this.cacheDataSourceFactory = cacheDataSourceFactory

        val smartDataSourceFactory = DataSource.Factory {
            val defaultSource = defaultDataSourceFactory.createDataSource()
            val cacheSource = cacheDataSourceFactory?.createDataSource()

            object : DataSource {
                private var currentSource: DataSource? = null

                override fun addTransferListener(transferListener: TransferListener) {
                    defaultSource.addTransferListener(transferListener)
                    cacheSource?.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme
                    val isNetwork = scheme == "http" || scheme == "https"

                    // Route to cache only for network requests, and only while
                    // the user's cache setting is on (checked per open() so a
                    // toggle applies to the very next stream, no restart).
                    currentSource = if (isNetwork && isCacheEnabled && cacheSource != null) {
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

        // Pin a known audio session id and announce it to the system, so
        // external equalizer apps (Poweramp Equalizer, Wavelet, the OEM EQ)
        // can attach their effects to Koda's music playback. Generating the
        // id ourselves means it exists before the audio sink initializes on
        // first playback (ExoPlayer's own id stays UNSET until then).
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val generatedSessionId = audioManager.generateAudioSessionId()
        if (generatedSessionId != android.media.AudioManager.ERROR) {
            audioSessionId = generatedSessionId
            player.audioSessionId = generatedSessionId
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, generatedSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            sendBroadcast(intent)
            Log.i(TAG, "Announced audio session $generatedSessionId for external equalizers")
        }
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
        // These flows update live across ThemePreferences instances (the
        // settings screen writes through its own instance) thanks to the
        // SharedPreferences change listener inside ThemePreferences.
        serviceScope.launch { themePreferences.crossfadeEnabled.collect { isCrossfadeEnabled = it } }
        serviceScope.launch { themePreferences.crossfadeDurationMs.collect { crossfadeDurationMs = it.toLong() } }
        serviceScope.launch { themePreferences.cacheEnabled.collect { isCacheEnabled = it } }
        serviceScope.launch {
            themePreferences.maxCacheSizeMb.collect { sizeMb ->
                CacheManager.setMaxCacheSize(this@MusicService, sizeMb)
            }
        }
        // Both Live Update surfaces read the preference fresh when they build,
        // so this only has to nudge them when it flips: drop the progress chip
        // right away, and rebuild the media notification so promotion is
        // applied or dropped without waiting for the next player event.
        serviceScope.launch {
            themePreferences.livePlaybackUpdates.collect { enabled ->
                if (!enabled) musicProgressLiveUpdate?.hide()
                mediaLibrarySession?.let { session ->
                    runCatching { onUpdateNotification(session, false) }
                }
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

        /**
         * The end-of-track sleep timer firing. Media3 drops playWhenReady with
         * this exact reason when [ExoPlayer.setPauseAtEndOfMediaItems] stops the
         * player on an item boundary, so it is the one unambiguous signal that
         * the timer - rather than the user or audio focus - paused playback.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)
            if (!playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                sleepTimerEndOfTrack
            ) {
                clearSleepTimer()
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
                            warmStreamCache(item.mediaId, resolvedItem.localConfiguration?.uri)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Prefetch: Failed to resolve upcoming ${item.mediaId}")
                    }
                }
            }
        }
    }

    /**
     * Write the first [WARM_CACHE_BYTES] of an upcoming song's stream into
     * the disk cache, so the eventual transition or skip starts playing from
     * disk instead of waiting on the network. Only warms real network
     * streams: local files, already-cached songs, and the resolver's
     * sentinel URIs (placeholder / cached / error) are skipped.
     */
    private fun warmStreamCache(videoId: String, uri: Uri?) {
        val factory = cacheDataSourceFactory ?: return
        if (!isCacheEnabled || uri == null) return
        if (uri.scheme != "http" && uri.scheme != "https") return
        val url = uri.toString()
        if (url.startsWith(PLACEHOLDER_PREFIX) || url.startsWith(CACHED_PREFIX)) return
        if (!warmedIds.add(videoId)) return
        if (CacheManager.isFullyCached(videoId)) return

        resolveScope.launch {
            warmSemaphore.acquire()
            try {
                val dataSpec = DataSpec.Builder()
                    .setUri(uri)
                    .setPosition(0)
                    .setLength(WARM_CACHE_BYTES)
                    // Playback reads the cache under the song id (see
                    // buildMediaItemWithUri's setCustomCacheKey), so the warm
                    // must write under the same key.
                    .setKey(videoId)
                    .build()
                androidx.media3.datasource.cache.CacheWriter(
                    factory.createDataSource(), dataSpec, null, null
                ).cache()
                Log.d(TAG, "Warm: cached stream head for $videoId")
            } catch (e: Exception) {
                // Retryable on the next prefetch round (e.g. an expired URL
                // that resolution will refresh).
                warmedIds.remove(videoId)
                Log.w(TAG, "Warm: failed for $videoId: ${e.message}")
            } finally {
                warmSemaphore.release()
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

        // 3. Disk Cache (Fully Cached - Instant Playback). Skipped when the
        // cache setting is off: the data source then bypasses the cache, so a
        // CACHED_PREFIX URI would hit the network with a fake host and fail.
        if (isCacheEnabled && CacheManager.isFullyCached(videoId)) {
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

            val availableSessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CMD_SLEEP_TIMER_SET, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SLEEP_TIMER_CANCEL, Bundle.EMPTY))
                    .build()

            return MediaSession.ConnectionResult.accept(
                availableSessionCommands,
                availablePlayerCommands
            )
        }

        /**
         * A controller that connects mid-session has no idea a timer is
         * running - the UI is routinely destroyed and rebuilt underneath a
         * playing service - so hand it the current state on arrival.
         */
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onPostConnect(session, controller)
            publishSleepTimerState()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            CMD_SLEEP_TIMER_SET -> {
                startSleepTimer(args.getInt(ARG_SLEEP_TIMER_MINUTES, 0))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_SLEEP_TIMER_CANCEL -> {
                clearSleepTimer()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> super.onCustomCommand(session, controller, customCommand, args)
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

    // --- Sleep timer ---
    //
    // This lives in the service, not in PlayerViewModel where it used to. The
    // ViewModel is scoped to MainActivity, so its viewModelScope - and with it
    // the timer's delay() - was cancelled the moment the activity went away.
    // Backing out of the app while music kept playing (the whole point of a
    // foreground media service, and exactly what someone setting a sleep timer
    // does next) silently killed the timer, and playback ran all night. There
    // was no persistence either, so reopening the app showed no timer running
    // and gave the user no way to tell it had died.
    //
    // The player outlives the UI, so the thing that stops the player has to as
    // well. State is published back through the session's extras, which is how
    // every connected controller - the UI, and anything else - learns about it.

    private var sleepTimerJob: Job? = null

    /** Wall-clock ms when the timer fires, or 0 when no duration timer is set. */
    private var sleepTimerEndsAt: Long = 0L

    /** True while the player is set to stop when the current track finishes. */
    private var sleepTimerEndOfTrack: Boolean = false

    /**
     * Arm the sleep timer. [minutes] of 0 or less means "at the end of the
     * current track" instead of a duration.
     */
    private fun startSleepTimer(minutes: Int) {
        clearSleepTimer(publish = false)

        if (minutes <= 0) {
            // Media3 has exactly this behaviour built in, and it is more precise
            // than watching for the track to end ourselves: the player stops on
            // the item boundary rather than a callback or two later, and a
            // later play() still moves on to the next track normally.
            sleepTimerEndOfTrack = true
            player.pauseAtEndOfMediaItems = true
        } else {
            val durationMs = minutes * 60_000L
            sleepTimerEndsAt = System.currentTimeMillis() + durationMs
            val deadline = SystemClock.elapsedRealtime() + durationMs
            sleepTimerJob = serviceScope.launch {
                // Sliced against elapsedRealtime rather than one long delay:
                // coroutine delays on the main dispatcher are driven by
                // uptimeMillis, which stops counting while the device is in
                // deep sleep. A timer set and then paused would come due long
                // after the wall clock said it should.
                while (true) {
                    val remaining = deadline - SystemClock.elapsedRealtime()
                    if (remaining <= 0L) break
                    delay(remaining.coerceAtMost(SLEEP_TIMER_TICK_MS))
                }
                fadeOutAndPause()
                clearSleepTimer()
            }
        }
        publishSleepTimerState()
    }

    /**
     * Ease the volume down before pausing.
     *
     * A sleep timer that cuts the audio dead is worse than one that does not
     * fire: the silence is what wakes people. Runs on [fadeVolumeJob] so it and
     * the crossfade fade-in can never drive the volume at the same time.
     */
    private fun fadeOutAndPause() {
        fadeVolumeJob?.cancel()
        fadeVolumeJob = serviceScope.launch {
            val steps = 20
            for (i in steps - 1 downTo 0) {
                player.volume = i / steps.toFloat()
                delay(SLEEP_TIMER_FADE_MS / steps)
            }
            player.pause()
            // Back to full straight away, or pressing play would be silent.
            player.volume = 1f
        }
    }

    /** Disarm, whether it fired or the user cancelled it. */
    private fun clearSleepTimer(publish: Boolean = true) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndsAt = 0L
        if (sleepTimerEndOfTrack) {
            sleepTimerEndOfTrack = false
            // Leaving this set would silently pause at the end of every
            // subsequent track too.
            player.pauseAtEndOfMediaItems = false
        }
        if (publish) publishSleepTimerState()
    }

    /**
     * Push the timer state to every connected controller. Session extras are
     * the right channel: they survive the UI being destroyed and rebuilt, so a
     * player reopened ten minutes later still shows the running countdown.
     */
    private fun publishSleepTimerState() {
        val session = mediaLibrarySession ?: return
        runCatching {
            session.setSessionExtras(
                Bundle().apply {
                    putLong(EXTRA_SLEEP_TIMER_ENDS_AT, sleepTimerEndsAt)
                    putBoolean(EXTRA_SLEEP_TIMER_END_OF_TRACK, sleepTimerEndOfTrack)
                }
            )
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
                         // Fetch the cover once per URL, off the notification
                         // path: this tick posts without it and the next one
                         // picks it up from the cache. Same approach as
                         // DownloadService.
                         val artUrl = mediaItem?.mediaMetadata?.artworkUri?.toString()
                         if (artUrl != null && NotificationArtworkLoader.cached(artUrl) == null &&
                             liveUpdateArtworkRequested.add(artUrl)
                         ) {
                             serviceScope.launch {
                                 NotificationArtworkLoader.load(this@MusicService, artUrl)
                             }
                         }
                         musicProgressLiveUpdate?.updateProgress(
                             songTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                             artistName = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown",
                             currentPositionMs = position,
                             durationMs = duration,
                             isPlaying = true,
                             artwork = NotificationArtworkLoader.cached(artUrl)
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
