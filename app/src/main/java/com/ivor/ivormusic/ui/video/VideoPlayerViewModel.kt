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
                        // Handle Loop logic if needed (handled by UI state usually or player mode)
                    }
                }
            })
        }
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
        
        viewModelScope.launch {
            try {
                _exoPlayer?.stop()
                _exoPlayer?.clearMediaItems()
                
                val details = youtubeRepository.getVideoDetails(video.videoId)
                
                // Update video metadata (icon, subs) if available
                if (details.updatedVideoItem != null) {
                    _currentVideo.value = details.updatedVideoItem
                }
                
                val qualities = details.qualities
                _availableQualities.value = qualities
                _relatedVideos.value = details.relatedVideos
                
                if (qualities.isNotEmpty()) {
                    val bestQuality = qualities.find { it.resolution.contains("1080p60") }
                        ?: qualities.find { it.resolution.contains("1080p") }
                        ?: qualities.find { it.isDASH }
                        ?: qualities.first()
                        
                    loadQuality(bestQuality)
                } else {
                    // Fallback to legacy stream url if no qualities found (rare)
                    val streamUrl = youtubeRepository.getVideoStreamUrl(video.videoId)
                    if (streamUrl != null) {
                         val mediaItem = MediaItem.fromUri(streamUrl)
                        _exoPlayer?.setMediaItem(mediaItem)
                        _exoPlayer?.prepare()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
        
        // Report Playback
        viewModelScope.launch {
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
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        
        if (quality.audioUrl != null) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.url))
            val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(quality.audioUrl!!))
            
            val mergingSource = MergingMediaSource(videoSource, audioSource)
            _exoPlayer?.setMediaSource(mergingSource)
        } else {
            _exoPlayer?.setMediaItem(mediaItemBuilder.build())
        }
        _exoPlayer?.prepare()
    }
    
    fun setQuality(quality: VideoQuality) {
        val position = _exoPlayer?.currentPosition ?: 0L
        loadQuality(quality)
        _exoPlayer?.seekTo(position)
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
    }

    fun closePlayer() {
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
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
