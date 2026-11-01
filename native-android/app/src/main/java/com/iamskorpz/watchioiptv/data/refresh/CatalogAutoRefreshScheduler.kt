package com.iamskorpz.watchioiptv.data.refresh

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.iamskorpz.watchioiptv.core.database.ProviderEntity
import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.player.WatchioPlayerState
import com.iamskorpz.watchioiptv.core.util.WatchioClock
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import com.iamskorpz.watchioiptv.domain.repository.SettingsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

fun interface NetworkConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidNetworkConnectivityChecker(
    context: Context,
) : NetworkConnectivityChecker {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun isOnline(): Boolean = runCatching {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        val activeNetwork = connectivityManager.activeNetwork ?: return@runCatching false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}

fun WatchioPlayerState.isActivePlaybackForRefreshDeferral(): Boolean = when (this) {
    is WatchioPlayerState.Connecting,
    is WatchioPlayerState.Buffering,
    is WatchioPlayerState.Playing,
    is WatchioPlayerState.Paused,
    is WatchioPlayerState.Recovering -> true
    is WatchioPlayerState.Idle,
    is WatchioPlayerState.Ended,
    is WatchioPlayerState.Failed,
    is WatchioPlayerState.Released -> false
}

internal fun isPlaybackRouteForAutoRefreshDeferral(
    route: String?,
    hasPendingSportsChannel: Boolean = false,
): Boolean {
    if (hasPendingSportsChannel) return true
    if (route == null) return false
    return route == "live" ||
        route.startsWith("live/fullscreen") ||
        route.startsWith("movies/player/") ||
        route.startsWith("series/player/")
}

data class SectionFailureState(
    val consecutiveFailures: Int = 0,
    val lastFailureAtEpochMs: Long = 0L,
    val nextRetryAllowedAtEpochMs: Long = 0L,
)

class CatalogAutoRefreshScheduler(
    private val database: WatchioDatabase,
    private val settingsRepository: SettingsRepository,
    private val coordinator: CatalogRefreshCoordinator,
    private val clock: WatchioClock,
    private val connectivityChecker: NetworkConnectivityChecker = NetworkConnectivityChecker { true },
    private val playerStateFlow: Flow<WatchioPlayerState>? = null,
    private val schedulerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val startupDelayMs: Long = STARTUP_READY_DELAY_MS,
    private val foregroundCheckIntervalMs: Long = FOREGROUND_CHECK_INTERVAL_MS,
    private val resumeCooldownMs: Long = RESUME_ATTEMPT_COOLDOWN_MS,
    private val liveStaleThresholdMs: Long = LIVE_STALE_THRESHOLD_MS,
    private val moviesStaleThresholdMs: Long = MOVIES_STALE_THRESHOLD_MS,
    private val seriesStaleThresholdMs: Long = SERIES_STALE_THRESHOLD_MS,
    private val initialRetryBackoffMs: Long = INITIAL_RETRY_BACKOFF_MS,
    private val maxRetryBackoffMs: Long = MAX_RETRY_BACKOFF_MS,
    private val maxRetryAfterMs: Long = MAX_RETRY_AFTER_MS,
) {
    private val evaluationMutex = Mutex()
    private val stateLock = Any()

    @Volatile
    private var observersStarted = false

    @Volatile
    private var isForegrounded = true

    @Volatile
    private var wasBackgrounded = false

    @Volatile
    private var homeReady = false

    @Volatile
    private var initialStartupCheckCompleted = false

    @Volatile
    private var manualPlaybackActive = false

    @Volatile
    private var routePlaybackActive = false

    @Volatile
    private var playerPlaybackActive = false

    @Volatile
    private var deferredRefreshPending = false

    @Volatile
    private var deferredTrigger: RefreshTrigger = RefreshTrigger.AutomaticPeriodic

    @Volatile
    private var lastKnownSelectedProviderId: ProviderId? = null

    private var startupJob: Job? = null
    private var periodicJob: Job? = null
    private var playerObserverJob: Job? = null
    private var providerSwitchObserverJob: Job? = null
    private var settingObserverJob: Job? = null

    private val lastResumeAttemptAtEpochMs = ConcurrentHashMap<ProviderId, Long>()
    private val failureStates = ConcurrentHashMap<Pair<ProviderId, ContentType>, SectionFailureState>()

    private val _isDeferredByPlayback = MutableStateFlow(false)
    val isDeferredByPlayback: StateFlow<Boolean> = _isDeferredByPlayback.asStateFlow()

    init {
        ensureObserversStarted()
    }

    fun onHomeReady(providerId: ProviderId? = null) {
        ensureObserversStarted()
        val shouldStartStartupJob: Boolean
        synchronized(stateLock) {
            homeReady = true
            if (providerId != null && lastKnownSelectedProviderId == null) {
                lastKnownSelectedProviderId = providerId
            }
            if (isForegrounded) {
                ensurePeriodicLoopRunningLocked()
            }
            shouldStartStartupJob = isForegrounded &&
                !initialStartupCheckCompleted &&
                startupJob?.isActive != true
            if (shouldStartStartupJob) {
                startupJob = schedulerScope.launch {
                    delay(startupDelayMs)
                    val proceed = synchronized(stateLock) {
                        if (!isForegrounded) {
                            false
                        } else {
                            initialStartupCheckCompleted = true
                            true
                        }
                    }
                    if (proceed) {
                        evaluateAndRefresh(RefreshTrigger.AutomaticStartup)
                    }
                }
            }
        }
    }

    fun onAppForegrounded() {
        ensureObserversStarted()
        var scheduleResumeCheck = false
        var scheduleStartupCheck = false
        synchronized(stateLock) {
            val comingFromBackground = !isForegrounded || wasBackgrounded
            isForegrounded = true
            wasBackgrounded = false
            if (homeReady) {
                ensurePeriodicLoopRunningLocked()
                if (!initialStartupCheckCompleted) {
                    if (startupJob?.isActive != true) {
                        scheduleStartupCheck = true
                    }
                } else if (comingFromBackground) {
                    scheduleResumeCheck = true
                }
            }
        }
        if (scheduleStartupCheck) {
            onHomeReady()
        } else if (scheduleResumeCheck) {
            schedulerScope.launch {
                evaluateAndRefresh(RefreshTrigger.AutomaticResume)
            }
        }
    }

    fun onAppResumed() = onAppForegrounded()

    fun onAppBackgrounded() {
        synchronized(stateLock) {
            isForegrounded = false
            wasBackgrounded = true
            periodicJob?.cancel()
            periodicJob = null
            if (!initialStartupCheckCompleted) {
                startupJob?.cancel()
                startupJob = null
            }
        }
    }

    fun setPlaybackActive(active: Boolean) {
        updatePlaybackState { manualPlaybackActive = active }
    }

    fun setPlaybackRouteActive(active: Boolean) {
        updatePlaybackState { routePlaybackActive = active }
    }

    fun isPlaybackCurrentlyActive(): Boolean = synchronized(stateLock) {
        manualPlaybackActive || routePlaybackActive || playerPlaybackActive
    }

    fun failureStateFor(providerId: ProviderId, contentType: ContentType): SectionFailureState? =
        failureStates[providerId to contentType]

    internal fun isEvaluating(): Boolean = evaluationMutex.isLocked

    suspend fun checkNow(
        trigger: RefreshTrigger = RefreshTrigger.AutomaticPeriodic,
        bypassResumeCooldown: Boolean = false,
    ) {
        evaluateAndRefresh(trigger, bypassResumeCooldown = bypassResumeCooldown)
    }

    suspend fun isSectionStale(
        providerId: ProviderId,
        contentType: ContentType,
        nowEpochMs: Long = clock.nowEpochMs(),
    ): Boolean {
        val provider = database.providerDao().findById(providerId.value) ?: return false
        return isSectionStale(provider, contentType, nowEpochMs)
    }

    private suspend fun isSectionStale(
        provider: ProviderEntity,
        contentType: ContentType,
        nowEpochMs: Long,
    ): Boolean {
        val providerId = ProviderId(provider.id)
        val sectionRefreshAt = settingsRepository.observeSectionRefreshEpochMs(providerId, contentType).first()
            ?: provider.lastRefreshAtEpochMs
        if (sectionRefreshAt == null || sectionRefreshAt <= 0L) return true
        val thresholdMs = thresholdFor(contentType)
        return nowEpochMs - sectionRefreshAt >= thresholdMs
    }

    private fun thresholdFor(contentType: ContentType): Long = when (contentType) {
        ContentType.Live -> liveStaleThresholdMs
        ContentType.Movie -> moviesStaleThresholdMs
        ContentType.Series -> seriesStaleThresholdMs
        ContentType.Episode -> seriesStaleThresholdMs
    }

    private fun ensureObserversStarted() {
        synchronized(stateLock) {
            if (observersStarted) return
            observersStarted = true

            playerStateFlow?.let { flow ->
                playerObserverJob = schedulerScope.launch {
                    flow.collect { state ->
                        val active = state.isActivePlaybackForRefreshDeferral()
                        updatePlaybackState { playerPlaybackActive = active }
                    }
                }
            }

            providerSwitchObserverJob = schedulerScope.launch {
                settingsRepository.selectedProviderId
                    .distinctUntilChanged()
                    .collect { selectedId ->
                        if (selectedId == null) return@collect
                        val shouldCheckSwitch = synchronized(stateLock) {
                            val previous = lastKnownSelectedProviderId
                            lastKnownSelectedProviderId = selectedId
                            isForegrounded && previous != null && previous != selectedId
                        }
                        if (shouldCheckSwitch) {
                            evaluateAndRefresh(
                                trigger = RefreshTrigger.AutomaticPeriodic,
                                bypassResumeCooldown = true,
                            )
                        }
                    }
            }

            settingObserverJob = schedulerScope.launch {
                var previousEnabled: Boolean? = null
                settingsRepository.automaticContentRefreshEnabled
                    .distinctUntilChanged()
                    .collect { enabled ->
                        val wasEnabled = previousEnabled
                        previousEnabled = enabled
                        if (!enabled) {
                            synchronized(stateLock) {
                                deferredRefreshPending = false
                                _isDeferredByPlayback.value = false
                            }
                        } else if (wasEnabled == false) {
                            val canEvaluate = synchronized(stateLock) { isForegrounded && (homeReady || initialStartupCheckCompleted) }
                            if (canEvaluate) {
                                evaluateAndRefresh(
                                    trigger = RefreshTrigger.AutomaticPeriodic,
                                    bypassResumeCooldown = true,
                                )
                            }
                        }
                    }
            }
        }
    }

    private fun ensurePeriodicLoopRunningLocked() {
        if (periodicJob?.isActive == true) return
        periodicJob = schedulerScope.launch {
            while (currentCoroutineContext().isActive) {
                delay(foregroundCheckIntervalMs)
                val activeAndForeground = synchronized(stateLock) { isForegrounded && homeReady }
                if (!activeAndForeground) break
                evaluateAndRefresh(RefreshTrigger.AutomaticPeriodic)
            }
        }
    }

    private fun updatePlaybackState(mutator: () -> Unit) {
        var playbackJustEnded = false
        var triggerToRun: RefreshTrigger = RefreshTrigger.AutomaticPeriodic
        synchronized(stateLock) {
            val wasActive = manualPlaybackActive || routePlaybackActive || playerPlaybackActive
            mutator()
            val nowActive = manualPlaybackActive || routePlaybackActive || playerPlaybackActive
            if (wasActive && !nowActive && isForegrounded && (deferredRefreshPending || homeReady || initialStartupCheckCompleted)) {
                playbackJustEnded = true
                triggerToRun = if (deferredRefreshPending) deferredTrigger else RefreshTrigger.AutomaticPeriodic
            }
        }
        if (playbackJustEnded) {
            schedulerScope.launch {
                evaluateAndRefresh(triggerToRun, bypassResumeCooldown = true)
            }
        }
    }

    private suspend fun evaluateAndRefresh(
        trigger: RefreshTrigger,
        bypassResumeCooldown: Boolean = false,
    ) {
        evaluationMutex.withLock {
            if (!settingsRepository.automaticContentRefreshEnabled.first()) {
                clearDeferredState()
                return
            }
            if (!synchronized(stateLock) { isForegrounded }) {
                return
            }
            val provider = resolveSelectedProvider() ?: return
            val providerId = ProviderId(provider.id)
            synchronized(stateLock) {
                if (lastKnownSelectedProviderId == null) {
                    lastKnownSelectedProviderId = providerId
                }
            }

            val now = clock.nowEpochMs()
            if (trigger == RefreshTrigger.AutomaticResume && !bypassResumeCooldown) {
                val lastResumeAt = lastResumeAttemptAtEpochMs[providerId]
                if (lastResumeAt != null && now - lastResumeAt < resumeCooldownMs) {
                    return
                }
            }

            val staleSections = CANONICAL_SECTIONS.filter { section ->
                isSectionStale(provider, section, now)
            }
            if (staleSections.isEmpty()) {
                clearDeferredState()
                return
            }

            if (isPlaybackCurrentlyActive()) {
                markDeferredByPlayback(trigger)
                return
            }

            if (!connectivityChecker.isOnline()) {
                return
            }

            when (ProviderType.fromPersisted(provider.type)) {
                ProviderType.M3uUrl, ProviderType.M3uFile -> {
                    val m3uRetryAllowed = CANONICAL_SECTIONS.all { section ->
                        isRetryAllowed(providerId, section, now)
                    }
                    if (!m3uRetryAllowed) return
                    if (trigger == RefreshTrigger.AutomaticResume) {
                        lastResumeAttemptAtEpochMs[providerId] = now
                    }
                    clearDeferredState()
                    val result = coordinator.refreshProvider(providerId, trigger)
                    handleM3uResult(providerId, result)
                }
                ProviderType.Xtream -> {
                    val eligibleSections = staleSections.filter { section ->
                        isRetryAllowed(providerId, section, now)
                    }
                    if (eligibleSections.isEmpty()) return
                    if (trigger == RefreshTrigger.AutomaticResume) {
                        lastResumeAttemptAtEpochMs[providerId] = now
                    }
                    clearDeferredState()
                    for (section in eligibleSections) {
                        if (!settingsRepository.automaticContentRefreshEnabled.first()) {
                            clearDeferredState()
                            return
                        }
                        if (!synchronized(stateLock) { isForegrounded }) {
                            return
                        }
                        if (isPlaybackCurrentlyActive()) {
                            markDeferredByPlayback(trigger)
                            return
                        }
                        if (!connectivityChecker.isOnline()) {
                            return
                        }
                        val currentProvider = resolveSelectedProvider()
                        if (currentProvider == null || currentProvider.id != providerId.value) {
                            return
                        }
                        val result = coordinator.refreshSection(providerId, section, trigger)
                        handleSectionResult(providerId, section, result)
                    }
                }
            }
        }
    }

    private suspend fun resolveSelectedProvider(): ProviderEntity? {
        val selectedId = settingsRepository.selectedProviderId.first()
        if (selectedId != null) {
            val matching = database.providerDao().findById(selectedId.value)
            if (matching != null && matching.enabled) {
                return matching
            }
        }
        return database.providerDao().getAll().firstOrNull { it.enabled }
    }

    private fun isRetryAllowed(
        providerId: ProviderId,
        contentType: ContentType,
        nowEpochMs: Long,
    ): Boolean {
        val state = failureStates[providerId to contentType] ?: return true
        return nowEpochMs >= state.nextRetryAllowedAtEpochMs
    }

    private fun handleM3uResult(
        providerId: ProviderId,
        result: CatalogRefreshResult,
    ) {
        when (result) {
            is CatalogRefreshResult.Success -> {
                CANONICAL_SECTIONS.forEach { section ->
                    failureStates.remove(providerId to section)
                }
            }
            is CatalogRefreshResult.Failure -> {
                val failureState = computeNextFailureState(
                    previous = failureStates[providerId to ContentType.Live],
                    cause = result.cause,
                    nowEpochMs = clock.nowEpochMs(),
                )
                CANONICAL_SECTIONS.forEach { section ->
                    failureStates[providerId to section] = failureState
                }
            }
            is CatalogRefreshResult.Skipped -> Unit
        }
    }

    private fun handleSectionResult(
        providerId: ProviderId,
        contentType: ContentType,
        result: CatalogRefreshResult,
    ) {
        when (result) {
            is CatalogRefreshResult.Success -> {
                failureStates.remove(providerId to contentType)
            }
            is CatalogRefreshResult.Failure -> {
                failureStates[providerId to contentType] = computeNextFailureState(
                    previous = failureStates[providerId to contentType],
                    cause = result.cause,
                    nowEpochMs = clock.nowEpochMs(),
                )
            }
            is CatalogRefreshResult.Skipped -> Unit
        }
    }

    private fun computeNextFailureState(
        previous: SectionFailureState?,
        cause: Throwable?,
        nowEpochMs: Long,
    ): SectionFailureState {
        val consecutive = (previous?.consecutiveFailures ?: 0) + 1
        val shift = (consecutive - 1).coerceIn(0, 10)
        val exponentialBackoffMs = (initialRetryBackoffMs * (1L shl shift)).coerceAtMost(maxRetryBackoffMs)
        val retryAfterMs = extractRetryAfterDelayMs(cause)
            ?.coerceIn(initialRetryBackoffMs, maxRetryAfterMs)
        val backoffMs = if (retryAfterMs != null) {
            maxOf(exponentialBackoffMs, retryAfterMs)
        } else {
            exponentialBackoffMs
        }
        return SectionFailureState(
            consecutiveFailures = consecutive,
            lastFailureAtEpochMs = nowEpochMs,
            nextRetryAllowedAtEpochMs = nowEpochMs + backoffMs,
        )
    }

    private fun markDeferredByPlayback(trigger: RefreshTrigger) {
        synchronized(stateLock) {
            deferredRefreshPending = true
            deferredTrigger = trigger
            _isDeferredByPlayback.value = true
        }
    }

    private fun clearDeferredState() {
        synchronized(stateLock) {
            deferredRefreshPending = false
            _isDeferredByPlayback.value = false
        }
    }

    companion object {
        const val STARTUP_READY_DELAY_MS: Long = 3_000L
        const val LIVE_STALE_THRESHOLD_MS: Long = 2 * 60 * 60 * 1_000L
        const val MOVIES_STALE_THRESHOLD_MS: Long = 6 * 60 * 60 * 1_000L
        const val SERIES_STALE_THRESHOLD_MS: Long = 6 * 60 * 60 * 1_000L
        const val FOREGROUND_CHECK_INTERVAL_MS: Long = 15 * 60 * 1_000L
        const val RESUME_ATTEMPT_COOLDOWN_MS: Long = 15 * 60 * 1_000L
        const val INITIAL_RETRY_BACKOFF_MS: Long = 60_000L
        const val MAX_RETRY_BACKOFF_MS: Long = 15 * 60 * 1_000L
        const val MAX_RETRY_AFTER_MS: Long = 60 * 60 * 1_000L

        private val CANONICAL_SECTIONS = listOf(
            ContentType.Live,
            ContentType.Movie,
            ContentType.Series,
        )

        fun extractRetryAfterDelayMs(cause: Throwable?): Long? {
            for (throwable in generateSequence(cause) { it.cause }) {
                when (throwable) {
                    is ProviderRateLimitException -> {
                        val seconds = throwable.retryAfterSeconds
                        if (seconds != null && seconds > 0L) {
                            return seconds * 1_000L
                        }
                    }
                    is HttpException -> {
                        if (throwable.code() == 429) {
                            val header = throwable.response()?.headers()?.get("Retry-After")?.trim()
                            val seconds = header?.toLongOrNull()
                            if (seconds != null && seconds > 0L) {
                                return seconds * 1_000L
                            }
                        }
                    }
                }
            }
            return null
        }
    }
}
