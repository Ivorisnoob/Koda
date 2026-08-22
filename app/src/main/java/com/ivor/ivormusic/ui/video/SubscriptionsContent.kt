package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.SubscribedChannel
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.SubscriptionGroup
import com.ivor.ivormusic.ui.components.ChannelRowSkeleton
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.ui.components.SEARCH_FIELD_MIN_ITEMS
import com.ivor.ivormusic.ui.components.SearchEmptyState
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.ui.components.SkeletonList
import com.ivor.ivormusic.ui.components.VideoCardSkeleton
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.util.MatchField
import com.ivor.ivormusic.util.fuzzyScore

private enum class SubscriptionFeedPeriod(val label: String, val ageMs: Long?) {
    ALL("Any time", null),
    TODAY("Today", 24L * 60L * 60L * 1000L),
    THIS_WEEK("This week", 7L * 24L * 60L * 60L * 1000L)
}

private enum class SubscriptionFeedOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first")
}

/**
 * Subscriptions tab for Video Mode. Default view is the subscriptions feed
 * (latest uploads across all subscribed channels) topped by a horizontal
 * rail of channel avatars. The rail is a feed filter: tapping a creator shows
 * only their uploads, while the leading All item clears the selection.
 *
 * The feed no longer implies a Google account. Channels followed on this
 * device sit in the same list as the account's, so the sign-in wall only
 * appears when there is genuinely nothing to show - and even then it offers
 * importing a list as an equal alternative to signing in, because for
 * somebody arriving from NewPipe or PipePipe it is the better one.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun SubscriptionsContent(
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onEnqueueVideo: ((VideoItem, Boolean) -> Unit)? = null,
    onLoginClick: () -> Unit,
    /** Open a creator's page: from a channel row, or from the long-press sheet. */
    onOpenChannel: ((String) -> Unit)? = null,
    contentPadding: PaddingValues,
    onManageSubscriptions: () -> Unit = {},
    /**
     * Hoisted by HomeScreen for the tab's root feed only. The channel list
     * keeps its own state: it is reached deliberately and popped with Back, so
     * the tab button scrolling the feed underneath it would act on a list the
     * user cannot see.
     */
    feedListState: LazyListState = rememberLazyListState()
) {
    val feed by viewModel.subscriptionFeed.collectAsState()
    val isFeedLoading by viewModel.isSubscriptionFeedLoading.collectAsState()
    val feedProgress by viewModel.subscriptionFeedProgress.collectAsState()
    val feedError by viewModel.subscriptionFeedError.collectAsState()
    val channels by viewModel.subscribedChannels.collectAsState()
    val isChannelsLoading by viewModel.isSubscriptionsLoading.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val localSubscriptions by viewModel.localSubscriptions.collectAsState()
    val groups by viewModel.subscriptionGroups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.background

    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var feedPeriodName by rememberSaveable { mutableStateOf(SubscriptionFeedPeriod.ALL.name) }
    var feedOrderName by rememberSaveable { mutableStateOf(SubscriptionFeedOrder.NEWEST.name) }
    val feedPeriod = remember(feedPeriodName) {
        SubscriptionFeedPeriod.entries.firstOrNull { it.name == feedPeriodName } ?: SubscriptionFeedPeriod.ALL
    }
    val feedOrder = remember(feedOrderName) {
        SubscriptionFeedOrder.entries.firstOrNull { it.name == feedOrderName } ?: SubscriptionFeedOrder.NEWEST
    }
    var showKindMenu by remember { mutableStateOf(false) }
    var showOrderMenu by remember { mutableStateOf(false) }

    val visibleFeed = remember(feed, selectedChannelId, feedPeriod, feedOrder) {
        val selectedChannel = channels.firstOrNull { it.channelId == selectedChannelId }
        val now = System.currentTimeMillis()
        feed.asSequence()
            .filter { video ->
                selectedChannel == null || video.channelId == selectedChannel.channelId ||
                    video.channelName.equals(selectedChannel.name, ignoreCase = true)
            }
            .filter { video ->
                feedPeriod.ageMs == null ||
                    (video.publishedAtMs ?: VideoItem.parseRelativeTime(video.uploadedDate, now))
                        ?.let { now - it <= feedPeriod.ageMs } == true
            }
            .sortedWith(compareBy<VideoItem> {
                it.publishedAtMs ?: VideoItem.parseRelativeTime(it.uploadedDate) ?: Long.MIN_VALUE
            }.let { if (feedOrder == SubscriptionFeedOrder.NEWEST) it.reversed() else it })
            .toList()
    }

    LaunchedEffect(channels, selectedChannelId) {
        if (selectedChannelId != null && channels.none { it.channelId == selectedChannelId }) {
            selectedChannelId = null
        }
    }

    val locallyFollowedIds = remember(localSubscriptions) {
        localSubscriptions.map { it.channelId }.toSet()
    }

    // Confirmation before dropping a channel, because the gesture that opens
    // it (a long-press on an avatar) is easy to trigger by accident while
    // scrolling the rail.
    var channelToUnfollow by remember { mutableStateOf<SubscribedChannel?>(null) }

    // Options sheet (long-press on a video card), mirroring the video home feed
    // so a long-press means the same thing wherever a video card appears.
    var saveTargetVideo by remember { mutableStateOf<VideoItem?>(null) }

    fun onVideoLongPress(video: VideoItem) {
        saveTargetVideo = video
    }

    // Internal navigation: feed -> channel list. A channel itself is no longer
    // a drill-in here: it is the full channel screen, on its own route, which
    // is what makes tapping an avatar in this rail land in the same place as
    // tapping a creator's name in the player or in search.
    // Saveable for the same reason the tab index is: opening a channel from
    // this list navigates away, and coming back to the feed instead of to the
    // list you were reading is a step the user did not ask for.
    var showChannelList by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(false)
    }

    /**
     * Filter over the channel list. Held out here rather than inside the branch,
     * and saveable, so it survives opening a channel: coming back to a list that
     * had silently reset itself is the worse of the two behaviours, and
     * comparing two channels is exactly what the filter was opened for.
     */
    var channelQuery by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf("")
    }
    val focusManager = LocalFocusManager.current

    // Name first, @handle second - both sources carry a handle, so typing one
    // finds account and device-local channels alike. Ranked by score while a
    // query is running; the underlying list is already alphabetical without one.
    val matchedChannels = remember(channels, channelQuery) {
        if (channelQuery.isBlank()) {
            channels
        } else {
            channels
                .mapNotNull { channel ->
                    fuzzyScore(
                        channelQuery,
                        MatchField(channel.name, weight = 3),
                        MatchField(channel.handle.orEmpty(), weight = 2)
                    )?.let { channel to it }
                }
                .sortedByDescending { it.second }
                .map { it.first }
        }
    }

    // Re-runs when the account connects or the local list changes size, so
    // subscribing to a first channel while signed out fills the tab straight
    // away instead of leaving the empty state up until a manual refresh.
    LaunchedEffect(isYouTubeConnected, localSubscriptions.size) {
        viewModel.loadSubscriptionFeed(force = localSubscriptions.isNotEmpty() && feed.isEmpty())
        viewModel.loadSubscriptions()
    }

    // Back unwinds: a running query, then the channel list. The query step is
    // deliberately not previewed - nothing leaves the screen for it, the list
    // simply widens again.
    val subscriptionsBack = {
        when {
            channelQuery.isNotBlank() -> channelQuery = ""
            else -> showChannelList = false
        }
    }
    val subscriptionsChildOpen = showChannelList
    val subscriptionsPreviewable = showChannelList && channelQuery.isBlank()

    // Animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // Nothing followed anywhere is the only case that still blocks the tab -
    // and it offers importing as well as signing in.
    if (!isYouTubeConnected && localSubscriptions.isEmpty()) {
        SubscriptionsEmptyWall(
            onLoginClick = onLoginClick,
            onImportClick = onManageSubscriptions,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.statusBars)
        )
        return
    }

    // Declared outside the `when` below so the sheets survive a drill-in or
    // back-out while one is open.
    saveTargetVideo?.let { video ->
        VideoOptionsSheetHost(
            video = video,
            viewModel = viewModel,
            onDismiss = { saveTargetVideo = null },
            onEnqueue = onEnqueueVideo?.let { enqueue -> { next -> enqueue(video, next) } },
            // No channel block offered here: hiding a channel the user
            // deliberately follows, from the feed that exists to show it, is a
            // contradiction. Unfollowing is the tool for that, and it is one
            // tap away in the same tab.
            allowBlockChannel = false,
            onOpenChannel = onOpenChannel
        )
    }

    channelToUnfollow?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToUnfollow = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            title = { Text("Unfollow ${channel.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Removes it from this device. Your YouTube account isn't touched."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsubscribeLocally(channel.channelId)
                    channelToUnfollow = null
                }) { Text("Unfollow") }
            },
            dismissButton = {
                TextButton(onClick = { channelToUnfollow = null }) { Text("Cancel") }
            }
        )
    }

    PredictiveBackStack(
        childOpen = subscriptionsChildOpen,
        onBack = subscriptionsBack,
        previewable = subscriptionsPreviewable,
        background = {
            ExpressivePullToRefresh(
                // As above: the pull spinner covers refreshes over existing
                // videos, the skeleton covers the empty first load.
                isRefreshing = isFeedLoading && feed.isNotEmpty(),
                onRefresh = {
                    viewModel.loadSubscriptionFeed(force = true)
                    viewModel.loadSubscriptions(force = true)
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = feedListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn() + slideInVertically(
                                initialOffsetY = { -40 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 8.dp)
                                    .padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Subscriptions",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subscriptionsSubtitle(
                                            channelCount = channels.size,
                                            localCount = localSubscriptions.size,
                                            isConnected = isYouTubeConnected
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = onManageSubscriptions) {
                                    Icon(
                                        imageVector = Icons.Rounded.Settings,
                                        contentDescription = "Manage subscriptions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Group filter. Only shown once groups exist, so a user who
                    // has never made one never sees a row of chrome that does
                    // nothing.
                    if (groups.isNotEmpty()) {
                        item(key = "group-filter") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item(key = "group-all") {
                                    FilterChip(
                                        selected = selectedGroupId == null,
                                        onClick = { viewModel.selectSubscriptionGroup(null) },
                                        label = { Text("All") },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                items(groups, key = { it.id }) { group ->
                                    FilterChip(
                                        selected = selectedGroupId == group.id,
                                        onClick = {
                                            viewModel.selectSubscriptionGroup(
                                                if (selectedGroupId == group.id) null else group.id
                                            )
                                        },
                                        label = { Text(group.name) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // "42 of 130 channels" during a device-local refresh. That
                    // refresh is one request per channel, so on a large list an
                    // indeterminate spinner reads as a hang.
                    feedProgress?.let { (done, total) ->
                        item(key = "feed-progress") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    text = "Checking $done of $total channels",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearWavyProgressIndicator(
                                    progress = { if (total > 0) done.toFloat() / total else 0f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Creator rail: selection filters the feed in place instead
                    // of navigating away from the uploads the user came to see.
                    if (channels.isNotEmpty()) {
                        item(key = "channel-filter") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item(key = "all-channels") {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { selectedChannelId = null }
                                            .padding(4.dp)
                                            .width(64.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(56.dp),
                                            shape = CircleShape,
                                            color = if (selectedChannelId == null) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                                            border = if (selectedChannelId == null) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                            else null
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Subscriptions,
                                                    contentDescription = "All channels",
                                                    tint = if (selectedChannelId == null) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "All",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selectedChannelId == null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                items(channels, key = { it.channelId }) { channel ->
                                    val selected = selectedChannelId == channel.channelId
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                selectedChannelId = if (selected) null else channel.channelId
                                            }
                                            .padding(4.dp)
                                            .width(64.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            ChannelAvatar(channel = channel, size = 56.dp)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = channel.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "feed-controls") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box {
                                    FilterChip(
                                        selected = feedPeriod != SubscriptionFeedPeriod.ALL,
                                        onClick = { showKindMenu = true },
                                        leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(18.dp)) },
                                        label = { Text(feedPeriod.label) }
                                    )
                                    DropdownMenu(expanded = showKindMenu, onDismissRequest = { showKindMenu = false }) {
                                        SubscriptionFeedPeriod.entries.forEach { period ->
                                            DropdownMenuItem(
                                                text = { Text(period.label) },
                                                onClick = { feedPeriodName = period.name; showKindMenu = false }
                                            )
                                        }
                                    }
                                }
                                Box {
                                    FilterChip(
                                        selected = feedOrder != SubscriptionFeedOrder.NEWEST,
                                        onClick = { showOrderMenu = true },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                                        label = { Text(feedOrder.label) }
                                    )
                                    DropdownMenu(expanded = showOrderMenu, onDismissRequest = { showOrderMenu = false }) {
                                        SubscriptionFeedOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(order.label) },
                                                onClick = { feedOrderName = order.name; showOrderMenu = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isFeedLoading && feed.isEmpty()) {
                        item {
                            SkeletonList(
                                count = 3,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) { alpha -> VideoCardSkeleton(alpha = alpha) }
                        }
                    } else if (feed.isEmpty()) {
                        item {
                            FeedEmptyState(
                                error = feedError,
                                hasChannels = channels.isNotEmpty(),
                                isGroupFiltered = selectedGroupId != null,
                                onRetry = { viewModel.loadSubscriptionFeed(force = true) },
                                onClearGroup = { viewModel.selectSubscriptionGroup(null) },
                                onManage = onManageSubscriptions
                            )
                        }
                    } else if (visibleFeed.isEmpty()) {
                        item(key = "filtered-feed-empty") {
                            FeedEmptyState(
                                error = "No uploads match these filters.",
                                hasChannels = true,
                                isGroupFiltered = selectedGroupId != null,
                                onRetry = {
                                    selectedChannelId = null
                                    feedPeriodName = SubscriptionFeedPeriod.ALL.name
                                },
                                onClearGroup = { viewModel.selectSubscriptionGroup(null) },
                                onManage = onManageSubscriptions
                            )
                        }
                    } else {
                        items(visibleFeed, key = { it.videoId }) { video ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onLongClick = { onVideoLongPress(video) },
                                onOpenChannel = onOpenChannel,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    ) { _ ->
    when {
        // Full channel list
        showChannelList -> {
            ExpressivePullToRefresh(
                // The pull spinner only ever means "refreshing what is already
                // on screen". First load is the skeleton's job below, and
                // driving both off the same flag runs two indicators at once.
                isRefreshing = isChannelsLoading && channels.isNotEmpty(),
                onRefresh = { viewModel.loadSubscriptions(force = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                showChannelList = false
                                channelQuery = ""
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Column {
                                Text(
                                    text = "All channels",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    // While filtering, the subtitle answers the
                                    // question the filter asked instead of
                                    // repeating a total the list no longer shows.
                                    text = if (channelQuery.isBlank()) {
                                        subscriptionsSubtitle(
                                            channelCount = channels.size,
                                            localCount = localSubscriptions.size,
                                            isConnected = isYouTubeConnected
                                        )
                                    } else {
                                        "${matchedChannels.size} of ${channels.size} channels"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Only once the list is long enough to be worth searching,
                    // and never while it is still loading: a field over a
                    // skeleton is a control that cannot do anything yet.
                    if (channels.size >= SEARCH_FIELD_MIN_ITEMS) {
                        item(key = "channel-search") {
                            SearchField(
                                query = channelQuery,
                                onQueryChange = { channelQuery = it },
                                placeholder = "Search channels",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    if (isChannelsLoading && channels.isEmpty()) {
                        item {
                            // A list of rows is a known shape, so it gets
                            // placeholders rather than a spinner: nothing jumps
                            // when the channels land.
                            SkeletonList(
                                count = 7,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                spacing = 8.dp
                            ) { alpha -> ChannelRowSkeleton(alpha = alpha) }
                        }
                    } else if (channels.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No subscriptions found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (matchedChannels.isEmpty()) {
                        item(key = "no-channel-matches") {
                            SearchEmptyState(
                                title = "No channels match \"$channelQuery\"",
                                hint = "Try part of the name, or the @handle."
                            )
                        }
                    } else {
                        items(matchedChannels, key = { it.channelId }) { channel ->
                            ChannelRow(
                                channel = channel,
                                isLocal = channel.channelId in locallyFollowedIds,
                                onClick = {
                                    // The keyboard outlives the field it was
                                    // opened from, and it would sit over the
                                    // channel page this is about to open.
                                    focusManager.clearFocus()
                                    onOpenChannel?.invoke(channel.channelId)
                                },
                                onUnfollow = { channelToUnfollow = channel },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }

        // Default: subscriptions feed with the channel avatar rail
        // The feed lives underneath now; this layer is empty over it,
        // and full size so every state measures the same.
        else -> Spacer(Modifier.fillMaxSize())
    }
    }
}

/**
 * Shown only when nothing is followed anywhere.
 *
 * Signing in and importing a list are offered as peers rather than one being
 * the fallback: for someone coming from NewPipe or PipePipe with a
 * subscriptions.json in Downloads, importing is the shorter path to a working
 * feed and does not involve a Google account at all.
 */
@Composable
private fun SubscriptionsEmptyWall(
    onLoginClick: () -> Unit,
    onImportClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Subscriptions,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Subscriptions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Follow channels on this device, or sign in to bring your " +
                    "YouTube subscriptions across.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Import subscriptions")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Rounded.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("Log in to YouTube")
            }
        }
    }
}

/**
 * What an empty feed actually means, which is never just "nothing here":
 * a failed refresh, a group that filters everything out, and channels that
 * genuinely have no recent uploads are three different problems with three
 * different fixes.
 */
@Composable
private fun FeedEmptyState(
    error: String?,
    hasChannels: Boolean,
    isGroupFiltered: Boolean,
    onRetry: () -> Unit,
    onClearGroup: () -> Unit,
    onManage: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp)
    ) {
        Text(
            text = when {
                error != null -> error
                isGroupFiltered -> "No recent uploads from this group."
                !hasChannels -> "You aren't following any channels yet."
                else -> "No recent uploads from your channels."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        when {
            error != null -> OutlinedButton(onClick = onRetry) { Text("Try again") }
            isGroupFiltered -> OutlinedButton(onClick = onClearGroup) { Text("Show all channels") }
            !hasChannels -> Button(onClick = onManage) { Text("Import subscriptions") }
        }
    }
}

/** "128 channels - 40 on this device", or just the count when there is one source. */
private fun subscriptionsSubtitle(
    channelCount: Int,
    localCount: Int,
    isConnected: Boolean
): String = when {
    channelCount == 0 -> "Latest from channels you follow"
    localCount == 0 -> "$channelCount channels"
    !isConnected || localCount == channelCount -> "$channelCount on this device"
    else -> "$channelCount channels - $localCount on this device"
}

@Composable
private fun ChannelRow(
    channel: SubscribedChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocal: Boolean = false,
    onUnfollow: (() -> Unit)? = null
) {
    Surface(
        // Clip before the click handler so the ripple follows the card's corners
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChannelAvatar(channel = channel, size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.subscriberCountText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = channel.subscriberCountText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // Unfollow only appears for device-followed channels: dropping an
            // account subscription is a write to the user's Google account,
            // which belongs behind the channel page, not a list row.
            if (isLocal && onUnfollow != null) {
                IconButton(onClick = onUnfollow) {
                    Icon(
                        imageVector = Icons.Rounded.PersonRemove,
                        contentDescription = "Unfollow ${channel.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelAvatar(channel: SubscribedChannel, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (channel.avatarUrl != null) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = channel.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
