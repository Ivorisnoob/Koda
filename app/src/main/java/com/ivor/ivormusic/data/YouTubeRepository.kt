package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
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
    private val videoHistoryRepository by lazy { VideoHistoryRepository(context) }

    companion object {
        private const val YT_MUSIC_BASE_URL = "https://music.youtube.com"
        @Volatile private var isInitialized = false
        private val newPipeInitLock = Any()

        // ServiceList eagerly constructs every extractor NewPipe supports. Koda
        // only uses YouTube, so keep the equivalent service instance directly
        // and let R8 discard the SoundCloud, PeerTube, Bandcamp and MediaCCC
        // implementations.
        private val youtubeService = YoutubeService(0)
        
        // Content filters for YouTube Music search
        const val FILTER_SONGS = "music_songs"
        const val FILTER_VIDEOS = "music_videos"
        const val FILTER_ALBUMS = "music_albums"
        const val FILTER_PLAYLISTS = "music_playlists"
        const val FILTER_ARTISTS = "music_artists"
        
        // Regular YouTube filters
        const val FILTER_YOUTUBE_VIDEOS = "videos"
        const val FILTER_YOUTUBE_PLAYLISTS = "playlists"
        const val FILTER_YOUTUBE_CHANNELS = "channels"
        
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

        // Current InnerTube client versions. YouTube rejects clients older than
        // a few months; bump these together when refreshing.
        // Re-derived from the bootstrap HTML of music.youtube.com and
        // www.youtube.com in September 2026 (INNERTUBE_CLIENT_VERSION), and
        // both confirmed live: WEB_REMIX answers a VL<playlistId> browse with a
        // full musicPlaylistShelfRenderer, WEB answers /next.
        private const val WEB_REMIX_VERSION = "1.20260901.12.00"
        private const val WEB_VERSION = "2.20260903.01.00"

        // browse params selecting a channel's Videos tab (protobuf: "videos")
        private const val CHANNEL_VIDEOS_TAB_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"

        // How many subscribed channels the local feed fetches at once. The
        // local feed costs one request per channel, so this is the only thing
        // standing between a 300-subscription refresh and 300 simultaneous
        // sockets - which mobile radios handle badly and which looks like a
        // scrape from the other end. Six keeps a large refresh moving without
        // starving whatever else the app is loading.
        private const val FEED_CONCURRENCY = 6

        // Two different caps, doing two different jobs.
        //
        // The budget is what playback actually feels: it bounds how long stream
        // resolution waits on NewPipe before falling back, and is deliberately
        // small enough to leave the InnerTube chain room inside MusicService's
        // own resolution timeout. A NewPipe path that merely takes too long
        // must degrade to the fallback, not spend the whole budget and then
        // resolve to nothing.
        //
        // The per-request cap is only a backstop, and is generous on purpose:
        // this same downloader serves search, playlists and channel pages,
        // whose responses are far larger than a /player call and which had no
        // wall-clock cap at all before. Tightening it to the budget would turn
        // a slow connection into failed searches to fix a playback problem the
        // budget already fixes.
        //
        // The budget is sized against MusicService.RESOLVE_TIMEOUT_MS (20s),
        // which discards - and therefore skips - anything slower: 8s here
        // leaves the 8s-capped direct /player chain room to succeed inside it.
        // The two are a pair; moving one alone reopens the skip.
        private const val NEWPIPE_REQUEST_TIMEOUT_SECONDS = 20L
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

        // YouTube's own account verdict, in the responseContext tracking params
        // of every InnerTube response: {"key":"logged_in","value":"0"|"1"}.
        // Tolerant of the pretty-printed spacing so it matches either form.
        private val LOGGED_IN_TRACKING_PARAM =
            Regex("\"logged_in\"\\s*,\\s*\"value\"\\s*:\\s*\"([01])\"")

        // ANDROID_VR is the client for audio extraction in 2026: it returns
        // direct, unobfuscated stream URLs (no signatureCipher to decrypt) and
        // needs no player JS.
        //
        // It no longer escapes GVS PO-Token enforcement, though, and nothing
        // does. Measured August 2026: googlevideo serves the first ~1.1 MiB of
        // a stream and answers 403 for every byte past it, on ANDROID_VR and
        // IOS alike. The verdict is keyed on the visitorData the /player call
        // carried, not on the video or the client: it is stable for a given
        // token and roughly half of freshly minted tokens are refused. So a
        // single unlucky mint breaks every uncached stream for the whole
        // VISITOR_DATA_TTL_MS, which is what refreshVisitorDataAfterPlaybackFailure
        // exists to undo. Do not read a 403 here as a UA or client problem.
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

        // These match the clients used by NewPipe Extractor v0.26.5. Stream
        // URLs carry the client name in `c=` and GVS can reject a request whose
        // User-Agent belongs to a different client family.
        private const val NEWPIPE_ANDROID_USER_AGENT =
            "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"
        private const val VISIONOS_CLIENT_VERSION = "1.02"
        private const val VISIONOS_CLIENT_ID = 101
        private const val VISIONOS_USER_AGENT =
            "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS " +
                "25_6_0 like Mac OS X; GB)"
        private const val NEWPIPE_VISIONOS_USER_AGENT = VISIONOS_USER_AGENT

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
                    NEWPIPE_ANDROID_USER_AGENT
                "VISIONOS" -> NEWPIPE_VISIONOS_USER_AGENT
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
        /**
         * Shared HTTP cache for the plain GETs this app makes - the channel
         * Atom feeds above all, which carry ETag/Last-Modified and are
         * re-fetched in full on every subscriptions refresh and by the
         * six-hourly upload check. With a cache those become 304s.
         *
         * **Companion-level because it must be, not merely because it is
         * cheaper.** OkHttp's Cache is a DiskLruCache holding an exclusive
         * lock on its directory, and this app builds a YouTubeRepository per
         * ViewModel. A per-instance cache would mean several Cache objects on
         * one directory, which is corruption, not contention. One instance,
         * shared by every client built from it.
         *
         * InnerTube calls are POSTs and are never cached by OkHttp, so this
         * cannot serve a stale feed or a stale playlist.
         */
        private const val HTTP_CACHE_DIR_NAME = "yt_http_cache"
        private const val HTTP_CACHE_BYTES = 10L * 1024 * 1024
        @Volatile private var sharedHttpCache: okhttp3.Cache? = null
        private val httpCacheLock = Any()

        // Repository instances retain their own cookie jars and Local Only
        // interceptors, but sockets and dispatcher threads are transport
        // resources rather than account state. Sharing both avoids building a
        // fresh connection pool and executor for every ViewModel.
        private val sharedHttpDispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 8
        }
        private val sharedConnectionPool = ConnectionPool(
            maxIdleConnections = 8,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES,
        )

        private fun httpCache(context: Context): okhttp3.Cache? {
            sharedHttpCache?.let { return it }
            return synchronized(httpCacheLock) {
                sharedHttpCache ?: try {
                    okhttp3.Cache(
                        java.io.File(
                            context.applicationContext.cacheDir,
                            HTTP_CACHE_DIR_NAME,
                        ),
                        HTTP_CACHE_BYTES,
                    ).also { sharedHttpCache = it }
                } catch (e: Exception) {
                    // A cache is an optimisation; losing it must not stop the
                    // app making requests.
                    KLog.w("YouTubeRepository", "HTTP cache unavailable: ${e.message}")
                    null
                }
            }
        }

        @Volatile private var cachedVisitorData: String? = null
        @Volatile private var visitorDataFetchedAt: Long = 0L
        private val visitorDataMutex = kotlinx.coroutines.sync.Mutex()
        private const val VISITOR_DATA_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours

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
            cachedVisitorData = null
            visitorDataFetchedAt = 0L
            VideoStreamResolutionCache.clear()
            val editor = context.applicationContext
                .getSharedPreferences("ivor_visitor_data", Context.MODE_PRIVATE)
                .edit().remove("visitor_data").remove("visitor_data_at")
            if (commitNow) editor.commit() else editor.apply()
        }
    }

    // Local-only kill-switch: checked per request so flipping the setting
    // needs no restart. newBuilder() copies interceptors, so this also guards
    // streamResolveClient and the NewPipe downloader (same client instance).
    private val okHttpClient = OkHttpClient.Builder()
        .dispatcher(sharedHttpDispatcher)
        .connectionPool(sharedConnectionPool)
        .addInterceptor { chain ->
            if (ThemePreferences.isLocalOnly(context)) {
                throw java.io.IOException("Local only mode is on: network disabled")
            }
            chain.proceed(chain.request())
        }
        // Folds Google's rotated session cookies back into storage. Without it
        // the login snapshot goes stale on its own and every authenticated
        // endpoint quietly answers as signed out. See SessionCookieJar.
        .cookieJar(SessionCookieJar(sessionManager))
        .apply { httpCache(context)?.let { cache(it) } }
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

    // The client NewPipe's downloader runs on. It needs its own callTimeout for
    // the same reason streamResolveClient has one, and the reason is sharper
    // here: NewPipe's Downloader.execute() is a *blocking* call, and one
    // extraction is many of them in sequence. [verified September 2026: a
    // single fetchPage() of an ordinary track made eight requests — an
    // ANDROID visitor_id mint, reel/reel_item_watch, a visionOS visitor_id and
    // /player, sw.js, a WEB visitor_id, the WEB metadata /player and /next.]
    // On the bare 30s connect/read budget of okHttpClient that is minutes of
    // worst case, and because the thread is blocked inside execute() no
    // coroutine timeout above it can cut it short. A per-request wall-clock cap
    // is the only thing that bounds one of those requests at all; what bounds
    // the *wait* is resolveAudioUrlWithinBudget, which is where playback
    // responsiveness actually comes from.
    private val newPipeClient = okHttpClient.newBuilder()
        .callTimeout(NEWPIPE_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    // Blocking NewPipe extractions are started here rather than in the caller's
    // scope. A coroutine timeout can only free the *caller*: the extraction
    // itself is uninterruptible until newPipeClient's callTimeout fires, and a
    // child job would keep the parent's coroutineScope waiting for exactly the
    // work it is trying to abandon. Detaching it is what lets the InnerTube
    // fallback start on time. SupervisorJob so one failed extraction cannot
    // cancel the scope every later one needs.
    private val newPipeScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO,
    )

    private fun getRandomUserAgent(): String {
        return BROWSER_USER_AGENT
    }

    init {
        initializeNewPipe()
    }

    /**
     * Forget everything cached in this instance that belonged to the previous
     * profile. The process-wide half is [invalidateSessionScopedCaches].
     */
    fun clearSessionScopedInstanceCaches() {
        searchExtractorCache.clear()
        searchNextPageCache.clear()
        videoSearchNextPageCache.clear()
    }

    private fun initializeNewPipe() {
        if (isInitialized) return
        synchronized(newPipeInitLock) {
            if (!isInitialized) {
                try {
                    NewPipe.init(NewPipeDownloaderImpl(newPipeClient, sessionManager))
                } catch (_: Exception) {
                    // NewPipe may already have been initialized by another
                    // process entry point. Its singleton is still usable.
                }
                isInitialized = true
            }
        }
    }

    // Cache extractors for pagination
    private val searchExtractorCache = mutableMapOf<String, org.schabi.newpipe.extractor.search.SearchExtractor>()

    // The next continuation Page per query. Advanced by every searchNext call
    // so repeated "Load More" presses walk pages 2, 3, 4... instead of
    // refetching page 2 forever. A null value means the query is exhausted.
    private val searchNextPageCache = mutableMapOf<String, Page?>()

    // Same pair again for video-mode search, keyed by the date-filtered query
    // so switching filters starts its own pagination rather than continuing
    // the previous one. Only the relevance-ordered (NewPipe) path populates
    // these; sorted searches go through InnerTube and do not paginate.
    private val videoSearchExtractorCache =
        mutableMapOf<String, org.schabi.newpipe.extractor.search.SearchExtractor>()
    private val videoSearchNextPageCache = mutableMapOf<String, Page?>()

    /**
     * Search for songs on YouTube Music.
     * @param query The search query
     * @param filter The content filter (FILTER_SONGS, FILTER_ALBUMS, etc.)
     * @return List of songs matching the query
     */
    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song> = withContext(Dispatchers.IO) {
        try {
            // YouTube Music search often uses the search extractor with specific filters
            val searchExtractor = youtubeService.getSearchExtractor(query, listOf(filter), "")
            searchExtractor.fetchPage()
            
            // Cache for pagination
            searchExtractorCache[query] = searchExtractor
            searchNextPageCache[query] =
                if (searchExtractor.initialPage.hasNextPage()) searchExtractor.initialPage.nextPage else null

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
            val searchExtractor = youtubeService.getSearchExtractor(query, listOf(FILTER_PLAYLISTS), "")
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
            val searchExtractor = youtubeService.getSearchExtractor(query, listOf(FILTER_ALBUMS), "")
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
            val searchExtractor = youtubeService.getSearchExtractor(query, listOf(FILTER_ARTISTS), "")
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

            KLog.d(
                "YouTubeRepo",
                "Artist $artistId: ${songs.size} songs, ${albums.size} releases"
            )
            Pair(songs.distinctBy { it.id }, albums.distinctBy { it.id })
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching artist details", e)
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
            KLog.e("YouTubeRepo", "Error fetching album $browseId", e)
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
            KLog.e("YouTubeRepo", "browseMusic($browseId) failed", e)
            null
        }
    }

    /**
     * Fetch next page of results for a previous query.
     */
    suspend fun searchNext(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val extractor = searchExtractorCache[query] ?: return@withContext emptyList()

            // Continuation cursor from the previous page; null = exhausted
            val pageInfo = searchNextPageCache[query] ?: return@withContext emptyList()

            val nextPage = extractor.getPage(pageInfo)
            searchNextPageCache[query] = if (nextPage.hasNextPage()) nextPage.nextPage else null

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
     * @return Result containing stream URL or error
     */
    suspend fun getStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()

        // Primary: NewPipe's maintained Android/visionOS client chain. Direct
        // ANDROID_VR URLs can start successfully and then hit GVS's progressive
        // byte ceiling on long media, which is the same failure that moved video
        // playback to NewPipe first. Audio must use the maintained path too or a
        // long song can fail only after it has already been playing for a while.
        //
        // Bounded, because being slow here used to be indistinguishable from
        // failing: the extraction is eight blocking requests (see newPipeClient)
        // and the caller's own timeout could not interrupt one, so a stalled
        // primary path ran out MusicService's whole resolution budget and the
        // song resolved to an error URI and was skipped - with a working
        // fallback sitting unused behind it.
        val newPipeUrl = resolveAudioUrlWithinBudget(videoId)
        if (!newPipeUrl.isNullOrEmpty()) {
            KLog.i(
                "YouTubeRepository",
                "Resolve[NewPipe] OK videoId=$videoId dt=${System.currentTimeMillis() - startMs}ms",
            )
            return@withContext Result.success(newPipeUrl)
        }

        // Last-resort fallback: the older direct /player chain. It can still
        // cover a client-specific NewPipe extraction failure, but its progressive
        // URLs must not be the normal path for the reason above.
        val innerTubeUrl = resolvePlayerStreamingData(videoId)?.let { pickAudioStreamUrl(videoId, it) }
        val dt = System.currentTimeMillis() - startMs
        if (!innerTubeUrl.isNullOrEmpty()) {
            KLog.i(
                "YouTubeRepository",
                "Resolve[InnerTube fallback] OK videoId=$videoId dt=${dt}ms",
            )
            Result.success(innerTubeUrl)
        } else {
            KLog.e(
                "YouTubeRepository",
                "Resolve FAIL videoId=$videoId all clients exhausted (NewPipe + InnerTube) dt=${dt}ms",
            )
            Result.failure(Exception("No audio stream found for $videoId"))
        }
    }

    /**
     * Resolve an AAC/M4A audio-only stream for a file download.
     *
     * Playback may consume Opus/WebM or a muxed video fallback because Media3
     * only needs a playable track. Downloads are published as `.m4a` and then
     * tagged, so accepting either fallback would put bytes from the wrong
     * container behind an M4A filename and make metadata writing unreliable.
     */
    suspend fun getDownloadAudioStreamUrl(videoId: String): Result<String> =
        withContext(Dispatchers.IO) {
            val newPipeUrl = resolveM4aAudioUrlViaNewPipe(videoId)
            if (!newPipeUrl.isNullOrBlank()) return@withContext Result.success(newPipeUrl)

            val innerTubeUrl = resolvePlayerStreamingData(videoId)
                ?.let(::pickM4aAudioStreamUrl)
            if (!innerTubeUrl.isNullOrBlank()) return@withContext Result.success(innerTubeUrl)

            Result.failure(Exception("No AAC/M4A audio stream found for $videoId"))
        }

    /**
     * Run [resolveAudioUrlViaNewPipe] with a wall-clock budget the caller can
     * actually rely on.
     *
     * NewPipe's `fetchPage()` is blocking and uninterruptible, so wrapping it in
     * `withTimeout` where it runs achieves nothing: `coroutineScope` will not
     * return until the blocking child returns, timeout or no timeout. The work
     * therefore starts on [newPipeScope], which is not a child of the caller,
     * and only the *await* is bounded. When the budget expires the caller moves
     * on to the InnerTube fallback while the extraction finishes in the
     * background, capped by [newPipeClient]'s own per-request timeout.
     *
     * Abandoning it rather than cancelling it is deliberate: nothing is written
     * outside the returned value, and a resolution that arrives late is simply
     * discarded.
     */
    private suspend fun resolveAudioUrlWithinBudget(videoId: String): String? {
        val extraction = newPipeScope.async { resolveAudioUrlViaNewPipe(videoId) }
        return try {
            kotlinx.coroutines.withTimeoutOrNull(NEWPIPE_STREAM_BUDGET_MS) { extraction.await() }
                ?: run {
                    // Cancelling cannot interrupt a thread already inside
                    // fetchPage() - only newPipeClient's per-request cap ends
                    // that - but it marks the work abandoned so nothing runs
                    // after it and a queued extraction never starts at all.
                    extraction.cancel()
                    KLog.w(
                        "YouTubeRepository",
                        "Resolve[NewPipe] over ${NEWPIPE_STREAM_BUDGET_MS}ms budget " +
                            "videoId=$videoId, falling back to InnerTube",
                    )
                    null
                }
        } catch (e: CancellationException) {
            // The caller went away rather than the budget expiring. Abandon the
            // extraction the same way and let the cancellation propagate.
            extraction.cancel()
            throw e
        }
    }

    /**
     * Resolve an audio stream URL through NewPipe's maintained client chain.
     * Applies the same per-network music quality policy as [pickAudioStreamUrl]
     * (NewPipe's averageBitrate is in kbps), and falls back to a muxed
     * video+audio URL when no audio-only URL exists. The resulting googlevideo
     * URL is tagged with its issuing client, so playback selects the matching
     * user agent through [uaForPlaybackUri].
     */
    private suspend fun resolveAudioUrlViaNewPipe(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val streamExtractor = youtubeService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()

            // Generated manifest content shares the Stream model with direct
            // URLs. Only the latter can be handed to Media3 as a URI.
            val audioStreams = originalTrackAudioStreams(
                streamExtractor.audioStreams.filter { it.isUrl },
            )
            pickAudioStreamForCurrentQuality(audioStreams)
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { return@withContext it }

            // No audio-only stream — a muxed stream still carries an audio track.
            streamExtractor.videoStreams
                .asSequence()
                .filter { it.isUrl }
                .mapNotNull { it.content?.takeIf(String::isNotBlank) }
                .firstOrNull()
        } catch (e: CancellationException) {
            // The budget in resolveAudioUrlWithinBudget expired, or the caller
            // went away. Either way this is not an extraction failure and must
            // not be reported as one.
            throw e
        } catch (e: Exception) {
            KLog.w(
                "YouTubeRepository",
                "Resolve[NewPipe] failed videoId=$videoId: ${e.message}",
            )
            null
        }
    }

    private suspend fun resolveM4aAudioUrlViaNewPipe(videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val extractor = youtubeService.getStreamExtractor(
                    "https://www.youtube.com/watch?v=$videoId"
                )
                extractor.fetchPage()

                val m4aStreams = originalTrackAudioStreams(
                    extractor.audioStreams.filter { it.isUrl }
                ).filter { stream ->
                    stream.format?.suffix.equals("m4a", ignoreCase = true) ||
                        stream.codec?.contains("mp4a", ignoreCase = true) == true
                }
                pickAudioStreamForCurrentQuality(m4aStreams)
                    ?.content
                    ?.takeIf(String::isNotBlank)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.w(
                    "YouTubeRepository",
                    "Resolve[M4A/NewPipe] failed videoId=$videoId: ${e.message}"
                )
                null
            }
        }

    private fun pickAudioStreamForCurrentQuality(streams: List<AudioStream>): AudioStream? =
        when (ThemePreferences.currentMusicQuality(context)) {
            ThemePreferences.MUSIC_QUALITY_LOW ->
                streams.minByOrNull { it.averageBitrate }
            ThemePreferences.MUSIC_QUALITY_NORMAL ->
                streams.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
            else -> streams.maxByOrNull { it.averageBitrate }
        }

    private fun pickM4aAudioStreamUrl(streamingData: org.json.JSONObject): String? {
        val formats = streamingData.optJSONArray("adaptiveFormats") ?: return null
        val candidates = (0 until formats.length())
            .mapNotNull(formats::optJSONObject)
            .filter { format ->
                val mime = format.optString("mimeType")
                mime.startsWith("audio/mp4") &&
                    (mime.contains("mp4a", ignoreCase = true) || mime.contains("aac", ignoreCase = true)) &&
                    format.optString("url").isNotBlank()
            }
        val originals = originalTrackAudioFormats(candidates)
        val selected = when (ThemePreferences.currentMusicQuality(context)) {
            ThemePreferences.MUSIC_QUALITY_LOW -> originals.minByOrNull { it.optInt("bitrate") }
            ThemePreferences.MUSIC_QUALITY_NORMAL ->
                originals.minByOrNull { kotlin.math.abs(it.optInt("bitrate") - 128_000) }
            else -> originals.maxByOrNull { it.optInt("bitrate") }
        }
        return selected?.optString("url")?.takeIf(String::isNotBlank)
    }

    /**
     * Keep YouTube's original soundtrack when it exposes alternate dubbed,
     * descriptive, or secondary audio tracks.
     *
     * Older/single-track responses do not carry `audioTrackType`; those
     * untyped streams are safe as the compatibility fallback. If YouTube
     * explicitly labels every stream as non-original, return no audio-only
     * stream and let the caller use its muxed fallback instead of knowingly
     * selecting a dub.
     */
    private fun originalTrackAudioStreams(streams: List<AudioStream>): List<AudioStream> {
        val originals = streams.filter { it.audioTrackType == AudioTrackType.ORIGINAL }
        if (originals.isNotEmpty()) return originals
        return streams.filter { it.audioTrackType == null }
    }

    /** The direct InnerTube equivalent of [originalTrackAudioStreams]. */
    private fun originalTrackAudioFormats(
        formats: List<org.json.JSONObject>
    ): List<org.json.JSONObject> {
        val typed = formats.map { it to audioTrackType(it) }
        val originals = typed.filter { it.second == AudioTrackType.ORIGINAL }.map { it.first }
        if (originals.isNotEmpty()) return originals
        return typed.filter { it.second == null }.map { it.first }
    }

    private fun audioTrackType(format: org.json.JSONObject): AudioTrackType? {
        val xtags = format.optString("xtags").takeIf { it.isNotBlank() } ?: return null
        return runCatching { YoutubeParsingHelper.extractAudioTrackType(xtags) }.getOrNull()
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
     * Warm the visitorData cache off the critical path, so the first
     * playback of a session doesn't pay for the mint (or the bootstrap
     * fallback download) before its /player call can go out.
     */
    suspend fun prefetchVisitorData() {
        try {
            getVisitorData()
        } catch (e: Exception) {
            KLog.w("YouTubeRepository", "visitorData prefetch failed: ${e.message}")
        }
    }

    private suspend fun getVisitorData(): String {
        val now = System.currentTimeMillis()
        cachedVisitorData?.let { if (now - visitorDataFetchedAt < VISITOR_DATA_TTL_MS) return it }
        return visitorDataMutex.withLock {
            val nowInner = System.currentTimeMillis()
            cachedVisitorData?.let { if (nowInner - visitorDataFetchedAt < VISITOR_DATA_TTL_MS) return it }
            // A token persisted by an earlier process start stays valid for
            // the full TTL: adopt it instead of re-minting on every app
            // start, which put a network fetch on the first resolution of
            // each session.
            // A negative age means the clock moved backwards since the mint
            // (timezone/NTP correction, manual change). Treat that as expired
            // rather than "forever fresh", which would pin a token for good.
            val persistedAt = visitorDataPrefs.getLong("visitor_data_at", 0L)
            if (nowInner - persistedAt in 0 until VISITOR_DATA_TTL_MS) {
                loadPersistedVisitorData()?.let {
                    cachedVisitorData = it
                    visitorDataFetchedAt = persistedAt
                    return it
                }
            }
            val fresh = fetchVisitorData()
            if (!fresh.isNullOrEmpty()) {
                cachedVisitorData = fresh
                visitorDataFetchedAt = nowInner
                persistVisitorData(fresh)
                KLog.i("YouTubeRepository", "visitorData refreshed (len=${fresh.length})")
                fresh
            } else {
                // Reuse a previously-good value: this session's, else the last
                // one this install successfully minted. Never a token shared
                // across installs — the bot check flags shared visitorData,
                // which kills all stream resolution.
                //
                // Empty is a real answer, not a soft one. It used to mean "send
                // the /player call without visitorData, which mostly still
                // works"; that is no longer true. [verified September 2026:
                // ANDROID_VR and VISIONOS answer a token-less /player with
                // LOGIN_REQUIRED, "Sign in to confirm you're not a bot", on
                // most videos, while IOS still answers OK.] Callers must treat
                // a blank token as a mint that has to happen before the chain
                // is worth running - see resolvePlayerStreamingData.
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
                KLog.i("YouTubeRepository", "visitorData reminted after bot-check flag")
                fresh
            } else {
                KLog.w("YouTubeRepository", "visitorData remint failed (bootstrap fetch)")
                null
            }
        }

    /**
     * Drop the current visitorData and mint a new one because *playback* failed,
     * not resolution.
     *
     * [resolvePlayerStreamingData] only remints when the /player call itself
     * shows the bot check (LOGIN_REQUIRED / missing streamingData). The other
     * signature is a /player that answers 200 OK with URLs googlevideo then
     * refuses with HTTP 403 — the token is flagged at the media layer only. The
     * player sees that, resolution never does, so without this entry point the
     * flagged token sits in prefs and is replayed on every launch for the whole
     * 6h TTL: "restarting and clearing cache don't help, clearing data does".
     *
     * Safe to call speculatively; a no-op when there is no token to replace.
     */
    suspend fun refreshVisitorDataAfterPlaybackFailure() {
        // Every cached ladder contains signed URLs minted before the failure.
        // They must not win the retry after identity or network conditions
        // change, even when there is no persisted visitor token to replace.
        VideoStreamResolutionCache.clear()
        val flagged = cachedVisitorData ?: loadPersistedVisitorData() ?: return
        try {
            remintVisitorData(flagged)
        } catch (e: Exception) {
            KLog.w(
                "YouTubeRepository",
                "visitorData remint after playback failure failed: ${e.message}",
            )
        }
    }

    // Last successfully minted token, persisted per install so a cold start on
    // a flaky network still has a usable, install-unique token to fall back on.
    private val visitorDataPrefs by lazy {
        context.getSharedPreferences("ivor_visitor_data", Context.MODE_PRIVATE)
    }

    private fun loadPersistedVisitorData(): String? =
        visitorDataPrefs.getString("visitor_data", null)?.takeIf { it.isNotBlank() }

    /**
     * The visitorData this install already has, without minting one.
     *
     * The browse/next/search helpers are not suspending and are called from
     * paths that must not block on a network round trip, so they take whatever
     * the cache and prefs already hold and send nothing when that is empty -
     * which is exactly what they did before this existed, so an empty cache is
     * a no-op rather than a regression.
     *
     * It is normally warm: [prefetchVisitorData] runs at `MusicService.onCreate`
     * and at `VideoPlayerViewModel.init`. Deliberately ignores the TTL - a token
     * slightly past six hours is still a far better identity for a browse call
     * than no token at all, and /player's own path re-mints on the bot-check
     * signal regardless.
     */
    private fun cachedVisitorDataOrNull(): String? =
        cachedVisitorData ?: loadPersistedVisitorData()

    private fun persistVisitorData(value: String) {
        visitorDataPrefs.edit()
            .putString("visitor_data", value)
            .putLong("visitor_data_at", System.currentTimeMillis())
            .apply()
    }

    private fun clearPersistedVisitorData(flagged: String) {
        if (visitorDataPrefs.getString("visitor_data", null) == flagged) {
            visitorDataPrefs.edit()
                .remove("visitor_data")
                .remove("visitor_data_at")
                .apply()
        }
    }

    /**
     * Mint a fresh visitorData token. Primary: the dedicated
     * `youtubei/v1/visitor_id` endpoint — a few hundred bytes and one round
     * trip. Fallback: scraping the youtube.com bootstrap HTML (~1.5 MB),
     * which is what this used to do on every mint. Verified July 2026.
     */
    private suspend fun fetchVisitorData(): String? = withContext(Dispatchers.IO) {
        fetchVisitorDataFromApi() ?: fetchVisitorDataFromBootstrap()
    }

    /**
     * `visitor_id` responses URL-encode the token's base64 padding (`%3D`);
     * unescape it since the /player payload wants the raw token.
     */
    private fun fetchVisitorDataFromApi(): String? {
        return try {
            val body = org.json.JSONObject().put(
                "context",
                org.json.JSONObject().put(
                    "client",
                    org.json.JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", WEB_VERSION)
                        put("hl", "en")
                        put("gl", "US")
                    }
                )
            ).toString()
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .build()
            // Tiny response — the hard-capped stream client keeps a dead
            // network from stalling the mint for 30s.
            val response = streamResolveClient.newCall(request).execute()
            val json = response.body?.string().orEmpty()
            response.close()
            org.json.JSONObject(json)
                .optJSONObject("responseContext")
                ?.optString("visitorData")
                ?.replace("%3D", "=")
                ?.replace("%3d", "=")
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            KLog.w("YouTubeRepository", "visitor_id mint failed: ${e.message}")
            null
        }
    }

    /**
     * Scrape a visitorData token from the youtube.com bootstrap HTML. The
     * token is JSON-escaped in the page (e.g. `=` for `=`), so unescape
     * the two characters that actually appear in base64url visitorData.
     */
    private fun fetchVisitorDataFromBootstrap(): String? {
        return try {
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
            KLog.w("YouTubeRepository", "bootstrap visitorData scrape failed: ${e.message}")
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
            val streamExtractor = youtubeService.getStreamExtractor(streamUrl)
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
            KLog.d("YouTubeRepo", "Not logged in, falling back to popular search")
            return@withContext search("trending music 2026", FILTER_SONGS)
        }

        try {
            // Fetch personalized home page content
            KLog.d("YouTubeRepo", "Fetching personalized recommendations from FEmusic_home")
            val jsonResponse = fetchInternalApi("FEmusic_home")
            
            if (jsonResponse.isEmpty()) {
                KLog.e("YouTubeRepo", "Empty response from FEmusic_home")
                return@withContext fillHomeRecommendations(emptyList())
            }
            
            // Parse songs from the home page response
            val items = usableHomeRecommendations(
                listOf(parseSongsFromInternalJson(jsonResponse))
            )
            KLog.d("YouTubeRepo", "Parsed ${items.size} songs from recommendations")

            // Classic Home needs three valid entries for its three artwork
            // shapes. A partially parsed response is still useful, but it must
            // be filled rather than accepted as complete.
            fillHomeRecommendations(items)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching recommendations", e)
            fillHomeRecommendations(emptyList())
        }
    }

    private suspend fun fillHomeRecommendations(primary: List<Song>): List<Song> {
        if (primary.size >= 3) return primary

        KLog.d("YouTubeRepo", "Home has ${primary.size} usable songs; filling from library")
        val liked = try {
            getLikedMusic()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "Could not use liked songs as Home fallback", e)
            emptyList()
        }
        val withLiked = usableHomeRecommendations(listOf(primary, liked))
        if (withLiked.size >= 3) return withLiked

        val trending = try {
            search("trending music 2026", FILTER_SONGS)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "Could not use search as Home fallback", e)
            emptyList()
        }
        return usableHomeRecommendations(listOf(withLiked, trending))
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
            KLog.e("YouTubeRepo", "Error fetching related songs for $videoId", e)
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
                KLog.d("YouTubeRepo", "Library playlists page $pageCount: ${parsed.size} items")
                if (parsed.isEmpty()) break
                val token = extractContinuationToken(json) ?: break
                json = fetchContinuation(token)
            }

            // The library grid can include "Your Likes" (VLLM) which we already synthesized
            playlists.distinctBy { it.id }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching user playlists", e)
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
                
                KLog.d("YouTubeRepo", "Liked songs page $pageCount: ${songs.size} songs, total: ${allSongs.size}")
                
            } while (continuationToken != null && pageCount < maxPages)
            
            if (allSongs.isNotEmpty()) {
                return@withContext allSongs.distinctBy { it.id }
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching liked music", e)
        }
        
        // Fallback to NewPipe method
        getPlaylistInternal("LM")
    }
    
    /**
     * Fetch continuation page using continuation token.
     */
    private fun fetchContinuation(continuationToken: String): String {
        // Account headers are conditional, the call is not. [verified August
        // 2026: a public playlist's continuation chain walks to the end with no
        // cookies, no auth header and no visitorData.] Returning "" without a
        // session made every signed-out continuation look like a failed fetch,
        // which capped public playlists at their first page just as the parser
        // gap did for signed-in ones - the same symptom from a second cause.
        val cookies = sessionManager.getCookies()

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
        
        val requestBuilder = okhttp3.Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", getRandomUserAgent())
            .addHeader("Origin", "https://music.youtube.com")

        if (cookies != null) {
            requestBuilder.addHeader("Cookie", cookies)
            YouTubeAuthUtils.getAuthorizationHeader(cookies)?.let { auth ->
                requestBuilder.addHeader("Authorization", auth)
                requestBuilder.addHeader("X-Goog-AuthUser", "0")
            }
        }

        val request = requestBuilder.build()

        return try {
            val response = okHttpClient.newCall(request).execute()
            (response.body?.string() ?: "").also { noteSessionState(it) }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Music continuation request failed", e)
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

        // The account path must be first. NewPipe is intentionally anonymous,
        // so an owned/private playlist may expose its first page and then deny
        // the continuation. That used to return a plausible-looking exact 100
        // songs and prevent this authenticated path from ever running.
        val isLoggedIn = sessionManager.isLoggedIn()
        val accountResult = if (isLoggedIn) {
            getBrowsePlaylistSongs(playlistId)
        } else PlaylistLoadResult(emptyList(), complete = false)
        if (accountResult.complete && accountResult.songs.isNotEmpty()) {
            return@withContext accountResult.songs
        }

        var newPipeComplete = true
        val newPipeSongs = try {
            val urlId = if (playlistId.startsWith("VL")) playlistId.removePrefix("VL") else playlistId
            val playlistUrl = "https://www.youtube.com/playlist?list=$urlId"

            val playlistExtractor = youtubeService.getPlaylistExtractor(playlistUrl)
            playlistExtractor.fetchPage()

            val allItems = mutableListOf<StreamInfoItem>()
            allItems.addAll(playlistExtractor.initialPage.items.filterIsInstance<StreamInfoItem>())

            var currentPage = playlistExtractor.initialPage
            while (currentPage.hasNextPage()) {
                try {
                    currentPage = playlistExtractor.getPage(currentPage.nextPage)
                    allItems.addAll(currentPage.items.filterIsInstance<StreamInfoItem>())
                } catch (e: Exception) {
                    newPipeComplete = false
                    KLog.w(
                        "YouTubeRepo",
                        "NewPipe playlist continuation failed for $playlistId after ${allItems.size} items",
                        e
                    )
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
        } catch (e: Exception) {
            newPipeComplete = false
            emptyList()
        }

        if (newPipeComplete && newPipeSongs.isNotEmpty()) return@withContext newPipeSongs

        // Fallback to InnerTube /browse, which anonymous WEB_REMIX answers for
        // public playlists and album playlists (OLAK5uy_…); playlists browse as
        // "VL<id>". Signed out the account path above never ran, so this is the
        // only browse - and it walks continuations for the same reason that one
        // does. Parsing page one alone was the 100-song cap wearing a second
        // hat: public playlists page to their end with no session at all.
        val anonymousResult = if (!isLoggedIn) {
            try {
                getBrowsePlaylistSongs(playlistId)
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Anonymous playlist browse failed for $playlistId", e)
                PlaylistLoadResult(emptyList(), complete = false)
            }
        } else PlaylistLoadResult(emptyList(), complete = false)
        if (anonymousResult.complete && anonymousResult.songs.isNotEmpty()) {
            return@withContext anonymousResult.songs
        }

        // Do not throw away useful rows if every complete path failed, but log
        // loudly that this is degraded rather than pretending the exact page
        // boundary is the playlist's real end.
        val partial = listOf(accountResult.songs, newPipeSongs, anonymousResult.songs)
            .maxByOrNull { it.size }
            .orEmpty()
        if (partial.isNotEmpty()) {
            KLog.w(
                "YouTubeRepo",
                "Returning incomplete playlist $playlistId (${partial.size} songs) after all full-load paths failed"
            )
        }
        partial
    }

    private data class PlaylistLoadResult(
        val songs: List<Song>,
        val complete: Boolean
    )

    /**
     * WEB_REMIX playlist browse, including every continuation.
     *
     * Account cookies are attached by [browseMusic] and [fetchContinuation]
     * when there is a session and omitted when there is not, so this one walker
     * serves both: a public playlist pages to its end anonymously, and an owned
     * or private one needs the session that those two helpers already apply.
     */
    private fun getBrowsePlaylistSongs(playlistId: String): PlaylistLoadResult {
        val browseId = if (playlistId.startsWith("VL") || playlistId.startsWith("FE")) {
            playlistId
        } else {
            "VL$playlistId"
        }
        val allSongs = mutableListOf<Song>()
        val seenTokens = mutableSetOf<String>()
        var json = browseMusic(browseId)
            ?: return PlaylistLoadResult(emptyList(), complete = false)

        while (true) {
            allSongs += parseSongsFromInternalJson(json, preserveDuplicates = true)
            val token = extractPlaylistContinuationToken(json)
                ?: return PlaylistLoadResult(allSongs, complete = true)
            if (!seenTokens.add(token)) {
                KLog.w("YouTubeRepo", "Repeated playlist continuation for $playlistId")
                return PlaylistLoadResult(allSongs, complete = false)
            }
            json = fetchContinuation(token)
            if (json.isEmpty()) {
                KLog.w(
                    "YouTubeRepo",
                    "Authenticated playlist continuation failed for $playlistId after ${allSongs.size} songs"
                )
                return PlaylistLoadResult(allSongs, complete = false)
            }
        }
    }

    /**
     * Read only the continuation belonging to the playlist shelf. A generic
     * recursive "first continuation" can pick an unrelated carousel token.
     *
     * That scoping is load-bearing rather than tidiness [verified August 2026]:
     * a playlist page also carries
     * `twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.continuations[0]`,
     * and following that token returns a `musicCarouselShelfRenderer` of
     * related playlists, not more tracks. Do not widen this to a bare
     * findContinuationTokens over the whole response.
     *
     * Page one puts the token in a `continuationItemRenderer` at the end of the
     * shelf's own contents, which the shelf scopes already cover. Later pages
     * arrive under `appendContinuationItemsAction`, where there is no shelf at
     * all - without that scope the chain stopped after page two even once the
     * items themselves parsed.
     */
    private fun extractPlaylistContinuationToken(json: String): String? = try {
        val root = org.json.JSONObject(json)
        val scopes = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "musicPlaylistShelfRenderer", scopes)
        findObjectsByKey(root, "musicPlaylistShelfContinuation", scopes)
        findObjectsByKey(root, "appendContinuationItemsAction", scopes)
        findObjectsByKey(root, "reloadContinuationItemsCommand", scopes)
        scopes.asSequence().mapNotNull { scope ->
            val tokens = mutableListOf<String>()
            findContinuationTokens(scope, tokens)
            tokens.firstOrNull()
        }.firstOrNull()
    } catch (e: Exception) {
        null
    }

    suspend fun fetchAccountInfo() = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext

        try {
            val jsonResponse = fetchInternalApi("account/account_menu")

            if (jsonResponse.isEmpty()) {
                KLog.w("YouTubeRepo", "fetchAccountInfo: empty response from account/account_menu")
                return@withContext
            }
            // Account payloads include identity details. Keep them out of the
            // release diagnostic ring buffer; success/failure is enough here.
            KLog.d("YouTubeRepo", "fetchAccountInfo response received")
            
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
            
            KLog.d("YouTubeRepo", "fetchAccountInfo parsed name=$userName avatar=$avatarUrl")

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

            // YouTube's own account identifier, which every authenticated
            // response carries for free (verified August 2026 against
            // account_menu, FEsubscriptions and FEwhat_to_watch: present on all
            // three, identical across them, alongside a `loggedOut` boolean).
            // Stored verbatim, trailing separators included - it is only ever
            // compared against another copy of itself.
            //
            // This is what lets the app recognise an account it already has:
            // signing back in repairs that profile instead of adding a
            // duplicate row, and a restored backup can tell that an account in
            // the file is the one already signed in here.
            runCatching {
                org.json.JSONObject(jsonResponse)
                    .optJSONObject("responseContext")
                    ?.optJSONObject("mainAppWebResponseContext")
                    ?.optString("datasyncId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sessionManager.saveDatasyncId(it) }
            }
            
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Account identity refresh failed", e)
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
        val captionTracks: List<CaptionTrack> = emptyList(),
        /**
         * `playerConfig.audioConfig.loudnessDb`: how far this track's master
         * sits above YouTube's -14 LKFS target, so the playback correction is a
         * gain of the negation. See [TrackLoudnessStore]. Null when the
         * response carried no audioConfig, which is every response that failed
         * playability.
         */
        val loudnessDb: Float? = null,
    )

    /**
     * Resolve /player streamingData for [videoId], reminting the visitorData
     * and retrying once when the responses show the current token has been
     * flagged by YouTube's bot check. Without the remint, a token flagged
     * mid-TTL poisons every resolution until it expires — the "music played
     * fine, then nothing plays anymore" failure mode.
     */
    private suspend fun resolvePlayerStreamingData(videoId: String): org.json.JSONObject? {
        var visitorData = getVisitorData()
        // A blank token is not "no identity, carry on": the bot check refuses
        // ANDROID_VR and VISIONOS outright without one (see getVisitorData).
        // Running the chain first would spend both clients on a refusal that is
        // already known, so mint before it rather than after.
        if (visitorData.isBlank()) {
            visitorData = remintVisitorData(flagged = "").orEmpty()
            if (visitorData.isBlank()) {
                KLog.w(
                    "YouTubeRepository",
                    "Resolve: no visitorData available for videoId=$videoId, bot check will refuse",
                )
            }
        }
        val first = runPlayerClientChain(videoId, visitorData)
        first.streamingData?.let { return it }
        if (!first.visitorDataSuspect) return null

        KLog.w(
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
     * ANDROID_VR is the primary client: it answers signed out, needs no player
     * JS, and returns unciphered URLs. It used to be the one client whose URLs
     * served a whole file without a GVS PO Token; as of August 2026 it is not,
     * and the ~1 MiB cutoff that used to be IOS-only now applies to both. Which
     * client resolved the stream no longer decides whether it can be fetched -
     * the visitorData does (see the ANDROID_VR constants).
     *
     * IOS stays only as a last-ditch fallback for the rare videos ANDROID_VR
     * can't serve (e.g. "made for kids", which ANDROID_VR omits).
     *
     * Each call is hard-capped by streamResolveClient's callTimeout, so the
     * worst case is bounded regardless of coroutine cancellability.
     */
    private fun androidVrClientFields(): org.json.JSONObject = org.json.JSONObject().apply {
        put("androidSdkVersion", 32)
        put("deviceMake", "Oculus")
        put("deviceModel", "Quest 3")
        put("osName", "Android")
        put("osVersion", "12L")
    }

    private fun iosClientFields(): org.json.JSONObject = org.json.JSONObject().apply {
        put("deviceMake", "Apple")
        put("deviceModel", "iPhone16,2")
        put("osName", "iPhone")
        put("osVersion", "18.1.0.22B83")
    }

    private fun visionOsClientFields(): org.json.JSONObject = org.json.JSONObject().apply {
        put("clientScreen", "WATCH")
        put("platform", "MOBILE")
        put("deviceMake", "Apple")
        put("deviceModel", "RealityDevice14,1")
        put("osName", "visionOS")
        put("osVersion", "25.6.0.23O471")
    }

    private fun nativeClientNonce(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val random = java.security.SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    /**
     * Resolve the direct visionOS response used only to augment an otherwise
     * successful NewPipe extraction with HDR formats. Its VP9.2 HDR URLs
     * serve bounded ranges across all HDR itags 330-337 without throttling.
     */
    private suspend fun resolveVisionOsStreamingData(videoId: String): org.json.JSONObject? {
        val visitorData = getVisitorData()
        val first = fetchVisionOsPlayerResponse(videoId, visitorData)
        first.streamingData?.let { return it }
        if (!first.visitorDataSuspect) return null

        val fresh = remintVisitorData(flagged = visitorData) ?: return null
        if (fresh == visitorData) return null
        return fetchVisionOsPlayerResponse(videoId, fresh).streamingData
    }

    private suspend fun fetchVisionOsPlayerResponse(
        videoId: String,
        visitorData: String,
    ): PlayerResponse = fetchPlayerResponse(
        videoId = videoId,
        clientName = "VISIONOS",
        clientVersion = VISIONOS_CLIENT_VERSION,
        clientNameId = VISIONOS_CLIENT_ID,
        userAgent = VISIONOS_USER_AGENT,
        visitorData = visitorData,
        extraClientFields = visionOsClientFields(),
        contentPlaybackNonce = nativeClientNonce(16),
        mobileTParameter = nativeClientNonce(12),
    )

    private suspend fun runPlayerClientChain(videoId: String, visitorData: String): PlayerResponse {
        val vr = fetchPlayerResponse(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = ANDROID_VR_VERSION,
            clientNameId = ANDROID_VR_CLIENT_ID,
            userAgent = ANDROID_VR_USER_AGENT,
            visitorData = visitorData,
            extraClientFields = androidVrClientFields(),
        )
        // The /player response carries the caption tracklist alongside the
        // streams, so harvesting it here makes a later CC tap free.
        cacheCaptionTracks(videoId, vr.captionTracks)
        cacheTrackLoudness(videoId, vr.loudnessDb)
        vr.streamingData?.let { return vr }

        val ios = fetchPlayerResponse(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = IOS_VERSION,
            clientNameId = IOS_CLIENT_ID,
            userAgent = IOS_USER_AGENT,
            visitorData = visitorData,
            extraClientFields = iosClientFields(),
        )
        cacheCaptionTracks(videoId, ios.captionTracks)
        cacheTrackLoudness(videoId, ios.loudnessDb)
        return PlayerResponse(
            streamingData = ios.streamingData,
            visitorDataSuspect = vr.visitorDataSuspect || ios.visitorDataSuspect,
            captionTracks = vr.captionTracks.ifEmpty { ios.captionTracks },
            // The measurement is the track's, not the client's, so whichever
            // response carried one is as good as the other.
            loudnessDb = vr.loudnessDb ?: ios.loudnessDb,
        )
    }

    /**
     * Select the audio URL from a /player streamingData object, honoring the
     * per-network music quality setting (fresh read at resolution time). Falls
     * back to muxed video formats (e.g. itag 18) when no audio-only format is
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

        val audioFormats = originalTrackAudioFormats(
            formats.filter {
                it.optString("mimeType").contains("audio") && hasPlayableUrl(it)
            }
        )
        KLog.d(
            "YouTubeRepository",
            "Resolve[InnerTube] formats=${formats.size} audioOnly=${audioFormats.size} videoId=$videoId",
        )

        // Per-network music quality (fresh static read — see ThemePreferences):
        // high takes the best bitrate, normal the track closest to ~128 kbps,
        // low the smallest stream.
        val musicQuality = ThemePreferences.currentMusicQuality(context)
        val pickedAudio = when (musicQuality) {
            ThemePreferences.MUSIC_QUALITY_LOW ->
                audioFormats.minByOrNull { it.optInt("bitrate") }
            ThemePreferences.MUSIC_QUALITY_NORMAL ->
                audioFormats.minByOrNull { kotlin.math.abs(it.optInt("bitrate") - 128_000) }
            else -> audioFormats.maxByOrNull { it.optInt("bitrate") }
        }
        pickedAudio?.optString("url")
            ?.takeIf { it.isNotEmpty() }?.let { return it }

        // No audio-only stream available — fall back to a muxed MP4 (itag 18 etc.).
        // ExoPlayer happily plays just the audio track of these.
        val muxedFormats = formats.filter {
            it.optString("mimeType").startsWith("video/") && hasPlayableUrl(it)
        }
        muxedFormats.minByOrNull { it.optInt("bitrate") }?.optString("url")
            ?.takeIf { it.isNotEmpty() }?.let {
                KLog.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube] using muxed video format videoId=$videoId (no audio-only)",
                )
                return it
            }

        val cipheredCount = formats.count {
            !it.optString("signatureCipher").isNullOrEmpty() ||
                !it.optString("cipher").isNullOrEmpty()
        }
        KLog.w(
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
        contentPlaybackNonce: String? = null,
        mobileTParameter: String? = null,
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
                if (contentPlaybackNonce != null) put("contentPlaybackNonce", contentPlaybackNonce)
                if (mobileTParameter != null) put("t", mobileTParameter)
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
                KLog.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] HTTP $code videoId=$videoId",
                )
                return@withContext PlayerResponse(null, false)
            }
            if (json.isEmpty()) {
                KLog.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] empty body videoId=$videoId",
                )
                return@withContext PlayerResponse(null, false)
            }

            val root = org.json.JSONObject(json)

            val playability = root.optJSONObject("playabilityStatus")
            val status = playability?.optString("status").orEmpty()
            if (status.isNotEmpty() && status != "OK") {
                KLog.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] playability=$status reason=${playability?.optString("reason")} videoId=$videoId",
                )
                // LOGIN_REQUIRED here is the bot check rejecting our
                // visitorData ("Sign in to confirm you're not a bot").
                return@withContext PlayerResponse(null, status == "LOGIN_REQUIRED")
            }

            val captionTracks = parseCaptionTracks(root)
            val loudnessDb = root.optJSONObject("playerConfig")
                ?.optJSONObject("audioConfig")
                ?.let { audio ->
                    // Present on every OK response probed (August 2026), but a
                    // missing key must read as "unknown" rather than 0.0, which
                    // is a real measurement meaning "already at target".
                    if (audio.has("loudnessDb")) audio.optDouble("loudnessDb").toFloat() else null
                }
                ?.takeIf { it.isFinite() }

            val streamingData = root.optJSONObject("streamingData")
            if (streamingData == null) {
                KLog.w(
                    "YouTubeRepository",
                    "Resolve[InnerTube/$clientName] no streamingData videoId=$videoId",
                )
                // Status OK with no streamingData is the other known signature
                // of a stale/missing visitorData.
                return@withContext PlayerResponse(null, true, captionTracks, loudnessDb)
            }
            PlayerResponse(streamingData, false, captionTracks, loudnessDb)
        } catch (e: CancellationException) {
            // Swallowing this would report a cancelled call as a client that
            // has no streams, sending the chain on to the next client inside a
            // coroutine that is already dead.
            throw e
        } catch (e: Exception) {
            KLog.e(
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
            // Client name 67 is WEB_REMIX. Sent for the same reason the WEB
            // calls now send theirs: a client that never identifies itself is
            // the shape anti-abuse looks for. The visitor id rides as a header
            // rather than in the body because this endpoint's context is built
            // from raw JSON strings in five places; the header carries the same
            // identity without touching any of them.
            .addHeader("X-YouTube-Client-Name", "67")
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .apply {
                cachedVisitorDataOrNull()?.let { addHeader("X-Goog-Visitor-Id", it) }
            }
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(
                        response.code,
                        "music browse $endpoint",
                        response.header("Retry-After"),
                    )
                    KLog.w("YouTubeRepo", "music browse $endpoint HTTP ${response.code}")
                    return ""
                }
                (response.body?.string() ?: "").also { noteSessionState(it) }
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Music browse request failed", e)
            ""
        }
    }

    private fun parseSongsFromInternalJson(
        json: String,
        preserveDuplicates: Boolean = false
    ): List<Song> {
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
            KLog.e("YouTubeRepo", "Could not parse music song shelf", e)
        }
        return if (preserveDuplicates) songs else songs.distinctBy { it.id }
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

        // Modern continuation shape - every branch above is the legacy form.
        // See continuationItemsOrNull for what changed and what it cost.
        continuationItemsOrNull(root)?.let { return it }

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
            KLog.e("YouTubeRepo", "Could not parse music playlist shelf", e)
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
        // Incognito covers the account's own history too, not only Koda's.
        // Gated here rather than at the call sites so nothing that starts
        // playback later has to remember.
        if (IncognitoMode.isEnabled(context)) return@withContext

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
                KLog.e("YouTubeRepo", "Player response empty for $videoId")
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
                KLog.d("YouTubeRepo", "History sync SUCCESS for $videoId")
            } else {
                KLog.e("YouTubeRepo", "History sync failed: ${trackingResponse.code}")
            }
            trackingResponse.close()

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

        if (sort != VideoSearchSort.RELEVANCE) {
            val sorted = searchVideosInnerTube(effectiveQuery, sort)
            if (sorted.isNotEmpty()) return@withContext sorted
            KLog.w("YouTubeRepo", "Sorted video search empty, falling back to relevance order")
        }

        try {
            // Use YouTube videos filter (not music_videos)
            val searchExtractor = youtubeService.getSearchExtractor(effectiveQuery, listOf(FILTER_YOUTUBE_VIDEOS), "")
            searchExtractor.fetchPage()

            // Cache for pagination (see searchVideosNext)
            videoSearchExtractorCache[effectiveQuery] = searchExtractor
            videoSearchNextPageCache[effectiveQuery] =
                if (searchExtractor.initialPage.hasNextPage()) searchExtractor.initialPage.nextPage else null

            searchExtractor.initialPage.items.toVideoItems()
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error searching videos", e)
            emptyList()
        }
    }

    /**
     * Next page of video search results for a query already fetched by
     * [searchVideos]. Empty means exhausted, which is also what a sorted
     * (non-relevance) search returns, since that path resolves through
     * InnerTube and never caches an extractor.
     *
     * [dateFilter] must match the original call - it is part of the cache key.
     */
    suspend fun searchVideosNext(
        query: String,
        dateFilter: VideoSearchDateFilter = VideoSearchDateFilter.ANY
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val effectiveQuery = dateFilter.applyTo(query)
            val extractor = videoSearchExtractorCache[effectiveQuery] ?: return@withContext emptyList()
            val pageInfo = videoSearchNextPageCache[effectiveQuery] ?: return@withContext emptyList()

            val nextPage = extractor.getPage(pageInfo)
            videoSearchNextPageCache[effectiveQuery] =
                if (nextPage.hasNextPage()) nextPage.nextPage else null

            nextPage.items.toVideoItems()
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error loading more video results", e)
            emptyList()
        }
    }

    /** Map a NewPipe search page's streams to [VideoItem]s, skipping unusable rows. */
    private fun List<org.schabi.newpipe.extractor.InfoItem>.toVideoItems(): List<VideoItem> =
        filterIsInstance<StreamInfoItem>().mapNotNull { item ->
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

    /**
     * Search for playlists on regular YouTube (video mode search). Mapped to
     * [VideoPlaylist] so results plug straight into the same playlist detail
     * page and models the video Library tab uses.
     */
    suspend fun searchVideoPlaylists(query: String): List<VideoPlaylist> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = youtubeService.getSearchExtractor(query, listOf(FILTER_YOUTUBE_PLAYLISTS), "")
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
                    youtubeService.getSearchExtractor(query, listOf(FILTER_YOUTUBE_CHANNELS), "")
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
    private fun searchVideosInnerTube(query: String, sort: VideoSearchSort): List<VideoItem> {
        return try {
            val body = org.json.JSONObject()
                .put("context", webContext())
                .put("query", query)
                .put("params", buildVideoSearchParams(sort))
            val response = postWatchApi("search", body) ?: return emptyList()

            val renderers = mutableListOf<org.json.JSONObject>()
            val root = org.json.JSONObject(response)
            findObjectsByKey(root, "videoRenderer", renderers)
            findObjectsByKey(root, "lockupViewModel", renderers)
            renderers.mapNotNull { renderer ->
                if (renderer.has("videoId")) parseVideoRenderer(renderer)
                else parseLockupViewModel(renderer)
            }.distinctBy { it.videoId }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "InnerTube video search failed", e)
            emptyList()
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
        val cookies = sessionManager.getCookies() ?: return@withContext empty
        
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
            // Never place Authorization material or response bodies in KLog:
            // users can deliberately attach its release ring buffer to a bug
            // report, and these values may carry account/feed information.
            KLog.d("YouTubeRepo", "Making personalized video request")
            val response = okHttpClient.newCall(request).execute()
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
     * Continuation token of a FEwhat_to_watch rich-grid browse response:
     * tabs[0].tabRenderer.content.richGridRenderer.contents holds a trailing
     * continuationItemRenderer whose continuationEndpoint.continuationCommand
     * carries the token for the next /browse page. Verified July 2026.
     */
    private fun extractRichGridContinuation(root: org.json.JSONObject): String? {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?: root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
        val contents = tabs?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("richGridRenderer")
            ?.optJSONArray("contents")
            ?: return null
        for (i in 0 until contents.length()) {
            val token = contents.optJSONObject(i)
                ?.optJSONObject("continuationItemRenderer")
                ?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
            if (token != null) return token
        }
        return null
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
                .put("context", webContext())
                .put("continuation", continuation)
            val raw = postWatchApi("browse", body) ?: return@withContext VideoFeedPage(emptyList())
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

    /**
     * Get user's watch history from YouTube.
     * Uses the YouTube browse endpoint with "FEhistory".
     */
    suspend fun getWatchHistory(): List<VideoItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            // Use the shared signed WEB request path so HTTP failures and an
            // expired session cannot masquerade as a valid empty history.
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webContext())
                    .put("browseId", "FEhistory")
            ) ?: return@withContext emptyList()

            // The live August 2026 shape carries roughly 200 lockups in the
            // initial date-grouped page. The generic feed parser used to throw
            // away everything after 30, making View all look out of sync.
            parseVideosFromYouTubeJson(raw, limit = 200)
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error fetching watch history", e)
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
    private fun parseVideosFromYouTubeJson(json: String, limit: Int = 30): List<VideoItem> {
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
            KLog.e("YouTubeRepo", "Could not parse watch history", e)
        }
        return videos.distinctBy { it.videoId }.take(limit)
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
                KLog.w("YouTubeRepo", "No Shorts shelf on home, using search fallback")
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Shorts home shelf failed", e)
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
            KLog.e("YouTubeRepo", "Shorts search fallback failed", e)
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
            KLog.e("YouTubeRepo", "getShortsSequence failed", e)
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
            KLog.w("YouTubeRepo", "parseShortsLockup failed", e)
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
        // This one really is account-only: the browse below runs anonymously
        // now, and signed out it would spend a request to be handed a
        // signed-out shell with no playlists in it.
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
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
            KLog.e("YouTubeRepo", "getVideoPlaylists failed", e)
            emptyList()
        }
    }

    /**
     * The first page of one video playlist for browsing and playback. A normal
     * open stays one network call; [getCompletePlaylistVideos] pays for every
     * continuation only when an operation really needs the whole list.
     */
    suspend fun getPlaylistVideos(playlistId: String): List<VideoItem> = withContext(Dispatchers.IO) {
        try {
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val json = fetchYouTubeBrowse(browseId)
                .takeIf { it.isNotEmpty() } ?: return@withContext getPlaylistVideosAnonymous(playlistId)
            val root = org.json.JSONObject(json)
            val renderers = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "playlistVideoRenderer", renderers)
            if (renderers.isNotEmpty()) {
                return@withContext renderers.mapNotNull { parsePlaylistVideoRenderer(it) }
            }
            val lockups = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "lockupViewModel", lockups)
            lockups.mapNotNull { parseLockupViewModel(it) }
                .ifEmpty { getPlaylistVideosAnonymous(playlistId) }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getPlaylistVideos failed for $playlistId", e)
            getPlaylistVideosAnonymous(playlistId)
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
        var json = fetchYouTubeBrowse(browseId)
        if (json.isEmpty()) return VideoPlaylistLoadResult(emptyList(), complete = false)

        return try {
            while (true) {
                val root = org.json.JSONObject(json)
                val renderers = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(root, "playlistVideoRenderer", renderers)
                if (renderers.isNotEmpty()) {
                    videos += renderers.mapNotNull { parsePlaylistVideoRenderer(it) }
                } else {
                    val lockups = mutableListOf<org.json.JSONObject>()
                    findObjectsByKey(root, "lockupViewModel", lockups)
                    videos += lockups.mapNotNull { parseLockupViewModel(it) }
                }

                val token = extractVideoPlaylistContinuationToken(root) ?: break
                if (!seenTokens.add(token)) {
                    KLog.w("YouTubeRepo", "Repeated video playlist continuation for $playlistId")
                    return VideoPlaylistLoadResult(videos, complete = false)
                }
                json = fetchYouTubeBrowseContinuation(token)
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

    private fun extractVideoPlaylistContinuationToken(root: org.json.JSONObject): String? {
        val scopes = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "itemSectionRenderer", scopes)
        findObjectsByKey(root, "playlistVideoListRenderer", scopes)
        findObjectsByKey(root, "appendContinuationItemsAction", scopes)
        findObjectsByKey(root, "reloadContinuationItemsCommand", scopes)
        return scopes.asSequence().mapNotNull { scope ->
            val tokens = mutableListOf<String>()
            findContinuationTokens(scope, tokens)
            tokens.firstOrNull()
        }.firstOrNull()
    }

    private fun fetchYouTubeBrowseContinuation(token: String): String =
        postWatchApi(
            "browse",
            org.json.JSONObject()
                .put("context", webContext())
                .put("continuation", token)
        ).orEmpty()

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
                videos += page.items.toVideoItems()
                while (page.hasNextPage()) {
                    try {
                        page = extractor.getPage(page.nextPage)
                        videos += page.items.toVideoItems()
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

    /** First NewPipe page only, matching [getPlaylistVideos]'s browse cost. */
    private suspend fun getPlaylistVideosAnonymous(playlistId: String): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val listId = playlistId.removePrefix("VL")
            if (listId == "WL" || listId == "LL" || listId == "LM") return@withContext emptyList()
            try {
                val extractor = youtubeService.getPlaylistExtractor(
                    "https://www.youtube.com/playlist?list=$listId"
                )
                extractor.fetchPage()
                extractor.initialPage.items.toVideoItems()
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "Anonymous playlist fetch failed for $listId", e)
                emptyList()
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
                val json = fetchYouTubeBrowse("VL$listId").takeIf { it.isNotEmpty() }
                    ?: return@withContext null
                val root = org.json.JSONObject(json)
                parseModernPlaylistHeader(root, listId)
                    ?: parseLegacyPlaylistHeader(root, listId)
            } catch (e: Exception) {
                KLog.e("YouTubeRepo", "getPlaylistHeader failed for $playlistId", e)
                null
            }
        }

    /**
     * `pageHeaderViewModel`, which is what an ordinary `PL…` playlist returns
     * (verified August 2026, both signed in and signed out).
     *
     * The header's metadata is positional prose - "by the bootleg boy",
     * "Playlist", "960 videos", "26,738,861 views" - so nothing here reads a
     * row by index. The author comes off the avatar stack, which is a
     * structurally distinct part rather than a position, and the count is
     * whichever part talks about videos. That word is localized, so a miss
     * reports -1 ("the page did not say") rather than a number invented from
     * the wrong row.
     */
    private fun parseModernPlaylistHeader(
        root: org.json.JSONObject,
        listId: String
    ): PlaylistPageInfo? {
        val headers = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "pageHeaderViewModel", headers)
        val header = headers.firstOrNull() ?: return null

        val title = header.optJSONObject("title")
            ?.optJSONObject("dynamicTextViewModel")
            ?.optJSONObject("text")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val rows = header.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")

        var author = ""
        var itemCount = -1
        if (rows != null) {
            for (rowIndex in 0 until rows.length()) {
                val parts = rows.optJSONObject(rowIndex)?.optJSONArray("metadataParts") ?: continue
                for (partIndex in 0 until parts.length()) {
                    val part = parts.optJSONObject(partIndex) ?: continue
                    val avatarText = part.optJSONObject("avatarStack")
                        ?.optJSONObject("avatarStackViewModel")
                        ?.optJSONObject("text")
                        ?.optString("content")
                        ?.takeIf { it.isNotBlank() }
                    if (avatarText != null && author.isBlank()) {
                        author = avatarText.removePrefix("by ").trim()
                        continue
                    }
                    val text = part.optJSONObject("text")?.optString("content").orEmpty()
                    if (itemCount < 0 && text.contains("video", ignoreCase = true)) {
                        itemCount = countFromText(text)
                    }
                }
            }
        }

        return PlaylistPageInfo(
            playlistId = listId,
            title = title,
            author = author,
            thumbnailUrl = header.optJSONObject("heroImage")
                ?.optJSONObject("contentPreviewImageViewModel")
                ?.optJSONObject("image")
                ?.let { bestImageSource(it.optJSONArray("sources")) },
            itemCount = itemCount
        )
    }

    /**
     * `playlistHeaderRenderer`, the legacy shape, still what an album playlist
     * (`OLAK5uy_…`) returns (verified August 2026).
     *
     * Its subtitle is "Daft Punk • Album", so the author is the part before the
     * separator; the type half is dropped rather than shown, because the page
     * this feeds already says what it is.
     */
    private fun parseLegacyPlaylistHeader(
        root: org.json.JSONObject,
        listId: String
    ): PlaylistPageInfo? {
        val headers = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "playlistHeaderRenderer", headers)
        val header = headers.firstOrNull() ?: return null

        val title = getRunText(header.optJSONObject("title"))?.takeIf { it.isNotBlank() }
            ?: return null
        val author = getRunText(header.optJSONObject("ownerText"))
            ?.takeIf { it.isNotBlank() }
            ?: getRunText(header.optJSONObject("subtitle"))
                ?.substringBefore("•")
                ?.trim()
                .orEmpty()

        return PlaylistPageInfo(
            playlistId = listId,
            title = title,
            author = author,
            thumbnailUrl = header.optJSONObject("playlistHeaderBanner")
                ?.optJSONObject("heroPlaylistThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.let { bestThumbnail(it.optJSONArray("thumbnails")) },
            itemCount = countFromText(getRunText(header.optJSONObject("numVideosText")))
        )
    }

    /** "1,234 videos" to a number; -1 for anything with no digits in it. */
    private fun countFromText(text: String?): Int {
        val digits = text?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() } ?: return -1
        return digits.toIntOrNull() ?: -1
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

             fun absorbVideoStats(parts: org.json.JSONArray?) {
                 if (parts == null) return
                 val stats = parseLockupVideoStats(
                     (0 until parts.length()).map { index ->
                         val part = parts.optJSONObject(index)
                         val text = part?.optJSONObject("text")
                         text?.optString("content").orEmpty() to
                             part?.optString("accessibilityLabel")?.takeIf { it.isNotBlank() }
                     }
                 )
                 if (viewCount.isBlank()) viewCount = stats.viewCount
                 if (uploadDate.isBlank()) uploadDate = stats.uploadedDate
             }
             
             if (metadataRows != null && metadataRows.length() > 0) {
                 val firstRowParts = metadataRows.optJSONObject(0)?.optJSONArray("metadataParts")
                 if (firstRowParts != null && firstRowParts.length() > 0) {
                     val textObj = firstRowParts.optJSONObject(0)?.optJSONObject("text")
                     val firstText = textObj?.optString("content").orEmpty()
                     val firstRowStats = parseLockupVideoStats(
                         (0 until firstRowParts.length()).map { index ->
                             val part = firstRowParts.optJSONObject(index)
                             val text = part?.optJSONObject("text")
                             text?.optString("content").orEmpty() to
                                 part?.optString("accessibilityLabel")?.takeIf { it.isNotBlank() }
                         }
                     )
                     val firstRowIsStats = firstRowStats.uploadedDate.isNotBlank() ||
                         firstText.contains("view", ignoreCase = true) ||
                         firstText.contains("watching", ignoreCase = true)

                     if (firstRowIsStats) {
                         // Channel tabs omit the creator row because the page
                         // itself already names the creator. Their first row is
                         // instead "N views • date"; treating it as a channel
                         // name used to discard the upload date entirely.
                         absorbVideoStats(firstRowParts)
                     } else {
                         channelName = firstText.ifBlank { channelName }

                         // Modern lockups use attributed text: the creator link
                         // is an innertubeCommand in commandRuns. Older responses
                         // use the legacy runs/navigationEndpoint shape below.
                         channelId = textObj?.optJSONArray("commandRuns")
                             ?.optJSONObject(0)
                             ?.optJSONObject("onTap")
                             ?.optJSONObject("innertubeCommand")
                             ?.optJSONObject("browseEndpoint")
                             ?.optString("browseId")
                             ?.takeIf { it.isNotBlank() }

                         if (channelId == null && textObj?.has("runs") == true) {
                             val runs = textObj.optJSONArray("runs")
                             if (runs != null && runs.length() > 0) {
                                 val browseEndpoint = runs.optJSONObject(0)
                                     ?.optJSONObject("navigationEndpoint")
                                     ?.optJSONObject("browseEndpoint")
                                 channelId = browseEndpoint?.optString("browseId")
                                     ?.takeIf { it.isNotBlank() }
                             }
                         }
                     }
                 }

                 // Normal feed lockups put creator in row zero and statistics
                 // below it. Read every remaining row because some variants
                 // split views and date while others keep them together.
                 for (rowIndex in 1 until metadataRows.length()) {
                     absorbVideoStats(
                         metadataRows.optJSONObject(rowIndex)?.optJSONArray("metadataParts")
                     )
                 }
             }
             
             // The creator block, which is where a lockup actually addresses its
             // channel. [verified September 2026, signed out, against a live
             // /next related list and a channel Videos tab]
             //
             // The metadata *text* rows above carry no creator command at all
             // on these responses - every card in a related list came back with
             // an empty onTap - so reading only those left `channelId` null for
             // ordinary videos too, and every channel tap paid a whole extra
             // /next through the `video:` fallback to recover an id the
             // response had already sent. The avatar is where it lives:
             //
             //   image.decoratedAvatarViewModel  one creator, browse id on its
             //                                   rendererContext.commandContext
             //   image.avatarStackViewModel      a collab, one avatar each and
             //                                   the collaborators dialog on
             //                                   the same command slot
             //
             // A channel tab's lockup has no image block at all - the page
             // already names the creator - so neither is present there and the
             // `video:` fallback remains the answer for those.
             val lockupImage = metadata?.optJSONObject("image")
             val decoratedAvatar = lockupImage?.optJSONObject("decoratedAvatarViewModel")
             val avatarStack = lockupImage?.optJSONObject("avatarStackViewModel")

             fun bestSourceUrl(sources: org.json.JSONArray?): String? {
                 if (sources == null) return null
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
                 return bestUrl?.takeIf { it.isNotBlank() }
             }

             val collaborators = collaboratorsFromDialogHost(avatarStack)

             // The collab fallback is second because a collab has no single
             // owner: the dialog lists its channels in the order the byline
             // names them ("Sidemen and CORE"), so the first is the uploader and
             // the right destination for a tap that is not offered the choice.
             if (channelId == null) {
                 val avatarChannelId = decoratedAvatar
                     ?.optJSONObject("rendererContext")
                     ?.optJSONObject("commandContext")
                     ?.optJSONObject("onTap")
                     ?.optJSONObject("innertubeCommand")
                     ?.optJSONObject("browseEndpoint")
                     ?.optString("browseId")
                     ?.takeIf { it.isNotBlank() }
                 channelId = avatarChannelId ?: collaborators.firstOrNull()?.channelId
             }

             val channelIconUrl = bestSourceUrl(
                 decoratedAvatar
                     ?.optJSONObject("avatar")
                     ?.optJSONObject("avatarViewModel")
                     ?.optJSONObject("image")
                     ?.optJSONArray("sources")
             ) ?: bestSourceUrl(
                 // Only a stack to draw from: its first avatar is the uploader's,
                 // matching the id chosen above.
                 avatarStack
                     ?.optJSONArray("avatars")
                     ?.optJSONObject(0)
                     ?.optJSONObject("avatarViewModel")
                     ?.optJSONObject("image")
                     ?.optJSONArray("sources")
             )

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
                isLive = isLive,
                dismissal = parseDismissalTokens(metadata),
                collaborators = collaborators
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * YouTube's own "Not interested" / "Don't recommend channel" tokens for a
     * lockup, out of its overflow menu.
     *
     * Signed in, every feed lockup's menu carries two `feedbackEndpoint`s, each
     * with a `feedbackToken` and - pre-baked into the notification it would
     * show - the `undoToken` that reverses it. Signed out there are none at
     * all, so this returns null and the caller simply does the local half.
     *
     * The two items are told apart by `leadingImage`'s `clientResource
     * .imageName` (`NOT_INTERESTED` vs `REMOVE`), **not** by the visible label:
     * the label is localized ("Not interested" on the home feed, "Hide" on the
     * subscriptions feed, translated on a non-English account), and matching on
     * it would silently stop working for most of the world. Verified against
     * live /next, FEwhat_to_watch and FEsubscriptions responses, August 2026.
     */
    private fun parseDismissalTokens(metadata: org.json.JSONObject?): DismissalTokens? {
        val listItems = metadata
            ?.optJSONObject("menuButton")
            ?.optJSONObject("buttonViewModel")
            ?.optJSONObject("onTap")
            ?.optJSONObject("innertubeCommand")
            ?.optJSONObject("showSheetCommand")
            ?.optJSONObject("panelLoadingStrategy")
            ?.optJSONObject("inlineContent")
            ?.optJSONObject("sheetViewModel")
            ?.optJSONObject("content")
            ?.optJSONObject("listViewModel")
            ?.optJSONArray("listItems") ?: return null

        var notInterested: String? = null
        var notInterestedUndo: String? = null
        var blockChannel: String? = null
        var blockChannelUndo: String? = null

        for (i in 0 until listItems.length()) {
            val item = listItems.optJSONObject(i)?.optJSONObject("listItemViewModel") ?: continue
            val endpoint = item
                .optJSONObject("rendererContext")
                ?.optJSONObject("commandContext")
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("feedbackEndpoint") ?: continue

            val token = endpoint.optString("feedbackToken").takeIf { it.isNotBlank() } ?: continue
            val imageName = item
                .optJSONObject("leadingImage")
                ?.optJSONArray("sources")
                ?.optJSONObject(0)
                ?.optJSONObject("clientResource")
                ?.optString("imageName")

            when (imageName) {
                "NOT_INTERESTED" -> {
                    notInterested = token
                    notInterestedUndo = parseUndoToken(endpoint)
                }
                "REMOVE" -> {
                    blockChannel = token
                    blockChannelUndo = parseUndoToken(endpoint)
                }
            }
        }

        if (notInterested == null && blockChannel == null) return null
        return DismissalTokens(
            notInterested = notInterested,
            notInterestedUndo = notInterestedUndo,
            blockChannel = blockChannel,
            blockChannelUndo = blockChannelUndo
        )
    }

    /**
     * The undo token YouTube pre-bakes into a feedback endpoint's own
     * "Video removed - Undo" notification, so undo needs no extra request.
     */
    private fun parseUndoToken(feedbackEndpoint: org.json.JSONObject): String? =
        feedbackEndpoint
            .optJSONArray("actions")
            ?.optJSONObject(0)
            ?.optJSONObject("replaceEnclosingAction")
            ?.optJSONObject("item")
            ?.optJSONObject("notificationMultiActionRenderer")
            ?.optJSONArray("buttons")
            ?.optJSONObject(0)
            ?.optJSONObject("buttonRenderer")
            ?.optJSONObject("serviceEndpoint")
            ?.optJSONObject("undoFeedbackEndpoint")
            ?.optString("undoToken")
            ?.takeIf { it.isNotBlank() }
    
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
            val streamExtractor = youtubeService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()

            // A muxed URL does not expose which language YouTube selected.
            // Do not use this last-resort path for multi-audio videos; the
            // quality resolver's separate original audio stream is the only
            // deterministic source for them.
            if (streamExtractor.audioStreams.any {
                    it.audioTrackType != null &&
                        it.audioTrackType != AudioTrackType.ORIGINAL
                }
            ) return@withContext null
            
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
            KLog.e("YouTubeRepo", "Error getting video stream", e)
            null
        }
    }

    /**
     * Get available video qualities for a video.
     */


    /**
     * WEB `/browse` on www.youtube.com, signed when there is a session and
     * anonymous when there is not.
     *
     * **It used to return an empty string the moment cookies were missing**,
     * which made every public read built on it look like empty content rather
     * than a missing session: that is what left video-mode playlists reading
     * "No videos in this playlist" for signed-out users. Public browse ids
     * (`VL<playlistId>`, a channel id) answer 200 anonymously - verified
     * August 2026 - so the account headers are what is conditional, not the
     * call. Anything account-scoped (`FEplaylist_aggregation`, `FEhistory`)
     * must gate on [SessionManager.isLoggedIn] at its own call site instead,
     * because signed out this returns a perfectly valid shell with nothing in
     * it rather than an error.
     */
    private fun fetchYouTubeBrowse(browseId: String): String {
        val cookies = sessionManager.getCookies()
        val url = "https://www.youtube.com/youtubei/v1/browse?key=$INNER_TUBE_API_KEY"

        // Generate SAPISIDHASH for www.youtube.com origin
        val authHeader = cookies?.let {
            YouTubeAuthUtils.getAuthorizationHeader(it, "https://www.youtube.com")
        }

        val visitorData = cachedVisitorDataOrNull()

        // Built through JSONObject rather than string interpolation so the
        // optional visitorData cannot produce malformed JSON.
        val jsonBody = org.json.JSONObject()
            .put(
                "context",
                org.json.JSONObject().put(
                    "client",
                    org.json.JSONObject()
                        .put("clientName", "WEB")
                        .put("clientVersion", WEB_VERSION)
                        .put("hl", "en")
                        .put("gl", "US")
                        .apply { visitorData?.let { put("visitorData", it) } }
                )
            )
            .put("browseId", browseId)
            .toString()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .addHeader("User-Agent", getRandomUserAgent())
            .addHeader("Origin", "https://www.youtube.com")
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", WEB_VERSION)
            .apply {
                visitorData?.let { addHeader("X-Goog-Visitor-Id", it) }
            }
            .apply {
                // Signed out these are the difference between a public read and
                // a malformed one: an empty Cookie or Authorization header is
                // worse than no header at all.
                if (!cookies.isNullOrBlank()) {
                    addHeader("Cookie", cookies)
                    if (!authHeader.isNullOrBlank()) addHeader("Authorization", authHeader)
                    addHeader("X-Goog-AuthUser", "0")
                }
            }
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                // This used to return the body whatever the status, so a 429
                // was handed to the parsers as a string, parsed to nothing, and
                // surfaced as empty content - indistinguishable from a real
                // empty result, and invisible to everything upstream.
                if (!response.isSuccessful) {
                    YouTubeRateLimit.note(
                        response.code,
                        "browse $browseId",
                        response.header("Retry-After"),
                    )
                    KLog.w("YouTubeRepo", "browse $browseId HTTP ${response.code}")
                    return ""
                }
                response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error in fetchYouTubeBrowse", e)
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
        // NewPipe already performs this visionOS call internally, but v0.26.5
        // drops HDR itags 330-337 because they are absent from its ItagItem
        // table. When HDR is requested, run the raw visionOS request in parallel
        // to recover those otherwise discarded formats.
        val visionOsResult = if (includeHdr) {
            async {
                try {
                    resolveVisionOsStreamingData(videoId)
                        ?.let { parseQualitiesFromStreamingData(it, includeHdr = true) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KLog.w("YouTubeRepo", "visionOS HDR resolution failed for $videoId", e)
                    null
                }
            }
        } else {
            null
        }

        // NewPipe 0.26.3+ deliberately resolves VOD streams through Android's
        // reel endpoint and a visionOS fallback, both of which avoid the WEB
        // client's SABR-only response. Keep that maintained client selection in
        // front of our older ANDROID_VR resolver: ANDROID_VR URLs now hit GVS's
        // progressive byte ceiling on long videos even though /player itself
        // succeeds, so accepting that ladder first creates a source that starts
        // normally and then dies part-way through playback.
        try {
            val extracted = getVideoStreamsFromNewPipe(videoId)
            if (extracted.qualities.isNotEmpty()) {
                val hdrQualities = if (extracted.qualities.any { it.isLive }) {
                    visionOsResult?.cancel()
                    emptyList()
                } else {
                    visionOsResult?.await().orEmpty().filter(VideoQuality::isHdr)
                }
                val merged = if (hdrQualities.isEmpty()) {
                    extracted
                } else {
                    extracted.copy(
                        qualities = deduplicateVideoQualityVariants(
                            extracted.qualities + hdrQualities
                        )
                    )
                }
                KLog.i(
                    "YouTubeRepo",
                    "Video qualities via NewPipe: ${merged.qualities.size} for $videoId" +
                        if (hdrQualities.isNotEmpty()) " (HDR=${hdrQualities.size})" else ""
                )
                return@withContext merged
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(
                "YouTubeRepo",
                "NewPipe quality resolution failed, falling back to direct InnerTube",
                e,
            )
        }

        // Last-resort fallback. It is still useful for a client-specific edge
        // case, but it must not be the normal VOD path for the reason above.
        try {
            VideoStreamResult(getVideoQualitiesFromInnerTube(videoId, includeHdr))
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

        val bestAudio = originalTrackAudioStreams(extractedAudioStreams)
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
        return VideoStreamResult(deduplicateVideoQualityVariants(qualities), seekPreview)
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
    ): List<VideoQuality> {
        val streamingData = resolvePlayerStreamingData(videoId) ?: return emptyList()
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
            val audioStreams = originalTrackAudioStreams(streamExtractor.audioStreams)
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

    /**
     * Get available video qualities for a video.
     */
    suspend fun getVideoQualities(videoId: String): List<VideoQuality> = getVideoDetails(videoId).qualities

    // ============================================================
    // Video engagement: like/dislike, subscribe, comments
    // (InnerTube WEB client against www.youtube.com)
    // ============================================================

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    /**
     * The WEB client context for www.youtube.com calls.
     *
     * Carries [cachedVisitorDataOrNull] when there is one. The app mints a
     * visitorData, persists it, TTLs it and re-mints it when the bot check
     * flags it - and for a long time used it on exactly one endpoint family
     * (/player). Every browse, next, search and engagement call went out with
     * no visitor identity at all, so from YouTube's side each was a brand new
     * anonymous client, hundreds per session from one address. That is a large
     * part of what makes a device's standing degrade over a session rather than
     * all at once.
     */
    private fun webContext(): org.json.JSONObject =
        org.json.JSONObject().put(
            "client",
            org.json.JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", WEB_VERSION)
                .put("hl", "en")
                .put("gl", "US")
                .apply {
                    cachedVisitorDataOrNull()?.let { put("visitorData", it) }
                }
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
            // A real WEB client always sends these; their absence alongside a
            // missing visitor id is most of what makes this traffic look
            // synthetic. Client name 1 is WEB.
            .addHeader("X-YouTube-Client-Name", "1")
            .addHeader("X-YouTube-Client-Version", WEB_VERSION)

        cachedVisitorDataOrNull()?.let { builder.addHeader("X-Goog-Visitor-Id", it) }

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
                    response.body?.string()?.also { noteSessionState(it) }
                } else {
                    YouTubeRateLimit.note(
                        response.code,
                        "watch api $endpoint",
                        response.header("Retry-After"),
                    )
                    KLog.w("YouTubeRepo", "watch api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "watch api $endpoint failed", e)
            null
        }
    }

    /**
     * Read YouTube's own verdict on the session out of a response.
     *
     * Every InnerTube response reports `logged_in` in its responseContext
     * tracking params. When the app sent cookies and a SAPISIDHASH and still
     * gets `0` back, the stored session is dead - which used to surface only as
     * an empty subscriptions tab and a blank account name, with no hint that
     * signing in again was what was needed. A `1` clears the flag, so a session
     * revived by a cookie rotation heals itself without a round trip through
     * the login screen.
     */
    private fun noteSessionState(body: String) {
        if (!sessionManager.isLoggedIn()) return
        val match = LOGGED_IN_TRACKING_PARAM.find(body) ?: return
        sessionManager.setSessionExpired(match.groupValues[1] == "0")
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

    /**
     * Parse chapter markers from a watch-next response. Chapters live in the
     * decorated player bar: multiMarkersPlayerBarRenderer.markersMap, keyed
     * DESCRIPTION_CHAPTERS (creator) or AUTO_CHAPTERS, each with a list of
     * chapterRenderer { title, timeRangeStartMillis, thumbnail }. Collecting
     * chapterRenderer by key returns them in document (chronological) order;
     * we sort defensively and drop anything without a title. Verified against
     * the live /next API July 2026.
     */
    private fun parseChaptersFromWatchNext(root: org.json.JSONObject): List<VideoChapter> {
        val renderers = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "chapterRenderer", renderers)
        if (renderers.isEmpty()) return emptyList()

        val chapters = renderers.mapNotNull { r ->
            val title = getRunText(r.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val startMs = r.optLong("timeRangeStartMillis", -1L)
            if (startMs < 0L) return@mapNotNull null
            val thumbs = r.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumbnailUrl = thumbs?.optJSONObject(thumbs.length() - 1)
                ?.optString("url")?.takeIf { it.isNotBlank() }
            VideoChapter(title = title, startMs = startMs, thumbnailUrl = thumbnailUrl)
        }

        // De-duplicate by start time (the same chapter list can appear twice in
        // the tree on some responses) and keep chronological order.
        return chapters
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
    }

    private fun fetchWatchNextRoot(videoId: String): org.json.JSONObject? {
        val body = org.json.JSONObject()
            .put("context", webContext())
            .put("videoId", videoId)
        val raw = postWatchApi("next", body) ?: return null
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
     * Pull the chat start token out of an already-fetched watch-next response.
     *
     * conversationBar is absent entirely when the video is not live or the
     * creator disabled chat, which is the "no chat" signal - not an error.
     *
     * A finished broadcast that kept its chat exposes the *replay* under this
     * same key. Nothing consumes that today - the panel is gated on isLive -
     * but it is the hook if replay chat is ever wanted.
     */
    private fun parseLiveChatContinuation(root: org.json.JSONObject): String? {
        val renderer = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?.optJSONObject("conversationBar")
            ?.optJSONObject("liveChatRenderer")
            ?: return null

        // Sub-menu entry 0 is "Top chat" and entry 1 "Live chat"; the top-level
        // continuation matches whichever the creator defaulted to, and is the
        // one the web player opens with.
        return renderer.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("reloadContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
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
                .put("context", webContext())
                .put("continuation", continuation)
            val raw = postWatchApi("live_chat/get_live_chat", body) ?: return@withContext null
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
            if (!isLoggedIn()) {
                return@withContext LiveChatSendResult(false, error = "Sign in to chat")
            }
            try {
                val body = org.json.JSONObject()
                    .put("context", webContext())
                    .put("params", params)
                    .put(
                        "richMessage",
                        org.json.JSONObject().put(
                            "textSegments",
                            org.json.JSONArray().put(org.json.JSONObject().put("text", text))
                        )
                    )
                    .put("clientMessageId", java.util.UUID.randomUUID().toString())
                val raw = postWatchApi("live_chat/send_message", body)
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
                .put("context", webContext())
                .put("videoId", videoId)
            val raw = postWatchApi("updated_metadata", body) ?: return@withContext null
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
     * Turn one addChatItemAction item into a [LiveChatMessage].
     *
     * Renderer frequencies measured across 59 live chats (~3.3k messages):
     * liveChatTextMessageRenderer dominates, then the system notice, then
     * giftMessageViewModel, placeholder, Super Chat and membership. The
     * placeholder is a slot for a message being moderated and carries only an
     * id - it renders as nothing, so it is dropped here.
     *
     * [fallbackOrder] backfills a sort key for giftMessageViewModel, the one
     * item that arrives without a timestamp of its own.
     */
    private fun parseLiveChatItem(item: org.json.JSONObject, fallbackOrder: Int): LiveChatMessage? {
        try {
            item.optJSONObject("liveChatTextMessageRenderer")?.let { r ->
                val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
                return LiveChatMessage.Text(
                    id = id,
                    timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                    author = parseLiveChatAuthor(r),
                    runs = parseLiveChatRuns(r.optJSONObject("message")),
                )
            }

            item.optJSONObject("liveChatPaidMessageRenderer")?.let { r ->
                val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
                return LiveChatMessage.Paid(
                    id = id,
                    timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                    author = parseLiveChatAuthor(r),
                    // An amount-only Super Chat carries no message at all.
                    runs = parseLiveChatRuns(r.optJSONObject("message")),
                    amountText = getRunText(r.optJSONObject("purchaseAmountText")).orEmpty(),
                    // Unsigned 32-bit ARGB - optInt would overflow.
                    headerBackgroundColor = r.optLong("headerBackgroundColor"),
                    headerTextColor = r.optLong("headerTextColor"),
                    bodyBackgroundColor = r.optLong("bodyBackgroundColor"),
                    bodyTextColor = r.optLong("bodyTextColor"),
                )
            }

            // A Super Sticker: same paid tier colors, but the payload is artwork
            // instead of a message, under a different set of color keys.
            item.optJSONObject("liveChatPaidStickerRenderer")?.let { r ->
                val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
                val background = r.optLong("backgroundColor")
                return LiveChatMessage.Paid(
                    id = id,
                    timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                    author = parseLiveChatAuthor(r),
                    runs = emptyList(),
                    amountText = getRunText(r.optJSONObject("purchaseAmountText")).orEmpty(),
                    headerBackgroundColor = background,
                    headerTextColor = r.optLong("authorNameTextColor"),
                    bodyBackgroundColor = background,
                    bodyTextColor = r.optLong("moneyChipTextColor"),
                    stickerUrl = widestThumbnailUrl(
                        r.optJSONObject("sticker")?.optJSONArray("thumbnails"),
                        "url"
                    )?.let { if (it.startsWith("//")) "https:$it" else it },
                )
            }

            item.optJSONObject("liveChatMembershipItemRenderer")?.let { r ->
                val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
                return LiveChatMessage.Membership(
                    id = id,
                    timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                    author = parseLiveChatAuthor(r),
                    headline = getRunText(r.optJSONObject("headerPrimaryText"))
                        ?.takeIf { it.isNotBlank() }
                        ?: "New member",
                    tierName = getRunText(r.optJSONObject("headerSubtext"))
                        ?.takeIf { it.isNotBlank() },
                )
            }

            // Gifts use the newer viewModel format: flat "content" strings
            // instead of run lists, an avatarViewModel instead of a thumbnail
            // list, and no timestamp.
            item.optJSONObject("giftMessageViewModel")?.let { vm ->
                val id = vm.optString("id").takeIf { it.isNotBlank() } ?: return null
                val avatar = vm.optJSONObject("authorAvatar")
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
                    ?.optJSONArray("sources")
                return LiveChatMessage.Gift(
                    id = id,
                    timestampUsec = fallbackOrder.toLong(),
                    author = LiveChatAuthor(
                        name = vm.optJSONObject("authorName")?.optString("content")?.trim().orEmpty(),
                        photoUrl = widestThumbnailUrl(avatar, "url"),
                    ),
                    text = vm.optJSONObject("text")?.optString("content").orEmpty(),
                    giftImageUrl = widestThumbnailUrl(vm.optJSONObject("giftImage")?.optJSONArray("sources"), "url")
                        ?.let { if (it.startsWith("//")) "https:$it" else it },
                )
            }

            item.optJSONObject("liveChatViewerEngagementMessageRenderer")?.let { r ->
                val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
                return LiveChatMessage.System(
                    id = id,
                    timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                    runs = parseLiveChatRuns(r.optJSONObject("message")),
                )
            }

            return null
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "parseLiveChatItem failed", e)
            return null
        }
    }

    /** Author name, avatar, channel id and badges, shared by every renderer. */
    private fun parseLiveChatAuthor(renderer: org.json.JSONObject): LiveChatAuthor =
        LiveChatAuthor(
            name = getRunText(renderer.optJSONObject("authorName")).orEmpty(),
            channelId = renderer.optString("authorExternalChannelId").takeIf { it.isNotBlank() },
            photoUrl = widestThumbnailUrl(
                renderer.optJSONObject("authorPhoto")?.optJSONArray("thumbnails"),
                "url"
            ),
            badges = parseLiveChatBadges(renderer.optJSONArray("authorBadges")),
        )

    /**
     * Badges come in two shapes: owner/moderator/verified as an icon.iconType,
     * and channel memberships as per-channel customThumbnail artwork whose
     * tooltip carries the tenure ("Member (1 year)").
     */
    private fun parseLiveChatBadges(badges: org.json.JSONArray?): List<LiveChatBadge> {
        if (badges == null) return emptyList()
        return (0 until badges.length()).mapNotNull { i ->
            val r = badges.optJSONObject(i)?.optJSONObject("liveChatAuthorBadgeRenderer")
                ?: return@mapNotNull null
            val tooltip = r.optString("tooltip")
            val custom = widestThumbnailUrl(
                r.optJSONObject("customThumbnail")?.optJSONArray("thumbnails"),
                "url"
            )
            if (custom != null) {
                return@mapNotNull LiveChatBadge(LiveChatBadgeKind.MEMBER, tooltip, custom)
            }
            val kind = when (r.optJSONObject("icon")?.optString("iconType")) {
                "OWNER" -> LiveChatBadgeKind.OWNER
                "MODERATOR" -> LiveChatBadgeKind.MODERATOR
                "VERIFIED" -> LiveChatBadgeKind.VERIFIED
                else -> return@mapNotNull null
            }
            LiveChatBadge(kind, tooltip)
        }
    }

    /**
     * Split a chat message into text and emoji runs.
     *
     * Standard unicode emoji carry the character itself in emojiId and need no
     * image; channel-custom emoji have an opaque id and must be drawn from the
     * thumbnail, so the two cases are distinguished rather than flattened.
     */
    private fun parseLiveChatRuns(message: org.json.JSONObject?): List<LiveChatRun> {
        if (message == null) return emptyList()
        message.optString("simpleText").takeIf { it.isNotBlank() }?.let {
            return listOf(LiveChatRun.Text(it))
        }
        val runs = message.optJSONArray("runs") ?: return emptyList()
        return (0 until runs.length()).mapNotNull { i ->
            val run = runs.optJSONObject(i) ?: return@mapNotNull null
            val emoji = run.optJSONObject("emoji")
            if (emoji != null) {
                val emojiId = emoji.optString("emojiId")
                val label = emoji.optJSONArray("shortcuts")?.optString(0)?.takeIf { it.isNotBlank() }
                    ?: emojiId
                // A unicode emoji's id is the character; anything longer is an
                // opaque channel emoji key that must render as artwork.
                val isUnicode = emojiId.isNotEmpty() && emojiId.codePointCount(0, emojiId.length) <= 2
                LiveChatRun.Emoji(
                    label = if (isUnicode) emojiId else label,
                    imageUrl = if (isUnicode) null else widestThumbnailUrl(
                        emoji.optJSONObject("image")?.optJSONArray("thumbnails"),
                        "url"
                    ),
                )
            } else {
                run.optString("text").takeIf { it.isNotEmpty() }?.let { LiveChatRun.Text(it) }
            }
        }
    }

    /**
     * The pinned card: either a message the creator pinned
     * (liveChatTextMessageRenderer) or the auto-generated chat summary
     * (liveChatBannerChatSummaryRenderer).
     */
    private fun parseLiveChatBanner(banner: org.json.JSONObject): LiveChatBanner? {
        val contents = banner.optJSONObject("contents") ?: return null
        contents.optJSONObject("liveChatBannerChatSummaryRenderer")?.let { summary ->
            return LiveChatBanner(
                id = summary.optString("liveChatSummaryId").takeIf { it.isNotBlank() }
                    ?: banner.optString("actionId"),
                author = null,
                runs = parseLiveChatRuns(summary.optJSONObject("chatSummary")),
                isSummary = true,
            )
        }
        contents.optJSONObject("liveChatTextMessageRenderer")?.let { pinned ->
            return LiveChatBanner(
                id = pinned.optString("id").takeIf { it.isNotBlank() }
                    ?: banner.optString("actionId"),
                author = parseLiveChatAuthor(pinned),
                runs = parseLiveChatRuns(pinned.optJSONObject("message")),
                isSummary = false,
            )
        }
        return null
    }

    /** Widest entry of a thumbnail/source array, which is the highest quality. */
    private fun widestThumbnailUrl(array: org.json.JSONArray?, urlKey: String): String? {
        if (array == null) return null
        var best: String? = null
        var bestWidth = -1
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val url = entry.optString(urlKey).takeIf { it.isNotBlank() } ?: continue
            val width = entry.optInt("width", 0)
            if (width >= bestWidth) {
                bestWidth = width
                best = url
            }
        }
        return best
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
     * Normally free: [runPlayerClientChain] already caches the tracklist from
     * the /player response fetched to start playback, so this only hits the
     * network when that cache missed or went stale.
     */
    suspend fun getCaptionTracks(videoId: String): List<CaptionTrack> = withContext(Dispatchers.IO) {
        cachedCaptionTracks(videoId)?.let { return@withContext it }
        try {
            val visitorData = getVisitorData()
            val vr = fetchPlayerResponse(
                videoId = videoId,
                clientName = "ANDROID_VR",
                clientVersion = ANDROID_VR_VERSION,
                clientNameId = ANDROID_VR_CLIENT_ID,
                userAgent = ANDROID_VR_USER_AGENT,
                visitorData = visitorData,
                extraClientFields = androidVrClientFields(),
            )
            val tracks = vr.captionTracks.ifEmpty {
                fetchPlayerResponse(
                    videoId = videoId,
                    clientName = "IOS",
                    clientVersion = IOS_VERSION,
                    clientNameId = IOS_CLIENT_ID,
                    userAgent = IOS_USER_AGENT,
                    visitorData = visitorData,
                    extraClientFields = iosClientFields(),
                ).captionTracks
            }
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
    suspend fun getCaptionCues(track: CaptionTrack): List<VttCue> = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(track.vttUrl)
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    KLog.w(
                        "YouTubeRepo",
                        "Caption fetch failed for ${track.languageCode}: HTTP ${response.code}"
                    )
                    return@withContext emptyList()
                }
                val body = response.body?.string().orEmpty()
                WebVttParser.parse(body)
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "getCaptionCues failed for ${track.languageCode}", e)
            emptyList()
        }
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
                // A collab video has no owner thumbnail at all, only a stack of
                // every collaborator's; the first of them is the uploader.
                ?: owner?.optJSONObject("avatarStack")
                    ?.optJSONObject("avatarStackViewModel")
                    ?.optJSONArray("avatars")
                    ?.optJSONObject(0)
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
                    ?.optJSONArray("sources")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() }
            val subscriberCount = getRunText(owner?.optJSONObject("subscriberCountText"))
                ?.takeIf { it.isNotBlank() }
            // attributedDescription carries commandRuns marking every link,
            // hashtag and timestamp with exact UTF-16 offsets, so the
            // description arrives already linkified (see parseRichText).
            val richDescription = parseRichText(
                secondaryInfo?.optJSONObject("attributedDescription")
            )
            val description = richDescription.text.takeIf { it.isNotBlank() }

            VideoItem(
                videoId = videoId,
                title = getRunText(primary?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                    ?: baseVideo?.title ?: "Unknown",
                channelName = getRunText(owner?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
                    // A collab video names no owner; its byline ("KSI and 2
                    // more") is attributed text, already localized by YouTube,
                    // which is why it is used rather than assembled here.
                    ?: owner?.optJSONObject("attributedTitle")?.optString("content")
                        ?.takeIf { it.isNotBlank() }
                    ?: baseVideo?.channelName ?: "Unknown",
                channelId = channelId ?: baseVideo?.channelId,
                channelIconUrl = channelIconUrl ?: baseVideo?.channelIconUrl,
                // Carried on the item as well as on the engagement so that a
                // surface holding only the VideoItem - the queue, the mini
                // player, a related card - can still resolve the creator.
                collaborators = parseCollaborators(owner)
                    .ifEmpty { baseVideo?.collaborators.orEmpty() },
                thumbnailUrl = baseVideo?.thumbnailUrl
                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                duration = baseVideo?.duration ?: 0L,
                viewCount = viewCount ?: baseVideo?.viewCount ?: "",
                uploadedDate = uploadedDate ?: baseVideo?.uploadedDate,
                isLive = baseVideo?.isLive ?: false,
                description = description ?: baseVideo?.description,
                subscriberCount = subscriberCount ?: baseVideo?.subscriberCount,
                // Only valid alongside the description they were measured
                // against; a fallback description has no matching offsets.
                descriptionLinks = if (description != null) richDescription.links else emptyList()
            )
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "watch-next metadata parse failed for $videoId", e)
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
                commentsToken = commentsToken,
                collaborators = parseCollaborators(owner)
            )
    }

    /**
     * Collaborators credited on a collab video, read out of the dialog the
     * owner renderer carries inline.
     *
     * [verified September 2026, signed out] A collab `videoOwnerRenderer` has no
     * `title`, `thumbnail` or `browseEndpoint` at all; the whole creator block is
     *
     * ```
     * videoOwnerRenderer
     *   attributedTitle.content              "KSI and 2 more"
     *   avatarStack.avatarStackViewModel     one avatar per collaborator
     *   navigationEndpoint.showDialogCommand.panelLoadingStrategy
     *     .inlineContent.dialogViewModel.customContent.listViewModel.listItems[]
     *       listItemViewModel
     *         title.content                  channel name
     *         title.commandRuns[0]...browseEndpoint.browseId
     *         title.attachmentRuns[]         CHECK_CIRCLE_FILLED when verified
     *         subtitle.content               "@handle • 19M subscribers"
     *         leadingAccessory.avatarViewModel.image.sources[0].url
     * ```
     *
     * The dialog is reached structurally rather than by matching its "Collaborators"
     * headline, which is localized. A row with no browse id is dropped: without an
     * id there is nothing to open, and a row that looks tappable and does nothing
     * is worse than one that is not listed.
     *
     * A feed lockup carries the same dialog under a different host - see
     * [collaboratorsFromDialogHost], which both entry points share.
     */
    private fun parseCollaborators(owner: org.json.JSONObject?): List<VideoCollaborator> =
        collaboratorsFromDialogHost(owner)

    /**
     * The collaborator rows out of whatever `showDialogCommand` [host] carries.
     *
     * Two hosts carry the same dialog and neither can be assumed [verified
     * September 2026, signed out, against a live collab upload]:
     *
     * - the watch page's `videoOwnerRenderer`, on its `navigationEndpoint`;
     * - a feed lockup's `lockupMetadataViewModel.image.avatarStackViewModel`,
     *   on its `rendererContext.commandContext.onTap`, where the row command
     *   sits on each list item rather than on its title text.
     *
     * The nesting between the host and the dialog differs, so the dialog is
     * located by descending to it rather than by spelling out one path: what is
     * structural here is that a collab's channels are the list inside the only
     * dialog the creator block carries, not the exact depth YouTube nests it at.
     * [host] is always the creator block itself, never a whole response, which
     * is what keeps that search from reaching an unrelated dialog.
     */
    private fun collaboratorsFromDialogHost(host: org.json.JSONObject?): List<VideoCollaborator> {
        if (host == null) return emptyList()
        return try {
            val dialogs = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(host, "dialogViewModel", dialogs)
            val items = dialogs.firstNotNullOfOrNull { dialog ->
                dialog.optJSONObject("customContent")
                    ?.optJSONObject("listViewModel")
                    ?.optJSONArray("listItems")
            } ?: return emptyList()

            (0 until items.length()).mapNotNull { index ->
                val item = items.optJSONObject(index)?.optJSONObject("listItemViewModel")
                    ?: return@mapNotNull null
                val titleObj = item.optJSONObject("title")
                val name = titleObj?.optString("content")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val channelId = titleObj.optJSONArray("commandRuns")
                    ?.optJSONObject(0)
                    ?.optJSONObject("onTap")
                    ?.optJSONObject("innertubeCommand")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
                    // The lockup variant of the same dialog puts the command on
                    // the row rather than on the title text.
                    ?: item.optJSONObject("rendererContext")
                        ?.optJSONObject("commandContext")
                        ?.optJSONObject("onTap")
                        ?.optJSONObject("innertubeCommand")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                // "@KSI • 19M subscribers", wrapped in bidi isolates that would
                // otherwise land in the middle of the visible strings.
                val subtitle = item.optJSONObject("subtitle")?.optString("content")
                    ?.filterNot { it in BIDI_CONTROL_CHARS }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val subtitleParts = subtitle?.split("•")?.map { it.trim() }.orEmpty()
                val handle = subtitleParts.firstOrNull { it.startsWith("@") }
                val subscriberCount = subtitleParts.firstOrNull { !it.startsWith("@") }
                    ?.takeIf { it.isNotBlank() }

                VideoCollaborator(
                    channelId = channelId,
                    name = name,
                    handle = handle,
                    subscriberCount = subscriberCount,
                    avatarUrl = item.optJSONObject("leadingAccessory")
                        ?.optJSONObject("avatarViewModel")
                        ?.optJSONObject("image")
                        ?.optJSONArray("sources")
                        ?.optJSONObject(0)
                        ?.optString("url")
                        ?.takeIf { it.isNotBlank() },
                    isVerified = hasVerifiedAttachment(titleObj)
                )
            }
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "collaborator parse failed", e)
            emptyList()
        }
    }

    /**
     * Bidi marks and isolates YouTube wraps around interpolated values.
     *
     * They are correct on a web page - they stop an RTL channel handle from
     * reordering the text around it - and invisible garbage the moment the
     * string is split on a separator or measured for layout, so they come off
     * before the parts are used.
     */
    private val BIDI_CONTROL_CHARS = setOf(
        '\u200E', // LEFT-TO-RIGHT MARK
        '\u200F', // RIGHT-TO-LEFT MARK
        '\u061C', // ARABIC LETTER MARK
        '\u2066', // LEFT-TO-RIGHT ISOLATE
        '\u2067', // RIGHT-TO-LEFT ISOLATE
        '\u2068', // FIRST STRONG ISOLATE
        '\u2069'  // POP DIRECTIONAL ISOLATE
    )

    /** True when an attributed title carries YouTube's verified-badge glyph. */
    private fun hasVerifiedAttachment(titleObj: org.json.JSONObject?): Boolean {
        val runs = titleObj?.optJSONArray("attachmentRuns") ?: return false
        for (i in 0 until runs.length()) {
            val sources = runs.optJSONObject(i)
                ?.optJSONObject("element")
                ?.optJSONObject("type")
                ?.optJSONObject("imageType")
                ?.optJSONObject("image")
                ?.optJSONArray("sources") ?: continue
            for (j in 0 until sources.length()) {
                val imageName = sources.optJSONObject(j)
                    ?.optJSONObject("clientResource")
                    ?.optString("imageName")
                if (imageName == "CHECK_CIRCLE_FILLED") return true
            }
        }
        return false
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
            KLog.e("YouTubeRepo", "getCommentsPage failed", e)
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

        // Comment bodies carry the same attributed-text shape as descriptions:
        // timestamps arrive as watchEndpoint runs with startTimeSeconds, so the
        // clickable ranges never have to be pattern-matched out of the prose.
        val content = parseRichText(props?.optJSONObject("content"))

        return CommentItem(
            commentId = id,
            text = content.text,
            links = content.links,
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
                    KLog.w("YouTubeRepo", "music api $endpoint HTTP ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "music api $endpoint failed", e)
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
     * Fetch the per-row playlist item ids ("setVideoId") for a playlist the
     * user can edit. Reordering via edit_playlist identifies rows by these,
     * not by videoId. Values stay occurrence-ordered because duplicate videos
     * are separate rows with separate setVideoIds. Browses VL<id> on music.youtube.com and reads
     * musicResponsiveListItemRenderer.playlistItemData. First page only
     * (~100 rows); rows past that simply stay un-movable. Verified July 2026.
     */
    suspend fun getPlaylistSetVideoIds(playlistId: String): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            if (!sessionManager.isLoggedIn()) return@withContext emptyMap()
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val raw = browseMusic(browseId) ?: return@withContext emptyMap()
            try {
                val rows = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(org.json.JSONObject(raw), "musicResponsiveListItemRenderer", rows)
                val idsByVideo = linkedMapOf<String, MutableList<String>>()
                for (row in rows) {
                    val itemData = row.optJSONObject("playlistItemData") ?: continue
                    val videoId = itemData.optString("videoId").takeIf { it.isNotBlank() } ?: continue
                    val setVideoId = itemData.optString("playlistSetVideoId")
                        .takeIf { it.isNotBlank() } ?: continue
                    idsByVideo.getOrPut(videoId) { mutableListOf() }.add(setVideoId)
                }
                idsByVideo.mapValues { (_, ids) -> ids.toList() }
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
            KLog.e("YouTubeRepo", "parseCreatedComment failed", e)
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
        if (IncognitoMode.isEnabled(context)) return@withContext
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
                KLog.w("YouTubeRepo", "No videostatsPlaybackUrl for $videoId")
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
                    KLog.d("YouTubeRepo", "Video history sync SUCCESS for $videoId")
                } else {
                    KLog.w("YouTubeRepo", "Video history sync failed: ${response.code}")
                }
            }
        } catch (e: Exception) {
            KLog.e("YouTubeRepo", "Error in reportVideoPlayback", e)
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
            KLog.e("YouTubeRepo", "getSubscriptionsFeed failed", e)
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
                    // Verified again August 2026 - both fields are present on every
                    // renderer, so the handle is free here and account channels are
                    // searchable by it exactly like device-local ones.
                    val subscriberCount = getRunText(renderer.optJSONObject("videoCountText"))
                        ?.takeIf { it.isNotBlank() }
                    val handle = getRunText(renderer.optJSONObject("subscriberCountText"))
                        ?.takeIf { it.startsWith("@") }
                    channels.add(
                        SubscribedChannel(channelId, name, avatarUrl, subscriberCount, handle)
                    )
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
            KLog.e("YouTubeRepo", "getChannelVideos failed", e)
            emptyList()
        }
    }

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
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webContext()).put("browseId", channelId)
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
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webContext())
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
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webContext()).put("continuation", token)
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
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject()
                    .put("context", webContext())
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
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webContext()).put("continuation", token)
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

    /**
     * Channel identity out of `pageHeaderViewModel`, with the metadata block as
     * the backstop.
     *
     * Two sources on purpose. The header is the visible one and carries the
     * banner, the verified tick and the subscriber count, but it is also the
     * half that gets redesigned - `c4TabbedHeaderRenderer` was replaced
     * wholesale, and some channels still answer with it. The metadata block has
     * outlived every one of those redesigns, so the name, id, avatar and handle
     * are taken from whichever of the two has them and the page survives a
     * header that parses to nothing.
     */
    private fun parseChannelHeader(
        root: org.json.JSONObject,
        fallbackChannelId: String
    ): ChannelHeader? {
        val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
        val view = root.optJSONObject("header")
            ?.optJSONObject("pageHeaderRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("pageHeaderViewModel")

        val channelId = metadata?.optString("externalId")?.takeIf { it.isNotBlank() }
            ?: fallbackChannelId
        val name = view?.optJSONObject("title")
            ?.optJSONObject("dynamicTextViewModel")
            ?.optJSONObject("text")
            ?.optString("content")?.takeIf { it.isNotBlank() }
            ?: metadata?.optString("title")?.takeIf { it.isNotBlank() }
            ?: run {
                // Legacy header, still served by a minority of channels
                val legacy = root.optJSONObject("header")
                    ?.optJSONObject("c4TabbedHeaderRenderer")
                getRunText(legacy?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
            }
            ?: return null

        // The metadata rows are "@handle" then "N subscribers" + "N videos",
        // but a channel with no handle simply omits the first row, so the parts
        // are classified by what they say rather than by where they sit.
        var handle: String? = null
        var subscriberCountText: String? = null
        var videoCountText: String? = null
        val rows = view?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        if (rows != null) {
            for (r in 0 until rows.length()) {
                val parts = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until parts.length()) {
                    val text = parts.optJSONObject(p)?.optJSONObject("text")
                        ?.optString("content")?.takeIf { it.isNotBlank() } ?: continue
                    when {
                        text.startsWith("@") -> handle = text
                        text.contains("subscriber", ignoreCase = true) ->
                            subscriberCountText = text
                        text.contains("video", ignoreCase = true) -> videoCountText = text
                    }
                }
            }
        }
        if (handle == null) {
            handle = metadata?.optString("vanityChannelUrl")
                ?.substringAfterLast('/')
                ?.takeIf { it.startsWith("@") }
        }

        val avatarUrl = view?.optJSONObject("image")
            ?.optJSONObject("decoratedAvatarViewModel")
            ?.optJSONObject("avatar")
            ?.optJSONObject("avatarViewModel")
            ?.optJSONObject("image")
            ?.let { bestImageSource(it.optJSONArray("sources")) }
            ?: metadata?.optJSONObject("avatar")?.optJSONArray("thumbnails")
                ?.let { bestThumbnail(it) }

        // Absent on plenty of channels; the screen draws its own backdrop
        // rather than treating a missing banner as a load that never finished.
        val bannerUrl = view?.optJSONObject("banner")
            ?.optJSONObject("imageBannerViewModel")
            ?.optJSONObject("image")
            ?.let { bestImageSource(it.optJSONArray("sources")) }

        // The tick arrives as an attachment run on the title text, identified
        // by a client resource name rather than by any visible label.
        val isVerified = runCatching {
            val attachments = view?.optJSONObject("title")
                ?.optJSONObject("dynamicTextViewModel")
                ?.optJSONObject("text")
                ?.optJSONArray("attachmentRuns") ?: return@runCatching false
            val names = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(attachments, "clientResource", names)
            names.any { it.optString("imageName").startsWith("CHECK_CIRCLE") }
        }.getOrDefault(false)

        val attribution = view?.optJSONObject("attribution")
            ?.optJSONObject("attributionViewModel")
            ?.optJSONObject("text")
        val attributionText = attribution?.optString("content")?.trim()
            ?.takeIf { it.isNotBlank() }
        val attributionUrl = attribution
            ?.optJSONArray("commandRuns")?.optJSONObject(0)
            ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("urlEndpoint")?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?.let { unwrapYouTubeRedirect(it) }

        val descriptionPreview = view?.optJSONObject("description")
            ?.optJSONObject("descriptionPreviewViewModel")
            ?.optJSONObject("description")
            ?.optString("content")?.takeIf { it.isNotBlank() }
            ?: metadata?.optString("description")?.takeIf { it.isNotBlank() }

        val aboutToken = view?.optJSONObject("description")
            ?.optJSONObject("descriptionPreviewViewModel")
            ?.optJSONObject("rendererContext")
            ?.optJSONObject("commandContext")
            ?.optJSONObject("onTap")
            ?.optJSONObject("innertubeCommand")
            ?.let { command ->
                val tokens = mutableListOf<String>()
                findContinuationTokens(command, tokens)
                tokens.firstOrNull()
            }

        return ChannelHeader(
            channelId = channelId,
            name = name,
            handle = handle,
            avatarUrl = avatarUrl,
            bannerUrl = bannerUrl,
            subscriberCountText = subscriberCountText,
            videoCountText = videoCountText,
            descriptionPreview = descriptionPreview,
            isVerified = isVerified,
            attributionText = attributionText,
            attributionUrl = attributionUrl,
            aboutToken = aboutToken,
            accountSubscribed = parseChannelSubscribedState(root)
        )
    }

    /**
     * Whether the signed-in account follows this channel, if the response says.
     *
     * The channel browse already carries this - the header's Subscribe button
     * has to be drawn in the right state - so reading it here means the channel
     * screen costs no extra request to know. Two shapes are checked because
     * both are live: the modern subscribe button keeps its state in a framework
     * entity mutation, and the legacy `subscribeButtonRenderer` keeps a plain
     * boolean.
     *
     * Returns null rather than false when neither is present, so a shape change
     * reads as "unknown" and the caller falls back to asking, instead of
     * quietly showing "Subscribe" for a channel the user follows.
     */
    private fun parseChannelSubscribedState(root: org.json.JSONObject): Boolean? {
        val entities = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "subscriptionStateEntity", entities)
        entities.firstOrNull { it.has("subscribed") }
            ?.let { return it.optBoolean("subscribed") }

        val legacy = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "subscribeButtonRenderer", legacy)
        legacy.firstOrNull { it.has("subscribed") }
            ?.let { return it.optBoolean("subscribed") }

        return null
    }

    /**
     * The tab list, each with the `params` that fetches it.
     *
     * Tabs are classified by their `params` prefix, never by their title: the
     * title is localized, so a screen that matched on the English word would
     * fall back to a generic layout for most of the world. Unrecognised tabs
     * are kept as [ChannelTabKind.OTHER] rather than dropped, which is what
     * lets "Store", "Courses" and "Podcasts" render without code of their own.
     */
    private fun parseChannelTabs(root: org.json.JSONObject): List<ChannelTab> {
        val renderers = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(root, "tabRenderer", renderers)
        findObjectsByKey(root, "expandableTabRenderer", renderers)
        return renderers.mapNotNull { tab ->
            val title = tab.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val params = tab.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("params")
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ChannelTab(channelTabKind(params), title, params)
        }.distinctBy { it.params }
    }

    /**
     * Which tab a `params` value addresses.
     *
     * The values are stable base64 of a short protobuf whose first field is the
     * tab name in plain ASCII ("videos", "shorts", "streams", ...), which is
     * why a prefix match on the encoded string is reliable and why sort
     * variants of the same tab (which append fields) still match.
     */
    private fun channelTabKind(params: String): ChannelTabKind = when {
        params.startsWith("EghmZWF0dXJlZ") -> ChannelTabKind.HOME
        params.startsWith("EgZ2aWRlb3") -> ChannelTabKind.VIDEOS
        params.startsWith("EgZzaG9ydH") -> ChannelTabKind.SHORTS
        params.startsWith("EgdzdHJlYW1z") -> ChannelTabKind.LIVE
        params.startsWith("EglwbGF5bGlzdH") -> ChannelTabKind.PLAYLISTS
        params.startsWith("EgVwb3N0c") -> ChannelTabKind.POSTS
        params.startsWith("EghyZWxlYXNlc") -> ChannelTabKind.RELEASES
        params.startsWith("EgZzZWFyY2") -> ChannelTabKind.SEARCH
        else -> ChannelTabKind.OTHER
    }

    /**
     * The selected tab's content subtree, so the item search below is scoped to
     * it instead of to the whole document.
     *
     * Scoping matters on the Home tab, where the response also carries the
     * header's own thumbnails and several dialogs; a document-wide search picks
     * items out of surfaces the user is not looking at.
     */
    private fun parseSelectedTab(
        root: org.json.JSONObject
    ): Pair<ChannelTabKind, org.json.JSONObject>? {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs") ?: return null
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer")
                ?: tabs.optJSONObject(i)?.optJSONObject("expandableTabRenderer")
                ?: continue
            if (!tab.optBoolean("selected", false)) continue
            val content = tab.optJSONObject("content") ?: continue
            val params = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                ?.optString("params").orEmpty()
            return channelTabKind(params) to content
        }
        return null
    }

    /**
     * Items, shelves, sort options and the next-page token out of one tab
     * response (or one continuation response - both shapes land here).
     *
     * Deliberately not switched on the tab kind. Every tab is parsed for every
     * item type it might hold and the empty lists cost nothing, which is what
     * makes an unrecognised tab like "Courses" render correctly without anyone
     * having taught this function about it.
     */
    private fun parseChannelTabPage(
        scope: org.json.JSONObject,
        header: ChannelHeader?
    ): ChannelTabPage {
        val shelves = parseChannelShelves(scope, header)

        // Shelved items are already accounted for above, so the flat lists are
        // built from what is left. Without this the Home tab would show every
        // video twice - once in its shelf and once in a flat grid below it.
        val shelvedVideoIds = shelves.flatMap { it.videos }.map { it.videoId }.toSet()
        val shelvedShortIds = shelves.flatMap { it.shorts }.map { it.videoId }.toSet()
        val shelvedPlaylistIds = shelves.flatMap { it.playlists }.map { it.playlistId }.toSet()
        val shelvedPostIds = shelves.flatMap { it.posts }.map { it.postId }.toSet()

        val lockups = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "lockupViewModel", lockups)
        val legacyVideos = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "videoRenderer", legacyVideos)
        val shortLockups = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "shortsLockupViewModel", shortLockups)
        val postRenderers = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "backstagePostRenderer", postRenderers)

        val videos = (
            lockups.mapNotNull { parseLockupViewModel(it) } +
                legacyVideos.mapNotNull { parseVideoRenderer(it) }
            )
            .map { stitchChannelIdentity(it, header) }
            .distinctBy { it.videoId }
            .filterNot { it.videoId in shelvedVideoIds }

        val shorts = shortLockups.mapNotNull { parseShortsLockup(it) }
            .distinctBy { it.videoId }
            .filterNot { it.videoId in shelvedShortIds }

        val playlists = lockups.mapNotNull { parsePlaylistLockup(it) }
            .distinctBy { it.playlistId }
            .filterNot { it.playlistId in shelvedPlaylistIds }

        val posts = postRenderers.mapNotNull { parseBackstagePost(it) }
            .distinctBy { it.postId }
            .filterNot { it.postId in shelvedPostIds }

        val featured = scope.optJSONObject("channelVideoPlayerRenderer")
            ?.let { parseChannelFeaturedVideo(it, header) }
            ?: run {
                val players = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(scope, "channelVideoPlayerRenderer", players)
                players.firstOrNull()?.let { parseChannelFeaturedVideo(it, header) }
            }

        return ChannelTabPage(
            videos = videos,
            shorts = shorts,
            playlists = playlists,
            posts = posts,
            shelves = shelves,
            featured = featured,
            sortOptions = parseChannelSortOptions(scope),
            continuation = parseChannelNextPageToken(scope)
        )
    }

    /**
     * Channel-page cards omit the channel row, because on YouTube's own layout
     * the channel is the page you are standing on. Every consumer downstream
     * (the queue, the options sheet, "don't recommend this channel") expects an
     * item to know whose it is, so the caller's identity is stitched back in.
     *
     * **An item that already carries a channel id keeps it.** A card that names
     * its channel is a card from somewhere else - the "Collaborations" and
     * "Featured channels" shelves are full of them - and overwriting that would
     * file another creator's video under this one, which then follows the item
     * into the queue and into a "don't recommend this channel" tap on the wrong
     * channel.
     */
    private fun stitchChannelIdentity(item: VideoItem, header: ChannelHeader?): VideoItem {
        if (header == null || item.channelId != null) return item
        // With no channel row present, the generic parser has read the first
        // metadata row as the channel name, and on a channel page that row is
        // "N views - date" instead.
        val misreadAsChannel = item.channelName.contains("view", ignoreCase = true) ||
            item.channelName.contains("watching", ignoreCase = true)
        return item.copy(
            channelName = header.name,
            channelId = header.channelId,
            channelIconUrl = item.channelIconUrl ?: header.avatarUrl,
            viewCount = if (item.viewCount.isBlank() && misreadAsChannel) {
                item.channelName
            } else {
                item.viewCount
            }
        )
    }

    /** The video a channel pins to the top of its Home tab. */
    private fun parseChannelFeaturedVideo(
        renderer: org.json.JSONObject,
        header: ChannelHeader?
    ): VideoItem? {
        val videoId = renderer.optString("videoId").takeIf { it.length == 11 } ?: return null
        val title = getRunText(renderer.optJSONObject("title"))?.takeIf { it.isNotBlank() }
            ?: return null
        val viewCount = getRunText(renderer.optJSONObject("viewCountText")).orEmpty()
        val uploadedDate = getRunText(renderer.optJSONObject("publishedTimeText"))
            ?.takeIf { it.isNotBlank() }
        return VideoItem(
            videoId = videoId,
            title = title,
            channelName = header?.name.orEmpty(),
            channelId = header?.channelId,
            channelIconUrl = header?.avatarUrl,
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            duration = 0L,
            viewCount = viewCount,
            uploadedDate = uploadedDate,
            description = getRunText(renderer.optJSONObject("description"))
        )
    }

    /**
     * Home-tab shelves, in the order the channel arranged them.
     *
     * Read out of the section list rather than by a document-wide key search,
     * because order is the whole point of this tab - a channel decides what
     * sits at the top - and a key search returns whatever traversal order
     * happens to be.
     */
    private fun parseChannelShelves(
        scope: org.json.JSONObject,
        header: ChannelHeader?
    ): List<ChannelShelf> {
        val sections = scope.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            ?: return emptyList()
        val shelves = mutableListOf<ChannelShelf>()
        for (s in 0 until sections.length()) {
            val contents = sections.optJSONObject(s)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents") ?: continue
            for (c in 0 until contents.length()) {
                val entry = contents.optJSONObject(c) ?: continue
                val shelf = entry.optJSONObject("shelfRenderer")
                    ?: entry.optJSONObject("reelShelfRenderer")
                    ?: continue
                val title = getRunText(shelf.optJSONObject("title"))
                    ?: shelf.optJSONObject("title")?.optString("simpleText")
                    ?: continue

                val items = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(shelf, "lockupViewModel", items)
                val shortItems = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(shelf, "shortsLockupViewModel", shortItems)
                val postItems = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(shelf, "backstagePostRenderer", postItems)
                val channelItems = mutableListOf<org.json.JSONObject>()
                findObjectsByKey(shelf, "gridChannelRenderer", channelItems)

                val built = ChannelShelf(
                    title = title,
                    videos = items.mapNotNull { parseLockupViewModel(it) }
                        .map { stitchChannelIdentity(it, header) }
                        .distinctBy { it.videoId },
                    shorts = shortItems.mapNotNull { parseShortsLockup(it) }
                        .distinctBy { it.videoId },
                    playlists = items.mapNotNull { parsePlaylistLockup(it) }
                        .distinctBy { it.playlistId },
                    posts = postItems.mapNotNull { parseBackstagePost(it) }
                        .distinctBy { it.postId },
                    channels = channelItems.mapNotNull { parseGridChannel(it) }
                        .distinctBy { it.channelId }
                )
                if (!built.isEmpty) shelves.add(built)
            }
        }
        return shelves
    }

    /** A channel card in a "Featured channels" shelf. */
    private fun parseGridChannel(renderer: org.json.JSONObject): SubscribedChannel? {
        val channelId = renderer.optString("channelId").takeIf { it.isNotBlank() } ?: return null
        val name = getRunText(renderer.optJSONObject("title"))
            ?: renderer.optJSONObject("title")?.optString("simpleText")
            ?: return null
        val avatarUrl = bestThumbnail(
            renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        )
        return SubscribedChannel(
            channelId = channelId,
            name = name,
            avatarUrl = avatarUrl,
            subscriberCountText = getRunText(renderer.optJSONObject("subscriberCountText"))
        )
    }

    /**
     * A community post, with whichever single attachment it carried.
     *
     * `contentText` is a legacy run list rather than the attributed-text shape
     * [parseRichText] takes, so the runs are folded into the same [RichText]
     * here - a post is full of links to videos and channels, and flattening it
     * to a String would throw all of them away.
     */
    private fun parseBackstagePost(renderer: org.json.JSONObject): ChannelPost? {
        val postId = renderer.optString("postId").takeIf { it.isNotBlank() } ?: return null
        val authorName = getRunText(renderer.optJSONObject("authorText")).orEmpty()
        val authorAvatarUrl = bestThumbnail(
            renderer.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")
        )

        val text = parseRunListAsRichText(renderer.optJSONObject("contentText"))

        val attachment = renderer.optJSONObject("backstageAttachment")
        val images = mutableListOf<String>()
        attachment?.optJSONObject("backstageImageRenderer")
            ?.optJSONObject("image")?.optJSONArray("thumbnails")
            ?.let { bestThumbnail(it) }
            ?.let { images.add(it) }
        attachment?.optJSONObject("postMultiImageRenderer")?.optJSONArray("images")
            ?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.optJSONObject("backstageImageRenderer")
                        ?.optJSONObject("image")?.optJSONArray("thumbnails")
                        ?.let { bestThumbnail(it) }
                        ?.let { images.add(it) }
                }
            }

        val video = attachment?.optJSONObject("videoRenderer")?.let { parseVideoRenderer(it) }

        val poll = attachment?.optJSONObject("pollRenderer")
        val pollChoices = poll?.optJSONArray("choices")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val choice = array.optJSONObject(i) ?: return@mapNotNull null
                val label = getRunText(choice.optJSONObject("text"))
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ChannelPollChoice(
                    text = label,
                    imageUrl = bestThumbnail(
                        choice.optJSONObject("image")?.optJSONArray("thumbnails")
                    )
                )
            }
        }.orEmpty()

        return ChannelPost(
            postId = postId,
            authorName = authorName,
            authorAvatarUrl = authorAvatarUrl,
            text = text,
            publishedText = getRunText(renderer.optJSONObject("publishedTimeText")),
            voteCountText = renderer.optJSONObject("voteCount")?.optString("simpleText")
                ?.takeIf { it.isNotBlank() },
            replyCountText = renderer.optJSONObject("actionButtons")
                ?.optJSONObject("commentActionButtonsRenderer")
                ?.optJSONObject("replyButton")
                ?.optJSONObject("buttonRenderer")
                ?.let { getRunText(it.optJSONObject("text")) },
            images = images,
            video = video,
            pollChoices = pollChoices,
            pollTotalText = getRunText(poll?.optJSONObject("totalVotes"))
        )
    }

    /**
     * A legacy `{runs: [...]}` text object as [RichText], keeping the links.
     *
     * Offsets are accumulated as the runs are concatenated, which is the same
     * UTF-16 indexing [parseRichText] documents - Kotlin's String indices are
     * UTF-16 code units, so an emoji in a post does not shift the spans.
     */
    private fun parseRunListAsRichText(node: org.json.JSONObject?): RichText {
        val runs = node?.optJSONArray("runs")
            ?: return RichText(node?.optString("simpleText").orEmpty())
        val builder = StringBuilder()
        val links = mutableListOf<RichLink>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text")
            if (text.isEmpty()) continue
            val start = builder.length
            builder.append(text)
            val command = run.optJSONObject("navigationEndpoint") ?: continue
            val target = parseRunLinkTarget(command) ?: continue
            var end = builder.length
            while (end > start && builder[end - 1].isWhitespace()) end--
            if (end > start) links.add(RichLink(start, end, target))
        }
        return RichText(builder.toString(), links)
    }

    private fun parseRunLinkTarget(command: org.json.JSONObject): RichLinkTarget? {
        command.optJSONObject("watchEndpoint")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }
            ?.let { return RichLinkTarget.Url("https://www.youtube.com/watch?v=$it") }
        command.optJSONObject("urlEndpoint")?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?.let { return RichLinkTarget.Url(unwrapYouTubeRedirect(it)) }
        command.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf { it.isNotBlank() }
            ?.let { return RichLinkTarget.Browse(it) }
        return null
    }

    /** One About-panel link, unwrapped out of YouTube's redirect. */
    private fun parseChannelExternalLink(view: org.json.JSONObject): ChannelLink? {
        val title = view.optJSONObject("title")?.optString("content")
            ?.takeIf { it.isNotBlank() }
        val link = view.optJSONObject("link")
        val display = link?.optString("content")?.takeIf { it.isNotBlank() }
        val url = link?.optJSONArray("commandRuns")?.optJSONObject(0)
            ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("urlEndpoint")?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?.let { unwrapYouTubeRedirect(it) }
            ?: return null
        return ChannelLink(
            title = title ?: display ?: url,
            url = url,
            faviconUrl = bestImageSource(
                view.optJSONObject("favicon")?.optJSONArray("sources")
            )
        )
    }

    /**
     * The sort orders a tab offers, from either of the two mechanisms YouTube
     * uses for them - see [ChannelSortOption] for why both are kept.
     */
    private fun parseChannelSortOptions(scope: org.json.JSONObject): List<ChannelSortOption> {
        val options = mutableListOf<ChannelSortOption>()

        // Videos / Shorts / Live: a chip that opens a sheet of continuations.
        val chips = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "chipViewModel", chips)
        for (chip in chips) {
            val listItems = chip.optJSONObject("tapCommand")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("showSheetCommand")
                ?.optJSONObject("panelLoadingStrategy")
                ?.optJSONObject("inlineContent")
                ?.optJSONObject("sheetViewModel")
                ?.optJSONObject("content")
                ?.optJSONObject("listViewModel")
                ?.optJSONArray("listItems") ?: continue
            for (i in 0 until listItems.length()) {
                val item = listItems.optJSONObject(i)?.optJSONObject("listItemViewModel") ?: continue
                val label = item.optJSONObject("title")?.optString("content")
                    ?.takeIf { it.isNotBlank() } ?: continue
                val command = item.optJSONObject("rendererContext")
                    ?.optJSONObject("commandContext")
                    ?.optJSONObject("onTap")
                    ?.optJSONObject("innertubeCommand") ?: continue
                val tokens = mutableListOf<String>()
                findContinuationTokens(command, tokens)
                val token = tokens.firstOrNull() ?: continue
                options.add(
                    ChannelSortOption(
                        label = label,
                        selected = item.optBoolean("isSelected", false),
                        token = token
                    )
                )
            }
        }
        if (options.isNotEmpty()) return options.distinctBy { it.label }

        // Playlists: a sub-menu of browse params, re-browsing the tab.
        val subMenus = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "sortFilterSubMenuRenderer", subMenus)
        for (menu in subMenus) {
            val items = menu.optJSONArray("subMenuItems") ?: continue
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val label = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                val params = item.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("params")?.takeIf { it.isNotBlank() } ?: continue
                options.add(
                    ChannelSortOption(
                        label = label,
                        selected = item.optBoolean("selected", false),
                        params = params
                    )
                )
            }
        }
        return options.distinctBy { it.label }
    }

    /**
     * The token for the next page, from the `continuationItemRenderer` YouTube
     * puts at the end of a grid.
     *
     * Taken from the *last* continuation in the response rather than the first:
     * a Home tab carries one per shelf, and the page-level one that actually
     * scrolls the grid is the trailing one.
     */
    private fun parseChannelNextPageToken(scope: org.json.JSONObject): String? {
        val renderers = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(scope, "continuationItemRenderer", renderers)
        return renderers.lastNotNullOfOrNull { renderer ->
            renderer.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
        }
    }

    private inline fun <T, R : Any> List<T>.lastNotNullOfOrNull(transform: (T) -> R?): R? {
        for (i in indices.reversed()) {
            transform(this[i])?.let { return it }
        }
        return null
    }

    /** Widest entry of a modern `image.sources` array. */
    private fun bestImageSource(sources: org.json.JSONArray?): String? {
        if (sources == null) return null
        var best: String? = null
        var maxWidth = -1
        for (i in 0 until sources.length()) {
            val source = sources.optJSONObject(i) ?: continue
            val width = source.optInt("width", 0)
            val url = source.optString("url").takeIf { it.isNotBlank() } ?: continue
            if (width >= maxWidth) {
                maxWidth = width
                best = url
            }
        }
        return best?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    /** Widest entry of a legacy `thumbnails` array. */
    private fun bestThumbnail(thumbnails: org.json.JSONArray?): String? {
        if (thumbnails == null) return null
        var best: String? = null
        var maxWidth = -1
        for (i in 0 until thumbnails.length()) {
            val thumb = thumbnails.optJSONObject(i) ?: continue
            val width = thumb.optInt("width", 0)
            val url = thumb.optString("url").takeIf { it.isNotBlank() } ?: continue
            if (width >= maxWidth) {
                maxWidth = width
                best = url
            }
        }
        return best?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    /**
     * Unwraps `youtube.com/redirect?...&q=<target>` to the real destination, so
     * a link does not bounce through YouTube and still works once the redirect
     * token expires. Same rule [RichText] applies to descriptions.
     */
    private fun unwrapYouTubeRedirect(url: String): String {
        if (!url.contains("/redirect?")) return url
        return try {
            android.net.Uri.parse(url).getQueryParameter("q")?.takeIf { it.isNotBlank() } ?: url
        } catch (e: Exception) {
            url
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
            val response = postWatchApi(
                "navigation/resolve_url",
                org.json.JSONObject().put("context", webContext()).put("url", url)
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
            val raw = postWatchApi(
                "browse",
                org.json.JSONObject().put("context", webContext()).put("browseId", channelId)
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
            val body = okHttpClient.newCall(request).execute().use { response ->
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
     * Parses the YouTube channel Atom feed.
     *
     * The parser is built namespace-*un*aware on purpose, so `getName()`
     * returns the literal prefixed tag ("yt:videoId", "media:thumbnail")
     * matched below. `android.util.Xml.newPullParser()` cannot be used here:
     * it turns namespace processing on, which strips the prefixes and would
     * collapse the Atom `<title>` and Media RSS `<media:title>` - two
     * different values on every entry - onto the same local name.
     */
    private fun parseChannelFeedXml(xml: String, avatarUrl: String?): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newPullParser()
        parser.setInput(java.io.StringReader(xml))

        var inEntry = false
        var inAuthor = false
        var videoId: String? = null
        var channelId: String? = null
        var title: String? = null
        var author: String? = null
        var publishedAtMs: Long? = null
        var thumbnailUrl: String? = null
        var description: String? = null
        var viewCount: Long? = null

        fun reset() {
            videoId = null; channelId = null; title = null; author = null
            publishedAtMs = null; thumbnailUrl = null; description = null; viewCount = null
        }

        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> { inEntry = true; reset() }
                    "author" -> inAuthor = true
                    "yt:videoId" -> if (inEntry) videoId = parser.nextText().trim()
                    "yt:channelId" -> if (inEntry) channelId = parser.nextText().trim()
                    // The feed-level <title> is the channel name; only the one
                    // inside an <entry> is a video title.
                    "title" -> if (inEntry && title == null) title = parser.nextText().trim()
                    "name" -> if (inEntry && inAuthor) author = parser.nextText().trim()
                    "published" -> if (inEntry) {
                        publishedAtMs = runCatching {
                            java.time.OffsetDateTime.parse(parser.nextText().trim())
                                .toInstant().toEpochMilli()
                        }.getOrNull()
                    }
                    "media:thumbnail" -> if (inEntry && thumbnailUrl == null) {
                        thumbnailUrl = parser.getAttributeValue(null, "url")
                    }
                    "media:description" -> if (inEntry) {
                        description = runCatching { parser.nextText() }.getOrNull()
                    }
                    "media:statistics" -> if (inEntry) {
                        viewCount = parser.getAttributeValue(null, "views")?.toLongOrNull()
                    }
                }

                org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        val id = videoId
                        if (!id.isNullOrBlank()) {
                            videos.add(
                                VideoItem(
                                    videoId = id,
                                    title = title.orEmpty(),
                                    channelName = author.orEmpty(),
                                    channelId = channelId,
                                    channelIconUrl = avatarUrl,
                                    thumbnailUrl = thumbnailUrl,
                                    duration = 0L,
                                    viewCount = VideoItem.formatViewCount(viewCount),
                                    uploadedDate = publishedAtMs?.let { VideoItem.formatRelativeTime(it) },
                                    description = description,
                                    publishedAtMs = publishedAtMs
                                )
                            )
                        }
                        reset()
                    }
                }
            }
            event = parser.next()
        }
        return videos
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
                .put("context", webContext())
                .put("feedbackTokens", org.json.JSONArray().put(token))
                .put("isFeedbackTokenUnencrypted", false)
                .put("shouldMerge", false)
            val raw = postWatchApi("feedback", body) ?: return@withContext false
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
            KLog.e("YouTubeRepo", "getNotifications failed", e)
            emptyList()
        }
    }
}
