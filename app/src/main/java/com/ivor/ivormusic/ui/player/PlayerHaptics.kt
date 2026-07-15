package com.ivor.ivormusic.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Expressive haptics shared by every player style.
 *
 * Scope is deliberately narrow for now: previous/next skips and the
 * play/pause toggle. Other player actions (like, shuffle, repeat, seek)
 * stay silent until there is a decision to expand this.
 */
class PlayerHaptics(private val haptics: HapticFeedback) {

    /** A previous/next skip committed (button tap or swipe gesture). */
    fun skip() = haptics.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Play/pause toggled; [nowPlaying] is the state being switched to. */
    fun playPause(nowPlaying: Boolean) = haptics.performHapticFeedback(
        if (nowPlaying) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
    )
}

@Composable
fun rememberPlayerHaptics(): PlayerHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { PlayerHaptics(haptics) }
}
