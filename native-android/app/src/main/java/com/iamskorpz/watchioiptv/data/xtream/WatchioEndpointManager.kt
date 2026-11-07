package com.iamskorpz.watchioiptv.data.xtream

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class WatchioEndpoint(val id: String, val url: String, val priority: Int = 0, val enabled: Boolean = true)

data class WatchioEndpointConfig(
    val version: Int,
    val endpoints: List<WatchioEndpoint>,
    val remoteConfigUrl: String? = null,
)

object WatchioEndpointConfigParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(raw: String): WatchioEndpointConfig {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: error("Endpoint config must be an object.")
        val allowed = setOf("version", "remoteConfigUrl", "endpoints")
        require(root.keys.all { it in allowed }) { "Endpoint config contains unsupported fields." }
        val version = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull()
        require(version == 1) { "Unsupported endpoint config version." }
        val remoteConfigUrl = (root["remoteConfigUrl"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        remoteConfigUrl?.let { require(validUrl(it, httpsOnly = true) != null) { "Remote config URL must use HTTPS." } }
        val entries = root["endpoints"] as? JsonArray ?: error("Endpoint config requires endpoints.")
        val endpoints = entries.mapIndexedNotNull { index, value ->
            when (value) {
                is JsonPrimitive -> normalize(value.content)?.let { WatchioEndpoint("endpoint-$index", it, index) }
                is JsonObject -> {
                    require(value.keys.all { it in setOf("id", "url", "priority", "enabled") }) { "Endpoint entry contains unsupported fields." }
                    val enabled = (value["enabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true
                    if (!enabled) null else {
                        val url = normalize((value["url"] as? JsonPrimitive)?.content.orEmpty())
                            ?: error("Endpoint URL is invalid.")
                        val id = (value["id"] as? JsonPrimitive)?.content?.trim().orEmpty().ifBlank { "endpoint-$index" }
                        val priority = (value["priority"] as? JsonPrimitive)?.content?.toIntOrNull() ?: index
                        WatchioEndpoint(id, url, priority)
                    }
                }
                else -> error("Endpoint entry is invalid.")
            }
        }.sortedWith(compareBy<WatchioEndpoint> { it.priority }.thenBy { it.id })
            .distinctBy { it.url }
        return WatchioEndpointConfig(version, endpoints, remoteConfigUrl)
    }

    fun normalize(value: String): String? = validUrl(value, httpsOnly = false)

    private fun validUrl(value: String, httpsOnly: Boolean): String? = runCatching {
        val uri = URI(value.trim())
        require(uri.scheme != null && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null)
        require(if (httpsOnly) uri.scheme.equals("https", true) else uri.scheme.equals("http", true) || uri.scheme.equals("https", true))
        val path = uri.path.orEmpty().trimEnd('/')
        URI(uri.scheme.lowercase(), null, uri.host.lowercase(), uri.port, path.ifBlank { null }, null, null).toString()
    }.getOrNull()
}

interface WatchioEndpointConfigSource {
    suspend fun remote(): String?
    suspend fun cached(): String?
    suspend fun bundled(): String
    suspend fun saveCache(raw: String)
    fun lastWorking(): String?
    fun saveLastWorking(url: String)
    fun clearLastWorking()
}

sealed class EndpointAttemptFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Account(message: String) : EndpointAttemptFailure(message)
    class Unavailable(cause: Throwable) : EndpointAttemptFailure("Endpoint unavailable.", cause)
}

data class EndpointSelection<T>(val endpoint: WatchioEndpoint, val value: T)

enum class ProviderConnectivity { NotChecked, Connected, TemporarilyUnavailable }
enum class ProviderAuthentication { NotChecked, Verified, Rejected }
enum class ProviderStreaming { NotChecked, Available, TemporarilyUnavailable }
enum class ProviderFailureKind { Network, Timeout, Http, Authentication, Playback }

data class ProviderHealthState(
    val selectedEndpointId: String? = null,
    val connectivity: ProviderConnectivity = ProviderConnectivity.NotChecked,
    val authentication: ProviderAuthentication = ProviderAuthentication.NotChecked,
    val streaming: ProviderStreaming = ProviderStreaming.NotChecked,
    val lastFailure: ProviderFailureKind? = null,
)

class WatchioEndpointManager(
    private val source: WatchioEndpointConfigSource,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) },
) {
    private val mutex = Mutex()
    @Volatile private var memoryConfig: WatchioEndpointConfig? = null
    private val _activeEndpoint = MutableStateFlow<WatchioEndpoint?>(null)
    val activeEndpoint: StateFlow<WatchioEndpoint?> = _activeEndpoint.asStateFlow()
    private val _activeEndpointOnline = MutableStateFlow<Boolean?>(null)
    val activeEndpointOnline: StateFlow<Boolean?> = _activeEndpointOnline.asStateFlow()
    private val _health = MutableStateFlow(ProviderHealthState())
    val health: StateFlow<ProviderHealthState> = _health.asStateFlow()
    private val _configuredEndpoints = MutableStateFlow<List<WatchioEndpoint>>(emptyList())
    val configuredEndpoints: StateFlow<List<WatchioEndpoint>> = _configuredEndpoints.asStateFlow()

    suspend fun <T> resolve(preferred: String? = null, attempt: suspend (WatchioEndpoint) -> T): EndpointSelection<T> = mutex.withLock {
        var candidates = candidatesUnlocked(preferred)
        if (candidates.isEmpty()) throw IllegalStateException("Watchio couldn't reach the service configuration. Please try again.")
        var lastFailure: Throwable? = null
        val attemptedUrls = mutableSetOf<String>()
        repeat(CONFIG_PASSES) { pass ->
            for (candidate in candidates.filterNot { it.url in attemptedUrls }) {
                attemptedUrls += candidate.url
                try {
                    val result = attemptWithRetry(candidate, attempt)
                    source.saveLastWorking(candidate.url)
                    _activeEndpoint.value = candidate
                    _activeEndpointOnline.value = true
                    _health.value = ProviderHealthState(
                        selectedEndpointId = candidate.id,
                        connectivity = ProviderConnectivity.Connected,
                        authentication = ProviderAuthentication.Verified,
                        streaming = _health.value.streaming.takeIf { _health.value.selectedEndpointId == candidate.id }
                            ?: ProviderStreaming.NotChecked,
                    )
                    return@withLock EndpointSelection(candidate, result)
                } catch (failure: EndpointAttemptFailure.Account) {
                    _health.value = ProviderHealthState(
                        selectedEndpointId = candidate.id,
                        connectivity = ProviderConnectivity.Connected,
                        authentication = ProviderAuthentication.Rejected,
                        lastFailure = ProviderFailureKind.Authentication,
                    )
                    throw failure
                } catch (failure: EndpointAttemptFailure.Unavailable) {
                    lastFailure = failure.cause ?: failure
                }
            }
            if (pass < CONFIG_PASSES - 1) {
                memoryConfig = loadConfig()
                _configuredEndpoints.value = memoryConfig!!.endpoints
                candidates = candidatesUnlocked(preferred)
            }
        }
        _activeEndpointOnline.value = false
        _health.value = _health.value.copy(
            selectedEndpointId = _activeEndpoint.value?.id,
            connectivity = ProviderConnectivity.TemporarilyUnavailable,
            lastFailure = classifyFailure(lastFailure),
        )
        throw IllegalStateException("Watchio couldn't connect to the service. Please try again shortly.", lastFailure)
    }

    suspend fun candidates(preferred: String? = null): List<WatchioEndpoint> = mutex.withLock {
        candidatesUnlocked(preferred)
    }

    suspend fun refreshConfig(): List<WatchioEndpoint> = mutex.withLock {
        memoryConfig = loadConfig()
        _configuredEndpoints.value = memoryConfig!!.endpoints
        if (_activeEndpoint.value?.url !in memoryConfig!!.endpoints.map { it.url }) {
            _activeEndpoint.value = null
            _activeEndpointOnline.value = null
            _health.value = ProviderHealthState()
        }
        candidatesUnlocked()
    }

    suspend fun restoreActive(url: String?): WatchioEndpoint? = mutex.withLock {
        val normalized = url?.let(WatchioEndpointConfigParser::normalize)
        val restored = candidatesUnlocked().firstOrNull { it.url == normalized }
        _activeEndpoint.value = restored
        _activeEndpointOnline.value = null
        _health.value = ProviderHealthState(selectedEndpointId = restored?.id)
        restored
    }

    fun clearActive() {
        _activeEndpoint.value = null
        _activeEndpointOnline.value = null
        _health.value = ProviderHealthState()
    }

    suspend fun <T> switch(current: String, attempt: suspend (WatchioEndpoint) -> T): EndpointSelection<T> = mutex.withLock {
        val configured = candidatesUnlocked().sortedWith(compareBy<WatchioEndpoint> { it.priority }.thenBy { it.id })
        require(configured.size > 1) { "No alternate server is available." }
        val normalizedCurrent = WatchioEndpointConfigParser.normalize(current)
        val currentIndex = configured.indexOfFirst { it.url == normalizedCurrent }.coerceAtLeast(0)
        val target = configured[(currentIndex + 1).mod(configured.size)]
        val value = attemptWithRetry(target, attempt)
        source.saveLastWorking(target.url)
        _activeEndpoint.value = target
        _activeEndpointOnline.value = true
        _health.value = ProviderHealthState(target.id, ProviderConnectivity.Connected, ProviderAuthentication.Verified)
        EndpointSelection(target, value)
    }

    suspend fun <T> switchTo(endpointId: String, attempt: suspend (WatchioEndpoint) -> T): EndpointSelection<T> = mutex.withLock {
        val target = candidatesUnlocked().firstOrNull { it.id.equals(endpointId, ignoreCase = true) }
            ?: throw IllegalArgumentException("Server is no longer available.")
        val value = try {
            attemptWithRetry(target, attempt)
        } catch (failure: EndpointAttemptFailure.Unavailable) {
            if (_activeEndpoint.value?.url == target.url) {
                _activeEndpointOnline.value = false
                _health.value = _health.value.copy(
                    connectivity = ProviderConnectivity.TemporarilyUnavailable,
                    lastFailure = classifyFailure(failure.cause ?: failure),
                )
            }
            throw failure
        } catch (failure: EndpointAttemptFailure.Account) {
            _health.value = ProviderHealthState(
                target.id,
                ProviderConnectivity.Connected,
                ProviderAuthentication.Rejected,
                lastFailure = ProviderFailureKind.Authentication,
            )
            throw failure
        }
        source.saveLastWorking(target.url)
        _activeEndpoint.value = target
        _activeEndpointOnline.value = true
        _health.value = ProviderHealthState(target.id, ProviderConnectivity.Connected, ProviderAuthentication.Verified)
        EndpointSelection(target, value)
    }

    suspend fun nextCandidate(current: String): WatchioEndpoint? = mutex.withLock {
        val configured = candidatesUnlocked().sortedWith(compareBy<WatchioEndpoint> { it.priority }.thenBy { it.id })
        if (configured.isEmpty()) return@withLock null
        val normalizedCurrent = WatchioEndpointConfigParser.normalize(current)
        val currentIndex = configured.indexOfFirst { it.url == normalizedCurrent }
        configured[(currentIndex + 1).mod(configured.size)]
    }

    fun reportStreamingAvailable() {
        if (_activeEndpoint.value == null) return
        _health.value = _health.value.copy(streaming = ProviderStreaming.Available, lastFailure = null)
    }

    fun reportStreamingFailure(failure: Throwable) {
        if (_activeEndpoint.value == null) return
        _health.value = _health.value.copy(
            streaming = ProviderStreaming.TemporarilyUnavailable,
            lastFailure = classifyFailure(failure, playback = true),
        )
    }

    private suspend fun <T> attemptWithRetry(
        endpoint: WatchioEndpoint,
        attempt: suspend (WatchioEndpoint) -> T,
    ): T {
        var lastUnavailable: EndpointAttemptFailure.Unavailable? = null
        repeat(CONNECTION_ATTEMPTS) { index ->
            try {
                return attempt(endpoint)
            } catch (failure: EndpointAttemptFailure.Account) {
                throw failure
            } catch (failure: EndpointAttemptFailure.Unavailable) {
                lastUnavailable = failure
                if (index < CONNECTION_ATTEMPTS - 1) retryDelay(RETRY_BACKOFF_MS * (index + 1))
            }
        }
        throw checkNotNull(lastUnavailable)
    }

    private fun classifyFailure(failure: Throwable?, playback: Boolean = false): ProviderFailureKind = when {
        playback -> ProviderFailureKind.Playback
        failure is java.net.SocketTimeoutException -> ProviderFailureKind.Timeout
        failure is retrofit2.HttpException -> ProviderFailureKind.Http
        else -> ProviderFailureKind.Network
    }

    private suspend fun candidatesUnlocked(preferred: String? = null): List<WatchioEndpoint> {
        val config = memoryConfig ?: loadConfig().also { memoryConfig = it }
        _configuredEndpoints.value = config.endpoints
        val byUrl = config.endpoints.associateBy { it.url }
        return (listOfNotNull(preferred?.let(WatchioEndpointConfigParser::normalize), source.lastWorking()?.let(WatchioEndpointConfigParser::normalize))
            .mapNotNull(byUrl::get) + config.endpoints)
            .distinctBy { it.url }
    }

    private suspend fun loadConfig(): WatchioEndpointConfig {
        val remote = source.remote()
        if (remote != null) runCatching { WatchioEndpointConfigParser.parse(remote) }
            .getOrNull()?.takeIf { it.endpoints.isNotEmpty() }?.let { valid -> source.saveCache(remote); return validateLastWorking(valid) }
        source.cached()?.let { cached -> runCatching { WatchioEndpointConfigParser.parse(cached) }.getOrNull() }
            ?.takeIf { it.endpoints.isNotEmpty() }?.let { return validateLastWorking(it) }
        return validateLastWorking(WatchioEndpointConfigParser.parse(source.bundled()))
    }

    private fun validateLastWorking(config: WatchioEndpointConfig): WatchioEndpointConfig {
        val lastWorking = source.lastWorking()?.let(WatchioEndpointConfigParser::normalize)
        if (lastWorking != null && config.endpoints.none { it.url == lastWorking }) source.clearLastWorking()
        return config
    }

    private companion object {
        const val CONFIG_PASSES = 2
        const val CONNECTION_ATTEMPTS = 2
        const val RETRY_BACKOFF_MS = 250L
    }
}

class AndroidWatchioEndpointConfigSource(
    context: Context,
    private val client: OkHttpClient,
) : WatchioEndpointConfigSource {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("watchio_endpoint_config", Context.MODE_PRIVATE)
    private val bundledRaw by lazy { appContext.assets.open("watchio_endpoints.json").bufferedReader().use { it.readText() } }

    override suspend fun remote(): String? = withContext(Dispatchers.IO) {
        val url = runCatching { WatchioEndpointConfigParser.parse(bundledRaw).remoteConfigUrl }.getOrNull() ?: return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    override suspend fun cached(): String? = preferences.getString(KEY_CACHE, null)
    override suspend fun bundled(): String = bundledRaw
    override suspend fun saveCache(raw: String) { preferences.edit { putString(KEY_CACHE, raw) } }
    override fun lastWorking(): String? = preferences.getString(KEY_LAST_WORKING, null)
    override fun saveLastWorking(url: String) { preferences.edit { putString(KEY_LAST_WORKING, url) } }
    override fun clearLastWorking() { preferences.edit { remove(KEY_LAST_WORKING) } }

    private companion object {
        const val KEY_CACHE = "last_valid_config"
        const val KEY_LAST_WORKING = "last_working_endpoint"
    }
}
