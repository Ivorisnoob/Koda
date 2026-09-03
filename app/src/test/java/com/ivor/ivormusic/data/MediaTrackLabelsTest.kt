package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MediaTrackLabelsTest {

    @Test
    fun `a known language code becomes its display name`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals("Japanese", languageDisplayName("ja"))
            assertEquals("German", languageDisplayName("de"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `container placeholders for no language are not shown as names`() {
        // A row reading "und" or "zxx" is worse than a positional label: it
        // looks like a language nobody has heard of rather than like a track
        // whose language the file never stated.
        assertNull(languageDisplayName("und"))
        assertNull(languageDisplayName("zxx"))
        assertNull(languageDisplayName("mul"))
        assertNull(languageDisplayName(null))
        assertNull(languageDisplayName("   "))
    }

    @Test
    fun `an unrecognised code falls through rather than being echoed back`() {
        assertNull(languageDisplayName("qbc"))
    }

    @Test
    fun `a track title wins over the language it is in`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals(
                "English - Director's commentary",
                audioTrackLabel("Director's commentary", "en", index = 1)
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a title that already names the language is not repeated`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals("English dub", audioTrackLabel("English dub", "en", index = 0))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `an untagged track gets a position rather than a blank row`() {
        // Multi-audio rips routinely tag nothing at all, and three rows all
        // reading "Audio" would make the menu useless.
        assertEquals("Audio track 3", audioTrackLabel(null, null, index = 2))
        assertEquals("Subtitle track 1", textTrackLabel(null, null, index = 0))
    }

    @Test
    fun `forced and hearing-impaired subtitle tracks are marked`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            assertEquals(
                "English (forced)",
                textTrackLabel(null, "en", index = 0, isForced = true)
            )
            assertEquals(
                "English (forced, SDH)",
                textTrackLabel(null, "en", index = 0, isForced = true, isHearingImpaired = true)
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `audio detail names what actually differs between two tracks`() {
        assertEquals("E-AC-3 - 5.1 - 640 kbps", audioTrackDetail("audio/eac3", 6, 640_000))
        assertEquals("AAC - Stereo", audioTrackDetail("audio/mp4a-latm", 2, 0))
        assertNull(audioTrackDetail(null, 0, 0))
    }

    @Test
    fun `only frame rates worth naming reach the quality label`() {
        // 29.97 and 30 both mean "normal" to a viewer; 59.94 is what every
        // other player shows as 60.
        assertEquals("1080p", localVideoQualityLabel(1080, 29.97f))
        assertEquals("1080p", localVideoQualityLabel(1080, 30f))
        assertEquals("2160p60", localVideoQualityLabel(2160, 59.94f))
        assertEquals("720p120", localVideoQualityLabel(720, 120f))
    }

    @Test
    fun `a video track with no reported height still gets a label`() {
        assertEquals("Video", localVideoQualityLabel(0, 60f))
    }
}
