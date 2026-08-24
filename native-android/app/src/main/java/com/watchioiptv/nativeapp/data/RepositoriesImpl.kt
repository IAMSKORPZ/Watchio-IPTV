package com.watchioiptv.nativeapp.data

import com.watchioiptv.nativeapp.core.database.CategoryDao
import com.watchioiptv.nativeapp.core.database.FavoriteDao
import com.watchioiptv.nativeapp.core.database.FavoriteEntity
import com.watchioiptv.nativeapp.core.database.LiveStreamDao
import com.watchioiptv.nativeapp.core.database.ProviderDao
import com.watchioiptv.nativeapp.core.database.SeriesDao
import com.watchioiptv.nativeapp.core.database.VodDao
import com.watchioiptv.nativeapp.core.database.WatchHistoryDao
import com.watchioiptv.nativeapp.core.database.WatchHistoryEntity
import com.watchioiptv.nativeapp.core.database.toDomain
import com.watchioiptv.nativeapp.core.database.toEntity
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.WatchioCategory
import com.watchioiptv.nativeapp.domain.model.WatchioChannel
import com.watchioiptv.nativeapp.domain.model.WatchioMovie
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.model.WatchioSeries
import com.watchioiptv.nativeapp.domain.repository.CatalogRepository
import com.watchioiptv.nativeapp.domain.repository.FavoriteItem
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryItem
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProviderRepository(
    private val providerDao: ProviderDao,
    private val credentialStore: ProviderCredentialStore,
) : ProviderRepository {
    override fun observeProviders(): Flow<List<WatchioProvider>> =
        providerDao.observeAll().map { providers -> providers.map { it.toDomain() } }

    override suspend fun getProviders(): List<WatchioProvider> =
        providerDao.getAll().map { it.toDomain() }

    override suspend fun getProvider(providerId: ProviderId): WatchioProvider? =
        providerDao.findById(providerId.value)?.toDomain()

    override suspend fun saveProvider(provider: WatchioProvider) {
        providerDao.upsert(provider.toEntity())
    }

    override suspend fun deleteProvider(providerId: ProviderId) {
        providerDao.deleteProviderAndCatalog(providerId.value)
        credentialStore.deleteProviderSecrets(providerId.value)
    }
}

class RoomCatalogRepository(
    private val categoryDao: CategoryDao,
    private val liveStreamDao: LiveStreamDao,
    private val vodDao: VodDao,
    private val seriesDao: SeriesDao,
    private val clock: WatchioClock,
) : CatalogRepository {
    override suspend fun replaceCategories(providerId: ProviderId, contentType: ContentType, categories: List<WatchioCategory>) {
        categoryDao.replaceCategories(providerId.value, contentType.persisted, categories.map { it.toEntity() })
    }

    override suspend fun replaceLiveStreams(providerId: ProviderId, streams: List<WatchioChannel>) {
        val now = clock.nowEpochMs()
        liveStreamDao.replaceLiveStreams(providerId.value, streams.map { it.toEntity(now) })
    }

    override suspend fun replaceMovies(providerId: ProviderId, movies: List<WatchioMovie>) {
        val now = clock.nowEpochMs()
        vodDao.replaceMovies(providerId.value, movies.map { it.toEntity(now) })
    }

    override suspend fun replaceSeries(providerId: ProviderId, series: List<WatchioSeries>) {
        val now = clock.nowEpochMs()
        seriesDao.replaceSeries(providerId.value, series.map { it.toEntity(now) })
    }
}

class RoomFavoritesRepository(
    private val favoriteDao: FavoriteDao,
) : FavoritesRepository {
    override suspend fun toggle(favorite: FavoriteItem): Boolean {
        val existing = favoriteDao.find(
            favorite.providerId.value,
            favorite.contentType.persisted,
            favorite.contentId,
            favorite.subContentId.orEmpty(),
        )
        return if (existing == null) {
            favoriteDao.upsert(favorite.toEntity())
            true
        } else {
            favoriteDao.delete(
                favorite.providerId.value,
                favorite.contentType.persisted,
                favorite.contentId,
                favorite.subContentId.orEmpty(),
            )
            false
        }
    }

    override suspend fun isFavorite(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String?): Boolean =
        favoriteDao.find(providerId.value, contentType.persisted, contentId, subContentId.orEmpty()) != null

    override suspend fun getFavorites(providerId: ProviderId): List<FavoriteItem> =
        favoriteDao.getByProvider(providerId.value).map { it.toDomain() }
}

class RoomHistoryRepository(
    private val historyDao: WatchHistoryDao,
) : HistoryRepository {
    override suspend fun upsert(item: HistoryItem) {
        historyDao.upsert(item.toEntity())
    }

    override suspend fun find(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String?): HistoryItem? =
        historyDao.find(providerId.value, contentType.persisted, contentId, subContentId.orEmpty())?.toDomain()

    override suspend fun recent(providerId: ProviderId): List<HistoryItem> =
        historyDao.getRecent(providerId.value).map { it.toDomain() }
}

private fun FavoriteItem.toEntity(): FavoriteEntity = FavoriteEntity(
    providerId = providerId.value,
    contentType = contentType.persisted,
    contentId = contentId,
    subContentId = subContentId.orEmpty(),
    title = title,
    imageUrl = imageUrl,
    createdAtEpochMs = createdAtEpochMs,
)

private fun FavoriteEntity.toDomain(): FavoriteItem = FavoriteItem(
    providerId = ProviderId(providerId),
    contentType = ContentType.fromPersisted(contentType),
    contentId = contentId,
    subContentId = subContentId.ifBlank { null },
    title = title,
    imageUrl = imageUrl,
    createdAtEpochMs = createdAtEpochMs,
)

private fun HistoryItem.toEntity(): WatchHistoryEntity = WatchHistoryEntity(
    providerId = providerId.value,
    contentType = contentType.persisted,
    contentId = contentId,
    subContentId = subContentId.orEmpty(),
    title = title,
    imageUrl = imageUrl,
    positionMs = positionMs,
    durationMs = durationMs,
    lastWatchedAtEpochMs = lastWatchedAtEpochMs,
)

private fun WatchHistoryEntity.toDomain(): HistoryItem = HistoryItem(
    providerId = ProviderId(providerId),
    contentType = ContentType.fromPersisted(contentType),
    contentId = contentId,
    subContentId = subContentId.ifBlank { null },
    title = title,
    imageUrl = imageUrl,
    positionMs = positionMs,
    durationMs = durationMs,
    lastWatchedAtEpochMs = lastWatchedAtEpochMs,
)
