package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.feature.sports.SportsDateSchedule
import com.iamskorpz.watchioiptv.feature.sports.SportsLoadState
import com.iamskorpz.watchioiptv.feature.sports.SportsScheduleException
import com.iamskorpz.watchioiptv.feature.sports.SportsViewModel
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun rapidNavigationKeepsLatestSelectedDate() = runTest(dispatcher) {
        val today = LocalDate.of(2026, 9, 6)
        val requests = mutableMapOf<LocalDate, CompletableDeferred<Result<SportsDateSchedule>>>()
        val viewModel = SportsViewModel(Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)) { date ->
            requests.getOrPut(date) { CompletableDeferred() }.await()
        }
        runCurrent()
        viewModel.nextDay()
        runCurrent()
        viewModel.nextDay()
        runCurrent()
        val latest = today.plusDays(2)
        requests.getValue(latest).complete(Result.success(SportsDateSchedule(latest, emptyList())))
        advanceUntilIdle()
        assertEquals(latest, viewModel.state.value.selectedDate)
        assertEquals(latest, (viewModel.state.value.loadState as SportsLoadState.Ready).schedule.date)
    }

    @Test fun rateLimitDisablesRetryUntilCooldownEnds() = runTest(dispatcher) {
        val clock = MutableClock(Instant.parse("2026-09-06T12:00:00Z"))
        var calls = 0
        val retryAt = clock.millis() + 30_000L
        val viewModel = SportsViewModel(clock) { date ->
            calls++
            if (calls == 1) Result.failure(SportsScheduleException.RateLimited(retryAt))
            else Result.success(SportsDateSchedule(date, emptyList()))
        }
        runCurrent()
        val limited = viewModel.state.value.loadState as SportsLoadState.Error
        assertFalse(limited.retryEnabled)
        viewModel.retry()
        runCurrent()
        assertEquals(1, calls)
        clock.advance(30_000L)
        advanceTimeBy(30_000L)
        runCurrent()
        assertTrue((viewModel.state.value.loadState as SportsLoadState.Error).retryEnabled)
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertTrue(viewModel.state.value.loadState is SportsLoadState.Ready)
    }

    @Test fun missingCredentialShowsSetupRequired() = runTest(dispatcher) {
        val viewModel = SportsViewModel(Clock.systemUTC()) { Result.failure(SportsScheduleException.MissingCredential) }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadState is SportsLoadState.SetupRequired)
    }

    @Test fun rejectedStoredCredentialNeedsAttention() = runTest(dispatcher) {
        val viewModel = SportsViewModel(Clock.systemUTC()) { Result.failure(SportsScheduleException.InvalidCredential) }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadState is SportsLoadState.CredentialNeedsAttention)
    }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(milliseconds: Long) { now = now.plusMillis(milliseconds) }
    }
}
