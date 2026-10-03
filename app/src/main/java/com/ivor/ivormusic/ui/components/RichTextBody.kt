package com.ivor.ivormusic.ui.components

import com.ivor.ivormusic.util.KLog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.ivor.ivormusic.data.RichLinkTarget
import com.ivor.ivormusic.data.RichText

/**
 * Turn [RichText] into an [AnnotatedString] whose links are tappable.
 *
 * The spans come from YouTube's own `commandRuns`, so this never has to guess
 * where a link starts - see `parseRichText`. Pass the result straight to a
 * `Text`; Compose handles hit-testing and accessibility for link annotations.
 *
 * URLs go through [LocalUriHandler], which `MainActivity` overrides so a
 * YouTube link opens inside Koda and anything else leaves for the browser.
 * A channel mention (a `UC…` browse id) opens that channel the same way when
 * the caller supplies no [onBrowseClick]; it used to render as plain text on
 * every screen, because none supplied one. Timestamps and hashtags only become
 * clickable when a handler is supplied, so a screen with nowhere to seek to
 * renders them as ordinary text rather than as a link that does nothing.
 */
@Composable
fun rememberLinkedText(
    rich: RichText,
    onTimestampClick: ((seconds: Long) -> Unit)? = null,
    onBrowseClick: ((browseId: String) -> Unit)? = null
): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary

    return remember(rich, linkColor, onTimestampClick, onBrowseClick, uriHandler) {
        if (rich.links.isEmpty()) return@remember AnnotatedString(rich.text)

        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
        )

        buildAnnotatedString {
            append(rich.text)
            rich.links.forEach { link ->
                val handler: (() -> Unit)? = when (val target = link.target) {
                    is RichLinkTarget.Url -> {
                        { openUri(uriHandler, target.url) }
                    }
                    is RichLinkTarget.Timestamp ->
                        onTimestampClick?.let { seek -> { seek(target.seconds) } }
                    is RichLinkTarget.Browse ->
                        onBrowseClick?.let { browse -> { browse(target.browseId) } }
                            ?: target.browseId.takeIf { it.startsWith("UC") }?.let { id ->
                                { openUri(uriHandler, "https://www.youtube.com/channel/$id") }
                            }
                }
                if (handler == null) return@forEach

                addLink(
                    LinkAnnotation.Clickable(
                        tag = "rich_link_${link.start}",
                        styles = styles,
                        linkInteractionListener = { handler() }
                    ),
                    start = link.start,
                    end = link.endExclusive
                )
            }
        }
    }
}

private fun openUri(uriHandler: UriHandler, url: String) {
    try {
        uriHandler.openUri(url)
    } catch (e: Exception) {
        KLog.w("RichTextBody", "Could not open $url", e)
    }
}
