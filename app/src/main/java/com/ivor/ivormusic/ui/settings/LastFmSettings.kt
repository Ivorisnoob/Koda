package com.ivor.ivormusic.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.*
import com.ivor.ivormusic.ui.components.AvatarImage
import java.text.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class LastFmViewModel(application: Application) : AndroidViewModel(application) {
    val repository = LastFmRepository.get(application)
    val state = repository.state
}

@Composable
internal fun LastFmHubRow(onClick: () -> Unit, model: LastFmViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    SettingsHubRow(icon = Icons.Rounded.History, title = stringResource(R.string.lastfm_title),
        value = when {
            !state.enabled -> stringResource(R.string.lastfm_off)
            state.connected -> state.user
            else -> stringResource(R.string.lastfm_not_connected)
        }, onClick = onClick, tint = MaterialTheme.colorScheme.tertiary,
        explanation = stringResource(R.string.lastfm_intro))
}

@Composable
internal fun LastFmSettingsPage(onBack: () -> Unit, model: LastFmViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val repository = model.repository
    val context = LocalContext.current
    val incognito by IncognitoMode.enabled(context).collectAsStateWithLifecycle()
    val theme = remember { ThemePreferences(context.applicationContext) }
    val localOnly by theme.localOnlyMode.collectAsStateWithLifecycle()
    val paused = incognito || localOnly
    val online = state.enabled && !paused
    var accountTab by rememberSaveable { mutableStateOf(false) }
    // Credentials never enter saved-instance-state or navigation arguments.
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    LaunchedEffect(state.pendingAuth) {
        if (state.pendingAuth) { key = ""; secret = "" }
    }
    val openBrowser: (String) -> Unit = { url ->
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: android.content.ActivityNotFoundException) { repository.reportBrowserFailure() }
    }
    val profileUrl = "https://www.last.fm/user/${Uri.encode(state.user)}"
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, online, state.connected) {
        if (online && state.connected) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    repository.refresh()
                    kotlinx.coroutines.delay(60_000)
                }
            }
        }
    }
    val history = state.recent.filterNot { it.nowPlaying }
    val nowPlaying = state.recent.firstOrNull { it.nowPlaying }
        ?.takeIf { System.currentTimeMillis() - state.refreshedAt in 0L..120_000L }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val groups = history.groupBy { it.playedAt?.let { seconds -> Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate() } }

    Box(Modifier.fillMaxSize().imePadding()) {
        SettingsDetailScaffold(title = stringResource(R.string.lastfm_title), onBack = onBack, itemSpacing = 4.dp) {
            if (state.connected) {
                item {
                    LastFmAccountHeader(state, online, onProfile = { openBrowser(profileUrl) })
                }
                item {
                    LastFmSyncStatus(state, paused, onSync = repository::refresh)
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        listOf(R.string.lastfm_recent, R.string.lastfm_account_tab).forEachIndexed { index, title ->
                            ToggleButton(checked = accountTab == (index == 1), onCheckedChange = { accountTab = index == 1 },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    else ButtonGroupDefaults.connectedTrailingButtonShapes()) {
                                Text(stringResource(title))
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(40.dp))
                            Text(stringResource(R.string.lastfm_welcome_title), style = MaterialTheme.typography.headlineMedium)
                            Text(stringResource(R.string.lastfm_welcome_body), style = MaterialTheme.typography.bodyLarge)
                            if (!state.enabled) Button(onClick = { repository.setEnabled(true) }, shapes = ButtonDefaults.shapes()) {
                                Text(stringResource(R.string.lastfm_get_started))
                            }
                        }
                    }
                }
            }
            state.error?.let { error -> item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Info, null)
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } }
            if (state.connected && !accountTab) {
                if (nowPlaying != null && online) item {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.lastfm_now_playing), style = MaterialTheme.typography.labelLarge)
                            }
                            LastFmTrackRow(nowPlaying, online, prominent = true)
                        }
                    }
                }
                if (history.isEmpty()) item {
                    SettingsCard {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (state.busy) LoadingIndicator(Modifier.size(40.dp))
                            else Icon(Icons.Rounded.History, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(if (state.busy) R.string.lastfm_loading_history else R.string.lastfm_history_empty_title),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.lastfm_history_empty_body), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                groups.forEach { (date, tracks) ->
                    item(key = "day:$date") {
                        Text(when (date) {
                            today -> stringResource(R.string.lastfm_today)
                            today.minusDays(1) -> stringResource(R.string.lastfm_yesterday)
                            null -> stringResource(R.string.lastfm_recent)
                            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
                    }
                    tracks.forEachIndexed { index, track ->
                        item(key = "play:$date:$index:${track.playedAt}:${track.title}") {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(topStart = if (index == 0) 24.dp else 6.dp,
                                    topEnd = if (index == 0) 24.dp else 6.dp,
                                    bottomStart = if (index == tracks.lastIndex) 24.dp else 6.dp,
                                    bottomEnd = if (index == tracks.lastIndex) 24.dp else 6.dp)) {
                                Box(Modifier.padding(14.dp)) { LastFmTrackRow(track, online) }
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.lastfm_history_limit), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openBrowser("$profileUrl/library") }) {
                            Text(stringResource(R.string.lastfm_full_history))
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                item {
                    SettingsCard {
                        SettingsToggleRow(icon = Icons.Rounded.Sync, title = stringResource(R.string.lastfm_enable),
                            subtitle = stringResource(R.string.lastfm_toggle_short), enabled = state.enabled,
                            onToggle = repository::setEnabled)
                    }
                }
                if (paused) item {
                    Text(stringResource(R.string.lastfm_paused), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
                if (state.connected) {
                    item {
                        SettingsSection(title = stringResource(R.string.lastfm_account_details)) {
                            SettingsCard {
                                LastFmDetail(stringResource(R.string.lastfm_username), state.user)
                                if (state.profile.realName.isNotBlank()) {
                                    SettingsDivider()
                                    LastFmDetail(stringResource(R.string.lastfm_display_name), state.profile.realName)
                                }
                                if (state.profile.country.isNotBlank()) {
                                    SettingsDivider()
                                    LastFmDetail(stringResource(R.string.lastfm_country), state.profile.country)
                                }
                                state.profile.registeredAt?.let {
                                    SettingsDivider()
                                    LastFmDetail(stringResource(R.string.lastfm_member_since),
                                        DateFormat.getDateInstance(DateFormat.LONG).format(java.util.Date(it * 1000)))
                                }
                            }
                        }
                    }
                } else if (state.enabled) {
                    item {
                        LastFmConnectionCard(key, { key = it }, secret, { secret = it },
                            state, paused,
                            onCredentials = { openBrowser("https://www.last.fm/api/account/create") },
                            onSignIn = { repository.beginLogin(key, secret, openBrowser) },
                            onFinish = repository::finishLogin,
                            onRestart = { repository.restartLogin(openBrowser) })
                    }
                }
                item {
                    SettingsSection(title = stringResource(R.string.lastfm_how_it_works)) {
                        SettingsCard {
                            LastFmDetail(stringResource(R.string.lastfm_scrobble_definition), stringResource(R.string.lastfm_scrobble_explanation))
                            SettingsDivider()
                            LastFmDetail(stringResource(R.string.lastfm_privacy_title), stringResource(R.string.lastfm_privacy_body))
                        }
                    }
                }
                if (state.hasCredentials) item {
                    TextButton(onClick = repository::disconnect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.lastfm_disconnect))
                    }
                }
            }
        }
    }
}

@Composable
private fun LastFmDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun LastFmAccountHeader(state: LastFmState, online: Boolean, onProfile: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AvatarImage(url = state.profile.avatar.takeIf { online }, contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.large), showProgress = false)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.profile.realName.ifBlank { state.user }, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Text(state.user, style = MaterialTheme.typography.bodyMedium)
                    if (state.profile.subscriber) Text(stringResource(R.string.lastfm_pro), style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.playCount.toLongOrNull()?.let { NumberFormat.getIntegerInstance().format(it) } ?: "\u2014",
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.lastfm_total_scrobbles), style = MaterialTheme.typography.labelLarge)
            }
            FilledTonalButton(onClick = onProfile, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.lastfm_view_profile))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LastFmSyncStatus(state: LastFmState, paused: Boolean, onSync: () -> Unit) {
    val active = state.enabled && !paused
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.busy) LoadingIndicator(Modifier.size(28.dp))
                else Icon(if (active) Icons.Rounded.CheckCircle else Icons.Rounded.PauseCircle, null,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(when {
                        !active -> R.string.lastfm_sync_paused
                        state.busy -> R.string.lastfm_syncing
                        state.queued > 0 -> R.string.lastfm_waiting_sync
                        else -> R.string.lastfm_scrobbling_on
                    }), style = MaterialTheme.typography.titleSmall)
                    if (state.queued > 0) Text(stringResource(R.string.lastfm_queue, state.queued), style = MaterialTheme.typography.bodySmall)
                    else if (state.refreshedAt > 0) Text(stringResource(R.string.lastfm_refreshed,
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(state.refreshedAt))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (active) TextButton(onClick = onSync, enabled = !state.busy, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lastfm_sync))
            }
        }
    }
}

@Composable
private fun LastFmTrackRow(track: LastFmTrack, online: Boolean, prominent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AvatarImage(url = track.artwork.takeIf { online }, contentDescription = null, emptyIcon = Icons.Rounded.MusicNote,
            showProgress = false, modifier = Modifier.size(if (prominent) 72.dp else 56.dp).clip(MaterialTheme.shapes.medium))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.album.isNotBlank()) Text(track.album, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (prominent)
                    MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!track.nowPlaying) track.playedAt?.let {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(java.util.Date(it * 1000)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LastFmConnectionCard(
    key: String, onKey: (String) -> Unit, secret: String, onSecret: (String) -> Unit,
    state: LastFmState, paused: Boolean,
    onCredentials: () -> Unit, onSignIn: () -> Unit, onFinish: () -> Unit, onRestart: () -> Unit
) {
    var reveal by remember { mutableStateOf(false) }
    SettingsCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if (state.pendingAuth) R.string.lastfm_approve_title else R.string.lastfm_connect),
                style = MaterialTheme.typography.titleLarge)
            if (state.pendingAuth) {
                Icon(Icons.Rounded.Security, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.lastfm_finish_detail), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onFinish, enabled = !state.busy && !paused, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (state.busy) LoadingIndicator(Modifier.size(24.dp))
                    else Text(stringResource(R.string.lastfm_finish))
                }
                TextButton(onClick = onRestart, enabled = !state.busy && !paused) {
                    Text(stringResource(R.string.lastfm_restart_hint))
                }
            } else {
                Text(stringResource(R.string.lastfm_login_detail), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = key, onValueChange = onKey, singleLine = true, enabled = !state.busy,
                    label = { Text(stringResource(R.string.lastfm_key)) }, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium)
                OutlinedTextField(value = secret, onValueChange = onSecret, singleLine = true, enabled = !state.busy,
                    label = { Text(stringResource(R.string.lastfm_secret)) }, modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = if (reveal) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                stringResource(if (reveal) R.string.lastfm_hide_secret else R.string.lastfm_show_secret))
                        }
                    })
                TextButton(onClick = onCredentials) { Text(stringResource(R.string.lastfm_get_key)) }
                Button(onClick = onSignIn, enabled = !state.busy && !paused && key.isNotBlank() && secret.isNotBlank(),
                    shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (state.busy) LoadingIndicator(Modifier.size(24.dp))
                    else Text(stringResource(R.string.lastfm_sign_in))
                }
            }
        }
    }
}
