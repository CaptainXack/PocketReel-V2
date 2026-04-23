package com.captainxack.pocketreel.media

import com.captainxack.pocketreel.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class TmdbRepository(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun isConfigured(): Boolean = BuildConfig.TMDB_API_KEY.isNotBlank() || BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank()

    fun enrich(items: List<MediaItem>, language: String): List<MediaItem> {
        if (!isConfigured()) return items
        return items.map { item -> findBestMatch(item, language) ?: item }
    }

    fun fetchDiscoverItems(language: String): List<DiscoverItem> {
        if (!isConfigured()) return emptyList()
        val buckets = listOf(
            fetchDiscoverPage(endpoint = "https://api.themoviedb.org/3/discover/movie", language = language, mediaType = "movie", page = 1),
            fetchDiscoverPage(endpoint = "https://api.themoviedb.org/3/discover/movie", language = language, mediaType = "movie", page = 2),
            fetchDiscoverPage(endpoint = "https://api.themoviedb.org/3/discover/movie", language = language, mediaType = "movie", page = 3),
            fetchDiscoverPage(endpoint = "https://api.themoviedb.org/3/discover/tv", language = language, mediaType = "tv", page = 1),
            fetchDiscoverPage(endpoint = "https://api.themoviedb.org/3/discover/tv", language = language, mediaType = "tv", page = 2),
            fetchDiscoverPage(endpoint = "https://api.themoviedb.org/3/discover/tv", language = language, mediaType = "tv", page = 3),
            fetchTrendingPage(endpoint = "https://api.themoviedb.org/3/trending/movie/week", language = language, mediaType = "movie", page = 1),
            fetchTrendingPage(endpoint = "https://api.themoviedb.org/3/trending/movie/week", language = language, mediaType = "movie", page = 2),
            fetchTrendingPage(endpoint = "https://api.themoviedb.org/3/trending/tv/week", language = language, mediaType = "tv", page = 1),
            fetchTrendingPage(endpoint = "https://api.themoviedb.org/3/trending/tv/week", language = language, mediaType = "tv", page = 2),
        )
        return buckets
            .flatten()
            .distinctBy { "${it.mediaType}:${it.id}" }
            .take(160)
    }

    fun fetchSeasonEpisodeNumbers(tvId: Int, seasonNumber: Int, language: String): List<Int> {
        if (!isConfigured() || seasonNumber < 0) return emptyList()
        val endpoint = "https://api.themoviedb.org/3/tv/$tvId/season/$seasonNumber"
        val root = requestJson(endpoint, language = language, query = null) ?: return emptyList()
        val episodes = root.optJSONArray("episodes") ?: return emptyList()
        val numbers = mutableListOf<Int>()
        for (i in 0 until episodes.length()) {
            val episodeNumber = episodes.optJSONObject(i)?.optInt("episode_number") ?: 0
            if (episodeNumber > 0) numbers.add(episodeNumber)
        }
        return numbers.distinct().sorted()
    }

    private fun fetchDiscoverPage(endpoint: String, language: String, mediaType: String, page: Int): List<DiscoverItem> {
        val root = requestJson(endpoint, language = language, query = null, extraParams = mapOf("page" to page.toString(), "sort_by" to "popularity.desc")) ?: return emptyList()
        return parseDiscoverItems(root, mediaType)
    }

    private fun fetchTrendingPage(endpoint: String, language: String, mediaType: String, page: Int): List<DiscoverItem> {
        val root = requestJson(endpoint, language = language, query = null, extraParams = mapOf("page" to page.toString())) ?: return emptyList()
        return parseDiscoverItems(root, mediaType)
    }

    private fun parseDiscoverItems(root: JSONObject, mediaType: String): List<DiscoverItem> {
        val array = root.optJSONArray("results") ?: return emptyList()
        val items = mutableListOf<DiscoverItem>()
        for (i in 0 until minOf(array.length(), 20)) {
            val entry = array.optJSONObject(i) ?: continue
            val id = entry.optInt("id")
            if (id == 0) continue
            val rawTitle = entry.optString("title")
            val fallbackTitle = entry.optString("name")
            val title = rawTitle.ifBlank { fallbackTitle }
            if (title.isBlank()) continue
            items.add(
                DiscoverItem(
                    id = id,
                    mediaType = mediaType,
                    title = title,
                    posterUrl = posterUrl(entry.optString("poster_path").ifBlank { null }),
                    backdropUrl = backdropUrl(entry.optString("backdrop_path").ifBlank { null }),
                    releaseLabel = entry.optString("release_date").ifBlank { entry.optString("first_air_date").ifBlank { null } },
                    overview = entry.optString("overview").ifBlank { null },
                    trailerUrl = null,
                ),
            )
        }
        return items
    }

    fun fetchActorProfile(actorId: Int, language: String): ActorProfile? {
        if (!isConfigured()) return null
        val endpoint = "https://api.themoviedb.org/3/person/$actorId"
        val root = requestJson(endpoint, language = language, query = null, appendToResponse = "combined_credits") ?: return null
        val creditsArray = root.optJSONObject("combined_credits")?.optJSONArray("cast")
        val rawCredits = mutableListOf<FilmCredit>()
        if (creditsArray != null) {
            for (i in 0 until creditsArray.length()) {
                val entry = creditsArray.optJSONObject(i) ?: continue
                val mediaType = entry.optString("media_type").ifBlank { "" }
                if (mediaType != "movie" && mediaType != "tv") continue
                val id = entry.optInt("id")
                if (id == 0) continue
                val title = entry.optString("title").ifBlank { entry.optString("name") }.ifBlank { "" }
                if (title.isBlank()) continue
                rawCredits.add(
                    FilmCredit(
                        id = id,
                        mediaType = mediaType,
                        title = title,
                        posterUrl = posterUrl(entry.optString("poster_path").ifBlank { null }),
                        backdropUrl = backdropUrl(entry.optString("backdrop_path").ifBlank { null }),
                        releaseLabel = entry.optString("release_date").ifBlank { entry.optString("first_air_date").ifBlank { null } },
                        overview = entry.optString("overview").ifBlank { null },
                    ),
                )
            }
        }
        val credits = rawCredits
            .distinctBy { "${it.mediaType}:${it.id}" }
            .sortedByDescending { it.releaseLabel ?: "" }
            .take(30)

        return ActorProfile(
            id = actorId,
            name = root.optString("name").ifBlank { "Unknown cast member" },
            profileUrl = profileUrl(root.optString("profile_path").ifBlank { null }),
            biography = root.optString("biography").ifBlank { null },
            knownForDepartment = root.optString("known_for_department").ifBlank { null },
            credits = credits,
        )
    }

    private fun findBestMatch(item: MediaItem, language: String): MediaItem? {
        val querySeed = item.normalizedTitle ?: item.originalFileName ?: item.title
        val parsed = parseQuery(querySeed)
        if (parsed.query.isBlank()) return null

        return if (parsed.isEpisode) {
            searchTv(parsed.query, language)?.let { result ->
                val showDetails = result.id?.let { fetchShowDetails(it, language) }
                val episodeDetails = if (item.seasonNumber != null && item.episodeNumber != null && result.id != null) {
                    fetchEpisodeDetails(result.id, item.seasonNumber, item.episodeNumber, language)
                } else {
                    null
                }

                item.copy(
                    title = buildEpisodeShellTitle(result.title, parsed.seasonEpisode, item.title),
                    artworkUrl = posterUrl(result.posterPath),
                    backdropUrl = backdropUrl(result.backdropPath),
                    trailerUrl = showDetails?.trailerUrl ?: item.trailerUrl,
                    overview = episodeDetails?.overview ?: item.overview,
                    seriesOverview = result.overview ?: item.seriesOverview,
                    episodeTitle = episodeDetails?.name?.ifBlank { item.episodeTitle } ?: item.episodeTitle,
                    releaseLabel = buildEpisodeLabel(parsed.seasonEpisode, episodeDetails?.airDate ?: result.releaseLabel),
                    matchSource = "tmdb-tv",
                    seriesTitle = result.title,
                    tmdbId = result.id,
                    tmdbMediaType = "tv",
                    castNames = showDetails?.castNames ?: item.castNames,
                    castMembers = showDetails?.castMembers ?: item.castMembers,
                    genreNames = showDetails?.genreNames ?: item.genreNames,
                )
            } ?: fallbackSearch(item, parsed.query, language)
        } else {
            searchMovie(parsed.query, language)?.let { result ->
                val movieDetails = result.id?.let { fetchMovieDetails(it, language) }
                item.copy(
                    title = result.title,
                    artworkUrl = posterUrl(result.posterPath),
                    backdropUrl = backdropUrl(result.backdropPath),
                    trailerUrl = movieDetails?.trailerUrl ?: item.trailerUrl,
                    overview = movieDetails?.overview ?: result.overview ?: item.overview,
                    releaseLabel = result.releaseLabel,
                    matchSource = "tmdb-movie",
                    tmdbId = result.id,
                    tmdbMediaType = "movie",
                    castNames = movieDetails?.castNames ?: item.castNames,
                    castMembers = movieDetails?.castMembers ?: item.castMembers,
                    genreNames = movieDetails?.genreNames ?: item.genreNames,
                )
            } ?: searchTv(parsed.query, language)?.let { result ->
                val showDetails = result.id?.let { fetchShowDetails(it, language) }
                item.copy(
                    title = result.title,
                    artworkUrl = posterUrl(result.posterPath),
                    backdropUrl = backdropUrl(result.backdropPath),
                    trailerUrl = showDetails?.trailerUrl ?: item.trailerUrl,
                    overview = showDetails?.overview ?: result.overview ?: item.overview,
                    releaseLabel = result.releaseLabel,
                    matchSource = "tmdb-tv",
                    seriesTitle = result.title,
                    seriesOverview = showDetails?.overview ?: result.overview,
                    tmdbId = result.id,
                    tmdbMediaType = "tv",
                    castNames = showDetails?.castNames ?: item.castNames,
                    castMembers = showDetails?.castMembers ?: item.castMembers,
                    genreNames = showDetails?.genreNames ?: item.genreNames,
                )
            } ?: fallbackSearch(item, parsed.query, language)
        }
    }

    private fun fetchMovieDetails(movieId: Int, language: String): DetailPayload? {
        val endpoint = "https://api.themoviedb.org/3/movie/$movieId"
        val root = requestJson(endpoint, language = language, query = null, appendToResponse = "credits,videos") ?: return null
        return DetailPayload(
            overview = root.optString("overview").ifBlank { null },
            trailerUrl = extractTrailerUrl(root.optJSONObject("videos")?.optJSONArray("results")),
            castNames = extractCastNames(root),
            castMembers = extractCastMembers(root),
            genreNames = extractGenres(root),
        )
    }

    private fun fetchShowDetails(tvId: Int, language: String): DetailPayload? {
        val endpoint = "https://api.themoviedb.org/3/tv/$tvId"
        val root = requestJson(endpoint, language = language, query = null, appendToResponse = "credits,videos") ?: return null
        return DetailPayload(
            overview = root.optString("overview").ifBlank { null },
            trailerUrl = extractTrailerUrl(root.optJSONObject("videos")?.optJSONArray("results")),
            castNames = extractCastNames(root),
            castMembers = extractCastMembers(root),
            genreNames = extractGenres(root),
        )
    }

    private fun fetchEpisodeDetails(tvId: Int, seasonNumber: Int, episodeNumber: Int, language: String): EpisodeDetails? {
        val endpoint = "https://api.themoviedb.org/3/tv/$tvId/season/$seasonNumber/episode/$episodeNumber"
        val root = requestJson(endpoint, language = language, query = null) ?: return null
        return EpisodeDetails(
            name = root.optString("name").ifBlank { null },
            overview = root.optString("overview").ifBlank { null },
            airDate = root.optString("air_date").ifBlank { null },
        )
    }

    private fun fallbackSearch(item: MediaItem, query: String, language: String): MediaItem? {
        val root = requestJson("https://api.themoviedb.org/3/search/multi", language, query) ?: return null
        val best = root.optJSONArray("results")?.optJSONObject(0) ?: return null
        val mediaType = best.optString("media_type").ifBlank { null }
        return item.copy(
            title = best.optString("title").ifBlank { best.optString("name").ifBlank { item.title } },
            artworkUrl = posterUrl(best.optString("poster_path").ifBlank { null }),
            backdropUrl = backdropUrl(best.optString("backdrop_path").ifBlank { null }),
            overview = best.optString("overview").ifBlank { item.overview },
            seriesOverview = best.optString("overview").ifBlank { item.seriesOverview },
            releaseLabel = best.optString("release_date").ifBlank { best.optString("first_air_date").ifBlank { null } },
            matchSource = mediaType ?: "tmdb",
            tmdbId = best.optInt("id").takeIf { it != 0 } ?: item.tmdbId,
            tmdbMediaType = mediaType ?: item.tmdbMediaType,
        )
    }

    private fun searchMovie(query: String, language: String): SearchResult? {
        val root = requestJson("https://api.themoviedb.org/3/search/movie", language, query) ?: return null
        return root.optJSONArray("results")?.optJSONObject(0)?.toSearchResult(titleField = "title", dateField = "release_date")
    }

    private fun searchTv(query: String, language: String): SearchResult? {
        val root = requestJson("https://api.themoviedb.org/3/search/tv", language, query) ?: return null
        return root.optJSONArray("results")?.optJSONObject(0)?.toSearchResult(titleField = "name", dateField = "first_air_date")
    }

    private fun requestJson(
        endpoint: String,
        language: String,
        query: String?,
        appendToResponse: String? = null,
        extraParams: Map<String, String> = emptyMap(),
    ): JSONObject? {
        val urlBuilder = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("language", language)
            .apply {
                if (!query.isNullOrBlank()) {
                    addQueryParameter("query", query)
                    addQueryParameter("include_adult", "false")
                }
                if (!appendToResponse.isNullOrBlank()) {
                    addQueryParameter("append_to_response", appendToResponse)
                }
                extraParams.forEach { (key, value) -> addQueryParameter(key, value) }
                if (BuildConfig.TMDB_READ_ACCESS_TOKEN.isBlank()) {
                    addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                }
            }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .header("accept", "application/json")

        if (BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${BuildConfig.TMDB_READ_ACCESS_TOKEN}")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            return JSONObject(body)
        }
    }

    private fun JSONObject.toSearchResult(titleField: String, dateField: String): SearchResult {
        return SearchResult(
            id = optInt("id").takeIf { it != 0 },
            title = optString(titleField).ifBlank { "Unknown title" },
            posterPath = optString("poster_path").ifBlank { null },
            backdropPath = optString("backdrop_path").ifBlank { null },
            overview = optString("overview").ifBlank { null },
            releaseLabel = optString(dateField).ifBlank { null },
        )
    }

    private fun extractCastNames(root: JSONObject): List<String> {
        return extractCastMembers(root).map { it.name }
    }

    private fun extractCastMembers(root: JSONObject): List<CastMember> {
        val castArray = root.optJSONObject("credits")?.optJSONArray("cast") ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(castArray.length(), 12)) {
                val cast = castArray.optJSONObject(i) ?: continue
                val id = cast.optInt("id")
                val name = cast.optString("name").ifBlank { null }
                if (id == 0 || name == null) continue
                add(
                    CastMember(
                        id = id,
                        name = name,
                        character = cast.optString("character").ifBlank { null },
                        profileUrl = profileUrl(cast.optString("profile_path").ifBlank { null }),
                    ),
                )
            }
        }
    }

    private fun extractGenres(root: JSONObject): List<String> {
        val genreArray = root.optJSONArray("genres") ?: return emptyList()
        return buildList {
            for (i in 0 until minOf(genreArray.length(), 5)) {
                val name = genreArray.optJSONObject(i)?.optString("name")?.ifBlank { null }
                if (name != null) add(name)
            }
        }
    }

    private fun extractTrailerUrl(results: JSONArray?): String? {
        if (results == null) return null
        var fallbackUrl: String? = null
        for (i in 0 until results.length()) {
            val video = results.optJSONObject(i) ?: continue
            val site = video.optString("site").orEmpty()
            val key = video.optString("key").orEmpty()
            val type = video.optString("type").orEmpty()
            if (key.isBlank()) continue
            val url = when (site.lowercase()) {
                "youtube" -> "https://www.youtube.com/watch?v=$key"
                "vimeo" -> "https://vimeo.com/$key"
                else -> null
            } ?: continue
            if (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true)) {
                return url
            }
            if (fallbackUrl == null) fallbackUrl = url
        }
        return fallbackUrl
    }

    private fun posterUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w342$it" }
    private fun backdropUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w780$it" }
    private fun profileUrl(path: String?): String? = path?.let { "https://image.tmdb.org/t/p/w185$it" }

    private fun buildEpisodeShellTitle(seriesTitle: String, seasonEpisode: String?, fallback: String): String {
        return if (!seasonEpisode.isNullOrBlank()) "$seriesTitle • $seasonEpisode" else seriesTitle.ifBlank { fallback }
    }

    private fun buildEpisodeLabel(seasonEpisode: String?, releaseLabel: String?): String? {
        return when {
            !seasonEpisode.isNullOrBlank() && !releaseLabel.isNullOrBlank() -> "$seasonEpisode • $releaseLabel"
            !seasonEpisode.isNullOrBlank() -> seasonEpisode
            else -> releaseLabel
        }
    }

    private fun parseQuery(input: String): ParsedQuery {
        val seasonEpisodeRegex = Regex("""\bS(\d{1,2})E(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        val seasonEpisodeMatch = seasonEpisodeRegex.find(input)
        val seasonEpisode = seasonEpisodeMatch?.let {
            val season = it.groupValues[1].padStart(2, '0')
            val episode = it.groupValues[2].padStart(2, '0')
            "S${season}E${episode}"
        }

        val cleaned = input
            .replace('•', ' ')
            .replace(Regex("""(?i)\b(1080p|720p|2160p|4k|x264|x265|h264|h265|bluray|webrip|web-dl|brrip|dvdrip|aac|ac3|hdr|hevc|proper|repack)\b"""), " ")
            .replace(seasonEpisodeRegex, " ")
            .replace(Regex("""\b\d{3,4}p\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[._-]+"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return ParsedQuery(
            query = cleaned,
            seasonEpisode = seasonEpisode,
            isEpisode = seasonEpisode != null,
        )
    }

    private data class ParsedQuery(
        val query: String,
        val seasonEpisode: String?,
        val isEpisode: Boolean,
    )

    private data class SearchResult(
        val id: Int?,
        val title: String,
        val posterPath: String?,
        val backdropPath: String?,
        val overview: String?,
        val releaseLabel: String?,
    )

    private data class EpisodeDetails(
        val name: String?,
        val overview: String?,
        val airDate: String?,
    )

    private data class DetailPayload(
        val overview: String?,
        val trailerUrl: String?,
        val castNames: List<String>,
        val castMembers: List<CastMember>,
        val genreNames: List<String>,
    )
}
