package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ThemePreferences

/**
 * Standby's two choices: whether Koda offers it when the phone is set down on
 * its side to charge, and whether it dims itself when left alone.
 *
 * Like the other newer Customization pages it reads and writes its own
 * `ThemePreferences`; Standby and its offer hold instances of their own and
 * follow the stored values, so a switch here applies the next time either
 * looks.
 */
@Composable
internal fun StandbySettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val suggest by prefs.standbySuggest.collectAsState()
    val dim by prefs.standbyDim.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.standby_title),
        onBack = onBack
    ) {
        item {
            SettingsSection(title = stringResource(R.string.cz_standby_section)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.BatteryChargingFull,
                        title = stringResource(R.string.cz_standby_suggest),
                        subtitle = stringResource(R.string.cz_standby_suggest_sub),
                        enabled = suggest,
                        onToggle = prefs::setStandbySuggest
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Brightness4,
                        title = stringResource(R.string.cz_standby_dim),
                        subtitle = stringResource(R.string.cz_standby_dim_sub),
                        enabled = dim,
                        onToggle = prefs::setStandbyDim
                    )
                }
            }
        }

        item {
            SettingsNotice(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.cz_standby_how)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** How Standby is reached, in a few words, for the row that opens this page. */
@Composable
internal fun standbySummary(): String {
    val context = LocalContext.current
    val suggest by remember(context) { ThemePreferences(context) }.standbySuggest.collectAsState()
    return stringResource(
        if (suggest) R.string.cz_standby_summary_suggest else R.string.cz_standby_summary_menu
    )
}
