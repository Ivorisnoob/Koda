package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two-mode to three-mode migration.
 *
 * Every case here compiles either way, which is the whole reason the test
 * exists: reading the legacy key backwards, or not reading it at all, resets
 * an existing user's mode on update with no error anywhere.
 */
class AppModeTest {

    @Test
    fun freshInstallWithNothingStoredIsMusic() {
        assertEquals(AppMode.MUSIC, appModeFromStored(stored = null, legacyVideoMode = false))
    }

    @Test
    fun upgradingUserOnVideoModeStaysOnVideo() {
        assertEquals(AppMode.VIDEO, appModeFromStored(stored = null, legacyVideoMode = true))
    }

    @Test
    fun storedModeWinsOverTheLegacyKey() {
        // setAppMode writes both keys, so a TV user has video_mode = false and
        // a Video user has it true. Neither may override the real value.
        assertEquals(AppMode.TV, appModeFromStored("TV", legacyVideoMode = false))
        assertEquals(AppMode.MUSIC, appModeFromStored("MUSIC", legacyVideoMode = true))
        assertEquals(AppMode.VIDEO, appModeFromStored("VIDEO", legacyVideoMode = false))
    }

    @Test
    fun unreadableStoredValueLandsWhereAFreshInstallDoes() {
        assertEquals(AppMode.MUSIC, appModeFromStored("PODCAST", legacyVideoMode = false))
        assertEquals(AppMode.MUSIC, appModeFromStored("", legacyVideoMode = true))
    }

    /**
     * Persisted by name and frozen. If this test is failing because a constant
     * was renamed, the rename is the bug - every existing user's stored mode
     * would fall through to MUSIC on their next launch.
     */
    @Test
    fun persistedNamesAreFrozen() {
        assertEquals(listOf("MUSIC", "VIDEO", "TV"), AppMode.entries.map { it.name })
    }

    @Test
    fun eachModeKnowsItsOwnLastTabIndex() {
        assertEquals(2, AppMode.MUSIC.lastTabIndex)
        assertEquals(3, AppMode.VIDEO.lastTabIndex)
        assertEquals(2, AppMode.TV.lastTabIndex)
    }
}
