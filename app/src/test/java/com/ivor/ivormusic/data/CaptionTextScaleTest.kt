package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionTextScaleTest {

    @Test
    fun legacyPresetsMigrateToEquivalentSliderValues() {
        assertEquals(0.75f, captionTextScaleFromStored("SMALL"))
        assertEquals(1f, captionTextScaleFromStored("MEDIUM"))
        assertEquals(1.25f, captionTextScaleFromStored("LARGE"))
    }

    @Test
    fun persistedSliderValueIsClampedToSupportedRange() {
        assertEquals(CAPTION_TEXT_SCALE_MIN, captionTextScaleFromStored(0.1f))
        assertEquals(1.75f, captionTextScaleFromStored(1.75f))
        assertEquals(CAPTION_TEXT_SCALE_MAX, captionTextScaleFromStored(9f))
    }
}
