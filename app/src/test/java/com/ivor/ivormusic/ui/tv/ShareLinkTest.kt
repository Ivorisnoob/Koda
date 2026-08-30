package com.ivor.ivormusic.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {

    @Test
    fun imdbIdsShareAnImdbPage() {
        assertEquals("https://www.imdb.com/title/tt1375666/", shareLinkFor("tt1375666"))
    }

    @Test
    fun kitsuIdsShareTheirKitsuPage() {
        assertEquals("https://kitsu.app/anime/46474", shareLinkFor("kitsu:46474"))
    }

    /**
     * Sharing nothing beats sharing a link that resolves to the wrong title, so
     * an id with no reliable public page is not offered rather than guessed at.
     */
    @Test
    fun idsWithNoReliablePublicPageAreNotShared() {
        assertNull(shareLinkFor("mal:52991"))
        assertNull(shareLinkFor("anilist:154587"))
        assertNull(shareLinkFor("someaddon:12345"))
        assertNull(shareLinkFor(""))
    }
}
