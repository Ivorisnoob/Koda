package com.ivor.ivormusic.ui.player

import com.ivor.ivormusic.util.KLog

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.MusicQueueItem
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.LyricsRepository
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.service.MusicService
import com.ivor.ivormusic.service.EXTRA_QUEUE_ITEM_ID
import com.ivor.ivormusic.service.toPlaybackMediaItem
import com.ivor.ivormusic.ui.video.CastPlaybackKind
import com.ivor.ivormusic.ui.video.CastRoute
import com.ivor.ivormusic.ui.video.VideoCastManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@UnstableApi
class PlayerViewModel(private val context: Context) : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = try {
            if (controllerFuture?.isDone == true) controllerFuture?.get() else null
        } catch (e: Exception) {
            // Future may have completed exceptionally if the service connection
            // failed (e.g. onGetSession returned null during a teardown race).
            KLog.w("PlayerViewModel", "controller getter: failed future", e)
            null
        }
    private var connectRetryAttempts = 0

    /**
     * A tap can beat the asynchronous MediaController connection on cold
     * start. Keep only the latest request: a second tap means the user changed
     * their mind, and replaying both after connection would flash the wrong
     * song before landing on the right one.
     */
    private data class PendingPlayRequest(
        val queue: List<MusicQueueItem>,
        val startIndex: Int
    )

    private var pendingPlayRequest: PendingPlayRequest? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    /**
     * Snapshot the current queue, index, and position for resume-on-reopen.
     * Controller state is read on the caller (main) thread; the file write
     * goes to IO.
     */
    private fun savePlaybackSession() {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return
        val index = controller?.currentMediaItemIndex
            ?.takeIf { it in queue.indices }
            ?: currentIndexInQueue()
        val position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playbackSessionRepository.save(queue, index, position)
        }
    }

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<MusicQueueItem>>(emptyList())
    val currentQueue: StateFlow<List<MusicQueueItem>> = _currentQueue.asStateFlow()

    private val _currentQueueItemId = MutableStateFlow<String?>(null)
    val currentQueueItemId: StateFlow<String?> = _currentQueueItemId.asStateFlow()

    // Stats tracking
    private var lastRecordedSongId: String? = null
    private var playRecordingJob: Job? = null

    // In-flight radio fill for the last playSongRadio() seed
    private var radioJob: Job? = null
    private var radioSeedId: String? = null
    
    // Flag to prevent listener from restoring song after clear
    private var isPlayerCleared = false
    
    // Liked songs functionality
    private val likedSongsRepository = LikedSongsRepository(context)
    
    private val _isCurrentSongLiked = MutableStateFlow(false)
    val isCurrentSongLiked: StateFlow<Boolean> = _isCurrentSongLiked.asStateFlow()
    
    val likedSongIds: StateFlow<Set<String>> = likedSongsRepository.likedSongIds
    
    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository.getInstance(context)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // YouTube Repository for fetching more songs
    private val youTubeRepository = com.ivor.ivormusic.data.YouTubeRepository(context)

    // Taste-profile based recommendations for the auto-queue
    private val recommendationEngine = com.ivor.ivormusic.data.RecommendationEngine(context, youTubeRepository)

    // Loading state for "Load More" button
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    // Lyrics Repository and State
    private val lyricsRepository = LyricsRepository(context)
    
    // Stats Repository
    private val statsRepository = com.ivor.ivormusic.data.StatsRepository(context)

    // Playback session snapshots for resume-on-reopen
    private val playbackSessionRepository = com.ivor.ivormusic.data.PlaybackSessionRepository(context)

    private val _lyricsResult = MutableStateFlow<LyricsResult>(LyricsResult.Loading)
    val lyricsResult: StateFlow<LyricsResult> = _lyricsResult.asStateFlow()
    
    // Playlist Repository (Local Playlists)
    private val playlistRepository = com.ivor.ivormusic.data.PlaylistRepository(context)

    private val _localPlaylists = playlistRepository.userPlaylists
    val localPlaylists: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> =
        _localPlaylists.map { list ->
            list.map { it.toDisplayItem() }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // YouTube playlists songs can be added to (loaded on demand when the
    // Add to Playlist sheet opens; only real "PL..." playlists are editable,
    // not the synthesized Supermix/Likes entries)
    private val _youtubeAddablePlaylists =
        MutableStateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList())

    /** Local playlists followed by editable YouTube playlists, for the Add to Playlist sheet. */
    val addToPlaylistItems: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> =
        kotlinx.coroutines.flow.combine(localPlaylists, _youtubeAddablePlaylists) { local, youtube ->
            local + youtube
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    /** Fetch the user's YouTube playlists for the Add to Playlist sheet (once per session). */
    fun loadYouTubePlaylistsForSheet() {
        if (_youtubeAddablePlaylists.value.isNotEmpty() || !youTubeRepository.isLoggedIn()) return
        viewModelScope.launch {
            _youtubeAddablePlaylists.value = youTubeRepository.getUserPlaylists()
                .filter { it.id.startsWith("PL") }
        }
    }
        
    // Cache & Crossfade Settings exposed for UI
    private val themePreferences = com.ivor.ivormusic.data.ThemePreferences(context)
    val cacheEnabled = themePreferences.cacheEnabled
    val maxCacheSizeMb = themePreferences.maxCacheSizeMb
    val currentCacheSize = com.ivor.ivormusic.data.CacheManager.currentCacheSizeBytes
    
    val crossfadeEnabled = themePreferences.crossfadeEnabled
    val crossfadeDurationMs = themePreferences.crossfadeDurationMs

    // Discovery lives with the screen; playback hand-off lives in MusicService.
    // Both managers join the same framework session and are separated by the
    // process-wide MUSIC/VIDEO ownership marker in VideoCastManager.
    private val castManager = VideoCastManager(context, CastPlaybackKind.MUSIC)
    val castAvailable: Boolean get() = castManager.available
    val castReceivers: StateFlow<List<CastRoute>> = castManager.receivers
    val castDeviceName: StateFlow<String?> = castManager.deviceName
    val isCastConnecting: StateFlow<Boolean> = castManager.isConnecting
    val isCasting: StateFlow<Boolean> = castManager.isSessionActive
    private val _castUnavailableMessage = MutableStateFlow<String?>(null)
    val castUnavailableMessage: StateFlow<String?> = _castUnavailableMessage.asStateFlow()

    init {
        castManager.beginObservation()
        initializeController()
        startProgressUpdates()
        startBufferingWatchdog()
    }

    /**
     * Global buffering watchdog: whenever the spinner has been showing for 30s
     * without playback starting, clear it. Covers every path that sets
     * _isBuffering (playQueue, skip, auto-advance) so a failed resolution can
     * never leave the UI on an eternal loading state.
     */
    private fun startBufferingWatchdog() {
        viewModelScope.launch {
            _isBuffering.collectLatest { buffering ->
                if (buffering) {
                    delay(30_000)
                    if (_isBuffering.value && !_isPlaying.value) {
                        KLog.w("PlayerViewModel", "Buffering watchdog: clearing stuck state")
                        _isBuffering.value = false
                    }
                }
            }
        }
    }
    
    /**
     * Restore the previous playback session on cold start: the full queue,
     * the song that was playing, and the position inside it — paused, so the
     * user decides when to jump back in. Falls back to the legacy single-song
     * restore when no session snapshot exists.
     */
    private fun restoreLastSession() {
        // Only restore if there's no current song and no items in the controller
        if (_currentSong.value != null) return
        if ((controller?.mediaItemCount ?: 0) > 0) return

        viewModelScope.launch {
            val session = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                playbackSessionRepository.load()
            }
            // Re-check: playback may have started while the file was read
            if (_currentSong.value != null) return@launch
            if ((controller?.mediaItemCount ?: 0) > 0) return@launch

            if (session == null) {
                restoreLastPlayedSong()
                return@launch
            }

            val queueItem = session.queue[session.currentIndex]
            val song = queueItem.song
            KLog.d(
                "PlayerViewModel",
                "Restoring session: ${session.queue.size} songs, index=${session.currentIndex}, pos=${session.positionMs}"
            )

            _currentQueue.value = session.queue
            _currentQueueItemId.value = queueItem.id
            _currentSong.value = song
            _progress.value = session.positionMs
            if (song.duration > 0) _duration.value = song.duration
            updateCurrentSongLikedStatus()

            controller?.let { player ->
                val items = session.queue.map { createMediaItem(it) }
                player.setMediaItems(items, session.currentIndex, session.positionMs)
                player.prepare()
            }

            fetchLyrics(song)
        }
    }

    /**
     * Legacy fallback restore (pre-session snapshots): last played song only,
     * from preferences.
     */
    private fun restoreLastPlayedSong() {
        val song = themePreferences.getLastPlayedSong() ?: return

        KLog.d("PlayerViewModel", "Restoring last played song: ${song.title}")

        // Set the current song for UI display
        val queueItem = MusicQueueItem(song = song)
        _currentSong.value = song
        _currentQueue.value = listOf(queueItem)
        _currentQueueItemId.value = queueItem.id

        // Prepare the song in the player (but don't auto-play)
        val mediaItem = createMediaItem(queueItem)
        controller?.setMediaItem(mediaItem)
        controller?.prepare()

        // Fetch lyrics for this song
        fetchLyrics(song)
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, sessionToken)
            // The sleep timer runs in the service and reports back through the
            // session's extras, which arrive on MediaController.Listener rather
            // than on the Player.Listener installed below.
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(
                    controller: MediaController,
                    extras: android.os.Bundle
                ) {
                    applySleepTimerExtras(extras)
                }
            })
            .buildAsync()
        controllerFuture = future

        future.addListener({
            val ctrl = try {
                future.get()
            } catch (e: Exception) {
                // "Session not found" / connection rejected — usually a race during
                // service teardown after the app was swiped away. Retry a couple of
                // times with backoff so the next time the user opens the app the
                // controller binds cleanly instead of leaving the UI dead.
                KLog.w("PlayerViewModel", "MediaController connect failed: ${e.message}")
                // Release the failed future before scheduling a retry so we don't
                // leak it — Media3 requires every buildAsync() future to be released
                // exactly once, and initializeController() will overwrite the field.
                MediaController.releaseFuture(future)
                controllerFuture = null
                if (connectRetryAttempts < 3) {
                    connectRetryAttempts++
                    viewModelScope.launch {
                        delay(300L * connectRetryAttempts)
                        initializeController()
                    }
                }
                return@addListener
            }
            connectRetryAttempts = 0

            // SYNC EXISTING SESSION STATE
            // This runs when we reconnect to an already-playing session
            syncStateFromController(ctrl)

            ctrl.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    // Clear buffering state when playback actually starts
                    if (isPlaying) {
                        _isBuffering.value = false
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _playWhenReady.value = playWhenReady
                    // Only set buffering if we're actively in BUFFERING state.
                    // Avoid setting it for IDLE — playQueue() already handles that,
                    // and re-setting here causes races where buffering flag gets stuck.
                    if (playWhenReady && !controller!!.isPlaying) {
                        val state = controller?.playbackState ?: Player.STATE_IDLE
                        if (state == Player.STATE_BUFFERING) {
                            _isBuffering.value = true
                        }
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            _isBuffering.value = true
                        }
                        Player.STATE_READY -> {
                            // Only clear buffering if we were actually playing or about to
                            _isBuffering.value = false
                            // Only set duration if it's a valid positive value
                            val dur = controller?.duration ?: 0L
                            if (dur > 0) {
                                _duration.value = dur
                            }
                        }
                        Player.STATE_ENDED -> {
                            _isBuffering.value = false
                        }
                        Player.STATE_IDLE -> {
                            // Don't aggressively set buffering here.
                            // playQueue() already sets _isBuffering = true before calling prepare().
                            // Setting it again here causes race conditions with STATE_READY
                            // especially for local songs that transition through IDLE->READY
                            // almost instantly.
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    KLog.e("PlayerViewModel", "Playback error: ${error.errorCodeName}", error)
                    // MusicService retries and skips on its own; if it recovers,
                    // the player re-enters BUFFERING and the flag comes back.
                    // Clearing here guarantees the spinner can't outlive a
                    // playback that is never going to start.
                    _isBuffering.value = false
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // If we just cleared the player, don't restore from this callback
                    if (isPlayerCleared) {
                        KLog.d("PlayerViewModel", "Ignoring media transition - player was cleared")
                        return
                    }

                    // A crossfade swaps the MediaSession onto an incoming
                    // player that is already STATE_READY, so there may be no
                    // later READY callback to refresh these values. Publish
                    // the new timeline values at the item boundary instead of
                    // leaving the previous song's duration on screen.
                    val transitionedDuration = controller?.duration?.takeIf { it > 0L }
                        ?: mediaItem?.mediaMetadata?.durationMs?.takeIf { it > 0L }
                        ?: 0L
                    _duration.value = transitionedDuration
                    _progress.value = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    
                    // The controller's index identifies the exact queue
                    // occurrence. mediaId only identifies the underlying song
                    // and is ambiguous when the same track appears twice.
                    val id = mediaItem?.mediaId
                    val currentIndex = controller?.currentMediaItemIndex ?: -1
                    // Metadata carries the occurrence ID through placeholder
                    // resolution and crossfade player swaps. Prefer it over
                    // the timeline index so a briefly drifted duplicate cannot
                    // be mistaken for another copy of the same song.
                    val mediaQueueItemId = mediaItem?.mediaMetadata?.extras
                        ?.getString(EXTRA_QUEUE_ITEM_ID)
                    var queueItem = mediaQueueItemId
                        ?.let { queueItemId -> _currentQueue.value.find { it.id == queueItemId } }
                        ?: _currentQueue.value.getOrNull(currentIndex)
                            ?.takeIf { id.isNullOrEmpty() || it.song.id == id }

                    var song: Song? = queueItem?.song
                    
                    // If still null, try to reconstruct from MediaItem metadata
                    if (song == null && mediaItem != null) {
                        song = extractSongFromMediaItem(mediaItem)
                    }
                    
                    song?.let {
                        _currentQueueItemId.value = queueItem?.id
                        _currentSong.value = it
                        updateCurrentSongLikedStatus()
                        fetchLyrics(it)
                        
                        // Save as last played song for restoration
                        themePreferences.saveLastPlayedSong(it)
                        savePlaybackSession()

                        // STATS RECORDING WITH THRESHOLD
                        // Cancel previous job if any
                        playRecordingJob?.cancel()
                        
                        // Sync history with YouTube and Local Stats
                        // CRITICAL: Only record play if it's a new song or a deliberate repeat/auto-next.
                        // We filter out transitions caused by Media Item Replacement (Resolution) 
                        // by checking if the ID actually changed.
                        val isResolutionTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && it.id == lastRecordedSongId
                        
                        if (!isResolutionTransition) {
                            val currentSongId = it.id
                            playRecordingJob = viewModelScope.launch {
                                // Wait for 15 seconds of playback before counting as a 'play'
                                // This prevents skips and resolution changes from inflating stats.
                                delay(15_000)
                                // A cold-start session restore fires this transition too but
                                // never plays; only count it once playback actually ran.
                                if (isActive && (_isPlaying.value || controller?.playWhenReady == true)) {
                                    lastRecordedSongId = currentSongId
                                    youTubeRepository.reportPlayback(currentSongId)
                                    // Fresh pref read: the toggle is flipped
                                    // from the settings screen and from the
                                    // history screen's own menu, both holding
                                    // their own ThemePreferences, so this VM's
                                    // StateFlow copy is stale at decision time.
                                    if (themePreferences.isSaveMusicHistoryEnabled()) {
                                        statsRepository.addPlayEvent(it)
                                    }
                                }
                            }
                        }
                        
                        // AUTO-QUEUE: top the queue up with recommendations when
                        // fewer than 5 songs are left after the current one.
                        // Fresh pref read: the settings screen toggles through
                        // its own ThemePreferences instance, so this VM's
                        // StateFlow copy is stale at decision time.
                        val totalItems = controller?.mediaItemCount ?: 0
                        val currentIndex = controller?.currentMediaItemIndex ?: 0
                        val songsLeft = totalItems - currentIndex - 1

                        if (songsLeft < 5 && !_isLoadingMore.value &&
                            themePreferences.isAutoLoadQueueEnabled()
                        ) {
                             KLog.d("PlayerViewModel", "Auto-Queue: $songsLeft songs left, loading more...")
                             loadMoreRecommendations()
                        }
                    }
                }
            })

            // A user tap wins over cold-start restoration. Previously a tap
            // made during controller connection updated the mini player but
            // silently lost the actual play command, leaving the song paused
            // until Play was tapped a second time.
            pendingPlayRequest?.let { pending ->
                pendingPlayRequest = null
                playQueueItems(pending.queue, pending.startIndex)
            } ?: restoreLastSession()
        }, MoreExecutors.directExecutor())
    }
    
    /**
     * Sync UI state from an already-connected MediaController.
     * Called when the app reconnects to a session that's already playing.
     */
    private fun syncStateFromController(ctrl: MediaController) {
        // Sync playback state
        _isPlaying.value = ctrl.isPlaying
        _playWhenReady.value = ctrl.playWhenReady
        _isBuffering.value = ctrl.playbackState == Player.STATE_BUFFERING
        _duration.value = if (ctrl.duration > 0) ctrl.duration else 0L
        _progress.value = ctrl.currentPosition
        _shuffleModeEnabled.value = ctrl.shuffleModeEnabled
        _repeatMode.value = ctrl.repeatMode
        
        // Rebuild queue from MediaSession
        val itemCount = ctrl.mediaItemCount
        if (itemCount > 0 && _currentQueue.value.isEmpty()) {
            val queueItems = mutableListOf<MusicQueueItem>()
            for (i in 0 until itemCount) {
                val mediaItem = ctrl.getMediaItemAt(i)
                extractSongFromMediaItem(mediaItem)?.let { song ->
                    queueItems.add(
                        MusicQueueItem(
                            id = mediaItem.mediaMetadata.extras
                                ?.getString(EXTRA_QUEUE_ITEM_ID)
                                ?: java.util.UUID.randomUUID().toString(),
                            song = song
                        )
                    )
                }
            }
            if (queueItems.isNotEmpty()) {
                _currentQueue.value = queueItems
            }
        }
        
        // Sync current song
        val currentMediaItem = ctrl.currentMediaItem
        if (currentMediaItem != null && _currentSong.value == null) {
            val currentItem = currentMediaItem.mediaMetadata.extras
                ?.getString(EXTRA_QUEUE_ITEM_ID)
                ?.let { id -> _currentQueue.value.find { it.id == id } }
                ?: _currentQueue.value.getOrNull(ctrl.currentMediaItemIndex)
                    ?.takeIf { it.song.id == currentMediaItem.mediaId }
            var song = currentItem?.song
            if (song == null) {
                song = extractSongFromMediaItem(currentMediaItem)
            }
            song?.let {
                _currentQueueItemId.value = currentItem?.id
                _currentSong.value = it
                updateCurrentSongLikedStatus()
                fetchLyrics(it)
            }
        }
        
        KLog.d("PlayerViewModel", "Synced state: playing=${_isPlaying.value}, song=${_currentSong.value?.title}, queue=${_currentQueue.value.size} items")
    }
    
    /**
     * Extract a Song object from a MediaItem's metadata.
     */
    private fun extractSongFromMediaItem(mediaItem: MediaItem): Song? {
        val metadata = mediaItem.mediaMetadata
        val id = mediaItem.mediaId
        if (id.isEmpty()) return null
        
        // Detect source from the URI scheme — local songs use content:// or file://
        val uri = mediaItem.localConfiguration?.uri
        val isLocal = uri != null && (uri.scheme == "content" || uri.scheme == "file")
        
        return if (isLocal) {
            Song(
                id = id,
                title = metadata.title?.toString() ?: "Unknown",
                artist = metadata.artist?.toString() ?: "Unknown Artist",
                album = metadata.albumTitle?.toString() ?: "",
                duration = metadata.durationMs ?: 0L,
                uri = uri,
                albumArtUri = metadata.artworkUri,
                source = com.ivor.ivormusic.data.SongSource.LOCAL
            )
        } else {
            Song(
                id = id,
                title = metadata.title?.toString() ?: "Unknown",
                artist = metadata.artist?.toString() ?: "Unknown Artist",
                album = metadata.albumTitle?.toString() ?: "",
                duration = metadata.durationMs ?: 0L,
                thumbnailUrl = metadata.artworkUri?.toString(),
                source = com.ivor.ivormusic.data.SongSource.YOUTUBE
            )
        }
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            var lastPosition = 0L
            var ticksSinceSave = 0
            while (isActive) {
                controller?.let {
                    val currentPos = it.currentPosition

                    // Periodic session snapshot so a swipe-away or process
                    // death loses at most a few seconds of position
                    if (it.isPlaying) {
                        ticksSinceSave++
                        if (ticksSinceSave >= 15) {
                            ticksSinceSave = 0
                            savePlaybackSession()
                        }
                    }
                    
                    // Only update progress if it's a valid non-negative value
                    if (currentPos >= 0) {
                        _progress.value = currentPos
                    }
                    
                    // Keep duration tied to the active player. Crossfade swaps
                    // between already-ready players, so this is also a
                    // backstop if a controller misses the item callback.
                    val dur = it.duration
                    if (dur > 0 && _duration.value != dur) {
                        _duration.value = dur
                    }
                    
                    // Update buffering sanity check
                    if (it.isPlaying) {
                        // Failsafe: if we are playing and updating progress, we are NOT buffering
                        if (_isBuffering.value) {
                             _isBuffering.value = false
                        }
                    }
                    
                    lastPosition = currentPos
                }
                delay(1000)
            }
        }
    }

    fun playSong(song: Song) {
        playQueue(listOf(song))
    }

    fun playQueue(songs: List<Song>, startSong: Song? = null) {
        if (songs.isEmpty()) return

        val queue = songs.map { MusicQueueItem(song = it) }
        // Callers pass the selected object from the displayed list. Reference
        // identity preserves the selected occurrence when a playlist contains
        // the same Song value twice; song ID is the compatibility fallback.
        val startIndex = when (startSong) {
            null -> 0
            else -> songs.indexOfFirst { it === startSong }
                .takeIf { it >= 0 }
                ?: songs.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)
        }
        playQueueItems(queue, startIndex)
    }

    private fun playQueueItems(queue: List<MusicQueueItem>, startIndex: Int) {
        if (queue.isEmpty()) return

        val requestedIndex = startIndex.coerceIn(queue.indices)
        val requestedItem = queue[requestedIndex]
        if (isCasting.value &&
            requestedItem.song.source == com.ivor.ivormusic.data.SongSource.LOCAL
        ) {
            val message = "Disconnect Cast to play files stored on this phone"
            _castUnavailableMessage.value = message
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            return
        }

        // The receiver cannot fetch content:// or file://. Remove local entries
        // from both the controller timeline and the UI queue so their indices
        // cannot drift after MusicService filters the same items.
        val playbackQueue = if (isCasting.value) {
            queue.filter { it.song.source != com.ivor.ivormusic.data.SongSource.LOCAL }
        } else {
            queue
        }
        val safeStartIndex = playbackQueue.indexOfFirst { it.id == requestedItem.id }
            .coerceAtLeast(0)

        // Reset cleared flag - user is actively playing
        isPlayerCleared = false

        // A song removed from the queue that is being replaced is no longer
        // undoable: putting it back would drop it into a queue it was never in.
        lastQueueRemoval = null

        _currentQueue.value = playbackQueue
        
        // Update current song immediately for UI responsiveness
        val currentItem = playbackQueue[safeStartIndex]
        val currentSong = currentItem.song
        _currentQueueItemId.value = currentItem.id
        _currentSong.value = currentSong
        _isBuffering.value = true // Immediately show loading
        _duration.value = 0L // Reset duration until we load the new song
        updateCurrentSongLikedStatus()
        fetchLyrics(currentSong)
        
        val player = controller
        if (player == null) {
            pendingPlayRequest = PendingPlayRequest(playbackQueue, safeStartIndex)
            return
        }
        pendingPlayRequest = null
        player.let {
            // 1. Set the target song first (triggers URL resolution in MusicService)
            val startItem = createMediaItem(currentItem, castResolveNow = true)
            it.setMediaItem(startItem)
            
            // 2. Add the rest of the queue BEFORE prepare (so notification sees full queue)
            val otherItemsBefore = playbackQueue.subList(0, safeStartIndex).map { createMediaItem(it) }
            val otherItemsAfter = playbackQueue
                .subList(safeStartIndex + 1, playbackQueue.size)
                .map { createMediaItem(it) }
            
            if (otherItemsBefore.isNotEmpty()) {
                it.addMediaItems(0, otherItemsBefore)
            }
            if (otherItemsAfter.isNotEmpty()) {
                // Start item is now at index otherItemsBefore.size
                it.addMediaItems(otherItemsBefore.size + 1, otherItemsAfter)
            }
            
            // 3. NOW prepare and play - notification will see complete queue
            // (the buffering watchdog in init covers the stuck-spinner case)
            it.prepare()
            it.play()
        }
    }
    
    /**
     * Start a radio from [song]: play it right away, then fill the queue with
     * YouTube's related-songs mix for that track (the same RDAMVM radio
     * YouTube Music autoplays into).
     *
     * This is the right behaviour for a one-off tap — a search result or a
     * pasted link — where the surrounding list is a set of same-titled matches
     * rather than a real playlist, so queueing it means hearing the same song
     * six times from six uploaders.
     *
     * Local songs have no radio to fetch, so they just play on their own; list
     * playback for those still goes through [playQueue].
     */
    fun playSongRadio(song: Song) {
        if (song.source != com.ivor.ivormusic.data.SongSource.YOUTUBE) {
            playSong(song)
            return
        }

        playQueue(listOf(song))

        radioJob?.cancel()
        // Claim the auto-queue slot synchronously: the media-item transition
        // that playQueue() just triggered lands on the main thread after this
        // returns and would otherwise fire its own continuation fetch for the
        // same seed.
        _isLoadingMore.value = true
        radioSeedId = song.id
        radioJob = viewModelScope.launch {
            try {
                var radio = youTubeRepository.getRelatedSongs(song.id)
                    .filter { it.id != song.id }

                // Radio came back empty (no /next mix, or the call failed):
                // fall back to the taste-profile continuation so the user
                // isn't left with a one-song queue.
                if (radio.isEmpty()) {
                    radio = recommendationEngine.getQueueContinuation(
                        currentSong = song,
                        excludeIds = setOf(song.id),
                        limit = 20
                    )
                }

                // Only extend if the user is still on this radio — a tap on
                // something else while /next was in flight must not graft the
                // old mix onto the new queue.
                val queue = _currentQueue.value
                if (radio.isNotEmpty() && queue.size == 1 && queue[0].song.id == song.id) {
                    addToQueue(radio)
                }
            } catch (e: Exception) {
                KLog.e("PlayerViewModel", "Radio fetch failed for ${song.id}", e)
            } finally {
                // A cancelled predecessor must not release the flag it no
                // longer owns — only the current seed clears it.
                if (radioSeedId == song.id) _isLoadingMore.value = false
            }
        }
    }

    /**
     * Jump to a song that is already in the queue without rebuilding the
     * player's timeline, so buffered and prefetched data is kept.
     * Falls back to [playQueue] if the song isn't in the queue.
     */
    fun skipToSong(song: Song) {
        val queue = _currentQueue.value
        val index = queue.indexOfFirst { it.song === song }
            .takeIf { it >= 0 }
            ?: queue.indexOfFirst { it.song.id == song.id }
        if (index < 0) {
            playQueue(listOf(song), song)
            return
        }
        skipToQueueItem(index)
    }

    /** Jump to one exact queue occurrence, even when its song appears twice. */
    fun skipToQueueItem(queueItemId: String) {
        val index = _currentQueue.value.indexOfFirst { it.id == queueItemId }
        if (index >= 0) skipToQueueItem(index)
    }

    /**
     * Seek the player to the queue item at [index] and start playback.
     */
    fun skipToQueueItem(index: Int) {
        val queue = _currentQueue.value
        val queueItem = queue.getOrNull(index) ?: return
        val song = queueItem.song
        val player = controller
        if (player == null) {
            playQueueItems(queue, index)
            return
        }

        // Guard: if the player's timeline drifted from the UI queue, rebuild.
        if (!timelineAgreesAt(index)) {
            playQueueItems(queue, index)
            return
        }

        if (index == player.currentMediaItemIndex) {
            player.play()
            return
        }

        isPlayerCleared = false

        // Update UI state immediately for responsiveness (same as playQueue)
        _currentQueueItemId.value = queueItem.id
        _currentSong.value = song
        // The outgoing track remains audible while the target prepares, so
        // this is not a buffering state and must not show a spinner.
        _isBuffering.value = false
        _duration.value = 0L
        updateCurrentSongLikedStatus()
        fetchLyrics(song)

        sendSkipCommand(MusicService.CMD_SKIP_TO_INDEX, index)
    }

    /**
     * Load more recommendations from YouTube Music and add to queue.
     */
    fun loadMoreRecommendations() {
        if (_isLoadingMore.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                // Related-songs radio for the current track, falling back to
                // seeds from the user's local taste profile (top artists).
                val newSongs = recommendationEngine.getQueueContinuation(
                    currentSong = _currentSong.value,
                    excludeIds = _currentQueue.value.map { it.song.id }.toSet(),
                    limit = 10
                )

                if (newSongs.isNotEmpty()) {
                    addToQueue(newSongs)
                }
            } catch (e: Exception) {
                KLog.e("PlayerViewModel", "Could not extend the music queue", e)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return

        val acceptedSongs = songsForCurrentOutput(songs)
        if (acceptedSongs.isEmpty()) return
        val added = acceptedSongs.map { MusicQueueItem(song = it) }
        val currentList = _currentQueue.value.toMutableList()
        currentList.addAll(added)
        _currentQueue.value = currentList

        controller?.let { player ->
            val newItems = added.map { createMediaItem(it) }
            player.addMediaItems(newItems)
        }
        savePlaybackSession()
    }

    /**
     * Put [songs] straight after whatever is playing.
     *
     * The other half of "add to queue", and the one people reach for more: it
     * is the difference between "I want this next" and "I want this eventually".
     * With nothing playing there is no "after", so it starts playback instead of
     * quietly building a queue nobody asked to hear.
     */
    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        val acceptedSongs = songsForCurrentOutput(songs)
        if (acceptedSongs.isEmpty()) return
        val currentList = _currentQueue.value
        if (currentList.isEmpty() || _currentSong.value == null) {
            playQueue(acceptedSongs)
            return
        }

        val player = controller
        val insertAt = ((player?.currentMediaItemIndex ?: currentIndexInQueue()) + 1)
            .coerceIn(0, currentList.size)

        val added = acceptedSongs.map { MusicQueueItem(song = it) }
        _currentQueue.value = currentList.toMutableList().apply { addAll(insertAt, added) }
        // The timeline can be shorter than the UI queue when the two have
        // drifted, and Media3 throws rather than clamping an out-of-range
        // insert. Append in that case; the order is wrong either way, and the
        // next explicit jump rebuilds the timeline from the queue.
        player?.let {
            it.addMediaItems(
                insertAt.coerceAtMost(it.mediaItemCount),
                added.map { createMediaItem(it) }
            )
        }
        savePlaybackSession()
    }

    fun playNext(song: Song) = playNext(listOf(song))

    fun addToQueue(song: Song) = addToQueue(listOf(song))

    private fun songsForCurrentOutput(songs: List<Song>): List<Song> {
        if (!isCasting.value) return songs
        val accepted = songs.filter {
            it.source != com.ivor.ivormusic.data.SongSource.LOCAL
        }
        if (accepted.size != songs.size) {
            val message = "Files stored on this phone were left out of the Cast queue"
            _castUnavailableMessage.value = message
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
        return accepted
    }

    /** Where the playing song sits in [_currentQueue], or 0 if it is not in it. */
    private fun currentIndexInQueue(): Int {
        val queueItemId = _currentQueueItemId.value
        if (queueItemId != null) {
            val index = _currentQueue.value.indexOfFirst { it.id == queueItemId }
            if (index >= 0) return index
        }
        val songId = _currentSong.value?.id ?: return 0
        return _currentQueue.value.indexOfFirst { it.song.id == songId }.coerceAtLeast(0)
    }

    /**
     * True when the player's timeline still agrees with [_currentQueue] at
     * [index].
     *
     * The two can drift - a failed resolution, a session restored underneath
     * us - and [skipToQueueItem] has always checked before seeking. A move or a
     * remove that does not check is worse than a seek that does not: it edits
     * the wrong song and leaves the queue and the timeline further apart than
     * it found them.
     */
    private fun timelineAgreesAt(index: Int): Boolean {
        val player = controller ?: return false
        val queueItem = _currentQueue.value.getOrNull(index) ?: return false
        if (index >= player.mediaItemCount) return false
        val mediaItem = player.getMediaItemAt(index)
        val mediaQueueItemId = mediaItem.mediaMetadata.extras?.getString(EXTRA_QUEUE_ITEM_ID)
        return mediaItem.mediaId == queueItem.song.id &&
            (mediaQueueItemId == null || mediaQueueItemId == queueItem.id)
    }

    /**
     * Move a queue item.
     *
     * [persist] is false for every step of a drag: a reorder crosses several
     * positions on the way to where the finger is going, and writing the whole
     * session to disk on each one turns a smooth gesture into stutter. The
     * drag calls [commitQueueOrder] once when the finger lifts.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int, persist: Boolean = true) {
        val currentList = _currentQueue.value
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return

        val agrees = timelineAgreesAt(fromIndex)

        val mutable = currentList.toMutableList()
        val movedItem = mutable.removeAt(fromIndex)
        mutable.add(toIndex, movedItem)
        _currentQueue.value = mutable

        // Only touch the timeline when it was in step to begin with. Out of
        // step, the UI list is the one the user is looking at and the player
        // will be rebuilt from it on the next explicit jump.
        if (agrees) controller?.moveMediaItem(fromIndex, toIndex)
        if (persist) savePlaybackSession()
    }

    /** Save once, after a drag has settled. */
    fun commitQueueOrder() {
        savePlaybackSession()
    }

    /**
     * Take a song out of the queue.
     *
     * The last song stays: an empty queue with a song still playing is a state
     * nothing else in the app knows how to draw. Removing what is currently
     * playing is allowed, and Media3 advances to the next item on its own,
     * which is what every other player does.
     */
    fun removeQueueItem(index: Int) {
        val currentList = _currentQueue.value
        if (index !in currentList.indices) return
        if (currentList.size <= 1) return

        val agrees = timelineAgreesAt(index)
        lastQueueRemoval = QueueRemoval(currentList[index], index)

        val mutable = currentList.toMutableList()
        mutable.removeAt(index)
        _currentQueue.value = mutable

        if (agrees) controller?.removeMediaItem(index)
        savePlaybackSession()
    }

    /** Remove one exact queue occurrence without relying on a stale UI index. */
    fun removeQueueItem(queueItemId: String) {
        val index = _currentQueue.value.indexOfFirst { it.id == queueItemId }
        if (index >= 0) removeQueueItem(index)
    }

    /** A song just taken out of the queue, kept so the snackbar can put it back. */
    data class QueueRemoval(val item: MusicQueueItem, val index: Int)

    private var lastQueueRemoval: QueueRemoval? = null

    /**
     * Put the last removed song back where it was.
     *
     * Restoring at the recorded index rather than appending, because "undo"
     * that drops the song at the end of the queue has not undone anything the
     * user can see.
     */
    fun undoQueueRemoval() {
        val removal = lastQueueRemoval ?: return
        lastQueueRemoval = null
        val currentList = _currentQueue.value
        val at = removal.index.coerceIn(0, currentList.size)

        _currentQueue.value = currentList.toMutableList().apply { add(at, removal.item) }
        controller?.let { player ->
            if (at <= player.mediaItemCount) {
                player.addMediaItem(at, createMediaItem(removal.item))
            }
        }
        savePlaybackSession()
    }

    private fun createMediaItem(
        queueItem: MusicQueueItem,
        castResolveNow: Boolean = false
    ): MediaItem = queueItem.toPlaybackMediaItem(castResolveNow)

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
                // Pausing is a natural leave point; pin the exact position
                savePlaybackSession()
            } else {
                it.play()
            }
        }
    }

    /**
     * Pause music playback without touching the queue. Also cancels a pending
     * playWhenReady while a track is still buffering, so a song that finishes
     * resolving after a video started does not begin playing over it.
     */
    fun pause() {
        controller?.pause()
        savePlaybackSession()
    }

    fun toggleShuffle() {
        controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleRepeat() {
        controller?.let {
            val nextMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            it.repeatMode = nextMode
        }
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _progress.value = position
    }

    // --- Sleep timer ---
    //
    // Owned by MusicService, not by this ViewModel. The timer used to be a
    // viewModelScope.launch { delay(...) } here, which meant it was cancelled
    // the moment MainActivity was destroyed - backing out of the app while the
    // music kept playing, which is exactly what someone who has just set a
    // sleep timer does. It died silently and playback ran on. Everything below
    // is a remote control for the service's copy.

    /** Wall-clock time when the sleep timer fires, or null when inactive. */
    private val _sleepTimerEndsAt = MutableStateFlow<Long?>(null)
    val sleepTimerEndsAt: StateFlow<Long?> = _sleepTimerEndsAt.asStateFlow()

    /** True while playback is set to stop at the end of the current track. */
    private val _sleepTimerEndOfTrack = MutableStateFlow(false)
    val sleepTimerEndOfTrack: StateFlow<Boolean> = _sleepTimerEndOfTrack.asStateFlow()

    /** Stop playback after [minutes]; playback fades out rather than cutting. */
    fun startSleepTimer(minutes: Int) {
        sendSleepTimerCommand(MusicService.CMD_SLEEP_TIMER_SET, minutes)
    }

    /** Stop playback when the track that is playing now finishes. */
    fun startSleepTimerEndOfTrack() {
        sendSleepTimerCommand(MusicService.CMD_SLEEP_TIMER_SET, 0)
    }

    fun cancelSleepTimer() {
        sendSleepTimerCommand(MusicService.CMD_SLEEP_TIMER_CANCEL, 0)
    }

    private fun sendSleepTimerCommand(action: String, minutes: Int) {
        val ctrl = controller ?: return
        val args = android.os.Bundle().apply {
            putInt(MusicService.ARG_SLEEP_TIMER_MINUTES, minutes)
        }
        ctrl.sendCustomCommand(
            androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY),
            args
        )
    }

    /**
     * Adopt the timer state the service published. Also called on connect, so
     * a player reopened after the activity was destroyed picks the running
     * countdown back up instead of showing nothing.
     */
    private fun applySleepTimerExtras(extras: android.os.Bundle) {
        if (!extras.containsKey(MusicService.EXTRA_SLEEP_TIMER_ENDS_AT)) return
        val endsAt = extras.getLong(MusicService.EXTRA_SLEEP_TIMER_ENDS_AT, 0L)
        _sleepTimerEndsAt.value = endsAt.takeIf { it > 0L }
        _sleepTimerEndOfTrack.value =
            extras.getBoolean(MusicService.EXTRA_SLEEP_TIMER_END_OF_TRACK, false)
    }

    fun skipToNext() {
        controller?.let { player ->
            if (!player.hasNextMediaItem()) {
                // FALLBACK: The player might not have the full queue loaded yet.
                // Check if our local queue has more items.
                val currentIndex = player.currentMediaItemIndex
                val queue = _currentQueue.value
                if (currentIndex < queue.lastIndex) {
                    // We have a next song in our list, but Player doesn't know it yet.
                    // Add it before asking the service to overlap into it.
                    val nextItem = createMediaItem(queue[currentIndex + 1])
                    player.addMediaItem(currentIndex + 1, nextItem)
                }
            }
            sendSkipCommand(MusicService.CMD_SKIP_NEXT)
        }
    }

    fun skipToPrevious() {
        if (controller == null) return
        sendSkipCommand(MusicService.CMD_SKIP_PREVIOUS)
    }

    private fun sendSkipCommand(action: String, index: Int? = null) {
        val ctrl = controller ?: return
        val args = android.os.Bundle().apply {
            index?.let { putInt(MusicService.ARG_SKIP_INDEX, it) }
        }
        ctrl.sendCustomCommand(
            androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY),
            args,
        )
    }

    /**
     * Toggle the like status of the current song.
     */
    fun toggleCurrentSongLike() {
        val song = _currentSong.value ?: return
        // Pass the full song so its metadata is persisted — the Library's
        // Liked Songs list needs it to display YouTube songs without a login.
        val isNowLiked = likedSongsRepository.toggleLike(song)
        _isCurrentSongLiked.value = isNowLiked
    }

    /**
     * Check if a specific song is liked.
     */
    fun isSongLiked(songId: String): Boolean {
        return likedSongsRepository.isLiked(songId)
    }

    /**
     * Toggle the like status of any song, not only the playing one.
     *
     * [toggleCurrentSongLike] reads the player; the song options sheet acts on
     * whatever row was long-pressed, which is usually not what is playing.
     *
     * @return true when the song is now liked.
     */
    fun toggleLike(song: Song): Boolean {
        val isNowLiked = likedSongsRepository.toggleLike(song)
        if (song.id == _currentSong.value?.id) _isCurrentSongLiked.value = isNowLiked
        return isNowLiked
    }

    /**
     * Update the liked status for the current song (called when song changes).
     */
    private fun updateCurrentSongLikedStatus() {
        val songId = _currentSong.value?.id
        _isCurrentSongLiked.value = if (songId != null) {
            likedSongsRepository.isLiked(songId)
        } else {
            false
        }
    }
    
    // --- Download Actions ---

    private val _pendingSongDownload = MutableStateFlow<Song?>(null)
    val pendingSongDownload: StateFlow<Song?> = _pendingSongDownload.asStateFlow()

    fun toggleDownload(song: Song) {
        if (downloadRepository.isDownloaded(song.id)) {
            viewModelScope.launch {
                downloadRepository.deleteDownload(song.id)
            }
        } else if (!downloadRepository.isLocalOriginal(song) && !isDownloading(song.id)) {
            _pendingSongDownload.value = song
        }
    }

    fun dismissPendingSongDownload() {
        _pendingSongDownload.value = null
    }

    fun confirmPendingSongDownload() {
        val song = _pendingSongDownload.value ?: return
        _pendingSongDownload.value = null
        viewModelScope.launch { downloadRepository.downloadSong(song) }
    }
    
    fun isDownloaded(songId: String): Boolean {
        return downloadRepository.isDownloaded(songId)
    }
    
    fun isDownloading(songId: String): Boolean {
        return downloadingIds.value.contains(songId)
    }
    
    fun isLocalOriginal(song: Song): Boolean {
        return downloadRepository.isLocalOriginal(song)
    }
    
    fun downloadPlaylist(songs: List<Song>) {
        viewModelScope.launch {
            downloadRepository.downloadPlaylist(songs)
        }
    }
    
    fun cancelDownload(songId: String) {
        downloadRepository.cancelDownload(songId)
    }

    /**
     * Re-queue a failed download. Distinct from toggleDownload, which would
     * treat the leftover failed entry as a fresh request and leave it in the
     * progress list.
     */
    fun retryDownload(request: com.ivor.ivormusic.data.DownloadRequest) {
        downloadRepository.retryDownload(request)
    }

    val downloadedVideos = downloadRepository.downloadedVideos
    val downloadQueue = downloadRepository.downloadQueue

    fun downloadVideo(video: com.ivor.ivormusic.data.VideoItem) {
        viewModelScope.launch { downloadRepository.downloadVideo(video) }
    }

    fun deleteVideoDownload(videoId: String) {
        downloadRepository.deleteVideoDownload(videoId)
    }

    /** Cancel every queued and in-flight download. */
    fun cancelAllDownloads() {
        downloadRepository.cancelAll()
    }
    
    fun deleteDownload(songId: String) {
        downloadRepository.deleteDownload(songId)
    }
    
    // --- Lyrics Actions ---
    
    private var lyricsFetchJob: Job? = null

    /**
     * Fetch synced lyrics for the given song.
     */
    private fun fetchLyrics(song: Song) {
        // Cancel the in-flight fetch: on a quick skip A -> B, A's slower
        // response would otherwise land last and show A's lyrics over B.
        lyricsFetchJob?.cancel()

        _lyricsResult.value = LyricsResult.Loading

        lyricsFetchJob = viewModelScope.launch {
            val result = lyricsRepository.fetchLyrics(
                song = song,
                // Local lyrics are always checked first. Local-only mode only
                // disables the provider fallback; it must not disable files
                // already stored on the device.
                allowRemote = !themePreferences.isLocalOnlyModeEnabled()
            )
            // Belt and braces for the same race: only apply the result if
            // this is still the song on screen.
            if (_currentSong.value?.id == song.id) {
                _lyricsResult.value = result
            }
        }
    }
    
    // --- Playlist Actions ---

    fun createPlaylist(name: String, description: String?) {
        viewModelScope.launch {
            // Same accent colors the Library's create flow uses, so a playlist
            // made from the player is not the odd one out in the grid.
            playlistRepository.createPlaylist(
                name,
                description,
                com.ivor.ivormusic.ui.theme.playlistCoverSeeds(context)
            )
        }
    }

    /**
     * Make a playlist and put [song] straight into it.
     *
     * [createPlaylist] only makes an empty one, which is right for the player's
     * own sheet where the song is added by a second tap on the new row. From
     * the song options sheet there is no second tap - creating a playlist there
     * is a way of filing the song you long-pressed, so leaving it empty would
     * silently drop what the user asked for.
     */
    fun createPlaylistWithSong(name: String, description: String?, song: Song) {
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(
                name,
                description,
                com.ivor.ivormusic.ui.theme.playlistCoverSeeds(context)
            )
            playlistRepository.addSongToPlaylist(id, song)
        }
    }

    fun addToPlaylist(playlistId: String, song: Song? = _currentSong.value) {
        if (song == null) return
        viewModelScope.launch {
            val isLocal = playlistRepository.userPlaylists.value.any { it.id == playlistId }
            if (isLocal) {
                playlistRepository.addSongToPlaylist(playlistId, song)
            } else if (song.source == com.ivor.ivormusic.data.SongSource.YOUTUBE) {
                // YouTube playlist target: the song id is the videoId
                youTubeRepository.addToYouTubePlaylist(playlistId, song.id, music = true)
            }
        }
    }
    
    /**
     * Clear the current player state, stop playback, and dismiss the mini player.
     * This removes the last played song from preferences so it won't restore on next launch.
     */
    fun clearPlayer() {
        // Set flag BEFORE clearing to prevent listener from restoring
        isPlayerCleared = true
        pendingPlayRequest = null
        
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        
        // Clear UI state
        _currentSong.value = null
        _currentQueue.value = emptyList()
        _currentQueueItemId.value = null
        _isPlaying.value = false
        _isBuffering.value = false
        _playWhenReady.value = false
        _progress.value = 0L
        _duration.value = 0L
        _lyricsResult.value = LyricsResult.Loading
        
        // Clear stored last played song and session so neither restores
        themePreferences.clearLastPlayedSong()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playbackSessionRepository.clear()
        }
        
        KLog.d("PlayerViewModel", "Player cleared and mini player dismissed")
    }

    override fun onCleared() {
        castManager.stopDiscovery()
        castManager.endObservation()
        super.onCleared()
        controllerFuture?.let(MediaController::releaseFuture)
    }

    fun startCastDiscovery() = castManager.startDiscovery()

    fun stopCastDiscovery() = castManager.stopDiscovery()

    fun startCast(routeId: String) {
        val song = _currentSong.value ?: return
        if (song.source == com.ivor.ivormusic.data.SongSource.LOCAL) {
            _castUnavailableMessage.value = "Local files can only play on this phone"
            return
        }
        _castUnavailableMessage.value = null
        // The service needs an up-to-date durable queue if the activity is
        // recreated while the TV owns the MediaSession timeline.
        savePlaybackSession()
        viewModelScope.launch {
            if (castManager.connect(routeId)) {
                val castableQueue = _currentQueue.value.filter {
                    it.song.source != com.ivor.ivormusic.data.SongSource.LOCAL
                }
                if (castableQueue.size != _currentQueue.value.size) {
                    // MusicService makes the same receiver-side filter. Keep
                    // the displayed indices in lockstep with its CastPlayer
                    // timeline once the connection has actually succeeded.
                    _currentQueue.value = castableQueue
                }
            }
        }
    }

    fun stopCasting() = castManager.endSession(stopOnReceiver = true)
    
    // --- Settings Actions ---
    
    fun clearCache() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.ivor.ivormusic.data.CacheManager.clearCache()
        }
    }
    
    fun setMaxCacheSize(sizeMb: Long) {
        themePreferences.setMaxCacheSizeMb(sizeMb)
    }
    
    fun toggleCrossfade() {
        themePreferences.toggleCrossfadeEnabled()
    }
    
    fun setCrossfadeDuration(durationMs: Int) {
        themePreferences.setCrossfadeDuration(durationMs)
    }
}
