package com.ivor.ivormusic.ui.player

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Accent seed colors pulled from one album cover. */
private data class ArtworkSeeds(val primary: Color, val secondary: Color)

/**
 * Derives a color scheme for the expanded player from the current album
 * cover: Palette swatches become the accent roles buttons draw from
 * (primary/secondary/tertiary families), while surfaces and background
 * stay on the app theme. Colors switch directly to the new song's seeds,
 * no crossfade. Returns [base] unchanged while disabled, while extraction
 * is in flight, or when the art has no usable swatches.
 */
@Composable
fun rememberArtworkColorScheme(
    enabled: Boolean,
    albumArtUri: String?,
    base: ColorScheme
): ColorScheme {
    val context = LocalContext.current
    var seeds by remember { mutableStateOf<ArtworkSeeds?>(null) }

    LaunchedEffect(enabled, albumArtUri) {
        if (!enabled || albumArtUri.isNullOrBlank()) {
            seeds = null
            return@LaunchedEffect
        }
        // Keep the previous song's seeds while the new art loads so the
        // scheme doesn't flash back to the theme colors between songs.
        extractArtworkSeeds(context, albumArtUri)?.let { seeds = it }
    }

    val current = seeds
    if (!enabled || current == null) return base

    val isDark = base.background.luminance() < 0.5f
    // With album colours set to the whole app, this surface takes its own
    // cover's complete scheme the way the root takes the playing song's:
    // accents over [base] would now be one album's buttons on another's
    // surfaces. For the player that is the same scheme the app is wearing.
    val theming = com.ivor.ivormusic.ui.theme.LocalAlbumTheming.current
    return remember(base, current, isDark, theming) {
        if (theming.wholeApp) {
            com.ivor.ivormusic.ui.theme.buildSeedColorScheme(current.primary, isDark, theming.style)
                .let { scheme ->
                    if (isDark && theming.amoled) {
                        with(com.ivor.ivormusic.ui.theme.AmoledScheme) { scheme.amoled() }
                    } else {
                        scheme
                    }
                }
        } else {
            base.withArtworkAccents(current.primary, current.secondary, isDark)
        }
    }
}

/**
 * The cover's main colour alone, for the root theme to build the app's
 * scheme from. Keeps the previous song's colour while the next cover loads,
 * so the app fades from album to album rather than through the palette, and
 * is null when [enabled] is off or there is no cover to read.
 */
@Composable
fun rememberArtworkSeed(enabled: Boolean, albumArtUri: String?): Color? {
    val context = LocalContext.current
    var seed by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(enabled, albumArtUri) {
        if (!enabled || albumArtUri.isNullOrBlank()) {
            seed = null
            return@LaunchedEffect
        }
        extractArtworkSeeds(context, albumArtUri)?.let { seed = it.primary }
    }
    return if (enabled) seed else null
}

/**
 * Map two seed colors onto the accent roles, tones chosen to mirror the
 * M3 dark/light role tones (primary 80/40, container 30/90, etc.) so
 * contrast pairs stay readable on any cover.
 */
private fun ColorScheme.withArtworkAccents(
    primarySeed: Color,
    secondarySeed: Color,
    isDark: Boolean
): ColorScheme {
    return if (isDark) {
        copy(
            primary = primarySeed.withTone(0.80f),
            onPrimary = primarySeed.withTone(0.20f),
            primaryContainer = primarySeed.withTone(0.30f),
            onPrimaryContainer = primarySeed.withTone(0.90f),
            inversePrimary = primarySeed.withTone(0.40f),
            secondary = secondarySeed.withTone(0.80f),
            onSecondary = secondarySeed.withTone(0.20f),
            secondaryContainer = secondarySeed.withTone(0.30f),
            onSecondaryContainer = secondarySeed.withTone(0.90f),
            tertiary = secondarySeed.withTone(0.85f),
            onTertiary = secondarySeed.withTone(0.25f),
            tertiaryContainer = secondarySeed.withTone(0.35f),
            onTertiaryContainer = secondarySeed.withTone(0.92f),
            surfaceTint = primarySeed.withTone(0.80f)
        )
    } else {
        copy(
            primary = primarySeed.withTone(0.40f),
            onPrimary = primarySeed.withTone(0.98f),
            primaryContainer = primarySeed.withTone(0.90f),
            onPrimaryContainer = primarySeed.withTone(0.10f),
            inversePrimary = primarySeed.withTone(0.80f),
            secondary = secondarySeed.withTone(0.40f),
            onSecondary = secondarySeed.withTone(0.98f),
            secondaryContainer = secondarySeed.withTone(0.90f),
            onSecondaryContainer = secondarySeed.withTone(0.10f),
            tertiary = secondarySeed.withTone(0.35f),
            onTertiary = secondarySeed.withTone(0.97f),
            tertiaryContainer = secondarySeed.withTone(0.88f),
            onTertiaryContainer = secondarySeed.withTone(0.08f),
            surfaceTint = primarySeed.withTone(0.40f)
        )
    }
}

/**
 * Approximate an HCT tone shift: blend toward white above the color's own
 * luminance, toward black below it, keeping the hue.
 */
private fun Color.withTone(tone: Float): Color {
    val lum = luminance()
    return if (tone >= lum) {
        lerp(this, Color.White, ((tone - lum) / (1f - lum).coerceAtLeast(0.01f)).coerceIn(0f, 1f))
    } else {
        lerp(Color.Black, this, (tone / lum.coerceAtLeast(0.01f)).coerceIn(0f, 1f))
    }
}

/** Pull vibrant + muted swatches from the cover (small software bitmap). */
private suspend fun extractArtworkSeeds(
    context: Context,
    uri: String
): ArtworkSeeds? = withContext(Dispatchers.IO) {
    try {
        // The app's shared Coil loader: same instance every other surface
        // uses, so this artwork is likely already in its memory cache.
        val loader = context.imageLoader
        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false) // Palette needs a software bitmap
            .size(128)
            .build()
        val result = loader.execute(request)
        val bitmap = (result as? SuccessResult)?.let { (it.drawable as? BitmapDrawable)?.bitmap }
            ?: return@withContext null
        val palette = Palette.from(bitmap).generate()

        val primary = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch
            ?: return@withContext null
        val secondary = palette.mutedSwatch
            ?: palette.lightMutedSwatch
            ?: palette.darkMutedSwatch
            ?: primary

        ArtworkSeeds(Color(primary.rgb), Color(secondary.rgb))
    } catch (e: Exception) {
        null
    }
}
