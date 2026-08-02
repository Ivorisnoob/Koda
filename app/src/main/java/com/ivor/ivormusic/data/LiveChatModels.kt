package com.ivor.ivormusic.data

/**
 * Models for YouTube live chat.
 *
 * Shapes verified against the live InnerTube API August 2026 - see
 * [YouTubeRepository.pollLiveChat] for the endpoint and renderer notes.
 */

/**
 * One inline piece of a chat message. Chat text arrives as a run list where
 * each run is either plain text or an emoji, so a message cannot be flattened
 * to a String without losing the custom channel emoji.
 */
sealed interface LiveChatRun {
    data class Text(val text: String) : LiveChatRun

    /**
     * [imageUrl] is null for standard unicode emoji, whose [label] already
     * holds the character and renders as ordinary text. Channel-custom emoji
     * carry an opaque id and must be drawn from [imageUrl].
     */
    data class Emoji(val label: String, val imageUrl: String?) : LiveChatRun
}

/** Author badge kinds YouTube exposes on a chat message. */
enum class LiveChatBadgeKind { OWNER, MODERATOR, VERIFIED, MEMBER }

/**
 * A badge beside an author's name. Owner/moderator/verified arrive as an
 * iconType and render as a vector; member badges are per-channel artwork and
 * arrive as a [imageUrl] thumbnail instead.
 */
data class LiveChatBadge(
    val kind: LiveChatBadgeKind,
    val tooltip: String,
    val imageUrl: String? = null,
)

data class LiveChatAuthor(
    val name: String,
    val channelId: String? = null,
    val photoUrl: String? = null,
    val badges: List<LiveChatBadge> = emptyList(),
) {
    val isOwner: Boolean get() = badges.any { it.kind == LiveChatBadgeKind.OWNER }
    val isModerator: Boolean get() = badges.any { it.kind == LiveChatBadgeKind.MODERATOR }
    val isMember: Boolean get() = badges.any { it.kind == LiveChatBadgeKind.MEMBER }
}

/**
 * A single entry in the chat stream. Ordinary messages dominate by a wide
 * margin; the paid/membership/gift variants are rare but visually loud, which
 * is why they are separate types rather than flags on [Text].
 */
sealed interface LiveChatMessage {
    val id: String
    val timestampUsec: Long

    /**
     * Null only for [System] notices, which YouTube itself authors. Declared
     * here so a ban - which deletes every message from one channel at once -
     * can be applied without branching over every variant.
     */
    val author: LiveChatAuthor?

    data class Text(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val runs: List<LiveChatRun>,
    ) : LiveChatMessage

    /**
     * A Super Chat, or a Super Sticker when [stickerUrl] is set. The colors come
     * from YouTube (ARGB ints, unsigned 32-bit) rather than the app theme - they
     * are part of the amount tier and are the one place a non-ColorScheme color
     * is correct.
     */
    data class Paid(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val runs: List<LiveChatRun>,
        val amountText: String,
        val headerBackgroundColor: Long,
        val headerTextColor: Long,
        val bodyBackgroundColor: Long,
        val bodyTextColor: Long,
        /** Artwork for a Super Sticker; null for an ordinary Super Chat. */
        val stickerUrl: String? = null,
    ) : LiveChatMessage

    /** A new or renewed channel membership. */
    data class Membership(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val headline: String,
        val tierName: String?,
    ) : LiveChatMessage

    /**
     * A gift, delivered in the newer viewModel format rather than as a
     * renderer, so it carries no timestamp of its own - ordering comes from
     * its position in the action list.
     */
    data class Gift(
        override val id: String,
        override val timestampUsec: Long,
        override val author: LiveChatAuthor,
        val text: String,
        val giftImageUrl: String?,
    ) : LiveChatMessage

    /** A YouTube system notice, e.g. "Subscribers-only mode". */
    data class System(
        override val id: String,
        override val timestampUsec: Long,
        val runs: List<LiveChatRun>,
    ) : LiveChatMessage {
        override val author: LiveChatAuthor? get() = null
    }
}

/**
 * The pinned card above the chat: either a message the creator pinned or the
 * auto-generated chat summary.
 */
data class LiveChatBanner(
    val id: String,
    val author: LiveChatAuthor?,
    val runs: List<LiveChatRun>,
    val isSummary: Boolean,
)

/** Entry point for a chat stream, from the watch-next response. */
data class LiveChatSession(
    val continuation: String,
)

/**
 * One poll's worth of chat. [timeoutMs] is the server-dictated delay before
 * the next poll - honour it rather than picking an interval.
 *
 * [sendParams] is the opaque token a send_message call must echo back. It
 * rides every poll response rather than the session, and is present even when
 * signed out, so it is not on its own permission to post.
 *
 * Moderation arrives as three separate action shapes, all of which have to be
 * applied or a deleted message stays on screen forever: [removedIds] (one
 * message), [removedAuthorIds] (every message from one channel, which is what a
 * ban emits) and [replacements] (a message edited in place, keyed by the id it
 * supersedes).
 */
data class LiveChatPage(
    val messages: List<LiveChatMessage> = emptyList(),
    val removedIds: Set<String> = emptySet(),
    val removedAuthorIds: Set<String> = emptySet(),
    val replacements: Map<String, LiveChatMessage> = emptyMap(),
    val banner: LiveChatBanner? = null,
    /** A removeBannerForLiveChatCommand: the pinned card should come down. */
    val bannerCleared: Boolean = false,
    /**
     * Why the composer is unavailable ("Subscribers-only mode", slow mode, a
     * ban), from liveChatRestrictedParticipationRenderer. Null when chat is
     * open to the viewer.
     */
    val restrictionMessage: String? = null,
    val nextContinuation: String? = null,
    val timeoutMs: Long = 10_000L,
    val sendParams: String? = null,
    val maxMessageLength: Int = 200,
)

/**
 * Outcome of a send. The response echoes the accepted message back as a normal
 * chat item, so [echo] can be shown immediately rather than waiting up to a
 * poll interval for it to come round again - the id matches the one the poll
 * will later deliver, so the ordinary dedupe drops the duplicate.
 */
data class LiveChatSendResult(
    val success: Boolean,
    val echo: LiveChatMessage? = null,
    val error: String? = null,
)

/**
 * Live counters that change while watching, from the updated_metadata endpoint.
 */
data class LiveMetadata(
    val viewerCountText: String? = null,
    val shortViewerCount: String? = null,
    val dateText: String? = null,
)
