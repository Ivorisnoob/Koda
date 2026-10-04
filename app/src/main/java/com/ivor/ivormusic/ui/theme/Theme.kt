package com.ivor.ivormusic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ivor.ivormusic.data.UI_SCALE_DEFAULT

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
/** [toAmoled] for callers outside this package. */
object AmoledScheme {
    fun ColorScheme.amoled(): ColorScheme = toAmoled()
}

internal fun ColorScheme.toAmoled(): ColorScheme = copy(
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
 * How Koda turns its theme preferences into a [ColorScheme]. Extracted
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
    paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
): ColorScheme {
    val useDynamic = colorPalette == DYNAMIC_PALETTE_ID
    // Wallpaper keeps the system scheme. Presets generate a complete HCT
    // scheme; this base is only the fallback for Android 11 or unknown ids.
    val baseColorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> expressiveLightColorScheme()
    }
    val palettedScheme = if (useDynamic) {
        baseColorScheme
    } else {
        findPalette(colorPalette)?.let { buildPaletteColorScheme(it, darkTheme, paletteStyle) }
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
    uiScale: Float = UI_SCALE_DEFAULT, // Multiplies every dp and sp in the app
    paletteStyle: PaletteStyle = PaletteStyle.TONAL_SPOT,
    /**
     * The playing song's cover colour, when album colours reach the whole
     * app: the scheme is then built from it rather than from [colorPalette].
     * Null is the chosen palette (video mode, nothing playing, no artwork).
     */
    artworkSeed: Color? = null,
    /** Album colours are set to "Whole app", whether or not a seed is in hand right now. */
    artworkWholeApp: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Configuration changes include wallpaper/UI mode changes. Rebuild then,
    // but never regenerate HCT roles on unrelated parent recompositions.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val paletteScheme = remember(context, configuration, darkTheme, colorPalette, amoledDark, paletteStyle) {
        kodaColorScheme(context, darkTheme, colorPalette, amoledDark, paletteStyle)
    }
    // The whole scheme from the cover, surfaces included, through the same
    // generator the preset palettes use - so it is one coherent scheme with
    // worked-out contrast, not album accents laid over palette surfaces.
    val targetScheme = remember(paletteScheme, artworkSeed, darkTheme, amoledDark, paletteStyle) {
        if (artworkSeed == null) {
            paletteScheme
        } else {
            buildSeedColorScheme(artworkSeed, darkTheme, paletteStyle)
                .let { if (darkTheme && amoledDark) it.toAmoled() else it }
        }
    }
    val colorScheme = rememberFadedColorScheme(targetScheme, fadeKey = artworkSeed)
    
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
    ) {
        // The app's own scheme, kept reachable from inside a local re-theme.
        // Content color too: most screens paint a plain background rather than
        // a Surface, and without one an uncolored Text falls back to black,
        // which is invisible in dark theme.
        CompositionLocalProvider(
            LocalAppColorScheme provides colorScheme,
            LocalAlbumTheming provides AlbumTheming(artworkWholeApp, paletteStyle, darkTheme && amoledDark),
            androidx.compose.material3.LocalContentColor provides colorScheme.onBackground,
        ) {
            ScaledDensity(uiScale, content)
        }
    }
}

/**
 * How a scheme built from a cover should be made, for the places that build
 * their own: the expanded player and the album and playlist pages.
 *
 * With [wholeApp] those take a complete scheme from *their* cover, as the root
 * does from the playing song's, instead of laying cover accents over the
 * ambient surfaces. That keeps the rule that a surface takes one scheme whole:
 * a playlist page is its own artwork's colours while the app around it is the
 * playing album's, and neither borrows roles from the other.
 */
data class AlbumTheming(val wholeApp: Boolean, val style: PaletteStyle, val amoled: Boolean)

val LocalAlbumTheming = staticCompositionLocalOf { AlbumTheming(false, PaletteStyle.TONAL_SPOT, false) }

/** How long the app takes to move from one song's colours to the next. */
private const val ALBUM_FADE_MS = 500

/**
 * [target], reached by a short fade when [fadeKey] is what changed.
 *
 * Only a change of song fades. A theme switch, a new palette or AMOLED mode
 * is a choice the user just made and lands at once, as it always did; fading
 * dark to light would pass through a grey nobody asked for.
 *
 * Every frame of the fade is a new scheme, and so a recomposition of whatever
 * reads a colour. That is the cost of the whole app changing colour, and why
 * the fade is short.
 */
@Composable
private fun rememberFadedColorScheme(target: ColorScheme, fadeKey: Color?): ColorScheme {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    var lastKey by remember { mutableStateOf(fadeKey) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        val songChanged = fadeKey != lastKey
        lastKey = fadeKey
        if (!songChanged) {
            from = target
            to = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // From wherever an interrupted fade had got to, not from its start.
        from = lerpColorScheme(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ALBUM_FADE_MS))
    }

    val fraction = progress.value
    return if (fraction >= 1f) to else lerpColorScheme(from, to, fraction)
}

/** The roles screens draw with, blended; the rest are taken from [stop]. */
private fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return start
    if (fraction >= 1f) return stop
    fun mix(a: Color, b: Color) = lerp(a, b, fraction)
    return stop.copy(
        primary = mix(start.primary, stop.primary),
        onPrimary = mix(start.onPrimary, stop.onPrimary),
        primaryContainer = mix(start.primaryContainer, stop.primaryContainer),
        onPrimaryContainer = mix(start.onPrimaryContainer, stop.onPrimaryContainer),
        inversePrimary = mix(start.inversePrimary, stop.inversePrimary),
        secondary = mix(start.secondary, stop.secondary),
        onSecondary = mix(start.onSecondary, stop.onSecondary),
        secondaryContainer = mix(start.secondaryContainer, stop.secondaryContainer),
        onSecondaryContainer = mix(start.onSecondaryContainer, stop.onSecondaryContainer),
        tertiary = mix(start.tertiary, stop.tertiary),
        onTertiary = mix(start.onTertiary, stop.onTertiary),
        tertiaryContainer = mix(start.tertiaryContainer, stop.tertiaryContainer),
        onTertiaryContainer = mix(start.onTertiaryContainer, stop.onTertiaryContainer),
        background = mix(start.background, stop.background),
        onBackground = mix(start.onBackground, stop.onBackground),
        surface = mix(start.surface, stop.surface),
        onSurface = mix(start.onSurface, stop.onSurface),
        surfaceVariant = mix(start.surfaceVariant, stop.surfaceVariant),
        onSurfaceVariant = mix(start.onSurfaceVariant, stop.onSurfaceVariant),
        surfaceTint = mix(start.surfaceTint, stop.surfaceTint),
        inverseSurface = mix(start.inverseSurface, stop.inverseSurface),
        inverseOnSurface = mix(start.inverseOnSurface, stop.inverseOnSurface),
        outline = mix(start.outline, stop.outline),
        outlineVariant = mix(start.outlineVariant, stop.outlineVariant),
        surfaceBright = mix(start.surfaceBright, stop.surfaceBright),
        surfaceDim = mix(start.surfaceDim, stop.surfaceDim),
        surfaceContainer = mix(start.surfaceContainer, stop.surfaceContainer),
        surfaceContainerHigh = mix(start.surfaceContainerHigh, stop.surfaceContainerHigh),
        surfaceContainerHighest = mix(start.surfaceContainerHighest, stop.surfaceContainerHighest),
        surfaceContainerLow = mix(start.surfaceContainerLow, stop.surfaceContainerLow),
        surfaceContainerLowest = mix(start.surfaceContainerLowest, stop.surfaceContainerLowest),
    )
}

/**
 * The palette the app chose, regardless of what the nearest `MaterialTheme` says.
 *
 * **This exists for getting *out* of a re-theme, not into one.** The expanded
 * player optionally derives its accents from the album cover, so everything
 * composed inside it - including the bottom sheets it hosts, which are
 * subcompositions and inherit the local - reads artwork colours from
 * `MaterialTheme.colorScheme`. A surface that belongs to the app rather than to
 * the playing song asks for this instead and re-themes itself back, which is
 * also the only way to do it: the artwork scheme replaces accent roles and keeps
 * the app's surfaces, so picking roles out of it one at a time produces a
 * half-and-half palette rather than either one.
 *
 * It falls back to [MaterialTheme]'s own scheme, so a composable used outside
 * `KodaTheme` (a preview, a test) still draws.
 */
val LocalAppColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/** The app's palette, or the ambient one when this is composed outside [KodaTheme]. */
@Composable
fun appColorScheme(): ColorScheme = LocalAppColorScheme.current ?: MaterialTheme.colorScheme

/**
 * Rescales the whole interface by lying about the display's density.
 *
 * Every `dp` in the app is converted to pixels through [LocalDensity], so
 * multiplying the density here resizes all 3,400-odd of them at once and keeps
 * the proportions the design was drawn at - which is the entire reason the
 * setting is a density override rather than a sweep of the dp literals.
 *
 * [Density.fontScale] is deliberately left alone rather than divided back out.
 * Compose resolves `sp` as `density * fontScale`, so text rides along with the
 * chrome and the layout stays in proportion; compensating would hold type at
 * its old size inside boxes that had shrunk around it. The user's system font
 * setting still applies on top, because it is the untouched multiplier.
 *
 * Applied inside `MaterialExpressiveTheme` so every consumer of the theme
 * sees it, dialogs and bottom sheets included - those compose as
 * subcompositions and inherit the local. Deliberately not reaching the Glance
 * widgets or the media notification: those are drawn by another process and
 * should match its density, not Koda's.
 */
@Composable
private fun ScaledDensity(scale: Float, content: @Composable () -> Unit) {
    if (scale == UI_SCALE_DEFAULT) {
        content()
        return
    }
    val current = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = current.density * scale,
            fontScale = current.fontScale
        ),
        content = content
    )
}
