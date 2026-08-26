package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LocalVideoPlaylistsRepository
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.delay

/** Per-row save progress. */
private enum class SaveRowState { IDLE, SAVING, SAVED, FAILED }

/** Which half of the sheet is on screen. */
private enum class OptionsPane { ACTIONS, PLAYLISTS }

/**
 * Row ids for the two queue actions. They share the sheet's save-state machine
 * so they get the same check-then-dismiss, and they cannot collide with a real
 * playlist id.
 */
private const val ENQUEUE_NEXT_ROW = "__queue_next__"
private const val ENQUEUE_END_ROW = "__queue_end__"

/** Watch Later's account id, and the row id the sheet's state machine uses. */
private const val WATCH_LATER_ID = "WL"

/** Past this many playlists the picker earns a search field. */
private const val PICKER_SEARCH_THRESHOLD = 8

/**
 * What you get for long-pressing a video card, anywhere in video mode.
 *
 * **Two panes in one sheet, not one long scroll.** The previous single-column
 * version laid ~1100dp of fixed rows into a sheet that gets ~890dp on a
 * Pixel-class phone, so New playlist and both "stop recommending" rows sat
 * below the fold with no gesture that could reach them - and in landscape,
 * which the video player opens this over, everything past the second row was
 * gone. Nothing about that depended on how many playlists the user had: the
 * playlist list was capped at 340dp, so it was the *fixed* chrome that
 * overflowed, and the queue rows made it worse rather than causing it.
 *
 * So the playlist targets moved behind [OptionsPane.PLAYLISTS], whose whole
 * body is a single [LazyColumn] with the create/close controls pinned under
 * it. That pane cannot overflow at any playlist count, and the actions pane
 * that remains is a fixed list short enough to fit without one. It is the same
 * split music mode has had all along (`SongOptionsSheet` handing off to
 * `AddToPlaylistSheet`), and the two panes swap in place so it stays one
 * gesture rather than a sheet on top of a sheet.
 *
 * Saving is inline and multi-target: a tapped row spins, then checks, and the
 * picker stays open so a video can go into several playlists at once. Only the
 * terminal actions - Watch later, the two queue rows - close the sheet behind
 * their check, because there is nothing left to do after one.
 *
 * [alreadyIn] marks targets that already hold the video. It covers the device's
 * playlists only, and deliberately: the account's would need a browse call
 * each, which is a request per playlist for a checkmark. Saving into one twice
 * is harmless (`LocalVideoPlaylistsRepository.addVideo` and YouTube both treat
 * it as a no-op), so the cost of not knowing is cosmetic.
 *
 * Passing null for [onNotInterested] / [onBlockChannel] hides the muted group
 * at the bottom, which is what surfaces with no recommendation feed behind them
 * do. [onDownload] hands off to the download sheet; [onEnqueue] is null where
 * there is no video player to queue into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(
    video: VideoItem,
    playlists: List<VideoPlaylist>,
    isLoading: Boolean,
    onSave: (playlistId: String, onResult: (Boolean) -> Unit) -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onNotInterested: (() -> Unit)? = null,
    onBlockChannel: (() -> Unit)? = null,
    /**
     * True when there is no YouTube session, which changes what the Watch later
     * row promises: it is then the device's own list rather than the account's,
     * and saying so is the difference between a save the user can find again
     * and one they will go looking for on youtube.com.
     */
    isSignedOut: Boolean = false,
    /** Make a playlist without leaving the sheet. Null hides the control. */
    onCreatePlaylist: ((name: String, onCreated: (String?) -> Unit) -> Unit)? = null,
    /** Queue this video, either straight after what is playing or at the end. */
    onEnqueue: ((playNext: Boolean) -> Unit)? = null,
    /** Playlist ids that already contain this video; device playlists only. */
    alreadyIn: Set<String> = emptySet(),
    /**
     * Open this video's creator. Null hides the row, which is right where the
     * item carried no channel id, and where the channel is the page the sheet
     * was opened from.
     */
    onOpenChannel: (() -> Unit)? = null
) {
    // Open fully expanded: in the half-expanded state the picker's list and the
    // sheet's drag-to-expand fight over scroll gestures, which reads as janky
    // scrolling.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()

    var pane by remember { mutableStateOf(OptionsPane.ACTIONS) }
    // Sets rather than single ids: the picker saves to several targets and a
    // second tap must not wait on the first request to come back.
    var savingIds by remember { mutableStateOf(emptySet<String>()) }
    var savedIds by remember { mutableStateOf(emptySet<String>()) }
    var failedIds by remember { mutableStateOf(emptySet<String>()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Only a terminal action closes the sheet behind its check. A save made in
    // the picker leaves it open, because the point of the picker is that a
    // video can go into more than one playlist.
    var confirmedTerminal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirmedTerminal) {
        if (confirmedTerminal != null) {
            delay(700)
            onDismiss()
        }
    }

    fun rowState(id: String): SaveRowState = when {
        id in savingIds -> SaveRowState.SAVING
        id in savedIds || id in alreadyIn -> SaveRowState.SAVED
        id in failedIds -> SaveRowState.FAILED
        else -> SaveRowState.IDLE
    }

    /** @param terminal whether a successful save should close the sheet. */
    fun save(id: String, terminal: Boolean) {
        if (id in savingIds || id in savedIds || id in alreadyIn) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        failedIds = failedIds - id
        savingIds = savingIds + id
        onSave(id) { ok ->
            savingIds = savingIds - id
            if (ok) {
                savedIds = savedIds + id
                if (terminal) confirmedTerminal = id
            } else {
                failedIds = failedIds + id
            }
        }
    }

    fun enqueue(playNext: Boolean) {
        if (confirmedTerminal != null) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onEnqueue?.invoke(playNext)
        val id = if (playNext) ENQUEUE_NEXT_ROW else ENQUEUE_END_ROW
        // Queueing is synchronous - it edits in-memory state - so there is no
        // spinner to show, only the check.
        savedIds = savedIds + id
        confirmedTerminal = id
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
                onCreatePlaylist(name) { newId -> if (newId != null) save(newId, terminal = false) }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        // Back returns to the actions pane before it closes the sheet. It has
        // to be registered *inside* the sheet's content: handlers run last
        // registered first, and the one ModalBottomSheet installs for its own
        // dismissal is composed with the sheet, so a handler declared beside
        // the sheet would lose to it and close the whole thing.
        androidx.activity.compose.BackHandler(enabled = pane == OptionsPane.PLAYLISTS) {
            pane = OptionsPane.ACTIONS
        }

        AnimatedContent(
            targetState = pane,
            transitionSpec = {
                if (targetState == OptionsPane.PLAYLISTS) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "videoOptionsPane"
        ) { current ->
            when (current) {
                OptionsPane.ACTIONS -> ActionsPane(
                    video = video,
                    isSignedOut = isSignedOut,
                    watchLaterState = rowState(WATCH_LATER_ID),
                    playNextState = rowState(ENQUEUE_NEXT_ROW),
                    addToQueueState = rowState(ENQUEUE_END_ROW),
                    playlistCount = playlists.size,
                    onEnqueue = if (onEnqueue != null) {
                        { playNext -> enqueue(playNext) }
                    } else null,
                    onWatchLater = { save(WATCH_LATER_ID, terminal = true) },
                    onOpenPlaylists = { pane = OptionsPane.PLAYLISTS },
                    onDownload = onDownload,
                    // Both dismissals close the sheet on the spot rather than
                    // showing a check: the undo snackbar at the root of the app
                    // is the confirmation, and it is behind this sheet.
                    onNotInterested = onNotInterested?.let { action ->
                        {
                            action()
                            onDismiss()
                        }
                    },
                    onBlockChannel = onBlockChannel?.let { action ->
                        {
                            action()
                            onDismiss()
                        }
                    },
                    // Terminal: the sheet is over a screen the channel page is
                    // about to replace, and leaving it open would put it on top
                    // of the destination.
                    onOpenChannel = onOpenChannel?.let { action ->
                        {
                            onDismiss()
                            action()
                        }
                    }
                )

                OptionsPane.PLAYLISTS -> PlaylistPickerPane(
                    playlists = playlists,
                    isLoading = isLoading,
                    isSignedOut = isSignedOut,
                    stateOf = { id -> rowState(id) },
                    onPick = { id -> save(id, terminal = false) },
                    onBack = { pane = OptionsPane.ACTIONS },
                    onCreatePlaylist = if (onCreatePlaylist != null) {
                        { showCreateDialog = true }
                    } else null,
                    onDone = onDismiss
                )
            }
        }
    }
}

/**
 * The fixed half: one action per row, no dynamic content, and therefore a
 * height the sheet can always show. It scrolls anyway, because landscape (which
 * the video player opens this over) leaves under 400dp and no arrangement of
 * eight rows fits that.
 */
@Composable
private fun ActionsPane(
    video: VideoItem,
    isSignedOut: Boolean,
    watchLaterState: SaveRowState,
    playNextState: SaveRowState,
    addToQueueState: SaveRowState,
    playlistCount: Int,
    onEnqueue: ((Boolean) -> Unit)?,
    onWatchLater: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onDownload: () -> Unit,
    onNotInterested: (() -> Unit)?,
    onBlockChannel: (() -> Unit)?,
    onOpenChannel: (() -> Unit)?
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        VideoOptionsHeader(video)

        Spacer(modifier = Modifier.height(16.dp))

        // Grouped rather than a stack of separate cards: eight standalone
        // 68dp cards is what made this sheet taller than the screen, and a
        // menu is what this actually is.
        OptionGroup {
            if (onEnqueue != null) {
                OptionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = stringResource(R.string.song_options_play_next),
                    state = playNextState,
                    onClick = { onEnqueue(true) }
                )
                OptionRowDivider()
                OptionRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = stringResource(R.string.song_options_add_to_queue),
                    state = addToQueueState,
                    onClick = { onEnqueue(false) }
                )
                OptionRowDivider()
            }

            OptionRow(
                icon = Icons.Rounded.WatchLater,
                title = stringResource(R.string.video_options_watch_later),
                // The one place the signed-out story has to be told: the save
                // lands on the device, not on the account the user may well
                // think they are saving to.
                subtitle = if (isSignedOut) stringResource(R.string.video_options_kept_on_device) else null,
                state = watchLaterState,
                onClick = onWatchLater
            )
            OptionRowDivider()
            OptionRow(
                icon = Icons.Rounded.PlaylistAdd,
                title = stringResource(R.string.video_options_save_to_playlist),
                subtitle = when (playlistCount) {
                    0 -> null
                    else -> pluralStringResource(R.plurals.n_playlists, playlistCount, playlistCount)
                },
                trailing = OptionRowTrailing.CHEVRON,
                onClick = onOpenPlaylists
            )
            OptionRowDivider()
            OptionRow(
                icon = Icons.Rounded.Download,
                title = stringResource(R.string.video_options_download),
                trailing = OptionRowTrailing.CHEVRON,
                onClick = onDownload
            )
            // The universal way into a creator's page. Every video-mode surface
            // opens this sheet, so putting it here is what makes "tap a
            // channel" mean the same thing in the feed, in search, in history
            // and in a playlist - rather than being a thing only the player
            // could do. Absent when the card never carried a channel id (RSS
            // and some legacy renderers), which is honest: there is nothing to
            // open.
            onOpenChannel?.let { action ->
                OptionRowDivider()
                OptionRow(
                    icon = Icons.Rounded.AccountCircle,
                    title = video.channelName.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.song_options_go_to_artist, it) }
                        ?: stringResource(R.string.video_options_go_to_channel),
                    trailing = OptionRowTrailing.CHEVRON,
                    onClick = action
                )
            }
            OptionRowDivider()
            OptionRow(
                icon = Icons.Rounded.Share,
                title = stringResource(R.string.video_options_share),
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            android.content.Intent.EXTRA_TEXT,
                            "https://youtube.com/watch?v=${video.videoId}"
                        )
                    }
                    context.startActivity(
                        android.content.Intent.createChooser(send, context.getString(R.string.video_options_share_chooser))
                    )
                }
            )
        }

        if (onNotInterested != null || onBlockChannel != null) {
            Spacer(modifier = Modifier.height(12.dp))

            // Its own group, in the muted tone the rows above are not. These
            // are destructive in a small way and share a surface with Save, so
            // they should never be the thing a thumb lands on by accident.
            OptionGroup {
                onNotInterested?.let { action ->
                    OptionRow(
                        icon = Icons.Rounded.NotInterested,
                        title = stringResource(R.string.video_options_not_interested),
                        muted = true,
                        onClick = action
                    )
                }
                onBlockChannel?.let { action ->
                    if (onNotInterested != null) OptionRowDivider()
                    OptionRow(
                        icon = Icons.Rounded.RemoveCircleOutline,
                        title = video.channelName.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.video_options_dont_recommend_channel_name, it) }
                            ?: stringResource(R.string.video_options_dont_recommend_channel),
                        muted = true,
                        onClick = action
                    )
                }
            }
        }
    }
}

/**
 * The playlist half: header and controls pinned, everything else one lazy list.
 *
 * `weight(1f, fill = false)` is what makes this safe at any length - the list
 * takes what it needs up to the space left over and shrinks below that, so
 * three playlists give a short sheet and three hundred give a scrolling one,
 * and in neither case can the footer be pushed off the bottom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistPickerPane(
    playlists: List<VideoPlaylist>,
    isLoading: Boolean,
    isSignedOut: Boolean,
    stateOf: (String) -> SaveRowState,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
    onCreatePlaylist: (() -> Unit)?,
    onDone: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    // Signed out the pinned Watch later row *is* the device's Watch Later, so
    // listing it again here would offer the same target twice under two names.
    val targets = remember(playlists, isSignedOut) {
        if (isSignedOut) {
            playlists.filterNot { it.playlistId == LocalVideoPlaylistsRepository.WATCH_LATER_ID }
        } else {
            playlists
        }
    }
    val filtered = remember(targets, query) {
        val q = query.trim()
        if (q.isEmpty()) targets else targets.filter { it.title.contains(q, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
            Text(
                text = stringResource(R.string.video_options_save_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        // A search field over six playlists is a control in the way. Over
        // sixty it is the only way to find one.
        if (targets.size >= PICKER_SEARCH_THRESHOLD) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_playlists_placeholder)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading && playlists.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator()
                }
            }

            filtered.isEmpty() -> {
                Text(
                    text = when {
                        query.isNotBlank() -> stringResource(R.string.no_playlist_matches, query.trim())
                        onCreatePlaylist != null ->
                            stringResource(R.string.no_playlists_yet_hint)
                        else -> stringResource(R.string.spotlight_empty_no_playlists)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.playlistId }) { playlist ->
                        PlaylistPickRow(
                            playlist = playlist,
                            state = stateOf(playlist.playlistId),
                            onClick = { onPick(playlist.playlistId) }
                        )
                    }
                }
            }
        }

        // Pinned: the create and close controls stay reachable however long the
        // list runs, which is the half of this pane the old sheet lost first.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onCreatePlaylist != null) {
                TextButton(onClick = onCreatePlaylist) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_new_playlist))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onDone) { Text(stringResource(R.string.action_done)) }
        }
    }
}

/** Thumbnail, title and channel, so the sheet says which card was pressed. */
@Composable
private fun VideoOptionsHeader(video: VideoItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
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
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            video.channelName.takeIf { it.isNotBlank() }?.let { channel ->
                Text(
                    text = channel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One rounded container holding a run of [OptionRow]s. */
@Composable
private fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(content = content)
    }
}

@Composable
private fun OptionRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

private enum class OptionRowTrailing { NONE, CHEVRON }

/**
 * One action inside a group: 24dp icon, title, optional subtitle, and a
 * trailing slot that shows either a chevron (this row navigates) or the
 * spinner/check/error walk (this row acts in place).
 *
 * Deliberately lighter than the standalone cards this replaced - no icon
 * plate, no per-row spring scale, ripple instead - because eight of those is
 * what did not fit.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    state: SaveRowState = SaveRowState.IDLE,
    trailing: OptionRowTrailing = OptionRowTrailing.NONE,
    muted: Boolean = false
) {
    val contentColor = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconTint: Color = if (muted) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(enabled = state != SaveRowState.SAVING, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
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

        when {
            state == SaveRowState.SAVING -> LoadingIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary
            )

            state == SaveRowState.SAVED -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_saved),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )

            state == SaveRowState.FAILED -> Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = stringResource(R.string.cd_couldnt_save),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )

            trailing == OptionRowTrailing.CHEVRON -> Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * One playlist in the picker: artwork, title, count, and the same
 * idle/spinner/check walk the action rows use.
 *
 * A checked row stays tappable-looking but is inert - saving is add-only, so a
 * second tap has nothing to do. Removing a video from a playlist belongs on
 * that playlist's own page, where the row being removed is visible.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistPickRow(
    playlist: VideoPlaylist,
    state: SaveRowState,
    onClick: () -> Unit
) {
    val saved = state == SaveRowState.SAVED

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = state == SaveRowState.IDLE || state == SaveRowState.FAILED) {
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        color = if (saved) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!playlist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (saved) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // The count and "On this device" are the two things that tell
                // one playlist from another of the same name, so both show.
                listOfNotNull(
                    playlist.videoCountText?.takeIf { it.isNotBlank() },
                    playlist.subtitle?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").takeIf { it.isNotBlank() }?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            when (state) {
                SaveRowState.SAVING -> LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                SaveRowState.SAVED -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.cd_in_this_playlist),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )

                SaveRowState.FAILED -> Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = stringResource(R.string.cd_couldnt_save),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
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
 * Name-only dialog for a playlist made from the options sheet.
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
        title = { Text(stringResource(R.string.action_new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name_label)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * [VideoOptionsSheet] wired to a [HomeViewModel], plus the download sheet it
 * hands off to.
 *
 * Every video-mode feed opens the same sheet against the same ViewModel, and
 * re-declaring those twelve arguments per surface is what let the Home tab ship
 * without `onEnqueue` - the whole queue section silently absent on one screen
 * of four, with nothing to compile-fail. One host, and a new surface is three
 * lines that cannot be half-wired.
 *
 * The player keeps its own call: it drives a `VideoPlayerViewModel`, and its
 * two actions are conditioned on the video that is currently playing.
 */
@Composable
fun VideoOptionsSheetHost(
    video: VideoItem,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onEnqueue: ((playNext: Boolean) -> Unit)? = null,
    /**
     * False where hiding the video would visibly do nothing. Search results are
     * never filtered, so the row is left out there rather than lying.
     */
    allowNotInterested: Boolean = true,
    /**
     * False in the Subscriptions feed: blocking a channel you deliberately
     * follow, from the feed that exists to show it, is a contradiction.
     */
    allowBlockChannel: Boolean = true,
    /**
     * Open this video's creator. Threaded from the screen because it is a
     * navigation, which no sheet can perform on its own; a call site that
     * leaves it null simply has no channel row.
     */
    onOpenChannel: ((channelId: String) -> Unit)? = null
) {
    val playlists by viewModel.videoPlaylists.collectAsState()
    val isLoading by viewModel.isVideoPlaylistsLoading.collectAsState()
    val isConnected by viewModel.isYouTubeConnected.collectAsState()
    val localPlaylists by viewModel.localVideoPlaylists.collectAsState()
    var showDownload by remember { mutableStateOf(false) }

    // No sign-in wall: device playlists are always a valid target, so only the
    // account's half waits on a session.
    LaunchedEffect(video.videoId, isConnected) {
        if (isConnected) viewModel.loadVideoPlaylists()
    }

    val alreadyIn = remember(localPlaylists, video.videoId, isConnected) {
        val ids = localPlaylists
            .filter { list -> list.videos.any { it.videoId == video.videoId } }
            .map { it.id }
            .toSet()
        // Signed out the pinned Watch later row saves into the device list, so
        // that is what tells it whether the video is already there.
        if (!isConnected && LocalVideoPlaylistsRepository.WATCH_LATER_ID in ids) {
            ids + WATCH_LATER_ID
        } else {
            ids
        }
    }

    if (showDownload) {
        VideoDownloadSheet(
            video = video,
            onDismiss = {
                showDownload = false
                onDismiss()
            }
        )
        return
    }

    VideoOptionsSheet(
        video = video,
        playlists = playlists,
        isLoading = isLoading,
        onSave = { playlistId, onResult ->
            viewModel.addVideoToPlaylist(playlistId, video, onResult)
        },
        onDownload = { showDownload = true },
        onDismiss = onDismiss,
        onNotInterested = if (allowNotInterested) {
            { viewModel.markNotInterested(video) }
        } else null,
        onBlockChannel = if (allowBlockChannel) {
            { viewModel.blockChannelFor(video) }
        } else null,
        isSignedOut = !isConnected,
        onCreatePlaylist = { name, onCreated ->
            viewModel.createLocalVideoPlaylist(name, onCreated)
        },
        onEnqueue = onEnqueue,
        alreadyIn = alreadyIn,
        onOpenChannel = onOpenChannel?.let { open ->
            video.channelId?.takeIf { it.startsWith("UC") }?.let { id -> { open(id) } }
        }
    )
}
