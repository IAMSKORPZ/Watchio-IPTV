package com.watchioiptv.nativeapp.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

internal class CategoryContentFocusTransferState {
    var pendingCategoryId by mutableStateOf<String?>(null)
        private set

    fun onCategoryActivated(categoryId: String, categoryWasFocused: Boolean) {
        pendingCategoryId = categoryId.takeIf { categoryWasFocused }
    }

    fun consume(categoryId: String) {
        if (pendingCategoryId == categoryId) pendingCategoryId = null
    }
}

@Composable
internal fun rememberCategoryContentFocusTransferState(): CategoryContentFocusTransferState =
    remember { CategoryContentFocusTransferState() }

@Composable
internal fun CategoryContentFocusTransferEffect(
    state: CategoryContentFocusTransferState,
    selectedCategoryId: String?,
    targetContentId: String?,
    targetFocusRequester: FocusRequester,
) {
    LaunchedEffect(state.pendingCategoryId, selectedCategoryId, targetContentId) {
        val pendingCategoryId = state.pendingCategoryId
        if (
            pendingCategoryId != null &&
            pendingCategoryId == selectedCategoryId &&
            targetContentId != null
        ) {
            withFrameNanos { }
            if (targetFocusRequester.requestFocus()) state.consume(pendingCategoryId)
        }
    }
}
