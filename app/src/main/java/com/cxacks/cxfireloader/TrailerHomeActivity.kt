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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.captainxack.pocketreel.media.MediaItem
import com.captainxack.pocketreel.media.MediaKind
import com.captainxack.pocketreel.ui.theme.PocketReelTheme

class TrailerHomeActivity : ComponentActivity() {
    private val vm by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketReelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TrailerDeckScreen(
                        vm = vm,
                        onPickTree = ::persistTreeAccess,
                        onPlay = ::launchPlayer,
                        onPlayTrailer = ::launchTrailer,
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

    private fun launchTrailer(trailerUri: String, title: String) {
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, trailerUri)
            putExtra(PlayerActivity.EXTRA_TITLE, "$title Trailer")
            putExtra(PlayerActivity.EXTRA_AUTOPLAY_STREAK, 0)
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

private enum class TrailerScreen { Home, Settings }

data class TrailerSeriesGroup(
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val episodes: List<MediaItem>,
) {
    val trailerUrl: String?
        get() = episodes.firstOrNull { !it.trailerUrl.isNullOrBlank() }?.trailerUrl
}

@Composable
private fun TrailerDeckScreen(
    vm: MainViewModel,
    onPickTree: (Uri) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onPlayTrailer: (String, String) -> Unit,
) {
    val state = vm.uiState.value
    var activeScreen by rememberSaveable { mutableStateOf(TrailerScreen.Home) }
    var selectedSeriesTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(onPickTree)
    }

    val seriesGroups = remember(state.library) { buildTrailerSeriesGroups(state.library) }
    val selectedGroup = selectedSeriesTitle?.let { title -> seriesGroups.firstOrNull { it.title == title } }
    val movieItems = remember(state.library) {
        state.library.filter { it.mediaKind == MediaKind.MOVIE }.sortedBy { it.title.lowercase() }
    }

    when {
        selectedGroup != null -> SeriesTrailerDetailScreen(
            group = selectedGroup,
            watchedUris = state.watchedUris,
            resumePositionsMs = state.resumePositionsMs,
            resumeProgressFractions = state.resumeProgressFractions,
            onBack = { selectedSeriesTitle = null },
            onPlay = onPlay,
            onPlayTrailer = onPlayTrailer,
        )

        activeScreen == TrailerScreen.Settings -> TrailerSettingsScreen(
            state = state,
            onBack = { activeScreen = TrailerScreen.Home },
            onPickFolder = { launcher.launch(null) },
            onRescan = vm::rescanLibrary,
            onRefreshArtwork = vm::fetchArtwork,
            onTmdbLanguageChange = vm::updateTmdbLanguage,
            onSubtitleLanguageChange = vm::updatePreferredSubtitleLanguage,
            onAudioLanguageChange = vm::updatePreferredAudioLanguage,
            onEnableOnlinePlaybackChange = vm::updateEnableOnlinePlayback,
            onAllDebridApiKeyChange = vm::updateAllDebridApiKey,
            onSave = vm::saveSettings,
        )

        else -> TrailerHomeScreen(
            state = state,
            seriesGroups = seriesGroups,
            movieItems = movieItems,
            onPickFolder = { launcher.launch(null) },
            onOpenSettings = { activeScreen = TrailerScreen.Settings },
            onOpenSeries = { selectedSeriesTitle = it.title },
            onPlay = onPlay,
        )
    }
}

@Composable
private fun TrailerHomeScreen(
    state: UiState,
    seriesGroups: List<TrailerSeriesGroup>,
    movieItems: List<MediaItem>,
    onPickFolder: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSeries: (TrailerSeriesGroup) -> Unit,
    onPlay: (MediaItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
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
                        Text("PocketReel", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onOpenSettings) { Text("Settings") }
                    }
                    Text("Trailer-first detail pages", style = MaterialTheme.typography.bodyLarge)
                    if (state.status.isNotBlank()) {
                        Text(state.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (state.library.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose a folder to begin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("PocketReel will scan your videos, match metadata, and expose visible trailer buttons.")
                    Button(onClick = onPickFolder) { Text("Pick folder") }
                    if (state.isBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            if (state.continueWatching.isNotEmpty()) {
                item {
                    MediaShelf(
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
                item { SeriesShelf(groups = seriesGroups, onOpenSeries = onOpenSeries) }
            }
            if (movieItems.isNotEmpty()) {
                item {
                    MediaShelf(
                        title = "Local Movies",
                        items = movieItems,
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
private fun SeriesShelf(groups: List<TrailerSeriesGroup>, onOpenSeries: (TrailerSeriesGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local Series", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(groups, key = { it.title }) { group ->
                Column(
                    modifier = Modifier.width(220.dp).clickable { onOpenSeries(group) },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PosterArt(group.posterUrl, group.title, 220.dp, 310.dp)
                    Text(group.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${group.episodes.map { it.seasonNumber ?: 0 }.distinct().size} seasons • ${group.episodes.size} episodes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!group.trailerUrl.isNullOrBlank()) {
                        Text("Trailer ready", color = Color(0xFF62B5FF), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaShelf(
    title: String,
    items: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    watchedUris: Set<String>,
    resumePositionsMs: Map<String, Long>,
    resumeProgressFractions: Map<String, Float>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 6.dp)) {
            items(items, key = { it.id }) { item ->
                Column(
                    modifier = Modifier.width(200.dp).clickable { onPlay(item) },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PosterArt(item.artworkUrl, item.title, 200.dp, 282.dp)
                    Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val metaText = when {
                        item.documentUri in watchedUris -> "Watched"
                        (resumePositionsMs[item.documentUri] ?: 0L) > 0L -> "Resume ${formatResumeLabel(resumePositionsMs[item.documentUri] ?: 0L)}"
                        (resumeProgressFractions[item.documentUri] ?: 0f) > 0f -> "Resume"
                        else -> item.releaseLabel ?: item.mediaKind.name.lowercase().replaceFirstChar { it.titlecase() }
                    }
                    Text(metaText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SeriesTrailerDetailScreen(
    group: TrailerSeriesGroup,
    watchedUris: Set<String>,
    resumePositionsMs: Map<String, Long>,
    resumeProgressFractions: Map<String, Float>,
    onBack: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onPlayTrailer: (String, String) -> Unit,
) {
    var detailTab by rememberSaveable { mutableStateOf("Episodes") }
    val leadEpisode = group.episodes.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("Back") }
        }
        item {
            AsyncImage(
                model = group.backdropUrl ?: group.posterUrl,
                contentDescription = group.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("POCKETREEL SERIES", color = Color(0xFFE50914), fontWeight = FontWeight.Bold)
                Text(group.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${group.episodes.map { it.seasonNumber ?: 0 }.distinct().size} Seasons • ${group.episodes.size} Episodes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(group.overview ?: leadEpisode?.seriesOverview ?: leadEpisode?.overview ?: "No synopsis yet")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { leadEpisode?.let(onPlay) }, enabled = leadEpisode != null) {
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = { group.trailerUrl?.let { onPlayTrailer(it, group.title) } },
                        enabled = !group.trailerUrl.isNullOrBlank(),
                    ) {
                        Text(if (!group.trailerUrl.isNullOrBlank()) "Trailer" else "No trailer")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { detailTab = "Episodes" }) { Text("Episodes") }
                TextButton(onClick = { detailTab = "Cast" }) { Text("Cast & More") }
            }
        }
        if (detailTab == "Episodes") {
            items(group.episodes, key = { it.id }) { episode ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PosterArt(episode.artworkUrl ?: group.posterUrl, episode.title, 110.dp, 70.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(episodeHeadline(episode), fontWeight = FontWeight.Bold)
                            Text(
                                episode.episodeTitle ?: episode.overview ?: "Episode ready to play",
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val metaText = when {
                                episode.documentUri in watchedUris -> "Watched"
                                (resumePositionsMs[episode.documentUri] ?: 0L) > 0L -> "Resume ${formatResumeLabel(resumePositionsMs[episode.documentUri] ?: 0L)}"
                                (resumeProgressFractions[episode.documentUri] ?: 0f) > 0f -> "Resume"
                                else -> episode.releaseLabel ?: "Episode"
                            }
                            Text(metaText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { onPlay(episode) }) { Text("Play") }
                    }
                }
            }
        } else {
            item {
                val castNames = group.episodes.flatMap { it.castNames }.distinct().take(12)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cast", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (castNames.isEmpty()) {
                        Text("No cast metadata yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        castNames.forEach { name ->
                            Text(name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerSettingsScreen(
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
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack) { Text("Back") }
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("This build focuses on visible trailer actions and stable playback.")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(friendlyFolderLabel(state.mediaTreeUri))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Text("Metadata", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.tmdbLanguage,
                        onValueChange = onTmdbLanguageChange,
                        label = { Text("TMDb language") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
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
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Online", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text("Enable online shelves", fontWeight = FontWeight.Bold)
                            Text("Turns on TMDb-backed metadata and online shelves.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.enableOnlinePlayback, onCheckedChange = onEnableOnlinePlaybackChange)
                    }
                    OutlinedTextField(
                        value = state.allDebridApiKey,
                        onValueChange = onAllDebridApiKeyChange,
                        label = { Text("AllDebrid API key") },
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
private fun PosterArt(url: String?, title: String, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = url,
        contentDescription = title,
        modifier = Modifier.width(width).height(height),
    )
}

private fun buildTrailerSeriesGroups(items: List<MediaItem>): List<TrailerSeriesGroup> {
    return items
        .filter { it.mediaKind == MediaKind.EPISODE && !it.seriesTitle.isNullOrBlank() }
        .groupBy { it.seriesTitle.orEmpty() }
        .map { (title, episodes) ->
            val sortedEpisodes = episodes.sortedWith(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }, { it.title.lowercase() }))
            val first = sortedEpisodes.firstOrNull()
            TrailerSeriesGroup(
                title = title,
                posterUrl = first?.artworkUrl,
                backdropUrl = first?.backdropUrl,
                overview = first?.seriesOverview ?: first?.overview,
                episodes = sortedEpisodes,
            )
        }
        .sortedBy { it.title.lowercase() }
}

private fun episodeHeadline(item: MediaItem): String {
    val season = item.seasonNumber
    val episode = item.episodeNumber
    val code = if (season != null && episode != null) {
        "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
    } else null
    val name = item.episodeTitle?.takeIf { it.isNotBlank() }
    return listOfNotNull(code, name ?: item.title).joinToString(" • ")
}

private fun friendlyFolderLabel(value: Any?): String = value?.toString()?.takeIf { it.isNotBlank() } ?: "No folder selected"

private fun formatResumeLabel(positionMs: Long): String {
    val totalMinutes = positionMs / 60000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
