package com.ivor.ivormusic.data

/**
 * Asking Google's image hosts for an image the size it will actually be drawn.
 *
 * InnerTube hands out avatars and covers at whatever size the surface it was
 * built for needed - a search result's channel avatar comes back at `=s48` or
 * `=s88`, a playlist cover at `w120-h120`. Koda draws several of those at
 * 140dp, which is 420 physical pixels on a 3x screen, so the picture is being
 * blown up five to nine times and looks exactly as bad as that sounds.
 *
 * **The size directive only lives after the last `=`.** [Song] learned this the
 * hard way: the opaque image token before the separator can itself contain
 * `s<digits>` runs, so an unanchored replace over the whole URL corrupts it
 * into a permanent 404. Everything here splits on that separator first.
 *
 * **A larger request is a request, not a guarantee.** Google serves the
 * nearest size it has and a channel that never uploaded a large avatar simply
 * returns a smaller image, so the upscaled URL is always drawn *over* the
 * original rather than instead of it - the layering `VideoThumbnail` and
 * `SongArtwork` already use, and the reason a miss costs sharpness rather than
 * an empty plate.
 */

/** Size directives Google's image URLs use, e.g. `s48`, `w120-h120`. */
private val SIZE_WH = Regex("w\\d+-h\\d+")
private val SIZE_S = Regex("s\\d+")

/**
 * The same URL asking for [px] pixels, or the URL unchanged when it is not a
 * Google-hosted image whose size we know how to rewrite.
 */
fun googleImageAtSize(url: String?, px: Int): String? {
    val source = url?.takeIf { it.isNotBlank() } ?: return null
    if (!source.contains("googleusercontent.com") && !source.contains("ggpht.com")) {
        return source
    }
    val separator = source.lastIndexOf('=')
    if (separator < 0) return source
    val directives = source.substring(separator)
        .replace(SIZE_WH, "w$px-h$px")
        .replace(SIZE_S, "s$px")
    return source.substring(0, separator) + directives
}

/**
 * A creator's avatar at a size worth drawing large.
 *
 * 512 rather than 1080: an avatar is square and is never drawn above ~180dp
 * anywhere in the app, so 512 covers a 3x screen with room to spare while
 * asking for a quarter of the bytes a cover-art request costs.
 */
const val AVATAR_TARGET_PX = 512

/** [ArtistItem.thumbnailUrl] at [AVATAR_TARGET_PX]; null when there is none. */
val ArtistItem.highResThumbnailUrl: String?
    get() = googleImageAtSize(thumbnailUrl, AVATAR_TARGET_PX)
