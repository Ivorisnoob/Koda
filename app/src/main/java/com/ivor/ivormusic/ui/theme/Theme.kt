package com.ivor.ivormusic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryBlue,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = OnPrimaryBlue,
    secondary = SecondaryPurple,
    secondaryContainer = SecondaryPurpleContainer,
    background = Color(0xFF0F0F0F), // Richer, slightly lighter than pure black
    surface = Color(0xFF1E1E1E), // Deeper surface
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

/**
 * Pure black variant for AMOLED displays: background and base surface go to
 * true #000000, and the surface container ramp is compressed toward black so
 * cards keep a subtle elevation separation without the default grey wash.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020)
)

// Expressive shapes with more rounded corners
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/**
 * How Koda turns its three theme preferences into a [ColorScheme]. Extracted
 * from [IvorMusicTheme] because the home screen widgets need the same answer
 * outside a Compose UI composition - a Glance composition has no
 * LocalContext of the Compose kind and cannot call [IvorMusicTheme] at all, and
 * widgets drawn in raw system dynamic color while the app runs a chosen palette
 * look like a different app's widgets.
 *
 * Takes a plain [android.content.Context] rather than reading a composition
 * local so both callers can use it.
 */
fun kodaColorScheme(
    context: android.content.Context,
    darkTheme: Boolean,
    colorPalette: String,
    amoledDark: Boolean,
): ColorScheme {
    val useDynamic = colorPalette == DYNAMIC_PALETTE_ID
    // Neutral base scheme (surfaces, on-surface text). Dynamic pulls from the
    // wallpaper; a fixed palette starts from the app's own neutral scheme so
    // wallpaper color is fully ignored and only our accents apply.
    val baseColorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> expressiveLightColorScheme()
    }
    val palettedScheme = if (useDynamic) {
        baseColorScheme
    } else {
        findPalette(colorPalette)?.let { buildPaletteColorScheme(it, darkTheme, baseColorScheme) }
            ?: baseColorScheme
    }
    return if (darkTheme && amoledDark) palettedScheme.toAmoled() else palettedScheme
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IvorMusicTheme(
    darkTheme: Boolean = true, // Default to dark theme for this music app
    colorPalette: String = DYNAMIC_PALETTE_ID, // "dynamic" = wallpaper color, else a fixed AppPalette id
    amoledDark: Boolean = false, // Pure black backgrounds when dark theme is active
    content: @Composable () -> Unit
) {
    val colorScheme = kodaColorScheme(
        context = LocalContext.current,
        darkTheme = darkTheme,
        colorPalette = colorPalette,
        amoledDark = amoledDark,
    )
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // enableEdgeToEdge leaves contrast enforcement on for the
            // navigation bar, which paints a translucent system scrim behind
            // it on three-button navigation - a visible band under the
            // floating toolbar and the mini player. Both setters are no-ops
            // from API 35, where the system owns this; they still matter on
            // 30-34.
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
            // Deliberately kept even though enableEdgeToEdge normally handles
            // icon appearance: it decides from the system uiMode, and Koda's
            // theme mode is its own setting, so a user forcing dark inside the
            // app on a light system would otherwise get dark icons on dark.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = Typography,
        content = content
    )
}
