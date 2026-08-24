package com.watchioiptv.nativeapp.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.data.library.MyListData
import com.watchioiptv.nativeapp.data.library.MyListRepository
import com.watchioiptv.nativeapp.data.library.SearchRepository
import com.watchioiptv.nativeapp.data.library.SearchResults
import com.watchioiptv.nativeapp.data.library.SearchScope
import com.watchioiptv.nativeapp.data.library.LibraryFavoriteItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.Global,
    val loading: Boolean = false,
    val results: SearchResults = SearchResults(),
)

class GlobalSearchViewModel(
    private val repository: SearchRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    private var searchJob: Job? = null
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

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
            mutableState.value = snapshot.copy(loading = false, results = SearchResults())
            return
        }
        searchJob = viewModelScope.launch {
            mutableState.value = snapshot.copy(loading = true)
            delay(250L)
            val results = repository.search(snapshot.query, snapshot.scope)
            mutableState.value = mutableState.value.copy(loading = false, results = results)
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
