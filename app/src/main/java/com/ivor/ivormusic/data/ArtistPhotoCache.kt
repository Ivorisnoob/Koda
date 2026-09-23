package com.ivor.ivormusic.data

import android.content.Context

/**
 * Artist name -> photo url, for Home's top artists rail.
 *
 * The play history knows an artist only by name, and a photo costs an artist
 * search, so each name is looked up once and remembered: a hit for 30 days
 * (photos change rarely), a miss for 7 (so an obscure name is not searched on
 * every Home visit, but a newly indexed one turns up eventually).
 *
 * A device-wide cache, not user data: it is not in `BackupRepository`'s
 * allowlist and needs no profile scoping, since a photo of an artist is the
 * same photo for every account.
 */
class ArtistPhotoCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** What is known about [name]: a url, a remembered miss, or nothing yet. */
    sealed interface Lookup {
        data class Photo(val url: String) : Lookup
        data object Miss : Lookup
        data object Unknown : Lookup
    }

    fun lookup(name: String, now: Long = System.currentTimeMillis()): Lookup {
        val raw = prefs.getString(key(name), null) ?: return Lookup.Unknown
        val stamp = raw.substringBefore('|').toLongOrNull() ?: return Lookup.Unknown
        val url = raw.substringAfter('|', "")
        val ttl = if (url.isEmpty()) MISS_TTL_MS else HIT_TTL_MS
        if (now - stamp > ttl) return Lookup.Unknown
        return if (url.isEmpty()) Lookup.Miss else Lookup.Photo(url)
    }

    fun put(name: String, url: String?, now: Long = System.currentTimeMillis()) {
        prefs.edit().putString(key(name), "$now|${url.orEmpty()}").apply()
    }

    private fun key(name: String) = name.trim().lowercase()

    private companion object {
        const val PREFS_NAME = "artist_photo_cache"
        const val HIT_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
