package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Subscriptions
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
 * What the video feeds show: the things mixed in among the videos, and the
 * videos to leave out.
 *
 * Like the other newer Customization pages it reads and writes its own
 * `ThemePreferences`; the feeds hold instances of their own and follow the
 * stored values, so a switch here changes a feed that is already on screen.
 *
 * Two of the rows are older settings shown where they now belong - the Shorts
 * shelf and the Subscriptions feed's "hide watched" - and keep the keys they
 * always had, so nobody's choice is reset by the move.
 */
@Composable
internal fun VideoFeedSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val feed by prefs.videoFeed.collectAsState()
    val shortsShelf by prefs.shortsEnabled.collectAsState()
    val shortsBlocked by prefs.shortsHardBlock.collectAsState()
    val hideWatchedSubs by prefs.hideWatchedInFeed.collectAsState()

    SettingsDetailScaffold(
        title = stringResource(R.string.cz_video_feed),
        onBack = onBack
    ) {
        item {
            SettingsSection(title = stringResource(R.string.cz_feed_section_mixed_in)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Forum,
                        title = stringResource(R.string.cz_feed_posts),
                        subtitle = stringResource(R.string.cz_feed_posts_sub),
                        enabled = feed.showPosts,
                        onToggle = { prefs.setVideoFeed(feed.copy(showPosts = it)) }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.cz_feed_shorts),
                        subtitle = stringResource(
                            if (shortsBlocked) R.string.cz_feed_shorts_blocked
                            else R.string.cz_feed_shorts_sub
                        ),
                        enabled = shortsShelf && !shortsBlocked,
                        onToggle = { prefs.setShortsEnabled(it) },
                        available = !shortsBlocked
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Sensors,
                        title = stringResource(R.string.cz_feed_live),
                        subtitle = stringResource(R.string.cz_feed_live_sub),
                        enabled = feed.showLive,
                        onToggle = { prefs.setVideoFeed(feed.copy(showLive = it)) }
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.cz_feed_section_watched)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.Home,
                        title = stringResource(R.string.cz_feed_hide_watched_home),
                        subtitle = stringResource(R.string.cz_feed_hide_watched_home_sub),
                        enabled = feed.hideWatchedOnHome,
                        onToggle = { prefs.setVideoFeed(feed.copy(hideWatchedOnHome = it)) }
                    )
                    SettingsToggleRow(
                        icon = Icons.Rounded.Subscriptions,
                        title = stringResource(R.string.cz_feed_hide_watched_subs),
                        subtitle = stringResource(R.string.cz_feed_hide_watched_subs_sub),
                        enabled = hideWatchedSubs,
                        onToggle = { prefs.setHideWatchedInFeed(it) }
                    )
                }
                SettingsFootnote(
                    icon = Icons.Rounded.CheckCircle,
                    text = stringResource(R.string.cz_feed_watched_note)
                )
            }
        }

        item {
            SettingsNotice(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.cz_feed_scope_note)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The feed's filters in a few words, for the row that opens this page. */
@Composable
internal fun videoFeedSummary(): String {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val feed by prefs.videoFeed.collectAsState()
    val shortsShelf by prefs.shortsEnabled.collectAsState()
    val hideWatchedSubs by prefs.hideWatchedInFeed.collectAsState()
    val hidden = listOf(
        !feed.showPosts,
        !shortsShelf,
        !feed.showLive,
        feed.hideWatchedOnHome,
        hideWatchedSubs,
    ).count { it }
    return if (hidden == 0) {
        stringResource(R.string.cz_feed_summary_all)
    } else {
        pluralStringResource(R.plurals.cz_feed_summary_some, hidden, hidden)
    }
}
