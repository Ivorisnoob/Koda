package com.ivor.ivormusic.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import kotlinx.coroutines.delay

/** Per-row save progress inside the sheet. */
private enum class SaveRowState { IDLE, SAVING, SAVED, FAILED }

/**
 * Bottom sheet shown when long-pressing a video card: save the video to
 * Watch Later (pinned hero row), download it to the device, save it to any
 * of the user's playlists, or tell the app to stop recommending the video
 * or its channel. Feedback is inline — the tapped row shows a spinner, then
 * a check, and the sheet dismisses itself; on failure the row flags an error
 * instead. [onDownload] hands off to the download sheet (the caller
 * dismisses this one and opens that one).
 *
 * The two "don't recommend" rows sit at the bottom, visually separated and
 * in the muted tone the rest of the sheet is not. They are destructive in a
 * small way and share a surface with Save, so they should never be the thing
 * a thumb lands on by accident. Passing null for [onNotInterested] hides
 * them, which is what surfaces with no recommendation feed behind them do.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveToPlaylistSheet(
    video: VideoItem,
    playlists: List<VideoPlaylist>,
    isLoading: Boolean,
    onSave: (playlistId: String, onResult: (Boolean) -> Unit) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onNotInterested: (() -> Unit)? = null,
    onBlockChannel: (() -> Unit)? = null,
    /**
     * True when there is no YouTube session, which changes what the pinned row
     * promises: Watch Later is then the device's own list rather than the
     * account's, and saying so is the difference between a save the user can
     * find again and one they will go looking for on youtube.com.
     */
    isSignedOut: Boolean = false,
    /**
     * Make a playlist without leaving the sheet. Null hides the row.
     *
     * Worth its place because the empty state is now reachable: signed out
     * there are no account playlists to list, and sending someone to the
     * Library tab to create one before they can save the video they are
     * looking at is exactly the three-taps-deep detour that makes a control
     * feel broken.
     */
    onCreatePlaylist: ((name: String, onCreated: (String?) -> Unit) -> Unit)? = null
) {
    // Open fully expanded: in the half-expanded state the inner playlist
    // list and the sheet's drag-to-expand fight over scroll gestures,
    // which reads as janky/stuttering scrolling.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var savingId by remember { mutableStateOf<String?>(null) }
    var savedId by remember { mutableStateOf<String?>(null) }
    var failedId by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Let the check linger for a beat, then close on success
    LaunchedEffect(savedId) {
        if (savedId != null) {
            delay(900)
            onDismiss()
        }
    }

    fun rowState(id: String): SaveRowState = when (id) {
        savingId -> SaveRowState.SAVING
        savedId -> SaveRowState.SAVED
        failedId -> SaveRowState.FAILED
        else -> SaveRowState.IDLE
    }

    fun save(id: String) {
        if (savingId != null || savedId != null) return
        failedId = null
        savingId = id
        onSave(id) { ok ->
            savingId = null
            if (ok) savedId = id else failedId = id
        }
    }

    if (showCreateDialog && onCreatePlaylist != null) {
        NewPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                // Save straight into it: the only reason to make a playlist
                // from this sheet is to put this video in it, and leaving the
                // user to then find the new row would be the sheet ignoring
                // what it was just asked for.
                onCreatePlaylist(name) { newId -> if (newId != null) save(newId) }
            }
        )
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
                text = "Save video to...",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Watch Later, pinned hero row. Signed out this is the device's own
            // list, not the account's, and the subtitle says so.
            SaveTargetRow(
                title = "Watch Later",
                subtitle = if (isSignedOut) "Kept on this device"
                    else "Saved for when you have time",
                state = rowState("WL"),
                hero = true,
                onClick = { save("WL") },
                leading = {
                    Icon(
                        imageVector = Icons.Rounded.WatchLater,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            DownloadRow(onClick = onDownload)

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Your playlists",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading && playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
                playlists.isEmpty() -> {
                    Text(
                        text = if (onCreatePlaylist != null) {
                            "None yet. Make one and this video goes straight into it."
                        } else {
                            "No playlists yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                else -> {
                    // No overscroll: the list is capped at 340dp inside the sheet,
                    // so the default stretch deforms the rows in place (while the
                    // sheet itself stays put) instead of reading as an edge effect.
                    CompositionLocalProvider(LocalOverscrollFactory provides null) {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(playlists, key = { it.playlistId }) { playlist ->
                                SaveTargetRow(
                                    title = playlist.title,
                                    subtitle = playlist.videoCountText,
                                    state = rowState(playlist.playlistId),
                                    onClick = { save(playlist.playlistId) },
                                    leading = {
                                        if (!playlist.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = playlist.thumbnailUrl,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (onCreatePlaylist != null) {
                Spacer(modifier = Modifier.height(8.dp))
                NewPlaylistRow(onClick = { showCreateDialog = true })
            }

            if (onNotInterested != null || onBlockChannel != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Stop recommending",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                onNotInterested?.let { action ->
                    DismissRow(
                        icon = Icons.Rounded.NotInterested,
                        title = "Not interested",
                        subtitle = "Hide this video from your feeds",
                        onClick = {
                            action()
                            onDismiss()
                        }
                    )
                }

                onBlockChannel?.let { action ->
                    if (onNotInterested != null) Spacer(modifier = Modifier.height(8.dp))
                    DismissRow(
                        icon = Icons.Rounded.RemoveCircleOutline,
                        title = "Don't recommend channel",
                        subtitle = video.channelName.takeIf { it.isNotBlank() }
                            ?.let { "Hide everything from $it" }
                            ?: "Hide everything from this channel",
                        onClick = {
                            action()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Name-only dialog for a playlist made from the save sheet.
 *
 * Deliberately not the Library's create dialog: there is no store to pick here.
 * A playlist created mid-save is a device one, because the sheet is reachable
 * signed out and because the video it was opened on is going into it either
 * way.
 */
@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * "New playlist", styled as a dashed-quiet peer of the save rows rather than a
 * filled one: it is a way to get a target, not a target.
 */
@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "newPlaylistRowScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New playlist",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Kept on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A "stop recommending" row. Deliberately quieter than the save rows above
 * it — surfaceContainerHigh with onSurfaceVariant text rather than a filled
 * container — so the destructive half of the sheet never reads as the
 * primary action.
 */
@Composable
private fun DismissRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dismissRowScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

/**
 * One save target: leading icon/thumbnail in a rounded box, title +
 * optional subtitle, and a trailing status icon that walks
 * add -> spinner -> check (or error). The hero variant (Watch Later)
 * fills with primaryContainer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveTargetRow(
    title: String,
    subtitle: String?,
    state: SaveRowState,
    onClick: () -> Unit,
    hero: Boolean = false,
    leading: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "saveRowScale"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            state == SaveRowState.SAVED -> MaterialTheme.colorScheme.secondaryContainer
            hero -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "saveRowColor"
    )
    val titleColor = when {
        state == SaveRowState.SAVED -> MaterialTheme.colorScheme.onSecondaryContainer
        hero -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(if (hero) 20.dp else 16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(if (hero) 20.dp else 16.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
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
                leading()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
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

            Spacer(modifier = Modifier.width(12.dp))

            when (state) {
                SaveRowState.SAVING -> LoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                SaveRowState.SAVED -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Saved",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                SaveRowState.FAILED -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Couldn't save",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                SaveRowState.IDLE -> Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Hand-off row to the download sheet, styled like a SaveTargetRow but with
 * a chevron: tapping it navigates to the quality picker rather than acting
 * in place, so the add/spinner/check state walk does not apply.
 */
@Composable
private fun DownloadRow(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "downloadRowScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pick a quality and save to this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
