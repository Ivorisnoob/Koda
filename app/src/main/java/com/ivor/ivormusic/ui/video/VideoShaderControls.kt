package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraRoll
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.HdrStrong
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.VideoShaderCustomization
import com.ivor.ivormusic.data.VideoShaderPreset
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sharpen, brightness, contrast and saturation: the sliders that tune every
 * built-in look, shared by the playback panel and the Video shaders page.
 *
 * A value follows the thumb through [onPreview] and is stored once, through
 * [onChange], when the finger lifts - the rule the speed slider follows, and
 * for the same reason: a picture is judged by watching, and a preference write
 * per frame of a drag is forty writes for one decision.
 *
 * A custom shader is the whole picture and reads none of these, so under it
 * they are disabled and say why. Sharpen is the same under the looks that
 * rebuild the picture ([VideoShaderPreset.sharpens]).
 */
@Composable
internal fun ShaderPictureSliders(
    settings: VideoShaderCustomization,
    onPreview: (VideoShaderCustomization) -> Unit,
    onChange: (VideoShaderCustomization) -> Unit,
    modifier: Modifier = Modifier
) {
    // What the thumb is on while a drag is in flight; the stored value otherwise.
    var dragged by remember { mutableStateOf<VideoShaderCustomization?>(null) }
    val shown = dragged ?: settings
    fun preview(next: VideoShaderCustomization) {
        dragged = next
        onPreview(next)
    }
    val finish = {
        dragged?.let(onChange)
        dragged = null
    }

    val custom = shown.activePreset == VideoShaderPreset.CUSTOM
    val unused = stringResource(R.string.vs_not_used)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PictureSlider(
            label = stringResource(R.string.vs_sharpen),
            valueText = when {
                !shown.activePreset.sharpens -> unused
                shown.sharpness == VideoShaderCustomization.SHARPNESS_OFF -> stringResource(R.string.vs_off)
                else -> percent(shown.sharpness)
            },
            value = shown.sharpness,
            range = VideoShaderCustomization.SHARPNESS_RANGE,
            neutral = VideoShaderCustomization.SHARPNESS_OFF,
            enabled = shown.activePreset.sharpens,
            onValue = { preview(shown.copy(sharpness = it)) },
            onFinished = finish
        )
        PictureSlider(
            label = stringResource(R.string.vs_brightness),
            valueText = if (custom) unused else signedPercent(shown.brightness),
            value = shown.brightness,
            range = VideoShaderCustomization.BRIGHTNESS_RANGE,
            neutral = VideoShaderCustomization.NEUTRAL_BRIGHTNESS,
            enabled = !custom,
            onValue = { preview(shown.copy(brightness = it)) },
            onFinished = finish
        )
        PictureSlider(
            label = stringResource(R.string.vs_contrast),
            valueText = if (custom) unused else percent(shown.contrast),
            value = shown.contrast,
            range = VideoShaderCustomization.CONTRAST_RANGE,
            neutral = VideoShaderCustomization.NEUTRAL_CONTRAST,
            enabled = !custom,
            onValue = { preview(shown.copy(contrast = it)) },
            onFinished = finish
        )
        PictureSlider(
            label = stringResource(R.string.vs_saturation),
            valueText = if (custom) unused else percent(shown.saturation),
            value = shown.saturation,
            range = VideoShaderCustomization.SATURATION_RANGE,
            neutral = VideoShaderCustomization.NEUTRAL_SATURATION,
            enabled = !custom,
            onValue = { preview(shown.copy(saturation = it)) },
            onFinished = finish
        )
    }
}

/**
 * One picture value: its name and reading over a slider that detents, with a
 * tick, on [neutral] - the one value people want to land on exactly.
 */
@Composable
private fun PictureSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    neutral: Float,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    onFinished: () -> Unit
) {
    val haptics = rememberKodaHaptics()
    val detent = (range.endInclusive - range.start) * NEUTRAL_DETENT
    val changed = enabled && value != neutral
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal,
                // The accent marks a value that has been moved off neutral.
                color = if (changed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1
            )
        }
        Slider(
            value = value,
            onValueChange = { raw ->
                // Whole percents, so the reading is the value, and neutral is
                // exactly neutral rather than a hair off it.
                val next = if (abs(raw - neutral) <= detent) neutral else (raw * 100f).roundToInt() / 100f
                if (next != value) {
                    if (next == neutral) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onValue(next)
                }
            },
            onValueChangeFinished = onFinished,
            valueRange = range,
            enabled = enabled
        )
    }
}

/** The icon a preset is found by, in the playback panel's grid and on the Video shaders page. */
internal val VideoShaderPreset.icon: ImageVector
    get() = when (this) {
        VideoShaderPreset.NONE -> Icons.Rounded.AutoFixOff
        VideoShaderPreset.VIVID -> Icons.Rounded.Palette
        VideoShaderPreset.CLARITY -> Icons.Rounded.HdrStrong
        VideoShaderPreset.FILM -> Icons.Rounded.Movie
        VideoShaderPreset.NOIR -> Icons.Rounded.FilterBAndW
        VideoShaderPreset.ANIME -> Icons.Rounded.Brush
        VideoShaderPreset.NEON -> Icons.Rounded.Bolt
        VideoShaderPreset.DREAM -> Icons.Rounded.AutoAwesome
        VideoShaderPreset.VINTAGE -> Icons.Rounded.CameraRoll
        VideoShaderPreset.VHS -> Icons.Rounded.Videocam
        VideoShaderPreset.CRT -> Icons.Rounded.Tv
        VideoShaderPreset.PIXEL -> Icons.Rounded.GridOn
        VideoShaderPreset.COMIC -> Icons.Rounded.ChatBubble
        VideoShaderPreset.SKETCH -> Icons.Rounded.Draw
        VideoShaderPreset.NIGHT_VISION -> Icons.Rounded.Visibility
        VideoShaderPreset.CUSTOM -> Icons.Rounded.Code
    }

private fun percent(value: Float): String = "${(value * 100f).roundToInt()}%"

private fun signedPercent(value: Float): String {
    val whole = (value * 100f).roundToInt()
    return if (whole > 0) "+$whole%" else "$whole%"
}

/** How close to neutral, as a share of the slider's travel, the thumb snaps onto it. */
private const val NEUTRAL_DETENT = 0.03f
