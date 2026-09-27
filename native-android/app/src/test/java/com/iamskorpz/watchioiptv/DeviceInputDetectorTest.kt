package com.iamskorpz.watchioiptv

import android.content.res.Configuration
import com.iamskorpz.watchioiptv.core.device.DeviceInputDetector
import com.iamskorpz.watchioiptv.core.device.DeviceInputSignals
import com.iamskorpz.watchioiptv.core.device.WatchioDeviceCategory
import com.iamskorpz.watchioiptv.domain.model.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInputDetectorTest {
    @Test fun televisionUiModeUsesRemote() = assertDetection(signals(uiModeType = Configuration.UI_MODE_TYPE_TELEVISION), WatchioDeviceCategory.AndroidTv, InputMode.TvRemote)
    @Test fun leanbackUsesRemote() = assertDetection(signals(hasLeanback = true), WatchioDeviceCategory.AndroidTv, InputMode.TvRemote)
    @Test fun televisionFeatureUsesRemote() = assertDetection(signals(hasTelevisionFeature = true), WatchioDeviceCategory.OtherTv, InputMode.TvRemote)
    @Test fun fireTvUsesRemote() = assertDetection(signals(hasLeanback = true, manufacturer = "Amazon", model = "AFTMM"), WatchioDeviceCategory.FireTv, InputMode.TvRemote)
    @Test fun fireTvFallbackUsesRemote() = assertDetection(signals(manufacturer = "Amazon", model = "AFTSSS", hasTouchscreen = false), WatchioDeviceCategory.FireTv, InputMode.TvRemote)
    @Test fun touchscreenPhoneUsesTouch() = assertDetection(signals(), WatchioDeviceCategory.Phone, InputMode.Touch)
    @Test fun touchscreenTabletUsesTouch() = assertDetection(signals(smallestWidthDp = 600), WatchioDeviceCategory.Tablet, InputMode.Touch)

    private fun assertDetection(signals: DeviceInputSignals, category: WatchioDeviceCategory, mode: InputMode) {
        val detection = DeviceInputDetector.detect(signals)
        assertEquals(category, detection.category)
        assertEquals(mode, detection.inputMode)
    }

    private fun signals(
        uiModeType: Int = Configuration.UI_MODE_TYPE_NORMAL,
        hasLeanback: Boolean = false,
        hasTelevisionFeature: Boolean = false,
        hasTouchscreen: Boolean = true,
        smallestWidthDp: Int = 411,
        manufacturer: String = "Samsung",
        model: String = "Phone",
    ) = DeviceInputSignals(uiModeType, hasLeanback, hasTelevisionFeature, hasTouchscreen, smallestWidthDp, manufacturer, model)
}
