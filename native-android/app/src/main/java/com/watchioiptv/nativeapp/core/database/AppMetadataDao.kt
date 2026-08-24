package com.watchioiptv.nativeapp.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppMetadataDao {
    @Upsert
    suspend fun upsert(entity: AppMetadataEntity)

    @Query("SELECT value FROM app_metadata WHERE `key` = :key LIMIT 1")
    suspend fun valueForKey(key: String): String?
}
