package com.ivor.ivormusic.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.lossless.LosslessAuthException
import com.ivor.ivormusic.data.lossless.LosslessClient
import com.ivor.ivormusic.data.lossless.LosslessConfig
import com.ivor.ivormusic.data.lossless.LosslessQuality
import com.ivor.ivormusic.data.lossless.LosslessSettings
import com.ivor.ivormusic.ui.components.LosslessPill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a connection test stands, on the Test connection row. */
private sealed interface ServerTest {
    data object Idle : ServerTest
    data object Running : ServerTest
    data object Passed : ServerTest
    data object Refused : ServerTest
    data class Unreachable(val reason: String?) : ServerTest
}

/**
 * Settings > Advanced > Lossless audio.
 *
 * Its own page with its own store ([LosslessSettings], encrypted, outside
 * backups), like the newer Customization pages, so nothing threads through
 * `SettingsScreen`. It leads with the server, because nothing else here does
 * anything without one: the streaming and download switches sit greyed until
 * a server is set up, and each one's own options appear under it once it is
 * on. The server dialog is the page's own; nothing here moves the page while
 * it is open, which is the case `SettingsScreen` hosts dialogs for.
 */
@Composable
internal fun LosslessSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<LosslessConfig?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<ServerTest>(ServerTest.Idle) }

    LaunchedEffect(Unit) {
        config = withContext(Dispatchers.IO) {
            runCatching { LosslessSettings.read(context) }.getOrDefault(LosslessConfig())
        }
    }

    fun save(next: LosslessConfig) {
        config = next
        scope.launch(Dispatchers.IO) { runCatching { LosslessSettings.write(context, next) } }
    }

    fun runTest(target: LosslessConfig) {
        test = ServerTest.Running
        scope.launch {
            test = try {
                LosslessClient(context, target).search("test", LosslessQuality.CD)
                ServerTest.Passed
            } catch (e: CancellationException) {
                throw e
            } catch (e: LosslessAuthException) {
                ServerTest.Refused
            } catch (e: Exception) {
                ServerTest.Unreachable(e.message)
            }
        }
    }

    val current = config
    val configured = current?.isConfigured == true

    SettingsDetailScaffold(title = stringResource(R.string.lossless_title), onBack = onBack) {
        item { LosslessIntroCard(configured = configured, host = current?.serverHost) }

        item {
            SettingsSection(title = stringResource(R.string.lossless_section_server)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Dns,
                        title = stringResource(R.string.lossless_server),
                        subtitle = current?.serverHost?.let { host ->
                            if (current.secret.isNotBlank()) stringResource(R.string.lossless_server_signed, host)
                            else host
                        } ?: stringResource(R.string.lossless_server_none),
                        onClick = { showServerDialog = true },
                        showChevron = true,
                        explanation = stringResource(R.string.lossless_server_explanation)
                    )
                    if (configured) {
                        SettingsRow(
                            icon = Icons.Rounded.NetworkCheck,
                            title = stringResource(R.string.lossless_test),
                            subtitle = when (val state = test) {
                                ServerTest.Idle -> stringResource(R.string.lossless_test_idle)
                                ServerTest.Running -> stringResource(R.string.lossless_test_running)
                                ServerTest.Passed -> stringResource(R.string.lossless_test_passed)
                                ServerTest.Refused -> stringResource(R.string.lossless_test_refused)
                                is ServerTest.Unreachable -> stringResource(R.string.lossless_test_unreachable)
                            },
                            onClick = { if (test != ServerTest.Running) current?.let(::runTest) },
                            tint = when (test) {
                                ServerTest.Passed -> MaterialTheme.colorScheme.tertiary
                                ServerTest.Refused, is ServerTest.Unreachable -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        SettingsRow(
                            icon = Icons.Rounded.LinkOff,
                            title = stringResource(R.string.lossless_forget),
                            subtitle = stringResource(R.string.lossless_forget_sub),
                            onClick = { confirmForget = true },
                            tint = SettingsRowDefaults.destructiveTint,
                            titleColor = SettingsRowDefaults.destructiveTint
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.lossless_section_streaming)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.PlayCircle,
                        title = stringResource(R.string.lossless_stream),
                        subtitle = stringResource(
                            if (configured) R.string.lossless_stream_sub else R.string.lossless_needs_server
                        ),
                        enabled = current?.streaming == true && configured,
                        onToggle = { on -> current?.let { save(it.copy(streaming = on)) } },
                        available = configured,
                        explanation = stringResource(R.string.lossless_stream_explanation)
                    )
                    if (configured && current?.streaming == true) {
                        SettingsToggleRow(
                            icon = Icons.Rounded.Wifi,
                            title = stringResource(R.string.lossless_unmetered),
                            subtitle = stringResource(R.string.lossless_unmetered_sub),
                            enabled = current.streamingUnmeteredOnly,
                            onToggle = { on -> save(current.copy(streamingUnmeteredOnly = on)) }
                        )
                        QualityChoice(
                            title = stringResource(R.string.lossless_stream_quality),
                            selected = current.streamingQuality,
                            onSelect = { save(current.copy(streamingQuality = it)) }
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.lossless_section_downloads)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.lossless_downloads),
                        subtitle = stringResource(
                            if (configured) R.string.lossless_downloads_sub else R.string.lossless_needs_server
                        ),
                        enabled = current?.downloads == true && configured,
                        onToggle = { on -> current?.let { save(it.copy(downloads = on)) } },
                        available = configured
                    )
                    if (configured && current?.downloads == true) {
                        SettingsToggleRow(
                            icon = Icons.Rounded.Star,
                            title = stringResource(R.string.lossless_default),
                            subtitle = stringResource(R.string.lossless_default_sub),
                            enabled = current.downloadByDefault,
                            onToggle = { on -> save(current.copy(downloadByDefault = on)) }
                        )
                        QualityChoice(
                            title = stringResource(R.string.lossless_download_quality),
                            selected = current.downloadQuality,
                            onSelect = { save(current.copy(downloadQuality = it)) }
                        )
                        SettingsToggleRow(
                            icon = Icons.Rounded.SwapHoriz,
                            title = stringResource(R.string.lossless_fallback),
                            subtitle = stringResource(
                                if (current.downloadFallback) R.string.lossless_fallback_on
                                else R.string.lossless_fallback_off
                            ),
                            enabled = current.downloadFallback,
                            onToggle = { on -> save(current.copy(downloadFallback = on)) }
                        )
                    }
                }
            }
        }

        item {
            SettingsFootnote(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.lossless_footnote)
            )
        }
    }

    if (showServerDialog) {
        LosslessServerDialog(
            initialUrl = current?.serverUrl.orEmpty(),
            initialSecret = current?.secret.orEmpty(),
            onDismiss = { showServerDialog = false },
            onSave = { url, secret ->
                showServerDialog = false
                val base = current ?: LosslessConfig()
                val firstTime = !base.isConfigured
                // A first server turns both uses on: setting one up is asking for them.
                val next = base.copy(
                    serverUrl = url,
                    secret = secret,
                    streaming = if (firstTime) true else base.streaming,
                    downloads = if (firstTime) true else base.downloads,
                )
                save(next)
                runTest(next)
            }
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            icon = { DialogIcon(Icons.Rounded.LinkOff, MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.lossless_forget_title)) },
            text = { Text(stringResource(R.string.lossless_forget_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    test = ServerTest.Idle
                    config = LosslessConfig()
                    scope.launch(Dispatchers.IO) { runCatching { LosslessSettings.forget(context) } }
                }) {
                    Text(stringResource(R.string.lossless_forget_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

/** The page's opening: what this is, and the pill it puts on songs. */
@Composable
private fun LosslessIntroCard(configured: Boolean, host: String?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.lossless_intro_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LosslessPill(compact = true)
                        LosslessPill(hiRes = true, compact = true)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (configured && host != null) stringResource(R.string.lossless_intro_ready, host)
                else stringResource(R.string.lossless_intro_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun QualityChoice(
    title: String,
    selected: LosslessQuality,
    onSelect: (LosslessQuality) -> Unit,
) {
    val options = listOf(LosslessQuality.HI_RES, LosslessQuality.CD)
    SettingsChoiceRow(
        icon = Icons.Rounded.HighQuality,
        title = title,
        subtitle = stringResource(
            if (selected == LosslessQuality.HI_RES) R.string.lossless_quality_hires_sub
            else R.string.lossless_quality_cd_sub
        ),
        labels = listOf(
            stringResource(R.string.lossless_quality_hires),
            stringResource(R.string.lossless_quality_cd)
        ),
        selectedIndex = options.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(options[it]) }
    )
}

@Composable
private fun DialogIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = 0.14f),
        contentColor = tint,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * The address (with its token) and the optional signing secret. Nothing is
 * written until Save, and Save is offered only for an http(s) address; the
 * page tests the connection as soon as it is saved.
 */
@Composable
private fun LosslessServerDialog(
    initialUrl: String,
    initialSecret: String,
    onDismiss: () -> Unit,
    onSave: (url: String, secret: String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var secret by remember { mutableStateOf(initialSecret) }
    var showSecret by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val valid = isServerUrl(url)

    run {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(32.dp),
            icon = { DialogIcon(Icons.Rounded.Dns, MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.lossless_server_dialog_title)) },
            text = {
                // Scrolls: two fields and a note overrun a landscape window.
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.lossless_server_dialog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            touched = true
                        },
                        label = { Text(stringResource(R.string.lossless_server_url)) },
                        placeholder = { Text("https://host/a/token/") },
                        singleLine = true,
                        isError = touched && url.isNotBlank() && !valid,
                        supportingText = if (touched && url.isNotBlank() && !valid) {
                            { Text(stringResource(R.string.lossless_server_url_invalid)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = { Text(stringResource(R.string.lossless_server_secret)) },
                        supportingText = { Text(stringResource(R.string.lossless_server_secret_hint)) },
                        singleLine = true,
                        visualTransformation = if (showSecret) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(
                                        if (showSecret) R.string.lossless_secret_hide else R.string.lossless_secret_show
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(url.trim(), secret.trim()) },
                    enabled = valid
                ) { Text(stringResource(R.string.lossless_server_save)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        )
    }
}

private fun isServerUrl(text: String): Boolean {
    val uri = runCatching { android.net.Uri.parse(text.trim()) }.getOrNull() ?: return false
    return (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()
}

/** The Advanced row's live value: off, or where songs come from and what for. */
@Composable
internal fun losslessSummary(config: LosslessConfig?): String {
    if (config == null) return ""
    val host = config.serverHost
    if (!config.isConfigured || host == null) return stringResource(R.string.lossless_summary_off)
    return when {
        config.streaming && config.downloads -> stringResource(R.string.lossless_summary_both, host)
        config.streaming -> stringResource(R.string.lossless_summary_streaming, host)
        config.downloads -> stringResource(R.string.lossless_summary_downloads, host)
        else -> stringResource(R.string.lossless_summary_idle, host)
    }
}
