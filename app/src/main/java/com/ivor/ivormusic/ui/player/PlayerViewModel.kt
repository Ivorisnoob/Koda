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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(private val context: Context) : ViewModel() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController? get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

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

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    // Liked songs functionality
    private val likedSongsRepository = LikedSongsRepository(context)
    
    private val _isCurrentSongLiked = MutableStateFlow(false)
    val isCurrentSongLiked: StateFlow<Boolean> = _isCurrentSongLiked.asStateFlow()
    
    val likedSongIds: StateFlow<Set<String>> = likedSongsRepository.likedSongIds
    
    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository(context)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // YouTube Repository for fetching more songs
    private val youTubeRepository = com.ivor.ivormusic.data.YouTubeRepository(context)
    
    // Loading state for "Load More" button
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    
    // Lyrics Repository and State
    private val lyricsRepository = LyricsRepository()
    
    private val _lyricsResult = MutableStateFlow<LyricsResult>(LyricsResult.Loading)
    val lyricsResult: StateFlow<LyricsResult> = _lyricsResult.asStateFlow()
    
    // Playlist Repository (Local Playlists)
    private val playlistRepository = com.ivor.ivormusic.data.PlaylistRepository(context)
    
    // Expose only local playlists for "Add to Playlist" feature (since we can only write to local ones)
    private val _localPlaylists = playlistRepository.userPlaylists
    val localPlaylists: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> = 
        _localPlaylists.map { list ->
            list.map { it.toDisplayItem() }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        initializeController()
        startProgressUpdates()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    // Clear buffering state when playback actually starts
                    if (isPlaying) {
                        _isBuffering.value = false
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _playWhenReady.value = playWhenReady
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        _isBuffering.value = false
                        _duration.value = controller?.duration ?: 0L
                    }
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
                    
                    song?.let {
                        _currentSong.value = it
                        updateCurrentSongLikedStatus()
                        fetchLyrics(it)
                        
                        // Sync history with YouTube
                        viewModelScope.launch {
                            youTubeRepository.reportPlayback(it.id)
                        }
                    }
                }
            })
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressUpdates() {
        viewModelScope.launch {
            var lastPosition = 0L
            while (isActive) {
                controller?.let {
                    val currentPos = it.currentPosition
                    
                    // Update progress always (to reflect seeking while paused)
                    _progress.value = currentPos
                    
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
        
        _currentQueue.value = songs
        val startIndex = (if (startSong != null) songs.indexOfFirst { it.id == startSong.id } else 0).coerceAtLeast(0)
        
        // Update current song immediately for UI responsiveness
        val currentSong = songs[startIndex]
        _currentSong.value = currentSong
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
            player.prepare()
            player.play()
            
            // Safety timeout for buffering state
            viewModelScope.launch {
                var timeout = 0
                while (_isBuffering.value && timeout < 10) {
                    delay(1000)
                    timeout++
                    if (timeout >= 10 && _isBuffering.value && !_isPlaying.value) {
                        _isBuffering.value = false
                        break
                    }
                }
            }
        }
    }
    
    /**
     * Load more recommendations from YouTube Music and add to queue.
     */
    fun loadMoreRecommendations() {
        if (_isLoadingMore.value) return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                // Fetch recommendations - simpler implementation for now:
                // If we have a current song, search for related content, otherwise generic
                val seed = _currentSong.value?.title ?: "Popular Music"
                val newSongs = youTubeRepository.search("Songs related to $seed", com.ivor.ivormusic.data.YouTubeRepository.FILTER_SONGS)
                    .filter { newSong -> _currentQueue.value.none { it.id == newSong.id } } // Filter duplicates
                    .take(10)
                    .map { 
                        if (it.album.isNullOrEmpty()) it.copy(album = "Related to $seed") else it 
                    }
                
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
                        .setArtworkUri(android.net.Uri.parse(song.highResThumbnailUrl ?: song.thumbnailUrl ?: ""))
                        .build()
                )
                .build()
        }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
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

    fun skipToNext() {
        controller?.let { player ->
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
                    player.seekToNext()
                    player.play()
                }
            }
        }
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    /**
     * Toggle the like status of the current song.
     */
    fun toggleCurrentSongLike() {
        val songId = _currentSong.value?.id ?: return
        val isNowLiked = likedSongsRepository.toggleLike(songId)
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
    
    fun deleteDownload(songId: String) {
        downloadRepository.deleteDownload(songId)
    }
    
    // --- Lyrics Actions ---
    
    /**
     * Fetch synced lyrics for the given song.
     */
    private fun fetchLyrics(song: Song) {
        _lyricsResult.value = LyricsResult.Loading
        
        viewModelScope.launch {
            val result = lyricsRepository.fetchLyrics(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                durationMs = song.duration
            )
            _lyricsResult.value = result
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
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }

    override fun onCleared() {
        super.onCleared()
        MediaController.releaseFuture(controllerFuture ?: return)
    }
}
