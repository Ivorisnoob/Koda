package com.ivor.ivormusic.video

import com.ivor.ivormusic.data.ChannelPost
import com.ivor.ivormusic.data.YouTubeRepository
import com.ivor.ivormusic.video.core.likeToken
import com.ivor.ivormusic.video.core.pollToken
import com.ivor.ivormusic.video.core.withLike
import com.ivor.ivormusic.video.core.withPollTap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Likes and poll votes on community posts: each shown at once and put back if
 * YouTube refuses it.
 *
 * Both are `comment/perform_comment_action` tokens the post carries when it was
 * fetched signed in [verified November 2026]. The changed post lives in [edits]
 * rather than in the card, because a card leaves composition as it scrolls away
 * and a vote must still be there when it comes back.
 */
class PostInteractions(
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
) {
    private val _edits = MutableStateFlow<Map<String, ChannelPost>>(emptyMap())
    val edits: StateFlow<Map<String, ChannelPost>> = _edits.asStateFlow()

    /** Posts with a write on its way, so a double tap cannot race itself. */
    private val writing = mutableSetOf<String>()

    fun toggleLike(post: ChannelPost) {
        val current = _edits.value[post.postId] ?: post
        write(current, current.likeToken(), current.withLike(!current.isLiked))
    }

    fun tapPollChoice(post: ChannelPost, index: Int) {
        val current = _edits.value[post.postId] ?: post
        write(current, current.pollToken(index), current.withPollTap(index))
    }

    /** Forget every edit: they belong to the account that made them. */
    fun clear() {
        _edits.value = emptyMap()
    }

    private fun write(current: ChannelPost, token: String?, next: ChannelPost) {
        if (token == null || !writing.add(current.postId)) return
        _edits.value = _edits.value + (current.postId to next)
        scope.launch {
            try {
                if (!repository.performCommentAction(token) && _edits.value[current.postId] === next) {
                    _edits.value = _edits.value + (current.postId to current)
                }
            } finally {
                writing.remove(current.postId)
            }
        }
    }
}

/**
 * What a post card can do, built by the screen hosting it. Required on the card
 * on purpose: an optional callback is how a feature ships half-wired.
 */
class PostCardActions(
    val signedIn: Boolean,
    val edits: Map<String, ChannelPost>,
    /** Either writes, or asks for a sign-in; the host decides. */
    val onToggleLike: (ChannelPost) -> Unit,
    val onPollChoice: (ChannelPost, Int) -> Unit,
)
