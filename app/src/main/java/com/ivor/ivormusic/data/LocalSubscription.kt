package com.ivor.ivormusic.data

/**
 * A channel the user follows *on this device*, with no YouTube account
 * involved. The device-local mirror of [SubscribedChannel], which only ever
 * describes what the signed-in Google account is subscribed to.
 *
 * [channelId] is always the canonical UC... id: handles and vanity URLs are
 * resolved before anything reaches this store, because the RSS feed endpoint
 * and the channel browse both key off the UC id and a stored @handle would
 * force a resolve on every feed refresh.
 */
data class LocalSubscription(
    val channelId: String,
    val name: String,
    val avatarUrl: String? = null,
    /** "@handle" when known - display only, never used for fetching. */
    val handle: String? = null,
    val subscribedAt: Long = System.currentTimeMillis()
) {
    /** Adapter so local channels can reuse the YouTube-account channel UI. */
    fun toSubscribedChannel(): SubscribedChannel = SubscribedChannel(
        channelId = channelId,
        name = name,
        avatarUrl = avatarUrl,
        // A local follow has no subscriber count to show, so the row's second
        // line carries the handle instead. It also travels in its own field,
        // because search needs it separately from whatever is being displayed.
        subscriberCountText = handle,
        handle = handle
    )
}

/**
 * A user-defined bundle of local subscriptions, used to filter the feed
 * ("Music", "Tech", ...). Groups only ever reference channel ids; a channel
 * may live in any number of groups, or none.
 */
data class SubscriptionGroup(
    val id: String,
    val name: String,
    val channelIds: List<String> = emptyList()
)

/**
 * Where a subscription is stored. Read by the subscribe button and by the
 * Subscriptions tab; see [ThemePreferences.subscriptionSource] and
 * [ThemePreferences.subscribeTarget].
 */
enum class SubscriptionStore { LOCAL, YOUTUBE, BOTH }

/**
 * A channel parsed out of an import file, before it has been resolved to a
 * canonical UC... id. [channelId] is null for entries that only carried a
 * handle or vanity URL, which need a network resolve before they can be
 * stored.
 */
data class ImportedChannel(
    val channelId: String?,
    val name: String,
    /** "@handle", "c/vanity" or "user/legacy" - whatever the file gave us. */
    val unresolvedPath: String? = null,
    val avatarUrl: String? = null
)

/** Outcome of an import run, surfaced to the user as a summary. */
data class SubscriptionImportResult(
    val added: Int,
    val alreadyPresent: Int,
    val unresolved: Int,
    val skippedOtherService: Int = 0,
    val error: String? = null
) {
    val total: Int get() = added + alreadyPresent + unresolved
}
