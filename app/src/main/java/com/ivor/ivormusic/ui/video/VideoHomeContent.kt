package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VideoLibrary
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import coil.compose.AsyncImage
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
    shorts: List<ShortsItem> = emptyList(),
    onShortClick: (Int) -> Unit = {},
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit = {},
    onRefresh: () -> Unit,
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
    listState: LazyListState = rememberLazyListState()
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val showOfflineDownloads = isOffline && videos.isEmpty() && downloadedVideos.isNotEmpty()

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
            val isLoadingMore by viewModel.isVideoLoadingMore.collectAsState()

            // Endless feed: ask for the next page whenever the last visible
            // item is within 5 of the end. The ViewModel guards against
            // duplicate/exhausted loads, so firing on every scroll frame is fine.
            LaunchedEffect(listState) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
                }.collect { (lastVisible, totalCount) ->
                    if (totalCount > 0 && lastVisible >= totalCount - 5) {
                        viewModel.loadMoreTrendingVideos()
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                item {
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
                            video = downloaded.asOfflineVideoItem(),
                            onClick = { onDownloadedVideoClick(downloaded) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    // Video cards, with the Shorts shelf slotted in after the
                    // first two like the YouTube home feed.
                    val leadingVideos = if (shorts.isEmpty()) videos else videos.take(2)
                    val trailingVideos = if (shorts.isEmpty()) emptyList() else videos.drop(2)

                    items(leadingVideos) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onLongClick = { onVideoLongPress(video) },
                            onOpenChannel = onOpenChannel,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (shorts.isNotEmpty()) {
                        item {
                            ShortsShelf(
                                shorts = shorts,
                                onShortClick = onShortClick
                            )
                        }
                    }

                    items(trailingVideos) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onLongClick = { onVideoLongPress(video) },
                            onOpenChannel = onOpenChannel,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                
                // Empty state
                if (videos.isEmpty() && !isLoading && !showOfflineDownloads) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (isOffline) stringResource(R.string.vh_youre_offline) else stringResource(R.string.vh_no_videos_found),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isOffline) {
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
private fun VideoTopBarSection(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    viewModel: HomeViewModel,
    videoMode: Boolean = true,
    onVideoModeToggle: (Boolean) -> Unit = {},
    showModeToggle: Boolean = true,
    modeToggleState: MusicVideoToggleState = rememberMusicVideoToggleState(videoMode)
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val iconColor = MaterialTheme.colorScheme.onSurface
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    
    val userAvatar by viewModel.userAvatar.collectAsState()
    val downloadingIds by viewModel.downloadingIds.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile avatar
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
        
        // Right side icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Notifications Button
            IconButton(
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                    contentDescription = item.title,
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
    onOpenChannel: ((String) -> Unit)? = null
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardShape = RoundedCornerShape(16.dp)
    val openChannel = onOpenChannel?.let { open ->
        { open(video.channelNavigationReference) }
    }

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
        Column {
            // Thumbnail with duration overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = video.highResThumbnailUrl ?: video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
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
                
                // Duration badge (skip entirely when the duration is unknown)
                if (!video.isLive && video.duration > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (video.isLive) {
                    // Live badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF0000)
                    ) {
                        Text(
                            text = stringResource(R.string.badge_live),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
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
