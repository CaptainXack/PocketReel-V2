package com.captainxack.pocketreel

import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.captainxack.pocketreel.data.WantedRequest
import com.captainxack.pocketreel.media.ActorProfile
import com.captainxack.pocketreel.media.CastMember
import com.captainxack.pocketreel.media.DiscoverItem
import com.captainxack.pocketreel.media.FilmCredit
import com.captainxack.pocketreel.media.MediaItem
import com.captainxack.pocketreel.media.MediaKind
import com.captainxack.pocketreel.ui.theme.PocketReelTheme

class MainActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketReelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MediaDeckScreen(
                        vm = vm,
                        onPickTree = ::persistTreeAccess,
                        onPlay = ::launchPlayer,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPlaybackState()
    }

    private fun persistTreeAccess(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        vm.onTreeSelected(uri)
    }

    private fun launchPlayer(item: MediaItem) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, item.documentUri)
            putExtra(PlayerActivity.EXTRA_TITLE, episodeHeadline(item))
            putExtra(PlayerActivity.EXTRA_AUTOPLAY_STREAK, 0)

            val seriesEpisodes = buildSeriesPlaylist(item)
            if (seriesEpisodes.isNotEmpty()) {
                putStringArrayListExtra(
                    PlayerActivity.EXTRA_SERIES_URIS,
                    ArrayList(seriesEpisodes.map { it.documentUri }),
                )
                putStringArrayListExtra(
                    PlayerActivity.EXTRA_SERIES_TITLES,
                    ArrayList(seriesEpisodes.map { episodeHeadline(it) }),
                )
                putExtra(
                    PlayerActivity.EXTRA_SERIES_INDEX,
                    seriesEpisodes.indexOfFirst { it.documentUri == item.documentUri },
                )
            }
        })
    }

    private fun buildSeriesPlaylist(item: MediaItem): List<MediaItem> {
        if (item.mediaKind != MediaKind.EPISODE || item.seriesTitle.isNullOrBlank()) return emptyList()
        return vm.uiState.value.library
            .filter { candidate ->
                candidate.mediaKind == MediaKind.EPISODE && candidate.seriesTitle == item.seriesTitle
            }
            .sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }, { it.title.lowercase() }))
    }
}

private sealed class DeckScreen {
    object Home : DeckScreen()
    object Settings : DeckScreen()
    data class SeriesDetail(val title: String) : DeckScreen()
    data class ActorDetail(val actorId: Int, val actorName: String) : DeckScreen()
}

data class SeriesGroup(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val castNames: List<String>,
    val castMembers: List<CastMember>,
    val genreNames: List<String>,
    val episodes: List<MediaItem>,
) {
    val seasons: Map<Int, List<MediaItem>> = episodes.groupBy { it.seasonNumber ?: 0 }.toSortedMap()
    val seasonCount: Int get() = seasons.size
    val episodeCount: Int get() = episodes.size
}

@Composable
private fun MediaDeckScreen(
    vm: MainViewModel,
    onPickTree: (Uri) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    val state = vm.uiState.value
    var screen by remember { mutableStateOf<DeckScreen>(DeckScreen.Home) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onPickTree)
    }

    val seriesGroups = remember(state.library) { buildSeriesGroups(state.library) }
    val selectedSeries = (screen as? DeckScreen.SeriesDetail)?.let { target ->
        seriesGroups.firstOrNull { it.title == target.title }
    }
    val selectedActor = screen as? DeckScreen.ActorDetail
    val movieItems = state.library.filter { it.mediaKind == MediaKind.MOVIE }.sortedBy { it.title.lowercase() }
    val miscItems = state.library.filter {
        it.mediaKind == MediaKind.VIDEO || (it.mediaKind == MediaKind.EPISODE && it.seriesTitle.isNullOrBlank())
    }.sortedBy { it.title.lowercase() }

    when {
        selectedActor != null -> {
            LaunchedEffect(selectedActor.actorId) {
                vm.ensureActorProfile(selectedActor.actorId)
            }
            ActorDetailScreen(
                actorName = selectedActor.actorName,
                profile = vm.actorProfiles[selectedActor.actorId],
                library = state.library,
                requestedMediaKeys = state.requestedMediaKeys,
                onBack = { screen = DeckScreen.Home },
                onPlay = onPlay,
                onRequest = { credit -> vm.requestMedia(credit.mediaType, credit.id, credit.title) },
            )
        }

        selectedSeries != null -> {
            SeriesDetailScreen(
                group = selectedSeries,
                watchedUris = state.watchedUris,
                resumePositionsMs = state.resumePositionsMs,
                resumeProgressFractions = state.resumeProgressFractions,
                expectedSeasonEpisodeNumbers = vm.seasonEpisodeNumbers,
                onEnsureSeasonEpisodeNumbers = vm::ensureSeasonEpisodeNumbers,
                onBack = { screen = DeckScreen.Home },
                onPlay = onPlay,
                onOpenActor = { actor -> screen = DeckScreen.ActorDetail(actor.id, actor.name) },
            )
        }

        screen == DeckScreen.Settings -> {
            SettingsScreen(
                state = state,
                onBack = { screen = DeckScreen.Home },
                onPickFolder = { launcher.launch(null) },
                onRescan = vm::rescanLibrary,
                onRefreshArtwork = vm::fetchArtwork,
                onTmdbLanguageChange = vm::updateTmdbLanguage,
                onSubtitleLanguageChange = vm::updatePreferredSubtitleLanguage,
                onAudioLanguageChange = vm::updatePreferredAudioLanguage,
                onEnableOnlinePlaybackChange = vm::updateEnableOnlinePlayback,
                onAllDebridApiKeyChange = vm::updateAllDebridApiKey,
                onPreferredOnlineAudioModeChange = vm::updatePreferredOnlineAudioMode,
                onPreferSeriesPacksChange = vm::updatePreferSeriesPacks,
                onAutoArtworkChange = vm::updateAutoFetchArtworkOnScan,
                onSave = vm::saveSettings,
            )
        }

        else -> {
            HomeScreen(
                state = state,
                seriesGroups = seriesGroups,
                movieItems = movieItems,
                miscItems = miscItems,
                onPickFolder = { launcher.launch(null) },
                onOpenSettings = { screen = DeckScreen.Settings },
                onOpenSeries = { group -> screen = DeckScreen.SeriesDetail(group.title) },
                onPlay = onPlay,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    seriesGroups: List<SeriesGroup>,
    movieItems: List<MediaItem>,
    miscItems: List<MediaItem>,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSeries: (SeriesGroup) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    val onlineEnabled = state.enableOnlinePlayback && state.allDebridApiKey.isNotBlank()
    val onlineSeries = remember(state.discoverItems, state.library, onlineEnabled) {
        if (!onlineEnabled) emptyList() else state.discoverItems
            .filter { it.mediaType == "tv" }
            .filterNot { discover -> isDiscoverItemLocal(discover, state.library) }
            .sortedBy { it.title.lowercase() }
    }
    val onlineMovies = remember(state.discoverItems, state.library, onlineEnabled) {
        if (!onlineEnabled) emptyList() else state.discoverItems
            .filter { it.mediaType == "movie" }
            .filterNot { discover -> isDiscoverItemLocal(discover, state.library) }
            .sortedBy { it.title.lowercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            SeamlessHero(
                title = "PocketReel",
                subtitle = "Private library. Premium playback.",
                highlight = state.status,
                onOpenSettings = onOpenSettings,
            )
        }

        if (state.library.isEmpty()) {
            item {
                MinimalSetupPrompt(
                    isBusy = state.isBusy,
                    onPickFolder = onPickFolder,
                    onOpenSettings = onOpenSettings,
                )
            }
        } else {
            if (state.continueWatching.isNotEmpty()) {
                item {
                    ShelfSection(
                        title = "Continue Watching",
                        items = state.continueWatching,
                        onPlay = onPlay,
                        watchedUris = state.watchedUris,
                        resumePositionsMs = state.resumePositionsMs,
                        resumeProgressFractions = state.resumeProgressFractions,
                    )
                }
            }
            if (seriesGroups.isNotEmpty()) {
                item { SeriesGroupShelf(title = "Local Series", subtitle = "Downloaded shows", groups = seriesGroups, onOpenSeries = onOpenSeries) }
            }
            if (onlineSeries.isNotEmpty()) {
                item { OnlineShelfSection(title = "Online Series", subtitle = "Tap or hold any poster for streaming options", items = onlineSeries) }
            }
            if (movieItems.isNotEmpty()) {
                item {
                    ShelfSection(
                        title = "Local Movies",
                        items = movieItems,
                        onPlay = onPlay,
                        watchedUris = state.watchedUris,
                        resumePositionsMs = state.resumePositionsMs,
                        resumeProgressFractions = state.resumeProgressFractions,
                    )
                }
            }
            if (onlineMovies.isNotEmpty()) {
                item { OnlineShelfSection(title = "Online Movies", subtitle = "Tap or hold any poster for streaming options", items = onlineMovies) }
            }
            if (miscItems.isNotEmpty()) {
                item {
                    ShelfSection(
                        title = "Other Videos",
                        items = miscItems,
                        onPlay = onPlay,
                        watchedUris = state.watchedUris,
                        resumePositionsMs = state.resumePositionsMs,
                        resumeProgressFractions = state.resumeProgressFractions,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeamlessHero(
    title: String,
    subtitle: String,
    highlight: String,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF275B9A), Color(0xFF1B2230), Color(0xFF131720))),
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (highlight.isNotBlank()) {
                SubtleMetaText(highlight)
            }
        }
    }
}

@Composable
private fun MinimalSetupPrompt(
    isBusy: Boolean,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (isBusy) "Scanning your media" else "Choose a folder to begin",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (isBusy) "PocketReel is gathering your videos." else "The controls stay out of the way after setup.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPickFolder, enabled = !isBusy) { Text("Pick folder") }
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        }
        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onRefreshArtwork: () -> Unit,
    onTmdbLanguageChange: (String) -> Unit,
    onSubtitleLanguageChange: (String) -> Unit,
    onAudioLanguageChange: (String) -> Unit,
    onEnableOnlinePlaybackChange: (Boolean) -> Unit,
    onAllDebridApiKeyChange: (String) -> Unit,
    onPreferredOnlineAudioModeChange: (String) -> Unit,
    onPreferSeriesPacksChange: (Boolean) -> Unit,
    onAutoArtworkChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BackHeader(title = "Settings", subtitle = "Hidden away so the library stays clean.", onBack = onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(friendlyFolderLabel(state.mediaTreeUri), style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onPickFolder) { Text("Pick folder") }
                        OutlinedButton(onClick = onRescan, enabled = !state.isBusy) { Text("Rescan") }
                        OutlinedButton(onClick = onRefreshArtwork, enabled = state.library.isNotEmpty() && !state.isBusy) { Text("Refresh") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Online Services", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    SettingToggleRow(
                        title = "Enable online playback",
                        subtitle = "Turn on online rows and AllDebrid-powered tests when configured.",
                        checked = state.enableOnlinePlayback,
                        onCheckedChange = onEnableOnlinePlaybackChange,
                    )
                    OutlinedTextField(
                        value = state.allDebridApiKey,
                        onValueChange = onAllDebridApiKeyChange,
                        label = { Text("AllDebrid API key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.preferredOnlineAudioMode,
                        onValueChange = onPreferredOnlineAudioModeChange,
                        label = { Text("Online audio mode") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    SettingToggleRow(
                        title = "Prefer season or full packs",
                        subtitle = "Useful when full-show English or multi-audio packs are available.",
                        checked = state.preferSeriesPacks,
                        onCheckedChange = onPreferSeriesPacksChange,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Metadata", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = state.tmdbLanguage, onValueChange = onTmdbLanguageChange, label = { Text("TMDb language") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    SettingToggleRow(
                        title = "Auto artwork on scan",
                        subtitle = "Turn off for faster raw scans.",
                        checked = state.autoFetchArtworkOnScan,
                        onCheckedChange = onAutoArtworkChange,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = state.preferredSubtitleLanguage, onValueChange = onSubtitleLanguageChange, label = { Text("Subtitle language") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = state.preferredAudioLanguage, onValueChange = onAudioLanguageChange, label = { Text("Audio language") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = onSave) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SeriesGroupShelf(title: String, subtitle: String, groups: List<SeriesGroup>, onOpenSeries: (SeriesGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfHeader(title = title, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(groups, key = { it.title }) { group -> SeriesPosterCard(group = group, onOpen = { onOpenSeries(group) }) }
        }
    }
}

@Composable
private fun SeriesPosterCard(group: SeriesGroup, onOpen: () -> Unit) {
    Column(
        modifier = Modifier.width(220.dp).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosterArt(artworkUrl = group.posterUrl, contentDescription = group.title, width = 220.dp, height = 310.dp)
        Text(group.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        SubtleMetaText("${group.seasonCount} seasons • ${group.episodeCount} episodes")
    }
}

@Composable
private fun OnlineShelfSection(title: String, subtitle: String, items: List<DiscoverItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfHeader(title = title, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(items, key = { "${it.mediaType}:${it.id}" }) { item ->
                OnlinePosterCard(item = item)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlinePosterCard(item: DiscoverItem) {
    var expanded by remember(item.id) { mutableStateOf(false) }
    Column(modifier = Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(282.dp)
                .combinedClickable(onClick = { expanded = true }, onLongClick = { expanded = true }),
        ) {
            PosterArt(artworkUrl = item.posterUrl, contentDescription = item.title, width = 200.dp, height = 282.dp)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x66000000)),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("ONLINE", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Tap or hold for options", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Play Online") }, onClick = { expanded = false })
                DropdownMenuItem(text = { Text("Download") }, onClick = { expanded = false })
            }
        }
        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        SubtleMetaText(listOfNotNull(item.mediaType.uppercase(), item.releaseLabel).joinToString(" • "))
    }
}

@Composable
private fun ShelfHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x22FFFFFF)),
        )
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeriesDetailScreen(
    group: SeriesGroup,
    watchedUris: Set<String>,
    resumePositionsMs: Map<String, Long>,
    resumeProgressFractions: Map<String, Float>,
    expectedSeasonEpisodeNumbers: Map<String, List<Int>>,
    onEnsureSeasonEpisodeNumbers: (Int, Int) -> Unit,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onOpenActor: (CastMember) -> Unit,
) {
    var selectedTab by remember(group.title) { mutableStateOf("Episodes") }
    var selectedSeason by remember(group.title) { mutableStateOf(group.seasons.keys.firstOrNull() ?: 0) }
    val featuredEpisode = remember(group, watchedUris, resumePositionsMs) {
        pickFeaturedEpisode(group, watchedUris, resumePositionsMs)
    }
    val seasonEpisodes = group.seasons[selectedSeason].orEmpty()
    val tvId = remember(group.title) { group.episodes.firstOrNull { it.tmdbMediaType == "tv" }?.tmdbId ?: 0 }
    LaunchedEffect(tvId, selectedSeason) {
        if (tvId > 0 && selectedSeason >= 0) {
            onEnsureSeasonEpisodeNumbers(tvId, selectedSeason)
        }
    }
    val tmdbExpected = if (tvId > 0) expectedSeasonEpisodeNumbers[buildSeasonKey(tvId, selectedSeason)] else null
    val missingEpisodes = remember(group.title, selectedSeason, seasonEpisodes, tmdbExpected) {
        computeMissingEpisodeLabels(seasonEpisodes, tmdbExpected)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { StreamingBackHeader(onBack = onBack) }
        item {
            NetflixStyleHero(
                group = group,
                featuredEpisode = featuredEpisode,
                onPlay = { featuredEpisode?.let(onPlay) },
            )
        }
        item {
            DetailTabsRow(selectedTab = selectedTab, onSelect = { selectedTab = it })
        }

        if (selectedTab == "Episodes") {
            if (group.seasons.size > 1) {
                item {
                    SeasonTabsRow(
                        seasons = group.seasons.keys.toList(),
                        selectedSeason = selectedSeason,
                        onSelect = { selectedSeason = it },
                    )
                }
            }
            if (missingEpisodes.isNotEmpty()) {
                item {
                    Text(
                        "Missing: ${missingEpisodes.joinToString(", ")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    seasonEpisodes.forEach { item ->
                        EpisodeRow(
                            item = item,
                            watched = item.documentUri in watchedUris,
                            resumePositionMs = resumePositionsMs[item.documentUri],
                            progressFraction = resumeProgressFractions[item.documentUri],
                            onPlay = { onPlay(item) },
                        )
                    }
                }
            }
        } else {
            item {
                MoreInfoSection(group = group, onOpenActor = onOpenActor)
            }
        }
    }
}

@Composable
private fun StreamingBackHeader(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun NetflixStyleHero(
    group: SeriesGroup,
    featuredEpisode: MediaItem?,
    onPlay: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!group.backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = group.backdropUrl,
                contentDescription = group.title,
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
        Text(
            text = "POCKETREEL SERIES",
            color = Color(0xFFE50914),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            group.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            "${group.seasonCount} Seasons • ${group.episodeCount} Episodes",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.genreNames.isNotEmpty()) {
            MetadataPillRow(group.genreNames.take(4))
        }
        if (!group.overview.isNullOrBlank()) {
            Text(
                group.overview,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
            enabled = featuredEpisode != null,
        ) {
            Text(if (featuredEpisode != null) "Play" else "Unavailable")
        }
        if (featuredEpisode != null) {
            SubtleMetaText("Starts with ${episodeHeadline(featuredEpisode)}")
        }
    }
}

@Composable
private fun DetailTabsRow(selectedTab: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("Episodes", "Cast & More").forEach { tab ->
            Column(
                modifier = Modifier.clickable { onSelect(tab) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    tab,
                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedTab == tab) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(2.dp)
                        .background(if (selectedTab == tab) Color(0xFFE50914) else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun SeasonTabsRow(seasons: List<Int>, selectedSeason: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        seasons.forEach { season ->
            TextButton(onClick = { onSelect(season) }) {
                Text(
                    if (season > 0) "Season $season" else "Episodes",
                    color = if (selectedSeason == season) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreInfoSection(group: SeriesGroup, onOpenActor: (CastMember) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (group.castMembers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cast", fontWeight = FontWeight.Bold)
                CastCarousel(castMembers = group.castMembers, onOpenActor = onOpenActor)
            }
        }
        if (!group.overview.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Overview", fontWeight = FontWeight.Bold)
                Text(group.overview, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (group.genreNames.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Genres", fontWeight = FontWeight.Bold)
                MetadataPillRow(group.genreNames)
            }
        }
    }
}

@Composable
private fun CastCarousel(castMembers: List<CastMember>, onOpenActor: (CastMember) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
        items(castMembers, key = { it.id }) { actor ->
            Column(
                modifier = Modifier.width(112.dp).clickable { onOpenActor(actor) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActorHeadshot(actor = actor)
                Text(actor.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                actor.character?.let { SubtleMetaText(it) }
            }
        }
    }
}

@Composable
private fun ActorDetailScreen(
    actorName: String,
    profile: ActorProfile?,
    library: List<MediaItem>,
    requestedMediaKeys: Set<String>,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onRequest: (FilmCredit) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { StreamingBackHeader(onBack = onBack) }
        item {
            ActorHero(profile = profile, fallbackName = actorName)
        }
        item {
            Text("Starring in", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (profile == null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text("Loading credits")
                }
            }
        } else {
            items(profile.credits, key = { "${it.mediaType}:${it.id}" }) { credit ->
                val localMatch = findLocalMatchForCredit(library, credit)
                val requestKey = buildRequestKey(credit.mediaType, credit.id)
                FilmographyRow(
                    credit = credit,
                    localMatch = localMatch,
                    requested = requestKey in requestedMediaKeys,
                    onPlay = { localMatch?.let(onPlay) },
                    onRequest = { onRequest(credit) },
                )
            }
        }
    }
}

@Composable
private fun ActorHero(profile: ActorProfile?, fallbackName: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        if (profile?.profileUrl != null) {
            AsyncImage(
                model = profile.profileUrl,
                contentDescription = profile.name,
                modifier = Modifier.width(120.dp).height(160.dp),
            )
        } else {
            Box(
                modifier = Modifier.width(120.dp).height(160.dp).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("No photo")
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile?.name ?: fallbackName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            profile?.knownForDepartment?.let { SubtleMetaText(it) }
            profile?.biography?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun FilmographyRow(
    credit: FilmCredit,
    localMatch: MediaItem?,
    requested: Boolean,
    onPlay: () -> Unit,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        EpisodeThumbnail(url = credit.posterUrl ?: credit.backdropUrl, contentDescription = credit.title)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(credit.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            credit.releaseLabel?.let { SubtleMetaText(it) }
            credit.overview?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    localMatch != null -> Button(onClick = onPlay) { Text("Play") }
                    requested -> TextButton(onClick = {}) { Text("Requested") }
                    else -> OutlinedButton(onClick = onRequest) { Text("Request") }
                }
            }
        }
    }
}

@Composable
private fun BackHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            SubtleMetaText(subtitle)
        }
    }
}

@Composable
private fun ShelfSection(
    title: String,
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    watchedUris: Set<String> = emptySet(),
    resumePositionsMs: Map<String, Long> = emptyMap(),
    resumeProgressFractions: Map<String, Float> = emptyMap(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfHeader(title = title, subtitle = "Downloaded and ready to play")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(items, key = { it.id }) { item ->
                HorizontalShelfCard(
                    item = item,
                    onPlay = { onPlay(item) },
                    watched = item.documentUri in watchedUris,
                    resumePositionMs = resumePositionsMs[item.documentUri],
                    progressFraction = resumeProgressFractions[item.documentUri],
                )
            }
        }
    }
}

@Composable
private fun HorizontalShelfCard(item: MediaItem, onPlay: () -> Unit, watched: Boolean = false, resumePositionMs: Long? = null, progressFraction: Float? = null) {
    val metaText = when {
        watched -> "Watched"
        resumePositionMs != null && resumePositionMs > 0L -> "Resume ${formatResumeLabel(resumePositionMs)}"
        else -> item.releaseLabel ?: shelfTypeLabel(item)
    }
    val actionText = when {
        watched -> "Play Again"
        resumePositionMs != null && resumePositionMs > 0L -> "Resume"
        else -> "Play"
    }

    Column(
        modifier = Modifier.width(200.dp).clickable(onClick = onPlay),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosterArt(artworkUrl = item.artworkUrl, contentDescription = item.title, width = 200.dp, height = 282.dp)
        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        SubtleMetaText(metaText)
        if (!watched && progressFraction != null && progressFraction > 0f) {
            LinearProgressIndicator(progress = progressFraction, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = onPlay) { Text(actionText) }
    }
}

@Composable
private fun EpisodeRow(item: MediaItem, watched: Boolean, resumePositionMs: Long?, progressFraction: Float?, onPlay: () -> Unit) {
    val metaText = when {
        watched -> "Watched"
        resumePositionMs != null && resumePositionMs > 0L -> "Resume ${formatResumeLabel(resumePositionMs)}"
        else -> item.releaseLabel ?: shelfTypeLabel(item)
    }
    val thumbnailUrl = item.backdropUrl ?: item.artworkUrl

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        EpisodeThumbnail(url = thumbnailUrl, contentDescription = item.title)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(episodeHeadline(item), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            SubtleMetaText(metaText)
            if (!item.overview.isNullOrBlank()) {
                Text(item.overview, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (!watched && progressFraction != null && progressFraction > 0f) {
                LinearProgressIndicator(progress = progressFraction, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onPlay) { Text(if (resumePositionMs != null && resumePositionMs > 0L && !watched) "Resume" else if (watched) "Play Again" else "Play") }
        }
    }
}

@Composable
private fun EpisodeThumbnail(url: String?, contentDescription: String) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = Modifier.width(128.dp).height(72.dp),
        )
    } else {
        Box(
            modifier = Modifier.width(128.dp).height(72.dp).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("No art")
        }
    }
}

@Composable
private fun ActorHeadshot(actor: CastMember) {
    if (actor.profileUrl != null) {
        AsyncImage(
            model = actor.profileUrl,
            contentDescription = actor.name,
            modifier = Modifier.width(112.dp).height(140.dp),
        )
    } else {
        Box(
            modifier = Modifier.width(112.dp).height(140.dp).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("No photo")
        }
    }
}

@Composable
private fun PosterArt(artworkUrl: String?, contentDescription: String, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    if (artworkUrl != null) {
        AsyncImage(model = artworkUrl, contentDescription = contentDescription, modifier = Modifier.width(width).height(height))
    } else {
        Box(modifier = Modifier.width(width).height(height).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text("No art")
        }
    }
}

@Composable
private fun MetadataPillRow(values: List<String>) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SubtleMetaText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun buildSeriesGroups(library: List<MediaItem>): List<SeriesGroup> {
    return library.filter { it.mediaKind == MediaKind.EPISODE && !it.seriesTitle.isNullOrBlank() }
        .groupBy { it.seriesTitle.orEmpty() }
        .map { (title, episodes) ->
            val sortedEpisodes = episodes.sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
            val artworkAnchor = sortedEpisodes.firstOrNull { !it.artworkUrl.isNullOrBlank() } ?: sortedEpisodes.first()
            val detailAnchor = sortedEpisodes.firstOrNull { it.castMembers.isNotEmpty() || it.genreNames.isNotEmpty() } ?: artworkAnchor
            SeriesGroup(
                title = title,
                posterUrl = artworkAnchor.artworkUrl,
                backdropUrl = artworkAnchor.backdropUrl,
                overview = sortedEpisodes.firstNotNullOfOrNull { it.seriesOverview ?: it.overview },
                castNames = detailAnchor.castNames,
                castMembers = detailAnchor.castMembers,
                genreNames = detailAnchor.genreNames,
                episodes = sortedEpisodes,
            )
        }
        .sortedBy { it.title.lowercase() }
}

private fun pickFeaturedEpisode(
    group: SeriesGroup,
    watchedUris: Set<String>,
    resumePositionsMs: Map<String, Long>,
): MediaItem? {
    val sorted = group.episodes.sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
    return sorted.firstOrNull { (resumePositionsMs[it.documentUri] ?: 0L) > 0L } ?: sorted.firstOrNull { it.documentUri !in watchedUris } ?: sorted.firstOrNull()
}

private fun computeMissingEpisodeLabels(episodes: List<MediaItem>, expectedNumbers: List<Int>?): List<String> {
    val localNumbers = episodes.mapNotNull { it.episodeNumber }.distinct().sorted()
    val sourceNumbers = expectedNumbers?.takeIf { it.isNotEmpty() } ?: localNumbers
    if (sourceNumbers.isEmpty()) return emptyList()
    val missing = mutableListOf<String>()
    val season = episodes.firstOrNull()?.seasonNumber ?: 0
    for (n in sourceNumbers) {
        if (n !in localNumbers) {
            missing.add(if (season > 0) "S${season.toString().padStart(2, '0')}E${n.toString().padStart(2, '0')}" else "Episode $n")
        }
    }
    return missing
}

private fun isDiscoverItemLocal(item: DiscoverItem, library: List<MediaItem>): Boolean {
    return library.any { local ->
        (local.tmdbId == item.id && local.tmdbMediaType == item.mediaType) ||
            normalizeMatchTitle(local.title) == normalizeMatchTitle(item.title)
    }
}

private fun findLocalMatchForCredit(library: List<MediaItem>, credit: FilmCredit): MediaItem? {
    return library.firstOrNull {
        it.tmdbId == credit.id && it.tmdbMediaType == credit.mediaType
    } ?: library.firstOrNull {
        normalizeMatchTitle(it.title) == normalizeMatchTitle(credit.title)
    }
}

private fun normalizeMatchTitle(value: String): String {
    return value.lowercase().replace(Regex("[^a-z0-9]"), "")
}

private fun buildRequestKey(mediaType: String, tmdbId: Int): String = "$mediaType:$tmdbId"

private fun buildSeasonKey(tvId: Int, seasonNumber: Int): String = "$tvId:$seasonNumber"

private fun friendlyFolderLabel(uriString: String?): String {
    if (uriString.isNullOrBlank()) return "No folder linked"
    val decoded = Uri.decode(uriString)
    val treePart = decoded.substringAfter("tree/", decoded)
    val clean = treePart.substringAfter(':', treePart)
    return clean.ifBlank { "Linked folder" }
}

private fun episodeHeadline(item: MediaItem): String {
    val episodeCodeRegex = Regex("""(?i)^S\d{2}E\d{2}$""")
    val episodeName = item.episodeTitle?.takeIf {
        it.isNotBlank() && !episodeCodeRegex.matches(it) && !it.equals(item.releaseLabel, ignoreCase = true)
    }
    return when {
        item.mediaKind != MediaKind.EPISODE -> item.title
        !episodeName.isNullOrBlank() && !item.releaseLabel.isNullOrBlank() -> "${item.releaseLabel} • $episodeName"
        !episodeName.isNullOrBlank() -> episodeName
        !item.releaseLabel.isNullOrBlank() -> item.releaseLabel
        else -> item.title
    }
}

private fun shelfTypeLabel(item: MediaItem): String {
    return when (item.mediaKind) {
        MediaKind.MOVIE -> "Movie"
        MediaKind.EPISODE -> listOfNotNull(item.seriesTitle, item.releaseLabel).joinToString(" • ").ifBlank { "Episode" }
        MediaKind.VIDEO -> item.mimeType ?: "Video"
    }
}

private fun formatResumeLabel(positionMs: Long): String {
    val totalSeconds = positionMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
