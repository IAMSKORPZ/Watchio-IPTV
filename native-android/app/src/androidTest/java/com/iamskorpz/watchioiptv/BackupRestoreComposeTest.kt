package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.security.AndroidSecretStore
import com.iamskorpz.watchioiptv.feature.backup.BackupRestoreScreen
import com.iamskorpz.watchioiptv.feature.backup.BackupRestoreViewModel
import com.iamskorpz.watchioiptv.feature.backup.WatchioBackupRepository
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test

class BackupRestoreComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun backupScreenExplainsSecureIncludedAndExcludedContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "backup-compose-${UUID.randomUUID()}.preferences_pb")
        }
        val viewModel = BackupRestoreViewModel(WatchioBackupRepository(context, database, dataStore, AndroidSecretStore(context)))
        compose.setContent { WatchioTheme { BackupRestoreScreen(viewModel) } }

        compose.onNodeWithText("Encrypted, user-controlled backup").assertIsDisplayed()
        compose.onNodeWithText("Create Backup").assertIsDisplayed()
        compose.onNodeWithText("Restore Backup").assertIsDisplayed()
        compose.onNodeWithText("Offline videos and caches are excluded.", substring = true).assertIsDisplayed()
    }
}
