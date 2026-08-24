package com.watchioiptv.nativeapp.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Upsert
    suspend fun upsert(provider: ProviderEntity)

    @Query("SELECT * FROM providers WHERE id = :providerId LIMIT 1")
    suspend fun findById(providerId: String): ProviderEntity?

    @Query("SELECT * FROM providers ORDER BY createdAtEpochMs ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE type = :type AND serverUrl = :serverUrl ORDER BY createdAtEpochMs ASC")
    suspend fun findByTypeAndServer(type: String, serverUrl: String): List<ProviderEntity>

    @Query("DELETE FROM providers WHERE id = :providerId")
    suspend fun deleteProvider(providerId: String)

    @Transaction
    suspend fun deleteProviderAndCatalog(providerId: String) {
        deleteProvider(providerId)
    }
}
