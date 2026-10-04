package com.iamskorpz.watchioiptv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.data.library.MyListData
import com.iamskorpz.watchioiptv.data.library.MyListRepository
import com.iamskorpz.watchioiptv.data.library.SearchRepository
import com.iamskorpz.watchioiptv.data.library.SearchResults
import com.iamskorpz.watchioiptv.data.library.SearchScope
import com.iamskorpz.watchioiptv.data.library.LibraryFavoriteItem
import com.iamskorpz.watchioiptv.core.model.ProviderId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class SearchStatus { Idle, Searching, Results, NoResults, Error }

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.Global,
    val loading: Boolean = false,
    val results: SearchResults = SearchResults(),
    val status: SearchStatus = SearchStatus.Idle,
    val recentQueries: List<String> = emptyList(),
    val errorMessage: String? = null,
    val activeProviderId: ProviderId? = null,
)

class GlobalSearchViewModel(
    private val repository: SearchRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    private var searchJob: Job? = null
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.activeProviderId.distinctUntilChanged().collectLatest { providerId ->
                searchJob?.cancel()
                val recents = providerId?.let { repository.recentQueries(it) }.orEmpty()
                mutableState.value = mutableState.value.copy(
                    activeProviderId = providerId,
                    recentQueries = recents,
                    results = SearchResults(),
                    status = SearchStatus.Idle,
                    loading = false,
                    errorMessage = null,
                )
                if (mutableState.value.query.isNotBlank()) runSearch()
            }
        }
    }

    fun setScope(scope: SearchScope) {
        mutableState.value = mutableState.value.copy(scope = scope)
        runSearch()
    }

    fun setQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val snapshot = mutableState.value
        if (snapshot.query.isBlank()) {
            mutableState.value = snapshot.copy(loading = false, results = SearchResults(), status = SearchStatus.Idle, errorMessage = null)
            return
        }
        val providerId = snapshot.activeProviderId
        if (providerId == null) {
            mutableState.value = snapshot.copy(loading = false, results = SearchResults(), status = SearchStatus.Error, errorMessage = "Choose an active provider to search.")
            return
        }
        searchJob = viewModelScope.launch {
            mutableState.value = snapshot.copy(loading = true, status = SearchStatus.Searching, errorMessage = null)
            delay(200L)
            runCatching { repository.search(providerId, snapshot.query, snapshot.scope) }
                .onSuccess { results ->
                    val current = mutableState.value
                    if (current.query == snapshot.query && current.scope == snapshot.scope && current.activeProviderId == providerId) {
                        mutableState.value = current.copy(
                            loading = false,
                            results = results,
                            status = if (results.isEmpty) SearchStatus.NoResults else SearchStatus.Results,
                        )
                    }
                }
                .onFailure {
                    val current = mutableState.value
                    if (current.query == snapshot.query && current.activeProviderId == providerId) {
                        mutableState.value = current.copy(loading = false, status = SearchStatus.Error, errorMessage = "Search is temporarily unavailable.")
                    }
                }
        }
    }

    fun selectRecent(query: String) = setQuery(query)

    fun recordCurrentQuery() {
        val snapshot = mutableState.value
        val providerId = snapshot.activeProviderId ?: return
        if (snapshot.query.isBlank()) return
        viewModelScope.launch {
            repository.recordQuery(providerId, snapshot.query)
            mutableState.value = mutableState.value.copy(recentQueries = repository.recentQueries(providerId))
        }
    }

    fun removeRecent(query: String) {
        val providerId = mutableState.value.activeProviderId ?: return
        viewModelScope.launch {
            repository.removeRecent(providerId, query)
            mutableState.value = mutableState.value.copy(recentQueries = repository.recentQueries(providerId))
        }
    }

    fun clearRecents() {
        val providerId = mutableState.value.activeProviderId ?: return
        viewModelScope.launch {
            repository.clearRecents(providerId)
            mutableState.value = mutableState.value.copy(recentQueries = emptyList())
        }
    }
}

data class MyListUiState(
    val loading: Boolean = true,
    val data: MyListData = MyListData(),
)

class MyListViewModel(
    private val repository: MyListRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MyListUiState())
    val state: StateFlow<MyListUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = MyListUiState(loading = true)
            mutableState.value = MyListUiState(loading = false, data = repository.load())
        }
    }

    fun removeFavorite(item: LibraryFavoriteItem) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(data = repository.removeFavorite(item))
        }
    }
}
