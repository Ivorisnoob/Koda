package com.ivor.ivormusic.ui.video

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.ui.channel.ChannelPhotoViewer
import com.ivor.ivormusic.ui.channel.ChannelPostCard
import com.ivor.ivormusic.ui.channel.PostCommentsContext
import com.ivor.ivormusic.ui.home.HomeViewModel

/**
 * Community posts scattered between the videos of a feed (Home and the
 * Subscriptions tab). The posts themselves come from
 * [HomeViewModel.feedPosts]; this file is where they go and how they are drawn.
 */

/** Videos a feed shows for each community post scattered into it. */
internal const val FEED_VIDEOS_PER_POST = 8

/** Videos ahead of the first post, so a feed never opens on one. */
private const val FEED_POST_FIRST_AFTER = 4

/** How far past the first visible row posts are fetched for. */
private const val FEED_POST_LOOKAHEAD = 16

/**
 * [videoListItems] with a post after the first [FEED_POST_FIRST_AFTER] videos
 * and then after every [FEED_VIDEOS_PER_POST]. Both are even, so
 * the two-up grid layout never breaks a row around a post, and a post only
 * follows a full run of videos, so a short feed does not end on one. With no
 * posts this is exactly [videoListItems].
 */
fun LazyListScope.videoListItemsWithPosts(
    videos: List<VideoItem>,
    posts: List<ChannelPost>,
    layout: String,
    keyPrefix: String = "",
    post: @Composable (ChannelPost) -> Unit,
    card: @Composable (VideoItem, Modifier) -> Unit,
) {
    if (posts.isEmpty()) {
        videoListItems(videos, layout, keyPrefix, card)
        return
    }
    // Deduplicated before it is cut into runs: each run is keyed on its own,
    // and a video repeated across two of them would be a duplicate key.
    val unique = videos.distinctBy { it.videoId }
    var start = 0
    var postIndex = 0
    var run = FEED_POST_FIRST_AFTER
    while (start < unique.size) {
        val end = minOf(unique.size, start + run)
        videoListItems(unique.subList(start, end), layout, keyPrefix, card)
        if (end - start == run && postIndex < posts.size) {
            val feedPost = posts[postIndex++]
            item(key = keyPrefix + "post_" + feedPost.postId, contentType = "feed_post") {
                post(feedPost)
            }
        }
        start = end
        run = FEED_VIDEOS_PER_POST
    }
}

/**
 * Asks for posts as the feed is scrolled, not for the whole feed at once: a
 * hundred videos would otherwise cost a dozen posts' worth of channel browses
 * for rows nobody reaches. Re-runs as posts arrive, so a batch that fell short
 * is followed by another.
 */
@Composable
fun FeedPostDemand(
    viewModel: HomeViewModel,
    listState: LazyListState,
    videoCount: Int,
    postCount: Int,
    enabled: Boolean = true
) {
    val reach by remember(listState) {
        derivedStateOf {
            (listState.firstVisibleItemIndex / FEED_POST_LOOKAHEAD + 1) * FEED_POST_LOOKAHEAD
        }
    }
    LaunchedEffect(reach, videoCount, postCount, enabled) {
        if (enabled && videoCount > 0) viewModel.ensureFeedPosts(minOf(videoCount, reach))
    }
}

/** A post as a feed row: the channel page's card, with its author a way to the channel. */
@Composable
fun FeedPostCard(
    post: ChannelPost,
    viewModel: HomeViewModel,
    onPlayVideo: (VideoItem) -> Unit,
    onOpenChannel: ((String) -> Unit)?,
    photoViewer: MutableState<Pair<List<String>, Int>?>
) {
    val channelId = post.channelId
    ChannelPostCard(
        post = post,
        onPlayVideo = onPlayVideo,
        onOpenComments = if (post.detailParams != null) {
            { viewModel.openFeedPostComments(post) }
        } else {
            null
        },
        modifier = Modifier.padding(horizontal = 16.dp),
        onOpenPhotos = { images, index -> photoViewer.value = images to index },
        onAuthorClick = if (channelId != null && onOpenChannel != null) {
            { onOpenChannel(channelId) }
        } else {
            null
        }
    )
}

/**
 * What a feed post opens over the feed: its photos fullscreen, and its
 * comments in the shared sheet with the post pinned above the thread. Hosted
 * once per screen, outside the list.
 */
@Composable
fun FeedPostOverlays(
    viewModel: HomeViewModel,
    photoViewer: MutableState<Pair<List<String>, Int>?>,
    onOpenChannel: ((String) -> Unit)?
) {
    photoViewer.value?.let { (images, index) ->
        ChannelPhotoViewer(
            images = images,
            startIndex = index,
            onDismiss = { photoViewer.value = null }
        )
    }

    val commentsPost by viewModel.feedCommentsPost.collectAsState()
    var showCommentSignIn by remember { mutableStateOf(false) }
    commentsPost?.let { post ->
        val thread = viewModel.feedPostComments
        val isLoggedInNow by viewModel.isYouTubeConnected.collectAsState()
        val threadComments by thread.comments.collectAsState()
        val threadReplies by thread.replies.collectAsState()
        val loadingReplyIds by thread.loadingReplyIds.collectAsState()
        val isThreadLoading by thread.isLoading.collectAsState()
        val isThreadLoadingMore by thread.isLoadingMore.collectAsState()
        val isThreadAvailable by thread.isAvailable.collectAsState()
        val canComment by thread.canComment.collectAsState()
        val isPosting by thread.isPosting.collectAsState()
        CommentsSheet(
            comments = threadComments,
            replies = threadReplies,
            loadingReplyIds = loadingReplyIds,
            isLoading = isThreadLoading,
            isLoadingMore = isThreadLoadingMore,
            commentsAvailable = isThreadAvailable,
            canComment = canComment,
            isPosting = isPosting,
            onLoadMore = thread::loadMore,
            onLoadReplies = thread::loadReplies,
            onPostComment = thread::postComment,
            onPostReply = thread::postReply,
            // Signed out, a like would be refused and flicker back; ask for
            // the sign-in instead, as the players do.
            onLikeComment = { comment ->
                if (isLoggedInNow) thread.toggleLike(comment) else showCommentSignIn = true
            },
            onDeleteComment = thread::delete,
            onDismiss = viewModel::closeFeedPostComments,
            onOpenAuthor = { channelId ->
                viewModel.closeFeedPostComments()
                onOpenChannel?.invoke(channelId)
            },
            heightFraction = 0.92f,
            header = { PostCommentsContext(post) },
            unavailableMessage = stringResource(R.string.ch_post_comments_unavailable)
        )
    }
    if (showCommentSignIn) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = { showCommentSignIn = false },
            onAuthSuccess = {
                showCommentSignIn = false
                viewModel.checkYouTubeConnection(force = true)
                // The thread was fetched signed out: no composer, no like state.
                viewModel.reloadFeedPostComments()
            }
        )
    }
}
