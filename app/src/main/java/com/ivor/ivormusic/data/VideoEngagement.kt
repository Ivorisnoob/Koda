package com.ivor.ivormusic.data

/**
 * Engagement data for a video: like state, subscription state and the
 * entry token for the comments section. Parsed from the InnerTube /next
 * response (WEB client). likeStatus and isSubscribed only reflect the
 * user's real state when the request was authenticated.
 */
data class VideoEngagement(
    val videoId: String,
    val likeCount: String?,          // formatted, e.g. "19M"
    val likeStatus: LikeStatus,
    val channelId: String?,          // canonical UC... id (needed for subscribe)
    val isSubscribed: Boolean,
    val subscriberCountText: String?,
    val commentsToken: String?,      // continuation token for the first comments page
    /**
     * Every channel credited on a collaboration video, in the order YouTube
     * lists them - so the first is the uploader. Empty for the overwhelming
     * majority of videos, which have one owner and describe it the ordinary
     * way; see [VideoCollaborator] for why a collab describes none.
     */
    val collaborators: List<VideoCollaborator> = emptyList()
)

enum class LikeStatus { LIKE, DISLIKE, INDIFFERENT }

/**
 * A single comment (top-level or reply), parsed from the modern
 * commentEntityPayload format used by InnerTube since 2024.
 */
data class CommentItem(
    val commentId: String,
    val text: String,
    // Spans YouTube marked clickable in [text] - timestamps and links. Empty
    // for the vast majority of comments, which are plain prose.
    val links: List<RichLink> = emptyList(),
    val author: String,
    val authorAvatarUrl: String?,
    val publishedTime: String,
    val likeCount: String,           // formatted, e.g. "263K"
    val replyCount: String,          // formatted; empty when no replies
    val isPinned: Boolean,
    val isHearted: Boolean,
    val isCreator: Boolean,
    val isVerified: Boolean,
    val repliesToken: String?,       // continuation token to load replies
    val replyParams: String? = null, // createReplyParams for posting a reply to this comment
    val likeCountLiked: String = "", // formatted count to show while liked (e.g. "264K")
    val isLiked: Boolean = false,
    val likeParams: String? = null,  // perform_comment_action param to like (signed-in only)
    val unlikeParams: String? = null, // perform_comment_action param to remove the like
    // perform_comment_action param to delete; present only on the user's own
    // comments, so non-null also means "this is my comment"
    val deleteParams: String? = null
)

/**
 * One page of comments plus the token for the next page (null = last page).
 * createCommentParams (top-level pages only) enables posting a new comment.
 */
data class CommentsPage(
    val comments: List<CommentItem>,
    val nextPageToken: String?,
    val createCommentParams: String? = null
)

/**
 * A comment whose text references a playback timestamp (e.g. "2:31 is wild").
 * timeMs is the first timestamp mentioned in the comment, in milliseconds.
 */
data class TimedComment(
    val comment: CommentItem,
    val timeMs: Long
)
