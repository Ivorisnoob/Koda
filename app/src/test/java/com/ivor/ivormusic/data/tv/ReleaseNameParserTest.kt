package com.ivor.ivormusic.data.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release-name parser, against names in the shape addons actually send.
 *
 * Every input here is either a real Torrentio / Comet / MediaFusion title
 * captured from a live response or a minimal reduction of one. That matters:
 * this parser's failure mode is silent - a mis-read release still renders and
 * still plays, it just sorts to the wrong place forever - so invented inputs
 * would test the regex against itself.
 */
class ReleaseNameParserTest {

    // --- Resolution ---------------------------------------------------------

    @Test
    fun readsTheResolutionLabel() {
        assertEquals(2160, ReleaseNameParser.parse("Movie.2010.2160p.BluRay").resolution)
        assertEquals(1080, ReleaseNameParser.parse("Movie.2010.1080p.WEB-DL").resolution)
        assertEquals(720, ReleaseNameParser.parse("Movie 720p HDTV").resolution)
    }

    @Test
    fun fourKAndUhdBothMean2160() {
        assertEquals(2160, ReleaseNameParser.parse("Movie 4K UHD BluRay").resolution)
        assertEquals(2160, ReleaseNameParser.parse("Movie UHD Remux").resolution)
    }

    @Test
    fun aYearIsNotAResolution() {
        // 2010 has no p or i suffix, so it must not read as a 2010-line format.
        assertNull(ReleaseNameParser.parse("Movie.Title.2010.BluRay.x264-GROUP").resolution)
    }

    // --- Source quality -----------------------------------------------------

    @Test
    fun aBluRayRemuxIsARemuxNotABluRay() {
        // Order of the checks is the whole correctness of sourceQuality().
        assertEquals(
            SourceQuality.REMUX,
            ReleaseNameParser.parse("Movie.2160p.UHD.BluRay.REMUX.DV.HDR10").sourceQuality
        )
    }

    @Test
    fun webRipIsNotWebDl() {
        assertEquals(SourceQuality.WEBRIP, ReleaseNameParser.parse("Show.S01E01.WEBRip").sourceQuality)
        assertEquals(SourceQuality.WEB_DL, ReleaseNameParser.parse("Show.S01E01.WEB-DL").sourceQuality)
    }

    @Test
    fun bareWebCountsAsWebDl() {
        assertEquals(SourceQuality.WEB_DL, ReleaseNameParser.parse("Show.S01E01.1080p.WEB.h264").sourceQuality)
    }

    @Test
    fun camReleasesAreRecognisedSoTheySortLast() {
        assertEquals(SourceQuality.CAM, ReleaseNameParser.parse("Movie.2024.HDCAM.c1nem4").sourceQuality)
        assertEquals(SourceQuality.CAM, ReleaseNameParser.parse("Movie.2024.HDTS.1080p").sourceQuality)
        assertTrue(SourceQuality.CAM.rank < SourceQuality.WEBRIP.rank)
    }

    // --- Codec and HDR ------------------------------------------------------

    @Test
    fun readsCodecs() {
        assertEquals("HEVC", ReleaseNameParser.parse("Movie.2160p.x265.10bit").codec)
        assertEquals("H.264", ReleaseNameParser.parse("Movie.1080p.x264").codec)
        assertEquals("AV1", ReleaseNameParser.parse("Movie.1080p.AV1").codec)
    }

    @Test
    fun dolbyVisionAndHdr10CoexistOnOneFile() {
        // A DV Profile 8 file carries an HDR10 base layer, so both are true and
        // picking one would misdescribe the file.
        val tags = ReleaseNameParser.parse("Movie.2160p.BluRay.REMUX.DV.HDR10.TrueHD.7.1")
        assertTrue(tags.hdr.contains(HdrFlag.DV))
        assertTrue(tags.hdr.contains(HdrFlag.HDR10))
        assertTrue(tags.isHdr)
    }

    @Test
    fun aPlainSdrReleaseHasNoHdrFlags() {
        assertFalse(ReleaseNameParser.parse("Movie.1080p.WEB-DL.x264-GROUP").isHdr)
    }

    // --- Audio --------------------------------------------------------------

    @Test
    fun atmosOutranksTheTrueHdItRidesOn() {
        assertEquals("Atmos", ReleaseNameParser.parse("Movie.2160p.TrueHD.7.1.Atmos").audioFormat)
    }

    @Test
    fun dtsHdIsNotPlainDts() {
        assertEquals("DTS-HD", ReleaseNameParser.parse("Movie.1080p.DTS-HD.MA.5.1").audioFormat)
        assertEquals("DTS", ReleaseNameParser.parse("Movie.1080p.DTS.5.1").audioFormat)
    }

    @Test
    fun readsChannelLayout() {
        assertEquals("7.1", ReleaseNameParser.parse("Movie.2160p.TrueHD.7.1").audioChannels)
        assertEquals("5.1", ReleaseNameParser.parse("Movie.1080p.DDP5.1").audioChannels)
        assertEquals("2.0", ReleaseNameParser.parse("Movie.1080p.AAC2.0").audioChannels)
    }

    // --- Languages ----------------------------------------------------------

    @Test
    fun decodesFlagEmojiIntoLanguages() {
        // Torrentio puts flags in the title and no structured language field
        // exists anywhere in the protocol, so this is the exact signal.
        val tags = ReleaseNameParser.parse("Show S01E01 1080p\n🇬🇧 🇯🇵")
        assertTrue(tags.languages.contains("en"))
        assertTrue(tags.languages.contains("ja"))
    }

    @Test
    fun readsLanguageWordsAsWell() {
        val tags = ReleaseNameParser.parse("Movie.2010.1080p.Japanese.English.Dual.Audio")
        assertTrue(tags.languages.contains("ja"))
        assertTrue(tags.languages.contains("en"))
    }

    @Test
    fun aFlagAndAWordAgreeingProduceOneLanguage() {
        val tags = ReleaseNameParser.parse("Movie English 🇺🇸")
        assertEquals(setOf("en"), tags.languages)
    }

    // --- Sub and dub --------------------------------------------------------

    @Test
    fun aDualAudioReleaseServesBothSubAndDubViewers() {
        // This is why the two flags are separate rather than one tri-state:
        // the file carries both tracks, so it satisfies either request.
        val tags = ReleaseNameParser.parse("[SubsPlease] Show - 01 (1080p) [Dual Audio]")
        assertTrue(tags.isDualAudio)
        assertTrue(tags.offersDub)
        assertTrue(tags.offersSub)
    }

    @Test
    fun aDubOnlyReleaseCannotServeASubViewer() {
        val tags = ReleaseNameParser.parse("Show.S01E01.1080p.English.Dubbed.WEB-DL")
        assertTrue(tags.isDubbed)
        assertTrue(tags.offersDub)
        assertFalse(tags.offersSub)
    }

    @Test
    fun anOrdinarySubbedReleaseOffersNoDub() {
        val tags = ReleaseNameParser.parse("[Erai-raws] Show - 01 [1080p][Multiple Subtitle]")
        assertFalse(tags.offersDub)
        assertTrue(tags.offersSub)
    }

    // --- Provenance ---------------------------------------------------------

    @Test
    fun readsATrailingSceneGroup() {
        assertEquals(
            "FLUX",
            ReleaseNameParser.parse("Movie.Title.2010.1080p.WEB-DL.DDP5.1.H.264-FLUX").releaseGroup
        )
    }

    @Test
    fun readsALeadingFansubGroup() {
        assertEquals(
            "SubsPlease",
            ReleaseNameParser.parse("[SubsPlease] Show - 01 (1080p) [ABCD1234].mkv").releaseGroup
        )
    }

    @Test
    fun aBracketedResolutionIsNotAGroupName() {
        // "[1080p] Title" must not report a release group called 1080p.
        assertEquals(null, ReleaseNameParser.parse("[1080p] Some Movie Title").releaseGroup)
    }

    @Test
    fun aDebridMarkerIsNotAReleaseGroup() {
        // "[RD+] Torrentio" leads every debrid-aware addon's stream text.
        assertEquals(
            "FLUX",
            ReleaseNameParser.parse("[RD+] Torrentio\n1080p\nMovie.2010.WEB-DL-FLUX").releaseGroup
        )
    }

    @Test
    fun anEpisodeCodeNextToAResolutionIsNotAChannelLayout() {
        // "S01E01 1080p" flattens to digits either side of a space, which is
        // exactly the shape a 5.1 has. It must not read as one.
        assertNull(ReleaseNameParser.parse("Show.S01E01.1080p.WEB-DL.x264").audioChannels)
    }

    @Test
    fun readsSeedersFromTheTorrentioLine() {
        assertEquals(284, ReleaseNameParser.parse("Movie.1080p\n👤 284 💾 2.1 GB").seeders)
        assertEquals(42, ReleaseNameParser.parse("Movie 1080p\nSeeders: 42").seeders)
    }

    @Test
    fun readsHumanReadableSizeIntoBytes() {
        val gib = 1024L * 1024 * 1024
        assertEquals(gib * 2, ReleaseNameParser.parse("Movie\n💾 2 GB").sizeBytes)
        assertEquals(
            (1.5 * gib).toLong(),
            ReleaseNameParser.parse("Movie\n💾 1.5 GB").sizeBytes
        )
    }

    @Test
    fun anExplicitVideoSizeBeatsTheParsedOne() {
        // behaviorHints.videoSize is the addon stating a fact; the title is prose.
        assertEquals(
            99L,
            ReleaseNameParser.parse("Movie\n💾 2 GB", videoSize = 99L).sizeBytes
        )
    }

    // --- Cache state --------------------------------------------------------

    @Test
    fun readsTheDebridCachedMarker() {
        assertEquals(
            CacheState.CACHED,
            ReleaseNameParser.parse("[RD+] Torrentio\nMovie.1080p.WEB-DL").cacheState
        )
    }

    @Test
    fun readsTheDebridDownloadMarker() {
        assertEquals(
            CacheState.NOT_CACHED,
            ReleaseNameParser.parse("[RD download] Torrentio\nMovie.1080p").cacheState
        )
    }

    @Test
    fun anAddonThatSaysNothingAboutCachingIsUnknownNotUncached() {
        // Reporting NOT_CACHED here would rank every direct-HTTP source below
        // every torrent, which is backwards.
        assertEquals(CacheState.UNKNOWN, ReleaseNameParser.parse("Movie.1080p.WEB-DL").cacheState)
    }

    // --- Whole names --------------------------------------------------------

    @Test
    fun parsesARealTorrentioRemuxRow() {
        val tags = ReleaseNameParser.parse(
            "Torrentio\n4k REMUX\n" +
                "Movie.Title.2010.2160p.UHD.BluRay.REMUX.DV.HDR10.TrueHD.7.1.Atmos-FraMeSToR\n" +
                "👤 34 💾 38.4 GB ⚙️ ThePirateBay"
        )
        assertEquals(2160, tags.resolution)
        assertEquals(SourceQuality.REMUX, tags.sourceQuality)
        assertEquals("Atmos", tags.audioFormat)
        assertEquals("7.1", tags.audioChannels)
        assertTrue(tags.hdr.contains(HdrFlag.DV))
        assertEquals(34, tags.seeders)
        assertEquals("FraMeSToR", tags.releaseGroup)
    }

    @Test
    fun anEmptyNameParsesToNothingRatherThanThrowing() {
        val tags = ReleaseNameParser.parse("")
        assertNull(tags.resolution)
        assertNull(tags.sourceQuality)
        assertTrue(tags.languages.isEmpty())
    }

    @Test
    fun resolutionLabelReadsTheWayViewersWriteIt() {
        assertEquals("4K", StreamTags(resolution = 2160).resolutionLabel)
        assertEquals("1080p", StreamTags(resolution = 1080).resolutionLabel)
        assertNull(StreamTags().resolutionLabel)
    }
}
