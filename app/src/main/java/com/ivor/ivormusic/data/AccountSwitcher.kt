package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place that decides what switching profiles means.
 *
 * Pointing the app at another profile is the easy half - see [ProfileManager],
 * where it is a single preference write. The hard half is everything in the
 * process that is still holding the *previous* profile's state, and getting
 * that wrong is how an account switcher ends up showing one account's feed
 * under another account's name.
 *
 * What has to be dropped, and why:
 *
 * - **visitorData.** Cached in [YouTubeRepository]'s companion, persisted
 *   device-wide, and prefetched from two places ([MusicService] and the video
 *   ViewModel). It is the anti-bot identity, and that file already warns that
 *   a stale or shared value gets flagged `LOGIN_REQUIRED`. Carrying one
 *   account's into another is exactly that failure, so it is dropped from
 *   memory and disk and re-minted.
 * - **Search extractor/page caches**, which hold personalised results.
 * - **The expired verdict**, which is per-profile and has to be re-read.
 * - **The process-wide profile-scoped stores** - watch history, local
 *   subscriptions, the blocklist, upload mutes and liked songs - whose stored
 *   keys are per-profile but whose flows are shared, so the new profile would
 *   otherwise be shown the previous one's until something reloaded them, and
 *   the next write would store the previous one's data under the new key.
 *
 * All of that runs before [activeProfileId] emits ([prepareForActiveProfile]);
 * see [ProfileManager.setActive] for why the order matters. Listening and
 * search history need nothing: they are read from disk on every call.
 *
 * Everything else follows automatically, because every consumer resolves the
 * session fresh on each call.
 *
 * ViewModels and the service cannot be reached directly - there is no DI - so
 * they observe [activeProfileId] and reset themselves. It is companion-scoped
 * for the same reason [LocalSubscriptionsRepository]'s flows are.
 */
class AccountSwitcher(context: Context) {

    private val appContext = context.applicationContext
    private val profileManager = ProfileManager(appContext)
    private val sessionManager = SessionManager(appContext)

    val profiles: StateFlow<List<Profile>> get() = profileManager.profiles

    /** The active profile id. Every account-derived cache observes this. */
    val activeProfileId: StateFlow<String> get() = profileManager.activeProfileId

    fun active(): Profile = profileManager.active()

    /**
     * Whether [profileId] still has a stored session behind it.
     *
     * Tells the two reasons a YouTube profile can be unusable apart, which
     * look identical on [Profile.expired] alone: a session YouTube started
     * rejecting (cookies present, refreshable) versus a profile that has never
     * had a session on this device at all - which is what a profile restored
     * from a backup is, since backups deliberately carry no credentials.
     * Calling both "expired" told someone on a new phone that something had
     * broken, when nothing had.
     */
    fun hasStoredSession(profileId: String): Boolean =
        profileManager.cookiesFor(profileId) != null

    /**
     * True while a switch is settling, so the UI can show progress on the
     * avatar rather than blocking the whole app behind a spinner.
     */
    val switching: StateFlow<Boolean> get() = sharedSwitching.asStateFlow()

    /**
     * Move the app onto [profileId].
     *
     * Returns false when the profile is unknown or already active, so the
     * caller can skip the refresh work. Cheap and synchronous by design: no
     * network happens here, which is what lets a switch work offline and land
     * on the next frame.
     */
    fun switchTo(profileId: String): Boolean {
        val target = profileManager.get(profileId) ?: return false
        if (target.id == profileManager.activeProfileId.value) return false

        sharedSwitching.value = true
        try {
            profileManager.setActive(target.id) { prepareForActiveProfile(appContext) }
            sessionManager.refreshExpiredFromProfile()
        } finally {
            sharedSwitching.value = false
        }
        return true
    }

    /**
     * Drop everything in the process that belonged to the previous profile,
     * after the fact.
     *
     * For the paths that change the active profile's identity without
     * changing its id (signing out of the last account) or that may not have
     * switched at all; a switch itself goes through [switchTo].
     */
    fun invalidateForProfileChange() {
        prepareForActiveProfile(appContext)
        sessionManager.refreshExpiredFromProfile()
    }

    /** Create a device-only profile and switch to it. */
    fun addLocalProfileAndSwitch(name: String): Profile {
        val profile = profileManager.addLocalProfile(name)
        switchTo(profile.id)
        return profile
    }

    /**
     * Store a freshly captured YouTube session as a profile and switch to it.
     *
     * [datasyncId] recognises an account already in the roster, so signing back
     * into one repairs that profile rather than adding a duplicate row.
     *
     * Signing in from a device-only profile is signing in, not adding a
     * stranger: a brand-new profile made from there starts with a copy of that
     * profile's data ([copyProfileScopedData]). From a YouTube profile it is a
     * second account and starts empty.
     */
    fun addYouTubeProfileAndSwitch(
        cookies: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ): Profile {
        val signingInFrom = profileManager.active()
        val known = profileManager.profiles.value.mapTo(HashSet()) { it.id }
        val profile = profileManager.addYouTubeProfile(cookies, name, handle, avatarUrl, datasyncId)
        if (signingInFrom.isLocal && profile.id !in known) {
            copyProfileScopedData(appContext, signingInFrom.id, profile.id)
        }
        if (!switchTo(profile.id)) invalidateForProfileChange()
        return profile
    }

    /**
     * The profile a long-press on the avatar would flip to, or null when there
     * is nothing to flip back to yet.
     */
    fun quickSwitchTarget(): Profile? =
        profileManager.previousProfileId.value
            ?.takeIf { it != profileManager.activeProfileId.value }
            ?.let { profileManager.get(it) }
            ?: profiles.value.firstOrNull { it.id != profileManager.activeProfileId.value }
                ?.takeIf { profiles.value.size == 2 }

    /**
     * Flip straight back to the last profile, skipping the sheet.
     *
     * Falls back to "the other one" when there are exactly two profiles and no
     * history yet, because with two the intent is unambiguous - and two is the
     * common case this shortcut exists for. Returns the profile switched to, or
     * null when there was nothing to switch to.
     */
    fun quickSwitch(): Profile? {
        val target = quickSwitchTarget() ?: return null
        return if (switchTo(target.id)) target else null
    }

    /**
     * Sign a YouTube profile out without removing it from the roster.
     *
     * Used when it is the only profile there is: the app must always have an
     * identity, so rather than deleting it, the account is stripped off and
     * what remains is a device-only profile. Its subscriptions and blocklist
     * are keyed to the profile id, which does not change, so disconnecting an
     * account does not throw away device-local work that never needed one.
     */
    fun signOut(profileId: String) {
        profileManager.replaceWithFreshLocal(profileId)
        if (profileManager.activeProfileId.value == profileId) invalidateForProfileChange()
    }

    /** Remove a profile. Returns false when it is the only one left. */
    fun remove(profileId: String): Boolean {
        val wasActive = profileManager.activeProfileId.value == profileId
        if (!profileManager.remove(profileId) { prepareForActiveProfile(appContext) }) return false
        if (wasActive) sessionManager.refreshExpiredFromProfile()
        return true
    }

    fun rename(profileId: String, name: String) {
        profileManager.updateIdentity(profileId, name = name)
    }

    companion object {
        private val sharedSwitching = MutableStateFlow(false)

        /**
         * Re-point everything process-wide at the profile now stored as
         * active. Passed to [ProfileManager.setActive] as its `beforePublish`,
         * so it resolves the profile from the stored id and never from a
         * [ProfileManager] flow, which still names the profile being left.
         *
         * Keep this list and [copyProfileScopedData]'s in step: a
         * process-wide store missing here shows the previous profile's data
         * and then writes it into the new one's.
         */
        internal fun prepareForActiveProfile(context: Context) {
            val appContext = context.applicationContext
            YouTubeRepository.invalidateSessionScopedCaches(appContext)
            LocalSubscriptionsRepository.reloadForActiveProfile(appContext)
            NotInterestedRepository.reloadForActiveProfile(appContext)
            VideoHistoryRepository.reloadForActiveProfile(appContext)
            UploadCheckRepository.reloadForActiveProfile(appContext)
            LikedSongsRepository.reloadForActiveProfile(appContext)
        }

        /**
         * Give a brand-new profile a copy of everything that belongs to
         * [fromProfileId]: listening, search and watch history, likes, local
         * subscriptions, the blocklist and upload mutes.
         *
         * Used when signing in from a device-only profile, which creates a
         * new YouTube profile rather than converting the local one. Without
         * it, signing in read as losing everything built up signed out. Each
         * store copies only into keys the target does not have yet, so this
         * can add data but never replace any.
         */
        internal fun copyProfileScopedData(context: Context, fromProfileId: String, toProfileId: String) {
            if (fromProfileId.isBlank() || toProfileId.isBlank() || fromProfileId == toProfileId) return
            val appContext = context.applicationContext
            StatsRepository.copyProfileData(appContext, fromProfileId, toProfileId)
            SearchHistoryRepository.copyProfileData(appContext, fromProfileId, toProfileId)
            LikedSongsRepository.copyProfileData(appContext, fromProfileId, toProfileId)
            VideoHistoryRepository.copyProfileData(appContext, fromProfileId, toProfileId)
            LocalSubscriptionsRepository.copyProfileData(appContext, fromProfileId, toProfileId)
            NotInterestedRepository.copyProfileData(appContext, fromProfileId, toProfileId)
            UploadCheckRepository.copyProfileData(appContext, fromProfileId, toProfileId)
        }
    }
}
