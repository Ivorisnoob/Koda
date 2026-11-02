package com.ivor.ivormusic.data

/**
 * One channel a signed-in Google account can act as: its own personal channel
 * or a brand account it manages.
 *
 * A brand account is not a separate login. It shares the Google account's
 * cookies, and YouTube decides which channel a request is for from the page id
 * sent alongside them ([pageId], null for the personal channel). That is why
 * signing in through the browser always landed on the personal channel: the
 * cookies alone can only ever mean the default identity.
 *
 * [datasyncId] is the same identifier [Profile.datasyncId] stores for a
 * profile, so a channel that already has a row in the roster is recognised
 * instead of added twice.
 */
data class BrandChannel(
    val name: String,
    val handle: String?,
    val avatarUrl: String?,
    val pageId: String?,
    val datasyncId: String?,
    val isSelected: Boolean,
)
