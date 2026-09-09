package com.ivor.ivormusic.ui.player

import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.VisualizerPreferences
import com.ivor.ivormusic.data.VisualizerStyle
import com.ivor.ivormusic.service.VisualizerBus
import com.ivor.ivormusic.ui.settings.SettingsCard
import com.ivor.ivormusic.ui.settings.SettingsDivider
import com.ivor.ivormusic.ui.settings.SettingsSection
import com.ivor.ivormusic.ui.settings.SettingsToggleRow
import com.ivor.ivormusic.util.VisualizerMath
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/** Height of the strip in every style, and the resting line it settles to. */
private val VISUALIZER_HEIGHT = 88.dp
private const val RESTING_LEVEL = 0.06f

/** Fast up so beats land on time, slow down so bars fall like gravity. */
private const val ATTACK = 0.55f
private const val RELEASE = 0.16f

/** Below this, with no fresh PCM, there is nothing left to animate. */
private const val SETTLED_EPSILON = 0.005f

/** One state write per this many milliseconds, rather than per frame. */
private const val EMIT_INTERVAL_MS = 33L

/**
 * The shared music-visualizer strip drawn under the progress row of every
 * expanded player style.
 *
 * The levels are Koda's own decoded PCM, measured on the playback pipeline by
 * `VisualizerAudioProcessor` and carried here by [VisualizerBus]. It needs no
 * permission and holds no microphone. The platform's
 * `android.media.audiofx.Visualizer` would have needed one: that is an audio
 * capture API, gated on RECORD_AUDIO whatever session it is pointed at, and it
 * lights the privacy indicator - for samples the app already has in hand on
 * their way to the sink.
 *
 * Everything colors through [MaterialTheme.colorScheme] so palettes, AMOLED
 * and dynamic color keep working. The per-frame dance is draw-phase only: the
 * levels are read inside the [Canvas] lambda, never in composition, so a frame
 * of movement never recomposes the player around it.
 *
 * Off by default - it adds [VISUALIZER_HEIGHT] to eight layouts, which is
 * something to opt into. Reduced motion and Battery Saver both collapse it to
 * still bars rather than removing it, so the layout does not change underneath
 * someone who turned it on.
 */
@Composable
fun PlayerVisualizerSlot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { VisualizerPreferences(context) }
    val enabled by prefs.enabled.collectAsState()
    val style by prefs.style.collectAsState()
    val barCount by prefs.barCount.collectAsState()
    val sensitivity by prefs.sensitivity.collectAsState()
    val description = stringResource(R.string.cd_visualizer)

    AnimatedVisibility(
        visible = enabled,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
        modifier = modifier
    ) {
        Crossfade(
            targetState = style,
            animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
            label = "VisualizerStyle"
        ) { activeStyle ->
            VisualizerCanvas(
                style = activeStyle,
                barCount = barCount,
                sensitivity = sensitivity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(VISUALIZER_HEIGHT)
                    .semantics { contentDescription = description }
            )
        }
    }
}

/**
 * Whether motion is wanted at all. Animator scale zero is the accessibility
 * setting; Battery Saver is the same answer for a different reason, and both
 * are read once because neither needs to land mid-frame.
 */
@Composable
private fun rememberStillPreferred(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val noAnimation = runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
        val saving = runCatching {
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)
        noAnimation || saving
    }
}

/**
 * Drive [barCount] smoothed levels in 0..1 off [VisualizerBus].
 *
 * The loop parks itself. Once the bars have settled onto the resting line and
 * no PCM has arrived for [VisualizerBus.STALE_AFTER_MS], it suspends until the
 * next submission instead of waking every frame to redraw a flat line - which
 * is what a paused player, a finished album and a collapsed player all look
 * like from here.
 */
@Composable
private fun rememberVisualizerLevels(barCount: Int, sensitivity: Float): State<FloatArray> {
    val levels = remember(barCount) {
        mutableStateOf(FloatArray(barCount) { RESTING_LEVEL })
    }
    val latestSensitivity by rememberUpdatedState(sensitivity)
    val still = rememberStillPreferred()

    LaunchedEffect(barCount, still) {
        if (still) {
            // Still, not absent: gentle fixed bars keep the strip legible as
            // part of the layout without any movement at all.
            levels.value = FloatArray(barCount) { i -> 0.22f + 0.06f * (i % 3) }
            return@LaunchedEffect
        }
        VisualizerBus.acquire(barCount)
        try {
            val displayed = FloatArray(barCount) { RESTING_LEVEL }
            val target = FloatArray(barCount)
            var lastEmitMs = 0L
            while (true) {
                withFrameNanos { }
                val now = SystemClock.uptimeMillis()
                val source = VisualizerBus.levels.value
                val stale = VisualizerBus.isStale(now)
                for (i in target.indices) {
                    val raw = if (stale || i >= source.size) 0f else source[i]
                    target[i] = (raw * latestSensitivity)
                        .coerceIn(0f, 1f)
                        .coerceAtLeast(RESTING_LEVEL)
                }
                VisualizerMath.smoothBands(displayed, target, ATTACK, RELEASE)
                if (now - lastEmitMs >= EMIT_INTERVAL_MS) {
                    lastEmitMs = now
                    levels.value = displayed.copyOf()
                }
                if (stale && displayed.all { it <= RESTING_LEVEL + SETTLED_EPSILON }) {
                    // Nothing left to draw until audio comes back. Any
                    // submission wakes this, whatever it contains.
                    displayed.fill(RESTING_LEVEL)
                    levels.value = FloatArray(barCount) { RESTING_LEVEL }
                    VisualizerBus.levels.drop(1).first()
                }
            }
        } finally {
            VisualizerBus.release()
        }
    }
    return levels
}

@Composable
private fun VisualizerCanvas(
    style: VisualizerStyle,
    barCount: Int,
    sensitivity: Float,
    modifier: Modifier = Modifier
) {
    val levels by rememberVisualizerLevels(barCount = barCount, sensitivity = sensitivity)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    // Every read of `levels` below happens inside this lambda, which runs in
    // the draw phase - so a frame of movement invalidates drawing and nothing
    // above it recomposes.
    Canvas(modifier = modifier) {
        when (style) {
            VisualizerStyle.BARS -> drawVisualizerBars(levels, primary, secondary)
            VisualizerStyle.WAVE -> drawVisualizerWave(levels, primary)
            VisualizerStyle.DOTS -> drawVisualizerDots(levels, primary, tertiary)
        }
    }
}

private fun DrawScope.drawVisualizerBars(
    levels: FloatArray,
    primary: Color,
    secondary: Color
) {
    if (levels.isEmpty()) return
    val gradient = Brush.verticalGradient(listOf(primary, secondary))
    val gap = size.width / levels.size
    val barWidth = (gap * 0.52f).coerceAtLeast(3f)
    val centerY = size.height / 2f
    levels.forEachIndexed { i, level ->
        val half = (level.coerceIn(0f, 1f) * (size.height / 2f - 4f)).coerceAtLeast(3f)
        drawRoundRect(
            brush = gradient,
            topLeft = Offset(gap * i + (gap - barWidth) / 2f, centerY - half),
            size = Size(barWidth, half * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
    }
}

private fun DrawScope.drawVisualizerWave(levels: FloatArray, primary: Color) {
    if (levels.size < 2) return
    val centerY = size.height / 2f
    val amplitude = size.height / 2f - 6f
    val stepX = size.width / (levels.size - 1)
    // Mirrored silhouette: the curve out and back, filled softly with a
    // bright center stroke so quiet passages still read.
    val silhouette = Path().apply {
        levels.forEachIndexed { i, level ->
            val x = stepX * i
            val y = centerY - level.coerceIn(0f, 1f) * amplitude
            if (i == 0) moveTo(x, y) else {
                val previousX = stepX * (i - 1)
                val previousY = centerY - levels[i - 1].coerceIn(0f, 1f) * amplitude
                quadraticTo(previousX + stepX / 2f, previousY, x, y)
            }
        }
        for (i in levels.size - 1 downTo 0) {
            val x = stepX * i
            lineTo(x, centerY + levels[i].coerceIn(0f, 1f) * amplitude)
        }
        close()
    }
    drawPath(
        silhouette,
        Brush.verticalGradient(
            listOf(
                primary.copy(alpha = 0.05f),
                primary.copy(alpha = 0.45f),
                primary.copy(alpha = 0.05f)
            )
        )
    )
    val spine = Path().apply {
        levels.forEachIndexed { i, level ->
            val x = stepX * i
            val y = centerY - level.coerceIn(0f, 1f) * amplitude
            if (i == 0) moveTo(x, y) else {
                val previousX = stepX * (i - 1)
                lineTo((previousX + x) / 2f, (centerY - levels[i - 1] * amplitude + y) / 2f)
            }
        }
    }
    drawPath(spine, primary, style = Stroke(width = 5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawVisualizerDots(
    levels: FloatArray,
    primary: Color,
    tertiary: Color
) {
    if (levels.isEmpty()) return
    val gap = size.width / levels.size
    val centerY = size.height / 2f
    val maxRadius = (size.height / 2f - 6f).coerceAtLeast(4f)
    levels.forEachIndexed { i, level ->
        val clamped = level.coerceIn(0f, 1f)
        val radius = 3f + clamped * (gap * 0.32f).coerceAtMost(maxRadius - 3f)
        val center = Offset(gap * i + gap / 2f, centerY)
        drawCircle(tertiary.copy(alpha = 0.30f), radius = radius * 2.1f, center = center)
        drawCircle(primary, radius = radius, center = center)
    }
}

/* ------------------------------------------------------------------ */
/* Settings                                                            */
/* ------------------------------------------------------------------ */

/**
 * The Player-page section owning every visualizer choice. It talks to its own
 * [VisualizerPreferences] directly, so no new parameter has to travel the
 * SettingsScreen contract from MainActivity: the page already hosts several
 * self-contained controls and this is one more.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VisualizerSettingsSection() {
    val context = LocalContext.current
    val prefs = remember(context) { VisualizerPreferences(context) }
    val enabled by prefs.enabled.collectAsState()
    val style by prefs.style.collectAsState()
    val barCount by prefs.barCount.collectAsState()
    val sensitivity by prefs.sensitivity.collectAsState()

    SettingsSection(title = stringResource(R.string.sp_visualizer)) {
        SettingsCard {
            SettingsToggleRow(
                icon = Icons.Rounded.GraphicEq,
                title = stringResource(R.string.sp_visualizer),
                subtitle = stringResource(R.string.sp_visualizer_sub),
                enabled = enabled,
                onToggle = prefs::setEnabled,
                explanation = stringResource(R.string.si_visualizer)
            )

            AnimatedVisibility(visible = enabled) {
                Column {
                    SettingsDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        VisualizerSettingLabel(stringResource(R.string.sp_visualizer_style))
                        Spacer(modifier = Modifier.height(12.dp))
                        val styles = VisualizerStyle.entries.toList()
                        val labels = listOf(
                            stringResource(R.string.sp_visualizer_style_bars),
                            stringResource(R.string.sp_visualizer_style_wave),
                            stringResource(R.string.sp_visualizer_style_dots)
                        )
                        ConnectedChoiceRow(
                            count = styles.size,
                            selected = { index -> style == styles[index] },
                            onSelect = { index -> prefs.setStyle(styles[index]) },
                            label = { index -> labels[index] }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        VisualizerSettingLabel(stringResource(R.string.sp_visualizer_density))
                        Spacer(modifier = Modifier.height(12.dp))
                        val counts = VisualizerPreferences.BAR_COUNT_OPTIONS
                        ConnectedChoiceRow(
                            count = counts.size,
                            selected = { index -> barCount == counts[index] },
                            onSelect = { index -> prefs.setBarCount(counts[index]) },
                            label = { index -> counts[index].toString() }
                        )
                        VisualizerHint(
                            icon = Icons.Rounded.ViewColumn,
                            text = stringResource(R.string.sp_visualizer_density_hint)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        VisualizerSettingLabel(stringResource(R.string.sp_visualizer_sensitivity))
                        Slider(
                            value = sensitivity,
                            onValueChange = prefs::setSensitivity,
                            valueRange = VisualizerPreferences.MIN_SENSITIVITY..
                                VisualizerPreferences.MAX_SENSITIVITY,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        VisualizerHint(
                            icon = Icons.Rounded.Tune,
                            text = stringResource(R.string.sp_visualizer_sensitivity_hint)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualizerSettingLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun VisualizerHint(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

/**
 * The segmented control M3 Expressive draws for a small set of mutually
 * exclusive choices, shaped as one connected group - the same component the
 * SponsorBlock category rows and the video quality tabs already use. Both
 * visualizer choices go through it rather than repeating the shape arithmetic.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedChoiceRow(
    count: Int,
    selected: (Int) -> Boolean,
    onSelect: (Int) -> Unit,
    label: (Int) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        for (index in 0 until count) {
            ToggleButton(
                checked = selected(index),
                onCheckedChange = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(label(index), maxLines = 1)
            }
        }
    }
}
