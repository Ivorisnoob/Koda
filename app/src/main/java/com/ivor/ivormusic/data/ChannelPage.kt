package com.ivor.ivormusic.data

/**
 * Models for a creator's channel page.
 *
 * Shapes verified against live InnerTube responses, signed out, August 2026 -
 * see [YouTubeRepository.getChannelPage] for the endpoints and the renderer
 * notes.
 *
 * **The organising fact about this file is that a channel page describes
 * itself.** The first browse call carries the tab list with each tab's own
 * `params`, every sort order with its own continuation token, and every "next
 * page" as another token. So nothing here holds a hardcoded browse parameter,
 * and nothing assumes a fixed set of tabs: a channel that has no Shorts simply
 * does not list one, and a musician's channel lists "Releases" where a teacher's
 * lists "Courses". Both are ordinary [ChannelTab]s and neither needed code.
 *
 * That is deliberate rather than convenient. Hardcoding the six tabs YouTube
 * shows a big tech channel would have meant showing empty tabs on the channels
 * that lack them and hiding real ones on the channels that have more.
 */

/**
 * Identity and display metadata for a channel, from `pageHeaderViewModel`
 * plus `metadata.channelMetadataRenderer` of the same response.
 *
 * The header is the half that drifts - `c4TabbedHeaderRenderer` was replaced
 * wholesale by `pageHeaderViewModel` - so everything optional here is genuinely
 * allowed to be null rather than being a parse failure. [name] and [channelId]
 * are the only two a page cannot be drawn without.
 */
data class ChannelHeader(
    val channelId: String,
    val name: String,
    /** "@handle", from the metadata row or the vanity URL. */
    val handle: String? = null,
    val avatarUrl: String? = null,
    /**
     * Banner art. Genuinely absent on many channels (verified on live
     * responses), so the UI must have something to draw in its place rather
     * than treating this as a loading state that never resolves.
     */
    val bannerUrl: String? = null,
    val subscriberCountText: String? = null,
    val videoCountText: String? = null,
    /** The truncated blurb YouTube shows under the name, not the full About text. */
    val descriptionPreview: String? = null,
    /** Verified tick, from a `CHECK_CIRCLE_FILLED` attachment run on the title. */
    val isVerified: Boolean = false,
    /** The one link YouTube promotes in the header, e.g. "lttstore.com". */
    val attributionText: String? = null,
    val attributionUrl: String? = null,
    /**
     * Continuation token for the About panel. YouTube does not put the full
     * about text in the channel response - it is a separate browse behind this
     * token - so the About tab is fetched on demand and only once.
     */
    val aboutToken: String? = null,
    /**
     * Whether the signed-in account follows this channel, when the response
     * said so.
     *
     * **Null means "the response did not say", not "no".** Signed out there is
     * no account state to report, and a shape change would silently turn a
     * subscribed channel into an unsubscribed-looking one, so the caller treats
     * null as a reason to go and ask rather than as an answer.
     */
    val accountSubscribed: Boolean? = null
) {
    fun toLocalSubscription(): LocalSubscription = LocalSubscription(
        channelId = channelId,
        name = name,
        avatarUrl = avatarUrl,
        handle = handle
    )

    fun toSubscribedChannel(): SubscribedChannel = SubscribedChannel(
        channelId = channelId,
        name = name,
        avatarUrl = avatarUrl,
        subscriberCountText = subscriberCountText,
        handle = handle
    )

    val shareUrl: String
        get() = handle?.takeIf { it.startsWith("@") }
            ?.let { "https://www.youtube.com/$it" }
            ?: "https://www.youtube.com/channel/$channelId"
}

/**
 * What a tab holds, as far as the UI needs to care.
 *
 * Matched on the tab's `params` prefix rather than its title, because the title
 * is localized: a French account calls the Videos tab "Vidéos", and a screen
 * that keyed its layout off the English word would fall back to a generic list
 * for most of the world. [OTHER] is not a failure - it is how "Store",
 * "Courses" and whatever YouTube adds next still render as a grid of whatever
 * they contain.
 */
enum class ChannelTabKind {
    HOME, VIDEOS, SHORTS, LIVE, PLAYLISTS, POSTS, RELEASES, SEARCH, OTHER,

    /**
     * Not one of YouTube's tabs. About is an engagement panel behind the
     * header's description there, but as far as a reader is concerned it is
     * another thing the channel has, so the UI promotes it to a tab and this
     * is how it addresses it. Nothing in the data layer ever produces it.
     */
    ABOUT
}

/**
 * One tab, carrying the `params` that fetches it. Read off the response, never
 * constructed here - see the file comment.
 */
data class ChannelTab(
    val kind: ChannelTabKind,
    val title: String,
    val params: String
)

/**
 * A sort order offered by a tab ("Latest", "Popular", "Oldest").
 *
 * The two tabs that offer sorting do it by different mechanisms, and both are
 * carried here rather than being normalised, because normalising would mean
 * inventing a token for the half that has none. Videos, Shorts and Live hand
 * back a [token]: a browse continuation whose response arrives as a
 * `reloadContinuationItemsCommand`, so sorting costs one call and replaces the
 * list in place. Playlists hands back [params] instead and is a re-browse of
 * the tab. Verified August 2026.
 */
data class ChannelSortOption(
    val label: String,
    val selected: Boolean = false,
    val token: String? = null,
    val params: String? = null
)

/** An external link from the About panel, with the favicon YouTube resolved. */
data class ChannelLink(
    val title: String,
    val url: String,
    val faviconUrl: String? = null
)

/**
 * The About panel: description, links, join date and lifetime counts.
 *
 * Fetched through [ChannelHeader.aboutToken] rather than being part of the page
 * response, so it costs a request only if the user opens About.
 */
data class ChannelAbout(
    val description: String? = null,
    val links: List<ChannelLink> = emptyList(),
    val joinedDateText: String? = null,
    val viewCountText: String? = null,
    val subscriberCountText: String? = null,
    val videoCountText: String? = null,
    val country: String? = null,
    val canonicalUrl: String? = null
)

/** One option of a community poll, with the share of the vote YouTube reports. */
data class ChannelPollChoice(
    val text: String,
    val imageUrl: String? = null
)

/**
 * A community post.
 *
 * The attachment is a sum type in the response (`backstageImageRenderer`,
 * `postMultiImageRenderer`, `videoRenderer`, `pollRenderer`) and is flattened
 * here into optional fields, because a post carries at most one and the UI
 * draws whichever is present. A post with none is a plain text post, which is
 * common.
 */
data class ChannelPost(
    val postId: String,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val text: RichText = RichText.EMPTY,
    val publishedText: String? = null,
    val voteCountText: String? = null,
    val replyCountText: String? = null,
    /** One entry for a single-image post, several for a carousel. */
    val images: List<String> = emptyList(),
    /** A shared video. Tapping the card plays it. */
    val video: VideoItem? = null,
    val pollChoices: List<ChannelPollChoice> = emptyList(),
    val pollTotalText: String? = null
)

/**
 * One horizontal shelf on the Home tab ("Popular videos", "Featured channels").
 *
 * Shelves are heterogeneous - the same tab carries video shelves, a playlist
 * shelf, a channel shelf and a post shelf - so each list is separate and all
 * but one are empty on any given shelf. A sealed hierarchy would be tidier and
 * would mean a new renderer family could not be added without touching every
 * `when` in the UI.
 */
data class ChannelShelf(
    val title: String,
    val videos: List<VideoItem> = emptyList(),
    val shorts: List<ShortsItem> = emptyList(),
    val playlists: List<VideoPlaylist> = emptyList(),
    val posts: List<ChannelPost> = emptyList(),
    val channels: List<SubscribedChannel> = emptyList()
) {
    val isEmpty: Boolean
        get() = videos.isEmpty() && shorts.isEmpty() && playlists.isEmpty() &&
            posts.isEmpty() && channels.isEmpty()
}

/**
 * One page of a tab's contents, plus what it takes to get the next one.
 *
 * Parallel lists for the same reason [ChannelShelf] has them: the Videos tab
 * fills [videos], the Shorts tab fills [shorts], and Home fills [shelves],
 * without the caller needing to know which kind of tab it asked for.
 *
 * [continuation] null means the tab is exhausted, which is what stops the
 * grid asking for a page that does not exist forever.
 */
data class ChannelTabPage(
    val videos: List<VideoItem> = emptyList(),
    val shorts: List<ShortsItem> = emptyList(),
    val playlists: List<VideoPlaylist> = emptyList(),
    val posts: List<ChannelPost> = emptyList(),
    val shelves: List<ChannelShelf> = emptyList(),
    /** The video a channel pins to the top of its Home tab, when it has one. */
    val featured: VideoItem? = null,
    val sortOptions: List<ChannelSortOption> = emptyList(),
    val continuation: String? = null
) {
    val isEmpty: Boolean
        get() = videos.isEmpty() && shorts.isEmpty() && playlists.isEmpty() &&
            posts.isEmpty() && shelves.isEmpty() && featured == null

    /** Appends [next] to this page, keeping this page's sort options. */
    fun plus(next: ChannelTabPage): ChannelTabPage = copy(
        videos = (videos + next.videos).distinctBy { it.videoId },
        shorts = (shorts + next.shorts).distinctBy { it.videoId },
        playlists = (playlists + next.playlists).distinctBy { it.playlistId },
        posts = (posts + next.posts).distinctBy { it.postId },
        shelves = shelves + next.shelves,
        continuation = next.continuation
    )
}

/**
 * Everything the first channel browse returns: who the creator is, what tabs
 * they have, and the contents of the one that was already selected.
 *
 * The selected tab's content rides along because it arrived in the same
 * response - fetching it again as "the first tab" would be a second request for
 * bytes already in hand, which the app's frugality rule rules out.
 */
data class ChannelPage(
    val header: ChannelHeader,
    val tabs: List<ChannelTab>,
    val selectedTab: ChannelTabKind,
    val selectedContent: ChannelTabPage
)
