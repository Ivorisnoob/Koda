package com.ivor.ivormusic.ui.home

import com.ivor.ivormusic.data.ThemePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRefreshPolicyTest {

    @Test
    fun localImportsWarmWithoutAnAccount() {
        assertTrue(
            shouldWarmSubscriptionFeed(
                ThemePreferences.SUBSCRIPTIONS_AUTO,
                hasLocalSubscriptions = true,
                isLoggedIn = false
            )
        )
        assertTrue(
            shouldWarmSubscriptionFeed(
                ThemePreferences.SUBSCRIPTIONS_LOCAL,
                hasLocalSubscriptions = true,
                isLoggedIn = false
            )
        )
    }

    @Test
    fun explicitSourceSelectionIsRespected() {
        assertFalse(
            shouldWarmSubscriptionFeed(
                ThemePreferences.SUBSCRIPTIONS_YOUTUBE,
                hasLocalSubscriptions = true,
                isLoggedIn = false
            )
        )
        assertFalse(
            shouldWarmSubscriptionFeed(
                ThemePreferences.SUBSCRIPTIONS_LOCAL,
                hasLocalSubscriptions = false,
                isLoggedIn = true
            )
        )
    }
}
