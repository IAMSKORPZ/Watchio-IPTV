package com.iamskorpz.watchioiptv

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.database.WatchioMigrations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineDownloadsMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        WatchioDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationSevenToEightPreservesDataAndCreatesIndependentDownloadTable() {
        helper.createDatabase("offline-download-migration", 7).use { db ->
            db.execSQL("INSERT INTO app_metadata(`key`, value) VALUES('migration-proof', 'preserved')")
        }
        helper.runMigrationsAndValidate(
            "offline-download-migration",
            8,
            true,
            WatchioMigrations.MIGRATION_7_8,
        ).use { db ->
            db.query("SELECT value FROM app_metadata WHERE `key` = 'migration-proof'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("preserved", cursor.getString(0))
            }
            db.execSQL(
                """
                INSERT INTO offline_downloads(
                    downloadId, providerId, contentId, title, localPosterPath,
                    containerExtension, mimeType, state, bytesDownloaded,
                    contentLength, percentDownloaded, failureReason,
                    createdAtEpochMs, updatedAtEpochMs, completedAtEpochMs
                ) VALUES('id', 'deleted-provider', 'movie', 'Movie', NULL,
                    'mp4', 'video/mp4', 'Queued', 0, -1, 0, NULL, 1, 1, NULL)
                """.trimIndent()
            )
            db.query("SELECT COUNT(*) FROM offline_downloads").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test fun migrationEightToNinePreservesMoviesAndAddsEpisodeGrouping() {
        helper.createDatabase("offline-download-migration-nine", 8).use { db ->
            db.execSQL(
                """INSERT INTO offline_downloads(downloadId,providerId,contentId,title,localPosterPath,containerExtension,mimeType,state,bytesDownloaded,contentLength,percentDownloaded,failureReason,createdAtEpochMs,updatedAtEpochMs,completedAtEpochMs)
                VALUES('movie','provider','movie','Movie',NULL,'mp4','video/mp4','Completed',10,10,100,NULL,1,1,1)"""
            )
        }
        helper.runMigrationsAndValidate(
            "offline-download-migration-nine", 9, true, WatchioMigrations.MIGRATION_8_9,
        ).use { db ->
            db.query("SELECT contentType, showId FROM offline_downloads WHERE downloadId='movie'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Movie", cursor.getString(0))
                assertEquals(true, cursor.isNull(1))
            }
        }
    }

    @Test fun migrationNineToTenPreservesMediaAndAddsOfflineDetailsMetadata() {
        helper.createDatabase("offline-download-migration-ten", 9).use { db ->
            db.execSQL(
                """INSERT INTO offline_downloads(downloadId,providerId,contentId,title,localPosterPath,containerExtension,mimeType,state,bytesDownloaded,contentLength,percentDownloaded,failureReason,createdAtEpochMs,updatedAtEpochMs,completedAtEpochMs,contentType,showId,showTitle,seasonNumber,episodeNumber)
                VALUES('episode','provider','ep-1','Episode','/poster','mp4','video/mp4','Completed',10,10,100,NULL,1,1,1,'Episode','show','Show',1,1)"""
            )
        }
        helper.runMigrationsAndValidate(
            "offline-download-migration-ten", 10, true, WatchioMigrations.MIGRATION_9_10,
        ).use { db ->
            db.query("SELECT title, localPosterPath, localBackdropPath, synopsis, episodeTitle FROM offline_downloads WHERE downloadId='episode'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Episode", cursor.getString(0))
                assertEquals("/poster", cursor.getString(1))
                assertEquals(true, cursor.isNull(2))
                assertEquals(true, cursor.isNull(3))
                assertEquals(true, cursor.isNull(4))
            }
        }
    }

    @Test fun migrationSevenToTenRunsCompleteChainAndPreservesExistingData() {
        helper.createDatabase("offline-download-migration-seven-to-ten", 7).use { db ->
            db.execSQL("INSERT INTO app_metadata(`key`, value) VALUES('full-chain-proof', 'preserved')")
        }

        helper.runMigrationsAndValidate(
            "offline-download-migration-seven-to-ten",
            10,
            true,
            WatchioMigrations.MIGRATION_7_8,
            WatchioMigrations.MIGRATION_8_9,
            WatchioMigrations.MIGRATION_9_10,
        ).use { db ->
            db.query("SELECT value FROM app_metadata WHERE `key` = 'full-chain-proof'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("preserved", cursor.getString(0))
            }

            db.execSQL(
                """INSERT INTO offline_downloads(
                    downloadId,providerId,contentId,title,localPosterPath,containerExtension,mimeType,
                    state,bytesDownloaded,contentLength,percentDownloaded,failureReason,createdAtEpochMs,
                    updatedAtEpochMs,completedAtEpochMs,contentType,showId,showTitle,seasonNumber,
                    episodeNumber,localBackdropPath,localThumbnailPath,synopsis,releaseYear,genre,rating,
                    runtime,episodeTitle
                ) VALUES(
                    'episode','removed-provider','episode-1','Episode','/poster','mp4','video/mp4',
                    'Completed',10,10,100,NULL,1,1,1,'Episode','show-1','Show',2,3,
                    '/backdrop','/thumbnail','Synopsis','2026','Drama','8.5','45 min','Episode 3'
                )"""
            )
            db.query(
                """SELECT contentType,showId,showTitle,seasonNumber,episodeNumber,localBackdropPath,
                    localThumbnailPath,synopsis,releaseYear,genre,rating,runtime,episodeTitle
                    FROM offline_downloads WHERE downloadId='episode'"""
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("Episode", cursor.getString(0))
                assertEquals("show-1", cursor.getString(1))
                assertEquals("Show", cursor.getString(2))
                assertEquals(2, cursor.getInt(3))
                assertEquals(3, cursor.getInt(4))
                assertEquals("/backdrop", cursor.getString(5))
                assertEquals("/thumbnail", cursor.getString(6))
                assertEquals("Synopsis", cursor.getString(7))
                assertEquals("2026", cursor.getString(8))
                assertEquals("Drama", cursor.getString(9))
                assertEquals("8.5", cursor.getString(10))
                assertEquals("45 min", cursor.getString(11))
                assertEquals("Episode 3", cursor.getString(12))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        }
    }
}
