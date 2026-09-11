package com.ivor.ivormusic.ui.artist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ArtistPage
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.SimilarArtist
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.googleImageCropped
import com.ivor.ivormusic.data.isUnknownAlbum
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.sortedInAlbumOrder
import com.ivor.ivormusic.ui.channel.creatorMetadata
import com.ivor.ivormusic.ui.components.AvatarImage
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.ui.components.SEARCH_FIELD_MIN_ITEMS
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.components.VideoThumbnail
import com.ivor.ivormusic.ui.components.releaseCaption
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlinx.coroutines.launch

/**
 * How the track list under an artist is ordered.
 *
 * [Suggested] is the order the source gave us and stays the default: for a
 * YouTube artist that is roughly popularity, which is the order somebody
 * arriving at an artist they do not know actually wants. The other three exist
 * because a discography of two hundred tracks in popularity order is unusable
 * for the opposite job - finding one song you already have in mind.
 *
 * Deliberately not persisted: a sort is a thing you do to the page you are on,
 * and coming back to a different artist under last week's ordering is a
 * setting nobody asked for. It survives rotation and nothing more.
 */
private enum class ArtistSongSort { Suggested, Title, Album, Longest }

@Composable
private fun artistSortLabel(sort: ArtistSongSort): String = when (sort) {
    ArtistSongSort.Suggested -> stringResource(R.string.ar_sort_suggested)
    ArtistSongSort.Title -> stringResource(R.string.ar_sort_title)
    ArtistSongSort.Album -> stringResource(R.string.ar_sort_album)
    ArtistSongSort.Longest -> stringResource(R.string.ar_sort_longest)
}

/**
 * Sort and filter in one place, so the list, the counts, the "show more"
 * budget and what Play enqueues can never disagree about what is on screen.
 */
private fun List<Song>.arrangedForArtist(sort: ArtistSongSort, query: String): List<Song> {
    val trimmed = query.trim()
    val filtered = if (trimmed.isEmpty()) this else filter { song ->
        song.title.contains(trimmed, ignoreCase = true) ||
            song.album.contains(trimmed, ignoreCase = true)
    }
    return when (sort) {
        ArtistSongSort.Suggested -> filtered
        ArtistSongSort.Title -> filtered.sortedBy { it.title.lowercase() }
        // Album order within an album, albums themselves alphabetical, and
        // anything untagged last rather than under a blank heading.
        ArtistSongSort.Album -> filtered.sortedWith(
            compareBy<Song> { isUnknownAlbum(it.album) }
                .thenBy { it.album.lowercase() }
                .thenBy { it.discNumber ?: Int.MAX_VALUE }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
        ArtistSongSort.Longest -> filtered.sortedByDescending { it.duration }
    }
}

/**
 * The sort control: connected toggle buttons, the same segmented shape
 * Spotlight's filters and the SponsorBlock categories use.
 *
 * Connected rather than loose chips because these are four mutually exclusive
 * views of one list, which is exactly what M3 Expressive's connected group
 * says and what a row of independent-looking chips does not. It scrolls rather
 * than dividing the width four ways: the labels are different lengths, and
 * equal quarters would either clip "Suggested" or waste the row on "A-Z".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistSortRow(
    selected: ArtistSongSort,
    onSelect: (ArtistSongSort) -> Unit
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val entries = ArtistSongSort.entries.toList()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        entries.forEachIndexed { index, entry ->
            ToggleButton(
                checked = entry == selected,
                onCheckedChange = {
                    if (entry != selected) {
                        haptics.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick
                        )
                        onSelect(entry)
                    }
                },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(artistSortLabel(entry))
            }
        }
    }
}

/**
 * Segmented list shape helper for Expressive design
 */
@Composable
private fun getSegmentedShape(index: Int, count: Int, cornerSize: androidx.compose.ui.unit.Dp = 28.dp): Shape {
    return when {
        count == 1 -> RoundedCornerShape(cornerSize)
        index == 0 -> RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
        index == count - 1 -> RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)
        else -> RectangleShape
    }
}

/**
 * How many of the top songs the overview shows before "See all". Five is what
 * the YouTube Music top-songs shelf itself carries, so the overview shows the
 * shelf and "See all" opens the rest of the discography.
 */
private const val POPULAR_SONG_COUNT = 5

/**
 * The corner of the content sheet that rises over the hero photo. The large
 * Expressive corner, and the same 28dp the segmented song list uses, so the
 * page's two rounded edges agree.
 */
private val SHEET_CORNER = 28.dp

/**
 * The hero's height as a fraction of its width: close to square on a phone,
 * which is what gives a portrait of a person room to be a portrait.
 */
private const val HERO_ASPECT = 0.96f

/**
 * The hero never takes more than this share of the window, so landscape and
 * split screen still show the music the hero introduces.
 */
private const val HERO_MAX_WINDOW_FRACTION = 0.6f

/** Page gutter for everything below the hero. */
private val ARTIST_GUTTER = 20.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    artistId: String? = null,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: ((String, List<Song>) -> Unit)? = null,
    onOpenAlbum: ((com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit)? = null,
    viewModel: HomeViewModel? = null,
    modifier: Modifier = Modifier,
    onSongLongPress: ((Song) -> Unit)? = null,
    /**
     * Open another artist in place (the "Fans also like" shelf). The Library
     * keeps one artist route, so this replaces rather than stacks: back
     * returns to the Library root, the same as every other route here.
     */
    onOpenArtist: ((name: String, id: String) -> Unit)? = null,
    /**
     * Open a playlist carrying this artist (the "Featured on" shelf) through
     * the Library's playlist route, the same destination search results use.
     */
    onOpenPlaylist: ((PlaylistDisplayItem) -> Unit)? = null,
    /**
     * Open this creator's video-mode channel page.
     *
     * The other half of the cross-link on the channel screen. The two stay
     * separate surfaces because a discography and an upload feed are genuinely
     * different content; they share this link, the channel's avatar, verified
     * tick and follower count, so arriving at a musician from either mode does
     * not feel like arriving at two different people.
     */
    onOpenChannel: ((channelId: String) -> Unit)? = null
) {
    // Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    // State for fetched songs
    var artistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var fetchedAlbums by remember { mutableStateOf<List<PlaylistDisplayItem>>(emptyList()) }
    var fetchedSingles by remember { mutableStateOf<List<PlaylistDisplayItem>>(emptyList()) }
    // The full artist page behind the lists above: bio, monthly audience,
    // banner, similar artists and featured playlists. Null for local-library
    // artists, which never reach the network.
    var artistPage by remember { mutableStateOf<ArtistPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMoreRemote by remember { mutableStateOf(true) }
    var visibleSongCount by remember { mutableIntStateOf(20) }
    // Both survive rotation but not a change of artist, which is why they are
    // keyed on the name below rather than only declared here.
    var songSort by remember(artistName) { mutableStateOf(ArtistSongSort.Suggested) }
    var songQuery by remember(artistName) { mutableStateOf("") }
    // The full, sortable discography is a child page over the overview, so
    // the overview can be the curated page the design asks for without losing
    // any of the list's tools. Closed by a change of artist, like the sort.
    var showAllSongs by remember(artistName, artistId) { mutableStateOf(false) }
    var hasLocalSongs by remember { mutableStateOf(false) }
    // The canonical UC id behind this artist, once something has resolved one.
    // Local-only artists never get one, which is why every use of it is guarded
    // rather than assumed.
    var resolvedChannelId by remember { mutableStateOf<String?>(null) }
    var channelHeader by remember {
        mutableStateOf<com.ivor.ivormusic.data.ChannelHeader?>(null)
    }
    val scope = rememberCoroutineScope()

    // Fetch songs - first check local files, then fetch from internet
    LaunchedEffect(artistName, artistId, songs) {
        isLoading = true
        visibleSongCount = 20
        artistSongs = emptyList()
        fetchedAlbums = emptyList()
        fetchedSingles = emptyList()
        artistPage = null

        // Only genuinely local files take the offline path. Liked/downloaded
        // YouTube songs shouldn't block fetching the full artist page.
        val localArtistSongs = songs.filter {
            it.artist.equals(artistName, ignoreCase = true) &&
                    it.source == SongSource.LOCAL
        }

        channelHeader = null
        resolvedChannelId = null

        if (localArtistSongs.isNotEmpty()) {
            // Use local songs if available
            artistSongs = localArtistSongs
            hasLocalSongs = true
            // Local albums are derived automatically below
            isLoading = false
        } else if (viewModel != null) {
            // Resolve a channel id when the caller only knows the name
            // (e.g. Library navigation passes the artist name as the id).
            val resolvedId = artistId?.takeIf { it.startsWith("UC") }
                ?: viewModel.searchArtists(artistName).let { results ->
                    (results.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                        ?: results.firstOrNull())?.id
                }

            resolvedChannelId = resolvedId
            if (resolvedId != null) {
                // One browse carries songs, both release shelves and the
                // identity block; the page keeps them split for the sections.
                val page = viewModel.getArtistPage(resolvedId)
                artistPage = page
                artistSongs = page?.songs.orEmpty()
                fetchedAlbums = page?.albums.orEmpty()
                fetchedSingles = page?.singles.orEmpty()
            }
            // Fallback: plain name search if the artist page gave nothing
            if (artistSongs.isEmpty()) {
                artistSongs = viewModel.searchArtistSongs(artistName)
            }
            hasLocalSongs = false
            isLoading = false
        } else {
            artistSongs = emptyList()
            hasLocalSongs = false
            isLoading = false
        }
    }

    // The identity half the artist browse does not carry - the channel's
    // avatar, verified tick and subscriber count - from the same channel
    // browse the video-mode page uses. One request, and the only reason this
    // screen makes it: without it a musician has a tick on one side of the app
    // and none on the other.
    LaunchedEffect(resolvedChannelId) {
        val id = resolvedChannelId ?: return@LaunchedEffect
        channelHeader = viewModel?.getChannelHeader(id)
    }

    // Local album names, grouped from the files themselves.
    val albums = remember(artistSongs, hasLocalSongs) {
        if (hasLocalSongs) artistSongs.groupBy { it.album }.keys.toList() else emptyList()
    }

    // The list as the user has arranged it. Everything on the All songs page
    // reads this rather than artistSongs: the rows, the counts, the pagination
    // budget and what a tap enqueues all have to be the same list, or tapping
    // the third row plays something else.
    val arrangedSongs = remember(artistSongs, songSort, songQuery) {
        artistSongs.arrangedForArtist(songSort, songQuery)
    }
    val isFiltering = songQuery.isNotBlank()
    // Songs currently visible (with pagination)
    val displayedSongs = remember(arrangedSongs, visibleSongCount) {
        arrangedSongs.take(visibleSongCount)
    }
    // A filter is applied to what has already been fetched, so paging further
    // into YouTube while one is active would answer a narrowed list with
    // unnarrowed rows. The remote budget is offered on the whole list only.
    val hasMoreSongs = arrangedSongs.size > visibleSongCount ||
            (!isFiltering && !hasLocalSongs && viewModel != null && canLoadMoreRemote)

    // The shelf's own top songs, in shelf order. Only songs that are also in
    // artistSongs: a tap plays artistSongs from the tapped song, and a start
    // song absent from the list it is played from clamps to index 0 and plays
    // a different track. Distinct because the rows are keyed by id.
    val popularSongs = remember(artistPage, artistSongs) {
        val inList = artistSongs.mapTo(HashSet()) { it.id }
        (artistPage?.topSongs.orEmpty().filter { it.id in inList }
            .takeIf { it.isNotEmpty() } ?: artistSongs)
            .distinctBy { it.id }
            .take(POPULAR_SONG_COUNT)
    }
    val canSeeAllSongs = artistSongs.size > popularSongs.size ||
            (!hasLocalSongs && viewModel != null)

    val displayName = artistPage?.name?.takeIf { it.isNotBlank() && !isUnknownArtist(it) }
        ?: artistName.takeIf { !isUnknownArtist(it) }
        ?: stringResource(R.string.unknown_artist)

    // Under the name: how many people listen, as the reference has it, and
    // the follower count the channel page shows, so both faces of the artist
    // quote the same number. A local artist is a tag on some files and has
    // neither, so it gets the one fact it does have.
    val songCountLabel = pluralStringResource(R.plurals.n_songs, artistSongs.size, artistSongs.size)
    val heroMetadata = if (hasLocalSongs) {
        listOf(songCountLabel)
    } else {
        creatorMetadata(artistPage?.monthlyAudience, channelHeader?.subscriberCountText)
            .ifEmpty { if (artistSongs.isNotEmpty()) listOf(songCountLabel) else emptyList() }
    }

    // The hero's picture, best first: the immersive photo the artist page
    // carries; the channel avatar, which is a photo of the same person; the
    // cover of something they made, which is every local-library artist.
    val heroFallback = remember(channelHeader, artistSongs) {
        val avatar = channelHeader?.avatarUrl?.takeIf { it.isNotBlank() }
        if (avatar != null) {
            avatar to googleImageAtSize(avatar, HERO_FALLBACK_PX)
        } else {
            artistSongs.firstNotNullOfOrNull { song ->
                val low = song.thumbnailUrl ?: song.albumArtUri?.toString()
                low?.let { it to song.highResThumbnailUrl }
            } ?: (null to null)
        }
    }

    val playAll: () -> Unit = {
        if (artistSongs.isNotEmpty()) onPlayQueue(artistSongs, null)
    }
    val shuffleAll: () -> Unit = {
        if (artistSongs.isNotEmpty()) onPlayQueue(artistSongs.shuffled(), null)
    }
    val radioSeed = artistSongs.firstOrNull { it.source == SongSource.YOUTUBE }
    val startRadio: (() -> Unit)? = if (radioSeed != null && viewModel != null) {
        {
            scope.launch {
                val radio = viewModel.getRadioSongs(radioSeed.id)
                if (radio.isNotEmpty()) {
                    onPlayQueue(listOf(radioSeed) + radio, radioSeed)
                }
            }
        }
    } else null

    val showMoreSongs: () -> Unit = {
        if (arrangedSongs.size > visibleSongCount) {
            // Show more from the list already fetched
            visibleSongCount += 20
        } else if (viewModel != null && !hasLocalSongs && !isLoadingMore) {
            // Load more from YouTube
            scope.launch {
                isLoadingMore = true
                val moreSongs = viewModel.loadMoreResults(artistName)
                if (moreSongs.isNotEmpty()) {
                    artistSongs = (artistSongs + moreSongs).distinctBy { it.id }
                    visibleSongCount += 20
                } else {
                    canLoadMoreRemote = false
                }
                isLoadingMore = false
            }
        }
    }

    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    PredictiveBackStack(
        childOpen = showAllSongs,
        onBack = { showAllSongs = false },
        modifier = modifier,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            ) {
                // Keyed on the artist: "Fans also like" swaps the artist in
                // place, and the next page should open at its hero rather than
                // at the previous page's scroll offset.
                val listState = remember(artistName, artistId) { LazyListState() }
                var heroHeightPx by remember { mutableIntStateOf(0) }
                // The top bar takes a surface and the name once the hero is
                // mostly gone, the point where the photo stops saying whose
                // page this is.
                val barCollapsed by remember(listState) {
                    derivedStateOf {
                        listState.firstVisibleItemIndex > 0 ||
                            (heroHeightPx > 0 &&
                                listState.firstVisibleItemScrollOffset > heroHeightPx * 0.62f)
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(48.dp),
                            color = primaryColor
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // Extra clearance so the last shelf sits above the
                        // mini player + floating nav bar + system inset.
                        contentPadding = PaddingValues(bottom = 220.dp)
                    ) {
                        // ========== HERO ==========
                        item(key = "hero") {
                            ArtistHero(
                                name = displayName,
                                isVerified = channelHeader?.isVerified == true,
                                metadata = heroMetadata,
                                bannerUrl = artistPage?.bannerUrl,
                                fallbackUrl = heroFallback.first,
                                fallbackHighResUrl = heroFallback.second,
                                // Read in the layer, not in composition, so a
                                // scroll redraws the photo without recomposing.
                                scrollOffsetPx = {
                                    if (listState.firstVisibleItemIndex == 0) {
                                        listState.firstVisibleItemScrollOffset.toFloat()
                                    } else 0f
                                },
                                modifier = Modifier.onSizeChanged { heroHeightPx = it.height }
                            )
                        }

                        // ========== ACTIONS ==========
                        // On the sheet rather than over the photo, so the photo
                        // carries only the name - the reference's whole point.
                        val channelId = resolvedChannelId
                        val showChannelLink = onOpenChannel != null && channelId != null
                        if (artistSongs.isNotEmpty() || showChannelLink) {
                            item(key = "actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = ARTIST_GUTTER)
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (artistSongs.isNotEmpty()) {
                                        PlaySplitButton(
                                            onPlay = playAll,
                                            onShuffle = shuffleAll,
                                            onStartRadio = startRadio
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    // Only offered once a real channel id has
                                    // resolved. A local-library artist is a tag
                                    // on a file and has no channel to open.
                                    if (onOpenChannel != null && channelId != null) {
                                        FilledIconButton(
                                            onClick = { onOpenChannel(channelId) },
                                            modifier = Modifier.size(48.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme
                                                    .surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme
                                                    .onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                Icons.Rounded.SmartDisplay,
                                                contentDescription = stringResource(R.string.ar_open_youtube_channel),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ========== POPULAR SONGS ==========
                        // The top of the discography, open rows as the
                        // reference draws them. "See all" opens the whole list
                        // with its sort and filter.
                        if (popularSongs.isNotEmpty()) {
                            item(key = "popular_header") {
                                ArtistSectionHeader(
                                    title = stringResource(
                                        if (hasLocalSongs) R.string.cat_songs else R.string.ar_section_popular
                                    ),
                                    actionLabel = if (canSeeAllSongs) {
                                        stringResource(R.string.action_see_all)
                                    } else null,
                                    onAction = { showAllSongs = true }
                                )
                            }
                            items(popularSongs, key = { "popular_${it.id}" }) { song ->
                                PopularSongRow(
                                    song = song,
                                    onClick = { onPlayQueue(artistSongs, song) },
                                    onMore = onSongLongPress?.let { press -> { press(song) } }
                                )
                            }
                        }

                        // ========== ALBUMS (device) ==========
                        // Device albums are grouped from the files themselves.
                        // Streams arrive pre-split as Albums and Singles shelves.
                        val validLocalAlbums = albums.filter { !isUnknownAlbum(it) }
                        if (hasLocalSongs && validLocalAlbums.isNotEmpty()) {
                            item(key = "local_albums_header") {
                                ArtistSectionHeader(title = stringResource(R.string.section_albums))
                            }
                            item(key = "local_albums") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = ARTIST_GUTTER),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(validLocalAlbums, key = { it }) { albumName ->
                                        val albumSongs = artistSongs
                                            .filter { it.album == albumName }
                                            .sortedInAlbumOrder()
                                        ArtworkCard(
                                            title = albumName,
                                            subtitle = pluralStringResource(
                                                R.plurals.n_songs, albumSongs.size, albumSongs.size
                                            ),
                                            thumbnailUrl = albumSongs.firstOrNull()?.let {
                                                it.highResThumbnailUrl ?: it.thumbnailUrl
                                                    ?: it.albumArtUri?.toString()
                                            },
                                            onClick = {
                                                onAlbumClick?.invoke(albumName, albumSongs)
                                                    ?: onPlayQueue(albumSongs, null)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ========== STREAMED RELEASES ==========
                        // The browse arrives with Albums complete and Singles
                        // paged; each keeps its own row so EPs stop hiding
                        // among LPs.
                        val streamedAlbums = fetchedAlbums.filter { !isUnknownAlbum(it.name) }
                        if (!hasLocalSongs && streamedAlbums.isNotEmpty()) {
                            item(key = "albums_header") {
                                ArtistSectionHeader(title = stringResource(R.string.section_albums))
                            }
                            item(key = "albums") {
                                ArtistReleaseRow(releases = streamedAlbums, onOpenAlbum = onOpenAlbum)
                            }
                        }
                        val streamedSingles = fetchedSingles.filter { !isUnknownAlbum(it.name) }
                        if (!hasLocalSongs && streamedSingles.isNotEmpty()) {
                            item(key = "singles_header") {
                                ArtistSectionHeader(title = stringResource(R.string.ar_section_singles_eps))
                            }
                            item(key = "singles") {
                                ArtistReleaseRow(releases = streamedSingles, onOpenAlbum = onOpenAlbum)
                            }
                        }

                        // ========== FEATURED ON ==========
                        // Playlists carrying this artist, drawn larger than the
                        // releases as the reference's playlist tiles are, and
                        // opened through the same playlist route search uses.
                        val featuredPlaylists = artistPage?.featuredOn.orEmpty()
                        if (!hasLocalSongs && featuredPlaylists.isNotEmpty() && onOpenPlaylist != null) {
                            item(key = "featured_header") {
                                ArtistSectionHeader(title = stringResource(R.string.ar_section_featured_on))
                            }
                            item(key = "featured") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = ARTIST_GUTTER),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(featuredPlaylists, key = { it.id }) { entry ->
                                        ArtworkCard(
                                            title = entry.title,
                                            subtitle = entry.subtitle?.takeIf { it.isNotBlank() }
                                                ?: stringResource(R.string.label_playlist),
                                            thumbnailUrl = entry.thumbnailUrl,
                                            width = 176.dp,
                                            artShape = RoundedCornerShape(28.dp),
                                            placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            onClick = {
                                                onOpenPlaylist(
                                                    PlaylistDisplayItem(
                                                        name = entry.title,
                                                        url = "https://music.youtube.com/playlist?list=${entry.id}",
                                                        uploaderName = entry.subtitle?.takeIf { it.isNotBlank() }
                                                            ?: "YouTube Music",
                                                        thumbnailUrl = entry.thumbnailUrl
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // ========== FANS ALSO LIKE ==========
                        // Other artists, opened in place: the Library keeps one
                        // artist route, so back returns to the root like every
                        // other route there.
                        val similarArtists = artistPage?.similarArtists.orEmpty()
                        if (!hasLocalSongs && similarArtists.isNotEmpty() && onOpenArtist != null) {
                            item(key = "similar_header") {
                                ArtistSectionHeader(title = stringResource(R.string.ar_section_similar))
                            }
                            item(key = "similar") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = ARTIST_GUTTER - 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(similarArtists, key = { it.id }) { entry ->
                                        SimilarArtistCard(
                                            entry = entry,
                                            onClick = { onOpenArtist(entry.name, entry.id) }
                                        )
                                    }
                                }
                            }
                        }

                        // ========== ABOUT ==========
                        // The immersive header's biography, collapsed: a
                        // thousand-plus character bio open by default would
                        // bury the music it describes.
                        val artistBio = artistPage?.bio
                        if (!hasLocalSongs && !artistBio.isNullOrBlank()) {
                            item(key = "about_header") {
                                ArtistSectionHeader(title = stringResource(R.string.ar_section_about))
                            }
                            item(key = "about") {
                                ArtistBioCard(
                                    bio = artistBio,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    primaryColor = primaryColor,
                                    modifier = Modifier.padding(horizontal = ARTIST_GUTTER)
                                )
                            }
                        }

                        if (artistSongs.isEmpty()) {
                            item(key = "empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.ar_no_songs),
                                        color = secondaryTextColor,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // Pinned rather than scrolled with the hero, so back is
                // reachable while loading and after the photo has gone.
                ArtistTopBar(
                    title = displayName,
                    collapsed = !isLoading && barCollapsed,
                    onBack = onBack
                )
            }
        }
    ) { committedByGesture ->
        AnimatedContent(
            targetState = showAllSongs,
            label = "ArtistAllSongs",
            transitionSpec = {
                val content = when {
                    // The finger already performed this exit.
                    committedByGesture -> EnterTransition.None togetherWith ExitTransition.None
                    !targetState ->
                        fadeIn(animationSpec = effectsSpec) togetherWith
                            (slideOutHorizontally(animationSpec = spatialSpec) { it } +
                                fadeOut(animationSpec = effectsSpec))
                    else ->
                        (slideInHorizontally(animationSpec = spatialSpec) { it } +
                            fadeIn(animationSpec = effectsSpec)) togetherWith
                            fadeOut(animationSpec = effectsSpec)
                }
                // The closed state is empty on this layer, so the default
                // SizeTransform would animate the container between nothing
                // and full screen and clip the page to it on the way.
                content using SizeTransform(clip = false) { _, _ -> snap() }
            }
        ) { open ->
            if (open) {
                ArtistAllSongsPage(
                    artistName = displayName,
                    totalCount = artistSongs.size,
                    arrangedSongs = arrangedSongs,
                    displayedSongs = displayedSongs,
                    isFiltering = isFiltering,
                    sort = songSort,
                    onSortChange = {
                        songSort = it
                        // A re-sort re-numbers the list, so the twenty rows on
                        // screen are a different twenty; paging back to the
                        // top of the budget keeps "show more" meaning the same
                        // thing it did before.
                        visibleSongCount = 20
                    },
                    query = songQuery,
                    onQueryChange = {
                        songQuery = it
                        visibleSongCount = 20
                    },
                    hasMoreSongs = hasMoreSongs,
                    isLoadingMore = isLoadingMore,
                    onShowMore = showMoreSongs,
                    // The arranged list, not the raw one: the queue has to be
                    // what is on screen, or playing the third row under an A-Z
                    // sort continues into the songs that follow it in
                    // popularity order instead.
                    onSongClick = { song -> onPlayQueue(arrangedSongs, song) },
                    onSongLongPress = onSongLongPress,
                    onBack = { showAllSongs = false }
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
    }
}

/** Pixels asked for when the hero falls back to a square avatar. */
private const val HERO_FALLBACK_PX = 1080

/** Widest crop the hero asks for; beyond it Google only upscales. */
private const val HERO_MAX_REQUEST_PX = 1920

/**
 * The immersive top of the page: the artist's photo edge to edge under the
 * status bar, the name set large on it, and the content sheet's rounded edge
 * rising over its foot.
 *
 * **The photo is asked for at the hero's own shape.** The immersive banner is
 * served 2.4:1 with the portrait pillarboxed inside it, and its `-p` URLs are
 * crops Google makes from the full source at whatever size is requested
 * (verified September 2026). So the hero requests exactly its frame: sharper
 * than cropping the wide banner on the device, and it never shows the bars.
 *
 * **Text sits on a fade into the page's own background**, not on a black
 * scrim with white type: the name is `onBackground` on `background`, so it
 * reads in both themes and on all of the app's palettes without a hardcoded
 * color. The photo keeps its top half untouched.
 */
@Composable
private fun ArtistHero(
    name: String,
    isVerified: Boolean,
    metadata: List<String>,
    bannerUrl: String?,
    fallbackUrl: String?,
    fallbackHighResUrl: String?,
    scrollOffsetPx: () -> Float,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val windowHeight = LocalWindowInfo.current.containerDpSize.height
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val photoHeight = min(maxWidth * HERO_ASPECT, windowHeight * HERO_MAX_WINDOW_FRACTION)
            .coerceAtLeast(260.dp)
        val frameWidth = maxWidth

        val photo = remember(bannerUrl, fallbackUrl, fallbackHighResUrl, frameWidth, photoHeight) {
            if (bannerUrl != null) {
                val widthPx = with(density) { frameWidth.roundToPx() }.coerceAtLeast(1)
                val heightPx = with(density) { photoHeight.roundToPx() }.coerceAtLeast(1)
                val scale = minOf(1f, HERO_MAX_REQUEST_PX.toFloat() / widthPx)
                val w = (widthPx * scale).toInt().coerceAtLeast(1)
                val h = (heightPx * scale).toInt().coerceAtLeast(1)
                // A quarter-size crop first, so something is on screen at
                // once; the full crop fades in over it.
                googleImageCropped(bannerUrl, w / 4, h / 4) to googleImageCropped(bannerUrl, w, h)
            } else {
                fallbackUrl to fallbackHighResUrl
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = photoHeight)
                // The photo drifts down under the scroll; clipped here so it
                // never draws over the sheet below.
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { translationY = scrollOffsetPx() * 0.5f }
            ) {
                val low = photo.first ?: photo.second
                if (low != null) {
                    VideoThumbnail(
                        thumbnailUrl = low,
                        highResThumbnailUrl = photo.second?.takeIf { it != low },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        showProgress = false,
                        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                } else {
                    HeroPlaceholder()
                }
            }

            // Top fade: the status bar icons and the back button need
            // something to sit on whatever the photo is. Faded to the same
            // color at zero alpha rather than to Transparent, which is
            // transparent black and greys the band in a light theme.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(background.copy(alpha = 0.55f), background.copy(alpha = 0f))
                        )
                    )
            )
            // Bottom fade: carries the name and hands the photo to the sheet.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0f),
                            0.42f to background.copy(alpha = 0f),
                            0.74f to background.copy(alpha = 0.6f),
                            1f to background.copy(alpha = 0.92f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    // Clear of the back button when a long name at a large
                    // font scale makes the column taller than the photo.
                    .padding(top = 64.dp)
                    .padding(horizontal = ARTIST_GUTTER)
                    .padding(bottom = SHEET_CORNER + 10.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.03).em,
                        lineHeight = 1.05.em
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // Big for "Frank Ocean", smaller rather than cut for
                    // "Red Hot Chili Peppers".
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 28.sp,
                        maxFontSize = 52.sp,
                        stepSize = 2.sp
                    )
                )
                if (isVerified || metadata.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isVerified) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.cd_verified),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        if (metadata.isNotEmpty()) {
                            Text(
                                text = metadata.joinToString("  •  "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // The content sheet's rounded edge, rising over the photo's foot.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(SHEET_CORNER)
                    .background(
                        background,
                        RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER)
                    )
            )
        }
    }
}

/**
 * The hero for an artist with no picture anywhere: the theme's containers
 * with a person cut in an Expressive shape, so the page still has a top.
 */
@Composable
private fun HeroPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(168.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            )
        }
    }
}

/**
 * Back over the hero, and a bar with the name once the hero has scrolled
 * away. The surface and the title fade (a crossfade, so `tween`); the title
 * also rises into place.
 */
@Composable
private fun ArtistTopBar(
    title: String,
    collapsed: Boolean,
    onBack: () -> Unit,
) {
    val barColor by animateColorAsState(
        targetValue = if (collapsed) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0f)
        },
        animationSpec = tween(220),
        label = "artistBarColor"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(
            onClick = onBack,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    .copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        AnimatedVisibility(
            visible = collapsed,
            modifier = Modifier.weight(1f),
            enter = fadeIn(tween(220)) + slideInVertically(
                spring(stiffness = Spring.StiffnessMediumLow)
            ) { it / 2 },
            exit = fadeOut(tween(160)) + slideOutVertically(
                spring(stiffness = Spring.StiffnessMedium)
            ) { it / 2 }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A section title with an optional pill action on the right ("See all"), as
 * the reference sets "Popular songs" against "All songs".
 */
@Composable
private fun ArtistSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ARTIST_GUTTER, end = ARTIST_GUTTER - 4.dp)
            .padding(top = 24.dp, bottom = 8.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Surface(
                onClick = onAction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * One popular song: cover, title, where it is from, and the options sheet
 * behind the trailing button as well as behind a long press, because a
 * visible button is the only route most people ever find.
 */
@Composable
private fun PopularSongRow(
    song: Song,
    onClick: () -> Unit,
    onMore: (() -> Unit)?,
) {
    val subtitle = listOfNotNull(
        song.album.takeIf { it.isNotBlank() && !isUnknownAlbum(it) },
        song.releaseYear?.toString()
    ).joinToString(" • ").ifBlank { song.artist }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .songRowClick(onClick = onClick, onLongClick = onMore)
            .padding(start = ARTIST_GUTTER, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(
            song = song,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** A card's press response: a small spring dip, the house bouncy default. */
@Composable
private fun pressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "artworkCardPress"
    )
    return scale
}

/**
 * An artwork-first card: the cover is the card, the words sit under it. Used
 * for releases and playlists alike, so every shelf on the page shares a
 * rhythm. The cover is asked for at the size it is drawn and layered over the
 * original, per `googleImageAtSize`.
 */
@Composable
private fun ArtworkCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String?,
    onClick: () -> Unit,
    width: Dp = 148.dp,
    artShape: Shape = RoundedCornerShape(20.dp),
    placeholderIcon: ImageVector = Icons.Rounded.Album,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val targetPx = with(LocalDensity.current) { width.roundToPx() }
    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUrl != null) {
                VideoThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    highResThumbnailUrl = googleImageAtSize(thumbnailUrl, targetPx)
                        ?.takeIf { it != thumbnailUrl },
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    showProgress = false,
                    placeholderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            } else {
                Icon(
                    placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * M3 Expressive split button: primary Play action + a spinning menu half
 * with related play actions (shuffle, radio).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaySplitButton(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            androidx.compose.material3.SplitButtonDefaults.LeadingButton(
                onClick = onPlay,
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.cd_play),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        trailingButton = {
            Box {
                androidx.compose.material3.SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    modifier = Modifier.height(56.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (menuOpen) 180f else 0f,
                        label = "playMenuRotation"
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.ar_more_play_options),
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_shuffle)) },
                        leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                        onClick = {
                            menuOpen = false
                            onShuffle()
                        }
                    )
                    if (onStartRadio != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.ar_start_radio)) },
                            leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                            onClick = {
                                menuOpen = false
                                onStartRadio()
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * One streamed release shelf (Albums or Singles & EPs). Type and year when the
 * card carried them, the uploader when it did not, never an empty subtitle.
 */
@Composable
private fun ArtistReleaseRow(
    releases: List<PlaylistDisplayItem>,
    onOpenAlbum: ((PlaylistDisplayItem) -> Unit)?,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = ARTIST_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(releases, key = { it.id }) { release ->
            ArtworkCard(
                title = release.name,
                subtitle = releaseCaption(release.releaseType, release.releaseYear).ifBlank {
                    release.uploaderName.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.label_album)
                },
                thumbnailUrl = release.thumbnailUrl,
                onClick = {
                    // Fetched releases carry a browse id in their URL; the
                    // album detail screen fetches the tracks itself.
                    onOpenAlbum?.invoke(release)
                }
            )
        }
    }
}

/**
 * The artist biography: collapsed to four lines, the whole card toggling it,
 * so a thousand-character bio does not bury the sections below it.
 */
@Composable
private fun ArtistBioCard(
    bio: String,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(bio) { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .padding(20.dp)
        ) {
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (expanded) R.string.action_show_less else R.string.action_show_more
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )
        }
    }
}

/**
 * One "Fans also like" entry: circular photo, name, monthly audience.
 */
@Composable
private fun SimilarArtistCard(
    entry: SimilarArtist,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    Column(
        modifier = Modifier
            .width(128.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarImage(
            url = entry.thumbnailUrl,
            contentDescription = entry.name,
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape),
            showProgress = false
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            entry.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        entry.audienceText?.takeIf { it.isNotBlank() }?.let { audience ->
            Text(
                audience,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The whole discography with its tools: sort, filter, count and paging.
 * A child page over the overview, so the overview can stay the curated top
 * of the artist while nothing the list could do before is lost.
 */
@Composable
private fun ArtistAllSongsPage(
    artistName: String,
    totalCount: Int,
    arrangedSongs: List<Song>,
    displayedSongs: List<Song>,
    isFiltering: Boolean,
    sort: ArtistSongSort,
    onSortChange: (ArtistSongSort) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    hasMoreSongs: Boolean,
    isLoadingMore: Boolean,
    onShowMore: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)?,
    onBack: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = ARTIST_GUTTER, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = onBack,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.cat_songs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1
                )
                Text(
                    artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                // While filtering, the count is the answer to what the field
                // just did - "12 of 148" - and a bare total there would read
                // as the filter not having applied.
                text = if (isFiltering) {
                    stringResource(R.string.ar_matching_count, arrangedSongs.size, totalCount)
                } else {
                    pluralStringResource(R.plurals.n_songs, totalCount, totalCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryTextColor
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 220.dp)
        ) {
            // The controls stand even when the arrangement matches nothing:
            // a filter with no results that also hides the field is a dead
            // end with no way back.
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ArtistSortRow(selected = sort, onSelect = onSortChange)
                // Below the threshold the whole discography is a screen or two
                // of scrolling and a permanent field is chrome.
                if (totalCount >= SEARCH_FIELD_MIN_ITEMS) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.ar_filter_hint),
                        modifier = Modifier.padding(horizontal = ARTIST_GUTTER)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            itemsIndexed(displayedSongs) { index, song ->
                ArtistSongCard(
                    song = song,
                    index = index + 1,
                    onClick = { onSongClick(song) },
                    cardColor = cardColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    primaryColor = primaryColor,
                    shape = if (index == displayedSongs.size - 1 && !hasMoreSongs) {
                        getSegmentedShape(index, displayedSongs.size)
                    } else if (index == 0) {
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    } else {
                        RectangleShape
                    },
                    modifier = Modifier.padding(horizontal = ARTIST_GUTTER),
                    onLongClick = onSongLongPress?.let { press -> { press(song) } }
                )
                if (index < displayedSongs.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 44.dp),
                        color = textColor.copy(alpha = 0.06f)
                    )
                }
            }

            if (hasMoreSongs && displayedSongs.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 44.dp),
                        color = textColor.copy(alpha = 0.06f)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ARTIST_GUTTER)
                            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                            .clickable(enabled = !isLoadingMore, onClick = onShowMore),
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
                                    color = primaryColor
                                )
                            } else {
                                Text(
                                    stringResource(R.string.action_show_more),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = primaryColor
                                )
                            }
                        }
                    }
                }
            }

            if (totalCount > 0 && displayedSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.ar_no_matching_songs),
                            color = secondaryTextColor,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Song card for the full artist song list
 */
@Composable
private fun ArtistSongCard(
    song: Song,
    index: Int,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    primaryColor: Color,
    shape: Shape,
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
                    text = song.title.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = song.album.takeIf { !isUnknownAlbum(it) } ?: stringResource(R.string.unknown_album),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                // Artwork, or the track number when there is none
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        SongArtwork(
                            song = song,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$index",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
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
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
