package com.ivor.ivormusic.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The one place that decides what a "don't recommend this" tap means.
 *
 * A dismissal has two halves, and they are not equal partners:
 *
 * - **The local hide always happens, first and synchronously.** It is what
 *   actually removes the item, it works signed out, and it takes effect on the
 *   next frame. See [NotInterestedRepository].
 * - **Telling the account is best-effort and optional.** Signed in, YouTube's
 *   feed responses carry dismissal tokens, and forwarding one also cleans up
 *   recommendations on youtube.com and in the official apps. Signed out no
 *   token exists at all, so this half is skipped rather than failed.
 *
 * The server half is deliberately fire-and-forget: it is launched on the
 * caller's scope and its result is dropped. Nothing in the UI waits on it and
 * nothing rolls back when it fails, because the hide the user asked for has
 * already happened - and YouTube's own feedback takes days to visibly change a
 * feed anyway, so there is no outcome worth reporting.
 *
 * Three ViewModels drive these taps (home/video feeds, the player's Up Next,
 * and Shorts) plus the undo snackbar, so the routing lives here rather than
 * being written out four times and drifting - same reason as
 * [SubscriptionActions].
 */
class NotInterestedActions(
    private val notInterestedRepository: NotInterestedRepository,
    private val youtubeRepository: YouTubeRepository
) {
    /**
     * Hide one video from every recommendation feed, and tell the account if
     * this item came from a signed-in response that offered a token.
     */
    fun hideVideo(video: VideoItem, scope: CoroutineScope) {
        notInterestedRepository.hideVideo(video)
        propagate(video.dismissal?.notInterested, scope)
    }

    /**
     * Stop recommending [video]'s channel.
     *
     * The token is the video's, not the channel's - YouTube keys the block off
     * the item the user dismissed it from, which is why this takes the whole
     * [VideoItem] rather than a channel id.
     */
    fun blockChannel(video: VideoItem, scope: CoroutineScope) {
        notInterestedRepository.blockChannel(
            channelId = video.channelId,
            name = video.channelName,
            avatarUrl = video.channelIconUrl,
            undoToken = video.dismissal?.blockChannelUndo
        )
        propagate(video.dismissal?.blockChannel, scope)
    }

    /**
     * The music-mode pair. Both are local and always will be: no music feed
     * response Koda parses carries a dismissal token, so there is nothing to
     * forward - the same position a signed-out video dismissal is already in.
     * They still route through here rather than straight to the store, because
     * this class is where "what does this tap mean" is answered and a second
     * answer elsewhere is how the two drift apart.
     */
    fun hideSong(song: Song) {
        notInterestedRepository.hideSong(song)
    }

    fun blockArtist(song: Song) {
        notInterestedRepository.blockArtist(song.artist)
    }

    /**
     * Take back [action], locally and - when the dismissal was forwarded and
     * YouTube pre-baked an undo token for it - on the account as well.
     */
    fun undo(action: NotInterestedRepository.UndoableAction, scope: CoroutineScope) {
        notInterestedRepository.undo(action)
        propagate(action.undoToken, scope)
    }

    private fun propagate(token: String?, scope: CoroutineScope) {
        if (token.isNullOrBlank()) return
        scope.launch { youtubeRepository.sendDismissalFeedback(token) }
    }
}
