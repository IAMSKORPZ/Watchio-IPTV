package com.watchioiptv.nativeapp.data.xtream

import com.watchioiptv.nativeapp.core.model.CategoryId
import com.watchioiptv.nativeapp.core.model.ChannelId
import com.watchioiptv.nativeapp.core.model.MovieId
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.model.SeriesId
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.WatchioCategory
import com.watchioiptv.nativeapp.domain.model.WatchioChannel
import com.watchioiptv.nativeapp.domain.model.WatchioMovie
import com.watchioiptv.nativeapp.domain.model.WatchioSeries

fun XtreamPlayerInfoResponseDto.toAuthInfo(): XtreamAuthInfo {
    val info = userInfo
    return XtreamAuthInfo(
        authenticated = info?.auth == 1 && !info.status.equals("Disabled", ignoreCase = true),
        username = info?.username,
        status = info?.status,
        expiration = info?.expiration,
        trial = info?.trial,
        activeConnections = info?.activeConnections,
        maxConnections = info?.maxConnections,
        allowedOutputFormats = info?.allowedOutputFormats.orEmpty(),
        serverUrl = serverInfo?.url,
        serverProtocol = serverInfo?.protocol,
        timezone = serverInfo?.timezone,
    )
}

fun XtreamCategoryDto.toDomain(providerId: ProviderId, contentType: ContentType, order: Int): WatchioCategory? {
    val id = categoryId?.takeIf { it.isNotBlank() } ?: return null
    val name = categoryName?.takeIf { it.isNotBlank() } ?: return null
    return WatchioCategory(providerId, CategoryId(id), name, parentId, contentType, order)
}

fun XtreamLiveStreamDto.toDomain(providerId: ProviderId, order: Int): WatchioChannel? {
    val id = streamId?.takeIf { it.isNotBlank() } ?: return null
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return WatchioChannel(
        providerId = providerId,
        streamId = ChannelId(id),
        name = title,
        iconUrl = streamIcon,
        categoryId = categoryId?.takeIf { it.isNotBlank() }?.let(::CategoryId),
        epgChannelId = epgChannelId,
        streamExtension = containerExtension?.ifBlank { null } ?: "ts",
        serverOrder = order,
    )
}

fun XtreamVodStreamDto.toDomain(providerId: ProviderId, order: Int): WatchioMovie? {
    val id = streamId?.takeIf { it.isNotBlank() } ?: return null
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return WatchioMovie(
        providerId = providerId,
        streamId = MovieId(id),
        name = title,
        posterUrl = streamIcon,
        categoryId = categoryId?.takeIf { it.isNotBlank() }?.let(::CategoryId),
        rating = rating,
        containerExtension = containerExtension,
        genre = genre,
        trailer = trailer,
        serverOrder = order,
    )
}

fun XtreamSeriesDto.toDomain(providerId: ProviderId, order: Int): WatchioSeries? {
    val id = seriesId?.takeIf { it.isNotBlank() } ?: return null
    val title = name?.takeIf { it.isNotBlank() } ?: return null
    return WatchioSeries(
        providerId = providerId,
        seriesId = SeriesId(id),
        name = title,
        coverUrl = cover,
        plot = plot,
        cast = cast,
        director = director,
        genre = genre,
        releaseDate = releaseDate,
        rating = rating,
        trailer = trailer,
        runtime = runtime,
        categoryId = categoryId?.takeIf { it.isNotBlank() }?.let(::CategoryId),
        tmdbId = null,
        serverOrder = order,
    )
}
