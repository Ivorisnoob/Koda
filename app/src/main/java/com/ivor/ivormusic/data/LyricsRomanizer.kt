package com.ivor.ivormusic.data

import android.icu.text.Transliterator

/**
 * Latin-letter readings of lyric lines, made on the device by Android's own
 * ICU transliterator: nothing is downloaded and no provider has to carry them.
 *
 * ICU reads every Han character as Mandarin, which is right for Chinese and
 * wrong for Japanese kanji (君 would come out as "jūn", not "kimi"). Kanji has
 * more than one reading and only a dictionary can choose, so a song with any
 * kana keeps its kanji as written rather than showing a confident wrong
 * reading. ICU also reads the particle は as "ha" for the same reason.
 */
object LyricsRomanizer {
    private const val ALL_SCRIPTS = "Any-Latin; NFC"
    private const val EXCEPT_HAN = "[^\\p{Han}] Any-Latin; NFC"

    /**
     * One entry per line of [lines]: its reading, or null where there is
     * nothing to add (a line already in Latin letters, or one ICU left as it
     * was). Slow enough on a long song to belong off the main thread.
     */
    fun romanize(lines: List<String>): List<String?> {
        if (lines.none(::needsRomanizing)) return lines.map { null }
        // android.icu.text.Transliterator is API 29: older phones keep the
        // lyrics as they are, the same as a device without these rules.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return lines.map { null }
        val japanese = lines.any(::hasKana)
        val transliterator = try {
            Transliterator.getInstance(if (japanese) EXCEPT_HAN else ALL_SCRIPTS)
        } catch (e: IllegalArgumentException) {
            // A device without these rules keeps the lyrics as they are.
            return lines.map { null }
        }
        return lines.map { line ->
            if (needsRomanizing(line)) {
                transliterator.transliterate(line).trim().takeIf { it.isNotBlank() && it != line.trim() }
            } else {
                null
            }
        }
    }

    internal fun needsRomanizing(line: String): Boolean =
        line.codePoints().anyMatch { codePoint ->
            Character.isLetter(codePoint) &&
                Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN
        }

    private fun hasKana(line: String): Boolean =
        line.codePoints().anyMatch { codePoint ->
            val script = Character.UnicodeScript.of(codePoint)
            script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
        }
}
