package com.ivor.ivormusic.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.components.rememberPermissionState
import com.ivor.ivormusic.ui.home.HomeStylePicker
import com.ivor.ivormusic.ui.player.PlayerStylePicker
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.ui.auth.YouTubeAuthDialog
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 7

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit,
    homeModeToggleEnabled: Boolean = true,
    onHomeModeToggleEnabledChange: (Boolean) -> Unit = {},
    shortsEnabled: Boolean = false,
    onShortsEnabledToggle: (Boolean) -> Unit = {},
    spotlightHome: Boolean = false,
    onSpotlightHomeChange: (Boolean) -> Unit = {},
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val sessionManager = remember { SessionManager(context) }
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    var showAuthDialog by remember { mutableStateOf(false) }

    val storagePermissionState = rememberPermissionState(
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    )
    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    if (showAuthDialog) {
        YouTubeAuthDialog(
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = {
                isLoggedIn = true
                showAuthDialog = false
            }
        )
    }

    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ),
                    start = Offset.Zero,
                    end = Offset(1400f, 2200f)
                )
            )
    ) {
        OnboardingBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 20.dp)
        ) {
            // Top bar: wordmark + skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(PolygonShape(MaterialShapes.Cookie9Sided))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Koda",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = onFinish) {
                        Text("Skip")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top
            ) { page ->
                val pageOffset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                Box(
                    modifier = Modifier.graphicsLayer {
                        val fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
                        alpha = 0.3f + 0.7f * fraction
                        val scale = 0.94f + 0.06f * fraction
                        scaleX = scale
                        scaleY = scale
                    }
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> LibraryPage(
                            loadLocalSongs = loadLocalSongs,
                            onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                            storagePermissionGranted = storagePermissionState.isGranted,
                            onRequestStoragePermission = { storagePermissionState.launchPermissionRequest() }
                        )
                        2 -> YouTubePage(
                            isLoggedIn = isLoggedIn,
                            onConnectYouTube = { showAuthDialog = true }
                        )
                        3 -> VideoModePage(
                            videoMode = videoMode,
                            shortsEnabled = shortsEnabled,
                            onShortsEnabledToggle = onShortsEnabledToggle,
                            onVideoModeToggle = onVideoModeToggle,
                            homeModeToggleEnabled = homeModeToggleEnabled,
                            onHomeModeToggleEnabledChange = onHomeModeToggleEnabledChange
                        )
                        4 -> LookAndFeelPage(
                            currentThemeMode = currentThemeMode,
                            onThemeModeChange = onThemeModeChange,
                            ambientBackground = ambientBackground,
                            onAmbientBackgroundToggle = onAmbientBackgroundToggle,
                            spotlightHome = spotlightHome,
                            onSpotlightHomeChange = onSpotlightHomeChange,
                            playerStyle = playerStyle,
                            onPlayerStyleChange = onPlayerStyleChange
                        )
                        5 -> FinalTouchesPage(
                            crossfadeEnabled = crossfadeEnabled,
                            onCrossfadeEnabledToggle = onCrossfadeEnabledToggle,
                            notificationPermissionGranted = notificationPermissionState?.isGranted ?: true,
                            onRequestNotificationPermission = { notificationPermissionState?.launchPermissionRequest() },
                            manualScanEnabled = manualScanEnabled,
                            onManualScanEnabledToggle = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                                onManualScanEnabledToggle(enabled)
                            }
                        )
                        else -> AppTimeLimitPage()
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom bar: wavy progress + navigation
            val progress by animateFloatAsState(
                targetValue = (pagerState.currentPage + 1f) / ONBOARDING_PAGE_COUNT,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "onboardingProgress"
            )

            Text(
                text = "Step ${pagerState.currentPage + 1} of $ONBOARDING_PAGE_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage > 0,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        if (isLastPage) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(56.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp)
                ) {
                    Text(
                        text = if (isLastPage) "Start listening" else "Continue",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isLastPage) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

// ---------------- Pages ----------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        MorphingHero(icon = Icons.Rounded.MusicNote)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Welcome to Koda",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Music and videos, your local files, and a player that adapts to you. A few quick choices and you are in.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                FeatureRow(
                    icon = Icons.Rounded.CloudSync,
                    shape = MaterialShapes.Flower,
                    title = "Stream anything",
                    subtitle = "Music and videos powered by YouTube Music"
                )
                FeatureRow(
                    icon = Icons.Rounded.Folder,
                    shape = MaterialShapes.Circle,
                    title = "Your local library",
                    subtitle = "Songs already on this device play right alongside"
                )
                FeatureRow(
                    icon = Icons.Rounded.Palette,
                    shape = MaterialShapes.SoftBurst,
                    title = "Made yours",
                    subtitle = "Dynamic color, ambient artwork and player styles"
                )
            }
        }
    }
}

@Composable
private fun LibraryPage(
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    storagePermissionGranted: Boolean,
    onRequestStoragePermission: () -> Unit
) {
    OnboardingPageScaffold(
        icon = Icons.Rounded.Folder,
        iconShape = MaterialShapes.Circle,
        title = "Your music",
        body = "Bring the songs already stored on this device into Home, Search and Library. Skip it if you want a streaming-first setup."
    ) {
        SettingSwitchRow(
            icon = Icons.Rounded.Folder,
            title = "Scan device audio",
            subtitle = "Show local songs across the app",
            checked = loadLocalSongs,
            onCheckedChange = onLoadLocalSongsToggle
        )

        AnimatedVisibility(visible = loadLocalSongs) {
            PermissionRow(
                icon = Icons.Rounded.Album,
                title = "Music access",
                subtitle = if (storagePermissionGranted) {
                    "Koda can read your audio library."
                } else {
                    "Needed to find songs on this device."
                },
                granted = storagePermissionGranted,
                actionLabel = "Allow",
                onAction = onRequestStoragePermission
            )
        }

        HintText("Only your audio files are read. Nothing leaves the device.")
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun YouTubePage(
    isLoggedIn: Boolean,
    onConnectYouTube: () -> Unit
) {
    OnboardingPageScaffold(
        icon = Icons.Rounded.CloudSync,
        iconShape = MaterialShapes.Flower,
        title = "YouTube Music",
        body = "Connect your account for personalized recommendations, liked songs and playlists. Search and streaming work without it."
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isLoggedIn) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLoggedIn) Icons.Rounded.Check else Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = if (isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = if (isLoggedIn) "Account connected" else "Not connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isLoggedIn) {
                                "Personalized recommendations are ready."
                            } else {
                                "Optional, and you can sign in later from Settings."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isLoggedIn) {
                    Button(
                        onClick = onConnectYouTube,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Connect YouTube Music")
                    }
                }
            }
        }

        HintText("Your session is stored securely on this device only.")
    }
}

@Composable
private fun VideoModePage(
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit,
    homeModeToggleEnabled: Boolean,
    onHomeModeToggleEnabledChange: (Boolean) -> Unit,
    shortsEnabled: Boolean,
    onShortsEnabledToggle: (Boolean) -> Unit
) {
    OnboardingPageScaffold(
        icon = Icons.Rounded.Videocam,
        iconShape = MaterialShapes.Arch,
        title = "Video mode",
        body = "Turn the Home tab into a video feed with an in-app player, mini player and picture-in-picture. You can flip between music and video any time."
    ) {
        SettingSwitchRow(
            icon = Icons.Rounded.Videocam,
            title = "Enable video mode",
            subtitle = "Video home, video search and watch history",
            checked = videoMode,
            onCheckedChange = onVideoModeToggle
        )

        SettingSwitchRow(
            icon = Icons.Rounded.ToggleOn,
            title = "Home screen switch",
            subtitle = "Quick music/video toggle in the Home top bar",
            checked = homeModeToggleEnabled,
            onCheckedChange = onHomeModeToggleEnabledChange
        )

        SettingSwitchRow(
            icon = Icons.Rounded.Bolt,
            title = "Shorts",
            subtitle = "A Shorts shelf and vertical swipe player in video mode",
            checked = shortsEnabled,
            onCheckedChange = onShortsEnabledToggle
        )

        ShortsWarningCard()

        HintText("Off keeps a cleaner, music-first layout. The video player stays available either way.")
    }
}

/**
 * Deliberate friction before enabling Shorts: endless short-form feeds are
 * engineered to be compulsive, so Koda ships them off and warns before the
 * user opts in.
 */
@Composable
private fun ShortsWarningCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Endless short-form feeds are designed to keep you scrolling. Shorts stay off unless you choose them, and you can turn them off again any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LookAndFeelPage(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit,
    spotlightHome: Boolean,
    onSpotlightHomeChange: (Boolean) -> Unit,
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit
) {
    OnboardingPageScaffold(
        icon = Icons.Rounded.Palette,
        iconShape = MaterialShapes.Diamond,
        title = "Look and feel",
        body = "Koda uses Material You dynamic color. Choose how it should behave on this device."
    ) {
        ChoiceCard(title = "Theme") {
            ConnectedChoiceGroup(
                options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.LIGHT to "Light"
                ),
                selected = currentThemeMode,
                onSelect = onThemeModeChange
            )
        }

        SettingSwitchRow(
            icon = Icons.Rounded.Wallpaper,
            title = "Ambient artwork",
            subtitle = "Let album art color the player background",
            checked = ambientBackground,
            onCheckedChange = onAmbientBackgroundToggle
        )

        ChoiceCard(title = "Home screen") {
            Text(
                text = "Classic leads with one big hero. Spotlight opens with a grid " +
                    "of the things you reach for most, then quick picks and " +
                    "shelves. Either can be changed later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            HomeStylePicker(
                spotlightHome = spotlightHome,
                onSpotlightHomeChange = onSpotlightHomeChange
            )
        }

        ChoiceCard(title = "Player style") {
            Text(
                text = "Pick the player you want. You can change it any time in " +
                    "Settings, or by long-pressing the artwork while a song plays.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PlayerStylePicker(
                currentStyle = playerStyle,
                onStyleSelected = onPlayerStyleChange
            )
        }
    }
}

@Composable
private fun FinalTouchesPage(
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit
) {
    OnboardingPageScaffold(
        icon = Icons.Rounded.Tune,
        iconShape = MaterialShapes.Pill,
        title = "Final touches",
        body = "A few optional extras. All of this lives in Settings if you change your mind later."
    ) {
        SettingSwitchRow(
            icon = Icons.Rounded.GraphicEq,
            title = "Crossfade",
            subtitle = "Blend songs into each other during playback",
            checked = crossfadeEnabled,
            onCheckedChange = onCrossfadeEnabledToggle
        )

        PermissionRow(
            icon = Icons.Rounded.NotificationsActive,
            title = "Playback notifications",
            subtitle = if (notificationPermissionGranted) {
                "Lock screen controls are ready."
            } else {
                "Enables lock screen and background controls."
            },
            granted = notificationPermissionGranted,
            actionLabel = "Enable",
            onAction = onRequestNotificationPermission
        )

        SettingSwitchRow(
            icon = Icons.Rounded.PhoneAndroid,
            title = "High compatibility scan",
            subtitle = "Only for devices that hide music from the system index, like some HyperOS builds",
            checked = manualScanEnabled,
            onCheckedChange = onManualScanEnabledToggle
        )
    }
}

/**
 * The last onboarding page: the optional daily time limit. Off by default;
 * enabling it here applies one budget to every weekday, and the per-day
 * editor lives in Settings > Advanced for anyone who wants finer control.
 *
 * Owns its [ThemePreferences] directly, like this screen already does for
 * SessionManager - nothing else needs to react to the choice while the page
 * is up.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppTimeLimitPage() {
    val context = LocalContext.current
    val prefs = remember { com.ivor.ivormusic.data.ThemePreferences(context) }
    var enabled by remember { mutableStateOf(prefs.isTimeLimitEnabled()) }
    var dailyMinutes by remember {
        val budgets =
            com.ivor.ivormusic.data.AppTimeLimit.parseBudgets(prefs.getTimeLimitBudgets())
        val uniform = budgets.values.distinct().singleOrNull()
        mutableStateOf(uniform ?: com.ivor.ivormusic.data.AppTimeLimit.DEFAULT_DAILY_MINUTES)
    }

    fun applyPreset(minutes: Int) {
        dailyMinutes = minutes
        prefs.setAllTimeLimitBudgets(minutes)
        if (!enabled) {
            enabled = true
            prefs.setTimeLimitEnabled(true)
        }
    }

    OnboardingPageScaffold(
        icon = Icons.Rounded.Bedtime,
        iconShape = MaterialShapes.SoftBurst,
        title = "Daily time limit",
        body = "Completely optional, and off by default. Decide how much Koda you get each day - when it's used up, the app locks until midnight."
    ) {
        SettingSwitchRow(
            icon = Icons.Rounded.Bedtime,
            title = "Limit my listening",
            subtitle = "Locks Koda once today's budget runs out",
            checked = enabled,
            onCheckedChange = { on ->
                enabled = on
                prefs.setTimeLimitEnabled(on)
                if (on) prefs.setAllTimeLimitBudgets(dailyMinutes)
            }
        )

        AnimatedVisibility(visible = enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Every day:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(60, 120, 180, 300, 480).forEach { minutes ->
                        FilterChip(
                            selected = dailyMinutes == minutes,
                            onClick = { applyPreset(minutes) },
                            label = {
                                Text(
                                    com.ivor.ivormusic.data.AppTimeLimit.formatBudget(minutes)
                                )
                            }
                        )
                    }
                }
                Text(
                    text = "Different hours per weekday can be set later in Settings > Advanced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------- Building blocks ----------------

@Composable
private fun OnboardingPageScaffold(
    icon: ImageVector,
    iconShape: RoundedPolygon,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ShapeIconBadge(icon = icon, shape = iconShape)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        content()
    }
}

/** Hero for the welcome page: a shape morphing between two expressive forms. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingHero(icon: ImageVector) {
    val transition = rememberInfiniteTransition(label = "heroMorph")
    val morphProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "morphProgress"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing)
        ),
        label = "heroRotation"
    )
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MorphShape(morph, morphProgress))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(56.dp)
        )
    }
}

/** Page icon inside a slowly rotating expressive shape. */
@Composable
private fun ShapeIconBadge(
    icon: ImageVector,
    shape: RoundedPolygon,
    size: Dp = 76.dp
) {
    val transition = rememberInfiniteTransition(label = "badgeSpin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing)
        ),
        label = "badgeRotation"
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { rotationZ = rotation }
                .clip(PolygonShape(shape))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(size * 0.42f)
        )
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    shape: RoundedPolygon,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(PolygonShape(shape))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (granted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Rounded.Check else icon,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (granted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                FilledTonalButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ConnectedChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, (value, label) ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(value) },
                modifier = Modifier.weight(1f),
                shapes = shapes,
                colors = ToggleButtonDefaults.toggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = label,
                    fontWeight = if (selected == value) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

// ---------------- Backdrop & shapes ----------------

@Composable
private fun OnboardingBackdrop() {
    val transition = rememberInfiniteTransition(label = "onboardingBackdrop")
    val driftA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftB"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(start = (24 + driftA * 50).dp, top = (70 + driftB * 40).dp)
                .size(190.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (12 + driftB * 40).dp, bottom = (170 + driftA * 50).dp)
                .size(230.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f))
        )
    }
}

private class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = polygon.toPath().asComposePath()
        val matrix = Matrix()
        val bounds = polygon.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

private class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = morph.toPath(progress).asComposePath()
        val matrix = Matrix()
        val bounds = morph.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
