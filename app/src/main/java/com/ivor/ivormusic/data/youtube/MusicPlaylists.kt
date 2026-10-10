package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.MusicMetadata
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.UNKNOWN_ALBUM
import com.ivor.ivormusic.data.items
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * The songs of a YouTube Music playlist or album, and the account's own
 * library of playlists and likes.
 *
 * One walker, [getBrowsePlaylistSongs], reads a playlist through WEB_REMIX
 * browse and its continuation chain for both session states. A page that
 * fails marks the load incomplete rather than ending it, so the NewPipe
 * fallback can still run; an HTTP error is never a playlist's end.
 */
internal class MusicPlaylists(
    private val musicApi: MusicApi,
    private val sessionManager: SessionManager,
) {
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
     * Fetch an album's tracks by its browse id (MPREb…).
     * Album pages aren't playlists, so they must go through /browse.
     */
    suspend fun getAlbumSongs(browseId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val body = musicApi.browseMusic(browseId) ?: return@withContext emptyList()
            val root = JSONObject(body)
            val songs = MusicMetadata.albumSongs(root, browseId)
            // Signed out, the page lists each track's video where it has one.
            // The album's audio playlist has the songs; one more request, and
            // only for an album that needs it.
            if (songs.isEmpty() || !MusicMetadata.albumHasVideoVersions(root)) return@withContext songs
            val audioPlaylist = MusicMetadata.albumAudioPlaylistId(root) ?: return@withContext songs
            val audio = getBrowsePlaylistSongs(audioPlaylist)
            if (audio.songs.isEmpty()) songs else MusicMetadata.withSongVersions(songs, audio.songs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching album $browseId", e)
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
                    musicApi.fetchInternalApi("FEmusic_liked_videos")
                } else {
                    musicApi.fetchContinuation(continuationToken)
                }
                
                if (json.isEmpty()) break
                
                val songs = parseSongsFromInternalJson(json)
                allSongs.addAll(songs)
                
                // Extract continuation token for next page
                continuationToken = extractContinuationToken(json)
                pageCount++
                
                KLog.d(YOUTUBE_TAG, "Liked songs page $pageCount: ${songs.size} songs, total: ${allSongs.size}")
                
            } while (continuationToken != null && pageCount < maxPages)
            
            if (allSongs.isNotEmpty()) {
                return@withContext allSongs.distinctBy { it.id }
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching liked music", e)
        }
        
        // Fallback to NewPipe method
        getPlaylistInternal("LM")
    }

    /**
     * Get the user's playlists.
     * Uses Internal YTM API with Cookies.
     */
    suspend fun getUserPlaylists(): List<PlaylistDisplayItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        
        try {
            val playlists = mutableListOf<PlaylistDisplayItem>()

            // Likes has a real account browse id; mixes must come from the API.
            playlists.add(PlaylistDisplayItem(
                name = "Your Likes",
                url = "https://music.youtube.com/playlist?list=LM",
                uploaderName = "You"
            ))

            // Fetch Library (Liked Playlists), following grid continuations so
            // large libraries come back in full rather than just the first page.
            // Note: FEmusic_liked_playlists gets playlists you've saved/liked
            var json = musicApi.fetchInternalApi("FEmusic_liked_playlists")
            var pageCount = 0
            val maxPages = 20
            while (json.isNotEmpty() && pageCount < maxPages) {
                val parsed = parsePlaylistsFromInternalJson(json)
                playlists.addAll(parsed)
                pageCount++
                KLog.d(YOUTUBE_TAG, "Library playlists page $pageCount: ${parsed.size} items")
                if (parsed.isEmpty()) break
                val token = extractContinuationToken(json) ?: break
                json = musicApi.fetchContinuation(token)
            }

            // The library grid can include "Your Likes" (VLLM) which we already synthesized
            playlists.distinctBy { it.id }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Error fetching user playlists", e)
            emptyList()
        }
    }

    /**
     * Internal playlist fetching without the LM redirect to avoid infinite recursion.
     */
    suspend fun getPlaylistInternal(playlistId: String): List<Song> = withContext(Dispatchers.IO) {

        // WEB_REMIX first for both session states: NewPipe drops album links
        // and cannot distinguish a playlist's title from a track's album.
        val accountResult = getBrowsePlaylistSongs(playlistId)
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
                        YOUTUBE_TAG,
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
                    album = UNKNOWN_ALBUM,
                    duration = item.duration * 1000L,
                    thumbnailUrl = item.thumbnails?.firstOrNull()?.url
                )
            }
        } catch (e: Exception) {
            newPipeComplete = false
            emptyList()
        }

        if (newPipeComplete && newPipeSongs.isNotEmpty()) return@withContext newPipeSongs

        // Do not throw away useful rows if every complete path failed, but log
        // loudly that this is degraded rather than pretending the exact page
        // boundary is the playlist's real end.
        val partial = listOf(accountResult.songs, newPipeSongs)
            .maxByOrNull { it.size }
            .orEmpty()
        if (partial.isNotEmpty()) {
            KLog.w(
                YOUTUBE_TAG,
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
     * Account cookies are attached by [MusicApi.browseMusic] and [MusicApi.fetchContinuation]
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
        var json = musicApi.browseMusic(browseId)
            ?: return PlaylistLoadResult(emptyList(), complete = false)

        while (true) {
            if (json.isBlank()) return PlaylistLoadResult(allSongs, complete = false)
            allSongs += parseSongsFromInternalJson(json, preserveDuplicates = true)
            val token = extractPlaylistContinuationToken(json)
                ?: return PlaylistLoadResult(allSongs, complete = true)
            if (!seenTokens.add(token)) {
                KLog.w(YOUTUBE_TAG, "Repeated playlist continuation for $playlistId")
                return PlaylistLoadResult(allSongs, complete = false)
            }
            json = musicApi.fetchContinuation(token)
            if (json.isEmpty()) {
                KLog.w(
                    YOUTUBE_TAG,
                    "Authenticated playlist continuation failed for $playlistId after ${allSongs.size} songs"
                )
                return PlaylistLoadResult(allSongs, complete = false)
            }
        }
    }
}
