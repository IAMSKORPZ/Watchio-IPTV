package com.iamskorpz.watchioiptv

import com.iamskorpz.watchioiptv.core.player.PlaybackMedia
import com.iamskorpz.watchioiptv.core.player.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMediaSourceTest {
    @Test fun existingPlaybackDefaultsToRemote() {
        val media = PlaybackMedia(url = "https://example.invalid/movie.mp4")
        assertEquals(PlaybackSourceType.Remote, media.sourceType)
    }

    @Test fun offlinePlaybackUsesOpaqueIdentifierWithoutHeaders() {
        val media = PlaybackMedia(
            url = "watchio-offline://movie/download-id",
            sourceType = PlaybackSourceType.OfflineCache,
            cacheKey = "watchio-offline:download-id",
            isLive = false,
        )
        assertFalse(media.url.contains("username", ignoreCase = true))
        assertTrue(media.headers.isEmpty())
        assertEquals(PlaybackSourceType.OfflineCache, media.sourceType)
    }
}
