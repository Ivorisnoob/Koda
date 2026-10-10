package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.ChannelBellParser
import com.ivor.ivormusic.data.ChannelHeader
import com.ivor.ivormusic.data.ChannelLink
import com.ivor.ivormusic.data.ChannelShelf
import com.ivor.ivormusic.data.ChannelSortChips
import com.ivor.ivormusic.data.ChannelSortOption
import com.ivor.ivormusic.data.ChannelTab
import com.ivor.ivormusic.data.ChannelTabKind
import com.ivor.ivormusic.data.ChannelTabPage
import com.ivor.ivormusic.data.SubscribedChannel
import com.ivor.ivormusic.data.VideoItem

// A channel page: header, the tabs the response itself lists, the selected
// tab's grid or shelves, sort orders and the next-page token. Pure.

/**
 * Channel identity out of `pageHeaderViewModel`, with the metadata block as
 * the backstop.
 *
 * Two sources on purpose. The header is the visible one and carries the
 * banner, the verified tick and the subscriber count, but it is also the
 * half that gets redesigned - `c4TabbedHeaderRenderer` was replaced
 * wholesale, and some channels still answer with it. The metadata block has
 * outlived every one of those redesigns, so the name, id, avatar and handle
 * are taken from whichever of the two has them and the page survives a
 * header that parses to nothing.
 */
internal fun parseChannelHeader(
    root: org.json.JSONObject,
    fallbackChannelId: String
): ChannelHeader? {
    val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
    val view = root.optJSONObject("header")
        ?.optJSONObject("pageHeaderRenderer")
        ?.optJSONObject("content")
        ?.optJSONObject("pageHeaderViewModel")

    val channelId = metadata?.optString("externalId")?.takeIf { it.isNotBlank() }
        ?: fallbackChannelId
    val name = view?.optJSONObject("title")
        ?.optJSONObject("dynamicTextViewModel")
        ?.optJSONObject("text")
        ?.optString("content")?.takeIf { it.isNotBlank() }
        ?: metadata?.optString("title")?.takeIf { it.isNotBlank() }
        ?: run {
            // Legacy header, still served by a minority of channels
            val legacy = root.optJSONObject("header")
                ?.optJSONObject("c4TabbedHeaderRenderer")
            getRunText(legacy?.optJSONObject("title"))?.takeIf { it.isNotBlank() }
        }
        ?: return null

    // The metadata rows are "@handle" then "N subscribers" + "N videos",
    // but a channel with no handle simply omits the first row, so the parts
    // are classified by what they say rather than by where they sit.
    var handle: String? = null
    var subscriberCountText: String? = null
    var videoCountText: String? = null
    val rows = view?.optJSONObject("metadata")
        ?.optJSONObject("contentMetadataViewModel")
        ?.optJSONArray("metadataRows")
    if (rows != null) {
        for (r in 0 until rows.length()) {
            val parts = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
            for (p in 0 until parts.length()) {
                val text = parts.optJSONObject(p)?.optJSONObject("text")
                    ?.optString("content")?.takeIf { it.isNotBlank() } ?: continue
                when {
                    text.startsWith("@") -> handle = text
                    text.contains("subscriber", ignoreCase = true) ->
                        subscriberCountText = text
                    text.contains("video", ignoreCase = true) -> videoCountText = text
                }
            }
        }
    }
    if (handle == null) {
        handle = metadata?.optString("vanityChannelUrl")
            ?.substringAfterLast('/')
            ?.takeIf { it.startsWith("@") }
    }

    val avatarUrl = view?.optJSONObject("image")
        ?.optJSONObject("decoratedAvatarViewModel")
        ?.optJSONObject("avatar")
        ?.optJSONObject("avatarViewModel")
        ?.optJSONObject("image")
        ?.let { bestImageSource(it.optJSONArray("sources")) }
        ?: metadata?.optJSONObject("avatar")?.optJSONArray("thumbnails")
            ?.let { bestThumbnail(it) }

    // Absent on plenty of channels; the screen draws its own backdrop
    // rather than treating a missing banner as a load that never finished.
    val bannerUrl = view?.optJSONObject("banner")
        ?.optJSONObject("imageBannerViewModel")
        ?.optJSONObject("image")
        ?.let { bestImageSource(it.optJSONArray("sources")) }

    // The tick arrives as an attachment run on the title text, identified
    // by a client resource name rather than by any visible label.
    val isVerified = runCatching {
        val attachments = view?.optJSONObject("title")
            ?.optJSONObject("dynamicTextViewModel")
            ?.optJSONObject("text")
            ?.optJSONArray("attachmentRuns") ?: return@runCatching false
        val names = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(attachments, "clientResource", names)
        names.any { it.optString("imageName").startsWith("CHECK_CIRCLE") }
    }.getOrDefault(false)

    val attribution = view?.optJSONObject("attribution")
        ?.optJSONObject("attributionViewModel")
        ?.optJSONObject("text")
    val attributionText = attribution?.optString("content")?.trim()
        ?.takeIf { it.isNotBlank() }
    val attributionUrl = attribution
        ?.optJSONArray("commandRuns")?.optJSONObject(0)
        ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
        ?.optJSONObject("urlEndpoint")?.optString("url")
        ?.takeIf { it.isNotBlank() }
        ?.let { unwrapYouTubeRedirect(it) }

    val descriptionPreview = view?.optJSONObject("description")
        ?.optJSONObject("descriptionPreviewViewModel")
        ?.optJSONObject("description")
        ?.optString("content")?.takeIf { it.isNotBlank() }
        ?: metadata?.optString("description")?.takeIf { it.isNotBlank() }

    val aboutToken = view?.optJSONObject("description")
        ?.optJSONObject("descriptionPreviewViewModel")
        ?.optJSONObject("rendererContext")
        ?.optJSONObject("commandContext")
        ?.optJSONObject("onTap")
        ?.optJSONObject("innertubeCommand")
        ?.let { command ->
            val tokens = mutableListOf<String>()
            findContinuationTokens(command, tokens)
            tokens.firstOrNull()
        }

    return ChannelHeader(
        channelId = channelId,
        name = name,
        handle = handle,
        avatarUrl = avatarUrl,
        bannerUrl = bannerUrl,
        subscriberCountText = subscriberCountText,
        videoCountText = videoCountText,
        descriptionPreview = descriptionPreview,
        isVerified = isVerified,
        attributionText = attributionText,
        attributionUrl = attributionUrl,
        aboutToken = aboutToken,
        accountSubscribed = parseChannelSubscribedState(root),
        bell = ChannelBellParser.fromSubscribeButtons(root)[channelId] ?: run {
            // The legacy button, as some watch pages still serve it.
            val legacy = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(root, "subscribeButtonRenderer", legacy)
            legacyBell(legacy.firstOrNull { it.optString("channelId") == channelId }, channelId)
        }
    )
}

/**
 * Whether the signed-in account follows this channel, if the response says.
 *
 * The channel browse already carries this - the header's Subscribe button
 * has to be drawn in the right state - so reading it here means the channel
 * screen costs no extra request to know. Two shapes are checked because
 * both are live: the modern subscribe button keeps its state in a framework
 * entity mutation, and the legacy `subscribeButtonRenderer` keeps a plain
 * boolean.
 *
 * Returns null rather than false when neither is present, so a shape change
 * reads as "unknown" and the caller falls back to asking, instead of
 * quietly showing "Subscribe" for a channel the user follows.
 */
private fun parseChannelSubscribedState(root: org.json.JSONObject): Boolean? {
    val entities = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(root, "subscriptionStateEntity", entities)
    entities.firstOrNull { it.has("subscribed") }
        ?.let { return it.optBoolean("subscribed") }

    val legacy = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(root, "subscribeButtonRenderer", legacy)
    legacy.firstOrNull { it.has("subscribed") }
        ?.let { return it.optBoolean("subscribed") }

    return null
}

/**
 * The tab list, each with the `params` that fetches it.
 *
 * Tabs are classified by their `params` prefix, never by their title: the
 * title is localized, so a screen that matched on the English word would
 * fall back to a generic layout for most of the world. Unrecognised tabs
 * are kept as [ChannelTabKind.OTHER] rather than dropped, which is what
 * lets "Store", "Courses" and "Podcasts" render without code of their own.
 */
internal fun parseChannelTabs(root: org.json.JSONObject): List<ChannelTab> {
    val renderers = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(root, "tabRenderer", renderers)
    findObjectsByKey(root, "expandableTabRenderer", renderers)
    return renderers.mapNotNull { tab ->
        val title = tab.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val params = tab.optJSONObject("endpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("params")
            ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ChannelTab(channelTabKind(params), title, params)
    }.distinctBy { it.params }
}

/**
 * Which tab a `params` value addresses.
 *
 * The values are stable base64 of a short protobuf whose first field is the
 * tab name in plain ASCII ("videos", "shorts", "streams", ...), which is
 * why a prefix match on the encoded string is reliable and why sort
 * variants of the same tab (which append fields) still match.
 */
private fun channelTabKind(params: String): ChannelTabKind = when {
    params.startsWith("EghmZWF0dXJlZ") -> ChannelTabKind.HOME
    params.startsWith("EgZ2aWRlb3") -> ChannelTabKind.VIDEOS
    params.startsWith("EgZzaG9ydH") -> ChannelTabKind.SHORTS
    params.startsWith("EgdzdHJlYW1z") -> ChannelTabKind.LIVE
    params.startsWith("EglwbGF5bGlzdH") -> ChannelTabKind.PLAYLISTS
    params.startsWith("EgVwb3N0c") -> ChannelTabKind.POSTS
    params.startsWith("EghyZWxlYXNlc") -> ChannelTabKind.RELEASES
    params.startsWith("EgZzZWFyY2") -> ChannelTabKind.SEARCH
    else -> ChannelTabKind.OTHER
}

/**
 * The selected tab's content subtree, so the item search below is scoped to
 * it instead of to the whole document.
 *
 * Scoping matters on the Home tab, where the response also carries the
 * header's own thumbnails and several dialogs; a document-wide search picks
 * items out of surfaces the user is not looking at.
 */
internal fun parseSelectedTab(
    root: org.json.JSONObject
): Pair<ChannelTabKind, org.json.JSONObject>? {
    val tabs = root.optJSONObject("contents")
        ?.optJSONObject("twoColumnBrowseResultsRenderer")
        ?.optJSONArray("tabs") ?: return null
    for (i in 0 until tabs.length()) {
        val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer")
            ?: tabs.optJSONObject(i)?.optJSONObject("expandableTabRenderer")
            ?: continue
        if (!tab.optBoolean("selected", false)) continue
        val content = tab.optJSONObject("content") ?: continue
        val params = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
            ?.optString("params").orEmpty()
        return channelTabKind(params) to content
    }
    return null
}

/**
 * Items, shelves, sort options and the next-page token out of one tab
 * response (or one continuation response - both shapes land here).
 *
 * Deliberately not switched on the tab kind. Every tab is parsed for every
 * item type it might hold and the empty lists cost nothing, which is what
 * makes an unrecognised tab like "Courses" render correctly without anyone
 * having taught this function about it.
 */
internal fun parseChannelTabPage(
    scope: org.json.JSONObject,
    header: ChannelHeader?
): ChannelTabPage {
    val shelves = parseChannelShelves(scope, header)

    // Shelved items are already accounted for above, so the flat lists are
    // built from what is left. Without this the Home tab would show every
    // video twice - once in its shelf and once in a flat grid below it.
    val shelvedVideoIds = shelves.flatMap { it.videos }.map { it.videoId }.toSet()
    val shelvedShortIds = shelves.flatMap { it.shorts }.map { it.videoId }.toSet()
    val shelvedPlaylistIds = shelves.flatMap { it.playlists }.map { it.playlistId }.toSet()
    val shelvedPostIds = shelves.flatMap { it.posts }.map { it.postId }.toSet()

    val lockups = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "lockupViewModel", lockups)
    val legacyVideos = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "videoRenderer", legacyVideos)
    val shortLockups = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "shortsLockupViewModel", shortLockups)
    val postRenderers = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "backstagePostRenderer", postRenderers)

    val videos = (
        lockups.mapNotNull { parseLockupViewModel(it) } +
            legacyVideos.mapNotNull { parseVideoRenderer(it) }
        )
        .map { stitchChannelIdentity(it, header) }
        .distinctBy { it.videoId }
        .filterNot { it.videoId in shelvedVideoIds }

    val shorts = shortLockups.mapNotNull { parseShortsLockup(it) }
        .distinctBy { it.videoId }
        .filterNot { it.videoId in shelvedShortIds }

    val playlists = lockups.mapNotNull { parsePlaylistLockup(it) }
        .distinctBy { it.playlistId }
        .filterNot { it.playlistId in shelvedPlaylistIds }

    val posts = postRenderers.mapNotNull { parseBackstagePost(it) }
        .distinctBy { it.postId }
        .filterNot { it.postId in shelvedPostIds }

    val featured = scope.optJSONObject("channelVideoPlayerRenderer")
        ?.let { parseChannelFeaturedVideo(it, header) }
        ?: run {
            val players = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(scope, "channelVideoPlayerRenderer", players)
            players.firstOrNull()?.let { parseChannelFeaturedVideo(it, header) }
        }

    return ChannelTabPage(
        videos = videos,
        shorts = shorts,
        playlists = playlists,
        posts = posts,
        shelves = shelves,
        featured = featured,
        sortOptions = parseChannelSortOptions(scope),
        continuation = parseChannelNextPageToken(scope)
    )
}

/**
 * Channel-page cards omit the channel row, because on YouTube's own layout
 * the channel is the page you are standing on. Every consumer downstream
 * (the queue, the options sheet, "don't recommend this channel") expects an
 * item to know whose it is, so the caller's identity is stitched back in.
 *
 * **An item that already carries a channel id keeps it.** A card that names
 * its channel is a card from somewhere else - the "Collaborations" and
 * "Featured channels" shelves are full of them - and overwriting that would
 * file another creator's video under this one, which then follows the item
 * into the queue and into a "don't recommend this channel" tap on the wrong
 * channel.
 */
private fun stitchChannelIdentity(item: VideoItem, header: ChannelHeader?): VideoItem {
    if (header == null || item.channelId != null) return item
    // With no channel row present, the generic parser has read the first
    // metadata row as the channel name, and on a channel page that row is
    // "N views - date" instead.
    val misreadAsChannel = item.channelName.contains("view", ignoreCase = true) ||
        item.channelName.contains("watching", ignoreCase = true)
    return item.copy(
        channelName = header.name,
        channelId = header.channelId,
        channelIconUrl = item.channelIconUrl ?: header.avatarUrl,
        viewCount = if (item.viewCount.isBlank() && misreadAsChannel) {
            item.channelName
        } else {
            item.viewCount
        }
    )
}

/** The video a channel pins to the top of its Home tab. */
private fun parseChannelFeaturedVideo(
    renderer: org.json.JSONObject,
    header: ChannelHeader?
): VideoItem? {
    val videoId = renderer.optString("videoId").takeIf { it.length == 11 } ?: return null
    val title = getRunText(renderer.optJSONObject("title"))?.takeIf { it.isNotBlank() }
        ?: return null
    val viewCount = getRunText(renderer.optJSONObject("viewCountText")).orEmpty()
    val uploadedDate = getRunText(renderer.optJSONObject("publishedTimeText"))
        ?.takeIf { it.isNotBlank() }
    return VideoItem(
        videoId = videoId,
        title = title,
        channelName = header?.name.orEmpty(),
        channelId = header?.channelId,
        channelIconUrl = header?.avatarUrl,
        thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        duration = 0L,
        viewCount = viewCount,
        uploadedDate = uploadedDate,
        description = getRunText(renderer.optJSONObject("description"))
    )
}

/**
 * Home-tab shelves, in the order the channel arranged them.
 *
 * Read out of the section list rather than by a document-wide key search,
 * because order is the whole point of this tab - a channel decides what
 * sits at the top - and a key search returns whatever traversal order
 * happens to be.
 */
private fun parseChannelShelves(
    scope: org.json.JSONObject,
    header: ChannelHeader?
): List<ChannelShelf> {
    val sections = scope.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
        ?: return emptyList()
    val shelves = mutableListOf<ChannelShelf>()
    for (s in 0 until sections.length()) {
        val contents = sections.optJSONObject(s)
            ?.optJSONObject("itemSectionRenderer")
            ?.optJSONArray("contents") ?: continue
        for (c in 0 until contents.length()) {
            val entry = contents.optJSONObject(c) ?: continue
            val shelf = entry.optJSONObject("shelfRenderer")
                ?: entry.optJSONObject("reelShelfRenderer")
                ?: continue
            val title = getRunText(shelf.optJSONObject("title"))
                ?: shelf.optJSONObject("title")?.optString("simpleText")
                ?: continue

            val items = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(shelf, "lockupViewModel", items)
            val shortItems = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(shelf, "shortsLockupViewModel", shortItems)
            val postItems = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(shelf, "backstagePostRenderer", postItems)
            val channelItems = mutableListOf<org.json.JSONObject>()
            findObjectsByKey(shelf, "gridChannelRenderer", channelItems)

            val built = ChannelShelf(
                title = title,
                videos = items.mapNotNull { parseLockupViewModel(it) }
                    .map { stitchChannelIdentity(it, header) }
                    .distinctBy { it.videoId },
                shorts = shortItems.mapNotNull { parseShortsLockup(it) }
                    .distinctBy { it.videoId },
                playlists = items.mapNotNull { parsePlaylistLockup(it) }
                    .distinctBy { it.playlistId },
                posts = postItems.mapNotNull { parseBackstagePost(it) }
                    .distinctBy { it.postId },
                channels = channelItems.mapNotNull { parseGridChannel(it) }
                    .distinctBy { it.channelId }
            )
            if (!built.isEmpty) shelves.add(built)
        }
    }
    return shelves
}

/** A channel card in a "Featured channels" shelf. */
private fun parseGridChannel(renderer: org.json.JSONObject): SubscribedChannel? {
    val channelId = renderer.optString("channelId").takeIf { it.isNotBlank() } ?: return null
    val name = getRunText(renderer.optJSONObject("title"))
        ?: renderer.optJSONObject("title")?.optString("simpleText")
        ?: return null
    val avatarUrl = bestThumbnail(
        renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
    )
    return SubscribedChannel(
        channelId = channelId,
        name = name,
        avatarUrl = avatarUrl,
        subscriberCountText = getRunText(renderer.optJSONObject("subscriberCountText"))
    )
}

/** One About-panel link, unwrapped out of YouTube's redirect. */
internal fun parseChannelExternalLink(view: org.json.JSONObject): ChannelLink? {
    val title = view.optJSONObject("title")?.optString("content")
        ?.takeIf { it.isNotBlank() }
    val link = view.optJSONObject("link")
    val display = link?.optString("content")?.takeIf { it.isNotBlank() }
    val url = link?.optJSONArray("commandRuns")?.optJSONObject(0)
        ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
        ?.optJSONObject("urlEndpoint")?.optString("url")
        ?.takeIf { it.isNotBlank() }
        ?.let { unwrapYouTubeRedirect(it) }
        ?: return null
    return ChannelLink(
        title = title ?: display ?: url,
        url = url,
        faviconUrl = bestImageSource(
            view.optJSONObject("favicon")?.optJSONArray("sources")
        )
    )
}

/**
 * The sort orders a tab offers, from either of the two mechanisms YouTube
 * uses for them - see [ChannelSortOption] for why both are kept.
 */
private fun parseChannelSortOptions(scope: org.json.JSONObject): List<ChannelSortOption> {
    // Videos / Shorts / Live, current shape: plain chips carrying their
    // continuation directly. See ChannelSortChips for the shape change that
    // made the sheet branch below find nothing.
    ChannelSortChips.parse(scope).takeIf { it.isNotEmpty() }?.let { return it }

    val options = mutableListOf<ChannelSortOption>()

    // Videos / Shorts / Live, August 2026 shape: a chip that opens a sheet
    // of continuations. Kept as the fallback for a response still using it.
    val chips = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "chipViewModel", chips)
    for (chip in chips) {
        val listItems = chip.optJSONObject("tapCommand")
            ?.optJSONObject("innertubeCommand")
            ?.optJSONObject("showSheetCommand")
            ?.optJSONObject("panelLoadingStrategy")
            ?.optJSONObject("inlineContent")
            ?.optJSONObject("sheetViewModel")
            ?.optJSONObject("content")
            ?.optJSONObject("listViewModel")
            ?.optJSONArray("listItems") ?: continue
        for (i in 0 until listItems.length()) {
            val item = listItems.optJSONObject(i)?.optJSONObject("listItemViewModel") ?: continue
            val label = item.optJSONObject("title")?.optString("content")
                ?.takeIf { it.isNotBlank() } ?: continue
            val command = item.optJSONObject("rendererContext")
                ?.optJSONObject("commandContext")
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand") ?: continue
            val tokens = mutableListOf<String>()
            findContinuationTokens(command, tokens)
            val token = tokens.firstOrNull() ?: continue
            options.add(
                ChannelSortOption(
                    label = label,
                    selected = item.optBoolean("isSelected", false),
                    token = token
                )
            )
        }
    }
    if (options.isNotEmpty()) return options.distinctBy { it.label }

    // Playlists: a sub-menu of browse params, re-browsing the tab.
    val subMenus = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "sortFilterSubMenuRenderer", subMenus)
    for (menu in subMenus) {
        val items = menu.optJSONArray("subMenuItems") ?: continue
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val label = item.optString("title").takeIf { it.isNotBlank() } ?: continue
            val params = item.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("params")?.takeIf { it.isNotBlank() } ?: continue
            options.add(
                ChannelSortOption(
                    label = label,
                    selected = item.optBoolean("selected", false),
                    params = params
                )
            )
        }
    }
    return options.distinctBy { it.label }
}

/**
 * The token for the next page, from the `continuationItemRenderer` YouTube
 * puts at the end of a grid.
 *
 * Taken from the *last* continuation in the response rather than the first:
 * a Home tab carries one per shelf, and the page-level one that actually
 * scrolls the grid is the trailing one.
 */
private fun parseChannelNextPageToken(scope: org.json.JSONObject): String? {
    val renderers = mutableListOf<org.json.JSONObject>()
    findObjectsByKey(scope, "continuationItemRenderer", renderers)
    return renderers.lastNotNullOfOrNull { renderer ->
        renderer.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
    }
}
