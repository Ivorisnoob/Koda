package com.ivor.ivormusic.ui.applock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.AppTimeLimit
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.delay

/**
 * The full-screen "time's up" state of the daily time limit.
 *
 * Drawn at the very top of MusicApp's Box, above the NavHost, both player
 * overlays and the crash prompt: while it is up there is nothing to tap and
 * back does nothing ([BackHandler] consumes), which is the whole enforcement
 * model - no PIN machinery, just nowhere to go until midnight. Time spent
 * here is deliberately not charged to any budget.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLockOverlay(
    usedSecondsToday: Long,
    budgetMinutes: Int,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { /* locked: back must not leave this screen */ }

    // Ticking countdown to midnight. One-second cadence keeps a minutes-level
    // label honest across DST boundaries and clock changes without math.
    var millisLeft by remember {
        mutableLongStateOf(AppTimeLimit.millisUntilMidnight())
    }
    LaunchedEffect(Unit) {
        while (true) {
            millisLeft = AppTimeLimit.millisUntilMidnight()
            delay(1000L)
        }
    }

    val progress = AppTimeLimit.progressFraction(usedSecondsToday, budgetMinutes)

    // Slow shape-morph hero, in the onboarding's visual language.
    val transition = rememberInfiniteTransition(label = "lockMorph")
    val morphProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing)
        ),
        label = "heroRotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val morph = remember {
                    Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst)
                }
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .graphicsLayer { rotationZ = rotation }
                        .clip(LockMorphShape(morph, morphProgress))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Icon(
                    imageVector = Icons.Rounded.NightsStay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(60.dp)
                )
            }

            CircularWavyProgressIndicator(progress = { progress })

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "That's today's listening",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("You've had ")
                        append(AppTimeLimit.formatBudget(budgetMinutes))
                        append(" with Koda today. Come back tomorrow - ")
                        append("the app unlocks in ")
                        append(formatCountdown(millisLeft))
                        append(".")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Resets every night at midnight",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatCountdown(millis: Long): String {
    val totalMinutes = (millis / 60000L).coerceAtLeast(0L)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "under a minute"
    }
}

/** Same outline math as the onboarding hero's morph shape, kept local. */
private class LockMorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        val bounds = morph.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
