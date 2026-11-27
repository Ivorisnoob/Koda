package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBackground
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.data.playerlayout.LayoutTextAlign
import com.ivor.ivormusic.data.playerlayout.LayoutTextSize
import com.ivor.ivormusic.ui.player.LayoutBlockPreview
import com.ivor.ivormusic.ui.player.LayoutHost
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.icon
import com.ivor.ivormusic.ui.player.playerStyleLabel
import com.ivor.ivormusic.ui.player.rememberLayoutInk

/** How tall a style preview is; its width follows the block's shape. */
private val STYLE_TILE_HEIGHT = 84.dp

/**
 * One block's settings, docked under the player rather than over it, so the
 * block stays in sight and every choice lands on it while the panel is open.
 *
 * One page, most wanted first: what the block does, then how it looks (the
 * players' own pieces, drawn for real), then its colour; shape, sizes and the
 * switches wait under More. Each choice is one undo step.
 */
@Composable
internal fun BlockSettingsPanel(
    block: LayoutBlock,
    background: LayoutBackground,
    viewModel: PlayerViewModel,
    playerScheme: ColorScheme,
    /** The player's safe area at its real size, so previews are drawn at the block's own size. */
    areaSize: DpSize,
    onChange: (LayoutBlock) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The block each id last showed, so the page fading out keeps its own.
    val seen = remember { mutableMapOf<String, LayoutBlock>() }
    seen[block.id] = block
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = block.id,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "BlockSettingsPage"
        ) { id ->
            val shown = if (id == block.id) block else seen[id] ?: block
            PanelPage(
                block = shown,
                background = background,
                viewModel = viewModel,
                playerScheme = playerScheme,
                areaSize = areaSize,
                onChange = onChange,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PanelPage(
    block: LayoutBlock,
    background: LayoutBackground,
    viewModel: PlayerViewModel,
    playerScheme: ColorScheme,
    areaSize: DpSize,
    onChange: (LayoutBlock) -> Unit,
    onClose: () -> Unit,
) {
    var showMore by remember(block.id) { mutableStateOf(false) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(modifier = Modifier.fillMaxSize()) {
        // The block's name, and the way out.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (block.kind == LayoutBlockKind.BUTTON) block.action.icon() else block.kind.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = block.editorLabel(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FilledTonalIconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cpl_close_settings))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The panel scrolls: a plain column clips what is past its
                // height with nothing logged.
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = navBottom + 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            val styled = block.kind != LayoutBlockKind.PANEL && OFFERED_SKINS.size > 1
            val style: @Composable () -> Unit = {
                if (styled) {
                    StyleSection(block, background, viewModel, playerScheme, areaSize, onChange)
                }
            }
            val more: (@Composable () -> Unit)?
            when (block.kind) {
                LayoutBlockKind.BUTTON -> {
                    Padded {
                        ConfigSection(stringResource(R.string.cpl_cfg_action)) {
                            ActionGrid(
                                actions = LayoutAction.forButtons,
                                selected = block.action,
                                onSelect = { onChange(block.copy(action = it)) }
                            )
                        }
                    }
                    style()
                    Padded { LookSection(block, onChange) }
                    Padded { ColorSection(block, onChange) }
                    more = { ShapeSection(block, onChange, BlockShape.entries) }
                }
                LayoutBlockKind.TRANSPORT -> {
                    style()
                    Padded { LookSection(block, onChange) }
                    Padded { ColorSection(block, onChange) }
                    more = {
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
                }
                LayoutBlockKind.COVER -> {
                    Padded {
                        ConfigSection(stringResource(R.string.cpl_cfg_tap)) {
                            ActionGrid(
                                actions = LayoutAction.forCover,
                                selected = block.action,
                                onSelect = { onChange(block.copy(action = it)) }
                            )
                        }
                    }
                    style()
                    more = {
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
                }
                LayoutBlockKind.TITLE -> {
                    style()
                    Padded {
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
                    }
                    Padded {
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
                    }
                    more = {
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
                }
                LayoutBlockKind.SCRUBBER -> {
                    style()
                    Padded { ColorSection(block, onChange) }
                    more = {
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
                }
                LayoutBlockKind.UP_NEXT -> {
                    style()
                    Padded { ColorSection(block, onChange) }
                    more = {
                        ShapeSection(
                            block, onChange,
                            listOf(BlockShape.SQUARE, BlockShape.ROUNDED, BlockShape.ROUND)
                        )
                    }
                }
                LayoutBlockKind.PANEL -> {
                    Padded { ColorSection(block, onChange) }
                    Padded { ShapeSection(block, onChange, BlockShape.entries) }
                    more = null
                }
                LayoutBlockKind.LYRICS -> {
                    style()
                    Padded { ConfigNote(stringResource(R.string.cpl_cfg_lyrics_note)) }
                    more = null
                }
                LayoutBlockKind.VISUALIZER -> {
                    style()
                    Padded { ConfigNote(stringResource(R.string.cpl_cfg_visualizer_note)) }
                    more = null
                }
                LayoutBlockKind.HEADER, LayoutBlockKind.TOOLBAR, LayoutBlockKind.TIME -> {
                    style()
                    more = null
                }
            }

            if (more != null) {
                Padded {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MoreRow(open = showMore, onToggle = { showMore = !showMore })
                        AnimatedVisibility(
                            visible = showMore,
                            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { more() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { content() }
}

/** "More options", with a chevron that turns over as it opens. */
@Composable
private fun MoreRow(open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "MoreChevron"
    )
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (open) R.string.cpl_fewer_options else R.string.cpl_more_options),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = turn }
            )
        }
    }
}

/**
 * The block as each player draws it, side by side, on the layout's own
 * background: a tap takes that look. Drawn by the real pieces, so what is
 * picked is what lands on the screen.
 */
@Composable
private fun StyleSection(
    block: LayoutBlock,
    background: LayoutBackground,
    viewModel: PlayerViewModel,
    playerScheme: ColorScheme,
    areaSize: DpSize,
    onChange: (LayoutBlock) -> Unit,
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
    val blockSize = DpSize(
        (areaSize.width * block.width).coerceAtLeast(1.dp),
        (areaSize.height * block.height).coerceAtLeast(1.dp)
    )
    // Wide blocks get wide tiles, so a row of controls is not drawn as a speck.
    val tileWidth = (STYLE_TILE_HEIGHT * (blockSize.width / blockSize.height)).coerceIn(STYLE_TILE_HEIGHT, 168.dp)
    val tileSize = DpSize(tileWidth, STYLE_TILE_HEIGHT)
    val fit = minOf(tileSize.width / blockSize.width, tileSize.height / blockSize.height) * 0.86f
    val listState = rememberLazyListState()
    // Open on the look the block has, not the first one.
    LaunchedEffect(block.id) {
        val index = OFFERED_SKINS.indexOf(block.skin)
        if (index > 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.cpl_cfg_skin),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(OFFERED_SKINS, key = { it.id }) { skin ->
                StyleTile(
                    block = block.copy(skin = skin),
                    label = skin.style?.let { playerStyleLabel(it) } ?: stringResource(R.string.cpl_skin_koda),
                    selected = skin == block.skin,
                    background = background,
                    host = host,
                    playerScheme = playerScheme,
                    tileSize = tileSize,
                    blockSize = blockSize,
                    fit = fit,
                    onClick = { if (skin != block.skin) onChange(block.copy(skin = skin)) }
                )
            }
        }
    }
}

@Composable
private fun StyleTile(
    block: LayoutBlock,
    label: String,
    selected: Boolean,
    background: LayoutBackground,
    host: LayoutHost,
    playerScheme: ColorScheme,
    tileSize: DpSize,
    blockSize: DpSize,
    fit: Float,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (selected) 22.dp else 16.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(tileSize.width, tileSize.height)
                .clip(shape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant,
                    shape = shape
                ),
            contentAlignment = Alignment.Center
        ) {
            MaterialTheme(colorScheme = playerScheme) {
                val ink = rememberLayoutInk(background)
                Box(Modifier.matchParentSize().background(ink.field))
                Box(
                    modifier = Modifier
                        .requiredSize(blockSize)
                        .graphicsLayer {
                            scaleX = fit
                            scaleY = fit
                        }
                ) {
                    LayoutBlockPreview(block = block, host = host, ink = ink)
                }
            }
            // Over the preview, so the pieces under it never take the touch.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(role = Role.RadioButton, onClick = onClick)
                    .semantics { contentDescription = label }
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
