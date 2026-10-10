package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.NotificationItem
import com.ivor.ivormusic.data.NotificationTarget
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The signed-in account itself: who it is, and its notification inbox.
 * Both answer nothing signed out.
 */
internal class YouTubeAccount(
    private val musicApi: MusicApi,
    private val webApi: WebApi,
    private val sessionManager: SessionManager,
) {
    suspend fun fetchAccountInfo() = withContext(Dispatchers.IO) {
        // Captured before the call so the name, avatar and datasyncId it parses
        // land on the account that was asked, even if the user switches away
        // while the request is out.
        val session = sessionManager.captureSession() ?: return@withContext

        try {
            val jsonResponse = musicApi.fetchInternalApi("account/account_menu")

            if (jsonResponse.isEmpty()) {
                KLog.w(YOUTUBE_TAG, "fetchAccountInfo: empty response from account/account_menu")
                return@withContext
            }
            // Account payloads include identity details. Keep them out of the
            // release diagnostic ring buffer; success/failure is enough here.
            KLog.d(YOUTUBE_TAG, "fetchAccountInfo response received")
            
            var avatarUrl: String? = null
            var userName: String? = null
            
            try {
                val root = JSONObject(jsonResponse)
                
                // Navigate to the account section
                // Usually: actions -> openPopupAction -> popup -> multiPageMenuRenderer -> header -> activeAccountHeaderRenderer
                val actions = root.optJSONArray("actions")
                val popup = actions?.optJSONObject(0)
                    ?.optJSONObject("openPopupAction")
                    ?.optJSONObject("popup")
                    ?.optJSONObject("multiPageMenuRenderer")
                
                val header = popup?.optJSONObject("header")?.optJSONObject("activeAccountHeaderRenderer")
                
                if (header != null) {
                    // Extract Name
                    userName = getRunText(header.optJSONObject("accountName"))
                    
                    // Extract Avatar
                    val thumbnails = header.optJSONObject("avatar")?.optJSONArray("thumbnails")
                    if (thumbnails != null && thumbnails.length() > 0) {
                        avatarUrl = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    }
                }
                
                // Fallback: Check sections if header failed
                if (userName == null || avatarUrl == null) {
                    val sections = popup?.optJSONArray("sections")
                    if (sections != null) {
                        for (i in 0 until sections.length()) {
                            val item = sections.optJSONObject(i)
                                ?.optJSONObject("multiPageMenuSectionRenderer")
                                ?.optJSONArray("items")?.optJSONObject(0)
                                ?.optJSONObject("compactLinkRenderer")
                                
                            // Sometimes the first item is the account link
                            if (item != null) {
                                val thumb = item.optJSONObject("icon")?.optJSONArray("thumbnails")
                                if (avatarUrl == null && thumb != null) {
                                    avatarUrl = thumb.optJSONObject(thumb.length() - 1)?.optString("url")
                                }
                            }
                        }
                    }
                }
                
                // Fallback: Regex for avatar hosts if parsing failed
                // (yt3.ggpht.com and lh3.googleusercontent.com serve account avatars)
                if (avatarUrl == null) {
                    val avatarRegex = "\"url\"\\s*:\\s*\"(https://(?:yt3\\.ggpht\\.com|[a-z0-9]+\\.googleusercontent\\.com)/[^\"]+)\"".toRegex()
                    val match = avatarRegex.find(jsonResponse)
                    avatarUrl = match?.groupValues?.get(1)
                }

            } catch (jsonEx: Exception) {
                // Ignore
            }
            
            KLog.d(YOUTUBE_TAG, "fetchAccountInfo parsed name=$userName avatar=$avatarUrl")

            // Save avatar if found
            if (!avatarUrl.isNullOrEmpty()) {
                // Upgrade resolution
                val highResUrl = avatarUrl
                    .replace("=s88", "=s512")
                    .replace("=s48", "=s512")
                    .replace("=s96", "=s512")
                sessionManager.updateSessionIdentity(session, avatarUrl = highResUrl)
            }

            // Save user name if found
            if (!userName.isNullOrEmpty()) {
                sessionManager.updateSessionIdentity(session, name = userName)
            }

            // YouTube's own account identifier, which every authenticated
            // response carries for free (verified August 2026 against
            // account_menu, FEsubscriptions and FEwhat_to_watch: present on all
            // three, identical across them, alongside a `loggedOut` boolean).
            // Stored verbatim, trailing separators included - it is only ever
            // compared against another copy of itself.
            //
            // This is what lets the app recognise an account it already has:
            // signing back in repairs that profile instead of adding a
            // duplicate row, and a restored backup can tell that an account in
            // the file is the one already signed in here.
            runCatching {
                JSONObject(jsonResponse)
                    .optJSONObject("responseContext")
                    ?.optJSONObject("mainAppWebResponseContext")
                    ?.optString("datasyncId")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sessionManager.updateSessionIdentity(session, datasyncId = it) }
            }
            
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Account identity refresh failed", e)
        }
    }

    /**
     * The user's notification inbox (new uploads from subscribed channels,
     * replies, etc.). Requires login.
     *
     * The inbox is paged, and how far it goes depends on the account. [verified
     * September 2026] A page is about 20 items followed by a
     * `continuationItemRenderer` whose `getNotificationMenuEndpoint.ctoken` is
     * posted back to the same endpoint as `{"ctoken": ...}`; that account's
     * whole inbox was three pages. [verified October 2026] Another account's
     * was 11 items and no continuation at all. So up to
     * [NOTIFICATION_INBOX_MAX_PAGES] are followed, and a response without a
     * token is simply the end. [scar] Only the first page was ever read, so
     * the sheet stopped at 20 on an account with sixty. Whatever YouTube has
     * dropped altogether is what [NotificationHistoryStore] remembers.
     */
    suspend fun getNotifications(): List<NotificationItem> = withContext(Dispatchers.IO) {
        if (!sessionManager.isLoggedIn()) return@withContext emptyList()
        try {
            val raw = webApi.postWatchApi(
                "notification/get_notification_menu",
                JSONObject()
                    .put("context", webApi.webContext())
                    .put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX")
            ) ?: return@withContext emptyList()
            var root = JSONObject(raw)
            val renderers = mutableListOf<JSONObject>()
            var page = 1
            while (true) {
                findObjectsByKey(root, "notificationRenderer", renderers)
                if (page >= NOTIFICATION_INBOX_MAX_PAGES) break
                val endpoints = mutableListOf<JSONObject>()
                findObjectsByKey(root, "getNotificationMenuEndpoint", endpoints)
                val token = endpoints.firstNotNullOfOrNull { endpoint ->
                    endpoint.optString("ctoken").takeIf { it.isNotBlank() }
                } ?: break
                // A later page that fails keeps the pages already read.
                val next = webApi.postWatchApi(
                    "notification/get_notification_menu",
                    JSONObject().put("context", webApi.webContext()).put("ctoken", token)
                ) ?: break
                root = JSONObject(next)
                page++
            }
            renderers.distinctBy { it.optString("notificationId").ifBlank { it.toString() } }.mapNotNull { renderer ->
                val message = renderer.optJSONObject("shortMessage")?.optString("simpleText")
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
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
                NotificationItem(
                    message = message,
                    sentTime = renderer.optJSONObject("sentTimeText")?.optString("simpleText").orEmpty(),
                    channelAvatarUrl = lastThumb("thumbnail"),
                    videoThumbnailUrl = lastThumb("videoThumbnail"),
                    target = target,
                    isRead = renderer.optBoolean("read", false),
                    id = renderer.optString("notificationId")
                )
            }
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "getNotifications failed", e)
            emptyList()
        }
    }

    private companion object {
        /** Pages of the notification inbox read on one open: one request each. */
        private const val NOTIFICATION_INBOX_MAX_PAGES = 3
    }
}
