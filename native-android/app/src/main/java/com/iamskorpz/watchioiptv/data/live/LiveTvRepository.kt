package com.iamskorpz.watchioiptv.data.live

import com.iamskorpz.watchioiptv.core.database.CategoryEntity
import com.iamskorpz.watchioiptv.core.database.LiveStreamEntity
import com.iamskorpz.watchioiptv.core.database.M3uItemEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.epg.EpgChannelMatcher
import com.iamskorpz.watchioiptv.data.epg.EpgMatchIndex
import com.iamskorpz.watchioiptv.data.epg.EpgNowNextCalculator
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlRequest
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlResolver
import com.iamskorpz.watchioiptv.domain.repository.FavoritesRepository
import com.iamskorpz.watchioiptv.domain.repository.HistoryRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

open class LiveTvRepository(
    private val database: WatchioDatabase? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val favoritesRepository: FavoritesRepository? = null,
    private val historyRepository: HistoryRepository? = null,
    private val playbackUrlResolver: PlaybackUrlResolver? = null,
    private val matcher: EpgChannelMatcher = EpgChannelMatcher(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
        return nowNextForChannels(channel.providerId, listOf(channel), nowEpochMs)[channel.id]
            ?: LiveTvNowNext(null, null, 0f)
    }

    open suspend fun nowNextForChannels(
        providerId: ProviderId,
        channels: List<LiveTvChannel>,
        nowEpochMs: Long,
    ): Map<String, LiveTvNowNext> = withContext(ioDispatcher) {
        val db = database ?: return@withContext emptyMap()
        if (channels.isEmpty()) return@withContext emptyMap()

        val epgChannels = db.epgDao().getChannels(providerId.value)
        if (epgChannels.isEmpty()) {
            return@withContext channels.associate { it.id to LiveTvNowNext(null, null, 0f) }
        }

        val matchIndex = EpgMatchIndex(epgChannels, matcher)
        val channelToEpgId = mutableMapOf<String, String>()
        channels.forEach { channel ->
            val matchedId = matchIndex.match(channel.epgChannelId, channel.name)
            if (!matchedId.isNullOrBlank()) {
                channelToEpgId[channel.id] = matchedId
            }
        }

        val matchedEpgIds = channelToEpgId.values.distinct()
        if (matchedEpgIds.isEmpty()) {
            return@withContext channels.associate { it.id to LiveTvNowNext(null, null, 0f) }
        }

        val fromEpochMs = nowEpochMs - 6 * 3_600_000L
        val toEpochMs = nowEpochMs + 24 * 3_600_000L
        val programmes = matchedEpgIds.chunked(500).flatMap { chunk ->
            db.epgDao().getGuide(providerId.value, chunk, fromEpochMs, toEpochMs)
        }
        val programmesByEpgId = programmes.groupBy { it.epgChannelId }

        channels.associate { channel ->
            val epgId = channelToEpgId[channel.id]
            val epgList = if (epgId != null) programmesByEpgId[epgId].orEmpty() else emptyList()
            channel.id to EpgNowNextCalculator.calculate(epgList, nowEpochMs)
        }
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
