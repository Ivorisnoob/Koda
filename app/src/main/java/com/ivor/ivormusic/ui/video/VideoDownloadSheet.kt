package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.util.KLog

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.DownloadStatus
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoQuality
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bottom sheet for downloading a video: shows the downloadable quality
 * ladder as selectable pills, an optional "remember this quality" switch
 * that stores the pick as the default download quality, and a download
 * button that queues the transfer and dismisses itself.
 *
 * Qualities are fetched on open (one /player call, same resolution the
 * player itself does) and filtered to what the download pipeline can
 * actually produce: MP4 adaptive pairs the muxer accepts, with the muxed
 * progressive stream as the last resort. DASH and webm/HDR entries are
 * excluded on purpose — see DownloadRepository.attemptVideoDownload.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun VideoDownloadSheet(
    video: VideoItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) { ThemePreferences(context) }
    val youtubeRepository = remember(context) { YouTubeRepository(context) }
    val downloadRepository = remember(context) { DownloadRepository.getInstance(context) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var options by remember(video.videoId) { mutableStateOf<List<VideoQuality>?>(null) }
    var loadFailed by remember(video.videoId) { mutableStateOf(false) }
    var selectedLabel by remember(video.videoId) { mutableStateOf<String?>(null) }
    var rememberQuality by remember { mutableStateOf(false) }
    var queued by remember(video.videoId) { mutableStateOf(false) }

    val downloadedVideos by downloadRepository.downloadedVideos.collectAsState()
    val progressMap by downloadRepository.downloadProgress.collectAsState()
    val alreadyDownloaded = downloadedVideos.any { it.id == video.videoId }
    val inFlight = progressMap[video.videoId]?.status.let {
        it == DownloadStatus.DOWNLOADING || it == DownloadStatus.QUEUED
    }

    fun height(label: String): Int = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0

    LaunchedEffect(video.videoId) {
        try {
            val qualities = youtubeRepository.getVideoStreamQualities(video.videoId)
            // Same eligibility rules as the download worker: MP4 adaptive
            // pairs first, else the muxed progressive stream (typically 360p).
            val mp4Adaptive = qualities.filter {
                !it.isDASH && it.audioUrl != null && it.isMp4DownloadCompatible
            }
            val downloadable = mp4Adaptive.ifEmpty {
                qualities.filter { !it.isDASH && it.audioUrl == null && it.isMp4Container }
            }.distinctBy { it.resolution }
            options = downloadable
            loadFailed = downloadable.isEmpty() && qualities.isEmpty()

            // Preselect the stored default: best label at or below its height,
            // "auto" (or no match below) meaning the best available.
            val preferred = themePreferences.getDownloadVideoQuality()
            val targetHeight = height(preferred)
            selectedLabel = when {
                downloadable.isEmpty() -> null
                preferred == ThemePreferences.VIDEO_QUALITY_AUTO || targetHeight == 0 ->
                    downloadable.first().resolution
                else -> downloadable.firstOrNull { height(it.resolution) in 1..targetHeight }?.resolution
                    ?: downloadable.first().resolution
            }
        } catch (e: Exception) {
            KLog.w("VideoDownloadSheet", "Failed to load qualities", e)
            options = emptyList()
            loadFailed = true
        }
    }

    // Linger on the queued state for a beat, then close (same rhythm as the
    // save sheet's post-save dismissal)
    LaunchedEffect(queued) {
        if (queued) {
            delay(900)
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Download video",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!video.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(96.dp)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Quality",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
                options == null -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
                options.orEmpty().isEmpty() -> Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (loadFailed) "Couldn't load qualities. Check your connection."
                            else "No downloadable stream for this video",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.orEmpty().forEach { quality ->
                        QualityPill(
                            label = quality.resolution,
                            selected = quality.resolution == selectedLabel,
                            onClick = { selectedLabel = quality.resolution }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Remember-as-default switch: persisted only when the download
            // actually starts, so browsing pills never rewrites the default
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remember this quality",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use it as the default for future downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rememberQuality,
                    onCheckedChange = { rememberQuality = it },
                    enabled = selectedLabel != null
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val label = selectedLabel ?: return@Button
                    if (rememberQuality) themePreferences.setDownloadVideoQuality(label)
                    scope.launch { downloadRepository.downloadVideo(video, label) }
                    queued = true
                },
                enabled = selectedLabel != null && !queued && !alreadyDownloaded && !inFlight,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                when {
                    alreadyDownloaded -> {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Already downloaded", fontWeight = FontWeight.SemiBold)
                    }
                    queued || inFlight -> {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (queued) "Added to downloads" else "Downloading...",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Download", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * One selectable quality label: a tonal pill that fills with
 * primaryContainer and gains a leading check when selected, with the house
 * press-scale spring.
 */
@Composable
private fun QualityPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "qualityPillScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "qualityPillColor"
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
