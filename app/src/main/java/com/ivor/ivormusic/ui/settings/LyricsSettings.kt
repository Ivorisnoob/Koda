package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LyricsConfiguration
import com.ivor.ivormusic.ui.components.queueDragHandle
import com.ivor.ivormusic.ui.components.rememberQueueReorderState
import com.ivor.ivormusic.util.rememberKodaHaptics

@Composable
internal fun LyricsSettingsPage(
    configuration: LyricsConfiguration,
    onChange: (LyricsConfiguration) -> Unit,
    onBack: () -> Unit
) {
    val haptics = rememberKodaHaptics()
    val listState = rememberLazyListState()
    var order by remember(configuration.providerOrder) { mutableStateOf(configuration.providerOrder) }
    val reorder = rememberQueueReorderState(
        listState = listState,
        keys = order,
        onMove = { from, to ->
            order = order.toMutableList().apply { add(to, removeAt(from)) }
        },
        onSettle = { onChange(configuration.copy(providerOrder = order)) }
    )
    val firstEnabled = order.firstOrNull { it !in configuration.disabledProviders }

    SettingsDetailScaffold(
        title = stringResource(R.string.lyrics_settings_title),
        onBack = onBack,
        listState = listState,
        itemSpacing = 4.dp
    ) {
        item {
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.lyrics_remote),
                    subtitle = stringResource(R.string.lyrics_remote_sub),
                    enabled = configuration.remoteEnabled,
                    onToggle = { onChange(configuration.copy(remoteEnabled = it)) }
                )
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(title = stringResource(R.string.lyrics_provider_order)) {
                Text(
                    stringResource(R.string.lyrics_drag_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                )
            }
        }
        itemsIndexed(order, key = { _, provider -> provider }) { index, provider ->
            val enabled = provider !in configuration.disabledProviders
            val preferred = enabled && provider == firstEnabled && configuration.remoteEnabled
            val dragging = reorder.isDragging(provider)
            val moveUp = stringResource(R.string.lyrics_move_up, provider)
            val moveDown = stringResource(R.string.lyrics_move_down, provider)
            val move: (Int) -> Boolean = { delta ->
                val destination = index + delta
                if (destination in order.indices) {
                    order = order.toMutableList().apply { add(destination, removeAt(index)) }
                    onChange(configuration.copy(providerOrder = order))
                    haptics.confirm()
                    true
                } else false
            }
            Surface(
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = reorder.offsetFor(provider) }
                    .animateItem(placementSpec = if (dragging) null else androidx.compose.animation.core.spring())
                    .fillMaxWidth(),
                shape = RoundedCornerShape(
                    topStart = if (index == 0 || dragging) 24.dp else 4.dp,
                    topEnd = if (index == 0 || dragging) 24.dp else 4.dp,
                    bottomStart = if (index == order.lastIndex || dragging) 24.dp else 4.dp,
                    bottomEnd = if (index == order.lastIndex || dragging) 24.dp else 4.dp
                ),
                color = when {
                    dragging -> MaterialTheme.colorScheme.secondaryContainer
                    preferred -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
                shadowElevation = if (dragging) 8.dp else 0.dp
            ) {
                Row(
                    Modifier.padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dragLabel = stringResource(R.string.lyrics_reorder_provider, provider)
                    Box(
                        Modifier.size(48.dp).queueDragHandle(reorder, provider).semantics {
                            contentDescription = dragLabel
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(moveUp) { move(-1) })
                                if (index < order.lastIndex) add(CustomAccessibilityAction(moveDown) { move(1) })
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.DragHandle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(provider, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                !enabled -> stringResource(R.string.lyrics_source_disabled)
                                preferred -> stringResource(R.string.lyrics_first_choice)
                                else -> stringResource(R.string.lyrics_priority_number, index + 1)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preferred) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            haptics.toggle(it)
                            onChange(configuration.copy(disabledProviders = if (it)
                                configuration.disabledProviders - provider else configuration.disabledProviders + provider))
                        },
                        modifier = Modifier.semantics { contentDescription = provider }
                    )
                }
            }
        }
        item {
            if (!configuration.remoteEnabled || firstEnabled == null) {
                Text(stringResource(R.string.lyrics_local_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
            }
            Spacer(Modifier.height(24.dp))
            SettingsSection(title = stringResource(R.string.lyrics_matching)) {
                SettingsCard {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.lyrics_selection_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                        ) {
                            listOf(true, false).forEachIndexed { index, synced ->
                                ToggleButton(
                                    checked = configuration.preferSynced == synced,
                                    onCheckedChange = {
                                        haptics.confirm()
                                        onChange(configuration.copy(preferSynced = synced))
                                    },
                                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    // The card is surfaceContainer, the toggle's own
                                    // default, so the unselected half needs a step up
                                    // the surface ladder to read against it.
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(if (synced) R.string.lyrics_synced_first else R.string.lyrics_my_order))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(if (configuration.preferSynced) R.string.lyrics_prefer_synced_sub else R.string.lyrics_strict_order_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Rounded.Notes,
                        title = stringResource(R.string.lyrics_plain),
                        subtitle = stringResource(R.string.lyrics_plain_short),
                        enabled = configuration.allowPlainText,
                        onToggle = { onChange(configuration.copy(allowPlainText = it)) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.lyrics_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { haptics.confirm(); onChange(LyricsConfiguration()) }) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lyrics_reset))
            }
        }
    }
}
