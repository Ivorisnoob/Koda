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
            profileManager.setActive(target.id)
            invalidateForProfileChange()
        } finally {
            sharedSwitching.value = false
        }
        return true
    }

    /**
     * Drop everything in the process that belonged to the previous profile.
     *
     * Also called after adding or removing a profile, since both can change
     * which one is active.
     */
    fun invalidateForProfileChange() {
        YouTubeRepository.invalidateSessionScopedCaches(appContext)
        LocalSubscriptionsRepository.reloadForActiveProfile(appContext)
        NotInterestedRepository.reloadForActiveProfile(appContext)
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
     */
    fun addYouTubeProfileAndSwitch(
        cookies: String,
        name: String? = null,
        handle: String? = null,
        avatarUrl: String? = null,
        datasyncId: String? = null
    ): Profile {
        val profile = profileManager.addYouTubeProfile(cookies, name, handle, avatarUrl, datasyncId)
        if (!switchTo(profile.id)) invalidateForProfileChange()
        return profile
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
        if (!profileManager.remove(profileId)) return false
        if (wasActive) invalidateForProfileChange()
        return true
    }

    fun rename(profileId: String, name: String) {
        profileManager.updateIdentity(profileId, name = name)
    }

    companion object {
        private val sharedSwitching = MutableStateFlow(false)
    }
}
