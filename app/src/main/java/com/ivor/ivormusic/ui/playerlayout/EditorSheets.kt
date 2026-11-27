package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SmartButton
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBackground
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutColor
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.data.playerlayout.LayoutTextAlign
import com.ivor.ivormusic.data.playerlayout.LayoutPreset
import com.ivor.ivormusic.data.playerlayout.LayoutTextSize
import com.ivor.ivormusic.data.playerlayout.PlayerLayoutPresets
import com.ivor.ivormusic.ui.components.AdaptiveModalSheet
import com.ivor.ivormusic.ui.player.composeShape
import com.ivor.ivormusic.ui.player.icon
import com.ivor.ivormusic.ui.player.label
import com.ivor.ivormusic.ui.player.playerStyleLabel
import com.ivor.ivormusic.ui.player.playerStyleSubtitle

/* ------------------------------------------------------------------ */
/* Names and icons                                                     */
/* ------------------------------------------------------------------ */

@Composable
internal fun LayoutBlockKind.label(): String = stringResource(
    when (this) {
        LayoutBlockKind.COVER -> R.string.cpl_block_cover
        LayoutBlockKind.HEADER -> R.string.cpl_block_header
        LayoutBlockKind.TOOLBAR -> R.string.cpl_block_toolbar
        LayoutBlockKind.TIME -> R.string.cpl_block_time
        LayoutBlockKind.TITLE -> R.string.cpl_block_title
        LayoutBlockKind.SCRUBBER -> R.string.cpl_block_scrubber
        LayoutBlockKind.TRANSPORT -> R.string.cpl_block_transport
        LayoutBlockKind.BUTTON -> R.string.cpl_block_button
        LayoutBlockKind.LYRICS -> R.string.cpl_block_lyrics
        LayoutBlockKind.VISUALIZER -> R.string.cpl_block_visualizer
        LayoutBlockKind.UP_NEXT -> R.string.cpl_block_up_next
        LayoutBlockKind.PANEL -> R.string.cpl_block_panel
    }
)

internal fun LayoutBlockKind.icon(): ImageVector = when (this) {
    LayoutBlockKind.COVER -> Icons.Rounded.Album
    LayoutBlockKind.HEADER -> Icons.Rounded.Title
    LayoutBlockKind.TOOLBAR -> Icons.Rounded.SmartButton
    LayoutBlockKind.TIME -> Icons.Rounded.LinearScale
    LayoutBlockKind.TITLE -> Icons.Rounded.Title
    LayoutBlockKind.SCRUBBER -> Icons.Rounded.LinearScale
    LayoutBlockKind.TRANSPORT -> Icons.Rounded.PlayCircle
    LayoutBlockKind.BUTTON -> Icons.Rounded.SmartButton
    LayoutBlockKind.LYRICS -> Icons.Rounded.Lyrics
    LayoutBlockKind.VISUALIZER -> Icons.Rounded.GraphicEq
    LayoutBlockKind.UP_NEXT -> Icons.AutoMirrored.Rounded.QueueMusic
    LayoutBlockKind.PANEL -> Icons.Rounded.CropSquare
}

/** A block's name in the editor: a button is called by what it does. */
@Composable
internal fun LayoutBlock.editorLabel(): String =
    if (kind == LayoutBlockKind.BUTTON) action.label() else kind.label()

@Composable
private fun BlockShape.label(): String = stringResource(
    when (this) {
        BlockShape.SQUARE -> R.string.cpl_shape_square
        BlockShape.ROUNDED -> R.string.cpl_shape_rounded
        BlockShape.ROUND -> R.string.cpl_shape_round
        BlockShape.COOKIE -> R.string.cpl_shape_cookie
        BlockShape.CLOVER -> R.string.cpl_shape_clover
        BlockShape.FLOWER -> R.string.cpl_shape_flower
        BlockShape.ARCH -> R.string.cpl_shape_arch
        BlockShape.SOFT_BURST -> R.string.cpl_shape_burst
        BlockShape.GEM -> R.string.cpl_shape_gem
        BlockShape.SUNNY -> R.string.cpl_shape_sunny
    }
)

@Composable
internal fun LayoutBackground.label(): String = stringResource(
    when (this) {
        LayoutBackground.AMBIENT -> R.string.cpl_bg_ambient
        LayoutBackground.CONTAINER -> R.string.cpl_bg_container
        LayoutBackground.BOARD -> R.string.cpl_bg_board
        LayoutBackground.SPLIT -> R.string.cpl_bg_split
        LayoutBackground.CANVAS -> R.string.cpl_bg_canvas
        LayoutBackground.SOLID -> R.string.cpl_bg_solid
        LayoutBackground.TONAL -> R.string.cpl_bg_tonal
        LayoutBackground.COVER -> R.string.cpl_bg_cover
    }
)

/* ------------------------------------------------------------------ */
/* Block settings                                                      */
/* ------------------------------------------------------------------ */

/**
 * What a block does and how it looks, for the one block tapped. Every choice
 * applies at once, behind the sheet, so the preview answers while it is still
 * open; each is one undo step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockConfigSheet(
    block: LayoutBlock,
    onChange: (LayoutBlock) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The sheet scrolls: a plain column clips what is past its
                // height with nothing logged.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (block.kind == LayoutBlockKind.BUTTON) block.action.icon() else block.kind.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.size(14.dp))
                Text(
                    text = block.editorLabel(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (block.kind) {
                LayoutBlockKind.BUTTON -> {
                    ConfigSection(stringResource(R.string.cpl_cfg_action)) {
                        ActionGrid(
                            actions = LayoutAction.forButtons,
                            selected = block.action,
                            onSelect = { onChange(block.copy(action = it)) }
                        )
                    }
                    LookSection(block, onChange)
                    ColorSection(block, onChange)
                    ShapeSection(block, onChange, BlockShape.entries)
                }
                LayoutBlockKind.TRANSPORT -> {
                    LookSection(block, onChange)
                    ColorSection(block, onChange)
                    ShapeSection(block, onChange, BlockShape.entries)
                    // Classic's button group carries shuffle and repeat at its ends.
                    if (block.skin == LayoutSkin.CLASSIC) {
                        ConfigToggles {
                            ConfigToggle(
                                title = stringResource(R.string.cpl_cfg_transport_toggles),
                                checked = block.showDetail,
                                onChange = { onChange(block.copy(showDetail = it)) }
                            )
                        }
                    }
                }
                LayoutBlockKind.COVER -> {
                    ConfigSection(stringResource(R.string.cpl_cfg_tap)) {
                        ActionGrid(
                            actions = LayoutAction.forCover,
                            selected = block.action,
                            onSelect = { onChange(block.copy(action = it)) }
                        )
                    }
                    ShapeSection(block, onChange, BlockShape.entries)
                    ConfigToggles {
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_spin),
                            subtitle = stringResource(R.string.cpl_cfg_spin_sub),
                            checked = block.motion,
                            onChange = { onChange(block.copy(motion = it)) }
                        )
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_fade),
                            checked = block.fade,
                            onChange = { onChange(block.copy(fade = it)) }
                        )
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_swipe),
                            checked = block.swipeToSkip,
                            onChange = { onChange(block.copy(swipeToSkip = it)) }
                        )
                    }
                }
                LayoutBlockKind.TITLE -> {
                    ConfigSection(stringResource(R.string.cpl_cfg_size)) {
                        ChoiceChips(
                            options = LayoutTextSize.entries,
                            selected = block.textSize,
                            label = {
                                stringResource(
                                    when (it) {
                                        LayoutTextSize.SMALL -> R.string.cpl_size_small
                                        LayoutTextSize.MEDIUM -> R.string.cpl_size_medium
                                        LayoutTextSize.LARGE -> R.string.cpl_size_large
                                        LayoutTextSize.HUGE -> R.string.cpl_size_huge
                                    }
                                )
                            },
                            onSelect = { onChange(block.copy(textSize = it)) }
                        )
                    }
                    ConfigSection(stringResource(R.string.cpl_cfg_align)) {
                        ChoiceChips(
                            options = LayoutTextAlign.entries,
                            selected = block.align,
                            label = {
                                stringResource(
                                    when (it) {
                                        LayoutTextAlign.START -> R.string.cpl_align_start
                                        LayoutTextAlign.CENTER -> R.string.cpl_align_center
                                        LayoutTextAlign.END -> R.string.cpl_align_end
                                    }
                                )
                            },
                            onSelect = { onChange(block.copy(align = it)) }
                        )
                    }
                    ConfigToggles {
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_show_artist),
                            checked = block.showDetail,
                            onChange = { onChange(block.copy(showDetail = it)) }
                        )
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_swipe),
                            checked = block.swipeToSkip,
                            onChange = { onChange(block.copy(swipeToSkip = it)) }
                        )
                    }
                }
                LayoutBlockKind.SCRUBBER -> {
                    ColorSection(block, onChange)
                    ConfigToggles {
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_show_times),
                            checked = block.showDetail,
                            onChange = { onChange(block.copy(showDetail = it)) }
                        )
                        ConfigToggle(
                            title = stringResource(R.string.cpl_cfg_wave),
                            checked = block.motion,
                            onChange = { onChange(block.copy(motion = it)) }
                        )
                    }
                }
                LayoutBlockKind.UP_NEXT -> {
                    ColorSection(block, onChange)
                    ShapeSection(
                        block, onChange,
                        listOf(BlockShape.SQUARE, BlockShape.ROUNDED, BlockShape.ROUND)
                    )
                }
                LayoutBlockKind.PANEL -> {
                    ColorSection(block, onChange)
                    ShapeSection(block, onChange, BlockShape.entries)
                }
                LayoutBlockKind.LYRICS -> ConfigNote(stringResource(R.string.cpl_cfg_lyrics_note))
                LayoutBlockKind.VISUALIZER -> ConfigNote(stringResource(R.string.cpl_cfg_visualizer_note))
                LayoutBlockKind.HEADER, LayoutBlockKind.TOOLBAR, LayoutBlockKind.TIME -> Unit
            }

            // Whose look it wears: Koda's blocks, or a built-in player's own
            // pieces. A player's look keeps its own buttons, so a Hero play
            // disc dropped into any layout is Hero's disc.
            if (OFFERED_SKINS.size > 1) {
                ConfigSection(stringResource(R.string.cpl_cfg_skin)) {
                    ChoiceChips(
                        options = OFFERED_SKINS,
                        selected = block.skin,
                        label = { skin ->
                            skin.style?.let { playerStyleLabel(it) } ?: stringResource(R.string.cpl_skin_koda)
                        },
                        onSelect = { onChange(block.copy(skin = it)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

@Composable
private fun ConfigNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Every action as a tile with its icon, three to a row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionGrid(
    actions: List<LayoutAction>,
    selected: LayoutAction,
    onSelect: (LayoutAction) -> Unit,
) {
    FlowRow(
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        actions.forEach { action ->
            val isSelected = action == selected
            Surface(
                onClick = { onSelect(action) },
                shape = RoundedCornerShape(if (isSelected) 22.dp else 16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 84.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                ) {
                    Icon(action.icon(), contentDescription = null, modifier = Modifier.size(24.dp))
                    Text(
                        text = action.label(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // Keep a short last row at the grid's column width.
        repeat((3 - actions.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                leadingIcon = if (option == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun LookSection(block: LayoutBlock, onChange: (LayoutBlock) -> Unit) {
    ConfigSection(stringResource(R.string.cpl_cfg_look)) {
        ChoiceChips(
            options = ButtonLook.entries,
            selected = block.look,
            label = {
                stringResource(
                    when (it) {
                        ButtonLook.FILLED -> R.string.cpl_look_filled
                        ButtonLook.TONAL -> R.string.cpl_look_tonal
                        ButtonLook.OUTLINED -> R.string.cpl_look_outlined
                        ButtonLook.PLAIN -> R.string.cpl_look_plain
                    }
                )
            },
            onSelect = { onChange(block.copy(look = it)) }
        )
    }
}

/** Four swatches in the scheme's own colours; neutral is the ink on the ground. */
@Composable
private fun ColorSection(block: LayoutBlock, onChange: (LayoutBlock) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ConfigSection(stringResource(R.string.cpl_cfg_color)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LayoutColor.entries.forEach { color ->
                val (fill, name) = when (color) {
                    LayoutColor.PRIMARY -> scheme.primary to stringResource(R.string.cpl_color_primary)
                    LayoutColor.SECONDARY -> scheme.secondary to stringResource(R.string.cpl_color_secondary)
                    LayoutColor.TERTIARY -> scheme.tertiary to stringResource(R.string.cpl_color_tertiary)
                    LayoutColor.NEUTRAL -> scheme.inverseSurface to stringResource(R.string.cpl_color_neutral)
                }
                val isSelected = color == block.color
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.border(3.dp, scheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(if (isSelected) 6.dp else 0.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .clickable(role = Role.RadioButton) { onChange(block.copy(color = color)) }
                        .semantics { contentDescription = name }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSection(block: LayoutBlock, onChange: (LayoutBlock) -> Unit, shapes: List<BlockShape>) {
    val scheme = MaterialTheme.colorScheme
    ConfigSection(stringResource(R.string.cpl_cfg_shape)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            shapes.forEach { shape ->
                val isSelected = shape == block.shape
                val name = shape.label()
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) scheme.secondaryContainer else Color.Transparent)
                        .clickable(role = Role.RadioButton) { onChange(block.copy(shape = shape)) }
                        .semantics { contentDescription = name }
                        .padding(9.dp)
                        .clip(shape.composeShape())
                        .background(if (isSelected) scheme.primary else scheme.surfaceContainerHighest)
                )
            }
        }
    }
}

@Composable
private fun ConfigToggles(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 4.dp)
    ) { content() }
}

@Composable
private fun ConfigToggle(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/* ------------------------------------------------------------------ */
/* Background                                                          */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackgroundSheet(
    selected: LayoutBackground,
    onSelect: (LayoutBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AdaptiveModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.cpl_background),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LayoutBackground.entries.forEach { background ->
                    val isSelected = background == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh
                            )
                            .clickable(role = Role.RadioButton) { onSelect(background) }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.4f)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    when (background) {
                                        LayoutBackground.AMBIENT -> Modifier.background(
                                            Brush.linearGradient(
                                                listOf(scheme.primaryContainer, scheme.tertiaryContainer)
                                            )
                                        )
                                        LayoutBackground.SOLID -> Modifier
                                            .background(scheme.surface)
                                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                                        LayoutBackground.TONAL -> Modifier.background(scheme.primaryContainer)
                                        LayoutBackground.CONTAINER -> Modifier.background(scheme.primaryContainer)
                                        LayoutBackground.BOARD, LayoutBackground.CANVAS -> Modifier.background(scheme.surfaceContainerLowest)
                                        LayoutBackground.SPLIT -> Modifier.background(
                                            Brush.verticalGradient(
                                                0f to scheme.surfaceContainerLow, 0.45f to scheme.surfaceContainerLow,
                                                0.45f to scheme.surfaceContainerLowest, 1f to scheme.surfaceContainerLowest
                                            )
                                        )
                                        LayoutBackground.COVER -> Modifier.background(
                                            Brush.verticalGradient(
                                                listOf(scheme.primary, scheme.surface)
                                            )
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (background == LayoutBackground.COVER) {
                                Icon(
                                    Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = scheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = background.label(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Start from                                                          */
/* ------------------------------------------------------------------ */

/**
 * Every built-in player as a layout to start from, then the two that are not
 * a player. Each tile is drawn from the preset's own blocks, so the picture is
 * what lands on the screen. Picking one replaces what is on the screen, as one
 * undo step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StartFromSheet(
    current: LayoutPreset?,
    /** Each player as read off the real thing, as it arrives; the rest draw their hand-built layout. */
    captured: Map<LayoutPreset, List<LayoutBlock>>,
    onPick: (LayoutPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    val built = remember { LayoutPreset.entries.associateWith { PlayerLayoutPresets.build(it, "") } }
    val previews = LayoutPreset.entries.associateWith { preset ->
        captured[preset]?.let { blocks ->
            com.ivor.ivormusic.data.playerlayout.PlayerLayout(
                id = "",
                name = "",
                background = PlayerLayoutPresets.backgroundFor(preset),
                blocks = blocks
            )
        } ?: built.getValue(preset)
    }
    AdaptiveModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.cpl_start_from_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.cpl_start_from_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val styled = LayoutPreset.entries.filter { it.style != null }
            val other = LayoutPreset.entries.filter { it.style == null }
            Text(
                text = stringResource(R.string.cpl_start_from_styles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            PresetGrid(styled, previews, current, onPick)
            Text(
                text = stringResource(R.string.cpl_start_from_other),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            PresetGrid(other, previews, current, onPick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetGrid(
    presets: List<LayoutPreset>,
    previews: Map<LayoutPreset, com.ivor.ivormusic.data.playerlayout.PlayerLayout>,
    current: LayoutPreset?,
    onPick: (LayoutPreset) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        presets.forEach { preset ->
            val isCurrent = preset == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isCurrent) scheme.secondaryContainer else scheme.surfaceContainerHigh)
                    .clickable(role = Role.Button) { onPick(preset) }
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LayoutWireframe(
                    layout = previews.getValue(preset),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.47f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isCurrent) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                )
                Text(
                    text = preset.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isCurrent) {
                        stringResource(R.string.cpl_start_from_current)
                    } else {
                        preset.subtitle()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        repeat((3 - presets.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun LayoutPreset.subtitle(): String = style?.let { playerStyleSubtitle(it) } ?: stringResource(
    if (this == LayoutPreset.LYRICS) R.string.cpl_preset_lyrics_sub else R.string.cpl_preset_blank_sub
)

/* ------------------------------------------------------------------ */
/* Dialogs                                                             */
/* ------------------------------------------------------------------ */

@Composable
internal fun RenameLayoutDialog(
    name: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text(stringResource(R.string.cpl_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                label = { Text(stringResource(R.string.cpl_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text(stringResource(R.string.cpl_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Composable
internal fun DiscardLayoutDialog(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text(stringResource(R.string.cpl_discard_title)) },
        text = { Text(stringResource(R.string.cpl_discard_body)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.cpl_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cpl_keep_editing)) }
        }
    )
}
