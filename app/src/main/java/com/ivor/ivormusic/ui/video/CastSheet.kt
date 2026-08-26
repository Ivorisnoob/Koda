package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.ui.player.PlayerViewModel

/**
 * The cast device sheet: what is on the network, what is connected, and the
 * one action each state offers.
 *
 * This replaces Google's MediaRouteChooserDialog on purpose. That dialog needs
 * the hosting activity to be a FragmentActivity (MainActivity is a
 * ComponentActivity for Compose) and renders in old Material 2; this sheet
 * reads the same discovered routes out of [VideoCastManager] and stays inside
 * Koda's M3 Expressive language like every other sheet in the app.
 *
 * Discovery runs only while the sheet is open - see [VideoCastManager.startDiscovery].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CastSheet(
    viewModel: VideoPlayerViewModel,
    onDismiss: () -> Unit
) {
    val receivers by viewModel.castReceivers.collectAsState()
    val deviceName by viewModel.castDeviceName.collectAsState()
    val isConnecting by viewModel.isCastConnecting.collectAsState()

    CastSheetContent(
        receivers = receivers,
        deviceName = deviceName,
        isConnecting = isConnecting,
        unavailableMessage = null,
        onConnect = viewModel::startCast,
        onDisconnect = viewModel::stopCasting,
        onDismiss = onDismiss
    )
}

/** The same device surface for the service-owned music playback pipeline. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicCastSheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val receivers by viewModel.castReceivers.collectAsState()
    val deviceName by viewModel.castDeviceName.collectAsState()
    val isConnecting by viewModel.isCastConnecting.collectAsState()
    val unavailableMessage by viewModel.castUnavailableMessage.collectAsState()

    CastSheetContent(
        receivers = receivers,
        deviceName = deviceName,
        isConnecting = isConnecting,
        unavailableMessage = unavailableMessage,
        onConnect = viewModel::startCast,
        onDisconnect = viewModel::stopCasting,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CastSheetContent(
    receivers: List<CastRoute>,
    deviceName: String?,
    isConnecting: Boolean,
    unavailableMessage: String?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.cast_to),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // The current connection leads the list when there is one, with
            // the only action a connected row has: leaving it.
            val connected = deviceName
            if (connected != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CastConnected,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = connected,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.cast_connected),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        IconButton(onClick = {
                            onDisconnect()
                            onDismiss()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cast_disconnect))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (isConnecting && connected == null) {
                ConnectingRow()
            }

            if (unavailableMessage != null) {
                Text(
                    text = unavailableMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            receivers.forEach { route ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onConnect(route.id)
                        }
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                ) {
                    Icon(
                        Icons.Rounded.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = route.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (receivers.isEmpty() && !isConnecting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 20.dp)
                ) {
                    Icon(
                        Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.cast_looking_for_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.cast_connecting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
