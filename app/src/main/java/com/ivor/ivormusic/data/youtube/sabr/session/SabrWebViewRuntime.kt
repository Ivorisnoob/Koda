/*
 * Headless page runtime for local-DOM PO token minting. Design adapted from
 * PipePipe (GPL-3.0), commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d
 * (SharedWebViewRuntime): app-scoped WebView, bridge readiness (never
 * onPageFinished - WebView 44/83 miss it headless), main-thread creation and
 * script posts, bounded ready retries.
 * Copyright the PipePipe contributors. See THIRD_PARTY_NOTICES.md.
 * Koda differences: Kotlin, KodaSabrBridge surface, KLog, renderer-gone resets
 * (the auth dialog's proven pattern - upstream has this gap), deleteSession
 * never blocks waiting for readiness, no androidx.webkit Safe Browsing toggle
 * (no such dependency; the page loads no remote content and network loads are
 * blocked, which is the restriction that matters). Scripts and token material
 * are never logged.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.session

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.ivor.ivormusic.BuildConfig
import com.ivor.ivormusic.util.KLog
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared, transport-equivalent page runtime (one per process, like OkHttp): a
 * headless WebView holding a local document on the youtube.com origin. Scripts
 * run locally; `setBlockNetworkLoads` plus DOM storage off keeps the page from
 * reaching the network or persisting anything. Never post from the main thread
 * when the caller then waits - every wait throws there instead of hanging.
 */
internal class SabrWebViewRuntime private constructor(appContext: Context) : SabrLocalDom {
    private val context = appContext.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initLock = Any()
    private var initLatch: CountDownLatch? = null
    private var initError: AtomicReference<Throwable>? = null
    private var attempt: InitializationAttempt? = null
    private var webView: WebView? = null
    @Volatile private var ready = false
    private var nextAttemptId = 0L
    private val callbacks = ConcurrentHashMap<String, SabrJsCallbacks>()
    @Volatile private var helper: String? = null

    override fun postScript(script: String, onError: (Throwable) -> Unit): Boolean {
        try {
            ensureReady(DEFAULT_TIMEOUT_MS, "async script post")
        } catch (error: Throwable) {
            onError(error)
            return false
        }
        return mainHandler.post {
            try {
                (webView ?: throw IllegalStateException("WebView runtime is gone"))
                    .evaluateJavascript(script, null)
            } catch (error: Throwable) {
                onError(error)
            }
        }
    }

    override fun registerCallbacks(sessionId: String, callbacks: SabrJsCallbacks): () -> Unit {
        this.callbacks[sessionId] = callbacks
        return { this.callbacks.remove(sessionId) }
    }

    override fun deleteSession(sessionId: String) {
        callbacks.remove(sessionId)
        val view = synchronized(initLock) { if (!ready) null else webView } ?: return
        mainHandler.post {
            runCatching { view.evaluateJavascript(sabrDeleteSessionScript(sessionId), null) }
        }
    }

    override fun helperScript(): String {
        helper?.let { return it }
        return loadAsset(HELPER_ASSET).also { helper = it }
    }

    fun warmUp() {
        synchronized(initLock) {
            if (ready || initLatch != null) return
            startInitializationLocked()
        }
    }

    private fun ensureReady(timeoutMs: Long, operation: String) {
        val latch: CountDownLatch
        val error: AtomicReference<Throwable>
        synchronized(initLock) {
            if (ready) return
            if (initLatch == null) startInitializationLocked()
            latch = initLatch
                ?: throw IllegalStateException("$operation did not start WebView initialization")
            error = initError
                ?: throw IllegalStateException("$operation did not start WebView initialization")
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("$operation cannot wait on the main thread")
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw IllegalStateException("$operation timed out waiting for the WebView runtime")
        }
        error.get()?.let { throw IllegalStateException("$operation found no WebView runtime", it) }
    }

    private fun loadAsset(path: String): String {
        context.assets.open(path).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private fun startInitializationLocked() {
        val latch = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        val current = InitializationAttempt(++nextAttemptId, 1, latch, error)
        initLatch = latch
        initError = error
        attempt = current
        if (!mainHandler.post { createWebView(current) }) {
            failAttempt(current, IllegalStateException("Could not post WebView creation"))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(current: InitializationAttempt) {
        if (!isCurrent(current)) return
        try {
            val view = WebView(context)
            current.view = view
            if (!isCurrent(current)) {
                destroy(view)
                return
            }
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = false
            view.settings.userAgentString = PAGE_USER_AGENT
            view.settings.blockNetworkLoads = true
            view.addJavascriptInterface(Bridge(), BRIDGE_NAME)
            view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (BuildConfig.DEBUG) {
                        KLog.d(TAG, "console ${message.messageLevel()} ${message.message()}")
                    }
                    return true
                }
            }
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    KLog.d(TAG, "page finished attempt=${current.number}")
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        failAttempt(current, IllegalStateException(
                            "WebView main frame error ${error.errorCode}: ${error.description}"))
                    }
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?,
                ): Boolean {
                    // A renderer that exits is permanently unusable; drop the runtime
                    // back to unready (pending posts time out on their own budgets)
                    // and let the next ensureReady start over.
                    reset(view)
                    return true
                }
            }
            view.loadDataWithBaseURL(
                "https://www.youtube.com/", runtimeDocument(current.id),
                "text/html", "UTF-8", null)
            mainHandler.postDelayed({
                failAttempt(current, IllegalStateException(
                    "WebView runtime ready callback timed out"))
            }, READY_TIMEOUT_MS)
        } catch (error: Throwable) {
            failAttempt(current, error)
        }
    }

    private fun runtimeDocument(attemptId: Long): String =
        "<!doctype html><html><head><script>" +
            "KodaSabrBridge.onRuntimeDocumentReady('$attemptId');" +
            "</script><title></title></head><body></body></html>"

    private fun isCurrent(current: InitializationAttempt): Boolean = synchronized(initLock) {
        attempt === current && !ready
    }

    private fun complete(current: InitializationAttempt) {
        if (!current.completed.compareAndSet(false, true)) return
        synchronized(initLock) {
            if (attempt !== current || ready) {
                mainHandler.post { destroy(current.view) }
                return
            }
            webView = current.view
            ready = true
            attempt = null
        }
        KLog.d(TAG, "ready attempt=${current.number} elapsed=${current.elapsedMs()}")
        current.latch.countDown()
    }

    private fun failAttempt(current: InitializationAttempt, error: Throwable) {
        if (!current.completed.compareAndSet(false, true)) return
        mainHandler.post { destroy(current.view) }
        synchronized(initLock) {
            if (attempt !== current || ready) return
            if (current.number < MAX_ATTEMPTS) {
                val retry = InitializationAttempt(
                    ++nextAttemptId, current.number + 1, current.latch, current.error)
                attempt = retry
                KLog.w(TAG, "retrying WebView runtime after attempt ${current.number}", error)
                if (!mainHandler.post { createWebView(retry) }) {
                    failAttempt(retry, IllegalStateException("Could not post WebView retry"))
                }
                return
            }
            attempt = null
            current.error.compareAndSet(null, error)
        }
        KLog.w(TAG, "WebView runtime failed attempt=${current.number}", error)
        current.latch.countDown()
    }

    private fun reset(deadView: WebView?) {
        mainHandler.post { destroy(deadView) }
        synchronized(initLock) {
            webView = null
            ready = false
            initLatch = null
            initError = null
            attempt = null
        }
        KLog.w(TAG, "WebView renderer gone; runtime reset to unready")
    }

    private fun destroy(view: WebView?) {
        if (view == null) return
        runCatching {
            view.stopLoading()
            view.destroy()
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onRuntimeDocumentReady(attemptId: String) {
            mainHandler.post {
                val current: InitializationAttempt?
                synchronized(initLock) {
                    current = attempt
                    if (current == null || current.id.toString() != attemptId) return@post
                }
                current?.let { complete(it) }
            }
        }

        @JavascriptInterface
        fun onSabrJsError(sessionId: String, error: String) {
            callbacks[sessionId]?.onJsError(error)
        }

        @JavascriptInterface
        fun onSabrRunBotguardResult(sessionId: String, botguardResponse: String) {
            callbacks[sessionId]?.onBotguardResult(botguardResponse)
        }

        @JavascriptInterface
        fun onSabrMinterReady(sessionId: String) {
            callbacks[sessionId]?.onMinterReady()
        }

        @JavascriptInterface
        fun onSabrObtainPoTokenResult(sessionId: String, identifier: String, poTokenU8: String) {
            callbacks[sessionId]?.onObtainPoTokenResult(identifier, poTokenU8)
        }

        @JavascriptInterface
        fun onSabrObtainPoTokenError(sessionId: String, identifier: String, error: String) {
            callbacks[sessionId]?.onObtainPoTokenError(identifier, error)
        }
    }

    private class InitializationAttempt(
        val id: Long,
        val number: Int,
        val latch: CountDownLatch,
        val error: AtomicReference<Throwable>,
    ) {
        val completed = AtomicBoolean()
        private val startedAtMs = SystemClock.elapsedRealtime()
        var view: WebView? = null
        fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAtMs
    }

    companion object {
        private const val TAG = "SabrWebView"
        private const val BRIDGE_NAME = "KodaSabrBridge"
        private const val HELPER_ASSET = "koda_sabr_po_token.js"
        private const val PAGE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val READY_TIMEOUT_MS = 5_000L
        private const val MAX_ATTEMPTS = 2

        @Volatile private var instance: SabrWebViewRuntime? = null

        fun get(context: Context): SabrWebViewRuntime {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                return SabrWebViewRuntime(context).also { instance = it }
            }
        }
    }
}
