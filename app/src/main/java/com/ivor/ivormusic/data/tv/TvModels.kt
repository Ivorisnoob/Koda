package com.ivor.ivormusic.data.tv

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Wire models for the Stremio addon protocol.
 *
 * **These are kotlinx-serialization rather than manual org.json traversal, and
 * that is a deliberate departure from how the InnerTube layer is written.** Two
 * reasons, both specific to this protocol:
 *
 * 1. InnerTube is an undocumented surface whose renderers get renamed wholesale,
 *    so lenient recursive traversal plus dated probes is the only thing that
 *    survives. The addon protocol is a documented, versioned contract with a
 *    published SDK. Unknown keys are ignored rather than hunted for.
 * 2. Android's `org.json` is a stub in JVM unit tests and this project sets
 *    `isReturnDefaultValues = true`, so an `org.json` parser here returns null
 *    for every field under test - verified, not assumed. Parsers that cannot be
 *    tested are exactly what this layer must not ship, since the shapes come
 *    from servers nobody here controls.
 *
 * Everything below is nullable or defaulted, because "required" in the spec
 * means "required of a well-behaved addon" and community addons are not
 * uniformly well-behaved.
 */
object TvJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }
}

/**
 * Accepts a JSON string *or* number and yields a string.
 *
 * `imdbRating` and `releaseInfo` are documented as strings and Cinemeta sends
 * them as strings, but community addons send numbers for both. Without this a
 * single such addon fails the whole response rather than one field.
 */
object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> element.contentOrNull ?: ""
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/**
 * A manifest resource, which the spec allows to be either a bare string
 * (`"stream"`) or an object (`{ name, types, idPrefixes }`).
 */
@Serializable(with = AddonResourceSerializer::class)
data class AddonResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

object AddonResourceSerializer : KSerializer<AddonResource> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AddonResource", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): AddonResource {
        val input = decoder as? JsonDecoder ?: return AddonResource(decoder.decodeString())
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> AddonResource(element.content)
            is JsonObject -> AddonResource(
                name = element["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                types = (element["types"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull },
                idPrefixes = (element["idPrefixes"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull },
            )
            else -> AddonResource("")
        }
    }

    override fun serialize(encoder: Encoder, value: AddonResource) =
        encoder.encodeString(value.name)
}

@Serializable
data class AddonExtra(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String>? = null,
    val optionsLimit: Int? = null,
)

@Serializable
data class AddonCatalog(
    val type: String = "",
    val id: String = "",
    val name: String = "",
    val extra: List<AddonExtra> = emptyList(),
    /** Legacy sibling of [extra] that Cinemeta still sends; read only as a fallback. */
    val extraSupported: List<String> = emptyList(),
    val extraRequired: List<String> = emptyList(),
    /** Cinemeta declares genres here as well as inside [extra]. */
    val genres: List<String> = emptyList(),
) {
    private fun extraFor(name: String): AddonExtra? = extra.firstOrNull { it.name == name }

    private fun supports(name: String): Boolean =
        extraFor(name) != null || extraSupported.contains(name)

    private fun requires(name: String): Boolean =
        extraFor(name)?.isRequired == true || extraRequired.contains(name)

    val supportsSearch: Boolean get() = supports("search")
    val supportsSkip: Boolean get() = supports("skip")

    /** Genre options, preferring the `extra` declaration over the legacy field. */
    val genreOptions: List<String>
        get() = extraFor("genre")?.options?.takeIf { it.isNotEmpty() } ?: genres

    /**
     * Whether this catalog can be asked for a plain feed.
     *
     * A catalog with `search` marked required is search-only and must never
     * become a Home shelf - Anime Kitsu's `kitsu-anime-list` is exactly this,
     * and requesting it without a query returns nothing useful. The same is
     * true of any other required extra we have no value for; `genre` is the one
     * exception, because a required genre can be satisfied from its own options.
     */
    val isBrowsable: Boolean
        get() {
            if (requires("search")) return false
            val unsatisfiable = extra.filter { it.isRequired && it.name != "genre" }
            if (unsatisfiable.isNotEmpty()) return false
            if (requires("genre") && genreOptions.isEmpty()) return false
            return true
        }
}

@Serializable
data class AddonBehaviorHints(
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
)

@Serializable
data class AddonManifest(
    val id: String = "",
    val name: String = "",
    val version: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
    val resources: List<AddonResource> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
    val behaviorHints: AddonBehaviorHints? = null,
) {
    fun resource(name: String): AddonResource? = resources.firstOrNull { it.name == name }

    fun provides(name: String): Boolean = resource(name) != null

    /**
     * Whether this addon should be asked for [resourceName] about [id].
     *
     * `idPrefixes` is how an addon says "only ask me about IMDb ids" or "only
     * about kitsu ids". Honouring it is what keeps a fan-out from being N
     * pointless requests: without it every anime lookup also asks every
     * IMDb-only addon, which answers 404 slowly.
     */
    fun handles(resourceName: String, type: String, id: String): Boolean {
        val res = resource(resourceName) ?: return false
        val allowedTypes = res.types ?: types
        if (allowedTypes.isNotEmpty() && !allowedTypes.contains(type)) return false
        val prefixes = res.idPrefixes ?: idPrefixes
        if (prefixes.isNullOrEmpty()) return true
        return prefixes.any { id.startsWith(it) }
    }
}

/** One entry of an `addon_catalog` response: a manifest plus where it lives. */
@Serializable
data class AddonDescriptor(
    val transportUrl: String = "",
    val transportName: String? = null,
    val manifest: AddonManifest = AddonManifest(),
)

@Serializable
data class AddonCatalogResponse(val addons: List<AddonDescriptor> = emptyList())

@Serializable
data class TvTrailer(
    val source: String = "",
    val type: String? = null,
)

@Serializable
data class TvLink(
    val name: String = "",
    val category: String = "",
    val url: String = "",
)

/**
 * A movie, series or anime, as returned by both `catalog` and `meta`.
 *
 * One model serves both because Cinemeta's catalog returns *full* meta objects
 * rather than the previews the spec allows - verified August 2026: logo,
 * background, poster, description, runtime and cast were present on 50 of 50
 * catalog items. That is what lets Home paint a hero and a shelf from one
 * request, and lets a detail page render instantly from the item already in
 * memory while the episode list loads.
 */
@Serializable
data class TvItem(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val releaseInfo: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val imdbRating: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val runtime: String? = null,
    val released: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val writer: List<String> = emptyList(),
    val country: String? = null,
    val awards: String? = null,
    val status: String? = null,
    val trailers: List<TvTrailer> = emptyList(),
    val links: List<TvLink> = emptyList(),
    val videos: List<TvEpisode> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,
    /** Anime Kitsu emits this alongside `id`, which is what makes cross-source dedupe exact. */
    @SerialName("kitsu_id") val kitsuId: String? = null,
) {
    /**
     * Whether this has episodes to list.
     *
     * Derived from the payload rather than from [type], deliberately. Anime
     * Kitsu's catalog is declared `anime` in the manifest while its items call
     * themselves `series`, and community addons invent types freely - so the
     * presence of `videos` is the only signal that does not need a lookup table
     * kept in step with an ecosystem nobody here controls.
     */
    val hasEpisodes: Boolean get() = videos.isNotEmpty()

    /** The YouTube id of the first trailer, which Koda can play natively. */
    val trailerYoutubeId: String?
        get() = trailers.firstOrNull { it.source.isNotBlank() }?.source

    val seasons: List<Int>
        get() = videos.mapNotNull { it.season }.filter { it > 0 }.distinct().sorted()

    /** Specials (season 0) are kept but never lead, which is how every client orders them. */
    fun episodesInSeason(season: Int): List<TvEpisode> =
        videos.filter { it.season == season }.sortedBy { it.episode ?: Int.MAX_VALUE }
}

@Serializable
data class TvEpisode(
    val id: String = "",
    val title: String? = null,
    /** Cinemeta sends `name`; the spec documents `title`. Both appear in the wild. */
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Cinemeta's alias for [episode]. */
    val number: Int? = null,
    val released: String? = null,
    val firstAired: String? = null,
    val overview: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val rating: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: ""
    val episodeNumber: Int? get() = episode ?: number
    val summary: String? get() = overview ?: description
    val airDate: String? get() = released ?: firstAired
}

@Serializable
data class TvCatalogResponse(
    val metas: List<TvItem> = emptyList(),
    val cacheMaxAge: Long? = null,
)

@Serializable
data class TvMetaResponse(val meta: TvItem? = null)
