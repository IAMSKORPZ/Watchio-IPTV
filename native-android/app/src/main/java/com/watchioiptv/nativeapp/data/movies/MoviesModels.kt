package com.watchioiptv.nativeapp.data.movies

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ProviderType

enum class MovieCategoryKind { All, ContinueWatching, Favorites, History, Provider }

data class MovieCategory(
    val id: String,
    val name: String,
    val kind: MovieCategoryKind,
    val sourceCategoryId: String? = null,
)

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
    val headers: Map<String, String>,
    val isFavorite: Boolean,
    val resumePositionMs: Long?,
    val resumeDurationMs: Long?,
)

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

data class MoviePlaybackRequest(
    val movie: WatchioMovieItem,
    val url: String,
    val headers: Map<String, String>,
    val startPositionMs: Long,
)
