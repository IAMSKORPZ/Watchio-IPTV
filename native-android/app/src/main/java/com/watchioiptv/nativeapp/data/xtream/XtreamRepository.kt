package com.watchioiptv.nativeapp.data.xtream

import androidx.room.withTransaction
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.database.toDomain
import com.watchioiptv.nativeapp.core.database.toEntity
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.XtreamCredentials
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.domain.model.WatchioProvider
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import com.watchioiptv.nativeapp.domain.repository.XtreamAccountMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

class XtreamRepository(
    private val database: WatchioDatabase,
    private val credentialStore: ProviderCredentialStore,
    private val settingsRepository: SettingsRepository,
    private val retrofitFactory: (String) -> Retrofit,
    private val clock: WatchioClock,
) {
    var onMoviesUpdated: ((ProviderId) -> Unit)? = null
    var onSeriesUpdated: ((ProviderId) -> Unit)? = null
    private val _state = MutableStateFlow<XtreamImportState>(XtreamImportState.Idle)
    val state: Flow<XtreamImportState> = _state

    suspend fun addProvider(input: XtreamCredentialsInput): XtreamImportState.Success {
        val normalizedUrl = XtreamUrlNormalizer.normalize(input.serverUrl)
            ?: throw IllegalArgumentException("Enter a valid server URL.")
        val name = input.displayName.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Provider name is required.")
        val username = input.username.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Username is required.")
        val password = input.password.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Password is required.")

        ensureNotDuplicate(normalizedUrl, username)

        val providerId = ProviderId("xtream-${UUID.randomUUID()}")
        return importProvider(
            providerId = providerId,
            displayName = name,
            serverUrl = normalizedUrl,
            username = username,
            password = password,
            saveProviderBeforeImport = true,
        )
    }

    suspend fun refreshProvider(providerId: ProviderId): XtreamImportState.Success {
        val provider = database.providerDao().findById(providerId.value)
            ?: throw IllegalArgumentException("Provider not found.")
        val credentials = credentialStore.getXtreamCredentials(providerId.value)
            ?: throw IllegalArgumentException("Provider credentials not found.")
        return importProvider(
            providerId = providerId,
            displayName = provider.displayName,
            serverUrl = provider.serverUrl ?: throw IllegalArgumentException("Provider has no server URL."),
            username = credentials.username,
            password = credentials.password,
            saveProviderBeforeImport = false,
        )
    }

    suspend fun refreshLive(providerId: ProviderId): XtreamImportState.Success =
        refreshSection(providerId, ContentType.Live)

    suspend fun refreshMovies(providerId: ProviderId): XtreamImportState.Success =
        refreshSection(providerId, ContentType.Movie)

    suspend fun refreshSeries(providerId: ProviderId): XtreamImportState.Success =
        refreshSection(providerId, ContentType.Series)

    private suspend fun refreshSection(providerId: ProviderId, contentType: ContentType): XtreamImportState.Success {
        val provider = database.providerDao().findById(providerId.value)
            ?: throw IllegalArgumentException("Provider not found.")
        val credentials = credentialStore.getXtreamCredentials(providerId.value)
            ?: throw IllegalArgumentException("Provider credentials not found.")
        val serverUrl = provider.serverUrl ?: throw IllegalArgumentException("Provider has no server URL.")
        val api = api(serverUrl)
        val auth = api.playerInfo(credentials.username, credentials.password).toAuthInfo()
        if (!auth.authenticated) throw IllegalArgumentException("Incorrect username or password.")
        settingsRepository.persistAccountMetadata(providerId, auth)
        val now = clock.nowEpochMs()
        when (contentType) {
            ContentType.Live -> {
                val categories = api.liveCategories(credentials.username, credentials.password)
                    .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Live, index) }
                val live = api.liveStreams(credentials.username, credentials.password)
                    .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
                database.withTransaction {
                    database.categoryDao().replaceCategories(providerId.value, ContentType.Live.persisted, categories.map { it.toEntity() })
                    database.liveStreamDao().replaceLiveStreams(providerId.value, live.map { it.toEntity(now) })
                    database.providerDao().upsert(provider.copy(updatedAtEpochMs = now).toDomain().toEntity())
                }
                settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Live, now)
                return XtreamImportState.Success(providerId, live.size, 0, 0)
            }
            ContentType.Movie -> {
                val categories = api.vodCategories(credentials.username, credentials.password)
                    .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Movie, index) }
                val movies = api.vodStreams(credentials.username, credentials.password)
                    .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
                database.withTransaction {
                    database.categoryDao().replaceCategories(providerId.value, ContentType.Movie.persisted, categories.map { it.toEntity() })
                    database.vodDao().replaceMovies(providerId.value, movies.map { it.toEntity(now) })
                    database.providerDao().upsert(provider.copy(updatedAtEpochMs = now).toDomain().toEntity())
                }
                onMoviesUpdated?.invoke(providerId)
                settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Movie, now)
                return XtreamImportState.Success(providerId, 0, movies.size, 0)
            }
            ContentType.Series -> {
                val categories = api.seriesCategories(credentials.username, credentials.password)
                    .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Series, index) }
                val series = api.series(credentials.username, credentials.password)
                    .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
                database.withTransaction {
                    database.categoryDao().replaceCategories(providerId.value, ContentType.Series.persisted, categories.map { it.toEntity() })
                    database.seriesDao().replaceSeries(providerId.value, series.map { it.toEntity(now) })
                    database.providerDao().upsert(provider.copy(updatedAtEpochMs = now).toDomain().toEntity())
                }
                onSeriesUpdated?.invoke(providerId)
                settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Series, now)
                return XtreamImportState.Success(providerId, 0, 0, series.size)
            }
            ContentType.Episode -> throw IllegalArgumentException("Episode refresh is not a Home section.")
        }
    }

    private suspend fun importProvider(
        providerId: ProviderId,
        displayName: String,
        serverUrl: String,
        username: String,
        password: String,
        saveProviderBeforeImport: Boolean,
    ): XtreamImportState.Success {
        var stage = XtreamImportStage.Authenticating
        try {
            _state.value = XtreamImportState.Importing(stage, displayName)
            val api = api(serverUrl)
            val auth = api.playerInfo(username, password).toAuthInfo()
            if (!auth.authenticated) {
                throw IllegalArgumentException("Incorrect username or password.")
            }

            stage = XtreamImportStage.LoadingLiveCategories
            _state.value = XtreamImportState.Importing(stage, displayName)
            val liveCategories = api.liveCategories(username, password)
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Live, index) }

            stage = XtreamImportStage.LoadingLiveStreams
            _state.value = XtreamImportState.Importing(stage, displayName)
            val live = api.liveStreams(username, password)
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            _state.value = XtreamImportState.Importing(stage, displayName, liveCount = live.size)

            stage = XtreamImportStage.LoadingVodCategories
            _state.value = XtreamImportState.Importing(stage, displayName, liveCount = live.size)
            val vodCategories = api.vodCategories(username, password)
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Movie, index) }

            stage = XtreamImportStage.LoadingVodStreams
            _state.value = XtreamImportState.Importing(stage, displayName, liveCount = live.size)
            val movies = api.vodStreams(username, password)
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size)

            stage = XtreamImportStage.LoadingSeriesCategories
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size)
            val seriesCategories = api.seriesCategories(username, password)
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Series, index) }

            stage = XtreamImportStage.LoadingSeries
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size)
            val series = api.series(username, password)
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }

            stage = XtreamImportStage.Saving
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size, series.size)
            val now = clock.nowEpochMs()
            val provider = WatchioProvider(
                id = providerId,
                displayName = displayName,
                type = ProviderType.Xtream,
                serverUrl = serverUrl,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                lastRefreshAtEpochMs = now,
                enabled = true,
            )
            if (saveProviderBeforeImport) {
                credentialStore.saveXtreamCredentials(providerId.value, XtreamCredentials(username, password))
            }
            database.withTransaction {
                database.providerDao().upsert(provider.toEntity())
                database.categoryDao().replaceCategories(providerId.value, ContentType.Live.persisted, liveCategories.map { it.toEntity() })
                database.categoryDao().replaceCategories(providerId.value, ContentType.Movie.persisted, vodCategories.map { it.toEntity() })
                database.categoryDao().replaceCategories(providerId.value, ContentType.Series.persisted, seriesCategories.map { it.toEntity() })
                database.liveStreamDao().replaceLiveStreams(providerId.value, live.map { it.toEntity(now) })
                database.vodDao().replaceMovies(providerId.value, movies.map { it.toEntity(now) })
                database.seriesDao().replaceSeries(providerId.value, series.map { it.toEntity(now) })
            }
            onMoviesUpdated?.invoke(providerId)
            onSeriesUpdated?.invoke(providerId)
            settingsRepository.setSelectedProviderId(providerId)
            settingsRepository.setProviderExpiryEpochMs(providerId, auth.expiration?.toLongOrNull()?.let { it * 1_000L })
            settingsRepository.persistAccountMetadata(providerId, auth)
            settingsRepository.setDeviceModeOnboardingCompleted(true)
            settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Live, now)
            settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Movie, now)
            settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Series, now)
            val success = XtreamImportState.Success(providerId, live.size, movies.size, series.size)
            _state.value = success
            return success
        } catch (cancellation: CancellationException) {
            if (saveProviderBeforeImport) credentialStore.deleteProviderSecrets(providerId.value)
            throw cancellation
        } catch (throwable: Throwable) {
            if (saveProviderBeforeImport) credentialStore.deleteProviderSecrets(providerId.value)
            val failure = XtreamImportState.Failure(stage, throwable.safeMessage())
            _state.value = failure
            throw IllegalStateException(failure.message, throwable)
        }
    }

    suspend fun counts(providerId: ProviderId): XtreamCatalogCounts = XtreamCatalogCounts(
        liveCount = database.liveStreamDao().countByProvider(providerId.value),
        movieCount = database.vodDao().countByProvider(providerId.value),
        seriesCount = database.seriesDao().countByProvider(providerId.value),
    )

    private suspend fun ensureNotDuplicate(serverUrl: String, username: String) {
        val candidates = database.providerDao().findByTypeAndServer(ProviderType.Xtream.persisted, serverUrl)
        val duplicate = candidates.any { provider ->
            credentialStore.getXtreamCredentials(provider.id)?.username == username
        }
        if (duplicate) throw DuplicateXtreamProviderException()
    }

    private fun api(serverUrl: String): XtreamApi =
        retrofitFactory(serverUrl.toHttpUrl().newBuilder().addPathSegment("").build().toString())
            .create(XtreamApi::class.java)

    private fun Throwable.safeMessage(): String = when (this) {
        is DuplicateXtreamProviderException -> "Provider appears to already exist."
        is SocketTimeoutException -> "Connection timed out."
        is IOException -> "Unable to connect to provider."
        is HttpException -> when (code()) {
            401, 403 -> "Incorrect username or password."
            in 500..599 -> "Provider server error."
            else -> "Unable to connect to provider."
        }
        is IllegalArgumentException -> message ?: "Provider returned an invalid response."
        else -> "Provider returned an invalid response."
    }
}

private suspend fun SettingsRepository.persistAccountMetadata(providerId: ProviderId, auth: XtreamAuthInfo) {
    setProviderExpiryEpochMs(providerId, auth.expiration?.toLongOrNull()?.let { it * 1_000L })
    setXtreamAccountMetadata(
        providerId,
        XtreamAccountMetadata(
            status = auth.status?.trim()?.takeIf { it.isNotBlank() },
            maxConnections = auth.maxConnections?.trim()?.takeIf { it.isNotBlank() },
            activeConnections = auth.activeConnections?.trim()?.takeIf { it.isNotBlank() },
            allowedOutputFormats = auth.allowedOutputFormats.mapNotNull { it.trim().takeIf(String::isNotBlank) },
        ),
    )
}
