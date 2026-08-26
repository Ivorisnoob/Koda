package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.BlockedChannel
import com.ivor.ivormusic.data.NotInterestedRepository
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * Everything the user has told the app to stop recommending, with a way to
 * take any of it back.
 *
 * This screen is what makes "not interested" safe to tap. The undo snackbar
 * only lives for a few seconds, so without a permanent list a mis-tap two
 * weeks ago would be an invisible, unfixable hole in the feed - and the user
 * would have no way to tell a hidden video from one YouTube simply stopped
 * recommending. Both lists are shown even when empty is the common case,
 * because the counts are the only feedback that the feature is doing
 * anything at all.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotInterestedScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val hiddenVideos by viewModel.hiddenVideos.collectAsState()
    val blockedChannels by viewModel.blockedChannels.collectAsState()

    var confirmClear by remember { mutableStateOf<ClearTarget?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.sp_not_recommended), fontWeight = FontWeight.Bold)
                        Text(
                            text = summaryLine(hiddenVideos.size, blockedChannels.size),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { scaffoldPadding ->
        if (hiddenVideos.isEmpty() && blockedChannels.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (blockedChannels.isNotEmpty()) {
                item(key = "channels-header") {
                    SectionHeader(
                        title = stringResource(R.string.section_channels),
                        subtitle = "${blockedChannels.size} won't appear in your feeds",
                        onClear = { confirmClear = ClearTarget.CHANNELS }
                    )
                }
                items(blockedChannels, key = { it.channelId.ifBlank { it.name } }) { channel ->
                    BlockedChannelRow(
                        channel = channel,
                        onUnblock = { viewModel.unblockChannel(channel.channelId, channel.name) }
                    )
                }
            }

            if (hiddenVideos.isNotEmpty()) {
                item(key = "videos-header") {
                    SectionHeader(
                        title = stringResource(R.string.cat_videos),
                        subtitle = "${hiddenVideos.size} hidden",
                        onClear = { confirmClear = ClearTarget.VIDEOS }
                    )
                }
                items(hiddenVideos, key = { it.videoId }) { video ->
                    HiddenVideoRow(
                        video = video,
                        onUnhide = { viewModel.unhideVideo(video.videoId) }
                    )
                }
            }
        }
    }

    confirmClear?.let { target ->
        val isChannels = target == ClearTarget.CHANNELS
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            title = {
                Text(
                    if (isChannels) stringResource(R.string.ni_unblock_all_q) else stringResource(R.string.ni_unhide_all_q),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (isChannels) {
                        stringResource(R.string.ni_unblock_body)
                    } else {
                        stringResource(R.string.ni_unhide_body)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isChannels) viewModel.clearBlockedChannels() else viewModel.clearHiddenVideos()
                    confirmClear = null
                }) { Text(if (isChannels) stringResource(R.string.ni_unblock_all) else stringResource(R.string.ni_unhide_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

private enum class ClearTarget { VIDEOS, CHANNELS }

@Composable
private fun summaryLine(hiddenCount: Int, blockedCount: Int): String = when {
    hiddenCount == 0 && blockedCount == 0 -> stringResource(R.string.ni_nothing_hidden)
    blockedCount == 0 -> "$hiddenCount video${if (hiddenCount == 1) "" else "s"}"
    hiddenCount == 0 -> "$blockedCount channel${if (blockedCount == 1) "" else "s"}"
    else -> "$blockedCount channel${if (blockedCount == 1) "" else "s"}, $hiddenCount video${if (hiddenCount == 1) "" else "s"}"
}

@Composable
private fun SectionHeader(title: String, subtitle: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
    }
}

@Composable
private fun BlockedChannelRow(channel: BlockedChannel, onUnblock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.avatarUrl.isNullOrBlank()) {
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
            Spacer(Modifier.width(12.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onUnblock) {
                Icon(
                    Icons.Rounded.Undo,
                    contentDescription = "Recommend ${channel.name} again",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HiddenVideoRow(
    video: NotInterestedRepository.HiddenVideo,
    onUnhide: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The stored entry has no thumbnail url - keeping one per hidden
            // video would bloat the preference for a list nobody browses for
            // pleasure - but YouTube serves a predictable one per id.
            AsyncImage(
                model = "https://i.ytimg.com/vi/${video.videoId}/mqdefault.jpg",
                contentDescription = null,
                modifier = Modifier
                    .size(width = 72.dp, height = 40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!video.channelName.isNullOrBlank()) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onUnhide) {
                Icon(
                    Icons.Rounded.Undo,
                    contentDescription = "Unhide ${video.title}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.NotInterested,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.ni_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ni_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
