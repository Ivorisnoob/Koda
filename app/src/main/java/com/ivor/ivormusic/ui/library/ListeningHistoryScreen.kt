package com.ivor.ivormusic.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.PlayHistoryEntry
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.SongSource
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * How far back each range reaches. The three cover the questions people
 * actually arrive with: what have I been playing just now, what was that thing
 * from earlier in the week, and the whole log.
 */
private enum class HistoryRange(val label: String, val daysBack: Int?) {
    Today("Today", 0),
    Week("This week", 6),
    All("All time", null)
}

/** One row: a play, plus any consecutive repeats of the same song folded in. */
private data class HistoryRun(
    val entry: PlayHistoryEntry,
    val playCount: Int,
    /** Null when the play cannot be replayed - a local file no longer on the device. */
    val song: Song?
) {
    val key: String get() = "${entry.songId}_${entry.timestamp}"
}

/** A day's worth of runs, with the totals its header shows. */
private data class HistoryDay(
    val label: String,
    val epochDay: Long,
    val runs: List<HistoryRun>,
    val plays: Int,
    val listenedMs: Long
)

/**
 * The listening history: every song that has played, newest first.
 *
 * Video mode has had `VideoHistoryContent` since the beginning and music had no
 * equivalent, which is the asymmetry people notice fastest - it is the same app
 * in two modes and one of them forgot. The data was never the missing part:
 * `StatsRepository` has recorded plays all along, and the statistics screen, the
 * Library's recently-played rail, its "Most played" sort and the taste profile
 * behind recommendations are all already built on it. This is the surface that
 * lets someone see the log itself and play something again.
 *
 * **It is a log, not a list of songs, and the design follows from that.** The
 * same song on repeat writes an entry every time, so an honest chronological
 * list of a single evening is mostly one title over and over. Consecutive plays
 * therefore collapse into one row carrying a count, the way a chat client folds
 * repeated messages: nothing is hidden, the sequence stays truthful, and the
 * list stays readable. Days are the other axis that matters - "what was I
 * playing last Tuesday" is the question this screen exists to answer - so rows
 * sit under sticky day headers that carry that day's totals.
 *
 * **Everything here is local and works signed out.** Nothing is fetched, there
 * is no account path and no empty-because-offline state; the file either has
 * entries or it does not.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ListeningHistoryScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val history by viewModel.playHistory.collectAsState()
    val isLoading by viewModel.isPlayHistoryLoading.collectAsState()
    val localSongs by viewModel.songs.collectAsState()
    val globalStats by viewModel.globalStats.collectAsState()

    // The pause toggle lives here as well as in Settings, because this is where
    // someone is standing when the thought occurs. Its own ThemePreferences
    // instance, like every other consumer - the flow is what keeps this screen
    // honest while it is open.
    val themePreferences = remember(context) { ThemePreferences(context) }
    val saveHistory by themePreferences.saveMusicHistory.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPlayHistory()
        viewModel.refreshStats()
    }

    var range by remember { mutableStateOf(HistoryRange.Week) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // Time and day formats follow the device: a 24-hour phone must not be shown
    // "2:14 PM", and a day header reading "12 Aug" is wrong in half the world.
    val timeFormat = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val weekdayFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("d MMMM", Locale.getDefault()) }
    val datedYearFormat = remember { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }

    // Grouped, filtered, collapsed. Keyed on everything that can change it so a
    // scroll or an unrelated recomposition does not redo the whole log; 5,000
    // entries is the ceiling StatsRepository trims to.
    val days = remember(history, range, query, localSongs) {
        buildHistoryDays(
            history = history,
            range = range,
            query = query,
            localSongs = localSongs,
            weekdayFormat = weekdayFormat,
            dateFormat = dateFormat,
            datedYearFormat = datedYearFormat
        )
    }

    val visiblePlays = days.sumOf { it.plays }
    val visibleListenedMs = days.sumOf { it.listenedMs }
    val visibleSongs = days.flatMap { day -> day.runs.map { it.entry.songId } }.distinct().size

    // Playing from history queues the songs on screen, in the order they are
    // shown, deduplicated - a queue that repeated a song fourteen times because
    // that is how the log reads would be nobody's idea of Play.
    val queue = remember(days) {
        days.flatMap { day -> day.runs.mapNotNull { it.song } }.distinctBy { it.id }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Listening history", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) {
                                query = ""
                                focusManager.clearFocus()
                            }
                        }
                    ) {
                        Icon(
                            if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search history"
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (saveHistory) "Pause history" else "Resume history")
                                },
                                leadingIcon = {
                                    Icon(
                                        if (saveHistory) Icons.Rounded.HistoryToggleOff
                                        else Icons.Rounded.History,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    themePreferences.setSaveMusicHistory(!saveHistory)
                                    menuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Clear history",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                enabled = history.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    showClearDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { scaffoldPadding ->
        Column(modifier = Modifier.padding(scaffoldPadding)) {
            AnimatedVisibility(
                visible = searchOpen,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                HistorySearchField(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" },
                    onSubmit = { focusManager.clearFocus() }
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero") {
                    HistoryHeroCard(
                        listenedMs = visibleListenedMs,
                        plays = visiblePlays,
                        songs = visibleSongs,
                        rangeLabel = range.label,
                        streakDays = globalStats.currentStreakDays,
                        isFiltered = query.isNotBlank()
                    )
                }

                item(key = "ranges") {
                    RangeSelector(
                        selected = range,
                        onSelect = {
                            range = it
                            scope.launch { listState.scrollToItem(0) }
                        }
                    )
                }

                item(key = "paused_banner") {
                    // Only worth saying while it is off. A banner explaining
                    // that a feature is working is noise.
                    AnimatedVisibility(
                        visible = !saveHistory,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        PausedBanner(onResume = { themePreferences.setSaveMusicHistory(true) })
                    }
                }

                if (isLoading && history.isEmpty()) {
                    // Placeholder rows rather than a spinner, the same as every
                    // other first load in the app.
                    item(key = "skeleton") {
                        TrackSkeletonList(rows = 8)
                    }
                } else if (days.isEmpty()) {
                    item(key = "empty") {
                        HistoryEmptyState(
                            hasAnyHistory = history.isNotEmpty(),
                            isSearching = query.isNotBlank(),
                            isPaused = !saveHistory,
                            rangeLabel = range.label,
                            onShowAllTime = { range = HistoryRange.All },
                            onClearSearch = {
                                query = ""
                                searchOpen = false
                            }
                        )
                    }
                } else {
                    days.forEach { day ->
                        stickyHeader(key = "header_${day.epochDay}") {
                            DayHeader(day = day)
                        }
                        items(day.runs, key = { it.key }) { run ->
                            HistoryRunRow(
                                run = run,
                                timeLabel = timeFormat.format(Date(run.entry.timestamp)),
                                onPlay = {
                                    run.song?.let { song -> onPlayQueue(queue, song) }
                                },
                                onRemove = { allPlays ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.removePlayHistoryEntry(
                                        songId = run.entry.songId,
                                        timestamp = run.entry.timestamp,
                                        allPlaysOfSong = allPlays
                                    ) { before ->
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = if (allPlays) {
                                                    "Removed all plays of ${run.entry.title}"
                                                } else {
                                                    "Removed from history"
                                                },
                                                actionLabel = "Undo",
                                                withDismissAction = true
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                viewModel.restorePlayHistory(before)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ClearHistoryDialog(
            entryCount = history.size,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                val before = history
                viewModel.clearPlayHistory()
                showClearDialog = false
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "History cleared",
                        actionLabel = "Undo",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restorePlayHistory(before)
                    }
                }
            }
        )
    }
}

/* ------------------------------------------------------------------ */
/* Grouping                                                            */
/* ------------------------------------------------------------------ */

/**
 * Turn the raw log into day groups of collapsed runs.
 *
 * Order of operations matters. Filtering happens first so a search never shows
 * a day header with nothing under it; days are split before runs are collapsed
 * so a song still playing across midnight reads as a play on each day rather
 * than one run filed under whichever day won.
 */
private fun buildHistoryDays(
    history: List<PlayHistoryEntry>,
    range: HistoryRange,
    query: String,
    localSongs: List<Song>,
    weekdayFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat,
    datedYearFormat: SimpleDateFormat
): List<HistoryDay> {
    if (history.isEmpty()) return emptyList()

    val today = localEpochDay(System.currentTimeMillis())
    val trimmedQuery = query.trim()

    val matching = history.asSequence()
        .filter { entry ->
            val day = localEpochDay(entry.timestamp)
            when (range.daysBack) {
                null -> true
                else -> day >= today - range.daysBack
            }
        }
        .filter { entry ->
            trimmedQuery.isBlank() ||
                entry.title.contains(trimmedQuery, ignoreCase = true) ||
                entry.artist.contains(trimmedQuery, ignoreCase = true) ||
                entry.album.contains(trimmedQuery, ignoreCase = true)
        }
        .toList()

    if (matching.isEmpty()) return emptyList()

    // Local files need a playable URI, which only the scanned library has. One
    // lookup map rather than a find() per entry: a long history of local tracks
    // would otherwise be quadratic.
    val localById = localSongs.associateBy { it.id }
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    return matching
        .groupBy { localEpochDay(it.timestamp) }
        .entries
        .sortedByDescending { it.key }
        .map { (epochDay, entries) ->
            val runs = mutableListOf<HistoryRun>()
            for (entry in entries) {
                val last = runs.lastOrNull()
                if (last != null && last.entry.songId == entry.songId) {
                    // Same song again, immediately after: fold it in and keep
                    // the newest timestamp, which is the one the row shows.
                    runs[runs.lastIndex] = last.copy(playCount = last.playCount + 1)
                } else {
                    runs.add(
                        HistoryRun(
                            entry = entry,
                            playCount = 1,
                            song = resolveSong(entry, localById)
                        )
                    )
                }
            }
            HistoryDay(
                label = dayLabel(
                    epochDay = epochDay,
                    today = today,
                    timestamp = entries.first().timestamp,
                    thisYear = thisYear,
                    weekdayFormat = weekdayFormat,
                    dateFormat = dateFormat,
                    datedYearFormat = datedYearFormat
                ),
                epochDay = epochDay,
                runs = runs,
                plays = entries.size,
                listenedMs = entries.sumOf { it.duration }
            )
        }
}

private fun resolveSong(entry: PlayHistoryEntry, localById: Map<String, Song>): Song? =
    if (entry.source == SongSource.LOCAL) {
        localById[entry.songId]
    } else {
        Song.fromYouTube(
            videoId = entry.songId,
            title = entry.title,
            artist = entry.artist,
            album = entry.album,
            duration = entry.duration,
            thumbnailUrl = entry.thumbnailUrl
        )
    }

/** Epoch day in the device's local timezone, matching StatsRepository. */
private fun localEpochDay(timestamp: Long): Long =
    (timestamp + TimeZone.getDefault().getOffset(timestamp)) / 86_400_000L

private fun dayLabel(
    epochDay: Long,
    today: Long,
    timestamp: Long,
    thisYear: Int,
    weekdayFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat,
    datedYearFormat: SimpleDateFormat
): String {
    val date = Date(timestamp)
    val calendar = Calendar.getInstance().apply { time = date }
    return when {
        epochDay == today -> "Today"
        epochDay == today - 1 -> "Yesterday"
        // Inside the last week a weekday name is the fastest thing to read.
        // Past that it stops being unambiguous and the date has to take over.
        epochDay > today - 7 -> weekdayFormat.format(date)
        calendar.get(Calendar.YEAR) == thisYear -> dateFormat.format(date)
        else -> datedYearFormat.format(date)
    }
}

/** "1h 14m", "14m", "48s" - the shortest form that is still accurate. */
private fun formatListened(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/* ------------------------------------------------------------------ */
/* Pieces                                                              */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryHeroCard(
    listenedMs: Long,
    plays: Int,
    songs: Int,
    rangeLabel: String,
    streakDays: Int,
    isFiltered: Boolean
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    formatListened(listenedMs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    buildString {
                        append(if (isFiltered) "matching" else rangeLabel.lowercase())
                        append(" • ")
                        append(if (plays == 1) "1 play" else "$plays plays")
                        append(" • ")
                        append(if (songs == 1) "1 song" else "$songs songs")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                // A streak is the one number here that is about carrying on
                // rather than looking back, so it earns its own chip.
                if (streakDays > 1) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$streakDays day streak",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RangeSelector(
    selected: HistoryRange,
    onSelect: (HistoryRange) -> Unit
) {
    // The same connected group the Library tabs use, so the control is already
    // familiar and morphs on select rather than just recolouring.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        HistoryRange.entries.forEachIndexed { index, entry ->
            ToggleButton(
                checked = selected == entry,
                onCheckedChange = { onSelect(entry) },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    HistoryRange.entries.lastIndex ->
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayHeader(day: HistoryDay) {
    // Sticky, so it sits on the background rather than over the list. Opaque
    // for the same reason: rows scrolling visibly under a translucent header is
    // the one place a blur would be doing nothing but cost.
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(Modifier.width(10.dp))
                Text(
                    day.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    "${day.plays} • ${formatListened(day.listenedMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRunRow(
    run: HistoryRun,
    timeLabel: String,
    onPlay: () -> Unit,
    onRemove: (allPlays: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    // confirmValueChange can be asked more than once for the same gesture, and
    // a second yes would delete the next play as well once this row's entry is
    // already gone from the list.
    var dismissed by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled && !dismissed) {
                dismissed = true
                onRemove(false)
                true
            } else {
                false
            }
        }
    )

    // Tick as the swipe crosses into "letting go will delete this", which is a
    // commit the user cannot see yet - the house rule for when haptics belong.
    val past = dismissState.targetValue != SwipeToDismissBoxValue.Settled
    LaunchedEffect(past) {
        if (past) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val backgroundScale by animateFloatAsState(
        targetValue = if (past) 1f else 0.7f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipeIconScale"
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size((24 * backgroundScale).dp)
                    )
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size((24 * backgroundScale).dp)
                    )
                }
            }
        }
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        enabled = true,
                        onClick = { if (run.song != null) onPlay() else menuOpen = true },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HistoryArtwork(run = run)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            run.entry.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            // A local file that is no longer on the device says
                            // so instead of silently doing nothing when tapped.
                            if (run.song == null) "Not on this device" else run.entry.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            color = if (run.song == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (run.playCount > 1) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = MaterialShapes.Pill.toShape(),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    "×${run.playCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (run.song != null) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onPlay()
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(if (run.playCount > 1) "Remove these ${run.playCount} plays" else "Remove this play")
                    },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove(false)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove every play of this song") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRemove(true)
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryArtwork(run: HistoryRun) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(52.dp)
    ) {
        val model = run.song?.highResThumbnailUrl
            ?: run.entry.thumbnailUrl
            ?: run.song?.albumArtUri?.toString()
        if (model != null) {
            coil.compose.AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // Opened by a deliberate tap, so the keyboard should already be there.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .focusRequester(focusRequester),
        placeholder = { Text("Search titles, artists, albums") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() })
    )
}

@Composable
private fun PausedBanner(onResume: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.HistoryToggleOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "History is paused",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "New plays are not being recorded. What is already here stays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onResume) { Text("Resume") }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryEmptyState(
    hasAnyHistory: Boolean,
    isSearching: Boolean,
    isPaused: Boolean,
    rangeLabel: String,
    onShowAllTime: () -> Unit,
    onClearSearch: () -> Unit
) {
    // The four empties are genuinely different situations and get different
    // words and different ways out. Collapsing them into "Nothing here" is how
    // a working filter reads as a broken screen.
    val title: String
    val body: String
    when {
        isSearching -> {
            title = "No matches"
            body = "Nothing in this range matches that search."
        }
        hasAnyHistory -> {
            title = "Nothing ${rangeLabel.lowercase()}"
            body = "You have listened before, just not in this range."
        }
        isPaused -> {
            title = "History is off"
            body = "Nothing has been recorded because history is paused. " +
                "Resume it and songs you play will show up here."
        }
        else -> {
            title = "Nothing here yet"
            body = "Songs you play are logged here, so you can find that one " +
                "track from Tuesday and play it again."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialShapes.Clover4Leaf.toShape(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPaused && !hasAnyHistory) Icons.Rounded.HistoryToggleOff
                    else Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (hasAnyHistory) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = if (isSearching) onClearSearch else onShowAllTime
            ) {
                Text(if (isSearching) "Clear search" else "Show all time")
            }
        }
    }
}

@Composable
private fun ClearHistoryDialog(
    entryCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        title = { Text("Clear listening history?") },
        text = {
            Text(
                "This removes all $entryCount plays. Your recently played row, " +
                    "the Library's \"Most played\" sort and your statistics are " +
                    "all built on this history and will reset with it."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
