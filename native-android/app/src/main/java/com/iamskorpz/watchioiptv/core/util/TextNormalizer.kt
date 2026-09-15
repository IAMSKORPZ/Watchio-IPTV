package com.iamskorpz.watchioiptv.core.util

import java.util.Locale

object TextNormalizer {
    fun normalizeForSearch(value: String): String =
        value.trim()
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.ROOT)
}
