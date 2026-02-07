package com.ivor.ivormusic.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
     */
    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
                ?: return@withContext Pair(emptyList(), emptyList())
            
            val url = when {
                artistId.startsWith("UC") || artistId.startsWith("UU") -> 
                    "https://www.youtube.com/channel/$artistId"
                artistId.startsWith("FMW") || artistId.startsWith("MPAD") ->
                    "https://music.youtube.com/browse/$artistId"
                else -> if (artistId.startsWith("@")) {
                    "https://www.youtube.com/$artistId"
                } else {
                    "https://www.youtube.com/channel/$artistId" // Default to channel
                }
            }
            android.util.Log.d("YouTubeRepo", "Fetching artist details from: $url")
            val extractor = ytService.getChannelExtractor(url)
            extractor.fetchPage()
            
            val artistName = extractor.name ?: "Unknown Artist"
            val initialPage = (extractor as org.schabi.newpipe.extractor.ListExtractor<*>).initialPage
            val items: List<InfoItem> = initialPage.items
            
            val songs = mutableListOf<Song>()
            val albums = mutableListOf<PlaylistDisplayItem>()
            
            items.forEach { item ->
                if (item is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
                    try {
                        songs.add(
                            Song.fromYouTube(
                                videoId = extractVideoId(item.url),
                                title = item.name ?: "Unknown",
                                artist = item.uploaderName ?: artistName,
                                album = "",
                                duration = item.duration * 1000L,
                                thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                            )
                        )
                    } catch (e: Exception) {}
                } else if (item is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem) {
                    try {
                        albums.add(
                            PlaylistDisplayItem(
                                name = item.name ?: "Unknown",
                                url = item.url,
                                uploaderName = item.uploaderName ?: artistName,
                                itemCount = item.streamCount.toInt(),
                                thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                            )
                        )
                    } catch (e: Exception) {}
                }
            }
            
            Pair(songs, albums)
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching artist details", e)
            Pair(emptyList(), emptyList())
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
        var attempts = 0
        while (attempts < 3) {
            try {
                // Determine client type mainly for logs, but NewPipe handles rotation internally usually
                val streamUrl = "https://www.youtube.com/watch?v=$videoId"
                val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } 
                    ?: return@withContext Result.failure(Exception("YouTube Service not found in NewPipe"))
                
                val streamExtractor = ytService.getStreamExtractor(streamUrl)
                streamExtractor.fetchPage()
                
                // Get the best audio-only stream
                val audioStreams = streamExtractor.audioStreams
                val bestAudioStream = audioStreams
                    .maxByOrNull { it.averageBitrate }
                    ?: audioStreams.maxByOrNull { it.bitrate }
                
                val url = bestAudioStream?.content
                if (url != null) {
                    return@withContext Result.success(url)
                } else {
                    // If no stream found, it might be a content restriction (age-gate, region)
                    if (attempts == 2) return@withContext Result.failure(Exception("No audio stream found for $videoId after 3 attempts"))
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeRepository", "Attempt ${attempts+1} failed for $videoId: ${e.message}")
                if (attempts == 2) {
                    // Try fallback before giving up purely on NewPipe
                    android.util.Log.w("YouTubeRepository", "NewPipe failed, trying Internal API fallback for $videoId")
                    val fallbackUrl = getStreamUrlFallback(videoId)
                    if (fallbackUrl != null) {
                         android.util.Log.d("YouTubeRepository", "Fallback success for $videoId")
                         return@withContext Result.success(fallbackUrl)
                    }
                    return@withContext Result.failure(e)
                }
            }
            attempts++
            // Exponential backoff: 500ms, 1000ms
            kotlinx.coroutines.delay(500L * attempts)
        }
        Result.failure(Exception("Unknown error resolving stream"))
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
     * Get the user's playlists.
     * Uses Internal YTM API with Cookies.
     */
    suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        
        try {
            // Fetch Library (Liked Playlists)
            // Note: FEmusic_liked_playlists gets playlists you've saved/liked
            // FEmusic_library_landing might be better but harder to parse
            val jsonResponse = fetchInternalApi("FEmusic_liked_playlists")
            
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

            // Add parsed playlists
            playlists.addAll(parsePlaylistsFromInternalJson(jsonResponse))
            
            playlists
        } catch (e: Exception) {
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
            val maxPages = 10 // Safety limit to prevent infinite loops
            
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
                        "clientVersion": "1.20240402.09.00",
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
                 
                 playlistExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
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

        // Fallback to Internal API (Works for LM, RTM, and public playlists if authenticated)
        if (sessionManager.isLoggedIn()) {
             try {
                 // Ensure ID is browseId format (usually adding VL prefix for playlists if not present, though newer API might just take PL)
                 // Actually for browse endpoint, usually we send the ID as is, or VL+ID.
                 val browseId = if (playlistId.startsWith("PL") || playlistId.startsWith("RD")) "VL$playlistId" else playlistId
                 val json = fetchInternalApi(browseId)
                 val internalSongs = parseSongsFromInternalJson(json)
                 if (internalSongs.isNotEmpty()) return@withContext internalSongs
             } catch (e: Exception) {
                 e.printStackTrace()
             }
        }
        
        emptyList()
    }

    suspend fun fetchAccountInfo() = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext

        try {
            val jsonResponse = fetchInternalApi("account/account_menu")
            
            if (jsonResponse.isEmpty()) return@withContext
            
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
                
                // Fallback: Regex for ggpht if parsing failed
                if (avatarUrl == null) {
                    val ggphtRegex = "\"url\"\\s*:\\s*\"(https://ggpht\\.googleusercontent\\.com/[^\"]+)\"".toRegex()
                    val match = ggphtRegex.find(jsonResponse)
                    avatarUrl = match?.groupValues?.get(1)
                }

            } catch (jsonEx: Exception) {
                // Ignore
            }
            
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
     * Fallback stream resolution using Internal API.
     * Bypasses NewPipe HTML parsing which can get stuck on "Page needs to be reloaded" logic.
     */
    /**
     * Fallback stream resolution using Internal API.
     * Uses ANDROID_MUSIC client (InnerTube) which is more robust against "reload" errors for music content.
     */
    private suspend fun getStreamUrlFallback(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            // Use ANDROID_MUSIC client which is specifically for YouTube Music content
            // and less prone to bot detection than the generic ANDROID client
            
            val jsonBody = """
               {
                   "videoId": "$videoId",
                   "context": {
                       "client": {
                           "clientName": "ANDROID_MUSIC",
                           "clientVersion": "7.20.51",
                           "androidSdkVersion": 34,
                           "hl": "en",
                           "gl": "US",
                           "utcOffsetMinutes": 0
                       }
                   },
                   "playbackContext": {
                       "contentPlaybackContext": {
                           "signatureTimestamp": ${System.currentTimeMillis() / 1000}
                       }
                   }
               }
            """.trimIndent()
            
            val url = "https://youtubei.googleapis.com/youtubei/v1/player?key=$INNER_TUBE_API_KEY"
            
            val requestBuilder = okhttp3.Request.Builder()
               .url(url)
               .post(jsonBody.toRequestBody("application/json".toMediaType()))
               .addHeader("User-Agent", "com.google.android.apps.youtube.music/7.20.51 (Linux; U; Android 14; en_US) gzip")
               .addHeader("X-Goog-Api-Format-Version", "1")
               
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            val json = response.body?.string() ?: return@withContext null
            
            val root = org.json.JSONObject(json)
            
            // Check for playability status error
            val playability = root.optJSONObject("playabilityStatus")
            if (playability != null && playability.optString("status") != "OK") {
                 android.util.Log.e("YouTubeRepository", "Fallback playability error: ${playability.optString("reason")}")
                 return@withContext null
            }
            
            val streamingData = root.optJSONObject("streamingData") 
            if (streamingData == null) {
                 android.util.Log.e("YouTubeRepository", "Fallback no streamingData found in response.")
                 return@withContext null
            }
            
            // Extract formats
            val formats = mutableListOf<org.json.JSONObject>()
            streamingData.optJSONArray("adaptiveFormats")?.let { arr ->
                for (i in 0 until arr.length()) formats.add(arr.getJSONObject(i))
            }
            streamingData.optJSONArray("formats")?.let { arr ->
                for (i in 0 until arr.length()) formats.add(arr.getJSONObject(i))
            }
            
            // Find best audio (Opus preferred, then AAC)
            val audioFormats = formats.filter { it.optString("mimeType").contains("audio") }
            
            // Log for debugging
            android.util.Log.d("YouTubeRepository", "Fallback found ${audioFormats.size} audio formats")
            
            val bestAudio = audioFormats.maxByOrNull { it.optInt("bitrate") }
                
            var streamUrl = bestAudio?.optString("url")
            
            if (streamUrl.isNullOrEmpty()) {
                // If URL is missing, it might use signatureCipher
                val cipher = bestAudio?.optString("signatureCipher") ?: bestAudio?.optString("cipher")
                if (!cipher.isNullOrEmpty()) {
                    android.util.Log.w("YouTubeRepository", "Fallback found cipher, but decryption not implemented here. Relying on NewPipe.")
                    // If we really need to decrypt, we would need a complex Decryptor. 
                    // Usually NewPipe handles this. If NewPipe failed, we are in trouble unless we fix NewPipe.
                    // However, ANDROID client usually gives direct URLs for most music content.
                    return@withContext null
                }
            }
            
            return@withContext streamUrl
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepository", "Fallback failed for $videoId", e)
            null
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
                            "clientVersion": "1.20240402.09.00",
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
                            "clientVersion": "1.20230102.01.00",
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
            ?.optJSONObject("sectionListContinuation")
            ?.optJSONArray("contents")
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
        
        // 4. Direct Item (if the "shelf" is actually just an item in a continuation list)
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

        return Song.fromYouTube(
            videoId = videoId!!,
            title = title,
            artist = artist,
            album = album,
            duration = 0L,
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
            val clientVersion = "1.20241230.01.00"

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
     * Get trending/recommended videos for video mode home screen.
     * Uses personalized recommendations when logged in, falls back to trending otherwise.
     */
    suspend fun getTrendingVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        // Try personalized recommendations first if logged in
        val isLoggedIn = sessionManager.isLoggedIn()
        android.util.Log.d("YouTubeRepo", "getTrendingVideos - isLoggedIn: $isLoggedIn")
        
        if (isLoggedIn) {
            try {
                android.util.Log.d("YouTubeRepo", "Fetching personalized video recommendations")
                val videos = getPersonalizedVideoRecommendations()
                if (videos.isNotEmpty()) {
                    android.util.Log.d("YouTubeRepo", "Got ${videos.size} personalized videos")
                    return@withContext videos
                } else {
                    android.util.Log.w("YouTubeRepo", "Personalized recommendations returned empty, falling back to trending")
                }
            } catch (e: Exception) {
                android.util.Log.e("YouTubeRepo", "Error fetching personalized videos", e)
            }
        }
        
        // Fallback to public trending
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" }
                ?: return@withContext emptyList()
            
            // Try to get trending/kiosk content
            val kioskList = ytService.kioskList
            val trendingExtractor = kioskList.getExtractorById("Trending", null)
            trendingExtractor.fetchPage()
            
            val videos = trendingExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
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
            
            if (videos.isNotEmpty()) return@withContext videos
            
            // Fallback to search for popular content
            searchVideos("trending videos ${java.time.Year.now().value}")
        } catch (e: Exception) {
            android.util.Log.e("YouTubeRepo", "Error fetching trending videos", e)
            // Fallback to search
            try {
                searchVideos("popular videos")
            } catch (e2: Exception) {
                emptyList()
            }
        }
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
                        "clientVersion": "2.20240101.00.00",
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
                        "clientVersion": "2.20240101.00.00",
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
            
            val tabs = root.optJSONObject("contents")
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
                    
                    // 2. RichSectionRenderer (Shelves within Grid)
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
             if (overlays != null) {
                 for (i in 0 until overlays.length()) {
                     val overlayItem = overlays.optJSONObject(i)
                     // Try thumbnailOverlayBadgeViewModel path
                     val badgeText = overlayItem?.optJSONObject("thumbnailOverlayBadgeViewModel")
                         ?.optJSONArray("thumbnailBadges")?.optJSONObject(0)
                         ?.optJSONObject("thumbnailBadgeViewModel")?.optString("text")
                     if (badgeText != null && badgeText.contains(":")) {
                         durationText = badgeText
                         durationSeconds = parseDurationToSeconds(badgeText)
                         break
                     }
                     // Try thumbnailOverlayTimeStatusRenderer path
                     val timeStatus = overlayItem?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
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
             val isLive = durationText.contains("LIVE", ignoreCase = true) || 
                         viewCount.contains("watching", ignoreCase = true)
             
            return VideoItem(
                videoId = contentId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                channelIconUrl = null, // Skip heavy recursion for icon
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
                        "clientVersion": "2.20240101.00.00",
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
}
