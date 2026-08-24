package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.domain.repository.XtreamAccountMetadata
import com.watchioiptv.nativeapp.feature.settings.AccountInformationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountInformationViewModelTest {
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
    fun selectedXtreamProviderShowsSafeMetadata() = runTest(dispatcher) {
        val providerId = ProviderId("provider-a")
        val provider = WatchioProvider(providerId, "Admin Provider", ProviderType.Xtream, "http://example.invalid", 1_000L, 2_000L, 3_000L, true)
        val settings = FakeAccountSettings(providerId).apply {
            expiry[providerId] = MutableStateFlow(4_000L)
            metadata[providerId] = MutableStateFlow(XtreamAccountMetadata(status = "Active", maxConnections = "2", activeConnections = "1", allowedOutputFormats = listOf("m3u8", "ts")))
            section(providerId, ContentType.Live).value = 5_000L
            section(providerId, ContentType.Movie).value = 6_000L
            section(providerId, ContentType.Series).value = 7_000L
        }
        val credentials = ProviderCredentialStore(FakeSecretStore())
        credentials.saveXtreamCredentials(providerId.value, XtreamCredentials("admin", "private-password"))

        val viewModel = AccountInformationViewModel(
            providerRepository = FakeAccountProviderRepository(provider),
            settingsRepository = settings,
            credentialStore = credentials,
            nowEpochMs = { 3_500L },
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Admin Provider", state.providerName)
        assertEquals("a***n", state.username)
        assertEquals("Active", state.accountStatus)
        assertEquals(4_000L, state.expirationEpochMs)
        assertEquals("Xtream Codes", state.providerType)
        assertEquals(5_000L, state.liveRefreshAtEpochMs)
        assertEquals(6_000L, state.moviesRefreshAtEpochMs)
        assertEquals(7_000L, state.seriesRefreshAtEpochMs)
        assertEquals("2", state.maximumConnections)
        assertEquals("1", state.activeConnections)
        assertEquals("HLS, TS", state.outputFormats)
        assertFalse(state.username.contains("private-password"))
    }

    @Test
    fun missingOptionalDataFallsBackSafely() = runTest(dispatcher) {
        val providerId = ProviderId("provider-a")
        val provider = WatchioProvider(providerId, "Provider", ProviderType.Xtream, "http://example.invalid", 1_000L, 2_000L, null, true)
        val viewModel = AccountInformationViewModel(
            providerRepository = FakeAccountProviderRepository(provider),
            settingsRepository = FakeAccountSettings(providerId),
            credentialStore = ProviderCredentialStore(FakeSecretStore()),
            nowEpochMs = { 3_500L },
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Unknown", state.accountStatus)
        assertEquals(null, state.expirationEpochMs)
        assertEquals("Not available", state.username)
        assertEquals("Not available", state.outputFormats)
    }

    @Test
    fun selectedProviderChangesDoNotLeakPreviousProvider() = runTest(dispatcher) {
        val providerA = WatchioProvider(ProviderId("a"), "Provider A", ProviderType.Xtream, "http://a.example.invalid", 1L, 1L, null, true)
        val providerB = WatchioProvider(ProviderId("b"), "Provider B", ProviderType.Xtream, "http://b.example.invalid", 2L, 2L, null, true)
        val settings = FakeAccountSettings(providerA.id)
        val viewModel = AccountInformationViewModel(
            providerRepository = FakeAccountProviderRepository(providerA, providerB),
            settingsRepository = settings,
            credentialStore = ProviderCredentialStore(FakeSecretStore()),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        assertEquals("Provider A", viewModel.state.value.providerName)

        settings.selectedProviderId.value = providerB.id
        advanceUntilIdle()

        assertEquals("Provider B", viewModel.state.value.providerName)
    }
}

private class FakeAccountProviderRepository(vararg initial: WatchioProvider) : ProviderRepository {
    private val providers = MutableStateFlow(initial.toList())
    override fun observeProviders(): Flow<List<WatchioProvider>> = providers
    override suspend fun getProviders(): List<WatchioProvider> = providers.value
    override suspend fun getProvider(providerId: ProviderId): WatchioProvider? = providers.value.firstOrNull { it.id == providerId }
    override suspend fun saveProvider(provider: WatchioProvider) {
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }
    override suspend fun deleteProvider(providerId: ProviderId) {
        providers.value = providers.value.filterNot { it.id == providerId }
    }
}

private class FakeAccountSettings(initialProvider: ProviderId?) : SettingsRepository {
    override val selectedProviderId = MutableStateFlow(initialProvider)
    override val inputMode = MutableStateFlow(InputMode.Auto)
    override val streamFormat = MutableStateFlow(StreamFormat.Auto)
    val expiry = mutableMapOf<ProviderId, MutableStateFlow<Long?>>()
    val metadata = mutableMapOf<ProviderId, MutableStateFlow<XtreamAccountMetadata>>()
    private val refreshes = mutableMapOf<Pair<ProviderId, ContentType>, MutableStateFlow<Long?>>()

    override suspend fun setSelectedProviderId(providerId: ProviderId?) {
        selectedProviderId.value = providerId
    }

    override suspend fun setInputMode(inputMode: InputMode) {
        this.inputMode.value = inputMode
    }

    override suspend fun setStreamFormat(streamFormat: StreamFormat) {
        this.streamFormat.value = streamFormat
    }

    override fun observeProviderExpiryEpochMs(providerId: ProviderId): Flow<Long?> =
        expiry.getOrPut(providerId) { MutableStateFlow(null) }

    override fun observeSectionRefreshEpochMs(providerId: ProviderId, contentType: ContentType): Flow<Long?> =
        section(providerId, contentType)

    override fun observeXtreamAccountMetadata(providerId: ProviderId): Flow<XtreamAccountMetadata> =
        metadata.getOrPut(providerId) { MutableStateFlow(XtreamAccountMetadata()) }

    fun section(providerId: ProviderId, contentType: ContentType): MutableStateFlow<Long?> =
        refreshes.getOrPut(providerId to contentType) { MutableStateFlow(null) }
}

private class FakeSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun putSecret(key: String, value: String) {
        values[key] = value
    }
    override suspend fun getSecret(key: String): String? = values[key]
    override suspend fun removeSecret(key: String) {
        values.remove(key)
    }
}
