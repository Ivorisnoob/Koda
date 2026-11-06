package com.ivor.ivormusic.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongRepository
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.LinkedHashMap

/**
 * The media browse tree Android Auto and other media browsers walk: root tabs,
 * the Library's categories, playlists and search.
 *
 * Every store is read through a service-owned, bounded cache. Blocking
 * repository work outlives a timed-out waiter, so another Auto request joins
 * it instead of duplicating it.
 *
 * A profile switch bumps [generation]; any request started under the old one
 * is answered with an error rather than the old profile's library.
 *
 * Main-thread owned, apart from the caches' own loaders on [ioScope].
 *
 * @param songCache and [localSongCache] stand in for the default caches in
 *   tests, which fill them directly instead of loading from repositories.
 */
@UnstableApi
internal class MusicBrowseTree(
    private val context: Context,
    private val mainScope: CoroutineScope,
    ioScope: CoroutineScope,
    private val youtube: () -> YouTubeRepository,
    private val downloadedSongs: () -> List<Song>,
    private val likedSongs: () -> List<Song>,
    private val statsRepository: () -> StatsRepository,
    private val songRepository: () -> SongRepository,
    private val preferences: () -> ThemePreferences,
    private val notifyChildrenChanged: (parentId: String, itemCount: Int) -> Unit,
    songCache: MediaBrowseCache<String, List<Song>>? = null,
    localSongCache: MediaBrowseCache<String, List<Song>>? = null,
) {
    private val browseSongs: MediaBrowseCache<String, List<Song>> = songCache ?: MediaBrowseCache(ioScope,
        ttlForValue = { if (it.isEmpty()) 15_000L else 5 * 60 * 1000L },
        onUpdated = { id, songs ->
            if (!id.startsWith("SEARCH:")) mainScope.launch {
                if (browseSongs.peek(id) === songs) notifyChildrenChanged(id, songs.size)
            }
        })
    private val browsePlaylists: MediaBrowseCache<String, List<PlaylistDisplayItem>> = MediaBrowseCache(ioScope,
        ttlForValue = { if (it.isEmpty()) 15_000L else 5 * 60 * 1000L },
        onUpdated = { id, playlists -> mainScope.launch {
            if (browsePlaylists.peek(id) === playlists) notifyChildrenChanged(id, playlists.size)
        } })
    // A stalled online load must not occupy the slots needed to read local music.
    private val localBrowseSongs: MediaBrowseCache<String, List<Song>> = localSongCache ?: MediaBrowseCache(ioScope,
        ttlForValue = { if (it.isEmpty()) 15_000L else 5 * 60 * 1000L },
        onUpdated = { id, songs -> mainScope.launch {
            if (localBrowseSongs.peek(id) === songs) notifyChildrenChanged(id, songs.size)
        } })

    private data class SearchKey(val browser: MediaSession.ControllerInfo, val query: String)

    // Retain terminal errors too so the legacy completion notification does
    // not trigger another network attempt on failure.
    private val searchResults = object : LinkedHashMap<SearchKey, LibraryResult<ImmutableList<MediaItem>>>(32, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<SearchKey, LibraryResult<ImmutableList<MediaItem>>>,
        ): Boolean = size > 32
    }

    /** Bumped on every profile switch; a request that sees it change gives up. */
    var generation = 0L
        private set

    /** Forget everything the old profile's library put in the tree. */
    fun invalidate() {
        generation++
        browseSongs.clear()
        localBrowseSongs.clear()
        browsePlaylists.clear()
        searchResults.clear()
    }

    fun onDisconnected(controller: MediaSession.ControllerInfo) {
        searchResults.keys.removeAll { it.browser == controller }
    }

    // --- Callback answers ---

    fun libraryRoot(): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(
            LibraryResult.ofItem(
                buildRootItem(),
                MediaLibraryService.LibraryParams.Builder().setExtras(contentStyleExtras(GRID_ITEM, LIST_ITEM)).build(),
            )
        )

    fun item(mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        // Clients use this to restore state or verify a single item after
        // reconnecting. The default answer is an error, which some
        // browsers treat as "this app's tree is gone" - so resolve real
        // answers from the same stores the browse tree serves.
        if (mediaId == "root") {
            return Futures.immediateFuture(LibraryResult.ofItem(buildRootItem(), null))
        }
        return browseResult {
            (rootItems() + libraryItems()).firstOrNull { it.mediaId == mediaId }?.let {
                return@browseResult LibraryResult.ofItem(it, null)
            }
            if (mediaId.startsWith("PLAYLIST_")) {
                val playlistId = mediaId.removePrefix("PLAYLIST_")
                val playlist = playlistsForBrowse()
                    .firstOrNull { it.url.substringAfter("list=") == playlistId }
                if (playlist != null) {
                    return@browseResult LibraryResult.ofItem(playlistEntry(playlist), null)
                }
            }
            findSong(mediaId)?.let { song ->
                return@browseResult LibraryResult.ofItem(toBrowseItem(song), null)
            }
            LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
        }
    }

    fun children(
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        if (page < 0 || pageSize <= 0) {
            return Futures.immediateFuture(LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE))
        }
        if (parentId == "root") {
            return Futures.immediateFuture(LibraryResult.ofItemList(mediaBrowsePage(rootItems(), page, pageSize), params))
        }
        return browseResult {
            val items = childrenOf(parentId)
            LibraryResult.ofItemList(mediaBrowsePage(items, page, pageSize), params)
        }
    }

    fun search(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = mainScope.future {
        val started = generation
        val result = loadSearch(query, params)
        if (started != generation) {
            // Complete the legacy request, but never expose the old profile.
            session.notifySearchResultChanged(browser, query, 0, params)
            return@future LibraryResult.ofError(SessionResult.RESULT_ERROR_INVALID_STATE)
        }
        searchResults[SearchKey(browser, query.trim())] = result
        // Required even for zero results/errors. Media3's legacy bridge
        // ignores the returned future and waits for this notification.
        session.notifySearchResultChanged(browser, query, result.value?.size ?: 0, params)
        if (result.resultCode == LibraryResult.RESULT_SUCCESS) LibraryResult.ofVoid()
        else LibraryResult.ofError(result.resultCode)
    }

    fun searchResult(
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = browseResult {
        if (page < 0 || pageSize <= 0) {
            return@browseResult LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
        }
        val result = searchResults[SearchKey(browser, query.trim())]
            ?: loadSearch(query, params)
        val items = result.value
        if (result.resultCode != LibraryResult.RESULT_SUCCESS || items == null) {
            LibraryResult.ofError(result.resultCode)
        } else {
            LibraryResult.ofItemList(mediaBrowsePage(items, page, pageSize), params)
        }
    }

    // --- Lookups shared with playback ---

    /** A song the tree has already loaded, without touching the network. */
    fun cachedSong(videoId: String): Song? {
        return (browseSongs.values() + localBrowseSongs.values()).asSequence()
            .flatten().firstOrNull { it.id == videoId }
    }

    /** A single playable id looked up across every store the tree serves. */
    suspend fun findSong(mediaId: String): Song? {
        cachedSong(mediaId)?.let { return it }
        downloadedSongs().firstOrNull { it.id == mediaId }?.let { return it }
        likedSongs().firstOrNull { it.id == mediaId }?.let { return it }
        localBrowseSongs.get("RECENT") { recentlyPlayedSongs() }
            .firstOrNull { it.id == mediaId }?.let { return it }
        return localBrowseSongs.get("LOCAL_SONGS") { localDeviceSongs() }
            .firstOrNull { it.id == mediaId }
    }

    suspend fun searchSongs(query: String): List<Song> =
        if (query.isBlank()) emptyList() else browseSongs.get("SEARCH:${query.trim()}") {
            youtube().search(query.trim())
        }

    fun toBrowseItem(song: Song): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(song.id)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setDurationMs(song.duration.takeIf { it > 0L })
                .setArtworkUri(song.thumbnailUrl?.toArtworkUri()
                    ?: song.albumArtUri)
                .setExtras(contentStyleExtras(LIST_ITEM, LIST_ITEM))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build())
        // Device-local songs must arrive carrying their content:// URI:
        // onAddMediaItems preserves it for direct playback, while an item
        // without one would be pushed through YouTube stream resolution and
        // fail. Downloads also resolve locally, but through the mediaId
        // lookup in performResolution instead.
        if (song.source == SongSource.LOCAL && song.uri != null) {
            builder.setUri(song.uri)
        }
        return builder.build()
    }

    /** A search match shaped for the resolution pipeline: id plus placeholder,
     *  exactly what a browse-served YouTube item looks like on arrival. */
    fun toPlaceholderItem(song: Song, placeholderPrefix: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id)
            .setUri("$placeholderPrefix${song.id}")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setDurationMs(song.duration.takeIf { it > 0L })
                .setArtworkUri(song.thumbnailUrl.toArtworkUri())
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build())
            .build()

    // --- The tree ---

    /** Return a terminal library error instead of an exceptional/hanging callback. */
    private fun <T : Any> browseResult(block: suspend () -> LibraryResult<T>): ListenableFuture<LibraryResult<T>> =
        mainScope.future {
            val started = generation
            try {
                val result = withTimeout(10_000L) { block() }
                if (started == generation) result
                else LibraryResult.ofError(SessionResult.RESULT_ERROR_INVALID_STATE)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                KLog.w(TAG, "Media browse request timed out", e)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                LibraryResult.ofError(SessionResult.RESULT_ERROR_INVALID_STATE)
            } catch (e: Exception) {
                KLog.e(TAG, "Media browse request failed", e)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
            }
        }

    private suspend fun loadSearch(
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): LibraryResult<ImmutableList<MediaItem>> = try {
        LibraryResult.ofItemList(searchSongs(query).map(::toBrowseItem), params)
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        KLog.w(TAG, "Media browse search timed out", e)
        LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        LibraryResult.ofError(SessionResult.RESULT_ERROR_INVALID_STATE)
    } catch (e: Exception) {
        KLog.e(TAG, "Media browse search failed", e)
        LibraryResult.ofError(SessionResult.RESULT_ERROR_IO)
    }

    /**
     * Cars normally expose at most four root tabs. Keep all local categories
     * under Library so recommendations and playlists are not silently dropped.
     * Opening the root or Library itself never needs the network.
     */
    private fun rootItems(): ImmutableList<MediaItem> = ImmutableList.of(
        browsableCategoryItem("LIBRARY", "Library"),
        browsableCategoryItem("RECOMMENDED", "Recommended"),
        browsableCategoryItem("PLAYLISTS", "Playlists"),
    )

    private fun libraryItems(): ImmutableList<MediaItem> = ImmutableList.of(
        browsableCategoryItem("DOWNLOADS", "Downloaded", "Offline"),
        browsableCategoryItem("LIKED", "Liked Songs"),
        browsableCategoryItem("RECENT", "Recently Played"),
        browsableCategoryItem("LOCAL_SONGS", "On This Device"),
    )

    private fun browsableCategoryItem(
        mediaId: String,
        title: String,
        subtitle: String? = null,
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                // Categories carry no song art of their own, and head units
                // render artless grid tiles as a broken-image glyph - the
                // warning triangles in the field photo. A deterministic tile
                // from AutoArtworkProvider instead (content:// PNG, offline).
                .setArtworkUri(AutoArtwork.categoryKey(mediaId)?.let { AutoArtwork.uriFor(context, it) })
                .setExtras(contentStyleExtras(GRID_ITEM, LIST_ITEM))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build()

    private suspend fun childrenOf(parentId: String): List<MediaItem> = when (parentId) {
        "LIBRARY" -> libraryItems()
        "RECOMMENDED" -> browseSongs.get(parentId) {
            youtube().getRecommendations()
        }.map(::toBrowseItem)
        "PLAYLISTS" -> playlistsForBrowse().map(::playlistEntry)
        "DOWNLOADS" -> downloadedSongs().map(::toBrowseItem)
        "LIKED" -> likedSongs().map(::toBrowseItem)
        "RECENT" -> localBrowseSongs.get(parentId) { recentlyPlayedSongs() }.map(::toBrowseItem)
        "LOCAL_SONGS" -> localBrowseSongs.get(parentId) { localDeviceSongs() }.map(::toBrowseItem)
        else -> {
            require(parentId.startsWith("PLAYLIST_")) { "Unknown browse parent" }
            browseSongs.get(parentId) {
                youtube().getPlaylist(parentId.removePrefix("PLAYLIST_"))
            }.map(::toBrowseItem)
        }
    }

    private suspend fun playlistsForBrowse(): List<PlaylistDisplayItem> =
        browsePlaylists.get("PLAYLISTS") { youtube().getUserPlaylists() }

    private fun playlistEntry(playlist: PlaylistDisplayItem): MediaItem {
        val playlistId = playlist.url.substringAfter("list=")
        return MediaItem.Builder()
            .setMediaId("PLAYLIST_$playlistId")
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(playlist.name)
                .setSubtitle(playlist.uploaderName)
                .setArtworkUri(playlist.thumbnailUrl.toArtworkUri())
                .setExtras(contentStyleExtras(LIST_ITEM, LIST_ITEM))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build())
            .build()
    }

    /**
     * Listening history as playable songs, newest first, deduplicated so a
     * song played ten times appears once. Device-local plays are skipped:
     * history stores ids but not their content URIs, and a local id pushed
     * through YouTube stream resolution just dead-ends.
     */
    private suspend fun recentlyPlayedSongs(): List<Song> =
        try {
            statsRepository().loadHistory()
                .filter { it.source == SongSource.YOUTUBE }
                .distinctBy { it.songId }
                .take(RECENTLY_PLAYED_LIMIT)
                .map { entry ->
                    Song.fromYouTube(
                        videoId = entry.songId,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        duration = entry.duration,
                        thumbnailUrl = entry.thumbnailUrl
                    )
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to load listening history for browse", e)
            emptyList()
        }

    private suspend fun localDeviceSongs(): List<Song> =
        try {
            val prefs = preferences()
            songRepository().getSongs(
                excludedFolders = prefs.excludedFolders.value,
                manualScan = prefs.manualScanEnabled.value
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(TAG, "Failed to scan device library for browse", e)
            emptyList()
        }

    /**
     * A usable artwork [Uri], or null when there is no artwork.
     *
     * `Uri.parse("")` yields an empty Uri rather than "no artwork", and a media
     * browser handed one tries to load it and fails. Android Auto can fail the
     * whole item on that, not just the picture, so a missing thumbnail has to
     * mean the field is absent.
     */
    private fun String?.toArtworkUri(): Uri? =
        this?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    /**
     * Content style hints tell Auto how to render each item: as a grid or a
     * list, as browsable or playable. Set per item rather than only at the
     * root, because the root hints alone leave deeper levels to the client's
     * default - which is why every category renders as a grid and every song
     * list as a list.
     */
    private fun contentStyleExtras(browsableHint: Int, playableHint: Int): android.os.Bundle =
        android.os.Bundle().apply {
            putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
            putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", browsableHint)
            putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", playableHint)
        }

    private fun buildRootItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Root")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private companion object {
        const val TAG = "MusicService"
        // Cap on the "Recently Played" browse node - a car screen does not
        // want thousands of history rows, just what you have been listening to.
        const val RECENTLY_PLAYED_LIMIT = 50
        // android.media.browse.ContentStyle values: how Auto renders items.
        const val LIST_ITEM = 1
        const val GRID_ITEM = 2
    }
}
