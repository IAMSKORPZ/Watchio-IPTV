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
import androidx.compose.material3.Text

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        modifier = modifier
            .tvSearchInput(focusRequester)
            .testTag(testTag),
    )
}
