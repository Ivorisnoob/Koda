package com.ivor.ivormusic.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.FolderInfo
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.LikedSongsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepository = SongRepository(application)
    private val youtubeRepository = YouTubeRepository(application)
    private val playlistRepository = com.ivor.ivormusic.data.PlaylistRepository(application)
    private val sessionManager = SessionManager(application)
    private val searchHistoryRepository = com.ivor.ivormusic.data.SearchHistoryRepository(application)
    private val recommendationEngine = com.ivor.ivormusic.data.RecommendationEngine(application, youtubeRepository)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _searchHistory = MutableStateFlow(searchHistoryRepository.getHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _youtubeSongs = MutableStateFlow<List<Song>>(emptyList())
    val youtubeSongs: StateFlow<List<Song>> = _youtubeSongs.asStateFlow()
    


    private val _isYouTubeConnected = MutableStateFlow(false)
    val isYouTubeConnected: StateFlow<Boolean> = _isYouTubeConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    // Combine YouTube liked songs with manually liked songs (local or YT)
    private val likedSongsRepository = LikedSongsRepository(application)
    
    // Combined liked songs: manually liked (full metadata stored on like, so
    // YouTube songs show without a login) + YT-account liked + liked local songs.
    val likedSongs: StateFlow<List<Song>> = combine(
        _likedSongs,                       // YouTube Liked (from API, requires login)
        _songs,                            // Local Songs
        likedSongsRepository.likedSongs,   // Manually liked, with metadata (newest first)
        likedSongsRepository.likedSongIds  // Manually liked IDs (covers legacy likes without metadata)
    ) { ytLiked, localSongs, manuallyLikedSongs, manuallyLikedIds ->
        val manuallyLikedLocalSongs = localSongs.filter { it.id in manuallyLikedIds }
        (manuallyLikedSongs + ytLiked + manuallyLikedLocalSongs).distinctBy { it.id }
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
    
    val localPlaylistIds: StateFlow<Set<String>> = playlistRepository.userPlaylists
        .map { playlists -> playlists.map { it.id }.toSet() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    private val _userAvatar = MutableStateFlow<String?>(sessionManager.getUserAvatar())
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository(application)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // Recently played (from the local play history)
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    fun refreshRecentlyPlayed(limit: Int = 15) {
        viewModelScope.launch {
            val history = statsRepository.loadHistory() // newest first
            val localSongs = _songs.value
            val seen = mutableSetOf<String>()
            val recents = mutableListOf<Song>()
            for (entry in history) {
                if (!seen.add(entry.songId)) continue
                val song = if (entry.source == com.ivor.ivormusic.data.SongSource.LOCAL) {
                    // Local files need a playable URI — resolve from the scanned library
                    localSongs.find { it.id == entry.songId }
                } else {
                    Song.fromYouTube(
                        videoId = entry.songId,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        duration = entry.duration,
                        thumbnailUrl = entry.thumbnailUrl
                    )
                }
                if (song != null) recents.add(song)
                if (recents.size >= limit) break
            }
            _recentlyPlayed.value = recents
        }
    }

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

    fun loadSongs(excludedFolders: Set<String> = emptySet(), manualScan: Boolean = false) {
        viewModelScope.launch {
            _songs.value = localRepository.getSongs(excludedFolders, manualScan)
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
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (sessionManager.isLoggedIn()) {
                    val recs = youtubeRepository.getRecommendations()
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs
                    }
                } else {
                    // Not logged in: personalize from the local taste profile
                    // (play history, likes, searches). Falls back to trending
                    // internally when there's no listening data yet.
                    val recs = recommendationEngine.getHomeRecommendations()
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs
                    }
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
        // "Liked Songs" is assembled locally so it works without a YouTube
        // login: stored metadata + YT-account likes + liked local songs.
        if (playlistId == "LM" || playlistId == "VLLM") {
            val manuallyLiked = likedSongsRepository.likedSongs.value
            val likedIds = likedSongsRepository.getAllLikedSongIds()
            val likedLocalSongs = _songs.value.filter { it.id in likedIds }
            val ytLiked = _likedSongs.value.ifEmpty {
                if (sessionManager.isLoggedIn()) {
                    try { youtubeRepository.getLikedMusic() } catch (e: Exception) { emptyList() }
                } else emptyList()
            }
            return (manuallyLiked + ytLiked + likedLocalSongs).distinctBy { it.id }
        }

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
     * Search Wrapper Functions for UI
     */
    suspend fun searchArtists(query: String): List<ArtistItem> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchArtists(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchAlbums(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchPlaylists(query)
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

    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> {
        return try {
            youtubeRepository.getArtistDetails(artistId)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }
    
    fun logout() {
        sessionManager.clearSession()
        _isYouTubeConnected.value = false
        _youtubeSongs.value = emptyList()
        _likedSongs.value = emptyList()
        _youtubePlaylists.value = emptyList()
    }

    fun refresh(excludedFolders: Set<String> = emptySet(), manualScan: Boolean = false) {
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
                } else if (_youtubeSongs.value.isNotEmpty()) {
                    // Logged-out YouTube mode: refresh the taste-based feed too.
                    // (Gated on a non-empty feed so local-only users don't pay
                    // for network searches on every pull-to-refresh.)
                    val recs = recommendationEngine.getHomeRecommendations()
                    if (recs.isNotEmpty()) {
                        _youtubeSongs.value = recs
                    }
                }
                // Reload local songs with exclusions and playlists
                playlistRepository.refreshPlaylists()
                _songs.value = localRepository.getSongs(excludedFolders, manualScan)
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

    fun updateLocalPlaylist(playlistId: String, name: String, description: String?) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlistId, name, description)
        }
    }

    fun deleteLocalPlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun moveSongInLocalPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playlistRepository.moveSongInPlaylist(playlistId, fromIndex, toIndex)
        }
    }

    fun replaceLocalPlaylistSongs(playlistId: String, songs: List<Song>) {
        viewModelScope.launch {
            playlistRepository.replacePlaylistSongs(playlistId, songs)
        }
    }

    // Stats
    private val statsRepository = com.ivor.ivormusic.data.StatsRepository(application)
    private val _globalStats = MutableStateFlow(com.ivor.ivormusic.data.GlobalStats())
    val globalStats: StateFlow<com.ivor.ivormusic.data.GlobalStats> = _globalStats.asStateFlow()

    // Plays per day for the last 7 days, keyed "M/d" (see StatsRepository.getDailyPlays)
    private val _dailyPlays = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dailyPlays: StateFlow<Map<String, Int>> = _dailyPlays.asStateFlow()

    fun refreshStats() {
        viewModelScope.launch {
            _globalStats.value = statsRepository.getGlobalStats()
            _dailyPlays.value = statsRepository.getDailyPlays()
        }
    }

    // --- Search History Actions ---

    fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        searchHistoryRepository.addQuery(query)
        _searchHistory.value = searchHistoryRepository.getHistory()
    }

    fun removeFromSearchHistory(query: String) {
        searchHistoryRepository.removeQuery(query)
        _searchHistory.value = searchHistoryRepository.getHistory()
    }

    fun clearSearchHistory() {
        searchHistoryRepository.clearHistory()
        _searchHistory.value = emptyList()
    }
}
