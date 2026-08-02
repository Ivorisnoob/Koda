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
    private val videoHistoryRepository = com.ivor.ivormusic.data.VideoHistoryRepository(application)
    private val themePreferences = com.ivor.ivormusic.data.ThemePreferences(application)

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

    private val _userName = MutableStateFlow<String?>(sessionManager.getUserName())
    val userName: StateFlow<String?> = _userName.asStateFlow()

    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository.getInstance(application)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // Recently played (from the local play history)
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    // Plays per song id, for the Library's "Most played" sort. Derived from the
    // same history read as the recents rail rather than a second file load.
    private val _playCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playCounts: StateFlow<Map<String, Int>> = _playCounts.asStateFlow()

    fun refreshRecentlyPlayed(limit: Int = 15) {
        viewModelScope.launch {
            val history = statsRepository.loadHistory() // newest first
            _playCounts.value = history.groupingBy { it.songId }.eachCount()
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

    private val _shortsFeed = MutableStateFlow<List<com.ivor.ivormusic.data.ShortsItem>>(emptyList())
    val shortsFeed: StateFlow<List<com.ivor.ivormusic.data.ShortsItem>> = _shortsFeed.asStateFlow()
    
    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()
    
    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    private val _isVideoLoadingMore = MutableStateFlow(false)
    val isVideoLoadingMore: StateFlow<Boolean> = _isVideoLoadingMore.asStateFlow()

    // Home feed paging: browse continuation token (logged-in personalized
    // feed) or watch-history seed offset (logged-out taste-based feed).
    private var videoFeedContinuation: String? = null
    private var tasteSeedOffset = 0
    private var videoFeedExhausted = false

    // Videos already put in front of the user this session, so a refresh can
    // skip them. FEwhat_to_watch barely moves between fetches - measured
    // against the live feed (August 2026), re-requesting page 1 came back 22
    // videos of which 16 had just been on screen - so a refresh that simply
    // replaced the list looked like nothing had happened. Continuation pages,
    // by contrast, were 100% new, which is what [refreshVideos] pulls from.
    private val shownVideoIds = LinkedHashSet<String>()

    // Subscriptions tab state
    private val _subscribedChannels = MutableStateFlow<List<com.ivor.ivormusic.data.SubscribedChannel>>(emptyList())
    val subscribedChannels: StateFlow<List<com.ivor.ivormusic.data.SubscribedChannel>> = _subscribedChannels.asStateFlow()

    private val _isSubscriptionsLoading = MutableStateFlow(false)
    val isSubscriptionsLoading: StateFlow<Boolean> = _isSubscriptionsLoading.asStateFlow()

    private val _subscriptionFeed = MutableStateFlow<List<VideoItem>>(emptyList())
    val subscriptionFeed: StateFlow<List<VideoItem>> = _subscriptionFeed.asStateFlow()

    private val _isSubscriptionFeedLoading = MutableStateFlow(false)
    val isSubscriptionFeedLoading: StateFlow<Boolean> = _isSubscriptionFeedLoading.asStateFlow()

    // Notifications state
    private val _notifications = MutableStateFlow<List<com.ivor.ivormusic.data.NotificationItem>>(emptyList())
    val notifications: StateFlow<List<com.ivor.ivormusic.data.NotificationItem>> = _notifications.asStateFlow()

    private val _isNotificationsLoading = MutableStateFlow(false)
    val isNotificationsLoading: StateFlow<Boolean> = _isNotificationsLoading.asStateFlow()

    // Video library tab state
    private val _videoPlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>>(emptyList())
    val videoPlaylists: StateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>> = _videoPlaylists.asStateFlow()

    private val _isVideoPlaylistsLoading = MutableStateFlow(false)
    val isVideoPlaylistsLoading: StateFlow<Boolean> = _isVideoPlaylistsLoading.asStateFlow()

    private val _playlistVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val playlistVideos: StateFlow<List<VideoItem>> = _playlistVideos.asStateFlow()

    private val _isPlaylistVideosLoading = MutableStateFlow(false)
    val isPlaylistVideosLoading: StateFlow<Boolean> = _isPlaylistVideosLoading.asStateFlow()

    init {
        checkYouTubeConnection()
    }

    /** Load the subscribed channels list (FEchannels). Requires login. */
    fun loadSubscriptions(force: Boolean = false) {
        if (_isSubscriptionsLoading.value) return
        if (_subscribedChannels.value.isNotEmpty() && !force) return
        viewModelScope.launch {
            _isSubscriptionsLoading.value = true
            try {
                _subscribedChannels.value = youtubeRepository.getSubscribedChannels()
            } finally {
                _isSubscriptionsLoading.value = false
            }
        }
    }

    /** Load the subscriptions video feed (latest uploads, newest first). Requires login. */
    fun loadSubscriptionFeed(force: Boolean = false) {
        if (_isSubscriptionFeedLoading.value) return
        if (_subscriptionFeed.value.isNotEmpty() && !force) return
        viewModelScope.launch {
            _isSubscriptionFeedLoading.value = true
            try {
                _subscriptionFeed.value = youtubeRepository.getSubscriptionsFeed()
            } finally {
                _isSubscriptionFeedLoading.value = false
            }
        }
    }

    /** Latest uploads of one subscribed channel (for the channel drill-in view). */
    suspend fun getChannelVideos(channel: com.ivor.ivormusic.data.SubscribedChannel): List<VideoItem> {
        return youtubeRepository.getChannelVideos(channel)
    }

    /** Load the user's YouTube playlists for the video Library tab. Requires login. */
    fun loadVideoPlaylists(force: Boolean = false) {
        if (_isVideoPlaylistsLoading.value) return
        if (_videoPlaylists.value.isNotEmpty() && !force) return
        viewModelScope.launch {
            _isVideoPlaylistsLoading.value = true
            try {
                _videoPlaylists.value = youtubeRepository.getVideoPlaylists()
            } finally {
                _isVideoPlaylistsLoading.value = false
            }
        }
    }

    /** Load one playlist's videos (also Watch Later "WL" / Liked videos "LL"). */
    fun loadPlaylistVideos(playlistId: String) {
        viewModelScope.launch {
            _playlistVideos.value = emptyList()
            _isPlaylistVideosLoading.value = true
            try {
                _playlistVideos.value = youtubeRepository.getPlaylistVideos(playlistId)
            } finally {
                _isPlaylistVideosLoading.value = false
            }
        }
    }

    /** Create a new YouTube playlist from the video Library tab. Requires login. */
    fun createVideoPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (youtubeRepository.createYouTubePlaylist(trimmed, music = false) != null) {
                loadVideoPlaylists(force = true)
            }
        }
    }

    /** Delete a YouTube playlist. Optimistic removal, restored on failure. */
    fun deleteVideoPlaylist(playlistId: String) {
        val previous = _videoPlaylists.value
        _videoPlaylists.value = previous.filterNot { it.playlistId == playlistId }
        viewModelScope.launch {
            if (!youtubeRepository.deleteYouTubePlaylist(playlistId, music = false)) {
                _videoPlaylists.value = previous
            }
        }
    }

    /**
     * Remove a video from a playlist, Watch Later ("WL") or Liked videos
     * ("LL", removes the like). Optimistic removal, restored on failure.
     */
    fun removePlaylistVideo(playlistId: String, video: VideoItem) {
        val previous = _playlistVideos.value
        _playlistVideos.value = previous.filterNot { it.videoId == video.videoId }
        viewModelScope.launch {
            if (!youtubeRepository.removeFromYouTubePlaylist(playlistId, video.videoId, music = false)) {
                _playlistVideos.value = previous
            }
        }
    }

    /**
     * Add a video to a YouTube playlist ("WL" = Watch Later). Reports the
     * outcome on the main thread so the save sheet can show inline
     * feedback. Requires login.
     */
    fun addVideoToPlaylist(playlistId: String, video: VideoItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(youtubeRepository.addToYouTubePlaylist(playlistId, video.videoId, music = false))
        }
    }

    /** Load the notification inbox. Requires login. */
    fun loadNotifications(force: Boolean = false) {
        if (_isNotificationsLoading.value) return
        if (_notifications.value.isNotEmpty() && !force) return
        viewModelScope.launch {
            _isNotificationsLoading.value = true
            try {
                _notifications.value = youtubeRepository.getNotifications()
            } finally {
                _isNotificationsLoading.value = false
            }
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
                _userName.value = sessionManager.getUserName()
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

    /** Related-songs radio seeded from a YouTube video id (works logged out). */
    suspend fun getRadioSongs(videoId: String): List<Song> {
        return try {
            youtubeRepository.getRelatedSongs(videoId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun logout() {
        sessionManager.clearSession()
        _isYouTubeConnected.value = false
        _userAvatar.value = null
        _userName.value = null
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
     * Also refreshes the Shorts shelf in parallel.
     */
    fun loadTrendingVideos() {
        loadShortsFeed()
        viewModelScope.launch {
            _isVideoLoading.value = true
            try {
                val page = youtubeRepository.getTrendingVideos()
                if (page.videos.isNotEmpty()) {
                    _trendingVideos.value = page.videos
                    videoFeedContinuation = page.continuation
                    // Taste-based page 1 seeds from the 6 most recent history
                    // entries; load-more continues from the 7th.
                    tasteSeedOffset = 6
                    videoFeedExhausted = false
                    rememberShown(page.videos)
                }
            } catch (e: Exception) {
                // Handle error silently
            } finally {
                _isVideoLoading.value = false
            }
        }
    }

    /**
     * Record videos as seen, keeping the newest [SHOWN_VIDEO_MEMORY] ids.
     * Bounded because the set only exists to keep consecutive refreshes from
     * repeating themselves, not to be a second watch history.
     */
    private fun rememberShown(videos: List<VideoItem>) {
        videos.forEach { shownVideoIds.add(it.videoId) }
        while (shownVideoIds.size > SHOWN_VIDEO_MEMORY) {
            shownVideoIds.remove(shownVideoIds.first())
        }
    }

    /**
     * Load the next page of the video home feed. Called when the grid scrolls
     * near its end (last ~5 items). Logged in this follows the InnerTube
     * browse continuation; logged out it mines older watch-history seeds for
     * more related videos. No-op while a load is already running or once the
     * feed is exhausted.
     */
    fun loadMoreTrendingVideos() {
        if (_isVideoLoading.value || _isVideoLoadingMore.value || videoFeedExhausted) return
        if (_trendingVideos.value.isEmpty()) return

        viewModelScope.launch {
            _isVideoLoadingMore.value = true
            try {
                val token = videoFeedContinuation
                val newVideos: List<VideoItem>
                if (token != null) {
                    val page = youtubeRepository.getVideoFeedContinuation(token)
                    videoFeedContinuation = page.continuation
                    newVideos = page.videos
                    if (page.videos.isEmpty() && page.continuation == null) {
                        videoFeedExhausted = true
                    }
                } else {
                    newVideos = youtubeRepository.getTasteBasedVideos(tasteSeedOffset)
                    tasteSeedOffset += 6
                    if (newVideos.isEmpty()) {
                        videoFeedExhausted = true
                    }
                }

                val onScreen = _trendingVideos.value.mapTo(HashSet()) { it.videoId }
                val fresh = newVideos.filterNot { it.videoId in onScreen }
                if (fresh.isNotEmpty()) {
                    _trendingVideos.value = _trendingVideos.value + fresh
                    rememberShown(fresh)
                }
            } catch (e: Exception) {
                // Handle error silently; the next scroll will retry
            } finally {
                _isVideoLoadingMore.value = false
            }
        }
    }

    /**
     * Load the Shorts shelf (personalized when logged in, search-seeded
     * otherwise). No-op unless the user opted into Shorts — fresh pref read,
     * since the settings screen toggles through its own ThemePreferences
     * instance. Failures leave the previous shelf in place.
     */
    fun loadShortsFeed() {
        if (!themePreferences.isShortsEnabled()) return
        viewModelScope.launch {
            try {
                val shorts = youtubeRepository.getShortsFeed()
                if (shorts.isNotEmpty()) {
                    _shortsFeed.value = shorts
                }
            } catch (e: Exception) {
                // Keep whatever shelf we already have
            }
        }
    }

    /**
     * Load user's watch history. Logged in: YouTube account history
     * (falling back to local). Logged out: locally persisted history.
     */
    fun loadYouTubeHistory() {
        if (!sessionManager.isLoggedIn()) {
             _historyVideos.value = videoHistoryRepository.getHistory()
             return
        }

        viewModelScope.launch {
            _isHistoryLoading.value = true
            try {
                val videos = youtubeRepository.getWatchHistory()
                _historyVideos.value = videos.ifEmpty { videoHistoryRepository.getHistory() }
            } catch (e: Exception) {
                _historyVideos.value = videoHistoryRepository.getHistory()
            } finally {
                _isHistoryLoading.value = false
            }
        }
    }
    
    /**
     * Search for videos (for video mode search).
     * [dateFilter] restricts results to the chosen upload-date window,
     * [sort] picks the result order.
     */
    suspend fun searchVideos(
        query: String,
        dateFilter: com.ivor.ivormusic.data.VideoSearchDateFilter = com.ivor.ivormusic.data.VideoSearchDateFilter.ANY,
        sort: com.ivor.ivormusic.data.VideoSearchSort = com.ivor.ivormusic.data.VideoSearchSort.RELEVANCE
    ): List<VideoItem> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchVideos(query, dateFilter, sort)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Next page of video search results. [dateFilter] must match the call that
     * produced the current results. Empty means there is nothing more to load.
     */
    suspend fun loadMoreVideoResults(
        query: String,
        dateFilter: com.ivor.ivormusic.data.VideoSearchDateFilter = com.ivor.ivormusic.data.VideoSearchDateFilter.ANY
    ): List<VideoItem> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchVideosNext(query, dateFilter)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for YouTube playlists (for video mode search).
     */
    suspend fun searchVideoPlaylists(query: String): List<com.ivor.ivormusic.data.VideoPlaylist> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchVideoPlaylists(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Refresh video mode content.
     */
    /**
     * Pull-to-refresh for the video home feed.
     *
     * Deliberately not a plain re-run of [loadTrendingVideos]. YouTube's
     * FEwhat_to_watch page 1 is close to static between fetches, so replacing
     * the list with it showed the user the videos they had just scrolled past
     * and the refresh read as broken. Measured against the live feed in August
     * 2026: a page-1 refetch returned 22 videos, 16 of them already on screen,
     * while the continuation returned an entire page of new ones.
     *
     * So this takes whatever page 1 offers that is genuinely new, then walks
     * forward through the feed until there is a screenful of unseen videos.
     * When the feed really is exhausted it falls back to page 1 rather than
     * emptying the screen.
     */
    fun refreshVideos() {
        loadShortsFeed()
        viewModelScope.launch {
            _isVideoLoading.value = true
            try {
                val page = youtubeRepository.getTrendingVideos()
                if (page.videos.isEmpty()) return@launch

                val fresh = mutableListOf<VideoItem>()
                val batchIds = HashSet<String>()
                fun takeUnseen(videos: List<VideoItem>) {
                    videos.forEach { video ->
                        if (video.videoId !in shownVideoIds && batchIds.add(video.videoId)) {
                            fresh += video
                        }
                    }
                }
                takeUnseen(page.videos)

                var continuation = page.continuation
                var pagesWalked = 0
                while (fresh.size < MIN_FRESH_VIDEOS_ON_REFRESH &&
                    pagesWalked < MAX_REFRESH_PAGES
                ) {
                    val token = continuation
                    val more = if (token != null) {
                        val next = youtubeRepository.getVideoFeedContinuation(token)
                        continuation = next.continuation
                        next.videos
                    } else {
                        // Logged out there is no token: page the taste-based
                        // feed by seed window instead, wrapping back to the
                        // newest history entries once the seeds run out.
                        tasteSeedOffset += 6
                        val seeded = youtubeRepository.getTasteBasedVideos(tasteSeedOffset)
                        if (seeded.isEmpty()) {
                            tasteSeedOffset = 0
                            youtubeRepository.getTasteBasedVideos(0)
                        } else {
                            seeded
                        }
                    }
                    pagesWalked++
                    if (more.isEmpty()) break
                    takeUnseen(more)
                }

                // Everything the feed has to offer is already seen. Showing
                // page 1 again beats showing nothing.
                val result = fresh.ifEmpty { page.videos }
                _trendingVideos.value = result
                videoFeedContinuation = continuation
                videoFeedExhausted = false
                rememberShown(result)
            } catch (e: Exception) {
                // Handle error silently; the list keeps its previous contents
            } finally {
                _isVideoLoading.value = false
            }
        }
    }

    // ============= PASTED YOUTUBE LINK RESOLUTION =============

    /**
     * Resolve a pasted YouTube video link into displayable metadata via a
     * single watch-next call (title, channel, view count — the same data the
     * video player enriches from). Returns null when the video can't be
     * loaded (bad id, private video, offline).
     */
    suspend fun resolveVideoFromLink(videoId: String): VideoItem? {
        return try {
            youtubeRepository.getWatchNextData(videoId).updatedVideoItem
        } catch (e: Exception) {
            null
        }
    }

    /** Resolve a pasted playlist link into songs (music mode). */
    suspend fun resolvePlaylistSongsFromLink(playlistId: String): List<Song> {
        return try {
            youtubeRepository.getPlaylist(playlistId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Resolve a pasted playlist link into videos (video mode). */
    suspend fun resolvePlaylistVideosFromLink(playlistId: String): List<VideoItem> {
        return try {
            youtubeRepository.getPlaylistVideos(playlistId)
        } catch (e: Exception) {
            emptyList()
        }
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

    // --- YouTube Music playlist editing (music.youtube.com side) ---

    /** Rename a YouTube Music playlist; the local list entry updates on success. */
    fun renameYouTubePlaylist(playlistId: String, name: String, description: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val ok = youtubeRepository.renameYouTubePlaylist(
                playlistId, trimmed, music = true, description = description
            )
            if (ok) {
                _youtubePlaylists.value = _youtubePlaylists.value.map {
                    if (it.id == playlistId) it.copy(name = trimmed, description = description) else it
                }
            }
        }
    }

    /** Delete a YouTube Music playlist. Optimistic removal, restored on failure. */
    fun deleteYouTubePlaylist(playlistId: String) {
        val previous = _youtubePlaylists.value
        _youtubePlaylists.value = previous.filterNot { it.id == playlistId }
        viewModelScope.launch {
            if (!youtubeRepository.deleteYouTubePlaylist(playlistId, music = true)) {
                _youtubePlaylists.value = previous
            }
        }
    }

    /** Remove a song from a YouTube Music playlist ("LM" removes the like). */
    fun removeSongFromYouTubePlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            youtubeRepository.removeFromYouTubePlaylist(playlistId, song.id, music = true)
        }
    }

    /**
     * Per-row playlist item ids (videoId -> setVideoId) needed to reorder a
     * YouTube Music playlist; empty when signed out or on failure.
     */
    suspend fun fetchYouTubePlaylistSetVideoIds(playlistId: String): Map<String, String> =
        youtubeRepository.getPlaylistSetVideoIds(playlistId)

    /**
     * Move a row of a YouTube Music playlist before the row identified by
     * successorSetVideoId (null appends at the end). Returns false when the
     * server rejected the move so the caller can resync.
     */
    suspend fun moveSongInYouTubePlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?
    ): Boolean = youtubeRepository.moveInYouTubePlaylist(
        playlistId, setVideoId, successorSetVideoId, music = true
    )

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

    private companion object {
        /** Ids kept in [shownVideoIds] before the oldest are forgotten. */
        const val SHOWN_VIDEO_MEMORY = 400

        /** A refresh stops walking the feed once it has this many new videos. */
        const val MIN_FRESH_VIDEOS_ON_REFRESH = 15

        /**
         * Cap on continuation fetches per refresh. The live feed ran out of
         * continuation tokens after roughly 50 videos, so this bounds a refresh
         * at about that depth instead of hammering the API.
         */
        const val MAX_REFRESH_PAGES = 3
    }
}
