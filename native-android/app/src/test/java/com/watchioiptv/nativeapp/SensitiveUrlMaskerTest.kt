package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SensitiveUrlMaskerTest {
    @Test
    fun masksQueryCredentials() {
        val masked = SensitiveUrlMasker.mask(
            "http://example.invalid/player_api.php?username=user1&password=secret&token=abc",
        )

        assertEquals(
            "http://example.invalid/player_api.php?username=***&password=***&token=***",
            masked,
        )
    }

    @Test
    fun masksIptvPathCredentials() {
        val masked = SensitiveUrlMasker.mask("http://server.invalid/live/user/password/123.ts")

        assertEquals("http://server.invalid/live/***/***/123.ts", masked)
        assertFalse(masked.contains("password"))
    }

    @Test
    fun masksAllIptvCredentialPatterns() {
        val urls = listOf(
            "http://example.invalid/player_api.php?username=testuser&password=testpass",
            "http://example.invalid/live/testuser/testpass/123.ts",
            "http://example.invalid/movie/testuser/testpass/55.mp4",
            "http://example.invalid/series/testuser/testpass/77.mp4",
            "http://example.invalid/xmltv.php?username=testuser&password=testpass",
        )

        urls.map(SensitiveUrlMasker::mask).forEach { masked ->
            assertFalse(masked, masked.contains("testuser"))
            assertFalse(masked, masked.contains("testpass"))
        }
    }

    @Test
    fun masksAuthorizationText() {
        val masked = SensitiveUrlMasker.mask("Authorization: Bearer secret-token")

        assertEquals("Authorization: Bearer ***", masked)
    }
}
