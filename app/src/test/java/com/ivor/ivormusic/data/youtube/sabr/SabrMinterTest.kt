package com.ivor.ivormusic.data.youtube.sabr

import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrLocalOnlyException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrAttestationChallenge
import com.ivor.ivormusic.data.youtube.sabr.model.SabrPoTokenBinding
import com.ivor.ivormusic.data.youtube.sabr.session.SabrAttestationTransport
import com.ivor.ivormusic.data.youtube.sabr.session.SabrJsCallbacks
import com.ivor.ivormusic.data.youtube.sabr.session.SabrLocalDom
import com.ivor.ivormusic.data.youtube.sabr.session.SabrTokenMinter
import com.ivor.ivormusic.data.youtube.sabr.session.csvToBytes
import com.ivor.ivormusic.data.youtube.sabr.session.sabrObtainTokenScript
import com.ivor.ivormusic.data.youtube.sabr.session.sabrRunBotguardScript
import com.ivor.ivormusic.data.youtube.sabr.session.u8Source
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SabrMinterTest {
    @Test fun `content binding mints per video`() {
        val fakes = Fakes(binding = SabrPoTokenBinding.CONTENT)
        val token = fakes.minter.mint("video-id-01")
        assertEquals("Cgt0ZXN0dj09", token.visitorData)
        assertEquals("2.20260917.01.00", token.clientVersion)
        // Base64url of the fake page's "1,2,3" bytes. First session: generation 0,
        // which only advances when a session is abandoned.
        assertEquals("AQID", token.poTokenBase64Url)
        assertEquals(0, fakes.minter.attestationGeneration)
        // The helper goes out before any session call.
        assertTrue(fakes.dom.scripts.first().endsWith("\ntrue"))
    }

    @Test fun `session binding mints once and reuses`() {
        val fakes = Fakes(binding = SabrPoTokenBinding.SESSION)
        val first = fakes.minter.mint("video-id-01")
        val second = fakes.minter.mint("video-id-02")
        assertEquals(first.poTokenBase64Url, second.poTokenBase64Url)
        assertEquals(1, fakes.dom.tokenCalls.get())
    }

    @Test fun `anonymous session binding falls back to visitor data`() {
        val fakes = Fakes(binding = SabrPoTokenBinding.SESSION, session = null)
        val token = fakes.minter.mint("video-id-01")
        assertEquals("AQID", token.poTokenBase64Url)
        assertEquals(listOf("Cgt0ZXN0dj09"), fakes.dom.mintedFor)
    }

    @Test fun `login move reinitializes without comparing cookie bytes`() {
        var session: YouTubeSession? = YouTubeSession("p1", 7, "cookies-v1")
        val fakes = Fakes(sessionNow = { session })
        fakes.minter.mint("video-id-01")
        assertEquals(1, fakes.transport.homeCalls.get())
        session = YouTubeSession("p1", 7, "cookies-v2-rotated")
        fakes.minter.mint("video-id-01")
        assertEquals("rotation keeps the generation, so no re-init", 1, fakes.transport.homeCalls.get())
        session = YouTubeSession("p1", 8, "cookies-v3")
        fakes.minter.mint("video-id-01")
        assertEquals(2, fakes.transport.homeCalls.get())
        session = YouTubeSession("p2", 1, "cookies-v4")
        fakes.minter.mint("video-id-01")
        assertEquals(3, fakes.transport.homeCalls.get())
    }

    @Test fun `invalidate advances the generation`() {
        val fakes = Fakes()
        fakes.minter.mint("video-id-01")
        val before = fakes.minter.attestationGeneration
        fakes.minter.invalidate()
        assertEquals(before + 1, fakes.minter.attestationGeneration)
        // Re-initializing after abandonment keeps the advanced generation.
        fakes.minter.mint("video-id-01")
        assertEquals(before + 1, fakes.minter.attestationGeneration)
    }

    @Test fun `local only refuses before any network`() {
        val fakes = Fakes(localOnly = true)
        assertThrows(SabrLocalOnlyException::class.java) { fakes.minter.mint("video-id-01") }
        assertThrows(SabrLocalOnlyException::class.java) { fakes.minter.warmUp(); fakes.minter.mint("x") }
        assertEquals(0, fakes.transport.homeCalls.get())
    }

    @Test fun `unsupported binding and token timeout throw`() {
        assertThrows(SabrProtocolException::class.java) {
            Fakes(binding = SabrPoTokenBinding.NONE).minter.mint("video-id-01")
        }
        val silent = Fakes()
        silent.dom.silentTokens = true
        assertThrows(SabrProtocolException::class.java) { silent.minter.mint("video-id-01") }
    }

    @Test fun `concurrent warm up single flights`() {
        val fakes = Fakes()
        val gate = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) {
            Thread {
                gate.await()
                fakes.minter.warmUp()
                done.countDown()
            }.apply { isDaemon = true; start() }
        }
        gate.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        // Second wave mints against the single established session.
        fakes.minter.mint("video-id-01")
        assertEquals(1, fakes.transport.homeCalls.get())
    }

    @Test fun `script builders carry the exact page contract`() {
        val run = sabrRunBotguardScript("sid", "eid",
            SabrAttestationChallenge("prog", "trayride", null, "https://example.com/bg.js"),
            "var bg=null;")
        assertTrue(run.startsWith("kodaSabrRunBotguard(\"sid\", \"eid\", "))
        assertTrue(run.contains("privateDoNotAccessOrElseSafeScriptWrappedValue"))
        assertTrue(run.contains("\"program\":\"prog\""))
        val obtain = sabrObtainTokenScript("sid", "video-id-01")
        assertTrue(obtain.contains("kodaSabrObtainPoToken(\"sid\", \"video-id-01\", new Uint8Array(["))
        assertEquals("new Uint8Array([1,2,3])", u8Source(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), csvToBytes("1,2,3"))
    }

    private class Fakes(
        val binding: SabrPoTokenBinding = SabrPoTokenBinding.CONTENT,
        session: YouTubeSession? = YouTubeSession("p1", 7, "cookies"),
        sessionNow: () -> YouTubeSession? = { session },
        localOnly: Boolean = false,
    ) {
        val transport = FakeTransport(binding)
        val dom = FakeDom()
        var local = localOnly
        val minter = SabrTokenMinter(
            sessionNow = sessionNow,
            localOnly = { local },
            transport = transport,
            dom = dom,
            initTimeoutMs = 5_000,
            tokenTimeoutMs = 500,
            expiryMarginMs = 60_000,
        )
    }

    private class FakeTransport(val binding: SabrPoTokenBinding) : SabrAttestationTransport {
        val homeCalls = AtomicInteger()
        override fun getHome(cookies: String?): String {
            homeCalls.incrementAndGet()
            val flags = when (binding) {
                SabrPoTokenBinding.CONTENT ->
                    "html5_generate_content_po_token=true&html5_generate_session_po_token=false"
                SabrPoTokenBinding.SESSION ->
                    "html5_generate_content_po_token=false&html5_generate_session_po_token=true"
                SabrPoTokenBinding.NONE ->
                    "html5_generate_content_po_token=false&html5_generate_session_po_token=false"
            }
            val config = JSONObject()
                .put("INNERTUBE_CONTEXT", JSONObject().put("client", JSONObject()
                    .put("clientName", "WEB").put("clientVersion", "2.20260917.01.00")))
                .put("EVENT_ID", "eid1")
                .put("VISITOR_DATA", "Cgt0ZXN0dj09")
                .put("DATASYNC_ID", "CAoQAA")
                .put("WEB_PLAYER_CONTEXT_CONFIGS", JSONObject().put(
                    "WEB_PLAYER_CONTEXT_CONFIG_ID_KEVLAR_WATCH", JSONObject()
                        .put("serializedExperimentFlags", flags)))
            val inner = JSONObject().put("bgChallenge", JSONObject()
                .put("program", "prog").put("globalName", "trayride")
                .put("interpreterJavascript", JSONObject()
                    .put("privateDoNotAccessOrElseSafeScriptWrappedValue", "var bg=null;")))
            val challenge = JSONObject().put("R", inner.toString())
            return "ytcfg.set($config);window.ytAtN($challenge);"
        }

        override fun getInterpreter(url: String): String = throw AssertionError("inline only")
        override fun postIntegrity(body: String): String {
            assertTrue(body.startsWith("[\"O43z0dpjhgX20SCx4KAo\",\""))
            // Token bytes "1,2,3" as standard base64 with a 1h lifetime.
            return "[\"AQID\",3600]"
        }
    }

    private class FakeDom : SabrLocalDom {
        val tokenCalls = AtomicInteger()
        val mintedFor = mutableListOf<String>()
        val scripts = mutableListOf<String>()
        var silentTokens = false
        private var callbacks: SabrJsCallbacks? = null

        override fun helperScript(): String = "true"

        override fun postScript(script: String, onError: (Throwable) -> Unit): Boolean {
            synchronized(scripts) { scripts.add(script) }
            when {
                script.startsWith("kodaSabrRunBotguard(") ->
                    callbacks?.onBotguardResult("botguard-response")
                script.startsWith("kodaSabrCreateMinter(") ->
                    callbacks?.onMinterReady()
                script.startsWith("kodaSabrObtainPoToken(") -> {
                    if (!silentTokens) {
                        // ("sid", "<id>", ...) - the identifier is the second quoted string.
                        val id = script.split("\"").getOrNull(3) ?: return true
                        tokenCalls.incrementAndGet()
                        synchronized(mintedFor) { mintedFor.add(id) }
                        callbacks?.onObtainPoTokenResult(id, "1,2,3")
                    }
                }
            }
            return true
        }

        override fun registerCallbacks(sessionId: String, callbacks: SabrJsCallbacks): () -> Unit {
            this.callbacks = callbacks
            return {}
        }

        override fun deleteSession(sessionId: String) = Unit
    }
}
