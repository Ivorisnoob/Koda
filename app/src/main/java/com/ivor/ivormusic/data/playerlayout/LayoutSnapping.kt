package com.ivor.ivormusic.data.playerlayout

import kotlin.math.abs

/** A rectangle in the editor's own pixels, for the snapping maths. */
data class SnapRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun offset(dx: Float, dy: Float) = SnapRect(left + dx, top + dy, right + dx, bottom + dy)
}

/** Where a dragged block ended up, and the lines it snapped to, for drawing. */
data class SnapResult(
    val rect: SnapRect,
    /** Vertical guide lines, as x positions. */
    val guidesX: List<Float>,
    /** Horizontal guide lines, as y positions. */
    val guidesY: List<Float>,
)

/** Which edges a resize handle moves. */
data class SnapEdges(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
)

/**
 * Free placement that still lines things up.
 *
 * A block being moved is pulled onto the area's edges and centre lines and onto
 * the edges and centres of the other blocks, whenever one of its own is within
 * [threshold]; resizing pulls only the edges that are moving. Each axis takes
 * the nearest line it can reach, so a block snaps on both at once. Pure and in
 * pixels, so the pull is the same distance under the finger at any zoom.
 */
object LayoutSnapping {

    fun move(
        proposed: SnapRect,
        others: List<SnapRect>,
        areaWidth: Float,
        areaHeight: Float,
        threshold: Float,
    ): SnapResult {
        val linesX = linesX(others, areaWidth)
        val linesY = linesY(others, areaHeight)
        val dx = nearestShift(listOf(proposed.left, proposed.centerX, proposed.right), linesX, threshold)
        val dy = nearestShift(listOf(proposed.top, proposed.centerY, proposed.bottom), linesY, threshold)
        val moved = proposed.offset(dx ?: 0f, dy ?: 0f)
        // Inside the area, whatever the finger did; clamping can only move it
        // onto an area edge, which is itself a line.
        val clamped = moved.offset(
            dx = when {
                moved.left < 0f -> -moved.left
                moved.right > areaWidth -> areaWidth - moved.right
                else -> 0f
            },
            dy = when {
                moved.top < 0f -> -moved.top
                moved.bottom > areaHeight -> areaHeight - moved.bottom
                else -> 0f
            }
        )
        return SnapResult(
            rect = clamped,
            guidesX = touching(listOf(clamped.left, clamped.centerX, clamped.right), linesX),
            guidesY = touching(listOf(clamped.top, clamped.centerY, clamped.bottom), linesY),
        )
    }

    fun resize(
        proposed: SnapRect,
        edges: SnapEdges,
        others: List<SnapRect>,
        areaWidth: Float,
        areaHeight: Float,
        threshold: Float,
        minSize: Float,
    ): SnapResult {
        val linesX = linesX(others, areaWidth)
        val linesY = linesY(others, areaHeight)
        fun snap(value: Float, lines: List<Float>) =
            value + (nearestShift(listOf(value), lines, threshold) ?: 0f)

        var left = if (edges.left) snap(proposed.left, linesX).coerceAtLeast(0f) else proposed.left
        var right = if (edges.right) snap(proposed.right, linesX).coerceAtMost(areaWidth) else proposed.right
        var top = if (edges.top) snap(proposed.top, linesY).coerceAtLeast(0f) else proposed.top
        var bottom = if (edges.bottom) snap(proposed.bottom, linesY).coerceAtMost(areaHeight) else proposed.bottom
        // Never smaller than the minimum: the moving edge gives way, never the
        // one held still.
        if (right - left < minSize) {
            if (edges.left) left = right - minSize else right = left + minSize
        }
        if (bottom - top < minSize) {
            if (edges.top) top = bottom - minSize else bottom = top + minSize
        }
        val rect = SnapRect(left, top, right, bottom)
        val movingX = listOfNotNull(left.takeIf { edges.left }, right.takeIf { edges.right })
        val movingY = listOfNotNull(top.takeIf { edges.top }, bottom.takeIf { edges.bottom })
        return SnapResult(rect, touching(movingX, linesX), touching(movingY, linesY))
    }

    private fun linesX(others: List<SnapRect>, areaWidth: Float): List<Float> =
        listOf(0f, areaWidth / 2f, areaWidth) + others.flatMap { listOf(it.left, it.centerX, it.right) }

    private fun linesY(others: List<SnapRect>, areaHeight: Float): List<Float> =
        listOf(0f, areaHeight / 2f, areaHeight) + others.flatMap { listOf(it.top, it.centerY, it.bottom) }

    /** The smallest shift putting any of [edges] on any of [lines], if one is within reach. */
    private fun nearestShift(edges: List<Float>, lines: List<Float>, threshold: Float): Float? {
        var best: Float? = null
        for (edge in edges) {
            for (line in lines) {
                val shift = line - edge
                if (abs(shift) <= threshold && (best == null || abs(shift) < abs(best))) best = shift
            }
        }
        return best
    }

    private fun touching(edges: List<Float>, lines: List<Float>): List<Float> =
        lines.filter { line -> edges.any { abs(it - line) < 0.5f } }.distinct()
}
