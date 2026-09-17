package com.iamskorpz.watchioiptv

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateBuildConfigTest {
    @Test
    fun updaterConfigurationMatchesBuildVariant() {
        val expected = when (BuildConfig.BUILD_TYPE) {
            "release" -> Triple(
                "stable",
                "Stable",
                "https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/main/native-android/update/stable.json",
            )
            "debug" -> Triple(
                "dev",
                "Development",
                "https://raw.githubusercontent.com/IAMSKORPZ/Watchio-IPTV/dev/native-android/update/update.json",
            )
            "local" -> Triple("local", "Local", "")
            "uitest" -> Triple("uitest", "Test", "watchio://uitest/update.json")
            else -> throw AssertionError("Unexpected build type: ${BuildConfig.BUILD_TYPE}")
        }

        assertEquals(expected.first, BuildConfig.UPDATE_CHANNEL)
        assertEquals(expected.second, BuildConfig.UPDATE_CHANNEL_DISPLAY_NAME)
        assertEquals(expected.third, BuildConfig.UPDATE_MANIFEST_URL)
    }
}
