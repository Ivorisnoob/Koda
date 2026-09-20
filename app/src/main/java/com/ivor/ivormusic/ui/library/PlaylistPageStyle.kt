package com.ivor.ivormusic.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

// The playlist page's shared look: the cover-coloured ground, the ink and
// raised surfaces that stand on it, and the rail everything is set on. Shared
// by the music PlaylistDetailScreen and the video VideoPlaylistDetail so the
// two pages resolve their colours the same way by construction.

/** The playlist page's text rail: title, facts, controls and description share it. */
internal val PLAYLIST_GUTTER = 24.dp

/**
 * Pixels asked for the hero cover. A playlist thumbnail arrives from a feed at
 * around 120px, which is a blurred smear across a full-width banner; Google
 * serves a real crop at whatever size the `=w-h` directive asks for, and past
 * this it only upscales.
 */
internal const val HERO_COVER_PX = 1080

/**
 * How far the page's ground is carried from `primaryContainer` toward
 * `primary` - that is, how saturated the cover's color reads on the page.
 *
 * **The ground is not mixed with the background at all, and that is the whole
 * point.** [scar] Two attempts did mix it, at 0.45 and then 0.8 of the way
 * from the background toward `primaryContainer`, and both read as white on
 * screen. `primaryContainer` is a *pale* tone by construction - tone 90 in a
 * light theme - so anything blended between it and a tone-98 background is at
 * best a few percent off the background however hard the blend is pushed.
 * There is no value of that formula that produces a visibly colored page.
 *
 * Blending toward `primary` fixes that, but **only a light theme needs it**,
 * and applying it to both was the next bug. [scar] In a dark scheme
 * `primaryContainer` is tone 30 against a tone-6 background - already strongly
 * colored, with nothing to rescue - so carrying it toward `primary`'s tone 80
 * dragged the ground to a mid tone where `onPrimaryContainer`, its own
 * contrast pair, stopped reading. The pale-role problem is asymmetric, so the
 * correction is too.
 *
 * Left alone in the dark and deepened in the light, the ground stays a
 * `primaryContainer` in both, which is what keeps `onPrimaryContainer` a
 * guaranteed-readable ink on it by M3 construction rather than by luck. It
 * also buys the controls their contrast - the filled Play button sits on
 * `primary` and the tonal ones on `secondaryContainer`, which land on opposite
 * sides of the ground instead of vanishing into a pastel one.
 */
internal const val PLAYLIST_GROUND_DEPTH = 0.3f

/**
 * The playlist page's ground: the theme background carried most of the way to
 * the container color pulled from the cover.
 *
 * **Flat, not a gradient.** [judgement] A wash that decays down the page ends
 * with the bottom of a long track list back on the plain theme, which reads as
 * the color having run out rather than as a gradient - and it gives the cover's
 * dissolve a moving target, so the hand-off can only line up at one depth. One
 * color that the cover blends into and the page then holds has neither problem.
 *
 * Every surface that has to agree with it - the app bar, the status-bar scrim
 * over the cover, the page behind the list - resolves it through here, so they
 * are the same color by construction rather than by three matching constants.
 * Call it inside the page's artwork [MaterialTheme]; with artwork colors off it
 * resolves to the app's own primaryContainer and the page is theme-tinted.
 */
@Composable
internal fun playlistPageGround(): Color {
    val scheme = MaterialTheme.colorScheme
    // A dark scheme's container is already a strong color against a near-black
    // page and is left exactly as it is; only the light scheme's pastel needs
    // deepening. See [PLAYLIST_GROUND_DEPTH].
    if (scheme.background.luminance() < 0.5f) return scheme.primaryContainer
    return lerp(scheme.primaryContainer, scheme.primary, PLAYLIST_GROUND_DEPTH)
}

/**
 * Ink for text standing on [playlistPageGround] or [playlistRaisedSurface].
 *
 * `onPrimaryContainer`, and deliberately not `onSurface` or `onBackground`.
 * Those are the contrast pairs for surfaces that follow the *theme*, and this
 * page's ground follows the **cover** instead - so on a colored page they are
 * paired with something that is no longer behind them, which is how a dark
 * theme ended up drawing near-white text on a mid-tone ground. Because the
 * ground stays a `primaryContainer` in both themes, its own `on` role is
 * readable on it by construction, in either.
 *
 * It does still flip with the theme - dark ink in a light scheme, light ink in
 * a dark one - because the ground flips with it. What it never does is flip
 * *independently* of the ground, which is the failure that matters.
 */
@Composable
internal fun playlistOnGround(): Color = MaterialTheme.colorScheme.onPrimaryContainer

/**
 * A card or field standing on [playlistPageGround].
 *
 * Once the ground is a mid tone rather than a near-white or near-black, the
 * M3 elevation rule inverts: a container is normally a step *toward* the
 * middle from an extreme background, but there is nowhere to step to from the
 * middle, and a card that darkens on a colored page reads as a hole punched in
 * it rather than as something lying on top. So a raised surface here always
 * lifts toward the light, which is the reading that survives on any ground.
 *
 * Getting there needs the theme, because the light end of the cover's palette
 * is a different role in each: `primaryContainer` is the pale tone in a light
 * scheme and `primary` is the bright one in a dark scheme. Probed off the
 * background's luminance, the same test `rememberArtworkColorScheme` uses to
 * decide which tones to map onto the accents in the first place.
 *
 * The two fractions differ because the distances do. A light ground sits only
 * a handful of tones below `primaryContainer`, so a small step is invisible; a
 * dark ground is most of the ramp away from `primary`, so the same step would
 * overshoot into a mid tone and take [playlistOnGround] out of contrast with
 * the card - which is the whole failure this page has already had once.
 */
@Composable
internal fun playlistRaisedSurface(): Color {
    val scheme = MaterialTheme.colorScheme
    val ground = playlistPageGround()
    return if (scheme.background.luminance() < 0.5f) {
        lerp(ground, scheme.primary, 0.16f)
    } else {
        lerp(ground, scheme.primaryContainer, 0.45f)
    }
}

/** Formats a summed track duration as "1 hr 32 min" / "45 min"; null when unknown. */
internal fun formatTotalDuration(totalMs: Long): String? {
    val totalMinutes = totalMs / 60_000
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours hr $minutes min" else "$minutes min"
}
