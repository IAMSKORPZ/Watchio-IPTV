package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.data.m3u.M3uParser
import com.watchioiptv.nativeapp.domain.model.ContentType
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()

    @Test
    fun parsesFlutterParityAttributesAndExtgrpFallback() = runBlocking {
        val items = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="id-1" tvg-name='Name 1' tvg-logo=http://example.invalid/logo.png tvg-url="http://epg.invalid/a.xml" tvg-rec="1" timeshift="-2" group-title="" user-agent="UA" http-referrer='http://ref.invalid' catchup="default" catchup-source="{utc}" catchup-days="7" channel-number="101", Display Name
            #EXTGRP: Backup Group
            http://example.invalid/live/1.ts
            """.trimIndent(),
        )

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("Display Name", item.name)
        assertEquals("Backup Group", item.groupTitle)
        assertEquals("UA", item.userAgent)
        assertEquals("http://ref.invalid", item.referrer)
        assertEquals("default", item.catchupType)
        assertEquals("{utc}", item.catchupSource)
        assertEquals(7, item.catchupDays)
        assertEquals(-2.0, item.timeshiftHours!!, 0.0)
        assertEquals("101", item.channelNumber)
        assertEquals(ContentType.Live, item.contentType)
    }

    @Test
    fun preservesFallbackOrderAndContentHeuristics() = runBlocking {
        val items = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-name="Movie Name" group-title="Movies",
            http://example.invalid/movie/9.mp4
            #EXTINF:-1 tvg-id="SeriesId",
            http://example.invalid/series/show-S01E01.mkv
            #EXTINF:-1,
            http://example.invalid/live/fallback.ts
            """.trimIndent(),
        )

        assertEquals("Movie Name", items[0].name)
        assertEquals(ContentType.Movie, items[0].contentType)
        assertEquals("SeriesId", items[1].name)
        assertEquals(ContentType.Series, items[1].contentType)
        assertEquals("fallback.ts", items[2].name)
        assertEquals(M3uParser.FALLBACK_GROUP, items[2].groupTitle)
    }

    @Test
    fun detectsSeriesPatternsAndToleratesMalformedValues() = runBlocking {
        val items = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-shift="bad" catchup-days="bad", Show S01 E002
            http://example.invalid/series/episode1.mkv
            #EXTINF:-1, Other Show Season 1 Episode 2
            http://example.invalid/series/episode2.mkv
            """.trimIndent(),
        )

        assertEquals("Show", items[0].seriesName)
        assertEquals(1, items[0].seasonNumber)
        assertEquals(2, items[0].episodeNumber)
        assertNull(items[0].timeshiftHours)
        assertNull(items[0].catchupDays)
        assertEquals("Other Show", items[1].seriesName)
    }

    @Test
    fun bomAndCrlfDoNotBreakParsingAndHlsManifestIsRejected() = runBlocking {
        val bomItems = parse("\uFEFF#EXTM3U\r\n#EXTINF:-1, One\r\nhttp://example.invalid/live/1.ts\r\n")
        assertEquals(1, bomItems.size)

        val hls = runCatching {
            parse("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg.ts\n")
        }
        assertTrue(hls.isFailure)
    }

    @Test
    fun masksM3uCredentialUrls() {
        val getPhp = SensitiveUrlMasker.mask("http://example.invalid/get.php?username=user&password=pass&type=m3u_plus")
        val token = SensitiveUrlMasker.mask("https://example.invalid/list.m3u?token=secret123")
        assertTrue(getPhp.contains("username=***"))
        assertTrue(getPhp.contains("password=***"))
        assertTrue(token.contains("token=***"))
    }

    private suspend fun parse(value: String) = mutableListOf<com.watchioiptv.nativeapp.data.m3u.ParsedM3uItem>().also { items ->
        parser.parse(ByteArrayInputStream(value.toByteArray(Charsets.UTF_8))) { items.add(it) }
    }
}
