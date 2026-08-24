package com.watchioiptv.nativeapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AppMetadataEntity::class,
        ProviderEntity::class,
        CategoryEntity::class,
        LiveStreamEntity::class,
        VodStreamEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        EpgSourceEntity::class,
        EpgImportChannelEntity::class,
        EpgImportProgrammeEntity::class,
        M3uItemEntity::class,
        M3uImportItemEntity::class,
        MovieDetailEntity::class,
        TmdbTrailerCacheEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class WatchioDatabase : RoomDatabase() {
    abstract fun appMetadataDao(): AppMetadataDao
    abstract fun providerDao(): ProviderDao
    abstract fun categoryDao(): CategoryDao
    abstract fun liveStreamDao(): LiveStreamDao
    abstract fun vodDao(): VodDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun m3uItemDao(): M3uItemDao
    abstract fun epgDao(): EpgDao
    abstract fun movieDetailDao(): MovieDetailDao
}
