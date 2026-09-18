/*
 * OkHttp edge for attestation bootstrapping: the home page, the BotGuard
 * interpreter and the GenerateIT integrity call. Values mirror PipePipe's
 * LocalDomPoTokenProvider (GPL-3.0, commit 08b277619ac05a5b227ca53a7fe4cb1958663c4d):
 * same endpoints, same Windows UA, same public Google API key any client sends.
 * No caching, no cookies stored - every call carries what the minter passes in.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.ivor.ivormusic.data.youtube.sabr.session

import com.ivor.ivormusic.data.youtube.sabr.exception.SabrProtocolException
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class SabrAttestationHttp(private val calls: Call.Factory) : SabrAttestationTransport {
    override fun getHome(cookies: String?): String {
        val request = Request.Builder()
            .url("https://www.youtube.com/")
            .get()
            .header("User-Agent", SABR_PAGE_USER_AGENT)
            .header("Accept-Language", "en-US")
            .header("Cookie", cookies?.takeIf { it.isNotBlank() } ?: ANONYMOUS_COOKIE)
            .build()
        calls.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SabrProtocolException("YouTube home failed: ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw SabrProtocolException("YouTube home is empty")
        }
    }

    override fun getInterpreter(url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", SABR_PAGE_USER_AGENT)
            .header("Accept", "*/*")
            .build()
        calls.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SabrProtocolException("BotGuard interpreter failed: ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw SabrProtocolException("BotGuard interpreter is empty")
        }
    }

    override fun postIntegrity(body: String): String {
        val request = Request.Builder()
            .url("https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT")
            .post(body.toRequestBody("application/json+protobuf".toMediaType()))
            .header("User-Agent", SABR_PAGE_USER_AGENT)
            .header("Accept", "application/json")
            .header("x-goog-api-key", LOCAL_DOM_API_KEY)
            .header("x-user-agent", "grpc-web-javascript/0.1")
            .build()
        calls.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SabrProtocolException("Integrity request failed: ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw SabrProtocolException("Integrity response is empty")
        }
    }

    private companion object {
        const val ANONYMOUS_COOKIE = "PREF=hl=en&gl=US"
        // Google's public WebView attestation key - identical for every client.
        const val LOCAL_DOM_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
    }
}
