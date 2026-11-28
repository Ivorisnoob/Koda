package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.playerlayout.AreaInsets
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutGeometry
import com.ivor.ivormusic.data.playerlayout.LayoutSnapping
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.data.playerlayout.SnapEdges
import com.ivor.ivormusic.data.playerlayout.SnapRect
import com.ivor.ivormusic.data.playerlayout.withBlock
import com.ivor.ivormusic.ui.player.CustomPlayerCanvas
import com.ivor.ivormusic.ui.player.LayoutHost
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.areaInsetsPx
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.abs
import kotlin.math.hypot

/** Pull distance for snapping, on screen. */
private val SNAP_DISTANCE = 8.dp

/** A block is never resized smaller than this, at the player's real size. */
private val MIN_BLOCK = 24.dp

/** Every block can be grabbed by at least this much, on screen, however small it is drawn. */
private val MIN_TOUCH = 40.dp

/** How far from a handle's centre a touch still takes it, on screen. */
private val HANDLE_REACH = 22.dp

private val HANDLE_RADIUS = 7.dp

/**
 * The player at its real size, scaled down to fit, with the editing layer over
 * it.
 *
 * The preview is the real renderer laid out at the size of the player on this
 * phone and only scaled in its graphics layer, so text sizes, insets and the
 * fractions blocks are stored as are exactly what the player will use. The
 * editing layer lives inside the same scaled layer, so touches arrive in the
 * player's own pixels; anything meant to be a fixed size on screen (handles,
 * the snap distance, touch slop) is divided by the scale.
 */
@Composable
internal fun EditorCanvas(
    layout: PlayerLayout,
    selectedId: String?,
    viewModel: PlayerViewModel,
    canvasSize: DpSize,
    safeArea: PaddingValues,
    ambientBackground: Boolean,
    playerScheme: ColorScheme,
    onSelect: (String?) -> Unit,
    onOpenConfig: (String) -> Unit,
    /** A drag or resize began from [before]; record it for undo. */
    onGestureStart: (before: PlayerLayout) -> Unit,
    /** Every frame of a drag or resize; no undo step per frame. */
    onLayoutChange: (PlayerLayout) -> Unit,
    onCanvasPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val chrome = MaterialTheme.colorScheme
    val haptics = rememberKodaHaptics()
    val host = remember(viewModel) {
        LayoutHost(
            viewModel = viewModel,
            interactive = false,
            onAction = {},
            onArtistClick = {},
            lyricsOpen = false,
            sleepTimerActive = false,
            swipeToSkip = null,
        )
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / canvasSize.width, maxHeight / canvasSize.height)
            .coerceIn(0.05f, 1f)
        // Sizes meant for the screen, in the player's own pixels.
        val perScreenPx = 1f / scale
        val snapPx = with(density) { SNAP_DISTANCE.toPx() } * perScreenPx
        val minBlockPx = with(density) { MIN_BLOCK.toPx() }
        val minTouchPx = with(density) { MIN_TOUCH.toPx() } * perScreenPx
        val reachPx = with(density) { HANDLE_REACH.toPx() } * perScreenPx
        val insets = areaInsetsPx(safeArea, direction, density)
        val canvasWidthPx = with(density) { canvasSize.width.toPx() }
        val canvasHeightPx = with(density) { canvasSize.height.toPx() }
        val areaWidth = (canvasWidthPx - insets.left - insets.right).coerceAtLeast(1f)
        val areaHeight = (canvasHeightPx - insets.top - insets.bottom).coerceAtLeast(1f)

        // The gesture below is keyed on the geometry only, so everything it
        // reads that changes between gestures comes through these.
        val currentLayout by rememberUpdatedState(layout)
        val currentSelected by rememberUpdatedState(selectedId)
        val select by rememberUpdatedState(onSelect)
        val openConfig by rememberUpdatedState(onOpenConfig)
        val gestureStart by rememberUpdatedState(onGestureStart)
        val layoutChange by rememberUpdatedState(onLayoutChange)
        var guidesX by remember { mutableStateOf(emptyList<Float>()) }
        var guidesY by remember { mutableStateOf(emptyList<Float>()) }

        Box(
            modifier = Modifier
                .requiredSize(canvasSize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    clip = true
                    shape = RoundedCornerShape(24.dp * perScreenPx)
                }
        ) {
            // The player's own scheme (the cover's, with album colours on);
            // the editing marks below keep the app's.
            MaterialTheme(colorScheme = playerScheme) {
                CustomPlayerCanvas(
                    layout = layout,
                    host = host,
                    safeArea = safeArea,
                    ambientBackground = ambientBackground,
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned(onCanvasPositioned)
                    .pointerInput(areaWidth, areaHeight, scale) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = currentLayout
                            val point = Offset(down.position.x - insets.left, down.position.y - insets.top)
                            val selected = start.blocks.firstOrNull { it.id == currentSelected }
                            val handle = selected?.let {
                                handleAt(LayoutGeometry.areaRect(it, areaWidth, areaHeight), point, reachPx)
                            }
                            val target: LayoutBlock? = when {
                                handle != null -> selected
                                selected != null && LayoutGeometry.areaRect(selected, areaWidth, areaHeight)
                                    .grown(minTouchPx).contains(point) -> selected
                                else -> start.blocks.lastOrNull {
                                    LayoutGeometry.areaRect(it, areaWidth, areaHeight).grown(minTouchPx).contains(point)
                                }
                            }
                            val startRect = target?.let { LayoutGeometry.areaRect(it, areaWidth, areaHeight) }
                            val others = start.blocks
                                .filter { it.id != target?.id }
                                .map { LayoutGeometry.areaRect(it, areaWidth, areaHeight) }
                            val slop = viewConfiguration.touchSlop * perScreenPx
                            var total = Offset.Zero
                            var dragging = false
                            var snapped = false

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (!dragging) {
                                        when {
                                            target == null -> select(null)
                                            target.id == currentSelected && handle == null -> openConfig(target.id)
                                            else -> select(target.id)
                                        }
                                    }
                                    break
                                }
                                total += change.positionChange()
                                if (!dragging && target != null && hypot(total.x, total.y) > slop) {
                                    dragging = true
                                    gestureStart(start)
                                    if (target.id != currentSelected) select(target.id)
                                }
                                if (dragging && target != null && startRect != null) {
                                    change.consume()
                                    val result = if (handle == null) {
                                        LayoutSnapping.move(
                                            startRect.offset(total.x, total.y),
                                            others, areaWidth, areaHeight, snapPx
                                        )
                                    } else {
                                        LayoutSnapping.resize(
                                            startRect.movedEdges(handle, total),
                                            handle, others, areaWidth, areaHeight, snapPx, minBlockPx
                                        )
                                    }
                                    val nowSnapped = result.guidesX.isNotEmpty() || result.guidesY.isNotEmpty()
                                    if (nowSnapped && !snapped) haptics.tick()
                                    snapped = nowSnapped
                                    guidesX = result.guidesX
                                    guidesY = result.guidesY
                                    layoutChange(
                                        start.withBlock(
                                            LayoutGeometry.withAreaRect(target, result.rect, areaWidth, areaHeight)
                                        )
                                    )
                                }
                            }
                            guidesX = emptyList()
                            guidesY = emptyList()
                        }
                    }
            ) {
                drawEditorChrome(
                    layout = layout,
                    selectedId = selectedId,
                    insets = insets,
                    areaWidth = areaWidth,
                    areaHeight = areaHeight,
                    perScreenPx = perScreenPx,
                    guidesX = guidesX,
                    guidesY = guidesY,
                    barTint = chrome.scrim.copy(alpha = 0.22f),
                    outline = chrome.outline,
                    accent = chrome.primary,
                    onAccent = chrome.onPrimary,
                    guide = chrome.tertiary,
                )
            }
        }
    }
}

private fun SnapRect.grown(minSide: Float): SnapRect {
    val padX = ((minSide - width) / 2f).coerceAtLeast(0f)
    val padY = ((minSide - height) / 2f).coerceAtLeast(0f)
    return SnapRect(left - padX, top - padY, right + padX, bottom + padY)
}

private fun SnapRect.contains(point: Offset) =
    point.x in left..right && point.y in top..bottom

private fun SnapRect.movedEdges(edges: SnapEdges, delta: Offset) = SnapRect(
    left = if (edges.left) left + delta.x else left,
    top = if (edges.top) top + delta.y else top,
    right = if (edges.right) right + delta.x else right,
    bottom = if (edges.bottom) bottom + delta.y else bottom,
)

/** The handles of a selected block: four corners, and the middle of each side long enough to hold one. */
private fun handlesOf(rect: SnapRect, reach: Float): List<Pair<Offset, SnapEdges>> {
    val corners = listOf(
        Offset(rect.left, rect.top) to SnapEdges(left = true, top = true),
        Offset(rect.right, rect.top) to SnapEdges(right = true, top = true),
        Offset(rect.left, rect.bottom) to SnapEdges(left = true, bottom = true),
        Offset(rect.right, rect.bottom) to SnapEdges(right = true, bottom = true),
    )
    val sides = buildList {
        if (rect.width > reach * 4f) {
            add(Offset(rect.centerX, rect.top) to SnapEdges(top = true))
            add(Offset(rect.centerX, rect.bottom) to SnapEdges(bottom = true))
        }
        if (rect.height > reach * 4f) {
            add(Offset(rect.left, rect.centerY) to SnapEdges(left = true))
            add(Offset(rect.right, rect.centerY) to SnapEdges(right = true))
        }
    }
    return corners + sides
}

/** The handle under [point], corners first, or null. */
private fun handleAt(rect: SnapRect, point: Offset, reach: Float): SnapEdges? =
    handlesOf(rect, reach)
        .filter { (center, _) -> abs(center.x - point.x) <= reach && abs(center.y - point.y) <= reach }
        .minByOrNull { (center, _) -> hypot(center.x - point.x, center.y - point.y) }
        ?.second

private fun DrawScope.drawEditorChrome(
    layout: PlayerLayout,
    selectedId: String?,
    insets: AreaInsets,
    areaWidth: Float,
    areaHeight: Float,
    perScreenPx: Float,
    guidesX: List<Float>,
    guidesY: List<Float>,
    barTint: androidx.compose.ui.graphics.Color,
    outline: androidx.compose.ui.graphics.Color,
    accent: androidx.compose.ui.graphics.Color,
    onAccent: androidx.compose.ui.graphics.Color,
    guide: androidx.compose.ui.graphics.Color,
) {
    val px = 1.dp.toPx() * perScreenPx
    // The status and navigation bars: blocks there sit under the system's own.
    if (insets.top > 0f) drawRect(barTint, size = Size(size.width, insets.top))
    if (insets.bottom > 0f) {
        drawRect(
            barTint,
            topLeft = Offset(0f, size.height - insets.bottom),
            size = Size(size.width, insets.bottom)
        )
    }
    val dashes = PathEffect.dashPathEffect(floatArrayOf(5f * px, 4f * px))
    val corner = CornerRadius(6f * px)
    layout.blocks.forEach { block ->
        if (block.id == selectedId) return@forEach
        val rect = LayoutGeometry.areaRect(block, areaWidth, areaHeight)
        drawRoundRect(
            color = outline.copy(alpha = 0.55f),
            topLeft = Offset(insets.left + rect.left, insets.top + rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = corner,
            style = Stroke(width = 1f * px, pathEffect = dashes)
        )
    }
    guidesX.forEach { x ->
        drawLine(guide, Offset(insets.left + x, 0f), Offset(insets.left + x, size.height), strokeWidth = 1.5f * px)
    }
    guidesY.forEach { y ->
        drawLine(guide, Offset(0f, insets.top + y), Offset(size.width, insets.top + y), strokeWidth = 1.5f * px)
    }
    val selected = layout.blocks.firstOrNull { it.id == selectedId } ?: return
    val rect = LayoutGeometry.areaRect(selected, areaWidth, areaHeight)
    val origin = Offset(insets.left + rect.left, insets.top + rect.top)
    drawRoundRect(
        color = accent,
        topLeft = origin,
        size = Size(rect.width, rect.height),
        cornerRadius = corner,
        style = Stroke(width = 2f * px)
    )
    val radius = HANDLE_RADIUS.toPx() * perScreenPx
    handlesOf(rect, HANDLE_REACH.toPx() * perScreenPx).forEach { (center, _) ->
        val at = Offset(insets.left + center.x, insets.top + center.y)
        drawCircle(onAccent, radius = radius + 2f * px, center = at)
        drawCircle(accent, radius = radius, center = at)
    }
}
