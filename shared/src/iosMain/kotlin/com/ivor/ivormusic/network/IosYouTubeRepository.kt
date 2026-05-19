package com.ivor.ivormusic.network

import com.ivor.ivormusic.domain.ArtistItem
import com.ivor.ivormusic.domain.PlaylistDisplayItem
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.VideoDetails
import com.ivor.ivormusic.domain.VideoItem
import com.ivor.ivormusic.domain.VideoQuality

class IosYouTubeRepository : YouTubeRepository {
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
    override suspend fun getVideoStreamUrl(videoId: String): String? = null
    override suspend fun getVideoStreamQualities(videoId: String): List<VideoQuality> = emptyList()
    override suspend fun getVideoDetails(videoId: String): VideoDetails = VideoDetails(emptyList(), emptyList())
    override suspend fun fetchAccountInfo() {}
    override suspend fun reportPlayback(videoId: String) {}
}
