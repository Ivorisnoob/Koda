package com.ivor.ivormusic.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.ivor.ivormusic.data.DownloadMediaType
import com.ivor.ivormusic.data.DownloadProgress
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.DownloadStatus
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlaylistDownloadKind(
    val itemName: String,
    val itemNamePlural: String
) {
    MUSIC("track", "tracks"),
    VIDEO("video", "videos")
}

private data class PlaylistDownloadSnapshot(
    val eligibleIds: Set<String>,
    val completedIds: Set<String>,
    val active: Map<String, DownloadProgress>,
    val failedIds: Set<String>,
    val localOfflineCount: Int,
    val skippedCount: Int
) {
    val completedCount: Int get() = completedIds.size + localOfflineCount
    val activeCount: Int get() = active.size
    val remainingIds: Set<String>
        get() = eligibleIds - completedIds - active.keys
    val remainingCount: Int get() = remainingIds.size
    val isComplete: Boolean
        get() = (eligibleIds.isNotEmpty() || localOfflineCount > 0) &&
            completedIds.containsAll(eligibleIds) && skippedCount == 0

    val overallProgress: Float
        get() {
            if (eligibleIds.isEmpty()) return if (localOfflineCount > 0) 1f else 0f
            val total = eligibleIds.sumOf { id ->
                when {
                    id in completedIds -> 1.0
                    else -> active[id]?.progress?.toDouble() ?: 0.0
                }
            }
            return (total / eligibleIds.size).toFloat().coerceIn(0f, 1f)
        }
}

/**
 * Download entry point for a music playlist or album.
 *
 * Device-local songs are already offline, while YouTube songs are deduplicated
 * by id before they reach the worker. The visible count is therefore the number
 * of files still needed, not the number of repeated rows in the playlist.
 */
@Composable
fun MusicPlaylistDownloadAction(
    playlistTitle: String,
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { DownloadRepository.getInstance(context) }
    val downloadedSongs by repository.downloadedSongs.collectAsState()
    val progress by repository.downloadProgress.collectAsState()
    var showSheet by remember(playlistTitle) { mutableStateOf(false) }

    val eligibleSongs = remember(songs) {
        songs.filter { it.source == SongSource.YOUTUBE && it.id.isNotBlank() }
            .distinctBy { it.id }
    }
    val eligibleIds = remember(eligibleSongs) { eligibleSongs.mapTo(linkedSetOf()) { it.id } }
    val downloadedIds = remember(downloadedSongs, eligibleIds) {
        downloadedSongs.asSequence().map { it.id }.filter { it in eligibleIds }.toSet()
    }
    val relevantProgress = remember(progress, eligibleIds) {
        progress.values.filter {
            it.request.type == DownloadMediaType.MUSIC && it.request.id in eligibleIds
        }.associateBy { it.request.id }
    }
    val snapshot = remember(eligibleIds, downloadedIds, relevantProgress) {
        PlaylistDownloadSnapshot(
            eligibleIds = eligibleIds,
            completedIds = downloadedIds,
            active = relevantProgress.filterValues {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            },
            failedIds = relevantProgress.filterValues { it.status == DownloadStatus.FAILED }.keys,
            localOfflineCount = songs.count { it.source == SongSource.LOCAL },
            skippedCount = songs.count { it.source == SongSource.YOUTUBE && it.id.isBlank() }
        )
    }

    PlaylistDownloadButton(
        kind = PlaylistDownloadKind.MUSIC,
        snapshot = snapshot,
        enabled = songs.isNotEmpty(),
        onClick = { showSheet = true },
        modifier = modifier
    )

    if (showSheet) {
        PlaylistDownloadSheet(
            playlistTitle = playlistTitle,
            kind = PlaylistDownloadKind.MUSIC,
            playlistItemCountLabel = itemCountLabel(songs.size, PlaylistDownloadKind.MUSIC),
            snapshot = snapshot,
            onDismiss = { showSheet = false },
            onQueue = {
                repository.downloadPlaylist(eligibleSongs)
                true
            }
        )
    }
}

/** Download entry point for a video playlist, including a per-batch quality cap. */
@Composable
fun VideoPlaylistDownloadAction(
    playlistId: String,
    playlistTitle: String,
    videos: List<VideoItem>,
    playlistCountText: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { DownloadRepository.getInstance(context) }
    val downloadedVideos by repository.downloadedVideos.collectAsState()
    val progress by repository.downloadProgress.collectAsState()
    var showSheet by remember(playlistTitle) { mutableStateOf(false) }

    val eligibleVideos = remember(videos) {
        videos.filter { !it.isLive && it.videoId.isNotBlank() }.distinctBy { it.videoId }
    }
    val eligibleIds = remember(eligibleVideos) { eligibleVideos.mapTo(linkedSetOf()) { it.videoId } }
    val downloadedIds = remember(downloadedVideos, eligibleIds) {
        downloadedVideos.asSequence().map { it.id }.filter { it in eligibleIds }.toSet()
    }
    val relevantProgress = remember(progress, eligibleIds) {
        progress.values.filter {
            it.request.type == DownloadMediaType.VIDEO && it.request.id in eligibleIds
        }.associateBy { it.request.id }
    }
    val snapshot = remember(eligibleIds, downloadedIds, relevantProgress, videos) {
        PlaylistDownloadSnapshot(
            eligibleIds = eligibleIds,
            completedIds = downloadedIds,
            active = relevantProgress.filterValues {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            },
            failedIds = relevantProgress.filterValues { it.status == DownloadStatus.FAILED }.keys,
            localOfflineCount = 0,
            skippedCount = videos.count { it.isLive || it.videoId.isBlank() }
        )
    }

    PlaylistDownloadButton(
        kind = PlaylistDownloadKind.VIDEO,
        snapshot = snapshot,
        enabled = videos.isNotEmpty(),
        onClick = { showSheet = true },
        modifier = modifier
    )

    if (showSheet) {
        PlaylistDownloadSheet(
            playlistTitle = playlistTitle,
            kind = PlaylistDownloadKind.VIDEO,
            playlistItemCountLabel = playlistCountText
                ?.takeIf { it.isNotBlank() }
                ?: if (videos.size >= 100) "100+ videos"
                else itemCountLabel(videos.size, PlaylistDownloadKind.VIDEO),
            snapshot = snapshot,
            onDismiss = { showSheet = false },
            onQueue = { quality ->
                repository.downloadVideoPlaylist(playlistId, eligibleVideos, quality)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistDownloadButton(
    kind: PlaylistDownloadKind,
    snapshot: PlaylistDownloadSnapshot,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canOpen = enabled && snapshot.eligibleIds.isNotEmpty() && !snapshot.isComplete
    val label = when {
        snapshot.isComplete -> "Available offline"
        snapshot.activeCount > 0 && kind == PlaylistDownloadKind.VIDEO -> "Downloading playlist"
        snapshot.activeCount > 0 -> "Downloading ${snapshot.activeCount}"
        snapshot.remainingCount > 0 && snapshot.remainingIds.all { it in snapshot.failedIds } ->
            "Retry ${snapshot.remainingCount}"
        snapshot.remainingCount > 0 && kind == PlaylistDownloadKind.VIDEO -> "Download playlist"
        snapshot.remainingCount > 0 -> "Download ${snapshot.remainingCount}"
        snapshot.skippedCount > 0 -> "Unavailable offline"
        else -> "Download ${kind.itemNamePlural}"
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = canOpen,
        modifier = modifier,
        shapes = ButtonDefaults.shapes()
    ) {
        AnimatedContent(
            targetState = when {
                snapshot.isComplete -> "complete"
                snapshot.activeCount > 0 -> "active"
                snapshot.eligibleIds.isEmpty() -> "unavailable"
                else -> "ready"
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "playlistDownloadState"
        ) { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (state) {
                    "complete" -> Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    "active" -> CircularWavyProgressIndicator(
                        progress = { snapshot.overallProgress },
                        modifier = Modifier
                            .size(20.dp)
                            .semantics {
                                contentDescription = "Downloading playlist"
                                progressBarRangeInfo = ProgressBarRangeInfo(
                                    snapshot.overallProgress,
                                    0f..1f
                                )
                            }
                    )
                    "unavailable" -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                    else -> Icon(Icons.Rounded.Download, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
private fun PlaylistDownloadSheet(
    playlistTitle: String,
    kind: PlaylistDownloadKind,
    playlistItemCountLabel: String,
    snapshot: PlaylistDownloadSnapshot,
    onDismiss: () -> Unit,
    onQueue: suspend (qualityLabel: String?) -> Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val preferences = remember(context) { ThemePreferences(context) }
    // This is a compact task with its own scrolling body and pinned action. Opening
    // halfway hides the quality and summary context while making the first scroll
    // gesture resize the sheet, so enter directly at the expanded anchor.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedQuality by remember {
        mutableStateOf(preferences.getDownloadVideoQuality())
    }
    var rememberQuality by remember { mutableStateOf(false) }
    var queued by remember { mutableStateOf(false) }
    var queueing by remember { mutableStateOf(false) }
    var queueFailed by remember { mutableStateOf(false) }
    val localOnly = remember { ThemePreferences.isLocalOnly(context) }

    LaunchedEffect(queued) {
        if (queued) {
            delay(850)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Download playlist",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = playlistTitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            DownloadSummaryRow("In playlist", playlistItemCountLabel)
                            if (kind == PlaylistDownloadKind.VIDEO) {
                                DownloadSummaryRow(
                                    "Loaded now",
                                    itemCountLabel(
                                        snapshot.eligibleIds.size + snapshot.skippedCount,
                                        kind
                                    )
                                )
                                DownloadSummaryRow(
                                    "Already offline here",
                                    itemCountLabel(snapshot.completedCount, kind)
                                )
                                Text(
                                    text = "Every remaining playlist page is resolved after you confirm; existing downloads are skipped then too.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                DownloadSummaryRow(
                                    "Already offline",
                                    itemCountLabel(snapshot.completedCount, kind)
                                )
                                DownloadSummaryRow(
                                    "To download",
                                    itemCountLabel(snapshot.remainingCount, kind)
                                )
                            }
                            if (snapshot.activeCount > 0) {
                                DownloadSummaryRow(
                                    "In progress",
                                    itemCountLabel(snapshot.activeCount, kind)
                                )
                                LinearWavyProgressIndicator(
                                    progress = { snapshot.overallProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = "Playlist download progress"
                                            progressBarRangeInfo = ProgressBarRangeInfo(
                                                snapshot.overallProgress,
                                                0f..1f
                                            )
                                        }
                                )
                            }
                        }
                    }
                }

                if (kind == PlaylistDownloadKind.VIDEO) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Video quality",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Each video uses the best MP4 quality at or below this limit.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemePreferences.VIDEO_QUALITY_OPTIONS.forEach { quality ->
                                    BatchQualityPill(
                                        label = if (quality == ThemePreferences.VIDEO_QUALITY_AUTO) {
                                            "Best available"
                                        } else quality,
                                        selected = selectedQuality == quality,
                                        onClick = { selectedQuality = quality }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { rememberQuality = !rememberQuality }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Remember this quality",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Use it for future video downloads",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = rememberQuality,
                                    onCheckedChange = { rememberQuality = it }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        val musicQuality = remember {
                            ThemePreferences.currentMusicQuality(context)
                        }
                        Text(
                            text = "Music uses your ${musicQuality.replaceFirstChar { it.uppercase() }} quality setting for this network.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (snapshot.skippedCount > 0 || localOnly) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (localOnly) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (localOnly) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = when {
                                        localOnly -> "Turn off Local only mode before starting a download."
                                        kind == PlaylistDownloadKind.VIDEO ->
                                            "${itemCountLabel(snapshot.skippedCount, kind)} can't be downloaded while live and will be skipped."
                                        else ->
                                            "${itemCountLabel(snapshot.skippedCount, kind)} has no downloadable source and will be skipped."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (localOnly) MaterialTheme.colorScheme.onErrorContainer
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (queueFailed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Couldn't load the complete playlist. Check your connection and try again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Downloads continue in the background. Existing downloads and repeated items are skipped automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        queueing = true
                        queueFailed = false
                        scope.launch {
                            val success = onQueue(
                                selectedQuality.takeIf { kind == PlaylistDownloadKind.VIDEO }
                            )
                            queueing = false
                            if (success) {
                                if (kind == PlaylistDownloadKind.VIDEO && rememberQuality) {
                                    preferences.setDownloadVideoQuality(selectedQuality)
                                }
                                queued = true
                            } else {
                                queueFailed = true
                            }
                        }
                    },
                    enabled = snapshot.remainingCount > 0 && !queued && !queueing && !localOnly,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    val label = when {
                        queued -> "Added to downloads"
                        queueing -> if (kind == PlaylistDownloadKind.VIDEO) {
                            "Loading full playlist..."
                        } else "Adding to downloads..."
                        localOnly -> "Local only mode is on"
                        snapshot.isComplete -> "Already available offline"
                        snapshot.remainingCount == 0 && snapshot.activeCount > 0 -> "Downloading"
                        snapshot.remainingIds.all { it in snapshot.failedIds } ->
                            "Retry ${itemCountLabel(snapshot.remainingCount, kind)}"
                        kind == PlaylistDownloadKind.VIDEO -> "Download full playlist"
                        else -> "Download ${itemCountLabel(snapshot.remainingCount, kind)}"
                    }
                    if (queueing) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(
                            imageVector = if (queued || snapshot.isComplete) Icons.Rounded.CheckCircle
                                else Icons.Rounded.Download,
                            contentDescription = null
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DownloadSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

private fun itemCountLabel(count: Int, kind: PlaylistDownloadKind): String =
    "$count ${if (count == 1) kind.itemName else kind.itemNamePlural}"

@Composable
private fun BatchQualityPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f),
        label = "batchQualityScale"
    )
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "batchQualityColor"
    )
    Surface(
        shape = RoundedCornerShape(if (selected) 14.dp else 24.dp),
        color = color,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
