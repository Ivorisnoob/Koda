package com.ivor.ivormusic.ui.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.VideoPlaylist
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.PlaylistRowSkeleton
import com.ivor.ivormusic.ui.components.SkeletonList
import com.ivor.ivormusic.ui.components.PredictiveBackStack
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * Feeds with no keepable playlist behind them. Watch Later and Liked videos are
 * the account's own built-ins, already in the library by definition, and the
 * music side's synthesized entries ("LM" likes, "RTM" Supermix) are assembled
 * rather than published. Same list as `LibraryScreen`'s, in the ids video mode
 * uses them under.
 */
private val NON_SAVABLE_VIDEO_PLAYLIST_IDS =
    setOf("WL", "VLWL", "LL", "VLLL", "LM", "VLLM", "RTM")

/** Internal navigation state of the Library tab. */
private sealed interface LibraryPage {
    data object Root : LibraryPage
    data object History : LibraryPage
    data class Playlist(val playlist: VideoPlaylist) : LibraryPage
}

/**
 * Video-mode Library tab: pinned Watch Later / Liked videos entries, a watch
 * history preview and the user's YouTube playlists, with playlist drill-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryContent(
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onLoginClick: () -> Unit,
    contentPadding: PaddingValues,
    /**
     * Hoisted by HomeScreen for the tab's root page only. History and playlist
     * pages are drill-ins popped with Back, so scrolling the root beneath them
     * would act on a list the user cannot see.
     */
    rootListState: LazyListState = rememberLazyListState()
) {
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsState()
    val historyVideos by viewModel.historyVideos.collectAsState()
    val playlists by viewModel.videoPlaylists.collectAsState()
    val isPlaylistsLoading by viewModel.isVideoPlaylistsLoading.collectAsState()
    // Playlists kept as references, from either mode. Device-local, so unlike
    // the account's own they are there signed out.
    val savedPlaylists by viewModel.savedVideoPlaylists.collectAsState()

    var page by remember { mutableStateOf<LibraryPage>(LibraryPage.Root) }

    LaunchedEffect(isYouTubeConnected) {
        if (historyVideos.isEmpty()) viewModel.loadYouTubeHistory()
        if (isYouTubeConnected) viewModel.loadVideoPlaylists()
    }

    PredictiveBackStack(
        childOpen = page != LibraryPage.Root,
        onBack = { page = LibraryPage.Root },
        background = {
            LibraryRoot(
                isLoggedIn = isYouTubeConnected,
                historyVideos = historyVideos,
                playlists = playlists,
                savedPlaylists = savedPlaylists,
                isPlaylistsLoading = isPlaylistsLoading,
                onVideoClick = onVideoClick,
                onLoginClick = onLoginClick,
                onOpenHistory = { page = LibraryPage.History },
                onOpenPlaylist = { playlist ->
                    viewModel.loadPlaylistVideos(playlist.playlistId)
                    page = LibraryPage.Playlist(playlist)
                },
                onCreatePlaylist = { name -> viewModel.createVideoPlaylist(name) },
                onDeletePlaylist = { playlist -> viewModel.deleteVideoPlaylist(playlist.playlistId) },
                onRemoveSavedPlaylist = { playlist ->
                    viewModel.removeSavedPlaylist(playlist.playlistId)
                },
                listState = rootListState,
                onRefresh = {
                    viewModel.loadYouTubeHistory()
                    if (isYouTubeConnected) viewModel.loadVideoPlaylists(force = true)
                },
                contentPadding = contentPadding
            )
        }
    ) { committedByGesture ->
    AnimatedContent(
        targetState = page,
        label = "LibraryPage",
        transitionSpec = {
            val content = when {
                // The finger already performed this exit.
                committedByGesture -> EnterTransition.None togetherWith ExitTransition.None
                targetState == LibraryPage.Root ->
                    fadeIn() togetherWith (slideOutHorizontally { it } + fadeOut())
                else ->
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
            }
            // The Root state of this layer is empty, so the default
            // SizeTransform would animate the container between nothing and
            // full screen and clip the page to it on the way.
            content using SizeTransform(clip = false) { _, _ -> snap() }
        }
    ) { target ->
        when (target) {
            // The root lives underneath now; this layer is empty over it, and
            // full size so both states measure the same.
            is LibraryPage.Root -> Spacer(Modifier.fillMaxSize())

            is LibraryPage.History -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SubPageTopBar(title = "Watch History", onBack = { page = LibraryPage.Root })
                VideoHistoryContent(
                    viewModel = viewModel,
                    onVideoClick = onVideoClick,
                    onLoginClick = onLoginClick,
                    contentPadding = contentPadding,
                    showHero = false
                )
            }

            is LibraryPage.Playlist -> VideoPlaylistDetail(
                playlist = target.playlist,
                viewModel = viewModel,
                onVideoClick = onVideoClick,
                onBack = { page = LibraryPage.Root },
                contentPadding = contentPadding
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryRoot(
    isLoggedIn: Boolean,
    historyVideos: List<VideoItem>,
    playlists: List<VideoPlaylist>,
    savedPlaylists: List<VideoPlaylist>,
    isPlaylistsLoading: Boolean,
    onVideoClick: (VideoItem) -> Unit,
    onLoginClick: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPlaylist: (VideoPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (VideoPlaylist) -> Unit,
    onRemoveSavedPlaylist: (VideoPlaylist) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState()
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateVideoPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }

    ExpressivePullToRefresh(
        // The pull spinner only represents a refresh over existing playlists;
        // the empty first load is the skeleton's job below.
        isRefreshing = isPlaylistsLoading && playlists.isNotEmpty(),
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your playlists, history and saved videos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pinned entries: Watch Later + Liked videos (need login)
            if (isLoggedIn) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PinnedLibraryCard(
                            title = "Watch Later",
                            icon = Icons.Rounded.WatchLater,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                onOpenPlaylist(VideoPlaylist("WL", "Watch Later"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PinnedLibraryCard(
                            title = "Liked Videos",
                            icon = Icons.Rounded.ThumbUp,
                            tint = MaterialTheme.colorScheme.tertiary,
                            onClick = {
                                onOpenPlaylist(VideoPlaylist("LL", "Liked Videos"))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // History preview
            if (historyVideos.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "History",
                        actionLabel = "View all",
                        onAction = onOpenHistory
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyVideos.take(12)) { video ->
                            HistoryPreviewCard(video = video, onClick = { onVideoClick(video) })
                        }
                    }
                }
            } else {
                item {
                    SectionHeader(title = "History", actionLabel = "View all", onAction = onOpenHistory)
                }
            }

            // Playlists
            item {
                SectionHeader(
                    title = "Your Playlists",
                    actionLabel = if (isLoggedIn) "New" else null,
                    actionIcon = Icons.Rounded.Add,
                    onAction = if (isLoggedIn) {
                        { showCreateDialog = true }
                    } else null
                )
            }
            // Saved ones lead, the way they do in the music Library grid: not
            // the user's own, but deliberately kept, so they belong above the
            // account's list rather than lost at the end of it. They are also
            // the half that survives being signed out.
            items(savedPlaylists, key = { "saved_${it.playlistId}" }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) },
                    isSaved = true,
                    onRemoveSaved = { onRemoveSavedPlaylist(playlist) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            when {
                // Signed out only the account half is unreachable. The saved
                // rows above are still there, so the invitation goes underneath
                // what the user already has instead of replacing it.
                !isLoggedIn -> item { LibraryLoginCard(onLoginClick = onLoginClick) }

                isPlaylistsLoading && playlists.isEmpty() -> item {
                    SkeletonList(
                        count = 4,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        spacing = 8.dp
                    ) { alpha -> PlaylistRowSkeleton(alpha = alpha) }
                }

                playlists.isEmpty() && savedPlaylists.isEmpty() -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playlists yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> items(playlists, key = { it.playlistId }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist) },
                        onDelete = { onDeletePlaylist(playlist) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    actionIcon: ImageVector = Icons.Rounded.ChevronRight,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateVideoPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PinnedLibraryCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        // Clip before the click handler so the ripple follows the card's corners
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryPreviewCard(
    video: VideoItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (!video.isLive && video.duration > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = video.formattedDuration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = video.channelName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: VideoPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    /**
     * Kept, not owned. The row looks the same either way, and the actions
     * behind it are not: deleting somebody else's playlist is a write the
     * account has no rights to, so a saved row offers removing the reference
     * instead.
     */
    isSaved: Boolean = false,
    onRemoveSaved: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete playlist?") },
            text = { Text("This will permanently remove \"${playlist.title}\" from your YouTube account.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Surface(
        // Clip before the click handler so the ripple follows the card's corners
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!playlist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
                if (playlist.videoCountText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                )
                            )
                    ) {
                        Text(
                            text = playlist.videoCountText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Saved is marked in the subtitle's own tone rather than with a
                // badge on the artwork: it is a fact about the row, not a
                // status worth shouting, and the author beside it is what
                // actually tells a kept playlist from one of your own.
                if (isSaved) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = listOfNotNull(
                                "Saved",
                                playlist.subtitle?.takeIf { it.isNotBlank() }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (playlist.subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = playlist.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSaved && onRemoveSaved != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Playlist options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Removing the reference, not the playlist: no
                        // confirmation, because nothing is destroyed and saving
                        // it again is one tap on its page.
                        DropdownMenuItem(
                            text = { Text("Remove from library") },
                            onClick = {
                                showMenu = false
                                onRemoveSaved()
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.BookmarkRemove, contentDescription = null)
                            }
                        )
                    }
                }
            } else if (onDelete != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Playlist options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LibraryLoginCard(onLoginClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Log in to see your playlists, Watch Later and liked videos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.Login, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Log in to YouTube")
            }
        }
    }
}

@Composable
private fun SubPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/**
 * Playlist contents page: used by the Library tab drill-in and by video-mode
 * search results. [allowRemove] hides the remove action for playlists the
 * user doesn't own (e.g. ones found through search).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlaylistDetail(
    playlist: VideoPlaylist,
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    allowRemove: Boolean = true
) {
    val videos by viewModel.playlistVideos.collectAsState()
    val isLoading by viewModel.isPlaylistVideosLoading.collectAsState()

    // Keeping the playlist you just found is the whole reason for arriving here
    // from search, so it sits in the top bar next to Share rather than behind
    // anything. Offered only on playlists that are not already the user's: the
    // account's own are in the library by definition, and the pinned feeds have
    // no published playlist behind them to keep.
    val savedPlaylistIds by viewModel.savedVideoPlaylistIds.collectAsState()
    val accountPlaylists by viewModel.videoPlaylists.collectAsState()
    val isSaved = playlist.playlistId in savedPlaylistIds
    val canSave = playlist.playlistId !in NON_SAVABLE_VIDEO_PLAYLIST_IDS &&
        (isSaved || accountPlaylists.none { it.playlistId == playlist.playlistId })
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Watch Later ("WL") and Liked ("LL") are private, non-shareable feeds;
        // only real YouTube playlists have a public URL.
        val isShareable = playlist.playlistId != "WL" && playlist.playlistId != "LL"
        SubPageTopBar(
            title = playlist.title,
            onBack = onBack,
            actions = {
                if (canSave) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.toggleSavedVideoPlaylist(playlist)
                        }
                    ) {
                        // Crossfade rather than a spatial spec: the icon must
                        // not move or resize under the finger still on it.
                        AnimatedContent(
                            targetState = isSaved,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "saveVideoPlaylist"
                        ) { saved ->
                            Icon(
                                imageVector = if (saved) Icons.Rounded.BookmarkAdded
                                    else Icons.Rounded.BookmarkAdd,
                                contentDescription = if (saved) "Remove from library"
                                    else "Save to library",
                                tint = if (saved) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                if (isShareable) {
                    val shareContext = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "https://youtube.com/playlist?list=${playlist.playlistId}"
                            )
                        }
                        shareContext.startActivity(
                            android.content.Intent.createChooser(send, "Share playlist")
                        )
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share playlist")
                    }
                }
            }
        )

        if (isLoading && videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else if (videos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No videos in this playlist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Index-qualified: YouTube playlists can contain the same video
                // twice, and duplicate LazyColumn keys crash
                itemsIndexed(videos, key = { index, video -> "${video.videoId}_$index" }) { _, video ->
                    PlaylistVideoRow(
                        video = video,
                        onClick = { onVideoClick(video) },
                        onRemove = if (allowRemove) {
                            { viewModel.removePlaylistVideo(playlist.playlistId, video) }
                        } else null,
                        removeLabel = when (playlist.playlistId) {
                            "WL" -> "Remove from Watch Later"
                            "LL" -> "Remove from Liked videos"
                            else -> "Remove from playlist"
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PlaylistVideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove from playlist"
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        // Clip before the click handler so the ripple follows the card's corners
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (!video.isLive && video.duration > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = video.formattedDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channelName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    video.viewCount.takeIf { it.isNotBlank() },
                    video.uploadedDate?.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onRemove != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Video options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(removeLabel) },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }
                        )
                    }
                }
            }
        }
    }
}
