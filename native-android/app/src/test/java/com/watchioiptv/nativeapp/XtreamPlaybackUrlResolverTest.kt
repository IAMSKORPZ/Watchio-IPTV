package com.watchioiptv.nativeapp

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SecretStore
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import com.watchioiptv.nativeapp.data.xtream.XtreamPlaybackUrlResolver
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.InputMode
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.StreamFormat
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.playback.PlaybackUrlRequest
import com.watchioiptv.nativeapp.domain.repository.ProviderRepository
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class XtreamPlaybackUrlResolverTest {
    @Test
    fun resolvesEphemeralUrlsAndMaskerHidesSecrets() = runTest {
        val secrets = ProviderCredentialStore(FakeSecretStore())
        secrets.saveXtreamCredentials("provider-a", XtreamCredentials("testuser", "testpass"))
        val resolver = XtreamPlaybackUrlResolver(FakeProviderRepository(), secrets, FakeSettingsRepository(StreamFormat.Hls))

        val live = resolver.resolve(PlaybackUrlRequest(ProviderId("provider-a"), ContentType.Live, "10"))
        val movie = resolver.resolve(PlaybackUrlRequest(ProviderId("provider-a"), ContentType.Movie, "20"))
        val series = resolver.resolve(PlaybackUrlRequest(ProviderId("provider-a"), ContentType.Series, "30"))

        assertEquals("http://example.invalid/live/testuser/testpass/10.m3u8", live)
        assertEquals("http://example.invalid/movie/testuser/testpass/20.mp4", movie)
        assertEquals("http://example.invalid/series/testuser/testpass/30.mp4", series)
        listOf(live, movie, series).map(SensitiveUrlMasker::mask).forEach {
            assertFalse(it, it.contains("testuser"))
            assertFalse(it, it.contains("testpass"))
        }
    }

    private class FakeSecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        override suspend fun putSecret(key: String, value: String) { values[key] = value }
        override suspend fun getSecret(key: String): String? = values[key]
        override suspend fun removeSecret(key: String) { values.remove(key) }
    }

    private class FakeProviderRepository : ProviderRepository {
        override fun observeProviders(): Flow<List<WatchioProvider>> = flowOf(emptyList())
        override suspend fun getProviders(): List<WatchioProvider> = emptyList()
        override suspend fun getProvider(providerId: ProviderId): WatchioProvider = WatchioProvider(
            providerId, "Provider", ProviderType.Xtream, "http://example.invalid", 1, 1, null, true,
        )
        override suspend fun saveProvider(provider: WatchioProvider) = Unit
        override suspend fun deleteProvider(providerId: ProviderId) = Unit
    }

    private class FakeSettingsRepository(private val format: StreamFormat) : SettingsRepository {
        override val selectedProviderId: Flow<ProviderId?> = flowOf(null)
        override val inputMode: Flow<InputMode> = flowOf(InputMode.Auto)
        override val streamFormat: Flow<StreamFormat> = flowOf(format)
        override suspend fun setSelectedProviderId(providerId: ProviderId?) = Unit
        override suspend fun setInputMode(inputMode: InputMode) = Unit
        override suspend fun setStreamFormat(streamFormat: StreamFormat) = Unit
    }
}
