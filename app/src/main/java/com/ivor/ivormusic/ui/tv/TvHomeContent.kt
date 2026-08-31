package com.ivor.ivormusic.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AppMode
import com.ivor.ivormusic.data.tv.TvItem
import com.ivor.ivormusic.data.tv.TvShelf
import com.ivor.ivormusic.ui.components.AppModeToggle
import com.ivor.ivormusic.ui.components.AppModeToggleState
import com.ivor.ivormusic.ui.components.ExpressivePullToRefresh
import com.ivor.ivormusic.ui.components.rememberAppModeToggleState

/**
 * TV mode's Home: a hero, Continue Watching, then one shelf per catalog.
 *
 * The order is deliberate. Continue Watching is the row most people came for,
 * but it is empty on a fresh install, so the hero sits above it rather than
 * below - otherwise a new user's first sight of the mode is an empty row.
 */
@Composable
fun TvHomeContent(
    shelves: List<TvShelf>,
    heroItems: List<TvItem>,
    continueWatching: List<TvContinueRow>,
    isLoading: Boolean,
    hasLoaded: Boolean,
    hasStreamSource: Boolean,
    isSaved: (String) -> Boolean,
    onItemClick: (TvItem) -> Unit,
    onContinueClick: (TvContinueRow) -> Unit,
    onContinueDismiss: (TvContinueRow) -> Unit,
    onToggleWatchlist: (TvItem) -> Unit,
    onSelectGenre: (String, String?) -> Unit,
    onLoadMore: (String) -> Unit,
    onRefresh: () -> Unit,
    /** The top-bar icon: the app's settings, where TV extensions now live. */
    onOpenSettings: () -> Unit,
    /**
     * The extension manager itself.
     *
     * Separate from [onOpenSettings] on purpose. The top-bar icon is a general
     * way in, but the two empty states exist *because* something is missing,
     * and sending someone who has no source to the settings root to hunt for
     * the right page is a worse answer than taking them there.
     */
    onOpenExtensions: () -> Unit,
    contentPadding: PaddingValues,
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
    showModeToggle: Boolean,
    modeToggleState: AppModeToggleState = rememberAppModeToggleState(appMode),
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    var sourceNoticeDismissed by remember { mutableStateOf(false) }

    ExpressivePullToRefresh(
        isRefreshing = isLoading && shelves.any { it.items.isNotEmpty() },
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "topbar") {
                TvTopBar(
                    appMode = appMode,
                    onAppModeChange = onAppModeChange,
                    showModeToggle = showModeToggle,
                    modeToggleState = modeToggleState,
                    onOpenSettings = onOpenSettings,
                )
            }

            if (heroItems.isNotEmpty()) {
                item(key = "hero") {
                    TvHero(
                        items = heroItems,
                        isSaved = isSaved,
                        onPlay = onItemClick,
                        onToggleWatchlist = onToggleWatchlist,
                    )
                }
            }

            // One card, dismissible, stating plainly that browsing works and
            // playing does not yet. Shown only once something has actually
            // loaded, so it never appears over a blank screen.
            if (!hasStreamSource && hasLoaded && !sourceNoticeDismissed) {
                item(key = "no-source") {
                    NoSourceCard(
                        onOpenExtensions = onOpenExtensions,
                        onDismiss = { sourceNoticeDismissed = true },
                    )
                }
            }

            if (continueWatching.isNotEmpty()) {
                item(key = "continue") {
                    Column {
                        TvSectionHeader(
                            title = stringResource(R.string.tv_continue_watching),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(continueWatching, key = { it.entry.id }) { row ->
                                TvContinueCard(
                                    entry = row.entry,
                                    progressFraction = row.fraction,
                                    subtitle = row.subtitle,
                                    onClick = { onContinueClick(row) },
                                    onLongClick = { onContinueDismiss(row) },
                                )
                            }
                        }
                    }
                }
            }

            if (!hasLoaded && shelves.isEmpty()) {
                item(key = "skeleton") { TvHomeSkeleton() }
            }

            items(shelves, key = { it.key }) { shelf ->
                TvShelfRow(
                    shelf = shelf,
                    onItemClick = onItemClick,
                    onToggleWatchlist = onToggleWatchlist,
                    onSelectGenre = { genre -> onSelectGenre(shelf.key, genre) },
                    onLoadMore = { onLoadMore(shelf.key) },
                )
            }

            if (hasLoaded && shelves.isEmpty()) {
                item(key = "empty") {
                    TvEmptyState(
                        title = stringResource(R.string.tv_no_catalogs_title),
                        body = stringResource(R.string.tv_no_catalogs_body),
                        actionLabel = stringResource(R.string.tv_browse_addons),
                        onAction = onOpenExtensions,
                    )
                }
            }

            item(key = "tail") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Continue Watching row data, flattened so the composable stays dumb. */
data class TvContinueRow(
    val entry: com.ivor.ivormusic.data.tv.TvLibraryEntry,
    val fraction: Float,
    val subtitle: String?,
    val episodeId: String,
)

@Composable
private fun TvTopBar(
    appMode: AppMode,
    onAppModeChange: (AppMode) -> Unit,
    showModeToggle: Boolean,
    modeToggleState: AppModeToggleState,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tv_mode),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // Settings rather than an extension manager. Installing a source is a
        // one-off setup step, and a permanent shortcut to it on the browsing
        // surface made TV mode read as a thing you configure rather than a
        // thing you watch. It lives under Playback in Settings now.
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(44.dp)) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                modifier = Modifier.size(22.dp),
            )
        }
        if (showModeToggle) {
            Spacer(Modifier.size(4.dp))
            AppModeToggle(
                mode = appMode,
                onModeChange = onAppModeChange,
                state = modeToggleState,
            )
        }
    }
}

/**
 * The hero.
 *
 * Uses the item's `logo` PNG over its `background` rather than setting the name
 * in type, because the logo is what makes this read as a movie surface at a
 * glance - Cinemeta ships one for effectively every catalog item.
 *
 * Two constraints the artwork forces. The logo is a transparent asset of
 * unpredictable aspect and luminance, so it is bounded by height and capped in
 * width rather than fitted, and a bottom-up scrim sits under it: a white logo
 * on a bright still is unreadable, which is most animated films.
 *
 * It does not auto-advance. A hero that moves while it is being read is the
 * single most complained-about pattern in this shape of screen; swiping is the
 * whole interaction.
 */
@Composable
private fun TvHero(
    items: List<TvItem>,
    isSaved: (String) -> Boolean,
    onPlay: (TvItem) -> Unit,
    onToggleWatchlist: (TvItem) -> Unit,
) {
    val pageCount = items.size.coerceAtMost(HERO_PAGES)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = items[page]
            var logoFailed by remember(item.id) { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                AsyncImage(
                    model = item.background ?: item.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                BoxScopeScrim()
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                ) {
                    if (!item.logo.isNullOrBlank() && !logoFailed) {
                        AsyncImage(
                            model = item.logo,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomStart,
                            onError = { logoFailed = true },
                            modifier = Modifier
                                .heightIn(max = 54.dp)
                                .widthIn(max = 220.dp),
                        )
                    } else {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = listOfNotNull(
                            item.releaseInfo?.takeIf { it.isNotBlank() },
                            item.runtime?.takeIf { it.isNotBlank() },
                            item.imdbRating?.takeIf { it.isNotBlank() }?.let { "$it/10" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPlay(item) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.inverseSurface,
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            ),
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(R.string.tv_open))
                        }
                        val saved = isSaved(item.id)
                        FilledTonalButton(
                            onClick = { onToggleWatchlist(item) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            ),
                        ) {
                            Icon(
                                imageVector = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        if (pageCount > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pageCount) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TvShelfRow(
    shelf: TvShelf,
    onItemClick: (TvItem) -> Unit,
    onToggleWatchlist: (TvItem) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (shelf.items.isEmpty() && !shelf.isLoading && !shelf.failed) return

    val rowState = rememberLazyListState()
    // Load-more when the end comes into view, the same trigger the video feed
    // uses. derivedStateOf so this recomposes on threshold crossings only.
    val atEnd by remember(shelf.key) {
        derivedStateOf {
            val last = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            shelf.items.isNotEmpty() && last >= shelf.items.size - 3
        }
    }
    LaunchedEffect(atEnd, shelf.key) { if (atEnd) onLoadMore() }

    Column {
        TvSectionHeader(
            title = shelf.title,
            trailing = shelf.addonName,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (shelf.genres.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = shelf.selectedGenre == null,
                        onClick = { onSelectGenre(null) },
                        label = { Text(stringResource(R.string.tv_genre_all)) },
                    )
                }
                items(shelf.genres, key = { it }) { genre ->
                    FilterChip(
                        selected = shelf.selectedGenre == genre,
                        onClick = {
                            onSelectGenre(if (shelf.selectedGenre == genre) null else genre)
                        },
                        label = { Text(genre) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (shelf.items.isEmpty() && shelf.failed) {
            ShelfFailed(shelf.addonName, Modifier.padding(horizontal = 16.dp))
        } else if (shelf.items.isEmpty()) {
            TvShelfSkeleton()
        } else {
            LazyRow(
                state = rowState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                itemsIndexed(shelf.items, key = { _, item -> item.id }) { _, item ->
                    TvPosterCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongClick = { onToggleWatchlist(item) },
                    )
                }
            }
        }
    }
}

/**
 * One shelf failing says so where the shelf is, and nowhere else.
 *
 * "No results" and "this addon timed out" are different problems with different
 * fixes, so the addon is named rather than the row silently disappearing.
 */
@Composable
private fun ShelfFailed(addonName: String, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.tv_shelf_failed, addonName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

@Composable
private fun NoSourceCard(onOpenExtensions: () -> Unit, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        ),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.tv_no_source_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tv_no_source_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenExtensions) {
                        Text(stringResource(R.string.tv_browse_addons))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.tv_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
fun TvEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun TvHomeSkeleton() {
    Column {
        repeat(2) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                com.ivor.ivormusic.ui.components.SkeletonTextLine(
                    width = 140.dp,
                    height = 18.dp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(10.dp))
                TvShelfSkeleton()
            }
        }
    }
}

@Composable
private fun TvShelfSkeleton() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        repeat(3) {
            Column {
                com.ivor.ivormusic.ui.components.SkeletonBox(
                    modifier = Modifier
                        .size(width = TvPosterWidth, height = TvPosterWidth / TV_POSTER_RATIO),
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(6.dp))
                com.ivor.ivormusic.ui.components.SkeletonTextLine(width = TvPosterWidth * 0.8f, height = 12.dp)
            }
        }
    }
}

/** How many hero pages to draw. Enough to feel curated, few enough to swipe. */
private const val HERO_PAGES = 5
