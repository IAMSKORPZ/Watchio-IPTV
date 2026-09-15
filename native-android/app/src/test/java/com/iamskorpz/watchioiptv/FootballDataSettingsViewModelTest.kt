package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.settings.FootballDataConnectionStatus
import com.iamskorpz.watchioiptv.feature.settings.FootballDataSettingsViewModel
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialStore
import com.iamskorpz.watchioiptv.feature.sports.FootballDataCredentialValidator
import com.iamskorpz.watchioiptv.feature.sports.FootballDataValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FootballDataSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun validTrimmedKeyPersistsAndInvalidatesCache() = runTest(dispatcher) {
        val store = FakeStore()
        val validator = FakeValidator(FootballDataValidationResult.Valid)
        var invalidations = 0
        val viewModel = FootballDataSettingsViewModel(store, validator) { invalidations++ }
        viewModel.updateInput("  test-token  ")
        viewModel.validateAndSave()
        advanceUntilIdle()
        assertEquals("test-token", validator.received)
        assertEquals("test-token", store.value)
        assertEquals(FootballDataConnectionStatus.Connected, viewModel.state.value.status)
        assertEquals("", viewModel.state.value.input)
        assertEquals(1, invalidations)
    }

    @Test fun invalidReplacementRetainsExistingKey() = runTest(dispatcher) {
        val store = FakeStore("working-token")
        val viewModel = FootballDataSettingsViewModel(store, FakeValidator(FootballDataValidationResult.Invalid)) {}
        advanceUntilIdle()
        viewModel.updateInput("mistyped-token")
        viewModel.validateAndSave()
        advanceUntilIdle()
        assertEquals("working-token", store.value)
        assertEquals(FootballDataConnectionStatus.Invalid, viewModel.state.value.status)
    }

    @Test fun blankKeyIsRejectedWithoutValidation() = runTest(dispatcher) {
        val validator = FakeValidator(FootballDataValidationResult.Valid)
        val viewModel = FootballDataSettingsViewModel(FakeStore(), validator) {}
        viewModel.updateInput("   ")
        viewModel.validateAndSave()
        advanceUntilIdle()
        assertNull(validator.received)
        assertFalse(viewModel.state.value.configured)
    }

    @Test fun rateLimitIsNotReportedAsInvalidAndDoesNotSave() = runTest(dispatcher) {
        val store = FakeStore()
        val viewModel = FootballDataSettingsViewModel(store, FakeValidator(FootballDataValidationResult.RateLimited)) {}
        viewModel.updateInput("test-token")
        viewModel.validateAndSave()
        advanceUntilIdle()
        assertNull(store.value)
        assertEquals(FootballDataConnectionStatus.RateLimited, viewModel.state.value.status)
    }

    @Test fun networkFailureDoesNotSave() = runTest(dispatcher) {
        val store = FakeStore()
        val viewModel = FootballDataSettingsViewModel(store, FakeValidator(FootballDataValidationResult.NetworkError)) {}
        viewModel.updateInput("test-token")
        viewModel.validateAndSave()
        advanceUntilIdle()
        assertNull(store.value)
        assertEquals(FootballDataConnectionStatus.UnableToVerify, viewModel.state.value.status)
    }

    @Test fun removalRequiresConfirmationAndOnlyRemovesSportsCredential() = runTest(dispatcher) {
        val store = FakeStore("working-token")
        var invalidations = 0
        val viewModel = FootballDataSettingsViewModel(store, FakeValidator(FootballDataValidationResult.Valid)) { invalidations++ }
        advanceUntilIdle()
        viewModel.requestRemove()
        assertTrue(viewModel.state.value.removeConfirmationVisible)
        assertEquals("working-token", store.value)
        viewModel.confirmRemove()
        advanceUntilIdle()
        assertNull(store.value)
        assertFalse(viewModel.state.value.configured)
        assertEquals(1, invalidations)
    }

    private class FakeStore(initial: String? = null) : FootballDataCredentialStore {
        var value: String? = initial
        override suspend fun get() = value
        override suspend fun save(value: String) { this.value = value }
        override suspend fun remove() { value = null }
    }

    private class FakeValidator(private val result: FootballDataValidationResult) : FootballDataCredentialValidator {
        var received: String? = null
        override suspend fun validate(apiKey: String): FootballDataValidationResult {
            received = apiKey
            return result
        }
    }
}
