package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.data.xtream.XtreamCategoryDto
import com.watchioiptv.nativeapp.data.xtream.XtreamLiveStreamDto
import com.watchioiptv.nativeapp.data.xtream.XtreamPlayerInfoResponseDto
import com.watchioiptv.nativeapp.data.xtream.XtreamSeriesDto
import com.watchioiptv.nativeapp.data.xtream.XtreamUserInfoDto
import com.watchioiptv.nativeapp.data.xtream.XtreamVodStreamDto
import com.watchioiptv.nativeapp.data.xtream.toAuthInfo
import com.watchioiptv.nativeapp.data.xtream.toDomain
import com.watchioiptv.nativeapp.domain.model.ContentType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamMappingTest {
    private val providerId = ProviderId("provider-a")

    @Test
    fun mapsAuthAndCatalogDtos() {
        val auth = XtreamPlayerInfoResponseDto(
            userInfo = XtreamUserInfoDto(username = "fake", auth = 1, status = "Active", allowedOutputFormats = listOf("ts")),
        ).toAuthInfo()
        assertTrue(auth.authenticated)

        val category = XtreamCategoryDto("10", "News", null).toDomain(providerId, ContentType.Live, 3)
        val live = XtreamLiveStreamDto("101", "BBC", "icon", "10", "bbc.uk", "ts").toDomain(providerId, 4)
        val movie = XtreamVodStreamDto("201", "Movie", "poster", "20", "7.5", "mp4", "Drama", "yt").toDomain(providerId, 5)
        val series = XtreamSeriesDto("301", "Series", "cover", "plot", "cast", "director", "genre", "2024", "8", "trailer", "45", "30").toDomain(providerId, 6)

        assertEquals("News", category?.name)
        assertEquals("bbc.uk", live?.epgChannelId)
        assertEquals("mp4", movie?.containerExtension)
        assertEquals("45", series?.runtime)
    }

    @Test
    fun parsesAccountConnectionMetadataFromFlexibleJson() {
        val auth = Json { ignoreUnknownKeys = true }.decodeFromString<XtreamPlayerInfoResponseDto>(
            """
            {
              "user_info": {
                "username": "fake",
                "auth": 1,
                "status": "Active",
                "max_connections": 2,
                "active_cons": "1",
                "allowed_output_formats": ["m3u8", "ts"]
              }
            }
            """.trimIndent(),
        ).toAuthInfo()

        assertEquals("2", auth.maxConnections)
        assertEquals("1", auth.activeConnections)
        assertEquals(listOf("m3u8", "ts"), auth.allowedOutputFormats)
    }
}
