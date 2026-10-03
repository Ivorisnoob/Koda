package com.ivor.ivormusic.data

import org.json.JSONObject

/**
 * The seek-preview storyboard a `/player` response carries, as the largest
 * usable frameset, or null when there is none.
 *
 * NewPipe used to supply this through `StreamExtractor.frames`, parsed from the
 * same `storyboards` object of the response it was given. Resolving streams with
 * Koda's own visionOS request means parsing it here, and the spec format is
 * NewPipe's to follow: ported from `YoutubeStreamExtractor.getFrames()` in
 * NewPipe Extractor v0.26.5 (GPL-3.0, TeamNewPipe), so the previews match what
 * the NewPipe path produced frame for frame.
 *
 * The spec is `baseUrl|level0|level1|...`, each level eight `#`-separated
 * fields: frame width, frame height, total frames, columns, rows, milliseconds
 * per frame, the `$N` name and the `sigh` signature. `$L` in the base URL is
 * the level index and `$M` the page. A level with a zero frame duration is a
 * placeholder YouTube does not serve. A live spec
 * (`playerLiveStoryboardSpecRenderer`) has a different, shorter shape and
 * yields nothing, exactly as it did through NewPipe. [verified September 2026:
 * visionOS returns `playerStoryboardSpecRenderer` on VODs and
 * `playerLiveStoryboardSpecRenderer` on a live stream.]
 */
internal fun parseStoryboardSeekPreview(playerResponse: JSONObject): VideoSeekPreview? {
    val storyboards = playerResponse.optJSONObject("storyboards") ?: return null
    val renderer = storyboards.optJSONObject("playerLiveStoryboardSpecRenderer")
        ?: storyboards.optJSONObject("playerStoryboardSpecRenderer")
        ?: return null
    val spec = renderer.optString("spec").takeIf(String::isNotBlank) ?: return null
    val levels = spec.split('|')
    val baseUrl = levels.first()

    return levels.drop(1).mapIndexedNotNull { index, level ->
        val parts = level.split('#')
        if (parts.size != 8) return@mapIndexedNotNull null
        val numbers = parts.subList(0, 6).map { it.toIntOrNull() ?: return@mapIndexedNotNull null }
        val (frameWidth, frameHeight, totalCount, perPageX, perPageY) = numbers
        val durationPerFrame = numbers[5]
        if (durationPerFrame == 0) return@mapIndexedNotNull null
        val levelUrl = baseUrl.replace("\$L", index.toString())
            .replace("\$N", parts[6]) + "&sigh=" + parts[7]
        val pageUrls = if ("\$M" in levelUrl && perPageX > 0 && perPageY > 0) {
            val pages = (totalCount + perPageX * perPageY - 1) / (perPageX * perPageY)
            (0 until pages).map { levelUrl.replace("\$M", it.toString()) }
        } else {
            listOf(levelUrl)
        }
        VideoSeekPreview(
            pageUrls = pageUrls,
            frameWidthPx = frameWidth,
            frameHeightPx = frameHeight,
            framesPerPageX = perPageX,
            framesPerPageY = perPageY,
            totalFrameCount = totalCount,
            durationPerFrameMs = durationPerFrame,
        )
    }
        // The NewPipe path's selection, unchanged: the sharpest usable level.
        .filter { it.isUsable }
        .maxByOrNull { it.frameWidthPx * it.frameHeightPx }
}
