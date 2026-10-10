package com.ivor.ivormusic.data

/**
 * How the mini player shows how far a song has played: the pill filling up
 * behind the title, the outline round the cover, or both (the default).
 *
 * The [id]s are stored and frozen.
 */
enum class MiniPlayerProgress(val id: String) {
    BOTH("both"),
    FILL("fill"),
    OUTLINE("outline");

    val showsFill: Boolean get() = this != OUTLINE
    val showsOutline: Boolean get() = this != FILL

    companion object {
        fun fromId(id: String?): MiniPlayerProgress = entries.firstOrNull { it.id == id } ?: BOTH
    }
}
