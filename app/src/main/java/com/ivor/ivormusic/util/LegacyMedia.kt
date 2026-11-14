package com.ivor.ivormusic.util

import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build

/**
 * Media helpers whose platform versions arrived after Koda's minSdk (26).
 * Each takes the platform call where it exists and an equivalent made of
 * older calls below it, so the call sites stay one line.
 */

/**
 * `MediaFormat.getInteger(name, default)` is API 29; the one-argument form
 * throws when the key is absent, which is why the check comes first.
 */
fun MediaFormat.intOrDefault(name: String, default: Int): Int =
    if (containsKey(name)) getInteger(name) else default

/**
 * A frame scaled to fit inside [width] x [height], aspect kept.
 *
 * `getScaledFrameAtTime` is API 27. Below it the full frame is decoded and
 * scaled down here, which costs more memory for the moment it is held but
 * draws the same picture. A 4K frame is about 33 MB, so running out of memory
 * on an old phone answers null (no thumbnail) rather than reaching the call
 * sites, whose `catch (Exception)` does not cover an `OutOfMemoryError`.
 */
fun MediaMetadataRetriever.scaledFrameAtTimeCompat(
    timeUs: Long,
    option: Int,
    width: Int,
    height: Int,
): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        return getScaledFrameAtTime(timeUs, option, width, height)
    }
    val frame = try {
        getFrameAtTime(timeUs, option)
    } catch (_: OutOfMemoryError) {
        null
    } ?: return null
    if (width <= 0 || height <= 0 || frame.width <= 0 || frame.height <= 0) return frame
    val scale = minOf(width.toFloat() / frame.width, height.toFloat() / frame.height)
    if (scale >= 1f) return frame
    return try {
        Bitmap.createScaledBitmap(
            frame,
            (frame.width * scale).toInt().coerceAtLeast(1),
            (frame.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { scaled -> if (scaled !== frame) frame.recycle() }
    } catch (_: OutOfMemoryError) {
        frame.recycle()
        null
    }
}
