package com.iamskorpz.watchioiptv.data.library

import java.text.Normalizer
import java.util.Locale

internal enum class SearchMatchType(val rank: Int) { Exact(0), Prefix(1), TokenPrefix(2), Contains(3), Fuzzy(4) }

internal data class SearchRank(val type: SearchMatchType, val distance: Int = 0)

internal data class PreparedSearchText(
    val normalized: String,
    val words: List<String>,
)

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
    return rankSearchText(item.title, item.subtitle, rawQuery)
}

internal fun rankSearchText(titleValue: String, subtitleValue: String?, rawQuery: String): SearchRank? {
    val query = prepareSearchText(rawQuery)
    val title = prepareSearchText(titleValue)
    return rankPreparedSearchText(title, subtitleValue?.let(::prepareSearchText), query)
}

internal fun prepareSearchText(value: String): PreparedSearchText {
    val normalized = normalizeSearchQuery(value)
    return PreparedSearchText(normalized, normalized.split(' ').filter(String::isNotBlank))
}

internal fun rankPreparedSearchText(
    title: PreparedSearchText,
    subtitle: PreparedSearchText?,
    query: PreparedSearchText,
    includeFuzzy: Boolean = true,
): SearchRank? = rankPreparedSearchText(
    titleNormalized = title.normalized,
    titleWords = title.words,
    subtitleNormalized = subtitle?.normalized,
    query = query,
    includeFuzzy = includeFuzzy,
)

internal fun rankPreparedSearchText(
    titleNormalized: String,
    titleWords: List<String>,
    subtitleNormalized: String?,
    query: PreparedSearchText,
    includeFuzzy: Boolean = true,
): SearchRank? {
    val queryValue = query.normalized
    if (queryValue.isBlank()) return null
    return when {
        titleNormalized == queryValue -> SearchRank(SearchMatchType.Exact)
        titleNormalized.startsWith("$queryValue ") -> SearchRank(SearchMatchType.Prefix)
        query.words.all { queryWord -> titleWords.any { it.startsWith(queryWord) } } -> SearchRank(SearchMatchType.TokenPrefix)
        queryValue.length >= 5 && titleNormalized.contains(queryValue) -> SearchRank(SearchMatchType.Contains)
        queryValue.length >= 5 && subtitleNormalized?.contains(queryValue) == true -> SearchRank(SearchMatchType.Contains, 1)
        includeFuzzy && queryValue.length >= 5 -> fuzzyRank(query, titleNormalized, titleWords)
        else -> null
    }
}

private fun fuzzyRank(query: PreparedSearchText, titleNormalized: String, titleWords: List<String>): SearchRank? {
    val queryValue = query.normalized
    val threshold = if (queryValue.length <= 7) 1 else 2
    var best = threshold + 1
    if (kotlin.math.abs(queryValue.length - titleNormalized.length) <= threshold) {
        best = levenshtein(queryValue, titleNormalized, threshold)
    }
    if (query.words.size == 1) {
        for (word in titleWords) {
            if (kotlin.math.abs(queryValue.length - word.length) <= threshold) {
                best = minOf(best, levenshtein(queryValue, word, threshold))
                if (best == 0) break
            }
        }
    }
    return if (best <= threshold) SearchRank(SearchMatchType.Fuzzy, best) else null
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
