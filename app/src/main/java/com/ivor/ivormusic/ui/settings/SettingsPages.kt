package com.ivor.ivormusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.HdrOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.data.CacheManager
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.player.PlayerStylePicker
import com.ivor.ivormusic.ui.theme.ThemeMode

/**
 * The settings detail pages. Each one owns a single category from the hub.
 *
 * The re-sort matters as much as the split: video quality used to sit under
 * "Content Mode" while music quality sat under "Playback", so the same decision
 * lived in two places. Both now live on [PlaybackSettingsPage] behind one
 * Wi-Fi/mobile switch.
 */

/* ------------------------------------------------------------------ */
/* Account                                                             */
/* ------------------------------------------------------------------ */

@Composable
internal fun AccountSettingsPage(
    isLoggedIn: Boolean,
    accountRefreshKey: Int,
    sessionManager: SessionManager,
    saveVideoHistory: Boolean,
    onSaveVideoHistoryToggle: (Boolean) -> Unit,
    onShowAuthDialog: () -> Unit,
    onShowCookieSheet: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Account", onBack = onBack) {
        if (isLoggedIn) {
            item {
                SettingsSection(title = "YouTube Music") {
                    SettingsCard {
                        key(accountRefreshKey) {
                            ExpressiveAccountItem(
                                sessionManager = sessionManager,
                                textColor = MaterialTheme.colorScheme.onBackground,
                                secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SettingsDivider()
                        SettingsToggleRow(
                            icon = Icons.Rounded.CheckCircle,
                            title = "Save Watch History",
                            subtitle = if (saveVideoHistory) {
                                "Videos you watch are added to your YouTube history"
                            } else {
                                "Watching does not touch your YouTube history"
                            },
                            enabled = saveVideoHistory,
                            onToggle = onSaveVideoHistoryToggle
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Cookie,
                            title = "Replace Session Cookies",
                            subtitle = "Paste a fresh cookie header if your session goes stale",
                            onClick = onShowCookieSheet,
                            showChevron = true
                        )
                    }
                }
            }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Sign Out",
                        subtitle = "Disconnect your YouTube account",
                        onClick = onSignOut,
                        tint = SettingsRowDefaults.destructiveTint,
                        titleColor = SettingsRowDefaults.destructiveTint
                    )
                }
            }
        } else {
            // Signed out is a supported state, not an error - say what signing
            // in buys rather than nagging.
            item {
                SettingsNotice(
                    icon = Icons.Rounded.Info,
                    text = "Koda works signed out. Signing in adds your playlists, " +
                        "liked songs, subscriptions and watch history."
                )
            }

            item {
                SettingsSection(title = "YouTube Music") {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.MusicNote,
                            title = "Connect YouTube Music",
                            subtitle = "Sign in to access your playlists and liked songs",
                            onClick = onShowAuthDialog,
                            showChevron = true
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Cookie,
                            title = "Sign In With Cookies",
                            subtitle = "Paste a cookie header instead of using the web sign-in",
                            onClick = onShowCookieSheet,
                            showChevron = true
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Appearance                                                          */
/* ------------------------------------------------------------------ */

@Composable
internal fun AppearanceSettingsPage(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    colorPalette: String,
    onNavigateToColorPalette: () -> Unit,
    amoledTheme: Boolean,
    onAmoledThemeToggle: (Boolean) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    spotlightHome: Boolean,
    onSpotlightHomeToggle: (Boolean) -> Unit,
    nonExpressiveNavigationBar: Boolean,
    onNonExpressiveNavigationBarToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val paletteName = if (colorPalette == ThemePreferences.DEFAULT_COLOR_PALETTE) {
        "Dynamic (from wallpaper)"
    } else {
        com.ivor.ivormusic.ui.theme.findPalette(colorPalette)?.name ?: "Dynamic"
    }

    SettingsDetailScaffold(title = "Appearance", onBack = onBack) {
        item {
            SettingsSection(title = "Theme") {
                SettingsCard {
                    ExpressiveThemeSelectGroup(
                        currentMode = currentThemeMode,
                        onModeSelected = onThemeModeChange,
                        textColor = MaterialTheme.colorScheme.onBackground,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.Palette,
                        title = "Color palette",
                        subtitle = paletteName,
                        onClick = onNavigateToColorPalette,
                        showChevron = true
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Contrast,
                        title = "AMOLED Black",
                        subtitle = if (amoledTheme) {
                            "Pure black backgrounds in dark theme"
                        } else {
                            "Standard dark backgrounds"
                        },
                        enabled = amoledTheme,
                        onToggle = onAmoledThemeToggle
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Backgrounds") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = "Ambient Background",
                        subtitle = if (ambientBackground) {
                            "Dynamic colors from album art"
                        } else {
                            "Solid background"
                        },
                        enabled = ambientBackground,
                        onToggle = onAmbientBackgroundToggle
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Navigation") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Dashboard,
                        title = "Non-expressive navigation bar",
                        subtitle = if (nonExpressiveNavigationBar) {
                            "Standard Material 3 bar with fixed labels"
                        } else {
                            "Expressive floating navigation"
                        },
                        enabled = nonExpressiveNavigationBar,
                        onToggle = onNonExpressiveNavigationBarToggle
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Home") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Dashboard,
                        title = "Spotlight Home",
                        subtitle = if (spotlightHome) {
                            "Shortcut grid, quick picks and artwork shelves"
                        } else {
                            "Classic Home with a hero and carousels"
                        },
                        enabled = spotlightHome,
                        onToggle = onSpotlightHomeToggle
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Player                                                              */
/* ------------------------------------------------------------------ */

@Composable
internal fun PlayerSettingsPage(
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    playerArtworkColors: Boolean,
    onPlayerArtworkColorsToggle: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Player", onBack = onBack) {
        item {
            SettingsSection(title = "Style") {
                SettingsCard {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Every style plays the same music - they differ in " +
                                "layout and how you control it.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        PlayerStylePicker(
                            currentStyle = playerStyle,
                            onStyleSelected = onPlayerStyleChange
                        )
                    }
                }
            }
        }

        item {
            SettingsNotice(
                icon = Icons.Rounded.Info,
                text = "Long-press the artwork in any player to switch styles from " +
                    "the wheel without coming back here."
            )
        }

        item {
            SettingsSection(title = "Colors") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = "Album Art Colors",
                        subtitle = "Color the expanded player's buttons from the current cover",
                        enabled = playerArtworkColors,
                        onToggle = onPlayerArtworkColorsToggle
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Playback and quality                                                */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaybackSettingsPage(
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit,
    crossfadeAuto: Boolean,
    onCrossfadeAutoChange: (Boolean) -> Unit,
    crossfadeDurationMs: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
    normalizeVolume: Boolean,
    onNormalizeVolumeToggle: (Boolean) -> Unit,
    rememberVideoBrightness: Boolean,
    onRememberVideoBrightnessToggle: (Boolean) -> Unit,
    hapticsLevel: String,
    onHapticsLevelChange: (String) -> Unit,
    autoLoadQueue: Boolean,
    onAutoLoadQueueToggle: (Boolean) -> Unit,
    saveMusicHistory: Boolean,
    onSaveMusicHistoryToggle: (Boolean) -> Unit,
    musicQualityWifi: String,
    musicQualityMobile: String,
    videoQualityWifi: String,
    videoQualityMobile: String,
    onOpenQualityPicker: (QualityDialogTarget) -> Unit,
    onBack: () -> Unit
) {
    // Which network the quality rows below are talking about. Reframing the
    // whole block beats four near-identical rows that each name their network
    // in the title.
    var showingWifi by remember { mutableStateOf(true) }

    SettingsDetailScaffold(title = "Playback and quality", onBack = onBack) {
        item {
            SettingsSection(title = "Playback") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Song transitions",
                        subtitle = when {
                            !crossfadeEnabled -> "Songs change without an overlap"
                            crossfadeAuto -> "AutoMix adapts to each song, up to 15s"
                            else -> "Always overlap by ${crossfadeDurationMs / 1000}s"
                        },
                        onClick = {
                            if (crossfadeEnabled) {
                                onCrossfadeEnabledToggle(false)
                            } else {
                                onCrossfadeAutoChange(true)
                                onCrossfadeEnabledToggle(true)
                            }
                        },
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        val selectedIndex = when {
                            !crossfadeEnabled -> 0
                            crossfadeAuto -> 1
                            else -> 2
                        }
                        val labels = listOf("Off", "AutoMix", "Manual")
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            labels.forEachIndexed { index, label ->
                                SegmentedButton(
                                    selected = selectedIndex == index,
                                    onClick = {
                                        when (index) {
                                            0 -> onCrossfadeEnabledToggle(false)
                                            1 -> {
                                                onCrossfadeAutoChange(true)
                                                onCrossfadeEnabledToggle(true)
                                            }
                                            2 -> {
                                                onCrossfadeAutoChange(false)
                                                onCrossfadeEnabledToggle(true)
                                            }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = labels.size,
                                    ),
                                    icon = {
                                        SegmentedButtonDefaults.Icon(active = selectedIndex == index) {}
                                    },
                                ) {
                                    Text(label)
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = crossfadeEnabled && !crossfadeAuto,
                            enter = fadeIn(tween(200)) + slideInVertically(
                                initialOffsetY = { -it / 4 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            ),
                            exit = fadeOut(tween(150))
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Duration: ${crossfadeDurationMs / 1000}s",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Slider(
                                    value = crossfadeDurationMs.toFloat(),
                                    onValueChange = { onCrossfadeDurationChange(it.toInt()) },
                                    valueRange = 1000f..15000f,
                                    steps = 13,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsToggleRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = "Normalise volume",
                        // Says what it does to the sound rather than naming the
                        // mechanism: nobody is looking for "loudness
                        // normalisation to -14 LKFS", they are looking for the
                        // reason one song is twice as loud as the last.
                        subtitle = "Even out loud and quiet tracks",
                        enabled = normalizeVolume,
                        onToggle = onNormalizeVolumeToggle
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "Auto-load Queue",
                        subtitle = "Add recommended songs when the queue runs low",
                        enabled = autoLoadQueue,
                        onToggle = onAutoLoadQueueToggle
                    )

                    SettingsDivider()

                    // Device-local, and not the same switch as "Save Watch
                    // History" on the Account page - that one governs the
                    // YouTube account's history. The subtitle spells out what
                    // stops growing, because this feeds four other surfaces.
                    SettingsToggleRow(
                        icon = Icons.Rounded.History,
                        title = "Save Listening History",
                        subtitle = if (saveMusicHistory) {
                            "Songs you play are logged on this device"
                        } else {
                            "Paused: new plays are not recorded"
                        },
                        enabled = saveMusicHistory,
                        onToggle = onSaveMusicHistoryToggle
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Streaming quality") {
                SettingsCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HighQuality,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quality per network",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Koda picks these based on the connection in use",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = showingWifi,
                                onClick = { showingWifi = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = showingWifi) {
                                        Icon(
                                            imageVector = Icons.Rounded.Wifi,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            ) {
                                Text("Wi-Fi")
                            }
                            SegmentedButton(
                                selected = !showingWifi,
                                onClick = { showingWifi = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = {
                                    SegmentedButtonDefaults.Icon(active = !showingWifi) {
                                        Icon(
                                            imageVector = Icons.Rounded.SignalCellularAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            ) {
                                Text("Mobile data")
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Rounded.MusicNote,
                        title = "Music quality",
                        subtitle = musicQualityLabel(
                            if (showingWifi) musicQualityWifi else musicQualityMobile
                        ),
                        onClick = {
                            onOpenQualityPicker(
                                if (showingWifi) QualityDialogTarget.MUSIC_WIFI
                                else QualityDialogTarget.MUSIC_MOBILE
                            )
                        },
                        showChevron = true
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Rounded.VideoLibrary,
                        title = "Video quality",
                        subtitle = videoQualityLabel(
                            if (showingWifi) videoQualityWifi else videoQualityMobile
                        ),
                        onClick = {
                            onOpenQualityPicker(
                                if (showingWifi) QualityDialogTarget.VIDEO_WIFI
                                else QualityDialogTarget.VIDEO_MOBILE
                            )
                        },
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Touch feedback") {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "Haptics",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val levelSubtitle = when (hapticsLevel) {
                            "off" -> "Silent: nothing vibrates"
                            "subtle" -> "Only the moments that matter"
                            "expressive" -> "Every touch, felt clearly"
                            else -> "A tick for every action"
                        }
                        Text(
                            text = levelSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val levels = listOf("off", "subtle", "balanced", "expressive")
                            val labels = listOf("Off", "Subtle", "Balanced", "Rich")
                            levels.forEachIndexed { index, value ->
                                SegmentedButton(
                                    selected = hapticsLevel == value,
                                    onClick = { onHapticsLevelChange(value) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = levels.size,
                                    ),
                                ) {
                                    Text(labels[index])
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "Video") {
                SettingsCard {
                    // The fullscreen brightness drag. Default keeps the
                    // behaviour the player has always had; off means every
                    // fullscreen video reopens at the system level.
                    SettingsToggleRow(
                        icon = Icons.Rounded.BrightnessMedium,
                        title = "Remember fullscreen brightness",
                        subtitle = if (rememberVideoBrightness) {
                            "Videos reopen at the brightness you last set"
                        } else {
                            "Videos reopen at the system brightness"
                        },
                        enabled = rememberVideoBrightness,
                        onToggle = onRememberVideoBrightnessToggle
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Content and feeds                                                   */
/* ------------------------------------------------------------------ */

@Composable
internal fun ContentSettingsPage(
    localOnlyMode: Boolean,
    onLocalOnlyModeToggle: (Boolean) -> Unit,
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit,
    homeModeToggleEnabled: Boolean,
    onHomeModeToggleChange: (Boolean) -> Unit,
    timedCommentsEnabled: Boolean,
    onTimedCommentsToggle: (Boolean) -> Unit,
    shortsEnabled: Boolean,
    onShortsEnabledToggle: (Boolean) -> Unit,
    shortsHiddenActions: Set<String>,
    onShowShortsButtons: () -> Unit,
    onNavigateToNotInterested: () -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Content and feeds", onBack = onBack) {
        item {
            SettingsSection(title = "Mode") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.CloudOff,
                        title = "Local Only",
                        subtitle = if (localOnlyMode) {
                            "Offline: device library only, no internet"
                        } else {
                            "YouTube features enabled"
                        },
                        enabled = localOnlyMode,
                        onToggle = onLocalOnlyModeToggle
                    )

                    // Everything below is about YouTube content, which local-only
                    // mode switches off wholesale.
                    AnimatedVisibility(
                        visible = !localOnlyMode,
                        enter = fadeIn(tween(200)) + slideInVertically(
                            initialOffsetY = { -it / 4 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        ),
                        exit = fadeOut(tween(150))
                    ) {
                        Column {
                            SettingsDivider()
                            ExpressiveVideoModeToggleItem(
                                enabled = videoMode,
                                onToggle = onVideoModeToggle,
                                textColor = MaterialTheme.colorScheme.onBackground,
                                secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                icon = Icons.Rounded.ToggleOn,
                                title = "Home Screen Mode Toggle",
                                subtitle = if (homeModeToggleEnabled) {
                                    "Switch music and video from the Home header"
                                } else {
                                    "Change the mode here in Settings only"
                                },
                                enabled = homeModeToggleEnabled,
                                onToggle = onHomeModeToggleChange
                            )
                        }
                    }
                }
            }
        }

        if (!localOnlyMode) {
            item {
                SettingsSection(title = "Video") {
                    SettingsCard {
                        SettingsToggleRow(
                            icon = Icons.AutoMirrored.Rounded.Comment,
                            title = "Timed Comments",
                            subtitle = if (timedCommentsEnabled) {
                                "Comments appear on the seek bar where they were posted"
                            } else {
                                "Comments stay in the comment sheet"
                            },
                            enabled = timedCommentsEnabled,
                            onToggle = onTimedCommentsToggle
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            icon = Icons.Rounded.Bolt,
                            title = "Shorts",
                            subtitle = if (shortsEnabled) {
                                "Shorts shelves appear in your feeds"
                            } else {
                                "Shorts are hidden everywhere"
                            },
                            enabled = shortsEnabled,
                            onToggle = onShortsEnabledToggle
                        )

                        // Shorts button visibility - only relevant while Shorts are on
                        AnimatedVisibility(
                            visible = shortsEnabled,
                            enter = fadeIn(tween(200)) + slideInVertically(
                                initialOffsetY = { -it / 4 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            ),
                            exit = fadeOut(tween(150))
                        ) {
                            Column {
                                SettingsDivider()
                                SettingsRow(
                                    icon = Icons.Rounded.Visibility,
                                    title = "Shorts Buttons",
                                    subtitle = if (shortsHiddenActions.isEmpty()) {
                                        "All buttons shown"
                                    } else {
                                        "${shortsHiddenActions.size} hidden"
                                    },
                                    onClick = onShowShortsButtons,
                                    showChevron = true
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Recommendations") {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.NotInterested,
                            title = "Not Recommended",
                            subtitle = "Videos and channels you've hidden from your feeds",
                            onClick = onNavigateToNotInterested,
                            showChevron = true
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Subscriptions                                                       */
/* ------------------------------------------------------------------ */

@Composable
internal fun SubscriptionsSettingsPage(
    subscriptionSource: String,
    subscribeTarget: String,
    fastSubscriptionFeed: Boolean,
    onFastSubscriptionFeedToggle: (Boolean) -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onOpenRoutingPicker: (SubscriptionDialogTarget) -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Subscriptions", onBack = onBack) {
        item {
            SettingsSection(title = "Channels") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Subscriptions,
                        title = "Manage Subscriptions",
                        subtitle = "Import, export and group the channels you follow",
                        onClick = onNavigateToSubscriptions,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Where they live") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.FilterList,
                        title = "Subscriptions Shown",
                        subtitle = subscriptionSourceLabel(subscriptionSource),
                        onClick = { onOpenRoutingPicker(SubscriptionDialogTarget.SOURCE) },
                        showChevron = true
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.BookmarkAdd,
                        title = "Subscribe Saves To",
                        subtitle = subscribeTargetLabel(subscribeTarget),
                        onClick = { onOpenRoutingPicker(SubscriptionDialogTarget.TARGET) },
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Feed") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Bolt,
                        title = "Fast Subscription Refresh",
                        subtitle = if (fastSubscriptionFeed) {
                            "Much less data and exact upload times, but no duration badges"
                        } else {
                            "Full details for every video - slow on a large subscription list"
                        },
                        enabled = fastSubscriptionFeed,
                        onToggle = onFastSubscriptionFeedToggle
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Storage and cache                                                   */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StorageSettingsPage(
    cacheEnabled: Boolean,
    onCacheEnabledToggle: (Boolean) -> Unit,
    maxCacheSizeMb: Long,
    onMaxCacheSizeMbChange: (Long) -> Unit,
    currentCacheSize: Long,
    onClearCacheClick: () -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Storage and cache", onBack = onBack) {
        item {
            SettingsSection(title = "Cache") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Save,
                        title = "Cache Music",
                        subtitle = "Store streamed songs for instant replay",
                        enabled = cacheEnabled,
                        onToggle = onCacheEnabledToggle
                    )

                    SettingsDivider()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Local Cache",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = CacheManager.formatSize(currentCacheSize),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Max Cache Size",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            val options = listOf(256L, 512L, 1024L, 2048L)
                            val labels = listOf("256MB", "512MB", "1GB", "2GB")

                            options.forEachIndexed { index, size ->
                                SegmentedButton(
                                    selected = maxCacheSizeMb == size,
                                    onClick = { onMaxCacheSizeMbChange(size) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = options.size
                                    )
                                ) {
                                    Text(text = labels[index])
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Rounded.FolderOff,
                    title = "Clear Cache",
                    subtitle = "Free up storage space",
                    onClick = onClearCacheClick,
                    tint = SettingsRowDefaults.destructiveTint,
                    titleColor = SettingsRowDefaults.destructiveTint
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Notifications                                                       */
/* ------------------------------------------------------------------ */

@Composable
internal fun NotificationsSettingsPage(
    liveDownloadUpdates: Boolean,
    onLiveDownloadUpdatesToggle: (Boolean) -> Unit,
    livePlaybackUpdates: Boolean,
    onLivePlaybackUpdatesToggle: (Boolean) -> Unit,
    canPostPromoted: Boolean,
    uploadNotificationsEnabled: Boolean,
    onUploadNotificationsToggle: (Boolean) -> Unit,
    followedChannels: List<com.ivor.ivormusic.data.LocalSubscription>,
    mutedChannelIds: Set<String>,
    onChannelMutedChange: (String, Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Notifications", onBack = onBack) {
        item {
            SettingsSection(title = "New uploads") {
                SettingsCard {
                    // Off by default and opt-in here: it is a battery-and-
                    // attention commitment the user has to ask for.
                    SettingsToggleRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Notify about new uploads",
                        subtitle = if (uploadNotificationsEnabled) {
                            "Checks channels you follow on this device"
                        } else {
                            "Off: Koda only checks while you are using it"
                        },
                        enabled = uploadNotificationsEnabled,
                        onToggle = onUploadNotificationsToggle
                    )
                }
            }
        }

        if (uploadNotificationsEnabled && followedChannels.isNotEmpty()) {
            item {
                SettingsSection(title = "Channels") {
                    SettingsCard {
                        Text(
                            text = "Choose who may notify you",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        followedChannels.forEachIndexed { index, channel ->
                            if (index > 0) SettingsDivider()
                            val muted = channel.channelId in mutedChannelIds
                            SettingsToggleRow(
                                icon = Icons.Rounded.Subscriptions,
                                title = channel.name,
                                subtitle = if (muted) "Muted" else "Notifying",
                                enabled = !muted,
                                onToggle = { onChannelMutedChange(channel.channelId, !muted) }
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = "Live updates") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Bolt,
                        title = "Live download updates",
                        subtitle = "Show download progress as a live status bar chip",
                        enabled = liveDownloadUpdates,
                        onToggle = onLiveDownloadUpdatesToggle
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Live playback updates",
                        subtitle = "Show what's playing as a live status bar chip",
                        enabled = livePlaybackUpdates,
                        onToggle = onLivePlaybackUpdatesToggle
                    )

                    // Promotion is a request the system can refuse. When the
                    // user has revoked it at the OS level the toggles above are
                    // a lie, so surface the way to fix it.
                    if ((liveDownloadUpdates || livePlaybackUpdates) && !canPostPromoted) {
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Security,
                            title = "Blocked by system settings",
                            subtitle = "Allow live updates for Koda in Android settings",
                            onClick = onOpenSystemSettings,
                            tint = SettingsRowDefaults.destructiveTint,
                            titleColor = SettingsRowDefaults.destructiveTint,
                            showChevron = true
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Local library                                                       */
/* ------------------------------------------------------------------ */

@Composable
internal fun LocalLibrarySettingsPage(
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    excludedFolderCount: Int,
    onOpenFolderExclusion: () -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = "Local library", onBack = onBack) {
        item {
            SettingsSection(title = "Device music") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Folder,
                        title = "Load Local Songs",
                        subtitle = if (loadLocalSongs) {
                            "Shows songs from your device"
                        } else {
                            "YouTube Music only"
                        },
                        enabled = loadLocalSongs,
                        onToggle = onLoadLocalSongsToggle
                    )

                    AnimatedVisibility(
                        visible = loadLocalSongs,
                        enter = fadeIn(tween(200)) + slideInVertically(
                            initialOffsetY = { -it / 4 },
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        ),
                        exit = fadeOut(tween(150))
                    ) {
                        Column {
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Rounded.FolderOff,
                                title = "Excluded Folders",
                                subtitle = if (excludedFolderCount == 0) {
                                    "All folders included"
                                } else {
                                    "$excludedFolderCount folder" +
                                        "${if (excludedFolderCount == 1) "" else "s"} excluded"
                                },
                                onClick = onOpenFolderExclusion,
                                showChevron = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Advanced                                                            */
/* ------------------------------------------------------------------ */

@Composable
internal fun AdvancedSettingsPage(
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit,
    onReportBug: () -> Unit,
    onOpenTimeLimit: () -> Unit,
    onOpenAutoHelp: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    SettingsDetailScaffold(title = "Advanced", onBack = onBack) {
        // The old screen showed this section to everyone. It only means
        // anything on the OEMs that break background playback and MediaStore,
        // so lead with why it is here.
        if (isXiaomiDevice()) {
            item {
                SettingsNotice(
                    icon = Icons.Rounded.Info,
                    text = "Xiaomi device detected. Enabling both of these is highly recommended.",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        item {
            SettingsSection(title = "Wellbeing") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Bedtime,
                        title = "Daily time limit",
                        subtitle = "Lock Koda after a set amount of listening per day",
                        onClick = onOpenTimeLimit,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Feedback") {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.BugReport,
                        title = "Report a bug",
                        subtitle = "Attach recent logs and device info to a report",
                        onClick = onReportBug,
                        tint = MaterialTheme.colorScheme.primary,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Compatibility") {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Security,
                        title = "High Compatibility Scanning",
                        subtitle = "Bypasses MediaStore (Fixes missing music on HyperOS)",
                        enabled = manualScanEnabled,
                        onToggle = { enabled ->
                            if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                if (!android.os.Environment.isExternalStorageManager()) {
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                    ).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            onManualScanEnabledToggle(enabled)
                        }
                    )

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Rounded.FlashOn,
                        title = "Ignore Battery Optimizations",
                        subtitle = "Prevents playback from stopping in background",
                        onClick = {
                            val packageName = context.packageName
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            ).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback for HyperOS/Restrictive OEMs: Open App Info
                                // From here user can manually set "No restrictions" in Battery saver
                                try {
                                    val appInfoIntent = Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                    ).apply {
                                        data = Uri.parse("package:$packageName")
                                    }
                                    context.startActivity(appInfoIntent)
                                } catch (e2: Exception) {
                                    // Absolute fallback
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    )
                                }
                            }
                        },
                        tint = MaterialTheme.colorScheme.tertiary,
                        showChevron = true
                    )

                    SettingsDivider()

                    // The Auto sideload wall is the one Auto problem no code
                    // can fix, and it fails silently: Auto simply never lists
                    // Koda. Saying so converts a "the app is broken" into a
                    // solvable toggle.
                    SettingsRow(
                        icon = Icons.Rounded.DirectionsCar,
                        title = "Android Auto",
                        subtitle = "Koda missing from your car? Start here",
                        onClick = onOpenAutoHelp,
                        showChevron = true
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shared bits                                                         */
/* ------------------------------------------------------------------ */

/** Inline explanatory banner - used for empty states and device-specific advice. */
@Composable
private fun SettingsNotice(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = tint,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
