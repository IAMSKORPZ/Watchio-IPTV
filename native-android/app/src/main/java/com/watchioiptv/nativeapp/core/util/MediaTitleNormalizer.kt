package com.watchioiptv.nativeapp.core.util

data class NormalizedMediaTitle(
    val displayTitle: String,
    val detectedYear: String? = null,
)

/**
 * Shared conservative media title normalizer for Watchio (Phase 14.2K.1).
 *
 * Cleans release/filename-style strings into human-readable media titles while
 * strictly protecting legitimate numbered titles (e.g. "1917", "2001: A Space Odyssey",
 * "Blade Runner 2049", "Se7en", "Catch-22", "Spider-Man: No Way Home", "F9", "28 Years Later").
 */
object MediaTitleNormalizer {

    private val EXTENSIONS = setOf(
        "mkv", "mp4", "avi", "ts", "m3u8", "mov", "m4v", "wmv", "flv", "vob", "iso"
    )

    private val RELEASE_TOKENS = setOf(
        // Resolutions
        "480p", "480i", "576p", "576i", "720p", "720i", "1080p", "1080i", "2160p", "2160i", "4k", "8k", "uhd", "fhd", "hd", "sd",
        // Sources
        "bluray", "blu-ray", "brrip", "bdrip", "dvdrip", "dvd", "hdtv", "pdtv", "dsr", "web-dl", "webdl", "webrip", "web", "remux", "cam", "telesync", "r5", "screener", "scr",
        // Codecs
        "x264", "x265", "h264", "h265", "hevc", "av1", "xvid", "divx", "10bit", "8bit",
        // Audio
        "aac", "ac3", "ddp", "ddp5.1", "dd5.1", "dts", "dts-hd", "truehd", "atmos", "eac3", "mp3", "flac", "stereo", "5.1", "7.1",
        // Visual / dynamic range / release tags
        "hdr", "hdr10", "hdr10+", "dv", "dolby", "vision", "imax", "sdr", "3d", "extended", "unrated", "directors.cut", "remastered", "proper", "repack"
    )

    private val EPISODE_PATTERN = Regex("(?i)^(s\\d{1,2}e\\d{1,2}|s\\d{1,2}|season\\s*\\d+|e\\d{1,3}|ep\\s*\\d+)$")
    private val YEAR_PATTERN = Regex("^(19\\d{2}|20\\d{2})$")

    fun cleanTitle(raw: String?): NormalizedMediaTitle {
        if (raw.isNullOrBlank()) return NormalizedMediaTitle("")
        val trimmed = raw.trim()

        var working = trimmed

        // Strip media file extension at end if present
        val extMatch = Regex("(?i)\\.([a-z0-9]{2,4})$").find(working)
        if (extMatch != null && extMatch.groupValues[1].lowercase() in EXTENSIONS) {
            working = working.substring(0, extMatch.range.first)
        }

        // Remove bracketed / curly-bracketed release metadata e.g. [FEATURETTE...], [x0r], {1080p}
        working = working.replace(Regex("\\[.*?\\]"), " ")
            .replace(Regex("\\{.*?\\}"), " ")

        val hasDotsOrUnderscores = working.contains('.') || working.contains('_')
        val words = working.split(Regex("[._\\s]+")).filter { it.isNotBlank() }

        if (words.isEmpty()) return NormalizedMediaTitle(trimmed)

        val hasReleaseMarker = words.any { isReleaseToken(it) } || words.any { EPISODE_PATTERN.matches(it) }

        // If the original string had no dots/underscores and no release markers, it is already a clean title
        if (!hasDotsOrUnderscores && !hasReleaseMarker) {
            return NormalizedMediaTitle(trimmed)
        }

        var detectedYear: String? = null
        val titleTokens = mutableListOf<String>()

        for (i in words.indices) {
            val token = words[i]
            val lower = token.lowercase()

            // Episode marker (e.g. S01E01) marks the end of series title
            if (EPISODE_PATTERN.matches(token)) {
                break
            }

            // Release marker (e.g. 1080p, BluRay, x264, WEB-DL) marks the end of title
            if (isReleaseToken(lower)) {
                break
            }

            // 4-digit release year (1900-2099)
            if (YEAR_PATTERN.matches(token)) {
                val remainingTokens = words.subList(i + 1, words.size)
                val remainingHasRelease = remainingTokens.any { isReleaseToken(it) || EPISODE_PATTERN.matches(it) || YEAR_PATTERN.matches(it) }

                if (titleTokens.isNotEmpty() && (hasDotsOrUnderscores || remainingHasRelease || i < words.size - 1)) {
                    detectedYear = token
                    break
                } else if (titleTokens.isEmpty()) {
                    // Number at start of title (e.g. "1917", "2001: A Space Odyssey")
                    titleTokens.add(token)
                    continue
                }
            }

            // Check hyphenated release group (e.g. "x264-x0r", "BluRay-RARBG")
            if (token.contains('-')) {
                val subParts = token.split('-')
                if (subParts.any { isReleaseToken(it) }) {
                    break
                }
            }

            titleTokens.add(token)
        }

        val display = if (titleTokens.isNotEmpty()) {
            titleTokens.joinToString(" ").trim()
        } else {
            trimmed
        }

        val cleanedDisplay = display.trim(' ', '-', '.', '_', ':', ',')

        return NormalizedMediaTitle(
            displayTitle = if (cleanedDisplay.isNotBlank()) cleanedDisplay else trimmed,
            detectedYear = detectedYear,
        )
    }

    private fun isReleaseToken(token: String): Boolean {
        val lower = token.lowercase().trim('(', ')', '[', ']', '-', '.', '_')
        if (RELEASE_TOKENS.contains(lower)) return true
        if (lower.startsWith("h.") || lower.startsWith("x.")) {
            val withoutDot = lower.replace(".", "")
            if (RELEASE_TOKENS.contains(withoutDot)) return true
        }
        return false
    }
}
