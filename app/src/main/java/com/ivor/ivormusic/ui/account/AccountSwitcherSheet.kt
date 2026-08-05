package com.ivor.ivormusic.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.AccountSwitcher
import com.ivor.ivormusic.data.Profile

/**
 * The profile switcher.
 *
 * Opened from the home avatar, because that is where the current identity is
 * already shown and it sits under the thumb. A bottom sheet rather than a menu:
 * a row here has to carry an avatar, a name, a handle and sometimes a
 * "reconnect" state, which a dropdown cannot hold legibly.
 *
 * Switching is instant and offline - the cookies are already on device - so
 * there is no confirm step and no blocking spinner. The sheet closes on tap and
 * the feeds refill behind it.
 */
@Composable
fun AccountSwitcherSheet(
    onDismiss: () -> Unit,
    onAddYouTubeAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val switcher = remember(context) { AccountSwitcher(context) }
    val profiles by switcher.profiles.collectAsState()
    val activeId by switcher.activeProfileId.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pendingRemoval by remember { mutableStateOf<Profile?>(null) }
    var showAddLocal by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Profiles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            Text(
                text = "Subscriptions and hidden recommendations are kept per profile. " +
                    "Playlists, liked songs and downloads are shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
            )

            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    isActive = profile.id == activeId,
                    // The last profile standing cannot be deleted, but a
                    // YouTube account on it can still be disconnected - that is
                    // the plain "sign out" case, and it must not disappear just
                    // because the roster has one row.
                    canRemove = profiles.size > 1 || !profile.isLocal,
                    onClick = {
                        if (profile.id != activeId) switcher.switchTo(profile.id)
                        onDismiss()
                    },
                    onRemove = { pendingRemoval = profile }
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            AddRow(
                icon = Icons.Rounded.Add,
                title = "Add YouTube account",
                subtitle = "Sign in to another Google account",
                onClick = {
                    onDismiss()
                    onAddYouTubeAccount()
                }
            )
            Spacer(Modifier.height(8.dp))
            AddRow(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Add profile on this device",
                subtitle = "Separate subscriptions, no account needed",
                onClick = { showAddLocal = true }
            )
        }
    }

    if (showAddLocal) {
        NameProfileDialog(
            onConfirm = { name ->
                showAddLocal = false
                switcher.addLocalProfileAndSwitch(name)
                onDismiss()
            },
            onDismiss = { showAddLocal = false }
        )
    }

    pendingRemoval?.let { target ->
        // With one profile left there is nothing to fall back to, so the
        // account is disconnected in place instead of the row being deleted.
        val isLastProfile = profiles.size <= 1
        RemoveProfileDialog(
            profile = target,
            isSignOutOnly = isLastProfile,
            onConfirm = {
                if (isLastProfile) switcher.signOut(target.id) else switcher.remove(target.id)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null }
        )
    }
}

/**
 * One profile.
 *
 * An expired session is called out here rather than failing silently later:
 * before profiles, a dead session showed up only as inexplicably empty
 * account screens, with nothing saying that signing in again was the fix.
 */
@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "profileRowScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(profile = profile, size = 48)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when {
                profile.expired -> "Session expired - tap to reconnect"
                profile.isLocal -> "On this device"
                else -> profile.handle ?: "YouTube account"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profile.expired) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (profile.expired) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        AnimatedVisibility(visible = isActive, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Active profile",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }

        if (canRemove) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Remove ${profile.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * A profile's picture, or a stand-in.
 *
 * A local profile has no avatar by definition, so it gets an icon rather than
 * a broken image - and an expired account is ringed in the error color, so the
 * state is legible from the home avatar too, not only inside this sheet.
 */
@Composable
fun ProfileAvatar(profile: Profile, size: Int, modifier: Modifier = Modifier) {
    val ringColor = if (profile.expired) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(width = if (profile.expired) 2.dp else 0.dp, color = ringColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val avatar = profile.avatarUrl
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Icon(
                imageVector = if (profile.isLocal) Icons.Rounded.PhoneAndroid
                else Icons.Rounded.PersonOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.5f).dp)
            )
        }
    }
}

@Composable
private fun AddRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "addRowScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NameProfileDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = { Text("New profile on this device") },
        text = {
            Column {
                Text(
                    text = "It gets its own subscriptions and hidden recommendations, " +
                        "with no Google account involved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RemoveProfileDialog(
    profile: Profile,
    isSignOutOnly: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(32.dp),
        title = {
            Text(if (isSignOutOnly) "Sign out of ${profile.name}?" else "Remove ${profile.name}?")
        },
        text = {
            Text(
                text = when {
                    isSignOutOnly ->
                        "The account is disconnected from this device. Your subscriptions " +
                            "and hidden recommendations stay, and nothing changes on YouTube."
                    profile.isLocal ->
                        "Its subscriptions and hidden recommendations are deleted. " +
                            "Playlists, liked songs and downloads are shared and stay."
                    else ->
                        "Signs this account out on this device and deletes its subscriptions " +
                            "and hidden recommendations. Nothing changes on YouTube itself."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (isSignOutOnly) "Sign out" else "Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
