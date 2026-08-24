package com.watchioiptv.nativeapp.domain.playback

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ContentType

interface PlaybackUrlResolver {
    suspend fun resolve(request: PlaybackUrlRequest): String
}

data class PlaybackUrlRequest(
    val providerId: ProviderId,
    val contentType: ContentType,
    val contentId: String,
    val subContentId: String? = null,
    val catchup: CatchupRequest? = null,
)

data class CatchupRequest(
    val startTime: String,
    val durationMinutes: Int,
)
