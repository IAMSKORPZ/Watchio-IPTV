package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.data.announcements.AnnouncementFeedParser
import com.watchioiptv.nativeapp.data.announcements.AnnouncementLocalStore
import com.watchioiptv.nativeapp.data.announcements.AnnouncementRemoteDataSource
import com.watchioiptv.nativeapp.data.announcements.AnnouncementRepository
import com.watchioiptv.nativeapp.domain.model.AnnouncementAction
import com.watchioiptv.nativeapp.domain.model.AnnouncementScreen
import com.watchioiptv.nativeapp.feature.announcements.formatAnnouncementDate
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
        assertTrue(snapshot.items.first { it.announcement.id == "two" }.isRead)
        assertEquals(0, snapshot.unreadCount)
    }

    @Test
    fun malformedDateUsesSafeUiFallback() {
        assertEquals("Date unavailable", formatAnnouncementDate("not-a-date", ZoneOffset.UTC))
    }

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

    override suspend fun saveFeed(raw: String) { cachedFeed.value = raw }
    override suspend fun markSeen(id: String) { seenIds.value += id }
    override suspend fun dismiss(id: String) {
        seenIds.value += id
        dismissedIds.value += id
    }
}
