package com.ivor.ivormusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.ui.auth.YouTubeAuthDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onBackClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    
    // Check actual login status
    var isLoggedIn by remember { mutableStateOf(sessionManager.isLoggedIn()) }
    
    val backgroundColor = if (isDarkMode) Color.Black else Color(0xFFF8F8F8)
    val surfaceColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB3B3B3) else Color(0xFF666666)
    val accentColor = if (isDarkMode) Color(0xFF3D5AFE) else Color(0xFF6200EE)

    // Dialog state for YouTube auth
    var showAuthDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(contentPadding)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "Settings",
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            item {
                SettingsSection(title = "Appearance", textColor = secondaryTextColor) {
                    SettingsCard(surfaceColor = surfaceColor) {
                        ThemeToggleItem(
                            isDarkMode = isDarkMode,
                            onToggle = onThemeToggle,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            accentColor = accentColor
                        )
                    }
                }
            }

            // YouTube Music Section
            item {
                SettingsSection(title = "YouTube Music", textColor = secondaryTextColor) {
                    SettingsCard(surfaceColor = surfaceColor) {
                        if (isLoggedIn) {
                            // Logged in state
                            SettingsItem(
                                icon = Icons.Rounded.AccountCircle,
                                title = "YouTube Account",
                                subtitle = "Connected ✓",
                                onClick = { },
                                textColor = textColor,
                                secondaryTextColor = secondaryTextColor,
                                iconTint = Color(0xFF4CAF50)
                            )
                            SettingsDivider(isDarkMode = isDarkMode)
                            SettingsItem(
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
                            SettingsItem(
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

            // About Section
            item {
                SettingsSection(title = "About", textColor = secondaryTextColor) {
                    SettingsCard(surfaceColor = surfaceColor) {
                        SettingsItem(
                            icon = Icons.Rounded.MusicNote,
                            title = "IvorMusic",
                            subtitle = "Version 1.0.0",
                            onClick = { },
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            iconTint = accentColor
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
                isLoggedIn = true  // Update UI immediately
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    textColor: Color,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title.uppercase(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsCard(
    surfaceColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = surfaceColor,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            content()
        }
    }
}

@Composable
private fun ThemeToggleItem(
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dark Mode",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isDarkMode) "On" else "Off",
                color = secondaryTextColor,
                fontSize = 13.sp
            )
        }

        // Switch
        Switch(
            checked = isDarkMode,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accentColor,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    showChevron: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
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
private fun SettingsDivider(isDarkMode: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFEEEEEE))
    )
}
