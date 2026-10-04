package com.iamskorpz.watchioiptv

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.iamskorpz.watchioiptv.feature.sports.MatchReminder
import com.iamskorpz.watchioiptv.feature.sports.MatchReminderState
import com.iamskorpz.watchioiptv.feature.sports.SportsFixture
import com.iamskorpz.watchioiptv.feature.sports.SportsFixtureStatus
import com.iamskorpz.watchioiptv.feature.sports.SportsReminderRepository
import com.iamskorpz.watchioiptv.feature.sports.SportsReminderScheduler
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportsReminderRepositoryTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun reminderPersistsAtFifteenMinutesBeforeKickoffAndTogglePreventsDuplicates() = runTest {
        val file = File.createTempFile("sports-reminders", ".preferences_pb").also { it.delete() }
        val scheduler = FakeScheduler()
        val repository = repository(file, scheduler, this)
        val fixture = fixture(now.plusSeconds(3600))

        assertTrue(repository.toggle(fixture))
        val saved = repository.reminders.first()
        assertEquals(1, saved.size)
        assertEquals(fixture.kickoffUtc.minusSeconds(900).toEpochMilli(), saved.single().triggerEpochMs)
        assertFalse(saved.single().fixtureKey.contains("http"))
        assertEquals(1, scheduler.scheduled.size)

        assertFalse(repository.toggle(fixture))
        assertTrue(repository.reminders.first().isEmpty())
        assertEquals(listOf("football-data:42"), scheduler.cancelled)
    }

    @Test fun processReloadReadsPersistedReminder() = runTest {
        val file = File.createTempFile("sports-reminders-reload", ".preferences_pb").also { it.delete() }
        val first = repository(file, FakeScheduler(), this)
        first.toggle(fixture(now.plusSeconds(7200)))
        assertEquals("Arsenal", first.reminders.first().single().homeTeam)
    }

    @Test fun kickoffMoveReschedulesAndCancelledOrFinishedFixturesSuppressReminder() = runTest {
        val file = File.createTempFile("sports-reminders-reconcile", ".preferences_pb").also { it.delete() }
        val scheduler = FakeScheduler()
        val repository = repository(file, scheduler, this)
        repository.toggle(fixture(now.plusSeconds(3600)))
        repository.reconcile(listOf(fixture(now.plusSeconds(7200))))
        assertEquals(now.plusSeconds(7200).minusSeconds(900).toEpochMilli(), repository.reminders.first().single().triggerEpochMs)
        assertEquals(2, scheduler.scheduled.size)

        repository.reconcile(listOf(fixture(now.plusSeconds(7200), SportsFixtureStatus.Cancelled)))
        assertEquals(MatchReminderState.CANCELLED, repository.reminders.first().single().state)
        assertTrue(scheduler.cancelled.isNotEmpty())
    }

    @Test fun liveAndFinishedFixturesCannotCreateFutureReminder() = runTest {
        val file = File.createTempFile("sports-reminders-status", ".preferences_pb").also { it.delete() }
        val repository = repository(file, FakeScheduler(), this)
        assertFalse(repository.toggle(fixture(now, SportsFixtureStatus.Live)))
        assertFalse(repository.toggle(fixture(now.minusSeconds(60), SportsFixtureStatus.Finished)))
        assertTrue(repository.reminders.first().isEmpty())
    }

    private fun repository(file: File, scheduler: FakeScheduler, scope: TestScope) = SportsReminderRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), scheduler, clock,
    )

    private fun fixture(kickoff: Instant, status: SportsFixtureStatus = SportsFixtureStatus.Scheduled) = SportsFixture(
        "42", "PL", "Premier League", kickoff, "Arsenal", "Chelsea", status,
    )

    private class FakeScheduler : SportsReminderScheduler {
        val scheduled = mutableListOf<MatchReminder>()
        val cancelled = mutableListOf<String>()
        override fun schedule(reminder: MatchReminder) { scheduled += reminder }
        override fun cancel(fixtureKey: String) { cancelled += fixtureKey }
    }
}
