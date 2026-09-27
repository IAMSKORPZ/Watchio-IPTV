package com.iamskorpz.watchioiptv.uitest

import com.iamskorpz.watchioiptv.data.announcements.AnnouncementFeedParser
import com.iamskorpz.watchioiptv.data.updates.InstalledVersion
import com.iamskorpz.watchioiptv.data.updates.UpdateAvailability
import com.iamskorpz.watchioiptv.data.updates.UpdatePolicy
import com.iamskorpz.watchioiptv.domain.model.AnnouncementAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupNotificationFixtureTest {
    private val installed = InstalledVersion(900013, "uitest")

    @Test
    fun updateStatesUseProductionPolicy() {
        val none = manifestFor(StartupFixtureState.NONE, installed)
        assertEquals(UpdateAvailability.UpToDate, UpdatePolicy.compare(none.versionCode, installed.versionCode))

        val optional = manifestFor(StartupFixtureState.OPTIONAL_UPDATE, installed)
        UpdatePolicy.validateManifest(optional)
        assertEquals(UpdateAvailability.UpdateAvailable, UpdatePolicy.compare(optional.versionCode, installed.versionCode))
        assertFalse(optional.mandatory)

        val mandatory = manifestFor(StartupFixtureState.MANDATORY_UPDATE, installed)
        UpdatePolicy.validateManifest(mandatory)
        assertEquals(UpdateAvailability.UpdateAvailable, UpdatePolicy.compare(mandatory.versionCode, installed.versionCode))
        assertTrue(mandatory.mandatory)
    }

    @Test
    fun announcementAndCombinationStatesSupplyOnlyRequestedData() {
        val parser = AnnouncementFeedParser()
        assertTrue(parser.parse(feedFor(StartupFixtureState.NONE, "test", 1)).isEmpty())
        assertEquals(1, parser.parse(feedFor(StartupFixtureState.ANNOUNCEMENT, "test", 1)).size)
        assertEquals(1, parser.parse(feedFor(StartupFixtureState.OPTIONAL_UPDATE_AND_ANNOUNCEMENT, "test", 1)).size)
        assertEquals(1, parser.parse(feedFor(StartupFixtureState.MANDATORY_UPDATE_AND_ANNOUNCEMENT, "test", 1)).size)
    }

    @Test
    fun announcementRevisionProducesStableDistinctIds() {
        val first = announcementIdFor("session", 2)
        assertEquals("uitest-physical-announcement-session-2", first)
        assertEquals(first, announcementIdFor("session", 2))
        assertTrue(first != announcementIdFor("session", 3))
        assertTrue(first != announcementIdFor("new-session", 2))
    }

    @Test
    fun inboxFixturesProvideDeterministicCountsAndDistinctIds() {
        val parser = AnnouncementFeedParser()
        val multiple = parser.parse(feedFor(StartupFixtureState.MULTIPLE_NOTIFICATIONS, "group", 1))
        val mixed = parser.parse(feedFor(StartupFixtureState.MIXED_NOTIFICATIONS, "mixed", 1))
        val ten = parser.parse(feedFor(StartupFixtureState.TEN_UNREAD_NOTIFICATIONS, "ten", 1))

        assertEquals(3, multiple.size)
        assertEquals(3, mixed.size)
        assertEquals(10, ten.size)
        assertEquals(10, ten.map { it.id }.distinct().size)
        assertEquals(ten.map { it.id }, inboxAnnouncementIds(StartupFixtureState.TEN_UNREAD_NOTIFICATIONS, "ten"))
    }

    @Test
    fun longAndActionFixturesStaySafe() {
        val parser = AnnouncementFeedParser()
        val long = parser.parse(feedFor(StartupFixtureState.LONG_NOTIFICATION, "long", 1)).single()
        val safe = parser.parse(feedFor(StartupFixtureState.SAFE_ACTION_NOTIFICATION, "safe", 1)).single()
        val unsafe = parser.parse(feedFor(StartupFixtureState.UNSAFE_ACTION_NOTIFICATION, "unsafe", 1)).single()

        assertTrue(long.body.length > 1_000)
        assertTrue(safe.action is AnnouncementAction.OpenUrl)
        assertTrue((safe.action as AnnouncementAction.OpenUrl).url.startsWith("https://"))
        assertEquals(null, unsafe.action)
    }

    @Test
    fun inboxGenerationPreventsReadStateLeakBetweenSelections() {
        val first = inboxAnnouncementIds(StartupFixtureState.MULTIPLE_NOTIFICATIONS, "first")
        val repeated = inboxAnnouncementIds(StartupFixtureState.MULTIPLE_NOTIFICATIONS, "first")
        val readIds = setOf(first.first())
        val second = inboxAnnouncementIds(StartupFixtureState.MULTIPLE_NOTIFICATIONS, "second")
        val mixed = inboxAnnouncementIds(StartupFixtureState.MIXED_NOTIFICATIONS, "mixed")
        val mixedReadIds = setOf(mixed.first())
        val ten = inboxAnnouncementIds(StartupFixtureState.TEN_UNREAD_NOTIFICATIONS, "ten")

        assertEquals(first, repeated)
        assertEquals(2, first.count { it !in readIds })
        assertEquals(2, repeated.count { it !in readIds })
        assertTrue(first.toSet().intersect(second.toSet()).isEmpty())
        assertEquals(3, second.count { it !in readIds })
        assertEquals(1, mixed.count { it in mixedReadIds })
        assertEquals(2, mixed.count { it !in mixedReadIds })
        assertEquals(10, ten.size)
        assertEquals(10, ten.distinct().size)
    }

    @Test
    fun fixtureArtifactBoundaryAlwaysBlocksDownloadAndInstall() {
        assertFalse(fixtureArtifactDownloadsEnabled())
    }
}
