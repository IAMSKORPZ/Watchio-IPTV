package com.iamskorpz.watchioiptv.data.refresh

import com.iamskorpz.watchioiptv.core.database.WatchioDatabase
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.core.util.WatchioClock
import com.iamskorpz.watchioiptv.data.m3u.M3uRepository
import com.iamskorpz.watchioiptv.data.xtream.XtreamRepository
import com.iamskorpz.watchioiptv.domain.model.ContentType
import com.iamskorpz.watchioiptv.domain.model.ProviderType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EmptyCatalogResponseException(
    message: String = "Provider returned an empty catalogue while cached content exists.",
) : IllegalStateException(message)

class ProviderRateLimitException(
    val retryAfterSeconds: Long? = null,
    message: String = "Provider is rate limiting requests. Please try again later.",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

enum class RefreshTrigger {
    Manual,
    AutomaticStartup,
    AutomaticResume,
    AutomaticPeriodic,
}

enum class RefreshStatus {
    Updated,
    Unchanged,
}

enum class RefreshSkipReason {
    ProviderNotFound,
    ProviderDisabled,
    UnsupportedSection,
    AlreadyFresh,
}

sealed interface CatalogRefreshResult {
    val providerId: ProviderId

    data class Success(
        override val providerId: ProviderId,
        val status: RefreshStatus,
        val refreshedSections: Set<ContentType>,
        val changedSections: Set<ContentType>,
        val liveCount: Int,
        val movieCount: Int,
        val seriesCount: Int,
        val completedAtEpochMs: Long,
        val coalesced: Boolean = false,
    ) : CatalogRefreshResult {
        val isUpdated: Boolean get() = status == RefreshStatus.Updated
        val isUnchanged: Boolean get() = status == RefreshStatus.Unchanged
    }

    data class Skipped(
        override val providerId: ProviderId,
        val reason: RefreshSkipReason,
        val message: String,
    ) : CatalogRefreshResult

    data class Failure(
        override val providerId: ProviderId,
        val requestedSections: Set<ContentType>,
        val message: String,
        val cause: Throwable? = null,
        val coalesced: Boolean = false,
    ) : CatalogRefreshResult
}

fun CatalogRefreshResult.getOrThrow(): CatalogRefreshResult.Success = when (this) {
    is CatalogRefreshResult.Success -> this
    is CatalogRefreshResult.Failure -> throw (cause ?: IllegalStateException(message))
    is CatalogRefreshResult.Skipped -> throw IllegalArgumentException(message)
}

data class CatalogUpdateEvent(
    val providerId: ProviderId,
    val trigger: RefreshTrigger,
    val status: RefreshStatus,
    val refreshedSections: Set<ContentType>,
    val changedSections: Set<ContentType>,
    val completedAtEpochMs: Long,
)

class CatalogRefreshCoordinator(
    private val database: WatchioDatabase,
    private val xtreamRepository: XtreamRepository,
    private val m3uRepository: M3uRepository,
    private val clock: WatchioClock,
    private val coordinatorScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val controls = ConcurrentHashMap<ProviderId, ProviderRefreshControl>()
    private val _updates = MutableSharedFlow<CatalogUpdateEvent>(extraBufferCapacity = 32)
    val updates: SharedFlow<CatalogUpdateEvent> = _updates.asSharedFlow()

    private val _activeRefreshes = MutableStateFlow<Set<ProviderId>>(emptySet())
    val activeRefreshes: StateFlow<Set<ProviderId>> = _activeRefreshes.asStateFlow()

    fun isRefreshing(providerId: ProviderId): Boolean = providerId in _activeRefreshes.value

    suspend fun refreshLive(
        providerId: ProviderId,
        trigger: RefreshTrigger = RefreshTrigger.Manual,
    ): CatalogRefreshResult = refreshSection(providerId, ContentType.Live, trigger)

    suspend fun refreshMovies(
        providerId: ProviderId,
        trigger: RefreshTrigger = RefreshTrigger.Manual,
    ): CatalogRefreshResult = refreshSection(providerId, ContentType.Movie, trigger)

    suspend fun refreshSeries(
        providerId: ProviderId,
        trigger: RefreshTrigger = RefreshTrigger.Manual,
    ): CatalogRefreshResult = refreshSection(providerId, ContentType.Series, trigger)

    suspend fun refreshProvider(
        providerId: ProviderId,
        trigger: RefreshTrigger = RefreshTrigger.Manual,
    ): CatalogRefreshResult {
        val provider = database.providerDao().findById(providerId.value)
            ?: return CatalogRefreshResult.Skipped(
                providerId = providerId,
                reason = RefreshSkipReason.ProviderNotFound,
                message = "Provider not found.",
            )
        if (!provider.enabled && trigger != RefreshTrigger.Manual) {
            return CatalogRefreshResult.Skipped(
                providerId = providerId,
                reason = RefreshSkipReason.ProviderDisabled,
                message = "Provider is disabled.",
            )
        }
        return when (ProviderType.fromPersisted(provider.type)) {
            ProviderType.M3uUrl, ProviderType.M3uFile -> executeOrJoinM3uRefresh(providerId, trigger)
            ProviderType.Xtream -> executeOrJoinXtreamFullRefresh(providerId, trigger)
        }
    }

    suspend fun refreshSection(
        providerId: ProviderId,
        contentType: ContentType,
        trigger: RefreshTrigger = RefreshTrigger.Manual,
    ): CatalogRefreshResult {
        if (contentType !in ALL_SECTIONS) {
            return CatalogRefreshResult.Skipped(
                providerId = providerId,
                reason = RefreshSkipReason.UnsupportedSection,
                message = "Episode refresh is not a Home section.",
            )
        }
        val provider = database.providerDao().findById(providerId.value)
            ?: return CatalogRefreshResult.Skipped(
                providerId = providerId,
                reason = RefreshSkipReason.ProviderNotFound,
                message = "Provider not found.",
            )
        if (!provider.enabled && trigger != RefreshTrigger.Manual) {
            return CatalogRefreshResult.Skipped(
                providerId = providerId,
                reason = RefreshSkipReason.ProviderDisabled,
                message = "Provider is disabled.",
            )
        }
        return when (ProviderType.fromPersisted(provider.type)) {
            // M3U playlists contain Live, Movies, and Series in one file; always consolidate into a single download/parse pass.
            ProviderType.M3uUrl, ProviderType.M3uFile -> executeOrJoinM3uRefresh(providerId, trigger)
            ProviderType.Xtream -> executeOrJoinXtreamSectionRefresh(providerId, contentType, trigger)
        }
    }

    private suspend fun executeOrJoinM3uRefresh(
        providerId: ProviderId,
        trigger: RefreshTrigger,
    ): CatalogRefreshResult {
        val control = controlFor(providerId)
        val (deferred, coalesced) = control.stateMutex.withLock {
            val activeFull = control.inFlightFullProvider
            if (activeFull != null && activeFull.isActive) {
                activeFull to true
            } else {
                lateinit var created: Deferred<CatalogRefreshResult>
                created = coordinatorScope.async {
                    markActive(control, providerId)
                    try {
                        control.withAllSectionLocks {
                            runCatching {
                                m3uRepository.refresh(providerId.value)
                            }.fold(
                                onSuccess = { state ->
                                    val now = clock.nowEpochMs()
                                    val status = if (state.changedSections.isNotEmpty()) {
                                        RefreshStatus.Updated
                                    } else {
                                        RefreshStatus.Unchanged
                                    }
                                    val result = CatalogRefreshResult.Success(
                                        providerId = providerId,
                                        status = status,
                                        refreshedSections = ALL_SECTIONS,
                                        changedSections = state.changedSections,
                                        liveCount = state.liveCount,
                                        movieCount = state.movieCount,
                                        seriesCount = state.seriesCount,
                                        completedAtEpochMs = now,
                                        coalesced = false,
                                    )
                                    emitUpdate(providerId, trigger, result)
                                    result
                                },
                                onFailure = { error ->
                                    if (error is CancellationException) throw error
                                    CatalogRefreshResult.Failure(
                                        providerId = providerId,
                                        requestedSections = ALL_SECTIONS,
                                        message = error.message ?: "Unable to refresh M3U provider.",
                                        cause = error,
                                        coalesced = false,
                                    )
                                },
                            )
                        }
                    } finally {
                        control.stateMutex.withLock {
                            if (control.inFlightFullProvider === created) {
                                control.inFlightFullProvider = null
                            }
                            updateActiveStateLocked(control, providerId)
                        }
                    }
                }
                control.inFlightFullProvider = created
                created to false
            }
        }
        val outcome = deferred.await()
        return if (coalesced) outcome.withCoalesced() else outcome
    }

    private suspend fun executeOrJoinXtreamSectionRefresh(
        providerId: ProviderId,
        contentType: ContentType,
        trigger: RefreshTrigger,
    ): CatalogRefreshResult {
        val control = controlFor(providerId)
        val (deferred, coalesced) = control.stateMutex.withLock {
            val activeFull = control.inFlightFullProvider
            if (activeFull != null && activeFull.isActive) {
                return@withLock activeFull to true
            }
            val activeSection = control.inFlightSections[contentType]
            if (activeSection != null && activeSection.isActive) {
                return@withLock activeSection to true
            }
            val created = startXtreamSectionDeferredLocked(
                control = control,
                providerId = providerId,
                contentType = contentType,
                trigger = trigger,
                emitEvent = true,
            )
            created to false
        }
        val outcome = deferred.await()
        return if (coalesced) outcome.withCoalesced() else outcome
    }

    private suspend fun executeOrJoinXtreamFullRefresh(
        providerId: ProviderId,
        trigger: RefreshTrigger,
    ): CatalogRefreshResult {
        val control = controlFor(providerId)
        val (deferred, coalesced) = control.stateMutex.withLock {
            val activeFull = control.inFlightFullProvider
            if (activeFull != null && activeFull.isActive) {
                return@withLock activeFull to true
            }
            val activeSectionJobs = control.inFlightSections.filterValues { it.isActive }
            if (activeSectionJobs.isEmpty()) {
                lateinit var created: Deferred<CatalogRefreshResult>
                created = coordinatorScope.async {
                    markActive(control, providerId)
                    try {
                        control.withAllSectionLocks {
                            runCatching {
                                xtreamRepository.refreshProvider(providerId)
                            }.fold(
                                onSuccess = { state ->
                                    val now = clock.nowEpochMs()
                                    val status = if (state.changedSections.isNotEmpty()) {
                                        RefreshStatus.Updated
                                    } else {
                                        RefreshStatus.Unchanged
                                    }
                                    val result = CatalogRefreshResult.Success(
                                        providerId = providerId,
                                        status = status,
                                        refreshedSections = ALL_SECTIONS,
                                        changedSections = state.changedSections,
                                        liveCount = state.liveCount,
                                        movieCount = state.movieCount,
                                        seriesCount = state.seriesCount,
                                        completedAtEpochMs = now,
                                        coalesced = false,
                                    )
                                    emitUpdate(providerId, trigger, result)
                                    result
                                },
                                onFailure = { error ->
                                    if (error is CancellationException) throw error
                                    CatalogRefreshResult.Failure(
                                        providerId = providerId,
                                        requestedSections = ALL_SECTIONS,
                                        message = error.message ?: "Unable to refresh provider.",
                                        cause = error,
                                        coalesced = false,
                                    )
                                },
                            )
                        }
                    } finally {
                        control.stateMutex.withLock {
                            if (control.inFlightFullProvider === created) {
                                control.inFlightFullProvider = null
                            }
                            updateActiveStateLocked(control, providerId)
                        }
                    }
                }
                control.inFlightFullProvider = created
                created to false
            } else {
                // One or more section refreshes are already in flight; join them and start the remaining sections
                // so full-provider and section refreshes never race or duplicate network calls.
                val sectionDeferreds = CANONICAL_SECTION_ORDER.associateWith { section ->
                    val existing = control.inFlightSections[section]
                    if (existing != null && existing.isActive) {
                        existing
                    } else {
                        startXtreamSectionDeferredLocked(
                            control = control,
                            providerId = providerId,
                            contentType = section,
                            trigger = trigger,
                            emitEvent = false,
                        )
                    }
                }
                lateinit var created: Deferred<CatalogRefreshResult>
                created = coordinatorScope.async {
                    markActive(control, providerId)
                    try {
                        val results = CANONICAL_SECTION_ORDER.map { section ->
                            sectionDeferreds.getValue(section).await()
                        }
                        val firstFailure = results.filterIsInstance<CatalogRefreshResult.Failure>().firstOrNull()
                        if (firstFailure != null) {
                            firstFailure.copy(requestedSections = ALL_SECTIONS, coalesced = false)
                        } else {
                            val successes = results.filterIsInstance<CatalogRefreshResult.Success>()
                            val changedSections = successes.flatMapTo( linkedSetOf() ) { it.changedSections }
                            val counts = xtreamRepository.counts(providerId)
                            val now = clock.nowEpochMs()
                            val status = if (changedSections.isNotEmpty()) {
                                RefreshStatus.Updated
                            } else {
                                RefreshStatus.Unchanged
                            }
                            val combined = CatalogRefreshResult.Success(
                                providerId = providerId,
                                status = status,
                                refreshedSections = ALL_SECTIONS,
                                changedSections = changedSections,
                                liveCount = counts.liveCount,
                                movieCount = counts.movieCount,
                                seriesCount = counts.seriesCount,
                                completedAtEpochMs = now,
                                coalesced = false,
                            )
                            emitUpdate(providerId, trigger, combined)
                            combined
                        }
                    } finally {
                        control.stateMutex.withLock {
                            if (control.inFlightFullProvider === created) {
                                control.inFlightFullProvider = null
                            }
                            updateActiveStateLocked(control, providerId)
                        }
                    }
                }
                control.inFlightFullProvider = created
                created to false
            }
        }
        val outcome = deferred.await()
        return if (coalesced) outcome.withCoalesced() else outcome
    }

    private fun startXtreamSectionDeferredLocked(
        control: ProviderRefreshControl,
        providerId: ProviderId,
        contentType: ContentType,
        trigger: RefreshTrigger,
        emitEvent: Boolean,
    ): Deferred<CatalogRefreshResult> {
        lateinit var created: Deferred<CatalogRefreshResult>
        created = coordinatorScope.async {
            markActive(control, providerId)
            try {
                control.sectionLocks.getValue(contentType).withLock {
                    runCatching {
                        when (contentType) {
                            ContentType.Live -> xtreamRepository.refreshLive(providerId)
                            ContentType.Movie -> xtreamRepository.refreshMovies(providerId)
                            ContentType.Series -> xtreamRepository.refreshSeries(providerId)
                            ContentType.Episode -> throw IllegalArgumentException("Episode refresh is not a Home section.")
                        }
                    }.fold(
                        onSuccess = { state ->
                            val now = clock.nowEpochMs()
                            val status = if (state.changedSections.isNotEmpty()) {
                                RefreshStatus.Updated
                            } else {
                                RefreshStatus.Unchanged
                            }
                            val result = CatalogRefreshResult.Success(
                                providerId = providerId,
                                status = status,
                                refreshedSections = setOf(contentType),
                                changedSections = state.changedSections,
                                liveCount = state.liveCount,
                                movieCount = state.movieCount,
                                seriesCount = state.seriesCount,
                                completedAtEpochMs = now,
                                coalesced = false,
                            )
                            if (emitEvent) {
                                emitUpdate(providerId, trigger, result)
                            }
                            result
                        },
                        onFailure = { error ->
                            if (error is CancellationException) throw error
                            CatalogRefreshResult.Failure(
                                providerId = providerId,
                                requestedSections = setOf(contentType),
                                message = error.message ?: "Unable to refresh section.",
                                cause = error,
                                coalesced = false,
                            )
                        },
                    )
                }
            } finally {
                control.stateMutex.withLock {
                    if (control.inFlightSections[contentType] === created) {
                        control.inFlightSections.remove(contentType)
                    }
                    updateActiveStateLocked(control, providerId)
                }
            }
        }
        control.inFlightSections[contentType] = created
        return created
    }

    private fun emitUpdate(
        providerId: ProviderId,
        trigger: RefreshTrigger,
        result: CatalogRefreshResult.Success,
    ) {
        _updates.tryEmit(
            CatalogUpdateEvent(
                providerId = providerId,
                trigger = trigger,
                status = result.status,
                refreshedSections = result.refreshedSections,
                changedSections = result.changedSections,
                completedAtEpochMs = result.completedAtEpochMs,
            ),
        )
    }

    private fun controlFor(providerId: ProviderId): ProviderRefreshControl =
        controls.getOrPut(providerId) { ProviderRefreshControl() }

    private fun markActive(control: ProviderRefreshControl, providerId: ProviderId) {
        control.activeCount += 1
        _activeRefreshes.update { it + providerId }
    }

    private fun updateActiveStateLocked(control: ProviderRefreshControl, providerId: ProviderId) {
        control.activeCount = (control.activeCount - 1).coerceAtLeast(0)
        val stillActive = control.activeCount > 0 ||
            control.inFlightFullProvider?.isActive == true ||
            control.inFlightSections.values.any { it.isActive }
        if (!stillActive) {
            _activeRefreshes.update { it - providerId }
        }
    }

    private fun CatalogRefreshResult.withCoalesced(): CatalogRefreshResult = when (this) {
        is CatalogRefreshResult.Success -> copy(coalesced = true)
        is CatalogRefreshResult.Failure -> copy(coalesced = true)
        is CatalogRefreshResult.Skipped -> this
    }

    private class ProviderRefreshControl {
        val stateMutex = Mutex()
        val sectionLocks: Map<ContentType, Mutex> = mapOf(
            ContentType.Live to Mutex(),
            ContentType.Movie to Mutex(),
            ContentType.Series to Mutex(),
        )
        var inFlightFullProvider: Deferred<CatalogRefreshResult>? = null
        val inFlightSections: MutableMap<ContentType, Deferred<CatalogRefreshResult>> = mutableMapOf()
        @Volatile
        var activeCount: Int = 0

        suspend fun <T> withAllSectionLocks(block: suspend () -> T): T =
            sectionLocks.getValue(ContentType.Live).withLock {
                sectionLocks.getValue(ContentType.Movie).withLock {
                    sectionLocks.getValue(ContentType.Series).withLock {
                        block()
                    }
                }
            }
    }

    companion object {
        private val CANONICAL_SECTION_ORDER = listOf(ContentType.Live, ContentType.Movie, ContentType.Series)
        val ALL_SECTIONS: Set<ContentType> = setOf(ContentType.Live, ContentType.Movie, ContentType.Series)
    }
}
