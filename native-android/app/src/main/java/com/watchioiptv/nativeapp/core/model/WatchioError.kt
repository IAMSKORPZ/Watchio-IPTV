package com.watchioiptv.nativeapp.core.model

data class WatchioError(
    val category: Category,
    val message: String,
    val cause: Throwable? = null,
) {
    enum class Category {
        Network,
        Authentication,
        Parsing,
        Database,
        Playback,
        Security,
        Unknown,
    }
}

sealed interface WatchioResult<out T> {
    data class Success<T>(val value: T) : WatchioResult<T>
    data class Failure(val error: WatchioError) : WatchioResult<Nothing>
}
