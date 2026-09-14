package com.watchioiptv.nativeapp.data.library

import com.watchioiptv.nativeapp.core.util.TextNormalizer

internal fun rankSearchResult(item: WatchioSearchResult, rawQuery: String): Int? {
    val query = normalizeWords(rawQuery)
    if (query.isBlank()) return null
    val title = normalizeWords(item.title)
    val queryWords = query.split(' ').filter(String::isNotBlank)
    val titleWords = title.split(' ').filter(String::isNotBlank)
    return when {
        title == query -> 0
        title == query || title.startsWith("$query ") -> 1
        queryWords.all { it in titleWords } -> 2
        queryWords.all { queryWord -> titleWords.any { it.startsWith(queryWord) } } -> 3
        item.subtitle?.let(::normalizeWords)
            ?.split(' ')
            ?.let { words -> queryWords.all { it in words } } == true -> 4
        else -> null
    }
}

private fun normalizeWords(value: String): String =
    TextNormalizer.normalizeForSearch(value)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

internal fun List<WatchioSearchResult>.rankedForSearch(query: String, limit: Int): List<WatchioSearchResult> =
    mapNotNull { item -> rankSearchResult(item, query)?.let { rank -> item to rank } }
        .sortedBy { it.second }
        .map { it.first }
        .distinctBy { Triple(it.providerId, it.contentType, it.contentId) }
        .take(limit)
