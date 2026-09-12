package com.ivor.ivormusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThumbnailUrlsTest {

    // The widest immersive banner from the artist_header fixture.
    private val banner = "https://lh3.googleusercontent.com/tQC4rOL6xz6FhmFr0ggQExxyGbYSOsyveXVSnPBh2WjEyIzQ9pMHablLJ-0GlMBrLBlBrbWQGmzrV6KN=w2880-h1200-p-l90-rj"

    @Test
    fun `crop rewrites only the size and keeps the crop flags`() {
        assertEquals(
            "https://lh3.googleusercontent.com/tQC4rOL6xz6FhmFr0ggQExxyGbYSOsyveXVSnPBh2WjEyIzQ9pMHablLJ-0GlMBrLBlBrbWQGmzrV6KN=w1080-h1037-p-l90-rj",
            googleImageCropped(banner, 1080, 1037)
        )
    }

    @Test
    fun `crop leaves a size-looking run in the token alone`() {
        val url = "https://lh3.googleusercontent.com/abcw12-h34xyz=w540-h225-p-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abcw12-h34xyz=w300-h400-p-l90-rj",
            googleImageCropped(url, 300, 400)
        )
    }

    @Test
    fun `crop returns urls it cannot rewrite unchanged`() {
        val square = "https://yt3.ggpht.com/avatar=s88-c-k-c0x00ffffff-no-rj"
        assertEquals(square, googleImageCropped(square, 300, 400))
        val ytimg = "https://i.ytimg.com/vi/abc/hqdefault.jpg"
        assertEquals(ytimg, googleImageCropped(ytimg, 300, 400))
        assertNull(googleImageCropped(null, 300, 400))
        assertNull(googleImageCropped(" ", 300, 400))
    }

    @Test
    fun `crop never asks for a zero-sized box`() {
        assertEquals(
            "https://lh3.googleusercontent.com/t=w1-h1-p",
            googleImageCropped("https://lh3.googleusercontent.com/t=w540-h225-p", 0, 0)
        )
    }
}
