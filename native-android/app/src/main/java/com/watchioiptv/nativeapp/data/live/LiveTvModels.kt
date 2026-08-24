package com.watchioiptv.nativeapp.data.live

import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.domain.model.ProviderType

enum class LiveTvCategoryKind {
    All,
    Favorites,
    History,
    Provider,
}

data class LiveTvCategory(
    val id: String,
    val name: String,
    val kind: LiveTvCategoryKind,
    val sourceCategoryId: String? = null,
)

data class LiveTvChannel(
    val providerId: ProviderId,
    val providerType: ProviderType,
    val id: String,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val extension: String?,
    val directUrl: String?,
    val headers: Map<String, String>,
    val serverOrder: Int,
    val isFavorite: Boolean,
)

data class LiveTvPlaybackRequest(
    val channel: LiveTvChannel,
    val url: String,
    val headers: Map<String, String>,
)

data class LiveTvNowNext(
    val currentTitle: String?,
    val nextTitle: String?,
    val progress: Float,
    val currentDescription: String? = null,
    val currentStartEpochMs: Long? = null,
    val currentEndEpochMs: Long? = null,
)
