package com.ivor.ivormusic.data

import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Segment lookups against the SponsorBlock API.
 *
 * **The video id is never sent.** The request carries only the first four hex
 * characters of its SHA-256, and the server answers with every video sharing
 * that prefix - a few dozen - which the client filters locally. That is the
 * whole reason this endpoint exists, and using the plain `?videoID=` form
 * instead would hand a third party a complete record of what every Koda user
 * watches. The extra payload is the price of not doing that, and it is small.
 *
 * This is the only third-party service the app talks to, which is why the
 * feature is off until someone turns it on and the settings row names the
 * host being contacted.
 */
class SponsorBlockRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Segments for [videoId], or an empty list for anything that goes wrong.
     *
     * Failure is deliberately indistinguishable from "no segments" to the
     * caller: there is nothing a viewer can do about a SponsorBlock outage,
     * and the correct behaviour in every failing case is the same - play the
     * video exactly as if the feature were off.
     */
    suspend fun getSegments(
        videoId: String,
        categories: List<SponsorCategory>
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank() || categories.isEmpty()) return@withContext emptyList()

        cached(videoId, categories)?.let { return@withContext it }

        try {
            val prefix = hashPrefix(videoId)
            val categoryList = categories.joinToString(",") { "\"${it.apiName}\"" }
            val url = "$API_BASE/api/skipSegments/$prefix?categories=[$categoryList]"

            val body = get(url) ?: return@withContext emptyList()
            val segments = parseSegments(body, videoId)
            store(videoId, categories, segments)
            segments
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            KLog.w(TAG, "Segment lookup failed for $videoId: ${e.message}")
            emptyList()
        }
    }

    private suspend fun get(url: String): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    // 404 is the ordinary answer for a prefix nobody has
                    // submitted against, not an error worth logging.
                    val payload = if (it.isSuccessful) it.body?.string() else null
                    if (continuation.isActive) continuation.resume(payload)
                }
            }
        })
    }

    companion object {
        private const val TAG = "SponsorBlock"
        const val API_BASE = "https://sponsor.ajay.app"
        const val API_HOST = "sponsor.ajay.app"
        private const val USER_AGENT = "Koda Android (https://github.com/Ivorisnoob/Koda)"

        /**
         * Four hex characters, which is what the API documents and what keeps
         * the anonymity set usefully large. Shortening it would return more
         * videos for no privacy gain; lengthening it narrows the set toward
         * identifying the video, which is the thing being avoided.
         */
        private const val HASH_PREFIX_LENGTH = 4

        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val CACHE_MAX_ENTRIES = 32

        /**
         * Keyed by video id *and* the categories asked for, so changing a
         * category from Ignore to Skip is not answered out of a cache that was
         * never told to fetch it.
         *
         * Companion-level and synchronised for the same reason the OkHttp disk
         * cache is: with no DI, several ViewModels build their own repository,
         * and a per-instance cache would miss on every one of them.
         */
        private val cache = LinkedHashMap<String, CacheEntry>(0, 0.75f, true)

        private data class CacheEntry(val segments: List<SponsorSegment>, val storedAt: Long)

        private fun cacheKey(videoId: String, categories: List<SponsorCategory>) =
            videoId + "|" + categories.sortedBy { it.apiName }.joinToString(",") { it.apiName }

        private fun cached(
            videoId: String,
            categories: List<SponsorCategory>
        ): List<SponsorSegment>? = synchronized(cache) {
            val entry = cache[cacheKey(videoId, categories)] ?: return null
            if (System.currentTimeMillis() - entry.storedAt > CACHE_TTL_MS) {
                cache.remove(cacheKey(videoId, categories))
                return null
            }
            entry.segments
        }

        private fun store(
            videoId: String,
            categories: List<SponsorCategory>,
            segments: List<SponsorSegment>
        ) = synchronized(cache) {
            cache[cacheKey(videoId, categories)] =
                CacheEntry(segments, System.currentTimeMillis())
            while (cache.size > CACHE_MAX_ENTRIES) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
        }

        /**
         * Deliberately *not* cleared on a profile switch, unlike visitorData
         * and the account-derived caches: a video's segments are public and
         * identical for everyone, so there is nothing here belonging to an
         * identity. Exposed for tests and for a settings change that should
         * invalidate what was fetched under the old categories.
         */
        fun clearCache() = synchronized(cache) { cache.clear() }

        fun hashPrefix(videoId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(videoId.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
                .take(HASH_PREFIX_LENGTH)
        }

        /**
         * Parses the prefix response down to one video's usable segments.
         *
         * Pure and internal so the shape can be tested without a network. Three
         * filters matter and each drops something that would misbehave:
         * - **the video id**, because the response covers the whole hash prefix
         * - **`actionType`**, keeping only `skip`. `mute` needs volume control
         *   this does not implement, `poi` is a single point rather than a
         *   range, and `full` means the entire video is that category - acting
         *   on it would skip to the end of the video.
         * - **zero-length and inverted ranges**, which would either do nothing
         *   or seek backwards forever.
         */
        internal fun parseSegments(body: String, videoId: String): List<SponsorSegment> {
            val videos = try {
                JSONArray(body)
            } catch (e: Exception) {
                KLog.w(TAG, "Unparseable segment payload: ${e.message}")
                return emptyList()
            }

            val out = mutableListOf<SponsorSegment>()
            for (i in 0 until videos.length()) {
                val video = videos.optJSONObject(i) ?: continue
                if (video.optString("videoID") != videoId) continue

                val segments = video.optJSONArray("segments") ?: continue
                for (j in 0 until segments.length()) {
                    val node = segments.optJSONObject(j) ?: continue
                    if (node.optString("actionType", "skip") != "skip") continue

                    val category = SponsorCategory.fromApiName(node.optString("category"))
                        ?: continue
                    val range = node.optJSONArray("segment") ?: continue
                    if (range.length() < 2) continue

                    val startMs = (range.optDouble(0, -1.0) * 1000).toLong()
                    val endMs = (range.optDouble(1, -1.0) * 1000).toLong()
                    if (startMs < 0 || endMs <= startMs) continue

                    val uuid = node.optString("UUID").takeIf { it.isNotBlank() }
                        ?: "$category-$startMs"
                    out.add(SponsorSegment(uuid, category, startMs, endMs))
                }
            }
            return out.sortedBy { it.startMs }
        }
    }
}
