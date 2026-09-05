package com.ivor.ivormusic.ui.artist
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.sortedInAlbumOrder
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.ui.components.SEARCH_FIELD_MIN_ITEMS
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.ui.channel.CreatorHeader
import com.ivor.ivormusic.ui.channel.creatorMetadata
import com.ivor.ivormusic.ui.home.HomeViewModel
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
            compareBy<Song> { it.album.isBlank() || it.album.startsWith("Unknown") }
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
     * Open this creator's video-mode channel page.
     *
     * The other half of the cross-link on the channel screen. The two stay
     * separate surfaces because a discography and an upload feed are genuinely
     * different content, but they share [CreatorHeader] and this link, so
     * arriving at a musician from either mode does not feel like arriving at
     * two different people.
     */
    onOpenChannel: ((channelId: String) -> Unit)? = null
) {
    // Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    
    // State for fetched songs
    var artistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var fetchedAlbums by remember { mutableStateOf<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMoreRemote by remember { mutableStateOf(true) }
    var visibleSongCount by remember { mutableIntStateOf(20) }
    // Both survive rotation but not a change of artist, which is why they are
    // keyed on the name below rather than only declared here.
    var songSort by remember(artistName) { mutableStateOf(ArtistSongSort.Suggested) }
    var songQuery by remember(artistName) { mutableStateOf("") }
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

        // Only genuinely local files take the offline path. Liked/downloaded
        // YouTube songs shouldn't block fetching the full artist page.
        val localArtistSongs = songs.filter {
            it.artist.equals(artistName, ignoreCase = true) &&
                    it.source == com.ivor.ivormusic.data.SongSource.LOCAL
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
                val (fetchedSongs, fetchedAlbumsList) = viewModel.getArtistDetails(resolvedId)
                artistSongs = fetchedSongs
                fetchedAlbums = fetchedAlbumsList
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
    
    // The identity half of the header, from the same channel browse the video
    // mode page uses. One request, and the only reason this screen makes it:
    // without it a musician has a banner and a verified tick on one side of the
    // app and neither on the other.
    LaunchedEffect(resolvedChannelId) {
        val id = resolvedChannelId ?: return@LaunchedEffect
        channelHeader = viewModel?.getChannelHeader(id)
    }

    // Get unique albums (Local + Fetched)
    val albums = remember(artistSongs, hasLocalSongs, fetchedAlbums) {
        if (hasLocalSongs) {
            artistSongs.groupBy { it.album }.keys.toList()
        } else {
            // Use fetched albums if available
            fetchedAlbums.map { it.name }
        }
    }
    
    // Sample thumbnails for the hero section (up to 4)
    val sampleThumbnails = remember(artistSongs) {
        artistSongs.take(4).mapNotNull { it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() }
    }
    
    // The list as the user has arranged it. Everything below reads this rather
    // than artistSongs: the rows, the counts, the pagination budget and what a
    // tap enqueues all have to be the same list, or tapping the third row
    // plays something else.
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
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
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
                modifier = Modifier.fillMaxSize(),
                // Extra clearance so the last row ("Show More") sits above
                // the mini player + floating nav bar + system inset.
                contentPadding = PaddingValues(bottom = 220.dp)
            ) {
                // ========== HERO HEADER ==========
                // The same CreatorHeader the channel page uses. What differs is
                // the actions slot: a discography has Play, Shuffle and Radio to
                // offer, which an upload feed does not, and the header is built
                // to take exactly that difference.
                item {
                    val radioSeed = artistSongs.firstOrNull {
                        it.source == com.ivor.ivormusic.data.SongSource.YOUTUBE
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CreatorHeader(
                            name = artistName.takeIf { !it.startsWith("Unknown") }
                                ?: stringResource(R.string.unknown_artist),
                            // The channel avatar when there is one, and the
                            // artwork of what they made when there is not -
                            // which is every local-library artist.
                            avatarUrl = channelHeader?.avatarUrl
                                ?: sampleThumbnails.firstOrNull(),
                            bannerUrl = channelHeader?.bannerUrl,
                            isVerified = channelHeader?.isVerified == true,
                            metadata = creatorMetadata(
                                channelHeader?.subscriberCountText,
                                "${artistSongs.size} songs",
                                albums.size.takeIf { it > 0 }?.let { "$it albums" }
                            ),
                            actions = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PlaySplitButton(
                                        onPlay = {
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayQueue(artistSongs, null)
                                            }
                                        },
                                        onShuffle = {
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayQueue(artistSongs.shuffled(), null)
                                            }
                                        },
                                        onStartRadio = if (radioSeed != null && viewModel != null) {
                                            {
                                                scope.launch {
                                                    val radio = viewModel.getRadioSongs(radioSeed.id)
                                                    if (radio.isNotEmpty()) {
                                                        onPlayQueue(listOf(radioSeed) + radio, radioSeed)
                                                    }
                                                }
                                            }
                                        } else null
                                    )
                                    Spacer(Modifier.weight(1f))
                                    // Only offered once a real channel id has
                                    // resolved. A local-library artist is a tag
                                    // on a file and has no channel to open.
                                    val channelId = resolvedChannelId
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
                        )

                        // Over the banner rather than above it, so the artwork
                        // starts at the top of the screen the way it does on
                        // the channel page.
                        FilledIconButton(
                            onClick = onBack,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    .copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(start = 12.dp, top = 8.dp)
                                .size(44.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                
                // ========== ALBUMS SECTION (Local songs only) ==========
                if (albums.isNotEmpty() && albums.any { it.isNotBlank() && !it.startsWith("Unknown") }) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            stringResource(R.string.section_albums),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val validAlbums = albums.filter { it.isNotBlank() && !it.startsWith("Unknown") }
                            items(validAlbums.size) { index ->
                                val albumName = validAlbums[index]
                                val albumSongs = if (hasLocalSongs) {
                                    artistSongs.filter { it.album == albumName }.sortedInAlbumOrder()
                                } else emptyList()
                                
                                val fetchedAlbum = fetchedAlbums.find { it.name == albumName }
                                val albumSubtitle = if (hasLocalSongs) {
                                    "${albumSongs.size} songs"
                                } else {
                                    fetchedAlbum?.uploaderName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.label_playlist)
                                }
                                val thumbnailUrl = if (hasLocalSongs) {
                                    albumSongs.firstOrNull()?.let { it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() }
                                } else {
                                    fetchedAlbum?.thumbnailUrl
                                }
                                
                                AlbumCard(
                                    albumName = albumName,
                                    subtitle = albumSubtitle,
                                    thumbnailUrl = thumbnailUrl,
                                    primaryColor = primaryColor,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onClick = {
                                        if (hasLocalSongs) {
                                            onAlbumClick?.invoke(albumName, albumSongs) ?: onPlayQueue(albumSongs, null)
                                        } else if (fetchedAlbum != null) {
                                            // Fetched albums carry a browse id in their URL;
                                            // the album detail screen fetches the tracks itself.
                                            onOpenAlbum?.invoke(fetchedAlbum)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // ========== SONGS SECTION ==========
                // The header stands even when the arrangement matches nothing,
                // because the controls that produced that state live in it: a
                // filter with no results that also hides the field is a dead
                // end with no way back.
                if (artistSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.cat_songs),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                // While filtering, the count is the answer to
                                // what the field just did - "12 of 148" - and
                                // a bare total there would read as the filter
                                // not having applied.
                                text = if (isFiltering) {
                                    stringResource(
                                        R.string.ar_matching_count,
                                        arrangedSongs.size,
                                        artistSongs.size
                                    )
                                } else {
                                    "${artistSongs.size} tracks"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        ArtistSortRow(
                            selected = songSort,
                            onSelect = {
                                songSort = it
                                // A re-sort re-numbers the list, so the twenty
                                // rows on screen are a different twenty; paging
                                // back to the top of the budget keeps "show
                                // more" meaning the same thing it did before.
                                visibleSongCount = 20
                            }
                        )
                        // Below the threshold the whole discography is a screen
                        // or two of scrolling and a permanent field is chrome.
                        if (artistSongs.size >= SEARCH_FIELD_MIN_ITEMS) {
                            Spacer(modifier = Modifier.height(12.dp))
                            SearchField(
                                query = songQuery,
                                onQueryChange = {
                                    songQuery = it
                                    visibleSongCount = 20
                                },
                                placeholder = stringResource(R.string.ar_filter_hint),
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    // Song list with segmented card design
                    itemsIndexed(displayedSongs) { index, song ->
                        ArtistSongCard(
                            song = song,
                            index = index + 1,
                            // The arranged list, not the raw one: the queue
                            // has to be what is on screen, or playing the third
                            // row under an A-Z sort continues into the songs
                            // that follow it in popularity order instead.
                            onClick = { onPlayQueue(arrangedSongs, song) },
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
                            modifier = Modifier.padding(horizontal = 20.dp),
                            onLongClick = onSongLongPress?.let { press -> { press(song) } }
                        )
                        if (index < displayedSongs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    // Show More button
                    if (hasMoreSongs) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                                    .clickable(enabled = !isLoadingMore) {
                                        if (artistSongs.size > visibleSongCount) {
                                            // Show more from existing list
                                            visibleSongCount += 20
                                        } else if (viewModel != null && !hasLocalSongs) {
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
                                    },
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
                }

                // Two different nothings: a filter that matched none of a real
                // discography, and an artist with no tracks at all.
                if (artistSongs.isNotEmpty() && displayedSongs.isEmpty()) {
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
                } else if (artistSongs.isEmpty() && !isLoading) {
                    item {
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
 * Album Card for horizontal scroll
 */
@Composable
private fun AlbumCard(
    albumName: String,
    subtitle: String,
    thumbnailUrl: String?,
    primaryColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Album art
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                shape = RoundedCornerShape(14.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = albumName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = primaryColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                albumName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
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

/**
 * Song card for artist song list
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
                    text = song.album.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: stringResource(R.string.unknown_album),
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                // Track number or thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
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
