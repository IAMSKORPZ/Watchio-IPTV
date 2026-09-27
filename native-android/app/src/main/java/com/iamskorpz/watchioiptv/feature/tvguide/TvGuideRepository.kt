package com.iamskorpz.watchioiptv.feature.tvguide

import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.data.epg.EpgChannelMatcher
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshCoordinator
import com.iamskorpz.watchioiptv.data.epg.EpgRepository
import com.iamskorpz.watchioiptv.data.live.LiveTvCategoryKind
import com.iamskorpz.watchioiptv.data.live.LiveTvCategory
import com.iamskorpz.watchioiptv.data.live.LiveTvChannel
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class TvGuideData(
    val providerId: ProviderId?,
    val categories: List<LiveTvCategory>,
    val selectedCategory: LiveTvCategory?,
    val channels: List<WatchioGuideChannel>,
    val programmes: Map<String, List<WatchioGuideProgramme>>,
    val hasEpgSource: Boolean,
    val epgChannelCount: Int,
    val epgProgrammeCount: Int,
)

class TvGuideRepository(
    private val database: WatchioDatabase,
    private val liveTvRepository: LiveTvRepository,
    private val epgRepository: EpgRepository,
    private val epgRefreshCoordinator: EpgRefreshCoordinator = EpgRefreshCoordinator(database, epgRepository),
    private val matcher: EpgChannelMatcher = EpgChannelMatcher(),
) {
    suspend fun selectedProviderId(): ProviderId? = liveTvRepository.selectedProviderId()
    fun observeSelectedProviderId(): Flow<ProviderId?> = liveTvRepository.observeSelectedProviderId()

    suspend fun categories(providerId: ProviderId): List<LiveTvCategory> = liveTvRepository.categories(providerId)

    suspend fun guide(window: WatchioGuideWindow, nowEpochMs: Long, selectedCategoryId: String?): TvGuideData = withContext(Dispatchers.IO) {
        val providerId = selectedProviderId() ?: return@withContext TvGuideData(null, emptyList(), null, emptyList(), emptyMap(), false, 0, 0)
        guideForProvider(providerId, window, nowEpochMs, selectedCategoryId)
    }

    suspend fun guideForProvider(providerId: ProviderId, window: WatchioGuideWindow, nowEpochMs: Long, selectedCategoryId: String?): TvGuideData = withContext(Dispatchers.IO) {
        val categories = liveTvRepository.categories(providerId)
        val allCategory = categories.firstOrNull { it.kind == LiveTvCategoryKind.All }
            ?: return@withContext TvGuideData(providerId, emptyList(), null, emptyList(), emptyMap(), false, 0, 0)
        val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: allCategory
        val liveChannels = liveTvRepository.channels(providerId, selectedCategory)
        val epgChannels = database.epgDao().getChannels(providerId.value)
        val matchIndex = EpgMatchIndex(epgChannels, matcher)
        val guideChannels = liveChannels.mapIndexed { index, channel ->
            val epgId = matchIndex.match(channel.epgChannelId, channel.name)
            channel.toGuideChannel(index + 1, epgId)
        }
        val channelsByEpgId = guideChannels
            .filter { !it.epgChannelId.isNullOrBlank() }
            .groupBy { it.epgChannelId!! }
        val matchedIds = channelsByEpgId.keys.toList()
        val programmes = if (matchedIds.isEmpty()) {
            emptyMap()
        } else {
            val epgGuide = epgRepository.guide(providerId.value, matchedIds, window.startUtcMs, window.endUtcMs)
            buildMap<String, List<WatchioGuideProgramme>> {
                epgGuide.forEach { (epgId, rows) ->
                    val matchingGuideChannels = channelsByEpgId[epgId].orEmpty()
                    if (matchingGuideChannels.isNotEmpty()) {
                        val validRows = rows.asSequence()
                            .filter { it.endEpochMs > it.startEpochMs }
                            .sortedBy { it.startEpochMs }
                            .toList()
                        matchingGuideChannels.forEach { guideChannel ->
                            put(
                                guideChannel.channelId,
                                validRows.map {
                                    WatchioGuideProgramme(
                                        programmeId = it.programmeId,
                                        channelId = guideChannel.channelId,
                                        epgChannelId = epgId,
                                        title = it.title.ifBlank { "Untitled" },
                                        description = it.description?.takeIf(String::isNotBlank),
                                        startUtcMs = it.startEpochMs,
                                        endUtcMs = it.endEpochMs,
                                        progress = TvGuideTimeline.progress(nowEpochMs, it.startEpochMs, it.endEpochMs),
                                        isLiveNow = it.startEpochMs <= nowEpochMs && it.endEpochMs > nowEpochMs,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
        TvGuideData(
            providerId = providerId,
            categories = categories,
            selectedCategory = selectedCategory,
            channels = guideChannels,
            programmes = programmes,
            hasEpgSource = database.epgDao().getSources(providerId.value).isNotEmpty(),
            epgChannelCount = database.epgDao().channelCount(providerId.value),
            epgProgrammeCount = database.epgDao().programmeCount(providerId.value),
        )
    }

    suspend fun refresh(): Result<String> = withContext(Dispatchers.IO) {
        val providerId = selectedProviderId() ?: return@withContext Result.failure(IllegalStateException("Add a provider first."))
        runCatching {
            val result = epgRefreshCoordinator.refreshProvider(providerId.value)
            "TV Guide refreshed: ${result.channelCount} channels, ${result.programmeCount} programmes."
        }
    }

    suspend fun playback(channel: WatchioGuideChannel): PlaybackMedia {
        val request = liveTvRepository.playback(channel.liveChannel)
        return PlaybackMedia(
            url = request.url,
            title = request.channel.name,
            headers = request.headers,
            isLive = true,
        )
    }

    private fun LiveTvChannel.toGuideChannel(number: Int, matchedEpgId: String?): WatchioGuideChannel =
        WatchioGuideChannel(
            providerId = providerId,
            channelId = id,
            displayName = name,
            logo = logoUrl,
            channelNumber = number.toString(),
            category = categoryId,
            isFavourite = isFavorite,
            isCurrentlyPlaying = false,
            epgChannelId = matchedEpgId,
            liveChannel = this,
        )

    private class EpgMatchIndex(
        channels: List<com.iamskorpz.watchioiptv.core.database.EpgChannelEntity>,
        private val matcher: EpgChannelMatcher,
    ) {
        private val byExactId = channels.associateBy { it.epgChannelId }
        private val byLowerId = channels.associateBy { it.epgChannelId.lowercase() }
        private val byExactName = channels.associateBy { it.displayName }
        private val byLowerName = channels.associateBy { it.displayName.lowercase() }
        private val byNormalizedName = channels.associateBy { it.normalizedName }
        private val byCompactName = channels
            .groupBy { matcher.compact(it.displayName) }
            .mapValues { (_, rows) -> rows.singleOrNull()?.epgChannelId }
        private val byCompactId = channels
            .groupBy { matcher.compactId(it.epgChannelId) }
            .mapValues { (_, rows) -> rows.singleOrNull()?.epgChannelId }

        fun match(primaryId: String?, displayName: String): String? {
            val id = primaryId?.trim()?.takeIf { it.isNotBlank() }
            if (id != null) {
                byExactId[id]?.let { return it.epgChannelId }
                byLowerId[id.lowercase()]?.let { return it.epgChannelId }
            }
            byExactName[displayName]?.let { return it.epgChannelId }
            byLowerName[displayName.lowercase()]?.let { return it.epgChannelId }
            byNormalizedName[com.iamskorpz.watchioiptv.core.util.TextNormalizer.normalizeForSearch(displayName)]?.let { return it.epgChannelId }
            val compact = matcher.compact(displayName)
            if (compact.isNotBlank()) {
                byCompactName[compact]?.let { return it }
                byCompactId[compact]?.let { return it }
            }
            return null
        }
    }
}
