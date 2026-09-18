package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.model.SabrMintedToken
import org.junit.Assert.*
import org.junit.Test

class SabrPlayerRequestTest {
    private val token = SabrMintedToken(
        visitorData = "Cgt0ZXN0dj09",
        clientVersion = "2.20260917.01.00",
        poTokenBase64Url = "QUJD",
    )

    @Test fun `parts mirror the upstream token bound body`() {
        val parts = token.requestParts("MWEB-UA", 19888)
        assertEquals("MWEB-UA", parts.extraClientFields.getString("userAgent"))
        assertEquals("UTC", parts.extraClientFields.getString("timeZone"))
        assertEquals(2, parts.extraClientFields.length())
        assertEquals("QUJD", parts.serviceIntegrityDimensions.getString("poToken"))
        assertEquals(1, parts.serviceIntegrityDimensions.length())
        assertEquals(19888, parts.signatureTimestamp)
    }

    @Test fun `token keeps its minting identity`() {
        assertEquals("Cgt0ZXN0dj09", token.visitorData)
        assertEquals("2.20260917.01.00", token.clientVersion)
        assertEquals("QUJD", token.poTokenBase64Url)
    }

    @Test fun `blank inputs throw without guessing`() {
        assertThrows(IllegalArgumentException::class.java) { SabrMintedToken("", "v", "t") }
        assertThrows(IllegalArgumentException::class.java) { SabrMintedToken("v", "", "t") }
        assertThrows(IllegalArgumentException::class.java) { SabrMintedToken("v", "v", "") }
        assertThrows(IllegalArgumentException::class.java) { token.requestParts("", 0) }
        assertThrows(IllegalArgumentException::class.java) { token.requestParts("MWEB-UA", -1) }
    }
}
