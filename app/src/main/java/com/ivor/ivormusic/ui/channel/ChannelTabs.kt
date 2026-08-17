package com.ivor.ivormusic.ui.channel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.ChannelAbout
import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.ChannelShelf
import com.ivor.ivormusic.data.ChannelSortOption
import com.ivor.ivormusic.data.ChannelTab
import com.ivor.ivormusic.data.ChannelTabKind
import com.ivor.ivormusic.data.ChannelTabPage
import com.ivor.ivormusic.data.ShortsItem
import com.ivor.ivormusic.data.SubscribedChannel
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.ui.video.VideoCard

/**
 * The About entry the UI adds to the tab row. Carries no `params`, because
 * there is no browse behind it - see [ChannelTabKind.ABOUT].
 */
internal val ABOUT_TAB = ChannelTab(ChannelTabKind.ABOUT, "About", "")

/**
 * The body of whichever tab is open, emitted into the channel screen's single
 * grid.
 *
 * A `LazyGridScope` extension rather than a composable so the content shares
 * the header's scroller: a tab that owned its own scroller would mean the
 * header could not scroll away, which is the whole feel of the page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyGridScope.channelTabContent(
    tabKind: ChannelTabKind,
    page: ChannelTabPage?,
    isTabLoading: Boolean,
    about: ChannelAbout?,
    isAboutLoading: Boolean,
    onPlayVideo: (VideoItem) -> Unit,
    onVideoLongPress: (VideoItem) -> Unit,
    onOpenShorts: (List<ShortsItem>, Int) -> Unit,
    onOpenPlaylist: (VideoPlaylist) -> Unit,
    onOpenChannel: (String) -> Unit,
    onSelectSort: (ChannelSortOption) -> Unit
) {
    if (tabKind == ChannelTabKind.ABOUT) {
        aboutTab(about = about, isLoading = isAboutLoading)
        return
    }

    if (isTabLoading && (page == null || page.isEmpty)) {
        item(key = "tab_loading", span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(44.dp))
            }
        }
        return
    }

    if (page == null || page.isEmpty) {
        item(key = "tab_empty", span = { GridItemSpan(maxLineSpan) }) {
            ChannelEmptyState(tabKind = tabKind)
        }
        return
    }

    if (page.sortOptions.size > 1) {
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            ChannelSortRow(options = page.sortOptions, onSelect = onSelectSort)
        }
    }

    page.featured?.let { featured ->
        item(key = "featured_${featured.videoId}", span = { GridItemSpan(maxLineSpan) }) {
            FeaturedVideoCard(
                video = featured,
                onClick = { onPlayVideo(featured) },
                onLongClick = { onVideoLongPress(featured) }
            )
        }
    }

    // Home: the channel's own arrangement of shelves, in its order.
    page.shelves.forEachIndexed { index, shelf ->
        item(key = "shelf_${index}_${shelf.title}", span = { GridItemSpan(maxLineSpan) }) {
            ChannelShelfRow(
                shelf = shelf,
                onPlayVideo = onPlayVideo,
                onVideoLongPress = onVideoLongPress,
                onOpenShorts = onOpenShorts,
                onOpenPlaylist = onOpenPlaylist,
                onOpenChannel = onOpenChannel
            )
        }
    }

    spanItems(page.videos, key = { "video_${it.videoId}" }) { video ->
        VideoCard(
            video = video,
            onClick = { onPlayVideo(video) },
            onLongClick = { onVideoLongPress(video) }
        )
    }

    spanItemsIndexed(
        items = page.shorts,
        span = SHORT_SPAN,
        key = { _, short -> "short_${short.videoId}" }
    ) { index, short ->
        ShortCard(short = short, onClick = { onOpenShorts(page.shorts, index) })
    }

    spanItems(
        items = page.playlists,
        span = PLAYLIST_SPAN,
        key = { "playlist_${it.playlistId}" }
    ) { playlist ->
        ChannelPlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
    }

    spanItems(page.posts, key = { "post_${it.postId}" }) { post ->
        ChannelPostCard(post = post, onPlayVideo = onPlayVideo)
    }
}

/**
 * A list of items at a fixed span, or full width when [span] is null.
 *
 * These two helpers exist because `LazyGridScope` carries a `count`-based
 * `items` **member**, and a member shadows the list-taking extension of the
 * same name: calling `items(items = …, span = …)` resolves to the member and
 * fails on the argument names. Routing every list through the member once, here,
 * is less surprising than importing the extension and hoping resolution goes the
 * other way.
 */
private fun <T> LazyGridScope.spanItems(
    items: List<T>,
    span: Int? = null,
    key: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    items(
        count = items.size,
        key = { index -> key(items[index]) },
        span = { GridItemSpan(span ?: maxLineSpan) }
    ) { index ->
        itemContent(items[index])
    }
}

/**
 * The same, keeping the index. Shorts need it: tapping the fourth Short has to
 * open the reel at position four, not search the list for it by id, because a
 * channel can list the same Short twice.
 */
private fun <T> LazyGridScope.spanItemsIndexed(
    items: List<T>,
    span: Int,
    key: (Int, T) -> Any,
    itemContent: @Composable (Int, T) -> Unit
) {
    if (items.isEmpty()) return
    items(
        count = items.size,
        key = { index -> key(index, items[index]) },
        span = { GridItemSpan(span) }
    ) { index ->
        itemContent(index, items[index])
    }
}

private const val SHORT_SPAN = 2
private const val PLAYLIST_SPAN = 3

/**
 * The sort chips ("Latest", "Popular", "Oldest").
 *
 * A menu rather than a chip row because the options are mutually exclusive and
 * there are never more than a handful, and because the current sort has to stay
 * visible while the grid scrolls past - which a row of chips scrolled off the
 * top cannot do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSortRow(
    options: List<ChannelSortOption>,
    onSelect: (ChannelSortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.selected } ?: options.first()

    Box(modifier = Modifier.padding(top = 4.dp)) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = current.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        if (!option.selected) onSelect(option)
                    }
                )
            }
        }
    }
}

/** The pinned video at the top of a channel's Home tab, given the space it asks for. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AsyncImage(
                    model = video.highResThumbnailUrl ?: video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "Featured",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.viewCount.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = video.viewCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** One Home-tab shelf, whatever it happens to contain. */
@Composable
private fun ChannelShelfRow(
    shelf: ChannelShelf,
    onPlayVideo: (VideoItem) -> Unit,
    onVideoLongPress: (VideoItem) -> Unit,
    onOpenShorts: (List<ShortsItem>, Int) -> Unit,
    onOpenPlaylist: (VideoPlaylist) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    Column(modifier = Modifier.bleedHorizontally().fillMaxWidth()) {
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = CHANNEL_GUTTER, bottom = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = CHANNEL_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shelf.videos, key = { "sv_${it.videoId}" }) { video ->
                ShelfVideoCard(
                    video = video,
                    onClick = { onPlayVideo(video) },
                    onLongClick = { onVideoLongPress(video) }
                )
            }
            itemsIndexed(shelf.shorts, key = { _, s -> "ss_${s.videoId}" }) { index, short ->
                ShortCard(
                    short = short,
                    onClick = { onOpenShorts(shelf.shorts, index) },
                    modifier = Modifier.width(130.dp)
                )
            }
            items(shelf.playlists, key = { "sp_${it.playlistId}" }) { playlist ->
                ChannelPlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) },
                    modifier = Modifier.width(180.dp)
                )
            }
            items(shelf.channels, key = { "sc_${it.channelId}" }) { channel ->
                ShelfChannelCard(channel = channel, onClick = { onOpenChannel(channel.channelId) })
            }
            items(shelf.posts, key = { "spo_${it.postId}" }) { post ->
                ChannelPostCard(
                    post = post,
                    onPlayVideo = onPlayVideo,
                    modifier = Modifier.width(300.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (video.duration > 0) {
                DurationBadge(
                    text = video.formattedDuration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        if (video.viewCount.isNotBlank()) {
            Text(
                text = listOfNotNull(
                    video.viewCount.takeIf { it.isNotBlank() },
                    video.uploadedDate?.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

@Composable
private fun ShelfChannelCard(channel: SubscribedChannel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickableCompat(onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CreatorAvatar(
            avatarUrl = channel.avatarUrl,
            name = channel.name,
            modifier = Modifier.size(76.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        channel.subscriberCountText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ShortCard(
    short: ShortsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .combinedClickableCompat(onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = short.portraitThumbnailUrl,
                contentDescription = short.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                if (short.title.isNotBlank()) {
                    Text(
                        text = short.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (short.viewCount.isNotBlank()) {
                    Text(
                        text = short.viewCount,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelPlaylistCard(
    playlist: VideoPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickableCompat(onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (playlist.thumbnailUrl != null) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            playlist.videoCountText?.let { count ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = count,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        playlist.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.8f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * What a tab says when it genuinely has nothing in it.
 *
 * Worded per tab rather than one "Nothing here": a channel with no posts and a
 * channel whose videos failed to load are different situations, and a single
 * message would describe one of them wrongly.
 */
@Composable
private fun ChannelEmptyState(tabKind: ChannelTabKind) {
    val (icon, message) = when (tabKind) {
        ChannelTabKind.SHORTS -> Icons.Rounded.PlayArrow to "This channel hasn't posted any Shorts"
        ChannelTabKind.LIVE -> Icons.Rounded.Visibility to "No past or upcoming live streams"
        ChannelTabKind.PLAYLISTS -> Icons.Rounded.VideoLibrary to "No public playlists"
        ChannelTabKind.POSTS -> Icons.AutoMirrored.Rounded.Article to "No community posts yet"
        ChannelTabKind.VIDEOS -> Icons.Rounded.VideoLibrary to "No videos here yet"
        else -> Icons.Rounded.VideoLibrary to "Nothing to show in this tab"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------

private fun LazyGridScope.aboutTab(about: ChannelAbout?, isLoading: Boolean) {
    if (isLoading && about == null) {
        item(key = "about_loading", span = { GridItemSpan(maxLineSpan) }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(44.dp))
            }
        }
        return
    }
    if (about == null) {
        item(key = "about_empty", span = { GridItemSpan(maxLineSpan) }) {
            ChannelEmptyState(tabKind = ChannelTabKind.OTHER)
        }
        return
    }

    if (!about.description.isNullOrBlank()) {
        item(key = "about_description", span = { GridItemSpan(maxLineSpan) }) {
            AboutCard(title = "Description") {
                Text(
                    text = about.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (about.links.isNotEmpty()) {
        item(key = "about_links", span = { GridItemSpan(maxLineSpan) }) {
            val uriHandler = LocalUriHandler.current
            AboutCard(title = "Links") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    about.links.forEach { link ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickableCompat {
                                    runCatching { uriHandler.openUri(link.url) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (link.faviconUrl != null) {
                                AsyncImage(
                                    model = link.faviconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = link.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    val stats = listOfNotNull(
        about.subscriberCountText?.let { Icons.Rounded.Person to it },
        about.videoCountText?.let { Icons.Rounded.VideoLibrary to it },
        about.viewCountText?.let { Icons.Rounded.Visibility to it },
        about.joinedDateText?.let { Icons.Rounded.CalendarMonth to it },
        about.country?.let { Icons.Rounded.Public to it }
    )
    if (stats.isNotEmpty()) {
        item(key = "about_stats", span = { GridItemSpan(maxLineSpan) }) {
            AboutCard(title = "Details") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.forEach { (icon, text) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Community posts
// ---------------------------------------------------------------------------

/**
 * One community post: text, then whichever single attachment it carried.
 *
 * Images are shown at their natural-ish ratio in a row rather than a grid,
 * because a multi-image post is a carousel on YouTube and cropping four photos
 * into equal squares loses what the post was about.
 */
@Composable
private fun ChannelPostCard(
    post: ChannelPost,
    onPlayVideo: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CreatorAvatar(
                    avatarUrl = post.authorAvatarUrl,
                    name = post.authorName,
                    modifier = Modifier.size(36.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    post.publishedText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!post.text.isBlank) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = post.text.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (post.images.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                if (post.images.size == 1) {
                    AsyncImage(
                        model = post.images.first(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(post.images) { image ->
                            AsyncImage(
                                model = image,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            post.video?.let { video ->
                Spacer(Modifier.height(12.dp))
                ShelfVideoCard(
                    video = video,
                    onClick = { onPlayVideo(video) },
                    onLongClick = {}
                )
            }

            if (post.pollChoices.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.pollChoices.forEach { choice ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = choice.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                    post.pollTotalText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val footer = listOfNotNull(post.voteCountText, post.replyCountText)
            if (footer.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    post.voteCountText?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ThumbUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    post.replyCountText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search within the channel
// ---------------------------------------------------------------------------

/**
 * Search over one creator's whole back catalogue.
 *
 * A full-surface mode rather than a tab, because it needs a text field and the
 * keyboard, and because on a channel with two thousand uploads it is the main
 * way anyone finds anything.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChannelSearchPane(
    channelName: String,
    query: String,
    results: List<VideoItem>,
    isSearching: Boolean,
    searchRan: Boolean,
    onQueryChange: (String) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onVideoLongPress: (VideoItem) -> Unit,
    onClose: () -> Unit,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(top = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = onClose,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search")
                }
            }
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search $channelName",
                modifier = Modifier.weight(1f)
            )
        }

        when {
            isSearching -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(modifier = Modifier.size(44.dp))
            }

            results.isEmpty() && searchRan -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Nothing in this channel matches \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            results.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Search everything $channelName has published",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = CHANNEL_GUTTER,
                    end = CHANNEL_GUTTER,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { it.videoId }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onLongClick = { onVideoLongPress(video) }
                    )
                }
            }
        }
    }
}

/**
 * `clickable` with the ripple clipped to the shape already applied above it,
 * matching the note on [VideoCard]: a Surface applies its own clip downstream,
 * so a click registered higher spills into square corners.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
