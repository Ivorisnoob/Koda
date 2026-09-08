package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.components.VideoThumbnail

/** Presentation only: VideoCard owns the same playback and channel actions in either layout. */
@Composable
internal fun CompactVideoCardContent(
    video: VideoItem,
    openChannel: (() -> Unit)?,
    onMoreClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(0.36f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
        ) {
            VideoThumbnail(video = video, modifier = Modifier.fillMaxSize())
            if (video.isLive || video.duration > 0L) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
                ) {
                    Text(
                        text = if (video.isLive) stringResource(R.string.badge_live) else video.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(0.64f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .then(if (openChannel != null) Modifier.clickable(onClick = openChannel) else Modifier),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metadata = listOfNotNull(
                        video.viewCount.takeIf { it.isNotBlank() },
                        video.uploadedDate?.takeIf { it.isNotBlank() }
                    ).joinToString(" · ")
                    if (metadata.isNotEmpty()) {
                        Text(
                            text = metadata,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (onMoreClick != null) {
                    IconButton(onClick = onMoreClick) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                    }
                }
            }
        }
    }
}
