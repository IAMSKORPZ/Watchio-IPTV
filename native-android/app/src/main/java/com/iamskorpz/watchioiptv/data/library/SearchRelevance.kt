package com.iamskorpz.watchioiptv.data.library

import java.text.Normalizer
import java.util.Locale

internal enum class SearchMatchType(val rank: Int) { Exact(0), Prefix(1), TokenPrefix(2), Contains(3), Fuzzy(4) }

internal data class SearchRank(val type: SearchMatchType, val distance: Int = 0)

private val leadingCountry = Regex("^(uk|us|usa|de|fr|es|it|ca|au|ie)\\s+")
private val trailingQuality = Regex("\\s+(hd|fhd|uhd|4k|hevc|h265|h264)$")

internal fun normalizeSearchQuery(value: String): String {
    val ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("['’]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
    return ascii.replace(leadingCountry, "").replace(trailingQuality, "").trim()
}

internal fun rankSearchResult(item: WatchioSearchResult, rawQuery: String): SearchRank? {
    val query = normalizeSearchQuery(rawQuery)
    if (query.isBlank()) return null
    val title = normalizeSearchQuery(item.title)
    val queryWords = query.split(' ')
    val titleWords = title.split(' ')
    return when {
        title == query -> SearchRank(SearchMatchType.Exact)
        title.startsWith("$query ") -> SearchRank(SearchMatchType.Prefix)
        queryWords.all { queryWord -> titleWords.any { it.startsWith(queryWord) } } -> SearchRank(SearchMatchType.TokenPrefix)
        query.length >= 5 && title.contains(query) -> SearchRank(SearchMatchType.Contains)
        query.length >= 5 && item.subtitle?.let(::normalizeSearchQuery)?.contains(query) == true -> SearchRank(SearchMatchType.Contains, 1)
        query.length >= 5 -> fuzzyRank(query, title)
        else -> null
    }
}

private fun fuzzyRank(query: String, title: String): SearchRank? {
    val threshold = if (query.length <= 7) 1 else 2
    val candidates = buildList {
        add(title)
        if (!query.contains(' ')) addAll(title.split(' '))
    }
    val distance = candidates.minOfOrNull { levenshtein(query, it, threshold) } ?: return null
    return if (distance <= threshold) SearchRank(SearchMatchType.Fuzzy, distance) else null
}

private fun levenshtein(left: String, right: String, cutoff: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > cutoff) return cutoff + 1
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        var rowMinimum = current[0]
        for (j in right.indices) {
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (left[i] == right[j]) 0 else 1)
            rowMinimum = minOf(rowMinimum, current[j + 1])
        }
        if (rowMinimum > cutoff) return cutoff + 1
        previous = current
    }
    return previous[right.length]
}

internal fun List<WatchioSearchResult>.rankedForSearch(query: String, limit: Int): List<WatchioSearchResult> =
    mapIndexedNotNull { index, item -> rankSearchResult(item, query)?.let { Triple(item, it, index) } }
        .sortedWith(compareBy<Triple<WatchioSearchResult, SearchRank, Int>> { it.second.type.rank }
            .thenBy { it.second.distance }
            .thenBy { it.third })
        .map { it.first }
        .distinctBy { Triple(it.providerId, it.contentType, it.contentId) }
        .take(limit)
