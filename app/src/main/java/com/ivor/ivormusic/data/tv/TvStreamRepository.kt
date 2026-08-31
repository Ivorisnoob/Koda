package com.ivor.ivormusic.data.tv

import android.content.Context
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One candidate release, with the addon that produced it and what its name
 * says about it.
 *
 * The wire object and the derived tags are kept side by side rather than merged
 * because they have different trust levels: [stream] is what an addon actually
 * said and [tags] is what a regex guessed. Anything the UI shows as fact comes
 * from the former.
 */
data class TvSource(
    val addonId: String,
    val addonName: String,
    val stream: TvStream,
    val tags: StreamTags,
) {
    /**
     * Whether this can actually be started.
     *
     * Koda plays a resolved `url` and nothing else, so this is exactly
     * [TvSourceKind.PLAYABLE]. It stays a named member rather than collapsing
     * into `kind == PLAYABLE` at the call sites because the ranking functions
     * sort on it, and one name is the thing to change if a second playable
     * shape ever arrives.
     */
    val isPlayable: Boolean get() = kind == TvSourceKind.PLAYABLE

    /**
     * Which of the protocol's delivery shapes this is.
     *
     * [TvSourceKind.EXTERNAL] exists because the two unplayable shapes are not
     * one thing. A torrent needs a debrid service to resolve it into a direct
     * link; a link to somebody's web player needs a browser and nothing else.
     * Calling both "a torrent" tells one of them a plain falsehood, which is
     * what the first cut of this did.
     */
    val kind: TvSourceKind
        get() = when {
            !stream.url.isNullOrBlank() -> TvSourceKind.PLAYABLE
            !stream.externalUrl.isNullOrBlank() || !stream.ytId.isNullOrBlank() ->
                TvSourceKind.EXTERNAL
            else -> TvSourceKind.TORRENT
        }

    /** Where an [TvSourceKind.EXTERNAL] row should send the viewer. */
    val externalLink: String?
        get() = stream.externalUrl?.takeIf { it.isNotBlank() }
            ?: stream.ytId?.takeIf { it.isNotBlank() }
                ?.let { "https://www.youtube.com/watch?v=" + it }

    /**
     * Stable identity for list keys and for cross-addon dedupe.
     *
     * The infoHash names the file itself, so two addons returning the same
     * torrent collapse to one row. A resolved URL is per-request and can carry
     * a token, but it is the only handle a direct-HTTP source has.
     */
    val id: String
        get() = stream.infoHash?.lowercase()?.let { hash ->
            // fileIdx separates two episodes packed in one season torrent,
            // which share a hash and are genuinely different files.
            if (stream.fileIdx != null) hash + ":" + stream.fileIdx else hash
        } ?: stream.url ?: stream.externalUrl ?: (addonId + ":" + stream.releaseName)

    val bingeGroup: String? get() = stream.behaviorHints?.bingeGroup?.takeIf { it.isNotBlank() }

    /** Headers the addon says the media request needs. Empty for almost all of them. */
    val requestHeaders: Map<String, String>
        get() = stream.behaviorHints?.proxyHeaders?.request.orEmpty()

    val sizeBytes: Long? get() = stream.behaviorHints?.videoSize ?: tags.sizeBytes

    /** The line addons put the quality summary on, when it is not the release name. */
    val addonLabel: String
        get() = stream.name?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
            ?.takeIf { it.isNotBlank() } ?: addonName
}

/** How a source delivers its file, which decides what tapping it can do. */
enum class TvSourceKind { PLAYABLE, TORRENT, EXTERNAL }

/** Which of the two dub mechanisms the viewer is asking for. See plan section 5. */
enum class DubPreference { ANY, SUB, DUB }

/**
 * What auto-select should aim for.
 *
 * Built per playback from a fresh preference read, not held: the network can
 * change between opening a title and opening its source sheet, and the Wi-Fi /
 * mobile split is only honest if it is read at the moment of the decision -
 * which is the rule `getDefaultVideoQuality` already follows.
 */
data class TvAutoSelectProfile(
    /** Height ceiling. 0 means no ceiling, which is what "auto" maps to. */
    val maxResolution: Int = 0,
    val maxSizeBytes: Long? = null,
    /**
     * HDR and Dolby Vision releases are de-prioritised rather than hidden.
     * `CLAUDE.md` states HDR is intentionally unsupported because the previous
     * path was unreliable; a DV Profile 5 file on a device that cannot decode
     * it plays green and purple rather than failing. Off by default, and the
     * source is still listed and still playable if chosen deliberately.
     */
    val allowHdr: Boolean = false,
    /** ISO 639-1, most wanted first. */
    val preferredLanguages: List<String> = emptyList(),
    val dubPreference: DubPreference = DubPreference.ANY,
) {
    companion object {
        /**
         * Read the profile for the network in use right now.
         *
         * Reuses the existing per-network video quality pair rather than adding
         * a second one, so someone who capped video at 720p on mobile does not
         * have to say it twice. A dedicated TV profile with size and codec
         * limits is phase 4; until then this is the honest approximation, and
         * it is the same value the YouTube player already obeys.
         */
        fun forCurrentNetwork(context: Context): TvAutoSelectProfile {
            val label = ThemePreferences(context).getDefaultVideoQuality()
            val height = label.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            return TvAutoSelectProfile(maxResolution = height)
        }
    }
}

/** Why auto-select landed where it did. Mapped to a string by the UI, not here. */
enum class PickReason { ONLY_PLAYABLE, CACHED, WITHIN_LIMIT, BEST_AVAILABLE }

data class TvAutoPick(val source: TvSource, val reason: PickReason)

/**
 * The outcome of one fan-out.
 *
 * [failedAddons] is named rather than counted because the two failures a viewer
 * can act on are different: an addon that needs an account answers 400 to every
 * request, and an addon that is down answers nothing. Collapsing either into an
 * empty list tells them "no addon has this title", which is a different problem
 * with a different fix - and is exactly how an addon that quietly requires
 * registration looks like a broken app.
 */
data class TvSourceResult(
    val sources: List<TvSource> = emptyList(),
    val failedAddons: List<String> = emptyList(),
)

/** The filter chips that this particular result set can actually offer. */
data class TvSourceFacets(
    val resolutions: List<Int> = emptyList(),
    val languages: List<String> = emptyList(),
    val sourceQualities: List<SourceQuality> = emptyList(),
    val hasHdr: Boolean = false,
    val hasCached: Boolean = false,
    val hasDub: Boolean = false,
    val hasSub: Boolean = false,
    val hasUnplayable: Boolean = false,
    val hasExternal: Boolean = false,
) {
    val isEmpty: Boolean
        get() = resolutions.isEmpty() && languages.isEmpty() && sourceQualities.isEmpty() &&
            !hasHdr && !hasCached && !hasDub
}

/** The chips the viewer has actually switched on. */
data class TvSourceFilter(
    val resolution: Int? = null,
    val language: String? = null,
    val sourceQuality: SourceQuality? = null,
    val cachedOnly: Boolean = false,
    val dub: DubPreference = DubPreference.ANY,
) {
    val isActive: Boolean
        get() = resolution != null || language != null || sourceQuality != null ||
            cachedOnly || dub != DubPreference.ANY
}

/**
 * Streams and subtitles, fanned out across the installed addons.
 *
 * Two rules, both inherited: **a failing addon never blocks the ones that
 * answered**, and the fan-out is bounded. Nothing here throws upward - an addon
 * that times out simply contributes nothing to this query, which is a different
 * failure from the rate-limit hold and gets a different answer (plan section 4).
 */
class TvStreamRepository(context: Context) {

    private val client = StremioClient(context)
    private val addonRepository = AddonRepository(context)

    val addons: AddonRepository get() = addonRepository

    /**
     * Every source for one item or episode.
     *
     * [id] is the addon-facing id: `tt0903747` for a film, `tt0903747:1:1` for
     * an episode. Nothing has to reconstruct it because that is exactly what
     * the progress store already keys on.
     */
    suspend fun sources(
        type: String,
        id: String,
        /**
         * Ask every addon regardless of what the earlier ones returned.
         *
         * False - the default - walks the addons in the user's own priority
         * order and stops at the first tier that produced something startable,
         * which is what pressing Play wants: the fastest correct answer, not
         * the complete one. True is for the source sheet, where the viewer has
         * explicitly asked to see the field and a missing release is a worse
         * failure than a slower list.
         */
        exhaustive: Boolean = false,
    ): TvSourceResult = coroutineScope {
        val providers = addonRepository.streamProviders()
            .filter { it.handlesEnabled("stream", type, id) }
        if (providers.isEmpty()) return@coroutineScope TvSourceResult()

        val collected = mutableListOf<TvSource>()
        val failed = mutableListOf<String>()

        // `streamProviders()` is already sorted by the user's ordering, so a
        // chunk is a priority tier. With the usual handful of stream addons
        // this is a single tier and behaves exactly as the old flat fan-out.
        for (tier in providers.chunked(TvRepository.CONCURRENCY)) {
            val answers = tier.map { addon ->
                async { resolveOne(addon, type, id) }
            }.awaitAll()

            for ((failedName, sources) in answers) {
                if (failedName != null) failed += failedName else collected += sources
            }

            // Anything startable ends the walk. Unplayable rows deliberately do
            // not: a tier that returned sixty torrents has answered the
            // question "is there a file here" with a no, and stopping on it
            // would hide the addon that could actually play the title.
            if (!exhaustive && collected.any { it.isPlayable }) break
        }

        TvSourceResult(sources = dedupe(collected), failedAddons = failed)
    }

    /**
     * One addon's answer, bounded in time.
     *
     * The timeout is the point. Without it a single addon that accepts the
     * connection and then never replies holds up playback for as long as the
     * socket read allows, and because the fan-out awaits every provider, one
     * hung addon stalled the whole thing - with a spinner and no explanation
     * now that Play no longer opens a sheet to look at.
     *
     * Returns the addon's name on failure and its sources on success, because
     * "could not be reached" and "answered with nothing" are different and only
     * the first is worth naming to the viewer.
     */
    private suspend fun resolveOne(
        addon: InstalledAddon,
        type: String,
        id: String,
    ): Pair<String?, List<TvSource>> {
        val streams = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
            client.streams(addon.transportUrl, type, id)
        }
        if (streams == null) {
            // Named, never with its URL: a configured transport URL carries the
            // user's debrid key.
            KLog.w(TAG, "No answer from " + addon.name)
            return addon.name to emptyList()
        }
        return null to streams.map { stream ->
            TvSource(
                addonId = addon.id,
                addonName = addon.name,
                stream = stream,
                tags = ReleaseNameParser.parse(stream.text, stream.behaviorHints?.videoSize),
            )
        }
    }

    /**
     * Subtitles for one item or episode, across every subtitle addon.
     *
     * Merged with whatever the chosen stream carried, by the caller, because
     * only the caller knows which stream was chosen. OpenSubtitles v3 alone
     * returned 90 tracks for one film [verified August 2026], so the picker
     * groups by language rather than listing them flat.
     */
    suspend fun subtitles(type: String, id: String): List<TvSubtitleTrack> = coroutineScope {
        val providers = addonRepository.subtitleProviders()
            .filter { it.handlesEnabled("subtitles", type, id) }
        if (providers.isEmpty()) return@coroutineScope emptyList()

        val gate = Semaphore(TvRepository.CONCURRENCY)
        providers.map { addon ->
            async {
                gate.withPermit {
                    client.subtitles(addon.transportUrl, type, id).orEmpty()
                        .filter { it.isUsable }
                        .map { it.copy(addonName = addon.name) }
                }
            }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    companion object {
        private const val TAG = "TvStreamRepository"

        /**
         * How long one addon gets to answer a stream request.
         *
         * Generous, because a debrid addon legitimately does work before it can
         * reply - it may be asking its provider whether a torrent is cached -
         * and cutting that short turns a working source into a missing one.
         * Bounded all the same, because Play now waits on this behind a
         * spinner and an addon that never replies must not become a spinner
         * that never ends.
         */
        private const val PROVIDER_TIMEOUT_MS = 20_000L

        /**
         * Cached beats everything, because on a debrid setup the alternative is
         * not a worse picture, it is a wait. Deliberately larger than the whole
         * resolution range so a cached 720p outranks an uncached 4K.
         */
        private const val CACHED_BONUS = 10_000
        private const val NOT_CACHED_PENALTY = 2_000

        /** Enough to move a source below every release of an allowed kind. */
        private const val HDR_PENALTY = 3_000
        private const val OVER_SIZE_PENALTY = 4_000
        private const val SOURCE_QUALITY_WEIGHT = 200
        private const val LANGUAGE_BONUS = 1_500
        private const val DUB_MATCH_BONUS = 1_200
        private const val RESOLUTION_WEIGHT = 2

        /** A tiebreak, not a ranking signal. Capped so a swarm cannot outvote quality. */
        private const val MAX_SEEDER_BONUS = 200

        /** Assumed when a release name says nothing, between WEBRip and HDTV. */
        private const val UNKNOWN_SOURCE_RANK = 2

        /**
         * Collapse the same file arriving from several addons.
         *
         * Keyed on [TvSource.id], so two addons wrapping the same torrent
         * collapse and two different files never do. **The playable one wins**:
         * the common case is one addon returning a bare infoHash and another
         * returning a debrid-resolved URL for the identical release, and
         * keeping the infoHash there would hide the one that actually plays.
         */
        fun dedupe(sources: List<TvSource>): List<TvSource> {
            val result = LinkedHashMap<String, TvSource>()
            for (source in sources) {
                val existing = result[source.id]
                if (existing == null || preferOver(source, existing)) result[source.id] = source
            }
            return result.values.toList()
        }

        private fun preferOver(candidate: TvSource, current: TvSource): Boolean {
            if (candidate.isPlayable != current.isPlayable) return candidate.isPlayable
            if (candidate.tags.cacheState != current.tags.cacheState) {
                return candidate.tags.cacheState == CacheState.CACHED
            }
            return false
        }

        /**
         * How well a source matches the profile. Higher is better.
         *
         * Pure and exposed, because this is the function that decides what
         * plays when nobody chooses - and a bad weight here is invisible: the
         * video still plays, it is simply the wrong one, every time, forever.
         */
        fun score(source: TvSource, profile: TvAutoSelectProfile): Int {
            val tags = source.tags
            var score = 0

            when (tags.cacheState) {
                CacheState.CACHED -> score += CACHED_BONUS
                CacheState.NOT_CACHED -> score -= NOT_CACHED_PENALTY
                CacheState.UNKNOWN -> Unit
            }

            // At or below the cap, sharper is better. Above it, the penalty
            // grows with the overshoot rather than disqualifying: a 4K file is
            // still the right answer when it is the only one there is.
            val resolution = tags.resolution ?: 0
            val cap = profile.maxResolution
            score += if (cap <= 0 || resolution <= cap) resolution * RESOLUTION_WEIGHT
            else cap * RESOLUTION_WEIGHT - (resolution - cap)

            if (tags.isHdr && !profile.allowHdr) score -= HDR_PENALTY

            score += (tags.sourceQuality?.rank ?: UNKNOWN_SOURCE_RANK) * SOURCE_QUALITY_WEIGHT

            // Ranked by position, so a second-choice language still beats none.
            val languageIndex = profile.preferredLanguages
                .indexOfFirst { tags.languages.contains(it) }
            if (languageIndex >= 0) score += LANGUAGE_BONUS - languageIndex * 100

            when (profile.dubPreference) {
                DubPreference.DUB -> if (tags.offersDub) score += DUB_MATCH_BONUS
                DubPreference.SUB -> if (tags.offersSub) score += DUB_MATCH_BONUS
                DubPreference.ANY -> Unit
            }

            val size = source.sizeBytes
            val maxSize = profile.maxSizeBytes
            if (size != null && maxSize != null && size > maxSize) score -= OVER_SIZE_PENALTY

            score += (tags.seeders ?: 0).coerceAtMost(MAX_SEEDER_BONUS)

            return score
        }

        /**
         * List order: playable first, then by score.
         *
         * Unplayable rows keep their place in the list rather than being
         * dropped, because hiding them makes a working torrent addon look like
         * a broken one - they sort last, dimmed, and say what they need.
         */
        fun ranked(
            sources: List<TvSource>,
            profile: TvAutoSelectProfile,
        ): List<TvSource> =
            sources.sortedWith(
                compareByDescending<TvSource> { it.isPlayable }
                    .thenByDescending { score(it, profile) }
            )

        /**
         * The one source the hero card offers, and the reason to show under it.
         *
         * Returns null when nothing is playable, which is a real and common
         * state - an unconfigured torrent addon returns sixty-odd rows and not
         * one of them can be opened.
         */
        fun autoPick(
            sources: List<TvSource>,
            profile: TvAutoSelectProfile,
        ): TvAutoPick? {
            val playable = sources.filter { it.isPlayable }
            if (playable.isEmpty()) return null
            val best = playable.maxByOrNull { score(it, profile) } ?: return null
            val reason = when {
                playable.size == 1 -> PickReason.ONLY_PLAYABLE
                best.tags.cacheState == CacheState.CACHED -> PickReason.CACHED
                profile.maxResolution > 0 &&
                    playable.any { (it.tags.resolution ?: 0) > profile.maxResolution } ->
                    PickReason.WITHIN_LIMIT
                else -> PickReason.BEST_AVAILABLE
            }
            return TvAutoPick(best, reason)
        }

        /** What this result set can be filtered by. Anything absent gets no chip. */
        fun facets(sources: List<TvSource>): TvSourceFacets = TvSourceFacets(
            resolutions = sources.mapNotNull { it.tags.resolution }.distinct().sortedDescending(),
            languages = sources.flatMap { it.tags.languages }.distinct().sorted(),
            sourceQualities = sources.mapNotNull { it.tags.sourceQuality }
                .distinct().sortedByDescending { it.rank },
            hasHdr = sources.any { it.tags.isHdr },
            hasCached = sources.any { it.tags.cacheState == CacheState.CACHED },
            hasDub = sources.any { it.tags.offersDub },
            hasSub = sources.any { it.tags.offersSub },
            hasUnplayable = sources.any { !it.isPlayable },
            hasExternal = sources.any { it.kind == TvSourceKind.EXTERNAL },
        )

        /**
         * Apply the chips.
         *
         * **A filter that empties the list is a broken control**, so the facets
         * are derived from the same set this filters - every chip offered can
         * match at least one row on its own.
         */
        fun filter(sources: List<TvSource>, filter: TvSourceFilter): List<TvSource> =
            sources.filter { source ->
                val tags = source.tags
                if (filter.resolution != null && tags.resolution != filter.resolution) return@filter false
                if (filter.language != null && !tags.languages.contains(filter.language)) return@filter false
                if (filter.sourceQuality != null && tags.sourceQuality != filter.sourceQuality) return@filter false
                if (filter.cachedOnly && tags.cacheState != CacheState.CACHED) return@filter false
                when (filter.dub) {
                    DubPreference.DUB -> tags.offersDub
                    DubPreference.SUB -> tags.offersSub
                    DubPreference.ANY -> true
                }
            }
    }
}
