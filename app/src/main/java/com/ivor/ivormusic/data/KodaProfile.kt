package com.ivor.ivormusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ivor.ivormusic.util.KLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One of Koda's own drawn avatars: a shape, a colour and a face, each chosen
 * from a small set, so a profile with no account and no photo still has a
 * picture that is recognisably its own.
 *
 * It is a handful of indices rather than an image, so it costs nothing to
 * store, scales to any size, and a new option is a new number rather than a
 * new asset. Indices are stored: an option may be added to the end of a set,
 * never inserted or removed. An index past the end of its set wraps (see the
 * view), so a profile restored from a newer build still draws.
 */
data class KodaAvatar(
    val shape: Int,
    val color: Int,
    val eyes: Int,
    val mouth: Int,
    val extra: Int,
    val blush: Boolean
) {
    fun encode(): String = "$shape.$color.$eyes.$mouth.$extra.${if (blush) 1 else 0}"

    companion object {
        const val SHAPES = 9
        const val EYES = 6
        const val MOUTHS = 6
        const val EXTRAS = 6

        fun decode(text: String?): KodaAvatar? {
            val parts = text?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return null
            if (parts.size != 6) return null
            return KodaAvatar(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5] == 1)
        }

        fun random(random: kotlin.random.Random = kotlin.random.Random): KodaAvatar = KodaAvatar(
            shape = random.nextInt(SHAPES),
            color = random.nextInt(KodaAvatarColors.size),
            eyes = random.nextInt(EYES),
            mouth = random.nextInt(MOUTHS),
            extra = random.nextInt(EXTRAS),
            blush = random.nextBoolean()
        )

        /** The avatar a profile wears before anyone has chosen one: the same every time for the same id. */
        fun forSeed(seed: String): KodaAvatar = random(kotlin.random.Random(seed.hashCode()))
    }
}

/** One avatar colourway: the fill, the ink the face is drawn in, and the cheeks. */
data class KodaAvatarColor(val fill: Long, val ink: Long, val blush: Long)

/**
 * The avatar colourways.
 *
 * **These are literal colours on purpose, the third place in the app where the
 * colour is the data** (after Super Chat tiers and SponsorBlock categories). An
 * avatar is somebody's picture: it has to be the same peach face in the dark
 * theme, in the light one and under an album's palette, and a role from the
 * colour scheme is by definition none of those. Appended to, never reordered:
 * the index is stored.
 */
val KodaAvatarColors: List<KodaAvatarColor> = listOf(
    KodaAvatarColor(0xFFFFD8C2, 0xFF5A2E1F, 0xFFFF8A65), // peach
    KodaAvatarColor(0xFFC8F2DC, 0xFF1F4D3A, 0xFF5CCB95), // mint
    KodaAvatarColor(0xFFE3D4FF, 0xFF3B2A66, 0xFFA98BFF), // lilac
    KodaAvatarColor(0xFFCFE8FF, 0xFF1D3C5C, 0xFF6FB3F2), // sky
    KodaAvatarColor(0xFFFFF0B8, 0xFF5C4A12, 0xFFF2B63C), // butter
    KodaAvatarColor(0xFFFFD3E0, 0xFF5E2238, 0xFFFF7FA5), // rose
    KodaAvatarColor(0xFFBFEDEA, 0xFF12474A, 0xFF45C1BC), // teal
    KodaAvatarColor(0xFFFFC9C2, 0xFF5C211B, 0xFFFF7961), // coral
    KodaAvatarColor(0xFFDDEBC0, 0xFF34451A, 0xFF9BC253), // moss
    KodaAvatarColor(0xFFF0E2CF, 0xFF4A3B2A, 0xFFD9A066), // sand
    KodaAvatarColor(0xFF2E2A3D, 0xFFF3EEFF, 0xFF8E7CC3), // dusk
    KodaAvatarColor(0xFF1F2A44, 0xFFE8F0FF, 0xFF5B8DEF), // night
)

/** What a profile looks like in Koda: a drawn avatar, or a photo of the user's own. */
data class KodaProfileCard(
    val avatar: KodaAvatar? = null,
    /** A square copy of a photo the user picked, in app storage. Wins over [avatar] while it exists. */
    val photoPath: String? = null,
    /** Bumped on every new photo, so an image cache keyed on the path does not show the old one. */
    val photoVersion: Long = 0L
)

/**
 * The look of each profile, by profile id.
 *
 * **Process-wide state**, for the reason every such store gives: the picture
 * chosen on the profile screen has to change on Home's top bar and in the
 * account switcher, each of which holds its own instance. It is device-wide
 * rather than scoped to the active profile, because it describes every profile
 * in the roster at once.
 */
class KodaProfileStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureLoaded()
    }

    val cards: StateFlow<Map<String, KodaProfileCard>> get() = shared.asStateFlow()

    fun cardFor(profileId: String): KodaProfileCard = shared.value[profileId] ?: KodaProfileCard()

    /** Choose a drawn avatar. A photo, if there was one, steps aside but is kept until replaced. */
    fun setAvatar(profileId: String, avatar: KodaAvatar) {
        write(profileId, cardFor(profileId).copy(avatar = avatar, photoPath = null))
    }

    fun clearPhoto(profileId: String) {
        val card = cardFor(profileId)
        card.photoPath?.let { runCatching { File(it).delete() } }
        write(profileId, card.copy(photoPath = null))
    }

    /**
     * Copy a picked image into app storage as a small square and use it.
     * The original is never referenced again, so it can be moved or deleted.
     */
    suspend fun setPhoto(profileId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val smaller = minOf(bounds.outWidth, bounds.outHeight)
            if (smaller <= 0) return@withContext false
            val options = BitmapFactory.Options().apply {
                inSampleSize = (smaller / PHOTO_SIZE_PX).coerceAtLeast(1)
            }
            val decoded = appContext.contentResolver.openInputStream(source)
                ?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext false
            val side = minOf(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(
                decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side
            )
            val scaled = if (side > PHOTO_SIZE_PX) {
                Bitmap.createScaledBitmap(square, PHOTO_SIZE_PX, PHOTO_SIZE_PX, true)
            } else {
                square
            }
            val directory = File(appContext.filesDir, PHOTO_DIRECTORY).apply { mkdirs() }
            // The id becomes part of a path; a restored profile keeps whatever
            // id its backup claimed, so it is cut down to safe characters.
            val safeId = profileId.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "profile" }
            val target = File(directory, "$safeId.jpg")
            target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val card = cardFor(profileId)
            write(
                profileId,
                card.copy(photoPath = target.absolutePath, photoVersion = System.currentTimeMillis())
            )
            true
        } catch (e: Exception) {
            KLog.w(TAG, "Could not use that photo", e)
            false
        }
    }

    private fun write(profileId: String, card: KodaProfileCard) {
        synchronized(lock) {
            shared.value = shared.value + (profileId to card)
            prefs.edit().putString(KEY_PREFIX + profileId, encode(card)).apply()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            shared.value = prefs.all.mapNotNull { (key, value) ->
                if (!key.startsWith(KEY_PREFIX) || value !is String) return@mapNotNull null
                key.removePrefix(KEY_PREFIX) to decode(value)
            }.toMap()
            loaded = true
        }
    }

    companion object {
        private const val TAG = "KodaProfileStore"
        private const val PREFS_NAME = "koda_profiles"
        private const val KEY_PREFIX = "card_"
        private const val PHOTO_DIRECTORY = "profile_photos"
        private const val PHOTO_SIZE_PX = 512

        private val lock = Any()
        private val shared = MutableStateFlow<Map<String, KodaProfileCard>>(emptyMap())
        @Volatile private var loaded = false

        private fun encode(card: KodaProfileCard): String =
            listOf(card.avatar?.encode().orEmpty(), card.photoPath.orEmpty(), card.photoVersion.toString())
                .joinToString("|")

        private fun decode(text: String): KodaProfileCard {
            val parts = text.split('|')
            return KodaProfileCard(
                avatar = KodaAvatar.decode(parts.getOrNull(0)),
                photoPath = parts.getOrNull(1)?.takeIf { it.isNotBlank() && File(it).exists() },
                photoVersion = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            )
        }
    }
}
