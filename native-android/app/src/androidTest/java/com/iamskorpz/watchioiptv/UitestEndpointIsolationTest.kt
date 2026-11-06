package com.iamskorpz.watchioiptv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.iamskorpz.watchioiptv.uitest.UitestWatchioEndpointConfigSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UitestEndpointIsolationTest {
    @Test
    fun endpointSourceUsesOnlyTwoLocalSyntheticEndpoints() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.iamskorpz.watchioiptv.uitest", context.packageName)

        val source = UitestWatchioEndpointConfigSource(context)
        val config = source.bundled()

        assertNull(source.remote())
        assertTrue(config.contains("http://127.0.0.1:18081"))
        assertTrue(config.contains("http://127.0.0.1:18082"))
        assertFalse(config.contains("githubusercontent"))
        assertFalse(config.contains("https://"))
    }
}
