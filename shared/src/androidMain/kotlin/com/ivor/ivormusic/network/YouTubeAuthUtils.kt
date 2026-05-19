package com.ivor.ivormusic.network

import java.security.MessageDigest

internal object YouTubeAuthUtils {

    fun getCookieValue(cookieString: String, cookieName: String): String? =
        cookieString.split(";")
            .map { it.trim().split("=") }
            .find { it.first() == cookieName }
            ?.getOrNull(1)

    fun getAuthorizationHeader(cookieString: String, origin: String = "https://music.youtube.com"): String? {
        val sapisid = getCookieValue(cookieString, "SAPISID") ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$hash"
    }
}
