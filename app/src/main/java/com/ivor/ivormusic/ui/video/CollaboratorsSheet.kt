package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.VideoCollaborator

/**
 * Every channel credited on a collab video, as a list you can open one from.
 *
 * A collab video's watch page names no single owner - see [VideoCollaborator] -
 * so the channel row cannot navigate anywhere and had to become a way in to all
 * of them. A sheet rather than an [androidx.compose.material3.AlertDialog]
 * because up to five rows each carrying an avatar, a name, a handle and a
 * subscriber count is a list, and every other list this player opens over the
 * video is a sheet.
 *
 * The rows navigate rather than subscribe. Subscribing has one decision point
 * in this app (`SubscriptionActions`, which weighs the account against the
 * device target), the channel page already routes through it correctly, and a
 * second Subscribe control here would be a second place to keep that right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollaboratorsSheet(
    collaborators: List<VideoCollaborator>,
    onOpenChannel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.vp_collaborators),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.vp_collaborators_count,
                        collaborators.size,
                        collaborators.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn {
                items(collaborators, key = { it.channelId }) { collaborator ->
                    CollaboratorRow(
                        collaborator = collaborator,
                        onClick = {
                            onOpenChannel(collaborator.channelId)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CollaboratorRow(
    collaborator: VideoCollaborator,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CollaboratorAvatar(collaborator = collaborator, size = 48.dp)

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = collaborator.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (collaborator.isVerified) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.cd_verified),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            // Handle and subscriber count are both optional on the wire, so the
            // line is assembled from whichever arrived rather than assuming both.
            val secondary = listOfNotNull(
                collaborator.handle,
                collaborator.subscriberCount
            ).joinToString("  •  ")
            if (secondary.isNotBlank()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One collaborator's avatar, falling back to their initial. */
@Composable
internal fun CollaboratorAvatar(
    collaborator: VideoCollaborator,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!collaborator.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = collaborator.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = collaborator.name.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * The collaborators' avatars as one overlapping cluster, standing in for the
 * single channel avatar a collab video does not have.
 *
 * Capped at [MAX_STACKED_AVATARS] because YouTube allows five and five 44dp
 * circles at a 16dp step is wider than the leading slot of a list row; the
 * count on the row's own line says how many there really are. Drawn back to
 * front so the first collaborator - the uploader - is the one on top.
 */
@Composable
internal fun CollaboratorAvatarStack(
    collaborators: List<VideoCollaborator>,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 36.dp,
    overlap: androidx.compose.ui.unit.Dp = 18.dp
) {
    val shown = collaborators.take(MAX_STACKED_AVATARS)
    Box(modifier = modifier) {
        shown.asReversed().forEachIndexed { indexFromEnd, _ ->
            val index = shown.lastIndex - indexFromEnd
            CollaboratorAvatar(
                collaborator = shown[index],
                size = avatarSize,
                modifier = Modifier
                    .padding(start = (avatarSize - overlap) * index)
                    // A ring in the row's own colour is what separates two dark
                    // avatars sitting on top of each other.
                    .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                    .padding(1.5.dp)
            )
        }
    }
}

private const val MAX_STACKED_AVATARS = 3
