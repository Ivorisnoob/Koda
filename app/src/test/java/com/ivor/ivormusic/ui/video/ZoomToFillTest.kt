package com.ivor.ivormusic.ui.video

import org.junit.Assert.*
import org.junit.Test

class ZoomToFillTest {
    private val tallPhoneLandscape = 19.5f / 9f
    private val tallPhonePortrait = 9f / 19.5f

    @Test fun identicalAspectsCropNothing() {
        assertEquals(0f, zoomFillCropFraction(16f / 9f, 16f / 9f), 0.0001f)
    }

    @Test fun fractionIsSymmetric() {
        val a = zoomFillCropFraction(16f / 9f, tallPhoneLandscape)
        val b = zoomFillCropFraction(tallPhoneLandscape, 16f / 9f)
        assertEquals(a, b, 0.0001f)
    }

    /** The feature's target case: a slim band off the top and bottom. */
    @Test fun landscapeVideoOnTallPhoneIsAvailable() {
        val crop = zoomFillCropFraction(16f / 9f, tallPhoneLandscape)
        assertTrue("expected ~0.18, got $crop", crop in 0.15f..0.21f)
        assertTrue(isZoomToFillAvailable(16f / 9f, tallPhoneLandscape))
    }

    /** A 9:16 source in a landscape window would lose most of the picture. */
    @Test fun verticalVideoInLandscapeWindowIsBlocked() {
        val crop = zoomFillCropFraction(9f / 16f, tallPhoneLandscape)
        assertTrue("expected ~0.74, got $crop", crop > MAX_ACCEPTABLE_CROP)
        assertFalse(isZoomToFillAvailable(9f / 16f, tallPhoneLandscape))
    }

    /** "Vertical" also covers 4:5 and 1:1, which would lose top and bottom. */
    @Test fun squarishVideoInLandscapeWindowIsBlocked() {
        assertFalse(isZoomToFillAvailable(4f / 5f, tallPhoneLandscape))
        assertFalse(isZoomToFillAvailable(1f, tallPhoneLandscape))
    }

    /** A vertical video fills a phone held upright, so zoom stays available. */
    @Test fun verticalVideoInPortraitWindowIsAvailable() {
        assertTrue(isZoomToFillAvailable(9f / 16f, tallPhonePortrait))
    }

    @Test fun nonPositiveAspectsAreRejected() {
        try {
            zoomFillCropFraction(0f, tallPhoneLandscape)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
        try {
            zoomFillCropFraction(16f / 9f, -1f)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected.
        }
    }
}
