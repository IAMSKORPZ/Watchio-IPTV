package com.iamskorpz.watchioiptv

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
        val viewModel = createViewModel(context)
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            WatchioTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupRestoreScreen(viewModel)
                }
            }
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

    @Test fun dpadTraversalReachesEveryPrimaryAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = createViewModel(context)
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            WatchioTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { BackupRestoreScreen(viewModel) }
            }
        }

        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithTag("backup-password").requestFocus().assertIsFocused()
        compose.onNodeWithTag("backup-password").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("backup-password-confirm").assertIsFocused()
        compose.onNodeWithTag("backup-password-confirm").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("backup-credentials").assertIsFocused()
        compose.onNodeWithTag("backup-credentials").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("backup-create").assertIsFocused()
        val createBounds = compose.onNodeWithTag("backup-create").fetchSemanticsNode().boundsInRoot
        val restoreBounds = compose.onNodeWithTag("backup-restore").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("backup-create").performKeyInput {
            pressKey(if (restoreBounds.top > createBounds.bottom) Key.DirectionDown else Key.DirectionRight)
        }
        compose.onNodeWithTag("backup-restore").assertIsFocused()
    }

    @Test fun missingDocumentPickersShowSafeErrorsAndRestoreFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = createViewModel(context)
        compose.setContent {
            WatchioTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    BackupRestoreScreen(
                        viewModel = viewModel,
                        launchCreateDocument = { throw ActivityNotFoundException() },
                        launchOpenDocument = { throw SecurityException() },
                    )
                }
            }
        }

        compose.onNodeWithTag("backup-password").performTextInput("safe-password")
        compose.onNodeWithTag("backup-password-confirm").performTextInput("safe-password")
        compose.onNodeWithTag("backup-create").performClick()
        compose.waitUntil(30_000) { viewModel.state.value.error != null }
        compose.onNodeWithText("No compatible document picker is available to save this backup.").assertIsDisplayed()
        compose.onNodeWithTag("backup-create").assertIsFocused()

        compose.onNodeWithTag("backup-restore").performClick()
        compose.onNodeWithText("No compatible document picker is available to select a backup.").assertIsDisplayed()
        compose.onNodeWithTag("backup-restore").assertIsFocused()
    }

    @Test fun cancelledRestoreShowsFeedbackWithoutChangingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = createViewModel(context)
        compose.setContent {
            WatchioTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { BackupRestoreScreen(viewModel) }
            }
        }

        compose.runOnIdle { viewModel.readBackup(context.contentResolver, null) }
        compose.onNodeWithText("Backup selection cancelled.").assertIsDisplayed()
    }

    @Test fun compactPortraitLargeFontLayoutKeepsActionsInsideViewport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = createViewModel(context)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                WatchioTheme {
                    Box(Modifier.size(320.dp, 480.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) { BackupRestoreScreen(viewModel) }
                    }
                }
            }
        }

        val createBounds = compose.onNodeWithTag("backup-create").getUnclippedBoundsInRoot()
        val restoreBounds = compose.onNodeWithTag("backup-restore").getUnclippedBoundsInRoot()
        assert(createBounds.left >= 0.dp && createBounds.right <= 320.dp)
        assert(restoreBounds.left >= 0.dp && restoreBounds.right <= 320.dp)
        assert(restoreBounds.top > createBounds.bottom)
    }

    private fun createViewModel(context: Context): BackupRestoreViewModel {
        val database = Room.inMemoryDatabaseBuilder(context, WatchioDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "backup-compose-${UUID.randomUUID()}.preferences_pb")
        }
        return BackupRestoreViewModel(WatchioBackupRepository(context, database, dataStore, AndroidSecretStore(context)))
    }
}
