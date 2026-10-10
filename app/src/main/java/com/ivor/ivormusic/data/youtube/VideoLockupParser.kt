package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.DismissalTokens
import com.ivor.ivormusic.data.VideoItem
import com.ivor.ivormusic.data.parseLockupVideoStats
import com.ivor.ivormusic.data.parseVideoWatchProgress

// The lockupViewModel: the card YouTube now sends for a video in Home,
// Subscriptions, history, channel tabs, playlists and related lists, each
// with a different set of rows. Pure.

internal fun parseLockupViewModel(lockupViewModel: org.json.JSONObject?): VideoItem? {
    if (lockupViewModel == null) return null
    try {
        val contentId = lockupViewModel.optString("contentId")
        // STRICT VALIDATION: Ensure it's a valid Video ID (11 chars) to avoid playlists/channels
        if (contentId.length != 11) return null

         val metadata = lockupViewModel.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
         val titleObj = metadata?.optJSONObject("title")
         val title = titleObj?.optString("content") ?: "Unknown Title"

         // Get channel name and ID from metadata
         val metadataDetails = metadata?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")
         val metadataRows = metadataDetails?.optJSONArray("metadataRows")
         var channelName = "Unknown Channel"
         var channelId: String? = null
         var viewCount = ""
         var uploadDate = ""

         fun absorbVideoStats(parts: org.json.JSONArray?) {
             if (parts == null) return
             val stats = parseLockupVideoStats(
                 (0 until parts.length()).map { index ->
                     val part = parts.optJSONObject(index)
                     val text = part?.optJSONObject("text")
                     text?.optString("content").orEmpty() to
                         part?.optString("accessibilityLabel")?.takeIf { it.isNotBlank() }
                 }
             )
             if (viewCount.isBlank()) viewCount = stats.viewCount
             if (uploadDate.isBlank()) uploadDate = stats.uploadedDate
         }

         if (metadataRows != null && metadataRows.length() > 0) {
             val firstRowParts = metadataRows.optJSONObject(0)?.optJSONArray("metadataParts")
             if (firstRowParts != null && firstRowParts.length() > 0) {
                 val textObj = firstRowParts.optJSONObject(0)?.optJSONObject("text")
                 val firstText = textObj?.optString("content").orEmpty()
                 val firstRowStats = parseLockupVideoStats(
                     (0 until firstRowParts.length()).map { index ->
                         val part = firstRowParts.optJSONObject(index)
                         val text = part?.optJSONObject("text")
                         text?.optString("content").orEmpty() to
                             part?.optString("accessibilityLabel")?.takeIf { it.isNotBlank() }
                     }
                 )
                 // FEhistory sends one row, "Creator • 432K views", and says
                 // so with a byline marker. Without it the positional
                 // fallback in parseLockupVideoStats read the creator as an
                 // upload date, which made the row look like statistics and
                 // left every history entry on "Unknown Channel".
                 // [verified October 2026, signed in, FEhistory first page;
                 // the continuation had the same shape in September 2026]
                 val firstRowIsByline = metadataRows.optJSONObject(0)
                     ?.optJSONObject("lockupContentMetadataRowExtension")
                     ?.optString("contentType") == "METADATA_ROW_CONTENT_TYPE_BYLINE"
                 val firstRowIsStats = !firstRowIsByline && (
                     firstRowStats.uploadedDate.isNotBlank() ||
                         firstText.contains("view", ignoreCase = true) ||
                         firstText.contains("watching", ignoreCase = true)
                     )

                 if (firstRowIsByline) {
                     // The parts after the creator are that row's statistics.
                     val trailingParts = org.json.JSONArray()
                     for (index in 1 until firstRowParts.length()) {
                         trailingParts.put(firstRowParts.opt(index))
                     }
                     absorbVideoStats(trailingParts)
                 }

                 if (firstRowIsStats) {
                     // Channel tabs omit the creator row because the page
                     // itself already names the creator. Their first row is
                     // instead "N views • date"; treating it as a channel
                     // name used to discard the upload date entirely.
                     absorbVideoStats(firstRowParts)
                 } else {
                     channelName = firstText.ifBlank { channelName }

                     // Modern lockups use attributed text: the creator link
                     // is an innertubeCommand in commandRuns. Older responses
                     // use the legacy runs/navigationEndpoint shape below.
                     channelId = textObj?.optJSONArray("commandRuns")
                         ?.optJSONObject(0)
                         ?.optJSONObject("onTap")
                         ?.optJSONObject("innertubeCommand")
                         ?.optJSONObject("browseEndpoint")
                         ?.optString("browseId")
                         ?.takeIf { it.isNotBlank() }

                     if (channelId == null && textObj?.has("runs") == true) {
                         val runs = textObj.optJSONArray("runs")
                         if (runs != null && runs.length() > 0) {
                             val browseEndpoint = runs.optJSONObject(0)
                                 ?.optJSONObject("navigationEndpoint")
                                 ?.optJSONObject("browseEndpoint")
                             channelId = browseEndpoint?.optString("browseId")
                                 ?.takeIf { it.isNotBlank() }
                         }
                     }
                 }
             }

             // Normal feed lockups put creator in row zero and statistics
             // below it. Read every remaining row because some variants
             // split views and date while others keep them together.
             for (rowIndex in 1 until metadataRows.length()) {
                 absorbVideoStats(
                     metadataRows.optJSONObject(rowIndex)?.optJSONArray("metadataParts")
                 )
             }
         }

         // The creator block, which is where a lockup actually addresses its
         // channel. [verified September 2026, signed out, against a live
         // /next related list and a channel Videos tab]
         //
         // The metadata *text* rows above carry no creator command at all
         // on these responses - every card in a related list came back with
         // an empty onTap - so reading only those left `channelId` null for
         // ordinary videos too, and every channel tap paid a whole extra
         // /next through the `video:` fallback to recover an id the
         // response had already sent. The avatar is where it lives:
         //
         //   image.decoratedAvatarViewModel  one creator, browse id on its
         //                                   rendererContext.commandContext
         //   image.avatarStackViewModel      a collab, one avatar each and
         //                                   the collaborators dialog on
         //                                   the same command slot
         //
         // A channel tab's lockup has no image block at all - the page
         // already names the creator - so neither is present there and the
         // `video:` fallback remains the answer for those.
         val lockupImage = metadata?.optJSONObject("image")
         val decoratedAvatar = lockupImage?.optJSONObject("decoratedAvatarViewModel")
         val avatarStack = lockupImage?.optJSONObject("avatarStackViewModel")

         fun bestSourceUrl(sources: org.json.JSONArray?): String? {
             if (sources == null) return null
             var bestUrl: String? = null
             var maxWidth = -1
             for (i in 0 until sources.length()) {
                 val source = sources.optJSONObject(i)
                 val width = source?.optInt("width", 0) ?: 0
                 if (width >= maxWidth) {
                     maxWidth = width
                     bestUrl = source?.optString("url")
                 }
             }
             return bestUrl?.takeIf { it.isNotBlank() }
         }

         val collaborators = collaboratorsFromDialogHost(avatarStack)

         // The collab fallback is second because a collab has no single
         // owner: the dialog lists its channels in the order the byline
         // names them ("Sidemen and CORE"), so the first is the uploader and
         // the right destination for a tap that is not offered the choice.
         if (channelId == null) {
             val avatarChannelId = decoratedAvatar
                 ?.optJSONObject("rendererContext")
                 ?.optJSONObject("commandContext")
                 ?.optJSONObject("onTap")
                 ?.optJSONObject("innertubeCommand")
                 ?.optJSONObject("browseEndpoint")
                 ?.optString("browseId")
                 ?.takeIf { it.isNotBlank() }
             channelId = avatarChannelId ?: collaborators.firstOrNull()?.channelId
         }

         val channelIconUrl = bestSourceUrl(
             decoratedAvatar
                 ?.optJSONObject("avatar")
                 ?.optJSONObject("avatarViewModel")
                 ?.optJSONObject("image")
                 ?.optJSONArray("sources")
         ) ?: bestSourceUrl(
             // Only a stack to draw from: its first avatar is the uploader's,
             // matching the id chosen above.
             avatarStack
                 ?.optJSONArray("avatars")
                 ?.optJSONObject(0)
                 ?.optJSONObject("avatarViewModel")
                 ?.optJSONObject("image")
                 ?.optJSONArray("sources")
         )

         // Get thumbnail
         val contentImage = lockupViewModel.optJSONObject("contentImage")
         val thumbnailViewModel = contentImage?.optJSONObject("collectionThumbnailViewModel")
             ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
             ?: contentImage?.optJSONObject("thumbnailViewModel")

         var thumbnailUrl = thumbnailViewModel?.optJSONObject("image")?.optJSONArray("sources")?.let { sources ->
             // Get highest quality thumbnail
             var bestUrl: String? = null
             var maxWidth = 0
             for (i in 0 until sources.length()) {
                 val source = sources.optJSONObject(i)
                 val width = source?.optInt("width", 0) ?: 0
                 if (width >= maxWidth) {
                     maxWidth = width
                     bestUrl = source?.optString("url")
                 }
             }
             bestUrl
         }

         if (thumbnailUrl.isNullOrBlank()) {
             thumbnailUrl = "https://i.ytimg.com/vi/$contentId/hqdefault.jpg"
         }

         // Get Duration
         val overlays = thumbnailViewModel?.optJSONArray("overlays")
         var durationSeconds = 0L
         var durationText = ""
         var hasLiveBadge = false
         if (overlays != null) {
             overlayLoop@ for (i in 0 until overlays.length()) {
                 val overlayItem = overlays.optJSONObject(i) ?: continue
                 // Badge containers: legacy thumbnailOverlayBadgeViewModel.thumbnailBadges
                 // and the modern (2026) thumbnailBottomOverlayViewModel.badges
                 val badgeArrays = listOfNotNull(
                     overlayItem.optJSONObject("thumbnailOverlayBadgeViewModel")
                         ?.optJSONArray("thumbnailBadges"),
                     overlayItem.optJSONObject("thumbnailBottomOverlayViewModel")
                         ?.optJSONArray("badges")
                 )
                 for (badges in badgeArrays) {
                     for (j in 0 until badges.length()) {
                         val badge = badges.optJSONObject(j)
                             ?.optJSONObject("thumbnailBadgeViewModel") ?: continue
                         val badgeText = badge.optString("text")
                         if (badge.optString("badgeStyle").contains("LIVE") ||
                             badgeText.equals("LIVE", ignoreCase = true)
                         ) {
                             hasLiveBadge = true
                         }
                         if (badgeText.contains(":")) {
                             durationText = badgeText
                             durationSeconds = parseDurationToSeconds(badgeText)
                             break@overlayLoop
                         }
                     }
                 }
                 // Try thumbnailOverlayTimeStatusRenderer path
                 val timeStatus = overlayItem.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                     ?.optJSONObject("text")?.optString("simpleText")
                 if (timeStatus != null && timeStatus.contains(":")) {
                     durationText = timeStatus
                     durationSeconds = parseDurationToSeconds(timeStatus)
                     break
                 }
             }
         }

         // If still no duration, try to extract from accessibility text or metadata
         if (durationSeconds <= 0L) {
             // Try to find duration in title accessibility or elsewhere
             val accessibilityLabel = titleObj?.optJSONObject("accessibility")?.optString("label") ?: ""
             val durationMatch = Regex("(\\d+):(\\d+)(?::(\\d+))?").find(accessibilityLabel)
             if (durationMatch != null) {
                 durationText = durationMatch.value
                 durationSeconds = parseDurationToSeconds(durationText)
             }
         }

         // Assume it's not live if we couldn't find duration (most videos have a duration)
         val isLive = hasLiveBadge ||
                     durationText.contains("LIVE", ignoreCase = true) ||
                     viewCount.contains("watching", ignoreCase = true)

        return VideoItem(
            videoId = contentId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            channelIconUrl = channelIconUrl,
            thumbnailUrl = thumbnailUrl,
            duration = durationSeconds,
            viewCount = viewCount,
            uploadedDate = uploadDate,
            isLive = isLive,
            dismissal = parseDismissalTokens(metadata),
            collaborators = collaborators,
            watchedProgress = parseVideoWatchProgress(lockupViewModel),
            watchProgressUpdatedAtMs = System.currentTimeMillis()
        )
    } catch (e: Exception) {
        return null
    }
}

/**
 * YouTube's own "Not interested" / "Don't recommend channel" tokens for a
 * lockup, out of its overflow menu.
 *
 * Signed in, every feed lockup's menu carries two `feedbackEndpoint`s, each
 * with a `feedbackToken` and - pre-baked into the notification it would
 * show - the `undoToken` that reverses it. Signed out there are none at
 * all, so this returns null and the caller simply does the local half.
 *
 * The two items are told apart by `leadingImage`'s `clientResource
 * .imageName` (`NOT_INTERESTED` vs `REMOVE`), **not** by the visible label:
 * the label is localized ("Not interested" on the home feed, "Hide" on the
 * subscriptions feed, translated on a non-English account), and matching on
 * it would silently stop working for most of the world. Verified against
 * live /next, FEwhat_to_watch and FEsubscriptions responses, August 2026.
 */
private fun parseDismissalTokens(metadata: org.json.JSONObject?): DismissalTokens? {
    val listItems = metadata
        ?.optJSONObject("menuButton")
        ?.optJSONObject("buttonViewModel")
        ?.optJSONObject("onTap")
        ?.optJSONObject("innertubeCommand")
        ?.optJSONObject("showSheetCommand")
        ?.optJSONObject("panelLoadingStrategy")
        ?.optJSONObject("inlineContent")
        ?.optJSONObject("sheetViewModel")
        ?.optJSONObject("content")
        ?.optJSONObject("listViewModel")
        ?.optJSONArray("listItems") ?: return null

    var notInterested: String? = null
    var notInterestedUndo: String? = null
    var blockChannel: String? = null
    var blockChannelUndo: String? = null

    for (i in 0 until listItems.length()) {
        val item = listItems.optJSONObject(i)?.optJSONObject("listItemViewModel") ?: continue
        val endpoint = item
            .optJSONObject("rendererContext")
            ?.optJSONObject("commandContext")
            ?.optJSONObject("onTap")
            ?.optJSONObject("innertubeCommand")
            ?.optJSONObject("feedbackEndpoint") ?: continue

        val token = endpoint.optString("feedbackToken").takeIf { it.isNotBlank() } ?: continue
        val imageName = item
            .optJSONObject("leadingImage")
            ?.optJSONArray("sources")
            ?.optJSONObject(0)
            ?.optJSONObject("clientResource")
            ?.optString("imageName")

        when (imageName) {
            "NOT_INTERESTED" -> {
                notInterested = token
                notInterestedUndo = parseUndoToken(endpoint)
            }
            "REMOVE" -> {
                blockChannel = token
                blockChannelUndo = parseUndoToken(endpoint)
            }
        }
    }

    if (notInterested == null && blockChannel == null) return null
    return DismissalTokens(
        notInterested = notInterested,
        notInterestedUndo = notInterestedUndo,
        blockChannel = blockChannel,
        blockChannelUndo = blockChannelUndo
    )
}

/**
 * The undo token YouTube pre-bakes into a feedback endpoint's own
 * "Video removed - Undo" notification, so undo needs no extra request.
 */
private fun parseUndoToken(feedbackEndpoint: org.json.JSONObject): String? =
    feedbackEndpoint
        .optJSONArray("actions")
        ?.optJSONObject(0)
        ?.optJSONObject("replaceEnclosingAction")
        ?.optJSONObject("item")
        ?.optJSONObject("notificationMultiActionRenderer")
        ?.optJSONArray("buttons")
        ?.optJSONObject(0)
        ?.optJSONObject("buttonRenderer")
        ?.optJSONObject("serviceEndpoint")
        ?.optJSONObject("undoFeedbackEndpoint")
        ?.optString("undoToken")
        ?.takeIf { it.isNotBlank() }
