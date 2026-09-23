package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import java.util.Locale

/** One pickable country: its ISO code and its name in the device's language. */
private data class Region(val code: String, val name: String)

/** Every country the platform knows, named in the device language, A to Z. */
private fun allRegions(): List<Region> {
    val display = Locale.getDefault()
    return Locale.getISOCountries()
        .map { code -> Region(code, Locale("", code).getDisplayCountry(display).ifBlank { code }) }
        .sortedBy { it.name.lowercase(display) }
}

private fun regionName(code: String): String =
    Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }

/**
 * The hub-style live value for the Content region row: the chosen country,
 * or "Automatic" with the country that currently resolves to, so the row says
 * what the setting is doing rather than only what it is set to.
 */
@Composable
internal fun contentRegionLabel(selected: String): String {
    if (selected.length == 2) return regionName(selected)
    val device = ThemePreferences.deviceRegion()
    return if (device != null) {
        stringResource(R.string.sp_content_region_auto_value, regionName(device))
    } else {
        stringResource(R.string.sp_content_region_auto)
    }
}

/**
 * Pick the country YouTube ranks search and signed-out feeds for.
 *
 * A sheet, not a dialog: it is a search over ~250 entries, and the
 * keyboard needs room. Automatic leads and is pinned outside the filter so
 * the way back to "follow my phone" is always one tap away. The list is a
 * LazyColumn inside the sheet, so every row is reachable at any font scale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContentRegionSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val regions = remember { allRegions() }
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val shown = remember(trimmed, regions) {
        if (trimmed.isEmpty()) regions
        else regions.filter {
            it.name.contains(trimmed, ignoreCase = true) || it.code.equals(trimmed, ignoreCase = true)
        }
    }
    val device = remember { ThemePreferences.deviceRegion() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .imePadding()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sp_content_region),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sp_content_region_sheet_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.sp_content_region_search)) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item(key = "auto") {
                    RegionRow(
                        badge = null,
                        title = stringResource(R.string.sp_content_region_auto),
                        subtitle = device?.let {
                            stringResource(R.string.sp_content_region_auto_sub, regionName(it))
                        },
                        selected = selected.length != 2,
                        onClick = { onSelect(""); onDismiss() }
                    )
                }
                items(shown, key = { it.code }) { region ->
                    RegionRow(
                        badge = region.code,
                        title = region.name,
                        subtitle = null,
                        selected = selected == region.code,
                        onClick = { onSelect(region.code); onDismiss() }
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "none") {
                        Text(
                            text = stringResource(R.string.sp_content_region_none, trimmed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One country. The selected row takes the secondary container as a full
 * pill so the current answer is findable at a glance while scrolling; the
 * ISO code sits in a small tonal circle where a flag would be, since flag
 * glyphs render inconsistently across OEM fonts.
 */
@Composable
private fun RegionRow(
    badge: String?,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainer
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(container)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = content,
                modifier = Modifier.padding(start = 8.dp).size(24.dp)
            )
        }
    }
}
