package com.ivor.ivormusic.util

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.ivor.ivormusic.MainActivity
import com.ivor.ivormusic.R

/**
 * The launcher's long-press shortcuts: open straight into music mode or video
 * mode. Each can be dragged onto the home screen as its own icon.
 *
 * Dynamic rather than a static shortcuts.xml, for two reasons. A static
 * shortcut's intent names its package literally, which is wrong on the
 * `.debug` build; and it would have to be declared on all nine app-icon
 * `<activity-alias>`es, since shortcuts belong to the launcher activity that
 * is enabled. A dynamic shortcut binds to whichever alias is enabled when it
 * is published, so [publish] runs on every start: after an icon change it
 * re-attaches to the new alias instead of vanishing with the old one.
 */
object ModeShortcuts {
    /** Carried by a shortcut's intent; [MainActivity] switches mode on it. */
    const val ACTION_OPEN_MUSIC = "com.ivor.ivormusic.action.OPEN_MUSIC"
    const val ACTION_OPEN_VIDEOS = "com.ivor.ivormusic.action.OPEN_VIDEOS"

    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = listOf(
            shortcut(context, "open_music", ACTION_OPEN_MUSIC,
                R.string.shortcut_music, R.string.shortcut_music_long, R.drawable.ic_shortcut_music, rank = 0),
            shortcut(context, "open_videos", ACTION_OPEN_VIDEOS,
                R.string.shortcut_videos, R.string.shortcut_videos_long, R.drawable.ic_shortcut_videos, rank = 1),
        )
        // A launcher can refuse (rate limits, no launcher activity enabled
        // mid icon switch); the shortcuts are a convenience, never a crash.
        runCatching { manager.dynamicShortcuts = shortcuts }
            .onFailure { KLog.w("ModeShortcuts", "Could not publish launcher shortcuts", it) }
    }

    private fun shortcut(
        context: Context,
        id: String,
        action: String,
        shortLabel: Int,
        longLabel: Int,
        icon: Int,
        rank: Int,
    ): ShortcutInfo = ShortcutInfo.Builder(context, id)
        .setShortLabel(context.getString(shortLabel))
        .setLongLabel(context.getString(longLabel))
        .setIcon(Icon.createWithResource(context, icon))
        .setRank(rank)
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        .build()
}
