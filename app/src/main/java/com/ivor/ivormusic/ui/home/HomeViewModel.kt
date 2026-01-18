package com.ivor.ivormusic.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.FolderInfo
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.LikedSongsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepository = SongRepository(application)
    private val youtubeRepository = YouTubeRepository(application)
    private val playlistRepository = com.ivor.ivormusic.data.PlaylistRepository(application)
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
    // Combine YouTube liked songs with manually liked songs (local or YT)
    private val likedSongsRepository = LikedSongsRepository(application)
    
    // We can filter local songs by liked IDs
    // But ideally we should have a single source. For now, let's expose a combined list.
    val likedSongs: StateFlow<List<Song>> = combine(
        _likedSongs, // YouTube Liked (from API)
        _songs,      // Local Songs
        likedSongsRepository.likedSongIds // Manually liked IDs
    ) { ytLiked, localSongs, manuallyLikedIds ->
        val manuallyLikedLocalSongs = localSongs.filter { it.id in manuallyLikedIds }
        // Note: We can't easily reconstruct YouTube songs from just ID without querying
        // So for now, we show YouTube Liked (API) + Manually Liked Local Songs
        (ytLiked + manuallyLikedLocalSongs).distinctBy { it.id }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    // YouTube playlists
    private val _youtubePlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList())
    
    // Merged Playlists (Local + YouTube)
    val userPlaylists: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> = combine(
        _youtubePlaylists,
        playlistRepository.userPlaylists
    ) { ytPlaylists, localPlaylists ->
        val localItems = localPlaylists.map { it.toDisplayItem() }
        localItems + ytPlaylists
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userAvatar = MutableStateFlow<String?>(sessionManager.getUserAvatar())
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository(application)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // Video Mode State
    private val _trendingVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val trendingVideos: StateFlow<List<VideoItem>> = _trendingVideos.asStateFlow()
    
    private val _historyVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val historyVideos: StateFlow<List<VideoItem>> = _historyVideos.asStateFlow()
    
    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()
    
    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    init {
        checkYouTubeConnection()
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

    fun loadSongs(excludedFolders: Set<String> = emptySet()) {
        viewModelScope.launch {
            _songs.value = localRepository.getSongs(excludedFolders)
        }
    }
    
    /**
     * Get all available music folders for the folder exclusion UI.
     */
    suspend fun getAvailableFolders(): List<FolderInfo> {
        return localRepository.getAvailableFolders()
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
                _youtubePlaylists.value = youtubeRepository.getUserPlaylists()
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
                    _youtubeSongs.value = recs
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

    suspend fun loadMoreResults(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchNext(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLikedMusic(): List<Song> {
        return _likedSongs.value
    }

    suspend fun getUserPlaylists(): List<com.ivor.ivormusic.data.PlaylistDisplayItem> {
        return userPlaylists.value
    }

    suspend fun fetchPlaylistSongs(playlistId: String): List<Song> {
        // Check local first
        val localPlaylist = playlistRepository.userPlaylists.value.find { it.id == playlistId }
        if (localPlaylist != null) {
            return localPlaylist.songs
        }
        // Fallback to YouTube
        return try {
            youtubeRepository.getPlaylist(playlistId)
        } catch (e: Exception) {
            emptyList()
        }

    }
    
    /**
     * Search for songs by a specific artist on YouTube Music.
     */
    suspend fun searchArtistSongs(artistName: String): List<Song> {
        return try {
            youtubeRepository.search(artistName)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun logout() {
        sessionManager.clearSession()
        _isYouTubeConnected.value = false
        _youtubeSongs.value = emptyList()
        _likedSongs.value = emptyList()
        _youtubePlaylists.value = emptyList()
    }

    fun refresh(excludedFolders: Set<String> = emptySet()) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _isYouTubeConnected.value = sessionManager.isLoggedIn()
                if (_isYouTubeConnected.value) {
                    // Fetch account info and avatar sync
                    youtubeRepository.fetchAccountInfo()
                    _userAvatar.value = sessionManager.getUserAvatar()
                    
                    // Fetch personalized recommendations (order preserved from YTM)
                    val recs = youtubeRepository.getRecommendations()
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs
                    }
                    
                    // Update library data
                    _likedSongs.value = youtubeRepository.getLikedMusic()
                    _youtubePlaylists.value = youtubeRepository.getUserPlaylists()
                }
                // Reload local songs with exclusions and playlists
                playlistRepository.refreshPlaylists()
                _songs.value = localRepository.getSongs(excludedFolders)
            } catch (e: Exception) {
                // Silently fail
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ============== VIDEO MODE FUNCTIONS ==============

    /**
     * Load trending/recommended videos for video mode home screen.
     */
    fun loadTrendingVideos() {
        viewModelScope.launch {
            _isVideoLoading.value = true
            try {
                val videos = youtubeRepository.getTrendingVideos()
                if (videos.isNotEmpty()) {
                    _trendingVideos.value = videos
                }
            } catch (e: Exception) {
                // Handle error silently
            } finally {
                _isVideoLoading.value = false
            }
        }
    }

    /**
     * Load user's watch history.
     */
    fun loadYouTubeHistory() {
        // If not logged in, clear history
        if (!sessionManager.isLoggedIn()) {
             _historyVideos.value = emptyList()
             return
        }
        
        viewModelScope.launch {
            _isHistoryLoading.value = true
            try {
                val videos = youtubeRepository.getWatchHistory()
                _historyVideos.value = videos
            } catch (e: Exception) {
                // Handle error silently
            } finally {
                _isHistoryLoading.value = false
            }
        }
    }
    
    /**
     * Search for videos (for video mode search).
     */
    suspend fun searchVideos(query: String): List<VideoItem> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchVideos(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Refresh video mode content.
     */
    fun refreshVideos() {
        loadTrendingVideos()
    }

    // ============= PLAYLIST MANAGEMENT =============
    
    fun createLocalPlaylist(name: String, description: String?) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
        }
    }

    fun addSongToLocalPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }
}
