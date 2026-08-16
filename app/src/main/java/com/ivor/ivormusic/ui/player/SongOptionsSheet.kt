package com.ivor.ivormusic.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.components.SongArtwork

/**
 * What you get for long-pressing a song anywhere in music mode.
 *
 * Music mode had no such sheet. Songs could be tapped to play and nothing else,
 * which is why "add to queue" existed on `PlayerViewModel` and could not be
 * reached from anywhere in the app - the queue was something you could only
 * build by starting playback over from a new list, and adding one track to what
 * was already playing was impossible.
 *
 * **Play next and Add to queue lead**, because they are the two this sheet
 * exists for and they are the two with no other route. Adding to a playlist
 * hands off to [AddToPlaylistSheet] rather than reimplementing it; the two
 * sheets swap in place so it stays one gesture rather than a sheet on top of a
 * sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    /** Offered only where there is somewhere to go; null hides the row. */
    onArtistClick: ((String) -> Unit)? = null
) {
    var showPlaylists by remember { mutableStateOf(false) }
    val addToPlaylistItems by viewModel.addToPlaylistItems.collectAsState()

    if (showPlaylists) {
        AddToPlaylistSheet(
            playlists = addToPlaylistItems,
            onPlaylistClick = { playlist ->
                viewModel.addToPlaylist(playlist.id, song)
                onDismiss()
            },
            onCreateNewClick = { name, desc ->
                viewModel.createPlaylistWithSong(name, desc, song)
                onDismiss()
            },
            onDismissRequest = onDismiss
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val likedIds by viewModel.likedSongIds.collectAsState()
    val isLiked = song.id in likedIds
    val isDownloaded = remember(song.id) { viewModel.isDownloaded(song.id) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            SongOptionsHeader(song)

            Spacer(modifier = Modifier.height(20.dp))

            OptionRow(
                icon = Icons.Rounded.PlaylistPlay,
                title = "Play next",
                subtitle = "Right after this song",
                hero = true,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.playNext(song)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OptionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "Add to queue",
                subtitle = "At the end",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.addToQueue(song)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                title = "Add to playlist",
                onClick = { showPlaylists = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OptionRow(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                title = if (isLiked) "Remove from liked" else "Like",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    viewModel.toggleLike(song)
                    onDismiss()
                }
            )

            // A song already on the device has nothing to download, and a row
            // that would undo the download belongs on the downloads screen
            // rather than one tap from a list of everything.
            if (!isDownloaded && !viewModel.isLocalOriginal(song)) {
                Spacer(modifier = Modifier.height(8.dp))
                OptionRow(
                    icon = Icons.Rounded.Download,
                    title = "Download",
                    subtitle = "Keep on this device",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.toggleDownload(song)
                        onDismiss()
                    }
                )
            } else if (isDownloaded) {
                Spacer(modifier = Modifier.height(8.dp))
                OptionRow(
                    icon = Icons.Rounded.Check,
                    title = "Downloaded",
                    enabled = false,
                    onClick = {}
                )
            }

            onArtistClick?.let { go ->
                val artist = song.artist.takeIf { it.isNotBlank() && !it.startsWith("Unknown") }
                if (artist != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OptionRow(
                        icon = Icons.Rounded.PlaylistPlay,
                        title = "Go to $artist",
                        onClick = {
                            go(artist)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongOptionsHeader(song: Song) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            SongArtwork(
                song = song,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One action. Shaped like `SaveToPlaylistSheet`'s rows on the video side, so
 * the same gesture produces a recognisably similar sheet in both modes.
 */
@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    hero: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "songOptionRowScale"
    )

    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        hero -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        hero -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(if (hero) 20.dp else 16.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(if (hero) 20.dp else 16.dp),
        color = container
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (hero) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint ?: content,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
