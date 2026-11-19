package com.ivor.ivormusic.ui.artist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.ArtistCredit
import com.ivor.ivormusic.ui.components.AdaptiveModalSheet
import com.ivor.ivormusic.ui.player.OptionGroup
import com.ivor.ivormusic.ui.player.OptionRow
import com.ivor.ivormusic.ui.player.OptionRowDivider
import com.ivor.ivormusic.ui.player.OptionRowTrailing

/** What a tap on a song's artist name leads to. */
internal sealed interface ArtistTap {
    /** One artist's page; [id] is its channel when known, else the page searches by name. */
    data class Open(val name: String, val id: String?) : ArtistTap

    /** A song by several artists, tapped on their joined name: ask which one. */
    data class Choose(val credits: List<ArtistCredit>) : ArtistTap
}

/**
 * Where a tap on [tapped] goes, for a song credited to [credits].
 *
 * A collaboration's artist line is every name joined ("Calvin Harris, Dua
 * Lipa"), and opening it used to search for that whole string, which lands on
 * one of them or neither (#373). Now a tap that names one credited artist
 * opens exactly that channel, and a tap on the joined line asks which. A song
 * without credits - a device file, a row saved before they were kept - opens
 * by name as it always did.
 */
internal fun resolveArtistTap(tapped: String, credits: List<ArtistCredit>): ArtistTap {
    val name = tapped.trim()
    credits.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?.let { return ArtistTap.Open(it.name, it.id) }
    return when {
        credits.size >= 2 -> ArtistTap.Choose(credits)
        credits.size == 1 -> ArtistTap.Open(credits[0].name, credits[0].id)
        else -> ArtistTap.Open(name, null)
    }
}

/**
 * The names a song menu offers as "Go to artist" rows: each credited artist
 * of a collaboration, or the one display name.
 */
internal fun artistMenuNames(displayName: String?, credits: List<ArtistCredit>): List<String> =
    if (credits.size >= 2) credits.map { it.name } else listOfNotNull(displayName)

/** Asks which artist of a collaboration to open. */
@Composable
internal fun ArtistChooserSheet(
    credits: List<ArtistCredit>,
    onChoose: (ArtistCredit) -> Unit,
    onDismiss: () -> Unit,
) {
    AdaptiveModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.artist_chooser_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 16.dp),
            )
            OptionGroup {
                credits.forEachIndexed { index, credit ->
                    if (index > 0) OptionRowDivider()
                    OptionRow(
                        icon = Icons.Rounded.AccountCircle,
                        title = credit.name,
                        trailing = OptionRowTrailing.CHEVRON,
                        onClick = { onChoose(credit) },
                    )
                }
            }
        }
    }
}
