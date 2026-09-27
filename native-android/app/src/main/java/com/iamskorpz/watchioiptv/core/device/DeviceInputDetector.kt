package com.iamskorpz.watchioiptv.core.device

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import com.iamskorpz.watchioiptv.domain.model.InputMode

enum class WatchioDeviceCategory {
    Phone,
    Tablet,
    AndroidTv,
    FireTv,
    OtherTv,
}

data class DeviceInputSignals(
    val uiModeType: Int,
    val hasLeanback: Boolean,
    val hasTelevisionFeature: Boolean,
    val hasTouchscreen: Boolean,
    val smallestWidthDp: Int,
    val manufacturer: String,
    val model: String,
)

data class DeviceInputDetection(
    val category: WatchioDeviceCategory,
    val inputMode: InputMode,
)

object DeviceInputDetector {
    fun detect(signals: DeviceInputSignals): DeviceInputDetection {
        val platformTv = signals.uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            signals.hasLeanback || signals.hasTelevisionFeature
        val amazonDevice = signals.manufacturer.equals("Amazon", ignoreCase = true)
        val fireModel = signals.model.startsWith("AFT", ignoreCase = true) ||
            signals.model.contains("Fire TV", ignoreCase = true)

        val category = when {
            platformTv && (amazonDevice || fireModel) -> WatchioDeviceCategory.FireTv
            signals.uiModeType == Configuration.UI_MODE_TYPE_TELEVISION || signals.hasLeanback -> WatchioDeviceCategory.AndroidTv
            signals.hasTelevisionFeature -> WatchioDeviceCategory.OtherTv
            amazonDevice && fireModel -> WatchioDeviceCategory.FireTv
            !signals.hasTouchscreen -> WatchioDeviceCategory.OtherTv
            signals.smallestWidthDp >= 600 -> WatchioDeviceCategory.Tablet
            else -> WatchioDeviceCategory.Phone
        }
        val mode = when (category) {
            WatchioDeviceCategory.Phone, WatchioDeviceCategory.Tablet -> InputMode.Touch
            WatchioDeviceCategory.AndroidTv, WatchioDeviceCategory.FireTv, WatchioDeviceCategory.OtherTv -> InputMode.TvRemote
        }
        return DeviceInputDetection(category, mode)
    }
}

@Suppress("DEPRECATION")
fun Context.detectDeviceInput(): DeviceInputDetection {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val packageManager = packageManager
    return DeviceInputDetector.detect(
        DeviceInputSignals(
            uiModeType = uiModeManager?.currentModeType ?: (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK),
            hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            hasTelevisionFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
            hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            smallestWidthDp = resources.configuration.smallestScreenWidthDp,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
        ),
    )
}

fun DeviceInputDetection.label(): String = when (category) {
    WatchioDeviceCategory.Phone -> "Mobile / Touch"
    WatchioDeviceCategory.Tablet -> "Tablet / Touch"
    WatchioDeviceCategory.AndroidTv -> "Android TV / Remote"
    WatchioDeviceCategory.FireTv -> "Fire TV / Remote"
    WatchioDeviceCategory.OtherTv -> "TV / Remote"
}
