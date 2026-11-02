package com.ivor.ivormusic.video.core

import com.ivor.ivormusic.data.ChannelPollChoice
import com.ivor.ivormusic.data.ChannelPost

/**
 * What a like or a poll vote does to a post, shown at once and undone if
 * YouTube refuses. Pure, so the rules are testable away from the network.
 */

/** The post as it reads with the like on or off. */
fun ChannelPost.withLike(liked: Boolean): ChannelPost = copy(
    isLiked = liked,
    voteCountText = (if (liked) likedCountText else unlikedCountText) ?: voteCountText,
)

/** The token that does what tapping the like button means right now. */
fun ChannelPost.likeToken(): String? = if (isLiked) unlikeAction else likeAction

val ChannelPost.hasVoted: Boolean get() = pollChoices.any { it.isSelected }

/** The token for tapping choice [index]: a tap on your own vote takes it back. */
fun ChannelPost.pollToken(index: Int): String? {
    val choice = pollChoices.getOrNull(index) ?: return null
    return if (choice.isSelected) choice.deselectAction else choice.selectAction
}

/** The post after tapping choice [index]. A poll holds one vote, so any other goes. */
fun ChannelPost.withPollTap(index: Int): ChannelPost {
    val tappedOwnVote = pollChoices.getOrNull(index)?.isSelected == true
    return copy(
        pollChoices = pollChoices.mapIndexed { i, choice ->
            choice.copy(isSelected = i == index && !tappedOwnVote)
        }
    )
}

/**
 * The share to draw for this choice. YouTube sends each choice's figures for
 * both cases, so a vote moves the bars without another request.
 */
fun ChannelPollChoice.shareRatio(): Float =
    (if (isSelected) ratioIfSelected else ratioIfNotSelected)?.coerceIn(0f, 1f) ?: 0f

fun ChannelPollChoice.shareText(): String? =
    if (isSelected) percentIfSelected else percentIfNotSelected
