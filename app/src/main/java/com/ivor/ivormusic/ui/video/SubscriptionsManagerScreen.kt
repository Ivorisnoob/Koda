package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LocalSubscription
import com.ivor.ivormusic.data.SubscriptionImportResult
import com.ivor.ivormusic.ui.components.SEARCH_FIELD_MIN_ITEMS
import com.ivor.ivormusic.ui.components.SearchEmptyState
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.ui.home.HomeViewModel
import com.ivor.ivormusic.util.MatchField
import com.ivor.ivormusic.util.fuzzyScore
import kotlinx.coroutines.launch

/**
 * Everything you can do to the device-local subscription list: bring one in,
 * take one out, and sort it into groups.
 *
 * Import is the whole reason this screen exists. Someone arriving from
 * NewPipe, PipePipe, Tubular or a Google Takeout has a file already, and the
 * alternative to reading it is re-subscribing to two hundred channels by
 * hand - so the file picker takes anything and the format is sniffed rather
 * than asked for.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SubscriptionsManagerScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val subscriptions by viewModel.localSubscriptions.collectAsState()
    val groups by viewModel.subscriptionGroups.collectAsState()
    val isImporting by viewModel.isImportingSubscriptions.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val isConnected by viewModel.isYouTubeConnected.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var importSummary by remember { mutableStateOf<SubscriptionImportResult?>(null) }
    var groupBeingEdited by remember { mutableStateOf<String?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    // Deleting a group is a small target sitting inside a tappable row, and
    // rebuilding a fifty-channel grouping by hand is not a cheap undo.
    var groupToDelete by remember { mutableStateOf<com.ivor.ivormusic.data.SubscriptionGroup?>(null) }
    var channelForGroups by remember { mutableStateOf<LocalSubscription?>(null) }

    // This screen is only ever opened with a particular channel in mind -
    // to put it in a group, or to drop it - so a long list without a filter
    // is the whole job done by hand.
    var channelQuery by remember { mutableStateOf("") }

    val sortedSubscriptions = remember(subscriptions) {
        subscriptions.sortedBy { it.name.lowercase() }
    }
    val matchedSubscriptions = remember(sortedSubscriptions, channelQuery) {
        filterChannels(sortedSubscriptions, channelQuery)
    }

    // SAF rather than a path: the file arrives as a content uri that cannot be
    // opened as a File, and asking for storage permission to read one export
    // would be a far worse trade.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importSubscriptions(uri) { importSummary = it }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportSubscriptions(uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Exported ${subscriptions.size} channels"
                        else stringResource(R.string.sm_write_failed)
                    )
                }
            }
        }
    }

    // Names and avatars for imported channels arrive later than the channels
    // themselves; kick that off when the list is actually being looked at.
    LaunchedEffect(Unit) { viewModel.backfillLocalChannelProfiles() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settings_subscriptions), fontWeight = FontWeight.Bold)
                        Text(
                            text = if (subscriptions.isEmpty()) stringResource(R.string.sm_nothing_followed)
                            else "${subscriptions.size} on this device",
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
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isImporting) {
                item(key = "import-progress") {
                    ImportProgressCard(progress = importProgress)
                }
            }

            item(key = "transfer") {
                ManagerSection(title = stringResource(R.string.sm_transfer)) {
                    ManagerRow(
                        icon = Icons.Rounded.FileUpload,
                        title = stringResource(R.string.sm_import_file),
                        subtitle = stringResource(R.string.sm_import_file_sub),
                        enabled = !isImporting,
                        onClick = {
                            // Exports are .json, .csv, .opml or .xml, backups
                            // are .zip, and a fair number of file providers
                            // report all of them as octet-stream, so the filter
                            // has to stay wide.
                            importLauncher.launch(
                                arrayOf(
                                    "application/json", "text/csv", "text/comma-separated-values",
                                    "text/xml", "application/xml", "text/plain",
                                    "application/zip", "application/x-zip-compressed",
                                    "application/octet-stream"
                                )
                            )
                        }
                    )
                    ManagerDivider()
                    ManagerRow(
                        icon = Icons.Rounded.CloudDownload,
                        title = stringResource(R.string.sm_copy_account),
                        subtitle = if (isConnected) {
                            stringResource(R.string.sm_copy_sub)
                        } else {
                            stringResource(R.string.sm_sign_in_first)
                        },
                        enabled = !isImporting,
                        onClick = {
                            if (isConnected) {
                                viewModel.importSubscriptionsFromAccount { importSummary = it }
                            } else {
                                onLoginClick()
                            }
                        }
                    )
                    ManagerDivider()
                    ManagerRow(
                        icon = Icons.Rounded.FileDownload,
                        title = stringResource(R.string.sm_export),
                        subtitle = if (subscriptions.isEmpty()) {
                            stringResource(R.string.sm_nothing_to_export)
                        } else {
                            stringResource(R.string.sm_readable_by_newpipe)
                        },
                        enabled = subscriptions.isNotEmpty(),
                        onClick = { exportLauncher.launch("koda_subscriptions.json") }
                    )
                }
            }

            item(key = "groups-header") {
                ManagerSection(title = stringResource(R.string.sp_groups)) {
                    if (groups.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sm_groups_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        groups.forEachIndexed { index, group ->
                            if (index > 0) ManagerDivider()
                            ManagerRow(
                                icon = Icons.Rounded.Folder,
                                title = group.name,
                                subtitle = "${group.channelIds.size} channels",
                                onClick = { groupBeingEdited = group.id },
                                trailing = {
                                    IconButton(onClick = { groupToDelete = group }) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Delete ${group.name}",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                    ManagerDivider()
                    ManagerRow(
                        icon = Icons.Rounded.Add,
                        title = stringResource(R.string.sm_new_group),
                        subtitle = null,
                        enabled = subscriptions.isNotEmpty(),
                        onClick = { showNewGroupDialog = true }
                    )
                }
            }

            if (subscriptions.isNotEmpty()) {
                item(key = "channels-title") {
                    Column {
                        Text(
                            text = if (channelQuery.isBlank()) {
                                stringResource(R.string.section_channels)
                            } else {
                                "Channels - ${matchedSubscriptions.size} of ${subscriptions.size}"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        )
                        if (subscriptions.size >= SEARCH_FIELD_MIN_ITEMS) {
                            Spacer(Modifier.height(8.dp))
                            SearchField(
                                query = channelQuery,
                                onQueryChange = { channelQuery = it },
                                placeholder = stringResource(R.string.search_channels)
                            )
                        }
                    }
                }

                if (matchedSubscriptions.isEmpty()) {
                    item(key = "no-channel-matches") {
                        SearchEmptyState(
                            title = "No channels match \"$channelQuery\"",
                            hint = stringResource(R.string.sc_search_hint)
                        )
                    }
                }

                items(matchedSubscriptions, key = { it.channelId }) { channel ->
                    ManagedChannelRow(
                        channel = channel,
                        groupCount = groups.count { channel.channelId in it.channelIds },
                        onEditGroups = { channelForGroups = channel },
                        onRemove = { viewModel.unsubscribeLocally(channel.channelId) }
                    )
                }

                // Hidden while filtering. "Remove all" sitting directly under
                // three search results reads as "remove these three", and it
                // is not - it clears the lot.
                if (channelQuery.isBlank()) item(key = "clear-all") {
                    OutlinedButton(
                        onClick = { showClearConfirmation = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sm_remove_all))
                    }
                }
            } else if (!isImporting) {
                item(key = "empty") {
                    EmptyManagerState()
                }
            }
        }
    }

    importSummary?.let { result ->
        ImportSummaryDialog(result = result, onDismiss = { importSummary = null })
    }

    if (showNewGroupDialog) {
        GroupNameDialog(
            initialName = "",
            title = stringResource(R.string.sm_new_group),
            onConfirm = { name ->
                viewModel.createSubscriptionGroup(name)
                showNewGroupDialog = false
            },
            onDismiss = { showNewGroupDialog = false }
        )
    }

    // Resolved from the live group list, so toggling a channel inside the
    // dialog re-renders it with the new selection.
    val editingGroup = groupBeingEdited?.let { id -> groups.firstOrNull { it.id == id } }
    if (groupBeingEdited != null && editingGroup == null) {
        // The group went away underneath the open dialog. Clearing the state
        // has to happen in an effect - writing it straight from the composable
        // body is a write during composition.
        LaunchedEffect(groupBeingEdited) { groupBeingEdited = null }
    }
    editingGroup?.let { group ->
        GroupChannelsDialog(
            groupName = group.name,
            channels = sortedSubscriptions,
            selectedIds = group.channelIds.toSet(),
            onToggle = { viewModel.toggleChannelInGroup(group.id, it) },
            onRename = { viewModel.renameSubscriptionGroup(group.id, it) },
            onDismiss = { groupBeingEdited = null }
        )
    }

    channelForGroups?.let { channel ->
        ChannelGroupsDialog(
            channelName = channel.name,
            groups = groups,
            selectedGroupIds = groups.filter { channel.channelId in it.channelIds }
                .map { it.id }.toSet(),
            onToggle = { viewModel.toggleChannelInGroup(it, channel.channelId) },
            onDismiss = { channelForGroups = null }
        )
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            title = { Text("Delete ${group.name}?", fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.sm_delete_group_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubscriptionGroup(group.id)
                    groupToDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            title = { Text(stringResource(R.string.sm_remove_all_q), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.sm_remove_all_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLocalSubscriptions()
                    showClearConfirmation = false
                }) { Text(stringResource(R.string.ni_unblock_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

/**
 * Real progress, not a spinner: resolving a two-hundred-channel OPML is one
 * network call per handle, and an indeterminate indicator over that reads as
 * a hang.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportProgressCard(progress: Pair<Int, Int>?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.sm_importing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = progress?.let { (done, total) -> "$done of $total channels" }
                    ?: stringResource(R.string.bk_reading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            if (progress != null && progress.second > 0) {
                LinearWavyProgressIndicator(
                    progress = { progress.first.toFloat() / progress.second },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EmptyManagerState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Subscriptions,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sm_none_yet),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sm_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ManagerSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ManagerDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    )
}

@Composable
private fun ManagerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * contentAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ManagedChannelRow(
    channel: LocalSubscription,
    groupCount: Int,
    onEditGroups: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    groupCount > 0 -> "In $groupCount group${if (groupCount == 1) "" else "s"}"
                    !channel.handle.isNullOrBlank() -> channel.handle
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onEditGroups) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = "Groups for ${channel.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Unfollow ${channel.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Reports what actually happened rather than a bare "done": an import that
 * silently dropped 30 of 200 channels is the case worth explaining, and
 * "already following" is the reassuring answer when a re-import appears to
 * have done nothing.
 */
@Composable
private fun ImportSummaryDialog(result: SubscriptionImportResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subscriptions,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = if (result.error != null) stringResource(R.string.sm_import_failed) else stringResource(R.string.sm_import_finished),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column {
                if (result.error != null) {
                    Text(result.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SummaryLine("Added", "${result.added}")
                    if (result.alreadyPresent > 0) {
                        SummaryLine("Already following", "${result.alreadyPresent}")
                    }
                    if (result.unresolved > 0) {
                        SummaryLine("Couldn't be found", "${result.unresolved}")
                    }
                    if (result.skippedOtherService > 0) {
                        SummaryLine("Other services", "${result.skippedOtherService}")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.sm_foreign_skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.action_done)) }
        }
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun GroupNameDialog(
    initialName: String,
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.name_label)) },
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

/**
 * Name first, @handle second, ranked by how well each matched. Both stores
 * carry a handle, so typing one behaves the same wherever the channel came
 * from.
 */
private fun filterChannels(
    channels: List<LocalSubscription>,
    query: String
): List<LocalSubscription> {
    if (query.isBlank()) return channels
    return channels
        .mapNotNull { channel ->
            fuzzyScore(
                query,
                MatchField(channel.name, weight = 3),
                MatchField(channel.handle.orEmpty(), weight = 2)
            )?.let { channel to it }
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

/** Pick which channels belong to a group. */
@Composable
private fun GroupChannelsDialog(
    groupName: String,
    channels: List<LocalSubscription>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showRename by remember { mutableStateOf(false) }

    // Building a group means picking a handful out of everything followed, so
    // this list is the one that is always too long by definition.
    var query by remember { mutableStateOf("") }
    val showSearch = channels.size >= SEARCH_FIELD_MIN_ITEMS
    val matched = remember(channels, query) { filterChannels(channels, query) }

    if (showRename) {
        GroupNameDialog(
            initialName = groupName,
            title = "Rename group",
            onConfirm = {
                onRename(it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(groupName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename group")
                }
            }
        },
        text = {
            Column {
                if (showSearch) {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.search_channels)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                // Shorter when the field is above it, so the dialog does not
                // grow past a small phone's screen.
                LazyColumn(
                    modifier = Modifier.height(if (showSearch) 300.dp else 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (matched.isEmpty()) {
                        item(key = "no-matches") {
                            Text(
                                text = "No channels match \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    items(matched, key = { it.channelId }) { channel ->
                        val selected = channel.channelId in selectedIds
                        FilterChip(
                            selected = selected,
                            onClick = { onToggle(channel.channelId) },
                            label = {
                                Text(
                                    channel.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.action_done)) }
        }
    )
}

/** Pick which groups a channel belongs to - the same relation, from the other end. */
@Composable
private fun ChannelGroupsDialog(
    channelName: String,
    groups: List<com.ivor.ivormusic.data.SubscriptionGroup>,
    selectedGroupIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text(channelName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            if (groups.isEmpty()) {
                Text(
                    "You haven't made any groups yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = group.id in selectedGroupIds,
                            onClick = { onToggle(group.id) },
                            label = { Text(group.name) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.action_done)) }
        }
    )
}
