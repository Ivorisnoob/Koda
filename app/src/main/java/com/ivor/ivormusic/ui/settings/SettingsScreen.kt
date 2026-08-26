package com.ivor.ivormusic.ui.settings
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import android.content.Intent
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.ivor.ivormusic.BuildConfig
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ivor.ivormusic.data.DownloadNotificationHelper
import com.ivor.ivormusic.data.FolderInfo
import com.ivor.ivormusic.data.BackupRepository
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.data.YouTubeAuthUtils

import com.ivor.ivormusic.ui.auth.YouTubeAuthDialog
import com.ivor.ivormusic.ui.components.coveredBy
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Helper to convert Expressive Polygons to a Compose Shape
// Based on official Android Shapes snippets
private class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val matrix = androidx.compose.ui.graphics.Matrix()
        
        // Calculate bounds of the polygon
        // calculateBounds() returns float array [left, top, right, bottom]
        val bounds = polygon.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]
        
        // Android Compose Matrix operations are applied in reverse order to the point
        // We want: Scale * Translate * Point
        // So we call scale() then translate()
        
        // Scale to fit component size
        // We scale width/boundsWidth and height/boundsHeight to stretch/fit exactly
        val scaleX = size.width / boundsWidth
        val scaleY = size.height / boundsHeight
        matrix.scale(scaleX, scaleY)
        
        // Translate to origin (0,0) based on bounds top-left
        matrix.translate(-bounds[0], -bounds[1])
        
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * One page of the settings screen. These are not nav routes on purpose: the
 * screen takes ~60 parameters, and threading those into a NavHost destination
 * per category would mean repeating the whole list eleven times. The Home tab
 * system solves the same problem the same way.
 */
internal enum class SettingsPage {
    HUB,
    ACCOUNT,
    APPEARANCE,
    PLAYER,
    PLAYBACK,
    CONTENT,
    SUBSCRIPTIONS,
    STORAGE,
    NOTIFICATIONS,
    LOCAL_LIBRARY,
    ADVANCED
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    amoledTheme: Boolean,
    onAmoledThemeToggle: (Boolean) -> Unit,
    colorPalette: String = ThemePreferences.DEFAULT_COLOR_PALETTE,
    onNavigateToColorPalette: () -> Unit = {},
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    playerArtworkColors: Boolean = true,
    onPlayerArtworkColorsToggle: (Boolean) -> Unit = {},
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit,
    homeModeToggleEnabled: Boolean = true,
    onHomeModeToggleChange: (Boolean) -> Unit = {},
    spotlightHome: Boolean = false,
    onSpotlightHomeToggle: (Boolean) -> Unit = {},
    nonExpressiveNavigationBar: Boolean = false,
    onNonExpressiveNavigationBarToggle: (Boolean) -> Unit = {},
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    saveVideoHistory: Boolean,
    onSaveVideoHistoryToggle: (Boolean) -> Unit,
    saveMusicHistory: Boolean,
    onSaveMusicHistoryToggle: (Boolean) -> Unit,
    liveDownloadUpdates: Boolean,
    onLiveDownloadUpdatesToggle: (Boolean) -> Unit,
    livePlaybackUpdates: Boolean,
    onLivePlaybackUpdatesToggle: (Boolean) -> Unit,
    timedCommentsEnabled: Boolean,
    onTimedCommentsToggle: (Boolean) -> Unit,
    shortsEnabled: Boolean,
    onShortsEnabledToggle: (Boolean) -> Unit,
    shortsHiddenActions: Set<String> = emptySet(),
    onShortsHiddenActionsChange: (Set<String>) -> Unit = {},
    videoQualityWifi: String,
    onVideoQualityWifiChange: (String) -> Unit,
    videoQualityMobile: String,
    onVideoQualityMobileChange: (String) -> Unit,
    musicQualityWifi: String,
    onMusicQualityWifiChange: (String) -> Unit,
    musicQualityMobile: String,
    onMusicQualityMobileChange: (String) -> Unit,
    subscriptionSource: String = ThemePreferences.SUBSCRIPTIONS_AUTO,
    onSubscriptionSourceChange: (String) -> Unit = {},
    subscribeTarget: String = ThemePreferences.SUBSCRIPTIONS_AUTO,
    onSubscribeTargetChange: (String) -> Unit = {},
    fastSubscriptionFeed: Boolean = true,
    onFastSubscriptionFeedToggle: (Boolean) -> Unit = {},
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToNotInterested: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToReportBug: () -> Unit = {},
    onNavigateToTimeLimit: () -> Unit = {},
    excludedFolders: Set<String>,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    homeViewModel: com.ivor.ivormusic.ui.home.HomeViewModel,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
    cacheEnabled: Boolean,
    onCacheEnabledToggle: (Boolean) -> Unit,
    maxCacheSizeMb: Long,
    onMaxCacheSizeMbChange: (Long) -> Unit,
    currentCacheSize: Long,
    onClearCacheClick: () -> Unit,
    autoLoadQueue: Boolean,
    onAutoLoadQueueToggle: (Boolean) -> Unit,
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
    uploadNotificationsEnabled: Boolean,
    onUploadNotificationsToggle: (Boolean) -> Unit,
    oemFixEnabled: Boolean,
    onOemFixEnabledToggle: (Boolean) -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit,
    privateDownloadsEnabled: Boolean,
    onPrivateDownloadsEnabledToggle: (Boolean) -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    localOnlyMode: Boolean = false,
    onLocalOnlyModeToggle: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // The upload-notification channel list reads the stores directly (no DI
    // house style): the mute toggles write through the same instances.
    val localSubscriptionsRepository = remember {
        com.ivor.ivormusic.data.LocalSubscriptionsRepository(context)
    }
    val uploadCheckRepository = remember {
        com.ivor.ivormusic.data.UploadCheckRepository(context)
    }
    val followedChannels by localSubscriptionsRepository.subscriptions.collectAsState()
    val mutedChannelIds by uploadCheckRepository.mutedChannelIds.collectAsState()

    // Check actual login status
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }

    // Whether the OS currently lets Koda post promoted (Live Update)
    // notifications. Re-read on resume, because the only way to change it is to
    // leave for system settings and come back.
    val notificationHelper = remember { DownloadNotificationHelper(context) }
    var canPostPromoted by remember { mutableStateOf(notificationHelper.canPostLiveUpdates()) }
    val settingsActivity = context as? androidx.activity.ComponentActivity
    DisposableEffect(settingsActivity) {
        val lifecycle = settingsActivity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canPostPromoted = notificationHelper.canPostLiveUpdates()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    // Which category page is open, and what is typed in the hub's search box.
    var page by remember { mutableStateOf(SettingsPage.HUB) }
    var searchQuery by remember { mutableStateOf("") }

    /**
     * Back unwinds one step at a time: an open page returns to the hub, then a
     * live query clears, and only then does Settings close. The query survives
     * opening a result on purpose, so coming back lands on the same list.
     *
     * The first of those steps is previewed while the finger is still down.
     * The manifest has opted into the modern back API for a long time, so a
     * plain `BackHandler` here was not neutral: it swallowed the gesture and
     * left the platform with nothing to draw, which is worse than never having
     * opted in.
     *
     * `peel` runs 0..2 and is deliberately one continuous value rather than a
     * preview animation plus a separate exit. 0..1 is the drag, 1..2 carries
     * the page the rest of the way off after the user commits, so releasing
     * continues the movement instead of snapping home to replay it.
     *
     * Clearing the query is not previewed. Nothing leaves the screen for that
     * step - the hub restates itself - so there is nothing to draw behind, and
     * a page-shaped animation over a list that is staying put would be a lie
     * about what is happening.
     */
    val peel = remember { Animatable(0f) }
    var isPeeling by remember { mutableStateOf(false) }
    var peelCommitted by remember { mutableStateOf(false) }
    // +1 when the finger came from the left edge, so the page leaves the way
    // the hand is already moving. Physical, so it needs no RTL adjustment.
    var peelSign by remember { mutableFloatStateOf(1f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = page != SettingsPage.HUB || searchQuery.isNotEmpty()) { events ->
        val hasPage = page != SettingsPage.HUB
        var dragged = false
        try {
            events.collect { event ->
                if (!hasPage) return@collect
                dragged = true
                isPeeling = true
                peelSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                peel.snapTo(event.progress.coerceIn(0f, 1f))
            }
            when {
                // Committed a real drag: finish the move it started.
                hasPage && dragged -> {
                    peelCommitted = true
                    peel.animateTo(
                        targetValue = 2f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                    page = SettingsPage.HUB
                }
                // A button press, or three-button navigation: no gesture to
                // continue from, so the ordinary transition is still the right
                // one and the peel stays out of it entirely.
                hasPage -> page = SettingsPage.HUB
                else -> searchQuery = ""
            }
        } catch (cancelled: CancellationException) {
            // The user changed their mind, and this is the case the feature
            // lives or dies on: a preview of leaving that does not come back
            // cleanly is worse than no preview at all.
            //
            // The spring has to be launched from the screen's own scope. This
            // coroutine is the one the system has just cancelled, so an
            // animation started here would be dead on arrival and the page
            // would stay stranded mid-peel.
            // Cleared here as well as after a swap: a second gesture can start
            // while the first is still finishing, and a commit flag left set on
            // a page that came back would make the next ordinary back vanish it
            // with no animation at all.
            peelCommitted = false
            if (dragged) {
                coroutineScope.launch {
                    peel.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    isPeeling = false
                }
            } else {
                isPeeling = false
            }
        }
    }

    // Put the peel away once the page layer has gone, ready for the next
    // gesture.
    //
    // The flags come down before the value, and that order is the whole point:
    // `snapTo` suspends, so clearing `isPeeling` after it leaves a frame where
    // the peel reads 0 while the hub is still wearing its peel layer, and the
    // hub jumps to 0.94 scale for exactly one frame. With the flag cleared
    // first there is no layer left for the value to feed.
    LaunchedEffect(page) {
        if (page == SettingsPage.HUB && peelCommitted) {
            peelCommitted = false
            isPeeling = false
            peel.snapTo(0f)
        }
    }

    // Dialog state for YouTube auth
    var showAuthDialog by remember { mutableStateOf(false) }

    // Dialog state for pasting a session cookie header by hand
    var showCookiePasteSheet by remember { mutableStateOf(false) }
    // Bumped after a session change so the account row re-reads name/avatar,
    // which SessionManager exposes as plain getters rather than a flow.
    var accountRefreshKey by remember { mutableStateOf(0) }

    // Dialog state for About
    var showAboutDialog by remember { mutableStateOf(false) }

    // Which per-network quality picker is open, if any
    var qualityDialogTarget by remember { mutableStateOf<QualityDialogTarget?>(null) }

    // Which subscription-routing picker is open, if any
    var subscriptionDialogTarget by remember { mutableStateOf<SubscriptionDialogTarget?>(null) }

    // Dialog state for Folder Exclusion
    var showFolderExclusionDialog by remember { mutableStateOf(false) }
    var showShortsButtonsDialog by remember { mutableStateOf(false) }
    var showAutoHelpDialog by remember { mutableStateOf(false) }
    var availableFolders by remember { mutableStateOf<List<FolderInfo>>(emptyList()) }
    var isFoldersLoading by remember { mutableStateOf(false) }

    val openFolderExclusion: () -> Unit = {
        // Load available folders when opening dialog
        isFoldersLoading = true
        coroutineScope.launch {
            availableFolders = homeViewModel.getAvailableFolders()
            isFoldersLoading = false
        }
        showFolderExclusionDialog = true
    }

    // Not remembered: the closures capture callbacks that arrive as parameters,
    // and ~38 small objects per recomposition is cheaper than a stale index.
    val searchEntries = buildSettingsSearchIndex(
        onOpenPage = { page = it },
        onOpenQualityPicker = { qualityDialogTarget = it },
        onOpenRoutingPicker = { subscriptionDialogTarget = it },
        onShowAbout = { showAboutDialog = true },
        onShowShortsButtons = { showShortsButtonsDialog = true },
        onOpenFolderExclusion = openFolderExclusion,
        onNavigateToColorPalette = onNavigateToColorPalette,
        onNavigateToSubscriptions = onNavigateToSubscriptions,
        onNavigateToNotInterested = onNavigateToNotInterested,
        onNavigateToBackup = onNavigateToBackup,
        onNavigateToReportBug = onNavigateToReportBug,
        onNavigateToTimeLimit = onNavigateToTimeLimit,
        supportsLiveUpdates = ThemePreferences.SUPPORTS_LIVE_UPDATES
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding)
            .onSizeChanged { containerWidth = it.width.toFloat() }
    ) {
        val dragFraction = peel.value.coerceIn(0f, 1f)
        val leavingFraction = (peel.value - 1f).coerceIn(0f, 1f)

        // The hub is composed underneath whatever page is open, rather than
        // being one branch of the AnimatedContent below. That is what makes a
        // preview possible at all - a page cannot peel away to reveal
        // something that is not composed - and it settles a smaller annoyance
        // on the way past: the hub's scroll position used to be discarded
        // every time a page was opened, because the hub left composition.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPeeling) {
                        Modifier.graphicsLayer {
                            // Comes forward as the page leaves, so the two read
                            // as one stack rather than two slides.
                            val scale = lerp(0.94f, 1f, dragFraction)
                            scaleX = scale
                            scaleY = scale
                            translationX =
                                -peelSign * containerWidth * 0.08f * (1f - dragFraction)
                        }
                    } else {
                        Modifier
                    }
                )
                // A composed hub behind a page is one whose rows are still
                // tappable through it and still read out by TalkBack.
                .coveredBy(page != SettingsPage.HUB)
        ) {
            SettingsHub(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchEntries = searchEntries,
                isLoggedIn = isLoggedIn,
                accountRefreshKey = accountRefreshKey,
                sessionManager = sessionManager,
                currentThemeMode = currentThemeMode,
                colorPalette = colorPalette,
                spotlightHome = spotlightHome,
                playerStyle = playerStyle,
                musicQualityWifi = musicQualityWifi,
                videoQualityWifi = videoQualityWifi,
                localOnlyMode = localOnlyMode,
                videoMode = videoMode,
                subscriptionSource = subscriptionSource,
                privateDownloadsEnabled = privateDownloadsEnabled,
                cacheEnabled = cacheEnabled,
                currentCacheSize = currentCacheSize,
                liveDownloadUpdates = liveDownloadUpdates,
                livePlaybackUpdates = livePlaybackUpdates,
                canPostPromoted = canPostPromoted,
                loadLocalSongs = loadLocalSongs,
                excludedFolderCount = excludedFolders.size,
                onOpenPage = { page = it },
                onNavigateToBackup = onNavigateToBackup,
                onShowAbout = { showAboutDialog = true },
                onBackClick = onBackClick
            )
        }

        // The open page, riding on top of the hub.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPeeling) {
                        Modifier.graphicsLayer {
                            val scale = lerp(1f, 0.90f, dragFraction)
                            scaleX = scale
                            scaleY = scale
                            translationX = peelSign * containerWidth *
                                (0.10f * dragFraction + 0.95f * leavingFraction)
                            alpha = 1f - leavingFraction
                            // Lifts off the hub as a card rather than a slab.
                            // Driven by a value the system clamps to 0..1, so
                            // it cannot undershoot into a negative corner.
                            shape = RoundedCornerShape(lerp(0f, 28f, dragFraction).dp)
                            clip = true
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val slide = spring<IntOffset>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                    val content = when {
                        // The finger already performed this exit. Animating it
                        // again would snap the page back to full size to replay
                        // the move it just made.
                        peelCommitted -> EnterTransition.None togetherWith ExitTransition.None
                        // Going deeper slides in from the trailing edge; coming
                        // back reverses it, so the hierarchy stays legible.
                        targetState != SettingsPage.HUB ->
                            (slideInHorizontally(slide) { it / 5 } + fadeIn(tween(200))) togetherWith
                                (fadeOut(tween(130)) + slideOutHorizontally(slide) { -it / 12 })
                        // Returning by button. The page now carries the whole
                        // movement, where it used to share it with a hub that
                        // slid in from the leading edge - the hub is underneath
                        // and staying put, so a token 1/12 nudge would read as
                        // the page dissolving rather than leaving.
                        else -> fadeIn(tween(200)) togetherWith
                            (fadeOut(tween(180)) + slideOutHorizontally(slide) { it / 3 })
                    }
                    // No size animation, and no clipping to one. The hub state
                    // of this layer is empty, so the default SizeTransform
                    // animates the container between nothing and full screen
                    // and clips the page to it on the way - which is a page
                    // unfolding out of a growing rectangle rather than sliding
                    // in, and a clipped snap on the way out.
                    content using SizeTransform(clip = false) { _, _ -> snap() }
                },
                label = "settingsPage"
            ) { currentPage ->
                when (currentPage) {
                    // Nothing on top: the hub below is the screen. Full size
                    // rather than empty so this layer measures the same in
                    // both states, and takes no touches so the hub underneath
                    // still gets them.
                    SettingsPage.HUB -> Spacer(Modifier.fillMaxSize())

                    SettingsPage.ACCOUNT -> AccountSettingsPage(
                    isLoggedIn = isLoggedIn,
                    accountRefreshKey = accountRefreshKey,
                    sessionManager = sessionManager,
                    saveVideoHistory = saveVideoHistory,
                    onSaveVideoHistoryToggle = onSaveVideoHistoryToggle,
                    onShowAuthDialog = { showAuthDialog = true },
                    onShowCookieSheet = { showCookiePasteSheet = true },
                    onSignOut = {
                        sessionManager.clearSession()
                        isLoggedIn = false
                        onLogoutClick()
                    },
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.APPEARANCE -> AppearanceSettingsPage(
                    currentThemeMode = currentThemeMode,
                    onThemeModeChange = onThemeModeChange,
                    colorPalette = colorPalette,
                    onNavigateToColorPalette = onNavigateToColorPalette,
                    amoledTheme = amoledTheme,
                    onAmoledThemeToggle = onAmoledThemeToggle,
                    ambientBackground = ambientBackground,
                    onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                    spotlightHome = spotlightHome,
                    onSpotlightHomeToggle = onSpotlightHomeToggle,
                    nonExpressiveNavigationBar = nonExpressiveNavigationBar,
                    onNonExpressiveNavigationBarToggle =
                        onNonExpressiveNavigationBarToggle,
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.PLAYER -> PlayerSettingsPage(
                    playerStyle = playerStyle,
                    onPlayerStyleChange = onPlayerStyleChange,
                    playerArtworkColors = playerArtworkColors,
                    onPlayerArtworkColorsToggle = onPlayerArtworkColorsToggle,
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.PLAYBACK -> PlaybackSettingsPage(
                    crossfadeEnabled = crossfadeEnabled,
                    onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                    crossfadeAuto = crossfadeAuto,
                    onCrossfadeAutoChange = onCrossfadeAutoChange,
                    crossfadeDurationMs = crossfadeDurationMs,
                    onCrossfadeDurationChange = onCrossfadeDurationChange,
                    normalizeVolume = normalizeVolume,
                    onNormalizeVolumeToggle = onNormalizeVolumeToggle,
                    rememberVideoBrightness = rememberVideoBrightness,
                    onRememberVideoBrightnessToggle = onRememberVideoBrightnessToggle,
                    hapticsLevel = hapticsLevel,
                    onHapticsLevelChange = onHapticsLevelChange,
                    autoLoadQueue = autoLoadQueue,
                    onAutoLoadQueueToggle = onAutoLoadQueueToggle,
                    saveMusicHistory = saveMusicHistory,
                    onSaveMusicHistoryToggle = onSaveMusicHistoryToggle,
                    musicQualityWifi = musicQualityWifi,
                    musicQualityMobile = musicQualityMobile,
                    videoQualityWifi = videoQualityWifi,
                    videoQualityMobile = videoQualityMobile,
                    onOpenQualityPicker = { qualityDialogTarget = it },
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.CONTENT -> ContentSettingsPage(
                    localOnlyMode = localOnlyMode,
                    onLocalOnlyModeToggle = onLocalOnlyModeToggle,
                    videoMode = videoMode,
                    onVideoModeToggle = onVideoModeToggle,
                    homeModeToggleEnabled = homeModeToggleEnabled,
                    onHomeModeToggleChange = onHomeModeToggleChange,
                    timedCommentsEnabled = timedCommentsEnabled,
                    onTimedCommentsToggle = onTimedCommentsToggle,
                    shortsEnabled = shortsEnabled,
                    onShortsEnabledToggle = onShortsEnabledToggle,
                    shortsHiddenActions = shortsHiddenActions,
                    onShowShortsButtons = { showShortsButtonsDialog = true },
                    onNavigateToNotInterested = onNavigateToNotInterested,
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.SUBSCRIPTIONS -> SubscriptionsSettingsPage(
                    subscriptionSource = subscriptionSource,
                    subscribeTarget = subscribeTarget,
                    fastSubscriptionFeed = fastSubscriptionFeed,
                    onFastSubscriptionFeedToggle = onFastSubscriptionFeedToggle,
                    onNavigateToSubscriptions = onNavigateToSubscriptions,
                    onOpenRoutingPicker = { subscriptionDialogTarget = it },
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.STORAGE -> StorageSettingsPage(
                    privateDownloadsEnabled = privateDownloadsEnabled,
                    onPrivateDownloadsEnabledToggle = onPrivateDownloadsEnabledToggle,
                    cacheEnabled = cacheEnabled,
                    onCacheEnabledToggle = onCacheEnabledToggle,
                    maxCacheSizeMb = maxCacheSizeMb,
                    onMaxCacheSizeMbChange = onMaxCacheSizeMbChange,
                    currentCacheSize = currentCacheSize,
                    onClearCacheClick = onClearCacheClick,
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.NOTIFICATIONS -> NotificationsSettingsPage(
                    liveDownloadUpdates = liveDownloadUpdates,
                    onLiveDownloadUpdatesToggle = onLiveDownloadUpdatesToggle,
                    livePlaybackUpdates = livePlaybackUpdates,
                    onLivePlaybackUpdatesToggle = onLivePlaybackUpdatesToggle,
                    canPostPromoted = canPostPromoted,
                    uploadNotificationsEnabled = uploadNotificationsEnabled,
                    onUploadNotificationsToggle = onUploadNotificationsToggle,
                    followedChannels = followedChannels,
                    mutedChannelIds = mutedChannelIds,
                    onChannelMutedChange = { channelId, muted ->
                        uploadCheckRepository.setMuted(channelId, muted)
                    },
                    onOpenSystemSettings = {
                        notificationHelper
                            .promotedNotificationSettingsIntent()
                            ?.let { runCatching { context.startActivity(it) } }
                    },
                    onBack = { page = SettingsPage.HUB }
                )

                SettingsPage.LOCAL_LIBRARY -> LocalLibrarySettingsPage(
                    loadLocalSongs = loadLocalSongs,
                    onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                    excludedFolderCount = excludedFolders.size,
                    onOpenFolderExclusion = openFolderExclusion,
                    onBack = { page = SettingsPage.HUB }
                )

                    SettingsPage.ADVANCED -> AdvancedSettingsPage(
                        manualScanEnabled = manualScanEnabled,
                        onManualScanEnabledToggle = onManualScanEnabledToggle,
                        onReportBug = onNavigateToReportBug,
                        onOpenTimeLimit = onNavigateToTimeLimit,
                        onOpenAutoHelp = { showAutoHelpDialog = true },
                        onBack = { page = SettingsPage.HUB }
                    )
                }
            }
        }
    }

    // YouTube Auth Dialog
    if (showAuthDialog) {
        YouTubeAuthDialog(
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = {
                showAuthDialog = false
                isLoggedIn = true
            }
        )
    }

    // Manual cookie paste (escape hatch when the WebView login fails, or to
    // refresh a session that has gone stale without signing in again)
    if (showCookiePasteSheet) {
        CookiePasteSheet(
            onDismiss = { showCookiePasteSheet = false },
            onSave = { cookies ->
                sessionManager.startSession(cookies)
                isLoggedIn = true
                showCookiePasteSheet = false
                coroutineScope.launch {
                    // Populates name/avatar for the account row, and doubles as
                    // a live check that the pasted session is actually accepted.
                    com.ivor.ivormusic.data.YouTubeRepository(context).fetchAccountInfo()
                    accountRefreshKey++
                }
            }
        )
    }

    // About Dialog with expressive styling
    if (showAboutDialog) {
        ExpressiveAboutDialog(
            onDismiss = { showAboutDialog = false },
            onNavigateToUpdate = onNavigateToUpdate
        )
    }

    // Per-network stream quality pickers (video + music, Wi-Fi + mobile data)
    qualityDialogTarget?.let { target ->
        val currentQuality = when (target) {
            QualityDialogTarget.VIDEO_WIFI -> videoQualityWifi
            QualityDialogTarget.VIDEO_MOBILE -> videoQualityMobile
            QualityDialogTarget.MUSIC_WIFI -> musicQualityWifi
            QualityDialogTarget.MUSIC_MOBILE -> musicQualityMobile
        }
        val onQualitySelected: (String) -> Unit = when (target) {
            QualityDialogTarget.VIDEO_WIFI -> onVideoQualityWifiChange
            QualityDialogTarget.VIDEO_MOBILE -> onVideoQualityMobileChange
            QualityDialogTarget.MUSIC_WIFI -> onMusicQualityWifiChange
            QualityDialogTarget.MUSIC_MOBILE -> onMusicQualityMobileChange
        }
        StreamQualityDialog(
            target = target,
            currentQuality = currentQuality,
            onQualitySelected = {
                onQualitySelected(it)
                qualityDialogTarget = null
            },
            onDismiss = { qualityDialogTarget = null }
        )
    }

    // Where subscriptions are read from / written to
    subscriptionDialogTarget?.let { target ->
        val current = when (target) {
            SubscriptionDialogTarget.SOURCE -> subscriptionSource
            SubscriptionDialogTarget.TARGET -> subscribeTarget
        }
        val onSelected: (String) -> Unit = when (target) {
            SubscriptionDialogTarget.SOURCE -> onSubscriptionSourceChange
            SubscriptionDialogTarget.TARGET -> onSubscribeTargetChange
        }
        SubscriptionRoutingDialog(
            target = target,
            currentValue = current,
            onValueSelected = {
                onSelected(it)
                subscriptionDialogTarget = null
            },
            onDismiss = { subscriptionDialogTarget = null }
        )
    }

    // Folder Exclusion Dialog
    if (showFolderExclusionDialog) {
        FolderExclusionDialog(
            availableFolders = availableFolders,
            excludedFolders = excludedFolders,
            isLoading = isFoldersLoading,
            onAddExcludedFolder = onAddExcludedFolder,
            onRemoveExcludedFolder = onRemoveExcludedFolder,
            onDismiss = { showFolderExclusionDialog = false }
        )
    }

    if (showShortsButtonsDialog) {
        ShortsButtonsDialog(
            hiddenActions = shortsHiddenActions,
            onHiddenActionsChange = onShortsHiddenActionsChange,
            onDismiss = { showShortsButtonsDialog = false }
        )
    }

    // Android Auto help dialog
    if (showAutoHelpDialog) {
        AndroidAutoHelpDialog(
            onDismiss = { showAutoHelpDialog = false }
        )
    }
}

/**
 * The category list. Every row carries the live value of what is inside it, so
 * the common "what is this set to?" question is answered without opening
 * anything.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsHub(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchEntries: List<SettingsSearchEntry>,
    isLoggedIn: Boolean,
    accountRefreshKey: Int,
    sessionManager: SessionManager,
    currentThemeMode: ThemeMode,
    colorPalette: String,
    spotlightHome: Boolean,
    playerStyle: PlayerStyle,
    musicQualityWifi: String,
    videoQualityWifi: String,
    localOnlyMode: Boolean,
    videoMode: Boolean,
    subscriptionSource: String,
    privateDownloadsEnabled: Boolean,
    cacheEnabled: Boolean,
    currentCacheSize: Long,
    liveDownloadUpdates: Boolean,
    livePlaybackUpdates: Boolean,
    canPostPromoted: Boolean,
    loadLocalSongs: Boolean,
    excludedFolderCount: Int,
    onOpenPage: (SettingsPage) -> Unit,
    onNavigateToBackup: () -> Unit,
    onShowAbout: () -> Unit,
    onBackClick: () -> Unit
) {
    // Animation states for staggered entry
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Opening a result should put the keyboard away before the page slides in.
    val focusManager = LocalFocusManager.current

    val accountValue = key(accountRefreshKey) {
        when {
            !isLoggedIn -> "Not signed in"
            else -> sessionManager.getUserName() ?: "Signed in"
        }
    }

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
    val playerStyleLabel = com.ivor.ivormusic.ui.player.playerStyleInfo(playerStyle).label

    val contentValue = when {
        localOnlyMode -> "Local only, offline"
        videoMode -> "Video mode"
        else -> "Music mode"
    }

    val storageValue = buildString {
        append(if (privateDownloadsEnabled) "Private downloads" else "Downloads/Koda")
        append(" · ")
        append(
            if (cacheEnabled) {
                "${com.ivor.ivormusic.data.CacheManager.formatSize(currentCacheSize)} cached"
            } else {
                "Caching off"
            }
        )
    }

    val notificationsValue = when {
        !canPostPromoted && (liveDownloadUpdates || livePlaybackUpdates) -> "Blocked by system"
        liveDownloadUpdates && livePlaybackUpdates -> "Downloads and playback"
        liveDownloadUpdates -> "Downloads only"
        livePlaybackUpdates -> "Playback only"
        else -> "Off"
    }

    // Read on entry rather than held in a flow: Navigation Compose disposes
    // this destination while the backup screen is on top, so returning from
    // one re-runs this and the row is current without any plumbing.
    val backupContext = LocalContext.current
    val backupValue = remember { lastBackupLine(BackupRepository.lastBackupAt(backupContext)) }

    val localLibraryValue = when {
        !loadLocalSongs -> "Off"
        excludedFolderCount > 0 -> "On, $excludedFolderCount folder${if (excludedFolderCount == 1) "" else "s"} excluded"
        else -> "On"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(400)) + slideInVertically(
                        initialOffsetY = { -it / 2 },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange
                )
            }

            val results = searchSettings(searchQuery, searchEntries)

            if (searchQuery.isNotBlank()) {
                if (results.isEmpty()) {
                    item { SettingsSearchEmptyState(query = searchQuery) }
                } else {
                    item {
                        SettingsCard {
                            results.forEachIndexed { index, entry ->
                                if (index > 0) SettingsDivider()
                                SettingsSearchResultRow(
                                    entry = entry,
                                    onClick = {
                                        focusManager.clearFocus()
                                        entry.action()
                                    }
                                )
                            }
                        }
                    }
                }
            } else {

            item {
                SettingsSection(title = stringResource(R.string.settings_section_you)) {
                    SettingsCard {
                        SettingsHubRow(
                            icon = Icons.Rounded.AccountCircle,
                            title = stringResource(R.string.settings_account),
                            value = accountValue,
                            onClick = { onOpenPage(SettingsPage.ACCOUNT) }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_look_and_feel)) {
                    SettingsCard {
                        SettingsHubRow(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.settings_appearance),
                            value = if (spotlightHome) {
                                "$themeLabel, $paletteName, Spotlight"
                            } else {
                                "$themeLabel, $paletteName"
                            },
                            onClick = { onOpenPage(SettingsPage.APPEARANCE) }
                        )
                        SettingsDivider()
                        SettingsHubRow(
                            icon = Icons.Rounded.PlayCircle,
                            title = stringResource(R.string.settings_player),
                            value = playerStyleLabel,
                            onClick = { onOpenPage(SettingsPage.PLAYER) }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_playback_and_content)) {
                    SettingsCard {
                        SettingsHubRow(
                            icon = Icons.Rounded.GraphicEq,
                            title = stringResource(R.string.settings_playback_and_quality),
                            value = "${musicQualityLabel(musicQualityWifi)} music, " +
                                "${videoQualityLabel(videoQualityWifi)} video on Wi-Fi",
                            onClick = { onOpenPage(SettingsPage.PLAYBACK) }
                        )
                        SettingsDivider()
                        SettingsHubRow(
                            icon = Icons.Rounded.VideoLibrary,
                            title = stringResource(R.string.settings_content_and_feeds),
                            value = contentValue,
                            onClick = { onOpenPage(SettingsPage.CONTENT) }
                        )
                        SettingsDivider()
                        SettingsHubRow(
                            icon = Icons.Rounded.Subscriptions,
                            title = stringResource(R.string.settings_subscriptions),
                            value = subscriptionSourceLabel(subscriptionSource),
                            onClick = { onOpenPage(SettingsPage.SUBSCRIPTIONS) }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_system)) {
                    SettingsCard {
                        SettingsHubRow(
                            icon = Icons.Rounded.Folder,
                            title = stringResource(R.string.settings_storage_and_cache),
                            value = storageValue,
                            onClick = { onOpenPage(SettingsPage.STORAGE) }
                        )
                        if (ThemePreferences.SUPPORTS_LIVE_UPDATES) {
                            SettingsDivider()
                            SettingsHubRow(
                                icon = Icons.Rounded.Bolt,
                                title = stringResource(R.string.settings_notifications),
                                value = notificationsValue,
                                onClick = { onOpenPage(SettingsPage.NOTIFICATIONS) },
                                tint = if (!canPostPromoted && (liveDownloadUpdates || livePlaybackUpdates)) {
                                    SettingsRowDefaults.destructiveTint
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        SettingsDivider()
                        SettingsHubRow(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.settings_local_library),
                            value = localLibraryValue,
                            onClick = { onOpenPage(SettingsPage.LOCAL_LIBRARY) }
                        )
                        SettingsDivider()
                        SettingsHubRow(
                            icon = Icons.Rounded.SettingsBackupRestore,
                            title = stringResource(R.string.settings_backup_and_restore),
                            value = backupValue,
                            onClick = onNavigateToBackup
                        )
                        SettingsDivider()
                        SettingsHubRow(
                            icon = Icons.Rounded.Security,
                            title = stringResource(R.string.settings_advanced),
                            value = if (isXiaomiDevice()) {
                                stringResource(R.string.settings_advanced_value_xiaomi)
                            } else {
                                stringResource(R.string.settings_advanced_value_other)
                            },
                            onClick = { onOpenPage(SettingsPage.ADVANCED) },
                            tint = if (isXiaomiDevice()) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.app_name),
                            subtitle = stringResource(R.string.settings_version_subtitle, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            onClick = onShowAbout,
                            showChevron = true
                        )
                    }
                }
            }

            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveThemeSelectGroup(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    textColor: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.sp_theme),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            val options = ThemeMode.values()
            
            options.forEachIndexed { index, mode ->
                // Determine shape based on position
                val shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
                
                ToggleButton(
                    checked = currentMode == mode,
                    onCheckedChange = { onModeSelected(mode) },
                    modifier = Modifier.weight(1f),
                    shapes = shapes,
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = accentColor.copy(alpha = 0.1f),
                        checkedContainerColor = accentColor,
                        contentColor = textColor,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    // Content
                    Text(
                        text = when(mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        fontSize = 14.sp,
                        fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpressiveAccountItem(
    sessionManager: SessionManager,
    textColor: Color,
    secondaryTextColor: Color
) {
    val userAvatar = sessionManager.getUserAvatar()
    val userName = sessionManager.getUserName()
    val connectedColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Picture
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(connectedColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (!userAvatar.isNullOrEmpty()) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = stringResource(R.string.cd_profile_picture),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = connectedColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // User Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = userName ?: "YouTube Account",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = connectedColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.sp_connected),
                    color = connectedColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ExpressiveVideoModeToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val options = listOf(stringResource(R.string.sp_mode_music), stringResource(R.string.sp_mode_video))
    val selectedIndex = if (enabled) 1 else 0
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (enabled) Icons.Rounded.VideoLibrary else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sp_content_mode),
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) {
                        stringResource(R.string.sp_content_mode_video)
                    } else {
                        stringResource(R.string.sp_content_mode_music)
                    },
                    color = secondaryTextColor,
                    fontSize = 13.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                ButtonGroupDefaults.ConnectedSpaceBetween
            ),
        ) {
            options.forEachIndexed { index, label ->
                ToggleButton(
                    checked = selectedIndex == index,
                    onCheckedChange = { onToggle(index == 1) },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex ->
                            ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = accentColor.copy(alpha = 0.1f),
                        checkedContainerColor = accentColor,
                        contentColor = textColor,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = if (index == 0) Icons.Rounded.MusicNote
                        else Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(label)
                }
            }
        }
    }
}

/** Human-readable label for a stored default video quality value. */
internal fun videoQualityLabel(value: String): String =
    if (value == ThemePreferences.VIDEO_QUALITY_AUTO) "Auto (Highest)" else value

/** Human-readable label for a stored music quality value. */
internal fun musicQualityLabel(value: String): String = when (value) {
    ThemePreferences.MUSIC_QUALITY_LOW -> "Data Saver"
    ThemePreferences.MUSIC_QUALITY_NORMAL -> "Normal"
    else -> "High"
}

/** Longer picker-row label carrying the approximate bitrate. */
private fun musicQualityOptionLabel(value: String): String = when (value) {
    ThemePreferences.MUSIC_QUALITY_LOW -> "Data Saver (~48 kbps)"
    ThemePreferences.MUSIC_QUALITY_NORMAL -> "Normal (~128 kbps)"
    else -> "High (best available)"
}

/** Short label for the stored "which subscriptions to show" value. */
internal fun subscriptionSourceLabel(value: String): String = when (value) {
    ThemePreferences.SUBSCRIPTIONS_LOCAL -> "On this device"
    ThemePreferences.SUBSCRIPTIONS_YOUTUBE -> "YouTube account"
    else -> "Everything you follow"
}

/** Short label for the stored "where Subscribe writes" value. */
internal fun subscribeTargetLabel(value: String): String = when (value) {
    ThemePreferences.SUBSCRIPTIONS_LOCAL -> "This device only"
    ThemePreferences.SUBSCRIPTIONS_YOUTUBE -> "YouTube account"
    ThemePreferences.SUBSCRIPTIONS_BOTH -> "Device and YouTube"
    else -> "YouTube when signed in"
}

/** Longer picker-row description for a subscription source option. */
private fun subscriptionSourceOptionLabel(value: String): Pair<String, String> = when (value) {
    ThemePreferences.SUBSCRIPTIONS_LOCAL ->
        "On this device" to "Only channels you follow inside Koda"
    ThemePreferences.SUBSCRIPTIONS_YOUTUBE ->
        "YouTube account" to "Only channels your Google account is subscribed to"
    else ->
        "Everything you follow" to "Both lists merged, whichever of them exist"
}

/** Longer picker-row description for a subscribe target option. */
private fun subscribeTargetOptionLabel(value: String): Pair<String, String> = when (value) {
    ThemePreferences.SUBSCRIPTIONS_LOCAL ->
        "This device only" to "Never touches your Google account"
    ThemePreferences.SUBSCRIPTIONS_YOUTUBE ->
        "YouTube account" to "Needs you to be signed in"
    ThemePreferences.SUBSCRIPTIONS_BOTH ->
        "Device and YouTube" to "Keeps a local copy that survives signing out"
    else ->
        "YouTube when signed in" to "Falls back to this device when signed out"
}

/** One subscription-routing picker the Settings screen can open. */
internal enum class SubscriptionDialogTarget(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    SOURCE(
        titleRes = R.string.sp_subscriptions_shown,
        descriptionRes = R.string.sp_subs_source_desc
    ),
    TARGET(
        titleRes = R.string.sp_subscribe_saves_to,
        descriptionRes = R.string.sp_subs_target_desc
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubscriptionRoutingDialog(
    target: SubscriptionDialogTarget,
    currentValue: String,
    onValueSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { dialogVisible = true }

    AnimatedVisibility(
        visible = dialogVisible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = backgroundColor,
            shape = RoundedCornerShape(32.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Subscriptions,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(target.titleRes),
                    color = textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(target.descriptionRes),
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val options = when (target) {
                        SubscriptionDialogTarget.SOURCE -> ThemePreferences.SUBSCRIPTION_SOURCE_OPTIONS
                        SubscriptionDialogTarget.TARGET -> ThemePreferences.SUBSCRIBE_TARGET_OPTIONS
                    }
                    options.forEach { option ->
                        val selected = option == currentValue
                        val (label, description) = when (target) {
                            SubscriptionDialogTarget.SOURCE -> subscriptionSourceOptionLabel(option)
                            SubscriptionDialogTarget.TARGET -> subscribeTargetOptionLabel(option)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) primaryColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { onValueSelected(option) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onValueSelected(option) }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = label,
                                    color = if (selected) primaryColor else textColor,
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                                Text(
                                    text = description,
                                    color = secondaryTextColor,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(text = stringResource(R.string.action_close), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

/** One per-network quality picker the Settings screen can open. */
internal enum class QualityDialogTarget(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val isMusic: Boolean
) {
    VIDEO_WIFI(
        titleRes = R.string.qd_video_wifi,
        descriptionRes = R.string.qd_video_wifi_desc,
        isMusic = false
    ),
    VIDEO_MOBILE(
        titleRes = R.string.qd_video_mobile,
        descriptionRes = R.string.qd_video_mobile_desc,
        isMusic = false
    ),
    MUSIC_WIFI(
        titleRes = R.string.qd_music_wifi,
        descriptionRes = R.string.qd_music_wifi_desc,
        isMusic = true
    ),
    MUSIC_MOBILE(
        titleRes = R.string.qd_music_mobile,
        descriptionRes = R.string.qd_music_mobile_desc,
        isMusic = true
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreamQualityDialog(
    target: QualityDialogTarget,
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Dialog entry animation
    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dialogVisible = true
    }

    AnimatedVisibility(
        visible = dialogVisible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = backgroundColor,
            shape = RoundedCornerShape(32.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (target.isMusic) Icons.Rounded.MusicNote else Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = stringResource(target.titleRes),
                    color = textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(target.descriptionRes),
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val options = if (target.isMusic) ThemePreferences.MUSIC_QUALITY_OPTIONS
                        else ThemePreferences.VIDEO_QUALITY_OPTIONS
                    options.forEach { option ->
                        val selected = option == currentQuality
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) primaryColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { onQualitySelected(option) }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onQualitySelected(option) }
                            )
                            Text(
                                text = if (target.isMusic) musicQualityOptionLabel(option)
                                    else videoQualityLabel(option),
                                color = if (selected) primaryColor else textColor,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveAboutDialog(
    onDismiss: () -> Unit,
    onNavigateToUpdate: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    val githubUrl = "https://github.com/${BuildConfig.GITHUB_REPO}"
    val developerAvatarUrl = "https://github.com/${BuildConfig.GITHUB_USERNAME}.png"
    
    // Dialog entry animation
    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dialogVisible = true
    }
    
    AnimatedVisibility(
        visible = dialogVisible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = backgroundColor,
            shape = RoundedCornerShape(32.dp),
            icon = {
                // Developer avatar in an organic Clover4Leaf shape
                val cloverShape = remember { PolygonShape(MaterialShapes.Clover4Leaf) }
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(cloverShape)
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = developerAvatarUrl,
                        contentDescription = stringResource(R.string.cd_developer_avatar),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cloverShape),
                        contentScale = ContentScale.Crop
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        color = textColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = primaryColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = primaryColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.about_tagline),
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Version details card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AboutDetailRow(
                                label = stringResource(R.string.about_version),
                                value = BuildConfig.VERSION_NAME,
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                            AboutDetailRow(
                                label = stringResource(R.string.about_build),
                                value = BuildConfig.VERSION_CODE.toString(),
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                            AboutDetailRow(
                                label = stringResource(R.string.about_build_type),
                                value = if (BuildConfig.DEBUG) "Debug" else "Release",
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                            AboutDetailRow(
                                label = stringResource(R.string.about_developer),
                                value = "ivorisnoob",
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDismiss()
                        onNavigateToUpdate()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.action_check_updates),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun AboutDetailRow(
    label: String,
    value: String,
    textColor: Color,
    labelColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = labelColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AndroidAutoHelpDialog(
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dialogVisible = true
    }

    AnimatedVisibility(
        visible = dialogVisible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = backgroundColor,
            shape = RoundedCornerShape(32.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sp_android_auto),
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.auto_help_subtitle),
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.auto_help_intro),
                        color = textColor,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val steps = listOf(
                        stringResource(R.string.auto_help_step1),
                        stringResource(R.string.auto_help_step2),
                        stringResource(R.string.auto_help_step3),
                        stringResource(R.string.auto_help_step4)
                    )
                    steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = primaryColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = primaryColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = step,
                                color = secondaryTextColor,
                                fontSize = 13.sp
                            )
                        }
                        if (index < steps.lastIndex) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.auto_help_note),
                        color = secondaryTextColor,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_got_it),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun FolderExclusionDialog(
    availableFolders: List<FolderInfo>,
    excludedFolders: Set<String>,
    isLoading: Boolean,
    onAddExcludedFolder: (String) -> Unit,
    onRemoveExcludedFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    // Dialog entry animation
    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dialogVisible = true
    }
    
    AnimatedVisibility(
        visible = dialogVisible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = backgroundColor,
            shape = RoundedCornerShape(32.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sp_excluded_folders),
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sp_excluded_folders_sub),
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = primaryColor,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.sp_scanning_folders),
                                        color = secondaryTextColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        availableFolders.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = secondaryTextColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.sp_no_music_folders),
                                        color = secondaryTextColor,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                itemsIndexed(availableFolders) { _, folder ->
                                    val isExcluded = excludedFolders.contains(folder.path)
                                    FolderItem(
                                        folder = folder,
                                        isExcluded = isExcluded,
                                        onToggle = {
                                            if (isExcluded) {
                                                onRemoveExcludedFolder(folder.path)
                                            } else {
                                                onAddExcludedFolder(folder.path)
                                            }
                                        },
                                        textColor = textColor,
                                        secondaryTextColor = secondaryTextColor,
                                        accentColor = primaryColor
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_done),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun FolderItem(
    folder: FolderInfo,
    isExcluded: Boolean,
    onToggle: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val excludedColor = MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isExcluded) excludedColor.copy(alpha = 0.08f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isExcluded,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = excludedColor,
                    uncheckedColor = secondaryTextColor.copy(alpha = 0.5f),
                    checkmarkColor = Color.White
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Folder icon
            Icon(
                imageVector = if (isExcluded) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (isExcluded) excludedColor else accentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Folder info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    color = if (isExcluded) excludedColor else textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.songCount} song${if (folder.songCount != 1) "s" else ""}",
                    color = secondaryTextColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Playful picker for the Shorts action rail: each action is a large shaped
 * toggle that previews the real rail button — shown buttons sit plump in
 * the same cookie shape the rail uses for its active state, hidden ones
 * deflate into a small dimmed circle. Tap to flip; changes apply live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortsButtonsDialog(
    hiddenActions: Set<String>,
    onHiddenActionsChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Dialog entry animation
    var dialogVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        dialogVisible = true
    }

    fun toggle(action: String) {
        onHiddenActionsChange(
            if (action in hiddenActions) hiddenActions - action else hiddenActions + action
        )
    }

    AnimatedVisibility(
        visible = dialogVisible,
        enter = scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + fadeIn(tween(200)),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(tween(150))
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = backgroundColor,
            shape = RoundedCornerShape(32.dp),
            icon = {
                // Sunny hero badge, echoing the rail's shaped buttons
                val heroShape = remember { PolygonShape(MaterialShapes.Sunny) }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(heroShape)
                        .background(primaryColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sp_shorts_buttons),
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sp_shorts_buttons_sub),
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.ThumbUp,
                        label = stringResource(R.string.action_like),
                        shown = ThemePreferences.SHORTS_ACTION_LIKE !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_LIKE) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.ThumbDown,
                        label = stringResource(R.string.cd_dislike),
                        shown = ThemePreferences.SHORTS_ACTION_DISLIKE !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_DISLIKE) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.ChatBubble,
                        label = stringResource(R.string.cd_comments),
                        shown = ThemePreferences.SHORTS_ACTION_COMMENTS !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_COMMENTS) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.Share,
                        label = stringResource(R.string.action_share),
                        shown = ThemePreferences.SHORTS_ACTION_SHARE !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_SHARE) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.NotInterested,
                        label = stringResource(R.string.video_options_not_interested),
                        shown = ThemePreferences.SHORTS_ACTION_NOT_INTERESTED !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_NOT_INTERESTED) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_done),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

/**
 * One shaped toggle of the Shorts buttons dialog. Shown = plump cookie
 * (the rail's active shape) filled with secondaryContainer and a bouncy
 * pop; hidden = deflated dim circle with a struck-through label.
 */
@Composable
private fun ShortsButtonShapeToggle(
    icon: ImageVector,
    label: String,
    shown: Boolean,
    onToggle: () -> Unit
) {
    val cookieShape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    val shape = if (shown) cookieShape else CircleShape

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.78f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "shortsToggleScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (shown) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "shortsToggleColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (shown) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        label = "shortsToggleIcon"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(108.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(scale)
                    .clip(shape)
                    .background(containerColor)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$label: ${if (shown) "shown" else "hidden"}",
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (shown) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (shown) null else TextDecoration.LineThrough,
            textAlign = TextAlign.Center
        )
    }
}

internal fun isXiaomiDevice(): Boolean {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val brand = android.os.Build.BRAND.lowercase()
    return manufacturer.contains("xiaomi") || brand.contains("xiaomi") || 
           brand.contains("redmi") || brand.contains("poco")
}
