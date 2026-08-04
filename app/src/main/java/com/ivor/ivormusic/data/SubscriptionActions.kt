package com.ivor.ivormusic.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * The one place that decides what a Subscribe tap means.
 *
 * There are two stores a subscription can live in - the device and the Google
 * account - and the button in the player is a single toggle over both. Three
 * ViewModels drive that button (video, Shorts, and the channel sheet through
 * the video one), so the routing rules live here rather than being written
 * out three times and drifting.
 *
 * The rules:
 *
 * - **Subscribing** writes wherever [ThemePreferences.subscribeTarget] says.
 *   On `auto` that is the account when signed in and the device otherwise, so
 *   nothing changes for someone who never opens the setting.
 * - **Unsubscribing ignores the target and clears both stores.** A toggle that
 *   turned off in one place and left the channel subscribed in the other
 *   would leave the button reading "Subscribe" for a channel the user is
 *   still following - the UI would simply be lying. Turning something off has
 *   to turn it off.
 * - The button only demands a login when the user has explicitly chosen the
 *   YouTube-account target. Every other setting has a path that works signed
 *   out, and throwing a sign-in wall at someone who picked "this device" would
 *   defeat the point of local subscriptions.
 */
class SubscriptionActions(
    context: Context,
    private val youtubeRepository: YouTubeRepository
) {
    private val appContext = context.applicationContext
    private val localSubscriptions = LocalSubscriptionsRepository(appContext)
    private val themePreferences = ThemePreferences(appContext)
    private val sessionManager = SessionManager(appContext)

    /** Process-wide, so a subscribe in the player lights up the tab at once. */
    val subscriptions: StateFlow<List<LocalSubscription>> = localSubscriptions.subscriptions

    fun isLocallySubscribed(channelId: String?): Boolean =
        localSubscriptions.isSubscribed(channelId)

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    /**
     * Where a Subscribe tap should write right now. Resolves `auto` against
     * the live login state; read fresh every time because Settings and the
     * player hold different ThemePreferences instances.
     */
    fun resolveTarget(): SubscriptionStore =
        when (themePreferences.currentSubscribeTarget()) {
            ThemePreferences.SUBSCRIPTIONS_LOCAL -> SubscriptionStore.LOCAL
            ThemePreferences.SUBSCRIPTIONS_YOUTUBE -> SubscriptionStore.YOUTUBE
            ThemePreferences.SUBSCRIPTIONS_BOTH -> SubscriptionStore.BOTH
            else -> if (sessionManager.isLoggedIn()) SubscriptionStore.YOUTUBE
            else SubscriptionStore.LOCAL
        }

    /**
     * True when tapping Subscribe cannot do anything useful without a sign-in,
     * i.e. the user picked the account target and is signed out. The UI shows
     * its login prompt on this, and on nothing else.
     */
    fun subscribeNeedsLogin(): Boolean =
        resolveTarget() == SubscriptionStore.YOUTUBE && !sessionManager.isLoggedIn()

    /**
     * Applies [subscribe] to the channel. [remotelySubscribed] is YouTube's
     * own view of the state, from the watch-next engagement block, and is
     * what decides whether an unsubscribe needs a network call at all.
     *
     * Returns false only when a requested write failed, so the caller can roll
     * its optimistic state back. A local write cannot fail, so a target that
     * includes the device always reports success for that half.
     */
    suspend fun setSubscribed(
        channel: LocalSubscription,
        subscribe: Boolean,
        remotelySubscribed: Boolean
    ): Boolean {
        if (channel.channelId.isBlank()) return false

        if (!subscribe) {
            // Clear both stores; see the class comment on why the target is
            // deliberately not consulted here.
            localSubscriptions.unsubscribe(channel.channelId)
            return if (remotelySubscribed && sessionManager.isLoggedIn()) {
                youtubeRepository.setSubscribed(channel.channelId, false)
            } else true
        }

        return when (resolveTarget()) {
            SubscriptionStore.LOCAL -> {
                localSubscriptions.subscribe(channel)
                true
            }
            SubscriptionStore.YOUTUBE ->
                youtubeRepository.setSubscribed(channel.channelId, true)
            SubscriptionStore.BOTH -> {
                localSubscriptions.subscribe(channel)
                // The local half already succeeded, so a failed remote write
                // must not report failure and make the caller roll back a
                // subscription that is genuinely stored.
                if (sessionManager.isLoggedIn()) {
                    youtubeRepository.setSubscribed(channel.channelId, true)
                }
                true
            }
        }
    }
}
