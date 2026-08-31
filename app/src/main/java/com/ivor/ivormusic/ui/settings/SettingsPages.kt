package com.ivor.ivormusic.ui.settings
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R
import kotlin.math.roundToInt

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FormatSize
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import com.ivor.ivormusic.data.AppMode
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
    SettingsDetailScaffold(title = stringResource(R.string.settings_account), onBack = onBack) {
        if (isLoggedIn) {
            item {
                SettingsSection(title = stringResource(R.string.settings_section_youtube_music)) {
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
                            title = stringResource(R.string.sp_save_watch_history),
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
                            title = stringResource(R.string.sp_replace_session_cookies),
                            subtitle = stringResource(R.string.sp_replace_session_cookies_sub),
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
                        title = stringResource(R.string.sign_out),
                        subtitle = stringResource(R.string.sp_sign_out_sub),
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
                    text = stringResource(R.string.sp_signed_out_info),
                )
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_youtube_music)) {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.sp_connect_youtube_music),
                            subtitle = stringResource(R.string.sp_connect_youtube_music_sub),
                            onClick = onShowAuthDialog,
                            showChevron = true
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Rounded.Cookie,
                            title = stringResource(R.string.sp_sign_in_cookies),
                            subtitle = stringResource(R.string.sp_sign_in_cookies_sub),
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
    uiScale: Float,
    onNavigateToDisplaySize: () -> Unit,
    onBack: () -> Unit
) {
    val paletteName = if (colorPalette == ThemePreferences.DEFAULT_COLOR_PALETTE) {
        "Dynamic (from wallpaper)"
    } else {
        com.ivor.ivormusic.ui.theme.findPalette(colorPalette)?.name ?: "Dynamic"
    }

    SettingsDetailScaffold(title = stringResource(R.string.settings_appearance), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_theme)) {
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
                        title = stringResource(R.string.sp_color_palette),
                        subtitle = paletteName,
                        onClick = onNavigateToColorPalette,
                        showChevron = true
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Rounded.Contrast,
                        title = stringResource(R.string.sp_amoled_black),
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
            SettingsSection(title = stringResource(R.string.sp_backgrounds)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.sp_ambient_background),
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
            SettingsSection(title = stringResource(R.string.sp_display_size)) {
                SettingsCard {
                    // A page rather than a slider here: the scale is worth
                    // previewing before it is applied, and a preview needs
                    // room the hub list does not have.
                    SettingsRow(
                        icon = Icons.Rounded.FormatSize,
                        title = stringResource(R.string.sp_display_size),
                        subtitle = "${(uiScale * 100).roundToInt()}%",
                        onClick = onNavigateToDisplaySize,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_navigation)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Dashboard,
                        title = stringResource(R.string.sp_non_expressive_nav),
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
            SettingsSection(title = stringResource(R.string.tab_home)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Dashboard,
                        title = stringResource(R.string.sp_spotlight_home),
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
    SettingsDetailScaffold(title = stringResource(R.string.settings_player), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_style)) {
                SettingsCard {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.sp_style_note),
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
                text = stringResource(R.string.sp_style_switch_hint),
            )
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_colors)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.sp_album_art_colors),
                        subtitle = stringResource(R.string.sp_album_art_colors_sub),
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
    onNavigateToTvExtensions: () -> Unit,
    onBack: () -> Unit
) {
    // Which network the quality rows below are talking about. Reframing the
    // whole block beats four near-identical rows that each name their network
    // in the title.
    var showingWifi by remember { mutableStateOf(true) }

    SettingsDetailScaffold(title = stringResource(R.string.settings_playback_and_quality), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_playback)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.sp_song_transitions),
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
                        val labels = listOf(
                            stringResource(R.string.haptic_level_off),
                            stringResource(R.string.sp_crossfade_automix),
                            stringResource(R.string.sp_crossfade_manual)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween
                            ),
                        ) {
                            labels.forEachIndexed { index, label ->
                                ToggleButton(
                                    checked = selectedIndex == index,
                                    onCheckedChange = {
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
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        labels.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
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
                        title = stringResource(R.string.sp_normalise_volume),
                        // Says what it does to the sound rather than naming the
                        // mechanism: nobody is looking for "loudness
                        // normalisation to -14 LKFS", they are looking for the
                        // reason one song is twice as loud as the last.
                        subtitle = stringResource(R.string.sp_normalise_volume_sub),
                        enabled = normalizeVolume,
                        onToggle = onNormalizeVolumeToggle
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = stringResource(R.string.sp_auto_load_queue),
                        subtitle = stringResource(R.string.sp_auto_load_queue_sub),
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
                        title = stringResource(R.string.sp_save_listening_history),
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
            SettingsSection(title = stringResource(R.string.sp_streaming_quality)) {
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
                                    text = stringResource(R.string.sp_quality_per_network),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.sp_quality_network_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val networkOptions = listOf(
                            true to Pair(Icons.Rounded.Wifi, stringResource(R.string.wifi)),
                            false to Pair(Icons.Rounded.SignalCellularAlt, stringResource(R.string.mobile_data)),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween
                            ),
                        ) {
                            networkOptions.forEachIndexed { index, (wifi, presentation) ->
                                ToggleButton(
                                    checked = showingWifi == wifi,
                                    onCheckedChange = { showingWifi = wifi },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        networkOptions.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = presentation.first,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(presentation.second)
                                }
                            }
                        }
                    }

                    SettingsDivider()

                    SettingsRow(
                        icon = Icons.Rounded.MusicNote,
                        title = stringResource(R.string.sp_music_quality),
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
                        title = stringResource(R.string.sp_video_quality),
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
                            text = stringResource(R.string.sp_haptics),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val levelSubtitle = when (hapticsLevel) {
                            "off" -> stringResource(R.string.sp_haptics_sub_off)
                            "subtle" -> stringResource(R.string.sp_haptics_sub_subtle)
                            "expressive" -> stringResource(R.string.sp_haptics_sub_expressive)
                            else -> stringResource(R.string.sp_haptics_sub_balanced)
                        }
                        Text(
                            text = levelSubtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val levels = listOf("off", "subtle", "balanced", "expressive")
                        val labels = listOf(
                            stringResource(R.string.haptic_level_off),
                            stringResource(R.string.haptic_level_subtle),
                            stringResource(R.string.haptic_level_balanced),
                            stringResource(R.string.haptic_level_rich)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween
                            ),
                        ) {
                            levels.forEachIndexed { index, value ->
                                ToggleButton(
                                    checked = hapticsLevel == value,
                                    onCheckedChange = { onHapticsLevelChange(value) },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        levels.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(labels[index], maxLines = 1)
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

        item {
            SettingsSection(title = stringResource(R.string.sp_tv_mode)) {
                SettingsCard {
                    // TV mode's addons live here rather than on the TV screen
                    // itself. Installing a source is a one-off setup decision,
                    // and a permanent button for it on the browsing surface
                    // made the mode look like it was about managing addons
                    // rather than about watching something.
                    SettingsRow(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.sp_tv_extensions),
                        subtitle = stringResource(R.string.sp_tv_extensions_subtitle),
                        onClick = onNavigateToTvExtensions,
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
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
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
    SettingsDetailScaffold(title = stringResource(R.string.settings_content_and_feeds), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_mode)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.CloudOff,
                        title = stringResource(R.string.sp_local_only),
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
                            ExpressiveAppModeToggleItem(
                                mode = appMode,
                                onModeChange = onAppModeChange,
                                textColor = MaterialTheme.colorScheme.onBackground,
                                secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                            SettingsDivider()
                            SettingsToggleRow(
                                icon = Icons.Rounded.ToggleOn,
                                title = stringResource(R.string.sp_home_mode_toggle),
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
                SettingsSection(title = stringResource(R.string.cat_videos)) {
                    SettingsCard {
                        SettingsToggleRow(
                            icon = Icons.AutoMirrored.Rounded.Comment,
                            title = stringResource(R.string.sp_timed_comments),
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
                            title = stringResource(R.string.sp_shorts),
                            subtitle = if (shortsEnabled) {
                                stringResource(R.string.sp_shorts_enabled_sub)
                            } else {
                                stringResource(R.string.sp_shorts_disabled_sub)
                            },
                            enabled = shortsEnabled,
                            onToggle = onShortsEnabledToggle
                        )

                        // Action-rail choices only apply to the dedicated swipe player.
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
                                    title = stringResource(R.string.sp_shorts_buttons),
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
                SettingsSection(title = stringResource(R.string.sp_recommendations)) {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.NotInterested,
                            title = stringResource(R.string.sp_not_recommended),
                            subtitle = stringResource(R.string.sp_not_recommended_sub),
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
    SettingsDetailScaffold(title = stringResource(R.string.settings_subscriptions), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.section_channels)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Subscriptions,
                        title = stringResource(R.string.sp_manage_subscriptions),
                        subtitle = stringResource(R.string.sp_manage_subscriptions_sub),
                        onClick = onNavigateToSubscriptions,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_where_they_live)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.FilterList,
                        title = stringResource(R.string.sp_subscriptions_shown),
                        subtitle = subscriptionSourceLabel(subscriptionSource),
                        onClick = { onOpenRoutingPicker(SubscriptionDialogTarget.SOURCE) },
                        showChevron = true
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Rounded.BookmarkAdd,
                        title = stringResource(R.string.sp_subscribe_saves_to),
                        subtitle = subscribeTargetLabel(subscribeTarget),
                        onClick = { onOpenRoutingPicker(SubscriptionDialogTarget.TARGET) },
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_feed)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.sp_fast_refresh),
                        subtitle = if (fastSubscriptionFeed) {
                            stringResource(R.string.sp_fast_refresh_on)
                        } else {
                            stringResource(R.string.sp_fast_refresh_off)
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
    privateDownloadsEnabled: Boolean,
    onPrivateDownloadsEnabledToggle: (Boolean) -> Unit,
    cacheEnabled: Boolean,
    onCacheEnabledToggle: (Boolean) -> Unit,
    maxCacheSizeMb: Long,
    onMaxCacheSizeMbChange: (Long) -> Unit,
    currentCacheSize: Long,
    onClearCacheClick: () -> Unit,
    onBack: () -> Unit
) {
    SettingsDetailScaffold(title = stringResource(R.string.settings_storage_and_cache), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_downloads)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.sp_private_downloads),
                        subtitle = if (privateDownloadsEnabled) {
                            stringResource(R.string.sp_private_downloads_on)
                        } else {
                            stringResource(R.string.sp_private_downloads_off)
                        },
                        enabled = privateDownloadsEnabled,
                        onToggle = onPrivateDownloadsEnabledToggle
                    )
                }
                SettingsFootnote(
                    icon = Icons.Rounded.Visibility,
                    text = stringResource(R.string.sp_private_downloads_note)
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_cache)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Save,
                        title = stringResource(R.string.sp_cache_music),
                        subtitle = stringResource(R.string.sp_cache_music_sub),
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
                                    text = stringResource(R.string.sp_local_cache),
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
                            text = stringResource(R.string.sp_max_cache_size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val options = listOf(256L, 512L, 1024L, 2048L)
                        val labels = listOf("256MB", "512MB", "1GB", "2GB")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                ButtonGroupDefaults.ConnectedSpaceBetween
                            ),
                        ) {
                            options.forEachIndexed { index, size ->
                                ToggleButton(
                                    checked = maxCacheSizeMb == size,
                                    onCheckedChange = { onMaxCacheSizeMbChange(size) },
                                    modifier = Modifier.weight(1f),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        options.lastIndex ->
                                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
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
                    title = stringResource(R.string.sp_clear_cache),
                    subtitle = stringResource(R.string.sp_clear_cache_sub),
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
    SettingsDetailScaffold(title = stringResource(R.string.settings_notifications), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_new_uploads)) {
                SettingsCard {
                    // Off by default and opt-in here: it is a battery-and-
                    // attention commitment the user has to ask for.
                    SettingsToggleRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = stringResource(R.string.sp_notify_new_uploads),
                        subtitle = if (uploadNotificationsEnabled) {
                            stringResource(R.string.sp_notify_new_uploads_on)
                        } else {
                            stringResource(R.string.sp_notify_new_uploads_off)
                        },
                        enabled = uploadNotificationsEnabled,
                        onToggle = onUploadNotificationsToggle
                    )
                }
            }
        }

        if (uploadNotificationsEnabled && followedChannels.isNotEmpty()) {
            item {
                SettingsSection(title = stringResource(R.string.sp_channels)) {
                    SettingsCard {
                        Text(
                            text = stringResource(R.string.sp_choose_notifiers),
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
                                subtitle = if (muted) {
                                    stringResource(R.string.sp_muted)
                                } else {
                                    stringResource(R.string.sp_notifying)
                                },
                                enabled = !muted,
                                onToggle = { onChannelMutedChange(channel.channelId, !muted) }
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_live_updates)) {                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.sp_live_download_updates),
                        subtitle = stringResource(R.string.sp_live_download_updates_sub),
                        enabled = liveDownloadUpdates,
                        onToggle = onLiveDownloadUpdatesToggle
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.sp_live_playback_updates),
                        subtitle = stringResource(R.string.sp_live_playback_updates_sub),
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
                            title = stringResource(R.string.sp_blocked_by_system),
                            subtitle = stringResource(R.string.sp_blocked_by_system_sub),
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
    SettingsDetailScaffold(title = stringResource(R.string.settings_local_library), onBack = onBack) {
        item {
            SettingsSection(title = stringResource(R.string.sp_device_music)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Folder,
                        title = stringResource(R.string.sp_load_local_songs),
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
                                title = stringResource(R.string.sp_excluded_folders),
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

    SettingsDetailScaffold(title = stringResource(R.string.settings_advanced), onBack = onBack) {
        // The old screen showed this section to everyone. It only means
        // anything on the OEMs that break background playback and MediaStore,
        // so lead with why it is here.
        if (isXiaomiDevice()) {
            item {
                SettingsNotice(
                    icon = Icons.Rounded.Info,
                    text = stringResource(R.string.sp_xiaomi_notice),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_wellbeing)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Bedtime,
                        title = stringResource(R.string.sp_daily_time_limit),
                        subtitle = stringResource(R.string.sp_daily_time_limit_sub),
                        onClick = onOpenTimeLimit,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_feedback)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.sp_report_bug),
                        subtitle = stringResource(R.string.sp_report_bug_sub),
                        onClick = onReportBug,
                        tint = MaterialTheme.colorScheme.primary,
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.sp_compatibility)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.sp_high_compat_scanning),
                        subtitle = stringResource(R.string.sp_high_compat_scanning_sub),
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
                        title = stringResource(R.string.sp_ignore_battery),
                        subtitle = stringResource(R.string.sp_ignore_battery_sub),
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

/** Quiet supporting copy that belongs to the setting above it, not a separate alert. */
@Composable
private fun SettingsFootnote(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 17.sp
        )
    }
}
