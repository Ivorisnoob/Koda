package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.remember
import com.ivor.ivormusic.ui.components.QueueDragHandle
import com.ivor.ivormusic.ui.components.QueueRowContainer
import com.ivor.ivormusic.ui.components.queueRowKeys
import com.ivor.ivormusic.ui.components.rememberQueueRemoval
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import com.ivor.ivormusic.data.VideoQueue

/**
 * The playlist behind the video that is playing, as a list you can jump around
 * in.
 *
 * The player is a single-video surface, so a playlist had nowhere to be seen at
 * all: the only list on the watch page is Up Next, which is recommendations. A
 * sheet rather than a panel wedged into the info column because a playlist runs
 * to hundreds of entries and the info column is a `verticalScroll` - the same
 * reason chapters and captions are sheets.
 *
 * Addressed by index throughout, never by video id: playlists may list the same
 * video twice (see [VideoQueue]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQueueSheet(
    queue: VideoQueue,
    onSelect: (index: Int) -> Unit,
    onDismiss: () -> Unit,
    keepSystemBarsHidden: Boolean = false,
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    onRemove: (index: Int) -> Unit = {},
    onUndoRemove: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val rowKeys = remember(queue.videos) {
        queueRowKeys(queue.videos.map { it.videoId }, "video_queue")
    }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = rowKeys,
        onMove = onMove,
        // Nothing to persist: the video queue is in-memory session state, not
        // a restored playback session the way music's is.
        onSettle = {}
    )
    val removal = rememberQueueRemoval(onUndo = onUndoRemove)

    // Open on what is playing, not at the top: on a long playlist the current
    // video is usually off-screen, and the first thing anyone does here is look
    // for where they are. Two rows of lead-in so it does not sit against the
    // header with no context above it.
    //
    // Keyed on the sheet opening rather than on queue.index, or every reorder
    // that shifts the playing row would yank the list back under the finger.
    LaunchedEffect(Unit) {
        listState.scrollToItem((queue.index - 2).coerceAtLeast(0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        KeepSystemBarsHidden(keepSystemBarsHidden)

        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(R.string.vq_playing_from),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = queue.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = queue.positionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Occurrence-qualified keys: the same video may appear twice in
                // one playlist so a bare id crashes, but a key carrying the row
                // index would change on every reorder. See queueRowKeys.
                itemsIndexed(
                    items = queue.videos,
                    key = { index, _ -> rowKeys.getOrElse(index) { "video_queue_$index" } }
                ) { index, video ->
                    val key = rowKeys.getOrElse(index) { "video_queue_$index" }
                    val isDragging = reorder.isDragging(key)
                    val isCurrent = index == queue.index

                    QueueRowContainer(
                        isDragging = isDragging,
                        dragOffset = reorder.offsetFor(key),
                        // The playing row is not removable, so it is not
                        // swipeable either - see VideoQueue.removedAt.
                        removeEnabled = queue.canRemoveAt(index),
                        onRemove = {
                            onRemove(index)
                            removal.onRemoved(video.title)
                        },
                        modifier = if (isDragging) Modifier else Modifier.animateItem()
                    ) {
                        QueueRow(
                            video = video,
                            position = index + 1,
                            isCurrent = isCurrent,
                            onClick = { onSelect(index) },
                            dragHandle = {
                                QueueDragHandle(
                                    state = reorder,
                                    rowKey = key,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = removal.hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun QueueRow(
    video: com.ivor.ivormusic.data.VideoItem,
    position: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    dragHandle: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                // Opaque, not translucent: the row now sits over the red
                // swipe-to-remove background, which would otherwise show
                // through every row at rest.
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .padding(start = 24.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The position marker doubles as the now-playing indicator, so the row
        // that is playing reads at a glance without a second badge.
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent) {
                Icon(
                    Icons.Rounded.Equalizer,
                    contentDescription = stringResource(R.string.cd_now_playing),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (video.duration > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.channelName.isNotBlank()) {
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        dragHandle()
    }
}
