package com.iamskorpz.watchioiptv.feature.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SportsUiState(
    val selectedDate: LocalDate,
    val loadState: SportsLoadState = SportsLoadState.Loading,
    val selectedFixture: SportsFixture? = null,
    val candidatesLoading: Boolean = false,
    val candidates: List<SportsChannelCandidate> = emptyList(),
    val candidateError: String? = null,
)

class SportsViewModel private constructor(
    private val repository: SportsRepository?,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val scheduleLoader: suspend (LocalDate) -> Result<SportsDateSchedule>,
) : ViewModel() {
    constructor(repository: SportsRepository, clock: Clock = Clock.systemDefaultZone()) :
        this(repository, clock, repository::schedule)

    internal constructor(clock: Clock, scheduleLoader: suspend (LocalDate) -> Result<SportsDateSchedule>) :
        this(null, clock, scheduleLoader)

    private val mutableState = MutableStateFlow(SportsUiState(LocalDate.now(clock)))
    val state: StateFlow<SportsUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var cooldownJob: Job? = null

    init { load() }

    fun previousDay() = selectDate(mutableState.value.selectedDate.minusDays(1))
    fun nextDay() = selectDate(mutableState.value.selectedDate.plusDays(1))
    fun today() = selectDate(LocalDate.now(clock))
    fun retry() {
        val error = mutableState.value.loadState as? SportsLoadState.Error
        if (error != null && !error.retryEnabled) return
        load()
    }

    fun watch(fixture: SportsFixture) {
        val repository = repository ?: return
        mutableState.value = mutableState.value.copy(selectedFixture = fixture, candidatesLoading = true, candidates = emptyList(), candidateError = null)
        viewModelScope.launch {
            repository.candidates(fixture).fold(
                onSuccess = { mutableState.value = mutableState.value.copy(candidatesLoading = false, candidates = it) },
                onFailure = { mutableState.value = mutableState.value.copy(candidatesLoading = false, candidateError = "Unable to match provider channels.") },
            )
        }
    }

    fun closeCandidates() { mutableState.value = mutableState.value.copy(selectedFixture = null, candidates = emptyList(), candidateError = null) }

    private fun selectDate(date: LocalDate) {
        if (date == mutableState.value.selectedDate) return
        mutableState.value = mutableState.value.copy(selectedDate = date)
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val date = mutableState.value.selectedDate
            mutableState.value = mutableState.value.copy(loadState = SportsLoadState.Loading)
            scheduleLoader(date).fold(
                onSuccess = { if (mutableState.value.selectedDate == date) mutableState.value = mutableState.value.copy(loadState = SportsLoadState.Ready(it)) },
                onFailure = { error ->
                    if (mutableState.value.selectedDate == date) applyError(error)
                },
            )
        }
    }

    private fun applyError(error: Throwable) {
        cooldownJob?.cancel()
        val state = when (error) {
            SportsScheduleException.MissingCredential -> SportsLoadState.SetupRequired
            SportsScheduleException.InvalidCredential -> SportsLoadState.CredentialNeedsAttention
            is SportsScheduleException.RateLimited -> SportsLoadState.Error(
                message = "Too many fixture requests",
                detail = "Please wait a moment and try again.",
                retryEnabled = clock.millis() >= error.retryAvailableAtEpochMs,
                retryAvailableAtEpochMs = error.retryAvailableAtEpochMs,
            )
            SportsScheduleException.ServiceUnavailable -> SportsLoadState.Error("Sports service unavailable")
            SportsScheduleException.TemporarilyUnavailable -> SportsLoadState.Error("Fixtures are temporarily unavailable")
            else -> SportsLoadState.Error("Unable to load fixtures")
        }
        mutableState.value = mutableState.value.copy(loadState = state)
        if (state is SportsLoadState.Error && !state.retryEnabled && state.retryAvailableAtEpochMs != null) {
            cooldownJob = viewModelScope.launch {
                delay((state.retryAvailableAtEpochMs - clock.millis()).coerceAtLeast(1L))
                val current = mutableState.value.loadState as? SportsLoadState.Error ?: return@launch
                if (current.retryAvailableAtEpochMs == state.retryAvailableAtEpochMs) {
                    mutableState.value = mutableState.value.copy(loadState = current.copy(retryEnabled = true, detail = "You can try again now."))
                }
            }
        }
    }
}
