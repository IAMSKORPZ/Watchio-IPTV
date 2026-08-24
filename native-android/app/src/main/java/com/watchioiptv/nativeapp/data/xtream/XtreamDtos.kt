package com.watchioiptv.nativeapp.data.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamPlayerInfoResponseDto(
    @SerialName("user_info") val userInfo: XtreamUserInfoDto? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfoDto? = null,
)

@Serializable
data class XtreamUserInfoDto(
    @Serializable(with = FlexibleStringSerializer::class) val username: String? = null,
    @Serializable(with = FlexibleIntSerializer::class) val auth: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class) val status: String? = null,
    @SerialName("exp_date") @Serializable(with = FlexibleStringSerializer::class) val expiration: String? = null,
    @SerialName("is_trial") @Serializable(with = FlexibleStringSerializer::class) val trial: String? = null,
    @SerialName("active_cons") @Serializable(with = FlexibleStringSerializer::class) val activeConnections: String? = null,
    @SerialName("max_connections") @Serializable(with = FlexibleStringSerializer::class) val maxConnections: String? = null,
    @SerialName("allowed_output_formats") @Serializable(with = FlexibleStringListSerializer::class) val allowedOutputFormats: List<String> = emptyList(),
)

@Serializable
data class XtreamServerInfoDto(
    @Serializable(with = FlexibleStringSerializer::class) val url: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val port: String? = null,
    @SerialName("https_port") @Serializable(with = FlexibleStringSerializer::class) val httpsPort: String? = null,
    @SerialName("server_protocol") @Serializable(with = FlexibleStringSerializer::class) val protocol: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val timezone: String? = null,
    @SerialName("timestamp_now") @Serializable(with = FlexibleLongSerializer::class) val timestamp: Long? = null,
    @SerialName("time_now") @Serializable(with = FlexibleStringSerializer::class) val currentTime: String? = null,
)

@Serializable
data class XtreamCategoryDto(
    @SerialName("category_id") @Serializable(with = FlexibleStringSerializer::class) val categoryId: String? = null,
    @SerialName("category_name") @Serializable(with = FlexibleStringSerializer::class) val categoryName: String? = null,
    @SerialName("parent_id") @Serializable(with = FlexibleStringSerializer::class) val parentId: String? = null,
)

@Serializable
data class XtreamLiveStreamDto(
    @SerialName("stream_id") @Serializable(with = FlexibleStringSerializer::class) val streamId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val name: String? = null,
    @SerialName("stream_icon") @Serializable(with = FlexibleStringSerializer::class) val streamIcon: String? = null,
    @SerialName("category_id") @Serializable(with = FlexibleStringSerializer::class) val categoryId: String? = null,
    @SerialName("epg_channel_id") @Serializable(with = FlexibleStringSerializer::class) val epgChannelId: String? = null,
    @SerialName("container_extension") @Serializable(with = FlexibleStringSerializer::class) val containerExtension: String? = null,
    @SerialName("added") @Serializable(with = FlexibleLongSerializer::class) val added: Long? = null,
)

@Serializable
data class XtreamVodStreamDto(
    @SerialName("stream_id") @Serializable(with = FlexibleStringSerializer::class) val streamId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val name: String? = null,
    @SerialName("stream_icon") @Serializable(with = FlexibleStringSerializer::class) val streamIcon: String? = null,
    @SerialName("category_id") @Serializable(with = FlexibleStringSerializer::class) val categoryId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val rating: String? = null,
    @SerialName("container_extension") @Serializable(with = FlexibleStringSerializer::class) val containerExtension: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val genre: String? = null,
    @SerialName("youtube_trailer") @Serializable(with = FlexibleStringSerializer::class) val trailer: String? = null,
)

@Serializable
data class XtreamVodInfoResponseDto(
    val info: XtreamVodInfoDto? = null,
)

@Serializable
data class XtreamVodInfoDto(
    @Serializable(with = FlexibleStringSerializer::class) val name: String? = null,
    @SerialName("movie_image") @Serializable(with = FlexibleStringSerializer::class) val movieImage: String? = null,
    @SerialName("cover_big") @Serializable(with = FlexibleStringSerializer::class) val coverBig: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cover: String? = null,
    @SerialName("backdrop_path") @Serializable(with = FlexibleStringListSerializer::class) val backdropPath: List<String> = emptyList(),
    @Serializable(with = FlexibleStringSerializer::class) val plot: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cast: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val director: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val genre: String? = null,
    @SerialName("releasedate") @Serializable(with = FlexibleStringSerializer::class) val releaseDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val rating: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val duration: String? = null,
    @SerialName("youtube_trailer") @Serializable(with = FlexibleStringSerializer::class) val youtubeTrailer: String? = null,
    @SerialName("tmdb_id") @Serializable(with = FlexibleIntSerializer::class) val tmdbId: Int? = null,
    @SerialName("container_extension") @Serializable(with = FlexibleStringSerializer::class) val containerExtension: String? = null,
)

@Serializable
data class XtreamSeriesDto(
    @SerialName("series_id") @Serializable(with = FlexibleStringSerializer::class) val seriesId: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val name: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cover: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val plot: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cast: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val director: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val genre: String? = null,
    @SerialName("releaseDate") @Serializable(with = FlexibleStringSerializer::class) val releaseDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val rating: String? = null,
    @SerialName("youtube_trailer") @Serializable(with = FlexibleStringSerializer::class) val trailer: String? = null,
    @SerialName("episode_run_time") @Serializable(with = FlexibleStringSerializer::class) val runtime: String? = null,
    @SerialName("category_id") @Serializable(with = FlexibleStringSerializer::class) val categoryId: String? = null,
    @SerialName("last_modified") @Serializable(with = FlexibleLongSerializer::class) val lastModified: Long? = null,
)

@Serializable
data class XtreamSeriesInfoResponseDto(
    val info: XtreamSeriesInfoDto? = null,
    val seasons: List<XtreamSeasonDto> = emptyList(),
    val episodes: Map<String, List<XtreamEpisodeDto>> = emptyMap(),
)

@Serializable
data class XtreamSeriesInfoDto(
    @Serializable(with = FlexibleStringSerializer::class) val name: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cover: String? = null,
    @SerialName("cover_big") @Serializable(with = FlexibleStringSerializer::class) val coverBig: String? = null,
    @SerialName("backdrop_path") @Serializable(with = FlexibleStringListSerializer::class) val backdropPath: List<String> = emptyList(),
    @Serializable(with = FlexibleStringSerializer::class) val plot: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cast: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val director: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val genre: String? = null,
    @SerialName("releaseDate") @Serializable(with = FlexibleStringSerializer::class) val releaseDateCamel: String? = null,
    @SerialName("releasedate") @Serializable(with = FlexibleStringSerializer::class) val releaseDate: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val rating: String? = null,
    @SerialName("rating_5based") @Serializable(with = FlexibleStringSerializer::class) val rating5Based: String? = null,
    @SerialName("youtube_trailer") @Serializable(with = FlexibleStringSerializer::class) val youtubeTrailer: String? = null,
    @SerialName("episode_run_time") @Serializable(with = FlexibleStringSerializer::class) val episodeRunTime: String? = null,
    @SerialName("tmdb_id") @Serializable(with = FlexibleIntSerializer::class) val tmdbId: Int? = null,
)

@Serializable
data class XtreamSeasonDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @SerialName("season_number") @Serializable(with = FlexibleIntSerializer::class) val seasonNumber: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class) val name: String? = null,
    @SerialName("air_date") @Serializable(with = FlexibleStringSerializer::class) val airDate: String? = null,
    @SerialName("episode_count") @Serializable(with = FlexibleIntSerializer::class) val episodeCount: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class) val overview: String? = null,
    @SerialName("cover_big") @Serializable(with = FlexibleStringSerializer::class) val coverBig: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val cover: String? = null,
)

@Serializable
data class XtreamEpisodeDto(
    @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @SerialName("episode_num") @Serializable(with = FlexibleIntSerializer::class) val episodeNum: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class) val title: String? = null,
    @SerialName("container_extension") @Serializable(with = FlexibleStringSerializer::class) val containerExtension: String? = null,
    val info: XtreamEpisodeInfoDto? = null,
)

@Serializable
data class XtreamEpisodeInfoDto(
    @SerialName("movie_image") @Serializable(with = FlexibleStringSerializer::class) val movieImage: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val plot: String? = null,
    @Serializable(with = FlexibleStringSerializer::class) val duration: String? = null,
    @SerialName("duration_secs") @Serializable(with = FlexibleIntSerializer::class) val durationSecs: Int? = null,
    @Serializable(with = FlexibleStringSerializer::class) val rating: String? = null,
    @SerialName("releasedate") @Serializable(with = FlexibleStringSerializer::class) val releaseDate: String? = null,
    @SerialName("tmdb_id") @Serializable(with = FlexibleIntSerializer::class) val tmdbId: Int? = null,
)
