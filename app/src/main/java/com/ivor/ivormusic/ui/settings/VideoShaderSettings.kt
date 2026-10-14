package com.ivor.ivormusic.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.VideoShaderCustomization
import com.ivor.ivormusic.data.VideoShaderPreset
import com.ivor.ivormusic.service.VideoShaderValidator
import com.ivor.ivormusic.ui.video.ShaderPictureSliders
import com.ivor.ivormusic.ui.video.icon
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Video shaders: the switch that builds players with the shader stage, and,
 * once it is on, the preset, the picture sliders and the custom shader.
 *
 * Read and written through the page's own `ThemePreferences`, as the video
 * player's page is. The player follows the stored value, so a preset chosen
 * here reaches a video still open in the mini player; only the switch itself
 * waits for the next video, which is what the note under it says.
 */
@Composable
internal fun VideoShaderSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberKodaHaptics()
    val prefs = remember(context) { ThemePreferences(context) }
    val shaders by prefs.videoShaders.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.sp_video_shaders),
        onBack = onBack
    ) {
        item {
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.sp_video_shaders),
                    subtitle = stringResource(
                        if (shaders.masterEnabled) {
                            R.string.sp_video_shaders_sub_on
                        } else {
                            R.string.sp_video_shaders_sub_off
                        }
                    ),
                    enabled = shaders.masterEnabled,
                    onToggle = { prefs.setVideoShaders(shaders.copy(masterEnabled = it)) },
                    explanation = stringResource(R.string.si_video_shaders)
                )
            }
            SettingsFootnote(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.sp_video_shaders_note)
            )
        }

        if (shaders.masterEnabled) {
            item {
                SettingsSection(title = stringResource(R.string.sp_video_shaders_presets)) {
                    // Two to a row, a row as tall as its taller tile, so a
                    // description that wraps at a large font is never cut.
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        VideoShaderPreset.entries.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                pair.forEach { preset ->
                                    val selected = shaders.activePreset == preset
                                    PresetTile(
                                        preset = preset,
                                        selected = selected,
                                        onClick = {
                                            if (!selected) {
                                                haptics.confirm()
                                                prefs.setVideoShaders(shaders.copy(activePreset = preset))
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.sp_video_shaders_picture)) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 6.dp)) {
                            // Nothing to draw a drag on here, so only the
                            // release is stored and the player picks it up.
                            ShaderPictureSliders(
                                settings = shaders,
                                onPreview = {},
                                onChange = prefs::setVideoShaders
                            )
                            TextButton(
                                onClick = {
                                    haptics.confirm()
                                    prefs.setVideoShaders(shaders.withNeutralPicture())
                                },
                                enabled = !shaders.isPictureNeutral,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.vs_reset_picture),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.sp_video_shaders_custom)) {
                    CustomShaderCard(shaders = shaders, onSave = prefs::setVideoShaders)
                }
            }
        }
    }
}

/**
 * A preset on the Video shaders page: its icon, its name and what it does.
 *
 * The chosen one fills with the accent and squares off, the selection the
 * player's tiles use, and the badge under its icon blooms from a circle into a
 * cookie. The badge is morphed in the draw phase, so choosing a preset redraws
 * two tiles and recomposes nothing around them.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PresetTile(
    preset: VideoShaderPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "presetTilePress"
    )
    val corner by animateDpAsState(
        targetValue = if (selected) 18.dp else 28.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "presetTileCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "presetTileContainer"
    )
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val badge by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "presetTileBadge"
    )
    val glyph by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "presetTileGlyph"
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val bloom by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "presetTileBloom"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = 40f * bloom
                            // The spring overshoots; a morph is only defined from 0 to 1.
                            shape = BadgeMorph(morph, bloom.coerceIn(0f, 1f))
                            clip = true
                        }
                        .background(badge)
                )
                Icon(
                    imageVector = preset.icon,
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(preset.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(preset.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    LocalContentColor.current.copy(alpha = 0.82f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** A [Morph] between two unit-square Material shapes, stretched to the badge. */
private class BadgeMorph(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** What Apply found: the shader built, or the driver's reason it did not. */
private sealed interface ShaderCheck {
    data object Built : ShaderCheck
    data class Failed(val log: String) : ShaderCheck
}

/**
 * The custom shader's editor.
 *
 * Apply compiles the text off-screen first ([VideoShaderValidator]) and says
 * what the driver said, because a shader that fails inside the player only
 * shows as a video that did not change. The text is stored either way, so
 * leaving the page never loses work; it becomes the preset only once it
 * builds. An example fills the editor and nothing else, so it cannot replace
 * a stored shader until Apply does.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomShaderCard(
    shaders: VideoShaderCustomization,
    onSave: (VideoShaderCustomization) -> Unit
) {
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val stored by rememberUpdatedState(shaders)
    var draft by rememberSaveable(shaders.customShaderSource) {
        mutableStateOf(shaders.customShaderSource)
    }
    var checking by remember { mutableStateOf(false) }
    // Kept apart from whether it is showing, so the line fading out after
    // an edit still reads what it read.
    var check by remember { mutableStateOf<ShaderCheck>(ShaderCheck.Built) }
    var checkShown by remember { mutableStateOf(false) }
    val canApply = draft.isNotBlank() && !checking &&
        (draft != shaders.customShaderSource || shaders.activePreset != VideoShaderPreset.CUSTOM)

    SettingsCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.sp_video_shaders_custom_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.sp_video_shaders_custom_template),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                // Wrapping rather than scrolling sideways, so no example is off the edge.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoShaderCustomization.STARTER_TEMPLATES.forEach { template ->
                        AssistChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                draft = template.source
                                checkShown = false
                            },
                            label = {
                                Text(
                                    text = stringResource(template.titleRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    checkShown = false
                },
                label = { Text(stringResource(R.string.sp_video_shaders_custom_field)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                // Code: a keyboard that capitalises or corrects it breaks it.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                ),
                shape = RoundedCornerShape(20.dp)
            )

            Text(
                text = stringResource(R.string.sp_video_shaders_custom_guide),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = checkShown) {
                when (val result = check) {
                    is ShaderCheck.Failed -> Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.sp_video_shaders_custom_error),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (result.log.isNotBlank()) {
                                Text(
                                    text = result.log,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                        }
                    }
                    ShaderCheck.Built -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.sp_video_shaders_custom_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val source = draft
                    checking = true
                    scope.launch {
                        val problem = withContext(Dispatchers.Default) {
                            VideoShaderValidator.check(source)
                        }
                        checking = false
                        check = if (problem == null) ShaderCheck.Built else ShaderCheck.Failed(problem)
                        checkShown = true
                        haptics.performHapticFeedback(
                            if (problem == null) HapticFeedbackType.Confirm else HapticFeedbackType.Reject
                        )
                        onSave(
                            stored.copy(
                                customShaderSource = source,
                                activePreset = if (problem == null) {
                                    VideoShaderPreset.CUSTOM
                                } else {
                                    stored.activePreset
                                }
                            )
                        )
                    }
                },
                enabled = canApply,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (checking) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = LocalContentColor.current
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.sp_video_shaders_custom_compile),
                    maxLines = 1
                )
            }
        }
    }
}

/** The shader stage in a word, for the Customization row that opens this page. */
@Composable
internal fun videoShadersSummary(): String {
    val context = LocalContext.current
    val shaders by remember(context) { ThemePreferences(context) }.videoShaders.collectAsState()
    return stringResource(
        when {
            !shaders.masterEnabled -> R.string.cz_video_shaders_off
            shaders.activePreset == VideoShaderPreset.NONE && !shaders.isPictureNeutral ->
                R.string.vpc_shaders_adjusted
            else -> shaders.activePreset.titleRes
        }
    )
}
