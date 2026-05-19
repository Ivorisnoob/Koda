package com.ivor.ivormusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivor.ivormusic.platform.extractAlbumColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ColorCloud(
    val color: Color,
    val baseOffset: Offset,
    val radiusMultiplier: Float,
    val phaseOffset: Float,
    val speedMultiplier: Float
)

@Composable
fun ChromaticMistBackground(
    albumArtUrl: String?,
    enabled: Boolean = true,
    fallbackColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val themeBackground = MaterialTheme.colorScheme.background
    val backgroundColor = fallbackColor ?: themeBackground
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary

    if (!enabled || albumArtUrl == null) {
        Box(modifier = modifier.fillMaxSize().background(backgroundColor))
        return
    }

    val defaultColors = listOf(surfaceColor, surfaceContainer, surfaceContainerHigh, primaryColor.copy(alpha = 0.3f))
    var colorPalette by remember { mutableStateOf(defaultColors) }

    LaunchedEffect(albumArtUrl) {
        val colors = extractAlbumColors(albumArtUrl)
        if (colors.isNotEmpty()) colorPalette = colors
    }

    val animatedColors = colorPalette.mapIndexed { index, targetColor ->
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            label = "ColorTransition$index"
        ).value
    }

    val clouds = remember(animatedColors) {
        listOf(
            ColorCloud(animatedColors.getOrElse(0) { surfaceColor }.copy(alpha = 0.6f), Offset(0.2f, 0.15f), 0.9f, 0f, 1f),
            ColorCloud(animatedColors.getOrElse(1) { surfaceColor }.copy(alpha = 0.5f), Offset(0.8f, 0.85f), 0.85f, PI.toFloat() * 0.5f, 0.8f),
            ColorCloud(animatedColors.getOrElse(2) { surfaceColor }.copy(alpha = 0.4f), Offset(0.75f, 0.3f), 0.7f, PI.toFloat(), 1.2f),
            ColorCloud(animatedColors.getOrElse(3) { surfaceColor }.copy(alpha = 0.35f), Offset(0.25f, 0.7f), 0.65f, PI.toFloat() * 1.5f, 0.9f)
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ChromaticMist")
    val primaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "PrimaryPhase"
    )
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Restart),
        label = "SecondaryPhase"
    )
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Breathing"
    )

    val baseBackgroundColor = backgroundColor
    val dominantTint = animatedColors.getOrElse(0) { surfaceColor }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(baseBackgroundColor, dominantTint.copy(alpha = 0.15f).compositeOver(baseBackgroundColor))
                )
            )
            clouds.forEach { cloud ->
                drawColorCloud(cloud, primaryPhase, secondaryPhase, breathingScale)
            }
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, baseBackgroundColor.copy(alpha = 0.4f)),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.maxDimension * 0.8f
                )
            )
        }
    }
}

private fun DrawScope.drawColorCloud(cloud: ColorCloud, primaryPhase: Float, secondaryPhase: Float, breathingScale: Float) {
    val phase = primaryPhase * cloud.speedMultiplier + cloud.phaseOffset
    val secondPhase = secondaryPhase * cloud.speedMultiplier * 0.7f + cloud.phaseOffset
    val xOffset = sin(phase) * 0.15f + sin(phase * 1.7f + 0.3f) * 0.08f + sin(secondPhase * 0.5f) * 0.05f
    val yOffset = cos(phase * 0.8f) * 0.12f + cos(phase * 1.3f + 0.7f) * 0.08f + cos(secondPhase * 0.6f + 0.5f) * 0.05f
    val centerX = size.width * (cloud.baseOffset.x + xOffset)
    val centerY = size.height * (cloud.baseOffset.y + yOffset)
    val radius = size.maxDimension * cloud.radiusMultiplier * breathingScale
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                cloud.color,
                cloud.color.copy(alpha = cloud.color.alpha * 0.5f),
                cloud.color.copy(alpha = cloud.color.alpha * 0.2f),
                Color.Transparent
            ),
            center = Offset(centerX, centerY),
            radius = radius
        ),
        radius = radius,
        center = Offset(centerX, centerY),
        blendMode = BlendMode.Plus
    )
}

private fun Color.compositeOver(background: Color): Color {
    val a = this.alpha
    val inv = 1f - a
    return Color(red * a + background.red * inv, green * a + background.green * inv, blue * a + background.blue * inv, 1f)
}
