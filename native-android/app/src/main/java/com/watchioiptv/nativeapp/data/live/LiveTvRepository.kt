package com.watchioiptv.nativeapp.data.live

import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.LiveStreamEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.repository.FavoritesRepository
import com.watchioiptv.nativeapp.domain.repository.HistoryRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

open class LiveTvRepository(
    private val database: WatchioDatabase? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val favoritesRepository: FavoritesRepository? = null,
    private val historyRepository: HistoryRepository? = null,
    private val playbackUrlResolver: PlaybackUrlResolver? = null,
) {
    open suspend fun selectedProviderId(): ProviderId? = settingsRepository?.selectedProviderId?.first()
    open fun observeSelectedProviderId(): Flow<ProviderId?> = settingsRepository?.selectedProviderId ?: kotlinx.coroutines.flow.flowOf(null)

    open suspend fun categories(providerId: ProviderId): List<LiveTvCategory> {
        val db = database ?: return emptyList()
        val providerCategories = db.categoryDao()
            .getByType(providerId.value, ContentType.Live.persisted)
            .map { it.toLiveCategory() }
        return listOf(
            LiveTvCategory("all", "ALL CHANNELS", LiveTvCategoryKind.All),
            LiveTvCategory("favorites", "FAVOURITES", LiveTvCategoryKind.Favorites),
            LiveTvCategory("history", "HISTORY", LiveTvCategoryKind.History),
        ) + providerCategories
    }

    open suspend fun channels(providerId: ProviderId, category: LiveTvCategory): List<LiveTvChannel> {
        val db = database ?: return emptyList()
        val provider = db.providerDao().findById(providerId.value) ?: return emptyList()
        val providerType = ProviderType.fromPersisted(provider.type)
        val favorites = favoritesRepository?.getFavorites(providerId)
            ?.filter { it.contentType == ContentType.Live }
            ?.associateBy { it.contentId }
            ?: emptyMap()
        val historyIds = historyRepository?.recent(providerId)
            ?.filter { it.contentType == ContentType.Live }
            ?.map { it.contentId }
            ?: emptyList()
        val rows = when (providerType) {
            ProviderType.Xtream -> xtreamChannels(providerId, category)
            ProviderType.M3uUrl,
            ProviderType.M3uFile -> m3uChannels(providerId, category)
        }
        return rows.map { row ->
            when (row) {
                is LiveRow.Xtream -> row.entity.toLive(providerType, favorites.containsKey(row.entity.streamId))
                is LiveRow.M3u -> row.entity.toLive(providerType, favorites.containsKey(row.entity.itemId))
            }
        }.let { channels ->
            when (category.kind) {
                LiveTvCategoryKind.Favorites -> channels.filter { it.isFavorite }
                LiveTvCategoryKind.History -> historyIds.mapNotNull { id -> channels.firstOrNull { it.id == id } }
                else -> channels
            }
        }
    }

    open suspend fun playback(channel: LiveTvChannel): LiveTvPlaybackRequest {
        val url = when (channel.providerType) {
            ProviderType.Xtream -> playbackUrlResolver?.resolve(
                PlaybackUrlRequest(channel.providerId, ContentType.Live, channel.id),
            ) ?: throw IllegalStateException("Stream URL unavailable.")
            ProviderType.M3uUrl,
            ProviderType.M3uFile -> channel.directUrl ?: throw IllegalStateException("Stream URL unavailable.")
        }
        return LiveTvPlaybackRequest(channel, url, channel.headers)
    }

    open suspend fun nowNext(channel: LiveTvChannel, nowEpochMs: Long): LiveTvNowNext {
        val db = database ?: return LiveTvNowNext(null, null, 0f)
        val epgId = channel.epgChannelId?.takeIf { it.isNotBlank() } ?: return LiveTvNowNext(null, null, 0f)
        val current = db.epgDao().getCurrentProgramme(channel.providerId.value, epgId, nowEpochMs)
        val next = db.epgDao().getNextProgramme(channel.providerId.value, epgId, nowEpochMs)
        val progress = current?.let {
            val duration = it.endTimeEpochMs - it.startTimeEpochMs
            if (duration <= 0L) 0f else ((nowEpochMs - it.startTimeEpochMs).toFloat() / duration).coerceIn(0f, 1f)
        } ?: 0f
        return LiveTvNowNext(
            currentTitle = current?.title,
            nextTitle = next?.title,
            progress = progress,
            currentDescription = current?.description,
            currentStartEpochMs = current?.startTimeEpochMs,
            currentEndEpochMs = current?.endTimeEpochMs,
        )
    }

    private suspend fun xtreamChannels(providerId: ProviderId, category: LiveTvCategory): List<LiveRow> {
        val db = database ?: return emptyList()
        val dao = db.liveStreamDao()
        return when (category.kind) {
            LiveTvCategoryKind.Provider -> dao.getByCategory(providerId.value, category.sourceCategoryId.orEmpty())
            else -> dao.getByProvider(providerId.value)
        }.map { LiveRow.Xtream(it) }
    }

    private suspend fun m3uChannels(providerId: ProviderId, category: LiveTvCategory): List<LiveRow> {
        val db = database ?: return emptyList()
        val dao = db.m3uItemDao()
        return when (category.kind) {
            LiveTvCategoryKind.Provider -> dao.getByCategoryAndType(providerId.value, ContentType.Live.persisted, category.sourceCategoryId.orEmpty())
            else -> dao.getByProviderAndType(providerId.value, ContentType.Live.persisted)
        }.map { LiveRow.M3u(it) }
    }

    private fun CategoryEntity.toLiveCategory(): LiveTvCategory =
        LiveTvCategory(categoryId, name, LiveTvCategoryKind.Provider, categoryId)

    private fun LiveStreamEntity.toLive(providerType: ProviderType, favorite: Boolean): LiveTvChannel =
        LiveTvChannel(
            providerId = ProviderId(providerId),
            providerType = providerType,
            id = streamId,
            name = name,
            logoUrl = iconUrl,
            categoryId = categoryId,
            epgChannelId = epgChannelId,
            extension = streamExtension,
            directUrl = null,
            headers = emptyMap(),
            serverOrder = serverOrder,
            isFavorite = favorite,
        )

    private fun M3uItemEntity.toLive(providerType: ProviderType, favorite: Boolean): LiveTvChannel {
        val headers = buildMap {
            userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
            referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        }
        return LiveTvChannel(
            providerId = ProviderId(providerId),
            providerType = providerType,
            id = itemId,
            name = name,
            logoUrl = tvgLogo,
            categoryId = categoryId,
            epgChannelId = tvgId?.takeIf { it.isNotBlank() },
            extension = null,
            directUrl = directUrl,
            headers = headers,
            serverOrder = playlistOrder,
            isFavorite = favorite,
        )
    }

    private sealed interface LiveRow {
        data class Xtream(val entity: LiveStreamEntity) : LiveRow
        data class M3u(val entity: M3uItemEntity) : LiveRow
    }
}
