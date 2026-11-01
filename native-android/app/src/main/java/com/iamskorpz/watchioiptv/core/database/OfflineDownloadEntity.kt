package com.iamskorpz.watchioiptv.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "offline_downloads",
    indices = [
        Index(value = ["providerId"]),
        Index(value = ["providerId", "contentId"]),
        Index(value = ["state"]),
    ],
)
data class OfflineDownloadEntity(
    @PrimaryKey val downloadId: String,
    val providerId: String?,
    val contentId: String,
    val title: String,
    val localPosterPath: String?,
    val localBackdropPath: String? = null,
    val localThumbnailPath: String? = null,
    val containerExtension: String,
    val mimeType: String,
    val state: String,
    val bytesDownloaded: Long,
    val contentLength: Long,
    val percentDownloaded: Float,
    val failureReason: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val contentType: String = "Movie",
    val showId: String? = null,
    val showTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val synopsis: String? = null,
    val releaseYear: String? = null,
    val genre: String? = null,
    val rating: String? = null,
    val runtime: String? = null,
    val episodeTitle: String? = null,
)

@Dao
interface OfflineDownloadDao {
    @Query("SELECT * FROM offline_downloads ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<OfflineDownloadEntity>>

    @Query("SELECT * FROM offline_downloads ORDER BY createdAtEpochMs DESC")
    suspend fun getAll(): List<OfflineDownloadEntity>

    @Query("SELECT * FROM offline_downloads WHERE downloadId = :downloadId LIMIT 1")
    suspend fun findById(downloadId: String): OfflineDownloadEntity?

    @Query("SELECT * FROM offline_downloads WHERE providerId = :providerId AND contentId = :contentId ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun findByContent(providerId: String, contentId: String): OfflineDownloadEntity?

    @Query("SELECT * FROM offline_downloads WHERE providerId = :providerId AND showId = :showId AND seasonNumber = :seasonNumber ORDER BY episodeNumber ASC")
    suspend fun getSeason(providerId: String, showId: String, seasonNumber: Int): List<OfflineDownloadEntity>

    @Query("SELECT * FROM offline_downloads WHERE downloadId = :downloadId LIMIT 1")
    fun findByIdBlocking(downloadId: String): OfflineDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineDownloadEntity)

    @Query(
        """
        UPDATE offline_downloads
        SET state = :state,
            bytesDownloaded = :bytesDownloaded,
            contentLength = :contentLength,
            percentDownloaded = :percentDownloaded,
            failureReason = :failureReason,
            updatedAtEpochMs = :updatedAtEpochMs,
            completedAtEpochMs = :completedAtEpochMs
        WHERE downloadId = :downloadId
        """
    )
    suspend fun updateProgress(
        downloadId: String,
        state: String,
        bytesDownloaded: Long,
        contentLength: Long,
        percentDownloaded: Float,
        failureReason: String?,
        updatedAtEpochMs: Long,
        completedAtEpochMs: Long?,
    )

    @Query("UPDATE offline_downloads SET localPosterPath = :localPosterPath, updatedAtEpochMs = :updatedAtEpochMs WHERE downloadId = :downloadId")
    suspend fun updateLocalPoster(downloadId: String, localPosterPath: String, updatedAtEpochMs: Long)

    @Query("UPDATE offline_downloads SET localBackdropPath = :path, updatedAtEpochMs = :updatedAtEpochMs WHERE downloadId = :downloadId")
    suspend fun updateLocalBackdrop(downloadId: String, path: String, updatedAtEpochMs: Long)

    @Query("UPDATE offline_downloads SET localThumbnailPath = :path, updatedAtEpochMs = :updatedAtEpochMs WHERE downloadId = :downloadId")
    suspend fun updateLocalThumbnail(downloadId: String, path: String, updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE offline_downloads SET
            title = :title, synopsis = :synopsis, releaseYear = :releaseYear,
            genre = :genre, rating = :rating, runtime = :runtime,
            showTitle = :showTitle, episodeTitle = :episodeTitle,
            updatedAtEpochMs = :updatedAtEpochMs
        WHERE downloadId = :downloadId
        """
    )
    suspend fun updateOfflineMetadata(
        downloadId: String,
        title: String,
        synopsis: String?,
        releaseYear: String?,
        genre: String?,
        rating: String?,
        runtime: String?,
        showTitle: String?,
        episodeTitle: String?,
        updatedAtEpochMs: Long,
    )

    @Query("DELETE FROM offline_downloads WHERE downloadId = :downloadId")
    suspend fun delete(downloadId: String)
}
