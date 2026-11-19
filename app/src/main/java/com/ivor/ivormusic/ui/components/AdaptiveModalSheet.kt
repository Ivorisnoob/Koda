package com.ivor.ivormusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ivor.ivormusic.ui.theme.currentWindowLayout

/**
 * A modal sheet that knows the shape of the window it opens in.
 *
 * On a phone it is [ModalBottomSheet], exactly as before - the bottom edge is
 * where a thumb already is, and a sheet that rises from it reads as an
 * extension of the page.
 *
 * At 600dp and up a bottom sheet is the wrong object: it drags a wide page's
 * content down to a strip along the bottom of a screen with room to spare.
 * There it opens as a floating side panel instead: a rounded card held off
 * the window's edges, a readable fixed width, the page dimmed behind it.
 *
 * Call sites are unchanged apart from the function name: the parameters mirror
 * [ModalBottomSheet]'s, so `ModalBottomSheet(` becomes `AdaptiveModalSheet(`.
 * [sheetState], [contentWindowInsets] and [dragHandle] are only used in the
 * phone layout.
 *
 * The panel's first version [scar November 2026] was a square, full-height
 * slab docked to the end edge, padded by `safeDrawing` on every side. In
 * landscape that is the camera cutout on the *other* edge of the screen, so
 * the panel opened with a wide empty strip down its leading side; it also
 * butted against the status bar, took 42% of the window, and popped in with
 * no motion. Now the dialog draws edge to edge and the panel is held clear of
 * only the insets it actually touches - the bars and the end edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState? = null,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = currentWindowLayout()

    if (!layout.usesNavigationRail) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState ?: rememberModalBottomSheetState(),
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            contentWindowInsets = contentWindowInsets,
            dragHandle = dragHandle,
            content = content,
        )
        return
    }

    SidePanel(
        onDismissRequest = onDismissRequest,
        windowWidth = layout.width,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        content = content,
    )
}

@Composable
private fun SidePanel(
    onDismissRequest: () -> Unit,
    windowWidth: Dp,
    modifier: Modifier,
    containerColor: Color,
    contentColor: Color,
    tonalElevation: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Starts hidden and is told to show, so the first frame animates in. A
    // dismissal the panel owns (back, a tap on the page) animates out first
    // and reports when the exit has finished. One the caller makes - an option
    // that closes the sheet - removes it at once, as a bottom sheet's does.
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    var dismissing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    // Only once a dismissal has started: before the first frame the panel is
    // also idle and hidden, and that must not read as "finished leaving".
    LaunchedEffect(dismissing, visibility.isIdle, visibility.currentState) {
        if (dismissing && visibility.isIdle && !visibility.currentState) currentOnDismiss()
    }
    val dismiss = {
        dismissing = true
        visibility.targetState = false
    }

    val panelWidth = (windowWidth * PANEL_WIDTH_FRACTION).coerceIn(PANEL_MIN_WIDTH, PANEL_MAX_WIDTH)

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Edge to edge, so the panel places itself against the bars
            // rather than the platform clipping the window to them.
            decorFitsSystemWindows = false,
        ),
    ) {
        // The platform's dim pops on with the window; the scrim below fades
        // with the panel instead.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = fadeIn(tween(SCRIM_FADE_MS)),
                exit = fadeOut(tween(SCRIM_FADE_MS)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss,
                        )
                )
            }

            AnimatedVisibility(
                visibleState = visibility,
                enter = slideInHorizontally(
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) { it / 2 } + fadeIn(tween(PANEL_FADE_MS)),
                exit = slideOutHorizontally(
                    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
                ) { it / 2 } + fadeOut(tween(PANEL_FADE_MS)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Clear of the status bar, the navigation bar and a cutout
                    // on the end edge; never the start edge's cutout, which is
                    // the other side of the screen.
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                    )
                    .padding(PANEL_MARGIN),
            ) {
                Surface(
                    modifier = modifier
                        .fillMaxHeight()
                        .width(panelWidth),
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = tonalElevation,
                    shadowElevation = 6.dp,
                    shape = RoundedCornerShape(PANEL_CORNER),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            // Sheet content written for a phone pads itself
                            // for the navigation bar; the panel is already
                            // clear of it, so those paddings come to nothing.
                            .consumeWindowInsets(WindowInsets.safeDrawing)
                            .padding(top = 8.dp)
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** A third of a tablet, held between a phone's width and a comfortable column. */
private const val PANEL_WIDTH_FRACTION = 0.36f
private val PANEL_MIN_WIDTH = 360.dp
private val PANEL_MAX_WIDTH = 440.dp

/** Space between the panel and the window's edges. */
private val PANEL_MARGIN = 12.dp

/** Material 3 Expressive's extra-large corner. */
private val PANEL_CORNER = 28.dp

private const val SCRIM_ALPHA = 0.32f
private const val SCRIM_FADE_MS = 200
private const val PANEL_FADE_MS = 150
