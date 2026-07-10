package com.ivor.ivormusic.ui.shorts

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LikeStatus
import com.ivor.ivormusic.ui.video.CommentsSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive as coroutineIsActive

/**
 * Fullscreen vertical-swipe Shorts player, layered above the NavHost like
 * VideoPlayerOverlay. One shared ExoPlayer follows the settled pager page;
 * neighbouring pages show their portrait thumbnails.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortsPlayerOverlay(viewModel: ShortsPlayerViewModel) {
    val isActive by viewModel.isActive.collectAsState()
    if (!isActive) return

    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    val shorts by viewModel.shorts.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val playbackError by viewModel.playbackError.collectAsState()
    val engagement by viewModel.engagement.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    val comments by viewModel.comments.collectAsState()
    val commentReplies by viewModel.replies.collectAsState()
    val loadingReplyIds by viewModel.loadingReplyIds.collectAsState()
    val isCommentsLoading by viewModel.isCommentsLoading.collectAsState()
    val isLoadingMoreComments by viewModel.isLoadingMoreComments.collectAsState()
    val canComment by viewModel.canComment.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()

    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }

    fun requireLogin(action: () -> Unit) {
        if (isLoggedIn) action() else showSignInDialog = true
    }

    BackHandler { viewModel.close() }

    // Keep the screen awake while a Short is playing
    DisposableEffect(isPlaying) {
        val window = activity?.window
        if (isPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // The whole overlay leaves composition on close, so this pager (and its
    // initial page) is recreated fresh on every open
    val pagerState = rememberPagerState(
        initialPage = viewModel.currentIndex.value
    ) { shorts.size }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.onPageSelected(page)
        }
    }

    // Thin playback progress bar at the very bottom, YouTube-style
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentVideo) {
        progress = 0f
        while (coroutineIsActive) {
            val player = viewModel.exoPlayer
            val duration = player?.duration ?: 0L
            progress = if (duration > 0) {
                (player?.currentPosition ?: 0L).toFloat() / duration.toFloat()
            } else 0f
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume clicks so the app underneath stays inert */ }
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = shorts[page]
            val isCurrent = page == pagerState.settledPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { if (isCurrent) viewModel.togglePlayPause() }
            ) {
                // Portrait thumbnail behind the surface: visible on
                // neighbouring pages and while the current one buffers
                AsyncImage(
                    model = item.portraitThumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (isCurrent && playbackError == null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        update = { pv -> pv.player = viewModel.exoPlayer },
                        onRelease = { pv -> pv.player = null },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isCurrent) {
                    // Buffering: expressive shape-morphing loader
                    AnimatedVisibility(
                        visible = isBuffering && playbackError == null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(56.dp),
                            color = Color.White
                        )
                    }

                    // Paused badge with a springy pop
                    AnimatedVisibility(
                        visible = !isPlaying && !isBuffering && playbackError == null,
                        enter = scaleIn(
                            initialScale = 0.6f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        ) + fadeIn(),
                        exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (playbackError != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = playbackError?.message ?: "Playback failed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.retryCurrent() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }

                // Bottom scrim for metadata + action legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
            }
        }

        // Top scrim + bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.close() },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Close Shorts"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Shorts",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Metadata (bottom-left) + action rail (bottom-right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val video = currentVideo
                if (video != null && video.channelName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!video.channelIconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = video.channelIconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val isSubscribed = engagement?.isSubscribed == true
                        Button(
                            onClick = { requireLogin { viewModel.toggleSubscribe() } },
                            colors = if (isSubscribed) {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 14.dp, vertical = 6.dp
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (video != null && video.title.isNotBlank()) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val likeStatus = engagement?.likeStatus ?: LikeStatus.INDIFFERENT
                ShortsAction(
                    icon = if (likeStatus == LikeStatus.LIKE) Icons.Rounded.ThumbUp
                        else Icons.Outlined.ThumbUp,
                    label = engagement?.likeCount ?: "Like",
                    active = likeStatus == LikeStatus.LIKE,
                    contentDescription = "Like",
                    onClick = { requireLogin { viewModel.toggleLike() } }
                )
                ShortsAction(
                    icon = if (likeStatus == LikeStatus.DISLIKE) Icons.Rounded.ThumbDown
                        else Icons.Outlined.ThumbDown,
                    label = "Dislike",
                    active = likeStatus == LikeStatus.DISLIKE,
                    contentDescription = "Dislike",
                    onClick = { requireLogin { viewModel.toggleDislike() } }
                )
                ShortsAction(
                    icon = Icons.Rounded.ChatBubble,
                    label = "Comments",
                    contentDescription = "Comments",
                    onClick = {
                        viewModel.ensureCommentsLoaded()
                        showCommentsSheet = true
                    }
                )
                ShortsAction(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    contentDescription = "Share",
                    onClick = {
                        val videoId = currentVideo?.videoId ?: return@ShortsAction
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "https://youtube.com/shorts/$videoId")
                        }
                        context.startActivity(Intent.createChooser(send, "Share Short"))
                    }
                )
            }
        }

        // Thin progress line hugging the bottom edge
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.25f),
            drawStopIndicator = {}
        )
    }

    if (showCommentsSheet) {
        CommentsSheet(
            comments = comments,
            replies = commentReplies,
            loadingReplyIds = loadingReplyIds,
            isLoading = isCommentsLoading,
            isLoadingMore = isLoadingMoreComments,
            commentsAvailable = engagement?.commentsToken != null,
            canComment = canComment,
            isPosting = isPostingComment,
            onLoadMore = { viewModel.loadMoreComments() },
            onLoadReplies = { viewModel.loadReplies(it) },
            onPostComment = { viewModel.postComment(it) },
            onPostReply = { target, threadParent, text ->
                viewModel.postReply(target, threadParent, text)
            },
            onLikeComment = { comment -> requireLogin { viewModel.toggleCommentLike(comment) } },
            onDeleteComment = { comment -> viewModel.deleteComment(comment) },
            onDismiss = { showCommentsSheet = false }
        )
    }

    if (showSignInDialog) {
        com.ivor.ivormusic.ui.auth.YouTubeAuthDialog(
            onDismiss = { showSignInDialog = false },
            onAuthSuccess = {
                showSignInDialog = false
                viewModel.onLoginStateChanged()
            }
        )
    }
}

/**
 * One button of the right-hand action rail: circular translucent icon
 * button with a spring scale bump when it becomes active, label underneath.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortsAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "actionScale"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                contentColor = if (active) MaterialTheme.colorScheme.primary else Color.White
            ),
            modifier = Modifier
                .size(48.dp)
                .scale(scale)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1
        )
    }
}
