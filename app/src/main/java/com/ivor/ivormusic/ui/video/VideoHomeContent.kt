package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import coil.compose.AsyncImage
import com.ivor.ivormusic.ui.components.VideoThumbnail
import com.ivor.ivormusic.ui.components.VideoThumbnailBadge
import com.ivor.ivormusic.data.DownloadedVideo
import com.ivor.ivormusic.data.ShortsItem
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.components.MusicVideoToggle
import com.ivor.ivormusic.ui.components.MusicVideoToggleState
import com.ivor.ivormusic.ui.components.rememberMusicVideoToggleState
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * Video Home Screen Content for Video Mode.
 * Displays trending/recommended videos with thumbnails, channel names, views, etc.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoHomeContent(
    videos: List<VideoItem>,
    isLoading: Boolean,
    isOffline: Boolean = false,
    downloadedVideos: List<DownloadedVideo> = emptyList(),
    onVideoClick: (VideoItem) -> Unit,
    onDownloadedVideoClick: (DownloadedVideo) -> Unit = {},
    onEnqueueVideo: ((VideoItem, Boolean) -> Unit)? = null,
    /** Open a video's creator, from the long-press sheet. */
    onOpenChannel: ((String) -> Unit)? = null,
    shortsEnabled: Boolean = false,
    shorts: List<ShortsItem> = emptyList(),
    onShortClick: (Int) -> Unit = {},
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onRefresh: () -> Unit,
    recommendationsEnabled: Boolean = true,
    compact: Boolean = false,
    isDarkMode: Boolean,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    videoMode: Boolean = true,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode),
    // Hoisted by HomeScreen so the position survives a tab switch and the nav
    // bar can send it back to the top on a re-tap. Defaulted for previews and
    // any caller that does not care.
    listState: LazyListState = rememberLazyListState(),
    /**
     * Whether a card plays a silent preview when the feed is rested on it.
     * Off by default here as well as in preferences: a caller that has not
     * thought about it gets the feed that does not move on its own.
     */
    inlinePreviews: Boolean = false
) {
    // Null when the setting is off, and then the cards read a null local and
    // draw exactly what they drew before this existed.
    val previewController = rememberInlinePreviewController(enabled = inlinePreviews)
    if (previewController != null) {
        // The dwell timer only runs while the list is still. Written here
        // rather than read per card, so one state read feeds every card.
        previewController.isListSettled = !listState.isScrollInProgress
    }

    // Anything that opens over the feed stops the preview under it. The video
    // player and the Shorts overlay are drawn above the NavHost, so this screen
    // is never paused and the card's own visibility still reads as on screen -
    // nothing else here would notice that a full-screen player is covering it.
    val openVideo: (VideoItem) -> Unit = { video ->
        previewController?.release()
        onVideoClick(video)
    }
    val openShort: (Int) -> Unit = { index ->
        previewController?.release()
        onShortClick(index)
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val localSubscriptions by viewModel.localSubscriptions.collectAsState()
    val subscribedChannels by viewModel.subscribedChannels.collectAsState()
    val showOfflineDownloads = isOffline && downloadedVideos.isNotEmpty()
    val isShortsLoading by viewModel.isShortsLoading.collectAsState()
    val shortsFeedFailed by viewModel.shortsFeedFailed.collectAsState()

    // Notifications sheet state
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val notifications by viewModel.notifications.collectAsState()
    val isNotificationsLoading by viewModel.isNotificationsLoading.collectAsState()

    // Options sheet (long-press on a video card)
    var saveTargetVideo by remember { mutableStateOf<VideoItem?>(null) }

    fun onVideoLongPress(video: VideoItem) {
        saveTargetVideo = video
    }

    // Animation state for staggered entry
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    if (showNotificationsSheet) {
        NotificationsSheet(
            notifications = notifications,
            isLoading = isNotificationsLoading,
            onNotificationClick = { notification ->
                val videoId = notification.videoId
                if (videoId != null) {
                    showNotificationsSheet = false
                    onVideoClick(
                        VideoItem(
                            videoId = videoId,
                            title = notification.message,
                            channelName = "",
                            channelIconUrl = notification.channelAvatarUrl,
                            thumbnailUrl = notification.videoThumbnailUrl,
                            duration = 0L,
                            viewCount = ""
                        )
                    )
                }
            },
            onDismiss = { showNotificationsSheet = false }
        )
    }

    saveTargetVideo?.let { video ->
        VideoOptionsSheetHost(
            video = video,
            viewModel = viewModel,
            onDismiss = { saveTargetVideo = null },
            onEnqueue = onEnqueueVideo?.let { enqueue -> { next -> enqueue(video, next) } },
            onOpenChannel = onOpenChannel
        )
    }

    ExpressivePullToRefresh(
        // Only let the pull-to-refresh spinner represent a refresh over existing
        // content. The empty-feed case shows its own centered indicator below, and
        // driving both off the same flag renders two spinners at once.
        isRefreshing = isLoading && (videos.isNotEmpty() || showOfflineDownloads),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading && videos.isEmpty() && !showOfflineDownloads) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            val isRecommendationsLoadingMore by viewModel.isVideoLoadingMore.collectAsState()
            val isMixLoadingMore by viewModel.isSubscriptionMixLoadingMore.collectAsState()
            val isLoadingMore = if (recommendationsEnabled) isRecommendationsLoadingMore else isMixLoadingMore
            // Read through updated state: the effect below is keyed on the list
            // alone and outlives a change of this setting.
            val currentRecommendationsEnabled by rememberUpdatedState(recommendationsEnabled)

            // Endless feed: ask for the next page whenever the last visible
            // item is within 5 of the end. The ViewModel guards against
            // duplicate/exhausted loads, so firing on every scroll frame is fine.
            LaunchedEffect(listState) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
                }.collect { (lastVisible, totalCount) ->
                    if (totalCount > 0 && lastVisible >= totalCount - 5) {
                        if (currentRecommendationsEnabled) {
                            viewModel.loadMoreTrendingVideos()
                        } else {
                            viewModel.loadMoreSubscriptionMix()
                        }
                    }
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(
                LocalInlinePreview provides previewController
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)
            ) {
                // Top Bar
                item {
                    VideoTopBarSection(
                        onProfileClick = onProfileClick,
                        onSettingsClick = onSettingsClick,
                        onDownloadsClick = onDownloadsClick,
                        onNotificationsClick = {
                            if (isYouTubeConnected) {
                                viewModel.loadNotifications(force = true)
                                showNotificationsSheet = true
                            } else {
                                onProfileClick()
                            }
                        },
                        viewModel = viewModel,
                        videoMode = videoMode,
                        onVideoModeToggle = onVideoModeToggle,
                        showModeToggle = showModeToggle,
                        modeToggleState = modeToggleState
                    )
                }
                
                // Section title - changes based on whether user is logged in
                // or recommendations are disabled (in which case subscriptions are shown).
                if (showOfflineDownloads || recommendationsEnabled || videos.isNotEmpty()) item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    ) {
                        Text(
                            text = when {
                                showOfflineDownloads -> stringResource(R.string.vh_available_offline)
                                !recommendationsEnabled -> stringResource(R.string.vh_subscription_mix)
                                isYouTubeConnected -> stringResource(R.string.vh_recommended_for_you)
                                else -> stringResource(R.string.vh_trending_videos)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                }
                
                if (showOfflineDownloads) {
                    items(downloadedVideos, key = { "download_${it.id}" }) { downloaded ->
                        VideoCard(
                            compact = compact,
                            video = downloaded.asOfflineVideoItem(),
                            onClick = { onDownloadedVideoClick(downloaded) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // Video cards, with the Shorts shelf slotted in after the
                    // first two like the YouTube home feed.
                    val feedVideos = videos
                    val leadingVideos = if (!shortsEnabled) {
                        feedVideos
                    } else {
                        feedVideos.take(2)
                    }
                    val trailingVideos = if (!shortsEnabled) {
                        emptyList()
                    } else {
                        feedVideos.drop(2)
                    }

                    items(leadingVideos) { video ->
                        VideoCard(
                            compact = compact,
                            video = video,
                            onClick = { openVideo(video) },
                            onLongClick = { onVideoLongPress(video) },
                            onOpenChannel = onOpenChannel,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (shortsEnabled) {
                        item(key = "shorts_shelf") {
                            ShortsShelf(
                                shorts = shorts,
                                onShortClick = openShort,
                                isLoading = isShortsLoading,
                                failed = shortsFeedFailed,
                                onRefresh = { viewModel.loadShortsFeed(force = true) }
                            )
                        }
                    }

                    items(trailingVideos) { video ->
                        VideoCard(
                            compact = compact,
                            video = video,
                            onClick = { openVideo(video) },
                            onLongClick = { onVideoLongPress(video) },
                            onOpenChannel = onOpenChannel,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                
                // Empty state
                if (videos.isEmpty() &&
                    !isLoading && !showOfflineDownloads && shorts.isEmpty()
                ) {
                    val hasSubscriptions = isYouTubeConnected || subscribedChannels.isNotEmpty() || localSubscriptions.isNotEmpty()

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (recommendationsEnabled) {
                                        Icons.Rounded.VideoLibrary
                                    } else {
                                        Icons.Rounded.Subscriptions
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = when {
                                        !recommendationsEnabled ->
                                            if (hasSubscriptions) {
                                                stringResource(R.string.vh_subscription_mix_empty)
                                            } else {
                                                stringResource(R.string.vh_no_subscriptions)
                                            }
                                        isOffline -> stringResource(R.string.vh_youre_offline)
                                        else -> stringResource(R.string.vh_no_videos_found)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!recommendationsEnabled && !hasSubscriptions) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.vh_no_subscriptions_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (isOffline) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.vh_downloaded_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Load-more footer
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
            }
        }
    }
}

private fun DownloadedVideo.asOfflineVideoItem() = VideoItem(
    videoId = id,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    duration = durationMs / 1000L,
    viewCount = quality.orEmpty()
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoTopBarSection(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    viewModel: HomeViewModel,
    videoMode: Boolean = true,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode),
    /**
     * The bell belongs to the Home feed it reports on, and its sheet is owned
     * by [VideoHomeContent]. The shell copy of this bar - the one that appears
     * when Home is hidden - leaves it out rather than drawing a button with
     * nowhere to go.
     */
    showNotifications: Boolean = true
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val iconColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    val userAvatar by viewModel.userAvatar.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    val context = LocalContext.current
    val incognito by com.ivor.ivormusic.data.IncognitoMode.enabled(context).collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile avatar. Incognito shows as a badge on the avatar itself
        // rather than a chip beside it, so the bar keeps its shape whether
        // history is paused or not.
        Box {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(surfaceColor)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center
            ) {
                if (userAvatar != null) {
                    AsyncImage(
                        model = userAvatar,
                        contentDescription = stringResource(R.string.cd_profile),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = stringResource(R.string.cd_profile),
                        tint = iconColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            // The history-paused mark. Tonal fill so it reads as "a mode is on"
            // without taking over the bar; the badge itself carries the label,
            // so a screen reader announces it alongside the profile button.
            androidx.compose.animation.AnimatedVisibility(
                visible = incognito,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = stringResource(R.string.incognito_active),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // Right side icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Notifications Button
            if (showNotifications) IconButton(
                onClick = onNotificationsClick,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = containerColor,
                    contentColor = iconColor
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = stringResource(R.string.settings_notifications),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Downloads Button
            Box {
                IconButton(
                    onClick = onDownloadsClick,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = containerColor,
                        contentColor = iconColor
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.cd_downloads),
                        modifier = Modifier.size(22.dp)
                    )
                }
                // Badge for active downloads
                if (downloadingIds.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            
            IconButton(
                onClick = onSettingsClick,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = containerColor,
                    contentColor = iconColor
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Music/Video mode switch, anchored in the corner so it stays put
            // when the home content swaps between modes. Can be hidden from
            // Settings (Home Screen Mode Toggle).
            if (showModeToggle) {
                MusicVideoToggle(
                    videoMode = videoMode,
                    onVideoModeChange = onVideoModeToggle,
                    state = modeToggleState
                )
            }
        }
    }
}

/**
 * Shorts shelf: an expressive bolt badge header plus the same
 * MultiBrowseCarousel treatment the music home uses for albums — masked
 * items that morph between sizes as they scroll. Tapping a card opens the
 * fullscreen Shorts player.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortsShelf(
    shorts: List<ShortsItem>,
    onShortClick: (Int) -> Unit,
    isLoading: Boolean,
    failed: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshLabel = stringResource(R.string.shorts_refresh)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.sp_shorts),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.semantics { contentDescription = refreshLabel }
            ) {
                if (isLoading) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (failed || (shorts.isEmpty() && !isLoading)) {
            Text(
                text = stringResource(if (failed) R.string.shorts_refresh_failed else R.string.shorts_feed_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        if (shorts.isEmpty()) return@Column

        val carouselState = rememberCarouselState { shorts.size }
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 160.dp,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) { index ->
            val item = shorts[index]
            Box(
                modifier = Modifier
                    .maskClip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onShortClick(index) }
            ) {
                AsyncImage(
                    model = item.portraitThumbnailUrl,
                    contentDescription = item.title.ifBlank { stringResource(R.string.sp_shorts) },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    if (item.title.isNotBlank()) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.viewCount.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.viewCount,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Video Card component - displays a single video with thumbnail, title, channel, views.
 * [onLongClick] is optional; the home feed uses it for the save-to-playlist sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** Opens the creator when the avatar is tapped, without invoking [onClick]. */
    onOpenChannel: ((String) -> Unit)? = null,
    compact: Boolean = false
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardShape = RoundedCornerShape(16.dp)
    // A collab card opens the list of credited channels rather than guessing
    // one; an ordinary card navigates as before. See videoChannelTap.
    val openChannel = videoChannelTap(video, onOpenChannel)

    Surface(
        // Clip before the click handler - Surface applies its own clip downstream of
        // the caller's modifier, so a ripple registered above it spills into square
        // corners on tap and long-press
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        if (compact) {
            CompactVideoCardContent(video, openChannel, onLongClick)
        } else {
            Column {
                // Rested on with previews turned on, this card plays where it
                // sits. The fraction is written into a float state nothing
                // reads during composition - only the claim's polling lambda -
                // so scrolling moves the number without recomposing every card
                // on screen every frame.
                val preview = LocalInlinePreview.current
                val visibleFraction = remember(video.videoId) { mutableFloatStateOf(0f) }
                val isPreviewing = preview?.previewingId == video.videoId

                // Thumbnail with duration overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .then(
                            if (preview == null) Modifier
                            else Modifier.onGloballyPositioned {
                                visibleFraction.floatValue = it.visibleVerticalFraction()
                            }
                        )
                ) {
                    if (preview != null) {
                        InlinePreviewClaim(video = video) { visibleFraction.floatValue }
                    }

                    VideoThumbnail(
                        video = video,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Drawn over the thumbnail rather than in place of it, and
                    // only once a frame has actually arrived: swapping to an
                    // empty surface first shows a black hole where the picture
                    // was for as long as the stream takes to start.
                    if (isPreviewing && preview != null) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = preview.isRendering,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.matchParentSize()
                        ) {
                            InlinePreviewSurface(preview, Modifier.fillMaxSize())
                        }
                    }

                    // Gradient overlay at bottom for duration
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )
                
                    // Skipped entirely when the duration is unknown, rather
                    // than drawn as "0:00".
                    VideoThumbnailBadge(
                        video = video,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    )

                    // How far the preview has got, on the card itself. Drawn
                    // only while previewing, so a card at rest is exactly the
                    // card it was before this feature existed.
                    if (isPreviewing && preview != null && preview.isRendering) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { preview.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
            
                // Video info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Channel avatar
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .then(
                                if (openChannel != null) {
                                    Modifier.clickable(
                                        onClickLabel = "Open ${video.channelName} channel",
                                        onClick = openChannel
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!video.channelIconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = video.channelIconUrl,
                                    contentDescription = "${video.channelName} channel",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = video.channelName.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                
                    Column(modifier = Modifier.weight(1f)) {
                        // Video title
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    
                        Spacer(modifier = Modifier.height(4.dp))
                    
                        // Channel name, views, date
                        Text(
                            text = buildString {
                                append(video.channelName)
                                if (video.viewCount.isNotEmpty()) {
                                    append(" • ")
                                    append(video.viewCount)
                                }
                                if (!video.uploadedDate.isNullOrEmpty()) {
                                    append(" • ")
                                    append(video.uploadedDate)
                                }
                            },
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
