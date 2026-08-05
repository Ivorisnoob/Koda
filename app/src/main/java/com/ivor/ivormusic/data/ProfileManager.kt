package com.ivor.ivormusic.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** What kind of identity a profile is. */
enum class ProfileKind { YOUTUBE, LOCAL }

/**
 * One identity in the app.
 *
 * A [ProfileKind.YOUTUBE] profile has a stored cookie string and a Google
 * identity behind it. A [ProfileKind.LOCAL] profile has neither - it is a
 * device-only identity, which is a first-class thing here rather than a
 * consolation prize: this app is built to work fully signed out, so "signed
 * out" is simply the local profile that always exists.
 *
 * [id] is a device-local UUID and never changes, because it keys this
 * profile's stored cookies and its feed-shaping data. [datasyncId] is
 * YouTube's own account identifier (`responseContext.mainAppWebResponseContext
 * .datasyncId`, verified August 2026) and is filled in once an authenticated
 * call has answered; it exists only to recognise that a re-added account is
 * one already in the roster, so adding it again updates rather than
 * duplicates.
 */
data class Profile(
    val id: String,
    val kind: ProfileKind,
    val name: String,
    val handle: String? = null,
    val avatarUrl: String? = null,
    val datasyncId: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    /**
     * True once YouTube answered this profile's authenticated call as
     * anonymous. Per-profile on purpose: a single global flag would badge the
     * wrong row the moment there is more than one account.
     */
    val expired: Boolean = false
) {
    val isLocal: Boolean get() = kind == ProfileKind.LOCAL
}

/**
 * The roster of profiles and which one is active.
 *
 * **Switching is deliberately just "point at another stored cookie string".**
 * Every consumer in this app resolves the session fresh on each call -
 * [SessionManager.getCookies] is a preference read, and NewPipe's downloader,
 * though initialised once, holds a SessionManager that resolves the same way -
 * so redirecting the active profile is enough to move the whole app onto
 * another account. No re-authentication, no network, works offline. What does
 * need doing is invalidation, and that is [AccountSwitcher]'s job.
 *
 * State is companion-scoped for the usual reason (see
 * [LocalSubscriptionsRepository]): with no DI, every ViewModel news up its own
 * instance, and a switch has to reach all of them at once.
 */
class ProfileManager(context: Context) {

    private val appContext = context.applicationContext

    // Opened once per process, not once per instance: SessionManager holds one
    // of these and is itself newed up in seven places, and each
    // EncryptedSharedPreferences.create is a keystore round trip.
    private val prefs = sharedPrefs(appContext)

    init {
        synchronized(LOCK) {
            if (sharedProfiles == null) {
                migrateIfNeeded()
                sharedProfiles = MutableStateFlow(loadProfiles())
                sharedActiveId = MutableStateFlow(loadActiveId())
            }
        }
    }

    val profiles: StateFlow<List<Profile>> get() = sharedProfiles!!.asStateFlow()

    /**
     * The active profile's id. Everything that caches account-derived state
     * observes this and resets when it changes.
     */
    val activeProfileId: StateFlow<String> get() = sharedActiveId!!.asStateFlow()

    fun active(): Profile = sharedProfiles!!.value.firstOrNull { it.id == sharedActiveId!!.value }
        ?: ensureDefaultLocal()

    fun get(id: String): Profile? = sharedProfiles!!.value.firstOrNull { it.id == id }

    // ---------------- Cookies (per profile) ----------------

    fun cookiesFor(id: String): String? =
        prefs.getString(keyCookies(id), null)?.takeIf { it.isNotBlank() }

    fun saveCookiesFor(id: String, cookies: String) {
        prefs.edit().putString(keyCookies(id), cookies).apply()
    }

    // ---------------- Roster writes ----------------

    /**
     * Add (or refresh) a YouTube profile from a captured cookie string.
     *
     * When [datasyncId] matches a profile already in the roster this updates
     * that one in place and returns it, so re-signing into an account that is
     * already here repairs it instead of leaving two rows that look identical.
     */
    fun addYouTubeProfile(
        cookies: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ): Profile {
        val existing = datasyncId?.let { sync ->
            sharedProfiles!!.value.firstOrNull { it.datasyncId == sync }
        }
        val profile = existing?.copy(
            name = name ?: existing.name,
            handle = handle ?: existing.handle,
            avatarUrl = avatarUrl ?: existing.avatarUrl,
            expired = false
        ) ?: Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.YOUTUBE,
            name = name ?: "YouTube account",
            handle = handle,
            avatarUrl = avatarUrl,
            datasyncId = datasyncId
        )
        saveCookiesFor(profile.id, cookies)
        upsert(profile)
        return profile
    }

    /** Create a device-only profile. Needs no account and never makes a request. */
    fun addLocalProfile(name: String): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.LOCAL,
            name = name.trim().takeIf { it.isNotBlank() } ?: "Local profile"
        )
        upsert(profile)
        return profile
    }

    /** Fill in identity details once an authenticated call has revealed them. */
    fun updateIdentity(
        id: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ) {
        val current = get(id) ?: return
        upsert(
            current.copy(
                name = name?.takeIf { it.isNotBlank() } ?: current.name,
                handle = handle ?: current.handle,
                avatarUrl = avatarUrl ?: current.avatarUrl,
                datasyncId = datasyncId ?: current.datasyncId
            )
        )
    }

    fun setExpired(id: String, expired: Boolean) {
        val current = get(id) ?: return
        if (current.expired == expired) return
        upsert(current.copy(expired = expired))
    }

    /**
     * Remove a profile, its cookies and its feed-shaping data.
     *
     * The last remaining profile cannot be removed - there is always an
     * identity, even if it is only the default local one - and removing the
     * active profile falls back to another rather than leaving nothing active.
     */
    fun remove(id: String): Boolean {
        val list = sharedProfiles!!.value
        if (list.size <= 1) return false
        val target = list.firstOrNull { it.id == id } ?: return false
        prefs.edit().remove(keyCookies(id)).apply()
        val next = list.filterNot { it.id == id }
        saveProfiles(next)
        if (sharedActiveId!!.value == target.id) {
            setActive(next.first().id)
        }
        return true
    }

    /**
     * Turn a profile into a device-only one: drop its cookies and its Google
     * identity, keep everything else.
     *
     * This is what signing out of the *last* remaining account does. The id is
     * deliberately kept, so the feed-shaping data scoped to it - local
     * subscriptions, the not-recommended list - survives the sign-out. Those
     * are device-local things the user built up; losing them because an
     * account was disconnected would be the app throwing away work it never
     * needed an account for.
     */
    fun replaceWithFreshLocal(id: String) {
        val current = get(id) ?: return
        prefs.edit().remove(keyCookies(id)).apply()
        upsert(
            current.copy(
                kind = ProfileKind.LOCAL,
                name = DEFAULT_LOCAL_NAME,
                handle = null,
                avatarUrl = null,
                datasyncId = null,
                expired = false
            )
        )
    }

    /** Point the app at another profile. Invalidation is [AccountSwitcher]'s job. */
    fun setActive(id: String) {
        if (get(id) == null) return
        prefs.edit().putString(KEY_ACTIVE_PROFILE, id).apply()
        sharedActiveId!!.value = id
    }

    // ---------------- Internals ----------------

    private fun upsert(profile: Profile) {
        val list = sharedProfiles!!.value
        val next = if (list.any { it.id == profile.id }) {
            list.map { if (it.id == profile.id) profile else it }
        } else {
            list + profile
        }
        saveProfiles(next)
    }

    private fun ensureDefaultLocal(): Profile {
        val existing = sharedProfiles!!.value.firstOrNull()
        if (existing != null) {
            setActive(existing.id)
            return existing
        }
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            kind = ProfileKind.LOCAL,
            name = DEFAULT_LOCAL_NAME
        )
        saveProfiles(listOf(profile))
        setActive(profile.id)
        return profile
    }

    /**
     * Bring a pre-profiles install forward.
     *
     * An existing signed-in session becomes the first YouTube profile and
     * keeps its stored name and avatar, so the upgrade is invisible: the app
     * comes back signed into the same account. An install with no session gets
     * the default local profile. Either way the existing feed-shaping data
     * (local subscriptions, the not-recommended list) belongs to whichever
     * profile this produces - it is what the user was looking at before the
     * upgrade, and [profileScopedKey] leaves the un-suffixed keys as that
     * profile's own.
     */
    private fun migrateIfNeeded() {
        if (prefs.contains(KEY_PROFILES)) return
        val legacyCookies = prefs.getString(LEGACY_KEY_COOKIES, null)?.takeIf { it.isNotBlank() }
        val profile = if (legacyCookies != null) {
            Profile(
                id = UUID.randomUUID().toString(),
                kind = ProfileKind.YOUTUBE,
                name = prefs.getString(LEGACY_KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }
                    ?: "YouTube account",
                avatarUrl = prefs.getString(LEGACY_KEY_USER_AVATAR, null)?.takeIf { it.isNotBlank() }
            )
        } else {
            Profile(
                id = UUID.randomUUID().toString(),
                kind = ProfileKind.LOCAL,
                name = DEFAULT_LOCAL_NAME
            )
        }
        val editor = prefs.edit()
        editor.putString(KEY_PROFILES, JSONArray().put(profile.toJson()).toString())
        editor.putString(KEY_ACTIVE_PROFILE, profile.id)
        editor.putString(KEY_MIGRATED_LEGACY_ID, profile.id)
        if (legacyCookies != null) editor.putString(keyCookies(profile.id), legacyCookies)
        editor.apply()
    }

    private fun saveProfiles(list: List<Profile>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
        sharedProfiles!!.value = list
    }

    private fun loadProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { fromJson(it) }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to load profiles", e)
            emptyList()
        }
    }

    private fun loadActiveId(): String {
        val stored = prefs.getString(KEY_ACTIVE_PROFILE, null)
        val list = loadProfiles()
        return stored?.takeIf { id -> list.any { it.id == id } }
            ?: list.firstOrNull()?.id
            ?: ""
    }

    private fun Profile.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("name", name)
        put("handle", handle ?: JSONObject.NULL)
        put("avatarUrl", avatarUrl ?: JSONObject.NULL)
        put("datasyncId", datasyncId ?: JSONObject.NULL)
        put("addedAt", addedAt)
        put("expired", expired)
    }

    private fun fromJson(obj: JSONObject): Profile? {
        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
        fun str(key: String) = obj.optString(key).takeIf { it.isNotBlank() && it != "null" }
        return Profile(
            id = id,
            kind = if (obj.optString("kind") == ProfileKind.LOCAL.name) ProfileKind.LOCAL
            else ProfileKind.YOUTUBE,
            name = str("name") ?: "Profile",
            handle = str("handle"),
            avatarUrl = str("avatarUrl"),
            datasyncId = str("datasyncId"),
            addedAt = obj.optLong("addedAt", 0L),
            expired = obj.optBoolean("expired", false)
        )
    }

    companion object {
        private const val TAG = "ProfileManager"
        private const val PREFS_FILE_NAME = "yt_music_session"

        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_MIGRATED_LEGACY_ID = "migrated_legacy_profile"

        // Pre-profiles keys, read once by the migration and then left alone.
        private const val LEGACY_KEY_COOKIES = "session_cookies"
        private const val LEGACY_KEY_USER_NAME = "user_name"
        private const val LEGACY_KEY_USER_AVATAR = "user_avatar"

        const val DEFAULT_LOCAL_NAME = "No account"

        private fun keyCookies(id: String) = "cookies_$id"

        private fun buildPrefs(context: Context) = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        @Volatile
        private var prefsInstance: android.content.SharedPreferences? = null

        /**
         * The one encrypted store, shared by every ProfileManager and
         * SessionManager in the process. Carries the same keystore-corruption
         * recovery SessionManager has always done, since it is the same file.
         */
        fun sharedPrefs(context: Context): android.content.SharedPreferences {
            prefsInstance?.let { return it }
            return synchronized(LOCK) {
                prefsInstance ?: run {
                    val app = context.applicationContext
                    val created = try {
                        buildPrefs(app)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "EncryptedSharedPreferences corrupted, resetting", e)
                        app.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        java.io.File(
                            app.filesDir.parent, "shared_prefs/$PREFS_FILE_NAME.xml"
                        ).delete()
                        buildPrefs(app)
                    }
                    prefsInstance = created
                    created
                }
            }
        }

        /** The profile that inherited the pre-profiles install's un-suffixed keys. */
        fun legacyProfileId(context: Context): String? =
            sharedPrefs(context).getString(KEY_MIGRATED_LEGACY_ID, null)

        /**
         * The active profile id, read straight from the store.
         *
         * A direct read rather than the flow, so the profile-scoped stores can
         * resolve their keys without depending on a ProfileManager instance
         * having been constructed first.
         */
        fun activeProfileId(context: Context): String =
            sharedPrefs(context).getString(KEY_ACTIVE_PROFILE, null).orEmpty()

        /**
         * Suffix a SharedPreferences key with the profile that owns it.
         *
         * The profile migrated from a pre-profiles install keeps the
         * *un-suffixed* keys, so an upgrade does not orphan the subscriptions
         * and blocklist the user already had - they simply become that
         * profile's.
         */
        fun profileScopedKey(base: String, profileId: String, legacyProfileId: String?): String =
            if (profileId == legacyProfileId) base else "${base}_$profileId"

        private val LOCK = Any()

        @Volatile
        private var sharedProfiles: MutableStateFlow<List<Profile>>? = null

        @Volatile
        private var sharedActiveId: MutableStateFlow<String>? = null
    }
}
