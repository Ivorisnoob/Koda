package com.ivor.ivormusic.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
 * URLs open in the browser here. Timestamps and hashtags only become clickable
 * when a handler is supplied, so a screen with nowhere to seek to renders them
 * as ordinary text rather than as a link that does nothing.
 */
@Composable
fun rememberLinkedText(
    rich: RichText,
    onTimestampClick: ((seconds: Long) -> Unit)? = null,
    onBrowseClick: ((browseId: String) -> Unit)? = null
): AnnotatedString {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary

    return remember(rich, linkColor, onTimestampClick, onBrowseClick) {
        if (rich.links.isEmpty()) return@remember AnnotatedString(rich.text)

        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
        )

        buildAnnotatedString {
            append(rich.text)
            rich.links.forEach { link ->
                val handler: (() -> Unit)? = when (val target = link.target) {
                    is RichLinkTarget.Url -> {
                        { openUrl(context, target.url) }
                    }
                    is RichLinkTarget.Timestamp ->
                        onTimestampClick?.let { seek -> { seek(target.seconds) } }
                    is RichLinkTarget.Browse ->
                        onBrowseClick?.let { browse -> { browse(target.browseId) } }
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

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Log.w("RichTextBody", "No handler for $url", e)
    } catch (e: Exception) {
        Log.w("RichTextBody", "Could not open $url", e)
    }
}
