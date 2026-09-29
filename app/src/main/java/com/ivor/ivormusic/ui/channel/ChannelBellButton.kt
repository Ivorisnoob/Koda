package com.ivor.ivormusic.ui.channel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.BellLevel
import com.ivor.ivormusic.data.ChannelBell
import com.ivor.ivormusic.util.rememberKodaHaptics

/**
 * The account bell beside a Subscribe button: YouTube's All / Personalized / None
 * for [bell]'s channel.
 *
 * Drawn only for an account subscription whose response carried a bell, so the
 * caller hides it rather than passing a disabled one. It wears the subscribed
 * button's colours at the same height, because it belongs to that state - it is
 * the second half of "Subscribed", not a separate action. A tap opens the three
 * levels; nothing changes until one is picked, since a bell cycling on each tap
 * would write to the account three times on the way to the level wanted.
 *
 * [busy] disables it while a write is in flight. [size] lets the dense
 * subscriptions list use a smaller target than the headers.
 */
@Composable
fun ChannelBellButton(
    bell: ChannelBell,
    channelName: String,
    onLevelChosen: (BellLevel) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    size: Dp = 44.dp,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = rememberKodaHaptics()
    val levelLabel = bell.level?.let { stringResource(it.labelRes()) }
    val description = stringResource(
        R.string.bell_content_description,
        channelName,
        levelLabel ?: stringResource(R.string.bell_level_unknown)
    )

    Box(modifier) {
        Surface(
            onClick = {
                haptics.tick()
                menuOpen = true
            },
            enabled = !busy,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = description }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = bell.level,
                    transitionSpec = { (scaleIn(initialScale = 0.7f) + fadeIn()) togetherWith fadeOut() },
                    label = "bellLevel"
                ) { level ->
                    Icon(
                        imageVector = level.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(if (size < 44.dp) 20.dp else 22.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = MaterialTheme.shapes.large,
        ) {
            BellLevel.entries.filter { bell.choices.containsKey(it) }.forEach { level ->
                val selected = level == bell.level
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(level.labelRes()),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = { Icon(level.icon(), contentDescription = null) },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        menuOpen = false
                        if (!selected) onLevelChosen(level)
                    }
                )
            }
        }
    }
}

private fun BellLevel?.icon(): ImageVector = when (this) {
    BellLevel.ALL -> Icons.Rounded.NotificationsActive
    BellLevel.NONE -> Icons.Rounded.NotificationsOff
    // Personalized, and a bell whose level the response did not say: the plain
    // bell, which is also what YouTube draws for Personalized.
    BellLevel.PERSONALIZED, null -> Icons.Rounded.Notifications
}

internal fun BellLevel.labelRes(): Int = when (this) {
    BellLevel.ALL -> R.string.bell_level_all
    BellLevel.PERSONALIZED -> R.string.bell_level_personalized
    BellLevel.NONE -> R.string.bell_level_none
}
