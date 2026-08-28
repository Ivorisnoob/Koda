package com.ivor.ivormusic.ui.settings
import com.ivor.ivormusic.R

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.ui.components.SearchEmptyState
import com.ivor.ivormusic.ui.components.SearchField
import com.ivor.ivormusic.util.MatchField
import com.ivor.ivormusic.util.fuzzyScore

/**
 * Settings search.
 *
 * The screen carries about fifty individual settings, and people do not know
 * what they are called - they know what they want. So the index carries
 * synonyms alongside each title ("offline" finds Local Only, "battery" finds
 * the OEM fix, "data saver" finds music quality), and the matcher tolerates
 * partial words, transposed words and typos rather than demanding the exact
 * string.
 *
 * Each entry owns an [action] instead of just a destination, so results that
 * are really a picker (music quality, subscribe target) can open that picker
 * directly rather than dropping you on a page to hunt for the row.
 */
internal data class SettingsSearchEntry(
    val id: String,
    val title: String,
    /** Where it lives, shown under the result so the location is learnable. */
    val category: String,
    val icon: ImageVector,
    /** Extra words people actually type. Never shown. */
    val keywords: List<String> = emptyList(),
    val action: () -> Unit
)

/* ------------------------------------------------------------------ */
/* Matching                                                            */
/* ------------------------------------------------------------------ */

/**
 * Scores a whole query against an entry, or null when it does not match.
 *
 * The matcher itself lives in [com.ivor.ivormusic.util.fuzzyScore], shared
 * with channel search. All that belongs here is the weighting: a title hit is
 * worth far more than the same hit in a synonym, and a synonym more than the
 * page it happens to sit on.
 */
internal fun scoreSettingsEntry(query: String, entry: SettingsSearchEntry): Int? =
    fuzzyScore(
        query,
        MatchField(entry.title, weight = 3),
        MatchField(entry.keywords.joinToString(" "), weight = 2),
        MatchField(entry.category, weight = 1)
    )

/** Best matches first, capped so the list stays scannable. */
internal fun searchSettings(
    query: String,
    entries: List<SettingsSearchEntry>,
    limit: Int = 12
): List<SettingsSearchEntry> {
    if (query.isBlank()) return emptyList()
    return entries
        .mapNotNull { entry -> scoreSettingsEntry(query, entry)?.let { entry to it } }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}

/* ------------------------------------------------------------------ */
/* The index                                                           */
/* ------------------------------------------------------------------ */

/**
 * Every setting the screen owns, with the words people reach for.
 *
 * The synonym lists are the working part. "Battery" is the word for the OEM
 * fix, "offline" is the word for Local Only, "bitrate" is the word for music
 * quality - none of those appear in the titles.
 */
@Composable
internal fun buildSettingsSearchIndex(
    onOpenPage: (SettingsPage) -> Unit,
    onOpenQualityPicker: (QualityDialogTarget) -> Unit,
    onOpenRoutingPicker: (SubscriptionDialogTarget) -> Unit,
    onShowAbout: () -> Unit,
    onShowShortsButtons: () -> Unit,
    onOpenFolderExclusion: () -> Unit,
    onNavigateToColorPalette: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToNotInterested: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToReportBug: () -> Unit,
    onNavigateToTimeLimit: () -> Unit,
    supportsLiveUpdates: Boolean
): List<SettingsSearchEntry> = buildList {
    fun entry(
        id: String,
        title: String,
        category: String,
        icon: ImageVector,
        keywords: List<String>,
        action: () -> Unit
    ) = add(SettingsSearchEntry(id, title, category, icon, keywords, action))

    // Account
    entry(
        "account", stringResource(R.string.settings_account), "Account", Icons.Rounded.AccountCircle,
        listOf("sign in", "signin", "login", "google", "youtube", "profile", "connect")
    ) { onOpenPage(SettingsPage.ACCOUNT) }
    entry(
        "watch_history", stringResource(R.string.sp_save_watch_history), "Account", Icons.Rounded.CheckCircle,
        listOf("history", "watched", "incognito", "private", "tracking")
    ) { onOpenPage(SettingsPage.ACCOUNT) }
    entry(
        "cookies", stringResource(R.string.sp_replace_session_cookies), "Account", Icons.Rounded.Cookie,
        listOf("cookie", "session", "token", "auth", "stale", "expired")
    ) { onOpenPage(SettingsPage.ACCOUNT) }
    entry(
        "sign_out", stringResource(R.string.sign_out), "Account", Icons.AutoMirrored.Rounded.Logout,
        listOf("logout", "log out", "disconnect", "remove account")
    ) { onOpenPage(SettingsPage.ACCOUNT) }

    // Appearance
    entry(
        "theme", stringResource(R.string.sp_theme), stringResource(R.string.settings_appearance), Icons.Rounded.Contrast,
        listOf("dark", "light", "system", "night", "day", "mode")
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "palette", stringResource(R.string.sp_color_palette), stringResource(R.string.settings_appearance), Icons.Rounded.Palette,
        listOf("colour", "accent", "dynamic", "material you", "monet", "wallpaper", "scheme")
    ) { onNavigateToColorPalette() }
    entry(
        "amoled", stringResource(R.string.sp_amoled_black), stringResource(R.string.settings_appearance), Icons.Rounded.Contrast,
        listOf("oled", "pure black", "true black", "battery saving", "deep dark")
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "ambient", stringResource(R.string.sp_ambient_background), stringResource(R.string.settings_appearance), Icons.Rounded.Palette,
        listOf("album art", "artwork", "background", "blur", "glow")
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "spotlight_home", stringResource(R.string.sp_spotlight_home), stringResource(R.string.settings_appearance), Icons.Rounded.Dashboard,
        listOf(
            "home", "home screen", "layout", "shortcuts", "grid", "shelves",
            "quick picks", "spotify", "feed", "classic home"
        )
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "non_expressive_navigation_bar",
        stringResource(R.string.sp_non_expressive_nav),
        stringResource(R.string.settings_appearance),
        Icons.Rounded.Dashboard,
        listOf(
            "navigation", "navbar", "nav bar", "short navigation bar", "compact",
            "classic", "floating", "expressive"
        )
    ) { onOpenPage(SettingsPage.APPEARANCE) }

    // Player
    entry(
        "player_style", stringResource(R.string.settings_player), stringResource(R.string.settings_player), Icons.Rounded.PlayCircle,
        listOf(
            "layout", "skin", "look", "classic", "gesture", "editorial", "canvas",
            "poster", "bento", "sticker", "morph", "dial"
        )
    ) { onOpenPage(SettingsPage.PLAYER) }
    entry(
        "artwork_colors", stringResource(R.string.sp_album_art_colors), stringResource(R.string.settings_player), Icons.Rounded.Palette,
        listOf("artwork", "colour", "buttons", "tint", "cover")
    ) { onOpenPage(SettingsPage.PLAYER) }

    // Playback and quality
    entry(
        "crossfade", stringResource(R.string.ob_crossfade), stringResource(R.string.settings_playback_and_quality), Icons.Rounded.GraphicEq,
        listOf(
            "fade", "blend", "transition", "overlap", "gapless", "automix",
            "automatic", "manual", "smart transition"
        )
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "normalizevolume", stringResource(R.string.sp_normalise_volume), stringResource(R.string.settings_playback_and_quality),
        Icons.Rounded.VolumeUp,
        // Both spellings, and the words people actually type when one song is
        // twice as loud as the last.
        listOf(
            "normalise", "normalize", "loudness", "volume", "levelling", "leveling",
            "replaygain", "gain", "too loud", "quiet", "even out"
        )
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "autoqueue", stringResource(R.string.sp_auto_load_queue), stringResource(R.string.settings_playback_and_quality),
        Icons.AutoMirrored.Rounded.QueueMusic,
        listOf("queue", "autoplay", "radio", "recommended", "keep playing", "endless")
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "music_history", stringResource(R.string.sp_save_listening_history), stringResource(R.string.settings_playback_and_quality),
        Icons.Rounded.History,
        listOf(
            "history", "listening", "recently played", "played", "log", "track",
            "incognito", "private", "pause history", "clear history", "stats"
        )
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "music_q_wifi", stringResource(R.string.sp_music_quality_wifi), stringResource(R.string.settings_playback_and_quality),
        Icons.Rounded.MusicNote,
        listOf("bitrate", "audio", "sound", "data saver", "high", "normal", "kbps")
    ) { onOpenQualityPicker(QualityDialogTarget.MUSIC_WIFI) }
    entry(
        "music_q_mobile", stringResource(R.string.sp_music_quality_mobile), stringResource(R.string.settings_playback_and_quality),
        Icons.Rounded.SignalCellularAlt,
        listOf("bitrate", "audio", "cellular", "data saver", "roaming", "kbps")
    ) { onOpenQualityPicker(QualityDialogTarget.MUSIC_MOBILE) }
    entry(
        "video_q_wifi", stringResource(R.string.sp_video_quality_wifi), stringResource(R.string.settings_playback_and_quality),
        Icons.Rounded.VideoLibrary,
        listOf("resolution", "1080p", "720p", "480p", "4k", "2160", "default quality")
    ) { onOpenQualityPicker(QualityDialogTarget.VIDEO_WIFI) }
    entry(
        "video_q_mobile", stringResource(R.string.sp_video_quality_mobile), stringResource(R.string.settings_playback_and_quality),
        Icons.Rounded.SignalCellularAlt,
        listOf("resolution", "cellular", "data", "1080p", "720p", "roaming")
    ) { onOpenQualityPicker(QualityDialogTarget.VIDEO_MOBILE) }
    entry(
        "video_brightness", "Remember fullscreen brightness", "Playback and quality",
        Icons.Rounded.BrightnessMedium,
        listOf(
            "brightness", "dim", "slider", "fullscreen", "gesture", "reset",
            "system brightness"
        )
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "haptics", "Haptics", "Playback and quality",
        Icons.Rounded.Vibration,
        listOf(
            "haptics", "vibration", "vibrate", "feedback", "touch", "buzz",
            "rumble", "feel", "silent"
        )
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "upload_notifications", "Notify about new uploads", "Notifications",
        Icons.Rounded.NotificationsActive,
        listOf(
            "upload", "new video", "subscription notification", "background check",
            "channel alert", "notify me"
        )
    ) { onOpenPage(SettingsPage.NOTIFICATIONS) }
    // Content and feeds
    entry(
        "local_only", stringResource(R.string.sp_local_only), stringResource(R.string.settings_content_and_feeds), Icons.Rounded.CloudOff,
        listOf("offline", "airplane", "no internet", "disable youtube", "private", "data off")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "content_mode", stringResource(R.string.sp_mode), stringResource(R.string.settings_content_and_feeds), Icons.Rounded.VideoLibrary,
        listOf("video mode", "music mode", "switch", "youtube videos")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "home_toggle", stringResource(R.string.sp_home_mode_toggle), stringResource(R.string.settings_content_and_feeds), Icons.Rounded.ToggleOn,
        listOf("home", "header", "switcher", "quick toggle")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "timed_comments", stringResource(R.string.sp_timed_comments), stringResource(R.string.settings_content_and_feeds),
        Icons.AutoMirrored.Rounded.Comment,
        listOf("comments", "seek bar", "timeline", "timestamps")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "shorts", stringResource(R.string.sp_shorts), stringResource(R.string.settings_content_and_feeds), Icons.Rounded.Bolt,
        listOf(
            "shorts", "reels", "short videos", "hide shorts", "home feed",
            "normal player", "standard player", "swipe player", "vertical"
        )
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "shorts_buttons", stringResource(R.string.sp_shorts_buttons), stringResource(R.string.settings_content_and_feeds), Icons.Rounded.Visibility,
        listOf("hide buttons", "like", "share", "overlay", "actions")
    ) { onShowShortsButtons() }
    entry(
        "not_interested", stringResource(R.string.sp_not_recommended), stringResource(R.string.settings_content_and_feeds), Icons.Rounded.NotInterested,
        listOf(
            "blocked", "hidden", "not interested", "blocklist", "dont recommend",
            "blocked channels", "unhide"
        )
    ) { onNavigateToNotInterested() }

    // Subscriptions
    entry(
        "manage_subs", stringResource(R.string.sp_manage_subscriptions), "Subscriptions", Icons.Rounded.Subscriptions,
        listOf(
            "import", "export", "opml", "takeout", "newpipe", "pipepipe", "groups",
            "channels", "follow", "backup"
        )
    ) { onNavigateToSubscriptions() }
    entry(
        "subs_source", stringResource(R.string.sp_subscriptions_shown), "Subscriptions", Icons.Rounded.FilterList,
        listOf("source", "feed", "which", "local", "account", "merged")
    ) { onOpenRoutingPicker(SubscriptionDialogTarget.SOURCE) }
    entry(
        "subs_target", stringResource(R.string.sp_subscribe_saves_to), "Subscriptions", Icons.Rounded.BookmarkAdd,
        listOf("target", "where", "device", "account", "save subscriptions")
    ) { onOpenRoutingPicker(SubscriptionDialogTarget.TARGET) }
    entry(
        "fast_subs", stringResource(R.string.sp_fast_refresh), "Subscriptions", Icons.Rounded.Bolt,
        listOf("rss", "refresh", "speed", "data usage", "feed slow", "duration badges")
    ) { onOpenPage(SettingsPage.SUBSCRIPTIONS) }

    // Storage
    entry(
        "private_downloads", stringResource(R.string.sp_private_downloads), "Storage and cache", Icons.Rounded.Security,
        listOf(
            "hidden", "hide files", "file manager", "downloads folder", "app only",
            "private storage", "gallery", "album art", "thumbnail"
        )
    ) { onOpenPage(SettingsPage.STORAGE) }
    entry(
        "cache_music", stringResource(R.string.sp_cache_music), "Storage and cache", Icons.Rounded.Save,
        listOf("cache", "store", "replay", "offline songs", "buffer")
    ) { onOpenPage(SettingsPage.STORAGE) }
    entry(
        "cache_size", stringResource(R.string.sp_max_cache_size), "Storage and cache", Icons.Rounded.Folder,
        listOf("size", "limit", "storage", "space", "mb", "gb", "how much")
    ) { onOpenPage(SettingsPage.STORAGE) }
    entry(
        "clear_cache", stringResource(R.string.sp_clear_cache), "Storage and cache", Icons.Rounded.FolderOff,
        listOf("clear", "delete", "free space", "wipe", "clean", "reset storage")
    ) { onOpenPage(SettingsPage.STORAGE) }

    // Backup and restore. Three entries rather than one, because people arrive
    // at this from opposite directions - "back up" before a new phone, and
    // "restore" or "transfer" after one - and neither word finds the other.
    entry(
        "backup", stringResource(R.string.bk_create), stringResource(R.string.settings_backup_and_restore), Icons.Rounded.SettingsBackupRestore,
        listOf(
            "backup", "back up", "export", "save everything", "new phone",
            "transfer", "move", "migrate", "copy"
        )
    ) { onNavigateToBackup() }
    entry(
        "restore", stringResource(R.string.bk_restore), stringResource(R.string.settings_backup_and_restore), Icons.Rounded.Restore,
        listOf(
            "restore", "import", "recover", "reinstall", "lost", "bring back",
            "old phone", "transfer"
        )
    ) { onNavigateToBackup() }
    entry(
        "backup_playlists", stringResource(R.string.bk_hold_2), stringResource(R.string.settings_backup_and_restore), Icons.Rounded.Save,
        listOf(
            "playlists", "liked songs", "stats", "history", "keep", "protect",
            "lose", "wipe", "uninstall"
        )
    ) { onNavigateToBackup() }

    // Notifications - only where the platform can promote an ongoing notification
    if (supportsLiveUpdates) {
        entry(
            "live_download", stringResource(R.string.sp_live_download_updates), "Notifications", Icons.Rounded.Bolt,
            listOf("notification", "status bar", "chip", "download progress", "live update")
        ) { onOpenPage(SettingsPage.NOTIFICATIONS) }
        entry(
            "live_playback", stringResource(R.string.sp_live_playback_updates), "Notifications", Icons.Rounded.GraphicEq,
            listOf("notification", "now playing", "status bar", "chip", "live update")
        ) { onOpenPage(SettingsPage.NOTIFICATIONS) }
    }

    // Local library
    entry(
        "local_songs", stringResource(R.string.sp_load_local_songs), "Local library", Icons.Rounded.Folder,
        listOf("device", "files", "mp3", "sd card", "my music", "scan", "offline")
    ) { onOpenPage(SettingsPage.LOCAL_LIBRARY) }
    entry(
        "excluded_folders", stringResource(R.string.sp_excluded_folders), "Local library", Icons.Rounded.FolderOff,
        listOf("exclude", "ignore", "hide folder", "ringtones", "whatsapp", "recordings")
    ) { onOpenFolderExclusion() }

    // Advanced
    entry(
        "compat_scan", stringResource(R.string.sp_high_compat_scanning), "Advanced", Icons.Rounded.Security,
        listOf(
            "mediastore", "hyperos", "miui", "xiaomi", "redmi", "poco",
            "missing music", "songs not showing", "scan"
        )
    ) { onOpenPage(SettingsPage.ADVANCED) }
    entry(
        "battery", stringResource(R.string.sp_ignore_battery), "Advanced", Icons.Rounded.FlashOn,
        listOf(
            "battery", "background", "playback stops", "killed", "doze",
            "optimisation", "keep alive"
        )
    ) { onOpenPage(SettingsPage.ADVANCED) }
    entry(
        "android_auto", stringResource(R.string.sp_android_auto), "Advanced", Icons.Rounded.DirectionsCar,
        listOf(
            "auto", "car", "vehicle", "android auto", "sideload", "unknown sources",
            "not showing in car", "play store"
        )
    ) { onOpenPage(SettingsPage.ADVANCED) }
    entry(
        "report_bug", stringResource(R.string.sp_report_bug), "Advanced", Icons.Rounded.BugReport,
        listOf(
            "bug report", "crash", "logs", "logcat", "feedback", "problem",
            "not working", "broken", "error", "diagnostics", "telegram"
        )
    ) { onNavigateToReportBug() }
    entry(
        "time_limit", stringResource(R.string.sp_daily_time_limit), "Advanced", Icons.Rounded.Bedtime,
        listOf(
            "screen time", "usage", "app lock", "lock", "limit", "focus",
            "digital wellbeing", "parental", "hours", "listening time"
        )
    ) { onNavigateToTimeLimit() }

    // About
    entry(
        "about", stringResource(R.string.settings_section_about), stringResource(R.string.settings_section_about), Icons.Rounded.Info,
        listOf("version", "update", "changelog", "github", "licence", "build")
    ) { onShowAbout() }
}

/* ------------------------------------------------------------------ */
/* UI                                                                  */
/* ------------------------------------------------------------------ */

@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = stringResource(R.string.ss_search_settings),
        modifier = modifier
    )
}

@Composable
internal fun SettingsSearchResultRow(
    entry: SettingsSearchEntry,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "resultScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.category,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shown when a query matches nothing - names the query so it reads as an answer. */
@Composable
internal fun SettingsSearchEmptyState(query: String) {
    SearchEmptyState(
        title = "No settings match \"$query\"",
        hint = "Try a shorter word, or what the setting does rather than its name."
    )
}
