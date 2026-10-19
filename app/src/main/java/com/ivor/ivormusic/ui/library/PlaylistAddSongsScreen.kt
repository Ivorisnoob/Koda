package com.ivor.ivormusic.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import com.ivor.ivormusic.ui.components.LocalBottomOverlayInset
import com.ivor.ivormusic.ui.components.floatAboveBottomOverlays
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Add songs" from the playlist page's app bar: pick from what is already to
 * hand, or search YouTube Music, and add the whole selection in one go.
 *
 * Before a search the list is what costs nothing to show - the play history
 * and the likes Home already holds. Typing replaces it with songs from the
 * same search the Search tab runs (and, for a device playlist, matches from
 * the device library above them). The selection outlives every search, sits
 * in a tray above the add button where a tap drops a song again, and nothing
 * is written until that button is pressed.
 *
 * It serves both kinds of playlist the user can add to: a device playlist
 * takes anything, an account playlist only what YouTube can hold, so device
 * files are left out of the lists for it. A screen rather than a sheet for the
 * reason the import flow is one: a growing multi-select list is what the
 * bottom-sheet ceiling clips.
 */
@Composable
fun PlaylistAddSongsScreen(
    targetPlaylist: PlaylistDisplayItem,
    /** A device playlist, as opposed to one on the account. */
    targetIsLocal: Boolean,
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    /** The selection was written; count is how many songs landed. */
    onDone: (Int) -> Unit
) {
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val deviceSongs by viewModel.songs.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    // The query the results on screen belong to; blank while nothing is searched.
    var searchedQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }

    var existingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selected = remember { mutableStateListOf<Song>() }
    var adding by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val addFailedMessage = stringResource(R.string.addsongs_failed)

    // What the playlist already holds, so rows can say so.
    LaunchedEffect(targetPlaylist.id) {
        existingIds = runCatching { viewModel.fetchPlaylistSongs(targetPlaylist.id) }
            .getOrDefault(emptyList())
            .mapTo(HashSet()) { it.id }
    }

    // Search as the query settles. The wait is what keeps a typed word from
    // costing a request per letter.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchedQuery = ""
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(SEARCH_SETTLE_MS)
        results = viewModel.searchYouTube(trimmed).distinctBy { it.id }
        searchedQuery = trimmed
        endReached = false
        searching = false
        listState.scrollToItem(0)
    }

    // The next page once the end of the results is in view.
    LaunchedEffect(searchedQuery) {
        if (searchedQuery.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_WITHIN
        }.collect { nearEnd ->
            if (!nearEnd || loadingMore || endReached || searching) return@collect
            loadingMore = true
            val known = results.mapTo(HashSet()) { it.id }
            val fresh = viewModel.loadMoreResults(searchedQuery).filter { known.add(it.id) }
            if (fresh.isEmpty()) endReached = true else results = results + fresh
            loadingMore = false
        }
    }

    fun offered(song: Song) = targetIsLocal || song.source == SongSource.YOUTUBE

    val recents = remember(recentlyPlayed, targetIsLocal) { recentlyPlayed.filter(::offered) }
    // A liked song that was also just played is listed once, under recents.
    val liked = remember(likedSongs, recents, targetIsLocal) {
        val shown = recents.mapTo(HashSet()) { it.id }
        likedSongs.asSequence().filter(::offered).filter { shown.add(it.id) }
            .take(LIKED_SHOWN).toList()
    }
    val deviceMatches = remember(deviceSongs, searchedQuery, targetIsLocal) {
        if (!targetIsLocal || searchedQuery.isEmpty()) emptyList()
        else deviceSongs.asSequence().filter {
            it.title.contains(searchedQuery, ignoreCase = true) ||
                it.artist.contains(searchedQuery, ignoreCase = true)
        }.take(DEVICE_MATCHES_SHOWN).toList()
    }
    val onlineResults = remember(results, deviceMatches) {
        val shown = deviceMatches.mapTo(HashSet()) { it.id }
        results.filter { shown.add(it.id) }
    }

    fun toggle(song: Song) {
        val index = selected.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            selected.removeAt(index)
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
        } else {
            selected.add(song)
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.import_title),
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
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.addsongs_search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }
        },
        snackbarHost = {
            // Above the add bar, which floats over the foot of the list.
            DismissibleSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomOverlayInset.current + ADD_BAR_CLEARANCE)
            )
        }
    ) { padding ->
        val row: @Composable (Song, Modifier) -> Unit = { song, modifier ->
            val alreadyIn = song.id in existingIds
            StudioSongRow(
                song = song,
                isSelected = selected.any { it.id == song.id },
                onToggle = { toggle(song) },
                enabled = !alreadyIn,
                disabledLabel = if (alreadyIn) stringResource(R.string.import_already_added) else null,
                modifier = modifier
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Room for the last rows to scroll clear of the add bar and of
            // whatever floats beneath it.
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = LocalBottomOverlayInset.current + ADD_BAR_CLEARANCE
            )
        ) {
            when {
                // ---- Nothing searched: what is already to hand ----
                query.isBlank() -> {
                    if (recents.isEmpty() && liked.isEmpty()) {
                        item(key = "start_hint") {
                            AddSongsMessage(stringResource(R.string.addsongs_start_hint))
                        }
                    }
                    if (recents.isNotEmpty()) {
                        item(key = "recent_header") {
                            AddSongsHeader(stringResource(R.string.addsongs_recent))
                        }
                        items(recents, key = { "recent_${it.id}" }) { song ->
                            row(song, Modifier.animateItem())
                        }
                    }
                    if (liked.isNotEmpty()) {
                        item(key = "liked_header") {
                            AddSongsHeader(stringResource(R.string.addsongs_liked))
                        }
                        items(liked, key = { "liked_${it.id}" }) { song ->
                            row(song, Modifier.animateItem())
                        }
                    }
                }

                // ---- Waiting on the first page of a new query ----
                searching && searchedQuery != query.trim() -> item(key = "searching") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(modifier = Modifier.size(44.dp))
                    }
                }

                deviceMatches.isEmpty() && onlineResults.isEmpty() -> item(key = "no_results") {
                    EmptyLibraryState(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.addsongs_no_results, searchedQuery),
                        subtitle = ""
                    )
                }

                // ---- Results ----
                else -> {
                    if (deviceMatches.isNotEmpty()) {
                        item(key = "device_header") {
                            AddSongsHeader(stringResource(R.string.addsongs_on_device))
                        }
                        items(deviceMatches, key = { "device_${it.id}" }) { song ->
                            row(song, Modifier.animateItem())
                        }
                        if (onlineResults.isNotEmpty()) {
                            item(key = "online_header") {
                                AddSongsHeader(stringResource(R.string.addsongs_online))
                            }
                        }
                    }
                    items(onlineResults, key = { "result_${it.id}" }) { song ->
                        row(song, Modifier.animateItem())
                    }
                    if (loadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
        AddSongsBar(
            selected = selected,
            adding = adding,
            onRemove = { toggle(it) },
            onAdd = {
                if (adding || selected.isEmpty()) return@AddSongsBar
                adding = true
                focusManager.clearFocus()
                scope.launch {
                    val added = viewModel.addSongsToPlaylist(targetPlaylist.id, selected.toList())
                    if (added == 0) {
                        // Nothing landed: stay, with the selection intact.
                        adding = false
                        snackbarHostState.showSnackbar(addFailedMessage)
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onDone(added)
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        }
    }
}

/** The add bar's height and margins: what the list and the snackbar keep clear. */
private val ADD_BAR_CLEARANCE = 96.dp

private const val SEARCH_SETTLE_MS = 450L
private const val LOAD_MORE_WITHIN = 4
private const val LIKED_SHOWN = 40
private const val DEVICE_MATCHES_SHOWN = 8

@Composable
private fun AddSongsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun AddSongsMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp)
    )
}

/**
 * The add bar, absent until something is picked: the selection as a tray of
 * covers (tap one to drop it) beside the one action that writes it.
 *
 * It floats rather than docks. A docked bar has to hold the space of the
 * navigation pill and the mini player underneath it, and both leave on scroll,
 * which left a tall empty panel under the button. This one rests just above
 * them and follows them ([floatAboveBottomOverlays]): down to the screen edge
 * as the pill hides, narrowing to sit beside the music bubble, and up over the
 * keyboard while searching.
 */
@Composable
private fun AddSongsBar(
    selected: List<Song>,
    adding: Boolean,
    onRemove: (Song) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trayState = rememberLazyListState()

    // The cover just picked is the one to show.
    LaunchedEffect(selected.size) {
        if (selected.isNotEmpty()) trayState.animateScrollToItem(selected.lastIndex)
    }

    AnimatedVisibility(
        visible = selected.isNotEmpty(),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier.floatAboveBottomOverlays()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(32.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            ) {
                LazyRow(
                    state = trayState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(selected, key = { it.id }) { song ->
                        Box(modifier = Modifier.animateItem()) {
                            SongArtwork(
                                song = song,
                                contentDescription = song.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !adding) { onRemove(song) }
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_studio_remove_song),
                                    modifier = Modifier.padding(3.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = onAdd,
                    enabled = !adding,
                    modifier = Modifier.height(52.dp)
                ) {
                    if (adding) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.addsongs_add_n, selected.size),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
