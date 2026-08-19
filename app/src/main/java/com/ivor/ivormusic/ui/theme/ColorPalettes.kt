package com.ivor.ivormusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Curated color palettes for the app theme.
 *
 * Each palette carries three vivid reference "seed" hues (primary, secondary,
 * tertiary). At theme-build time [buildPaletteColorScheme] derives the full set
 * of Material accent roles from those seeds for the active light/dark mode and
 * overlays them onto the neutral base scheme, so surfaces, AMOLED black, and the
 * on-surface neutrals stay coherent while only the accents change.
 *
 * The special id [DYNAMIC_PALETTE_ID] is not seed-based: it means "use the
 * system wallpaper dynamic color" (Android 12+), the app's historical default.
 */
data class AppPalette(
    val id: String,
    val name: String,
    val category: String,
    val seedPrimary: Color,
    val seedSecondary: Color,
    val seedTertiary: Color
)

/** Sentinel palette id meaning wallpaper-based dynamic color (Android 12+). */
const val DYNAMIC_PALETTE_ID = "dynamic"

/**
 * All seed palettes, grouped by [AppPalette.category]. Diverse on purpose:
 * bright/vivid, soft/pastel, warm/earthy, deep/moody and jewel/mono families so
 * there is a genuinely distinct pick for every taste.
 */
val APP_PALETTES: List<AppPalette> = listOf(
    // Vibrant
    AppPalette("electric", "Electric", "Vibrant", Color(0xFF2F6BFF), Color(0xFF00C2FF), Color(0xFF7A5CFF)),
    AppPalette("magenta", "Magenta Pop", "Vibrant", Color(0xFFFF2D95), Color(0xFFFF5CA8), Color(0xFFB14BFF)),
    AppPalette("citrus", "Citrus", "Vibrant", Color(0xFFFF7A00), Color(0xFFFFB400), Color(0xFFB6D400)),
    AppPalette("neon", "Neon Lime", "Vibrant", Color(0xFF00E676), Color(0xFF00E5C0), Color(0xFF3AD1FF)),

    // Pastel
    AppPalette("lavender", "Lavender Haze", "Pastel", Color(0xFFB388FF), Color(0xFFCBA6FF), Color(0xFFFF9EE6)),
    AppPalette("mint", "Cotton Mint", "Pastel", Color(0xFF66E0C0), Color(0xFF7FE3D0), Color(0xFF66D9E8)),
    AppPalette("peach", "Peach Sorbet", "Pastel", Color(0xFFFFAB91), Color(0xFFFF8A80), Color(0xFFFFB2C0)),
    AppPalette("sky", "Baby Sky", "Pastel", Color(0xFF7EC8FF), Color(0xFF90A8FF), Color(0xFFA6E3FF)),

    // Aesthetic — muted, faded-film, low-saturation tones
    AppPalette("vintagefilm", "Vintage Film", "Aesthetic", Color(0xFFC6A15B), Color(0xFF9CAF88), Color(0xFFB08968)),
    AppPalette("dustyrose", "Dusty Rose", "Aesthetic", Color(0xFFC58B96), Color(0xFFA99ABA), Color(0xFFCBA6A0)),
    AppPalette("sagesand", "Sage & Sand", "Aesthetic", Color(0xFF9CAF88), Color(0xFFD8C3A5), Color(0xFFCB9273)),
    AppPalette("fadeddenim", "Faded Denim", "Aesthetic", Color(0xFF7E9AAE), Color(0xFF94A7B2), Color(0xFF6E9B94)),
    AppPalette("oatlatte", "Oat Latte", "Aesthetic", Color(0xFFC2A386), Color(0xFFD6C6B0), Color(0xFFA07E64)),
    AppPalette("sunbleached", "Sun-bleached", "Aesthetic", Color(0xFFD79A87), Color(0xFF9CC0B3), Color(0xFFD9C4A3)),

    // Earthy
    AppPalette("terracotta", "Terracotta", "Earthy", Color(0xFFE2725B), Color(0xFFC67B5C), Color(0xFFD9A066)),
    AppPalette("forest", "Forest", "Earthy", Color(0xFF4C9A5B), Color(0xFF7A8B3C), Color(0xFFA6B04A)),
    AppPalette("mocha", "Mocha", "Earthy", Color(0xFFA9745B), Color(0xFFC89B6E), Color(0xFF9E8B7D)),
    AppPalette("autumn", "Autumn", "Earthy", Color(0xFFC1440E), Color(0xFFE08E0B), Color(0xFFD4A017)),

    // Moody
    AppPalette("crimson", "Crimson Noir", "Moody", Color(0xFFE0114B), Color(0xFFFF3D57), Color(0xFF9C1B3E)),
    AppPalette("indigo", "Midnight Indigo", "Moody", Color(0xFF3D3DE0), Color(0xFF5C6BC0), Color(0xFF7E57C2)),
    AppPalette("deepteal", "Deep Teal", "Moody", Color(0xFF009688), Color(0xFF00BCD4), Color(0xFF4DB6AC)),
    AppPalette("plum", "Royal Plum", "Moody", Color(0xFF8E24AA), Color(0xFFAB47BC), Color(0xFFE040FB)),

    // Jewel & Mono
    AppPalette("emerald", "Emerald", "Jewel & Mono", Color(0xFF10B981), Color(0xFF34D399), Color(0xFF6EE7B7)),
    AppPalette("ocean", "Ocean", "Jewel & Mono", Color(0xFF0077B6), Color(0xFF0096C7), Color(0xFF3A67C4)),
    AppPalette("rosegold", "Rose Gold", "Jewel & Mono", Color(0xFFE18C8C), Color(0xFFE0B0A0), Color(0xFFD4AF37)),
    AppPalette("graphite", "Graphite", "Jewel & Mono", Color(0xFF64748B), Color(0xFF94A3B8), Color(0xFF475569)),
    // Pure black/white seeds have zero saturation, so hue is meaningless and
    // the generic tone() re-mapping below - which deliberately discards a
    // seed's own lightness so every palette lands on the same Material tone
    // targets - would make these two indistinguishable from each other and
    // from Graphite. roleColors() special-cases these two ids instead of
    // deriving from the seeds; the seeds here only feed the picker's preview
    // swatches, so they are kept literal.
    AppPalette("black", "Black", "Jewel & Mono", Color(0xFF000000), Color(0xFF000000), Color(0xFF000000)),
    AppPalette("white", "White", "Jewel & Mono", Color(0xFFFFFFFF), Color(0xFFFFFFFF), Color(0xFFFFFFFF))
)

/** Ordered list of the distinct categories, for grouping in the picker UI. */
val PALETTE_CATEGORIES: List<String> = APP_PALETTES.map { it.category }.distinct()

fun findPalette(id: String?): AppPalette? = APP_PALETTES.firstOrNull { it.id == id }

// --- HSL tone derivation (dependency-free; good enough for accent roles) ---

private fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    val d = max - min
    var h = 0f
    var s = 0f
    if (d != 0f) {
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
    }
    return floatArrayOf(h, s, l)
}

private fun hsl(h: Float, s: Float, l: Float): Color {
    if (s == 0f) return Color(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    fun channel(t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return Color(channel(h + 1f / 3f), channel(h), channel(h - 1f / 3f))
}

/** Re-tone a seed to a target lightness, optionally scaling its saturation. */
private fun Color.tone(lightness: Float, satScale: Float = 1f): Color {
    val (h, s, _) = toHsl()
    return hsl(h, (s * satScale).coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

/**
 * The full set of accent color roles a palette resolves to for a given mode,
 * following Material 3 tone targets (e.g. primary at tone 40 light / 80 dark,
 * containers at tone 90 light / 30 dark) so on-color pairings keep >=3:1
 * contrast. This is the single source of truth: both the applied [ColorScheme]
 * and the picker preview are built from it, so what you see is what you get.
 */
data class PaletteRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color
)

/**
 * Role colors for the two monochrome presets (Black, White), which cannot go
 * through the seed-based derivation below: it deliberately discards a seed's
 * own lightness so every hued palette lands on the same fixed Material tone
 * targets regardless of how light or dark its swatch was, and with
 * saturation 0 that lightness is the only thing that told a black seed and a
 * white seed apart. Both would come out as the exact same grey.
 *
 * [primary]/[secondary]/[tertiary] and their "on" colors stay mode-adaptive -
 * dark ink in light mode, light ink in dark mode - the same flip every other
 * palette makes for small controls, because one that goes invisible against
 * its own surface is not a design choice. What actually carries the
 * Black-vs-White identity is the containers: large fills that have room to
 * lean all the way toward one end without losing legibility, since
 * [contrastInk] picks each one's "on" color from the fill itself rather than
 * from the app's light/dark mode - so a near-black container can still carry
 * legible light text even inside the app's light mode, and vice versa.
 * Primary/secondary/tertiary containers step slightly apart from each other
 * so three filled elements on screen together still read as distinct
 * surfaces rather than one flat block.
 */
private fun monochromeRoles(dark: Boolean, leanDark: Boolean): PaletteRoles {
    val ink = if (dark) Color(0xFFF2F2F2) else Color(0xFF141414)
    val onInk = if (dark) Color(0xFF141414) else Color(0xFFF2F2F2)
    val containers = if (leanDark) {
        // Never pure black in dark mode - the app's own dark surfaces sit
        // near black too, and a container identical to its background is an
        // invisible one.
        if (dark) listOf(Color(0xFF2B2B2B), Color(0xFF363636), Color(0xFF414141))
        else listOf(Color(0xFF1E1E1E), Color(0xFF292929), Color(0xFF343434))
    } else {
        if (dark) listOf(Color(0xFFE8E8E8), Color(0xFFD8D8D8), Color(0xFFC8C8C8))
        else listOf(Color(0xFFECECEC), Color(0xFFE2E2E2), Color(0xFFD6D6D6))
    }
    val (primaryContainer, secondaryContainer, tertiaryContainer) = containers
    return PaletteRoles(
        primary = ink,
        onPrimary = onInk,
        primaryContainer = primaryContainer,
        onPrimaryContainer = contrastInk(primaryContainer),
        secondary = ink,
        onSecondary = onInk,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contrastInk(secondaryContainer),
        tertiary = ink,
        onTertiary = onInk,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contrastInk(tertiaryContainer)
    )
}

/**
 * Derive the M3 accent roles from the palette's three seeds for the active
 * mode. Primary keeps near-full chroma (expressive), containers ease off a
 * touch so soft fills don't glow.
 */
fun AppPalette.roleColors(dark: Boolean): PaletteRoles {
    when (id) {
        "black" -> return monochromeRoles(dark, leanDark = true)
        "white" -> return monochromeRoles(dark, leanDark = false)
    }
    val p = seedPrimary; val s = seedSecondary; val t = seedTertiary
    return if (dark) PaletteRoles(
        primary = p.tone(0.80f, 0.98f),
        onPrimary = p.tone(0.20f),
        primaryContainer = p.tone(0.32f, 0.92f),
        onPrimaryContainer = p.tone(0.90f, 0.75f),
        secondary = s.tone(0.80f, 0.85f),
        onSecondary = s.tone(0.20f),
        secondaryContainer = s.tone(0.30f, 0.70f),
        onSecondaryContainer = s.tone(0.90f, 0.65f),
        tertiary = t.tone(0.82f, 0.90f),
        onTertiary = t.tone(0.20f),
        tertiaryContainer = t.tone(0.32f, 0.78f),
        onTertiaryContainer = t.tone(0.92f, 0.70f)
    ) else PaletteRoles(
        primary = p.tone(0.42f),
        onPrimary = Color.White,
        primaryContainer = p.tone(0.88f, 0.88f),
        onPrimaryContainer = p.tone(0.30f),
        secondary = s.tone(0.44f, 0.90f),
        onSecondary = Color.White,
        secondaryContainer = s.tone(0.88f, 0.72f),
        onSecondaryContainer = s.tone(0.28f),
        tertiary = t.tone(0.44f),
        onTertiary = Color.White,
        tertiaryContainer = t.tone(0.88f, 0.76f),
        onTertiaryContainer = t.tone(0.28f)
    )
}

/**
 * Overlay a palette's derived accent roles onto [base] for the active mode.
 * Neutral roles (background, surfaces, on-surface text) are inherited from
 * [base] so the app's structural look and AMOLED handling stay intact.
 */
fun buildPaletteColorScheme(palette: AppPalette, dark: Boolean, base: ColorScheme): ColorScheme {
    val r = palette.roleColors(dark)
    return base.copy(
        primary = r.primary,
        onPrimary = r.onPrimary,
        primaryContainer = r.primaryContainer,
        onPrimaryContainer = r.onPrimaryContainer,
        inversePrimary = palette.seedPrimary.tone(if (dark) 0.40f else 0.80f),
        secondary = r.secondary,
        onSecondary = r.onSecondary,
        secondaryContainer = r.secondaryContainer,
        onSecondaryContainer = r.onSecondaryContainer,
        tertiary = r.tertiary,
        onTertiary = r.onTertiary,
        tertiaryContainer = r.tertiaryContainer,
        onTertiaryContainer = r.onTertiaryContainer,
        surfaceTint = r.primary
    )
}

/**
 * A legible ink color (near-black or white) to lay over a flat [background],
 * chosen by perceived luminance.
 */
fun contrastInk(background: Color): Color =
    if (background.luminance() > 0.48f) Color(0xFF0E0E0E) else Color(0xFFFDFDFD)
