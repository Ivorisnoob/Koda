// Color utility APIs are library-group scoped in the pinned Material AAR.
// Keep the dependency pinned and verify role/contrast tests when updating it.
@file:Suppress("RestrictedApi")

package com.ivor.ivormusic.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeMonochrome
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.ivor.ivormusic.R

/** Persist stable ids, never ordinals; unknown restored values use the calm default. */
enum class PaletteStyle(val storageId: String, @param:StringRes val labelRes: Int) {
    TONAL_SPOT("tonal_spot", R.string.palette_style_tonal_spot),
    VIBRANT("vibrant", R.string.palette_style_vibrant),
    EXPRESSIVE("expressive", R.string.palette_style_expressive),
    FRUIT_SALAD("fruit_salad", R.string.palette_style_fruit_salad),
    MONOCHROME("monochrome", R.string.palette_style_monochrome);

    companion object {
        fun fromStorageId(value: String?): PaletteStyle =
            entries.firstOrNull { it.storageId == value } ?: TONAL_SPOT
    }
}

/**
 * Material 1.12 exposes roles through MaterialDynamicColors, not DynamicScheme
 * getters. Resolve every Compose role, including fixed roles and the surface
 * ramp, so neither default purple nor an unrelated neutral base can leak in.
 * Verified against the pinned AAR, September 2026.
 */
internal fun buildSeedColorScheme(seed: Color, dark: Boolean, style: PaletteStyle): ColorScheme {
    val source = Hct.fromInt(seed.toArgb())
    val scheme = when (style) {
        PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(source, dark, 0.0)
        PaletteStyle.VIBRANT -> SchemeVibrant(source, dark, 0.0)
        PaletteStyle.EXPRESSIVE -> SchemeExpressive(source, dark, 0.0)
        PaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(source, dark, 0.0)
        PaletteStyle.MONOCHROME -> SchemeMonochrome(source, dark, 0.0)
    }
    val roles = MaterialDynamicColors()
    fun DynamicColor.color(): Color = Color(getArgb(scheme))
    return lightColorScheme(
        primary = roles.primary().color(),
        onPrimary = roles.onPrimary().color(),
        primaryContainer = roles.primaryContainer().color(),
        onPrimaryContainer = roles.onPrimaryContainer().color(),
        inversePrimary = roles.inversePrimary().color(),
        secondary = roles.secondary().color(),
        onSecondary = roles.onSecondary().color(),
        secondaryContainer = roles.secondaryContainer().color(),
        onSecondaryContainer = roles.onSecondaryContainer().color(),
        tertiary = roles.tertiary().color(),
        onTertiary = roles.onTertiary().color(),
        tertiaryContainer = roles.tertiaryContainer().color(),
        onTertiaryContainer = roles.onTertiaryContainer().color(),
        background = roles.background().color(),
        onBackground = roles.onBackground().color(),
        surface = roles.surface().color(),
        onSurface = roles.onSurface().color(),
        surfaceVariant = roles.surfaceVariant().color(),
        onSurfaceVariant = roles.onSurfaceVariant().color(),
        surfaceTint = roles.surfaceTint().color(),
        inverseSurface = roles.inverseSurface().color(),
        inverseOnSurface = roles.inverseOnSurface().color(),
        error = roles.error().color(),
        onError = roles.onError().color(),
        errorContainer = roles.errorContainer().color(),
        onErrorContainer = roles.onErrorContainer().color(),
        outline = roles.outline().color(),
        outlineVariant = roles.outlineVariant().color(),
        scrim = roles.scrim().color(),
        surfaceBright = roles.surfaceBright().color(),
        surfaceDim = roles.surfaceDim().color(),
        surfaceContainer = roles.surfaceContainer().color(),
        surfaceContainerHigh = roles.surfaceContainerHigh().color(),
        surfaceContainerHighest = roles.surfaceContainerHighest().color(),
        surfaceContainerLow = roles.surfaceContainerLow().color(),
        surfaceContainerLowest = roles.surfaceContainerLowest().color(),
        primaryFixed = roles.primaryFixed().color(),
        primaryFixedDim = roles.primaryFixedDim().color(),
        onPrimaryFixed = roles.onPrimaryFixed().color(),
        onPrimaryFixedVariant = roles.onPrimaryFixedVariant().color(),
        secondaryFixed = roles.secondaryFixed().color(),
        secondaryFixedDim = roles.secondaryFixedDim().color(),
        onSecondaryFixed = roles.onSecondaryFixed().color(),
        onSecondaryFixedVariant = roles.onSecondaryFixedVariant().color(),
        tertiaryFixed = roles.tertiaryFixed().color(),
        tertiaryFixedDim = roles.tertiaryFixedDim().color(),
        onTertiaryFixed = roles.onTertiaryFixed().color(),
        onTertiaryFixedVariant = roles.onTertiaryFixedVariant().color(),
    )
}
