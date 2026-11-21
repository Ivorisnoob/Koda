package com.ivor.ivormusic.ui.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.recap.DayPart
import com.ivor.ivormusic.data.recap.RecapKind
import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One card of the story. */
@Composable
internal fun RecapCard(kind: RecapCardKind, s: RecapStoryScope) {
    when (kind) {
        RecapCardKind.DOOR -> DoorCard(s)
        RecapCardKind.SONG -> SongCard(s)
        RecapCardKind.RACE -> RaceCard(s)
        RecapCardKind.ARTIST -> ArtistCard(s)
        RecapCardKind.REGULARS -> RegularsCard(s)
        RecapCardKind.RIDGE -> RidgeCard(s)
        RecapCardKind.HOURS -> HoursCard(s)
        RecapCardKind.CAME_BACK -> CameBackCard(s)
        RecapCardKind.CALENDAR -> CalendarCard(s)
        RecapCardKind.SITTING -> SittingCard(s)
        RecapCardKind.NEW_ARTIST -> NewArtistCard(s)
        RecapCardKind.ODD_HOUR -> OddHourCard(s)
        RecapCardKind.PRESSING -> PressingCard(s)
    }
}

private val gutter = 24.dp

/** A number counting up on a critical spring, recomposing only when the shown digit changes. */
@Composable
private fun countUp(s: RecapStoryScope, target: Int, at: Float): Int {
    val shown by remember(target) {
        derivedStateOf { (target * springAt(s.clock() - at, 0.6f)).roundToInt().coerceIn(0, target) }
    }
    return shown
}

private val numbers: NumberFormat get() = NumberFormat.getIntegerInstance()

@Composable
private fun Line(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    maxLines: Int = 2,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** One line that shrinks to fit rather than wrapping: titles, names. */
@Composable
private fun FitLine(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    BasicText(
        text = text,
        style = style.copy(color = color),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = style.fontSize, stepSize = 1.sp),
        modifier = modifier,
    )
}

/** A square as big as the room allows, centred at [biasX] across it: where a window goes. */
@Composable
private fun WindowRoom(
    modifier: Modifier,
    fraction: Float,
    biasX: Float = 0f,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val side = min(maxWidth.value * fraction, maxHeight.value * 0.94f).dp
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.BiasAlignment(biasX, 0f)) {
            content(Modifier.size(side))
        }
    }
}

// ---------------------------------------------------------------------------
// The door

@Composable
private fun DoorCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val r = s.recap
    // The covers of the period take turns in the window, ending on the first.
    val covers = r.topSongs.take(5).map { it.id }
    val order = if (covers.size > 1) covers.drop(1) + covers.first() else covers
    val phase by remember { derivedStateOf { ((s.clock() - 0.55f) / 0.92f).toInt() } }
    val showing = if (phase < 0 || order.isEmpty()) covers.firstOrNull() else order[phase.coerceAtMost(order.lastIndex)]
    val days = countUp(s, r.activeDays, 0.75f)
    val plays = countUp(s, r.plays, 0.87f)

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        FitLine(periodTitle(context, r.period), RecapType.hero, Modifier.rise(s.clock, 0.25f))
        if (r.period.inProgress) {
            Line(
                stringResource(R.string.recap_door_so_far), RecapType.title,
                Modifier.rise(s.clock, 0.37f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WindowRoom(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp), 0.68f, biasX = 0.35f) { m ->
            Box(m.then(s.slot(RecapShape.COOKIE9, showing)))
        }
        Line(pluralStringResource(R.plurals.recap_door_days, days, days), RecapType.title, Modifier.rise(s.clock, 0.7f))
        Spacer(Modifier.height(6.dp))
        val heard = totalText(context, r.heardMs)
        Line(
            stringResource(
                if (r.measuredShare >= 0.9f) R.string.recap_heard_line else R.string.recap_heard_line_about,
                playsText(context, plays), heard,
            ),
            RecapType.body, Modifier.rise(s.clock, 0.82f),
        )
        Spacer(Modifier.height(4.dp))
        Line(rangeText(context, r), RecapType.small, Modifier.rise(s.clock, 0.94f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------------
// Your song

@Composable
private fun SongCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val song = s.recap.topSongs.first()
    val accent = s.colourOf(song.id)
    val days = countUp(s, song.days, 0.55f)
    val elapsed = s.recap.elapsedDays

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        WindowRoom(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 20.dp), 0.74f) { m ->
            Box(m.then(s.slot(RecapShape.CLOVER4, song.id)))
        }
        Line(stringResource(R.string.recap_song_line1), RecapType.title, Modifier.rise(s.clock, 0.45f))
        Text(
            accented(
                stringResource(R.string.recap_song_on),
                pluralStringResource(R.plurals.recap_days, days, days),
                accent,
            ),
            style = RecapType.big,
            modifier = Modifier.rise(s.clock, 0.51f),
        )
        Spacer(Modifier.height(6.dp))
        Line("${song.title} · ${song.artist}", RecapType.body, Modifier.rise(s.clock, 0.57f), maxLines = 1)
        Spacer(Modifier.height(18.dp))
        // One tick a day of the period; lit on the days it was played.
        val lit = remember(song) { song.dayIndexes.toHashSet() }
        val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val n = elapsed.coerceAtLeast(1)
            val pitch = size.width / n
            val w = max(1.5f, min(pitch * 0.55f, 4.dp.toPx()))
            for (d in 0 until n) {
                val p = springAt(s.clock() - 0.9f - d * (1.3f / n), 0.35f)
                if (p <= 0f) break
                val x = pitch * (d + 0.5f)
                val h = size.height * p
                drawLine(
                    color = if (d in lit) accent else dim,
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - h),
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).rise(s.clock, 2.1f)) {
            val first = s.recap.period.firstDay
            Line(dayText(context, first, abbreviated = true), RecapType.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Line(dayText(context, first.plusDays(elapsed - 1L), abbreviated = true), RecapType.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Line(
            stringResource(R.string.recap_heard_line, playsText(context, song.plays), durationText(context, song.heardMs)),
            RecapType.body, Modifier.rise(s.clock, 2.2f), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.rise(s.clock, 2.3f), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { s.play(listOf(song.id), song.id) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.recap_play))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = s.openProof) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.recap_proof_open))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// The overtake

/** The race starts once the window has landed on the line's start. */
private const val RACE_AT = 1.15f
private const val RACE_SECONDS = 3.6f

@Composable
private fun RaceCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val race = s.recap.race ?: return
    val days = race.leaderSoFar.size
    val max = max(race.leaderSoFar.last(), race.passedSoFar.last())
    val leaderColour = s.colourOf(race.leader.id)
    val passedColour = s.colourOf(race.passed.id)
    val crossAt = RACE_AT + RACE_SECONDS * race.dayIndex / (days - 1).coerceAtLeast(1)
    val running by remember { derivedStateOf { s.clock() >= RACE_AT } }
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val dateLabel = dayText(context, s.recap.period.firstDay.plusDays(race.dayIndex.toLong()), abbreviated = true)
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        Line(
            stringResource(R.string.recap_race_on, dayText(context, s.recap.period.firstDay.plusDays(race.dayIndex.toLong()))),
            RecapType.title, Modifier.rise(s.clock, crossAt + 0.3f),
        )
        Text(
            accented(stringResource(R.string.recap_race_passed, "%1\$s", race.passed.title), race.leader.title, leaderColour),
            style = RecapType.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.rise(s.clock, crossAt + 0.38f),
        )
        Line(
            stringResource(R.string.recap_race_kept), RecapType.body,
            Modifier.padding(top = 6.dp).rise(s.clock, RACE_AT + RACE_SECONDS + 0.25f), color = quiet,
        )
        var chart by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 28.dp, bottom = 12.dp)
                .onSizeChanged { chart = it }
        ) {
            val headPx = with(density) { 56.dp.toPx() }
            val otherPx = with(density) { 44.dp.toPx() }
            // The chart leaves room on the right for the heads and their counts.
            val origin = Offset(headPx / 2f, chart.height - with(density) { 12.dp.toPx() })
            val w = (chart.width - headPx * 1.9f).coerceAtLeast(1f)
            val h = (chart.height - headPx).coerceAtLeast(1f)
            fun day(): Float = ((s.clock() - RACE_AT) / RACE_SECONDS).coerceIn(0f, 1f) * (days - 1)
            Canvas(Modifier.fillMaxSize()) {
                val d = day()
                drawRace(origin, w, h, days, max, race.passedSoFar, race.leaderSoFar, d, passedColour, leaderColour, quiet.copy(alpha = 0.3f), 4.dp.toPx())
                if (d >= race.dayIndex) {
                    val at = raceHead(origin, w, h, days, max, race.leaderSoFar, race.dayIndex.toFloat())
                    val a = springAt(s.clock() - crossAt, 0.4f)
                    drawLine(
                        ink.copy(alpha = 0.5f * a), Offset(at.x, origin.y), at, strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)),
                    )
                    val label = measurer.measure(dateLabel, TextStyle(color = ink.copy(alpha = a), fontSize = 14.sp))
                    drawText(label, topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height - 40.dp.toPx()))
                }
                if (running) {
                    for ((series, colour, r) in listOf(
                        Triple(race.leaderSoFar, ink, headPx),
                        Triple(race.passedSoFar, quiet, otherPx),
                    )) {
                        val head = raceHead(origin, w, h, days, max, series, d)
                        val count = measurer.measure(
                            numbers.format(series[d.toInt().coerceIn(0, series.lastIndex)]),
                            TextStyle(color = colour, fontSize = 14.sp),
                        )
                        drawText(count, topLeft = Offset(head.x + r / 2f + 6.dp.toPx(), head.y - count.size.height / 2f))
                    }
                }
            }
            // The song it passed rides its own line, smaller.
            AsyncImage(
                model = s.coverOf(race.passed.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset {
                        val head = raceHead(origin, w, h, days, max, race.passedSoFar, day())
                        IntOffset((head.x - otherPx / 2f).roundToInt(), (head.y - otherPx / 2f).roundToInt())
                    }
                    .size(44.dp)
                    .drop(s.clock, 0.55f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            // The leader's head is the window itself: it flies down to the
            // start, then rides the line.
            Box(
                Modifier
                    .offset {
                        val head = raceHead(origin, w, h, days, max, race.leaderSoFar, day())
                        IntOffset((head.x - headPx / 2f).roundToInt(), (head.y - headPx / 2f).roundToInt())
                    }
                    .size(56.dp)
                    .then(s.slot(RecapShape.COOKIE9, race.leader.id, follow = running))
            )
        }
        Row(Modifier.fillMaxWidth().rise(s.clock, 0.5f)) {
            val first = s.recap.period.firstDay
            Line(dayText(context, first, abbreviated = true), RecapType.small, color = quiet)
            Spacer(Modifier.weight(1f))
            Line(dayText(context, first.plusDays(days - 1L), abbreviated = true), RecapType.small, color = quiet)
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// Your artist

@Composable
private fun ArtistCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val artist = s.recap.topArtists.first()
    // Two different sleeves of theirs, the most played first.
    val sleeves = remember(artist) {
        val seen = HashSet<String?>()
        artist.songIds.filter { seen.add(s.coverOf(it)) }.take(2)
    }
    val second by remember { derivedStateOf { s.clock() >= 3f } }
    val showing = if (second && sleeves.size > 1) sleeves[1] else sleeves.firstOrNull()
    val share = s.recap.topArtistShare
    val shareText = when {
        share > 0.45f && share < 0.55f -> stringResource(R.string.recap_artist_half)
        share > 0.3f && share <= 0.37f -> stringResource(R.string.recap_artist_third)
        share > 0.22f && share <= 0.3f -> stringResource(R.string.recap_artist_quarter)
        share > 0.17f && share <= 0.22f -> stringResource(R.string.recap_artist_fifth)
        else -> stringResource(R.string.recap_artist_percent, (share * 100).roundToInt())
    }

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        WindowRoom(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), 0.72f, biasX = 0.3f) { m ->
            Box(m.then(s.slot(RecapShape.SUNNY, showing, rotation = 8f)))
        }
        // The name, a word at a time, as large as fits.
        artist.name.split(" ").filter { it.isNotBlank() }.take(4).forEachIndexed { i, word ->
            FitLine(word, RecapType.hero.copy(fontSize = 64.sp, lineHeight = 64.sp), Modifier.rise(s.clock, 0.55f + i * 0.12f))
        }
        Spacer(Modifier.height(14.dp))
        Line(shareText, RecapType.title, Modifier.rise(s.clock, 1.4f))
        Spacer(Modifier.height(6.dp))
        Line(
            listOf(
                pluralStringResource(R.plurals.recap_songs, artist.songs, artist.songs),
                pluralStringResource(R.plurals.recap_on_days, artist.days, artist.days),
                playsText(context, artist.plays),
            ).joinToString(" · "),
            RecapType.body, Modifier.rise(s.clock, 1.5f), color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        FilledTonalButton(onClick = { s.play(artist.songIds, null) }, modifier = Modifier.rise(s.clock, 1.6f)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.recap_play_artist))
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// Your regulars

private val rankShapes = listOf(RecapShape.SUNNY, RecapShape.CLOVER4, RecapShape.COOKIE9, RecapShape.SOFTBURST, RecapShape.FLOWER)

/** The sleeve an artist is shown with everywhere in a story: their most played song's. */
internal fun RecapStoryScope.sleeveOf(artistIndex: Int): String? {
    val artist = recap.topArtists.getOrNull(artistIndex) ?: return null
    // The band's artist keeps the sleeve the hours ring shows, so the two agree.
    if (artist.name == recap.hours.bandArtist) recap.hours.bandSongId
        ?.takeIf { song(it)?.artist == artist.name }?.let { return it }
    return artist.songIds.firstOrNull()
}

@Composable
private fun RegularsCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val artists = s.recap.topArtists
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val maxHeard = artists.maxOf { it.heardMs }.coerceAtLeast(1L)
    val total = artists.sumOf { it.plays }

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        Line(stringResource(R.string.recap_regulars_title), RecapType.big, Modifier.rise(s.clock, 0.3f))
        Line(
            stringResource(
                R.string.recap_regulars_sub,
                pluralStringResource(R.plurals.recap_artists, artists.size, artists.size),
                numbers.format(total), numbers.format(s.recap.plays),
            ),
            RecapType.body, Modifier.padding(top = 4.dp).rise(s.clock, 0.38f), color = quiet,
        )
        Column(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp)) {
            artists.forEachIndexed { i, artist ->
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val side = (min(maxHeight.value * 1.15f, maxWidth.value * 0.36f) *
                        (0.62f + 0.38f * sqrt(artist.heardMs.toFloat() / maxHeard))).dp
                    val left = i % 2 == 0
                    val at = 0.75f + i * 0.16f
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (left) Arrangement.Start else Arrangement.End,
                    ) {
                        @Composable
                        fun Sleeve() {
                            // Required, not bounded by the row: the zig-zag's
                            // sleeves are meant to reach into their neighbours.
                            if (i == 0) {
                                Box(Modifier.requiredSize(side).then(s.slot(rankShapes[0], s.sleeveOf(0))))
                            } else {
                                AsyncImage(
                                    model = s.coverOf(s.sleeveOf(i)),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .requiredSize(side)
                                        .drop(s.clock, at, turn = if (left) -14f else 14f)
                                        .clip(shapeOf(rankShapes[i % rankShapes.size]))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                            }
                        }
                        @Composable
                        fun Words() {
                            Column(
                                Modifier.padding(horizontal = 16.dp).rise(s.clock, at + 0.08f),
                                horizontalAlignment = if (left) Alignment.Start else Alignment.End,
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${i + 1}", style = RecapType.title, color = quiet)
                                    Spacer(Modifier.width(10.dp))
                                    Line(artist.name, RecapType.title, maxLines = 1)
                                }
                                Line(
                                    "${playsText(context, artist.plays)} · ${pluralStringResource(R.plurals.recap_on_days, artist.days, artist.days)}",
                                    RecapType.small, color = quiet, maxLines = 1,
                                )
                            }
                        }
                        if (left) { Sleeve(); Box(Modifier.weight(1f)) { Words() } }
                        else { Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { Words() }; Sleeve() }
                    }
                }
            }
        }
        // How close it was, and who never missed a week.
        val first = artists[0]
        val gap = first.plays - (artists.getOrNull(1)?.plays ?: 0)
        Line(
            if (artists.size > 1 && gap == 0) stringResource(R.string.recap_regulars_tied, first.name, artists[1].name)
            else stringResource(R.string.recap_regulars_by, first.name, pluralStringResource(R.plurals.recap_plays_count, gap, gap)),
            RecapType.title, Modifier.rise(s.clock, 2.2f),
        )
        val loyal = artists.filter { it.everyWeek }
        if (s.recap.elapsedDays >= 21 && loyal.isNotEmpty()) {
            Line(
                when (loyal.size) {
                    1 -> stringResource(R.string.recap_regulars_week_one, loyal[0].name)
                    artists.size -> stringResource(R.string.recap_regulars_week_all)
                    else -> pluralStringResource(R.plurals.recap_regulars_week_some, loyal.size, loyal.size)
                },
                RecapType.body, Modifier.padding(top = 4.dp).rise(s.clock, 2.32f), color = quiet,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// Who came round when

@Composable
private fun RidgeCard(s: RecapStoryScope) {
    val artists = s.recap.topArtists.filter { it.plays >= 5 }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val room = MaterialTheme.colorScheme.surfaceContainerLowest
    val ridges = remember(artists) { artists.map { ridgeOf(it.hourMs) } }
    val partLine: @Composable (DayPart, String) -> String = { part, name ->
        stringResource(
            when (part) {
                DayPart.MORNING -> R.string.recap_ridge_morning
                DayPart.AFTERNOON -> R.string.recap_ridge_afternoon
                DayPart.EVENING -> R.string.recap_ridge_evening
                DayPart.NIGHT -> R.string.recap_ridge_night
            },
            name,
        )
    }
    val first = artists[0]
    val other = artists.drop(1).firstOrNull { it.dayPart != first.dayPart }

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        Line(partLine(first.dayPart, first.name), RecapType.title, Modifier.rise(s.clock, 3.55f))
        Line(
            if (other != null) partLine(other.dayPart, other.name) else stringResource(R.string.recap_ridge_same, artists[1].name),
            RecapType.title, Modifier.rise(s.clock, 3.67f),
        )
        Line(stringResource(R.string.recap_ridge_sub), RecapType.small, Modifier.padding(top = 6.dp).rise(s.clock, 3.79f), color = quiet)
        Column(Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp)) {
            artists.forEachIndexed { i, artist ->
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.Bottom) {
                    Column(
                        Modifier.width(76.dp).padding(bottom = 4.dp).rise(s.clock, 0.5f + i * 0.05f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (i == 0) {
                            Box(Modifier.size(40.dp).then(s.slot(RecapShape.COOKIE9, s.sleeveOf(0))))
                        } else {
                            AsyncImage(
                                model = s.coverOf(s.sleeveOf(s.recap.topArtists.indexOf(artist))),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(40.dp).clip(shapeOf(RecapShape.COOKIE9))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                        }
                        Line(artist.name, RecapType.small.copy(fontSize = 12.sp, lineHeight = 14.sp), maxLines = 2, textAlign = TextAlign.Center)
                    }
                    val colour = s.colourOf(s.sleeveOf(s.recap.topArtists.indexOf(artist)))
                    Canvas(Modifier.weight(1f).fillMaxSize()) {
                        val sweep = ((s.clock() - 0.95f) / 2.6f).coerceIn(0f, 1f)
                        // Each row's landscape rises into the one above it.
                        drawRidge(
                            ridge = ridges[i],
                            left = 0f, right = size.width,
                            baseline = size.height - 2.dp.toPx(),
                            height = size.height * 1.35f,
                            sweep = sweep,
                            fill = colour.copy(alpha = 0.88f),
                            under = room,
                            edge = ink,
                            edgeWidth = 1.5.dp.toPx(),
                        )
                        if (sweep in 0.001f..0.999f) {
                            val x = size.width * sweep
                            drawLine(ink.copy(alpha = 0.6f), Offset(x, -size.height * 0.4f), Offset(x, size.height), 1.dp.toPx())
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 76.dp, top = 6.dp).rise(s.clock, 0.6f)) {
            listOf("00", "06", "12", "18", "24").forEachIndexed { i, label ->
                Line(label, RecapType.small, color = quiet)
                if (i < 4) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// The hours

@Composable
private fun HoursCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val h = s.recap.hours
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val hourColour = remember(s.recap) {
        // Each hour coloured by the song heard most in it.
        val bySong = HashMap<Int, HashMap<String, Long>>()
        s.recap.arcs.forEach { arc ->
            val hour = (arc.startMinute / 60f).toInt().coerceIn(0, 23)
            val m = bySong.getOrPut(hour) { HashMap() }
            m[arc.songId] = (m[arc.songId] ?: 0L) + (arc.heardMinutes * 60_000).toLong()
        }
        bySong.mapValues { (_, m) -> m.maxByOrNull { it.value }?.key }
    }
    val windowSong = h.bandArtist?.let { name ->
        s.recap.topArtists.indexOfFirst { it.name == name }.takeIf { it >= 0 }?.let { s.sleeveOf(it) }
    } ?: h.bandSongId

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawHoursRing(
                        center = center,
                        inner = r * 0.44f,
                        span = r * 0.44f,
                        heardMs = h.heardMs,
                        colourOf = { hour -> hourColour[hour]?.let(s::colourOf) },
                        grow = { hour -> springAt(s.clock() - 0.6f - hour * 0.055f, 0.45f) },
                        empty = quiet.copy(alpha = 0.18f),
                        band = h.bandStart,
                        bandProgress = ((s.clock() - 2.45f) / 0.55f).coerceIn(0f, 1f),
                        bandColour = ink,
                    )
                }
                Box(Modifier.size(side * 0.4f).then(s.slot(RecapShape.COOKIE12, windowSong)))
                // The four quarters of the day.
                listOf(0 to Alignment.TopCenter, 6 to Alignment.CenterEnd, 12 to Alignment.BottomCenter, 18 to Alignment.CenterStart)
                    .forEach { (hour, align) ->
                        Text(
                            "%02d".format(hour), style = RecapType.small, color = quiet,
                            modifier = Modifier.align(align).rise(s.clock, 0.7f),
                        )
                    }
            }
        }
        Spacer(Modifier.height(16.dp))
        Line(stringResource(R.string.recap_hours_line1), RecapType.title, Modifier.rise(s.clock, 2.55f))
        Line(
            stringResource(R.string.recap_hours_line2, hourName(context, h.bandStart), hourName(context, h.bandStart + 2)),
            RecapType.title, Modifier.rise(s.clock, 2.63f),
        )
        h.bandArtist?.let {
            Line(stringResource(R.string.recap_hours_mostly, it), RecapType.body, Modifier.padding(top = 6.dp).rise(s.clock, 2.71f), color = quiet)
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// The song that came back

@Composable
private fun CameBackCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val back = s.recap.cameBack ?: return
    val accent = s.colourOf(back.song.id)
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val moved by remember { derivedStateOf { s.clock() >= 1.05f } }
    val gap = countUp(s, back.gapDays, 1.05f)

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val w = maxWidth
            val ghost = w * 0.4f
            val final = w * 0.52f
            val ghostTop = 12.dp
            val finalLeft = w - final
            val finalTop = maxHeight - final - 30.dp
            // The line from where it was left to where it came back.
            Canvas(Modifier.fillMaxSize()) {
                val p = springAt(s.clock() - 1.05f, 0.9f)
                if (p <= 0f) return@Canvas
                val from = Offset(ghost.toPx() / 2f, ghostTop.toPx() + ghost.toPx() / 2f)
                val to = Offset(finalLeft.toPx() + final.toPx() / 2f, finalTop.toPx() + final.toPx() / 2f)
                val end = from + (to - from) * p
                drawLine(
                    ink.copy(alpha = 0.55f), from, end, strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f)),
                )
            }
            AsyncImage(
                model = s.coverOf(back.song.id),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(y = ghostTop)
                    .size(ghost)
                    .graphicsLayer { alpha = 0.42f * springAt(s.clock() - 0.95f, 0.3f) }
                    .clip(RoundedCornerShape(20.dp)),
            )
            Line(
                dateText(context, back.lastBefore, abbreviated = true), RecapType.body,
                Modifier.offset(y = ghostTop + ghost + 8.dp).rise(s.clock, 0.7f), color = quiet,
            )
            // The window: first in the June sleeve's place, then off to its return.
            Box(
                Modifier
                    .offset(x = if (moved) finalLeft else 0.dp, y = if (moved) finalTop else ghostTop)
                    .size(if (moved) final else ghost)
                    .then(s.slot(RecapShape.SOFTBURST, back.song.id, response = if (moved) 0.9f else 0.55f))
            )
            Line(
                dateText(context, back.returnedAt, abbreviated = true), RecapType.body,
                Modifier.offset(x = finalLeft, y = finalTop + final + 6.dp).rise(s.clock, 2.55f),
            )
            // The gap counts up in the corner the two sleeves leave free.
            Text(
                accented("%1\$s", pluralStringResource(R.plurals.recap_days, gap, gap), accent),
                style = RecapType.big,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ghostTop + 28.dp)
                    .width(w - ghost - 12.dp)
                    .rise(s.clock, 1.05f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Line(stringResource(R.string.recap_back_title, back.song.title), RecapType.title, Modifier.rise(s.clock, 2.85f))
        Line(
            pluralStringResource(R.plurals.recap_back_week, back.playsWithinWeek, back.playsWithinWeek),
            RecapType.body, Modifier.padding(top = 6.dp).rise(s.clock, 2.95f), color = quiet,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = { s.play(listOf(back.song.id), back.song.id) }, modifier = Modifier.rise(s.clock, 3.05f)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.recap_play))
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// The calendar

@Composable
private fun CalendarCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val days = s.recap.days
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val columns = (days.size + 6) / 7
    val maxHeard = days.maxOf { it.heardMs }.coerceAtLeast(1L)
    val segments = s.recap.segments
    val kind = s.recap.period.kind

    // Where the finger is, in days; the card sweeps it once on its own, and a
    // drag along the stitches takes over from there.
    var dragging by remember { mutableStateOf(false) }
    var dragDay by remember { mutableFloatStateOf(0f) }
    val autoDay by remember {
        derivedStateOf {
            val p = ((s.clock() - 2.65f) / 2.9f).coerceIn(0f, 1f)
            val eased = p * p * (3f - 2f * p)
            eased * (columns - 1) * 7f + 3f
        }
    }
    val scrubDay = (if (dragging) dragDay else autoDay).roundToInt().coerceIn(0, days.lastIndex)
    // The window shows the nearest day with listening at or before the finger.
    val shownDay = (scrubDay downTo 0).firstOrNull { days[it].songId != null } ?: days.indexOfFirst { it.songId != null }
    val shownSong = days.getOrNull(shownDay)?.songId
    val scrubbing by remember { derivedStateOf { dragging || s.clock() in 2.6f..5.6f } }

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        if (segments != null) {
            val firstColour = s.rawColourOf(segments.firstSongId)
            val lastColour = s.rawColourOf(segments.lastSongId)
            val firstName = segmentName(context, kind, segments.firstStart, true).capitalised()
            val lastName = segmentName(context, kind, segments.lastStart, false).capitalised()
            if (firstColour != null) {
                Line(
                    stringResource(R.string.recap_calendar_was, firstName, stringResource(colourName(firstColour))),
                    RecapType.title, Modifier.rise(s.clock, 0.35f),
                )
            }
            if (lastColour != null) {
                val lastWord = colourName(lastColour)
                Line(
                    if (lastWord == R.string.recap_colour_black && firstColour?.let(::colourName) != lastWord) {
                        stringResource(R.string.recap_calendar_went_out)
                    } else {
                        stringResource(R.string.recap_calendar_was, lastName, stringResource(lastWord))
                    },
                    RecapType.title, Modifier.rise(s.clock, 0.45f),
                )
            }
        } else {
            Line(stringResource(R.string.recap_calendar_plain), RecapType.title, Modifier.rise(s.clock, 0.35f))
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val pitch = min(maxWidth.value / columns, 34f)
            val gridWidth = (pitch * columns).dp
            val gridHeight = (pitch * 7).dp
            val windowSide = min(maxWidth.value * 0.3f, (maxHeight - gridHeight).value * 0.6f).coerceAtLeast(40f).dp
            val gridTop = maxHeight - gridHeight - 8.dp
            val density = LocalDensity.current
            val colX = { day: Int -> ((day / 7) + 0.5f) * pitch }
            // The day above the finger, and the window over it.
            val windowX = colX(shownDay.coerceAtLeast(0)).dp.coerceIn(windowSide / 2, maxWidth - windowSide / 2)
            val windowTop = gridTop - windowSide - 44.dp
            Text(
                dayText(context, days[shownDay.coerceAtLeast(0)].date, weekday = true, abbreviated = true),
                style = RecapType.body,
                modifier = Modifier
                    .offset(x = (windowX - 80.dp).coerceAtLeast(0.dp), y = windowTop - 30.dp)
                    .width(160.dp)
                    .graphicsLayer { alpha = springAt(s.clock() - 2.6f, 0.3f) },
                textAlign = TextAlign.Center,
            )
            Box(
                Modifier
                    .offset(x = windowX - windowSide / 2, y = windowTop)
                    .size(windowSide)
                    .then(s.slot(RecapShape.COOKIE9, shownSong, follow = scrubbing))
            )
            // Month or week marks over the grid.
            val marks = remember(days) {
                days.filter { d ->
                    when (kind) {
                        RecapKind.YEAR -> d.date.dayOfMonth == 1
                        // Years of it: a month a label would be a smear.
                        RecapKind.ALL_TIME -> d.date.dayOfYear == 1 || d.index == 0
                        RecapKind.MONTH -> d.index % 7 == 0
                        RecapKind.WEEK -> d.index == 0
                    }
                }
            }
            marks.forEach { d ->
                Text(
                    when (kind) {
                        RecapKind.YEAR -> d.date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                        RecapKind.ALL_TIME -> d.date.year.toString()
                        else -> dayText(context, d.date, abbreviated = true)
                    },
                    style = RecapType.small.copy(fontSize = 11.sp),
                    color = quiet,
                    maxLines = 1,
                    modifier = Modifier.offset(x = ((d.index / 7) * pitch).dp, y = gridTop - 18.dp).rise(s.clock, 0.8f),
                )
            }
            Canvas(
                Modifier
                    .offset(y = gridTop)
                    .size(gridWidth, gridHeight)
                    .pointerInput(columns) {
                        detectDragGestures(
                            onDragStart = { at ->
                                dragging = true
                                dragDay = ((at.x / with(density) { pitch.dp.toPx() }).toInt() * 7 + (at.y / with(density) { pitch.dp.toPx() }).toInt()).toFloat()
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            change.consume()
                            val p = with(density) { pitch.dp.toPx() }
                            val col = (change.position.x / p).toInt().coerceIn(0, columns - 1)
                            val row = (change.position.y / p).toInt().coerceIn(0, 6)
                            val day = (col * 7 + row).coerceIn(0, days.lastIndex)
                            if (day != dragDay.roundToInt()) s.haptics.subtle()
                            dragDay = day.toFloat()
                        }
                    }
            ) {
                val p = pitch.dp.toPx()
                for (d in days) {
                    val col = d.index / 7
                    val row = d.index % 7
                    val appear = springAt(s.clock() - 0.6f - col * (1.6f / columns) - row * 0.012f, 0.4f)
                    if (appear <= 0f) continue
                    val k = if (d.heardMs > 0) 0.5f + 0.5f * sqrt(d.heardMs.toFloat() / maxHeard) else 1f
                    val side = p * 0.86f * k * appear
                    val topLeft = Offset(col * p + (p - side) / 2f, row * p + (p - side) / 2f)
                    if (d.songId != null) {
                        drawRoundRect(
                            s.colourOf(d.songId), topLeft, androidx.compose.ui.geometry.Size(side, side),
                            androidx.compose.ui.geometry.CornerRadius(side * 0.26f),
                        )
                    } else {
                        drawRoundRect(
                            quiet.copy(alpha = 0.22f), topLeft, androidx.compose.ui.geometry.Size(side, side),
                            androidx.compose.ui.geometry.CornerRadius(side * 0.26f), style = Stroke(1.dp.toPx()),
                        )
                    }
                }
                if (scrubbing) {
                    val col = scrubDay / 7
                    val row = scrubDay % 7
                    drawRoundRect(
                        quiet, Offset(col * p, row * p), androidx.compose.ui.geometry.Size(p, p),
                        androidx.compose.ui.geometry.CornerRadius(p * 0.3f), style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Line(stringResource(R.string.recap_calendar_hint), RecapType.small, Modifier.rise(s.clock, 1.0f), color = quiet)
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// The long sitting, the newcomer, the odd hour

@Composable
private fun SittingCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val sitting = s.recap.longSitting ?: return
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        WindowRoom(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), 0.62f) { m ->
            Box(m.then(s.slot(RecapShape.FLOWER, sitting.songIds.firstOrNull())))
        }
        // The rest of the run, in the order it played.
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sitting.songIds.drop(1).take(5).forEachIndexed { i, id ->
                AsyncImage(
                    model = s.coverOf(id),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .drop(s.clock, 0.5f + i * 0.07f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
        }
        Line(
            stringResource(R.string.recap_sitting_when, dateText(context, sitting.startedAt, weekday = true), timeText(context, sitting.startedAt)),
            RecapType.title, Modifier.rise(s.clock, 0.3f),
        )
        Line(
            pluralStringResource(R.plurals.recap_sitting_line, sitting.plays, sitting.plays, durationText(context, sitting.heardMs)),
            RecapType.body, Modifier.padding(top = 6.dp).rise(s.clock, 0.4f), color = quiet,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun NewArtistCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val newcomer = s.recap.newArtist ?: return
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        WindowRoom(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 12.dp), 0.62f, biasX = 0.15f) { m ->
            Box(m.then(s.slot(RecapShape.CLOVER4, newcomer.songId, rotation = 20f)))
        }
        FitLine("${newcomer.name}.", RecapType.hero.copy(fontSize = 60.sp, lineHeight = 60.sp), Modifier.rise(s.clock, 0.15f))
        Line(
            stringResource(R.string.recap_new_first, dateText(context, newcomer.firstPlayedAt)),
            RecapType.title, Modifier.padding(top = 8.dp).rise(s.clock, 0.25f),
        )
        Line(
            pluralStringResource(R.plurals.recap_new_days, newcomer.days, newcomer.days),
            RecapType.body, Modifier.padding(top = 6.dp).rise(s.clock, 0.35f), color = quiet,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = { s.play(listOf(newcomer.songId), newcomer.songId) }, modifier = Modifier.rise(s.clock, 0.45f)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.recap_play))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun OddHourCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val play = s.recap.oddHour ?: return
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        FitLine(stringResource(R.string.recap_odd_at, timeText(context, play.timestamp)), RecapType.big.copy(fontSize = 44.sp, lineHeight = 48.sp), Modifier.rise(s.clock, 0.06f))
        FitLine(stringResource(R.string.recap_odd_this), RecapType.big.copy(fontSize = 44.sp, lineHeight = 48.sp), Modifier.rise(s.clock, 0.22f))
        WindowRoom(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp), 0.6f, biasX = 0.4f) { m ->
            Box(m.then(s.slot(RecapShape.SOFTBURST, play.songId)))
        }
        Line("${play.title} · ${play.artist}", RecapType.title.copy(fontSize = 20.sp), Modifier.rise(s.clock, 0.45f), maxLines = 1)
        Line(dateText(context, play.timestamp, weekday = true), RecapType.body, Modifier.padding(top = 4.dp).rise(s.clock, 0.53f), color = quiet)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = { s.play(listOf(play.songId), play.songId) }, modifier = Modifier.rise(s.clock, 0.6f)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.recap_play))
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ---------------------------------------------------------------------------
// The pressing and the note

@Composable
private fun PressingCard(s: RecapStoryScope) {
    val context = LocalContext.current
    val r = s.recap
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val groove = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
    val top = r.topSongs.firstOrNull()?.id
    val note = noteText(context, s)

    Column(Modifier.fillMaxSize().padding(horizontal = gutter).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            FitLine(periodTitle(context, r.period), RecapType.hero.copy(fontSize = 56.sp, lineHeight = 56.sp), Modifier.rise(s.clock, 0.5f))
            Line(rangeText(context, r), RecapType.small, Modifier.rise(s.clock, 0.6f), color = quiet)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawPressing(
                        center = center,
                        radius = size.minDimension / 2f,
                        arcs = r.arcs,
                        days = r.elapsedDays,
                        progress = ((s.clock() - 0.9f) / 4.2f).coerceIn(0f, 1f),
                        colourOf = s::colourOf,
                        groove = groove,
                    )
                }
                // The record's label is the window, carried in from the last card.
                Box(Modifier.size(side * 0.26f).then(s.slot(RecapShape.COOKIE9, top)))
            }
        }
        // The key, so the picture can be read.
        listOf(R.string.recap_key_rings, R.string.recap_key_time, R.string.recap_key_colour, R.string.recap_key_length)
            .forEachIndexed { i, res ->
                Line(stringResource(res), RecapType.small.copy(fontSize = 12.sp, lineHeight = 15.sp), Modifier.rise(s.clock, 4.9f + i * 0.08f), color = quiet, maxLines = 1)
            }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).rise(s.clock, 5.3f)) {
            Line("KODA · ${r.catalog}", RecapType.small.copy(letterSpacing = 0.12.em), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Line("${playsText(context, r.plays)} · ${totalText(context, r.heardMs)}", RecapType.small, color = quiet, maxLines = 1)
        }
        if (note != null) {
            Text(note, style = RecapType.body, modifier = Modifier.padding(top = 14.dp).rise(s.clock, 5.6f))
        }
        Row(Modifier.padding(top = 16.dp).rise(s.clock, 5.8f), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = s.openShare, colors = ButtonDefaults.buttonColors()) {
                Icon(Icons.Rounded.IosShare, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recap_share))
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { s.play(r.topSongs.map { it.id }, null) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.recap_play_top))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * The note: one sentence from a fixed grammar, never written by a model -
 * the light at the start, the light at the end, and who kept the room.
 * Null until the covers' colours it names are known.
 */
internal fun noteText(context: android.content.Context, s: RecapStoryScope): String? {
    val seg = s.recap.segments ?: return null
    val firstColour = s.rawColourOf(seg.firstSongId) ?: return null
    val lastColour = s.rawColourOf(seg.lastSongId) ?: return null
    val kind = s.recap.period.kind
    val firstName = segmentName(context, kind, seg.firstStart, true)
    val lastName = segmentName(context, kind, seg.lastStart, false)
    // Whole phrases, so each language words them its own way: "In June",
    // "On Monday", "In the first week"; "By November", "By Sunday".
    val atStart = context.getString(
        if (kind == RecapKind.WEEK) R.string.recap_note_on else R.string.recap_note_in, firstName,
    ).capitalised()
    val byEnd = context.getString(R.string.recap_note_by, lastName).capitalised()
    val firstWord = context.getString(colourName(firstColour))
    val lastWord = context.getString(colourName(lastColour))
    val artist = seg.lastArtist
    val nights = seg.lastArtistNights
    val nightsText = context.resources.getQuantityString(R.plurals.recap_nights, nights, nights)
    return when {
        artist != null && nights >= 3 && firstWord == lastWord ->
            context.getString(R.string.recap_note_same, atStart, firstWord, artist, nightsText)
        artist != null && nights >= 3 ->
            context.getString(R.string.recap_note, atStart, firstWord, byEnd, lastWord, artist, nightsText)
        firstWord == lastWord -> context.getString(R.string.recap_note_colour_same, atStart, firstWord)
        else -> context.getString(R.string.recap_note_colours, atStart, firstWord, byEnd, lastWord)
    }
}
