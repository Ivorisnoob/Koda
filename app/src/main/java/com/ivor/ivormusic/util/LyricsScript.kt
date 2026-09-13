package com.ivor.ivormusic.util

/**
 * Whether a song's lyrics can be set in the app's display family (Montserrat)
 * without losing letters.
 *
 * **Lyrics are the one text in the app that is not in the user's language.**
 * A Korean release, a Hindi film song, an Arabic or Japanese track all arrive as
 * the body text of a screen whose chrome is English - and Montserrat publishes
 * Latin, Latin Extended, Vietnamese and Cyrillic, and nothing else. Asking for
 * it unconditionally bets the legibility of those songs on a platform fallback
 * that is neither promised nor visible in review: the failure is a screen of
 * boxes, on exactly the songs nobody testing in English would open.
 *
 * So the decision is made from the lyrics themselves, **once per song rather
 * than per line**: a mixed verse-and-chorus track would otherwise change
 * typeface as it scrolled, and the metrics shift is plainly worse than either
 * font alone.
 *
 * **A share rather than a single character**, because one stray quote, one CJK
 * bracket or one borrowed word would otherwise demote a whole English song -
 * and for a handful of glyphs a platform fallback is a bonus rather than
 * something being relied on. Only letters are counted: digits, punctuation and
 * symbols say nothing about which script a lyric is written in.
 */
fun lyricsFitDisplayFont(lines: List<String>): Boolean {
    var letters = 0
    var uncovered = 0
    for (line in lines) {
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            index += Character.charCount(codePoint)
            if (!Character.isLetter(codePoint)) continue
            letters++
            if (!isDisplayFontLetter(codePoint)) uncovered++
        }
    }
    // No letters at all is an instrumental marker or a line of dashes; either
    // font draws it identically, so take the nicer one.
    if (letters == 0) return true
    return uncovered.toFloat() / letters <= MAX_UNCOVERED_SHARE
}

/** Past this share of letters the display family would be drawing holes. */
private const val MAX_UNCOVERED_SHARE = 0.08f

/**
 * The blocks Montserrat actually ships, as published on Google Fonts: Latin,
 * Latin Extended-A and -B, Latin Extended Additional (which is what covers
 * Vietnamese), and Cyrillic with its supplement. Greek, CJK, Hangul, Devanagari,
 * Arabic, Hebrew and Thai are all absent - re-derive this list before widening
 * it, rather than assuming a font that draws one accent draws a script.
 */
private fun isDisplayFontLetter(codePoint: Int): Boolean = when (codePoint) {
    in 0x0041..0x005A, in 0x0061..0x007A -> true
    in 0x00AA..0x024F -> true
    in 0x1E00..0x1EFF -> true
    in 0x0400..0x052F -> true
    else -> false
}
