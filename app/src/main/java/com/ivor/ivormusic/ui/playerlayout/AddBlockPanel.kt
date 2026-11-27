package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBackground
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.ui.player.LayoutBlockPreview
import com.ivor.ivormusic.ui.player.LayoutHost
import com.ivor.ivormusic.ui.player.LocalPlayerMiniature
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.label
import com.ivor.ivormusic.ui.player.rememberLayoutInk
import kotlin.math.hypot

/** One thing the Add gallery offers: a kind of block, and for a button, its job. */
internal data class AddEntry(val kind: LayoutBlockKind, val action: LayoutAction? = null)

private val ESSENTIALS = listOf(
    AddEntry(LayoutBlockKind.COVER),
    AddEntry(LayoutBlockKind.TITLE),
    AddEntry(LayoutBlockKind.SCRUBBER),
    AddEntry(LayoutBlockKind.TRANSPORT),
)

private val BUTTONS = listOf(
    LayoutAction.PLAY_PAUSE, LayoutAction.PREVIOUS, LayoutAction.NEXT,
    LayoutAction.LIKE, LayoutAction.SHUFFLE, LayoutAction.REPEAT,
    LayoutAction.QUEUE, LayoutAction.LYRICS, LayoutAction.DOWNLOAD,
    LayoutAction.SLEEP_TIMER, LayoutAction.SEEK_BACK, LayoutAction.SEEK_FORWARD,
).map { AddEntry(LayoutBlockKind.BUTTON, it) }

private val EXTRAS = listOf(
    AddEntry(LayoutBlockKind.LYRICS),
    AddEntry(LayoutBlockKind.UP_NEXT),
    AddEntry(LayoutBlockKind.HEADER),
    AddEntry(LayoutBlockKind.TOOLBAR),
    AddEntry(LayoutBlockKind.TIME),
    AddEntry(LayoutBlockKind.VISUALIZER),
    AddEntry(LayoutBlockKind.PANEL),
)

/** Where a hold-and-drag out of the gallery is. */
internal enum class PickPhase { START, MOVE, DROP, CANCEL }

private val TILE_PREVIEW_HEIGHT = 84.dp

/**
 * Everything that can go on the player, drawn for real in the look the
 * layout already wears, in three groups. A tap puts a piece in the middle; a
 * hold picks it up to drag onto the player, the way a widget is placed.
 *
 * Docked in the editor's own window rather than a sheet, because a sheet is
 * a window of its own and a drag cannot leave it.
 */
@Composable
internal fun AddBlockPanel(
    /** The block an entry adds, at its starting size and in the layout's look. */
    templateFor: (AddEntry) -> LayoutBlock,
    /** A block's real size on the player. */
    sizeOf: (LayoutBlock) -> DpSize,
    background: LayoutBackground,
    viewModel: PlayerViewModel,
    playerScheme: ColorScheme,
    onAdd: (AddEntry) -> Unit,
    onPick: (AddEntry, PickPhase, LayoutCoordinates, Offset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cpl_add_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.cpl_add_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cpl_close_add))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // The panel scrolls: a plain column clips what is past its
                    // height with nothing logged.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = navBottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                listOf(
                    R.string.cpl_add_essentials to ESSENTIALS,
                    R.string.cpl_add_buttons to BUTTONS,
                    R.string.cpl_add_extras to EXTRAS,
                ).forEach { (title, entries) ->
                    AddGroup(
                        title = stringResource(title),
                        entries = entries,
                        templateFor = templateFor,
                        sizeOf = sizeOf,
                        background = background,
                        host = host,
                        playerScheme = playerScheme,
                        onAdd = onAdd,
                        onPick = onPick,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddGroup(
    title: String,
    entries: List<AddEntry>,
    templateFor: (AddEntry) -> LayoutBlock,
    sizeOf: (LayoutBlock) -> DpSize,
    background: LayoutBackground,
    host: LayoutHost,
    playerScheme: ColorScheme,
    onAdd: (AddEntry) -> Unit,
    onPick: (AddEntry, PickPhase, LayoutCoordinates, Offset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        // Plain rows of three rather than a FlowRow: a FlowRow asks each tile
        // its intrinsic width first, and the real pieces drawn in a tile are
        // built on BoxWithConstraints, which cannot answer - it throws.
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            entries.chunked(3).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    row.forEach { entry ->
                        val block = templateFor(entry)
                        AddTile(
                            entry = entry,
                            block = block,
                            blockSize = sizeOf(block),
                            background = background,
                            host = host,
                            playerScheme = playerScheme,
                            onAdd = onAdd,
                            onPick = onPick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
internal fun AddEntry.label(): String =
    if (kind == LayoutBlockKind.BUTTON && action != null) action.label() else kind.label()

@Composable
private fun AddTile(
    entry: AddEntry,
    block: LayoutBlock,
    blockSize: DpSize,
    background: LayoutBackground,
    host: LayoutHost,
    playerScheme: ColorScheme,
    onAdd: (AddEntry) -> Unit,
    onPick: (AddEntry, PickPhase, LayoutCoordinates, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = entry.label()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "AddTileSquish"
    )
    val add by rememberUpdatedState(onAdd)
    val pick by rememberUpdatedState(onPick)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(TILE_PREVIEW_HEIGHT)
                .graphicsLayer {
                    scaleX = squish
                    scaleY = squish
                }
                .clip(RoundedCornerShape(18.dp))
                .onGloballyPositioned { coordinates = it }
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    role = Role.Button
                    onClick { add(entry); true }
                }
                .pointerInput(entry) {
                    // A tap adds; a hold picks it up to drag; a move before
                    // the hold is the gallery scrolling, and is left to it.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressed = true
                        val slop = viewConfiguration.touchSlop
                        var total = Offset.Zero
                        var last = down.position
                        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                                if (!change.pressed) {
                                    if (!change.isConsumed) add(entry)
                                    return@withTimeoutOrNull true
                                }
                                total += change.positionChange()
                                last = change.position
                                if (change.isConsumed || hypot(total.x, total.y) > slop) return@withTimeoutOrNull true
                            }
                        }
                        if (ended != null) {
                            pressed = false
                            return@awaitEachGesture
                        }
                        val coords = coordinates
                        if (coords == null || !coords.isAttached) {
                            pressed = false
                            return@awaitEachGesture
                        }
                        pick(entry, PickPhase.START, coords, last)
                        var dropped = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    dropped = true
                                    pick(entry, PickPhase.DROP, coords, last)
                                    break
                                }
                                last = change.position
                                change.consume()
                                pick(entry, PickPhase.MOVE, coords, last)
                            }
                        } finally {
                            pressed = false
                            if (!dropped) pick(entry, PickPhase.CANCEL, coords, last)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val fit = minOf(maxWidth / blockSize.width, maxHeight / blockSize.height) * 0.8f
            MaterialTheme(colorScheme = playerScheme) {
                val ink = rememberLayoutInk(background)
                Box(Modifier.fillMaxSize().background(ink.field))
                Box(
                    modifier = Modifier
                        .requiredSize(blockSize)
                        .graphicsLayer {
                            scaleX = fit
                            scaleY = fit
                        }
                ) {
                    CompositionLocalProvider(LocalPlayerMiniature provides true) {
                        LayoutBlockPreview(block = block, host = host, ink = ink)
                    }
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A piece being dragged out of the gallery, drawn at the size it will land at
 * on the player, under the finger.
 */
@Composable
internal fun AddGhost(
    block: LayoutBlock,
    blockSize: DpSize,
    /** The player's scale on screen, so the piece is shown at its landing size. */
    scale: Float,
    background: LayoutBackground,
    viewModel: PlayerViewModel,
    playerScheme: ColorScheme,
) {
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
    val lift by animateFloatAsState(
        targetValue = 1.06f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "AddGhostLift"
    )
    MaterialTheme(colorScheme = playerScheme) {
        val ink = rememberLayoutInk(background)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ink.field.copy(alpha = 0.9f),
            shadowElevation = 10.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
        ) {
            Box(
                modifier = Modifier.requiredSize(blockSize * scale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(blockSize)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                ) {
                    CompositionLocalProvider(LocalPlayerMiniature provides true) {
                        LayoutBlockPreview(block = block, host = host, ink = ink)
                    }
                }
            }
        }
    }
}
