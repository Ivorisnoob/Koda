package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingChannelNameTest {
    @Test
    fun blankIsHowAParserSaysTheResponseNamedNoChannel() {
        assertTrue(isMissingChannelName(null))
        assertTrue(isMissingChannelName(""))
        assertTrue(isMissingChannelName("   "))
    }

    @Test
    fun thePlaceholderOlderBuildsStoredIsStillRecognised() {
        assertTrue(isMissingChannelName("Unknown Channel"))
        assertTrue(isMissingChannelName(" unknown channel "))
        assertEquals("", cleanChannelName("Unknown Channel"))
    }

    @Test
    fun aRealNameIsKeptEvenWhenItLooksLikeAPlaceholder() {
        assertFalse(isMissingChannelName("Unknown"))
        assertFalse(isMissingChannelName("Unknown Channel Official"))
        assertEquals("Unknown Mortal Orchestra", cleanChannelName("Unknown Mortal Orchestra"))
        assertEquals("jawed", cleanChannelName("jawed"))
    }

    @Test
    fun aSongTitleSentinelHasOneSpelling() {
        assertTrue(isUnknownTitle(UNKNOWN_TITLE))
        assertTrue(isUnknownTitle("unknown"))
        assertTrue(isUnknownTitle(""))
        assertFalse(isUnknownTitle("Unknown Pleasures"))
    }
}
