package com.ivor.ivormusic.service

import com.ivor.ivormusic.service.core.QueueShuffle
import com.ivor.ivormusic.util.KLog

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.ivor.ivormusic.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.MainActivity
import android.media.audiofx.AudioEffect
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.AudioProfileStore
import com.ivor.ivormusic.data.TrackLoudnessStore
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.arrangedBy
import com.ivor.ivormusic.data.PlaybackSessionRepository
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.widget.PlayerWidgetStore
import com.ivor.ivormusic.widget.PlayerWidgets
import com.ivor.ivormusic.widget.toWidgetSnapshot
import com.ivor.ivormusic.presence.DiscordPresenceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

    // --- Collaborators ---
    //
    // Each owns one part of what the service does; the service wires them to
    // the player and the session. Those that launch work are built on first
    // use, from the scopes above, after onCreate has set up what they read.
    // The browse tree and the resolver are plain fields, which tests replace.

    /** See [MusicBrowseTree]. */
    private val browseTree = MusicBrowseTree(
        context = this,
        mainScope = serviceScope,
        ioScope = resolveScope,
        youtube = { youtubeRepository },
        downloadedSongs = { downloadRepository.downloadedSongs.value },
        likedSongs = { likedSongsRepository.likedSongs.value },
        statsRepository = { statsRepository },
        songRepository = { songRepository },
        preferences = { themePreferences },
        notifyChildrenChanged = { parentId, count ->
            mediaLibrarySession?.notifyChildrenChanged(parentId, count, null)
        },
    )

    /** See [MusicStreamResolver]. */
    private val streamResolver = MusicStreamResolver(
        resolveScope = resolveScope,
        youtube = { youtubeRepository },
        downloadedSongs = { downloadRepository.downloadedSongs.value },
        cacheEnabled = { isCacheEnabled },
        cachedSong = { browseTree.cachedSong(it) },
    )

    /** See [StreamCacheWarmer]. */
    private val streamWarmer by lazy {
        StreamCacheWarmer(
            context = this,
            ioScope = resolveScope,
            cacheFactory = { cacheDataSourceFactory },
            cacheEnabled = { isCacheEnabled },
        )
    }

    /** See [AutoMixProfiling]. */
    private val autoMixProfiling by lazy {
        AutoMixProfiling(
            context = this,
            ioScope = resolveScope,
            store = { audioProfileStore },
            cacheFactory = { cacheDataSourceFactory },
            enabled = { isCrossfadeEnabled && isAutoMixEnabled },
        )
    }

    /** See [PlaybackRecovery]. */
    private val playbackRecovery by lazy {
        PlaybackRecovery(
            scope = serviceScope,
            player = { player },
            resolver = { streamResolver },
            youtube = { youtubeRepository },
            forgetWarmed = { streamWarmer.clear() },
            announce = ::announcePlaybackFailure,
        )
    }

    /** See [PlaybackProgressTicker]. */
    private val progressTicker by lazy {
        PlaybackProgressTicker(
            context = this,
            scope = serviceScope,
            player = { player },
            liveUpdate = { musicProgressLiveUpdate },
            publishWidgets = ::publishWidgetState,
            checkpoint = { sessionCheckpoints.checkpoint() },
        )
    }

    private val sessionRepository by lazy { PlaybackSessionRepository(this) }

    /** See [PlaybackSessionCheckpoints]. */
    private val sessionCheckpoints by lazy {
        PlaybackSessionCheckpoints(
            repository = { sessionRepository },
            mainScope = serviceScope,
            ioScope = resolveScope,
            player = { if (::engine.isInitialized) player else null },
            unshuffledOrder = { queueShuffle.unshuffledOrder() },
        )
    }

    /** See [HeadsetButtonHandler]. */
    private val headsetButtons by lazy {
        HeadsetButtonHandler(serviceScope) { forward, restartCurrent ->
            requestManualSkip(forward = forward, restartCurrentOnPrevious = restartCurrent)
        }
    }

    /** See [RequestedMediaItems]. */
    private val requestedItems by lazy { RequestedMediaItems(serviceScope, browseTree) }

    /** See [AutomaticCrossfades]. */
    private val automaticCrossfades by lazy {
        AutomaticCrossfades(
            scope = serviceScope,
            engine = { engine },
            profiles = { audioProfileStore },
            settings = {
                AutomaticCrossfades.Settings(
                    crossfade = isCrossfadeEnabled,
                    autoMix = isAutoMixEnabled,
                    crossfadeDurationMs = crossfadeDurationMs,
                    playbackSpeed = playbackSpeed,
                    stopAtEndOfSong = sleepTimer.endOfTrack,
                )
            },
            prefetch = ::prefetchUpcomingSongs,
        )
    }

    /** See [SleepTimerController]. */
    private val sleepTimer by lazy {
        SleepTimerController(
            scope = serviceScope,
            preferences = themePreferences,
            setPauseAtEndOfItems = { engine.setPauseAtEndOfMediaItems(it) },
            fadeOutAndPause = ::fadeOutAndPause,
            pauseIfPlaying = { if (player.playWhenReady) player.pause() },
            onChanged = ::publishSessionState,
        )
    }

    // --- State & Cache ---

    // The disk cache the player reads through, shared with the warmer and the
    // profiler so all three write and read under the same keys.
    private var cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory? = null

    private val prefetchingOccurrences = java.util.concurrent.ConcurrentHashMap.newKeySet<Any>()

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
    private var playbackRepeatMode = Player.REPEAT_MODE_OFF

    /** Shuffle as a mode over a timeline that is the play order; see [QueueShuffle]. */
    private val queueShuffle = QueueShuffle()

    /**
     * A restored session's order from before it was shuffled, adopted when
     * its queue lands so the restore is not shuffled a second time.
     */
    private var pendingRestoredOrder: List<String>? = null

    /** True while [arrangeQueue] rebuilds the timeline, whose steps are not edits. */
    private var arranging = false

    /**
     * Songs heard in the last [RECENT_PLAY_WINDOW_MS], plus every song this
     * service has played since. A shuffle built while this is known plays the
     * rest first (see [QueueShuffle]). Seeded from play history
     * off the main thread; read on it, synchronously, when a shuffle is built.
     */
    private val recentSongIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Live Update (Android 16+)
    private var musicProgressLiveUpdate: MusicProgressLiveUpdate? = null

    companion object {
        private const val TAG = "MusicService"

        /**
         * How long a play keeps a song at the back of new shuffles. Three days
         * so someone shuffling the same playlist daily works through all of it
         * rather than re-hearing yesterday's first half. [judgement]
         */
        private const val RECENT_PLAY_WINDOW_MS = 3L * 24 * 60 * 60 * 1000
        private const val PREFETCH_AHEAD_COUNT = 3

        private const val PLACEHOLDER_PREFIX = MusicStreamResolver.PLACEHOLDER_PREFIX
        private const val CACHED_PREFIX = MusicStreamResolver.CACHED_PREFIX

        /**
         * The short ramp a manual skip and a non-overlapped advance get.
         * Long enough not to click, short enough that pressing next still
         * feels immediate.
         */
        private const val SKIP_FADE_MS = 300L

        private const val PREVIOUS_RESTART_MS = 3_000L

        /**
         * Broadcast to controllers when a song could not be played, so the
         * app can say so instead of the queue silently jumping. Carries
         * [ARG_FAILED_TITLE] and [ARG_FAILED_OUTCOME], one of the
         * `FAILURE_` values.
         */
        const val EVENT_PLAYBACK_FAILED = "com.ivor.ivormusic.PLAYBACK_FAILED"
        const val ARG_FAILED_TITLE = "failed_title"
        const val ARG_FAILED_OUTCOME = "failed_outcome"
        /** Skipped to the next song. */
        const val FAILURE_SKIPPED = "skipped"
        /** Nothing left to skip to, so playback stopped. */
        const val FAILURE_STOPPED = "stopped"
        /** YouTube's bot check refused it; the player stays on the song. */
        const val FAILURE_REFUSED = "refused"

        // --- Sleep timer: the contract with PlayerViewModel ---

        /** Arm the timer. Carries [ARG_SLEEP_TIMER_MINUTES]; 0 = end of track. */
        const val CMD_SLEEP_TIMER_SET = "com.ivor.ivormusic.SLEEP_TIMER_SET"
        const val CMD_SLEEP_TIMER_CANCEL = "com.ivor.ivormusic.SLEEP_TIMER_CANCEL"
        const val ARG_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"

        const val CMD_SKIP_NEXT = "com.ivor.ivormusic.SKIP_NEXT"
        const val CMD_SKIP_PREVIOUS = "com.ivor.ivormusic.SKIP_PREVIOUS"
        const val CMD_SKIP_TO_INDEX = "com.ivor.ivormusic.SKIP_TO_INDEX"
        const val CMD_RESTORE_PLAYBACK = "com.ivor.ivormusic.RESTORE_PLAYBACK"
        const val ARG_SKIP_INDEX = "skip_index"

        /**
         * Set the music playback rate. Carries [ARG_PLAYBACK_SPEED], and
         * [ARG_PLAYBACK_SPEED_PERSIST] to say whether this value should
         * outlive the session - a slider sends the live value continuously
         * while it is being dragged and persists once, on release.
         *
         * A custom command rather than the player's own COMMAND_SET_SPEED_AND
         * _PITCH because the rate belongs to both engines and to the crossfade
         * plan, not to whichever ExoPlayer the session happens to point at:
         * written straight onto the active player it would be undone by the
         * next transition, which resets tempo on both.
         */
        const val CMD_SET_PLAYBACK_SPEED = "com.ivor.ivormusic.SET_PLAYBACK_SPEED"
        const val ARG_PLAYBACK_SPEED = "playback_speed"
        const val ARG_PLAYBACK_SPEED_PERSIST = "playback_speed_persist"

        /** The media notification's repeat button: off -> all -> one -> off. */
        const val CMD_CYCLE_REPEAT = "com.ivor.ivormusic.CYCLE_REPEAT"

        /** The media notification's heart: like or unlike the playing song. */
        const val CMD_TOGGLE_LIKE = "com.ivor.ivormusic.TOGGLE_LIKE"

        /**
         * Ask YouTube for the playing song's stream again and swap it in where
         * it was. Answers RESULT_ERROR_IO when no new stream arrived in time,
         * RESULT_ERROR_NOT_SUPPORTED for a file on this device.
         */
        const val CMD_REFRESH_STREAM = "com.ivor.ivormusic.REFRESH_STREAM"
        const val EXTRA_SONG_SOURCE = "com.ivor.ivormusic.SONG_SOURCE"

        /** Session-extras keys the timer state is published under. */
        const val EXTRA_SLEEP_TIMER_ENDS_AT = "sleep_timer_ends_at"
        const val EXTRA_SLEEP_TIMER_END_OF_TRACK = "sleep_timer_end_of_track"

        /** Session-extras key the current playback rate is published under. */
        const val EXTRA_PLAYBACK_SPEED = "playback_speed"

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
        CacheManager.removeLegacyVideoEntries()
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
        sessionCheckpoints.start()
        initializePlayer()
        restorePlaybackModes()
        sleepTimer.restore()
        seedRecentSongs()

        // 5. Initialize Session
        initializeSession()

        // 6. Warm the visitorData token so the first stream resolution of a
        // session doesn't pay for the mint on its critical path. Music-first
        // sessions (and Android Auto) never construct VideoPlayerViewModel,
        // which was the only other place that prefetched it.
        resolveScope.launch { youtubeRepository.prefetchVisitorData() }
        resolveScope.launch { audioProfileStore.warm() }

        // 7. React to the user switching profile.
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
                    browseTree.invalidate()
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
                                // Likes and listening history are per profile too.
                                session.notifyChildrenChanged(controller, "LIKED", 0, null)
                                session.notifyChildrenChanged(controller, "RECENT", 0, null)
                            }
                        }
                    }.onFailure { KLog.w(TAG, "notifyChildrenChanged after profile switch failed", it) }

                    resolveScope.launch { youtubeRepository.prefetchVisitorData() }

                    // Listening history is per profile, and a fresh-first
                    // shuffle plays last what this profile heard recently.
                    recentSongIds.clear()
                    seedRecentSongs()
                }
        }
    }

    /**
     * The app was swiped away from recents.
     *
     * Music that is meant to be playing keeps playing - swiping away the screen
     * is not a request to stop the music, and every other player treats it
     * that way. Only an idle service stops, which is also the one case the old
     * reason for always stopping applied to: a paused foreground notification
     * cannot be swiped away by the user.
     *
     * "Meant to be playing" is play-when-ready rather than Media3's default
     * `isPlaying`: a song that is still buffering at the moment of the swipe
     * would otherwise be paused and lost.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        sessionCheckpoints.checkpoint()
        val current = player
        val meantToPlay = current.playWhenReady &&
            current.mediaItemCount > 0 &&
            current.playbackState != Player.STATE_ENDED &&
            current.playbackState != Player.STATE_IDLE
        if (!meantToPlay) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        KLog.i(TAG, "MusicService Destroying...")
        // The widgets keep the track but must not keep claiming it is playing:
        // they render from a stored snapshot with no session of their own, so
        // nothing else would ever correct a playing flag left behind here.
        runCatching { PlayerWidgetStore.markStopped(this) }
        runCatching { PlayerWidgets.pushAll(this) }
        runCatching { DiscordPresenceManager.onServiceStopped() }
        fadeVolumeJob?.cancel()
        progressTicker.stop()
        automaticCrossfades.stop()
        headsetButtons.release()
        sleepTimer.release()
        audioFocus.abandon()
        // Before the scopes go: the writer lives in one of them, and this is
        // the last moment the player can still be read.
        sessionCheckpoints.writeFinal()
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
        streamResolver.clear()
        playbackRecovery.clear()
        streamWarmer.clear()
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        // May be null briefly during teardown; Media3 handles this gracefully and
        // the connecting MediaController will simply receive a connection failure
        // rather than binding to a released session.
        return mediaLibrarySession
    }

    private val notificationGate = com.ivor.ivormusic.service.core.MediaNotificationGate()
    private var notificationPostsSkipped = 0

    /**
     * Post only what changed. Media3 asks for a rebuild on every queue edit,
     * and each one re-sends the cover to every app watching notifications
     * (see [com.ivor.ivormusic.service.core.MediaNotificationGate]). A request
     * to start in the foreground always goes through: skipping one would let
     * the system stop the service mid-song.
     */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val state = notificationState(session.player)
        if (!startInForegroundRequired && !notificationGate.shouldPost(state)) {
            notificationPostsSkipped++
            return
        }
        if (startInForegroundRequired) notificationGate.shouldPost(state)
        KLog.d(TAG, "Notification: posted (skipped $notificationPostsSkipped unchanged since the last)")
        notificationPostsSkipped = 0
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun notificationState(player: Player): com.ivor.ivormusic.service.core.MediaNotificationState {
        val item = player.currentMediaItem
        val metadata = player.mediaMetadata
        return com.ivor.ivormusic.service.core.MediaNotificationState(
            playerIdentity = System.identityHashCode(player),
            mediaId = item?.mediaId,
            queueItemId = item?.queueItemId,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            album = metadata.albumTitle?.toString(),
            artworkUri = metadata.artworkUri?.toString(),
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            suppressionReason = player.playbackSuppressionReason,
            isPlaying = player.isPlaying,
            speed = player.playbackParameters.speed,
            canGoPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS),
            canGoNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT),
            canPlayPause = player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
            repeatMode = player.repeatMode,
            liked = item?.mediaId?.let(likedSongsRepository::isLiked) == true,
            promoted = ThemePreferences.isLivePlaybackUpdatesEnabled(this),
        )
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
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, CacheManager.createPerClientHttpFactory(this))
        // Null when cache init failed — playback then always goes direct.
        val cacheDataSourceFactory = CacheManager.createCacheDataSourceFactory(this, null)
        this.cacheDataSourceFactory = cacheDataSourceFactory
        val readOnlyCacheFactory = CacheManager.createCacheDataSourceFactory(this, writeEnabled = false)

        val smartDataSourceFactory = DataSource.Factory {
            val defaultSource = defaultDataSourceFactory.createDataSource()
            val cacheSource = cacheDataSourceFactory?.createDataSource()
            val readOnlyCacheSource = readOnlyCacheFactory?.createDataSource()

            object : DataSource {
                private var currentSource: DataSource? = null

                override fun addTransferListener(transferListener: TransferListener) {
                    defaultSource.addTransferListener(transferListener)
                    cacheSource?.addTransferListener(transferListener)
                    readOnlyCacheSource?.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme
                    val isNetwork = scheme == "http" || scheme == "https"

                    // Route to cache only for network requests, and only while
                    // the user's cache setting is on (checked per open() so a
                    // toggle applies to the very next stream, no restart).
                    currentSource = if (isNetwork && isCacheEnabled && cacheSource != null) {
                        cacheSource
                    } else if (isNetwork) {
                        readOnlyCacheSource ?: defaultSource
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
            // After the filter, so the strip shows what is actually heard
            // during an overlap. This is why the visualizer needs no
            // permission: the PCM is already ours on its way to the sink.
            val visualizerTap = VisualizerAudioProcessor()
            val renderersFactory = object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): AudioSink = DefaultAudioSink.Builder(context)
                    // Koda's own chain, for its tempo change: a stretcher
                    // made for music does the small corrections AutoMix
                    // asks for, in place of the speech-tuned default.
                    .setAudioProcessorChain(
                        MusicAudioProcessorChain(arrayOf(transitionFilter, visualizerTap))
                    )
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
            setEcho = { p, amount, delayMs -> transitionFilters[p]?.setEcho(amount, delayMs) },
        )

        audioFocus = AudioFocusController(
            context = this,
            isPlaybackActive = {
                engine.active.playWhenReady &&
                    engine.active.playbackState != Player.STATE_ENDED &&
                    engine.active.playbackState != Player.STATE_IDLE
            },
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

        mediaLibrarySession = MediaLibrarySession.Builder(this, SteadyDurationPlayer(engine.active), LibrarySessionCallback())
            .setSessionActivity(sessionIntent)
            .build()
        refreshMediaButtons()
        // A like from the app's own heart has to reach the notification's.
        serviceScope.launch {
            likedSongsRepository.likedSongIds.collect { refreshMediaButtons() }
        }
    }

    /** What the extra buttons last showed, so an unchanged state posts nothing. */
    private var mediaButtonsState: Pair<Int, Boolean>? = null

    /**
     * Repeat and like, in the two slots the system media controls keep after
     * previous/play/next. Rebuilt whenever what they draw changes - the repeat
     * mode, the song, or its liked state - because each button's icon is its
     * state: a notification cannot tint one button to say it is on.
     */
    private fun refreshMediaButtons() {
        val session = mediaLibrarySession ?: return
        val player = session.player
        val songId = player.currentMediaItem?.mediaId
        val liked = songId != null && likedSongsRepository.isLiked(songId)
        val state = player.repeatMode to liked
        if (state == mediaButtonsState) return
        mediaButtonsState = state

        val repeat = CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName(
                getString(
                    when (player.repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.notification_repeat_one
                        Player.REPEAT_MODE_ALL -> R.string.notification_repeat_all
                        else -> R.string.notification_repeat_off
                    }
                )
            )
            .setSessionCommand(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
            .build()
        val like = CommandButton.Builder(
            if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(
                getString(if (liked) R.string.notification_unlike else R.string.notification_like)
            )
            .setSessionCommand(SessionCommand(CMD_TOGGLE_LIKE, Bundle.EMPTY))
            .setEnabled(songId != null)
            .build()
        session.setMediaButtonPreferences(ImmutableList.of(repeat, like))
    }

    private fun cycleRepeatMode(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * The whole song goes to the store, not only its id: the Library's Liked
     * Songs list draws YouTube songs from that metadata when signed out.
     */
    private fun toggleCurrentSongLike(player: Player) {
        val song = player.currentMediaItem?.toQueueSong() ?: return
        likedSongsRepository.toggleLike(song)
        // The store's flow calls refreshMediaButtons too; this just skips the wait.
        refreshMediaButtons()
    }

    /** Fill [recentSongIds] from play history, newest first, back to the window's edge. */
    private fun seedRecentSongs() {
        serviceScope.launch {
            val cutoff = System.currentTimeMillis() - RECENT_PLAY_WINDOW_MS
            runCatching { statsRepository.loadHistory() }.getOrNull()
                ?.asSequence()
                ?.takeWhile { it.timestamp >= cutoff }
                ?.forEach { recentSongIds += it.songId }
        }
    }

    private fun restorePlaybackModes() {
        applyPlaybackSpeed(themePreferences.getPlaybackSpeed(), persist = false)
        playbackRepeatMode = themePreferences.getPlaybackRepeatMode()
        engine.setShuffleMode(themePreferences.isPlaybackShuffleEnabled())
        engine.setRepeatMode(playbackRepeatMode)
    }

    // Null until Last.fm might be enabled. Building the repository opens an
    // encrypted store - a keystore round trip - which nobody with Last.fm off
    // should pay at service start, and which never belongs on the main thread.
    private var lastFmTracker: LastFmPlaybackTracker? = null
    private var lastFmTrackerLoad: Job? = null

    /**
     * Builds the repository off the main thread, then hands the tracker back
     * on it, since sampling reads the player. A failure is logged and not
     * retried for this service's lifetime: the job stays set, so the loop does
     * not rebuild every 250ms.
     */
    private suspend fun loadLastFmTracker() {
        try {
            val repository = withContext(Dispatchers.IO) {
                com.ivor.ivormusic.data.LastFmRepository.get(applicationContext)
            }
            lastFmTracker = LastFmPlaybackTracker(repository)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.e(TAG, "Last.fm tracker unavailable", e)
        }
    }

    private fun observePreferences() {
        serviceScope.launch {
            while (isActive) {
                if (::engine.isInitialized) {
                    val tracker = lastFmTracker
                    if (tracker != null) {
                        tracker.sample(player)
                    } else if (lastFmTrackerLoad == null &&
                        com.ivor.ivormusic.data.LastFmRepository.mayBeEnabled(this@MusicService)
                    ) {
                        lastFmTrackerLoad = launch { loadLastFmTracker() }
                    }
                }
                delay(250)
            }
        }
        // These flows update live across ThemePreferences instances (the
        // settings screen writes through its own instance) thanks to the
        // SharedPreferences change listener inside ThemePreferences.
        serviceScope.launch {
            themePreferences.crossfadeEnabled.collect { enabled ->
                isCrossfadeEnabled = enabled
                automaticCrossfades.forgetAttempt()
                if (!enabled && ::engine.isInitialized) {
                    fadeVolumeJob?.cancel()
                    engine.cancelTransition()
                    engine.applyIdleVolumes()
                }
            }
        }
        serviceScope.launch {
            themePreferences.crossfadeAuto.collect {
                if (isAutoMixEnabled != it) engine.cancelTransition()
                automaticCrossfades.forgetAttempt()
                isAutoMixEnabled = it
            }
        }
        serviceScope.launch {
            themePreferences.crossfadeDurationMs.collect {
                if (crossfadeDurationMs != it.toLong() && !isAutoMixEnabled) engine.cancelTransition()
                crossfadeDurationMs = it.toLong()
            }
        }
        serviceScope.launch { themePreferences.cacheEnabled.collect { isCacheEnabled = it } }
        serviceScope.launch {
            WaveformSampler(this@MusicService, themePreferences.waveformSeekBar, { player }, { isCacheEnabled }).run()
        }
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

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
            ) {
                // Same-song scrubs do not emit onMediaItemTransition. Their
                // old fade clock is just as stale as a different-song jump.
                engine.cancelTransition()
                automaticCrossfades.forgetAttempt()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && !arranging) {
                followQueueEdit()
            }
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                // An emptied queue is the player being cleared (the mini
                // player dismissed); nothing should restore it next launch.
                if (timeline.isEmpty) sessionCheckpoints.clear() else sessionCheckpoints.schedule()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            automaticCrossfades.forgetAttempt()
            themePreferences.setPlaybackShuffle(shuffleModeEnabled)
            engine.setShuffleMode(shuffleModeEnabled)
            val keys = player.occurrenceKeys()
            when {
                shuffleModeEnabled && !queueShuffle.isOn -> arrangeQueue(
                    queueShuffle.turnOn(keys, player.currentMediaItemIndex, isRecent(keys), java.util.Random())
                )
                !shuffleModeEnabled && queueShuffle.isOn -> arrangeQueue(queueShuffle.turnOff(keys))
            }
            sessionCheckpoints.schedule()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            automaticCrossfades.forgetAttempt()
            playbackRepeatMode = repeatMode
            themePreferences.setPlaybackRepeatMode(repeatMode)
            engine.setRepeatMode(repeatMode)
            refreshMediaButtons()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) lastFmTracker?.reset()
            automaticCrossfades.forgetAttempt()
            refreshMediaButtons()
            // Arriving at a song - an advance, a skip, a seek back to it - gives
            // it a fresh recovery allowance. Not a playlist change: that is also
            // what replacing a failed song's source reports, and resetting there
            // would retry it forever.
            if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                playbackRecovery.resetAllowance(mediaItem)
            }
            mediaItem?.mediaId?.let {
                com.ivor.ivormusic.data.YouTubeRequestLedger.begin("song $it")
                // Heard now, so a shuffle built later in this session puts it
                // back. Not gated on incognito: nothing is recorded, it only
                // steers this process's own shuffles.
                if (it.isNotBlank()) recentSongIds += it
            }

            // 1. Loudness correction for the new track, before anything sets a
            // volume. It may still be unknown here - an unresolved song has not
            // called /player yet - so STATE_READY refreshes it again once the
            // real URI is in place.
            refreshTrackGain(applyNow = false)

            // 1b. Any change of the current item invalidates a transition
            // that is already in flight, so drop it whatever moved us here.
            // The engine's own swap never reaches this callback - the incoming
            // player is handed its item directly rather than advancing into it
            // - so reaching here mid-fade always means something else moved.
            //
            // AUTO is the outgoing track ending before the overlap finished.
            // PLAYLIST_CHANGED is the one users meet: tapping a song in the
            // list they are listening to replaces the queue, after which every
            // index the transition planned against addresses a different
            // track. Leaving it running let the swap hand the session to the
            // standby - playing the old queue's next song - instead of the
            // song that was tapped, and left `pendingTargetIndex` pointing
            // into a queue that no longer exists, so the next Next/Previous
            // jumped somewhere arbitrary too.
            // Also retire a post-swap tempo release, which outlives isFading.
            engine.cancelTransition()

            // 2. Volume for the new track. An automatic advance that reaches
            // here is one the engine did *not* overlap - crossfade off, an
            // unresolved next item or repeat-one. Off means literally off: no
            // overlap and no fade-in on either automatic or manual changes.
            if (!isCrossfadeEnabled || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                fadeVolumeJob?.cancel()
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

            sessionCheckpoints.checkpoint()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)

            // Start prefetching as soon as we are ready
            if (playbackState == Player.STATE_READY) {
                // The current song plays — give it back its full retry budget
                // so one bad stretch (expired URL, network blip) months of
                // uptime ago can't permanently blacklist it.
                player.currentMediaItem?.let(playbackRecovery::resetAllowance)
                // Resolution happens after the transition, so this is the first
                // point at which a first-play song's loudness is known. Applied
                // only when no fade is running, so a crossfade-in keeps the
                // volume it is ramping.
                refreshTrackGain(applyNow = true)
                player.currentMediaItem?.let { autoMixProfiling.profile(it, player.duration) }
                prefetchUpcomingSongs()
            }

            // Android 16 Live Update: dismiss when playback ends or returns to idle so
            // the progress chip never lingers on the lock screen / shade after the
            // queue finishes or the service is paused.
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                progressTicker.stop()
                musicProgressLiveUpdate?.hide()
            }
        }

        /**
         * Everything the home screen widgets know, written from here.
         *
         * This callback is the only writer that never has to guess: it runs on
         * the player's own thread, inside the batch that reports what changed,
         * so the state it reads is exactly the state that caused the redraw.
         * The widgets then render straight out of the store with no session
         * bind of their own - see widget/PlayerWidgetCommon.kt.
         *
         * It replaced three scattered pushAll calls whose coverage was the bug:
         * onIsPlayingChanged only fired on the *pause* edge, so starting
         * playback left every widget showing a play glyph until something else
         * happened to redraw it, and shuffle and repeat had no push at all, so
         * the Quick controls toggles lit up only on the next track change.
         * onEvents carries the full set at once, which makes the list below
         * auditable rather than being whatever overrides happened to exist.
         */
        override fun onEvents(player: Player, events: Player.Events) {
            super.onEvents(player, events)
            if (events.containsAny(
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                publishWidgetState()
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
                sleepTimer.endOfTrack
            ) {
                sleepTimer.clear()
            }
            if (!playWhenReady) {
                // A pause, whatever caused it. Settled here rather than in
                // onIsPlayingChanged: a pause while the outgoing track is
                // buffering changes nothing about isPlaying, and the overlap
                // would then be abandoned by the fade itself with the
                // incoming track dropped.
                engine.settleForPause()
            }
            if (!playWhenReady && !audioFocus.isPausedByFocusLoss) {
                audioFocus.abandon()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                // Focus is the service's, not the players' - see
                // AudioFocusController. Requested here rather than on the play
                // command so it covers every route into playback, including the
                // session callbacks and Android Auto.
                audioFocus.request()
                progressTicker.start()
                automaticCrossfades.start()
            } else {
                progressTicker.stop()
                sessionCheckpoints.checkpoint()
                // A pause mid-overlap is settled in onPlayWhenReadyChanged,
                // which may hand the pause to the incoming track. Anything else
                // that stops the audible player - buffering, an error, the end
                // - still drops the overlap and keeps the track on screen.
                if (player.playWhenReady) engine.cancelTransition()
                automaticCrossfades.stop()
                musicProgressLiveUpdate?.hide()
                if (!audioFocus.isPausedByFocusLoss) {
                    audioFocus.abandon()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            KLog.e(TAG, "Player Error: ${error.errorCodeName}", error)
            playbackRecovery.onError(error)
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
                val deferred = streamResolver.resolve(mediaItem)

                try {
                    val resolvedItem = streamResolver.bind(mediaItem, deferred.await())

                    // Apply if still current
                    if (player.currentMediaItem?.isSameQueueItemAs(mediaItem) == true &&
                        isPlaceholder(player.currentMediaItem?.localConfiguration?.uri)
                    ) {
                        // Prefetch may have already applied this shared result.
                        // Only the still-unresolved occurrence needs replacing.
                        KLog.i(TAG, "Validation: Applied resolved item for $videoId")
                        player.replaceMusicSource(player.currentMediaItemIndex, resolvedItem)
                    }
                } catch (e: CancellationException) {
                    throw e
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
        if (!ThemePreferences.isPlaybackPreloadEnabled(this)) return
        // Up to three full resolutions for songs not yet reached, each of which
        // the bot check would refuse the same way. The song the user is on
        // still resolves through validateAndPlayCurrentItem.
        if (YouTubeRepository.isBotCheckVerdictActive()) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        // The first entry must be the player's real next item. Under repeat
        // that can be the top of the queue rather than currentIndex + 1, and
        // resolving only the rows below would leave it a placeholder until
        // the outgoing song has already ended.
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
                // Resolution is shared by song; applying and warming belong
                // to each occurrence. An old queue must not suppress a new one.
                val occurrence = item.queueItemId ?: item
                if (!prefetchingOccurrences.add(occurrence)) return@forEachIndexed
                serviceScope.launch {
                    try {
                        val deferred = streamResolver.resolve(item)
                        val resolvedItem = streamResolver.bind(item, deferred.await())
                        
                        // Update player if item is still there
                        if (targetIndex < player.mediaItemCount &&
                            player.getMediaItemAt(targetIndex).isSameQueueItemAs(item) &&
                            isPlaceholder(player.getMediaItemAt(targetIndex).localConfiguration?.uri)
                        ) {
                            KLog.d(
                                TAG,
                                "Prefetch: Updated ${if (offset == 0) "actual next" else "+${offset + 1}"} " +
                                    "(${item.mediaId})"
                            )
                            player.replaceMusicSource(targetIndex, resolvedItem)
                            streamWarmer.warm(item.mediaId, resolvedItem.localConfiguration?.uri)
                            autoMixProfiling.profile(resolvedItem)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        KLog.w(TAG, "Prefetch: Failed to resolve upcoming ${item.mediaId}")
                    } finally {
                        prefetchingOccurrences.remove(occurrence)
                    }
                }
            } else {
                // Already resolved: by an earlier round, by validation, or a
                // local or downloaded file that never was a placeholder. The
                // branch above is the only other place an upcoming song is
                // analysed, so without this one such a song reached its
                // transition with no profile and AutoMix fell back to a plain
                // fade. A song already analysed, or being analysed, returns
                // at once.
                autoMixProfiling.profile(item)
            }
        }
    }

    // --- Stream resolution: see MusicStreamResolver ---

    private fun isPlaceholder(uri: Uri?): Boolean {
        return uri == null || uri.toString().startsWith(PLACEHOLDER_PREFIX)
    }

    // --- Playback failures: see PlaybackRecovery ---

    /** Tell every controller a song could not be played; see [EVENT_PLAYBACK_FAILED]. */
    private fun announcePlaybackFailure(failed: MediaItem, outcome: String) {
        val session = mediaLibrarySession ?: return
        runCatching {
            session.broadcastCustomCommand(
                SessionCommand(EVENT_PLAYBACK_FAILED, Bundle.EMPTY),
                Bundle().apply {
                    putString(ARG_FAILED_TITLE, failed.mediaMetadata.title?.toString())
                    putString(ARG_FAILED_OUTCOME, outcome)
                },
            )
        }.onFailure { KLog.w(TAG, "Could not announce a playback failure: ${it.message}") }
    }

    // --- Media Library Session Callback ---

    /**
     * Read the last playable queue without touching a Player from the IO
     * thread: the rows in the order they play, and, when shuffle is on and the
     * session was saved shuffled, the order they had before it.
     */
    private fun loadPlaybackResumption(): Pair<MediaSession.MediaItemsWithStartPosition, List<String>?>? {
        val saved = sessionRepository.load()
        if (saved == null) {
            val lastSong = themePreferences.getLastPlayedSong() ?: return null
            val item = MusicQueueItem(song = lastSong).toPlaybackMediaItem()
            return MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0L) to null
        }
        val order = saved.playOrder.toIntArray().takeIf { it.isNotEmpty() && themePreferences.isPlaybackShuffleEnabled() }
        val rows = if (order != null) saved.queue.arrangedBy(order) else saved.queue
        val startIndex = order?.indexOf(saved.currentIndex)?.takeIf { it >= 0 } ?: saved.currentIndex
        return MediaSession.MediaItemsWithStartPosition(
            rows.map { it.toPlaybackMediaItem() },
            startIndex,
            saved.positionMs,
        ) to order?.let { saved.queue.map(MusicQueueItem::id) }
    }
    
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        /** The one-button headset gesture; see [HeadsetButtonHandler]. */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean = headsetButtons.handle(session.player, intent) ||
            super.onMediaButtonEvent(session, controllerInfo, intent)

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .build()

            val availableSessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(CMD_SLEEP_TIMER_SET, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SLEEP_TIMER_CANCEL, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SKIP_NEXT, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SKIP_PREVIOUS, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SKIP_TO_INDEX, Bundle.EMPTY))
                    .add(SessionCommand(CMD_RESTORE_PLAYBACK, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SET_PLAYBACK_SPEED, Bundle.EMPTY))
                    .add(SessionCommand(CMD_CYCLE_REPEAT, Bundle.EMPTY))
                    .add(SessionCommand(CMD_TOGGLE_LIKE, Bundle.EMPTY))
                    .add(SessionCommand(CMD_REFRESH_STREAM, Bundle.EMPTY))
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
            publishSessionState()
        }

        /**
         * Rebuild the last queue when a short-lived controller asks an empty
         * service to prepare or play. Home-screen widget actions commonly hit
         * this path after an OEM has reclaimed Koda's process: the widget still
         * has its persisted picture, but a newly created ExoPlayer has no
         * timeline until Media3 is given the saved session here.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // System UI also asks for resume-card metadata without applying
            // a queue. That read remains independent of current playback.
            if (!isForPlayback) return resolveScope.future {
                loadPlaybackResumption()?.first
                    ?: throw IllegalStateException("No saved playback session")
            }
            val requestedPlayer = mediaSession.player
            return serviceScope.future {
                fun queueStillEmpty() = mediaSession.player === requestedPlayer &&
                    requestedPlayer.mediaItemCount == 0
                check(queueStillEmpty()) { "Playback queue changed before resumption" }
                val restored = withContext(Dispatchers.IO) { loadPlaybackResumption() }
                // Media3 1.11 applies a successful future unconditionally.
                // Refuse on the application thread before returning stale data.
                check(queueStillEmpty()) { "Playback queue changed during resumption" }
                restored ?: throw IllegalStateException("No saved playback session")
                pendingRestoredOrder = restored.second
                restored.first
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> = when (customCommand.customAction) {
            CMD_SLEEP_TIMER_SET -> {
                sleepTimer.start(args.getInt(ARG_SLEEP_TIMER_MINUTES, 0))
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_SLEEP_TIMER_CANCEL -> {
                sleepTimer.clear()
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
            CMD_CYCLE_REPEAT -> {
                cycleRepeatMode(session.player)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_TOGGLE_LIKE -> {
                toggleCurrentSongLike(session.player)
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_REFRESH_STREAM -> playbackRecovery.refreshCurrent()
            CMD_SET_PLAYBACK_SPEED -> {
                applyPlaybackSpeed(
                    args.getFloat(ARG_PLAYBACK_SPEED, ThemePreferences.DEFAULT_PLAYBACK_SPEED),
                    persist = args.getBoolean(ARG_PLAYBACK_SPEED_PERSIST, true),
                )
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            CMD_RESTORE_PLAYBACK -> serviceScope.future {
                if (session.player.mediaItemCount == 0) {
                    val restored = withContext(Dispatchers.IO) { loadPlaybackResumption() }
                        ?: return@future SessionResult(SessionResult.RESULT_ERROR_IO)
                    // A second controller can restore while the file read is
                    // suspended. Do not replace a queue that became live in
                    // the meantime (or reset its freshly changed position).
                    if (session.player.mediaItemCount == 0) {
                        pendingRestoredOrder = restored.second
                        session.player.setMediaItems(
                            restored.first.mediaItems,
                            restored.first.startIndex,
                            restored.first.startPositionMs,
                        )
                        session.player.prepare()
                    }
                }
                SessionResult(SessionResult.RESULT_SUCCESS)
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
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD -> {
                // Even a seek to the existing position supersedes a pending
                // skip or overlap; Media3 may emit no discontinuity for it.
                engine.cancelTransition()
                automaticCrossfades.forgetAttempt()
                super.onPlayerCommandRequest(session, controller, playerCommand)
            }
            else -> super.onPlayerCommandRequest(session, controller, playerCommand)
        }

        /** Voice requests, browse ids and external rows; see [RequestedMediaItems]. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = requestedItems.resolve(mediaItems)

        // --- Browsing Logic (Android Auto / Media Browser): see MusicBrowseTree ---

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = browseTree.libraryRoot()

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = browseTree.item(mediaId)

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            browseTree.children(parentId, page, pageSize, params)

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = browseTree.search(session, browser, query, params)

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            browseTree.searchResult(browser, query, page, pageSize, params)

        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            browseTree.onDisconnected(controller)
            super.onDisconnected(session, controller)
        }
    }

    // --- Speed, shuffle and session extras ---

    /**
     * The rate music plays at, 1f being the recorded speed. Held here rather
     * than read back off a player because the crossfade engine rewrites tempo
     * on both of its players throughout a transition, so no single player's
     * value is the user's choice at an arbitrary moment.
     */
    private var playbackSpeed: Float = ThemePreferences.DEFAULT_PLAYBACK_SPEED

    /**
     * Apply the user's playback rate to both engines.
     *
     * @param persist false while a slider is still under the finger. The rate
     *   is applied either way - it is judged by ear, so it has to be audible
     *   as it moves - and written once when the gesture ends, rather than
     *   forty times on the way there.
     */
    private fun applyPlaybackSpeed(speed: Float, persist: Boolean) {
        val bounded = speed.coerceIn(
            ThemePreferences.MIN_PLAYBACK_SPEED,
            ThemePreferences.MAX_PLAYBACK_SPEED,
        )
        playbackSpeed = bounded
        if (::engine.isInitialized) engine.setBaseSpeed(bounded)
        if (persist) themePreferences.setPlaybackSpeed(bounded)
        publishSessionState()
    }

    /**
     * Keep [queueShuffle] in step with a queue edit, and shuffle a whole new
     * queue that arrives while shuffle is on, with its start song first. A
     * restored session brings its own order instead: it is the listener's own
     * listening, so it wins over a fresh shuffle.
     */
    private fun followQueueEdit() {
        val keys = player.occurrenceKeys()
        val restored = pendingRestoredOrder
        if (restored != null) {
            pendingRestoredOrder = null
            queueShuffle.adopt(keys, restored.takeIf { player.shuffleModeEnabled })
            return
        }
        if (queueShuffle.follow(keys) && player.shuffleModeEnabled) {
            arrangeQueue(
                queueShuffle.reshuffle(keys, player.currentMediaItemIndex, isRecent(keys), java.util.Random())
            )
        }
    }

    /**
     * Lay the timeline out as [order] (see [arrangeAs]). The steps on the way
     * are not queue edits. ExoPlayer delivers their events once this returns,
     * and [followQueueEdit] reads the live timeline rather than the event's,
     * so those see the finished order; [arranging] covers any delivered while
     * it is still being built.
     */
    private fun arrangeQueue(order: List<String>) {
        engine.cancelTransition()
        arranging = true
        try {
            player.arrangeAs(order)
        } finally {
            arranging = false
        }
        queueShuffle.follow(player.occurrenceKeys())
    }

    /** Which of [keys] name a song heard lately, for a shuffle that plays those last. */
    private fun isRecent(keys: List<String>): (String) -> Boolean {
        if (recentSongIds.isEmpty()) return { false }
        val mediaIdOf = HashMap<String, String>(keys.size)
        keys.forEachIndexed { index, key -> mediaIdOf[key] = player.getMediaItemAt(index).mediaId }
        return { key -> mediaIdOf[key] in recentSongIds }
    }

    /**
     * Push the service's own state to every connected controller. Session
     * extras are the right channel: they survive the UI being destroyed and
     * rebuilt, so a player reopened ten minutes later still shows the running
     * countdown and the rate it is playing at.
     *
     * One bundle for all of it, because [MediaSession.setSessionExtras]
     * replaces what is there rather than merging into it - a second publisher
     * with its own bundle would silently blank the first one's keys.
     */
    private fun publishSessionState() {
        val session = mediaLibrarySession ?: return
        runCatching {
            session.setSessionExtras(
                Bundle().apply {
                    putLong(EXTRA_SLEEP_TIMER_ENDS_AT, sleepTimer.endsAt)
                    putBoolean(EXTRA_SLEEP_TIMER_END_OF_TRACK, sleepTimer.endOfTrack)
                    putFloat(EXTRA_PLAYBACK_SPEED, playbackSpeed)
                }
            )
        }
    }

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

    /**
     * Recompute [trackGain] for whatever is playing now.
     *
     * @param applyNow whether to push the new gain to the player immediately.
     *   False while a fade owns the volume, since the ramp reads [trackGain]
     *   on every step and would fight a write landing mid-ramp.
     */
    private fun refreshTrackGain(applyNow: Boolean) {
        trackGain = gainForPlayer(player)
        if (applyNow && fadeVolumeJob?.isActive != true) engine.applyIdleVolumes()
    }

    /**
     * A volume writer owns its cleanup as well as its ramp. Publish it before
     * starting so immediate completion and cancellation before the first frame
     * also restore gain. An older job must never overwrite a newer ramp or an
     * engine overlap; restoration reads the live player, not the retired item.
     */
    private fun launchVolumeFade(block: suspend CoroutineScope.() -> Unit) {
        val previous = fadeVolumeJob
        val job = serviceScope.launch(start = CoroutineStart.LAZY, block = block)
        fadeVolumeJob = job
        job.invokeOnCompletion {
            if (fadeVolumeJob === job) {
                fadeVolumeJob = null
                if (serviceScope.isActive) engine.applyIdleVolumes()
            }
        }
        previous?.cancel()
        job.start()
    }

    /**
     * Ease the volume down before pausing.
     *
     * A sleep timer that cuts the audio dead is worse than one that does not
     * fire: the silence is what wakes people. Runs on [fadeVolumeJob] so it and
     * the crossfade fade-in can never drive the volume at the same time.
     */
    private fun fadeOutAndPause(durationMs: Long): Job {
        launchVolumeFade {
            val steps = 60
            for (i in steps - 1 downTo 0) {
                player.volume = trackGain * engine.duckGain * (i / steps.toFloat())
                delay(durationMs / steps)
            }
            player.pause()
            // launchVolumeFade restores the current gain, including ducking.
        }
        return fadeVolumeJob!!
    }

    /** Short fade-in after an item change; queue selection never waits for it. */
    private fun performSkipFadeIn() {
        val target = player
        val occurrence = target.currentMediaItem
        if (occurrence == null) {
            fadeVolumeJob?.cancel()
            fadeVolumeJob = null
            engine.applyIdleVolumes()
            return
        }
        launchVolumeFade {
            val gain = gainForPlayer(target)
            val steps = (SKIP_FADE_MS / 16L).toInt().coerceAtLeast(4)
            for (i in 1..steps) {
                if (player !== target ||
                    target.currentMediaItem?.isSameQueueItemAs(occurrence) != true ||
                    engine.isFading || !isCrossfadeEnabled
                ) return@launchVolumeFade
                val angle = (i.toFloat() / steps) * (Math.PI.toFloat() / 2f)
                target.volume = gain * kotlin.math.sin(angle) * engine.duckGain
                delay(SKIP_FADE_MS / steps)
            }
        }
    }

    /** Resolve every press against the current playback order, including rapid taps. */
    private fun requestManualSkip(
        forward: Boolean,
        restartCurrentOnPrevious: Boolean = true,
    ) {
        val current = player
        if (current.mediaItemCount == 0) return
        val currentIndex = current.currentMediaItemIndex.coerceIn(0, current.mediaItemCount - 1)
        val targetIndex = when {
            !forward && restartCurrentOnPrevious && current.currentPosition > PREVIOUS_RESTART_MS ->
                currentIndex
            current.repeatMode == Player.REPEAT_MODE_ONE ->
                if (forward) currentIndex + 1 else currentIndex - 1
            forward -> current.getNextMediaItemIndex()
            else -> current.getPreviousMediaItemIndex()
        }
        // An intercepted command has no Media3 fallback. At either boundary
        // restart the current occurrence instead of silently discarding intent.
        requestManualTransition(targetIndex.takeIf { it in 0 until current.mediaItemCount } ?: currentIndex)
    }

    /**
     * A user skip must select a track or restart it during this command, never
     * wait for extraction or standby preparation. Each following press then
     * addresses the updated queue. Automatic advancement still crossfades;
     * manual changes can use the ordinary short fade-in from the item listener.
     *
     * There is no suspended manual plan to revive after a seek, natural end or
     * queue replacement. Current-item validation resolves a selected placeholder
     * with its occurrence checks and latest position/intent intact.
     */
    private fun requestManualTransition(targetIndex: Int) {
        val current = player
        if (current.mediaItemCount == 0) return
        val selectedIndex = targetIndex.takeIf { it in 0 until current.mediaItemCount }
            ?: current.currentMediaItemIndex.coerceIn(0, current.mediaItemCount - 1)
        fadeVolumeJob?.cancel()
        engine.cancelTransition()
        automaticCrossfades.forgetAttempt()
        current.seekTo(selectedIndex, 0L)
        current.prepare()
        current.play()
        // The seek can be a no-op, or its item callback can be absent/delayed.
        // Never mute speculatively and depend on that callback to unmute us.
        if (fadeVolumeJob?.isActive != true) engine.applyIdleVolumes()
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
        automaticCrossfades.forgetAttempt()
        // Let a failure reach CrossfadeEngine's rollback. Swallowing it would
        // retire the old player while the session still points at that player.
        mediaLibrarySession?.setPlayer(SteadyDurationPlayer(newActive))
        trackGain = gainForPlayer(newActive)
        engine.setPauseAtEndOfMediaItems(sleepTimer.endOfTrack)
        prefetchUpcomingSongs()
        newActive.currentMediaItem?.let { autoMixProfiling.profile(it, newActive.duration) }
        // The incoming player was handed its item directly, so no transition
        // callback records the new song, and none of its earlier events
        // reached the listener either: a swap made by a pause arrives with the
        // incoming player already paused.
        sessionCheckpoints.checkpoint()
        publishWidgetState()
    }

    
    /**
     * Snapshot the active player for the home screen widgets. Main thread only
     * - every caller is already a player callback or the progress loop, both of
     * which run there.
     */
    private fun publishWidgetState() {
        val snapshot = player.toWidgetSnapshot()
        runCatching { PlayerWidgets.publish(this, snapshot) }
            .onFailure { KLog.w(TAG, "Widget publish failed: ${it.message}") }
        // Discord Rich Presence reads the same snapshot; it never touches playback.
        runCatching { DiscordPresenceManager.onSnapshot(snapshot) }
    }

}
