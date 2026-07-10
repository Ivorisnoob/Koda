package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.ivor.ivormusic.data.PlayerStyle
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The style wheel: long-press the artwork in any player style and every
 * style blooms out of the press point as its own die-cut MaterialShapes
 * button, arranged in a ring. Tap one and the player morphs into that
 * style live.
 *
 * Expressive contract:
 * - Shape signals identity: each style is cut into the polygon that best
 *   matches its personality (Editorial is a flower, Dial is a sunny ring,
 *   Morph is a soft burst, ...).
 * - Motion is physics: items bloom outward on staggered underdamped
 *   springs with visible overshoot while the whole ring settles from a
 *   slight counter-rotation; icons stay upright throughout.
 * - The overlay is a flat high-alpha surface, no blur, no gradient scrim.
 * - Haptics mark the open (long-press) and the pick (confirm).
 */
internal data class PlayerStyleWheelEntry(
    val style: PlayerStyle,
    val label: String,
    val icon: ImageVector,
    val polygon: RoundedPolygon
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberPlayerStyleWheelEntries(): List<PlayerStyleWheelEntry> = remember {
    listOf(
        PlayerStyleWheelEntry(PlayerStyle.CLASSIC, "Classic", Icons.Rounded.PlayCircle, MaterialShapes.Circle),
        PlayerStyleWheelEntry(PlayerStyle.GESTURE, "Gesture", Icons.Rounded.SwipeRight, MaterialShapes.Pill),
        PlayerStyleWheelEntry(PlayerStyle.EDITORIAL, "Editorial", Icons.Rounded.Newspaper, MaterialShapes.Flower),
        PlayerStyleWheelEntry(PlayerStyle.POSTER, "Poster", Icons.Rounded.TextFields, MaterialShapes.Gem),
        PlayerStyleWheelEntry(PlayerStyle.BENTO, "Bento", Icons.Rounded.GridView, MaterialShapes.Cookie4Sided),
        PlayerStyleWheelEntry(PlayerStyle.STICKER, "Sticker", Icons.Rounded.Interests, MaterialShapes.Clover4Leaf),
        PlayerStyleWheelEntry(PlayerStyle.MORPH, "Morph", Icons.Rounded.Animation, MaterialShapes.SoftBurst),
        PlayerStyleWheelEntry(PlayerStyle.DIAL, "Dial", Icons.Rounded.RadioButtonChecked, MaterialShapes.Sunny)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerStyleWheel(
    currentStyle: PlayerStyle,
    onStyleSelected: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }
    val entries = rememberPlayerStyleWheelEntries()
    val haptics = LocalHapticFeedback.current

    // Mark the open with the long-press haptic the gesture earned.
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Staggered radial bloom: every item springs outward with overshoot.
    val bloom = remember { entries.map { Animatable(0f) } }
    val wheelSettle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch {
            wheelSettle.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.7f,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        bloom.forEachIndexed { index, animatable ->
            launch {
                delay(index * 35L)
                animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Flat dismiss field: solid high-alpha surface, no blur, no scrim
        // gradient. First child, so item touches never reach it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val radius = minOf(maxWidth, maxHeight) * 0.36f
            val radiusPx = with(LocalDensity.current) { radius.toPx() }
            val ringRotation = (1f - wheelSettle.value) * -24f

            // Center readout: what you are on now.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Player style",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = entries.firstOrNull { it.style == currentStyle }?.label ?: "",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            entries.forEachIndexed { index, entry ->
                val progress = bloom[index].value
                val angleRad = Math.toRadians((index * 360.0 / entries.size) - 90.0)
                val itemX = (cos(angleRad) * radiusPx * progress * wheelRotationCos(ringRotation) -
                    sin(angleRad) * radiusPx * progress * wheelRotationSin(ringRotation)).roundToInt()
                val itemY = (sin(angleRad) * radiusPx * progress * wheelRotationCos(ringRotation) +
                    cos(angleRad) * radiusPx * progress * wheelRotationSin(ringRotation)).roundToInt()
                val selected = entry.style == currentStyle
                val itemShape = remember(entry.polygon) { EditorialPolygonShape(entry.polygon) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(itemX, itemY) }
                        .graphicsLayer {
                            alpha = progress.coerceIn(0f, 1f)
                            val scale = progress * (if (selected) 1.12f else 1f)
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    Surface(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onStyleSelected(entry.style)
                            onDismiss()
                        },
                        modifier = Modifier.size(64.dp),
                        shape = itemShape,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = entry.icon,
                                contentDescription = entry.label,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun wheelRotationCos(degrees: Float): Float =
    cos(Math.toRadians(degrees.toDouble())).toFloat()

private fun wheelRotationSin(degrees: Float): Float =
    sin(Math.toRadians(degrees.toDouble())).toFloat()
