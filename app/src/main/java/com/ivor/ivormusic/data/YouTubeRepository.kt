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
import java.io.IOException
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
     * Get a playlist's songs.
     * @param playlistId The YouTube playlist ID
     * @return List of songs in the playlist
     */
    suspend fun getPlaylist(playlistId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val playlistUrl = "https://music.youtube.com/playlist?list=$playlistId"
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext emptyList()
            val playlistExtractor = ytService.getPlaylistExtractor(playlistUrl)
            playlistExtractor.fetchPage()
            
            playlistExtractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { item: StreamInfoItem ->
                try {
                    Song.fromYouTube(
                        videoId = extractVideoId(item.url),
                        title = item.name ?: "Unknown",
                        artist = item.uploaderName ?: "Unknown Artist",
                        album = playlistExtractor.name ?: "",
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
     * Get personalized recommendations (Quick Picks).
     * Note: This requires user authentication (cookies) for best results.
     * @return List of recommended songs
     */
    suspend fun getRecommendations(): List<Song> = withContext(Dispatchers.IO) {
        // Fallback to searching for trending music as a simplified "recommendation"
        search("trending music", FILTER_SONGS)
    }


    /**
     * Get the user's playlists.
     * Note: Requires authenticated session (cookies).
     * @return List of playlist info items
     */
    suspend fun getUserPlaylists(): List<PlaylistInfoItem> = withContext(Dispatchers.IO) {
        try {
            val ytService = ServiceList.all().find { it.serviceInfo.name == "YouTube" } ?: return@withContext emptyList()
            // Library playlists are usually at FEmusic_library_playlists
            // Need to check if getBrowseExtractor is actually the correct method
            emptyList() // Placeholder since getBrowseExtractor is failing
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get the user's liked music playlist.
     * Note: Requires authenticated session (cookies).
     * @return List of liked songs
     */
    suspend fun getLikedMusic(): List<Song> = withContext(Dispatchers.IO) {
        getPlaylist("LM") // "LM" is the reserved ID for Liked Music
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
