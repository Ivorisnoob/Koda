package com.ivor.ivormusic.ui.video
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.CommentItem

/**
 * Inline comments panel shown over the info area below the video,
 * YouTube-style: the video keeps playing on top while the list scrolls,
 * with infinite scroll pagination, expandable replies and a composer
 * pinned at the bottom for writing comments and replies (login required).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentsPanel(
    comments: List<CommentItem>,
    replies: Map<String, List<CommentItem>>,
    loadingReplyIds: Set<String>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    commentsAvailable: Boolean,
    canComment: Boolean,
    isPosting: Boolean,
    onLoadMore: () -> Unit,
    onLoadReplies: (CommentItem) -> Unit,
    onPostComment: (String) -> Unit,
    onPostReply: (CommentItem, CommentItem, String) -> Unit,
    onLikeComment: (CommentItem) -> Unit,
    onDeleteComment: (CommentItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Seek the player, in seconds, when a comment's timestamp is tapped.
     * Null (the Shorts sheet) renders timestamps as ordinary text.
     */
    onSeekTo: ((seconds: Long) -> Unit)? = null
) {
    val listState = rememberLazyListState()

    // Trigger pagination when the user nears the end of the list
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    // The comment being replied to; null = composing a top-level comment.
    // When replying to a reply, threadParent is the enclosing top-level
    // comment (the reply posts into that thread, YouTube-style).
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var replyThreadParent by remember { mutableStateOf<CommentItem?>(null) }

    // Own comment awaiting delete confirmation
    var commentPendingDelete by remember { mutableStateOf<CommentItem?>(null) }

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.cd_comments),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_comments),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    comments.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (commentsAvailable) "No comments yet" else "Comments are unavailable for this video",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 24.dp, end = 24.dp, bottom = 24.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(comments.size, key = { comments[it].commentId }) { index ->
                                val comment = comments[index]
                                Column {
                                    CommentRow(
                                        comment = comment,
                                        onLikeClick = { onLikeComment(comment) },
                                        onDeleteClick = if (comment.deleteParams != null) {
                                            { commentPendingDelete = comment }
                                        } else null,
                                        onSeekTo = onSeekTo
                                    )

                                    // Reply affordance (needs login + reply params)
                                    if (canComment && comment.replyParams != null) {
                                        Text(
                                            text = "Reply",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(start = 48.dp, top = 4.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    replyTarget = comment
                                                    replyThreadParent = comment
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    // Replies
                                    val commentReplies = replies[comment.commentId]
                                    val isLoadingReplies = comment.commentId in loadingReplyIds
                                    if (comment.repliesToken != null && commentReplies == null) {
                                        Text(
                                            text = if (isLoadingReplies) "Loading replies…"
                                            else "View replies" + if (comment.replyCount.isNotEmpty()) " (${comment.replyCount})" else "",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(start = 48.dp, top = 4.dp)
                                                .clip(CircleShape)
                                                .clickable(enabled = !isLoadingReplies) { onLoadReplies(comment) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    if (!commentReplies.isNullOrEmpty()) {
                                        Column(
                                            modifier = Modifier.padding(start = 40.dp, top = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            commentReplies.forEach { reply ->
                                                Column {
                                                    CommentRow(
                                                        comment = reply,
                                                        isReply = true,
                                                        onLikeClick = { onLikeComment(reply) },
                                                        onDeleteClick = if (reply.deleteParams != null) {
                                                            { commentPendingDelete = reply }
                                                        } else null,
                                                        onSeekTo = onSeekTo
                                                    )
                                                    // Replying to a reply posts into the
                                                    // same thread, addressed to its author
                                                    if (canComment && reply.replyParams != null) {
                                                        Text(
                                                            text = "Reply",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .padding(start = 40.dp, top = 4.dp)
                                                                .clip(CircleShape)
                                                                .clickable {
                                                                    replyTarget = reply
                                                                    replyThreadParent = comment
                                                                }
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isLoadingMore) {
                                item(key = "loading-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            commentPendingDelete?.let { pending ->
                AlertDialog(
                    onDismissRequest = { commentPendingDelete = null },
                    title = { Text(stringResource(R.string.cm_delete_q)) },
                    text = { Text(stringResource(R.string.cm_delete_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteComment(pending)
                                commentPendingDelete = null
                            }
                        ) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { commentPendingDelete = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            if (canComment) {
                CommentComposer(
                    replyTarget = replyTarget,
                    isPosting = isPosting,
                    onCancelReply = {
                        replyTarget = null
                        replyThreadParent = null
                    },
                    onSend = { text ->
                        val target = replyTarget
                        val thread = replyThreadParent ?: target
                        if (target != null && thread != null) {
                            onPostReply(target, thread, text)
                        } else {
                            onPostComment(text)
                        }
                        replyTarget = null
                        replyThreadParent = null
                    }
                )
            }
        }
    }
}

/**
 * Modal bottom sheet wrapper around [CommentsPanel], used by the Shorts
 * player. Capped below full height so the short stays visible and playing
 * above the sheet, YouTube Shorts-style — swiping up cannot expand it to
 * cover the whole screen. The regular video player hosts [CommentsPanel]
 * inline below the video instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(
    comments: List<CommentItem>,
    replies: Map<String, List<CommentItem>>,
    loadingReplyIds: Set<String>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    commentsAvailable: Boolean,
    canComment: Boolean,
    isPosting: Boolean,
    onLoadMore: () -> Unit,
    onLoadReplies: (CommentItem) -> Unit,
    onPostComment: (String) -> Unit,
    onPostReply: (CommentItem, CommentItem, String) -> Unit,
    onLikeComment: (CommentItem) -> Unit,
    onDeleteComment: (CommentItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        CommentsPanel(
            comments = comments,
            replies = replies,
            loadingReplyIds = loadingReplyIds,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            commentsAvailable = commentsAvailable,
            canComment = canComment,
            isPosting = isPosting,
            onLoadMore = onLoadMore,
            onLoadReplies = onLoadReplies,
            onPostComment = onPostComment,
            onPostReply = onPostReply,
            onLikeComment = onLikeComment,
            onDeleteComment = onDeleteComment,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxHeight(0.65f)
                .navigationBarsPadding()
        )
    }
}

/**
 * Input row pinned under the comments list. Shows a "Replying to" banner
 * when a reply target is active.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommentComposer(
    replyTarget: CommentItem?,
    isPosting: Boolean,
    onCancelReply: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // No focus on sheet open: the keyboard should only come up when the user
    // taps the input themselves or taps "Reply" on a comment.

    // Focus the input whenever the user taps "Reply" on a comment.
    LaunchedEffect(replyTarget) {
        if (replyTarget != null) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            if (replyTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Replying to ${replyTarget.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancelReply) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Cancel reply",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(if (replyTarget != null) "Add a reply…" else "Add a comment…")
                    },
                    shape = CircleShape,
                    maxLines = 4,
                    enabled = !isPosting
                )
                if (isPosting) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text)
                                text = ""
                            }
                        },
                        enabled = text.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Post",
                            tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    isReply: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    /** Seek the player, in seconds. Null leaves timestamps as plain text. */
    onSeekTo: ((seconds: Long) -> Unit)? = null
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Avatar
        val avatarSize = if (isReply) 28.dp else 36.dp
        if (comment.authorAvatarUrl != null) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.author.removePrefix("@").take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            // Author line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (comment.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (comment.isCreator) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = comment.author,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = comment.author,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (comment.isVerified) {
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = "Verified",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = comment.publishedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            // Timestamps and links come pre-marked by YouTube (CommentItem.links),
            // so the tappable ranges are exact rather than pattern-matched.
            Text(
                text = com.ivor.ivormusic.ui.components.rememberLinkedText(
                    rich = com.ivor.ivormusic.data.RichText(comment.text, comment.links),
                    onTimestampClick = onSeekTo
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            // Like button + count + creator heart. Tappable only when the
            // signed-in fetch provided the matching toolbar action params.
            val canToggleLike = onLikeClick != null &&
                (if (comment.isLiked) comment.unlikeParams else comment.likeParams) != null
            val likeTint = if (comment.isLiked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = canToggleLike) { onLikeClick?.invoke() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Rounded.ThumbUp,
                        contentDescription = if (comment.isLiked) "Unlike" else "Like",
                        modifier = Modifier.size(14.dp),
                        tint = likeTint
                    )
                    Text(
                        text = (if (comment.isLiked) comment.likeCountLiked.ifEmpty { comment.likeCount }
                        else comment.likeCount).ifEmpty { "0" },
                        style = MaterialTheme.typography.labelSmall,
                        color = likeTint
                    )
                    if (comment.isHearted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = "Hearted by creator",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Delete affordance — only shown on the user's own comments
                // (deleteParams exists only for those)
                if (onDeleteClick != null) {
                    Spacer(Modifier.width(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDeleteClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete comment",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Delete",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
