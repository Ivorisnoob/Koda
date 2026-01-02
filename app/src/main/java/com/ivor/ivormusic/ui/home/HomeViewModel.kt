package com.ivor.ivormusic.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepository = SongRepository(application)
    private val youtubeRepository = YouTubeRepository(application)
    private val sessionManager = SessionManager(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _youtubeSongs = MutableStateFlow<List<Song>>(emptyList())
    val youtubeSongs: StateFlow<List<Song>> = _youtubeSongs.asStateFlow()

    private val _isYouTubeConnected = MutableStateFlow(false)
    val isYouTubeConnected: StateFlow<Boolean> = _isYouTubeConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList())
    val userPlaylists: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> = _userPlaylists.asStateFlow()

    private val _userAvatar = MutableStateFlow<String?>(sessionManager.getUserAvatar())
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    init {
        checkYouTubeConnection()
    }

    fun loadSongs() {
        viewModelScope.launch {
            _songs.value = localRepository.getSongs()
        }
    }

    fun checkYouTubeConnection() {
        viewModelScope.launch {
            _isYouTubeConnected.value = sessionManager.isLoggedIn()
            if (_isYouTubeConnected.value) {
                youtubeRepository.fetchAccountInfo()
                _userAvatar.value = sessionManager.getUserAvatar()
                loadLibraryData()
            }
        }
    }

    private fun loadLibraryData() {
        viewModelScope.launch {
            try {
                _likedSongs.value = youtubeRepository.getLikedMusic()
                _userPlaylists.value = youtubeRepository.getUserPlaylists()
            } catch (e: Exception) { }
        }
    }

    fun loadYouTubeRecommendations() {
        if (!sessionManager.isLoggedIn()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val recs = youtubeRepository.getRecommendations()
                if (recs.isNotEmpty()) {
                    _youtubeSongs.value = recs.shuffled()
                }
            } catch (e: Exception) {
                // Handle error silently for now
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun searchYouTube(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.search(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLikedMusic(): List<Song> {
        return _likedSongs.value
    }

    suspend fun getUserPlaylists(): List<com.ivor.ivormusic.data.PlaylistDisplayItem> {
        return _userPlaylists.value
    }

    suspend fun fetchPlaylistSongs(playlistId: String): List<Song> {
        return try {
            youtubeRepository.getPlaylist(playlistId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun logout() {
        sessionManager.clearSession()
        _isYouTubeConnected.value = false
        _youtubeSongs.value = emptyList()
        _likedSongs.value = emptyList()
        _userPlaylists.value = emptyList()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _isYouTubeConnected.value = sessionManager.isLoggedIn()
                if (_isYouTubeConnected.value) {
                    // Fetch account info and avatar sync
                    youtubeRepository.fetchAccountInfo()
                    _userAvatar.value = sessionManager.getUserAvatar()
                    
                    // Fetch recommendations and shuffle them to ensure the UI looks "fresh"
                    val recs = youtubeRepository.getRecommendations()
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs.shuffled()
                    }
                    
                    // Update library data
                    _likedSongs.value = youtubeRepository.getLikedMusic()
                    _userPlaylists.value = youtubeRepository.getUserPlaylists()
                }
                // Reload local songs
                _songs.value = localRepository.getSongs()
            } catch (e: Exception) {
                // Silently fail
            } finally {
                _isLoading.value = false
            }
        }
    }
}
