package com.ivor.ivormusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.data.FolderInfo
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.ThemePreferences

import com.ivor.ivormusic.ui.auth.YouTubeAuthDialog
import com.ivor.ivormusic.data.PlayerStyle
import com.ivor.ivormusic.ui.theme.ThemeMode
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
    playerStyle: PlayerStyle,
    onPlayerStyleChange: (PlayerStyle) -> Unit,
    saveVideoHistory: Boolean,
    onSaveVideoHistoryToggle: (Boolean) -> Unit,
    timedCommentsEnabled: Boolean,
    onTimedCommentsToggle: (Boolean) -> Unit,
    shortsEnabled: Boolean,
    onShortsEnabledToggle: (Boolean) -> Unit,
    shortsHiddenActions: Set<String> = emptySet(),
    onShortsHiddenActionsChange: (Set<String>) -> Unit = {},
    defaultVideoQuality: String,
    onDefaultVideoQualityChange: (String) -> Unit,
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
    crossfadeDurationMs: Int,
    onCrossfadeDurationChange: (Int) -> Unit,
    oemFixEnabled: Boolean,
    onOemFixEnabledToggle: (Boolean) -> Unit,
    manualScanEnabled: Boolean,
    onManualScanEnabledToggle: (Boolean) -> Unit,
    onNavigateToUpdate: () -> Unit = {},
    localOnlyMode: Boolean = false,
    onLocalOnlyModeToggle: (Boolean) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Check actual login status
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    // Dialog state for YouTube auth
    var showAuthDialog by remember { mutableStateOf(false) }
    
    // Dialog state for About
    var showAboutDialog by remember { mutableStateOf(false) }
    
    // Dialog state for Default Video Quality
    var showVideoQualityDialog by remember { mutableStateOf(false) }

    // Dialog state for Folder Exclusion
    var showFolderExclusionDialog by remember { mutableStateOf(false) }
    var showShortsButtonsDialog by remember { mutableStateOf(false) }
    var availableFolders by remember { mutableStateOf<List<FolderInfo>>(emptyList()) }
    var isFoldersLoading by remember { mutableStateOf(false) }
    
    // Animation states for staggered entry
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(contentPadding)
    ) {
        // Top App Bar with expressive back button
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
                        text = "Settings",
                        color = textColor,
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
                        contentColor = textColor
                    ),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Appearance Section with staggered animation
            item {
                SettingsSection(
                    title = "Appearance",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveThemeSelectGroup(
                            currentMode = currentThemeMode,
                            onModeSelected = onThemeModeChange,
                            textColor = textColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        // Color palette picker link
                        val paletteName = if (colorPalette == ThemePreferences.DEFAULT_COLOR_PALETTE) {
                            "Dynamic (from wallpaper)"
                        } else {
                            com.ivor.ivormusic.ui.theme.findPalette(colorPalette)?.name ?: "Dynamic"
                        }
                        ExpressiveSettingsItem(
                            icon = Icons.Rounded.Palette,
                            title = "Color palette",
                            subtitle = paletteName,
                            onClick = onNavigateToColorPalette,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            iconTint = accentColor,
                            showChevron = true
                        )

                        SettingsDivider()

                        // AMOLED pure black toggle
                        ExpressiveAmoledThemeToggleItem(
                            enabled = amoledTheme,
                            onToggle = onAmoledThemeToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        // Ambient Background toggle
                        ExpressiveAmbientBackgroundToggleItem(
                            enabled = ambientBackground,
                            onToggle = onAmbientBackgroundToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )
                    }
                }
            }
            
            // Player UI Section
            item {
                SettingsSection(
                    title = "Player UI",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressivePlayerStyleSelectItem(
                            currentStyle = playerStyle,
                            onStyleSelected = onPlayerStyleChange,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        // Album-art button colors, applies to every player style
                        ExpressiveOemToggleItem(
                            icon = Icons.Rounded.Palette,
                            title = "Album Art Colors",
                            subtitle = "Color the expanded player's buttons from the current cover",
                            enabled = playerArtworkColors,
                            onToggle = onPlayerArtworkColorsToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )
                    }
                }
            }

            // Playback Section
            item {
                SettingsSection(
                    title = "Playback",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        // Crossfade Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Crossfade",
                                    color = textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Smoothly fade between songs",
                                    color = secondaryTextColor,
                                    fontSize = 13.sp
                                )
                            }
                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = onCrossfadeEnabledToggle,
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accentColor)
                            )
                        }
                        
                        AnimatedVisibility(visible = crossfadeEnabled) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Duration: ${crossfadeDurationMs / 1000}s",
                                    color = textColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                androidx.compose.material3.Slider(
                                    value = crossfadeDurationMs.toFloat(),
                                    onValueChange = { onCrossfadeDurationChange(it.toInt()) },
                                    valueRange = 1000f..12000f,
                                    steps = 10,
                                    colors = androidx.compose.material3.SliderDefaults.colors(
                                        thumbColor = accentColor,
                                        activeTrackColor = accentColor
                                    )
                                )
                            }
                        }

                        SettingsDivider()

                        // Auto-load Queue Toggle
                        ExpressiveOemToggleItem(
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            title = "Auto-load Queue",
                            subtitle = "Add recommended songs when the queue runs low",
                            enabled = autoLoadQueue,
                            onToggle = onAutoLoadQueueToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )
                    }
                }
            }

            // OEM & HyperOS Fixes Section
            item {
                SettingsSection(
                    title = "OEM & HyperOS Fixes",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        // High Compatibility Scanning (Manual Scan)
                        ExpressiveOemToggleItem(
                            icon = Icons.Rounded.Security,
                            title = "High Compatibility Scanning",
                            subtitle = "Bypasses MediaStore (Fixes missing music on HyperOS)",
                            enabled = manualScanEnabled,
                            onToggle = { enabled ->
                                if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    if (!android.os.Environment.isExternalStorageManager()) {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                onManualScanEnabledToggle(enabled)
                            },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = Color(0xFF4CAF50)
                        )
                        
                        SettingsDivider()
                        
                        // Battery Optimization Fix
                        ExpressiveSettingsItem(
                            icon = Icons.Rounded.FlashOn,
                            title = "Ignore Battery Optimizations",
                            subtitle = "Prevents playback from stopping in background",
                            onClick = {
                                val packageName = context.packageName
                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Fallback for HyperOS/Restrictive OEMs: Open App Info
                                    // From here user can manually set "No restrictions" in Battery saver
                                    try {
                                        val appInfoIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:$packageName")
                                        }
                                        context.startActivity(appInfoIntent)
                                    } catch (e2: Exception) {
                                        // Absolute fallback
                                        context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                }
                            },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            iconTint = Color(0xFFFFB300),
                            showChevron = true
                        )
                    }
                    
                    if (isXiaomiDevice()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.1f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFF57C00),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Xiaomi device detected. Enabling these is highly recommended.",
                                    color = Color(0xFFF57C00),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // Storage & Cache Section
            item {
                SettingsSection(
                    title = "Storage & Cache",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        // Music Cache Toggle
                        ExpressiveOemToggleItem(
                            icon = Icons.Rounded.Save,
                            title = "Cache Music",
                            subtitle = "Store streamed songs for instant replay",
                            enabled = cacheEnabled,
                            onToggle = onCacheEnabledToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        // Cache Size Display (Expressive Card)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(accentColor.copy(alpha = 0.1f))
                                .padding(16.dp)
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                 Icon(
                                     imageVector = Icons.Rounded.Folder,
                                     contentDescription = null,
                                     tint = accentColor,
                                     modifier = Modifier.size(32.dp)
                                 )
                                 Spacer(modifier = Modifier.width(16.dp))
                                 Column {
                                     Text(
                                         text = "Local Cache",
                                         color = textColor,
                                         fontWeight = FontWeight.SemiBold,
                                         fontSize = 16.sp
                                     )
                                     Text(
                                         text = com.ivor.ivormusic.data.CacheManager.formatSize(currentCacheSize), 
                                         color = accentColor,
                                         fontWeight = FontWeight.Bold,
                                         fontSize = 24.sp
                                     )
                                 }
                             }
                        }
                        
                        // Limit Selector
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Max Cache Size",
                                color = secondaryTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val options = listOf(256L, 512L, 1024L, 2048L)
                                val labels = listOf("256MB", "512MB", "1GB", "2GB")
                                
                                options.forEachIndexed { index, size ->
                                    SegmentedButton(
                                        selected = maxCacheSizeMb == size,
                                        onClick = { onMaxCacheSizeMbChange(size) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                                    ) {
                                        Text(text = labels[index])
                                    }
                                }
                            }
                        }
                        
                        SettingsDivider()
                        
                        // Clear Cache Button
                        ExpressiveSettingsItem(
                            icon = Icons.Rounded.FolderOff,
                            title = "Clear Cache",
                            subtitle = "Free up storage space",
                            onClick = onClearCacheClick,
                            textColor = Color(0xFFE53935),
                            secondaryTextColor = secondaryTextColor,
                            iconTint = Color(0xFFE53935)
                        )
                    }
                }
            }

            // YouTube Music Section
            item {
                SettingsSection(
                    title = "YouTube Music",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                    if (isLoggedIn) {
                            // Logged in state with user avatar
                            ExpressiveAccountItem(
                                sessionManager = sessionManager,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor
                            )
                            SettingsDivider()
                            // Save Video History Toggle
                            ExpressiveSaveHistoryToggleItem(
                                enabled = saveVideoHistory,
                                onToggle = onSaveVideoHistoryToggle,
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                accentColor = Color(0xFFFF0000) // YouTube red
                            )
                            SettingsDivider()
                            ExpressiveSettingsItem(
                                icon = Icons.AutoMirrored.Rounded.Logout,
                                title = "Sign Out",
                                subtitle = "Disconnect your YouTube account",
                                onClick = {
                                    sessionManager.clearSession()
                                    isLoggedIn = false
                                    onLogoutClick()
                                },
                                textColor = Color(0xFFE53935),
                                secondaryTextColor = secondaryTextColor,
                                iconTint = Color(0xFFE53935)
                            )
                        } else {
                            // Logged out state
                            ExpressiveSettingsItem(
                                icon = Icons.Rounded.MusicNote,
                                title = "Connect YouTube Music",
                                subtitle = "Sign in to access your playlists and liked songs",
                                onClick = { showAuthDialog = true },
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                iconTint = Color(0xFFFF0000),
                                showChevron = true
                            )
                        }
                    }
                }
            }

            // Content Mode Section (Video/Music toggle)
            item {
                SettingsSection(
                    title = "Content Mode",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveLocalOnlyToggleItem(
                            enabled = localOnlyMode,
                            onToggle = onLocalOnlyModeToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        ExpressiveVideoModeToggleItem(
                            enabled = videoMode,
                            onToggle = onVideoModeToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = Color(0xFFFF0000) // YouTube red
                        )

                        SettingsDivider()

                        ExpressiveHomeModeToggleItem(
                            enabled = homeModeToggleEnabled,
                            onToggle = onHomeModeToggleChange,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        ExpressiveTimedCommentsToggleItem(
                            enabled = timedCommentsEnabled,
                            onToggle = onTimedCommentsToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )

                        SettingsDivider()

                        ExpressiveShortsToggleItem(
                            enabled = shortsEnabled,
                            onToggle = onShortsEnabledToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
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
                                ExpressiveSettingsItem(
                                    icon = Icons.Rounded.Visibility,
                                    title = "Shorts Buttons",
                                    subtitle = if (shortsHiddenActions.isEmpty()) "All buttons shown"
                                        else "${shortsHiddenActions.size} hidden",
                                    onClick = { showShortsButtonsDialog = true },
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    iconTint = accentColor,
                                    showChevron = true
                                )
                            }
                        }

                        SettingsDivider()

                        ExpressiveSettingsItem(
                            icon = Icons.Rounded.HighQuality,
                            title = "Default Video Quality",
                            subtitle = videoQualityLabel(defaultVideoQuality),
                            onClick = { showVideoQualityDialog = true },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            iconTint = accentColor,
                            showChevron = true
                        )
                    }
                }
            }

            // Library Section
            item {
                SettingsSection(
                    title = "Library",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveLocalSongsToggleItem(
                            loadLocalSongs = loadLocalSongs,
                            onToggle = onLoadLocalSongsToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )
                        
                        // Folder Exclusion - only show when local songs are enabled
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
                                ExpressiveFolderExclusionItem(
                                    excludedFoldersCount = excludedFolders.size,
                                    onClick = {
                                        // Load available folders when opening dialog
                                        isFoldersLoading = true
                                        coroutineScope.launch {
                                            availableFolders = homeViewModel.getAvailableFolders()
                                            isFoldersLoading = false
                                        }
                                        showFolderExclusionDialog = true
                                    },
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    accentColor = accentColor
                                )
                            }
                        }
                    }
                }
            }

            // About Section
            item {
                SettingsSection(
                    title = "About",
                    textColor = secondaryTextColor
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveSettingsItem(
                            icon = Icons.Rounded.Info,
                            title = "Koda",
                            subtitle = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            onClick = { showAboutDialog = true },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            iconTint = accentColor,
                            showChevron = true
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
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
    
    // About Dialog with expressive styling
    if (showAboutDialog) {
        ExpressiveAboutDialog(
            onDismiss = { showAboutDialog = false },
            onNavigateToUpdate = onNavigateToUpdate
        )
    }
    
    // Default Video Quality Dialog
    if (showVideoQualityDialog) {
        VideoQualityDialog(
            currentQuality = defaultVideoQuality,
            onQualitySelected = {
                onDefaultVideoQualityChange(it)
                showVideoQualityDialog = false
            },
            onDismiss = { showVideoQualityDialog = false }
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
}

@Composable
private fun SettingsSection(
    title: String,
    textColor: Color,
    content: @Composable () -> Unit
) {
    // No entrance animation on purpose: these live in a LazyColumn, which
    // recomposes items as they scroll into view — any enter animation replays
    // on every scroll and makes the cards look like they're "loading".
    Column {
        Text(
            text = title.uppercase(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun ExpressiveSettingsCard(
    surfaceColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = surfaceColor,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            content()
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveThemeSelectGroup(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    textColor: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Theme",
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
                        containerColor = MaterialTheme.colorScheme.surface,
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
private fun ExpressiveSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    showChevron: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon with expressive background
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        if (showChevron) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = secondaryTextColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun ExpressiveAccountItem(
    sessionManager: SessionManager,
    textColor: Color,
    secondaryTextColor: Color
) {
    val userAvatar = sessionManager.getUserAvatar()
    val userName = sessionManager.getUserName()
    
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
                .background(Color(0xFF4CAF50).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (!userAvatar.isNullOrEmpty()) {
                AsyncImage(
                    model = userAvatar,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
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
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Connected",
                    color = Color(0xFF4CAF50),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLocalSongsToggleItem(
    loadLocalSongs: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!loadLocalSongs) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Load Local Songs",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (loadLocalSongs) "Shows songs from your device" else "YouTube Music only",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = loadLocalSongs,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * Local only mode: the app stays fully offline — device library only, all
 * YouTube features and network calls disabled.
 */
@Composable
private fun ExpressiveLocalOnlyToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Local Only",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) "Offline: device library only, no internet"
                else "YouTube features enabled",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveAmoledThemeToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.Rounded.Contrast,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AMOLED Black",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) "Pure black backgrounds in dark theme" else "Standard dark backgrounds",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ExpressiveAmbientBackgroundToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.Rounded.Palette,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ambient Background",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) "Dynamic colors from album art" else "Solid background",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveVideoModeToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val options = listOf("Music", "Video")
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
                    text = "Content Mode",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) "Showing YouTube videos" else "Showing music content",
                    color = secondaryTextColor,
                    fontSize = 13.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Segmented Button Row
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedIndex == index,
                    onClick = { onToggle(index == 1) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    ),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = selectedIndex == index) {
                            Icon(
                                imageVector = if (index == 0) Icons.Rounded.MusicNote else Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                ) {
                    Text(label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveHomeModeToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.Rounded.ToggleOn,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Home Screen Mode Toggle",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) "Music/video switch shown in the Home top bar" else "Switch modes from Settings only",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveTimedCommentsToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.AutoMirrored.Rounded.Comment,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Timed Comments",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) "Timestamped comments briefly appear over videos" else "Adds a comments button to the video player",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

/**
 * Opt-in toggle for YouTube Shorts, off by default. The subtitle carries a
 * deliberate warning: short-form feeds are engineered to be compulsive, so
 * the user should choose them consciously rather than get them by default.
 */
@Composable
private fun ExpressiveShortsToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Shorts",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) {
                    "Shorts shelf shown in video mode. Mind your screen time"
                } else {
                    "Off by default. Endless short-form feeds are designed to keep you scrolling"
                },
                color = if (enabled) MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    else secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveSaveHistoryToggleItem(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Save Watch History",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (enabled) "Videos saved to your YouTube account" else "Watch history not saved",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

private data class PlayerStyleOption(
    val style: PlayerStyle,
    val label: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val playerStyleOptions = listOf(
    PlayerStyleOption(PlayerStyle.CLASSIC, "Classic", "Button controls for playback", Icons.Rounded.PlayCircle),
    PlayerStyleOption(PlayerStyle.GESTURE, "Gesture", "Swipe album art to navigate", Icons.Rounded.SwipeRight),
    PlayerStyleOption(PlayerStyle.EDITORIAL, "Editorial", "Two-tone magazine layout", Icons.Rounded.Newspaper),
    PlayerStyleOption(PlayerStyle.POSTER, "Canvas", "Full-bleed album art", Icons.Rounded.Wallpaper),
    PlayerStyleOption(PlayerStyle.BENTO, "Bento", "Squishy grid of flat tiles", Icons.Rounded.GridView),
    PlayerStyleOption(PlayerStyle.STICKER, "Sticker", "Die-cut art with toy physics", Icons.Rounded.Interests),
    PlayerStyleOption(PlayerStyle.MORPH, "Morph", "Living shape that breathes", Icons.Rounded.Animation),
    PlayerStyleOption(PlayerStyle.DIAL, "Dial", "Rotary ring, spin to scrub", Icons.Rounded.RadioButtonChecked)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExpressivePlayerStyleSelectItem(
    currentStyle: PlayerStyle,
    onStyleSelected: (PlayerStyle) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val current = playerStyleOptions.firstOrNull { it.style == currentStyle }
        ?: playerStyleOptions.first()

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
                    imageVector = current.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Player Style",
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = current.subtitle,
                    color = secondaryTextColor,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Wrapping chip grid - scales past the point where a segmented row
        // stops fitting.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            playerStyleOptions.forEach { option ->
                FilterChip(
                    selected = currentStyle == option.style,
                    onClick = { onStyleSelected(option.style) },
                    label = { Text(option.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

/** Human-readable label for a stored default video quality value. */
private fun videoQualityLabel(value: String): String =
    if (value == ThemePreferences.VIDEO_QUALITY_AUTO) "Auto (Highest)" else value

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoQualityDialog(
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
                        imageVector = Icons.Rounded.HighQuality,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Default Video Quality",
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
                        text = "Videos start at this quality when available, falling back to the closest lower one.",
                        color = secondaryTextColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    ThemePreferences.VIDEO_QUALITY_OPTIONS.forEach { option ->
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
                                text = videoQualityLabel(option),
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
                        text = "Close",
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
                        contentDescription = "Developer Avatar",
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
                        text = "Koda",
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
                        text = "A modern, open-source music player with YouTube Music integration.",
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
                                label = "Version",
                                value = BuildConfig.VERSION_NAME,
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                            AboutDetailRow(
                                label = "Build",
                                value = BuildConfig.VERSION_CODE.toString(),
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                            AboutDetailRow(
                                label = "Build Type",
                                value = if (BuildConfig.DEBUG) "Debug" else "Release",
                                textColor = textColor,
                                labelColor = secondaryTextColor
                            )
                            AboutDetailRow(
                                label = "Developer",
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
                        text = "Check Updates",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Close",
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
private fun ExpressiveFolderExclusionItem(
    excludedFoldersCount: Int,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (excludedFoldersCount > 0) Color(0xFFE57373).copy(alpha = 0.12f)
                    else accentColor.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (excludedFoldersCount > 0) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (excludedFoldersCount > 0) Color(0xFFE57373) else accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Excluded Folders",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (excludedFoldersCount == 0) "All folders included"
                       else "$excludedFoldersCount folder${if (excludedFoldersCount > 1) "s" else ""} hidden",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Chevron
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = secondaryTextColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                        text = "Excluded Folders",
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Songs from selected folders won't appear in your library",
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
                                        text = "Scanning folders...",
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
                                        text = "No music folders found",
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
                        text = "Done",
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
        color = if (isExcluded) Color(0xFFE57373).copy(alpha = 0.08f) else Color.Transparent
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
                    checkedColor = Color(0xFFE57373),
                    uncheckedColor = secondaryTextColor.copy(alpha = 0.5f),
                    checkmarkColor = Color.White
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Folder icon
            Icon(
                imageVector = if (isExcluded) Icons.Rounded.FolderOff else Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (isExcluded) Color(0xFFE57373) else accentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Folder info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.displayName,
                    color = if (isExcluded) Color(0xFFE57373) else textColor,
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
                        text = "Shorts Buttons",
                        color = textColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap a button to show or hide it in Shorts",
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
                        label = "Like",
                        shown = ThemePreferences.SHORTS_ACTION_LIKE !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_LIKE) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.ThumbDown,
                        label = "Dislike",
                        shown = ThemePreferences.SHORTS_ACTION_DISLIKE !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_DISLIKE) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.ChatBubble,
                        label = "Comments",
                        shown = ThemePreferences.SHORTS_ACTION_COMMENTS !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_COMMENTS) }
                    )
                    ShortsButtonShapeToggle(
                        icon = Icons.Rounded.Share,
                        label = "Share",
                        shown = ThemePreferences.SHORTS_ACTION_SHARE !in hiddenActions,
                        onToggle = { toggle(ThemePreferences.SHORTS_ACTION_SHARE) }
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
                        text = "Done",
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

@Composable
private fun ExpressiveOemToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onToggle(!enabled) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f),
                uncheckedBorderColor = Color.Transparent,
                checkedBorderColor = Color.Transparent
            )
        )
    }
}

private fun isXiaomiDevice(): Boolean {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val brand = android.os.Build.BRAND.lowercase()
    return manufacturer.contains("xiaomi") || brand.contains("xiaomi") || 
           brand.contains("redmi") || brand.contains("poco")
}
