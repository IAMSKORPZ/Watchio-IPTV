package com.watchioiptv.nativeapp.data.epg

import androidx.room.withTransaction
import com.watchioiptv.nativeapp.core.database.EpgImportChannelEntity
import com.watchioiptv.nativeapp.core.database.EpgImportProgrammeEntity
import com.watchioiptv.nativeapp.core.database.EpgSourceEntity
import com.watchioiptv.nativeapp.core.database.WatchioDatabase
import com.watchioiptv.nativeapp.core.security.ProviderCredentialStore
import com.watchioiptv.nativeapp.core.security.SensitiveUrlMasker
import com.watchioiptv.nativeapp.core.util.TextNormalizer
import com.watchioiptv.nativeapp.core.util.WatchioClock
import com.watchioiptv.nativeapp.domain.model.ProviderType
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.brotli.dec.BrotliInputStream

class EpgRepository(
    private val database: WatchioDatabase,
    private val okHttpClient: OkHttpClient,
    private val credentialStore: ProviderCredentialStore,
    private val clock: WatchioClock,
    private val parser: XmlTvParser = XmlTvParser(),
    private val batchSize: Int = 1_000,
) {
    private val _state = MutableStateFlow<EpgImportState>(EpgImportState.Idle)
    val state: StateFlow<EpgImportState> = _state.asStateFlow()

    suspend fun upsertCustomSource(providerId: String, url: String): EpgSourceDescriptor = withContext(Dispatchers.IO) {
        val now = clock.nowEpochMs()
        val source = EpgSourceEntity(
            providerId = providerId,
            sourceId = "custom",
            sourceType = EpgSourceType.CustomUrl.persisted,
            url = url.trim(),
            enabled = true,
            priority = 100,
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
        )
        database.epgDao().upsertSource(source)
        source.toDescriptor()
    }

    suspend fun refresh(providerId: String, sourceId: String? = null): EpgImportResult = withContext(Dispatchers.IO) {
        val sources = sourceId?.let { id -> database.epgDao().getSources(providerId).filter { it.sourceId == id } }
            ?: discoverSources(providerId)
        var lastFailure: Throwable? = null
        for (source in sources) {
            try {
                return@withContext importSource(source)
            } catch (throwable: Throwable) {
                lastFailure = throwable
            }
        }
        throw lastFailure ?: IllegalStateException("No EPG source available.")
    }

    suspend fun currentNext(providerId: String, epgChannelId: String, now: Long): NowNext {
        val current = database.epgDao().getCurrentProgramme(providerId, epgChannelId, now)?.toGuide()
        val next = database.epgDao().getNextProgramme(providerId, epgChannelId, now)?.toGuide()
        return NowNext(current, next, progress(current, now))
    }

    suspend fun programmes(providerId: String, epgChannelId: String, from: Long, to: Long): List<GuideProgramme> =
        database.epgDao().getProgrammes(providerId, epgChannelId, from, to, 500).map { it.toGuide() }

    suspend fun guide(providerId: String, channelIds: List<String>, from: Long, to: Long): Map<String, List<GuideProgramme>> =
        if (channelIds.isEmpty()) emptyMap() else database.epgDao().getGuide(providerId, channelIds, from, to)
            .groupBy { it.epgChannelId }
            .mapValues { (_, rows) -> rows.map { it.toGuide() } }

    suspend fun prune(providerId: String): Int {
        val now = clock.nowEpochMs()
        return database.epgDao().prune(
            providerId = providerId,
            minEndEpochMs = now - KEEP_PAST_MS,
            maxStartEpochMs = now + KEEP_FUTURE_MS,
        )
    }

    private suspend fun createDefaultSource(providerId: String): EpgSourceEntity {
        val provider = database.providerDao().findById(providerId) ?: throw IllegalArgumentException("Provider not found.")
        val now = clock.nowEpochMs()
        val source = when (provider.type) {
            ProviderType.Xtream.persisted -> EpgSourceEntity(providerId, "xtream_xmltv", EpgSourceType.XtreamXmltv.persisted, null, true, 0, null, null, null, null, null, null, 0, 0, now, now)
            else -> throw IllegalStateException("No EPG source configured.")
        }
        database.epgDao().upsertSource(source)
        return source
    }

    private suspend fun discoverSources(providerId: String): List<EpgSourceEntity> {
        val existing = database.epgDao().getSources(providerId).filter { it.enabled }
        val provider = database.providerDao().findById(providerId) ?: throw IllegalArgumentException("Provider not found.")
        if (provider.type != ProviderType.Xtream.persisted) {
            if (existing.isEmpty()) throw IllegalStateException("No EPG source available.")
            return existing
        }
        val explicit = existing.filter { it.sourceType != EpgSourceType.XtreamXmltv.persisted }.sortedBy { it.priority }
        val default = existing.filter { it.sourceType == EpgSourceType.XtreamXmltv.persisted }.sortedBy { it.priority }
            .ifEmpty { listOf(createDefaultSource(providerId)) }
        return explicit + default
    }

    private suspend fun importSource(source: EpgSourceEntity): EpgImportResult {
        _state.value = EpgImportState.Importing(EpgImportStage.ResolvingSource)
        val resolvedUrl = resolveUrl(source)
        val requestBuilder = Request.Builder().url(resolvedUrl)
        source.etag?.let { requestBuilder.header("If-None-Match", it) }
        source.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }
        _state.value = EpgImportState.Importing(EpgImportStage.Downloading)
        val response = try {
            okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            markFailure(source, "Unable to download EPG.")
            throw IllegalStateException("Unable to download EPG.", e)
        }
        response.use {
            val now = clock.nowEpochMs()
            if (it.code == 304) {
                database.epgDao().upsertSource(source.copy(lastRefreshAtEpochMs = now, updatedAtEpochMs = now))
                val counts = database.epgDao().channelCount(source.providerId) to database.epgDao().programmeCount(source.providerId)
                _state.value = EpgImportState.Success(source.providerId, counts.first, counts.second)
                return EpgImportResult(source.providerId, counts.first, counts.second)
            }
            if (!it.isSuccessful) {
                markFailure(source, "Unable to download EPG.")
                throw IllegalStateException("Unable to download EPG.")
            }
            val body = it.body ?: throw IllegalStateException("EPG response was empty.")
            body.byteStream().use { raw ->
                val import = importStream(
                    source = source,
                    input = decodedStream(raw, it.header("Content-Encoding"), resolvedUrl),
                    etag = it.header("ETag"),
                    lastModified = it.header("Last-Modified"),
                )
                return import
            }
        }
    }

    private suspend fun importStream(
        source: EpgSourceEntity,
        input: InputStream,
        etag: String?,
        lastModified: String?,
    ): EpgImportResult {
        val sessionId = UUID.randomUUID().toString()
        val now = clock.nowEpochMs()
        val channels = ArrayList<EpgImportChannelEntity>(batchSize)
        val programmes = ArrayList<EpgImportProgrammeEntity>(batchSize)
        var channelCount = 0
        var programmeCount = 0
        try {
            _state.value = EpgImportState.Importing(EpgImportStage.Parsing)
            input.use { stream ->
                parser.parse(
                    stream,
                    onChannel = { channel ->
                        currentCoroutineContext().ensureActive()
                        channels += EpgImportChannelEntity(sessionId, source.providerId, channel.id, channel.displayName, TextNormalizer.normalizeForSearch(channel.displayName), channel.iconUrl, now)
                        channelCount++
                        if (channels.size >= batchSize) {
                            database.epgDao().upsertStagedChannels(channels.toList())
                            channels.clear()
                            _state.value = EpgImportState.Importing(EpgImportStage.Parsing, channelCount, programmeCount)
                        }
                    },
                    onProgramme = { programme ->
                        currentCoroutineContext().ensureActive()
                        programmes += EpgImportProgrammeEntity(sessionId, source.providerId, programme.channelId, programme.programmeId, programme.title, programme.description, programme.startEpochMs, programme.endEpochMs, now)
                        programmeCount++
                        if (programmes.size >= batchSize) {
                            database.epgDao().upsertStagedProgrammes(programmes.toList())
                            programmes.clear()
                            _state.value = EpgImportState.Importing(EpgImportStage.Parsing, channelCount, programmeCount)
                        }
                    },
                )
            }
            if (channels.isNotEmpty()) database.epgDao().upsertStagedChannels(channels.toList())
            if (programmes.isNotEmpty()) database.epgDao().upsertStagedProgrammes(programmes.toList())
            if (channelCount == 0 && programmeCount == 0) throw IllegalStateException("EPG does not contain supported data.")
            _state.value = EpgImportState.Importing(EpgImportStage.Saving, channelCount, programmeCount)
            database.withTransaction {
                database.epgDao().replaceFromStaging(source.providerId, sessionId, batchSize)
                val pruned = database.epgDao().prune(source.providerId, now - KEEP_PAST_MS, now + KEEP_FUTURE_MS)
                val finalProgrammes = programmeCount - pruned
                database.epgDao().upsertSource(
                    source.copy(
                        lastRefreshAtEpochMs = now,
                        lastSuccessAtEpochMs = now,
                        lastErrorAtEpochMs = null,
                        lastError = null,
                        etag = etag,
                        lastModified = lastModified,
                        channelCount = channelCount,
                        programmeCount = finalProgrammes.coerceAtLeast(0),
                        updatedAtEpochMs = now,
                    ),
                )
                programmeCount = finalProgrammes.coerceAtLeast(0)
            }
            _state.value = EpgImportState.Success(source.providerId, channelCount, programmeCount)
            return EpgImportResult(source.providerId, channelCount, programmeCount)
        } catch (throwable: Throwable) {
            database.epgDao().deleteStagedProgrammes(sessionId)
            database.epgDao().deleteStagedChannels(sessionId)
            markFailure(source, "EPG import failed.")
            _state.value = EpgImportState.Failure(EpgImportStage.Parsing, "EPG import failed.")
            throw throwable
        }
    }

    private suspend fun resolveUrl(source: EpgSourceEntity): String {
        if (source.sourceType == EpgSourceType.XtreamXmltv.persisted) {
            val provider = database.providerDao().findById(source.providerId) ?: throw IllegalArgumentException("Provider not found.")
            val credentials = credentialStore.getXtreamCredentials(source.providerId) ?: throw IllegalStateException("Provider credentials unavailable.")
            val base = provider.serverUrl?.trimEnd('/') ?: throw IllegalStateException("Provider server unavailable.")
            return "$base/xmltv.php".toHttpUrl().newBuilder()
                .addQueryParameter("username", credentials.username)
                .addQueryParameter("password", credentials.password)
                .build()
                .toString()
        }
        return source.url?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("EPG source URL unavailable.")
    }

    private fun decodedStream(input: InputStream, encoding: String?, url: String): InputStream {
        val lowerEncoding = encoding.orEmpty().lowercase()
        val lowerUrl = url.lowercase()
        return when {
            "br" in lowerEncoding || lowerUrl.endsWith(".br") -> BrotliInputStream(input)
            "gzip" in lowerEncoding || lowerUrl.endsWith(".gz") || lowerUrl.endsWith(".gzip") -> GZIPInputStream(input)
            else -> input
        }
    }

    private suspend fun markFailure(source: EpgSourceEntity, message: String) {
        val now = clock.nowEpochMs()
        database.epgDao().upsertSource(source.copy(lastRefreshAtEpochMs = now, lastErrorAtEpochMs = now, lastError = SensitiveUrlMasker.mask(message), updatedAtEpochMs = now))
    }

    private fun progress(current: GuideProgramme?, now: Long): Float {
        current ?: return 0f
        val duration = current.endEpochMs - current.startEpochMs
        if (duration <= 0) return 0f
        return ((now - current.startEpochMs).toFloat() / duration).coerceIn(0f, 1f)
    }

    private fun EpgSourceEntity.toDescriptor() = EpgSourceDescriptor(providerId, sourceId, EpgSourceType.fromPersisted(sourceType), url, enabled, priority)
    private fun com.watchioiptv.nativeapp.core.database.EpgProgrammeEntity.toGuide() = GuideProgramme(programmeId, title, description, startTimeEpochMs, endTimeEpochMs)

    companion object {
        const val KEEP_PAST_MS: Long = 48L * 60L * 60L * 1000L
        const val KEEP_FUTURE_MS: Long = 72L * 60L * 60L * 1000L
    }
}
