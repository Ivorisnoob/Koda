package com.ivor.ivormusic.ui.taste

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.TasteArtist
import com.ivor.ivormusic.data.TasteGenre
import kotlinx.coroutines.launch

/**
 * Taste setup: artists, then genres, then a deck of songs to say yes or no to.
 *
 * One full-screen route with three entrances - the end of onboarding, once
 * after updating to the version that added it, and Settings - which is why it
 * is not a page of the onboarding pager: two of the three arrive with no
 * onboarding around them.
 *
 * [onFinished] is every way out (done, skipped, backed out of the first step).
 * Picks are saved as they are made, so none of those loses anything.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TasteSetupScreen(
    /** Start from nothing, ignoring a saved profile and the listening history. */
    ignoreExisting: Boolean,
    onFinished: () -> Unit,
    viewModel: TasteViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.start(ignoreExisting) }

    val step by viewModel.step.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val pickedGenres by viewModel.pickedGenres.collectAsState()
    val hasPicks = picked.isNotEmpty() || pickedGenres.isNotEmpty()

    val close = {
        viewModel.markSeen()
        onFinished()
    }
    BackHandler { if (!viewModel.back()) close() }

    // A sample must not keep playing behind the lock screen or another app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.preview.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            TasteTopBar(
                step = step,
                canClear = hasPicks && step != TasteStep.DONE,
                onBack = { if (!viewModel.back()) close() },
                onClear = viewModel::clearAll,
                onSkip = {
                    // Leaving the deck early still ends on the summary; skipping
                    // the picking steps leaves the screen.
                    if (step == TasteStep.DECK) viewModel.goTo(TasteStep.DONE) else close()
                }
            )
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width ->
                        if (forward) width / 3 else -width / 3
                    } + fadeIn()) togetherWith
                        (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { width ->
                            if (forward) -width / 3 else width / 3
                        } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "TasteStep"
            ) { current ->
                when (current) {
                    TasteStep.ARTISTS -> ArtistsStep(viewModel)
                    TasteStep.GENRES -> GenresStep(viewModel)
                    TasteStep.DECK -> DeckStep(viewModel)
                    TasteStep.DONE -> DoneStep(
                        viewModel = viewModel,
                        onDone = close,
                        onChange = { viewModel.goTo(TasteStep.ARTISTS) }
                    )
                }
            }
            AnimatedVisibility(visible = step == TasteStep.ARTISTS || step == TasteStep.GENRES) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (step == TasteStep.GENRES) {
                            pluralStringResource(R.plurals.taste_genres_count, pickedGenres.size, pickedGenres.size)
                        } else {
                            pluralStringResource(R.plurals.taste_artists_count, picked.size, picked.size)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            when {
                                step == TasteStep.ARTISTS -> viewModel.goTo(TasteStep.GENRES)
                                // With nothing picked there is nothing to build a
                                // deck from, so the summary is next.
                                hasPicks -> viewModel.goTo(TasteStep.DECK)
                                else -> viewModel.goTo(TasteStep.DONE)
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.height(56.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ob_continue),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TasteTopBar(
    step: TasteStep,
    canClear: Boolean,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Three steps of picking; the summary is not one of them.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(TasteStep.ARTISTS, TasteStep.GENRES, TasteStep.DECK).forEach { marker ->
                val active = marker == step
                val reached = step.ordinal >= marker.ordinal
                val width by animateDpAsState(
                    targetValue = if (active) 28.dp else 10.dp,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "tasteStepWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (reached) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    label = "tasteStepColor"
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        AnimatedVisibility(visible = canClear, enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.taste_clear_all)) }
        }
        AnimatedVisibility(visible = step != TasteStep.DONE, enter = fadeIn(), exit = fadeOut()) {
            TextButton(onClick = onSkip) {
                Text(
                    stringResource(
                        if (step == TasteStep.DECK) R.string.taste_finish else R.string.ob_skip
                    )
                )
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Artists
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistsStep(viewModel: TasteViewModel) {
    val artists by viewModel.artists.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val isLoading by viewModel.isLoadingArtists.collectAsState()
    val loadFailed by viewModel.loadFailed.collectAsState()
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        StepHeader(
            title = stringResource(R.string.taste_title_artists),
            subtitle = stringResource(R.string.taste_sub_artists)
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            placeholder = { Text(stringResource(R.string.taste_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        val searching = query.isNotBlank()
        val shown = if (searching) searchResults else artists
        when {
            (searching && isSearching && shown.isEmpty()) || (!searching && isLoading && shown.isEmpty()) ->
                CenteredLoader()
            !searching && loadFailed -> TasteLoadFailed(onRetry = viewModel::retry)
            shown.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.taste_no_artist_results),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // The count-and-key member, on purpose: it is the one a grid
                // scope resolves `items` to (see docs/channels.md).
                items(count = shown.size, key = { shown[it].key }) { index ->
                    val artist = shown[index]
                    val isPicked = picked.any { it.sameArtistAs(artist) }
                    ArtistBubble(
                        artist = artist,
                        picked = isPicked,
                        onClick = {
                            haptics.performHapticFeedback(
                                if (isPicked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                            )
                            viewModel.toggleArtist(artist)
                        },
                        // "More like this" arrives into the middle of the grid;
                        // the rest slides to make room rather than jumping.
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistBubble(
    artist: TasteArtist,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (picked) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "artistBubbleScale"
    )
    val ringAlpha by animateFloatAsState(targetValue = if (picked) 1f else 0f, label = "artistBubbleRing")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha), CircleShape)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                // The initial under the picture: an artist with no image, or
                // one known only by name, still reads as somebody.
                Text(
                    text = artist.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!artist.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = picked,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
            color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CenteredLoader(label: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LoadingIndicator(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary)
        if (label != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TasteLoadFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.taste_offline_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.taste_offline_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.taste_retry)) }
    }
}

// ---------------------------------------------------------------------------
// Genres
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenresStep(viewModel: TasteViewModel) {
    val genres by viewModel.genres.collectAsState()
    val picked by viewModel.pickedGenres.collectAsState()
    val isLoading by viewModel.isLoadingArtists.collectAsState()
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        StepHeader(
            title = stringResource(R.string.taste_title_genres),
            subtitle = stringResource(R.string.taste_sub_genres)
        )
        Spacer(modifier = Modifier.height(20.dp))
        when {
            genres.isEmpty() && isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) { CenteredLoader() }
            genres.isEmpty() -> Text(
                text = stringResource(R.string.taste_offline_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            else -> FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                genres.forEach { genre ->
                    val selected = picked.any { it.title == genre.title }
                    GenreChip(
                        genre = genre,
                        selected = selected,
                        onClick = {
                            haptics.performHapticFeedback(
                                if (selected) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                            )
                            viewModel.toggleGenre(genre)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GenreChip(genre: TasteGenre, selected: Boolean, onClick: () -> Unit) {
    // Squares off as it fills, the selection language of the app's tiles.
    val corner by animateDpAsState(
        targetValue = if (selected) 14.dp else 26.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "genreChipCorner"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "genreChipContainer"
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = selected) {
                Row {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Text(
                text = genre.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The deck
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeckStep(viewModel: TasteViewModel) {
    val deck by viewModel.deck.collectAsState()
    val index by viewModel.deckIndex.collectAsState()
    val isLoading by viewModel.isDeckLoading.collectAsState()
    val isPlaying by viewModel.preview.isPlaying.collectAsState()
    val isPreviewLoading by viewModel.preview.isLoading.collectAsState()
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()

    if (isLoading || deck.isEmpty()) {
        CenteredLoader(label = stringResource(R.string.taste_deck_loading))
        return
    }
    val card = deck.getOrNull(index) ?: return
    val nextCard = deck.getOrNull(index + 1)
    // A button press and a swipe end the same way; the card owns the animation,
    // so a press only asks for a verdict and the card flies itself off.
    var requested by remember(card.song.id) { mutableStateOf<DeckVerdict?>(null) }
    val answer: (DeckVerdict) -> Unit = { verdict ->
        haptics.performHapticFeedback(
            if (verdict == DeckVerdict.NO) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm
        )
        viewModel.answer(verdict)
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StepHeader(
                title = stringResource(R.string.taste_title_deck),
                subtitle = stringResource(R.string.taste_sub_deck)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.taste_deck_count, index + 1, deck.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val flyDistance = constraints.maxWidth * 1.6f
            val flyHeight = constraints.maxHeight * 1.6f
            if (nextCard != null) {
                // The card underneath: already drawn, so the next song is there
                // the instant this one leaves.
                key(nextCard.song.id) {
                    DeckCardSurface(
                        card = nextCard,
                        isPlaying = false,
                        isPreviewLoading = false,
                        onTogglePreview = null,
                        modifier = Modifier
                            .widthIn(max = 420.dp)
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 0.94f
                                scaleY = 0.94f
                                translationY = 18.dp.toPx()
                                alpha = 0.7f
                            }
                    )
                }
            }
            key(card.song.id) {
                SwipeableDeckCard(
                    requested = requested,
                    flyDistance = flyDistance,
                    flyHeight = flyHeight,
                    onAnswer = answer,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxSize()
                ) {
                    DeckCardSurface(
                        card = card,
                        isPlaying = isPlaying,
                        isPreviewLoading = isPreviewLoading,
                        onTogglePreview = viewModel::togglePreview,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { requested = DeckVerdict.NO },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.taste_action_no),
                    modifier = Modifier.size(32.dp)
                )
            }
            FilledTonalIconButton(
                onClick = { requested = DeckVerdict.LOVE },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.taste_action_love))
            }
            FilledIconButton(
                onClick = { requested = DeckVerdict.YES },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.taste_action_yes),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * The drag, the tilt, the three verdict stamps and the fly-off, around whatever
 * the card shows. Right is yes, left is no, up is love; anything short of the
 * threshold springs back.
 */
@Composable
private fun SwipeableDeckCard(
    requested: DeckVerdict?,
    flyDistance: Float,
    flyHeight: Float,
    onAnswer: (DeckVerdict) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var answered by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thresholdX = with(density) { 104.dp.toPx() }
    val thresholdY = with(density) { 120.dp.toPx() }

    suspend fun fly(verdict: DeckVerdict) {
        if (answered) return
        answered = true
        when (verdict) {
            DeckVerdict.YES -> offsetX.animateTo(flyDistance, tween(220))
            DeckVerdict.NO -> offsetX.animateTo(-flyDistance, tween(220))
            DeckVerdict.LOVE -> offsetY.animateTo(-flyHeight, tween(240))
        }
        onAnswer(verdict)
    }

    LaunchedEffect(requested) { requested?.let { fly(it) } }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX.value
                translationY = offsetY.value
                rotationZ = (offsetX.value / flyDistance) * 18f
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val x = offsetX.value
                            val y = offsetY.value
                            when {
                                // Up wins only when it is clearly the larger
                                // movement, so a diagonal yes is not a love.
                                y < -thresholdY && -y > kotlin.math.abs(x) -> fly(DeckVerdict.LOVE)
                                x > thresholdX -> fly(DeckVerdict.YES)
                                x < -thresholdX -> fly(DeckVerdict.NO)
                                else -> {
                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                    launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            launch { offsetX.animateTo(0f, spring()) }
                            launch { offsetY.animateTo(0f, spring()) }
                        }
                    }
                ) { change, drag ->
                    change.consume()
                    if (answered) return@detectDragGestures
                    scope.launch {
                        offsetX.snapTo(offsetX.value + drag.x)
                        // Down goes nowhere, so the card resists it.
                        offsetY.snapTo((offsetY.value + drag.y).coerceAtMost(thresholdY / 3f))
                    }
                }
            }
    ) {
        content()
        VerdictStamp(
            icon = Icons.Rounded.Check,
            container = MaterialTheme.colorScheme.primary,
            content = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(20.dp)
                .graphicsLayer { alpha = (offsetX.value / thresholdX).coerceIn(0f, 1f) }
        )
        VerdictStamp(
            icon = Icons.Rounded.Close,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .graphicsLayer { alpha = (-offsetX.value / thresholdX).coerceIn(0f, 1f) }
        )
        VerdictStamp(
            icon = Icons.Rounded.Favorite,
            container = MaterialTheme.colorScheme.tertiary,
            content = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = (-offsetY.value / thresholdY).coerceIn(0f, 1f) }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VerdictStamp(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(36.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeckCardSurface(
    card: DeckCard,
    isPlaying: Boolean,
    isPreviewLoading: Boolean,
    /** Null for the card underneath, which plays nothing and offers no button. */
    onTogglePreview: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 6.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = card.song.highResThumbnailUrl ?: card.song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (onTogglePreview != null) {
                    FilledTonalIconButton(
                        onClick = onTogglePreview,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(56.dp)
                    ) {
                        if (isPreviewLoading) {
                            LoadingIndicator(modifier = Modifier.size(28.dp))
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.taste_preview_pause else R.string.taste_preview_play
                                )
                            )
                        }
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Surface(
                    shape = RoundedCornerShape(100),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = when (val reason = card.reason) {
                            is DeckReason.Artist -> stringResource(R.string.taste_reason_artist, reason.name)
                            is DeckReason.Genre -> stringResource(R.string.taste_reason_genre, reason.title)
                            DeckReason.Discovery -> stringResource(R.string.taste_reason_discovery)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = card.song.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.song.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Done
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun DoneStep(viewModel: TasteViewModel, onDone: () -> Unit, onChange: () -> Unit) {
    val picked by viewModel.picked.collectAsState()
    val pickedGenres by viewModel.pickedGenres.collectAsState()
    val kept by viewModel.keptCount.collectAsState()

    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { landed = true }
    val badgeScale by animateFloatAsState(
        targetValue = if (landed) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tasteDoneBadge"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .graphicsLayer {
                    scaleX = badgeScale
                    scaleY = badgeScale
                }
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.taste_done_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.taste_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                pluralStringResource(R.plurals.taste_artists_count, picked.size, picked.size),
                pluralStringResource(R.plurals.taste_genres_count, pickedGenres.size, pickedGenres.size),
                pluralStringResource(R.plurals.taste_songs_kept_count, kept, kept)
            ).forEach { label ->
                Surface(
                    shape = RoundedCornerShape(100),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .heightIn(min = 56.dp)
        ) {
            Text(text = stringResource(R.string.taste_done_button), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onChange) { Text(stringResource(R.string.taste_change_picks)) }
    }
}
