package com.ivor.ivormusic.data

/**
 * One channel credited on a collaboration video.
 *
 * YouTube's collaborations feature lets up to five channels share a single
 * upload, and a collab video's watch page does not describe an owner the way
 * every other video does. [verified September 2026 against a live signed-out
 * `/next` for a three-channel collab] Its `videoOwnerRenderer` carries **no**
 * `title`, `thumbnail`, `subscriberCountText` or `navigationEndpoint.browseEndpoint`
 * - it has an `attributedTitle` reading "KSI and 2 more", an `avatarStack` of
 * every collaborator's avatar, and a `navigationEndpoint` that is a
 * `showDialogCommand` rather than a browse. The subscribe button likewise
 * carries no `channelId` and opens the same dialog.
 *
 * That is why this exists rather than the usual single channel: with no id
 * anywhere on the response, "go to channel" had nothing to navigate to and the
 * Subscribe button had nothing to subscribe to, so a collab video was a watch
 * page whose creator could not be reached at all. Every collaborator's id,
 * name, handle, subscriber count and avatar come from the dialog that command
 * carries inline, so listing them costs no extra request.
 */
data class VideoCollaborator(
    /** Canonical UC id, from the row's own browseEndpoint. */
    val channelId: String,
    val name: String,
    /** "@KSI", when the row carried one. */
    val handle: String? = null,
    /** Already formatted by YouTube, e.g. "19M subscribers". */
    val subscriberCount: String? = null,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false
)
