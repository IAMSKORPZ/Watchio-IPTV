package com.watchioiptv.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.watchioiptv.nativeapp.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class WatchioNativeApplication : Application(), ImageLoaderFactory {
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }
}
