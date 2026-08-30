package com.ivor.ivormusic.data.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * URL shapes, checked against real addon URLs that were probed live.
 *
 * Every assertion here is a URL that returned 200 in August 2026, so a change
 * that breaks one is breaking a request that is known to work rather than one
 * that was only believed to.
 */
class StremioUrlsTest {

    @Test
    fun baseStripsTheManifestFilename() {
        assertEquals(
            "https://v3-cinemeta.strem.io/",
            StremioUrls.baseOf("https://v3-cinemeta.strem.io/manifest.json")
        )
    }

    @Test
    fun configuredAddonsKeepTheirConfigurationSegments() {
        // A debrid key rides in the path. Parsing the host instead of stripping
        // the filename would drop it and silently return unconfigured results.
        assertEquals(
            "https://torrentio.strem.fun/realdebrid=KEY%7Csort=quality/",
            StremioUrls.baseOf("https://torrentio.strem.fun/realdebrid=KEY%7Csort=quality/manifest.json")
        )
    }

    @Test
    fun aBaseWithoutManifestStillGetsOneTrailingSlash() {
        assertEquals("https://host/", StremioUrls.baseOf("https://host"))
        assertEquals("https://host/", StremioUrls.baseOf("https://host/"))
        assertEquals("https://host/", StremioUrls.baseOf("  https://host/manifest.json  "))
    }

    @Test
    fun plainResourceUrl() {
        assertEquals(
            "https://v3-cinemeta.strem.io/meta/series/tt0903747.json",
            StremioUrls.resource(
                "https://v3-cinemeta.strem.io/manifest.json", "meta", "series", "tt0903747"
            )
        )
    }

    @Test
    fun animeIdsHaveTheirColonsEncoded() {
        // Probed live: torrentio answers this exact path for kitsu:46474:1.
        assertEquals(
            "https://torrentio.strem.fun/stream/series/kitsu%3A46474%3A1.json",
            StremioUrls.resource(
                "https://torrentio.strem.fun/manifest.json", "stream", "series", "kitsu:46474:1"
            )
        )
    }

    @Test
    fun extrasBecomeATrailingSegmentWithLiteralSeparators() {
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/series/top/genre=Animation&skip=20.json",
            StremioUrls.resource(
                "https://v3-cinemeta.strem.io/manifest.json", "catalog", "series", "top",
                listOf("genre" to "Animation", "skip" to "20")
            )
        )
    }

    @Test
    fun searchQueriesAreEncodedAndSpacesNeverBecomePlus() {
        // URLEncoder would emit "the+matrix", which is a literal plus in a path.
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top/search=the%20matrix.json",
            StremioUrls.resource(
                "https://v3-cinemeta.strem.io/manifest.json", "catalog", "movie", "top",
                listOf("search" to "the matrix")
            )
        )
    }

    @Test
    fun nonAsciiQueriesEncodeAsUtf8() {
        assertEquals("%E9%AC%BC", StremioUrls.encodeSegment("鬼"))
    }

    @Test
    fun unreservedCharactersSurvive() {
        assertEquals("Sci-Fi", StremioUrls.encodeSegment("Sci-Fi"))
        assertEquals("a_b.c~d", StremioUrls.encodeSegment("a_b.c~d"))
    }

    @Test
    fun ampersandInsideAValueIsEncodedSoItCannotForgeAnExtra() {
        assertEquals(
            "search=a%26skip%3D99",
            StremioUrls.encodeExtras(listOf("search" to "a&skip=99"))
        )
    }
}
