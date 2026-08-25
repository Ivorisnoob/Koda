package com.ivor.ivormusic.ui.downloads
import com.ivor.ivormusic.R
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.DownloadMediaType
import com.ivor.ivormusic.data.DownloadProgress
import com.ivor.ivormusic.data.DownloadRequest
import com.ivor.ivormusic.data.DownloadStatus
import com.ivor.ivormusic.data.DownloadedVideo
import com.ivor.ivormusic.data.Song

private enum class DownloadsTab(val label: String) {
    MUSIC("Music"),
    VIDEO("Video")
}

/** Outer radius of a connected group. Matches AlbumScreen's track list. */
private val SEGMENT_CORNER = 28.dp

/**
 * Shape for one row of a connected list: only the group's outer corners are
 * rounded, so consecutive rows read as a single container rather than a stack
 * of separate cards. A lone item is rounded on all four.
 */
private fun segmentedShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(SEGMENT_CORNER)
    index == 0 -> RoundedCornerShape(topStart = SEGMENT_CORNER, topEnd = SEGMENT_CORNER)
    index == count - 1 -> RoundedCornerShape(
        bottomStart = SEGMENT_CORNER,
        bottomEnd = SEGMENT_CORNER
    )
    else -> RectangleShape
}

/**
 * Hairline between connected rows, inset past the artwork so it aligns with the
 * text column rather than cutting the whole row in half.
 */
@Composable
private fun SegmentDivider(inset: Dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    )
}

/**
 * Downloads library, split by media type.
 *
 * Music and video are genuinely different things - one feeds the queue, the
 * other opens the video player - so they get separate tabs rather than one
 * mixed list. In-flight transfers appear under the tab they belong to, so a
 * video downloading while the Music tab is open is not silently invisible.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    downloadedVideos: List<DownloadedVideo>,
    activeDownloads: Map<String, DownloadProgress>,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayQueue: (List<Song>, Song) -> Unit = { _, song -> onPlaySong(song) },
    onPlayVideo: (List<DownloadedVideo>, DownloadedVideo) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (DownloadRequest) -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(DownloadsTab.MUSIC) }

    // Split by the media type of the request itself rather than by which list
    // the finished item lands in, so queued and failed entries route correctly
    // before anything has completed.
    val musicProgress = activeDownloads.values
        .filter { it.request.type == DownloadMediaType.MUSIC }
        .sortedBy { it.status.ordinal }
    val videoProgress = activeDownloads.values
        .filter { it.request.type == DownloadMediaType.VIDEO }
        .sortedBy { it.status.ordinal }

    val activeCount = activeDownloads.values.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.fab_downloads))
                        Text(
                            text = buildString {
                                append("${downloadedSongs.size} songs")
                                append(" • ${downloadedVideos.size} videos")
                                if (activeCount > 0) append(" • $activeCount active")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeCount > 0) {
                        TextButton(onClick = onCancelAll) { Text(stringResource(R.string.stop_all)) }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // M3 Expressive connected button group, matching LibraryScreen's
            // view switcher.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                DownloadsTab.entries.forEachIndexed { index, tab ->
                    val selected = selectedTab == tab
                    val pending = if (tab == DownloadsTab.MUSIC) {
                        musicProgress.size
                    } else {
                        videoProgress.size
                    }
                    ToggleButton(
                        checked = selected,
                        onCheckedChange = { selectedTab = tab },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            DownloadsTab.entries.lastIndex ->
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (tab == DownloadsTab.MUSIC) {
                                Icons.Rounded.MusicNote
                            } else {
                                Icons.Rounded.Videocam
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pending > 0) "${tab.label} ($pending)" else tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "downloads_tab"
            ) { tab ->
                when (tab) {
                    DownloadsTab.MUSIC -> MusicTab(
                        songs = downloadedSongs,
                        progress = musicProgress,
                        onPlayQueue = onPlayQueue,
                        onDelete = onDeleteDownload,
                        onCancel = onCancelDownload,
                        onRetry = onRetryDownload
                    )

                    DownloadsTab.VIDEO -> VideoTab(
                        videos = downloadedVideos,
                        progress = videoProgress,
                        onPlay = { video -> onPlayVideo(downloadedVideos, video) },
                        onDelete = onDeleteVideo,
                        onCancel = onCancelDownload,
                        onRetry = onRetryDownload
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicTab(
    songs: List<Song>,
    progress: List<DownloadProgress>,
    onPlayQueue: (List<Song>, Song) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (DownloadRequest) -> Unit
) {
    // No spacedBy: rows in a connected group sit flush and are separated by
    // dividers, not gaps. Section spacing is added explicitly instead.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (progress.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.in_progress)) }
            itemsIndexed(progress, key = { _, it -> "p_${it.songId}" }) { index, item ->
                ProgressCard(
                    item = item,
                    shape = segmentedShape(index, progress.size),
                    onCancel = onCancel,
                    onRetry = onRetry
                )
                if (index < progress.lastIndex) SegmentDivider(inset = 46.dp)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (songs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.song_options_downloaded)) }
            itemsIndexed(songs, key = { _, it -> "s_${it.id}" }) { index, song ->
                DownloadedRow(
                    title = song.title,
                    subtitle = song.artist,
                    artworkUrl = song.thumbnailUrl ?: song.albumArtUri?.toString(),
                    fallbackIcon = Icons.Rounded.MusicNote,
                    shape = segmentedShape(index, songs.size),
                    onPlay = { onPlayQueue(songs, song) },
                    onDelete = { onDelete(song.id) }
                )
                if (index < songs.lastIndex) SegmentDivider(inset = 74.dp)
            }
        }

        if (songs.isEmpty() && progress.isEmpty()) {
            item { EmptyState("No downloaded music", Icons.Rounded.MusicNote) }
        }
    }
}

@Composable
private fun VideoTab(
    videos: List<DownloadedVideo>,
    progress: List<DownloadProgress>,
    onPlay: (DownloadedVideo) -> Unit,
    onDelete: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (DownloadRequest) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        if (progress.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.in_progress)) }
            itemsIndexed(progress, key = { _, it -> "p_${it.songId}" }) { index, item ->
                ProgressCard(
                    item = item,
                    shape = segmentedShape(index, progress.size),
                    onCancel = onCancel,
                    onRetry = onRetry
                )
                if (index < progress.lastIndex) SegmentDivider(inset = 46.dp)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (videos.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.song_options_downloaded)) }
            itemsIndexed(videos, key = { _, it -> "v_${it.id}" }) { index, video ->
                DownloadedRow(
                    title = video.title,
                    subtitle = listOfNotNull(video.channelName, video.quality)
                        .filter { it.isNotBlank() }
                        .joinToString(" • "),
                    artworkUrl = video.thumbnailUrl,
                    fallbackIcon = Icons.Rounded.Videocam,
                    shape = segmentedShape(index, videos.size),
                    wideArtwork = true,
                    onPlay = { onPlay(video) },
                    onDelete = { onDelete(video.id) }
                )
                if (index < videos.lastIndex) SegmentDivider(inset = 110.dp)
            }
        }

        if (videos.isEmpty() && progress.isEmpty()) {
            item { EmptyState("No downloaded videos", Icons.Rounded.Videocam) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun ProgressCard(
    item: DownloadProgress,
    shape: Shape,
    onCancel: (String) -> Unit,
    onRetry: (DownloadRequest) -> Unit
) {
    val failed = item.status == DownloadStatus.FAILED
    val queued = item.status == DownloadStatus.QUEUED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        failed -> Icons.Rounded.ErrorOutline
                        queued -> Icons.Rounded.Schedule
                        else -> Icons.Rounded.Download
                    },
                    contentDescription = null,
                    tint = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.request.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            failed -> stringResource(R.string.dl_failed)
                            queued -> stringResource(R.string.dl_waiting)
                            item.totalBytes > 0 -> "%.1f / %.1f MB".format(
                                item.bytesDownloaded / (1024 * 1024f),
                                item.totalBytes / (1024 * 1024f)
                            )
                            else -> stringResource(R.string.dl_preparing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (failed) {
                    IconButton(onClick = { onRetry(item.request) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                    }
                }
                IconButton(onClick = { onCancel(item.songId) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                }
            }

            if (!failed) {
                Spacer(modifier = Modifier.height(10.dp))
                if (queued) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    shape: Shape,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    wideArtwork: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Clip before clickable so the ripple follows the segment shape
            // instead of bleeding past the rounded corners.
            .clip(shape)
            .clickable(onClick = onPlay),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = if (wideArtwork) 88.dp else 52.dp, height = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
