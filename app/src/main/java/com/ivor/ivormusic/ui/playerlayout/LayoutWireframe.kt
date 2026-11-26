package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutBackground
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutColor
import com.ivor.ivormusic.data.playerlayout.LayoutTextAlign
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.ui.player.composeShape

/** The share of the thumbnail given to each system bar, so blocks sit where they will. */
private const val BAR_FRACTION = 0.04f

/**
 * A small drawing of a layout, made from its own blocks: what is shown is
 * exactly what would be built, not a picture of a style. Cheap enough for a
 * grid of them - one Canvas, no artwork, no player. Draw it at the phone's
 * proportions (about 1:2).
 */
@Composable
internal fun LayoutWireframe(layout: PlayerLayout, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // Shapes are composable to build; a fixed list, resolved once per draw.
    val shapes: Map<BlockShape, Shape> = BlockShape.entries.associateWith { it.composeShape() }
    Canvas(modifier = modifier) {
        val ground = when (layout.background) {
            LayoutBackground.TONAL -> scheme.primaryContainer
            else -> scheme.surface
        }
        val ink = if (layout.background == LayoutBackground.TONAL) scheme.onPrimaryContainer else scheme.onSurface
        when (layout.background) {
            LayoutBackground.AMBIENT -> drawRect(
                Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.surface, scheme.tertiaryContainer),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            LayoutBackground.COVER -> drawRect(
                Brush.verticalGradient(listOf(scheme.primary, scheme.primary, scheme.surface))
            )
            else -> drawRect(ground)
        }

        val top = size.height * BAR_FRACTION
        val areaWidth = size.width
        val areaHeight = size.height * (1f - 2 * BAR_FRACTION)
        layout.blocks.forEach { block ->
            var left = block.x * areaWidth
            var y = top + block.y * areaHeight
            var right = block.right * areaWidth
            var bottom = top + block.bottom * areaHeight
            if (block.kind.bleeds) {
                if (block.x <= 0.002f) left = 0f
                if (block.y <= 0.002f) y = 0f
                if (block.right >= 0.998f) right = size.width
                if (block.bottom >= 0.998f) bottom = size.height
            }
            drawBlock(block, Offset(left, y), Size(right - left, bottom - y), shapes, scheme, ink)
        }
    }
}

private fun DrawScope.drawBlock(
    block: LayoutBlock,
    origin: Offset,
    size: Size,
    shapes: Map<BlockShape, Shape>,
    scheme: androidx.compose.material3.ColorScheme,
    ink: Color,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val accent = when (block.color) {
        LayoutColor.PRIMARY -> scheme.primary
        LayoutColor.SECONDARY -> scheme.secondary
        LayoutColor.TERTIARY -> scheme.tertiary
        LayoutColor.NEUTRAL -> ink
    }
    val container = when (block.color) {
        LayoutColor.PRIMARY -> scheme.primaryContainer
        LayoutColor.SECONDARY -> scheme.secondaryContainer
        LayoutColor.TERTIARY -> scheme.tertiaryContainer
        LayoutColor.NEUTRAL -> scheme.surfaceContainerHighest
    }
    val line = ink.copy(alpha = 0.45f)
    val stroke = size.minDimension.coerceAtMost(4f).coerceAtLeast(1.5f)

    fun shapeIn(shape: BlockShape, at: Offset, s: Size, color: Color, square: Boolean, outline: Boolean = false) {
        val side = minOf(s.width, s.height)
        val box = if (square) Size(side, side) else s
        val offset = if (square) Offset(at.x + (s.width - side) / 2f, at.y + (s.height - side) / 2f) else at
        val path = (shapes[shape] ?: return).createOutline(box, LayoutDirection.Ltr, this)
        translate(offset.x, offset.y) {
            if (outline) drawOutline(path, color, style = Stroke(stroke)) else drawOutline(path, color)
        }
    }

    fun bar(x: Float, y: Float, w: Float, h: Float, color: Color) {
        drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(h / 2f))
    }

    when (block.kind) {
        LayoutBlockKind.COVER -> shapeIn(
            block.shape, origin, size, scheme.primary,
            square = block.shape == BlockShape.ROUND || block.shape.isPolygon
        )
        LayoutBlockKind.PANEL -> shapeIn(block.shape, origin, size, container, square = false)
        LayoutBlockKind.TITLE -> {
            val h = (size.height * 0.22f).coerceAtLeast(2f)
            val titleWidth = size.width * 0.8f
            val artistWidth = size.width * 0.5f
            fun startFor(w: Float) = when (block.align) {
                LayoutTextAlign.START -> origin.x
                LayoutTextAlign.CENTER -> origin.x + (size.width - w) / 2f
                LayoutTextAlign.END -> origin.x + size.width - w
            }
            val middle = origin.y + size.height / 2f
            bar(startFor(titleWidth), middle - h * 1.2f, titleWidth, h, ink.copy(alpha = 0.8f))
            if (block.showDetail) bar(startFor(artistWidth), middle + h * 0.4f, artistWidth, h * 0.7f, line)
        }
        LayoutBlockKind.SCRUBBER -> {
            val y = origin.y + size.height * 0.4f
            val h = stroke
            val tint = if (block.color == LayoutColor.NEUTRAL) ink else accent
            bar(origin.x, y - h / 2f, size.width, h, tint.copy(alpha = 0.25f))
            bar(origin.x, y - h / 2f, size.width * 0.4f, h, tint)
            drawCircle(tint, radius = h * 1.4f, center = Offset(origin.x + size.width * 0.4f, y))
        }
        LayoutBlockKind.TRANSPORT -> {
            val gap = size.width * 0.04f
            val unit = (size.width - gap * 2f) / 3.6f
            val skip = Size(unit, size.height)
            val play = Size(unit * 1.6f, size.height)
            val skipShape = if (block.shape.isPolygon) BlockShape.ROUND else block.shape
            shapeIn(skipShape, origin, skip, container, square = false)
            shapeIn(
                block.shape, Offset(origin.x + unit + gap, origin.y), play,
                if (block.look == ButtonLook.PLAIN) line else accent,
                square = block.shape.isPolygon
            )
            shapeIn(skipShape, Offset(origin.x + unit * 2.6f + gap * 2f, origin.y), skip, container, square = false)
        }
        LayoutBlockKind.BUTTON -> when (block.look) {
            ButtonLook.FILLED -> shapeIn(block.shape, origin, size, accent, square = block.shape.isPolygon)
            ButtonLook.TONAL -> shapeIn(block.shape, origin, size, container, square = block.shape.isPolygon)
            ButtonLook.OUTLINED -> shapeIn(block.shape, origin, size, accent, square = block.shape.isPolygon, outline = true)
            ButtonLook.PLAIN -> drawCircle(
                if (block.color == LayoutColor.NEUTRAL) line else accent,
                radius = size.minDimension * 0.22f,
                center = Offset(origin.x + size.width / 2f, origin.y + size.height / 2f)
            )
        }
        LayoutBlockKind.LYRICS -> {
            val rows = 6
            val h = (size.height / (rows * 2.2f)).coerceAtLeast(1.5f)
            val widths = floatArrayOf(0.7f, 0.85f, 0.6f, 0.9f, 0.5f, 0.75f)
            repeat(rows) { i ->
                val y = origin.y + size.height * (i + 0.6f) / (rows + 0.6f)
                bar(
                    origin.x + size.width * 0.08f, y, size.width * 0.84f * widths[i], h,
                    if (i == 1) ink.copy(alpha = 0.85f) else line
                )
            }
        }
        LayoutBlockKind.VISUALIZER -> {
            val count = 9
            val w = size.width / (count * 1.6f)
            repeat(count) { i ->
                val level = 0.3f + 0.6f * ((i * 37 % 10) / 10f)
                val h = size.height * level
                drawRoundRect(
                    accent.copy(alpha = 0.7f),
                    Offset(origin.x + i * w * 1.6f + w * 0.3f, origin.y + size.height - h),
                    Size(w, h),
                    CornerRadius(w / 2f)
                )
            }
        }
        LayoutBlockKind.UP_NEXT -> {
            drawRoundRect(container, origin, size, CornerRadius(size.height * 0.25f))
            val art = size.height * 0.64f
            val pad = (size.height - art) / 2f
            drawRoundRect(scheme.primary.copy(alpha = 0.6f), Offset(origin.x + pad, origin.y + pad), Size(art, art), CornerRadius(art * 0.2f))
            bar(origin.x + pad * 2 + art, origin.y + size.height / 2f - art * 0.12f, size.width * 0.45f, art * 0.24f, line)
        }
    }
}
