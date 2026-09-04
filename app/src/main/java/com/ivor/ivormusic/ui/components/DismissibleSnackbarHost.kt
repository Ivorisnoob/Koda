package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Drag distance that counts as "get rid of this" rather than a stray touch. */
private val DISMISS_DISTANCE = 56.dp

/** Or a flick, so a fast short swipe is not ignored. Pixels per second. */
private const val DISMISS_VELOCITY = 600f

/**
 * A [SnackbarHost] whose snackbar can be swiped away.
 *
 * Material's Compose snackbar has no dismissal gesture at all - unlike the View
 * one it replaced, which has swiped away since 2015 - so an undo toast sits
 * there for its full duration covering whatever is underneath, and the only way
 * to be rid of it is to perform the very action being offered. Every snackbar in
 * the app goes through here rather than each site growing its own gesture, for
 * the reason [com.ivor.ivormusic.ui.player.SwipeToSkip] exists: a gesture copied
 * by hand is a gesture that ends up on some surfaces and not others.
 *
 * Down and sideways both dismiss. Down is the obvious direction for something
 * anchored to the bottom of the screen, and sideways is the gesture the platform
 * taught people. Upward is rubber-banded rather than accepted: above the toast
 * is content, and flinging it up over that content reads as the wrong thing
 * moving.
 *
 * Dismissing this way settles as [androidx.compose.material3.SnackbarResult.Dismissed],
 * which is the same result a timeout produces - so a caller's "the offer expired"
 * branch already handles it and no call site has to learn about the gesture.
 */
@Composable
fun DismissibleSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        SwipeAwaySnackbar(data)
    }
}

@Composable
private fun SwipeAwaySnackbar(data: SnackbarData) {
    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val density = LocalDensity.current
    val dismissDistancePx = with(density) { DISMISS_DISTANCE.toPx() }

    // Keyed on the data: a replacement snackbar is a new offer and has to
    // arrive centred, not wherever the previous one was left mid-drag.
    val offsetX = remember(data) { Animatable(0f) }
    val offsetY = remember(data) { Animatable(0f) }
    // Only read when a drag is released, to decide how far the toast has to
    // travel before it is off screen.
    var size by remember(data) { mutableStateOf(IntSize.Zero) }

    Snackbar(
        snackbarData = data,
        modifier = Modifier
            .onSizeChanged { size = it }
            .offset {
                IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt())
            }
            // Fades with whichever axis has travelled further, so the toast
            // thins out under the finger instead of vanishing at the threshold.
            .alpha(
                1f - (maxOf(abs(offsetX.value), abs(offsetY.value)) /
                    (dismissDistancePx * 3f)).coerceIn(0f, 0.75f)
            )
            .semantics {
                // Swipe is not an option with a screen reader on, so the same
                // dismissal is published as an action TalkBack can offer.
                dismiss {
                    data.dismiss()
                    true
                }
            }
            .pointerInput(data) {
                val tracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, homeSpring()) }
                        scope.launch { offsetY.animateTo(0f, homeSpring()) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            // Upward travel is resisted rather than refused, so
                            // a drag that starts slightly wrong still feels like
                            // it is attached to the finger.
                            val nextY = offsetY.value + dragAmount.y
                            offsetY.snapTo(if (nextY < 0f) nextY / 4f else nextY)
                        }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity()
                        val flungDown = velocity.y > DISMISS_VELOCITY
                        val flungSideways = abs(velocity.x) > DISMISS_VELOCITY
                        val draggedDown = offsetY.value > dismissDistancePx
                        val draggedSideways = abs(offsetX.value) > dismissDistancePx

                        when {
                            draggedDown || flungDown -> {
                                haptics.tick()
                                scope.launch {
                                    offsetY.animateTo(size.height + 1f, tween(140))
                                    data.dismiss()
                                }
                            }

                            draggedSideways || flungSideways -> {
                                haptics.tick()
                                val target =
                                    if (offsetX.value > 0f || velocity.x > 0f) {
                                        size.width.toFloat()
                                    } else {
                                        -size.width.toFloat()
                                    }
                                scope.launch {
                                    offsetX.animateTo(target, tween(140))
                                    data.dismiss()
                                }
                            }

                            else -> {
                                scope.launch { offsetX.animateTo(0f, homeSpring()) }
                                scope.launch { offsetY.animateTo(0f, homeSpring()) }
                            }
                        }
                    },
                )
            },
    )
}

/** The house spring: a released drag that did not commit bounces home. */
private fun homeSpring() = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)
