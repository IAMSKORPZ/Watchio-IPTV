package com.watchioiptv.nativeapp.data.m3u

import com.watchioiptv.nativeapp.domain.model.ContentType
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class M3uParser(
    private val classifier: M3uContentClassifier = M3uContentClassifier(),
    private val seriesParser: M3uSeriesParser = M3uSeriesParser(),
) {
    suspend fun parse(
        inputStream: InputStream,
        onHeaderEpgUrl: suspend (String) -> Unit = {},
        onItem: suspend (ParsedM3uItem) -> Unit,
    ): Int {
        var count = 0
        val currentMeta = mutableMapOf<String, String?>()
        var currentName: String? = null

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                coroutineContext.ensureActive()
                val raw = reader.readLine() ?: break
                val line = raw.trim().removePrefix("\uFEFF")
                when {
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        firstNonBlank(
                            extractAttribute(line, "url-tvg"),
                            extractAttribute(line, "x-tvg-url"),
                            extractAttribute(line, "tvg-url"),
                        )?.let { onHeaderEpgUrl(it) }
                    }
                    line.startsWith("#EXT-X-TARGETDURATION", ignoreCase = true) ||
                        line.startsWith("#EXT-X-MEDIA-SEQUENCE", ignoreCase = true) ||
                        line.startsWith("#EXT-X-PLAYLIST-TYPE", ignoreCase = true) -> {
                        throw IllegalArgumentException("HLS media playlist is not an M3U channel list.")
                    }
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val comma = line.indexOf(',')
                        val metadataPart = if (comma >= 0) line.substring(0, comma) else line
                        currentName = if (comma >= 0) line.substring(comma + 1).trim().takeIf { it.isNotBlank() } else null
                        currentMeta.clear()
                        currentMeta["tvg-id"] = extractAttribute(metadataPart, "tvg-id")
                        currentMeta["tvg-name"] = extractAttribute(metadataPart, "tvg-name")
                        currentMeta["tvg-logo"] = extractAttribute(metadataPart, "tvg-logo")
                        currentMeta["tvg-url"] = extractAttribute(metadataPart, "tvg-url")
                        currentMeta["tvg-rec"] = extractAttribute(metadataPart, "tvg-rec")
                        currentMeta["tvg-shift"] = firstNonBlank(
                            extractAttribute(metadataPart, "tvg-shift"),
                            extractAttribute(metadataPart, "timeshift"),
                        )
                        currentMeta["group-title"] = extractAttribute(metadataPart, "group-title")
                        currentMeta["user-agent"] = extractAttribute(metadataPart, "user-agent")
                        currentMeta["http-user-agent"] = extractAttribute(metadataPart, "http-user-agent")
                        currentMeta["referrer"] = firstNonBlank(
                            extractAttribute(metadataPart, "referrer"),
                            extractAttribute(metadataPart, "http-referrer"),
                        )
                        currentMeta["catchup"] = extractAttribute(metadataPart, "catchup")
                        currentMeta["catchup-source"] = extractAttribute(metadataPart, "catchup-source")
                        currentMeta["catchup-days"] = extractAttribute(metadataPart, "catchup-days")
                        currentMeta["tvg-chno"] = firstNonBlank(
                            extractAttribute(metadataPart, "tvg-chno"),
                            extractAttribute(metadataPart, "channel-number"),
                        )
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        currentMeta["group-name"] = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                    }
                    line.isBlank() || line.startsWith("#") -> Unit
                    currentMeta.isNotEmpty() || currentName != null -> {
                        val url = line
                        val name = firstNonBlank(
                            currentName,
                            currentMeta["tvg-name"],
                            currentMeta["tvg-id"],
                            filenameFromUrl(url),
                        ) ?: return@use
                        val groupTitle = firstNonBlank(
                            currentMeta["group-title"],
                            currentMeta["group-name"],
                            FALLBACK_GROUP,
                        ) ?: FALLBACK_GROUP
                        val contentType = classifier.classify(url, name)
                        val series = seriesParser.parse(name)
                        onItem(
                            ParsedM3uItem(
                                name = name,
                                url = url,
                                tvgId = currentMeta["tvg-id"],
                                tvgName = currentMeta["tvg-name"],
                                tvgLogo = currentMeta["tvg-logo"],
                                tvgUrl = currentMeta["tvg-url"],
                                tvgRec = currentMeta["tvg-rec"],
                                tvgShift = currentMeta["tvg-shift"],
                                groupTitle = groupTitle,
                                groupName = currentMeta["group-name"],
                                userAgent = firstNonBlank(currentMeta["user-agent"], currentMeta["http-user-agent"]),
                                referrer = currentMeta["referrer"],
                                catchupType = currentMeta["catchup"],
                                catchupSource = currentMeta["catchup-source"],
                                catchupDays = currentMeta["catchup-days"]?.toIntOrNull(),
                                timeshiftHours = currentMeta["tvg-shift"]?.toDoubleOrNull(),
                                channelNumber = currentMeta["tvg-chno"],
                                contentType = contentType,
                                seriesName = series?.name,
                                seasonNumber = series?.seasonNumber,
                                episodeNumber = series?.episodeNumber,
                                playlistOrder = count,
                            ),
                        )
                        count += 1
                        currentMeta.clear()
                        currentName = null
                    }
                }
            }
        }
        return count
    }

    fun extractAttribute(line: String, attribute: String): String? {
        val regex = Regex(
            "\\b${Regex.escape(attribute)}\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s,]+))",
            RegexOption.IGNORE_CASE,
        )
        val match = regex.find(line) ?: return null
        return (match.groups[2]?.value ?: match.groups[3]?.value ?: match.groups[4]?.value)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val FALLBACK_GROUP = "Diğer"

        fun firstNonBlank(vararg values: String?): String? =
            values.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotBlank) }

        fun filenameFromUrl(url: String): String? {
            val path = url.split('#', '?').firstOrNull().orEmpty()
            return path.split('/').lastOrNull { it.isNotBlank() }?.trim()
        }
    }
}

class M3uContentClassifier {
    fun classify(url: String, name: String? = null): ContentType {
        val haystack = "${url.lowercase()} ${name.orEmpty().lowercase()}"
        return when {
            "movie" in haystack -> ContentType.Movie
            "series" in haystack -> ContentType.Series
            else -> ContentType.Live
        }
    }
}

data class M3uSeriesMatch(
    val name: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
)

class M3uSeriesParser {
    private val compact = Regex("^(.+?)\\s+S(\\d{1,2})\\s*E(\\d{1,3})", RegexOption.IGNORE_CASE)
    private val words = Regex("^(.+?)\\s+Season\\s+(\\d{1,2})\\s+Episode\\s+(\\d{1,3})", RegexOption.IGNORE_CASE)

    fun parse(name: String): M3uSeriesMatch? {
        val trimmed = name.trim()
        val match = compact.find(trimmed) ?: words.find(trimmed) ?: return null
        return M3uSeriesMatch(
            name = match.groupValues[1].trim(),
            seasonNumber = match.groupValues[2].toIntOrNull() ?: return null,
            episodeNumber = match.groupValues[3].toIntOrNull() ?: return null,
        )
    }
}
