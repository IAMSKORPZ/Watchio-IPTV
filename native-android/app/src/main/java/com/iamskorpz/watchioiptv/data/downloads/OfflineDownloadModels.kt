package com.iamskorpz.watchioiptv.data.downloads

enum class DownloadAuthorization {
    NotEstablished,
    ProviderApproved,
    UserOwned,
}

data class OfflineMovieDownloadCandidate(
    val providerId: String?,
    val contentId: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val thumbnailUrl: String? = null,
    val containerExtension: String?,
    val mimeType: String? = null,
    val expectedContentLength: Long? = null,
    val isDrmProtected: Boolean = false,
    val authorization: DownloadAuthorization = DownloadAuthorization.NotEstablished,
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

interface MovieDownloadAuthorizationResolver {
    fun authorizationFor(providerId: String, contentId: String): DownloadAuthorization
}

object DenyMovieDownloadAuthorizationResolver : MovieDownloadAuthorizationResolver {
    override fun authorizationFor(providerId: String, contentId: String) = DownloadAuthorization.NotEstablished
}

/** Playlist login establishes permission for on-demand catalogue downloads. */
object PlaylistMovieDownloadAuthorizationResolver : MovieDownloadAuthorizationResolver {
    override fun authorizationFor(providerId: String, contentId: String): DownloadAuthorization =
        if (providerId.isNotBlank() && contentId.isNotBlank()) DownloadAuthorization.ProviderApproved
        else DownloadAuthorization.NotEstablished
}

object UitestMovieDownloadAuthorizationResolver : MovieDownloadAuthorizationResolver {
    const val PROVIDER_ID = "watchio-uitest-download-provider"
    const val CONTENT_ID = "watchio-uitest-authorized-movie"

    override fun authorizationFor(providerId: String, contentId: String): DownloadAuthorization =
        if (providerId == PROVIDER_ID && contentId == CONTENT_ID) DownloadAuthorization.ProviderApproved
        else DownloadAuthorization.NotEstablished
}

sealed interface DownloadEligibility {
    data class Allowed(val extension: String, val mimeType: String) : DownloadEligibility
    data class Denied(val reason: DownloadDenialReason) : DownloadEligibility
}

enum class DownloadDenialReason {
    AuthorizationRequired,
    DrmProtected,
    UnsupportedFormat,
    InsufficientStorage,
    SourceUnavailable,
}

object OfflineDownloadPolicy {
    fun evaluate(candidate: OfflineMovieDownloadCandidate): DownloadEligibility {
        if (candidate.authorization == DownloadAuthorization.NotEstablished) {
            return DownloadEligibility.Denied(DownloadDenialReason.AuthorizationRequired)
        }
        if (candidate.isDrmProtected) {
            return DownloadEligibility.Denied(DownloadDenialReason.DrmProtected)
        }
        val extension = candidate.containerExtension
            ?.trim()
            ?.removePrefix(".")
            ?.lowercase()
            ?: candidate.mimeType.toExtension()
            ?: return DownloadEligibility.Denied(DownloadDenialReason.UnsupportedFormat)
        val mimeType = when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            else -> return DownloadEligibility.Denied(DownloadDenialReason.UnsupportedFormat)
        }
        return DownloadEligibility.Allowed(extension, mimeType)
    }

    private fun String?.toExtension(): String? = when (this?.lowercase()) {
        "video/mp4" -> "mp4"
        "video/x-matroska", "video/mkv" -> "mkv"
        else -> null
    }
}

object OfflineStoragePolicy {
    const val MINIMUM_RESERVE_BYTES = 128L * 1024L * 1024L
    const val UNKNOWN_SIZE_ALLOWANCE_BYTES = 512L * 1024L * 1024L

    fun reserveBytes(totalBytes: Long): Long = maxOf(MINIMUM_RESERVE_BYTES, totalBytes / 20L)

    fun hasCapacity(totalBytes: Long, availableBytes: Long, expectedBytes: Long?): Boolean {
        val required = (expectedBytes?.takeIf { it > 0L } ?: UNKNOWN_SIZE_ALLOWANCE_BYTES) + reserveBytes(totalBytes)
        return availableBytes >= required
    }
}

enum class OfflineDownloadState {
    Queued,
    Downloading,
    Paused,
    Completed,
    Failed,
    Removing,
}

enum class OfflineDownloadFailure {
    Network,
    StorageExhausted,
    SourceUnavailable,
    Unknown,
}

fun DownloadDenialReason.userMessage(): String = when (this) {
    DownloadDenialReason.AuthorizationRequired -> "Sign in to a playlist before downloading this movie."
    DownloadDenialReason.DrmProtected -> "DRM-protected movies cannot be downloaded."
    DownloadDenialReason.UnsupportedFormat -> "Offline downloads currently support MP4 and MKV movies only."
    DownloadDenialReason.InsufficientStorage -> "Not enough private app storage is available for this download."
    DownloadDenialReason.SourceUnavailable -> "The movie source is unavailable."
}
