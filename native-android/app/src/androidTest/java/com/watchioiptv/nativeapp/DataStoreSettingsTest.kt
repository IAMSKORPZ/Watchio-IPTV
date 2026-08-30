package com.watchioiptv.nativeapp

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watchioiptv.nativeapp.core.datastore.WatchioSettingsRepository
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.epg.EpgRefreshInterval
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.repository.ControlAutoHideDelay
import com.watchioiptv.nativeapp.domain.repository.LiveTvBrowsingState
import com.watchioiptv.nativeapp.domain.repository.VideoScalingMode
import com.watchioiptv.nativeapp.domain.repository.XtreamAccountMetadata
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeId
import com.watchioiptv.nativeapp.ui.theme.WatchioThemeState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsTest {
    private lateinit var scope: CoroutineScope
    private lateinit var file: File
    private lateinit var repository: WatchioSettingsRepository

    @Before
    fun createStore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val directory = context.getDir("watchio_datastore_tests", Context.MODE_PRIVATE)
        file = File(directory, "watchio_test_settings_${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = WatchioSettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { file },
        )
    }

    @After
    fun cleanup() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun selectedProviderAndDefaultsRoundTrip() = runBlocking {
        assertNull(repository.selectedProviderId.first())
        assertEquals(InputMode.Auto, repository.inputMode.first())
        assertEquals(StreamFormat.Auto, repository.streamFormat.first())

        repository.setSelectedProviderId(ProviderId("provider-a"))
        repository.setInputMode(InputMode.TvRemote)
        repository.setStreamFormat(StreamFormat.Hls)

        assertEquals(ProviderId("provider-a"), repository.selectedProviderId.first())
        assertEquals(InputMode.TvRemote, repository.inputMode.first())
        assertEquals(StreamFormat.Hls, repository.streamFormat.first())
    }

    @Test
    fun themeSelectionPersists() = runBlocking {
        assertNull(repository.themeJson.first())
        assertEquals(WatchioThemeId.WatchioDefault, repository.theme.first().id)

        repository.setTheme(WatchioThemeState.fromId(WatchioThemeId.Purple))

        assertEquals("purple", repository.themeJson.first())
        assertEquals(WatchioThemeId.Purple, repository.theme.first().id)

        repository.setTheme(WatchioThemeState.fromId(WatchioThemeId.Blue))

        assertEquals("blue", repository.themeJson.first())
        assertEquals(WatchioThemeId.Blue, repository.theme.first().id)

        repository.setTheme(WatchioThemeState.fromId(WatchioThemeId.WatchioDefault))

        assertEquals("watchio-default", repository.themeJson.first())
        assertEquals(WatchioThemeId.WatchioDefault, repository.theme.first().id)
    }

    @Test
    fun invalidStoredThemeFallsBackToWatchioDefault() = runBlocking {
        repository.setThemeJson("legacy-custom-theme")

        assertEquals(WatchioThemeId.WatchioDefault, repository.theme.first().id)
    }

    @Test
    fun epgRefreshDefaultsAndIntervalPersist() = runBlocking {
        assertEquals(true, repository.epgAutoRefreshEnabled.first())
        assertEquals(EpgRefreshInterval.ThreeDays, repository.epgRefreshInterval.first())

        repository.setEpgAutoRefreshEnabled(false)
        repository.setEpgRefreshInterval(EpgRefreshInterval.OneDay)

        assertEquals(false, repository.epgAutoRefreshEnabled.first())
        assertEquals(EpgRefreshInterval.OneDay, repository.epgRefreshInterval.first())
    }

    @Test
    fun xtreamAccountMetadataIsProviderScoped() = runBlocking {
        val providerA = ProviderId("provider-a")
        val providerB = ProviderId("provider-b")

        repository.setXtreamAccountMetadata(providerA, XtreamAccountMetadata(status = "Active", maxConnections = "2", activeConnections = "1", allowedOutputFormats = listOf("m3u8", "ts")))

        assertEquals("2", repository.observeXtreamAccountMetadata(providerA).first().maxConnections)
        assertEquals("1", repository.observeXtreamAccountMetadata(providerA).first().activeConnections)
        assertEquals(listOf("m3u8", "ts"), repository.observeXtreamAccountMetadata(providerA).first().allowedOutputFormats)
        assertEquals(null, repository.observeXtreamAccountMetadata(providerB).first().maxConnections)
    }

    @Test
    fun playerSettingsDefaultsAndValuesPersist() = runBlocking {
        val defaults = repository.playerSettings.first()
        assertEquals(true, defaults.autoResume)
        assertEquals(false, defaults.autoPlayLiveChannel)
        assertEquals(true, defaults.rememberLastLiveChannel)
        assertEquals(true, defaults.showPlayerControls)
        assertEquals(ControlAutoHideDelay.FiveSeconds, defaults.controlAutoHideDelay)
        assertEquals(true, defaults.autoRetryStreams)
        assertEquals(2, defaults.retryAttempts)
        assertEquals(VideoScalingMode.Fit, defaults.videoScalingMode)

        repository.setAutoResume(false)
        repository.setAutoPlayLiveChannel(true)
        repository.setRememberLastLiveChannel(false)
        repository.setShowPlayerControls(false)
        repository.setControlAutoHideDelay(ControlAutoHideDelay.Never)
        repository.setAutoRetryStreams(false)
        repository.setRetryAttempts(3)
        repository.setVideoScalingMode(VideoScalingMode.Zoom)

        val updated = repository.playerSettings.first()
        assertEquals(false, updated.autoResume)
        assertEquals(true, updated.autoPlayLiveChannel)
        assertEquals(false, updated.rememberLastLiveChannel)
        assertEquals(false, updated.showPlayerControls)
        assertEquals(ControlAutoHideDelay.Never, updated.controlAutoHideDelay)
        assertEquals(false, updated.autoRetryStreams)
        assertEquals(3, updated.retryAttempts)
        assertEquals(VideoScalingMode.Zoom, updated.videoScalingMode)
    }

    @Test
    fun lastLiveChannelIsProviderScopedAndClearsWhenRememberDisabled() = runBlocking {
        val providerA = ProviderId("provider-a")
        val providerB = ProviderId("provider-b")

        repository.setLastLiveChannelId(providerA, "channel-a")

        assertEquals("channel-a", repository.observeLastLiveChannelId(providerA).first())
        assertNull(repository.observeLastLiveChannelId(providerB).first())

        repository.setRememberLastLiveChannel(false)

        assertNull(repository.observeLastLiveChannelId(providerA).first())
    }

    @Test
    fun liveBrowsingStateIsProviderScopedAndClearsWhenRememberDisabled() = runBlocking {
        val providerA = ProviderId("provider-a")
        val providerB = ProviderId("provider-b")

        val stateA = LiveTvBrowsingState(
            categoryId = "cat-movies",
            categoryName = "Sky Movies",
            channelId = "ch-comedy",
            channelName = "Sky Cinema Comedy FHD",
            channelIndex = 17,
            scrollIndex = 17,
            scrollOffset = 0,
        )

        repository.saveLiveBrowsingState(providerA, stateA)

        val restoredA = repository.observeLiveBrowsingState(providerA).first()
        assertEquals("cat-movies", restoredA.categoryId)
        assertEquals("Sky Movies", restoredA.categoryName)
        assertEquals("ch-comedy", restoredA.channelId)
        assertEquals("Sky Cinema Comedy FHD", restoredA.channelName)
        assertEquals(17, restoredA.channelIndex)
        assertEquals(17, restoredA.scrollIndex)

        // Verify provider B is completely isolated
        val restoredB = repository.observeLiveBrowsingState(providerB).first()
        assertNull(restoredB.categoryId)
        assertNull(restoredB.channelId)

        // Clear when remember disabled
        repository.setRememberLastLiveChannel(false)

        val clearedA = repository.observeLiveBrowsingState(providerA).first()
        assertNull(clearedA.categoryId)
        assertNull(clearedA.channelId)
        assertNull(clearedA.channelIndex)
    }
}
