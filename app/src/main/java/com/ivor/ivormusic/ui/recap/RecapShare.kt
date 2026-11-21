package com.ivor.ivormusic.ui.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.recap.Recap
import com.ivor.ivormusic.ui.lyricsshare.LyricsShareExport
import com.ivor.ivormusic.ui.standby.appleSpring
import com.ivor.ivormusic.ui.theme.MontserratFamily
import com.ivor.ivormusic.util.KLog
import com.ivor.ivormusic.util.rememberKodaHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What a share image shows. */
internal enum class RecapDesign(val labelRes: Int) {
    PRESSING(R.string.recap_share_design_pressing),
    SONGS(R.string.recap_share_design_songs),
    ARTISTS(R.string.recap_share_design_artists),
}

/** The image's shape, on a 360-unit-wide grid exported at 1080 pixels. */
internal enum class RecapFormat(val heightUnits: Int, val labelRes: Int) {
    STORY(640, R.string.recap_share_story),
    FEED(450, R.string.recap_share_feed),
    SQUARE(360, R.string.recap_share_square);

    val heightPx: Int get() = heightUnits * PX_PER_UNIT
}

private const val PX_PER_UNIT = 3
private const val EXPORT_WIDTH_PX = 360 * PX_PER_UNIT

/** Design units to this density's dp and sp, so a unit is always three pixels, whatever the scales. */
private class Units(private val density: Density) {
    fun dp(u: Float): Dp = with(density) { (u * PX_PER_UNIT).toDp() }
    fun sp(u: Float): TextUnit = with(density) { (u * PX_PER_UNIT).toSp() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecapSharePage(recap: Recap, colours: Map<String, Color>, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberKodaHaptics()
    val scheme = MaterialTheme.colorScheme
    var design by remember { mutableStateOf(RecapDesign.PRESSING) }
    var format by remember { mutableStateOf(RecapFormat.STORY) }
    var numbers by remember { mutableStateOf(true) }
    var layer by remember { mutableStateOf<GraphicsLayer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) { delay(2200); saved = false } }
    LaunchedEffect(failed) { if (failed) { delay(3000); failed = false } }
    LaunchedEffect(design, format, numbers) { saved = false }

    fun export(share: Boolean) {
        val captured = layer ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                val bitmap = captured.toImageBitmap()
                val what = "${periodTitle(context, recap.period)} ${design.name.lowercase()}"
                val ok = if (share) {
                    LyricsShareExport.shareImage(context, bitmap, "recap", what, context.getString(R.string.recap_share_chooser))
                } else {
                    LyricsShareExport.saveImage(context, bitmap, "recap", what)
                }
                if (ok) {
                    haptics.confirm()
                    if (!share) saved = true
                } else {
                    haptics.reject()
                    failed = true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                KLog.e("RecapShare", "Could not capture the image", e)
                haptics.reject()
                failed = true
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.surfaceContainerLowest)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.recap_close))
            }
            Text(
                stringResource(R.string.recap_share_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        // The image, as large as the room allows, laid out at export size.
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val roomW = constraints.maxWidth.toFloat()
            val roomH = constraints.maxHeight.toFloat()
            AnimatedContent(
                targetState = Triple(design, format, numbers),
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) + scaleIn(appleSpring(0.45f, 0.85f), initialScale = 0.95f)) togetherWith
                        fadeOut(tween(140))
                },
                contentAlignment = Alignment.Center,
                label = "recapShare",
            ) { (shownDesign, shownFormat, shownNumbers) ->
                val fit = min(roomW / EXPORT_WIDTH_PX, roomH / shownFormat.heightPx)
                val cardLayer = rememberGraphicsLayer()
                if (shownDesign == design && shownFormat == format && shownNumbers == numbers) {
                    SideEffect { layer = cardLayer }
                }
                Box(
                    Modifier
                        .shadow(20.dp, RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .layout { measurable, _ ->
                            val placeable = measurable.measure(Constraints.fixed(EXPORT_WIDTH_PX, shownFormat.heightPx))
                            layout((EXPORT_WIDTH_PX * fit).roundToInt(), (shownFormat.heightPx * fit).roundToInt()) {
                                placeable.placeWithLayer(0, 0) {
                                    scaleX = fit
                                    scaleY = fit
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                            }
                        }
                        .drawWithContent {
                            cardLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(cardLayer)
                        }
                ) {
                    RecapShareImage(recap, colours, shownDesign, shownFormat, shownNumbers)
                }
            }
        }
        // What to share, and in which shape.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Choices(RecapDesign.entries, design, { stringResource(it.labelRes) }) { haptics.tick(); design = it }
            Choices(RecapFormat.entries, format, { stringResource(it.labelRes) }) { haptics.tick(); format = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recap_share_numbers), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = numbers, onCheckedChange = { haptics.toggle(it); numbers = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = { export(share = false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (saved) R.string.recap_share_saved else R.string.recap_share_save))
                }
                Button(onClick = { export(share = true) }, enabled = !busy, modifier = Modifier.weight(2f)) {
                    Icon(Icons.Rounded.IosShare, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recap_share))
                }
            }
            if (failed) {
                Text(
                    stringResource(R.string.recap_share_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> Choices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Unselected on a tenth of the accent: a default container vanishes on this page.
    val colors = ToggleButtonDefaults.colors(
        containerColor = scheme.primary.copy(alpha = 0.1f),
        contentColor = scheme.onSurface,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { if (option != selected) onSelect(option) },
                modifier = Modifier.weight(1f),
                colors = colors,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * One share image at its export size. Its colours are the story's: the
 * scheme's darkest surface leaning toward the image's leading cover, and the
 * covers' own colours on the marks that show them.
 */
@Composable
private fun RecapShareImage(recap: Recap, colours: Map<String, Color>, design: RecapDesign, format: RecapFormat, numbers: Boolean) {
    val u = Units(LocalDensity.current)
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lead = when (design) {
        RecapDesign.ARTISTS -> recap.topArtists.firstOrNull()?.songIds?.firstOrNull()
        else -> recap.topSongs.firstOrNull()?.id
    }
    val ground = roomTone(scheme.surfaceContainerLowest, lead?.let { colours[it] }, if (design == RecapDesign.PRESSING) 0.08f else 0.2f)
    val ink = scheme.onSurface
    val quiet = scheme.onSurfaceVariant
    val colourOf: (String?) -> Color = { id -> id?.let { colours[it] }?.onRoom() ?: quiet.copy(alpha = 0.5f) }
    val display = TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, color = ink)
    val body = TextStyle(color = ink)
    val square = format == RecapFormat.SQUARE

    Column(
        Modifier
            .fillMaxSize()
            .background(ground)
            .padding(horizontal = u.dp(24f), vertical = u.dp(if (square) 20f else 28f))
    ) {
        // Header: what this is, and when.
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (design) {
                            RecapDesign.PRESSING -> R.string.recap_share_head_pressing
                            RecapDesign.SONGS -> R.string.recap_share_head_songs
                            RecapDesign.ARTISTS -> R.string.recap_share_head_artists
                        }
                    ),
                    style = body.copy(fontSize = u.sp(11f), color = quiet, letterSpacing = 0.08.em),
                )
                Text(
                    periodTitle(context, recap.period),
                    style = display.copy(fontSize = u.sp(if (square) 30f else 40f), lineHeight = u.sp(if (square) 32f else 42f)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (recap.period.inProgress) {
                Text(stringResource(R.string.recap_door_so_far), style = body.copy(fontSize = u.sp(12f), color = quiet))
            }
        }
        Spacer(Modifier.height(u.dp(if (square) 10f else 18f)))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (design) {
                RecapDesign.PRESSING -> SharePressing(recap, colourOf, u, ink, format)
                RecapDesign.SONGS -> ShareSongs(recap, colourOf, u, ink, quiet, display, body, numbers, square)
                RecapDesign.ARTISTS -> ShareArtists(recap, colourOf, u, ink, quiet, display, body, numbers, square)
            }
        }
        Spacer(Modifier.height(u.dp(10f)))
        // Footer: the maker and the pressing's number.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.recap_share_footer),
                style = body.copy(fontSize = u.sp(10f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (numbers) "${playsText(context, recap.plays)} · ${recap.catalog}" else recap.catalog,
                style = body.copy(fontSize = u.sp(10f), color = quiet, letterSpacing = 0.06.em),
            )
        }
    }
}

@Composable
private fun SharePressing(recap: Recap, colourOf: (String?) -> Color, u: Units, ink: Color, format: RecapFormat) {
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val groove = ink.copy(alpha = 0.05f)
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawPressing(center, size.minDimension / 2f, recap.arcs, recap.elapsedDays, 1f, { colourOf(it) }, groove)
                }
                CoverIn(recap.topSongs.firstOrNull()?.thumbnailUrl, RecapShape.COOKIE9, Modifier.size(side * 0.26f))
            }
        }
        if (format != RecapFormat.SQUARE) {
            Spacer(Modifier.height(u.dp(10f)))
            listOf(R.string.recap_key_rings, R.string.recap_key_time, R.string.recap_key_colour, R.string.recap_key_length).forEach {
                Text(stringResource(it), style = TextStyle(fontSize = u.sp(9.5f), lineHeight = u.sp(12.5f), color = quiet), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShareSongs(
    recap: Recap,
    colourOf: (String?) -> Color,
    u: Units,
    ink: Color,
    quiet: Color,
    display: TextStyle,
    body: TextStyle,
    numbers: Boolean,
    square: Boolean,
) {
    val context = LocalContext.current
    val songs = recap.topSongs.take(5)
    val first = songs.firstOrNull() ?: return
    Column(Modifier.fillMaxSize()) {
        // The first, large, in its window.
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoverIn(first.thumbnailUrl, RecapShape.CLOVER4, Modifier.size(u.dp(if (square) 92f else 130f)))
            Spacer(Modifier.width(u.dp(16f)))
            Column(Modifier.weight(1f)) {
                Text("1", style = display.copy(fontSize = u.sp(14f), color = colourOf(first.id)))
                Text(first.title, style = display.copy(fontSize = u.sp(if (square) 20f else 24f), lineHeight = u.sp(if (square) 22f else 27f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(first.artist, style = body.copy(fontSize = u.sp(12f), color = quiet), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (numbers) {
                    Text(
                        "${context.resources.getQuantityString(R.plurals.recap_on_days, first.days, first.days)} · ${durationText(context, first.heardMs)}",
                        style = body.copy(fontSize = u.sp(11f), color = quiet), maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(u.dp(if (square) 12f else 20f)))
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            songs.drop(1).forEachIndexed { i, song ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 2}", style = display.copy(fontSize = u.sp(16f), color = colourOf(song.id)),
                        modifier = Modifier.width(u.dp(24f)),
                    )
                    CoverIn(song.thumbnailUrl, RecapShape.COOKIE9, Modifier.size(u.dp(if (square) 34f else 44f)))
                    Spacer(Modifier.width(u.dp(12f)))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = body.copy(fontSize = u.sp(13f), fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = body.copy(fontSize = u.sp(11f), color = quiet), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (numbers) {
                        Text(
                            context.resources.getQuantityString(R.plurals.recap_on_days, song.days, song.days),
                            style = body.copy(fontSize = u.sp(10f), color = quiet), textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareArtists(
    recap: Recap,
    colourOf: (String?) -> Color,
    u: Units,
    ink: Color,
    quiet: Color,
    display: TextStyle,
    body: TextStyle,
    numbers: Boolean,
    square: Boolean,
) {
    val context = LocalContext.current
    val artists = recap.topArtists.take(5)
    if (artists.isEmpty()) return
    val maxHeard = artists.maxOf { it.heardMs }.coerceAtLeast(1L)
    val shapes = listOf(RecapShape.SUNNY, RecapShape.CLOVER4, RecapShape.COOKIE9, RecapShape.SOFTBURST, RecapShape.FLOWER)
    Column(Modifier.fillMaxSize()) {
        artists.forEachIndexed { i, artist ->
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val side = (min(maxHeight.value * 1.08f, maxWidth.value * 0.34f) *
                    (0.62f + 0.38f * sqrt(artist.heardMs.toFloat() / maxHeard))).dp
                val left = i % 2 == 0
                val thumb = artist.songIds.firstOrNull()?.let { recap.songs[it]?.thumbnailUrl }
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (left) Arrangement.Start else Arrangement.End,
                ) {
                    val words = @Composable {
                        Column(
                            Modifier.padding(horizontal = u.dp(12f)),
                            horizontalAlignment = if (left) Alignment.Start else Alignment.End,
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${i + 1}", style = display.copy(fontSize = u.sp(15f), color = colourOf(artist.songIds.firstOrNull())))
                                Spacer(Modifier.width(u.dp(8f)))
                                Text(artist.name, style = display.copy(fontSize = u.sp(if (square) 15f else 18f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (numbers) {
                                Text(
                                    context.resources.getQuantityString(R.plurals.recap_on_days, artist.days, artist.days),
                                    style = body.copy(fontSize = u.sp(10.5f), color = quiet), maxLines = 1,
                                )
                            }
                        }
                    }
                    if (left) {
                        CoverIn(thumb, shapes[i], Modifier.size(side))
                        Box(Modifier.weight(1f)) { words() }
                    } else {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { words() }
                        CoverIn(thumb, shapes[i], Modifier.size(side))
                    }
                }
            }
        }
    }
}

/** A cover in one of the story's shapes. */
@Composable
private fun CoverIn(url: String?, shape: RecapShape, modifier: Modifier) {
    Box(modifier.clip(shapeOf(shape)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (url != null) {
            AsyncImage(
                model = googleImageAtSize(url, 544) ?: url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
