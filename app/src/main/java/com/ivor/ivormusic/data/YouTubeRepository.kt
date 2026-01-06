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
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
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
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

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
     * Get the best audio stream URL for a video.
     * Note: These URLs expire, so call this right before playback.
     * @param videoId The YouTube video ID
     * @return The stream URL or null if not found
     */
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val streamUrl = "https://www.youtube.com/watch?v=$videoId"
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext null
            val streamExtractor = ytService.getStreamExtractor(streamUrl)
            streamExtractor.fetchPage()
            
            // Get the best audio-only stream
            val audioStreams = streamExtractor.audioStreams
            val bestAudioStream = audioStreams
                .maxByOrNull { it.averageBitrate }
                ?: audioStreams.maxByOrNull { it.bitrate }
            
            bestAudioStream?.content
        } catch (e: Exception) {
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
     * Get liked music.
     */
    suspend fun getLikedMusic(): List<Song> = withContext(Dispatchers.IO) {
        if (sessionManager.isLoggedIn()) {
            try {
                val json = fetchInternalApi("FEmusic_liked_videos")
                val songs = parseSongsFromInternalJson(json)
                if (songs.isNotEmpty()) return@withContext songs
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        getPlaylist("LM") 
    }
    
    suspend fun getPlaylist(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        // Fix for "Your Likes" playlist not loading: redirect to the working getLikedMusic() method
        if (playlistId == "LM" || playlistId == "VLLM") {
            return@withContext getLikedMusic()
        }

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
            
            // Basic parsing for avatar
            // Pattern: "thumbnail":{"thumbnails":[{"url":"..."
            val thumbRegex = """"thumbnails":\[\{"url":"([^"]+)"""".toRegex()
            val match = thumbRegex.find(jsonResponse)
            
            match?.groupValues?.get(1)?.let { url ->
                // Ensure high res
                val avatarUrl = url.replace("s88", "s1080").replace("s48", "s1080")
                sessionManager.saveUserAvatar(avatarUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                            "clientVersion": "1.20230102.01.00",
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
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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
            val items = mutableListOf<org.json.JSONObject>()
            // Recursively find all "musicResponsiveListItemRenderer" (Lists)
            findAllObjects(root, "musicResponsiveListItemRenderer", items)
            // Also find "musicTwoRowItemRenderer" (Shelves/Cards)
            findAllObjects(root, "musicTwoRowItemRenderer", items)

            items.forEach { item ->
                try {
                    // Strategy 1: musicResponsiveListItemRenderer (Flex Columns)
                    if (item.has("flexColumns")) {
                        // Extract Video ID
                        val videoId = extractValueFromRuns(item, "videoId") ?: return@forEach
                        
                        // Extract Title
                        val flexColumns = item.optJSONArray("flexColumns")
                        val titleFormatted = flexColumns?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                        val title = getRunText(titleFormatted) ?: "Unknown Title"

                        // Extract Artist and Album
                        val subtitleFormatted = flexColumns?.optJSONObject(1)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                        
                        val subtitleRuns = subtitleFormatted?.optJSONArray("runs")
                        var artist = "Unknown Artist"
                        var album = "Unknown Album"
                        
                        if (subtitleRuns != null && subtitleRuns.length() > 0) {
                            artist = subtitleRuns.optJSONObject(0)?.optString("text") ?: artist
                            if (subtitleRuns.length() > 2) {
                                album = subtitleRuns.optJSONObject(2)?.optString("text") ?: album
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

                        songs.add(Song.fromYouTube(
                            videoId = videoId,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = 0L,
                            thumbnailUrl = thumbnailUrl
                        )!!)
                    }
                    // Strategy 2: musicTwoRowItemRenderer (Title/Subtitle)
                    else if (item.has("title")) { // Basic check for TwoRow
                        // Check if it's a song/video (has videoId in navigation)
                        val videoId = extractValueFromRuns(item, "videoId")
                        if (videoId != null) {
                            val title = getRunText(item.optJSONObject("title")) ?: "Unknown"
                            val subtitleFormatted = item.optJSONObject("subtitle")
                            val subtitleRuns = subtitleFormatted?.optJSONArray("runs")
                             
                            var artist = "Unknown Artist"
                            var album = "Unknown"
                            
                            if (subtitleRuns != null && subtitleRuns.length() > 0) {
                                artist = subtitleRuns.optJSONObject(0)?.optString("text") ?: artist
                            }
                            
                             val thumbnails = item.optJSONObject("thumbnailRenderer")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            
                            val thumbnailUrl = thumbnails?.let {
                                it.optJSONObject(it.length() - 1)?.optString("url")
                            }

                            songs.add(Song.fromYouTube(
                                videoId = videoId,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = 0L,
                                thumbnailUrl = thumbnailUrl
                            )!!)
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed item
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs.distinctBy { it.id }
    }
    
    private fun parsePlaylistsFromInternalJson(json: String): List<PlaylistDisplayItem> {
        val playlists = mutableListOf<PlaylistDisplayItem>()
        try {
            val root = org.json.JSONObject(json)
            val items = mutableListOf<org.json.JSONObject>()
            // Playlists are usually in "musicTwoRowItemRenderer" or "gridPlaylistRenderer"
            // For "likedsongs" endpoint, it might be different, but library usually uses TwoRow
            findAllObjects(root, "musicTwoRowItemRenderer", items)
            
            items.forEach { item ->
                 try {
                     // Extract ID
                     val navigationEndpoint = item.optJSONObject("navigationEndpoint")
                     val browseId = navigationEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                     
                     // Ensure it's a playlist
                     if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL"))) {
                         val cleanId = browseId.removePrefix("VL")
                         
                         // Extract Title
                         val title = getRunText(item.optJSONObject("title")) ?: "Unknown Playlist"
                         
                         // Extract Subtitle (Uploader / Count)
                         val subtitle = getRunText(item.optJSONObject("subtitle")) ?: "Unknown"
                         
                         // Extract Thumbnail
                         val thumbnails = item.optJSONObject("thumbnailRenderer")
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
                             thumbnailUrl = thumbnailUrl
                         ))
                     }
                 } catch (e: Exception) {
                     // Skip
                 }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return playlists 
    }

    // --- JSON Helpers ---

    private fun findAllObjects(node: Any, key: String, results: MutableList<org.json.JSONObject>) {
        if (node is org.json.JSONObject) {
            if (node.has(key)) {
                results.add(node.getJSONObject(key))
            }
            // Recurse keys
            val keys = node.keys()
            while (keys.hasNext()) {
                val nextKey = keys.next()
                findAllObjects(node.get(nextKey), key, results)
            }
        } else if (node is org.json.JSONArray) {
            for (i in 0 until node.length()) {
                findAllObjects(node.get(i), key, results)
            }
        }
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
        // Recursive search for a specific key value pair in a small subtree is expensive 
        // but for videoId it usually lives in navigationEndpoint -> watchEndpoint -> videoId
        // Let's try to find navigationEndpoint recursively in the item
        val endpoints = mutableListOf<org.json.JSONObject>()
        findAllObjects(item, "watchEndpoint", endpoints)
        return endpoints.firstOrNull()?.optString("videoId")
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
}
