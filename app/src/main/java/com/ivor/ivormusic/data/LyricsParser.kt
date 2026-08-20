package com.ivor.ivormusic.data

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.math.roundToLong

/** Converts every supported provider format into the one model consumed by the player. */
internal object LyricsParser {
    private val lrcLineTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val enhancedWordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val qrcLine = Regex("""^\[(\d{1,9}),(\d{1,9})](.*)$""")
    private val qrcWord = Regex("""(.*?)\((\d{1,9}),(\d{1,9})(?:,\d{1,9})?\)""")
    private val metadataLine = Regex("""^\[[A-Za-z]{1,12}:.*]$""")
    private val whitespace = Regex("""\s+""")

    fun parse(content: String): ParsedLyrics? {
        val normalized = normalize(content)
        if (normalized.isBlank()) return null

        return when {
            isTtml(normalized) -> parseTtml(normalized)
            isQrc(normalized) -> parseQrc(normalized)
            lrcLineTag.containsMatchIn(normalized) -> parseLrc(normalized)
            normalized.startsWith("<") || normalized.startsWith("{") || normalized.startsWith("[") -> null
            else -> parsePlain(normalized)
        }?.takeIf { parsed -> parsed.lines.any { it.text.isNotBlank() } }
    }

    private fun normalize(content: String): String =
        content
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("&apos;", "'")
            .trim()

    private fun isTtml(content: String): Boolean =
        content.startsWith("<", ignoreCase = true) &&
            (content.contains("<tt", ignoreCase = true) ||
                content.contains("http://www.w3.org/ns/ttml", ignoreCase = true))

    private fun isQrc(content: String): Boolean =
        content.contains("<QrcInfos", ignoreCase = true) ||
            content.contains("LyricContent=", ignoreCase = true) ||
            content.lineSequence().any { line ->
                qrcLine.matchEntire(line.trim())?.groupValues?.getOrNull(3)?.let(qrcWord::containsMatchIn) == true
            }

    fun parseLrc(content: String): ParsedLyrics? {
        val lines = mutableListOf<LrcLine>()
        var hasWordTiming = false

        normalize(content).lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || metadataLine.matches(line)) return@forEach

            val occurrenceTimes = mutableListOf<Long>()
            var contentStart = 0
            while (true) {
                val match = lrcLineTag.matchAt(line, contentStart) ?: break
                occurrenceTimes += lrcTime(match.groupValues[1], match.groupValues[2], match.groupValues[3])
                contentStart = match.range.last + 1
                while (contentStart < line.length && line[contentStart].isWhitespace()) contentStart++
            }
            if (occurrenceTimes.isEmpty() || contentStart >= line.length) return@forEach

            val lyricText = line.substring(contentStart)
            val wordMatches = enhancedWordTag.findAll(lyricText).toList()
            if (wordMatches.isEmpty()) {
                val text = lyricText.trim()
                if (text.isNotEmpty()) occurrenceTimes.forEach { lines += LrcLine(it, text) }
                return@forEach
            }

            val primaryTime = occurrenceTimes.first()
            val spans = mutableListOf<LrcContentSpan>()
            val firstTag = wordMatches.first()
            if (firstTag.range.first > 0) {
                val prefix = lyricText.substring(0, firstTag.range.first)
                if (prefix.isNotBlank()) {
                    spans += LrcContentSpan(
                        timeMs = primaryTime,
                        text = prefix,
                        durationMs = (wordTime(firstTag) - primaryTime).coerceAtLeast(0L)
                    )
                }
            }

            wordMatches.forEachIndexed { index, match ->
                val textStart = match.range.last + 1
                val textEnd = wordMatches.getOrNull(index + 1)?.range?.first ?: lyricText.length
                if (textStart >= textEnd) return@forEachIndexed
                val segment = lyricText.substring(textStart, textEnd)
                if (segment.isEmpty()) return@forEachIndexed
                val startMs = wordTime(match)
                val endMs = wordMatches.getOrNull(index + 1)?.let(::wordTime)
                spans += LrcContentSpan(
                    timeMs = startMs,
                    text = segment,
                    durationMs = endMs?.minus(startMs)?.coerceAtLeast(0L) ?: 0L
                )
            }

            val displayText = enhancedWordTag.replace(lyricText, "").trim()
            if (displayText.isBlank()) return@forEach
            hasWordTiming = hasWordTiming || spans.isNotEmpty()
            lines += LrcLine(primaryTime, displayText, spans)
            occurrenceTimes.drop(1).forEach { lines += LrcLine(it, displayText) }
        }

        if (lines.isEmpty()) return null
        val completed = completeDurations(lines)
        return ParsedLyrics(
            lines = completed,
            syncType = if (hasWordTiming) LyricsSyncType.WORD else LyricsSyncType.LINE
        )
    }

    private fun parseQrc(content: String): ParsedLyrics? {
        val lyricContent = extractQrcContent(content)
        val parsed = mutableListOf<LrcLine>()

        lyricContent.lineSequence().forEach { rawLine ->
            val match = qrcLine.matchEntire(rawLine.trim()) ?: return@forEach
            val lineStart = match.groupValues[1].toLongOrNull() ?: return@forEach
            val body = match.groupValues[3]
            val spans = qrcWord.findAll(body).mapNotNull { wordMatch ->
                val text = wordMatch.groupValues[1]
                if (text.isEmpty()) return@mapNotNull null
                val start = wordMatch.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val duration = wordMatch.groupValues[3].toLongOrNull() ?: 0L
                LrcContentSpan(start, text, duration)
            }.toList()
            if (spans.isEmpty()) return@forEach
            parsed += LrcLine(
                timeMs = lineStart,
                text = spans.joinToString(separator = "") { it.text }.trim(),
                contentSpans = spans
            )
        }

        if (parsed.isEmpty()) return null
        return ParsedLyrics(completeDurations(parsed), LyricsSyncType.WORD)
    }

    private fun extractQrcContent(content: String): String {
        val marker = Regex("""LyricContent\s*=\s*\"""", RegexOption.IGNORE_CASE).find(content) ?: return content
        val start = marker.range.last + 1
        val end = content.indexOf('"', start)
        if (end < 0) return content
        return content.substring(start, end)
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun parseTtml(content: String): ParsedLyrics? {
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                runCatching { isXIncludeAware = false }
                runCatching { setExpandEntityReferences(false) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(content)))
            val root = document.documentElement
            val baseFrameRate = attributeEndingWith(root, "frameRate")
                ?.toDoubleOrNull()
                ?.takeIf { it > 0 }
                ?: 30.0
            val frameRateMultiplier = attributeEndingWith(root, "frameRateMultiplier")
                ?.split(whitespace)
                ?.mapNotNull { it.toDoubleOrNull() }
                ?.takeIf { it.size == 2 && it[1] != 0.0 }
                ?.let { it[0] / it[1] }
                ?: 1.0
            val frameRate = (baseFrameRate * frameRateMultiplier).coerceAtLeast(1.0)
            val tickRate = attributeEndingWith(root, "tickRate")
                ?.toDoubleOrNull()
                ?.takeIf { it > 0 }
                ?: frameRate
            val parsed = mutableListOf<LrcLine>()
            val elements = document.getElementsByTagName("*")

            for (index in 0 until elements.length) {
                val paragraph = elements.item(index) as? Element ?: continue
                if (!paragraph.tagName.endsWith("p", ignoreCase = true)) continue
                val begin = attributeEndingWith(paragraph, "begin") ?: continue
                val lineStart = parseTtmlTime(begin, tickRate, frameRate) ?: continue
                val lineEnd = resolveElementEnd(paragraph, lineStart, tickRate, frameRate)
                    .takeIf { it > lineStart }
                    ?: lineStart + 5_000L
                val timedElements = mutableListOf<Element>()
                collectTimedLeafSpans(paragraph, timedElements)
                val rawSpans = timedElements.mapNotNull { span ->
                    val text = normalizeSpanText(span.textContent)
                    if (text.isBlank()) return@mapNotNull null
                    val rawSpanStart = attributeEndingWith(span, "begin")
                        ?.let { parseTtmlTime(it, tickRate, frameRate) }
                        ?: lineStart
                    val spanStart = normalizeChildTime(rawSpanStart, lineStart, lineEnd, lineStart)
                    val rawSpanEnd = resolveElementEnd(span, rawSpanStart, tickRate, frameRate)
                    val spanEnd = normalizeChildTime(rawSpanEnd, lineStart, lineEnd, lineEnd)
                        .coerceAtLeast(spanStart)
                    LrcContentSpan(
                        timeMs = spanStart,
                        text = text,
                        durationMs = (spanEnd - spanStart).coerceAtLeast(0L)
                    )
                }
                val wordSpans = repairWordSpacing(rawSpans)
                val text = if (wordSpans.isNotEmpty()) {
                    wordSpans.joinToString(separator = "") { it.text }.replace(whitespace, " ").trim()
                } else {
                    paragraph.textContent.replace(whitespace, " ").trim()
                }
                if (text.isBlank()) continue
                parsed += LrcLine(
                    timeMs = lineStart,
                    text = text,
                    contentSpans = wordSpans
                )
            }

            if (parsed.isEmpty()) null else {
                val completed = completeDurations(parsed)
                ParsedLyrics(
                    lines = completed,
                    syncType = if (completed.any { it.contentSpans.isNotEmpty() }) LyricsSyncType.WORD else LyricsSyncType.LINE
                )
            }
        }.getOrNull()
    }

    private fun collectTimedLeafSpans(parent: Element, result: MutableList<Element>) {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            val isSpan = child.tagName.endsWith("span", ignoreCase = true)
            val hasTiming = attributeEndingWith(child, "begin") != null
            val hasTimedSpanChild = hasTimedSpanDescendant(child)
            if (isSpan && hasTiming && !hasTimedSpanChild) {
                result += child
            } else {
                collectTimedLeafSpans(child, result)
            }
        }
    }

    private fun hasTimedSpanDescendant(element: Element): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName.endsWith("span", ignoreCase = true) && attributeEndingWith(child, "begin") != null) {
                return true
            }
            if (hasTimedSpanDescendant(child)) return true
        }
        return false
    }

    private fun resolveElementEnd(element: Element, startMs: Long, tickRate: Double, frameRate: Double): Long {
        attributeEndingWith(element, "end")?.let { parseTtmlTime(it, tickRate, frameRate) }?.let { return it }
        attributeEndingWith(element, "dur")?.let { parseTtmlTime(it, tickRate, frameRate) }?.let { return startMs + it }
        return startMs
    }

    private fun normalizeChildTime(raw: Long, lineStart: Long, lineEnd: Long, fallback: Long): Long {
        if (raw < 0L) return fallback
        val lineDuration = (lineEnd - lineStart).coerceAtLeast(0L)
        val isProbablyRelative = raw < lineStart - 250L && raw <= lineDuration + 1_000L
        val adjusted = if (isProbablyRelative) lineStart + raw else raw
        return adjusted.coerceIn(lineStart.coerceAtLeast(0L), lineEnd.coerceAtLeast(lineStart))
    }

    private fun attributeEndingWith(element: Element, suffix: String): String? {
        val attributes = element.attributes ?: return null
        for (index in 0 until attributes.length) {
            val item = attributes.item(index)
            if (item.nodeName.substringAfter(':').equals(suffix, ignoreCase = true)) {
                return item.nodeValue?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun parseTtmlTime(value: String, tickRate: Double, frameRate: Double): Long? {
        val time = value.trim().replace(';', ':')
        return when {
            time.endsWith("ms", ignoreCase = true) -> time.dropLast(2).toDoubleOrNull()?.roundToLong()
            time.endsWith("h", ignoreCase = true) -> time.dropLast(1).toDoubleOrNull()?.times(3_600_000.0)?.roundToLong()
            time.endsWith("m", ignoreCase = true) -> time.dropLast(1).toDoubleOrNull()?.times(60_000.0)?.roundToLong()
            time.endsWith("s", ignoreCase = true) -> time.dropLast(1).toDoubleOrNull()?.times(1_000.0)?.roundToLong()
            time.endsWith("t", ignoreCase = true) -> time.dropLast(1).toDoubleOrNull()?.div(tickRate)?.times(1_000.0)?.roundToLong()
            time.endsWith("f", ignoreCase = true) -> time.dropLast(1).toDoubleOrNull()?.div(frameRate)?.times(1_000.0)?.roundToLong()
            ':' in time -> {
                val parts = time.split(':')
                when (parts.size) {
                    2 -> {
                        val minutes = parts[0].toDoubleOrNull() ?: return null
                        val seconds = parts[1].toDoubleOrNull() ?: return null
                        ((minutes * 60.0 + seconds) * 1_000.0).roundToLong()
                    }
                    3 -> {
                        val hours = parts[0].toDoubleOrNull() ?: return null
                        val minutes = parts[1].toDoubleOrNull() ?: return null
                        val seconds = parts[2].toDoubleOrNull() ?: return null
                        ((hours * 3_600.0 + minutes * 60.0 + seconds) * 1_000.0).roundToLong()
                    }
                    4 -> {
                        val hours = parts[0].toDoubleOrNull() ?: return null
                        val minutes = parts[1].toDoubleOrNull() ?: return null
                        val seconds = parts[2].toDoubleOrNull() ?: return null
                        val frames = parts[3].toDoubleOrNull() ?: return null
                        ((hours * 3_600.0 + minutes * 60.0 + seconds + frames / frameRate) * 1_000.0).roundToLong()
                    }
                    else -> null
                }
            }
            else -> time.toDoubleOrNull()?.times(1_000.0)?.roundToLong()
        }
    }

    private fun normalizeSpanText(text: String): String {
        if (text.isEmpty()) return text
        val leading = text.first().isWhitespace()
        val trailing = text.last().isWhitespace()
        val core = text.replace(whitespace, " ").trim()
        if (core.isEmpty()) return ""
        return buildString {
            if (leading) append(' ')
            append(core)
            if (trailing) append(' ')
        }
    }

    private fun repairWordSpacing(spans: List<LrcContentSpan>): List<LrcContentSpan> {
        if (spans.size < 2) return spans
        return spans.mapIndexed { index, span ->
            if (index == 0) return@mapIndexed span
            val previous = spans[index - 1].text.lastOrNull()
            val current = span.text.firstOrNull()
            val needsSpace = previous?.isLetterOrDigit() == true && current?.isLetterOrDigit() == true &&
                !isCjk(previous) && !isCjk(current) && !span.text.startsWith(' ') && !spans[index - 1].text.endsWith(' ')
            if (needsSpace) span.copy(text = " ${span.text}") else span
        }
    }

    private fun isCjk(char: Char): Boolean =
        Character.UnicodeScript.of(char.code) in setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL
        )

    private fun parsePlain(content: String): ParsedLyrics? {
        val lines = content.lineSequence()
            .map { it.replace(whitespace, " ").trim() }
            .filter { it.isNotBlank() && !metadataLine.matches(it) }
            .map { LrcLine(timeMs = -1L, text = it) }
            .toList()
        return lines.takeIf { it.isNotEmpty() }?.let { ParsedLyrics(it, LyricsSyncType.PLAIN) }
    }

    private fun completeDurations(lines: List<LrcLine>): List<LrcLine> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
        return sorted.mapIndexed { lineIndex, line ->
            if (line.contentSpans.isEmpty()) return@mapIndexed line
            val nextLineTime = sorted.getOrNull(lineIndex + 1)?.timeMs?.takeIf { it > line.timeMs } ?: line.timeMs + 5_000L
            val sortedSpans = line.contentSpans.sortedBy { it.timeMs }
            val spans = sortedSpans.mapIndexed { spanIndex, span ->
                val fallbackEnd = sortedSpans.getOrNull(spanIndex + 1)?.timeMs ?: nextLineTime
                if (span.durationMs > 0L) span else span.copy(
                    durationMs = (fallbackEnd - span.timeMs).coerceIn(80L, 10_000L)
                )
            }
            line.copy(contentSpans = spans)
        }
    }

    private fun lrcTime(minutes: String, seconds: String, fraction: String): Long {
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return (minutes.toLongOrNull() ?: 0L) * 60_000L +
            (seconds.toLongOrNull() ?: 0L) * 1_000L + millis
    }

    private fun wordTime(match: MatchResult): Long =
        lrcTime(match.groupValues[1], match.groupValues[2], match.groupValues[3])
}
