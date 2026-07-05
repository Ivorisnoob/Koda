package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.TimedComment
import java.util.Locale

/** How long a timed comment stays on screen after its timestamp passes. */
private const val TIMED_COMMENT_DISPLAY_MS = 6_000L

/**
 * Non-intrusive overlay that surfaces comments referencing the current
 * playback position. A comment appears when playback reaches the timestamp
 * mentioned in its text, stays visible for a few seconds and fades away.
 */
@Composable
fun TimedCommentsOverlay(
    timedComments: List<TimedComment>,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    // Most recent comment whose timestamp has passed within the display window.
    // timedComments is sorted by timeMs, so lastOrNull picks the latest one.
    val active = timedComments.lastOrNull {
        positionMs >= it.timeMs && positionMs - it.timeMs < TIMED_COMMENT_DISPLAY_MS
    }

    AnimatedContent(
        targetState = active,
        transitionSpec = {
            (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 })
                .togetherWith(fadeOut(tween(500)))
        },
        contentKey = { it?.comment?.commentId },
        label = "timedComment",
        modifier = modifier
    ) { timedComment ->
        if (timedComment != null) {
            TimedCommentCard(timedComment)
        } else {
            Spacer(Modifier.size(0.dp))
        }
    }
}

@Composable
private fun TimedCommentCard(timedComment: TimedComment) {
    val comment = timedComment.comment
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        modifier = Modifier.widthIn(max = 360.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (comment.authorAvatarUrl != null) {
                AsyncImage(
                    model = comment.authorAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = comment.author.removePrefix("@").take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = comment.author,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = formatTimestamp(timedComment.timeMs),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val seconds = millis / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
