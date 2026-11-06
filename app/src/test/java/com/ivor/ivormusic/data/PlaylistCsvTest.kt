package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCsvTest {

    @Test
    fun plainFieldIsLeftAsIs() {
        assertEquals("Blinding Lights", PlaylistTransfer.csvField("Blinding Lights"))
    }

    @Test
    fun commaAndQuoteAreQuoted() {
        assertEquals("\"Hello, \"\"World\"\"\"", PlaylistTransfer.csvField("Hello, \"World\""))
    }

    @Test
    fun lineBreaksBecomeSpaces() {
        assertEquals("one two", PlaylistTransfer.csvField("one\ntwo"))
    }
}
