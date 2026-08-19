package com.ivor.ivormusic.ui.artist

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import coil.compose.AsyncImage
import com.ivor.ivormusic.data.Song
import com.ivor.ivormusic.ui.library.songRowClick
import com.ivor.ivormusic.ui.channel.CreatorHeader
import com.ivor.ivormusic.ui.channel.creatorMetadata
import com.ivor.ivormusic.ui.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Segmented list shape helper for Expressive design
 */
@Composable
private fun getSegmentedShape(index: Int, count: Int, cornerSize: androidx.compose.ui.unit.Dp = 28.dp): Shape {
    return when {
        count == 1 -> RoundedCornerShape(cornerSize)
        index == 0 -> RoundedCornerShape(topStart = cornerSize, topEnd = cornerSize)
        index == count - 1 -> RoundedCornerShape(bottomStart = cornerSize, bottomEnd = cornerSize)
        else -> RectangleShape
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    artistId: String? = null,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayQueue: (List<Song>, Song?) -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: ((String, List<Song>) -> Unit)? = null,
    onOpenAlbum: ((com.ivor.ivormusic.data.PlaylistDisplayItem) -> Unit)? = null,
    viewModel: HomeViewModel? = null,
    modifier: Modifier = Modifier,
    onSongLongPress: ((Song) -> Unit)? = null,
    /**
     * Open this creator's video-mode channel page.
     *
     * The other half of the cross-link on the channel screen. The two stay
     * separate surfaces because a discography and an upload feed are genuinely
     * different content, but they share [CreatorHeader] and this link, so
     * arriving at a musician from either mode does not feel like arriving at
     * two different people.
     */
    onOpenChannel: ((channelId: String) -> Unit)? = null
) {
    // Theme colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cardColor = MaterialTheme.colorScheme.surfaceContainer
    val textColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
    
    // State for fetched songs
    var artistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var fetchedAlbums by remember { mutableStateOf<List<com.ivor.ivormusic.data.PlaylistDisplayItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var canLoadMoreRemote by remember { mutableStateOf(true) }
    var visibleSongCount by remember { mutableIntStateOf(20) }
    var hasLocalSongs by remember { mutableStateOf(false) }
    // The canonical UC id behind this artist, once something has resolved one.
    // Local-only artists never get one, which is why every use of it is guarded
    // rather than assumed.
    var resolvedChannelId by remember { mutableStateOf<String?>(null) }
    var channelHeader by remember {
        mutableStateOf<com.ivor.ivormusic.data.ChannelHeader?>(null)
    }
    val scope = rememberCoroutineScope()
    
    // Fetch songs - first check local files, then fetch from internet
    LaunchedEffect(artistName, artistId, songs) {
        isLoading = true
        visibleSongCount = 20

        // Only genuinely local files take the offline path. Liked/downloaded
        // YouTube songs shouldn't block fetching the full artist page.
        val localArtistSongs = songs.filter {
            it.artist.equals(artistName, ignoreCase = true) &&
                    it.source == com.ivor.ivormusic.data.SongSource.LOCAL
        }

        channelHeader = null
        resolvedChannelId = null

        if (localArtistSongs.isNotEmpty()) {
            // Use local songs if available
            artistSongs = localArtistSongs
            hasLocalSongs = true
            // Local albums are derived automatically below
            isLoading = false
        } else if (viewModel != null) {
            // Resolve a channel id when the caller only knows the name
            // (e.g. Library navigation passes the artist name as the id).
            val resolvedId = artistId?.takeIf { it.startsWith("UC") }
                ?: viewModel.searchArtists(artistName).let { results ->
                    (results.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                        ?: results.firstOrNull())?.id
                }

            resolvedChannelId = resolvedId
            if (resolvedId != null) {
                val (fetchedSongs, fetchedAlbumsList) = viewModel.getArtistDetails(resolvedId)
                artistSongs = fetchedSongs
                fetchedAlbums = fetchedAlbumsList
            }
            // Fallback: plain name search if the artist page gave nothing
            if (artistSongs.isEmpty()) {
                artistSongs = viewModel.searchArtistSongs(artistName)
            }
            hasLocalSongs = false
            isLoading = false
        } else {
            artistSongs = emptyList()
            hasLocalSongs = false
            isLoading = false
        }
    }
    
    // The identity half of the header, from the same channel browse the video
    // mode page uses. One request, and the only reason this screen makes it:
    // without it a musician has a banner and a verified tick on one side of the
    // app and neither on the other.
    LaunchedEffect(resolvedChannelId) {
        val id = resolvedChannelId ?: return@LaunchedEffect
        channelHeader = viewModel?.getChannelHeader(id)
    }

    // Get unique albums (Local + Fetched)
    val albums = remember(artistSongs, hasLocalSongs, fetchedAlbums) {
        if (hasLocalSongs) {
            artistSongs.groupBy { it.album }.keys.toList()
        } else {
            // Use fetched albums if available
            fetchedAlbums.map { it.name }
        }
    }
    
    // Sample thumbnails for the hero section (up to 4)
    val sampleThumbnails = remember(artistSongs) {
        artistSongs.take(4).mapNotNull { it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() }
    }
    
    // Songs currently visible (with pagination)
    val displayedSongs = remember(artistSongs, visibleSongCount) {
        artistSongs.take(visibleSongCount)
    }
    val hasMoreSongs = artistSongs.size > visibleSongCount ||
            (!hasLocalSongs && viewModel != null && canLoadMoreRemote)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator(
                    modifier = Modifier.size(48.dp),
                    color = primaryColor
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Extra clearance so the last row ("Show More") sits above
                // the mini player + floating nav bar + system inset.
                contentPadding = PaddingValues(bottom = 220.dp)
            ) {
                // ========== HERO HEADER ==========
                // The same CreatorHeader the channel page uses. What differs is
                // the actions slot: a discography has Play, Shuffle and Radio to
                // offer, which an upload feed does not, and the header is built
                // to take exactly that difference.
                item {
                    val radioSeed = artistSongs.firstOrNull {
                        it.source == com.ivor.ivormusic.data.SongSource.YOUTUBE
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        CreatorHeader(
                            name = artistName.takeIf { !it.startsWith("Unknown") }
                                ?: "Unknown Artist",
                            // The channel avatar when there is one, and the
                            // artwork of what they made when there is not -
                            // which is every local-library artist.
                            avatarUrl = channelHeader?.avatarUrl
                                ?: sampleThumbnails.firstOrNull(),
                            bannerUrl = channelHeader?.bannerUrl,
                            isVerified = channelHeader?.isVerified == true,
                            metadata = creatorMetadata(
                                channelHeader?.subscriberCountText,
                                "${artistSongs.size} songs",
                                albums.size.takeIf { it > 0 }?.let { "$it albums" }
                            ),
                            actions = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PlaySplitButton(
                                        onPlay = {
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayQueue(artistSongs, null)
                                            }
                                        },
                                        onShuffle = {
                                            if (artistSongs.isNotEmpty()) {
                                                onPlayQueue(artistSongs.shuffled(), null)
                                            }
                                        },
                                        onStartRadio = if (radioSeed != null && viewModel != null) {
                                            {
                                                scope.launch {
                                                    val radio = viewModel.getRadioSongs(radioSeed.id)
                                                    if (radio.isNotEmpty()) {
                                                        onPlayQueue(listOf(radioSeed) + radio, radioSeed)
                                                    }
                                                }
                                            }
                                        } else null
                                    )
                                    Spacer(Modifier.weight(1f))
                                    // Only offered once a real channel id has
                                    // resolved. A local-library artist is a tag
                                    // on a file and has no channel to open.
                                    val channelId = resolvedChannelId
                                    if (onOpenChannel != null && channelId != null) {
                                        FilledIconButton(
                                            onClick = { onOpenChannel(channelId) },
                                            modifier = Modifier.size(48.dp),
                                            shape = CircleShape,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme
                                                    .surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme
                                                    .onSurfaceVariant
                                            )
                                        ) {
                                            Icon(
                                                Icons.Rounded.SmartDisplay,
                                                contentDescription = "Open YouTube channel",
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        // Over the banner rather than above it, so the artwork
                        // starts at the top of the screen the way it does on
                        // the channel page.
                        FilledIconButton(
                            onClick = onBack,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    .copy(alpha = 0.85f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(start = 12.dp, top = 8.dp)
                                .size(44.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                
                // ========== ALBUMS SECTION (Local songs only) ==========
                if (albums.isNotEmpty() && albums.any { it.isNotBlank() && !it.startsWith("Unknown") }) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Albums",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val validAlbums = albums.filter { it.isNotBlank() && !it.startsWith("Unknown") }
                            items(validAlbums.size) { index ->
                                val albumName = validAlbums[index]
                                val albumSongs = if (hasLocalSongs) artistSongs.filter { it.album == albumName } else emptyList()
                                
                                val fetchedAlbum = fetchedAlbums.find { it.name == albumName }
                                val albumSubtitle = if (hasLocalSongs) {
                                    "${albumSongs.size} songs"
                                } else {
                                    fetchedAlbum?.uploaderName?.takeIf { it.isNotBlank() } ?: "Album"
                                }
                                val thumbnailUrl = if (hasLocalSongs) {
                                    albumSongs.firstOrNull()?.let { it.highResThumbnailUrl ?: it.thumbnailUrl ?: it.albumArtUri?.toString() }
                                } else {
                                    fetchedAlbum?.thumbnailUrl
                                }
                                
                                AlbumCard(
                                    albumName = albumName,
                                    subtitle = albumSubtitle,
                                    thumbnailUrl = thumbnailUrl,
                                    primaryColor = primaryColor,
                                    cardColor = cardColor,
                                    textColor = textColor,
                                    secondaryTextColor = secondaryTextColor,
                                    onClick = {
                                        if (hasLocalSongs) {
                                            onAlbumClick?.invoke(albumName, albumSongs) ?: onPlayQueue(albumSongs, null)
                                        } else if (fetchedAlbum != null) {
                                            // Fetched albums carry a browse id in their URL;
                                            // the album detail screen fetches the tracks itself.
                                            onOpenAlbum?.invoke(fetchedAlbum)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // ========== SONGS SECTION ==========
                if (displayedSongs.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Songs",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                "${artistSongs.size} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryTextColor
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    // Song list with segmented card design
                    itemsIndexed(displayedSongs) { index, song ->
                        ArtistSongCard(
                            song = song,
                            index = index + 1,
                            onClick = { onPlayQueue(artistSongs, song) },
                            cardColor = cardColor,
                            textColor = textColor,
                            secondaryTextColor = secondaryTextColor,
                            primaryColor = primaryColor,
                            shape = if (index == displayedSongs.size - 1 && !hasMoreSongs) {
                                getSegmentedShape(index, displayedSongs.size)
                            } else if (index == 0) {
                                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            } else {
                                RectangleShape
                            },
                            modifier = Modifier.padding(horizontal = 20.dp),
                            onLongClick = onSongLongPress?.let { press -> { press(song) } }
                        )
                        if (index < displayedSongs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                        }
                    }
                    
                    // Show More button
                    if (hasMoreSongs) {
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 44.dp),
                                color = textColor.copy(alpha = 0.06f)
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                                    .clickable(enabled = !isLoadingMore) {
                                        if (artistSongs.size > visibleSongCount) {
                                            // Show more from existing list
                                            visibleSongCount += 20
                                        } else if (viewModel != null && !hasLocalSongs) {
                                            // Load more from YouTube
                                            scope.launch {
                                                isLoadingMore = true
                                                val moreSongs = viewModel.loadMoreResults(artistName)
                                                if (moreSongs.isNotEmpty()) {
                                                    artistSongs = (artistSongs + moreSongs).distinctBy { it.id }
                                                    visibleSongCount += 20
                                                } else {
                                                    canLoadMoreRemote = false
                                                }
                                                isLoadingMore = false
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                                color = cardColor,
                                tonalElevation = 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isLoadingMore) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = primaryColor
                                        )
                                    } else {
                                        Text(
                                            "Show More",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = primaryColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No songs found for this artist",
                                color = secondaryTextColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
        
    }
}

/**
 * M3 Expressive split button: primary Play action + a spinning menu half
 * with related play actions (shuffle, radio).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaySplitButton(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    androidx.compose.material3.SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            androidx.compose.material3.SplitButtonDefaults.LeadingButton(
                onClick = onPlay,
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Play",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        trailingButton = {
            Box {
                androidx.compose.material3.SplitButtonDefaults.TrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    modifier = Modifier.height(56.dp)
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (menuOpen) 180f else 0f,
                        label = "playMenuRotation"
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "More play options",
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Shuffle") },
                        leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                        onClick = {
                            menuOpen = false
                            onShuffle()
                        }
                    )
                    if (onStartRadio != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Start radio") },
                            leadingIcon = { Icon(Icons.Rounded.Radio, null) },
                            onClick = {
                                menuOpen = false
                                onStartRadio()
                            }
                        )
                    }
                }
            }
        }
    )
}

/**
 * Album Card for horizontal scroll
 */
@Composable
private fun AlbumCard(
    albumName: String,
    subtitle: String,
    thumbnailUrl: String?,
    primaryColor: Color,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Album art
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(116.dp),
                shape = RoundedCornerShape(14.dp),
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = albumName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Album,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = primaryColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                albumName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Song card for artist song list
 */
@Composable
private fun ArtistSongCard(
    song: Song,
    index: Int,
    onClick: () -> Unit,
    cardColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    primaryColor: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .songRowClick(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        color = cardColor,
        tonalElevation = 1.dp
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Untitled Song",
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = song.album.takeIf { !it.isNullOrBlank() && !it.startsWith("Unknown", ignoreCase = true) } ?: "Unknown Album",
                    color = secondaryTextColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                // Track number or thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.albumArtUri != null || song.thumbnailUrl != null) {
                        AsyncImage(
                            model = song.highResThumbnailUrl ?: song.albumArtUri ?: song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$index",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                        }
                    }
                }
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
