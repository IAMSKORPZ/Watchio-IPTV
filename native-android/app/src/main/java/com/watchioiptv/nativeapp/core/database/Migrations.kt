package com.watchioiptv.nativeapp.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WatchioMigrations {
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_sources (
                    providerId TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    sourceType TEXT NOT NULL,
                    url TEXT,
                    enabled INTEGER NOT NULL,
                    priority INTEGER NOT NULL,
                    lastRefreshAtEpochMs INTEGER,
                    lastSuccessAtEpochMs INTEGER,
                    lastErrorAtEpochMs INTEGER,
                    lastError TEXT,
                    etag TEXT,
                    lastModified TEXT,
                    channelCount INTEGER NOT NULL,
                    programmeCount INTEGER NOT NULL,
                    createdAtEpochMs INTEGER NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(providerId, sourceId),
                    FOREIGN KEY(providerId) REFERENCES providers(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_sources_providerId ON epg_sources(providerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_sources_providerId_enabled ON epg_sources(providerId, enabled)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_import_channels (
                    sessionId TEXT NOT NULL,
                    providerId TEXT NOT NULL,
                    epgChannelId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    normalizedName TEXT NOT NULL,
                    iconUrl TEXT,
                    updatedAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(sessionId, providerId, epgChannelId)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_import_channels_sessionId ON epg_import_channels(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_import_channels_sessionId_providerId ON epg_import_channels(sessionId, providerId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS epg_import_programmes (
                    sessionId TEXT NOT NULL,
                    providerId TEXT NOT NULL,
                    epgChannelId TEXT NOT NULL,
                    programmeId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    startTimeEpochMs INTEGER NOT NULL,
                    endTimeEpochMs INTEGER NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(sessionId, providerId, epgChannelId, programmeId)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_import_programmes_sessionId ON epg_import_programmes(sessionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_import_programmes_sessionId_providerId ON epg_import_programmes(sessionId, providerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_import_programmes_sessionId_providerId_epgChannelId ON epg_import_programmes(sessionId, providerId, epgChannelId)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS movie_details (
                    providerId TEXT NOT NULL,
                    movieId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    posterUrl TEXT,
                    backdropUrl TEXT,
                    plot TEXT,
                    cast TEXT,
                    director TEXT,
                    genre TEXT,
                    releaseDate TEXT,
                    rating TEXT,
                    runtime TEXT,
                    youtubeTrailer TEXT,
                    tmdbId INTEGER,
                    containerExtension TEXT,
                    updatedAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(providerId, movieId),
                    FOREIGN KEY(providerId) REFERENCES providers(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_details_providerId ON movie_details(providerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_details_providerId_updatedAtEpochMs ON movie_details(providerId, updatedAtEpochMs)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tmdb_trailer_caches (
                    tmdbId TEXT NOT NULL,
                    type TEXT NOT NULL,
                    trailerKey TEXT NOT NULL,
                    cachedAtEpochMs INTEGER NOT NULL,
                    PRIMARY KEY(tmdbId, type)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tmdb_trailer_caches_tmdbId_type ON tmdb_trailer_caches(tmdbId, type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tmdb_trailer_caches_cachedAtEpochMs ON tmdb_trailer_caches(cachedAtEpochMs)")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE series ADD COLUMN rating5Based TEXT")
            db.execSQL("ALTER TABLE series ADD COLUMN backdropUrl TEXT")
            db.execSQL("ALTER TABLE seasons ADD COLUMN seasonId TEXT")
            db.execSQL("ALTER TABLE seasons ADD COLUMN airDate TEXT")
            db.execSQL("ALTER TABLE seasons ADD COLUMN coverBigUrl TEXT")
            db.execSQL("ALTER TABLE seasons ADD COLUMN rating TEXT")
            db.execSQL("ALTER TABLE seasons ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE episodes ADD COLUMN plot TEXT")
            db.execSQL("ALTER TABLE episodes ADD COLUMN duration TEXT")
            db.execSQL("ALTER TABLE episodes ADD COLUMN releaseDate TEXT")
            db.execSQL("ALTER TABLE episodes ADD COLUMN tmdbId INTEGER")
            db.execSQL("ALTER TABLE episodes ADD COLUMN directUrl TEXT")
            db.execSQL("ALTER TABLE episodes ADD COLUMN userAgent TEXT")
            db.execSQL("ALTER TABLE episodes ADD COLUMN referrer TEXT")
            db.execSQL("ALTER TABLE episodes ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0")
        }
    }
}
