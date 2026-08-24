package com.watchioiptv.nativeapp.domain.model

import com.watchioiptv.nativeapp.core.model.CategoryId
import com.watchioiptv.nativeapp.core.model.ChannelId
import com.watchioiptv.nativeapp.core.model.EpisodeId
import com.watchioiptv.nativeapp.core.model.MovieId
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.model.SeriesId

enum class ProviderType(val persisted: String) {
    Xtream("xtream"),
    M3uUrl("m3u_url"),
    M3uFile("m3u_file");

    companion object {
        fun fromPersisted(value: String): ProviderType =
            entries.firstOrNull { it.persisted == value } ?: M3uUrl
    }
}

enum class ContentType(val persisted: String) {
    Live("live"),
    Movie("movie"),
    Series("series"),
    Episode("episode");

    companion object {
        fun fromPersisted(value: String): ContentType =
            entries.firstOrNull { it.persisted == value } ?: Live
    }
}

enum class InputMode(val persisted: String) {
    Auto("auto"),
    TvRemote("tv_remote"),
    Touch("touch");

    companion object {
        fun fromPersisted(value: String?): InputMode =
            entries.firstOrNull { it.persisted == value } ?: Auto
    }
}

enum class StreamFormat(val persisted: String) {
    Auto("auto"),
    Ts("ts"),
    Hls("hls");

    companion object {
        fun fromPersisted(value: String?): StreamFormat =
            entries.firstOrNull { it.persisted == value } ?: Auto
    }
}

data class WatchioProvider(
    val id: ProviderId,
    val displayName: String,
    val type: ProviderType,
    val serverUrl: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastRefreshAtEpochMs: Long?,
    val enabled: Boolean,
)

data class WatchioCategory(
    val providerId: ProviderId,
    val categoryId: CategoryId,
    val name: String,
    val parentId: String?,
    val contentType: ContentType,
    val serverOrder: Int,
)

data class WatchioChannel(
    val providerId: ProviderId,
    val streamId: ChannelId,
    val name: String,
    val iconUrl: String?,
    val categoryId: CategoryId?,
    val epgChannelId: String?,
    val streamExtension: String?,
    val serverOrder: Int,
)

data class WatchioMovie(
    val providerId: ProviderId,
    val streamId: MovieId,
    val name: String,
    val posterUrl: String?,
    val categoryId: CategoryId?,
    val rating: String?,
    val containerExtension: String?,
    val genre: String?,
    val trailer: String?,
    val serverOrder: Int,
)

data class WatchioSeries(
    val providerId: ProviderId,
    val seriesId: SeriesId,
    val name: String,
    val coverUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val trailer: String?,
    val runtime: String?,
    val categoryId: CategoryId?,
    val tmdbId: Int?,
    val serverOrder: Int,
)

data class WatchioEpisode(
    val providerId: ProviderId,
    val seriesId: SeriesId,
    val episodeId: EpisodeId,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val imageUrl: String?,
    val durationSecs: Int?,
)
