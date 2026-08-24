package com.watchioiptv.nativeapp.domain.repository

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.model.WatchioCategory
import com.watchioiptv.nativeapp.domain.model.WatchioChannel
import com.watchioiptv.nativeapp.domain.model.WatchioMovie
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.model.WatchioSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class ControlAutoHideDelay(val seconds: Int, val persisted: String, val label: String) {
    ThreeSeconds(3, "3", "3 Seconds"),
    FiveSeconds(5, "5", "5 Seconds"),
    EightSeconds(8, "8", "8 Seconds"),
    Never(0, "never", "Never");

    companion object {
        val Default = FiveSeconds
        fun fromPersisted(value: String?): ControlAutoHideDelay =
            entries.firstOrNull { it.persisted == value } ?: Default
    }
}

enum class VideoScalingMode(val persisted: String, val label: String) {
    Fit("fit", "Fit"),
    Fill("fill", "Fill"),
    Zoom("zoom", "Zoom");

    companion object {
        val Default = Fit
        fun fromPersisted(value: String?): VideoScalingMode =
            entries.firstOrNull { it.persisted == value } ?: Default
    }
}

data class PlayerSettings(
    val autoResume: Boolean = true,
    val autoPlayLiveChannel: Boolean = false,
    val rememberLastLiveChannel: Boolean = true,
    val showPlayerControls: Boolean = true,
    val controlAutoHideDelay: ControlAutoHideDelay = ControlAutoHideDelay.Default,
    val autoRetryStreams: Boolean = true,
    val retryAttempts: Int = 2,
    val videoScalingMode: VideoScalingMode = VideoScalingMode.Default,
)

data class XtreamAccountMetadata(
    val status: String? = null,
    val maxConnections: String? = null,
    val activeConnections: String? = null,
    val allowedOutputFormats: List<String> = emptyList(),
)

interface ProviderRepository {
    fun observeProviders(): Flow<List<WatchioProvider>>
    suspend fun getProviders(): List<WatchioProvider>
    suspend fun getProvider(providerId: ProviderId): WatchioProvider?
    suspend fun saveProvider(provider: WatchioProvider)
    suspend fun deleteProvider(providerId: ProviderId)
}

interface CatalogRepository {
    suspend fun replaceCategories(providerId: ProviderId, contentType: ContentType, categories: List<WatchioCategory>)
    suspend fun replaceLiveStreams(providerId: ProviderId, streams: List<WatchioChannel>)
    suspend fun replaceMovies(providerId: ProviderId, movies: List<WatchioMovie>)
    suspend fun replaceSeries(providerId: ProviderId, series: List<WatchioSeries>)
}

data class FavoriteItem(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val subContentId: String? = null,
    val title: String,
    val imageUrl: String? = null,
    val createdAtEpochMs: Long,
)

interface FavoritesRepository {
    suspend fun toggle(favorite: FavoriteItem): Boolean
    suspend fun isFavorite(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String? = null): Boolean
    suspend fun getFavorites(providerId: ProviderId): List<FavoriteItem>
}

data class HistoryItem(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val subContentId: String? = null,
    val title: String,
    val imageUrl: String? = null,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val lastWatchedAtEpochMs: Long,
)

interface HistoryRepository {
    suspend fun upsert(item: HistoryItem)
    suspend fun find(providerId: ProviderId, contentType: ContentType, contentId: String, subContentId: String? = null): HistoryItem?
    suspend fun recent(providerId: ProviderId): List<HistoryItem>
}

interface SettingsRepository {
    val selectedProviderId: Flow<ProviderId?>
    val inputMode: Flow<InputMode>
    val streamFormat: Flow<StreamFormat>
    suspend fun setSelectedProviderId(providerId: ProviderId?)
    suspend fun setInputMode(inputMode: InputMode)
    suspend fun setStreamFormat(streamFormat: StreamFormat)
    fun observeProviderExpiryEpochMs(providerId: ProviderId): Flow<Long?> = flowOf(null)
    suspend fun setProviderExpiryEpochMs(providerId: ProviderId, expiryEpochMs: Long?) = Unit
    val deviceModeOnboardingCompleted: Flow<Boolean> get() = flowOf(false)
    suspend fun setDeviceModeOnboardingCompleted(completed: Boolean) = Unit
    fun observeSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType): Flow<Long?> = flowOf(null)
    suspend fun setSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType, epochMs: Long?) = Unit
    fun observeXtreamAccountMetadata(providerId: ProviderId): Flow<XtreamAccountMetadata> = flowOf(XtreamAccountMetadata())
    suspend fun setXtreamAccountMetadata(providerId: ProviderId, metadata: XtreamAccountMetadata) = Unit
    val playerSettings: Flow<PlayerSettings> get() = flowOf(PlayerSettings())
    suspend fun setAutoResume(enabled: Boolean) = Unit
    suspend fun setAutoPlayLiveChannel(enabled: Boolean) = Unit
    suspend fun setRememberLastLiveChannel(enabled: Boolean) = Unit
    suspend fun setShowPlayerControls(enabled: Boolean) = Unit
    suspend fun setControlAutoHideDelay(delay: ControlAutoHideDelay) = Unit
    suspend fun setAutoRetryStreams(enabled: Boolean) = Unit
    suspend fun setRetryAttempts(attempts: Int) = Unit
    suspend fun setVideoScalingMode(mode: VideoScalingMode) = Unit
    fun observeLastLiveChannelId(providerId: ProviderId): Flow<String?> = flowOf(null)
    suspend fun setLastLiveChannelId(providerId: ProviderId, channelId: String?) = Unit
}
