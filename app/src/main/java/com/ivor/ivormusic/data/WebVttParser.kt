package com.ivor.ivormusic.data

/**
 * One subtitle cue: [text] is on screen from [startMs] to [endMs] of media time.
 */
data class VttCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Minimal WebVTT reader for YouTube's timedtext output.
 *
 * Koda renders captions itself rather than handing them to ExoPlayer as a
 * sideloaded text track: a sideloaded track is part of the media source, so
 * every caption change rebuilt the source and threw away the whole video
 * buffer. Parsing here keeps the CC toggle free and instant.
 *
 * Only what timedtext actually emits is supported - cue timings, multi-line
 * payloads, and the inline markup auto-generated captions carry. Cue settings
 * (alignment, position, region) are read and discarded; captions are laid out
 * by the overlay instead.
 */
object WebVttParser {

    private val TIMESTAMP = Regex("""(\d+):(\d{2})(?::(\d{2}))?[.,](\d{1,3})""")

    /** Inline markup: <c>, </c>, <b>, and the <00:00:01.234> word timings. */
    private val TAG = Regex("<[^>]*>")

    fun parse(vtt: String): List<VttCue> {
        val lines = vtt.lines()
        val cues = mutableListOf<VttCue>()
        var i = 0

        while (i < lines.size) {
            val arrow = lines[i].indexOf("-->")
            if (arrow < 0) {
                i++
                continue
            }
            val start = parseTimestamp(lines[i].substring(0, arrow))
            val end = parseTimestamp(lines[i].substring(arrow + 3))
            i++

            // The payload runs to the next blank line, whether or not the
            // timings above it parsed.
            val payload = buildString {
                while (i < lines.size && lines[i].isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(lines[i])
                    i++
                }
            }
            if (start == null || end == null || end <= start) continue

            val text = cleanCueText(payload)
            if (text.isEmpty()) continue

            // Auto-generated captions repeat a line across back-to-back cues as
            // it scrolls. Extending the previous cue instead of adding a
            // duplicate stops the overlay flickering on every hand-off.
            val previous = cues.lastOrNull()
            if (previous != null && previous.text == text && start <= previous.endMs) {
                cues[cues.lastIndex] = previous.copy(endMs = maxOf(previous.endMs, end))
            } else {
                cues.add(VttCue(start, end, text))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    /**
     * Milliseconds from the first timestamp in [raw], which is either
     * HH:MM:SS.mmm or MM:SS.mmm. Anything after it (cue settings such as
     * "align:start position:0%") is ignored.
     */
    private fun parseTimestamp(raw: String): Long? {
        val match = TIMESTAMP.find(raw) ?: return null
        val (first, second, third, fraction) = match.destructured
        val millis = fraction.padEnd(3, '0').toLong()
        return if (third.isEmpty()) {
            first.toLong() * 60_000 + second.toLong() * 1_000 + millis
        } else {
            first.toLong() * 3_600_000 + second.toLong() * 60_000 + third.toLong() * 1_000 + millis
        }
    }

    /**
     * Strip markup first, then decode entities - the other order would turn an
     * escaped "&lt;i&gt;" into a tag and delete the text inside it.
     */
    private fun cleanCueText(raw: String): String =
        TAG.replace(raw, "")
            .replace("&lrm;", "")
            .replace("&rlm;", "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    /**
     * The cue covering [positionMs], or null when there is a gap. Cues are
     * ordered and non-overlapping in practice, so a binary search would work,
     * but lists are small and playback only asks a few times a second.
     */
    fun cueAt(cues: List<VttCue>, positionMs: Long): VttCue? =
        cues.firstOrNull { positionMs in it.startMs until it.endMs }
}
