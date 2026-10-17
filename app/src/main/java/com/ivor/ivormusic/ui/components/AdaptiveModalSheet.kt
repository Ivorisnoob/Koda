package com.ivor.ivormusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivor.ivormusic.ui.theme.currentWindowLayout

/**
 * A modal sheet that knows the shape of the window it opens in.
 *
 * On a phone it is [ModalBottomSheet], exactly as before - the bottom edge is
 * where a thumb already is, and a sheet that rises from it reads as an
 * extension of the page.
 *
 * At 600dp and up a bottom sheet is the wrong object: it drags a wide page's
 * content down to a strip along the bottom of a screen with room to spare, and
 * the options it holds sit as far from the user's hands as the layout allows.
 * There it opens as a right-docked side panel instead - full height, a fixed
 * readable width, the page still visible behind it, which is what every
 * desktop-class app does with the same content.
 *
 * Call sites are unchanged apart from the function name: the parameters mirror
 * [ModalBottomSheet]'s, so `ModalBottomSheet(` becomes `AdaptiveModalSheet(`.
 * [sheetState] is only used in the phone layout; omit it and one is created.
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

    // Wide window: a side panel over a dimmed page. `usePlatformDefaultWidth`
    // is off so the panel can be its own width and dock to the end edge rather
    // than sitting in the platform's centred dialog box.
    val panelWidth = (layout.width * 0.42f).coerceIn(360.dp, 560.dp)
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // The page behind, dimmed, and tappable to dismiss. Transparent
            // itself so the platform's own dialog dim is the only darkening -
            // this Box exists to catch the tap, not to draw a second scrim.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
            )
            Surface(
                modifier = modifier
                    .fillMaxHeight()
                    .width(panelWidth),
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
                // Square, because only the leading edge faces the page; the
                // trailing edge is the screen's own edge.
                shape = RectangleShape,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    content()
                }
            }
        }
    }
}
