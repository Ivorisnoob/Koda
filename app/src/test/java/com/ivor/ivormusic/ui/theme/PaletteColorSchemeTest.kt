package com.ivor.ivormusic.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class PaletteColorSchemeTest {
    @Test fun everyPaletteStyleKeepsTextReadableInLightDarkAndAmoled() {
        for (palette in APP_PALETTES) for (style in PaletteStyle.entries) {
            for (dark in listOf(false, true)) for (amoled in listOf(false, true)) {
                val generated = buildPaletteColorScheme(palette, dark, style)
                val scheme = if (dark && amoled) generated.toAmoled() else generated
                val pairs = listOf(
                    "primary" to (scheme.primary to scheme.onPrimary),
                    "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
                    "secondary" to (scheme.secondary to scheme.onSecondary),
                    "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
                    "tertiary" to (scheme.tertiary to scheme.onTertiary),
                    "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
                    "background" to (scheme.background to scheme.onBackground),
                    "surface" to (scheme.surface to scheme.onSurface),
                    "surfaceContainerHighest" to (scheme.surfaceContainerHighest to scheme.onSurface),
                    "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant),
                    "error" to (scheme.error to scheme.onError),
                    "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
                    "inverseSurface" to (scheme.inverseSurface to scheme.inverseOnSurface),
                    "primaryFixed" to (scheme.primaryFixed to scheme.onPrimaryFixedVariant),
                    "secondaryFixed" to (scheme.secondaryFixed to scheme.onSecondaryFixedVariant),
                    "tertiaryFixed" to (scheme.tertiaryFixed to scheme.onTertiaryFixedVariant),
                )
                for ((role, colors) in pairs) {
                    val ratio = contrast(colors.first, colors.second)
                    assertTrue("${palette.id}/$style dark=$dark amoled=$amoled $role: $ratio", ratio >= 4.5)
                }
                if (dark && amoled) {
                    assertEquals(Color.Black, scheme.background)
                    assertEquals(Color.Black, scheme.surface)
                    assertTrue(scheme.surfaceContainerHighest.luminance() > scheme.surfaceContainerLow.luminance())
                }
            }
        }
    }

    @Test fun presetsTintSurfacesAndStylesActuallyChangeTheTheme() {
        val electric = findPalette("electric")!!
        val citrus = findPalette("citrus")!!
        for (dark in listOf(false, true)) {
            assertNotEquals(buildPaletteColorScheme(electric, dark).surfaceContainer,
                buildPaletteColorScheme(citrus, dark).surfaceContainer)
            // A dark blue primary can hit the sRGB gamut ceiling in both
            // Tonal Spot and Vibrant; their other roles still differ.
            val colors = PaletteStyle.entries.map {
                val scheme = buildPaletteColorScheme(electric, dark, it)
                listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surfaceContainer)
            }
            assertEquals(PaletteStyle.entries.size, colors.distinct().size)
        }
    }

    @Test fun monochromePresetsStayDistinctAndIgnoreStyleOverrides() {
        for (dark in listOf(false, true)) {
            val black = buildPaletteColorScheme(findPalette("black")!!, dark)
            val white = buildPaletteColorScheme(findPalette("white")!!, dark)
            assertNotEquals(black.primaryContainer, white.primaryContainer)
            for (style in PaletteStyle.entries) {
                assertEquals(black.primaryContainer, buildPaletteColorScheme(findPalette("black")!!, dark, style).primaryContainer)
                assertEquals(white.primaryContainer, buildPaletteColorScheme(findPalette("white")!!, dark, style).primaryContainer)
            }
            for (color in listOf(black.surface, white.surface, black.primaryContainer, white.primaryContainer)) {
                assertEquals(color.red, color.green, 0.001f)
                assertEquals(color.green, color.blue, 0.001f)
            }
        }
    }

    @Test fun restoredStylesUseStableIdsAndUnknownValuesFallBack() {
        for (style in PaletteStyle.entries) assertEquals(style, PaletteStyle.fromStorageId(style.storageId))
        assertEquals(PaletteStyle.TONAL_SPOT, PaletteStyle.fromStorageId(null))
        assertEquals(PaletteStyle.TONAL_SPOT, PaletteStyle.fromStorageId("future_style"))
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
