package com.watchioiptv.nativeapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.data.epg.EpgRefreshInterval
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.PlayerSettings
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.watchioiptv.nativeapp.domain.repository.XtreamAccountMetadata
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeId
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WatchioSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val selectedProviderId: Flow<ProviderId?> = dataStore.data.map { preferences ->
        preferences[SelectedProviderId]?.let(::ProviderId)
    }

    val themeJson: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ThemeJson]
    }

    val theme: Flow<WatchioThemeState> = themeJson.map {
        WatchioThemeState.fromId(WatchioThemeId.fromPersisted(it))
    }

    override val inputMode: Flow<InputMode> = dataStore.data.map { preferences ->
        InputMode.fromPersisted(preferences[InputModeKey])
    }

    override val streamFormat: Flow<StreamFormat> = dataStore.data.map { preferences ->
        StreamFormat.fromPersisted(preferences[StreamFormatKey])
    }

    override val deviceModeOnboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DeviceModeOnboardingCompleted] ?: false
    }

    val epgAutoRefreshEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[EpgAutoRefreshEnabled] ?: true
    }

    val epgRefreshInterval: Flow<EpgRefreshInterval> = dataStore.data.map { preferences ->
        EpgRefreshInterval.fromPersisted(preferences[EpgRefreshIntervalKey])
    }

    val resumePlaybackEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ResumePlaybackEnabled] ?: true
    }

    override val playerSettings: Flow<PlayerSettings> = dataStore.data.map { preferences ->
        PlayerSettings(
            autoResume = preferences[PlayerAutoResume] ?: true,
            autoPlayLiveChannel = preferences[PlayerAutoPlayLiveChannel] ?: false,
            rememberLastLiveChannel = preferences[PlayerRememberLastLiveChannel] ?: true,
            showPlayerControls = preferences[PlayerShowControls] ?: true,
            controlAutoHideDelay = ControlAutoHideDelay.fromPersisted(preferences[PlayerControlAutoHideDelay]),
            autoRetryStreams = preferences[PlayerAutoRetryStreams] ?: true,
            retryAttempts = (preferences[PlayerRetryAttempts] ?: "2").toIntOrNull()?.coerceIn(1, 3) ?: 2,
            videoScalingMode = VideoScalingMode.fromPersisted(preferences[PlayerVideoScalingMode]),
        )
    }

    override suspend fun setSelectedProviderId(providerId: ProviderId?) {
        dataStore.edit { preferences ->
            if (providerId == null) {
                preferences.remove(SelectedProviderId)
            } else {
                preferences[SelectedProviderId] = providerId.value
            }
        }
    }

    override suspend fun setInputMode(inputMode: InputMode) {
        dataStore.edit { preferences -> preferences[InputModeKey] = inputMode.persisted }
    }

    override suspend fun setStreamFormat(streamFormat: StreamFormat) {
        dataStore.edit { preferences -> preferences[StreamFormatKey] = streamFormat.persisted }
    }

    override suspend fun setDeviceModeOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences -> preferences[DeviceModeOnboardingCompleted] = completed }
    }

    override fun observeProviderExpiryEpochMs(providerId: ProviderId): Flow<Long?> = dataStore.data.map { preferences ->
        preferences[providerExpiryKey(providerId)]
    }

    override suspend fun setProviderExpiryEpochMs(providerId: ProviderId, expiryEpochMs: Long?) {
        dataStore.edit { preferences ->
            val key = providerExpiryKey(providerId)
            if (expiryEpochMs == null) {
                preferences.remove(key)
            } else {
                preferences[key] = expiryEpochMs
            }
        }
    }

    override fun observeSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType): Flow<Long?> =
        dataStore.data.map { preferences -> preferences[sectionRefreshKey(providerId, contentType)] }

    override suspend fun setSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType, epochMs: Long?) {
        dataStore.edit { preferences ->
            val key = sectionRefreshKey(providerId, contentType)
            if (epochMs == null) {
                preferences.remove(key)
            } else {
                preferences[key] = epochMs
            }
        }
    }

    override fun observeXtreamAccountMetadata(providerId: ProviderId): Flow<XtreamAccountMetadata> =
        dataStore.data.map { preferences ->
            XtreamAccountMetadata(
                status = preferences[accountStatusKey(providerId)],
                maxConnections = preferences[accountMaxConnectionsKey(providerId)],
                activeConnections = preferences[accountActiveConnectionsKey(providerId)],
                allowedOutputFormats = preferences[accountOutputFormatsKey(providerId)]
                    ?.split(",")
                    ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                    .orEmpty(),
            )
        }

    override suspend fun setXtreamAccountMetadata(providerId: ProviderId, metadata: XtreamAccountMetadata) {
        dataStore.edit { preferences ->
            preferences.setOrRemove(accountStatusKey(providerId), metadata.status)
            preferences.setOrRemove(accountMaxConnectionsKey(providerId), metadata.maxConnections)
            preferences.setOrRemove(accountActiveConnectionsKey(providerId), metadata.activeConnections)
            preferences.setOrRemove(accountOutputFormatsKey(providerId), metadata.allowedOutputFormats.joinToString(",").takeIf { it.isNotBlank() })
        }
    }

    suspend fun setThemeJson(value: String?) {
        dataStore.edit { preferences ->
            if (value == null) {
                preferences.remove(ThemeJson)
            } else {
                preferences[ThemeJson] = value
            }
        }
    }

    suspend fun setTheme(theme: WatchioThemeState) {
        setThemeJson(theme.id.persisted)
    }

    suspend fun setEpgAutoRefreshEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[EpgAutoRefreshEnabled] = enabled }
    }

    suspend fun setEpgRefreshInterval(interval: EpgRefreshInterval) {
        dataStore.edit { preferences -> preferences[EpgRefreshIntervalKey] = interval.persisted }
    }

    override suspend fun setAutoResume(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PlayerAutoResume] = enabled }
    }

    override suspend fun setAutoPlayLiveChannel(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PlayerAutoPlayLiveChannel] = enabled }
    }

    override suspend fun setRememberLastLiveChannel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PlayerRememberLastLiveChannel] = enabled
            if (!enabled) {
                preferences.asMap().keys
                    .filter { it.name.startsWith("provider_") && it.name.endsWith("_last_live_channel_id") }
                    .forEach { preferences.remove(it) }
            }
        }
    }

    override suspend fun setShowPlayerControls(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PlayerShowControls] = enabled }
    }

    override suspend fun setControlAutoHideDelay(delay: ControlAutoHideDelay) {
        dataStore.edit { preferences -> preferences[PlayerControlAutoHideDelay] = delay.persisted }
    }

    override suspend fun setAutoRetryStreams(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PlayerAutoRetryStreams] = enabled }
    }

    override suspend fun setRetryAttempts(attempts: Int) {
        dataStore.edit { preferences -> preferences[PlayerRetryAttempts] = attempts.coerceIn(1, 3).toString() }
    }

    override suspend fun setVideoScalingMode(mode: VideoScalingMode) {
        dataStore.edit { preferences -> preferences[PlayerVideoScalingMode] = mode.persisted }
    }

    override fun observeLastLiveChannelId(providerId: ProviderId): Flow<String?> =
        dataStore.data.map { preferences -> preferences[lastLiveChannelKey(providerId)] }

    override suspend fun setLastLiveChannelId(providerId: ProviderId, channelId: String?) {
        dataStore.edit { preferences -> preferences.setOrRemove(lastLiveChannelKey(providerId), channelId) }
    }

    private companion object {
        val SelectedProviderId = stringPreferencesKey("selected_provider_id")
        val ThemeJson = stringPreferencesKey("theme_json")
        val InputModeKey = stringPreferencesKey("input_mode")
        val StreamFormatKey = stringPreferencesKey("stream_format")
        val DeviceModeOnboardingCompleted = booleanPreferencesKey("device_mode_onboarding_completed")
        val EpgAutoRefreshEnabled = booleanPreferencesKey("epg_auto_refresh_enabled")
        val EpgRefreshIntervalKey = stringPreferencesKey("epg_refresh_interval")
        val ResumePlaybackEnabled = booleanPreferencesKey("resume_playback_enabled")
        val PlayerAutoResume = booleanPreferencesKey("player_auto_resume")
        val PlayerAutoPlayLiveChannel = booleanPreferencesKey("player_auto_play_live_channel")
        val PlayerRememberLastLiveChannel = booleanPreferencesKey("player_remember_last_live_channel")
        val PlayerShowControls = booleanPreferencesKey("player_show_controls")
        val PlayerControlAutoHideDelay = stringPreferencesKey("player_control_auto_hide_delay")
        val PlayerAutoRetryStreams = booleanPreferencesKey("player_auto_retry_streams")
        val PlayerRetryAttempts = stringPreferencesKey("player_retry_attempts")
        val PlayerVideoScalingMode = stringPreferencesKey("player_video_scaling_mode")
        fun providerExpiryKey(providerId: ProviderId) = longPreferencesKey("provider_expiry_${providerId.value}")
        fun sectionRefreshKey(providerId: ProviderId, contentType: ContentType) =
            longPreferencesKey("provider_${providerId.value}_${contentType.persisted}_refresh_at")
        fun accountStatusKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_xtream_status")
        fun accountMaxConnectionsKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_xtream_max_connections")
        fun accountActiveConnectionsKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_xtream_active_connections")
        fun accountOutputFormatsKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_xtream_output_formats")
        fun lastLiveChannelKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_last_live_channel_id")
    }
}

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    val cleaned = value?.trim()?.takeIf { it.isNotBlank() }
    if (cleaned == null) remove(key) else this[key] = cleaned
}
