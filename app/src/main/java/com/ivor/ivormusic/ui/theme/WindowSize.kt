package com.ivor.ivormusic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize

/**
 * The window's size in the dp this composition actually lays out in.
 *
 * Use this, never `LocalWindowInfo.current.containerDpSize` or
 * `Configuration.screenWidthDp`. Both convert with the *platform* density,
 * and the interface scale (Settings, Appearance, Display size) is a
 * [LocalDensity] override, so at any stop other than 100% they report a
 * window that is the wrong size in the units everything else is measured in.
 * At 0.85x the expanded player sized itself from the platform figure and drew
 * as a box 15% short of the screen, with Home showing around it (#291).
 * Converting the pixel size through the current density is right at every
 * scale, and identical to the platform figure at 100%.
 */
@Composable
fun windowDpSize(): DpSize {
    val px = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(px.width.toDp(), px.height.toDp()) }
}
