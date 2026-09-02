package com.ivor.ivormusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.UI_SCALE_DEFAULT
import com.ivor.ivormusic.data.UI_SCALE_MAX
import com.ivor.ivormusic.data.UI_SCALE_MIN
import com.ivor.ivormusic.data.UI_SCALE_STEPS
import com.ivor.ivormusic.data.nearestUiScaleStep
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.roundToInt

/**
 * The interface scale page.
 *
 * The setting is one number, so the page is really about making that number
 * legible before it is committed. Two things do that work.
 *
 * The **preview** is the reason this is its own page rather than a slider on
 * the Appearance list. Scaling is a density override, so a preview costs
 * nothing but a nested override - the mock below is laid out by the same
 * engine at the same density the whole app is about to use, which makes it an
 * actual preview rather than an illustration of one.
 *
 * The **commit on release** matters more than it looks. Applying every
 * intermediate value would rescale the page under the finger dragging it,
 * moving the slider away from the touch and making the control fight the user.
 * So the preview follows the drag and the app follows the release.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DisplaySizeSettingsPage(
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val haptics = rememberKodaHaptics()

    // The value the slider and preview follow. Seeded from the committed
    // scale and resynced whenever that changes from elsewhere - a restore, or
    // the preset buttons below, which commit immediately.
    var pending by remember { mutableFloatStateOf(uiScale) }
    LaunchedEffect(uiScale) { pending = uiScale }

    SettingsDetailScaffold(
        title = stringResource(R.string.sp_display_size),
        onBack = onBack
    ) {
        item {
            SettingsSection(title = stringResource(R.string.sp_display_size_preview)) {
                SettingsCard {
                    ScalePreview(pending = pending, committed = uiScale)
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SettingsSection(title = stringResource(R.string.sp_display_size_scale)) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        ScaleReadout(
                            pending = pending,
                            isDefault = uiScale == UI_SCALE_DEFAULT,
                            onReset = {
                                haptics.confirm()
                                pending = UI_SCALE_DEFAULT
                                onUiScaleChange(UI_SCALE_DEFAULT)
                            }
                        )

                        Spacer(Modifier.height(4.dp))

                        Slider(
                            value = pending,
                            onValueChange = { raw ->
                                val snapped = nearestUiScaleStep(raw)
                                if (snapped != pending) {
                                    pending = snapped
                                    haptics.tick()
                                }
                            },
                            // Only the release reaches the app, so the page
                            // does not resize while it is being dragged.
                            onValueChangeFinished = { onUiScaleChange(pending) },
                            valueRange = UI_SCALE_MIN..UI_SCALE_MAX,
                            steps = UI_SCALE_STEPS.size - 2,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ScaleTick(stringResource(R.string.sp_display_size_compact))
                            ScaleTick(stringResource(R.string.sp_display_size_default))
                            ScaleTick(stringResource(R.string.sp_display_size_large))
                        }

                        Spacer(Modifier.height(16.dp))

                        // The slider is the fine control; these are the three
                        // answers most people actually want, one tap away.
                        val presets = listOf(UI_SCALE_MIN, UI_SCALE_DEFAULT, UI_SCALE_MAX)
                        val presetLabels = listOf(
                            stringResource(R.string.sp_display_size_compact),
                            stringResource(R.string.sp_display_size_default),
                            stringResource(R.string.sp_display_size_large)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween
                            )
                        ) {
                            presets.forEachIndexed { index, preset ->
                                ToggleButton(
                                    checked = pending == preset,
                                    onCheckedChange = {
                                        haptics.confirm()
                                        pending = preset
                                        onUiScaleChange(preset)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        presets.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.colors(
                                        containerColor =
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text(presetLabels[index])
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SettingsSection(title = stringResource(R.string.sp_display_size_about)) {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            text = stringResource(R.string.sp_display_size_note_system),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.sp_display_size_note_excluded),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The percentage, and the reset that only exists when there is one to undo. */
@Composable
private fun ScaleReadout(pending: Float, isDefault: Boolean, onReset: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatSize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        // Animated so a preset tap reads as a move along the same scale
        // rather than an unrelated number replacing another.
        val shown by animateFloatAsState(
            targetValue = pending,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "uiScaleReadout"
        )
        Text(
            text = "${(shown * 100).roundToInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )

        AnimatedVisibility(
            visible = !isDefault,
            enter = fadeIn() + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            TextButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.sp_display_size_reset))
            }
        }
    }
}

@Composable
private fun ScaleTick(label: String) {
    Text(
        text = label,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * A miniature of the app, laid out at [pending] rather than the scale the rest
 * of the screen is using.
 *
 * The frame is deliberately outside the override so it stays put while its
 * contents grow and shrink inside it - that contrast is the whole point, and a
 * frame that resized with the content would show no change at all.
 *
 * The density is multiplied by `pending / committed` rather than by `pending`,
 * because [LocalDensity] here already carries the committed scale: this page
 * is drawn by the same override it is configuring.
 */
@Composable
private fun ScalePreview(pending: Float, committed: Float) {
    val outer = LocalDensity.current
    val previewDensity = remember(outer, pending, committed) {
        Density(
            density = outer.density * (pending / committed),
            fontScale = outer.fontScale
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .height(200.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.clipToBounds()) {
            CompositionLocalProvider(LocalDensity provides previewDensity) {
                MockScreen()
            }
        }
    }
}

/**
 * The mock itself: a header, two rows and a mini player, chosen because they
 * are the three things whose size people are actually complaining about.
 * Static by design - it is a ruler, not a demo.
 */
@Composable
private fun MockScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_library),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        repeat(2) {
            MockSongRow()
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))

        // The mini player, the surface most affected by a scale change
        // because it is the one that is always on screen.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MockTextLine(widthFraction = 0.55f, height = 8.dp)
                    Spacer(Modifier.height(5.dp))
                    MockTextLine(widthFraction = 0.32f, height = 6.dp, dim = true)
                }
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MockSongRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            MockTextLine(widthFraction = 0.62f, height = 8.dp)
            Spacer(Modifier.height(5.dp))
            MockTextLine(widthFraction = 0.38f, height = 6.dp, dim = true)
        }
    }
}

/**
 * A stand-in for a line of text. Drawn as a bar rather than real words so the
 * preview reads at a glance and needs no translation of its own.
 */
@Composable
private fun MockTextLine(widthFraction: Float, height: Dp, dim: Boolean = false) {
    val color: Color = if (dim) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}
