package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.NotificationPage
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
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
     * One page of the user's notification inbox. Requires login. Null when the
     * request failed, so a caller can tell that from an empty inbox and keep
     * [continuation] to retry.
     *
     * Reads the inbox the website reads, `FEnotifications_inbox` over WEB
     * `/browse`; see [parseNotificationInboxPage] for its shape. [scar] This
     * used `notification/get_notification_menu`, which YouTube stopped
     * updating: in October 2026 it answered the same account with thirteen
     * items three days to a month old while the website's inbox had items from
     * hours before, and people saw a bell weeks behind. Pages are read one at
     * a time as the sheet scrolls, the way the website does, because a fixed
     * number of pages either stops short or spends requests nobody looks at.
     * Whatever YouTube has dropped altogether is what [NotificationHistoryStore]
     * remembers.
     *
     * [session] is the login the first page went out for; a later page sent
     * for another one would put that account's inbox in this one's sheet.
     */
    suspend fun getNotificationsPage(
        continuation: String? = null,
        session: YouTubeSession? = sessionManager.captureSession(),
    ): NotificationPage? = withContext(Dispatchers.IO) {
        session ?: return@withContext null
        try {
            val body = JSONObject().put("context", webApi.webContext())
            if (continuation == null) body.put("browseId", NOTIFICATION_INBOX_BROWSE_ID)
            else body.put("continuation", continuation)
            val raw = webApi.postWatchApi("browse", body, session)
                ?.takeIf { it.isNotBlank() } ?: return@withContext null
            val root = JSONObject(raw)
            if (root.has("error")) return@withContext null
            parseNotificationInboxPage(root).also { page ->
                KLog.d(
                    YOUTUBE_TAG,
                    "Notifications ${if (continuation == null) "first" else "next"} page: " +
                        "${page.items.size} items, more=${page.continuation != null}"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.e(YOUTUBE_TAG, "Notifications page failed", e)
            null
        }
    }
}
