package com.iamskorpz.watchioiptv

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityManifestTest {
    @Test
    fun uitestPackageIdentitiesRemainIsolated() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        assertEquals("com.iamskorpz.watchioiptv.uitest", instrumentation.targetContext.packageName)
        assertEquals("com.iamskorpz.watchioiptv.uitest.test", instrumentation.context.packageName)
    }

    @Test
    fun mainActivityIsSensorLandscapeOnly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, info.screenOrientation)
    }
}
