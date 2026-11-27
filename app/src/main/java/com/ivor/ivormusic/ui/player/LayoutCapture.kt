package com.ivor.ivormusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import com.ivor.ivormusic.data.playerlayout.AreaInsets
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutGeometry
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.data.playerlayout.SnapRect

/**
 * Where a built-in player put its pieces, read off the real thing.
 *
 * "Start from Hero" draws the actual Hero player out of sight at this phone's
 * size with one of these provided; every piece tagged with [captureAs] reports
 * where it landed, and those become the blocks of the new layout. So a layout
 * started from a player is that player as laid out on this screen, not a
 * hand-copied approximation of it, and it keeps matching when the player
 * changes.
 *
 * Only ever provided by the editor's capture; in the player it is null and the
 * tag is free.
 */
internal class LayoutCapture {
    private class Found(val order: Int, val template: LayoutBlock, val coordinates: LayoutCoordinates)

    private val found = mutableMapOf<String, Found>()

    /** The box the player was drawn in; positions are read relative to it. */
    var root: LayoutCoordinates? = null

    fun report(key: String, order: Int, template: LayoutBlock, coordinates: LayoutCoordinates) {
        found[key] = Found(order, template, coordinates)
    }

    /**
     * The pieces as blocks, bottom of the stack first. A piece with no size (a
     * visualizer that is switched off) is left out.
     */
    fun blocks(width: Float, height: Float, insets: AreaInsets): List<LayoutBlock> {
        val root = root?.takeIf { it.isAttached } ?: return emptyList()
        return found.values
            .sortedBy { it.order }
            .mapNotNull { piece ->
                if (!piece.coordinates.isAttached) return@mapNotNull null
                val bounds = root.localBoundingBoxOf(piece.coordinates, clipBounds = false)
                if (bounds.width < 1f || bounds.height < 1f) return@mapNotNull null
                LayoutGeometry.fromScreenRect(
                    block = piece.template.copy(id = PlayerLayout.newId()),
                    rect = SnapRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    width = width,
                    height = height,
                    insets = insets,
                )
            }
    }
}

internal val LocalLayoutCapture = staticCompositionLocalOf<LayoutCapture?> { null }

/**
 * Tags a piece of a built-in player as a block for "start from". [order] is
 * its place in the stack (lower draws first); [template] is the block it
 * becomes, its position filled in from where the piece landed.
 *
 * What is read is the slot the piece was given, not what it reports back:
 * an icon button drawn 44dp across reports its 48dp touch target, which
 * spills past its slot, and reading that made every such button 4dp bigger
 * in the layout than in the player. The pass-through layout below answers
 * with the size its constraints allowed.
 */
@Composable
internal fun Modifier.captureAs(key: String, order: Int, template: () -> LayoutBlock): Modifier {
    val capture = LocalLayoutCapture.current ?: return this
    return this
        .onGloballyPositioned { capture.report(key, order, template(), it) }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
}

/** A capture template: a block with no position yet, in [skin]. */
internal fun captureBlock(
    kind: LayoutBlockKind,
    skin: LayoutSkin,
    configure: LayoutBlock.() -> LayoutBlock = { this },
): LayoutBlock = LayoutBlock(
    id = "",
    kind = kind,
    x = 0f,
    y = 0f,
    width = 0f,
    height = 0f,
    skin = skin,
).configure()
