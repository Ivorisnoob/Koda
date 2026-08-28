package com.ivor.ivormusic.ui.auth
import androidx.compose.ui.res.stringResource
import com.ivor.ivormusic.R

import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.ivor.ivormusic.data.AccountSwitcher
import com.ivor.ivormusic.data.SessionManager
import com.ivor.ivormusic.data.YouTubeAuthUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeAuthDialog(
    onDismiss: () -> Unit,
    onAuthSuccess: () -> Unit,
    addAsNewProfile: Boolean = false
) {
    val context = LocalContext.current
    val sessionManager = SessionManager(context)
    val accountSwitcher = AccountSwitcher(context)
    var webViewGeneration by remember { mutableIntStateOf(0) }
    var rendererFailed by remember { mutableStateOf(false) }
    
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.au_sign_in_title),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // WebView
                Box(modifier = Modifier.weight(1f)) {
                    if (rendererFailed) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.au_sign_in_renderer_failed),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                            Button(onClick = {
                                rendererFailed = false
                                webViewGeneration++
                            }) {
                                Text(stringResource(R.string.action_try_again))
                            }
                        }
                    } else key(webViewGeneration) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        private var completed = false

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            if (completed) return
                                        // Always read the youtube.com cookie jar, NOT the current
                                        // page's. Mid-login pages live on accounts.google.com whose
                                        // jar also contains a SAPISID — saving that jar produces a
                                        // "logged in" state that every YouTube endpoint rejects as
                                        // anonymous (no feed, no avatar, login loops).
                                            val cookies = CookieManager.getInstance()
                                                .getCookie("https://music.youtube.com")
                                        // Exact cookie names, not substrings: "SID="
                                        // also matches SAPISID=, APISID= and
                                        // __Secure-3PSID=, so the old check passed on
                                        // jars that had no SID session at all and saved
                                        // a login that could never authenticate.
                                            if (cookies != null &&
                                                YouTubeAuthUtils.missingRequiredCookies(cookies).isEmpty()
                                            ) {
                                                completed = true
                                            // A regular sign-in connects or repairs the active
                                            // profile. "Add account" must instead preserve that
                                            // profile and put the captured session in a new one.
                                            // Sending both paths through startSession used to
                                            // overwrite the active account's stored cookies.
                                                if (addAsNewProfile) {
                                                    accountSwitcher.addYouTubeProfileAndSwitch(cookies)
                                                } else {
                                                    sessionManager.startSession(cookies)
                                                }
                                            // Without this the jar only reaches disk on
                                            // WebView's own schedule, so a process death
                                            // right after signing in left nothing to
                                            // refresh the session from later.
                                                CookieManager.getInstance().flush()
                                                onAuthSuccess()
                                            }
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView?,
                                            detail: RenderProcessGoneDetail?
                                        ): Boolean {
                                            // A WebView whose renderer exited is permanently
                                            // unusable. Remove and destroy it before Compose
                                            // creates a replacement; returning true prevents
                                            // WebView's default process-killing behaviour.
                                            view?.let { deadView ->
                                                (deadView.parent as? ViewGroup)?.removeView(deadView)
                                                runCatching { deadView.destroy() }
                                            }
                                            rendererFailed = true
                                            return true
                                        }
                                    }
                                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com")
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { webView ->
                                webView.stopLoading()
                                webView.removeAllViews()
                                runCatching { webView.destroy() }
                            }
                        )
                    }
                }
            }
        }
    }
}
