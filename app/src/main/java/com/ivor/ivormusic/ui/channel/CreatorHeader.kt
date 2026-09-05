package com.ivor.ivormusic.ui.channel
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * The header that says who a creator is, shared by the video-mode channel page
 * and the music-mode artist page.
 *
 * **Why it is shared.** A musician's YouTube channel and their YouTube Music
 * artist page are one person with two faces, and the two screens genuinely
 * differ below the fold - one lists uploads, the other lists releases. What
 * must not differ is the person: arriving at the same creator from video mode
 * and from music mode should not feel like arriving at two different people.
 * So the identity is one component and the content is two screens, rather than
 * two headers that drift until the same name has two avatars and two follower
 * counts.
 *
 * [actions] is the slot that keeps them honest about the difference. The
 * channel page puts Subscribe and Share there; the artist page puts Play,
 * Shuffle and Radio there as well, because those mean something for a
 * discography and nothing for an upload feed.
 *
 * Everything except [name] is optional, and every one of those absences is
 * real: plenty of channels have no banner, new ones have no description, local
 * artists have no handle and no subscriber count at all.
 */
@Composable
fun CreatorHeader(
    name: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    bannerUrl: String? = null,
    isVerified: Boolean = false,
    /** "@handle", subscriber count, video count - whichever are known. */
    metadata: List<String> = emptyList(),
    description: String? = null,
    onDescriptionClick: (() -> Unit)? = null,
    /**
     * Fed the collapsing header's own scroll offset in pixels so the banner can
     * drift at half speed behind the content. Zero is a perfectly good value
     * and the header simply sits still.
     */
    scrollOffsetPx: Float = 0f,
    actions: @Composable () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CreatorBanner(
            bannerUrl = bannerUrl,
            avatarUrl = avatarUrl,
            scrollOffsetPx = scrollOffsetPx
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CHANNEL_GUTTER)
                // Lifts the avatar into the banner so the two read as one
                // block. Negative padding rather than a Box overlay, so the
                // text below reflows correctly when the name wraps to two
                // lines.
                .padding(top = if (bannerUrl != null) 0.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CreatorAvatar(
                avatarUrl = avatarUrl,
                name = name,
                modifier = Modifier
                    .size(84.dp)
                    .graphicsLayer { translationY = -28.dp.toPx() }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { translationY = -8.dp.toPx() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVerified) {
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.cd_verified),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (metadata.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = metadata.joinToString("  •  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CHANNEL_GUTTER)
                    .then(
                        if (onDescriptionClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onDescriptionClick)
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.padding(horizontal = CHANNEL_GUTTER)) { actions() }
    }
}

/**
 * The banner, or something worth looking at when there is not one.
 *
 * A missing banner is common enough that treating it as an empty box would
 * make a large share of channels open onto a blank rectangle. The fallback
 * builds a backdrop out of what the channel does have - its avatar, scaled up
 * behind a theme gradient and cut into an Expressive shape - so the page still
 * has a top, and it is the creator's own artwork rather than a generic
 * placeholder.
 */
@Composable
private fun CreatorBanner(
    bannerUrl: String?,
    avatarUrl: String?,
    scrollOffsetPx: Float
) {
    val height = if (bannerUrl != null) 156.dp else 128.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(0.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Half speed, so the banner sinks behind the content rather
                    // than travelling with it. Clamped to its own height: the
                    // caller reports a very large offset once the header has
                    // scrolled out of the list entirely, and an unclamped
                    // parallax would translate the layer arbitrarily far during
                    // the frame that state changes on.
                    translationY = (scrollOffsetPx * 0.5f).coerceIn(0f, size.height)
                }
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        )
                )
                if (!avatarUrl.isNullOrBlank()) {
                    // Requested at a real size: this is 150dp of picture, and
                    // a channel avatar arrives from InnerTube at =s48 or =s88.
                    AsyncImage(
                        model = com.ivor.ivormusic.data.googleImageAtSize(
                            avatarUrl,
                            com.ivor.ivormusic.data.AVATAR_TARGET_PX
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 24.dp)
                            .size(150.dp)
                            .clip(MaterialShapes.Cookie9Sided.toShape())
                            .graphicsLayer { alpha = 0.35f }
                    )
                }
            }
        }

        // Hands the banner off to the page rather than ending on a hard edge,
        // and guarantees the avatar and name below have something to sit on
        // whatever the artwork happens to be.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                    )
                )
        )
    }
}

/** Circular avatar with the initial as the fallback, ringed against the banner. */
@Composable
fun CreatorAvatar(
    avatarUrl: String?,
    name: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(
            3.dp,
            MaterialTheme.colorScheme.surface
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!avatarUrl.isNullOrBlank()) {
                com.ivor.ivormusic.ui.components.AvatarImage(
                    url = avatarUrl,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    indicatorSize = 26.dp,
                    // The branch below already draws the no-picture case with
                    // this header's own initial, which says more than a glyph.
                    emptyIcon = null
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * A Subscribe button that changes its whole shape, not just its label.
 *
 * The state has to be readable at a glance from across the header, which a
 * colour swap alone does not achieve on every one of the app's 29 palettes.
 * The corner radius carries it: subscribed is a settled pill, not-subscribed
 * is a squarer, more clickable-looking button, and the spring between them is
 * the house bouncy default because this is a touch-driven state change.
 */
@Composable
fun SubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSubscribed) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "subscribeContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSubscribed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "subscribeContent"
    )
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSubscribed) 22.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "subscribeCorner"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isSubscribed,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                }
            }
            Text(
                text = if (isSubscribed) stringResource(R.string.subscribed) else stringResource(R.string.subscribe),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * The horizontal gutter every channel-page surface lines up on, matching the
 * video feed's cards so the two do not disagree by four pixels.
 */
internal val CHANNEL_GUTTER = 16.dp

/**
 * Lets a child draw [amount] wider than its parent allows, on both sides.
 *
 * The channel page is one grid whose content padding gives every card its
 * gutter, and the banner and tab strip are the two things that must ignore it:
 * a banner inset by 16dp is not a banner, and a scrollable tab row that stops
 * short of the edge cuts its own scroll target off. Compose has no negative
 * padding, so the constraint is widened and the child placed back over the
 * gutter it was given.
 */
internal fun Modifier.bleedHorizontally(amount: Dp = CHANNEL_GUTTER): Modifier =
    this.layout { measurable, constraints ->
        val extra = amount.roundToPx() * 2
        val widened = constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                constraints.maxWidth + extra
            }
        )
        val placeable = measurable.measure(widened)
        layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
            placeable.place(-amount.roundToPx(), 0)
        }
    }

/** The metadata line, dropping the parts a creator does not have. */
fun creatorMetadata(vararg parts: String?): List<String> =
    parts.filterNotNull().map { it.trim() }.filter { it.isNotBlank() }
