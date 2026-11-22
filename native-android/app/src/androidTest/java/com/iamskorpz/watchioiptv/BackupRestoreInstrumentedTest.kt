package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.database.FavoriteEntity
import com.iamskorpz.watchioiptv.core.database.ProviderEntity
import com.iamskorpz.watchioiptv.core.database.WatchHistoryEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.security.AndroidSecretStore
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreInstrumentedTest {
    @Test fun encryptedFullCycleRestoresSyntheticUserStateWithoutOfflineMedia() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStoreFile = File(context.cacheDir, "backup-test-${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { dataStoreFile })
        val secrets = AndroidSecretStore(context)
        val repository = WatchioBackupRepository(context, database, dataStore, secrets, now = { 1234L })
        val provider = ProviderEntity("backup-test-provider", "Synthetic", "Xtream", "https://synthetic.invalid", 1, 1, null, true)
        database.providerDao().upsert(provider)
        database.favoriteDao().upsert(FavoriteEntity(provider.id, "Movie", "movie-1", "", "Movie", null, 1))
        database.watchHistoryDao().upsert(WatchHistoryEntity(provider.id, "Movie", "movie-1", "", "Movie", null, 10, 100, 2))
        dataStore.edit { it[stringPreferencesKey("theme_json")] = "synthetic-theme" }
        secrets.putSecret("provider.${provider.id}.xtream_password", "synthetic-secret")

        val bytes = repository.create("correct horse battery".toCharArray(), includeCredentials = true)
        val (payload, preview) = repository.preview(bytes, "correct horse battery".toCharArray())
        assertEquals(1, preview.providers)
        assertTrue(preview.includesCredentials)
        assertTrue(payload.excludedCategories.contains("offline-media"))

        database.clearAllTables()
        dataStore.edit { it.clear() }
        secrets.removeSecret("provider.${provider.id}.xtream_password")
        repository.restore(payload)

        assertEquals("Synthetic", database.providerDao().findById(provider.id)?.displayName)
        assertEquals(1, database.favoriteDao().getByProvider(provider.id).size)
        assertEquals(10L, database.watchHistoryDao().getRecent(provider.id).single().positionMs)
        assertEquals("synthetic-theme", dataStore.data.first()[stringPreferencesKey("theme_json")])
        assertEquals("synthetic-secret", secrets.getSecret("provider.${provider.id}.xtream_password"))

        bytes.fill(0)
        database.close()
        scope.cancel()
        dataStoreFile.delete()
        secrets.removeSecret("provider.${provider.id}.xtream_password")
    }
}
