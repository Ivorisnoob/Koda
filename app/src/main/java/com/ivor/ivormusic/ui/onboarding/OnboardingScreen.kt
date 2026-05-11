package com.ivor.ivormusic.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.ui.auth.YouTubeAuthDialog
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 9

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    ),
                    start = Offset.Zero,
                    end = Offset(1200f, 1800f)
                )
            )
    ) {
        AnimatedBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Koda setup",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Set the app up your way.",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(0.86f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Local files, streaming, video mode, and sign-in stay optional. You can change everything later in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(end = 24.dp),
                pageSpacing = 16.dp,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> LocalMusicPage(
                        loadLocalSongs = loadLocalSongs,
                        onLoadLocalSongsToggle = onLoadLocalSongsToggle,
                        storagePermissionGranted = storagePermissionState.status.isGranted,
                        onRequestStoragePermission = { storagePermissionState.launchPermissionRequest() }
                    )
                    1 -> YouTubeConnectionPage(
                        isLoggedIn = isLoggedIn,
                        onConnectYouTube = { showAuthDialog = true }
                    )
                    2 -> VideoModePage(
                        videoMode = videoMode,
                        onVideoModeToggle = onVideoModeToggle
                    )
                    3 -> ThemePage(
                        currentThemeMode = currentThemeMode,
                        onThemeModeChange = onThemeModeChange
                    )
                    4 -> PlayerStylePage(
                        playerStyle = playerStyle,
                        onPlayerStyleChange = onPlayerStyleChange
                    )
                    5 -> AmbientBackgroundPage(
                        ambientBackground = ambientBackground,
                        onAmbientBackgroundToggle = onAmbientBackgroundToggle
                    )
                    6 -> CrossfadePage(
                        crossfadeEnabled = crossfadeEnabled,
                        onCrossfadeEnabledToggle = onCrossfadeEnabledToggle
                    )
                    7 -> PermissionsPage(
                        loadLocalSongs = loadLocalSongs,
                        storagePermissionGranted = storagePermissionState.status.isGranted,
                        onRequestStoragePermission = { storagePermissionState.launchPermissionRequest() },
                        notificationPermissionGranted = notificationPermissionState?.status?.isGranted ?: true,
                        onRequestNotificationPermission = { notificationPermissionState?.launchPermissionRequest() }
                    )
                    else -> CompatibilityPage(
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
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(ONBOARDING_PAGE_COUNT) { index ->
                        val selected = pagerState.currentPage == index
                        val width by animateFloatAsState(
                            targetValue = if (selected) 28f else 10f,
                            animationSpec = spring(),
                            label = "pagerIndicatorWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(width.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(
                        visible = pagerState.currentPage > 0,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Back")
                        }
                    }

                    Button(
                        onClick = {
                            if (pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1) {
                                onFinish()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1) "Start listening" else "Continue")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalMusicPage(
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    storagePermissionGranted: Boolean,
    onRequestStoragePermission: () -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.Folder,
        iconShape = MaterialShapes.Circle,
        title = "Local music",
        body = "Use music already stored on this device. This is best when you want Koda to behave like a normal offline-capable music player.",
        details = listOf(
            "When this is on, Home and Search start from your phone's audio library.",
            "Koda only asks for media access if you choose local music.",
            "Turn this off if you want the first screen to use YouTube Music discovery instead."
        )
    ) {
        ToggleCard(
            icon = Icons.Rounded.Folder,
            title = "Scan device audio",
            subtitle = "Show songs from local storage across Home, Search, and Library.",
            checked = loadLocalSongs,
            onCheckedChange = onLoadLocalSongsToggle
        )

        PermissionCard(
            icon = Icons.Rounded.Album,
            title = "Music access",
            subtitle = if (loadLocalSongs) {
                "Required for local library scanning. You can skip it and grant it later."
            } else {
                "Not needed while local audio scanning is off."
            },
            granted = storagePermissionGranted || !loadLocalSongs,
            actionLabel = if (storagePermissionGranted || !loadLocalSongs) "Ready" else "Allow access",
            onAction = onRequestStoragePermission,
            actionEnabled = loadLocalSongs && !storagePermissionGranted
        )
    }
}

@Composable
private fun YouTubeConnectionPage(
    isLoggedIn: Boolean,
    onConnectYouTube: () -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.CloudSync,
        iconShape = MaterialShapes.Flower,
        title = "YouTube Music",
        body = "Connect only if you want personal YouTube Music data. Search and public streaming can still work without making Google account setup part of onboarding.",
        details = listOf(
            "Connected accounts can show personalized recommendations, liked songs, and playlists.",
            "Cookies are stored by the existing secure session manager.",
            "Skipping sign-in keeps the app usable and avoids forcing account setup."
        )
    ) {
        PermissionCard(
            icon = Icons.Rounded.CloudSync,
            title = if (isLoggedIn) "Account connected" else "Optional account connection",
            subtitle = if (isLoggedIn) {
                "Personalized YouTube Music features are available."
            } else {
                "Use this only when playlists, liked songs, and account recommendations matter to you."
            },
            granted = isLoggedIn,
            actionLabel = if (isLoggedIn) "Connected" else "Connect YouTube Music",
            onAction = onConnectYouTube,
            actionEnabled = !isLoggedIn
        )
    }
}

@Composable
private fun VideoModePage(
    videoMode: Boolean,
    onVideoModeToggle: (Boolean) -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.Videocam,
        iconShape = MaterialShapes.Arch,
        title = "Video mode",
        body = "Video mode shifts discovery toward music videos and watch history, while keeping the in-app video player available.",
        details = listOf(
            "Turn this on if you want video discovery on the main tab.",
            "The app can play videos in-app and keep a mini player overlay.",
            "Turn it off for a cleaner music-first layout."
        )
    ) {
        ToggleCard(
            icon = Icons.Rounded.Videocam,
            title = "Enable video mode",
            subtitle = "Show video home, video search results, and video history surfaces.",
            checked = videoMode,
            onCheckedChange = onVideoModeToggle
        )
    }
}

@Composable
private fun ThemePage(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.Palette,
        iconShape = MaterialShapes.Diamond,
        title = "Theme",
        body = "Choose the color mode used on first launch. The app still uses Material 3 dynamic color where the device supports it.",
        details = listOf(
            "System follows the phone's light or dark setting.",
            "Dark gives the player a more focused media-app feel.",
            "Light keeps library and settings screens brighter in daytime use."
        )
    ) {
        ThemeSelector(
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange
        )
    }
}

@Composable
private fun PlayerStylePage(
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.MusicNote,
        iconShape = MaterialShapes.Flower,
        title = "Player controls",
        body = "Pick how the now-playing screen should feel. This only changes the player interaction model, not playback quality or queue behavior.",
        details = listOf(
            "Classic keeps visible playback controls and is easier to learn.",
            "Gesture uses swipe-driven movement for a more fluid player.",
            "You can switch between them later without losing your queue or settings."
        )
    ) {
        PlayerStyleSelector(
            playerStyle = playerStyle,
            onPlayerStyleChange = onPlayerStyleChange
        )
    }
}

@Composable
private fun AmbientBackgroundPage(
    ambientBackground: Boolean,
    onAmbientBackgroundToggle: (Boolean) -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.Wallpaper,
        iconShape = MaterialShapes.SoftBurst,
        title = "Ambient artwork",
        body = "Ambient backgrounds let album art influence the visual tone of the player and browse surfaces.",
        details = listOf(
            "This makes the app feel more responsive to the song you are playing.",
            "Keep it on for a richer media-player look.",
            "Turn it off if you prefer cleaner, steadier backgrounds."
        )
    ) {
        ToggleCard(
            icon = Icons.Rounded.Wallpaper,
            title = "Use ambient backgrounds",
            subtitle = "Let artwork colors subtly influence the interface.",
            checked = ambientBackground,
            onCheckedChange = onAmbientBackgroundToggle
        )
    }
}

@Composable
private fun CrossfadePage(
    crossfadeEnabled: Boolean,
    onCrossfadeEnabledToggle: (Boolean) -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.GraphicEq,
        iconShape = MaterialShapes.Pill,
        title = "Crossfade",
        body = "Crossfade softens the handoff between songs. It is useful for playlists and long listening sessions, but not everyone wants overlap between tracks.",
        details = listOf(
            "When enabled, the player fades from one track into the next.",
            "It can make shuffled playback feel smoother.",
            "Disable it if you want album transitions and track endings to stay exact."
        )
    ) {
        ToggleCard(
            icon = Icons.Rounded.GraphicEq,
            title = "Crossfade playback",
            subtitle = "Blend neighboring songs during continuous playback.",
            checked = crossfadeEnabled,
            onCheckedChange = onCrossfadeEnabledToggle
        )
    }
}

@Composable
private fun PermissionsPage(
    loadLocalSongs: Boolean,
    storagePermissionGranted: Boolean,
    onRequestStoragePermission: () -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.Security,
        iconShape = MaterialShapes.Pentagon,
        title = "Permissions",
        body = "Permissions stay tied to the features that need them. This keeps first launch useful even when you skip local files or notification controls.",
        details = listOf(
            "Music library access is only needed for device audio scanning.",
            "Notifications are optional, but they improve lock screen and ongoing playback controls.",
            "Both can be changed later from Android settings if you skip them here."
        )
    ) {
        PermissionCard(
            icon = Icons.Rounded.Album,
            title = "Music library access",
            subtitle = if (loadLocalSongs) {
                "Needed only if you want Koda to scan songs stored on this device."
            } else {
                "Not required right now because local device audio is turned off."
            },
            granted = storagePermissionGranted || !loadLocalSongs,
            actionLabel = if (storagePermissionGranted || !loadLocalSongs) "Ready" else "Allow access",
            onAction = onRequestStoragePermission,
            actionEnabled = loadLocalSongs && !storagePermissionGranted
        )

        PermissionCard(
            icon = Icons.Rounded.NotificationsActive,
            title = "Playback notifications",
            subtitle = "Optional, but useful for lock screen controls and ongoing playback status.",
            granted = notificationPermissionGranted,
            actionLabel = if (notificationPermissionGranted) "Ready" else "Enable",
            onAction = onRequestNotificationPermission,
            actionEnabled = !notificationPermissionGranted
        )
    }
}

@Composable
private fun CompatibilityPage(
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit
) {
    OnboardingPage(
        icon = Icons.Rounded.PhoneAndroid,
        iconShape = MaterialShapes.Boom,
        title = "Compatibility scanning",
        body = "This is an escape hatch for devices where Android's normal media index misses songs. It is useful, but it can ask for broader file access.",
        details = listOf(
            "Leave it off when your music library appears normally.",
            "Turn it on for devices that hide music from MediaStore, especially some Xiaomi, Redmi, Poco, or HyperOS builds.",
            "If Android opens an All Files Access screen, that is only for this high compatibility scan path."
        )
    ) {
        ToggleCard(
            icon = Icons.Rounded.PhoneAndroid,
            title = "High compatibility scanning",
            subtitle = "Use a broader scan when normal library discovery misses songs.",
            checked = manualScanEnabled,
            onCheckedChange = onManualScanEnabledToggle
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
        val matrix = androidx.compose.ui.graphics.Matrix()
        val bounds = polygon.calculateBounds()
        val boundsWidth = bounds[2] - bounds[0]
        val boundsHeight = bounds[3] - bounds[1]

        matrix.scale(size.width / boundsWidth, size.height / boundsHeight)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    iconShape: RoundedPolygon,
    title: String,
    body: String,
    details: List<String>,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(34.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StaticShapeIcon(
                    icon = icon,
                    shape = iconShape
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                details.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        content()
    }
}

@Composable
private fun StaticShapeIcon(
    icon: ImageVector,
    shape: RoundedPolygon
) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .clip(PolygonShape(shape))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun ThemeSelector(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Color mode", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ThemeMode.SYSTEM to "System",
                    ThemeMode.DARK to "Dark",
                    ThemeMode.LIGHT to "Light"
                )
                options.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentThemeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        ),
                        icon = {}
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerStyleSelector(
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Player style", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    PlayerStyle.CLASSIC to "Classic",
                    PlayerStyle.GESTURE to "Gesture"
                )
                options.forEachIndexed { index, (style, label) ->
                    SegmentedButton(
                        selected = playerStyle == style,
                        onClick = { onPlayerStyleChange(style) },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size
                        ),
                        icon = {}
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (granted) Color(0xFF3D8B40).copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (granted) Color(0xFF3D8B40) else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            }
            OutlinedButton(
                onClick = onAction,
                enabled = actionEnabled,
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun AnimatedBackdrop() {
    val transition = rememberInfiniteTransition(label = "background")
    val driftA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftB"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(start = (20 + driftA * 60).dp, top = (60 + driftB * 30).dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (10 + driftB * 50).dp, bottom = (160 + driftA * 40).dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = (driftA * 30).dp)
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
        )
    }
}
