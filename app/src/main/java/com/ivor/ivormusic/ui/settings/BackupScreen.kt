package com.ivor.ivormusic.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.BackupManifest
import com.ivor.ivormusic.data.BackupRepository
import com.ivor.ivormusic.data.BackupSnapshot
import com.ivor.ivormusic.data.BackupTransfer
import com.ivor.ivormusic.data.FolderStatus
import com.ivor.ivormusic.data.ProfileKind
import com.ivor.ivormusic.data.RestoreResult
import com.ivor.ivormusic.data.ScheduledBackupRepository
import com.ivor.ivormusic.data.ScheduledBackupResult
import com.ivor.ivormusic.data.ScheduledBackupStatus
import com.ivor.ivormusic.data.UnsupportedBackupException
import com.ivor.ivormusic.ui.components.DismissibleSnackbarHost
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Writing this install to a file, and putting one back.
 *
 * A screen rather than two rows on a settings page, because a restore is not a
 * setting: it replaces everything the user has, so it has to be able to say
 * what is in the file *before* they agree to it, and it has to end somewhere
 * that explains what just happened. A row cannot do either.
 *
 * The whole flow works offline and signed out - nothing here makes a request -
 * which is the point of it existing at all for an app people sideload from
 * GitHub.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = remember { BackupRepository(context) }
    val scheduledRepo = remember { ScheduledBackupRepository(context) }

    val scheduledEnabled by scheduledRepo.enabled.collectAsState()
    val scheduledFolderUri by scheduledRepo.folderUri.collectAsState()
    val scheduledFolderName by scheduledRepo.folderDisplayName.collectAsState()
    val scheduledFrequencyHours by scheduledRepo.frequencyHours.collectAsState()
    val scheduledRetentionLimit by scheduledRepo.retentionLimit.collectAsState()
    val scheduledRequiresCharging by scheduledRepo.requiresCharging.collectAsState()
    val scheduledRequiresDeviceIdle by scheduledRepo.requiresDeviceIdle.collectAsState()
    val scheduledRequiresUnmetered by scheduledRepo.requiresUnmetered.collectAsState()
    val scheduledLastRunAt by scheduledRepo.lastRunAt.collectAsState()
    val scheduledLastRunStatus by scheduledRepo.lastRunStatus.collectAsState()
    val scheduledLastRunError by scheduledRepo.lastRunError.collectAsState()

    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var folderStatus by remember { mutableStateOf(FolderStatus.VALID) }

    LaunchedEffect(scheduledFolderUri, scheduledEnabled) {
        folderStatus = scheduledRepo.checkFolderStatus()
    }

    var phase by remember { mutableStateOf(BackupPhase.IDLE) }
    var lastBackupAt by remember { mutableStateOf(BackupRepository.lastBackupAt(context)) }
    var preview by remember { mutableStateOf<BackupSnapshot?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf<RestoreResult?>(null) }

    val busy = phase != BackupPhase.IDLE

    // A restore is mid-write for as long as this is up. Leaving the screen
    // cannot stop it, but it can leave the user somewhere that will not tell
    // them how it went, so back is held until it lands.
    BackHandler(enabled = phase == BackupPhase.RESTORING) {}

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupTransfer.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        phase = BackupPhase.BACKING_UP
        scope.launch {
            val manifest = repository.writeTo(uri)
            phase = BackupPhase.IDLE
            if (manifest == null) {
                failure = context.getString(R.string.bk_write_failed)
            } else {
                lastBackupAt = manifest.createdAt
                snackbarHostState.showSnackbar(backupWrittenMessage(manifest))
            }
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scheduledRepo.setFolder(uri)
            scheduledRepo.setEnabled(true)
            folderStatus = FolderStatus.VALID
        }
    }

    // The filter stays wide for the reason the subscription importer's does:
    // providers report a zip as octet-stream, as x-zip-compressed, or as
    // nothing at all. The file is recognised by its contents either way.
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        phase = BackupPhase.READING
        scope.launch {
            val snapshot = try {
                repository.peek(uri)
            } catch (e: UnsupportedBackupException) {
                phase = BackupPhase.IDLE
                failure = e.message
                return@launch
            } catch (e: Exception) {
                null
            }
            phase = BackupPhase.IDLE
            if (snapshot == null) {
                failure = context.getString(R.string.bk_read_failed)
            } else {
                preview = snapshot
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { DismissibleSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.settings_backup_and_restore), fontWeight = FontWeight.Bold)
                        Text(
                            text = lastBackupLine(lastBackupAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = phase != BackupPhase.RESTORING) {
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
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "progress") {
                AnimatedVisibility(
                    visible = busy,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                    ),
                    exit = fadeOut() + shrinkVertically(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                    )
                ) {
                    BusyCard(phase)
                }
            }

            item(key = "scheduled") {
                SettingsSection(title = stringResource(R.string.bk_scheduled_title)) {
                    SettingsCard {
                        SettingsToggleRow(
                            icon = Icons.Rounded.Schedule,
                            title = stringResource(R.string.bk_scheduled_title),
                            subtitle = stringResource(R.string.bk_scheduled_sub),
                            enabled = scheduledEnabled,
                            onToggle = { enable ->
                                if (enable && scheduledFolderUri == null) {
                                    folderLauncher.launch(null)
                                } else {
                                    scheduledRepo.setEnabled(enable)
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = scheduledEnabled,
                            enter = fadeIn() + expandVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            ),
                            exit = fadeOut() + shrinkVertically(
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)
                            )
                        ) {
                            Column {
                                if (folderStatus == FolderStatus.PERMISSION_REVOKED || folderStatus == FolderStatus.FOLDER_MISSING) {
                                    FolderWarningCard(
                                        status = folderStatus,
                                        onFix = { folderLauncher.launch(null) }
                                    )
                                }

                                SettingsDivider()
                                SettingsRow(
                                    icon = Icons.Rounded.Folder,
                                    title = stringResource(R.string.bk_folder_title),
                                    subtitle = scheduledFolderName ?: stringResource(R.string.bk_folder_none),
                                    onClick = { folderLauncher.launch(null) },
                                    showChevron = true
                                )

                                SettingsDivider()
                                SettingsRow(
                                    icon = Icons.Rounded.Update,
                                    title = stringResource(R.string.bk_freq_title),
                                    subtitle = frequencyLabel(scheduledFrequencyHours),
                                    onClick = { showFrequencyDialog = true },
                                    showChevron = true
                                )

                                SettingsDivider()
                                SettingsRow(
                                    icon = Icons.Rounded.Layers,
                                    title = stringResource(R.string.bk_retention_title),
                                    subtitle = retentionLabel(scheduledRetentionLimit),
                                    onClick = { showRetentionDialog = true },
                                    showChevron = true
                                )

                                SettingsDivider()
                                SettingsToggleRow(
                                    icon = Icons.Rounded.Power,
                                    title = stringResource(R.string.bk_constraint_charging),
                                    subtitle = stringResource(R.string.bk_constraint_charging_sub),
                                    enabled = scheduledRequiresCharging,
                                    onToggle = { scheduledRepo.setRequiresCharging(it) }
                                )

                                SettingsDivider()
                                SettingsToggleRow(
                                    icon = Icons.Rounded.Bedtime,
                                    title = stringResource(R.string.bk_constraint_idle),
                                    subtitle = stringResource(R.string.bk_constraint_idle_sub),
                                    enabled = scheduledRequiresDeviceIdle,
                                    onToggle = { scheduledRepo.setRequiresDeviceIdle(it) }
                                )

                                SettingsDivider()
                                SettingsToggleRow(
                                    icon = Icons.Rounded.Wifi,
                                    title = stringResource(R.string.bk_constraint_unmetered),
                                    subtitle = stringResource(R.string.bk_constraint_unmetered_sub),
                                    enabled = scheduledRequiresUnmetered,
                                    onToggle = { scheduledRepo.setRequiresUnmetered(it) }
                                )

                                if (scheduledFolderUri != null && folderStatus == FolderStatus.VALID) {
                                    SettingsDivider()
                                    SettingsRow(
                                        icon = Icons.Rounded.PlayArrow,
                                        title = stringResource(R.string.bk_run_now),
                                        subtitle = stringResource(R.string.bk_run_now_sub),
                                        onClick = {
                                            if (!busy) {
                                                phase = BackupPhase.BACKING_UP
                                                scope.launch {
                                                    when (val result = scheduledRepo.executeBackup()) {
                                                        is ScheduledBackupResult.Success -> {
                                                            phase = BackupPhase.IDLE
                                                            lastBackupAt = result.manifest.createdAt
                                                            val baseMsg = context.getString(
                                                                R.string.bk_backup_saved_to_folder,
                                                                scheduledFolderName ?: ""
                                                            )
                                                            val fullMsg = if (result.prunedCount > 0) {
                                                                baseMsg + " • " + context.getString(
                                                                    R.string.bk_pruned_count,
                                                                    result.prunedCount
                                                                )
                                                            } else {
                                                                baseMsg
                                                            }
                                                            snackbarHostState.showSnackbar(fullMsg)
                                                        }
                                                        is ScheduledBackupResult.Failure -> {
                                                            phase = BackupPhase.IDLE
                                                            failure = result.message
                                                            folderStatus = scheduledRepo.checkFolderStatus()
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        showChevron = false
                                    )

                                    val statusCaption = when {
                                        scheduledLastRunAt > 0L && scheduledLastRunStatus == ScheduledBackupStatus.SUCCESS -> {
                                            stringResource(
                                                R.string.bk_last_run_success,
                                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                                    .format(Date(scheduledLastRunAt))
                                            )
                                        }
                                        scheduledLastRunStatus != ScheduledBackupStatus.IDLE && scheduledLastRunError != null -> {
                                            stringResource(R.string.bk_last_run_failed, scheduledLastRunError ?: "")
                                        }
                                        else -> stringResource(R.string.bk_last_run_never)
                                    }

                                    Text(
                                        text = statusCaption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (scheduledLastRunStatus == ScheduledBackupStatus.SUCCESS || scheduledLastRunStatus == ScheduledBackupStatus.IDLE) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "actions") {
                SettingsSection(title = stringResource(R.string.bk_this_install)) {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Save,
                            title = stringResource(R.string.bk_create),
                            subtitle = stringResource(R.string.bk_create_sub),
                            onClick = {
                                if (!busy) createLauncher.launch(BackupRepository.suggestedFileName())
                            },
                            showChevron = true
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Restore,
                            title = stringResource(R.string.bk_restore),
                            subtitle = stringResource(R.string.bk_restore_sub),
                            onClick = {
                                if (!busy) {
                                    openLauncher.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream"
                                        )
                                    )
                                }
                            },
                            tint = SettingsRowDefaults.destructiveTint,
                            showChevron = true
                        )
                    }
                }
            }

            item(key = "included") {
                SettingsSection(title = stringResource(R.string.bk_holds)) {
                    SettingsCard {
                        InfoBlock(
                            icon = Icons.Rounded.Info,
                            lines = listOf(
                                stringResource(R.string.bk_hold_1),
                                stringResource(R.string.bk_hold_2),
                                stringResource(R.string.bk_hold_3),
                                stringResource(R.string.bk_hold_4),
                                stringResource(R.string.bk_hold_5),
                                stringResource(R.string.bk_hold_6)
                            )
                        )
                    }
                }
            }

            item(key = "excluded") {
                SettingsSection(title = stringResource(R.string.bk_not_holds)) {
                    SettingsCard {
                        InfoBlock(
                            icon = Icons.Rounded.CloudOff,
                            lines = listOf(
                                stringResource(R.string.bk_not_1),
                                stringResource(R.string.bk_not_2),
                                stringResource(R.string.bk_not_3)
                            )
                        )
                    }
                }
            }
        }
    }

    if (showFrequencyDialog) {
        FrequencyDialog(
            currentHours = scheduledFrequencyHours,
            onSelect = { scheduledRepo.setFrequencyHours(it) },
            onDismiss = { showFrequencyDialog = false }
        )
    }

    if (showRetentionDialog) {
        RetentionDialog(
            currentLimit = scheduledRetentionLimit,
            onSelect = { scheduledRepo.setRetentionLimit(it) },
            onDismiss = { showRetentionDialog = false }
        )
    }

    preview?.let { snapshot ->
        RestoreConfirmDialog(
            snapshot = snapshot,
            onDismiss = { preview = null },
            onConfirm = {
                preview = null
                phase = BackupPhase.RESTORING
                scope.launch {
                    val result = repository.apply(snapshot)
                    phase = BackupPhase.IDLE
                    if (result.success) restored = result else failure = result.error
                }
            }
        )
    }

    restored?.let { result ->
        RestartDialog(result = result, onRestart = { restartApp(context) })
    }

    failure?.let { message ->
        FailureDialog(message = message, onDismiss = { failure = null })
    }
}

private enum class BackupPhase { IDLE, BACKING_UP, READING, RESTORING }

// ---------------- Pieces ----------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BusyCard(phase: BackupPhase) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoadingIndicator(modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = when (phase) {
                        BackupPhase.BACKING_UP -> stringResource(R.string.bk_writing)
                        BackupPhase.READING -> stringResource(R.string.bk_reading)
                        BackupPhase.RESTORING -> stringResource(R.string.bk_restoring)
                        BackupPhase.IDLE -> ""
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (phase == BackupPhase.RESTORING) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.bk_dont_leave),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    lines: List<String>
) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lines.forEach { line ->
                Text(
                    text = line,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * What is in the file, and what agreeing to it costs.
 *
 * The counts come out of the backup's own manifest rather than out of the
 * snapshot that was just parsed, so a file written by a build that knew about
 * a store this one does not still describes itself honestly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreConfirmDialog(
    snapshot: BackupSnapshot,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val accounts = snapshot.profiles.count { it.kind == ProfileKind.YOUTUBE.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.bk_confirm_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = manifestLine(snapshot.manifest),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val contents = describeContents(snapshot.manifest)
                if (contents.isEmpty()) {
                    // A valid backup of an install with nothing in it yet. Worth
                    // saying out loud: restoring it would wipe this device for
                    // nothing, and that is exactly the mistake to catch here.
                    Text(
                        text = stringResource(R.string.bk_empty_warn),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    contents.forEach { line ->
                        Text(
                            text = "•  $line",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.bk_replace_warn),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
                if (accounts > 0) {
                    Text(
                        text = "Backups never carry a sign-in, so " +
                            (if (accounts == 1) "the account in it" else "the $accounts accounts in it") +
                            " will come back needing to be signed into again. Accounts " +
                            "already signed in on this device stay signed in.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.bk_restarts),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.bk_replace_restore), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/**
 * The end of a restore.
 *
 * Deliberately not dismissible and with no way out but the button: the process
 * has to go before any of this is visible, and an app left running on top of
 * data it has already replaced would show a mix of the two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestartDialog(result: RestoreResult, onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
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
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.bk_restored),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.bk_restart_needed),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (result.signInNeeded > 0) {
                    Text(
                        text = stringResource(R.string.bk_sign_in_again),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRestart) { Text(stringResource(R.string.bk_restart_now)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FailureDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.bk_failed_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } }
    )
}

// ---------------- Text ----------------

internal fun lastBackupLine(lastBackupAt: Long?): String {
    if (lastBackupAt == null) return "Never backed up from this device"
    return "Last backup ${relativeDay(lastBackupAt)}"
}

private fun relativeDay(timestamp: Long): String {
    val days = ((System.currentTimeMillis() - timestamp) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "today"
        days == 1 -> "yesterday"
        days < 30 -> "$days days ago"
        else -> "on " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
    }
}

private fun manifestLine(manifest: BackupManifest): String {
    val date = if (manifest.createdAt > 0) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(manifest.createdAt))
    } else {
        "an unknown date"
    }
    val from = manifest.device?.let { " from $it" }.orEmpty()
    val version = manifest.appVersionName?.let { ", Koda $it" }.orEmpty()
    return "Taken $date$from$version."
}

private fun backupWrittenMessage(manifest: BackupManifest): String {
    val lines = describeContents(manifest)
    return if (lines.isEmpty()) "Backup saved" else "Backup saved: " + lines.joinToString(", ")
}

/**
 * The manifest's counts as lines, skipping anything at zero.
 *
 * Zero-suppressed rather than shown as "0 playlists": a list of zeroes reads
 * like something failed, when the honest meaning is simply that the install had
 * none. An empty result is handled separately by the caller, which says so.
 */
private fun describeContents(manifest: BackupManifest): List<String> {
    fun count(key: String) = manifest.contents[key] ?: 0
    fun line(key: String, singular: String, plural: String): String? {
        val value = count(key)
        return when {
            value <= 0 -> null
            value == 1 -> "1 $singular"
            else -> "$value $plural"
        }
    }
    return listOfNotNull(
        line(BackupRepository.COUNT_PLAYLISTS, "playlist", "playlists"),
        line(BackupRepository.COUNT_VIDEO_PLAYLISTS, "video playlist", "video playlists"),
        line(BackupRepository.COUNT_SAVED_PLAYLISTS, "saved playlist", "saved playlists"),
        line(BackupRepository.COUNT_LIKED_SONGS, "liked song", "liked songs"),
        line(BackupRepository.COUNT_PLAY_HISTORY, "play", "plays"),
        line(BackupRepository.COUNT_WATCH_HISTORY, "watched video", "watched videos"),
        line(BackupRepository.COUNT_SUBSCRIPTIONS, "followed channel", "followed channels"),
        line(BackupRepository.COUNT_BLOCKED, "blocked channel", "blocked channels"),
        line(BackupRepository.COUNT_HIDDEN, "hidden video", "hidden videos"),
        line(BackupRepository.COUNT_SETTINGS, "setting", "settings"),
        // Only worth a line when there is more than one identity in the file;
        // "1 profile" is true of every install and tells nobody anything.
        count(BackupRepository.COUNT_PROFILES)
            .takeIf { it > 1 }?.let { "$it profiles" }
    )
}

// ---------------- Restart ----------------

/**
 * Relaunch the app as a cold start.
 *
 * A restore rewrites preference files and stored JSON underneath a process
 * that has already read all of it: `SharedPreferences` serves from an
 * in-memory map, five stores seed process-wide caches once, and `MusicService`
 * holds a repository and a media session of its own. Rather than reloading
 * each of those by hand - a list with no compiler behind it, where one
 * omission is a half-restored install - the process goes and comes back.
 *
 * The launch intent is started before the exit so there is something to return
 * to; `exit` rather than `killProcess` so the VM shuts down through the normal
 * path. If the launch intent cannot be resolved, the process is left alone
 * rather than killed into nothing.
 */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ?: return
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}


@Composable
private fun FolderWarningCard(
    status: FolderStatus,
    onFix: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (status == FolderStatus.PERMISSION_REVOKED) {
                        stringResource(R.string.bk_folder_permission_lost)
                    } else {
                        stringResource(R.string.bk_folder_not_found)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onFix,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.bk_folder_pick), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FrequencyDialog(
    currentHours: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Update,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.bk_freq_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ScheduledBackupRepository.FREQUENCY_OPTIONS.forEach { option ->
                    val selected = option.hours == currentHours
                    Surface(
                        onClick = {
                            haptics.confirm()
                            onSelect(option.hours)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun RetentionDialog(
    currentLimit: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.bk_retention_dialog_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.bk_retention_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ScheduledBackupRepository.RETENTION_OPTIONS.forEach { count ->
                    val selected = count == currentLimit
                    val label = when (count) {
                        3 -> stringResource(R.string.bk_retention_keep_3)
                        5 -> stringResource(R.string.bk_retention_keep_5)
                        10 -> stringResource(R.string.bk_retention_keep_10)
                        20 -> stringResource(R.string.bk_retention_keep_20)
                        else -> stringResource(R.string.bk_retention_format, count)
                    }
                    Surface(
                        onClick = {
                            haptics.confirm()
                            onSelect(count)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun frequencyLabel(hours: Long): String {
    val opt = ScheduledBackupRepository.FREQUENCY_OPTIONS.firstOrNull { it.hours == hours }
    return if (opt != null) stringResource(opt.labelRes) else "${hours}h"
}

@Composable
private fun retentionLabel(limit: Int): String {
    return when (limit) {
        3 -> stringResource(R.string.bk_retention_keep_3)
        5 -> stringResource(R.string.bk_retention_keep_5)
        10 -> stringResource(R.string.bk_retention_keep_10)
        20 -> stringResource(R.string.bk_retention_keep_20)
        else -> stringResource(R.string.bk_retention_format, limit)
    }
}
