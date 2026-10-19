package com.ivor.ivormusic.ui.standby

import android.view.OrientationEventListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The offer to enter Standby: a small capsule that drops in from the top when
 * the phone looks set down for the night - charging, music playing, and the
 * phone itself turned on its side.
 *
 * The phone, not the window: on a phone Koda is held upright unless the user
 * lets it rotate, so the window never goes landscape and could not be the
 * signal. The orientation sensor is read only while the other conditions hold,
 * and the side has to be held for a moment before anything appears, so
 * picking the phone up does not flash the offer.
 *
 * It never opens Standby on its own. Once per charge: shown, tapped or
 * dismissed, it stays away until the phone comes off the charger. Swipe it up
 * or tap the cross to dismiss; it also leaves by itself after a few seconds.
 */
@Composable
fun StandbySuggestion(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val enabled by prefs.standbySuggest.collectAsState()
    if (!enabled) return

    val standbyOpen by viewModel.isStandbyOpen.collectAsState()
    val playing by viewModel.playWhenReady.collectAsState()
    val song by viewModel.currentSong.collectAsState()
    val battery by rememberBatteryInfo()
    val charging = battery?.charging == true
    var spent by remember { mutableStateOf(false) }
    LaunchedEffect(charging) { if (!charging) spent = false }

    val armed = charging && playing && song != null && !standbyOpen && !spent
    val sideways by rememberPhoneOnItsSide(active = armed)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(armed && sideways) {
        if (armed && sideways) {
            delay(SETTLE_MS)
            visible = true
            delay(SHOWN_MS)
            visible = false
            spent = true
        } else {
            visible = false
        }
    }

    val haptics = rememberKodaHaptics()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(appleSpring(0.5f, 0.8f)) { -it * 2 } +
                scaleIn(appleSpring(0.5f, 0.8f), initialScale = 0.86f) +
                fadeIn(tween(180)),
            exit = slideOutVertically(appleSpring(0.38f)) { -it * 2 } +
                scaleOut(appleSpring(0.38f), targetScale = 0.9f) +
                fadeOut(tween(160)),
        ) {
            SuggestionCapsule(
                onOpen = {
                    haptics.confirm()
                    spent = true
                    visible = false
                    viewModel.openStandby()
                },
                onDismiss = {
                    spent = true
                    visible = false
                },
            )
        }
    }
}

@Composable
private fun SuggestionCapsule(onOpen: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // Follows a finger pushing it back up, resists one pulling it down.
    val drag = remember { Animatable(0f) }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 420.dp)
            .graphicsLayer { translationY = drag.value }
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                var raw = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        raw = drag.value
                    },
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        raw += dy
                        val shown = if (raw > 0f) rubberBand(raw, size.height * 3f) else raw
                        scope.launch { drag.snapTo(shown) }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().y
                        if (drag.value + projectedDistance(velocity) < -size.height * 0.6f) {
                            onDismiss()
                        } else {
                            scope.launch { drag.animateTo(0f, appleSpring(0.4f), initialVelocity = velocity) }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f, appleSpring(0.4f)) } },
                )
            }
            .shadow(18.dp, CircleShape, ambientColor = scheme.scrim, spotColor = scheme.scrim)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest)
            .clickable(onClick = onOpen)
            .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = stringResource(R.string.standby_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(R.string.standby_suggest_body),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.standby_suggest_dismiss),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Whether the phone is turned on its side, from the orientation sensor; false
 * while lying flat, where the sensor has no answer. Listens only while
 * [active].
 */
@Composable
private fun rememberPhoneOnItsSide(active: Boolean): State<Boolean> {
    val context = LocalContext.current
    val sideways = remember { mutableStateOf(false) }
    DisposableEffect(context, active) {
        if (!active) {
            sideways.value = false
            return@DisposableEffect onDispose { }
        }
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                sideways.value = degrees != ORIENTATION_UNKNOWN &&
                    (degrees in 60..120 || degrees in 240..300)
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return sideways
}

/** Held on its side this long before the offer appears. */
private const val SETTLE_MS = 1_500L

/** How long the offer stays if left alone. */
private const val SHOWN_MS = 9_000L
