package com.iamskorpz.watchioiptv

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
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
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            WatchioTheme { BackupRestoreScreen(viewModel) }
        }

        compose.onNodeWithText("Encrypted, user-controlled backup").assertIsDisplayed()
        compose.onNodeWithText("Create Backup").assertIsDisplayed()
        compose.onNodeWithText("Restore Backup").assertIsDisplayed()
        compose.onNodeWithText("Offline videos and caches are excluded.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("backup-credentials").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("backup-password").requestFocus().assertIsFocused()
        compose.onNodeWithTag("backup-password").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("backup-password-confirm").assertIsFocused()
    }
}
