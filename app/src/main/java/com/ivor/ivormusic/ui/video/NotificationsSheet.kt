package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.NotificationItem
import com.ivor.ivormusic.data.NotificationTarget

/** What a notification is about, which is also how the inbox is filtered. */
private enum class NotificationKind { VIDEO, SHORT, POST, OTHER }

private val NotificationItem.kind: NotificationKind
    get() = when (target) {
        is NotificationTarget.Video -> NotificationKind.VIDEO
        is NotificationTarget.Short -> NotificationKind.SHORT
        is NotificationTarget.Post -> NotificationKind.POST
        is NotificationTarget.Link, null -> NotificationKind.OTHER
    }

/**
 * The notification inbox: what YouTube currently lists, and under it what this
 * device remembers from before YouTube dropped it.
 *
 * YouTube's own inbox is about a dozen recent items with nothing older to
 * fetch, so the sheet has three parts - New, Earlier, and Kept on this device -
 * and a row of kind filters, because a week of history is mostly Shorts
 * uploads with the one post somebody was looking for somewhere among them.
 *
 * A swipe hides a notification on this device only; the footer brings the
 * hidden ones back, which is the undo. A tap opens the item's
 * [NotificationTarget]: a video in the player, a Short in the Shorts player, a
 * post over its comments, anything else as its link.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NotificationsSheet(
    notifications: List<NotificationItem>,
    isLoading: Boolean,
    hiddenCount: Int,
    onNotificationClick: (NotificationItem) -> Unit,
    onHide: (NotificationItem) -> Unit,
    onRestoreHidden: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    // Null is "All". Saved by name, so a rotation keeps the filter.
    var filterName by rememberSaveable { mutableStateOf<String?>(null) }
    val counts = remember(notifications) { notifications.groupingBy { it.kind }.eachCount() }
    // A filter whose last item was just hidden falls back to All rather than
    // leaving an empty list under a chip that is no longer drawn.
    val filter = NotificationKind.entries.firstOrNull { it.name == filterName && it in counts }
    val shown = remember(notifications, filter) {
        if (filter == null) notifications else notifications.filter { it.kind == filter }
    }
    val unread = notifications.count { !it.isRead && !it.remembered }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_notifications),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (unread > 0) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = stringResource(R.string.ns_new_count, unread),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // The first load has the big indicator below; this one is for
                // a refresh over a list that is already showing.
                if (isLoading && notifications.isNotEmpty()) {
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                    }
                } else {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.ns_refresh)
                        )
                    }
                }
            }

            // Only worth drawing once there is more than one kind to choose between.
            if (counts.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NotificationFilterChip(
                        label = stringResource(R.string.ns_filter_all),
                        count = notifications.size,
                        selected = filter == null,
                        onClick = { filterName = null }
                    )
                    NotificationKind.entries.forEach { kind ->
                        val count = counts[kind] ?: return@forEach
                        NotificationFilterChip(
                            label = stringResource(kind.label),
                            count = count,
                            selected = filter == kind,
                            onClick = { filterName = kind.name }
                        )
                    }
                }
            }

            when {
                isLoading && notifications.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                notifications.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.ns_none_yet),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Everything hidden is not the same as nothing arriving.
                        if (hiddenCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            HiddenNotificationsButton(hiddenCount, onRestoreHidden)
                        }
                    }
                }
                else -> {
                    val fresh = shown.filterNot { it.remembered }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notificationSection(
                            title = R.string.ns_section_new,
                            items = fresh.filterNot { it.isRead },
                            onClick = onNotificationClick,
                            onHide = onHide
                        )
                        notificationSection(
                            title = R.string.ns_section_earlier,
                            items = fresh.filter { it.isRead },
                            onClick = onNotificationClick,
                            onHide = onHide
                        )
                        notificationSection(
                            title = R.string.ns_section_kept,
                            note = R.string.ns_section_kept_note,
                            items = shown.filter { it.remembered },
                            onClick = onNotificationClick,
                            onHide = onHide
                        )
                        if (hiddenCount > 0) {
                            item(key = "hidden_footer", contentType = "footer") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    HiddenNotificationsButton(hiddenCount, onRestoreHidden)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val NotificationKind.label: Int
    get() = when (this) {
        NotificationKind.VIDEO -> R.string.ns_filter_videos
        NotificationKind.SHORT -> R.string.ns_filter_shorts
        NotificationKind.POST -> R.string.ns_filter_posts
        NotificationKind.OTHER -> R.string.ns_filter_other
    }

private val NotificationKind.icon: ImageVector
    get() = when (this) {
        NotificationKind.VIDEO -> Icons.Rounded.PlayArrow
        NotificationKind.SHORT -> Icons.Rounded.Bolt
        NotificationKind.POST -> Icons.Rounded.Forum
        NotificationKind.OTHER -> Icons.Rounded.Person
    }

/** One headed run of notifications; draws nothing at all when it has none. */
private fun LazyListScope.notificationSection(
    title: Int,
    items: List<NotificationItem>,
    onClick: (NotificationItem) -> Unit,
    onHide: (NotificationItem) -> Unit,
    note: Int? = null,
) {
    if (items.isEmpty()) return
    item(key = "header_$title", contentType = "header") {
        Column(modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (note != null) {
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    // An item with no id cannot be hidden or told apart, so it is keyed by
    // where it sits; every other row keeps its identity through a removal.
    items(
        items = items,
        key = { item -> item.id.ifBlank { "anon_${item.message.hashCode()}_${item.sentTime}" } },
        contentType = { "notification" }
    ) { notification ->
        Box(modifier = Modifier.animateItem()) {
            HideableNotificationRow(
                notification = notification,
                onClick = { onClick(notification) },
                onHide = { onHide(notification) }
            )
        }
    }
}

@Composable
private fun NotificationFilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    FilterChip(
        selected = selected,
        onClick = {
            if (!selected) haptics.tick()
            onClick()
        },
        label = { Text(stringResource(R.string.ns_filter_with_count, label, count)) },
        shape = CircleShape
    )
}

@Composable
private fun HiddenNotificationsButton(count: Int, onRestore: () -> Unit) {
    TextButton(onClick = onRestore) {
        Icon(
            imageVector = Icons.Rounded.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            androidx.compose.ui.res.pluralStringResource(R.plurals.ns_hidden_restore, count, count)
        )
    }
}

/** A row that a swipe towards the start hides. One with no id is drawn but stays put. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HideableNotificationRow(
    notification: NotificationItem,
    onClick: () -> Unit,
    onHide: () -> Unit
) {
    if (notification.id.isBlank()) {
        NotificationRow(notification, onClick)
        return
    }
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val hide by rememberUpdatedState(onHide)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.confirm()
                hide()
                true
            } else {
                false
            }
        }
    )
    // [scar] A lazy list keeps each key's saved state after its row leaves, and
    // the swipe state is saved state: a notification brought back by "show
    // them again" came back still swiped away, as an empty "Hide" strip that
    // stayed. A row that is composed is by definition not hidden, so it starts
    // settled whatever was saved for it.
    LaunchedEffect(notification.id) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ns_hide),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    ) {
        // Opaque, or the "Hide" behind it shows through a resting row.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            NotificationRow(notification, onClick)
        }
    }
}

@Composable
private fun NotificationRow(
    notification: NotificationItem,
    onClick: () -> Unit
) {
    val unread = !notification.isRead && !notification.remembered
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = notification.target != null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Unread dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (unread) MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Transparent
                )
        )

        // Channel avatar, with what the notification is about in its corner:
        // the kind is otherwise only in the wording, which is not scannable.
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (notification.channelAvatarUrl != null) {
                    AsyncImage(
                        model = notification.channelAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = notification.kind.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            // YouTube's own "3 hours ago" is only true while YouTube is still
            // listing the item; a remembered one is timed from when it was seen.
            val time = if (notification.remembered && notification.firstSeenMs > 0L) {
                remember(notification.firstSeenMs) {
                    DateUtils.getRelativeTimeSpanString(
                        notification.firstSeenMs,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    ).toString()
                }
            } else {
                notification.sentTime
            }
            if (time.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (notification.videoThumbnailUrl != null) {
            AsyncImage(
                model = notification.videoThumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
