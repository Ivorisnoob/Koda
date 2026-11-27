package com.ivor.ivormusic.ui.player

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic as FilledQueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown as FilledKeyboardArrowDown
import androidx.compose.material.icons.filled.Pause as FilledPause
import androidx.compose.material.icons.filled.PlayArrow as FilledPlayArrow
import androidx.compose.material.icons.filled.Repeat as FilledRepeat
import androidx.compose.material.icons.filled.RepeatOne as FilledRepeatOne
import androidx.compose.material.icons.filled.Shuffle as FilledShuffle
import androidx.compose.material.icons.filled.SkipNext as FilledSkipNext
import androidx.compose.material.icons.filled.SkipPrevious as FilledSkipPrevious
import androidx.compose.ui.graphics.vector.ImageVector
import com.ivor.ivormusic.data.playerlayout.LayoutAction

/**
 * Which icon family a player draws its transport glyphs in. Every player but
 * Classic draws play, the skips, shuffle, repeat, the queue and collapse from
 * the filled set and the rest (lyrics, the sleep timer, the menu) from the
 * rounded one; Classic draws all of them rounded. A block in a player's skin
 * uses that player's family, so its skip arrow is the same arrow.
 */
internal enum class ActionGlyphs { ROUNDED, FILLED }

/** The filled glyph for [action], or null where every player draws the rounded one. */
internal fun filledGlyph(action: LayoutAction, isPlaying: Boolean, repeatOne: Boolean): ImageVector? =
    when (action) {
        LayoutAction.PLAY_PAUSE -> if (isPlaying) Icons.Filled.FilledPause else Icons.Filled.FilledPlayArrow
        LayoutAction.PREVIOUS -> Icons.Filled.FilledSkipPrevious
        LayoutAction.NEXT -> Icons.Filled.FilledSkipNext
        LayoutAction.SHUFFLE -> Icons.Filled.FilledShuffle
        LayoutAction.REPEAT -> if (repeatOne) Icons.Filled.FilledRepeatOne else Icons.Filled.FilledRepeat
        LayoutAction.QUEUE -> Icons.AutoMirrored.Filled.FilledQueueMusic
        LayoutAction.COLLAPSE -> Icons.Filled.FilledKeyboardArrowDown
        else -> null
    }
