package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences

/**
 * The video player's optional gestures, each with a switch.
 *
 * Read and written through the page's own `ThemePreferences`, as the mini
 * player's page is: the player's gesture surface holds an instance of its own
 * and follows the stored value, so a switch here reaches a video that is
 * already playing.
 */
@Composable
internal fun VideoPlayerSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val gestures by prefs.videoGestures.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.cz_video_player),
        onBack = onBack
    ) {
        item {
            SettingsSection(title = stringResource(R.string.sp_mini_gestures)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.FastForward,
                        title = stringResource(R.string.cz_video_hold_speed),
                        subtitle = stringResource(R.string.cz_video_hold_speed_sub),
                        enabled = gestures.holdToSpeedUp,
                        onToggle = { prefs.setVideoGestures(gestures.copy(holdToSpeedUp = it)) }
                    )
                    val holdSpeeds = com.ivor.ivormusic.data.VideoGestureCustomization.HOLD_SPEEDS
                    SettingsChoiceRow(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.cz_video_hold_rate),
                        subtitle = stringResource(R.string.cz_video_hold_rate_sub),
                        labels = holdSpeeds.map { stringResource(R.string.cz_video_hold_rate_value, it.toInt()) },
                        selectedIndex = holdSpeeds.indexOf(gestures.holdSpeed),
                        onSelect = { prefs.setVideoGestures(gestures.copy(holdSpeed = holdSpeeds[it])) }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.LightMode,
                        title = stringResource(R.string.cz_video_brightness),
                        subtitle = stringResource(R.string.cz_video_brightness_sub),
                        enabled = gestures.brightnessSwipe,
                        onToggle = { prefs.setVideoGestures(gestures.copy(brightnessSwipe = it)) }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.cz_video_volume),
                        subtitle = stringResource(R.string.cz_video_volume_sub),
                        enabled = gestures.volumeSwipe,
                        onToggle = { prefs.setVideoGestures(gestures.copy(volumeSwipe = it)) }
                    )
                }
            }
        }
        item {
            SettingsSection(title = stringResource(R.string.cz_video_controls)) {
                SettingsCard {
                    val delays = com.ivor.ivormusic.data.VideoGestureCustomization.CONTROLS_HIDE_SECONDS
                    SettingsChoiceRow(
                        icon = Icons.Rounded.Timer,
                        title = stringResource(R.string.cz_video_controls_hide),
                        subtitle = stringResource(R.string.cz_video_controls_hide_sub),
                        labels = delays.map { stringResource(R.string.cz_video_controls_hide_value, it) },
                        selectedIndex = delays.indexOf(gestures.controlsHideSeconds),
                        onSelect = {
                            prefs.setVideoGestures(gestures.copy(controlsHideSeconds = delays[it]))
                        }
                    )
                }
            }
        }
        item {
            SettingsNotice(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.cz_video_gestures_note)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The video player's gestures in a few words, for the row that opens this page. */
@Composable
internal fun videoPlayerSummary(): String {
    val context = LocalContext.current
    val gestures by remember(context) { ThemePreferences(context) }.videoGestures.collectAsState()
    return when (val off = gestures.offCount) {
        0 -> stringResource(R.string.cz_video_gestures_all)
        3 -> stringResource(R.string.cz_video_gestures_none)
        else -> pluralStringResource(R.plurals.cz_video_gestures_some, off, off)
    }
}
