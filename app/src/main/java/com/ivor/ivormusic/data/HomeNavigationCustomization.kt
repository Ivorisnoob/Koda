package com.ivor.ivormusic.data

/** The tab Koda opens on. The [id]s are stored and frozen. */
enum class StartTab(val id: String) {
    /** Wherever the user was last, which is how Koda has always opened. */
    LAST("last"),
    HOME("home"),
    SEARCH("search"),
    LIBRARY("library");

    companion object {
        fun fromId(id: String?): StartTab = entries.firstOrNull { it.id == id } ?: LAST
    }
}

/** The mode Koda opens in. The [id]s are stored and frozen. */
enum class StartMode(val id: String) {
    LAST("last"),
    MUSIC("music"),
    VIDEO("video");

    companion object {
        fun fromId(id: String?): StartMode = entries.firstOrNull { it.id == id } ?: LAST
    }
}

/**
 * When the navigation bar names its tabs. [AUTO] is each bar's own habit: the
 * floating bar names only the open tab and the standard bar names them all.
 * The [id]s are stored and frozen.
 */
enum class NavTabLabels(val id: String) {
    AUTO("auto"),
    ALWAYS("always"),
    SELECTED("selected"),
    NEVER("never");

    companion object {
        fun fromId(id: String?): NavTabLabels = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * How Koda opens and how its navigation bar behaves, as one snapshot. The
 * defaults are the app as it shipped before any of this was a setting.
 */
data class HomeNavigationCustomization(
    val startTab: StartTab = StartTab.LAST,
    val startMode: StartMode = StartMode.LAST,
    val tabLabels: NavTabLabels = NavTabLabels.AUTO,
    /** Whether the floating navigation bar slides away as a page scrolls. */
    val barHidesOnScroll: Boolean = true,
)
