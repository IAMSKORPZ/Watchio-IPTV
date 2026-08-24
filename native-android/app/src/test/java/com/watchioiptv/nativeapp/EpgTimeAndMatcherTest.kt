package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.database.EpgChannelEntity
import com.watchioiptv.nativeapp.core.database.LiveStreamEntity
import com.watchioiptv.nativeapp.core.database.M3uItemEntity
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.data.epg.EpgChannelMatcher
import com.watchioiptv.nativeapp.data.epg.GuideProgramme
import com.watchioiptv.nativeapp.data.epg.XmlTvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgTimeAndMatcherTest {
    private val parser = XmlTvParser()

    @Test
    fun parsesXmlTvTimezoneOffsets() {
        assertEquals(0L, parser.parseXmlTvTime("19700101000000 +0000"))
        assertEquals(-3_600_000L, parser.parseXmlTvTime("19700101010000 +0200"))
        assertEquals(18_000_000L, parser.parseXmlTvTime("19700101000000 -0500"))
        assertEquals(-5_400_000L, parser.parseXmlTvTime("19700101000000 +0130"))
        assertEquals(0L, parser.parseXmlTvTime("19700101000000"))
        assertNull(parser.parseXmlTvTime("bad"))
    }

    @Test
    fun matcherUsesSafePriorityAndAvoidsAmbiguousCompactMatches() {
        val channels = listOf(
            channel("bbc.one", "BBC One"),
            channel("BBC.TWO", "BBC Two"),
            channel("four-seven", "Channel 4Seven"),
            channel("four7", "4 Seven"),
            channel("four7-alt", "4Seven"),
        )
        val matcher = EpgChannelMatcher()
        assertEquals("bbc.one", matcher.match("bbc.one", "Wrong", channels))
        assertEquals("BBC.TWO", matcher.match("bbc.two", "Wrong", channels))
        assertEquals("bbc.one", matcher.match(null, "BBC One", channels))
        assertEquals("bbc.one", matcher.match(null, "bbc one", channels))
        assertNull(matcher.match(null, "UK | 4 Seven HD", channels))
    }

    @Test
    fun m3uAndXtreamStrongIdsMatch() {
        val channels = listOf(channel("xmltv-id", "Display"))
        val matcher = EpgChannelMatcher()
        val live = LiveStreamEntity("p", "1", "Live", "live", null, null, "xmltv-id", null, 0, 0, 0)
        val m3u = M3uItemEntity("p", "i", "http://example.invalid/1.ts", "Live", "live", "xmltv-id", null, null, null, null, null, "Live", null, "cat", null, null, null, null, null, null, null, "live", null, null, null, 0, 0, 0)
        assertEquals("xmltv-id", matcher.matchXtream(live, channels))
        assertEquals("xmltv-id", matcher.matchM3u(m3u, channels))
    }

    @Test
    fun progressAndMaskingHelpersAreSafe() {
        val masked = SensitiveUrlMasker.mask("http://example.invalid/xmltv.php?username=user&password=pass&token=secret")
        assertTrue(masked.contains("username=***"))
        assertTrue(masked.contains("password=***"))
        assertTrue(masked.contains("token=***"))
        val programme = GuideProgramme("p", "Now", null, 0, 100)
        val progress = ((50 - programme.startEpochMs).toFloat() / (programme.endEpochMs - programme.startEpochMs)).coerceIn(0f, 1f)
        assertEquals(0.5f, progress, 0f)
    }

    private fun channel(id: String, name: String) = EpgChannelEntity("p", id, name, name.lowercase(), null, 0)
}
