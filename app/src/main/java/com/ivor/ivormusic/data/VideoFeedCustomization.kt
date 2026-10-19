package com.ivor.ivormusic.data

/**
 * What the video feeds are allowed to show, as one snapshot. The defaults are
 * the feeds as they were before any of this was a setting.
 *
 * These are filters over what was fetched, never a change to the fetch: a
 * switch put back on has to bring its rows back on the next frame, not on the
 * next refresh. The one exception is posts, which are fetched on demand and so
 * are simply not asked for while they are off.
 *
 * The Shorts shelf and the Subscriptions feed's own "hide watched" are older
 * settings with their own keys; the Video feed page shows them beside these
 * but they are not stored here.
 */
data class VideoFeedCustomization(
    /** Community posts scattered through Home and Subscriptions. */
    val showPosts: Boolean = true,
    /** Live streams among the videos of Home and Subscriptions. */
    val showLive: Boolean = true,
    /** Leave out of Home anything already in this profile's watch history. */
    val hideWatchedOnHome: Boolean = false,
)
