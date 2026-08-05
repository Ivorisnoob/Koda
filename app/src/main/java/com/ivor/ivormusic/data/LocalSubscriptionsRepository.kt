package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Device-local subscriptions: the channels a user follows without a YouTube
 * account, plus the groups they organise them into.
 *
 * **The backing state is process-wide.** Every other repository in this app
 * news itself up per ViewModel and accepts that StateFlow updates do not cross
 * instances (see CLAUDE.md), but subscriptions cannot live with that: tapping
 * Subscribe inside the video player has to light up the Subscriptions tab
 * immediately, and those are different ViewModels holding different
 * instances. So the flows here are held in the companion object and shared by
 * every instance - the same trick the visitorData cache uses. Reads still go
 * through the flow, writes still hit SharedPreferences synchronously, so a
 * process restart loses nothing.
 */
class LocalSubscriptionsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Which profile's subscriptions these are.
     *
     * Resolved fresh on every read and write: an instance outlives a profile
     * switch, and a captured id would keep writing into the profile the user
     * just left.
     */
    private fun subscriptionsKey() = ProfileManager.profileScopedKey(
        KEY_SUBSCRIPTIONS,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    private fun groupsKey() = ProfileManager.profileScopedKey(
        KEY_GROUPS,
        ProfileManager.activeProfileId(appContext),
        ProfileManager.legacyProfileId(appContext)
    )

    init {
        // First instance in the process seeds the shared state from disk.
        synchronized(LOCK) {
            if (sharedSubscriptions == null) {
                sharedSubscriptions = MutableStateFlow(loadSubscriptions())
                sharedGroups = MutableStateFlow(loadGroups())
            }
        }
    }

    private val subscriptionsState: MutableStateFlow<List<LocalSubscription>>
        get() = sharedSubscriptions!!

    private val groupsState: MutableStateFlow<List<SubscriptionGroup>>
        get() = sharedGroups!!

    /** All local subscriptions, newest subscription first. */
    val subscriptions: StateFlow<List<LocalSubscription>> get() = subscriptionsState.asStateFlow()

    val groups: StateFlow<List<SubscriptionGroup>> get() = groupsState.asStateFlow()

    // ---------------- Reads ----------------

    fun getAll(): List<LocalSubscription> = subscriptionsState.value

    /** Local subscriptions sorted A-Z, for the "All channels" list. */
    fun getAllSortedByName(): List<LocalSubscription> =
        subscriptionsState.value.sortedBy { it.name.lowercase() }

    fun isSubscribed(channelId: String?): Boolean {
        if (channelId.isNullOrBlank()) return false
        return subscriptionsState.value.any { it.channelId == channelId }
    }

    fun get(channelId: String): LocalSubscription? =
        subscriptionsState.value.firstOrNull { it.channelId == channelId }

    fun getGroup(groupId: String): SubscriptionGroup? =
        groupsState.value.firstOrNull { it.id == groupId }

    /** The channels in [groupId], or all subscriptions when the group is gone. */
    fun channelsInGroup(groupId: String?): List<LocalSubscription> {
        val group = groupId?.let { getGroup(it) } ?: return getAll()
        val ids = group.channelIds.toSet()
        return getAll().filter { it.channelId in ids }
    }

    // ---------------- Writes ----------------

    /** Adds a channel, or refreshes the stored name/avatar if it is already there. */
    fun subscribe(subscription: LocalSubscription) {
        if (subscription.channelId.isBlank()) return
        val current = subscriptionsState.value
        val existing = current.firstOrNull { it.channelId == subscription.channelId }
        val next = if (existing == null) {
            listOf(subscription) + current
        } else {
            // Keep the original subscribedAt so the list does not reshuffle when
            // a fresher avatar arrives from a watch page.
            current.map {
                if (it.channelId == subscription.channelId) {
                    it.copy(
                        name = subscription.name.takeIf { n -> n.isNotBlank() } ?: it.name,
                        avatarUrl = subscription.avatarUrl ?: it.avatarUrl,
                        handle = subscription.handle ?: it.handle
                    )
                } else it
            }
        }
        saveSubscriptions(next)
    }

    fun unsubscribe(channelId: String) {
        val next = subscriptionsState.value.filterNot { it.channelId == channelId }
        if (next.size == subscriptionsState.value.size) return
        saveSubscriptions(next)
        // A group holding a dropped channel would otherwise keep a dead id and
        // quietly under-count itself in the group chips.
        val prunedGroups = groupsState.value.map { group ->
            if (channelId in group.channelIds) {
                group.copy(channelIds = group.channelIds - channelId)
            } else group
        }
        if (prunedGroups != groupsState.value) saveGroups(prunedGroups)
    }

    /** Returns the state after the toggle: true = now subscribed. */
    fun toggle(subscription: LocalSubscription): Boolean {
        return if (isSubscribed(subscription.channelId)) {
            unsubscribe(subscription.channelId)
            false
        } else {
            subscribe(subscription)
            true
        }
    }

    /**
     * Refreshes name/avatar/handle for channels already in the store, in one
     * write.
     *
     * Used by the profile backfill, which resolves a dozen channels at a time:
     * calling [subscribe] per channel would emit the whole list a dozen times
     * and re-sort it downstream on each. Channels no longer in the store are
     * skipped rather than re-added - the user may have unfollowed one while
     * its lookup was in flight, and a backfill must never resurrect it.
     */
    fun updateProfiles(updates: List<LocalSubscription>) {
        if (updates.isEmpty()) return
        val byId = updates.associateBy { it.channelId }
        var changed = false
        val next = subscriptionsState.value.map { existing ->
            val update = byId[existing.channelId] ?: return@map existing
            changed = true
            existing.copy(
                name = update.name.takeIf { it.isNotBlank() } ?: existing.name,
                avatarUrl = update.avatarUrl ?: existing.avatarUrl,
                handle = update.handle ?: existing.handle
            )
        }
        if (changed) saveSubscriptions(next)
    }

    /**
     * Merges [channels] into the store, keeping whatever is already there.
     * Returns how many were genuinely new.
     */
    fun importAll(channels: List<LocalSubscription>): Int {
        if (channels.isEmpty()) return 0
        val current = subscriptionsState.value
        val known = current.map { it.channelId }.toMutableSet()
        val additions = mutableListOf<LocalSubscription>()
        for (channel in channels) {
            if (channel.channelId.isBlank()) continue
            if (known.add(channel.channelId)) additions.add(channel)
        }
        if (additions.isEmpty()) return 0
        saveSubscriptions(additions + current)
        return additions.size
    }

    fun clearAll() {
        saveSubscriptions(emptyList())
        saveGroups(emptyList())
    }

    // ---------------- Groups ----------------

    fun createGroup(name: String, channelIds: List<String> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        saveGroups(groupsState.value + SubscriptionGroup(id, name.trim(), channelIds))
        return id
    }

    fun renameGroup(groupId: String, name: String) {
        saveGroups(groupsState.value.map {
            if (it.id == groupId) it.copy(name = name.trim()) else it
        })
    }

    fun deleteGroup(groupId: String) {
        saveGroups(groupsState.value.filterNot { it.id == groupId })
    }

    fun setGroupChannels(groupId: String, channelIds: List<String>) {
        saveGroups(groupsState.value.map {
            if (it.id == groupId) it.copy(channelIds = channelIds.distinct()) else it
        })
    }

    fun toggleChannelInGroup(groupId: String, channelId: String) {
        saveGroups(groupsState.value.map { group ->
            if (group.id != groupId) group
            else if (channelId in group.channelIds) group.copy(channelIds = group.channelIds - channelId)
            else group.copy(channelIds = group.channelIds + channelId)
        })
    }

    // ---------------- Persistence ----------------

    private fun saveSubscriptions(list: List<LocalSubscription>) {
        subscriptionsState.value = list
        val array = JSONArray()
        list.forEach { sub ->
            array.put(JSONObject().apply {
                put("channelId", sub.channelId)
                put("name", sub.name)
                put("avatarUrl", sub.avatarUrl ?: JSONObject.NULL)
                put("handle", sub.handle ?: JSONObject.NULL)
                put("subscribedAt", sub.subscribedAt)
            })
        }
        prefs.edit().putString(subscriptionsKey(), array.toString()).apply()
    }

    private fun loadSubscriptions(): List<LocalSubscription> {
        val raw = prefs.getString(subscriptionsKey(), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val channelId = obj.optString("channelId").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                LocalSubscription(
                    channelId = channelId,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: channelId,
                    avatarUrl = obj.optNullableString("avatarUrl"),
                    handle = obj.optNullableString("handle"),
                    subscribedAt = obj.optLong("subscribedAt", 0L)
                )
            }.distinctBy { it.channelId }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load local subscriptions", e)
            emptyList()
        }
    }

    private fun saveGroups(list: List<SubscriptionGroup>) {
        groupsState.value = list
        val array = JSONArray()
        list.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("channelIds", JSONArray().also { ids -> group.channelIds.forEach(ids::put) })
            })
        }
        prefs.edit().putString(groupsKey(), array.toString()).apply()
    }

    private fun loadGroups(): List<SubscriptionGroup> {
        val raw = prefs.getString(groupsKey(), null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val ids = obj.optJSONArray("channelIds")
                SubscriptionGroup(
                    id = id,
                    name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Group",
                    channelIds = (0 until (ids?.length() ?: 0)).mapNotNull { j ->
                        ids?.optString(j)?.takeIf { it.isNotBlank() }
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load subscription groups", e)
            emptyList()
        }
    }

    private fun JSONObject.optNullableString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val TAG = "LocalSubsRepo"
        private const val PREFS_NAME = "local_subscriptions"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val KEY_GROUPS = "groups"

        private val LOCK = Any()

        /**
         * Re-seed the shared flows from the newly active profile's keys.
         *
         * Local subscriptions follow the profile because they are what the
         * device-local feed is built from, and that feed is the identity's.
         * The flows are process-wide, so a switch has to push new values
         * through them - no new instance is coming.
         */
        fun reloadForActiveProfile(context: Context) {
            val repo = LocalSubscriptionsRepository(context)
            synchronized(LOCK) {
                sharedSubscriptions?.value = repo.loadSubscriptions()
                sharedGroups?.value = repo.loadGroups()
            }
        }

        @Volatile
        private var sharedSubscriptions: MutableStateFlow<List<LocalSubscription>>? = null

        @Volatile
        private var sharedGroups: MutableStateFlow<List<SubscriptionGroup>>? = null
    }
}
