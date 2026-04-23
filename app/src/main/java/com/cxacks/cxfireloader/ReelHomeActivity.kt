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
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.captainxack.pocketreel.media.ActorProfile
import com.captainxack.pocketreel.media.CastMember
import com.captainxack.pocketreel.media.DiscoverItem
import com.captainxack.pocketreel.media.FilmCredit
import com.captainxack.pocketreel.media.MediaItem
import com.captainxack.pocketreel.media.MediaKind
import com.captainxack.pocketreel.ui.theme.PocketReelTheme

class ReelHomeActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketReelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ReelDeckScreen(
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

private sealed class ReelScreen {
    object Home : ReelScreen()
    object Settings : ReelScreen()
    data class SeriesDetail(val title: String) : ReelScreen()
    data class ActorDetail(val actorId: Int, val actorName: String) : ReelScreen()
    data class OnlineDetail(val mediaType: String, val id: Int, val title: String) : ReelScreen()
}

private enum class ReelModeTab { Local, Online, Search }
private enum class ReelMediaTab { All, Movies, Series }

data class ReelSeriesGroup(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val castMembers: List<CastMember>,
    val genreNames: List<String>,
    val episodes: List<MediaItem>,
) {
    val seasons: Map<Int, List<MediaItem>> = episodes.groupBy { it.seasonNumber ?: 0 }.toSortedMap()
    val seasonCount: Int get() = seasons.size
    val episodeCount: Int get() = episodes.size
}

@Composable
private fun ReelDeckScreen(
    vm: MainViewModel,
    onPickTree: (Uri) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    val state = vm.uiState.value
    var screen by remember { mutableStateOf<ReelScreen>(ReelScreen.Home) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onPickTree)
    }

    val seriesGroups = remember(state.library) { buildSeriesGroups(state.library) }
    val movieItems = remember(state.library) { state.library.filter { it.mediaKind == MediaKind.MOVIE }.sortedBy { it.title.lowercase() } }
    val miscItems = remember(state.library) {
        state.library.filter { it.mediaKind == MediaKind.VIDEO || (it.mediaKind == MediaKind.EPISODE && it.seriesTitle.isNullOrBlank()) }
            .sortedBy { it.title.lowercase() }
    }
    val onlineSeries = remember(state.discoverItems, state.library) {
        state.discoverItems.filter { it.mediaType == "tv" }.filterNot { isDiscoverItemLocal(it, state.library) }.sortedBy { it.title.lowercase() }
    }
    val onlineMovies = remember(state.discoverItems, state.library) {
        state.discoverItems.filter { it.mediaType == "movie" }.filterNot { isDiscoverItemLocal(it, state.library) }.sortedBy { it.title.lowercase() }
    }

    when (val activeScreen = screen) {
        is ReelScreen.SeriesDetail -> {
            val selectedSeries = seriesGroups.firstOrNull { it.title == activeScreen.title }
            if (selectedSeries == null) {
                screen = ReelScreen.Home
            } else {
                SeriesDetailScreen(
                    group = selectedSeries,
                    watchedUris = state.watchedUris,
                    resumePositionsMs = state.resumePositionsMs,
                    resumeProgressFractions = state.resumeProgressFractions,
                    expectedSeasonEpisodeNumbers = vm.seasonEpisodeNumbers,
                    onEnsureSeasonEpisodeNumbers = vm::ensureSeasonEpisodeNumbers,
                    onBack = { screen = ReelScreen.Home },
                    onPlay = onPlay,
                    onOpenActor = { actor -> screen = ReelScreen.ActorDetail(actor.id, actor.name) },
                )
            }
        }

        is ReelScreen.ActorDetail -> {
            LaunchedEffect(activeScreen.actorId) {
                vm.ensureActorProfile(activeScreen.actorId)
            }
            ActorDetailScreen(
                actorName = activeScreen.actorName,
                profile = vm.actorProfiles[activeScreen.actorId],
                library = state.library,
                requestedMediaKeys = state.requestedMediaKeys,
                onBack = { screen = ReelScreen.Home },
                onPlay = onPlay,
                onRequest = { credit -> vm.requestMedia(credit.mediaType, credit.id, credit.title) },
            )
        }

        is ReelScreen.OnlineDetail -> {
            val item = state.discoverItems.firstOrNull { it.mediaType == activeScreen.mediaType && it.id == activeScreen.id }
            if (item == null) {
                screen = ReelScreen.Home
            } else {
                OnlineDetailScreen(
                    item = item,
                    rssFeedCount = configuredRssFeeds(state.rssFeedsText).size,
                    onlineEnabled = state.enableOnlinePlayback,
                    onBack = { screen = ReelScreen.Home },
                )
            }
        }

        ReelScreen.Settings -> {
            SettingsScreen(
                state = state,
                onBack = { screen = ReelScreen.Home },
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
                onRssFeedsTextChange = vm::updateRssFeedsText,
                onAutoArtworkChange = vm::updateAutoFetchArtworkOnScan,
                onSave = vm::saveSettings,
            )
        }

        ReelScreen.Home -> {
            HomeScreen(
                state = state,
                seriesGroups = seriesGroups,
                movieItems = movieItems,
                miscItems = miscItems,
                onlineSeries = onlineSeries,
                onlineMovies = onlineMovies,
                onPickFolder = { launcher.launch(null) },
                onOpenSettings = { screen = ReelScreen.Settings },
                onOpenSeries = { group -> screen = ReelScreen.SeriesDetail(group.title) },
                onOpenOnline = { item -> screen = ReelScreen.OnlineDetail(item.mediaType, item.id, item.title) },
                onPlay = onPlay,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    seriesGroups: List<ReelSeriesGroup>,
    movieItems: List<MediaItem>,
    miscItems: List<MediaItem>,
    onlineSeries: List<DiscoverItem>,
    onlineMovies: List<DiscoverItem>,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSeries: (ReelSeriesGroup) -> Unit,
    onOpenOnline: (DiscoverItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    var modeTab by rememberSaveable { mutableStateOf(ReelModeTab.Local) }
    var mediaTab by rememberSaveable { mutableStateOf(ReelMediaTab.All) }
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val rssCount = configuredRssFeeds(state.rssFeedsText).size

    val filteredSeriesGroups = remember(seriesGroups, normalizedQuery) {
        if (normalizedQuery.isBlank()) seriesGroups else seriesGroups.filter {
            it.title.lowercase().contains(normalizedQuery) || (it.overview?.lowercase()?.contains(normalizedQuery) == true)
        }
    }
    val filteredMovieItems = remember(movieItems, normalizedQuery) {
        if (normalizedQuery.isBlank()) movieItems else movieItems.filter {
            it.title.lowercase().contains(normalizedQuery) || (it.overview?.lowercase()?.contains(normalizedQuery) == true)
        }
    }
    val filteredOnlineSeries = remember(onlineSeries, normalizedQuery) {
        if (normalizedQuery.isBlank()) onlineSeries else onlineSeries.filter {
            it.title.lowercase().contains(normalizedQuery) || (it.overview?.lowercase()?.contains(normalizedQuery) == true)
        }
    }
    val filteredOnlineMovies = remember(onlineMovies, normalizedQuery) {
        if (normalizedQuery.isBlank()) onlineMovies else onlineMovies.filter {
            it.title.lowercase().contains(normalizedQuery) || (it.overview?.lowercase()?.contains(normalizedQuery) == true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SeamlessHero(
                title = "PocketReel",
                subtitle = when (modeTab) {
                    ReelModeTab.Local -> "Local library with TMDb polish"
                    ReelModeTab.Online -> "Online browse with posters and detail pages"
                    ReelModeTab.Search -> "Search local and online together"
                },
                highlight = state.status,
                onOpenSettings = onOpenSettings,
            )
        }
        item {
            DeckModeTabs(modeTab = modeTab, onSelect = { modeTab = it })
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (modeTab == ReelModeTab.Search) "Search everything" else "Filter visible shelves") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (modeTab != ReelModeTab.Search) {
            item {
                MediaTabs(mediaTab = mediaTab, onSelect = { mediaTab = it })
            }
        }

        when (modeTab) {
            ReelModeTab.Local -> {
                if (state.library.isEmpty()) {
                    item {
                        MinimalSetupPrompt(
                            isBusy = state.isBusy,
                            onPickFolder = onPickFolder,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                } else {
                    if (state.continueWatching.isNotEmpty() && normalizedQuery.isBlank() && mediaTab != ReelMediaTab.Series) {
                        item {
                            ShelfSection(
                                title = "Continue Watching",
                                subtitle = "Resume instantly",
                                items = state.continueWatching,
                                onPlay = onPlay,
                                watchedUris = state.watchedUris,
                                resumePositionsMs = state.resumePositionsMs,
                                resumeProgressFractions = state.resumeProgressFractions,
                            )
                        }
                    }
                    if (mediaTab != ReelMediaTab.Movies && filteredSeriesGroups.isNotEmpty()) {
                        item {
                            SeriesGroupShelf(
                                title = "Local Series",
                                subtitle = "Grouped by show",
                                groups = filteredSeriesGroups,
                                onOpenSeries = onOpenSeries,
                            )
                        }
                        val localSeriesGenres = filteredSeriesGroups.flatMap { it.genreNames }.distinct().take(6)
                        localSeriesGenres.forEach { genre ->
                            val genreGroups = filteredSeriesGroups.filter { genre in it.genreNames }
                            if (genreGroups.isNotEmpty()) {
                                item {
                                    SeriesGroupShelf(
                                        title = genre,
                                        subtitle = "Series tagged ${genre.lowercase()}",
                                        groups = genreGroups,
                                        onOpenSeries = onOpenSeries,
                                    )
                                }
                            }
                        }
                    }
                    if (mediaTab != ReelMediaTab.Series && filteredMovieItems.isNotEmpty()) {
                        item {
                            ShelfSection(
                                title = "Local Movies",
                                subtitle = "Ready to play",
                                items = filteredMovieItems,
                                onPlay = onPlay,
                                watchedUris = state.watchedUris,
                                resumePositionsMs = state.resumePositionsMs,
                                resumeProgressFractions = state.resumeProgressFractions,
                            )
                        }
                        val localMovieGenres = filteredMovieItems.flatMap { it.genreNames }.distinct().take(6)
                        localMovieGenres.forEach { genre ->
                            val genreMovies = filteredMovieItems.filter { genre in it.genreNames }
                            if (genreMovies.isNotEmpty()) {
                                item {
                                    ShelfSection(
                                        title = genre,
                                        subtitle = "Movies tagged ${genre.lowercase()}",
                                        items = genreMovies,
                                        onPlay = onPlay,
                                        watchedUris = state.watchedUris,
                                        resumePositionsMs = state.resumePositionsMs,
                                        resumeProgressFractions = state.resumeProgressFractions,
                                    )
                                }
                            }
                        }
                    }
                    if (mediaTab == ReelMediaTab.All && miscItems.isNotEmpty()) {
                        item {
                            ShelfSection(
                                title = "Other Videos",
                                subtitle = "Everything else",
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

            ReelModeTab.Online -> {
                if (!state.enableOnlinePlayback) {
                    item {
                        EmptyOnlineState(
                            message = "Online shelves are switched off in Settings.",
                            rssCount = rssCount,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                } else {
                    if (mediaTab != ReelMediaTab.Movies) {
                        item {
                            DiscoverShelfSection(
                                title = "Online Series",
                                subtitle = buildOnlineSubtitle(rssCount),
                                items = filteredOnlineSeries,
                                onOpenOnline = onOpenOnline,
                            )
                        }
                    }
                    if (mediaTab != ReelMediaTab.Series) {
                        item {
                            DiscoverShelfSection(
                                title = "Online Movies",
                                subtitle = buildOnlineSubtitle(rssCount),
                                items = filteredOnlineMovies,
                                onOpenOnline = onOpenOnline,
                            )
                        }
                    }
                }
            }

            ReelModeTab.Search -> {
                val localResultSeries = filteredSeriesGroups
                val localResultMovies = filteredMovieItems
                item {
                    SearchResultsBlock(
                        localSeries = localResultSeries,
                        localMovies = localResultMovies,
                        onlineSeries = filteredOnlineSeries,
                        onlineMovies = filteredOnlineMovies,
                        onOpenSeries = onOpenSeries,
                        onPlay = onPlay,
                        onOpenOnline = onOpenOnline,
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
            if (highlight.isNotBlank()) SubtleMetaText(highlight)
        }
    }
}

@Composable
private fun DeckModeTabs(modeTab: ReelModeTab, onSelect: (ReelModeTab) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReelModeTab.entries.forEach { tab ->
            FilterChip(
                selected = modeTab == tab,
                onClick = { onSelect(tab) },
                label = { Text(tab.name) },
            )
        }
    }
}

@Composable
private fun MediaTabs(mediaTab: ReelMediaTab, onSelect: (ReelMediaTab) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReelMediaTab.entries.forEach { tab ->
            FilterChip(
                selected = mediaTab == tab,
                onClick = { onSelect(tab) },
                label = { Text(tab.name) },
            )
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
            if (isBusy) "PocketReel is gathering your library." else "Link your local folder first, then online options stay tucked away in Settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPickFolder, enabled = !isBusy) { Text("Pick folder") }
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        }
        if (isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun EmptyOnlineState(message: String, rssCount: Int, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Online is currently hidden", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Configured RSS feeds: $rssCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onOpenSettings) { Text("Open settings") }
    }
}

@Composable
private fun SearchResultsBlock(
    localSeries: List<ReelSeriesGroup>,
    localMovies: List<MediaItem>,
    onlineSeries: List<DiscoverItem>,
    onlineMovies: List<DiscoverItem>,
    onOpenSeries: (ReelSeriesGroup) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onOpenOnline: (DiscoverItem) -> Unit,
    watchedUris: Set<String>,
    resumePositionsMs: Map<String, Long>,
    resumeProgressFractions: Map<String, Float>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (localSeries.isNotEmpty()) {
            SeriesGroupShelf("Local Series Results", "Matches from your library", localSeries, onOpenSeries)
        }
        if (localMovies.isNotEmpty()) {
            ShelfSection(
                title = "Local Movie Results",
                subtitle = "Matches from your library",
                items = localMovies,
                onPlay = onPlay,
                watchedUris = watchedUris,
                resumePositionsMs = resumePositionsMs,
                resumeProgressFractions = resumeProgressFractions,
            )
        }
        if (onlineSeries.isNotEmpty()) {
            DiscoverShelfSection("Online Series Results", "TMDb browse matches", onlineSeries, onOpenOnline)
        }
        if (onlineMovies.isNotEmpty()) {
            DiscoverShelfSection("Online Movie Results", "TMDb browse matches", onlineMovies, onOpenOnline)
        }
        if (localSeries.isEmpty() && localMovies.isEmpty() && onlineSeries.isEmpty() && onlineMovies.isEmpty()) {
            Text("No matches yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onRssFeedsTextChange: (String) -> Unit,
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
                        title = "Enable online shelves",
                        subtitle = "Show online movie and series rows on the home screen.",
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
                        subtitle = "Useful when lawful multi-audio or season packs are already available.",
                        checked = state.preferSeriesPacks,
                        onCheckedChange = onPreferSeriesPacksChange,
                    )
                    OutlinedTextField(
                        value = state.rssFeedsText,
                        onValueChange = onRssFeedsTextChange,
                        label = { Text("RSS feeds") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        supportingText = { Text("One feed URL per line") },
                    )
                    SubtleMetaText("Active feeds: ${configuredRssFeeds(state.rssFeedsText).size}")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Metadata", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.tmdbLanguage,
                        onValueChange = onTmdbLanguageChange,
                        label = { Text("TMDb language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
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
                    OutlinedTextField(
                        value = state.preferredSubtitleLanguage,
                        onValueChange = onSubtitleLanguageChange,
                        label = { Text("Subtitle language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.preferredAudioLanguage,
                        onValueChange = onAudioLanguageChange,
                        label = { Text("Audio language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
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
private fun SeriesGroupShelf(title: String, subtitle: String, groups: List<ReelSeriesGroup>, onOpenSeries: (ReelSeriesGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfHeader(title = title, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(groups, key = { it.title }) { group ->
                Column(modifier = Modifier.width(220.dp).clickable { onOpenSeries(group) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosterArt(group.posterUrl, group.title, 220.dp, 310.dp)
                    Text(group.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    SubtleMetaText("${group.seasonCount} seasons • ${group.episodeCount} episodes")
                }
            }
        }
    }
}

@Composable
private fun ShelfSection(
    title: String,
    subtitle: String,
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    watchedUris: Set<String>,
    resumePositionsMs: Map<String, Long>,
    resumeProgressFractions: Map<String, Float>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfHeader(title = title, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(items, key = { it.id }) { item ->
                Column(modifier = Modifier.width(200.dp).clickable { onPlay(item) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosterArt(item.artworkUrl, item.title, 200.dp, 282.dp)
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val metaText = when {
                        item.documentUri in watchedUris -> "Watched"
                        (resumePositionsMs[item.documentUri] ?: 0L) > 0L -> "Resume ${formatResumeLabel(resumePositionsMs[item.documentUri] ?: 0L)}"
                        else -> item.releaseLabel ?: shelfTypeLabel(item)
                    }
                    SubtleMetaText(metaText)
                    val progress = resumeProgressFractions[item.documentUri]
                    if (progress != null && progress > 0f && item.documentUri !in watchedUris) {
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverShelfSection(
    title: String,
    subtitle: String,
    items: List<DiscoverItem>,
    onOpenOnline: (DiscoverItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShelfHeader(title = title, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(items, key = { "${it.mediaType}:${it.id}" }) { item ->
                Column(modifier = Modifier.width(200.dp).clickable { onOpenOnline(item) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        PosterArt(item.posterUrl, item.title, 200.dp, 282.dp)
                        Box(modifier = Modifier.matchParentSize().background(Color(0x22000000)))
                        Text(
                            text = "ONLINE",
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color(0xAA000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    SubtleMetaText(listOfNotNull(item.mediaType.uppercase(), item.releaseLabel).joinToString(" • "))
                }
            }
        }
    }
}

@Composable
private fun OnlineDetailScreen(item: DiscoverItem, rssFeedCount: Int, onlineEnabled: Boolean, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { StreamingBackHeader(onBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!item.backdropUrl.isNullOrBlank()) {
                    AsyncImage(model = item.backdropUrl, contentDescription = item.title, modifier = Modifier.fillMaxWidth().height(220.dp))
                } else {
                    PosterArt(item.posterUrl, item.title, width = 220.dp, height = 310.dp)
                }
                Text(item.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                SubtleMetaText(listOfNotNull(item.mediaType.uppercase(), item.releaseLabel).joinToString(" • "))
                item.overview?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Online actions", fontWeight = FontWeight.Bold)
                        Text(
                            if (onlineEnabled) "Online shelves are enabled. RSS feeds configured: $rssFeedCount. Connect your preferred lawful sources in Settings to light up the next action layer here."
                            else "Online shelves are currently switched off in Settings.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesDetailScreen(
    group: ReelSeriesGroup,
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
        if (tvId > 0 && selectedSeason >= 0) onEnsureSeasonEpisodeNumbers(tvId, selectedSeason)
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
        item { StreamingBackHeader(onBack) }
        item {
            NetflixStyleHero(group, featuredEpisode, onPlay = { featuredEpisode?.let(onPlay) })
        }
        item { DetailTabsRow(selectedTab = selectedTab, onSelect = { selectedTab = it }) }
        if (selectedTab == "Episodes") {
            if (group.seasons.size > 1) {
                item { SeasonTabsRow(group.seasons.keys.toList(), selectedSeason, onSelect = { selectedSeason = it }) }
            }
            if (missingEpisodes.isNotEmpty()) {
                item { Text("Missing: ${missingEpisodes.joinToString(", ")}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            item { MoreInfoSection(group, onOpenActor) }
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
private fun NetflixStyleHero(group: ReelSeriesGroup, featuredEpisode: MediaItem?, onPlay: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!group.backdropUrl.isNullOrBlank()) {
            AsyncImage(model = group.backdropUrl, contentDescription = group.title, modifier = Modifier.fillMaxWidth().height(220.dp))
        }
        Text("POCKETREEL SERIES", color = Color(0xFFE50914), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(group.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text("${group.seasonCount} Seasons • ${group.episodeCount} Episodes", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (group.genreNames.isNotEmpty()) MetadataPillRow(group.genreNames.take(4))
        group.overview?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Button(onClick = onPlay, modifier = Modifier.fillMaxWidth(), enabled = featuredEpisode != null) {
            Text(if (featuredEpisode != null) "Play" else "Unavailable")
        }
    }
}

@Composable
private fun DetailTabsRow(selectedTab: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf("Episodes", "Cast & More").forEach { tab ->
            Column(modifier = Modifier.clickable { onSelect(tab) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
private fun MoreInfoSection(group: ReelSeriesGroup, onOpenActor: (CastMember) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (group.castMembers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cast", fontWeight = FontWeight.Bold)
                CastCarousel(group.castMembers, onOpenActor)
            }
        }
        group.overview?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Overview", fontWeight = FontWeight.Bold)
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Column(modifier = Modifier.width(112.dp).clickable { onOpenActor(actor) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActorHeadshot(actor)
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
        item { StreamingBackHeader(onBack) }
        item { ActorHero(profile, actorName) }
        if (profile == null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text("Loading credits")
                }
            }
        } else {
            items(profile.credits, key = { "${it.mediaType}:${it.id}" }) { credit ->
                val requestKey = buildRequestKey(credit.mediaType, credit.id)
                val localMatch = findLocalMatchForCredit(library, credit)
                FilmographyRow(
                    credit = credit,
                    localMatch = localMatch,
                    requested = requestKey in requestedMediaKeys,
                    onPlay = onPlay,
                    onRequest = onRequest,
                )
            }
        }
    }
}

@Composable
private fun ActorHero(profile: ActorProfile?, fallbackName: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        if (profile?.profileUrl != null) {
            AsyncImage(model = profile.profileUrl, contentDescription = profile.name, modifier = Modifier.width(120.dp).height(160.dp))
        } else {
            Box(modifier = Modifier.width(120.dp).height(160.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text("No photo")
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile?.name ?: fallbackName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            profile?.knownForDepartment?.let { SubtleMetaText(it) }
            profile?.biography?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun FilmographyRow(
    credit: FilmCredit,
    localMatch: MediaItem?,
    requested: Boolean,
    onPlay: (MediaItem) -> Unit,
    onRequest: (FilmCredit) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        PosterArt(credit.posterUrl ?: credit.backdropUrl, credit.title, 90.dp, 135.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(credit.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            credit.releaseLabel?.let { SubtleMetaText(it) }
            credit.overview?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (localMatch != null) {
                    Button(onClick = { onPlay(localMatch) }) { Text("Play") }
                } else {
                    OutlinedButton(onClick = { onRequest(credit) }, enabled = !requested) {
                        Text(if (requested) "Requested" else "Request")
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    item: MediaItem,
    watched: Boolean,
    resumePositionMs: Long?,
    progressFraction: Float?,
    onPlay: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onPlay() }, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        PosterArt(item.backdropUrl ?: item.artworkUrl, item.title, 128.dp, 72.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(episodeHeadline(item), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val metaText = when {
                watched -> "Watched"
                (resumePositionMs ?: 0L) > 0L -> "Resume ${formatResumeLabel(resumePositionMs ?: 0L)}"
                else -> item.releaseLabel ?: shelfTypeLabel(item)
            }
            SubtleMetaText(metaText)
            item.overview?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (progressFraction != null && progressFraction > 0f && !watched) {
                LinearProgressIndicator(progress = progressFraction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ActorHeadshot(actor: CastMember) {
    if (actor.profileUrl != null) {
        AsyncImage(model = actor.profileUrl, contentDescription = actor.name, modifier = Modifier.width(112.dp).height(140.dp))
    } else {
        Box(modifier = Modifier.width(112.dp).height(140.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
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
            Text(contentDescription, modifier = Modifier.padding(10.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetadataPillRow(values: List<String>) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun ShelfHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SubtleMetaText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

private fun buildSeriesGroups(library: List<MediaItem>): List<ReelSeriesGroup> {
    return library.filter { it.mediaKind == MediaKind.EPISODE && !it.seriesTitle.isNullOrBlank() }
        .groupBy { it.seriesTitle.orEmpty() }
        .map { (title, episodes) ->
            val sorted = episodes.sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
            val artAnchor = sorted.firstOrNull { !it.artworkUrl.isNullOrBlank() } ?: sorted.first()
            val detailAnchor = sorted.firstOrNull { it.castMembers.isNotEmpty() || it.genreNames.isNotEmpty() } ?: artAnchor
            ReelSeriesGroup(
                title = title,
                posterUrl = artAnchor.artworkUrl,
                backdropUrl = artAnchor.backdropUrl,
                overview = sorted.firstNotNullOfOrNull { it.seriesOverview ?: it.overview },
                castMembers = detailAnchor.castMembers,
                genreNames = detailAnchor.genreNames,
                episodes = sorted,
            )
        }
        .sortedBy { it.title.lowercase() }
}

private fun pickFeaturedEpisode(group: ReelSeriesGroup, watchedUris: Set<String>, resumePositionsMs: Map<String, Long>): MediaItem? {
    val sorted = group.episodes.sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
    return sorted.firstOrNull { (resumePositionsMs[it.documentUri] ?: 0L) > 0L }
        ?: sorted.firstOrNull { it.documentUri !in watchedUris }
        ?: sorted.firstOrNull()
}

private fun computeMissingEpisodeLabels(episodes: List<MediaItem>, expectedNumbers: List<Int>?): List<String> {
    val localNumbers = episodes.mapNotNull { it.episodeNumber }.distinct().sorted()
    val sourceNumbers = expectedNumbers?.takeIf { it.isNotEmpty() } ?: localNumbers
    if (sourceNumbers.isEmpty()) return emptyList()
    val season = episodes.firstOrNull()?.seasonNumber ?: 0
    return sourceNumbers.filterNot { it in localNumbers }.map {
        if (season > 0) "S${season.toString().padStart(2, '0')}E${it.toString().padStart(2, '0')}" else "Episode $it"
    }
}

private fun configuredRssFeeds(raw: String): List<String> = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
private fun buildOnlineSubtitle(rssCount: Int): String = if (rssCount > 0) "Configured RSS feeds: $rssCount" else "Add RSS feeds in Settings"
private fun buildRequestKey(mediaType: String, tmdbId: Int): String = "$mediaType:$tmdbId"

private fun isDiscoverItemLocal(item: DiscoverItem, library: List<MediaItem>): Boolean {
    return library.any { local ->
        (local.tmdbId == item.id && local.tmdbMediaType == item.mediaType) || normalizeMatchTitle(local.title) == normalizeMatchTitle(item.title)
    }
}

private fun findLocalMatchForCredit(library: List<MediaItem>, credit: FilmCredit): MediaItem? {
    return library.firstOrNull { it.tmdbId == credit.id && it.tmdbMediaType == credit.mediaType }
        ?: library.firstOrNull { normalizeMatchTitle(it.title) == normalizeMatchTitle(credit.title) }
}

private fun normalizeMatchTitle(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]"), "")
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

private fun shelfTypeLabel(item: MediaItem): String = when (item.mediaKind) {
    MediaKind.MOVIE -> "Movie"
    MediaKind.EPISODE -> listOfNotNull(item.seriesTitle, item.releaseLabel).joinToString(" • ").ifBlank { "Episode" }
    MediaKind.VIDEO -> item.mimeType ?: "Video"
}

private fun formatResumeLabel(positionMs: Long): String {
    val totalSeconds = positionMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}
