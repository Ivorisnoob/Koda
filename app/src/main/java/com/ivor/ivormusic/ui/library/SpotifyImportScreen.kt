package com.ivor.ivormusic.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SpotifyCollection
import com.ivor.ivormusic.data.SpotifyLinks
import com.ivor.ivormusic.data.SpotifyTrack
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.ui.components.LocalBottomOverlayInset
import com.ivor.ivormusic.ui.components.floatAboveBottomOverlays
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bring a Spotify playlist or album into Koda as a playlist of the same songs
 * on YouTube Music.
 *
 * Koda reads the playlist's public track list - title, artist, length - and
 * looks each song up on YouTube Music. **Only exact matches are taken**: the
 * same title, the same artist and the same length. A song with no exact match
 * is shown as not found and left out, never swapped for the nearest thing, so
 * what is created is the playlist rather than a guess at it. Matches arrive
 * one at a time as the list fills in, can be unticked before anything is
 * saved, and the result is an ordinary device playlist.
 *
 * Nothing is played or taken from Spotify, and no Spotify account is used.
 *
 * It is one search per song, paced, and it stops if YouTube starts refusing
 * requests rather than push into the limit; what was matched by then can still
 * be saved.
 */
@Composable
fun SpotifyImportScreen(
    /** A link handed over from a share, or null to start with the paste field. */
    initialLink: String?,
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    /** The playlist exists; the caller decides where to go and show it. */
    onCreated: (PlaylistDisplayItem) -> Unit
) {
    var linkText by rememberSaveable { mutableStateOf(initialLink.orEmpty()) }
    // The link being imported. Set from the field's button, or straight away
    // for a shared link.
    var submitted by rememberSaveable { mutableStateOf(initialLink) }
    var loading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf<SpotifyCollection?>(null) }
    val rows = remember { mutableStateListOf<ImportRow>() }
    var matchedThrough by remember { mutableStateOf(0) }
    var stoppedByLimit by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val focusManager = LocalFocusManager.current
    val importedDescription = stringResource(R.string.spotify_import_description)

    LaunchedEffect(submitted) {
        val ref = submitted?.let(SpotifyLinks::parse) ?: return@LaunchedEffect
        loading = true
        loadFailed = false
        collection = null
        rows.clear()
        matchedThrough = 0
        stoppedByLimit = false
        val loaded = viewModel.loadSpotifyCollection(ref)
        loading = false
        if (loaded == null || loaded.tracks.isEmpty()) {
            loadFailed = true
            return@LaunchedEffect
        }
        collection = loaded
        rows.addAll(loaded.tracks.map { ImportRow(it) })
        val taken = HashSet<String>()
        for (index in loaded.tracks.indices) {
            if (YouTubeRateLimit.isHeld()) {
                stoppedByLimit = true
                break
            }
            val song = viewModel.matchSpotifyTrack(loaded.tracks[index])
                // The same song listed twice on Spotify is one row here: a
                // device playlist holds a song once.
                ?.takeIf { taken.add(it.id) }
            rows[index] = rows[index].copy(searched = true, match = song, included = song != null)
            matchedThrough = index + 1
            delay(MATCH_PACE_MS)
        }
    }

    val total = collection?.tracks?.size ?: 0
    val matching = collection != null && matchedThrough < total && !stoppedByLimit
    val chosen = rows.filter { it.included && it.match != null }
    val linkValid = SpotifyLinks.parse(linkText) != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = collection?.name?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.spotify_import_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val owner = collection?.owner
                            if (owner != null) {
                                Text(
                                    text = owner,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
                if (collection != null) {
                    // How far the matching has got, and what it has found.
                    Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
                        Text(
                            text = if (matching) {
                                stringResource(R.string.spotify_import_progress, matchedThrough, total)
                            } else {
                                stringResource(
                                    R.string.spotify_import_result,
                                    rows.count { it.match != null }, total
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (matching) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (total == 0) 0f else matchedThrough.toFloat() / total },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = LocalBottomOverlayInset.current + 96.dp
                )
            ) {
                // ---- The link ----
                if (collection == null) {
                    item(key = "link") {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = stringResource(R.string.spotify_import_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            TextField(
                                value = linkText,
                                onValueChange = { linkText = it },
                                placeholder = { Text(stringResource(R.string.spotify_import_link_hint)) },
                                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                trailingIcon = if (linkText.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { linkText = "" }) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.cd_clear_search)
                                            )
                                        }
                                    }
                                } else null,
                                singleLine = true,
                                enabled = !loading,
                                shape = RoundedCornerShape(28.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    if (linkValid) {
                                        focusManager.clearFocus()
                                        submitted = linkText
                                    }
                                }),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    // A second press on the same link is a retry.
                                    if (submitted == linkText) submitted = "$linkText " else submitted = linkText
                                },
                                enabled = linkValid && !loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                if (loading) {
                                    LoadingIndicator(modifier = Modifier.size(22.dp))
                                } else {
                                    Text(stringResource(R.string.spotify_import_find))
                                }
                            }
                        }
                    }
                    if (loadFailed) {
                        item(key = "load_failed") {
                            EmptyLibraryState(
                                icon = Icons.Rounded.LinkOff,
                                title = stringResource(R.string.spotify_import_load_failed),
                                subtitle = stringResource(R.string.spotify_import_load_failed_hint)
                            )
                        }
                    }
                }

                // ---- The songs ----
                val loaded = collection
                if (loaded != null) {
                    if (loaded.mayBeTruncated) {
                        item(key = "truncated") {
                            ImportNote(stringResource(R.string.spotify_import_truncated))
                        }
                    }
                    if (stoppedByLimit) {
                        item(key = "limited") {
                            ImportNote(stringResource(R.string.spotify_import_rate_limited))
                        }
                    }
                    itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                        val match = row.match
                        when {
                            match != null -> StudioSongRow(
                                song = match,
                                isSelected = row.included,
                                onToggle = {
                                    rows[index] = row.copy(included = !row.included)
                                    haptics.performHapticFeedback(
                                        if (row.included) HapticFeedbackType.ToggleOff
                                        else HapticFeedbackType.ToggleOn
                                    )
                                }
                            )

                            else -> UnmatchedRow(track = row.track, searched = row.searched)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = collection != null && chosen.isNotEmpty(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .floatAboveBottomOverlays()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(32.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                ) {
                    Button(
                        onClick = {
                            val loaded = collection ?: return@Button
                            if (creating) return@Button
                            creating = true
                            scope.launch {
                                val songs = chosen.mapNotNull { it.match }
                                val name = loaded.name.ifBlank { importedDescription }
                                val id = viewModel.createLocalPlaylistWithSongs(
                                    name, importedDescription, songs
                                )
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onCreated(
                                    PlaylistDisplayItem(
                                        name = name,
                                        url = id,
                                        uploaderName = "You",
                                        itemCount = songs.size
                                    )
                                )
                            }
                        },
                        // Saving early is allowed once the search has stopped
                        // for any reason; while it runs, the count is moving.
                        enabled = !creating && !matching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .height(52.dp)
                    ) {
                        if (creating) {
                            LoadingIndicator(modifier = Modifier.size(22.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = if (matching) {
                                    stringResource(R.string.spotify_import_matching)
                                } else {
                                    pluralStringResource(
                                        R.plurals.spotify_import_create_n, chosen.size, chosen.size
                                    )
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A gap between searches: a hundred back to back is the traffic a rate limit exists for. */
private const val MATCH_PACE_MS = 250L

private data class ImportRow(
    val track: SpotifyTrack,
    val searched: Boolean = false,
    val match: Song? = null,
    val included: Boolean = false,
)

@Composable
private fun ImportNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/** A Spotify song with no exact match yet, or none at all: named, dimmed, not selectable. */
@Composable
private fun UnmatchedRow(track: SpotifyTrack, searched: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (searched) {
                Icon(
                    Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (searched) stringResource(R.string.spotify_import_no_match, track.artists)
                else track.artists,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
