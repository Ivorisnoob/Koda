package com.ivor.ivormusic.video

import com.ivor.ivormusic.data.CommentItem
import com.ivor.ivormusic.data.YouTubeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One comment thread's state and actions: load, page, expand replies, post,
 * reply, like and delete, each write shown at once and reverted if YouTube
 * refuses it.
 *
 * The one copy. [scar] The video player and the Shorts player each carried a
 * line-for-line copy of this beside the version community posts used, so a
 * fix reached one host of four. Every thread - a video's, a Short's, a channel
 * post's, a feed post's - now lives here, and its sheet reads the flows below.
 *
 * [viaBrowse] picks the endpoint the thread's continuations answer on (see
 * [com.ivor.ivormusic.data.youtube.CommentThreads.getCommentsPage]): posts
 * answer on `/browse`, videos on `/next`. [signedIn] is the host's own
 * sign-in state; the composer follows it, so signing in with the sheet open
 * shows the composer without reopening the thread.
 *
 * Every write lands only if the thread it was started for is still the open
 * one: [generation] moves on each [load] and [clear], so a slow response for a
 * thread the user has left cannot write into the next.
 */
class CommentThreadController(
    private val repository: YouTubeRepository,
    private val scope: CoroutineScope,
    private val viaBrowse: Boolean,
    signedIn: StateFlow<Boolean>,
) {
    private val _comments = MutableStateFlow<List<CommentItem>>(emptyList())
    val comments: StateFlow<List<CommentItem>> = _comments.asStateFlow()

    /** Replies by parent comment id; a key present means they arrived. */
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

    /** What posting a new comment needs; arrives with the first page. */
    private val _createCommentParams = MutableStateFlow<String?>(null)

    /** Signed in, and the thread accepts new comments. */
    val canComment: StateFlow<Boolean> =
        combine(_createCommentParams, signedIn) { params, isSignedIn -> params != null && isSignedIn }
            .stateIn(scope, SharingStarted.Eagerly, false)

    private var nextToken: String? = null
    private var generation = 0L
    private var loadJob: Job? = null

    /** What the open thread belongs to (a video id), when the host names one. */
    private var subject: String? = null

    /** [subject] once its first page has arrived. */
    private var loadedSubject: String? = null

    /**
     * Open [subject]'s thread unless it is already open or on its way. The
     * players call this whenever comments come into view; a load that failed
     * is tried again on the next call, one that worked is not repeated.
     */
    fun ensureLoaded(subject: String, firstToken: suspend () -> String?) {
        if (subject == this.subject && (_isLoading.value || loadedSubject == subject)) return
        load(subject, firstToken)
    }

    /**
     * Open a thread. [firstToken] resolves the first page's token (a post's
     * detail browse, or a token the host already holds); returning null reads
     * as "no comments here".
     */
    fun load(subject: String? = null, firstToken: suspend () -> String?) {
        val gen = reset()
        this.subject = subject
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
                    loadedSubject = subject
                }
            } finally {
                if (gen == generation) _isLoading.value = false
            }
        }
    }

    /** Leave the thread; anything still in flight for it is dropped. */
    fun clear() {
        reset()
    }

    /** Drop the open thread and everything in flight for it; the new generation. */
    private fun reset(): Long {
        val gen = ++generation
        loadJob?.cancel()
        _comments.value = emptyList()
        _replies.value = emptyMap()
        _loadingReplyIds.value = emptySet()
        _isLoading.value = false
        _isLoadingMore.value = false
        _isPosting.value = false
        _isAvailable.value = true
        _createCommentParams.value = null
        nextToken = null
        subject = null
        loadedSubject = null
        return gen
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
                    // YouTube can repeat an item across pages.
                    val known = _comments.value.mapTo(HashSet()) { it.commentId }
                    _comments.value = _comments.value + page.comments.filter { it.commentId !in known }
                    nextToken = page.nextPageToken
                }
            } finally {
                if (gen == generation) _isLoadingMore.value = false
            }
        }
    }

    /** A comment's replies, first page; collapsing is the sheet's business. */
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

    /** Post a top-level comment; it is put first once YouTube accepts it. */
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

    /**
     * Reply to [target], a top-level comment or one of its replies: replying
     * to a reply posts into the same thread, as on YouTube. [threadParent] is
     * the top-level comment whose replies show the result.
     */
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
                        // Replies already showing, or none yet: append in place.
                        _replies.value = _replies.value +
                            (threadParent.commentId to (existing ?: emptyList()) + created)
                    } else {
                        // Replies exist but are collapsed: fetch them, so the
                        // older ones are not hidden behind the new one.
                        loadReplies(threadParent)
                    }
                }
            } finally {
                if (gen == generation) _isPosting.value = false
            }
        }
    }

    /** Like or unlike, shown at once and reverted if YouTube refuses. */
    fun toggleLike(comment: CommentItem) {
        val action = (if (comment.isLiked) comment.unlikeParams else comment.likeParams) ?: return
        replace(comment.copy(isLiked = !comment.isLiked))
        val gen = generation
        scope.launch {
            if (!repository.performCommentAction(action) && gen == generation) replace(comment)
        }
    }

    /** Delete one of the user's own comments, restored if YouTube refuses. */
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

    /** Swap a comment, matched by id, in the list and in every reply thread. */
    private fun replace(updated: CommentItem) {
        _comments.value = _comments.value.map {
            if (it.commentId == updated.commentId) updated else it
        }
        _replies.value = _replies.value.mapValues { (_, list) ->
            list.map { if (it.commentId == updated.commentId) updated else it }
        }
    }
}
