package com.ivor.ivormusic.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.LyricsRepository
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.service.MusicService
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

class PlayerViewModel(private val context: Context) : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = try {
            if (controllerFuture?.isDone == true) controllerFuture?.get() else null
        } catch (e: Exception) {
            // Future may have completed exceptionally if the service connection
            // failed (e.g. onGetSession returned null during a teardown race).
            android.util.Log.w("PlayerViewModel", "controller getter: failed future", e)
            null
        }
    private var connectRetryAttempts = 0

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

    // One-shot user-facing playback error message. The UI shows it (toast) and
    // calls clearPlaybackError(). Without this, resolution/network failures
    // were completely silent and the player sat on the buffering spinner.
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    /**
     * Snapshot the current queue, index, and position for resume-on-reopen.
     * Controller state is read on the caller (main) thread; the file write
     * goes to IO.
     */
    private fun savePlaybackSession() {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return
        val index = controller?.currentMediaItemIndex ?: 0
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

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

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
    private val lyricsRepository = LyricsRepository()
    
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

    init {
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
                        android.util.Log.w("PlayerViewModel", "Buffering watchdog: clearing stuck state")
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

            val song = session.songs[session.currentIndex]
            android.util.Log.d(
                "PlayerViewModel",
                "Restoring session: ${session.songs.size} songs, index=${session.currentIndex}, pos=${session.positionMs}"
            )

            _currentQueue.value = session.songs
            _currentSong.value = song
            _progress.value = session.positionMs
            if (song.duration > 0) _duration.value = song.duration
            updateCurrentSongLikedStatus()

            controller?.let { player ->
                val items = session.songs.map { createMediaItem(it) }
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

        android.util.Log.d("PlayerViewModel", "Restoring last played song: ${song.title}")

        // Set the current song for UI display
        _currentSong.value = song
        _currentQueue.value = listOf(song)

        // Prepare the song in the player (but don't auto-play)
        val mediaItem = createMediaItem(song)
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
                android.util.Log.w("PlayerViewModel", "MediaController connect failed: ${e.message}")
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

            // Restore the previous session if there's nothing currently playing
            restoreLastSession()
            
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
                    android.util.Log.e("PlayerViewModel", "Playback error: ${error.errorCodeName}", error)
                    // MusicService retries and skips on its own; if it recovers,
                    // the player re-enters BUFFERING and the flag comes back.
                    // Clearing here guarantees the spinner can't outlive a
                    // playback that is never going to start.
                    _isBuffering.value = false
                    val title = _currentSong.value?.title
                    _playbackError.value = if (title != null) {
                        "Couldn't play \"$title\". Check your connection and try again."
                    } else {
                        "Playback failed. Check your connection and try again."
                    }
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
                        android.util.Log.d("PlayerViewModel", "Ignoring media transition - player was cleared")
                        return
                    }
                    
                    // Update current song based on Media ID
                    val id = mediaItem?.mediaId
                    var song: Song? = null
                    
                    // Try to find song by mediaId first
                    if (!id.isNullOrEmpty()) {
                        song = _currentQueue.value.find { it.id == id }
                    }
                    
                    // Fallback to index-based lookup if mediaId lookup fails
                    if (song == null) {
                        val currentIndex = controller?.currentMediaItemIndex ?: -1
                        if (currentIndex >= 0 && currentIndex < _currentQueue.value.size) {
                            song = _currentQueue.value.getOrNull(currentIndex)
                        }
                    }
                    
                    // If still null, try to reconstruct from MediaItem metadata
                    if (song == null && mediaItem != null) {
                        song = extractSongFromMediaItem(mediaItem)
                    }
                    
                    song?.let {
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
                                    statsRepository.addPlayEvent(it)
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
                             android.util.Log.d("PlayerViewModel", "Auto-Queue: $songsLeft songs left, loading more...")
                             loadMoreRecommendations()
                        }
                    }
                }
            })
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
            val songs = mutableListOf<Song>()
            for (i in 0 until itemCount) {
                val mediaItem = ctrl.getMediaItemAt(i)
                extractSongFromMediaItem(mediaItem)?.let { songs.add(it) }
            }
            if (songs.isNotEmpty()) {
                _currentQueue.value = songs
            }
        }
        
        // Sync current song
        val currentMediaItem = ctrl.currentMediaItem
        if (currentMediaItem != null && _currentSong.value == null) {
            var song = _currentQueue.value.find { it.id == currentMediaItem.mediaId }
            if (song == null) {
                song = extractSongFromMediaItem(currentMediaItem)
            }
            song?.let {
                _currentSong.value = it
                updateCurrentSongLikedStatus()
                fetchLyrics(it)
            }
        }
        
        android.util.Log.d("PlayerViewModel", "Synced state: playing=${_isPlaying.value}, song=${_currentSong.value?.title}, queue=${_currentQueue.value.size} items")
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
                        if (ticksSinceSave >= 5) {
                            ticksSinceSave = 0
                            savePlaybackSession()
                        }
                    }
                    
                    // Only update progress if it's a valid non-negative value
                    if (currentPos >= 0) {
                        _progress.value = currentPos
                    }
                    
                    // Also update duration if it was not set yet (fallback)
                    val dur = it.duration
                    if (dur > 0 && _duration.value == 0L) {
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
        
        // Reset cleared flag - user is actively playing
        isPlayerCleared = false
        
        _currentQueue.value = songs
        val startIndex = (if (startSong != null) songs.indexOfFirst { it.id == startSong.id } else 0).coerceAtLeast(0)
        
        // Update current song immediately for UI responsiveness
        val currentSong = songs[startIndex]
        _currentSong.value = currentSong
        _isBuffering.value = true // Immediately show loading
        _duration.value = 0L // Reset duration until we load the new song
        updateCurrentSongLikedStatus()
        fetchLyrics(currentSong)
        
        controller?.let { player ->
            // 1. Set the target song first (triggers URL resolution in MusicService)
            val startItem = createMediaItem(currentSong)
            player.setMediaItem(startItem)
            
            // 2. Add the rest of the queue BEFORE prepare (so notification sees full queue)
            val otherItemsBefore = songs.subList(0, startIndex).map { createMediaItem(it) }
            val otherItemsAfter = songs.subList(startIndex + 1, songs.size).map { createMediaItem(it) }
            
            if (otherItemsBefore.isNotEmpty()) {
                player.addMediaItems(0, otherItemsBefore)
            }
            if (otherItemsAfter.isNotEmpty()) {
                // Start item is now at index otherItemsBefore.size
                player.addMediaItems(otherItemsBefore.size + 1, otherItemsAfter)
            }
            
            // 3. NOW prepare and play - notification will see complete queue
            // (the buffering watchdog in init covers the stuck-spinner case)
            player.prepare()
            player.play()
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
                if (radio.isNotEmpty() && queue.size == 1 && queue[0].id == song.id) {
                    addToQueue(radio)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Radio fetch failed for ${song.id}", e)
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
        val index = queue.indexOfFirst { it.id == song.id }
        if (index < 0) {
            playQueue(queue.ifEmpty { listOf(song) }, song)
            return
        }
        skipToQueueItem(index)
    }

    /**
     * Seek the player to the queue item at [index] and start playback.
     */
    fun skipToQueueItem(index: Int) {
        val queue = _currentQueue.value
        val song = queue.getOrNull(index) ?: return
        val player = controller
        if (player == null) {
            playQueue(queue, song)
            return
        }

        // Guard: if the player's timeline drifted from the UI queue, rebuild.
        if (index >= player.mediaItemCount || player.getMediaItemAt(index).mediaId != song.id) {
            playQueue(queue, song)
            return
        }

        if (index == player.currentMediaItemIndex) {
            player.play()
            return
        }

        isPlayerCleared = false

        // Update UI state immediately for responsiveness (same as playQueue)
        _currentSong.value = song
        _isBuffering.value = true
        _duration.value = 0L
        updateCurrentSongLikedStatus()
        fetchLyrics(song)

        player.seekTo(index, 0)
        player.play()
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
                    excludeIds = _currentQueue.value.map { it.id }.toSet(),
                    limit = 10
                )

                if (newSongs.isNotEmpty()) {
                    addToQueue(newSongs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        
        val currentList = _currentQueue.value.toMutableList()
        currentList.addAll(songs)
        _currentQueue.value = currentList
        
        controller?.let { player ->
            val newItems = songs.map { createMediaItem(it) }
            player.addMediaItems(newItems)
        }
        savePlaybackSession()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val currentList = _currentQueue.value
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return

        val mutable = currentList.toMutableList()
        val movedSong = mutable.removeAt(fromIndex)
        mutable.add(toIndex, movedSong)
        _currentQueue.value = mutable

        controller?.moveMediaItem(fromIndex, toIndex)
        savePlaybackSession()
    }

    fun removeQueueItem(index: Int) {
        val currentList = _currentQueue.value
        if (index !in currentList.indices) return
        if (currentList.size <= 1) return

        val mutable = currentList.toMutableList()
        mutable.removeAt(index)
        _currentQueue.value = mutable

        controller?.removeMediaItem(index)
        savePlaybackSession()
    }

    private fun createMediaItem(song: Song): MediaItem {
        return if (song.source == com.ivor.ivormusic.data.SongSource.LOCAL && song.uri != null) {
            // For local songs, we still need to set mediaId for proper tracking
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
        } else {
            // YouTube songs: Use mediaId as placeholder URI
            // MusicService will resolve the actual stream URL when this track is about to play
            // This ensures MediaSession counts this as a valid timeline item (fixes Next button)
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri("https://placeholder.ivormusic/${song.id}")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        // Absent rather than an empty Uri when there is no
                        // artwork: Uri.parse("") is a valid-looking Uri that
                        // every consumer then fails to load, and this metadata
                        // feeds the notification and the lock screen.
                        .setArtworkUri(
                            (song.highResThumbnailUrl ?: song.thumbnailUrl)
                                ?.takeIf { it.isNotBlank() }
                                ?.let(android.net.Uri::parse)
                        )
                        .build()
                )
                .build()
        }
    }

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
            // Check if there is physically a next item in the implementation list
            val hasGenuineNextItem = player.currentMediaItemIndex < player.mediaItemCount - 1
            
            // Fix: Override 'Repeat One' behavior which normally prevents skipping to next track
            if (player.repeatMode == Player.REPEAT_MODE_ONE && hasGenuineNextItem) {
                // Force skip to next index
                player.seekTo(player.currentMediaItemIndex + 1, 0)
                player.play()
                _isBuffering.value = true
                return
            }

            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
                _isBuffering.value = true // Expect buffering on skip
            } else {
                // FALLBACK: The player might not have the full queue loaded yet.
                // Check if our local queue has more items.
                val currentIndex = player.currentMediaItemIndex
                val queue = _currentQueue.value
                
                if (currentIndex < queue.lastIndex) {
                    // We have a next song in our list, but Player doesn't know it yet.
                    // Manually add it and skip.
                    val nextSong = queue[currentIndex + 1]
                    val nextItem = createMediaItem(nextSong)
                    
                    viewModelScope.launch {
                        player.addMediaItem(currentIndex + 1, nextItem)
                        player.seekTo(currentIndex + 1, 0)
                        player.play()
                    }
                    _isBuffering.value = true
                } else {
                    // Genuine end of playlist
                    if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                        // Loop to start if desired, or just do nothing (standard behavior is loop for Repeat One)
                        if (player.mediaItemCount > 0) {
                            player.seekTo(0, 0)
                            player.play()
                            _isBuffering.value = true
                        }
                    } else {
                        player.seekToNext()
                        player.play()
                    }
                }
            }
        }
    }

    fun skipToPrevious() {
        controller?.let { player ->
            // Fix for Repeat One: Previous button should go to previous song (if < 3s played), not restart current
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                // If we are well into the song, restart it (standard behavior)
                if (player.currentPosition > 3000) {
                    player.seekTo(0)
                    player.play()
                } else {
                    // If at start of song, go to previous track
                    val prevIndex = player.currentMediaItemIndex - 1
                    if (prevIndex >= 0) {
                        player.seekTo(prevIndex, 0)
                        player.play()
                    } else {
                        // Start of list. Loop to end.
                        if (player.mediaItemCount > 0) {
                            player.seekTo(player.mediaItemCount - 1, 0)
                            player.play()
                        }
                    }
                }
            } else {
                player.seekToPrevious()
            }
        }
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
    
    fun toggleDownload(song: Song) {
        viewModelScope.launch {
            if (downloadRepository.isDownloaded(song.id)) {
                downloadRepository.deleteDownload(song.id)
            } else {
                downloadRepository.downloadSong(song)
            }
        }
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

        // Lyrics come from an online API; skip entirely in local-only mode
        if (themePreferences.isLocalOnlyModeEnabled()) {
            _lyricsResult.value = LyricsResult.NotFound
            return
        }
        _lyricsResult.value = LyricsResult.Loading

        lyricsFetchJob = viewModelScope.launch {
            val result = lyricsRepository.fetchLyrics(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album ?: "",
                durationMs = song.duration
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
            playlistRepository.createPlaylist(name, description)
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
        
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        
        // Clear UI state
        _currentSong.value = null
        _currentQueue.value = emptyList()
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
        
        android.util.Log.d("PlayerViewModel", "Player cleared and mini player dismissed")
    }

    override fun onCleared() {
        super.onCleared()
        MediaController.releaseFuture(controllerFuture ?: return)
    }
    
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
