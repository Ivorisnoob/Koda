/*
 * PO-token minting orchestration over an injected local-DOM runtime. Flow adapted
 * from PipePipe (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d
 * (LocalDomPoTokenProvider): home bootstrap, BotGuard challenge, GenerateIT
 * integrity token, content/session binding, expiry-aware single-flight minter.
 * Copyright the PipePipe contributors. See THIRD_PARTY_NOTICES.md.
 * Koda differences: transport and page runtime are injected seams (JVM-testable,
 * no Android APIs here); no auto re-warm after invalidate (next mint re-inits,
 * so an idle process holds no WebView); re-init when the profile/login
 * generation moves (rotation-safe: cookie bytes are never compared);
 * interruption maps to SabrCancelledException; Local Only refuses up front;
 * every abandoned session advances attestationGeneration for SabrIdentity.
 * App-scoped; the daemon init thread needs no shutdown.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.session

import com.ivor.ivormusic.data.YouTubeSession
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrCancelledException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrLocalOnlyException
import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import com.ivor.ivormusic.data.youtube.sabr.model.SabrAttestationChallenge
import com.ivor.ivormusic.data.youtube.sabr.model.SabrBootstrap
import com.ivor.ivormusic.data.youtube.sabr.model.SabrMintedToken
import com.ivor.ivormusic.data.youtube.sabr.model.SabrPoTokenBinding
import com.ivor.ivormusic.data.youtube.sabr.model.parseSabrBootstrap
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

/** Callbacks from the page into one mint session; implemented by the runtime (4b). */
internal interface SabrJsCallbacks {
    fun onJsError(error: String)
    fun onBotguardResult(botguardResponse: String)
    fun onMinterReady()
    fun onObtainPoTokenResult(identifier: String, tokenCsvU8: String)
    fun onObtainPoTokenError(identifier: String, error: String)
}

/**
 * The shared page runtime (4b). All calls return immediately; results arrive on
 * the registered callbacks. Implementations must serialize script posts per page.
 */
internal interface SabrLocalDom {
    fun postScript(script: String, onError: (Throwable) -> Unit): Boolean
    fun registerCallbacks(sessionId: String, callbacks: SabrJsCallbacks): () -> Unit
    fun deleteSession(sessionId: String)
    /** The page helper (asset text); posted before any session call. */
    fun helperScript(): String
}

/** Network edge of attestation; implemented where OkHttp lives (4c). No caching here. */
internal interface SabrAttestationTransport {
    fun getHome(cookies: String?): String
    fun getInterpreter(url: String): String
    fun postIntegrity(body: String): String
}

/**
 * App-scoped PO-token minter. Thread-safe; mint blocks the caller (never call
 * from the main thread). [invalidate] must also be called when Local-Only flips;
 * profile and login moves are picked up automatically.
 */
internal class SabrTokenMinter(
    private val sessionNow: () -> YouTubeSession?,
    private val localOnly: () -> Boolean,
    private val transport: SabrAttestationTransport,
    private val dom: SabrLocalDom,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val initTimeoutMs: Long = 60_000,
    private val tokenTimeoutMs: Long = 30_000,
    private val expiryMarginMs: Long = 60_000,
) {
    private val lock = Any()
    private var task: FutureTask<MintState>? = null
    private val generation = AtomicLong(0)

    /** Advances whenever a mint session is abandoned; feeds SabrIdentity. */
    val attestationGeneration: Long get() = generation.get()

    fun warmUp() {
        ensureTask()
    }

    @Throws(SabrProtocolException::class)
    fun mint(videoId: String): SabrMintedToken {
        if (localOnly()) throw SabrLocalOnlyException()
        require(videoId.isNotBlank()) { "Missing SABR video id" }
        val state = getState()
        val raw = when (state.bootstrap.binding) {
            SabrPoTokenBinding.CONTENT -> obtainToken(state.sessionId, state.waiters, videoId)
            SabrPoTokenBinding.SESSION -> state.sessionPoToken?.copyOf()
                ?: throw SabrProtocolException("PO token session has no session token")
            SabrPoTokenBinding.NONE -> throw SabrProtocolException(
                "YouTube home does not enable a supported PO token binding")
        }
        return SabrMintedToken(
            visitorData = state.bootstrap.visitorData,
            clientVersion = state.bootstrap.clientVersion,
            poTokenBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(raw),
        )
    }

    /**
     * Drops the mint session and advances the generation so descriptors minted
     * before it read as stale. The next mint re-initializes lazily.
     */
    fun invalidate() {
        val state = synchronized(lock) {
            val current = task ?: return
            task = null
            try {
                current.get()
            } catch (_: Exception) {
                return
            }
        }
        abandon(state)
    }

    private fun getState(): MintState {
        while (true) {
            val current = ensureTask()
            val state = try {
                current.get()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SabrCancelledException("PO token initialization interrupted")
            } catch (error: ExecutionException) {
                synchronized(lock) {
                    if (task === current) task = null
                }
                val cause = error.cause
                if (cause is SabrProtocolException || cause is SabrLocalOnlyException) throw cause
                throw SabrProtocolException("PO token initialization failed: ${cause?.message}")
            } catch (error: java.util.concurrent.CancellationException) {
                synchronized(lock) {
                    if (task === current) task = null
                }
                throw SabrCancelledException("PO token initialization cancelled")
            }
            if (!isStale(state)) return state
            synchronized(lock) {
                if (task === current) task = null else continue
            }
            abandon(state)
        }
    }

    private fun isStale(state: MintState): Boolean {
        if (nowMs() >= state.expiresAtMs - expiryMarginMs) return true
        val current = sessionNow()?.let { it.profileId to it.generation }
        return current != state.login
    }

    private fun abandon(state: MintState) {
        generation.incrementAndGet()
        state.unregister()
        dom.deleteSession(state.sessionId)
        // Threads blocked in mint would otherwise hang until their timeouts even
        // though their session is gone; fail them so they re-init immediately.
        val waiters = synchronized(state.waiters) {
            state.waiters.values.toList().also { state.waiters.clear() }
        }
        val closed = SabrCancelledException("PO token session closed")
        waiters.forEach {
            it.error.compareAndSet(null, closed)
            it.latch.countDown()
        }
    }

    private fun ensureTask(): FutureTask<MintState> {
        task?.let { return it }
        synchronized(lock) {
            task?.let { return it }
            val attempt = FutureTask { initialize() }
            task = attempt
            initExecutor.execute(attempt)
            return attempt
        }
    }

    private fun initialize(): MintState {
        if (localOnly()) throw SabrLocalOnlyException()
        val session = sessionNow()
        val bootstrap = parseSabrBootstrap(transport.getHome(session?.cookies))
        if (bootstrap.binding == SabrPoTokenBinding.NONE) {
            throw SabrProtocolException("YouTube home does not enable a supported PO token binding")
        }
        val sessionId = UUID.randomUUID().toString()
        val waiters = mutableMapOf<String, TokenWaiter>()
        val botguardDone = CountDownLatch(1)
        val botguardResult = AtomicReference<String>()
        val botguardError = AtomicReference<Throwable>()
        val unregister = dom.registerCallbacks(sessionId, mintCallbacks(
            waiters = waiters,
            onBotguard = {
                botguardResult.set(it)
                botguardDone.countDown()
            },
            onJsError = {
                // Async page faults on the helper and BotGuard legs land here; the
                // minter-ready window and the token legs fail their own waiters.
                botguardError.set(SabrProtocolException(it))
                botguardDone.countDown()
            },
        ))
        try {
            if (!dom.postScript(dom.helperScript() + "\ntrue",
                    onError = {
                        botguardError.set(it)
                        botguardDone.countDown()
                    })) {
                throw SabrProtocolException("Could not post PO token helper")
            }
            val inline = bootstrap.challenge.interpreterJavascript
            val interpreter = inline ?: transport.getInterpreter(
                bootstrap.challenge.interpreterUrl
                    ?: throw SabrProtocolException("Attestation challenge has no interpreter"))
            if (!dom.postScript(
                    sabrRunBotguardScript(sessionId, bootstrap.eventId,
                        bootstrap.challenge, interpreter),
                    onError = {
                        botguardError.set(it)
                        botguardDone.countDown()
                    },
                )) {
                throw SabrProtocolException("Could not post BotGuard challenge")
            }
            try {
                if (!botguardDone.await(initTimeoutMs, TimeUnit.MILLISECONDS)) {
                    throw SabrProtocolException("BotGuard challenge timed out")
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw SabrCancelledException("BotGuard challenge interrupted")
            }
            botguardError.get()?.let { throw it }
            val challenge = botguardResult.get()
                ?: throw SabrProtocolException("BotGuard challenge returned nothing")
            val (tokenU8, ttlSec) = requestIntegrity(challenge)
            val ready = TokenWaiter()
            synchronized(waiters) { waiters[INIT_WAITER] = ready }
            if (!dom.postScript(sabrCreateMinterScript(sessionId, tokenU8),
                    onError = { failWaiter(waiters, INIT_WAITER, it) })) {
                throw SabrProtocolException("Could not post PO token minter creation")
            }
            await(ready, waiters, INIT_WAITER, initTimeoutMs, "PO token minter initialization")
            ready.error.get()?.let { throw it }
            val sessionToken = if (bootstrap.binding == SabrPoTokenBinding.SESSION) {
                val binding = if (session != null) {
                    bootstrap.dataSyncId
                        ?: throw SabrProtocolException("Authenticated home has no data sync id")
                } else bootstrap.visitorData
                obtainToken(sessionId, waiters, binding)
            } else null
            return MintState(bootstrap, sessionId, unregister, sessionToken,
                nowMs() + TimeUnit.SECONDS.toMillis(ttlSec),
                session?.let { it.profileId to it.generation }, waiters)
        } catch (error: Throwable) {
            unregister()
            dom.deleteSession(sessionId)
            throw error
        }
    }

    private fun requestIntegrity(botguardResponse: String): Pair<String, Long> {
        val raw = transport.postIntegrity("[\"$INTEGRITY_REQUEST_KEY\",\"$botguardResponse\"]")
        val parsed = try {
            JSONArray(raw)
        } catch (error: Exception) {
            throw SabrProtocolException("Integrity response is not a JSON array", error)
        }
        if (parsed.length() < 2) throw SabrProtocolException("Integrity response is truncated")
        val token = try {
            Base64.getDecoder().decode(parsed.getString(0).replace('-', '+')
                .replace('_', '/').replace('.', '='))
        } catch (error: Exception) {
            throw SabrProtocolException("Integrity token is not base64", error)
        }
        val ttlSec = parsed.optLong(1, -1)
        if (ttlSec <= 0) throw SabrProtocolException("Integrity token has no lifetime")
        return u8Source(token) to ttlSec
    }

    private fun obtainToken(
        sessionId: String,
        waiters: MutableMap<String, TokenWaiter>,
        identifier: String,
    ): ByteArray {
        val waiter = TokenWaiter()
        synchronized(waiters) { waiters[identifier] = waiter }
        if (!dom.postScript(sabrObtainTokenScript(sessionId, identifier),
                onError = { failWaiter(waiters, identifier, it) })) {
            synchronized(waiters) { waiters.remove(identifier) }
            throw SabrProtocolException("Could not post PO token generation")
        }
        await(waiter, waiters, identifier, tokenTimeoutMs, "PO token generation")
        waiter.error.get()?.let { throw it }
        return waiter.token.get()?.takeIf { it.isNotEmpty() }
            ?: throw SabrProtocolException("PO token generation returned nothing")
    }

    @Throws(SabrProtocolException::class)
    private fun await(
        waiter: TokenWaiter,
        waiters: MutableMap<String, TokenWaiter>,
        key: String,
        timeoutMs: Long,
        operation: String,
    ) {
        try {
            if (!waiter.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                synchronized(waiters) { waiters.remove(key) }
                throw SabrProtocolException("$operation timed out")
            }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            synchronized(waiters) { waiters.remove(key) }
            throw SabrCancelledException("$operation interrupted")
        }
    }

    private fun failWaiter(
        waiters: MutableMap<String, TokenWaiter>,
        key: String,
        error: Throwable,
    ) {
        val waiter = synchronized(waiters) { waiters.remove(key) } ?: return
        waiter.error.set(error)
        waiter.latch.countDown()
    }

    private fun mintCallbacks(
        waiters: MutableMap<String, TokenWaiter>,
        onBotguard: ((String) -> Unit)? = null,
        onJsError: ((String) -> Unit)? = null,
    ) = object : SabrJsCallbacks {
        override fun onJsError(error: String) {
            onJsError?.invoke(error)
                ?: failWaiter(waiters, INIT_WAITER, SabrProtocolException(error))
        }

        override fun onBotguardResult(botguardResponse: String) {
            onBotguard?.invoke(botguardResponse)
        }

        override fun onMinterReady() {
            val waiter = synchronized(waiters) { waiters.remove(INIT_WAITER) } ?: return
            waiter.latch.countDown()
        }

        override fun onObtainPoTokenResult(identifier: String, tokenCsvU8: String) {
            val waiter = synchronized(waiters) { waiters.remove(identifier) } ?: return
            try {
                waiter.token.set(csvToBytes(tokenCsvU8))
            } catch (error: Throwable) {
                waiter.error.set(error)
            } finally {
                waiter.latch.countDown()
            }
        }

        override fun onObtainPoTokenError(identifier: String, error: String) {
            failWaiter(waiters, identifier, SabrProtocolException(error))
        }
    }

    private inner class MintState(
        val bootstrap: SabrBootstrap,
        val sessionId: String,
        val unregister: () -> Unit,
        val sessionPoToken: ByteArray?,
        val expiresAtMs: Long,
        val login: Pair<String, Long>?,
        val waiters: MutableMap<String, TokenWaiter>,
    )

    private class TokenWaiter {
        val latch = CountDownLatch(1)
        val token = AtomicReference<ByteArray>()
        val error = AtomicReference<Throwable>()
    }


    private companion object {
        const val INTEGRITY_REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val INIT_WAITER = "init"
        val initExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "SabrPoTokenInit").apply { isDaemon = true }
        }
    }
}

/** `new Uint8Array([...])` source evaluated by the page to rebuild raw bytes. */
internal fun u8Source(bytes: ByteArray): String =
    "new Uint8Array([" + bytes.joinToString(",") { it.toUByte().toString() } + "])"

internal fun csvToBytes(csv: String): ByteArray {
    if (csv.isBlank()) throw SabrProtocolException("PO token came back empty")
    return csv.split(",").map {
        it.toUByteOrNull()?.toByte() ?: throw SabrProtocolException("PO token is not bytes")
    }.toByteArray()
}

internal fun sabrRunBotguardScript(
    sessionId: String,
    eventId: String,
    challenge: SabrAttestationChallenge,
    interpreterJavascript: String,
): String = "kodaSabrRunBotguard(${JSONObject.quote(sessionId)}, " +
    "${JSONObject.quote(eventId)}, " +
    JSONObject().put("interpreterJavascript", JSONObject()
        .put("privateDoNotAccessOrElseSafeScriptWrappedValue", interpreterJavascript))
        .put("program", challenge.program)
        .put("globalName", challenge.globalName)
        .toString() + ");"

internal fun sabrCreateMinterScript(sessionId: String, integrityTokenU8: String): String =
    "kodaSabrCreateMinter(${JSONObject.quote(sessionId)}, $integrityTokenU8);"

internal fun sabrObtainTokenScript(sessionId: String, identifier: String): String =
    "kodaSabrObtainPoToken(${JSONObject.quote(sessionId)}, " +
        "${JSONObject.quote(identifier)}, ${u8Source(identifier.toByteArray(Charsets.UTF_8))});"

internal fun sabrDeleteSessionScript(sessionId: String): String =
    "kodaSabrDeleteSession(${JSONObject.quote(sessionId)});"
