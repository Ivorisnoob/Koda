package com.ivor.ivormusic.data.playerlayout

/** The bars around the safe area, in pixels: status bar on top, navigation below. */
data class AreaInsets(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Where blocks land on a real screen.
 *
 * A block is stored as fractions of the safe area. Covers and panels that touch
 * an edge of it run on to the screen's edge underneath the bar, so a cover put
 * against the top fills behind the status bar instead of stopping short of it.
 */
object LayoutGeometry {

    /** Within this of an edge counts as touching it. */
    const val EDGE = 0.002f

    /** The block's rectangle in the whole screen's pixels. */
    fun bounds(block: LayoutBlock, width: Float, height: Float, insets: AreaInsets): SnapRect {
        val areaWidth = (width - insets.left - insets.right).coerceAtLeast(0f)
        val areaHeight = (height - insets.top - insets.bottom).coerceAtLeast(0f)
        var left = insets.left + block.x * areaWidth
        var top = insets.top + block.y * areaHeight
        var right = insets.left + block.right * areaWidth
        var bottom = insets.top + block.bottom * areaHeight
        if (block.kind.bleeds) {
            if (block.x <= EDGE) left = 0f
            if (block.y <= EDGE) top = 0f
            if (block.right >= 1f - EDGE) right = width
            if (block.bottom >= 1f - EDGE) bottom = height
        }
        return SnapRect(left, top, right, bottom)
    }

    /** The block in the safe area's own pixels, ignoring any bleed: what the editor moves. */
    fun areaRect(block: LayoutBlock, areaWidth: Float, areaHeight: Float) = SnapRect(
        block.x * areaWidth,
        block.y * areaHeight,
        block.right * areaWidth,
        block.bottom * areaHeight,
    )

    /** A rectangle in the safe area's pixels, written back onto the block as fractions. */
    fun withAreaRect(block: LayoutBlock, rect: SnapRect, areaWidth: Float, areaHeight: Float): LayoutBlock {
        if (areaWidth <= 0f || areaHeight <= 0f) return block
        return block.copy(
            x = rect.left / areaWidth,
            y = rect.top / areaHeight,
            width = rect.width / areaWidth,
            height = rect.height / areaHeight,
        ).clampedToArea()
    }

    /**
     * A rectangle measured on a whole screen of [width] x [height] pixels,
     * written onto [block] as fractions of the safe area. Each edge is taken
     * on its own and kept inside the area, so a piece that runs under a bar
     * (a cover drawn up to the top of the screen) is stored touching that edge
     * and bleeds back out to it when drawn.
     */
    fun fromScreenRect(block: LayoutBlock, rect: SnapRect, width: Float, height: Float, insets: AreaInsets): LayoutBlock {
        val areaWidth = width - insets.left - insets.right
        val areaHeight = height - insets.top - insets.bottom
        if (areaWidth <= 0f || areaHeight <= 0f) return block
        val left = ((rect.left - insets.left) / areaWidth).coerceIn(0f, 1f)
        val top = ((rect.top - insets.top) / areaHeight).coerceIn(0f, 1f)
        val right = ((rect.right - insets.left) / areaWidth).coerceIn(0f, 1f)
        val bottom = ((rect.bottom - insets.top) / areaHeight).coerceIn(0f, 1f)
        return block.copy(x = left, y = top, width = right - left, height = bottom - top).clampedToArea()
    }

    /**
     * Where the playback controls begin, as a fraction of the safe area: the
     * top of the highest seek bar, control group or skip/play button in the
     * lower part of the screen. Lyrics opened from a button fill the screen
     * above it, so the controls stay where they are and reachable.
     */
    fun controlsTop(layout: PlayerLayout): Float =
        layout.blocks
            .filter { block ->
                block.y >= 0.35f && (
                    block.kind == LayoutBlockKind.SCRUBBER ||
                        block.kind == LayoutBlockKind.TRANSPORT ||
                        (block.kind == LayoutBlockKind.BUTTON && block.action in CONTROL_ACTIONS)
                    )
            }
            .minOfOrNull { it.y }
            ?: 1f

    private val CONTROL_ACTIONS = setOf(
        LayoutAction.PLAY_PAUSE, LayoutAction.PREVIOUS, LayoutAction.NEXT,
        LayoutAction.SEEK_BACK, LayoutAction.SEEK_FORWARD
    )
}
