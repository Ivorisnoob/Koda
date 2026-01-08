package com.ivor.ivormusic.ui.player

import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chromatic Mist - Premium ambient background inspired by Apple Music
 * 
 * Creates a beautiful, organic animated background that extracts colors
 * from album artwork and displays them as slowly drifting, heavily blurred
 * color clouds that create an immersive atmosphere.
 * 
 * Features:
 * - Color extraction from album art using Palette API
 * - Multiple layered gradient clouds with independent animations
 * - Organic, Perlin-noise-like movement patterns
 * - Smooth color transitions when songs change
 * - Breathing/pulsing effect for a living feel
 * - Full Material 3 theme support
 */

data class ColorCloud(
    val color: Color,
    val baseOffset: Offset,      // Center position (0-1 normalized)
    val radiusMultiplier: Float, // Size relative to screen (0.3 - 0.8)
    val phaseOffset: Float,      // Animation phase offset for variety
    val speedMultiplier: Float   // Movement speed variation
)

@Composable
fun ChromaticMistBackground(
    albumArtUrl: String?,
    enabled: Boolean = true,
    fallbackColor: Color? = null, // null = use MaterialTheme.colorScheme.background
    modifier: Modifier = Modifier
) {
    // Get M3 theme colors
    val themeBackground = MaterialTheme.colorScheme.background
    val backgroundColor = fallbackColor ?: themeBackground
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // If disabled or no album art, show simple fallback with M3 colors
    if (!enabled || albumArtUrl == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor)
        )
        return
    }
    
    val context = LocalContext.current
    
    // Extracted colors state - starts with theme-aware defaults
    val defaultColors = listOf(
        surfaceColor,
        surfaceContainer,
        surfaceContainerHigh,
        primaryColor.copy(alpha = 0.3f)
    )
    
    var colorPalette by remember { mutableStateOf(defaultColors) }
    
    // Extract colors from album art
    LaunchedEffect(albumArtUrl) {
        val colors = extractColorsFromUrl(context, albumArtUrl)
        if (colors.isNotEmpty()) {
            colorPalette = colors
        }
    }
    
    // Animate color transitions smoothly
    val animatedColors = colorPalette.mapIndexed { index: Int, targetColor: Color ->
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            label = "ColorTransition$index"
        ).value
    }
    
    // Create color clouds with the animated colors
    val clouds = remember(animatedColors) {
        listOf(
            // Large dominant color cloud - top left
            ColorCloud(
                color = animatedColors.getOrElse(0) { surfaceColor }.copy(alpha = 0.6f),
                baseOffset = Offset(0.2f, 0.15f),
                radiusMultiplier = 0.9f,
                phaseOffset = 0f,
                speedMultiplier = 1f
            ),
            // Secondary color - bottom right
            ColorCloud(
                color = animatedColors.getOrElse(1) { surfaceColor }.copy(alpha = 0.5f),
                baseOffset = Offset(0.8f, 0.85f),
                radiusMultiplier = 0.85f,
                phaseOffset = PI.toFloat() * 0.5f,
                speedMultiplier = 0.8f
            ),
            // Accent color - center right
            ColorCloud(
                color = animatedColors.getOrElse(2) { surfaceColor }.copy(alpha = 0.4f),
                baseOffset = Offset(0.75f, 0.3f),
                radiusMultiplier = 0.7f,
                phaseOffset = PI.toFloat(),
                speedMultiplier = 1.2f
            ),
            // Fourth color - bottom left
            ColorCloud(
                color = animatedColors.getOrElse(3) { surfaceColor }.copy(alpha = 0.35f),
                baseOffset = Offset(0.25f, 0.7f),
                radiusMultiplier = 0.65f,
                phaseOffset = PI.toFloat() * 1.5f,
                speedMultiplier = 0.9f
            )
        )
    }
    
    // Infinite animation for organic movement
    val infiniteTransition = rememberInfiniteTransition(label = "ChromaticMist")
    
    // Primary movement cycle - very slow for subtle effect
    val primaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PrimaryPhase"
    )
    
    // Secondary movement cycle - offset timing for organic feel
    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 19000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SecondaryPhase"
    )
    
    // Breathing effect - subtle scale pulsing
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )
    
    // Capture colors for use in Canvas (can't access MaterialTheme inside DrawScope)
    val baseBackgroundColor = backgroundColor
    val dominantTint = animatedColors.getOrElse(0) { surfaceColor }
    
    Box(modifier = modifier.fillMaxSize()) {
        // M3-themed base layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Use theme background with subtle color tint from album
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        baseBackgroundColor,
                        dominantTint
                            .copy(alpha = 0.15f)
                            .compositeOver(baseBackgroundColor)
                    )
                )
            )
            
            // Draw each cloud with organic movement
            clouds.forEach { cloud ->
                drawColorCloud(
                    cloud = cloud,
                    primaryPhase = primaryPhase,
                    secondaryPhase = secondaryPhase,
                    breathingScale = breathingScale
                )
            }
            
            // Subtle vignette overlay for depth (theme-aware)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        baseBackgroundColor.copy(alpha = 0.4f)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.4f),
                    radius = size.maxDimension * 0.8f
                )
            )
        }
    }
}

/**
 * Draw a single color cloud with organic movement
 */
private fun DrawScope.drawColorCloud(
    cloud: ColorCloud,
    primaryPhase: Float,
    secondaryPhase: Float,
    breathingScale: Float
) {
    // Calculate organic offset using layered sine waves (pseudo-Perlin)
    val phase = primaryPhase * cloud.speedMultiplier + cloud.phaseOffset
    val secondPhase = secondaryPhase * cloud.speedMultiplier * 0.7f + cloud.phaseOffset
    
    // Multi-frequency movement for natural feel - Increased amplitude for more movement
    val xOffset = (
        sin(phase) * 0.15f +                    // Primary movement
        sin(phase * 1.7f + 0.3f) * 0.08f +      // Secondary frequency
        sin(secondPhase * 0.5f) * 0.05f         // Tertiary slow drift
    )
    
    val yOffset = (
        cos(phase * 0.8f) * 0.12f +             // Primary movement (different rate)
        cos(phase * 1.3f + 0.7f) * 0.08f +      // Secondary frequency
        cos(secondPhase * 0.6f + 0.5f) * 0.05f  // Tertiary slow drift
    )
    
    val centerX = size.width * (cloud.baseOffset.x + xOffset)
    val centerY = size.height * (cloud.baseOffset.y + yOffset)
    val radius = size.maxDimension * cloud.radiusMultiplier * breathingScale
    
    // Draw the cloud as a large radial gradient
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

/**
 * Extract dominant colors from an image URL using Palette API
 */
private suspend fun extractColorsFromUrl(
    context: android.content.Context,
    url: String
): List<Color> = withContext(Dispatchers.IO) {
    try {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false) // Palette needs software bitmap
            .size(128) // Small size for faster processing
            .build()
        
        val result = loader.execute(request)
        if (result is SuccessResult) {
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                
                // Extract colors in priority order
                listOfNotNull(
                    palette.darkVibrantSwatch?.rgb?.let { rgb -> Color(rgb) },
                    palette.vibrantSwatch?.rgb?.let { rgb -> Color(rgb) },
                    palette.darkMutedSwatch?.rgb?.let { rgb -> Color(rgb) },
                    palette.mutedSwatch?.rgb?.let { rgb -> Color(rgb) },
                    palette.dominantSwatch?.rgb?.let { rgb -> Color(rgb) }
                ).take(4)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Blend/composite a color over another
 */
private fun Color.compositeOver(background: Color): Color {
    val alpha = this.alpha
    val inverseAlpha = 1f - alpha
    return Color(
        red = this.red * alpha + background.red * inverseAlpha,
        green = this.green * alpha + background.green * inverseAlpha,
        blue = this.blue * alpha + background.blue * inverseAlpha,
        alpha = 1f
    )
}
