package com.ivor.ivormusic.ui.channel
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.data.ChannelTabKind
import com.ivor.ivormusic.data.ShortsItem
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.data.VideoQueue
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.video.VideoOptionsSheetHost
import com.ivor.ivormusic.ui.video.VideoPlaylistDetail

/**
 * A creator's page: banner, identity, and every tab the channel actually has.
 *
 * **What makes this a screen rather than the bottom sheet it replaces** is that
 * deciding whether to follow someone is answered by a banner, an about text, an
 * upload cadence and what else they make - and a sheet holding one flat list of
 * uploads can show none of it.
 *
 * Three things about the structure are worth knowing before changing it:
 *
 * - **The tab row is built from the response, not from an enum.** A channel
 *   with no Shorts has no Shorts tab, a musician has "Releases" and a teacher
 *   has "Courses", and none of that needed code. See
 *   [com.ivor.ivormusic.data.ChannelPage].
 * - **One scroller, not one per tab.** The header, the tab row and the content
 *   share a single [LazyVerticalGrid] on a six-column base, so a video spans
 *   six, a playlist three and a Short two. That is what lets the header scroll
 *   away under the tabs instead of the page being a fixed header with a
 *   scrolling well underneath it, and it is why switching tabs keeps the reading
 *   position rather than snapping to the top.
 * - **Opening a playlist is an in-screen child**, layered over this page through
 *   [PredictiveBackStack] so a back gesture previews the channel underneath.
 *   Opening another *channel* is a real navigation, because that is a different
 *   creator and belongs in the back stack.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChannelScreen(
    channelId: String,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onPlayQueue: (VideoQueue) -> Unit,
    onOpenShorts: (List<ShortsItem>, Int) -> Unit,
    onOpenChannel: (String) -> Unit,
    onEnqueueVideo: (VideoItem, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    /**
     * Opens this creator's music-mode artist page. Null in builds or call sites
     * where music mode is not reachable; the row is then simply absent rather
     * than present and inert.
     */
    onOpenMusicArtist: ((channelId: String, name: String) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ChannelViewModel = viewModel()
) {
    LaunchedEffect(channelId) { viewModel.load(channelId) }

    var openPlaylist by remember { mutableStateOf<VideoPlaylist?>(null) }

    PredictiveBackStack(
        childOpen = openPlaylist != null,
        onBack = { openPlaylist = null },
        background = {
            ChannelRoot(
                viewModel = viewModel,
                homeViewModel = homeViewModel,
                onBack = onBack,
                onPlayVideo = onPlayVideo,
                onOpenShorts = onOpenShorts,
                onOpenChannel = onOpenChannel,
                onOpenPlaylist = { playlist ->
                    homeViewModel.loadPlaylistVideos(playlist.playlistId)
                    openPlaylist = playlist
                },
                onEnqueueVideo = onEnqueueVideo,
                onLoginClick = onLoginClick,
                onOpenMusicArtist = onOpenMusicArtist,
                contentPadding = contentPadding
            )
        },
        foreground = {
            openPlaylist?.let { playlist ->
                VideoPlaylistDetail(
                    playlist = playlist,
                    viewModel = homeViewModel,
                    onVideoClick = onPlayVideo,
                    onBack = { openPlaylist = null },
                    contentPadding = contentPadding,
                    // Someone else's playlist; removal is not the viewer's to do.
                    allowRemove = false,
                    onPlayQueue = onPlayQueue,
                    onEnqueueVideo = onEnqueueVideo,
                    onOpenChannel = onOpenChannel
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelRoot(
    viewModel: ChannelViewModel,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onOpenShorts: (List<ShortsItem>, Int) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (VideoPlaylist) -> Unit,
    onEnqueueVideo: (VideoItem, Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onOpenMusicArtist: ((channelId: String, name: String) -> Unit)?,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val header by viewModel.header.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val loadingTabs by viewModel.loadingTabs.collectAsState()
    val isLoadingPage by viewModel.isLoadingPage.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val isSubscribed by viewModel.isSubscribed.collectAsState()
    val isBlocked by viewModel.isBlocked.collectAsState()
    val about by viewModel.about.collectAsState()
    val isAboutLoading by viewModel.isAboutLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchRan by viewModel.searchRan.collectAsState()

    val gridState = rememberLazyGridState()
    var searchMode by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<VideoItem?>(null) }

    // The About panel is a tab in this UI and an engagement panel on YouTube's
    // side, so it is fetched the moment it is opened rather than with the page.
    LaunchedEffect(selectedTab) {
        if (selectedTab == ChannelTabKind.ABOUT) viewModel.loadAbout()
    }

    /**
     * Back leaves search before it leaves the channel, and that step is
     * deliberately not previewable: closing search widens the page in place,
     * so peeling the screen would animate a departure that is not happening.
     */
    BackHandler(enabled = searchMode) {
        searchMode = false
        viewModel.clearSearch()
    }

    // Distance scrolled through the header, which drives both the banner's
    // parallax and the moment the compact title takes over. Read through
    // derivedStateOf so a scroll does not recompose the whole page per frame.
    val headerOffsetPx by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) Float.MAX_VALUE
            else gridState.firstVisibleItemScrollOffset.toFloat()
        }
    }
    val titleCollapsed by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || headerOffsetPx > 220f }
    }

    // Endless paging: ask for the next page while there is still a screenful to
    // scroll, so the grid never visibly stops at the bottom waiting.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, selectedTab, pages) {
        if (nearEnd && !searchMode) viewModel.loadMore(selectedTab)
    }

    optionsTarget?.let { video ->
        VideoOptionsSheetHost(
            video = video,
            viewModel = homeViewModel,
            onDismiss = { optionsTarget = null },
            onEnqueue = { next -> onEnqueueVideo(video, next) },
            // "Don't recommend this channel" is the page you are standing on,
            // and there is a Block row in the overflow menu that says so
            // plainly. Two ways to do the same thing, one of them phrased as if
            // it were about a single video, is worse than one.
            allowBlockChannel = false
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            isLoadingPage -> ChannelLoading()
            loadFailed || header == null -> ChannelUnavailable(onBack = onBack)
            else -> {
                val currentHeader = header!!
                val page = pages[selectedTab]
                val isTabLoading = selectedTab in loadingTabs

                LazyVerticalGrid(
                    columns = GridCells.Fixed(CHANNEL_GRID_COLUMNS),
                    state = gridState,
                    // One gutter for the whole page, so a video card, a
                    // playlist tile and a Short all line up on the same edge
                    // without any of them carrying padding of its own. The two
                    // things that must ignore it - the banner and the tab strip
                    // - bleed back over it.
                    contentPadding = PaddingValues(
                        start = CHANNEL_GUTTER,
                        end = CHANNEL_GUTTER,
                        bottom = contentPadding.calculateBottomPadding() + 120.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                        CreatorHeader(
                            modifier = Modifier.bleedHorizontally(),
                            name = currentHeader.name,
                            avatarUrl = currentHeader.avatarUrl,
                            bannerUrl = currentHeader.bannerUrl,
                            isVerified = currentHeader.isVerified,
                            metadata = creatorMetadata(
                                currentHeader.handle,
                                currentHeader.subscriberCountText,
                                currentHeader.videoCountText
                            ),
                            description = currentHeader.descriptionPreview,
                            onDescriptionClick = {
                                viewModel.selectTab(ChannelTabKind.ABOUT)
                            },
                            scrollOffsetPx = headerOffsetPx,
                            actions = {
                                ChannelHeaderActions(
                                    isSubscribed = isSubscribed,
                                    isBlocked = isBlocked,
                                    canSearch = tabs.any { it.kind == ChannelTabKind.SEARCH },
                                    onSubscribeClick = {
                                        if (viewModel.subscribeNeedsLogin()) onLoginClick()
                                        else viewModel.toggleSubscribe()
                                    },
                                    onShareClick = { shareChannel(context, currentHeader.shareUrl) },
                                    onSearchClick = { searchMode = true },
                                    onBlockClick = { viewModel.toggleBlocked() },
                                    onOpenMusicArtist = onOpenMusicArtist?.let { open ->
                                        { open(currentHeader.channelId, currentHeader.name) }
                                    }
                                )
                            }
                        )
                    }

                    item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                        ChannelTabRow(
                            tabs = tabs,
                            selected = selectedTab,
                            onSelect = viewModel::selectTab,
                            modifier = Modifier.bleedHorizontally()
                        )
                    }

                    channelTabContent(
                        tabKind = selectedTab,
                        page = page,
                        isTabLoading = isTabLoading,
                        about = about,
                        isAboutLoading = isAboutLoading,
                        onPlayVideo = onPlayVideo,
                        onVideoLongPress = { optionsTarget = it },
                        onOpenShorts = onOpenShorts,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenChannel = onOpenChannel,
                        onSelectSort = { viewModel.selectSort(it, selectedTab) }
                    )

                    if (isLoadingMore) {
                        item(key = "loading_more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(modifier = Modifier.size(36.dp))
                            }
                        }
                    }
                }

                // Sits above the grid so the title can fade in over the banner
                // as it scrolls away, rather than pushing the content down.
                ChannelTopBar(
                    title = currentHeader.name,
                    showTitle = titleCollapsed,
                    onBack = onBack
                )

                // Search takes the whole surface. It is a mode rather than a
                // tab because it needs the keyboard and a text field, and
                // burying that behind a tab chip would make the one thing
                // people open a big channel to do the hardest thing to find.
                AnimatedVisibility(
                    visible = searchMode,
                    enter = slideInVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        initialOffsetY = { -it / 6 }
                    ) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it / 6 }) + fadeOut()
                ) {
                    ChannelSearchPane(
                        channelName = currentHeader.name,
                        query = searchQuery,
                        results = searchResults,
                        isSearching = isSearching,
                        searchRan = searchRan,
                        onQueryChange = viewModel::setSearchQuery,
                        onVideoClick = onPlayVideo,
                        onVideoLongPress = { optionsTarget = it },
                        onOpenChannel = onOpenChannel,
                        onClose = {
                            searchMode = false
                            viewModel.clearSearch()
                        },
                        contentPadding = contentPadding
                    )
                }
            }
        }
    }
}

/**
 * The scrollable tab row.
 *
 * A connected [ToggleButton] group like the app's other tab strips, but
 * horizontally scrollable rather than weighted, because the number of tabs is
 * whatever the channel has - eight on a large one - and weighting eight buttons
 * across a phone leaves eight unreadable slivers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelTabRow(
    tabs: List<com.ivor.ivormusic.data.ChannelTab>,
    selected: ChannelTabKind,
    onSelect: (ChannelTabKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    // About is not one of YouTube's tabs - it is a panel behind the header's
    // description - but it belongs in the row, because as far as the reader is
    // concerned it is another thing this channel has.
    val entries = remember(tabs) { tabs + ABOUT_TAB }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = CHANNEL_GUTTER, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        entries.forEachIndexed { index, tab ->
            val isSelected = tab.kind == selected
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelect(tab.kind) },
                modifier = Modifier.height(42.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(
                    text = tab.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * The row of actions under the identity: Subscribe, Share, Search and the
 * overflow.
 *
 * Subscribe is the only one that gets the full width it needs, because it is
 * the decision the page exists to support. Everything else is an icon.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelHeaderActions(
    isSubscribed: Boolean,
    isBlocked: Boolean,
    canSearch: Boolean,
    onSubscribeClick: () -> Unit,
    onShareClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBlockClick: () -> Unit,
    onOpenMusicArtist: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SubscribeButton(
            isSubscribed = isSubscribed,
            onClick = onSubscribeClick,
            modifier = Modifier.weight(1f)
        )
        if (canSearch) {
            ChannelIconAction(
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.ch_search_channel),
                onClick = onSearchClick
            )
        }
        ChannelIconAction(
            icon = Icons.Rounded.Share,
            contentDescription = stringResource(R.string.ch_share_channel),
            onClick = onShareClick
        )
        Box {
            ChannelIconAction(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.cd_more_options),
                onClick = { menuOpen = true }
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                if (onOpenMusicArtist != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ch_open_artist_page)) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onOpenMusicArtist()
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(if (isBlocked) stringResource(R.string.ch_recommend_again) else stringResource(R.string.video_options_dont_recommend_channel))
                    },
                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onBlockClick()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/**
 * Back button always, channel name only once the header has scrolled past.
 *
 * The bar itself stays transparent over the banner and gains a surface as the
 * title arrives, so the artwork is never covered by a strip of solid colour it
 * does not need.
 */
@Composable
private fun ChannelTopBar(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit
) {
    val barAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "channelTopBar"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = barAlpha * 0.94f)
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(60.dp)
            .padding(horizontal = 12.dp)
    ) {
        FilledIconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(44.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                // Legible over artwork while the bar is clear, and settling
                // into the ordinary surface treatment once it is not.
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    .copy(alpha = 0.4f + 0.5f * (1f - barAlpha)),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = barAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp, end = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChannelLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator()
    }
}

/**
 * The channel could not be loaded at all - a deleted or terminated channel, a
 * handle that resolves to nothing, or no connection.
 *
 * Deliberately does not blame the network, because most of the time it is not
 * the network, and telling someone to check a connection that is plainly
 * working sends them fixing the wrong thing.
 */
@Composable
private fun ChannelUnavailable(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ch_unavailable_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ch_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Text(
                text = stringResource(R.string.cd_back),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

private fun shareChannel(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, url)
    runCatching {
        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.ch_share_chooser)))
    }
}

/**
 * Six, so one row divides cleanly into one video, two playlists or three
 * Shorts without any tab needing a scroller of its own.
 */
internal const val CHANNEL_GRID_COLUMNS = 6
