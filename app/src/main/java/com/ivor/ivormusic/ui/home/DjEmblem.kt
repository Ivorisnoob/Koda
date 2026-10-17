package com.ivor.ivormusic.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The AI DJ's emblem: a minimal headphone mark drawn in code, not an image
 * asset, so it follows the dynamic theme — when album art recolors the app,
 * the emblem recolors with it.
 */
@Composable
fun DjEmblem(
    modifier: Modifier = Modifier,
    emblemSize: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    val container = MaterialTheme.colorScheme.tertiaryContainer
    val mark = MaterialTheme.colorScheme.onTertiaryContainer
    Canvas(
        modifier = modifier
            .size(emblemSize)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // DrawScope.size: the canvas in pixels; the emblem is square.
        val r = size.minDimension / 2f
        drawCircle(color = container, radius = r, center = center)

        val stroke = r * 0.16f
        val padR = r * 0.20f
        // Headband arc.
        drawArc(
            color = mark,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - r * 0.52f, center.y - r * 0.62f),
            size = androidx.compose.ui.geometry.Size(r * 1.04f, r * 1.04f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Ear pads.
        drawCircle(
            color = mark,
            radius = padR,
            center = Offset(center.x - r * 0.52f, center.y + r * 0.18f),
        )
        drawCircle(
            color = mark,
            radius = padR,
            center = Offset(center.x + r * 0.52f, center.y + r * 0.18f),
        )
        // Sound wave ticks between the pads.
        val waveColor = mark.copy(alpha = 0.85f)
        val cx = center.x
        val cy = center.y + r * 0.18f
        drawLine(
            waveColor,
            Offset(cx - r * 0.12f, cy - r * 0.10f),
            Offset(cx - r * 0.12f, cy + r * 0.10f),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round,
        )
        drawLine(
            waveColor,
            Offset(cx + r * 0.12f, cy - r * 0.10f),
            Offset(cx + r * 0.12f, cy + r * 0.10f),
            strokeWidth = stroke * 0.7f,
            cap = StrokeCap.Round,
        )
    }
}
