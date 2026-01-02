package com.ivor.ivormusic.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val contentDescription: String
)

@Composable
fun FloatingPillNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isDarkMode: Boolean = true
) {
    val navItems = listOf(
        NavItem(
            label = "Your Mix",
            selectedIcon = Icons.Rounded.Home,
            unselectedIcon = Icons.Rounded.Home,
            contentDescription = "Your Mix"
        ),
        NavItem(
            label = "Search",
            selectedIcon = Icons.Filled.Search,
            unselectedIcon = Icons.Outlined.Search,
            contentDescription = "Search"
        ),
        NavItem(
            label = "Library",
            selectedIcon = Icons.Filled.LibraryMusic,
            unselectedIcon = Icons.Outlined.LibraryMusic,
            contentDescription = "Library"
        )
    )

    // Colors based on theme - more transparent for floating effect
    val containerColor = if (isDarkMode) Color(0xE61A1A1A) else Color(0xE6F5F5F5)
    val selectedBgColor = if (isDarkMode) Color(0xFF3D5AFE) else Color(0xFF6200EE)
    val selectedContentColor = Color.White
    val unselectedContentColor = if (isDarkMode) Color(0xFF9E9E9E) else Color(0xFF757575)

    // Floating pill - no fillMaxWidth, just wraps content
    Surface(
        modifier = modifier
            .padding(bottom = 16.dp)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f)
            )
            .clip(RoundedCornerShape(32.dp)),
        color = containerColor,
        shape = RoundedCornerShape(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                NavBarItem(
                    item = item,
                    isSelected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    selectedBgColor = selectedBgColor,
                    selectedContentColor = selectedContentColor,
                    unselectedContentColor = unselectedContentColor
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedBgColor: Color,
    selectedContentColor: Color,
    unselectedContentColor: Color,
    modifier: Modifier = Modifier
) {
    // Animated background color
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedBgColor else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bgColor"
    )

    // Animated content color
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) selectedContentColor else unselectedContentColor,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "contentColor"
    )

    // Animated padding for selection indicator
    val horizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 20.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "padding"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = selectedBgColor),
                onClick = onClick
            )
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}
