package com.watchioiptv.nativeapp.data.m3u

import com.watchioiptv.nativeapp.core.database.CategoryEntity
import com.watchioiptv.nativeapp.core.database.EpgSourceEntity
import com.watchioiptv.nativeapp.core.database.M3uImportItemEntity
import com.watchioiptv.nativeapp.core.database.ProviderEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.model.ProviderId
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.domain.model.ContentType
import com.watchioiptv.nativeapp.domain.model.ProviderType
import com.watchioiptv.nativeapp.data.epg.EpgSourceType
import com.watchioiptv.nativeapp.domain.repository.SettingsRepository
import androidx.room.withTransaction
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class M3uRepository(
    private val database: WatchioDatabase,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val clock: WatchioClock,
    private val openLocalInputStream: suspend (String) -> InputStream?,
    private val parser: M3uParser = M3uParser(),
    private val batchSize: Int = 1_000,
) {
    var onMoviesUpdated: ((ProviderId) -> Unit)? = null
    var onSeriesUpdated: ((ProviderId) -> Unit)? = null
    private val _state = MutableStateFlow<M3uImportState>(M3uImportState.Idle)
    val state: StateFlow<M3uImportState> = _state.asStateFlow()

    suspend fun addUrlProvider(input: M3uUrlInput): String = withContext(Dispatchers.IO) {
        validateName(input.displayName)
        val normalizedUrl = normalizePlaylistUrl(input.playlistUrl)
        if (database.providerDao().findByTypeAndServer(ProviderType.M3uUrl.persisted, normalizedUrl).isNotEmpty()) {
            throw IllegalStateException("Provider appears to already exist.")
        }
        val providerId = UUID.randomUUID().toString()
        importUrlProvider(providerId, input.displayName.trim(), normalizedUrl, input.userAgent, createProvider = true)
        providerId
    }

    suspend fun addFileProvider(input: M3uFileInput): String = withContext(Dispatchers.IO) {
        validateName(input.displayName)
        val uri = input.uri.trim()
        require(uri.isNotBlank()) { "Choose a playlist file." }
        if (database.providerDao().findByTypeAndServer(ProviderType.M3uFile.persisted, uri).isNotEmpty()) {
            throw IllegalStateException("Provider appears to already exist.")
        }
        val providerId = UUID.randomUUID().toString()
        importFileProvider(providerId, input.displayName.trim(), uri, createProvider = true)
        providerId
    }

    suspend fun refresh(providerId: String) = withContext(Dispatchers.IO) {
        val provider = database.providerDao().findById(providerId) ?: throw IllegalArgumentException("Provider not found.")
        when (provider.type) {
            ProviderType.M3uUrl.persisted -> importUrlProvider(provider.id, provider.displayName, provider.serverUrl.orEmpty(), null, createProvider = false)
            ProviderType.M3uFile.persisted -> importFileProvider(provider.id, provider.displayName, provider.serverUrl.orEmpty(), createProvider = false)
            else -> throw IllegalArgumentException("Provider is not an M3U provider.")
        }
    }

    suspend fun refreshSection(providerId: String, contentType: ContentType) = withContext(Dispatchers.IO) {
        require(contentType == ContentType.Live || contentType == ContentType.Movie || contentType == ContentType.Series) {
            "Unsupported M3U refresh section."
        }
        val provider = database.providerDao().findById(providerId) ?: throw IllegalArgumentException("Provider not found.")
        when (provider.type) {
            ProviderType.M3uUrl.persisted -> importUrlProvider(provider.id, provider.displayName, provider.serverUrl.orEmpty(), null, createProvider = false, replaceTypes = setOf(contentType))
            ProviderType.M3uFile.persisted -> importFileProvider(provider.id, provider.displayName, provider.serverUrl.orEmpty(), createProvider = false, replaceTypes = setOf(contentType))
            else -> throw IllegalArgumentException("Provider is not an M3U provider.")
        }
    }

    suspend fun counts(providerId: String): M3uCounts = M3uCounts(
        liveCount = database.m3uItemDao().countByProviderAndType(providerId, ContentType.Live.persisted),
        movieCount = database.m3uItemDao().countByProviderAndType(providerId, ContentType.Movie.persisted),
        seriesCount = database.m3uItemDao().countByProviderAndType(providerId, ContentType.Series.persisted),
    )

    private suspend fun importUrlProvider(
        providerId: String,
        displayName: String,
        playlistUrl: String,
        userAgent: String?,
        createProvider: Boolean,
        replaceTypes: Set<ContentType> = setOf(ContentType.Live, ContentType.Movie, ContentType.Series),
    ) {
        _state.value = M3uImportState.Importing(M3uImportStage.OpeningPlaylist)
        val requestBuilder = Request.Builder().url(playlistUrl)
        userAgent?.trim()?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("User-Agent", it) }
        val response = try {
            okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            fail(M3uImportStage.OpeningPlaylist, "Unable to download playlist.", e)
        }
        response.use {
            if (!it.isSuccessful) fail(M3uImportStage.OpeningPlaylist, "Unable to download playlist.")
            val body = it.body ?: fail(M3uImportStage.OpeningPlaylist, "Playlist is empty.")
            body.byteStream().use { stream ->
                importStream(providerId, displayName, ProviderType.M3uUrl, playlistUrl, stream, createProvider, replaceTypes)
            }
        }
    }

    private suspend fun importFileProvider(
        providerId: String,
        displayName: String,
        uri: String,
        createProvider: Boolean,
        replaceTypes: Set<ContentType> = setOf(ContentType.Live, ContentType.Movie, ContentType.Series),
    ) {
        _state.value = M3uImportState.Importing(M3uImportStage.OpeningPlaylist)
        val stream = openLocalInputStream(uri)
            ?: fail(M3uImportStage.OpeningPlaylist, "Playlist file is no longer available. Please select it again.")
        stream.use {
            importStream(providerId, displayName, ProviderType.M3uFile, uri, it, createProvider, replaceTypes)
        }
    }

    private suspend fun importStream(
        providerId: String,
        displayName: String,
        providerType: ProviderType,
        source: String,
        inputStream: InputStream,
        createProvider: Boolean,
        replaceTypes: Set<ContentType>,
    ) {
        val sessionId = UUID.randomUUID().toString()
        val categoryTracker = LinkedHashMap<String, CategoryEntity>()
        var live = 0
        var movies = 0
        var series = 0
        var headerEpgUrl: String? = null
        val stagedBatch = ArrayList<M3uImportItemEntity>(batchSize)
        val now = clock.nowEpochMs()

        try {
            _state.value = M3uImportState.Importing(M3uImportStage.ReadingPlaylist)
            val parsedCount = parser.parse(
                inputStream,
                onHeaderEpgUrl = { headerEpgUrl = it },
            ) { item ->
                currentCoroutineContext().ensureActive()
                when (item.contentType) {
                    ContentType.Live -> live += 1
                    ContentType.Movie -> movies += 1
                    ContentType.Series -> series += 1
                    ContentType.Episode -> Unit
                }
                val categoryId = categoryId(item.contentType, item.groupTitle)
                categoryTracker.putIfAbsent(
                    "${item.contentType.persisted}:$categoryId",
                    CategoryEntity(
                        providerId = providerId,
                        categoryId = categoryId,
                        name = item.groupTitle,
                        normalizedName = TextNormalizer.normalizeForSearch(item.groupTitle),
                        parentId = null,
                        contentType = item.contentType.persisted,
                        serverOrder = categoryTracker.size,
                    ),
                )
                stagedBatch += item.toStaged(providerId, sessionId, categoryId, now)
                if (stagedBatch.size >= batchSize) {
                    database.m3uItemDao().upsertStaged(stagedBatch.toList())
                    stagedBatch.clear()
                    _state.value = M3uImportState.Importing(M3uImportStage.ReadingPlaylist, live, movies, series)
                }
            }
            if (stagedBatch.isNotEmpty()) database.m3uItemDao().upsertStaged(stagedBatch.toList())
            if (parsedCount == 0) fail(M3uImportStage.ReadingPlaylist, "Playlist does not contain any supported items.")

            _state.value = M3uImportState.Importing(M3uImportStage.Saving, live, movies, series)
            database.withTransaction {
                if (createProvider) {
                    database.providerDao().upsert(
                        ProviderEntity(
                            id = providerId,
                            displayName = displayName,
                            type = providerType.persisted,
                            serverUrl = source,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                            lastRefreshAtEpochMs = now,
                            enabled = true,
                        ),
                    )
                } else {
                    val existing = database.providerDao().findById(providerId)
                        ?: throw IllegalArgumentException("Provider not found.")
                    database.providerDao().upsert(
                        existing.copy(
                            updatedAtEpochMs = now,
                            lastRefreshAtEpochMs = now,
                            enabled = true,
                        ),
                    )
                }
                replaceTypes.forEach { type ->
                    database.categoryDao().deleteByProviderAndType(providerId, type.persisted)
                }
                database.categoryDao().upsertAll(categoryTracker.values.filter { ContentType.fromPersisted(it.contentType) in replaceTypes })
                if (replaceTypes.containsAll(listOf(ContentType.Live, ContentType.Movie, ContentType.Series))) {
                    database.m3uItemDao().replaceFromStaging(providerId, sessionId, batchSize)
                } else {
                    replaceTypes.forEach { type ->
                        database.m3uItemDao().replaceTypeFromStaging(providerId, sessionId, type.persisted, batchSize)
                    }
                    database.m3uItemDao().deleteStaged(sessionId)
                }
                if (replaceTypes.contains(ContentType.Movie)) {
                    onMoviesUpdated?.invoke(ProviderId(providerId))
                }
                if (replaceTypes.contains(ContentType.Series)) {
                    onSeriesUpdated?.invoke(ProviderId(providerId))
                }
                headerEpgUrl?.trim()?.takeIf { it.isNotBlank() }?.let { epgUrl ->
                    database.epgDao().upsertSource(
                        EpgSourceEntity(
                            providerId = providerId,
                            sourceId = "m3u_header",
                            sourceType = EpgSourceType.M3uHeader.persisted,
                            url = epgUrl,
                            enabled = true,
                            priority = 10,
                            lastRefreshAtEpochMs = null,
                            lastSuccessAtEpochMs = null,
                            lastErrorAtEpochMs = null,
                            lastError = null,
                            etag = null,
                            lastModified = null,
                            channelCount = 0,
                            programmeCount = 0,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now,
                        ),
                    )
                }
            }
            settingsRepository.setSelectedProviderId(ProviderId(providerId))
            settingsRepository.setDeviceModeOnboardingCompleted(true)
            replaceTypes.forEach { type -> settingsRepository.setSectionRefreshEpochMs(ProviderId(providerId), type, now) }
            _state.value = M3uImportState.Success(providerId, live, movies, series)
        } catch (throwable: Throwable) {
            database.m3uItemDao().deleteStaged(sessionId)
            if (throwable is M3uImportException) {
                _state.value = M3uImportState.Failure(throwable.stage, throwable.safeMessage)
                throw IllegalStateException(throwable.safeMessage, throwable)
            }
            _state.value = M3uImportState.Failure(M3uImportStage.ReadingPlaylist, "Playlist format is invalid.")
            throw throwable
        }
    }

    private fun ParsedM3uItem.toStaged(
        providerId: String,
        sessionId: String,
        categoryId: String,
        now: Long,
    ): M3uImportItemEntity {
        val itemId = stableItemId(providerId, this)
        return M3uImportItemEntity(
            sessionId = sessionId,
            providerId = providerId,
            itemId = itemId,
            directUrl = url,
            name = name,
            normalizedName = TextNormalizer.normalizeForSearch(name),
            tvgId = tvgId,
            tvgName = tvgName,
            tvgLogo = tvgLogo,
            tvgUrl = tvgUrl,
            tvgRec = tvgRec,
            tvgShift = tvgShift,
            groupTitle = groupTitle,
            groupName = groupName,
            categoryId = categoryId,
            userAgent = userAgent,
            referrer = referrer,
            catchupType = catchupType,
            catchupSource = catchupSource,
            catchupDays = catchupDays,
            timeshiftHours = timeshiftHours,
            channelNumber = channelNumber,
            contentType = contentType.persisted,
            seriesName = seriesName,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            playlistOrder = playlistOrder,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Provider name is required." }
    }

    fun normalizePlaylistUrl(value: String): String {
        val candidate = value.trim()
        require(candidate.isNotBlank()) { "Playlist URL is required." }
        val withScheme = if ("://" in candidate) candidate else "http://$candidate"
        val parsed = withScheme.toHttpUrlOrNull() ?: throw IllegalArgumentException("Playlist URL is invalid.")
        return parsed.toString()
    }

    private fun categoryId(contentType: ContentType, group: String): String =
        sha256("${contentType.persisted}|${TextNormalizer.normalizeForSearch(group)}").take(24)

    private fun stableItemId(providerId: String, item: ParsedM3uItem): String {
        val base = if (!item.tvgId.isNullOrBlank()) {
            "${item.contentType.persisted}|${item.tvgId}|${TextNormalizer.normalizeForSearch(item.name)}|${TextNormalizer.normalizeForSearch(item.groupTitle)}"
        } else {
            "${item.contentType.persisted}|${TextNormalizer.normalizeForSearch(item.name)}|${TextNormalizer.normalizeForSearch(item.groupTitle)}|${item.url.trim()}"
        }
        return sha256("$providerId|$base").take(32)
    }

    private fun fail(stage: M3uImportStage, message: String, cause: Throwable? = null): Nothing {
        throw M3uImportException(stage, message, cause)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private class M3uImportException(
        val stage: M3uImportStage,
        val safeMessage: String,
        cause: Throwable?,
    ) : RuntimeException("${safeMessage} ${SensitiveUrlMasker.mask(cause?.message.orEmpty())}", cause)
}
