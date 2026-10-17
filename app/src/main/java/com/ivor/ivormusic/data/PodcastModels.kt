package com.ivor.ivormusic.data

import kotlinx.serialization.Serializable

/**
 * A subscribed RSS podcast feed.
 *
 * Persisted as JSON by [PodcastRepository]; the [id] is the feed URL's
 * stable hash so re-adding the same URL is idempotent.
 */
@Serializable
data class PodcastFeed(
    val id: String,
    val title: String,
    val feedUrl: String,
    val siteUrl: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * One episode of a [PodcastFeed].
 *
 * The [id] is the episode GUID when the feed supplies one, otherwise the
 * enclosure/audio URL. Episodes are immutable snapshots of the feed at
 * fetch time; refreshing a feed replaces its episode rows.
 */
@Serializable
data class PodcastEpisode(
    val id: String,
    val feedId: String,
    val title: String,
    val audioUrl: String,
    val artworkUrl: String? = null,
    val description: String? = null,
    /** Publication time, epoch millis. 0 when the feed gives no date. */
    val pubDate: Long = 0L,
    /** Duration in milliseconds. 0 when unknown. */
    val durationMs: Long = 0L,
)

/**
 * The parsed result of fetching one RSS feed: the channel metadata plus
 * its episode list.
 */
data class ParsedPodcastFeed(
    val title: String,
    val siteUrl: String?,
    val description: String?,
    val artworkUrl: String?,
    val episodes: List<ParsedPodcastEpisode>,
)

data class ParsedPodcastEpisode(
    val guid: String?,
    val title: String,
    /** Always present: [PodcastRssParser] drops items without playable audio. */
    val audioUrl: String,
    val artworkUrl: String?,
    val description: String?,
    val pubDate: Long,
    val durationMs: Long,
)
