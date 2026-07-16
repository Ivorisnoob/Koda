package com.ivor.ivormusic.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clearance, measured from the top of the navigation bar inset, that
 * bottom-anchored UI (FABs, split buttons) needs to stay clear of the
 * floating overlays HomeScreen stacks above every tab: the pill nav bar,
 * plus the music and/or video mini players when something is loaded.
 *
 * Provided (animated) by HomeScreen so deep screens like the playlist detail
 * page don't need the player state threaded through every call site. Screens
 * hosted outside HomeScreen fall back to 0.
 */
val LocalBottomOverlayInset = compositionLocalOf { 0.dp }
