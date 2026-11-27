package com.ivor.ivormusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.data.isUnknownArtist
import com.ivor.ivormusic.data.isUnknownTitle
import com.ivor.ivormusic.data.playerlayout.AreaInsets
import com.ivor.ivormusic.data.playerlayout.BlockShape
import com.ivor.ivormusic.data.playerlayout.ButtonLook
import com.ivor.ivormusic.data.playerlayout.LayoutAction
import com.ivor.ivormusic.data.playerlayout.LayoutBackground
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutBlockKind
import com.ivor.ivormusic.data.playerlayout.LayoutColor
import com.ivor.ivormusic.data.playerlayout.LayoutGeometry
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.data.playerlayout.LayoutTextAlign
import com.ivor.ivormusic.data.playerlayout.LayoutTextSize
import com.ivor.ivormusic.data.playerlayout.PlayerLayout
import com.ivor.ivormusic.data.playerlayout.SnapRect
import com.ivor.ivormusic.ui.components.LikeBurstIcon
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.theme.MontserratFamily
import kotlin.math.roundToInt

/*
 * The pieces a custom player layout is drawn from. The Custom style and the
 * layout editor both draw through [CustomPlayerCanvas], so what the editor
 * shows is the player, not a picture of it.
 */

/** How far the tonal field is taken toward the surface in a dark scheme; Hero's value. */
private const val TONAL_DARK_FIELD_DEPTH = 0.5f

/**
 * The two colours a layout draws its own furniture in: the ground and the ink
 * on it. Both are roles of the player's scheme, which is the cover's when
 * album colours are on.
 */
@Immutable
internal class LayoutInk(val field: Color, val ink: Color)

@Composable
internal fun rememberLayoutInk(background: LayoutBackground): LayoutInk {
    val scheme = MaterialTheme.colorScheme
    return when (background) {
        LayoutBackground.TONAL -> {
            val field = if (scheme.surface.luminance() < 0.5f) {
                lerp(scheme.primaryContainer, scheme.surface, TONAL_DARK_FIELD_DEPTH)
            } else {
                scheme.primaryContainer
            }
            LayoutInk(field, scheme.onPrimaryContainer)
        }
        LayoutBackground.AMBIENT -> LayoutInk(scheme.background, scheme.onBackground)
        LayoutBackground.SOLID, LayoutBackground.COVER -> LayoutInk(scheme.surface, scheme.onSurface)
        LayoutBackground.CONTAINER -> LayoutInk(scheme.primaryContainer, scheme.onPrimaryContainer)
        LayoutBackground.BOARD, LayoutBackground.SPLIT ->
            LayoutInk(scheme.surfaceContainerLowest, scheme.onSurface)
        // Canvas is white type over a dark scrim, whatever the theme.
        LayoutBackground.CANVAS -> LayoutInk(CanvasScrim, CanvasGlyph)
    }
}

/**
 * Everything a block needs from whoever is drawing it. The player passes the
 * real actions; the editor passes [interactive] false, so blocks draw exactly
 * as they will but take no touch.
 */
@Immutable
internal class LayoutHost(
    val viewModel: PlayerViewModel,
    val interactive: Boolean,
    val onAction: (LayoutAction) -> Unit,
    val onArtistClick: (String) -> Unit,
    val lyricsOpen: Boolean,
    val sleepTimerActive: Boolean,
    val swipeToSkip: SwipeToSkipState?,
    /** Which way the last skip went, for covers that slide the next one in. */
    val skipDirection: Int = 1,
    /** Classic's rising panel, when the layout carries Classic's tab bar. */
    val classicPanel: ClassicPanelState? = null,
)

/** Where a block sits on the whole screen, for pieces drawn against the screen (Canvas's scrim). */
@Immutable
internal class BlockFrame(val rect: SnapRect, val screenWidth: Float, val screenHeight: Float)

internal val LocalBlockFrame = compositionLocalOf<BlockFrame?> { null }

/**
 * Lays a piece out over the whole screen from inside a block, for a block
 * whose player draws that piece full screen (Canvas's lyrics). It still draws
 * where the block sits in the stack, so blocks above it stay above it.
 */
internal fun Modifier.screenSized(frame: BlockFrame): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints.fixed(
            frame.screenWidth.roundToInt().coerceAtLeast(0),
            frame.screenHeight.roundToInt().coerceAtLeast(0)
        )
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(-frame.rect.left.roundToInt(), -frame.rect.top.roundToInt())
    }
}

/** The background, then every block where it was put. */
@Composable
internal fun CustomPlayerCanvas(
    layout: PlayerLayout,
    host: LayoutHost,
    safeArea: PaddingValues,
    ambientBackground: Boolean,
    modifier: Modifier = Modifier,
    /** Applied to the blocks only, over a background that stays put. */
    blocksModifier: Modifier = Modifier,
    /**
     * Applied to each block's own slot: the editor lifts a block while it is
     * carried and fades one out as it is removed. Read state in a deferred
     * lambda (`graphicsLayer { }`), so a moving value never recomposes.
     */
    blockModifier: (LayoutBlock) -> Modifier = { Modifier },
) {
    val song by host.viewModel.currentSong.collectAsState()
    val ink = rememberLayoutInk(layout.background)
    // One decoder for motion artwork: only the first cover may show it.
    val motionCoverId = layout.blocks.firstOrNull { it.kind == LayoutBlockKind.COVER }?.id
    Box(modifier = modifier.fillMaxSize()) {
        LayoutBackgroundLayer(
            background = layout.background,
            song = song,
            ambientEnabled = ambientBackground,
            ink = ink,
            layout = layout,
            safeArea = safeArea,
        )
        LayoutBlocks(
            blocks = layout.blocks,
            safeArea = safeArea,
            modifier = blocksModifier,
            blockModifier = blockModifier,
        ) { block ->
            LayoutBlockContent(
                block = block,
                host = host,
                ink = ink,
                motionArtwork = block.id == motionCoverId,
            )
        }
    }
}

/**
 * Places each block at its stored rectangle in one layout pass. Positions are
 * measured from the left edge in either direction, because a layout is a
 * picture someone arranged, not a reading order.
 */
@Composable
internal fun LayoutBlocks(
    blocks: List<LayoutBlock>,
    safeArea: PaddingValues,
    modifier: Modifier = Modifier,
    blockModifier: (LayoutBlock) -> Modifier = { Modifier },
    content: @Composable (LayoutBlock) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val screenWidth = constraints.maxWidth.toFloat()
    val screenHeight = constraints.maxHeight.toFloat()
    val frameInsets = areaInsetsPx(safeArea, direction, density)
    Layout(
        content = {
            blocks.forEach { block ->
                key(block.id) {
                    val frame = BlockFrame(
                        LayoutGeometry.bounds(block, screenWidth, screenHeight, frameInsets),
                        screenWidth,
                        screenHeight
                    )
                    Box(modifier = Modifier.layoutId(block.id).then(blockModifier(block))) {
                        CompositionLocalProvider(LocalBlockFrame provides frame) { content(block) }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val insets = areaInsetsPx(safeArea, direction, this)
        val byId = blocks.associateBy { it.id }
        val placed = measurables.mapNotNull { measurable ->
            val block = byId[measurable.layoutId] ?: return@mapNotNull null
            val rect = LayoutGeometry.bounds(block, width.toFloat(), height.toFloat(), insets)
            val placeable = measurable.measure(
                Constraints.fixed(
                    rect.width.roundToInt().coerceAtLeast(0),
                    rect.height.roundToInt().coerceAtLeast(0)
                )
            )
            placeable to rect
        }
        layout(width, height) {
            placed.forEach { (placeable, rect) ->
                placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
            }
        }
    }
    }
}

internal fun areaInsetsPx(safeArea: PaddingValues, direction: LayoutDirection, density: Density) =
    with(density) {
        AreaInsets(
            left = safeArea.calculateLeftPadding(direction).toPx(),
            top = safeArea.calculateTopPadding().toPx(),
            right = safeArea.calculateRightPadding(direction).toPx(),
            bottom = safeArea.calculateBottomPadding().toPx(),
        )
    }

@Composable
internal fun LayoutBackgroundLayer(
    background: LayoutBackground,
    song: Song?,
    ambientEnabled: Boolean,
    ink: LayoutInk,
    modifier: Modifier = Modifier,
    layout: PlayerLayout? = null,
    safeArea: PaddingValues = PaddingValues(),
) {
    val scheme = MaterialTheme.colorScheme
    when (background) {
        LayoutBackground.CONTAINER -> Box(modifier.fillMaxSize().background(scheme.primaryContainer))
        LayoutBackground.BOARD -> Box(modifier.fillMaxSize().background(scheme.surfaceContainerLowest))
        // Sticker's board: two flat tones meeting on a hard edge, no gradient.
        LayoutBackground.SPLIT -> Column(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.45f).background(scheme.surfaceContainerLow))
            Box(Modifier.fillMaxWidth().weight(0.55f).background(scheme.surfaceContainerLowest))
        }
        // Canvas's field, joined to the foot of the first cover so the seam
        // is continuous by construction, under Canvas's scrim.
        LayoutBackground.CANVAS -> BoxWithConstraints(
            modifier.fillMaxSize().background(scheme.surfaceContainerLowest)
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val insets = areaInsetsPx(safeArea, LocalLayoutDirection.current, LocalDensity.current)
            val joinAt = layout?.blocks?.firstOrNull { it.kind == LayoutBlockKind.COVER }
                ?.let { LayoutGeometry.bounds(it, width, height, insets).bottom / height }
                ?: CANVAS_ART_FRACTION
            CanvasField(song = song, joinAt = joinAt, scrim = CanvasScrim, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(canvasScrimBrush()))
        }
        LayoutBackground.AMBIENT -> Box(modifier.fillMaxSize().background(ink.field)) {
            ChromaticMistBackground(
                // Not highResThumbnailUrl alone: it is null for every song on
                // this device, and the mist answers null with a flat field.
                albumArtUrl = song?.highResThumbnailUrl
                    ?: song?.thumbnailUrl
                    ?: song?.albumArtUri?.toString(),
                enabled = ambientEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }
        LayoutBackground.SOLID, LayoutBackground.TONAL ->
            Box(modifier.fillMaxSize().background(ink.field))
        LayoutBackground.COVER -> Box(modifier.fillMaxSize().background(scheme.surface)) {
            val artSong = song?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
            if (artSong != null) {
                PlayerArtwork(
                    song = artSong,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    motionEligible = false
                )
            }
            // Something for the status bar to sit on, and the surface rising
            // from the foot so text and controls read on any cover.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to scheme.surface.copy(alpha = 0.45f),
                            0.16f to scheme.surface.copy(alpha = 0f),
                            0.4f to scheme.surface.copy(alpha = 0f),
                            0.72f to scheme.surface.copy(alpha = 0.82f),
                            1f to scheme.surface
                        )
                    )
            )
        }
    }
}

/**
 * One block drawn on its own at whatever size it is given, for the editor's
 * style previews: the same pieces as on the screen, with no motion artwork.
 */
@Composable
internal fun LayoutBlockPreview(block: LayoutBlock, host: LayoutHost, ink: LayoutInk) {
    LayoutBlockContent(block = block, host = host, ink = ink, motionArtwork = false)
}

@Composable
private fun LayoutBlockContent(
    block: LayoutBlock,
    host: LayoutHost,
    ink: LayoutInk,
    motionArtwork: Boolean,
) {
    // A block in a player's skin is drawn by that player's own pieces; what a
    // skin has no piece for falls back to Koda's block in the skin's colours.
    when (block.skin) {
        LayoutSkin.HERO -> HeroSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.EDITORIAL -> EditorialSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.CANVAS -> CanvasSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.CLASSIC -> ClassicSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.GESTURE -> GestureSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.BENTO -> BentoSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.STICKER -> StickerSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.MORPH -> MorphSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.DIAL -> DialSkinBlock(block, host, motionArtwork) { skinInk ->
            GenericBlockContent(block, host, skinInk, motionArtwork)
        }
        LayoutSkin.KODA -> GenericBlockContent(block, host, ink, motionArtwork)
    }
}

@Composable
private fun GenericBlockContent(
    block: LayoutBlock,
    host: LayoutHost,
    ink: LayoutInk,
    motionArtwork: Boolean,
) {
    when (block.kind) {
        LayoutBlockKind.COVER -> CoverBlock(block, host, ink, motionArtwork)
        LayoutBlockKind.TITLE -> TitleBlock(block, host, ink)
        LayoutBlockKind.SCRUBBER -> ScrubberBlock(block, host, ink)
        LayoutBlockKind.TRANSPORT -> TransportBlock(block, host, ink)
        LayoutBlockKind.BUTTON -> LayoutActionButton(
            action = block.action,
            look = block.look,
            color = block.color,
            shape = block.shape,
            host = host,
            ink = ink,
            modifier = Modifier.fillMaxSize()
        )
        LayoutBlockKind.LYRICS -> LyricsBlock(host, ink)
        LayoutBlockKind.VISUALIZER -> PlayerVisualizerBlock(Modifier.fillMaxSize())
        LayoutBlockKind.UP_NEXT -> UpNextBlock(block, host, ink)
        LayoutBlockKind.PANEL -> PanelBlock(block)
        LayoutBlockKind.HEADER -> HeaderBlock(block, host, ink)
        LayoutBlockKind.TOOLBAR -> ToolbarBlock(host)
        LayoutBlockKind.TIME -> TimeBlock(host, ink)
    }
}

/* ------------------------------------------------------------------ */
/* Shapes and colours                                                  */
/* ------------------------------------------------------------------ */

/** Rounded corners never larger than [max] or a share of the smaller side. */
private class SoftCornerShape(private val max: Dp, private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = minOf(with(density) { max.toPx() }, size.minDimension * fraction)
        return Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                0f, 0f, size.width, size.height,
                androidx.compose.ui.geometry.CornerRadius(radius)
            )
        )
    }

    override fun equals(other: Any?) =
        other is SoftCornerShape && other.max == max && other.fraction == fraction

    override fun hashCode() = max.hashCode() * 31 + fraction.hashCode()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BlockShape.composeShape(): Shape = when (this) {
    BlockShape.SQUARE -> RectangleShape
    BlockShape.ROUNDED -> remember { SoftCornerShape(28.dp, 0.22f) }
    BlockShape.ROUND -> RoundedCornerShape(percent = 50)
    BlockShape.COOKIE -> MaterialShapes.Cookie9Sided.toShape()
    BlockShape.CLOVER -> MaterialShapes.Clover4Leaf.toShape()
    BlockShape.FLOWER -> MaterialShapes.Flower.toShape()
    BlockShape.ARCH -> MaterialShapes.Arch.toShape()
    BlockShape.SOFT_BURST -> MaterialShapes.SoftBurst.toShape()
    BlockShape.GEM -> MaterialShapes.Gem.toShape()
    BlockShape.SUNNY -> MaterialShapes.Sunny.toShape()
}

/** A colour family as its four roles. */
@Immutable
private class RoleColors(val role: Color, val onRole: Color, val container: Color, val onContainer: Color)

@Composable
private fun roleColors(color: LayoutColor, ink: LayoutInk): RoleColors {
    val scheme = MaterialTheme.colorScheme
    return when (color) {
        LayoutColor.PRIMARY -> RoleColors(
            scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer
        )
        LayoutColor.SECONDARY -> RoleColors(
            scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer
        )
        LayoutColor.TERTIARY -> RoleColors(
            scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer
        )
        // The layout's own ground and ink: a neutral button on a plate of the
        // field reads over any cover, the way Hero's chrome does.
        LayoutColor.NEUTRAL -> RoleColors(ink.ink, ink.field, ink.field.copy(alpha = 0.78f), ink.ink)
    }
}

/* ------------------------------------------------------------------ */
/* Buttons                                                             */
/* ------------------------------------------------------------------ */

@Composable
internal fun LayoutAction.label(): String = stringResource(
    when (this) {
        LayoutAction.NONE -> R.string.cpl_action_none
        LayoutAction.PLAY_PAUSE -> R.string.cpl_action_play_pause
        LayoutAction.PREVIOUS -> R.string.cpl_action_previous
        LayoutAction.NEXT -> R.string.cpl_action_next
        LayoutAction.SEEK_BACK -> R.string.cpl_action_seek_back
        LayoutAction.SEEK_FORWARD -> R.string.cpl_action_seek_forward
        LayoutAction.LIKE -> R.string.cpl_action_like
        LayoutAction.SHUFFLE -> R.string.cpl_action_shuffle
        LayoutAction.REPEAT -> R.string.cpl_action_repeat
        LayoutAction.QUEUE -> R.string.cpl_action_queue
        LayoutAction.LYRICS -> R.string.cpl_action_lyrics
        LayoutAction.SLEEP_TIMER -> R.string.cpl_action_sleep_timer
        LayoutAction.RADIO -> R.string.cpl_action_radio
        LayoutAction.STANDBY -> R.string.cpl_action_standby
        LayoutAction.OPTIONS -> R.string.cpl_action_options
        LayoutAction.COLLAPSE -> R.string.cpl_action_collapse
        LayoutAction.DOWNLOAD -> R.string.cpl_action_download
    }
)

/** The icon an action shows at rest; play and repeat change with their state. */
internal fun LayoutAction.icon(): ImageVector = when (this) {
    LayoutAction.NONE -> Icons.Rounded.Block
    LayoutAction.PLAY_PAUSE -> Icons.Rounded.PlayArrow
    LayoutAction.PREVIOUS -> Icons.Rounded.SkipPrevious
    LayoutAction.NEXT -> Icons.Rounded.SkipNext
    LayoutAction.SEEK_BACK -> Icons.Rounded.Replay10
    LayoutAction.SEEK_FORWARD -> Icons.Rounded.Forward10
    LayoutAction.LIKE -> Icons.Rounded.Favorite
    LayoutAction.SHUFFLE -> Icons.Rounded.Shuffle
    LayoutAction.REPEAT -> Icons.Rounded.Repeat
    LayoutAction.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
    LayoutAction.LYRICS -> Icons.Rounded.Lyrics
    LayoutAction.SLEEP_TIMER -> Icons.Rounded.Bedtime
    LayoutAction.RADIO -> Icons.Rounded.Radio
    LayoutAction.STANDBY -> Icons.Rounded.Nightlight
    LayoutAction.OPTIONS -> Icons.Rounded.MoreVert
    LayoutAction.COLLAPSE -> Icons.Rounded.KeyboardArrowDown
    LayoutAction.DOWNLOAD -> Icons.Rounded.Download
}

/** What a button needs to know about the player to draw its action's state. */
@Immutable
internal class LayoutActionState(
    val isPlaying: Boolean,
    val buffering: Boolean,
    /** On or off for a toggle; null for an action with no state. */
    val checked: Boolean?,
    val repeatOne: Boolean,
    /** A download in progress, or a file that is already on the device. */
    val downloading: Boolean = false,
    val localFile: Boolean = false,
) {
    fun iconFor(action: LayoutAction, glyphs: ActionGlyphs = ActionGlyphs.ROUNDED): ImageVector {
        if (glyphs == ActionGlyphs.FILLED) filledGlyph(action, isPlaying, repeatOne)?.let { return it }
        return roundedIconFor(action)
    }

    private fun roundedIconFor(action: LayoutAction): ImageVector = when {
        action == LayoutAction.PLAY_PAUSE && isPlaying -> Icons.Rounded.Pause
        action == LayoutAction.REPEAT && repeatOne -> Icons.Rounded.RepeatOne
        action == LayoutAction.DOWNLOAD && localFile -> Icons.Rounded.Smartphone
        action == LayoutAction.DOWNLOAD && checked == true -> Icons.Rounded.CheckCircle
        else -> action.icon()
    }
}

/** Collects only what [action] shows, so a button recomposes for its own state. */
@Composable
internal fun rememberLayoutActionState(action: LayoutAction, host: LayoutHost): LayoutActionState {
    val viewModel = host.viewModel
    return when (action) {
        LayoutAction.PLAY_PAUSE -> {
            val playing by viewModel.isPlaying.collectAsState()
            val isBuffering by viewModel.isBuffering.collectAsState()
            val playWhenReady by viewModel.playWhenReady.collectAsState()
            LayoutActionState(playing, isBuffering && playWhenReady && !playing, null, false)
        }
        LayoutAction.LIKE -> {
            val liked by viewModel.isCurrentSongLiked.collectAsState()
            LayoutActionState(false, false, liked, false)
        }
        LayoutAction.SHUFFLE -> {
            val shuffle by viewModel.shuffleModeEnabled.collectAsState()
            LayoutActionState(false, false, shuffle, false)
        }
        LayoutAction.REPEAT -> {
            val repeat by viewModel.repeatMode.collectAsState()
            LayoutActionState(false, false, repeat != Player.REPEAT_MODE_OFF, repeat == Player.REPEAT_MODE_ONE)
        }
        LayoutAction.DOWNLOAD -> {
            val song by viewModel.currentSong.collectAsState()
            val downloadingIds by viewModel.downloadingIds.collectAsState()
            val downloadedSongs by viewModel.downloadedSongs.collectAsState()
            val current = song
            val local = current != null && viewModel.isLocalOriginal(current)
            val downloading = current != null && current.id in downloadingIds
            val downloaded = current != null && downloadedSongs.any { it.id == current.id }
            LayoutActionState(false, false, downloaded || downloading || local, false, downloading, local)
        }
        LayoutAction.LYRICS -> LayoutActionState(false, false, host.lyricsOpen, false)
        LayoutAction.SLEEP_TIMER -> LayoutActionState(false, false, host.sleepTimerActive, false)
        else -> LayoutActionState(false, false, null, false)
    }
}

/** The icon for [action] in its current state; Like bursts as it does everywhere. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LayoutActionIcon(
    action: LayoutAction,
    state: LayoutActionState,
    contentDescription: String,
    modifier: Modifier,
    glyphs: ActionGlyphs = ActionGlyphs.ROUNDED,
) {
    when {
        state.buffering -> LoadingIndicator(modifier = modifier, color = androidx.compose.material3.LocalContentColor.current)
        state.downloading -> androidx.compose.material3.CircularWavyProgressIndicator(
            modifier = modifier,
            color = androidx.compose.material3.LocalContentColor.current
        )
        action == LayoutAction.LIKE -> BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            LikeBurstIcon(isFavorite = state.checked == true, iconSize = min(maxWidth, maxHeight))
        }
        else -> Icon(state.iconFor(action, glyphs), contentDescription = contentDescription, modifier = modifier)
    }
}

/** An icon at a player's own size, shrinking with a block made smaller than the player drew it. */
internal fun Modifier.layoutIcon(size: Dp): Modifier =
    fillMaxSize(0.62f).sizeIn(maxWidth = size, maxHeight = size).aspectRatio(1f)

/**
 * [action] through [host] as one lambda that keeps its identity and always
 * does what the layout says now.
 *
 * A gesture detector keeps the lambda it started with until its key changes,
 * and several of the players' own pieces start one, so a lambda that captured
 * `block.action` went on doing the old thing after the layout was edited -
 * until the player was closed and opened again.
 */
@Composable
internal fun rememberLayoutCallback(
    host: LayoutHost,
    action: LayoutAction,
    enabled: Boolean = true,
): () -> Unit {
    val currentHost by rememberUpdatedState(host)
    val currentAction by rememberUpdatedState(action)
    val currentEnabled by rememberUpdatedState(enabled)
    return remember {
        {
            if (currentHost.interactive && currentEnabled && currentAction != LayoutAction.NONE) {
                currentHost.onAction(currentAction)
            }
        }
    }
}

/** What a tap on [block] does, read afresh on every tap. */
@Composable
internal fun rememberBlockAction(block: LayoutBlock, host: LayoutHost): () -> Unit =
    rememberLayoutCallback(host, block.action)

/**
 * A cover's gestures in a custom layout: the follow and the swipe to skip,
 * the double-tap seek with the block's own single-tap action, and the hold
 * that opens the style wheel. Nothing in the editor.
 */
@Composable
internal fun Modifier.layoutCoverGestures(
    block: LayoutBlock,
    host: LayoutHost,
    /** How far the cover follows the finger; null for a cover that moves itself. */
    follow: Float? = SwipeToSkipDefaults.ArtFollow,
    /** Off while the cover is showing the lyrics, which scroll and seek. */
    enabled: Boolean = true,
): Modifier {
    val tap = rememberBlockAction(block, host)
    val swipe = host.swipeToSkip
    val canSwipe = host.interactive && block.swipeToSkip && swipe != null
    if (!host.interactive || !enabled) return this
    val seek = LocalCoverSeek.current
    val wheel = LocalPlayerStyleWheelController.current
    return this
        .then(if (canSwipe && follow != null) Modifier.swipeToSkipFollow(swipe!!, follow) else Modifier)
        .coverTaps(seek, tap)
        .then(if (canSwipe) Modifier.swipeToSkip(swipe!!) else Modifier)
        .styleWheelHold(wheel)
}

/** A title's swipe to skip in a custom layout, following the finger at text speed. */
internal fun Modifier.layoutTitleSwipe(
    block: LayoutBlock,
    host: LayoutHost,
    factor: Float = SwipeToSkipDefaults.TextFollow,
): Modifier {
    val swipe = host.swipeToSkip
    return if (host.interactive && block.swipeToSkip && swipe != null) {
        this.swipeToSkipFollow(swipe, factor).swipeToSkip(swipe)
    } else {
        this
    }
}

/**
 * A player's own title in its block: drawn at its natural height with its foot
 * on the block's foot, the way every player stacks its title above the
 * controls. A title that wraps to more lines than it had when the layout was
 * made grows up past the block's top rather than losing its artist line.
 */
@Composable
internal fun SkinTitleFrame(
    block: LayoutBlock,
    host: LayoutHost,
    factor: Float = SwipeToSkipDefaults.TextFollow,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layoutTitleSwipe(block, host, factor)
    ) {
        content(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
        )
    }
}

/**
 * Whether a cover in this skin turns into the lyrics in place, as its player
 * does. Koda's own blocks open them over the screen instead, and Classic
 * keeps them in its rising panel.
 */
internal val LayoutSkin.lyricsInCover: Boolean
    get() = this != LayoutSkin.KODA && this != LayoutSkin.CLASSIC

/** Whether [layout]'s first cover shows the lyrics itself when they are opened. */
internal fun PlayerLayout.lyricsInCover(): Boolean =
    blocks.firstOrNull { it.kind == LayoutBlockKind.COVER }?.skin?.lyricsInCover == true

/** The lyrics a cover shows in place of its art, in its player's colours. */
@Composable
internal fun CoverLyrics(
    host: LayoutHost,
    primary: Color,
    ink: Color,
    inkVariant: Color,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    val viewModel = host.viewModel
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    SyncedLyricsView(
        lyricsResult = lyricsResult,
        currentPositionMs = progress,
        isPlaying = isPlaying,
        onSeekTo = { viewModel.seekTo(it) },
        modifier = modifier,
        primaryColor = primary,
        onSurfaceColor = ink,
        onSurfaceVariantColor = inkVariant,
        interactive = host.interactive,
        backgroundColor = background
    )
}

/**
 * One button doing [action], in any look, colour and shape.
 *
 * A toggle that is on (shuffle, repeat, like, lyrics, the sleep timer) fills
 * with its colour whatever its resting look, so its state reads by tone; a
 * plain one only takes the accent. Play squares its shoulders while playing
 * when it is round, Hero's signature, and every button squishes under the
 * thumb on the bouncy spring.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LayoutActionButton(
    action: LayoutAction,
    look: ButtonLook,
    color: LayoutColor,
    shape: BlockShape,
    host: LayoutHost,
    ink: LayoutInk,
    modifier: Modifier = Modifier,
) {
    val state = rememberLayoutActionState(action, host)
    val isPlaying = state.isPlaying
    val buffering = state.buffering
    val checked = state.checked

    val roles = roleColors(color, ink)
    val scheme = MaterialTheme.colorScheme
    val accentForNeutral = if (color == LayoutColor.NEUTRAL) scheme.primary else roles.role
    val effectiveLook = when {
        look == ButtonLook.PLAIN -> ButtonLook.PLAIN
        checked == true -> ButtonLook.FILLED
        checked == false && look == ButtonLook.FILLED -> ButtonLook.TONAL
        else -> look
    }
    val container: Color
    val content: Color
    var border: BorderStroke? = null
    when (effectiveLook) {
        ButtonLook.FILLED -> {
            container = if (checked == true && color == LayoutColor.NEUTRAL) scheme.primary else roles.role
            content = if (checked == true && color == LayoutColor.NEUTRAL) scheme.onPrimary else roles.onRole
        }
        ButtonLook.TONAL -> {
            container = roles.container
            content = roles.onContainer
        }
        ButtonLook.OUTLINED -> {
            content = roles.role
            container = roles.role.copy(alpha = 0.06f)
            border = BorderStroke(1.5.dp, roles.role.copy(alpha = 0.55f))
        }
        ButtonLook.PLAIN -> {
            container = Color.Transparent
            content = when (checked) {
                true -> accentForNeutral
                false -> ink.ink.copy(alpha = 0.7f)
                null -> if (color == LayoutColor.NEUTRAL) ink.ink else roles.role
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squish by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "LayoutButtonSquish"
    )
    // Round play buttons square their shoulders while playing. The spring
    // overshoots past a circle, so the corner is held at half the side.
    val morphs = action == LayoutAction.PLAY_PAUSE && shape == BlockShape.ROUND
    val corner by animateFloatAsState(
        targetValue = if (morphs && isPlaying) 30f else 50f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "LayoutPlayCorner"
    )
    val outline = if (morphs) {
        RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50))
    } else {
        shape.composeShape()
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            onClick = { host.onAction(action) },
            enabled = host.interactive,
            interactionSource = interaction,
            shape = outline,
            color = container,
            contentColor = content,
            border = border,
            modifier = Modifier
                .then(if (shape.isPolygon) Modifier.aspectRatio(1f) else Modifier.fillMaxSize())
                .graphicsLayer {
                    scaleX = squish
                    scaleY = squish
                }
        ) {
            BoxWithConstraints(contentAlignment = Alignment.Center) {
                val iconSize = (min(maxWidth, maxHeight) * 0.46f).coerceIn(18.dp, 64.dp)
                when {
                    buffering -> LoadingIndicator(
                        modifier = Modifier.size(iconSize),
                        color = content
                    )
                    action == LayoutAction.LIKE -> LikeBurstIcon(
                        isFavorite = checked == true,
                        iconSize = iconSize,
                        tint = content
                    )
                    else -> Icon(
                        imageVector = state.iconFor(action),
                        contentDescription = action.label(),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Blocks                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun CoverBlock(block: LayoutBlock, host: LayoutHost, ink: LayoutInk, motionArtwork: Boolean) {
    val song by host.viewModel.currentSong.collectAsState()
    val isPlaying by host.viewModel.isPlaying.collectAsState()
    val shape = block.shape.composeShape()
    val spins = block.motion && (block.shape == BlockShape.ROUND || block.shape.isPolygon)

    // Written from a frame clock and read only in the graphics layer, so a
    // turning cover never recomposes; paused, it holds where it is.
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(spins, isPlaying) {
        if (!spins || !isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle = (angle + (now - last) / 1_000_000_000f * 24f) % 360f
                last = now
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .then(
                    if (block.shape == BlockShape.ROUND || block.shape.isPolygon) {
                        Modifier.aspectRatio(1f)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .layoutCoverGestures(block, host)
                .graphicsLayer { if (spins) rotationZ = angle }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            val artSong = song?.takeIf { it.thumbnailUrl != null || it.albumArtUri != null }
            if (artSong != null) {
                PlayerArtwork(
                    song = artSong,
                    contentDescription = stringResource(R.string.cpl_block_cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    motionEligible = motionArtwork
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(0.36f)
                )
            }
            if (block.fade) {
                // Faded to the field at zero alpha, never to Transparent,
                // which greys the band.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to ink.field.copy(alpha = 0f),
                                0.5f to ink.field.copy(alpha = 0f),
                                0.78f to ink.field.copy(alpha = 0.6f),
                                1f to ink.field
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun TitleBlock(block: LayoutBlock, host: LayoutHost, ink: LayoutInk) {
    val song by host.viewModel.currentSong.collectAsState()
    val title = when {
        song == null -> stringResource(R.string.cpl_sample_title)
        else -> song?.title?.takeIf { !isUnknownTitle(it) } ?: stringResource(R.string.untitled_song)
    }
    val artistName = song?.artist?.takeIf { !isUnknownArtist(it) }
    val artistText = when {
        song == null -> stringResource(R.string.cpl_sample_artist)
        else -> artistName ?: stringResource(R.string.unknown_artist)
    }
    val maxSize = when (block.textSize) {
        LayoutTextSize.SMALL -> 18
        LayoutTextSize.MEDIUM -> 26
        LayoutTextSize.LARGE -> 36
        LayoutTextSize.HUGE -> 52
    }
    val emphasis = block.textSize == LayoutTextSize.LARGE || block.textSize == LayoutTextSize.HUGE
    val textAlign = when (block.align) {
        LayoutTextAlign.START -> TextAlign.Start
        LayoutTextAlign.CENTER -> TextAlign.Center
        LayoutTextAlign.END -> TextAlign.End
    }
    val horizontal = when (block.align) {
        LayoutTextAlign.START -> Alignment.Start
        LayoutTextAlign.CENTER -> Alignment.CenterHorizontally
        LayoutTextAlign.END -> Alignment.End
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .layoutTitleSwipe(block, host),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = horizontal
    ) {
        Crossfade(
            targetState = title,
            label = "LayoutTitle",
            modifier = Modifier.weight(1f, fill = false)
        ) { shown ->
            Text(
                text = shown,
                style = if (emphasis) {
                    MaterialTheme.typography.displaySmall.copy(
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em,
                        lineHeight = 1.05.em
                    )
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                },
                color = ink.ink,
                textAlign = textAlign,
                maxLines = if (block.textSize == LayoutTextSize.SMALL) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = maxSize.sp,
                    stepSize = 1.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (block.showDetail) {
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = (maxSize * 0.55f).coerceIn(12f, 20f).sp,
                fontWeight = FontWeight.SemiBold,
                color = ink.ink.copy(alpha = 0.75f),
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = host.interactive && artistName != null) {
                        artistName?.let(host.onArtistClick)
                    }
            )
        }
    }
}

@Composable
private fun ScrubberBlock(block: LayoutBlock, host: LayoutHost, ink: LayoutInk) {
    val roles = roleColors(block.color, ink)
    val lineColor = if (block.color == LayoutColor.NEUTRAL) ink.ink else roles.role
    LayoutScrubber(
        host = host,
        color = lineColor,
        trackColor = lineColor.copy(alpha = 0.22f),
        timeColor = ink.ink.copy(alpha = 0.75f),
        wavesOnlyWhilePlaying = block.motion,
        flat = !block.motion,
        showTimes = block.showDetail,
    )
}

/**
 * The seek bar every style draws: the smooth wavy scrubber under a clear
 * Slider that owns the drag, through the scrub return point, with the times
 * beneath. Each player's skin passes its own measurements, so a seek bar in
 * Classic's skin is Classic's seven-dot stroke in its own colours.
 */
@Composable
internal fun LayoutScrubber(
    host: LayoutHost,
    color: Color,
    trackColor: Color,
    timeColor: Color,
    modifier: Modifier = Modifier.fillMaxSize(),
    stroke: Dp = 4.dp,
    trackHeight: Dp = 14.dp,
    /** The wave settles flat on pause; false waves whenever it is drawn. */
    wavesOnlyWhilePlaying: Boolean = true,
    /** No wave at all. */
    flat: Boolean = false,
    showTimes: Boolean = true,
    timeStyle: TextStyle = MaterialTheme.typography.labelMedium,
    timeWeight: FontWeight? = null,
    timesPadding: Dp = 0.dp,
    topPadding: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
) {
    val viewModel = host.viewModel
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayedProgress = scrubPosition?.toLong() ?: progress
    val scrubReturnPoint = LocalScrubReturnPoint.current
    // Advanced from the clock each frame rather than sprung toward a
    // once-a-second sample - see rememberSmoothProgress.
    val smoothProgress = rememberSmoothProgress(
        positionMs = progress,
        durationMs = duration,
        isPlaying = isPlaying,
        scrubPositionMs = scrubPosition
    )
    val lineStroke = Stroke(
        width = with(LocalDensity.current) { stroke.toPx() },
        cap = StrokeCap.Round
    )
    Column(modifier = modifier, verticalArrangement = verticalArrangement) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(top = topPadding)) {
            ExpressiveScrubber(
                progress = { smoothProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scrubberTrackHeight(trackHeight)),
                color = color,
                trackColor = trackColor,
                stroke = lineStroke,
                trackStroke = lineStroke,
                amplitude = {
                    when {
                        flat -> 0f
                        wavesOnlyWhilePlaying -> if (isPlaying) 1f else 0f
                        else -> 1f
                    }
                }
            )
            Slider(
                interactionSource = LocalPlayerScrubInteraction.current,
                value = scrubPosition ?: progress.toFloat(),
                enabled = host.interactive,
                // Through the return point: letting go on the marker is
                // "never mind" rather than a seek. See ScrubReturnPoint.
                onValueChange = {
                    scrubPosition = scrubReturnPoint.follow(
                        value = it,
                        rangeEnd = duration.toFloat().coerceAtLeast(1f)
                    ) { smoothProgress.value }
                },
                onValueChangeFinished = {
                    val cancelled = scrubReturnPoint.release()
                    if (!cancelled) scrubPosition?.let { viewModel.seekTo(it.toLong()) }
                    scrubPosition = null
                },
                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showTimes) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = timesPadding),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatEditorialTime(displayedProgress),
                    style = timeStyle,
                    fontWeight = timeWeight,
                    color = timeColor
                )
                Text(
                    text = formatEditorialTime(duration),
                    style = timeStyle,
                    fontWeight = timeWeight,
                    color = timeColor
                )
            }
        }
    }
}

/** "Now playing" over the album, Classic's header in the layout's ink. */
@Composable
private fun HeaderBlock(block: LayoutBlock, host: LayoutHost, ink: LayoutInk) {
    val song by host.viewModel.currentSong.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.ps_now_playing).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.18.em,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        val album = song?.album?.takeIf { it.isNotBlank() }
        if (album != null && block.showDetail) {
            Text(
                text = album,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ink.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The time as text, "1:23  /  3:45". */
@Composable
private fun TimeBlock(host: LayoutHost, ink: LayoutInk) {
    val progress by host.viewModel.progress.collectAsState()
    val duration by host.viewModel.duration.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "${formatEditorialTime(progress)}  /  ${formatEditorialTime(duration)}",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                letterSpacing = 2.sp
            ),
            color = ink.ink,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** A floating toolbar of actions with Like as its button: Gesture's, which reads in any layout. */
@Composable
private fun ToolbarBlock(host: LayoutHost) {
    GestureToolbarBlock(host)
}

/** Previous, play and next; play takes the block's look and the skips a quieter one. */
@Composable
private fun TransportBlock(block: LayoutBlock, host: LayoutHost, ink: LayoutInk) {
    val skipLook = when (block.look) {
        ButtonLook.FILLED, ButtonLook.TONAL -> ButtonLook.TONAL
        ButtonLook.OUTLINED -> ButtonLook.OUTLINED
        ButtonLook.PLAIN -> ButtonLook.PLAIN
    }
    val skipShape = if (block.shape.isPolygon) BlockShape.ROUND else block.shape
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LayoutActionButton(
            action = LayoutAction.PREVIOUS, look = skipLook, color = block.color, shape = skipShape,
            host = host, ink = ink,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        LayoutActionButton(
            action = LayoutAction.PLAY_PAUSE, look = block.look, color = block.color, shape = block.shape,
            host = host, ink = ink,
            modifier = Modifier.weight(1.6f).fillMaxHeight()
        )
        LayoutActionButton(
            action = LayoutAction.NEXT, look = skipLook, color = block.color, shape = skipShape,
            host = host, ink = ink,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun LyricsBlock(host: LayoutHost, ink: LayoutInk) {
    val viewModel = host.viewModel
    val song by viewModel.currentSong.collectAsState()
    if (song == null) {
        // The editor with nothing playing: say what goes here.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.cpl_no_lyrics),
                style = MaterialTheme.typography.titleMedium,
                color = ink.ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }
    val lyricsResult by viewModel.lyricsResult.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    SyncedLyricsView(
        lyricsResult = lyricsResult,
        currentPositionMs = progress,
        isPlaying = isPlaying,
        onSeekTo = { viewModel.seekTo(it) },
        modifier = Modifier.fillMaxSize(),
        primaryColor = MaterialTheme.colorScheme.primary,
        onSurfaceColor = ink.ink,
        onSurfaceVariantColor = ink.ink.copy(alpha = 0.6f),
        interactive = host.interactive,
        backgroundColor = ink.field
    )
}

@Composable
private fun UpNextBlock(block: LayoutBlock, host: LayoutHost, ink: LayoutInk) {
    val queue by host.viewModel.currentQueue.collectAsState()
    val currentId by host.viewModel.currentQueueItemId.collectAsState()
    val position = queue.indexOfFirst { it.id == currentId }
    val next = if (position >= 0) queue.getOrNull(position + 1)?.song else null
    val roles = roleColors(block.color, ink)
    Surface(
        onClick = { host.onAction(LayoutAction.QUEUE) },
        enabled = host.interactive,
        shape = block.shape.takeIf { !it.isPolygon }?.composeShape() ?: RoundedCornerShape(20.dp),
        color = roles.container,
        contentColor = roles.onContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints {
            val art = (maxHeight - 16.dp).coerceIn(0.dp, 72.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (next != null && art >= 24.dp) {
                    SongArtwork(
                        song = next,
                        contentDescription = null,
                        modifier = Modifier
                            .size(art)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cpl_up_next),
                        style = MaterialTheme.typography.labelMedium,
                        color = roles.onContainer.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                    Text(
                        text = next?.title ?: stringResource(R.string.cpl_nothing_next),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (next != null && !isUnknownArtist(next.artist)) {
                        Text(
                            text = next.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = roles.onContainer.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelBlock(block: LayoutBlock) {
    val scheme = MaterialTheme.colorScheme
    val color = when (block.color) {
        LayoutColor.PRIMARY -> scheme.primaryContainer
        LayoutColor.SECONDARY -> scheme.secondaryContainer
        LayoutColor.TERTIARY -> scheme.tertiaryContainer
        LayoutColor.NEUTRAL -> scheme.surfaceContainerHigh
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(block.shape.composeShape())
            .background(color)
    )
}
