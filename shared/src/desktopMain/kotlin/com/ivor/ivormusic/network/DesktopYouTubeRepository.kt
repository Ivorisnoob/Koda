package com.ivor.ivormusic.network

import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.domain.ArtistItem
import com.ivor.ivormusic.domain.PlaylistDisplayItem
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.VideoDetails
import com.ivor.ivormusic.domain.VideoItem
import com.ivor.ivormusic.domain.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor

class DesktopYouTubeRepository(
    private val sessionManager: SessionManager
) : YouTubeRepository {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", YouTubeRepository.BROWSER_USER_AGENT)
                    .build()
            )
        }
        .build()

    init {
        try { NewPipe.init(DesktopDownloaderImpl(client)) } catch (_: Exception) {}
    }

    override suspend fun search(query: String, filter: String): List<Song> = emptyList()
    override suspend fun searchNext(query: String): List<Song> = emptyList()
    override suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem> = emptyList()
    override suspend fun searchAlbums(query: String): List<PlaylistDisplayItem> = emptyList()
    override suspend fun searchArtists(query: String): List<ArtistItem> = emptyList()
    override suspend fun searchVideos(query: String): List<VideoItem> = emptyList()
    override suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>> = Pair(emptyList(), emptyList())
    override suspend fun getRecommendations(): List<Song> = emptyList()
    override suspend fun getLikedMusic(): List<Song> = emptyList()
    override suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = emptyList()
    override suspend fun getPlaylist(playlistId: String): List<Song> = emptyList()
    override suspend fun getTrendingVideos(): List<VideoItem> = emptyList()
    override suspend fun getWatchHistory(): List<VideoItem> = emptyList()
    override suspend fun getVideoStreamUrl(videoId: String): String? = getStreamUrl(videoId)
    override suspend fun getVideoStreamQualities(videoId: String): List<VideoQuality> = emptyList()
    override suspend fun getVideoDetails(videoId: String): VideoDetails = VideoDetails(emptyList(), emptyList())
    override suspend fun fetchAccountInfo() {}
    override suspend fun reportPlayback(videoId: String) {}

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor: StreamExtractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()
            extractor.audioStreams.maxByOrNull { it.averageBitrate }?.url
        } catch (_: Exception) { null }
    }
}
