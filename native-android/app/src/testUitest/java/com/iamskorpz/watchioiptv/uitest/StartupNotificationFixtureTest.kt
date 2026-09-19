package com.iamskorpz.watchioiptv.uitest

import com.iamskorpz.watchioiptv.data.announcements.AnnouncementFeedParser
import com.iamskorpz.watchioiptv.data.updates.InstalledVersion
import com.iamskorpz.watchioiptv.data.updates.UpdateAvailability
import com.iamskorpz.watchioiptv.data.updates.UpdatePolicy
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
        UpdatePolicy.validateManifest(optional, "uitest")
        assertEquals(UpdateAvailability.UpdateAvailable, UpdatePolicy.compare(optional.versionCode, installed.versionCode))
        assertFalse(optional.mandatory)

        val mandatory = manifestFor(StartupFixtureState.MANDATORY_UPDATE, installed)
        UpdatePolicy.validateManifest(mandatory, "uitest")
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
    fun fixtureArtifactBoundaryAlwaysBlocksDownloadAndInstall() {
        assertFalse(fixtureArtifactDownloadsEnabled())
    }
}
