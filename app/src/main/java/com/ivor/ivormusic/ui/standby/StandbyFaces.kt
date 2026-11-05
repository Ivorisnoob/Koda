package com.ivor.ivormusic.ui.standby

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.LyricsResult
import com.ivor.ivormusic.data.ThemePreferences
import com.ivor.ivormusic.ui.components.SongArtwork
import com.ivor.ivormusic.ui.player.PlayerViewModel
import com.ivor.ivormusic.ui.player.SyncedLyricsView
import com.ivor.ivormusic.util.rememberKodaHaptics
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.delay

/**
 * The faces Standby can show, swiped between sideways and chosen from the
 * zoomed-out picker a long press opens. Persisted by [id]: frozen, append only.
 */
internal enum class StandbyFace(val id: String, @StringRes val titleRes: Int) {
    NOW_PLAYING("now_playing", R.string.standby_face_now_playing),
    WIDGETS("widgets", R.string.standby_face_widgets),
    LYRICS("lyrics", R.string.standby_face_lyrics),
    CLOCK("clock", R.string.standby_face_clock);

    companion object {
        fun fromId(id: String?): StandbyFace = entries.firstOrNull { it.id == id } ?: NOW_PLAYING
    }
}

/**
 * What a widget stack can show, swiped through vertically as a smart stack is.
 * Persisted by [id]: frozen, append only.
 */
internal enum class StandbyWidget(val id: String, @StringRes val titleRes: Int) {
    NOW_PLAYING("now_playing", R.string.standby_widget_now_playing),
    CLOCK("clock", R.string.standby_widget_clock),
    ANALOG("analog", R.string.standby_widget_analog),
    LYRICS("lyrics", R.string.standby_widget_lyrics),
    UP_NEXT("up_next", R.string.standby_widget_up_next),
    CALENDAR("calendar", R.string.standby_widget_calendar);

    companion object {
        fun fromId(id: String?, fallback: StandbyWidget): StandbyWidget =
            entries.firstOrNull { it.id == id } ?: fallback
    }
}

// ---------------------------------------------------------------------------
// Widgets face: two smart stacks
// ---------------------------------------------------------------------------

/**
 * Two stacks side by side (one above the other in portrait), each a vertical
 * pager of widgets that remembers where it was left. By default the left one
 * opens on the song and the right one on the clock.
 */
@Composable
internal fun WidgetsFace(
    viewModel: PlayerViewModel,
    state: StandbyState,
    landscape: Boolean,
    onInteraction: () -> Unit,
) {
    val gap = 14.dp
    val pad = if (landscape) 20.dp else 18.dp
    if (landscape) {
        Row(
            Modifier.fillMaxSize().padding(pad),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            WidgetStack(0, StandbyWidget.NOW_PLAYING, viewModel, state, onInteraction, Modifier.weight(1f).fillMaxHeight())
            WidgetStack(1, StandbyWidget.CLOCK, viewModel, state, onInteraction, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(horizontal = pad, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            WidgetStack(0, StandbyWidget.NOW_PLAYING, viewModel, state, onInteraction, Modifier.weight(1f).fillMaxWidth())
            WidgetStack(1, StandbyWidget.CLOCK, viewModel, state, onInteraction, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun WidgetStack(
    stack: Int,
    default: StandbyWidget,
    viewModel: PlayerViewModel,
    state: StandbyState,
    onInteraction: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) { ThemePreferences(context) }
    val widgets = StandbyWidget.entries
    val start = remember { widgets.indexOf(StandbyWidget.fromId(prefs.getStandbyStackWidget(stack), default)) }
    val pager = rememberPagerState(initialPage = start) { widgets.size }
    val haptics = rememberKodaHaptics()
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.settledPage }.collect { page ->
            prefs.setStandbyStackWidget(stack, widgets[page].id)
            if (!first) haptics.tick()
            first = false
            onInteraction()
        }
    }
    val scheme = MaterialTheme.colorScheme
    // The dots say "there is more here" while the stack moves, then step aside.
    val dotsAlpha by animateFloatAsState(
        targetValue = if (pager.isScrollInProgress) 1f else 0f,
        animationSpec = tween(if (pager.isScrollInProgress) 120 else 900),
        label = "stackDots",
    )
    Box(
        modifier
            .clip(RoundedCornerShape(34.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.55f))
    ) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // A card leaving shrinks and fades a little; the one
                        // arriving grows into place - the stack has depth.
                        val offset = abs((pager.currentPage - page) + pager.currentPageOffsetFraction)
                        val s = 1f - 0.08f * offset.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.6f * offset.coerceIn(0f, 1f)
                    }
                    .padding(20.dp)
            ) {
                StandbyWidgetContent(widgets[page], viewModel, state)
            }
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .graphicsLayer { alpha = dotsAlpha },
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            widgets.indices.forEach { i ->
                val selected = i == pager.currentPage
                Box(
                    Modifier
                        .size(width = 5.dp, height = if (selected) 14.dp else 5.dp)
                        .clip(CircleShape)
                        .background(scheme.onSurface.copy(alpha = if (selected) 0.9f else 0.35f))
                )
            }
        }
    }
}

@Composable
private fun StandbyWidgetContent(widget: StandbyWidget, viewModel: PlayerViewModel, state: StandbyState) {
    when (widget) {
        StandbyWidget.NOW_PLAYING -> NowPlayingWidget(viewModel, state)
        StandbyWidget.CLOCK -> DigitalClockWidget()
        StandbyWidget.ANALOG -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnalogClock(Modifier.fillMaxHeight().aspectRatio(1f))
        }
        StandbyWidget.LYRICS -> LyricsWidget(viewModel, state)
        StandbyWidget.UP_NEXT -> UpNextWidget(viewModel)
        StandbyWidget.CALENDAR -> CalendarWidget()
    }
}

@Composable
private fun WidgetLabel(text: String) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.08.em,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NowPlayingWidget(viewModel: PlayerViewModel, state: StandbyState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tall = maxHeight > maxWidth * 0.9f
        val cover = min(maxHeight.value * (if (tall) 0.48f else 0.62f), maxWidth.value * 0.5f).dp
        if (tall) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                StandbyCover(state.song, state.direction, Modifier.size(cover))
                Spacer(Modifier.weight(1f))
                WidgetTitle(state, TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                MiniTransport(viewModel, state.isPaused, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                StandbyCover(state.song, state.direction, Modifier.size(cover))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    WidgetTitle(state, TextAlign.Start)
                    Spacer(Modifier.height(10.dp))
                    MiniTransport(viewModel, state.isPaused, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun WidgetTitle(state: StandbyState, align: TextAlign) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = state.song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.song.artist.isNotBlank()) {
            Text(
                text = state.song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Previous, play or pause, next: the transport at widget size. */
@Composable
internal fun MiniTransport(viewModel: PlayerViewModel, paused: Boolean, modifier: Modifier = Modifier) {
    val haptics = rememberKodaHaptics()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(stringResource(R.string.cd_previous), 48.dp, { haptics.confirm(); viewModel.skipToPrevious() }) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = null, modifier = Modifier.size(30.dp))
        }
        GlyphButton(
            stringResource(if (paused) R.string.cd_play else R.string.cd_pause),
            58.dp,
            { haptics.toggle(paused); viewModel.togglePlayPause() },
        ) {
            AnimatedContent(
                targetState = paused,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
                label = "miniPlayPause",
            ) { showPlay ->
                Icon(
                    if (showPlay) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        GlyphButton(stringResource(R.string.cd_next), 48.dp, { haptics.confirm(); viewModel.skipToNext() }) {
            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun DigitalClockWidget() {
    val context = LocalContext.current
    val now by rememberMinuteClock()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Measured, not guessed: "00:00" in this very style is set at 100sp
        // and scaled to the card, as Standby's own clock is sized. [scar] A
        // guessed width/3.1 ran off the right edge of a half-width card, and
        // more so at a large font scale.
        val base = MaterialTheme.typography.displayLarge.copy(
            fontSize = 100.sp,
            lineHeight = 1.em,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.03).em,
            fontFeatureSettings = "tnum, lnum",
            color = MaterialTheme.colorScheme.onSurface,
        )
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val roomW = constraints.maxWidth
        val roomH = constraints.maxHeight
        val style = remember(base, roomW, roomH, density) {
            val atHundred = measurer.measure("00:00", base).size
            // 0.8, not the full width: the measure can be taken against the
            // fallback font before the downloadable one arrives wider.
            val byWidth = 100f * roomW * 0.8f / atHundred.width.coerceAtLeast(1)
            val byHeight = 100f * roomH * 0.55f / atHundred.height.coerceAtLeast(1)
            base.copy(fontSize = min(byWidth, byHeight).coerceAtLeast(12f).sp)
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StandbyClock(
                timeText = remember(now) { clockText(context, now) },
                style = style,
            )
            Text(
                text = remember(now) { dateText(now) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LyricsWidget(viewModel: PlayerViewModel, state: StandbyState) {
    val lyrics by viewModel.lyricsResult.collectAsState()
    val position by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val hasLyrics = (lyrics as? LyricsResult.Success)?.lines?.isNotEmpty() == true
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(stringResource(R.string.standby_widget_lyrics))
        Spacer(Modifier.height(4.dp))
        if (hasLyrics) {
            SyncedLyricsView(
                lyricsResult = lyrics,
                currentPositionMs = position,
                isPlaying = isPlaying,
                onSeekTo = viewModel::seekTo,
                modifier = Modifier.fillMaxSize(),
                primaryColor = scheme.primary,
                onSurfaceColor = scheme.onSurface,
                onSurfaceVariantColor = scheme.onSurfaceVariant,
                horizontalGutter = 0.dp,
                anchorFraction = 0.4f,
                cascadeScroll = false,
                // Standby shows the words; a swipe over them still moves the stack.
                interactive = false,
            )
        } else {
            // A bedside screen says nothing rather than "No lyrics".
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                WidgetTitle(state, TextAlign.Start)
            }
        }
    }
}

@Composable
private fun UpNextWidget(viewModel: PlayerViewModel) {
    val queue by viewModel.currentQueue.collectAsState()
    val currentId by viewModel.currentQueueItemId.collectAsState()
    val haptics = rememberKodaHaptics()
    val upcoming = remember(queue, currentId) {
        val at = queue.indexOfFirst { it.id == currentId }
        if (at < 0) emptyList() else queue.drop(at + 1).take(4)
    }
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(stringResource(R.string.standby_widget_up_next))
        Spacer(Modifier.height(10.dp))
        if (upcoming.isEmpty()) {
            Text(
                text = stringResource(R.string.standby_up_next_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        upcoming.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        haptics.confirm()
                        viewModel.skipToQueueItem(item.id)
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SongArtwork(
                    song = item.song,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.song.artist.isNotBlank()) {
                        Text(
                            item.song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** This month at a glance, today ringed in the album's accent. */
@Composable
private fun CalendarWidget() {
    val now by rememberMinuteClock()
    val today = remember(now / 3_600_000L) { LocalDate.now() }
    val scheme = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    val firstDay = remember(locale) { java.time.temporal.WeekFields.of(locale).firstDayOfWeek }
    val month = today.withDayOfMonth(1)
    val lead = ((month.dayOfWeek.value - firstDay.value) + 7) % 7
    val days = month.lengthOfMonth()
    val cells = lead + days
    val rows = (cells + 6) / 7
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = today.month.getDisplayName(JavaTextStyle.FULL, locale).uppercase(locale),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                color = scheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = today.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cell: Dp = min(maxWidth.value / 7f, maxHeight.value / (rows + 1f)).dp
            val font = (cell.value * 0.42f).sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { i ->
                        val day = firstDay.plus(i.toLong())
                        Text(
                            text = day.getDisplayName(JavaTextStyle.NARROW, locale),
                            fontSize = font,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                (0 until rows).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { col ->
                            val n = row * 7 + col - lead + 1
                            Box(Modifier.weight(1f).height(cell), contentAlignment = Alignment.Center) {
                                if (n in 1..days) {
                                    val isToday = n == today.dayOfMonth
                                    if (isToday) {
                                        Box(
                                            Modifier
                                                .size(cell * 0.86f)
                                                .clip(CircleShape)
                                                .background(scheme.primary)
                                        )
                                    }
                                    Text(
                                        text = n.toString(),
                                        fontSize = font,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isToday) scheme.onPrimary else scheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// The analog clock
// ---------------------------------------------------------------------------

/** Seconds into the current twelve hours, ticking once a second on the second. */
@Composable
private fun rememberTwelveHourSeconds(): State<Int> = produceState(secondsIntoTwelveHours()) {
    while (true) {
        val now = System.currentTimeMillis()
        delay(1000L - now % 1000L + 5L)
        value = secondsIntoTwelveHours()
    }
}

private fun secondsIntoTwelveHours(): Int {
    val time = java.time.LocalTime.now()
    return (time.hour % 12) * 3600 + time.minute * 60 + time.second
}

/**
 * A clock face in the album's colours. The second hand steps with a little
 * spring each second, a railway clock's beat; the minute and hour hands move
 * with it, so nothing on the face ever jumps.
 */
@Composable
internal fun AnalogClock(modifier: Modifier = Modifier) {
    val seconds by rememberTwelveHourSeconds()
    // Unwrapped across midnight and noon by the spring's own target only once
    // every twelve hours: a sweep back the long way, while nobody is looking.
    val secondAngle by animateFloatAsState(
        targetValue = seconds * 6f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "secondHand",
    )
    val minuteAngle by animateFloatAsState(seconds * 0.1f, spring(stiffness = Spring.StiffnessLow), label = "minuteHand")
    val hourAngle by animateFloatAsState(seconds / 120f, spring(stiffness = Spring.StiffnessLow), label = "hourHand")
    val scheme = MaterialTheme.colorScheme
    val face = scheme.surfaceContainerHighest
    val tick = scheme.onSurfaceVariant
    val hand = scheme.onSurface
    val accent = scheme.primary
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(face.copy(alpha = 0.7f), radius = r, center = c)
        for (i in 0 until 60) {
            val hour = i % 5 == 0
            rotate(i * 6f, c) {
                drawLine(
                    color = if (hour) tick else tick.copy(alpha = 0.35f),
                    start = Offset(c.x, c.y - r * 0.92f),
                    end = Offset(c.x, c.y - r * (if (hour) 0.80f else 0.86f)),
                    strokeWidth = r * (if (hour) 0.03f else 0.012f),
                    cap = StrokeCap.Round,
                )
            }
        }
        rotate(hourAngle % 360f, c) {
            drawLine(hand, Offset(c.x, c.y + r * 0.06f), Offset(c.x, c.y - r * 0.5f), r * 0.07f, StrokeCap.Round)
        }
        rotate(minuteAngle % 360f, c) {
            drawLine(hand, Offset(c.x, c.y + r * 0.06f), Offset(c.x, c.y - r * 0.76f), r * 0.045f, StrokeCap.Round)
        }
        rotate(secondAngle, c) {
            drawLine(accent, Offset(c.x, c.y + r * 0.16f), Offset(c.x, c.y - r * 0.84f), r * 0.018f, StrokeCap.Round)
        }
        drawCircle(accent, radius = r * 0.05f, center = c)
        drawCircle(face, radius = r * 0.02f, center = c)
    }
}

// ---------------------------------------------------------------------------
// Lyrics face and clock face
// ---------------------------------------------------------------------------

/** The words fill the room; the song sits quietly in a corner with its play button. */
@Composable
internal fun LyricsFace(viewModel: PlayerViewModel, state: StandbyState, landscape: Boolean) {
    val lyrics by viewModel.lyricsResult.collectAsState()
    val position by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val hasLyrics = (lyrics as? LyricsResult.Success)?.lines?.isNotEmpty() == true
    val pad = if (landscape) 40.dp else 24.dp
    Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = 28.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasLyrics) {
                SyncedLyricsView(
                    lyricsResult = lyrics,
                    currentPositionMs = position,
                    isPlaying = isPlaying,
                    onSeekTo = viewModel::seekTo,
                    modifier = Modifier.fillMaxSize(),
                    primaryColor = scheme.primary,
                    onSurfaceColor = scheme.onSurface,
                    onSurfaceVariantColor = scheme.onSurfaceVariant,
                    horizontalGutter = 0.dp,
                    anchorFraction = 0.38f,
                    interactive = false,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AnalogClock(Modifier.fillMaxHeight(0.7f).aspectRatio(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SongPill(viewModel, state)
    }
}

/** A large clock, in the style chosen in the picker, with the date and the song. */
@Composable
internal fun ClockFace(viewModel: PlayerViewModel, state: StandbyState, landscape: Boolean, look: StandbyLook) {
    val context = LocalContext.current
    val now by rememberMinuteClock()
    when (look.clockStyle) {
        StandbyClockStyle.BOLD -> {
            BoldClock(
                timeText = remember(now) { clockText(context, now) },
                dateText = remember(now) { dateText(now) },
                footer = { SongPill(viewModel, state) },
            )
            return
        }
        StandbyClockStyle.MINIMAL -> {
            MinimalClock(
                timeText = remember(now) { clockText(context, now) },
                dateText = remember(now) { dateText(now) },
                footer = { SongPill(viewModel, state) },
            )
            return
        }
        StandbyClockStyle.ANALOG -> Unit
    }
    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = remember(now) { clockText(context, now) },
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = remember(now) { dateText(now) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(18.dp))
            SongPill(viewModel, state)
        }
    }
    if (landscape) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnalogClock(Modifier.fillMaxHeight().aspectRatio(1f))
            Spacer(Modifier.width(40.dp))
            Box(Modifier.weight(1f)) { details() }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnalogClock(Modifier.fillMaxWidth().aspectRatio(1f))
            Spacer(Modifier.height(32.dp))
            Box(Modifier.fillMaxWidth()) { details() }
        }
    }
}

/** The song in one capsule: cover, title, and play or pause. */
@Composable
private fun SongPill(viewModel: PlayerViewModel, state: StandbyState) {
    val haptics = rememberKodaHaptics()
    Row(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArtwork(
            song = state.song,
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                state.song.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.song.artist.isNotBlank()) {
                Text(
                    state.song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlyphButton(
            stringResource(if (state.isPaused) R.string.cd_play else R.string.cd_pause),
            48.dp,
            { haptics.toggle(state.isPaused); viewModel.togglePlayPause() },
        ) {
            Icon(
                if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
