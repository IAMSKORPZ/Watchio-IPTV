package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.data.announcements.AnnouncementFeedParser
import com.iamskorpz.watchioiptv.data.announcements.GitHubAnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementLocalStore
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRemoteDataSource
import com.iamskorpz.watchioiptv.data.announcements.AnnouncementRepository
import com.iamskorpz.watchioiptv.data.updates.InstalledVersion
import com.iamskorpz.watchioiptv.data.updates.UpdateApk
import com.iamskorpz.watchioiptv.data.updates.UpdateAvailability
import com.iamskorpz.watchioiptv.data.updates.UpdateCheckResult
import com.iamskorpz.watchioiptv.data.updates.UpdateManifest
import com.iamskorpz.watchioiptv.domain.model.AnnouncementAction
import com.iamskorpz.watchioiptv.domain.model.AnnouncementPriority
import com.iamskorpz.watchioiptv.domain.model.AnnouncementScreen
import com.iamskorpz.watchioiptv.domain.model.AnnouncementType
import com.iamskorpz.watchioiptv.feature.announcements.formatAnnouncementDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementRepositoryTest {
    private val parser = AnnouncementFeedParser()

    @Test
    fun productionFeedUsesNewRawWebsiteLocation() {
        assertEquals(
            "https://raw.githubusercontent.com/IAMSKORPZ/Watchio_Website/main/2711/JFO/YLT/announcements.json",
            GitHubAnnouncementRemoteDataSource.FEED_URL,
        )
    }

    @Test
    fun parserSkipsBadEntriesAndToleratesUnknownOrMalformedOptionalFields() {
        val items = parser.parse(feed(
            entry("good", extra = "\"futureField\":42,\"dismissible\":{\"bad\":true},\"expiresAt\":{\"bad\":true}"),
            """{"id":"bad","title":"Missing fields"}""",
        ))

        assertEquals(listOf("good"), items.map { it.id })
        assertTrue(items.single().dismissible)
        assertNull(items.single().expiresAt)
    }

    @Test
    fun disabledAnnouncementIsParsedButExcludedFromRepositorySnapshot() = runTest {
        val disabled = entry("disabled", extra = "\"enabled\":false")
        val local = FakeAnnouncementStore()
        val repository = AnnouncementRepository(AnnouncementRemoteDataSource { feed(disabled, entry("enabled")) }, local)

        assertTrue(repository.refresh().isSuccess)
        assertEquals(listOf("enabled"), repository.snapshot.first().items.map { it.announcement.id })
    }

    @Test
    fun parserSupportsSafeActionsAndDropsMalformedActions() {
        val items = parser.parse(feed(
            entry("url", action = """{"type":"OPEN_URL","url":"https://watchio.example/news","label":"NEWS"}"""),
            entry("screen", action = """{"type":"OPEN_SCREEN","target":"MOVIES"}"""),
            entry("updater", action = """{"type":"OPEN_UPDATER"}"""),
            entry("unsafe", action = """{"type":"OPEN_URL","url":"javascript:alert(1)"}"""),
        ))

        assertEquals("https://watchio.example/news", (items[0].action as AnnouncementAction.OpenUrl).url)
        assertEquals(AnnouncementScreen.MOVIES, (items[1].action as AnnouncementAction.OpenScreen).screen)
        assertTrue(items[2].action is AnnouncementAction.OpenUpdater)
        assertNull(items[3].action)
    }

    @Test
    fun cachedFeedSurvivesRefreshFailureAndExpiredItemsAreHidden() = runTest {
        val local = FakeAnnouncementStore(feed(
            entry("current"),
            entry("expired", extra = "\"expiresAt\":\"2026-01-01T00:00:00Z\""),
        ))
        val repository = AnnouncementRepository(
            remote = AnnouncementRemoteDataSource { error("offline") },
            local = local,
            clock = Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC),
        )

        assertEquals(2, parser.parse(local.cachedFeed.value!!).size)
        assertTrue(repository.refresh().isFailure)
        val snapshot = repository.snapshot.first()
        assertTrue(snapshot.hasCachedFeed)
        assertEquals(listOf("current"), snapshot.items.map { it.announcement.id })
    }

    @Test
    fun readAndDismissStatePersistWhileNewIdsRemainUnread() = runTest {
        val local = FakeAnnouncementStore(feed(entry("one")))
        val repository = AnnouncementRepository(AnnouncementRemoteDataSource { feed(entry("one"), entry("two")) }, local)

        repository.markRead("one")
        assertTrue(repository.refresh().isSuccess)
        var snapshot = repository.snapshot.first()
        assertTrue(snapshot.items.first { it.announcement.id == "one" }.isRead)
        assertFalse(snapshot.items.first { it.announcement.id == "two" }.isRead)
        assertEquals(1, snapshot.unreadCount)

        repository.dismiss("two")
        snapshot = repository.snapshot.first()
        assertTrue(snapshot.items.first { it.announcement.id == "two" }.isDismissed)
        assertFalse(snapshot.items.first { it.announcement.id == "two" }.isRead)
        assertEquals(1, snapshot.unreadCount)

        repository.markAllRead(setOf("one", "two"))
        snapshot = repository.snapshot.first()
        assertEquals(0, snapshot.unreadCount)
    }

    @Test
    fun archiveUnreadMovesItemOutOfInboxBadgeAndIntoArchive() = runTest {
        val local = FakeAnnouncementStore(feed(entry("one"), entry("two")))
        val repository = AnnouncementRepository(AnnouncementRemoteDataSource { error("unused") }, local)

        repository.archive("one")
        val snapshot = repository.snapshot.first()

        assertEquals(listOf("two"), snapshot.inboxItems.map { it.announcement.id })
        assertEquals(listOf("one"), snapshot.archivedItems.map { it.announcement.id })
        assertEquals(1, snapshot.unreadCount)
        assertFalse(snapshot.archivedItems.single().isRead)
    }

    @Test
    fun restorePreservesReadAndUnreadStateAndRestoresBadge() = runTest {
        val local = FakeAnnouncementStore(feed(entry("read"), entry("unread")))
        val repository = AnnouncementRepository(AnnouncementRemoteDataSource { error("unused") }, local)
        repository.markRead("read")
        repository.archive("read")
        repository.archive("unread")

        repository.restore("read")
        var snapshot = repository.snapshot.first()
        assertTrue(snapshot.inboxItems.single().isRead)
        assertEquals(0, snapshot.unreadCount)

        repository.restore("unread")
        snapshot = repository.snapshot.first()
        assertFalse(snapshot.inboxItems.first { it.announcement.id == "unread" }.isRead)
        assertEquals(1, snapshot.unreadCount)
    }

    @Test
    fun feedRefreshKeepsStableIdsArchived() = runTest {
        val local = FakeAnnouncementStore(feed(entry("one")))
        val repository = AnnouncementRepository(
            AnnouncementRemoteDataSource { feed(entry("one"), entry("two")) },
            local,
        )
        repository.archive("one")

        assertTrue(repository.refresh().isSuccess)
        val snapshot = repository.snapshot.first()
        assertEquals(listOf("one"), snapshot.archivedItems.map { it.announcement.id })
        assertEquals(listOf("two"), snapshot.inboxItems.map { it.announcement.id })
    }

    @Test
    fun archiveStateSurvivesRepositoryRecreationAndStaysIndependentFromDismissal() = runTest {
        val local = FakeAnnouncementStore(feed(entry("one")))
        AnnouncementRepository(AnnouncementRemoteDataSource { error("unused") }, local).apply {
            dismiss("one")
            archive("one")
        }

        val recreated = AnnouncementRepository(AnnouncementRemoteDataSource { error("unused") }, local)
        var item = recreated.snapshot.first().archivedItems.single()
        assertTrue(item.isDismissed)
        assertTrue(item.isArchived)

        recreated.restore("one")
        item = recreated.snapshot.first().inboxItems.single()
        assertTrue(item.isDismissed)
        assertFalse(item.isArchived)
    }

    @Test
    fun markAllReadCanTargetInboxWithoutChangingArchivedUnread() = runTest {
        val local = FakeAnnouncementStore(feed(entry("active"), entry("archived")))
        val repository = AnnouncementRepository(AnnouncementRemoteDataSource { error("unused") }, local)
        repository.archive("archived")

        repository.markAllRead(setOf("active"))
        val snapshot = repository.snapshot.first()
        assertTrue(snapshot.inboxItems.single().isRead)
        assertFalse(snapshot.archivedItems.single().isRead)
        assertEquals(0, snapshot.unreadCount)
    }

    @Test
    fun noUpdateYieldsNoGeneratedUpdateAnnouncement() = runTest {
        val local = FakeAnnouncementStore(feed(entry("welcome")))
        val repository = AnnouncementRepository(
            remote = AnnouncementRemoteDataSource { feed(entry("welcome")) },
            local = local,
            updateChecker = {
                UpdateCheckResult(
                    installed = InstalledVersion(5, "v0.1.0-dev.4"),
                    manifest = sampleManifest(5, "v0.1.0-dev.4"),
                    status = UpdateAvailability.UpToDate,
                )
            },
        )
        repository.refresh()
        val snapshot = repository.snapshot.first()
        assertEquals(listOf("welcome"), snapshot.items.map { it.announcement.id })
    }

    @Test
    fun updateAvailableGeneratesAnnouncementAndMergesWithRemoteFeed() = runTest {
        val local = FakeAnnouncementStore()
        val repository = AnnouncementRepository(
            remote = AnnouncementRemoteDataSource { feed(entry("welcome")) },
            local = local,
            updateChecker = {
                UpdateCheckResult(
                    installed = InstalledVersion(4, "v0.1.0-dev.3"),
                    manifest = sampleManifest(5, "v0.1.0-dev.4", releaseNotes = listOf("New feature", "Bug fixes")),
                    status = UpdateAvailability.UpdateAvailable,
                )
            },
        )
        repository.refresh()
        val snapshot = repository.snapshot.first()
        val updateItem = snapshot.items.first { it.announcement.type == AnnouncementType.UPDATE }
        assertEquals("update-v0.1.0-dev.4-5", updateItem.announcement.id)
        assertEquals("Watchio v0.1.0-dev.4 is available", updateItem.announcement.title)
        assertTrue(updateItem.announcement.body.contains("New feature"))
        assertTrue(updateItem.announcement.body.contains("Bug fixes"))
        assertTrue(updateItem.announcement.action is AnnouncementAction.OpenUpdater)
        assertEquals(AnnouncementPriority.IMPORTANT, updateItem.announcement.priority)
        assertFalse(updateItem.isRead)
        assertEquals(2, snapshot.unreadCount)
    }

    @Test
    fun generatedUpdateParticipatesInReadAndDismissStatePerExactVersion() = runTest {
        val local = FakeAnnouncementStore()
        var currentManifest = sampleManifest(5, "v0.1.0-dev.4")
        val repository = AnnouncementRepository(
            remote = AnnouncementRemoteDataSource { feed(entry("welcome")) },
            local = local,
            updateChecker = {
                UpdateCheckResult(
                    installed = InstalledVersion(4, "v0.1.0-dev.3"),
                    manifest = currentManifest,
                    status = UpdateAvailability.UpdateAvailable,
                )
            },
        )
        repository.refresh()
        var snapshot = repository.snapshot.first()
        assertEquals(2, snapshot.unreadCount)

        // Mark read
        repository.markRead("update-v0.1.0-dev.4-5")
        snapshot = repository.snapshot.first()
        assertTrue(snapshot.items.first { it.announcement.id == "update-v0.1.0-dev.4-5" }.isRead)
        assertEquals(1, snapshot.unreadCount)

        // Dismiss exact version
        repository.dismiss("update-v0.1.0-dev.4-5")
        snapshot = repository.snapshot.first()
        assertTrue(snapshot.items.first { it.announcement.id == "update-v0.1.0-dev.4-5" }.isDismissed)

        // Newer update published
        currentManifest = sampleManifest(6, "v0.1.0-dev.5")
        repository.refresh()
        snapshot = repository.snapshot.first()
        val newUpdateItem = snapshot.items.first { it.announcement.id == "update-v0.1.0-dev.5-6" }
        assertFalse(newUpdateItem.isRead)
        assertFalse(newUpdateItem.isDismissed)
    }

    @Test
    fun matchingRemoteUpdateAnnouncementIsDeduplicatedWithGeneratedUpdate() = runTest {
        val local = FakeAnnouncementStore()
        val repository = AnnouncementRepository(
            remote = AnnouncementRemoteDataSource {
                feed(
                    entry("watchio-v0.1.0-dev.4", action = """{"type":"OPEN_UPDATER"}"""),
                    entry("other"),
                )
            },
            local = local,
            updateChecker = {
                UpdateCheckResult(
                    installed = InstalledVersion(4, "v0.1.0-dev.3"),
                    manifest = sampleManifest(5, "v0.1.0-dev.4"),
                    status = UpdateAvailability.UpdateAvailable,
                )
            },
        )
        repository.refresh()
        val snapshot = repository.snapshot.first()
        val updateItems = snapshot.items.filter { it.announcement.type == AnnouncementType.UPDATE }
        assertEquals(1, updateItems.size)
        assertEquals("update-v0.1.0-dev.4-5", updateItems.single().announcement.id)
    }

    @Test
    fun updateCheckFailureRetainsCachedAnnouncementsWithoutThrowing() = runTest {
        val local = FakeAnnouncementStore(feed(entry("welcome")))
        val repository = AnnouncementRepository(
            remote = AnnouncementRemoteDataSource { feed(entry("welcome")) },
            local = local,
            updateChecker = { error("network timeout") },
        )
        assertTrue(repository.refresh().isSuccess)
        val snapshot = repository.snapshot.first()
        assertEquals(listOf("welcome"), snapshot.items.map { it.announcement.id })
    }

    @Test
    fun malformedDateUsesSafeUiFallback() {
        assertEquals("Date unavailable", formatAnnouncementDate("not-a-date", ZoneOffset.UTC))
    }

    private fun sampleManifest(
        versionCode: Int,
        versionName: String,
        mandatory: Boolean = false,
        releaseNotes: List<String> = listOf("Sample release note"),
    ) = UpdateManifest(
        schemaVersion = 1,
        channel = "dev",
        versionCode = versionCode,
        versionName = versionName,
        minimumSupportedVersionCode = 1,
        mandatory = mandatory,
        publishedAt = "2026-08-31T17:00:00Z",
        releaseNotes = releaseNotes,
        githubRelease = "https://github.com/IAMSKORPZ/Watchio-IPTV/releases/tag/$versionName",
        apk = UpdateApk("watchio.apk", "https://example.com/watchio.apk", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
    )

    private fun feed(vararg entries: String) = """{"version":1,"announcements":[${entries.joinToString()}]}"""

    private fun entry(id: String, action: String? = null, extra: String? = null): String {
        val optional = listOfNotNull(action?.let { """"action":$it""" }, extra).joinToString(",")
        return """{"id":"$id","title":"Title $id","body":"Body $id","publishedAt":"2026-08-30T12:00:00Z","type":"GENERAL","priority":"NORMAL"${if (optional.isEmpty()) "" else ",$optional"}}"""
    }
}

private class FakeAnnouncementStore(initialFeed: String? = null) : AnnouncementLocalStore {
    override val cachedFeed = MutableStateFlow(initialFeed)
    override val seenIds = MutableStateFlow(emptySet<String>())
    override val dismissedIds = MutableStateFlow(emptySet<String>())
    override val archivedIds = MutableStateFlow(emptySet<String>())

    override suspend fun saveFeed(raw: String) { cachedFeed.value = raw }
    override suspend fun markSeen(id: String) { seenIds.value += id }
    override suspend fun markSeen(ids: Set<String>) { seenIds.value += ids }
    override suspend fun dismiss(id: String) {
        dismissedIds.value += id
    }
    override suspend fun archive(id: String) { archivedIds.value += id }
    override suspend fun restore(id: String) { archivedIds.value -= id }
}
