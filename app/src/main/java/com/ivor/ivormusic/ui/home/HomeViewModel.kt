package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.util.KLog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ivor.ivormusic.ui.theme.playlistCoverSeeds
import androidx.lifecycle.viewModelScope
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.FolderInfo
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.PlaylistPageInfo
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.data.LikedSongsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal fun shouldWarmSubscriptionFeed(
    source: String,
    hasLocalSubscriptions: Boolean,
    isLoggedIn: Boolean
): Boolean = when (source) {
    com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_LOCAL -> hasLocalSubscriptions
    com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_YOUTUBE -> isLoggedIn
    else -> hasLocalSubscriptions || isLoggedIn
}

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

    /**
     * An artist page asked for from outside the Home screen - today, the
     * "Open music artist page" cross-link on a creator's channel page.
     *
     * The Library owns artist detail and is handed one through `initialArtist`,
     * which is state inside `HomeScreen` and therefore unreachable from a
     * NavHost destination sitting beside it. This is the same hand-off, one
     * level up: set from anywhere, consumed by `HomeScreen` the moment it
     * routes to the Library tab, and cleared so returning to Library later
     * lands on the list rather than re-opening the artist.
     */
    private val _pendingArtistPage = MutableStateFlow<String?>(null)
    val pendingArtistPage: StateFlow<String?> = _pendingArtistPage.asStateFlow()

    fun requestArtistPage(artistName: String) {
        _pendingArtistPage.value = artistName.takeIf { it.isNotBlank() }
    }

    fun consumeArtistPageRequest() {
        _pendingArtistPage.value = null
    }

    /**
     * A playlist page asked for from outside the Home screen - today, a
     * playlist link shared or opened into the app.
     *
     * The same hand-off as [pendingArtistPage] and for the same reason: both
     * playlist pages live inside the tab system, which a share intent arriving
     * at `MainActivity` cannot reach. Two flows rather than one tagged value
     * because the two modes land on different tabs and hold different types,
     * and a share names which mode it wants by the link it carries.
     */
    private val _pendingPlaylistPage = MutableStateFlow<PlaylistDisplayItem?>(null)
    val pendingPlaylistPage: StateFlow<PlaylistDisplayItem?> = _pendingPlaylistPage.asStateFlow()

    private val _pendingVideoPlaylistPage = MutableStateFlow<VideoPlaylist?>(null)
    val pendingVideoPlaylistPage: StateFlow<VideoPlaylist?> = _pendingVideoPlaylistPage.asStateFlow()

    fun requestPlaylistPage(info: PlaylistPageInfo) {
        _pendingPlaylistPage.value = info.toDisplayItem()
    }

    fun consumePlaylistPageRequest() {
        _pendingPlaylistPage.value = null
    }

    fun requestVideoPlaylistPage(info: PlaylistPageInfo) {
        _pendingVideoPlaylistPage.value = info.toVideoPlaylist()
    }

    fun consumeVideoPlaylistPageRequest() {
        _pendingVideoPlaylistPage.value = null
    }

    /**
     * What a shared playlist link points at, as the page that opens it needs to
     * describe itself. Null when the id has no page behind it - a generated
     * mix, a private or deleted list - which is the caller's cue to fall back
     * to playing rather than opening.
     */
    suspend fun resolvePlaylistPageFromLink(playlistId: String): PlaylistPageInfo? {
        return try {
            youtubeRepository.getPlaylistHeader(playlistId)
        } catch (e: Exception) {
            null
        }
    }

    private val _searchHistory = MutableStateFlow(searchHistoryRepository.getHistory())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private val _youtubeSongs = MutableStateFlow<List<Song>>(emptyList())
    val youtubeSongs: StateFlow<List<Song>> = _youtubeSongs.asStateFlow()
    


    private val _isYouTubeConnected = MutableStateFlow(false)

    /**
     * A YouTube session that actually authenticates. A session YouTube has
     * rejected reads as disconnected, so account-only screens offer the sign-in
     * wall instead of sitting on an empty list with no explanation.
     */
    val isYouTubeConnected: StateFlow<Boolean> =
        combine(_isYouTubeConnected, SessionManager.sessionExpired) { connected, expired ->
            connected && !expired
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
    
    // Playlists and albums saved from search or an artist page: references,
    // fetched live when opened. See SavedPlaylistsRepository.
    private val savedPlaylistsRepository =
        com.ivor.ivormusic.data.SavedPlaylistsRepository(application)

    // Video playlists held on the device, with the videos embedded. The video
    // counterpart of playlistRepository, and the reason video mode can save
    // anything at all signed out. See LocalVideoPlaylistsRepository.
    private val localVideoPlaylistsRepository =
        com.ivor.ivormusic.data.LocalVideoPlaylistsRepository(application)

    // Merged Playlists (Local + Saved + YouTube)
    //
    // Saved sit between the two because that is what they are: not the user's
    // own, but kept deliberately, so they belong above the account's own list
    // rather than lost at the end of it.
    val userPlaylists: StateFlow<List<com.ivor.ivormusic.data.PlaylistDisplayItem>> = combine(
        _youtubePlaylists,
        playlistRepository.userPlaylists,
        savedPlaylistsRepository.savedPlaylists
    ) { ytPlaylists, localPlaylists, savedPlaylists ->
        val localItems = localPlaylists.map { it.toDisplayItem() }
        // A playlist kept locally that also turns up in the account's own
        // library would otherwise appear twice in the grid.
        val accountIds = ytPlaylists.map { it.id }.toSet()
        val savedItems = savedPlaylists
            .filterNot { it.id in accountIds }
            .map { it.toDisplayItem() }
        localItems + savedItems + ytPlaylists
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    
    val localPlaylistIds: StateFlow<Set<String>> = playlistRepository.userPlaylists
        .map { playlists -> playlists.map { it.id }.toSet() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Ids of playlists kept as references rather than owned.
     *
     * The UI needs this and not just "is it in the library": a saved playlist
     * is somebody else's, so the rename and delete actions a library playlist
     * normally offers would be writes against a playlist the user has no rights
     * to. Anything the account genuinely owns is excluded, so an id here is
     * always a reference.
     */
    val savedPlaylistIds: StateFlow<Set<String>> = combine(
        savedPlaylistsRepository.savedPlaylists,
        _youtubePlaylists
    ) { saved, ytPlaylists ->
        val accountIds = ytPlaylists.map { it.id }.toSet()
        saved.map { it.id }.filterNot { it in accountIds }.toSet()
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    fun isPlaylistSaved(playlistId: String?): Boolean = savedPlaylistsRepository.isSaved(playlistId)

    /**
     * Keep or drop [playlist]. Returns the state after the toggle.
     *
     * Synchronous: the store is one preference write, and the button has to
     * settle in the frame it was tapped in.
     */
    fun toggleSavedPlaylist(
        playlist: com.ivor.ivormusic.data.PlaylistDisplayItem,
        isAlbum: Boolean = false
    ): Boolean = savedPlaylistsRepository.toggle(
        com.ivor.ivormusic.data.SavedPlaylist(
            id = playlist.id,
            url = playlist.url,
            name = playlist.name,
            uploaderName = playlist.uploaderName,
            thumbnailUrl = playlist.thumbnailUrl,
            itemCount = playlist.itemCount,
            isAlbum = isAlbum
        )
    )

    fun removeSavedPlaylist(playlistId: String) = savedPlaylistsRepository.remove(playlistId)

    private val _userAvatar = MutableStateFlow<String?>(sessionManager.getUserAvatar())
    val userAvatar: StateFlow<String?> = _userAvatar.asStateFlow()

    private val _userName = MutableStateFlow<String?>(sessionManager.getUserName())
    val userName: StateFlow<String?> = _userName.asStateFlow()

    // Downloads
    private val downloadRepository = com.ivor.ivormusic.data.DownloadRepository.getInstance(application)
    val downloadedSongs = downloadRepository.downloadedSongs
    val downloadedVideos = downloadRepository.downloadedVideos
    val downloadingIds = downloadRepository.downloadingIds
    val downloadProgress = downloadRepository.downloadProgress

    // Recently played (from the local play history)
    private val _recentlyPlayed = MutableStateFlow<List<Song>>(emptyList())
    val recentlyPlayed: StateFlow<List<Song>> = _recentlyPlayed.asStateFlow()

    // Plays per song id, for the Library's "Most played" sort. Derived from the
    // same history read as the recents rail rather than a second file load.
    private val _playCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val playCounts: StateFlow<Map<String, Int>> = _playCounts.asStateFlow()

    // Songs sitting in the stream cache in full, so they play with no network.
    // See ReadyOfflineRepository for why this is a state rather than a playlist.
    private val readyOfflineRepository =
        com.ivor.ivormusic.data.ReadyOfflineRepository(application)
    private val _readyOffline =
        MutableStateFlow(com.ivor.ivormusic.data.ReadyOfflineRepository.Result())
    val readyOffline: StateFlow<com.ivor.ivormusic.data.ReadyOfflineRepository.Result> =
        _readyOffline.asStateFlow()

    /**
     * Re-read the cache and resolve it against the play history.
     *
     * Pulled rather than observed: the cache has no change notification, and
     * polling it would mean walking every key on a timer for a list nobody is
     * looking at. The Library refreshes it on open, which is the only place it
     * is shown.
     */
    fun refreshReadyOffline() {
        viewModelScope.launch {
            _readyOffline.value = readyOfflineRepository.load(
                downloadedIds = downloadedSongs.value.map { it.id }.toSet()
            )
        }
    }

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
    private val notInterestedRepository =
        com.ivor.ivormusic.data.NotInterestedRepository(application)

    /** Local hide plus best-effort account propagation - see NotInterestedActions. */
    private val notInterestedActions =
        com.ivor.ivormusic.data.NotInterestedActions(notInterestedRepository, youtubeRepository)

    /**
     * Raw feed as fetched. Everything user-facing reads [trendingVideos]
     * instead, which subtracts what the user asked not to see.
     */
    private val _trendingVideos = MutableStateFlow<List<VideoItem>>(emptyList())

    /**
     * The home feed with hidden videos and blocked channels removed.
     *
     * Filtering is a derived flow rather than a write into the raw list, so a
     * "not interested" tap takes the item off screen on the next frame with no
     * refetch, and Undo puts it straight back where it was. Doing it the other
     * way - mutating the fetched list - would make undo a re-fetch, and the
     * video would come back in a different position or not at all.
     */
    val trendingVideos: StateFlow<List<VideoItem>> =
        combine(
            _trendingVideos,
            notInterestedRepository.hiddenVideos,
            notInterestedRepository.blockedChannels
        ) { videos, _, _ -> notInterestedRepository.filter(videos) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    private val _historyVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val historyVideos: StateFlow<List<VideoItem>> = _historyVideos.asStateFlow()

    private val _shortsFeed = MutableStateFlow<List<com.ivor.ivormusic.data.ShortsItem>>(emptyList())

    /**
     * Shorts shelf minus individually hidden Shorts.
     *
     * Only video ids can be filtered here: a shelf ShortsItem carries no
     * channel at all (see ShortsItem), so a channel block cannot reach it.
     * The block still applies the moment the Short is opened and enriched -
     * it just cannot pre-empt the shelf.
     */
    val shortsFeed: StateFlow<List<com.ivor.ivormusic.data.ShortsItem>> =
        combine(_shortsFeed, notInterestedRepository.hiddenVideos) { shorts, _ ->
            shorts.filterNot { notInterestedRepository.isVideoHidden(it.videoId) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading: StateFlow<Boolean> = _isHistoryLoading.asStateFlow()
    
    private val _isVideoLoading = MutableStateFlow(false)
    val isVideoLoading: StateFlow<Boolean> = _isVideoLoading.asStateFlow()

    /**
     * True when the latest empty Home fetch failed without a validated network.
     * Kept separate from an empty online response so the UI can offer completed
     * downloads only when they are actually the useful fallback.
     */
    private val _isVideoHomeOffline = MutableStateFlow(false)
    val isVideoHomeOffline: StateFlow<Boolean> = _isVideoHomeOffline.asStateFlow()

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

    // ---------------- Subscriptions tab ----------------

    private val localSubscriptionsRepository =
        com.ivor.ivormusic.data.LocalSubscriptionsRepository(application)

    /** Channels from the signed-in Google account (FEchannels). */
    private val _accountChannels = MutableStateFlow<List<com.ivor.ivormusic.data.SubscribedChannel>>(emptyList())

    /** Channels followed on this device. Process-wide, so a subscribe anywhere lands here. */
    val localSubscriptions: StateFlow<List<com.ivor.ivormusic.data.LocalSubscription>> =
        localSubscriptionsRepository.subscriptions

    val subscriptionGroups: StateFlow<List<com.ivor.ivormusic.data.SubscriptionGroup>> =
        localSubscriptionsRepository.groups

    /** Which group filters the feed, or null for everything. */
    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    /**
     * The channel list the Subscriptions tab shows, resolved from the source
     * setting. On "auto" both lists are merged: someone who imported a list
     * *and* signed in wants both, and a channel followed in both places must
     * appear once, so the merge dedupes on channel id with the local entry
     * winning (it carries the avatar an import backfilled).
     */
    val subscribedChannels: StateFlow<List<com.ivor.ivormusic.data.SubscribedChannel>> =
        combine(
            _accountChannels,
            localSubscriptions,
            themePreferences.subscriptionSource
        ) { account, local, source ->
            val localAsChannels = local.map { it.toSubscribedChannel() }
            when (source) {
                com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_LOCAL -> localAsChannels
                com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_YOUTUBE -> account
                else -> (localAsChannels + account).distinctBy { it.channelId }
            }.sortedBy { it.name.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isSubscriptionsLoading = MutableStateFlow(false)
    val isSubscriptionsLoading: StateFlow<Boolean> = _isSubscriptionsLoading.asStateFlow()

    private val _subscriptionFeed = MutableStateFlow<List<VideoItem>>(emptyList())

    /**
     * The subscriptions feed, minus what the user asked not to see. A channel
     * block does apply here even though the user follows the channel: the two
     * are different statements, and someone who blocks a channel they follow
     * has been unambiguous about it.
     */
    val subscriptionFeed: StateFlow<List<VideoItem>> =
        combine(
            _subscriptionFeed,
            notInterestedRepository.hiddenVideos,
            notInterestedRepository.blockedChannels
        ) { videos, _, _ -> notInterestedRepository.filter(videos) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isSubscriptionFeedLoading = MutableStateFlow(false)
    val isSubscriptionFeedLoading: StateFlow<Boolean> = _isSubscriptionFeedLoading.asStateFlow()

    // A force request can arrive while a large imported list is still being
    // fetched (for example, the import finishes during startup warm-up). The
    // old guard dropped it outright, leaving the newly added channels absent
    // until the user pulled manually. Coalesce those requests into one follow-
    // up pass instead of running two hundred-channel refreshes concurrently.
    private var subscriptionFeedRefreshPending = false

    private val _selectedChannelFeed = MutableStateFlow<List<VideoItem>>(emptyList())
    val selectedChannelFeed: StateFlow<List<VideoItem>> = _selectedChannelFeed.asStateFlow()

    private val _isSelectedChannelFeedLoading = MutableStateFlow(false)
    val isSelectedChannelFeedLoading: StateFlow<Boolean> =
        _isSelectedChannelFeedLoading.asStateFlow()

    private val _selectedChannelFeedError = MutableStateFlow<String?>(null)
    val selectedChannelFeedError: StateFlow<String?> = _selectedChannelFeedError.asStateFlow()
    private var selectedChannelFeedJob: kotlinx.coroutines.Job? = null

    /**
     * "42 of 130 channels" while a local refresh runs. A device-local feed
     * costs one request per channel, so a large list takes long enough that an
     * indeterminate spinner reads as a hang.
     */
    private val _subscriptionFeedProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val subscriptionFeedProgress: StateFlow<Pair<Int, Int>?> = _subscriptionFeedProgress.asStateFlow()

    /** Set when a refresh produced nothing and the network was the reason. */
    private val _subscriptionFeedError = MutableStateFlow<String?>(null)
    val subscriptionFeedError: StateFlow<String?> = _subscriptionFeedError.asStateFlow()

    // Notifications state
    private val _notifications = MutableStateFlow<List<com.ivor.ivormusic.data.NotificationItem>>(emptyList())
    val notifications: StateFlow<List<com.ivor.ivormusic.data.NotificationItem>> = _notifications.asStateFlow()

    private val _isNotificationsLoading = MutableStateFlow(false)
    val isNotificationsLoading: StateFlow<Boolean> = _isNotificationsLoading.asStateFlow()

    // Video library tab state. This half is the signed-in account's own
    // playlists; the device's are merged in by [videoPlaylists] below.
    private val _videoPlaylists = MutableStateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>>(emptyList())

    /**
     * Every playlist a video can be saved into: the device's own first, then
     * the account's.
     *
     * Local ones lead because they are the user's own creations and, signed
     * out, the only ones there are - the same order the music Library merges
     * its three kinds in. Consumers tell them apart with
     * [com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.isLocal]; nothing
     * here may assume a playlist id addresses YouTube.
     */
    val videoPlaylists: StateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>> = combine(
        localVideoPlaylistsRepository.playlists,
        _videoPlaylists
    ) { local, account ->
        local.map { it.toVideoPlaylist() } + account
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isVideoPlaylistsLoading = MutableStateFlow(false)
    val isVideoPlaylistsLoading: StateFlow<Boolean> = _isVideoPlaylistsLoading.asStateFlow()

    /**
     * The device's video playlists with their videos still attached, which is
     * what [videoPlaylists] drops on the way to [com.ivor.ivormusic.data.VideoPlaylist].
     *
     * Exposed so the options sheet can mark the playlists a video is already
     * in. It stops at the device's own on purpose: the account's would need a
     * playlist browse each, which is one request per row for a checkmark, and
     * saving twice is a no-op on both sides anyway.
     */
    val localVideoPlaylists: StateFlow<List<com.ivor.ivormusic.data.LocalVideoPlaylist>> =
        localVideoPlaylistsRepository.playlists

    /**
     * The saved playlists again, shaped for video mode's Library list and
     * playlist page. One store feeds both modes, so anything kept in music mode
     * is here too - see [com.ivor.ivormusic.data.SavedPlaylistsRepository].
     *
     * Excludes what the account already owns *on the video side*, for the
     * reason [savedPlaylistIds] excludes the music side's: a playlist that is
     * genuinely the user's would otherwise sit in the list twice, once as
     * theirs and once as a reference offering to remove it from the library.
     *
     * Albums are the one thing that does not cross over: they are saved by
     * browse id ("MPRE..."), which is not a playlist and does not resolve
     * through the video playlist call, so a saved album shown here would open
     * an empty page. Video mode has no album page to send it to either.
     *
     * Declared here rather than beside the other saved-playlist members because
     * it reads [_videoPlaylists], and a property initialized before the one it
     * combines with gets null.
     */
    val savedVideoPlaylists: StateFlow<List<com.ivor.ivormusic.data.VideoPlaylist>> = combine(
        savedPlaylistsRepository.savedPlaylists,
        _videoPlaylists
    ) { saved, accountPlaylists ->
        val accountIds = accountPlaylists.map { it.playlistId }.toSet()
        saved.filterNot { it.isAlbum || it.id in accountIds }.map { it.toVideoPlaylist() }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ids of [savedVideoPlaylists], for marking a row or a page as kept. */
    val savedVideoPlaylistIds: StateFlow<Set<String>> = savedVideoPlaylists
        .map { playlists -> playlists.map { it.playlistId }.toSet() }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * Keep or drop a playlist found in video mode. Returns the state after the
     * toggle, synchronous for the same reason [toggleSavedPlaylist] is.
     */
    fun toggleSavedVideoPlaylist(playlist: com.ivor.ivormusic.data.VideoPlaylist): Boolean =
        savedPlaylistsRepository.toggle(com.ivor.ivormusic.data.SavedPlaylist.from(playlist))

    private val _playlistVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val playlistVideos: StateFlow<List<VideoItem>> = _playlistVideos.asStateFlow()

    private val _isPlaylistVideosLoading = MutableStateFlow(false)
    val isPlaylistVideosLoading: StateFlow<Boolean> = _isPlaylistVideosLoading.asStateFlow()

    init {
        observeSubscriptionFeedWarmup()
        checkYouTubeConnection()
        observeProfileSwitches()
    }

    /**
     * Warm the Subscriptions feed from its real inputs, independently of the
     * tab's composition. Local imports are already persisted and available at
     * ViewModel construction, so a signed-out user now starts fetching their
     * device feed as the app opens and sees it ready (or already progressing)
     * when they visit Subscriptions.
     *
     * Only channel ids participate in the key. Avatar/profile backfill rewrites
     * the same subscriptions and must not restart a potentially large feed.
     */
    private fun observeSubscriptionFeedWarmup() {
        viewModelScope.launch {
            combine(
                localSubscriptions
                    .map { subscriptions -> subscriptions.map { it.channelId }.sorted() }
                    .distinctUntilChanged(),
                themePreferences.subscriptionSource,
                themePreferences.fastSubscriptionFeed
            ) { localIds, source, fastMode -> Triple(localIds, source, fastMode) }
                .distinctUntilChanged()
                .collect { (localIds, source, _) ->
                    if (shouldWarmSubscriptionFeed(
                            source = source,
                            hasLocalSubscriptions = localIds.isNotEmpty(),
                            isLoggedIn = sessionManager.isLoggedIn()
                        )
                    ) {
                        loadSubscriptionFeed(force = true)
                    } else {
                        _subscriptionFeed.value = emptyList()
                        _subscriptionFeedError.value = null
                    }
                }
        }
    }

    /**
     * Reload everything account-derived when the active profile changes.
     *
     * There is no DI, so a switch cannot reach this ViewModel directly - it
     * watches the process-wide id instead, the same pattern the subscription
     * and blocklist stores use. Without this the app would keep showing the
     * previous account's feeds, playlists and name under the new identity,
     * which is the single most visible way an account switcher can be wrong.
     *
     * `drop(1)` because the flow replays the current profile on subscribe and
     * that is not a switch; `checkYouTubeConnection()` already covers startup.
     */
    private fun observeProfileSwitches() {
        viewModelScope.launch {
            com.ivor.ivormusic.data.ProfileManager(getApplication())
                .activeProfileId
                .drop(1)
                .distinctUntilChanged()
                .collect { resetForProfileChange() }
        }
    }

    /**
     * Drop the previous profile's state and refetch for the new one.
     *
     * Mirrors [logout]'s clearing - the same flows go stale for the same
     * reason - but follows it with a reload rather than leaving the app empty.
     */
    private fun resetForProfileChange() {
        youtubeRepository.clearSessionScopedInstanceCaches()

        // Identity first, so the avatar and name change on the next frame
        // rather than after the feeds have finished loading.
        _userAvatar.value = sessionManager.getUserAvatar()
        _userName.value = sessionManager.getUserName()
        _isYouTubeConnected.value = sessionManager.isLoggedIn()

        _youtubeSongs.value = emptyList()
        _likedSongs.value = emptyList()
        _youtubePlaylists.value = emptyList()
        _accountChannels.value = emptyList()
        _subscriptionFeed.value = emptyList()

        // Both modes are emptied, not just the visible one. Toggling modes
        // refetches on its own (HomeScreen's LaunchedEffect(videoMode)), but
        // the old list would stay on screen until that lands - so switching
        // account and flipping to video mode would show the previous account's
        // feed for as long as the fetch takes. These loaders also only assign
        // when the result is non-empty, so clearing is what guarantees a failed
        // refetch leaves nothing rather than the wrong account's videos.
        _trendingVideos.value = emptyList()
        _shortsFeed.value = emptyList()
        _historyVideos.value = emptyList()

        checkYouTubeConnection()
        loadSubscriptions(force = true)
        loadSubscriptionFeed(force = true)

        // Refresh whichever home the user is actually on. Same split the
        // sign-in handler uses, so a switch and a fresh login behave alike.
        // loadTrendingVideos already pulls the Shorts shelf itself.
        if (themePreferences.videoMode.value) {
            loadTrendingVideos()
            loadYouTubeHistory()
        } else {
            loadYouTubeRecommendations()
        }
    }

    /**
     * Load the account's subscribed channel list (FEchannels). Local
     * subscriptions need no loading - they are already in memory - so this is
     * a no-op when the source setting excludes the account or nobody is
     * signed in.
     */
    fun loadSubscriptions(force: Boolean = false) {
        // First, and outside every guard below: imported channels have no
        // avatar until this runs, and the guards are all about the *account*
        // half. Behind them, a signed-out user - the exact person most likely
        // to have imported a list - never got any pictures.
        backfillLocalChannelProfiles()

        if (_isSubscriptionsLoading.value) return
        if (!shouldUseAccountSubscriptions()) return
        if (_accountChannels.value.isNotEmpty() && !force) return
        viewModelScope.launch {
            _isSubscriptionsLoading.value = true
            try {
                _accountChannels.value = youtubeRepository.getSubscribedChannels()
            } finally {
                _isSubscriptionsLoading.value = false
            }
        }
    }

    /**
     * Load the subscriptions feed, newest first.
     *
     * The two halves come from completely different places: YouTube builds
     * the account feed server side in one browse call, while the device feed
     * has to be merged from one request per followed channel. Both are pulled
     * when the source setting asks for both, and interleaved on upload time so
     * the result reads as one feed rather than two stacked lists.
     */
    /**
     * Whether the device has a usable network right now.
     *
     * Used only to word a failure correctly: an empty feed on a working
     * connection is a real empty feed, not something the user can fix by
     * checking their wifi.
     */
    private fun hasNetworkConnection(): Boolean = try {
        val cm = getApplication<Application>()
            .getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        // Unknown is treated as connected, so a permissions or API oddity does
        // not turn every empty feed into a bogus "check your connection".
        true
    }

    fun loadSubscriptionFeed(force: Boolean = false) {
        if (_isSubscriptionFeedLoading.value) {
            if (force) subscriptionFeedRefreshPending = true
            return
        }
        if (_subscriptionFeed.value.isNotEmpty() && !force) return
        // Claim the refresh before launching so two callers in the same main-
        // thread frame cannot both pass the guard and start duplicate work.
        _isSubscriptionFeedLoading.value = true
        viewModelScope.launch {
            _subscriptionFeedError.value = null
            try {
                val source = themePreferences.currentSubscriptionSource()
                val useAccount = source != com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_LOCAL &&
                    sessionManager.isLoggedIn()
                val useLocal = source != com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_YOUTUBE

                val accountFeed = if (useAccount) youtubeRepository.getSubscriptionsFeed() else emptyList()

                val channels = if (useLocal) groupFilteredLocalChannels() else emptyList()
                val localFeed = if (channels.isNotEmpty()) {
                    _subscriptionFeedProgress.value = 0 to channels.size
                    youtubeRepository.getLocalSubscriptionsFeed(
                        channels = channels,
                        fastMode = themePreferences.isFastSubscriptionFeedEnabled()
                    ) { done, total -> _subscriptionFeedProgress.value = done to total }
                } else emptyList()

                _subscriptionFeed.value = mergeFeeds(accountFeed, localFeed)
                if (_subscriptionFeed.value.isEmpty() && (useAccount || channels.isNotEmpty())) {
                    // Only blame the connection when nothing was reachable.
                    // Channels that answer but have nothing recent are a normal
                    // empty feed, and telling someone to check a connection that
                    // is plainly working sends them fixing the wrong thing.
                    _subscriptionFeedError.value = if (hasNetworkConnection()) {
                        "No recent uploads from the channels you follow."
                    } else {
                        "Couldn't load recent uploads. Check your connection and try again."
                    }
                }
            } catch (e: Exception) {
                KLog.e("HomeViewModel", "Subscription feed refresh failed", e)
                _subscriptionFeedError.value =
                    "Couldn't load recent uploads. Check your connection and try again."
            } finally {
                _subscriptionFeedProgress.value = null
                _isSubscriptionFeedLoading.value = false
                if (subscriptionFeedRefreshPending) {
                    subscriptionFeedRefreshPending = false
                    loadSubscriptionFeed(force = true)
                }
            }
        }
    }

    /**
     * Load the selected creator's Videos tab rather than filtering the recent
     * subscriptions snapshot. The latter may contain no recent item from a
     * channel that still has hundreds of uploads, which is not an empty feed.
     */
    fun loadSelectedChannelFeed(channel: com.ivor.ivormusic.data.SubscribedChannel) {
        selectedChannelFeedJob?.cancel()
        selectedChannelFeedJob = viewModelScope.launch {
            _selectedChannelFeed.value = emptyList()
            _selectedChannelFeedError.value = null
            _isSelectedChannelFeedLoading.value = true
            try {
                val videos = youtubeRepository.getChannelVideos(channel)
                _selectedChannelFeed.value = videos
                if (videos.isEmpty()) {
                    _selectedChannelFeedError.value =
                        "Couldn't load uploads from ${channel.name}. Pull to try again."
                }
            } catch (e: Exception) {
                KLog.e("HomeViewModel", "Selected channel feed failed", e)
                _selectedChannelFeedError.value =
                    "Couldn't load uploads from ${channel.name}. Pull to try again."
            } finally {
                _isSelectedChannelFeedLoading.value = false
            }
        }
    }

    fun clearSelectedChannelFeed() {
        selectedChannelFeedJob?.cancel()
        _selectedChannelFeed.value = emptyList()
        _selectedChannelFeedError.value = null
        _isSelectedChannelFeedLoading.value = false
    }

    /**
     * Interleaves the account feed and the device feed on upload time.
     *
     * The account feed carries no exact timestamp (InnerTube only says "3 days
     * ago"), so its items are placed by parsing that prose. It is deliberately
     * coarse, but stacking one list on top of the other would be worse: a
     * month-old account upload would sit above this morning's local one.
     */
    private fun mergeFeeds(accountFeed: List<VideoItem>, localFeed: List<VideoItem>): List<VideoItem> {
        if (localFeed.isEmpty()) return accountFeed
        if (accountFeed.isEmpty()) return localFeed
        val now = System.currentTimeMillis()
        return (accountFeed + localFeed)
            .distinctBy { it.videoId }
            .sortedByDescending {
                it.publishedAtMs ?: VideoItem.parseRelativeTime(it.uploadedDate, now) ?: Long.MIN_VALUE
            }
    }

    /** Local channels the selected group allows through, or all of them. */
    private fun groupFilteredLocalChannels(): List<com.ivor.ivormusic.data.LocalSubscription> =
        localSubscriptionsRepository.channelsInGroup(_selectedGroupId.value)

    private fun shouldUseAccountSubscriptions(): Boolean =
        themePreferences.currentSubscriptionSource() !=
            com.ivor.ivormusic.data.ThemePreferences.SUBSCRIPTIONS_LOCAL &&
            sessionManager.isLoggedIn()

    /** Filter the feed by a group. Passing null clears the filter. */
    fun selectSubscriptionGroup(groupId: String?) {
        if (_selectedGroupId.value == groupId) return
        _selectedGroupId.value = groupId
        loadSubscriptionFeed(force = true)
    }

    /**
     * Fills in names and avatars for imported channels, which arrive with a
     * name at best and never a picture. Capped per run inside the repository,
     * so a large library fills in over a few visits instead of one burst of
     * hundreds of channel browses.
     */
    fun backfillLocalChannelProfiles() {
        val pending = localSubscriptions.value.filter { it.avatarUrl.isNullOrBlank() }
        if (pending.isEmpty()) return
        viewModelScope.launch {
            val updated = youtubeRepository.fetchMissingChannelProfiles(pending)
            localSubscriptionsRepository.updateProfiles(updated)
        }
    }

    /** Drop a locally followed channel. Account subscriptions are untouched. */
    fun unsubscribeLocally(channelId: String) {
        localSubscriptionsRepository.unsubscribe(channelId)
        _subscriptionFeed.value = _subscriptionFeed.value.filterNot { it.channelId == channelId }
    }

    fun isLocallySubscribed(channelId: String?): Boolean =
        localSubscriptionsRepository.isSubscribed(channelId)

    // ---------------- Subscription import / export ----------------

    private val _isImportingSubscriptions = MutableStateFlow(false)
    val isImportingSubscriptions: StateFlow<Boolean> = _isImportingSubscriptions.asStateFlow()

    /** "resolved 40 of 220" while an import runs. */
    private val _importProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val importProgress: StateFlow<Pair<Int, Int>?> = _importProgress.asStateFlow()

    /**
     * Imports a subscription file - NewPipe/PipePipe JSON, a NewPipe-family
     * backup archive, Takeout CSV or OPML, sniffed rather than asked for.
     *
     * Reading happens through the content resolver because the file arrives as
     * a SAF uri, which is not a path and cannot be opened as one. Bytes rather
     * than text, since a backup archive is a zipped database and decoding one
     * as UTF-8 first would corrupt it. The whole run is one merge into the
     * existing list: importing twice, or importing a second device's export,
     * adds what is missing and touches nothing else.
     */
    fun importSubscriptions(
        uri: android.net.Uri,
        onResult: (com.ivor.ivormusic.data.SubscriptionImportResult) -> Unit
    ) {
        if (_isImportingSubscriptions.value) return
        viewModelScope.launch {
            _isImportingSubscriptions.value = true
            _importProgress.value = null
            try {
                val imported = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        null
                    } else {
                        com.ivor.ivormusic.data.SubscriptionTransfer.read(
                            bytes,
                            java.io.File(
                                getApplication<Application>().cacheDir,
                                "subscription-import.db"
                            )
                        )
                    }
                }
                if (imported == null) {
                    onResult(
                        com.ivor.ivormusic.data.SubscriptionImportResult(
                            0, 0, 0, error = "That file was empty or could not be opened."
                        )
                    )
                    return@launch
                }

                val entries = imported.channels
                val foreign = imported.foreignServiceEntries
                if (entries.isEmpty()) {
                    onResult(
                        com.ivor.ivormusic.data.SubscriptionImportResult(
                            0, 0, 0, foreign,
                            error = if (foreign > 0) {
                                "That file only had channels from services Koda can't play."
                            } else {
                                "Couldn't find any channels in that file."
                            }
                        )
                    )
                    return@launch
                }

                val (resolved, unresolved) = youtubeRepository.resolveImportedChannels(entries) { done, total ->
                    _importProgress.value = done to total
                }
                val alreadyPresent = resolved.count { localSubscriptionsRepository.isSubscribed(it.channelId) }
                val added = localSubscriptionsRepository.importAll(resolved)

                // Groups come from Koda's own export, or from the feed groups
                // inside a NewPipe-family backup - never from the JSON export,
                // which has never carried them.
                imported.groups.forEach { group ->
                    val existing = subscriptionGroups.value.firstOrNull { it.name.equals(group.name, true) }
                    if (existing == null) {
                        localSubscriptionsRepository.createGroup(group.name, group.channelIds)
                    } else {
                        localSubscriptionsRepository.setGroupChannels(
                            existing.id,
                            existing.channelIds + group.channelIds
                        )
                    }
                }

                onResult(
                    com.ivor.ivormusic.data.SubscriptionImportResult(
                        added = added,
                        alreadyPresent = alreadyPresent,
                        unresolved = unresolved,
                        skippedOtherService = foreign
                    )
                )
                backfillLocalChannelProfiles()
            } catch (e: Exception) {
                KLog.e("HomeViewModel", "Subscription import failed", e)
                onResult(
                    com.ivor.ivormusic.data.SubscriptionImportResult(
                        0, 0, 0, error = "Couldn't read that file."
                    )
                )
            } finally {
                _importProgress.value = null
                _isImportingSubscriptions.value = false
            }
        }
    }

    /**
     * Copies the signed-in account's subscriptions onto the device, so they
     * survive signing out - the main reason someone would want a local copy
     * while still having an account.
     */
    fun importSubscriptionsFromAccount(
        onResult: (com.ivor.ivormusic.data.SubscriptionImportResult) -> Unit
    ) {
        if (_isImportingSubscriptions.value) return
        if (!sessionManager.isLoggedIn()) {
            onResult(
                com.ivor.ivormusic.data.SubscriptionImportResult(
                    0, 0, 0, error = "Sign in to YouTube first."
                )
            )
            return
        }
        viewModelScope.launch {
            _isImportingSubscriptions.value = true
            try {
                val channels = youtubeRepository.getSubscribedChannels()
                if (channels.isEmpty()) {
                    onResult(
                        com.ivor.ivormusic.data.SubscriptionImportResult(
                            0, 0, 0, error = "Your account has no subscriptions to copy."
                        )
                    )
                    return@launch
                }
                _accountChannels.value = channels
                val asLocal = channels.map {
                    com.ivor.ivormusic.data.LocalSubscription(
                        channelId = it.channelId,
                        name = it.name,
                        avatarUrl = it.avatarUrl
                    )
                }
                val alreadyPresent = asLocal.count { localSubscriptionsRepository.isSubscribed(it.channelId) }
                val added = localSubscriptionsRepository.importAll(asLocal)
                onResult(
                    com.ivor.ivormusic.data.SubscriptionImportResult(
                        added = added,
                        alreadyPresent = alreadyPresent,
                        unresolved = 0
                    )
                )
            } catch (e: Exception) {
                KLog.e("HomeViewModel", "Account subscription copy failed", e)
                onResult(
                    com.ivor.ivormusic.data.SubscriptionImportResult(
                        0, 0, 0, error = "Couldn't reach YouTube."
                    )
                )
            } finally {
                _isImportingSubscriptions.value = false
            }
        }
    }

    /**
     * Writes the local subscriptions to [uri] in the NewPipe-compatible shape,
     * so the file imports cleanly into NewPipe, PipePipe and Tubular as well
     * as back into Koda.
     */
    fun exportSubscriptions(uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val json = com.ivor.ivormusic.data.SubscriptionTransfer.buildExportJson(
                        subscriptions = localSubscriptions.value,
                        groups = subscriptionGroups.value,
                        appVersionName = com.ivor.ivormusic.BuildConfig.VERSION_NAME
                    )
                    getApplication<Application>().contentResolver
                        .openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    true
                } catch (e: Exception) {
                    KLog.e("HomeViewModel", "Subscription export failed", e)
                    false
                }
            }
            onResult(ok)
        }
    }

    // ---------------- Subscription groups ----------------

    fun createSubscriptionGroup(name: String, channelIds: List<String> = emptyList()) {
        if (name.isBlank()) return
        localSubscriptionsRepository.createGroup(name, channelIds)
    }

    fun renameSubscriptionGroup(groupId: String, name: String) {
        if (name.isBlank()) return
        localSubscriptionsRepository.renameGroup(groupId, name)
    }

    fun deleteSubscriptionGroup(groupId: String) {
        localSubscriptionsRepository.deleteGroup(groupId)
        if (_selectedGroupId.value == groupId) selectSubscriptionGroup(null)
    }

    fun toggleChannelInGroup(groupId: String, channelId: String) {
        localSubscriptionsRepository.toggleChannelInGroup(groupId, channelId)
        if (_selectedGroupId.value == groupId) loadSubscriptionFeed(force = true)
    }

    /** Wipes every local subscription and group. Account subscriptions survive. */
    fun clearLocalSubscriptions() {
        localSubscriptionsRepository.clearAll()
        _selectedGroupId.value = null
        loadSubscriptionFeed(force = true)
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

    /**
     * Load one playlist's videos (also Watch Later "WL" / Liked videos "LL").
     *
     * A local playlist is already in memory, so it is served straight from the
     * store rather than through a fetch that would fail signed out. It still
     * goes through the same [_playlistVideos] state, which is what lets
     * `VideoPlaylistDetail` snapshot it into a [com.ivor.ivormusic.data.VideoQueue]
     * without knowing which kind it opened.
     */
    fun loadPlaylistVideos(playlistId: String) {
        if (com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.isLocal(playlistId)) {
            _isPlaylistVideosLoading.value = false
            _playlistVideos.value = localVideoPlaylistsRepository.videosOf(playlistId)
            return
        }
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

    /**
     * Create a playlist from the video Library tab.
     *
     * [onDevice] picks the store. It is forced true signed out, because the
     * YouTube path needs a session and a create that silently does nothing is
     * exactly the failure this whole change exists to remove.
     */
    fun createVideoPlaylist(name: String, onDevice: Boolean = !isYouTubeConnected.value) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            if (onDevice || !isYouTubeConnected.value) {
                localVideoPlaylistsRepository.create(trimmed)
            } else if (youtubeRepository.createYouTubePlaylist(trimmed, music = false) != null) {
                loadVideoPlaylists(force = true)
            }
        }
    }

    /**
     * Delete a playlist. The local store owns its own list, so there is nothing
     * to roll back there; the account path stays optimistic-and-restore.
     */
    fun deleteVideoPlaylist(playlistId: String) {
        if (com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.isLocal(playlistId)) {
            viewModelScope.launch { localVideoPlaylistsRepository.delete(playlistId) }
            return
        }
        val previous = _videoPlaylists.value
        _videoPlaylists.value = previous.filterNot { it.playlistId == playlistId }
        viewModelScope.launch {
            if (!youtubeRepository.deleteYouTubePlaylist(playlistId, music = false)) {
                _videoPlaylists.value = previous
            }
        }
    }

    /** Rename a playlist held on this device. */
    fun renameLocalVideoPlaylist(playlistId: String, name: String) {
        if (!com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.isLocal(playlistId)) return
        viewModelScope.launch { localVideoPlaylistsRepository.rename(playlistId, name) }
    }

    /**
     * Remove a video from a playlist, Watch Later ("WL") or Liked videos
     * ("LL", removes the like). Optimistic removal, restored on failure.
     */
    fun removePlaylistVideo(playlistId: String, video: VideoItem) {
        val previous = _playlistVideos.value
        _playlistVideos.value = previous.filterNot { it.videoId == video.videoId }
        if (com.ivor.ivormusic.data.LocalVideoPlaylistsRepository.isLocal(playlistId)) {
            viewModelScope.launch {
                localVideoPlaylistsRepository.removeVideo(playlistId, video.videoId)
            }
            return
        }
        viewModelScope.launch {
            if (!youtubeRepository.removeFromYouTubePlaylist(playlistId, video.videoId, music = false)) {
                _playlistVideos.value = previous
            }
        }
    }

    /**
     * Add a video to a playlist. Reports the outcome on the main thread so the
     * save sheet can show inline feedback.
     *
     * Three targets, decided here rather than at the five call sites that open
     * the sheet: a local playlist, the account's Watch Later, and any other
     * account playlist. Signed out "WL" is the device's Watch Later, created on
     * first use - the pinned hero row used to post to an endpoint that answers
     * 200 without a session and does nothing, so the sheet reported a save that
     * had not happened.
     */
    fun addVideoToPlaylist(playlistId: String, video: VideoItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(saveVideoToPlaylist(playlistId, video))
        }
    }

    /** Create a playlist on the device, from the save sheet. */
    fun createLocalVideoPlaylist(name: String, onCreated: (String?) -> Unit) {
        viewModelScope.launch { onCreated(localVideoPlaylistsRepository.create(name)) }
    }

    /** Mirrored by `VideoPlayerViewModel.addVideoToPlaylist`; keep them in step. */
    private suspend fun saveVideoToPlaylist(playlistId: String, video: VideoItem): Boolean {
        val local = com.ivor.ivormusic.data.LocalVideoPlaylistsRepository
        return when {
            local.isLocal(playlistId) ->
                localVideoPlaylistsRepository.addVideo(playlistId, video)
            playlistId == "WL" && !isYouTubeConnected.value ->
                localVideoPlaylistsRepository.addVideo(
                    localVideoPlaylistsRepository.ensureWatchLater(),
                    video
                )
            else ->
                youtubeRepository.addToYouTubePlaylist(playlistId, video.videoId, music = false)
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
                // The feed keeps whatever it already had - both branches above
                // only assign a non-empty result - so a failure here is not
                // destructive and does not warrant tearing the screen down.
                // It does have to be visible in a bug report though, which is
                // what this was missing.
                KLog.e("HomeViewModel", "Home recommendations failed to load", e)
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
        // The Subscriptions tab no longer empties itself on sign-out - local
        // subscriptions outlive the session - so the account's half has to be
        // dropped explicitly, or it would sit there unreachable and stale.
        _accountChannels.value = emptyList()
        _subscriptionFeed.value = emptyList()
        loadSubscriptionFeed(force = true)
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
                    _isVideoHomeOffline.value = false
                    _trendingVideos.value = page.videos
                    videoFeedContinuation = page.continuation
                    // Taste-based page 1 seeds from the 6 most recent history
                    // entries; load-more continues from the 7th.
                    tasteSeedOffset = 6
                    videoFeedExhausted = false
                    rememberShown(page.videos)
                } else if (_trendingVideos.value.isEmpty()) {
                    _isVideoHomeOffline.value = !hasNetworkConnection()
                }
            } catch (e: Exception) {
                if (_trendingVideos.value.isEmpty()) {
                    _isVideoHomeOffline.value = !hasNetworkConnection()
                }
            } finally {
                _isVideoLoading.value = false
            }
        }
    }

    // ---------------- "Don't recommend this" ----------------

    /** The most recent hide/block, for the app-wide undo snackbar. */
    val lastNotInterested: StateFlow<com.ivor.ivormusic.data.NotInterestedRepository.UndoableAction?> =
        notInterestedRepository.lastAction

    val hiddenVideos: StateFlow<List<com.ivor.ivormusic.data.NotInterestedRepository.HiddenVideo>> =
        notInterestedRepository.hiddenVideos

    val blockedChannels: StateFlow<List<com.ivor.ivormusic.data.BlockedChannel>> =
        notInterestedRepository.blockedChannels

    /** Hide one video from every recommendation feed. */
    fun markNotInterested(video: VideoItem) {
        notInterestedActions.hideVideo(video, viewModelScope)
        topUpFeedAfterFiltering()
    }

    /** Stop recommending anything from this video's channel. */
    fun blockChannelFor(video: VideoItem) {
        notInterestedActions.blockChannel(video, viewModelScope)
        topUpFeedAfterFiltering()
    }

    fun unhideVideo(videoId: String) = notInterestedRepository.unhideVideo(videoId)

    fun unblockChannel(channelId: String, name: String) =
        notInterestedRepository.unblockChannel(channelId, name)

    fun clearHiddenVideos() = notInterestedRepository.clearHiddenVideos()

    fun clearBlockedChannels() = notInterestedRepository.clearBlockedChannels()

    /**
     * Fetch another page when filtering has left too little on screen.
     *
     * Blocking a prolific channel can take a dozen items out of a twenty-item
     * grid at once. Load-more normally fires on scroll, but there is nothing
     * left to scroll after a cut like that, so the feed would just sit there
     * looking broken until the user pulled to refresh.
     */
    private fun topUpFeedAfterFiltering() {
        val raw = _trendingVideos.value
        if (raw.isEmpty()) return
        // Recomputed here rather than read off [trendingVideos]: the block was
        // written to the repository a moment ago, but the derived flow emits
        // asynchronously, so its current value is still the pre-block list and
        // the check would decide there was plenty left.
        if (notInterestedRepository.filter(raw).size < FEED_TOP_UP_THRESHOLD) {
            loadMoreTrendingVideos()
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
     * A creator's channel identity - banner, avatar, verified tick, subscriber
     * count - for the music artist page.
     *
     * One browse, and the artist page's only reason to make it: it is what lets
     * the same musician look like the same person whichever mode you arrive
     * from. Returns null for anything that is not a channel id, which is the
     * common case in a local library where the "artist" is a tag on a file.
     */
    suspend fun getChannelHeader(
        channelId: String
    ): com.ivor.ivormusic.data.ChannelHeader? {
        if (!channelId.startsWith("UC")) return null
        return try {
            youtubeRepository.getChannelPage(channelId)?.header
        } catch (e: Exception) {
            null
        }
    }

    /** Search for channels (video mode's Channels filter). */
    suspend fun searchChannels(query: String): List<com.ivor.ivormusic.data.SubscribedChannel> {
        if (query.isBlank()) return emptyList()
        return try {
            youtubeRepository.searchChannels(query)
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
    
    /** Accent colors for a generated cover - see [playlistCoverSeeds]. */
    private fun coverSeedColors(): Pair<Int, Int>? = playlistCoverSeeds(getApplication())

    fun createLocalPlaylist(name: String, description: String?) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description, coverSeedColors())
        }
    }

    /** Replace a local playlist's artwork with an image the user picked. */
    fun setLocalPlaylistCover(playlistId: String, source: android.net.Uri) {
        viewModelScope.launch {
            playlistRepository.setCustomCover(playlistId, source)
        }
    }

    /** Drop a chosen cover and go back to the generated one. */
    fun resetLocalPlaylistCover(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.resetCoverToGenerated(playlistId, coverSeedColors())
        }
    }


    fun addSongToLocalPlaylist(playlistId: String, song: Song) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, song)
        }
    }

    fun updateLocalPlaylist(playlistId: String, name: String, description: String?) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlistId, name, description, coverSeedColors())
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

    // ---------------- Listening history ----------------

    // The raw play log, newest first - what the listening history screen shows.
    // Distinct from recentlyPlayed, which is the same source deduplicated down
    // to one card per song for the Library rail.
    private val _playHistory =
        MutableStateFlow<List<com.ivor.ivormusic.data.PlayHistoryEntry>>(emptyList())
    val playHistory: StateFlow<List<com.ivor.ivormusic.data.PlayHistoryEntry>> =
        _playHistory.asStateFlow()

    private val _isPlayHistoryLoading = MutableStateFlow(false)
    val isPlayHistoryLoading: StateFlow<Boolean> = _isPlayHistoryLoading.asStateFlow()

    fun loadPlayHistory() {
        viewModelScope.launch {
            _isPlayHistoryLoading.value = true
            _playHistory.value = statsRepository.loadHistory()
            _isPlayHistoryLoading.value = false
        }
    }

    /**
     * Remove one play, or every play of a song when [allPlaysOfSong] is set.
     *
     * [onRemoved] receives the list as it was beforehand, which is what Undo
     * needs: removing a song's whole run takes an unknown number of entries
     * with it, and putting them back in order is not something a single entry
     * can describe.
     */
    fun removePlayHistoryEntry(
        songId: String,
        timestamp: Long,
        allPlaysOfSong: Boolean = false,
        onRemoved: (List<com.ivor.ivormusic.data.PlayHistoryEntry>) -> Unit = {}
    ) {
        viewModelScope.launch {
            val before = _playHistory.value
            _playHistory.value = if (allPlaysOfSong) {
                statsRepository.removeAllPlaysOf(songId)
            } else {
                statsRepository.removeEntry(songId, timestamp)
            }
            onRemoved(before)
            // The rail, the "Most played" sort and the stats screen all read the
            // same file. Leaving them stale is how a song deleted from history
            // stays visible one screen over.
            refreshRecentlyPlayed()
            refreshStats()
        }
    }

    fun restorePlayHistory(entries: List<com.ivor.ivormusic.data.PlayHistoryEntry>) {
        viewModelScope.launch {
            statsRepository.restoreHistory(entries)
            _playHistory.value = entries
            refreshRecentlyPlayed()
            refreshStats()
        }
    }

    fun clearPlayHistory() {
        viewModelScope.launch {
            statsRepository.clearHistory()
            _playHistory.value = emptyList()
            refreshRecentlyPlayed()
            refreshStats()
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

        /**
         * How few visible items it takes for a "not interested" to trigger a
         * top-up page. Roughly one screen of the grid - below that there is
         * nothing left to scroll, so the usual scroll-triggered load-more
         * would never fire.
         */
        const val FEED_TOP_UP_THRESHOLD = 8

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
