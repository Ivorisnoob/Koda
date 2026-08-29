package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockTest {

    /* ---------------- preference encoding ---------------- */

    @Test
    fun nothingStoredGivesTheDocumentedDefaults() {
        val actions = decodeSegmentActions(null)
        assertEquals(SegmentAction.SKIP, actions[SponsorCategory.SPONSOR])
        assertEquals(SegmentAction.SKIP, actions[SponsorCategory.SELF_PROMO])
        assertEquals(SegmentAction.SKIP, actions[SponsorCategory.INTERACTION])
        assertEquals(SegmentAction.IGNORE, actions[SponsorCategory.INTRO])
        assertEquals(SegmentAction.IGNORE, actions[SponsorCategory.FILLER])
    }

    @Test
    fun everyCategorySurvivesAnEncodeDecodeRoundTrip() {
        val original = SponsorCategory.entries.associateWith { SegmentAction.MANUAL }
        assertEquals(original, decodeSegmentActions(encodeSegmentActions(original)))
    }

    /**
     * The format has to tolerate a build that knew more or fewer categories,
     * or a hand-edited backup, without losing the rest of the map.
     */
    @Test
    fun unknownAndMalformedEntriesAreDroppedNotFatal() {
        val stored = "sponsor:MANUAL;notacategory:SKIP;intro:NOTANACTION;garbage;outro:SKIP"
        val actions = decodeSegmentActions(stored)
        assertEquals(SegmentAction.MANUAL, actions[SponsorCategory.SPONSOR])
        assertEquals(SegmentAction.SKIP, actions[SponsorCategory.OUTRO])
        // Unparseable action falls back to the category default rather than
        // silently disabling it.
        assertEquals(SegmentAction.IGNORE, actions[SponsorCategory.INTRO])
        assertEquals(SponsorCategory.entries.size, actions.size)
    }

    @Test
    fun blankStoredValueIsTreatedAsUnset() {
        assertEquals(decodeSegmentActions(null), decodeSegmentActions(""))
        assertEquals(decodeSegmentActions(null), decodeSegmentActions("   "))
    }

    @Test
    fun onlyNonIgnoredCategoriesAreRequested() {
        val actions = mapOf(
            SponsorCategory.SPONSOR to SegmentAction.SKIP,
            SponsorCategory.INTRO to SegmentAction.MANUAL,
            SponsorCategory.OUTRO to SegmentAction.IGNORE
        )
        val active = activeCategories(actions)
        assertTrue(SponsorCategory.SPONSOR in active)
        assertTrue(SponsorCategory.INTRO in active)
        assertTrue(SponsorCategory.OUTRO !in active)
    }

    /* ---------------- segment selection ---------------- */

    private fun seg(uuid: String, cat: SponsorCategory, start: Long, end: Long) =
        SponsorSegment(uuid, cat, start, end)

    @Test
    fun aSegmentIsHalfOpenSoBackToBackSegmentsDoNotOverlap() {
        val s = seg("a", SponsorCategory.SPONSOR, 1000, 2000)
        assertTrue(s.contains(1000))
        assertTrue(s.contains(1999))
        // The end belongs to whatever comes next, which is what stops a skip
        // landing exactly on a boundary from re-triggering.
        assertTrue(!s.contains(2000))
    }

    @Test
    fun overlappingSegmentsResolveToTheOneEndingLatest() {
        val outer = seg("outer", SponsorCategory.SPONSOR, 1000, 9000)
        val inner = seg("inner", SponsorCategory.SELF_PROMO, 1000, 3000)
        // Skipping to the nearer end would drop the viewer back inside the
        // outer segment and skip a second time, which reads as a stutter.
        assertEquals(outer, segmentAt(listOf(inner, outer), 1500))
    }

    @Test
    fun ignoredSegmentsAreNotReturned() {
        val s = seg("a", SponsorCategory.SPONSOR, 1000, 2000)
        assertEquals(s, segmentAt(listOf(s), 1500))
        assertNull(segmentAt(listOf(s), 1500, ignored = setOf("a")))
    }

    @Test
    fun positionOutsideEverySegmentMatchesNothing() {
        val s = seg("a", SponsorCategory.SPONSOR, 1000, 2000)
        assertNull(segmentAt(listOf(s), 500))
        assertNull(segmentAt(listOf(s), 5000))
        assertNull(segmentAt(emptyList(), 1500))
    }

    /* ---------------- response parsing ---------------- */

    @Test
    fun onlyTheRequestedVideoIsTakenFromAPrefixResponse() {
        val body = """
            [
              {"videoID":"other1","segments":[
                {"UUID":"x","category":"sponsor","actionType":"skip","segment":[1.0,2.0]}]},
              {"videoID":"wanted","segments":[
                {"UUID":"y","category":"sponsor","actionType":"skip","segment":[10.0,20.0]}]}
            ]
        """.trimIndent()
        val segments = SponsorBlockRepository.parseSegments(body, "wanted")
        assertEquals(1, segments.size)
        assertEquals("y", segments[0].uuid)
        assertEquals(10_000L, segments[0].startMs)
        assertEquals(20_000L, segments[0].endMs)
    }

    /**
     * `full` would skip to the end of the video and `poi` is a point rather
     * than a range, so acting on either would be actively wrong.
     */
    @Test
    fun nonSkipActionTypesAreDropped() {
        val body = """
            [{"videoID":"v","segments":[
              {"UUID":"a","category":"sponsor","actionType":"skip","segment":[1.0,2.0]},
              {"UUID":"b","category":"sponsor","actionType":"mute","segment":[3.0,4.0]},
              {"UUID":"c","category":"sponsor","actionType":"full","segment":[0.0,999.0]},
              {"UUID":"d","category":"sponsor","actionType":"poi","segment":[5.0,5.0]}]}]
        """.trimIndent()
        val segments = SponsorBlockRepository.parseSegments(body, "v")
        assertEquals(listOf("a"), segments.map { it.uuid })
    }

    @Test
    fun zeroLengthAndInvertedRangesAreDropped() {
        val body = """
            [{"videoID":"v","segments":[
              {"UUID":"a","category":"sponsor","actionType":"skip","segment":[5.0,5.0]},
              {"UUID":"b","category":"sponsor","actionType":"skip","segment":[9.0,4.0]},
              {"UUID":"c","category":"sponsor","actionType":"skip","segment":[1.0,2.0]}]}]
        """.trimIndent()
        val segments = SponsorBlockRepository.parseSegments(body, "v")
        assertEquals(listOf("c"), segments.map { it.uuid })
    }

    @Test
    fun unknownCategoriesAreDroppedRatherThanCrashing() {
        val body = """
            [{"videoID":"v","segments":[
              {"UUID":"a","category":"brand_new_category","actionType":"skip","segment":[1.0,2.0]},
              {"UUID":"b","category":"outro","actionType":"skip","segment":[3.0,4.0]}]}]
        """.trimIndent()
        val segments = SponsorBlockRepository.parseSegments(body, "v")
        assertEquals(listOf("b"), segments.map { it.uuid })
    }

    @Test
    fun segmentsComeBackInPlaybackOrder() {
        val body = """
            [{"videoID":"v","segments":[
              {"UUID":"late","category":"sponsor","actionType":"skip","segment":[30.0,40.0]},
              {"UUID":"early","category":"sponsor","actionType":"skip","segment":[1.0,2.0]}]}]
        """.trimIndent()
        val segments = SponsorBlockRepository.parseSegments(body, "v")
        assertEquals(listOf("early", "late"), segments.map { it.uuid })
    }

    @Test
    fun rubbishPayloadsYieldNoSegmentsRatherThanThrowing() {
        assertEquals(emptyList<SponsorSegment>(), SponsorBlockRepository.parseSegments("", "v"))
        assertEquals(
            emptyList<SponsorSegment>(),
            SponsorBlockRepository.parseSegments("not json at all", "v")
        )
        assertEquals(
            emptyList<SponsorSegment>(),
            SponsorBlockRepository.parseSegments("""{"unexpected":"object"}""", "v")
        )
    }

    /* ---------------- privacy ---------------- */

    /**
     * The whole privacy claim rests on this being short and on the video id
     * never leaving the device, so it is worth a test rather than a comment.
     */
    @Test
    fun onlyAShortHashPrefixIdentifiesTheRequest() {
        val prefix = SponsorBlockRepository.hashPrefix("dQw4w9WgXcQ")
        assertEquals(4, prefix.length)
        assertTrue(prefix.all { it in "0123456789abcdef" })
        // Deterministic, or the cache and the request would disagree.
        assertEquals(prefix, SponsorBlockRepository.hashPrefix("dQw4w9WgXcQ"))
        // And it is genuinely derived from the id, not a constant.
        assertTrue(prefix != SponsorBlockRepository.hashPrefix("someOtherId"))
    }
}
