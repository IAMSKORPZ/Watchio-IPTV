package com.watchioiptv.nativeapp.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE providerId = :providerId AND contentType = :contentType")
    suspend fun deleteByProviderAndType(providerId: String, contentType: String)

    @Query("SELECT * FROM categories WHERE providerId = :providerId AND contentType = :contentType ORDER BY serverOrder ASC, name ASC")
    fun observeByType(providerId: String, contentType: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE providerId = :providerId AND contentType = :contentType ORDER BY serverOrder ASC, name ASC")
    suspend fun getByType(providerId: String, contentType: String): List<CategoryEntity>

    @Transaction
    suspend fun replaceCategories(providerId: String, contentType: String, categories: List<CategoryEntity>) {
        deleteByProviderAndType(providerId, contentType)
        upsertAll(categories)
    }
}

@Dao
interface LiveStreamDao {
    @Upsert
    suspend fun upsertAll(streams: List<LiveStreamEntity>)

    @Query("DELETE FROM live_streams WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Query("SELECT * FROM live_streams WHERE providerId = :providerId AND categoryId = :categoryId ORDER BY serverOrder ASC, name ASC")
    suspend fun getByCategory(providerId: String, categoryId: String): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE providerId = :providerId ORDER BY serverOrder ASC, name ASC")
    suspend fun getByProvider(providerId: String): List<LiveStreamEntity>

    @Query("SELECT * FROM live_streams WHERE providerId = :providerId AND normalizedName LIKE '%' || :query || '%' ORDER BY serverOrder ASC, name ASC LIMIT :limit")
    suspend fun search(providerId: String, query: String, limit: Int): List<LiveStreamEntity>

    @Query("SELECT COUNT(*) FROM live_streams WHERE providerId = :providerId")
    suspend fun countByProvider(providerId: String): Int

    @Transaction
    suspend fun replaceLiveStreams(providerId: String, streams: List<LiveStreamEntity>) {
        deleteByProvider(providerId)
        upsertAll(streams)
    }
}

@Dao
interface VodDao {
    @Upsert
    suspend fun upsertAll(movies: List<VodStreamEntity>)

    @Query("DELETE FROM vod_streams WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Query("SELECT * FROM vod_streams WHERE providerId = :providerId ORDER BY serverOrder ASC, name ASC")
    suspend fun getByProvider(providerId: String): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE providerId = :providerId AND categoryId = :categoryId ORDER BY serverOrder ASC, name ASC")
    suspend fun getByCategory(providerId: String, categoryId: String): List<VodStreamEntity>

    @Query("SELECT * FROM vod_streams WHERE providerId = :providerId AND normalizedName LIKE '%' || :query || '%' ORDER BY serverOrder ASC, name ASC LIMIT :limit")
    suspend fun search(providerId: String, query: String, limit: Int): List<VodStreamEntity>

    @Query("SELECT COUNT(*) FROM vod_streams WHERE providerId = :providerId")
    suspend fun countByProvider(providerId: String): Int

    @Transaction
    suspend fun replaceMovies(providerId: String, movies: List<VodStreamEntity>) {
        deleteByProvider(providerId)
        upsertAll(movies)
    }
}

@Dao
interface MovieDetailDao {
    @Upsert
    suspend fun upsertDetail(detail: MovieDetailEntity)

    @Query("SELECT * FROM movie_details WHERE providerId = :providerId AND movieId = :movieId LIMIT 1")
    suspend fun findDetail(providerId: String, movieId: String): MovieDetailEntity?

    @Upsert
    suspend fun upsertTrailer(cache: TmdbTrailerCacheEntity)

    @Query("SELECT * FROM tmdb_trailer_caches WHERE tmdbId = :tmdbId AND type = :type LIMIT 1")
    suspend fun findTrailer(tmdbId: String, type: String): TmdbTrailerCacheEntity?
}

@Dao
interface SeriesDao {
    @Upsert
    suspend fun upsertAll(series: List<SeriesEntity>)

    @Upsert
    suspend fun upsertSeasons(seasons: List<SeasonEntity>)

    @Query("DELETE FROM series WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Query("SELECT * FROM series WHERE providerId = :providerId ORDER BY serverOrder ASC, name ASC")
    suspend fun getByProvider(providerId: String): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :providerId AND seriesId = :seriesId LIMIT 1")
    suspend fun find(providerId: String, seriesId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE providerId = :providerId AND categoryId = :categoryId ORDER BY serverOrder ASC, name ASC")
    suspend fun getByCategory(providerId: String, categoryId: String): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :providerId AND normalizedName LIKE '%' || :query || '%' ORDER BY serverOrder ASC, name ASC LIMIT :limit")
    suspend fun search(providerId: String, query: String, limit: Int): List<SeriesEntity>

    @Query("SELECT COUNT(*) FROM series WHERE providerId = :providerId")
    suspend fun countByProvider(providerId: String): Int

    @Query("SELECT * FROM seasons WHERE providerId = :providerId AND seriesId = :seriesId ORDER BY seasonNumber ASC")
    suspend fun getSeasons(providerId: String, seriesId: String): List<SeasonEntity>

    @Query("DELETE FROM seasons WHERE providerId = :providerId AND seriesId = :seriesId")
    suspend fun deleteSeasons(providerId: String, seriesId: String)

    @Query("UPDATE series SET name = :name, coverUrl = :coverUrl, plot = :plot, cast = :cast, director = :director, genre = :genre, releaseDate = :releaseDate, rating = :rating, rating5Based = :rating5Based, trailer = :trailer, runtime = :runtime, backdropUrl = :backdropUrl, tmdbId = :tmdbId, updatedAtEpochMs = :updatedAtEpochMs WHERE providerId = :providerId AND seriesId = :seriesId")
    suspend fun updateDetails(
        providerId: String,
        seriesId: String,
        name: String,
        coverUrl: String?,
        plot: String?,
        cast: String?,
        director: String?,
        genre: String?,
        releaseDate: String?,
        rating: String?,
        rating5Based: String?,
        trailer: String?,
        runtime: String?,
        backdropUrl: String?,
        tmdbId: Int?,
        updatedAtEpochMs: Long,
    )

    @Transaction
    suspend fun replaceSeries(providerId: String, series: List<SeriesEntity>) {
        deleteByProvider(providerId)
        upsertAll(series)
    }

    @Transaction
    suspend fun replaceDetails(providerId: String, series: SeriesEntity, seasons: List<SeasonEntity>, episodes: List<EpisodeEntity>) {
        updateDetails(
            providerId = providerId,
            seriesId = series.seriesId,
            name = series.name,
            coverUrl = series.coverUrl,
            plot = series.plot,
            cast = series.cast,
            director = series.director,
            genre = series.genre,
            releaseDate = series.releaseDate,
            rating = series.rating,
            rating5Based = series.rating5Based,
            trailer = series.trailer,
            runtime = series.runtime,
            backdropUrl = series.backdropUrl,
            tmdbId = series.tmdbId,
            updatedAtEpochMs = series.updatedAtEpochMs,
        )
        deleteSeasons(providerId, series.seriesId)
        deleteEpisodes(providerId, series.seriesId)
        upsertSeasons(seasons)
        upsertEpisodes(episodes)
    }

    @Query("DELETE FROM episodes WHERE providerId = :providerId AND seriesId = :seriesId")
    suspend fun deleteEpisodes(providerId: String, seriesId: String)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)
}

@Dao
interface EpisodeDao {
    @Upsert
    suspend fun upsertAll(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE providerId = :providerId AND seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getBySeries(providerId: String, seriesId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE providerId = :providerId AND seriesId = :seriesId AND seasonNumber = :seasonNumber ORDER BY episodeNumber ASC")
    suspend fun getBySeason(providerId: String, seriesId: String, seasonNumber: Int): List<EpisodeEntity>
}

@Dao
interface FavoriteDao {
    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE providerId = :providerId AND contentType = :contentType AND contentId = :contentId AND subContentId = :subContentId")
    suspend fun delete(providerId: String, contentType: String, contentId: String, subContentId: String = "")

    @Query("SELECT * FROM favorites WHERE providerId = :providerId ORDER BY createdAtEpochMs DESC")
    fun observeByProvider(providerId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE providerId = :providerId ORDER BY createdAtEpochMs DESC")
    suspend fun getByProvider(providerId: String): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE providerId = :providerId AND contentType = :contentType AND contentId = :contentId AND subContentId = :subContentId LIMIT 1")
    suspend fun find(providerId: String, contentType: String, contentId: String, subContentId: String = ""): FavoriteEntity?
}

@Dao
interface WatchHistoryDao {
    @Upsert
    suspend fun upsert(history: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history WHERE providerId = :providerId ORDER BY lastWatchedAtEpochMs DESC")
    fun observeRecent(providerId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE providerId = :providerId ORDER BY lastWatchedAtEpochMs DESC")
    suspend fun getRecent(providerId: String): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE providerId = :providerId AND contentType = :contentType AND contentId = :contentId AND subContentId = :subContentId LIMIT 1")
    suspend fun find(providerId: String, contentType: String, contentId: String, subContentId: String = ""): WatchHistoryEntity?
}

@Dao
interface M3uItemDao {
    @Upsert
    suspend fun upsertAll(items: List<M3uItemEntity>)

    @Upsert
    suspend fun upsertStaged(items: List<M3uImportItemEntity>)

    @Query("DELETE FROM m3u_items WHERE providerId = :providerId")
    suspend fun deleteByProvider(providerId: String)

    @Query("DELETE FROM m3u_items WHERE providerId = :providerId AND contentType = :contentType")
    suspend fun deleteByProviderAndType(providerId: String, contentType: String)

    @Query("DELETE FROM m3u_import_items WHERE sessionId = :sessionId")
    suspend fun deleteStaged(sessionId: String)

    @Query("SELECT * FROM m3u_import_items WHERE sessionId = :sessionId ORDER BY playlistOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getStagedBatch(sessionId: String, limit: Int, offset: Int): List<M3uImportItemEntity>

    @Query("SELECT * FROM m3u_import_items WHERE sessionId = :sessionId AND contentType = :contentType ORDER BY playlistOrder ASC LIMIT :limit OFFSET :offset")
    suspend fun getStagedBatchByType(sessionId: String, contentType: String, limit: Int, offset: Int): List<M3uImportItemEntity>

    @Query("SELECT COUNT(*) FROM m3u_import_items WHERE sessionId = :sessionId")
    suspend fun stagedCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM m3u_items WHERE providerId = :providerId AND contentType = :contentType")
    suspend fun countByProviderAndType(providerId: String, contentType: String): Int

    @Query("SELECT * FROM m3u_items WHERE providerId = :providerId ORDER BY playlistOrder ASC LIMIT :limit")
    suspend fun getFirstByProvider(providerId: String, limit: Int = 1): List<M3uItemEntity>

    @Query("SELECT * FROM m3u_items WHERE providerId = :providerId AND contentType = :contentType ORDER BY playlistOrder ASC, name ASC")
    suspend fun getByProviderAndType(providerId: String, contentType: String): List<M3uItemEntity>

    @Query("SELECT * FROM m3u_items WHERE providerId = :providerId AND contentType = :contentType AND normalizedName LIKE '%' || :query || '%' ORDER BY playlistOrder ASC, name ASC LIMIT :limit")
    suspend fun searchByType(providerId: String, contentType: String, query: String, limit: Int): List<M3uItemEntity>

    @Query("SELECT * FROM m3u_items WHERE providerId = :providerId AND contentType = :contentType AND categoryId = :categoryId ORDER BY playlistOrder ASC, name ASC")
    suspend fun getByCategoryAndType(providerId: String, contentType: String, categoryId: String): List<M3uItemEntity>

    @Query("SELECT * FROM m3u_items WHERE providerId = :providerId AND itemId = :itemId LIMIT 1")
    suspend fun find(providerId: String, itemId: String): M3uItemEntity?

    @Transaction
    suspend fun replaceFromStaging(providerId: String, sessionId: String, batchSize: Int) {
        deleteByProvider(providerId)
        var offset = 0
        while (true) {
            val batch = getStagedBatch(sessionId, batchSize, offset)
            if (batch.isEmpty()) break
            upsertAll(batch.map { it.toFinalEntity() })
            offset += batch.size
        }
        deleteStaged(sessionId)
    }

    @Transaction
    suspend fun replaceTypeFromStaging(providerId: String, sessionId: String, contentType: String, batchSize: Int) {
        deleteByProviderAndType(providerId, contentType)
        var offset = 0
        while (true) {
            val batch = getStagedBatchByType(sessionId, contentType, batchSize, offset)
            if (batch.isEmpty()) break
            upsertAll(batch.map { it.toFinalEntity() })
            offset += batch.size
        }
    }
}

@Dao
interface EpgDao {
    @Upsert
    suspend fun upsertSource(source: EpgSourceEntity)

    @Query("SELECT * FROM epg_sources WHERE providerId = :providerId AND enabled = 1 ORDER BY priority ASC LIMIT 1")
    suspend fun getEnabledSource(providerId: String): EpgSourceEntity?

    @Query("SELECT * FROM epg_sources WHERE providerId = :providerId ORDER BY priority ASC")
    suspend fun getSources(providerId: String): List<EpgSourceEntity>

    @Query("SELECT MAX(lastSuccessAtEpochMs) FROM epg_sources WHERE providerId = :providerId")
    suspend fun latestSuccess(providerId: String): Long?

    @Query("DELETE FROM epg_sources WHERE providerId = :providerId AND sourceId = :sourceId")
    suspend fun deleteSource(providerId: String, sourceId: String)

    @Upsert
    suspend fun upsertStagedChannels(channels: List<EpgImportChannelEntity>)

    @Upsert
    suspend fun upsertStagedProgrammes(programmes: List<EpgImportProgrammeEntity>)

    @Query("DELETE FROM epg_import_channels WHERE sessionId = :sessionId")
    suspend fun deleteStagedChannels(sessionId: String)

    @Query("DELETE FROM epg_import_programmes WHERE sessionId = :sessionId")
    suspend fun deleteStagedProgrammes(sessionId: String)

    @Query("DELETE FROM epg_channels WHERE providerId = :providerId")
    suspend fun deleteChannels(providerId: String)

    @Query("DELETE FROM epg_programmes WHERE providerId = :providerId")
    suspend fun deleteProgrammes(providerId: String)

    @Query("SELECT * FROM epg_import_channels WHERE sessionId = :sessionId ORDER BY epgChannelId ASC LIMIT :limit OFFSET :offset")
    suspend fun getStagedChannelBatch(sessionId: String, limit: Int, offset: Int): List<EpgImportChannelEntity>

    @Query("SELECT * FROM epg_import_programmes WHERE sessionId = :sessionId ORDER BY epgChannelId ASC, startTimeEpochMs ASC LIMIT :limit OFFSET :offset")
    suspend fun getStagedProgrammeBatch(sessionId: String, limit: Int, offset: Int): List<EpgImportProgrammeEntity>

    @Query("SELECT COUNT(*) FROM epg_import_channels WHERE sessionId = :sessionId")
    suspend fun stagedChannelCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM epg_import_programmes WHERE sessionId = :sessionId")
    suspend fun stagedProgrammeCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM epg_channels WHERE providerId = :providerId")
    suspend fun channelCount(providerId: String): Int

    @Query("SELECT COUNT(*) FROM epg_programmes WHERE providerId = :providerId")
    suspend fun programmeCount(providerId: String): Int

    @Query("SELECT * FROM epg_channels WHERE providerId = :providerId")
    suspend fun getChannels(providerId: String): List<EpgChannelEntity>

    @Query("SELECT * FROM epg_programmes WHERE providerId = :providerId AND epgChannelId = :epgChannelId AND startTimeEpochMs <= :now AND endTimeEpochMs > :now ORDER BY startTimeEpochMs ASC LIMIT 1")
    suspend fun getCurrentProgramme(providerId: String, epgChannelId: String, now: Long): EpgProgrammeEntity?

    @Query("SELECT * FROM epg_programmes WHERE providerId = :providerId AND epgChannelId = :epgChannelId AND startTimeEpochMs > :now ORDER BY startTimeEpochMs ASC LIMIT 1")
    suspend fun getNextProgramme(providerId: String, epgChannelId: String, now: Long): EpgProgrammeEntity?

    @Query("SELECT * FROM epg_programmes WHERE providerId = :providerId AND epgChannelId = :epgChannelId AND startTimeEpochMs < :toEpochMs AND endTimeEpochMs > :fromEpochMs ORDER BY startTimeEpochMs ASC LIMIT :limit")
    suspend fun getProgrammes(providerId: String, epgChannelId: String, fromEpochMs: Long, toEpochMs: Long, limit: Int): List<EpgProgrammeEntity>

    @Query("SELECT * FROM epg_programmes WHERE providerId = :providerId AND epgChannelId IN (:epgChannelIds) AND startTimeEpochMs < :toEpochMs AND endTimeEpochMs > :fromEpochMs ORDER BY epgChannelId ASC, startTimeEpochMs ASC")
    suspend fun getGuide(providerId: String, epgChannelIds: List<String>, fromEpochMs: Long, toEpochMs: Long): List<EpgProgrammeEntity>

    @Query("DELETE FROM epg_programmes WHERE providerId = :providerId AND (endTimeEpochMs < :minEndEpochMs OR startTimeEpochMs > :maxStartEpochMs)")
    suspend fun prune(providerId: String, minEndEpochMs: Long, maxStartEpochMs: Long): Int

    @Transaction
    suspend fun replaceFromStaging(providerId: String, sessionId: String, batchSize: Int) {
        deleteProgrammes(providerId)
        deleteChannels(providerId)
        var channelOffset = 0
        while (true) {
            val batch = getStagedChannelBatch(sessionId, batchSize, channelOffset)
            if (batch.isEmpty()) break
            upsertChannels(batch.map { it.toFinalEntity() })
            channelOffset += batch.size
        }
        var programmeOffset = 0
        while (true) {
            val batch = getStagedProgrammeBatch(sessionId, batchSize, programmeOffset)
            if (batch.isEmpty()) break
            upsertProgrammes(batch.map { it.toFinalEntity() })
            programmeOffset += batch.size
        }
        deleteStagedProgrammes(sessionId)
        deleteStagedChannels(sessionId)
    }

    @Upsert
    suspend fun upsertChannels(channels: List<EpgChannelEntity>)

    @Upsert
    suspend fun upsertProgrammes(programmes: List<EpgProgrammeEntity>)
}
