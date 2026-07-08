package com.ivor.ivormusic.data

/**
 * A channel the logged-in user is subscribed to, parsed from the FEchannels
 * browse response (channelRenderer items).
 */
data class SubscribedChannel(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCountText: String?  // e.g. "701K subscribers"
)

/**
 * One entry of the user's notification inbox, parsed from
 * notification/get_notification_menu (notificationRenderer items).
 */
data class NotificationItem(
    val message: String,             // e.g. "penguinz0 uploaded: ..."
    val sentTime: String,            // e.g. "3 hours ago"
    val channelAvatarUrl: String?,
    val videoThumbnailUrl: String?,
    val videoId: String?,            // null for non-video notifications
    val isRead: Boolean
)
