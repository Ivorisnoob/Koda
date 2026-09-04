package com.ivor.ivormusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.scrobble.LastFmClient
import com.ivor.ivormusic.data.scrobble.ScrobbleRepository
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.launch

/**
 * Settings subpage for Last.fm and ListenBrainz scrobbling.
 *
 * Implements the User API model: users enter their own Last.fm API Key & Secret
 * and ListenBrainz personal token. Sensitive credentials are encrypted in KeyStore-backed
 * storage and never exposed in plaintext backups.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScrobbleSettingsPage(
    lastFmEnabled: Boolean,
    onLastFmEnabledToggle: (Boolean) -> Unit,
    listenBrainzEnabled: Boolean,
    onListenBrainzEnabledToggle: (Boolean) -> Unit,
    listenBrainzCustomUrl: String,
    onListenBrainzCustomUrlChange: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val repository = remember { ScrobbleRepository.getInstance(context) }
    val credentialsStore = repository.credentialsStore

    val lastFmUser by credentialsStore.lastFmUsername.collectAsState()
    val listenBrainzUser by credentialsStore.listenBrainzUsername.collectAsState()
    val pendingQueueCount by repository.pendingQueueCount.collectAsState()

    // Last.fm local form states
    var lastFmApiKey by remember { mutableStateOf(credentialsStore.getLastFmApiKey().orEmpty()) }
    var lastFmApiSecret by remember { mutableStateOf(credentialsStore.getLastFmApiSecret().orEmpty()) }
    var lastFmPendingToken by remember { mutableStateOf<String?>(null) }
    var lastFmIsAuthorizing by remember { mutableStateOf(false) }
    var lastFmStatusMessage by remember { mutableStateOf<String?>(null) }
    var lastFmShowPasswordLogin by remember { mutableStateOf(false) }
    var lastFmUsernameInput by remember { mutableStateOf("") }
    var lastFmPasswordInput by remember { mutableStateOf("") }

    // ListenBrainz local form states
    var lbTokenInput by remember { mutableStateOf(credentialsStore.getListenBrainzToken().orEmpty()) }
    var lbIsVerifying by remember { mutableStateOf(false) }
    var lbStatusMessage by remember { mutableStateOf<String?>(null) }
    var lbShowCustomEndpoint by remember { mutableStateOf(false) }

    SettingsDetailScaffold(
        title = stringResource(R.string.scrobble_page_title),
        onBack = onBack
    ) {
        // Explanatory note
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.scrobble_page_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ==================== LAST.FM SECTION ====================
        item {
            SettingsSection(title = stringResource(R.string.scrobble_lastfm)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.scrobble_lastfm),
                        subtitle = when {
                            !lastFmEnabled -> stringResource(R.string.scrobble_lastfm_sub)
                            !lastFmUser.isNullOrBlank() -> stringResource(R.string.scrobble_connected_as, lastFmUser.orEmpty())
                            else -> stringResource(R.string.scrobble_not_connected)
                        },
                        enabled = lastFmEnabled,
                        onToggle = onLastFmEnabledToggle
                    )

                    AnimatedVisibility(
                        visible = lastFmEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (!lastFmUser.isNullOrBlank()) {
                                // Connected state
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.scrobble_connected_as, lastFmUser.orEmpty()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Active session key stored securely",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                haptics.subtle()
                                                credentialsStore.clearLastFmSession()
                                                lastFmStatusMessage = null
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text(stringResource(R.string.scrobble_disconnect))
                                        }
                                    }
                                }
                            } else {
                                // Not connected: User API Credentials form
                                Text(
                                    text = stringResource(R.string.scrobble_lastfm_api_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = lastFmApiKey,
                                    onValueChange = {
                                        lastFmApiKey = it.trim()
                                        credentialsStore.saveLastFmApiCredentials(lastFmApiKey, lastFmApiSecret)
                                    },
                                    label = { Text(stringResource(R.string.scrobble_api_key)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = lastFmApiSecret,
                                    onValueChange = {
                                        lastFmApiSecret = it.trim()
                                        credentialsStore.saveLastFmApiCredentials(lastFmApiKey, lastFmApiSecret)
                                    },
                                    label = { Text(stringResource(R.string.scrobble_api_secret)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (lastFmStatusMessage != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = lastFmStatusMessage.orEmpty(),
                                        color = if (lastFmStatusMessage?.startsWith("Error") == true) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                // Web Authorization Button Flow
                                if (lastFmPendingToken == null) {
                                    Button(
                                        onClick = {
                                            if (lastFmApiKey.isBlank() || lastFmApiSecret.isBlank()) {
                                                lastFmStatusMessage = "Error: Please enter both API Key and Shared Secret"
                                                return@Button
                                            }
                                            haptics.subtle()
                                            lastFmIsAuthorizing = true
                                            lastFmStatusMessage = null
                                            scope.launch {
                                                val result = repository.lastFmClient.fetchRequestToken(
                                                    lastFmApiKey,
                                                    lastFmApiSecret
                                                )
                                                lastFmIsAuthorizing = false
                                                result.onSuccess { token ->
                                                    lastFmPendingToken = token
                                                    val authUrl = LastFmClient.getAuthorizationUrl(lastFmApiKey, token)
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                                    context.startActivity(browserIntent)
                                                }.onFailure { err ->
                                                    lastFmStatusMessage = "Error: ${err.message}"
                                                }
                                            }
                                        },
                                        enabled = !lastFmIsAuthorizing && lastFmApiKey.isNotBlank() && lastFmApiSecret.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        if (lastFmIsAuthorizing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.OpenInBrowser,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(stringResource(R.string.scrobble_login_browser))
                                    }
                                } else {
                                    // Authorizing banner with Complete Login
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = stringResource(R.string.scrobble_authorizing_banner),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        haptics.subtle()
                                                        lastFmIsAuthorizing = true
                                                        scope.launch {
                                                            val result = repository.lastFmClient.fetchSession(
                                                                lastFmPendingToken.orEmpty(),
                                                                lastFmApiKey,
                                                                lastFmApiSecret
                                                            )
                                                            lastFmIsAuthorizing = false
                                                            result.onSuccess { (sessionKey, username) ->
                                                                credentialsStore.saveLastFmSession(sessionKey, username)
                                                                lastFmPendingToken = null
                                                                lastFmStatusMessage = null
                                                                repository.triggerQueueDrain()
                                                            }.onFailure { err ->
                                                                lastFmStatusMessage = "Error: ${err.message}"
                                                            }
                                                        }
                                                    },
                                                    enabled = !lastFmIsAuthorizing,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(stringResource(R.string.scrobble_complete_login))
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        lastFmPendingToken = null
                                                        lastFmIsAuthorizing = false
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(stringResource(R.string.action_cancel))
                                                }
                                            }
                                        }
                                    }
                                }

                                // Alternative password login
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = { lastFmShowPasswordLogin = !lastFmShowPasswordLogin }
                                ) {
                                    Text(
                                        text = if (lastFmShowPasswordLogin) "Hide password login" else stringResource(R.string.scrobble_login_password),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                AnimatedVisibility(visible = lastFmShowPasswordLogin) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = lastFmUsernameInput,
                                            onValueChange = { lastFmUsernameInput = it },
                                            label = { Text(stringResource(R.string.scrobble_username)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = lastFmPasswordInput,
                                            onValueChange = { lastFmPasswordInput = it },
                                            label = { Text(stringResource(R.string.scrobble_password)) },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.scrobble_password_privacy_note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                if (lastFmUsernameInput.isBlank() || lastFmPasswordInput.isBlank()) {
                                                    lastFmStatusMessage = "Error: Username and password are required"
                                                    return@Button
                                                }
                                                haptics.subtle()
                                                lastFmIsAuthorizing = true
                                                scope.launch {
                                                    val result = repository.lastFmClient.fetchMobileSession(
                                                        lastFmUsernameInput,
                                                        lastFmPasswordInput,
                                                        lastFmApiKey,
                                                        lastFmApiSecret
                                                    )
                                                    lastFmIsAuthorizing = false
                                                    result.onSuccess { (sessionKey, username) ->
                                                        credentialsStore.saveLastFmSession(sessionKey, username)
                                                        lastFmPasswordInput = ""
                                                        lastFmStatusMessage = null
                                                        repository.triggerQueueDrain()
                                                    }.onFailure { err ->
                                                        lastFmStatusMessage = "Error: ${err.message}"
                                                    }
                                                }
                                            },
                                            enabled = !lastFmIsAuthorizing,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(stringResource(R.string.scrobble_login_btn))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ==================== LISTENBRAINZ SECTION ====================
        item {
            SettingsSection(title = stringResource(R.string.scrobble_listenbrainz)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.scrobble_listenbrainz),
                        subtitle = when {
                            !listenBrainzEnabled -> stringResource(R.string.scrobble_listenbrainz_sub)
                            !listenBrainzUser.isNullOrBlank() -> stringResource(R.string.scrobble_connected_as, listenBrainzUser.orEmpty())
                            else -> stringResource(R.string.scrobble_not_connected)
                        },
                        enabled = listenBrainzEnabled,
                        onToggle = onListenBrainzEnabledToggle
                    )

                    AnimatedVisibility(
                        visible = listenBrainzEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (!listenBrainzUser.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.scrobble_connected_as, listenBrainzUser.orEmpty()),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Token verified and active",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                haptics.subtle()
                                                credentialsStore.clearListenBrainz()
                                                lbStatusMessage = null
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text(stringResource(R.string.scrobble_disconnect))
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.scrobble_token_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = lbTokenInput,
                                    onValueChange = { lbTokenInput = it.trim() },
                                    label = { Text(stringResource(R.string.scrobble_token_label)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (lbStatusMessage != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = lbStatusMessage.orEmpty(),
                                        color = if (lbStatusMessage?.startsWith("Error") == true) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (lbTokenInput.isBlank()) {
                                            lbStatusMessage = "Error: Token cannot be empty"
                                            return@Button
                                        }
                                        haptics.subtle()
                                        lbIsVerifying = true
                                        lbStatusMessage = null
                                        scope.launch {
                                            val result = repository.listenBrainzClient.validateToken(
                                                lbTokenInput,
                                                listenBrainzCustomUrl
                                            )
                                            lbIsVerifying = false
                                            result.onSuccess { userName ->
                                                credentialsStore.saveListenBrainzToken(lbTokenInput, userName)
                                                lbStatusMessage = null
                                                repository.triggerQueueDrain()
                                            }.onFailure { err ->
                                                lbStatusMessage = "Error: ${err.message}"
                                            }
                                        }
                                    },
                                    enabled = !lbIsVerifying && lbTokenInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    if (lbIsVerifying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(stringResource(R.string.scrobble_verify_token))
                                }
                            }

                            // Custom API URL
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = { lbShowCustomEndpoint = !lbShowCustomEndpoint }
                            ) {
                                Text(
                                    text = stringResource(R.string.scrobble_custom_endpoint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            AnimatedVisibility(visible = lbShowCustomEndpoint) {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    Text(
                                        text = stringResource(R.string.scrobble_custom_endpoint_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = listenBrainzCustomUrl,
                                        onValueChange = onListenBrainzCustomUrlChange,
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ==================== OFFLINE QUEUE SECTION ====================
        item {
            SettingsSection(title = stringResource(R.string.scrobble_queue_title)) {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.scrobble_queue_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (pendingQueueCount == 0) {
                                    stringResource(R.string.scrobble_queue_empty)
                                } else {
                                    stringResource(R.string.scrobble_queue_count, pendingQueueCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (pendingQueueCount > 0) {
                            IconButton(
                                onClick = {
                                    haptics.subtle()
                                    repository.triggerQueueDrain()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.scrobble_sync_now),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.subtle()
                                    scope.launch {
                                        repository.queueRepository.clear()
                                        repository.refreshPendingCount()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = stringResource(R.string.scrobble_clear_queue),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
