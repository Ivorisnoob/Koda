package com.ivor.ivormusic.data.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source ranking, dedupe and the auto-pick.
 *
 * This is the function that decides what plays when nobody chooses, and a bad
 * weight in it is invisible: the video still plays, it is simply the wrong one,
 * every time. Each test below states the trade it is pinning down rather than
 * asserting a number.
 */
class TvSourceRankingTest {

    private fun source(
        name: String,
        // Derived from the name so two fixtures never share an id by accident -
        // TvSource.id falls back to the URL, and identical URLs would make
        // unrelated rows dedupe into one.
        url: String? = "https://host/" + name.hashCode() + ".mkv",
        infoHash: String? = null,
        fileIdx: Int? = null,
        addonId: String = "addon.one",
        videoSize: Long? = null,
        bingeGroup: String? = null,
    ): TvSource {
        val stream = TvStream(
            url = url,
            infoHash = infoHash,
            fileIdx = fileIdx,
            title = name,
            behaviorHints = StreamBehaviorHints(videoSize = videoSize, bingeGroup = bingeGroup),
        )
        return TvSource(
            addonId = addonId,
            addonName = "Addon",
            stream = stream,
            tags = ReleaseNameParser.parse(stream.text, videoSize),
        )
    }

    private val noCap = TvAutoSelectProfile()
    private val cap1080 = TvAutoSelectProfile(maxResolution = 1080)

    // --- Ranking ------------------------------------------------------------

    @Test
    fun sharperWinsWhenNothingElseSeparatesThem() {
        val hd = source("Movie.1080p.WEB-DL")
        val uhd = source("Movie.2160p.WEB-DL")
        assertTrue(
            TvStreamRepository.score(uhd, noCap) > TvStreamRepository.score(hd, noCap)
        )
    }

    @Test
    fun aCached720pBeatsAnUncached4K() {
        // On a debrid setup the alternative to cached is not a worse picture,
        // it is a wait. That is why the cached bonus is larger than the whole
        // resolution range.
        val cached = source("[RD+] Movie.720p.WEB-DL")
        val uncached = source("[RD download] Movie.2160p.BluRay.REMUX")
        assertTrue(
            TvStreamRepository.score(cached, noCap) > TvStreamRepository.score(uncached, noCap)
        )
    }

    @Test
    fun anAddonThatSaysNothingAboutCachingIsNotPunished() {
        // UNKNOWN must sit between CACHED and NOT_CACHED, or every direct HTTP
        // source ranks below every torrent.
        val unknown = source("Movie.1080p.WEB-DL")
        val notCached = source("[RD download] Movie.1080p.WEB-DL")
        assertTrue(
            TvStreamRepository.score(unknown, noCap) > TvStreamRepository.score(notCached, noCap)
        )
    }

    @Test
    fun theCapPushes4KBelow1080pWithoutRemovingIt() {
        val uhd = source("Movie.2160p.WEB-DL")
        val hd = source("Movie.1080p.WEB-DL")
        assertTrue(TvStreamRepository.score(hd, cap1080) > TvStreamRepository.score(uhd, cap1080))
        // Still a candidate: when it is the only thing there, it must play.
        assertNotNull(TvStreamRepository.autoPick(listOf(uhd), cap1080))
    }

    @Test
    fun hdrIsDeprioritisedWhileTheToggleIsOff() {
        // CLAUDE.md states HDR is intentionally unsupported, and a DV Profile 5
        // file on a device that cannot decode it plays green and purple rather
        // than failing. De-prioritised, not hidden.
        val hdr = source("Movie.1080p.WEB-DL.DV.HDR10")
        val sdr = source("Movie.1080p.WEB-DL")
        assertTrue(TvStreamRepository.score(sdr, noCap) > TvStreamRepository.score(hdr, noCap))

        val allowed = TvAutoSelectProfile(allowHdr = true)
        assertTrue(
            TvStreamRepository.score(hdr, allowed) >= TvStreamRepository.score(sdr, allowed)
        )
    }

    @Test
    fun aRemuxOutranksAWebRipAtTheSameResolution() {
        val remux = source("Movie.1080p.BluRay.REMUX")
        val webrip = source("Movie.1080p.WEBRip")
        assertTrue(
            TvStreamRepository.score(remux, noCap) > TvStreamRepository.score(webrip, noCap)
        )
    }

    @Test
    fun aPreferredLanguageOutweighsOneStepOfSourceQuality() {
        val profile = TvAutoSelectProfile(preferredLanguages = listOf("ja"))
        val japanese = source("Show.1080p.WEBRip.Japanese")
        val english = source("Show.1080p.WEB-DL.English")
        assertTrue(
            TvStreamRepository.score(japanese, profile) >
                TvStreamRepository.score(english, profile)
        )
    }

    @Test
    fun aSecondChoiceLanguageStillBeatsNone() {
        val profile = TvAutoSelectProfile(preferredLanguages = listOf("ja", "en"))
        val english = source("Show.1080p.WEB-DL.English")
        val neither = source("Show.1080p.WEB-DL.Polski")
        assertTrue(
            TvStreamRepository.score(english, profile) > TvStreamRepository.score(neither, profile)
        )
    }

    @Test
    fun theDubPreferenceIsSatisfiedByADualAudioRelease() {
        val wantDub = TvAutoSelectProfile(dubPreference = DubPreference.DUB)
        val dual = source("[Group] Show - 01 (1080p) [Dual Audio]")
        val subOnly = source("[Group] Show - 01 (1080p) [Subbed]")
        assertTrue(
            TvStreamRepository.score(dual, wantDub) > TvStreamRepository.score(subOnly, wantDub)
        )
    }

    @Test
    fun aFileOverTheSizeCapIsPenalisedNotDropped() {
        val gib = 1024L * 1024 * 1024
        val profile = TvAutoSelectProfile(maxSizeBytes = 4 * gib)
        val huge = source("Movie.1080p.WEB-DL", videoSize = 40 * gib)
        val small = source("Movie.1080p.WEB-DL", videoSize = 2 * gib)
        assertTrue(TvStreamRepository.score(small, profile) > TvStreamRepository.score(huge, profile))
        assertNotNull(TvStreamRepository.autoPick(listOf(huge), profile))
    }

    // --- List order ---------------------------------------------------------

    @Test
    fun unplayableRowsSortLastButAreKept() {
        // Hiding torrent-only rows makes a working addon look broken.
        val torrent = source("Movie.2160p.BluRay.REMUX", url = null, infoHash = "abc123")
        val playable = source("Movie.480p.WEBRip")
        val ordered = TvStreamRepository.ranked(listOf(torrent, playable), noCap)
        assertEquals(2, ordered.size)
        assertEquals(playable.id, ordered.first().id)
        assertFalse(ordered.last().isPlayable)
    }

    @Test
    fun autoPickReturnsNothingWhenEveryRowIsATorrent() {
        // The honest and common state for an unconfigured torrent addon.
        val torrents = listOf(
            source("A.1080p", url = null, infoHash = "aaa"),
            source("B.2160p", url = null, infoHash = "bbb"),
        )
        assertNull(TvStreamRepository.autoPick(torrents, noCap))
    }

    @Test
    fun autoPickSaysWhyItChose() {
        val only = listOf(source("Movie.1080p.WEB-DL"))
        assertEquals(PickReason.ONLY_PLAYABLE, TvStreamRepository.autoPick(only, noCap)?.reason)

        val cached = listOf(
            source("[RD+] Movie.1080p.WEB-DL"),
            source("Movie.720p.WEBRip"),
        )
        assertEquals(PickReason.CACHED, TvStreamRepository.autoPick(cached, noCap)?.reason)

        val overCap = listOf(
            source("Movie.2160p.WEB-DL"),
            source("Movie.1080p.WEB-DL"),
        )
        assertEquals(
            PickReason.WITHIN_LIMIT,
            TvStreamRepository.autoPick(overCap, cap1080)?.reason
        )
    }

    // --- Dedupe -------------------------------------------------------------

    @Test
    fun theSameTorrentFromTwoAddonsCollapses() {
        val a = source("Movie.1080p", url = null, infoHash = "ABC123", addonId = "one")
        val b = source("Movie.1080p", url = null, infoHash = "abc123", addonId = "two")
        assertEquals(1, TvStreamRepository.dedupe(listOf(a, b)).size)
    }

    @Test
    fun theResolvedCopyWinsOverTheBareInfoHash() {
        // The common shape: one addon returns a torrent, another returns the
        // same release already resolved through debrid. Keeping the torrent
        // hides the one that actually plays.
        val torrent = source("Movie.1080p", url = null, infoHash = "abc123")
        val resolved = source("Movie.1080p", url = "https://dl/f.mkv", infoHash = "abc123")
        val out = TvStreamRepository.dedupe(listOf(torrent, resolved))
        assertEquals(1, out.size)
        assertTrue(out.first().isPlayable)
    }

    @Test
    fun twoEpisodesInOneSeasonPackAreNotTheSameFile() {
        // They share a hash and differ only by fileIdx.
        val first = source("Show S01E01", url = null, infoHash = "pack", fileIdx = 0)
        val second = source("Show S01E02", url = null, infoHash = "pack", fileIdx = 1)
        assertEquals(2, TvStreamRepository.dedupe(listOf(first, second)).size)
    }

    // --- Facets and filtering -----------------------------------------------

    @Test
    fun facetsOnlyOfferWhatThisResultSetHolds() {
        val sources = listOf(
            source("Movie.1080p.WEB-DL.English"),
            source("Movie.720p.WEBRip.English"),
        )
        val facets = TvStreamRepository.facets(sources)
        assertEquals(listOf(1080, 720), facets.resolutions)
        assertEquals(listOf("en"), facets.languages)
        assertFalse(facets.hasHdr)
        assertFalse(facets.hasCached)
    }

    @Test
    fun everyOfferedChipMatchesAtLeastOneRow() {
        // A chip that filters to zero results is a broken control, and the only
        // guard against it is deriving the chips from the same set.
        val sources = listOf(
            source("Movie.2160p.BluRay.REMUX.DV.HDR10.English"),
            source("[RD+] Movie.1080p.WEB-DL.Japanese.Dual.Audio"),
            source("Movie.720p.HDTV", url = null, infoHash = "x"),
        )
        val facets = TvStreamRepository.facets(sources)
        for (resolution in facets.resolutions) {
            assertTrue(
                "resolution chip " + resolution + " matched nothing",
                TvStreamRepository.filter(sources, TvSourceFilter(resolution = resolution))
                    .isNotEmpty()
            )
        }
        for (language in facets.languages) {
            assertTrue(
                "language chip " + language + " matched nothing",
                TvStreamRepository.filter(sources, TvSourceFilter(language = language)).isNotEmpty()
            )
        }
        for (quality in facets.sourceQualities) {
            assertTrue(
                "quality chip " + quality + " matched nothing",
                TvStreamRepository.filter(sources, TvSourceFilter(sourceQuality = quality))
                    .isNotEmpty()
            )
        }
        if (facets.hasCached) {
            assertTrue(
                TvStreamRepository.filter(sources, TvSourceFilter(cachedOnly = true)).isNotEmpty()
            )
        }
    }

    @Test
    fun theSubFilterKeepsDualAudioAndDropsDubOnly() {
        val dual = source("[Group] Show 01 [Dual Audio]")
        val dubbed = source("Show.S01E01.English.Dubbed")
        val plain = source("Show.S01E01.1080p.WEB-DL")
        val out = TvStreamRepository.filter(
            listOf(dual, dubbed, plain), TvSourceFilter(dub = DubPreference.SUB)
        )
        assertTrue(out.any { it.id == dual.id })
        assertTrue(out.any { it.id == plain.id })
        assertFalse(out.any { it.id == dubbed.id })
    }

    // --- Identity -----------------------------------------------------------

    @Test
    fun bingeGroupIsReadOffTheStreamAndBlanksAreNull() {
        assertEquals(
            "torrentio|1080p|web-dl",
            source("Movie", bingeGroup = "torrentio|1080p|web-dl").bingeGroup
        )
        assertNull(source("Movie", bingeGroup = "").bingeGroup)
        assertNull(source("Movie").bingeGroup)
    }
}
