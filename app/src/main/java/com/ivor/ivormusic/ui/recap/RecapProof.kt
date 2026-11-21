package com.ivor.ivormusic.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.recap.Recap
import com.ivor.ivormusic.data.recap.RecapSong

/**
 * The plays behind a number: every play of the song in the period, newest
 * first, with when it was and how much of it was heard. What makes a recap
 * checkable rather than taken on trust. It scrolls - a sheet that does not
 * cuts its rows off silently.
 */
@Composable
internal fun ProofSheet(
    recap: Recap,
    song: RecapSong,
    accent: Color,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val plays = recap.playsOf[song.id].orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.62f)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(scheme.surfaceContainer)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurfaceVariant.copy(alpha = 0.4f)))
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.recap_proof_title, plays.size, plays.size, song.title),
                    style = RecapType.title.copy(fontSize = 22.sp, lineHeight = 26.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.recap_proof_sub), style = RecapType.small, color = scheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.recap_play))
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            itemsIndexed(plays, key = { _, p -> "${p.songId}-${p.timestamp}" }) { _, play ->
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${dateText(context, play.timestamp, weekday = true, abbreviated = true)} · ${timeText(context, play.timestamp)}",
                            style = RecapType.body,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            play.listenedMs?.let {
                                stringResource(R.string.recap_proof_of, clockOf(it), clockOf(play.duration))
                            } ?: stringResource(R.string.recap_proof_unmeasured),
                            style = RecapType.small,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // How much of it was heard; an old play's length is all there is.
                    val heard = play.listenedMs?.let { (it.toFloat() / play.duration.coerceAtLeast(1L)).coerceIn(0f, 1f) }
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurface.copy(alpha = 0.12f))) {
                        Box(
                            Modifier
                                .fillMaxWidth(heard ?: 1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (heard != null) accent else scheme.onSurface.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        }
    }
}

private fun clockOf(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
