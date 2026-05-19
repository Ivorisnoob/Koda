package com.ivor.ivormusic.network

import com.ivor.ivormusic.domain.ArtistItem
import com.ivor.ivormusic.domain.PlaylistDisplayItem
import com.ivor.ivormusic.domain.Song
import com.ivor.ivormusic.domain.VideoDetails
import com.ivor.ivormusic.domain.VideoItem
import com.ivor.ivormusic.domain.VideoQuality

interface YouTubeRepository {

    companion object {
        const val FILTER_SONGS = "music_songs"
        const val FILTER_VIDEOS = "music_videos"
        const val FILTER_ALBUMS = "music_albums"
        const val FILTER_PLAYLISTS = "music_playlists"
        const val FILTER_ARTISTS = "music_artists"
        const val FILTER_YOUTUBE_VIDEOS = "videos"
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    suspend fun search(query: String, filter: String = FILTER_SONGS): List<Song>
    suspend fun searchNext(query: String): List<Song>
    suspend fun searchPlaylists(query: String): List<PlaylistDisplayItem>
    suspend fun searchAlbums(query: String): List<PlaylistDisplayItem>
    suspend fun searchArtists(query: String): List<ArtistItem>
    suspend fun searchVideos(query: String): List<VideoItem>
    suspend fun getArtistDetails(artistId: String): Pair<List<Song>, List<PlaylistDisplayItem>>

    suspend fun getRecommendations(): List<Song>
    suspend fun getLikedMusic(): List<Song>
    suspend fun getUserPlaylists(): List<PlaylistDisplayItem>
    suspend fun getPlaylist(playlistId: String): List<Song>
    suspend fun getTrendingVideos(): List<VideoItem>
    suspend fun getWatchHistory(): List<VideoItem>

    suspend fun getVideoStreamUrl(videoId: String): String?
    suspend fun getVideoStreamQualities(videoId: String): List<VideoQuality>
    suspend fun getVideoDetails(videoId: String): VideoDetails

    suspend fun fetchAccountInfo()
    suspend fun reportPlayback(videoId: String)
}
