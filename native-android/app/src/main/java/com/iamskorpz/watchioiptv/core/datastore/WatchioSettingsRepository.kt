package com.iamskorpz.watchioiptv.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.StreamFormat
import com.iamskorpz.watchioiptv.data.epg.EpgRefreshInterval
import com.iamskorpz.watchioiptv.domain.repository.ControlAutoHideDelay
import com.iamskorpz.watchioiptv.domain.repository.LiveTvBrowsingState
import com.iamskorpz.watchioiptv.domain.repository.PlayerSettings
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.domain.repository.VideoScalingMode
import com.iamskorpz.watchioiptv.domain.repository.XtreamAccountMetadata
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeId
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeState
import com.iamskorpz.watchioiptv.ui.theme.WatchioAppearanceCodec
import com.iamskorpz.watchioiptv.ui.theme.WatchioAppearanceLibrary
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeDefinition
import com.iamskorpz.watchioiptv.ui.theme.WATCHIO_DEFAULT_THEME_ID
import com.iamskorpz.watchioiptv.ui.theme.WatchioBuiltInThemes
import com.iamskorpz.watchioiptv.ui.theme.toAppearanceLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

class WatchioSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val selectedProviderId: Flow<ProviderId?> = dataStore.data.map { preferences ->
        preferences[SelectedProviderId]?.let(::ProviderId)
    }

    val themeJson: Flow<String?> = dataStore.data.map { preferences ->
        preferences[ThemeJson]
    }

    val appearanceLibrary: Flow<WatchioAppearanceLibrary> = dataStore.data.map { preferences ->
        preferences[AppearanceJson]?.let(WatchioAppearanceCodec::decode)
            ?: legacyLibrary(preferences[ThemeJson])
    }

    val activeAppearance: Flow<WatchioThemeDefinition> = appearanceLibrary.map { it.activeTheme() }

    val theme: Flow<WatchioThemeState> = activeAppearance.map(WatchioThemeState::fromDefinition)

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
            autoPlayNextEpisode = preferences[PlayerAutoPlayNextEpisode] ?: true,
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
        saveAppearanceLibrary(legacyLibrary(theme.id.persisted))
    }

    suspend fun saveAppearanceLibrary(library: WatchioAppearanceLibrary) {
        dataStore.edit { preferences -> preferences[AppearanceJson] = WatchioAppearanceCodec.encode(library) }
    }

    suspend fun applyTheme(theme: WatchioThemeDefinition) {
        val normalized = theme.normalized()
        val current = appearanceLibrary.first()
        val themes = if (normalized.isBuiltIn) current.themes else current.themes.filterNot { it.id == normalized.id } + normalized
        saveAppearanceLibrary(current.copy(activeThemeId = normalized.id, themes = themes))
    }

    suspend fun createTheme(name: String): WatchioThemeDefinition {
        val theme = appearanceLibrary.first().activeTheme().duplicate(name)
        applyTheme(theme)
        return theme
    }

    suspend fun renameTheme(id: String, name: String) = updateLibrary { library ->
        if (WatchioBuiltInThemes.byId(id) != null) library
        else library.copy(themes = library.themes.map { if (it.id == id) it.copy(name = name).normalized() else it })
    }

    suspend fun duplicateTheme(id: String): WatchioThemeDefinition {
        val library = appearanceLibrary.first()
        val source = WatchioBuiltInThemes.byId(id) ?: library.themes.first { it.id == id }
        val duplicate = source.duplicate()
        saveAppearanceLibrary(library.copy(activeThemeId = duplicate.id, themes = library.themes + duplicate))
        return duplicate
    }

    suspend fun deleteTheme(id: String): Boolean {
        if (WatchioBuiltInThemes.byId(id) != null) return false
        updateLibrary { library ->
            val remaining = library.themes.filterNot { it.id == id }
            library.copy(
                themes = remaining,
                activeThemeId = if (library.activeThemeId == id) WATCHIO_DEFAULT_THEME_ID else library.activeThemeId,
            )
        }
        return true
    }

    suspend fun resetAppearance() = saveAppearanceLibrary(WatchioAppearanceLibrary.Default)

    private suspend fun updateLibrary(block: (WatchioAppearanceLibrary) -> WatchioAppearanceLibrary) {
        saveAppearanceLibrary(block(appearanceLibrary.first()).normalized())
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

    override suspend fun setAutoPlayNextEpisode(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PlayerAutoPlayNextEpisode] = enabled }
    }

    override suspend fun setAutoPlayLiveChannel(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PlayerAutoPlayLiveChannel] = enabled }
    }

    override suspend fun setRememberLastLiveChannel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PlayerRememberLastLiveChannel] = enabled
            if (!enabled) {
                preferences.asMap().keys
                    .filter { it.name.startsWith("provider_") && it.name.contains("_last_live_") }
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

    override fun observeLiveBrowsingState(providerId: ProviderId): Flow<LiveTvBrowsingState> =
        dataStore.data.map { preferences ->
            LiveTvBrowsingState(
                categoryId = preferences[lastLiveCategoryIdKey(providerId)],
                categoryName = preferences[lastLiveCategoryNameKey(providerId)],
                channelId = preferences[lastLiveChannelKey(providerId)],
                channelName = preferences[lastLiveChannelNameKey(providerId)],
                channelIndex = preferences[lastLiveChannelIndexKey(providerId)],
                scrollIndex = preferences[lastLiveScrollIndexKey(providerId)],
                scrollOffset = preferences[lastLiveScrollOffsetKey(providerId)],
            )
        }

    override suspend fun saveLiveBrowsingState(providerId: ProviderId, state: LiveTvBrowsingState) {
        dataStore.edit { preferences ->
            preferences.setOrRemove(lastLiveCategoryIdKey(providerId), state.categoryId)
            preferences.setOrRemove(lastLiveCategoryNameKey(providerId), state.categoryName)
            preferences.setOrRemove(lastLiveChannelKey(providerId), state.channelId)
            preferences.setOrRemove(lastLiveChannelNameKey(providerId), state.channelName)
            preferences.setOrRemoveInt(lastLiveChannelIndexKey(providerId), state.channelIndex)
            preferences.setOrRemoveInt(lastLiveScrollIndexKey(providerId), state.scrollIndex)
            preferences.setOrRemoveInt(lastLiveScrollOffsetKey(providerId), state.scrollOffset)
        }
    }

    private companion object {
        val SelectedProviderId = stringPreferencesKey("selected_provider_id")
        val ThemeJson = stringPreferencesKey("theme_json")
        val AppearanceJson = stringPreferencesKey("appearance_json_v1")
        val InputModeKey = stringPreferencesKey("input_mode")
        val StreamFormatKey = stringPreferencesKey("stream_format")
        val DeviceModeOnboardingCompleted = booleanPreferencesKey("device_mode_onboarding_completed")
        val EpgAutoRefreshEnabled = booleanPreferencesKey("epg_auto_refresh_enabled")
        val EpgRefreshIntervalKey = stringPreferencesKey("epg_refresh_interval")
        val ResumePlaybackEnabled = booleanPreferencesKey("resume_playback_enabled")
        val PlayerAutoResume = booleanPreferencesKey("player_auto_resume")
        val PlayerAutoPlayNextEpisode = booleanPreferencesKey("player_auto_play_next_episode")
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
        fun lastLiveCategoryIdKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_last_live_category_id")
        fun lastLiveCategoryNameKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_last_live_category_name")
        fun lastLiveChannelKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_last_live_channel_id")
        fun lastLiveChannelNameKey(providerId: ProviderId) = stringPreferencesKey("provider_${providerId.value}_last_live_channel_name")
        fun lastLiveChannelIndexKey(providerId: ProviderId) = intPreferencesKey("provider_${providerId.value}_last_live_channel_index")
        fun lastLiveScrollIndexKey(providerId: ProviderId) = intPreferencesKey("provider_${providerId.value}_last_live_scroll_index")
        fun lastLiveScrollOffsetKey(providerId: ProviderId) = intPreferencesKey("provider_${providerId.value}_last_live_scroll_offset")
    }

    private fun legacyLibrary(value: String?): WatchioAppearanceLibrary {
        val legacy = WatchioThemeState.fromId(WatchioThemeId.fromPersisted(value))
        if (legacy.id == WatchioThemeId.WatchioDefault) return WatchioAppearanceLibrary.Default
        val custom = WatchioThemeDefinition.WatchioDefault.copy(
            id = "legacy-${legacy.id.persisted}",
            name = legacy.id.label,
            colors = WatchioThemeDefinition.WatchioDefault.colors.copy(
                appBackground = legacy.surfaceBase.toAppearanceLong(),
                primaryPanel = legacy.surfaceCard.toAppearanceLong(),
                secondaryPanel = legacy.surfaceElevated.toAppearanceLong(),
                popup = legacy.surfaceStatus.toAppearanceLong(),
                primaryText = legacy.textPrimary.toAppearanceLong(),
                secondaryText = legacy.textSecondary.toAppearanceLong(),
                mutedText = legacy.textMuted.toAppearanceLong(),
                accent = legacy.liveTvAccent.toAppearanceLong(),
                selectedButton = legacy.liveTvAccentBright.toAppearanceLong(),
                buttonOutline = legacy.liveTvAccentDim.toAppearanceLong(),
                selectedCardOutline = legacy.moviesAccent.toAppearanceLong(),
                focusGlow = legacy.focusGlow.toAppearanceLong(),
                focusOutline = legacy.focusBorder.toAppearanceLong(),
            ),
        )
        return WatchioAppearanceLibrary(activeThemeId = custom.id, themes = listOf(custom))
    }
}

private fun MutablePreferences.setOrRemove(key: Preferences.Key<String>, value: String?) {
    val cleaned = value?.trim()?.takeIf { it.isNotBlank() }
    if (cleaned == null) remove(key) else this[key] = cleaned
}

private fun MutablePreferences.setOrRemoveInt(key: Preferences.Key<Int>, value: Int?) {
    if (value == null || value < 0) remove(key) else this[key] = value
}
