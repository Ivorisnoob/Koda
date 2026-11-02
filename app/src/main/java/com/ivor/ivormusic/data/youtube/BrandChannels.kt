package com.ivor.ivormusic.data.youtube

import com.ivor.ivormusic.data.BrandChannel
import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.util.KLog
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

/**
 * The channels the signed-in Google account can act as, from the
 * `account/accounts_list` response (the list YouTube's own "Switch account"
 * screen shows).
 *
 * Each identity is an `accountItem`. Its page id rides in
 * `serviceEndpoint.selectActiveIdentityEndpoint.supportedTokens[].pageIdToken
 * .pageId`, absent for the personal channel, and its datasyncId in
 * `...supportedTokens[].datasyncIdToken.datasyncIdToken`. The items are found
 * by walking the tree rather than by a fixed path, so a wrapper renderer
 * YouTube adds or renames around the list does not empty it.
 *
 * Not yet probed (November 2026): the shape above is unverified, and the
 * fixtures in BrandChannelsTest are hand-built from it, not reduced from a
 * live response. Probe a signed-in account with a brand channel before
 * trusting it, and pin a real fixture in ParserFixturesTest.
 */
internal fun parseBrandChannels(root: JSONObject): List<BrandChannel> {
    val items = ArrayList<JSONObject>()
    collectAccountItems(root, items, depth = 0)
    return items.mapNotNull { item ->
        val name = textOf(item.optJSONObject("accountName")) ?: return@mapNotNull null
        val tokens = item.optJSONObject("serviceEndpoint")
            ?.optJSONObject("selectActiveIdentityEndpoint")
            ?.optJSONArray("supportedTokens")
        var pageId: String? = null
        var datasyncId: String? = null
        if (tokens != null) {
            for (i in 0 until tokens.length()) {
                val token = tokens.optJSONObject(i) ?: continue
                token.optJSONObject("pageIdToken")?.optString("pageId")
                    ?.takeIf { it.isNotBlank() }?.let { pageId = it }
                token.optJSONObject("datasyncIdToken")?.optString("datasyncIdToken")
                    ?.takeIf { it.isNotBlank() }?.let { datasyncId = it }
            }
        }
        val thumbs = item.optJSONObject("accountPhoto")?.optJSONArray("thumbnails")
        val avatar = thumbs?.let { it.optJSONObject(it.length() - 1) }
            ?.optString("url")?.takeIf { it.isNotBlank() }
        BrandChannel(
            name = name,
            handle = textOf(item.optJSONObject("channelHandle")),
            avatarUrl = avatar,
            pageId = pageId,
            datasyncId = datasyncId,
            isSelected = item.optBoolean("isSelected", false),
        )
    }.distinctBy { it.pageId to it.datasyncId }
}

private fun collectAccountItems(node: Any?, out: MutableList<JSONObject>, depth: Int) {
    if (depth > 24) return
    when (node) {
        is JSONObject -> {
            node.optJSONObject("accountItem")?.let { out += it }
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "accountItem") continue
                collectAccountItems(node.opt(key), out, depth + 1)
            }
        }
        is JSONArray -> for (i in 0 until node.length()) collectAccountItems(node.opt(i), out, depth + 1)
    }
}

private fun textOf(obj: JSONObject?): String? {
    obj ?: return null
    obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
    val runs = obj.optJSONArray("runs") ?: return null
    return buildString {
        for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
    }.takeIf { it.isNotBlank() }
}

/**
 * Puts a brand channel's page id into the body of every InnerTube call made
 * for a profile that acts as one.
 *
 * The `X-Goog-PageId` header [authenticate] adds covers the website's API; the
 * music API reads the identity from `context.user.onBehalfOfUser` in the body
 * instead. Doing it here rather than at each call site means no request can
 * quietly go out as the personal channel because its context was built by
 * hand somewhere this change did not reach. Only POSTs to `/youtubei/` that
 * carry a [YouTubeSession] with a page id and a signed Authorization header
 * are touched; everything else passes through untouched.
 */
internal object BrandChannelInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pageId = request.tag(YouTubeSession::class.java)?.pageId
        val body = request.body
        // Only calls signed by [authenticate]. NewPipe's own requests carry the
        // session's cookies unsigned for playback, and an identity they cannot
        // prove is the last thing to add to a stream request.
        if (pageId == null || body == null || request.method != "POST" ||
            request.header("Authorization") == null ||
            !request.url.encodedPath.startsWith("/youtubei/")
        ) return chain.proceed(request)

        val rewritten = runCatching {
            val buffer = Buffer().also { body.writeTo(it) }
            val json = JSONObject(buffer.readUtf8())
            val context = json.optJSONObject("context") ?: JSONObject().also { json.put("context", it) }
            val user = context.optJSONObject("user") ?: JSONObject().also { context.put("user", it) }
            user.put("onBehalfOfUser", pageId)
            json.toString().toRequestBody(body.contentType())
        }.onFailure {
            // Not JSON (or unreadable): send it as it was rather than fail the call.
            KLog.w(YOUTUBE_TAG, "Brand channel: left a non-JSON body unchanged")
        }.getOrNull() ?: return chain.proceed(request)

        return chain.proceed(request.newBuilder().method(request.method, rewritten).build())
    }
}
