package com.ivor.ivormusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivor.ivormusic.util.KodaHaptics
import com.ivor.ivormusic.util.rememberKodaHaptics

/**
 * Expressive haptics shared by every player style.
 *
 * Routes through [KodaHaptics], so everything here honours the user's haptics
 * setting along with the rest of the app.
 */
class PlayerHaptics(private val koda: KodaHaptics) {

    /** A previous/next skip committed (button tap or swipe gesture). */
    fun skip() = koda.confirm()

    /** Play/pause toggled; [nowPlaying] is the state being switched to. */
    fun playPause(nowPlaying: Boolean) = koda.toggle(nowPlaying)
}

@Composable
fun rememberPlayerHaptics(): PlayerHaptics {
    val koda = rememberKodaHaptics()
    return remember(koda) { PlayerHaptics(koda) }
}
