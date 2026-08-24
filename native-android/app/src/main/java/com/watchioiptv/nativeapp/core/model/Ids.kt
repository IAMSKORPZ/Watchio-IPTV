package com.watchioiptv.nativeapp.core.model

@JvmInline
value class ProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProviderId cannot be blank" }
    }
}

@JvmInline
value class ContentId(val value: String) {
    init {
        require(value.isNotBlank()) { "ContentId cannot be blank" }
    }
}

@JvmInline
value class ChannelId(val value: String) {
    init {
        require(value.isNotBlank()) { "ChannelId cannot be blank" }
    }
}

@JvmInline
value class MovieId(val value: String) {
    init {
        require(value.isNotBlank()) { "MovieId cannot be blank" }
    }
}

@JvmInline
value class SeriesId(val value: String) {
    init {
        require(value.isNotBlank()) { "SeriesId cannot be blank" }
    }
}

@JvmInline
value class EpisodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "EpisodeId cannot be blank" }
    }
}

@JvmInline
value class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId cannot be blank" }
    }
}

@JvmInline
value class EpgChannelId(val value: String) {
    init {
        require(value.isNotBlank()) { "EpgChannelId cannot be blank" }
    }
}
