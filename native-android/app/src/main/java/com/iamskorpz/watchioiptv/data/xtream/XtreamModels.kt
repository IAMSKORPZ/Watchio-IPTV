package com.iamskorpz.watchioiptv.data.xtream

import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.domain.model.ContentType

data class XtreamCredentialsInput(
    val displayName: String,
    val serverUrl: String? = null,
    val username: String,
    val password: String,
    val managed: Boolean = false,
)

data class XtreamAuthInfo(
    val authenticated: Boolean,
    val username: String?,
    val status: String?,
    val expiration: String?,
    val trial: String?,
    val activeConnections: String?,
    val maxConnections: String?,
    val allowedOutputFormats: List<String>,
    val serverUrl: String?,
    val serverProtocol: String?,
    val timezone: String?,
)

sealed interface AccountValidationResult {
    data object Active : AccountValidationResult
    data class Blocked(
        val message: String,
        val reason: AccountBlockReason = AccountBlockReason.InvalidCredentials,
    ) : AccountValidationResult
    data class Retryable(val message: String) : AccountValidationResult
}

enum class AccountBlockReason { Expired, Disabled, Revoked, InvalidCredentials }

enum class XtreamImportStage(val label: String) {
    Idle("Idle"),
    Authenticating("Authenticating"),
    LoadingLiveCategories("Loading Live Categories"),
    LoadingLiveStreams("Loading Live Channels"),
    LoadingVodCategories("Loading Movie Categories"),
    LoadingVodStreams("Loading Movies"),
    LoadingSeriesCategories("Loading Series Categories"),
    LoadingSeries("Loading Series"),
    Saving("Saving"),
}

sealed interface XtreamImportState {
    data object Idle : XtreamImportState
    data class Importing(
        val stage: XtreamImportStage,
        val providerName: String,
        val liveCount: Int = 0,
        val movieCount: Int = 0,
        val seriesCount: Int = 0,
    ) : XtreamImportState
    data class Success(
        val providerId: ProviderId,
        val liveCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val changedSections: Set<ContentType> = emptySet(),
    ) : XtreamImportState
    data class Failure(
        val stage: XtreamImportStage,
        val message: String,
    ) : XtreamImportState
}

data class XtreamCatalogCounts(
    val liveCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
)

class DuplicateXtreamProviderException : IllegalStateException("Provider appears to already exist.")
