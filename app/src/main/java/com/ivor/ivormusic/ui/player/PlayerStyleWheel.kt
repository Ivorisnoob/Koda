package com.ivor.ivormusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.ivor.ivormusic.data.PlayerStyle
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The style wheel: long-press the artwork in any player style and every
 * style blooms out of the press point as its own die-cut MaterialShapes
 * button, arranged in a ring, while the live player blurs beneath it.
 *
 * Two ways to pick:
 * - Keep holding after the long-press, drag toward a style (it grows and
 *   ticks as you aim), and release on it - one continuous gesture.
 * - Or release in the center: the wheel stays open, tap a style.
 *
 * Expressive contract:
 * - Shape signals identity: each style is cut into the polygon that best
 *   matches its personality (Editorial is a flower, Dial is a sunny ring,
 *   Morph is a soft burst, ...).
 * - Motion is physics: items bloom outward on staggered underdamped
 *   springs with visible overshoot while the whole ring settles from a
 *   slight counter-rotation.
 * - Haptics mark the open (long-press), each aim change (tick), and the
 *   pick (confirm).
 */
internal data class PlayerStyleWheelEntry(
    val style: PlayerStyle,
    val label: String,
    val icon: ImageVector,
    val polygon: RoundedPolygon
)

/**
 * Bridges the artwork's hold gesture into the wheel. The artwork opens the
 * wheel on long-press and keeps streaming drag deltas while the finger is
 * down; the wheel turns the accumulated offset into an aimed item and
 * commits it when the finger lifts.
 */
class PlayerStyleWheelController {
    var isOpen by mutableStateOf(false)
        private set
    var isHolding by mutableStateOf(false)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    /** Incremented on every hold release so the wheel can commit the aim. */
    var releaseTick by mutableIntStateOf(0)
        private set

    fun openFromHold() {
        isOpen = true
        isHolding = true
        dragOffset = Offset.Zero
    }

    fun dragBy(delta: Offset) {
        if (isHolding) dragOffset += delta
    }

    fun releaseHold() {
        if (!isHolding) return
        isHolding = false
        releaseTick++
    }

    fun dismiss() {
        isOpen = false
        isHolding = false
        dragOffset = Offset.Zero
    }
}

@Composable
fun rememberPlayerStyleWheelController(): PlayerStyleWheelController =
    remember { PlayerStyleWheelController() }

/**
 * Provided by ExpandablePlayer around the active style content so any
 * artwork can host the hold gesture without per-style plumbing. Null when
 * no wheel is available (previews, standalone usage).
 */
val LocalPlayerStyleWheelController =
    staticCompositionLocalOf<PlayerStyleWheelController?> { null }

/**
 * Hold gesture for artwork: long-press opens the style wheel and, while
 * the finger stays down, streams drag deltas to it; the release commits
 * the aimed style. Consumes the post-long-press stream (including the up)
 * so sibling tap/drag detectors on the same artwork stay quiet. A no-op
 * when no controller is provided.
 */
fun Modifier.styleWheelHold(controller: PlayerStyleWheelController?): Modifier {
    if (controller == null) return this
    return this.pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                controller.openFromHold()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == longPress.id }
                        ?: event.changes.first()
                    if (change.changedToUpIgnoreConsumed()) {
                        change.consume()
                        controller.releaseHold()
                        break
                    }
                    if (change.positionChanged()) {
                        controller.dragBy(change.positionChange())
                        change.consume()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberPlayerStyleWheelEntries(): List<PlayerStyleWheelEntry> = remember {
    listOf(
        PlayerStyleWheelEntry(PlayerStyle.CLASSIC, "Classic", Icons.Rounded.PlayCircle, MaterialShapes.Circle),
        PlayerStyleWheelEntry(PlayerStyle.GESTURE, "Gesture", Icons.Rounded.SwipeRight, MaterialShapes.Pill),
        PlayerStyleWheelEntry(PlayerStyle.EDITORIAL, "Editorial", Icons.Rounded.Newspaper, MaterialShapes.Flower),
        PlayerStyleWheelEntry(PlayerStyle.POSTER, "Canvas", Icons.Rounded.Wallpaper, MaterialShapes.Gem),
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
    controller: PlayerStyleWheelController,
    onStyleSelected: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }
    val entries = rememberPlayerStyleWheelEntries()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Mark the open with the long-press haptic the gesture earned.
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Aim: while the finger is still down, the accumulated drag direction
    // picks the nearest item once it clears a dead zone around the center.
    val aimThresholdPx = with(density) { 56.dp.toPx() }
    val aimedIndex: Int? = if (
        controller.isHolding &&
        controller.dragOffset.getDistance() > aimThresholdPx
    ) {
        val dragAngle = Math.toDegrees(
            atan2(controller.dragOffset.y.toDouble(), controller.dragOffset.x.toDouble())
        ).toFloat()
        entries.indices.minByOrNull { index ->
            val itemAngle = (index * 360f / entries.size) - 90f
            var diff = abs(dragAngle - itemAngle) % 360f
            if (diff > 180f) diff = 360f - diff
            diff
        }
    } else null

    // Tick as the aim moves between items.
    LaunchedEffect(aimedIndex) {
        if (aimedIndex != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Releasing the hold commits the aim; releasing in the dead zone keeps
    // the wheel open for tap selection.
    val currentAimedIndex by rememberUpdatedState(aimedIndex)
    val currentOnStyleSelected by rememberUpdatedState(onStyleSelected)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(controller.releaseTick) {
        if (controller.releaseTick == 0) return@LaunchedEffect
        val aimed = currentAimedIndex
        if (aimed != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            currentOnStyleSelected(entries[aimed].style)
            currentOnDismiss()
        }
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
        // Light tint over the blurred player beneath (ExpandablePlayer
        // blurs the live content while the wheel is open). First child, so
        // item touches never reach it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val radius = minOf(maxWidth, maxHeight) * 0.36f
            val radiusPx = with(density) { radius.toPx() }

            // Center readout: what you are on, or what you are aiming at.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (aimedIndex != null) "Release for" else "Player style",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = aimedIndex?.let { entries[it].label }
                        ?: (entries.firstOrNull { it.style == currentStyle }?.label ?: ""),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (aimedIndex != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            entries.forEachIndexed { index, entry ->
                val progress = bloom[index].value
                val angleRad = Math.toRadians((index * 360.0 / entries.size) - 90.0)
                val itemX = (cos(angleRad) * radiusPx * progress).roundToInt()
                val itemY = (sin(angleRad) * radiusPx * progress).roundToInt()
                val selected = entry.style == currentStyle
                val aimed = aimedIndex == index
                val itemShape = remember(entry.polygon) { EditorialPolygonShape(entry.polygon) }

                // Aimed items grow toward the finger; the emphasis is a
                // spring, so sweeping the aim feels alive.
                val emphasis by animateFloatAsState(
                    targetValue = when {
                        aimed -> 1.28f
                        selected -> 1.12f
                        else -> 1f
                    },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "WheelItemEmphasis"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset { IntOffset(itemX, itemY) }
                        .graphicsLayer {
                            alpha = progress.coerceIn(0f, 1f)
                            val scale = progress * emphasis
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
                        color = when {
                            aimed -> MaterialTheme.colorScheme.primary
                            selected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (aimed || selected) MaterialTheme.colorScheme.onPrimary
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
                        color = if (aimed || selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
