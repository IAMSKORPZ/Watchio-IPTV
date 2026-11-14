package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.xtream.AccountBlockReason
import com.iamskorpz.watchioiptv.data.xtream.AccountValidationResult
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.model.StreamFormat
import com.iamskorpz.watchioiptv.domain.model.WatchioProvider
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import com.iamskorpz.watchioiptv.feature.provider.ProviderRemovalOutcome
import com.iamskorpz.watchioiptv.feature.provider.removeProviderAndResolve
import com.iamskorpz.watchioiptv.feature.provider.visibleProviders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRemovalRoutingTest {
    @Test
    fun lastActiveAccountWithoutPlayableDownloadsRoutesToLogin() = runTest {
        val active = provider("active")
        val providers = FakeProviderRepository(listOf(active))
        val settings = FakeRemovalSettings(active.id)
        var cleanupCalled = false

        val outcome = removeProviderAndResolve(
            active.id,
            providers,
            settings,
            validateAccount = { AccountValidationResult.Active },
            hasPlayableDownloads = { false },
            onActiveProviderRemoval = { cleanupCalled = true },
        )

        assertEquals(ProviderRemovalOutcome.LoginRequired, outcome)
        assertTrue(cleanupCalled)
        assertNull(settings.current.value)
        assertTrue(providers.providers.value.isEmpty())
    }

    @Test
    fun lastActiveAccountWithPlayableDownloadsRoutesToDownloadsOnly() = runTest {
        val active = provider("active")
        val providers = FakeProviderRepository(listOf(active))
        val settings = FakeRemovalSettings(active.id)

        val outcome = removeProviderAndResolve(
            active.id,
            providers,
            settings,
            validateAccount = { AccountValidationResult.Active },
            hasPlayableDownloads = { true },
            onActiveProviderRemoval = {},
        )

        assertEquals(ProviderRemovalOutcome.DownloadsOnly, outcome)
        assertNull(settings.current.value)
    }

    @Test
    fun activeRemovalSkipsBlockedAndRetryableAccountsAndSelectsValidatedAccount() = runTest {
        val active = provider("active")
        val expired = provider("expired")
        val offline = provider("offline")
        val valid = provider("valid")
        val providers = FakeProviderRepository(listOf(active, expired, offline, valid))
        val settings = FakeRemovalSettings(active.id)

        val outcome = removeProviderAndResolve(
            active.id,
            providers,
            settings,
            validateAccount = {
                when (it) {
                    expired.id -> AccountValidationResult.Blocked("Expired", AccountBlockReason.Expired)
                    offline.id -> AccountValidationResult.Retryable("Offline")
                    else -> AccountValidationResult.Active
                }
            },
            hasPlayableDownloads = { false },
            onActiveProviderRemoval = {},
        )

        assertEquals(ProviderRemovalOutcome.ActiveProviderSelected(valid.id), outcome)
        assertEquals(valid.id, settings.current.value)
    }

    @Test
    fun inactiveRemovalPreservesActiveSessionAndDoesNotRunActiveCleanup() = runTest {
        val active = provider("active")
        val inactive = provider("inactive")
        val providers = FakeProviderRepository(listOf(active, inactive))
        val settings = FakeRemovalSettings(active.id)
        var cleanupCalled = false
        var validationCalled = false

        val outcome = removeProviderAndResolve(
            inactive.id,
            providers,
            settings,
            validateAccount = { validationCalled = true; AccountValidationResult.Active },
            hasPlayableDownloads = { false },
            onActiveProviderRemoval = { cleanupCalled = true },
        )

        assertEquals(ProviderRemovalOutcome.InactiveProviderRemoved, outcome)
        assertEquals(active.id, settings.current.value)
        assertFalse(cleanupCalled)
        assertFalse(validationCalled)
    }

    @Test
    fun successfulInactiveRemovalImmediatelyExcludesProviderFromVisibleState() = runTest {
        val active = provider("active")
        val inactive = provider("inactive")
        val providers = FakeProviderRepository(listOf(active, inactive))
        val outcome = removeProviderAndResolve(
            inactive.id,
            providers,
            FakeRemovalSettings(active.id),
            validateAccount = { AccountValidationResult.Active },
            hasPlayableDownloads = { false },
            onActiveProviderRemoval = {},
        )

        assertEquals(ProviderRemovalOutcome.InactiveProviderRemoved, outcome)
        assertEquals(listOf(active), visibleProviders(listOf(active, inactive), setOf(inactive.id)))
    }

    @Test
    fun removalReportsFailureWhenPersistenceStillContainsProvider() = runTest {
        val active = provider("active")
        val providers = FakeProviderRepository(listOf(active), persistDelete = false)

        val result = runCatching {
            removeProviderAndResolve(
                active.id,
                providers,
                FakeRemovalSettings(active.id),
                validateAccount = { AccountValidationResult.Active },
                hasPlayableDownloads = { false },
                onActiveProviderRemoval = {},
            )
        }

        assertTrue(result.isFailure)
        assertEquals(listOf(active), providers.providers.value)
    }

    @Test
    fun repeatedRemovalFailsInsteadOfReportingFalseSuccess() = runTest {
        val inactive = provider("inactive")
        val providers = FakeProviderRepository(listOf(inactive))
        val settings = FakeRemovalSettings(null)

        removeProviderAndResolve(
            inactive.id,
            providers,
            settings,
            validateAccount = { AccountValidationResult.Active },
            hasPlayableDownloads = { false },
            onActiveProviderRemoval = {},
        )

        val repeated = runCatching {
            removeProviderAndResolve(
                inactive.id,
                providers,
                settings,
                validateAccount = { AccountValidationResult.Active },
                hasPlayableDownloads = { false },
                onActiveProviderRemoval = {},
            )
        }

        assertTrue(repeated.isFailure)
    }

    @Test
    fun offlineRemovalFailsClosedToDownloadsWhenNoAccountCanBeValidated() = runTest {
        val active = provider("active")
        val alternate = provider("alternate")
        val providers = FakeProviderRepository(listOf(active, alternate))
        val settings = FakeRemovalSettings(active.id)

        val outcome = removeProviderAndResolve(
            active.id,
            providers,
            settings,
            validateAccount = { AccountValidationResult.Retryable("Offline") },
            hasPlayableDownloads = { true },
            onActiveProviderRemoval = {},
        )

        assertEquals(ProviderRemovalOutcome.DownloadsOnly, outcome)
        assertNull(settings.current.value)
    }

    @Test
    fun failedActiveRemovalRestoresSelectionAndDoesNotRoute() = runTest {
        val active = provider("active")
        val providers = FakeProviderRepository(listOf(active), failDelete = true)
        val settings = FakeRemovalSettings(active.id)

        val result = runCatching {
            removeProviderAndResolve(
                active.id,
                providers,
                settings,
                validateAccount = { AccountValidationResult.Active },
                hasPlayableDownloads = { false },
                onActiveProviderRemoval = {},
            )
        }

        assertTrue(result.isFailure)
        assertEquals(active.id, settings.current.value)
    }

    private fun provider(id: String) = WatchioProvider(
        id = ProviderId(id),
        displayName = id,
        type = ProviderType.Xtream,
        serverUrl = null,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
        lastRefreshAtEpochMs = null,
        enabled = true,
    )

    private class FakeProviderRepository(
        initial: List<WatchioProvider>,
        private val failDelete: Boolean = false,
        private val persistDelete: Boolean = true,
    ) : ProviderRepository {
        val providers = MutableStateFlow(initial)
        override fun observeProviders(): Flow<List<WatchioProvider>> = providers
        override suspend fun getProviders(): List<WatchioProvider> = providers.value
        override suspend fun getProvider(providerId: ProviderId): WatchioProvider? = providers.value.firstOrNull { it.id == providerId }
        override suspend fun saveProvider(provider: WatchioProvider) { providers.value = providers.value + provider }
        override suspend fun deleteProvider(providerId: ProviderId) {
            if (failDelete) error("delete failed")
            if (persistDelete) providers.value = providers.value.filterNot { it.id == providerId }
        }
    }

    private class FakeRemovalSettings(initial: ProviderId?) : SettingsRepository {
        val current = MutableStateFlow(initial)
        override val selectedProviderId: Flow<ProviderId?> = current
        override val inputMode: Flow<InputMode> = MutableStateFlow(InputMode.TvRemote)
        override val streamFormat: Flow<StreamFormat> = MutableStateFlow(StreamFormat.Auto)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) { current.value = providerId }
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
    }
}
