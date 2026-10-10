package com.ivor.ivormusic.data

import com.ivor.ivormusic.data.stream.AudioPreference
import com.ivor.ivormusic.data.stream.AudioResolution
import com.ivor.ivormusic.data.stream.AudioStreamResolver
import com.ivor.ivormusic.data.stream.BotCheckVerdict
import com.ivor.ivormusic.data.stream.NewPipeAudioSource
import com.ivor.ivormusic.data.stream.PlayerApi
import com.ivor.ivormusic.data.stream.PlayerClient
import com.ivor.ivormusic.data.stream.PlayerClients
import com.ivor.ivormusic.data.stream.PlayerSession
import com.ivor.ivormusic.data.stream.StreamProbe
import com.ivor.ivormusic.data.stream.VisitorIdentity
import com.ivor.ivormusic.data.stream.isNewPipeBotCheck
import com.ivor.ivormusic.data.stream.m4aAudioFormats
import com.ivor.ivormusic.data.stream.okRoot
import com.ivor.ivormusic.data.stream.originalAudioStreams
import com.ivor.ivormusic.data.stream.userAgentForStreamClient
import com.ivor.ivormusic.data.youtube.*
import com.ivor.ivormusic.util.KLog

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.Page
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


import java.util.concurrent.TimeUnit

/**
 * Repository for fetching data from YouTube Music.
 * Uses NewPipeExtractor to avoid official API restrictions.
 */
class YouTubeRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val http = YouTubeHttp(context, sessionManager)
    private val webApi = WebApi(http, sessionManager)
    private val musicApi = MusicApi(http, sessionManager)
    private val newPipeGateway = NewPipeGateway(http, sessionManager)
    private val musicSearch = MusicSearch(musicApi, newPipeGateway)
    private val musicPlaylists = MusicPlaylists(musicApi, sessionManager)
    private val musicBrowse = MusicBrowse(http, musicApi, musicSearch, musicPlaylists, sessionManager)
    private val youtubeAccount = YouTubeAccount(musicApi, webApi, sessionManager)
    private val videoSearch = VideoSearch(context, webApi, newPipeGateway)
    private val watchPage = WatchPage(webApi, sessionManager)
    private val videoFeeds = VideoFeeds(context, http, webApi, videoSearch, watchPage, sessionManager)
    private val videoPlaylists = VideoPlaylists(context, webApi, sessionManager)
    private val playlistEditing = PlaylistEditing(webApi, musicApi, sessionManager)
    private val liveChat = LiveChat(webApi, watchPage, sessionManager)
    private val commentThreads = CommentThreads(webApi, sessionManager)
    private val channelPages = ChannelPages(webApi, sessionManager)
    private val localFeed = LocalSubscriptionFeed(http, channelPages)
    private val playbackHistory = PlaybackHistory(context, http, webApi, sessionManager)

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
        fun uaForPlaybackUri(uri: android.net.Uri): String {
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

        private class CachedCaptions(val tracks: List<CaptionTrack>, val fetchedAt: Long)

        // Caption tracklists harvested from the /player response already made to
        // start playback, so tapping CC costs no extra request. Companion-level
        // for the same reason visitorData is: the player VM and the repository
        // that resolved the stream can be different instances. Timedtext URLs
        // are signed with a ~6h expiry, so entries are dropped well before that.
        private const val CAPTION_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private const val CAPTION_CACHE_MAX_ENTRIES = 16
        private val captionCache = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedCaptions>(CAPTION_CACHE_MAX_ENTRIES, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, CachedCaptions>,
                ): Boolean = size > CAPTION_CACHE_MAX_ENTRIES
            }
        )

        /**
         * Drop the process-wide caches that belong to one profile, so a switch
         * cannot serve the previous account's identity.
         *
         * **visitorData is the one that actually matters.** It is this app's
         * anti-bot identity, cached here and persisted device-wide with a 6h
         * TTL, and prefetched independently by MusicService and the video
         * ViewModel. Replaying an account's token under a different account is
         * precisely the "stale or shared value gets flagged" case documented
         * above, so it is cleared from memory and disk and left to be re-minted
         * lazily on the next call.
         *
         * The caption cache is deliberately left alone: it is keyed by video id
         * and a video's subtitles are the same whoever is watching.
         */
        /**
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

    private val playerSession = PlayerSession(
        http.visitorIdentity,
        PlayerApi(http.streamResolveClient, INNER_TUBE_API_KEY),
    )
    private val audioStreams = AudioStreamResolver(
        identity = http.visitorIdentity,
        session = playerSession,
        probe = StreamProbe(http.streamResolveClient, BROWSER_USER_AGENT),
        newPipe = NewPipeAudioSource(youtubeService, newPipeGateway.newPipeScope),
        newPipeBudgetMs = NEWPIPE_STREAM_BUDGET_MS,
        onResponse = ::harvestPlayerResponse,
        onLoudness = ::cacheTrackLoudness,
    )

    /**
     * Forget everything cached in this instance that belonged to the previous
     * profile. The process-wide half is [invalidateSessionScopedCaches].
     */
    fun clearSessionScopedInstanceCaches() {
        musicSearch.clearCaches()
        videoSearch.clearCaches()
    }

    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song> = musicSearch.search(query, filter)

    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = musicSearch.searchPlaylists(query)

    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = musicSearch.searchAlbums(query)

    suspend fun searchArtists(query: String): List<ArtistItem> = musicSearch.searchArtists(query)

    suspend fun getArtistPage(artistId: String): ArtistPage? = musicBrowse.getArtistPage(artistId)

    suspend fun getArtistTasteSample(artistId: String): ArtistTasteSample? = musicBrowse.getArtistTasteSample(artistId)

    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> =
        musicBrowse.getArtistDetails(artistId)

    suspend fun getSongAlbumRef(videoId: String): SongAlbumRef? = musicBrowse.getSongAlbumRef(videoId)

    suspend fun getAlbumSongs(browseId: String): List<Song> = musicPlaylists.getAlbumSongs(browseId)

    suspend fun getMusicShelves(browseId: String, params: String? = null): MusicShelfPage? =
        musicBrowse.getMusicShelves(browseId, params)

    suspend fun getChartArtists(country: String? = null): List<ArtistItem> = musicBrowse.getChartArtists(country)

    suspend fun getMusicShelvesContinuation(token: String): MusicShelfPage? =
        musicBrowse.getMusicShelvesContinuation(token)

    suspend fun searchNext(query: String): List<Song> = musicSearch.searchNext(query)

    /**
     * Get the best audio stream URL for a video.
     * Note: These URLs expire, so call this right before playback.
     * @param videoId The YouTube video ID
     * @return Result containing stream URL or error
     */
    suspend fun getStreamUrl(videoId: String): Result<String> =
        when (val resolution = audioStreams.forPlayback(videoId, currentAudioPreference())) {
            is AudioResolution.Resolved -> Result.success(resolution.url)
            AudioResolution.Unresolved ->
                Result.failure(Exception("No audio stream found for $videoId"))
        }

    /** The per-network music quality setting, read fresh for each resolution. */
    private fun currentAudioPreference(): AudioPreference =
        when (ThemePreferences.currentMusicQuality(context)) {
            ThemePreferences.MUSIC_QUALITY_LOW -> AudioPreference.LOWEST
            ThemePreferences.MUSIC_QUALITY_NORMAL -> AudioPreference.BALANCED
            else -> AudioPreference.HIGHEST
        }

    /**
     * Resolve an AAC/M4A audio-only stream for a file download.
     *
     * Playback may consume Opus/WebM or a muxed video fallback because Media3
     * only needs a playable track. Downloads are published as `.m4a` and then
     * tagged, so accepting either fallback would put bytes from the wrong
     * container behind an M4A filename and make metadata writing unreliable.
     */
    suspend fun getDownloadAudioStreamUrl(
        videoId: String,
        quality: String? = null,
    ): Result<String> {
        val wanted = quality ?: ThemePreferences.currentDownloadMusicQuality(context)
        val smallest = wanted == ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER
        return when (val resolution = audioStreams.forDownload(videoId, smallest)) {
            is AudioResolution.Resolved -> Result.success(resolution.url)
            AudioResolution.Unresolved ->
                Result.failure(Exception("No AAC/M4A audio stream found for $videoId"))
        }
    }

    /**
     * The qualities a song can be downloaded at, best first, each with the
     * size YouTube states for it.
     *
     * [verified October 2026, visionOS `/player`, signed out] A music id
     * answers with two AAC/M4A audio streams: itag 140 (AAC-LC, about 128
     * kbps, 44.1 kHz) and itag 139 (HE-AAC, about 48 kbps), both with a
     * plain `url` and a `contentLength`. The Opus streams beside them
     * (249/250/251) are WebM and are left out for the reason
     * [getDownloadAudioStreamUrl] gives.
     *
     * One request and no size probe: the download sheet used to resolve a
     * stream and then ask googlevideo how long it was. Only the visionOS
     * answer is read, so an empty list means "unknown", not "unavailable" -
     * the download itself still has the fallback chain behind it.
     */
    suspend fun getDownloadAudioFormats(videoId: String): List<DownloadAudioFormat> =
        withContext(Dispatchers.IO) {
            val streamingData = playerSession.visionOs(videoId).answer?.streamingData
                ?: return@withContext emptyList()
            val originals = m4aAudioFormats(streamingData)
            val best = originals.maxByOrNull { it.optInt("bitrate") }
                ?: return@withContext emptyList()
            val smallest = originals.minByOrNull { it.optInt("bitrate") }
            fun org.json.JSONObject.toOption(quality: String) = DownloadAudioFormat(
                quality = quality,
                bitrate = optInt("averageBitrate").takeIf { it > 0 } ?: optInt("bitrate"),
                contentLength = optString("contentLength").toLongOrNull()?.takeIf { it > 0L },
            )
            buildList {
                add(best.toOption(ThemePreferences.DOWNLOAD_MUSIC_QUALITY_HIGH))
                if (smallest != null && smallest.optInt("itag") != best.optInt("itag")) {
                    add(smallest.toOption(ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER))
                }
            }
        }

    // --- Identity -----------------------------------------------------------
    // The visitorData token itself lives in VisitorIdentity.

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


    suspend fun getRecommendations(): List<Song> = musicBrowse.getRecommendations()

    suspend fun getRelatedSongs(videoId: String, limit: Int = 25): List<Song> =
        musicBrowse.getRelatedSongs(videoId, limit)

    suspend fun getSongFromPanel(videoId: String): Song? = musicBrowse.getSongFromPanel(videoId)

    suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = musicPlaylists.getUserPlaylists()

    suspend fun getLikedMusic(): List<Song> = musicPlaylists.getLikedMusic()
    
    suspend fun getPlaylist(playlistId: String): List<Song> = musicPlaylists.getPlaylist(playlistId)
    
    suspend fun fetchAccountInfo() = youtubeAccount.fetchAccountInfo()

    /**
     * Keep what a `/player` response carries besides its streams: the caption
     * tracklist, so a later CC tap is free, and the track's loudness.
     */
    private fun harvestPlayerResponse(videoId: String, root: org.json.JSONObject) {
        cacheCaptionTracks(videoId, parseCaptionTracks(root))
        cacheTrackLoudness(videoId, playerLoudnessDb(root))
    }

    /**
     * `playerConfig.audioConfig.loudnessDb`: how far the track's master sits
     * above YouTube's -14 LKFS target, so the playback correction is a gain of
     * the negation. See [TrackLoudnessStore]. Present on every OK response
     * probed (August 2026), but a missing key must read as unknown rather than
     * 0.0, which is a real measurement meaning "already at target".
     */
    private fun playerLoudnessDb(root: org.json.JSONObject): Float? =
        root.optJSONObject("playerConfig")
            ?.optJSONObject("audioConfig")
            ?.let { audio ->
                if (audio.has("loudnessDb")) audio.optDouble("loudnessDb").toFloat() else null
            }
            ?.takeIf { it.isFinite() }

    // --- Internal API Helper ---

    // --- Optimized Traversal Helpers ---

    // --- JSON Helpers ---


    suspend fun reportPlayback(videoId: String) = playbackHistory.reportPlayback(videoId)

    // ============== VIDEO MODE FUNCTIONS ==============

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

    suspend fun getTrendingVideos(): VideoFeedPage = videoFeeds.getTrendingVideos()

    suspend fun getTasteBasedVideos(seedOffset: Int = 0): List<VideoItem> = videoFeeds.getTasteBasedVideos(seedOffset)

    suspend fun getVideoFeedContinuation(continuation: String): VideoFeedPage =
        videoFeeds.getVideoFeedContinuation(continuation)

    internal suspend fun getWatchHistoryPage(

        continuation: String? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoFeedPage? =
        videoFeeds.getWatchHistoryPage(continuation, session)

    // ============================================================
    // YouTube Shorts (www.youtube.com): shelf feed + endless
    // reel_watch_sequence pager. Shapes verified against the live
    // API July 2026.
    // ============================================================

    suspend fun getShortsFeed(): List<ShortsItem> = videoFeeds.getShortsFeed()

    suspend fun getShortsSequence(sequenceParams: String): ShortsFeedPage = videoFeeds.getShortsSequence(sequenceParams)

    // ============================================================
    // Video library (www.youtube.com): user playlists, Watch Later,
    // Liked videos. Shapes verified against the live API July 2026.
    // ============================================================

    suspend fun getVideoPlaylists(): List<VideoPlaylist> = videoPlaylists.getVideoPlaylists()

    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> = videoPlaylists.getPlaylistVideos(playlistId)

    internal suspend fun getPlaylistVideosPage(

        playlistId: String,
        continuation: VideoPlaylistCursor? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoPlaylistPage? =
        videoPlaylists.getPlaylistVideosPage(playlistId, continuation, session)

    suspend fun getCompletePlaylistVideos(playlistId: String): List<VideoItem>? =
        videoPlaylists.getCompletePlaylistVideos(playlistId)

    suspend fun getPlaylistHeader(playlistId: String): PlaylistPageInfo? = videoPlaylists.getPlaylistHeader(playlistId)

    // --- Video Parsing Helpers ---

    private fun getChannelAvatarUrl(channelId: String?): String? {
        if (channelId.isNullOrBlank()) return null
        
        try {
            // Use regular YouTube browse for channels/handles
            val json = webApi.fetchYouTubeBrowse(channelId).takeIf { it.isNotEmpty() } ?: return null
            val root = org.json.JSONObject(json)
            
            val header = root.optJSONObject("header")
            
            // 1. C4TabbedHeaderRenderer
            val c4Header = header?.optJSONObject("c4TabbedHeaderRenderer")
            if (c4Header != null) {
                val thumbs = c4Header.optJSONObject("avatar")?.optJSONArray("thumbnails")
                return thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
            }
            
            // 2. PageHeader (New UI)
            val pageHeader = header?.optJSONObject("pageHeaderRenderer")?.optJSONObject("content")
                ?.optJSONObject("pageHeaderViewModel")?.optJSONObject("image")
                ?.optJSONObject("decoratedAvatarViewModel")?.optJSONObject("avatar")
                ?.optJSONObject("avatarViewModel")?.optJSONObject("image")
                
            val sources = pageHeader?.optJSONArray("sources")
            if (sources != null && sources.length() > 0) {
                return sources.optJSONObject(sources.length() - 1)?.optString("url")
            }
            
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching channel avatar", e)
        }
        return null
    }

    /**
     * FAST: Get only video stream qualities for immediate playback.
     * Does NOT fetch channel avatar, related videos, or extra metadata.
     * Use this to start playback ASAP, then call getVideoDetails() for the rest.
     */
    suspend fun getVideoStreamQualities(
        videoId: String,
        includeHdr: Boolean = false,
    ): List<VideoQuality> = getVideoStreamResult(videoId, includeHdr).qualities

    /**
     * Resolve the quality ladder and the storyboard harvested by that exact
     * extraction as one value. Callers that render a scrub preview must use
     * this API instead of trying to coordinate two independently mutable reads.
     */
    suspend fun getVideoStreamResult(
        videoId: String,
        includeHdr: Boolean = false,
    ): VideoStreamResult =
        VideoStreamResolutionCache.getOrResolve(videoId, includeHdr) {
            resolveVideoStreamResult(videoId, includeHdr)
        }

    /** Forget a failed ladder before retrying the same video. */
    fun invalidateVideoStreamResult(videoId: String) {
        VideoStreamResolutionCache.invalidate(videoId)
    }

    private suspend fun resolveVideoStreamResult(
        videoId: String,
        includeHdr: Boolean = false,
    ): VideoStreamResult = withContext(Dispatchers.IO) {
        // Primary: one visionOS /player under Koda's own visitorData. See
        // PlayerSession.visionOs for why this is not NewPipe any more. The
        // direct parser keeps HDR itags 330-337 that NewPipe v0.26.5's ItagItem
        // table drops, so HDR comes from this same response rather than from a
        // second request merged into NewPipe's ladder as it used to.
        val direct = playerSession.visionOs(videoId)
        direct.answer?.let { answer ->
            val qualities = parseQualitiesFromStreamingData(answer.streamingData, includeHdr)
            if (qualities.isNotEmpty()) {
                BotCheckVerdict.clearOnSuccess()
                // Makes a CC tap free: getCaptionTracks reads this cache first.
                cacheCaptionTracks(videoId, parseCaptionTracks(answer.root))
                KLog.i(
                    "YouTubeRepo",
                    "Video qualities via visionOS: ${qualities.size} for $videoId" +
                        qualities.count(VideoQuality::isHdr).let { if (it > 0) " (HDR=$it)" else "" },
                )
                // Dubs ride the same response. Live has one soundtrack in its
                // HLS master, so only a VOD ladder offers a choice.
                val audioTracks = if (qualities.any(VideoQuality::isLive)) {
                    emptyList()
                } else {
                    parseDirectAudioTracks(answer.streamingData)
                }
                // Best-effort: a malformed spec costs the scrub preview, never
                // the stream.
                val seekPreview =
                    runCatching { parseStoryboardSeekPreview(answer.root) }.getOrNull()
                return@withContext VideoStreamResult(qualities, seekPreview, audioTracks)
            }
            KLog.w("YouTubeRepo", "visionOS answered with no usable formats for $videoId")
        }

        // Fallback: NewPipe's maintained Android reel + visionOS chain, with
        // identities of its own. It no longer carries the HDR augmentation - a
        // video that reaches this far is already a failure being covered, and
        // the augmentation was the same visionOS call that just failed.
        var newPipeBotChecked = false
        try {
            val extracted = getVideoStreamsFromNewPipe(videoId)
            if (extracted.qualities.isNotEmpty()) {
                BotCheckVerdict.clearOnSuccess()
                KLog.i(
                    "YouTubeRepo",
                    "Video qualities via NewPipe fallback: ${extracted.qualities.size} for $videoId",
                )
                return@withContext extracted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            newPipeBotChecked = e.isNewPipeBotCheck()
            KLog.w(
                "YouTubeRepo",
                "NewPipe quality resolution failed, falling back to direct InnerTube",
                e,
            )
        }

        // Last resort: the ANDROID_VR -> IOS chain. Still useful for a
        // client-specific edge case, but never the normal VOD path: ANDROID_VR
        // URLs hit googlevideo's progressive byte ceiling on long videos even
        // though /player succeeds, so the source starts and then dies part-way.
        try {
            VideoStreamResult(
                getVideoQualitiesFromInnerTube(
                    videoId,
                    includeHdr,
                    newPipeBotChecked || direct.botChecked,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error getting video stream qualities", e)
            VideoStreamResult(emptyList())
        }
    }

    /**
     * Resolve playable URLs through the maintained NewPipe client chain.
     *
     * Only actual URL streams are admitted. NewPipe can also expose generated
     * DASH manifest text through the same Stream model (`isUrl == false`); that
     * content is not a URI and handing it to Media3's progressive source fails
     * before the first frame.
     */
    private fun getVideoStreamsFromNewPipe(videoId: String): VideoStreamResult {
        val extractor = youtubeService.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()

        // Storyboards ride the same extraction as the stream URLs. Prefer the
        // largest usable frameset so a fullscreen scrub preview stays sharp;
        // failure is best-effort and must never hold playback resolution up.
        val seekPreview = runCatching {
            extractor.frames
                .asSequence()
                .filter {
                    it.urls.isNotEmpty() && it.frameWidth > 0 && it.frameHeight > 0 &&
                        it.framesPerPageX > 0 && it.framesPerPageY > 0 &&
                        it.totalCount > 0 && it.durationPerFrame > 0
                }
                .maxByOrNull { it.frameWidth * it.frameHeight }
                ?.let {
                    VideoSeekPreview(
                        pageUrls = it.urls,
                        frameWidthPx = it.frameWidth,
                        frameHeightPx = it.frameHeight,
                        framesPerPageX = it.framesPerPageX,
                        framesPerPageY = it.framesPerPageY,
                        totalFrameCount = it.totalCount,
                        durationPerFrameMs = it.durationPerFrame,
                    )
                }
        }.getOrNull()

        val videoOnlyStreams = extractor.videoOnlyStreams
        val muxedStreams = extractor.videoStreams
        val isLiveStream = extractor.streamType == StreamType.LIVE_STREAM ||
            extractor.streamType == StreamType.AUDIO_LIVE_STREAM
        val sourceAspect = (videoOnlyStreams + muxedStreams)
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.height }
            ?.let { it.width.toFloat() / it.height.toFloat() }

        val extractedAudioStreams = extractor.audioStreams
        val hasAlternateAudioTracks = extractedAudioStreams.any {
            it.audioTrackType != null && it.audioTrackType != AudioTrackType.ORIGINAL
        }
        val qualities = mutableListOf<VideoQuality>()
        // Live progressive endpoints are unusable: a live broadcast is only
        // playable through its HLS master playlist, including its audio
        // rendition. Never let a live DASH URL win merely because NewPipe
        // happened to expose both manifest fields.
        val manifest = if (isLiveStream) {
            extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { "HLS" to it }
        } else {
            extractor.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { "DASH" to it }
                ?: extractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { "HLS" to it }
        }
        manifest?.let { (format, url) ->
            qualities.add(
                VideoQuality(
                    resolution = if (format == "HLS") "Auto (HLS)" else "Auto (Best)",
                    url = url,
                    format = format,
                    isDASH = true,
                    isLive = isLiveStream,
                    sourceAspectRatio = sourceAspect,
                )
            )
        }

        // Progressive live entries are segment endpoints, not complete files.
        if (isLiveStream) return VideoStreamResult(qualities)

        val bestAudio = originalAudioStreams(extractedAudioStreams)
            .asSequence()
            .filter { it.isUrl }
            // MP4 downloads are remuxed on-device. Prefer AAC/M4A over the
            // usually-higher-bitrate Opus stream, which MediaMuxer cannot put
            // into an MP4 container reliably.
            .maxWithOrNull(
                compareBy<AudioStream>(
                    {
                        if (it.format?.suffix.equals("m4a", ignoreCase = true) ||
                            it.codec?.contains("mp4a", ignoreCase = true) == true
                        ) 1 else 0
                    },
                    { it.averageBitrate },
                )
            )
        val hasOriginalAdaptivePair = bestAudio != null && videoOnlyStreams.any { it.isUrl }
        if (hasAlternateAudioTracks && hasOriginalAdaptivePair) {
            // A manifest or muxed stream lets its issuing YouTube client pick
            // the default language again. When alternate tracks exist and we
            // have a known-original separate stream, expose only that
            // deterministic path—even for the "Auto" quality choice.
            qualities.removeAll { it.isDASH }
        }
        if (bestAudio != null) {
            videoOnlyStreams.asSequence()
                .filter { it.isUrl }
                .mapNotNull { stream ->
                    stream.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                        VideoQuality(
                            resolution = resolution,
                            url = stream.content,
                            format = stream.format?.suffix,
                            isDASH = false,
                            audioUrl = bestAudio.content,
                            sourceAspectRatio = sourceAspect,
                            codec = stream.codec,
                        )
                    }
                }
                .forEach(qualities::add)
        }

        if (!hasAlternateAudioTracks || !hasOriginalAdaptivePair) {
            muxedStreams.asSequence()
                .filter { it.isUrl }
                .mapNotNull { stream ->
                    stream.resolution?.takeIf { it.isNotBlank() }?.let { resolution ->
                        VideoQuality(
                            resolution = resolution,
                            url = stream.content,
                            format = stream.format?.suffix,
                            isDASH = false,
                            sourceAspectRatio = sourceAspect,
                            codec = stream.codec,
                        )
                    }
                }
                .forEach(qualities::add)
        }

        // NewPipe exposes several codecs and delivery types for the same
        // visible label. Collapse codec alternatives, but retain both a split
        // local-playback entry and a muxed download entry when both exist.
        return VideoStreamResult(
            deduplicateVideoQualityVariants(qualities),
            seekPreview,
            newPipeAudioTracks(extractedAudioStreams, hasOriginalAdaptivePair),
        )
    }

    /**
     * The same soundtrack menu as [parseDirectAudioTracks], built from NewPipe's
     * streams when the direct call failed. Offered only when the qualities are
     * split pairs, because the dub replaces a pair's audio half; a muxed or
     * manifest ladder has no half to replace. NewPipe folds machine dubs into
     * DUBBED, so this path cannot label them.
     */
    private fun newPipeAudioTracks(
        streams: List<AudioStream>,
        hasOriginalAdaptivePair: Boolean,
    ): List<YouTubeAudioTrack> {
        if (!hasOriginalAdaptivePair) return emptyList()
        val byTrack = streams
            .filter { it.isUrl && !it.audioTrackId.isNullOrBlank() }
            .groupBy { it.audioTrackId!! }
        if (byTrack.size < 2) return emptyList()
        return byTrack.mapNotNull { (id, group) ->
            val best = group.maxWithOrNull(
                compareBy<AudioStream>(
                    { if (it.codec?.contains("mp4a", ignoreCase = true) == true) 1 else 0 },
                    { it.averageBitrate },
                )
            ) ?: return@mapNotNull null
            YouTubeAudioTrack(
                id = id,
                displayName = best.audioTrackName?.takeIf { it.isNotBlank() } ?: id,
                languageTag = best.audioLocale?.toLanguageTag()
                    ?: id.substringBefore('.').takeIf { it.isNotBlank() },
                kind = when (best.audioTrackType) {
                    AudioTrackType.ORIGINAL -> YouTubeAudioTrackKind.ORIGINAL
                    AudioTrackType.DUBBED -> YouTubeAudioTrackKind.DUBBED
                    AudioTrackType.DESCRIPTIVE -> YouTubeAudioTrackKind.DESCRIPTIVE
                    AudioTrackType.SECONDARY -> YouTubeAudioTrackKind.SECONDARY
                    null -> YouTubeAudioTrackKind.UNKNOWN
                },
                url = best.content,
            )
        }.sortedForMenu()
    }

    /**
     * Resolve the full video quality ladder via InnerTube: ANDROID_VR first
     * (no PO token, unciphered URLs), IOS as fallback, with a one-shot
     * visitorData remint when the bot check flags the current token. Returns
     * an empty list when neither client yields usable streamingData.
     */
    private suspend fun getVideoQualitiesFromInnerTube(
        videoId: String,
        includeHdr: Boolean = false,
        newPipeBotChecked: Boolean = false,
    ): List<VideoQuality> {
        val streamingData = playerSession.nativeFallback(videoId, newPipeBotChecked) {
            harvestPlayerResponse(videoId, it)
        }?.streamingData ?: return emptyList()
        return parseQualitiesFromStreamingData(streamingData, includeHdr)
    }

    private fun parseQualitiesFromStreamingData(
        streamingData: org.json.JSONObject,
        includeHdr: Boolean = false,
    ): List<VideoQuality> = parseDirectVideoQualities(streamingData, includeHdr)

    /**
     * Get video details including qualities and related videos.
     */
    suspend fun getVideoDetails(videoId: String): VideoDetails = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val streamExtractor = youtubeService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()
            
            val qualities = mutableListOf<VideoQuality>()
            
            // 1. DASH/HLS
            streamExtractor.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { url ->
                qualities.add(VideoQuality("Auto (Best)", url, "DASH", true))
            } ?: streamExtractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { url ->
                qualities.add(VideoQuality("Auto (HLS)", url, "HLS", true))
            }
            
            // 2. Adaptive Streams
            val videoOnlyStreams = streamExtractor.videoOnlyStreams
            val audioStreams = originalAudioStreams(streamExtractor.audioStreams)
            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
            
            if (bestAudio != null) {
                qualities.addAll(videoOnlyStreams
                    .mapNotNull { stream ->
                        val res = stream.resolution ?: return@mapNotNull null
                        val url = stream.content ?: return@mapNotNull null
                        VideoQuality(res, url, stream.format?.name, false, bestAudio.content)
                    }
                )
            }

            // 3. Muxed Streams
            qualities.addAll(streamExtractor.videoStreams
                .mapNotNull { stream ->
                    val res = stream.resolution ?: return@mapNotNull null
                    val url = stream.content ?: return@mapNotNull null
                    VideoQuality(res, url, stream.format?.name, false)
                }
            )
            
            val finalQualities = deduplicateVideoQualityVariants(qualities)
            
            // Related Videos
            val relatedItems = streamExtractor.relatedItems?.items ?: emptyList()
            val related = relatedItems.mapNotNull { item: InfoItem ->
                if (item is StreamInfoItem) {
                    VideoItem.fromStreamInfoItem(
                        videoId = item.url.replace("https://www.youtube.com/watch?v=", ""),
                        title = item.name ?: "Unknown",
                        channelName = item.uploaderName ?: "Unknown",
                        channelIconUrl = null,
                        thumbnailUrl = item.thumbnails?.maxByOrNull { it.width }?.url,
                        durationSeconds = item.duration,
                        viewCount = item.viewCount,
                        uploadedDate = item.uploadDate?.let { try { it.offsetDateTime().toString() } catch(e:Exception){ null } },
                        isLive = item.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
                    )
                } else null
            }
            
            // Channel Info
            val channelName = streamExtractor.uploaderName ?: "Unknown"
            val uploaderUrl = streamExtractor.uploaderUrl ?: ""
            
            // Clean extraction of Channel ID or Handle
            val channelId = when {
                uploaderUrl.contains("/channel/") -> uploaderUrl.substringAfter("/channel/")
                uploaderUrl.contains("/@") -> uploaderUrl.substringAfter("/@").let { "@$it" }
                uploaderUrl.contains("/user/") -> uploaderUrl.substringAfter("/user/")
                else -> null
            }
            
            // 🌟 Try to fetch channel avatar - Priority 1: From Extractor directly
            var channelIconUrl = try {
                 streamExtractor.uploaderAvatars?.maxByOrNull { it.width }?.url
            } catch (e: Exception) { null }
            
            // Priority 2: From InnerTube Browse API
            if (channelIconUrl.isNullOrEmpty()) {
                channelIconUrl = getChannelAvatarUrl(channelId)
            }
            
            val subCount = streamExtractor.uploaderSubscriberCount
            
            // Create updated video item (using original videoId)
            val updatedVideoItem = VideoItem(
                videoId = videoId,
                title = streamExtractor.name ?: "Unknown",
                channelName = channelName,
                channelId = channelId,
                channelIconUrl = channelIconUrl,
                thumbnailUrl = streamExtractor.thumbnails?.maxByOrNull { it.width }?.url, // Use high res if available
                duration = streamExtractor.length,
                viewCount = VideoItem.formatViewCount(streamExtractor.viewCount),
                uploadedDate = streamExtractor.uploadDate?.let { try { it.offsetDateTime().toString() } catch(e:Exception){ null } },
                isLive = streamExtractor.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM,
                description = streamExtractor.description?.content,
                subscriberCount = if (subCount != null && subCount >= 0) VideoItem.formatViewCount(subCount).replace("views", "subscribers") else null
            )

            VideoDetails(finalQualities, related, updatedVideoItem)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error getting video details", e)
            VideoDetails(emptyList(), emptyList())
        }
    }

    // ============================================================
    // Video engagement: like/dislike, subscribe, comments
    // (InnerTube WEB client against www.youtube.com)
    // ============================================================

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    suspend fun getVideoEngagement(videoId: String): VideoEngagement? = watchPage.getVideoEngagement(videoId)

    suspend fun getVideoChannelId(videoId: String): String? = watchPage.getVideoChannelId(videoId)

    suspend fun getWatchNextData(videoId: String, baseVideo: VideoItem? = null): WatchNextData =
        watchPage.getWatchNextData(videoId, baseVideo)

    // ============================================================
    // Live chat (InnerTube WEB client against www.youtube.com)
    // ============================================================

    suspend fun getLiveChatSession(videoId: String): LiveChatSession? = liveChat.getLiveChatSession(videoId)

    suspend fun pollLiveChat(continuation: String): LiveChatPage? = liveChat.pollLiveChat(continuation)

    suspend fun sendLiveChatMessage(params: String, text: String): LiveChatSendResult =
        liveChat.sendLiveChatMessage(params, text)

    suspend fun getLiveMetadata(videoId: String): LiveMetadata? = liveChat.getLiveMetadata(videoId)

    /**
     * Parse captions.playerCaptionsTracklistRenderer.captionTracks out of a
     * /player response. Each entry carries a signed timedtext baseUrl, a
     * languageCode, a display name (runs on the native clients, simpleText on
     * WEB) and, for auto-captions, kind == "asr" / a vssId prefixed "a.".
     * Manually authored tracks are listed before auto-generated ones.
     * Verified against the live /player API July 2026.
     */
    private fun parseCaptionTracks(root: org.json.JSONObject): List<CaptionTrack> {
        return try {
            val tracks = root.optJSONObject("captions")
                ?.optJSONObject("playerCaptionsTracklistRenderer")
                ?.optJSONArray("captionTracks")
                ?: return emptyList()

            (0 until tracks.length()).mapNotNull { i ->
                val t = tracks.optJSONObject(i) ?: return@mapNotNull null
                val baseUrl = t.optString("baseUrl").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val languageCode = t.optString("languageCode").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val name = getRunText(t.optJSONObject("name"))?.takeIf { it.isNotBlank() }
                    ?: languageCode
                CaptionTrack(
                    languageCode = languageCode,
                    name = name,
                    baseUrl = baseUrl,
                    // vssId is the more reliable marker: the native clients
                    // sometimes omit "kind" while still prefixing vssId "a.".
                    isAutoGenerated = t.optString("kind") == "asr" ||
                        t.optString("vssId").startsWith("a."),
                )
            }
                .distinctBy { it.languageCode to it.isAutoGenerated }
                .sortedBy { it.isAutoGenerated }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "parseCaptionTracks failed", e)
            emptyList()
        }
    }

    /**
     * Caption/subtitle tracks for a video.
     *
     * Resolved with the ANDROID_VR client (IOS as fallback) — the same chain
     * used for streams, and deliberately *not* WEB: a WEB /player call without
     * account cookies comes back UNPLAYABLE ("Video unavailable") with no
     * captions block at all, so every signed-out user saw an empty CC menu.
     * The native clients answer with the full tracklist either way.
     *
     * Normally free: the tracklist is cached from the /player response
     * fetched to start playback, so this only hits the network when that
     * cache missed or went stale.
     */
    suspend fun getCaptionTracks(videoId: String): List<CaptionTrack> = withContext(Dispatchers.IO) {
        cachedCaptionTracks(videoId)?.let { return@withContext it }
        try {
            suspend fun via(client: PlayerClient): List<CaptionTrack> =
                playerSession.single(videoId, client).okRoot()?.let(::parseCaptionTracks).orEmpty()
            val tracks = via(PlayerClients.ANDROID_VR).ifEmpty { via(PlayerClients.IOS) }
            cacheCaptionTracks(videoId, tracks)
            tracks
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getCaptionTracks failed for $videoId", e)
            emptyList()
        }
    }

    private fun cachedCaptionTracks(videoId: String): List<CaptionTrack>? {
        val entry = captionCache[videoId] ?: return null
        if (System.currentTimeMillis() - entry.fetchedAt > CAPTION_CACHE_TTL_MS) {
            captionCache.remove(videoId)
            return null
        }
        return entry.tracks
    }

    private fun cacheCaptionTracks(videoId: String, tracks: List<CaptionTrack>) {
        if (tracks.isEmpty()) return
        captionCache[videoId] = CachedCaptions(tracks, System.currentTimeMillis())
    }

    /**
     * Persist the track's loudness alongside the captions harvested from the
     * same response.
     *
     * Written here rather than at the caller because this is the one place
     * every `/player` response passes through, and because a song that is
     * already fully cached never comes back this way - see
     * [TrackLoudnessStore] for why that makes persistence the point.
     */
    private fun cacheTrackLoudness(videoId: String, loudnessDb: Float?) {
        TrackLoudnessStore.put(context, videoId, loudnessDb ?: return)
    }

    /**
     * Download and parse one caption track into cues the player overlay can
     * render itself.
     *
     * Captions deliberately do not travel through ExoPlayer as a sideloaded
     * text track: that made them part of the media source, so turning captions
     * on or off rebuilt the source and discarded the entire video buffer. The
     * timedtext endpoint lives on www.youtube.com rather than googlevideo, so a
     * plain browser User-Agent is enough and no ranged chunking is needed - the
     * payload is a few tens of KB.
     *
     * Returns an empty list on any failure; captions are best-effort and must
     * never take playback down with them.
     */
    suspend fun getCaptionCues(track: CaptionTrack): List<VttCue> =
        getCaptionVtt(track)?.let { WebVttParser.parse(it) }.orEmpty()

    /**
     * One caption track as the WebVTT document timedtext serves, unparsed, or
     * null on any failure. The player parses it straight away; a video download
     * keeps it as it is, to be parsed when the file is watched offline.
     */
    suspend fun getCaptionVtt(track: CaptionTrack): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(track.vttUrl)
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .build()
            http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    KLog.w(
                        "YouTubeRepo",
                        "Caption fetch failed for ${track.languageCode}: HTTP ${response.code}"
                    )
                    return@withContext null
                }
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Caption fetch failed for ${track.languageCode}", e)
            null
        }
    }

    suspend fun getPostDetail(detailParams: String): ChannelPost? = commentThreads.getPostDetail(detailParams)

    suspend fun getPostCommentsToken(detailParams: String): String? = commentThreads.getPostCommentsToken(detailParams)

    suspend fun getCommentsPage(token: String, viaBrowse: Boolean = false): CommentsPage? =
        commentThreads.getCommentsPage(token, viaBrowse)

    suspend fun rateVideo(videoId: String, status: LikeStatus): Boolean = watchPage.rateVideo(videoId, status)

    suspend fun setSubscribed(channelId: String, subscribe: Boolean): Boolean =
        watchPage.setSubscribed(channelId, subscribe)

    suspend fun setChannelBell(bell: ChannelBell, level: BellLevel): ChannelBellChange? =
        watchPage.setChannelBell(bell, level)

    // ============================================================
    // Playlist editing: playlist/create, playlist/delete and
    // browse/edit_playlist. The same InnerTube write endpoints exist
    // on both hosts — music=true goes through music.youtube.com
    // (WEB_REMIX) so edits land in the YouTube Music library,
    // music=false through www.youtube.com (WEB) for video playlists
    // and Watch Later. These are the long-stable action-based writes
    // (same family as like/subscribe above); responses are only
    // checked for a STATUS_SUCCEEDED/playlistId, never deep-parsed.
    // ============================================================

    suspend fun createYouTubePlaylist(title: String, music: Boolean, videoIds: List<String> = emptyList()): String? =
        playlistEditing.createYouTubePlaylist(title, music, videoIds)

    suspend fun uploadPlaylist(title: String, description: String?, videoIds: List<String>): PlaylistUpload? =
        playlistEditing.uploadPlaylist(title, description, videoIds)

    suspend fun setPlaylistInLibrary(playlistId: String, saved: Boolean): Boolean =
        playlistEditing.setPlaylistInLibrary(playlistId, saved)

    suspend fun deleteYouTubePlaylist(playlistId: String, music: Boolean): Boolean =
        playlistEditing.deleteYouTubePlaylist(playlistId, music)

    suspend fun renameYouTubePlaylist(

        playlistId: String,
        title: String,
        music: Boolean,
        description: String? = null
    ): Boolean =
        playlistEditing.renameYouTubePlaylist(playlistId, title, music, description)

    suspend fun addToYouTubePlaylist(playlistId: String, videoId: String, music: Boolean): Boolean =
        playlistEditing.addToYouTubePlaylist(playlistId, videoId, music)

    suspend fun removeFromYouTubePlaylist(playlistId: String, videoId: String, music: Boolean): Boolean =
        playlistEditing.removeFromYouTubePlaylist(playlistId, videoId, music)

    suspend fun getPlaylistsContaining(videoId: String): Set<String>? = playlistEditing.getPlaylistsContaining(videoId)

    suspend fun getPlaylistSetVideoIds(playlistId: String): Map<String, List<String>> =
        playlistEditing.getPlaylistSetVideoIds(playlistId)

    suspend fun moveInYouTubePlaylist(

        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
        music: Boolean
    ): Boolean =
        playlistEditing.moveInYouTubePlaylist(playlistId, setVideoId, successorSetVideoId, music)

    suspend fun createComment(createCommentParams: String, text: String): CommentItem? =
        commentThreads.createComment(createCommentParams, text)

    suspend fun createCommentReply(createReplyParams: String, text: String): CommentItem? =
        commentThreads.createCommentReply(createReplyParams, text)

    suspend fun performCommentAction(action: String): Boolean = commentThreads.performCommentAction(action)

    internal suspend fun beginVideoHistorySession(videoId: String, positionMs: Long): VideoHistorySession? =
        playbackHistory.beginVideoHistorySession(videoId, positionMs)

    internal suspend fun reportVideoWatchProgress(

        session: VideoHistorySession,
        startMs: Long,
        positionMs: Long,
        final: Boolean,
    ): HistoryPingResult =
        playbackHistory.reportVideoWatchProgress(session, startMs, positionMs, final)

    suspend fun getSubscriptionsFeedPage(): VideoFeedPage = videoFeeds.getSubscriptionsFeedPage()

    suspend fun getChannelPosts(channelId: String): List<ChannelPost> = channelPages.getChannelPosts(channelId)

    suspend fun getSubscribedChannels(): List<SubscribedChannel> = channelPages.getSubscribedChannels()

    suspend fun getChannelVideos(channel: SubscribedChannel): List<VideoItem> = channelPages.getChannelVideos(channel)

    suspend fun getChannelMixPool(channel: SubscribedChannel): ChannelMixPool = channelPages.getChannelMixPool(channel)

    // ============================================================
    // The channel page. Verified against live responses, signed out,
    // August 2026.
    //
    // **A channel page describes itself, and this section is written to let it.**
    // The first browse returns the tab list with each tab's own `params`, every
    // sort order with its own continuation token, and every next page as
    // another token. So there is exactly one hardcoded browse parameter in here
    // (CHANNEL_VIDEOS_TAB_PARAMS, kept only as the fallback for a response whose
    // tab list failed to parse), and no fixed set of tabs.
    //
    // That matters beyond tidiness. Tab sets genuinely differ per channel:
    // a musician has "Releases" where a teacher has "Courses" and a big tech
    // channel has "Podcasts" and "Store". Hardcoding the six tabs YouTube shows
    // one channel would have meant drawing empty tabs on channels that lack
    // them and hiding real ones on channels that have more - and both failures
    // are silent, which is the worst kind.
    //
    // Everything here works signed out, which is the whole point: deciding
    // whether a creator is worth following is exactly the thing a signed-out
    // user does most.
    // ============================================================

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

    // ============================================================
    // Local subscriptions: following channels with no YouTube account.
    //
    // The account-backed path above (FEsubscriptions / FEchannels) is one
    // browse call for the whole feed because YouTube assembles it server
    // side. Nobody assembles a device-local feed, so it is built here by
    // fetching each followed channel and merging - which makes the cost per
    // refresh linear in the number of subscriptions, and makes the choice of
    // per-channel source the single most important decision in this section.
    // ============================================================

    suspend fun resolveChannelId(urlOrHandle: String): String? = channelPages.resolveChannelId(urlOrHandle)

    suspend fun getChannelProfile(channelId: String): ChannelProfile? = channelPages.getChannelProfile(channelId)

    suspend fun getChannelFeedRss(channelId: String, avatarUrl: String? = null): List<VideoItem> =
        localFeed.getChannelFeedRss(channelId, avatarUrl)

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

    suspend fun sendDismissalFeedback(token: String?): Boolean = videoFeeds.sendDismissalFeedback(token)

    suspend fun getNotifications(): List<NotificationItem> = youtubeAccount.getNotifications()
}
