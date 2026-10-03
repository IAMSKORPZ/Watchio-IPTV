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
class SportsCacheMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        WatchioDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationSixToSevenPreservesExistingDataAndCreatesSportsCache() {
        helper.createDatabase("sports-v2-migration", 6).use { db ->
            db.execSQL("INSERT INTO app_metadata(`key`, value) VALUES('migration-proof', 'preserved')")
        }
        helper.runMigrationsAndValidate("sports-v2-migration", 7, true, WatchioMigrations.MIGRATION_6_7).use { db ->
            db.query("SELECT value FROM app_metadata WHERE `key` = 'migration-proof'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("preserved", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM sports_fixture_cache").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
