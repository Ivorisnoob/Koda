package com.ivor.ivormusic.service

import com.ivor.ivormusic.util.KLog

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
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
import com.ivor.ivormusic.data.AudioProfileStore
import com.ivor.ivormusic.data.AudioProfiler
import com.ivor.ivormusic.data.TrackLoudnessStore
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.widget.NowPlayingWidget
import com.ivor.ivormusic.ui.video.CastPlaybackKind
import com.ivor.ivormusic.ui.video.VideoCastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

    /**
     * Two engines alternating whole tracks, so a crossfade is a real overlap.
     * See [CrossfadeEngine]; the rest of this service talks to [player], which
     * is whichever of the two is currently audible.
     */
    private lateinit var engine: CrossfadeEngine
    private lateinit var audioFocus: AudioFocusController
    private lateinit var castManager: VideoCastManager
    private var castPlayer: CastPlayer? = null
    private var castStartJob: Job? = null
    private var castPrefetchJob: Job? = null
    private var castEndOfTrackJob: Job? = null
    private var castSourceRetryCount = 0

    /**
     * The audible engine. Everything outside the transition itself addresses
     * this, so the two-player split stays invisible to the queue, the session
     * callbacks, the sleep timer and Android Auto.
     */
    private val player: ExoPlayer get() = engine.active
    // Pinned at player creation and broadcast so external equalizer apps can
    // attach to Koda's playback
    private var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private lateinit var youtubeRepository: YouTubeRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var themePreferences: ThemePreferences
    private lateinit var audioProfileStore: AudioProfileStore
    private val likedSongsRepository by lazy { LikedSongsRepository(this) }
    private val statsRepository by lazy { StatsRepository(this) }
    private val songRepository by lazy { SongRepository(this) }
    private val transitionFilters = ConcurrentHashMap<ExoPlayer, TransitionFilterAudioProcessor>()

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
    private val profilingIds =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val prefetchingIds =
        java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // One warm at a time: warming must never contend with the current song's
    // own buffering for the whole prefetch window.
    private val warmSemaphore = kotlinx.coroutines.sync.Semaphore(1)
    private val profileSemaphore = kotlinx.coroutines.sync.Semaphore(1)
    
    // --- Configuration ---
    private var isCrossfadeEnabled = true
    private var isAutoMixEnabled = true
    private var crossfadeDurationMs = 3000L
    private var isNormalizeVolumeEnabled = true

    /**
     * The current track's loudness correction, as a linear volume scalar.
     *
     * **Every volume the service sets is this times a curve, never a bare
     * 1.0.** `player.volume` has one field and two jobs - the correction, which
     * holds for a whole track, and the fades, which move within it - so the
     * moment normalisation exists, "full volume" stops meaning 1.0 and starts
     * meaning this. A ramp that ends at 1.0 would undo the correction at the
     * exact moment the next track starts, which is the one moment it is for.
     */
    @Volatile private var trackGain = 1f
    // Read on the playback data-source hot path (every open()), so it's a
    // volatile field fed by the preference flow instead of a prefs read.
    @Volatile private var isCacheEnabled = true
    private var fadeVolumeJob: Job? = null
    private var progressJob: Job? = null
    private var transitionJob: Job? = null
    private var manualTransitionJob: Job? = null
    private var playbackShuffleEnabled = false
    private var playbackShuffleSeed = 0L
    private var playbackRepeatMode = Player.REPEAT_MODE_OFF
    private var lastShuffleOrderItemCount = -1

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

    // One background refresh per browse category at a time, so a client that
    // browses twice while the cache is stale does not stack two identical
    // network fetches.
    private val recommendationsRefreshing = AtomicBoolean(false)
    private val playlistsRefreshing = AtomicBoolean(false)

    // Browse search. Auto calls onSearch, then onGetSearchResult for pages of
    // the same query; both serve this one cached result set so the two cannot
    // disagree.
    @Volatile private var lastSearchQuery: String? = null
    @Volatile private var lastSearchResults: List<Song> = emptyList()

    companion object {
        private const val TAG = "MusicService"
        private const val PREFETCH_AHEAD_COUNT = 3
        // Covers the maintained NewPipe extraction and the direct InnerTube
        // fallback; their individual requests are also bounded by OkHttp.
        private const val RESOLVE_TIMEOUT_MS = 20_000L
        private const val PROFILE_TIMEOUT_MS = 30_000L
        private const val PLACEHOLDER_PREFIX = "https://placeholder.ivormusic/"
        private const val CACHED_PREFIX = "https://cached.ivormusic/"
        private const val ANDROID_AUTO_BROWSE_TIMEOUT_MS = 30_000L
        // Cap on the "Recently Played" browse node - a car screen does not
        // want thousands of history rows, just what you have been listening to.
        private const val RECENTLY_PLAYED_BROWSE_LIMIT = 50
        // android.media.browse.ContentStyle values: how Auto renders items.
        private const val LIST_ITEM = 1
        private const val GRID_ITEM = 2
        // Safety margin before a googlevideo URL's `expire` timestamp, and the
        // fallback lifetime when the URL carries no readable expire param.
        private const val URI_EXPIRY_SAFETY_MS = 5 * 60 * 1000L
        private const val URI_DEFAULT_TTL_MS = 4 * 60 * 60 * 1000L
        // Stream head pre-cached for upcoming songs: ~30s of opus audio, enough
        // to cover the 0.5s start buffer plus the first ranged chunk's RTT.
        private const val WARM_CACHE_BYTES = 512L * 1024
        // Tail warm for AutoMix: comfortably more than the profiler's
        // twenty-second outro window at any audio bitrate YouTube serves.
        private const val WARM_TAIL_BYTES = 1024L * 1024

        /**
         * The short ramp a manual skip and a non-overlapped advance get.
         * Long enough not to click, short enough that pressing next still
         * feels immediate.
         */
        private const val SKIP_FADE_MS = 300L

        /** Manual track changes overlap briefly without making Next feel slow. */
        private const val MANUAL_CROSSFADE_MS = 500L
        private const val MANUAL_RESOLVE_WAIT_MS = 1_500L
        private const val PREVIOUS_RESTART_MS = 3_000L
        private const val AUTO_MIX_FALLBACK_OVERLAP_MS = 3_000L
        private const val AUTO_MIX_MAX_OVERLAP_MS = 15_000L

        /**
         * How often the transition watcher checks whether the outgoing track
         * has entered its fade window. Fine enough to place the start of a
         * fade, which the one-second progress tick never was.
         */
        private const val TRANSITION_POLL_MS = 200L
        private const val TRANSITION_PREPARE_LEAD_MS = 1_500L

        // Re-resolution attempts before a song is skipped. A dead or expired URL
        // is fixed by a fresh extraction or not at all, so the general ceiling
        // stays low. A 403 on the direct InnerTube fallback is different: it is
        // a verdict on visitorData, so each retry re-rolls that identity. Four
        // attempts recover the large majority of those fallbacks (measured
        // August 2026) without applying that expensive recovery to NewPipe URLs.
        private const val MAX_RETRIES = 2
        private const val MAX_FORBIDDEN_RETRIES = 4

        // --- Sleep timer: the contract with PlayerViewModel ---

        /** Arm the timer. Carries [ARG_SLEEP_TIMER_MINUTES]; 0 = end of track. */
        const val CMD_SLEEP_TIMER_SET = "com.ivor.ivormusic.SLEEP_TIMER_SET"
        const val CMD_SLEEP_TIMER_CANCEL = "com.ivor.ivormusic.SLEEP_TIMER_CANCEL"
        const val ARG_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"

        const val CMD_SKIP_NEXT = "com.ivor.ivormusic.SKIP_NEXT"
        const val CMD_SKIP_PREVIOUS = "com.ivor.ivormusic.SKIP_PREVIOUS"
        const val CMD_SKIP_TO_INDEX = "com.ivor.ivormusic.SKIP_TO_INDEX"
        const val ARG_SKIP_INDEX = "skip_index"
        const val EXTRA_SONG_SOURCE = "com.ivor.ivormusic.SONG_SOURCE"
        const val EXTRA_CAST_RESOLVE_NOW = "com.ivor.ivormusic.CAST_RESOLVE_NOW"

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
        KLog.i(TAG, "MusicService Creating...")

        // 1. Initialize Dependencies
        themePreferences = ThemePreferences(this)
        isCacheEnabled = themePreferences.cacheEnabled.value
        // Initialize the cache directly at the persisted size instead of the
        // default; the size and toggle stay live via observePreferences().
        CacheManager.initialize(this, themePreferences.maxCacheSizeMb.value)
        youtubeRepository = YouTubeRepository(this)
        downloadRepository = DownloadRepository.getInstance(this)
        audioProfileStore = AudioProfileStore(this)

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
        restorePlaybackModes()
        restoreSleepTimer()

        // 5. Initialize Session
        initializeSession()

        // Cast is service-owned for the same reason local playback is: the
        // queue, notification, sleep timer and process lifetime must not depend
        // on whether the now-playing Compose screen happens to exist.
        initializeCast()

        // 6. Pre-warm caches
        preWarmAutoCache()

        // 7. Warm the visitorData token so the first stream resolution of a
        // session doesn't pay for the mint on its critical path. Music-first
        // sessions (and Android Auto) never construct VideoPlayerViewModel,
        // which was the only other place that prefetched it.
        resolveScope.launch { youtubeRepository.prefetchVisitorData() }
        resolveScope.launch { audioProfileStore.warm() }

        // 8. React to the user switching profile.
        observeProfileSwitches()
    }

    /**
     * Drop this service's account-derived state when the active profile changes.
     *
     * The service is a second process-level holder of everything an account
     * switch invalidates: its own YouTubeRepository, its own visitorData
     * prefetch, and - the part that is actually visible - a five-minute cache of
     * the account's recommendations and playlists that it serves to the media
     * browser. Left alone, Android Auto and any other browser client would list
     * one account's playlists while the app is signed into another.
     *
     * There is no DI, so the service watches the process-wide profile id the
     * same way the ViewModels do.
     */
    private fun observeProfileSwitches() {
        serviceScope.launch {
            com.ivor.ivormusic.data.ProfileManager(applicationContext)
                .activeProfileId
                .drop(1)
                .distinctUntilChanged()
                .collect {
                    cachedRecommendations = null
                    cachedPlaylists = null
                    cachedPlaylistSongs = mutableMapOf()
                    // Not just "expired": the timestamp gates all three caches
                    // above, and a switch has to invalidate them regardless of
                    // how recently they were filled.
                    lastBrowseCacheTime = 0L
                    youtubeRepository.clearSessionScopedInstanceCaches()

                    // Playback deliberately continues - the queue's streams are
                    // already resolved and killing someone's music because they
                    // checked another account would be a bad trade - but the
                    // browse tree has to be told it is stale, or a client that
                    // is already sitting on the old list never re-asks.
                    runCatching {
                        mediaLibrarySession?.let { session ->
                            session.connectedControllers.forEach { controller ->
                                session.notifyChildrenChanged(controller, "RECOMMENDED", 0, null)
                                session.notifyChildrenChanged(controller, "PLAYLISTS", 0, null)
                            }
                        }
                    }.onFailure { KLog.w(TAG, "notifyChildrenChanged after profile switch failed", it) }

                    resolveScope.launch { youtubeRepository.prefetchVisitorData() }
                }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // When the user swipes the app from recents, pause playback and stop the
        // service so the foreground notification is dismissed instead of getting
        // stuck (a foreground-service notification cannot be swiped away by the user).
        // pauseAllPlayersAndStopSelf() is the official Media3 helper for this.
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        KLog.i(TAG, "MusicService Destroying...")
        fadeVolumeJob?.cancel()
        progressJob?.cancel()
        transitionJob?.cancel()
        manualTransitionJob?.cancel()
        sleepTimerJob?.cancel()
        castStartJob?.cancel()
        castPrefetchJob?.cancel()
        castEndOfTrackJob?.cancel()
        if (::castManager.isInitialized) castManager.endObservation()
        castPlayer?.let { runCatching { it.release() } }
        castPlayer = null
        audioFocus.abandon()
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
            engine.release()
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
        // A LoadControl is stateful and may belong to only one playback
        // thread. Crossfade owns two ExoPlayers, so the factory must build one
        // per player rather than sharing a single instance between them.
        val buildLoadControl = {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    300_000, // Min buffer 5min (== max: continuous top-up)
                    300_000, // Max buffer 5min
                    500,     // Buffer for Playback: 0.5s (near-instant start)
                    3000     // Buffer for Rebuffer: 3s
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

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

        // Built twice, identically. `handleAudioFocus = false` on both is
        // load-bearing: two players each managing their own focus are two
        // clients, and the second one requesting makes the first receive
        // AUDIOFOCUS_LOSS and pause itself - which during a crossfade is the
        // outgoing track dying exactly when the incoming one starts. Focus is
        // owned by [audioFocus] instead.
        val buildPlayer: () -> ExoPlayer = {
            val transitionFilter = TransitionFilterAudioProcessor()
            val renderersFactory = object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink = DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(transitionFilter))
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(this).setDataSourceFactory(smartDataSourceFactory)
                )
                .setLoadControl(buildLoadControl())
                .setAudioAttributes(AudioAttributes.DEFAULT, false)
                .setHandleAudioBecomingNoisy(true)
                .build()
                .also { transitionFilters[it] = transitionFilter }
        }

        engine = CrossfadeEngine(
            scope = serviceScope,
            playerFactory = buildPlayer,
            onActiveChanged = { newActive -> onEngineSwapped(newActive) },
            gainFor = { p -> gainForPlayer(p) },
            setFilterSweep = { p, amount -> transitionFilters[p]?.setSweep(amount) },
        )

        audioFocus = AudioFocusController(
            context = this,
            onPause = { engine.active.pause() },
            onResume = { engine.active.play() },
            onDuck = { gain -> engine.duckGain = gain },
        )

        engine.setActiveListener(PlayerEventListener())

        // Pin a known audio session id and announce it to the system, so
        // external equalizer apps (Poweramp Equalizer, Wavelet, the OEM EQ)
        // can attach their effects to Koda's music playback. Generating the
        // id ourselves means it exists before the audio sink initializes on
        // first playback (ExoPlayer's own id stays UNSET until then).
        // Both engines take the same id, or the equalizer would drop out on
        // alternate tracks.
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val generatedSessionId = audioManager.generateAudioSessionId()
        if (generatedSessionId != android.media.AudioManager.ERROR) {
            audioSessionId = generatedSessionId
            engine.setAudioSessionId(generatedSessionId)
            val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, generatedSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            sendBroadcast(intent)
            KLog.i(TAG, "Announced audio session $generatedSessionId for external equalizers")
        }
    }

    private fun initializeSession() {
        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName).let {
            val intent = it ?: Intent(this, MainActivity::class.java)
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        mediaLibrarySession = MediaLibrarySession.Builder(this, engine.active, LibrarySessionCallback())
            .setSessionActivity(sessionIntent)
            .build()
    }

    // --- Chromecast -------------------------------------------------------

    private fun initializeCast() {
        castManager = VideoCastManager(this, CastPlaybackKind.MUSIC).apply {
            onRemoteFailed = { recoverCastCurrentItem() }
            onRemoteFinished = {
                // The receiver only reports FINISHED after the last queue item.
                // Keep the service state honest instead of treating IDLE as a
                // local ExoPlayer error.
                castPlayer?.pause()
            }
            onSessionLost = { positionMs ->
                val shouldPlay = castPlayer?.playWhenReady == true
                resumeLocalAfterCast(positionMs, shouldPlay)
            }
            beginObservation()
        }
        serviceScope.launch {
            castManager.isSessionActive.collect { active ->
                if (active && castPlayer == null) startMusicCast()
            }
        }
    }

    /**
     * Hand the audible queue to the receiver without making it resolve local
     * files, cache pseudo-URIs or Koda's placeholders.
     *
     * Only the current item blocks the hand-off. The next three are resolved in
     * the background and replaced in CastPlayer's queue before the receiver's
     * preloader reaches them, matching the local player's bounded prefetch
     * policy without turning a 300-song queue into 300 extraction calls.
     */
    private fun startMusicCast() {
        if (castStartJob?.isActive == true || castPlayer != null) return
        castStartJob = serviceScope.launch {
            val remoteAlreadyPlaying = castManager.currentMediaStatus()?.mediaInfo != null
            val remote = castManager.createPlayer() ?: run {
                castManager.endSession(stopOnReceiver = true)
                return@launch
            }
            attachCastPlayerListener(remote)
            castPlayer = remote

            // Snapshot the phone state before pausing it. Reading
            // playWhenReady after pause() would always make a newly connected
            // receiver start paused even when music was already playing.
            val local = player
            val wasPlaying = local.playWhenReady
            val originalItems = (0 until local.mediaItemCount).map(local::getMediaItemAt)
            val originalIndex = local.currentMediaItemIndex.coerceIn(
                0,
                originalItems.lastIndex.coerceAtLeast(0)
            )
            val positionMs = local.currentPosition.coerceAtLeast(0L)

            engine.cancelTransition()
            manualTransitionJob?.cancel()
            fadeVolumeJob?.cancel()
            player.pause()
            audioFocus.abandon()

            mediaLibrarySession?.let { session ->
                runCatching { session.setPlayer(remote) }
                    .onFailure { KLog.e(TAG, "Could not point music session at CastPlayer", it) }
            }

            // Process recreation while the TV is already playing: CastPlayer's
            // timeline tracker adopts the receiver queue. Reloading would jump
            // back to the phone's stale checkpoint and interrupt the room.
            if (remoteAlreadyPlaying) {
                castSourceRetryCount = 0
                // CastPlayer receives the existing status asynchronously after
                // construction; let its timeline tracker publish the queue
                // before asking which entries need prefetch.
                delay(300L)
                prefetchCastUpcoming()
                armCastEndOfTrackTimerIfNeeded()
                return@launch
            }

            val originalCurrent = originalItems.getOrNull(originalIndex)
            if (originalCurrent == null || !isCastableMusicItem(originalCurrent)) {
                // A receiver cannot read MediaStore/content URIs. Do not silently
                // skip the song someone is listening to just to make the icon
                // turn blue; leave local playback exactly where it was.
                castPlayer = null
                runCatching { remote.release() }
                mediaLibrarySession?.let { runCatching { it.setPlayer(local) } }
                castManager.endSession(stopOnReceiver = true)
                if (wasPlaying) local.play()
                return@launch
            }

            val castable = originalItems.withIndex()
                .filter { isCastableMusicItem(it.value) }
            val castIndex = castable.indexOfFirst { it.index == originalIndex }
                .coerceAtLeast(0)
            val currentResolved = resolveCastMusicItem(castable[castIndex].value)
            if (currentResolved == null) {
                castPlayer = null
                runCatching { remote.release() }
                mediaLibrarySession?.let { runCatching { it.setPlayer(local) } }
                castManager.endSession(stopOnReceiver = true)
                if (wasPlaying) local.play()
                return@launch
            }

            val receiverQueue = castable.mapIndexed { index, indexed ->
                if (index == castIndex) currentResolved
                else castPlaceholder(indexed.value)
            }
            remote.repeatMode = playbackRepeatMode
            remote.shuffleModeEnabled = playbackShuffleEnabled
            remote.setMediaItems(receiverQueue, castIndex, positionMs)
            remote.prepare()
            if (wasPlaying) remote.play() else remote.pause()
            castSourceRetryCount = 0
            prefetchCastUpcoming()
            armCastEndOfTrackTimerIfNeeded()
        }
    }

    private fun attachCastPlayerListener(remote: CastPlayer) {
        remote.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                prefetchCastUpcoming()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                playbackShuffleEnabled = shuffleModeEnabled
                themePreferences.setPlaybackShuffle(shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackRepeatMode = repeatMode
                themePreferences.setPlaybackRepeatMode(repeatMode)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                castSourceRetryCount = 0
                prefetchCastUpcoming()
                if (sleepTimerEndOfTrack) {
                    // The progress guard normally pauses before the boundary;
                    // this is the race backstop for a receiver that advanced
                    // between polls.
                    remote.pause()
                    remote.seekTo(0L)
                    clearSleepTimer()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) castSourceRetryCount = 0
            }
        })
    }

    private fun isCastableMusicItem(item: MediaItem): Boolean =
        item.mediaMetadata.extras?.getString(EXTRA_SONG_SOURCE) !=
            com.ivor.ivormusic.data.SongSource.LOCAL.name

    private fun castPlaceholder(item: MediaItem): MediaItem = item.buildUpon()
        .setUri("$PLACEHOLDER_PREFIX${item.mediaId}")
        // The converter requires a MIME type for every queue entry, including
        // future entries which are replaced before the receiver opens them.
        .setMimeType(MimeTypes.AUDIO_MP4)
        .build()

    /** Resolve AAC/M4A specifically: broad receiver support and a truthful MIME. */
    private suspend fun resolveCastMusicItem(item: MediaItem): MediaItem? {
        val url = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            youtubeRepository.getDownloadAudioStreamUrl(item.mediaId).getOrNull()
        }?.takeIf { it.isNotBlank() } ?: return null
        return item.buildUpon()
            .setUri(url)
            .setMimeType(MimeTypes.AUDIO_MP4)
            .setCustomCacheKey(null)
            .build()
    }

    private fun prefetchCastUpcoming() {
        if (castPrefetchJob?.isActive == true) return
        castPrefetchJob = serviceScope.launch {
            val remote = castPlayer ?: return@launch
            val seen = mutableSetOf<Int>()
            var index = remote.currentMediaItemIndex
            repeat(PREFETCH_AHEAD_COUNT) {
                index = remote.currentTimeline.getNextWindowIndex(
                    index,
                    remote.repeatMode,
                    remote.shuffleModeEnabled
                )
                if (index == C.INDEX_UNSET || !seen.add(index) || index >= remote.mediaItemCount) {
                    return@launch
                }
                val original = remote.getMediaItemAt(index)
                if (!isPlaceholder(original.localConfiguration?.uri)) return@repeat
                val resolved = resolveCastMusicItem(original) ?: return@repeat
                if (castPlayer === remote && index < remote.mediaItemCount &&
                    remote.getMediaItemAt(index).mediaId == original.mediaId
                ) {
                    remote.replaceMediaItem(index, resolved)
                }
            }
        }
    }

    private fun recoverCastCurrentItem() {
        val remote = castPlayer ?: return
        val index = remote.currentMediaItemIndex
        val original = remote.currentMediaItem ?: return
        serviceScope.launch {
            if (castSourceRetryCount < 1) {
                castSourceRetryCount++
                youtubeRepository.refreshVisitorDataAfterPlaybackFailure()
                val resolved = resolveCastMusicItem(castPlaceholder(original))
                if (resolved != null && castPlayer === remote && index < remote.mediaItemCount) {
                    val position = remote.currentPosition.coerceAtLeast(0L)
                    remote.replaceMediaItem(index, resolved)
                    remote.seekTo(index, position)
                    remote.play()
                    return@launch
                }
            }
            requestCastTransition(remote.getNextMediaItemIndex())
        }
    }

    private fun requestCastTransition(targetIndex: Int) {
        val remote = castPlayer ?: return
        if (targetIndex !in 0 until remote.mediaItemCount) return
        serviceScope.launch {
            val original = remote.getMediaItemAt(targetIndex)
            val target = if (isPlaceholder(original.localConfiguration?.uri)) {
                resolveCastMusicItem(original)
            } else original
            if (target == null || castPlayer !== remote || targetIndex >= remote.mediaItemCount) {
                return@launch
            }
            if (target !== original) remote.replaceMediaItem(targetIndex, target)
            remote.seekTo(targetIndex, 0L)
            remote.play()
            prefetchCastUpcoming()
        }
    }

    private fun resumeLocalAfterCast(positionMs: Long, playWhenReady: Boolean) {
        serviceScope.launch {
            val remote = castPlayer ?: return@launch
            val remoteItems = (0 until remote.mediaItemCount).map(remote::getMediaItemAt)
            val remoteIndex = remote.currentMediaItemIndex
                .coerceIn(0, remoteItems.lastIndex.coerceAtLeast(0))

            castPrefetchJob?.cancel()
            castEndOfTrackJob?.cancel()
            castPlayer = null
            runCatching { remote.release() }

            val local = player
            engine.cancelTransition()
            if (remoteItems.isNotEmpty()) {
                // Re-resolve under the phone's client/quality policy. Receiver
                // URLs were selected as AAC and may be UA-bound or near expiry.
                val placeholders = remoteItems.map(::castPlaceholder)
                local.setMediaItems(placeholders, remoteIndex, positionMs.coerceAtLeast(0L))
                local.prepare()
            }
            engine.setShuffleState(playbackShuffleEnabled, playbackShuffleSeed)
            engine.setRepeatMode(playbackRepeatMode)
            mediaLibrarySession?.let { runCatching { it.setPlayer(local) } }
            if (playWhenReady) {
                audioFocus.request()
                local.play()
            } else {
                local.pause()
            }
            prefetchUpcomingSongs()
        }
    }

    private fun armCastEndOfTrackTimerIfNeeded() {
        castEndOfTrackJob?.cancel()
        if (!sleepTimerEndOfTrack || castPlayer == null) return
        castEndOfTrackJob = serviceScope.launch {
            while (isActive && sleepTimerEndOfTrack) {
                val remote = castPlayer ?: return@launch
                val duration = remote.duration
                if (duration > 0 && duration - remote.currentPosition <= 500L) {
                    remote.pause()
                    clearSleepTimer()
                    return@launch
                }
                delay(200L)
            }
        }
    }

    private fun restorePlaybackModes() {
        playbackShuffleEnabled = themePreferences.isPlaybackShuffleEnabled()
        playbackRepeatMode = themePreferences.getPlaybackRepeatMode()
        playbackShuffleSeed = themePreferences.getPlaybackShuffleSeed().takeIf { it != 0L }
            ?: kotlin.random.Random.nextLong().also(themePreferences::setPlaybackShuffleSeed)
        engine.setShuffleState(playbackShuffleEnabled, playbackShuffleSeed)
        engine.setRepeatMode(playbackRepeatMode)
    }

    private fun observePreferences() {
        // These flows update live across ThemePreferences instances (the
        // settings screen writes through its own instance) thanks to the
        // SharedPreferences change listener inside ThemePreferences.
        serviceScope.launch {
            themePreferences.crossfadeEnabled.collect { enabled ->
                isCrossfadeEnabled = enabled
                if (!enabled && ::engine.isInitialized) {
                    manualTransitionJob?.cancel()
                    fadeVolumeJob?.cancel()
                    engine.cancelTransition()
                    engine.applyIdleVolumes()
                }
            }
        }
        serviceScope.launch { themePreferences.crossfadeAuto.collect { isAutoMixEnabled = it } }
        serviceScope.launch { themePreferences.crossfadeDurationMs.collect { crossfadeDurationMs = it.toLong() } }
        serviceScope.launch { themePreferences.cacheEnabled.collect { isCacheEnabled = it } }
        serviceScope.launch {
            themePreferences.normalizeVolume.collect { enabled ->
                isNormalizeVolumeEnabled = enabled
                // Apply to what is already playing rather than waiting for the
                // next track: the setting is judged by ear, and a toggle that
                // does nothing until the song changes reads as broken.
                refreshTrackGain(applyNow = true)
            }
        }
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

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            val itemCount = player.mediaItemCount
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED &&
                playbackShuffleEnabled &&
                itemCount != lastShuffleOrderItemCount
            ) {
                // Set before applying: setShuffleOrder itself publishes a
                // timeline change with the same count.
                lastShuffleOrderItemCount = itemCount
                engine.refreshActiveShuffleOrder()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled && !playbackShuffleEnabled) {
                playbackShuffleSeed = kotlin.random.Random.nextLong()
                themePreferences.setPlaybackShuffleSeed(playbackShuffleSeed)
            }
            playbackShuffleEnabled = shuffleModeEnabled
            lastShuffleOrderItemCount = player.mediaItemCount
            themePreferences.setPlaybackShuffle(shuffleModeEnabled)
            engine.setShuffleState(shuffleModeEnabled, playbackShuffleSeed)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            playbackRepeatMode = repeatMode
            themePreferences.setPlaybackRepeatMode(repeatMode)
            engine.setRepeatMode(repeatMode)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)

            // 1. Loudness correction for the new track, before anything sets a
            // volume. It may still be unknown here - an unresolved song has not
            // called /player yet - so STATE_READY refreshes it again once the
            // real URI is in place.
            refreshTrackGain(applyNow = false)

            // 1b. An automatic advance while a fade is running means the
            // outgoing track ended before the overlap finished - the guard at
            // the end of the fade window lost a race with a stall or a short
            // file. Drop the overlap rather than swap onto a player the queue
            // has already moved past, which would play the same track twice.
            if (engine.isFading && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                KLog.w(TAG, "Advance beat the crossfade; dropping the overlap")
                engine.cancelTransition()
            }

            // 2. Volume for the new track. An automatic advance that reaches
            // here is one the engine did *not* overlap - crossfade off, an
            // unresolved next item or repeat-one. Off means literally off: no
            // overlap and no fade-in on either automatic or manual changes.
            if (!isCrossfadeEnabled || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                player.volume = trackGain * engine.duckGain
            } else {
                performSkipFadeIn()
            }

            // 2. Critical: Check validity of CURRENT item
            if (mediaItem != null) {
                validateAndPlayCurrentItem(mediaItem)
            }

            // 3. Robust Prefetching of FUTURE items
            prefetchUpcomingSongs()

            // 4. The home screen widget shows what changed
            NowPlayingWidget.push(this@MusicService)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            // Start prefetching as soon as we are ready
            if (playbackState == Player.STATE_READY) {
                // The current song plays — give it back its full retry budget
                // so one bad stretch (expired URL, network blip) months of
                // uptime ago can't permanently blacklist it.
                player.currentMediaItem?.mediaId?.let { retryCounts.remove(it) }
                // Resolution happens after the transition, so this is the first
                // point at which a first-play song's loudness is known. Applied
                // only when no fade is running, so a crossfade-in keeps the
                // volume it is ramping.
                refreshTrackGain(applyNow = true)
                player.currentMediaItem?.let { maybeProfile(it, player.duration) }
                prefetchUpcomingSongs()
            }

            // Android 16 Live Update: dismiss when playback ends or returns to idle so
            // the progress chip never lingers on the lock screen / shade after the
            // queue finishes or the service is paused.
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                progressJob?.cancel()
                musicProgressLiveUpdate?.hide()
                NowPlayingWidget.push(this@MusicService)
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
                // Focus is the service's, not the players' - see
                // AudioFocusController. Requested here rather than on the play
                // command so it covers every route into playback, including the
                // session callbacks and Android Auto.
                audioFocus.request()
                monitorProgress()
                monitorTransitions()
            } else {
                progressJob?.cancel()
                progressJob = null
                // A pause mid-overlap would leave the standby running under a
                // stopped session. Drop the transition and keep the track that
                // is actually on screen.
                engine.cancelTransition()
                transitionJob?.cancel()
                transitionJob = null
                musicProgressLiveUpdate?.hide()
                NowPlayingWidget.push(this@MusicService)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            KLog.e(TAG, "Player Error: ${error.errorCodeName}", error)
            handlePlayerError(error)
        }
    }

    // --- Logic 1: Validation & Playback execution ---

    private fun validateAndPlayCurrentItem(mediaItem: MediaItem) {
        val uri = mediaItem.localConfiguration?.uri
        val videoId = mediaItem.mediaId

        if (isPlaceholder(uri)) {
            KLog.w(TAG, "Validation: Hit placeholder for $videoId. Resolving...")

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
                        // Replacing the restored placeholder can reset the
                        // current item to zero even though the controller and
                        // mini player already adopted the saved position.
                        val resumePosition = player.currentPosition.coerceAtLeast(0L)
                        KLog.i(TAG, "Validation: Applied resolved item for $videoId (playWhenReady=$playWhenReady)")
                        val index = player.currentMediaItemIndex
                        player.replaceMediaItem(index, resolvedItem)
                        player.prepare()
                        if (resumePosition > 0L) {
                            player.seekTo(index, resumePosition)
                        }
                        player.playWhenReady = playWhenReady
                    }
                } catch (e: Exception) {
                    KLog.e(TAG, "Validation: Resolution failed for $videoId", e)
                }
            }
        } else {
            KLog.d(TAG, "Validation: Playing valid URI for $videoId")
        }
    }

    // --- Logic 2: Robust Prefetching ---

    private fun prefetchUpcomingSongs() {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        // The first entry must be the player's real next item. In shuffle mode
        // that is usually not currentIndex + 1, and resolving only sequential
        // indices leaves the actual successor as a placeholder until the
        // outgoing song has already ended.
        val targetIndices = linkedSetOf<Int>()
        player.getNextMediaItemIndex()
            .takeIf { it != C.INDEX_UNSET }
            ?.let(targetIndices::add)
        for (i in 1..PREFETCH_AHEAD_COUNT) {
            val targetIndex = currentIndex + i
            if (targetIndex >= player.mediaItemCount) break
            targetIndices.add(targetIndex)
        }

        targetIndices.take(PREFETCH_AHEAD_COUNT).forEachIndexed { offset, targetIndex ->
            val item = player.getMediaItemAt(targetIndex)
            val uri = item.localConfiguration?.uri

            if (isPlaceholder(uri)) {
                if (!prefetchingIds.add(item.mediaId)) return@forEachIndexed
                serviceScope.launch {
                    try {
                        val deferred = getOrStartResolution(item)
                        val resolvedItem = deferred.await()
                        
                        // Update player if item is still there
                        if (targetIndex < player.mediaItemCount &&
                            player.getMediaItemAt(targetIndex).mediaId == item.mediaId) {
                            KLog.d(
                                TAG,
                                "Prefetch: Updated ${if (offset == 0) "actual next" else "+${offset + 1}"} " +
                                    "(${item.mediaId})"
                            )
                            player.replaceMediaItem(targetIndex, resolvedItem)
                            warmStreamCache(item.mediaId, resolvedItem.localConfiguration?.uri)
                            maybeProfile(resolvedItem)
                        }
                    } catch (e: Exception) {
                        KLog.w(TAG, "Prefetch: Failed to resolve upcoming ${item.mediaId}")
                    } finally {
                        prefetchingIds.remove(item.mediaId)
                    }
                }
            }
        }
    }

    /**
     * Write the first [WARM_CACHE_BYTES] - and, unmetered, the last
     * [WARM_TAIL_BYTES] - of an upcoming song's stream into the disk cache,
     * so the eventual transition or skip starts playing from disk instead of
     * waiting on the network, and AutoMix can read the track's outro without
     * a network crawl. Only warms real network streams: local files,
     * already-cached songs, and the resolver's sentinel URIs (placeholder /
     * cached / error) are skipped.
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
                KLog.d(TAG, "Warm: cached stream head for $videoId")

                // The tail matters as much as the head, for a different
                // reason: AutoMix reads the last twenty seconds to decide how
                // to transition out of this track, and without these bytes
                // every read crawls over the network and profiling loses its
                // race with the song itself - which is why every transition
                // used to degrade to FALLBACK. googlevideo carries the full
                // size in `clen`; without it there is no honest end to seek
                // from. Metered networks skip it, matching the profiler's own
                // guard, so warming never spends data AutoMix would refuse to
                // use anyway.
                val totalBytes = uri.getQueryParameter("clen")?.toLongOrNull() ?: 0L
                val canWarmTail = totalBytes > WARM_CACHE_BYTES + WARM_TAIL_BYTES &&
                    !CacheManager.isFullyCached(videoId) &&
                    !ThemePreferences.isNetworkMetered(this@MusicService)
                if (canWarmTail) {
                    val tailSpec = DataSpec.Builder()
                        .setUri(uri)
                        .setPosition(totalBytes - WARM_TAIL_BYTES)
                        .setLength(WARM_TAIL_BYTES)
                        .setKey(videoId)
                        .build()
                    androidx.media3.datasource.cache.CacheWriter(
                        factory.createDataSource(), tailSpec, null, null
                    ).cache()
                    KLog.d(TAG, "Warm: cached stream tail for $videoId")
                }
            } catch (e: Exception) {
                // Retryable on the next prefetch round (e.g. an expired URL
                // that resolution will refresh).
                warmedIds.remove(videoId)
                KLog.w(TAG, "Warm: failed for $videoId: ${e.message}")
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
        KLog.d(TAG, "Resolution: Starting for $videoId")
        
        // 1. Downloads
        val downloaded = downloadRepository.downloadedSongs.value.find { it.id == videoId }
        if (downloaded != null && downloaded.uri != null) {
            KLog.d(TAG, "Resolution: Found download for $videoId")
            return buildMediaItemWithUri(originalItem, downloaded.uri, downloaded.duration)
        }

        // 2. Cache (Memory) — only while the underlying googlevideo URL is
        // still valid; expired entries are re-resolved instead of replayed.
        uriCache[videoId]?.let { cached ->
            if (cached.expiresAtMs > System.currentTimeMillis()) {
                KLog.d(TAG, "Resolution: Found cached URI for $videoId")
                return buildMediaItemWithUri(originalItem, Uri.parse(cached.uri))
            }
            KLog.d(TAG, "Resolution: Cached URI expired for $videoId, re-resolving")
            uriCache.remove(videoId)
        }

        // 3. Disk Cache (Fully Cached - Instant Playback). Skipped when the
        // cache setting is off: the data source then bypasses the cache, so a
        // CACHED_PREFIX URI would hit the network with a fake host and fail.
        if (isCacheEnabled && CacheManager.isFullyCached(videoId)) {
            KLog.d(TAG, "Resolution: Found full disk cache for $videoId. Enabling instant playback.")
            return buildMediaItemWithUri(originalItem, Uri.parse("$CACHED_PREFIX$videoId"))
        }

        // 4. Network with Retry
        // YouTubeRepository owns the NewPipe-first client fallback. This layer
        // bounds the whole resolution and handles playback-time re-resolution.
        return try {
            val result = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                youtubeRepository.getStreamUrl(videoId)
            }
            
            val streamUrl = result?.getOrNull()
            if (!streamUrl.isNullOrEmpty()) {
                uriCache[videoId] = CachedUri(streamUrl, streamUrlExpiryMs(streamUrl))
                KLog.d(TAG, "Resolution: Network success for $videoId")
                buildMediaItemWithUri(originalItem, Uri.parse(streamUrl))
            } else {
                KLog.e(TAG, "Resolution: Failed or Timed Out for $videoId")
                // Return an item with a special error URI instead of the placeholder
                // This breaks the loop because isPlaceholder() will be false.
                buildMediaItemWithUri(originalItem, Uri.parse("error://resolution_failed/$videoId"))
            }
        } catch (e: Exception) {
            KLog.e(TAG, "Resolution: Exception for $videoId", e)
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
        
        // Stream URLs carry short-lived signatures and local URIs may expose
        // device paths. The media id is sufficient for a user-shared report.
        KLog.w(TAG, "Handling playback error for $videoId")

        // Local songs (content:// or file://) — errors are typically unrecoverable
        // (file deleted, permission revoked, corrupt file). Don't try YouTube resolution.
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            KLog.e(TAG, "Error: Local song $videoId failed. Skipping (not retryable via YouTube).")
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
            KLog.d(TAG, "Error: Already resolving $videoId. Ignoring error.")
            player.playWhenReady = true
            return
        }

        // 2. Retry Logic (YouTube songs only)
        val retryCount = retryCounts[videoId] ?: 0
        // Only direct InnerTube streams are tied to Koda's visitorData. The
        // NewPipe-first path uses maintained Android/visionOS clients; a 403
        // there needs a fresh extraction, not an unrelated identity remint.
        val issuingClient = try {
            uri?.getQueryParameter("c")?.uppercase()
        } catch (_: Exception) {
            null
        }
        val isVisitorDataForbidden = httpResponseCode(error) == 403 &&
            (issuingClient == "ANDROID_VR" || issuingClient == "IOS")
        val maxRetries = if (isVisitorDataForbidden) MAX_FORBIDDEN_RETRIES else MAX_RETRIES

        if (retryCount < maxRetries) {
            KLog.w(TAG, "Error: Retrying ($retryCount/$maxRetries) for $videoId...")
            retryCounts[videoId] = retryCount + 1
            uriCache.remove(videoId) // Clear bad cache

            serviceScope.launch {
                delay(1000)
                // Mint a fresh visitorData before re-resolving. /player answered
                // 200 and never sees this refusal, so without it the flagged
                // token stays in prefs and is replayed for its whole 6h TTL -
                // every uncached song failing until the user clears app data.
                // Mirrors VideoPlayerViewModel.recoverFromSourceError.
                if (isVisitorDataForbidden) {
                    youtubeRepository.refreshVisitorDataAfterPlaybackFailure()
                    // Everything prefetchUpcomingSongs resolved was signed with
                    // the token just discarded, so the rest of the queue is
                    // already dead. Dropping it here turns one recovery into a
                    // recovery for the whole queue, instead of the same stall
                    // repeating on every following song.
                    uriCache.clear()
                    retryCounts.clear()
                    retryCounts[videoId] = retryCount + 1
                    resetUpcomingItemsToPlaceholders()
                }
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
            KLog.e(TAG, "Error: Max retries exhausted for $videoId. Skipping.")
            if (player.hasNextMediaItem()) {
                player.seekToNext()
                player.play()
            } else {
                player.stop()
            }
        }
    }

    /**
     * Put every already-resolved YouTube item in the queue back to its
     * placeholder URI, so the normal prefetch path resolves it again.
     *
     * [prefetchUpcomingSongs] only acts on placeholders, so an item it has
     * already replaced would keep its stream URL forever. After a visitorData
     * remint those URLs are all signed with the discarded token, and leaving
     * them in place means the 403, the retry and the stall repeat once per
     * song for the rest of the queue.
     *
     * The playing item is left alone: its own re-resolution is already in
     * flight, and replacing it here would fight that. Local songs are left
     * alone because their content:// / file:// URIs never came from YouTube.
     *
     * Must run on the application thread; callers are on [serviceScope].
     */
    private fun resetUpcomingItemsToPlaceholders() {
        val playingIndex = player.currentMediaItemIndex
        var reset = 0
        for (index in 0 until player.mediaItemCount) {
            if (index == playingIndex) continue
            val item = player.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: continue
            if (uri.scheme != "http" && uri.scheme != "https") continue
            if (uri.toString().startsWith(PLACEHOLDER_PREFIX)) continue
            if (uri.toString().startsWith(CACHED_PREFIX)) continue
            player.replaceMediaItem(
                index,
                item.buildUpon().setUri("$PLACEHOLDER_PREFIX${item.mediaId}").build(),
            )
            reset++
        }
        // Warming is one-shot per id, so an id warmed under the old token would
        // never be re-warmed. Forget them and let the fresh prefetch warm again.
        warmedIds.clear()
        if (reset > 0) KLog.d(TAG, "Recovery: reset $reset queued item(s) for re-resolution")
    }

    /**
     * The HTTP status behind a playback failure, or null when the error did not
     * come from an HTTP response. googlevideo's refusals surface as an
     * [HttpDataSource.InvalidResponseCodeException] nested some way down the
     * cause chain, never as the top-level exception.
     */
    private fun httpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
            cause = cause.cause
        }
        return null
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
                    .add(SessionCommand(CMD_SKIP_NEXT, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SKIP_PREVIOUS, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SKIP_TO_INDEX, Bundle.EMPTY))
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
            CMD_SKIP_NEXT -> {
                requestManualSkip(forward = true)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_SKIP_PREVIOUS -> {
                requestManualSkip(forward = false)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_SKIP_TO_INDEX -> {
                requestManualTransition(args.getInt(ARG_SKIP_INDEX, C.INDEX_UNSET))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }

        /** Route Bluetooth and Android Auto skips through the same short overlap. */
        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int,
        ): Int = when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                requestManualSkip(forward = true)
                SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                requestManualSkip(forward = false)
                SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            else -> super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            // A voice request ("Hey Google, play X on Koda") arrives as an
            // item carrying only a search query - no media id, no uri. Those
            // need one network round trip to become playable, so the whole
            // batch resolves asynchronously in that case and stays synchronous
            // otherwise.
            val casting = castPlayer != null
            val needsSearchResolution = mediaItems.any {
                it.mediaId.isBlank() && !it.requestMetadata.searchQuery.isNullOrBlank()
            }
            if (!needsSearchResolution) {
                val prepared = mediaItems.mapNotNullTo(mutableListOf()) { item ->
                    if (casting && isDeviceLocalSong(item)) null
                    else preparePlaybackItem(item, casting)
                }
                return finishIncomingCastItems(prepared, casting)
            }

            return serviceScope.future(Dispatchers.IO) {
                val resolved = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    val query = item.requestMetadata.searchQuery?.trim()?.takeIf { it.isNotEmpty() }
                    if (item.mediaId.isNotBlank() || query == null) {
                        if (!casting || !isDeviceLocalSong(item)) {
                            resolved.add(preparePlaybackItem(item, casting))
                        }
                        continue
                    }
                    // Take the best song result for the query. A miss is
                    // dropped rather than invented: handing the player an
                    // empty id would surface as an unexplained playback error.
                    val match = try {
                        youtubeRepository.search(query).firstOrNull()
                    } catch (e: Exception) {
                        KLog.e(TAG, "Voice resolution failed for '$query'", e)
                        null
                    }
                    if (match == null) {
                        KLog.w(TAG, "Voice request '$query' matched nothing")
                        continue
                    }
                    resolved.add(preparePlaybackItem(match.toPlaceholderMediaItem(), casting))
                }
                if (resolved.isEmpty()) {
                    throw IllegalStateException("No results for any requested media item")
                }
                resolveIncomingCastItems(resolved, casting)
            }
        }

        /**
         * Give every incoming item a concrete shape the playback pipeline can
         * carry: local URIs preserved, YouTube ids turned into placeholders
         * that [performResolution] swaps for a real stream.
         */
        private fun preparePlaybackItem(item: MediaItem, casting: Boolean): MediaItem {
            val existingUri = item.localConfiguration?.uri

            // Local songs come with either content:// (MediaStore) or file://
            // (downloaded) URIs that ExoPlayer can play directly — we must NOT
            // overwrite them with a placeholder.
            val isLocalUri = existingUri != null
                && !existingUri.toString().startsWith(PLACEHOLDER_PREFIX)
                && (existingUri.scheme == "content" || existingUri.scheme == "file")

            // Check if we have metadata in our browse cache to enrich the item immediately
            var meta = item.mediaMetadata
            if (meta.title == null) {
                val cached = findSongInCache(item.mediaId)
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

            return if (isLocalUri) {
                // Local song: preserve the original content:// URI for direct playback
                KLog.d(TAG, "onAddMediaItems: Preserving local URI for ${item.mediaId}: $existingUri")
                MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri(existingUri)
                    .setMediaMetadata(meta)
                    .build()
            } else {
                // YouTube song: use placeholder — resolution will happen via prefetch system
                MediaItem.Builder()
                    .setMediaId(item.mediaId)
                    .setUri("$PLACEHOLDER_PREFIX${item.mediaId}")
                    .setMediaMetadata(meta)
                    .apply { if (casting) setMimeType(MimeTypes.AUDIO_MP4) }
                    .build()
            }
        }

        private fun isDeviceLocalSong(item: MediaItem): Boolean =
            item.mediaMetadata.extras?.getString(EXTRA_SONG_SOURCE) == SongSource.LOCAL.name

        private fun finishIncomingCastItems(
            items: MutableList<MediaItem>,
            casting: Boolean,
        ): ListenableFuture<MutableList<MediaItem>> = if (casting) {
            serviceScope.future { resolveIncomingCastItems(items, casting = true) }
        } else {
            Futures.immediateFuture(items)
        }

        private suspend fun resolveIncomingCastItems(
            items: MutableList<MediaItem>,
            casting: Boolean,
        ): MutableList<MediaItem> {
            if (!casting) return items
            val singleItemRequest = items.size == 1
            return items.map { item ->
                val resolveNow = singleItemRequest ||
                    item.mediaMetadata.extras?.getBoolean(EXTRA_CAST_RESOLVE_NOW, false) == true
                if (resolveNow && isPlaceholder(item.localConfiguration?.uri)) {
                    resolveCastMusicItem(item) ?: item
                } else {
                    item
                }
            }.toMutableList()
        }
        
        // --- Browsing Logic (Android Auto / Media Browser) ---

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(buildRootItem(), MediaLibraryService.LibraryParams.Builder().setExtras(contentStyleExtras(GRID_ITEM, LIST_ITEM)).build())
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Clients use this to restore state or verify a single item after
            // reconnecting. The default answer is an error, which some
            // browsers treat as "this app's tree is gone" - so resolve real
            // answers from the same stores the browse tree serves.
            if (mediaId == "root") {
                return Futures.immediateFuture(LibraryResult.ofItem(buildRootItem(), null))
            }
            return serviceScope.future(Dispatchers.IO) {
                getRootItems().firstOrNull { it.mediaId == mediaId }?.let {
                    return@future LibraryResult.ofItem(it, null)
                }
                if (mediaId.startsWith("PLAYLIST_")) {
                    val playlistId = mediaId.removePrefix("PLAYLIST_")
                    val playlist = (cachedPlaylists ?: youtubeRepository.getUserPlaylists()
                        .also { if (it.isNotEmpty()) cachedPlaylists = it })
                        .firstOrNull { it.url.substringAfter("list=") == playlistId }
                    if (playlist != null) {
                        return@future LibraryResult.ofItem(
                            playlistEntry(playlist), null
                        )
                    }
                }
                findSongForBrowseId(mediaId)?.let { song ->
                    return@future LibraryResult.ofItem(mapSongToMediaItem(song), null)
                }
                LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
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
            if (parentId == "root") {
                return Futures.immediateFuture(LibraryResult.ofItemList(getRootItems(), null))
            }

            // Async fetch for content
            return serviceScope.future(Dispatchers.IO) {
                val items = fetchChildrenForId(parentId)
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }
        }

        /**
         * Search from a media browser's search box (Android Auto) and any
         * voice query routed through the library. Serves songs only: the
         * browse tree is playable content, and Auto renders results as a
         * flat list to play.
         */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                return Futures.immediateFuture(LibraryResult.ofVoid())
            }
            return serviceScope.future(Dispatchers.IO) {
                try {
                    val songs = youtubeRepository.search(trimmed)
                    lastSearchQuery = trimmed
                    lastSearchResults = songs
                    // Media3's search contract: onSearch only reports success;
                    // the results themselves are served by onGetSearchResult.
                    LibraryResult.ofVoid()
                } catch (e: Exception) {
                    KLog.e(TAG, "Browse search failed for '$trimmed'", e)
                    LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
                }
            }
        }

        /**
         * Pages of a previous search result. Normally [onSearch] has just
         * run for this exact query, so this only slices the cached list; a
         * client that asks for pages without searching first gets one fetch
         * rather than an error.
         */
        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val trimmed = query.trim()
            return serviceScope.future(Dispatchers.IO) {
                if (trimmed.isNotEmpty() && trimmed != lastSearchQuery) {
                    try {
                        lastSearchResults = youtubeRepository.search(trimmed)
                        lastSearchQuery = trimmed
                    } catch (e: Exception) {
                        KLog.e(TAG, "Browse search-result fetch failed for '$trimmed'", e)
                        return@future LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
                    }
                }
                val safePageSize = pageSize.coerceAtLeast(1)
                val from = page.coerceAtLeast(0) * safePageSize
                if (from >= lastSearchResults.size) {
                    return@future LibraryResult.ofItemList(ImmutableList.of(), null)
                }
                val to = (from + safePageSize).coerceAtMost(lastSearchResults.size)
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(lastSearchResults.subList(from, to).map(::mapSongToMediaItem)),
                    null
                )
            }
        }
    }
    
    // --- Browsing Helper Methods ---

    /**
     * Root of the browse tree, ordered by how well each node survives a bad
     * connection: the offline-first nodes (downloads, likes, history, device
     * library) come before the two that need the network. A car is the
     * environment most likely to be offline. All four offline nodes read
     * device-local stores already in memory or one prefs/SQLite read away -
     * none of them can come back empty because the network did.
     */
    private fun getRootItems(): ImmutableList<MediaItem> {
        val items = mutableListOf<MediaItem>()
        // 1. Recommended
        items.add(browsableCategoryItem("RECOMMENDED", "Recommended For You"))
        // 2. Playlists
        items.add(browsableCategoryItem("PLAYLISTS", "Your Playlists"))
        // 3. Downloads - playable with no network at all
        items.add(browsableCategoryItem("DOWNLOADS", "Downloaded", "Offline"))
        // 4. Liked - the device-side like store, not the account's
        items.add(browsableCategoryItem("LIKED", "Liked Songs"))
        // 5. Recently played - from listening history
        items.add(browsableCategoryItem("RECENT", "Recently Played"))
        // 6. The device's own audio library
        items.add(browsableCategoryItem("LOCAL_SONGS", "On This Device"))
        return ImmutableList.copyOf(items)
    }

    private fun browsableCategoryItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setExtras(contentStyleExtras(GRID_ITEM, LIST_ITEM))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build()

    private suspend fun fetchChildrenForId(parentId: String): List<MediaItem> {
        val now = System.currentTimeMillis()
        val isCacheValid = (now - lastBrowseCacheTime) < browseCacheValidityMs

        return when (parentId) {
            "RECOMMENDED" -> recommendedSongs(isCacheValid).map(::mapSongToMediaItem)
            "PLAYLISTS" -> playlistEntries(isCacheValid)
            // Offline-first nodes: all read device stores, none touch the
            // network, so none can fail the way the two above can.
            "DOWNLOADS" -> downloadRepository.downloadedSongs.value.map(::mapSongToMediaItem)
            "LIKED" -> likedSongsRepository.likedSongs.value.map(::mapSongToMediaItem)
            "RECENT" -> recentlyPlayedSongs().map(::mapSongToMediaItem)
            "LOCAL_SONGS" -> localDeviceSongs().map(::mapSongToMediaItem)            else -> {
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

    /**
     * Network recommendations with stale-while-revalidate serving: a valid
     * cache answers immediately, an expired one answers immediately from
     * cache while a single background refresh runs, and only a cold start
     * with nothing cached has to wait on the network.
     */
    private suspend fun recommendedSongs(isCacheValid: Boolean): List<Song> {
        val cached = cachedRecommendations
        if (cached != null && isCacheValid) return cached
        if (cached != null) {
            refreshRecommendationsInBackground()
            return cached
        }
        return try {
            youtubeRepository.getRecommendations().also {
                if (it.isNotEmpty()) {
                    cachedRecommendations = it
                    lastBrowseCacheTime = System.currentTimeMillis()
                }
            }
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to load recommendations for browse", e)
            emptyList()
        }
    }

    private fun refreshRecommendationsInBackground() {
        if (!recommendationsRefreshing.compareAndSet(false, true)) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val fresh = youtubeRepository.getRecommendations()
                if (fresh.isNotEmpty()) {
                    cachedRecommendations = fresh
                    lastBrowseCacheTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                KLog.e(TAG, "Background recommendations refresh failed", e)
            } finally {
                recommendationsRefreshing.set(false)
            }
        }
    }

    private suspend fun playlistEntries(isCacheValid: Boolean): List<MediaItem> {
        val cached = cachedPlaylists
        val playlists = if (cached != null && isCacheValid) {
            cached
        } else if (cached != null) {
            refreshPlaylistsInBackground()
            cached
        } else {
            try {
                youtubeRepository.getUserPlaylists().also {
                    if (it.isNotEmpty()) {
                        cachedPlaylists = it
                        lastBrowseCacheTime = System.currentTimeMillis()
                    }
                }
            } catch (e: Exception) {
                KLog.e(TAG, "Failed to load playlists for browse", e)
                emptyList()
            }
        }
        return playlists.map { playlist -> playlistEntry(playlist) }
    }

    private fun playlistEntry(playlist: PlaylistDisplayItem): MediaItem {
        val playlistId = playlist.url.substringAfter("list=")
        return MediaItem.Builder()
            .setMediaId("PLAYLIST_$playlistId")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(playlist.name)
                .setSubtitle(playlist.uploaderName)
                .setArtworkUri(playlist.thumbnailUrl.toArtworkUri())
                .setExtras(contentStyleExtras(LIST_ITEM, LIST_ITEM))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build()
    }

    private fun refreshPlaylistsInBackground() {
        if (!playlistsRefreshing.compareAndSet(false, true)) return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val fresh = youtubeRepository.getUserPlaylists()
                if (fresh.isNotEmpty()) {
                    cachedPlaylists = fresh
                    lastBrowseCacheTime = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                KLog.e(TAG, "Background playlists refresh failed", e)
            } finally {
                playlistsRefreshing.set(false)
            }
        }
    }

    /**
     * A usable artwork [Uri], or null when there is no artwork.
     *
     * `Uri.parse("")` yields an empty Uri rather than "no artwork", and a media
     * browser handed one tries to load it and fails. Android Auto can fail the
     * whole item on that, not just the picture, so a missing thumbnail has to
     * mean the field is absent.
     */
    /**
     * Listening history as playable songs, newest first, deduplicated so a
     * song played ten times appears once. Device-local plays are skipped:
     * history stores ids but not their content URIs, and a local id pushed
     * through YouTube stream resolution just dead-ends.
     */
    private suspend fun recentlyPlayedSongs(): List<Song> =
        try {
            statsRepository.loadHistory()
                .filter { it.source == SongSource.YOUTUBE }
                .distinctBy { it.songId }
                .take(RECENTLY_PLAYED_BROWSE_LIMIT)
                .map { entry ->
                    Song.fromYouTube(
                        videoId = entry.songId,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        duration = entry.duration,
                        thumbnailUrl = entry.thumbnailUrl
                    )
                }
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to load listening history for browse", e)
            emptyList()
        }

    private suspend fun localDeviceSongs(): List<Song> =
        try {
            songRepository.getSongs(
                excludedFolders = themePreferences.excludedFolders.value,
                manualScan = themePreferences.manualScanEnabled.value
            )
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to scan device library for browse", e)
            emptyList()
        }

    private fun String?.toArtworkUri(): Uri? =
        this?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    /**
     * Content style hints tell Auto how to render each item: as a grid or a
     * list, as browsable or playable. Set per item rather than only at the
     * root, because the root hints alone leave deeper levels to the client's
     * default - which is why every category renders as a grid and every song
     * list as a list.
     */
    private fun contentStyleExtras(browsableHint: Int, playableHint: Int): android.os.Bundle =
        android.os.Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", browsableHint)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", playableHint)
        }

    private fun buildRootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Root")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    /** A single playable id looked up across every store the tree serves. */
    private suspend fun findSongForBrowseId(mediaId: String): Song? {
        findSongInCache(mediaId)?.let { return it }
        downloadRepository.downloadedSongs.value.firstOrNull { it.id == mediaId }
            ?.let { return it }
        likedSongsRepository.likedSongs.value.firstOrNull { it.id == mediaId }
            ?.let { return it }
        recentlyPlayedSongs().firstOrNull { it.id == mediaId }?.let { return it }
        return null
    }

    /** A search match shaped for the resolution pipeline: id plus placeholder,
     *  exactly what a browse-served YouTube item looks like on arrival. */
    private fun Song.toPlaceholderMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri("$PLACEHOLDER_PREFIX$id")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setDurationMs(duration.takeIf { it > 0L })
                .setArtworkUri(thumbnailUrl.toArtworkUri())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build())
            .build()

    private fun mapSongToMediaItem(song: Song): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setDurationMs(song.duration.takeIf { it > 0L })
                .setArtworkUri(song.thumbnailUrl?.toArtworkUri()
                    ?: song.albumArtUri)
                .setExtras(contentStyleExtras(LIST_ITEM, LIST_ITEM))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build())
        // Device-local songs must arrive carrying their content:// URI:
        // onAddMediaItems preserves it for direct playback, while an item
        // without one would be pushed through YouTube stream resolution and
        // fail. Downloads also resolve locally, but through the mediaId
        // lookup in performResolution instead.
        if (song.source == SongSource.LOCAL && song.uri != null) {
            builder.setUri(song.uri)
        }
        return builder.build()
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
                KLog.e(TAG, "Failed to pre-warm cache", e)
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
            engine.setPauseAtEndOfMediaItems(true)
            armCastEndOfTrackTimerIfNeeded()
            themePreferences.saveSleepTimer(endsAt = 0L, endOfTrack = true)
        } else {
            val durationMs = minutes * 60_000L
            armDurationSleepTimer(System.currentTimeMillis() + durationMs)
        }
        publishSleepTimerState()
    }

    private fun armDurationSleepTimer(endsAt: Long) {
        sleepTimerEndOfTrack = false
        sleepTimerEndsAt = endsAt
        engine.setPauseAtEndOfMediaItems(false)
        themePreferences.saveSleepTimer(endsAt = endsAt, endOfTrack = false)
        sleepTimerJob = serviceScope.launch {
            while (true) {
                val remaining = sleepTimerEndsAt - System.currentTimeMillis()
                if (remaining <= 0L) break
                delay(remaining.coerceAtMost(SLEEP_TIMER_TICK_MS))
            }
            fadeOutAndPause()
            clearSleepTimer()
        }
    }

    /** Re-arm a timer after the media service itself was recreated. */
    private fun restoreSleepTimer() {
        val endOfTrack = themePreferences.isSleepTimerEndOfTrack()
        val endsAt = themePreferences.getSleepTimerEndsAt()
        when {
            endOfTrack -> {
                sleepTimerEndOfTrack = true
                sleepTimerEndsAt = 0L
                engine.setPauseAtEndOfMediaItems(true)
            }
            endsAt > System.currentTimeMillis() -> armDurationSleepTimer(endsAt)
            else -> {
                engine.setPauseAtEndOfMediaItems(false)
                themePreferences.clearSleepTimer()
            }
        }
    }

    /**
     * Ease the volume down before pausing.
     *
     * A sleep timer that cuts the audio dead is worse than one that does not
     * fire: the silence is what wakes people. Runs on [fadeVolumeJob] so it and
     * the crossfade fade-in can never drive the volume at the same time.
     */
    private fun fadeOutAndPause() {
        castPlayer?.let { remote ->
            // CastPlayer.volume is the receiver device volume, not a per-item
            // gain. Fading it would turn the television itself down and then
            // back up, affecting every app on it. Pause cleanly instead.
            remote.pause()
            return
        }
        fadeVolumeJob?.cancel()
        fadeVolumeJob = serviceScope.launch {
            val steps = 20
            for (i in steps - 1 downTo 0) {
                player.volume = trackGain * (i / steps.toFloat())
                delay(SLEEP_TIMER_FADE_MS / steps)
            }
            player.pause()
            // Back to full straight away, or pressing play would be silent.
            // Full is the corrected level, not 1.0.
            player.volume = trackGain
        }
    }

    /** Disarm, whether it fired or the user cancelled it. */
    private fun clearSleepTimer(publish: Boolean = true) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndsAt = 0L
        sleepTimerEndOfTrack = false
        castEndOfTrackJob?.cancel()
        castEndOfTrackJob = null
        // Both engines matter. The standby becomes audible at the next
        // crossfade and must not carry an old end-of-track instruction.
        engine.setPauseAtEndOfMediaItems(false)
        themePreferences.clearSleepTimer()
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

    /**
     * Recompute [trackGain] for whatever is playing now.
     *
     * @param applyNow whether to push the new gain to the player immediately.
     *   False while a fade owns the volume, since the ramp reads [trackGain]
     *   on every step and would fight a write landing mid-ramp.
     */
    /**
     * The loudness correction for whatever [p] is playing.
     *
     * Takes the player rather than reading the active one, because during a
     * transition the two engines are on different tracks and each needs its
     * own correction - that is the whole reason the engine asks per player
     * instead of being handed a single number.
     */
    private fun gainForPlayer(p: ExoPlayer): Float {
        val videoId = p.currentMediaItem?.mediaId ?: return 1f
        if (!isNormalizeVolumeEnabled) return 1f
        return TrackLoudnessStore.gainFor(this, videoId)
    }

    private fun refreshTrackGain(applyNow: Boolean) {
        trackGain = gainForPlayer(player)
        if (applyNow) engine.applyIdleVolumes()
    }

    /**
     * The short ramp a *manual* skip gets.
     *
     * Deliberately not the full crossfade: a three second overlap on a skip
     * makes the app feel unresponsive when the user has just asked for the next
     * song now. Deliberately not a hard cut either, which is jarring when every
     * automatic transition is smooth. Equal power like the real thing, so the
     * two never sound like different effects.
     */
    private fun performSkipFadeIn() {
        fadeVolumeJob?.cancel()
        val target = player
        fadeVolumeJob = serviceScope.launch {
            val gain = gainForPlayer(target)
            val steps = (SKIP_FADE_MS / 16L).toInt().coerceAtLeast(4)
            for (i in 1..steps) {
                val angle = (i.toFloat() / steps) * (Math.PI.toFloat() / 2f)
                target.volume = gain * kotlin.math.sin(angle) * engine.duckGain
                delay(SKIP_FADE_MS / steps)
            }
            target.volume = gain * engine.duckGain
        }
    }

    /**
     * Analyse only the small head/tail windows needed by AutoMix. Metered
     * networks never fetch bytes for analysis; they can still use a profile
     * once normal playback has fully cached the song.
     */
    private fun maybeProfile(mediaItem: MediaItem, knownDurationMs: Long? = null) {
        val id = mediaItem.mediaId
        val uri = mediaItem.localConfiguration?.uri ?: return
        val factory = cacheDataSourceFactory
        val durationMs = knownDurationMs?.takeIf { it > 0L }
            ?: mediaItem.mediaMetadata.durationMs?.takeIf { it > 0L }
            ?: return
        val isNetwork = uri.scheme == "http" || uri.scheme == "https"
        if (isNetwork && factory == null) return
        if (!profilingIds.add(id)) return

        resolveScope.launch {
            profileSemaphore.acquire()
            try {
                if (audioProfileStore.get(id) != null) return@launch
                if (isNetwork && ThemePreferences.isNetworkMetered(this@MusicService) &&
                    !CacheManager.isFullyCached(id)
                ) return@launch

                withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
                    AudioProfiler.profile(
                        songId = id,
                        context = this@MusicService,
                        uri = uri,
                        cacheKey = id,
                        factory = factory,
                        durationMs = durationMs,
                    )
                }?.let { profile ->
                    audioProfileStore.put(profile)
                    KLog.d(
                        TAG,
                        "Profile: $id lead=${profile.leadInSilenceMs} " +
                            "tail=${profile.tailFadeMs} abrupt=${profile.endsAbruptly} " +
                            "outro=${profile.outroLeadMs}"
                    )
                }
            } finally {
                profileSemaphore.release()
                profilingIds.remove(id)
            }
        }
    }

    /** Resolve Previous/Next against the audible queue, including rapid taps. */
    private fun requestManualSkip(forward: Boolean) {
        castPlayer?.let { remote ->
            if (!forward && remote.currentPosition > PREVIOUS_RESTART_MS) {
                remote.seekTo(0L)
                remote.play()
                return
            }
            val targetIndex = if (forward) {
                remote.getNextMediaItemIndex()
            } else {
                remote.getPreviousMediaItemIndex()
            }
            requestCastTransition(targetIndex)
            return
        }
        val current = player
        val pendingIndex = engine.pendingTargetIndex
        val baseIndex = pendingIndex ?: current.currentMediaItemIndex

        if (!forward && pendingIndex == null && current.currentPosition > PREVIOUS_RESTART_MS) {
            engine.cancelTransition()
            current.seekTo(0L)
            current.play()
            return
        }

        val targetIndex = when {
            pendingIndex != null -> if (forward) baseIndex + 1 else baseIndex - 1
            current.repeatMode == Player.REPEAT_MODE_ONE -> {
                if (forward) baseIndex + 1 else baseIndex - 1
            }
            forward -> current.getNextMediaItemIndex()
            else -> current.getPreviousMediaItemIndex()
        }
        requestManualTransition(targetIndex)
    }

    /**
     * Briefly overlap the currently audible track with a user-requested queue
     * item. Off bypasses this method's preparation entirely and performs an
     * ordinary immediate player jump.
     */
    private fun requestManualTransition(targetIndex: Int) {
        if (castPlayer != null) {
            requestCastTransition(targetIndex)
            return
        }
        val current = player
        if (targetIndex !in 0 until current.mediaItemCount) return
        if (targetIndex == current.currentMediaItemIndex) {
            current.seekTo(0L)
            current.play()
            return
        }
        if (!isCrossfadeEnabled) {
            jumpWithoutTransition(targetIndex)
            return
        }

        manualTransitionJob?.cancel()
        engine.cancelTransition()
        manualTransitionJob = serviceScope.launch {
            val outgoing = player
            if (targetIndex !in 0 until outgoing.mediaItemCount) return@launch
            val original = outgoing.getMediaItemAt(targetIndex)

            val target = if (isPlaceholder(original.localConfiguration?.uri)) {
                withTimeoutOrNull(MANUAL_RESOLVE_WAIT_MS) {
                    getOrStartResolution(original).await()
                }
            } else {
                original
            }

            if (target == null ||
                player !== outgoing ||
                targetIndex !in 0 until outgoing.mediaItemCount ||
                outgoing.getMediaItemAt(targetIndex).mediaId != original.mediaId
            ) {
                fallbackManualJump(targetIndex)
                return@launch
            }

            if (target !== original) outgoing.replaceMediaItem(targetIndex, target)
            val incomingStartMs = audioProfileStore.peek(target.mediaId)
                ?.leadInSilenceMs
                ?.minus(60L)
                ?.coerceIn(0L, 15_000L)
                ?: 0L
            val canOverlap = outgoing.isPlaying && engine.startTransition(
                nextItem = target,
                durationMs = MANUAL_CROSSFADE_MS,
                targetIndex = targetIndex,
                incomingStartMs = incomingStartMs,
            )
            if (!canOverlap) fallbackManualJump(targetIndex)
        }
    }

    /** Defined degraded path for an unresolved, paused, or unready target. */
    private fun fallbackManualJump(targetIndex: Int) {
        val current = player
        if (targetIndex !in 0 until current.mediaItemCount) return
        engine.cancelTransition()
        current.volume = 0f
        current.seekTo(targetIndex, 0L)
        current.play()
    }

    /** A literal Off path: no overlap and no volume ramp. */
    private fun jumpWithoutTransition(targetIndex: Int) {
        val current = player
        if (targetIndex !in 0 until current.mediaItemCount) return
        manualTransitionJob?.cancel()
        fadeVolumeJob?.cancel()
        engine.cancelTransition()
        current.volume = gainForPlayer(current) * engine.duckGain
        current.seekTo(targetIndex, 0L)
        current.play()
    }

    /**
     * A transition finished and the session now points at the other engine.
     *
     * The incoming player never emits `onMediaItemTransition` - it was handed
     * its item directly rather than advancing into it - so the work that
     * normally hangs off a transition has to be done here instead, or the queue
     * would stop prefetching the moment the first crossfade completed.
     */
    private fun onEngineSwapped(newActive: ExoPlayer) {
        mediaLibrarySession?.let { session ->
            runCatching { session.setPlayer(newActive) }
                .onFailure { KLog.e(TAG, "Could not re-point the session", it) }
        }
        trackGain = gainForPlayer(newActive)
        engine.setPauseAtEndOfMediaItems(sleepTimerEndOfTrack)
        prefetchUpcomingSongs()
    }

    /**
     * Start the overlap when the outgoing track is within the fade window.
     *
     * Runs at [TRANSITION_POLL_MS] rather than off the one-second progress
     * loop, which is too coarse to place the start of a fade and was what made
     * the old one step audibly.
     */
    private fun monitorTransitions() {
        transitionJob?.cancel()
        transitionJob = serviceScope.launch {
            while (isActive) {
                delay(TRANSITION_POLL_MS)
                if (!isCrossfadeEnabled || engine.isFading) continue
                val current = player
                if (!current.isPlaying) continue
                // Repeat-one means the "next" track is this one, and an overlap
                // of a track with itself is comb filtering, not a crossfade.
                if (current.repeatMode == Player.REPEAT_MODE_ONE) continue
                // The sleep timer wants this track to be the last one; starting
                // the next would be the app arguing with it.
                if (sleepTimerEndOfTrack) continue
                if (!current.hasNextMediaItem()) continue

                val duration = current.duration
                if (duration <= 0) continue
                val remaining = duration - current.currentPosition
                if (remaining <= 0) continue

                val nextIndex = current.getNextMediaItemIndex()
                if (nextIndex !in 0 until current.mediaItemCount) continue
                val nextItem = current.getMediaItemAt(nextIndex)
                // An unresolved item has no stream to fade in. Let the normal
                // advance happen only as the degraded path; normally this
                // proactively resolves the real playback-order successor long
                // before the transition window.
                if (isPlaceholder(nextItem.localConfiguration?.uri)) {
                    prefetchUpcomingSongs()
                    continue
                }

                val plan = if (isAutoMixEnabled) {
                    TransitionPlanner.plan(
                        fallbackOverlapMs = AUTO_MIX_FALLBACK_OVERLAP_MS,
                        maximumOverlapMs = AUTO_MIX_MAX_OVERLAP_MS,
                        outgoing = current.currentMediaItem?.mediaId?.let(audioProfileStore::peek),
                        incoming = audioProfileStore.peek(nextItem.mediaId),
                        outgoingDurationMs = duration,
                        preserveAbruptEnding = !current.shuffleModeEnabled &&
                            current.currentMediaItem?.mediaMetadata?.albumTitle
                                ?.toString()?.takeIf { it.isNotBlank() }?.let { album ->
                                    album == nextItem.mediaMetadata.albumTitle
                                        ?.toString()?.takeIf { it.isNotBlank() }
                                } == true,
                    )
                } else {
                    TransitionPlan(
                        overlapMs = crossfadeDurationMs,
                        incomingStartMs = 0L,
                        reason = TransitionPlan.Reason.FALLBACK,
                    )
                }
                // The prepare lead is how far out the transition may be held
                // ready; normally it equals the overlap, and only the silence
                // skip stretches it - the engine waits out the dead air and
                // fades where the music actually stopped.
                val prepareLeadMs = plan.effectivePrepareLeadMs
                if (!plan.shouldOverlap ||
                    remaining > prepareLeadMs + TRANSITION_PREPARE_LEAD_MS
                ) continue

                val started = engine.startTransition(
                    nextItem = nextItem,
                    durationMs = plan.overlapMs,
                    targetIndex = nextIndex,
                    incomingStartMs = plan.incomingStartMs,
                    incomingSpeed = plan.incomingSpeed,
                    filterSweepStrength = plan.filterSweepStrength,
                    startAtRemainingMs = prepareLeadMs,
                )
                if (started) {
                    KLog.d(
                        TAG,
                        "Crossfade: ${plan.reason} ${plan.overlapMs}ms " +
                            "lead=${plan.incomingStartMs} beatIn=${plan.incomingDownbeatDelayMs} " +
                            "speed=${plan.incomingSpeed} " +
                            "key=${plan.harmonicMatch} into ${nextItem.mediaId}"
                    )
                }
            }
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

                    // The fade-out used to live here, on a one-second tick,
                    // which gave a three second fade about three volume steps.
                    // monitorTransitions drives it now, off the real playback
                    // position, and the outgoing ramp belongs to CrossfadeEngine.

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
