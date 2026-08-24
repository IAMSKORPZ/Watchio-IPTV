package com.watchioiptv.nativeapp.data.epg

enum class EpgSourceType(val persisted: String) {
    XtreamXmltv("xtream_xmltv"),
    M3uHeader("m3u_header"),
    CustomUrl("custom_url");

    companion object {
        fun fromPersisted(value: String): EpgSourceType =
            entries.firstOrNull { it.persisted == value } ?: CustomUrl
    }
}

enum class EpgImportStage(val label: String) {
    ResolvingSource("Resolving Source"),
    Downloading("Downloading"),
    Decompressing("Decompressing"),
    Parsing("Parsing"),
    Saving("Saving"),
    Matching("Matching Channels"),
    Complete("Complete"),
}

sealed interface EpgImportState {
    data object Idle : EpgImportState
    data class Importing(
        val stage: EpgImportStage,
        val channelCount: Int = 0,
        val programmeCount: Int = 0,
    ) : EpgImportState
    data class Success(val providerId: String, val channelCount: Int, val programmeCount: Int) : EpgImportState
    data class Failure(val stage: EpgImportStage, val message: String) : EpgImportState
}

data class EpgSourceDescriptor(
    val providerId: String,
    val sourceId: String,
    val sourceType: EpgSourceType,
    val url: String?,
    val enabled: Boolean,
    val priority: Int,
)

data class EpgChannel(
    val id: String,
    val displayName: String,
    val iconUrl: String?,
)

data class EpgProgramme(
    val channelId: String,
    val programmeId: String,
    val title: String,
    val description: String?,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

data class GuideProgramme(
    val programmeId: String,
    val title: String,
    val description: String?,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

data class NowNext(
    val current: GuideProgramme?,
    val next: GuideProgramme?,
    val progress: Float,
)

data class EpgImportResult(
    val providerId: String,
    val channelCount: Int,
    val programmeCount: Int,
)
