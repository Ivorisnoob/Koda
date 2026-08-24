package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSeekPreviewTest {

    @Test
    fun usabilityRequiresEveryMappingDimension() {
        val valid = preview()

        assertTrue(valid.isUsable)
        assertFalse(valid.copy(pageUrls = emptyList()).isUsable)
        assertFalse(valid.copy(frameWidthPx = 0).isUsable)
        assertFalse(valid.copy(frameHeightPx = 0).isUsable)
        assertFalse(valid.copy(framesPerPageX = 0).isUsable)
        assertFalse(valid.copy(framesPerPageY = 0).isUsable)
        assertFalse(valid.copy(totalFrameCount = 0).isUsable)
        assertFalse(valid.copy(durationPerFrameMs = 0).isUsable)
    }

    @Test
    fun negativePositionClampsToFirstFrame() {
        assertFrame(preview().frameAt(-10_000L), page = 0, column = 0, row = 0)
    }

    @Test
    fun positionMapsAcrossRowsAndSpritePages() {
        val preview = preview()

        // Four columns by three rows: frame 11 is the final cell on page 0.
        assertFrame(preview.frameAt(11_999L), page = 0, column = 3, row = 2)
        // The next millisecond bucket begins page 1 at its top-left cell.
        assertFrame(preview.frameAt(12_000L), page = 1, column = 0, row = 0)
        assertFrame(preview.frameAt(17_000L), page = 1, column = 1, row = 1)
    }

    @Test
    fun exactFrameBoundarySelectsTheNewFrame() {
        val preview = preview()

        assertFrame(preview.frameAt(999L), page = 0, column = 0, row = 0)
        assertFrame(preview.frameAt(1_000L), page = 0, column = 1, row = 0)
    }

    @Test
    fun positionsPastDurationClampToLastDeclaredFrame() {
        val preview = preview(totalFrameCount = 18)

        assertFrame(preview.frameAt(Long.MAX_VALUE), page = 1, column = 1, row = 1)
    }

    @Test
    fun incompleteLastPageNeverAddressesAnUndeclaredCell() {
        val preview = preview(totalFrameCount = 14)

        assertFrame(preview.frameAt(13_000L), page = 1, column = 1, row = 0)
        assertFrame(preview.frameAt(99_000L), page = 1, column = 1, row = 0)
    }

    @Test
    fun missingSpritePageReturnsNullInsteadOfUsingTheWrongPage() {
        val preview = preview(pageUrls = listOf("page-0"), totalFrameCount = 18)

        assertNull(preview.frameAt(12_000L))
    }

    @Test
    fun invalidPreviewNeverProducesAFrame() {
        assertNull(preview(durationPerFrameMs = 0).frameAt(0L))
    }

    @Test
    fun localVideoIsUsableWithoutStoryboardDimensions() {
        val preview = VideoSeekPreview.local("content://media/video/42")

        assertTrue(preview.isUsable)
        assertTrue(preview.isLocal)
        assertEquals("content://media/video/42", preview.localVideoUri)
        assertNull(preview.frameAt(12_000L))
    }

    private fun preview(
        pageUrls: List<String> = listOf("page-0", "page-1"),
        totalFrameCount: Int = 18,
        durationPerFrameMs: Int = 1_000,
    ) = VideoSeekPreview(
        pageUrls = pageUrls,
        frameWidthPx = 160,
        frameHeightPx = 90,
        framesPerPageX = 4,
        framesPerPageY = 3,
        totalFrameCount = totalFrameCount,
        durationPerFrameMs = durationPerFrameMs,
    )

    private fun assertFrame(
        actual: VideoSeekPreviewFrame?,
        page: Int,
        column: Int,
        row: Int,
    ) {
        requireNotNull(actual)
        assertEquals("page-$page", actual.pageUrl)
        assertEquals(column, actual.column)
        assertEquals(row, actual.row)
    }
}
