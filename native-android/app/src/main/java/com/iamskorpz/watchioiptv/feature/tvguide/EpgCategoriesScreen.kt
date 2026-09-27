package com.iamskorpz.watchioiptv.feature.tvguide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iamskorpz.watchioiptv.core.model.ProviderId
import com.iamskorpz.watchioiptv.data.live.LiveTvCategory
import com.iamskorpz.watchioiptv.data.live.LiveTvCategoryKind
import com.iamskorpz.watchioiptv.data.live.LiveTvRepository
import com.iamskorpz.watchioiptv.ui.components.WatchioCard
import com.iamskorpz.watchioiptv.ui.components.WatchioPageHeader
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioAppearance
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioSpacing
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioTypography
import com.iamskorpz.watchioiptv.ui.theme.toComposeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class EpgCategoryEntry(val category: LiveTvCategory, val channelCount: Int)

data class EpgCategoriesUiState(
    val loading: Boolean = true,
    val providerId: ProviderId? = null,
    val entries: List<EpgCategoryEntry> = emptyList(),
    val errorMessage: String? = null,
)

class EpgCategoriesViewModel(private val repository: LiveTvRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(EpgCategoriesUiState())
    val state: StateFlow<EpgCategoriesUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSelectedProviderId().distinctUntilChanged().collect(::load)
        }
    }

    private suspend fun load(providerId: ProviderId?) {
        if (providerId == null) {
            mutableState.value = EpgCategoriesUiState(loading = false, errorMessage = "Add a provider first.")
            return
        }
        mutableState.value = EpgCategoriesUiState(loading = true, providerId = providerId)
        try {
            val categories = repository.categories(providerId).filter { it.kind != LiveTvCategoryKind.History }
            val entries = categories.map { EpgCategoryEntry(it, repository.channels(providerId, it).size) }
            mutableState.value = EpgCategoriesUiState(loading = false, providerId = providerId, entries = entries)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.value = EpgCategoriesUiState(loading = false, providerId = providerId, errorMessage = "Could not load EPG categories.")
        }
    }
}

@Composable
fun EpgCategoriesScreen(
    state: EpgCategoriesUiState,
    onCategory: (LiveTvCategory) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val firstFocus = androidx.compose.runtime.remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(horizontal = spacing.md, vertical = spacing.sm)) {
        WatchioPageHeader(
            title = "EPG CATEGORIES",
            onBack = onBack,
            testTagPrefix = "epg-categories",
            leadingIcon = { GuideIcon(Modifier.size(34.dp)) },
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = spacing.sm)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.liveTvAccent)
                state.errorMessage != null -> Text(state.errorMessage, color = colors.textSecondary, modifier = Modifier.align(Alignment.Center))
                else -> {
                    val columns = if (maxWidth >= 700.dp) 2 else 1
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        modifier = Modifier.fillMaxSize().testTag("epg-categories-grid"),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        itemsIndexed(state.entries, key = { _, entry -> entry.category.id }) { index, entry ->
                            CategoryRow(
                                entry = entry,
                                modifier = Modifier.then(if (index == 0) Modifier else Modifier).testTag("epg-category-${entry.category.id}"),
                                focusRequester = firstFocus.takeIf { index == 0 },
                                onClick = { onCategory(entry.category) },
                            )
                        }
                    }
                    LaunchedEffect(state.providerId, state.entries.firstOrNull()?.category?.id) {
                        if (state.entries.isNotEmpty()) {
                            withFrameNanos { }
                            firstFocus.requestFocus()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(entry: EpgCategoryEntry, modifier: Modifier, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val colors = LocalWatchioColors.current
    val spacing = LocalWatchioSpacing.current
    val type = LocalWatchioTypography.current
    val appearance = LocalWatchioAppearance.current
    WatchioCard(
        modifier = modifier.fillMaxWidth(),
        focusRequester = focusRequester,
        accent = colors.liveTvAccent,
        minHeight = 70.dp,
        contentDescription = "${entry.category.name}, ${entry.channelCount} channels",
        onClick = onClick,
    ) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            GuideIcon(Modifier.size(28.dp))
            Text(entry.category.name, modifier = Modifier.weight(1f), color = if (focused) appearance.colors.focusedText.toComposeColor() else colors.textPrimary, style = type.cardTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.channelCount.toString(), color = if (focused) appearance.colors.focusedText.toComposeColor() else colors.textSecondary, style = type.body)
            Text(">", color = if (focused) appearance.colors.focusedText.toComposeColor() else colors.liveTvAccent, style = type.cardTitle, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GuideIcon(modifier: Modifier = Modifier) {
    val color = LocalWatchioColors.current.liveTvAccent
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.24f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.08f), style = stroke)
        val play = Path().apply { moveTo(size.width * 0.43f, size.height * 0.40f); lineTo(size.width * 0.68f, size.height * 0.55f); lineTo(size.width * 0.43f, size.height * 0.70f); close() }
        drawPath(play, color)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.24f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.08f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.24f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.08f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}
