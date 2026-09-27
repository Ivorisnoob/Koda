package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * [ListItemDefaults.segmentedColors] for rows standing on [background].
 *
 * The library's segmented rows are `surface`, which is exactly what a sheet or
 * a page background already is - so on those the rows vanished and the group
 * read as loose text (feedback, September 2026). Every segmented list states
 * what it sits on and gets a container one visible step away from it: raised
 * on the page-level surfaces, recessed to `surface` on the high containers a
 * dialog uses. Selected rows keep the library's secondaryContainer.
 */
@Composable
fun segmentedColorsOver(background: androidx.compose.ui.graphics.Color): androidx.compose.material3.ListItemColors {
    val scheme = MaterialTheme.colorScheme
    val container = when (background) {
        scheme.surfaceContainerHigh, scheme.surfaceContainerHighest -> scheme.surface
        scheme.surfaceContainer -> scheme.surfaceContainerHighest
        else -> scheme.surfaceContainerHigh
    }
    return ListItemDefaults.segmentedColors(containerColor = container)
}

/** The segmented list's outer corners: `ListTokens.ContainerShape`, CornerLarge. */
private val SEGMENT_OUTER_CORNER = 16.dp

/** Its inner corners: `ListTokens.ItemContainerExpressiveShape`, CornerExtraSmall. */
private val SEGMENT_INNER_CORNER = 4.dp

/**
 * The segmented shape for a row that draws its own container rather than
 * being a [androidx.compose.material3.SegmentedListItem] - a search result, a
 * download card. The same token radii, so both kinds of row sit together.
 *
 * [continues] is true when a closing segment ([SegmentedFooterShape]: a
 * "Show more", a paging footer) follows the last row. Pass it only when that
 * footer is actually drawn, or the group ends on a square edge.
 */
fun segmentedRowShape(
    index: Int,
    count: Int,
    continues: Boolean = false,
    /**
     * 0..1 toward fully rounded - the library's pressed/selected shape
     * (CornerLarge all round), for rows that animate the morph themselves.
     */
    rounding: Float = 0f,
): Shape {
    val r = rounding.coerceIn(0f, 1f)
    fun corner(outer: Boolean) =
        if (outer) SEGMENT_OUTER_CORNER
        else SEGMENT_INNER_CORNER + (SEGMENT_OUTER_CORNER - SEGMENT_INNER_CORNER) * r
    val top = corner(index == 0)
    val bottom = corner(index == count - 1 && !continues)
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** The last segment of a group whose rows used `continues = true`. */
val SegmentedFooterShape: Shape = RoundedCornerShape(
    topStart = SEGMENT_INNER_CORNER, topEnd = SEGMENT_INNER_CORNER,
    bottomStart = SEGMENT_OUTER_CORNER, bottomEnd = SEGMENT_OUTER_CORNER
)

/**
 * [ListItemDefaults.segmentedShapes], but a row that becomes the first or last
 * of its group - because a neighbour was removed or arrived - rounds its
 * corners over a spring instead of snapping. For groups whose membership
 * changes under the user's finger, like a day in Listening history. The
 * press, selected and focus shapes are the library's own.
 */
@Composable
fun animatedSegmentedShapes(index: Int, count: Int): ListItemShapes {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val top by animateDpAsState(
        if (index == 0) SEGMENT_OUTER_CORNER else SEGMENT_INNER_CORNER, spec, label = "segmentTop"
    )
    val bottom by animateDpAsState(
        if (index == count - 1) SEGMENT_OUTER_CORNER else SEGMENT_INNER_CORNER, spec, label = "segmentBottom"
    )
    return ListItemDefaults.shapes(
        shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    )
}

/**
 * One Material 3 Expressive segmented list outside a lazy container: rows
 * [ListItemDefaults.SegmentedGap] apart, each given its index and the group
 * size so it can take `ListItemDefaults.segmentedShapes(index, count)`.
 *
 * For a short, bounded group (a top-five, a settings card, a sheet's option
 * rows). A long list stays lazy and shapes its own rows the same way; see
 * `ListeningHistoryScreen`.
 *
 * The group is one layout unit on purpose. As separate items of a list with
 * its own spacing, segmented rows inherit that spacing and stop reading as a
 * group, which is the bug Statistics had.
 */
@Composable
fun <T> SegmentedGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (index: Int, count: Int, item: T) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        items.forEachIndexed { index, item -> row(index, items.size, item) }
    }
}
