package com.ivor.ivormusic.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.toArgb
import com.ivor.ivormusic.data.ThemePreferences

/**
 * The two accent colors a generated playlist cover is drawn from, resolved the
 * same way [IvorMusicTheme] resolves its scheme: the chosen palette's seeds, or
 * the wallpaper's dynamic colors when the palette is "dynamic".
 *
 * Lives here rather than in `PlaylistRepository` because the repository is data
 * layer and has no business reaching into the theme; the ViewModels that create
 * and rename playlists resolve this and hand it down. Both of them do - a
 * playlist created from the player's add-to-playlist sheet gets the same colors
 * as one created from the Library.
 *
 * Read fresh on every call. The palette is picked in Settings through that
 * screen's own [ThemePreferences] instance, and StateFlow updates do not cross
 * instances, so a cached value would keep drawing covers in the palette the
 * user already left.
 *
 * Returns null when there is nothing to resolve - "dynamic" below API 31, where
 * the platform has no wallpaper colors to read. The generator's own fallback is
 * the honest answer there.
 */
fun playlistCoverSeeds(context: Context): Pair<Int, Int>? {
    val paletteId = ThemePreferences.currentColorPalette(context)
    findPalette(paletteId)?.let { palette ->
        return palette.seedPrimary.toArgb() to palette.seedTertiary.toArgb()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = dynamicDarkColorScheme(context)
        return scheme.primary.toArgb() to scheme.tertiary.toArgb()
    }
    return null
}
