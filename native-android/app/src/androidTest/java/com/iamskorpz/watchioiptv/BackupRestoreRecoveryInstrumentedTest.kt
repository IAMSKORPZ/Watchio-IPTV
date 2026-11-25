package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.database.ProviderEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.security.AndroidSecretStore
import com.iamskorpz.watchioiptv.feature.backup.BackupProvider
import com.iamskorpz.watchioiptv.feature.backup.BackupRecoveryStatus
import com.iamskorpz.watchioiptv.feature.backup.BackupValue
import com.iamskorpz.watchioiptv.feature.backup.RestoreBoundary
import com.iamskorpz.watchioiptv.feature.backup.RestoreFailureInjector
import com.iamskorpz.watchioiptv.feature.backup.RestoreJournalStore
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupPayload
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreRecoveryInstrumentedTest {
    @Test fun everyPreCommitBoundaryPreservesOrRecoversOriginalState() = runBlocking {
        val boundaries = listOf(
            RestoreBoundary.BEFORE_RECOVERY_POINT,
            RestoreBoundary.AFTER_JOURNAL,
            RestoreBoundary.DURING_RECOVERY_POINT,
            RestoreBoundary.AFTER_RECOVERY_POINT,
            RestoreBoundary.BEFORE_DATABASE,
            RestoreBoundary.DURING_DATABASE,
            RestoreBoundary.AFTER_DATABASE,
            RestoreBoundary.BEFORE_PREFERENCES,
            RestoreBoundary.DURING_PREFERENCES,
            RestoreBoundary.AFTER_PREFERENCES,
            RestoreBoundary.BEFORE_CREDENTIALS,
            RestoreBoundary.DURING_CREDENTIALS,
            RestoreBoundary.AFTER_CREDENTIALS,
            RestoreBoundary.BEFORE_VERIFICATION,
            RestoreBoundary.AFTER_VERIFICATION,
        )
        boundaries.forEach { boundary ->
            val harness = Harness("boundary-${boundary.name.lowercase()}")
            try {
                harness.seedOriginal()
                harness.injector.boundary = boundary
                assertNotNull(runCatching { harness.repository.restore(harness.target()) }.exceptionOrNull())
                harness.assertOriginal()
                assertNull(harness.journal.read())
            } finally {
                harness.close()
            }
        }
    }

    @Test fun failureAfterDatabaseCommitRollsBackEveryStore() = runBlocking {
        val harness = Harness("after-database")
        try {
            harness.seedOriginal()
            harness.injector.boundary = RestoreBoundary.AFTER_DATABASE

            assertNotNull(runCatching { harness.repository.restore(harness.target()) }.exceptionOrNull())

            harness.assertOriginal()
            assertNull(harness.journal.read())
            assertEquals(BackupRecoveryStatus.READY, harness.repository.recoveryStatus.value)
        } finally {
            harness.close()
        }
    }

    @Test fun interruptedRollbackIsRetriedOnNextRepositoryStartup() = runBlocking {
        val harness = Harness("restart-recovery")
        try {
            harness.seedOriginal()
            harness.injector.boundary = RestoreBoundary.AFTER_DATABASE
            harness.injector.additionalBoundary = RestoreBoundary.DURING_ROLLBACK
            assertNotNull(runCatching { harness.repository.restore(harness.target()) }.exceptionOrNull())
            assertNotNull(harness.journal.read())
            assertEquals(BackupRecoveryStatus.RECOVERY_REQUIRED, harness.repository.recoveryStatus.value)

            harness.injector.boundary = null
            harness.injector.additionalBoundary = null
            val restarted = harness.newRepository()
            restarted.recoverInterruptedRestore()

            harness.assertOriginal()
            assertNull(harness.journal.read())
            assertEquals(BackupRecoveryStatus.READY, restarted.recoveryStatus.value)
        } finally {
            harness.close()
        }
    }

    @Test fun committedRestoreSurvivesCleanupInterruptionAndRestart() = runBlocking {
        val harness = Harness("commit-cleanup")
        try {
            harness.seedOriginal()
            harness.injector.boundary = RestoreBoundary.AFTER_COMMIT
            assertNotNull(runCatching { harness.repository.restore(harness.target()) }.exceptionOrNull())
            assertNotNull(harness.journal.read())

            harness.injector.boundary = null
            val restarted = harness.newRepository()
            restarted.recoverInterruptedRestore()

            assertEquals("Target", harness.database.providerDao().findById(harness.targetProviderId)?.displayName)
            assertEquals("target-setting", harness.dataStore.data.first()[stringPreferencesKey("theme_json")])
            assertEquals("target-secret", harness.secrets.getSecret(harness.targetSecretKey))
            assertNull(harness.journal.read())
        } finally {
            harness.close()
        }
    }

    @Test fun failureBeforeRecoveryPointLeavesOriginalStateUntouched() = runBlocking {
        val harness = Harness("before-recovery")
        try {
            harness.seedOriginal()
            harness.injector.boundary = RestoreBoundary.BEFORE_RECOVERY_POINT
            assertNotNull(runCatching { harness.repository.restore(harness.target()) }.exceptionOrNull())
            harness.assertOriginal()
            assertNull(harness.journal.read())
        } finally {
            harness.close()
        }
    }

    private class OneShotInjector : RestoreFailureInjector {
        var boundary: RestoreBoundary? = null
        var additionalBoundary: RestoreBoundary? = null
        private val fired = mutableSetOf<RestoreBoundary>()

        override fun check(boundary: RestoreBoundary) {
            if (boundary !in fired && (this.boundary == boundary || additionalBoundary == boundary)) {
                fired += boundary
                throw InjectedFailure()
            }
        }
    }

    private class InjectedFailure : RuntimeException("synthetic restore boundary failure")

    private class Harness(private val suffix: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val dataStoreFile = File(context.cacheDir, "backup-recovery-$suffix-${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { dataStoreFile })
        private val secretPreferencesName = "backup-recovery-secrets-$suffix-${UUID.randomUUID()}"
        val secrets = AndroidSecretStore(context, secretPreferencesName)
        val journal = RestoreJournalStore(context)
        val injector = OneShotInjector()
        val originalProviderId = "backup-original-$suffix"
        val targetProviderId = "backup-target-$suffix"
        val originalSecretKey = "backup-test.$suffix.original"
        val targetSecretKey = "backup-test.$suffix.target"
        val repository = newRepository()

        fun newRepository() = WatchioBackupRepository(
            context = context,
            database = database,
            dataStore = dataStore,
            secretStore = secrets,
            journalStore = journal,
            failureInjector = injector,
            now = { 1_000L },
        )

        suspend fun seedOriginal() {
            check(journal.read() == null)
            database.providerDao().upsert(ProviderEntity(originalProviderId, "Original", "Xtream", "https://synthetic.invalid", 1, 1, 1, true))
            dataStore.edit {
                it[stringPreferencesKey("theme_json")] = "original-setting"
                it[stringPreferencesKey("input_mode")] = "tv_remote"
            }
            secrets.putSecret(originalSecretKey, "original-secret")
            secrets.removeSecret(targetSecretKey)
        }

        fun target() = WatchioBackupPayload(
            applicationVersion = "test",
            databaseSchemaVersion = 10,
            createdAtEpochMs = 2,
            includedCategories = listOf("providers", "settings", "credentials"),
            excludedCategories = listOf("offline-media"),
            providers = listOf(BackupProvider(targetProviderId, "Target", "Xtream", "https://synthetic.invalid", 2, 2, true, 2)),
            favorites = emptyList(),
            history = emptyList(),
            epgSources = emptyList(),
            dataStore = mapOf("theme_json" to BackupValue("string", "target-setting")),
            sharedPreferences = emptyMap(),
            encryptedCredentials = mapOf(targetSecretKey to "target-secret"),
        )

        suspend fun assertOriginal() {
            assertEquals("Original", database.providerDao().findById(originalProviderId)?.displayName)
            assertNull(database.providerDao().findById(targetProviderId))
            assertEquals("original-setting", dataStore.data.first()[stringPreferencesKey("theme_json")])
            assertEquals("tv_remote", dataStore.data.first()[stringPreferencesKey("input_mode")])
            assertEquals("original-secret", secrets.getSecret(originalSecretKey))
            assertNull(secrets.getSecret(targetSecretKey))
        }

        suspend fun close() {
            journal.read()?.let {
                secrets.removeSecret(it.recoveryKeyAlias)
                journal.cleanup(it)
            }
            secrets.removeSecret(originalSecretKey)
            secrets.removeSecret(targetSecretKey)
            WatchioBackupRepository.PORTABLE_PREFERENCES.forEach {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
            }
            database.close()
            scope.cancel()
            dataStoreFile.delete()
            context.deleteSharedPreferences(secretPreferencesName)
            assertFalse(dataStoreFile.exists())
        }
    }
}
