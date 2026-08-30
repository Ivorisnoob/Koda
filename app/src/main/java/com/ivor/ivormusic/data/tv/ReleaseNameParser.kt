package com.ivor.ivormusic.data.tv

/**
 * How the file was made, best to worst.
 *
 * [rank] is the ordering used when nothing else separates two sources. REMUX is
 * an untouched disc stream, BluRay a re-encode of one, WEB-DL a clean pull from
 * a streaming service, WEBRIP a re-encode of that, and everything below is a
 * broadcast or a camera. The gap between WEB_DL and WEBRIP is the one people
 * actually notice.
 */
enum class SourceQuality(val label: String, val rank: Int) {
    REMUX("REMUX", 6),
    BLURAY("BluRay", 5),
    WEB_DL("WEB-DL", 4),
    WEBRIP("WEBRip", 3),
    HDTV("HDTV", 2),
    DVD("DVD", 1),
    CAM("CAM", 0),
}

enum class HdrFlag(val label: String) {
    DV("Dolby Vision"),
    HDR10_PLUS("HDR10+"),
    HDR10("HDR10"),
    HLG("HLG"),
}

/**
 * Whether a debrid service already holds this file.
 *
 * On a debrid setup this is the difference between playing now and waiting for
 * a download, so it outranks almost everything else in auto-select. [UNKNOWN]
 * is the honest answer for an addon that does not say, which is most of them.
 */
enum class CacheState { CACHED, NOT_CACHED, UNKNOWN }

/**
 * What a release name says about itself.
 *
 * **Every field here is derived and every field here can be wrong.** The raw
 * name stays visible on every row for exactly that reason: the person choosing
 * a source often knows a release group better than any regex does. Nothing in
 * the UI may show only the tags.
 */
data class StreamTags(
    val resolution: Int? = null,
    val sourceQuality: SourceQuality? = null,
    val codec: String? = null,
    val hdr: Set<HdrFlag> = emptySet(),
    val audioFormat: String? = null,
    val audioChannels: String? = null,
    /** ISO 639-1 where it could be determined, from flag emoji or from words. */
    val languages: Set<String> = emptySet(),
    val isDualAudio: Boolean = false,
    val isDubbed: Boolean = false,
    val isSubbed: Boolean = false,
    val releaseGroup: String? = null,
    val seeders: Int? = null,
    val sizeBytes: Long? = null,
    val indexer: String? = null,
    val cacheState: CacheState = CacheState.UNKNOWN,
) {
    val isHdr: Boolean get() = hdr.isNotEmpty()

    /**
     * Whether this release can serve someone who wants a dub.
     *
     * A dual-audio file carries both, so it satisfies Sub and Dub at once -
     * which is the whole reason the two flags are separate rather than one
     * tri-state. See section 5 of `plan.md`: "dub" means two unrelated things
     * and this is the one that means "a different file".
     */
    val offersDub: Boolean get() = isDubbed || isDualAudio

    /** A dub-only release is the one case that cannot serve a sub viewer. */
    val offersSub: Boolean get() = !isDubbed || isDualAudio

    val resolutionLabel: String?
        get() = when (resolution) {
            null -> null
            4320 -> "8K"
            2160 -> "4K"
            else -> resolution.toString() + "p"
        }

    val audioLabel: String?
        get() = listOfNotNull(audioFormat, audioChannels)
            .takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/**
 * Turns a release name into [StreamTags].
 *
 * **Pure and free of Android, deliberately**, because it is the one piece of the
 * TV layer whose failure is silent: a mis-parsed release still renders, still
 * plays, and simply sorts to the wrong place. It gets tested against real names
 * captured from live addons rather than invented ones.
 *
 * The input is everything an addon wrote about a stream - `name`, `title`,
 * `description` and `behaviorHints.filename` joined - because addons disagree
 * about which field carries what, and every one of them is prose.
 */
object ReleaseNameParser {

    fun parse(text: String, videoSize: Long? = null): StreamTags {
        if (text.isBlank()) return StreamTags(sizeBytes = videoSize)
        // Separators in release names are noise: dots, underscores and brackets
        // all mean "space". Matching on a normalised copy is what lets one
        // pattern cover Web.DL, WEB_DL, [WEB-DL] and WEB DL.
        val flat = text.lowercase().replace(SEPARATORS, " ")

        return StreamTags(
            resolution = resolution(flat),
            sourceQuality = sourceQuality(flat),
            codec = codec(flat),
            hdr = hdr(flat),
            audioFormat = audioFormat(flat),
            audioChannels = audioChannels(flat),
            languages = languages(text, flat),
            isDualAudio = DUAL_AUDIO.containsMatchIn(flat),
            isDubbed = DUBBED.containsMatchIn(flat),
            isSubbed = SUBBED.containsMatchIn(flat),
            releaseGroup = releaseGroup(text),
            seeders = seeders(text),
            sizeBytes = videoSize ?: size(text),
            indexer = indexer(text),
            cacheState = cacheState(text),
        )
    }

    // --- Resolution ---------------------------------------------------------

    private fun resolution(flat: String): Int? {
        RESOLUTION_LABEL.find(flat)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return when {
            EIGHT_K.containsMatchIn(flat) -> 4320
            FOUR_K.containsMatchIn(flat) -> 2160
            FULL_HD.containsMatchIn(flat) -> 1080
            else -> null
        }
    }

    // --- Source quality -----------------------------------------------------

    /**
     * Order is the whole correctness of this function. "BluRay REMUX" is a
     * remux and "WEBRip" is not a WEB-DL, so the more specific pattern has to
     * be tried first in both pairs.
     */
    private fun sourceQuality(flat: String): SourceQuality? = when {
        REMUX.containsMatchIn(flat) -> SourceQuality.REMUX
        CAM.containsMatchIn(flat) -> SourceQuality.CAM
        BLURAY.containsMatchIn(flat) -> SourceQuality.BLURAY
        WEBRIP.containsMatchIn(flat) -> SourceQuality.WEBRIP
        WEB_DL.containsMatchIn(flat) -> SourceQuality.WEB_DL
        HDTV.containsMatchIn(flat) -> SourceQuality.HDTV
        DVD.containsMatchIn(flat) -> SourceQuality.DVD
        else -> null
    }

    private fun codec(flat: String): String? = when {
        HEVC.containsMatchIn(flat) -> "HEVC"
        AV1.containsMatchIn(flat) -> "AV1"
        AVC.containsMatchIn(flat) -> "H.264"
        XVID.containsMatchIn(flat) -> "XviD"
        VP9.containsMatchIn(flat) -> "VP9"
        else -> null
    }

    /**
     * Dolby Vision and HDR10 co-occur constantly - a DV Profile 8 file carries
     * an HDR10 base layer - so this returns a set rather than picking one.
     */
    private fun hdr(flat: String): Set<HdrFlag> = buildSet {
        if (DOLBY_VISION.containsMatchIn(flat)) add(HdrFlag.DV)
        if (HDR10_PLUS.containsMatchIn(flat)) add(HdrFlag.HDR10_PLUS)
        if (HDR10.containsMatchIn(flat)) add(HdrFlag.HDR10)
        if (HLG.containsMatchIn(flat)) add(HdrFlag.HLG)
    }

    /** Most specific first: DTS-HD MA is not DTS, and Atmos rides on TrueHD. */
    private fun audioFormat(flat: String): String? = when {
        ATMOS.containsMatchIn(flat) -> "Atmos"
        DTS_X.containsMatchIn(flat) -> "DTS:X"
        DTS_HD.containsMatchIn(flat) -> "DTS-HD"
        TRUEHD.containsMatchIn(flat) -> "TrueHD"
        DTS.containsMatchIn(flat) -> "DTS"
        EAC3.containsMatchIn(flat) -> "EAC3"
        AC3.containsMatchIn(flat) -> "AC3"
        FLAC.containsMatchIn(flat) -> "FLAC"
        OPUS.containsMatchIn(flat) -> "Opus"
        AAC.containsMatchIn(flat) -> "AAC"
        MP3.containsMatchIn(flat) -> "MP3"
        else -> null
    }

    private fun audioChannels(flat: String): String? {
        CHANNELS.find(flat)?.let { match ->
            val front = match.groupValues[1]
            val lfe = match.groupValues[2]
            // Separator flattening already turned "7.1" into "7 1", so the dot
            // is put back rather than the space simply removed - which would
            // report a 71-channel layout.
            if (front.isNotBlank() && lfe.isNotBlank()) return front + "." + lfe
            val ch = match.groupValues[3]
            if (ch.isNotBlank()) return channelsFromCount(ch.toIntOrNull())
        }
        return null
    }

    /** "6ch" is 5.1 and "8ch" is 7.1; anything else is reported as it was written. */
    private fun channelsFromCount(count: Int?): String? = when (count) {
        null -> null
        8 -> "7.1"
        6 -> "5.1"
        2 -> "2.0"
        1 -> "Mono"
        else -> count.toString() + "ch"
    }

    // --- Languages ----------------------------------------------------------

    /**
     * Languages, from two independent signals.
     *
     * Torrentio and its relatives put **flag emoji** in the title and nothing
     * else - there is no structured language field anywhere in the protocol
     * [verified August 2026], which is why sub/dub has to be read out of prose.
     * A flag is two regional-indicator code points spelling a country code, so
     * decoding it is exact where word matching is a guess; both run, and the
     * union is the answer.
     */
    internal fun languages(raw: String, flat: String): Set<String> = buildSet {
        addAll(flagLanguages(raw))
        for ((pattern, code) in LANGUAGE_WORDS) {
            if (pattern.containsMatchIn(flat)) add(code)
        }
    }

    /** Decode regional-indicator pairs into ISO 639-1 codes via their country. */
    internal fun flagLanguages(raw: String): Set<String> = buildSet {
        val points = raw.codePoints().toArray()
        var i = 0
        while (i < points.size - 1) {
            val first = points[i]
            val second = points[i + 1]
            if (first in REGIONAL_START..REGIONAL_END && second in REGIONAL_START..REGIONAL_END) {
                val country = charArrayOf(
                    'A' + (first - REGIONAL_START),
                    'A' + (second - REGIONAL_START),
                ).concatToString()
                COUNTRY_LANGUAGE[country]?.let { add(it) }
                i += 2
            } else {
                i++
            }
        }
    }

    // --- Provenance ---------------------------------------------------------

    /**
     * The release group.
     *
     * Two conventions, both common and neither reliable. Scene and P2P names
     * end with -GROUP; anime fansub names begin with [Group]. The
     * leading-bracket branch has to reject bracketed technical tokens, since
     * "[1080p] Title" would otherwise report a group called 1080p.
     */
    internal fun releaseGroup(raw: String): String? {
        LEADING_BRACKET.find(raw.trimStart())?.groupValues?.get(1)?.trim()?.let { candidate ->
            val lower = candidate.lowercase()
            if (candidate.isNotBlank() &&
                !TECHNICAL_TOKEN.containsMatchIn(lower) &&
                !DEBRID_TAG.matches(lower)
            ) {
                return candidate
            }
        }
        // Every line, first match wins. An addon's stream text is several lines
        // - the addon name, a quality summary, the release name, then a stats
        // line - and only one of them ends in a group. Reading just the first
        // or just the last picks the wrong line for half the ecosystem.
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line -> TRAILING_GROUP.find(line)?.groupValues?.get(1) }
            .firstOrNull { it.length in 2..20 }
    }

    internal fun seeders(raw: String): Int? =
        SEEDERS.find(raw)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }?.toIntOrNull()

    /** File size written for humans, converted to bytes. Binary units, as posted. */
    internal fun size(raw: String): Long? {
        val match = SIZE.find(raw) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase().first()) {
            't' -> 1024.0 * 1024 * 1024 * 1024
            'g' -> 1024.0 * 1024 * 1024
            'm' -> 1024.0 * 1024
            else -> 1024.0
        }
        return (value * multiplier).toLong()
    }

    internal fun indexer(raw: String): String? =
        INDEXER.find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }

    /**
     * Whether a debrid service already holds the file.
     *
     * The convention every debrid-aware addon follows is a bracketed service
     * code with a plus for cached and the word download for not: [RD+],
     * [TB download]. Nothing in the protocol standardises it, so absence means
     * unknown rather than not-cached - reporting "not cached" for an addon that
     * never mentions caching would make direct HTTP sources look worse than
     * torrents, which is backwards.
     */
    internal fun cacheState(raw: String): CacheState = when {
        CACHED.containsMatchIn(raw) -> CacheState.CACHED
        NOT_CACHED.containsMatchIn(raw) -> CacheState.NOT_CACHED
        else -> CacheState.UNKNOWN
    }

    // --- Patterns -----------------------------------------------------------

    private val SEPARATORS = Regex("[._\\-\\[\\]()|/]+")

    private val RESOLUTION_LABEL = Regex("\\b(4320|2160|1440|1080|720|576|480|360|240)\\s*[pi]\\b")
    private val EIGHT_K = Regex("\\b8k\\b")
    private val FOUR_K = Regex("\\b(4k|uhd)\\b")
    private val FULL_HD = Regex("\\b(fhd|fullhd|full hd)\\b")

    private val REMUX = Regex("\\bremux\\b")
    private val BLURAY = Regex("\\b(bluray|blu ray|bdrip|brrip|bdremux|bd25|bd50|uhdbd)\\b")
    private val WEBRIP = Regex("\\b(webrip|web rip)\\b")
    private val WEB_DL = Regex("\\b(webdl|web dl|web)\\b")
    private val HDTV = Regex("\\b(hdtv|pdtv|sdtv|dsr|hdrip|dvbrip)\\b")
    private val DVD = Regex("\\b(dvdrip|dvd5|dvd9|dvdr|dvd)\\b")
    private val CAM = Regex("\\b(cam|camrip|hdcam|hdts|telesync|telecine|dvdscr|screener|workprint)\\b")

    private val HEVC = Regex("\\b(hevc|h ?265|x265)\\b")
    private val AVC = Regex("\\b(avc|h ?264|x264)\\b")
    private val AV1 = Regex("\\bav1\\b")
    private val XVID = Regex("\\b(xvid|divx)\\b")
    private val VP9 = Regex("\\bvp9\\b")

    private val DOLBY_VISION = Regex("\\b(dv|dovi|dolby vision)\\b")
    private val HDR10_PLUS = Regex("\\b(hdr10 ?plus|hdr10\\+)\\b")
    private val HDR10 = Regex("\\b(hdr10|hdr)\\b")
    private val HLG = Regex("\\bhlg\\b")

    private val ATMOS = Regex("\\batmos\\b")
    private val TRUEHD = Regex("\\b(truehd|true hd)\\b")
    private val DTS_X = Regex("\\bdts ?x\\b")
    private val DTS_HD = Regex("\\b(dts ?hd|dtshd ?ma|dts ?ma)\\b")
    private val DTS = Regex("\\bdts\\b")
    private val EAC3 = Regex("\\b(eac3|e ac3|ddp|dd\\+|ddplus)\\b")
    private val AC3 = Regex("\\b(ac3|dd)\\b")
    private val AAC = Regex("\\baac\\b")
    private val OPUS = Regex("\\bopus\\b")
    private val FLAC = Regex("\\bflac\\b")
    private val MP3 = Regex("\\bmp3\\b")

    /**
     * Channel layout, after separator flattening has already turned "5.1" into
     * "5 1" - which is why the decimal branch matches a space, not a dot.
     *
     * No leading word boundary, because the digit is routinely glued to the
     * codec: "DDP5.1" flattens to "ddp5 1" and there is no boundary between the
     * p and the 5. The lookarounds do the work a boundary cannot - they keep
     * "S01E01 1080p" from reading as a 1.1 layout, which it otherwise does.
     */
    private val CHANNELS = Regex("(?<![0-9])(?:([1-8])\\s([0-2])(?![0-9])|([1-8])\\s?ch\\b)")

    private val DUAL_AUDIO = Regex("\\b(dual audio|dualaudio|multi audio|multiaudio|dual)\\b")
    private val DUBBED = Regex("\\b(dubbed|dub|dublado|doblado|latino)\\b")
    private val SUBBED = Regex("\\b(subbed|softsub|hardsub|vostfr|legendado|multi subs?)\\b")

    private val LEADING_BRACKET = Regex("^\\[([^\\]]{2,24})\\]")
    private val TECHNICAL_TOKEN = Regex(
        "\\d{3,4}p|remux|bluray|web|hevc|x26[45]|hdr|\\bdv\\b|aac|dts|ddp|10 ?bit|multi|dual"
    )
    private val TRAILING_GROUP = Regex("-([A-Za-z0-9]{2,20})(?:\\.[a-z0-9]{2,4})?\\s*$")

    /**
     * A debrid service marker, which leads the line on every debrid-aware addon
     * and is emphatically not a release group. Without this, "[RD+] Torrentio"
     * reports a fansub group called RD+.
     */
    private val DEBRID_TAG = Regex("^(rd|ad|tb|pm|oc|dl|ed|debrid)(\\+|\\s+download)?$")

    private val SEEDERS = Regex(
        "[👤👥]\\s*(\\d+)|(\\d+)\\s*seeders?|seeders?\\s*:?\\s*(\\d+)",
        RegexOption.IGNORE_CASE
    )
    private val SIZE = Regex(
        "(\\d+(?:[.,]\\d+)?)\\s*(TB|GB|MB|KB|TiB|GiB|MiB|KiB)\\b",
        RegexOption.IGNORE_CASE
    )
    private val INDEXER = Regex("⚙️?\\s*([^\\n💾👤⚙]+)")

    private val CACHED = Regex(
        "\\[(?:rd|ad|tb|pm|oc|dl|ed|debrid)\\+\\]|\\[cached\\]|⚡",
        RegexOption.IGNORE_CASE
    )
    private val NOT_CACHED = Regex(
        "\\[(?:rd|ad|tb|pm|oc|dl|ed|debrid)\\s+download\\]",
        RegexOption.IGNORE_CASE
    )

    private const val REGIONAL_START = 0x1F1E6
    private const val REGIONAL_END = 0x1F1FF

    /**
     * Country to language, for flag decoding.
     *
     * Deliberately a country map rather than a language map: the emoji encodes
     * a country and the mapping is lossy by nature - a Swiss flag could mean
     * three languages, so it is absent rather than guessed.
     */
    private val COUNTRY_LANGUAGE: Map<String, String> = mapOf(
        "US" to "en", "GB" to "en", "AU" to "en", "CA" to "en", "NZ" to "en", "IE" to "en",
        "JP" to "ja", "KR" to "ko", "CN" to "zh", "TW" to "zh", "HK" to "zh",
        "FR" to "fr", "DE" to "de", "AT" to "de", "IT" to "it",
        "ES" to "es", "MX" to "es", "AR" to "es", "CO" to "es", "CL" to "es",
        "PT" to "pt", "BR" to "pt", "RU" to "ru", "UA" to "uk", "PL" to "pl",
        "NL" to "nl", "SE" to "sv", "NO" to "no", "DK" to "da", "FI" to "fi",
        "GR" to "el", "CZ" to "cs", "SK" to "sk", "HU" to "hu", "RO" to "ro",
        "BG" to "bg", "HR" to "hr", "RS" to "sr", "SI" to "sl", "TR" to "tr",
        "IN" to "hi", "PK" to "ur", "BD" to "bn", "TH" to "th", "VN" to "vi",
        "ID" to "id", "MY" to "ms", "PH" to "tl", "IL" to "he", "IR" to "fa",
        "SA" to "ar", "AE" to "ar", "EG" to "ar", "LT" to "lt", "LV" to "lv", "EE" to "et",
    )

    /**
     * Language words, checked against the flattened text.
     *
     * Short forms carry real ambiguity - "ita" appears inside other words and
     * "vf" inside filenames - so every pattern is word-bounded and the riskiest
     * abbreviations are simply not listed. A missed language sorts a source
     * slightly wrong; a wrong one filters it out of view, which is worse.
     */
    private val LANGUAGE_WORDS: List<Pair<Regex, String>> = listOf(
        Regex("\\b(english|eng|ingles)\\b") to "en",
        Regex("\\b(japanese|jap|jpn|nihongo)\\b") to "ja",
        Regex("\\b(korean|kor)\\b") to "ko",
        Regex("\\b(chinese|mandarin|cantonese)\\b") to "zh",
        Regex("\\b(french|francais|truefrench|vostfr|vff)\\b") to "fr",
        Regex("\\b(german|deutsch|ger)\\b") to "de",
        Regex("\\b(spanish|espanol|castellano|latino|esp)\\b") to "es",
        Regex("\\b(italian|italiano|ita)\\b") to "it",
        Regex("\\b(portuguese|portugues|dublado)\\b") to "pt",
        Regex("\\b(russian|rus)\\b") to "ru",
        Regex("\\b(hindi|hin)\\b") to "hi",
        Regex("\\b(tamil|tam)\\b") to "ta",
        Regex("\\b(telugu|tel)\\b") to "te",
        Regex("\\b(arabic|arab)\\b") to "ar",
        Regex("\\b(turkish|turkce)\\b") to "tr",
        Regex("\\b(polish|polski)\\b") to "pl",
        Regex("\\b(dutch|nederlands)\\b") to "nl",
        Regex("\\bswedish\\b") to "sv",
        Regex("\\bthai\\b") to "th",
        Regex("\\b(indonesian|bahasa)\\b") to "id",
        Regex("\\bvietnamese\\b") to "vi",
        Regex("\\bhebrew\\b") to "he",
        Regex("\\bgreek\\b") to "el",
        Regex("\\bukrainian\\b") to "uk",
    )
}
