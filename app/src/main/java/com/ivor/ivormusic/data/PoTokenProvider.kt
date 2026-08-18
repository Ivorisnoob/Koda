package com.ivor.ivormusic.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Mints Proof-of-Origin tokens by running Google's BotGuard challenge.
 *
 * SABR playback ([SabrSession]) will not start without one, and there is no way
 * to compute it offline: BotGuard ships as obfuscated JavaScript that is
 * re-generated server-side, so it has to be *executed*. That is the whole
 * reason a WebView appears in the data layer. Same approach NewPipe takes.
 *
 * The exchange is four steps, deliberately split between OkHttp and the WebView
 * so the page only does what must happen in a JS realm:
 *
 * 1. `youtubei/v1/att/get` returns a `bgChallenge` - an interpreter URL, a
 *    program blob, and the global name the interpreter defines. (OkHttp)
 * 2. The interpreter JS is fetched from google.com. (OkHttp - the page cannot,
 *    that host serves no CORS headers.)
 * 3. The WebView evaluates the interpreter, runs the program, and takes a
 *    "snapshot"; the same run leaves a minter factory behind in the page.
 * 4. `Waa/GenerateIT` trades the snapshot for an integrity token (OkHttp),
 *    which is handed back into the page to mint the actual token.
 *
 * **The page is loaded with `https://www.youtube.com` as its base URL**, not as
 * a `data:` document. Origin matters: the challenge fetch and the VM's own
 * probes are same-origin against youtube.com that way, and opaque-origin pages
 * fail them.
 *
 * **Tokens are session-bound**, i.e. bound to the `visitorData` in force when
 * they were minted. A token replayed under a different `visitorData` is not
 * valid, so [invalidate] must be called wherever that value is dropped - which
 * is [AccountSwitcher] on a profile switch and
 * `YouTubeRepository.refreshVisitorDataAfterPlaybackFailure` on a re-mint.
 *
 * Cache is process-wide rather than per-instance for the same reason
 * `visitorData` is: repositories are created per ViewModel, and minting costs a
 * WebView plus three network round trips.
 *
 * Verified against live endpoints, August 2026.
 */
object PoTokenProvider {

    private const val TAG = "PoTokenProvider"

    private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
    private const val GOOG_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    private const val GENERATE_IT_URL =
        "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT"
    private const val ATT_GET_URL = "https://www.youtube.com/youtubei/v1/att/get?prettyPrint=false"

    /**
     * Held well under the integrity token's own TTL (12h observed) and under
     * `VISITOR_DATA_TTL_MS`, because the token is only valid for as long as the
     * `visitorData` it was bound to.
     */
    private const val TOKEN_TTL_MS = 5 * 60 * 60 * 1000L

    /** The VM is not fast, and a slow device on a cold WebView is slower. */
    private const val MINT_TIMEOUT_MS = 30_000L

    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedForVisitorData: String? = null

    @Volatile
    private var mintedAt = 0L

    /** Set when minting fails hard, to stop every playback attempt re-trying it. */
    @Volatile
    private var backoffUntil = 0L

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * A PO token bound to [visitorData], minting one if the cache is empty,
     * stale, or bound to a different session.
     *
     * Returns null when minting fails. Callers must treat that as "no SABR this
     * time" rather than as fatal - a null is also what a device with no WebView
     * returns, and that device can still play anything under the progressive
     * cap.
     */
    suspend fun getPoToken(context: Context, visitorData: String): String? {
        if (visitorData.isBlank()) return null
        val now = System.currentTimeMillis()

        cachedToken?.let {
            if (cachedForVisitorData == visitorData && now - mintedAt < TOKEN_TTL_MS) return it
        }
        if (now < backoffUntil) return null

        return mutex.withLock {
            // Re-check: another caller may have minted while this one waited.
            val inner = System.currentTimeMillis()
            cachedToken?.let {
                if (cachedForVisitorData == visitorData && inner - mintedAt < TOKEN_TTL_MS) {
                    return@withLock it
                }
            }
            if (inner < backoffUntil) return@withLock null

            val token = try {
                withTimeoutOrNull(MINT_TIMEOUT_MS) { mint(context, visitorData) }
            } catch (e: Exception) {
                Log.w(TAG, "PO token mint failed: ${e.message}")
                null
            }

            if (token.isNullOrBlank()) {
                // Back off rather than re-running a 30s WebView dance on every
                // tap; playback still works for anything under the cap.
                backoffUntil = System.currentTimeMillis() + 2 * 60 * 1000L
                Log.w(TAG, "PO token unavailable; backing off for 2 minutes")
                null
            } else {
                cachedToken = token
                cachedForVisitorData = visitorData
                mintedAt = System.currentTimeMillis()
                backoffUntil = 0L
                Log.i(TAG, "PO token minted (${token.length} chars)")
                token
            }
        }
    }

    /** The cached token, if one is already valid for [visitorData]. No network. */
    fun peek(visitorData: String): String? {
        val token = cachedToken ?: return null
        if (cachedForVisitorData != visitorData) return null
        if (System.currentTimeMillis() - mintedAt >= TOKEN_TTL_MS) return null
        return token
    }

    /**
     * Drop the cached token. Must be called anywhere `visitorData` is dropped,
     * since the token is bound to it and a stale one fails silently rather than
     * loudly.
     */
    fun invalidate() {
        cachedToken = null
        cachedForVisitorData = null
        mintedAt = 0L
        backoffUntil = 0L
    }

    // ------------------------------------------------------------------
    // the exchange
    // ------------------------------------------------------------------

    private suspend fun mint(context: Context, visitorData: String): String? {
        val challenge = fetchChallenge() ?: return null
        val interpreter = fetchInterpreter(challenge.interpreterUrl) ?: return null

        val runner = BotGuardRunner(context.applicationContext)
        try {
            if (!runner.start(interpreter, challenge.program, challenge.globalName)) return null
            val snapshot = runner.snapshot() ?: return null
            val integrityToken = generateIntegrityToken(snapshot) ?: return null
            return runner.mint(integrityToken, visitorData)
        } finally {
            runner.destroy()
        }
    }

    private class Challenge(
        val program: String,
        val globalName: String,
        val interpreterUrl: String,
    )

    private suspend fun fetchChallenge(): Challenge? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("context", JSONObject().put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", YouTubeRepository.webClientVersion())
                    put("hl", "en")
                    put("gl", "US")
                }))
                put("engagementType", "ENGAGEMENT_TYPE_UNBOUND")
            }.toString()

            val request = Request.Builder()
                .url(ATT_GET_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", YouTubeRepository.BROWSER_USER_AGENT)
                .addHeader("Origin", "https://www.youtube.com")
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "att/get HTTP ${response.code}")
                    return@withContext null
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val bg = json.optJSONObject("bgChallenge") ?: return@withContext null
                val program = bg.optString("program").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val globalName = bg.optString("globalName").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                var url = bg.optJSONObject("interpreterUrl")
                    ?.optString("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue")
                    .orEmpty()
                if (url.isBlank()) return@withContext null
                if (url.startsWith("//")) url = "https:$url"
                Challenge(program, globalName, url)
            }
        } catch (e: Exception) {
            Log.w(TAG, "att/get failed: ${e.message}")
            null
        }
    }

    private suspend fun fetchInterpreter(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", YouTubeRepository.BROWSER_USER_AGENT)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "interpreter HTTP ${response.code}")
                    return@withContext null
                }
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "interpreter fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Trades the BotGuard snapshot for an integrity token.
     *
     * The request and response are both bare JSON arrays - this is a gRPC-web
     * endpoint speaking `application/json+protobuf`, not an InnerTube call, so
     * none of the usual helpers apply.
     */
    private suspend fun generateIntegrityToken(snapshot: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val payload = JSONArray().put(REQUEST_KEY).put(snapshot).toString()
                val request = Request.Builder()
                    .url(GENERATE_IT_URL)
                    .post(payload.toRequestBody("application/json+protobuf".toMediaType()))
                    .addHeader("x-goog-api-key", GOOG_API_KEY)
                    .addHeader("x-user-agent", "grpc-web-javascript/0.1")
                    .addHeader("User-Agent", YouTubeRepository.BROWSER_USER_AGENT)
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GenerateIT HTTP ${response.code}")
                        return@withContext null
                    }
                    val arr = JSONArray(response.body?.string().orEmpty())
                    arr.optString(0).takeIf { it.isNotBlank() && it != "null" }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GenerateIT failed: ${e.message}")
                null
            }
        }

    // ------------------------------------------------------------------
    // the WebView half
    // ------------------------------------------------------------------

    /**
     * Drives one BotGuard session inside a hidden WebView.
     *
     * The VM's minter factory is a closure that lives in the page, so the same
     * WebView has to survive from [snapshot] through [mint] - which is why this
     * is an object with a lifecycle rather than a single call.
     */
    private class BotGuardRunner(private val context: Context) {

        private var webView: WebView? = null
        private val pageReady = CompletableDeferred<Boolean>()
        private var snapshotResult = CompletableDeferred<String?>()
        private var mintResult = CompletableDeferred<String?>()

        @Suppress("unused")
        private inner class Bridge {
            @JavascriptInterface
            fun onSnapshot(value: String?, error: String?) {
                if (error != null) Log.w(TAG, "snapshot error: $error")
                if (!snapshotResult.isCompleted) snapshotResult.complete(value)
            }

            @JavascriptInterface
            fun onMint(value: String?, error: String?) {
                if (error != null) Log.w(TAG, "mint error: $error")
                if (!mintResult.isCompleted) mintResult.complete(value)
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        suspend fun start(interpreterJs: String, program: String, globalName: String): Boolean =
            withContext(Dispatchers.Main) {
                try {
                    val view = WebView(context)
                    webView = view
                    view.settings.javaScriptEnabled = true
                    view.settings.blockNetworkLoads = false
                    view.settings.domStorageEnabled = true
                    view.settings.userAgentString = YouTubeRepository.BROWSER_USER_AGENT
                    view.addJavascriptInterface(Bridge(), "KodaBg")
                    view.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!pageReady.isCompleted) pageReady.complete(true)
                        }
                    }
                    // Base URL, not a data: document: the VM's probes and the
                    // challenge machinery expect a youtube.com origin.
                    view.loadDataWithBaseURL(
                        "https://www.youtube.com",
                        "<!doctype html><html><head></head><body></body></html>",
                        "text/html",
                        "utf-8",
                        null,
                    )
                } catch (e: Throwable) {
                    // A device with no WebView provider at all lands here.
                    Log.w(TAG, "WebView unavailable: ${e.message}")
                    return@withContext false
                }

                if (withTimeoutOrNull(10_000L) { pageReady.await() } != true) {
                    Log.w(TAG, "WebView page never finished loading")
                    return@withContext false
                }

                evaluate(interpreterJs)
                evaluate(runnerScript(program, globalName))
                true
            }

        /** Runs the program and returns the snapshot the integrity call needs. */
        suspend fun snapshot(): String? {
            val result = withTimeoutOrNull(20_000L) { snapshotResult.await() }
            if (result.isNullOrBlank()) Log.w(TAG, "no BotGuard snapshot")
            return result
        }

        /** Mints a token for [binding] using the minter left behind by [snapshot]. */
        suspend fun mint(integrityToken: String, binding: String): String? {
            withContext(Dispatchers.Main) {
                evaluate(
                    "window.__kodaMint(${JSONObject.quote(integrityToken)}," +
                        "${JSONObject.quote(binding)});"
                )
            }
            val result = withTimeoutOrNull(15_000L) { mintResult.await() }
            if (result.isNullOrBlank()) Log.w(TAG, "minter returned nothing")
            return result
        }

        fun destroy() {
            val view = webView ?: return
            webView = null
            // Always on the main thread, and never from inside a WebView callback.
            view.post {
                try {
                    view.removeJavascriptInterface("KodaBg")
                    view.loadUrl("about:blank")
                    view.destroy()
                } catch (_: Exception) {
                }
            }
        }

        private fun evaluate(js: String) {
            webView?.evaluateJavascript(js, null)
        }

        /**
         * The page-side half, a direct transcription of the VM's calling
         * convention:
         *
         * - `vm.a(program, setupCallback, true, ...)` hands back the async
         *   snapshot function through the callback rather than returning it.
         * - `asyncSnapshotFunction(cb, [contentBinding, signedTimestamp,
         *   webPoSignalOutput, skipPrivacyBuffer])` fills `webPoSignalOutput[0]`
         *   with the minter factory as a side effect. That side effect is the
         *   only way to get a minter, which is why the array is created here and
         *   kept for [mint].
         */
        private fun runnerScript(program: String, globalName: String): String = """
            (function () {
              window.__kodaSignals = [];
              window.__kodaMinter = null;
              var report = function (v, e) {
                try { KodaBg.onSnapshot(v || null, e || null); } catch (x) {}
              };
              try {
                var vm = window[${JSONObject.quote(globalName)}];
                if (!vm || !vm.a) { report(null, 'botguard vm unavailable'); return; }

                // The VM hands its functions back through the setup callback,
                // and does so on its own schedule - not necessarily before
                // vm.a() settles. Waiting on a promise resolved by the callback
                // is the only correct ordering; checking straight after vm.a()
                // races it and usually loses.
                var resolveFns;
                var vmFns = new Promise(function (res) { resolveFns = res; });
                var setup = function (asyncSnapshot, shutdown, passEvent, checkCamera) {
                  resolveFns({ asyncSnapshot: asyncSnapshot });
                };
                var noop = function () {};
                // Five telemetry callbacks, and the VM calls them: an empty
                // array throws inside the program rather than being ignored.
                var loggerFunctions = [noop, noop, noop, noop, noop];
                var timeout = new Promise(function (_, rej) {
                  setTimeout(function () { rej(new Error('vm setup timed out')); }, 10000);
                });

                Promise.resolve(
                  vm.a(${JSONObject.quote(program)}, setup, true, undefined,
                       noop, [[], []], undefined, false, loggerFunctions)
                ).then(function () {
                  return Promise.race([vmFns, timeout]);
                }).then(function (fns) {
                  if (!fns || !fns.asyncSnapshot) { report(null, 'no snapshot fn'); return; }
                  fns.asyncSnapshot(function (response) {
                    window.__kodaMinter = window.__kodaSignals[0] || null;
                    report(response, null);
                  }, [undefined, undefined, window.__kodaSignals, undefined]);
                }).catch(function (err) { report(null, String(err)); });
              } catch (err) { report(null, String(err)); }

              window.__kodaMint = function (integrityToken, binding) {
                var done = function (v, e) {
                  try { KodaBg.onMint(v || null, e || null); } catch (x) {}
                };
                try {
                  var getMinter = window.__kodaMinter;
                  if (!getMinter) { done(null, 'no minter'); return; }
                  var raw = atob(integrityToken.replace(/-/g, '+').replace(/_/g, '/'));
                  var itBytes = new Uint8Array(raw.length);
                  for (var i = 0; i < raw.length; i++) itBytes[i] = raw.charCodeAt(i);
                  Promise.resolve(getMinter(itBytes)).then(function (mintCb) {
                    if (typeof mintCb !== 'function') { done(null, 'minter not a function'); return; }
                    return Promise.resolve(mintCb(new TextEncoder().encode(binding)))
                      .then(function (out) {
                        if (!out) { done(null, 'empty mint'); return; }
                        var bytes = new Uint8Array(out);
                        var s = '';
                        for (var j = 0; j < bytes.length; j++) s += String.fromCharCode(bytes[j]);
                        done(btoa(s).replace(/\+/g, '-').replace(/\//g, '_'), null);
                      });
                  }).catch(function (err) { done(null, String(err)); });
                } catch (err) { done(null, String(err)); }
              };
            })();
        """.trimIndent()
    }
}
