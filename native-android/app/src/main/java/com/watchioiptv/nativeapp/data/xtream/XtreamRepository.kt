package com.watchioiptv.nativeapp.data.xtream

import androidx.room.withTransaction
import com.watchioiptv.nativeapp.core.diagnostics.QuickLoginBootstrapTrace
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

enum class CatalogSyncState { Idle, Syncing, Ready, Failed }

data class CatalogSyncKey(val providerId: ProviderId, val contentType: ContentType)

class XtreamRepository(
    private val database: WatchioDatabase,
    private val credentialStore: ProviderCredentialStore,
    private val settingsRepository: SettingsRepository,
    private val retrofitFactory: (String) -> Retrofit,
    private val clock: WatchioClock,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val endpointManager: WatchioEndpointManager? = null,
) {
    var onMoviesUpdated: ((ProviderId) -> Unit)? = null
    var onSeriesUpdated: ((ProviderId) -> Unit)? = null
    internal var onDeferredSyncStarted: ((ProviderId) -> Unit)? = null
    private val _state = MutableStateFlow<XtreamImportState>(XtreamImportState.Idle)
    val state: Flow<XtreamImportState> = _state
    private val _catalogSyncStates = MutableStateFlow<Map<CatalogSyncKey, CatalogSyncState>>(emptyMap())
    val catalogSyncStates: StateFlow<Map<CatalogSyncKey, CatalogSyncState>> = _catalogSyncStates.asStateFlow()
    private val deferredJobs = mutableMapOf<ProviderId, Job>()

    suspend fun addProvider(input: XtreamCredentialsInput): XtreamImportState.Success {
        val name = input.displayName.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Provider name is required.")
        val username = input.username.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Username is required.")
        val password = input.password.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Password is required.")

        if (input.managed) return addManagedProvider(name, username, password)
        val normalizedUrl = XtreamUrlNormalizer.normalize(input.serverUrl.orEmpty())
            ?: throw IllegalArgumentException("Enter a valid server URL.")

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

    private suspend fun addManagedProvider(name: String, username: String, password: String): XtreamImportState.Success {
        ensureNoManagedDuplicate(username)
        val manager = endpointManager
            ?: throw IllegalStateException("Watchio couldn't reach the service configuration. Please try again.")
        val selected = manager.resolve { endpoint -> authenticateEndpoint(endpoint.url, username, password) }
        return importProvider(
            providerId = ProviderId("$MANAGED_PROVIDER_PREFIX${UUID.randomUUID()}"),
            displayName = name,
            serverUrl = selected.endpoint.url,
            username = username,
            password = password,
            saveProviderBeforeImport = true,
            authenticatedInfo = selected.value,
        )
    }

    suspend fun refreshProvider(providerId: ProviderId): XtreamImportState.Success {
        endpointManager?.refreshConfig()
        val provider = database.providerDao().findById(providerId.value)
            ?: throw IllegalArgumentException("Provider not found.")
        val credentials = credentialStore.getXtreamCredentials(providerId.value)
            ?: throw IllegalArgumentException("Provider credentials not found.")
        val resolved = resolveProviderEndpoint(provider.toDomain(), credentials)
        return importProvider(
            providerId = providerId,
            displayName = provider.displayName,
            serverUrl = resolved.first,
            username = credentials.username,
            password = credentials.password,
            saveProviderBeforeImport = false,
            authenticatedInfo = resolved.second,
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
        val resolved = resolveProviderEndpoint(provider.toDomain(), credentials)
        val serverUrl = resolved.first
        val api = api(serverUrl)
        val auth = resolved.second ?: api.playerInfo(credentials.username, credentials.password).toAuthInfo()
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
        authenticatedInfo: XtreamAuthInfo? = null,
    ): XtreamImportState.Success {
        var stage = XtreamImportStage.Authenticating
        try {
            _state.value = XtreamImportState.Importing(stage, displayName)
            val api = api(serverUrl)
            var started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_auth_started")
            val auth = authenticatedInfo ?: timedRequest("player_api_authentication") { api.playerInfo(username, password) }.toAuthInfo()
            if (!auth.authenticated) {
                throw IllegalArgumentException("Incorrect username or password.")
            }
            QuickLoginBootstrapTrace.mark("quicklogin_auth_completed", started)

            stage = XtreamImportStage.LoadingLiveCategories
            _state.value = XtreamImportState.Importing(stage, displayName)
            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_live_sync_started")
            val liveCategories = timedRequest("live_categories") { api.liveCategories(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Live, index) }

            stage = XtreamImportStage.LoadingLiveStreams
            _state.value = XtreamImportState.Importing(stage, displayName)
            val live = timedRequest("live_streams") { api.liveStreams(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            _state.value = XtreamImportState.Importing(stage, displayName, liveCount = live.size)
            QuickLoginBootstrapTrace.mark("quicklogin_live_sync_completed", started, "category_count=${liveCategories.size} item_count=${live.size}")

            stage = XtreamImportStage.LoadingVodCategories
            _state.value = XtreamImportState.Importing(stage, displayName, liveCount = live.size)
            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_movies_sync_started")
            val vodCategories = timedRequest("vod_categories") { api.vodCategories(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Movie, index) }

            stage = XtreamImportStage.LoadingVodStreams
            _state.value = XtreamImportState.Importing(stage, displayName, liveCount = live.size)
            val movies = timedRequest("vod_streams") { api.vodStreams(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size)
            QuickLoginBootstrapTrace.mark("quicklogin_movies_sync_completed", started, "category_count=${vodCategories.size} item_count=${movies.size}")

            stage = XtreamImportStage.LoadingSeriesCategories
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size)
            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_series_sync_started")
            val seriesCategories = timedRequest("series_categories") { api.seriesCategories(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Series, index) }

            stage = XtreamImportStage.LoadingSeries
            _state.value = XtreamImportState.Importing(stage, displayName, live.size, movies.size)
            val series = timedRequest("series_list") { api.series(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            QuickLoginBootstrapTrace.mark("quicklogin_series_sync_completed", started, "category_count=${seriesCategories.size} item_count=${series.size}")

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
                started = QuickLoginBootstrapTrace.now()
                QuickLoginBootstrapTrace.mark("quicklogin_credentials_save_started")
                credentialStore.saveXtreamCredentials(providerId.value, XtreamCredentials(username, password))
                QuickLoginBootstrapTrace.mark("quicklogin_credentials_save_completed", started)
            }
            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_provider_save_started")
            database.withTransaction {
                timedRoom("provider_upsert", 1) { database.providerDao().upsert(provider.toEntity()) }
                timedRoom("live_categories_replace", liveCategories.size) { database.categoryDao().replaceCategories(providerId.value, ContentType.Live.persisted, liveCategories.map { it.toEntity() }) }
                timedRoom("movie_categories_replace", vodCategories.size) { database.categoryDao().replaceCategories(providerId.value, ContentType.Movie.persisted, vodCategories.map { it.toEntity() }) }
                timedRoom("series_categories_replace", seriesCategories.size) { database.categoryDao().replaceCategories(providerId.value, ContentType.Series.persisted, seriesCategories.map { it.toEntity() }) }
                timedRoom("live_replace", live.size) { database.liveStreamDao().replaceLiveStreams(providerId.value, live.map { it.toEntity(now) }) }
                timedRoom("movies_replace", movies.size) { database.vodDao().replaceMovies(providerId.value, movies.map { it.toEntity(now) }) }
                timedRoom("series_replace", series.size) { database.seriesDao().replaceSeries(providerId.value, series.map { it.toEntity(now) }) }
            }
            QuickLoginBootstrapTrace.mark("quicklogin_provider_save_completed", started, "category_count=${liveCategories.size + vodCategories.size + seriesCategories.size} live_count=${live.size} movie_count=${movies.size} series_count=${series.size}")
            onMoviesUpdated?.invoke(providerId)
            onSeriesUpdated?.invoke(providerId)
            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_provider_select_started")
            settingsRepository.setSelectedProviderId(providerId)
            QuickLoginBootstrapTrace.mark("quicklogin_provider_select_completed", started)
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

    suspend fun switchManagedProviderServer(providerId: ProviderId, endpointId: String): WatchioEndpoint {
        val provider = database.providerDao().findById(providerId.value)?.toDomain()
            ?: throw IllegalArgumentException("Provider not found.")
        require(provider.id.value.startsWith(MANAGED_PROVIDER_PREFIX)) { "Server switching is only available for managed providers." }
        provider.serverUrl ?: throw IllegalArgumentException("Provider has no server URL.")
        val credentials = credentialStore.getXtreamCredentials(providerId.value)
            ?: throw IllegalArgumentException("Provider credentials not found.")
        val manager = endpointManager ?: throw IllegalStateException("Watchio couldn't reach the service configuration. Please try again.")
        manager.refreshConfig()
        val selected = manager.switchTo(endpointId) { endpoint -> authenticateEndpoint(endpoint.url, credentials.username, credentials.password) }
        database.providerDao().upsert(provider.copy(serverUrl = selected.endpoint.url, updatedAtEpochMs = clock.nowEpochMs()).toEntity())
        return selected.endpoint
    }

    private suspend fun findExistingProvider(serverUrl: String, username: String) =
        database.providerDao().findByTypeAndServer(ProviderType.Xtream.persisted, serverUrl).firstOrNull { provider ->
            credentialStore.getXtreamCredentials(provider.id)?.username == username
        }

    private suspend fun ensureNoManagedDuplicate(username: String) {
        val duplicate = database.providerDao().getAll().asSequence()
            .filter { it.type == ProviderType.Xtream.persisted && it.id.startsWith(MANAGED_PROVIDER_PREFIX) }
            .any { provider -> credentialStore.getXtreamCredentials(provider.id)?.username.equals(username, ignoreCase = true) }
        if (duplicate) throw DuplicateXtreamProviderException()
    }

    private suspend fun resolveProviderEndpoint(provider: WatchioProvider, credentials: XtreamCredentials): Pair<String, XtreamAuthInfo?> {
        val current = provider.serverUrl ?: throw IllegalArgumentException("Provider has no server URL.")
        if (!provider.id.value.startsWith(MANAGED_PROVIDER_PREFIX)) return current to null
        val manager = endpointManager
            ?: throw IllegalStateException("Watchio couldn't reach the service configuration. Please try again.")
        val selected = manager.resolve(current) { endpoint -> authenticateEndpoint(endpoint.url, credentials.username, credentials.password) }
        if (selected.endpoint.url != current) {
            database.providerDao().upsert(provider.copy(serverUrl = selected.endpoint.url, updatedAtEpochMs = clock.nowEpochMs()).toEntity())
        }
        return selected.endpoint.url to selected.value
    }

    private suspend fun authenticateEndpoint(serverUrl: String, username: String, password: String): XtreamAuthInfo {
        return try {
            val auth = timedRequest("player_api_authentication") { api(serverUrl).playerInfo(username, password) }.toAuthInfo()
            if (!auth.authenticated) {
                val status = auth.status.orEmpty().lowercase()
                val message = if (status.contains("expired") || status.contains("disabled")) "Account is ${auth.status}." else "Username or password is incorrect."
                throw EndpointAttemptFailure.Account(message)
            }
            auth
        } catch (failure: EndpointAttemptFailure.Account) {
            throw failure
        } catch (failure: HttpException) {
            if (failure.code() == 401 || failure.code() == 403) throw EndpointAttemptFailure.Account("Username or password is incorrect.")
            if (failure.code() in 500..599) throw EndpointAttemptFailure.Unavailable(failure)
            throw EndpointAttemptFailure.Account("Account could not be authenticated.")
        } catch (failure: IOException) {
            throw EndpointAttemptFailure.Unavailable(failure)
        }
    }

    private fun api(serverUrl: String): XtreamApi =
        retrofitFactory(serverUrl.toHttpUrl().newBuilder().addPathSegment("").build().toString())
            .create(XtreamApi::class.java)

    private companion object {
        const val MANAGED_PROVIDER_PREFIX = "xtream-managed-"
    }

    private suspend fun <T> timedRequest(type: String, block: suspend () -> T): T {
        val started = QuickLoginBootstrapTrace.now()
        QuickLoginBootstrapTrace.mark("quicklogin_network_request_started", metadata = "request_type=$type")
        return runCatching { block() }
            .onSuccess { result ->
                val count = (result as? Collection<*>)?.size?.let { " item_count=$it" }.orEmpty()
                QuickLoginBootstrapTrace.mark("quicklogin_network_request_completed", started, "request_type=$type success=true$count")
            }
            .onFailure {
                QuickLoginBootstrapTrace.mark("quicklogin_network_request_completed", started, "request_type=$type success=false")
            }
            .getOrThrow()
    }

    suspend fun addProviderTwoPhase(input: XtreamCredentialsInput): XtreamImportState.Success = withContext(Dispatchers.IO) {
        val name = input.displayName.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Provider name is required.")
        val username = input.username.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Username is required.")
        val password = input.password.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Password is required.")

        val managedSelection = if (input.managed) {
            ensureNoManagedDuplicate(username)
            val manager = endpointManager
                ?: throw IllegalStateException("Watchio couldn't reach the service configuration. Please try again.")
            manager.resolve { endpoint -> authenticateEndpoint(endpoint.url, username, password) }
        } else null
        val normalizedUrl = managedSelection?.endpoint?.url ?: XtreamUrlNormalizer.normalize(input.serverUrl.orEmpty())
            ?: throw IllegalArgumentException("Enter a valid server URL.")
        val existing = if (input.managed) null else findExistingProvider(normalizedUrl, username)
        val providerId = existing?.let { ProviderId(it.id) }
            ?: ProviderId(if (input.managed) "$MANAGED_PROVIDER_PREFIX${UUID.randomUUID()}" else "xtream-${UUID.randomUUID()}")
        val previousCredentials = existing?.let { credentialStore.getXtreamCredentials(it.id) }
        var credentialsSaved = false
        var providerCreated = false
        try {
            val api = api(normalizedUrl)
            var started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_auth_started")
            val auth = managedSelection?.value ?: timedRequest("player_api_authentication") { api.playerInfo(username, password) }.toAuthInfo()
            if (!auth.authenticated) throw IllegalArgumentException("Incorrect username or password.")
            QuickLoginBootstrapTrace.mark("quicklogin_auth_completed", started)

            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_credentials_save_started")
            credentialStore.saveXtreamCredentials(providerId.value, XtreamCredentials(username, password))
            credentialsSaved = true
            QuickLoginBootstrapTrace.mark("quicklogin_credentials_save_completed", started)

            val now = clock.nowEpochMs()
            val provider = WatchioProvider(
                id = providerId,
                displayName = name,
                type = ProviderType.Xtream,
                serverUrl = normalizedUrl,
                createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
                lastRefreshAtEpochMs = existing?.lastRefreshAtEpochMs,
                enabled = true,
            )
            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_provider_save_started")
            database.providerDao().upsert(provider.toEntity())
            providerCreated = existing == null
            QuickLoginBootstrapTrace.mark("quicklogin_provider_save_completed", started, "row_count=1 reused=${existing != null}")

            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_live_sync_started")
            val liveCategories = timedRequest("live_categories") { api.liveCategories(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Live, index) }
            val live = timedRequest("live_streams") { api.liveStreams(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            database.withTransaction {
                database.categoryDao().replaceCategories(providerId.value, ContentType.Live.persisted, liveCategories.map { it.toEntity() })
                database.liveStreamDao().replaceLiveStreams(providerId.value, live.map { it.toEntity(now) })
                database.providerDao().upsert(provider.copy(lastRefreshAtEpochMs = now).toEntity())
            }
            QuickLoginBootstrapTrace.mark("quicklogin_live_sync_completed", started, "category_count=${liveCategories.size} item_count=${live.size}")

            started = QuickLoginBootstrapTrace.now()
            QuickLoginBootstrapTrace.mark("quicklogin_provider_select_started")
            settingsRepository.setSelectedProviderId(providerId)
            settingsRepository.setProviderExpiryEpochMs(providerId, auth.expiration?.toLongOrNull()?.let { it * 1_000L })
            settingsRepository.persistAccountMetadata(providerId, auth)
            settingsRepository.setDeviceModeOnboardingCompleted(true)
            settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Live, now)
            QuickLoginBootstrapTrace.mark("quicklogin_provider_select_completed", started)

            startDeferredCatalogSync(providerId, api, username, password)
            XtreamImportState.Success(providerId, live.size, 0, 0).also { _state.value = it }
        } catch (throwable: Throwable) {
            if (providerCreated) database.providerDao().deleteProviderAndCatalog(providerId.value)
            if (credentialsSaved) {
                if (previousCredentials == null) credentialStore.deleteProviderSecrets(providerId.value)
                else credentialStore.saveXtreamCredentials(providerId.value, previousCredentials)
            }
            throw throwable
        }
    }

    private fun startDeferredCatalogSync(providerId: ProviderId, api: XtreamApi, username: String, password: String) {
        synchronized(deferredJobs) {
            if (deferredJobs[providerId]?.isActive == true) return
            setCatalogSyncState(providerId, ContentType.Movie, CatalogSyncState.Syncing)
            setCatalogSyncState(providerId, ContentType.Series, CatalogSyncState.Syncing)
            deferredJobs[providerId] = backgroundScope.launch {
                onDeferredSyncStarted?.invoke(providerId)
                QuickLoginBootstrapTrace.mark("quicklogin_deferred_sync_started")
                coroutineScope {
                    val movies = async { syncDeferredMovies(providerId, api, username, password) }
                    val series = async { syncDeferredSeries(providerId, api, username, password) }
                    movies.await()
                    series.await()
                }
                QuickLoginBootstrapTrace.finishDeferredSync()
            }
        }
    }

    internal suspend fun awaitDeferredCatalogSync(providerId: ProviderId) {
        synchronized(deferredJobs) { deferredJobs[providerId] }?.join()
    }

    private suspend fun syncDeferredMovies(providerId: ProviderId, api: XtreamApi, username: String, password: String) {
        val started = QuickLoginBootstrapTrace.now()
        QuickLoginBootstrapTrace.mark("quicklogin_movies_background_started")
        runCatching {
            val categories = timedRequest("vod_categories") { api.vodCategories(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Movie, index) }
            val movies = timedRequest("vod_streams") { api.vodStreams(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            val now = clock.nowEpochMs()
            database.withTransaction {
                database.categoryDao().replaceCategories(providerId.value, ContentType.Movie.persisted, categories.map { it.toEntity() })
                database.vodDao().replaceMovies(providerId.value, movies.map { it.toEntity(now) })
            }
            onMoviesUpdated?.invoke(providerId)
            settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Movie, now)
            setCatalogSyncState(providerId, ContentType.Movie, CatalogSyncState.Ready)
            QuickLoginBootstrapTrace.mark("quicklogin_movies_background_completed", started, "success=true item_count=${movies.size}")
        }.onFailure {
            setCatalogSyncState(providerId, ContentType.Movie, CatalogSyncState.Failed)
            QuickLoginBootstrapTrace.mark("quicklogin_movies_background_completed", started, "success=false")
        }
    }

    private suspend fun syncDeferredSeries(providerId: ProviderId, api: XtreamApi, username: String, password: String) {
        val started = QuickLoginBootstrapTrace.now()
        QuickLoginBootstrapTrace.mark("quicklogin_series_background_started")
        runCatching {
            val categories = timedRequest("series_categories") { api.seriesCategories(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, ContentType.Series, index) }
            val series = timedRequest("series_list") { api.series(username, password) }
                .mapIndexedNotNull { index, dto -> dto.toDomain(providerId, index) }
            val now = clock.nowEpochMs()
            database.withTransaction {
                database.categoryDao().replaceCategories(providerId.value, ContentType.Series.persisted, categories.map { it.toEntity() })
                database.seriesDao().replaceSeries(providerId.value, series.map { it.toEntity(now) })
            }
            onSeriesUpdated?.invoke(providerId)
            settingsRepository.setSectionRefreshEpochMs(providerId, ContentType.Series, now)
            setCatalogSyncState(providerId, ContentType.Series, CatalogSyncState.Ready)
            QuickLoginBootstrapTrace.mark("quicklogin_series_background_completed", started, "success=true item_count=${series.size}")
        }.onFailure {
            setCatalogSyncState(providerId, ContentType.Series, CatalogSyncState.Failed)
            QuickLoginBootstrapTrace.mark("quicklogin_series_background_completed", started, "success=false")
        }
    }

    private fun setCatalogSyncState(providerId: ProviderId, contentType: ContentType, state: CatalogSyncState) {
        _catalogSyncStates.update { it + (CatalogSyncKey(providerId, contentType) to state) }
    }

    private suspend fun <T> timedRoom(operation: String, rowCount: Int, block: suspend () -> T): T {
        val started = QuickLoginBootstrapTrace.now()
        return block().also {
            QuickLoginBootstrapTrace.mark("quicklogin_room_operation_completed", started, "operation=$operation row_count=$rowCount")
        }
    }

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
