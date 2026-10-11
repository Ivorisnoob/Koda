package com.ivor.ivormusic.ui.components

import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * A single-line title that scrolls while the song plays, and stands still
 * otherwise. [isPlaying] is the only thing to pass: whether motion is wanted
 * at all is never the call site's decision, so it is answered once here.
 */
@Composable
fun Modifier.titleMarquee(isPlaying: Boolean = true): Modifier {
    val stillPreferred = rememberStillPreferred()
    return if (isPlaying && !stillPreferred) {
        this.basicMarquee(
            iterations = Int.MAX_VALUE,
            repeatDelayMillis = 2000,
            initialDelayMillis = 2000,
            velocity = 30.dp
        )
    } else {
        this
    }
}

/**
 * Whether motion is wanted at all. The animation scale of zero is the
 * accessibility setting and Battery Saver is the same answer for a different
 * reason; both are read once because neither needs to land mid-frame. This is
 * the check the visualizer bars and the motion artwork make, for the same
 * reason: continuous animation is a choice, and someone who turned it off has
 * already made it.
 */
@Composable
private fun rememberStillPreferred(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val noAnimation = runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        }.getOrDefault(false)
        val saving = runCatching {
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)
        noAnimation || saving
    }
}
