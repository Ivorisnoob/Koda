package com.ivor.ivormusic.ui.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LocalVideo
import com.ivor.ivormusic.data.LocalVideoAccess
import com.ivor.ivormusic.data.LocalVideoFolder
import com.ivor.ivormusic.data.LocalVideoRepository
import com.ivor.ivormusic.data.LocalVideoSort
import com.ivor.ivormusic.data.LocalVideoThumbnail
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh

/**
 * "On this device": one entry for the whole library, then a card per folder.
 *
 * Folders rather than a flat list because that is how a phone's videos are
 * actually organised in the owner's head - camera clips, downloads, a
 * messaging app's saved videos and a couple of films are four different kinds
 * of thing that happen to share a file type - and a device with four hundred
 * clips is otherwise one unbroken scroll.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceVideosRoot(
    videos: List<LocalVideo>,
    folders: List<LocalVideoFolder>,
    access: LocalVideoAccess,
    isLoading: Boolean,
    hasScanned: Boolean,
    onRequestAccess: () -> Unit,
    onOpenAll: () -> Unit,
    onOpenFolder: (LocalVideoFolder) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (access == LocalVideoAccess.DENIED) {
        DeviceVideoAccessWall(onRequestAccess = onRequestAccess, contentPadding = contentPadding)
        return
    }

    ExpressivePullToRefresh(
        isRefreshing = isLoading && videos.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (access == LocalVideoAccess.PARTIAL) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PartialAccessNotice(onRequestAccess = onRequestAccess)
                }
            }

            when {
                // The first scan and a refresh over an existing list are
                // different states: only the first has nothing to show behind
                // the spinner, and the pull-to-refresh indicator covers the
                // other.
                isLoading && videos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(48.dp))
                    }
                }

                hasScanned && videos.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    DeviceVideosEmpty(
                        access = access,
                        onRequestAccess = onRequestAccess,
                    )
                }

                else -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AllDeviceVideosCard(count = videos.size, onClick = onOpenAll)
                    }
                    // The count-based member, not the list extension: inside a
                    // LazyGridScope the member shadows it and resolution fails
                    // on the lambda's parameter type rather than falling
                    // through - the same trap ChannelTabs routes around.
                    items(
                        count = folders.size,
                        key = { index ->
                            folders[index].bucketId ?: folders[index].name.hashCode().toLong()
                        }
                    ) { index ->
                        val folder = folders[index]
                        DeviceFolderCard(folder = folder, onClick = { onOpenFolder(folder) })
                    }
                }
            }
        }
    }
}

/**
 * The device's videos as a self-contained screen: folder grid, and one step in
 * to a folder's contents.
 *
 * Owning that step here rather than in the Library's page enum is what lets
 * local-only mode show the same thing: with YouTube switched off the video
 * Library *is* this, and it would otherwise be a notice explaining that the
 * tab does not work.
 */
@Composable
internal fun DeviceVideosScreen(
    videos: List<LocalVideo>,
    folders: List<LocalVideoFolder>,
    access: LocalVideoAccess,
    isLoading: Boolean,
    hasScanned: Boolean,
    sort: LocalVideoSort,
    onSortChange: (LocalVideoSort) -> Unit,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (List<LocalVideo>, LocalVideo) -> Unit,
    contentPadding: PaddingValues,
    /** Rendered above the folder grid; the Library supplies its own back bar. */
    topBar: @Composable () -> Unit = {},
    /** Title bar for a folder, given the folder's display name. */
    folderTopBar: @Composable (String, () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // null is the folder grid; ALL_VIDEOS is every file on the device.
    var open by remember { mutableStateOf<LocalVideoFolder?>(null) }
    var showingAll by remember { mutableStateOf(false) }
    val inList = showingAll || open != null

    BackHandler(enabled = inList) {
        showingAll = false
        open = null
    }

    if (!inList) {
        Column(modifier = modifier.fillMaxSize()) {
            topBar()
            DeviceVideosRoot(
                videos = videos,
                folders = folders,
                access = access,
                isLoading = isLoading,
                hasScanned = hasScanned,
                onRequestAccess = onRequestAccess,
                onOpenAll = { showingAll = true },
                onOpenFolder = { open = it },
                onRefresh = onRefresh,
                contentPadding = contentPadding,
            )
        }
        return
    }

    val folder = open
    // Recomputed from the live list rather than captured when the folder was
    // opened, so a refresh that finds a new recording shows it here too.
    val folderVideos = remember(videos, folder) {
        if (folder == null) videos else LocalVideoRepository.videosIn(videos, folder)
    }
    Column(modifier = modifier.fillMaxSize()) {
        folderTopBar(folder?.name ?: stringResource(R.string.dv_all_videos)) {
            showingAll = false
            open = null
        }
        DeviceVideoListPage(
            videos = folderVideos,
            sort = sort,
            onSortChange = onSortChange,
            isLoading = isLoading,
            onPlay = { video ->
                // The list as sorted on screen becomes the queue, so "next"
                // means the row underneath the one that was tapped rather than
                // an unrelated file order.
                onPlay(LocalVideoRepository.sorted(folderVideos, sort), video)
            },
            onRefresh = onRefresh,
            contentPadding = contentPadding,
        )
    }
}

/**
 * A folder's videos, or every video on the device.
 *
 * The sort menu lives here rather than on the root because it is a property of
 * a list: "longest first" means nothing applied to a set of folder cards.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceVideoListPage(
    videos: List<LocalVideo>,
    sort: LocalVideoSort,
    onSortChange: (LocalVideoSort) -> Unit,
    isLoading: Boolean,
    onPlay: (LocalVideo) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
) {
    val sorted = remember(videos, sort) { LocalVideoRepository.sorted(videos, sort) }

    ExpressivePullToRefresh(
        isRefreshing = isLoading && videos.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                DeviceVideoListHeader(
                    count = sorted.size,
                    sort = sort,
                    onSortChange = onSortChange,
                )
            }
            if (sorted.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.dv_folder_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(sorted, key = { it.id }) { video ->
                DeviceVideoRow(video = video, onClick = { onPlay(video) })
            }
        }
    }
}

/**
 * Back bar for a folder page, for hosts that do not already have one of their
 * own. The Library passes its own SubPageTopBar instead, so the two drill-ins
 * in that tab look identical.
 */
@Composable
internal fun DeviceFolderTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeviceVideoListHeader(
    count: Int,
    sort: LocalVideoSort,
    onSortChange: (LocalVideoSort) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralVideoCount(count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(
                onClick = { menuOpen = true },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.dv_sort))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                LocalVideoSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option)) },
                        leadingIcon = if (option == sort) {
                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            onSortChange(option)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun sortLabel(sort: LocalVideoSort): String = when (sort) {
    LocalVideoSort.RECENT -> stringResource(R.string.dv_sort_recent)
    LocalVideoSort.NAME -> stringResource(R.string.dv_sort_name)
    LocalVideoSort.DURATION -> stringResource(R.string.dv_sort_duration)
    LocalVideoSort.SIZE -> stringResource(R.string.dv_sort_size)
}

@Composable
private fun AllDeviceVideosCard(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.dv_all_videos),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = pluralVideoCount(count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceFolderCard(folder: LocalVideoFolder, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            folder.coverUri?.let { uri ->
                AsyncImage(
                    model = LocalVideoThumbnail(uri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            // Sits over whatever frame loaded, so the count stays legible on a
            // bright thumbnail as well as on the empty placeholder behind it.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.65f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = folder.videoCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = folder.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun DeviceVideoRow(video: LocalVideo, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            // Drawn under the frame rather than instead of it, so a file whose
            // thumbnail could not be decoded still reads as a video rather than
            // as an empty rectangle.
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp),
            )
            AsyncImage(
                model = LocalVideoThumbnail(video.uri),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.8f),
            ) {
                Text(
                    text = formatDeviceVideoDuration(video.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = deviceVideoSubtitle(video),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The hand-off to whatever else on the phone can open a video. Koda
        // deliberately offers no delete of its own: that belongs to a file
        // manager or gallery, which already has the system's confirmation and
        // the user's own trash behind it - see openVideoWithExternalApp.
        androidx.compose.material3.IconButton(
            onClick = { openVideoWithExternalApp(context, video.uri, video.mimeType) }
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.dv_open_with),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "1080p - 412 MB - 3 days ago", skipping whatever the file did not report. */
private fun deviceVideoSubtitle(video: LocalVideo): String = buildList {
    video.resolutionLabel?.let { add(it) }
    LocalVideoRepository.formatSize(video.sizeBytes).takeIf { it.isNotEmpty() }?.let { add(it) }
    video.dateAddedMs?.let { add(com.ivor.ivormusic.data.VideoItem.formatRelativeTime(it)) }
}.joinToString(" - ")

private fun formatDeviceVideoDuration(durationMs: Long): String {
    val total = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
    else String.format("%d:%02d", minutes, seconds)
}

@Composable
private fun pluralVideoCount(count: Int): String =
    if (count == 1) stringResource(R.string.dv_one_video)
    else stringResource(R.string.dv_many_videos, count)

/**
 * Shown in place of the page when there is no access at all. A full-page wall
 * rather than a card, because with nothing readable there is nothing for a card
 * to sit above.
 */
@Composable
private fun DeviceVideoAccessWall(
    onRequestAccess: () -> Unit,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.dv_on_this_device),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.dv_access_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            FilledTonalButton(onClick = onRequestAccess) {
                Text(stringResource(R.string.dv_allow_access))
            }
        }
    }
}

/**
 * The Android 14 partial-grant banner.
 *
 * Deliberately a notice above a working list rather than a wall in front of
 * one: the user did grant access, to the files they chose, and those files are
 * below. All this adds is the way to widen the selection, which otherwise
 * exists only in system settings.
 */
@Composable
private fun PartialAccessNotice(onRequestAccess: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dv_partial_access),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestAccess) {
                Text(stringResource(R.string.dv_select_more))
            }
        }
    }
}

@Composable
private fun DeviceVideosEmpty(
    access: LocalVideoAccess,
    onRequestAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.dv_no_videos),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        // Under a partial grant an empty list usually means the picker was
        // dismissed without choosing anything, which is fixable from here.
        if (access == LocalVideoAccess.PARTIAL) {
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onRequestAccess) {
                Text(stringResource(R.string.dv_select_videos))
            }
        }
    }
}
