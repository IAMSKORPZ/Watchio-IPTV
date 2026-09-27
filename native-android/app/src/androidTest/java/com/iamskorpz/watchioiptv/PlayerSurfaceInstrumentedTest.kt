package com.iamskorpz.watchioiptv

import android.view.LayoutInflater
import android.view.TextureView
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlayerSurfaceInstrumentedTest {
    @Test
    fun sharedPlayerViewUsesFitTextureSurfaceWithMatchParentBounds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        lateinit var view: PlayerView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(context)
            view = LayoutInflater.from(context)
                .inflate(R.layout.watchio_player_view, parent, false) as PlayerView
        }

        assertTrue(view.videoSurfaceView is TextureView)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.resizeMode)
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.width)
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.height)
    }
}
