package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.VideoCollaborator
import com.ivor.ivormusic.util.KLog

// The channels behind a collaboration. A collab upload names no single
// owner; every collaborator comes inside a dialog command, and the nesting
// between the host and that dialog differs per surface. Pure.

/**
 * Collaborators credited on a collab video, read out of the dialog the
 * owner renderer carries inline.
 *
 * [verified September 2026, signed out] A collab `videoOwnerRenderer` has no
 * `title`, `thumbnail` or `browseEndpoint` at all; the whole creator block is
 *
 * ```
 * videoOwnerRenderer
 *   attributedTitle.content              "KSI and 2 more"
 *   avatarStack.avatarStackViewModel     one avatar per collaborator
 *   navigationEndpoint.showDialogCommand.panelLoadingStrategy
 *     .inlineContent.dialogViewModel.customContent.listViewModel.listItems[]
 *       listItemViewModel
 *         title.content                  channel name
 *         title.commandRuns[0]...browseEndpoint.browseId
 *         title.attachmentRuns[]         CHECK_CIRCLE_FILLED when verified
 *         subtitle.content               "@handle • 19M subscribers"
 *         leadingAccessory.avatarViewModel.image.sources[0].url
 * ```
 *
 * The dialog is reached structurally rather than by matching its "Collaborators"
 * headline, which is localized. A row with no browse id is dropped: without an
 * id there is nothing to open, and a row that looks tappable and does nothing
 * is worse than one that is not listed.
 *
 * A feed lockup carries the same dialog under a different host - see
 * [collaboratorsFromDialogHost], which both entry points share.
 */
internal fun parseCollaborators(owner: org.json.JSONObject?): List<VideoCollaborator> =
    collaboratorsFromDialogHost(owner)

/**
 * The collaborator rows out of whatever `showDialogCommand` [host] carries.
 *
 * Two hosts carry the same dialog and neither can be assumed [verified
 * September 2026, signed out, against a live collab upload]:
 *
 * - the watch page's `videoOwnerRenderer`, on its `navigationEndpoint`;
 * - a feed lockup's `lockupMetadataViewModel.image.avatarStackViewModel`,
 *   on its `rendererContext.commandContext.onTap`, where the row command
 *   sits on each list item rather than on its title text.
 *
 * The nesting between the host and the dialog differs, so the dialog is
 * located by descending to it rather than by spelling out one path: what is
 * structural here is that a collab's channels are the list inside the only
 * dialog the creator block carries, not the exact depth YouTube nests it at.
 * [host] is always the creator block itself, never a whole response, which
 * is what keeps that search from reaching an unrelated dialog.
 */
internal fun collaboratorsFromDialogHost(host: org.json.JSONObject?): List<VideoCollaborator> {
    if (host == null) return emptyList()
    return try {
        val dialogs = mutableListOf<org.json.JSONObject>()
        findObjectsByKey(host, "dialogViewModel", dialogs)
        val items = dialogs.firstNotNullOfOrNull { dialog ->
            dialog.optJSONObject("customContent")
                ?.optJSONObject("listViewModel")
                ?.optJSONArray("listItems")
        } ?: return emptyList()

        (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index)?.optJSONObject("listItemViewModel")
                ?: return@mapNotNull null
            val titleObj = item.optJSONObject("title")
            val name = titleObj?.optString("content")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val channelId = titleObj.optJSONArray("commandRuns")
                ?.optJSONObject(0)
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.isNotBlank() }
                // The lockup variant of the same dialog puts the command on
                // the row rather than on the title text.
                ?: item.optJSONObject("rendererContext")
                    ?.optJSONObject("commandContext")
                    ?.optJSONObject("onTap")
                    ?.optJSONObject("innertubeCommand")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            // "@KSI • 19M subscribers", wrapped in bidi isolates that would
            // otherwise land in the middle of the visible strings.
            val subtitle = item.optJSONObject("subtitle")?.optString("content")
                ?.filterNot { it in BIDI_CONTROL_CHARS }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val subtitleParts = subtitle?.split("•")?.map { it.trim() }.orEmpty()
            val handle = subtitleParts.firstOrNull { it.startsWith("@") }
            val subscriberCount = subtitleParts.firstOrNull { !it.startsWith("@") }
                ?.takeIf { it.isNotBlank() }

            VideoCollaborator(
                channelId = channelId,
                name = name,
                handle = handle,
                subscriberCount = subscriberCount,
                avatarUrl = item.optJSONObject("leadingAccessory")
                    ?.optJSONObject("avatarViewModel")
                    ?.optJSONObject("image")
                    ?.optJSONArray("sources")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    ?.takeIf { it.isNotBlank() },
                isVerified = hasVerifiedAttachment(titleObj)
            )
        }
    } catch (e: Exception) {
        KLog.w("YouTubeRepo", "collaborator parse failed", e)
        emptyList()
    }
}

/** True when an attributed title carries YouTube's verified-badge glyph. */
private fun hasVerifiedAttachment(titleObj: org.json.JSONObject?): Boolean {
    val runs = titleObj?.optJSONArray("attachmentRuns") ?: return false
    for (i in 0 until runs.length()) {
        val sources = runs.optJSONObject(i)
            ?.optJSONObject("element")
            ?.optJSONObject("type")
            ?.optJSONObject("imageType")
            ?.optJSONObject("image")
            ?.optJSONArray("sources") ?: continue
        for (j in 0 until sources.length()) {
            val imageName = sources.optJSONObject(j)
                ?.optJSONObject("clientResource")
                ?.optString("imageName")
            if (imageName == "CHECK_CIRCLE_FILLED") return true
        }
    }
    return false
}

/**
 * Bidi marks and isolates YouTube wraps around interpolated values.
 *
 * They are correct on a web page - they stop an RTL channel handle from
 * reordering the text around it - and invisible garbage the moment the
 * string is split on a separator or measured for layout, so they come off
 * before the parts are used.
 */
private val BIDI_CONTROL_CHARS = setOf(
    '\u200E', // LEFT-TO-RIGHT MARK
    '\u200F', // RIGHT-TO-LEFT MARK
    '\u061C', // ARABIC LETTER MARK
    '\u2066', // LEFT-TO-RIGHT ISOLATE
    '\u2067', // RIGHT-TO-LEFT ISOLATE
    '\u2068', // FIRST STRONG ISOLATE
    '\u2069'  // POP DIRECTIONAL ISOLATE
)
