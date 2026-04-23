package com.captainxack.pocketreel.media

data class DiscoverItem(
    val id: Int,
    val mediaType: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseLabel: String? = null,
    val overview: String? = null,
)
