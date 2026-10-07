package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlin.math.roundToInt

/**
 * Everything about how Koda looks and behaves, in one place and sorted by the
 * part of the app it changes: its colours, its players, its layout and how it
 * answers a touch.
 *
 * A page of ways in rather than a page of settings. Each row opens the page
 * that owns that surface and shows what is chosen there now, as the hub's rows
 * do, so the whole look of the app can be read off this one screen. A setting
 * that changes how something looks or what a gesture does belongs behind one of
 * these rows; a new surface gets a row of its own here, not a row on the hub.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CustomizationSettingsPage(
    currentThemeMode: ThemeMode,
    colorPalette: String,
    appIcon: String,
    uiScale: Float,
    playerStyle: PlayerStyle,
    spotlightHome: Boolean,
    nonExpressiveNavigationBar: Boolean,
    hapticsLevel: String,
    onOpenPage: (SettingsPage) -> Unit,
    onBack: () -> Unit
) {
    val themeLabel = when (currentThemeMode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
    val paletteName = if (colorPalette == ThemePreferences.DEFAULT_COLOR_PALETTE) {
        "Dynamic"
    } else {
        com.ivor.ivormusic.ui.theme.findPalette(colorPalette)?.name ?: "Dynamic"
    }
    val currentAppIcon = remember(appIcon) { AppIcon.fromId(appIcon) }
    val hapticsLabel = stringResource(
        when (hapticsLevel) {
            "off" -> R.string.haptic_level_off
            "subtle" -> R.string.haptic_level_subtle
            "expressive" -> R.string.haptic_level_rich
            else -> R.string.haptic_level_balanced
        }
    )

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_customization),
        onBack = onBack
    ) {
        item {
            SettingsSection(title = stringResource(R.string.cz_section_look)) {
                SettingsCard {
                    SettingsHubRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_theme_and_colors),
                        value = "$themeLabel, $paletteName",
                        onClick = { onOpenPage(SettingsPage.APPEARANCE) },
                        tint = MaterialTheme.colorScheme.tertiary,
                        iconShape = MaterialShapes.Cookie9Sided.toShape(),
                        explanation = stringResource(R.string.cz_info_theme)
                    )
                    SettingsHubRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.sp_app_icon),
                        value = stringResource(currentAppIcon.titleRes),
                        onClick = { onOpenPage(SettingsPage.APP_ICON) },
                        tint = MaterialTheme.colorScheme.tertiary,
                        iconShape = MaterialShapes.Sunny.toShape(),
                        explanation = stringResource(R.string.si_app_icon)
                    )
                    SettingsHubRow(
                        icon = Icons.Rounded.FormatSize,
                        title = stringResource(R.string.sp_display_size),
                        value = "${(uiScale * 100).roundToInt()}%",
                        onClick = { onOpenPage(SettingsPage.DISPLAY_SIZE) },
                        tint = MaterialTheme.colorScheme.tertiary,
                        iconShape = MaterialShapes.Pentagon.toShape(),
                        explanation = stringResource(R.string.si_display_size)
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.cz_section_players)) {
                SettingsCard {
                    SettingsHubRow(
                        icon = Icons.Rounded.PlayCircle,
                        title = stringResource(R.string.settings_player),
                        value = com.ivor.ivormusic.ui.player.playerStyleInfo(playerStyle).label,
                        onClick = { onOpenPage(SettingsPage.PLAYER) },
                        iconShape = MaterialShapes.Clover4Leaf.toShape(),
                        explanation = stringResource(R.string.si_hub_player)
                    )
                    SettingsHubRow(
                        icon = Icons.Rounded.PictureInPictureAlt,
                        title = stringResource(R.string.sp_mini_player),
                        value = miniPlayerSummary(),
                        onClick = { onOpenPage(SettingsPage.MINI_PLAYER) },
                        iconShape = MaterialShapes.Pill.toShape(),
                        explanation = stringResource(R.string.cz_info_mini_player)
                    )
                    SettingsHubRow(
                        icon = Icons.Rounded.SmartDisplay,
                        title = stringResource(R.string.cz_video_player),
                        value = videoPlayerSummary(),
                        onClick = { onOpenPage(SettingsPage.VIDEO_PLAYER) },
                        iconShape = MaterialShapes.Gem.toShape(),
                        explanation = stringResource(R.string.cz_info_video_player)
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.cz_section_layout)) {
                SettingsCard {
                    SettingsHubRow(
                        icon = Icons.Rounded.Dashboard,
                        title = stringResource(R.string.sp_home_and_navigation),
                        value = stringResource(
                            if (spotlightHome) R.string.sp_home_spotlight else R.string.sp_home_classic
                        ) + ", " + stringResource(
                            if (nonExpressiveNavigationBar) R.string.sp_nav_standard
                            else R.string.sp_nav_floating
                        ),
                        onClick = { onOpenPage(SettingsPage.HOME_NAVIGATION) },
                        tint = MaterialTheme.colorScheme.secondary,
                        iconShape = MaterialShapes.Cookie6Sided.toShape(),
                        explanation = stringResource(R.string.cz_info_home)
                    )
                    SettingsHubRow(
                        icon = Icons.Rounded.VideoLibrary,
                        title = stringResource(R.string.cz_video_feed),
                        value = videoFeedSummary(),
                        onClick = { onOpenPage(SettingsPage.VIDEO_FEED) },
                        tint = MaterialTheme.colorScheme.secondary,
                        iconShape = MaterialShapes.Cookie4Sided.toShape(),
                        explanation = stringResource(R.string.cz_info_video_feed)
                    )
                    SettingsHubRow(
                        icon = Icons.Rounded.TouchApp,
                        title = stringResource(R.string.settings_gestures),
                        value = stringResource(R.string.cz_gestures_value, hapticsLabel),
                        onClick = { onOpenPage(SettingsPage.GESTURES) },
                        tint = MaterialTheme.colorScheme.secondary,
                        iconShape = MaterialShapes.SoftBurst.toShape(),
                        explanation = stringResource(R.string.cz_info_gestures)
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
