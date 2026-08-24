package com.watchioiptv.nativeapp.core.database

import com.watchioiptv.nativeapp.core.model.CategoryId
import com.watchioiptv.nativeapp.core.model.ChannelId
import com.watchioiptv.nativeapp.core.model.EpisodeId
import com.watchioiptv.nativeapp.core.model.MovieId
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.model.SeriesId
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.WatchioCategory
import com.watchioiptv.nativeapp.domain.model.WatchioChannel
import com.watchioiptv.nativeapp.domain.model.WatchioEpisode
import com.watchioiptv.nativeapp.domain.model.WatchioMovie
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.model.WatchioSeries

fun ProviderEntity.toDomain(): WatchioProvider = WatchioProvider(
    id = ProviderId(id),
    displayName = displayName,
    type = ProviderType.fromPersisted(type),
    serverUrl = serverUrl,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    lastRefreshAtEpochMs = lastRefreshAtEpochMs,
    enabled = enabled,
)

fun WatchioProvider.toEntity(): ProviderEntity = ProviderEntity(
    id = id.value,
    displayName = displayName,
    type = type.persisted,
    serverUrl = serverUrl,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    lastRefreshAtEpochMs = lastRefreshAtEpochMs,
    enabled = enabled,
)

fun WatchioCategory.toEntity(): CategoryEntity = CategoryEntity(
    providerId = providerId.value,
    categoryId = categoryId.value,
    name = name,
    normalizedName = TextNormalizer.normalizeForSearch(name),
    parentId = parentId,
    contentType = contentType.persisted,
    serverOrder = serverOrder,
)

fun CategoryEntity.toDomain(): WatchioCategory = WatchioCategory(
    providerId = ProviderId(providerId),
    categoryId = CategoryId(categoryId),
    name = name,
    parentId = parentId,
    contentType = ContentType.fromPersisted(contentType),
    serverOrder = serverOrder,
)

fun WatchioChannel.toEntity(nowEpochMs: Long): LiveStreamEntity = LiveStreamEntity(
    providerId = providerId.value,
    streamId = streamId.value,
    name = name,
    normalizedName = TextNormalizer.normalizeForSearch(name),
    iconUrl = iconUrl,
    categoryId = categoryId?.value,
    epgChannelId = epgChannelId,
    streamExtension = streamExtension,
    serverOrder = serverOrder,
    createdAtEpochMs = nowEpochMs,
    updatedAtEpochMs = nowEpochMs,
)

fun LiveStreamEntity.toDomain(): WatchioChannel = WatchioChannel(
    providerId = ProviderId(providerId),
    streamId = ChannelId(streamId),
    name = name,
    iconUrl = iconUrl,
    categoryId = categoryId?.let(::CategoryId),
    epgChannelId = epgChannelId,
    streamExtension = streamExtension,
    serverOrder = serverOrder,
)

fun WatchioMovie.toEntity(nowEpochMs: Long): VodStreamEntity = VodStreamEntity(
    providerId = providerId.value,
    streamId = streamId.value,
    name = name,
    normalizedName = TextNormalizer.normalizeForSearch(name),
    posterUrl = posterUrl,
    categoryId = categoryId?.value,
    rating = rating,
    containerExtension = containerExtension,
    genre = genre,
    trailer = trailer,
    serverOrder = serverOrder,
    updatedAtEpochMs = nowEpochMs,
)

fun VodStreamEntity.toDomain(): WatchioMovie = WatchioMovie(
    providerId = ProviderId(providerId),
    streamId = MovieId(streamId),
    name = name,
    posterUrl = posterUrl,
    categoryId = categoryId?.let(::CategoryId),
    rating = rating,
    containerExtension = containerExtension,
    genre = genre,
    trailer = trailer,
    serverOrder = serverOrder,
)

fun WatchioSeries.toEntity(nowEpochMs: Long): SeriesEntity = SeriesEntity(
    providerId = providerId.value,
    seriesId = seriesId.value,
    name = name,
    normalizedName = TextNormalizer.normalizeForSearch(name),
    coverUrl = coverUrl,
    plot = plot,
    cast = cast,
    director = director,
    genre = genre,
    releaseDate = releaseDate,
    rating = rating,
    trailer = trailer,
    runtime = runtime,
    categoryId = categoryId?.value,
    tmdbId = tmdbId,
    serverOrder = serverOrder,
    updatedAtEpochMs = nowEpochMs,
)

fun SeriesEntity.toDomain(): WatchioSeries = WatchioSeries(
    providerId = ProviderId(providerId),
    seriesId = SeriesId(seriesId),
    name = name,
    coverUrl = coverUrl,
    plot = plot,
    cast = cast,
    director = director,
    genre = genre,
    releaseDate = releaseDate,
    rating = rating,
    trailer = trailer,
    runtime = runtime,
    categoryId = categoryId?.let(::CategoryId),
    tmdbId = tmdbId,
    serverOrder = serverOrder,
)

fun EpisodeEntity.toDomain(): WatchioEpisode = WatchioEpisode(
    providerId = ProviderId(providerId),
    seriesId = SeriesId(seriesId),
    episodeId = EpisodeId(episodeId),
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    title = title,
    imageUrl = imageUrl,
    durationSecs = durationSecs,
)
