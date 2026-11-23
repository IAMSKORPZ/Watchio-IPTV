package com.iamskorpz.watchioiptv

import android.annotation.SuppressLint
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.iamskorpz.watchioiptv.core.di.AppContainer
import com.iamskorpz.watchioiptv.feature.backup.BackupRecoveryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@SuppressLint("UnsafeOptInUsageError")
class WatchioNativeApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runBlocking(Dispatchers.IO) {
            container.backupRepository.recoverInterruptedRestore()
        }
        if (container.backupRepository.recoveryStatus.value != BackupRecoveryStatus.READY) return
        appScope.launch(Dispatchers.IO) {
            container.offlineMovieDownloadRepository.reconcile()
        }
        appScope.launch {
            combine(
                container.settingsRepository.epgAutoRefreshEnabled,
                container.settingsRepository.epgRefreshInterval,
                container.backupRepository.recoveryStatus,
            ) { enabled, interval, recoveryStatus -> Triple(enabled, interval, recoveryStatus) }
                .distinctUntilChanged()
                .collect { (enabled, interval, recoveryStatus) ->
                    container.epgAutoRefreshScheduler.apply(enabled && recoveryStatus == BackupRecoveryStatus.READY, interval)
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
