package com.ivor.ivormusic.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.AppTimeLimit
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.settings.SettingsCard
import com.ivor.ivormusic.ui.settings.SettingsDivider
import com.ivor.ivormusic.ui.settings.SettingsSection
import com.ivor.ivormusic.ui.settings.SettingsToggleRow
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Editor for the daily time limit.
 *
 * Owns its own [ThemePreferences] instance rather than receiving values as
 * parameters on purpose: it is the only writer while it is open, the lock
 * ticker in MainActivity fresh-reads preferences at every tick anyway, and a
 * seven-day budget list would otherwise be threaded through MusicApp for no
 * reactive benefit - the same trade ReportBugScreen makes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTimeLimitScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { ThemePreferences(context) }

    val enabled by prefs.timeLimitEnabled.collectAsState()
    val budgetsStored by prefs.timeLimitBudgets.collectAsState()
    val budgets = remember(budgetsStored) { AppTimeLimit.parseBudgets(budgetsStored) }

    // Snapshot of today's usage for the summary ring. Read once per entry;
    // the live number lives with the activity's ticker, and an editor does
    // not need second-level truth to set a policy.
    val usedSecondsToday = remember { AppTimeLimit.usedSecondsToday(context) }
    val todayIndex = LocalDate.now().dayOfWeek.value - 1
    val todayBudget = budgets[todayIndex] ?: 0
    val lockedNow = AppTimeLimit.isLocked(context, enabled, budgetsStored)
    val dayNames = remember {
        (0..6).map { day ->
            java.time.DayOfWeek.of(day + 1)
                .getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Daily time limit", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (enabled) {
                                if (lockedNow) "Koda is locked until midnight"
                                else "Koda locks when the day's budget runs out"
                            } else {
                                "Off - Koda is always unlocked"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "today") {
                TodayCard(
                    enabled = enabled,
                    usedSecondsToday = usedSecondsToday,
                    budgetMinutes = todayBudget,
                    lockedNow = lockedNow
                )
            }

            item(key = "enable") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Bedtime,
                        title = "Enable daily time limit",
                        subtitle = "Locks all of Koda once today's budget is used",
                        enabled = enabled,
                        onToggle = { prefs.setTimeLimitEnabled(it) }
                    )
                }
            }

            if (enabled) {
                item(key = "presets") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Quick set - same budget every day",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(60, 120, 180, 300, 480).forEach { minutes ->
                                FilterChip(
                                    selected = false,
                                    onClick = { prefs.setAllTimeLimitBudgets(minutes) },
                                    label = { Text(AppTimeLimit.formatBudget(minutes)) }
                                )
                            }
                        }
                    }
                }

                item(key = "week-header") {
                    SettingsSection(title = "Per-day budgets") {}
                }

                items(count = 7, key = { "day-$it" }) { day ->
                    val minutes = budgets[day] ?: 0
                    DayBudgetCard(
                        dayName = dayNames[day],
                        minutes = minutes,
                        isToday = day == todayIndex,
                        onChange = { newMinutes -> prefs.setTimeLimitBudget(day, newMinutes) }
                    )
                }

                item(key = "note") {
                    Text(
                        text = "Counts only time Koda is open on screen, and resets at midnight. " +
                            "Setting a day to Unlimited leaves it untracked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodayCard(
    enabled: Boolean,
    usedSecondsToday: Long,
    budgetMinutes: Int,
    lockedNow: Boolean
) {
    SettingsSection(title = "Today") {
        SettingsCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!enabled || budgetMinutes <= 0) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                } else {
                    CircularWavyProgressIndicator(
                        progress = {
                            AppTimeLimit.progressFraction(usedSecondsToday, budgetMinutes)
                        },
                        modifier = Modifier.size(56.dp)
                    )
                }
                Column {
                    Text(
                        text = when {
                            !enabled -> "Tracking off"
                            budgetMinutes <= 0 ->
                                "Unlimited today - no budget set"
                            lockedNow ->
                                "${AppTimeLimit.formatBudget(budgetMinutes)} used - locked until midnight"
                            else ->
                                "${formatUsed(usedSecondsToday)} of " +
                                    "${AppTimeLimit.formatBudget(budgetMinutes)} used"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            !enabled -> "Turn on the limit below to start tracking"
                            else -> "Resets tonight at midnight"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBudgetCard(
    dayName: String,
    minutes: Int,
    isToday: Boolean,
    onChange: (Int) -> Unit
) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(
                    if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "$dayName (today)" else dayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = AppTimeLimit.formatBudget(minutes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (minutes == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onChange(it.toInt()) },
                valueRange = 0f..720f,
                steps = (720 / AppTimeLimit.BUDGET_STEP_MINUTES) - 1
            )
        }
    }
}

private fun formatUsed(seconds: Long): String =
    AppTimeLimit.formatBudget((seconds / 60L).toInt())
