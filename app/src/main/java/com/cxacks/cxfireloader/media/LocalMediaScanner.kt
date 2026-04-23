package com.captainxack.pocketreel.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object LocalMediaScanner {
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv",
    )

    private val episodeRegex = Regex("(?i)^(.*?)[ ._-]*S(\\d{1,2})E(\\d{1,2}).*$")
    private val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
    private val junkRegex = Regex("(?i)\\b(1080p|720p|2160p|4k|x264|x265|h264|h265|bluray|webrip|web-dl|brrip|dvdrip|aac|ac3|hdr|hevc|proper|repack)\\b")

    fun scan(context: Context, treeUriString: String?): List<MediaItem> {
        if (treeUriString.isNullOrBlank()) return emptyList()
        val treeUri = Uri.parse(treeUriString)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val output = mutableListOf<MediaItem>()
        walk(root, output)
        return output.sortedBy { it.title.lowercase() }
    }

    private fun walk(node: DocumentFile, output: MutableList<MediaItem>) {
        if (node.isDirectory) {
            node.listFiles().forEach { child -> walk(child, output) }
            return
        }
        if (!node.isFile) return
        if (!looksLikeVideo(node)) return

        val originalName = node.name.orEmpty()
        val rawBase = originalName.substringBeforeLast('.')
        val parsed = parseMedia(rawBase)

        output += MediaItem(
            id = node.uri.toString(),
            title = parsed.displayTitle,
            documentUri = node.uri.toString(),
            mimeType = node.type,
            sizeBytes = node.length().takeIf { it > 0L },
            mediaKind = parsed.mediaKind,
            seriesTitle = parsed.seriesTitle,
            seasonNumber = parsed.seasonNumber,
            episodeNumber = parsed.episodeNumber,
            originalFileName = originalName,
            normalizedTitle = parsed.normalizedTitle,
            releaseLabel = parsed.releaseLabel,
        )
    }

    private fun parseMedia(rawBase: String): ParsedMedia {
        val cleaned = cleanup(rawBase)
        val episodeMatch = episodeRegex.find(rawBase)
        if (episodeMatch != null) {
            val seriesTitle = cleanup(episodeMatch.groupValues[1]).ifBlank { cleaned }
            val season = episodeMatch.groupValues[2].toIntOrNull()
            val episode = episodeMatch.groupValues[3].toIntOrNull()
            val seasonEpisode = buildSeasonEpisode(season, episode)
            return ParsedMedia(
                mediaKind = MediaKind.EPISODE,
                displayTitle = listOfNotNull(seriesTitle, seasonEpisode).joinToString(" • "),
                normalizedTitle = "$seriesTitle - $seasonEpisode",
                seriesTitle = seriesTitle,
                seasonNumber = season,
                episodeNumber = episode,
                releaseLabel = seasonEpisode,
            )
        }

        val year = yearRegex.find(cleaned)?.value
        val movieTitle = cleaned.substringBefore(year ?: "").trim().ifBlank { cleaned }
        return ParsedMedia(
            mediaKind = if (year != null || movieTitle.isNotBlank()) MediaKind.MOVIE else MediaKind.VIDEO,
            displayTitle = listOfNotNull(movieTitle.takeIf { it.isNotBlank() }, year).joinToString(" ").ifBlank { cleaned },
            normalizedTitle = listOfNotNull(movieTitle.takeIf { it.isNotBlank() }, year).joinToString(" ").ifBlank { cleaned },
            releaseLabel = year,
        )
    }

    private fun buildSeasonEpisode(season: Int?, episode: Int?): String {
        val seasonValue = season?.toString()?.padStart(2, '0') ?: "00"
        val episodeValue = episode?.toString()?.padStart(2, '0') ?: "00"
        return "S${seasonValue}E${episodeValue}"
    }

    private fun cleanup(value: String): String {
        return value
            .replace(junkRegex, " ")
            .replace(Regex("[._-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Unknown video" }
    }

    private fun looksLikeVideo(file: DocumentFile): Boolean {
        val mime = file.type.orEmpty()
        if (mime.startsWith("video/")) return true
        val name = file.name.orEmpty()
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in videoExtensions
    }

    private data class ParsedMedia(
        val mediaKind: MediaKind,
        val displayTitle: String,
        val normalizedTitle: String,
        val seriesTitle: String? = null,
        val seasonNumber: Int? = null,
        val episodeNumber: Int? = null,
        val releaseLabel: String? = null,
    )
}
