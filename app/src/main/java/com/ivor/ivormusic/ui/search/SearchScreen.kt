package com.ivor.ivormusic.ui.search
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoSearchDateFilter
import com.ivor.ivormusic.data.VideoSearchSort
import com.ivor.ivormusic.data.YouTubeLinkParser
import com.ivor.ivormusic.data.ArtistItem
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.ui.home.HomeViewModel
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Outline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import com.ivor.ivormusic.ui.video.VideoCard
import com.ivor.ivormusic.ui.video.VideoOptionsSheetHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Quick-search topics shown on the video mode explore state
private val VIDEO_EXPLORE_TOPICS = listOf(
    "Gaming" to Icons.Rounded.SportsEsports,
    "Music" to Icons.Rounded.MusicNote,
    "News" to Icons.Rounded.Newspaper,
    "Live" to Icons.Rounded.LiveTv,
    "Podcasts" to Icons.Rounded.Podcasts,
    "Movies" to Icons.Rounded.Movie,
    "Tech" to Icons.Rounded.Science,
    "Sports" to Icons.Rounded.SportsBasketball,
    "Learning" to Icons.Rounded.School
)

/**
 * Segmented list shape helper for Expressive design
 */
@Composable
private fun getSegmentedShape(index: Int, count: Int, hasMore: Boolean = false, cornerSize: androidx.compose.ui.unit.Dp = 28.dp): Shape {
    return when {
        count == 1 && !hasMore -> RoundedCornerShape(cornerSize)
        index == 0 -> RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
        index == count - 1 && !hasMore -> RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)
        else -> RectangleShape
    }
}

/**
 * 🌟 Material 3 Expressive Search Screen
 * 
 * Design Features:
 * - Gradient header with decorative organic shapes
 * - Beautiful rounded search field with depth
 * - Premium segmented card design for results
 * - YouTube Music integration with pagination
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song) -> Unit = { _, song -> onSongClick(song) },
    /** Long-press a song result: opens the shared song options sheet. */
    onSongLongPress: ((Song) -> Unit)? = null,
    /** Queue a video result, next or at the end. */
    onEnqueueVideo: ((VideoItem, Boolean) -> Unit)? = null,
    /**
     * Play a single YouTube result and continue into its radio. Used instead of
     * [onPlayQueue] for loose result lists, where the neighbouring entries are
     * other uploads of the same title rather than a playlist worth queueing.
     */
    onPlayRadio: (Song) -> Unit = { song -> onPlayQueue(listOf(song), song) },
    onVideoClick: (VideoItem) -> Unit = {},
    onArtistClick: (ArtistItem) -> Unit = {},
    onAlbumClick: (PlaylistDisplayItem) -> Unit = {},
    onPlaylistClick: (PlaylistDisplayItem) -> Unit = {},
    onVideoPlaylistClick: (VideoPlaylist) -> Unit = {},
    /** Open a creator's page, from a Channels result or the long-press sheet. */
    onOpenChannel: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    videoMode: Boolean = false,
    localOnly: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * Hoisted by HomeScreen so the results keep their place across a tab switch
     * and the nav bar can send them back to the top on a re-tap. Still drives
     * the paging trigger below, which is what it was remembered here for.
     */
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState()
) {
    // Saveable, not just remembered: this composable is disposed and rebuilt
    // both on a tab switch (AnimatedContent in HomeScreen only keeps the
    // target tab's subtree composed) and on process death, and a plainly-
    // remembered query silently threw away what was typed either way - see
    // ROADMAP.md, Surviving process death. Only the query and the filters
    // that shape it are saved; the result lists stay plain remember and are
    // re-fetched by the LaunchedEffect below once query is restored, which
    // avoids needing a Parcelable/serializer story for every result type.
    var query by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    // Set once a "load more" comes back empty, so scrolling at the bottom of
    // an exhausted result set stops firing requests forever.
    var songResultsExhausted by remember { mutableStateOf(false) }
    var videoResultsExhausted by remember { mutableStateOf(false) }
    var youtubeResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var videoResults by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var videoPlaylistResults by remember { mutableStateOf<List<VideoPlaylist>>(emptyList()) }
    var channelResults by remember {
        mutableStateOf<List<com.ivor.ivormusic.data.SubscribedChannel>>(emptyList())
    }
    var artistResults by remember { mutableStateOf<List<ArtistItem>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<PlaylistDisplayItem>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<PlaylistDisplayItem>>(emptyList()) }
    var selectedCategory by rememberSaveable { mutableStateOf(SearchCategory.SONGS) }
    var selectedVideoCategory by rememberSaveable { mutableStateOf(VideoSearchCategory.VIDEOS) }
    var selectedDateFilter by rememberSaveable { mutableStateOf(VideoSearchDateFilter.ANY) }
    var selectedSort by rememberSaveable { mutableStateOf(VideoSearchSort.RELEVANCE) }
    
    var visibleLocalCount by remember { mutableIntStateOf(20) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // Search history and focus state
    val searchHistory by viewModel.searchHistory.collectAsState()
    var isSearchFocused by remember { mutableStateOf(false) }

    // Playlists and albums already kept, so a result the user saved earlier
    // comes back marked instead of inviting them to save it twice. One store
    // behind both, read through each mode's own shape.
    val savedPlaylistIds by viewModel.savedPlaylistIds.collectAsState()
    val savedVideoPlaylistIds by viewModel.savedVideoPlaylistIds.collectAsState()

    // Options sheet (long-press on a video search result), same wiring as the
    // video home feed
    var saveTargetVideo by remember { mutableStateOf<VideoItem?>(null) }

    fun onVideoLongPress(video: VideoItem) {
        saveTargetVideo = video
    }

    // Pasted YouTube link handling: when the query is a URL, resolve it into
    // a directly playable result instead of running a text search.
    val parsedLink = remember(query) { YouTubeLinkParser.parse(query) }
    var linkState by remember { mutableStateOf<LinkLookupState>(LinkLookupState.Idle) }
    var linkRetryToken by remember { mutableIntStateOf(0) }

    // Endless scroll: pull the next page once the bottom of the list is in
    // sight, rather than making the user hunt for a "load more" button. The
    // state itself is a parameter now, hoisted by HomeScreen.
    val isNearListEnd by remember {
        androidx.compose.runtime.derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 3
        }
    }

    // Video mode browse state: trending feed doubles as the explore list
    val trendingVideos by viewModel.trendingVideos.collectAsState()
    LaunchedEffect(videoMode) {
        if (videoMode && trendingVideos.isEmpty()) {
            viewModel.loadTrendingVideos()
        }
    }
    
    // Theme colors from MaterialTheme
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    // Filter local songs based on query
    val filteredLocalSongs = remember(query, songs) {
        if (query.isEmpty()) songs
        else songs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.album.contains(query, ignoreCase = true)
        }
    }
    
    // Search YouTube/Videos/Artists/Albums/Playlists when query changes.
    // Local-only mode never fetches: the local library filter below is
    // the entire search experience.
    LaunchedEffect(query, videoMode, selectedCategory, selectedVideoCategory, selectedDateFilter, selectedSort) {
        if (parsedLink != null) {
            // A pasted link is resolved by its own effect below; make sure no
            // stale text-search results linger behind the link result card.
            isLoading = false
            youtubeResults = emptyList()
            videoResults = emptyList()
            videoPlaylistResults = emptyList()
            channelResults = emptyList()
            artistResults = emptyList()
            albumResults = emptyList()
            playlistResults = emptyList()
            return@LaunchedEffect
        }
        if (query.length >= 2 && !localOnly) {
            delay(500) // Debounce
            isLoading = true

            // Clear previous results of other types
            youtubeResults = emptyList()
            videoResults = emptyList()
            videoPlaylistResults = emptyList()
            channelResults = emptyList()
            artistResults = emptyList()
            albumResults = emptyList()
            playlistResults = emptyList()
            // A new query gets a fresh pagination cursor in the repository,
            // so the exhausted flags have to come off with it.
            songResultsExhausted = false
            videoResultsExhausted = false

            if (videoMode) {
                when (selectedVideoCategory) {
                    VideoSearchCategory.VIDEOS -> videoResults = viewModel.searchVideos(query, selectedDateFilter, selectedSort)
                    VideoSearchCategory.PLAYLISTS -> videoPlaylistResults = viewModel.searchVideoPlaylists(query)
                    VideoSearchCategory.CHANNELS -> channelResults = viewModel.searchChannels(query)
                }
            } else {
                when (selectedCategory) {
                    SearchCategory.SONGS -> youtubeResults = viewModel.searchYouTube(query)
                    SearchCategory.ARTISTS -> artistResults = viewModel.searchArtists(query)
                    SearchCategory.ALBUMS -> albumResults = viewModel.searchAlbums(query)
                    SearchCategory.PLAYLISTS -> playlistResults = viewModel.searchPlaylists(query)
                }
            }
            isLoading = false
        } else {
            youtubeResults = emptyList()
            videoResults = emptyList()
            videoPlaylistResults = emptyList()
            channelResults = emptyList()
            artistResults = emptyList()
            albumResults = emptyList()
            playlistResults = emptyList()
        }
    }

    // Endless scroll driver. Keyed on the result sizes as well as the scroll
    // position so that a page which lands while the bottom is still in view
    // immediately pulls the next one, instead of stalling until the user
    // nudges the list.
    LaunchedEffect(
        isNearListEnd, youtubeResults.size, videoResults.size,
        query, videoMode, selectedCategory, selectedVideoCategory, selectedDateFilter
    ) {
        if (!isNearListEnd || isLoading || isLoadingMore) return@LaunchedEffect
        if (localOnly || parsedLink != null || query.length < 2) return@LaunchedEffect

        val loadingVideos = videoMode && selectedVideoCategory == VideoSearchCategory.VIDEOS
        val loadingSongs = !videoMode && selectedCategory == SearchCategory.SONGS
        when {
            loadingVideos && videoResults.isNotEmpty() && !videoResultsExhausted -> {
                isLoadingMore = true
                val more = viewModel.loadMoreVideoResults(query, selectedDateFilter)
                if (more.isEmpty()) {
                    videoResultsExhausted = true
                } else {
                    // Adjacent pages can overlap; never show a video twice
                    val merged = (videoResults + more).distinctBy { it.videoId }
                    // Nothing new survived de-duplication, so the feed is
                    // repeating itself - treat that as the end.
                    if (merged.size == videoResults.size) videoResultsExhausted = true
                    videoResults = merged
                }
                isLoadingMore = false
            }
            loadingSongs && youtubeResults.isNotEmpty() && !songResultsExhausted -> {
                isLoadingMore = true
                val more = viewModel.loadMoreResults(query)
                if (more.isEmpty()) {
                    songResultsExhausted = true
                } else {
                    val merged = (youtubeResults + more).distinctBy { it.id }
                    if (merged.size == youtubeResults.size) songResultsExhausted = true
                    youtubeResults = merged
                }
                isLoadingMore = false
            }
        }
    }

    saveTargetVideo?.let { video ->
        VideoOptionsSheetHost(
            video = video,
            viewModel = viewModel,
            onDismiss = { saveTargetVideo = null },
            onEnqueue = onEnqueueVideo?.let { enqueue -> { next -> enqueue(video, next) } },
            // Search results are never filtered - searching is explicit
            // intent - so "not interested" would visibly do nothing here and
            // is left out. Blocking the channel still has a real effect on
            // every feed, and the undo snackbar says so.
            allowNotInterested = false,
            onOpenChannel = onOpenChannel
        )
    }

    // Reset visible count when query changes
    LaunchedEffect(query) {
        visibleLocalCount = 20
    }

    // Resolve a pasted YouTube link (video, playlist or channel) off the normal
    // search path. A watch link resolves through one watch-next call; a playlist
    // link loads its items. Retriggered by the retry button via linkRetryToken.
    LaunchedEffect(parsedLink, videoMode, linkRetryToken) {
        if (parsedLink == null) {
            linkState = LinkLookupState.Idle
            return@LaunchedEffect
        }
        // A channel link has nothing to preview - there is no track to play and
        // no list to show - so it opens the creator's page instead of resolving
        // into a result card. The query is cleared on the way out so coming back
        // to search does not immediately reopen it.
        parsedLink.channelRef?.let { ref ->
            linkState = LinkLookupState.Idle
            query = ""
            onOpenChannel(ref)
            return@LaunchedEffect
        }
        linkState = LinkLookupState.Resolving
        // A playlist link points at something with a page of its own, exactly
        // like the channel link above, so it opens that page instead of
        // resolving into a result card. The page is where the title, the
        // author, the artwork and - the reason this matters - the Save button
        // are; the card had none of them, so a playlist arriving by link was
        // the one that could not be kept.
        //
        // A link that also names a video still resolves to that video: naming
        // one is asking for it. And a list with no page behind it - a generated
        // mix answers "This playlist type is unviewable" - falls through to the
        // preview below, which for a mix is the whole of what there is to show.
        val linkPlaylistId = parsedLink.playlistId
        if (linkPlaylistId != null && parsedLink.videoId == null) {
            val page = viewModel.resolvePlaylistPageFromLink(linkPlaylistId)
            if (page != null) {
                linkState = LinkLookupState.Idle
                // Cleared for the same reason the channel branch clears it:
                // coming back from the page must land on search, not reopen
                // what was just backed out of.
                query = ""
                if (videoMode) onVideoPlaylistClick(page.toVideoPlaylist())
                else onPlaylistClick(page.toDisplayItem())
                return@LaunchedEffect
            }
        }
        linkState = when {
            parsedLink.videoId != null -> {
                val video = viewModel.resolveVideoFromLink(parsedLink.videoId)
                if (video == null) LinkLookupState.Error
                else LinkLookupState.MediaResult(video)
            }
            parsedLink.playlistId != null && videoMode -> {
                val videos = viewModel.resolvePlaylistVideosFromLink(parsedLink.playlistId)
                if (videos.isEmpty()) LinkLookupState.Error
                else LinkLookupState.PlaylistVideosResult(videos)
            }
            parsedLink.playlistId != null -> {
                val playlistSongs = viewModel.resolvePlaylistSongsFromLink(parsedLink.playlistId)
                if (playlistSongs.isEmpty()) LinkLookupState.Error
                else LinkLookupState.PlaylistSongsResult(playlistSongs)
            }
            else -> LinkLookupState.Idle
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 140.dp)
        ) {
            // ========== HERO HEADER WITH SEARCH ==========
            item {
                SearchHeroHeader(
                    query = query,
                    onQueryChange = { query = it },
                    onFocusChanged = { isSearchFocused = it },
                    onSearch = {
                        if (it.isNotBlank()) {
                            viewModel.addToSearchHistory(it)
                            focusManager.clearFocus()
                        }
                    },
                    placeholderText = if (videoMode) stringResource(R.string.search_placeholder_videos)
                    else stringResource(R.string.search_placeholder_music),
                    isLinkDetected = parsedLink != null,
                    primaryColor = primaryColor,
                    primaryContainerColor = primaryContainerColor,
                    tertiaryContainerColor = tertiaryContainerColor,
                    surfaceColor = surfaceColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor
                )
            }
            
            // Category Chips (music mode only; pointless in local-only and
            // hidden while a pasted link resolves)
            if (!videoMode && !localOnly && query.isNotEmpty() && parsedLink == null) {
                item {
                    SearchFilterChips(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        primaryColor = primaryColor,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            // Video Mode filters, hidden while a pasted link resolves: the
            // Videos / Playlists toggle, plus upload-date + sort chips that
            // only apply to video results
            if (videoMode && query.isNotEmpty() && parsedLink == null) {
                item {
                    VideoSearchFilterChips(
                        selectedCategory = selectedVideoCategory,
                        onCategorySelected = { selectedVideoCategory = it },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                if (selectedVideoCategory == VideoSearchCategory.VIDEOS) {
                    item {
                        VideoFilterChipRow(
                            labels = VideoSearchDateFilter.entries.map { it.label },
                            selectedIndex = selectedDateFilter.ordinal,
                            onSelected = { selectedDateFilter = VideoSearchDateFilter.entries[it] },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    item {
                        VideoFilterChipRow(
                            labels = VideoSearchSort.entries.map { it.label },
                            selectedIndex = selectedSort.ordinal,
                            onSelected = { selectedSort = VideoSearchSort.entries[it] },
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                }
            }
            
            // ========== CONTENT ==========
            when {
                // Pasted YouTube link: resolve it into a playable hero result
                // instead of running a text search.
                parsedLink != null -> {
                    item {
                        LinkDetectedBanner(
                            state = linkState,
                            primaryColor = primaryColor,
                            textColor = textColor
                        )
                    }
                    when (val state = linkState) {
                        is LinkLookupState.MediaResult -> {
                            item {
                                val video = state.video
                                val subtitle = listOfNotNull(
                                    video.channelName.takeIf { it.isNotBlank() && it != "Unknown" },
                                    video.viewCount.takeIf { it.isNotBlank() },
                                    video.uploadedDate
                                ).joinToString(" • ")
                                LinkHeroCard(
                                    title = video.title,
                                    subtitle = subtitle,
                                    thumbnailUrl = video.highResThumbnailUrl ?: video.thumbnailUrl,
                                    durationText = video.formattedDuration.takeIf { it.isNotBlank() },
                                    badgeText = if (videoMode) stringResource(R.string.badge_video) else stringResource(R.string.badge_song),
                                    accentColor = primaryColor,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onPlay = {
                                        if (videoMode) {
                                            onVideoClick(video)
                                        } else {
                                            onPlayRadio(video.toSong())
                                        }
                                    }
                                )
                            }
                        }

                        is LinkLookupState.PlaylistSongsResult -> {
                            item {
                                LinkPlaylistHeader(
                                    count = state.songs.size,
                                    isVideos = false,
                                    thumbnailUrl = state.songs.firstOrNull()?.highResThumbnailUrl
                                        ?: state.songs.firstOrNull()?.thumbnailUrl,
                                    accentColor = primaryColor,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onPlayAll = { onPlayQueue(state.songs, state.songs.first()) }
                                )
                            }
                            itemsIndexed(state.songs) { index, song ->
                                SearchSongCard(
                                    song = song,
                                    onClick = { onPlayQueue(state.songs, song) },
                                    onLongClick = onSongLongPress?.let { press -> { press(song) } },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = primaryColor,
                                    isYouTube = true,
                                    shape = getSegmentedShape(index, state.songs.size),
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                                if (index < state.songs.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 44.dp),
                                        color = textColor.copy(alpha = 0.06f)
                                    )
                                }
                            }
                        }

                        is LinkLookupState.PlaylistVideosResult -> {
                            item {
                                LinkPlaylistHeader(
                                    count = state.videos.size,
                                    isVideos = true,
                                    thumbnailUrl = state.videos.firstOrNull()?.thumbnailUrl,
                                    accentColor = primaryColor,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onPlayAll = { onVideoClick(state.videos.first()) }
                                )
                            }
                            items(state.videos) { video ->
                                CompactVideoRow(
                                    video = video,
                                    onClick = { onVideoClick(video) },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }

                        LinkLookupState.Error -> {
                            item {
                                LinkErrorCard(
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onRetry = { linkRetryToken++ }
                                )
                            }
                        }

                        else -> {
                            item {
                                LinkResolvingCard(
                                    videoMode = videoMode,
                                    primaryColor = primaryColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor
                                )
                            }
                        }
                    }
                }

                // Show search history if focused and query is empty. Skipped in
                // video mode so the field auto-focusing on entry doesn't hide the
                // video Explore browse behind the recent-searches list.
                !videoMode && isSearchFocused && query.isEmpty() && searchHistory.isNotEmpty() -> {
                    item {
                        SearchHistoryList(
                            history = searchHistory,
                            onHistoryClick = { 
                                query = it
                                focusManager.clearFocus()
                            },
                            onRemoveClick = { viewModel.removeFromSearchHistory(it) },
                            onClearAll = { viewModel.clearSearchHistory() },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            surfaceColor = surfaceColor
                        )
                    }
                }

                isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = primaryColor
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    when {
                                        videoMode && selectedVideoCategory == VideoSearchCategory.PLAYLISTS -> stringResource(R.string.searching_playlists)
                                        videoMode -> stringResource(R.string.searching_videos)
                                        selectedCategory == SearchCategory.ARTISTS -> stringResource(R.string.searching_artists)
                                        selectedCategory == SearchCategory.ALBUMS -> stringResource(R.string.searching_albums)
                                        selectedCategory == SearchCategory.PLAYLISTS -> stringResource(R.string.searching_playlists)
                                        else -> stringResource(R.string.searching_music)
                                    },
                                    color = secondaryTextColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                // Video mode browse: explore topics + trending instead of the music library
                videoMode && query.isEmpty() -> {
                    item {
                        Text(
                            stringResource(R.string.search_explore_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    // Topic chips: one tap starts a search
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(VIDEO_EXPLORE_TOPICS) { (topic, icon) ->
                                Surface(
                                    onClick = {
                                        query = topic
                                        focusManager.clearFocus()
                                    },
                                    shape = CircleShape,
                                    color = cardColor,
                                    tonalElevation = 1.dp
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                         Text(
                                             exploreTopicLabel(topic),
                                             style = MaterialTheme.typography.labelLarge,
                                             color = textColor
                                         )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.trending_now),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                                .padding(top = 20.dp, bottom = 4.dp)
                        )
                    }

                    if (trendingVideos.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = primaryColor
                                )
                            }
                        }
                    } else {
                        items(trendingVideos) { video ->
                            CompactVideoRow(
                                video = video,
                                onClick = { onVideoClick(video) },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }
                }

                query.isEmpty() -> {
                    // Browse section when no search
                    item {
                        Text(
                            stringResource(R.string.browse_your_library),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    val displaySongs = songs.take(visibleLocalCount)
                    val hasMoreLocal = songs.size > visibleLocalCount

                    itemsIndexed(displaySongs) { index, song ->
                        SearchSongCard(
                            song = song,
                            onClick = { onPlayQueue(songs, song) },
                            onLongClick = onSongLongPress?.let { press -> { press(song) } },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = primaryColor,
                            shape = getSegmentedShape(index, displaySongs.size, hasMoreLocal),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < displaySongs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }

                    // Show more button for local browse
                    if (hasMoreLocal) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            ShowMoreButton(
                                onClick = { visibleLocalCount += 20 },
                                cardColor = cardColor,
                                primaryColor = primaryColor
                            )
                        }
                    }
                }
                
                // Video Mode Results: the Videos/Playlists toggle above decides
                // which search ran, so only one of these lists is ever populated
                videoMode && (
                    videoResults.isNotEmpty() ||
                        videoPlaylistResults.isNotEmpty() ||
                        channelResults.isNotEmpty()
                    ) -> {
                    if (channelResults.isNotEmpty()) {
                        item {
                            ResultHeader(
                                title = "Channels",
                                count = channelResults.size,
                                icon = Icons.Rounded.AccountCircle,
                                color = MaterialTheme.colorScheme.primary,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                        items(channelResults, key = { it.channelId }) { channel ->
                            ChannelResultRow(
                                channel = channel,
                                onClick = {
                                    viewModel.addToSearchHistory(query)
                                    onOpenChannel(channel.channelId)
                                },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }

                    if (videoPlaylistResults.isNotEmpty()) {
                        item {
                            ResultHeader(
                                title = "Playlists",
                                count = videoPlaylistResults.size,
                                icon = Icons.Rounded.QueueMusic,
                                color = Color(0xFF_FF9800), // Orange
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                        items(videoPlaylistResults, key = { it.playlistId }) { playlist ->
                            VideoPlaylistRow(
                                playlist = playlist,
                                onClick = {
                                    viewModel.addToSearchHistory(query)
                                    onVideoPlaylistClick(playlist)
                                },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                isSaved = savedVideoPlaylistIds.contains(playlist.playlistId),
                                onToggleSave = { viewModel.toggleSavedVideoPlaylist(playlist) }
                            )
                        }
                    }

                    // Video Search Results Section
                    if (videoResults.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.TravelExplore,
                                            contentDescription = null,
                                            tint = Color(0xFFFF0000),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    stringResource(R.string.source_youtube_videos),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${videoResults.size} results",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        // Display video results; long-press opens the save sheet
                        itemsIndexed(videoResults) { index, video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onLongClick = { onVideoLongPress(video) },
                                onOpenChannel = onOpenChannel,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        item {
                            SearchPagingFooter(
                                isLoadingMore = isLoadingMore,
                                isExhausted = videoResultsExhausted,
                                cardColor = cardColor,
                                accentColor = primaryColor,
                                secondaryTextColor = secondaryTextColor
                            )
                        }
                    }
                }
                
                
                artistResults.isNotEmpty() -> {
                    item {
                        ResultHeader(
                            title = "Artists",
                            count = artistResults.size,
                            icon = Icons.Rounded.Person,
                            color = Color(0xFF9C27B0), // Purple
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                    
                    val artistPairs = artistResults.chunked(2)
                    items(artistPairs) { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { artist ->
                                ArtistResultCard(
                                    artist = artist,
                                    onClick = { 
                                        viewModel.addToSearchHistory(query)
                                        onArtistClick(artist) 
                                    },
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // --- Album Results ---
                albumResults.isNotEmpty() -> {
                     item {
                        ResultHeader(
                            title = "Albums",
                            count = albumResults.size,
                            icon = Icons.Rounded.Album,
                            color = Color(0xFF_009688), // Teal
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                    items(albumResults) { album ->
                        PlaylistResultCard(
                            item = album,
                            onClick = {
                                viewModel.addToSearchHistory(query)
                                onAlbumClick(album)
                            },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isAlbum = true,
                            isSaved = savedPlaylistIds.contains(album.id),
                            onToggleSave = { viewModel.toggleSavedPlaylist(album, isAlbum = true) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // --- Playlist Results ---
                playlistResults.isNotEmpty() -> {
                    item {
                        ResultHeader(
                            title = "Playlists",
                            count = playlistResults.size,
                            icon = Icons.Rounded.QueueMusic,
                            color = Color(0xFF_FF9800), // Orange
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                    items(playlistResults) { playlist ->
                        PlaylistResultCard(
                            item = playlist,
                            onClick = {
                                viewModel.addToSearchHistory(query)
                                onPlaylistClick(playlist)
                            },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            isAlbum = false,
                            isSaved = savedPlaylistIds.contains(playlist.id),
                            onToggleSave = { viewModel.toggleSavedPlaylist(playlist) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                youtubeResults.isNotEmpty() -> {
                    // YouTube Search Results Section
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF0000).copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.TravelExplore,
                                        contentDescription = null,
                                        tint = Color(0xFFFF0000),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.source_youtube_music),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                "${youtubeResults.size} results",
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryTextColor
                            )
                        }
                    }
                    
                    itemsIndexed(youtubeResults) { index, song ->
                        SearchSongCard(
                            song = song,
                            // Radio, not the result list: the other hits are
                            // usually the same track from other uploaders.
                            onClick = { onPlayRadio(song) },
                            onLongClick = onSongLongPress?.let { press -> { press(song) } },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = primaryColor,
                            isYouTube = true,
                            shape = getSegmentedShape(index, youtubeResults.size, hasMore = true),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < youtubeResults.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    // Pages now arrive on scroll; this footer only reports state.
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 44.dp),
                            color = textColor.copy(alpha = 0.06f)
                        )
                        SearchPagingFooter(
                            isLoadingMore = isLoadingMore,
                            isExhausted = songResultsExhausted,
                            cardColor = cardColor,
                            accentColor = primaryColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    }
                    
                    // Local Library matches section
                    if (filteredLocalSongs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = primaryColor.copy(alpha = 0.15f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(
                                    stringResource(R.string.source_local_library),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${filteredLocalSongs.size} matches",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryTextColor
                                )
                            }
                        }
                        
                        val localDisplayed = filteredLocalSongs.take(visibleLocalCount)
                        val hasMoreLocalMatches = filteredLocalSongs.size > visibleLocalCount
                        
                        itemsIndexed(localDisplayed) { index, song ->
                            SearchSongCard(
                                song = song,
                                onClick = { onPlayQueue(filteredLocalSongs, song) },
                                onLongClick = onSongLongPress?.let { press -> { press(song) } },
                                cardColor = cardColor,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = primaryColor,
                                shape = getSegmentedShape(index, localDisplayed.size, hasMoreLocalMatches),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            if (index < localDisplayed.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 44.dp),
                                    color = textColor.copy(alpha = 0.06f)
                                )
                            }
                        }
                        
                        if (hasMoreLocalMatches) {
                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 44.dp),
                                    color = textColor.copy(alpha = 0.06f)
                                )
                                ShowMoreButton(
                                    onClick = { visibleLocalCount += 20 },
                                    cardColor = cardColor,
                                    primaryColor = primaryColor
                                )
                            }
                        }
                    }
                }
                
                filteredLocalSongs.isEmpty() && youtubeResults.isEmpty() && query.isNotEmpty() -> {
                    // No results
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = CircleShape,
                                    color = secondaryTextColor.copy(alpha = 0.1f),
                                    modifier = Modifier.size(100.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = secondaryTextColor.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    stringResource(R.string.search_no_results),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.search_no_results_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }
                }
                
                else -> {
                    // Local search results only (when no YouTube results but have local matches)
                    item {
                        Text(
                            "${filteredLocalSongs.size} results",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = secondaryTextColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    
                    val displayedLocal = filteredLocalSongs.take(visibleLocalCount)
                    val hasMoreLocal = filteredLocalSongs.size > visibleLocalCount
                    
                    itemsIndexed(displayedLocal) { index, song ->
                        SearchSongCard(
                            song = song,
                            onClick = { onPlayQueue(filteredLocalSongs, song) },
                            onLongClick = onSongLongPress?.let { press -> { press(song) } },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = primaryColor,
                            shape = getSegmentedShape(index, displayedLocal.size, hasMoreLocal),
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        if (index < displayedLocal.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    if (hasMoreLocal) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            ShowMoreButton(
                                onClick = { visibleLocalCount += 20 },
                                cardColor = cardColor,
                                primaryColor = primaryColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero Header with Search Bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchHeroHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    placeholderText: String,
    isLinkDetected: Boolean,
    primaryColor: Color,
    primaryContainerColor: Color,
    tertiaryContainerColor: Color,
    surfaceColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    // Auto-focus the search field (and pop the keyboard) when the screen appears
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(150) // let the enter transition settle so focus/IME lands reliably
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            onQueryChange(spoken)
            onSearch(spoken)
        }
        // A cancelled or empty recognition leaves the field exactly as it was:
        // nothing to clear, nothing to search, nothing to explain.
    }

    fun launchVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to listen to?")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // No recognizer on the device. A toast beats a silent dead button,
            // and this is the one voice-search failure that is a device fact.
            Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Guard against invalid dimensions during transitions
        if (maxWidth <= 0.dp) {
            return@BoxWithConstraints
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Title
            Text(
                stringResource(R.string.search_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Search Field with beautiful rounded corners
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChanged(it.isFocused) },
                shape = RoundedCornerShape(28.dp),
                color = surfaceColor,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(placeholderText, color = secondaryTextColor)
                    },
                    leadingIcon = {
                        // Morph the magnifier into a link icon (with a springy
                        // pop) the moment a YouTube URL is detected.
                        AnimatedContent(
                            targetState = isLinkDetected,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.4f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(tween(120))) togetherWith
                                    (scaleOut(targetScale = 0.4f, animationSpec = tween(120)) + fadeOut(tween(120)))
                            },
                            label = "searchLeadingIcon"
                        ) { linkDetected ->
                            Icon(
                                if (linkDetected) Icons.Rounded.Link else Icons.Default.Search,
                                contentDescription = null,
                                tint = primaryColor
                            )
                        }
                    },
                    trailingIcon = {
                        // Mic while the field is empty - the one-tap voice
                        // entry point - morphing to a clear button the moment
                        // there is something to clear. Same springy swap the
                        // leading icon uses, so the two read as one system.
                        AnimatedContent(
                            targetState = query.isNotEmpty(),
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.4f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) + fadeIn(tween(120))) togetherWith
                                    (scaleOut(targetScale = 0.4f, animationSpec = tween(120)) + fadeOut(tween(120)))
                            },
                            label = "searchTrailingIcon"
                        ) { hasQuery ->
                            if (hasQuery) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.cd_clear),
                                        tint = secondaryTextColor
                                    )
                                }
                            } else {
                                IconButton(onClick = { launchVoiceSearch() }) {
                                    Icon(
                                        Icons.Rounded.KeyboardVoice,
                                        contentDescription = stringResource(R.string.cd_voice_search),
                                        tint = primaryColor
                                    )
                                }
                            }
                        }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch(query) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = primaryColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Show More Button for segmented lists
 */
@Composable
private fun ShowMoreButton(
    onClick: () -> Unit,
    cardColor: Color,
    primaryColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = cardColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                stringResource(R.string.action_show_more),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )
        }
    }
}

/**
 * Song Card for search results
 */
@Composable
private fun SearchSongCard(
    song: Song,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color,
    isYouTube: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .songRowClick(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = cardColor,
        tonalElevation = 1.dp
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: stringResource(R.string.untitled_song),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = song.artist.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: stringResource(R.string.unknown_artist),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isYouTube) Color(0xFFFF0000).copy(alpha = 0.15f) else accentColor.copy(alpha = 0.15f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = if (isYouTube) Color(0xFFFF0000) else accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = textColor,
                supportingColor = secondaryTextColor
            )
        )
    }
}

enum class SearchCategory {
    SONGS, ARTISTS, ALBUMS, PLAYLISTS
}

enum class VideoSearchCategory {
    VIDEOS, PLAYLISTS, CHANNELS
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoSearchFilterChips(
    selectedCategory: VideoSearchCategory,
    onCategorySelected: (VideoSearchCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    // Same M3 Expressive connected button group as the music-mode chips
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        VideoSearchCategory.entries.forEachIndexed { index, category ->
            val selected = category == selectedCategory
            androidx.compose.material3.ToggleButton(
                checked = selected,
                onCheckedChange = { onCategorySelected(category) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shapes = when (index) {
                    0 -> androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()
                    VideoSearchCategory.entries.lastIndex -> androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    when (category) {
                        VideoSearchCategory.VIDEOS -> stringResource(R.string.cat_videos)
                        VideoSearchCategory.PLAYLISTS -> stringResource(R.string.cat_playlists)
                        VideoSearchCategory.CHANNELS -> stringResource(R.string.cat_channels)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ResultHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "$count results",
            style = MaterialTheme.typography.bodySmall,
            color = secondaryTextColor
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchFilterChips(
    selectedCategory: SearchCategory,
    onCategorySelected: (SearchCategory) -> Unit,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    // M3 Expressive connected button group (replaces the FilterChip row) —
    // same pattern as the Library tabs, shape-morphs on select.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(androidx.compose.material3.ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        SearchCategory.entries.forEachIndexed { index, category ->
            val selected = category == selectedCategory
            androidx.compose.material3.ToggleButton(
                checked = selected,
                onCheckedChange = { onCategorySelected(category) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shapes = when (index) {
                    0 -> androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShapes()
                    SearchCategory.entries.lastIndex -> androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    when (category) {
                        SearchCategory.SONGS -> stringResource(R.string.cat_songs)
                        SearchCategory.ARTISTS -> stringResource(R.string.cat_artists)
                        SearchCategory.ALBUMS -> stringResource(R.string.cat_albums)
                        SearchCategory.PLAYLISTS -> stringResource(R.string.cat_playlists)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

private fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

@Composable
fun VideoFilterChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Single-select filter row for video mode search (upload date, sort
    // order). The option sets are too wide for a connected button group (the
    // segmented row is only used where the whole set fits, like the music
    // categories), so this is a scrollable row of pill FilterChips matching
    // the explore topic chips above.
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(labels) { index, label ->
            val selected = index == selectedIndex
            FilterChip(
                selected = selected,
                onClick = { onSelected(index) },
                shape = CircleShape,
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                leadingIcon = if (selected) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistResultCard(
    artist: ArtistItem,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier
) {
    // List of organic shapes from the library
    val shapes = remember {
        listOf(
            MaterialShapes.Cookie9Sided,
            MaterialShapes.ClamShell,
            MaterialShapes.Flower,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Puffy,
            MaterialShapes.Sunny
        )
    }
    
    val shapeItem = remember(artist.name) {
        shapes[Math.abs(artist.name.hashCode()) % shapes.size]
    }
    val artistShape = shapeItem.toShape()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .graphicsLayer { 
                    shape = artistShape
                    clip = true 
                }
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = artist.thumbnailUrl ?: "",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (artist.isVerified) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.CheckCircle, 
                    contentDescription = stringResource(R.string.cd_verified),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
            }
            
            if (!artist.subscriberCount.isNullOrEmpty()) {
                Text(
                    text = "${artist.subscriberCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistResultCard(
    item: PlaylistDisplayItem,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    isAlbum: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Saved state, or null when this card offers no save action. A visible
     * toggle rather than a long-press: nothing else in these results hides an
     * action behind a hold, and a gesture nobody knows about is not an action.
     */
    isSaved: Boolean? = null,
    onToggleSave: () -> Unit = {}
) {
     Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        color = cardColor,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             val albumShape = MaterialShapes.Square.toShape()
             val imageShape = if (isAlbum) albumShape else RoundedCornerShape(20.dp)
             
             AsyncImage(
                model = item.thumbnailUrl ?: "",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(imageShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
             )
             Spacer(modifier = Modifier.size(16.dp))

             Column(modifier = Modifier.weight(1f)) {
                 Text(
                     text = item.name,
                     style = MaterialTheme.typography.titleMedium,
                     fontWeight = FontWeight.SemiBold,
                     color = textColor,
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis
                 )
                 Spacer(modifier = Modifier.height(2.dp))
                 Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                         if (isAlbum) Icons.Rounded.Album else Icons.Rounded.QueueMusic,
                         contentDescription = null,
                         tint = secondaryTextColor,
                         modifier = Modifier.size(14.dp)
                      )
                      Spacer(modifier = Modifier.size(6.dp))
                      val metadata = if (isAlbum) {
                          stringResource(R.string.album_metadata, item.uploaderName)
                      } else {
                          buildList {
                              add(stringResource(R.string.label_playlist))
                              item.uploaderName.takeIf { it.isNotBlank() }?.let(::add)
                              item.itemCount.takeIf { it >= 0 }?.let { count ->
                                  add(pluralStringResource(R.plurals.n_songs, count, count))
                              }
                          }.joinToString(" • ")
                      }
                      Text(
                         text = metadata,
                         style = MaterialTheme.typography.bodySmall,
                         color = secondaryTextColor,
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis
                      )
                 }
             }

             if (isSaved != null) {
                 val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
                 IconButton(
                     onClick = {
                         haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                         onToggleSave()
                     }
                 ) {
                     Icon(
                         imageVector = if (isSaved) Icons.Rounded.Bookmark
                             else Icons.Rounded.BookmarkBorder,
                         contentDescription = if (isSaved) stringResource(R.string.cd_remove_from_library)
                             else stringResource(R.string.cd_save_to_library),
                         tint = if (isSaved) MaterialTheme.colorScheme.primary
                             else secondaryTextColor
                     )
                 }
             }
        }
    }
}

/**
 * Search History List Composable
 */
@Composable
fun SearchHistoryList(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onRemoveClick: (String) -> Unit,
    onClearAll: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    surfaceColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = secondaryTextColor
            )
            TextButton(onClick = onClearAll) {
                Text(
                    stringResource(R.string.action_clear_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        history.forEach { query ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onHistoryClick(query) },
                color = surfaceColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveClick(query) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_remove),
                            tint = secondaryTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Pasted YouTube link UI
// ============================================================

/** UI state for a pasted YouTube link being resolved into playable content. */
private sealed interface LinkLookupState {
    data object Idle : LinkLookupState
    data object Resolving : LinkLookupState

    /** A watch/shorts/youtu.be link resolved to one video (or song in music mode). */
    data class MediaResult(val video: VideoItem) : LinkLookupState

    /** A playlist link resolved in music mode. */
    data class PlaylistSongsResult(val songs: List<Song>) : LinkLookupState

    /** A playlist link resolved in video mode. */
    data class PlaylistVideosResult(val videos: List<VideoItem>) : LinkLookupState

    data object Error : LinkLookupState
}

/** Music-mode representation of a video resolved from a pasted link. */
private fun VideoItem.toSong(): Song = Song.fromYouTube(
    videoId = videoId,
    title = title,
    artist = channelName,
    album = "",
    duration = duration * 1000,
    thumbnailUrl = thumbnailUrl
)

/** Shared springy entrance for the link result cards. */
private fun linkCardEnter() = fadeIn(tween(220)) +
    scaleIn(
        initialScale = 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    ) +
    slideInVertically(
        initialOffsetY = { it / 6 },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

/**
 * Pill banner that springs in under the search bar as soon as a YouTube URL
 * is detected, live-updating its label as the link resolves.
 */
@Composable
private fun LinkDetectedBanner(
    state: LinkLookupState,
    primaryColor: Color,
    textColor: Color
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    val isResolving = state is LinkLookupState.Resolving || state is LinkLookupState.Idle

    // Gentle heartbeat on the link icon while the lookup is in flight
    val pulse = rememberInfiniteTransition(label = "linkBannerPulse")
    val iconScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "linkBannerIconScale"
    )

    AnimatedVisibility(
        visibleState = entrance,
        enter = fadeIn(tween(180)) + slideInVertically(
            initialOffsetY = { -it / 2 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            Surface(
                shape = CircleShape,
                color = primaryColor.copy(alpha = 0.12f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier
                            .size(16.dp)
                            .scale(if (isResolving) iconScale else 1f)
                    )
                    AnimatedContent(
                        targetState = when (state) {
                            is LinkLookupState.MediaResult -> stringResource(R.string.link_ready_to_play)
                            is LinkLookupState.PlaylistSongsResult,
                            is LinkLookupState.PlaylistVideosResult -> stringResource(R.string.link_playlist_loaded)
                            LinkLookupState.Error -> stringResource(R.string.link_error_banner)
                            else -> stringResource(R.string.link_detected)
                        },
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically { it / 2 }) togetherWith
                                (fadeOut(tween(150)))
                        },
                        label = "linkBannerLabel"
                    ) { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Loading state for a pasted link: the link icon with two expanding sonar
 * rings while the metadata is fetched.
 */
@Composable
private fun LinkResolvingCard(
    videoMode: Boolean,
    primaryColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    val sonar = rememberInfiniteTransition(label = "linkSonar")
    val ring1Progress by sonar.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)),
        label = "linkSonarRing1"
    )
    val ring2Progress by sonar.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            initialStartOffset = StartOffset(700)
        ),
        label = "linkSonarRing2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                listOf(ring1Progress, ring2Progress).forEach { progress ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(1f + progress)
                            .background(
                                primaryColor.copy(alpha = 0.25f * (1f - progress)),
                                CircleShape
                            )
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Link,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                stringResource(R.string.link_fetching),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (videoMode) stringResource(R.string.link_preparing_video) else stringResource(R.string.link_preparing_song),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor
            )
        }
    }
}

/**
 * Hero result for a resolved video/song link: full-width thumbnail with a
 * pulsing play button, springing in with a scale + slide entrance. The whole
 * card is tappable and squishes slightly while pressed.
 */
@Composable
private fun LinkHeroCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    durationText: String?,
    badgeText: String,
    accentColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onPlay: () -> Unit
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "linkHeroPressScale"
    )
    val playPulse = rememberInfiniteTransition(label = "linkHeroPlayPulse")
    val playScale by playPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "linkHeroPlayScale"
    )

    AnimatedVisibility(visibleState = entrance, enter = linkCardEnter()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .scale(pressScale)
                .clip(RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onPlay
                ),
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            tonalElevation = 2.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.45f)
                                )
                            )
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    if (durationText != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.8f)
                        ) {
                            Text(
                                durationText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .scale(playScale),
                        shape = CircleShape,
                        color = accentColor,
                        shadowElevation = 6.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header card for a resolved playlist link: cover, item count and a play-all
 * affordance, with the same springy entrance as the hero card.
 */
@Composable
private fun LinkPlaylistHeader(
    count: Int,
    isVideos: Boolean,
    thumbnailUrl: String?,
    accentColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onPlayAll: () -> Unit
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = entrance, enter = linkCardEnter()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onPlayAll),
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.link_header_playlist),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        if (isVideos) stringResource(R.string.link_tap_to_play_videos, count)
                        else stringResource(R.string.link_tap_to_play_songs, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = accentColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play all",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Error state for a pasted link, with a retry action. */
@Composable
private fun LinkErrorCard(
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onRetry: () -> Unit
) {
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = entrance, enter = linkCardEnter()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            tonalElevation = 2.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.link_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.link_error_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.FilledTonalButton(onClick = onRetry) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.action_try_again))
                }
            }
        }
    }
}

/**
 * Playlist row for video mode search results: thumbnail with a stacked
 * playlist badge on the left, title/uploader/count on the right. Same
 * density as [CompactVideoRow] so mixed results read as one list.
 */
@Composable
private fun VideoPlaylistRow(
    playlist: VideoPlaylist,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    /**
     * Saved state, or null when this row offers no save action. Visible rather
     * than behind a long-press, matching the music results: nothing else in
     * this list hides an action behind a hold.
     */
    isSaved: Boolean? = null,
    onToggleSave: () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Playlist badge, like YouTube's stacked-videos corner chip
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = playlist.videoCountText?.substringBefore(" ") ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp, end = 4.dp)
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(stringResource(R.string.label_playlist))
                        playlist.subtitle?.let { append(" • ").append(it) }
                        playlist.videoCountText?.let { append(" • ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSaved != null) {
                val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onToggleSave()
                    },
                    // Centred against a 16:9 thumbnail rather than the top of
                    // the row, so it does not float beside a one-line title.
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Bookmark
                            else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isSaved) stringResource(R.string.cd_remove_from_library)
                            else stringResource(R.string.cd_save_to_library),
                        tint = if (isSaved) MaterialTheme.colorScheme.primary
                            else secondaryTextColor
                    )
                }
            }
        }
    }
}

/**
 * Compact video row for the video mode explore list: thumbnail on the
 * left, title/channel/views on the right. Denser than VideoCard so the
 * browse state reads as a list, not a feed.
 */
@Composable
private fun CompactVideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (video.isLive) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF0000)
                    ) {
                        Text(
                            text = stringResource(R.string.badge_live),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else if (video.duration > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp, end = 4.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(video.channelName)
                        if (video.viewCount.isNotEmpty()) {
                            append(" • ")
                            append(video.viewCount)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Footer under a paginated result list. Results load automatically as the list
 * nears its end, so this never invites a tap - it only says whether more is on
 * the way or the list has run out.
 */
@Composable
private fun SearchPagingFooter(
    isLoadingMore: Boolean,
    isExhausted: Boolean,
    cardColor: Color,
    accentColor: Color,
    secondaryTextColor: Color
) {
    if (!isLoadingMore && !isExhausted) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)),
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = cardColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoadingMore) {
                LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = accentColor
                )
            } else {
                Text(
                    stringResource(R.string.thats_everything),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryTextColor
                )
            }
        }
    }
}


/**
 * A channel in video-mode search results.
 *
 * Deliberately the only video-mode result row with no save or queue action:
 * a channel is not something to keep or play, it is somewhere to go, so the
 * whole row is one target and there is nothing else on it to miss.
 */
@Composable
private fun ChannelResultRow(
    channel: com.ivor.ivormusic.data.SubscribedChannel,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.ivor.ivormusic.ui.channel.CreatorAvatar(
                avatarUrl = channel.avatarUrl,
                name = channel.name,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(
                    channel.handle?.takeIf { it.isNotBlank() },
                    channel.subscriberCountText?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = secondaryTextColor
            )
        }
    }
}


@Composable
private fun exploreTopicLabel(topic: String): String = when (topic) {
    "Gaming" -> stringResource(R.string.topic_gaming)
    "Music" -> stringResource(R.string.topic_music)
    "News" -> stringResource(R.string.topic_news)
    "Live" -> stringResource(R.string.topic_live)
    "Podcasts" -> stringResource(R.string.topic_podcasts)
    "Movies" -> stringResource(R.string.topic_movies)
    "Tech" -> stringResource(R.string.topic_tech)
    "Sports" -> stringResource(R.string.topic_sports)
    else -> stringResource(R.string.topic_learning)
}
