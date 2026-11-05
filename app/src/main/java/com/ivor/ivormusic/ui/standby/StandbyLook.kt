package com.ivor.ivormusic.ui.standby

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.min

/** The Clock face's three faces. Persisted by [id]: frozen, append only. */
internal enum class StandbyClockStyle(val id: String, @StringRes val titleRes: Int) {
    ANALOG("analog", R.string.standby_clock_analog),
    BOLD("bold", R.string.standby_clock_bold),
    MINIMAL("minimal", R.string.standby_clock_minimal),
}

/** Standby's colours: the cover's, or the same in greys. Persisted by [id]. */
internal enum class StandbyTint(val id: String, @StringRes val titleRes: Int) {
    ALBUM("album", R.string.standby_tint_album),
    MONO("mono", R.string.standby_tint_mono),
}

/** What is behind every face: the cover's glow, or black. Persisted by [id]. */
internal enum class StandbyBackground(val id: String, @StringRes val titleRes: Int) {
    GLOW("glow", R.string.standby_background_glow),
    BLACK("black", R.string.standby_background_black),
}

/**
 * The listener's choices for how Standby looks, chosen from the zoomed-out
 * picker and remembered. One holder for the whole screen, because the colour
 * and the background are drawn by the host while the clock style is drawn by
 * a face.
 */
@Stable
internal class StandbyLook(private val prefs: ThemePreferences) {
    var clockStyle by mutableStateOf(
        StandbyClockStyle.entries.firstOrNull { it.id == prefs.getStandbyLook(KEY_CLOCK) } ?: StandbyClockStyle.ANALOG
    )
        private set
    var tint by mutableStateOf(
        StandbyTint.entries.firstOrNull { it.id == prefs.getStandbyLook(KEY_TINT) } ?: StandbyTint.ALBUM
    )
        private set
    var background by mutableStateOf(
        StandbyBackground.entries.firstOrNull { it.id == prefs.getStandbyLook(KEY_BACKGROUND) } ?: StandbyBackground.GLOW
    )
        private set

    fun choose(style: StandbyClockStyle) {
        clockStyle = style
        prefs.setStandbyLook(KEY_CLOCK, style.id)
    }

    fun choose(tint: StandbyTint) {
        this.tint = tint
        prefs.setStandbyLook(KEY_TINT, tint.id)
    }

    fun choose(background: StandbyBackground) {
        this.background = background
        prefs.setStandbyLook(KEY_BACKGROUND, background.id)
    }

    private companion object {
        const val KEY_CLOCK = "clock_style"
        const val KEY_TINT = "tint"
        const val KEY_BACKGROUND = "background"
    }
}

@Composable
internal fun rememberStandbyLook(): StandbyLook {
    val context = LocalContext.current
    return remember(context) { StandbyLook(ThemePreferences(context)) }
}

/**
 * The picker's options, as a frosted capsule of choices: the clock's style
 * when the Clock face is chosen, then the colour and the background, which
 * every face shares. A choice applies at once, so it is judged on the face
 * right under it.
 */
@Composable
internal fun StandbyLookBar(look: StandbyLook, face: StandbyFace, modifier: Modifier = Modifier) {
    val haptics = rememberKodaHaptics()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.72f))
            .border(0.5.dp, scheme.onSurface.copy(alpha = 0.12f), CircleShape)
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (face == StandbyFace.CLOCK) {
            StandbyClockStyle.entries.forEach { style ->
                LookChip(stringResource(style.titleRes), look.clockStyle == style) {
                    haptics.tick()
                    look.choose(style)
                }
            }
            LookDivider()
        }
        StandbyTint.entries.forEach { tint ->
            LookChip(stringResource(tint.titleRes), look.tint == tint) {
                haptics.tick()
                look.choose(tint)
            }
        }
        LookDivider()
        StandbyBackground.entries.forEach { background ->
            LookChip(stringResource(background.titleRes), look.background == background) {
                haptics.tick()
                look.choose(background)
            }
        }
    }
}

@Composable
private fun LookDivider() {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .size(width = 1.dp, height = 18.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
    )
}

/** One choice: a capsule that fills when chosen and presses in under the finger. */
@Composable
private fun LookChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.onSurface else scheme.onSurface.copy(alpha = 0f),
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "lookChip",
    )
    val content by animateColorAsState(
        if (selected) scheme.surface else scheme.onSurface,
        spring(stiffness = Spring.StiffnessMediumLow),
        label = "lookChipText",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        if (pressed) appleSpring(0.16f) else appleSpring(0.38f, 0.6f),
        label = "lookChipScale",
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

/**
 * [base] at the size that fills [widthFraction] of [roomW] and
 * [heightFraction] of [roomH] with "00:00", measured in that very style.
 */
@Composable
internal fun rememberFittedClockStyle(
    base: TextStyle,
    roomW: Int,
    roomH: Int,
    widthFraction: Float,
    heightFraction: Float,
): TextStyle {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(base, roomW, roomH, density, widthFraction, heightFraction) {
        val probe = base.copy(fontSize = 100.sp)
        val atHundred = measurer.measure("00:00", probe).size
        val byWidth = 100f * roomW * widthFraction / atHundred.width.coerceAtLeast(1)
        val byHeight = 100f * roomH * heightFraction / atHundred.height.coerceAtLeast(1)
        probe.copy(fontSize = min(byWidth, byHeight).coerceAtLeast(12f).sp)
    }
}

/**
 * Digits as the whole screen: heavy, in the album's accent, the date above
 * and the song beneath. Measured to the room, with a margin for the
 * downloadable display font arriving wider than its fallback.
 */
@Composable
internal fun BoldClock(
    timeText: String,
    dateText: String,
    footer: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
        val style = rememberFittedClockStyle(
            base = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                lineHeight = 1.em,
                letterSpacing = (-0.04).em,
                fontFeatureSettings = "tnum, lnum",
                color = scheme.primary,
            ),
            roomW = constraints.maxWidth,
            roomH = constraints.maxHeight,
            widthFraction = 0.82f,
            heightFraction = 0.6f,
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                dateText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            StandbyClock(timeText = timeText, style = style)
            footer()
        }
    }
}

/** Thin digits in the middle of the dark, the date under them, the song at the foot. */
@Composable
internal fun MinimalClock(
    timeText: String,
    dateText: String,
    footer: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp)) {
        val style = rememberFittedClockStyle(
            base = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.ExtraLight,
                lineHeight = 1.em,
                letterSpacing = (-0.02).em,
                fontFeatureSettings = "tnum, lnum",
                color = scheme.onSurface,
            ),
            roomW = constraints.maxWidth,
            roomH = constraints.maxHeight,
            widthFraction = 0.5f,
            heightFraction = 0.4f,
        )
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.weight(1f))
            StandbyClock(timeText = timeText, style = style)
            Spacer(Modifier.height(8.dp))
            Text(
                dateText.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.18.em,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            footer()
        }
    }
}
