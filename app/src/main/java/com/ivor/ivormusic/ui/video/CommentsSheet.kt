package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.CommentItem

/**
 * Bottom sheet showing the comments of the current video, with infinite
 * scroll pagination and expandable replies.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentsSheet(
    comments: List<CommentItem>,
    replies: Map<String, List<CommentItem>>,
    loadingReplyIds: Set<String>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    commentsAvailable: Boolean,
    onLoadMore: () -> Unit,
    onLoadReplies: (CommentItem) -> Unit,
    onDismiss: () -> Unit
) {
    // Opens half-height (YouTube style), draggable to full
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

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
                            start = 24.dp, end = 24.dp, bottom = 48.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        items(comments.size, key = { comments[it].commentId }) { index ->
                            val comment = comments[index]
                            Column {
                                CommentRow(comment = comment)

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
                                            .padding(start = 48.dp, top = 8.dp)
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
                                            CommentRow(comment = reply, isReply = true)
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
    }
}

@Composable
private fun CommentRow(
    comment: CommentItem,
    isReply: Boolean = false
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

            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            // Like count + creator heart
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.ThumbUp,
                    contentDescription = "Likes",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = comment.likeCount.ifEmpty { "0" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        }
    }
}
