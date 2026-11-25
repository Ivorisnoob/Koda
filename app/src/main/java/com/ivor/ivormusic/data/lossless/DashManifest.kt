package com.ivor.ivormusic.data.lossless

import org.w3c.dom.Element
import java.io.StringReader
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/**
 * The files one audio representation of a DASH manifest is made of, in
 * order: the initialization segment (if any) and every media segment. Joined
 * end to end they are one playable fragmented MP4.
 */
data class DashSegments(
    val initUrl: String?,
    val mediaUrls: List<String>,
    val bandwidth: Int,
    val codecs: String?,
)

/**
 * Reads the segment list out of a DASH manifest, for downloads.
 *
 * Playback hands the manifest to ExoPlayer, which needs none of this. The
 * Windows client found its segments with three regexes and assumed a
 * non-standard `segmentDuration` attribute, falling back to four seconds: a
 * manifest with shorter segments was cut off early without an error, one with
 * longer ones failed halfway. This reads the three addressing schemes the
 * standard has - `SegmentTemplate` (with or without a `SegmentTimeline`),
 * `SegmentList` and a single `BaseURL` file - honours `startNumber`, the
 * timescale and `$Number%05d$`-style widths, and resolves `BaseURL`s down the
 * MPD, Period, AdaptationSet and Representation chain.
 *
 * Pure (javax.xml DOM, available on Android and the JVM), so it is pinned by
 * a unit test.
 */
object DashManifest {

    fun parse(xml: String, manifestUrl: String): DashSegments {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // No external entities: the manifest is from a server, not trusted.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val document = factory.newDocumentBuilder()
            .parse(org.xml.sax.InputSource(StringReader(xml)))
        val mpd = document.documentElement
        require(mpd.localTag() == "MPD") { "Not a DASH manifest" }

        val totalSeconds = parseDuration(mpd.getAttribute("mediaPresentationDuration"))
        var base = resolve(manifestUrl, mpd.baseUrl())

        val period = mpd.children("Period").firstOrNull()
            ?: throw IllegalArgumentException("Manifest has no Period")
        base = resolve(base, period.baseUrl())
        val periodSeconds = parseDuration(period.getAttribute("duration")).takeIf { it > 0 } ?: totalSeconds

        // The audio set, and in it the richest representation.
        val sets = period.children("AdaptationSet")
        val audioSet = sets.firstOrNull { it.isAudio() } ?: sets.firstOrNull()
            ?: throw IllegalArgumentException("Manifest has no AdaptationSet")
        val representation = audioSet.children("Representation")
            .maxByOrNull { it.getAttribute("bandwidth").toIntOrNull() ?: 0 }
            ?: throw IllegalArgumentException("Manifest has no Representation")

        val setBase = resolve(base, audioSet.baseUrl())
        val repBase = resolve(setBase, representation.baseUrl())
        val repId = representation.getAttribute("id")
        val bandwidth = representation.getAttribute("bandwidth").toIntOrNull() ?: 0
        val codecs = representation.getAttribute("codecs").ifBlank { audioSet.getAttribute("codecs") }
            .ifBlank { null }

        // A template on the Representation wins over the set's, attribute by attribute.
        val template = mergedTemplate(
            audioSet.children("SegmentTemplate").firstOrNull(),
            representation.children("SegmentTemplate").firstOrNull()
        )
        if (template != null) {
            return fromTemplate(template, repBase, repId, bandwidth, codecs, periodSeconds)
        }

        val list = representation.children("SegmentList").firstOrNull()
            ?: audioSet.children("SegmentList").firstOrNull()
        if (list != null) {
            val init = list.children("Initialization").firstOrNull()
                ?.getAttribute("sourceURL")?.takeIf { it.isNotBlank() }
                ?.let { resolve(repBase, it) }
            val media = list.children("SegmentURL")
                .mapNotNull { it.getAttribute("media").takeIf(String::isNotBlank) }
                .map { resolve(repBase, it) }
            require(media.isNotEmpty()) { "SegmentList has no segments" }
            return DashSegments(init, media, bandwidth, codecs)
        }

        // SegmentBase or nothing at all: the BaseURL is the whole file.
        return DashSegments(initUrl = null, mediaUrls = listOf(repBase), bandwidth, codecs)
    }

    private class Template(
        val media: String,
        val initialization: String?,
        val startNumber: Long,
        val timescale: Long,
        val duration: Long,
        val timeline: Element?,
    )

    private fun mergedTemplate(outer: Element?, inner: Element?): Template? {
        if (outer == null && inner == null) return null
        fun attr(name: String): String? =
            inner?.getAttribute(name)?.takeIf { it.isNotBlank() }
                ?: outer?.getAttribute(name)?.takeIf { it.isNotBlank() }
        val media = attr("media") ?: return null
        return Template(
            media = media,
            initialization = attr("initialization"),
            startNumber = attr("startNumber")?.toLongOrNull() ?: 1L,
            timescale = attr("timescale")?.toLongOrNull()?.takeIf { it > 0 } ?: 1L,
            duration = attr("duration")?.toLongOrNull() ?: 0L,
            timeline = inner?.children("SegmentTimeline")?.firstOrNull()
                ?: outer?.children("SegmentTimeline")?.firstOrNull(),
        )
    }

    private fun fromTemplate(
        template: Template,
        base: String,
        repId: String,
        bandwidth: Int,
        codecs: String?,
        periodSeconds: Double,
    ): DashSegments {
        // (number, time) of every segment.
        val segments = mutableListOf<Pair<Long, Long>>()
        if (template.timeline != null) {
            var number = template.startNumber
            var time = 0L
            for (s in template.timeline.children("S")) {
                s.getAttribute("t").toLongOrNull()?.let { time = it }
                val d = s.getAttribute("d").toLongOrNull() ?: continue
                var repeat = s.getAttribute("r").toLongOrNull() ?: 0L
                if (repeat < 0) {
                    // "Until the end of the period": count from the length.
                    val end = (periodSeconds * template.timescale).toLong()
                    repeat = if (d > 0 && end > time) ceil((end - time).toDouble() / d).toLong() - 1 else 0
                }
                for (i in 0..repeat) {
                    segments += number to time
                    number++
                    time += d
                }
            }
        } else {
            require(template.duration > 0 && periodSeconds > 0) {
                "Template gives neither a timeline nor a segment duration"
            }
            val segmentSeconds = template.duration.toDouble() / template.timescale
            val count = ceil(periodSeconds / segmentSeconds - 1e-6).toLong().coerceAtLeast(1)
            for (i in 0 until count) {
                segments += (template.startNumber + i) to (i * template.duration)
            }
        }
        require(segments.isNotEmpty()) { "Manifest lists no segments" }

        val init = template.initialization?.let {
            resolve(base, fill(it, repId, bandwidth, number = null, time = null))
        }
        val media = segments.map { (number, time) ->
            resolve(base, fill(template.media, repId, bandwidth, number, time))
        }
        return DashSegments(init, media, bandwidth, codecs)
    }

    private val IDENTIFIER = Regex("""\$(RepresentationID|Number|Bandwidth|Time)(%0(\d+)d)?\$|\$\$""")

    /** Substitutes the template identifiers, widths included. */
    internal fun fill(template: String, repId: String, bandwidth: Int, number: Long?, time: Long?): String =
        IDENTIFIER.replace(template) { match ->
            if (match.value == "$$") return@replace "$"
            val width = match.groupValues[3].toIntOrNull()
            val value: Any = when (match.groupValues[1]) {
                "RepresentationID" -> return@replace repId
                "Number" -> number ?: return@replace match.value
                "Bandwidth" -> bandwidth.toLong()
                "Time" -> time ?: return@replace match.value
                else -> return@replace match.value
            }
            if (width != null) String.format(Locale.US, "%0${width}d", value) else value.toString()
        }

    /** ISO 8601 durations as DASH writes them: PT1H2M3.5S, P0DT3M. */
    internal fun parseDuration(text: String?): Double {
        if (text.isNullOrBlank()) return 0.0
        val match = Regex("""P(?:(\d+(?:\.\d+)?)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?""")
            .matchEntire(text.trim()) ?: return 0.0
        fun part(i: Int) = match.groupValues[i].toDoubleOrNull() ?: 0.0
        return part(1) * 86_400 + part(2) * 3_600 + part(3) * 60 + part(4)
    }

    private fun resolve(base: String, reference: String?): String {
        if (reference.isNullOrBlank()) return base
        return runCatching { URI(base).resolve(reference.trim()).toString() }.getOrElse { reference.trim() }
    }

    private fun Element.localTag(): String = tagName.substringAfter(':')

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { it.localTag() == name }
    }

    private fun Element.baseUrl(): String? =
        children("BaseURL").firstOrNull()?.textContent?.trim()?.takeIf { it.isNotBlank() }

    private fun Element.isAudio(): Boolean =
        getAttribute("contentType").equals("audio", ignoreCase = true) ||
            getAttribute("mimeType").startsWith("audio/", ignoreCase = true) ||
            children("Representation").any { it.getAttribute("mimeType").startsWith("audio/", ignoreCase = true) }
}
