package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.feature.bootstrap.BootstrapDestination
import com.watchioiptv.nativeapp.feature.bootstrap.BootstrapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BootstrapViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun existingProviderWithoutOnboardingFlagMigratesToReady() = runTest(dispatcher) {
        val providerId = ProviderId("provider-a")
        val providers = FakeProviderRepository(
            WatchioProvider(providerId, "Provider A", ProviderType.Xtream, "http://example.invalid", 1, 1, null, true),
        )
        val settings = FakeSettingsRepository()
        val viewModel = BootstrapViewModel(providers, settings)
        backgroundScope.launch { viewModel.destination.collect {} }

        advanceUntilIdle()

        assertEquals(BootstrapDestination.Ready, viewModel.destination.value)
        assertEquals(true, settings.deviceModeOnboardingCompleted.value)
        assertEquals(providerId, settings.selectedProviderId.value)
    }

    @Test
    fun firstRunChoosesTouchThenNeedsXtreamLogin() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = BootstrapViewModel(FakeProviderRepository(), settings)
        backgroundScope.launch { viewModel.destination.collect {} }

        advanceUntilIdle()
        assertEquals(BootstrapDestination.NeedsDeviceMode, viewModel.destination.value)

        viewModel.chooseMobile()
        advanceUntilIdle()

        assertEquals(InputMode.Touch, settings.inputMode.value)
        assertEquals(true, settings.deviceModeOnboardingCompleted.value)
        assertEquals(BootstrapDestination.NeedsXtreamLogin, viewModel.destination.value)
    }

    @Test
    fun firstRunChoosesTvThenNeedsXtreamLogin() = runTest(dispatcher) {
        val settings = FakeSettingsRepository()
        val viewModel = BootstrapViewModel(FakeProviderRepository(), settings)
        backgroundScope.launch { viewModel.destination.collect {} }

        viewModel.chooseTv()
        advanceUntilIdle()

        assertEquals(InputMode.TvRemote, settings.inputMode.value)
        assertEquals(true, settings.deviceModeOnboardingCompleted.value)
        assertEquals(BootstrapDestination.NeedsXtreamLogin, viewModel.destination.value)
    }

    @Test
    fun deviceModeSelectedWithoutProvidersNeedsXtreamLogin() = runTest(dispatcher) {
        val settings = FakeSettingsRepository().apply {
            deviceModeOnboardingCompleted.value = true
        }
        val viewModel = BootstrapViewModel(FakeProviderRepository(), settings)
        backgroundScope.launch { viewModel.destination.collect {} }

        advanceUntilIdle()

        assertEquals(BootstrapDestination.NeedsXtreamLogin, viewModel.destination.value)
    }

    @Test
    fun m3uProviderDoesNotUnlockHome() = runTest(dispatcher) {
        val settings = FakeSettingsRepository().apply {
            deviceModeOnboardingCompleted.value = true
        }
        val viewModel = BootstrapViewModel(
            FakeProviderRepository(
                WatchioProvider(ProviderId("m3u"), "M3U", ProviderType.M3uUrl, "http://example.invalid/list.m3u", 1, 1, null, true),
            ),
            settings,
        )
        backgroundScope.launch { viewModel.destination.collect {} }

        advanceUntilIdle()

        assertEquals(BootstrapDestination.NeedsXtreamLogin, viewModel.destination.value)
    }

    @Test
    fun staleSelectedProviderRecoversToAvailableXtreamProvider() = runTest(dispatcher) {
        val xtream = ProviderId("xtream")
        val settings = FakeSettingsRepository().apply {
            selectedProviderId.value = ProviderId("missing")
            deviceModeOnboardingCompleted.value = true
        }
        val viewModel = BootstrapViewModel(
            FakeProviderRepository(
                WatchioProvider(ProviderId("m3u"), "M3U", ProviderType.M3uUrl, "http://example.invalid/list.m3u", 1, 1, null, true),
                WatchioProvider(xtream, "Xtream", ProviderType.Xtream, "http://example.invalid", 1, 1, null, true),
            ),
            settings,
        )
        backgroundScope.launch { viewModel.destination.collect {} }

        advanceUntilIdle()

        assertEquals(BootstrapDestination.Ready, viewModel.destination.value)
        assertEquals(xtream, settings.selectedProviderId.value)
    }
}

private class FakeProviderRepository(vararg initial: WatchioProvider) : ProviderRepository {
    private val providers = MutableStateFlow(initial.toList())
    override fun observeProviders() = providers
    override suspend fun getProviders(): List<WatchioProvider> = providers.value
    override suspend fun getProvider(providerId: ProviderId): WatchioProvider? = providers.value.firstOrNull { it.id == providerId }
    override suspend fun saveProvider(provider: WatchioProvider) {
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }
    override suspend fun deleteProvider(providerId: ProviderId) {
        providers.value = providers.value.filterNot { it.id == providerId }
    }
}

private class FakeSettingsRepository : SettingsRepository {
    override val selectedProviderId = MutableStateFlow<ProviderId?>(null)
    override val inputMode = MutableStateFlow(InputMode.Auto)
    override val streamFormat = MutableStateFlow(StreamFormat.Auto)
    override val deviceModeOnboardingCompleted = MutableStateFlow(false)

    override suspend fun setSelectedProviderId(providerId: ProviderId?) {
        selectedProviderId.value = providerId
    }

    override suspend fun setInputMode(inputMode: InputMode) {
        this.inputMode.value = inputMode
    }

    override suspend fun setStreamFormat(streamFormat: StreamFormat) {
        this.streamFormat.value = streamFormat
    }

    override suspend fun setDeviceModeOnboardingCompleted(completed: Boolean) {
        deviceModeOnboardingCompleted.value = completed
    }
}
