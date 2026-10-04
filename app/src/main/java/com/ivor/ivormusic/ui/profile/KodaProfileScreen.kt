package com.ivor.ivormusic.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.GlobalStats
import com.ivor.ivormusic.data.KodaAvatar
import com.ivor.ivormusic.data.KodaAvatarColors
import com.ivor.ivormusic.data.KodaProfileCard
import com.ivor.ivormusic.data.KodaProfileStore
import com.ivor.ivormusic.data.LikedSongsRepository
import com.ivor.ivormusic.data.Profile
import com.ivor.ivormusic.data.ProfileManager
import com.ivor.ivormusic.data.StatsRepository
import com.ivor.ivormusic.data.TasteProfileStore
import com.ivor.ivormusic.data.isUnknownArtist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the profile screen shows of someone's listening, read once when it opens. */
private data class ProfileListening(
    val stats: GlobalStats = GlobalStats(),
    val firstPlayAtMs: Long? = null,
    val likedCount: Int = 0
)

/**
 * A Koda profile: who is using the app, drawn large, over what they listen to.
 *
 * Opened by tapping the profile picture in music mode; holding the picture is
 * still the account switcher. A device-only profile is its own thing here, with
 * a name and a picture of its choosing (a Koda avatar or a photo); an account
 * profile shows the account's name and picture and is not editable, because
 * those are YouTube's to change.
 *
 * Everything on it is already on the device: the listening history, the likes
 * and the stated taste of the active profile. The one thing it may fetch is a
 * top artist's picture, through the same cache and the same exact-name rule
 * Home's top-artists shelf uses, so an artist seen there is free here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun KodaProfileScreen(
    onBack: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenStats: () -> Unit
) {
    val context = LocalContext.current
    val profileManager = remember(context) { ProfileManager(context) }
    val store = remember(context) { KodaProfileStore(context) }
    val tasteStore = remember(context) { TasteProfileStore(context) }
    val activeId by profileManager.activeProfileId.collectAsState()
    val profiles by profileManager.profiles.collectAsState()
    val cards by store.cards.collectAsState()
    val taste by tasteStore.profile.collectAsState()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val card = cards[profile.id] ?: KodaProfileCard()

    val listening by produceState(ProfileListening(), activeId) {
        value = withContext(Dispatchers.Default) {
            val stats = StatsRepository(context)
            ProfileListening(
                stats = stats.getGlobalStats(),
                firstPlayAtMs = stats.loadHistory().minOfOrNull { it.timestamp },
                likedCount = LikedSongsRepository(context).getAllLikedSongIds().size
            )
        }
    }

    // Pictures for the top artists. A play count carries a name and nothing
    // else, so each is looked up: the cache answers at once for any artist
    // Home has already shown, and the rest are searched one at a time. Only an
    // exact name match is taken - a near miss would put a stranger's face on
    // somebody's favourite artist, and the initial is the better answer.
    val topArtistNames = remember(listening.stats) {
        listening.stats.topArtists.map { it.name }.filterNot { isUnknownArtist(it) }.take(8)
    }
    val artistPhotos = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(topArtistNames) {
        val cache = com.ivor.ivormusic.data.ArtistPhotoCache(context)
        val missing = mutableListOf<String>()
        for (name in topArtistNames) {
            when (val hit = cache.lookup(name)) {
                is com.ivor.ivormusic.data.ArtistPhotoCache.Lookup.Photo -> artistPhotos[name] = hit.url
                com.ivor.ivormusic.data.ArtistPhotoCache.Lookup.Miss -> Unit
                com.ivor.ivormusic.data.ArtistPhotoCache.Lookup.Unknown -> missing += name
            }
        }
        if (missing.isEmpty() ||
            com.ivor.ivormusic.data.ThemePreferences(context).isLocalOnlyModeEnabled()
        ) return@LaunchedEffect
        val repository = com.ivor.ivormusic.data.YouTubeRepository(context)
        for (name in missing) {
            // Discretionary, so a rate-limit hold stands it down.
            if (com.ivor.ivormusic.data.YouTubeRateLimit.isHeld()) return@LaunchedEffect
            val match = try {
                repository.searchArtists(name).firstOrNull()
                    ?.takeIf { it.name.trim().equals(name.trim(), ignoreCase = true) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
            val url = com.ivor.ivormusic.data.googleImageAtSize(match?.thumbnailUrl, 288)
            cache.put(name, url)
            if (url != null) artistPhotos[name] = url
        }
    }

    var editing by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    if (editing) {
        ProfileEditSheet(
            profile = profile,
            card = card,
            store = store,
            profileManager = profileManager,
            onDismiss = { editing = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "hero") {
                ProfileHero(
                    profile = profile,
                    card = card,
                    firstPlayAtMs = listening.firstPlayAtMs,
                    onEdit = if (profile.isLocal) ({ editing = true }) else null,
                    onSwitchProfile = onSwitchProfile
                )
            }

            item(key = "stats") {
                val stats = listening.stats
                val hours = stats.totalPlayTimeSeconds / 3600.0
                val tiles = listOf(
                    Triple(
                        Icons.Rounded.Schedule,
                        if (hours < 10) String.format(java.util.Locale.getDefault(), "%.1f", hours)
                        else hours.toInt().toString(),
                        stringResource(R.string.kp_stat_time)
                    ),
                    Triple(Icons.Rounded.MusicNote, stats.totalPlays.toString(), stringResource(R.string.kp_stat_plays)),
                    Triple(Icons.Rounded.Person, stats.uniqueArtists.toString(), stringResource(R.string.kp_stat_artists)),
                    Triple(
                        Icons.Rounded.LocalFireDepartment,
                        stats.currentStreakDays.toString(),
                        stringResource(R.string.kp_stat_streak)
                    ),
                    Triple(Icons.Rounded.Favorite, listening.likedCount.toString(), stringResource(R.string.kp_stat_liked)),
                    Triple(Icons.Rounded.PersonAdd, taste.artists.size.toString(), stringResource(R.string.kp_stat_following)),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tiles.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (icon, value, label) ->
                                StatTile(
                                    icon = icon,
                                    value = value,
                                    label = label,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }

            val topArtists = listening.stats.topArtists.filterNot { isUnknownArtist(it.name) }.take(8)
            if (topArtists.isNotEmpty()) {
                item(key = "artists") {
                    ProfileSection(title = stringResource(R.string.st_top_artists)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(count = topArtists.size, key = { topArtists[it].name }) { index ->
                                val artist = topArtists[index]
                                Column(
                                    modifier = Modifier.width(88.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // The initial under the picture: it shows
                                    // while the picture is looked up, and stays
                                    // for an artist with none.
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(rememberAvatarShape(index + 1))
                                            .background(
                                                if (index == 0) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = artist.name.trim().take(1).uppercase(),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (index == 0) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        artistPhotos[artist.name]?.let { photo ->
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = pluralStringResource(R.plurals.kp_plays, artist.playCount, artist.playCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val topSongs = listening.stats.topSongs.take(5)
            if (topSongs.isNotEmpty()) {
                item(key = "songs") {
                    ProfileSection(title = stringResource(R.string.st_top_songs)) {
                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                topSongs.forEachIndexed { index, song ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(24.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!song.thumbnailUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = song.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = song.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = song.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = pluralStringResource(R.plurals.kp_plays, song.playCount, song.playCount),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (taste.genres.isNotEmpty()) {
                item(key = "genres") {
                    ProfileSection(title = stringResource(R.string.kp_genres)) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            taste.genres.forEach { genre ->
                                Surface(
                                    shape = RoundedCornerShape(100),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(
                                        text = genre.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (taste.artists.isNotEmpty()) {
                item(key = "following") {
                    ProfileSection(title = stringResource(R.string.kp_following)) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(count = taste.artists.size, key = { taste.artists[it].key }) { index ->
                                val artist = taste.artists[index]
                                Column(
                                    modifier = Modifier.width(76.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = artist.name.trim().take(1).uppercase(),
                                            style = MaterialTheme.typography.titleLarge,
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
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "footer") {
                if (listening.stats.totalPlays == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.st_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.st_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onOpenStats,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.kp_full_stats),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Back, over the hero.
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileHero(
    profile: Profile,
    card: KodaProfileCard,
    firstPlayAtMs: Long?,
    /** Null for an account profile, whose name and picture are YouTube's. */
    onEdit: (() -> Unit)?,
    onSwitchProfile: () -> Unit
) {
    // The avatar lands with a bounce and then drifts, very slightly, so the
    // screen has something alive on it without anything that reads as loading.
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { landed = true }
    val entrance by animateFloatAsState(
        targetValue = if (landed) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "profileAvatarEntrance"
    )
    val drift by rememberInfiniteTransition(label = "profileAvatarDrift").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600), RepeatMode.Reverse),
        label = "profileAvatarDriftValue"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 56.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ProfileAvatar(
                profile = profile,
                card = card,
                modifier = Modifier
                    .size(148.dp)
                    .graphicsLayer {
                        scaleX = entrance
                        scaleY = entrance
                        translationY = drift * 4.dp.toPx()
                        rotationZ = drift * 1.5f
                    }
                    .then(if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier)
            )
            if (onEdit != null) {
                FilledTonalIconButton(
                    onClick = onEdit,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.kp_edit),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = profile.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip(
                text = stringResource(if (profile.isLocal) R.string.kp_koda_profile else R.string.kp_youtube_account),
                strong = true
            )
            firstPlayAtMs?.let { first ->
                val since = remember(first) {
                    java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(first))
                }
                HeroChip(text = stringResource(R.string.kp_since, since), strong = false)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onEdit != null) {
                Button(
                    onClick = onEdit,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.kp_edit), maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = onSwitchProfile,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.kp_switch), maxLines = 1)
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, strong: Boolean) {
    Surface(
        shape = RoundedCornerShape(100),
        color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )
        content()
    }
}

// ---------------------------------------------------------------------------
// Editing
// ---------------------------------------------------------------------------

/**
 * The avatar maker and the name, for a device-only profile.
 *
 * Every choice is applied as it is made - the picture above the options is the
 * real one, and it is already the picture on Home - so there is nothing to
 * save and nothing to lose by swiping the sheet away. It scrolls: five rows of
 * options and a name field do not fit a short screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileEditSheet(
    profile: Profile,
    card: KodaProfileCard,
    store: KodaProfileStore,
    profileManager: ProfileManager,
    onDismiss: () -> Unit
) {
    val haptics = com.ivor.ivormusic.util.rememberKodaHaptics()
    val scope = rememberCoroutineScope()
    val avatar = card.avatar ?: KodaAvatar.forSeed(profile.id)
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    val pick: (KodaAvatar) -> Unit = { next ->
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        store.setAvatar(profile.id, next)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { store.setPhoto(profile.id, uri) }
    }
    val commitName = {
        if (name.isNotBlank() && name.trim() != profile.name) {
            profileManager.updateIdentity(profile.id, name = name.trim())
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            commitName()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ProfileAvatar(profile = profile, card = card, modifier = Modifier.size(124.dp))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { pick(KodaAvatar.random()) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.kp_surprise))
                    }
                    if (card.photoPath == null) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.kp_photo_choose))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { store.clearPhoto(profile.id) },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(stringResource(R.string.kp_photo_remove))
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = { Text(stringResource(R.string.kp_name)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )

            AvatarOptionRow(
                title = stringResource(R.string.kp_shape),
                count = KodaAvatar.SHAPES,
                selected = Math.floorMod(avatar.shape, KodaAvatar.SHAPES),
                onSelect = { pick(avatar.copy(shape = it)) }
            ) { index, modifier -> KodaAvatarView(avatar.copy(shape = index), modifier) }

            Column {
                OptionTitle(stringResource(R.string.kp_color))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(count = KodaAvatarColors.size) { index ->
                        val chosen = Math.floorMod(avatar.color, KodaAvatarColors.size) == index
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 3.dp,
                                    color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .padding(6.dp)
                                .clip(CircleShape)
                                .background(Color(KodaAvatarColors[index].fill))
                                .clickable { pick(avatar.copy(color = index)) }
                        )
                    }
                }
            }

            AvatarOptionRow(
                title = stringResource(R.string.kp_eyes),
                count = KodaAvatar.EYES,
                selected = Math.floorMod(avatar.eyes, KodaAvatar.EYES),
                onSelect = { pick(avatar.copy(eyes = it)) }
            ) { index, modifier -> KodaAvatarView(avatar.copy(eyes = index), modifier) }

            AvatarOptionRow(
                title = stringResource(R.string.kp_mouth),
                count = KodaAvatar.MOUTHS,
                selected = Math.floorMod(avatar.mouth, KodaAvatar.MOUTHS),
                onSelect = { pick(avatar.copy(mouth = it)) }
            ) { index, modifier -> KodaAvatarView(avatar.copy(mouth = index), modifier) }

            AvatarOptionRow(
                title = stringResource(R.string.kp_extra),
                count = KodaAvatar.EXTRAS,
                selected = Math.floorMod(avatar.extra, KodaAvatar.EXTRAS),
                onSelect = { pick(avatar.copy(extra = it)) }
            ) { index, modifier -> KodaAvatarView(avatar.copy(extra = index), modifier) }

            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { pick(avatar.copy(blush = !avatar.blush)) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.kp_blush),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = avatar.blush, onCheckedChange = null)
            }

            Button(
                onClick = {
                    commitName()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.action_done), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun OptionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
    )
}

/** One row of the maker: the current avatar with each value of one feature. */
@Composable
private fun AvatarOptionRow(
    title: String,
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    option: @Composable (index: Int, modifier: Modifier) -> Unit
) {
    Column {
        OptionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(count = count) { index ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (index == selected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(index) }
                        .padding(7.dp)
                ) {
                    option(index, Modifier.fillMaxSize())
                }
            }
        }
    }
}
