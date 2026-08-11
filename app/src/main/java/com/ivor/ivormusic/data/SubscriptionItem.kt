package com.ivor.ivormusic.data

/**
 * A channel the logged-in user is subscribed to, parsed from the FEchannels
 * browse response (channelRenderer items).
 */
data class SubscribedChannel(
    val channelId: String,
    val name: String,
    val avatarUrl: String?,
    val subscriberCountText: String?,  // e.g. "701K subscribers"
    /**
     * "@handle" when known. Searchable, never used for fetching - every call
     * in this app keys off the canonical UC id.
     *
     * Both sources carry one: FEchannels puts it in the renderer's
     * `subscriberCountText` (verified August 2026, see getSubscribedChannels),
     * and a device-local follow brings its own. Without that symmetry, typing
     * an @handle would find device channels and silently miss account ones,
     * which is the kind of gap people report as a bug rather than a limit.
     */
    val handle: String? = null
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
