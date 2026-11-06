package com.ivor.ivormusic.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import com.ivor.ivormusic.ui.components.LocalSongSelection
import com.ivor.ivormusic.ui.player.AddToPlaylistSheet
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.launch

/**
 * What can be done with the songs in [LocalSongSelection]: the bar Home's
 * navigation turns into while songs are picked, at the same place, height
 * and shape, so the change reads as one bar changing what it holds.
 *
 * Holds its own snackbar host, outside the bar, so "Added 12 songs" is still
 * on screen after the action has ended the selection and the bar has gone.
 *
 * @param bottomPadding the room the bar keeps from the bottom edge, so it
 * lands where the navigation bar it replaces sits on this window.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongSelectionDeck(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val selection = LocalSongSelection.current
    val context = LocalContext.current
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showPlaylists by remember { mutableStateOf(false) }

    fun announce(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    /** Runs [action] on a copy of the picked songs and ends the selection. */
    fun finish(action: (List<Song>) -> Unit) {
        val songs = selection.songs.toList()
        if (songs.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        action(songs)
        selection.clear()
    }

    BackHandler(enabled = selection.isActive) { selection.clear() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DismissibleSnackbarHost(hostState = snackbar)
            AnimatedVisibility(
                visible = selection.isActive,
                enter = fadeIn() + scaleIn(
                    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.85f,
                ),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.widthIn(max = 560.dp),
                    content = {
                        IconButton(
                            onClick = { selection.clear() },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.sel_close))
                        }
                        SelectedCount(selection.count)
                        if (selection.available.size > 1) {
                            val all = selection.allSelected
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    selection.toggleAll()
                                },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    if (all) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    stringResource(if (all) R.string.sel_select_none else R.string.sel_select_all),
                                )
                            }
                        }
                        IconButton(onClick = { showPlaylists = true }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.sel_add_to_playlist))
                        }
                        IconButton(
                            onClick = {
                                finish { songs ->
                                    playerViewModel.playNext(songs)
                                    announce(context.resources.getQuantityString(R.plurals.sel_played_next, songs.size, songs.size))
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.song_options_play_next))
                        }
                        IconButton(
                            onClick = {
                                finish { songs ->
                                    playerViewModel.addToQueue(songs)
                                    announce(context.resources.getQuantityString(R.plurals.sel_queued, songs.size, songs.size))
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.song_options_add_to_queue))
                        }
                        IconButton(
                            onClick = {
                                finish { songs ->
                                    // Only streams download; device files and songs
                                    // already saved are left as they are.
                                    val wanted = songs.filter {
                                        it.source == SongSource.YOUTUBE && !playerViewModel.isDownloaded(it.id)
                                    }
                                    if (wanted.isEmpty()) {
                                        announce(context.getString(R.string.sel_nothing_to_download))
                                    } else {
                                        playerViewModel.downloadPlaylist(wanted)
                                        announce(context.resources.getQuantityString(R.plurals.sel_downloading, wanted.size, wanted.size))
                                    }
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.Download, stringResource(R.string.song_options_download))
                        }
                        selection.removeAction?.let { remove ->
                            IconButton(
                                onClick = { finish(remove) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    stringResource(R.string.sel_remove),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    if (showPlaylists) {
        val items by playerViewModel.addToPlaylistItems.collectAsState()
        val local by playerViewModel.localPlaylistContents.collectAsState()
        // A device file has no place on the account, so a pick that holds one
        // is offered the device playlists only.
        val localIds = remember(local) { local.map { it.id }.toSet() }
        val anyDeviceFile = selection.songs.any { it.source != SongSource.YOUTUBE }
        AddToPlaylistSheet(
            playlists = if (anyDeviceFile) items.filter { it.id in localIds } else items,
            onPlaylistClick = { playlist ->
                showPlaylists = false
                val songs = selection.songs.toList()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                selection.clear()
                scope.launch {
                    val added = homeViewModel.addSongsToPlaylist(playlist.id, songs)
                    val name = playlist.name.orEmpty()
                    announce(
                        if (added == 0) context.getString(R.string.sel_already_in_playlist, name)
                        else context.resources.getQuantityString(R.plurals.sel_added_to_playlist, added, added, name)
                    )
                }
            },
            onCreateNewClick = { name, description ->
                showPlaylists = false
                val songs = selection.songs.toList()
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                selection.clear()
                scope.launch {
                    homeViewModel.createLocalPlaylistWithSongs(name, description, songs)
                    announce(context.resources.getQuantityString(R.plurals.sel_added_to_playlist, songs.size, songs.size, name.trim()))
                }
            },
            onDismissRequest = { showPlaylists = false },
        )
    }
}

/** The number of songs picked, rolling up or down as it changes. */
@Composable
private fun SelectedCount(count: Int) {
    val label = LocalContext.current.resources.getQuantityString(R.plurals.sel_count, count, count)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .height(36.dp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(stiffness = Spring.StiffnessMedium)) { if (up) it else -it } + fadeIn()) togetherWith
                        (slideOutVertically(spring(stiffness = Spring.StiffnessMedium)) { if (up) -it else it } + fadeOut())
                },
                label = "SelectedCount",
            ) { shown ->
                Text(
                    text = shown.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
