package com.iamskorpz.watchioiptv

import android.content.pm.ActivityInfo
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iamskorpz.watchioiptv.ui.WatchioNativeApp

class MainActivity : ComponentActivity() {
    private var sportsRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        applyImmersiveLandscape()
        sportsRequest = intent.getStringExtra("open_sports_fixture")
        val container = (application as WatchioNativeApplication).container
        setContent {
            WatchioNativeApp(container = container, openSportsRequest = sportsRequest, onSportsRequestConsumed = { sportsRequest = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sportsRequest = intent.getStringExtra("open_sports_fixture")
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        applyImmersiveLandscape()
    }

    private fun applyImmersiveLandscape() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
