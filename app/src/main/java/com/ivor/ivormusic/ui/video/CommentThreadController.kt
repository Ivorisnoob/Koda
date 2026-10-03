package com.ivor.ivormusic.ui.video

import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One comment thread's state and actions, for a host that is not a player:
 * load, page, expand replies, post, reply, like and delete, with the same
 * optimistic-then-revert rules the video and Shorts players use.
 *
 * The players still carry their own copies, interleaved with per-video
 * bookkeeping; this is the self-contained form, built for community posts,
 * that they could move onto. [viaBrowse] picks the endpoint the thread's
 * continuations answer on (see [YouTubeRepository.getCommentsPage]).
 *
 * Every write lands only if the thread it was started for is still the open
 * one: [generation] moves on each [load], so a slow response for a thread
 * the user has left cannot write into the next.
 */
class CommentThreadController(
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
    private val viaBrowse: Boolean
) {
    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments.asStateFlow()

    private val _replies = MutableStateFlow<Map<String, List<CommentItem>>>(emptyMap())
    val replies: StateFlow<Map<String, List<CommentItem>>> = _replies.asStateFlow()

    private val _loadingReplyIds = MutableStateFlow<Set<String>>(emptySet())
    val loadingReplyIds: StateFlow<Set<String>> = _loadingReplyIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    /** False once a load found no thread: comments off, or the fetch failed. */
    private val _isAvailable = MutableStateFlow(true)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _createCommentParams = MutableStateFlow<String?>(null)

    /** Signed in and the thread accepts new comments. */
    private val _canComment = MutableStateFlow(false)
    val canComment: StateFlow<Boolean> = _canComment.asStateFlow()

    private var nextToken: String? = null
    private var generation = 0L
    private var loadJob: Job? = null

    /**
     * Open a thread. [firstToken] resolves the first page's token (a post's
     * detail browse); returning null reads as "no comments here".
     */
    fun load(firstToken: suspend () -> String?) {
        val gen = ++generation
        loadJob?.cancel()
        _comments.value = emptyList()
        _replies.value = emptyMap()
        _loadingReplyIds.value = emptySet()
        _isLoadingMore.value = false
        _isPosting.value = false
        _isAvailable.value = true
        _createCommentParams.value = null
        _canComment.value = false
        nextToken = null
        _isLoading.value = true
        loadJob = scope.launch {
            try {
                val token = firstToken()
                val page = token?.let { repository.getCommentsPage(it, viaBrowse) }
                if (gen != generation) return@launch
                if (page == null) {
                    _isAvailable.value = false
                } else {
                    _comments.value = page.comments
                    nextToken = page.nextPageToken
                    _createCommentParams.value = page.createCommentParams
                    _canComment.value = page.createCommentParams != null && repository.isLoggedIn()
                }
            } finally {
                if (gen == generation) _isLoading.value = false
            }
        }
    }

    /** Leave the thread; anything still in flight for it is dropped. */
    fun clear() {
        generation++
        loadJob?.cancel()
        _comments.value = emptyList()
        _replies.value = emptyMap()
        _loadingReplyIds.value = emptySet()
        _isLoading.value = false
        _isLoadingMore.value = false
        _isPosting.value = false
        nextToken = null
    }

    fun loadMore() {
        val token = nextToken ?: return
        if (_isLoadingMore.value || _isLoading.value) return
        val gen = generation
        _isLoadingMore.value = true
        scope.launch {
            try {
                val page = repository.getCommentsPage(token, viaBrowse)
                if (gen == generation && page != null) {
                    val known = _comments.value.mapTo(HashSet()) { it.commentId }
                    _comments.value = _comments.value + page.comments.filter { it.commentId !in known }
                    nextToken = page.nextPageToken
                }
            } finally {
                if (gen == generation) _isLoadingMore.value = false
            }
        }
    }

    fun loadReplies(comment: CommentItem) {
        val token = comment.repliesToken ?: return
        if (_replies.value.containsKey(comment.commentId) ||
            comment.commentId in _loadingReplyIds.value
        ) return
        val gen = generation
        _loadingReplyIds.value = _loadingReplyIds.value + comment.commentId
        scope.launch {
            try {
                val page = repository.getCommentsPage(token, viaBrowse)
                if (gen == generation && page != null) {
                    _replies.value = _replies.value + (comment.commentId to page.comments)
                }
            } finally {
                if (gen == generation) {
                    _loadingReplyIds.value = _loadingReplyIds.value - comment.commentId
                }
            }
        }
    }

    fun postComment(text: String) {
        val params = _createCommentParams.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isPosting.value) return
        val gen = generation
        _isPosting.value = true
        scope.launch {
            try {
                val created = repository.createComment(params, trimmed)
                if (created != null && gen == generation) {
                    _comments.value = listOf(created) + _comments.value
                }
            } finally {
                if (gen == generation) _isPosting.value = false
            }
        }
    }

    fun postReply(target: CommentItem, threadParent: CommentItem, text: String) {
        val params = target.replyParams ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isPosting.value) return
        val gen = generation
        _isPosting.value = true
        scope.launch {
            try {
                val created = repository.createCommentReply(params, trimmed)
                if (created != null && gen == generation) {
                    val existing = _replies.value[threadParent.commentId]
                    if (existing != null || threadParent.repliesToken == null) {
                        _replies.value = _replies.value +
                            (threadParent.commentId to (existing ?: emptyList()) + created)
                    } else {
                        loadReplies(threadParent)
                    }
                }
            } finally {
                if (gen == generation) _isPosting.value = false
            }
        }
    }

    fun toggleLike(comment: CommentItem) {
        val action = (if (comment.isLiked) comment.unlikeParams else comment.likeParams) ?: return
        replace(comment.copy(isLiked = !comment.isLiked))
        val gen = generation
        scope.launch {
            if (!repository.performCommentAction(action) && gen == generation) replace(comment)
        }
    }

    fun delete(comment: CommentItem) {
        val action = comment.deleteParams ?: return
        val previousComments = _comments.value
        val previousReplies = _replies.value
        _comments.value = _comments.value.filterNot { it.commentId == comment.commentId }
        _replies.value = _replies.value
            .mapValues { (_, list) -> list.filterNot { it.commentId == comment.commentId } }
            .filterKeys { it != comment.commentId }
        val gen = generation
        scope.launch {
            if (!repository.performCommentAction(action) && gen == generation) {
                _comments.value = previousComments
                _replies.value = previousReplies
            }
        }
    }

    private fun replace(updated: CommentItem) {
        _comments.value = _comments.value.map {
            if (it.commentId == updated.commentId) updated else it
        }
        _replies.value = _replies.value.mapValues { (_, list) ->
            list.map { if (it.commentId == updated.commentId) updated else it }
        }
    }
}
