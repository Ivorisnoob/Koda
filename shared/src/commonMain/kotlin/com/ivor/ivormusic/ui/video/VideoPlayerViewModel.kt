package com.ivor.ivormusic.ui.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.domain.VideoDetails
import com.ivor.ivormusic.domain.VideoItem
import com.ivor.ivormusic.domain.VideoQuality
import com.ivor.ivormusic.network.YouTubeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VideoPlayerViewModel(
    private val youtubeRepository: YouTubeRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _currentVideo = MutableStateFlow<VideoItem?>(null)
    val currentVideo: StateFlow<VideoItem?> = _currentVideo.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities.asStateFlow()

    private val _currentQuality = MutableStateFlow<VideoQuality?>(null)
    val currentQuality: StateFlow<VideoQuality?> = _currentQuality.asStateFlow()

    private val _relatedVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val relatedVideos: StateFlow<List<VideoItem>> = _relatedVideos.asStateFlow()

    private val _isAutoPlayEnabled = MutableStateFlow(false)
    val isAutoPlayEnabled: StateFlow<Boolean> = _isAutoPlayEnabled.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var playbackReportJob: Job? = null

    fun toggleAutoPlay() { _isAutoPlayEnabled.value = !_isAutoPlayEnabled.value }
    fun toggleLooping() { _isLooping.value = !_isLooping.value }

    fun setExpanded(expanded: Boolean) { _isExpanded.value = expanded }
    fun dismiss() { _isExpanded.value = false }

    fun setQuality(quality: VideoQuality) { _currentQuality.value = quality }

    fun playVideo(video: VideoItem) {
        if (_currentVideo.value?.videoId == video.videoId) {
            _isExpanded.value = true
            return
        }
        _currentVideo.value = video
        _isExpanded.value = true
        _isLoading.value = true
        _relatedVideos.value = emptyList()
        _playbackError.value = null

        // Phase 1: get stream URLs fast
        viewModelScope.launch {
            try {
                val qualities = youtubeRepository.getVideoStreamQualities(video.videoId)
                _availableQualities.value = qualities
                if (qualities.isNotEmpty()) {
                    val best = qualities.find { it.resolution.contains("1080p60") }
                        ?: qualities.find { it.resolution.contains("1080p") }
                        ?: qualities.find { it.isDASH }
                        ?: qualities.first()
                    _currentQuality.value = best
                } else {
                    val fallback = youtubeRepository.getVideoStreamUrl(video.videoId)
                    if (fallback != null) {
                        _currentQuality.value = VideoQuality("Auto", fallback)
                    } else {
                        _playbackError.value = "Unable to load video stream"
                    }
                }
            } catch (e: Exception) {
                _playbackError.value = e.message ?: "Playback error"
            } finally {
                _isLoading.value = false
            }
        }

        // Phase 2: load metadata and related in background
        viewModelScope.launch {
            try {
                val details: VideoDetails = youtubeRepository.getVideoDetails(video.videoId)
                if (details.updatedVideoItem != null) _currentVideo.value = details.updatedVideoItem
                _relatedVideos.value = details.relatedVideos
                if (_availableQualities.value.isEmpty() && details.qualities.isNotEmpty()) {
                    _availableQualities.value = details.qualities
                }
            } catch (_: Exception) {}
        }

        // Report playback after 10s if history is enabled
        playbackReportJob?.cancel()
        playbackReportJob = viewModelScope.launch {
            delay(10_000)
            if (prefs.saveVideoHistory.value) {
                youtubeRepository.reportPlayback(video.videoId)
            }
        }
    }
}
