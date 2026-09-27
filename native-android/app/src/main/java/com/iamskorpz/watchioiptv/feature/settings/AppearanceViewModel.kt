package com.iamskorpz.watchioiptv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.datastore.WatchioSettingsRepository
import com.iamskorpz.watchioiptv.ui.theme.WatchioAppearanceLibrary
import com.iamskorpz.watchioiptv.ui.theme.WatchioBuiltInThemes
import com.iamskorpz.watchioiptv.ui.theme.WatchioThemeDefinition
import com.iamskorpz.watchioiptv.ui.theme.WatchioSemanticColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppearanceEditorState(
    val library: WatchioAppearanceLibrary = WatchioAppearanceLibrary.Default,
    val draft: WatchioThemeDefinition = WatchioThemeDefinition.WatchioDefault,
    val dirty: Boolean = false,
    val saving: Boolean = false,
)

class AppearanceViewModel(
    private val repository: WatchioSettingsRepository,
) : ViewModel() {
    private val draft = MutableStateFlow<WatchioThemeDefinition?>(null)
    private val dirty = MutableStateFlow(false)
    private val saving = MutableStateFlow(false)

    val state: StateFlow<AppearanceEditorState> = combine(
        repository.appearanceLibrary,
        draft,
        dirty,
        saving,
    ) { library, currentDraft, isDirty, isSaving ->
        AppearanceEditorState(
            library = library,
            draft = currentDraft ?: library.activeTheme(),
            dirty = isDirty,
            saving = isSaving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceEditorState())

    fun update(transform: (WatchioThemeDefinition) -> WatchioThemeDefinition) {
        val current = draft.value ?: state.value.library.activeTheme()
        val editable = if (current.isBuiltIn) current.duplicate("${current.name} Custom") else current
        draft.value = transform(editable).normalized()
        dirty.value = true
    }

    fun updateColors(transform: (WatchioSemanticColors) -> WatchioSemanticColors) =
        update { it.copy(colors = transform(it.colors)) }

    fun select(id: String) {
        val selected = WatchioBuiltInThemes.byId(id)
            ?: state.value.library.themes.firstOrNull { it.id == id }
            ?: return
        draft.value = selected
        dirty.value = selected.id != state.value.library.activeThemeId
    }

    fun apply() {
        viewModelScope.launch {
            saving.value = true
            repository.applyTheme(state.value.draft)
            draft.value = state.value.draft
            dirty.value = false
            saving.value = false
        }
    }

    fun discard() {
        draft.value = state.value.library.activeTheme()
        dirty.value = false
    }

    fun resetSection(section: AppearanceSection) = update { theme ->
        val defaults = WatchioBuiltInThemes.byId(theme.id) ?: WatchioThemeDefinition.WatchioDefault
        when (section) {
            AppearanceSection.Presets -> theme
            AppearanceSection.Colours -> theme.copy(colors = defaults.colors)
            AppearanceSection.Background -> theme.copy(background = defaults.background)
            AppearanceSection.Text -> theme.copy(typography = defaults.typography)
            AppearanceSection.Panels -> theme.copy(surfaces = defaults.surfaces)
            AppearanceSection.Cards -> theme.copy(cards = defaults.cards)
            AppearanceSection.Controls -> theme.copy(controls = defaults.controls)
            AppearanceSection.Navigation -> theme.copy(navigation = defaults.navigation)
            AppearanceSection.TvFocus -> theme.copy(focus = defaults.focus)
            AppearanceSection.Layout -> theme.copy(layout = defaults.layout)
            AppearanceSection.Effects -> theme.copy(effects = defaults.effects)
        }
    }

    fun resetAll() {
        draft.value = WatchioThemeDefinition.WatchioDefault
        dirty.value = state.value.library.activeThemeId != WatchioThemeDefinition.WatchioDefault.id
    }

    fun duplicate() {
        draft.value = state.value.draft.duplicate("${state.value.draft.name} Custom")
        dirty.value = true
    }

    fun rename(name: String) = viewModelScope.launch {
        val id = state.value.draft.id
        if (WatchioBuiltInThemes.byId(id) != null) return@launch
        repository.renameTheme(id, name)
        draft.value = state.value.draft.copy(name = name).normalized()
    }

    fun delete() = viewModelScope.launch {
        if (repository.deleteTheme(state.value.draft.id)) {
            draft.value = WatchioThemeDefinition.WatchioDefault
            dirty.value = false
        }
    }
}

enum class AppearanceSection(val label: String) {
    Presets("Presets"), Colours("Colours"), Background("Background"), Text("Text"),
    Panels("Panels"), Cards("Cards"), Controls("Buttons / Controls"), Navigation("Navigation"),
    TvFocus("TV Focus"), Layout("Layout"), Effects("Effects / Animation"),
}
