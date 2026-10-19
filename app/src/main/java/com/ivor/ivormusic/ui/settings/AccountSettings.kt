package com.ivor.ivormusic.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AccountSwitcher
import com.ivor.ivormusic.data.DownloadRepository
import com.ivor.ivormusic.data.IncognitoMode
import com.ivor.ivormusic.data.KodaProfileCard
import com.ivor.ivormusic.data.KodaProfileStore
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.LocalSubscriptionsRepository
import com.ivor.ivormusic.data.NotInterestedRepository
import com.ivor.ivormusic.data.Profile
import com.ivor.ivormusic.data.SavedPlaylistsRepository
import com.ivor.ivormusic.data.SearchHistoryRepository
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.VideoHistoryRepository
import com.ivor.ivormusic.ui.account.AccountSwitcherSheet
import com.ivor.ivormusic.ui.profile.ProfileAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Account page: who Koda is being right now, what that profile holds, who
 * else is on this device, and the switches that decide what gets recorded.
 *
 * It follows the profile roster rather than the `isLoggedIn` flag its host
 * keeps, because a profile can be switched from this page and "signed out" is
 * only one of the states a profile can be in: a device profile never had an
 * account, and a restored or expired one has an account and no session.
 *
 * The counts are the reason the page exists. A profile is where history and
 * taste are kept (docs/identity.md), and before this nothing showed how much of
 * either a profile held, or which of it would stay behind on a switch.
 */
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
    /** Sign into another account as a new profile. The host clears the web login first. */
    onAddYouTubeAccount: () -> Unit,
    /** Sign the active profile back in, keeping it the same profile. */
    onReconnectProfile: () -> Unit,
    onOpenPage: (SettingsPage) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val switcher = remember(context) { AccountSwitcher(context) }
    val cardStore = remember(context) { KodaProfileStore(context) }
    val profiles by switcher.profiles.collectAsState()
    val activeId by switcher.activeProfileId.collectAsState()
    val cards by cardStore.cards.collectAsState()
    val incognito by IncognitoMode.enabled(context).collectAsState()
    val active = profiles.firstOrNull { it.id == activeId }

    // A YouTube profile with no stored session: restored from a backup, or
    // never signed into here. Resolved per roster change, since each lookup
    // is a decrypt.
    val needSignIn = remember(profiles, isLoggedIn, accountRefreshKey) {
        profiles.filter { !it.isLocal && !switcher.hasStoredSession(it.id) }
            .mapTo(HashSet()) { it.id }
    }
    val signedIn = remember(activeId, isLoggedIn, accountRefreshKey) { sessionManager.isLoggedIn() }

    val holdings by produceState<ProfileHoldings?>(null, activeId, accountRefreshKey) {
        value = null
        value = withContext(Dispatchers.IO) { ProfileHoldings.read(context) }
    }
    val downloads = remember(context) { DownloadRepository.getInstance(context) }
    val downloadedSongs by downloads.downloadedSongs.collectAsState()
    val downloadedVideos by downloads.downloadedVideos.collectAsState()
    val savedPlaylists by remember(context) { SavedPlaylistsRepository(context) }
        .savedPlaylists.collectAsState()

    var showProfilesSheet by remember { mutableStateOf(false) }

    SettingsDetailScaffold(title = stringResource(R.string.settings_account), onBack = onBack) {
        if (active != null) {
            item {
                AccountHero(
                    profile = active,
                    card = cards[active.id] ?: KodaProfileCard(),
                    accountAvatarUrl = if (active.isLocal) null else sessionManager.getUserAvatar(),
                    needsSignIn = active.id in needSignIn,
                    onReconnect = onReconnectProfile
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.ac_section_profile_data)) {
                AccountStatGrid(
                    listOf(
                        AccountStat(Icons.Rounded.Favorite, holdings?.likedSongs, stringResource(R.string.kp_stat_liked)),
                        AccountStat(Icons.Rounded.MusicNote, holdings?.songsPlayed, stringResource(R.string.kp_stat_plays)),
                        AccountStat(Icons.Rounded.SmartDisplay, holdings?.videosWatched, stringResource(R.string.ac_stat_videos)),
                        AccountStat(Icons.Rounded.Search, holdings?.searches, stringResource(R.string.ac_stat_searches)),
                        AccountStat(Icons.Rounded.Subscriptions, holdings?.deviceSubscriptions, stringResource(R.string.ac_stat_subs)),
                        AccountStat(Icons.Rounded.VisibilityOff, holdings?.hiddenChannels, stringResource(R.string.ac_stat_hidden)),
                    )
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.ac_section_device_data)) {
                AccountStatGrid(
                    listOf(
                        AccountStat(Icons.AutoMirrored.Rounded.QueueMusic, savedPlaylists.size, stringResource(R.string.ac_stat_saved_playlists)),
                        AccountStat(Icons.Rounded.Download, downloadedSongs.size, stringResource(R.string.ac_stat_dl_songs)),
                        AccountStat(Icons.Rounded.VideoLibrary, downloadedVideos.size, stringResource(R.string.ac_stat_dl_videos)),
                    )
                )
                SettingsFootnote(
                    icon = Icons.Rounded.PhoneAndroid,
                    text = stringResource(R.string.ac_data_note)
                )
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.profiles_title)) {
                SettingsCard {
                    profiles.forEach { profile ->
                        val broken = profile.expired || profile.id in needSignIn
                        AccountProfileRow(
                            profile = profile,
                            card = cards[profile.id] ?: KodaProfileCard(),
                            isActive = profile.id == activeId,
                            needsSignIn = profile.id in needSignIn,
                            onClick = {
                                // Switch first, so a reconnect stores its
                                // session against this profile and not the
                                // one that was active.
                                if (profile.id != activeId) switcher.switchTo(profile.id)
                                if (broken) onReconnectProfile()
                            }
                        )
                    }
                    SettingsRow(
                        icon = Icons.Rounded.ManageAccounts,
                        title = stringResource(R.string.ac_manage_profiles),
                        subtitle = stringResource(R.string.ac_manage_profiles_sub),
                        onClick = { showProfilesSheet = true },
                        showChevron = true
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.ac_section_privacy)) {
                SettingsCard {
                    SettingsToggleRow(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.incognito_title),
                        subtitle = stringResource(R.string.incognito_subtitle),
                        enabled = incognito,
                        onToggle = { IncognitoMode.setEnabled(context, it) }
                    )
                    if (signedIn) {
                        SettingsToggleRow(
                            icon = Icons.Rounded.CheckCircle,
                            title = stringResource(R.string.sp_save_watch_history),
                            subtitle = if (saveVideoHistory) {
                                "Videos you watch are added to your YouTube history"
                            } else {
                                "Watching does not touch your YouTube history"
                            },
                            enabled = saveVideoHistory,
                            onToggle = onSaveVideoHistoryToggle,
                            explanation = stringResource(R.string.si_watch_history_account)
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.settings_section_youtube_music)) {
                SettingsCard {
                    if (signedIn) {
                        SettingsRow(
                            icon = Icons.Rounded.Cookie,
                            title = stringResource(R.string.sp_replace_session_cookies),
                            subtitle = stringResource(R.string.sp_replace_session_cookies_sub),
                            onClick = onShowCookieSheet,
                            showChevron = true
                        )
                        SettingsRow(
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            title = stringResource(R.string.sign_out),
                            subtitle = stringResource(R.string.sp_sign_out_sub),
                            onClick = onSignOut,
                            tint = SettingsRowDefaults.destructiveTint,
                            titleColor = SettingsRowDefaults.destructiveTint
                        )
                    } else {
                        SettingsRow(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.sp_connect_youtube_music),
                            subtitle = stringResource(R.string.sp_connect_youtube_music_sub),
                            onClick = onShowAuthDialog,
                            showChevron = true
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Cookie,
                            title = stringResource(R.string.sp_sign_in_cookies),
                            subtitle = stringResource(R.string.sp_sign_in_cookies_sub),
                            onClick = onShowCookieSheet,
                            showChevron = true
                        )
                    }
                }
                // Signed out is a supported state, not an error - say what
                // signing in buys rather than nagging.
                if (!signedIn) {
                    SettingsFootnote(
                        icon = Icons.Rounded.CheckCircle,
                        text = stringResource(R.string.sp_signed_out_info)
                    )
                }
            }
        }

        item {
            SettingsSection(title = stringResource(R.string.ac_section_more)) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Backup,
                        title = stringResource(R.string.settings_backup_and_restore),
                        subtitle = stringResource(R.string.ac_backup_sub),
                        onClick = { onOpenPage(SettingsPage.BACKUP) },
                        showChevron = true
                    )
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.lastfm_title),
                        subtitle = stringResource(R.string.ac_lastfm_sub),
                        onClick = { onOpenPage(SettingsPage.LASTFM) },
                        showChevron = true
                    )
                    SettingsRow(
                        icon = Icons.Rounded.Subscriptions,
                        title = stringResource(R.string.settings_subscriptions),
                        subtitle = stringResource(R.string.ac_subscriptions_sub),
                        onClick = { onOpenPage(SettingsPage.SUBSCRIPTIONS) },
                        showChevron = true
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showProfilesSheet) {
        AccountSwitcherSheet(
            onDismiss = { showProfilesSheet = false },
            onAddYouTubeAccount = onAddYouTubeAccount,
            onReconnectProfile = onReconnectProfile
        )
    }
}

/**
 * How much the active profile holds, read once per profile off the main thread.
 *
 * Counts only, and only from stores already on the device: nothing here asks
 * YouTube anything, so the page is the same offline and never spends a request
 * to draw a number. That is also why account subscriptions are not counted -
 * they live with YouTube, and the one honest number available is the device's.
 */
private data class ProfileHoldings(
    val likedSongs: Int,
    val songsPlayed: Int,
    val videosWatched: Int,
    val searches: Int,
    val deviceSubscriptions: Int,
    val hiddenChannels: Int,
) {
    companion object {
        suspend fun read(context: android.content.Context) = ProfileHoldings(
            likedSongs = LikedSongsRepository(context).getAllLikedSongIds().size,
            songsPlayed = StatsRepository(context).loadHistory().size,
            videosWatched = VideoHistoryRepository(context).getHistory().size,
            searches = SearchHistoryRepository(context).getHistory().size,
            deviceSubscriptions = LocalSubscriptionsRepository(context).subscriptions.value.size,
            hiddenChannels = NotInterestedRepository(context).blockedChannels.value.size,
        )
    }
}

/** Null [value] is "still counting", drawn as a dash rather than a zero that then changes. */
private data class AccountStat(val icon: ImageVector, val value: Int?, val label: String)

/**
 * The active profile, large: its picture, its name, what kind of profile it
 * is, and whether its session is good. A broken session gets its fix on the
 * card, because an expired account otherwise shows up only as empty screens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountHero(
    profile: Profile,
    card: KodaProfileCard,
    accountAvatarUrl: String?,
    needsSignIn: Boolean,
    onReconnect: () -> Unit
) {
    val broken = profile.expired || needsSignIn
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    profile = profile,
                    card = card,
                    modifier = Modifier.size(72.dp),
                    accountAvatarUrl = accountAvatarUrl ?: profile.avatarUrl
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            profile.isLocal -> stringResource(R.string.profile_on_this_device)
                            else -> profile.handle ?: stringResource(R.string.profile_youtube_account)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    AccountStatusPill(
                        icon = when {
                            broken -> Icons.Rounded.ErrorOutline
                            profile.isLocal -> Icons.Rounded.PhoneAndroid
                            else -> Icons.Rounded.CheckCircle
                        },
                        text = stringResource(
                            when {
                                needsSignIn -> R.string.ac_status_signed_out
                                profile.expired -> R.string.ac_status_expired
                                profile.isLocal -> R.string.ac_status_device
                                else -> R.string.sp_connected
                            }
                        ),
                        container = if (broken) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        content = if (broken) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            if (broken) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = onReconnect,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ac_reconnect), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AccountStatusPill(icon: ImageVector, text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Two tiles to a row, equal height within a row whatever their labels wrap to. */
@Composable
private fun AccountStatGrid(stats: List<AccountStat>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { stat ->
                    AccountStatTile(
                        stat = stat,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccountStatTile(stat: AccountStat, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = stat.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stat.value?.let { "%,d".format(it) } ?: "-",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = stat.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** One profile in the roster: tap to become it, or to sign it back in when its session is gone. */
@Composable
private fun AccountProfileRow(
    profile: Profile,
    card: KodaProfileCard,
    isActive: Boolean,
    needsSignIn: Boolean,
    onClick: () -> Unit
) {
    val flagged = profile.expired || needsSignIn
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "accountProfileRowScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(profile = profile, card = card, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    // Order matters: a restored profile is also flagged
                    // expired, and "signed out" is the accurate half of that.
                    needsSignIn -> stringResource(R.string.profile_signed_out)
                    profile.expired -> stringResource(R.string.profile_session_expired)
                    profile.isLocal -> stringResource(R.string.profile_on_this_device)
                    else -> profile.handle ?: stringResource(R.string.profile_youtube_account)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (flagged) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isActive) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.cd_active_profile),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
