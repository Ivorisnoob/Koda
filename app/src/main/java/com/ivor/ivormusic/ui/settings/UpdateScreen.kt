package com.ivor.ivormusic.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ApkAsset
import com.ivor.ivormusic.data.UpdateRepository
import com.ivor.ivormusic.data.UpdateResult
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    localOnlyMode: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val repository = remember { UpdateRepository() }
    var result by remember { mutableStateOf<UpdateResult>(UpdateResult.Checking) }
    var checkRequest by remember { mutableIntStateOf(0) }

    // Local Only is a key rather than an early return: turning it off in
    // another window has to start the check that was refused, and turning it
    // on has to stop showing a release the user can no longer download.
    LaunchedEffect(checkRequest, localOnlyMode) {
        if (localOnlyMode) {
            result = UpdateResult.LocalOnly
            return@LaunchedEffect
        }
        result = UpdateResult.Checking
        result = repository.checkForUpdate(
            repoPath = BuildConfig.GITHUB_REPO,
            currentVersion = BuildConfig.VERSION_NAME,
            forceRefresh = checkRequest > 0,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.us_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { checkRequest++ },
                    enabled = result !is UpdateResult.Checking && !localOnlyMode,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.us_check_again),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { UpdateStatusHero(result) }
            item { InstalledBuildCard() }
            item {
                AnimatedContent(
                    targetState = result,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "UpdateResult",
                ) { state ->
                    when (state) {
                        is UpdateResult.Checking -> CheckingCard()
                        is UpdateResult.UpdateAvailable -> AvailableUpdateContent(state)
                        is UpdateResult.UpToDate -> CurrentReleaseContent(state)
                        is UpdateResult.Error -> UpdateErrorCard(
                            message = state.message,
                            onRetry = { checkRequest++ },
                        )
                        is UpdateResult.NoReleases -> NoReleasesCard()
                        is UpdateResult.LocalOnly -> LocalOnlyUpdateCard()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateStatusHero(result: UpdateResult) {
    val colors = MaterialTheme.colorScheme
    val (container, content, icon) = when (result) {
        is UpdateResult.UpdateAvailable -> Triple(colors.primaryContainer, colors.onPrimaryContainer, Icons.Rounded.SystemUpdate)
        is UpdateResult.UpToDate -> Triple(colors.secondaryContainer, colors.onSecondaryContainer, Icons.Rounded.CheckCircle)
        is UpdateResult.Error -> Triple(colors.errorContainer, colors.onErrorContainer, Icons.Rounded.CloudOff)
        // Local Only is a setting the user chose, not a failure, so it reads as
        // a neutral surface rather than borrowing the error colours.
        is UpdateResult.LocalOnly -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Rounded.CloudOff)
        else -> Triple(colors.surfaceContainerHigh, colors.onSurface, Icons.Rounded.Info)
    }
    val title = when (result) {
        is UpdateResult.UpdateAvailable -> stringResource(R.string.us_available)
        is UpdateResult.UpToDate -> stringResource(R.string.us_up_to_date)
        is UpdateResult.Checking -> stringResource(R.string.us_checking_short)
        is UpdateResult.Error -> stringResource(R.string.us_error)
        is UpdateResult.NoReleases -> stringResource(R.string.us_no_releases_short)
        is UpdateResult.LocalOnly -> stringResource(R.string.local_only_title)
    }
    val subtitle = when (result) {
        is UpdateResult.UpdateAvailable -> result.releaseName.ifBlank {
            stringResource(R.string.us_version_label, result.latestVersion)
        }
        is UpdateResult.UpToDate -> stringResource(R.string.us_latest_body)
        is UpdateResult.Checking -> stringResource(R.string.us_installed_visible_while_checking)
        is UpdateResult.Error -> stringResource(R.string.us_check_failed_body)
        is UpdateResult.NoReleases -> stringResource(R.string.us_no_releases)
        is UpdateResult.LocalOnly -> stringResource(R.string.us_local_only_hero)
    }

    Surface(
        shape = RoundedCornerShape(36.dp),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialShapes.SoftBurst.toShape())
                        .background(content.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (result is UpdateResult.Checking) {
                        LoadingIndicator(modifier = Modifier.size(42.dp), color = content)
                    } else {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(36.dp))
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.78f),
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VersionChip(
                    text = stringResource(R.string.us_installed_version, BuildConfig.VERSION_NAME),
                    container = content.copy(alpha = 0.1f),
                    content = content,
                )
                if (result is UpdateResult.UpdateAvailable) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(20.dp),
                    )
                    VersionChip(
                        text = stringResource(R.string.us_available_version, result.latestVersion),
                        container = content.copy(alpha = 0.16f),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionChip(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(100), color = container, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun InstalledBuildCard() {
    val abi = remember { UpdateRepository.getDeviceAbi() }
    UpdateSection(
        title = stringResource(R.string.us_installed_on_device),
        icon = Icons.Rounded.PhoneAndroid,
    ) {
        DetailRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
        DetailDivider()
        DetailRow(stringResource(R.string.about_build), BuildConfig.VERSION_CODE.toString())
        DetailDivider()
        DetailRow(
            stringResource(R.string.about_build_type),
            if (BuildConfig.DEBUG) stringResource(R.string.about_debug) else stringResource(R.string.about_release),
        )
        DetailDivider()
        DetailRow(stringResource(R.string.us_android_api), "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        DetailDivider()
        DetailRow(stringResource(R.string.us_architecture), abi)
    }
}

@Composable
private fun CheckingCard() {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoadingIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.us_contacting_github),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.us_checking_background),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AvailableUpdateContent(result: UpdateResult.UpdateAvailable) {
    val context = LocalContext.current
    val bestApk = remember(result.apkAssets) { UpdateRepository.findBestApk(result.apkAssets) }

    // Koda hands the asset URL to the system browser and always has: it holds
    // no REQUEST_INSTALL_PACKAGES permission and never writes an APK itself, so
    // the download and the install prompt stay with the components the user
    // already trusts. What was missing was any acknowledgement that the tap did
    // anything - and, when no app could take the intent, the failure was
    // swallowed and the button looked dead.
    var handoff by remember(result.htmlUrl) { mutableStateOf<DownloadHandoff?>(null) }
    fun handOff(url: String, label: String) {
        handoff = if (context.openExternal(url)) {
            DownloadHandoff.Started(label)
        } else {
            DownloadHandoff.NoHandler
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        UpdateSection(title = stringResource(R.string.us_ready_to_download), icon = Icons.Rounded.Download) {
            DetailRow(stringResource(R.string.us_latest_version), result.latestVersion)
            formatReleaseDate(result.publishedAt)?.let {
                DetailDivider()
                DetailRow(stringResource(R.string.us_published), it)
            }
            if (bestApk != null) {
                DetailDivider()
                DetailRow(stringResource(R.string.us_apk), bestApk.name)
                if (bestApk.size > 0L) {
                    DetailDivider()
                    DetailRow(stringResource(R.string.us_download_size), formatFileSize(bestApk.size))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (bestApk != null) {
                        handOff(bestApk.downloadUrl, bestApk.name)
                    } else {
                        handOff(result.htmlUrl, result.latestVersion)
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(
                    imageVector = if (bestApk != null) Icons.Rounded.Download else Icons.Rounded.OpenInNew,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (bestApk != null) {
                        stringResource(R.string.us_download_for, UpdateRepository.getDeviceAbi())
                    } else {
                        stringResource(R.string.us_view_release_short)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { handOff(result.htmlUrl, result.latestVersion) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.us_view_release))
            }
            handoff?.let {
                Spacer(modifier = Modifier.height(14.dp))
                DownloadHandoffNotice(it)
            }
        }
        ReleaseNotesCard(result.releaseNotes)
        if (result.apkAssets.size > 1) {
            ApkVariantsCard(result.apkAssets, bestApk) { asset -> handOff(asset.downloadUrl, asset.name) }
        }
    }
}

/**
 * What happened to the last download tap. Koda cannot report progress it does
 * not own, so it reports the hand-off honestly instead of inventing a
 * percentage for a transfer running in another app.
 */
private sealed interface DownloadHandoff {
    data class Started(val label: String) : DownloadHandoff
    data object NoHandler : DownloadHandoff
}

@Composable
private fun DownloadHandoffNotice(handoff: DownloadHandoff) {
    val started = handoff is DownloadHandoff.Started
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (started) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        contentColor = if (started) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (started) Icons.Rounded.Download else Icons.Rounded.CloudOff,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (started) {
                        stringResource(R.string.us_handoff_started)
                    } else {
                        stringResource(R.string.us_handoff_no_handler)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (handoff) {
                        is DownloadHandoff.Started ->
                            stringResource(R.string.us_handoff_started_body, handoff.label)
                        DownloadHandoff.NoHandler ->
                            stringResource(R.string.us_handoff_no_handler_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun LocalOnlyUpdateCard() {
    UpdateSection(
        title = stringResource(R.string.local_only_title),
        icon = Icons.Rounded.CloudOff,
    ) {
        Text(
            text = stringResource(R.string.local_only_update_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.us_local_only_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CurrentReleaseContent(result: UpdateResult.UpToDate) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        UpdateSection(title = stringResource(R.string.us_current_release), icon = Icons.Rounded.NewReleases) {
            DetailRow(stringResource(R.string.us_latest_version), result.latestVersion)
            formatReleaseDate(result.publishedAt)?.let {
                DetailDivider()
                DetailRow(stringResource(R.string.us_published), it)
            }
            if (result.releaseName.isNotBlank() && result.releaseName != result.latestVersion) {
                DetailDivider()
                DetailRow(stringResource(R.string.us_release_name), result.releaseName)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { context.openExternal(result.htmlUrl) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.us_view_release_short))
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Rounded.OpenInNew, contentDescription = null)
            }
        }
        ReleaseNotesCard(result.releaseNotes)
    }
}

@Composable
private fun UpdateErrorCard(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.us_couldnt_check),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun NoReleasesCard() {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text = stringResource(R.string.us_no_releases),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(28.dp),
        )
    }
}

@Composable
private fun ReleaseNotesCard(releaseNotes: String) {
    val notes = remember(releaseNotes) { parseReleaseNotes(releaseNotes) }
    var expanded by remember(releaseNotes) { mutableStateOf(false) }
    val visibleNotes = if (expanded) notes else notes.take(10)

    UpdateSection(title = stringResource(R.string.us_whats_new), icon = Icons.Rounded.NewReleases) {
        if (visibleNotes.isEmpty()) {
            Text(
                text = stringResource(R.string.us_notes_on_github),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            visibleNotes.forEachIndexed { index, note ->
                ReleaseNoteRow(note)
                if (index != visibleNotes.lastIndex) Spacer(modifier = Modifier.height(10.dp))
            }
        }
        if (notes.size > 10) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (expanded) R.string.action_show_less else R.string.action_show_more))
            }
        }
    }
}

@Composable
private fun ReleaseNoteRow(note: ReleaseNote) {
    when (note) {
        is ReleaseNote.Heading -> Text(
            text = note.text,
            style = if (note.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        is ReleaseNote.Bullet -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(7.dp)
                    .clip(MaterialShapes.Circle.toShape())
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        is ReleaseNote.Paragraph -> Text(
            text = note.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApkVariantsCard(assets: List<ApkAsset>, bestApk: ApkAsset?, onOpen: (ApkAsset) -> Unit) {
    UpdateSection(title = stringResource(R.string.us_all_variants), icon = Icons.Rounded.Memory) {
        assets.forEachIndexed { index, asset ->
            val recommended = asset == bestApk
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpen(asset) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (recommended) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val secondary = listOfNotNull(
                        formatFileSize(asset.size).takeIf { asset.size > 0L },
                        stringResource(R.string.us_recommended).takeIf { recommended },
                    ).joinToString(" · ")
                    if (secondary.isNotEmpty()) {
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.song_options_download))
            }
            if (index != assets.lastIndex) DetailDivider()
        }
    }
}

@Composable
private fun UpdateSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.42f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
    }
}

@Composable
private fun DetailDivider() {
    Spacer(modifier = Modifier.height(11.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    )
    Spacer(modifier = Modifier.height(11.dp))
}

private sealed interface ReleaseNote {
    data class Heading(val text: String, val level: Int) : ReleaseNote
    data class Bullet(val text: AnnotatedString) : ReleaseNote
    data class Paragraph(val text: AnnotatedString) : ReleaseNote
}

private fun parseReleaseNotes(markdown: String): List<ReleaseNote> = buildList {
    markdown.lineSequence().forEach { source ->
        val line = source.trim()
        if (line.isBlank() || line.startsWith("![") || line.startsWith("<img", true) ||
            line.startsWith("https://github.com/user-attachments/")
        ) return@forEach
        val heading = Regex("^(#{1,4})\\s+(.*)$").find(line)
        if (heading != null) {
            add(ReleaseNote.Heading(heading.groupValues[2], heading.groupValues[1].length))
            return@forEach
        }
        val bullet = Regex("^[-*+]\\s+(.*)$").find(line)
        if (bullet != null) {
            add(ReleaseNote.Bullet(parseInlineMarkdown(bullet.groupValues[1])))
        } else {
            add(ReleaseNote.Paragraph(parseInlineMarkdown(line)))
        }
    }
}

private fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    Regex("\\*\\*(.*?)\\*\\*").findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun formatReleaseDate(value: String): String? = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
}.getOrNull()

private fun formatFileSize(sizeBytes: Long): String = when {
    sizeBytes >= 1_000_000_000 -> "%.1f GB".format(Locale.getDefault(), sizeBytes / 1_000_000_000.0)
    sizeBytes >= 1_000_000 -> "%.1f MB".format(Locale.getDefault(), sizeBytes / 1_000_000.0)
    sizeBytes >= 1_000 -> "%.1f KB".format(Locale.getDefault(), sizeBytes / 1_000.0)
    else -> "$sizeBytes B"
}

/**
 * Hand a release URL to whatever the user browses with, and say whether that
 * worked. The old version swallowed the failure, so a device with no activity
 * for `ACTION_VIEW` had a download button that did nothing at all.
 */
private fun Context.openExternal(url: String): Boolean {
    if (url.isBlank()) return false
    return runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.isSuccess
}
