package com.iamskorpz.watchioiptv.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface SportsCacheDao {
    @Upsert
    suspend fun upsertFixtures(fixtures: List<SportsFixtureCacheEntity>)

    @Query("SELECT * FROM sports_fixture_cache WHERE source = :source AND kickoffEpochMs >= :fromEpochMs AND kickoffEpochMs < :toEpochMs ORDER BY kickoffEpochMs ASC")
    suspend fun fixtures(source: String, fromEpochMs: Long, toEpochMs: Long): List<SportsFixtureCacheEntity>

    @Query("SELECT * FROM sports_fixture_cache WHERE source = :source AND sourceFixtureId = :sourceFixtureId LIMIT 1")
    suspend fun fixture(source: String, sourceFixtureId: String): SportsFixtureCacheEntity?

    @Query("DELETE FROM sports_fixture_cache WHERE source = :source AND kickoffEpochMs >= :fromEpochMs AND kickoffEpochMs < :toEpochMs")
    suspend fun deleteRange(source: String, fromEpochMs: Long, toEpochMs: Long)

    @Transaction
    suspend fun replaceRange(source: String, fromEpochMs: Long, toEpochMs: Long, fixtures: List<SportsFixtureCacheEntity>) {
        deleteRange(source, fromEpochMs, toEpochMs)
        upsertFixtures(fixtures)
    }

    @Query("DELETE FROM sports_fixture_cache WHERE expiresAtEpochMs < :beforeEpochMs")
    suspend fun deleteExpired(beforeEpochMs: Long): Int
}
