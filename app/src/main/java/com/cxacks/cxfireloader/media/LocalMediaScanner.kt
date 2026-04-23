package com.captainxack.pocketreel.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object LocalMediaScanner {
    private val videoExtensions = setOf(
        "mp4", "mkv", "avi", "mov", "m4v", "webm", "ts", "m2ts", "mpg", "mpeg", "wmv",
    )

    private val ignoredDirectoryNames = setOf(
        ".thumbnails", "thumbnails", "thumbs", "cache", "caches", "temp", "tmp",
    )

    private val trailerRegex = Regex("(?i)\\b(trailer|trailers|teaser|promo|preview)\\b")
    private val episodeRegex = Regex("(?i)^(.*?)[ ._-]*S(\\d{1,2})E(\\d{1,2}).*$")
    private val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
    private val junkRegex = Regex("(?i)\\b(1080p|720p|2160p|4k|x264|x265|h264|h265|bluray|webrip|web-dl|brrip|dvdrip|aac|ac3|hdr|hevc|proper|repack)\\b")

    fun scan(context: Context, treeUriString: String?): List<MediaItem> {
        if (treeUriString.isNullOrBlank()) return emptyList()
        val treeUri = Uri.parse(treeUriString)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()

        val mediaFiles = mutableListOf<ScannedMedia>()
        val trailerFiles = mutableListOf<TrailerFile>()
        walk(root, mediaFiles, trailerFiles, parentNames = emptyList())

        val trailerMap = buildTrailerMap(trailerFiles)
        return mediaFiles
            .map { scanned ->
                val trailerUri = resolveTrailerUri(scanned.item, trailerMap)
                scanned.item.copy(trailerUrl = trailerUri)
            }
            .sortedBy { it.title.lowercase() }
    }

    private fun walk(
        node: DocumentFile,
        mediaFiles: MutableList<ScannedMedia>,
        trailerFiles: MutableList<TrailerFile>,
        parentNames: List<String>,
    ) {
        if (node.isDirectory) {
            val name = node.name.orEmpty().trim()
            if (shouldIgnoreDirectory(name)) return
            val nextParents = parentNames + name
            node.listFiles().forEach { child -> walk(child, mediaFiles, trailerFiles, nextParents) }
            return
        }
        if (!node.isFile || !looksLikeVideo(node)) return

        val originalName = node.name.orEmpty()
        val rawBase = originalName.substringBeforeLast('.')
        val trailerHint = parentNames.any { trailerRegex.containsMatchIn(it) } || trailerRegex.containsMatchIn(rawBase)
        if (trailerHint) {
            trailerFiles += TrailerFile(
                uri = node.uri.toString(),
                primaryKey = normalizeKey(cleanup(rawBase.replace(trailerRegex, " "))),
                alternateKeys = alternateKeys(cleanup(rawBase.replace(trailerRegex, " "))),
            )
            return
        }

        val parsed = parseMedia(rawBase)
        mediaFiles += ScannedMedia(
            item = MediaItem(
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
            ),
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

    private fun buildTrailerMap(trailerFiles: List<TrailerFile>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        trailerFiles.forEach { trailer ->
            if (trailer.primaryKey.isNotBlank()) {
                map.putIfAbsent(trailer.primaryKey, trailer.uri)
            }
            trailer.alternateKeys.forEach { key ->
                if (key.isNotBlank()) map.putIfAbsent(key, trailer.uri)
            }
        }
        return map
    }

    private fun resolveTrailerUri(item: MediaItem, trailerMap: Map<String, String>): String? {
        val keys = buildList {
            add(normalizeKey(item.seriesTitle ?: item.title))
            add(normalizeKey(item.normalizedTitle ?: item.title))
            add(normalizeKey(stripSeasonEpisode(item.title)))
            add(normalizeKey(stripSeasonEpisode(item.originalFileName ?: item.title)))
            alternateKeys(item.seriesTitle ?: item.title).forEach { add(it) }
            alternateKeys(item.normalizedTitle ?: item.title).forEach { add(it) }
        }.filter { it.isNotBlank() }.distinct()

        return keys.firstNotNullOfOrNull { trailerMap[it] }
    }

    private fun stripSeasonEpisode(value: String): String {
        return value.replace(episodeRegex, "$1").trim()
    }

    private fun alternateKeys(value: String): List<String> {
        val cleaned = cleanup(value)
        val noYear = cleaned.replace(yearRegex, " ").replace(Regex("\\s+"), " ").trim()
        return listOf(normalizeKey(cleaned), normalizeKey(noYear)).distinct()
    }

    private fun shouldIgnoreDirectory(name: String): Boolean {
        if (name.isBlank()) return false
        if (name.startsWith('.')) return true
        return ignoredDirectoryNames.contains(name.lowercase())
    }

    private fun buildSeasonEpisode(season: Int?, episode: Int?): String {
        val seasonValue = season?.toString()?.padStart(2, '0') ?: "00"
        val episodeValue = episode?.toString()?.padStart(2, '0') ?: "00"
        return "S${seasonValue}E${episodeValue}"
    }

    private fun cleanup(value: String): String {
        return value
            .replace(trailerRegex, " ")
            .replace(junkRegex, " ")
            .replace(Regex("[._-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Unknown video" }
    }

    private fun normalizeKey(value: String): String {
        return cleanup(value)
            .lowercase()
            .replace(yearRegex, " ")
            .replace(Regex("[^a-z0-9]"), "")
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

    private data class ScannedMedia(
        val item: MediaItem,
    )

    private data class TrailerFile(
        val uri: String,
        val primaryKey: String,
        val alternateKeys: List<String>,
    )
}
