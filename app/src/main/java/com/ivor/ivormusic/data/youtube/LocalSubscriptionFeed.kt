package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ImportedChannel
import com.ivor.ivormusic.data.LocalSubscription
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.YouTubeRateLimit
import com.ivor.ivormusic.data.YouTubeRateLimitedException
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Outcome of one Atom feed fetch, kept richer than a list because
 * [getLocalSubscriptionsFeed]'s browse fallback is correct for one of these
 * failures and actively harmful for the other. See there.
 */
sealed interface ChannelFeedResult {
    data class Items(val videos: List<VideoItem>) : ChannelFeedResult

    /**
     * The feed answered, but not with a feed - 404/410, or anything else
     * that is a verdict on this channel rather than on this device. The
     * browse fallback is exactly right here and is why it exists.
     */
    data object NoFeed : ChannelFeedResult

    /** HTTP 429. Escalating to a ~1 MB browse would make this worse. */
    data object RateLimited : ChannelFeedResult

    /** Network or parse failure - indistinguishable from a dead channel. */
    data object Failed : ChannelFeedResult
}

/**
 * Local subscriptions: following channels with no YouTube account.
 *
 * The account-backed feed (`FEsubscriptions`, in [VideoFeeds]) is one browse
 * call for the whole feed because YouTube assembles it server side. Nobody
 * assembles a device-local feed, so it is built here by fetching each followed
 * channel and merging - which makes the cost per refresh linear in the number
 * of subscriptions, and makes the choice of per-channel source the single most
 * important decision in this class: the Atom feed first, a channel browse only
 * for a channel that has no feed, and never as the answer to a 429.
 */
internal class LocalSubscriptionFeed(
    private val http: YouTubeHttp,
    private val channelPages: ChannelPages,
) {
    suspend fun getLocalSubscriptionsFeed(
        channels: List<LocalSubscription>,
        fastMode: Boolean = true,
        maxPerChannel: Int = MAX_FEED_ITEMS_PER_CHANNEL,
        maxTotal: Int = MAX_FEED_ITEMS,
        /** True when the user asked for this refresh, so it must revalidate. */
        forceFresh: Boolean = false,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyList()
        // Refuse before spending a single request. A refresh taken during a
        // hold is the exact loop that deepens one: blocked feed reads as empty,
        // user pulls to refresh, N more requests.
        if (YouTubeRateLimit.isHeld()) {
            throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
        }
        val gate = kotlinx.coroutines.sync.Semaphore(FEED_CONCURRENCY)
        val completed = java.util.concurrent.atomic.AtomicInteger(0)
        val total = channels.size

        val perChannel = kotlinx.coroutines.coroutineScope {
            channels.map { channel ->
                async {
                    gate.acquire()
                    try {
                        // A sibling already hit the limit. The shared state is
                        // the coordination channel here rather than cancelling
                        // the scope, so the channels that already succeeded
                        // keep their results instead of being thrown away.
                        if (YouTubeRateLimit.isHeld()) return@async emptyList()
                        val videos = if (fastMode) {
                            // RSS is not universally available: some channels
                            // 404 on the feed URL YouTube itself advertises in
                            // their own channelMetadataRenderer.rssUrl, uploads
                            // and all (verified August 2026). Treating that as
                            // "no uploads" silently drops the channel from the
                            // feed, and for someone following only a handful it
                            // empties the tab and reads as a network failure.
                            // So fast mode means "RSS, else browse", not "RSS
                            // or nothing" - the fallback costs a request only
                            // for the channels that actually need it.
                            //
                            // The one refusal it must not answer is 429. That
                            // is a verdict on this device rather than on this
                            // channel, a browse will be refused too, and doing
                            // it for every channel turns a 10 MB refresh into a
                            // 200 MB one aimed at a server that just said stop.
                            when (val feed = fetchChannelFeedRss(
                                channel.channelId,
                                channel.avatarUrl,
                                forceFresh,
                            )) {
                                is ChannelFeedResult.Items ->
                                    feed.videos.ifEmpty { channelVideosWithTimestamps(channel) }
                                ChannelFeedResult.NoFeed,
                                ChannelFeedResult.Failed ->
                                    channelVideosWithTimestamps(channel)
                                ChannelFeedResult.RateLimited -> emptyList()
                            }
                        } else {
                            channelVideosWithTimestamps(channel)
                        }
                        videos.take(maxPerChannel)
                    } catch (e: Exception) {
                        KLog.w("YouTubeRepo", "feed fetch failed for ${channel.channelId}", e)
                        emptyList()
                    } finally {
                        gate.release()
                        onProgress?.invoke(completed.incrementAndGet(), total)
                    }
                }
            }.map { it.await() }
        }

        // A hold armed mid-refresh means the rest of the channels stood down,
        // so what came back is a partial feed. Reporting it as the feed would
        // present a throttled refresh as "these are your subscriptions", and
        // the empty case would read as "nothing new" - which is what sends
        // people to check a connection that is working fine.
        if (YouTubeRateLimit.isHeld()) {
            throw YouTubeRateLimitedException(YouTubeRateLimit.remainingMs())
        }

        perChannel.flatten()
            .distinctBy { it.videoId }
            // Items with no usable timestamp sink to the bottom rather than
            // floating to the top on a null-sorts-first comparator.
            .sortedByDescending { it.publishedAtMs ?: Long.MIN_VALUE }
            // Cap after the global sort, never per channel: trimming per
            // channel would hide a prolific channel's recent uploads while
            // keeping a dormant one's year-old video.
            .take(maxTotal)
    }

    /**
     * A channel's 15 most recent uploads from its public Atom feed.
     *
     * This is the cheap half of the local feed. The feed is ~50 KB against
     * roughly 1 MB for the equivalent channel browse, which is the difference
     * between a 200-channel refresh costing 10 MB and costing 200 MB, and it
     * needs no client version, no cookies and no visitorData - so it keeps
     * working when InnerTube shapes drift.
     *
     * It also carries a real ISO timestamp per entry, which the browse path
     * does not: InnerTube only ever says "3 days ago", and merging fifteen
     * channels on prose that coarse shuffles the top of the feed arbitrarily.
     *
     * What it does not carry is duration or live status, so cards from this
     * path show no duration badge. That is the documented trade the "Fast
     * refresh" setting makes.
     */
    suspend fun getChannelFeedRss(
        channelId: String,
        avatarUrl: String? = null
    ): List<VideoItem> =
        (fetchChannelFeedRss(channelId, avatarUrl) as? ChannelFeedResult.Items)?.videos
            ?: emptyList()

    private suspend fun fetchChannelFeedRss(
        channelId: String,
        avatarUrl: String? = null,
        forceFresh: Boolean = false,
    ): ChannelFeedResult = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .apply { feedCacheControl(forceFresh)?.let { cacheControl(it) } }
                .build()
            val body = http.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 429 is a verdict on this device, not on this channel, and
                    // it is the one code the browse fallback must not answer:
                    // escalating 200 channels from a 50 KB feed to a 1 MB browse
                    // aims 200 MB at the server that just asked us to stop.
                    if (YouTubeRateLimit.note(
                            response.code,
                            "channel feed",
                            response.header("Retry-After"),
                        )
                    ) {
                        return@withContext ChannelFeedResult.RateLimited
                    }
                    // 404 means the channel is gone or the id was never valid;
                    // the caller keeps the subscription either way, because a
                    // transient failure must not silently delete channels.
                    KLog.w("YouTubeRepo", "channel feed $channelId HTTP ${response.code}")
                    return@withContext ChannelFeedResult.NoFeed
                }
                response.body?.string()
            } ?: return@withContext ChannelFeedResult.NoFeed
            ChannelFeedResult.Items(parseChannelFeedXml(body, avatarUrl))
        } catch (e: Exception) {
            KLog.w("YouTubeRepo", "getChannelFeedRss failed for $channelId", e)
            ChannelFeedResult.Failed
        }
    }

    /**
     * Whether this fetch may be answered from the HTTP cache.
     *
     * YouTube marks these feeds `public, max-age=900`, so with a cache in the
     * client a refresh inside fifteen minutes costs no request at all - which
     * is most of the point of having one. But it also means a pull-to-refresh
     * would silently do nothing for those fifteen minutes, and a refresh that
     * visibly does nothing is worse than the traffic it saves. So an explicit
     * refresh revalidates and everything else - a tab revisit, a process
     * restart, the six-hourly worker - takes the cached answer.
     */
    private fun feedCacheControl(forceFresh: Boolean): okhttp3.CacheControl? =
        if (forceFresh) okhttp3.CacheControl.Builder().noCache().build() else null

    /**
     * Builds the device-local subscriptions feed: the latest uploads across
     * [channels], newest first.
     *
     * Channels are fetched concurrently but only [FEED_CONCURRENCY] at a time.
     * Unbounded parallelism here would fire one request per subscription at
     * once - a couple of hundred sockets on a pull-to-refresh, which mobile
     * radios handle badly and which reads to YouTube like a scrape.
     *
     * A channel that fails contributes nothing and does not fail the refresh:
     * one dead channel out of two hundred must not empty the feed.
     * [onProgress] reports completed channels so a long first refresh can show
     * real progress instead of an indeterminate spinner.
     */
    /**
     * A channel's uploads from the InnerTube channel browse, with the merge key
     * reconstructed.
     *
     * The browse path has durations and live badges, which RSS lacks, but only
     * prose dates ("3 days ago"), so [VideoItem.publishedAtMs] has to be
     * derived from those to sort alongside RSS items carrying real timestamps.
     */
    private suspend fun channelVideosWithTimestamps(channel: LocalSubscription): List<VideoItem> =
        channelPages.getChannelVideos(channel.toSubscribedChannel()).map { video ->
            video.copy(
                publishedAtMs = video.publishedAtMs
                    ?: VideoItem.parseRelativeTime(video.uploadedDate)
            )
        }

    /**
     * Turns parsed import entries into storable subscriptions, resolving the
     * ones that only carried a handle or vanity URL.
     *
     * Entries that already have a UC id cost nothing - the common case, since
     * both Takeout and every NewPipe-family export write canonical channel
     * URLs. Only the leftovers hit the network, [FEED_CONCURRENCY] at a time,
     * and an entry that cannot be resolved is dropped and counted rather than
     * stored as a broken id that would fail silently on every refresh.
     */
    suspend fun resolveImportedChannels(
        entries: List<ImportedChannel>,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Pair<List<LocalSubscription>, Int> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<LocalSubscription>()
        val needsNetwork = mutableListOf<ImportedChannel>()

        for (entry in entries) {
            val id = entry.channelId
            if (id != null) {
                resolved.add(LocalSubscription(id, entry.name, entry.avatarUrl))
            } else if (entry.unresolvedPath != null) {
                needsNetwork.add(entry)
            }
        }

        if (needsNetwork.isEmpty()) {
            onProgress?.invoke(entries.size, entries.size)
            return@withContext resolved to 0
        }

        val gate = kotlinx.coroutines.sync.Semaphore(FEED_CONCURRENCY)
        val completed = java.util.concurrent.atomic.AtomicInteger(resolved.size)
        val total = entries.size
        onProgress?.invoke(completed.get(), total)

        val lookups = kotlinx.coroutines.coroutineScope {
            needsNetwork.map { entry ->
                async {
                    gate.acquire()
                    try {
                        channelPages.resolveChannelId(entry.unresolvedPath!!)?.let { id ->
                            LocalSubscription(id, entry.name, entry.avatarUrl, handle = entry.unresolvedPath)
                        }
                    } finally {
                        gate.release()
                        onProgress?.invoke(completed.incrementAndGet(), total)
                    }
                }
            }.map { it.await() }
        }

        (resolved + lookups.filterNotNull()).distinctBy { it.channelId } to lookups.count { it == null }
    }

    /**
     * Fills in name and avatar for subscriptions that are missing them - the
     * normal state right after an import, where the file gave at most a name
     * and never a picture.
     *
     * Capped at [limit] channels per run because this is one browse per
     * channel, i.e. the expensive shape the local feed deliberately avoids.
     * It runs against whichever channels are actually on screen, so a
     * 300-channel library fills in over a few visits rather than in one
     * 300-request burst.
     */
    suspend fun fetchMissingChannelProfiles(
        channels: List<LocalSubscription>,
        limit: Int = PROFILE_BACKFILL_LIMIT
    ): List<LocalSubscription> = withContext(Dispatchers.IO) {
        val pending = channels.filter { it.avatarUrl.isNullOrBlank() }.take(limit)
        if (pending.isEmpty()) return@withContext emptyList()
        val gate = kotlinx.coroutines.sync.Semaphore(FEED_CONCURRENCY)
        kotlinx.coroutines.coroutineScope {
            pending.map { channel ->
                async {
                    gate.acquire()
                    try {
                        channelPages.getChannelProfile(channel.channelId)?.let { profile ->
                            channel.copy(
                                name = profile.name,
                                avatarUrl = profile.avatarUrl,
                                handle = profile.handle ?: channel.handle
                            )
                        }
                    } catch (e: Exception) {
                        null
                    } finally {
                        gate.release()
                    }
                }
            }.map { it.await() }
        }.filterNotNull()
    }

    companion object {
        // How many subscribed channels the local feed fetches at once. The
        // local feed costs one request per channel, so this is the only thing
        // standing between a 300-subscription refresh and 300 simultaneous
        // sockets - which mobile radios handle badly and which looks like a
        // scrape from the other end. Six keeps a large refresh moving without
        // starving whatever else the app is loading.
        private const val FEED_CONCURRENCY = 6

        // The channel Atom feed only ever returns 15 entries, so this takes
        // everything it has and lets the global sort decide what survives.
        const val MAX_FEED_ITEMS_PER_CHANNEL = 15

        // Ceiling on the merged feed. 300 subscriptions x 15 uploads is 4500
        // items, which is a lot of LazyColumn for a list nobody scrolls past
        // the first screen of.
        const val MAX_FEED_ITEMS = 300

        // Avatar/name backfill is one channel browse each - the expensive
        // shape the RSS feed exists to avoid - so a run is capped and the
        // rest is picked up on later visits.
        const val PROFILE_BACKFILL_LIMIT = 12
    }
}
