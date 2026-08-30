package com.ivor.ivormusic.ui.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the "add addon" field accepts.
 *
 * The ecosystem's install buttons produce `stremio://` links and its guides
 * print bare hosts, so rejecting either would make the obvious paste fail for
 * no reason a user could act on.
 */
class AddonUrlNormaliseTest {

    private fun norm(s: String) = TvAddonsViewModel.normalise(s)

    @Test
    fun aFullManifestUrlIsKept() {
        assertEquals(
            "https://v3-cinemeta.strem.io/manifest.json",
            norm("https://v3-cinemeta.strem.io/manifest.json")
        )
    }

    @Test
    fun theStremioSchemeBecomesHttps() {
        assertEquals(
            "https://torrentio.strem.fun/manifest.json",
            norm("stremio://torrentio.strem.fun/manifest.json")
        )
    }

    @Test
    fun httpIsUpgraded() {
        assertEquals("https://host/manifest.json", norm("http://host/manifest.json"))
    }

    @Test
    fun aBareHostGetsBothSchemeAndFilename() {
        assertEquals("https://host/manifest.json", norm("host"))
        assertEquals("https://host/manifest.json", norm("host/"))
    }

    @Test
    fun configurationSegmentsSurvive() {
        // The debrid key lives in the path. Dropping it would install an
        // addon that silently returns unconfigured results.
        assertEquals(
            "https://torrentio.strem.fun/realdebrid=KEY/manifest.json",
            norm("stremio://torrentio.strem.fun/realdebrid=KEY/manifest.json")
        )
        assertEquals(
            "https://torrentio.strem.fun/realdebrid=KEY/manifest.json",
            norm("https://torrentio.strem.fun/realdebrid=KEY")
        )
    }

    @Test
    fun whitespaceIsTrimmed() {
        assertEquals("https://host/manifest.json", norm("  https://host/manifest.json  "))
    }

    @Test
    fun emptyAndUnknownSchemesAreRejected() {
        assertEquals("", norm(""))
        assertEquals("", norm("   "))
        assertEquals("", norm("ftp://host/manifest.json"))
        assertEquals("", norm("javascript://evil"))
    }
}
