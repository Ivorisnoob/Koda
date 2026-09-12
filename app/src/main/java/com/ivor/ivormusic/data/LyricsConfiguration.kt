package com.ivor.ivormusic.data

/** Stable provider IDs, also matching the source credited on lyrics. */
data class LyricsConfiguration(
    val providerOrder: List<String> = DEFAULT_ORDER,
    val disabledProviders: Set<String> = emptySet(),
    val remoteEnabled: Boolean = true,
    val preferSynced: Boolean = true,
    val allowPlainText: Boolean = true
) {
    fun normalized(): LyricsConfiguration = copy(
        providerOrder = (providerOrder.filter { it in DEFAULT_ORDER } + DEFAULT_ORDER).distinct(),
        disabledProviders = disabledProviders.intersect(DEFAULT_ORDER.toSet())
    )

    val enabledProviders: List<String>
        get() = normalized().providerOrder.filterNot { it in disabledProviders }

    companion object {
        val DEFAULT_ORDER = listOf("YouLyPlus", "BetterLyrics", "NetEase", "SimpMusic", "LRCLIB", "KuGou", "Unison")
    }
}
