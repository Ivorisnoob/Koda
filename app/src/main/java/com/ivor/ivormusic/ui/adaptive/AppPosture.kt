package com.ivor.ivormusic.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Width buckets for window-posture decisions. Breakpoints follow Material 3
 * WindowSizeClass (600 / 840dp) without taking the dependency: the app only
 * needs the width bucket, and a hand-rolled bucket keeps the posture a single
 * small value every screen can read for free.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * The single window-posture source of truth for landscape and tablet layouts.
 *
 * Width-based, never orientation-alone, so split-screen and foldables work
 * without any per-screen sniffing: a half-width landscape split lands in
 * Compact and keeps bottom chrome, while a tablet portrait lands in Medium
 * or Expanded and gets the rail.
 *
 * Phone landscape is width-abundant but height-scarce (~360-400dp); tablets
 * are abundant in both. Each screen handles both off this same value rather
 * than keying off orientation.
 */
data class AppPosture(
    /** True when the window is wider than it is tall. */
    val isLandscape: Boolean,
    val widthClass: WindowWidthClass,
    /** Smallest width >= 600dp, stable across rotation. */
    val isTablet: Boolean,
) {
    /**
     * App chrome moves from the bottom bar to a [NavigationRail] side bar.
     * Landscape always rails (the bottom stack would eat the scarce vertical
     * space); portrait rails from Medium up (tablets, foldables unfolded).
     */
    val useRail: Boolean
        get() = isLandscape || widthClass != WindowWidthClass.Compact
}

/**
 * Provided once at the MusicApp level; every screen below reads this instead
 * of measuring the window itself.
 */
val LocalAppPosture = compositionLocalOf {
    AppPosture(
        isLandscape = false,
        widthClass = WindowWidthClass.Compact,
        isTablet = false,
    )
}

/**
 * Derives the posture from the real window container, not the configuration
 * screen size: Configuration.screenWidthDp excludes the system bar areas on
 * older releases even though the edge-to-edge container covers them, which is
 * the same mismatch ExpandablePlayer already measures around.
 */
@Composable
fun rememberAppPosture(): AppPosture {
    val windowSize = LocalWindowInfo.current.containerDpSize
    val smallestWidthDp = LocalConfiguration.current.smallestScreenWidthDp
    return remember(windowSize, smallestWidthDp) {
        val width = windowSize.width
        val widthClass = when {
            width < 600.dp -> WindowWidthClass.Compact
            width < 840.dp -> WindowWidthClass.Medium
            else -> WindowWidthClass.Expanded
        }
        AppPosture(
            isLandscape = width > windowSize.height,
            widthClass = widthClass,
            isTablet = smallestWidthDp >= 600,
        )
    }
}
