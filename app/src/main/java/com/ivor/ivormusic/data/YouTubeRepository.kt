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
    private val videoHistoryRepository by lazy { VideoHistoryRepository(context) }
    private val videoHistoryPreferences by lazy { ThemePreferences(context) }

    companion object {
        /** The filter [search] takes for songs, which is also its default. */
        const val FILTER_SONGS = com.ivor.ivormusic.data.youtube.FILTER_SONGS

        private const val UPLOAD_CREATE_BATCH = 100
        private const val UPLOAD_ADD_BATCH = 50
        private const val UPLOAD_BATCH_PAUSE_MS = 400L

        // Regular YouTube filters
        const val FILTER_YOUTUBE_VIDEOS = "videos"
        const val FILTER_YOUTUBE_PLAYLISTS = "playlists"
        const val FILTER_YOUTUBE_CHANNELS = "channels"
        
        // browse params selecting a channel's Videos tab (protobuf: "videos")
        private const val CHANNEL_VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

        // browse params selecting a channel's Posts tab (protobuf: "posts").
        // The second and last hardcoded tab: the feeds sample posts from
        // channels whose page was never opened, so there is no tab list to
        // read it from. Identical on all five channels that had the tab, and a
        // channel without one answers with another tab and no posts.
        // [verified October 2026, signed in, WEB]
        private const val CHANNEL_POSTS_TAB_PARAMS = "EgVwb3N0c_IGBAoCSgA%3D"

        // How many subscribed channels the local feed fetches at once. The
        // local feed costs one request per channel, so this is the only thing
        // standing between a 300-subscription refresh and 300 simultaneous
        // sockets - which mobile radios handle badly and which looks like a
        // scrape from the other end. Six keeps a large refresh moving without
        // starving whatever else the app is loading.
        private const val FEED_CONCURRENCY = 6

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

        // The channel Atom feed only ever returns 15 entries, so this takes
        // everything it has and lets the global sort decide what survives.
        private const val MAX_FEED_ITEMS_PER_CHANNEL = 15

        // Ceiling on the merged feed. 300 subscriptions x 15 uploads is 4500
        // items, which is a lot of LazyColumn for a list nobody scrolls past
        // the first screen of.
        private const val MAX_FEED_ITEMS = 300

        // Avatar/name backfill is one channel browse each - the expensive
        // shape the RSS feed exists to avoid - so a run is capped and the
        // rest is picked up on later visits.
        private const val PROFILE_BACKFILL_LIMIT = 12

        // Where a music history ping may carry the account's cookies. WEB_REMIX
        // /player returns its tracking URLs on s.youtube.com, with no cpn, c or
        // ver of their own [verified September 2026, signed-in probe]; the
        // video path's www.youtube.com is allowed as well.
        private val MUSIC_HISTORY_TRACKING_HOSTS = setOf("s.youtube.com", "www.youtube.com")

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

    /** What the history /player calls must carry; see [PlayerSignatureTimestamp]. */
    private val playerSignatureTimestamp = PlayerSignatureTimestamp(context, http.okHttpClient)

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
        videoSearchExtractorCache.clear()
        videoSearchNextPageCache.clear()
        videoSearchContinuations.clear()
    }

    private data class VideoSearchKey(val query: String, val sort: VideoSearchSort)
    private val videoSearchExtractorCache =
        mutableMapOf<VideoSearchKey, org.schabi.newpipe.extractor.search.SearchExtractor>()
    private val videoSearchNextPageCache = mutableMapOf<VideoSearchKey, Page?>()
    // Present null means InnerTube exhausted; it must never resume NewPipe.
    private val videoSearchContinuations = mutableMapOf<VideoSearchKey, String?>()

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


    private fun generateCpn(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }

    /**
     * Reports playback to YouTube Music history.
     * This mimics the web player's behavior to ensure the song appears in history.
     * 
     * The flow is:
     * 1. Call /player endpoint to get playback tracking URLs
     * 2. Call the videostatsPlaybackUrl to register the play in history
     */
    suspend fun reportPlayback(videoId: String) = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext
        // Incognito covers the account's own history too, not only Koda's.
        // Gated here rather than at the call sites so nothing that starts
        // playback later has to remember. The music history switch does not:
        // it is the on-device log, and its setting says it is separate from
        // the account's history (si_music_history).
        if (IncognitoMode.isEnabled(context)) return@withContext

        try {
            val session = sessionManager.captureSession() ?: return@withContext
            val cpn = generateCpn()

            // This install's own token, which every other InnerTube call rides
            // on, minted here if none is cached yet (a fresh install's first
            // play). Never a fallback literal: a hardcoded visitor id is a
            // stranger's session, and without a token the play is skipped
            // rather than filed under one.
            val visitorData = http.visitorIdentity.current().ifEmpty {
                KLog.w("YouTubeRepo", "History sync: no visitorData, skipped $videoId")
                return@withContext
            }

            // Client constants - using WEB_REMIX (web player)
            val clientName = "WEB_REMIX"
            val clientVersion = WEB_REMIX_VERSION

            // Step 1: Call player endpoint to get tracking URLs. It must carry
            // the player's signatureTimestamp: without one, every video answers
            // "Video unavailable" with no playbackTracking (October 2026).
            fun postPlayer(signatureTimestamp: Int): String? {
                val jsonBody = org.json.JSONObject()
                    .put("context", org.json.JSONObject().put("client", org.json.JSONObject()
                        .put("clientName", clientName)
                        .put("clientVersion", clientVersion)
                        .put("hl", "en")
                        .put("gl", http.contentRegion())
                        .put("visitorData", visitorData)))
                    .put("videoId", videoId)
                    .put("cpn", cpn)
                    .put("playbackContext", org.json.JSONObject().put("contentPlaybackContext",
                        org.json.JSONObject().put("signatureTimestamp", signatureTimestamp)))
                    .toString()
                val playerRequest = okhttp3.Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/player")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .authenticate(session)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Origin", "https://music.youtube.com")
                    .addHeader("Referer", "https://music.youtube.com/")
                    .addHeader("X-Goog-Api-Format-Version", "1")
                    .addHeader("X-YouTube-Client-Name", "67") // WEB_REMIX numeric ID
                    .addHeader("X-YouTube-Client-Version", clientVersion)
                    .addHeader("X-Goog-Visitor-Id", visitorData)
                    .build()
                return http.okHttpClient.newCall(playerRequest).execute().use { response ->
                    response.body?.string().also {
                        if (it.isNullOrEmpty()) {
                            KLog.e("YouTubeRepo", "History sync: /player HTTP ${response.code} with an empty body for $videoId")
                        }
                    }
                }
            }

            val sentTimestamp = playerSignatureTimestamp.current()
            var playerResponseBody = postPlayer(sentTimestamp)
                ?.takeIf { it.isNotEmpty() } ?: return@withContext
            if (historyPlayerSignedOut(playerResponseBody, session, "Music")) return@withContext
            var playerJson = org.json.JSONObject(playerResponseBody)
            if (playerJson.optJSONObject("playbackTracking") == null) {
                // A signatureTimestamp YouTube no longer accepts looks exactly
                // like this; retry once if a fresh read gives another value.
                val retry = playerSignatureTimestamp.refreshAfterRejection(sentTimestamp)
                if (retry != null) {
                    KLog.w("YouTubeRepo", "History sync: no playbackTracking with sts $sentTimestamp, retrying with $retry")
                    playerResponseBody = postPlayer(retry)
                        ?.takeIf { it.isNotEmpty() } ?: return@withContext
                    if (historyPlayerSignedOut(playerResponseBody, session, "Music")) return@withContext
                    playerJson = org.json.JSONObject(playerResponseBody)
                }
            }

            // Parse response to extract playback tracking URL
            val playbackTracking = playerJson.optJSONObject("playbackTracking")

            if (playbackTracking == null) {
                // Log more details about the error
                val playabilityStatus = playerJson.optJSONObject("playabilityStatus")
                val status = playabilityStatus?.optString("status")
                val reason = playabilityStatus?.optString("reason")
                KLog.e("YouTubeRepo", "No playbackTracking. Status: $status, Reason: $reason")
                return@withContext
            }
            
            val videostatsPlaybackUrl = playbackTracking
                .optJSONObject("videostatsPlaybackUrl")
                ?.optString("baseUrl")

            if (videostatsPlaybackUrl.isNullOrEmpty()) {
                KLog.e("YouTubeRepo", "No playback tracking URL found for $videoId")
                return@withContext
            }

            // Step 3: Call the tracking URL to register the play. Credentials
            // only go to the YouTube tracking hosts /player returns, and each
            // parameter is set rather than appended, so one the URL already
            // carries is replaced instead of sent twice.
            val baseTrackingUrl = videostatsPlaybackUrl.toHttpUrlOrNull()
            if (baseTrackingUrl == null || baseTrackingUrl.scheme != "https" ||
                baseTrackingUrl.host !in MUSIC_HISTORY_TRACKING_HOSTS
            ) {
                KLog.w("YouTubeRepo", "History sync: refused a tracking URL on ${baseTrackingUrl?.host}")
                return@withContext
            }
            val trackingUrl = baseTrackingUrl.newBuilder()
                .setQueryParameter("cpn", cpn)
                .setQueryParameter("ver", "2")
                .setQueryParameter("c", clientName)
                .build()

            // The /player call above blocks, and the switch can be flipped or
            // the account changed while it does. This ping is the write that
            // reaches the account, so both are rechecked against live state
            // here rather than only at the top. currentSession also hands back
            // the cookies as they are now, which /player itself may have
            // rotated on the way through.
            if (IncognitoMode.isEnabled(context)) return@withContext
            val live = sessionManager.currentSession(session) ?: run {
                KLog.w("YouTubeRepo", "History sync: login changed before the ping for $videoId")
                return@withContext
            }

            val trackingRequest = okhttp3.Request.Builder()
                .url(trackingUrl)
                .get()
                .authenticate(live)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/watch?v=$videoId")
                .build()

            val trackingResponse = http.okHttpClient.newCall(trackingRequest).execute()
            if (trackingResponse.isSuccessful) {
                KLog.d("YouTubeRepo", "History sync: ping accepted for $videoId")
            } else {
                KLog.e("YouTubeRepo", "History sync: ping HTTP ${trackingResponse.code} for $videoId")
            }
            trackingResponse.close()

        } catch (e: kotlinx.coroutines.CancellationException) {
            // The token mint above suspends; never swallow a cancellation.
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error in reportPlayback", e)
        }
    }

    // ============== VIDEO MODE FUNCTIONS ==============

    /**
     * Search for videos on YouTube (not YouTube Music).
     * Returns VideoItem objects with view counts, channel info, etc.
     * [dateFilter] restricts results by upload date via the `after:` search operator.
     * [sort] picks the result order; anything but relevance goes through a direct
     * InnerTube /search call (NewPipe's YouTube search cannot sort), falling back
     * to the relevance-ordered NewPipe path if that call fails.
     */
    suspend fun searchVideos(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY,
        sort: VideoSearchSort = VideoSearchSort.RELEVANCE
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val effectiveQuery = dateFilter.applyTo(query)
        val key = VideoSearchKey(effectiveQuery, sort)
        videoSearchExtractorCache.remove(key)
        videoSearchNextPageCache.remove(key)
        videoSearchContinuations.remove(key)

        if (sort != VideoSearchSort.RELEVANCE) {
            val sorted = searchVideosInnerTube(effectiveQuery, sort)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (sorted != null) {
                videoSearchContinuations[key] = sorted.continuation
                return@withContext sorted.videos
            }
            KLog.w("YouTubeRepo", "Sorted video search failed, falling back to relevance order")
        }

        try {
            // Use YouTube videos filter (not music_videos)
            val searchExtractor = newPipeGateway.regionalSearchExtractor(effectiveQuery, listOf(FILTER_YOUTUBE_VIDEOS), "")
            searchExtractor.fetchPage()

            // Cache for pagination (see searchVideosNext)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            videoSearchExtractorCache[key] = searchExtractor
            videoSearchNextPageCache[key] =
                if (searchExtractor.initialPage.hasNextPage()) searchExtractor.initialPage.nextPage else null

            searchExtractor.initialPage.items.toVideoItems(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error searching videos", e)
            emptyList()
        }
    }

    /** Continue exactly the query, date window and sort used for page one. */
    suspend fun searchVideosNext(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY,
        sort: VideoSearchSort = VideoSearchSort.RELEVANCE
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val key = VideoSearchKey(dateFilter.applyTo(query), sort)
            if (videoSearchContinuations.containsKey(key)) {
                val token = videoSearchContinuations[key] ?: return@withContext emptyList()
                val response = webApi.postWatchApi("search", org.json.JSONObject()
                    .put("context", webApi.webContext()).put("continuation", token))
                    ?: return@withContext emptyList()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val page = parseVideoSearchPage(response)
                videoSearchContinuations[key] = page.continuation?.takeUnless { it == token }
                return@withContext page.videos
            }
            val extractor = videoSearchExtractorCache[key] ?: return@withContext emptyList()
            val pageInfo = videoSearchNextPageCache[key] ?: return@withContext emptyList()
            val nextPage = extractor.getPage(pageInfo)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            videoSearchNextPageCache[key] =
                if (nextPage.hasNextPage()) nextPage.nextPage else null
            nextPage.items.toVideoItems(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error loading more video results", e)
            emptyList()
        }
    }

    /**
     * Search for playlists on regular YouTube (video mode search). Mapped to
     * [VideoPlaylist] so results plug straight into the same playlist detail
     * page and models the video Library tab uses.
     */
    suspend fun searchVideoPlaylists(query: String): List<VideoPlaylist> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_YOUTUBE_PLAYLISTS), "")
            searchExtractor.fetchPage()

            searchExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                val playlistId = item.url?.substringAfter("list=", "")
                    ?.substringBefore("&")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                VideoPlaylist(
                    playlistId = playlistId,
                    title = item.name?.takeIf { it.isNotBlank() } ?: "Unknown Playlist",
                    thumbnailUrl = item.thumbnails?.maxByOrNull { it.width }?.url
                        ?: item.thumbnails?.firstOrNull()?.url,
                    videoCountText = item.streamCount.takeIf { it > 0 }?.let { "$it videos" },
                    subtitle = item.uploaderName?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error searching video playlists", e)
            emptyList()
        }
    }

    /**
     * Channels matching [query], for video mode's Channels search filter.
     *
     * NewPipe's channel filter rather than an InnerTube search: it already
     * returns the canonical UC id, the avatar, the subscriber count and the
     * verified flag in one shape, and the channel page this feeds only needs
     * the id. Mirrors [searchArtists], which does the same on the music side.
     */
    suspend fun searchChannels(query: String): List<SubscribedChannel> =
        withContext(Dispatchers.IO) {
            try {
                val searchExtractor =
                    newPipeGateway.regionalSearchExtractor(query, listOf(FILTER_YOUTUBE_CHANNELS), "")
                searchExtractor.fetchPage()

                searchExtractor.initialPage.items
                    .filterIsInstance<ChannelInfoItem>()
                    .mapNotNull { item ->
                        // Every other call in the app keys off the canonical id,
                        // so a result whose URL is a handle rather than /channel/
                        // is dropped here instead of failing later on the page.
                        val channelId = item.url?.substringAfterLast('/')
                            ?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
                        SubscribedChannel(
                            channelId = channelId,
                            name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                            avatarUrl = item.thumbnails?.maxByOrNull { it.width }?.url
                                ?: item.thumbnails?.firstOrNull()?.url,
                            subscriberCountText = item.subscriberCount.takeIf { it >= 0 }
                                ?.let { "${VideoItem.formatViewCount(it)} subscribers" }
                        )
                    }
                    .distinctBy { it.channelId }
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Error searching channels", e)
                emptyList()
            }
        }

    /**
     * Base64url protobuf for the /search `params` field: sort order (field 1)
     * plus a filter block (field 2) pinning the result type to videos, the same
     * values the youtube.com filter sheet puts in the `sp` URL param.
     * Verified July 2026.
     */
    private fun buildVideoSearchParams(sort: VideoSearchSort): String {
        val bytes = byteArrayOf(0x08, sort.code.toByte(), 0x12, 0x02, 0x10, 0x01)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Video search through a direct InnerTube /search call, used when a
     * non-default sort order is picked. Results arrive as videoRenderers
     * (legacy shape, still what /search returns signed out) or lockupViewModels;
     * both parsers already exist for the feed. Verified July 2026.
     */
    private fun searchVideosInnerTube(query: String, sort: VideoSearchSort): VideoFeedPage? {
        return try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("query", query)
                .put("params", buildVideoSearchParams(sort))
            val response = webApi.postWatchApi("search", body) ?: return null
            parseVideoSearchPage(response)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "InnerTube video search failed", e)
            null
        }
    }

    /**
     * Get recommended videos for the video mode home screen.
     * 1. Logged in: personalized YouTube home feed (with a browse continuation
     *    token for endless scrolling — see [getVideoFeedContinuation]).
     * 2. Otherwise: taste-based mix built from the local watch history (pages
     *    by seed offset instead of a token — see [getTasteBasedVideos]).
     * 3. Cold start: generic popular search.
     * (YouTube removed the public Trending page in mid-2025 — the InnerTube
     * FEtrending browseId now returns HTTP 400, so no trending fallback.)
     */
    suspend fun getTrendingVideos(): VideoFeedPage = withContext(Dispatchers.IO) {
        val isLoggedIn = sessionManager.isLoggedIn()
        KLog.d("YouTubeRepo", "getTrendingVideos - isLoggedIn: $isLoggedIn")

        if (isLoggedIn) {
            try {
                val page = getPersonalizedVideoRecommendations()
                if (page.videos.isNotEmpty()) {
                    KLog.d("YouTubeRepo", "Got ${page.videos.size} personalized videos (continuation=${page.continuation != null})")
                    return@withContext page
                }
                KLog.w("YouTubeRepo", "Personalized recommendations empty, using taste-based feed")
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Error fetching personalized videos", e)
            }
        }

        try {
            val tasteFeed = getTasteBasedVideos()
            if (tasteFeed.isNotEmpty()) {
                KLog.d("YouTubeRepo", "Got ${tasteFeed.size} taste-based videos")
                return@withContext VideoFeedPage(tasteFeed)
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error building taste-based feed", e)
        }

        // Cold start: nothing watched yet and not logged in
        try {
            VideoFeedPage(searchVideos("trending videos ${java.time.Year.now().value}"))
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Cold-start search failed", e)
            VideoFeedPage(emptyList())
        }
    }

    /**
     * Related videos for a seed video from the watch-next endpoint.
     * Far lighter than a full NewPipe StreamExtractor fetch (one JSON call,
     * no stream resolution). Related items are lockupViewModels since 2025.
     */
    private fun getRelatedVideosLight(videoId: String): List<VideoItem> {
        return try {
            val root = fetchWatchNextRoot(videoId) ?: return emptyList()
            parseRelatedFromWatchNext(root)
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "getRelatedVideosLight failed for $videoId", e)
            emptyList()
        }
    }

    /**
     * Build a feed from the related videos of recently watched ones.
     * Interleaves per-seed results for variety; drops watched videos and dupes.
     * [seedOffset] pages through the watch history 6 seeds at a time so the
     * home feed can load more logged out: offset 0 seeds from the 6 most
     * recent videos, offset 6 from the next 6, and so on. Returns empty once
     * the history runs out of seeds.
     */
    suspend fun getTasteBasedVideos(seedOffset: Int = 0): List<VideoItem> = kotlinx.coroutines.coroutineScope {
        val history = videoHistoryRepository.getHistory()
        if (history.isEmpty()) return@coroutineScope emptyList()

        val seeds = history.drop(seedOffset).take(6)
        if (seeds.isEmpty()) return@coroutineScope emptyList()
        val historyIds = history.mapTo(HashSet()) { it.videoId }
        val perSeed = seeds.map { seed ->
            async(Dispatchers.IO) { getRelatedVideosLight(seed.videoId) }
        }.map { it.await() }

        val mixed = mutableListOf<VideoItem>()
        val seen = HashSet<String>()
        val longest = perSeed.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in perSeed) {
                val video = list.getOrNull(i) ?: continue
                if (video.videoId in historyIds || !seen.add(video.videoId)) continue
                mixed.add(video)
            }
        }
        mixed
    }

    /**
     * Get personalized video recommendations from YouTube (requires login).
     * Uses the YouTube homepage API to get personalized suggestions. The
     * returned page carries the rich-grid continuation token so the home feed
     * can keep loading (feed shape verified July 2026).
     */
    private suspend fun getPersonalizedVideoRecommendations(): VideoFeedPage = withContext(Dispatchers.IO) {
        val empty = VideoFeedPage(emptyList())
        val session = sessionManager.captureSession() ?: return@withContext empty
        val origin = "https://www.youtube.com"

        // Use YouTube browse endpoint for "What to Watch" (home page recommendations)
        val url = "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "$WEB_VERSION",
                        "hl": "en",
                        "gl": "${http.contentRegion()}",
                        "originalUrl": "https://www.youtube.com/",
                        "platform": "DESKTOP"
                    },
                    "user": {
                        "lockedSafetyMode": false
                    }
                },
                "browseId": "FEwhat_to_watch"
            }
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .authenticate(session, origin)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Origin", origin)
            .addHeader("Referer", "$origin/")
            .addHeader("X-Origin", origin)
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            // Never place Authorization material or response bodies in KLog:
            // users can deliberately attach its release ring buffer to a bug
            // report, and these values may carry account/feed information.
            KLog.d("YouTubeRepo", "Making personalized video request")
            val response = http.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext empty
            response.close()

            KLog.d("YouTubeRepo", "Personalized response received")
            val root = org.json.JSONObject(responseBody)
            val videos = parseVideosFromYouTubeJson(responseBody)
            KLog.d("YouTubeRepo", "Parsed ${videos.size} personalized videos")
            VideoFeedPage(videos, extractRichGridContinuation(root))
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error in getPersonalizedVideoRecommendations", e)
            empty
        }
    }

    /**
     * Next page of the personalized home feed from a browse continuation
     * token. The response carries appendContinuationItemsAction with ~23 more
     * richItemRenderers (lockupViewModel contents) plus the next page's
     * continuationItemRenderer. Requires login (the token comes from a signed
     * FEwhat_to_watch response). Shape verified July 2026.
     */
    suspend fun getVideoFeedContinuation(continuation: String): VideoFeedPage = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("continuation", continuation)
            val raw = webApi.postWatchApi("browse", body) ?: return@withContext VideoFeedPage(emptyList())
            val root = org.json.JSONObject(raw)

            val videos = mutableListOf<VideoItem>()
            var nextToken: String? = null
            val actions = root.optJSONArray("onResponseReceivedActions") ?: org.json.JSONArray()
            for (i in 0 until actions.length()) {
                val items = actions.optJSONObject(i)
                    ?.optJSONObject("appendContinuationItemsAction")
                    ?.optJSONArray("continuationItems")
                    ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val content = item.optJSONObject("richItemRenderer")?.optJSONObject("content")
                    content?.optJSONObject("lockupViewModel")?.let {
                        parseLockupViewModel(it)?.let { v -> videos.add(v) }
                    }
                    content?.optJSONObject("videoRenderer")?.let {
                        parseVideoRenderer(it)?.let { v -> videos.add(v) }
                    }
                    item.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { nextToken = it }
                }
            }
            KLog.d("YouTubeRepo", "Feed continuation: ${videos.size} videos, next=${nextToken != null}")
            VideoFeedPage(videos.distinctBy { it.videoId }, nextToken)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Feed continuation failed", e)
            VideoFeedPage(emptyList())
        }
    }

    /** One history page; Library must never walk an account's entire history. */
    internal suspend fun getWatchHistoryPage(
        continuation: String? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoFeedPage? = withContext(Dispatchers.IO) {
        if (session == null) return@withContext null
        try {
            val body = org.json.JSONObject().put("context", webApi.webContext())
            if (continuation == null) body.put("browseId", "FEhistory")
            else body.put("continuation", continuation)
            val raw = webApi.postWatchApi("browse", body, session)
                ?.takeIf { it.isNotBlank() } ?: return@withContext null
            val root = org.json.JSONObject(raw)
            if (root.has("error")) return@withContext null
            val videos = parseVideosFromYouTubeJson(raw, limit = Int.MAX_VALUE)
            KLog.d("YouTubeRepo", "History ${if (continuation == null) "first" else "next"} page: ${videos.size} videos")
            VideoFeedPage(videos, videoListContinuationToken(root))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching watch history page", e)
            null
        }
    }

    // ============================================================
    // YouTube Shorts (www.youtube.com): shelf feed + endless
    // reel_watch_sequence pager. Shapes verified against the live
    // API July 2026.
    // ============================================================

    /**
     * Signed-in Shorts use YouTube's seedless Shorts navigation, not a search
     * query. FEwhat_to_watch can contain no Shorts even with logged_in=1.
     * Seed and continuation shapes verified live September 2026.
     * Signed-out profiles retain the local-history search fallback.
     */
    suspend fun getShortsFeed(): List<ShortsItem> = withContext(Dispatchers.IO) {
        val session = sessionManager.captureSession()
        if (session != null) {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("params", "CA8%3D")
                .put("inputType", "REEL_WATCH_INPUT_TYPE_SEEDLESS")
                .put("disablePlayerResponse", true)
            val raw = webApi.postWatchApi("reel/reel_item_watch", body, session)
                ?: throw java.io.IOException("Shorts recommendations request failed")
            val root = org.json.JSONObject(raw)
            if (LOGGED_IN_TRACKING_PARAM.find(raw)?.groupValues?.get(1) == "0") {
                throw java.io.IOException("YouTube rejected the Shorts session")
            }
            val seed = parseShortsSeed(root)
            if (sessionManager.currentSession(session) == null) {
                throw kotlinx.coroutines.CancellationException("Shorts account changed")
            }
            val page = seed.continuation?.let { requestShortsSequence(it, session) }
            val continuation = if (page != null) page.continuation else seed.continuation
            // Every shelf tap continues after the loaded shelf, without replaying
            // a search candidate list or dropping the account's sequence token.
            return@withContext (seed.items + page?.items.orEmpty())
                .distinctBy { it.videoId }
                .map { it.copy(sequenceParams = continuation) }
        }
        val seedChannel = videoHistoryRepository.getHistory()
            .firstOrNull { it.channelName.isNotBlank() && it.channelName != "Unknown Channel" }
            ?.channelName
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("query", seedChannel?.let { "$it shorts" } ?: "trending shorts")
        val raw = webApi.postWatchApi("search", body)
            ?: throw java.io.IOException("Shorts search request failed")
        parseShortsLockups(org.json.JSONObject(raw))
    }

    /**
     * One account-aware reel_watch_sequence page. Errors must propagate so the
     * pager keeps its token for retry instead of treating a failure as exhaustion.
     * Seed params and continuation tokens use the same field (verified September 2026).
     */
    suspend fun getShortsSequence(sequenceParams: String): ShortsFeedPage = withContext(Dispatchers.IO) {
        requestShortsSequence(sequenceParams, sessionManager.captureSession())
    }

    private fun requestShortsSequence(sequenceParams: String, session: YouTubeSession?): ShortsFeedPage {
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("sequenceParams", sequenceParams)
        val raw = webApi.postWatchApi("reel/reel_watch_sequence", body, session)
            ?: throw java.io.IOException("Shorts sequence request failed")
        if (session != null &&
            LOGGED_IN_TRACKING_PARAM.find(raw)?.groupValues?.get(1) == "0") {
            throw java.io.IOException("YouTube rejected the Shorts session")
        }
        return parseShortsSequence(org.json.JSONObject(raw))
    }

    // ============================================================
    // Video library (www.youtube.com): user playlists, Watch Later,
    // Liked videos. Shapes verified against the live API July 2026.
    // ============================================================

    /**
     * The user's playlists from FEplaylist_aggregation (requires login).
     * Watch Later never appears here and Liked videos ("LL") sometimes does;
     * the Library UI pins both as fixed entries, so they are filtered out.
     */
    suspend fun getVideoPlaylists(): List<VideoPlaylist> = withContext(Dispatchers.IO) {
        // This one really is account-only: the browse below runs anonymously
        // now, and signed out it would spend a request to be handed a
        // signed-out shell with no playlists in it.
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val json = webApi.fetchYouTubeBrowse("FEplaylist_aggregation")
                .takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
            val root = org.json.JSONObject(json)
            val lockups = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "lockupViewModel", lockups)
            lockups.mapNotNull { parsePlaylistLockup(it) }
                .filter { it.playlistId != "LL" && it.playlistId != "WL" }
                .distinctBy { it.playlistId }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getVideoPlaylists failed", e)
            emptyList()
        }
    }

    /** Bounded preview for link resolution. Detail screens retain the page cursor. */
    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> =
        getPlaylistVideosPage(playlistId)?.videos.orEmpty()

    /**
     * Fetch exactly one playlist page. WEB and NewPipe keep separate cursors;
     * a failed continuation stays retryable and never switches source mid-list.
     * Renderer and continuation scopes verified August/September 2026.
     */
    internal suspend fun getPlaylistVideosPage(
        playlistId: String,
        continuation: VideoPlaylistCursor? = null,
        session: YouTubeSession? = sessionManager.captureSession()
    ): VideoPlaylistPage? = withContext(Dispatchers.IO) {
        try {
            if (continuation is VideoPlaylistCursor.NewPipe) {
                val page = continuation.extractor.getPage(continuation.page)
                return@withContext VideoPlaylistPage(page.items.toVideoItems(context),
                    page.nextPage?.takeIf { page.hasNextPage() }
                        ?.let { VideoPlaylistCursor.NewPipe(continuation.extractor, it) })
            }
            val body = org.json.JSONObject().put("context", webApi.webContext())
            if (continuation is VideoPlaylistCursor.Browse) body.put("continuation", continuation.token)
            else body.put("browseId", if (playlistId.startsWith("VL")) playlistId else "VL$playlistId")
            val raw = webApi.postWatchApi("browse", body, session)
            val root = raw?.takeIf { it.isNotBlank() }?.let { org.json.JSONObject(it) }
                ?.takeUnless { it.has("error") }
            if (root != null) {
                val videos = parseVideoPlaylistRows(root)
                val token = extractVideoPlaylistContinuationToken(root)
                if (videos.isNotEmpty() || token != null || continuation != null ||
                    playlistId.removePrefix("VL") in setOf("WL", "LL", "LM")) {
                    KLog.d("YouTubeRepo", "Playlist ${if (continuation == null) "first" else "next"} page: ${videos.size} videos")
                    return@withContext VideoPlaylistPage(videos, token?.let { VideoPlaylistCursor.Browse(it) })
                }
            }
            if (continuation != null || playlistId.removePrefix("VL") in setOf("WL", "LL", "LM")) {
                return@withContext null
            }
            val extractor = youtubeService.getPlaylistExtractor(
                "https://www.youtube.com/playlist?list=${playlistId.removePrefix("VL")}")
            extractor.fetchPage()
            val page = extractor.initialPage
            VideoPlaylistPage(page.items.toVideoItems(context),
                page.nextPage?.takeIf { page.hasNextPage() }
                    ?.let { VideoPlaylistCursor.NewPipe(extractor, it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching video playlist page", e)
            null
        }
    }

    /**
     * Resolve a playlist only if one of the two independent paths reaches its
     * real end. Used by whole-playlist download; returning null on an incomplete
     * chain prevents a button labelled "full playlist" from silently queuing
     * only the first exact page boundary.
     *
     * The WEB renderer differs per session: an authenticated response can carry
     * `playlistVideoRenderer`s, while an anonymous public playlist uses
     * `lockupViewModel`s. Verified August 2026: page one puts its playlist token
     * inside `itemSectionRenderer`; later pages arrive under
     * `appendContinuationItemsAction.continuationItems`.
     */
    suspend fun getCompletePlaylistVideos(playlistId: String): List<VideoItem>? =
        withContext(Dispatchers.IO) {
            val browseResult = getPlaylistVideosFromBrowse(playlistId)
            if (browseResult.complete && browseResult.videos.isNotEmpty()) {
                return@withContext browseResult.videos
            }

            val newPipeResult = getCompletePlaylistVideosFromNewPipe(playlistId)
            if (newPipeResult.complete && newPipeResult.videos.isNotEmpty()) {
                return@withContext newPipeResult.videos
            }

            val partialSize = maxOf(browseResult.videos.size, newPipeResult.videos.size)
            if (partialSize > 0) {
                KLog.w(
                    "YouTubeRepo",
                    "Refusing incomplete full-playlist load for $playlistId ($partialSize videos resolved)"
                )
            }
            null
        }

    private data class VideoPlaylistLoadResult(
        val videos: List<VideoItem>,
        val complete: Boolean
    )

    private fun getPlaylistVideosFromBrowse(playlistId: String): VideoPlaylistLoadResult {
        val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
        val videos = mutableListOf<VideoItem>()
        val seenTokens = mutableSetOf<String>()
        var json = webApi.fetchYouTubeBrowse(browseId)
        if (json.isEmpty()) return VideoPlaylistLoadResult(emptyList(), complete = false)

        return try {
            while (true) {
                val root = org.json.JSONObject(json)
                videos += parseVideoPlaylistRows(root)

                val token = extractVideoPlaylistContinuationToken(root) ?: break
                if (!seenTokens.add(token)) {
                    KLog.w("YouTubeRepo", "Repeated video playlist continuation for $playlistId")
                    return VideoPlaylistLoadResult(videos, complete = false)
                }
                json = webApi.fetchYouTubeBrowseContinuation(token)
                if (json.isEmpty()) {
                    KLog.w(
                        "YouTubeRepo",
                        "Video playlist continuation failed for $playlistId after ${videos.size} videos"
                    )
                    return VideoPlaylistLoadResult(videos, complete = false)
                }
            }
            VideoPlaylistLoadResult(videos, complete = true)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Video playlist browse failed for $playlistId", e)
            VideoPlaylistLoadResult(videos, complete = false)
        }
    }

    /**
     * A playlist's videos through NewPipe's playlist page, as the last resort
     * when the browse above parsed to nothing.
     *
     * **Not the primary signed-out path**: page-one browsing uses WEB lockups,
     * while this is the independent full-load fallback. NewPipe's playlist
     * extractor collects `playlistVideoRenderer`s, and a signed-out browse can
     * therefore come back with zero items and *no exception* when that shape
     * changes. It remains valuable here precisely because its page tokens are
     * independent from WEB's.
     *
     * Every NewPipe continuation is followed. If one fails, the rows already
     * resolved are returned as an explicitly incomplete result so the caller
     * can reject the batch instead of presenting a partial one as complete.
     */
    private suspend fun getCompletePlaylistVideosFromNewPipe(
        playlistId: String
    ): VideoPlaylistLoadResult =
        withContext(Dispatchers.IO) {
            val listId = playlistId.removePrefix("VL")
            // The account's own feeds have no public page to fetch: asking for
            // one anonymously is a guaranteed miss, so skip the request.
            if (listId == "WL" || listId == "LL" || listId == "LM") {
                return@withContext VideoPlaylistLoadResult(emptyList(), complete = false)
            }
            try {
                val extractor = youtubeService.getPlaylistExtractor(
                    "https://www.youtube.com/playlist?list=$listId"
                )
                extractor.fetchPage()
                val videos = mutableListOf<VideoItem>()
                var page = extractor.initialPage
                videos += page.items.toVideoItems(context)
                while (page.hasNextPage()) {
                    try {
                        page = extractor.getPage(page.nextPage)
                        videos += page.items.toVideoItems(context)
                    } catch (e: Exception) {
                        KLog.w(
                            "YouTubeRepo",
                            "Anonymous video playlist continuation failed for $listId after ${videos.size} videos",
                            e
                        )
                        return@withContext VideoPlaylistLoadResult(videos, complete = false)
                    }
                }
                VideoPlaylistLoadResult(videos, complete = true)
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Anonymous playlist fetch failed for $listId", e)
                VideoPlaylistLoadResult(emptyList(), complete = false)
            }
        }

    /**
     * What a playlist says it is - title, author, cover, length - without its
     * contents.
     *
     * Exists for links. Everywhere else a playlist arrives already described,
     * from a search result or a lockup or the account's own list, but a shared
     * URL is an id and nothing more, and a page opened on an id alone has an
     * empty header. [getPlaylistVideos] answers the other half of the same
     * response and throws this half away, so the two are deliberately separate
     * calls: the pages fetch their own items, and asking for both here would
     * fetch every item twice.
     *
     * Anonymous when there is no session - `fetchYouTubeBrowse` only signs when
     * one exists, and a public playlist answers without it (verified August
     * 2026) - which is what a shared link needs, since most arrive with the app
     * signed out.
     *
     * Null means "no page behind this id": a generated mix answers "This
     * playlist type is unviewable", and private or deleted lists answer with an
     * alert and no header at all. That is the caller's signal to fall back to
     * playing the id rather than to open a blank page.
     */
    suspend fun getPlaylistHeader(playlistId: String): PlaylistPageInfo? =
        withContext(Dispatchers.IO) {
            val listId = playlistId.removePrefix("VL").takeIf { it.isNotBlank() }
                ?: return@withContext null
            try {
                val json = webApi.fetchYouTubeBrowse("VL$listId").takeIf { it.isNotEmpty() }
                    ?: return@withContext null
                val root = org.json.JSONObject(json)
                parseModernPlaylistHeader(root, listId)
                    ?: parseLegacyPlaylistHeader(root, listId)
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "getPlaylistHeader failed for $playlistId", e)
                null
            }
        }

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

    /**
     * Fetch like count/status, subscription state and the comments entry token
     * for a video. likeStatus/isSubscribed are only meaningful when logged in.
     */
    suspend fun getVideoEngagement(videoId: String): VideoEngagement? = withContext(Dispatchers.IO) {
        try {
            val root = fetchWatchNextRoot(videoId) ?: return@withContext null
            parseEngagementFromWatchNext(videoId, root)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getVideoEngagement failed", e)
            null
        }
    }

    /**
     * Resolve the creator of a feed video without resolving streams or touching
     * either player. Used only when a feed lockup supplied an avatar/name but
     * omitted its channel browse endpoint.
     */
    suspend fun getVideoChannelId(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.length != 11) return@withContext null
        try {
            val root = fetchWatchNextRoot(videoId) ?: return@withContext null
            val engagement = runCatching { parseEngagementFromWatchNext(videoId, root) }.getOrNull()
            engagement?.channelId
                ?: parseVideoMetadataFromWatchNext(videoId, root, null)?.channelId
                // A collab upload names no owner at all - no title, no
                // thumbnail, no browse endpoint - so both of the above are null
                // and this used to answer "no such channel", which is what a
                // feed card whose lockup carried no creator command (a channel
                // tab's, for one) surfaced as a channel page that failed to
                // load. The collaborators are listed uploader first, so the
                // first of them is the channel the byline leads with.
                ?: engagement?.collaborators?.firstOrNull()?.channelId
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "Channel lookup failed for $videoId", e)
            null
        }
    }

    /**
     * Everything the video player needs from one watch-next call: engagement,
     * enriched metadata and related videos, all parsed from a single /next
     * response. Replaces the previous pair of an engagement call plus a full
     * NewPipe StreamExtractor fetch, halving the network work per video open
     * and freeing bandwidth for the player's initial buffer.
     */
    suspend fun getWatchNextData(
        videoId: String,
        baseVideo: VideoItem? = null
    ): WatchNextData = withContext(Dispatchers.IO) {
        try {
            val root = fetchWatchNextRoot(videoId)
                ?: return@withContext WatchNextData(null, null, emptyList())
            WatchNextData(
                engagement = try {
                    parseEngagementFromWatchNext(videoId, root)
                } catch (e: Exception) {
                    KLog.w("YouTubeRepo", "engagement parse failed for $videoId", e)
                    null
                },
                updatedVideoItem = parseVideoMetadataFromWatchNext(videoId, root, baseVideo),
                relatedVideos = parseRelatedFromWatchNext(root),
                chapters = try {
                    parseChaptersFromWatchNext(root)
                } catch (e: Exception) {
                    KLog.w("YouTubeRepo", "chapters parse failed for $videoId", e)
                    emptyList()
                },
                liveChatContinuation = parseLiveChatContinuation(root)
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getWatchNextData failed", e)
            WatchNextData(null, null, emptyList())
        }
    }

    private fun fetchWatchNextRoot(videoId: String): org.json.JSONObject? {
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("videoId", videoId)
        val raw = webApi.postWatchApi("next", body) ?: return null
        return org.json.JSONObject(raw)
    }

    // ============================================================
    // Live chat (InnerTube WEB client against www.youtube.com)
    // ============================================================

    /**
     * Open a live chat stream for [videoId], returning the first continuation
     * token and the send token.
     *
     * The entry point rides the same /next response the player already parses:
     * contents.twoColumnWatchNextResults.conversationBar.liveChatRenderer, with
     * the start token at continuations[0].reloadContinuationData.continuation.
     * conversationBar is absent entirely when the video is not live or the
     * creator disabled chat, which is the "no chat" signal - not an error.
     *
     * Reading chat needs no account: a signed-out poll returns the full
     * backlog. Sending does, so [LiveChatSession.sendParams] is only meaningful
     * alongside [isLoggedIn].
     *
     * Verified against the live /next API August 2026.
     */
    suspend fun getLiveChatSession(videoId: String): LiveChatSession? = withContext(Dispatchers.IO) {
        try {
            val root = fetchWatchNextRoot(videoId) ?: return@withContext null
            parseLiveChatContinuation(root)?.let { LiveChatSession(continuation = it) }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getLiveChatSession failed for $videoId", e)
            null
        }
    }

    /**
     * Poll one page of live chat.
     *
     * continuationContents.liveChatContinuation carries the new actions plus
     * the next token in continuations[0].invalidationContinuationData, whose
     * timeoutMs (10s on every stream sampled) is the interval the server wants
     * between polls. The first poll returns the whole visible backlog, roughly
     * 70 messages; subsequent polls return only what arrived since.
     *
     * Action shapes handled, in order of how often they actually appear:
     * addChatItemAction (text/paid/membership/gift/system items),
     * addBannerToLiveChatCommand (pinned message or chat summary) and
     * removeChatItemAction (a message deleted after it was already rendered).
     * These were verified against the live live_chat/get_live_chat API
     * August 2026.
     *
     * NOT yet probed against a live response, and so to be treated as
     * best-effort until they are: markChatItemAsDeletedAction,
     * markChatItemsByAuthorAsDeletedAction, replaceChatItemAction,
     * removeBannerForLiveChatCommand, liveChatPaidStickerRenderer and
     * liveChatRestrictedParticipationRenderer. Each one is an additive branch
     * that no-ops when the key is absent, so a wrong guess costs the feature
     * rather than the chat - but confirm the shapes before relying on them.
     */
    suspend fun pollLiveChat(continuation: String): LiveChatPage? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("continuation", continuation)
            val raw = webApi.postWatchApi("live_chat/get_live_chat", body) ?: return@withContext null
            val chat = org.json.JSONObject(raw)
                .optJSONObject("continuationContents")
                ?.optJSONObject("liveChatContinuation")
                ?: return@withContext null

            // Either invalidationContinuationData (the usual live tick) or
            // timedContinuationData / reloadContinuationData; all three hold the
            // next token and a timeout under different keys.
            val nextData = chat.optJSONArray("continuations")?.optJSONObject(0)?.let { c ->
                c.optJSONObject("invalidationContinuationData")
                    ?: c.optJSONObject("timedContinuationData")
                    ?: c.optJSONObject("reloadContinuationData")
            }

            val actions = chat.optJSONArray("actions")
            val messages = mutableListOf<LiveChatMessage>()
            val removed = mutableSetOf<String>()
            val removedAuthors = mutableSetOf<String>()
            val replacements = mutableMapOf<String, LiveChatMessage>()
            var banner: LiveChatBanner? = null
            var bannerCleared = false

            for (i in 0 until (actions?.length() ?: 0)) {
                val action = actions?.optJSONObject(i) ?: continue
                action.optJSONObject("addChatItemAction")?.optJSONObject("item")?.let { item ->
                    parseLiveChatItem(item, fallbackOrder = i)?.let(messages::add)
                }
                action.optJSONObject("removeChatItemAction")
                    ?.optString("targetItemId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(removed::add)
                // What a moderator delete actually emits. The renderer carries a
                // "deleted by" placeholder, but YouTube's own client collapses
                // the row away, so the message is simply dropped.
                action.optJSONObject("markChatItemAsDeletedAction")
                    ?.optString("targetItemId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(removed::add)
                // A ban: every message from that channel disappears at once.
                action.optJSONObject("markChatItemsByAuthorAsDeletedAction")
                    ?.optString("externalChannelId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(removedAuthors::add)
                action.optJSONObject("replaceChatItemAction")?.let { replace ->
                    val target = replace.optString("targetItemId").takeIf { it.isNotBlank() }
                    val item = replace.optJSONObject("replacementItem")
                    if (target != null && item != null) {
                        parseLiveChatItem(item, fallbackOrder = i)?.let { replacements[target] = it }
                    }
                }
                action.optJSONObject("addBannerToLiveChatCommand")
                    ?.optJSONObject("bannerRenderer")
                    ?.optJSONObject("liveChatBannerRenderer")
                    ?.let { parseLiveChatBanner(it) }
                    ?.let { banner = it }
                if (action.has("removeBannerForLiveChatCommand")) bannerCleared = true
            }

            val actionPanel = chat.optJSONObject("actionPanel")
            val inputRenderer = actionPanel?.optJSONObject("liveChatMessageInputRenderer")
            // When chat is subscribers-only, members-only, in slow mode, or the
            // viewer is banned, the input renderer is replaced wholesale by this
            // one carrying the reason.
            val restriction = actionPanel
                ?.optJSONObject("liveChatRestrictedParticipationRenderer")
                ?.let { getRunText(it.optJSONObject("message")) }
                ?.takeIf { it.isNotBlank() }

            LiveChatPage(
                messages = messages,
                removedIds = removed,
                removedAuthorIds = removedAuthors,
                replacements = replacements,
                banner = banner,
                bannerCleared = bannerCleared,
                restrictionMessage = restriction,
                nextContinuation = nextData?.optString("continuation")?.takeIf { it.isNotBlank() },
                timeoutMs = nextData?.optLong("timeoutMs")?.takeIf { it > 0L } ?: 10_000L,
                sendParams = inputRenderer
                    ?.optJSONObject("sendButton")
                    ?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("serviceEndpoint")
                    ?.optJSONObject("sendLiveChatMessageEndpoint")
                    ?.optString("params")
                    ?.takeIf { it.isNotBlank() },
                maxMessageLength = inputRenderer
                    ?.optJSONObject("inputField")
                    ?.optJSONObject("liveChatTextInputFieldRenderer")
                    ?.optInt("maxCharacterLimit")
                    ?.takeIf { it > 0 }
                    ?: 200,
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "pollLiveChat failed", e)
            null
        }
    }

    /**
     * Post a message to a live chat. Requires login - the input renderer is
     * present in the response even signed out, so its presence is not an auth
     * signal.
     *
     * [params] is the opaque token from the poll response's
     * actionPanel.liveChatMessageInputRenderer.sendButton, echoed back verbatim.
     *
     * An accepted message comes straight back as an addChatItemAction, so the
     * result carries the parsed item: showing it at once is what makes sending
     * feel instant instead of costing up to a full poll interval. Its id is the
     * one the poll will hand back later, so the normal dedupe absorbs it.
     */
    suspend fun sendLiveChatMessage(params: String, text: String): LiveChatSendResult =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) {
                return@withContext LiveChatSendResult(false, error = "Sign in to chat")
            }
            try {
                val body = org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("params", params)
                    .put(
                        "richMessage",
                        org.json.JSONObject().put(
                            "textSegments",
                            org.json.JSONArray().put(org.json.JSONObject().put("text", text))
                        )
                    )
                    .put("clientMessageId", java.util.UUID.randomUUID().toString())
                val raw = webApi.postWatchApi("live_chat/send_message", body)
                    ?: return@withContext LiveChatSendResult(false, error = "Message not sent")
                val root = org.json.JSONObject(raw)

                val results = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "addChatItemAction", results)
                val echo = results.firstNotNullOfOrNull { action ->
                    action.optJSONObject("item")?.let { parseLiveChatItem(it, fallbackOrder = 0) }
                }
                if (results.isNotEmpty()) {
                    return@withContext LiveChatSendResult(true, echo = echo)
                }

                // A rejected message (slow mode, a word filter, a ban) answers
                // 200 with an error string in place of the item.
                val errors = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "errorMessage", errors)
                val reason = errors.firstNotNullOfOrNull { getRunText(it) }
                    ?.takeIf { it.isNotBlank() }
                LiveChatSendResult(false, error = reason ?: "Message not sent")
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "sendLiveChatMessage failed", e)
                LiveChatSendResult(false, error = "Message not sent")
            }
        }

    /**
     * Concurrent viewers and the "Started streaming ..." line for a live video.
     *
     * The updated_metadata endpoint is what the web player polls to keep those
     * counters fresh without re-running /next. It asks for a 5s tick, which is
     * far more often than a phone needs - call it on the chat's 10s cadence or
     * slower.
     *
     * Verified against the live updated_metadata API August 2026.
     */
    suspend fun getLiveMetadata(videoId: String): LiveMetadata? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("videoId", videoId)
            val raw = webApi.postWatchApi("updated_metadata", body) ?: return@withContext null
            val root = org.json.JSONObject(raw)

            val viewCounts = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "videoViewCountRenderer", viewCounts)
            val viewCount = viewCounts.firstOrNull { it.optBoolean("isLive") } ?: viewCounts.firstOrNull()

            val dateTexts = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "updateDateTextAction", dateTexts)

            LiveMetadata(
                viewerCountText = getRunText(viewCount?.optJSONObject("viewCount"))
                    ?.takeIf { it.isNotBlank() },
                shortViewerCount = getRunText(viewCount?.optJSONObject("extraShortViewCount"))
                    ?.takeIf { it.isNotBlank() },
                dateText = getRunText(dateTexts.firstOrNull()?.optJSONObject("dateText"))
                    ?.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getLiveMetadata failed for $videoId", e)
            null
        }
    }

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

    /**
     * One community post, read from its own `FEpost_detail` page: what a
     * notification about a post points at.
     *
     * [verified October 2026, signed in] The page's `backstage-item-section`
     * holds exactly one `backstagePostRenderer`, the same renderer a channel's
     * Posts tab lists, so [parseBackstagePost] reads it unchanged. The params
     * asked for are kept as the post's [ChannelPost.detailParams] rather than
     * re-derived from the renderer: they are known to open this page, which is
     * what the comment thread is loaded from next.
     */
    suspend fun getPostDetail(detailParams: String): ChannelPost? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", POST_DETAIL_BROWSE_ID)
                    .put("params", detailParams)
            ) ?: return@withContext null
            val renderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(org.json.JSONObject(raw), "backstagePostRenderer", renderers)
            val renderer = renderers.firstOrNull() ?: return@withContext null
            parseBackstagePost(renderer)?.copy(
                detailParams = detailParams,
                channelId = renderer.optJSONObject("authorEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.startsWith("UC") }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getPostDetail failed", e)
            null
        }
    }

    /**
     * The continuation token for a community post's comments, from the post's
     * own `FEpost_detail` page ([ChannelPost.detailParams]). Null when the post
     * has comments turned off - the page then carries no comment section - or
     * when the request fails.
     *
     * Verified September 2026: the page holds a `backstage-item-section` (the
     * post) and a `comment-item-section` whose continuation opens the thread.
     */
    suspend fun getPostCommentsToken(detailParams: String): String? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", POST_DETAIL_BROWSE_ID)
                    .put("params", detailParams)
            ) ?: return@withContext null
            val sections = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(org.json.JSONObject(raw), "itemSectionRenderer", sections)
            val comments = sections.firstOrNull {
                it.optString("sectionIdentifier") == "comment-item-section"
            } ?: return@withContext null
            val tokens = mutableListOf<String>()
            findContinuationTokens(comments, tokens)
            tokens.firstOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getPostCommentsToken failed", e)
            null
        }
    }

    /**
     * Fetch one page of comments (top-level or replies) from a continuation token.
     * Parses the modern commentEntityPayload format (frameworkUpdates mutations).
     *
     * [viaBrowse] is for a community post's thread: its first page, next pages
     * and reply threads all answer on `/browse` in the same entity shape and
     * come back empty from `/next` [verified September 2026]. A video's thread
     * is the other way round.
     */
    suspend fun getCommentsPage(
        token: String,
        viaBrowse: Boolean = false
    ): CommentsPage? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("continuation", token)
            val raw = webApi.postWatchApi(if (viaBrowse) "browse" else "next", body)
                ?: return@withContext null
            val root = org.json.JSONObject(raw)

            // 1. Collect entity payloads: commentId -> payload, toolbar states and
            // toolbar surfaces (like/reply actions) by their entity keys
            val entities = mutableMapOf<String, org.json.JSONObject>()
            val toolbarStates = mutableMapOf<String, org.json.JSONObject>()
            val toolbarSurfaces = mutableMapOf<String, org.json.JSONObject>()
            val replyParamsList = mutableListOf<String>()
            val mutations = root.optJSONObject("frameworkUpdates")
                ?.optJSONObject("entityBatchUpdate")
                ?.optJSONArray("mutations")
            if (mutations != null) {
                for (i in 0 until mutations.length()) {
                    val payload = mutations.optJSONObject(i)?.optJSONObject("payload") ?: continue
                    payload.optJSONObject("commentEntityPayload")?.let { entity ->
                        val id = entity.optJSONObject("properties")?.optString("commentId")
                        if (!id.isNullOrBlank()) entities[id] = entity
                    }
                    payload.optJSONObject("engagementToolbarStateEntityPayload")?.let { state ->
                        val key = state.optString("key")
                        if (key.isNotBlank()) toolbarStates[key] = state
                    }
                    // Reply-box params and like/unlike actions live in the toolbar
                    // surface entity, one per comment (matched via toolbarSurfaceKey)
                    payload.optJSONObject("engagementToolbarSurfaceEntityPayload")?.let { surface ->
                        val key = surface.optString("key")
                        if (key.isNotBlank()) toolbarSurfaces[key] = surface
                        val replyEndpoints = mutableListOf<org.json.JSONObject>()
                        findObjectsByKey(surface, "createCommentReplyEndpoint", replyEndpoints)
                        replyEndpoints.firstOrNull()?.optString("createReplyParams")
                            ?.takeIf { it.isNotBlank() }?.let { replyParamsList.add(it) }
                    }
                }
            }
            // Match reply params to their comment: the decoded protobuf embeds the commentId
            val replyParamsByCommentId = mutableMapOf<String, String>()
            for (params in replyParamsList) {
                val decoded = decodeInnerTubeParams(params) ?: continue
                entities.keys.firstOrNull { decoded.contains(it) }?.let { id ->
                    replyParamsByCommentId[id] = params
                }
            }

            // Params for posting a new top-level comment (present on first pages only)
            val createEndpoints = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "createCommentEndpoint", createEndpoints)
            val createCommentParams = createEndpoints.firstOrNull()
                ?.optString("createCommentParams")?.takeIf { it.isNotBlank() }

            // 2. Walk continuationItems in order to keep YouTube's comment ordering
            val comments = mutableListOf<CommentItem>()
            var nextToken: String? = null
            val endpoints = root.optJSONArray("onResponseReceivedEndpoints") ?: org.json.JSONArray()
            for (i in 0 until endpoints.length()) {
                val ep = endpoints.optJSONObject(i) ?: continue
                val items = (ep.optJSONObject("reloadContinuationItemsCommand")
                    ?: ep.optJSONObject("appendContinuationItemsAction"))
                    ?.optJSONArray("continuationItems") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val thread = item.optJSONObject("commentThreadRenderer")
                    // Top-level pages wrap comments in commentThreadRenderer;
                    // reply pages carry bare commentViewModel items.
                    val viewModel = (thread ?: item).optJSONObject("commentViewModel")
                        ?.let { vm -> vm.optJSONObject("commentViewModel") ?: vm }
                    if (viewModel != null) {
                        val id = viewModel.optString("commentId")
                        val entity = entities[id] ?: continue
                        var repliesToken: String? = null
                        thread?.optJSONObject("replies")?.let { replies ->
                            val tokens = mutableListOf<String>()
                            findContinuationTokens(replies, tokens)
                            repliesToken = tokens.firstOrNull()
                        }
                        comments.add(
                            parseCommentEntity(
                                id, entity, viewModel, toolbarStates, repliesToken,
                                replyParamsByCommentId[id], toolbarSurfaces
                            )
                        )
                    } else if (item.has("continuationItemRenderer")) {
                        val tokens = mutableListOf<String>()
                        findContinuationTokens(item.getJSONObject("continuationItemRenderer"), tokens)
                        if (nextToken == null) nextToken = tokens.firstOrNull()
                    }
                }
            }

            CommentsPage(comments, nextToken, createCommentParams)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getCommentsPage failed", e)
            null
        }
    }

    /**
     * Rate a video: LIKE, DISLIKE, or INDIFFERENT (removes existing rating).
     * Requires login. Returns true on success.
     */
    suspend fun rateVideo(videoId: String, status: LikeStatus): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("target", org.json.JSONObject().put("videoId", videoId))
        webApi.postWatchApi(endpoint, body) != null
    }

    /**
     * Subscribe to / unsubscribe from a channel. Requires login and a
     * canonical UC... channelId (from getVideoEngagement).
     */
    suspend fun setSubscribed(channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("channelIds", org.json.JSONArray().put(channelId))
        webApi.postWatchApi(endpoint, body) != null
    }

    /**
     * Move [bell]'s channel to [level] on the account, with the params YouTube
     * served for that level.
     *
     * [verified September 2026, signed in] `notification/modify_channel_preference`
     * answers with the channel's fresh bell (`newNotificationButton`, the toggle
     * shape) and a toast (`notificationActionRenderer.responseText`, "You'll get
     * personalized notifications"). Success is that bell standing at [level], not
     * the HTTP code: account writes on this API answer 200 to requests they did
     * not act on. Null means the level did not change; the caller keeps what it
     * had.
     */
    suspend fun setChannelBell(bell: ChannelBell, level: BellLevel): ChannelBellChange? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val params = bell.choices[level] ?: return@withContext null
            try {
                val raw = webApi.postWatchApi(
                    "notification/modify_channel_preference",
                    org.json.JSONObject().put("context", webApi.webContext()).put("params", params)
                ) ?: return@withContext null
                val root = org.json.JSONObject(raw)
                val updated = ChannelBellParser.fromToggle(
                    root.optJSONObject("newNotificationButton"), bell.channelId
                )
                if (updated?.level != level) {
                    KLog.w("YouTubeRepo", "Bell for ${bell.channelId} did not move to $level (reply: ${updated?.level})")
                    return@withContext null
                }
                val toasts = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "notificationActionRenderer", toasts)
                ChannelBellChange(
                    // The level is the reply's; the params stay the ones the
                    // calling surface was served, which were minted for it.
                    bell = bell.withLevel(level),
                    message = getRunText(toasts.firstOrNull()?.optJSONObject("responseText"))
                        ?.takeIf { it.isNotBlank() }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.w("YouTubeRepo", "setChannelBell failed", e)
                null
            }
        }

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

    private fun postPlaylistApi(music: Boolean, endpoint: String, body: org.json.JSONObject): String? =
        if (music) musicApi.postMusicApi(endpoint, body) else webApi.postWatchApi(endpoint, body)

    private fun playlistContext(music: Boolean): org.json.JSONObject =
        if (music) musicApi.musicContext() else webApi.webContext()

    /** Playlist ids sometimes carry the VL browse prefix — edit calls need it stripped. */
    private fun normalizePlaylistId(playlistId: String): String = playlistId.removePrefix("VL")

    /** edit_playlist responses report success in a top-level status field. */
    private fun editStatusOk(raw: String?): Boolean {
        if (raw == null) return false
        return try {
            org.json.JSONObject(raw).optString("status") == "STATUS_SUCCEEDED"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create a playlist, optionally with initial videos. Returns the new
     * playlist id or null. Requires login.
     */
    suspend fun createYouTubePlaylist(
        title: String,
        music: Boolean,
        videoIds: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext null
        val body = org.json.JSONObject()
            .put("context", playlistContext(music))
            .put("title", title)
        if (videoIds.isNotEmpty()) {
            body.put("videoIds", org.json.JSONArray(videoIds))
        }
        val raw = postPlaylistApi(music, "playlist/create", body) ?: return@withContext null
        try {
            org.json.JSONObject(raw).optString("playlistId").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /** How an upload went: [uploaded] of [total] songs reached [playlistId]. */
    data class PlaylistUpload(val playlistId: String, val uploaded: Int, val total: Int)

    /**
     * Copy a playlist onto the YouTube Music account as a new private playlist.
     *
     * Batched so a long playlist costs a handful of writes, not one per song:
     * `playlist/create` carries the first [UPLOAD_CREATE_BATCH] ids and each
     * `edit_playlist` carries [UPLOAD_ADD_BATCH] add actions [verified September
     * 2026: a 100-id create and a 50-action edit both landed every row]. A
     * failed batch stops the upload and reports how far it got, rather than
     * retrying writes against an account. Null when nothing was created.
     */
    suspend fun uploadPlaylist(title: String, description: String?, videoIds: List<String>): PlaylistUpload? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn() || videoIds.isEmpty()) return@withContext null
            val first = videoIds.take(UPLOAD_CREATE_BATCH)
            val createBody = org.json.JSONObject()
                .put("context", musicApi.musicContext())
                .put("title", title)
                .put("privacyStatus", "PRIVATE")
                .put("videoIds", org.json.JSONArray(first))
            val playlistId = musicApi.postMusicApi("playlist/create", createBody)
                ?.let { runCatching { org.json.JSONObject(it).optString("playlistId") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            var uploaded = first.size
            for (batch in videoIds.drop(UPLOAD_CREATE_BATCH).chunked(UPLOAD_ADD_BATCH)) {
                kotlinx.coroutines.delay(UPLOAD_BATCH_PAUSE_MS)
                val actions = org.json.JSONArray()
                batch.forEach {
                    actions.put(org.json.JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", it))
                }
                val body = org.json.JSONObject()
                    .put("context", musicApi.musicContext())
                    .put("playlistId", playlistId)
                    .put("actions", actions)
                if (!editStatusOk(musicApi.postMusicApi("browse/edit_playlist", body))) break
                uploaded += batch.size
            }
            if (!description.isNullOrBlank()) {
                renameYouTubePlaylist(playlistId, title, music = true, description = description)
            }
            PlaylistUpload(playlistId, uploaded, videoIds.size)
        }

    /**
     * Delete a playlist. playlist/delete only works on playlists the user
     * owns; for saved (someone else's) playlists it fails, so fall back to
     * removing the playlist from the library instead. Requires login.
     */
    /** Add or remove someone else's playlist from the account's YouTube Music library. */
    suspend fun setPlaylistInLibrary(playlistId: String, saved: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext false
            val body = org.json.JSONObject()
                .put("context", playlistContext(true))
                .put("target", org.json.JSONObject().put("playlistId", normalizePlaylistId(playlistId)))
            postPlaylistApi(true, if (saved) "like/like" else "like/removelike", body) != null
        }

    suspend fun deleteYouTubePlaylist(playlistId: String, music: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext false
            val id = normalizePlaylistId(playlistId)
            val body = org.json.JSONObject()
                .put("context", playlistContext(music))
                .put("playlistId", id)
            if (postPlaylistApi(music, "playlist/delete", body) != null) return@withContext true
            val unlikeBody = org.json.JSONObject()
                .put("context", playlistContext(music))
                .put("target", org.json.JSONObject().put("playlistId", id))
            postPlaylistApi(music, "like/removelike", unlikeBody) != null
        }

    /**
     * Rename a playlist (and optionally replace its description). Only works
     * on playlists the user owns. Requires login.
     */
    suspend fun renameYouTubePlaylist(
        playlistId: String,
        title: String,
        music: Boolean,
        description: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val actions = org.json.JSONArray().put(
            org.json.JSONObject()
                .put("action", "ACTION_SET_PLAYLIST_NAME")
                .put("playlistName", title)
        )
        if (description != null) {
            actions.put(
                org.json.JSONObject()
                    .put("action", "ACTION_SET_PLAYLIST_DESCRIPTION")
                    .put("playlistDescription", description)
            )
        }
        val body = org.json.JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", normalizePlaylistId(playlistId))
            .put("actions", actions)
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * Add a video/song to a playlist ("WL" adds to Watch Later). Requires login.
     */
    suspend fun addToYouTubePlaylist(
        playlistId: String,
        videoId: String,
        music: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val body = org.json.JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", normalizePlaylistId(playlistId))
            .put(
                "actions",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("action", "ACTION_ADD_VIDEO")
                        .put("addedVideoId", videoId)
                )
            )
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * Remove a video/song from a playlist. Works for Watch Later ("WL");
     * the liked lists ("LL" videos, "LM" music) are not editable playlists —
     * removing from them means removing the like. Requires login.
     */
    suspend fun removeFromYouTubePlaylist(
        playlistId: String,
        videoId: String,
        music: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val id = normalizePlaylistId(playlistId)
        if (id == "LL" || id == "LM") {
            val body = org.json.JSONObject()
                .put("context", playlistContext(music))
                .put("target", org.json.JSONObject().put("videoId", videoId))
            return@withContext postPlaylistApi(music, "like/removelike", body) != null
        }
        val body = org.json.JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", id)
            .put(
                "actions",
                org.json.JSONArray().put(
                    org.json.JSONObject()
                        .put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                        .put("removedVideoId", videoId)
                )
            )
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * The account playlists holding [videoId], from one www
     * `playlist/get_add_to_playlist` (only www reports membership). Eventually
     * consistent - see [PlaylistMembership]. Null signed out or on failure.
     */
    suspend fun getPlaylistsContaining(videoId: String): Set<String>? = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext null
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("videoIds", org.json.JSONArray().put(videoId))
            .put("excludeWatchLater", false)
        webApi.postWatchApi("playlist/get_add_to_playlist", body)?.let(::parsePlaylistsContaining)
    }

    /**
     * Fetch the per-row playlist item ids ("setVideoId") for a playlist the
     * user can edit. Reordering via edit_playlist identifies rows by these,
     * not by videoId. Values stay occurrence-ordered because duplicate videos
     * are separate rows with separate setVideoIds. Browses VL<id> on music.youtube.com and reads
     * musicResponsiveListItemRenderer.playlistItemData across every playlist
     * continuation. An incomplete map cannot safely address duplicate rows,
     * so a failed/repeated continuation returns no map. Verified September 2026.
     */
    suspend fun getPlaylistSetVideoIds(playlistId: String): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext emptyMap()
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            var raw = musicApi.browseMusic(browseId) ?: return@withContext emptyMap()
            try {
                val idsByVideo = linkedMapOf<String, MutableList<String>>()
                val seenTokens = mutableSetOf<String>()
                while (true) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (raw.isBlank()) return@withContext emptyMap()
                    val root = org.json.JSONObject(raw)
                    if (root.has("error")) return@withContext emptyMap()
                    val rows = mutableListOf<org.json.JSONObject>()
                    findObjectsByKey(root, "musicResponsiveListItemRenderer", rows)
                    for (row in rows) {
                        val itemData = row.optJSONObject("playlistItemData") ?: continue
                        val videoId = itemData.optString("videoId").takeIf { it.isNotBlank() } ?: continue
                        val setVideoId = itemData.optString("playlistSetVideoId")
                            .takeIf { it.isNotBlank() } ?: continue
                        idsByVideo.getOrPut(videoId) { mutableListOf() }.add(setVideoId)
                    }
                    val token = extractPlaylistContinuationToken(raw) ?: break
                    if (!seenTokens.add(token)) {
                        KLog.w("YouTubeRepo", "Repeated playlist row-id continuation for $playlistId")
                        return@withContext emptyMap()
                    }
                    raw = musicApi.fetchContinuation(token)
                }
                idsByVideo.mapValues { (_, ids) -> ids.toList() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "getPlaylistSetVideoIds failed", e)
                emptyMap()
            }
        }

    /**
     * Move a playlist row before another row (or to the end when
     * successorSetVideoId is null). Rows are addressed by their setVideoId
     * from getPlaylistSetVideoIds. The anchor field is
     * "movedSetVideoIdSuccessor" — the "movedSetVideoId" name some client
     * libraries document is silently ignored and drops the row to the end
     * with STATUS_SUCCEEDED. Requires login. Verified July 2026.
     */
    suspend fun moveInYouTubePlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
        music: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val action = org.json.JSONObject()
            .put("action", "ACTION_MOVE_VIDEO_BEFORE")
            .put("setVideoId", setVideoId)
        if (successorSetVideoId != null) {
            action.put("movedSetVideoIdSuccessor", successorSetVideoId)
        }
        val body = org.json.JSONObject()
            .put("context", playlistContext(music))
            .put("playlistId", normalizePlaylistId(playlistId))
            .put("actions", org.json.JSONArray().put(action))
        editStatusOk(postPlaylistApi(music, "browse/edit_playlist", body))
    }

    /**
     * Post a new top-level comment. createCommentParams comes from the first
     * comments page (CommentsPage.createCommentParams). Returns the created
     * comment parsed from the response, or null on failure. Requires login.
     */
    suspend fun createComment(createCommentParams: String, text: String): CommentItem? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("commentText", text)
                .put("createCommentParams", createCommentParams)
            parseCreatedComment(webApi.postWatchApi("comment/create_comment", body))
        }

    /**
     * Post a reply to a comment. createReplyParams comes from the parent
     * comment (CommentItem.replyParams). Requires login.
     */
    suspend fun createCommentReply(createReplyParams: String, text: String): CommentItem? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("commentText", text)
                .put("createReplyParams", createReplyParams)
            parseCreatedComment(webApi.postWatchApi("comment/create_comment_reply", body))
        }

    /**
     * Execute a comment toolbar action (like/unlike). The action param comes
     * from the comment's toolbar surface (CommentItem.likeParams /
     * unlikeParams, present only on signed-in fetches). Requires login.
     */
    suspend fun performCommentAction(action: String): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val body = org.json.JSONObject()
            .put("context", webApi.webContext())
            .put("actions", org.json.JSONArray().put(action))
        val raw = webApi.postWatchApi("comment/perform_comment_action", body)
            ?: return@withContext false
        try {
            val results = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(org.json.JSONObject(raw), "actionResult", results)
            results.isEmpty() || results.any { it.optString("status") == "STATUS_SUCCEEDED" }
        } catch (e: Exception) {
            true
        }
    }

    internal suspend fun beginVideoHistorySession(
        videoId: String,
        positionMs: Long,
    ): VideoHistorySession? = withContext(Dispatchers.IO) {
        val session = sessionManager.captureSession() ?: return@withContext null
        if (!mayWriteVideoHistory()) return@withContext null
        try {
            val cpn = generateCpn()
            // signatureTimestamp is required: without one, WEB /player answers
            // every video "Video unavailable" with no playbackTracking
            // (verified October 2026; see PlayerSignatureTimestamp).
            fun postPlayer(signatureTimestamp: Int): String? =
                // postWatchApi has already logged the HTTP failure or the changed login.
                webApi.postWatchApi("player", org.json.JSONObject()
                    .put("context", webApi.webContext()).put("videoId", videoId).put("cpn", cpn)
                    .put("playbackContext", org.json.JSONObject().put("contentPlaybackContext",
                        org.json.JSONObject().put("signatureTimestamp", signatureTimestamp))), session)
                    ?: run {
                        KLog.w("YouTubeRepo", "Video history: no /player response for $videoId")
                        null
                    }
            val sentTimestamp = playerSignatureTimestamp.current()
            var raw = postPlayer(sentTimestamp) ?: return@withContext null
            if (historyPlayerSignedOut(raw, session = null, "Video")) return@withContext null
            var json = org.json.JSONObject(raw)
            if (json.optJSONObject("playbackTracking") == null) {
                // A signatureTimestamp YouTube no longer accepts looks exactly
                // like this; retry once if a fresh read gives another value.
                val retry = playerSignatureTimestamp.refreshAfterRejection(sentTimestamp)
                if (retry != null) {
                    KLog.w("YouTubeRepo", "Video history: no playbackTracking with sts $sentTimestamp, retrying with $retry")
                    raw = postPlayer(retry) ?: return@withContext null
                    if (historyPlayerSignedOut(raw, session = null, "Video")) return@withContext null
                    json = org.json.JSONObject(raw)
                }
            }
            val tracking = json.optJSONObject("playbackTracking")
            val playback = tracking?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")
            val watchtime = tracking?.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")
            if (playback.isNullOrBlank() || watchtime.isNullOrBlank()) {
                val status = json.optJSONObject("playabilityStatus")
                KLog.w(
                    "YouTubeRepo",
                    "Video history: no tracking URLs for $videoId " +
                        "(playback=${!playback.isNullOrBlank()} watchtime=${!watchtime.isNullOrBlank()} " +
                        "status=${status?.optString("status")} reason=${status?.optString("reason")})"
                )
                return@withContext null
            }
            val history = VideoHistorySession(videoId, session, cpn, playback, watchtime)
            val first = sendVideoHistoryPing(history, playback, positionMs, positionMs, false)
            if (first != HistoryPingResult.SENT) {
                KLog.w("YouTubeRepo", "Video history: first ping for $videoId ended $first")
                return@withContext null
            }
            history
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "Could not start video history reporting", e)
            null
        }
    }

    /** WEB watchtime and FEhistory readback verified September 2026 (60s of a 634s VOD -> 10%). */
    internal suspend fun reportVideoWatchProgress(
        session: VideoHistorySession,
        startMs: Long,
        positionMs: Long,
        final: Boolean,
    ): HistoryPingResult = withContext(Dispatchers.IO) {
        try {
            sendVideoHistoryPing(session, session.watchtimeUrl, startMs, positionMs, final)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "Could not report video watch progress", e)
            HistoryPingResult.FAILED
        }
    }

    /**
     * True, with a log line saying so, when YouTube answered a history /player
     * call as signed out.
     *
     * A session YouTube no longer accepts still gets `status: OK`, full
     * tracking URLs and a 204 on every ping - and the play is recorded nowhere.
     * `logged_in` in the responseContext is the only thing that tells the two
     * apart, so the pings are skipped rather than reported as a sync that did
     * nothing. Probed September 2026 against FEhistory and FEmusic_history.
     *
     * [session] is noted for the expired badge where the caller's request did
     * not already do it (postWatchApi notes its own responses).
     */
    private fun historyPlayerSignedOut(raw: String, session: YouTubeSession?, surface: String): Boolean {
        if (session != null) http.noteSessionState(raw, session)
        if (LOGGED_IN_TRACKING_PARAM.find(raw)?.groupValues?.get(1) != "0") return false
        KLog.w("YouTubeRepo", "$surface history: YouTube treated the session as signed out, nothing recorded")
        return true
    }

    /** The switches, as opposed to the session. Both have to hold at ping time. */
    private fun mayWriteVideoHistory(): Boolean =
        !IncognitoMode.isEnabled(context) && videoHistoryPreferences.isSaveVideoHistoryEnabled()

    private fun sendVideoHistoryPing(
        session: VideoHistorySession,
        baseUrl: String,
        startMs: Long,
        positionMs: Long,
        final: Boolean,
    ): HistoryPingResult {
        if (!mayWriteVideoHistory()) {
            KLog.d("YouTubeRepo", "Video history: not sent for ${session.videoId}, history off or incognito")
            return HistoryPingResult.FAILED
        }
        // Deliberately not a comparison of cookie strings. Google rotates the
        // session cookies mid-video, and a string compare read every rotation
        // as a different login and silently stopped reporting for the rest of
        // the video on a perfectly valid account. This asks the question that
        // was meant - same profile, still active, still the same login - and
        // hands back the refreshed cookies to sign this ping with.
        val live = sessionManager.currentSession(session.login) ?: run {
            KLog.w("YouTubeRepo", "Video history: login changed, reporting stops for ${session.videoId}")
            return HistoryPingResult.SESSION_ENDED
        }
        val url = baseUrl.toHttpUrlOrNull() ?: return HistoryPingResult.FAILED
        // Credentials only go to the YouTube tracking hosts returned by /player.
        if (url.scheme != "https" || url.host !in setOf("s.youtube.com", "www.youtube.com")) {
            KLog.w("YouTubeRepo", "Video history: refused a tracking URL on ${url.host}")
            return HistoryPingResult.FAILED
        }
        val position = (positionMs.coerceAtLeast(0L) / 1000.0).toString()
        val trackingUrl = url.newBuilder()
            .setQueryParameter("cpn", session.cpn)
            .setQueryParameter("ver", "2").setQueryParameter("c", "WEB")
            .setQueryParameter("cver", WEB_VERSION)
            .setQueryParameter("cmt", position)
            .setQueryParameter("st", (startMs.coerceIn(0L, positionMs.coerceAtLeast(0L)) / 1000.0).toString())
            .setQueryParameter("et", position)
            .setQueryParameter("state", if (final) "paused" else "playing")
            .setQueryParameter("final", if (final) "1" else "0")
            .build()
        val request = okhttp3.Request.Builder().url(trackingUrl)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .header("Referer", "https://www.youtube.com/watch?v=${session.videoId}")
            .authenticate(live, "https://www.youtube.com")
            .build()
        // "playback" is the one that files the video in history; "watchtime"
        // pings carry how far it was watched.
        val kind = url.pathSegments.lastOrNull() ?: url.encodedPath
        return http.okHttpClient.newCall(request).execute().use { response ->
            val detail = "$kind ping for ${session.videoId} at ${position}s " +
                "(from ${trackingUrl.queryParameter("st")}s, final=$final): HTTP ${response.code}"
            if (response.isSuccessful) {
                KLog.d("YouTubeRepo", "Video history: $detail")
            } else {
                KLog.w("YouTubeRepo", "Video history ping failed: $detail")
            }
            if (response.isSuccessful) HistoryPingResult.SENT else HistoryPingResult.FAILED
        }
    }

    /**
     * The user's subscriptions feed (FEsubscriptions): latest uploads from
     * all subscribed channels, newest first. Requires login.
     *
     * The first page is everything YouTube sent, not the first thirty: the
     * token it carries continues from the page's real end, so a truncated page
     * followed by its continuation would skip whatever was cut. Later pages come
     * from [getVideoFeedContinuation], whose `appendContinuationItemsAction`
     * shape this feed shares with Home. [verified October 2026, signed in: page
     * one about a hundred lockups, the continuation 95 more and a next token]
     */
    suspend fun getSubscriptionsFeedPage(): VideoFeedPage = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext VideoFeedPage(emptyList())
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("browseId", "FEsubscriptions")
            ) ?: return@withContext VideoFeedPage(emptyList())
            val root = org.json.JSONObject(raw)
            VideoFeedPage(
                videos = parseVideosFromYouTubeJson(raw, limit = Int.MAX_VALUE, parsedRoot = root),
                continuation = extractRichGridContinuation(root)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getSubscriptionsFeedPage failed", e)
            VideoFeedPage(emptyList())
        }
    }

    /**
     * A channel's newest community posts, for the posts the feeds scatter
     * between videos. One browse straight at the Posts tab, each post stamped
     * with the channel it was asked of. Empty for a channel with no Posts tab
     * and on any failure: a missing post is not worth an error anywhere.
     */
    suspend fun getChannelPosts(channelId: String): List<ChannelPost> = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channelId)
                    .put("params", CHANNEL_POSTS_TAB_PARAMS)
            ) ?: return@withContext emptyList()
            val postRenderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(org.json.JSONObject(raw), "backstagePostRenderer", postRenderers)
            postRenderers.mapNotNull { parseBackstagePost(it) }
                .distinctBy { it.postId }
                .map { it.copy(channelId = channelId) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "getChannelPosts failed for $channelId", e)
            emptyList()
        }
    }

    /**
     * All channels the user is subscribed to, from the FEchannels browse
     * feed (channelRenderer items), following continuations. Requires login.
     */
    suspend fun getSubscribedChannels(): List<SubscribedChannel> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val channels = mutableListOf<SubscribedChannel>()
            var response = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("browseId", "FEchannels")
            )
            var pages = 0
            while (response != null && pages < 10) {
                val root = org.json.JSONObject(response)
                val renderers = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "channelRenderer", renderers)
                for (renderer in renderers) {
                    val channelId = renderer.optString("channelId").takeIf { it.isNotBlank() } ?: continue
                    val name = renderer.optJSONObject("title")?.optString("simpleText")
                        ?.takeIf { it.isNotBlank() } ?: continue
                    val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    var avatarUrl = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                        ?.optString("url")?.takeIf { it.isNotBlank() }
                    if (avatarUrl?.startsWith("//") == true) avatarUrl = "https:$avatarUrl"
                    // InnerTube quirk: on FEchannels the subscriber count arrives in
                    // videoCountText; subscriberCountText carries the @handle.
                    // Verified again August 2026 - both fields are present on every
                    // renderer, so the handle is free here and account channels are
                    // searchable by it exactly like device-local ones.
                    val subscriberCount = getRunText(renderer.optJSONObject("videoCountText"))
                        ?.takeIf { it.isNotBlank() }
                    val handle = getRunText(renderer.optJSONObject("subscriberCountText"))
                        ?.takeIf { it.startsWith("@") }
                    // Every row carries its bell, so the list knows each level
                    // without a request per channel (ChannelBellParser).
                    val toggles = mutableListOf<org.json.JSONObject>()
                    findObjectsByKey(renderer, "subscriptionNotificationToggleButtonRenderer", toggles)
                    val bell = ChannelBellParser.fromToggle(toggles.firstOrNull(), channelId)
                    channels.add(
                        SubscribedChannel(channelId, name, avatarUrl, subscriberCount, handle, bell)
                    )
                }
                val token = if (renderers.isNotEmpty()) extractContinuationToken(response) else null
                response = token?.let {
                    webApi.postWatchApi(
                        "browse",
                        org.json.JSONObject().put("context", webApi.webContext()).put("continuation", it)
                    )
                }
                pages++
            }
            channels.distinctBy { it.channelId }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getSubscribedChannels failed", e)
            emptyList()
        }
    }

    /**
     * Latest uploads of a channel (Videos tab browse). Channel-page lockups
     * omit the channel row, so the caller's channel identity is stitched in.
     */
    suspend fun getChannelVideos(channel: SubscribedChannel): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channel.channelId)
                    .put("params", CHANNEL_VIDEOS_TAB_PARAMS)
            ) ?: return@withContext emptyList()
            val root = org.json.JSONObject(raw)
            val richItems = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "richItemRenderer", richItems)
            richItems.mapNotNull { item ->
                val content = item.optJSONObject("content") ?: return@mapNotNull null
                val parsed = parseLockupViewModel(content.optJSONObject("lockupViewModel"))
                    ?: parseVideoRenderer(content.optJSONObject("videoRenderer"))
                    ?: return@mapNotNull null
                withSubscribedChannelIdentity(parsed, channel)
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getChannelVideos failed", e)
            emptyList()
        }
    }

    /**
     * A channel-page card with the followed channel's identity put back.
     * Channel-page lockups omit the channel row, and the generic parser then
     * reads the first metadata row - "N views • date" - as the channel name.
     */
    private fun withSubscribedChannelIdentity(parsed: VideoItem, channel: SubscribedChannel): VideoItem {
        val viewCount = if (parsed.viewCount.isBlank() &&
            (parsed.channelName.contains("view", ignoreCase = true) ||
                parsed.channelName.contains("watching", ignoreCase = true))
        ) parsed.channelName else parsed.viewCount
        return parsed.copy(
            channelName = channel.name,
            channelId = channel.channelId,
            channelIconUrl = channel.avatarUrl ?: parsed.channelIconUrl,
            viewCount = viewCount
        )
    }

    /**
     * One followed channel's contribution to the shuffled Home feed: its latest
     * uploads and its all-time Popular order. Two requests - the Videos tab,
     * which also carries the sort chips, then the Popular continuation - and
     * nothing when the Popular order is not offered beyond the first.
     *
     * Only finished uploads are kept. A live stream or an upcoming premiere has
     * no duration on these cards, and a shuffled list of videos from any point
     * in a channel's history is not where either belongs.
     *
     * Throws [YouTubeRateLimitedException] rather than returning empty during a
     * hold, so the caller can stop the whole batch instead of recording every
     * channel as having nothing.
     */
    suspend fun getChannelMixPool(channel: SubscribedChannel): ChannelMixPool =
        withContext(Dispatchers.IO) {
            if (YouTubeRateLimit.isHeld()) {
                throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
            }
            val tab = getChannelTab(channel.channelId, CHANNEL_VIDEOS_TAB_PARAMS)
            val recent = tab.videos
                .map { withSubscribedChannelIdentity(it, channel) }
                .filter(::isFinishedUpload)
            val popularToken = ChannelSortChips.popularToken(tab.sortOptions)
            val catalogue = if (popularToken != null && !YouTubeRateLimit.isHeld()) {
                getChannelContinuation(popularToken).videos
                    .map { withSubscribedChannelIdentity(it, channel) }
                    .filter(::isFinishedUpload)
            } else emptyList()
            if (YouTubeRateLimit.isHeld()) {
                throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
            }
            ChannelMixPool(channelId = channel.channelId, recent = recent, catalogue = catalogue)
        }

    /**
     * A card for a finished upload: not live, and either carrying a duration or
     * an upload date. An upcoming premiere has neither, and the date half keeps
     * a card whose duration badge simply failed to parse.
     */
    private fun isFinishedUpload(video: VideoItem): Boolean =
        !video.isLive && (video.duration > 0 || !video.uploadedDate.isNullOrBlank())

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

    /**
     * Identity, tab list, and the contents of whichever tab YouTube had already
     * selected - all from one browse.
     *
     * The selected tab's items ride along rather than being fetched again,
     * because they arrived in this same response. Asking for them a second time
     * would be a request for bytes already in hand.
     *
     * Returns null only when the channel could not be identified at all, which
     * for the caller means "this is not a channel" rather than "try again".
     */
    suspend fun getChannelPage(channelId: String): ChannelPage? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("browseId", channelId)
            ) ?: return@withContext null
            val root = org.json.JSONObject(raw)
            val header = parseChannelHeader(root, channelId) ?: return@withContext null
            val tabs = parseChannelTabs(root)
            val selected = parseSelectedTab(root)
            val selectedKind = selected?.first ?: ChannelTabKind.HOME
            val content = selected?.second?.let { parseChannelTabPage(it, header) }
                ?: ChannelTabPage()
            ChannelPage(
                header = header,
                tabs = tabs,
                selectedTab = selectedKind,
                selectedContent = content
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getChannelPage failed for $channelId", e)
            null
        }
    }

    /** One tab's first page, by the `params` the page handed out for it. */
    suspend fun getChannelTab(
        channelId: String,
        params: String,
        header: ChannelHeader? = null
    ): ChannelTabPage = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channelId)
                    .put("params", params)
            ) ?: return@withContext ChannelTabPage()
            val root = org.json.JSONObject(raw)
            val scope = parseSelectedTab(root)?.second ?: root
            parseChannelTabPage(scope, header)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getChannelTab failed for $channelId", e)
            ChannelTabPage()
        }
    }

    /**
     * The next page of a tab, or the same tab re-sorted - the two are the same
     * call, because YouTube expresses both as a browse continuation. The
     * response differs only in whether it says append or reload, and since the
     * caller already knows which it asked for, that distinction stays with the
     * caller rather than being guessed here.
     */
    suspend fun getChannelContinuation(
        token: String,
        header: ChannelHeader? = null
    ): ChannelTabPage = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("continuation", token)
            ) ?: return@withContext ChannelTabPage()
            parseChannelTabPage(org.json.JSONObject(raw), header)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getChannelContinuation failed", e)
            ChannelTabPage()
        }
    }

    /**
     * Search a single channel's back catalogue.
     *
     * The one tab whose results come back as legacy `videoRenderer`s rather
     * than lockups (verified August 2026), which the generic page parser
     * already handles, so this is a browse with a query bolted on and nothing
     * more.
     */
    suspend fun searchWithinChannel(
        channelId: String,
        params: String,
        query: String,
        header: ChannelHeader? = null
    ): ChannelTabPage = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ChannelTabPage()
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webApi.webContext())
                    .put("browseId", channelId)
                    .put("params", params)
                    .put("query", query)
            ) ?: return@withContext ChannelTabPage()
            val root = org.json.JSONObject(raw)
            parseChannelTabPage(parseSelectedTab(root)?.second ?: root, header)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "searchWithinChannel failed for $channelId", e)
            ChannelTabPage()
        }
    }

    /**
     * The About panel, behind the token the header carried.
     *
     * YouTube does not put the full description, the links, the join date or
     * the lifetime view count in the channel response at all - they live in an
     * engagement panel fetched by continuation - so About costs one request and
     * only when someone opens it.
     */
    suspend fun getChannelAbout(token: String): ChannelAbout? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("continuation", token)
            ) ?: return@withContext null
            val root = org.json.JSONObject(raw)
            val about = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "aboutChannelViewModel", about)
            val view = about.firstOrNull() ?: return@withContext null

            val links = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(view, "channelExternalLinkViewModel", links)

            ChannelAbout(
                description = view.optString("description").takeIf { it.isNotBlank() },
                links = links.mapNotNull { parseChannelExternalLink(it) },
                joinedDateText = view.optJSONObject("joinedDateText")
                    ?.optString("content")?.takeIf { it.isNotBlank() },
                viewCountText = view.optString("viewCountText").takeIf { it.isNotBlank() },
                subscriberCountText = view.optString("subscriberCountText")
                    .takeIf { it.isNotBlank() },
                videoCountText = view.optString("videoCountText").takeIf { it.isNotBlank() },
                country = view.optString("country").takeIf { it.isNotBlank() },
                canonicalUrl = view.optString("canonicalChannelUrl").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getChannelAbout failed", e)
            null
        }
    }

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

    /**
     * Channel identity and display metadata, from one channel browse.
     *
     * Everything comes out of `metadata.channelMetadataRenderer`, which has
     * outlived several redesigns of the visible header (`c4TabbedHeaderRenderer`
     * is gone entirely as of 2026; the header is a `pageHeaderViewModel` now).
     * The subscriber count only exists in the header, so it is read from there
     * and is the one field allowed to come back null on a shape change.
     * Verified August 2026.
     */
    data class ChannelProfile(
        val channelId: String,
        val name: String,
        val avatarUrl: String?,
        val handle: String?,
        val subscriberCountText: String?
    )

    /**
     * Turns a handle, vanity URL or legacy user URL into a canonical UC id
     * via `navigation/resolve_url`. Works signed out. Verified August 2026.
     *
     * Import files are full of these - a Takeout CSV is all UC ids, but an
     * OPML from an RSS reader or a hand-written list is usually @handles, and
     * every other call in the app needs the UC id.
     */
    suspend fun resolveChannelId(urlOrHandle: String): String? = withContext(Dispatchers.IO) {
        val raw = urlOrHandle.trim()
        if (raw.isBlank()) return@withContext null
        if (raw.startsWith("UC") && raw.length >= 24) return@withContext raw
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("@") -> "https://www.youtube.com/$raw"
            else -> "https://www.youtube.com/${raw.trimStart('/')}"
        }
        try {
            val response = webApi.postWatchApi(
                "navigation/resolve_url",
                org.json.JSONObject().put("context", webApi.webContext()).put("url", url)
            ) ?: return@withContext null
            org.json.JSONObject(response)
                .optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.startsWith("UC") }
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "resolveChannelId failed for $url", e)
            null
        }
    }

    /**
     * Name, avatar and handle for a channel. Used to fill in imported entries,
     * which arrive carrying a name at best and never an avatar.
     */
    suspend fun getChannelProfile(channelId: String): ChannelProfile? = withContext(Dispatchers.IO) {
        try {
            val raw = webApi.postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webApi.webContext()).put("browseId", channelId)
            ) ?: return@withContext null
            val root = org.json.JSONObject(raw)
            val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
            val id = metadata?.optString("externalId")?.takeIf { it.isNotBlank() } ?: channelId
            val name = metadata?.optString("title")?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val thumbs = metadata.optJSONObject("avatar")?.optJSONArray("thumbnails")
            val avatarUrl = thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                ?.optString("url")?.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("//")) "https:$it" else it }
            val handle = metadata.optString("vanityChannelUrl")
                .substringAfterLast('/')
                .takeIf { it.startsWith("@") }

            // Subscriber count lives only in the visible header. The search is
            // scoped to the header subtree on purpose: a whole channel page
            // carries ~95 contentMetadataViewModels, all but one of them a
            // video card, so a document-wide key search would be a coin flip.
            // A miss here is not worth failing the whole profile over.
            val subscriberCountText = runCatching {
                val header = root.optJSONObject("header") ?: return@runCatching null
                val texts = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(header, "text", texts)
                texts.mapNotNull { it.optString("content").takeIf { c -> c.isNotBlank() } }
                    .firstOrNull { it.contains("subscriber", ignoreCase = true) }
            }.getOrNull()

            ChannelProfile(id, name, avatarUrl, handle, subscriberCountText)
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "getChannelProfile failed for $channelId", e)
            null
        }
    }

    /**
     * A channel's 15 most recent uploads from its public Atom feed.
     *
     * This is the cheap half of the local feed. The feed is ~50 KB against
     * roughly 1 MB for the equivalent channel browse, which is the difference
     * between a 200-channel refresh costing 10 MB and costing 200 MB, and it
     * needs no client version, no cookies and no visitorData - so it keeps
     * working when InnerTube shapes drift.
     *
     * It also carries a real ISO timestamp per entry, which the browse path
     * does not: InnerTube only ever says "3 days ago", and merging fifteen
     * channels on prose that coarse shuffles the top of the feed arbitrarily.
     *
     * What it does not carry is duration or live status, so cards from this
     * path show no duration badge. That is the documented trade the "Fast
     * refresh" setting makes.
     */
    suspend fun getChannelFeedRss(
        channelId: String,
        avatarUrl: String? = null
    ): List<VideoItem> =
        (fetchChannelFeedRss(channelId, avatarUrl) as? ChannelFeedResult.Items)?.videos
            ?: emptyList()

    /**
     * Whether this fetch may be answered from the HTTP cache.
     *
     * YouTube marks these feeds `public, max-age=900`, so with a cache in the
     * client a refresh inside fifteen minutes costs no request at all - which
     * is most of the point of having one. But it also means a pull-to-refresh
     * would silently do nothing for those fifteen minutes, and a refresh that
     * visibly does nothing is worse than the traffic it saves. So an explicit
     * refresh revalidates and everything else - a tab revisit, a process
     * restart, the six-hourly worker - takes the cached answer.
     */
    private fun feedCacheControl(forceFresh: Boolean): okhttp3.CacheControl? =
        if (forceFresh) okhttp3.CacheControl.Builder().noCache().build() else null

    /**
     * Outcome of one Atom feed fetch, kept richer than a list because
     * [getLocalSubscriptionsFeed]'s browse fallback is correct for one of these
     * failures and actively harmful for the other. See there.
     */
    sealed interface ChannelFeedResult {
        data class Items(val videos: List<VideoItem>) : ChannelFeedResult

        /**
         * The feed answered, but not with a feed - 404/410, or anything else
         * that is a verdict on this channel rather than on this device. The
         * browse fallback is exactly right here and is why it exists.
         */
        data object NoFeed : ChannelFeedResult

        /** HTTP 429. Escalating to a ~1 MB browse would make this worse. */
        data object RateLimited : ChannelFeedResult

        /** Network or parse failure - indistinguishable from a dead channel. */
        data object Failed : ChannelFeedResult
    }

    private suspend fun fetchChannelFeedRss(
        channelId: String,
        avatarUrl: String? = null,
        forceFresh: Boolean = false,
    ): ChannelFeedResult = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .apply { feedCacheControl(forceFresh)?.let { cacheControl(it) } }
                .build()
            val body = http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 429 is a verdict on this device, not on this channel, and
                    // it is the one code the browse fallback must not answer:
                    // escalating 200 channels from a 50 KB feed to a 1 MB browse
                    // aims 200 MB at the server that just asked us to stop.
                    if (YouTubeRateLimit.note(
                            response.code,
                            "channel feed",
                            response.header("Retry-After"),
                        )
                    ) {
                        return@withContext ChannelFeedResult.RateLimited
                    }
                    // 404 means the channel is gone or the id was never valid;
                    // the caller keeps the subscription either way, because a
                    // transient failure must not silently delete channels.
                    KLog.w("YouTubeRepo", "channel feed $channelId HTTP ${response.code}")
                    return@withContext ChannelFeedResult.NoFeed
                }
                response.body?.string()
            } ?: return@withContext ChannelFeedResult.NoFeed
            ChannelFeedResult.Items(parseChannelFeedXml(body, avatarUrl))
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "getChannelFeedRss failed for $channelId", e)
            ChannelFeedResult.Failed
        }
    }

    /**
     * Builds the device-local subscriptions feed: the latest uploads across
     * [channels], newest first.
     *
     * Channels are fetched concurrently but only [FEED_CONCURRENCY] at a time.
     * Unbounded parallelism here would fire one request per subscription at
     * once - a couple of hundred sockets on a pull-to-refresh, which mobile
     * radios handle badly and which reads to YouTube like a scrape.
     *
     * A channel that fails contributes nothing and does not fail the refresh:
     * one dead channel out of two hundred must not empty the feed.
     * [onProgress] reports completed channels so a long first refresh can show
     * real progress instead of an indeterminate spinner.
     */
    /**
     * A channel's uploads from the InnerTube channel browse, with the merge key
     * reconstructed.
     *
     * The browse path has durations and live badges, which RSS lacks, but only
     * prose dates ("3 days ago"), so [VideoItem.publishedAtMs] has to be
     * derived from those to sort alongside RSS items carrying real timestamps.
     */
    private suspend fun channelVideosWithTimestamps(channel: LocalSubscription): List<VideoItem> =
        getChannelVideos(channel.toSubscribedChannel()).map { video ->
            video.copy(
                publishedAtMs = video.publishedAtMs
                    ?: VideoItem.parseRelativeTime(video.uploadedDate)
            )
        }

    suspend fun getLocalSubscriptionsFeed(
        channels: List<LocalSubscription>,
        fastMode: Boolean = true,
        maxPerChannel: Int = MAX_FEED_ITEMS_PER_CHANNEL,
        maxTotal: Int = MAX_FEED_ITEMS,
        /** True when the user asked for this refresh, so it must revalidate. */
        forceFresh: Boolean = false,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyList()
        // Refuse before spending a single request. A refresh taken during a
        // hold is the exact loop that deepens one: blocked feed reads as empty,
        // user pulls to refresh, N more requests.
        if (YouTubeRateLimit.isHeld()) {
            throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
        }
        val gate = kotlinx.coroutines.sync.Semaphore(FEED_CONCURRENCY)
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val total = channels.size

        val perChannel = kotlinx.coroutines.coroutineScope {
            channels.map { channel ->
                async {
                    gate.acquire()
                    try {
                        // A sibling already hit the limit. The shared state is
                        // the coordination channel here rather than cancelling
                        // the scope, so the channels that already succeeded
                        // keep their results instead of being thrown away.
                        if (YouTubeRateLimit.isHeld()) return@async emptyList()
                        val videos = if (fastMode) {
                            // RSS is not universally available: some channels
                            // 404 on the feed URL YouTube itself advertises in
                            // their own channelMetadataRenderer.rssUrl, uploads
                            // and all (verified August 2026). Treating that as
                            // "no uploads" silently drops the channel from the
                            // feed, and for someone following only a handful it
                            // empties the tab and reads as a network failure.
                            // So fast mode means "RSS, else browse", not "RSS
                            // or nothing" - the fallback costs a request only
                            // for the channels that actually need it.
                            //
                            // The one refusal it must not answer is 429. That
                            // is a verdict on this device rather than on this
                            // channel, a browse will be refused too, and doing
                            // it for every channel turns a 10 MB refresh into a
                            // 200 MB one aimed at a server that just said stop.
                            when (val feed = fetchChannelFeedRss(
                                channel.channelId,
                                channel.avatarUrl,
                                forceFresh,
                            )) {
                                is ChannelFeedResult.Items ->
                                    feed.videos.ifEmpty { channelVideosWithTimestamps(channel) }
                                ChannelFeedResult.NoFeed,
                                ChannelFeedResult.Failed ->
                                    channelVideosWithTimestamps(channel)
                                ChannelFeedResult.RateLimited -> emptyList()
                            }
                        } else {
                            channelVideosWithTimestamps(channel)
                        }
                        videos.take(maxPerChannel)
                    } catch (e: Exception) {
                        KLog.w("YouTubeRepo", "feed fetch failed for ${channel.channelId}", e)
                        emptyList()
                    } finally {
                        gate.release()
                        onProgress?.invoke(completed.incrementAndGet(), total)
                    }
                }
            }.map { it.await() }
        }

        // A hold armed mid-refresh means the rest of the channels stood down,
        // so what came back is a partial feed. Reporting it as the feed would
        // present a throttled refresh as "these are your subscriptions", and
        // the empty case would read as "nothing new" - which is what sends
        // people to check a connection that is working fine.
        if (YouTubeRateLimit.isHeld()) {
            throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
        }

        perChannel.flatten()
            .distinctBy { it.videoId }
            // Items with no usable timestamp sink to the bottom rather than
            // floating to the top on a null-sorts-first comparator.
            .sortedByDescending { it.publishedAtMs ?: Long.MIN_VALUE }
            // Cap after the global sort, never per channel: trimming per
            // channel would hide a prolific channel's recent uploads while
            // keeping a dormant one's year-old video.
            .take(maxTotal)
    }

    /**
     * Turns parsed import entries into storable subscriptions, resolving the
     * ones that only carried a handle or vanity URL.
     *
     * Entries that already have a UC id cost nothing - the common case, since
     * both Takeout and every NewPipe-family export write canonical channel
     * URLs. Only the leftovers hit the network, [FEED_CONCURRENCY] at a time,
     * and an entry that cannot be resolved is dropped and counted rather than
     * stored as a broken id that would fail silently on every refresh.
     */
    suspend fun resolveImportedChannels(
        entries: List<ImportedChannel>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Pair<List<LocalSubscription>, Int> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<LocalSubscription>()
        val needsNetwork = mutableListOf<ImportedChannel>()

        for (entry in entries) {
            val id = entry.channelId
            if (id != null) {
                resolved.add(LocalSubscription(id, entry.name, entry.avatarUrl))
            } else if (entry.unresolvedPath != null) {
                needsNetwork.add(entry)
            }
        }

        if (needsNetwork.isEmpty()) {
            onProgress?.invoke(entries.size, entries.size)
            return@withContext resolved to 0
        }

        val gate = kotlinx.coroutines.sync.Semaphore(FEED_CONCURRENCY)
        val completed = java.util.concurrent.atomic.AtomicInteger(resolved.size)
        val total = entries.size
        onProgress?.invoke(completed.get(), total)

        val lookups = kotlinx.coroutines.coroutineScope {
            needsNetwork.map { entry ->
                async {
                    gate.acquire()
                    try {
                        resolveChannelId(entry.unresolvedPath!!)?.let { id ->
                            LocalSubscription(id, entry.name, entry.avatarUrl, handle = entry.unresolvedPath)
                        }
                    } finally {
                        gate.release()
                        onProgress?.invoke(completed.incrementAndGet(), total)
                    }
                }
            }.map { it.await() }
        }

        (resolved + lookups.filterNotNull()).distinctBy { it.channelId } to lookups.count { it == null }
    }

    /**
     * Fills in name and avatar for subscriptions that are missing them - the
     * normal state right after an import, where the file gave at most a name
     * and never a picture.
     *
     * Capped at [limit] channels per run because this is one browse per
     * channel, i.e. the expensive shape the local feed deliberately avoids.
     * It runs against whichever channels are actually on screen, so a
     * 300-channel library fills in over a few visits rather than in one
     * 300-request burst.
     */
    suspend fun fetchMissingChannelProfiles(
        channels: List<LocalSubscription>,
        limit: Int = PROFILE_BACKFILL_LIMIT
    ): List<LocalSubscription> = withContext(Dispatchers.IO) {
        val pending = channels.filter { it.avatarUrl.isNullOrBlank() }.take(limit)
        if (pending.isEmpty()) return@withContext emptyList()
        val gate = kotlinx.coroutines.sync.Semaphore(FEED_CONCURRENCY)
        kotlinx.coroutines.coroutineScope {
            pending.map { channel ->
                async {
                    gate.acquire()
                    try {
                        getChannelProfile(channel.channelId)?.let { profile ->
                            channel.copy(
                                name = profile.name,
                                avatarUrl = profile.avatarUrl,
                                handle = profile.handle ?: channel.handle
                            )
                        }
                    } catch (e: Exception) {
                        null
                    } finally {
                        gate.release()
                    }
                }
            }.map { it.await() }
        }.filterNotNull()
    }

    /**
     * Tell the signed-in account about a dismissal, so the choice also cleans
     * up recommendations on youtube.com and in the official apps.
     *
     * This is the *bonus* half of "don't recommend this", never the mechanism:
     * the local hide in [NotInterestedRepository] has already happened by the
     * time this runs, and a failure here must not undo it. YouTube's own
     * feedback is advisory and takes days to visibly change a feed, whereas
     * the local filter takes effect on the next frame - so this returning
     * false is not something the user should ever be told about.
     *
     * Signed out there is nothing to call: no response carries a token, so
     * [token] is null and this is skipped. Search results carry no tokens even
     * when signed in, which is consistent with search never being filtered.
     *
     * The same endpoint reverses a dismissal - pass the undo token. Success is
     * `feedbackResponses[0].isProcessed`, not the HTTP code: like
     * `subscription/subscribe`, this endpoint answers 200 to requests it did
     * not actually act on. Verified against the live endpoint, August 2026.
     */
    suspend fun sendDismissalFeedback(token: String?): Boolean = withContext(Dispatchers.IO) {
        if (token.isNullOrBlank()) return@withContext false
        if (!sessionManager.isLoggedIn()) return@withContext false
        try {
            val body = org.json.JSONObject()
                .put("context", webApi.webContext())
                .put("feedbackTokens", org.json.JSONArray().put(token))
                .put("isFeedbackTokenUnencrypted", false)
                .put("shouldMerge", false)
            val raw = webApi.postWatchApi("feedback", body) ?: return@withContext false
            val processed = org.json.JSONObject(raw)
                .optJSONArray("feedbackResponses")
                ?.optJSONObject(0)
                ?.optBoolean("isProcessed", false) ?: false
            if (!processed) {
                KLog.w("YouTubeRepo", "feedback token not processed")
            }
            processed
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "feedback failed", e)
            false
        }
    }

    suspend fun getNotifications(): List<NotificationItem> = youtubeAccount.getNotifications()
}
