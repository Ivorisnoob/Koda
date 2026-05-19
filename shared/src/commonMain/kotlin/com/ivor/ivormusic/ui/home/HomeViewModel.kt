package com.ivor.ivormusic.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.AppPreferences
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.LocalSongRepository
import com.ivor.ivormusic.data.PlaylistRepository
import com.ivor.ivormusic.data.SearchHistoryRepository
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.GlobalStats
import com.ivor.ivormusic.domain.ArtistItem
import com.ivor.ivormusic.domain.FolderInfo
import com.ivor.ivormusic.domain.PlaylistDisplayItem
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.VideoItem
import com.ivor.ivormusic.network.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val localRepository: LocalSongRepository,
    private val youtubeRepository: YouTubeRepository,
    private val playlistRepository: PlaylistRepository,
    private val sessionManager: SessionManager,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val likedSongsRepository: LikedSongsRepository,
    private val downloadRepository: DownloadRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

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
    val likedSongs: StateFlow<List<Song>> = combine(
        _likedSongs,
        _songs,
        likedSongsRepository.likedSongIds
    ) { ytLiked, localSongs, manuallyLikedIds ->
        val manuallyLikedLocal = localSongs.filter { it.id in manuallyLikedIds }
        (ytLiked + manuallyLikedLocal).distinctBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _youtubePlaylists = MutableStateFlow<List<PlaylistDisplayItem>>(emptyList())
    val userPlaylists: StateFlow<List<PlaylistDisplayItem>> = combine(
        _youtubePlaylists,
        playlistRepository.userPlaylists
    ) { ytPlaylists, localPlaylists ->
        localPlaylists.map { it.toDisplayItem() } + ytPlaylists
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localPlaylistIds: StateFlow<Set<String>> = playlistRepository.userPlaylists
        .map { playlists -> playlists.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _userAvatar = MutableStateFlow<String?>(null)
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    private val _trendingVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val trendingVideos: StateFlow<List<VideoItem>> = _trendingVideos.asStateFlow()

    private val _historyVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val historyVideos: StateFlow<List<VideoItem>> = _historyVideos.asStateFlow()

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()

    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    private val _globalStats = MutableStateFlow(GlobalStats())
    val globalStats: StateFlow<GlobalStats> = _globalStats.asStateFlow()

    init {
        checkYouTubeConnection()
    }

    fun loadSongs(excludedFolders: Set<String> = emptySet(), manualScan: Boolean = false) {
        viewModelScope.launch { _songs.value = localRepository.getSongs(excludedFolders, manualScan) }
    }

    suspend fun getAvailableFolders(): List<FolderInfo> = localRepository.getAvailableFolders()

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
            } catch (_: Exception) {}
        }
    }

    fun loadYouTubeRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val songs = if (sessionManager.isLoggedIn()) youtubeRepository.getRecommendations()
                            else youtubeRepository.search("Popular Music")
                if (songs.isNotEmpty()) _youtubeSongs.value = songs
            } catch (_: Exception) {}
            finally { _isLoading.value = false }
        }
    }

    suspend fun searchYouTube(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try { youtubeRepository.search(query) } catch (_: Exception) { emptyList() }
    }

    suspend fun loadMoreResults(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return try { youtubeRepository.searchNext(query) } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchPlaylistSongs(playlistId: String): List<Song> {
        val local = playlistRepository.userPlaylists.value.find { it.id == playlistId }
        if (local != null) return local.songs
        return try { youtubeRepository.getPlaylist(playlistId) } catch (_: Exception) { emptyList() }
    }

    suspend fun searchArtists(query: String): List<ArtistItem> = if (query.isBlank()) emptyList()
    else try { youtubeRepository.searchArtists(query) } catch (_: Exception) { emptyList() }

    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = if (query.isBlank()) emptyList()
    else try { youtubeRepository.searchAlbums(query) } catch (_: Exception) { emptyList() }

    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = if (query.isBlank()) emptyList()
    else try { youtubeRepository.searchPlaylists(query) } catch (_: Exception) { emptyList() }

    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> =
        try { youtubeRepository.getArtistDetails(artistId) } catch (_: Exception) { Pair(emptyList(), emptyList()) }

    suspend fun searchVideos(query: String): List<VideoItem> = if (query.isBlank()) emptyList()
    else try { youtubeRepository.searchVideos(query) } catch (_: Exception) { emptyList() }

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
                    youtubeRepository.fetchAccountInfo()
                    _userAvatar.value = sessionManager.getUserAvatar()
                    val recs = youtubeRepository.getRecommendations()
                    if (recs.isNotEmpty()) _youtubeSongs.value = recs
                    _likedSongs.value = youtubeRepository.getLikedMusic()
                    _youtubePlaylists.value = youtubeRepository.getUserPlaylists()
                }
                playlistRepository.refreshPlaylists()
                _songs.value = localRepository.getSongs(excludedFolders, manualScan)
            } catch (_: Exception) {}
            finally { _isLoading.value = false }
        }
    }

    fun loadTrendingVideos() {
        viewModelScope.launch {
            _isVideoLoading.value = true
            try { _trendingVideos.value = youtubeRepository.getTrendingVideos() } catch (_: Exception) {}
            finally { _isVideoLoading.value = false }
        }
    }

    fun loadYouTubeHistory() {
        if (!sessionManager.isLoggedIn()) { _historyVideos.value = emptyList(); return }
        viewModelScope.launch {
            _isHistoryLoading.value = true
            try { _historyVideos.value = youtubeRepository.getWatchHistory() } catch (_: Exception) {}
            finally { _isHistoryLoading.value = false }
        }
    }

    fun refreshVideos() = loadTrendingVideos()
    fun refreshStats() = viewModelScope.launch { _globalStats.value = statsRepository.getGlobalStats() }

    fun toggleDownload(song: Song) = viewModelScope.launch {
        if (downloadRepository.isDownloaded(song.id)) downloadRepository.deleteDownload(song.id)
        else downloadRepository.downloadSong(song)
    }
    fun isDownloaded(songId: String) = downloadRepository.isDownloaded(songId)
    fun isDownloading(songId: String) = downloadingIds.value.contains(songId)
    fun isLocalOriginal(song: Song) = downloadRepository.isLocalOriginal(song)

    fun createLocalPlaylist(name: String, description: String?) =
        viewModelScope.launch { playlistRepository.createPlaylist(name, description) }
    fun addSongToLocalPlaylist(playlistId: String, song: Song) =
        viewModelScope.launch { playlistRepository.addSongToPlaylist(playlistId, song) }
    fun updateLocalPlaylist(playlistId: String, name: String, description: String?) =
        viewModelScope.launch { playlistRepository.updatePlaylist(playlistId, name, description) }
    fun deleteLocalPlaylist(playlistId: String) =
        viewModelScope.launch { playlistRepository.deletePlaylist(playlistId) }
    fun moveSongInLocalPlaylist(playlistId: String, from: Int, to: Int) =
        viewModelScope.launch { playlistRepository.moveSongInPlaylist(playlistId, from, to) }
    fun replaceLocalPlaylistSongs(playlistId: String, songs: List<Song>) =
        viewModelScope.launch { playlistRepository.replacePlaylistSongs(playlistId, songs) }

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
