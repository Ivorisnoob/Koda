package com.ivor.ivormusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.SPONSORBLOCK_MAX_MIN_DURATION_MS
import com.ivor.ivormusic.data.SegmentAction
import com.ivor.ivormusic.data.SponsorBlockRepository
import com.ivor.ivormusic.data.SponsorCategory
import com.ivor.ivormusic.ui.video.categoryDescription
import com.ivor.ivormusic.ui.video.categoryLabel
import com.ivor.ivormusic.util.rememberKodaHaptics

/**
 * The SponsorBlock page.
 *
 * Its own page rather than rows on Playback because the useful part is
 * per-category, and eight categories with three states each is not something
 * that can sit inside a list of unrelated toggles.
 *
 * Everything below the master switch is hidden while the feature is off. The
 * alternative - showing eight disabled category rows - fills the page with
 * controls that do nothing and buries the one switch that matters.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SponsorBlockSettingsPage(
    enabled: Boolean,
    onEnabledToggle: (Boolean) -> Unit,
    actions: Map<SponsorCategory, SegmentAction>,
    onActionChange: (SponsorCategory, SegmentAction) -> Unit,
    onResetCategories: () -> Unit,
    showOnSeekBar: Boolean,
    onShowOnSeekBarToggle: (Boolean) -> Unit,
    showNotice: Boolean,
    onShowNoticeToggle: (Boolean) -> Unit,
    minDurationMs: Long,
    onMinDurationChange: (Long) -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = stringResource(R.string.sb_title), onBack = onBack) {
        item {
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Rounded.MoneyOff,
                    title = stringResource(R.string.sb_enable),
                    subtitle = stringResource(R.string.sb_enable_sub),
                    enabled = enabled,
                    onToggle = onEnabledToggle
                )
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            // Stated up front rather than buried at the bottom: this is the
            // only third-party service the app contacts, and someone deciding
            // whether to turn it on deserves to know what leaves the device
            // before they decide, not after.
            PrivacyNote()
        }

        item {
            AnimatedVisibility(
                visible = enabled,
                enter = fadeIn() + slideInVertically(
                    initialOffsetY = { -it / 6 },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                ),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(20.dp))
                    SettingsSection(title = stringResource(R.string.sb_categories)) {
                        SettingsCard {
                            SponsorCategory.entries.forEachIndexed { index, category ->
                                if (index > 0) SettingsDivider()
                                CategoryRow(
                                    category = category,
                                    action = actions[category] ?: category.defaultAction,
                                    onActionChange = { onActionChange(category, it) }
                                )
                            }
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Rounded.RestartAlt,
                                title = stringResource(R.string.sb_reset_categories),
                                subtitle = stringResource(R.string.sb_categories_sub),
                                onClick = onResetCategories
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SettingsSection(title = stringResource(R.string.sb_appearance)) {
                        SettingsCard {
                            SettingsToggleRow(
                                icon = Icons.Rounded.Timeline,
                                title = stringResource(R.string.sb_show_on_seekbar),
                                subtitle = stringResource(R.string.sb_show_on_seekbar_sub),
                                enabled = showOnSeekBar,
                                onToggle = onShowOnSeekBarToggle
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                icon = Icons.Rounded.Notifications,
                                title = stringResource(R.string.sb_notice),
                                subtitle = stringResource(R.string.sb_notice_sub),
                                enabled = showNotice,
                                onToggle = onShowNoticeToggle
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SettingsSection(title = stringResource(R.string.sb_behaviour)) {
                        SettingsCard {
                            MinDurationRow(
                                minDurationMs = minDurationMs,
                                onChange = onMinDurationChange
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.sb_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = SponsorBlockRepository.API_HOST,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * One category, with its colour, what it covers, and the three-way choice.
 *
 * The colour swatch is the same one drawn on the seek bar, which is what makes
 * a mark on the bar identifiable without a legend anywhere.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryRow(
    category: SponsorCategory,
    action: SegmentAction,
    onActionChange: (SegmentAction) -> Unit
) {
    val haptics = rememberKodaHaptics()
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(category.color)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // The category names are lowercase so they read naturally
                    // inside "Skipped sponsor" on the player chip; here they
                    // are a heading and want a capital.
                    text = stringResource(categoryLabel(category))
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(categoryDescription(category)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val options = listOf(SegmentAction.SKIP, SegmentAction.MANUAL, SegmentAction.IGNORE)
        val labels = listOf(
            stringResource(R.string.sb_action_skip),
            stringResource(R.string.sb_action_manual),
            stringResource(R.string.sb_action_ignore)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            options.forEachIndexed { index, option ->
                ToggleButton(
                    checked = action == option,
                    onCheckedChange = {
                        haptics.confirm()
                        onActionChange(option)
                    },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(labels[index], fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * The "ignore very short segments" threshold.
 *
 * Exists because a one-second skip is more disruptive than the second it
 * removes - the jump is noticed, the content is not. Zero means skip
 * everything, and is the default.
 */
@Composable
private fun MinDurationRow(minDurationMs: Long, onChange: (Long) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
            text = stringResource(R.string.sb_min_duration),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (minDurationMs <= 0L) {
                stringResource(R.string.sb_min_duration_off)
            } else {
                stringResource(R.string.sb_min_duration_value, minDurationMs / 1000f)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = minDurationMs.toFloat(),
            onValueChange = { onChange(it.toLong()) },
            valueRange = 0f..SPONSORBLOCK_MAX_MIN_DURATION_MS.toFloat(),
            // Half-second stops: finer than anyone can judge by feel, and the
            // readout is shown to one decimal place.
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
