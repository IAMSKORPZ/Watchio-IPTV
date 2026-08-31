package com.watchioiptv.nativeapp.feature.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.watchioiptv.nativeapp.data.announcements.AnnouncementRepository
import com.watchioiptv.nativeapp.domain.model.AnnouncementItem
import com.watchioiptv.nativeapp.domain.model.AnnouncementSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnnouncementsUiState(
    val snapshot: AnnouncementSnapshot = AnnouncementSnapshot(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val showArchived: Boolean = false,
    val selectedId: String? = null,
) {
    val visibleItems: List<AnnouncementItem> get() = snapshot.items.filter { it.isDismissed == showArchived }
    val selected: AnnouncementItem? get() = snapshot.items.firstOrNull { it.announcement.id == selectedId }
}

class AnnouncementsViewModel(private val repository: AnnouncementRepository) : ViewModel() {
    private val controls = MutableStateFlow(Controls())
    val state: StateFlow<AnnouncementsUiState> = combine(repository.snapshot, controls) { snapshot, controls ->
        AnnouncementsUiState(snapshot, controls.loading, controls.error, controls.showArchived, controls.selectedId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AnnouncementsUiState())

    fun refresh() {
        if (controls.value.loading) return
        viewModelScope.launch {
            controls.value = controls.value.copy(loading = true, error = false)
            val result = repository.refresh()
            controls.value = controls.value.copy(
                loading = false,
                error = result.isFailure && !state.value.snapshot.hasCachedFeed,
            )
        }
    }

    fun open(id: String) {
        controls.value = controls.value.copy(selectedId = id)
        viewModelScope.launch { repository.markRead(id) }
    }

    fun closeDetails() { controls.value = controls.value.copy(selectedId = null) }
    fun toggleArchived() { controls.value = controls.value.copy(showArchived = !controls.value.showArchived) }
    fun dismiss(id: String) {
        controls.value = controls.value.copy(selectedId = null)
        viewModelScope.launch { repository.dismiss(id) }
    }

    private data class Controls(
        val loading: Boolean = false,
        val error: Boolean = false,
        val showArchived: Boolean = false,
        val selectedId: String? = null,
    )
}
