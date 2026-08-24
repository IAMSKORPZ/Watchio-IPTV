package com.watchioiptv.nativeapp.core.security

object SensitiveUrlMasker {
    private val sensitiveQueryKeys = setOf(
        "username",
        "user",
        "password",
        "pass",
        "token",
        "auth",
        "api_key",
        "apikey",
        "key",
    )

    private val iptvPathMarkers = setOf("live", "movie", "series", "timeshift")

    fun mask(input: String): String {
        if (input.isBlank()) return input
        val queryMasked = maskQueryValues(input)
        return maskIptvPathCredentials(queryMasked)
    }

    private fun maskQueryValues(input: String): String {
        var result = input.replace(
            Regex("(?i)(Authorization\\s*[:=]\\s*)(Bearer\\s+)?[^,\\s]+"),
            "$1$2***",
        )
        sensitiveQueryKeys.forEach { key ->
            result = result.replace(
                Regex("(?i)([?&]${Regex.escape(key)}=)[^&#\\s]+"),
                "$1***",
            )
            result = result.replace(
                Regex("(?i)($key\\s*:\\s*)[^,\\s]+"),
                "$1***",
            )
        }
        return result
    }

    private fun maskIptvPathCredentials(input: String): String {
        val parts = input.split("/")
        if (parts.size < 6) return input

        val mutable = parts.toMutableList()
        for (index in mutable.indices) {
            val marker = mutable[index].lowercase()
            if (marker in iptvPathMarkers && mutable.size > index + 2) {
                mutable[index + 1] = "***"
                mutable[index + 2] = "***"
            }
        }
        return mutable.joinToString("/")
    }
}
