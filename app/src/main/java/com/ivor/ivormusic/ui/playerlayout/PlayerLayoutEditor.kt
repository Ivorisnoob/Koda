package com.ivor.ivormusic.ui.playerlayout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
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
import com.ivor.ivormusic.data.playerlayout.toBack
import com.ivor.ivormusic.data.playerlayout.toFront
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

/**
 * The editor is the player itself in edit mode, the way a home screen is
 * edited: every block carries a badge that removes it, a tap on one opens its
 * settings, a hold offers its menu, and a drag moves it. The only chrome is
 * Cancel and Done above, and Templates, Add and Background below.
 */
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
    // The block just added or copied, which grows into place.
    var arrivingId by remember { mutableStateOf<String?>(null) }
    var configFor by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showBackground by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    // A new layout opens on the "start from" picker, with the player it came
    // from marked; the preview behind it is already that player.
    var showStartFrom by remember(request) {
        mutableStateOf((request as? LayoutEditorRequest.New)?.choose == true)
    }
    var basePreset by remember(request) { mutableStateOf((request as? LayoutEditorRequest.New)?.preset) }
    // A new layout is named after what it started from until someone names it.
    var renamed by remember(request) { mutableStateOf(existing != null) }
    val presetNames = LayoutPreset.entries.associateWith { it.label() }
    // A player whose pieces can be read off the real thing is captured rather
    // than built: (the preset, whether it is the editor's starting point and so
    // replaces the first draft without an undo step).
    var pendingCapture by remember(request) {
        mutableStateOf(
            (request as? LayoutEditorRequest.New)?.preset?.takeIf { it.isCaptured }?.let { it to true }
        )
    }
    // Every player read off the real thing while the "start from" picker is
    // open: its previews are drawn from these, and a pick is instant. Read
    // again for a new song, since a title's length moves the pieces.
    val captured = remember(song?.id) { mutableStateMapOf<LayoutPreset, List<LayoutBlock>>() }
    val dirty = existing == null || draft != initial

    fun commit(updated: PlayerLayout) {
        if (updated == draft) return
        history.record(draft)
        draft = updated
        historyVersion++
    }

    /** A new layout takes the name of what it started from until someone names it. */
    fun nameFor(preset: LayoutPreset): String = if (renamed) {
        draft.name
    } else {
        uniqueLayoutName(
            presetNames.getValue(preset),
            store.layouts.value.filter { it.id != draft.id }.map { it.name }
        )
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

    /**
     * Puts a new [kind] on the screen, centred on [at] (the middle when null),
     * and lets it grow into place. A new button is asked what it does.
     */
    fun addBlock(
        kind: LayoutBlockKind,
        at: Offset? = null,
        sizeOverride: DpSize? = null,
        configure: (LayoutBlock) -> LayoutBlock = { it },
    ) {
        if (areaWidthPx <= 0f || areaHeightPx <= 0f) return
        val size = sizeOverride ?: when (kind) {
            LayoutBlockKind.COVER -> DpSize(areaWidthDp * 0.66f, areaWidthDp * 0.66f)
            LayoutBlockKind.HEADER -> DpSize(areaWidthDp - 160.dp, 44.dp)
            LayoutBlockKind.TOOLBAR -> DpSize(areaWidthDp - 40.dp, 72.dp)
            LayoutBlockKind.TIME -> DpSize(areaWidthDp - 80.dp, 32.dp)
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
        }.let(configure).clampedToArea()
        // A panel is something to put things on, so it goes under them.
        commit(
            if (kind == LayoutBlockKind.PANEL) {
                draft.copy(blocks = listOf(block) + draft.blocks)
            } else {
                draft.plusBlock(block)
            }
        )
        haptics.confirm()
        arrivingId = block.id
        selectedId = block.id
        // A new button is asked what it does straight away; one added for
        // its job already knows.
        if (kind == LayoutBlockKind.BUTTON && sizeOverride == null) {
            configFor = block.id
        }
    }

    /** The fix for a screen that cannot play or pause: a big play button low and central. */
    fun addPlayButton() {
        addBlock(
            kind = LayoutBlockKind.BUTTON,
            at = Offset(areaWidthPx / 2f, areaHeightPx * 0.84f),
            sizeOverride = DpSize(80.dp, 80.dp),
            configure = {
                it.copy(
                    action = LayoutAction.PLAY_PAUSE,
                    look = ButtonLook.FILLED,
                    shape = BlockShape.ROUND,
                )
            }
        )
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

    Box(modifier = Modifier.fillMaxSize()) {
    // The real player being read for "start from", under the editor's opaque
    // surface: never seen, never touched.
    pendingCapture?.let { (preset, isStart) ->
        StyleCaptureHost(
            preset = preset,
            viewModel = viewModel,
            canvasSize = canvasSize,
            safeArea = safeArea,
            playerScheme = playerScheme,
            ambientBackground = ambientBackground,
            onCaptured = { blocks ->
                pendingCapture = null
                if (blocks.isNotEmpty()) captured[preset] = blocks
                val updated = draft.copy(
                    name = nameFor(preset),
                    background = PlayerLayoutPresets.backgroundFor(preset),
                    blocks = blocks.ifEmpty { PlayerLayoutPresets.build(preset, "").blocks }
                )
                if (isStart) draft = updated else commit(updated)
            }
        )
    }
    if (showStartFrom) {
        val remaining = remember(song?.id) {
            LayoutPreset.entries.filter { it.isCaptured && it !in captured }
        }
        StyleCaptureQueue(
            presets = remaining,
            viewModel = viewModel,
            canvasSize = canvasSize,
            safeArea = safeArea,
            playerScheme = playerScheme,
            ambientBackground = ambientBackground,
            onCaptured = { preset, blocks -> if (blocks.isNotEmpty()) captured[preset] = blocks }
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                EditorTopBar(
                    name = draft.name,
                    canUndo = historyVersion >= 0 && history.canUndo,
                    canRedo = historyVersion >= 0 && history.canRedo,
                    onCancel = { if (dirty) confirmDiscard = true else onClose() },
                    onRename = { showRename = true },
                    onUndo = {
                        history.undo(draft)?.let { draft = it; historyVersion++ }
                        if (draft.blocks.none { it.id == selectedId }) selectedId = null
                    },
                    onRedo = {
                        history.redo(draft)?.let { draft = it; historyVersion++ }
                        if (draft.blocks.none { it.id == selectedId }) selectedId = null
                    },
                    onDone = ::save,
                )

                EditorCanvas(
                    layout = draft,
                    selectedId = selectedId,
                    arrivingId = arrivingId,
                    viewModel = viewModel,
                    canvasSize = canvasSize,
                    safeArea = safeArea,
                    ambientBackground = ambientBackground,
                    playerScheme = playerScheme,
                    onSelect = { selectedId = it },
                    onOpenConfig = { configFor = it },
                    onTapBackground = { showBackground = true },
                    onRemove = { id ->
                        commit(draft.minusBlock(id))
                        if (selectedId == id) selectedId = null
                    },
                    onDuplicate = { id ->
                        draft.duplicating(id)?.let { (updated, copyId) ->
                            commit(updated)
                            haptics.confirm()
                            arrivingId = copyId
                            selectedId = copyId
                        }
                    },
                    onFront = { id -> commit(draft.toFront(id)) },
                    onBack = { id -> commit(draft.toBack(id)) },
                    onGestureStart = { before ->
                        history.record(before)
                        historyVersion++
                    },
                    onLayoutChange = { draft = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                EditorToolbar(
                    onTemplates = { showStartFrom = true },
                    onAdd = { showAdd = true },
                    onBackground = { showBackground = true },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .navigationBarsPadding()
                        .padding(top = 8.dp, bottom = 12.dp)
                )
            }

            // A screen nothing on which plays or pauses is one tap from fixed.
            AnimatedVisibility(
                visible = !draft.canPlayPause,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.9f) +
                    slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 },
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = TOOLBAR_HEIGHT + 32.dp)
            ) {
                MissingPlayPill(onFix = ::addPlayButton)
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
    if (showAdd) {
        AddBlockSheet(
            onAdd = { kind ->
                showAdd = false
                addBlock(kind)
            },
            onDismiss = { showAdd = false }
        )
    }
    if (showStartFrom) {
        StartFromSheet(
            current = basePreset,
            captured = captured,
            onPick = { preset ->
                val ready = captured[preset]
                when {
                    // Fresh ids, so picking the same player twice is two layouts.
                    ready != null -> commit(
                        draft.copy(
                            name = nameFor(preset),
                            background = PlayerLayoutPresets.backgroundFor(preset),
                            blocks = ready.map { it.copy(id = PlayerLayout.newId()) }
                        )
                    )
                    preset.isCaptured -> pendingCapture = preset to false
                    else -> {
                        val built = PlayerLayoutPresets.build(preset, "")
                        commit(draft.copy(name = nameFor(preset), background = built.background, blocks = built.blocks))
                    }
                }
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

/** Cancel on one side, Done on the other, the layout's name between. */
@Composable
private fun EditorTopBar(
    name: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onCancel: () -> Unit,
    onRename: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cpl_cancel)) }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Surface(
                onClick = onRename,
                shape = CircleShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.cpl_rename),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.cpl_undo))
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.cpl_redo))
        }
        Spacer(Modifier.width(4.dp))
        Button(onClick = onDone) { Text(stringResource(R.string.cpl_done)) }
    }
}

private val TOOLBAR_HEIGHT = 80.dp

/**
 * The three things that are not on the screen: a whole player to start from,
 * something new to put on it, and what is behind it. Each says what it is.
 */
@Composable
private fun EditorToolbar(
    onTemplates: () -> Unit,
    onAdd: () -> Unit,
    onBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 2.dp,
        modifier = modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolbarTile(
                icon = Icons.Rounded.AutoAwesomeMosaic,
                label = stringResource(R.string.cpl_templates),
                prominent = false,
                onClick = onTemplates,
                modifier = Modifier.weight(1f)
            )
            ToolbarTile(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.cpl_add),
                prominent = true,
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            )
            ToolbarTile(
                icon = Icons.Rounded.Wallpaper,
                label = stringResource(R.string.cpl_background),
                prominent = false,
                onClick = onBackground,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** A labelled toolbar button that squishes and squares its corners under the thumb. */
@Composable
private fun ToolbarTile(
    icon: ImageVector,
    label: String,
    prominent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "ToolbarTileSquish"
    )
    val corner: Dp by animateDpAsState(
        targetValue = if (pressed) 14.dp else 24.dp,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "ToolbarTileCorner"
    )
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = if (prominent) scheme.primary else Color.Transparent,
        contentColor = if (prominent) scheme.onPrimary else scheme.onSurface,
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = squish
                scaleY = squish
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Says what is wrong and fixes it in one tap. */
@Composable
private fun MissingPlayPill(onFix: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        shadowElevation = 3.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.cpl_no_play_short),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = onFix,
                shape = CircleShape,
                color = scheme.error,
                contentColor = scheme.onError,
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.cpl_add_play),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
