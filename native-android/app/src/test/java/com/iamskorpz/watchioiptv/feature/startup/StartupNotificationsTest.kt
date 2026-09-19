package com.iamskorpz.watchioiptv.feature.startup

import com.iamskorpz.watchioiptv.data.updates.UpdateApk
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import com.iamskorpz.watchioiptv.domain.model.Announcement
import com.iamskorpz.watchioiptv.domain.model.AnnouncementItem
import com.iamskorpz.watchioiptv.domain.model.AnnouncementPriority
import com.iamskorpz.watchioiptv.domain.model.AnnouncementSnapshot
import com.iamskorpz.watchioiptv.domain.model.AnnouncementType
import com.iamskorpz.watchioiptv.feature.settings.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupNotificationsTest {
    @Test
    fun updateHasPriorityOverAnnouncement() {
        val result = resolve(UpdateStatus.UpdateAvailable, manifest(), snapshot())
        assertTrue(result is StartupNotification.Update)
    }

    @Test
    fun laterSuppressesOnlyThatUpdateAndThenShowsOneAnnouncement() {
        val result = resolve(
            status = UpdateStatus.UpdateAvailable,
            manifest = manifest(),
            snapshot = snapshot(),
            deferredCode = 16,
        )
        assertEquals("newest", (result as StartupNotification.RemoteAnnouncement).announcement.id)
    }

    @Test
    fun mandatoryUpdateCannotBeReplacedByAnnouncementWithoutMatchingDeferredCode() {
        val result = resolve(UpdateStatus.UpdateAvailable, manifest().copy(mandatory = true), snapshot())
        assertTrue(result is StartupNotification.Update)
    }

    @Test
    fun checkingShowsNothingAndFailureMayFallBackToAnnouncement() {
        assertNull(resolve(UpdateStatus.Checking, null, snapshot()))
        val result = resolve(UpdateStatus.Error, null, snapshot())
        assertTrue(result is StartupNotification.RemoteAnnouncement)
    }

    @Test
    fun currentOrOlderRemoteVersionShowsNoUpdateModal() {
        assertNull(resolve(UpdateStatus.UpToDate, manifest(), AnnouncementSnapshot()))
        assertNull(resolve(UpdateStatus.DevelopmentBuildNewer, manifest(), AnnouncementSnapshot()))
    }

    @Test
    fun shownAnnouncementPreventsSecondModalThisSession() {
        assertNull(resolve(UpdateStatus.UpToDate, null, snapshot(), shownId = "newest"))
    }

    @Test
    fun dismissedAndUpdateTypedAnnouncementsAreSkipped() {
        val dismissed = item("dismissed", dismissed = true)
        val update = item("remote-update", type = AnnouncementType.UPDATE)
        val ordinary = item("ordinary")
        val result = selectStartupAnnouncement(AnnouncementSnapshot(listOf(dismissed, update, ordinary)), emptySet())
        assertEquals("ordinary", result?.id)
    }

    private fun resolve(
        status: UpdateStatus,
        manifest: UpdateManifest?,
        snapshot: AnnouncementSnapshot,
        deferredCode: Int? = null,
        shownId: String? = null,
    ) = resolveStartupNotification(status, manifest, deferredCode, snapshot, shownId, updaterScreenOpen = false)

    private fun snapshot() = AnnouncementSnapshot(listOf(item("newest"), item("older")))

    private fun item(
        id: String,
        dismissed: Boolean = false,
        type: AnnouncementType = AnnouncementType.GENERAL,
    ) = AnnouncementItem(
        announcement = Announcement(
            id = id,
            title = "Title $id",
            body = "Body $id",
            publishedAt = "2026-09-18T12:00:00Z",
            type = type,
            priority = AnnouncementPriority.NORMAL,
        ),
        isRead = false,
        isDismissed = dismissed,
    )

    private fun manifest() = UpdateManifest(
        schemaVersion = 1,
        channel = "dev",
        versionCode = 16,
        versionName = "0.1.3",
        minimumSupportedVersionCode = 1,
        mandatory = false,
        publishedAt = "2026-09-18T12:00:00Z",
        releaseNotes = listOf("Fix one", "Fix two"),
        githubRelease = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/v0.1.3",
        apk = UpdateApk("Watchio-IPTV.apk", "https://example.com/Watchio-IPTV.apk", "a".repeat(64)),
    )
}
