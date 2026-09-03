package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoTest {

    @Test
    fun `device video ids are told apart from YouTube ids`() {
        // The prefix is what stops a MediaStore row id being mistaken for a
        // watch id and sent to InnerTube, which would look like a real request
        // for a video that does not exist.
        assertTrue(LocalVideo.isDeviceVideoId("device:1042"))
        assertFalse(LocalVideo.isDeviceVideoId("dQw4w9WgXcQ"))
        assertFalse(LocalVideo.isDeviceVideoId(null))
        assertFalse(LocalVideo.isDeviceVideoId(""))
    }

    @Test
    fun `sizes read the way a file manager shows them`() {
        assertEquals("1.4 GB", LocalVideoRepository.formatSize(1_400_000_000))
        assertEquals("812 MB", LocalVideoRepository.formatSize(812_000_000))
        assertEquals("4 KB", LocalVideoRepository.formatSize(4_096))
        // A file too small to name in KB still reads as something rather than
        // as an empty gap in the subtitle.
        assertEquals("1 KB", LocalVideoRepository.formatSize(200))
        assertEquals("", LocalVideoRepository.formatSize(0))
    }
}
