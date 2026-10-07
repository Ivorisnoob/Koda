package com.ivor.ivormusic.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.rememberKodaHaptics

/**
 * What one quality card says under its name. [size] is null while unknown;
 * [sizeLoading] then decides between a spinner and nothing.
 */
internal data class MusicQualityCardInfo(
    val detail: String,
    val size: String? = null,
    val sizeLoading: Boolean = false,
    val available: Boolean = true,
)

/**
 * The two music download qualities as a pair of cards, shared by the song and
 * playlist download sheets so the choice looks and behaves the same in both.
 *
 * Two, because that is what YouTube serves as AAC (see
 * `YouTubeRepository.getDownloadAudioFormats`); the size is on the card
 * because it is the only thing that tells the two apart before listening.
 */
@Composable
internal fun MusicQualityPicker(
    selected: String,
    onSelect: (String) -> Unit,
    info: (String) -> MusicQualityCardInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MusicQualityCard(
            icon = Icons.Rounded.GraphicEq,
            title = stringResource(R.string.sd_quality_high),
            info = info(ThemePreferences.DOWNLOAD_MUSIC_QUALITY_HIGH),
            selected = selected == ThemePreferences.DOWNLOAD_MUSIC_QUALITY_HIGH,
            onClick = { onSelect(ThemePreferences.DOWNLOAD_MUSIC_QUALITY_HIGH) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        MusicQualityCard(
            icon = Icons.Rounded.DataSaverOn,
            title = stringResource(R.string.sd_quality_saver),
            info = info(ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER),
            selected = selected == ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER,
            onClick = { onSelect(ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER) },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MusicQualityCard(
    icon: ImageVector,
    title: String,
    info: MusicQualityCardInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberKodaHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "musicQualityScale"
    )
    // The chosen card tightens its corners, the way a selected M3 Expressive
    // button does, so the pair reads as a choice rather than two panels.
    val corner by animateDpAsState(
        targetValue = if (selected) 18.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "musicQualityCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "musicQualityContainer"
    )
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    val muted = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(corner)

    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (info.available) 1f else 0.45f)
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = info.available,
                role = Role.RadioButton,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (!selected) haptics.tick()
                    onClick()
                }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selected,
                        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (info.available) info.detail
                    else stringResource(R.string.sd_quality_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            // A fixed slot, so a size arriving does not make the cards jump.
            Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    !info.available -> Unit
                    info.size != null -> Text(
                        text = info.size,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    info.sizeLoading -> LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = content
                    )
                }
            }
        }
    }
}

/** One on/off extra in a download sheet: an icon, two lines and a switch on a tonal tile. */
@Composable
internal fun DownloadOptionSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = true,
) {
    val haptics = rememberKodaHaptics()
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (tonal) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            // The tile is the control; the switch only shows its state.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** "AAC, 128 kbps" for a stream whose bitrate is known, in bits per second. */
@Composable
internal fun musicBitrateLabel(bitsPerSecond: Int): String =
    stringResource(R.string.sd_bitrate, ((bitsPerSecond + 500) / 1000))

/** Nominal bitrates, for when a size has to be estimated before any stream is resolved. */
internal fun nominalMusicBitrate(quality: String): Int =
    if (quality == ThemePreferences.DOWNLOAD_MUSIC_QUALITY_SAVER) 48_000 else 128_000
