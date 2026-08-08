package com.ivor.ivormusic.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Fuzzy matching for searching a list the app is already holding in memory.
 *
 * Written for settings search and lifted here unchanged the moment a second
 * caller wanted it. Channel names are exactly the input that needs all of it:
 * they are long, they carry punctuation and emoji, and people remember them
 * approximately. A `contains()` is regretted the first time somebody types a
 * name slightly wrong.
 *
 * Nothing here touches the network or the disk. It is a scorer over strings,
 * cheap enough to run over a few hundred candidates on every keystroke.
 */

/** One field of a candidate, and how much a hit inside it counts. */
class MatchField(val text: String, val weight: Int = 1)

/** Lowercase, with every non-alphanumeric character flattened to a space. */
fun normalizeForSearch(text: String): String =
    text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")

/** Levenshtein distance, bailing out once it cannot beat [limit]. */
private fun editDistance(a: String, b: String, limit: Int): Int {
    if (a == b) return 0
    if (abs(a.length - b.length) > limit) return limit + 1
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        var best = current[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = min(
                min(current[j - 1] + 1, previous[j] + 1),
                previous[j - 1] + cost
            )
            best = min(best, current[j])
        }
        if (best > limit) return limit + 1
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

/** True when every character of [token] appears in [word], in order. */
private fun isSubsequence(token: String, word: String): Boolean {
    var t = 0
    for (c in word) {
        if (t < token.length && token[t] == c) t++
    }
    return t == token.length
}

/**
 * Scores one token against one already-normalized field. Higher is better,
 * null means no match.
 *
 * The tiers are deliberate: a word that starts with what you typed is what you
 * meant far more often than a word that merely contains it, and a typo match
 * should never outrank a real one.
 */
private fun scoreToken(token: String, field: String): Int? {
    if (token.isEmpty()) return null
    val words = field.split(' ').filter { it.isNotEmpty() }

    if (field.startsWith(token)) return 120
    words.forEachIndexed { index, word ->
        if (word.startsWith(token)) return 100 - min(index, 8)
    }
    if (field.contains(token)) return 70

    // "amld" -> "amoled": compact subsequences beat scattered ones.
    words.forEach { word ->
        if (token.length >= 3 && isSubsequence(token, word)) {
            return max(30, 55 - (word.length - token.length))
        }
    }

    // Outright typos: "quailty" -> "quality".
    if (token.length >= 4) {
        val limit = if (token.length <= 5) 1 else 2
        words.forEach { word ->
            if (word.length >= 3 && editDistance(token, word, limit) <= limit) return 35
        }
    }
    return null
}

/**
 * Scores a whole query against one candidate's [fields], or null if any token
 * fails to match anywhere.
 *
 * Every token must land somewhere - an AND, not an OR, because "cache size"
 * returning everything that mentions "size" is noise. The first field is the
 * candidate's primary one (its title, its name), and a short primary gets a
 * small nudge up, so an exact-ish hit on "Shorts" beats the same hit buried in
 * a longer name.
 */
fun fuzzyScore(query: String, vararg fields: MatchField): Int? {
    val tokens = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty() || fields.isEmpty()) return null

    val normalized = fields.map { normalizeForSearch(it.text) to it.weight }

    var total = 0
    for (token in tokens) {
        val best = normalized.mapNotNull { (text, weight) ->
            scoreToken(token, text)?.let { it * weight }
        }.maxOrNull() ?: return null
        total += best
    }
    return total - normalized.first().first.length / 8
}
