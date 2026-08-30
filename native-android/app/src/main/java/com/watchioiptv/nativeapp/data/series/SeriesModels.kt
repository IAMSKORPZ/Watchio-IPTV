package com.watchioiptv.nativeapp.data.series

import androidx.compose.runtime.Immutable
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ProviderType

enum class SeriesCategoryKind { All, ContinueWatching, Favorites, History, Provider }

@Immutable
data class SeriesCategory(
    val id: String,
    val name: String,
    val kind: SeriesCategoryKind,
    val sourceCategoryId: String? = null,
)

@Immutable
data class WatchioSeriesItem(
    val providerId: ProviderId,
    val providerType: ProviderType,
    val id: String,
    val name: String,
    val coverUrl: String?,
    val categoryId: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val runtime: String?,
    val trailerKey: String?,
    val tmdbId: Int?,
    val serverOrder: Int,
    val isFavorite: Boolean,
    val lastEpisodeId: String?,
    val normalizedSearchText: String = "",
    val formattedRating: String? = null,
)

@Immutable
data class SeriesCardUiModel(
    val series: WatchioSeriesItem,
    val isContinueWatching: Boolean = false,
    val targetEpisodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeLabel: String? = null,
    val progress: Float? = null,
)

data class SeriesCatalogCache(
    val providerId: ProviderId,
    val series: List<WatchioSeriesItem>,
    val seriesLookup: Map<String, WatchioSeriesItem>,
    val providerCategories: Map<String, List<WatchioSeriesItem>>,
    @Volatile var searchIndex: Map<String, String> = emptyMap(),
)

@Immutable
data class SeriesDetails(
    val series: WatchioSeriesItem,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val runtime: String?,
    val trailerKey: String?,
    val tmdbId: Int?,
    val seasons: List<WatchioSeason>,
    val episodes: List<WatchioEpisodeItem>,
)

@Immutable
data class WatchioSeason(
    val providerId: ProviderId,
    val seriesId: String,
    val seasonId: String,
    val seasonNumber: Int,
    val name: String,
    val airDate: String?,
    val episodeCount: Int,
    val overview: String?,
    val coverUrl: String?,
    val rating: String?,
)

@Immutable
data class WatchioEpisodeItem(
    val providerId: ProviderId,
    val providerType: ProviderType,
    val seriesId: String,
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String?,
    val imageUrl: String?,
    val duration: String?,
    val durationSeconds: Int?,
    val rating: String?,
    val releaseDate: String?,
    val containerExtension: String?,
    val tmdbId: Int?,
    val directUrl: String?,
    val headers: Map<String, String>,
    val resumePositionMs: Long?,
    val resumeDurationMs: Long?,
)

@Immutable
data class EpisodePlaybackRequest(
    val episode: WatchioEpisodeItem,
    val url: String,
    val headers: Map<String, String>,
    val startPositionMs: Long,
)

