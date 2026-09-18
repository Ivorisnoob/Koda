package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrPoTokenBinding
import com.ivor.ivormusic.data.youtube.sabr.model.parseSabrBootstrap
import org.junit.Assert.*
import org.junit.Test

class SabrBootstrapTest {
    @Test fun `content binding resolves every field`() {
        val bootstrap = parseSabrBootstrap(home(challenge(), flags = CONTENT_FLAGS))
        assertEquals(SabrPoTokenBinding.CONTENT, bootstrap.binding)
        assertEquals("Cgt0ZXN0dj09", bootstrap.visitorData)
        assertEquals("CAoQAA", bootstrap.dataSyncId)
        assertEquals("WEB", bootstrap.clientName)
        assertEquals("2.20260917.01.00", bootstrap.clientVersion)
        assertEquals("eid1", bootstrap.eventId)
        assertEquals("prog", bootstrap.challenge.program)
        assertEquals("trayride", bootstrap.challenge.globalName)
        assertNull(bootstrap.challenge.interpreterJavascript)
        assertEquals("https://example.com/bg.js", bootstrap.challenge.interpreterUrl)
    }

    @Test fun `session binding keeps working without a data sync id`() {
        val bootstrap = parseSabrBootstrap(home(challenge(), flags = SESSION_FLAGS, dataSync = null))
        assertEquals(SabrPoTokenBinding.SESSION, bootstrap.binding)
        assertNull(bootstrap.dataSyncId)
    }

    @Test fun `content wins when both experiment flags are true`() {
        val bootstrap = parseSabrBootstrap(home(challenge(), flags = BOTH_FLAGS))
        assertEquals(SabrPoTokenBinding.CONTENT, bootstrap.binding)
    }

    @Test fun `missing watch config defaults to content`() {
        val bootstrap = parseSabrBootstrap(home(challenge(), flags = null))
        assertEquals(SabrPoTokenBinding.CONTENT, bootstrap.binding)
    }

    @Test fun `disabled flags with a watch config stay none`() {
        val bootstrap = parseSabrBootstrap(home(challenge(), flags = OFF_FLAGS))
        assertEquals(SabrPoTokenBinding.NONE, bootstrap.binding)
    }

    @Test fun `inline interpreter is preferred over the url`() {
        val bootstrap = parseSabrBootstrap(home(inlineChallenge(), flags = CONTENT_FLAGS))
        assertEquals("var bg=null;", bootstrap.challenge.interpreterJavascript)
        assertEquals("https://example.com/bg.js", bootstrap.challenge.interpreterUrl)
    }

    @Test fun `hex escapes decode and later configs win before the challenge`() {
        val first = config(event = "eid-old", visitor = "old")
        val second = config(event = "eid1", visitor = "Cgt0ZXN0dj09")
        val hexChallenge = challenge().replace("\\\"", "\\x22")
        val bootstrap = parseSabrBootstrap(
            "ytcfg.set($first);ytcfg.set($second);window.ytAtN($hexChallenge);" +
                "ytcfg.set(${config(event = "eid-late")});",
        )
        assertEquals("eid1", bootstrap.eventId)
        assertEquals("prog", bootstrap.challenge.program)
    }

    @Test fun `escaped visitor data is normalized and malformed configs skipped`() {
        val bootstrap = parseSabrBootstrap(
            "ytcfg.set(not json);ytcfg.set(${config(visitor = "Cgt%3D0ZXN0")});" +
                "window.ytAtN(${challenge()});",
        )
        assertEquals("Cgt=0ZXN0", bootstrap.visitorData)
    }

    @Test fun `missing pieces throw without guessing`() {
        assertThrows(SabrProtocolException::class.java) { parseSabrBootstrap("") }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap("ytcfg.set(${config()});")
        }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap("window.ytAtN(${challenge()});")
        }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap(home(challenge(), event = null))
        }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap(home(challenge(), visitor = null))
        }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap(home(challenge(), client = "MWEB"))
        }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap(home("""{"R":"{}"}""", flags = CONTENT_FLAGS))
        }
        assertThrows(SabrProtocolException::class.java) {
            parseSabrBootstrap(home(noInterpreterChallenge(), flags = CONTENT_FLAGS))
        }
    }

    @Test fun `diagnostics stay redacted`() {
        val bootstrap = parseSabrBootstrap(home(challenge(), flags = CONTENT_FLAGS))
        assertFalse(bootstrap.toString().contains("Cgt0ZXN0dj09"))
        assertFalse(bootstrap.toString().contains("prog"))
    }

    private fun config(
        event: String? = "eid1",
        visitor: String? = "Cgt0ZXN0dj09",
        dataSync: String? = "CAoQAA",
        client: String = "WEB",
        flags: String? = CONTENT_FLAGS,
    ): String {
        val fields = mutableListOf(
            "\"INNERTUBE_CONTEXT\":{\"client\":{\"clientName\":\"$client\"," +
                "\"clientVersion\":\"2.20260917.01.00\"}}",
        )
        event?.let { fields += "\"EVENT_ID\":\"$it\"" }
        visitor?.let { fields += "\"VISITOR_DATA\":\"$it\"" }
        dataSync?.let { fields += "\"DATASYNC_ID\":\"$it\"" }
        flags?.let {
            fields += "\"WEB_PLAYER_CONTEXT_CONFIGS\":" +
                "{\"WEB_PLAYER_CONTEXT_CONFIG_ID_KEVLAR_WATCH\":" +
                "{\"serializedExperimentFlags\":\"$it\"}}"
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun challenge(program: String = "prog"): String {
        val inner = "{\"bgChallenge\":{\"program\":\"$program\",\"globalName\":\"trayride\"," +
            "\"interpreterUrl\":{\"privateDoNotAccessOrElseTrustedResourceUrlWrappedValue\":" +
            "\"//example.com/bg.js\"}}}"
        return "{\"R\":\"" + inner.replace("\"", "\\\"") + "\"}"
    }

    private fun inlineChallenge(): String {
        val inner = "{\"bgChallenge\":{\"program\":\"prog\",\"globalName\":\"trayride\"," +
            "\"interpreterJavascript\":{\"privateDoNotAccessOrElseSafeScriptWrappedValue\":" +
            "\"var bg=null;\"}," +
            "\"interpreterUrl\":{\"privateDoNotAccessOrElseTrustedResourceUrlWrappedValue\":" +
            "\"//example.com/bg.js\"}}}"
        return "{\"R\":\"" + inner.replace("\"", "\\\"") + "\"}"
    }

    private fun noInterpreterChallenge(): String {
        val inner = "{\"bgChallenge\":{\"program\":\"prog\",\"globalName\":\"trayride\"}}"
        return "{\"R\":\"" + inner.replace("\"", "\\\"") + "\"}"
    }

    private fun home(
        challengeArg: String,
        flags: String? = CONTENT_FLAGS,
        event: String? = "eid1",
        visitor: String? = "Cgt0ZXN0dj09",
        dataSync: String? = "CAoQAA",
        client: String = "WEB",
    ): String = "ytcfg.set(${config(event, visitor, dataSync, client, flags)});" +
        "window.ytAtN($challengeArg);"

    private companion object {
        const val CONTENT_FLAGS =
            "html5_generate_content_po_token=true&html5_generate_session_po_token=false"
        const val SESSION_FLAGS =
            "html5_generate_content_po_token=false&html5_generate_session_po_token=true"
        const val BOTH_FLAGS =
            "html5_generate_content_po_token=true&html5_generate_session_po_token=true"
        const val OFF_FLAGS =
            "html5_generate_content_po_token=false&html5_generate_session_po_token=false"
    }
}
