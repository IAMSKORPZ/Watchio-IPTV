package com.iamskorpz.watchioiptv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.iamskorpz.watchioiptv.ui.theme.LocalWatchioColors

internal fun isTvSearchActivationKey(key: Key, type: KeyEventType): Boolean =
    type == KeyEventType.KeyUp && (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter)

@Composable
fun Modifier.tvSearchInput(focusRequester: FocusRequester): Modifier {
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequester, keyboardController) {
        focusRequester.requestFocus()
        withFrameNanos { }
        keyboardController?.show()
    }
    return focusRequester(focusRequester)
        .onPreviewKeyEvent { event ->
            if (isTvSearchActivationKey(event.key, event.type)) {
                focusRequester.requestFocus()
                keyboardController?.show()
                true
            } else {
                false
            }
        }
}

@Composable
fun WatchioSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val focusRequester = androidx.compose.runtime.remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val colors = LocalWatchioColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            disabledTextColor = colors.textMuted,
            cursorColor = colors.focusBorder,
            focusedBorderColor = colors.liveTvAccent,
            unfocusedBorderColor = colors.textMuted.copy(alpha = 0.55f),
            focusedLabelColor = colors.liveTvAccent,
            unfocusedLabelColor = colors.textSecondary,
            focusedContainerColor = colors.surfaceElevated.copy(alpha = 0.92f),
            unfocusedContainerColor = colors.surfaceElevated.copy(alpha = 0.72f),
        ),
        modifier = modifier
            .tvSearchInput(focusRequester)
            .testTag(testTag),
    )
}
