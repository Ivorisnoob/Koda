package com.ivor.ivormusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import androidx.compose.runtime.collectAsState
import com.ivor.ivormusic.ui.components.LocalBottomOverlayInset
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.launch

/**
 * "Add from your playlists": copy songs from any playlist in the library into
 * a local one. Two steps in one screen - pick a source, then pick songs - with
 * an in-place back between them.
 *
 * Any source works, including the account's playlists, saved references and
 * Liked Songs: the songs are *copied into* the user's own local playlist, the
 * same read `copyPlaylistToLocal` already performs, so no ownership rule is
 * crossed. Songs already in the target are shown but disabled - visible truth
 * beats a silently shorter list - and the write itself deduplicates again in
 * [com.ivor.ivormusic.data.PlaylistRepository.addSongsToPlaylist].
 */
@Composable
fun PlaylistImportScreen(
    targetPlaylist: PlaylistDisplayItem,
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    /** Selection landed; count is how many songs were genuinely new. */
    onDone: (Int) -> Unit
) {
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()

    var source by remember { mutableStateOf<PlaylistDisplayItem?>(null) }
    var sourceSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var sourceLoading by remember { mutableStateOf(false) }
    var existingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sort by remember { mutableStateOf(ImportSort.PLAYLIST_ORDER) }
    var showSortMenu by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()

    // What the target already holds, so rows can say so. Local, so instant.
    LaunchedEffect(targetPlaylist.id) {
        existingIds = viewModel.fetchPlaylistSongs(targetPlaylist.id).mapTo(HashSet()) { it.id }
    }

    LaunchedEffect(source?.id) {
        val picked = source ?: return@LaunchedEffect
        sourceLoading = true
        sourceSongs = emptyList()
        selectedIds = emptySet()
        sort = ImportSort.PLAYLIST_ORDER
        // Duplicate entries collapse to one row: selection is by id, and two
        // rows toggling together reads as a bug.
        sourceSongs = runCatching { viewModel.fetchPlaylistSongs(picked.id) }
            .getOrDefault(emptyList())
            .distinctBy { it.id }
        sourceLoading = false
    }

    // In-step back: leaving the song list returns to the source list rather
    // than the playlist. Registered inside this screen so it wins over the
    // Library-level PredictiveBackHandler while a source is open.
    BackHandler(enabled = source != null) { source = null }

    val sortedSongs = remember(sourceSongs, sort) {
        when (sort) {
            ImportSort.PLAYLIST_ORDER -> sourceSongs
            ImportSort.TITLE -> sourceSongs.sortedBy { it.title.lowercase() }
            ImportSort.ARTIST -> sourceSongs.sortedWith(
                compareBy({ it.artist.lowercase() }, { it.title.lowercase() })
            )
        }
    }
    val addableIds = remember(sourceSongs, existingIds) {
        sourceSongs.asSequence().map { it.id }.filterNot { it in existingIds }.toSet()
    }
    val allSelected = addableIds.isNotEmpty() && selectedIds.size == addableIds.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = source?.name ?: stringResource(R.string.import_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = targetPlaylist.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (source != null) source = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (source != null && !sourceLoading && addableIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds = if (allSelected) emptySet() else addableIds
                                haptics.performHapticFeedback(
                                    if (allSelected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                                )
                            }
                        ) {
                            Text(
                                stringResource(
                                    if (allSelected) R.string.import_clear_selection
                                    else R.string.import_select_all
                                )
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.cd_import_sort))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ImportSort.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(option.labelRes)) },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = sort == option,
                                                onClick = null
                                            )
                                        },
                                        onClick = {
                                            sort = option
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = source != null && selectedIds.isNotEmpty(),
                enter = fadeIn() + slideInHorizontally { it / 8 },
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = LocalBottomOverlayInset.current)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                if (adding) return@Button
                                adding = true
                                scope.launch {
                                    val chosen = sortedSongs.filter { it.id in selectedIds }
                                    val added = viewModel.addSongsToLocalPlaylist(
                                        targetPlaylist.id, chosen
                                    )
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onDone(added)
                                }
                            },
                            enabled = !adding,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (adding) {
                                LoadingIndicator(modifier = Modifier.size(22.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(20.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    pluralStringResource(
                                        R.plurals.import_add_n_songs,
                                        selectedIds.size,
                                        selectedIds.size
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = source,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "importPhase",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) { picked ->
            if (picked == null) {
                // ---- Step 1: pick a source ----
                val sources = remember(userPlaylists, targetPlaylist.id, likedSongs) {
                    buildList {
                        if (likedSongs.isNotEmpty()) {
                            add(
                                PlaylistDisplayItem(
                                    name = "Liked Songs",
                                    url = "LM",
                                    uploaderName = "You",
                                    itemCount = likedSongs.size
                                )
                            )
                        }
                        userPlaylists
                            .filterNot { it.id == targetPlaylist.id || it.id == "LM" }
                            .forEach { add(it) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = LocalBottomOverlayInset.current + 24.dp
                    )
                ) {
                    item(key = "intro") {
                        Text(
                            text = stringResource(R.string.import_pick_source, targetPlaylist.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                    if (sources.isEmpty()) {
                        item(key = "no_sources") {
                            EmptyLibraryState(
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                title = stringResource(R.string.import_no_source_playlists),
                                subtitle = ""
                            )
                        }
                    }
                    items(sources, key = { it.id }) { playlist ->
                        ImportSourceRow(
                            playlist = playlist,
                            isLiked = playlist.id == "LM",
                            onClick = { source = playlist },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            } else {
                // ---- Step 2: pick songs ----
                when {
                    sourceLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(modifier = Modifier.size(44.dp))
                    }

                    sourceSongs.isEmpty() -> EmptyLibraryState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        title = stringResource(R.string.import_source_empty),
                        subtitle = ""
                    )

                    addableIds.isEmpty() -> EmptyLibraryState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        title = stringResource(R.string.import_all_added),
                        subtitle = ""
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = LocalBottomOverlayInset.current + 96.dp
                        )
                    ) {
                        items(sortedSongs, key = { it.id }) { song ->
                            val alreadyIn = song.id in existingIds
                            StudioSongRow(
                                song = song,
                                isSelected = song.id in selectedIds,
                                onToggle = {
                                    val on = song.id !in selectedIds
                                    selectedIds = if (on) selectedIds + song.id else selectedIds - song.id
                                    haptics.performHapticFeedback(
                                        if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                                    )
                                },
                                enabled = !alreadyIn,
                                disabledLabel = if (alreadyIn) {
                                    stringResource(R.string.import_already_added)
                                } else null,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ImportSort(val labelRes: Int) {
    PLAYLIST_ORDER(R.string.import_sort_playlist_order),
    TITLE(R.string.import_sort_title),
    ARTIST(R.string.import_sort_artist)
}

@Composable
private fun ImportSourceRow(
    playlist: PlaylistDisplayItem,
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(56.dp)
        ) {
            if (isLiked) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else if (!playlist.thumbnailUrl.isNullOrBlank() && playlist.thumbnailUrl != "null") {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                playlist.uploaderName.takeIf { it.isNotBlank() },
                playlist.itemCount.takeIf { it >= 0 }?.let { count ->
                    pluralStringResource(R.plurals.studio_songs_selected, count, count)
                }
            ).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.PlaylistAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
    }
}
