package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.NotificationItem
import com.ivor.ivormusic.data.NotificationPage
import com.ivor.ivormusic.data.NotificationTarget
import org.json.JSONArray
import org.json.JSONObject

// The notification inbox as youtube.com reads it: WEB /browse of
// FEnotifications_inbox, then ordinary browse continuations. Pure.

/** The browse id of the website's notification inbox. */
internal const val NOTIFICATION_INBOX_BROWSE_ID = "FEnotifications_inbox"

/**
 * One page of the inbox: its items in YouTube's order and the token for the
 * next page, null at the end.
 *
 * [verified October 2026, signed in] Both kinds of page answer under
 * `onResponseReceivedEndpoints`, not the `onResponseReceivedActions` the feeds
 * use, so [com.ivor.ivormusic.data.continuationItemsOrNull] does not find them:
 *
 *   first page   openPopupAction.popup.multiPageMenuRenderer.sections[]
 *                .multiPageMenuNotificationSectionRenderer.items[]
 *                - an "Important" section, then "More notifications"
 *   later pages  appendContinuationItemsAction.continuationItems[]
 *
 * Each list ends in a `continuationItemRenderer` whose
 * `continuationCommand.token` is posted back as `{"continuation": ...}`. About
 * twenty items a page, newest first, no repeats across pages; one account's
 * chain went back past two weeks in six pages.
 */
internal fun parseNotificationInboxPage(root: JSONObject): NotificationPage {
    val entries = mutableListOf<JSONObject>()
    for (key in listOf("onResponseReceivedEndpoints", "onResponseReceivedActions")) {
        val endpoints = root.optJSONArray(key) ?: continue
        for (i in 0 until endpoints.length()) {
            val endpoint = endpoints.optJSONObject(i) ?: continue
            endpoint.optJSONObject("appendContinuationItemsAction")
                ?.optJSONArray("continuationItems")
                ?.let { entries += it.objects() }
            val sections = endpoint.optJSONObject("openPopupAction")
                ?.optJSONObject("popup")
                ?.optJSONObject("multiPageMenuRenderer")
                ?.optJSONArray("sections")
                ?: continue
            for (section in sections.objects()) {
                section.optJSONObject("multiPageMenuNotificationSectionRenderer")
                    ?.optJSONArray("items")
                    ?.let { entries += it.objects() }
            }
        }
    }
    val items = entries.mapNotNull { it.optJSONObject("notificationRenderer")?.let(::parseNotificationRenderer) }
        .distinctBy { it.id.ifBlank { "${it.message}|${it.sentTime}" } }
    val continuation = entries.firstNotNullOfOrNull { entry ->
        entry.optJSONObject("continuationItemRenderer")
            ?.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
    }
    return NotificationPage(items, continuation)
}

/**
 * One `notificationRenderer`. Its message arrives as `runs` on this endpoint
 * (channel, " uploaded: ", title) where the old inbox sent `simpleText`; both
 * are read, since reading only `simpleText` drops every item here.
 */
internal fun parseNotificationRenderer(renderer: JSONObject): NotificationItem? {
    val message = textOf(renderer.optJSONObject("shortMessage"))?.takeIf { it.isNotBlank() } ?: return null
    fun lastThumb(key: String): String? {
        val thumbs = renderer.optJSONObject(key)?.optJSONArray("thumbnails") ?: return null
        var url = thumbs.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))
            ?.optString("url")?.takeIf { it.isNotBlank() }
        if (url?.startsWith("//") == true) url = "https:$url"
        return url
    }
    val navigation = renderer.optJSONObject("navigationEndpoint")
    fun endpointVideoId(key: String): String? =
        navigation?.optJSONObject(key)?.optString("videoId")?.takeIf { it.isNotBlank() }
    val webUrl = navigation?.optJSONObject("commandMetadata")
        ?.optJSONObject("webCommandMetadata")?.optString("url")
        ?.takeIf { it.startsWith("/") }
        ?.let { "https://www.youtube.com$it" }
    val postParams = navigation?.optJSONObject("browseEndpoint")
        ?.takeIf { it.optString("browseId") == POST_DETAIL_BROWSE_ID }
        ?.optString("params")?.takeIf { it.isNotBlank() }
    val target = endpointVideoId("watchEndpoint")?.let { NotificationTarget.Video(it) }
        ?: endpointVideoId("reelWatchEndpoint")?.let { NotificationTarget.Short(it) }
        ?: postParams?.let { NotificationTarget.Post(it, webUrl) }
        ?: webUrl?.let { NotificationTarget.Link(it) }
    return NotificationItem(
        message = message,
        sentTime = textOf(renderer.optJSONObject("sentTimeText")).orEmpty(),
        channelAvatarUrl = lastThumb("thumbnail"),
        videoThumbnailUrl = lastThumb("videoThumbnail"),
        target = target,
        isRead = renderer.optBoolean("read", false),
        id = renderer.optString("notificationId")
    )
}

/** `simpleText`, or the `runs` joined. */
private fun textOf(text: JSONObject?): String? {
    text ?: return null
    text.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
    val runs = text.optJSONArray("runs") ?: return null
    return runs.objects().joinToString("") { it.optString("text") }.takeIf { it.isNotBlank() }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
