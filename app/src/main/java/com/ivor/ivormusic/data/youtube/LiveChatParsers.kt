package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.LiveChatAuthor
import com.ivor.ivormusic.data.LiveChatBadge
import com.ivor.ivormusic.data.LiveChatBadgeKind
import com.ivor.ivormusic.data.LiveChatBanner
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.LiveChatRun
import com.ivor.ivormusic.util.KLog

// Live chat actions read into messages, authors, badges and the pinned banner. Pure.

/**
 * Turn one addChatItemAction item into a [LiveChatMessage].
 *
 * Renderer frequencies measured across 59 live chats (~3.3k messages):
 * liveChatTextMessageRenderer dominates, then the system notice, then
 * giftMessageViewModel, placeholder, Super Chat and membership. The
 * placeholder is a slot for a message being moderated and carries only an
 * id - it renders as nothing, so it is dropped here.
 *
 * [fallbackOrder] backfills a sort key for giftMessageViewModel, the one
 * item that arrives without a timestamp of its own.
 */
internal fun parseLiveChatItem(item: org.json.JSONObject, fallbackOrder: Int): LiveChatMessage? {
    try {
        item.optJSONObject("liveChatTextMessageRenderer")?.let { r ->
            val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
            return LiveChatMessage.Text(
                id = id,
                timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                author = parseLiveChatAuthor(r),
                runs = parseLiveChatRuns(r.optJSONObject("message")),
            )
        }

        item.optJSONObject("liveChatPaidMessageRenderer")?.let { r ->
            val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
            return LiveChatMessage.Paid(
                id = id,
                timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                author = parseLiveChatAuthor(r),
                // An amount-only Super Chat carries no message at all.
                runs = parseLiveChatRuns(r.optJSONObject("message")),
                amountText = getRunText(r.optJSONObject("purchaseAmountText")).orEmpty(),
                // Unsigned 32-bit ARGB - optInt would overflow.
                headerBackgroundColor = r.optLong("headerBackgroundColor"),
                headerTextColor = r.optLong("headerTextColor"),
                bodyBackgroundColor = r.optLong("bodyBackgroundColor"),
                bodyTextColor = r.optLong("bodyTextColor"),
            )
        }

        // A Super Sticker: same paid tier colors, but the payload is artwork
        // instead of a message, under a different set of color keys.
        item.optJSONObject("liveChatPaidStickerRenderer")?.let { r ->
            val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
            val background = r.optLong("backgroundColor")
            return LiveChatMessage.Paid(
                id = id,
                timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                author = parseLiveChatAuthor(r),
                runs = emptyList(),
                amountText = getRunText(r.optJSONObject("purchaseAmountText")).orEmpty(),
                headerBackgroundColor = background,
                headerTextColor = r.optLong("authorNameTextColor"),
                bodyBackgroundColor = background,
                bodyTextColor = r.optLong("moneyChipTextColor"),
                stickerUrl = widestThumbnailUrl(
                    r.optJSONObject("sticker")?.optJSONArray("thumbnails"),
                    "url"
                )?.let { if (it.startsWith("//")) "https:$it" else it },
            )
        }

        item.optJSONObject("liveChatMembershipItemRenderer")?.let { r ->
            val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
            return LiveChatMessage.Membership(
                id = id,
                timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                author = parseLiveChatAuthor(r),
                headline = getRunText(r.optJSONObject("headerPrimaryText"))
                    ?.takeIf { it.isNotBlank() }
                    ?: "New member",
                tierName = getRunText(r.optJSONObject("headerSubtext"))
                    ?.takeIf { it.isNotBlank() },
            )
        }

        // Gifts use the newer viewModel format: flat "content" strings
        // instead of run lists, an avatarViewModel instead of a thumbnail
        // list, and no timestamp.
        item.optJSONObject("giftMessageViewModel")?.let { vm ->
            val id = vm.optString("id").takeIf { it.isNotBlank() } ?: return null
            val avatar = vm.optJSONObject("authorAvatar")
                ?.optJSONObject("avatarViewModel")
                ?.optJSONObject("image")
                ?.optJSONArray("sources")
            return LiveChatMessage.Gift(
                id = id,
                timestampUsec = fallbackOrder.toLong(),
                author = LiveChatAuthor(
                    name = vm.optJSONObject("authorName")?.optString("content")?.trim().orEmpty(),
                    photoUrl = widestThumbnailUrl(avatar, "url"),
                ),
                text = vm.optJSONObject("text")?.optString("content").orEmpty(),
                giftImageUrl = widestThumbnailUrl(vm.optJSONObject("giftImage")?.optJSONArray("sources"), "url")
                    ?.let { if (it.startsWith("//")) "https:$it" else it },
            )
        }

        item.optJSONObject("liveChatViewerEngagementMessageRenderer")?.let { r ->
            val id = r.optString("id").takeIf { it.isNotBlank() } ?: return null
            return LiveChatMessage.System(
                id = id,
                timestampUsec = r.optString("timestampUsec").toLongOrNull() ?: 0L,
                runs = parseLiveChatRuns(r.optJSONObject("message")),
            )
        }

        return null
    } catch (e: Exception) {
        KLog.w("YouTubeRepo", "parseLiveChatItem failed", e)
        return null
    }
}

/** Author name, avatar, channel id and badges, shared by every renderer. */
private fun parseLiveChatAuthor(renderer: org.json.JSONObject): LiveChatAuthor =
    LiveChatAuthor(
        name = getRunText(renderer.optJSONObject("authorName")).orEmpty(),
        channelId = renderer.optString("authorExternalChannelId").takeIf { it.isNotBlank() },
        photoUrl = widestThumbnailUrl(
            renderer.optJSONObject("authorPhoto")?.optJSONArray("thumbnails"),
            "url"
        ),
        badges = parseLiveChatBadges(renderer.optJSONArray("authorBadges")),
    )

/**
 * Badges come in two shapes: owner/moderator/verified as an icon.iconType,
 * and channel memberships as per-channel customThumbnail artwork whose
 * tooltip carries the tenure ("Member (1 year)").
 */
private fun parseLiveChatBadges(badges: org.json.JSONArray?): List<LiveChatBadge> {
    if (badges == null) return emptyList()
    return (0 until badges.length()).mapNotNull { i ->
        val r = badges.optJSONObject(i)?.optJSONObject("liveChatAuthorBadgeRenderer")
            ?: return@mapNotNull null
        val tooltip = r.optString("tooltip")
        val custom = widestThumbnailUrl(
            r.optJSONObject("customThumbnail")?.optJSONArray("thumbnails"),
            "url"
        )
        if (custom != null) {
            return@mapNotNull LiveChatBadge(LiveChatBadgeKind.MEMBER, tooltip, custom)
        }
        val kind = when (r.optJSONObject("icon")?.optString("iconType")) {
            "OWNER" -> LiveChatBadgeKind.OWNER
            "MODERATOR" -> LiveChatBadgeKind.MODERATOR
            "VERIFIED" -> LiveChatBadgeKind.VERIFIED
            else -> return@mapNotNull null
        }
        LiveChatBadge(kind, tooltip)
    }
}

/**
 * Split a chat message into text and emoji runs.
 *
 * Standard unicode emoji carry the character itself in emojiId and need no
 * image; channel-custom emoji have an opaque id and must be drawn from the
 * thumbnail, so the two cases are distinguished rather than flattened.
 */
private fun parseLiveChatRuns(message: org.json.JSONObject?): List<LiveChatRun> {
    if (message == null) return emptyList()
    message.optString("simpleText").takeIf { it.isNotBlank() }?.let {
        return listOf(LiveChatRun.Text(it))
    }
    val runs = message.optJSONArray("runs") ?: return emptyList()
    return (0 until runs.length()).mapNotNull { i ->
        val run = runs.optJSONObject(i) ?: return@mapNotNull null
        val emoji = run.optJSONObject("emoji")
        if (emoji != null) {
            val emojiId = emoji.optString("emojiId")
            val label = emoji.optJSONArray("shortcuts")?.optString(0)?.takeIf { it.isNotBlank() }
                ?: emojiId
            // A unicode emoji's id is the character; anything longer is an
            // opaque channel emoji key that must render as artwork.
            val isUnicode = emojiId.isNotEmpty() && emojiId.codePointCount(0, emojiId.length) <= 2
            LiveChatRun.Emoji(
                label = if (isUnicode) emojiId else label,
                imageUrl = if (isUnicode) null else widestThumbnailUrl(
                    emoji.optJSONObject("image")?.optJSONArray("thumbnails"),
                    "url"
                ),
            )
        } else {
            run.optString("text").takeIf { it.isNotEmpty() }?.let { LiveChatRun.Text(it) }
        }
    }
}

/**
 * The pinned card: either a message the creator pinned
 * (liveChatTextMessageRenderer) or the auto-generated chat summary
 * (liveChatBannerChatSummaryRenderer).
 */
internal fun parseLiveChatBanner(banner: org.json.JSONObject): LiveChatBanner? {
    val contents = banner.optJSONObject("contents") ?: return null
    contents.optJSONObject("liveChatBannerChatSummaryRenderer")?.let { summary ->
        return LiveChatBanner(
            id = summary.optString("liveChatSummaryId").takeIf { it.isNotBlank() }
                ?: banner.optString("actionId"),
            author = null,
            runs = parseLiveChatRuns(summary.optJSONObject("chatSummary")),
            isSummary = true,
        )
    }
    contents.optJSONObject("liveChatTextMessageRenderer")?.let { pinned ->
        return LiveChatBanner(
            id = pinned.optString("id").takeIf { it.isNotBlank() }
                ?: banner.optString("actionId"),
            author = parseLiveChatAuthor(pinned),
            runs = parseLiveChatRuns(pinned.optJSONObject("message")),
            isSummary = false,
        )
    }
    return null
}
