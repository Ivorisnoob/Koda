package com.ivor.ivormusic.ui.video

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@UnstableApi
class VideoPlayerViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val youtubeRepository = YouTubeRepository(context)

    // Player Instance
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    // State
    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering
    
    // Qualities and Related
    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities

    private val _currentQuality = MutableStateFlow<VideoQuality?>(null)
    val currentQuality: StateFlow<VideoQuality?> = _currentQuality

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val relatedVideos: StateFlow<List<VideoItem>> = _relatedVideos

    private val _isAutoPlayEnabled = MutableStateFlow(false)
    val isAutoPlayEnabled: StateFlow<Boolean> = _isAutoPlayEnabled.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private var playbackReportJob: kotlinx.coroutines.Job? = null

    // Track quality change listener to prevent leaks
    private var qualityChangeListener: Player.Listener? = null

    // Error state
    private val _playbackError = MutableStateFlow<Throwable?>(null)
    val playbackError: StateFlow<Throwable?> = _playbackError.asStateFlow()

    init {
        // Initialize ExoPlayer
        _exoPlayer = ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) {
                        if (_isAutoPlayEnabled.value) {
                            val nextVideo = _relatedVideos.value.firstOrNull()
                            if (nextVideo != null) {
                                // Dispatch to main thread via viewModelScope
                                viewModelScope.launch { playVideo(nextVideo) }
                            }
                        }
                    }
                }
            })
        }
    }

    fun toggleAutoPlay() {
        _isAutoPlayEnabled.value = !_isAutoPlayEnabled.value
    }

    fun toggleLooping() {
        _isLooping.value = !_isLooping.value
        _exoPlayer?.repeatMode = if (_isLooping.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun playVideo(video: VideoItem) {
        if (_currentVideo.value?.videoId == video.videoId) {
            // Already playing this video, just expand
            _isExpanded.value = true
            return
        }
        
        _currentVideo.value = video
        _isExpanded.value = true
        _isLoading.value = true
        _relatedVideos.value = emptyList() // Clear previous related
        
        // Fetch video details once and share between parallel tasks
        val detailsDeferred = viewModelScope.async {
            youtubeRepository.getVideoDetails(video.videoId)
        }
        
        // Parallel Task 1: Load Video and Start Playback ASAP
        viewModelScope.launch {
            try {
                _playbackError.value = null // Clear previous error
                _exoPlayer?.stop()
                _exoPlayer?.clearMediaItems()
                
                // Get stream qualities first to start playback
                val details = detailsDeferred.await()
                val qualities = details.qualities
                _availableQualities.value = qualities
                
                if (qualities.isNotEmpty()) {
                    val bestQuality = qualities.find { it.resolution.contains("1080p60") }
                        ?: qualities.find { it.resolution.contains("1080p") }
                        ?: qualities.find { it.isDASH }
                        ?: qualities.first()
                        
                    loadQuality(bestQuality)
                    _isLoading.value = false // Set loading to false once playback starts
                } else {
                    // Fallback to legacy stream url if no qualities found
                    val streamUrl = youtubeRepository.getVideoStreamUrl(video.videoId)
                    if (streamUrl != null) {
                        // Set legacy quality sentinel so UI reflects fallback state
                        _currentQuality.value = VideoQuality(
                            resolution = "Auto",
                            url = streamUrl,
                            isDASH = false,
                            audioUrl = null
                        )
                        val mediaItem = MediaItem.fromUri(streamUrl)
                        _exoPlayer?.setMediaItem(mediaItem)
                        _exoPlayer?.prepare()
                        _isLoading.value = false
                    } else {
                        _playbackError.value = Exception("Unable to load video stream")
                        _isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _playbackError.value = e
                _isLoading.value = false
            }
        }

        // Parallel Task 2: Load Metadata and Related Videos
        viewModelScope.launch {
            try {
                val details = detailsDeferred.await()
                
                // Update video metadata (icon, subs, description) if available
                if (details.updatedVideoItem != null) {
                    _currentVideo.value = details.updatedVideoItem
                }
                
                _relatedVideos.value = details.relatedVideos
                
                // If Task 1 failed to set qualities (race condition), set them here
                if (_availableQualities.value.isEmpty()) {
                    _availableQualities.value = details.qualities
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Report Playback (cancel previous if user switched videos)
        playbackReportJob?.cancel()
        playbackReportJob = viewModelScope.launch {
            kotlinx.coroutines.delay(10000)
            if (_isPlaying.value) {
                youtubeRepository.reportPlayback(video.videoId)
            }
        }
    }

    private fun loadQuality(quality: VideoQuality) {
        _currentQuality.value = quality
        val mediaItemBuilder = MediaItem.Builder().setUri(quality.url)
        
        if (quality.isDASH) {
            // DASH streams are adaptive - use directly without merging
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            _exoPlayer?.setMediaItem(mediaItemBuilder.build())
        } else {
            val audioUrl = quality.audioUrl
            if (audioUrl != null) {
                // Non-DASH with separate audio - use MergingMediaSource
                val dataSourceFactory = DefaultDataSource.Factory(context)
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(quality.url))
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(audioUrl))
                
                val mergingSource = MergingMediaSource(videoSource, audioSource)
                _exoPlayer?.setMediaSource(mergingSource)
            } else {
                _exoPlayer?.setMediaItem(mediaItemBuilder.build())
            }
        }
        _exoPlayer?.prepare()
    }
    
    fun setQuality(quality: VideoQuality) {
        val player = _exoPlayer ?: return
        val position = player.currentPosition
        
        // Remove any existing quality change listener to prevent leaks
        qualityChangeListener?.let { player.removeListener(it) }
        
        loadQuality(quality)
        
        // Wait for player to be ready before seeking to preserved position
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.seekTo(position)
                    player.removeListener(this)
                    qualityChangeListener = null
                }
            }
        }
        qualityChangeListener = listener
        player.addListener(listener)
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun closePlayer() {
        // Remove quality change listener to prevent leaks if player closed before STATE_READY
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        _exoPlayer?.stop()
        _currentVideo.value = null
        _isExpanded.value = false
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            _exoPlayer?.pause()
        } else {
            _exoPlayer?.play()
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Remove quality change listener to prevent leaks
        qualityChangeListener?.let { _exoPlayer?.removeListener(it) }
        qualityChangeListener = null
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
