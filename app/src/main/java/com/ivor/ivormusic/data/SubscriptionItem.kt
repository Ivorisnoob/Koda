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
    val handle: String? = null,
    /**
     * The account bell for this channel, from its FEchannels row. Null for a
     * device-only follow, which has no account bell, and for a row that did not
     * carry one.
     */
    val bell: ChannelBell? = null
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
    /** Where a tap goes; null when the item named nowhere Koda can open. */
    val target: NotificationTarget?,
    val isRead: Boolean,
    /**
     * YouTube's `notificationId`: a string, unique per item [verified October
     * 2026]. What [NotificationHistoryStore] remembers and hides an item by.
     * Blank only for an item that carried none, which is then never stored.
     */
    val id: String = "",
    /** When this device first saw the item. Set by [NotificationHistoryStore]. */
    val firstSeenMs: Long = 0L,
    /**
     * No longer in YouTube's inbox, shown from this device's memory of it.
     * [sentTime] is then stale ("3 hours ago", said days ago) and the row
     * draws a time from [firstSeenMs] instead.
     */
    val remembered: Boolean = false
)

/**
 * What a notification opens. [verified September 2026, signed in] An inbox
 * item's `navigationEndpoint` is a `watchEndpoint` (uploads, lives), a
 * `reelWatchEndpoint` (Shorts) or a `browseEndpoint` to `FEpost_detail`
 * (community posts), and every one carries its web URL in
 * `commandMetadata.webCommandMetadata.url`.
 */
sealed interface NotificationTarget {
    data class Video(val videoId: String) : NotificationTarget
    data class Short(val videoId: String) : NotificationTarget
    /**
     * A community post, by the `params` of its `FEpost_detail` page. [verified
     * October 2026, signed in] "X posted ..." items carry a `browseEndpoint`
     * with that browse id and its params, and the page they open holds the
     * post itself as one `backstagePostRenderer` beside its comment section,
     * so the post opens in Koda with its comments. [url] is the way out when
     * the page cannot be read.
     */
    data class Post(val detailParams: String, val url: String?) : NotificationTarget
    /**
     * Anything else, as its youtube.com URL - a channel ("New subscriber"),
     * which the in-app link handler opens here, or something Koda has no
     * screen for, which it passes on to the YouTube app or the browser.
     */
    data class Link(val url: String) : NotificationTarget
}
