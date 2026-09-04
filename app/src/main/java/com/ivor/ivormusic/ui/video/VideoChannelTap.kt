package com.ivor.ivormusic.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ivor.ivormusic.data.VideoItem

/**
 * What tapping the creator on a video card means.
 *
 * One video, one channel is the ordinary case and stays a plain navigation. A
 * collab upload credits up to five channels and names none of them as the
 * owner, so there is no single destination: the card shows a stack of avatars
 * and a byline YouTube already localized ("Sidemen and CORE"), and the honest
 * answer to a tap is the same one the watch page gives - the list, and let the
 * viewer pick.
 *
 * **It hosts its own sheet rather than taking a callback**, which is what makes
 * it safe to drop into any card. Routing this through a parameter instead would
 * mean every surface that draws a video card - Home, Subscriptions, search,
 * history, the channel tabs, the playlist pages - passing one more lambda, and
 * a surface that forgot would silently be the one place collab cards still went
 * nowhere. That is the failure `VideoOptionsSheetHost` exists to prevent, and
 * this is the same shape at a smaller scale: the behaviour travels with the
 * card, and a new surface gets it by drawing a card.
 *
 * Hosting a modal sheet from inside a lazy list item is safe *because* it is
 * modal: the scrim takes the gestures, so the row that owns the sheet cannot be
 * scrolled out of the viewport and disposed underneath it.
 *
 * Returns null when there is nothing to open, so a caller can leave the whole
 * affordance out rather than showing one that does nothing.
 */
@Composable
fun videoChannelTap(
    video: VideoItem,
    onOpenChannel: ((String) -> Unit)?
): (() -> Unit)? {
    // Kept above the early return so the slot this composable occupies does not
    // change shape when a surface passes a null handler.
    // Keyed by video: a recycled row must not inherit the previous card's
    // open sheet.
    var showCollaborators by remember(video.videoId) { mutableStateOf(false) }

    if (onOpenChannel == null) return null

    if (showCollaborators) {
        CollaboratorsSheet(
            collaborators = video.collaborators,
            onOpenChannel = onOpenChannel,
            onDismiss = { showCollaborators = false }
        )
    }

    return {
        // One credited channel is not a choice, and neither is none: both go
        // straight to the reference the card already resolves.
        if (video.collaborators.size > 1) {
            showCollaborators = true
        } else {
            onOpenChannel(video.channelNavigationReference)
        }
    }
}
