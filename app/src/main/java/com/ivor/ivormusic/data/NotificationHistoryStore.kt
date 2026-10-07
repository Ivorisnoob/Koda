package com.ivor.ivormusic.data

import android.content.Context
import com.ivor.ivormusic.util.KLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * The notifications this device has seen, kept after YouTube stops listing them.
 *
 * YouTube's inbox only reaches so far back - a few pages on a busy account, a
 * dozen items on a quiet one (see `YouTubeRepository.getNotifications`) - and
 * what falls off the end is simply gone from it. Koda remembers each item the
 * first time it sees it and goes on showing it, under its own heading, once
 * YouTube has dropped it.
 *
 * Keyed by profile id directly: an inbox is one account's, and two profiles on
 * a device must not see each other's. A cache rather than user data - it is
 * rebuilt by opening the inbox, and nothing here was chosen by anybody except
 * what to hide - so like [WaveformStore] it has its own preference file and is
 * deliberately absent from [BackupRepository]. It is read and written only by
 * the inbox's ViewModel, so it is not process-wide state either.
 */
internal class NotificationHistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Everything remembered for [profileId], newest first, all marked remembered. */
    fun remembered(profileId: String): List<NotificationItem> =
        read(profileId).map { it.copy(remembered = true) }

    /**
     * Fold a fresh inbox into what is remembered and return the two together:
     * the fresh items in YouTube's order, then the ones only this device still has.
     */
    fun merge(
        profileId: String,
        fresh: List<NotificationItem>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<NotificationItem> {
        val merged = mergeNotificationHistory(read(profileId), fresh, nowMs)
        write(profileId, merged)
        // A hidden id is only worth keeping while its item can still be shown.
        val kept = merged.mapTo(HashSet()) { it.id }
        val hidden = hiddenIds(profileId)
        val pruned = hidden.filterTo(HashSet()) { it in kept }
        if (pruned.size != hidden.size) {
            prefs.edit().putStringSet(hiddenKey(profileId), pruned).apply()
        }
        return merged
    }

    fun hiddenIds(profileId: String): Set<String> =
        prefs.getStringSet(hiddenKey(profileId), emptySet()).orEmpty().toSet()

    fun hide(profileId: String, id: String) {
        if (id.isBlank()) return
        prefs.edit().putStringSet(hiddenKey(profileId), hiddenIds(profileId) + id).apply()
    }

    fun restoreHidden(profileId: String) {
        prefs.edit().remove(hiddenKey(profileId)).apply()
    }

    private fun read(profileId: String): List<NotificationItem> {
        val raw = prefs.getString(itemsKey(profileId), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::fromJson) }
        } catch (e: org.json.JSONException) {
            KLog.w(TAG, "Dropping unreadable notification history", e)
            emptyList()
        }
    }

    private fun write(profileId: String, items: List<NotificationItem>) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        prefs.edit().putString(itemsKey(profileId), array.toString()).apply()
    }

    private fun toJson(item: NotificationItem) = JSONObject().apply {
        put("id", item.id)
        put("message", item.message)
        put("sent", item.sentTime)
        put("avatar", item.channelAvatarUrl ?: JSONObject.NULL)
        put("thumb", item.videoThumbnailUrl ?: JSONObject.NULL)
        put("read", item.isRead)
        put("seen", item.firstSeenMs)
        when (val target = item.target) {
            is NotificationTarget.Video -> put("kind", KIND_VIDEO).put("ref", target.videoId)
            is NotificationTarget.Short -> put("kind", KIND_SHORT).put("ref", target.videoId)
            is NotificationTarget.Post ->
                put("kind", KIND_POST).put("ref", target.detailParams)
                    .put("url", target.url ?: JSONObject.NULL)
            is NotificationTarget.Link -> put("kind", KIND_LINK).put("ref", target.url)
            null -> Unit
        }
    }

    private fun fromJson(obj: JSONObject): NotificationItem? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        val message = obj.optString("message").takeIf { it.isNotBlank() } ?: return null
        fun text(key: String) = obj.optString(key).takeIf { it.isNotBlank() && !obj.isNull(key) }
        val ref = text("ref")
        val target = when (obj.optString("kind")) {
            KIND_VIDEO -> ref?.let { NotificationTarget.Video(it) }
            KIND_SHORT -> ref?.let { NotificationTarget.Short(it) }
            KIND_POST -> ref?.let { NotificationTarget.Post(it, text("url")) }
            KIND_LINK -> ref?.let { NotificationTarget.Link(it) }
            else -> null
        }
        return NotificationItem(
            message = message,
            sentTime = obj.optString("sent"),
            channelAvatarUrl = text("avatar"),
            videoThumbnailUrl = text("thumb"),
            target = target,
            isRead = obj.optBoolean("read", true),
            id = id,
            firstSeenMs = obj.optLong("seen", 0L),
        )
    }

    private fun itemsKey(profileId: String) = "items_$profileId"
    private fun hiddenKey(profileId: String) = "hidden_$profileId"

    private companion object {
        const val TAG = "NotificationHistory"
        const val PREFS_NAME = "notification_inbox_history"

        // Stored, so frozen.
        const val KIND_VIDEO = "video"
        const val KIND_SHORT = "short"
        const val KIND_POST = "post"
        const val KIND_LINK = "link"
    }
}

/** How many notifications a profile keeps. An inbox is a dozen, so this is months of it. */
internal const val NOTIFICATION_HISTORY_MAX = 200

/** How long a notification YouTube has dropped is kept. */
internal const val NOTIFICATION_HISTORY_MAX_AGE_MS = 60L * 24 * 60 * 60 * 1000

/**
 * The fresh inbox followed by what only the device still remembers.
 *
 * Fresh items keep YouTube's order and state, and keep the time they were first
 * seen; one seen for the first time is stamped now, a millisecond apart down
 * the list, so the order they arrived in survives once they are history. An
 * item with no id cannot be recognised next time and is shown but never
 * remembered. Pure, so the rules can be held by a JVM test.
 */
internal fun mergeNotificationHistory(
    known: List<NotificationItem>,
    fresh: List<NotificationItem>,
    nowMs: Long,
): List<NotificationItem> {
    val knownById = known.associateBy { it.id }
    val current = fresh.filter { it.id.isNotBlank() }.distinctBy { it.id }
        .mapIndexed { index, item ->
            item.copy(
                firstSeenMs = knownById[item.id]?.firstSeenMs?.takeIf { it > 0L } ?: (nowMs - index),
                remembered = false,
            )
        }
    val currentIds = current.mapTo(HashSet()) { it.id }
    val older = known
        .filter { it.id !in currentIds && nowMs - it.firstSeenMs <= NOTIFICATION_HISTORY_MAX_AGE_MS }
        .sortedByDescending { it.firstSeenMs }
        // Whatever YouTube last said about it, an item it no longer lists is not new.
        .map { it.copy(remembered = true, isRead = true) }
    return (current + older).take(NOTIFICATION_HISTORY_MAX)
}
