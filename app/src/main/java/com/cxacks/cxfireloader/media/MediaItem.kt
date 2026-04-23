package com.captainxack.pocketreel.media

enum class MediaKind {
    MOVIE,
    EPISODE,
    VIDEO,
}

data class CastMember(
    val id: Int,
    val name: String,
    val character: String? = null,
    val profileUrl: String? = null,
)

data class FilmCredit(
    val id: Int,
    val mediaType: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseLabel: String? = null,
    val overview: String? = null,
)

data class ActorProfile(
    val id: Int,
    val name: String,
    val profileUrl: String? = null,
    val biography: String? = null,
    val knownForDepartment: String? = null,
    val credits: List<FilmCredit> = emptyList(),
)

data class MediaItem(
    val id: String,
    val title: String,
    val documentUri: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val artworkUrl: String? = null,
    val backdropUrl: String? = null,
    val trailerUrl: String? = null,
    val overview: String? = null,
    val seriesOverview: String? = null,
    val episodeTitle: String? = null,
    val releaseLabel: String? = null,
    val matchSource: String? = null,
    val mediaKind: MediaKind = MediaKind.VIDEO,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val originalFileName: String? = null,
    val normalizedTitle: String? = null,
    val tmdbId: Int? = null,
    val tmdbMediaType: String? = null,
    val castNames: List<String> = emptyList(),
    val castMembers: List<CastMember> = emptyList(),
    val genreNames: List<String> = emptyList(),
)
