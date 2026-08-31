package com.watchioiptv.nativeapp

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.watchioiptv.nativeapp.data.updates.InstalledVersion
import com.watchioiptv.nativeapp.data.updates.UpdateApk
import com.watchioiptv.nativeapp.data.updates.UpdateManifest
import com.watchioiptv.nativeapp.data.updates.VerifiedUpdateFile
import com.watchioiptv.nativeapp.feature.settings.UpdatesScreen
import com.watchioiptv.nativeapp.feature.settings.UpdatesUiState
import com.watchioiptv.nativeapp.feature.settings.UpdateStatus
import com.watchioiptv.nativeapp.ui.theme.WatchioTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UpdatesScreenComposeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val installed = InstalledVersion(versionCode = 4L, versionName = "0.1.0-dev.4")
    private val manifest = UpdateManifest(
        schemaVersion = 1,
        channel = "dev",
        versionCode = 5,
        versionName = "0.1.0-dev.5",
        minimumSupportedVersionCode = 1,
        mandatory = false,
        publishedAt = "2026-08-31",
        releaseNotes = listOf("New updater UI", "Bug fixes"),
        githubRelease = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/dev-0.1.0-dev.5",
        apk = UpdateApk(
            fileName = "watchio-dev.apk",
            downloadUrl = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/download/dev-0.1.0-dev.5/watchio-dev.apk",
            sha256 = "a".repeat(64),
        ),
    )

    private fun setContent(
        status: UpdateStatus = UpdateStatus.Idle,
        manifest: UpdateManifest? = null,
        progress: Int? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        errorMessage: String? = null,
        mandatory: Boolean = false,
        verifiedFile: VerifiedUpdateFile? = null,
        onBack: () -> Unit = {},
        onCheck: () -> Unit = {},
        onDownload: () -> Unit = {},
        onPermissionRequired: () -> Unit = {},
    ) {
        val effectiveManifest = manifest ?: if (mandatory) this.manifest.copy(mandatory = true) else null
        composeRule.setContent {
            WatchioTheme {
                UpdatesScreen(
                    state = UpdatesUiState(
                        installed = installed,
                        status = status,
                        manifest = effectiveManifest,
                        verifiedFile = verifiedFile,
                        progressPercent = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        errorMessage = errorMessage,
                    ),
                    onBack = onBack,
                    onCheck = onCheck,
                    onDownload = onDownload,
                    onPermissionRequired = onPermissionRequired,
                )
            }
        }
    }

    @Test
    fun headerTitleIsUpdates() {
        setContent()
        composeRule.onNodeWithTag("updates-title").assertIsDisplayed()
        composeRule.onNodeWithText("UPDATES").assertIsDisplayed()
    }

    @Test
    fun idleStateShowsCheckButton() {
        setContent(status = UpdateStatus.Idle)
        composeRule.onNodeWithTag("updates-idle-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-check").assertIsDisplayed()
    }

    @Test
    fun checkingStateShowsProgressAndNoFocusableAction() {
        setContent(status = UpdateStatus.Checking)
        composeRule.onNodeWithTag("updates-checking-panel").assertIsDisplayed()
        // No primary focusable action button shown while busy
        assertTrue(allNodesWithTag("updates-download").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun updateAvailableShowsVersionsAndUpdateNowButton() {
        setContent(status = UpdateStatus.UpdateAvailable, manifest = manifest)
        composeRule.onNodeWithTag("updates-available-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-download").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-available-version").assertIsDisplayed()
        composeRule.onNodeWithText("0.1.0-dev.5").assertIsDisplayed()
    }

    @Test
    fun updateAvailableShowsInstalledAndNewVersionInStatusCard() {
        setContent(status = UpdateStatus.UpdateAvailable, manifest = manifest)
        composeRule.onNodeWithTag("updates-installed-version").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-new-version").assertIsDisplayed()
    }

    @Test
    fun updateAvailableReleaseNotesShown() {
        setContent(status = UpdateStatus.UpdateAvailable, manifest = manifest)
        composeRule.onNodeWithTag("updates-release-notes-card").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-release-note-0").assertIsDisplayed()
    }

    @Test
    fun downloadingStateShowsProgress() {
        setContent(
            status = UpdateStatus.Downloading,
            manifest = manifest,
            progress = 42,
            downloadedBytes = 10 * 1024 * 1024L,
            totalBytes = 24 * 1024 * 1024L,
        )
        composeRule.onNodeWithTag("updates-downloading-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-progress-percent").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
    }

    @Test
    fun verifyingStateShownWithPanel() {
        setContent(status = UpdateStatus.Verifying, manifest = manifest)
        composeRule.onNodeWithTag("updates-verifying-panel").assertIsDisplayed()
    }

    @Test
    fun readyToInstallShowsInstallButton() {
        val vf = VerifiedUpdateFile(manifest = manifest, filePath = "/fake/path/watchio.apk")
        setContent(status = UpdateStatus.ReadyToInstall, manifest = manifest, verifiedFile = vf)
        composeRule.onNodeWithTag("updates-ready-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-install").assertIsDisplayed()
    }

    @Test
    fun upToDateShowsCheckAgain() {
        setContent(status = UpdateStatus.UpToDate)
        composeRule.onNodeWithTag("updates-up-to-date").assertIsDisplayed()
        composeRule.onNodeWithText("WATCHIO IS UP TO DATE").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-check").assertIsDisplayed()
    }

    @Test
    fun developmentBuildNewerShowsCheckAgain() {
        setContent(status = UpdateStatus.DevelopmentBuildNewer)
        composeRule.onNodeWithTag("updates-up-to-date").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-check").assertIsDisplayed()
    }

    @Test
    fun errorStateShowsRetryButton() {
        setContent(status = UpdateStatus.Error, errorMessage = "Network error occurred.")
        composeRule.onNodeWithTag("updates-error-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-error").assertIsDisplayed()
        composeRule.onNodeWithTag("updates-check").assertIsDisplayed()
    }

    @Test
    fun mandatoryUpdateHidesLaterButton() {
        val mandatoryManifest = manifest.copy(mandatory = true)
        setContent(status = UpdateStatus.UpdateAvailable, manifest = mandatoryManifest)
        composeRule.onNodeWithTag("updates-mandatory-badge").assertIsDisplayed()
        assertTrue(allNodesWithTag("updates-later").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun nonMandatoryUpdateShowsLaterButton() {
        setContent(status = UpdateStatus.UpdateAvailable, manifest = manifest.copy(mandatory = false))
        composeRule.onNodeWithTag("updates-later").assertIsDisplayed()
    }

    @Test
    fun backLambdaInvokedOnBackIconClick() {
        var backCalled = false
        setContent(onBack = { backCalled = true })
        composeRule.onNodeWithTag("updates-back-icon").assertIsDisplayed().performClick()
        assertTrue(backCalled)
    }

    @Test
    fun noRawUrlsOrHashesInUiText() {
        setContent(status = UpdateStatus.UpdateAvailable, manifest = manifest)
        composeRule.waitForIdle()
        // Ensure no raw HTTPS URL or 64-char hex SHA is rendered as visible text
        assertTrue(
            "Raw URL must not appear in the UI",
            composeRule.onAllNodesWithText("https://").fetchSemanticsNodes().isEmpty(),
        )
        assertTrue(
            "SHA256 hash must not appear in the UI",
            composeRule.onAllNodesWithText("a".repeat(64)).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun allNodesWithTag(tag: String) =
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag))
}
