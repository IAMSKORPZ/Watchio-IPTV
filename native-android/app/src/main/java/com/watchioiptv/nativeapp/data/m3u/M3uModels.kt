package com.watchioiptv.nativeapp.data.m3u

import com.watchioiptv.nativeapp.domain.model.ContentType

data class ParsedM3uItem(
    val name: String,
    val url: String,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val tvgUrl: String?,
    val tvgRec: String?,
    val tvgShift: String?,
    val groupTitle: String,
    val groupName: String?,
    val userAgent: String?,
    val referrer: String?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val timeshiftHours: Double?,
    val channelNumber: String?,
    val contentType: ContentType,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val playlistOrder: Int,
)

data class M3uCounts(
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
)

enum class M3uImportStage(val label: String) {
    OpeningPlaylist("Opening Playlist"),
    ReadingPlaylist("Reading Playlist"),
    Saving("Saving"),
    Complete("Complete"),
}

sealed interface M3uImportState {
    data object Idle : M3uImportState
    data class Importing(
        val stage: M3uImportStage,
        val liveCount: Int = 0,
        val movieCount: Int = 0,
        val seriesCount: Int = 0,
    ) : M3uImportState

    data class Success(
        val providerId: String,
        val liveCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
    ) : M3uImportState

    data class Failure(val stage: M3uImportStage, val message: String) : M3uImportState
}

data class M3uUrlInput(
    val displayName: String,
    val playlistUrl: String,
    val userAgent: String? = null,
)

data class M3uFileInput(
    val displayName: String,
    val uri: String,
)
