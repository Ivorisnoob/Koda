package com.ivor.ivormusic.data

import kotlin.random.Random

/**
 * What one subscribed channel contributes to the shuffled Home feed.
 *
 * [recent] is the first page of the Videos tab (the latest 30 uploads) and
 * [catalogue] is the Popular order, which spans the channel's whole history.
 * Both come from the requests a channel page already makes, two per channel.
 */
data class ChannelMixPool(
    val channelId: String,
    val recent: List<VideoItem>,
    val catalogue: List<VideoItem>
) {
    val isEmpty: Boolean get() = recent.isEmpty() && catalogue.isEmpty()
}

/**
 * The shuffled Home feed shown when video recommendations are off.
 *
 * With recommendations off, Home used to show the Subscriptions feed itself -
 * the same newest-first list as the Subscriptions tab one tap away. A tester
 * asked for the opposite of a date order: videos from the channels they follow,
 * from any point in those channels' histories, in no particular order. So Home
 * samples each channel's recent uploads *and* its all-time Popular page, favours
 * the catalogue two to one so a four-year-old upload is as likely as last
 * week's, and interleaves creators so one prolific channel cannot fill a
 * screen.
 *
 * Pure and deterministic for a given [Random], so the sampling rules are tested
 * on the JVM rather than eyeballed on a device.
 */
object SubscriptionMix {

    /** Videos a channel contributes to one page, before repeats run out. */
    const val VIDEOS_PER_CHANNEL = 3

    /**
     * One page: up to [perChannel] unseen videos from each pool, catalogue
     * first, then spread so the same creator is not placed twice in a row
     * whenever another creator is available.
     *
     * A video id already in [shownIds] or picked earlier on this page is never
     * picked again, which also keeps a collaboration upload that appears in
     * two channels' pools from showing twice.
     */
    fun buildPage(
        pools: List<ChannelMixPool>,
        shownIds: Set<String>,
        random: Random,
        perChannel: Int = VIDEOS_PER_CHANNEL
    ): List<VideoItem> {
        val taken = HashSet(shownIds)
        val picked = mutableListOf<VideoItem>()
        for (pool in pools) {
            val fromCatalogue = (perChannel * 2 + 2) / 3
            val fromRecent = perChannel - fromCatalogue
            val chosen = mutableListOf<VideoItem>()
            chosen += sample(pool.catalogue, fromCatalogue, taken, random)
            chosen += sample(pool.recent, fromRecent, taken, random)
            // A channel with a thin catalogue (or no Popular order at all)
            // still contributes its share from whichever list has more.
            if (chosen.size < perChannel) {
                chosen += sample(pool.catalogue + pool.recent, perChannel - chosen.size, taken, random)
            }
            picked += chosen
        }
        return spreadCreators(picked.shuffled(random), random)
    }

    /** Whether any pool still holds a video that has not been shown. */
    fun hasUnseen(pools: Collection<ChannelMixPool>, shownIds: Set<String>): Boolean =
        pools.any { pool ->
            pool.recent.any { it.videoId !in shownIds } || pool.catalogue.any { it.videoId !in shownIds }
        }

    /**
     * Reorder so no two neighbours share a creator where that is possible.
     *
     * Greedy: at each position take a random remaining video whose channel
     * differs from the previous one, preferring the creator with the most
     * videos left so the tail does not end as a run of one channel.
     */
    fun spreadCreators(videos: List<VideoItem>, random: Random): List<VideoItem> {
        if (videos.size < 3) return videos
        val byChannel = videos.groupBy { it.channelId ?: it.channelName }
            .mapValues { (_, list) -> ArrayDeque(list) }
            .toMutableMap()
        val result = ArrayList<VideoItem>(videos.size)
        var previous: String? = null
        while (byChannel.isNotEmpty()) {
            val candidates = byChannel.keys.filter { it != previous }.ifEmpty { byChannel.keys.toList() }
            val most = candidates.maxOf { byChannel.getValue(it).size }
            val key = candidates.filter { byChannel.getValue(it).size == most }.random(random)
            val queue = byChannel.getValue(key)
            result += queue.removeFirst()
            if (queue.isEmpty()) byChannel.remove(key)
            previous = key
        }
        return result
    }

    private fun sample(
        source: List<VideoItem>,
        count: Int,
        taken: MutableSet<String>,
        random: Random
    ): List<VideoItem> {
        if (count <= 0) return emptyList()
        val chosen = source.filter { it.videoId !in taken }
            .distinctBy { it.videoId }
            .shuffled(random)
            .take(count)
        chosen.forEach { taken += it.videoId }
        return chosen
    }
}
