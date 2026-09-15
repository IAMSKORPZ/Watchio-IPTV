package com.iamskorpz.watchioiptv.data.xtream

import com.iamskorpz.watchioiptv.core.security.ProviderCredentialStore
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.StreamFormat
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlRequest
import com.iamskorpz.watchioiptv.domain.playback.PlaybackUrlResolver
import com.iamskorpz.watchioiptv.domain.repository.ProviderRepository
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class XtreamPlaybackUrlResolver(
    private val providerRepository: ProviderRepository,
    private val credentialStore: ProviderCredentialStore,
    private val settingsRepository: SettingsRepository,
) : PlaybackUrlResolver {
    override suspend fun resolve(request: PlaybackUrlRequest): String {
        val provider = providerRepository.getProvider(request.providerId)
            ?: throw IllegalArgumentException("Provider not found.")
        val base = provider.serverUrl?.trimEnd('/')
            ?: throw IllegalArgumentException("Provider has no server URL.")
        val credentials = credentialStore.getXtreamCredentials(request.providerId.value)
            ?: throw IllegalArgumentException("Provider credentials not found.")
        val ext = request.extension()
        val path = when (request.contentType) {
            ContentType.Live -> "live/${credentials.username}/${credentials.password}/${request.contentId}.$ext"
            ContentType.Movie -> "movie/${credentials.username}/${credentials.password}/${request.contentId}.$ext"
            ContentType.Series, ContentType.Episode -> {
                val episodeId = request.subContentId?.takeUnless { it.startsWith(".") } ?: request.contentId
                "series/${credentials.username}/${credentials.password}/$episodeId.$ext"
            }
        }
        return "$base/$path"
    }

    private suspend fun PlaybackUrlRequest.extension(): String {
        if (contentType == ContentType.Live) {
            return when (settingsRepository.streamFormat.first()) {
                StreamFormat.Hls -> "m3u8"
                StreamFormat.Ts, StreamFormat.Auto -> "ts"
            }
        }
        val override = subContentId?.trimStart('.')?.takeIf { it.isNotBlank() }
        return override?.substringAfterLast('.', missingDelimiterValue = override)?.takeIf { it.isNotBlank() }
            ?: "mp4"
    }
}
