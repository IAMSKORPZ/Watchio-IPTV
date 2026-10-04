package com.iamskorpz.watchioiptv.feature.sports

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.iamskorpz.watchioiptv.MainActivity
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class MatchReminderState { SCHEDULED, TRIGGERED, DISMISSED, CANCELLED, COMPLETED }

@Serializable
data class MatchReminder(
    val fixtureKey: String,
    val source: String,
    val sourceFixtureId: String,
    val competitionId: String,
    val competitionName: String,
    val homeTeam: String,
    val awayTeam: String,
    val kickoffEpochMs: Long,
    val triggerEpochMs: Long,
    val state: MatchReminderState = MatchReminderState.SCHEDULED,
) {
    fun toFixture() = SportsFixture(
        id = sourceFixtureId,
        competitionId = competitionId,
        competitionName = competitionName,
        kickoffUtc = Instant.ofEpochMilli(kickoffEpochMs),
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        status = SportsFixtureStatus.Scheduled,
    )
}

interface SportsReminderScheduler {
    fun schedule(reminder: MatchReminder)
    fun cancel(fixtureKey: String)
}

class WorkManagerSportsReminderScheduler(context: Context) : SportsReminderScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(reminder: MatchReminder) {
        val delay = (reminder.triggerEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val data = Data.Builder()
            .putString(MatchReminderWorker.KEY_ID, reminder.fixtureKey)
            .putString(MatchReminderWorker.KEY_HOME, reminder.homeTeam)
            .putString(MatchReminderWorker.KEY_AWAY, reminder.awayTeam)
            .putString(MatchReminderWorker.KEY_COMPETITION, reminder.competitionName)
            .putLong(MatchReminderWorker.KEY_KICKOFF, reminder.kickoffEpochMs)
            .build()
        val work = OneTimeWorkRequestBuilder<MatchReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        workManager.enqueueUniqueWork(workName(reminder.fixtureKey), ExistingWorkPolicy.REPLACE, work)
    }

    override fun cancel(fixtureKey: String) { workManager.cancelUniqueWork(workName(fixtureKey)) }
    private fun workName(key: String) = "sports-reminder-$key"
}

class SportsReminderRepository(
    private val dataStore: DataStore<Preferences>,
    private val scheduler: SportsReminderScheduler,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    val reminders: Flow<List<MatchReminder>> = dataStore.data.map { preferences -> decode(preferences[REMINDERS]) }

    suspend fun toggle(fixture: SportsFixture): Boolean {
        if (fixture.status != SportsFixtureStatus.Scheduled || fixture.kickoffUtc <= clock.instant()) return false
        var enabled = false
        dataStore.edit { preferences ->
            val rows = decode(preferences[REMINDERS]).toMutableList()
            val key = fixture.reminderKey()
            val existing = rows.indexOfFirst { it.fixtureKey == key && it.state == MatchReminderState.SCHEDULED }
            if (existing >= 0) {
                rows.removeAt(existing)
                scheduler.cancel(key)
            } else {
                val reminder = fixture.toReminder(clock)
                rows.removeAll { it.fixtureKey == key }
                rows += reminder
                scheduler.schedule(reminder)
                enabled = true
            }
            preferences[REMINDERS] = json.encodeToString(ListSerializer(MatchReminder.serializer()), rows)
        }
        return enabled
    }

    suspend fun reconcile(fixtures: List<SportsFixture>) {
        val byKey = fixtures.associateBy(SportsFixture::reminderKey)
        dataStore.edit { preferences ->
            val updated = decode(preferences[REMINDERS]).map { reminder ->
                val fixture = byKey[reminder.fixtureKey] ?: return@map reminder
                when (fixture.status) {
                    SportsFixtureStatus.Postponed, SportsFixtureStatus.Cancelled -> {
                        scheduler.cancel(reminder.fixtureKey)
                        reminder.copy(state = MatchReminderState.CANCELLED)
                    }
                    SportsFixtureStatus.Finished -> {
                        scheduler.cancel(reminder.fixtureKey)
                        reminder.copy(state = MatchReminderState.COMPLETED)
                    }
                    SportsFixtureStatus.Scheduled -> if (
                        fixture.kickoffUtc.toEpochMilli() != reminder.kickoffEpochMs ||
                        reminder.triggerEpochMs != reminderTriggerEpochMs(fixture.kickoffUtc.toEpochMilli(), clock.millis())
                    ) {
                        val moved = fixture.toReminder(clock)
                        scheduler.schedule(moved)
                        moved
                    } else reminder
                    SportsFixtureStatus.Live -> reminder.copy(state = MatchReminderState.TRIGGERED)
                }
            }
            preferences[REMINDERS] = json.encodeToString(ListSerializer(MatchReminder.serializer()), updated)
        }
    }

    suspend fun dismiss(fixtureKey: String) = updateState(fixtureKey, MatchReminderState.DISMISSED)

    private suspend fun updateState(key: String, state: MatchReminderState) {
        dataStore.edit { preferences ->
            val rows = decode(preferences[REMINDERS]).map { if (it.fixtureKey == key) it.copy(state = state) else it }
            preferences[REMINDERS] = json.encodeToString(ListSerializer(MatchReminder.serializer()), rows)
        }
    }

    private fun decode(raw: String?): List<MatchReminder> = raw?.let {
        runCatching { json.decodeFromString(ListSerializer(MatchReminder.serializer()), it) }.getOrDefault(emptyList())
    } ?: emptyList()

    companion object {
        private val REMINDERS = stringPreferencesKey("sports_match_reminders_v1")
        val LEAD_TIME: Duration = Duration.ofMinutes(15)
    }
}

fun SportsFixture.reminderKey(): String {
    val identity = v2Fixture?.identity
    return if (identity != null) "${identity.source.value}:${identity.sourceId}" else "football-data:$id"
}

private fun SportsFixture.toReminder(clock: Clock): MatchReminder {
    val identity = v2Fixture?.identity
    val kickoff = kickoffUtc.toEpochMilli()
    return MatchReminder(
        fixtureKey = reminderKey(),
        source = identity?.source?.value ?: "football-data",
        sourceFixtureId = identity?.sourceId ?: id,
        competitionId = competitionId,
        competitionName = competitionName,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        kickoffEpochMs = kickoff,
        triggerEpochMs = reminderTriggerEpochMs(kickoff, clock.millis()),
    )
}

internal fun reminderTriggerEpochMs(kickoffEpochMs: Long, nowEpochMs: Long): Long =
    (kickoffEpochMs - SportsReminderRepository.LEAD_TIME.toMillis()).coerceAtLeast(nowEpochMs)

class MatchReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        createChannel(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val home = inputData.getString(KEY_HOME).orEmpty()
        val away = inputData.getString(KEY_AWAY).orEmpty()
        val competition = inputData.getString(KEY_COMPETITION).orEmpty()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra("open_sports_fixture", id)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(applicationContext, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(com.iamskorpz.watchioiptv.R.mipmap.ic_launcher)
            .setContentTitle("Match starting soon")
            .setContentText("$home vs $away${if (competition.isBlank()) "" else " • $competition"}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "watchio_sports"
        const val KEY_ID = "fixture_id"
        const val KEY_HOME = "home"
        const val KEY_AWAY = "away"
        const val KEY_COMPETITION = "competition"
        const val KEY_KICKOFF = "kickoff"
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Watchio Sports", NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }
}
