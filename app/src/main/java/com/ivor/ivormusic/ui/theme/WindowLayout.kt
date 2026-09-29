package com.ivor.ivormusic.ui.theme

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.ThemePreferences

/**
 * The window's shape, as the questions layouts actually ask of it.
 *
 * Deliberately not Material's `WindowSizeClass` / `currentWindowAdaptiveInfo`:
 * both measure the window with the platform density, and the interface scale
 * (Settings, Appearance, Display size) is a `LocalDensity` override, so at any
 * stop other than 100% they classify a window that is not the one being laid
 * out - the #291 scar [windowDpSize] documents. This is built from that
 * function, so a phone at 85% that really does have 700dp of width to lay out
 * in is treated as having it.
 *
 * The breakpoints are Material's (600 / 840 wide, 480 tall); what differs is
 * only the ruler.
 */
@Immutable
data class WindowLayout(val width: Dp, val height: Dp) {

    val widthClass: WindowWidthClass = when {
        width < MEDIUM_WIDTH -> WindowWidthClass.COMPACT
        width < EXPANDED_WIDTH -> WindowWidthClass.MEDIUM
        else -> WindowWidthClass.EXPANDED
    }

    val isLandscape: Boolean get() = width > height

    /**
     * Too short for anything composed top to bottom: a phone on its side, or a
     * split-screen half. Portrait player styles stack artwork, title and a
     * control deck, and none of that fits in 360dp of height.
     */
    val isShort: Boolean get() = height < SHORT_HEIGHT

    /**
     * Navigation moves from the bottom edge to the start edge. At 600dp and
     * up a bottom bar is a thin strip under a wide page and puts the tabs at
     * the far end of the screen from where the thumbs rest when held sideways.
     */
    val usesNavigationRail: Boolean get() = width >= MEDIUM_WIDTH

    /** A second pane beside the first is worth its room from here up. */
    val supportsTwoPanes: Boolean get() = width >= EXPANDED_WIDTH ||
        (width >= MEDIUM_WIDTH && isLandscape)

    /**
     * How many columns of at least [minCell] fit across [available], clamped
     * so a huge window does not produce thumbnail confetti.
     */
    fun columns(available: Dp = width, minCell: Dp, max: Int = 8): Int =
        (available / minCell).toInt().coerceIn(1, max.coerceAtLeast(1))

    companion object {
        val MEDIUM_WIDTH = 600.dp
        val EXPANDED_WIDTH = 840.dp
        val SHORT_HEIGHT = 480.dp
    }
}

enum class WindowWidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * The current [WindowLayout]. Call it where the decision is made rather than
 * reading it from a CompositionLocal: it is measured through [windowDpSize],
 * which must run beneath the interface-scale density override to be right.
 */
@Composable
fun currentWindowLayout(): WindowLayout {
    val size = windowDpSize()
    return remember(size) { WindowLayout(size.width, size.height) }
}

/**
 * Which way round the app is allowed to be.
 *
 * Large screens always follow the device: Android 16 ignores orientation
 * locks on them for apps targeting SDK 36, and on older releases a tablet
 * forced upright is the app being wrong rather than cautious. Phones follow
 * the device only when Settings, Appearance, "Rotate with device" says so,
 * and otherwise stay portrait as Koda always has. Either way it is
 * `SCREEN_ORIENTATION_USER`, not `SENSOR`/`UNSPECIFIED`, so the system
 * rotation lock is respected.
 *
 * Device class comes from `smallestScreenWidthDp` on purpose: this is a
 * question about the hardware, not a layout size, so the platform density is
 * the right ruler here (unlike [WindowLayout]).
 */
object AppOrientation {
    private const val LARGE_SCREEN_SMALLEST_WIDTH_DP = 600

    fun isLargeScreen(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= LARGE_SCREEN_SMALLEST_WIDTH_DP

    fun policy(context: Context): Int =
        if (isLargeScreen(context) || ThemePreferences.isRotateWithDevice(context)) {
            ActivityInfo.SCREEN_ORIENTATION_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    /** Applies [policy]; a fresh preference read every time. */
    fun apply(activity: Activity) {
        val wanted = policy(activity)
        if (activity.requestedOrientation != wanted) activity.requestedOrientation = wanted
    }
}
