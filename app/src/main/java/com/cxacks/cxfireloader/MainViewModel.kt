package com.captainxack.pocketreel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captainxack.pocketreel.data.PlaybackStateStore
import com.captainxack.pocketreel.data.RequestStore
import com.captainxack.pocketreel.data.SettingsStore
import com.captainxack.pocketreel.data.WantedRequest
import com.captainxack.pocketreel.media.ActorProfile
import com.captainxack.pocketreel.media.DiscoverItem
import com.captainxack.pocketreel.media.LocalMediaScanner
import com.captainxack.pocketreel.media.MediaItem
import com.captainxack.pocketreel.media.MediaKind
import com.captainxack.pocketreel.media.TmdbRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class UiState(
    val mediaTreeUri: String? = null,
    val tmdbLanguage: String = SettingsStore.DEFAULT_TMDB_LANGUAGE,
    val preferredSubtitleLanguage: String = SettingsStore.DEFAULT_SUBTITLE_LANGUAGE,
    val preferredAudioLanguage: String = SettingsStore.DEFAULT_AUDIO_LANGUAGE,
    val enableOnlinePlayback: Boolean = false,
    val allDebridApiKey: String = "",
    val preferredOnlineAudioMode: String = SettingsStore.DEFAULT_ONLINE_AUDIO_MODE,
    val preferSeriesPacks: Boolean = true,
    val rssFeedsText: String = "",
    val autoFetchArtworkOnScan: Boolean = true,
    val library: List<MediaItem> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    val watchedUris: Set<String> = emptySet(),
    val resumePositionsMs: Map<String, Long> = emptyMap(),
    val resumeProgressFractions: Map<String, Float> = emptyMap(),
    val requestedMediaKeys: Set<String> = emptySet(),
    val wantedRequests: List<WantedRequest> = emptyList(),
    val discoverItems: List<DiscoverItem> = emptyList(),
    val status: String = "Choose your media folder to start building the library",
    val isBusy: Boolean = false,
    val tmdbConfigured: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = SettingsStore(application)
    private val playbackStateStore = PlaybackStateStore(application)
    private val requestStore = RequestStore(application)
    private val tmdbRepository = TmdbRepository()

    val actorProfiles = mutableStateMapOf<Int, ActorProfile?>()
    val seasonEpisodeNumbers = mutableStateMapOf<String, List<Int>>()

    var uiState = androidx.compose.runtime.mutableStateOf(
        UiState(
            mediaTreeUri = settingsStore.getMediaTreeUri(),
            tmdbLanguage = settingsStore.getTmdbLanguage(),
            preferredSubtitleLanguage = settingsStore.getPreferredSubtitleLanguage(),
            preferredAudioLanguage = settingsStore.getPreferredAudioLanguage(),
            enableOnlinePlayback = settingsStore.getEnableOnlinePlayback(),
            allDebridApiKey = settingsStore.getAllDebridApiKey(),
            preferredOnlineAudioMode = settingsStore.getPreferredOnlineAudioMode(),
            preferSeriesPacks = settingsStore.getPreferSeriesPacks(),
            rssFeedsText = settingsStore.getRssFeedsText(),
            autoFetchArtworkOnScan = settingsStore.getAutoFetchArtworkOnScan(),
            requestedMediaKeys = requestStore.getRequestedMediaKeys(),
            wantedRequests = requestStore.getWantedRequests(),
            discoverItems = emptyList(),
            tmdbConfigured = tmdbRepository.isConfigured(),
        ),
    )
        private set

    init {
        rescanLibrary()
        refreshDiscover()
    }

    fun onTreeSelected(uri: Uri) {
        settingsStore.setMediaTreeUri(uri.toString())
        uiState.value = uiState.value.copy(mediaTreeUri = uri.toString(), status = "Media folder linked")
        rescanLibrary()
    }

    fun updateTmdbLanguage(value: String) {
        uiState.value = uiState.value.copy(tmdbLanguage = value)
    }

    fun updatePreferredSubtitleLanguage(value: String) {
        uiState.value = uiState.value.copy(preferredSubtitleLanguage = value)
    }

    fun updatePreferredAudioLanguage(value: String) {
        uiState.value = uiState.value.copy(preferredAudioLanguage = value)
    }

    fun updateEnableOnlinePlayback(value: Boolean) {
        uiState.value = uiState.value.copy(enableOnlinePlayback = value)
    }

    fun updateAllDebridApiKey(value: String) {
        uiState.value = uiState.value.copy(allDebridApiKey = value)
    }

    fun updatePreferredOnlineAudioMode(value: String) {
        uiState.value = uiState.value.copy(preferredOnlineAudioMode = value)
    }

    fun updatePreferSeriesPacks(value: Boolean) {
        uiState.value = uiState.value.copy(preferSeriesPacks = value)
    }

    fun updateRssFeedsText(value: String) {
        uiState.value = uiState.value.copy(rssFeedsText = value)
    }

    fun updateAutoFetchArtworkOnScan(value: Boolean) {
        uiState.value = uiState.value.copy(autoFetchArtworkOnScan = value)
    }

    fun saveSettings() {
        settingsStore.setTmdbLanguage(uiState.value.tmdbLanguage)
        settingsStore.setPreferredSubtitleLanguage(uiState.value.preferredSubtitleLanguage)
        settingsStore.setPreferredAudioLanguage(uiState.value.preferredAudioLanguage)
        settingsStore.setEnableOnlinePlayback(uiState.value.enableOnlinePlayback)
        settingsStore.setAllDebridApiKey(uiState.value.allDebridApiKey)
        settingsStore.setPreferredOnlineAudioMode(uiState.value.preferredOnlineAudioMode)
        settingsStore.setPreferSeriesPacks(uiState.value.preferSeriesPacks)
        settingsStore.setRssFeedsText(uiState.value.rssFeedsText)
        settingsStore.setAutoFetchArtworkOnScan(uiState.value.autoFetchArtworkOnScan)
        uiState.value = uiState.value.copy(status = "Settings saved")
        refreshDiscover()
        rescanLibrary()
    }

    fun requestMedia(mediaType: String, tmdbId: Int, title: String) {
        val key = buildRequestKey(mediaType, tmdbId)
        if (key in uiState.value.requestedMediaKeys) return
        requestStore.addWantedRequest(mediaType, tmdbId, title)
        uiState.value = uiState.value.copy(
            requestedMediaKeys = requestStore.getRequestedMediaKeys(),
            wantedRequests = requestStore.getWantedRequests(),
            status = "Added $title to Wanted",
        )
    }

    fun ensureActorProfile(actorId: Int) {
        if (actorProfiles.containsKey(actorId) || !tmdbRepository.isConfigured()) return
        actorProfiles[actorId] = null
        viewModelScope.launch(Dispatchers.IO) {
            val profile = tmdbRepository.fetchActorProfile(actorId, uiState.value.tmdbLanguage)
            actorProfiles[actorId] = profile
        }
    }

    fun ensureSeasonEpisodeNumbers(tvId: Int, seasonNumber: Int) {
        if (tvId <= 0 || seasonNumber < 0 || !tmdbRepository.isConfigured()) return
        val key = buildSeasonKey(tvId, seasonNumber)
        if (seasonEpisodeNumbers.containsKey(key)) return
        seasonEpisodeNumbers[key] = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            val numbers = tmdbRepository.fetchSeasonEpisodeNumbers(tvId, seasonNumber, uiState.value.tmdbLanguage)
            seasonEpisodeNumbers[key] = numbers
        }
    }

    fun refreshPlaybackState() {
        val currentLibrary = uiState.value.library
        if (currentLibrary.isEmpty()) return
        applyPlaybackDecorations(
            library = currentLibrary,
            status = uiState.value.status,
            isBusy = uiState.value.isBusy,
            tmdbConfigured = uiState.value.tmdbConfigured,
        )
    }

    fun rescanLibrary() {
        val app = getApplication<Application>()
        val snapshot = uiState.value
        val uri = snapshot.mediaTreeUri
        if (uri.isNullOrBlank()) {
            uiState.value = snapshot.copy(
                library = emptyList(),
                continueWatching = emptyList(),
                watchedUris = emptySet(),
                resumePositionsMs = emptyMap(),
                resumeProgressFractions = emptyMap(),
                status = "Choose your media folder to start building the library",
                isBusy = false,
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            postStatus("Scanning your local library", true)
            val scanned = LocalMediaScanner.scan(app, uri)
            val enriched = if (
                tmdbRepository.isConfigured() &&
                scanned.isNotEmpty() &&
                uiState.value.autoFetchArtworkOnScan
            ) {
                tmdbRepository.enrich(scanned, uiState.value.tmdbLanguage)
            } else {
                scanned
            }
            val shows = enriched.count { it.mediaKind == MediaKind.EPISODE }
            val movies = enriched.count { it.mediaKind == MediaKind.MOVIE }
            val artwork = enriched.count { !it.artworkUrl.isNullOrBlank() }
            val baseStatus = when {
                enriched.isEmpty() -> "No videos found in that folder yet"
                tmdbRepository.isConfigured() && uiState.value.autoFetchArtworkOnScan -> "Found ${enriched.size} titles: ${movies} movies, ${shows} episodes, artwork on $artwork"
                tmdbRepository.isConfigured() -> "Found ${enriched.size} titles without auto artwork. Open settings or refresh manually when you want posters."
                else -> "Found ${enriched.size} titles ready to play"
            }
            applyPlaybackDecorations(
                library = enriched,
                status = baseStatus,
                isBusy = false,
                tmdbConfigured = tmdbRepository.isConfigured(),
            )
        }
    }

    fun fetchArtwork() {
        if (!tmdbRepository.isConfigured()) {
            uiState.value = uiState.value.copy(status = "Artwork provider is offline in this build")
            return
        }
        if (uiState.value.library.isEmpty()) {
            uiState.value = uiState.value.copy(status = "Scan your library first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            postStatus("Refreshing posters and metadata", true)
            val enriched = tmdbRepository.enrich(uiState.value.library, uiState.value.tmdbLanguage)
            val withArtwork = enriched.count { !it.artworkUrl.isNullOrBlank() }
            applyPlaybackDecorations(
                library = enriched,
                status = "Artwork refresh finished. Matched $withArtwork of ${enriched.size} titles",
                isBusy = false,
                tmdbConfigured = true,
            )
        }
    }

    private fun refreshDiscover() {
        if (!tmdbRepository.isConfigured()) return
        viewModelScope.launch(Dispatchers.IO) {
            val discover = tmdbRepository.fetchDiscoverItems(uiState.value.tmdbLanguage)
            uiState.value = uiState.value.copy(discoverItems = discover)
        }
    }

    private fun applyPlaybackDecorations(
        library: List<MediaItem>,
        status: String,
        isBusy: Boolean,
        tmdbConfigured: Boolean,
    ) {
        val playbackStates = library.associateBy(
            keySelector = { it.documentUri },
            valueTransform = { playbackStateStore.getState(it.documentUri) },
        )

        val watchedUris = playbackStates
            .filterValues { it.watched }
            .keys

        val resumePositions = playbackStates
            .mapNotNull { (uri, state) -> state.resumePositionMs.takeIf { it > 0L }?.let { uri to it } }
            .toMap()

        val resumeFractions = playbackStates
            .mapNotNull { (uri, state) ->
                if (state.resumePositionMs > 0L && state.durationMs > 0L) {
                    uri to (state.resumePositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
            }
            .toMap()

        val continueWatching = library
            .filter { item ->
                val state = playbackStates[item.documentUri]
                state != null && !state.watched && state.resumePositionMs >= PlaybackStateStore.MIN_RESUME_MS
            }
            .sortedByDescending { playbackStates[it.documentUri]?.updatedAt ?: 0L }
            .take(20)

        uiState.value = uiState.value.copy(
            library = library,
            continueWatching = continueWatching,
            watchedUris = watchedUris,
            resumePositionsMs = resumePositions,
            resumeProgressFractions = resumeFractions,
            requestedMediaKeys = requestStore.getRequestedMediaKeys(),
            wantedRequests = requestStore.getWantedRequests(),
            status = status,
            isBusy = isBusy,
            tmdbConfigured = tmdbConfigured,
        )
    }

    private fun postStatus(message: String, busy: Boolean) {
        uiState.value = uiState.value.copy(status = message, isBusy = busy)
    }

    private fun buildRequestKey(mediaType: String, tmdbId: Int): String = "$mediaType:$tmdbId"

    private fun buildSeasonKey(tvId: Int, seasonNumber: Int): String = "$tvId:$seasonNumber"
}
