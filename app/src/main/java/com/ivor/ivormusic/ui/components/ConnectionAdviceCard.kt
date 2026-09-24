package com.ivor.ivormusic.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ConnectionAdvice
import com.ivor.ivormusic.data.ConnectionRefusal
import com.ivor.ivormusic.data.NetworkKind

/**
 * What a player shows in place of its error when YouTube refused the
 * connection rather than the video: what to do on this kind of network, a way
 * straight to the controls that do it, and the promise that playback resumes
 * once the network changes (the ViewModel's NetworkChangeWatcher keeps it).
 *
 * Fills its parent and centres itself, like the error overlays it stands in
 * for. [compact] layout below 300dp of height is for the inline portrait
 * player, whose 16:9 box is about 200dp tall: the badge shrinks beside the
 * title and the retry promise, the least necessary line, goes. The column
 * scrolls regardless, so a large font scale overflows into a scroll rather
 * than clipping the actions away.
 *
 * Tertiary rather than error colour for the badge: this is a way forward, not
 * a dead end, and it must read differently from the generic failure card. Its
 * one Expressive moment is the slowly turning cookie while it waits, still
 * when the system's animations are off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionAdviceCard(
    advice: ConnectionAdvice,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 300.dp
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .padding(horizontal = if (compact) 12.dp else 24.dp, vertical = 8.dp)
                .widthIn(max = 440.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (compact) 16.dp else 24.dp,
                        vertical = if (compact) 14.dp else 22.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
                ) {
                    NetworkBadge(advice.network, if (compact) 40.dp else 56.dp)
                    Text(
                        text = stringResource(R.string.conn_refused_title),
                        style = if (compact) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = adviceText(advice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!compact) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.conn_refused_auto_retry),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                    modifier = Modifier.padding(top = if (compact) 0.dp else 4.dp),
                ) {
                    // Changing network is the fix, so it leads; Retry is for
                    // someone who already changed it or wants to wait it out.
                    Button(
                        onClick = { openInternetPanel(context) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).heightIn(min = if (compact) 40.dp else 48.dp),
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.conn_refused_change_network), maxLines = 1)
                    }
                    if (onRetry != null) {
                        FilledTonalButton(
                            onClick = onRetry,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f).heightIn(min = if (compact) 40.dp else 48.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_retry), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun adviceText(advice: ConnectionAdvice): String {
    val base = stringResource(
        when (advice.network) {
            NetworkKind.MOBILE -> R.string.conn_refused_mobile
            NetworkKind.WIFI -> R.string.conn_refused_wifi
            NetworkKind.VPN -> R.string.conn_refused_vpn
            NetworkKind.OTHER -> R.string.conn_refused_other
        }
    )
    // Only a 429 is known to lift on its own; a bot-check refusal of an
    // address tends to outlast a few minutes, so it is not promised there.
    return if (advice.refusal == ConnectionRefusal.RATE_LIMITED) {
        base + " " + stringResource(R.string.conn_refused_wait)
    } else {
        base
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NetworkBadge(network: NetworkKind, size: Dp) {
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    val spin = if (animate) {
        rememberInfiniteTransition(label = "connection-badge").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
            label = "connection-badge-rotation",
        )
    } else {
        null
    }
    val icon: ImageVector = when (network) {
        NetworkKind.MOBILE -> Icons.Rounded.SignalCellularAlt
        NetworkKind.WIFI -> Icons.Rounded.Wifi
        NetworkKind.VPN -> Icons.Rounded.VpnKey
        NetworkKind.OTHER -> Icons.Rounded.Public
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                // Reading the animated value in the draw phase keeps the spin
                // from recomposing the card every frame.
                .graphicsLayer { rotationZ = spin?.value ?: 0f }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer)
        )
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/**
 * Android's internet panel: Wi-Fi, mobile data and, on most devices,
 * airplane mode in one sheet over the app. Apps cannot toggle airplane mode
 * themselves, so this is as close as a button can get. Falls back to the
 * wireless settings page where an OEM strips the panel.
 */
private fun openInternetPanel(context: Context) {
    val panel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(panel)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
