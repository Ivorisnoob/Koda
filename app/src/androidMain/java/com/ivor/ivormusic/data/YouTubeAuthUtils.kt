package com.ivor.ivormusic.data

import java.security.MessageDigest

/**
 * Utility for generating YouTube Music internal API authorization headers.
 */
object YouTubeAuthUtils {

    fun getCookieValue(cookieString: String, cookieName: String): String? {
        return cookieString.split(";")
            .map { it.trim().split("=") }
            .find { it.first() == cookieName }
            ?.getOrNull(1)
    }

    /**
     * Generates the SAPISIDHASH required for authenticated requests.
     */
    fun getAuthorizationHeader(cookieString: String, origin: String = "https://music.youtube.com"): String? {
        val sapisid = getCookieValue(cookieString, "SAPISID") ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        
        // The hash format is: timestamp + space + sapisid + space + origin
        val input = "$timestamp $sapisid $origin"
        
        // SHA-1 Hashing
        val digest = MessageDigest.getInstance("SHA-1")
        val bytes = digest.digest(input.toByteArray())
        val hash = bytes.joinToString("") { "%02x".format(it) }

        return "SAPISIDHASH ${timestamp}_${hash}"
    }
}
