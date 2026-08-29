package com.watchioiptv.nativeapp.data.movies

import com.watchioiptv.nativeapp.BuildConfig
import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.MovieDetailEntity
import com.watchioiptv.nativeapp.core.database.TmdbTrailerCacheEntity
import com.watchioiptv.nativeapp.core.database.VodStreamEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.util.MediaTitleNormalizer
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.data.xtream.XtreamApi
import com.watchioiptv.nativeapp.data.xtream.XtreamVodInfoDto
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Locale

class MoviesRepository(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val playbackUrlResolver: PlaybackUrlResolver,
    private val credentialStore: ProviderCredentialStore,
    private val retrofitFactory: (String) -> Retrofit,
    private val tmdbRetrofitFactory: (String) -> Retrofit,
    private val clock: WatchioClock,
) {
    suspend fun selectedProviderId(): ProviderId? = settingsRepository.selectedProviderId.first()

    suspend fun categories(providerId: ProviderId): List<MovieCategory> = withContext(Dispatchers.IO) {
        listOf(
            MovieCategory("all", "ALL MOVIES", MovieCategoryKind.All),
            MovieCategory("continue_watching", "CONTINUE WATCHING", MovieCategoryKind.ContinueWatching),
            MovieCategory("favorites", "FAVOURITES", MovieCategoryKind.Favorites),
            MovieCategory("history", "HISTORY", MovieCategoryKind.History),
        ) + database.categoryDao().getByType(providerId.value, ContentType.Movie.persisted).map { it.toMovieCategory() }
    }

    suspend fun movies(providerId: ProviderId, category: MovieCategory, query: String = ""): List<WatchioMovieItem> = withContext(Dispatchers.IO) {
        val provider = database.providerDao().findById(providerId.value) ?: return@withContext emptyList()
        val providerType = ProviderType.fromPersisted(provider.type)
        val favorites = favoritesRepository.getFavorites(providerId).filter { it.contentType == ContentType.Movie }.associateBy { it.contentId }
        val historyList = historyRepository.recent(providerId).filter { it.contentType == ContentType.Movie }
        val history = historyList.associateBy { it.contentId }
        val normalizedQuery = TextNormalizer.normalizeForSearch(query)
        val all = when (providerType) {
            ProviderType.Xtream -> xtreamMovies(providerId, category, normalizedQuery)
            ProviderType.M3uUrl, ProviderType.M3uFile -> m3uMovies(providerId, category, normalizedQuery)
        }.map { row ->
            when (row) {
                is MovieRow.Xtream -> row.entity.toMovie(providerType, favorites.containsKey(row.entity.streamId), history[row.entity.streamId])
                is MovieRow.M3u -> row.entity.toMovie(providerType, favorites.containsKey(row.entity.itemId), history[row.entity.itemId])
            }
        }
        when (category.kind) {
            MovieCategoryKind.ContinueWatching -> {
                val resumable = historyList.filter { shouldResumePosition(it.positionMs, it.durationMs) }
                val movieMap = all.associateBy { it.id }
                resumable.mapNotNull { hist -> movieMap[hist.contentId] }
            }
            MovieCategoryKind.Favorites -> all.filter { it.isFavorite }
            MovieCategoryKind.History -> {
                val movieMap = all.associateBy { it.id }
                historyList.mapNotNull { hist -> movieMap[hist.contentId] }
            }
            else -> all
        }
    }

    suspend fun movie(providerId: ProviderId, movieId: String): WatchioMovieItem? {
        val all = categories(providerId).firstOrNull { it.kind == MovieCategoryKind.All } ?: return null
        return movies(providerId, all).firstOrNull { it.id == movieId }
    }

    suspend fun details(movie: WatchioMovieItem): MovieDetails = withContext(Dispatchers.IO) {
        val cached = database.movieDetailDao().findDetail(movie.providerId.value, movie.id)
        val freshEnough = cached != null && clock.nowEpochMs() - cached.updatedAtEpochMs < DETAIL_CACHE_MS
        val detail = if (freshEnough) cached else loadXtreamDetail(movie) ?: cached
        val providerTrailer = detail?.youtubeTrailer ?: movie.trailerKey
        val trailer = providerTrailer?.takeIf { it.isNotBlank() } ?: detail?.tmdbId?.let { tmdbTrailer(it) }
        MovieDetails(
            movie = movie,
            title = detail?.title ?: movie.name,
            posterUrl = detail?.posterUrl ?: movie.posterUrl,
            backdropUrl = detail?.backdropUrl,
            plot = detail?.plot,
            cast = detail?.cast,
            director = detail?.director,
            genre = detail?.genre ?: movie.genre,
            releaseDate = detail?.releaseDate,
            rating = detail?.rating ?: movie.rating,
            runtime = detail?.runtime,
            trailerKey = trailer,
            tmdbId = detail?.tmdbId,
        )
    }

    suspend fun playback(movie: WatchioMovieItem, resume: Boolean): MoviePlaybackRequest {
        val url = when (movie.providerType) {
            ProviderType.Xtream -> playbackUrlResolver.resolve(
                PlaybackUrlRequest(movie.providerId, ContentType.Movie, movie.id, movie.containerExtension?.let { ".$it" }),
            )
            ProviderType.M3uUrl, ProviderType.M3uFile -> movie.directUrl ?: throw IllegalStateException("Movie URL unavailable.")
        }
        val start = if (resume && shouldResume(movie.resumePositionMs, movie.resumeDurationMs)) clampedResumePosition(movie.resumePositionMs, movie.resumeDurationMs) else 0L
        return MoviePlaybackRequest(movie, url, movie.headers, start)
    }

    fun shouldResume(positionMs: Long?, durationMs: Long?): Boolean {
        return shouldResumePosition(positionMs, durationMs)
    }

    fun isCompleted(positionMs: Long?, durationMs: Long?): Boolean {
        return isCompletedPosition(positionMs, durationMs)
    }

    private suspend fun loadXtreamDetail(movie: WatchioMovieItem): MovieDetailEntity? {
        if (movie.providerType != ProviderType.Xtream) return null
        val provider = database.providerDao().findById(movie.providerId.value) ?: return null
        val credentials = credentialStore.getXtreamCredentials(movie.providerId.value) ?: return null
        val base = provider.serverUrl ?: return null
        return runCatching {
            val dto = retrofitFactory(base).create(XtreamApi::class.java)
                .vodInfo(credentials.username, credentials.password, vodId = movie.id)
                .info ?: return null
            dto.toEntity(movie)
        }.getOrNull()?.also { database.movieDetailDao().upsertDetail(it) }
    }

    private suspend fun tmdbTrailer(tmdbId: Int): String? {
        val now = clock.nowEpochMs()
        val cached = database.movieDetailDao().findTrailer(tmdbId.toString(), "movie")
        if (cached != null && now - cached.cachedAtEpochMs < TRAILER_CACHE_MS) return cached.trailerKey
        val apiKey = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val videos = tmdbRetrofitFactory(TMDB_BASE).create(TmdbApi::class.java).movieVideos(tmdbId, apiKey).results
            videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" && it.official == true }
                ?: videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                ?: videos.firstOrNull { it.site == "YouTube" }
        }.getOrNull()?.key?.also { key ->
            database.movieDetailDao().upsertTrailer(TmdbTrailerCacheEntity(tmdbId.toString(), "movie", key, now))
        }
    }

    private suspend fun xtreamMovies(providerId: ProviderId, category: MovieCategory, query: String): List<MovieRow> {
        val rows = when {
            query.isNotBlank() -> database.vodDao().search(providerId.value, query, SEARCH_LIMIT)
            category.kind == MovieCategoryKind.Provider -> database.vodDao().getByCategory(providerId.value, category.sourceCategoryId.orEmpty())
            else -> database.vodDao().getByProvider(providerId.value)
        }
        return rows.map { MovieRow.Xtream(it) }
    }

    private suspend fun m3uMovies(providerId: ProviderId, category: MovieCategory, query: String): List<MovieRow> {
        val dao = database.m3uItemDao()
        val rows = when (category.kind) {
            MovieCategoryKind.Provider -> dao.getByCategoryAndType(providerId.value, ContentType.Movie.persisted, category.sourceCategoryId.orEmpty())
            else -> dao.getByProviderAndType(providerId.value, ContentType.Movie.persisted)
        }.filter { query.isBlank() || it.normalizedName.contains(query) }
        return rows.map { MovieRow.M3u(it) }
    }

    private fun CategoryEntity.toMovieCategory() = MovieCategory(categoryId, name, MovieCategoryKind.Provider, categoryId)

    private fun VodStreamEntity.toMovie(providerType: ProviderType, favorite: Boolean, history: com.watchioiptv.nativeapp.domain.repository.HistoryItem?) = WatchioMovieItem(
        providerId = ProviderId(providerId),
        providerType = providerType,
        id = streamId,
        name = MediaTitleNormalizer.cleanTitle(name).displayTitle,
        posterUrl = posterUrl,
        categoryId = categoryId,
        rating = rating,
        genre = genre,
        containerExtension = containerExtension,
        trailerKey = trailer,
        serverOrder = serverOrder,
        directUrl = null,
        headers = emptyMap(),
        isFavorite = favorite,
        resumePositionMs = history?.positionMs,
        resumeDurationMs = history?.durationMs,
    )

    private fun M3uItemEntity.toMovie(providerType: ProviderType, favorite: Boolean, history: com.watchioiptv.nativeapp.domain.repository.HistoryItem?) = WatchioMovieItem(
        providerId = ProviderId(providerId),
        providerType = providerType,
        id = itemId,
        name = MediaTitleNormalizer.cleanTitle(name).displayTitle,
        posterUrl = tvgLogo,
        categoryId = categoryId,
        rating = null,
        genre = null,
        containerExtension = directUrl.substringAfterLast('.', "").substringBefore('?').takeIf { it.isNotBlank() },
        trailerKey = null,
        serverOrder = playlistOrder,
        directUrl = directUrl,
        headers = buildMap {
            userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
            referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        },
        isFavorite = favorite,
        resumePositionMs = history?.positionMs,
        resumeDurationMs = history?.durationMs,
    )

    private fun cleanM3uMovieTitle(raw: String): String = MediaTitleNormalizer.cleanTitle(raw).displayTitle

    private fun XtreamVodInfoDto.toEntity(movie: WatchioMovieItem): MovieDetailEntity = MovieDetailEntity(
        providerId = movie.providerId.value,
        movieId = movie.id,
        title = name ?: movie.name,
        posterUrl = movieImage ?: coverBig ?: cover ?: movie.posterUrl,
        backdropUrl = backdropPath.firstOrNull(),
        plot = plot,
        cast = cast,
        director = director,
        genre = genre ?: movie.genre,
        releaseDate = releaseDate,
        rating = rating ?: movie.rating,
        runtime = duration,
        youtubeTrailer = youtubeTrailer ?: movie.trailerKey,
        tmdbId = tmdbId,
        containerExtension = containerExtension ?: movie.containerExtension,
        updatedAtEpochMs = clock.nowEpochMs(),
    )

    private sealed interface MovieRow {
        data class Xtream(val entity: VodStreamEntity) : MovieRow
        data class M3u(val entity: M3uItemEntity) : MovieRow
    }

    @Serializable
    private data class TmdbVideosDto(val results: List<TmdbVideoDto> = emptyList())

    @Serializable
    private data class TmdbVideoDto(
        val key: String? = null,
        val site: String? = null,
        val type: String? = null,
        val official: Boolean? = null,
    )

    private interface TmdbApi {
        @GET("movie/{tmdbId}/videos")
        suspend fun movieVideos(@Path("tmdbId") tmdbId: Int, @Query("api_key") apiKey: String): TmdbVideosDto
    }

    companion object {
        private const val SEARCH_LIMIT = 500
        const val RESUME_MIN_MS = 30_000L
        const val RESUME_REMAINING_MIN_MS = 60_000L
        const val COMPLETE_REMAINING_MS = 120_000L
        const val COMPLETE_FRACTION = 0.90f
        private const val DETAIL_CACHE_MS = 24 * 60 * 60 * 1000L
        private const val TRAILER_CACHE_MS = 30L * 24L * 60L * 60L * 1000L
        private const val TMDB_BASE = "https://api.themoviedb.org/3/"

        fun isCompletedPosition(positionMs: Long?, durationMs: Long?): Boolean {
            val position = positionMs ?: return false
            val duration = durationMs ?: return false
            if (duration <= 0L || position < 0L) return false
            if (position >= duration) return true
            val fraction = position.toFloat() / duration.toFloat()
            val remaining = duration - position
            return fraction >= COMPLETE_FRACTION || remaining <= COMPLETE_REMAINING_MS
        }

        fun shouldResumePosition(positionMs: Long?, durationMs: Long?): Boolean {
            val position = positionMs ?: return false
            if (position < RESUME_MIN_MS) return false
            val duration = durationMs
            if (duration == null || duration <= 0L) return true
            if (position >= duration) return false
            if (isCompletedPosition(position, duration)) return false
            val remaining = duration - position
            return remaining > RESUME_REMAINING_MIN_MS
        }

        fun clampedResumePosition(positionMs: Long?, durationMs: Long?): Long {
            val position = positionMs ?: return 0L
            if (position < RESUME_MIN_MS) return 0L
            val duration = durationMs
            if (duration != null && duration > 0L) {
                if (position >= duration || isCompletedPosition(position, duration)) return 0L
                if (duration - position <= RESUME_REMAINING_MIN_MS) return 0L
                return position.coerceIn(0L, duration)
            }
            return position.coerceAtLeast(0L)
        }
    }
}
