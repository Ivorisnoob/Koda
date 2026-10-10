package com.ivor.ivormusic.data

import android.content.Context
import android.net.Uri
import com.ivor.ivormusic.data.stream.AudioStreamResolver
import com.ivor.ivormusic.data.stream.BotCheckVerdict
import com.ivor.ivormusic.data.stream.NewPipeAudioSource
import com.ivor.ivormusic.data.stream.PlayerApi
import com.ivor.ivormusic.data.stream.PlayerClient
import com.ivor.ivormusic.data.stream.PlayerSession
import com.ivor.ivormusic.data.stream.SongStreams
import com.ivor.ivormusic.data.stream.StreamProbe
import com.ivor.ivormusic.data.stream.VideoStreamResolver
import com.ivor.ivormusic.data.stream.VisitorIdentity
import com.ivor.ivormusic.data.stream.userAgentForStreamClient
import com.ivor.ivormusic.data.youtube.BROWSER_USER_AGENT
import com.ivor.ivormusic.data.youtube.CaptionTracks
import com.ivor.ivormusic.data.youtube.ChannelPages
import com.ivor.ivormusic.data.youtube.ChannelProfile
import com.ivor.ivormusic.data.youtube.CommentThreads
import com.ivor.ivormusic.data.youtube.INNER_TUBE_API_KEY
import com.ivor.ivormusic.data.youtube.LiveChat
import com.ivor.ivormusic.data.youtube.LocalSubscriptionFeed
import com.ivor.ivormusic.data.youtube.MusicApi
import com.ivor.ivormusic.data.youtube.MusicBrowse
import com.ivor.ivormusic.data.youtube.MusicPlaylists
import com.ivor.ivormusic.data.youtube.MusicSearch
import com.ivor.ivormusic.data.youtube.NewPipeGateway
import com.ivor.ivormusic.data.youtube.PlaybackHistory
import com.ivor.ivormusic.data.youtube.PlayerResponseHarvest
import com.ivor.ivormusic.data.youtube.PlaylistEditing
import com.ivor.ivormusic.data.youtube.PlaylistUpload
import com.ivor.ivormusic.data.youtube.VideoFeeds
import com.ivor.ivormusic.data.youtube.VideoPlaylists
import com.ivor.ivormusic.data.youtube.VideoSearch
import com.ivor.ivormusic.data.youtube.WatchPage
import com.ivor.ivormusic.data.youtube.WebApi
import com.ivor.ivormusic.data.youtube.YouTubeAccount
import com.ivor.ivormusic.data.youtube.YouTubeHttp
import com.ivor.ivormusic.data.youtube.parseCaptionTracks
import com.ivor.ivormusic.data.youtube.youtubeService

/**
 * Everything Koda asks of YouTube and YouTube Music, behind one class.
 *
 * This file decides nothing. It builds the pieces in `data/youtube` and
 * `data/stream` and hands each call to the one that owns it, so the
 * ViewModels and services that construct a repository need not know how it is
 * divided. A new call goes in the piece for its area and gets a one-line
 * function here; the reasoning and the verified response shapes are
 * documented on the piece.
 *
 * There is no DI framework, so every ViewModel and service builds its own
 * repository. What has to be the same for all of them (the visitor identity,
 * the bot-check verdict, the rate-limit hold, the stream and caption caches)
 * is process-wide inside the pieces. Everything built here is per instance.
 */
class YouTubeRepository(private val context: Context) {

    // --- Transport ---
    private val sessionManager = SessionManager(context)
    private val http = YouTubeHttp(context, sessionManager)
    private val webApi = WebApi(http, sessionManager)
    private val musicApi = MusicApi(http, sessionManager)
    private val newPipeGateway = NewPipeGateway(http, sessionManager)

    // --- YouTube Music ---
    private val musicSearch = MusicSearch(musicApi, newPipeGateway)
    private val musicPlaylists = MusicPlaylists(musicApi, sessionManager)
    private val musicBrowse = MusicBrowse(http, musicApi, musicSearch, musicPlaylists, sessionManager)

    // --- YouTube ---
    private val videoSearch = VideoSearch(context, webApi, newPipeGateway)
    private val watchPage = WatchPage(webApi, sessionManager)
    private val videoFeeds = VideoFeeds(context, http, webApi, videoSearch, watchPage, sessionManager)
    private val videoPlaylists = VideoPlaylists(context, webApi, sessionManager)
    private val liveChat = LiveChat(webApi, watchPage, sessionManager)
    private val commentThreads = CommentThreads(webApi, sessionManager)
    private val channelPages = ChannelPages(webApi, sessionManager)
    private val localFeed = LocalSubscriptionFeed(http, channelPages)

    // --- Both ---
    private val youtubeAccount = YouTubeAccount(musicApi, webApi, sessionManager)
    private val playlistEditing = PlaylistEditing(webApi, musicApi, sessionManager)
    private val playbackHistory = PlaybackHistory(context, http, webApi, sessionManager)

    // --- Streams and captions ---
    private val playerSession = PlayerSession(
        http.visitorIdentity,
        PlayerApi(http.streamResolveClient, INNER_TUBE_API_KEY),
    )
    private val captionTracks = CaptionTracks(playerSession, http)
    private val playerHarvest = PlayerResponseHarvest(context, captionTracks)
    private val audioStreams = AudioStreamResolver(
        identity = http.visitorIdentity,
        session = playerSession,
        probe = StreamProbe(http.streamResolveClient, BROWSER_USER_AGENT),
        newPipe = NewPipeAudioSource(youtubeService, newPipeGateway.newPipeScope),
        newPipeBudgetMs = NEWPIPE_STREAM_BUDGET_MS,
        onResponse = playerHarvest::harvestPlayerResponse,
        onLoudness = playerHarvest::cacheTrackLoudness,
    )
    private val songStreams = SongStreams(context, audioStreams, playerSession)
    private val videoStreams = VideoStreamResolver(
        playerSession = playerSession,
        youtubeService = youtubeService,
        onCaptions = { videoId, root -> captionTracks.cacheCaptionTracks(videoId, parseCaptionTracks(root)) },
        onResponse = playerHarvest::harvestPlayerResponse,
    )

    companion object {
        /** The filter [search] takes for songs, which is also its default. */
        const val FILTER_SONGS = com.ivor.ivormusic.data.youtube.FILTER_SONGS

        // How long stream resolution waits on NewPipe before falling back, which
        // is what playback actually feels. Deliberately small enough to leave
        // the InnerTube chain room inside MusicService's own resolution
        // timeout: a NewPipe path that merely takes too long must degrade to
        // the fallback, not spend the whole budget and then resolve to nothing.
        //
        // Sized against MusicService.RESOLVE_TIMEOUT_MS (20s), which discards -
        // and therefore skips - anything slower: 8s here leaves the 8s-capped
        // direct /player chain room to succeed inside it. The two are a pair;
        // moving one alone reopens the skip. The per-request cap on NewPipe's
        // own client is a separate backstop in YouTubeHttp.
        private const val NEWPIPE_STREAM_BUDGET_MS = 8_000L

        /**
         * The User-Agent a player must send when fetching a googlevideo URL:
         * the one belonging to the client that resolved it, read from the
         * URL's `c=`. See [PlayerClient].
         */
        fun uaForPlaybackUri(uri: Uri): String {
            val client = try { uri.getQueryParameter("c") } catch (_: Exception) { null }
            return userAgentForStreamClient(client, BROWSER_USER_AGENT)
        }

        /**
         * Whether stream resolution was refused by the bot check recently
         * enough that repeating it automatically would only add requests.
         * Never a reason to refuse a request the user made.
         */
        fun isBotCheckVerdictActive(): Boolean = BotCheckVerdict.isActive()

        /**
         * The device moved to a different network, so the verdicts YouTube
         * passed on the old address no longer describe this one: drop the
         * bot-check verdict, the 429 hold and every ladder resolved under the
         * old address. The caller remints visitorData on the new one.
         */
        fun forgetConnectionVerdicts() {
            BotCheckVerdict.reset()
            YouTubeRateLimit.clear()
            VideoStreamResolutionCache.clear()
        }

        /**
         * Drop the process-wide caches that belong to one profile, so a switch
         * cannot serve the previous account's identity.
         *
         * **visitorData is the one that actually matters.** It is this app's
         * anti-bot identity, persisted device-wide with a 6h TTL, and
         * prefetched independently by MusicService and the video ViewModel.
         * Replaying an account's token under a different account is precisely
         * the "stale or shared value gets flagged" case [VisitorIdentity]
         * documents, so it is cleared from memory and disk and left to be
         * minted again on the next call.
         *
         * The caption cache is deliberately left alone: it is keyed by video id
         * and a video's subtitles are the same whoever is watching.
         *
         * [commitNow] forces the erase to disk before returning. Only a
         * restore needs it: it kills the process on purpose, and a queued
         * apply() dying with it would leave the previous identity's
         * visitorData persisted and re-read on the next start.
         */
        fun invalidateSessionScopedCaches(context: Context, commitNow: Boolean = false) {
            VisitorIdentity.forget(context, commitNow)
            BotCheckVerdict.reset()
            VideoStreamResolutionCache.clear()
        }
    }

    // --- Session and identity ---

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    /**
     * Forget everything cached in this instance that belonged to the previous
     * profile. The process-wide half is [invalidateSessionScopedCaches].
     */
    fun clearSessionScopedInstanceCaches() {
        musicSearch.clearCaches()
        videoSearch.clearCaches()
    }

    /** Warm the identity off the critical path; see [VisitorIdentity.prefetch]. */
    suspend fun prefetchVisitorData() = http.visitorIdentity.prefetch()

    /**
     * Replace the identity because *playback* failed, not resolution.
     *
     * Resolution only replaces a token when `/player` itself shows the bot
     * check. The other signature is a `/player` that answers OK with URLs
     * googlevideo then refuses with HTTP 403: the player sees that,
     * resolution never does, so without this entry point the refused token
     * sits in prefs and is replayed for its whole TTL - "restarting and
     * clearing cache don't help, clearing data does".
     *
     * Safe to call speculatively; without a token there is nothing to replace.
     */
    suspend fun refreshVisitorDataAfterPlaybackFailure() {
        // Every cached ladder holds URLs signed before the failure. They must
        // not win the retry, even when there is no token to replace.
        VideoStreamResolutionCache.clear()
        http.visitorIdentity.replaceCurrent()
    }

    // --- The account (YouTubeAccount) ---

    suspend fun fetchAccountInfo() {
        youtubeAccount.fetchAccountInfo()
    }

    suspend fun getNotifications(): List<NotificationItem> = youtubeAccount.getNotifications()

    // --- Music search (MusicSearch) ---

    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song> = musicSearch.search(query, filter)

    suspend fun searchNext(query: String): List<Song> = musicSearch.searchNext(query)

    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = musicSearch.searchAlbums(query)

    suspend fun searchArtists(query: String): List<ArtistItem> = musicSearch.searchArtists(query)

    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = musicSearch.searchPlaylists(query)

    // --- Music home, shelves, artists and radio (MusicBrowse) ---

    suspend fun getRecommendations(): List<Song> = musicBrowse.getRecommendations()

    suspend fun getMusicShelves(browseId: String, params: String? = null): MusicShelfPage? =
        musicBrowse.getMusicShelves(browseId, params)

    suspend fun getMusicShelvesContinuation(token: String): MusicShelfPage? =
        musicBrowse.getMusicShelvesContinuation(token)

    suspend fun getChartArtists(country: String? = null): List<ArtistItem> = musicBrowse.getChartArtists(country)

    suspend fun getArtistPage(artistId: String): ArtistPage? = musicBrowse.getArtistPage(artistId)

    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> =
        musicBrowse.getArtistDetails(artistId)

    suspend fun getArtistTasteSample(artistId: String): ArtistTasteSample? = musicBrowse.getArtistTasteSample(artistId)

    suspend fun getSongAlbumRef(videoId: String): SongAlbumRef? = musicBrowse.getSongAlbumRef(videoId)

    suspend fun getRelatedSongs(videoId: String, limit: Int = 25): List<Song> =
        musicBrowse.getRelatedSongs(videoId, limit)

    suspend fun getSongFromPanel(videoId: String): Song? = musicBrowse.getSongFromPanel(videoId)

    // --- Music playlists, albums and the library (MusicPlaylists) ---

    suspend fun getPlaylist(playlistId: String): List<Song> = musicPlaylists.getPlaylist(playlistId)

    suspend fun getAlbumSongs(browseId: String): List<Song> = musicPlaylists.getAlbumSongs(browseId)

    suspend fun getLikedMusic(): List<Song> = musicPlaylists.getLikedMusic()

    suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = musicPlaylists.getUserPlaylists()

    // --- Song streams (SongStreams) ---

    suspend fun getStreamUrl(videoId: String): Result<String> = songStreams.getStreamUrl(videoId)

    suspend fun getDownloadAudioStreamUrl(videoId: String, quality: String? = null): Result<String> =
        songStreams.getDownloadAudioStreamUrl(videoId, quality)

    suspend fun getDownloadAudioFormats(videoId: String): List<DownloadAudioFormat> =
        songStreams.getDownloadAudioFormats(videoId)

    // --- Video search (VideoSearch) ---

    suspend fun searchVideos(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY,
        sort: VideoSearchSort = VideoSearchSort.RELEVANCE
    ): List<VideoItem> =
        videoSearch.searchVideos(query, dateFilter, sort)

    suspend fun searchVideosNext(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY,
        sort: VideoSearchSort = VideoSearchSort.RELEVANCE
    ): List<VideoItem> =
        videoSearch.searchVideosNext(query, dateFilter, sort)

    suspend fun searchVideoPlaylists(query: String): List<VideoPlaylist> = videoSearch.searchVideoPlaylists(query)

    suspend fun searchChannels(query: String): List<SubscribedChannel> = videoSearch.searchChannels(query)

    // --- Home, Subscriptions, history and Shorts (VideoFeeds) ---

    suspend fun getTrendingVideos(): VideoFeedPage = videoFeeds.getTrendingVideos()

    suspend fun getTasteBasedVideos(seedOffset: Int = 0): List<VideoItem> = videoFeeds.getTasteBasedVideos(seedOffset)

    suspend fun getVideoFeedContinuation(continuation: String): VideoFeedPage =
        videoFeeds.getVideoFeedContinuation(continuation)

    suspend fun getSubscriptionsFeedPage(): VideoFeedPage = videoFeeds.getSubscriptionsFeedPage()

    internal suspend fun getWatchHistoryPage(
        continuation: String? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoFeedPage? =
        videoFeeds.getWatchHistoryPage(continuation, session)

    suspend fun getShortsFeed(): List<ShortsItem> = videoFeeds.getShortsFeed()

    suspend fun getShortsSequence(sequenceParams: String): ShortsFeedPage = videoFeeds.getShortsSequence(sequenceParams)

    suspend fun sendDismissalFeedback(token: String?): Boolean = videoFeeds.sendDismissalFeedback(token)

    // --- Video playlists (VideoPlaylists) ---

    suspend fun getVideoPlaylists(): List<VideoPlaylist> = videoPlaylists.getVideoPlaylists()

    suspend fun getPlaylistHeader(playlistId: String): PlaylistPageInfo? = videoPlaylists.getPlaylistHeader(playlistId)

    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> = videoPlaylists.getPlaylistVideos(playlistId)

    internal suspend fun getPlaylistVideosPage(
        playlistId: String,
        continuation: VideoPlaylistCursor? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoPlaylistPage? =
        videoPlaylists.getPlaylistVideosPage(playlistId, continuation, session)

    suspend fun getCompletePlaylistVideos(playlistId: String): List<VideoItem>? =
        videoPlaylists.getCompletePlaylistVideos(playlistId)

    // --- Playlist writes on both hosts (PlaylistEditing) ---

    suspend fun createYouTubePlaylist(title: String, music: Boolean, videoIds: List<String> = emptyList()): String? =
        playlistEditing.createYouTubePlaylist(title, music, videoIds)

    suspend fun uploadPlaylist(title: String, description: String?, videoIds: List<String>): PlaylistUpload? =
        playlistEditing.uploadPlaylist(title, description, videoIds)

    suspend fun renameYouTubePlaylist(
        playlistId: String,
        title: String,
        music: Boolean,
        description: String? = null
    ): Boolean =
        playlistEditing.renameYouTubePlaylist(playlistId, title, music, description)

    suspend fun deleteYouTubePlaylist(playlistId: String, music: Boolean): Boolean =
        playlistEditing.deleteYouTubePlaylist(playlistId, music)

    suspend fun setPlaylistInLibrary(playlistId: String, saved: Boolean): Boolean =
        playlistEditing.setPlaylistInLibrary(playlistId, saved)

    suspend fun addToYouTubePlaylist(playlistId: String, videoId: String, music: Boolean): Boolean =
        playlistEditing.addToYouTubePlaylist(playlistId, videoId, music)

    suspend fun removeFromYouTubePlaylist(playlistId: String, videoId: String, music: Boolean): Boolean =
        playlistEditing.removeFromYouTubePlaylist(playlistId, videoId, music)

    suspend fun moveInYouTubePlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
        music: Boolean
    ): Boolean =
        playlistEditing.moveInYouTubePlaylist(playlistId, setVideoId, successorSetVideoId, music)

    suspend fun getPlaylistsContaining(videoId: String): Set<String>? = playlistEditing.getPlaylistsContaining(videoId)

    suspend fun getPlaylistSetVideoIds(playlistId: String): Map<String, List<String>> =
        playlistEditing.getPlaylistSetVideoIds(playlistId)

    // --- Video streams (VideoStreamResolver) ---

    suspend fun getVideoStreamQualities(videoId: String, includeHdr: Boolean = false): List<VideoQuality> =
        videoStreams.getVideoStreamQualities(videoId, includeHdr)

    suspend fun getVideoStreamResult(videoId: String, includeHdr: Boolean = false): VideoStreamResult =
        videoStreams.getVideoStreamResult(videoId, includeHdr)

    fun invalidateVideoStreamResult(videoId: String) {
        videoStreams.invalidateVideoStreamResult(videoId)
    }

    // --- Captions (CaptionTracks) ---

    suspend fun getCaptionTracks(videoId: String): List<CaptionTrack> = captionTracks.getCaptionTracks(videoId)

    suspend fun getCaptionCues(track: CaptionTrack): List<VttCue> = captionTracks.getCaptionCues(track)

    suspend fun getCaptionVtt(track: CaptionTrack): String? = captionTracks.getCaptionVtt(track)

    // --- The watch page: /next, like, subscribe, bell (WatchPage) ---

    suspend fun getWatchNextData(videoId: String, baseVideo: VideoItem? = null): WatchNextData =
        watchPage.getWatchNextData(videoId, baseVideo)

    suspend fun getVideoEngagement(videoId: String): VideoEngagement? = watchPage.getVideoEngagement(videoId)

    suspend fun getVideoChannelId(videoId: String): String? = watchPage.getVideoChannelId(videoId)

    suspend fun rateVideo(videoId: String, status: LikeStatus): Boolean = watchPage.rateVideo(videoId, status)

    suspend fun setSubscribed(channelId: String, subscribe: Boolean): Boolean =
        watchPage.setSubscribed(channelId, subscribe)

    suspend fun setChannelBell(bell: ChannelBell, level: BellLevel): ChannelBellChange? =
        watchPage.setChannelBell(bell, level)

    // --- Live chat (LiveChat) ---

    suspend fun getLiveChatSession(videoId: String): LiveChatSession? = liveChat.getLiveChatSession(videoId)

    suspend fun pollLiveChat(continuation: String): LiveChatPage? = liveChat.pollLiveChat(continuation)

    suspend fun sendLiveChatMessage(params: String, text: String): LiveChatSendResult =
        liveChat.sendLiveChatMessage(params, text)

    suspend fun getLiveMetadata(videoId: String): LiveMetadata? = liveChat.getLiveMetadata(videoId)

    // --- Comments on videos and posts (CommentThreads) ---

    suspend fun getCommentsPage(token: String, viaBrowse: Boolean = false): CommentsPage? =
        commentThreads.getCommentsPage(token, viaBrowse)

    suspend fun createComment(createCommentParams: String, text: String): CommentItem? =
        commentThreads.createComment(createCommentParams, text)

    suspend fun createCommentReply(createReplyParams: String, text: String): CommentItem? =
        commentThreads.createCommentReply(createReplyParams, text)

    suspend fun performCommentAction(action: String): Boolean = commentThreads.performCommentAction(action)

    suspend fun getPostDetail(detailParams: String): ChannelPost? = commentThreads.getPostDetail(detailParams)

    suspend fun getPostCommentsToken(detailParams: String): String? = commentThreads.getPostCommentsToken(detailParams)

    // --- Channels (ChannelPages) ---

    suspend fun getChannelPage(channelId: String): ChannelPage? = channelPages.getChannelPage(channelId)

    suspend fun getChannelTab(channelId: String, params: String, header: ChannelHeader? = null): ChannelTabPage =
        channelPages.getChannelTab(channelId, params, header)

    suspend fun getChannelContinuation(token: String, header: ChannelHeader? = null): ChannelTabPage =
        channelPages.getChannelContinuation(token, header)

    suspend fun searchWithinChannel(
        channelId: String,
        params: String,
        query: String,
        header: ChannelHeader? = null
    ): ChannelTabPage =
        channelPages.searchWithinChannel(channelId, params, query, header)

    suspend fun getChannelAbout(token: String): ChannelAbout? = channelPages.getChannelAbout(token)

    suspend fun getChannelPosts(channelId: String): List<ChannelPost> = channelPages.getChannelPosts(channelId)

    suspend fun getChannelVideos(channel: SubscribedChannel): List<VideoItem> = channelPages.getChannelVideos(channel)

    suspend fun getChannelMixPool(channel: SubscribedChannel): ChannelMixPool = channelPages.getChannelMixPool(channel)

    suspend fun getSubscribedChannels(): List<SubscribedChannel> = channelPages.getSubscribedChannels()

    suspend fun resolveChannelId(urlOrHandle: String): String? = channelPages.resolveChannelId(urlOrHandle)

    suspend fun getChannelProfile(channelId: String): ChannelProfile? = channelPages.getChannelProfile(channelId)

    // --- Local subscriptions (LocalSubscriptionFeed) ---

    suspend fun getLocalSubscriptionsFeed(
        channels: List<LocalSubscription>,
        fastMode: Boolean = true,
        maxPerChannel: Int = LocalSubscriptionFeed.MAX_FEED_ITEMS_PER_CHANNEL,
        maxTotal: Int = LocalSubscriptionFeed.MAX_FEED_ITEMS,
        /** True when the user asked for this refresh, so it must revalidate. */
        forceFresh: Boolean = false,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): List<VideoItem> =
        localFeed.getLocalSubscriptionsFeed(channels, fastMode, maxPerChannel, maxTotal, forceFresh, onProgress)

    suspend fun getChannelFeedRss(channelId: String, avatarUrl: String? = null): List<VideoItem> =
        localFeed.getChannelFeedRss(channelId, avatarUrl)

    suspend fun resolveImportedChannels(
        entries: List<ImportedChannel>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Pair<List<LocalSubscription>, Int> =
        localFeed.resolveImportedChannels(entries, onProgress)

    suspend fun fetchMissingChannelProfiles(
        channels: List<LocalSubscription>,
        limit: Int = LocalSubscriptionFeed.PROFILE_BACKFILL_LIMIT
    ): List<LocalSubscription> =
        localFeed.fetchMissingChannelProfiles(channels, limit)

    // --- History reports to the account (PlaybackHistory) ---

    suspend fun reportPlayback(videoId: String) {
        playbackHistory.reportPlayback(videoId)
    }

    internal suspend fun beginVideoHistorySession(videoId: String, positionMs: Long): VideoHistorySession? =
        playbackHistory.beginVideoHistorySession(videoId, positionMs)

    internal suspend fun reportVideoWatchProgress(
        session: VideoHistorySession,
        startMs: Long,
        positionMs: Long,
        final: Boolean,
    ): HistoryPingResult =
        playbackHistory.reportVideoWatchProgress(session, startMs, positionMs, final)
}
