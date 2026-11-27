package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.playerlayout.AreaInsets
import com.ivor.ivormusic.data.playerlayout.LayoutBackground
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Pull distance for snapping, on screen. */
private val SNAP_DISTANCE = 8.dp

/** A block is never resized smaller than this, at the player's real size. */
private val MIN_BLOCK = 24.dp

/** Every block can be grabbed by at least this much, on screen, however small it is drawn. */
private val MIN_TOUCH = 40.dp

/** How far from a handle's centre a touch still takes it, on screen. */
private val HANDLE_REACH = 22.dp

private val HANDLE_RADIUS = 7.dp

/** The remove badge on every block's top corner, on screen. */
private val BADGE_RADIUS = 11.dp

/** How far from a badge's centre a touch still takes it, on screen. */
private val BADGE_REACH = 18.dp

/** How much a block grows when it is picked up. */
private const val LIFT_SCALE = 1.04f

/**
 * The player at its real size, scaled down to fit, in edit mode.
 *
 * The preview is the real renderer laid out at the size of the player on this
 * phone and only scaled in its graphics layer, so text sizes, insets and the
 * fractions blocks are stored as are exactly what the player will use. The
 * editing layer lives inside the same scaled layer, so touches arrive in the
 * player's own pixels; anything meant to be a fixed size on screen (handles,
 * badges, the snap distance, touch slop) is divided by the scale.
 *
 * Each gesture means one thing: a tap on a block opens its settings, a drag
 * moves it, a hold lifts it and offers its menu (and a drag from there carries
 * it), the badge on its corner removes it, and the dots on the chosen block
 * resize it. A tap on the empty background asks for a new background.
 */
@Composable
internal fun EditorCanvas(
    layout: PlayerLayout,
    selectedId: String?,
    /** The block just added or copied, which grows into place. */
    arrivingId: String?,
    viewModel: PlayerViewModel,
    canvasSize: DpSize,
    safeArea: PaddingValues,
    ambientBackground: Boolean,
    playerScheme: ColorScheme,
    onSelect: (String?) -> Unit,
    onOpenConfig: (String) -> Unit,
    onTapBackground: () -> Unit,
    onRemove: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onFront: (String) -> Unit,
    onBack: (String) -> Unit,
    /** A drag or resize began from [before]; record it for undo. */
    onGestureStart: (before: PlayerLayout) -> Unit,
    /** Every frame of a drag or resize; no undo step per frame. */
    onLayoutChange: (PlayerLayout) -> Unit,
    modifier: Modifier = Modifier,
    /** Room left under the player at rest, for the toolbar floating there. */
    restBottom: Dp = 0.dp,
    /** The block whose settings are open, panned clear of the panel. */
    focusId: String? = null,
    /** How much of the bottom the settings panel covers while [focusId] is set. */
    focusBottom: Dp = 0.dp,
    /** The editing layer, inside the scaled player, for dropping things onto it. */
    onCanvasPositioned: (LayoutCoordinates) -> Unit = {},
    /** A swipe across empty space chose the next or previous background. */
    onSwipeBackground: (LayoutBackground) -> Unit = {},
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val chrome = MaterialTheme.colorScheme
    val haptics = rememberKodaHaptics()
    val scope = rememberCoroutineScope()
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

    // The badges and outlines pop in once the editor has arrived.
    val marks = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(140)
        marks.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }

    // A block being carried (dragged, or held for its menu) is lifted and
    // loses its marks, which would no longer line up with it.
    var liftedId by remember { mutableStateOf<String?>(null) }
    val lift = remember { Animatable(1f) }
    fun liftUp(id: String) {
        liftedId = id
        scope.launch { lift.animateTo(LIFT_SCALE, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
    }
    fun setDown() {
        scope.launch {
            lift.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
            // Not reached when another lift took over the animation.
            liftedId = null
        }
    }

    // The chosen block's outline and dots grow in, each time it is chosen
    // and each time it is set down.
    val selection = remember { Animatable(1f) }
    LaunchedEffect(selectedId, liftedId) {
        if (selectedId != null && liftedId == null) {
            selection.snapTo(0f)
            selection.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }

    // A new block grows into place.
    val arrival = remember { Animatable(1f) }
    LaunchedEffect(arrivingId) {
        if (arrivingId != null) {
            arrival.snapTo(0f)
            arrival.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        }
    }

    // A removed block shrinks and fades before it leaves the layout.
    var removingId by remember { mutableStateOf<String?>(null) }
    val removal = remember { Animatable(0f) }
    val remove by rememberUpdatedState(onRemove)
    fun removeBlock(id: String) {
        if (removingId != null) return
        removingId = id
        haptics.subtle()
        scope.launch {
            removal.snapTo(0f)
            removal.animateTo(1f, tween(durationMillis = 170, easing = FastOutLinearInEasing))
            remove(id)
            removingId = null
        }
    }

    // A swipe across empty space pages through backgrounds: the player
    // follows the finger a little and the background's name shows.
    val swipe = remember { Animatable(0f) }
    var swipeName by remember { mutableStateOf<LayoutBackground?>(null) }
    var swipeNameShown by remember { mutableStateOf(false) }
    var swipeEnded by remember { mutableIntStateOf(0) }
    LaunchedEffect(swipeEnded) {
        if (swipeEnded > 0) {
            delay(1100)
            swipeNameShown = false
        }
    }
    val swipeBackground by rememberUpdatedState(onSwipeBackground)

    // Snap guides fade in and out rather than blink.
    val guideAlpha = remember { Animatable(0f) }

    // The block whose menu is open, and where the finger was, on screen.
    var menuFor by remember { mutableStateOf<String?>(null) }
    var menuAt by remember { mutableStateOf(Offset.Zero) }

    val currentArriving by rememberUpdatedState(arrivingId)
    val blockModifier: (LayoutBlock) -> Modifier = remember {
        { block ->
            Modifier.graphicsLayer {
                var scale = 1f
                var alpha = 1f
                if (block.id == liftedId) scale *= lift.value
                if (block.id == currentArriving) {
                    val p = arrival.value
                    scale *= 0.82f + 0.18f * p
                    alpha *= p.coerceIn(0f, 1f)
                }
                if (block.id == removingId) {
                    val p = removal.value
                    scale *= 1f - 0.22f * p
                    alpha *= 1f - p
                }
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val restHeight = (maxHeight - restBottom).coerceAtLeast(1.dp)
        val scale = minOf(maxWidth / canvasSize.width, restHeight / canvasSize.height)
            .coerceIn(0.05f, 1f)
        // Sizes meant for the screen, in the player's own pixels.
        val perScreenPx = 1f / scale
        val snapPx = with(density) { SNAP_DISTANCE.toPx() } * perScreenPx
        val minBlockPx = with(density) { MIN_BLOCK.toPx() }
        val minTouchPx = with(density) { MIN_TOUCH.toPx() } * perScreenPx
        val reachPx = with(density) { HANDLE_REACH.toPx() } * perScreenPx
        val badgePx = with(density) { BADGE_RADIUS.toPx() } * perScreenPx
        val badgeReachPx = with(density) { BADGE_REACH.toPx() } * perScreenPx
        val insets = areaInsetsPx(safeArea, direction, density)
        val canvasWidthPx = with(density) { canvasSize.width.toPx() }
        val canvasHeightPx = with(density) { canvasSize.height.toPx() }
        val areaWidth = (canvasWidthPx - insets.left - insets.right).coerceAtLeast(1f)
        val areaHeight = (canvasHeightPx - insets.top - insets.bottom).coerceAtLeast(1f)
        // At rest the player is centred in the room above the toolbar; it is
        // laid out centred in the whole box and scaled about its centre, then
        // moved up by half the toolbar's room.
        val restShift = -with(density) { restBottom.toPx() } / 2f
        val shownLeft = (constraints.maxWidth - canvasWidthPx * scale) / 2f
        val shownTop = (constraints.maxHeight - canvasHeightPx * scale) / 2f + restShift

        // With a block's settings open the player slides up just far enough
        // for the block to sit clear of the panel, keeping its top in view.
        // Worked out once per block, so dragging it does not move the page.
        val boxHeight = constraints.maxHeight.toFloat()
        val focusBottomPx = with(density) { focusBottom.toPx() }
        val focusMargin = with(density) { 16.dp.toPx() }
        val focusTarget = remember(focusId, scale, boxHeight, focusBottomPx, shownTop) {
            val block = focusId?.let { id -> layout.blocks.firstOrNull { it.id == id } }
            if (block == null) {
                0f
            } else {
                val rect = LayoutGeometry.areaRect(block, areaWidth, areaHeight)
                val top = shownTop + (insets.top + rect.top) * scale
                val bottom = shownTop + (insets.top + rect.bottom) * scale
                val visibleBottom = boxHeight - focusBottomPx
                var dy = 0f
                if (bottom + focusMargin > visibleBottom) dy = visibleBottom - focusMargin - bottom
                if (dy < 0f && top + dy < focusMargin) dy = minOf(0f, focusMargin - top)
                dy
            }
        }
        val focusShift by animateFloatAsState(
            targetValue = focusTarget,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "EditorFocusShift"
        )

        // The gesture below is keyed on the geometry only, so everything it
        // reads that changes between gestures comes through these.
        val currentLayout by rememberUpdatedState(layout)
        val currentSelected by rememberUpdatedState(selectedId)
        val select by rememberUpdatedState(onSelect)
        val openConfig by rememberUpdatedState(onOpenConfig)
        val tapBackground by rememberUpdatedState(onTapBackground)
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
                    translationY = restShift + focusShift
                    translationX = swipe.value
                    clip = true
                    shape = RoundedCornerShape(28.dp * perScreenPx)
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
                    blockModifier = blockModifier,
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
                            val slop = viewConfiguration.touchSlop * perScreenPx
                            val live = start.blocks.filter { it.id != removingId }

                            // The remove badges sit over everything, top of the stack first.
                            val badged = live.lastOrNull { block ->
                                val center = badgeCenter(
                                    LayoutGeometry.areaRect(block, areaWidth, areaHeight),
                                    insets, canvasWidthPx, canvasHeightPx, badgePx
                                )
                                hypot(down.position.x - center.x, down.position.y - center.y) <= badgeReachPx
                            }
                            if (badged != null) {
                                down.consume()
                                var moved = Offset.Zero
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                    if (!change.pressed) {
                                        if (hypot(moved.x, moved.y) <= slop) removeBlock(badged.id)
                                        return@awaitEachGesture
                                    }
                                    moved += change.positionChange()
                                    change.consume()
                                }
                            }

                            val point = Offset(down.position.x - insets.left, down.position.y - insets.top)
                            val selected = live.firstOrNull { it.id == currentSelected }
                            val handle = selected?.let {
                                handleAt(LayoutGeometry.areaRect(it, areaWidth, areaHeight), point, reachPx)
                            }
                            val target: LayoutBlock? = when {
                                handle != null -> selected
                                selected != null && LayoutGeometry.areaRect(selected, areaWidth, areaHeight)
                                    .grown(minTouchPx).contains(point) -> selected
                                else -> live.lastOrNull {
                                    LayoutGeometry.areaRect(it, areaWidth, areaHeight).grown(minTouchPx).contains(point)
                                }
                            }
                            val startRect = target?.let { LayoutGeometry.areaRect(it, areaWidth, areaHeight) }
                            val others = start.blocks
                                .filter { it.id != target?.id }
                                .map { LayoutGeometry.areaRect(it, areaWidth, areaHeight) }
                            val holdMillis = viewConfiguration.longPressTimeoutMillis
                            var lastUptime = down.uptimeMillis
                            var total = Offset.Zero
                            var dragging = false
                            var held = false
                            var snapped = false
                            var paging = false
                            var pageTo: LayoutBackground? = null
                            val pagePx = with(density) { 72.dp.toPx() } * perScreenPx
                            val followMax = with(density) { 40.dp.toPx() }

                            try {
                                while (true) {
                                    // Until it moves or lets go, a finger on a block is
                                    // waiting to become a hold.
                                    val waiting = target != null && handle == null && !dragging && !held
                                    val event = if (waiting) {
                                        val left = holdMillis - (lastUptime - down.uptimeMillis)
                                        if (left <= 0L) null else withTimeoutOrNull(left) {
                                            awaitPointerEvent(PointerEventPass.Main)
                                        }
                                    } else {
                                        awaitPointerEvent(PointerEventPass.Main)
                                    }
                                    if (event == null) {
                                        // Held still: lift it and offer its menu, under the finger.
                                        held = true
                                        val heldBlock = target!!
                                        haptics.longPress()
                                        if (heldBlock.id != currentSelected) select(heldBlock.id)
                                        liftUp(heldBlock.id)
                                        menuAt = Offset(
                                            shownLeft + down.position.x * scale,
                                            shownTop + focusShift + down.position.y * scale
                                        )
                                        menuFor = heldBlock.id
                                        continue
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    lastUptime = change.uptimeMillis
                                    if (!change.pressed) {
                                        if (paging) {
                                            pageTo?.let { swipeBackground(it) }
                                        } else if (!dragging && !held) {
                                            when {
                                                target != null -> {
                                                    haptics.subtle()
                                                    select(target.id)
                                                    openConfig(target.id)
                                                }
                                                currentSelected != null -> select(null)
                                                else -> tapBackground()
                                            }
                                        }
                                        break
                                    }
                                    total += change.positionChange()
                                    if (target == null && !paging && abs(total.x) > slop && abs(total.x) > abs(total.y)) {
                                        paging = true
                                        swipeName = start.background
                                        swipeNameShown = true
                                    }
                                    if (paging) {
                                        change.consume()
                                        val follow = (total.x * scale * 0.35f).coerceIn(-followMax, followMax)
                                        scope.launch { swipe.snapTo(follow) }
                                        val next = if (abs(total.x) > pagePx) {
                                            start.background.step(if (total.x < 0f) 1 else -1)
                                        } else {
                                            null
                                        }
                                        if (next != pageTo) {
                                            pageTo = next
                                            swipeName = next ?: start.background
                                            haptics.tick()
                                        }
                                        continue
                                    }
                                    if (!dragging && target != null && hypot(total.x, total.y) > slop) {
                                        dragging = true
                                        // A drag out of a hold carries the block, as on a home screen.
                                        if (held) menuFor = null
                                        gestureStart(start)
                                        if (target.id != currentSelected) select(target.id)
                                        if (handle == null && !held) {
                                            haptics.subtle()
                                            liftUp(target.id)
                                        }
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
                                        if (nowSnapped) {
                                            guidesX = result.guidesX
                                            guidesY = result.guidesY
                                        }
                                        if (nowSnapped && !snapped) {
                                            haptics.tick()
                                            scope.launch { guideAlpha.animateTo(1f, tween(90)) }
                                            // A carried block gives a small pulse as it catches the line.
                                            if (handle == null) {
                                                scope.launch {
                                                    lift.animateTo(LIFT_SCALE + 0.025f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh))
                                                    lift.animateTo(LIFT_SCALE, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                                                }
                                            }
                                        } else if (!nowSnapped && snapped) {
                                            scope.launch { guideAlpha.animateTo(0f, tween(160)) }
                                        }
                                        snapped = nowSnapped
                                        layoutChange(
                                            start.withBlock(
                                                LayoutGeometry.withAreaRect(target, result.rect, areaWidth, areaHeight)
                                            )
                                        )
                                    }
                                }
                            } finally {
                                scope.launch { guideAlpha.animateTo(0f, tween(180)) }
                                if (paging) {
                                    swipeEnded++
                                    scope.launch {
                                        swipe.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
                                    }
                                }
                                // A block held for its menu stays up until the menu goes.
                                if (liftedId != null && menuFor == null) setDown()
                            }
                        }
                    }
            ) {
                drawEditorChrome(
                    layout = layout,
                    selectedId = selectedId,
                    hiddenIds = setOfNotNull(liftedId, removingId),
                    insets = insets,
                    areaWidth = areaWidth,
                    areaHeight = areaHeight,
                    perScreenPx = perScreenPx,
                    marks = marks.value,
                    selection = selection.value,
                    guidesX = guidesX,
                    guidesY = guidesY,
                    guideAlpha = guideAlpha.value,
                    barTint = chrome.scrim.copy(alpha = 0.22f),
                    outline = chrome.outline,
                    accent = chrome.primary,
                    onAccent = chrome.onPrimary,
                    guide = chrome.tertiary,
                    badgeFill = chrome.inverseSurface,
                    badgeGlyph = chrome.inverseOnSurface,
                    badgeRing = chrome.surface,
                )
            }
        }

        // The background's name while a swipe pages through them.
        AnimatedVisibility(
            visible = swipeNameShown,
            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.9f),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (shownTop + focusShift + 20.dp.toPx()).roundToInt()) }
        ) {
            BackgroundNamePill(swipeName ?: layout.background)
        }

        // The hold menu, in the app's own colours, at the finger.
        val menuBlock = menuFor?.let { id -> layout.blocks.firstOrNull { it.id == id } }
        if (menuBlock != null) {
            val isTop = layout.blocks.lastOrNull()?.id == menuBlock.id
            val isBottom = layout.blocks.firstOrNull()?.id == menuBlock.id
            fun closeMenu() {
                menuFor = null
                setDown()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(menuAt.x.roundToInt(), menuAt.y.roundToInt()) }
                    .size(1.dp)
            ) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = ::closeMenu,
                    offset = DpOffset(0.dp, 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = menuBlock.editorLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cpl_menu_change)) },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        onClick = {
                            closeMenu()
                            openConfig(menuBlock.id)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cpl_duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = {
                            closeMenu()
                            onDuplicate(menuBlock.id)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cpl_bring_front)) },
                        leadingIcon = { Icon(Icons.Rounded.FlipToFront, contentDescription = null) },
                        enabled = !isTop,
                        onClick = {
                            closeMenu()
                            onFront(menuBlock.id)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cpl_send_back)) },
                        leadingIcon = { Icon(Icons.Rounded.FlipToBack, contentDescription = null) },
                        enabled = !isBottom,
                        onClick = {
                            closeMenu()
                            onBack(menuBlock.id)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cpl_remove)) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        ),
                        onClick = {
                            menuFor = null
                            liftedId = null
                            scope.launch { lift.snapTo(1f) }
                            removeBlock(menuBlock.id)
                        }
                    )
                }
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

/**
 * Where a block's remove badge sits, in the canvas's pixels: on its top left
 * corner, held inside the canvas so a block against an edge keeps a whole one.
 */
private fun badgeCenter(
    rect: SnapRect,
    insets: AreaInsets,
    canvasWidth: Float,
    canvasHeight: Float,
    radius: Float,
): Offset {
    val margin = radius * 1.2f
    return Offset(
        (insets.left + rect.left).coerceIn(margin, (canvasWidth - margin).coerceAtLeast(margin)),
        (insets.top + rect.top).coerceIn(margin, (canvasHeight - margin).coerceAtLeast(margin)),
    )
}

/**
 * The handles of the chosen block: three corners (the fourth carries the
 * remove badge), and the middle of each side long enough to hold one.
 */
private fun handlesOf(rect: SnapRect, reach: Float): List<Pair<Offset, SnapEdges>> {
    val corners = listOf(
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
    /** Blocks drawn without marks: lifted, or on their way out. */
    hiddenIds: Set<String>,
    insets: AreaInsets,
    areaWidth: Float,
    areaHeight: Float,
    perScreenPx: Float,
    /** 0 to 1 as the marks pop in when the editor opens. */
    marks: Float,
    /** 0 to 1 as the chosen block's outline and dots grow in. */
    selection: Float,
    guidesX: List<Float>,
    guidesY: List<Float>,
    guideAlpha: Float,
    barTint: Color,
    outline: Color,
    accent: Color,
    onAccent: Color,
    guide: Color,
    badgeFill: Color,
    badgeGlyph: Color,
    badgeRing: Color,
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
    val shown = marks.coerceIn(0f, 1f)
    val dashes = PathEffect.dashPathEffect(floatArrayOf(5f * px, 4f * px))
    val corner = CornerRadius(10f * px)
    layout.blocks.forEach { block ->
        if (block.id == selectedId || block.id in hiddenIds) return@forEach
        val rect = LayoutGeometry.areaRect(block, areaWidth, areaHeight)
        drawRoundRect(
            color = outline.copy(alpha = 0.5f * shown),
            topLeft = Offset(insets.left + rect.left, insets.top + rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = corner,
            style = Stroke(width = 1f * px, pathEffect = dashes)
        )
    }
    val guideTint = guide.copy(alpha = guide.alpha * guideAlpha.coerceIn(0f, 1f))
    if (guideAlpha > 0f) {
        guidesX.forEach { x ->
            drawLine(guideTint, Offset(insets.left + x, 0f), Offset(insets.left + x, size.height), strokeWidth = 1.5f * px)
        }
        guidesY.forEach { y ->
            drawLine(guideTint, Offset(0f, insets.top + y), Offset(size.width, insets.top + y), strokeWidth = 1.5f * px)
        }
    }

    val selected = layout.blocks.firstOrNull { it.id == selectedId }?.takeIf { it.id !in hiddenIds }
    if (selected != null) {
        val grow = selection.coerceAtLeast(0f)
        val rect = LayoutGeometry.areaRect(selected, areaWidth, areaHeight)
        drawRoundRect(
            color = accent.copy(alpha = grow.coerceAtMost(1f)),
            topLeft = Offset(insets.left + rect.left, insets.top + rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = corner,
            style = Stroke(width = 2f * px)
        )
        val radius = HANDLE_RADIUS.toPx() * perScreenPx * grow
        handlesOf(rect, HANDLE_REACH.toPx() * perScreenPx).forEach { (center, _) ->
            val at = Offset(insets.left + center.x, insets.top + center.y)
            drawCircle(onAccent, radius = radius + 2f * px, center = at)
            drawCircle(accent, radius = radius, center = at)
        }
    }

    // Remove badges last, in stack order, so the top block's is on top.
    val badge = BADGE_RADIUS.toPx() * perScreenPx * marks.coerceAtLeast(0f)
    if (badge <= 0f) return
    layout.blocks.forEach { block ->
        if (block.id in hiddenIds) return@forEach
        val center = badgeCenter(
            LayoutGeometry.areaRect(block, areaWidth, areaHeight),
            insets, size.width, size.height, BADGE_RADIUS.toPx() * perScreenPx
        )
        drawCircle(badgeRing, radius = badge + 1.5f * px, center = center)
        drawCircle(badgeFill, radius = badge, center = center)
        val arm = badge * 0.46f
        drawLine(
            color = badgeGlyph,
            start = Offset(center.x - arm, center.y),
            end = Offset(center.x + arm, center.y),
            strokeWidth = 2f * px,
            cap = StrokeCap.Round
        )
    }
}

/** The background after this one, or before it, round the list. */
private fun LayoutBackground.step(by: Int): LayoutBackground {
    val all = LayoutBackground.entries
    return all[(ordinal + by).mod(all.size)]
}

/** A background's name, and where it sits among them. */
@Composable
private fun BackgroundNamePill(background: LayoutBackground) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(background.label(), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LayoutBackground.entries.forEach { each ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                scheme.inverseOnSurface.copy(alpha = if (each == background) 1f else 0.35f)
                            )
                    )
                }
            }
        }
    }
}
