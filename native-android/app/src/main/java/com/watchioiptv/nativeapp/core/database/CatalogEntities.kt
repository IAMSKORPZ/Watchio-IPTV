package com.watchioiptv.nativeapp.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "providers",
    indices = [Index("type"), Index("enabled")],
)
data class ProviderEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String,
    val serverUrl: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val lastRefreshAtEpochMs: Long?,
    val enabled: Boolean,
)

@Entity(
    tableName = "categories",
    primaryKeys = ["providerId", "categoryId", "contentType"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "contentType"]),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "contentType", "serverOrder"]),
    ],
)
data class CategoryEntity(
    val providerId: String,
    val categoryId: String,
    val name: String,
    val normalizedName: String,
    val parentId: String?,
    val contentType: String,
    val serverOrder: Int,
)

@Entity(
    tableName = "live_streams",
    primaryKeys = ["providerId", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "normalizedName"]),
        Index(value = ["providerId", "serverOrder"]),
    ],
)
data class LiveStreamEntity(
    val providerId: String,
    val streamId: String,
    val name: String,
    val normalizedName: String,
    val iconUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val streamExtension: String?,
    val serverOrder: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "vod_streams",
    primaryKeys = ["providerId", "streamId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "normalizedName"]),
        Index(value = ["providerId", "serverOrder"]),
    ],
)
data class VodStreamEntity(
    val providerId: String,
    val streamId: String,
    val name: String,
    val normalizedName: String,
    val posterUrl: String?,
    val categoryId: String?,
    val rating: String?,
    val containerExtension: String?,
    val genre: String?,
    val trailer: String?,
    val serverOrder: Int,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "series",
    primaryKeys = ["providerId", "seriesId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "normalizedName"]),
        Index(value = ["providerId", "serverOrder"]),
    ],
)
data class SeriesEntity(
    val providerId: String,
    val seriesId: String,
    val name: String,
    val normalizedName: String,
    val coverUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val rating5Based: String? = null,
    val trailer: String?,
    val runtime: String?,
    val backdropUrl: String? = null,
    val categoryId: String?,
    val tmdbId: Int?,
    val serverOrder: Int,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "seasons",
    primaryKeys = ["providerId", "seriesId", "seasonNumber"],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["providerId", "seriesId"],
            childColumns = ["providerId", "seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["providerId", "seriesId"])],
)
data class SeasonEntity(
    val providerId: String,
    val seriesId: String,
    val seasonNumber: Int,
    val name: String,
    val seasonId: String? = null,
    val airDate: String? = null,
    val overview: String?,
    val coverUrl: String?,
    val coverBigUrl: String? = null,
    val rating: String? = null,
    val episodeCount: Int?,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["providerId", "seriesId", "episodeId"],
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["providerId", "seriesId"],
            childColumns = ["providerId", "seriesId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["providerId", "seriesId"]),
        Index(value = ["providerId", "seriesId", "seasonNumber", "episodeNumber"]),
    ],
)
data class EpisodeEntity(
    val providerId: String,
    val seriesId: String,
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val normalizedTitle: String,
    val imageUrl: String?,
    val plot: String? = null,
    val containerExtension: String?,
    val duration: String? = null,
    val durationSecs: Int?,
    val rating: String?,
    val releaseDate: String? = null,
    val tmdbId: Int? = null,
    val directUrl: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["providerId", "contentType", "contentId", "subContentId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "contentType"]),
        Index(value = ["providerId", "createdAtEpochMs"]),
    ],
)
data class FavoriteEntity(
    val providerId: String,
    val contentType: String,
    val contentId: String,
    val subContentId: String = "",
    val title: String,
    val imageUrl: String?,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["providerId", "contentType", "contentId", "subContentId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "contentType"]),
        Index(value = ["providerId", "lastWatchedAtEpochMs"]),
    ],
)
data class WatchHistoryEntity(
    val providerId: String,
    val contentType: String,
    val contentId: String,
    val subContentId: String = "",
    val title: String,
    val imageUrl: String?,
    val positionMs: Long?,
    val durationMs: Long?,
    val lastWatchedAtEpochMs: Long,
)

@Entity(
    tableName = "epg_channels",
    primaryKeys = ["providerId", "epgChannelId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("providerId"), Index(value = ["providerId", "normalizedName"])],
)
data class EpgChannelEntity(
    val providerId: String,
    val epgChannelId: String,
    val displayName: String,
    val normalizedName: String,
    val iconUrl: String?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "epg_programmes",
    primaryKeys = ["providerId", "epgChannelId", "programmeId"],
    foreignKeys = [
        ForeignKey(
            entity = EpgChannelEntity::class,
            parentColumns = ["providerId", "epgChannelId"],
            childColumns = ["providerId", "epgChannelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "epgChannelId", "startTimeEpochMs", "endTimeEpochMs"]),
    ],
)
data class EpgProgrammeEntity(
    val providerId: String,
    val epgChannelId: String,
    val programmeId: String,
    val title: String,
    val description: String?,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "epg_sources",
    primaryKeys = ["providerId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("providerId"), Index(value = ["providerId", "enabled"])],
)
data class EpgSourceEntity(
    val providerId: String,
    val sourceId: String,
    val sourceType: String,
    val url: String?,
    val enabled: Boolean,
    val priority: Int,
    val lastRefreshAtEpochMs: Long?,
    val lastSuccessAtEpochMs: Long?,
    val lastErrorAtEpochMs: Long?,
    val lastError: String?,
    val etag: String?,
    val lastModified: String?,
    val channelCount: Int,
    val programmeCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "epg_import_channels",
    primaryKeys = ["sessionId", "providerId", "epgChannelId"],
    indices = [Index("sessionId"), Index(value = ["sessionId", "providerId"])],
)
data class EpgImportChannelEntity(
    val sessionId: String,
    val providerId: String,
    val epgChannelId: String,
    val displayName: String,
    val normalizedName: String,
    val iconUrl: String?,
    val updatedAtEpochMs: Long,
) {
    fun toFinalEntity(): EpgChannelEntity = EpgChannelEntity(
        providerId = providerId,
        epgChannelId = epgChannelId,
        displayName = displayName,
        normalizedName = normalizedName,
        iconUrl = iconUrl,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

@Entity(
    tableName = "epg_import_programmes",
    primaryKeys = ["sessionId", "providerId", "epgChannelId", "programmeId"],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "providerId"]),
        Index(value = ["sessionId", "providerId", "epgChannelId"]),
    ],
)
data class EpgImportProgrammeEntity(
    val sessionId: String,
    val providerId: String,
    val epgChannelId: String,
    val programmeId: String,
    val title: String,
    val description: String?,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toFinalEntity(): EpgProgrammeEntity = EpgProgrammeEntity(
        providerId = providerId,
        epgChannelId = epgChannelId,
        programmeId = programmeId,
        title = title,
        description = description,
        startTimeEpochMs = startTimeEpochMs,
        endTimeEpochMs = endTimeEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

@Entity(
    tableName = "m3u_items",
    primaryKeys = ["providerId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("providerId"),
        Index(value = ["providerId", "contentType"]),
        Index(value = ["providerId", "categoryId"]),
        Index(value = ["providerId", "playlistOrder"]),
    ],
)
data class M3uItemEntity(
    val providerId: String,
    val itemId: String,
    val directUrl: String,
    val name: String,
    val normalizedName: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val tvgUrl: String?,
    val tvgRec: String?,
    val tvgShift: String?,
    val groupTitle: String,
    val groupName: String?,
    val categoryId: String,
    val userAgent: String?,
    val referrer: String?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val timeshiftHours: Double?,
    val channelNumber: String?,
    val contentType: String,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val playlistOrder: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "m3u_import_items",
    primaryKeys = ["sessionId", "providerId", "itemId"],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "providerId"]),
        Index(value = ["sessionId", "playlistOrder"]),
    ],
)
data class M3uImportItemEntity(
    val sessionId: String,
    val providerId: String,
    val itemId: String,
    val directUrl: String,
    val name: String,
    val normalizedName: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val tvgUrl: String?,
    val tvgRec: String?,
    val tvgShift: String?,
    val groupTitle: String,
    val groupName: String?,
    val categoryId: String,
    val userAgent: String?,
    val referrer: String?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val timeshiftHours: Double?,
    val channelNumber: String?,
    val contentType: String,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val playlistOrder: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    fun toFinalEntity(): M3uItemEntity = M3uItemEntity(
        providerId = providerId,
        itemId = itemId,
        directUrl = directUrl,
        name = name,
        normalizedName = normalizedName,
        tvgId = tvgId,
        tvgName = tvgName,
        tvgLogo = tvgLogo,
        tvgUrl = tvgUrl,
        tvgRec = tvgRec,
        tvgShift = tvgShift,
        groupTitle = groupTitle,
        groupName = groupName,
        categoryId = categoryId,
        userAgent = userAgent,
        referrer = referrer,
        catchupType = catchupType,
        catchupSource = catchupSource,
        catchupDays = catchupDays,
        timeshiftHours = timeshiftHours,
        channelNumber = channelNumber,
        contentType = contentType,
        seriesName = seriesName,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        playlistOrder = playlistOrder,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

@Entity(
    tableName = "movie_details",
    primaryKeys = ["providerId", "movieId"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("providerId"), Index(value = ["providerId", "updatedAtEpochMs"])],
)
data class MovieDetailEntity(
    val providerId: String,
    val movieId: String,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val runtime: String?,
    val youtubeTrailer: String?,
    val tmdbId: Int?,
    val containerExtension: String?,
    val updatedAtEpochMs: Long,
)

@Entity(
    tableName = "tmdb_trailer_caches",
    primaryKeys = ["tmdbId", "type"],
    indices = [Index(value = ["tmdbId", "type"]), Index("cachedAtEpochMs")],
)
data class TmdbTrailerCacheEntity(
    val tmdbId: String,
    val type: String,
    val trailerKey: String,
    val cachedAtEpochMs: Long,
)
