package com.ivor.ivormusic.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.tv.CacheState
import com.ivor.ivormusic.data.tv.DubPreference
import com.ivor.ivormusic.data.tv.PickReason
import com.ivor.ivormusic.data.tv.SourceQuality
import com.ivor.ivormusic.data.tv.TvAutoPick
import com.ivor.ivormusic.data.tv.TvSource
import com.ivor.ivormusic.data.tv.TvSourceFacets
import com.ivor.ivormusic.data.tv.TvSourceFilter
import com.ivor.ivormusic.data.tv.TvSourceKind

/**
 * Where a title's playable sources are chosen.
 *
 * **The body is one `LazyColumn` and everything else is pinned outside it.** A
 * bottom sheet whose content does not scroll has a silent hard ceiling -
 * `VideoOptionsSheet` was clipped identically at zero playlists and at three
 * hundred before it was split - and this list is unbounded by construction: a
 * single title routinely returns sixty-odd releases.
 *
 * The shape, top to bottom: one hero card carrying the automatic pick and the
 * reason for it, a filter row derived from this result set, then the list
 * grouped by resolution. **Ninety per cent of viewers should only ever touch
 * the hero card**; everything below it is for the ten per cent who know what a
 * REMUX is, and it is written for them - the raw release name is on every row,
 * never only the parsed badges.
 */
@Composable
fun TvSourceSheet(
    title: String,
    hasStreamSource: Boolean,
    isLoading: Boolean,
    loaded: Boolean,
    sources: List<TvSource>,
    totalCount: Int,
    autoPick: TvAutoPick?,
    facets: TvSourceFacets,
    filter: TvSourceFilter,
    failedAddons: List<String>,
    onPlay: (TvSource) -> Unit,
    onOpenExternal: (String) -> Unit,
    onSetResolution: (Int?) -> Unit,
    onSetLanguage: (String?) -> Unit,
    onSetSourceQuality: (SourceQuality?) -> Unit,
    onSetCachedOnly: (Boolean) -> Unit,
    onSetDub: (DubPreference) -> Unit,
    onClearFilters: () -> Unit,
    onBrowseAddons: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var torrentNotice by remember { mutableStateOf(false) }
    var externalNotice by remember { mutableStateOf<TvSource?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tv_sources_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (totalCount > 0) {
                    Text(
                        text = stringResource(R.string.tv_sources_count, totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (failedAddons.isNotEmpty() && !isLoading) {
                FailedAddonsNotice(
                    names = failedAddons,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            when {
                !hasStreamSource -> NoSourceAddonBody(onBrowseAddons)
                isLoading -> SearchingBody()
                loaded && totalCount == 0 -> EmptyBody(onBrowseAddons)
                else -> {
                    // Pinned above the scroller: the one interaction most
                    // viewers should ever need.
                    autoPick?.let { pick ->
                        AutoPickCard(
                            pick = pick,
                            onPlay = { onPlay(pick.source) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    SourceFilterRow(
                        facets = facets,
                        filter = filter,
                        onSetResolution = onSetResolution,
                        onSetLanguage = onSetLanguage,
                        onSetSourceQuality = onSetSourceQuality,
                        onSetCachedOnly = onSetCachedOnly,
                        onSetDub = onSetDub,
                    )

                    Spacer(Modifier.height(4.dp))

                    if (sources.isEmpty()) {
                        FilteredEmptyBody(onClearFilters)
                    } else {
                        SourceList(
                            sources = sources,
                            onPlay = onPlay,
                            onTorrent = { torrentNotice = true },
                            onExternal = { externalNotice = it },
                        )
                    }
                }
            }
        }
    }

    externalNotice?.let { source ->
        ExternalNoticeDialog(
            onOpen = {
                source.externalLink?.let(onOpenExternal)
                externalNotice = null
            },
            onDismiss = { externalNotice = null },
        )
    }

    if (torrentNotice) {
        TorrentNoticeDialog(
            onBrowseAddons = {
                torrentNotice = false
                onBrowseAddons()
            },
            onDismiss = { torrentNotice = false },
        )
    }
}

/**
 * The list, grouped by resolution with a header per group.
 *
 * Grouping rather than one flat ranked list because "which file" is the
 * decision being made, and resolution is the axis people decide on first. The
 * groups are in descending order and anything the parser could not read falls
 * into one honest bucket at the end rather than being scattered.
 */
@Composable
private fun SourceList(
    sources: List<TvSource>,
    onPlay: (TvSource) -> Unit,
    onTorrent: () -> Unit,
    onExternal: (TvSource) -> Unit,
) {
    val groups = remember(sources) {
        sources.groupBy { it.tags.resolution }
            .toList()
            .sortedByDescending { (resolution, _) -> resolution ?: -1 }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((resolution, group) in groups) {
            item(key = "header-" + resolution) {
                Text(
                    text = group.first().tags.resolutionLabel
                        ?: stringResource(R.string.tv_group_other),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(group, key = { it.id }) { source ->
                SourceRow(
                    source = source,
                    onClick = {
                        when (source.kind) {
                            TvSourceKind.PLAYABLE -> onPlay(source)
                            TvSourceKind.EXTERNAL -> onExternal(source)
                            TvSourceKind.TORRENT -> onTorrent()
                        }
                    },
                )
            }
        }
    }
}

/**
 * One release.
 *
 * Three lines, in order of how much they are trusted: the derived badges, the
 * raw release name, and the provenance. **The raw name is never omitted** - the
 * parser is best-effort and the person choosing a source often knows a group
 * name better than any regex does.
 */
@Composable
private fun SourceRow(source: TvSource, onClick: () -> Unit) {
    val tags = source.tags
    val playable = source.isPlayable
    // A torrent row is shown, dimmed: hiding it makes a working addon look
    // empty, which is a worse lie than showing something that needs a step.
    val contentColor =
        if (playable) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadges(
                    source = source,
                    playable = playable,
                    modifier = Modifier.weight(1f),
                )
                source.sizeBytes?.let { bytes ->
                    Text(
                        text = formatSize(bytes),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = source.stream.releaseName.ifBlank { source.addonLabel },
                style = MaterialTheme.typography.bodySmall,
                // Monospace because release names are read token by token and a
                // proportional face makes the dots and dashes disappear.
                fontFamily = FontFamily.Monospace,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildProvenance(source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (tags.cacheState == CacheState.CACHED) {
                    CachedPip()
                }
            }
        }
    }
}

@Composable
private fun SourceBadges(source: TvSource, playable: Boolean, modifier: Modifier = Modifier) {
    val tags = source.tags
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!playable) {
            Badge(
                text = stringResource(
                    if (source.kind == TvSourceKind.EXTERNAL) R.string.tv_external_badge
                    else R.string.tv_torrent_badge
                ),
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        tags.resolutionLabel?.let {
            Badge(
                text = it,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        tags.sourceQuality?.let {
            Badge(
                text = it.label,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (tags.hdr.isNotEmpty()) {
            Badge(
                text = tags.hdr.joinToString(" ") { flag -> flag.label },
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        tags.audioLabel?.let {
            Badge(
                text = it,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Badge(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CachedPip() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(R.string.tv_source_cached),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The hero card.
 *
 * One tap, one line saying why this and not something else. The reason is a
 * string chosen by the ranker rather than composed here, so what the card
 * claims and what the ranker did cannot drift apart.
 */
@Composable
private fun AutoPickCard(pick: TvAutoPick, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val tags = pick.source.tags
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(
                        tags.resolutionLabel,
                        tags.sourceQuality?.label,
                        tags.audioLabel,
                    ).joinToString("  ").ifBlank { pick.source.addonLabel },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        when (pick.reason) {
                            PickReason.ONLY_PLAYABLE -> R.string.tv_reason_only
                            PickReason.CACHED -> R.string.tv_reason_cached
                            PickReason.WITHIN_LIMIT -> R.string.tv_reason_within_limit
                            PickReason.BEST_AVAILABLE -> R.string.tv_reason_best
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = pick.source.addonName +
                        (pick.source.sizeBytes?.let { "  " + formatSize(it) } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
            Button(
                onClick = onPlay,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.tv_play_now),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/**
 * Filter chips, built from this result set only.
 *
 * **A chip that filters to zero rows is a broken control**, so nothing is
 * offered that the facets did not find in the sources actually returned - a
 * fixed vocabulary of resolutions would draw a 4K chip for a title that has
 * none.
 */
@Composable
private fun SourceFilterRow(
    facets: TvSourceFacets,
    filter: TvSourceFilter,
    onSetResolution: (Int?) -> Unit,
    onSetLanguage: (String?) -> Unit,
    onSetSourceQuality: (SourceQuality?) -> Unit,
    onSetCachedOnly: (Boolean) -> Unit,
    onSetDub: (DubPreference) -> Unit,
) {
    if (facets.isEmpty) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (facets.hasCached) {
            item(key = "cached") {
                FilterChip(
                    selected = filter.cachedOnly,
                    onClick = { onSetCachedOnly(!filter.cachedOnly) },
                    label = { Text(stringResource(R.string.tv_filter_cached)) },
                )
            }
        }
        // Sub and Dub only appear when the set actually contains both kinds,
        // which is the same rule the detail page uses: a control that cannot
        // change anything should not be drawn.
        if (facets.hasDub && facets.hasSub) {
            item(key = "sub") {
                FilterChip(
                    selected = filter.dub == DubPreference.SUB,
                    onClick = {
                        onSetDub(
                            if (filter.dub == DubPreference.SUB) DubPreference.ANY
                            else DubPreference.SUB
                        )
                    },
                    label = { Text(stringResource(R.string.tv_filter_sub)) },
                )
            }
            item(key = "dub") {
                FilterChip(
                    selected = filter.dub == DubPreference.DUB,
                    onClick = {
                        onSetDub(
                            if (filter.dub == DubPreference.DUB) DubPreference.ANY
                            else DubPreference.DUB
                        )
                    },
                    label = { Text(stringResource(R.string.tv_filter_dub)) },
                )
            }
        }
        items(facets.resolutions, key = { "res-" + it }) { resolution ->
            val label = when (resolution) {
                4320 -> "8K"
                2160 -> "4K"
                else -> resolution.toString() + "p"
            }
            FilterChip(
                selected = filter.resolution == resolution,
                onClick = {
                    onSetResolution(if (filter.resolution == resolution) null else resolution)
                },
                label = { Text(label) },
            )
        }
        items(facets.sourceQualities, key = { "sq-" + it.name }) { quality ->
            FilterChip(
                selected = filter.sourceQuality == quality,
                onClick = {
                    onSetSourceQuality(if (filter.sourceQuality == quality) null else quality)
                },
                label = { Text(quality.label) },
            )
        }
        items(facets.languages, key = { "lang-" + it }) { language ->
            FilterChip(
                selected = filter.language == language,
                onClick = { onSetLanguage(if (filter.language == language) null else language) },
                label = { Text(languageLabel(language)) },
            )
        }
    }
}

// --- The states that are not a list ----------------------------------------

@Composable
private fun SearchingBody() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tv_sources_searching),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoSourceAddonBody(onBrowseAddons: () -> Unit) {
    SheetMessage(
        title = stringResource(R.string.tv_no_source_title),
        body = stringResource(R.string.tv_sources_none_installed),
        actionLabel = stringResource(R.string.tv_browse_addons),
        onAction = onBrowseAddons,
    )
}

@Composable
private fun EmptyBody(onBrowseAddons: () -> Unit) {
    SheetMessage(
        title = stringResource(R.string.tv_sources_none_found_title),
        body = stringResource(R.string.tv_sources_none_found_body),
        actionLabel = stringResource(R.string.tv_browse_addons),
        onAction = onBrowseAddons,
    )
}

@Composable
private fun FilteredEmptyBody(onClear: () -> Unit) {
    SheetMessage(
        title = stringResource(R.string.tv_sources_filtered_empty),
        body = null,
        actionLabel = stringResource(R.string.tv_sources_clear_filters),
        onAction = onClear,
    )
}

@Composable
private fun SheetMessage(
    title: String,
    body: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * What a torrent-only row needs, said once and plainly.
 *
 * The alternative was hiding those rows, which makes a correctly working
 * torrent addon look like a broken one - the user installed something, it
 * answered with sixty results, and the app showed nothing.
 */
@Composable
private fun TorrentNoticeDialog(onBrowseAddons: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text(stringResource(R.string.tv_torrent_title)) },
        text = { Text(stringResource(R.string.tv_torrent_body)) },
        confirmButton = {
            TextButton(onClick = onBrowseAddons) {
                Text(stringResource(R.string.tv_browse_addons))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_got_it)) }
        },
    )
}

/**
 * A link to somebody's web player, which is not a torrent and must not be
 * described as one.
 */
@Composable
private fun ExternalNoticeDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text(stringResource(R.string.tv_external_title)) },
        text = { Text(stringResource(R.string.tv_external_body)) },
        confirmButton = {
            TextButton(onClick = onOpen) { Text(stringResource(R.string.tv_open_link)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tv_got_it)) }
        },
    )
}

/**
 * Which addons were asked and did not answer.
 *
 * Named, because the usual cause is an addon that requires an account and
 * enforces it at the stream call rather than at install - it looks perfectly
 * healthy in the addon list and returns nothing forever. Saying "nothing found"
 * for that is Koda taking the blame for someone else's login wall.
 */
@Composable
private fun FailedAddonsNotice(names: List<String>, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (names.size == 1) {
                stringResource(R.string.tv_addons_failed_one, names.first())
            } else {
                stringResource(
                    R.string.tv_addons_failed_many,
                    names.size,
                    names.joinToString(", "),
                )
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// --- Formatting -------------------------------------------------------------

/**
 * Size for a badge, in the binary units release names are posted in.
 *
 * Hoisted and internal so the rounding can be tested: "1.0 GB" for a 1.04 GB
 * file is fine, "1024 MB" is not.
 */
internal fun formatSize(bytes: Long): String {
    val gib = 1024.0 * 1024 * 1024
    val mib = 1024.0 * 1024
    return when {
        bytes >= gib -> String.format(java.util.Locale.US, "%.1f GB", bytes / gib)
        bytes >= mib -> String.format(java.util.Locale.US, "%.0f MB", bytes / mib)
        else -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

/**
 * The provenance line: who found it, from where, and how healthy it is.
 *
 * Built here rather than in the data layer because it is presentation, and
 * empty parts are dropped rather than rendered as blanks - most addons supply
 * one of the three.
 */
@Composable
private fun buildProvenance(source: TvSource): String {
    val parts = buildList {
        add(source.addonName)
        source.tags.indexer?.let { add(it) }
        source.tags.seeders?.let { add(stringResource(R.string.tv_source_seeders, it)) }
        source.tags.releaseGroup?.let { add(it) }
    }
    return parts.joinToString("  ")
}

/**
 * A language chip's label.
 *
 * `java.util.Locale` already carries every display name the platform knows, in
 * the viewer's own language, so nothing here is translated by hand. An unknown
 * code falls back to itself uppercased rather than being dropped, because a
 * chip that filters something real must still be tappable.
 */
internal fun languageLabel(code: String): String {
    val locale = java.util.Locale.forLanguageTag(code)
    val name = locale.getDisplayLanguage(java.util.Locale.getDefault())
    return if (name.isNotBlank() && !name.equals(code, ignoreCase = true)) {
        name.replaceFirstChar { it.uppercase() }
    } else {
        code.uppercase()
    }
}
