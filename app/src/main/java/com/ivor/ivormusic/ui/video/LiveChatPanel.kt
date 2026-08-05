package com.ivor.ivormusic.ui.video

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.LiveChatAuthor
import com.ivor.ivormusic.data.LiveChatBadge
import com.ivor.ivormusic.data.LiveChatBadgeKind
import com.ivor.ivormusic.data.LiveChatBanner
import com.ivor.ivormusic.data.LiveChatMessage
import com.ivor.ivormusic.data.LiveChatRun
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Slack at the live edge. A settled scroll within this many pixels of the
 * newest message still counts as following, so a stray flick does not pause
 * chat.
 */
private const val LIVE_EDGE_TOLERANCE_PX = 120

/** Characters left before the message-length counter is worth showing. */
private const val COUNTER_VISIBLE_AT = 30

/**
 * Live chat, in two dresses.
 *
 * The portrait dress slides up over the info area the way [CommentsPanel]
 * does - opaque surfaceContainer, rounded top, a composer pinned at the
 * bottom. The [compact] dress is the landscape one: a narrow translucent
 * column docked to the right of the video, so chat reads alongside the stream
 * instead of covering it.
 *
 * The list runs reversed, which is what makes it behave like a chat rather
 * than a feed: new messages enter at the visual bottom and the view stays
 * pinned there on its own, while scrolling up to read back holds position and
 * surfaces the jump-to-latest pill instead of yanking the user forward.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveChatPanel(
    messages: List<LiveChatMessage>,
    banner: LiveChatBanner?,
    isLoading: Boolean,
    /** Null while the first poll is still in flight. */
    isAvailable: Boolean?,
    canSend: Boolean,
    isSending: Boolean,
    maxMessageLength: Int,
    restriction: String?,
    onSend: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /**
     * Whether the view is riding the live edge.
     *
     * This cannot be derived from the scroll position alone. The list is
     * reversed, so an arriving message is *prepended*, and Compose keeps the
     * viewport anchored on the message the user was looking at - which pushes
     * firstVisibleItemIndex off 0 without the user having scrolled at all. Read
     * naively that looks identical to scrolling back, and the auto-follow
     * switches itself off after the very first message.
     *
     * So a drag is the only thing that detaches it. Touching the list pauses
     * chat immediately, and where it comes to rest - after any fling - decides
     * whether following resumes. The follow-scrolls this screen performs itself
     * are deliberately not consulted: one of them settling a row short while a
     * message lands mid-animation would otherwise latch the pause on and freeze
     * the panel behind a pill.
     */
    var followLatest by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var userDragged = false
        launch {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) {
                    userDragged = true
                    followLatest = false
                }
            }
        }
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (!userDragged) return@collect
                userDragged = false
                followLatest = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < LIVE_EDGE_TOLERANCE_PX
            }
    }

    // Index 0 is the newest message. Follow it while the user is at the live
    // edge; a large jump (the opening backlog, or a flush) snaps rather than
    // animating through hundreds of rows.
    LaunchedEffect(messages.lastOrNull()?.id, followLatest) {
        if (!followLatest || messages.isEmpty()) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 4) {
            listState.scrollToItem(0)
        } else {
            listState.animateScrollToItem(0)
        }
    }

    // How much was missed while reading back, so the pill can say so rather
    // than just offering a ride to the bottom.
    var pausedAtId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(followLatest) {
        pausedAtId = if (followLatest) null else messages.lastOrNull()?.id
    }
    val missedCount = remember(messages, pausedAtId) {
        val anchor = pausedAtId
        if (anchor == null) {
            0
        } else {
            val index = messages.indexOfLast { it.id == anchor }
            // The anchor aging out of the capped buffer means everything on
            // screen is newer than it.
            if (index < 0) messages.size else messages.lastIndex - index
        }
    }

    val containerColor = if (compact) {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        shape = if (compact) {
            RoundedCornerShape(28.dp)
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (compact) 16.dp else 24.dp,
                        end = if (compact) 4.dp else 12.dp,
                        top = if (compact) 8.dp else 12.dp,
                        bottom = 4.dp,
                    ),
            ) {
                LiveDot()
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Live chat",
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close live chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            banner?.let {
                LiveChatBannerCard(
                    banner = it,
                    modifier = Modifier.padding(
                        horizontal = if (compact) 12.dp else 20.dp,
                        vertical = 4.dp,
                    ),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading && messages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    isAvailable == false -> {
                        LiveChatEmptyState(
                            text = "Live chat is turned off for this stream",
                        )
                    }

                    messages.isEmpty() -> {
                        LiveChatEmptyState(text = "Waiting for messages")
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = if (compact) 12.dp else 20.dp,
                                end = if (compact) 12.dp else 20.dp,
                                top = 12.dp,
                                bottom = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
                        ) {
                            val ordered = messages.asReversed()
                            items(
                                count = ordered.size,
                                key = { ordered[it].id },
                            ) { index ->
                                LiveChatMessageRow(
                                    message = ordered[index],
                                    compact = compact,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }

                // Reading back through chat should not be interrupted by the
                // stream of new messages, so getting back to the bottom is an
                // explicit tap rather than an automatic jump.
                //
                // Qualified: this Box is lexically inside a Column, whose
                // scoped AnimatedVisibility would otherwise win overload
                // resolution and fail to compile.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !followLatest && messages.isNotEmpty(),
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                ) {
                    JumpToLatestPill(
                        missedCount = missedCount,
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                                followLatest = true
                            }
                        },
                    )
                }
            }

            LiveChatComposer(
                canSend = canSend,
                isSending = isSending,
                maxMessageLength = maxMessageLength,
                restriction = restriction,
                compact = compact,
                onSend = onSend,
            )
        }
    }
}

/**
 * Live chat as an overlay on the stream itself, for the vertical live player.
 *
 * Neither dress of [LiveChatPanel] fits a full-bleed portrait video: the
 * portrait one covers the stream outright, and the compact one docks against a
 * side that does not exist once the video is the whole screen. This is a
 * read-only ticker instead - the newest messages fading up into the frame,
 * older ones dimming as they climb - so chat stays legible without putting a
 * slab of surface color over what the user came to watch.
 *
 * The scrim behind it belongs to the caller, not to this composable. The
 * vertical live player stacks a title and a channel row above the ticker, and
 * a gradient that started here would leave those two sitting on bare video -
 * one scrim spanning the whole bottom stack is both more legible and one less
 * darkening to compound.
 *
 * Read-only is the point: sending, scrolling back and the jump-to-latest pill
 * all still belong to the full panel, which [onOpenFullChat] opens.
 */
@Composable
fun LiveChatOverlay(
    messages: List<LiveChatMessage>,
    isAvailable: Boolean?,
    canSend: Boolean,
    onOpenFullChat: () -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 5,
) {
    // A stream with chat turned off should cost the viewer nothing - no ticker,
    // no empty state, just video.
    if (isAvailable == false) return

    val recent = remember(messages, maxVisible) {
        messages.takeLast(maxVisible).asReversed()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            // Reversed so the newest message sits at the visual bottom and the
            // list grows upward into the fade. Scrolling is the full panel's
            // job - here it would fight the player's gesture surface.
            reverseLayout = true,
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp),
        ) {
            items(count = recent.size, key = { recent[it].id }) { index ->
                LiveChatOverlayRow(
                    message = recent[index],
                    // Index 0 is the newest. Older messages recede rather than
                    // vanishing at a hard edge.
                    modifier = Modifier
                        .animateItem()
                        .alpha(1f - (index * 0.16f).coerceAtMost(0.62f)),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // The one control in the ticker, so it is the one thing here that wears
        // the app's colors rather than the video's. Everything above it is text
        // on a frame and stays white; this is a surface, and a surface that
        // ignored the palette is what made the whole screen read as foreign.
        Surface(
            onClick = onOpenFullChat,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = if (canSend) "Say something..." else "Open live chat",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Open live chat",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * One ticker row. Text messages get the flat light treatment; Super Chats,
 * memberships and gifts keep their own cards - they carry colors YouTube sent
 * with the message and read fine against video.
 */
@Composable
private fun LiveChatOverlayRow(message: LiveChatMessage, modifier: Modifier = Modifier) {
    when (message) {
        is LiveChatMessage.Text -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AuthorAvatar(message.author, 20.dp)
            LiveChatRunsText(
                runs = message.runs,
                prefix = message.author,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 3,
                authorColorOverride = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.weight(1f),
            )
        }

        is LiveChatMessage.System -> LiveChatRunsText(
            runs = message.runs,
            prefix = null,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 2,
            modifier = modifier.fillMaxWidth(),
        )

        else -> LiveChatMessageRow(message = message, compact = true, modifier = modifier)
    }
}

@Composable
private fun LiveChatEmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Chat is paused while reading back. Naming the number of missed messages is
 * what makes it a status rather than just a button - it tells the user the
 * stream is still running behind the scroll.
 */
@Composable
private fun JumpToLatestPill(missedCount: Int, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Rounded.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = when {
                    missedCount <= 0 -> "Latest"
                    missedCount == 1 -> "1 new message"
                    else -> "$missedCount new messages"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * One entry in the chat stream. Ordinary messages stay visually quiet - they
 * are 95% of the traffic - while Super Chats, memberships and gifts get a
 * container so they stand out the way they do on YouTube.
 */
@Composable
private fun LiveChatMessageRow(
    message: LiveChatMessage,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    when (message) {
        is LiveChatMessage.Text -> LiveChatTextRow(message, compact, modifier)
        is LiveChatMessage.Paid -> LiveChatPaidRow(message, compact, modifier)
        is LiveChatMessage.Membership -> LiveChatEventRow(
            author = message.author,
            icon = Icons.Rounded.WorkspacePremium,
            headline = message.headline,
            detail = message.tierName,
            modifier = modifier,
        )

        is LiveChatMessage.Gift -> LiveChatEventRow(
            author = message.author,
            icon = Icons.Rounded.CardGiftcard,
            headline = "${message.author.name} ${message.text}".trim(),
            detail = null,
            modifier = modifier,
        )

        is LiveChatMessage.System -> LiveChatSystemRow(message, modifier)
    }
}

@Composable
private fun LiveChatTextRow(
    message: LiveChatMessage.Text,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val avatarSize = if (compact) 20.dp else 24.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuthorAvatar(message.author, avatarSize)
        // Author name and message share one text flow so a short message wraps
        // beside the name instead of claiming a line of its own, which is what
        // keeps a busy chat readable in a narrow column.
        LiveChatRunsText(
            runs = message.runs,
            prefix = message.author,
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveChatPaidRow(
    message: LiveChatMessage.Paid,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    // The only colors in the app that do not come from the ColorScheme: they
    // are part of the Super Chat amount tier, sent by YouTube with the message.
    val headerColor = Color(message.headerBackgroundColor.toInt())
    val bodyColor = Color(message.bodyBackgroundColor.toInt())
    val headerText = Color(message.headerTextColor.toInt())
    val bodyText = Color(message.bodyTextColor.toInt())

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bodyColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuthorAvatar(message.author, 24.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = message.author.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = headerText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = message.amountText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = headerText,
                    )
                }
            }
            message.stickerUrl?.let { sticker ->
                AsyncImage(
                    model = sticker,
                    contentDescription = "Super Sticker",
                    modifier = Modifier
                        .padding(12.dp)
                        .size(if (compact) 56.dp else 72.dp),
                )
            }
            if (message.runs.isNotEmpty()) {
                LiveChatRunsText(
                    runs = message.runs,
                    prefix = null,
                    style = if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = bodyText,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun LiveChatEventRow(
    author: LiveChatAuthor,
    icon: ImageVector,
    headline: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                detail?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (author.name.isNotBlank() && !headline.startsWith(author.name)) {
                    Text(
                        text = author.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveChatSystemRow(
    message: LiveChatMessage.System,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LiveChatRunsText(
            runs = message.runs,
            prefix = null,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LiveChatBannerCard(banner: LiveChatBanner, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            LiveChatRunsText(
                runs = banner.runs,
                prefix = banner.author,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 6,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AuthorAvatar(author: LiveChatAuthor, size: Dp) {
    if (!author.photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = author.photoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = author.name.trimStart('@').take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Render a chat message as one text flow: optional author name and badges,
 * then the message runs.
 *
 * Custom channel emoji are images inside the text, so they ride as
 * [InlineTextContent] rather than being dropped or replaced with their
 * shortcut. Standard unicode emoji already carry the character and need no
 * such treatment.
 */
@Composable
private fun LiveChatRunsText(
    runs: List<LiveChatRun>,
    prefix: LiveChatAuthor?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    /**
     * Overrides the role tint on the author name. The overlay dress sits on
     * video rather than on a surface, where the tertiary/primary/secondary
     * role colors are not guaranteed to carry contrast - it passes a flat
     * light tone instead and leans on the badges, which still render, to keep
     * owner/moderator/member legible.
     */
    authorColorOverride: Color? = null,
) {
    val authorColor = authorColorOverride ?: when {
        prefix?.isOwner == true -> MaterialTheme.colorScheme.tertiary
        prefix?.isModerator == true -> MaterialTheme.colorScheme.primary
        prefix?.isMember == true -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Filled while the string is built, so it must not survive recomposition -
    // a remembered map would keep entries for messages that are no longer here.
    val inlineContent = mutableMapOf<String, InlineTextContent>()

    val text = buildAnnotatedString {
        prefix?.let { author ->
            author.badges.forEachIndexed { index, badge ->
                val id = "badge-$index-${badge.tooltip}"
                appendInlineContent(id, badge.tooltip)
                append(" ")
                inlineContent[id] = InlineTextContent(
                    Placeholder(
                        width = style.fontSize,
                        height = style.fontSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    )
                ) { LiveChatBadgeIcon(badge) }
            }
            withStyle(SpanStyle(color = authorColor, fontWeight = FontWeight.Bold)) {
                append(author.name.trimStart('@'))
            }
            append("  ")
        }

        runs.forEachIndexed { index, run ->
            when (run) {
                is LiveChatRun.Text -> append(run.text)
                is LiveChatRun.Emoji -> {
                    if (run.imageUrl == null) {
                        append(run.label)
                    } else {
                        val id = "emoji-$index-${run.label}"
                        appendInlineContent(id, run.label)
                        inlineContent[id] = InlineTextContent(
                            Placeholder(
                                width = style.fontSize * 1.3f,
                                height = style.fontSize * 1.3f,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            )
                        ) {
                            AsyncImage(
                                model = run.imageUrl,
                                contentDescription = run.label,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    Text(
        text = text,
        style = style,
        color = color,
        inlineContent = inlineContent,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun LiveChatBadgeIcon(badge: LiveChatBadge) {
    when (badge.kind) {
        LiveChatBadgeKind.MEMBER -> {
            if (!badge.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = badge.imageUrl,
                    contentDescription = badge.tooltip,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        LiveChatBadgeKind.OWNER -> Icon(
            Icons.Rounded.WorkspacePremium,
            contentDescription = badge.tooltip,
            modifier = Modifier.fillMaxSize(),
            tint = MaterialTheme.colorScheme.tertiary,
        )

        LiveChatBadgeKind.MODERATOR -> Icon(
            Icons.Rounded.Shield,
            contentDescription = badge.tooltip,
            modifier = Modifier.fillMaxSize(),
            tint = MaterialTheme.colorScheme.primary,
        )

        LiveChatBadgeKind.VERIFIED -> Icon(
            Icons.Rounded.Verified,
            contentDescription = badge.tooltip,
            modifier = Modifier.fillMaxSize(),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The composer, in three states: open, closed by the creator ([restriction] -
 * subscribers-only, slow mode, a ban), or signed out.
 *
 * A send clears the field immediately, because the message appears in the list
 * in the same breath - the send response echoes it back. If the server rejects
 * it instead, the text is put back and the reason shown, so a message is never
 * silently swallowed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LiveChatComposer(
    canSend: Boolean,
    isSending: Boolean,
    maxMessageLength: Int,
    restriction: String?,
    compact: Boolean,
    onSend: (String, (String) -> Unit) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Nothing to type into when chat is closed - say why instead of offering a
    // field that cannot work.
    if (restriction != null) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = restriction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val remaining = maxMessageLength - text.length

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AnimatedVisibility(visible = error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    // YouTube hard-limits chat messages, so the field enforces it
                    // rather than letting the send fail server-side.
                    onValueChange = {
                        if (it.length <= maxMessageLength) {
                            text = it
                            error = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (canSend) "Chat..." else "Sign in to chat")
                    },
                    // Only worth the space once the limit is actually in reach.
                    suffix = if (remaining <= COUNTER_VISIBLE_AT) {
                        { Text("$remaining", style = MaterialTheme.typography.labelSmall) }
                    } else {
                        null
                    },
                    shape = CircleShape,
                    maxLines = if (compact) 2 else 4,
                    enabled = canSend && !isSending,
                    textStyle = if (compact) {
                        MaterialTheme.typography.bodySmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                )
                if (isSending) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val enabled = canSend && text.isNotBlank()
                    FilledIconButton(
                        onClick = {
                            val body = text
                            if (body.isNotBlank()) {
                                text = ""
                                error = null
                                onSend(body) { reason ->
                                    // Rejected: hand the text back so it is not lost.
                                    if (text.isBlank()) text = body
                                    error = reason
                                }
                            }
                        },
                        enabled = enabled,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The pulsing dot that marks anything live. Deliberately a slow, low-amplitude
 * pulse: it has to read as "this is happening now" from the corner of the eye
 * without competing with the video.
 */
@Composable
fun LiveDot(modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveDotAlpha",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha)),
    )
}

/**
 * "LIVE" pill plus the concurrent viewer count, for the metadata row and the
 * player chrome.
 *
 * The pill itself is an opaque errorContainer either way, so it reads
 * anywhere. The viewer count is bare text, and [onVideo] is what keeps it
 * legible when there is no surface behind it - onSurfaceVariant over a bright
 * frame in a light theme is close to invisible.
 */
@Composable
fun LiveBadge(
    viewerCount: String?,
    modifier: Modifier = Modifier,
    onVideo: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                LiveDot(size = 7.dp)
                Text(
                    text = "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        viewerCount?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (onVideo) {
                    Color.White.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
