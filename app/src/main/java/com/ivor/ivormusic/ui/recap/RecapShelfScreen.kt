package com.ivor.ivormusic.ui.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.googleImageAtSize
import com.ivor.ivormusic.data.recap.Recap
import com.ivor.ivormusic.data.recap.RecapKind
import com.ivor.ivormusic.data.recap.RecapPreview
import com.ivor.ivormusic.ui.settings.settingsChoiceButtonColors
import com.ivor.ivormusic.util.rememberKodaHaptics
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * The shelf of recaps: Week, Month, Year or All time, the chosen period's
 * pressing on a card, and every earlier period as a record spine striped in
 * its covers' colours, so the light of each stretch shows before it is opened.
 * The page wears the app's own theme; the story it opens is always dark.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapShelfScreen(viewModel: RecapViewModel, onBack: () -> Unit) {
    val shelf by viewModel.shelf.collectAsState()
    val hero by viewModel.hero.collectAsState()
    val colours by viewModel.colours.collectAsState()
    val haptics = rememberKodaHaptics()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.recap_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.recap_close))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally).widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "kinds") {
                val kinds = RecapKind.entries
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    kinds.forEachIndexed { index, kind ->
                        ToggleButton(
                            checked = shelf.kind == kind,
                            onCheckedChange = {
                                if (shelf.kind != kind) {
                                    haptics.confirm()
                                    viewModel.selectKind(kind)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = settingsChoiceButtonColors(),
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                kinds.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) {
                            Text(stringResource(kind.labelRes()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            when {
                shelf.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                shelf.previews.isEmpty() -> item(key = "empty") { EmptyShelf(shelf.historyOn) }
                else -> {
                    val selected = shelf.selected
                    if (selected != null) {
                        item(key = "hero") {
                            HeroCard(
                                preview = selected,
                                recap = hero?.takeIf { it.period == selected.period },
                                colours = colours,
                                onOpen = { haptics.confirm(); viewModel.open(selected.period) },
                            )
                        }
                    }
                    if (shelf.previews.size > 1) {
                        item(key = "spines") {
                            Column {
                                Text(
                                    stringResource(R.string.recap_shelf_earlier),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(shelf.previews, key = { it.period.id }) { preview ->
                                        Spine(
                                            preview = preview,
                                            colours = colours,
                                            selected = preview.period == selected?.period,
                                            onClick = { haptics.tick(); viewModel.selectPeriod(preview.period.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!shelf.historyOn) item(key = "off") { HistoryOffNote() }
                    item(key = "private") {
                        Text(
                            stringResource(R.string.recap_shelf_private),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun RecapKind.labelRes(): Int = when (this) {
    RecapKind.WEEK -> R.string.recap_kind_week
    RecapKind.MONTH -> R.string.recap_kind_month
    RecapKind.YEAR -> R.string.recap_kind_year
    RecapKind.ALL_TIME -> R.string.recap_kind_all
}

@Composable
private fun HeroCard(preview: RecapPreview, recap: Recap?, colours: Map<String, Color>, onOpen: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // The pressing cuts itself whenever the card shows a different period.
    val cut = remember(preview.period) { Animatable(0f) }
    LaunchedEffect(preview.period, recap != null) {
        if (recap != null) cut.animateTo(1f, tween(1800))
    }
    val groove = scheme.onSurface.copy(alpha = 0.06f)
    val quiet = scheme.onSurfaceVariant.copy(alpha = 0.5f)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(scheme.surfaceContainer)
            .padding(20.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
            if (recap != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawPressing(
                        center = center,
                        radius = size.minDimension / 2f,
                        arcs = recap.arcs,
                        days = recap.elapsedDays,
                        progress = cut.value,
                        colourOf = { id -> colours[id]?.onRoom() ?: quiet },
                        groove = groove,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize(0.26f)
                    .aspectRatio(1f)
                    .clip(shapeOf(RecapShape.COOKIE9))
                    .background(scheme.surfaceContainerHigh)
            ) {
                preview.topThumbnail?.let {
                    AsyncImage(
                        model = googleImageAtSize(it, 288) ?: it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    periodTitle(context, preview.period),
                    style = RecapType.big.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${pluralStringResource(R.plurals.recap_days, preview.activeDays, preview.activeDays)} · ${playsText(context, preview.plays)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    listOfNotNull(
                        stringResource(if (preview.period.inProgress) R.string.recap_test_pressing else R.string.recap_pressing),
                        recap?.catalog?.let { "KODA · $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onOpen) { Text(stringResource(R.string.recap_open)) }
        }
    }
}

/** A period as a record spine: its covers' colours in bands as wide as their share of it. */
@Composable
private fun Spine(preview: RecapPreview, colours: Map<String, Color>, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = when (preview.period.kind) {
        RecapKind.MONTH -> preview.period.firstDay.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
        RecapKind.YEAR -> preview.period.firstDay.year.toString()
        RecapKind.WEEK -> dayText(LocalContext.current, preview.period.firstDay, abbreviated = true)
        RecapKind.ALL_TIME -> stringResource(R.string.recap_kind_all)
    }
    Column(
        Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainer)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)
    ) {
        val total = preview.stripe.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        Column(Modifier.fillMaxWidth().height(150.dp)) {
            preview.stripe.forEach { (id, share) ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight((share / total).coerceAtLeast(0.001f))
                        .background(colours[id]?.deep() ?: scheme.surfaceContainerHighest)
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (preview.period.inProgress) scheme.primary else scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp),
        )
    }
}

@Composable
private fun EmptyShelf(historyOn: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(scheme.surfaceContainer)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(shapeOf(RecapShape.COOKIE9)).background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.recap_shelf_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(if (historyOn) R.string.recap_shelf_empty_body else R.string.recap_shelf_history_off),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryOffNote() {
    Text(
        stringResource(R.string.recap_shelf_history_off),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
