package com.watchioiptv.nativeapp.data.xtream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object XtreamUrlNormalizer {
    fun normalize(input: String): String? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isBlank() || trimmed.contains(' ')) return null
        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val url = candidate.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank() || url.encodedPath.contains("player_api.php", ignoreCase = true)) return null
        return url.newBuilder()
            .encodedQuery(null)
            .fragment(null)
            .build()
            .toString()
            .trimEnd('/')
    }
}
