package com.ivor.ivormusic.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.PlaylistRepository
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.domain.LyricsResult
import com.ivor.ivormusic.domain.PlaylistDisplayItem
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.media.PlayerController
import com.ivor.ivormusic.network.LyricsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val playerController: PlayerController,
    private val likedSongsRepository: LikedSongsRepository,
    private val lyricsRepository: LyricsRepository,
    private val downloadRepository: DownloadRepository,
    private val statsRepository: StatsRepository,
    private val playlistRepository: PlaylistRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    // Delegate playback state directly from PlayerController
    val currentSong: StateFlow<Song?> = playerController.currentSong
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val progress: StateFlow<Long> = playerController.progressMs
    val duration: StateFlow<Long> = playerController.durationMs
    val isBuffering: StateFlow<Boolean> = playerController.isBuffering
    val shuffleModeEnabled: StateFlow<Boolean> = playerController.shuffleModeEnabled
    val repeatMode: StateFlow<Int> = playerController.repeatMode
    val currentQueue: StateFlow<List<Song>> = playerController.queue

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isCurrentSongLiked = MutableStateFlow(false)
    val isCurrentSongLiked: StateFlow<Boolean> = _isCurrentSongLiked.asStateFlow()
    val likedSongIds: StateFlow<Set<String>> = likedSongsRepository.likedSongIds

    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    val localPlaylists: StateFlow<List<PlaylistDisplayItem>> =
        playlistRepository.userPlaylists.map { list -> list.map { it.toDisplayItem() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cacheEnabled = prefs.cacheEnabled
    val maxCacheSizeMb = prefs.maxCacheSizeMb
    val crossfadeEnabled = prefs.crossfadeEnabled
    val crossfadeDurationMs = prefs.crossfadeDurationMs

    private val _lyricsResult = MutableStateFlow<LyricsResult>(LyricsResult.Loading)
    val lyricsResult: StateFlow<LyricsResult> = _lyricsResult.asStateFlow()

    private var playRecordingJob: Job? = null
    private var lastRecordedSongId: String? = null

    init {
        viewModelScope.launch {
            currentSong.collect { song ->
                song?.let {
                    _isCurrentSongLiked.value = likedSongsRepository.isLiked(it.id)
                    fetchLyrics(it)
                    prefs.saveLastPlayedSong(it)
                    scheduleStatsRecording(it)
                }
            }
        }
    }

    private fun scheduleStatsRecording(song: Song) {
        playRecordingJob?.cancel()
        playRecordingJob = viewModelScope.launch {
            delay(15_000)
            if (isActive && song.id != lastRecordedSongId) {
                lastRecordedSongId = song.id
                statsRepository.addPlayEvent(song)
            }
        }
    }

    private fun fetchLyrics(song: Song) {
        _lyricsResult.value = LyricsResult.Loading
        viewModelScope.launch {
            _lyricsResult.value = lyricsRepository.fetchLyrics(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.duration
            )
        }
    }

    fun playSong(song: Song) = playerController.playSong(song, listOf(song))
    fun playQueue(songs: List<Song>, startSong: Song? = null) {
        val idx = songs.indexOfFirst { it.id == startSong?.id }.coerceAtLeast(0)
        playerController.playSong(songs[idx], songs, idx)
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun toggleShuffle() = playerController.setShuffleMode(!shuffleModeEnabled.value)
    fun toggleRepeat() {
        val next = when (repeatMode.value) {
            PlayerController.REPEAT_MODE_OFF -> PlayerController.REPEAT_MODE_ALL
            PlayerController.REPEAT_MODE_ALL -> PlayerController.REPEAT_MODE_ONE
            else -> PlayerController.REPEAT_MODE_OFF
        }
        playerController.setRepeatMode(next)
    }
    fun seekTo(position: Long) = playerController.seekTo(position)
    fun loadMoreRecommendations() { /* no-op: recommendations loaded on demand */ }
    fun skipToNext() = playerController.skipToNext()
    fun skipToPrevious() = playerController.skipToPrevious()
    fun addToQueue(songs: List<Song>) = songs.forEach { playerController.addToQueue(it) }
    fun moveQueueItem(from: Int, to: Int) = playerController.reorderQueue(from, to)
    fun removeQueueItem(index: Int) = playerController.removeFromQueue(index)

    fun toggleCurrentSongLike() {
        val id = currentSong.value?.id ?: return
        _isCurrentSongLiked.value = likedSongsRepository.toggleLike(id)
    }

    fun isSongLiked(songId: String) = likedSongsRepository.isLiked(songId)

    fun toggleDownload(song: Song) {
        viewModelScope.launch {
            if (downloadRepository.isDownloaded(song.id)) downloadRepository.deleteDownload(song.id)
            else downloadRepository.downloadSong(song)
        }
    }

    fun isDownloaded(songId: String) = downloadRepository.isDownloaded(songId)
    fun isDownloading(songId: String) = downloadingIds.value.contains(songId)
    fun isLocalOriginal(song: Song) = downloadRepository.isLocalOriginal(song)
    fun cancelDownload(songId: String) = downloadRepository.cancelDownload(songId)
    fun deleteDownload(songId: String) = downloadRepository.deleteDownload(songId)
    fun downloadPlaylist(songs: List<Song>) = viewModelScope.launch { downloadRepository.downloadPlaylist(songs) }

    fun createPlaylist(name: String, description: String?) =
        viewModelScope.launch { playlistRepository.createPlaylist(name, description) }

    fun addToPlaylist(playlistId: String, song: Song? = currentSong.value) {
        if (song == null) return
        viewModelScope.launch { playlistRepository.addSongToPlaylist(playlistId, song) }
    }

    fun clearPlayer() {
        playerController.clearQueue()
        _lyricsResult.value = LyricsResult.Loading
        prefs.clearLastPlayedSong()
    }

    fun setMaxCacheSize(sizeMb: Long) = prefs.setMaxCacheSizeMb(sizeMb)
    fun toggleCrossfade() = prefs.toggleCrossfadeEnabled()
    fun setCrossfadeDuration(ms: Int) = prefs.setCrossfadeDuration(ms)

    override fun onCleared() {
        super.onCleared()
        playerController.release()
    }
}
