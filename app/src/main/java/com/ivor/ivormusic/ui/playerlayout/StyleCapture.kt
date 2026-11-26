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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.ivor.ivormusic.ui.player.HeroPlayerSheetContent
import com.ivor.ivormusic.ui.player.LayoutCapture
import com.ivor.ivormusic.ui.player.LocalLayoutCapture
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.areaInsetsPx

/** The players whose pieces are tagged for capture and drawn by their own skin. */
internal val CAPTURED_STYLES: Set<PlayerStyle> = setOf(PlayerStyle.HERO)

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
 * ones that size themselves to their text to settle.
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
            CompositionLocalProvider(LocalLayoutCapture provides capture) {
                when (style) {
                    PlayerStyle.HERO -> HeroPlayerSheetContent(
                        viewModel = viewModel,
                        ambientBackground = ambientBackground,
                        onCollapse = {}
                    )
                    else -> Unit
                }
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
