package com.ivor.ivormusic.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.UpdateRepository
import com.ivor.ivormusic.data.UpdateResult
import com.ivor.ivormusic.ui.auth.YouTubeAuthDialog
import com.ivor.ivormusic.ui.theme.ThemeMode
import kotlinx.coroutines.delay

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
    loadLocalSongs: Boolean,
    onLoadLocalSongsToggle: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
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
                AnimatedSettingsSection(
                    title = "Appearance",
                    textColor = secondaryTextColor,
                    visible = isVisible,
                    delay = 0
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveThemeSelectGroup(
                            currentMode = currentThemeMode,
                            onModeSelected = onThemeModeChange,
                            textColor = textColor,
                            accentColor = accentColor
                        )
                    }
                }
            }

            // YouTube Music Section
            item {
                AnimatedSettingsSection(
                    title = "YouTube Music",
                    textColor = secondaryTextColor,
                    visible = isVisible,
                    delay = 50
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        if (isLoggedIn) {
                            // Logged in state
                            ExpressiveSettingsItem(
                                icon = Icons.Rounded.AccountCircle,
                                title = "YouTube Account",
                                subtitle = "Connected ✓",
                                onClick = { },
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                iconTint = Color(0xFF4CAF50)
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

            // Library Section
            item {
                AnimatedSettingsSection(
                    title = "Library",
                    textColor = secondaryTextColor,
                    visible = isVisible,
                    delay = 100
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveLocalSongsToggleItem(
                            loadLocalSongs = loadLocalSongs,
                            onToggle = onLoadLocalSongsToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )
                    }
                }
            }

            // About Section
            item {
                AnimatedSettingsSection(
                    title = "About",
                    textColor = secondaryTextColor,
                    visible = isVisible,
                    delay = 150
                ) {
                    ExpressiveSettingsCard(surfaceColor = surfaceColor) {
                        ExpressiveSettingsItem(
                            icon = Icons.Rounded.Info,
                            title = "IvorMusic",
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
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun AnimatedSettingsSection(
    title: String,
    textColor: Color,
    visible: Boolean,
    delay: Int,
    content: @Composable () -> Unit
) {
    var sectionVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(visible) {
        if (visible) {
            delay(delay.toLong())
            sectionVisible = true
        }
    }
    
    AnimatedVisibility(
        visible = sectionVisible,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    ) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveAboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    val githubReleasesUrl = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest"
    val developerAvatarUrl = "https://github.com/${BuildConfig.GITHUB_USERNAME}.png"
    
    // Update check state
    val updateRepository = remember { UpdateRepository() }
    var updateResult by remember { mutableStateOf<UpdateResult>(UpdateResult.Checking) }
    
    // Check for updates on dialog open
    LaunchedEffect(Unit) {
        updateResult = updateRepository.checkForUpdate(
            repoPath = BuildConfig.GITHUB_REPO,
            currentVersion = BuildConfig.VERSION_NAME
        )
    }
    
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
                        text = "IvorMusic",
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
                    
                    // Update status indicator
                    when (val result = updateResult) {
                        is UpdateResult.Checking -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = secondaryTextColor.copy(alpha = 0.1f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = secondaryTextColor
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Checking for updates...",
                                        color = secondaryTextColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                        is UpdateResult.UpdateAvailable -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Update available: v${result.latestVersion}",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        is UpdateResult.UpToDate -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = primaryColor.copy(alpha = 0.1f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "You're up to date!",
                                        color = primaryColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        else -> { /* NoReleases or Error - don't show anything */ }
                    }
                    
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubReleasesUrl))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Get Latest",
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
