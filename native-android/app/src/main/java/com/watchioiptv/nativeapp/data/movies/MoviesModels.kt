package com.watchioiptv.nativeapp.data.movies

import androidx.compose.runtime.Immutable
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ProviderType

enum class MovieCategoryKind { All, ContinueWatching, Favorites, History, Provider }

@Immutable
data class MovieCategory(
    val id: String,
    val name: String,
    val kind: MovieCategoryKind,
    val sourceCategoryId: String? = null,
)

@Immutable
data class WatchioMovieItem(
    val providerId: ProviderId,
    val providerType: ProviderType,
    val id: String,
    val name: String,
    val posterUrl: String?,
    val categoryId: String?,
    val rating: String?,
    val genre: String?,
    val containerExtension: String?,
    val trailerKey: String?,
    val serverOrder: Int,
    val directUrl: String?,
    val headers: Map<String, String> = emptyMap(),
    val isFavorite: Boolean = false,
    val resumePositionMs: Long? = null,
    val resumeDurationMs: Long? = null,
    val normalizedSearchText: String = "",
    val formattedRating: String? = null,
)

data class MovieCatalogCache(
    val providerId: ProviderId,
    val movies: List<WatchioMovieItem>,
    val movieLookup: Map<String, WatchioMovieItem>,
    val providerCategories: Map<String, List<WatchioMovieItem>>,
    @Volatile var searchIndex: Map<String, String> = emptyMap(),
)

@Immutable
data class MovieDetails(
    val movie: WatchioMovieItem,
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
)

@Immutable
data class MoviePlaybackRequest(
    val movie: WatchioMovieItem,
    val url: String,
    val headers: Map<String, String>,
    val startPositionMs: Long,
)
