package com.ivor.ivormusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * True inside a [SegmentedColumn], where the gaps between segments already
 * separate the rows. The house dividers (settings, option sheets, Library
 * shortcuts) read it and draw nothing there, rather than every card having to
 * drop them by hand.
 */
val LocalInSegmentedColumn = staticCompositionLocalOf { false }

/**
 * A column of rows drawn as a Material 3 Expressive segmented list - the
 * Android 16 Settings look: each child its own container,
 * [ListItemDefaults.SegmentedGap] apart, the group's outer corners large and
 * its inner ones small.
 *
 * For cards that take arbitrary rows and cannot hand each one its index and
 * count. Children that measure to nothing (a row hidden by a collapsed
 * AnimatedVisibility, a divider) are skipped, so they never leave a stray gap
 * or take the group's rounded ends. Each child is placed in a layer clipped to
 * its segment, and the containers are drawn behind them.
 *
 * A pressed segment morphs toward fully rounded, as a SegmentedListItem's
 * pressed shape does (the OpenStream lists this copies). The column observes
 * the touch itself - hit-testing it against the segments without consuming
 * it - so the rows need no index and keep their own click handling. A drag
 * past touch slop (a scroll) lets the segment go at once.
 *
 * [contentInset] insets every child horizontally inside its segment, for
 * cards whose rows were written against a padded container.
 */
@Composable
fun SegmentedColumn(
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentInset: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val segments = remember { mutableStateOf<List<Rect>>(emptyList()) }
    val press = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    val scope = rememberCoroutineScope()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    fun shapeOf(index: Int, count: Int): Shape =
        segmentedRowShape(index, count, rounding = press[index]?.value ?: 0f)

    CompositionLocalProvider(LocalInSegmentedColumn provides true) {
        Layout(
            content = { SegmentedColumnScope.content() },
            modifier = modifier
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val index = segments.value.indexOfFirst { it.contains(down.position) }
                        if (index < 0) return@awaitEachGesture
                        val progress = press.getOrPut(index) { Animatable(0f) }
                        val pressing = scope.launch { progress.animateTo(1f, spec) }
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial)
                                .changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if ((change.position - down.position).getDistance() > slop) break
                        }
                        pressing.cancel()
                        scope.launch { progress.animateTo(0f, spec) }
                    }
                }
                .drawBehind {
                    val rects = segments.value
                    rects.forEachIndexed { index, rect ->
                        val outline = shapeOf(index, rects.size).createOutline(rect.size, layoutDirection, this)
                        translate(rect.left, rect.top) { drawOutline(outline, containerColor) }
                    }
                }
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val inset = contentInset.roundToPx()
            val childWidth = (width - inset * 2).coerceAtLeast(0)
            val childConstraints = Constraints(
                minWidth = childWidth, maxWidth = childWidth,
                minHeight = 0, maxHeight = Constraints.Infinity
            )
            val visible = measurables.map { it.measure(childConstraints) }.filter { it.height > 0 }
            val gap = ListItemDefaults.SegmentedGap.roundToPx()
            val height = visible.sumOf { it.height } + gap * (visible.size - 1).coerceAtLeast(0)
            var y = 0
            val rects = visible.map { placeable ->
                Rect(0f, y.toFloat(), width.toFloat(), (y + placeable.height).toFloat())
                    .also { y += placeable.height + gap }
            }
            // A layout-phase write read in draw: repaints the containers when a
            // row changes height without the column itself changing size.
            segments.value = rects
            layout(width, height.coerceAtLeast(0)) {
                rects.forEachIndexed { index, rect ->
                    visible[index].placeWithLayer(inset, rect.top.toInt()) {
                        // Read here, so a press morph updates the layer alone.
                        shape = shapeOf(index, rects.size)
                        clip = true
                    }
                }
            }
        }
    }
}

/**
 * The [ColumnScope] a [SegmentedColumn]'s content is written against. Weight
 * and alignment have no meaning in a segmented stack, so they pass through;
 * no caller relies on them.
 */
private object SegmentedColumnScope : ColumnScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = this
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier = this
    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier = this
    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}
