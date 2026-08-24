package com.ivor.ivormusic.ui.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSizeFormattingTest {

    @Test
    fun formatsMediaSizesInBinaryMegabytesAndGigabytes() {
        assertEquals("7.5 MB", formatDownloadSize((7.5 * 1024 * 1024).toLong()))
        assertEquals("1.5 GB", formatDownloadSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
