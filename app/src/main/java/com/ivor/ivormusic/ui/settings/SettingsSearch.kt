package com.ivor.ivormusic.ui.settings

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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        "account", "Account", "Account", Icons.Rounded.AccountCircle,
        listOf("sign in", "signin", "login", "google", "youtube", "profile", "connect")
    ) { onOpenPage(SettingsPage.ACCOUNT) }
    entry(
        "watch_history", "Save Watch History", "Account", Icons.Rounded.CheckCircle,
        listOf("history", "watched", "incognito", "private", "tracking")
    ) { onOpenPage(SettingsPage.ACCOUNT) }
    entry(
        "cookies", "Replace Session Cookies", "Account", Icons.Rounded.Cookie,
        listOf("cookie", "session", "token", "auth", "stale", "expired")
    ) { onOpenPage(SettingsPage.ACCOUNT) }
    entry(
        "sign_out", "Sign Out", "Account", Icons.AutoMirrored.Rounded.Logout,
        listOf("logout", "log out", "disconnect", "remove account")
    ) { onOpenPage(SettingsPage.ACCOUNT) }

    // Appearance
    entry(
        "theme", "Theme", "Appearance", Icons.Rounded.Contrast,
        listOf("dark", "light", "system", "night", "day", "mode")
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "palette", "Color palette", "Appearance", Icons.Rounded.Palette,
        listOf("colour", "accent", "dynamic", "material you", "monet", "wallpaper", "scheme")
    ) { onNavigateToColorPalette() }
    entry(
        "amoled", "AMOLED Black", "Appearance", Icons.Rounded.Contrast,
        listOf("oled", "pure black", "true black", "battery saving", "deep dark")
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "ambient", "Ambient Background", "Appearance", Icons.Rounded.Palette,
        listOf("album art", "artwork", "background", "blur", "glow")
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "spotlight_home", "Spotlight Home", "Appearance", Icons.Rounded.Dashboard,
        listOf(
            "home", "home screen", "layout", "shortcuts", "grid", "shelves",
            "quick picks", "spotify", "feed", "classic home"
        )
    ) { onOpenPage(SettingsPage.APPEARANCE) }
    entry(
        "non_expressive_navigation_bar",
        "Non-expressive navigation bar",
        "Appearance",
        Icons.Rounded.Dashboard,
        listOf(
            "navigation", "navbar", "nav bar", "short navigation bar", "compact",
            "classic", "floating", "expressive"
        )
    ) { onOpenPage(SettingsPage.APPEARANCE) }

    // Player
    entry(
        "player_style", "Player Style", "Player", Icons.Rounded.PlayCircle,
        listOf(
            "layout", "skin", "look", "classic", "gesture", "editorial", "canvas",
            "poster", "bento", "sticker", "morph", "dial"
        )
    ) { onOpenPage(SettingsPage.PLAYER) }
    entry(
        "artwork_colors", "Album Art Colors", "Player", Icons.Rounded.Palette,
        listOf("artwork", "colour", "buttons", "tint", "cover")
    ) { onOpenPage(SettingsPage.PLAYER) }

    // Playback and quality
    entry(
        "crossfade", "Crossfade", "Playback and quality", Icons.Rounded.GraphicEq,
        listOf("fade", "blend", "transition", "overlap", "gapless")
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "autoqueue", "Auto-load Queue", "Playback and quality",
        Icons.AutoMirrored.Rounded.QueueMusic,
        listOf("queue", "autoplay", "radio", "recommended", "keep playing", "endless")
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "music_history", "Save Listening History", "Playback and quality",
        Icons.Rounded.History,
        listOf(
            "history", "listening", "recently played", "played", "log", "track",
            "incognito", "private", "pause history", "clear history", "stats"
        )
    ) { onOpenPage(SettingsPage.PLAYBACK) }
    entry(
        "music_q_wifi", "Music quality on Wi-Fi", "Playback and quality",
        Icons.Rounded.MusicNote,
        listOf("bitrate", "audio", "sound", "data saver", "high", "normal", "kbps")
    ) { onOpenQualityPicker(QualityDialogTarget.MUSIC_WIFI) }
    entry(
        "music_q_mobile", "Music quality on mobile data", "Playback and quality",
        Icons.Rounded.SignalCellularAlt,
        listOf("bitrate", "audio", "cellular", "data saver", "roaming", "kbps")
    ) { onOpenQualityPicker(QualityDialogTarget.MUSIC_MOBILE) }
    entry(
        "video_q_wifi", "Video quality on Wi-Fi", "Playback and quality",
        Icons.Rounded.VideoLibrary,
        listOf("resolution", "1080p", "720p", "480p", "4k", "2160", "default quality")
    ) { onOpenQualityPicker(QualityDialogTarget.VIDEO_WIFI) }
    entry(
        "video_q_mobile", "Video quality on mobile data", "Playback and quality",
        Icons.Rounded.SignalCellularAlt,
        listOf("resolution", "cellular", "data", "1080p", "720p", "roaming")
    ) { onOpenQualityPicker(QualityDialogTarget.VIDEO_MOBILE) }
    entry(
        "hdr", "Prefer HDR Videos", "Playback and quality", Icons.Rounded.HdrOn,
        listOf("hdr", "high dynamic range", "hlg", "dolby", "colour depth")
    ) { onOpenPage(SettingsPage.PLAYBACK) }

    // Content and feeds
    entry(
        "local_only", "Local Only", "Content and feeds", Icons.Rounded.CloudOff,
        listOf("offline", "airplane", "no internet", "disable youtube", "private", "data off")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "content_mode", "Content Mode", "Content and feeds", Icons.Rounded.VideoLibrary,
        listOf("video mode", "music mode", "switch", "youtube videos")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "home_toggle", "Home Screen Mode Toggle", "Content and feeds", Icons.Rounded.ToggleOn,
        listOf("home", "header", "switcher", "quick toggle")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "timed_comments", "Timed Comments", "Content and feeds",
        Icons.AutoMirrored.Rounded.Comment,
        listOf("comments", "seek bar", "timeline", "timestamps")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "shorts", "Shorts", "Content and feeds", Icons.Rounded.Bolt,
        listOf("shorts", "reels", "short videos", "hide shorts", "vertical")
    ) { onOpenPage(SettingsPage.CONTENT) }
    entry(
        "shorts_buttons", "Shorts Buttons", "Content and feeds", Icons.Rounded.Visibility,
        listOf("hide buttons", "like", "share", "overlay", "actions")
    ) { onShowShortsButtons() }
    entry(
        "not_interested", "Not Recommended", "Content and feeds", Icons.Rounded.NotInterested,
        listOf(
            "blocked", "hidden", "not interested", "blocklist", "dont recommend",
            "blocked channels", "unhide"
        )
    ) { onNavigateToNotInterested() }

    // Subscriptions
    entry(
        "manage_subs", "Manage Subscriptions", "Subscriptions", Icons.Rounded.Subscriptions,
        listOf(
            "import", "export", "opml", "takeout", "newpipe", "pipepipe", "groups",
            "channels", "follow", "backup"
        )
    ) { onNavigateToSubscriptions() }
    entry(
        "subs_source", "Subscriptions Shown", "Subscriptions", Icons.Rounded.FilterList,
        listOf("source", "feed", "which", "local", "account", "merged")
    ) { onOpenRoutingPicker(SubscriptionDialogTarget.SOURCE) }
    entry(
        "subs_target", "Subscribe Saves To", "Subscriptions", Icons.Rounded.BookmarkAdd,
        listOf("target", "where", "device", "account", "save subscriptions")
    ) { onOpenRoutingPicker(SubscriptionDialogTarget.TARGET) }
    entry(
        "fast_subs", "Fast Subscription Refresh", "Subscriptions", Icons.Rounded.Bolt,
        listOf("rss", "refresh", "speed", "data usage", "feed slow", "duration badges")
    ) { onOpenPage(SettingsPage.SUBSCRIPTIONS) }

    // Storage
    entry(
        "cache_music", "Cache Music", "Storage and cache", Icons.Rounded.Save,
        listOf("cache", "store", "replay", "offline songs", "buffer")
    ) { onOpenPage(SettingsPage.STORAGE) }
    entry(
        "cache_size", "Max Cache Size", "Storage and cache", Icons.Rounded.Folder,
        listOf("size", "limit", "storage", "space", "mb", "gb", "how much")
    ) { onOpenPage(SettingsPage.STORAGE) }
    entry(
        "clear_cache", "Clear Cache", "Storage and cache", Icons.Rounded.FolderOff,
        listOf("clear", "delete", "free space", "wipe", "clean", "reset storage")
    ) { onOpenPage(SettingsPage.STORAGE) }

    // Notifications - only where the platform can promote an ongoing notification
    if (supportsLiveUpdates) {
        entry(
            "live_download", "Live download updates", "Notifications", Icons.Rounded.Bolt,
            listOf("notification", "status bar", "chip", "download progress", "live update")
        ) { onOpenPage(SettingsPage.NOTIFICATIONS) }
        entry(
            "live_playback", "Live playback updates", "Notifications", Icons.Rounded.GraphicEq,
            listOf("notification", "now playing", "status bar", "chip", "live update")
        ) { onOpenPage(SettingsPage.NOTIFICATIONS) }
    }

    // Local library
    entry(
        "local_songs", "Load Local Songs", "Local library", Icons.Rounded.Folder,
        listOf("device", "files", "mp3", "sd card", "my music", "scan", "offline")
    ) { onOpenPage(SettingsPage.LOCAL_LIBRARY) }
    entry(
        "excluded_folders", "Excluded Folders", "Local library", Icons.Rounded.FolderOff,
        listOf("exclude", "ignore", "hide folder", "ringtones", "whatsapp", "recordings")
    ) { onOpenFolderExclusion() }

    // Advanced
    entry(
        "compat_scan", "High Compatibility Scanning", "Advanced", Icons.Rounded.Security,
        listOf(
            "mediastore", "hyperos", "miui", "xiaomi", "redmi", "poco",
            "missing music", "songs not showing", "scan"
        )
    ) { onOpenPage(SettingsPage.ADVANCED) }
    entry(
        "battery", "Ignore Battery Optimizations", "Advanced", Icons.Rounded.FlashOn,
        listOf(
            "battery", "background", "playback stops", "killed", "doze",
            "optimisation", "keep alive"
        )
    ) { onOpenPage(SettingsPage.ADVANCED) }

    // About
    entry(
        "about", "About Koda", "About", Icons.Rounded.Info,
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
        placeholder = "Search settings",
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
