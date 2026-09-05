package com.ivor.ivormusic.ui.home
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.MusicVideoToggleState
import com.ivor.ivormusic.ui.components.rememberMusicVideoToggleState

/**
 * Spotlight: an alternative music Home built the way music apps are actually
 * built, off by default and chosen in onboarding or from Settings.
 *
 * It takes the two ideas that make Spotify's and YouTube Music's homes work and
 * puts them in one screen:
 *
 * **The shortcut grid** (Spotify). Two columns of wide, short tiles at the very
 * top - the six things you reach for most. It is the densest part of any music
 * home and the part that gets used, because it is above the fold and needs no
 * scrolling.
 *
 * **Quick picks** (YouTube Music). A *paged* block of four song rows. This is
 * the one horizontal gesture on the screen that earns its place: paging snaps,
 * so it does not compete with vertical scrolling the way a free-scrolling
 * carousel does, and it fits twelve songs into the height of four.
 *
 * Then artwork shelves, which is what the rest of every music home is, and what
 * the previous attempt at an alternative Home wrongly threw away.
 *
 * **Same data, different composition.** Every value comes from a flow
 * [HomeViewModel] already exposes. No new ViewModel, no new fetches. The moment
 * Spotlight owns data the classic Home does not, there are two Homes to keep
 * working and one quietly falls behind.
 *
 * The chips are honest about that: they scope the page to material that really
 * exists on the device rather than pretending to be YouTube Music's mood chips,
 * which are a browse call this screen deliberately does not make.
 */
@Composable
fun SpotlightHomeContent(
    songs: List<Song>,
    recentlyPlayed: List<Song>,
    likedSongs: List<Song>,
    playlists: List<PlaylistDisplayItem>,
    isInitialLoading: Boolean = false,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onPlaySongs: (List<Song>, Song?) -> Unit,
    onRecentClick: (Song) -> Unit,
    onPlaylistClick: (PlaylistDisplayItem) -> Unit = {},
    onOpenLiked: () -> Unit = {},
    onShowAllInLibrary: () -> Unit = {},
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    excludedFolders: Set<String> = emptySet(),
    manualScan: Boolean = false,
    videoMode: Boolean = false,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode),
    listState: LazyListState = rememberLazyListState(),
) {
    val isRefreshing by viewModel.isLoading.collectAsState()

    // rememberSaveable, not remember: the filter is a place in the screen, and
    // losing it on a tab switch or a rotation is the same annoyance as losing a
    // scroll position.
    var filter by rememberSaveable { mutableStateOf(SpotlightFilter.All) }

    val discoverySongs by viewModel.discoverySongs.collectAsState()
    val discoveryCollections by viewModel.discoveryCollections.collectAsState()
    val isDiscoveryLoading by viewModel.isDiscoveryLoading.collectAsState()

    // Fetched when its tab is opened rather than with the rest of Home: it is
    // several searches plus a radio call, and most sessions never look at it.
    LaunchedEffect(filter) {
        if (filter == SpotlightFilter.Recommended) viewModel.loadDiscovery()
    }

    val quickPicks = remember(filter, songs, likedSongs, recentlyPlayed, discoverySongs) {
        when (filter) {
            SpotlightFilter.Liked -> likedSongs
            SpotlightFilter.Recent -> recentlyPlayed
            SpotlightFilter.Recommended -> discoverySongs
            else -> songs
        }.take(QUICK_PICK_PAGES * QUICK_PICK_ROWS)
    }

    // YouTube Music mints a handful of auto-generated mixes per account and
    // they arrive alongside real playlists. They are not something the user
    // made or saved, they churn on their own, and giving them a slot in a grid
    // of six pushes out things that were actually chosen. Dropped everywhere on
    // this screen rather than only from the grid, so they cannot come back in
    // through a shelf.
    val ownPlaylists = remember(playlists) { playlists.filterNot { isAutoMix(it.name) } }

    val shortcuts = remember(recentlyPlayed, ownPlaylists, likedSongs) {
        buildShortcuts(likedSongs, recentlyPlayed, ownPlaylists)
    }

    ExpressivePullToRefresh(
        // The discovery tab has its own fetch, so its own spinner: pulling on
        // it and watching the library reload would be the gesture doing
        // something other than what the screen is showing.
        isRefreshing = (isRefreshing || isDiscoveryLoading) && !isInitialLoading,
        onRefresh = {
            if (filter == SpotlightFilter.Recommended) {
                viewModel.loadDiscovery(force = true)
            } else {
                viewModel.refresh(excludedFolders, manualScan)
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = contentPadding,
        ) {
            item(key = "topbar") {
                TopBarSection(
                    onProfileClick = onProfileClick,
                    onSettingsClick = onSettingsClick,
                    onDownloadsClick = onDownloadsClick,
                    isDarkMode = isDarkMode,
                    viewModel = viewModel,
                    videoMode = videoMode,
                    onVideoModeToggle = onVideoModeToggle,
                    showModeToggle = showModeToggle,
                    modeToggleState = modeToggleState,
                )
            }

            item(key = "chips") {
                SpotlightFilterChips(selected = filter, onSelect = { filter = it })
            }

            // The shortcut grid only makes sense unfiltered - a two-column grid
            // of "your most reached-for things" is not a thing you scope.
            if (filter == SpotlightFilter.All && shortcuts.isNotEmpty()) {
                item(key = "shortcuts") {
                    SpotlightShortcutGrid(
                        shortcuts = shortcuts,
                        onClick = { shortcut ->
                            when (shortcut) {
                                is Shortcut.Liked -> onOpenLiked()
                                is Shortcut.Playlist -> onPlaylistClick(shortcut.playlist)
                                is Shortcut.Track -> onRecentClick(shortcut.song)
                            }
                        },
                    )
                }
            }

            // First load of the discovery tab: the grid below is empty and
            // will be for a second or two of network, and an empty page reads
            // as "nothing to suggest" rather than "still asking".
            if (filter == SpotlightFilter.Recommended && isDiscoveryLoading && quickPicks.isEmpty()) {
                item(key = "discovery-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
            }

            if (quickPicks.isNotEmpty()) {
                item(key = "quick-header") {
                    SpotlightSectionHeader(
                        title = if (filter == SpotlightFilter.Recommended) {
                            stringResource(R.string.spotlight_new_for_you)
                        } else {
                            stringResource(R.string.spotlight_quick_picks)
                        },
                        subtitle = spotlightQuickPickCaption(filter),
                        actionLabel = stringResource(R.string.action_play_all),
                        onAction = { onPlaySongs(quickPicks, quickPicks.firstOrNull()) },
                    )
                }
                item(key = "quick-picks") {
                    SpotlightQuickPicks(
                        songs = quickPicks,
                        onSongClick = { onPlaySongs(quickPicks, it) },
                        onSongLongPress = onSongLongPress,
                    )
                }
            }

            // The discovery tab's shelves. A grid of songs answers "what do I
            // play next" and nothing else; a record, an hour somebody curated
            // and a whole catalogue are the other three ways people find music,
            // and none of them was reachable from this tab.
            if (filter == SpotlightFilter.Recommended) {
                if (discoveryCollections.albums.isNotEmpty()) {
                    item(key = "discovery-albums-header") {
                        SpotlightSectionHeader(
                            title = stringResource(R.string.spotlight_albums_to_explore),
                            subtitle = stringResource(R.string.spotlight_albums_to_explore_sub),
                        )
                    }
                    item(key = "discovery-albums") {
                        SpotlightShelf(
                            items = discoveryCollections.albums.map { it.toShelfItem() },
                            onClick = { id ->
                                discoveryCollections.albums.find { it.id == id }
                                    ?.let(onPlaylistClick)
                            },
                        )
                    }
                }

                if (discoveryCollections.artists.isNotEmpty()) {
                    item(key = "discovery-artists-header") {
                        SpotlightSectionHeader(
                            title = stringResource(R.string.spotlight_artists_for_you),
                            subtitle = stringResource(R.string.spotlight_artists_for_you_sub),
                        )
                    }
                    item(key = "discovery-artists") {
                        SpotlightArtistRail(
                            artists = discoveryCollections.artists,
                            // The artist page lives in the Library tab, so this
                            // is the hand-off the player and the channel page
                            // already use rather than a route of its own.
                            onClick = { viewModel.requestArtistPage(it.name) },
                        )
                    }
                }

                if (discoveryCollections.playlists.isNotEmpty()) {
                    item(key = "discovery-playlists-header") {
                        SpotlightSectionHeader(
                            title = stringResource(R.string.spotlight_playlists_to_try),
                            subtitle = stringResource(R.string.spotlight_playlists_to_try_sub),
                        )
                    }
                    item(key = "discovery-playlists") {
                        SpotlightShelf(
                            items = discoveryCollections.playlists.map { it.toShelfItem() },
                            onClick = { id ->
                                discoveryCollections.playlists.find { it.id == id }
                                    ?.let(onPlaylistClick)
                            },
                        )
                    }
                }
            }

            if (filter != SpotlightFilter.Playlists &&
                filter != SpotlightFilter.Recommended &&
                recentlyPlayed.isNotEmpty()
            ) {
                item(key = "recent-header") {
                    SpotlightSectionHeader(title = stringResource(R.string.home_section_jump_back_in))
                }
                item(key = "recent-shelf") {
                    SpotlightShelf(
                        items = recentlyPlayed.take(SHELF_ITEMS).map {
                            ShelfItem(
                                it.id,
                                it.title,
                                it.artist,
                                it.albumArtUri?.toString() ?: it.thumbnailUrl,
                                it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                            )
                        },
                        onClick = { id ->
                            recentlyPlayed.find { it.id == id }?.let(onRecentClick)
                        },
                    )
                }
            }

            if (filter == SpotlightFilter.All || filter == SpotlightFilter.Playlists) {
                if (ownPlaylists.isNotEmpty()) {
                    item(key = "playlists-header") {
                        SpotlightSectionHeader(title = stringResource(R.string.spotlight_your_playlists))
                    }
                    item(key = "playlists-shelf") {
                        SpotlightShelf(
                            items = ownPlaylists.take(SHELF_ITEMS).map {
                                ShelfItem(
                                    it.id,
                                    it.name,
                                    if (it.itemCount >= 0) "${it.itemCount} songs" else it.uploaderName,
                                    it.thumbnailUrl,
                                    com.ivor.ivormusic.data.googleImageAtSize(it.thumbnailUrl, 512),
                                )
                            },
                            onClick = { id ->
                                ownPlaylists.find { it.id == id }?.let(onPlaylistClick)
                            },
                        )
                    }
                }
            }

            if (filter == SpotlightFilter.All && songs.isNotEmpty()) {
                item(key = "library-header") {
                    SpotlightSectionHeader(
                        title = stringResource(R.string.spotlight_from_your_library),
                        actionLabel = stringResource(R.string.action_see_all),
                        onAction = onShowAllInLibrary,
                    )
                }
                item(key = "library-shelf") {
                    SpotlightShelf(
                        items = songs.take(SHELF_ITEMS).map {
                            ShelfItem(
                                it.id,
                                it.title,
                                it.artist,
                                it.albumArtUri?.toString() ?: it.thumbnailUrl,
                                it.albumArtUri?.toString() ?: it.highResThumbnailUrl,
                            )
                        },
                        onClick = { id -> songs.find { it.id == id }?.let(onSongClick) },
                    )
                }
            }

            // A filter that turned up nothing is a real state, and a blank page
            // reads as a bug rather than as an answer.
            if (!isInitialLoading && !isDiscoveryLoading && quickPicks.isEmpty() &&
                (filter == SpotlightFilter.Recommended || shortcuts.isEmpty()) &&
                (filter != SpotlightFilter.Recommended || discoveryCollections.isEmpty()) &&
                (filter != SpotlightFilter.Playlists || ownPlaylists.isEmpty())
            ) {
                item(key = "empty") { SpotlightEmptyState(filter) }
            }

            item(key = "tail") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * YouTube Music's own auto-generated mixes, by name.
 *
 * There is no flag on the playlist saying "we made this for you" - the only
 * signal is the title, so this matches the shapes YouTube ships: "My Supermix",
 * "Discover Mix", "New Release Mix", "Replay Mix", and the per-artist
 * "<Artist> Mix" / "My Mix 3" forms. Matched case-insensitively and anchored on
 * the word "mix", so a user's own playlist called "Late night mixtape" is kept.
 */
private val AUTO_MIX_PATTERNS = listOf(
    Regex("""^my\s+supermix$""", RegexOption.IGNORE_CASE),
    Regex("""^supermix$""", RegexOption.IGNORE_CASE),
    Regex("""^my\s+mix(\s+\d+)?$""", RegexOption.IGNORE_CASE),
    Regex("""^(discover|new\s+release|replay)\s+mix$""", RegexOption.IGNORE_CASE),
    Regex("""\bradio$""", RegexOption.IGNORE_CASE),
)

internal fun isAutoMix(name: String): Boolean {
    val trimmed = name.trim()
    return AUTO_MIX_PATTERNS.any { it.containsMatchIn(trimmed) }
}

private const val QUICK_PICK_ROWS = 4
private const val QUICK_PICK_PAGES = 3
private const val SHELF_ITEMS = 12
private const val SHORTCUT_COUNT = 6

/* ------------------------------------------------------------------ */
/* Filters                                                             */
/* ------------------------------------------------------------------ */

/**
 * Deliberately not YouTube Music's mood chips. Those are a browse call, and
 * this screen resolves everything from flows that already exist, so the chips
 * scope what is genuinely on the device instead of implying a catalogue.
 */
internal enum class SpotlightFilter(val label: String, val quickPickCaption: String?) {
    All("All", null),

    /**
     * The one filter that is not a view of what the user already has.
     *
     * It sits second because it is a peer of All rather than a corner of the
     * screen: everything else here scopes the library, and this is the only
     * entry that answers "what should I listen to that I have not heard".
     */
    Recommended("For you", "Not in your library yet"),
    Liked("Liked", "From songs you liked"),
    Recent("Recent", "From what you played lately"),
    Playlists("Playlists", null),
}

/**
 * The filter row, as one connected group rather than five loose chips.
 *
 * These are mutually exclusive views of the same screen, which is a segmented
 * control, not a set of independent toggles - and Material 3 Expressive draws
 * that as connected [ToggleButton]s whose shape morphs on selection, the same
 * component the SponsorBlock category rows and the video quality tabs use.
 * Loose chips read as "pick any" and gave the selected one nothing but a fill.
 *
 * It scrolls rather than dividing the width five ways: "Playlists" and
 * "For you" are not the same length as "All", and equal weights would either
 * clip them or waste half the row.
 */
@Composable
private fun SpotlightFilterChips(
    selected: SpotlightFilter,
    onSelect: (SpotlightFilter) -> Unit,
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val entries = SpotlightFilter.entries.toList()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        entries.forEachIndexed { index, entry ->
            ToggleButton(
                checked = entry == selected,
                onCheckedChange = {
                    if (entry != selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
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
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(spotlightFilterLabel(entry))
            }
        }
    }
}

@Composable
private fun spotlightFilterLabel(filter: SpotlightFilter): String = when (filter) {
    SpotlightFilter.All -> stringResource(R.string.filter_all)
    SpotlightFilter.Recommended -> stringResource(R.string.filter_recommended)
    SpotlightFilter.Liked -> stringResource(R.string.filter_liked)
    SpotlightFilter.Recent -> stringResource(R.string.filter_recent)
    SpotlightFilter.Playlists -> stringResource(R.string.filter_playlists)
}

@Composable
private fun spotlightQuickPickCaption(filter: SpotlightFilter): String? = when (filter) {
    SpotlightFilter.Recommended -> stringResource(R.string.spotlight_new_for_you_caption)
    SpotlightFilter.Liked -> stringResource(R.string.spotlight_caption_liked)
    SpotlightFilter.Recent -> stringResource(R.string.spotlight_caption_recent)
    else -> null
}

/* ------------------------------------------------------------------ */
/* Shortcut grid                                                       */
/* ------------------------------------------------------------------ */

internal sealed interface Shortcut {
    val key: String
    val title: String

    data class Liked(val count: Int) : Shortcut {
        override val key = "liked"
        override val title = "Liked songs"
    }

    data class Playlist(val playlist: PlaylistDisplayItem) : Shortcut {
        override val key = "pl-${playlist.id}"
        override val title = playlist.name
    }

    data class Track(val song: Song) : Shortcut {
        override val key = "tr-${song.id}"
        override val title = song.title
    }
}

/**
 * Liked songs first when there are any - it is the one destination every music
 * app puts in this position - then playlists and recent tracks interleaved, so
 * the grid is not six of the same thing.
 */
private fun buildShortcuts(
    liked: List<Song>,
    recent: List<Song>,
    playlists: List<PlaylistDisplayItem>,
): List<Shortcut> = buildList {
    if (liked.isNotEmpty()) add(Shortcut.Liked(liked.size))
    val tracks = recent.map { Shortcut.Track(it) }
    val lists = playlists.map { Shortcut.Playlist(it) }
    var t = 0
    var l = 0
    while (size < SHORTCUT_COUNT && (t < tracks.size || l < lists.size)) {
        if (l < lists.size) add(lists[l++])
        if (size < SHORTCUT_COUNT && t < tracks.size) add(tracks[t++])
    }
}.take(SHORTCUT_COUNT)

/**
 * Two columns of wide, short tiles. Built from Rows rather than a LazyGrid,
 * because a lazy grid nested in this LazyColumn has unbounded height - the same
 * constraint PlayerStylePicker works around.
 */
@Composable
private fun SpotlightShortcutGrid(
    shortcuts: List<Shortcut>,
    onClick: (Shortcut) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shortcuts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { shortcut ->
                    SpotlightShortcutTile(
                        shortcut = shortcut,
                        onClick = { onClick(shortcut) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // An odd count must not stretch the last tile across the row.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SpotlightShortcutTile(
    shortcut: Shortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(60.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (shortcut) {
                    is Shortcut.Liked -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    is Shortcut.Playlist -> ShortcutImage(
                        shortcut.playlist.thumbnailUrl,
                        com.ivor.ivormusic.data.googleImageAtSize(shortcut.playlist.thumbnailUrl, 512),
                    )
                    is Shortcut.Track -> ShortcutImage(
                        shortcut.song.albumArtUri?.toString() ?: shortcut.song.thumbnailUrl,
                        shortcut.song.albumArtUri?.toString() ?: shortcut.song.highResThumbnailUrl,
                    )
                }
            }
            Text(
                text = if (shortcut is Shortcut.Liked) stringResource(R.string.shortcut_liked_songs) else shortcut.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

/**
 * Artwork for a shortcut tile or a shelf card.
 *
 * Goes through [VideoThumbnail] rather than a bare `AsyncImage` for the same
 * reasons the feed cards do: the sharper URL is layered over the one the feed
 * returned so a 404 upstairs is invisible, a dropped fetch is retried twice
 * instead of leaving a permanently blank card, and the Material 3 loading
 * indicator is held off long enough that a memory-cache hit never flashes one
 * during a fling.
 */
@Composable
private fun ShortcutImage(model: String?, highRes: String? = null) {
    com.ivor.ivormusic.ui.components.VideoThumbnail(
        thumbnailUrl = model,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        highResThumbnailUrl = highRes,
        contentScale = ContentScale.Crop,
        indicatorSize = 24.dp,
        emptyIcon = Icons.AutoMirrored.Rounded.QueueMusic,
        placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

/* ------------------------------------------------------------------ */
/* Quick picks                                                         */
/* ------------------------------------------------------------------ */

/**
 * Four song rows per page, paged horizontally.
 *
 * Paging rather than free scrolling is the whole point: it snaps, so the
 * gesture resolves to one page or the next instead of leaving the reader
 * halfway between two, and it never steals a vertical drag the way a
 * free-scrolling carousel nested in a scrolling page does.
 */
@Composable
private fun SpotlightQuickPicks(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
) {
    val pages = songs.chunked(QUICK_PICK_ROWS)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 8.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pages[page].forEach { song ->
                    SpotlightQuickPickRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onLongClick = onSongLongPress?.let { press -> { press(song) } }
                    )
                }
            }
        }

        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightQuickPickRow(
    song: Song,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .songRowClick(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            // A quick-pick row draws at 48dp, so the feed's own thumbnail is
            // already enough: asking for 1080 here would fetch a megabyte to
            // fill a thumbnail and evict the covers that do need it.
            ShortcutImage(song.albumArtUri?.toString() ?: song.thumbnailUrl)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shelves                                                             */
/* ------------------------------------------------------------------ */

internal data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    /**
     * The same picture asked for at a size worth drawing. Every shelf card is
     * 140dp - 420 physical pixels on a 3x screen - and a feed thumbnail
     * arrives at 120px or less, so the low one alone is being blown up three
     * to four times. Drawn over [artwork] rather than instead of it, because
     * `maxresdefault` does not exist for every video and a channel may have no
     * large avatar: a miss then costs sharpness, not an empty card.
     */
    val artworkHighRes: String? = null,
)

/**
 * The artwork row every music home is mostly made of. Cards are large enough
 * that the art is the content rather than a thumbnail, which is the difference
 * between a shelf and a list with pictures.
 */
@Composable
private fun SpotlightShelf(items: List<ShelfItem>, onClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onClick(item.id) }
                    .padding(bottom = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    ShortcutImage(item.artwork, item.artworkHighRes)
                }
                Column(
                    modifier = Modifier.padding(
                        start = 8.dp,
                        top = 8.dp,
                        end = 8.dp,
                        bottom = 10.dp,
                    ),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * A playlist or album as a shelf card.
 *
 * The high-res field is the point: an InnerTube search hands back covers at
 * whatever size the surface it was built for wanted - often 120px - and a shelf
 * card is 140dp, which is 420 physical pixels on a 3x screen. Asking for the
 * size actually being drawn is what separates a cover from a blur.
 */
private fun PlaylistDisplayItem.toShelfItem(): ShelfItem = ShelfItem(
    id = id,
    title = name,
    subtitle = when {
        uploaderName.isNotBlank() -> uploaderName
        itemCount >= 0 -> "$itemCount songs"
        else -> ""
    },
    artwork = thumbnailUrl,
    artworkHighRes = com.ivor.ivormusic.data.googleImageAtSize(thumbnailUrl, 512),
)

/**
 * Artists as a rail of circles.
 *
 * Round rather than the square shelf card above, and that is the whole reason
 * it is a separate composable: a person is drawn as a circle everywhere else in
 * this app - the account avatar, the channel page, the artist page it opens -
 * and a square card of a face in a row of square records reads as another
 * album. The name sits centred under the circle with the subscriber count
 * beneath it, so the rail scans as "people" at a glance.
 */
@Composable
private fun SpotlightArtistRail(
    artists: List<com.ivor.ivormusic.data.ArtistItem>,
    onClick: (com.ivor.ivormusic.data.ArtistItem) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(artists, key = { it.id }) { artist ->
            Column(
                modifier = Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onClick(artist) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    com.ivor.ivormusic.ui.components.AvatarImage(
                        url = artist.thumbnailUrl,
                        contentDescription = null,
                        targetPx = 288,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                artist.subscriberCount?.takeIf { it.isNotBlank() }?.let { subscribers ->
                    Text(
                        text = subscribers,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Chrome                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun SpotlightSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Surface(
                onClick = onAction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightEmptyState(filter: SpotlightFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = when (filter) {
                SpotlightFilter.Liked -> Icons.Rounded.Favorite
                SpotlightFilter.Recommended -> Icons.Rounded.AutoAwesome
                else -> Icons.Rounded.History
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (filter) {
                SpotlightFilter.Liked -> stringResource(R.string.spotlight_empty_no_liked)
                SpotlightFilter.Recent -> stringResource(R.string.spotlight_empty_no_recent)
                SpotlightFilter.Playlists -> stringResource(R.string.spotlight_empty_no_playlists)
                SpotlightFilter.Recommended -> stringResource(R.string.spotlight_discovery_empty)
                SpotlightFilter.All -> stringResource(R.string.spotlight_empty_generic)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (filter) {
                SpotlightFilter.Liked -> stringResource(R.string.spotlight_hint_liked)
                SpotlightFilter.Playlists -> stringResource(R.string.spotlight_hint_playlists)
                SpotlightFilter.Recommended -> stringResource(R.string.spotlight_discovery_empty_sub)
                else -> stringResource(R.string.spotlight_hint_all)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
