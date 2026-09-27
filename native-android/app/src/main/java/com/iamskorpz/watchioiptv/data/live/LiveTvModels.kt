package com.iamskorpz.watchioiptv.data.live

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.domain.model.ProviderType

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
    val nextStartEpochMs: Long? = null,
    val nextEndEpochMs: Long? = null,
    val laterTitle: String? = null,
    val laterStartEpochMs: Long? = null,
    val laterEndEpochMs: Long? = null,
) {
    val hasNow: Boolean get() = !currentTitle.isNullOrBlank()
    val hasNext: Boolean get() = !nextTitle.isNullOrBlank()
    val hasLater: Boolean get() = !laterTitle.isNullOrBlank()
    val hasAny: Boolean get() = hasNow || hasNext || hasLater
}
