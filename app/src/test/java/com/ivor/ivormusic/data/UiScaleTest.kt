package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiScaleTest {

    @Test
    fun missingOrUnreadableValueFallsBackToDefault() {
        assertEquals(UI_SCALE_DEFAULT, uiScaleFromStored(null))
        assertEquals(UI_SCALE_DEFAULT, uiScaleFromStored("LARGE"))
        assertEquals(UI_SCALE_DEFAULT, uiScaleFromStored(Unit))
    }

    /**
     * A backup written on a build with a wider range must not reach
     * `Density` unclamped, which is the case the coercion exists for.
     */
    @Test
    fun storedValueIsClampedToSupportedRange() {
        assertEquals(UI_SCALE_MIN, uiScaleFromStored(0.1f))
        assertEquals(UI_SCALE_MAX, uiScaleFromStored(4f))
        assertEquals(0.95f, uiScaleFromStored(0.95f))
    }

    @Test
    fun everyStepSurvivesAStoreAndReadRoundTrip() {
        UI_SCALE_STEPS.forEach { step ->
            assertEquals(step, uiScaleFromStored(step))
        }
    }

    @Test
    fun stepsAreOrderedAndSpanTheWholeRange() {
        assertEquals(UI_SCALE_MIN, UI_SCALE_STEPS.first())
        assertEquals(UI_SCALE_MAX, UI_SCALE_STEPS.last())
        assertTrue(UI_SCALE_STEPS == UI_SCALE_STEPS.sorted())
        assertTrue(UI_SCALE_DEFAULT in UI_SCALE_STEPS)
    }

    /**
     * The slider hands over a continuous value; only a step may ever be
     * written, or the presets stop matching what the slider produces.
     */
    @Test
    fun arbitrarySliderPositionsSnapToTheNearestStep() {
        assertEquals(0.85f, nearestUiScaleStep(0.86f))
        assertEquals(0.90f, nearestUiScaleStep(0.89f))
        assertEquals(1.00f, nearestUiScaleStep(0.99f))
        assertEquals(1.15f, nearestUiScaleStep(1.14f))
    }

    @Test
    fun snappingIsClosedOverTheSupportedRange() {
        var value = UI_SCALE_MIN
        while (value <= UI_SCALE_MAX + 0.001f) {
            assertTrue(nearestUiScaleStep(value) in UI_SCALE_STEPS)
            value += 0.01f
        }
    }
}
