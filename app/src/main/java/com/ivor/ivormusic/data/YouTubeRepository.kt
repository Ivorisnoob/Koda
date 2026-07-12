package com.ivor.ivormusic.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.Page
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody


import java.util.concurrent.TimeUnit

/**
 * Repository for fetching data from YouTube Music.
 * Uses NewPipeExtractor to avoid official API restrictions.
 */
class YouTubeRepository(private val context: Context) {

    private val sessionManager = SessionManager(context)
    private val videoHistoryRepository by lazy { VideoHistoryRepository(context) }

    companion object {
        private const val YT_MUSIC_BASE_URL = "https://music.youtube.com"
        private var isInitialized = false
        
        // Content filters for YouTube Music search
        const val FILTER_SONGS = "music_songs"
        const val FILTER_VIDEOS = "music_videos"
        const val FILTER_ALBUMS = "music_albums"
        const val FILTER_PLAYLISTS = "music_playlists"
        const val FILTER_ARTISTS = "music_artists"
        
        // Regular YouTube video filter
        const val FILTER_YOUTUBE_VIDEOS = "videos"
        
        /**
         * Public InnerTube API Key for WEB client.
         * 
         * NOTE TO REVIEWERS: This is NOT a secret/private API key. This is a publicly-known,
         * Google-generated API key that is embedded in YouTube's and YouTube Music's public
         * JavaScript source code. It is designed to be used by web clients and is the same
         * key used by all major open-source YouTube projects including:
         * - NewPipe/NewPipeExtractor
         * - yt-dlp
         * - ytmusicapi
         * - Invidious
         * - and many others
         * 
         * This key is rate-limited by Google on a per-IP basis, not per-key, and does not
         * grant access to any private user data. It simply identifies the client type (WEB)
         * for the InnerTube API. Moving it to BuildConfig or environment variables would
         * provide no security benefit as it is already public knowledge.
         * 
         * Reference: https://github.com/AyMaN-GhOsT/YouTube-Internal-Clients
         */
        /**
         * Global Browser User-Agent to be used across the app (NewPipe, CacheManager, internal API).
         * Must be consistent to avoid playback throttling and "Page needs to be reloaded" errors.
         * Using a modern Chrome UA is recommended.
         */
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val INNER_TUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

        // Current InnerTube client versions (kept in sync with yt-dlp upstream).
        // YouTube rejects clients older than a few months; bump these together when refreshing.
        private const val WEB_REMIX_VERSION = "1.20260114.03.00"
        private const val WEB_VERSION = "2.20260114.08.00"

        // browse params selecting a channel's Videos tab (protobuf: "videos")
        private const val CHANNEL_VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

        // ANDROID_VR is the recommended client for audio extraction in 2026:
        //  - Does NOT require a PO Token (unlike WEB / ANDROID / IOS / MWEB).
        //  - Returns direct, unobfuscated stream URLs (no signatureCipher to decrypt).
        private const val ANDROID_VR_VERSION = "1.65.10"
        private const val ANDROID_VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        private const val ANDROID_VR_CLIENT_ID = 28

        // IOS used as a secondary fallback when ANDROID_VR is rejected (rare).
        private const val IOS_VERSION = "21.02.3"
        const val IOS_USER_AGENT =
            "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X)"
        private const val IOS_CLIENT_ID = 5

        // Default UA used when a URL has no recognisable `?c=` client tag.
        // Most callers should use uaForPlaybackUri() instead, which picks the UA
        // matching the URL's issuing client so YouTube doesn't 403 on UA mismatch.
        const val PLAYBACK_USER_AGENT = IOS_USER_AGENT

        /**
         * Returns the User-Agent ExoPlayer must use when fetching a googlevideo
         * URL. YouTube binds resolved stream URLs to the client tagged in the
         * `?c=` query param and answers 403 if the playback request's UA doesn't
         * look like that client. Pick the UA per URL, not globally.
         */
        fun uaForPlaybackUri(uri: android.net.Uri): String {
            val c = try { uri.getQueryParameter("c") } catch (_: Exception) { null }
            return when (c?.uppercase()) {
                "IOS" -> IOS_USER_AGENT
                "ANDROID_VR" -> ANDROID_VR_USER_AGENT
                "ANDROID", "ANDROID_TESTSUITE", "ANDROID_MUSIC" ->
                    "com.google.android.youtube/19.09.37 (Linux; U; Android 14) gzip"
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER", "TVHTML5_SIMPLY_EMBEDDED", "TVHTML5" ->
                    TV_EMBED_USER_AGENT
                "WEB_EMBEDDED_PLAYER", "WEB", "WEB_REMIX" -> BROWSER_USER_AGENT
                else -> BROWSER_USER_AGENT
            }
        }

        // UA kept only for uaForPlaybackUri: previously-resolved TV-client URLs
        // may still live in the player queue / URI cache and need a matching UA.
        private const val TV_EMBED_USER_AGENT =
            "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15"

        // visitorData cache is companion-level: repositories are created per
        // ViewModel (no DI), so instance-level storage would make every VM pay
        // the youtube.com bootstrap download once. Shared storage means one
        // fetch warms it for the whole process.
        @Volatile private var cachedVisitorData: String? = null
        @Volatile private var visitorDataFetchedAt: Long = 0L
        private val visitorDataMutex = kotlinx.coroutines.sync.Mutex()
        private const val VISITOR_DATA_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Dedicated client for stream resolution. callTimeout is a hard wall-clock
    // cap enforced by OkHttp itself — unlike withTimeoutOrNull, it cannot be
    // defeated by a thread blocked inside execute().
    private val streamResolveClient = okHttpClient.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private fun getRandomUserAgent(): String {
        return BROWSER_USER_AGENT
    }

    init {
        initializeNewPipe()
    }

    private fun initializeNewPipe() {
        if (!isInitialized) {
            try {
                NewPipe.init(NewPipeDownloaderImpl(okHttpClient, sessionManager))
                isInitialized = true
            } catch (e: Exception) {
                // Already initialized
                isInitialized = true
            }
        }
    }

    // Cache extractors for pagination
    private val searchExtractorCache = mutableMapOf<String, org.schabi.newpipe.extractor.search.SearchExtractor>()

    /**
     * Search for songs on YouTube Music.
     * @param query The search query
     * @param filter The content filter (FILTER_SONGS, FILTER_ALBUMS, etc.)
     * @return List of songs matching the query
     */
    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext emptyList()
            // YouTube Music search often uses the search extractor with specific filters
            val searchExtractor = ytService.getSearchExtractor(query, listOf(filter), "")
            searchExtractor.fetchPage()
            
            // Cache for pagination
            searchExtractorCache[query] = searchExtractor
            
            searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item: StreamInfoItem ->
                try {
                    Song.fromYouTube(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "Unknown",
                        artist = item.uploaderName ?: "Unknown Artist",
                        album = "",
                        duration = item.duration * 1000L,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for playlists on YouTube Music.
     */
    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext emptyList()
            val searchExtractor = ytService.getSearchExtractor(query, listOf(FILTER_PLAYLISTS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                PlaylistDisplayItem(
                    name = item.name ?: "Unknown Playlist",
                    url = item.url,
                    uploaderName = item.uploaderName ?: "Unknown",
                    itemCount = item.streamCount.toInt(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for albums on YouTube Music.
     * Note: Albums are often returned as PlaylistInfoItem in NewPipe for YouTube Music.
     */
    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext emptyList()
            val searchExtractor = ytService.getSearchExtractor(query, listOf(FILTER_ALBUMS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { item ->
                PlaylistDisplayItem(
                    name = item.name ?: "Unknown Album",
                    url = item.url, // Album URL usually works like a playlist
                    uploaderName = item.uploaderName ?: "Unknown Artist",
                    itemCount = item.streamCount.toInt(),
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for artists on YouTube Music.
     */
    suspend fun searchArtists(query: String): List<ArtistItem> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext emptyList()
            val searchExtractor = ytService.getSearchExtractor(query, listOf(FILTER_ARTISTS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<ChannelInfoItem>().mapNotNull { item ->
                ArtistItem(
                    id = item.url.substringAfterLast("/"), // Extract Browse ID from URL
                    name = item.name ?: "Unknown Artist",
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url,
                    subscriberCount = item.subscriberCount?.let { VideoItem.formatViewCount(it) }, // Reusing helper
                    description = item.description,
                    isVerified = item.isVerified
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get details for a specific artist (Songs and Albums).
     *
     * Uses InnerTube /browse with the WEB_REMIX client — the same call the
     * YT Music web app makes — because NewPipe's channel extractor only sees
     * the plain-YouTube uploads tab (a few videos, no albums or top songs).
     */
    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> = withContext(Dispatchers.IO) {
        if (!artistId.startsWith("UC")) {
            // Not a channel browse id (e.g. Library passes the artist *name*);
            // callers fall back to a name search when we return nothing.
            return@withContext Pair(emptyList(), emptyList())
        }
        try {
            val body = browseMusic(artistId)
                ?: return@withContext Pair(emptyList(), emptyList())
            val root = org.json.JSONObject(body)

            val songs = mutableListOf<Song>()
            val albums = mutableListOf<PlaylistDisplayItem>()

            // --- Top songs shelf ("Songs") ---
            // The shelf itself only holds ~5 entries; its bottomEndpoint links
            // the artist's full songs playlist which we fetch below.
            var songsPlaylistBrowseId: String? = null
            val shelves = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "musicShelfRenderer", shelves)
            shelves.firstOrNull()?.let { shelf ->
                songsPlaylistBrowseId = shelf.optJSONObject("bottomEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotEmpty() }
                val contents = shelf.optJSONArray("contents")
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        contents.optJSONObject(i)
                            ?.optJSONObject("musicResponsiveListItemRenderer")
                            ?.let { renderer ->
                                parseResponsiveListItem(renderer)?.let { songs.add(it) }
                            }
                    }
                }
            }

            // --- Albums / Singles carousels ---
            val carousels = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "musicCarouselShelfRenderer", carousels)
            carousels.forEach { carousel ->
                val headerTitle = getRunText(
                    carousel.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                ) ?: ""
                val isReleaseShelf = headerTitle.contains("Album", ignoreCase = true) ||
                        headerTitle.contains("Single", ignoreCase = true) ||
                        headerTitle.contains("EP", ignoreCase = true)
                if (!isReleaseShelf) return@forEach

                val items = carousel.optJSONArray("contents") ?: return@forEach
                for (i in 0 until items.length()) {
                    val twoRow = items.optJSONObject(i)
                        ?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                    val browseId = twoRow.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId") ?: continue
                    if (!browseId.startsWith("MPRE")) continue

                    val title = getRunText(twoRow.optJSONObject("title")) ?: continue
                    val subtitle = getRunText(twoRow.optJSONObject("subtitle")) ?: ""
                    val thumbs = twoRow.optJSONObject("thumbnailRenderer")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val thumbnailUrl = thumbs?.let { it.optJSONObject(it.length() - 1)?.optString("url") }

                    albums.add(
                        PlaylistDisplayItem(
                            name = title,
                            url = "https://music.youtube.com/browse/$browseId",
                            uploaderName = subtitle.ifBlank { "Album" },
                            itemCount = -1,
                            thumbnailUrl = thumbnailUrl
                        )
                    )
                }
            }

            // --- Full songs list via the shelf's "More" playlist ---
            songsPlaylistBrowseId?.let { browseId ->
                val fullList = try {
                    getPlaylistInternal(browseId.removePrefix("VL"))
                } catch (e: Exception) {
                    emptyList()
                }
                fullList.forEach { song ->
                    if (songs.none { it.id == song.id }) songs.add(song)
                }
            }

            android.util.Log.d(
                "YouTubeRepo",
                "Artist $artistId: ${songs.size} songs, ${albums.size} releases"
            )
            Pair(songs.distinctBy { it.id }, albums.distinctBy { it.id })
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching artist details", e)
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Fetch an album's tracks by its browse id (MPREb…).
     * Album pages aren't playlists, so they must go through /browse.
     */
    suspend fun getAlbumSongs(browseId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val body = browseMusic(browseId) ?: return@withContext emptyList()
            val root = org.json.JSONObject(body)

            // Album metadata lives in the header; the renderer differs between
            // the old detail layout and the newer two-column responsive one.
            val headers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "musicResponsiveHeaderRenderer", headers)
            findObjectsByKey(root, "musicDetailHeaderRenderer", headers)
            var albumName = ""
            var albumArtist = ""
            var albumThumb: String? = null
            headers.firstOrNull()?.let { header ->
                albumName = getRunText(header.optJSONObject("title")) ?: ""
                albumArtist = getRunText(header.optJSONObject("straplineTextOne"))
                    ?: getRunText(header.optJSONObject("subtitle"))?.substringAfter("• ")?.substringBefore(" •")
                    ?: ""
                val thumbRenderers = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(header, "musicThumbnailRenderer", thumbRenderers)
                findObjectsByKey(header, "croppedSquareThumbnailRenderer", thumbRenderers)
                albumThumb = thumbRenderers.firstOrNull()
                    ?.optJSONObject("thumbnail")
                    ?.optJSONArray("thumbnails")
                    ?.let { it.optJSONObject(it.length() - 1)?.optString("url") }
            }

            val itemRenderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "musicResponsiveListItemRenderer", itemRenderers)

            itemRenderers.mapNotNull { parseResponsiveListItem(it) }
                .distinctBy { it.id }
                .map { song ->
                    // Album track rows carry no album column and often no
                    // per-track art; fill both from the album header.
                    song.copy(
                        album = albumName.ifBlank { song.album },
                        artist = song.artist.takeIf {
                            it.isNotBlank() && it != "Unknown Artist"
                        } ?: albumArtist.ifBlank { song.artist },
                        thumbnailUrl = song.thumbnailUrl ?: albumThumb
                    )
                }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching album $browseId", e)
            emptyList()
        }
    }

    /**
     * POST to InnerTube /browse with the WEB_REMIX client. Works anonymously;
     * cookies are attached when logged in so results are personalized.
     * Unlike [fetchInternalApi], this does NOT require a login.
     */
    private fun browseMusic(browseId: String, params: String? = null): String? {
        return try {
            val paramsField = if (params != null) """, "params": "$params"""" else ""
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "$WEB_REMIX_VERSION",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "$browseId"$paramsField
                }
            """.trimIndent()

            val requestBuilder = okhttp3.Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/browse")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", getRandomUserAgent())
                .addHeader("Origin", "https://music.youtube.com")

            val cookies = sessionManager.getCookies()
            if (cookies != null) {
                requestBuilder.addHeader("Cookie", cookies)
                YouTubeAuthUtils.getAuthorizationHeader(cookies)?.let { auth ->
                    requestBuilder.addHeader("Authorization", auth)
                    requestBuilder.addHeader("X-Goog-AuthUser", "0")
                }
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            response.body?.string()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "browseMusic($browseId) failed", e)
            null
        }
    }

    /**
     * Fetch next page of results for a previous query.
     */
    suspend fun searchNext(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val extractor = searchExtractorCache[query] ?: return@withContext emptyList()
            
            if (!extractor.initialPage.hasNextPage()) return@withContext emptyList()
            
            val nextPage = extractor.getPage(extractor.initialPage.nextPage)
            // Update the extractor in cache with the new page state if necessary
            // In NewPipe, the extractor object itself might manage the state, or we get a new Page.
            // Actually, we just need to get the items from the new page.
            // But wait, for the *next* next page (page 3), we need to know the offset from THIS page.
            // NewPipe's architecture usually returns a Page which has its OWN next page info.
            
            // However, since we are reusing the SEARCH extractor, we might not be effectively advancing it 
            // if we keep calling getPage on the *initial* page's next info.
            // We need to store the *latest* page info.
            
            // For now, let's just return the items from this second page. 
            // Ideally we'd wrap this in a customized Paginator class.
            
            nextPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item: StreamInfoItem ->
                try {
                    Song.fromYouTube(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "Unknown",
                        artist = item.uploaderName ?: "Unknown Artist",
                        album = "",
                        duration = item.duration * 1000L,
                        thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get the best audio stream URL for a video.
     * Note: These URLs expire, so call this right before playback.
     * @param videoId The YouTube video ID
     * @return The stream URL or null if not found
     */
    /**
     * Get the best audio stream URL for a video.
     * Note: These URLs expire, so call this right before playback.
     * @param videoId The YouTube video ID
     * @return Result containing stream URL or error
     */
    suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()

        // Single resolution mechanism: direct InnerTube /player calls
        // (ANDROID_VR primary, IOS fallback — see runPlayerClientChain), with a
        // one-shot visitorData remint + retry when YouTube's bot check flags
        // the current token (see resolvePlayerStreamingData).
        val url = resolvePlayerStreamingData(videoId)?.let { pickAudioStreamUrl(videoId, it) }

        val dt = System.currentTimeMillis() - startMs
        if (!url.isNullOrEmpty()) {
            android.util.Log.i(
                "YouTubeRepository",
                "Resolve[InnerTube] OK videoId=$videoId dt=${dt}ms",
            )
            Result.success(url)
        } else {
            android.util.Log.e(
                "YouTubeRepository",
                "Resolve FAIL videoId=$videoId all clients exhausted dt=${dt}ms",
            )
            Result.failure(Exception("No audio stream found for $videoId"))
        }
    }

    // --- Fresh visitorData (anti-bot) ---------------------------------------
    // YouTube's /player bot check keys off visitorData: a stale or shared value
    // gets flagged (LOGIN_REQUIRED). A visitorData freshly minted from the
    // youtube.com bootstrap passes. We cache it (companion-level, see there),
    // refresh on a TTL, persist the last good token per install, and remint
    // immediately when a /player response shows the current token got flagged
    // mid-TTL (remintVisitorData) — otherwise every resolution keeps failing
    // for hours, which users experience as "music suddenly stops playing".

    /**
     * Warm the visitorData cache off the critical path. The cold fetch
     * downloads the whole youtube.com bootstrap HTML, so paying for it at
     * app start instead of on the first playback shaves seconds off the
     * first video load of a session.
     */
    suspend fun prefetchVisitorData() {
        try {
            getVisitorData()
        } catch (e: Exception) {
            android.util.Log.w("YouTubeRepository", "visitorData prefetch failed: ${e.message}")
        }
    }

    private suspend fun getVisitorData(): String {
        val now = System.currentTimeMillis()
        cachedVisitorData?.let { if (now - visitorDataFetchedAt < VISITOR_DATA_TTL_MS) return it }
        return visitorDataMutex.withLock {
            val nowInner = System.currentTimeMillis()
            cachedVisitorData?.let { if (nowInner - visitorDataFetchedAt < VISITOR_DATA_TTL_MS) return it }
            val fresh = fetchVisitorData()
            if (!fresh.isNullOrEmpty()) {
                cachedVisitorData = fresh
                visitorDataFetchedAt = nowInner
                persistVisitorData(fresh)
                android.util.Log.i("YouTubeRepository", "visitorData refreshed (len=${fresh.length})")
                fresh
            } else {
                // Reuse a previously-good value: this session's, else the last
                // one this install successfully minted. Never a token shared
                // across installs — the bot check flags shared visitorData,
                // which kills all stream resolution. Empty means "send the
                // /player call without visitorData", which mostly still works.
                cachedVisitorData ?: loadPersistedVisitorData() ?: ""
            }
        }
    }

    /**
     * Drop [flagged] from the in-memory and persisted caches and mint a fresh
     * visitorData right now. Called when a /player response shows YouTube's
     * bot check rejected the current token (LOGIN_REQUIRED / missing
     * streamingData). Returns null when the bootstrap fetch fails.
     */
    private suspend fun remintVisitorData(flagged: String): String? =
        visitorDataMutex.withLock {
            // A concurrent resolution may have already reminted while this one
            // waited on the lock — reuse its token instead of re-fetching.
            cachedVisitorData?.takeIf { it != flagged }?.let { return@withLock it }
            cachedVisitorData = null
            visitorDataFetchedAt = 0L
            clearPersistedVisitorData(flagged)
            val fresh = fetchVisitorData()
            if (!fresh.isNullOrEmpty()) {
                cachedVisitorData = fresh
                visitorDataFetchedAt = System.currentTimeMillis()
                persistVisitorData(fresh)
                android.util.Log.i("YouTubeRepository", "visitorData reminted after bot-check flag")
                fresh
            } else {
                android.util.Log.w("YouTubeRepository", "visitorData remint failed (bootstrap fetch)")
                null
            }
        }

    // Last successfully minted token, persisted per install so a cold start on
    // a flaky network still has a usable, install-unique token to fall back on.
    private val visitorDataPrefs by lazy {
        context.getSharedPreferences("ivor_visitor_data", Context.MODE_PRIVATE)
    }

    private fun loadPersistedVisitorData(): String? =
        visitorDataPrefs.getString("visitor_data", null)?.takeIf { it.isNotBlank() }

    private fun persistVisitorData(value: String) {
        visitorDataPrefs.edit().putString("visitor_data", value).apply()
    }

    private fun clearPersistedVisitorData(flagged: String) {
        if (visitorDataPrefs.getString("visitor_data", null) == flagged) {
            visitorDataPrefs.edit().remove("visitor_data").apply()
        }
    }

    /**
     * Scrape a fresh visitorData token from the youtube.com bootstrap HTML.
     * The token is JSON-escaped in the page (e.g. `=` for `=`), so unescape
     * the two characters that actually appear in base64url visitorData.
     */
    private suspend fun fetchVisitorData(): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/")
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()
            // The bootstrap page is ~1.5 MB — use the general 30s client, not
            // streamResolveClient whose 8s callTimeout kills the download on
            // slow connections and left those users without a usable token.
            val response = okHttpClient.newCall(request).execute()
            val html = response.body?.string().orEmpty()
            response.close()
            Regex("\"visitorData\":\"(.*?)\"").find(html)
                ?.groupValues?.get(1)
                ?.replace("\\u003d", "=")
                ?.replace("\\u0026", "&")
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.w("YouTubeRepository", "fetchVisitorData failed: ${e.message}")
            null
        }
    }

    /**
     * Get stream info including metadata.
     * @param videoId The YouTube video ID
     * @return StreamInfo or null if not found
     */
    suspend fun getStreamInfo(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext null
            val streamExtractor = ytService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()
            // This return type might need adjustment depending on what's expected
            null 
        } catch (e: Exception) {
            null
        }
    }



    /**
     * Get personalized recommendations (Quick Picks / Home).
     * Uses Internal YTM API with Cookies for personalized content.
     */
    suspend fun getRecommendations(): List<Song> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) {
            android.util.Log.d("YouTubeRepo", "Not logged in, falling back to popular search")
            return@withContext search("trending music 2026", FILTER_SONGS)
        }

        try {
            // Fetch personalized home page content
            android.util.Log.d("YouTubeRepo", "Fetching personalized recommendations from FEmusic_home")
            val jsonResponse = fetchInternalApi("FEmusic_home")
            
            if (jsonResponse.isEmpty()) {
                android.util.Log.e("YouTubeRepo", "Empty response from FEmusic_home")
                // Try liked music as fallback
                val likedSongs = getLikedMusic()
                if (likedSongs.isNotEmpty()) return@withContext likedSongs
                return@withContext search("trending music 2024", FILTER_SONGS)
            }
            
            // Parse songs from the home page response
            val items = parseSongsFromInternalJson(jsonResponse)
            android.util.Log.d("YouTubeRepo", "Parsed ${items.size} songs from recommendations")
            
            if (items.isNotEmpty()) return@withContext items
            
            // Fallback to liked music if home parsing failed
            android.util.Log.d("YouTubeRepo", "Recommendations empty, trying liked music")
            val likedSongs = getLikedMusic()
            if (likedSongs.isNotEmpty()) return@withContext likedSongs
            
            // Last resort: search
            search("trending music 2026", FILTER_SONGS)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching recommendations", e)
            try {
                getLikedMusic()
            } catch (e2: Exception) {
                search("trending music 2026", FILTER_SONGS)
            }
        }
    }

    /**
     * Get related songs ("radio") for a video via the InnerTube /next endpoint —
     * the same source YouTube Music uses for its own autoplay queue.
     * Works anonymously; when logged in, the attached cookies personalize the mix.
     */
    suspend fun getRelatedSongs(videoId: String, limit: Int = 25): List<Song> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "$WEB_REMIX_VERSION",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "videoId": "$videoId",
                    "playlistId": "RDAMVM$videoId",
                    "isAudioOnly": true,
                    "tunerSettingValue": "AUTOMIX_SETTING_NORMAL"
                }
            """.trimIndent()

            val requestBuilder = okhttp3.Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", getRandomUserAgent())
                .addHeader("Origin", "https://music.youtube.com")

            // Personalize the radio when logged in; anonymous works fine too.
            val cookies = sessionManager.getCookies()
            if (cookies != null) {
                requestBuilder.addHeader("Cookie", cookies)
                YouTubeAuthUtils.getAuthorizationHeader(cookies)?.let { auth ->
                    requestBuilder.addHeader("Authorization", auth)
                    requestBuilder.addHeader("X-Goog-AuthUser", "0")
                }
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string()
            if (body.isNullOrEmpty()) return@withContext emptyList()

            val renderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(org.json.JSONObject(body), "playlistPanelVideoRenderer", renderers)

            renderers.mapNotNull { parsePlaylistPanelVideo(it) }
                .filter { it.id != videoId } // first radio item is the seed itself
                .distinctBy { it.id }
                .take(limit)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching related songs for $videoId", e)
            emptyList()
        }
    }

    /**
     * Recursively collect every JSONObject stored under [key] anywhere in the tree.
     * Kept structure-agnostic on purpose — InnerTube nests these renderers
     * differently across response variants (queue vs. wrapper renderers).
     */
    private fun findObjectsByKey(node: Any, key: String, results: MutableList<org.json.JSONObject>) {
        when (node) {
            is org.json.JSONObject -> {
                node.optJSONObject(key)?.let { results.add(it) }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k != key) findObjectsByKey(node.get(k), key, results)
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until node.length()) {
                    findObjectsByKey(node.get(i), key, results)
                }
            }
        }
    }

    private fun parsePlaylistPanelVideo(renderer: org.json.JSONObject): Song? {
        val id = renderer.optString("videoId")
        if (id.isEmpty()) return null
        val title = renderer.optJSONObject("title")
            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        if (title.isNullOrEmpty()) return null

        // longBylineText runs alternate: [artist, " • ", album, " • ", views, ...]
        val bylineRuns = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")
        val artist = bylineRuns?.optJSONObject(0)?.optString("text")
            ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val albumCandidate = if ((bylineRuns?.length() ?: 0) > 2) {
            bylineRuns!!.optJSONObject(2)?.optString("text").orEmpty()
        } else ""
        // Music videos put view counts where songs put the album name
        val album = albumCandidate.takeUnless { it.contains(" views") || it.contains(" plays") } ?: ""

        val lengthText = renderer.optJSONObject("lengthText")
            ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")

        val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = if (thumbs != null && thumbs.length() > 0) {
            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
        } else null

        return Song.fromYouTube(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            duration = parseDurationTextToMs(lengthText),
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun parseDurationTextToMs(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        return try {
            val parts = text.split(":").map { it.trim().toLong() }
            when (parts.size) {
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
                2 -> (parts[0] * 60 + parts[1]) * 1000
                1 -> parts[0] * 1000
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get the user's playlists.
     * Uses Internal YTM API with Cookies.
     */
    suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        
        try {
            val playlists = mutableListOf<PlaylistDisplayItem>()

            // Synthesized "Supermix" and "Likes" always useful to have
            playlists.add(PlaylistDisplayItem(
                name = "My Supermix",
                url = "https://music.youtube.com/playlist?list=RTM",
                uploaderName = "YouTube Music",
                thumbnailUrl = "https://www.gstatic.com/youtube/media/ytm/images/pbg/liked_music_@576.png"
            ))
            playlists.add(PlaylistDisplayItem(
                name = "Your Likes",
                url = "https://music.youtube.com/playlist?list=LM",
                uploaderName = "You"
            ))

            // Fetch Library (Liked Playlists), following grid continuations so
            // large libraries come back in full rather than just the first page.
            // Note: FEmusic_liked_playlists gets playlists you've saved/liked
            var json = fetchInternalApi("FEmusic_liked_playlists")
            var pageCount = 0
            val maxPages = 20
            while (json.isNotEmpty() && pageCount < maxPages) {
                val parsed = parsePlaylistsFromInternalJson(json)
                playlists.addAll(parsed)
                pageCount++
                android.util.Log.d("YouTubeRepo", "Library playlists page $pageCount: ${parsed.size} items")
                if (parsed.isEmpty()) break
                val token = extractContinuationToken(json) ?: break
                json = fetchContinuation(token)
            }

            // The library grid can include "Your Likes" (VLLM) which we already synthesized
            playlists.distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching user playlists", e)
            emptyList()
        }
    }

    /**
     * Get liked music with pagination support.
     * YouTube Music API returns paginated results, so we need to fetch all pages.
     */
    suspend fun getLikedMusic(): List<Song> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) {
            return@withContext getPlaylistInternal("LM")
        }
        
        try {
            val allSongs = mutableListOf<Song>()
            var continuationToken: String? = null
            var pageCount = 0
            val maxPages = 500 // Increased limit to fetch all liked songs
            
            do {
                val json = if (continuationToken == null) {
                    fetchInternalApi("FEmusic_liked_videos")
                } else {
                    fetchContinuation(continuationToken)
                }
                
                if (json.isEmpty()) break
                
                val songs = parseSongsFromInternalJson(json)
                allSongs.addAll(songs)
                
                // Extract continuation token for next page
                continuationToken = extractContinuationToken(json)
                pageCount++
                
                android.util.Log.d("YouTubeRepo", "Liked songs page $pageCount: ${songs.size} songs, total: ${allSongs.size}")
                
            } while (continuationToken != null && pageCount < maxPages)
            
            if (allSongs.isNotEmpty()) {
                return@withContext allSongs.distinctBy { it.id }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching liked music", e)
            e.printStackTrace()
        }
        
        // Fallback to NewPipe method
        getPlaylistInternal("LM")
    }
    
    /**
     * Fetch continuation page using continuation token.
     */
    private fun fetchContinuation(continuationToken: String): String {
        val cookies = sessionManager.getCookies() ?: return ""
        val authHeader = YouTubeAuthUtils.getAuthorizationHeader(cookies) ?: ""
        
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB_REMIX",
                        "clientVersion": "$WEB_REMIX_VERSION",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "continuation": "$continuationToken"
            }
        """.trimIndent()
        
        val request = okhttp3.Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", authHeader)
            .addHeader("User-Agent", getRandomUserAgent())
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Goog-AuthUser", "0")
            .build()
        
        return try {
            val response = okHttpClient.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * Extract continuation token from API response for pagination.
     */
    private fun extractContinuationToken(json: String): String? {
        try {
            val root = org.json.JSONObject(json)
            val continuations = mutableListOf<String>()
            
            // Find all nextContinuationData or continuationEndpoint objects
            findContinuationTokens(root, continuations)
            
            return continuations.firstOrNull()
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
    
    private fun findContinuationTokens(node: Any, results: MutableList<String>) {
        if (node is org.json.JSONObject) {
            // Check for nextContinuationData
            if (node.has("nextContinuationData")) {
                val token = node.optJSONObject("nextContinuationData")?.optString("continuation")
                if (!token.isNullOrEmpty()) {
                    results.add(token)
                    return
                }
            }
            // Check for continuationEndpoint
            if (node.has("continuationEndpoint")) {
                val token = node.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")
                if (!token.isNullOrEmpty()) {
                    results.add(token)
                    return
                }
            }
            // Check for direct continuationCommand
            if (node.has("continuationCommand")) {
                val token = node.optJSONObject("continuationCommand")?.optString("token")
                if (!token.isNullOrEmpty()) {
                    results.add(token)
                    return
                }
            }
            // Recurse
            val keys = node.keys()
            while (keys.hasNext()) {
                val nextKey = keys.next()
                findContinuationTokens(node.get(nextKey), results)
            }
        } else if (node is org.json.JSONArray) {
            for (i in 0 until node.length()) {
                findContinuationTokens(node.get(i), results)
            }
        }
    }
    
    suspend fun getPlaylist(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        // For "Your Likes" playlist, use getLikedMusic which handles pagination
        if (playlistId == "LM" || playlistId == "VLLM") {
            return@withContext getLikedMusic()
        }

        // Album browse ids aren't playlists; they only resolve via /browse
        if (playlistId.startsWith("MPRE")) {
            return@withContext getAlbumSongs(playlistId)
        }

        // For other playlists, use internal method
        getPlaylistInternal(playlistId)
    }
    
    /**
     * Internal playlist fetching without the LM redirect to avoid infinite recursion.
     */
    private suspend fun getPlaylistInternal(playlistId: String): List<Song> = withContext(Dispatchers.IO) {

        val newPipeSongs = try {
             val urlId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
             val playlistUrl = "https://www.youtube.com/playlist?list=$urlId"
             
             // Try NewPipe
             val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
             if (ytService != null) {
                 val playlistExtractor = ytService.getPlaylistExtractor(playlistUrl)
                 playlistExtractor.fetchPage()
                 
                 val allItems = mutableListOf<StreamInfoItem>()
                 allItems.addAll(playlistExtractor.initialPage.items.filterIsInstance<StreamInfoItem>())
                 
                 var currentPage = playlistExtractor.initialPage
                 while (currentPage.hasNextPage()) {
                     try {
                         currentPage = playlistExtractor.getPage(currentPage.nextPage)
                         allItems.addAll(currentPage.items.filterIsInstance<StreamInfoItem>())
                     } catch (e: Exception) {
                         break
                     }
                 }
                 
                 allItems.mapNotNull { item ->
                     Song.fromYouTube(
                         videoId = extractVideoId(item.url),
                         title = item.name ?: "Unknown",
                         artist = item.uploaderName ?: "Unknown Artist",
                         album = playlistExtractor.name ?: "",
                         duration = item.duration * 1000L,
                         thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                     )
                 }
             } else emptyList()
        } catch (e: Exception) {
             emptyList()
        }

        if (newPipeSongs.isNotEmpty()) return@withContext newPipeSongs

        // Fallback to InnerTube /browse. Anonymous WEB_REMIX works for public
        // playlists and album playlists (OLAK5uy_…); cookies personalize when
        // logged in (LM, RTM). Playlists browse as "VL<id>".
        try {
            val browseId = if (playlistId.startsWith("VL") || playlistId.startsWith("FE")) {
                playlistId
            } else {
                "VL$playlistId"
            }
            val json = browseMusic(browseId)
            if (json != null) {
                val internalSongs = parseSongsFromInternalJson(json)
                if (internalSongs.isNotEmpty()) return@withContext internalSongs
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        emptyList()
    }

    suspend fun fetchAccountInfo() = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext

        try {
            val jsonResponse = fetchInternalApi("account/account_menu")

            if (jsonResponse.isEmpty()) {
                android.util.Log.w("YouTubeRepo", "fetchAccountInfo: empty response from account/account_menu")
                return@withContext
            }
            android.util.Log.d("YouTubeRepo", "fetchAccountInfo response: ${jsonResponse.take(400)}")
            
            var avatarUrl: String? = null
            var userName: String? = null
            
            try {
                val root = org.json.JSONObject(jsonResponse)
                
                // Navigate to the account section
                // Usually: actions -> openPopupAction -> popup -> multiPageMenuRenderer -> header -> activeAccountHeaderRenderer
                val actions = root.optJSONArray("actions")
                val popup = actions?.optJSONObject(0)
                    ?.optJSONObject("openPopupAction")
                    ?.optJSONObject("popup")
                    ?.optJSONObject("multiPageMenuRenderer")
                
                val header = popup?.optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer")
                
                if (header != null) {
                    // Extract Name
                    userName = getRunText(header.optJSONObject("accountName"))
                    
                    // Extract Avatar
                    val thumbnails = header.optJSONObject("avatar")?.optJSONArray("thumbnails")
                    if (thumbnails != null && thumbnails.length() > 0) {
                        avatarUrl = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    }
                }
                
                // Fallback: Check sections if header failed
                if (userName == null || avatarUrl == null) {
                    val sections = popup?.optJSONArray("sections")
                    if (sections != null) {
                        for (i in 0 until sections.length()) {
                            val item = sections.optJSONObject(i)
                                ?.optJSONObject("multiPageMenuSectionRenderer")
                                ?.optJSONArray("items")?.optJSONObject(0)
                                ?.optJSONObject("compactLinkRenderer")
                                
                            // Sometimes the first item is the account link
                            if (item != null) {
                                val thumb = item.optJSONObject("icon")?.optJSONArray("thumbnails")
                                if (avatarUrl == null && thumb != null) {
                                    avatarUrl = thumb.optJSONObject(thumb.length() - 1)?.optString("url")
                                }
                            }
                        }
                    }
                }
                
                // Fallback: Regex for avatar hosts if parsing failed
                // (yt3.ggpht.com and lh3.googleusercontent.com serve account avatars)
                if (avatarUrl == null) {
                    val avatarRegex = "\"url\"\\s*:\\s*\"(https://(?:yt3\\.ggpht\\.com|[a-z0-9]+\\.googleusercontent\\.com)/[^\"]+)\"".toRegex()
                    val match = avatarRegex.find(jsonResponse)
                    avatarUrl = match?.groupValues?.get(1)
                }

            } catch (jsonEx: Exception) {
                // Ignore
            }
            
            android.util.Log.d("YouTubeRepo", "fetchAccountInfo parsed name=$userName avatar=$avatarUrl")

            // Save avatar if found
            if (!avatarUrl.isNullOrEmpty()) {
                // Upgrade resolution
                val highResUrl = avatarUrl
                    .replace("=s88", "=s512")
                    .replace("=s48", "=s512")
                    .replace("=s96", "=s512")
                sessionManager.saveUserAvatar(highResUrl)
            }
            
            // Save user name if found
            if (!userName.isNullOrEmpty()) {
                sessionManager.saveUserName(userName)
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Outcome of one InnerTube /player call. [visitorDataSuspect] is true when
     * the response indicates YouTube's bot check flagged our visitorData:
     * playability LOGIN_REQUIRED ("Sign in to confirm you're not a bot"), or a
     * 200/OK response with streamingData missing (stale/missing visitorData).
     */
    private class PlayerResponse(
        val streamingData: org.json.JSONObject?,
        val visitorDataSuspect: Boolean,
    )

    /**
     * Resolve /player streamingData for [videoId], reminting the visitorData
     * and retrying once when the responses show the current token has been
     * flagged by YouTube's bot check. Without the remint, a token flagged
     * mid-TTL poisons every resolution until it expires — the "music played
     * fine, then nothing plays anymore" failure mode.
     */
    private suspend fun resolvePlayerStreamingData(videoId: String): org.json.JSONObject? {
        val visitorData = getVisitorData()
        val first = runPlayerClientChain(videoId, visitorData)
        first.streamingData?.let { return it }
        if (!first.visitorDataSuspect) return null

        android.util.Log.w(
            "YouTubeRepository",
            "Resolve: visitorData flagged by bot check, reminting and retrying videoId=$videoId",
        )
        val fresh = remintVisitorData(flagged = visitorData) ?: return null
        if (fresh == visitorData) return null
        return runPlayerClientChain(videoId, fresh).streamingData
    }

    /**
     * The two-client /player chain.
     *
     * ANDROID_VR is the primary (and effectively only) client that yields
     * *fully downloadable* audio URLs without a GVS PO Token: its googlevideo
     * URLs serve the whole file, where IOS-issued URLs are throttled to the
     * first ~1 MiB and then return HTTP 403 (GVS PO-Token enforcement).
     *
     * IOS stays only as a last-ditch fallback for the rare videos ANDROID_VR
     * can't serve (e.g. "made for kids", which ANDROID_VR omits). Its URL may
     * be throttled, but a few seconds of audio beats a hard failure.
     *
     * Each call is hard-capped by streamResolveClient's callTimeout, so the
     * worst case is bounded regardless of coroutine cancellability.
     */
    private suspend fun runPlayerClientChain(videoId: String, visitorData: String): PlayerResponse {
        val androidVrExtras = org.json.JSONObject().apply {
            put("androidSdkVersion", 32)
            put("deviceMake", "Oculus")
            put("deviceModel", "Quest 3")
            put("osName", "Android")
            put("osVersion", "12L")
        }
        val iosExtras = org.json.JSONObject().apply {
            put("deviceMake", "Apple")
            put("deviceModel", "iPhone16,2")
            put("osName", "iPhone")
            put("osVersion", "18.1.0.22B83")
        }

        val vr = fetchPlayerResponse(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = ANDROID_VR_VERSION,
            clientNameId = ANDROID_VR_CLIENT_ID,
            userAgent = ANDROID_VR_USER_AGENT,
            visitorData = visitorData,
            extraClientFields = androidVrExtras,
        )
        vr.streamingData?.let { return vr }

        val ios = fetchPlayerResponse(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = IOS_VERSION,
            clientNameId = IOS_CLIENT_ID,
            userAgent = IOS_USER_AGENT,
            visitorData = visitorData,
            extraClientFields = iosExtras,
        )
        return PlayerResponse(
            streamingData = ios.streamingData,
            visitorDataSuspect = vr.visitorDataSuspect || ios.visitorDataSuspect,
        )
    }

    /**
     * Select the best audio URL from a /player streamingData object. Falls back
     * to muxed video formats (e.g. itag 18) when no audio-only format is
     * available — ExoPlayer extracts the audio track from the MP4 container,
     * which is critical because ANDROID_VR can return only format 18 since
     * March 2026 (see yt-dlp issue #16150).
     */
    private fun pickAudioStreamUrl(
        videoId: String,
        streamingData: org.json.JSONObject,
    ): String? {
        val formats = mutableListOf<org.json.JSONObject>()
        streamingData.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) formats.add(arr.getJSONObject(i))
        }
        streamingData.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) formats.add(arr.getJSONObject(i))
        }

        fun hasPlayableUrl(f: org.json.JSONObject): Boolean = !f.optString("url").isNullOrEmpty()

        val audioFormats = formats.filter {
            it.optString("mimeType").contains("audio") && hasPlayableUrl(it)
        }
        android.util.Log.d(
            "YouTubeRepository",
            "Resolve[InnerTube] formats=${formats.size} audioOnly=${audioFormats.size} videoId=$videoId",
        )

        audioFormats.maxByOrNull { it.optInt("bitrate") }?.optString("url")
            ?.takeIf { it.isNotEmpty() }?.let { return it }

        // No audio-only stream available — fall back to a muxed MP4 (itag 18 etc.).
        // ExoPlayer happily plays just the audio track of these.
        val muxedFormats = formats.filter {
            it.optString("mimeType").startsWith("video/") && hasPlayableUrl(it)
        }
        muxedFormats.minByOrNull { it.optInt("bitrate") }?.optString("url")
            ?.takeIf { it.isNotEmpty() }?.let {
                android.util.Log.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube] using muxed video format videoId=$videoId (no audio-only)",
                )
                return it
            }

        val cipheredCount = formats.count {
            !it.optString("signatureCipher").isNullOrEmpty() ||
                !it.optString("cipher").isNullOrEmpty()
        }
        android.util.Log.w(
            "YouTubeRepository",
            "Resolve[InnerTube] no usable URL videoId=$videoId ciphered=${cipheredCount}/${formats.size}",
        )
        return null
    }

    /**
     * Single-client InnerTube /player call returning the raw streamingData
     * object plus a bot-check verdict (see [PlayerResponse]). Shared by audio
     * resolution and video quality listing.
     */
    private suspend fun fetchPlayerResponse(
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientNameId: Int,
        userAgent: String,
        visitorData: String,
        extraClientFields: org.json.JSONObject = org.json.JSONObject(),
    ): PlayerResponse = withContext(Dispatchers.IO) {
        try {
            val clientObj = org.json.JSONObject().apply {
                put("clientName", clientName)
                put("clientVersion", clientVersion)
                put("hl", "en")
                put("gl", "US")
                put("utcOffsetMinutes", 0)
                if (visitorData.isNotBlank()) put("visitorData", visitorData)
                val keys = extraClientFields.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    put(k, extraClientFields.get(k))
                }
            }
            val contextObj = org.json.JSONObject().put("client", clientObj)
            // No playbackContext.signatureTimestamp: that field is the player-JS
            // "sts" value, which only matters for ciphered WEB streams. The
            // native clients used here (ANDROID_VR / IOS) return unciphered
            // URLs and don't need it.
            val jsonBody = org.json.JSONObject().apply {
                put("videoId", videoId)
                put("context", contextObj)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }.toString()

            val url = "https://youtubei.googleapis.com/youtubei/v1/player?key=$INNER_TUBE_API_KEY&prettyPrint=false"

            val requestBuilder = okhttp3.Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", userAgent)
                .addHeader("X-Goog-Api-Format-Version", "2")
                .addHeader("X-YouTube-Client-Name", clientNameId.toString())
                .addHeader("X-YouTube-Client-Version", clientVersion)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("Accept", "application/json")
            if (visitorData.isNotBlank()) {
                requestBuilder.addHeader("X-Goog-Visitor-Id", visitorData)
            }
            val request = requestBuilder.build()

            val response = streamResolveClient.newCall(request).execute()
            val code = response.code
            val json = response.body?.string().orEmpty()
            response.close()

            if (code !in 200..299) {
                android.util.Log.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] HTTP $code videoId=$videoId body=${json.take(160)}",
                )
                return@withContext PlayerResponse(null, false)
            }
            if (json.isEmpty()) {
                android.util.Log.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] empty body videoId=$videoId",
                )
                return@withContext PlayerResponse(null, false)
            }

            val root = org.json.JSONObject(json)

            val playability = root.optJSONObject("playabilityStatus")
            val status = playability?.optString("status").orEmpty()
            if (status.isNotEmpty() && status != "OK") {
                android.util.Log.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] playability=$status reason=${playability?.optString("reason")} videoId=$videoId",
                )
                // LOGIN_REQUIRED here is the bot check rejecting our
                // visitorData ("Sign in to confirm you're not a bot").
                return@withContext PlayerResponse(null, status == "LOGIN_REQUIRED")
            }

            val streamingData = root.optJSONObject("streamingData")
            if (streamingData == null) {
                android.util.Log.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] no streamingData videoId=$videoId",
                )
                // Status OK with no streamingData is the other known signature
                // of a stale/missing visitorData.
                return@withContext PlayerResponse(null, true)
            }
            PlayerResponse(streamingData, false)
        } catch (e: Exception) {
            android.util.Log.e(
                "YouTubeRepository",
                "Resolve[InnerTube/$clientName] exception videoId=$videoId",
                e,
            )
            PlayerResponse(null, false)
        }
    }

    // --- Internal API Helper ---

    private fun fetchInternalApi(endpoint: String): String {
        val cookies = sessionManager.getCookies() ?: return ""
        val isBrowse = !endpoint.contains("/") // simple check: browseId vs endpoint path
        
        val url = if (isBrowse) {
            "https://music.youtube.com/youtubei/v1/browse"
        } else {
            "https://music.youtube.com/youtubei/v1/$endpoint"
        }
        
        // Generate the required Authorization header (SAPISIDHASH)
        val authHeader = YouTubeAuthUtils.getAuthorizationHeader(cookies) ?: ""

        // Construct complete JSON body for WEB_REMIX client
        val jsonBody = if (isBrowse) {
            """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "$WEB_REMIX_VERSION",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "browseId": "$endpoint"
                }
            """.trimIndent()
        } else {
             """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "$WEB_REMIX_VERSION",
                            "hl": "en",
                            "gl": "US"
                        }
                    }
                }
            """.trimIndent()
        }

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", authHeader)
            .addHeader("User-Agent", getRandomUserAgent())
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Goog-AuthUser", "0")
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseSongsFromInternalJson(json: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val root = org.json.JSONObject(json)
            
            // OPTIMIZED: Direct traversal instead of recursive search
            // Find the SectionListRenderer which contains the shelves
            val contentsArray = findRootContents(root) ?: return emptyList()

            // Iterate over shelves (musicCarouselShelfRenderer, musicShelfRenderer, etc.)
            for (i in 0 until contentsArray.length()) {
                val shelfWrapper = contentsArray.optJSONObject(i) ?: continue
                
                // Get the items array from the shelf
                val items = parseItemsFromShelf(shelfWrapper)
                
                // Process items
                items.forEach { item ->
                    try {
                        // Strategy 1: musicResponsiveListItemRenderer (Flex Columns) - Standard Song/Video list
                        val responsiveItem = item.optJSONObject("musicResponsiveListItemRenderer")
                        if (responsiveItem != null) {
                            parseResponsiveListItem(responsiveItem)?.let { songs.add(it) }
                        }
                        
                        // Strategy 2: musicTwoRowItemRenderer (Title/Subtitle) - Cards/Shelves
                        val twoRowItem = item.optJSONObject("musicTwoRowItemRenderer")
                        if (twoRowItem != null) {
                             parseTwoRowItem(twoRowItem)?.let { songs.add(it) }
                        }
                    } catch (e: Exception) {
                        // Skip malformed item
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs.distinctBy { it.id }
    }

    // --- Optimized Traversal Helpers ---

    /**
     * Locates the 'contents' array within sectionListRenderer by traversing standard paths.
     * Handles: Home (Browse), Search Results, and Playlist Details.
     */
    private fun findRootContents(root: org.json.JSONObject): org.json.JSONArray? {
        // Path 1: Standard Browse/Home/Playlist (contents -> singleColumn... -> tabs -> tab -> content -> sectionList)
        root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { return it }

        // Path 1b: Two-column Browse (newer playlist/album layout:
        // contents -> twoColumnBrowseResultsRenderer -> secondaryContents -> sectionList)
        root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { return it }

        // Path 2: Search Results (contents -> tabbedSearchResultsRenderer -> tabs -> tab -> content -> sectionList)
        root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.let { return it }
            
        // Path 3: Direct SectionList (sometimes used in continuation responses)
        root.optJSONObject("continuationContents")
            ?.optJSONObject("musicPlaylistShelfContinuation")
            ?.optJSONArray("contents")
            ?.let { 
                // Wrap items in a synthetic shelf structure to match loop expectation or return directly
                // For continuation, it's usually a list of items directly.
                // To keep logic consistent, we'll return this directly and handle it if the caller expects shelves.
                // Actually, continuations usually return items directly, not shelves.
                // Let's handle generic continuation structure:
                return it
            }
            

            
        root.optJSONObject("continuationContents")
            ?.optJSONObject("musicShelfContinuation")
            ?.optJSONArray("contents")
            ?.let { return it }
            
        root.optJSONObject("continuationContents")
            ?.optJSONObject("sectionListContinuation")
            ?.optJSONArray("contents")
            ?.let { return it }

        // Grid continuation (library playlist/album pages)
        root.optJSONObject("continuationContents")
            ?.optJSONObject("gridContinuation")
            ?.optJSONArray("items")
            ?.let { return it }

        return null
    }

    /**
     * Extracts the list of items from a Shelf wrapper (Carousel, Shelf, or direct list).
     */
    private fun parseItemsFromShelf(shelfWrapper: org.json.JSONObject): List<org.json.JSONObject> {
        val items = mutableListOf<org.json.JSONObject>()
        
        // 1. musicCarouselShelfRenderer (Horizontal Scroll)
        val carousel = shelfWrapper.optJSONObject("musicCarouselShelfRenderer")
        if (carousel != null) {
            val contents = carousel.optJSONArray("contents")
            if (contents != null) {
                for (j in 0 until contents.length()) {
                    contents.optJSONObject(j)?.let { items.add(it) }
                }
            }
            return items
        }
        
        // 2. musicShelfRenderer (Vertical List)
        val shelf = shelfWrapper.optJSONObject("musicShelfRenderer")
        if (shelf != null) {
            val contents = shelf.optJSONArray("contents")
            if (contents != null) {
                for (j in 0 until contents.length()) {
                    contents.optJSONObject(j)?.let { items.add(it) }
                }
            }
            return items
        }
        
        // 3. musicPlaylistShelfRenderer (Playlist Detail List)
        val playlistShelf = shelfWrapper.optJSONObject("musicPlaylistShelfRenderer")
        if (playlistShelf != null) {
            val contents = playlistShelf.optJSONArray("contents")
            if (contents != null) {
                for (j in 0 until contents.length()) {
                    contents.optJSONObject(j)?.let { items.add(it) }
                }
            }
            return items
        }
        
        // 4. gridRenderer (Library pages: grid of playlists/albums)
        val grid = shelfWrapper.optJSONObject("gridRenderer")
        if (grid != null) {
            val gridItems = grid.optJSONArray("items")
            if (gridItems != null) {
                for (j in 0 until gridItems.length()) {
                    gridItems.optJSONObject(j)?.let { items.add(it) }
                }
            }
            return items
        }

        // 5. itemSectionRenderer wraps another shelf (e.g. the library grid)
        val itemSection = shelfWrapper.optJSONObject("itemSectionRenderer")
        if (itemSection != null) {
            val contents = itemSection.optJSONArray("contents")
            if (contents != null) {
                for (j in 0 until contents.length()) {
                    contents.optJSONObject(j)?.let { items.addAll(parseItemsFromShelf(it)) }
                }
            }
            return items
        }

        // 6. Direct Item (if the "shelf" is actually just an item in a continuation list)
        if (shelfWrapper.has("musicResponsiveListItemRenderer") || shelfWrapper.has("musicTwoRowItemRenderer")) {
            items.add(shelfWrapper)
        }

        return items
    }

    private fun parseResponsiveListItem(item: org.json.JSONObject): Song? {
        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        
        // Video ID extraction
        // Usually in navigationEndpoint -> watchEndpoint
        // Or playlistItemData -> videoId
        var videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
        
        if (videoId.isNullOrEmpty()) {
             // Try searching deep for watch endpoint
             val nav = item.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
             videoId = nav?.optString("videoId")
        }
        
        if (videoId.isNullOrEmpty()) {
             // Last resort: scan the flex columns for a navigation endpoint
             // This is cheaper than full recursion
             for (i in 0 until flexColumns.length()) {
                 val col = flexColumns.optJSONObject(i)
                             ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                             ?.optJSONObject("text")
                 val runs = col?.optJSONArray("runs")
                 if (runs != null) {
                     for (r in 0 until runs.length()) {
                         val vid = runs.optJSONObject(r)
                            ?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                         if (!vid.isNullOrEmpty()) {
                             videoId = vid
                             break
                         }
                     }
                 }
                 if (!videoId.isNullOrEmpty()) break
             }
        }

        if (videoId.isNullOrEmpty()) return null

        // Extract Title
        val titleFormatted = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        val title = getRunText(titleFormatted) ?: "Unknown Title"

        // Extract Artist and Album
        val subtitleFormatted = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        
        val subtitleRuns = subtitleFormatted?.optJSONArray("runs")
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        
        if (subtitleRuns != null && subtitleRuns.length() > 0) {
            val firstPart = subtitleRuns.optJSONObject(0)?.optString("text")
            if (firstPart == "Song" || firstPart == "Video" || firstPart == "Music video") {
                // Format: Song • Artist • Album
                if (subtitleRuns.length() > 2) {
                    artist = subtitleRuns.optJSONObject(2)?.optString("text") ?: artist
                    if (subtitleRuns.length() > 4) {
                        album = subtitleRuns.optJSONObject(4)?.optString("text") ?: album
                    }
                }
            } else {
                // Format: Artist • Album
                artist = firstPart ?: artist
                if (subtitleRuns.length() > 2) {
                    album = subtitleRuns.optJSONObject(2)?.optString("text") ?: album
                }
            }
        }

        // Extract Thumbnail
        val thumbnails = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
        
        val thumbnailUrl = thumbnails?.let {
            it.optJSONObject(it.length() - 1)?.optString("url")
        }

        // Duration sits in the trailing fixed column ("3:42")
        val durationText = item.optJSONArray("fixedColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
            ?.optJSONObject("text")
            ?.let { getRunText(it) }

        return Song.fromYouTube(
            videoId = videoId!!,
            title = title,
            artist = artist,
            album = album,
            duration = parseDurationTextToMs(durationText),
            thumbnailUrl = thumbnailUrl
        )
    }

    private fun parseTwoRowItem(item: org.json.JSONObject): Song? {
         // Check if it's a song/video (has videoId in navigation)
         // Navigation often in: navigationEndpoint -> watchEndpoint -> videoId
         val nav = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
         val videoId = nav?.optString("videoId")
         
         if (videoId.isNullOrEmpty()) return null 

         val title = getRunText(item.optJSONObject("title")) ?: "Unknown"
         
         val subtitleFormatted = item.optJSONObject("subtitle")
         val subtitleRuns = subtitleFormatted?.optJSONArray("runs")
          
         var artist = "Unknown Artist"
         var album = "Unknown"
         
         if (subtitleRuns != null && subtitleRuns.length() > 0) {
             val firstPart = subtitleRuns.optJSONObject(0)?.optString("text")
             if (firstPart == "Song" || firstPart == "Video" || firstPart == "Music video") {
                 if (subtitleRuns.length() > 2) {
                     artist = subtitleRuns.optJSONObject(2)?.optString("text") ?: artist
                 }
             } else {
                 artist = firstPart ?: artist
             }
         }
         
          val thumbnails = item.optJSONObject("thumbnailRenderer")
             ?.optJSONObject("musicThumbnailRenderer")
             ?.optJSONObject("thumbnail")
             ?.optJSONArray("thumbnails")
         
         val thumbnailUrl = thumbnails?.let {
             it.optJSONObject(it.length() - 1)?.optString("url")
         }

         return Song.fromYouTube(
             videoId = videoId,
             title = title,
             artist = artist,
             album = album,
             duration = 0L,
             thumbnailUrl = thumbnailUrl
         )
    }
    
    private fun parsePlaylistsFromInternalJson(json: String): List<PlaylistDisplayItem> {
        val playlists = mutableListOf<PlaylistDisplayItem>()
        try {
            val root = org.json.JSONObject(json)
            
            // OPTIMIZED: Use direct traversal
            val contentsArray = findRootContents(root) ?: return emptyList()

            // Iterate over shelves and items
            for (i in 0 until contentsArray.length()) {
                val shelfWrapper = contentsArray.optJSONObject(i) ?: continue
                val items = parseItemsFromShelf(shelfWrapper)
                
                items.forEach { item ->
                    try {
                         // Playlists are usually musicTwoRowItemRenderer
                         val twoRowItem = item.optJSONObject("musicTwoRowItemRenderer")
                         if (twoRowItem != null) {
                             // Extract ID
                             val navigationEndpoint = twoRowItem.optJSONObject("navigationEndpoint")
                             val browseId = navigationEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                             
                             // Ensure it's a playlist
                             if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL"))) {
                                 val cleanId = browseId.removePrefix("VL")
                                 
                                 // Extract Title
                                 val title = getRunText(twoRowItem.optJSONObject("title")) ?: "Unknown Playlist"
                                 
                                 // Extract Subtitle (Uploader / Count)
                                 val subtitleObj = twoRowItem.optJSONObject("subtitle")
                                 val subtitle = getRunText(subtitleObj) ?: "Unknown"
                                 
                                 val itemCount = extractItemCountFromSubtitle(subtitleObj)
                                 
                                 // Extract Thumbnail
                                 val thumbnails = twoRowItem.optJSONObject("thumbnailRenderer")
                                    ?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")
                                    ?.optJSONArray("thumbnails")
                                 
                                 val thumbnailUrl = thumbnails?.let {
                                     it.optJSONObject(it.length() - 1)?.optString("url")
                                 }

                                 playlists.add(PlaylistDisplayItem(
                                     name = title,
                                     url = "https://music.youtube.com/playlist?list=$cleanId",
                                     uploaderName = subtitle,
                                     itemCount = itemCount,
                                     thumbnailUrl = thumbnailUrl
                                 ))
                             }
                         }
                    } catch (e: Exception) {
                         // Skip
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return playlists 
    }
    
    /**
     * Extract item count from playlist subtitle.
     * The subtitle typically contains patterns like "100 songs", "50 videos", etc.
     */
    private fun extractItemCountFromSubtitle(subtitleObj: org.json.JSONObject?): Int {
        if (subtitleObj == null) return -1
        
        try {
            // Try to find count in runs array
            val runs = subtitleObj.optJSONArray("runs")
            if (runs != null) {
                for (i in 0 until runs.length()) {
                    val runText = runs.optJSONObject(i)?.optString("text") ?: continue
                    // Look for patterns like "100 songs", "50 videos", "25 tracks"
                    val countMatch = Regex("""(\d+)\s*(songs?|videos?|tracks?)""", RegexOption.IGNORE_CASE).find(runText)
                    if (countMatch != null) {
                        return countMatch.groupValues[1].toIntOrNull() ?: -1
                    }
                    // Also check for just numbers that might represent count
                    val numberMatch = Regex("""^(\d+)$""").find(runText.trim())
                    if (numberMatch != null) {
                        return numberMatch.groupValues[1].toIntOrNull() ?: -1
                    }
                }
            }
            
            // Try from simpleText
            val simpleText = subtitleObj.optString("simpleText", "")
            val countMatch = Regex("""(\d+)\s*(songs?|videos?|tracks?)""", RegexOption.IGNORE_CASE).find(simpleText)
            if (countMatch != null) {
                return countMatch.groupValues[1].toIntOrNull() ?: -1
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return -1
    }

    // --- JSON Helpers ---

    // Optimized replacement for recursive searching when needed
    // Only search 1 level deep for specific keys to avoid full recursion
    private fun findObject(node: Any, key: String): org.json.JSONObject? {
         if (node is org.json.JSONObject) {
            if (node.has(key)) return node.getJSONObject(key)
         }
         return null
    }

    private fun getRunText(formattedString: org.json.JSONObject?): String? {
        if (formattedString == null) return null
        if (formattedString.has("simpleText")) {
            return formattedString.optString("simpleText")
        }
        val runs = formattedString.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
        }
        return sb.toString()
    }

    private fun extractValueFromRuns(item: org.json.JSONObject, key: String): String? {
        // Direct checkout instead of recursion
        val nav = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
        return nav?.optString(key)
    }



    private fun extractVideoId(url: String): String {
        // Extract video ID from various YouTube URL formats
        val patterns = listOf(
            Regex("watch\\?v=([a-zA-Z0-9_-]+)"),
            Regex("youtu\\.be/([a-zA-Z0-9_-]+)"),
            Regex("youtube\\.com/embed/([a-zA-Z0-9_-]+)"),
            Regex("music\\.youtube\\.com/watch\\?v=([a-zA-Z0-9_-]+)")
        )
        
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        
        return url // Fallback: return the URL as-is
    }

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

        try {
            val cookies = sessionManager.getCookies() ?: return@withContext
            val authHeader = YouTubeAuthUtils.getAuthorizationHeader(cookies) ?: ""
            val cpn = generateCpn()
            
            // Visitor Data (default fallback)
            val visitorData = "Cgt6SUNYVzB2VkJDbyjGrrSmBg%3D%3D"

            // Client constants - using WEB_REMIX (web player)
            val clientName = "WEB_REMIX"
            val clientVersion = WEB_REMIX_VERSION

            // Step 1: Call player endpoint to get tracking URLs
            val playerUrl = "https://music.youtube.com/youtubei/v1/player"
            val jsonBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "$clientName",
                            "clientVersion": "$clientVersion",
                            "hl": "en",
                            "gl": "US",
                            "visitorData": "$visitorData"
                        }
                    },
                    "videoId": "$videoId",
                    "cpn": "$cpn",
                    "playbackContext": {
                        "contentPlaybackContext": {
                            "signatureTimestamp": ${System.currentTimeMillis() / 1000}
                        }
                    }
                }
            """.trimIndent()

            val playerRequest = okhttp3.Request.Builder()
                .url(playerUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Cookie", cookies)
                .addHeader("Authorization", authHeader)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/")
                .addHeader("X-Goog-AuthUser", "0")
                .addHeader("X-Goog-Api-Format-Version", "1")
                .addHeader("X-YouTube-Client-Name", "67") // WEB_REMIX numeric ID
                .addHeader("X-YouTube-Client-Version", clientVersion)
                .addHeader("X-Goog-Visitor-Id", visitorData)
                .build()

            val playerResponse = okHttpClient.newCall(playerRequest).execute()
            val playerResponseBody = playerResponse.body?.string()
            playerResponse.close()
            
            if (playerResponseBody.isNullOrEmpty()) {
                android.util.Log.e("YouTubeRepo", "Player response empty for $videoId")
                return@withContext
            }

            // Parse response to extract playback tracking URL
            val playerJson = org.json.JSONObject(playerResponseBody)
            val playbackTracking = playerJson.optJSONObject("playbackTracking")
            
            if (playbackTracking == null) {
                // Log more details about the error
                val playabilityStatus = playerJson.optJSONObject("playabilityStatus")
                val status = playabilityStatus?.optString("status")
                val reason = playabilityStatus?.optString("reason")
                android.util.Log.e("YouTubeRepo", "No playbackTracking. Status: $status, Reason: $reason")
                return@withContext
            }
            
            val videostatsPlaybackUrl = playbackTracking
                .optJSONObject("videostatsPlaybackUrl")
                ?.optString("baseUrl")

            if (videostatsPlaybackUrl.isNullOrEmpty()) {
                android.util.Log.e("YouTubeRepo", "No playback tracking URL found for $videoId")
                return@withContext
            }

            // Step 3: Call the tracking URL to register the play
            // Append required parameters
            val trackingUrl = buildString {
                append(videostatsPlaybackUrl)
                if (!videostatsPlaybackUrl.contains("cpn=")) {
                    append(if (videostatsPlaybackUrl.contains("?")) "&" else "?")
                    append("cpn=$cpn")
                }
                append("&ver=2")
                append("&c=$clientName")
            }

            val trackingRequest = okhttp3.Request.Builder()
                .url(trackingUrl)
                .get()
                .addHeader("Cookie", cookies)
                .addHeader("Authorization", authHeader)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Origin", "https://music.youtube.com")
                .addHeader("Referer", "https://music.youtube.com/watch?v=$videoId")
                .build()

            val trackingResponse = okHttpClient.newCall(trackingRequest).execute()
            if (trackingResponse.isSuccessful) {
                android.util.Log.d("YouTubeRepo", "History sync SUCCESS for $videoId")
            } else {
                android.util.Log.e("YouTubeRepo", "History sync failed: ${trackingResponse.code}")
            }
            trackingResponse.close()

        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error in reportPlayback", e)
        }
    }

    // ============== VIDEO MODE FUNCTIONS ==============

    /**
     * Search for videos on YouTube (not YouTube Music).
     * Returns VideoItem objects with view counts, channel info, etc.
     */
    suspend fun searchVideos(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
                ?: return@withContext emptyList()
            
            // Use YouTube videos filter (not music_videos)
            val searchExtractor = ytService.getSearchExtractor(query, listOf(FILTER_YOUTUBE_VIDEOS), "")
            searchExtractor.fetchPage()
            
            searchExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
                try {
                    val uploaderUrl = item.uploaderUrl ?: ""
                    val channelId = when {
                        uploaderUrl.contains("/channel/") -> uploaderUrl.substringAfter("/channel/")
                        uploaderUrl.contains("/@") -> uploaderUrl.substringAfter("/@").let { "@$it" }
                        uploaderUrl.contains("/user/") -> uploaderUrl.substringAfter("/user/")
                        else -> null
                    }
                    
                    VideoItem.fromStreamInfoItem(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "Unknown",
                        channelName = item.uploaderName ?: "Unknown Channel",
                        channelId = channelId,
                        channelIconUrl = item.uploaderAvatars?.maxByOrNull { it.width }?.url,
                        thumbnailUrl = item.thumbnails?.maxByOrNull { it.width }?.url ?: item.thumbnails?.firstOrNull()?.url,
                        durationSeconds = item.duration,
                        viewCount = item.viewCount,
                        uploadedDate = item.textualUploadDate,
                        isLive = item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM,
                        subscriberCount = null
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error searching videos", e)
            emptyList()
        }
    }

    /**
     * Get recommended videos for the video mode home screen.
     * 1. Logged in: personalized YouTube home feed.
     * 2. Otherwise: taste-based mix built from the local watch history.
     * 3. Cold start: generic popular search.
     * (YouTube removed the public Trending page in mid-2025 — the InnerTube
     * FEtrending browseId now returns HTTP 400, so no trending fallback.)
     */
    suspend fun getTrendingVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val isLoggedIn = sessionManager.isLoggedIn()
        android.util.Log.d("YouTubeRepo", "getTrendingVideos - isLoggedIn: $isLoggedIn")

        if (isLoggedIn) {
            try {
                val videos = getPersonalizedVideoRecommendations()
                if (videos.isNotEmpty()) {
                    android.util.Log.d("YouTubeRepo", "Got ${videos.size} personalized videos")
                    return@withContext videos
                }
                android.util.Log.w("YouTubeRepo", "Personalized recommendations empty, using taste-based feed")
            } catch (e: Exception) {
                android.util.Log.e("YouTubeRepo", "Error fetching personalized videos", e)
            }
        }

        try {
            val tasteFeed = getTasteBasedVideos()
            if (tasteFeed.isNotEmpty()) {
                android.util.Log.d("YouTubeRepo", "Got ${tasteFeed.size} taste-based videos")
                return@withContext tasteFeed
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error building taste-based feed", e)
        }

        // Cold start: nothing watched yet and not logged in
        try {
            searchVideos("trending videos ${java.time.Year.now().value}")
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Cold-start search failed", e)
            emptyList()
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
            android.util.Log.w("YouTubeRepo", "getRelatedVideosLight failed for $videoId", e)
            emptyList()
        }
    }

    /**
     * Related videos from a watch-next response: lockupViewModels under
     * secondaryResults (the modern shape since 2025).
     */
    private fun parseRelatedFromWatchNext(root: org.json.JSONObject): List<VideoItem> {
        val secondary = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?.optJSONObject("secondaryResults") ?: return emptyList()
        val lockups = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(secondary, "lockupViewModel", lockups)
        return lockups.mapNotNull { parseLockupViewModel(it) }
    }

    /**
     * Build a feed from the related videos of recently watched ones.
     * Interleaves per-seed results for variety; drops watched videos and dupes.
     */
    private suspend fun getTasteBasedVideos(): List<VideoItem> = kotlinx.coroutines.coroutineScope {
        val history = videoHistoryRepository.getHistory()
        if (history.isEmpty()) return@coroutineScope emptyList()

        val seeds = history.take(6)
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
     * Uses the YouTube homepage API to get personalized suggestions.
     */
    private suspend fun getPersonalizedVideoRecommendations(): List<VideoItem> = withContext(Dispatchers.IO) {
        val cookies = sessionManager.getCookies() ?: return@withContext emptyList()
        
        // Extract SAPISID for authentication hash
        val sapisid = cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.split("=")?.getOrNull(1)
        
        // Generate SAPISID hash for authorization
        val origin = "https://www.youtube.com"
        val authHeader = if (sapisid != null) {
            val timestamp = System.currentTimeMillis() / 1000
            val hashInput = "$timestamp $sapisid $origin"
            val hash = java.security.MessageDigest.getInstance("SHA-1")
                .digest(hashInput.toByteArray())
                .joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_${hash}"
        } else {
            YouTubeAuthUtils.getAuthorizationHeader(cookies, origin) ?: ""
        }
        
        // Use YouTube browse endpoint for "What to Watch" (home page recommendations)
        val url = "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "$WEB_VERSION",
                        "hl": "en",
                        "gl": "US",
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
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", authHeader)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Origin", origin)
            .addHeader("Referer", "$origin/")
            .addHeader("X-Goog-AuthUser", "0")
            .addHeader("X-Origin", origin)
            .addHeader("Accept", "*/*")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            android.util.Log.d("YouTubeRepo", "Making personalized video request with auth: ${authHeader.take(30)}...")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            response.close()
            
            android.util.Log.d("YouTubeRepo", "Got personalized response: ${responseBody.take(500)}...")
            val videos = parseVideosFromYouTubeJson(responseBody)
            android.util.Log.d("YouTubeRepo", "Parsed ${videos.size} personalized videos")
            videos
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error in getPersonalizedVideoRecommendations", e)
            emptyList()
        }
    }

    /**
     * Get user's watch history from YouTube.
     * Uses the YouTube browse endpoint with "FEhistory".
     */
    suspend fun getWatchHistory(): List<VideoItem> = withContext(Dispatchers.IO) {
        val cookies = sessionManager.getCookies() ?: return@withContext emptyList()
        
        // Extract SAPISID for authentication hash (reusing logic for consistency)
        val sapisid = cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.split("=")?.getOrNull(1)
        
        // Generate SAPISID hash for authorization
        val origin = "https://www.youtube.com"
        val authHeader = if (sapisid != null) {
            val timestamp = System.currentTimeMillis() / 1000
            val hashInput = "$timestamp $sapisid $origin"
            val hash = java.security.MessageDigest.getInstance("SHA-1")
                .digest(hashInput.toByteArray())
                .joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_${hash}"
        } else {
            YouTubeAuthUtils.getAuthorizationHeader(cookies, origin) ?: ""
        }
        
        val url = "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false"
        
        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "$WEB_VERSION",
                        "hl": "en",
                        "gl": "US",
                        "originalUrl": "https://www.youtube.com/feed/history",
                        "platform": "DESKTOP"
                    },
                    "user": {
                        "lockedSafetyMode": false
                    }
                },
                "browseId": "FEhistory"
            }
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", authHeader)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Origin", origin)
            .addHeader("Referer", "$origin/feed/history")
            .addHeader("X-Goog-AuthUser", "0")
            .addHeader("X-Origin", origin)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            response.close()
            
            // Re-use the existing parsing logic which handles various video item formats
            parseVideosFromYouTubeJson(responseBody)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching watch history", e)
            emptyList()
        }
    }

    /**
     * Resurrected helper for deep recursive search.
     * Used sparingly for fallback scenarios where structure is unknown.
     */
    private fun findAllObjects(json: org.json.JSONObject, key: String, results: MutableList<org.json.JSONObject>, depth: Int = 0) {
        if (depth > 20) return // Reduced depth limit from 50
        
        if (json.has(key)) {
            val value = json.opt(key)
            if (value is org.json.JSONObject) {
                results.add(value)
            } else if (value is org.json.JSONArray) {
                for (i in 0 until value.length()) {
                    val item = value.optJSONObject(i)
                    if (item != null) results.add(item)
                }
            }
        }
        
        json.keys().forEach { keyName ->
            val value = json.opt(keyName)
            when (value) {
                is org.json.JSONObject -> findAllObjects(value, key, results, depth + 1)
                is org.json.JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.optJSONObject(i)
                        if (item != null) findAllObjects(item, key, results, depth + 1)
                    }
                }
            }
        }
    }
    /**
     * Parse video items from YouTube homepage JSON response.
     * Use optimized path traversal instead of recursive findAllObjects.
     */
    private fun parseVideosFromYouTubeJson(json: String): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        try {
            val root = org.json.JSONObject(json)
            
            // Locate content array
            // Normal Home: contents -> singleColumnBrowseResultsRenderer -> tabs[0] -> tabRenderer -> content -> richGridRenderer -> contents
            // Or sectionListRenderer -> contents
            
            var contents: org.json.JSONArray? = null
            
            // Desktop WEB responses (FEwhat_to_watch) use twoColumnBrowseResultsRenderer;
            // singleColumn is the mobile/YTM shape. Check both.
            val browseContents = root.optJSONObject("contents")
            val tabs = browseContents
                ?.optJSONObject("twoColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?: browseContents
                    ?.optJSONObject("singleColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                
            if (tabs != null && tabs.length() > 0) {
                 val contentObj = tabs.optJSONObject(0)?.optJSONObject("tabRenderer")?.optJSONObject("content")
                 
                 // Try RichGrid (modern Home)
                 contents = contentObj?.optJSONObject("richGridRenderer")?.optJSONArray("contents")
                 
                 // Try SectionList (old Home or other views)
                 if (contents == null) {
                     contents = contentObj?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                 }
            }
            
            // If we found contents, iterate them
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val item = contents.optJSONObject(i) ?: continue
                    
                    // 1. RichItemRenderer (Home Grid)
                    val richItem = item.optJSONObject("richItemRenderer")
                    if (richItem != null) {
                        val content = richItem.optJSONObject("content")
                        
                        // Handler for VideoRenderer (Old UI)
                        content?.optJSONObject("videoRenderer")?.let { 
                            parseVideoRenderer(it)?.let { v -> videos.add(v) } 
                        }
                        
                        // Handler for LockupViewModel (New UI)
                        content?.optJSONObject("lockupViewModel")?.let {
                            parseLockupViewModel(it)?.let { v -> videos.add(v) }
                        }
                    }
                    
                    // 2. ItemSectionRenderer (flat lists, e.g. FEhistory's date-grouped sections)
                    val itemSection = item.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")
                    if (itemSection != null) {
                        for (j in 0 until itemSection.length()) {
                            val sectionItem = itemSection.optJSONObject(j) ?: continue
                            sectionItem.optJSONObject("videoRenderer")?.let {
                                parseVideoRenderer(it)?.let { v -> videos.add(v) }
                            }
                            sectionItem.optJSONObject("lockupViewModel")?.let {
                                parseLockupViewModel(it)?.let { v -> videos.add(v) }
                            }
                        }
                    }

                    // 3. RichSectionRenderer (Shelves within Grid)
                    val richSection = item.optJSONObject("richSectionRenderer")?.optJSONObject("content")
                    if (richSection != null) {
                        val shelfItems = parseItemsFromShelf(richSection)
                        shelfItems.forEach { shelfItem ->
                             // Check for LockupViewModel in shelf
                             if (shelfItem.has("lockupViewModel")) {
                                  parseLockupViewModel(shelfItem.optJSONObject("lockupViewModel"))?.let { v -> videos.add(v) }
                             } else if (shelfItem.has("videoRenderer")) {
                                  parseVideoRenderer(shelfItem.optJSONObject("videoRenderer"))?.let { v -> videos.add(v) }
                             } else if (shelfItem.has("gridVideoRenderer")) { // Search results often use this
                                  parseVideoRenderer(shelfItem.optJSONObject("gridVideoRenderer"))?.let { v -> videos.add(v) }
                             }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return videos.distinctBy { it.videoId }.take(30)
    }
    
    // ============================================================
    // YouTube Shorts (www.youtube.com): shelf feed + endless
    // reel_watch_sequence pager. Shapes verified against the live
    // API July 2026.
    // ============================================================

    /**
     * Shorts for the video home shelf. Logged in: the personalized Shorts
     * shelf inside the FEwhat_to_watch home response (postWatchApi signs the
     * call, so the shelf matches the account's recommendations). Logged out
     * or shelf missing: an InnerTube search seeded from local watch history,
     * which surfaces the same shortsLockupViewModel items.
     */
    suspend fun getShortsFeed(): List<ShortsItem> = withContext(Dispatchers.IO) {
        if (sessionManager.isLoggedIn()) {
            try {
                val body = org.json.JSONObject()
                    .put("context", webContext())
                    .put("browseId", "FEwhat_to_watch")
                val raw = postWatchApi("browse", body)
                if (raw != null) {
                    val shorts = parseShortsLockups(org.json.JSONObject(raw))
                    if (shorts.isNotEmpty()) return@withContext shorts
                }
                android.util.Log.w("YouTubeRepo", "No Shorts shelf on home, using search fallback")
            } catch (e: Exception) {
                android.util.Log.e("YouTubeRepo", "Shorts home shelf failed", e)
            }
        }
        try {
            val seedChannel = videoHistoryRepository.getHistory()
                .firstOrNull { it.channelName.isNotBlank() && it.channelName != "Unknown Channel" }
                ?.channelName
            val query = seedChannel?.let { "$it shorts" } ?: "trending shorts"
            val body = org.json.JSONObject()
                .put("context", webContext())
                .put("query", query)
            val raw = postWatchApi("search", body) ?: return@withContext emptyList()
            parseShortsLockups(org.json.JSONObject(raw))
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Shorts search fallback failed", e)
            emptyList()
        }
    }

    /**
     * One page of the endless swipe feed from reel/reel_watch_sequence.
     * [sequenceParams] is either a shelf item's seed params or the
     * continuation token of a previous page (the endpoint accepts both in
     * the same field). Signed calls return the personalized sequence.
     * Entries carry no title/views — the player enriches the current Short
     * from its watch-next call.
     */
    suspend fun getShortsSequence(sequenceParams: String): ShortsFeedPage = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webContext())
                .put("sequenceParams", sequenceParams)
            val raw = postWatchApi("reel/reel_watch_sequence", body)
                ?: return@withContext ShortsFeedPage(emptyList(), null)
            val root = org.json.JSONObject(raw)

            val items = mutableListOf<ShortsItem>()
            val entries = root.optJSONArray("entries")
            if (entries != null) {
                for (i in 0 until entries.length()) {
                    val reel = entries.optJSONObject(i)
                        ?.optJSONObject("command")
                        ?.optJSONObject("reelWatchEndpoint") ?: continue
                    parseReelWatchEndpoint(reel)?.let { items.add(it) }
                }
            }
            val continuation = root.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")?.takeIf { it.isNotBlank() }
            ShortsFeedPage(items, continuation)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getShortsSequence failed", e)
            ShortsFeedPage(emptyList(), null)
        }
    }

    /** All shortsLockupViewModels of a response, deduped, in shelf order. */
    private fun parseShortsLockups(root: org.json.JSONObject): List<ShortsItem> {
        val lockups = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "shortsLockupViewModel", lockups)
        return lockups.mapNotNull { parseShortsLockup(it) }
            .distinctBy { it.videoId }
            .take(30)
    }

    /**
     * shortsLockupViewModel: videoId + sequenceParams live in
     * onTap.innertubeCommand.reelWatchEndpoint, title/views in
     * overlayMetadata.primaryText/secondaryText, portrait thumbnail in the
     * reelWatchEndpoint (1080x1920 frame0).
     */
    private fun parseShortsLockup(lockup: org.json.JSONObject): ShortsItem? {
        return try {
            val reel = lockup.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("reelWatchEndpoint") ?: return null
            val base = parseReelWatchEndpoint(reel) ?: return null

            val overlay = lockup.optJSONObject("overlayMetadata")
            val title = overlay?.optJSONObject("primaryText")?.optString("content")
                ?.takeIf { it.isNotBlank() } ?: ""
            val viewCount = overlay?.optJSONObject("secondaryText")?.optString("content")
                ?.takeIf { it.isNotBlank() } ?: ""

            // Prefer the lockup's own thumbnailViewModel (sized variants) over
            // the reel endpoint's single 1080x1920 frame
            val sources = lockup.optJSONObject("thumbnailViewModel")
                ?.optJSONObject("image")?.optJSONArray("sources")
            val lockupThumb = sources?.optJSONObject(sources.length() - 1)
                ?.optString("url")?.takeIf { it.isNotBlank() }

            base.copy(
                title = title,
                viewCount = viewCount,
                thumbnailUrl = lockupThumb ?: base.thumbnailUrl
            )
        } catch (e: Exception) {
            android.util.Log.w("YouTubeRepo", "parseShortsLockup failed", e)
            null
        }
    }

    /** reelWatchEndpoint: videoId, portrait thumbnail and sequence seed. */
    private fun parseReelWatchEndpoint(reel: org.json.JSONObject): ShortsItem? {
        val videoId = reel.optString("videoId").takeIf { it.length == 11 } ?: return null
        val thumbs = reel.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = thumbs?.optJSONObject(0)?.optString("url")
            ?.takeIf { it.isNotBlank() }
        return ShortsItem(
            videoId = videoId,
            thumbnailUrl = thumbnailUrl,
            sequenceParams = reel.optString("sequenceParams").takeIf { it.isNotBlank() }
        )
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
        try {
            val json = fetchYouTubeBrowse("FEplaylist_aggregation")
                .takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
            val root = org.json.JSONObject(json)
            val lockups = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "lockupViewModel", lockups)
            lockups.mapNotNull { parsePlaylistLockup(it) }
                .filter { it.playlistId != "LL" && it.playlistId != "WL" }
                .distinctBy { it.playlistId }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getVideoPlaylists failed", e)
            emptyList()
        }
    }

    /**
     * Videos of one playlist via browse VL<playlistId> (first page, up to 100).
     * Works for Watch Later ("WL") and Liked videos ("LL") too — both need
     * login. The renderer differs per playlist surface: WL and regular
     * playlists come as playlistVideoRenderers, LL comes as plain video
     * lockupViewModels — parse whichever the response contains.
     */
    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val json = fetchYouTubeBrowse(browseId)
                .takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
            val root = org.json.JSONObject(json)
            val renderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "playlistVideoRenderer", renderers)
            if (renderers.isNotEmpty()) {
                return@withContext renderers.mapNotNull { parsePlaylistVideoRenderer(it) }
            }
            val lockups = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "lockupViewModel", lockups)
            lockups.mapNotNull { parseLockupViewModel(it) }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getPlaylistVideos failed for $playlistId", e)
            emptyList()
        }
    }

    /**
     * Playlist lockup (LOCKUP_CONTENT_TYPE_PLAYLIST / PODCAST): contentId is
     * the playlist id, title lives in lockupMetadataViewModel, the video count
     * is a thumbnailBadgeViewModel text ("28 videos") and the first metadata
     * row carries privacy/type parts ("Private", "Playlist").
     */
    private fun parsePlaylistLockup(lockup: org.json.JSONObject): VideoPlaylist? {
        val contentType = lockup.optString("contentType")
        if (contentType != "LOCKUP_CONTENT_TYPE_PLAYLIST" &&
            contentType != "LOCKUP_CONTENT_TYPE_PODCAST"
        ) return null
        val playlistId = lockup.optString("contentId").takeIf { it.isNotBlank() } ?: return null
        val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = metadata?.optJSONObject("title")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: return null

        val contentImage = lockup.optJSONObject("contentImage")
        val thumbnailViewModel = contentImage?.optJSONObject("collectionThumbnailViewModel")
            ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
            ?: contentImage?.optJSONObject("thumbnailViewModel")
        val sources = thumbnailViewModel?.optJSONObject("image")?.optJSONArray("sources")
        var thumbnailUrl: String? = null
        var maxWidth = -1
        if (sources != null) {
            for (i in 0 until sources.length()) {
                val source = sources.optJSONObject(i)
                val width = source?.optInt("width", 0) ?: 0
                if (width >= maxWidth) {
                    maxWidth = width
                    thumbnailUrl = source?.optString("url")
                }
            }
        }

        val badges = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(lockup, "thumbnailBadgeViewModel", badges)
        val videoCountText = badges.firstNotNullOfOrNull { badge ->
            badge.optString("text").takeIf { it.isNotBlank() }
        }

        val firstRowParts = metadata.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")?.optJSONObject(0)
            ?.optJSONArray("metadataParts")
        val subtitle = firstRowParts?.let { parts ->
            (0 until parts.length())
                .mapNotNull { parts.optJSONObject(it)?.optJSONObject("text")?.optString("content") }
                .filter { it.isNotBlank() }
                .joinToString(" • ")
                .takeIf { it.isNotBlank() }
        }

        return VideoPlaylist(
            playlistId = playlistId,
            title = title,
            thumbnailUrl = thumbnailUrl?.takeIf { it.isNotBlank() },
            videoCountText = videoCountText,
            subtitle = subtitle
        )
    }

    /**
     * playlistVideoRenderer: videoId/title/shortBylineText/lengthSeconds plus
     * a combined videoInfo line ("376K views • 2 days ago"). Unavailable
     * entries (deleted/private) come with isPlayable=false and are skipped.
     */
    private fun parsePlaylistVideoRenderer(renderer: org.json.JSONObject): VideoItem? {
        val videoId = renderer.optString("videoId").takeIf { it.length == 11 } ?: return null
        if (!renderer.optBoolean("isPlayable", true)) return null
        val title = getRunText(renderer.optJSONObject("title"))
            ?.takeIf { it.isNotBlank() } ?: return null

        val byline = renderer.optJSONObject("shortBylineText")
        val channelName = getRunText(byline)?.takeIf { it.isNotBlank() } ?: "Unknown Channel"
        val channelId = byline?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?.optString("browseId")?.takeIf { it.isNotBlank() }

        val info = getRunText(renderer.optJSONObject("videoInfo")).orEmpty()
        val infoParts = info.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val viewCount = infoParts.firstOrNull { it.contains("view", ignoreCase = true) } ?: ""
        val uploadedDate = infoParts.firstOrNull { !it.contains("view", ignoreCase = true) }

        val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumbnailUrl = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() } ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return VideoItem(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = renderer.optString("lengthSeconds").toLongOrNull() ?: 0L,
            viewCount = viewCount,
            uploadedDate = uploadedDate
        )
    }

    // --- Video Parsing Helpers ---

    private fun parseLockupViewModel(lockupViewModel: org.json.JSONObject?): VideoItem? {
        if (lockupViewModel == null) return null
        try {
            val contentId = lockupViewModel.optString("contentId")
            // STRICT VALIDATION: Ensure it's a valid Video ID (11 chars) to avoid playlists/channels
            if (contentId.length != 11) return null
            
             val metadata = lockupViewModel.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
             val titleObj = metadata?.optJSONObject("title")
             val title = titleObj?.optString("content") ?: "Unknown Title"
             
             // Get channel name and ID from metadata
             val metadataDetails = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
             val metadataRows = metadataDetails?.optJSONArray("metadataRows")
             var channelName = "Unknown Channel"
             var channelId: String? = null
             var viewCount = ""
             var uploadDate = ""
             
             if (metadataRows != null && metadataRows.length() > 0) {
                 val firstRowParts = metadataRows.optJSONObject(0)?.optJSONArray("metadataParts")
                 if (firstRowParts != null && firstRowParts.length() > 0) {
                     val textObj = firstRowParts.optJSONObject(0)?.optJSONObject("text")
                     channelName = textObj?.optString("content") ?: channelName
                     
                     // Extract channel ID directly from runs if available
                     if (textObj?.has("runs") == true) {
                         val runs = textObj.optJSONArray("runs")
                         if (runs != null && runs.length() > 0) {
                             val browseEndpoint = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                             channelId = browseEndpoint?.optString("browseId")
                         }
                     }
                 }
                 // Second row usually has views and date
                 if (metadataRows.length() > 1) {
                     val secondRow = metadataRows.optJSONObject(1)?.optJSONArray("metadataParts")
                     if (secondRow != null) {
                         for (i in 0 until secondRow.length()) {
                             val part = secondRow.optJSONObject(i)?.optJSONObject("text")?.optString("content") ?: ""
                             if (part.contains("view", ignoreCase = true)) {
                                 viewCount = part
                             } else if (part.isNotBlank() && uploadDate.isBlank()) {
                                 uploadDate = part
                             }
                         }
                     }
                 }
             }
             
             // Channel avatar lives inline in the lockup metadata:
             // metadata.image.decoratedAvatarViewModel.avatar.avatarViewModel.image.sources[]
             val channelIconUrl = metadata
                 ?.optJSONObject("image")
                 ?.optJSONObject("decoratedAvatarViewModel")
                 ?.optJSONObject("avatar")
                 ?.optJSONObject("avatarViewModel")
                 ?.optJSONObject("image")
                 ?.optJSONArray("sources")
                 ?.let { sources ->
                     var bestUrl: String? = null
                     var maxWidth = -1
                     for (i in 0 until sources.length()) {
                         val source = sources.optJSONObject(i)
                         val width = source?.optInt("width", 0) ?: 0
                         if (width >= maxWidth) {
                             maxWidth = width
                             bestUrl = source?.optString("url")
                         }
                     }
                     bestUrl?.takeIf { it.isNotBlank() }
                 }

             // Get thumbnail
             val contentImage = lockupViewModel.optJSONObject("contentImage")
             val thumbnailViewModel = contentImage?.optJSONObject("collectionThumbnailViewModel")
                 ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
                 ?: contentImage?.optJSONObject("thumbnailViewModel")
             
             var thumbnailUrl = thumbnailViewModel?.optJSONObject("image")?.optJSONArray("sources")?.let { sources ->
                 // Get highest quality thumbnail
                 var bestUrl: String? = null
                 var maxWidth = 0
                 for (i in 0 until sources.length()) {
                     val source = sources.optJSONObject(i)
                     val width = source?.optInt("width", 0) ?: 0
                     if (width >= maxWidth) {
                         maxWidth = width
                         bestUrl = source?.optString("url")
                     }
                 }
                 bestUrl
             }
             
             if (thumbnailUrl.isNullOrBlank()) {
                 thumbnailUrl = "https://i.ytimg.com/vi/$contentId/hqdefault.jpg"
             }
             
             // Get Duration
             val overlays = thumbnailViewModel?.optJSONArray("overlays")
             var durationSeconds = 0L
             var durationText = ""
             var hasLiveBadge = false
             if (overlays != null) {
                 overlayLoop@ for (i in 0 until overlays.length()) {
                     val overlayItem = overlays.optJSONObject(i) ?: continue
                     // Badge containers: legacy thumbnailOverlayBadgeViewModel.thumbnailBadges
                     // and the modern (2026) thumbnailBottomOverlayViewModel.badges
                     val badgeArrays = listOfNotNull(
                         overlayItem.optJSONObject("thumbnailOverlayBadgeViewModel")
                             ?.optJSONArray("thumbnailBadges"),
                         overlayItem.optJSONObject("thumbnailBottomOverlayViewModel")
                             ?.optJSONArray("badges")
                     )
                     for (badges in badgeArrays) {
                         for (j in 0 until badges.length()) {
                             val badge = badges.optJSONObject(j)
                                 ?.optJSONObject("thumbnailBadgeViewModel") ?: continue
                             val badgeText = badge.optString("text")
                             if (badge.optString("badgeStyle").contains("LIVE") ||
                                 badgeText.equals("LIVE", ignoreCase = true)
                             ) {
                                 hasLiveBadge = true
                             }
                             if (badgeText.contains(":")) {
                                 durationText = badgeText
                                 durationSeconds = parseDurationToSeconds(badgeText)
                                 break@overlayLoop
                             }
                         }
                     }
                     // Try thumbnailOverlayTimeStatusRenderer path
                     val timeStatus = overlayItem.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                         ?.optJSONObject("text")?.optString("simpleText")
                     if (timeStatus != null && timeStatus.contains(":")) {
                         durationText = timeStatus
                         durationSeconds = parseDurationToSeconds(timeStatus)
                         break
                     }
                 }
             }
             
             // If still no duration, try to extract from accessibility text or metadata
             if (durationSeconds <= 0L) {
                 // Try to find duration in title accessibility or elsewhere
                 val accessibilityLabel = titleObj?.optJSONObject("accessibility")?.optString("label") ?: ""
                 val durationMatch = Regex("(\\d+):(\\d+)(?::(\\d+))?").find(accessibilityLabel)
                 if (durationMatch != null) {
                     durationText = durationMatch.value
                     durationSeconds = parseDurationToSeconds(durationText)
                 }
             }
             
             // Assume it's not live if we couldn't find duration (most videos have a duration)
             val isLive = hasLiveBadge ||
                         durationText.contains("LIVE", ignoreCase = true) ||
                         viewCount.contains("watching", ignoreCase = true)
             
            return VideoItem(
                videoId = contentId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                channelIconUrl = channelIconUrl,
                thumbnailUrl = thumbnailUrl,
                duration = durationSeconds,
                viewCount = viewCount,
                uploadedDate = uploadDate,
                isLive = isLive
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseVideoRenderer(videoRenderer: org.json.JSONObject?): VideoItem? {
        if (videoRenderer == null) return null
        try {
            val videoId = videoRenderer.optString("videoId")
                .takeIf { it.isNotBlank() }
                ?: videoRenderer.optString("contentId")
            
            if (videoId.isNullOrBlank()) {
                return null
            }
            
            // Extract title
            val titleObj = videoRenderer.optJSONObject("title")
            val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: titleObj?.optString("simpleText")
                ?: titleObj?.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label")
                ?: "Unknown Title"
            
            // Extract channel name
            val channelObj = videoRenderer.optJSONObject("ownerText")
                ?: videoRenderer.optJSONObject("shortBylineText")
                ?: videoRenderer.optJSONObject("longBylineText")
            val channelName = channelObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: "Unknown Channel"
            
            // Extract view count
            val viewCountText = videoRenderer.optJSONObject("viewCountText")?.optString("simpleText")
                ?: videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText")
                ?: videoRenderer.optJSONObject("shortViewCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: ""
            
            // Extract duration
            val durationText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") 
                ?: videoRenderer.optJSONObject("lengthText")?.optJSONObject("accessibility")?.optJSONObject("accessibilityData")?.optString("label")?.let { 
                    // Convert "3 minutes, 45 seconds" to "3:45"
                    val mins = Regex("(\\d+) minute").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val secs = Regex("(\\d+) second").find(it)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    String.format("%d:%02d", mins, secs)
                }
                ?: "0:00"
            val durationSeconds = parseDurationToSeconds(durationText)
            
            // Extract thumbnail
            val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnailUrl = thumbnails?.let {
                // Get highest quality thumbnail
                var bestUrl: String? = null
                var maxWidth = 0
                for (i in 0 until it.length()) {
                    val thumb = it.optJSONObject(i)
                    val width = thumb?.optInt("width", 0) ?: 0
                    if (width >= maxWidth) {
                        maxWidth = width
                        bestUrl = thumb?.optString("url")
                    }
                }
                bestUrl ?: it.optJSONObject(it.length() - 1)?.optString("url")
            }
            
            // Extract channel icon
            var channelId: String? = null
            
            // 1. Try directly from channelThumbnailSupportedRenderers
            val channelThumbnails = videoRenderer.optJSONObject("channelThumbnailSupportedRenderers")
                ?.optJSONObject("channelThumbnailWithLinkRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            
            var channelIconUrl = channelThumbnails?.let {
                it.optJSONObject(it.length() - 1)?.optString("url")
            }
            
            // 2. Try to extract channelId and icon from channelObj navigationEndpoint
            try {
                val runs = channelObj?.optJSONArray("runs")
                if (runs != null && runs.length() > 0) {
                    val browseEndpoint = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    channelId = browseEndpoint?.optString("browseId")
                }
            } catch (e: Exception) {}

            // 3. Fallback search for avatar in the whole renderer if missing
            if (channelIconUrl == null) {
                // We use the light-weight finder here since we are inside a single renderer, so recursion is shallow
                val avatarList = mutableListOf<org.json.JSONObject>()
                findAllObjects(videoRenderer, "avatar", avatarList, 0)
                for (avatar in avatarList) {
                    val thumbs = avatar.optJSONArray("thumbnails")
                    if (thumbs != null && thumbs.length() > 0) {
                        channelIconUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                        break
                    }
                }
            }
            // Extract upload date
            val publishedText = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText")
            
            return VideoItem(
                videoId = videoId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                channelIconUrl = channelIconUrl,
                thumbnailUrl = thumbnailUrl,
                duration = durationSeconds,
                viewCount = viewCountText,
                uploadedDate = publishedText,
                isLive = durationSeconds <= 0L
            )
        } catch (e: Exception) {
             return null
        }
    }

    /**
     * Parse duration string like "3:45" or "1:23:45" to seconds.
     */
    private fun parseDurationToSeconds(duration: String): Long {
        if (duration.isBlank() || duration == "0:00") return 0L
        val parts = duration.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    private fun formatSubscriberCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    /**
     * Recursively find all JSON objects with a specific key and add them to the results list.
     */




    /**
     * Get the video stream URL (both audio and video) for playback.
     * For video mode, we need the video stream not just audio.
     */
    suspend fun getVideoStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
                ?: return@withContext null
            val streamExtractor = ytService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()
            
            // Get video streams (with audio)
            val videoStreams = streamExtractor.videoStreams
            // Prefer higher quality
            val bestVideoStream = videoStreams
                .filter { it.resolution != null }
                .maxByOrNull { 
                    it.resolution?.replace("p", "")?.toIntOrNull() ?: 0 
                }
            
            bestVideoStream?.content
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error getting video stream", e)
            null
        }
    }

    /**
     * Get available video qualities for a video.
     */


    private fun fetchYouTubeBrowse(browseId: String): String {
        val cookies = sessionManager.getCookies() ?: return ""
        val url = "https://www.youtube.com/youtubei/v1/browse?key=$INNER_TUBE_API_KEY"
        
        // Generate SAPISIDHASH for www.youtube.com origin
        val authHeader = YouTubeAuthUtils.getAuthorizationHeader(cookies, "https://www.youtube.com") ?: ""

        val jsonBody = """
            {
                "context": {
                    "client": {
                        "clientName": "WEB",
                        "clientVersion": "$WEB_VERSION",
                        "hl": "en",
                        "gl": "US"
                    }
                },
                "browseId": "$browseId"
            }
        """.trimIndent()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", authHeader)
            .addHeader("User-Agent", getRandomUserAgent())
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("X-Goog-AuthUser", "0")
            .build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error in fetchYouTubeBrowse", e)
            ""
        }
    }

    private fun getChannelAvatarUrl(channelId: String?): String? {
        if (channelId.isNullOrBlank()) return null
        
        try {
            // Use regular YouTube browse for channels/handles
            val json = fetchYouTubeBrowse(channelId).takeIf { it.isNotEmpty() } ?: return null
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
            android.util.Log.e("YouTubeRepo", "Error fetching channel avatar", e)
        }
        return null
    }

    /**
     * FAST: Get only video stream qualities for immediate playback.
     * Does NOT fetch channel avatar, related videos, or extra metadata.
     * Use this to start playback ASAP, then call getVideoDetails() for the rest.
     */
    suspend fun getVideoStreamQualities(videoId: String): List<VideoQuality> = withContext(Dispatchers.IO) {
        // Primary: direct InnerTube /player with the ANDROID_VR client. It returns
        // the full adaptive ladder (up to 2160p60) with direct, unciphered URLs,
        // where NewPipe's WEB-based extraction often degrades to the muxed 360p
        // format 18 (PO-Token enforcement).
        try {
            val innerTubeQualities = getVideoQualitiesFromInnerTube(videoId)
            if (innerTubeQualities.isNotEmpty()) {
                android.util.Log.i(
                    "YouTubeRepo",
                    "Video qualities via InnerTube: ${innerTubeQualities.size} for $videoId"
                )
                return@withContext innerTubeQualities
            }
        } catch (e: Exception) {
            android.util.Log.w("YouTubeRepo", "InnerTube quality resolution failed, falling back to NewPipe", e)
        }

        // Fallback: NewPipe extractor.
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
                ?: return@withContext emptyList()
            val streamExtractor = ytService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()
            
            val qualities = mutableListOf<VideoQuality>()
            
            // 1. DASH/HLS (best quality, adaptive)
            streamExtractor.dashMpdUrl?.takeIf { it.isNotBlank() }?.let { url ->
                qualities.add(VideoQuality("Auto (Best)", url, "DASH", true))
            } ?: streamExtractor.hlsUrl?.takeIf { it.isNotBlank() }?.let { url ->
                qualities.add(VideoQuality("Auto (HLS)", url, "HLS", true))
            }
            
            // 2. Adaptive Streams (video + separate audio)
            val videoOnlyStreams = streamExtractor.videoOnlyStreams
            val audioStreams = streamExtractor.audioStreams
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

            // 3. Muxed Streams (video + audio combined)
            qualities.addAll(streamExtractor.videoStreams
                .mapNotNull { stream ->
                    val res = stream.resolution ?: return@mapNotNull null
                    val url = stream.content ?: return@mapNotNull null
                    VideoQuality(res, url, stream.format?.name, false)
                }
            )
            
            qualities.distinctBy { it.resolution }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error getting video stream qualities", e)
            emptyList()
        }
    }

    /**
     * Resolve the full video quality ladder via InnerTube: ANDROID_VR first
     * (no PO token, unciphered URLs), IOS as fallback, with a one-shot
     * visitorData remint when the bot check flags the current token. Returns
     * an empty list when neither client yields usable streamingData.
     */
    private suspend fun getVideoQualitiesFromInnerTube(videoId: String): List<VideoQuality> {
        val streamingData = resolvePlayerStreamingData(videoId) ?: return emptyList()
        return parseQualitiesFromStreamingData(streamingData)
    }

    private fun parseQualitiesFromStreamingData(streamingData: org.json.JSONObject): List<VideoQuality> {
        fun org.json.JSONArray.objects(): List<org.json.JSONObject> =
            (0 until length()).mapNotNull { optJSONObject(it) }

        val adaptive = streamingData.optJSONArray("adaptiveFormats")?.objects() ?: emptyList()
        val muxed = streamingData.optJSONArray("formats")?.objects() ?: emptyList()

        // Best separate audio track; prefer AAC (mp4a) for broad hardware support,
        // then highest bitrate.
        val bestAudioUrl = adaptive
            .filter { it.optString("mimeType").startsWith("audio/") && it.optString("url").isNotEmpty() }
            .maxWithOrNull(
                compareBy(
                    { if (it.optString("mimeType").contains("mp4a")) 1 else 0 },
                    { it.optInt("bitrate") }
                )
            )
            ?.optString("url")?.takeIf { it.isNotEmpty() }

        // Codec preference for the video track: H.264 decodes everywhere,
        // VP9 is widely supported, AV1 only on recent chipsets.
        fun codecRank(mimeType: String): Int = when {
            mimeType.contains("avc1") -> 3
            mimeType.contains("vp9") || mimeType.contains("vp09") -> 2
            else -> 1
        }

        fun container(mimeType: String): String =
            mimeType.substringAfter("video/").substringBefore(";").ifEmpty { "mp4" }

        val qualities = mutableListOf<VideoQuality>()

        // Video-only adaptive formats, one entry per quality label, merged with
        // the best audio track at playback time.
        if (bestAudioUrl != null) {
            adaptive
                .filter {
                    it.optString("mimeType").startsWith("video/") &&
                        it.optString("url").isNotEmpty() &&
                        it.optString("qualityLabel").isNotEmpty()
                }
                .groupBy { it.optString("qualityLabel") }
                .forEach { (label, formats) ->
                    val best = formats.maxWithOrNull(
                        compareBy({ codecRank(it.optString("mimeType")) }, { it.optInt("bitrate") })
                    ) ?: return@forEach
                    qualities.add(
                        VideoQuality(
                            resolution = label,
                            url = best.optString("url"),
                            format = container(best.optString("mimeType")),
                            isDASH = false,
                            audioUrl = bestAudioUrl
                        )
                    )
                }
        }

        // Muxed formats (itag 18 etc.) fill in labels not already covered and
        // keep playback possible when no audio-only track exists.
        muxed.forEach { f ->
            val label = f.optString("qualityLabel")
            val url = f.optString("url")
            if (label.isNotEmpty() && url.isNotEmpty() && qualities.none { it.resolution == label }) {
                qualities.add(VideoQuality(label, url, container(f.optString("mimeType")), false))
            }
        }

        // Highest resolution first, 60fps variants before 30fps at equal height.
        fun height(label: String): Int = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        fun fps(label: String): Int = label.substringAfter("p", "").takeWhile { it.isDigit() }.toIntOrNull() ?: 30
        return qualities.sortedWith(
            compareByDescending<VideoQuality> { height(it.resolution) }.thenByDescending { fps(it.resolution) }
        )
    }

    /**
     * Get video details including qualities and related videos.
     */
    suspend fun getVideoDetails(videoId: String): VideoDetails = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
                ?: return@withContext VideoDetails(emptyList(), emptyList())
            val streamExtractor = ytService.getStreamExtractor(streamUrl)
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
            val audioStreams = streamExtractor.audioStreams
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
            
            val finalQualities = qualities.distinctBy { it.resolution }
            
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
            android.util.Log.e("YouTubeRepo", "Error getting video details", e)
            VideoDetails(emptyList(), emptyList())
        }
    }

    /**
     * Get available video qualities for a video.
     */
    suspend fun getVideoQualities(videoId: String): List<VideoQuality> = getVideoDetails(videoId).qualities

    // ============================================================
    // Video engagement: like/dislike, subscribe, comments
    // (InnerTube WEB client against www.youtube.com)
    // ============================================================

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    private fun webContext(): org.json.JSONObject =
        org.json.JSONObject().put(
            "client",
            org.json.JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", WEB_VERSION)
                .put("hl", "en")
                .put("gl", "US")
        )

    /**
     * POST to an InnerTube endpoint on www.youtube.com, attaching cookies and
     * SAPISIDHASH when logged in. Returns the raw response body or null on failure.
     */
    private fun postWatchApi(endpoint: String, body: org.json.JSONObject): String? {
        val builder = okhttp3.Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("X-Origin", "https://www.youtube.com")

        val cookies = sessionManager.getCookies()
        if (!cookies.isNullOrBlank()) {
            builder.addHeader("Cookie", cookies)
            YouTubeAuthUtils.getAuthorizationHeader(cookies, "https://www.youtube.com")?.let { auth ->
                builder.addHeader("Authorization", auth)
                builder.addHeader("X-Goog-AuthUser", "0")
            }
        }

        return try {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    android.util.Log.w("YouTubeRepo", "watch api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "watch api $endpoint failed", e)
            null
        }
    }

    /**
     * Fetch like count/status, subscription state and the comments entry token
     * for a video. likeStatus/isSubscribed are only meaningful when logged in.
     */
    suspend fun getVideoEngagement(videoId: String): VideoEngagement? = withContext(Dispatchers.IO) {
        try {
            val root = fetchWatchNextRoot(videoId) ?: return@withContext null
            parseEngagementFromWatchNext(videoId, root)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getVideoEngagement failed", e)
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
                    android.util.Log.w("YouTubeRepo", "engagement parse failed for $videoId", e)
                    null
                },
                updatedVideoItem = parseVideoMetadataFromWatchNext(videoId, root, baseVideo),
                relatedVideos = parseRelatedFromWatchNext(root)
            )
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getWatchNextData failed", e)
            WatchNextData(null, null, emptyList())
        }
    }

    private fun fetchWatchNextRoot(videoId: String): org.json.JSONObject? {
        val body = org.json.JSONObject()
            .put("context", webContext())
            .put("videoId", videoId)
        val raw = postWatchApi("next", body) ?: return null
        return org.json.JSONObject(raw)
    }

    /**
     * Enriched video metadata from a watch-next response, layered over
     * [baseVideo] (feed items lack description, channel avatar and subscriber
     * count). Shapes verified against the live API July 2026:
     * videoPrimaryInfoRenderer (title, viewCount.videoViewCountRenderer,
     * relativeDateText) and videoSecondaryInfoRenderer (owner.videoOwnerRenderer,
     * attributedDescription.content).
     */
    private fun parseVideoMetadataFromWatchNext(
        videoId: String,
        root: org.json.JSONObject,
        baseVideo: VideoItem?
    ): VideoItem? {
        return try {
            val primaries = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "videoPrimaryInfoRenderer", primaries)
            val primary = primaries.firstOrNull()

            val secondaries = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "videoSecondaryInfoRenderer", secondaries)
            val secondaryInfo = secondaries.firstOrNull()

            if (primary == null && secondaryInfo == null) return baseVideo

            val viewCountRenderer = primary?.optJSONObject("viewCount")
                ?.optJSONObject("videoViewCountRenderer")
            val viewCount = getRunText(viewCountRenderer?.optJSONObject("shortViewCount"))
                ?.takeIf { it.isNotBlank() }
                ?: getRunText(viewCountRenderer?.optJSONObject("viewCount"))?.takeIf { it.isNotBlank() }
            val uploadedDate = getRunText(primary?.optJSONObject("relativeDateText"))
                ?.takeIf { it.isNotBlank() }
                ?: getRunText(primary?.optJSONObject("dateText"))?.takeIf { it.isNotBlank() }

            val owner = secondaryInfo?.optJSONObject("owner")?.optJSONObject("videoOwnerRenderer")
            val channelId = owner?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?.takeIf { it.isNotBlank() }
            val avatarThumbs = owner?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val channelIconUrl = avatarThumbs
                ?.optJSONObject(avatarThumbs.length() - 1)?.optString("url")
                ?.takeIf { it.isNotBlank() }
            val subscriberCount = getRunText(owner?.optJSONObject("subscriberCountText"))
                ?.takeIf { it.isNotBlank() }
            val description = secondaryInfo?.optJSONObject("attributedDescription")
                ?.optString("content")?.takeIf { it.isNotBlank() }

            VideoItem(
                videoId = videoId,
                title = getRunText(primary?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                    ?: baseVideo?.title ?: "Unknown",
                channelName = getRunText(owner?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                    ?: baseVideo?.channelName ?: "Unknown",
                channelId = channelId ?: baseVideo?.channelId,
                channelIconUrl = channelIconUrl ?: baseVideo?.channelIconUrl,
                thumbnailUrl = baseVideo?.thumbnailUrl
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                duration = baseVideo?.duration ?: 0L,
                viewCount = viewCount ?: baseVideo?.viewCount ?: "",
                uploadedDate = uploadedDate ?: baseVideo?.uploadedDate,
                isLive = baseVideo?.isLive ?: false,
                description = description ?: baseVideo?.description,
                subscriberCount = subscriberCount ?: baseVideo?.subscriberCount
            )
        } catch (e: Exception) {
            android.util.Log.w("YouTubeRepo", "watch-next metadata parse failed for $videoId", e)
            baseVideo
        }
    }

    private fun parseEngagementFromWatchNext(
        videoId: String,
        root: org.json.JSONObject
    ): VideoEngagement {
            // Like count + user's like status live in frameworkUpdates entities
            val likeCounts = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "likeCountEntity", likeCounts)
            var likeCount = likeCounts.firstOrNull()
                ?.optJSONObject("likeCountIfIndifferent")?.optString("content")
                ?.takeIf { it.isNotBlank() }
            if (likeCount == null) {
                // Fallback: the visible title on the like toggle button ("19M")
                val likeButtons = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "segmentedLikeDislikeButtonViewModel", likeButtons)
                val title = likeButtons.firstOrNull()
                    ?.optJSONObject("likeButtonViewModel")?.optJSONObject("likeButtonViewModel")
                    ?.optJSONObject("toggleButtonViewModel")?.optJSONObject("toggleButtonViewModel")
                    ?.optJSONObject("defaultButtonViewModel")?.optJSONObject("buttonViewModel")
                    ?.optString("title")
                // Ignore non-numeric titles like "Like" (count hidden by creator)
                likeCount = title?.takeIf { it.isNotBlank() && it.any { c -> c.isDigit() } }
            }

            val likeStatuses = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "likeStatusEntity", likeStatuses)
            val likeStatus = when (likeStatuses.firstOrNull()?.optString("likeStatus")) {
                "LIKE" -> LikeStatus.LIKE
                "DISLIKE" -> LikeStatus.DISLIKE
                else -> LikeStatus.INDIFFERENT
            }

            val subButtons = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "subscribeButtonRenderer", subButtons)
            val subButton = subButtons.firstOrNull()
            val isSubscribed = subButton?.optBoolean("subscribed", false) ?: false

            val owners = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "videoOwnerRenderer", owners)
            val owner = owners.firstOrNull()
            val channelId = subButton?.optString("channelId")?.takeIf { it.isNotBlank() }
                ?: owner?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
            val subscriberCountText = getRunText(owner?.optJSONObject("subscriberCountText"))

            // Comments entry token: the itemSectionRenderer tagged comment-item-section.
            // Two tokens usually appear; the longest is the full comments panel.
            var commentsToken: String? = null
            val sections = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "itemSectionRenderer", sections)
            for (section in sections) {
                if (section.optString("sectionIdentifier") == "comment-item-section") {
                    val tokens = mutableListOf<String>()
                    findContinuationTokens(section, tokens)
                    val best = tokens.maxByOrNull { it.length }
                    if (best != null && best.length > (commentsToken?.length ?: 0)) {
                        commentsToken = best
                    }
                }
            }

            return VideoEngagement(
                videoId = videoId,
                likeCount = likeCount,
                likeStatus = likeStatus,
                channelId = channelId,
                isSubscribed = isSubscribed,
                subscriberCountText = subscriberCountText,
                commentsToken = commentsToken
            )
    }

    /**
     * Fetch one page of comments (top-level or replies) from a continuation token.
     * Parses the modern commentEntityPayload format (frameworkUpdates mutations).
     */
    suspend fun getCommentsPage(token: String): CommentsPage? = withContext(Dispatchers.IO) {
        try {
            val body = org.json.JSONObject()
                .put("context", webContext())
                .put("continuation", token)
            val raw = postWatchApi("next", body) ?: return@withContext null
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
            android.util.Log.e("YouTubeRepo", "getCommentsPage failed", e)
            null
        }
    }

    /**
     * Decode a URL-encoded, URL-safe base64 InnerTube params blob into a
     * string for substring matching (embedded ids are plain ASCII).
     */
    private fun decodeInnerTubeParams(params: String): String? = try {
        val unescaped = java.net.URLDecoder.decode(params, "UTF-8")
        val bytes = android.util.Base64.decode(unescaped, android.util.Base64.URL_SAFE)
        String(bytes, Charsets.ISO_8859_1)
    } catch (e: Exception) {
        null
    }

    private fun parseCommentEntity(
        id: String,
        entity: org.json.JSONObject,
        viewModel: org.json.JSONObject,
        toolbarStates: Map<String, org.json.JSONObject>,
        repliesToken: String?,
        replyParams: String? = null,
        toolbarSurfaces: Map<String, org.json.JSONObject> = emptyMap()
    ): CommentItem {
        val props = entity.optJSONObject("properties")
        val author = entity.optJSONObject("author")
        val toolbar = entity.optJSONObject("toolbar")
        val toolbarStateKey = props?.optString("toolbarStateKey").orEmpty()
        val toolbarState = toolbarStates[toolbarStateKey]
        val heartState = toolbarState?.optString("heartState")
        val likeState = toolbarState?.optString("likeState")

        // Like/unlike actions come from the comment's toolbar surface entity
        // (signed-in responses only; signed out the commands are empty stubs)
        val surface = toolbarSurfaces[viewModel.optString("toolbarSurfaceKey")]
        fun surfaceAction(command: String): String? =
            surface?.optJSONObject(command)?.let {
                val endpoints = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(it, "performCommentActionEndpoint", endpoints)
                endpoints.firstOrNull()?.optString("action")?.takeIf { a -> a.isNotBlank() }
            }

        // Own comments carry a "Delete" item in the surface's three-dot menu
        // (menuCommand -> menuRenderer -> menuNavigationItemRenderer, label is
        // stable because webContext pins hl=en); its confirm-dialog endpoint
        // holds the perform_comment_action delete param. Verified July 2026.
        val deleteParams = surface?.optJSONObject("menuCommand")?.let { menu ->
            val menuItems = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(menu, "menuNavigationItemRenderer", menuItems)
            menuItems.firstOrNull { getRunText(it.optJSONObject("text")) == "Delete" }
                ?.let { item ->
                    val endpoints = mutableListOf<org.json.JSONObject>()
                    findObjectsByKey(item, "performCommentActionEndpoint", endpoints)
                    endpoints.firstOrNull()?.optString("action")?.takeIf { a -> a.isNotBlank() }
                }
        }

        return CommentItem(
            commentId = id,
            text = props?.optJSONObject("content")?.optString("content").orEmpty(),
            author = author?.optString("displayName").orEmpty(),
            authorAvatarUrl = author?.optString("avatarThumbnailUrl")?.takeIf { it.isNotBlank() },
            publishedTime = props?.optString("publishedTime").orEmpty(),
            likeCount = toolbar?.optString("likeCountNotliked").orEmpty().trim(),
            replyCount = toolbar?.optString("replyCount").orEmpty().trim(),
            isPinned = viewModel.has("pinnedText"),
            isHearted = heartState == "TOOLBAR_HEART_STATE_HEARTED",
            isCreator = author?.optBoolean("isCreator", false) ?: false,
            isVerified = author?.optBoolean("isVerified", false) ?: false,
            repliesToken = repliesToken,
            replyParams = replyParams,
            likeCountLiked = toolbar?.optString("likeCountLiked").orEmpty().trim(),
            isLiked = likeState == "TOOLBAR_LIKE_STATE_LIKED",
            likeParams = surfaceAction("likeCommand"),
            unlikeParams = surfaceAction("unlikeCommand"),
            deleteParams = deleteParams
        )
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
            .put("context", webContext())
            .put("target", org.json.JSONObject().put("videoId", videoId))
        postWatchApi(endpoint, body) != null
    }

    /**
     * Subscribe to / unsubscribe from a channel. Requires login and a
     * canonical UC... channelId (from getVideoEngagement).
     */
    suspend fun setSubscribed(channelId: String, subscribe: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        val body = org.json.JSONObject()
            .put("context", webContext())
            .put("channelIds", org.json.JSONArray().put(channelId))
        postWatchApi(endpoint, body) != null
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

    private fun musicContext(): org.json.JSONObject =
        org.json.JSONObject().put(
            "client",
            org.json.JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", WEB_REMIX_VERSION)
                .put("hl", "en")
                .put("gl", "US")
        )

    /**
     * POST to an InnerTube endpoint on music.youtube.com with cookies and a
     * music-origin SAPISIDHASH (the hash is per-origin — a www.youtube.com
     * hash is rejected here). Returns the raw body or null on failure.
     */
    private fun postMusicApi(endpoint: String, body: org.json.JSONObject): String? {
        val cookies = sessionManager.getCookies() ?: return null
        val auth = YouTubeAuthUtils.getAuthorizationHeader(cookies, "https://music.youtube.com")
            ?: return null
        val request = okhttp3.Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Cookie", cookies)
            .addHeader("Authorization", auth)
            .addHeader("User-Agent", BROWSER_USER_AGENT)
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("X-Origin", "https://music.youtube.com")
            .addHeader("X-Goog-AuthUser", "0")
            .build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    android.util.Log.w("YouTubeRepo", "music api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "music api $endpoint failed", e)
            null
        }
    }

    private fun postPlaylistApi(music: Boolean, endpoint: String, body: org.json.JSONObject): String? =
        if (music) postMusicApi(endpoint, body) else postWatchApi(endpoint, body)

    private fun playlistContext(music: Boolean): org.json.JSONObject =
        if (music) musicContext() else webContext()

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

    /**
     * Delete a playlist. playlist/delete only works on playlists the user
     * owns; for saved (someone else's) playlists it fails, so fall back to
     * removing the playlist from the library instead. Requires login.
     */
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
     * Post a new top-level comment. createCommentParams comes from the first
     * comments page (CommentsPage.createCommentParams). Returns the created
     * comment parsed from the response, or null on failure. Requires login.
     */
    suspend fun createComment(createCommentParams: String, text: String): CommentItem? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val body = org.json.JSONObject()
                .put("context", webContext())
                .put("commentText", text)
                .put("createCommentParams", createCommentParams)
            parseCreatedComment(postWatchApi("comment/create_comment", body))
        }

    /**
     * Post a reply to a comment. createReplyParams comes from the parent
     * comment (CommentItem.replyParams). Requires login.
     */
    suspend fun createCommentReply(createReplyParams: String, text: String): CommentItem? =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext null
            val body = org.json.JSONObject()
                .put("context", webContext())
                .put("commentText", text)
                .put("createReplyParams", createReplyParams)
            parseCreatedComment(postWatchApi("comment/create_comment_reply", body))
        }

    /**
     * Execute a comment toolbar action (like/unlike). The action param comes
     * from the comment's toolbar surface (CommentItem.likeParams /
     * unlikeParams, present only on signed-in fetches). Requires login.
     */
    suspend fun performCommentAction(action: String): Boolean = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext false
        val body = org.json.JSONObject()
            .put("context", webContext())
            .put("actions", org.json.JSONArray().put(action))
        val raw = postWatchApi("comment/perform_comment_action", body)
            ?: return@withContext false
        try {
            val results = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(org.json.JSONObject(raw), "actionResult", results)
            results.isEmpty() || results.any { it.optString("status") == "STATUS_SUCCEEDED" }
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Parse the comment entity out of a create_comment / create_comment_reply
     * response. Returns null unless the actionResult reports success.
     */
    private fun parseCreatedComment(raw: String?): CommentItem? {
        if (raw == null) return null
        return try {
            val root = org.json.JSONObject(raw)
            val results = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "actionResult", results)
            if (results.none { it.optString("status") == "STATUS_SUCCEEDED" }) return null

            val entities = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "commentEntityPayload", entities)
            val entity = entities.firstOrNull() ?: return null
            val id = entity.optJSONObject("properties")?.optString("commentId")
                ?.takeIf { it.isNotBlank() } ?: return null

            val replyEndpoints = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "createCommentReplyEndpoint", replyEndpoints)
            val replyParams = replyEndpoints.firstOrNull()
                ?.optString("createReplyParams")?.takeIf { it.isNotBlank() }

            // The create response also carries the comment's viewModel and its
            // toolbar surface — passing them through gives the fresh comment
            // its like/delete params immediately (no page reload needed)
            val viewModels = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "commentViewModel", viewModels)
            val viewModel = viewModels
                .map { it.optJSONObject("commentViewModel") ?: it }
                .firstOrNull { it.optString("commentId") == id }
                ?: org.json.JSONObject()

            val surfaces = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "engagementToolbarSurfaceEntityPayload", surfaces)
            val toolbarSurfaces = surfaces
                .filter { it.optString("key").isNotBlank() }
                .associateBy { it.optString("key") }

            parseCommentEntity(
                id, entity,
                viewModel = viewModel,
                toolbarStates = emptyMap(),
                repliesToken = null,
                replyParams = replyParams,
                toolbarSurfaces = toolbarSurfaces
            )
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "parseCreatedComment failed", e)
            null
        }
    }

    /**
     * Record a regular (non-music) video playback into the user's YouTube
     * watch history. Mirrors reportPlayback but uses the WEB client against
     * www.youtube.com: the WEB_REMIX/music flow does not register plain
     * videos. Requires login.
     */
    suspend fun reportVideoPlayback(videoId: String) = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext
        try {
            val cpn = generateCpn()
            val raw = postWatchApi(
                "player",
                org.json.JSONObject()
                    .put("context", webContext())
                    .put("videoId", videoId)
                    .put("cpn", cpn)
            ) ?: return@withContext
            val baseUrl = org.json.JSONObject(raw)
                .optJSONObject("playbackTracking")
                ?.optJSONObject("videostatsPlaybackUrl")
                ?.optString("baseUrl")
            if (baseUrl.isNullOrEmpty()) {
                android.util.Log.w("YouTubeRepo", "No videostatsPlaybackUrl for $videoId")
                return@withContext
            }
            val trackingUrl = buildString {
                append(baseUrl)
                if (!baseUrl.contains("cpn=")) {
                    append(if (baseUrl.contains("?")) "&" else "?")
                    append("cpn=$cpn")
                }
                append("&ver=2&c=WEB")
            }
            val cookies = sessionManager.getCookies() ?: return@withContext
            val builder = okhttp3.Request.Builder()
                .url(trackingUrl)
                .get()
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .addHeader("Origin", "https://www.youtube.com")
                .addHeader("Referer", "https://www.youtube.com/watch?v=$videoId")
                .addHeader("Cookie", cookies)
            YouTubeAuthUtils.getAuthorizationHeader(cookies, "https://www.youtube.com")?.let {
                builder.addHeader("Authorization", it)
                builder.addHeader("X-Goog-AuthUser", "0")
            }
            okHttpClient.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.d("YouTubeRepo", "Video history sync SUCCESS for $videoId")
                } else {
                    android.util.Log.w("YouTubeRepo", "Video history sync failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error in reportVideoPlayback", e)
        }
    }

    /**
     * The user's subscriptions feed (FEsubscriptions): latest uploads from
     * all subscribed channels, newest first. Requires login.
     */
    suspend fun getSubscriptionsFeed(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webContext()).put("browseId", "FEsubscriptions")
            ) ?: return@withContext emptyList()
            parseVideosFromYouTubeJson(raw)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getSubscriptionsFeed failed", e)
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
            var response = postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webContext()).put("browseId", "FEchannels")
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
                    val subscriberCount = renderer.optJSONObject("videoCountText")
                        ?.optString("simpleText")?.takeIf { it.isNotBlank() }
                    channels.add(SubscribedChannel(channelId, name, avatarUrl, subscriberCount))
                }
                val token = if (renderers.isNotEmpty()) extractContinuationToken(response) else null
                response = token?.let {
                    postWatchApi(
                        "browse",
                        org.json.JSONObject().put("context", webContext()).put("continuation", it)
                    )
                }
                pages++
            }
            channels.distinctBy { it.channelId }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getSubscribedChannels failed", e)
            emptyList()
        }
    }

    /**
     * Latest uploads of a channel (Videos tab browse). Channel-page lockups
     * omit the channel row, so the caller's channel identity is stitched in.
     */
    suspend fun getChannelVideos(channel: SubscribedChannel): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webContext())
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
                // On the channel page the first metadata row is "N views • date",
                // which the generic parser reads as the channel name.
                val viewCount = if (parsed.viewCount.isBlank() &&
                    (parsed.channelName.contains("view", ignoreCase = true) ||
                        parsed.channelName.contains("watching", ignoreCase = true))
                ) parsed.channelName else parsed.viewCount
                parsed.copy(
                    channelName = channel.name,
                    channelId = channel.channelId,
                    channelIconUrl = channel.avatarUrl ?: parsed.channelIconUrl,
                    viewCount = viewCount
                )
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getChannelVideos failed", e)
            emptyList()
        }
    }

    /**
     * The user's notification inbox (new uploads from subscribed channels,
     * replies, etc.). First page only. Requires login.
     */
    suspend fun getNotifications(): List<NotificationItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val raw = postWatchApi(
                "notification/get_notification_menu",
                org.json.JSONObject()
                    .put("context", webContext())
                    .put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
            ) ?: return@withContext emptyList()
            val root = org.json.JSONObject(raw)
            val renderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "notificationRenderer", renderers)
            renderers.mapNotNull { renderer ->
                val message = renderer.optJSONObject("shortMessage")?.optString("simpleText")
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                fun lastThumb(key: String): String? {
                    val thumbs = renderer.optJSONObject(key)?.optJSONArray("thumbnails") ?: return null
                    var url = thumbs.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
                        ?.optString("url")?.takeIf { it.isNotBlank() }
                    if (url?.startsWith("//") == true) url = "https:$url"
                    return url
                }
                NotificationItem(
                    message = message,
                    sentTime = renderer.optJSONObject("sentTimeText")?.optString("simpleText").orEmpty(),
                    channelAvatarUrl = lastThumb("thumbnail"),
                    videoThumbnailUrl = lastThumb("videoThumbnail"),
                    videoId = renderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")?.optString("videoId")
                        ?.takeIf { it.isNotBlank() },
                    isRead = renderer.optBoolean("read", false)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "getNotifications failed", e)
            emptyList()
        }
    }
}
