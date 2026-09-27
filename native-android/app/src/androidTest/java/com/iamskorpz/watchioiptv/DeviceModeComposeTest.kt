package com.iamskorpz.watchioiptv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.InputMode as ComposeInputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.iamskorpz.watchioiptv.core.device.DeviceInputDetection
import com.iamskorpz.watchioiptv.core.device.WatchioDeviceCategory
import com.iamskorpz.watchioiptv.domain.model.InputMode
import com.iamskorpz.watchioiptv.ui.DeviceModeScreen
import com.iamskorpz.watchioiptv.ui.theme.WatchioTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeviceModeComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun detectedTouchGetsInitialFocusAndTvRemainsManualOverride() {
        var automatic = 0
        var tv = 0
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            WatchioTheme {
                DeviceModeScreen(
                    detection = DeviceInputDetection(WatchioDeviceCategory.Phone, InputMode.Touch),
                    onAutomatic = { automatic++ },
                    onMobile = {},
                    onTv = { tv++ },
                )
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }

        composeRule.onNodeWithTag("device-mode-mobile").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.onNodeWithTag("device-mode-mobile").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeRule.onNodeWithTag("device-mode-tv").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        composeRule.runOnIdle {
            assertEquals(1, automatic)
            assertEquals(1, tv)
        }
    }

    @Test
    fun detectedTvGetsInitialFocus() {
        lateinit var inputModeManager: InputModeManager
        composeRule.setContent {
            inputModeManager = LocalInputModeManager.current
            WatchioTheme {
                DeviceModeScreen(
                    detection = DeviceInputDetection(WatchioDeviceCategory.FireTv, InputMode.TvRemote),
                    onAutomatic = {},
                    onMobile = {},
                    onTv = {},
                )
            }
        }
        composeRule.runOnIdle { inputModeManager.requestInputMode(ComposeInputMode.Keyboard) }

        composeRule.onNodeWithTag("device-mode-tv").assertIsFocused()
    }
}
