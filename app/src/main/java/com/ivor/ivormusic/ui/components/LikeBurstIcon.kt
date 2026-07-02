package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 💥 Expressive "like" heart with a one-shot burst.
 *
 * When [isFavorite] flips to true, a Burst-shaped flash expands and fades
 * behind the heart while the heart itself springs in with a bouncy
 * overshoot. Purely decorative — reduced-motion users just see the icon
 * swap (animations scale with the system animator settings).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LikeBurstIcon(
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current
) {
    val heartScale = remember { Animatable(1f) }
    val burstProgress = remember { Animatable(0f) }
    // Don't burst for the initial state (e.g. opening an already-liked song)
    var isInitial by remember { mutableStateOf(true) }

    LaunchedEffect(isFavorite) {
        if (isInitial) {
            isInitial = false
            return@LaunchedEffect
        }
        if (isFavorite) {
            launch {
                heartScale.snapTo(0.5f)
                heartScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            burstProgress.snapTo(0.01f)
            burstProgress.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing))
            burstProgress.snapTo(0f)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val p = burstProgress.value
        if (p > 0f) {
            Box(
                modifier = Modifier
                    .size(iconSize * 2.2f)
                    .graphicsLayer {
                        scaleX = 0.3f + p * 1.2f
                        scaleY = 0.3f + p * 1.2f
                        alpha = 1f - p
                    }
                    .clip(MaterialShapes.Burst.toShape())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            )
        }
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Favorite",
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                }
        )
    }
}
