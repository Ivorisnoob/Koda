package com.ivor.ivormusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivor.ivormusic.data.Song

/**
 * Songs picked for one action together, in music mode.
 *
 * Entered from a song's long-press sheet ("Select"); while it is on, a tap on
 * any song row ticks it rather than playing it, and the Home navigation bar
 * becomes the actions for the picked songs ([SongSelectionDeck]). It leaves
 * on its own when the last song is unticked, on back, and when the page that
 * offered "Select all" goes away.
 *
 * Kept in pick order, so Play next and a new playlist take the songs in the
 * order they were chosen. Ids are held beside the list so a row can ask
 * whether it is ticked without walking the list on every frame.
 */
@Stable
class SongSelection {
    private val picked = mutableStateListOf<Song>()
    private val pickedIds = mutableStateListOf<String>()

    val songs: List<Song> get() = picked
    val count: Int get() = picked.size
    val isActive: Boolean by derivedStateOf { picked.isNotEmpty() }

    /**
     * The songs the page on screen lists, for "Select all". Set by a page
     * while it is shown, cleared when it leaves; empty hides the action.
     */
    var available by mutableStateOf<List<Song>>(emptyList())

    /**
     * Removes the picked songs from the playlist on screen, where that page
     * can edit its songs. Null hides the action.
     */
    var removeAction by mutableStateOf<((List<Song>) -> Unit)?>(null)

    fun isSelected(song: Song): Boolean = song.id in pickedIds

    fun start(song: Song) {
        if (isSelected(song)) return
        picked += song
        pickedIds += song.id
    }

    fun toggle(song: Song) {
        val index = pickedIds.indexOf(song.id)
        if (index >= 0) {
            picked.removeAt(index)
            pickedIds.removeAt(index)
        } else {
            start(song)
        }
    }

    val allSelected: Boolean
        get() = available.isNotEmpty() && available.all { it.id in pickedIds }

    /** Every song on the page, or none of them if they are all picked already. */
    fun toggleAll() {
        if (allSelected) {
            clear()
        } else {
            available.forEach(::start)
        }
    }

    fun clear() {
        picked.clear()
        pickedIds.clear()
    }
}

/**
 * The one selection music mode has. A default instance rather than a provider:
 * every song row in the app reads it, and the rows are drawn from screens that
 * share no common parent below the activity. It holds no data of its own,
 * only which rows are ticked right now, so one per process is the right size.
 */
val LocalSongSelection = staticCompositionLocalOf { SongSelection() }

/**
 * The tick drawn over a song's artwork while it is picked. Laid over the
 * artwork's own box, so every row style keeps its own shape and size.
 */
@Composable
fun SongSelectedMark(song: Song, modifier: Modifier = Modifier) {
    val selection = LocalSongSelection.current
    AnimatedVisibility(
        visible = selection.isSelected(song),
        enter = fadeIn() + scaleIn(
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            initialScale = 0.6f
        ),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
