package com.ivor.ivormusic.ui.library
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.ui.home.HomeViewModel
import java.util.Calendar

/**
 * Listening statistics.
 *
 * - Hero listening-time card with a SoftBurst shape badge
 * - Stat tiles with expressive MaterialShapes badges
 * - 7-day activity bar chart from real play history
 * - Ranked top songs / artists with shaped rank badges
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel,
    contentPadding: PaddingValues
) {
    val searchHistory by viewModel.searchHistory.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val globalStats by viewModel.globalStats.collectAsState()
    val dailyPlays by viewModel.dailyPlays.collectAsState()

    // Refresh stats on entry
    LaunchedEffect(Unit) {
        viewModel.refreshStats()
    }

    // Format duration from seconds
    val hours = globalStats.totalPlayTimeSeconds / 3600
    val minutes = (globalStats.totalPlayTimeSeconds % 3600) / 60
    val formattedTime = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val onBg = MaterialTheme.colorScheme.onBackground
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.fab_statistics), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== HERO: total listening time =====
            item {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialShapes.SoftBurst.toShape(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(Modifier.width(20.dp))
                        Column {
                            Text(
                                formattedTime,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "listened • ${globalStats.totalPlays} plays",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // ===== Stat tiles =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveStatTile(
                        value = "${globalStats.uniqueSongs}",
                        label = "Songs played",
                        icon = Icons.Rounded.MusicNote,
                        badgeShape = MaterialShapes.Cookie9Sided.toShape(),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                        badge = MaterialTheme.colorScheme.secondary,
                        onBadge = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatTile(
                        value = "${globalStats.uniqueArtists}",
                        label = "Artists explored",
                        icon = Icons.Rounded.Person,
                        badgeShape = MaterialShapes.Clover4Leaf.toShape(),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                        badge = MaterialTheme.colorScheme.tertiary,
                        onBadge = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ExpressiveStatTile(
                        value = "${likedSongs.size}",
                        label = "Liked songs",
                        icon = Icons.Rounded.Favorite,
                        badgeShape = MaterialShapes.Sunny.toShape(),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onContainer = MaterialTheme.colorScheme.onSurface,
                        badge = MaterialTheme.colorScheme.primary,
                        onBadge = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveStatTile(
                        value = globalStats.topArtists.firstOrNull()?.name ?: "—",
                        label = "Top artist",
                        icon = Icons.Rounded.MusicNote,
                        badgeShape = MaterialShapes.Pill.toShape(),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onContainer = MaterialTheme.colorScheme.onSurface,
                        badge = MaterialTheme.colorScheme.tertiary,
                        onBadge = MaterialTheme.colorScheme.onTertiary,
                        valueIsText = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ===== 7-day activity chart =====
            item {
                WeeklyActivityCard(dailyPlays = dailyPlays)
            }

            // ===== Streak + next milestone =====
            if (globalStats.totalPlays > 0) {
                item {
                    StreakAndMilestoneRow(
                        currentStreak = globalStats.currentStreakDays,
                        longestStreak = globalStats.longestStreakDays,
                        totalPlays = globalStats.totalPlays
                    )
                }
            }

            // ===== Top songs =====
            if (globalStats.topSongs.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.st_top_songs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onBg,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                itemsIndexed(globalStats.topSongs) { index, songStats ->
                    val shape = segmentShape(index, globalStats.topSongs.size)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index == globalStats.topSongs.lastIndex) 0.dp else 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RankBadge(rank = index + 1)
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(44.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                if (songStats.thumbnailUrl != null) {
                                    AsyncImage(
                                        model = songStats.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.MusicNote, null,
                                            modifier = Modifier.padding(10.dp),
                                            tint = secondaryText
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    songStats.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    songStats.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${songStats.playCount}×",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ===== Top artists =====
            if (globalStats.topArtists.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.st_top_artists),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onBg,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val topArtists = globalStats.topArtists.take(5)
                itemsIndexed(topArtists) { index, artistStats ->
                    Surface(
                        shape = segmentShape(index, topArtists.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index == topArtists.lastIndex) 0.dp else 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RankBadge(rank = index + 1)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    artistStats.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${artistStats.songCount} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryText
                                )
                            }
                            Text(
                                "${artistStats.playCount} plays",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // ===== Empty state =====
            if (globalStats.totalPlays == 0) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie12Sided.toShape(),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Headphones, null,
                                    modifier = Modifier.size(40.dp),
                                    tint = secondaryText
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.st_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = onBg
                        )
                        Text(
                            stringResource(R.string.st_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ===== Recent searches =====
            if (searchHistory.isNotEmpty()) {
                item {
                    Text(
                        "Recent searches",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onBg,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                val historyList = searchHistory
                itemsIndexed(historyList) { index, query ->
                    Surface(
                        shape = segmentShape(index, historyList.size),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index == historyList.lastIndex) 0.dp else 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.History, null, tint = secondaryText)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                query,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeFromSearchHistory(query) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = secondaryText)
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 80.dp)) }
        }
    }
}

/** Segmented container shape: rounded on the outside edges of the group. */
private fun segmentShape(index: Int, count: Int): Shape {
    val big = 20.dp
    val small = 6.dp
    return when {
        count == 1 -> RoundedCornerShape(big)
        index == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
        index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
        else -> RoundedCornerShape(small)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RankBadge(rank: Int) {
    val (shape, color, contentColor) = when (rank) {
        1 -> Triple(MaterialShapes.Sunny.toShape(), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        2 -> Triple(MaterialShapes.Cookie9Sided.toShape(), MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
        3 -> Triple(MaterialShapes.Clover4Leaf.toShape(), MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        else -> Triple(
            MaterialShapes.Cookie7Sided.toShape(),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Surface(shape = shape, color = color, modifier = Modifier.size(36.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveStatTile(
    value: String,
    label: String,
    icon: ImageVector,
    badgeShape: Shape,
    container: Color,
    onContainer: Color,
    badge: Color,
    onBadge: Color,
    valueIsText: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(24.dp),
        color = container
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = badgeShape, color = badge, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = onBadge, modifier = Modifier.size(20.dp))
                }
            }
            Column {
                Text(
                    text = value,
                    style = if (valueIsText) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Listening streak, and progress toward the next play-count milestone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreakAndMilestoneRow(
    currentStreak: Int,
    longestStreak: Int,
    totalPlays: Int
) {
    val milestones = listOf(10, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000)
    val nextMilestone = milestones.firstOrNull { it > totalPlays }
    val prevMilestone = milestones.lastOrNull { it <= totalPlays } ?: 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Streak card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier
                .weight(1f)
                .height(132.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = MaterialShapes.Flower.toShape(),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        if (currentStreak == 1) "1 day" else "$currentStreak days",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        if (longestStreak > currentStreak) "Streak • best $longestStreak" else "Listening streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Milestone card with wavy progress
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(132.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = MaterialShapes.Gem.toShape(),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (nextMilestone != null) {
                    Column {
                        val fraction = (totalPlays - prevMilestone).toFloat() /
                                (nextMilestone - prevMilestone).toFloat()
                        Text(
                            "${nextMilestone - totalPlays} to go",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            "Next: $nextMilestone plays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearWavyProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Column {
                        Text(
                            stringResource(R.string.st_legend),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.st_every_milestone),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bar chart of plays per day for the last 7 days.
 * Bars are pill-shaped and scale against the busiest day.
 */
@Composable
private fun WeeklyActivityCard(dailyPlays: Map<String, Int>) {
    // Build the last 7 days in StatsRepository's "M/d" key format
    val days = remember(dailyPlays) {
        (6 downTo 0).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val key = "${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}"
            val label = when (c.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "M"
                Calendar.TUESDAY -> "T"
                Calendar.WEDNESDAY -> "W"
                Calendar.THURSDAY -> "T"
                Calendar.FRIDAY -> "F"
                Calendar.SATURDAY -> "S"
                else -> "S"
            }
            Triple(key, label, dailyPlays[key] ?: 0)
        }
    }
    val maxCount = (days.maxOfOrNull { it.third } ?: 0).coerceAtLeast(1)
    val weekTotal = days.sumOf { it.third }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "This week",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "$weekTotal plays",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { index, (_, label, count) ->
                    val isToday = index == days.lastIndex
                    val fraction = count.toFloat() / maxCount
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((8 + 80 * fraction).dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    when {
                                        count == 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    }
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
