package com.ivor.ivormusic.ui.playerlayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.playerlayout.LayoutBlock
import com.ivor.ivormusic.data.playerlayout.LayoutPreset
import com.ivor.ivormusic.data.playerlayout.LayoutSkin
import com.ivor.ivormusic.ui.player.BentoPlayerSheetContent
import com.ivor.ivormusic.ui.player.DialPlayerSheetContent
import com.ivor.ivormusic.ui.player.EditorialPlayerSheetContent
import com.ivor.ivormusic.ui.player.GesturePlayerSheetContent
import com.ivor.ivormusic.ui.player.HeroPlayerSheetContent
import com.ivor.ivormusic.ui.player.LayoutCapture
import com.ivor.ivormusic.ui.player.LocalLayoutCapture
import com.ivor.ivormusic.ui.player.LocalMotionArtwork
import com.ivor.ivormusic.ui.player.LocalNowPlayingOptionsOpen
import com.ivor.ivormusic.ui.player.LocalPlayerQueueOpen
import com.ivor.ivormusic.ui.player.MorphPlayerSheetContent
import com.ivor.ivormusic.ui.player.PlayerSheetContent
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.PosterPlayerSheetContent
import com.ivor.ivormusic.ui.player.StickerPlayerSheetContent
import com.ivor.ivormusic.ui.player.areaInsetsPx

/**
 * The players whose pieces are tagged for capture and drawn by their own skin:
 * every built-in one.
 */
internal val CAPTURED_STYLES: Set<PlayerStyle> =
    LayoutPreset.entries.mapNotNullTo(mutableSetOf()) { it.style }

/** Skins a block can be given in the editor: Koda's own, and every captured player's. */
internal val OFFERED_SKINS: List<LayoutSkin> =
    LayoutSkin.entries.filter { it == LayoutSkin.KODA || it.style in CAPTURED_STYLES }

internal val LayoutPreset.isCaptured: Boolean get() = style in CAPTURED_STYLES

/**
 * Draws the real player [preset] stands for, out of sight at the player's
 * size, and hands back where its pieces landed as blocks.
 *
 * Hosted under the editor's opaque surface, so it is never seen and never
 * touched. Three frames is enough for the pieces to be measured and for the
 * ones that size themselves to their text to settle. The hidden player gets
 * its own queue and menu flags and no motion artwork, so it can neither open
 * anything in the real player nor start a second video decoder.
 */
@Composable
internal fun StyleCaptureHost(
    preset: LayoutPreset,
    viewModel: PlayerViewModel,
    canvasSize: DpSize,
    safeArea: PaddingValues,
    playerScheme: ColorScheme,
    ambientBackground: Boolean,
    onCaptured: (List<LayoutBlock>) -> Unit,
) {
    val style = preset.style ?: return
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val capture = remember(preset) { LayoutCapture() }
    val deliver by rememberUpdatedState(onCaptured)

    Box(
        modifier = Modifier
            .requiredSize(canvasSize)
            .graphicsLayer { alpha = 0f }
            .onGloballyPositioned { capture.root = it }
    ) {
        MaterialTheme(colorScheme = playerScheme) {
            CompositionLocalProvider(
                LocalLayoutCapture provides capture,
                LocalMotionArtwork provides null,
                LocalPlayerQueueOpen provides null,
                LocalNowPlayingOptionsOpen provides null,
            ) {
                CapturedPlayer(style, viewModel, ambientBackground)
            }
        }
    }

    LaunchedEffect(preset) {
        repeat(3) { withFrameNanos { } }
        val insets = areaInsetsPx(safeArea, direction, density)
        val width = with(density) { canvasSize.width.toPx() }
        val height = with(density) { canvasSize.height.toPx() }
        deliver(capture.blocks(width, height, insets))
    }
}

@Composable
private fun CapturedPlayer(style: PlayerStyle, viewModel: PlayerViewModel, ambientBackground: Boolean) {
    when (style) {
        PlayerStyle.CLASSIC -> PlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.GESTURE -> GesturePlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.EDITORIAL -> EditorialPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.POSTER -> PosterPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.BENTO -> BentoPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.STICKER -> StickerPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.MORPH -> MorphPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.DIAL -> DialPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        PlayerStyle.HERO -> HeroPlayerSheetContent(viewModel = viewModel, ambientBackground = ambientBackground, onCollapse = {})
        else -> Unit
    }
}

/**
 * Reads every player in [presets] off the real thing, one after another so
 * only one hidden player is drawn at a time, handing each one's blocks to
 * [onCaptured] as it lands. For the "start from" previews, and so a pick is
 * instant.
 */
@Composable
internal fun StyleCaptureQueue(
    presets: List<LayoutPreset>,
    viewModel: PlayerViewModel,
    canvasSize: DpSize,
    safeArea: PaddingValues,
    playerScheme: ColorScheme,
    ambientBackground: Boolean,
    onCaptured: (LayoutPreset, List<LayoutBlock>) -> Unit,
) {
    var index by remember(presets) { mutableIntStateOf(0) }
    val preset = presets.getOrNull(index) ?: return
    key(preset) {
        StyleCaptureHost(
            preset = preset,
            viewModel = viewModel,
            canvasSize = canvasSize,
            safeArea = safeArea,
            playerScheme = playerScheme,
            ambientBackground = ambientBackground,
            onCaptured = { blocks ->
                onCaptured(preset, blocks)
                index++
            }
        )
    }
}
