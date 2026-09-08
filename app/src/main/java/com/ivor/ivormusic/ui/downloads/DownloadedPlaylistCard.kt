package com.ivor.ivormusic.ui.downloads

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.DownloadedPlaylist
import com.ivor.ivormusic.data.Song

/**
 * The head of an expandable downloaded playlist: cover, title, offline count,
 * play, and the chevron that opens it.
 *
 * **It is the top of one container, not a card with a list underneath it.** The
 * tracks that open out of it are ordinary [DownloadedRow]s in the same lazy
 * list, drawn flush against this header on the same surface, so the group reads
 * exactly like the In progress and Downloaded sections above it. That is why
 * the bottom corners are animated rather than fixed: at rest the header is a
 * lone rounded card, and as the list appears the bottom radius runs to zero on
 * the same spring the chevron turns on, so the container visibly opens instead
 * of the corner snapping square one frame before the rows arrive.
 *
 * The row is the collapse toggle and the count is ordinary visible text;
 * `stateDescription` carries expanded/collapsed, which is the state a screen
 * reader has no other way to learn. An earlier version put the count there, so
 * TalkBack announced "9 of 12 tracks available offline" as the *state* of the
 * toggle and never once said whether it was open.
 *
 * Play is its own control rather than a full-width button under the title: a
 * labelled button sat in every collapsed card and made six playlists fill the
 * screen before anything else in the tab was reachable. Its 40dp container
 * carries M3's own 48dp touch target, and the label survives as its
 * content description.
 */
@Composable
internal fun DownloadedPlaylistCard(
    playlist: DownloadedPlaylist,
    available: List<Song>,
    expanded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomCorner by animateDpAsState(
        targetValue = if (expanded) 0.dp else SEGMENT_CORNER,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "playlistCorner"
    )
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "playlistChevron"
    )
    val shape = RoundedCornerShape(
        topStart = SEGMENT_CORNER,
        topEnd = SEGMENT_CORNER,
        bottomStart = bottomCorner,
        bottomEnd = bottomCorner
    )

    val state = stringResource(
        if (expanded) R.string.download_playlist_expanded else R.string.download_playlist_collapsed
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Clip before clickable so the ripple follows the corner as it
            // opens rather than flashing a full rectangle past it.
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics { stateDescription = state },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            // Matches DownloadedRow exactly, so the header's text column and
            // the track rows below it share one left edge and one divider inset.
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                // The downloaded companion is durable and the playlist's own
                // cover is a network URL, so the file on this device wins:
                // offline is the state this whole section is about.
                val cover = available.firstOrNull()?.albumArtUri?.toString()
                    ?: available.firstOrNull()?.thumbnailUrl
                    ?: playlist.artworkUrl
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.download_playlist_available,
                        available.size,
                        playlist.songs.size
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledTonalIconButton(
                onClick = onPlay,
                enabled = available.isNotEmpty(),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play_all),
                    modifier = Modifier.size(20.dp)
                )
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                // The row already carries the expanded/collapsed state, so the
                // chevron is decoration and naming it again would make the
                // whole row announce twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .graphicsLayer { rotationZ = chevronTurn }
            )
        }
    }
}

/**
 * What an opened playlist shows when none of its tracks are on the device.
 *
 * Without it the chevron turns, the corner opens and nothing appears, which
 * reads as a broken control rather than as an empty playlist - and this state
 * is reachable the moment someone deletes the last downloaded track of a
 * playlist whose snapshot is still remembered.
 */
@Composable
internal fun DownloadedPlaylistEmptyRow(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            bottomStart = SEGMENT_CORNER,
            bottomEnd = SEGMENT_CORNER
        ),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Text(
            text = stringResource(R.string.download_playlist_none_offline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
        )
    }
}
