package com.ivor.ivormusic.data.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsed against responses captured live from the real addons (August 2026),
 * not against hand-written JSON. A parser tested only on JSON written by the
 * same person who wrote the parser proves nothing about a protocol served by
 * machines nobody here controls.
 */
class TvModelsTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("tv/$name")) {
            "missing fixture tv/$name"
        }.bufferedReader().readText()

    private inline fun <reified T> parse(name: String): T =
        TvJson.instance.decodeFromString(fixture(name))

    // ---- manifests ----

    @Test
    fun cinemetaManifestParses() {
        val m: AddonManifest = parse("cinemeta_manifest.json")
        assertEquals("com.linvo.cinemeta", m.id)
        assertEquals("Cinemeta", m.name)
        assertTrue(m.provides("catalog"))
        assertTrue(m.provides("meta"))
        assertFalse(m.provides("stream"))
        assertEquals(listOf("movie", "series"), m.types)
        assertEquals(listOf("tt"), m.idPrefixes)
    }

    @Test
    fun stringAndObjectResourcesBothParse() {
        // Cinemeta sends bare strings; Torrentio and the local addon send objects.
        val cinemeta: AddonManifest = parse("cinemeta_manifest.json")
        assertEquals("catalog", cinemeta.resources.first().name)
        assertNull(cinemeta.resources.first().types)

        val objectForm = TvJson.instance.decodeFromString<AddonManifest>(
            """{"id":"x","name":"X","resources":[{"name":"stream","types":["movie"],
               "idPrefixes":["tt","kitsu"]}]}"""
        )
        val res = objectForm.resource("stream")
        assertNotNull(res)
        assertEquals(listOf("movie"), res!!.types)
        assertEquals(listOf("tt", "kitsu"), res.idPrefixes)
    }

    @Test
    fun idPrefixesGateTheFanOut() {
        val m = TvJson.instance.decodeFromString<AddonManifest>(
            """{"id":"x","name":"X","types":["movie","series"],
               "resources":[{"name":"stream","types":["movie","series"],"idPrefixes":["tt"]}]}"""
        )
        assertTrue(m.handles("stream", "movie", "tt1375666"))
        assertFalse(m.handles("stream", "movie", "kitsu:46474"))
        assertFalse(m.handles("stream", "channel", "tt1375666"))
        assertFalse(m.handles("meta", "movie", "tt1375666"))
    }

    @Test
    fun anAddonWithoutIdPrefixesIsAskedAboutEverything() {
        val m = TvJson.instance.decodeFromString<AddonManifest>(
            """{"id":"x","name":"X","types":["movie"],"resources":["stream"]}"""
        )
        assertTrue(m.handles("stream", "movie", "anything"))
        assertFalse(m.handles("stream", "series", "anything"))
    }

    // ---- catalogs ----

    @Test
    fun searchOnlyCatalogsAreNotBrowsable() {
        // Anime Kitsu's kitsu-anime-list marks search isRequired. Asking it for
        // a plain feed returns nothing useful, so it must never become a shelf.
        val kitsu: AddonManifest = parse("kitsu_manifest.json")
        val list = kitsu.catalogs.first { it.id == "kitsu-anime-list" }
        assertTrue(list.supportsSearch)
        assertFalse(list.isBrowsable)

        val trending = kitsu.catalogs.first { it.id == "kitsu-anime-trending" }
        assertTrue(trending.isBrowsable)
        assertFalse(trending.supportsSearch)
    }

    @Test
    fun cinemetaCatalogsAreBrowsableAndSearchable() {
        val m: AddonManifest = parse("cinemeta_manifest.json")
        val top = m.catalogs.first { it.id == "top" && it.type == "movie" }
        assertTrue(top.isBrowsable)
        assertTrue(top.supportsSearch)
        assertTrue(top.supportsSkip)
        assertTrue(top.genreOptions.contains("Action"))
        assertTrue(top.genreOptions.contains("Western"))
    }

    @Test
    fun catalogsRequiringAnExtraWeCannotSupplyAreNotBrowsable() {
        // Cinemeta's last-videos catalog requires lastVideosIds.
        val m: AddonManifest = parse("cinemeta_manifest.json")
        val lastVideos = m.catalogs.firstOrNull { it.id == "last-videos" }
        assertNotNull(lastVideos)
        assertFalse(lastVideos!!.isBrowsable)
    }

    // ---- catalog responses ----

    @Test
    fun catalogItemsCarryEverythingAHeroNeeds() {
        val r: TvCatalogResponse = parse("cinemeta_catalog_movie_top.json")
        assertEquals(3, r.metas.size)
        r.metas.forEach {
            assertTrue("id", it.id.isNotBlank())
            assertTrue("name", it.name.isNotBlank())
            assertTrue("poster", !it.poster.isNullOrBlank())
            assertTrue("background", !it.background.isNullOrBlank())
            assertTrue("logo", !it.logo.isNullOrBlank())
            assertTrue("description", !it.description.isNullOrBlank())
        }
    }

    @Test
    fun kitsuSearchCarriesTheImdbCrossMapUsedForDedupe() {
        val r: TvCatalogResponse = parse("kitsu_catalog_search.json")
        val frieren = r.metas.first()
        assertTrue(frieren.id.startsWith("kitsu:"))
        assertEquals("46474", frieren.kitsuId)
        assertEquals("tt22248376", frieren.imdbId)
    }

    // ---- meta ----

    @Test
    fun seriesMetaParsesEpisodes() {
        val r: TvMetaResponse = parse("cinemeta_meta_series.json")
        val meta = checkNotNull(r.meta)
        assertEquals("tt0903747", meta.id)
        assertEquals("series", meta.type)
        assertTrue(meta.hasEpisodes)

        val first = meta.episodesInSeason(1).first()
        assertEquals("tt0903747:1:1", first.id)
        assertEquals("Pilot", first.displayTitle)
        assertEquals(1, first.episodeNumber)
        assertTrue(first.summary!!.isNotBlank())
        assertTrue(first.thumbnail!!.startsWith("https://"))
        assertNotNull(first.airDate)
    }

    @Test
    fun episodeNumberFallsBackToCinemetaAlias() {
        // Cinemeta sends both `episode` and `number`; the spec only documents
        // `episode`, and some addons send only `number`.
        val onlyNumber = TvJson.instance.decodeFromString<TvEpisode>(
            """{"id":"x:1:4","name":"Four","season":1,"number":4}"""
        )
        assertEquals(4, onlyNumber.episodeNumber)
        assertEquals("Four", onlyNumber.displayTitle)
    }

    @Test
    fun movieMetaHasNoEpisodes() {
        val r: TvMetaResponse = parse("cinemeta_meta_movie.json")
        val meta = checkNotNull(r.meta)
        assertEquals("movie", meta.type)
        assertFalse(meta.hasEpisodes)
        assertTrue(meta.seasons.isEmpty())
    }

    @Test
    fun trailersExposeAYoutubeIdKodaCanPlayItself() {
        val r: TvMetaResponse = parse("cinemeta_meta_movie.json")
        val id = checkNotNull(r.meta).trailerYoutubeId
        assertNotNull("Cinemeta ships trailers for ~98% of items", id)
        assertTrue(id!!.isNotBlank())
    }

    // ---- robustness ----

    @Test
    fun numericRatingsAndYearsDoNotFailTheWholeResponse() {
        // Documented as strings, sent as numbers by several community addons.
        val item = TvJson.instance.decodeFromString<TvItem>(
            """{"id":"tt1","type":"movie","name":"N","imdbRating":7.9,"releaseInfo":2021}"""
        )
        assertEquals("7.9", item.imdbRating)
        assertEquals("2021", item.releaseInfo)
    }

    @Test
    fun unknownKeysAreIgnoredRatherThanFatal() {
        val item = TvJson.instance.decodeFromString<TvItem>(
            """{"id":"tt1","type":"movie","name":"N","somethingNewIn2027":{"a":[1,2]}}"""
        )
        assertEquals("tt1", item.id)
    }

    @Test
    fun aMetaResponseWithNoMetaIsNullRatherThanACrash() {
        val r = TvJson.instance.decodeFromString<TvMetaResponse>("""{}""")
        assertNull(r.meta)
    }

    @Test
    fun addonDirectoryParses() {
        val r: AddonCatalogResponse = parse("addon_catalog_community.json")
        assertTrue(r.addons.isNotEmpty())
        val kitsu = r.addons.first { it.manifest.name == "Anime Kitsu" }
        assertEquals("https://anime-kitsu.strem.fun/manifest.json", kitsu.transportUrl)
        assertTrue(kitsu.manifest.provides("catalog"))
    }
}
