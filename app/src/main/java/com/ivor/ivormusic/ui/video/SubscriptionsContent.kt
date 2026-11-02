package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.ivor.ivormusic.R

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.automirrored.rounded.Article
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ivor.ivormusic.data.SubscriptionPostsIssue
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.util.MatchField
import com.ivor.ivormusic.util.fuzzyScore
import kotlinx.coroutines.launch

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
    val feedUpdatedAt by viewModel.subscriptionFeedUpdatedAt.collectAsState()
    val listLayout = com.ivor.ivormusic.ui.video.LocalVideoListLayout.current
    val videoGridColumns = com.ivor.ivormusic.ui.video.rememberVideoGridColumns()
    val selectedChannelFeed by viewModel.selectedChannelFeed.collectAsState()
    val isSelectedChannelFeedLoading by viewModel.isSelectedChannelFeedLoading.collectAsState()
    val selectedChannelFeedError by viewModel.selectedChannelFeedError.collectAsState()
    val channels by viewModel.subscribedChannels.collectAsState()
    val accountChannels by viewModel.accountSubscribedChannels.collectAsState()
    val bellWrites by viewModel.bellWrites.collectAsState()
    val isChannelsLoading by viewModel.isSubscriptionsLoading.collectAsState()
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val localSubscriptions by viewModel.localSubscriptions.collectAsState()
    val groups by viewModel.subscriptionGroups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.background

    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    // Persisted rather than remembered. rememberSaveable survives rotation and
    // process death, but this tab lives inside Home's AnimatedContent: leaving
    // the tab disposes it and takes the saved state with it, so the feed came
    // back on "Any time" every single time. Matched by name rather than
    // ordinal, so a period removed in a later version falls back to the
    // default instead of throwing.
    val themePreferences = remember(context) {
        com.ivor.ivormusic.data.ThemePreferences(context)
    }
    val feedPeriodName by themePreferences.subscriptionFeedPeriod.collectAsState()
    val feedOrderName by themePreferences.subscriptionFeedOrder.collectAsState()
    val hideWatched by themePreferences.hideWatchedInFeed.collectAsState()
    val feedOptions by themePreferences.videoFeed.collectAsState()
    val watchHistoryRepository = remember(context) {
        com.ivor.ivormusic.data.VideoHistoryRepository(context)
    }
    val watchHistory by watchHistoryRepository.history.collectAsState()
    val watchedIds = remember(watchHistory) { watchHistory.map { it.videoId }.toSet() }
    val feedPeriod = remember(feedPeriodName) {
        SubscriptionFeedPeriod.entries.firstOrNull { it.name == feedPeriodName } ?: SubscriptionFeedPeriod.ALL
    }
    val feedOrder = remember(feedOrderName) {
        SubscriptionFeedOrder.entries.firstOrNull { it.name == feedOrderName } ?: SubscriptionFeedOrder.NEWEST
    }
    var showKindMenu by remember { mutableStateOf(false) }
    var showOrderMenu by remember { mutableStateOf(false) }

    val visibleFeed = remember(
        feed,
        selectedChannelFeed,
        channels,
        selectedChannelId,
        feedPeriod,
        feedOrder,
        hideWatched,
        watchedIds,
        feedOptions.showLive
    ) {
        val selectedChannel = channels.firstOrNull { it.channelId == selectedChannelId }
        val sourceFeed = if (selectedChannel == null) feed else selectedChannelFeed
        val now = System.currentTimeMillis()
        sourceFeed.asSequence()
            .filter { video ->
                feedPeriod.ageMs == null ||
                    (video.publishedAtMs ?: VideoItem.parseRelativeTime(video.uploadedDate, now))
                        ?.let { now - it <= feedPeriod.ageMs } == true
            }
            // A filter over the fetched list rather than a write into it, the
            // same shape the dismissal store uses: marking something unwatched
            // has to put it back on the next frame, not on the next refresh.
            .filter { video -> !hideWatched || video.videoId !in watchedIds }
            // The Video feed setting. Not while one creator is selected: that
            // view is everything from one channel, live included.
            .filter { video -> feedOptions.showLive || selectedChannel != null || !video.isLive }
            .sortedWith(compareBy<VideoItem> {
                it.publishedAtMs ?: VideoItem.parseRelativeTime(it.uploadedDate) ?: Long.MIN_VALUE
            }.let { if (feedOrder == SubscriptionFeedOrder.NEWEST) it.reversed() else it })
            .toList()
    }

    // Endless feed: ask for the account feed's next page when the last
    // visible row is within five of the end, as Home does. Not while one
    // creator is selected (that list is theirs, not the feed), not in
    // oldest-first order (the end of the list is then the newest video, and
    // older pages would land above it), and not once everything loaded is
    // already older than the period filter: every further page would be
    // filtered away, leaving the trigger standing and the paging running
    // to the end of the account's history for nothing.
    val isFeedLoadingMore by viewModel.isSubscriptionFeedLoadingMore.collectAsState()
    val canPageFeed = remember(feed, selectedChannelId, feedPeriod, feedOrder) {
        val now = System.currentTimeMillis()
        val periodMs = feedPeriod.ageMs
        selectedChannelId == null && feed.isNotEmpty() &&
            feedOrder == SubscriptionFeedOrder.NEWEST &&
            (periodMs == null || feed.all { video ->
                (video.publishedAtMs ?: VideoItem.parseRelativeTime(video.uploadedDate, now))
                    ?.let { now - it <= periodMs } == true
            })
    }
    val currentCanPageFeed by rememberUpdatedState(canPageFeed)
    LaunchedEffect(feedListState) {
        snapshotFlow {
            val info = feedListState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.collect { (lastVisible, totalCount) ->
            if (currentCanPageFeed && totalCount > 0 && lastVisible >= totalCount - 5) {
                viewModel.loadMoreSubscriptionFeed()
            }
        }
    }

    // Community posts from followed channels live in their own Posts view
    // rather than between the uploads: scattered in a shuffled order they had
    // no sense of time, and a dedicated list can be newest first. With one
    // creator selected the view is that creator's own Posts tab.
    val showPostsTab by viewModel.subscriptionPostsTab.collectAsState()
    val subscriptionPosts by viewModel.subscriptionPosts.collectAsState()
    val isFeedPostsLoading by viewModel.isFeedPostsLoading.collectAsState()
    val feedPostsExhausted by viewModel.feedPostsExhausted.collectAsState()
    val feedPostsPaused by viewModel.feedPostsPaused.collectAsState()
    val subscriptionPostsIssue by viewModel.subscriptionPostsIssue.collectAsState()
    val selectedChannelPostsFailed by viewModel.selectedChannelPostsFailed.collectAsState()
    val selectedChannelPosts by viewModel.selectedChannelPosts.collectAsState()
    val isSelectedChannelPostsLoading by viewModel.isSelectedChannelPostsLoading.collectAsState()
    val postPhotoViewer = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    FeedPostOverlays(viewModel, postPhotoViewer, onOpenChannel)
    val shownPosts = if (selectedChannelId != null) selectedChannelPosts else subscriptionPosts
    val shownPostsLoading = if (selectedChannelId != null) {
        isSelectedChannelPostsLoading
    } else {
        isFeedPostsLoading
    }
    // Set once this view has asked for its posts, so the first frame shows the
    // skeleton rather than flashing the empty state before the request starts.
    var postsAsked by remember(showPostsTab, selectedChannelId) { mutableStateOf(false) }
    val nearListEnd by remember(feedListState) {
        derivedStateOf {
            val info = feedListState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || lastVisible >= info.totalItemsCount - SUBS_POSTS_LOOKAHEAD
        }
    }
    LaunchedEffect(
        showPostsTab,
        selectedChannelId,
        nearListEnd,
        subscriptionPosts.size,
        isFeedPostsLoading,
        feedPostsExhausted,
        feedPostsPaused,
        subscriptionPostsIssue
    ) {
        if (!showPostsTab) return@LaunchedEffect
        val channelId = selectedChannelId
        if (channelId != null) {
            viewModel.loadSelectedChannelPosts(channelId)
        } else if (!isFeedPostsLoading && !feedPostsExhausted && !feedPostsPaused &&
            (nearListEnd || subscriptionPosts.isEmpty())
        ) {
            // Asked a page at a time as the list nears its end. Posts from
            // channels asked later slot in by date, so the list is newest
            // first across every channel asked so far.
            viewModel.ensureSubscriptionPosts(subscriptionPosts.size + SUBS_POSTS_PAGE)
        }
        postsAsked = true
    }
    val postsScope = rememberCoroutineScope()
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()

    LaunchedEffect(channels, selectedChannelId) {
        if (selectedChannelId != null && channels.none { it.channelId == selectedChannelId }) {
            selectedChannelId = null
        }
    }

    val locallyFollowedIds = remember(localSubscriptions) {
        localSubscriptions.map { it.channelId }.toSet()
    }

    // Persist the rail preference independently of this tab's composition.
    val railPreferences = remember(context) { context.getSharedPreferences("subscription_channel_rail", 0) }
    var channelSort by remember(railPreferences) {
        // OFFICIAL is retained as a stored key for compatibility, but now uses
        // the original FEchannels/local order instead of a name-sorted list.
        mutableStateOf(railPreferences.getString("sort_mode", "RECENT") ?: "RECENT")
    }
    val channelWatchCounts by viewModel.channelWatchCounts.collectAsState()
    val channelWatchCountsProgress by viewModel.channelWatchCountsProgress.collectAsState()
    val pinnedChannelPositions = remember(context) {
        androidx.compose.runtime.mutableStateMapOf<String, Int>().apply {
            runCatching {
                val json = org.json.JSONObject(railPreferences.getString("pinned_positions", "{}") ?: "{}")
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val position = json.optInt(key, 0)
                    if (position > 0) put(key, position)
                }
            }
        }
    }
    var channelToPosition by remember { mutableStateOf<SubscribedChannel?>(null) }
    var channelPositionInput by rememberSaveable { mutableStateOf("1") }
    val recentlyUploadingChannels = remember(
        channels, feed, channelSort, channelWatchCounts, pinnedChannelPositions.toMap(),
        localSubscriptions, accountChannels
    ) {
        val newestUploadById = mutableMapOf<String, Long>()
        val newestUploadByName = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()
        feed.forEachIndexed { index, video ->
            val uploadedAt = video.publishedAtMs
                ?: VideoItem.parseRelativeTime(video.uploadedDate, now)
                ?: (now - index)
            video.channelId?.takeIf { it.isNotBlank() }?.let { id ->
                newestUploadById[id] = maxOf(newestUploadById[id] ?: Long.MIN_VALUE, uploadedAt)
            }
            val normalizedName = video.channelName.trim().lowercase()
            if (normalizedName.isNotEmpty()) {
                newestUploadByName[normalizedName] = maxOf(
                    newestUploadByName[normalizedName] ?: Long.MIN_VALUE, uploadedAt
                )
            }
        }
        val byRecent = compareByDescending<SubscribedChannel> { channel ->
            newestUploadById[channel.channelId]
                ?: newestUploadByName[channel.name.trim().lowercase()]
                ?: Long.MIN_VALUE
        }.thenBy { it.name.lowercase() }
        val localSubscriptionPositions = localSubscriptions.mapIndexed { index, item ->
            item.channelId to index
        }.toMap()
        val subscriberCounts = accountChannels.associate { accountChannel ->
            val raw = accountChannel.subscriberCountText.orEmpty()
            val match = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*([KMB万億]?)""", RegexOption.IGNORE_CASE).find(raw)
            val amount = match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            val multiplier = when (match?.groupValues?.get(2)?.uppercase()) {
                "K" -> 1_000.0
                "M" -> 1_000_000.0
                "B" -> 1_000_000_000.0
                "万" -> 10_000.0
                "億" -> 100_000_000.0
                else -> 1.0
            }
            accountChannel.channelId to ((amount ?: 0.0) * multiplier).toLong()
        }
        val sortedAll = when (channelSort) {
            "MOST_WATCHED" -> channels.sortedWith(
                compareByDescending<SubscribedChannel> {
                    channelWatchCounts[it.channelId] ?: channelWatchCounts["name:" + it.name.trim().lowercase()] ?: 0
                }.thenBy { it.name.lowercase() }
            )
            "SUBSCRIBER_COUNT" -> channels.sortedWith(
                compareByDescending<SubscribedChannel> { subscriberCounts[it.channelId] ?: Long.MIN_VALUE }
                    .thenBy { it.name.lowercase() }
            )
            "SUBSCRIPTION_DATE" -> channels.sortedWith(
                compareByDescending<SubscribedChannel> { localSubscriptionPositions[it.channelId] ?: Int.MIN_VALUE }
                    .thenBy { it.name.lowercase() }
            )
            "NAME" -> channels.sortedBy { it.name.lowercase() }
            "OLDEST" -> channels.sortedWith(
                compareBy<SubscribedChannel> { channel ->
                    newestUploadById[channel.channelId]
                        ?: newestUploadByName[channel.name.trim().lowercase()]
                        ?: Long.MIN_VALUE
                }.thenBy { it.name.lowercase() }
            )
            // OFFICIAL/MANUAL retain source order, including the user's local manual order.
            "OFFICIAL" -> channels
            "RECENT" -> channels.sortedWith(byRecent)
            else -> channels.sortedWith(byRecent)
        }
        // Fixed channels occupy absolute 1-based positions; the selected sort
        // fills all remaining slots.
        val ordered = MutableList<SubscribedChannel?>(channels.size) { null }
        val channelById = channels.associateBy { it.channelId }
        pinnedChannelPositions.entries
            .mapNotNull { (id, position) -> channelById[id]?.let { it to position } }
            .sortedWith(compareBy<Pair<SubscribedChannel, Int>> { it.second }.thenBy { it.first.name.lowercase() })
            .forEach { (channel, requestedPosition) ->
                if (ordered.isNotEmpty()) {
                    val target = (requestedPosition - 1).coerceIn(0, ordered.lastIndex)
                    var slot = target
                    while (slot < ordered.size && ordered[slot] != null) slot++
                    if (slot >= ordered.size) {
                        slot = target
                        while (slot >= 0 && ordered[slot] != null) slot--
                    }
                    if (slot >= 0) ordered[slot] = channel
                }
            }
        val fixedIds = pinnedChannelPositions.keys
        val remaining = sortedAll.filter { it.channelId !in fixedIds }.iterator()
        ordered.indices.forEach { index -> if (ordered[index] == null && remaining.hasNext()) ordered[index] = remaining.next() }
        ordered.filterNotNull()
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
    // away instead of leaving the empty state up until a manual refresh. The
    // ViewModel also warms this before the tab is composed; this non-forced
    // call simply covers account connection without duplicating an in-flight
    // local refresh.
    LaunchedEffect(isYouTubeConnected, channelSort) {
        if (isYouTubeConnected && channelSort == "MOST_WATCHED") {
            viewModel.loadChannelWatchCounts()
        }
    }

    LaunchedEffect(isYouTubeConnected, localSubscriptions.size) {
        viewModel.loadSubscriptionFeed()
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

    channelToPosition?.let { channel ->
        AlertDialog(
            onDismissRequest = { channelToPosition = null },
            title = { Text(stringResource(R.string.sm_pin_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sm_pin_body, channel.name))
                    OutlinedTextField(
                        value = channelPositionInput,
                        onValueChange = { value -> channelPositionInput = value.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.sm_pin_position_label, channels.size.coerceAtLeast(1))) },
                        singleLine = true
                    )
                    Text(stringResource(R.string.sm_pin_unpin_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val position = channelPositionInput.toIntOrNull()
                    if (position != null && position in 1..channels.size.coerceAtLeast(1)) {
                        pinnedChannelPositions[channel.channelId] = position
                        val json = org.json.JSONObject()
                        pinnedChannelPositions.forEach { (id, value) -> json.put(id, value) }
                        railPreferences.edit().putString("pinned_positions", json.toString()).apply()
                    }
                    channelToPosition = null
                }) { Text(stringResource(R.string.sm_pin_confirm)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pinnedChannelPositions.remove(channel.channelId)
                        val json = org.json.JSONObject()
                        pinnedChannelPositions.forEach { (id, value) -> json.put(id, value) }
                        railPreferences.edit().putString("pinned_positions", json.toString()).apply()
                        channelToPosition = null
                    }) { Text(stringResource(R.string.sm_unpin)) }
                    TextButton(onClick = { channelToPosition = null }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
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
                    stringResource(R.string.sc_unfollow_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unsubscribeLocally(channel.channelId)
                    channelToUnfollow = null
                }) { Text(stringResource(R.string.unfollow)) }
            },
            dismissButton = {
                TextButton(onClick = { channelToUnfollow = null }) { Text(stringResource(R.string.action_cancel)) }
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
                isRefreshing = if (showPostsTab) {
                    // A refresh empties the posts, so the skeleton covers it.
                    false
                } else if (selectedChannelId != null) {
                    isSelectedChannelFeedLoading && selectedChannelFeed.isNotEmpty()
                } else {
                    isFeedLoading && feed.isNotEmpty()
                },
                onRefresh = {
                    val selected = channels.firstOrNull { it.channelId == selectedChannelId }
                    if (showPostsTab) {
                        if (selected != null) {
                            viewModel.loadSelectedChannelPosts(selected.channelId, force = true)
                        } else {
                            viewModel.refreshFeedPosts()
                        }
                    } else if (selected != null) {
                        viewModel.loadSelectedChannelFeed(selected)
                    } else {
                        viewModel.refreshFeedPosts()
                        viewModel.loadSubscriptionFeed(force = true)
                        viewModel.loadSubscriptions(force = true)
                    }
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
                                        text = stringResource(R.string.settings_subscriptions),
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
                                        contentDescription = stringResource(R.string.sp_manage_subscriptions),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Videos or Posts. Item 1, so a switch made deep in the
                    // list can bring the switch itself to the top.
                    item(key = "feed-kind") {
                        SubscriptionsKindSwitch(
                            showPosts = showPostsTab,
                            onSelect = { posts ->
                                if (posts != showPostsTab) {
                                    haptics.subtle()
                                    viewModel.setSubscriptionPostsTab(posts)
                                    postsScope.launch {
                                        if (feedListState.firstVisibleItemIndex > 1) {
                                            feedListState.scrollToItem(1)
                                        }
                                    }
                                }
                            },
                            trailing = {}
                        )
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
                                        label = { Text(stringResource(R.string.filter_all)) },
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
                    feedProgress?.takeIf { !showPostsTab }?.let { (done, total) ->
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

                    channelWatchCountsProgress?.let { (pages, videos) ->
                        if (channelSort == "MOST_WATCHED") {
                            item(key = "channel-watch-progress") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                ) {
                                    Text(
                                        text = "Loading watch history · $pages pages · $videos videos",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
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
                                            .clickable {
                                                selectedChannelId = null
                                                viewModel.clearSelectedChannelFeed()
                                            }
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
                                                    contentDescription = stringResource(R.string.cd_all_channels),
                                                    tint = if (selectedChannelId == null) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.filter_all),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selectedChannelId == null) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                items(recentlyUploadingChannels, key = { it.channelId }) { channel ->
                                    val selected = selectedChannelId == channel.channelId
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (selected) {
                                                        selectedChannelId = null
                                                        viewModel.clearSelectedChannelFeed()
                                                    } else {
                                                        selectedChannelId = channel.channelId
                                                        viewModel.loadSelectedChannelFeed(channel)
                                                    }
                                                },
                                                onLongClick = {
                                                    channelPositionInput = (pinnedChannelPositions[channel.channelId]
                                                        ?: (recentlyUploadingChannels.indexOfFirst { it.channelId == channel.channelId } + 1).coerceAtLeast(1)).toString()
                                                    channelToPosition = channel
                                                }
                                            )
                                            .padding(4.dp)
                                            .width(64.dp)
                                    ) {
                                        Box {
                                            Surface(
                                                shape = CircleShape,
                                                border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                ChannelAvatar(channel = channel, size = 56.dp)
                                            }
                                            pinnedChannelPositions[channel.channelId]?.let { position ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    shadowElevation = 3.dp,
                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.PushPin,
                                                            contentDescription = "Pinned at position $position",
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = position.toString(),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
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

                        if (!showPostsTab) item(key = "feed-controls") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box {
                                    FilterChip(
                                        selected = feedPeriod != SubscriptionFeedPeriod.ALL,
                                        onClick = { showKindMenu = true },
                                        leadingIcon = { Icon(Icons.Rounded.FilterList, null, Modifier.size(18.dp)) },
                                        label = { Text(feedPeriodLabel(feedPeriod)) }
                                    )
                                    DropdownMenu(expanded = showKindMenu, onDismissRequest = { showKindMenu = false }) {
                                        SubscriptionFeedPeriod.entries.forEach { period ->
                                            DropdownMenuItem(
                                                text = { Text(feedPeriodLabel(period)) },
                                                onClick = {
                                                    themePreferences.setSubscriptionFeedPeriod(period.name)
                                                    showKindMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                                // A chip rather than a menu entry, because it
                                // is a switch with two states and its own state
                                // is the answer to "where did those videos go".
                                FilterChip(
                                    selected = hideWatched,
                                    onClick = { themePreferences.setHideWatchedInFeed(!hideWatched) },
                                    leadingIcon = {
                                        Icon(
                                            if (hideWatched) {
                                                Icons.Rounded.VisibilityOff
                                            } else {
                                                Icons.Rounded.Visibility
                                            },
                                            null,
                                            Modifier.size(18.dp)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.sc_hide_watched)) }
                                )
                                Box {
                                    FilterChip(
                                        selected = feedOrder != SubscriptionFeedOrder.NEWEST,
                                        onClick = { showOrderMenu = true },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)) },
                                        label = { Text(feedOrderLabel(feedOrder)) }
                                    )
                                    DropdownMenu(expanded = showOrderMenu, onDismissRequest = { showOrderMenu = false }) {
                                        SubscriptionFeedOrder.entries.forEach { order ->
                                            DropdownMenuItem(
                                                text = { Text(feedOrderLabel(order)) },
                                                onClick = {
                                                    themePreferences.setSubscriptionFeedOrder(order.name)
                                                    showOrderMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showPostsTab) {
                        val selectedName = channels.firstOrNull { it.channelId == selectedChannelId }?.name
                        if (shownPosts.isEmpty() && (shownPostsLoading || !postsAsked)) {
                            item(key = "posts-loading") {
                                SkeletonList(
                                    count = 3,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) { alpha -> VideoCardSkeleton(alpha = alpha) }
                            }
                        } else if (shownPosts.isEmpty()) {
                            item(key = "posts-empty") {
                                PostsEmptyState(
                                    channelName = selectedName,
                                    hasChannels = channels.isNotEmpty(),
                                    issue = when {
                                        selectedChannelId == null -> subscriptionPostsIssue
                                        selectedChannelPostsFailed -> SubscriptionPostsIssue.FAILED
                                        else -> SubscriptionPostsIssue.NONE
                                    },
                                    moreToCheck = selectedChannelId == null && !feedPostsExhausted,
                                    onRetry = {
                                        val channelId = selectedChannelId
                                        when {
                                            channelId != null ->
                                                viewModel.loadSelectedChannelPosts(channelId, force = true)
                                            feedPostsExhausted -> viewModel.refreshFeedPosts()
                                            else -> viewModel.resumeSubscriptionPosts()
                                        }
                                    }
                                )
                            }
                        } else {
                            items(
                                shownPosts,
                                key = { "subs_post_" + it.postId },
                                contentType = { "feed_post" }
                            ) { post ->
                                // Capped and centred: on a wide window a
                                // full-width card would make every photo a
                                // poster.
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Box(modifier = Modifier.widthIn(max = SUBS_POST_MAX_WIDTH)) {
                                        FeedPostCard(post, viewModel, onVideoClick, onOpenChannel, postPhotoViewer)
                                    }
                                }
                            }
                            if (shownPostsLoading) {
                                item(key = "posts-loading-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.LoadingIndicator(
                                            modifier = Modifier.size(36.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else if (selectedChannelId == null && feedPostsPaused && !feedPostsExhausted) {
                                item(key = "posts-check-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OutlinedButton(onClick = viewModel::resumeSubscriptionPosts) {
                                            Text(stringResource(R.string.sc_posts_check_more))
                                        }
                                    }
                                }
                            }
                        }
                    } else if (isSelectedChannelFeedLoading && selectedChannelId != null) {
                        item(key = "selected-channel-loading") {
                            SkeletonList(
                                count = 3,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) { alpha -> VideoCardSkeleton(alpha = alpha) }
                        }
                    } else if (isFeedLoading && feed.isEmpty()) {
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
                                error = selectedChannelFeedError ?: stringResource(R.string.sc_no_uploads_match),
                                hasChannels = true,
                                isGroupFiltered = selectedGroupId != null,
                                onRetry = {
                                    val selected = channels.firstOrNull { it.channelId == selectedChannelId }
                                    if (selected != null && selectedChannelFeedError != null) {
                                        viewModel.loadSelectedChannelFeed(selected)
                                    } else {
                                        selectedChannelId = null
                                        themePreferences.setSubscriptionFeedPeriod(
                                            SubscriptionFeedPeriod.ALL.name
                                        )
                                        viewModel.clearSelectedChannelFeed()
                                    }
                                },
                                onClearGroup = { viewModel.selectSubscriptionGroup(null) },
                                onManage = onManageSubscriptions
                            )
                        }
                    } else {
                        feedUpdatedAt?.let { at ->
                            item(key = "feed_updated") {
                                Text(
                                    text = stringResource(
                                        R.string.subs_feed_updated,
                                        android.text.format.DateUtils.getRelativeTimeSpanString(
                                            at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                                        ).toString()
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                                )
                            }
                        }
                        videoListItems(
                            videos = visibleFeed,
                            layout = listLayout,
                            columns = videoGridColumns
                        ) { video, cell ->
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onLongClick = { onVideoLongPress(video) },
                                onOpenChannel = onOpenChannel,
                                modifier = cell
                            )
                        }
                        if (isFeedLoadingMore) {
                            item(key = "feed-loading-more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.LoadingIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
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
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.cd_all_channels),
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
                                placeholder = stringResource(R.string.search_channels),
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
                                    stringResource(R.string.sc_none_found),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (matchedChannels.isEmpty()) {
                        item(key = "no-channel-matches") {
                            SearchEmptyState(
                                title = "No channels match \"$channelQuery\"",
                                hint = stringResource(R.string.sc_search_hint)
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
                                onBellChosen = { level -> viewModel.setChannelBell(channel, level) },
                                bellBusy = channel.channelId in bellWrites,
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
                text = stringResource(R.string.settings_subscriptions),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sc_empty_body),
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
                Text(stringResource(R.string.import_subscriptions))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Rounded.Login, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.log_in_youtube))
            }
        }
    }
}

/** Posts asked for beyond those in hand each time the Posts view nears its end. */
private const val SUBS_POSTS_PAGE = 10

/** Rows from the end of the Posts view at which the next page is asked for. */
private const val SUBS_POSTS_LOOKAHEAD = 4

/** Widest a post card is drawn in the Posts view. */
private val SUBS_POST_MAX_WIDTH = 640.dp

/**
 * Videos | Posts: the two things the Subscriptions feed shows, as a connected
 * button group like the channel page's tab strip.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubscriptionsKindSwitch(
    showPosts: Boolean,
    onSelect: (posts: Boolean) -> Unit,
    trailing: @Composable () -> Unit
) {
    val options = listOf(
        false to stringResource(R.string.sc_tab_videos),
        true to stringResource(R.string.sc_tab_posts)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
        options.forEachIndexed { index, (posts, label) ->
            val selected = posts == showPosts
            ToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(posts) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = com.ivor.ivormusic.ui.settings.settingsChoiceButtonColors(),
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
        }
        trailing()
    }
}

/** The Posts view with nothing to show: nobody followed has posted, or one creator has not. */
@Composable
private fun PostsEmptyState(
    channelName: String?,
    hasChannels: Boolean,
    issue: SubscriptionPostsIssue,
    moreToCheck: Boolean,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                issue == SubscriptionPostsIssue.LOCAL_ONLY -> stringResource(R.string.sc_posts_local_only)
                issue == SubscriptionPostsIssue.RATE_LIMITED -> stringResource(R.string.sc_posts_rate_limited)
                issue == SubscriptionPostsIssue.FAILED -> stringResource(R.string.sc_posts_failed)
                channelName != null -> stringResource(R.string.sc_posts_empty_channel, channelName)
                !hasChannels -> stringResource(R.string.sc_not_following)
                moreToCheck -> stringResource(R.string.sc_posts_none_yet)
                else -> stringResource(R.string.sc_posts_empty)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hasChannels && issue != SubscriptionPostsIssue.LOCAL_ONLY) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onRetry) {
                Text(
                    stringResource(
                        if (moreToCheck && issue == SubscriptionPostsIssue.NONE) {
                            R.string.sc_posts_check_more
                        } else {
                            R.string.action_try_again
                        }
                    )
                )
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
                isGroupFiltered -> stringResource(R.string.sc_group_empty)
                !hasChannels -> stringResource(R.string.sc_not_following)
                else -> stringResource(R.string.sc_no_uploads)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        when {
            error != null -> OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
            isGroupFiltered -> OutlinedButton(onClick = onClearGroup) { Text(stringResource(R.string.show_all_channels)) }
            !hasChannels -> Button(onClick = onManage) { Text(stringResource(R.string.import_subscriptions)) }
        }
    }
}

/** "128 channels - 40 on this device", or just the count when there is one source. */
@Composable
private fun subscriptionsSubtitle(
    channelCount: Int,
    localCount: Int,
    isConnected: Boolean
): String = when {
    channelCount == 0 -> stringResource(R.string.sc_latest_from_channels)
    localCount == 0 -> pluralStringResource(R.plurals.n_channels, channelCount, channelCount)
    !isConnected || localCount == channelCount -> stringResource(R.string.sc_on_device, channelCount)
    else -> stringResource(R.string.sc_channels_on_device, channelCount, localCount)
}

@Composable
private fun ChannelRow(
    channel: SubscribedChannel,
    onClick: () -> Unit,
    /** The account bell; drawn only when [channel] carries one. */
    onBellChosen: (com.ivor.ivormusic.data.BellLevel) -> Unit,
    bellBusy: Boolean,
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
            // The account bell is the one account write a row offers: it is
            // cheap to undo and this list is where many are set at once.
            channel.bell?.let { bell ->
                com.ivor.ivormusic.ui.channel.ChannelBellButton(
                    bell = bell,
                    channelName = channel.name,
                    onLevelChosen = onBellChosen,
                    busy = bellBusy,
                    size = 40.dp
                )
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

@Composable
private fun feedPeriodLabel(period: SubscriptionFeedPeriod): String = when (period) {
    SubscriptionFeedPeriod.ALL -> stringResource(R.string.sc_any_time)
    SubscriptionFeedPeriod.TODAY -> stringResource(R.string.sc_today)
    SubscriptionFeedPeriod.THIS_WEEK -> stringResource(R.string.sc_this_week)
}

@Composable
private fun feedOrderLabel(order: SubscriptionFeedOrder): String = when (order) {
    SubscriptionFeedOrder.NEWEST -> stringResource(R.string.sc_newest_first)
    SubscriptionFeedOrder.OLDEST -> stringResource(R.string.sc_oldest_first)
}
