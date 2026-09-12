package com.ivor.ivormusic.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlaylistCoverArt
import com.ivor.ivormusic.data.PlaylistDisplayItem
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.ui.components.LocalBottomOverlayInset
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.ui.theme.playlistCoverSeeds
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The immersive local-playlist creation flow: name and live generated cover on
 * top, seed chips built from the user's own listening underneath, and a song
 * list that grows sideways - every song added pulls in a related-songs shelf -
 * so a playlist can be built in one sitting rather than assembled row by row
 * from other screens.
 *
 * Seeds come from what the app actually knows. YouTube exposes no genre
 * metadata anywhere this app reads [judgement], so "genres you listen to" is
 * approximated honestly: top artists and favorites from the recency-weighted
 * taste profile ([HomeViewModel.buildPlaylistSeedProfile]), recent searches,
 * and a small curated genre list backed by ordinary music searches - the same
 * mechanism `RecommendationEngine` already uses, no new InnerTube shapes.
 *
 * Related songs are fetched once per picked song (`/next` radio, the existing
 * budget for a user action) and never re-fetched for the same id.
 */
@Composable
fun PlaylistStudioScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    /** The playlist exists; the caller decides where to go and show it. */
    onCreated: (PlaylistDisplayItem) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var showDescription by rememberSaveable { mutableStateOf(false) }
    // The user's own cover, chosen here rather than after the fact on the
    // detail page. Stored as a string for rememberSaveable; applied to the
    // playlist only at create.
    var customCoverUri by rememberSaveable { mutableStateOf<String?>(null) }

    var seedProfile by remember { mutableStateOf<HomeViewModel.PlaylistSeedProfile?>(null) }
    var activeChip by remember { mutableStateOf<StudioSeedChip?>(null) }
    var chipSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var chipLoading by remember { mutableStateOf(false) }
    var chipFailed by remember { mutableStateOf(false) }
    // Retry re-arms the LaunchedEffect below without re-picking the chip.
    var chipAttempt by remember { mutableStateOf(0) }

    val selected = remember { mutableStateListOf<Song>() }
    var suggestionGroups by remember { mutableStateOf<List<StudioSuggestionGroup>>(emptyList()) }
    // Song ids whose related-songs shelf was already requested. Not state:
    // nothing draws it, it only stops a second fetch for the same pick.
    val suggestedFor = remember { HashSet<String>() }
    var creating by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Null is the picker being backed out of, which must not clear a cover
    // already chosen - the same rule as the detail page's picker.
    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) customCoverUri = uri.toString()
    }

    LaunchedEffect(Unit) {
        seedProfile = viewModel.buildPlaylistSeedProfile()
    }

    // Picking a seed is the moment attention moves from naming to choosing
    // songs: drop the keyboard (it covers the very list that just loaded) and
    // bring the rails to the top so the songs are on screen rather than a
    // viewport below the hero. Item 1 is the first rail whether or not the
    // listening rail exists, so both rails stay visible for switching seeds.
    LaunchedEffect(activeChip?.key) {
        if (activeChip != null) {
            focusManager.clearFocus()
            listState.animateScrollToItem(1)
        }
    }

    // Load the picked chip's songs. Keyed on the attempt counter as well so
    // the failure state's retry re-runs it; switching chips cancels the
    // in-flight load with the effect.
    LaunchedEffect(activeChip, chipAttempt) {
        val chip = activeChip ?: return@LaunchedEffect
        chipFailed = false
        when (chip.kind) {
            StudioSeedKind.FAVORITES -> {
                chipSongs = seedProfile?.favorites.orEmpty()
                chipLoading = false
            }
            else -> {
                chipLoading = true
                chipSongs = emptyList()
                val result = runCatching {
                    when (chip.kind) {
                        StudioSeedKind.ARTIST -> viewModel.searchArtistSongs(chip.query)
                        else -> viewModel.searchYouTube(chip.query)
                    }
                }
                chipLoading = false
                result.fold(
                    onSuccess = { songs -> chipSongs = songs.distinctBy { it.id } },
                    onFailure = { chipFailed = true }
                )
            }
        }
    }

    fun toggleSong(song: Song) {
        val index = selected.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            selected.removeAt(index)
            haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
            return
        }
        selected.add(song)
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        // Every pick earns one related-songs shelf, once, YouTube songs only
        // (a device file has no radio), and the page stops growing at a cap
        // so a 30-song selection does not build an endless column.
        if (song.source == SongSource.YOUTUBE &&
            suggestionGroups.size < MAX_SUGGESTION_GROUPS &&
            suggestedFor.add(song.id)
        ) {
            scope.launch {
                val related = runCatching { viewModel.getRadioSongs(song.id) }
                    .getOrDefault(emptyList())
                    .asSequence()
                    .filter { it.id != song.id }
                    .distinctBy { it.id }
                    .take(SUGGESTIONS_PER_PICK)
                    .toList()
                if (related.isNotEmpty()) {
                    suggestionGroups = suggestionGroups + StudioSuggestionGroup(song, related)
                }
            }
        }
    }

    // A song already on screen in the chip list (or an earlier shelf) is not
    // shown again by a later shelf - same song twice reads as a glitch, and
    // lazy keys must be unique anyway.
    val visibleSuggestions = remember(chipSongs, suggestionGroups) {
        val seen = chipSongs.mapTo(HashSet()) { it.id }
        suggestionGroups.mapNotNull { group ->
            val fresh = group.songs.filter { seen.add(it.id) }
            if (fresh.isEmpty()) null else group.copy(songs = fresh)
        }
    }

    val canCreate = name.isNotBlank() && !creating
    val create: () -> Unit = create@{
        if (!canCreate) return@create
        creating = true
        scope.launch {
            val id = viewModel.createLocalPlaylistWithSongs(name, description, selected.toList())
            // Applied before navigating so the detail page never flashes the
            // generated cover under the one the user just chose.
            customCoverUri?.let { uri ->
                viewModel.applyLocalPlaylistCover(id, android.net.Uri.parse(uri))
            }
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onCreated(
                PlaylistDisplayItem(
                    name = name.trim(),
                    url = id,
                    uploaderName = "You",
                    itemCount = selected.distinctBy { it.id }.size
                )
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_studio_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            StudioCreateBar(
                selected = selected,
                nameBlank = name.isBlank(),
                creating = creating,
                onRemove = { toggleSong(it) },
                onCreate = create
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            // ---- Hero: generated cover beside the name ----
            // A row rather than a stacked hero on purpose: the first version
            // centred a 168dp cover over a display-size field and, with two
            // wrapping chip sections below, pushed the song list a full
            // viewport down. The cover still redraws while typing - it just
            // no longer owns the screen.
            item(key = "hero") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Box {
                        val chosen = customCoverUri
                        // The artwork is a button: tapping it opens the picker,
                        // and the camera badge says so without a caption - the
                        // detail page's affordance, at creation time.
                        if (chosen != null) {
                            AsyncImage(
                                model = chosen,
                                contentDescription = stringResource(R.string.lib_change_cover_art),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable {
                                        coverPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                            )
                        } else {
                            GeneratedCoverPreview(
                                name = name,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable {
                                        coverPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clickable {
                                    coverPicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                        ) {
                            Icon(
                                Icons.Rounded.PhotoCamera,
                                contentDescription = stringResource(R.string.lib_change_cover_art),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                        // Back to the generated artwork - only shown once
                        // there is a choice to undo.
                        if (chosen != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .clickable { customCoverUri = null }
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.lib_reset_cover),
                                    modifier = Modifier
                                        .padding(5.dp)
                                        .size(14.dp)
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.studio_name_hint),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            colors = studioFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (showDescription) {
                            TextField(
                                value = description,
                                onValueChange = { description = it },
                                placeholder = { Text(stringResource(R.string.studio_description_hint)) },
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                colors = studioFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            TextButton(
                                onClick = { showDescription = true },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text(stringResource(R.string.studio_add_description))
                            }
                        }
                    }
                }
            }

            // ---- Seed chips from the user's own listening ----
            val profile = seedProfile
            val listeningChips = buildList {
                if (profile != null) {
                    if (profile.favorites.isNotEmpty()) {
                        add(
                            StudioSeedChip(
                                key = "favorites",
                                label = context.getString(R.string.studio_recent_favorites),
                                icon = Icons.Rounded.AutoAwesome,
                                kind = StudioSeedKind.FAVORITES
                            )
                        )
                    }
                    profile.topArtists.forEach { artist ->
                        add(
                            StudioSeedChip(
                                key = "artist_$artist",
                                label = artist,
                                icon = Icons.Rounded.Person,
                                kind = StudioSeedKind.ARTIST,
                                query = artist
                            )
                        )
                    }
                    profile.recentSearches.take(3).forEach { query ->
                        add(
                            StudioSeedChip(
                                key = "search_$query",
                                label = query,
                                icon = Icons.Rounded.History,
                                kind = StudioSeedKind.SEARCH,
                                query = query
                            )
                        )
                    }
                }
            }

            if (listeningChips.isNotEmpty()) {
                item(key = "listening_section") {
                    StudioChipRail(
                        title = stringResource(R.string.studio_from_listening),
                        chips = listeningChips,
                        activeKey = activeChip?.key,
                        onChipPicked = { chip ->
                            activeChip = if (activeChip?.key == chip.key) null else chip
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "genre_section") {
                val genreChips = STUDIO_GENRE_SEEDS.map { genre ->
                    StudioSeedChip(
                        key = "genre_${genre.query}",
                        label = stringResource(genre.labelRes),
                        icon = Icons.Rounded.MusicNote,
                        kind = StudioSeedKind.SEARCH,
                        query = genre.query
                    )
                }
                StudioChipRail(
                    title = stringResource(R.string.studio_genres_moods),
                    chips = genreChips,
                    activeKey = activeChip?.key,
                    onChipPicked = { chip ->
                        activeChip = if (activeChip?.key == chip.key) null else chip
                    },
                    modifier = Modifier.animateItem()
                )
            }

            // ---- The picked chip's songs ----
            when {
                activeChip == null -> item(key = "pick_hint") {
                    Text(
                        text = stringResource(R.string.studio_pick_seed_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                            .animateItem()
                    )
                }

                chipLoading -> item(key = "chip_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp)
                            .animateItem(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(modifier = Modifier.size(44.dp))
                    }
                }

                chipFailed -> item(key = "chip_failed") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .animateItem(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.studio_seed_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { chipAttempt++ }) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

                chipSongs.isEmpty() -> item(key = "chip_empty") {
                    Text(
                        text = stringResource(R.string.studio_nothing_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .animateItem()
                    )
                }

                else -> {
                    item(key = "chip_header") {
                        StudioSectionHeader(
                            text = activeChip?.label.orEmpty(),
                            modifier = Modifier.animateItem()
                        )
                    }
                    items(chipSongs, key = { "seed_${it.id}" }) { song ->
                        StudioSongRow(
                            song = song,
                            isSelected = selected.any { it.id == song.id },
                            onToggle = { toggleSong(song) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // ---- Related shelves, one per picked song ----
            visibleSuggestions.forEach { group ->
                item(key = "sug_header_${group.seed.id}") {
                    StudioSectionHeader(
                        text = stringResource(R.string.studio_because_you_added, group.seed.title),
                        modifier = Modifier.animateItem()
                    )
                }
                items(group.songs, key = { "sug_${group.seed.id}_${it.id}" }) { song ->
                    StudioSongRow(
                        song = song,
                        isSelected = selected.any { it.id == song.id },
                        onToggle = { toggleSong(song) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Seed model
// ---------------------------------------------------------------------------

private enum class StudioSeedKind { FAVORITES, ARTIST, SEARCH }

private data class StudioSeedChip(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val kind: StudioSeedKind,
    val query: String = ""
)

private data class StudioSuggestionGroup(val seed: Song, val songs: List<Song>)

private data class StudioGenreSeed(val labelRes: Int, val query: String)

/**
 * Curated genre chips. Labels are resources; queries stay English because they
 * are YouTube Music searches, not UI. Approximation is deliberate - see the
 * screen KDoc: nothing this app reads carries genre metadata.
 */
private val STUDIO_GENRE_SEEDS = listOf(
    StudioGenreSeed(R.string.genre_pop, "pop hits"),
    StudioGenreSeed(R.string.genre_hiphop, "hip hop essentials"),
    StudioGenreSeed(R.string.genre_rnb, "r&b songs"),
    StudioGenreSeed(R.string.genre_rock, "rock classics"),
    StudioGenreSeed(R.string.genre_electronic, "electronic music"),
    StudioGenreSeed(R.string.genre_indie, "indie songs"),
    StudioGenreSeed(R.string.genre_kpop, "kpop hits"),
    StudioGenreSeed(R.string.genre_latin, "latin hits"),
    StudioGenreSeed(R.string.genre_afrobeats, "afrobeats"),
    StudioGenreSeed(R.string.genre_lofi, "lofi beats"),
    StudioGenreSeed(R.string.genre_jazz, "jazz essentials"),
    StudioGenreSeed(R.string.genre_chill, "chill songs")
)

private const val SUGGESTIONS_PER_PICK = 8
private const val MAX_SUGGESTION_GROUPS = 8

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun studioFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun StudioSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp)
    )
}

/**
 * A titled single-line rail of M3E toggle chips; one active seed at a time
 * across all rails. A horizontally scrolling row rather than a wrap on
 * purpose [scar]: the first version was two FlowRows, which at a dozen chips
 * each stacked six-plus rows of buttons between the name and the songs, and
 * the list the screen exists for started a whole viewport down.
 */
@Composable
private fun StudioChipRail(
    title: String,
    chips: List<StudioSeedChip>,
    activeKey: String?,
    onChipPicked: (StudioSeedChip) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StudioSectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(chips, key = { it.key }) { chip ->
                val checked = chip.key == activeKey
                // ToggleButton rather than FilterChip: the shape morph on
                // select is the Expressive signal these seeds are the fun
                // part of the page.
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onChipPicked(chip) }
                ) {
                    Icon(chip.icon, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        chip.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * One selectable song row. The whole row toggles; the trailing icon is the
 * state, springing between add and added, and the row surface tints while the
 * song is in the playlist so a scroll-back reads at a glance.
 */
@Composable
fun StudioSongRow(
    song: Song,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Small line under the artist for rows that cannot be picked. */
    disabledLabel: String? = null
) {
    val container by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "studioRowContainer"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "studioRowIconScale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
    ) {
        SongArtwork(
            song = song,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = disabledLabel ?: song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (enabled) {
            AnimatedContent(
                targetState = isSelected,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f))
                },
                label = "studioRowIcon"
            ) { picked ->
                Icon(
                    imageVector = if (picked) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                    contentDescription = stringResource(
                        if (picked) R.string.cd_studio_remove_song else R.string.cd_studio_add_song
                    ),
                    tint = if (picked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        }
    }
}

/**
 * Docked create bar: the selection tray (tap a cover to drop it) above one
 * full-width create action. Padded above the floating nav pill and any mini
 * player, and above the IME so the name field never hides the button.
 */
@Composable
private fun StudioCreateBar(
    selected: List<Song>,
    nameBlank: Boolean,
    creating: Boolean,
    onRemove: (Song) -> Unit,
    onCreate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = LocalBottomOverlayInset.current)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            AnimatedVisibility(
                visible = selected.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    items(selected, key = { it.id }) { song ->
                        Box(modifier = Modifier.animateItem()) {
                            SongArtwork(
                                song = song,
                                contentDescription = song.title,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onRemove(song) }
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(16.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_studio_remove_song),
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.studio_songs_selected, selected.size, selected.size
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val hint = when {
                        nameBlank -> stringResource(R.string.studio_name_first)
                        selected.isEmpty() -> stringResource(R.string.studio_empty_selection)
                        else -> null
                    }
                    if (hint != null) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = onCreate,
                    enabled = !nameBlank && !creating,
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier.height(52.dp)
                ) {
                    if (creating) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.studio_create))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Generated cover preview
// ---------------------------------------------------------------------------

/**
 * A live preview of the generated cover, redrawn as the name is typed.
 *
 * This shares [PlaylistCoverArt] with the real generator in
 * [com.ivor.ivormusic.data.PlaylistRepository] - same tonal scheme, same four
 * motif families - so what the preview promises is what the bitmap delivers.
 * It still cannot be pixel-identical by construction: the real cover is
 * seeded by the playlist's random id, which does not exist until create, so
 * this seeds by the name instead and typing makes the artwork feel alive.
 * A family added to the generator needs adding here.
 */
@Composable
private fun GeneratedCoverPreview(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val seeds = remember(context) { playlistCoverSeeds(context) }
    val seedA = seeds?.first ?: MaterialTheme.colorScheme.primary.toArgb()
    val seedB = seeds?.second ?: MaterialTheme.colorScheme.tertiary.toArgb()
    val seed = name.trim().lowercase().hashCode()

    AnimatedContent(
        targetState = seed,
        transitionSpec = {
            (fadeIn() + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )) togetherWith fadeOut()
        },
        label = "generatedCover",
        modifier = modifier
    ) { s ->
        val scheme = remember(seedA, seedB, s) { PlaylistCoverArt.scheme(seedA, seedB, s) }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rng = Random(s)
            val family = PlaylistCoverArt.family(s)
            val w = size.width
            val h = size.height

            val overlay = Color(scheme.overlay)
            val accent = Color(scheme.accent)
            val fillAlpha = if (scheme.lightBase) 44 else 56
            val lineAlpha = if (scheme.lightBase) 150 else 175
            fun ink(alpha: Int) = overlay.copy(alpha = alpha / 255f)

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(scheme.top), Color(scheme.bottom)),
                    start = Offset.Zero,
                    end = Offset(w * 0.22f, h)
                )
            )

            when (family) {
                0 -> {
                    // Arch with halo ring, accent sun above.
                    val cx = w * (0.35f + rng.nextFloat() * 0.3f)
                    val cy = h * (1.05f + rng.nextFloat() * 0.08f)
                    val r = w * (0.55f + rng.nextFloat() * 0.12f)
                    drawCircle(ink(fillAlpha), radius = r, center = Offset(cx, cy))
                    drawCircle(
                        ink(lineAlpha),
                        radius = r * (1.16f + rng.nextFloat() * 0.08f),
                        center = Offset(cx, cy),
                        style = Stroke(width = w * 0.011f)
                    )
                    drawCircle(
                        accent,
                        radius = w * 0.07f,
                        center = Offset(
                            cx + (rng.nextFloat() - 0.5f) * w * 0.3f,
                            (cy - r * (1.42f + rng.nextFloat() * 0.12f)).coerceAtLeast(h * 0.14f)
                        )
                    )
                }
                1 -> {
                    // Concentric rings off a top corner, accent low.
                    val cx = w * (0.68f + rng.nextFloat() * 0.2f)
                    val cy = h * (0.18f + rng.nextFloat() * 0.16f)
                    val r0 = w * (0.11f + rng.nextFloat() * 0.04f)
                    drawCircle(ink(fillAlpha + 20), radius = r0 * 0.55f, center = Offset(cx, cy))
                    for (i in 0 until 3) {
                        drawCircle(
                            ink(lineAlpha - i * 42),
                            radius = r0 * (1f + i * 0.62f),
                            center = Offset(cx, cy),
                            style = Stroke(width = w * 0.013f)
                        )
                    }
                    drawCircle(
                        accent,
                        radius = w * 0.075f,
                        center = Offset(
                            w * (0.18f + rng.nextFloat() * 0.12f),
                            h * (0.68f + rng.nextFloat() * 0.12f)
                        )
                    )
                }
                2 -> {
                    // Ribbons sweeping the lower half, accent high.
                    val bandWidth = w * 0.105f
                    val firstY = h * (0.46f + rng.nextFloat() * 0.12f)
                    rotate(
                        degrees = -14f + rng.nextFloat() * 7f,
                        pivot = Offset(w / 2f, h / 2f)
                    ) {
                        for (i in 0 until 3) {
                            val y = firstY + i * bandWidth * 1.65f
                            drawLine(
                                color = ink((fillAlpha + 26 - i * 16).coerceAtLeast(14)),
                                start = Offset(w * (0.10f + i * 0.07f + rng.nextFloat() * 0.05f), y),
                                end = Offset(w * 1.3f, y),
                                strokeWidth = bandWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    drawCircle(
                        accent,
                        radius = w * 0.06f,
                        center = Offset(
                            w * (0.2f + rng.nextFloat() * 0.15f),
                            h * (0.16f + rng.nextFloat() * 0.12f)
                        )
                    )
                }
                else -> {
                    // Bauhaus corners: filled quarter low-left, stroked
                    // quarter answering top-right.
                    drawCircle(
                        ink(fillAlpha),
                        radius = w * (0.52f + rng.nextFloat() * 0.16f),
                        center = Offset(0f, h)
                    )
                    drawCircle(
                        ink(lineAlpha),
                        radius = w * (0.30f + rng.nextFloat() * 0.12f),
                        center = Offset(w, 0f),
                        style = Stroke(width = w * 0.013f)
                    )
                    drawCircle(
                        accent,
                        radius = w * 0.065f,
                        center = Offset(
                            w * (0.60f + rng.nextFloat() * 0.16f),
                            h * (0.52f + rng.nextFloat() * 0.16f)
                        )
                    )
                }
            }
        }
    }
}
