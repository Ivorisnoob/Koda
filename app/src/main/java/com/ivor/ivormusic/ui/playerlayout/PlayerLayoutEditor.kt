package com.ivor.ivormusic.ui.playerlayout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.zIndex
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutColor
import com.ivor.ivormusic.data.playerlayout.LayoutHistory
import com.ivor.ivormusic.data.playerlayout.LayoutPreset
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.data.playerlayout.PlayerLayoutPresets
import com.ivor.ivormusic.data.playerlayout.PlayerLayoutStore
import com.ivor.ivormusic.data.playerlayout.clampedToArea
import com.ivor.ivormusic.data.playerlayout.duplicating
import com.ivor.ivormusic.data.playerlayout.minusBlock
import com.ivor.ivormusic.data.playerlayout.plusBlock
import com.ivor.ivormusic.data.playerlayout.restacked
import com.ivor.ivormusic.data.playerlayout.withBlock
import com.ivor.ivormusic.ui.player.PlayerFrameKind
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.areaInsetsPx
import com.ivor.ivormusic.ui.player.playerFrameFor
import com.ivor.ivormusic.ui.player.playerStyleLabel
import com.ivor.ivormusic.ui.player.rememberArtworkColorScheme
import com.ivor.ivormusic.ui.theme.currentWindowLayout
import com.ivor.ivormusic.ui.theme.windowDpSize
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlin.math.roundToInt

/**
 * The custom player layout editor, hosted once in `MainActivity` above both
 * players and opened through [PlayerLayoutEditorBus].
 */
@Composable
fun PlayerLayoutEditorOverlay(viewModel: PlayerViewModel) {
    val request by PlayerLayoutEditorBus.request.collectAsState()
    AnimatedVisibility(
        visible = request != null,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 10 },
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 10 },
    ) {
        // Held for the exit, when the bus has already let go of it.
        val opened = remember { PlayerLayoutEditorBus.request.value } ?: return@AnimatedVisibility
        PlayerLayoutEditor(
            request = opened,
            viewModel = viewModel,
            onClose = { PlayerLayoutEditorBus.close() }
        )
    }
}

/** Drag-and-drop from the tray, in the editor's own coordinates. */
private class TrayDrag(val kind: LayoutBlockKind, val position: Offset)

/** A preset is called by the player it stands for. */
@Composable
internal fun LayoutPreset.label(): String = style?.let { playerStyleLabel(it) } ?: stringResource(
    if (this == LayoutPreset.LYRICS) R.string.cpl_preset_lyrics else R.string.cpl_preset_blank
)

/** [base], or "[base] 2", "[base] 3"... whichever is not taken yet. */
internal fun uniqueLayoutName(base: String, taken: Collection<String>): String {
    if (base !in taken) return base
    var n = 2
    while ("$base $n" in taken) n++
    return "$base $n"
}

@Composable
private fun PlayerLayoutEditor(
    request: LayoutEditorRequest,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val haptics = rememberKodaHaptics()
    val store = remember(context) { PlayerLayoutStore(context) }
    val prefs = remember(context) { ThemePreferences(context) }
    val ambientBackground by prefs.ambientBackground.collectAsState()
    val artworkColors by prefs.playerArtworkColors.collectAsState()
    val song by viewModel.currentSong.collectAsState()

    val newPreset = (request as? LayoutEditorRequest.New)?.preset ?: LayoutPreset.CLASSIC
    val newName = newPreset.label()
    val existing = remember(request) {
        (request as? LayoutEditorRequest.Edit)?.let { edit ->
            store.layouts.value.firstOrNull { it.id == edit.layoutId }
        }
    }
    val initial = remember(request) {
        existing ?: PlayerLayoutPresets.build(
            preset = newPreset,
            name = uniqueLayoutName(newName, store.layouts.value.map { it.name }),
        )
    }
    var draft by remember(request) { mutableStateOf(initial) }
    val history = remember(request) { LayoutHistory() }
    // Bumped on every step so undo and redo re-read whether they can go.
    var historyVersion by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var configFor by remember { mutableStateOf<String?>(null) }
    var showBackground by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var trayDrag by remember { mutableStateOf<TrayDrag?>(null) }
    // A new layout opens on the "start from" picker, with the player it came
    // from marked; the preview behind it is already that player.
    var showStartFrom by remember(request) {
        mutableStateOf((request as? LayoutEditorRequest.New)?.choose == true)
    }
    var basePreset by remember(request) { mutableStateOf((request as? LayoutEditorRequest.New)?.preset) }
    // A new layout is named after what it started from until someone names it.
    var renamed by remember(request) { mutableStateOf(existing != null) }
    val presetNames = LayoutPreset.entries.associateWith { it.label() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(2) } // [0] editor root, [1] canvas
    val dirty = existing == null || draft != initial

    fun commit(updated: PlayerLayout) {
        if (updated == draft) return
        history.record(draft)
        draft = updated
        historyVersion++
    }

    // The preview is the player as this phone draws it: the window, or the
    // phone-shaped stage on a wide screen. On its side the player uses its
    // landscape layout instead, so the editor shows the upright one.
    val window = windowDpSize()
    val frame = playerFrameFor(currentWindowLayout())
    val canvasSize = when (frame.kind) {
        PlayerFrameKind.STAGE -> DpSize(frame.stageWidth, window.height)
        PlayerFrameKind.LANDSCAPE -> DpSize(min(window.width, window.height), max(window.width, window.height))
        PlayerFrameKind.FULL -> window
    }
    val systemSafeArea = WindowInsets.safeDrawing.asPaddingValues()
    val safeArea = if (frame.kind == PlayerFrameKind.LANDSCAPE) {
        PaddingValues(top = 32.dp, bottom = 24.dp)
    } else {
        systemSafeArea
    }
    val insets = areaInsetsPx(safeArea, direction, density)
    val areaWidthPx = with(density) { canvasSize.width.toPx() } - insets.left - insets.right
    val areaHeightPx = with(density) { canvasSize.height.toPx() } - insets.top - insets.bottom
    val areaWidthDp = with(density) { areaWidthPx.toDp() }
    val areaHeightDp = with(density) { areaHeightPx.toDp() }

    fun addBlock(kind: LayoutBlockKind, at: Offset?) {
        if (areaWidthPx <= 0f || areaHeightPx <= 0f) return
        val size = when (kind) {
            LayoutBlockKind.COVER -> DpSize(areaWidthDp * 0.66f, areaWidthDp * 0.66f)
            LayoutBlockKind.TITLE -> DpSize(areaWidthDp - 48.dp, 72.dp)
            LayoutBlockKind.SCRUBBER -> DpSize(areaWidthDp - 40.dp, 52.dp)
            LayoutBlockKind.TRANSPORT -> DpSize(areaWidthDp - 40.dp, 88.dp)
            LayoutBlockKind.BUTTON -> DpSize(56.dp, 56.dp)
            LayoutBlockKind.LYRICS -> DpSize(areaWidthDp, areaHeightDp * 0.4f)
            LayoutBlockKind.VISUALIZER -> DpSize(areaWidthDp - 40.dp, 88.dp)
            LayoutBlockKind.UP_NEXT -> DpSize(areaWidthDp - 40.dp, 72.dp)
            LayoutBlockKind.PANEL -> DpSize(areaWidthDp - 32.dp, 160.dp)
        }
        val width = with(density) { size.width.toPx() } / areaWidthPx
        val height = with(density) { size.height.toPx() } / areaHeightPx
        val center = at ?: Offset(areaWidthPx / 2f, areaHeightPx / 2f)
        val block = LayoutBlock(
            id = PlayerLayout.newId(),
            kind = kind,
            x = center.x / areaWidthPx - width / 2f,
            y = center.y / areaHeightPx - height / 2f,
            width = width,
            height = height,
        ).let {
            when (kind) {
                LayoutBlockKind.BUTTON, LayoutBlockKind.TRANSPORT ->
                    it.copy(look = ButtonLook.TONAL, color = LayoutColor.PRIMARY)
                else -> it
            }
        }.clampedToArea()
        // A panel is something to put things on, so it goes under them.
        commit(
            if (kind == LayoutBlockKind.PANEL) {
                draft.copy(blocks = listOf(block) + draft.blocks)
            } else {
                draft.plusBlock(block)
            }
        )
        haptics.confirm()
        selectedId = block.id
        // A new button is asked what it does straight away.
        if (kind == LayoutBlockKind.BUTTON) configFor = block.id
    }

    fun save() {
        store.save(draft.copy(name = draft.name.ifBlank { newName }))
        store.setActive(draft.id)
        prefs.setPlayerStyle(PlayerStyle.CUSTOM)
        haptics.confirm()
        onClose()
    }

    BackHandler {
        when {
            selectedId != null -> selectedId = null
            dirty -> confirmDiscard = true
            else -> onClose()
        }
    }

    val playerScheme = rememberArtworkColorScheme(
        enabled = artworkColors,
        albumArtUri = song?.albumArtUri?.toString() ?: song?.thumbnailUrl,
        base = MaterialTheme.colorScheme
    )
    val selectedBlock = draft.blocks.firstOrNull { it.id == selectedId }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates[0] = it }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                EditorTopBar(
                    name = draft.name,
                    canUndo = historyVersion >= 0 && history.canUndo,
                    canRedo = historyVersion >= 0 && history.canRedo,
                    onClose = { if (dirty) confirmDiscard = true else onClose() },
                    onRename = { showRename = true },
                    onUndo = {
                        history.undo(draft)?.let { draft = it; historyVersion++ }
                        if (draft.blocks.none { it.id == selectedId }) selectedId = null
                    },
                    onRedo = {
                        history.redo(draft)?.let { draft = it; historyVersion++ }
                        if (draft.blocks.none { it.id == selectedId }) selectedId = null
                    },
                    onSave = ::save,
                )

                EditorCanvas(
                    layout = draft,
                    selectedId = selectedId,
                    viewModel = viewModel,
                    canvasSize = canvasSize,
                    safeArea = safeArea,
                    ambientBackground = ambientBackground,
                    playerScheme = playerScheme,
                    onSelect = { id ->
                        if (id != null && id != selectedId) haptics.subtle()
                        selectedId = id
                    },
                    onOpenConfig = { configFor = it },
                    onGestureStart = { before ->
                        history.record(before)
                        historyVersion++
                    },
                    onLayoutChange = { draft = it },
                    onCanvasPositioned = { coordinates[1] = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Text(
                    text = if (!draft.canPlayPause) {
                        stringResource(R.string.cpl_no_play_warning)
                    } else if (selectedBlock == null) {
                        stringResource(R.string.cpl_tray_hint)
                    } else {
                        stringResource(R.string.cpl_editor_hint)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (!draft.canPlayPause) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )

                val barEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                val barSpatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                AnimatedContent(
                    targetState = selectedBlock != null,
                    transitionSpec = {
                        (fadeIn(barEffects) + scaleIn(barSpatial, initialScale = 0.94f))
                            .togetherWith(fadeOut(barEffects))
                    },
                    label = "EditorBottomBar",
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                ) { hasSelection ->
                    val block = draft.blocks.firstOrNull { it.id == selectedId }
                    if (hasSelection && block != null) {
                        SelectionBar(
                            block = block,
                            isTop = draft.blocks.lastOrNull()?.id == block.id,
                            isBottom = draft.blocks.firstOrNull()?.id == block.id,
                            onEdit = { configFor = block.id },
                            onDuplicate = {
                                draft.duplicating(block.id)?.let { (updated, id) ->
                                    commit(updated)
                                    selectedId = id
                                }
                            },
                            onForward = { commit(draft.restacked(block.id, up = true)) },
                            onBackward = { commit(draft.restacked(block.id, up = false)) },
                            onRemove = {
                                commit(draft.minusBlock(block.id))
                                selectedId = null
                            },
                            onDone = { selectedId = null }
                        )
                    } else {
                        BlockTray(
                            background = draft.background,
                            onStartFrom = { showStartFrom = true },
                            onBackground = { showBackground = true },
                            onAdd = { addBlock(it, null) },
                            onDrag = { kind, tile, local, finished ->
                                val root = coordinates[0]
                                if (root == null || !root.isAttached || !tile.isAttached) {
                                    trayDrag = null
                                    return@BlockTray
                                }
                                if (!finished) {
                                    trayDrag = TrayDrag(kind, root.localPositionOf(tile, local))
                                } else {
                                    trayDrag = null
                                    val canvas = coordinates[1]
                                    if (canvas != null && canvas.isAttached) {
                                        val point = canvas.localPositionOf(tile, local)
                                        val inArea = Offset(point.x - insets.left, point.y - insets.top)
                                        if (inArea.x in 0f..areaWidthPx && inArea.y in 0f..areaHeightPx) {
                                            addBlock(kind, inArea)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // The block being dragged out of the tray, under the finger.
            trayDrag?.let { drag ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .zIndex(2f)
                        .offset {
                            IntOffset(
                                (drag.position.x - 40.dp.toPx()).roundToInt(),
                                (drag.position.y - 60.dp.toPx()).roundToInt()
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(drag.kind.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(drag.kind.label(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    configFor?.let { id ->
        draft.blocks.firstOrNull { it.id == id }?.let { block ->
            BlockConfigSheet(
                block = block,
                onChange = { commit(draft.withBlock(it)) },
                onDismiss = { configFor = null }
            )
        } ?: run { configFor = null }
    }
    if (showStartFrom) {
        StartFromSheet(
            current = basePreset,
            onPick = { preset ->
                val built = PlayerLayoutPresets.build(preset, "")
                val name = if (renamed) {
                    draft.name
                } else {
                    uniqueLayoutName(
                        presetNames.getValue(preset),
                        store.layouts.value.filter { it.id != draft.id }.map { it.name }
                    )
                }
                commit(draft.copy(name = name, background = built.background, blocks = built.blocks))
                basePreset = preset
                selectedId = null
                showStartFrom = false
                haptics.confirm()
            },
            onDismiss = { showStartFrom = false }
        )
    }
    if (showBackground) {
        BackgroundSheet(
            selected = draft.background,
            onSelect = { commit(draft.copy(background = it)) },
            onDismiss = { showBackground = false }
        )
    }
    if (showRename) {
        RenameLayoutDialog(
            name = draft.name,
            onRename = { name ->
                commit(draft.copy(name = name))
                renamed = true
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
    if (confirmDiscard) {
        DiscardLayoutDialog(
            onDiscard = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false }
        )
    }
}

@Composable
private fun EditorTopBar(
    name: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onClose: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cpl_close_editor))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRename)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.cpl_editor_tap_rename),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.cpl_undo))
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.cpl_redo))
        }
        Spacer(Modifier.width(4.dp))
        Button(onClick = onSave) { Text(stringResource(R.string.cpl_save)) }
    }
}

/**
 * Every kind of block, to tap onto the middle of the screen or drag up to
 * where it should go. The background sits first, because it is the one
 * choice that is not a block.
 */
@Composable
private fun BlockTray(
    background: com.ivor.ivormusic.data.playerlayout.LayoutBackground,
    onStartFrom: () -> Unit,
    onBackground: () -> Unit,
    onAdd: (LayoutBlockKind) -> Unit,
    onDrag: (kind: LayoutBlockKind, tile: LayoutCoordinates, local: Offset, finished: Boolean) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            TrayTile(
                label = stringResource(R.string.cpl_start_from),
                icon = Icons.Rounded.AutoAwesomeMosaic,
                accent = true,
                contentDescription = stringResource(R.string.cpl_start_from_title),
                modifier = Modifier.clickable(onClick = onStartFrom)
            )
        }
        item {
            TrayTile(
                label = background.label(),
                icon = Icons.Rounded.Wallpaper,
                accent = true,
                contentDescription = stringResource(R.string.cpl_background),
                modifier = Modifier.clickable(onClick = onBackground)
            )
        }
        items(LayoutBlockKind.entries) { kind ->
            val description = stringResource(R.string.cpl_add_block, kind.label())
            var tile by remember { mutableStateOf<LayoutCoordinates?>(null) }
            TrayTile(
                label = kind.label(),
                icon = kind.icon(),
                accent = false,
                contentDescription = description,
                modifier = Modifier
                    .onGloballyPositioned { tile = it }
                    .pointerInput(kind) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Only a pull upward leaves the tray; sideways is
                            // the tray's own scroll.
                            val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                                if (over < 0f) change.consume()
                            } ?: return@awaitEachGesture
                            val coords = tile ?: return@awaitEachGesture
                            var last = start.position
                            onDrag(kind, coords, last, false)
                            drag(start.id) { change ->
                                last = change.position
                                change.consume()
                                onDrag(kind, coords, last, false)
                            }
                            onDrag(kind, coords, last, true)
                        }
                    }
                    .clickable { onAdd(kind) }
            )
        }
    }
}

@Composable
private fun TrayTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (accent) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (accent) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .size(width = 84.dp, height = 80.dp)
            .then(modifier)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The picked block: change it, copy it, restack it, remove it. */
@Composable
private fun SelectionBar(
    block: LayoutBlock,
    isTop: Boolean,
    isBottom: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = block.kind.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp)
                )
                Text(
                    text = block.editorLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                IconButton(onClick = onDone) {
                    Icon(Icons.Rounded.Done, contentDescription = stringResource(R.string.cpl_deselect))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(width = 64.dp, height = 48.dp)
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.cpl_edit_block))
                }
                IconButton(onClick = onDuplicate) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.cpl_duplicate))
                }
                IconButton(onClick = onForward, enabled = !isTop) {
                    Icon(Icons.Rounded.FlipToFront, contentDescription = stringResource(R.string.cpl_bring_forward))
                }
                IconButton(onClick = onBackward, enabled = !isBottom) {
                    Icon(Icons.Rounded.FlipToBack, contentDescription = stringResource(R.string.cpl_send_backward))
                }
                IconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.cpl_remove))
                }
            }
        }
    }
}
