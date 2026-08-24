package com.watchioiptv.nativeapp

import android.app.Application
import com.watchioiptv.nativeapp.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class WatchioNativeApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            combine(
                container.settingsRepository.epgAutoRefreshEnabled,
                container.settingsRepository.epgRefreshInterval,
            ) { enabled, interval -> enabled to interval }
                .distinctUntilChanged()
                .collect { (enabled, interval) ->
                    container.epgAutoRefreshScheduler.apply(enabled, interval)
                }
        }
    }
}
